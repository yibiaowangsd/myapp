package com.personal.wo.core

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.ZoneId

class DayClockTest {
    private val policy=Policy(archiveZone="Asia/Shanghai")
    private val span=window(LocalDate.of(2026,10,19),ZoneId.of(policy.archiveZone))
    private val at=span.start+12*3_600_000L
    private fun day()=DayView("2026-10-19",policy.archiveZone,span.start,span.end,at,"IN_PROGRESS","",emptyList(),emptyList(),emptyList(),listOf(
        Episode("open",startMs=at-60_000,endMs=at-60_000,lane=Lane.DIGITAL,kind="APP",assertion=Assertion.UNKNOWN,title="未闭合")))
    @Test fun clockAdvancesWithoutInventingAnEndForUnknownActivity() {
        val before=day();val after=advanceDayClock(before,policy,at+120_000)!!
        assertEquals(at+120_000,after.asOfMs);assertEquals(before.episodes,after.episodes)
        assertEquals(before.revision,after.revision)
    }
    @Test fun timeRollbackAndNewlyActiveExclusionsRequireAnEngineRefresh() {
        assertNull(advanceDayClock(day(),policy,at-1))
        assertNull(advanceDayClock(day(),policy.copy(exclusions=listOf(Exclusion("future",at+30_000,at+60_000))),at+120_000))
    }
    @Test fun oldDaysStopAtTheirActualMidnightBoundary() {
        val after=advanceDayClock(day(),policy,span.end+60_000)!!
        assertEquals(span.end,after.asOfMs);assertEquals("READY",after.status)
    }
}
