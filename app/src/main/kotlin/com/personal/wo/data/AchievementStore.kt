package com.personal.wo.data

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.personal.wo.core.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.time.ZoneId

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

    suspend fun load():AchievementSnapshot=mutex.withLock {
        db.withTransaction {
            val settings=settingsUnlocked()
            val policy=policyProvider()
            val asOf=now()
            val today=Instant.ofEpochMilli(asOf).atZone(ZoneId.of(policy.archiveZone)).toLocalDate()
            val from=today.minusDays((retentionDays.coerceIn(1,36500)-1).toLong())
            val accumulator=AchievementAccumulator(settings,policy)
            var after=from.minusDays(1).toString()
            // Keyset paging in the active archive zone. Do not reuse the 90-day history UI,
            // offset across duplicate zones, or keep years of full DayViews in memory.
            while(true) {
                currentCoroutineContext().ensureActive()
                val page=db.query(SimpleSQLiteQuery(
                    "SELECT date,json FROM daily_summary WHERE zone=? AND date>? AND date<=? ORDER BY date ASC LIMIT 24",
                    arrayOf(policy.archiveZone,after,today.toString())
                )).use { c -> buildList { while(c.moveToNext())add(c.getString(0) to c.getString(1)) } }
                if(page.isEmpty())break
                page.forEach { (date,json) ->
                    currentCoroutineContext().ensureActive()
                    val day=WoJson.decodeFromString<DayView>(json)
                    require(day.dayDate==date&&day.zoneId==policy.archiveZone){"归档索引与内容不一致"}
                    accumulator.accept(day.copy(asOfMs=minOf(day.asOfMs,asOf)))
                }
                after=page.last().first
            }
            val memory=db.dao().meta(MEMORY_KEY)?.let {
                require(it.value.length<=16_384){"成就状态超过大小限制"}
                WoJson.decodeFromString<AchievementMemory>(it.value).also{m->require(m.version==1)}
            } ?: AchievementMemory()
            val snapshot=accumulator.snapshot(memory,from.toString(),today.toString())
            // Deletions/changed evidence may relock a medal. Remove stale display slots
            // in the same transaction so invisible pins cannot block future choices.
            val unlocked=snapshot.unlocked.map{it.goal.id}.toSet()
            val cleaned=settings.copy(showcaseIds=settings.showcaseIds.filter{it in unlocked})
            if(cleaned!=settings)db.dao().putMeta(MetaRow(SETTINGS_KEY,WoJson.encodeToString(cleaned)))
            snapshot.copy(settings=cleaned)
        }
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
