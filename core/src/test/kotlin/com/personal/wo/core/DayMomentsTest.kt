package com.personal.wo.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DayMomentsTest {
    private val day=demoDay(LocalDate.of(2026,10,15),"Asia/Shanghai")
    private fun event(id:String,start:Long,end:Long=start,kind:String="PHOTO")=Episode(id,
        startMs=day.windowStartMs+start*60_000L,endMs=day.windowStartMs+end*60_000L,
        kind=kind,lane=Lane.LIFE,title=id,assertion=Assertion.RECORDED)
    @Test fun chronologicalOrderClipsBoundariesWithoutAlteringOriginalEvidence() {
        val old=event("crossing",-60,60,"MEDIA")
        val d=day.copy(asOfMs=day.windowStartMs+900*60_000L,episodes=listOf(event("late",840,960,"APP"),event("morning",500),old,event("ended",-60,0,"APP")))
        val rows=dayMoments(d)
        assertEquals(listOf("crossing","morning","late"),rows.map{it.episode.id})
        assertEquals(d.windowStartMs,rows.first().startMs)
        assertEquals(d.asOfMs,rows.last().endMs)
        assertEquals(old,rows.first().episode)
    }
    @Test fun futurePlansAndUnknownEventsNeverBecomeExperiences() {
        val e=event("sample",500)
        val d=day.copy(episodes=listOf(e.copy(assertion=Assertion.PLANNED),e.copy(assertion=Assertion.UNKNOWN),
            e.copy(qualityFlags=listOf("CLOCK_CONFLICT")),event("future",1400)))
        assertTrue(dayMoments(d).isEmpty())
    }
    @Test fun StepIntervalAppearsOnceAtItsEndingPartWithoutDividingAmount() {
        val d=day.copy(episodes=listOf(event("steps",300,800,"STEPS").copy(amount=1234)))
        val row=dayMoments(d).single()
        assertEquals("下午",row.part)
        assertEquals(1234L,row.episode.amount)
        assertEquals(d.windowStartMs+800*60_000L-1,row.anchorMs)
    }
    @Test fun ScrubbingKeepsGapsAndSelectsOnlyActualRecords() {
        val rows=dayMoments(day.copy(episodes=listOf(event("early",60),event("late",1200))))
        assertEquals(0,nearestMoment(rows,day.windowStartMs))
        assertEquals(1,nearestMoment(rows,day.windowEndMs))
        assertEquals(0,nearestMoment(rows,day.windowStartMs+600*60_000L))
        assertEquals(-1,nearestMoment(emptyList(),day.windowStartMs))
    }
}
