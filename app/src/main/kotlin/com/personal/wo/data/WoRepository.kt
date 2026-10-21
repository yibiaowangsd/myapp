package com.personal.wo.data

import android.content.Context
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
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

class WoRepository(val db: WoDatabase, val vault: KeyVault, val config: EngineConfig, private val context: Context,
    private val clock:()->Long=System::currentTimeMillis) {
    val changes = MutableStateFlow(0L)
    val mutex = Mutex()
    @Volatile var cachedPolicy = Policy(); private set
    val engine = DayEngine(config)
    private val dao get() = db.dao()
    private val recentDays=RecentDayCache()
    private fun dayKey(date:LocalDate,zone:String)="day.snapshot.v1:$zone:$date"
    private fun snapshotPolicy()="v2:${cachedPolicy.generation}:${config.version}"
    private fun notifyChange() { changes.value += 1 }
    private suspend fun invalidateDays(start:Long,end:Long,neighbors:Boolean=true) {
        val zone=ZoneId.of(cachedPolicy.archiveZone)
        val padding=if(neighbors)1L else 0L
        val from=Instant.ofEpochMilli(start.coerceAtLeast(0)).atZone(zone).toLocalDate().minusDays(padding)
        val to=Instant.ofEpochMilli(maxOf(start,end-1).coerceAtLeast(0)).atZone(zone).toLocalDate().plusDays(padding)
        recentDays.invalidate(window(from,zone).start,window(to,zone).end)
        dao.deleteMetaRange(dayKey(from,zone.id),dayKey(to,zone.id))
    }
    private suspend fun invalidateAllDays() {
        recentDays.clear();dao.deleteMetaRange("day.snapshot.v1:","day.snapshot.v1:\uffff")
        dao.deleteMetaRange("achievement.day.v1:","achievement.day.v1:\uffff")
    }
    private suspend fun sourceAvailabilityChanged(old:SourceState?,next:SourceState) {
        val zone=ZoneId.of(cachedPolicy.archiveZone)
        fun date(s:SourceState?)=s?.lastSuccessMs?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        if(date(old)!=date(next))listOfNotNull(old?.lastSuccessMs,next.lastSuccessMs).forEach { invalidateDays(it,it+1,false) }
    }
    suspend fun initialize() { mutex.withLock {
        recentDays.clear()
        val saved=dao.policy()
        cachedPolicy = saved?.let { WoJson.decodeFromString(it.json) } ?: Policy()
        if(saved==null)dao.putPolicy(PolicyRow(json = WoJson.encodeToString(cachedPolicy)))
        if(dao.meta("engineVersion")?.value!=config.version)dao.putMeta(MetaRow("engineVersion",config.version))
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
        val old=sources().firstOrNull { it.source==state.source }
        if(old!=state) { db.withTransaction { dao.putSource(row);sourceAvailabilityChanged(old,state) };notifyChange() }
    }
    suspend fun probed(fresh:List<SourceState>,expectedGeneration:Long?=null) = mutex.withLock {
        if(expectedGeneration!=null&&expectedGeneration!=cachedPolicy.generation)return@withLock
        val old=sources().associateBy { it.source }
        val changed=fresh.map { s -> old[s.source]?.let { s.copy(lastSuccessMs=it.lastSuccessMs,cursor=it.cursor,freshness=it.freshness) } ?: s }
            .filter { it!=old[it.source] }
        if(changed.isNotEmpty()) {
            db.withTransaction { changed.forEach { dao.putSource(SourceRow(it.source.name,WoJson.encodeToString(it)));sourceAvailabilityChanged(old[it.source],it) } }
            notifyChange()
        }
    }
    suspend fun log(code: String, count: Long = 0) = mutex.withLock { dao.putOperation(OperationRow(UUID.randomUUID().toString(),System.currentTimeMillis(),code,count)) }
    suspend fun ingest(batch: List<Observation>, expectedGeneration: Long, source: Source? = null, cursor: String? = null, coverageSpan: Span? = null, replaceSpan: Span? = null,
        replaceHealthProvider:String?=null,replaceKinds:Set<Kind>?=null) = mutex.withLock {
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
        val incoming=filtered.distinctBy { it.id }
        val prior=if(incoming.isEmpty())emptyMap() else dao.observationsByIds(incoming.map { it.id }).associateBy { it.id }
        // Re-reading a system event is not new evidence. Preserve its original arrival
        // time when its content is unchanged instead of rewriting every overlap pull.
        val changed=incoming.filter { o -> prior[o.id]?.let { old ->
            val previous=WoJson.decodeFromString<Observation>(old.json)
            o.copy(receivedMs=previous.receivedMs)!=previous
        } ?: true }
        val replaced=if(replaceSpan!=null&&source!=null) {
            if(source==Source.HEALTH) {
                require(replaceHealthProvider in setOf(HUAWEI_HEALTH,HEALTH_CONNECT)) { "Health snapshots must identify their provider" }
                dao.sourceObservations(source.name,replaceSpan.start,replaceSpan.end).filter { row ->
                    val o=WoJson.decodeFromString<Observation>(row.json)
                    healthProvider(o)==replaceHealthProvider&&(replaceKinds==null||o.kind in replaceKinds)
                }
            } else dao.sourceObservations(source.name,replaceSpan.start,replaceSpan.end)
        } else emptyList()
        val incomingIds=incoming.map { it.id }.toSet()
        val removed=replaced.filter { it.id !in incomingIds }
        db.withTransaction {
            if(full)dao.putOperation(OperationRow("storage-budget",System.currentTimeMillis(),"D001_STORAGE_BUDGET",batch.size.toLong()))
            if(full&&resourceStop==null)savePolicy(p.copy(generation=p.generation+1,exclusions=p.exclusions+Exclusion("resource-${System.currentTimeMillis()}",System.currentTimeMillis(),reason="RESOURCE_LIMIT")))
            if(removed.isNotEmpty())dao.deleteObservations(removed.map { it.id })
            if(changed.isNotEmpty())dao.putObservations(changed.map { ObservationRow(it.id,it.source.name,it.sourceKey,it.kind.name,it.startMs,it.endMs,it.receivedMs,WoJson.encodeToString(it)) })
            changed.filter { it.kind==Kind.PHOTO }.forEach { dao.putMedia(MediaRow(it.id,it.sourceKey,it.payload.uri,it.startMs)) }
            val spans=changed.map { Span(it.startMs,maxOf(it.endMs,it.startMs+1)) }+
                (removed+changed.mapNotNull { prior[it.id] }).map { Span(it.startMs,maxOf(it.endMs,it.startMs+1)) }
            if(spans.isNotEmpty()) {
                val start=spans.minOf { it.start };val end=spans.maxOf { it.end }
                dao.putDirty(DirtyRow(UUID.randomUUID().toString(),start,end,p.generation,if(replaceSpan!=null)"RECONCILE" else "INGEST"))
                invalidateDays(start,end)
            }
            if (source!=null) {
                val old = this@WoRepository.source(source); val now=System.currentTimeMillis()
                val next=old.copy(cursor=if(full)old.cursor else cursor ?: old.cursor,lastSuccessMs=if(full)old.lastSuccessMs else now,runtime=if(full) "DEGRADED" else "RUNNING",freshness=if(full) "STALE" else if(filtered.isEmpty()) "NO_DATA" else "FRESH",detail=if(full) "空间预算耗尽，原始记录已停止保存" else old.detail)
                dao.putSource(SourceRow(source.name,WoJson.encodeToString(next)));sourceAvailabilityChanged(old,next)
                if (coverageSpan!=null) {
                    val row=CoverageRow(stableId(source.name,coverageSpan.start,coverageSpan.end),source.name,coverageSpan.start,coverageSpan.end,WoJson.encodeToString(Coverage(source,coverageSpan.start,coverageSpan.end,!full,if(full) "空间预算耗尽，原始观察未保存" else "成功查询范围；不代表全部生活行为")))
                    if(dao.coverageById(row.id)!=row) { dao.putCoverage(row);invalidateDays(row.startMs,row.endMs,false) }
                }
            }
            filtered.map { it.source }.distinct().filter { it!=source }.forEach { s ->
                val old=this@WoRepository.source(s)
                val next=old.copy(lastSuccessMs=System.currentTimeMillis(),runtime="RUNNING",freshness="FRESH")
                dao.putSource(SourceRow(s.name,WoJson.encodeToString(next)));sourceAvailabilityChanged(old,next)
            }
        }
        notifyChange()
    }
    /** V1-compatible provider cleanup. Never delete phone or Health Connect rows. */
    suspend fun retainHuaweiHealth(kinds:Set<Kind>)=mutex.withLock {
        val raw=dao.healthObservations().filter { row ->
            val o=WoJson.decodeFromString<Observation>(row.json)
            healthProvider(o)==HUAWEI_HEALTH&&o.kind !in kinds
        }
        val episodes=dao.healthEpisodes().filter { row ->
            val e=WoJson.decodeFromString<Episode>(row.json)
            HUAWEI_HEALTH in e.qualityFlags&&when(e.kind) { "STEPS"->Kind.HEALTH_STEPS;"SLEEP"->Kind.SLEEP;else->Kind.EXERCISE } !in kinds
        }
        val removedIds=raw.map { it.id }.toSet()
        val summaries=dao.summaries(Int.MAX_VALUE).filter { row ->
            val day=WoJson.decodeFromString<DayView>(row.json)
            day.metrics.any { m->m.evidenceIds.any { it in removedIds } }||day.episodes.any { e->
                HUAWEI_HEALTH in e.qualityFlags&&when(e.kind) { "STEPS"->Kind.HEALTH_STEPS;"SLEEP"->Kind.SLEEP;else->Kind.EXERCISE } !in kinds
            }
        }
        if(raw.isEmpty()&&episodes.isEmpty()&&summaries.isEmpty())return@withLock
        db.withTransaction {
            dao.deleteObservations(raw.map { it.id })
            val ids=episodes.map { it.id }
            ids.forEach { dao.removeIndex(it);dao.clearEvidence(it) }
            dao.deleteRelations(ids);dao.deleteAnnotations(ids);dao.deleteEpisodes(ids)
            summaries.forEach { row->dao.deleteSummaries(row.date,row.date);dao.deleteMetrics(row.date,row.date) }
            (raw.map { Span(it.startMs,it.endMs) }+episodes.map { Span(it.startMs,it.endMs) }).forEach {
                dao.putDirty(DirtyRow(UUID.randomUUID().toString(),it.start,it.end,cachedPolicy.generation,"HUAWEI_REVOKE"))
            }
        }
        invalidateAllDays();notifyChange()
    }
    suspend fun coverage(source: Source,start: Long,end: Long,available: Boolean,detail: String) = mutex.withLock {
        if(end>start) {
            val row=CoverageRow(stableId(source,start,end,available),source.name,start,end,WoJson.encodeToString(Coverage(source,start,end,available,detail)))
            if(dao.coverageById(row.id)!=row)db.withTransaction { dao.putCoverage(row);invalidateDays(start,end,false) }
        }
        notifyChange()
    }
    suspend fun places(): List<Place> = dao.places().map { WoJson.decodeFromString(it.json) }
    suspend fun namePlace(e: Episode,name: String) = mutex.withLock {
        require(e.lat!=null && e.lon!=null && name.isNotBlank())
        val place = Place(stableId("place",e.lat,e.lon),name.take(80),e.lat!!,e.lon!!)
        db.withTransaction { dao.putPlace(PlaceRow(place.id,WoJson.encodeToString(place)));invalidateAllDays() }; notifyChange()
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
        val p=cachedPolicy;val now=clock();val zone=ZoneId.of(p.archiveZone)
        val key=dayKey(date,zone.id)
        val cached=recentDays.get(key,0,p.generation,now,false) ?: run {
            val row=dao.summary(date.toString(),zone.id)
            if(row!=null&&dao.meta(key)?.value==snapshotPolicy())WoJson.decodeFromString<DayView>(row.json) else null
        }
        if(cached!=null) {
            val until=minOf(now,cached.windowEndMs).coerceAtLeast(cached.windowStartMs)
            val view=if(until<cached.asOfMs||(cached.asOfMs<cached.windowEndMs&&now-cached.asOfMs>=30_000))advanceDayClock(cached,p,now) else cached
            if(view!=null) { recentDays.put(key,view,0,p.generation,now);return@withLock view }
        }
        buildDay(date,true)
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
        val places=places(); val now=clock()
        val previous=dao.episodes(w.start,w.end).associateBy { it.id }
        val rawIds=raw.map { it.id }.toSet()
        val retired=previous.values.map { WoJson.decodeFromString<Episode>(it.json) }.filter { e ->
            e.evidence.any { !it.rawAvailable } || (e.kind=="PHOTO"&&e.endMs<now-config.rawRetentionDays*86_400_000L&&e.evidence.none { it.id in rawIds })
        }.map { e -> if(e.kind=="PHOTO"&&e.evidence.none { it.id in rawIds })e.copy(evidence=e.evidence.map { it.copy(rawAvailable=false) }) else e }
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
        recentDays.put(dayKey(date,zone.id),view,0,p.generation,clock())
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
            invalidateDays(minOf(s,current.startMs),maxOf(t,current.endMs)+1)
        }; notifyChange(); a.id
    }
    suspend fun undoAnnotation(id:String)=mutex.withLock { db.withTransaction { dao.undoAnnotation(id);invalidateAllDays() }; notifyChange() }
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
            dao.deleteMetaRange("achievement.day.v1:","achievement.day.v1:\uffff")
            dao.putOperation(OperationRow(UUID.randomUUID().toString(),System.currentTimeMillis(),"DELETE",ids.size.toLong()))
        }; notifyChange()
    }
    suspend fun search(text:String,from:LocalDate,all:Boolean=false,offset:Int=0,filter:String="ALL"):List<Episode> {
        val zone=ZoneId.of(cachedPolicy.archiveZone)
        val request=memorySearch(text,LocalDate.now(zone),zone)
        val start=request.window?.start ?: if(all)0L else window(from,zone).start
        val end=request.window?.end ?: clock()+86_400_000L
        val clauses=mutableListOf("e.startMs>=?","e.startMs<?")
        val args=mutableListOf<Any>(start,end)
        if(request.text.isNotBlank()) {
            val tokens=NoticeRules.tokens(request.text).split(" ").filter { it.length>=2 }.distinct()
            if(tokens.isNotEmpty()) {
                // Keep FTS prefix matches and literal substrings in one ordered page.
                clauses+="(e.id IN (SELECT episodeId FROM event_search WHERE tokens MATCH ?) OR e.searchText LIKE ? ESCAPE '\\')"
                args+=tokens.joinToString(" AND ") { "\"${it.replace("\"", "\"\"")}\"*" }
                args+=memoryLikePattern(request.text)
            } else {
                clauses+="e.searchText LIKE ? ESCAPE '\\'"
                args+=memoryLikePattern(request.text)
            }
        }
        when(filter) {
            "PHOTO"->clauses+="e.kind='PHOTO'"
            "FAVORITE"->{clauses+="e.json LIKE ?";args+="%\"isFavorite\":true%"}
            "PLACE"->clauses+="e.kind IN ('STAY','MOVE')"
            "APP"->clauses+="e.kind='APP'"
        }
        args+=50;args+=offset
        val query=SimpleSQLiteQuery("SELECT e.json FROM episode e WHERE "+clauses.joinToString(" AND ")+" ORDER BY e.startMs DESC,e.id ASC LIMIT ? OFFSET ?",args.toTypedArray())
        return db.openHelper.readableDatabase.query(query).use { cursor ->
            buildList { while(cursor.moveToNext())add(WoJson.decodeFromString<Episode>(cursor.getString(0))) }
        }
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
                val updated=d.episodes.filter { it.kind=="PHOTO"||it.isFavorite||it.endMs>=episodeCutoff }.map { e ->
                    if(e.startMs<cutoff||(e.kind=="NOTICE"&&e.startMs<now-config.noticeTextDays*86_400_000L))e.copy(detail=if(e.kind=="NOTICE") "${e.packageName} · 通知正文已到期" else e.detail,evidence=e.evidence.map { it.copy(rawAvailable=false) }) else e
                }
                if(updated!=d.episodes)dao.putSummary(row.copy(revision=row.revision+1,json=WoJson.encodeToString(d.copy(episodes=updated,revision=row.revision+1))))
            }
            val expired=dao.episodes(0,episodeCutoff).filter { it.kind!="PHOTO"&&!WoJson.decodeFromString<Episode>(it.json).isFavorite }.map { it.id }
            expired.forEach { dao.removeIndex(it) };dao.deleteRelations(expired);dao.deleteAnnotations(expired);dao.deleteEpisodes(expired)
        }
        runCatching { db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close() }
        context.cacheDir.listFiles()?.filter { it.lastModified()<now-86_400_000L }?.forEach { it.deleteRecursively() }
        invalidateAllDays();notifyChange()
    }
    suspend fun dirtyDays(): Set<LocalDate> = dao.dirty().flatMap { d ->
        val z=ZoneId.of(cachedPolicy.archiveZone); val a=Instant.ofEpochMilli(d.startMs).atZone(z).toLocalDate(); val b=Instant.ofEpochMilli(d.endMs).atZone(z).toLocalDate()
        generateSequence(a) { if(it<b) it.plusDays(1) else null }.take(16).toList()
    }.toSet()
    suspend fun clearDirty(ids:List<String>)=mutex.withLock { ids.forEach { dao.clearDirty(it) } }
    suspend fun heartbeat()=mutex.withLock { savePolicy(cachedPolicy.copy(lastAliveMs=System.currentTimeMillis())) }
}
