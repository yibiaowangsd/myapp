package com.personal.wo.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DayChaptersTest {
    private val day=demoDay(LocalDate.of(2026,10,15),"Asia/Shanghai")
    private fun event(id:String,start:Int,end:Int=start,kind:String="APP")=Episode(id,
        startMs=day.windowStartMs+start*60_000L,endMs=day.windowStartMs+end*60_000L,
        lane=Lane.DIGITAL,kind=kind,title=id,assertion=Assertion.RECORDED,packageName="app.$id")
    @Test fun manyShortOpeningsBecomeOnePeriodAndRemainInDetails() {
        val entries=(0..39).map{event("brief-$it",400+it*2,401+it*2).copy(packageName="same.app")}
        val chapter=dayChapters(day.copy(episodes=entries)).single()
        assertEquals("上午",chapter.part.label)
        assertTrue(chapter.apps.isEmpty())
        assertEquals(entries,chapter.part.episodes)
        assertEquals(40,chapter.shortAppCount)
        assertFalse(chapter.summary.contains("brief"))
    }
    @Test fun thresholdQualifiesEachSessionInclusivelyBeforeGrouping() {
        val entries=listOf(event("long",500,510),event("short",520,529),event("short2",540,549))
        val d=day.copy(episodes=entries)
        assertEquals(listOf("long"),dayChapters(d).single().apps.map{it.episode.id})
        assertEquals(3,dayChapters(d,5).single().apps.size)
        assertTrue(dayChapters(d,20).single().apps.isEmpty())
        assertEquals(10,checkedHomeAppMinutes(-1))
        assertEquals(entries,dayChapters(d,30).single().part.episodes)
    }
    @Test fun qualifyingCrossNoonSessionSplitsOnlyItsObservedDuration() {
        val e=event("crossing",715,735)
        val chapters=dayChapters(day.copy(episodes=listOf(e)))
        assertEquals(listOf("上午","下午"),chapters.map{it.part.label})
        assertEquals(listOf(5*60_000L,15*60_000L),chapters.map{it.apps.single().durationMs})
        assertTrue(chapters.all{it.part.episodes.single()==e})
    }
    @Test fun sameAppOverlapsAreUnionedAndRealGapsAreNotCounted() {
        val entries=listOf(event("one",500,530),event("overlap",510,540),event("later",570,590)).map{it.copy(packageName="one.app")}
        val app=dayChapters(day.copy(episodes=entries)).single().apps.single()
        assertEquals(60*60_000L,app.durationMs)
        assertEquals(2,app.spans.size)
        assertEquals(30*60_000L,app.spans[1].start-app.spans[0].end)
    }
    @Test fun cutoffPlansClockConflictsAndUnknownNeverInventLongUsage() {
        val e=event("live",500,540)
        val d=day.copy(asOfMs=day.windowStartMs+505*60_000L,episodes=listOf(e,
            e.copy(id="plan",assertion=Assertion.PLANNED),e.copy(id="unknown",assertion=Assertion.UNKNOWN),
            e.copy(id="conflict",qualityFlags=listOf("CLOCK_CONFLICT")),event("future",600,650)))
        val chapter=dayChapters(d).single()
        assertTrue(chapter.apps.isEmpty());assertEquals(listOf(e),chapter.part.episodes)
        assertEquals(d.asOfMs,chapter.part.endMs)
    }
    @Test fun photosStayInTheirTimePeriodAndEveryDistinctUriIsReachable() {
        val morning=event("morning-photo",600,kind="PHOTO").copy(photoUris=listOf("content://one","content://two"))
        val afternoon=event("afternoon-photo",800,kind="PHOTO").copy(photoUris=listOf("content://three","content://three"))
        val chapters=dayChapters(day.copy(episodes=listOf(morning,afternoon)))
        assertEquals(listOf("content://one","content://two"),chapters[0].photos.map{it.uri})
        assertEquals(listOf("content://three"),chapters[1].photos.map{it.uri})
        assertEquals(morning,chapters[0].photos.first().episode)
    }
    @Test fun stepsAcrossPartsAreNotSplitAndInferencesStayQualified() {
        val step=event("step",300,800,"STEPS").copy(amount=1234)
        val stay=event("stay",500,620,"STAY").copy(title="办公室附近停留",assertion=Assertion.INFERRED)
        val chapters=dayChapters(day.copy(episodes=listOf(step,stay)))
        assertEquals(1,chapters.count{it.summary.contains("1234步")})
        assertTrue(chapters.first().summary.contains("推测"))
    }
    @Test fun localDayBoundariesHandleDaylightSavingsAndScrubbing() {
        for(date in listOf(LocalDate.of(2026,3,8),LocalDate.of(2026,11,1))) {
            val zone=ZoneId.of("America/New_York");val w=window(date,zone)
            val start=date.atTime(5,55).atZone(zone).toInstant().toEpochMilli()
            val e=event("edge",0).copy(startMs=start,endMs=start+20*60_000L)
            val chapters=dayChapters(day.copy(dayDate=date.toString(),zoneId=zone.id,windowStartMs=w.start,windowEndMs=w.end,asOfMs=w.end,episodes=listOf(e)))
            assertEquals(listOf("凌晨","上午"),chapters.map{it.part.label})
            assertEquals(20*60_000L,chapters.sumOf{it.apps.single().durationMs})
            assertEquals(1,nearestChapter(chapters,start+10*60_000L))
            assertEquals(0,nearestChapter(chapters,w.start))
        }
        assertEquals(-1,nearestChapter(emptyList(),0))
    }
}
