package com.personal.wo

import com.personal.wo.collector.*
import org.junit.Assert.*
import org.junit.Test

class StepSensorsTest {
    @Test fun rejectedBatchRetriesImmediateDelivery() {
        val attempts=mutableListOf<Int>()
        val choice=registerStepCandidates(listOf(StepCandidate("counter",true,"counter"))) { _,latency -> attempts+=latency;latency==0 }
        assertEquals("counter",choice?.sensor)
        assertEquals(listOf(10_000_000,0),attempts)
    }
    @Test fun failedCounterFallsBackToDetectorWithoutAddingBoth() {
        val attempts=mutableListOf<String>()
        val choice=registerStepCandidates(listOf(StepCandidate("detector",false,"detector"),StepCandidate("counter",true,"counter"))) { name,_ -> attempts+=name;name=="detector" }
        assertEquals("detector",choice?.sensor)
        assertEquals(listOf("counter","counter","detector"),attempts)
    }
    @Test fun totalFailureIsNotReportedAsConnected() {
        assertNull(registerStepCandidates(listOf(StepCandidate("counter",true,"counter"))) { _,_ -> false })
        assertNull(registerStepCandidates(listOf(StepCandidate("counter",true,"counter"))) { _,_ -> throw SecurityException("denied") })
    }
    @Test fun detectorOnlyDeviceCanConnect() {
        val choice=registerStepCandidates(listOf(StepCandidate("detector",false,"detector"))) { _,latency -> latency==0 }
        assertEquals(false,choice?.counter)
    }
}
