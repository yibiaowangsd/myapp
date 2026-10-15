package com.personal.wo.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DayPresentationTest {
    private val base=demoDay(LocalDate.of(2026,10,15),"Asia/Shanghai")
    private fun app(id:String,start:Long,end:Long,title:String="应用")=Episode(id,startMs=base.windowStartMs+start,endMs=base.windowStartMs+end,lane=Lane.DIGITAL,kind="APP",assertion=Assertion.RECORDED,title=title)
    @Test fun overlappingIntervalsAreNotCountedTwice() {
        val d=base.copy(episodes=listOf(app("a",0,120000),app("b",60000,180000)))
        assertEquals(180000,appDurations(d).single().durationMs)
        assertEquals(180000,hourlyUsage(d).sumOf{it.durationMs})
    }
    @Test fun chartsClipAtDayStartAndObservationCutoff() {
        val d=base.copy(asOfMs=base.windowStartMs+60000,episodes=listOf(app("a",-60000,120000)))
        assertEquals(60000,appDurations(d).single().durationMs)
    }
    @Test fun plansUnknownAndClockConflictsNeverBecomeObservedUsage() {
        val e=app("a",0,60000)
        val d=base.copy(episodes=listOf(e.copy(assertion=Assertion.PLANNED),e.copy(assertion=Assertion.UNKNOWN),e.copy(qualityFlags=listOf("CLOCK_CONFLICT"))))
        assertTrue(appDurations(d).isEmpty())
        assertEquals(0,hourlyUsage(d).sumOf{it.durationMs})
    }
    @Test fun splitScreenDoesNotDoubleHourlyTotal() {
        val d=base.copy(episodes=listOf(app("a",0,120000,"甲"),app("b",60000,180000,"乙")))
        assertEquals(240000,appDurations(d).sumOf{it.durationMs})
        assertEquals(180000,hourlyUsage(d).sumOf{it.durationMs})
    }
    @Test fun partialAuthorizationIsUsableButUnsupportedIsNot() {
        assertTrue(sourceReady(SourceState(Source.PHOTO,authorization="PARTIAL")))
        assertFalse(sourceReady(SourceState(Source.HEALTH,supported=false,authorization="GRANTED")))
        assertFalse(sourceReady(null))
    }
    @Test fun daylightSavingDayHasItsActualNumberOfHours() {
        val d=demoDay(LocalDate.of(2026,11,1),"America/New_York")
        assertEquals(25,hourlyUsage(d).size)
    }
}
