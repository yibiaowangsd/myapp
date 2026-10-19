package com.personal.wo

import android.app.Application
import androidx.room.Room
import com.personal.wo.core.*
import com.personal.wo.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/** Tests real Room queries and transactions; not a substitute for SQLCipher device tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class AchievementStoreTest {
    private lateinit var db:WoDatabase
    private val queries=CopyOnWriteArrayList<String>()
    private val today=LocalDate.of(2026,10,16)
    private val policy=Policy(archiveZone="Asia/Shanghai")
    private val now=window(today,ZoneId.of(policy.archiveZone)).start+12*3_600_000L
    private fun store()=AchievementStore(db,policy,730){now}
    @Before fun open() {
        db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),WoDatabase::class.java).allowMainThreadQueries()
            .setQueryCallback({sql,_->queries.add(sql)},Executor{it.run()}).build()
    }
    @After fun close(){db.close()}
    private suspend fun insert(date:LocalDate,steps:Long=1000,zone:String=policy.archiveZone) {
        val w=window(date,ZoneId.of(zone))
        val day=DayView(date.toString(),zone,w.start,w.end,minOf(w.end,now),"FINAL","测试记录",emptyList(),emptyList(),listOf(Metric("steps",steps,"步","日步数")),emptyList())
        db.dao().putSummary(SummaryRow(date.toString(),zone,1,day.asOfMs,WoJson.encodeToString(day)))
    }
    private fun AchievementSnapshot.value(metric:AchievementMetric)=items.first{it.goal.metric==metric}.current
    @Test fun pagedArchiveIncludesMoreThanNinetyDaysButNotOtherZonesFutureOrExpiredDates()=runBlocking {
        repeat(101){insert(today.minusDays(it.toLong()))}
        insert(today,50_000,"UTC")
        insert(today.plusDays(1),90_000)
        insert(today.minusDays(730),90_000)
        val result=store().load()
        assertEquals(101L,result.value(AchievementMetric.DAYS))
        assertEquals(101_000L,result.value(AchievementMetric.STEPS))
        assertEquals(today.minusDays(729).toString(),result.fromDate)
        assertEquals(today.toString(),result.toDate)
    }
    @Test fun customGoalsAndReadingConfigurationPersistAcrossStoreInstances()=runBlocking {
        val goal=AchievementGoal("custom-stay","自己的日常","给每一天一份肯定",AchievementMetric.DAYS,1,2,true)
        store().add(goal);store().readingApps(setOf("com.example.reader"));insert(today)
        val reloaded=store().load()
        assertEquals(listOf(goal),reloaded.settings.customGoals)
        assertEquals(setOf("com.example.reader"),reloaded.settings.readingPackages)
        assertTrue(reloaded.items.last().unlocked)
        assertNotNull(db.dao().meta(AchievementStore.SETTINGS_KEY))
    }
    @Test fun acknowledgedMedalsDoNotBecomeNewEveryTimeThePageIsOpened()=runBlocking {
        insert(today)
        val ids=store().load().newIds
        assertTrue(ids.isNotEmpty())
        store().acknowledge(ids)
        assertTrue(store().load().newIds.isEmpty())
    }
    @Test fun removingEvidenceRelocksProgressAndClearsStaleShowcaseSlots()=runBlocking {
        insert(today)
        store().showcase("days-1")
        assertEquals(listOf("days-1"),store().load().settings.showcaseIds)
        db.dao().deleteSummaries(today.toString(),today.toString())
        val result=store().load()
        assertFalse(result.items.first{it.goal.id=="days-1"}.unlocked)
        assertTrue(result.settings.showcaseIds.isEmpty())
        assertTrue(store().settings().showcaseIds.isEmpty())
    }
    @Test fun invalidChangesAreRejectedWithoutReplacingValidSettings()=runBlocking {
        store().readingApps(setOf("com.example.reader"))
        try {store().readingApps(setOf("not-a-package"));fail("Invalid package must be rejected")}
        catch(expected:IllegalArgumentException){ }
        assertEquals(setOf("com.example.reader"),store().settings().readingPackages)
        try {store().remove("days-1");fail("Preset medal must not be deletable")}
        catch(expected:IllegalArgumentException){ }
    }
    @Test fun unknownMetadataVersionIsReportedAndPreserved()=runBlocking {
        val encoded=WoJson.encodeToString(AchievementSettings(version=9))
        db.dao().putMeta(MetaRow(AchievementStore.SETTINGS_KEY,encoded))
        try {store().load();fail("Unknown version must not silently reset")}
        catch(expected:IllegalArgumentException){ }
        assertEquals(encoded,db.dao().meta(AchievementStore.SETTINGS_KEY)?.value)
    }
    @Test fun deletingACustomGoalDoesNotDeleteAnyLifeArchive()=runBlocking {
        insert(today)
        val goal=AchievementGoal("custom-a","第一天","",AchievementMetric.DAYS,1,1,true)
        store().add(goal);store().showcase(goal.id);store().remove(goal.id)
        val result=store().load()
        assertEquals(32,result.items.size)
        assertTrue(result.settings.customGoals.isEmpty())
        assertNotNull(db.dao().summary(today.toString(),policy.archiveZone))
        assertEquals(1000L,result.value(AchievementMetric.STEPS))
    }
    @Test fun unchangedArchivesUseOnlyHeadersAndCachedContributions()=runBlocking {
        repeat(101){insert(today.minusDays(it.toLong()))}
        val first=store().load();queries.clear()
        assertEquals(first,store().load())
        assertTrue(queries.none{it.contains("SELECT * FROM daily_summary",true)})
    }
    @Test fun onlyTheChangedArchiveDayIsDecodedAndItsCorrectedTotalIsUsed()=runBlocking {
        repeat(10){insert(today.minusDays(it.toLong()))};store().load()
        val row=db.dao().summary(today.toString(),policy.archiveZone)!!
        val day=WoJson.decodeFromString<DayView>(row.json)
        val corrected=day.copy(revision=2,metrics=listOf(Metric("steps",500,"步","修正后的步数")))
        db.dao().putSummary(row.copy(revision=2,json=WoJson.encodeToString(corrected)));queries.clear()
        assertEquals(9_500L,store().load().value(AchievementMetric.STEPS))
        assertEquals(1,queries.count{it.contains("SELECT * FROM daily_summary",true)})
    }
    @Test fun changedExclusionsInvalidateDerivedDayEvidence()=runBlocking {
        insert(today);store().load()
        val span=window(today,ZoneId.of(policy.archiveZone))
        val excluded=policy.copy(generation=1,exclusions=listOf(Exclusion("pause",span.start,span.end)))
        val snapshot=AchievementStore(db,excluded,730){now}.load()
        assertEquals(0L,snapshot.value(AchievementMetric.STEPS))
        assertFalse(snapshot.items.first{it.goal.id=="steps-1"}.hasEvidence)
    }
}
