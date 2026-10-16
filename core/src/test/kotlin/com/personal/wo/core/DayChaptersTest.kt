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
    @Test fun shortOpeningsDoNotInventAnEventAndRawDayRemainsIntact() {
        val entries=(0..39).map{event("brief-$it",400+it*2,401+it*2).copy(packageName="same.app")}
        val d=day.copy(episodes=entries)
        assertTrue(dayChapters(d).isEmpty())
        assertEquals(entries,recapEpisodes(d))
    }
    @Test fun thresholdQualifiesEachObservedSessionInclusivelyBeforeGrouping() {
        val entries=listOf(event("long",500,510),event("short",520,529),event("short2",540,549))
        val d=day.copy(episodes=entries)
        assertEquals(listOf("long"),dayChapters(d).flatMap{it.apps}.map{it.episode.id})
        assertEquals(3,dayChapters(d,5).flatMap{it.apps}.size)
        assertTrue(dayChapters(d,20).isEmpty())
        assertEquals(10,checkedHomeAppMinutes(-1))
        assertEquals(entries,recapEpisodes(d))
    }
    @Test fun crossNoonEventKeepsOneActualIntervalWithoutClockBuckets() {
        val e=event("crossing",715,735)
        val chapter=dayChapters(day.copy(episodes=listOf(e))).single()
        assertEquals(e.startMs,chapter.part.startMs);assertEquals(e.endMs,chapter.part.endMs)
        assertEquals(20*60_000L,chapter.apps.single().durationMs)
        assertEquals("crossing",chapter.part.label)
    }
    @Test fun appOverlapsAreUnionedAndGapsAreNeverCounted() {
        val entries=listOf(event("one",500,530),event("overlap",510,540),event("nearby",545,565)).map{it.copy(packageName="one.app")}
        val app=dayChapters(day.copy(episodes=entries)).single().apps.single()
        assertEquals(60*60_000L,app.durationMs);assertEquals(2,app.spans.size)
        assertEquals(5*60_000L,app.spans[1].start-app.spans[0].end)
    }
    @Test fun cutoffPlansClockConflictsAndUnknownNeverInventLongUsage() {
        val e=event("live",500,540)
        val d=day.copy(asOfMs=day.windowStartMs+505*60_000L,episodes=listOf(e,
            e.copy(id="plan",assertion=Assertion.PLANNED),e.copy(id="unknown",assertion=Assertion.UNKNOWN),
            e.copy(id="conflict",qualityFlags=listOf("CLOCK_CONFLICT")),event("future",600,650)))
        assertTrue(dayChapters(d).isEmpty())
        val five=dayChapters(d,5).single()
        assertEquals(listOf(e),five.part.episodes);assertEquals(d.asOfMs,five.part.endMs)
    }
    @Test fun nearbyPhotosGroupByTheirActualTimesWithEveryUriReachable() {
        val first=event("one",600,kind="PHOTO").copy(photoUris=listOf("content://one","content://two"))
        val nearby=event("near",620,kind="PHOTO").copy(photoUris=listOf("content://two","content://three"))
        val later=event("later",800,kind="PHOTO").copy(photoUris=listOf("content://four"))
        val chapters=dayChapters(day.copy(episodes=listOf(first,nearby,later)))
        assertEquals(2,chapters.size)
        assertEquals(first.startMs,chapters[0].part.startMs);assertEquals(nearby.endMs,chapters[0].part.endMs)
        assertEquals(listOf("content://one","content://two","content://three"),chapters[0].photos.map{it.uri})
        assertEquals(later.startMs,chapters[1].part.startMs)
    }
    @Test fun realWorldEventCollectsRelatedEvidenceAndKeepsInferenceVisible() {
        val stay=event("stay",500,620,"STAY").copy(title="办公室附近停留",assertion=Assertion.INFERRED)
        val photo=event("photo",550,kind="PHOTO").copy(photoUris=listOf("content://one"))
        val long=event("browser",560,590);val short=event("brief",600,601)
        val outside=event("unrelated",700,701)
        val chapter=dayChapters(day.copy(episodes=listOf(stay,photo,long,short,outside))).single()
        assertEquals("办公室附近停留（推测）",chapter.part.label)
        assertTrue(chapter.summary.contains("推测"));assertEquals(1,chapter.shortAppCount)
        assertEquals(setOf(stay,photo,long,short),chapter.part.episodes.toSet())
        assertEquals(stay.startMs,chapter.part.startMs);assertEquals(stay.endMs,chapter.part.endMs)
        assertEquals(listOf(long),chapter.apps.map{it.episode})
    }
    @Test fun distinctActivitiesAndOverlappingEvidenceHaveOneOwner() {
        val stay=event("stay",480,720,"STAY")
        val walk=event("walk",540,580,"EXERCISE")
        val photo=event("photo",550,kind="PHOTO").copy(photoUris=listOf("content://one"))
        val total=event("day-steps",0,1200,"STEPS").copy(amount=1234)
        val d=day.copy(episodes=listOf(stay,walk,photo,total));val chapters=dayChapters(d)
        assertEquals(2,chapters.size)
        assertEquals(listOf(photo),chapters.single{it.kind=="EXERCISE"}.photos.map{it.episode})
        assertEquals(3,chapters.flatMap{it.part.episodes}.size)
        assertEquals(3,chapters.flatMap{it.part.episodes}.distinctBy{it.id}.size)
        assertTrue(chapters.none{it.part.episodes.contains(total)})
        assertTrue(recapEpisodes(d).contains(total))
    }
    @Test fun sessionCrossingAStayKeepsItsOwnRangeWithoutDuplicatedDuration() {
        val stay=event("stay",500,600,"STAY");val app=event("app",580,630)
        val chapters=dayChapters(day.copy(episodes=listOf(stay,app)))
        assertEquals(2,chapters.size)
        assertEquals(50*60_000L,chapters.flatMap{it.apps}.sumOf{it.durationMs})
        assertEquals(app.endMs,chapters.last().part.endMs)
    }
    @Test fun adjacencyChainsCannotMergeAnEntireDay() {
        val apps=(0..17).map{event("app-$it",400+it*20,415+it*20)}
        val appGroups=dayChapters(day.copy(episodes=apps))
        assertTrue(appGroups.size>=3)
        assertTrue(appGroups.all{it.part.endMs-it.part.startMs<=120*60_000L})
        val photos=(0..14).map{event("photo-$it",400+it*25,kind="PHOTO")}
        val photoGroups=dayChapters(day.copy(episodes=photos))
        assertTrue(photoGroups.size>=4)
        assertTrue(photoGroups.all{it.part.endMs-it.part.startMs<=90*60_000L})
    }
    @Test fun stableIdsDistinguishSimultaneousEventsAndSurviveRelatedUpdates() {
        val stay=event("stay",500,620,"STAY");val walk=event("walk",500,540,"EXERCISE")
        val d=day.copy(episodes=listOf(stay,walk));val before=dayChapters(d)
        assertEquals(2,before.map{it.id}.distinct().size)
        val updated=dayChapters(d.copy(episodes=d.episodes+event("photo",510,kind="PHOTO")))
        assertEquals(before.map{it.id},updated.map{it.id})
    }
    @Test fun localDayBoundsAndSeekingWorkAcrossDaylightSavingChanges() {
        for(date in listOf(LocalDate.of(2026,3,8),LocalDate.of(2026,11,1))) {
            val zone=ZoneId.of("America/New_York");val w=window(date,zone)
            val start=date.atTime(5,55).atZone(zone).toInstant().toEpochMilli()
            val e=event("edge",0).copy(startMs=start,endMs=start+20*60_000L)
            val chapters=dayChapters(day.copy(dayDate=date.toString(),zoneId=zone.id,windowStartMs=w.start,windowEndMs=w.end,asOfMs=w.end,episodes=listOf(e)))
            assertEquals(1,chapters.size);assertEquals(start,chapters.single().part.startMs)
            assertEquals(20*60_000L,chapters.single().apps.single().durationMs)
            assertEquals(0,nearestChapter(chapters,start+10*60_000L))
            assertEquals(0,nearestChapter(chapters,w.start))
        }
        assertEquals(-1,nearestChapter(emptyList(),0))
    }
}
