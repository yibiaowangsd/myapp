package com.personal.wo.data

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.personal.wo.core.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

@Serializable private data class CachedAchievementDay(
    val version:Int=1,val revision:Long,val asOfMs:Long,val projectionKey:String,val day:AchievementDayContribution
)
private data class AchievementHeader(val date:String,val revision:Long,val asOfMs:Long)
private data class AchievementInput(val settings:AchievementSettings,val policy:Policy,val headers:List<AchievementHeader>)

/** Achievement settings live inside the existing SQLCipher database, not plaintext preferences. */
class AchievementStore private constructor(
    private val db:WoDatabase,private val mutex:Mutex,private val policyProvider:()->Policy,
    private val retentionDays:Int,private val now:()->Long
) {
    constructor(repo:WoRepository):this(repo.db,repo.mutex,{repo.cachedPolicy},repo.config.episodeRetentionDays,System::currentTimeMillis)
    internal constructor(db:WoDatabase,policy:Policy,retentionDays:Int=730,now:()->Long=System::currentTimeMillis):
        this(db,Mutex(),{policy},retentionDays,now)
    companion object {
        const val SETTINGS_KEY="achievements.settings.v1"
        const val MEMORY_KEY="achievements.memory.v1"
    }
    private suspend fun settingsUnlocked():AchievementSettings = db.dao().meta(SETTINGS_KEY)?.let {
        require(it.value.length<=32_768){"成就设置超过大小限制"}
        WoJson.decodeFromString<AchievementSettings>(it.value).checked()
    } ?: AchievementSettings()
    suspend fun settings():AchievementSettings=mutex.withLock{settingsUnlocked()}

    private fun headers(zone:String,from:String,to:String):List<AchievementHeader> = db.query(SimpleSQLiteQuery(
        "SELECT date,revision,asOfMs FROM daily_summary WHERE zone=? AND date>=? AND date<=? ORDER BY date ASC",
        arrayOf(zone,from,to)
    )).use { c->buildList { while(c.moveToNext())add(AchievementHeader(c.getString(0),c.getLong(1),c.getLong(2))) } }
    private fun policyKey(policy:Policy)=WoJson.encodeToString(policy.copy(lastAliveMs=0))
    suspend fun load():AchievementSnapshot {
        // Read small headers under the repository lock. Decode and aggregate history
        // outside both that lock and the write transaction, then validate the snapshot.
        repeat(3) {
            currentCoroutineContext().ensureActive()
            val asOf=now()
            val policy=mutex.withLock { policyProvider() }
            val today=Instant.ofEpochMilli(asOf).atZone(ZoneId.of(policy.archiveZone)).toLocalDate()
            val from=today.minusDays((retentionDays.coerceIn(1,36500)-1).toLong())
            val input=mutex.withLock { AchievementInput(settingsUnlocked(),policyProvider(),headers(policy.archiveZone,from.toString(),today.toString())) }
            if(input.policy.archiveZone!=policy.archiveZone)return@repeat
            val prefix="achievement.day.v1:${policy.archiveZone}:"
            val cached=db.query(SimpleSQLiteQuery("SELECT `key`,value FROM app_meta WHERE `key`>=? AND `key`<=?",
                arrayOf(prefix+from,prefix+today))).use { c->buildMap { while(c.moveToNext())put(c.getString(0),c.getString(1)) } }
            val projectionKey=stableId(policyKey(input.policy),input.settings.readingPackages.sorted())
            val accumulator=AchievementAccumulator(input.settings,input.policy)
            val updates=mutableListOf<MetaRow>()
            var consistent=true
            input.headers.forEach { header ->
                currentCoroutineContext().ensureActive()
                if(!consistent)return@forEach
                val key=prefix+header.date
                val saved=cached[key]?.let { json->runCatching { WoJson.decodeFromString<CachedAchievementDay>(json) }.getOrNull() }
                val contribution=if(saved!=null&&saved.version==1&&saved.revision==header.revision&&saved.asOfMs==minOf(header.asOfMs,asOf)&&
                    saved.projectionKey==projectionKey&&saved.day.date==header.date&&saved.day.zone==policy.archiveZone) saved.day else {
                    val row=db.dao().summary(header.date,policy.archiveZone)
                    if(row==null||row.revision!=header.revision||row.asOfMs!=header.asOfMs) { consistent=false;return@forEach }
                    val projected=withContext(Dispatchers.Default) {
                        val day=WoJson.decodeFromString<DayView>(row.json)
                        require(day.dayDate==header.date&&day.zoneId==policy.archiveZone){"归档索引与内容不一致"}
                        accumulator.contribution(day.copy(asOfMs=minOf(day.asOfMs,asOf)))
                    }
                    updates+=MetaRow(key,WoJson.encodeToString(CachedAchievementDay(revision=row.revision,asOfMs=minOf(row.asOfMs,asOf),projectionKey=projectionKey,day=projected)))
                    projected
                }
                withContext(Dispatchers.Default){accumulator.accept(contribution)}
            }
            if(!consistent)return@repeat
            val result=mutex.withLock {
                db.withTransaction {
                    if(policyKey(policyProvider())!=policyKey(input.policy)||settingsUnlocked()!=input.settings||
                        headers(policy.archiveZone,from.toString(),today.toString())!=input.headers)return@withTransaction null
                    updates.forEach { db.dao().putMeta(it) }
                    val retained=input.headers.map { prefix+it.date }.toSet()
                    (cached.keys-retained).forEach { db.dao().deleteMetaRange(it,it) }
                    val memory=db.dao().meta(MEMORY_KEY)?.let {
                        require(it.value.length<=16_384){"成就状态超过大小限制"}
                        WoJson.decodeFromString<AchievementMemory>(it.value).also{m->require(m.version==1)}
                    } ?: AchievementMemory()
                    val snapshot=accumulator.snapshot(memory,from.toString(),today.toString())
                    val unlocked=snapshot.unlocked.map{it.goal.id}.toSet()
                    val cleaned=input.settings.copy(showcaseIds=input.settings.showcaseIds.filter{it in unlocked})
                    if(cleaned!=input.settings)db.dao().putMeta(MetaRow(SETTINGS_KEY,WoJson.encodeToString(cleaned)))
                    snapshot.copy(settings=cleaned)
                }
            }
            if(result!=null)return result
        }
        error("归档正在更新，请稍后刷新成就")
    }
    private suspend fun update(transform:(AchievementSettings)->AchievementSettings)=mutex.withLock {
        db.withTransaction {
            val next=transform(settingsUnlocked()).checked()
            val encoded=WoJson.encodeToString(next)
            require(encoded.length<=32_768){"成就设置超过大小限制"}
            db.dao().putMeta(MetaRow(SETTINGS_KEY,encoded))
        }
    }
    suspend fun readingApps(packages:Set<String>)=update{it.copy(readingPackages=packages)}
    suspend fun add(goal:AchievementGoal)=update { old ->
        require(goal.custom&&old.customGoals.none{it.id==goal.id}){"成就已存在"}
        old.copy(customGoals=old.customGoals+goal.checked())
    }
    suspend fun remove(id:String)=update { old ->
        require(old.customGoals.any{it.id==id}){"只能删除自定义成就"}
        old.copy(customGoals=old.customGoals.filterNot{it.id==id},showcaseIds=old.showcaseIds-id)
    }
    suspend fun showcase(id:String)=update { old ->
        old.copy(showcaseIds=if(id in old.showcaseIds)old.showcaseIds-id else {
            require(old.showcaseIds.size<3){"展台最多放3枚徽章，请先取下一枚"};old.showcaseIds+id
        })
    }
    suspend fun acknowledge(ids:Set<String>)=mutex.withLock {
        db.withTransaction {
            val valid=(achievementCatalog+settingsUnlocked().customGoals).map{it.id}.toSet()
            val old=db.dao().meta(MEMORY_KEY)?.let{WoJson.decodeFromString<AchievementMemory>(it.value)} ?: AchievementMemory()
            require(old.version==1)
            db.dao().putMeta(MetaRow(MEMORY_KEY,WoJson.encodeToString(old.copy(seenIds=(old.seenIds+ids).intersect(valid)))))
        }
    }
}
