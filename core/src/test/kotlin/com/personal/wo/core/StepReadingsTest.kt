package com.personal.wo.core

import org.junit.Assert.*
import org.junit.Test

class StepReadingsTest {
    @Test fun firstCounterIsBaselineNotLifetimeSteps() {
        val counter=StepReadings(true)
        assertNull(counter.accept(85100f,1_000_000_000,10000,1_000_000_000,"boot"))
        val d=counter.accept(85112f,2_000_000_000,11000,2_000_000_000,"boot")!!
        assertEquals(12L,d.second)
        assertEquals(Span(10000,11000),d.first)
    }
    @Test fun batchedEventsUseSensorTimeInsteadOfArrivalTime() {
        val counter=StepReadings(true)
        counter.accept(100f,1_000_000_000,10000,1_000_000_000,"boot")
        val d=counter.accept(110f,2_000_000_000,15000,6_000_000_000,"boot")!!
        assertEquals(11000L,d.first.end)
    }
    @Test fun detectorCountsEachStepOnceAndRejectsMalformedEvents() {
        val detector=StepReadings(false)
        assertEquals(1L,detector.accept(1f,100,1000,100,"boot")!!.second)
        assertNull(detector.accept(1f,100,1000,100,"boot"))
        assertNull(detector.accept(2f,200,2000,200,"boot"))
        assertNull(detector.accept(Float.NaN,300,3000,300,"boot"))
        assertNull(detector.accept(1f,500,3000,300,"boot"))
    }
    @Test fun resetOrRebootCannotCreateNegativeOrHugeDeltas() {
        val counter=StepReadings(true)
        counter.accept(100f,1000,1000,1000,"boot")
        assertNull(counter.accept(2f,2000,2000,2000,"boot2"))
        assertEquals(3L,counter.accept(5f,3000,3000,3000,"boot2")!!.second)
        assertNull(counter.accept(1f,4000,4000,4000,"boot2"))
    }
}
