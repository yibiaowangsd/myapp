package com.personal.wo.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.time.LocalDate
import java.util.concurrent.CancellationException

@Serializable data class SharedDayEntry(val date:String,val path:String,val hash:String)
@Serializable data class SharedMemoryEntry(val path:String="wo/memory.json",val hash:String)
@Serializable data class SharedDataIndex(
    val schemaVersion:Int=1,val source:String="wo-life-records",val archiveZone:String,
    val updatedAtMs:Long,val days:List<SharedDayEntry>,val memory:SharedMemoryEntry?=null,val pendingDates:List<String> = emptyList()
)
data class SharedSyncResult(val uploaded:Int,val pending:Int,val total:Int,val failedDates:List<String> = emptyList())
interface SharedFileStore {
    suspend fun requirePrivate()
    suspend fun read(path:String):String?
    /** Publish every change together, or leave the live branch unchanged. */
    suspend fun commit(files:Map<String,String>,deletions:Set<String>)
}

fun sharedDayPath(date:String):String="wo/days/${LocalDate.parse(date)}.json"
fun sharedHash(text:String):String=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).lowerHex()
fun sharedDayHash(payload:JsonObject):String=sharedHash(JsonObject(payload.filterKeys { it!="asOfMs" }).toString())

const val SHARED_FILE_BYTES=524288
private const val PART_BYTES=240*1024
@Serializable data class SharedPart(val path:String,val hash:String)
@Serializable data class SharedDayParts(
    val schemaVersion:Int=2,val format:String="wo-day-parts",val date:String,val timeZone:String,
    val asOfMs:Long,val hash:String,val fields:List<String>,val arrays:List<String>,val parts:List<SharedPart>
)

/** Each part is valid JSON. Array order and all records survive without truncation. */
fun sharedDayFiles(payload:JsonObject):Map<String,String> {
    val date=payload.getValue("date").jsonPrimitive.content
    val path=sharedDayPath(date);val text=payload.toString()
    if(text.toByteArray().size<=SHARED_FILE_BYTES)return linkedMapOf(path to text)
    val hash=sharedDayHash(payload);val files=linkedMapOf<String,String>();val parts=mutableListOf<SharedPart>()
    var fragment=linkedMapOf<String,JsonElement>();var fragmentBytes=2
    fun flush() {
        if(fragment.isEmpty())return
        val partPath="wo/parts/$date/$hash/${parts.size.toString().padStart(5,'0')}.json"
        val content=JsonObject(fragment).toString()
        files[partPath]=content;parts+=SharedPart(partPath,sharedHash(content));fragment=linkedMapOf();fragmentBytes=2
    }
    fun add(key:String,value:JsonElement,array:Boolean) {
        val valueBytes=value.toString().toByteArray().size
        fun growth():Int {
            val previous=fragment[key]
            if(previous!=null) {
                require(array&&previous is JsonArray)
                return valueBytes+if(previous.isEmpty())0 else 1
            }
            return (if(fragment.isEmpty())0 else 1)+JsonPrimitive(key).toString().toByteArray().size+1+valueBytes+if(array)2 else 0
        }
        if(fragmentBytes+growth()>PART_BYTES)flush()
        val extra=growth();require(fragmentBytes+extra<=PART_BYTES) { "单条记录超过分片大小" }
        fragment[key]=if(array)JsonArray((fragment[key] as? JsonArray).orEmpty()+value) else value
        fragmentBytes+=extra
    }
    payload.forEach { (key,value)->
        if(value is JsonArray) {
            if(value.isEmpty())add(key,value,false) else value.forEach { add(key,it,true) }
        } else add(key,value,false)
    }
    flush()
    files[path]=WoJson.encodeToString(SharedDayParts.serializer(),SharedDayParts(date=date,
        timeZone=payload.getValue("timeZone").jsonPrimitive.content,asOfMs=payload.getValue("asOfMs").jsonPrimitive.long,
        hash=hash,fields=payload.keys.toList(),arrays=payload.filterValues { it is JsonArray }.keys.toList(),parts=parts))
    require(files.getValue(path).toByteArray().size<=SHARED_FILE_BYTES) { "日期分片目录过大" }
    return files
}

