package com.personal.wo.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.time.LocalDate

@Serializable data class SharedDayEntry(val date:String,val path:String,val hash:String)
@Serializable data class SharedDataIndex(
    val schemaVersion:Int=1,val source:String="wo-life-records",val archiveZone:String,
    val updatedAtMs:Long,val days:List<SharedDayEntry>
)
data class SharedSyncResult(val uploaded:Int,val pending:Int,val total:Int)
interface SharedFileStore {
    suspend fun requirePrivate()
    suspend fun read(path:String):String?
    suspend fun write(path:String,text:String)
    suspend fun delete(path:String)
}

fun sharedDayPath(date:String):String="wo/days/${LocalDate.parse(date)}.json"

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

/** Index is published last. A partial upload remains retryable and never advertises a missing new day. */
class SharedDataPublisher(private val store:SharedFileStore,private val now:()->Long=System::currentTimeMillis) {
    suspend fun publish(dates:List<String>,zone:String,load:suspend(String)->DayView,maxChanges:Int=48):SharedSyncResult {
        require(maxChanges>0)
        store.requirePrivate()
        val original=store.read("wo/index.json")?.let { WoJson.decodeFromString<SharedDataIndex>(it) }
        require(original==null||(original.schemaVersion==1&&original.source=="wo-life-records")) { "数据目录格式不兼容" }
        val existing=original?.days.orEmpty().associateBy { it.date }
        require(existing.values.all { it.path==sharedDayPath(it.date) }) { "数据索引中的日期路径不正确" }
        val canonical=dates.distinct().sortedDescending();canonical.forEach { LocalDate.parse(it) }
        val entries=existing.filterKeys { it in canonical }.toMutableMap()
        // Remove withdrawn dates from the live branch; Git history remains subject to repository retention.
        existing.filterKeys { it !in canonical }.values.forEach { store.delete(it.path) }
        var uploaded=0;var pending=0
        for(date in canonical) {
            val day=load(date);require(day.dayDate==date&&day.zoneId==zone)
            val payload=sharedDay(day)
            val stable=JsonObject(payload.filterKeys { it!="asOfMs" }).toString()
            val hash=MessageDigest.getInstance("SHA-256").digest(stable.toByteArray()).lowerHex()
            if(existing[date]?.hash==hash)continue
            if(uploaded>=maxChanges) { entries.remove(date);pending++;continue }
            val path=sharedDayPath(date);store.write(path,payload.toString())
            entries[date]=SharedDayEntry(date,path,hash);uploaded++
        }
        val days=entries.values.sortedByDescending { it.date }
        if(original==null||original.archiveZone!=zone||original.days!=days) {
            val index=SharedDataIndex(archiveZone=zone,updatedAtMs=now(),days=days)
            store.write("wo/index.json",WoJson.encodeToString(SharedDataIndex.serializer(),index))
        }
        return SharedSyncResult(uploaded,pending,days.size)
    }
}
