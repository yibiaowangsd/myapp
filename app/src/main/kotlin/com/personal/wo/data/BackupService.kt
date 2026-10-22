package com.personal.wo.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.Room
import androidx.room.withTransaction
import com.personal.wo.core.*
import com.personal.wo.core.Annotation
import com.google.crypto.tink.*
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import com.google.crypto.tink.subtle.AesGcmJce
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.*

@Serializable data class ArchiveHeader(val version:Int=1,val algorithm:String="AES256_GCM_HKDF_1MB",val encryptedKeyset:String)
@Serializable data class ArchiveManifest(val version:Int=1,val archiveZone:String,val hashes:Map<String,String>,val counts:Map<String,Int>)
@Serializable data class BackupPolicy(val policy:Policy,val sourceKeySeed:String,val memory:LifeMemory?=null)
@Serializable data class BackupEpisode(val episode:Episode,val relations:List<RelationExport> = emptyList())
@Serializable data class RelationExport(val parentId:String,val childId:String,val relation:String)
data class RestoreInspection(val directory:File,val manifest:ArchiveManifest,val files:Map<String,File>,val count:Int)

class BackupService(private val context:Context,private val repo:WoRepository) {
    private val names=ArchiveRules.names
    private val maxBytes=512L*1024*1024
    private fun digest(file:File):String = MessageDigest.getInstance("SHA-256").run { file.inputStream().use { input -> val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;update(b,0,n)} };digest().joinToString("") { "%02x".format(it) } }
    private fun privateTemp():File=File(context.noBackupFilesDir,"archive-${UUID.randomUUID()}").apply { mkdirs() }
    suspend fun export(uri:Uri):String = repo.mutex.withLock {
        StreamingAeadConfig.register()
        val directory=privateTemp();val counts=mutableMapOf<String,Int>()
        try {
            val dao=repo.db.dao()
            suspend fun rows(name:String,fetch:suspend(Int)->List<String>) {
                var offset=0
                File(directory,name).bufferedWriter().use { w ->
                    while(true) { currentCoroutineContext().ensureActive();val lines=fetch(offset);if(lines.isEmpty())break
                        lines.forEach { w.write(it);w.newLine() };offset+=lines.size;check(offset<=50000) { "本次归档超过50000条，请按日期范围导出" }
                    }
                };counts[name]=offset
            }
            rows("observations.jsonl") { offset -> dao.allObservations(200,offset).map { it.json } }
            val relations=dao.relations()
            rows("episodes.jsonl") { offset -> dao.allEpisodes(200,offset).map { r -> val e=WoJson.decodeFromString<Episode>(r.json);WoJson.encodeToString(BackupEpisode(e,relations.filter { it.parentId==e.id }.map { RelationExport(it.parentId,it.childId,it.relation) })) } }
            val annotations=dao.annotations()
            File(directory,"annotations.jsonl").bufferedWriter().use { w -> annotations.forEach { w.write(it.json);w.newLine() } };counts["annotations.jsonl"]=annotations.size
            val places=repo.places();File(directory,"places.json").writeText(WoJson.encodeToString(places));counts["places.json"]=places.size
            val secretSeed=repo.vault.seed()
            val policy=BackupPolicy(repo.cachedPolicy,Base64.encodeToString(secretSeed,Base64.NO_WRAP),dao.lifeMemory());secretSeed.fill(0)
            File(directory,"policy.json").writeText(WoJson.encodeToString(policy));counts["policy.json"]=1
            val days=dao.summaries(50000)
            File(directory,"daily.jsonl").bufferedWriter().use { w -> days.forEach { w.write(it.json);w.newLine() } };counts["daily.jsonl"]=days.size
            val hashes=(names-"manifest.json").associateWith { digest(File(directory,it)) }
            check(directory.listFiles()!!.sumOf { it.length() }<=maxBytes)
            File(directory,"manifest.json").writeText(WoJson.encodeToString(ArchiveManifest(archiveZone=repo.cachedPolicy.archiveZone,hashes=hashes,counts=counts)))
            val recovery=ByteArray(32).also { SecureRandom().nextBytes(it) }
            val handle=KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM_HKDF_1MB"))
            val wrapped=ByteArrayOutputStream().also { handle.write(JsonKeysetWriter.withOutputStream(it),AesGcmJce(recovery)) }.toByteArray()
            val header=WoJson.encodeToString(ArchiveHeader(encryptedKeyset=Base64.encodeToString(wrapped,Base64.NO_WRAP))).toByteArray()
            val aad=ByteArrayOutputStream().also { val d=DataOutputStream(it);d.write("WOARCH01".toByteArray());d.writeInt(header.size);d.write(header) }.toByteArray()
            context.contentResolver.openOutputStream(uri,"wt")!!.use { raw ->
                raw.write(aad)
                handle.getPrimitive(StreamingAead::class.java).newEncryptingStream(raw,aad).use { encrypted ->
                    ZipOutputStream(encrypted).use { zip ->
                        names.sorted().forEach { name -> currentCoroutineContext().ensureActive();zip.putNextEntry(ZipEntry(name));File(directory,name).inputStream().use { it.copyTo(zip,65536) };zip.closeEntry() }
                    }
                }
            }
            Base64.encodeToString(recovery,Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING).also { recovery.fill(0) }
        } finally { directory.deleteRecursively() }
    }
    suspend fun inspect(uri:Uri,recoveryCode:String):RestoreInspection {
        StreamingAeadConfig.register()
        val dir=privateTemp()
        try {
            val recovery=Base64.decode(recoveryCode.trim(),Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING);require(recovery.size==32) { "恢复码应为32字节的Base64URL编码" }
            context.contentResolver.openInputStream(uri)!!.use { raw ->
                val data=DataInputStream(raw);val magic=ByteArray(8).also { data.readFully(it) };check(magic.contentEquals("WOARCH01".toByteArray())) { "E001:归档文件标识不正确" }
                val length=data.readInt();ArchiveRules.header(length)
                val bytes=ByteArray(length).also { data.readFully(it) };val header=WoJson.decodeFromString<ArchiveHeader>(bytes.decodeToString())
                ArchiveRules.header(length,header.version,header.algorithm)
                val aad=ByteArrayOutputStream().also { DataOutputStream(it).apply { write(magic);writeInt(length);write(bytes) } }.toByteArray()
                val handle=try { KeysetHandle.read(JsonKeysetReader.withBytes(Base64.decode(header.encryptedKeyset,Base64.NO_WRAP)),AesGcmJce(recovery)) } finally { recovery.fill(0) }
                // Authenticate the entire stream, including its final segment, before any ZIP parsing or database mutation.
                val plain=File(dir,"payload.zip")
                handle.getPrimitive(StreamingAead::class.java).newDecryptingStream(raw,aad).use { decrypted ->
                    plain.outputStream().use { out -> val b=ByteArray(65536);var total=0L
                        while(true) { currentCoroutineContext().ensureActive();val n=decrypted.read(b);if(n<0)break;total+=n;require(total<=maxBytes);out.write(b,0,n) }
                    }
                }
                val seen=mutableSetOf<String>();var expanded=0L
                ZipInputStream(plain.inputStream()).use { zip ->
                    while(true) { val entry=zip.nextEntry ?: break;ArchiveRules.entry(entry.name,entry.isDirectory,seen)
                        File(dir,entry.name).outputStream().use { output -> val buffer=ByteArray(65536)
                            while(true) { currentCoroutineContext().ensureActive();val n=zip.read(buffer);if(n<0)break;expanded+=n;require(expanded<=maxBytes);output.write(buffer,0,n) }
                        };zip.closeEntry()
                    }
                }
                require(seen==names);plain.delete()
            }
            require(File(dir,"manifest.json").length()<=65536&&File(dir,"policy.json").length()<=524288&&File(dir,"places.json").length()<=16*1024*1024)
            val manifest=WoJson.decodeFromString<ArchiveManifest>(File(dir,"manifest.json").readText());require(manifest.version==1&&manifest.hashes.keys==names-"manifest.json")
            val files=names.associateWith { File(dir,it) }
            manifest.hashes.forEach { (name,hash) -> require(digest(files.getValue(name))==hash) { "E001:内容哈希不匹配" } }
            var total=0
            listOf("observations.jsonl","episodes.jsonl","annotations.jsonl","daily.jsonl").forEach { name -> var count=0
                files.getValue(name).forEachLine { line -> require(line.toByteArray().size<=if(name=="observations.jsonl") 8192 else 2*1024*1024);count++;require(count<=50000) }
                require(count==manifest.counts[name]);total+=count
            }
            return RestoreInspection(dir,manifest,files,total)
        } catch(e:Exception) { dir.deleteRecursively();throw IllegalArgumentException("E001:恢复校验未通过，现有数据未更改（${e.javaClass.simpleName}）") }
    }
    suspend fun restore(inspection:RestoreInspection) = repo.mutex.withLock {
        val stageName="wo-restore-${UUID.randomUUID()}.db";val stage=Room.databaseBuilder(context,WoDatabase::class.java,stageName).openHelperFactory(SupportOpenHelperFactory(repo.vault.password())).build()
        val oldSeed=repo.vault.seed()
        try {
            val policy=WoJson.decodeFromString<BackupPolicy>(inspection.files.getValue("policy.json").readText())
            policy.memory?.checked()
            val seed=Base64.decode(policy.sourceKeySeed,Base64.NO_WRAP);require(seed.size==32);java.time.ZoneId.of(policy.policy.archiveZone)
            val places=WoJson.decodeFromString<List<Place>>(inspection.files.getValue("places.json").readText());require(places.size<=50000)
            val dao=stage.dao()
            stage.withTransaction {
                suspend fun <T> lines(name:String,decode:(String)->T,insert:suspend(T)->Unit) {
                    inspection.files.getValue(name).bufferedReader().use { r ->
                        while(true) { val line=r.readLine() ?: break;currentCoroutineContext().ensureActive();insert(decode(line)) }
                    }
                }
                lines("observations.jsonl",{ WoJson.decodeFromString<Observation>(it) }) { original ->
                    require(original.endMs>=original.startMs&&original.startMs>=0&&original.schemaVersion==1)
                    val o=if(original.kind==Kind.PHOTO)original.copy(payload=original.payload.copy(uri=""),quality=original.quality+"MEDIA_RELINK_REQUIRED") else original
                    dao.putObservations(listOf(ObservationRow(o.id,o.source.name,o.sourceKey,o.kind.name,o.startMs,o.endMs,o.receivedMs,WoJson.encodeToString(o))))
                }
                lines("episodes.jsonl",{ WoJson.decodeFromString<BackupEpisode>(it) }) { original ->
                    val e=original.episode.copy(photoUris=emptyList(),qualityFlags=if(original.episode.photoUris.isNotEmpty())original.episode.qualityFlags+"MEDIA_RELINK_REQUIRED" else original.episode.qualityFlags)
                    require(e.endMs>=e.startMs&&e.startMs>=0)
                    val text="${e.title} ${e.detail} ${e.packageName}".lowercase()
                    dao.putEpisode(EpisodeRow(e.id,e.revision,e.startMs,e.endMs,e.kind,e.title,text,WoJson.encodeToString(e)))
                    dao.putIndex(SearchRow(episodeId=e.id,tokens=NoticeRules.tokens(text)));dao.putEvidence(e.evidence.map { EvidenceRow(e.id,it.id,WoJson.encodeToString(it)) })
                    dao.putRelations(original.relations.map { RelationRow(it.parentId,it.childId,it.relation) })
                }
                lines("annotations.jsonl",{ WoJson.decodeFromString<Annotation>(it) }) { a -> dao.putAnnotation(AnnotationRow(a.id,a.episodeId,0,WoJson.encodeToString(a))) }
                lines("daily.jsonl",{ WoJson.decodeFromString<DayView>(it) }) { original ->
                    val d=original.copy(episodes=original.episodes.map { it.copy(photoUris=emptyList()) })
                    require(d.schemaVersion==1&&d.windowEndMs>d.windowStartMs)
                    dao.putSummary(SummaryRow(d.dayDate,d.zoneId,d.revision,d.asOfMs,WoJson.encodeToString(d)))
                }
                places.forEach { dao.putPlace(PlaceRow(it.id,WoJson.encodeToString(it))) }
            }
            require(stage.openHelper.writableDatabase.query("PRAGMA integrity_check").use { it.moveToFirst()&&it.getString(0)=="ok" })
            // All imported objects are validated in an independent encrypted database first.
            // The final replacement is one SQLite transaction, so a crash rolls back to the old data.
            repo.vault.restoreSeed(seed);seed.fill(0)
            repo.db.withTransaction {
                val target=repo.db.dao()
                val memoryRevision=target.lifeMemory().revision
                target.putLifeMemory((policy.memory ?: LifeMemory()).copy(revision=memoryRevision+1))
                listOf("event_search","episode_evidence","episode_relation","annotation","observation","episode","place","media_ref","daily_metric","daily_summary","source_coverage","dirty_window","exclusion","source_state").forEach { repo.db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
                repo.db.dao().deleteMetaRange("achievement.day.v1:","achievement.day.v1:\uffff")
                repo.db.dao().deleteMetaRange("day.snapshot.v1:","day.snapshot.v1:\uffff")
                var offset=0
                while(true) { val rows=dao.allObservations(200,offset);if(rows.isEmpty())break;target.putObservations(rows);offset+=rows.size }
                offset=0
                while(true) { val rows=dao.allEpisodes(200,offset);if(rows.isEmpty())break;rows.forEach { r -> target.putEpisode(r);target.putIndex(SearchRow(episodeId=r.id,tokens=NoticeRules.tokens(r.searchText)));val e=WoJson.decodeFromString<Episode>(r.json);target.putEvidence(e.evidence.map { EvidenceRow(e.id,it.id,WoJson.encodeToString(it)) }) };offset+=rows.size }
                dao.annotations().forEach { target.putAnnotation(it) };dao.places().forEach { target.putPlace(it) };dao.summaries(50000).forEach { target.putSummary(it) };target.putRelations(dao.relations())
                val now=System.currentTimeMillis();val p=policy.policy.copy(requested=false,paused=false,generation=repo.cachedPolicy.generation+1,exclusions=policy.policy.exclusions.filter { it.endMs!=null || it.reason.startsWith("DELETE") }+Exclusion(UUID.randomUUID().toString(),now,reason="STOP"))
                target.putPolicy(PolicyRow(json=WoJson.encodeToString(p)));p.exclusions.forEach { target.putExclusion(ExclusionRow(it.id,it.startMs,it.endMs,WoJson.encodeToString(it))) }
                target.putSource(SourceRow(Source.USAGE.name,WoJson.encodeToString(SourceState(Source.USAGE,cursor=now.toString(),detail="恢复后从本机当前时刻重新建立来源游标"))))
                target.putOperation(OperationRow(UUID.randomUUID().toString(),now,"RESTORE_COMMITTED",inspection.count.toLong()))
            }
        } catch(e:Exception) { repo.vault.restoreSeed(oldSeed);throw e }
        finally { oldSeed.fill(0);stage.close();context.deleteDatabase(stageName);inspection.directory.deleteRecursively() }
    }
    suspend fun exportDay(uri:Uri,day:DayView,json:Boolean=false) {
        val filtered=day.copy(episodes=day.episodes.map { it.copy(lat=null,lon=null,photoUris=emptyList(),detail=if(it.kind=="NOTICE") "通知元数据（不含正文）" else it.detail) })
        context.contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use { w ->
            if(json)w.write(WoJson.encodeToString(filtered)) else {
                w.write("# ${day.dayDate} · 我的一天\n\n归档时区：${day.zoneId}\n\n${day.sentences.joinToString("\n\n") { it.text }}\n\n")
                filtered.episodes.forEach { e -> val t=java.time.Instant.ofEpochMilli(e.startMs).atZone(java.time.ZoneId.of(day.zoneId)).toLocalTime();w.write("- $t · ${e.title}（${e.assertion}）\n") }
                if(day.warnings.isNotEmpty())w.write("\n说明：${day.warnings.joinToString("；")}\n")
            }
        }
    }
}