/** Used by readers and migrations; verify every part and the reconstructed day. */
suspend fun readSharedDay(text:String,read:suspend(String)->String?):JsonObject {
    val json=WoJson.parseToJsonElement(text).jsonObject
    if(json["format"]?.jsonPrimitive?.content!="wo-day-parts")return json
    val manifest=WoJson.decodeFromString<SharedDayParts>(text)
    require(manifest.schemaVersion==2&&manifest.parts.isNotEmpty())
    val values=mutableMapOf<String,JsonElement>()
    manifest.parts.forEachIndexed { i,part->
        require(part.path=="wo/parts/${LocalDate.parse(manifest.date)}/${manifest.hash}/${i.toString().padStart(5,'0')}.json")
        val fragment=checkNotNull(read(part.path)) { "缺少日期分片" };require(sharedHash(fragment)==part.hash) { "日期分片校验失败" }
        WoJson.parseToJsonElement(fragment).jsonObject.forEach { (key,value)->
            require(key in manifest.fields)
            if(key in manifest.arrays)values[key]=JsonArray((values[key] as? JsonArray).orEmpty()+value.jsonArray)
            else { require(key !in values);values[key]=value }
        }
    }
    require(values.keys==manifest.fields.toSet())
    val payload=JsonObject(manifest.fields.associateWith { values.getValue(it) })
    require(payload["date"]?.jsonPrimitive?.content==manifest.date&&payload["timeZone"]?.jsonPrimitive?.content==manifest.timeZone)
    require(sharedDayHash(payload)==manifest.hash) { "日期内容校验失败" }
    return payload
}

/** Share structured observations, never raw notification text, coordinates or local media URIs. */
fun sharedDay(day:DayView):JsonObject=buildJsonObject {
    put("schemaVersion",1);put("date",day.dayDate);put("timeZone",day.zoneId)
    put("asOfMs",day.asOfMs);put("status",day.status);put("headline",day.headline)
    put("summary",buildJsonArray { day.sentences.forEach { add(it.text) } })
    put("facts",buildJsonArray { day.facts.forEach { f->add(buildJsonObject {
        put("id",f.id);put("type",f.type);put("value",f.displayValue);put("assertion",f.assertion.name)
        put("sourceRefs",buildJsonArray { f.sourceRefs.forEach { add(it) } })
    }) } })
    put("metrics",buildJsonArray { day.metrics.forEach { m->add(buildJsonObject {
        put("key",m.key);put("value",m.value?.let(::JsonPrimitive) ?: JsonNull);put("unit",m.unit);put("label",m.label)
    }) } })
    put("episodes",buildJsonArray { day.episodes.filter { it.kind!="NOTICE" }.forEach { e->add(buildJsonObject {
        put("id",e.id);put("startMs",e.startMs);put("endMs",e.endMs);put("kind",e.kind)
        put("title",e.title);put("assertion",e.assertion.name);put("favorite",e.isFavorite)
        put("sources",buildJsonArray { e.evidence.map { it.source.name }.distinct().forEach { add(it) } })
        put("qualityFlags",buildJsonArray { e.qualityFlags.forEach { add(it) } })
    }) } })
    put("coverage",buildJsonArray { day.coverage.forEach { c->add(buildJsonObject {
        put("source",c.source.name);put("available",c.available);put("startMs",c.startMs);put("endMs",c.endMs)
    }) } })
    put("warnings",buildJsonArray { day.warnings.forEach { add(it) } })
}

