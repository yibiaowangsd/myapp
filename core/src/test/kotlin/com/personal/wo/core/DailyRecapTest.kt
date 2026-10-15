package com.personal.wo.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DailyRecapTest {
    private val sample=demoDay(LocalDate.of(2026,10,15),"Asia/Hong_Kong")
    private val start=sample.windowStartMs
    private val minute=60_000L
    @Test fun excludesPlansUncertainEventsAndFutureFromRecap() {
        val template=sample.episodes.first()
        val d=sample.copy(episodes=listOf(template.copy(title="会议",assertion=Assertion.PLANNED),template.copy(title="不确定",assertion=Assertion.UNKNOWN),template.copy(title="未来",startMs=sample.asOfMs+minute,endMs=sample.asOfMs+2*minute)),metrics=emptyList())
        assertTrue(recapParts(d).isEmpty())
        assertEquals("今天的总结会随着记录逐渐丰富。",dailyRecap(d))
    }
    @Test fun emptyMeasurementsAreNotInventedAsZero() {
        val d=sample.copy(episodes=emptyList(),metrics=listOf(Metric("steps",null,"步","步数")))
        assertFalse(dailyRecap(d).contains("0步"))
        assertTrue(dailyRecap(d.copy(metrics=listOf(Metric("steps",0,"步","步数")))) .contains("0步"))
    }
    @Test fun sameAppSessionsKeepTheirRealGapAndClipAtCutoff() {
        val e=sample.episodes.first{it.kind=="APP"}
        val d=sample.copy(asOfMs=start+600*minute,episodes=listOf(e.copy(startMs=start+540*minute,endMs=start+550*minute),e.copy(id="next",startMs=start+580*minute,endMs=start+620*minute)))
        val rows=appPeriods(d)
        assertEquals(2,rows.size)
        assertEquals(30*minute,rows.sumOf{it.endMs-it.startMs})
        assertEquals(30*minute,rows[1].startMs-rows[0].endMs)
    }
    @Test fun stepIntervalAcrossNoonIsCountedInOnePartOnly() {
        val step=Episode("step",startMs=start+719*minute,endMs=start+721*minute,lane=Lane.ACTIVITY,kind="STEPS",assertion=Assertion.RECORDED,title="走路",amount=12)
        val parts=recapParts(sample.copy(episodes=listOf(step)))
        assertEquals(listOf("下午"),parts.map{it.label})
        assertTrue(recapPartText(parts.single()).contains("12步"))
    }
    @Test fun inferredStaysKeepTheirQualificationAndPhotosKeepTheirClockPart() {
        val e=Episode("photo",startMs=start+800*minute,endMs=start+800*minute,lane=Lane.LIFE,kind="PHOTO",assertion=Assertion.RECORDED,title="照片",photoUris=listOf("content://photos/1"))
        val d=sample.copy(episodes=sample.episodes+e)
        assertTrue(dailyRecap(d).contains("推测"))
        val parts=recapParts(d)
        assertEquals("下午",parts.first{p->p.episodes.any{it.id=="photo"}}.label)
        assertTrue(recapPartText(parts.first{it.label=="上午"}).contains("推测"))
    }
}
