package com.personal.wo

import com.personal.wo.collector.ProbeGate
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*

class ProbeGateTest {
    @Test fun resumesCoalesceButPermissionsPoliciesExpiryAndClockRollbackDoNot()=runBlocking {
        var time=100L;var calls=0;val gate=ProbeGate({time})
        assertTrue(gate.run(false,1){calls++});assertFalse(gate.run(false,1){calls++})
        assertTrue(gate.run(true,1){calls++});assertTrue(gate.run(false,2){calls++})
        time+=30_000;assertTrue(gate.run(false,2){calls++})
        time=0;assertTrue(gate.run(false,2){calls++});assertEquals(5,calls)
    }
    @Test fun concurrentAutomaticResumesPerformOnlyOneProbe()=runBlocking {
        var calls=0;val gate=ProbeGate({100})
        List(6){async{gate.run(false,1){calls++;delay(20)}}}.awaitAll()
        assertEquals(1,calls)
    }
    @Test fun cancellationNeverMarksAnIncompleteProbeAsFresh()=runBlocking {
        val gate=ProbeGate({100})
        withTimeoutOrNull(20){gate.run(false,1){delay(200)}}
        assertTrue(gate.run(false,1){})
    }
}
