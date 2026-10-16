package com.personal.wo.data

import android.content.Context
import androidx.room.withTransaction
import com.personal.wo.core.*
import com.personal.wo.core.Annotation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.time.*
import java.util.UUID

data class DeletionPreview(val generation: Long, val startMs: Long, val endMs: Long, val episodes: Int, val observations: Int, val id: String? = null)

class WoRepository(val db: WoDatabase, val vault: KeyVault, val config: EngineConfig, private val context: Context) {
    val changes = MutableStateFlow(0L)
    val mutex = Mutex()
    @Volatile var cachedPolicy = Policy(); private set
    val engine = DayEngine(config)
    private val dao get() = db.dao()
    private val recentDays=RecentDayCache()
    private fun dayKey(date:LocalDate,zone:String)="day.snapshot.v1:$zone:$date"
    private fun snapshotPolicy()="${cachedPolicy.generation}:${config.version}"
    private fun notifyChange() { changes.value += 1 }
    suspend fun initialize() { mutex.withLock {
        recentDays.clear()
        cachedPolicy = dao.policy()?.let { WoJson.decodeFromString(it.json) } ?: Policy()
        dao.putPolicy(PolicyRow(json = WoJson.encodeToString(cachedPolicy)))
        dao.putMeta(MetaRow("engineVersion",config.version))
        mirror()
    } }
    private fun mirror() { context.getSharedPreferences("runtime",0).edit().putBoolean("requested",cachedPolicy.requested).putBoolean("paused",cachedPolicy.paused).apply() }
    private suspend fun savePolicy(p: Policy) {
        dao.putPolicy(PolicyRow(json = WoJson.encodeToString(p)))
        p.exclusions.forEach { dao.putExclusion(ExclusionRow(it.id,it.startMs,it.endMs,WoJson.encodeToString(it))) }
        cachedPolicy = p; mirror()
    }
    suspend fun command(action: String) = mutex.withLock {
        val now = System.currentTimeMillis(); val p = cachedPolicy
        db.withTransaction {
            val es = p.exclusions.toMutableList()
            when (action) {
                "start", "resume" -> {
                    es.replaceAll { if (it.reason in listOf("PAUSE","STOP") && it.endMs == null) it.copy(endMs = now) else it }
                    savePolicy(p.copy(requested=true,paused=false,generation=p.generation+1,startedMs=p.startedMs ?: now,lastAliveMs=now,exclusions=es))
                }
                "pause", "stop" -> {
                    if (!(p.paused || !p.requested)) es += Exclusion(UUID.randomUUID().toString(),now,reason=if(action=="pause") "PAUSE" else "STOP")
                    savePolicy(p.copy(requested=action=="pause",paused=action=="pause",generation=p.generation+1,exclusions=es))
                }
            }
            dao.putOperation(OperationRow(UUID.randomUUID().toString(),now,"RECORDING_${action.uppercase()}"))
        }
        notifyChange()
    }
    suspend fun configure(transform: (Policy) -> Policy) = mutex.withLock { db.withTransaction { savePolicy(transform(cachedPolicy).copy(generation=cachedPolicy.generation+1)) }; notifyChange() }
    suspend fun sources(): List<SourceState> = dao.sources().map { WoJson.decodeFromString(it.json) }
    suspend fun source(source: Source): SourceState = sources().firstOrNull { it.source==source } ?: SourceState(source)
    suspend fun state(state: SourceState) = mutex.withLock {
        val row=SourceRow(state.source.name,WoJson.encodeToString(state))
        if(dao.sources().none { it==row }) { dao.putSource(row);notifyChange() }
    }
    suspend fun probed(fresh:List<SourceState>) = mutex.withLock {
        val old=sources().associateBy { it.source }
        val changed=fresh.map { s -> old[s.source]?.let { s.copy(lastSuccessMs=it.lastSuccessMs,cursor=it.cursor,freshness=it.freshness) } ?: s }
            .filter { it!=old[it.source] }
        if(changed.isNotEmpty()) {
            db.withTransaction { changed.forEach { dao.putSource(SourceRow(it.source.name,WoJson.encodeToString(it))) } }
            notifyChange()
        }
    }
    suspend fun log(code: String, count: Long = 0) = mutex.withLock { dao.putOperation(OperationRow(UUID.randomUUID().toString(),System.currentTimeMillis(),code,count)) }
    suspend fun ingest(batch: List<Observation>, expectedGeneration: Long, source: Source? = null, cursor: String? = null, coverageSpan: Span? = null, replaceSpan: Span? = null) = mutex.withLock {
        val p = cachedPolicy
        if (!p.requested || p.paused || p.generation!=expectedGeneration) return@withLock
        val used=context.getDatabasePath("wo.db").length()+context.getDatabasePath("wo.db-wal").length()
        val softLimit=config.databaseSoftLimitMB*1024L*1024
        val full=used>softLimit*5/4 || context.filesDir.usableSpace<20*1024*1024
        val resourceStop=p.exclusions.firstOrNull { it.reason=="RESOURCE_LIMIT"&&it.endMs==null }
        if(!full&&resourceStop!=null) {
            db.withTransaction { savePolicy(p.copy(generation=p.generation+1,exclusions=p.exclusions.map { if(it.id==resourceStop.id)it.copy(endMs=System.currentTimeMillis()) else it })) }
            notifyChange();return@withLock
        }
        val filtered = if(full)emptyList() else batch.take(512).map { original ->
            val o=if(original.deviceId.isBlank())original.copy(deviceId=vault.deviceId) else original
            if(used>=softLimit&&o.kind==Kind.NOTICE)o.copy(payload=o.payload.copy(text="",label="通知元数据（存储预算降级）"),quality=o.quality+"STORAGE_DEGRADED") else o
        }.filterNot { used>=softLimit&&it.kind==Kind.PHOTO }.flatMap { filterObservation(it,p) }.filter { WoJson.encodeToString(it).toByteArray().size <= 8192 }
        db.withTransaction {
            if(full)dao.putOperation(OperationRow("storage-budget",System.currentTimeMillis(),"D001_STORAGE_BUDGET",batch.size.toLong()))
            if(full&&resourceStop==null)savePolicy(p.copy(generation=p.generation+1,exclusions=p.exclusions+Exclusion("resource-${System.currentTimeMillis()}",System.currentTimeMillis(),reason="RESOURCE_LIMIT")))
            if (replaceSpan != null && source != null) dao.clearSourceRange(source.name,replaceSpan.start,replaceSpan.end)
            dao.putObservations(filtered.map { ObservationRow(it.id,it.source.name,it.sourceKey,it.kind.name,it.startMs,it.endMs,it.receivedMs,WoJson.encodeToString(it)) })
            filtered.filter { it.kind==Kind.PHOTO }.forEach { dao.putMedia(MediaRow(it.id,it.sourceKey,it.payload.uri,it.startMs)) }
            if (filtered.isNotEmpty()) dao.putDirty(DirtyRow(UUID.randomUUID().toString(),filtered.minOf { it.startMs },filtered.maxOf { maxOf(it.endMs,it.startMs+1) },p.generation,"INGEST"))
            if (source!=null) {
                val old = this@WoRepository.source(source); val now=System.currentTimeMillis()
                dao.putSource(SourceRow(source.name,WoJson.encodeToString(old.copy(cursor=if(full)old.cursor else cursor ?: old.cursor,lastSuccessMs=if(full)old.lastSuccessMs else now,runtime=if(full) "DEGRADED" else "RUNNING",freshness=if(full) "STALE" else if(filtered.isEmpty()) "NO_DATA" else "FRESH",detail=if(full) "空间预算耗尽，原始记录已停止保存" else old.detail))))
                if (coverageSpan!=null) dao.putCoverage(CoverageRow(stableId(source.name,coverageSpan.start,coverageSpan.end),source.name,coverageSpan.start,coverageSpan.end,WoJson.encodeToString(Coverage(source,coverageSpan.start,coverageSpan.end,!full,if(full) "空间预算耗尽，原始观察未保存" else "成功查询范围；不代表全部生活行为"))))
            }
            filtered.map { it.source }.distinct().filter { it!=source }.forEach { s ->
                val old=this@WoRepository.source(s)
                dao.putSource(SourceRow(s.name,WoJson.encodeToString(old.copy(lastSuccessMs=System.currentTimeMillis(),runtime="RUNNING",freshness="FRESH"))))
            }
        }
        notifyChange()
    }
    suspend fun coverage(source: Source,start: Long,end: Long,available: Boolean,detail: String) = mutex.withLock {
        if(end>start) dao.putCoverage(CoverageRow(stableId(source,start,end,available),source.name,start,end,WoJson.encodeToString(Coverage(source,start,end,available,detail))))
        notifyChange()
    }
    suspend fun places(): List<Place> = dao.places().map { WoJson.decodeFromString(it.json) }
    suspend fun namePlace(e: Episode,name: String) = mutex.withLock {
        require(e.lat!=null && e.lon!=null && name.isNotBlank())
        val place = Place(stableId("place",e.lat,e.lon),name.take(80),e.lat!!,e.lon!!)
        dao.putPlace(PlaceRow(place.id,WoJson.encodeToString(place))); notifyChange()
    }
    private suspend fun saveEpisode(e: Episode) {
        val text="${e.title} ${e.detail} ${e.packageName}".lowercase()
        dao.putEpisode(EpisodeRow(e.id,e.revision,e.startMs,e.endMs,e.kind,e.title,text,WoJson.encodeToString(e)))
        dao.removeIndex(e.id); dao.putIndex(SearchRow(episodeId=e.id,tokens=NoticeRules.tokens(text)))
        dao.clearEvidence(e.id); dao.putEvidence(e.evidence.map { EvidenceRow(e.id,it.id,WoJson.encodeToString(it)) })
    }
    /** Read an already-built encrypted snapshot without provider queries or rebuilding. */
    suspend fun cachedDay(date:LocalDate):DayView?=mutex.withLock {
        val p=cachedPolicy;val row=dao.summary(date.toString(),p.archiveZone) ?: return@withLock null
        val stamp=dao.meta(dayKey(date,p.archiveZone))?.value
        // Older releases have no stamp. Only reuse their unrestricted snapshots.
        if(stamp!=snapshotPolicy() && !(stamp==null&&p.exclusions.isEmpty()&&p.excludedApps.isEmpty()))return@withLock null
        WoJson.decodeFromString<DayView>(row.json)
    }
    /** Repeated UI refreshes reuse the same revision until data/policy or the live clock changes. */
    suspend fun currentDay(date:LocalDate):DayView=mutex.withLock {
        val p=cachedPolicy;val now=System.currentTimeMillis();val zone=ZoneId.of(p.archiveZone)
        recentDays.get(dayKey(date,zone.id),changes.value,p.generation,now,date==Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
            ?: buildDay(date,true)
    }
    suspend fun day(date:LocalDate,force:Boolean=true):DayView=mutex.withLock { buildDay(date,force) }
    private suspend fun buildDay(date: LocalDate,force: Boolean): DayView {
        val p=cachedPolicy; val zone=ZoneId.of(p.archiveZone); val w=window(date,zone)
        val cached=dao.summary(date.toString(),zone.id)
        if (cached!=null && !force && p.exclusions.none { it.startMs<w.end&&(it.endMs?:Long.MAX_VALUE)>w.start }) return WoJson.decodeFromString(cached.json)
        val start=(w.start-86_400_000L).coerceAtLeast(0)
        val raw=dao.observations(start,w.end+86_400_000L).map { WoJson.decodeFromString<Observation>(it.json) }
        val ann=dao.annotations().map { WoJson.decodeFromString<Annotation>(it.json) }
        val cs=dao.coverage(w.start,w.end).map { WoJson.decodeFromString<Coverage>(it.json) }
        val places=places(); val now=System.currentTimeMillis()
        val previous=dao.episodes(w.start,w.end).associateBy { it.id }
        val retired=previous.values.map { WoJson.decodeFromString<Episode>(it.json) }.filter { e->e.evidence.any { !it.rawAvailable } }
        val archivedMetrics=if(cached!=null && w.end<now-config.rawRetentionDays*86_400_000L && p.exclusions.none { (it.source==null||it.source in setOf(Source.HEALTH,Source.STEPS))&&it.startMs<w.end&&(it.endMs?:Long.MAX_VALUE)>w.start })WoJson.decodeFromString<DayView>(cached.json).metrics else emptyList()
        val fresh=engine.episodes(raw,p,places,now)
        val view=engine.build(date,p,raw,places,sources(),cs,ann,now,(cached?.revision ?: 0)+1,retired,archivedMetrics,freshEpisodes=fresh)
        db.withTransaction {
            val originals=fresh.filter { it.endMs>=w.start && it.startMs<w.end }
            val validIds=(originals+retired).map { it.id }.toSet()
            val stale=previous.values.filter { it.id !in validIds }.map { it.id }
            stale.forEach { dao.removeIndex(it) };dao.deleteRelations(stale);dao.deleteEpisodes(stale)
            originals.forEach { e ->
                val a=ann.lastOrNull { it.episodeId==e.id }
                val old=previous[e.id] ?: dao.episode(e.id)
                val final=if(a==null) e else e.copy(title=a.title ?: e.title,startMs=a.startMs ?: e.startMs,endMs=a.endMs ?: e.endMs,assertion=if(a.confirmed) Assertion.CONFIRMED else e.assertion,isFavorite=a.favorite ?: e.isFavorite)
                val prior=old?.let { WoJson.decodeFromString<Episode>(it.json) }
                if(prior==null || prior.copy(revision=1)!=final.copy(revision=1)) saveEpisode(final.copy(revision=(old?.revision ?: 0)+1))
            }
            dao.putSummary(SummaryRow(date.toString(),zone.id,view.revision,view.asOfMs,WoJson.encodeToString(view)))
            dao.putMeta(MetaRow(dayKey(date,zone.id),snapshotPolicy()))
            dao.putMetrics(view.metrics.map { MetricRow(date.toString(),zone.id,it.key,WoJson.encodeToString(it)) })
        }
        recentDays.put(dayKey(date,zone.id),view,changes.value,p.generation,System.currentTimeMillis())
        return view
    }
    suspend fun history(): List<DayView> = dao.summaries(90).map { WoJson.decodeFromString(it.json) }
    suspend fun episode(id: String): Episode? = dao.episode(id)?.let { WoJson.decodeFromString(it.json) }
    suspend fun annotate(e: Episode,title: String?,confirmed: Boolean,favorite: Boolean?=null,start: Long?=null,end: Long?=null): String = mutex.withLock {
        val old=dao.episode(e.id) ?: error("片段不存在")
        val current=WoJson.decodeFromString<Episode>(old.json)
        require(current.revision==e.revision) { "C001:片段已更新，请重新打开后修正" }
        val s=start ?: current.startMs; val t=end ?: current.endMs; require(t>=s)
        val a=Annotation(UUID.randomUUID().toString(),e.id,e.revision,title?.take(256),start,end,favorite,confirmed)
        db.withTransaction {
            dao.putAnnotation(AnnotationRow(a.id,a.episodeId,System.currentTimeMillis(),WoJson.encodeToString(a)))
            saveEpisode(current.copy(revision=current.revision+1,title=title ?: current.title,startMs=s,endMs=t,assertion=if(confirmed) Assertion.CONFIRMED else current.assertion,isFavorite=favorite ?: current.isFavorite))
            val z=ZoneId.of(cachedPolicy.archiveZone); val from=Instant.ofEpochMilli(minOf(s,current.startMs)).atZone(z).toLocalDate(); val to=Instant.ofEpochMilli(maxOf(t,current.endMs)).atZone(z).toLocalDate()
            // Keep the snapshot for old days; update its episodes and aggregate activities immediately.
            dao.summaries(1000).filter { it.date in from.toString()..to.toString() }.forEach { row ->
                val d=WoJson.decodeFromString<DayView>(row.json); val changed=WoJson.decodeFromString<Episode>(dao.episode(e.id)!!.json)
                val episodes=d.episodes.filterNot { it.id==e.id } + changed.copy(startMs=maxOf(changed.startMs,d.windowStartMs),endMs=minOf(changed.endMs,d.windowEndMs)).let { if(it.endMs>=it.startMs) listOf(it) else emptyList() }
                val corrected=d.copy(episodes=episodes.sortedBy { it.startMs },revision=d.revision+1,sentences=listOf(Sentence("片段已按本人修正更新，可在时间轴查看依据。")))
                dao.putSummary(row.copy(revision=corrected.revision,json=WoJson.encodeToString(corrected)))
            }
        }; notifyChange(); a.id
    }
    suspend fun undoAnnotation(id:String)=mutex.withLock { dao.undoAnnotation(id); notifyChange() }
    suspend fun preview(start:Long,end:Long,id:String?=null): DeletionPreview = mutex.withLock {
        require(end>start); DeletionPreview(cachedPolicy.generation,start,end,if(id!=null) 1 else dao.episodes(start,end).size,dao.observationCount(start,end),id)
    }
    suspend fun delete(preview:DeletionPreview)=mutex.withLock {
        require(preview.generation==cachedPolicy.generation) { "C001:删除范围已变化，请重新预览" }
        val rows=if(preview.id==null) dao.episodes(preview.startMs,preview.endMs) else listOfNotNull(dao.episode(preview.id))
        val eps=rows.map { WoJson.decodeFromString<Episode>(it.json) }; val ids=eps.map { it.id }; val p=cachedPolicy
        db.withTransaction {
            val xs=if(preview.id==null) listOf(Exclusion(UUID.randomUUID().toString(),preview.startMs,preview.endMs,reason="DELETE")) else eps.flatMap { e -> e.evidence.map { v -> Exclusion(UUID.randomUUID().toString(),0,null,v.source,sourceKey=v.sourceKey,reason="DELETE_EPISODE") } }
            savePolicy(p.copy(generation=p.generation+1,exclusions=p.exclusions+xs))
            val rawIds=if(preview.id==null) dao.observations(preview.startMs,preview.endMs).map { it.id } else eps.flatMap { it.evidence }.map { it.id }
            dao.deleteObservations(rawIds); ids.forEach { dao.removeIndex(it) }; dao.deleteRelations(ids); dao.deleteAnnotations(ids); dao.deleteEpisodes(ids)
            dao.deleteMedia(preview.startMs,preview.endMs)
            val zone=ZoneId.of(p.archiveZone); val from=Instant.ofEpochMilli(preview.startMs).atZone(zone).toLocalDate().toString(); val to=Instant.ofEpochMilli(preview.endMs-1).atZone(zone).toLocalDate().toString()
            dao.deleteSummaries(from,to); dao.deleteMetrics(from,to)
            dao.putOperation(OperationRow(UUID.randomUUID().toString(),System.currentTimeMillis(),"DELETE",ids.size.toLong()))
        }; notifyChange()
    }
    suspend fun search(text:String,from:LocalDate,all:Boolean=false,offset:Int=0):List<Episode> {
        val normalized=text.trim().lowercase().take(200); if(normalized.isEmpty())return emptyList()
        val escaped=normalized.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")
        val zone=ZoneId.of(cachedPolicy.archiveZone); val start=if(all) 0L else window(from,zone).start
        val tokens=NoticeRules.tokens(normalized).split(" ").filter { it.length>=2 }
        if(tokens.isNotEmpty()) {
            val candidates=dao.searchFts(tokens.joinToString(" AND ") { "\"$it\"*" },start,System.currentTimeMillis()+86_400_000L,200,offset)
            val matched=candidates.filter { it.searchText.contains(normalized) }.take(50)
            if(matched.isNotEmpty())return matched.map { WoJson.decodeFromString(it.json) }
        }
        return dao.search("%$escaped%",start,System.currentTimeMillis()+86_400_000L,50,offset).map { WoJson.decodeFromString(it.json) }
    }
    suspend fun maintenance()=mutex.withLock {
        val now=System.currentTimeMillis(); val cutoff=now-config.rawRetentionDays*86_400_000L
        val episodeCutoff=now-config.episodeRetentionDays*86_400_000L
        db.withTransaction {
            dao.expireObservations(now-config.noticeTextDays*86_400_000L,cutoff); dao.expireLog(now-7*86_400_000L)
            dao.episodes(0,now).filter { it.startMs<cutoff || (it.kind=="NOTICE" && it.startMs<now-config.noticeTextDays*86_400_000L) }.forEach { row ->
                val e=WoJson.decodeFromString<Episode>(row.json)
                saveEpisode(e.copy(detail=if(e.kind=="NOTICE") "${e.packageName} · 通知正文已到期" else e.detail,evidence=e.evidence.map { it.copy(rawAvailable=false) }))
            }
            dao.summaries(1000).forEach { row ->
                val d=WoJson.decodeFromString<DayView>(row.json)
                val updated=d.episodes.filter { it.isFavorite||it.endMs>=episodeCutoff }.map { e ->
                    if(e.startMs<cutoff||(e.kind=="NOTICE"&&e.startMs<now-config.noticeTextDays*86_400_000L))e.copy(detail=if(e.kind=="NOTICE") "${e.packageName} · 通知正文已到期" else e.detail,evidence=e.evidence.map { it.copy(rawAvailable=false) }) else e
                }
                if(updated!=d.episodes)dao.putSummary(row.copy(json=WoJson.encodeToString(d.copy(episodes=updated))))
            }
            val expired=dao.episodes(0,episodeCutoff).filter { !WoJson.decodeFromString<Episode>(it.json).isFavorite }.map { it.id }
            expired.forEach { dao.removeIndex(it) };dao.deleteRelations(expired);dao.deleteAnnotations(expired);dao.deleteEpisodes(expired)
        }
        runCatching { db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close() }
        context.cacheDir.listFiles()?.filter { it.lastModified()<now-86_400_000L }?.forEach { it.deleteRecursively() }
        notifyChange()
    }
    suspend fun dirtyDays(): Set<LocalDate> = dao.dirty().flatMap { d ->
        val z=ZoneId.of(cachedPolicy.archiveZone); val a=Instant.ofEpochMilli(d.startMs).atZone(z).toLocalDate(); val b=Instant.ofEpochMilli(d.endMs).atZone(z).toLocalDate()
        generateSequence(a) { if(it<b) it.plusDays(1) else null }.take(16).toList()
    }.toSet()
    suspend fun clearDirty(ids:List<String>)=mutex.withLock { ids.forEach { dao.clearDirty(it) } }
    suspend fun heartbeat()=mutex.withLock { savePolicy(cachedPolicy.copy(lastAliveMs=System.currentTimeMillis())) }
}