/** A batch and its index become visible in one atomic commit. Failed dates never starve memory. */
class SharedDataPublisher(private val store:SharedFileStore,private val now:()->Long=System::currentTimeMillis) {
    suspend fun publish(dates:List<String>,zone:String,load:suspend(String)->DayView,maxChanges:Int=48,memory:LifeMemory?=null):SharedSyncResult {
        require(maxChanges>0)
        store.requirePrivate()
        val original=store.read("wo/index.json")?.let { WoJson.decodeFromString<SharedDataIndex>(it) }
        require(original==null||(original.schemaVersion in 1..2&&original.source=="wo-life-records")) { "数据目录格式不兼容" }
        require(original?.memory==null||original.memory.path=="wo/memory.json") { "记忆路径不正确" }
        val existing=original?.days.orEmpty().associateBy { it.date }
        require(existing.values.all { it.path==sharedDayPath(it.date) }) { "数据索引中的日期路径不正确" }
        val canonical=dates.distinct().sortedDescending();canonical.forEach { LocalDate.parse(it) }
        val entries=existing.filterKeys { it in canonical }.toMutableMap()
        val changes=linkedMapOf<String,String>()
        suspend fun sameDay(text:String?,hash:String):Boolean {
            if(text==null)return false
            return try { sharedDayHash(readSharedDay(text,store::read))==hash }
            catch(e:CancellationException) { throw e }
            catch(e:java.io.IOException) { throw e }
            catch(e:Exception) { false }
        }
        val legacyContents=mutableMapOf<String,String?>()
        if(original?.schemaVersion==1)for(entry in existing.values) {
            val text=store.read(entry.path);legacyContents[entry.date]=text
            if(!sameDay(text,entry.hash))entries.remove(entry.date)
        }
        suspend fun storedPaths(entry:SharedDayEntry):Set<String> {
            val text=store.read(entry.path) ?: return emptySet()
            val json=WoJson.parseToJsonElement(text).jsonObject
            if(json["format"]?.jsonPrimitive?.content!="wo-day-parts")return setOf(entry.path)
            val manifest=WoJson.decodeFromString<SharedDayParts>(text)
            require(manifest.date==entry.date&&manifest.hash==entry.hash)
            require(manifest.parts.withIndex().all { (i,part)->part.path=="wo/parts/${entry.date}/${entry.hash}/${i.toString().padStart(5,'0')}.json" })
            return manifest.parts.map { it.path }.toSet()+entry.path
        }
        val deletions=mutableSetOf<String>()
        existing.filterKeys { it !in canonical }.values.forEach { deletions+=storedPaths(it) }
        var uploaded=0;var pending=0;val failed=mutableListOf<String>();val pendingDates=mutableListOf<String>()
        for(date in canonical) {
            val payload=try {
                val day=load(date);require(day.dayDate==date&&day.zoneId==zone);sharedDay(day)
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { failed+=date;pendingDates+=date;pending++;continue }
            val hash=sharedDayHash(payload)
            val path=sharedDayPath(date)
            // A v1 writer could have overwritten a day before its index failed. Verify that
            // migration snapshot instead of trusting an unchanged index hash on its own.
            val remote=if(date in legacyContents)legacyContents[date] else if(original==null||original.schemaVersion==1)store.read(path) else null
            if(existing[date]?.hash==hash&&(original?.schemaVersion!=1||date in entries))continue
            if(uploaded>=maxChanges) { pendingDates+=date;pending++;continue }
            val encoded=try { sharedDayFiles(payload) }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) { failed+=date;pendingDates+=date;pending++;continue }
            // With no index, adopt already-uploaded legacy bytes rather than uploading history again.
            val reusable=sameDay(remote,hash)
            if(!reusable)changes.putAll(encoded)
            existing[date]?.let { deletions+=storedPaths(it)-encoded.keys }
            entries[date]=SharedDayEntry(date,path,hash);uploaded++
        }
        val days=entries.values.sortedByDescending { it.date }
        val memoryEntry=memory?.let {
            val text=WoJson.encodeToString(LifeMemory.serializer(),it.checked())
            val hash=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).lowerHex()
            // Compare content, rather than just the index, so a failed index write is retryable.
            if(store.read("wo/memory.json")!=text)changes["wo/memory.json"]=text
            SharedMemoryEntry(hash=hash)
        } ?: original?.memory
        if(original==null||original.schemaVersion!=2||original.archiveZone!=zone||original.days!=days||original.memory!=memoryEntry||original.pendingDates!=pendingDates) {
            val index=SharedDataIndex(schemaVersion=2,archiveZone=zone,updatedAtMs=now(),days=days,memory=memoryEntry,pendingDates=pendingDates)
            changes["wo/index.json"]=WoJson.encodeToString(SharedDataIndex.serializer(),index)
        }
        if(changes.isNotEmpty()||deletions.isNotEmpty())store.commit(changes,deletions)
        return SharedSyncResult(uploaded,pending,days.size,failed)
    }
}
