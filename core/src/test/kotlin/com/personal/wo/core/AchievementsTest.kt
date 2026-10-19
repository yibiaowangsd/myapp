package com.personal.wo.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.encodeToString

class AchievementsTest {
    private val date=LocalDate.of(2026,10,1)
    private val zone="Asia/Shanghai"
    private val policy=Policy(archiveZone=zone)
    private fun day(index:Int=0,episodes:List<Episode> = emptyList(),steps:Long?=null):DayView {
        val d=date.plusDays(index.toLong());val w=window(d,ZoneId.of(zone))
        return DayView(d.toString(),zone,w.start,w.end,w.end,"FINAL","测试",emptyList(),emptyList(),listOf(Metric("steps",steps,"步","步数")),episodes)
    }
    private fun e(id:String="a",kind:String="APP",start:Int=60,end:Int=120,index:Int=0,source:Source=Source.USAGE,
        assertion:Assertion=Assertion.RECORDED,pkg:String="com.tencent.weread"):Episode {
        val startMs=day(index).windowStartMs
        return Episode(id,startMs=startMs+start*60_000L,endMs=startMs+end*60_000L,lane=Lane.LIFE,kind=kind,assertion=assertion,title=id,
            packageName=pkg,evidence=listOf(Evidence(id,source,"key-$id","测试依据")))
    }
    private fun evaluate(vararg days:DayView,settings:AchievementSettings=AchievementSettings(),p:Policy=policy)=achievementSnapshot(days.toList(),settings,p)
    private fun AchievementSnapshot.value(metric:AchievementMetric)=items.first{it.goal.metric==metric}.current
    @Test fun cachedContributionsPreserveCrossDayDeduplicationAndFractionalMinutes() {
        val first=day(episodes=listOf(e("reading",start=60,end=61).copy(endMs=day().windowStartMs+60*60_000L+40_000),
            e("photo",kind="PHOTO",start=120,end=120,source=Source.PHOTO).copy(photoUris=listOf("content://same"),isFavorite=true)),steps=1000)
        val second=day(1,episodes=listOf(e("reading-2",index=1,start=60,end=61).copy(endMs=day(1).windowStartMs+60*60_000L+40_000),
            e("photo",index=1,kind="PHOTO",start=120,end=120,source=Source.PHOTO).copy(photoUris=listOf("content://same"),isFavorite=true)),steps=1000)
        val accumulator=AchievementAccumulator(AchievementSettings(),policy)
        listOf(first,second).forEach { d->
            val saved=WoJson.encodeToString(accumulator.contribution(d))
            accumulator.accept(WoJson.decodeFromString<AchievementDayContribution>(saved))
        }
        val result=accumulator.snapshot()
        assertEquals(1L,result.value(AchievementMetric.READING))
        assertEquals(1L,result.value(AchievementMetric.PHOTOS))
        assertEquals(1L,result.value(AchievementMetric.FAVORITES))
        assertEquals(2L,result.value(AchievementMetric.DAYS))
    }
    @Test fun catalogHasThirtyTwoOriginalNamedMedalsWithFourTiers() {
        assertEquals(32,achievementCatalog.size)
        assertEquals(32,achievementCatalog.map{it.id}.toSet().size)
        assertEquals(32,achievementCatalog.map{it.name}.toSet().size)
        achievementCatalog.forEach{it.checked();assertTrue(it.description.isNotBlank());assertTrue(it.rule.isNotBlank())}
        AchievementMetric.entries.forEach{m->assertEquals(listOf(1,2,3,4),achievementCatalog.filter{it.metric==m}.map{it.tier})}
    }
    @Test fun emptyMissingAndZeroDataDoNotUnlock() {
        assertTrue(evaluate(day()).unlocked.isEmpty())
        assertTrue(evaluate(day(steps=0)).unlocked.isEmpty())
        assertTrue(evaluate(day(steps=-50)).unlocked.isEmpty())
    }
    @Test fun openingAnAppBrieflyDoesNotBecomeARecordedDay() {
        assertEquals(0,evaluate(day(episodes=listOf(e(end=61)))).value(AchievementMetric.DAYS))
    }
    @Test fun readingRequiresSelectedPackageAndObservedEvidence() {
        assertEquals(60,evaluate(day(episodes=listOf(e()))).value(AchievementMetric.READING))
        assertEquals(0,evaluate(day(episodes=listOf(e(pkg="com.other.app")))).value(AchievementMetric.READING))
        assertEquals(0,evaluate(day(episodes=listOf(e(source=Source.CALENDAR)))).value(AchievementMetric.READING))
        assertEquals(0,evaluate(day(episodes=listOf(e().copy(evidence=emptyList())))).value(AchievementMetric.READING))
    }
    @Test fun readingOverlapIsUnionedAcrossAppsAndDuplicateRows() {
        val a=e();val b=e("b",start=90,end=150,pkg="com.other.reader")
        val settings=AchievementSettings(readingPackages=setOf("com.tencent.weread","com.other.reader"))
        assertEquals(90,evaluate(day(episodes=listOf(a,a,b)),settings=settings).value(AchievementMetric.READING))
    }
    @Test fun changingReadingAppsRecalculatesInsteadOfKeepingFalseAwards() {
        val d=day(episodes=listOf(e()))
        assertTrue(evaluate(d).items.first{it.goal.id=="reading-1"}.unlocked)
        assertFalse(evaluate(d,settings=AchievementSettings(readingPackages=emptySet())).items.first{it.goal.id=="reading-1"}.unlocked)
    }
    @Test fun futureTailAndCrossDayTimeAreClipped() {
        val d=day();val event=e(start=-30,end=120)
        assertEquals(60,evaluate(d.copy(episodes=listOf(event),asOfMs=d.windowStartMs+60*60_000L)).value(AchievementMetric.READING))
    }
    @Test fun plannedAndClockConflictAreNotAchievements() {
        assertEquals(0,evaluate(day(episodes=listOf(e(assertion=Assertion.PLANNED)))).value(AchievementMetric.READING))
        assertEquals(0,evaluate(day(episodes=listOf(e().copy(qualityFlags=listOf("CLOCK_CONFLICT"))))).value(AchievementMetric.READING))
        assertEquals(0,evaluate(day(episodes=listOf(e(assertion=Assertion.UNKNOWN)))).value(AchievementMetric.DAYS))
    }
    @Test fun readingHonoursExclusionAndExcludedApp() {
        val d=day(episodes=listOf(e()))
        val excluded=policy.copy(exclusions=listOf(Exclusion("exclude",d.windowStartMs+90*60_000L,d.windowStartMs+120*60_000L,reason="USER_RANGE")))
        assertEquals(30,evaluate(d,p=excluded).value(AchievementMetric.READING))
        assertEquals(0,evaluate(d,p=policy.copy(excludedApps=setOf("com.tencent.weread"))).value(AchievementMetric.READING))
    }
    @Test fun sourceKeyDeletionDoesNotCountTheDeletedEvent() {
        val d=day(episodes=listOf(e()))
        val p=policy.copy(exclusions=listOf(Exclusion("deleted",source=Source.USAGE,sourceKey="key-a",reason="DELETE_EPISODE")))
        assertEquals(0,evaluate(d,p=p).value(AchievementMetric.READING))
    }
    @Test fun walkingAndCalendarPlansAreNotExercise() {
        val move=e(kind="MOVE",source=Source.LOCATION,assertion=Assertion.INFERRED)
        val plan=e("plan",kind="EXERCISE",source=Source.CALENDAR,assertion=Assertion.PLANNED)
        assertEquals(0,evaluate(day(episodes=listOf(move,plan))).value(AchievementMetric.EXERCISE))
        assertEquals(60,evaluate(day(episodes=listOf(e(kind="EXERCISE",source=Source.HEALTH)))).value(AchievementMetric.EXERCISE))
    }
    @Test fun overlappingHealthWorkoutsAreNotCountedTwice() {
        val a=e(kind="EXERCISE",source=Source.HEALTH)
        val b=e("b",kind="EXERCISE",start=90,end=150,source=Source.HEALTH)
        assertEquals(90,evaluate(day(episodes=listOf(a,b))).value(AchievementMetric.EXERCISE))
    }
    @Test fun stepsUseOneMergedDailyTotalAndOneLatestDayRevision() {
        val d=day(steps=1200)
        val revised=d.copy(revision=2,metrics=listOf(Metric("steps",1600,"步","步数")))
        assertEquals(1600,evaluate(d,revised,d).value(AchievementMetric.STEPS))
    }
    @Test fun missingStepsStayMissingRatherThanBeingEstimated() {
        val d=day(steps=2000)
        val p=policy.copy(exclusions=listOf(Exclusion("x",d.windowStartMs+60_000L,d.windowStartMs+120_000L,source=Source.STEPS)))
        assertEquals(0,evaluate(d,p=p).value(AchievementMetric.STEPS))
        assertFalse(evaluate(d,p=p).items.first{it.goal.metric==AchievementMetric.STEPS}.hasEvidence)
    }
    @Test fun photosUseMediaIdentityAcrossDaysAndEpisodes() {
        val a=e(kind="PHOTO",start=60,end=60,source=Source.PHOTO).copy(photoUris=listOf("content://media/1","content://media/1"))
        val b=e("b",kind="PHOTO",start=60,end=60,index=1,source=Source.PHOTO).copy(photoUris=listOf("content://media/1","content://media/2"))
        assertEquals(2,evaluate(day(episodes=listOf(a)),day(1,listOf(b))).value(AchievementMetric.PHOTOS))
    }
    @Test fun favoritesAreCurrentAndNotJustAnyVisitedEpisode() {
        val a=e().copy(isFavorite=true)
        assertEquals(1,evaluate(day(episodes=listOf(a,a))).value(AchievementMetric.FAVORITES))
        assertEquals(0,evaluate(day(episodes=listOf(a.copy(isFavorite=false)))).value(AchievementMetric.FAVORITES))
    }
    @Test fun exploreRequiresConfirmationAndDeduplicatesNearbyPlaces() {
        val a=e(kind="STAY",source=Source.LOCATION,assertion=Assertion.CONFIRMED).copy(lat=31.0,lon=121.0)
        val b=a.copy(id="b",lat=31.0001)
        val c=a.copy(id="c",lat=31.01)
        assertEquals(2,evaluate(day(episodes=listOf(a,b,c))).value(AchievementMetric.PLACES))
        assertEquals(0,evaluate(day(episodes=listOf(a.copy(assertion=Assertion.INFERRED)))).value(AchievementMetric.PLACES))
        assertEquals(0,evaluate(day(episodes=listOf(a.copy(lat=Double.NaN)))).value(AchievementMetric.PLACES))
    }
    @Test fun streakUsesCalendarAdjacencyAndKeepsLongestCompletedRun() {
        val days=listOf(0,1,3,4,5,9).map{day(it,steps=1)}
        val s=achievementSnapshot(days,policy=policy)
        assertEquals(6,s.value(AchievementMetric.DAYS));assertEquals(3,s.value(AchievementMetric.STREAK))
        assertEquals(date.plusDays(5).toString(),s.items.first{it.goal.id=="streak-1"}.earnedOn)
    }
    @Test fun collectionIsNotLimitedToNinetyHistoryCards() {
        val accumulator=AchievementAccumulator(AchievementSettings(),policy)
        repeat(101){accumulator.accept(day(it,steps=1))}
        assertEquals(101,accumulator.snapshot().value(AchievementMetric.DAYS))
        assertEquals(date.plusDays(99).toString(),accumulator.snapshot().items.first{it.goal.id=="days-4"}.earnedOn)
    }
    @Test fun archiveTimeZoneDoesNotGetMixed() {
        val d=day(steps=1000)
        assertEquals(1000,evaluate(d,d.copy(zoneId="UTC")).value(AchievementMetric.STEPS))
    }
    @Test fun earnedDateIsTheFirstQualifyingArchiveDay() {
        val s=evaluate(day(episodes=listOf(e(end=80))),day(1,listOf(e("b",index=1,end=80))))
        assertEquals(date.plusDays(1).toString(),s.items.first{it.goal.id=="reading-1"}.earnedOn)
    }
    @Test fun historicalEvidenceRemainsValidAfterRawRetention() {
        val event=e().let{it.copy(evidence=it.evidence.map{v->v.copy(rawAvailable=false)})}
        assertEquals(60,evaluate(day(episodes=listOf(event))).value(AchievementMetric.READING))
    }
    @Test fun customGoalsShareTheSameEvaluatorAndValidateBoundaries() {
        val goal=AchievementGoal("custom-first","自己的约定","留住生活",AchievementMetric.DAYS,1,2,true)
        val s=evaluate(day(steps=1),settings=AchievementSettings(customGoals=listOf(goal)))
        assertEquals(33,s.items.size);assertTrue(s.items.last().unlocked)
        assertThrows(IllegalArgumentException::class.java){goal.copy(target=0).checked()}
        assertThrows(IllegalArgumentException::class.java){goal.copy(name=" ").checked()}
        assertThrows(IllegalArgumentException::class.java){AchievementSettings(customGoals=List(13){goal.copy(id="custom-$it")}).checked()}
        assertThrows(IllegalArgumentException::class.java){AchievementSettings(customGoals=listOf(goal,goal)).checked()}
    }
    @Test fun seenStateSuppressesRepeatedCelebrationAndDemoNeverProducesNewIds() {
        val d=day(steps=1000);val earned=evaluate(d).unlocked.map{it.goal.id}.toSet()
        assertTrue(achievementSnapshot(listOf(d),policy=policy,memory=AchievementMemory(seenIds=earned)).newIds.isEmpty())
        assertTrue(achievementSnapshot(listOf(d),policy=policy,demo=true).newIds.isEmpty())
    }
    @Test fun settingsSerializeAndRoundTripWithoutLosingCustomDescriptions() {
        val goal=AchievementGoal("custom-one","纸上的风","为自己的好奇心留一点时间",AchievementMetric.READING,45,3,true)
        val settings=AchievementSettings(customGoals=listOf(goal),showcaseIds=listOf("custom-one"))
        assertEquals(settings,WoJson.decodeFromString<AchievementSettings>(WoJson.encodeToString(AchievementSettings.serializer(),settings)))
    }
}
