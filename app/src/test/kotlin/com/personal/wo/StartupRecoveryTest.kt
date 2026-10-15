package com.personal.wo

import com.personal.wo.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class StartupRecoveryTest {
    @Test fun initializationFailureBecomesAnInspectableResult() = runBlocking {
        val failure=IllegalStateException("database unavailable")
        assertSame(failure,recoverableTask<Unit> { throw failure }.exceptionOrNull())
    }
    @Test fun nativeLoadFailureDoesNotEscapeTheStartupBoundary() = runBlocking {
        val error=UnsatisfiedLinkError("missing test symbol")
        assertSame(error,recoverableTask<Unit> { throw error }.exceptionOrNull())
    }
    @Test fun coroutineCancellationIsNotReportedAsADeviceFailure() = runBlocking {
        val cancellation=CancellationException("screen left foreground")
        try { recoverableTask<Unit> { throw cancellation };fail("Cancellation was swallowed") }
        catch(actual:CancellationException) { assertSame(cancellation,actual) }
    }
    @Test fun missingOptionalProviderDoesNotPreventOtherSources() = runBlocking {
        val policy=Policy()
        val states=listOf(Source.HEALTH,Source.USAGE).map { source -> probeSource(source,policy) {
            if(source==Source.HEALTH)throw NoClassDefFoundError("missing optional provider")
            SourceState(source,authorization="GRANTED",runtime="RUNNING")
        } }
        assertFalse(states[0].supported)
        assertEquals("UNKNOWN",states[0].authorization)
        assertEquals("RUNNING",states[1].runtime)
    }
    @Test fun deniedSystemProbeDoesNotBecomeAZeroMetric() = runBlocking {
        val state=probeSource(Source.USAGE,Policy()) { throw SecurityException("denied") }
        assertEquals("UNKNOWN",state.authorization)
        assertEquals("STOPPED",state.runtime)
        assertTrue(state.supported)
    }
    @Test fun disabledProviderIsNeverInitialized() = runBlocking {
        val state=probeSource(Source.HEALTH,Policy(enabledSources=setOf(Source.USAGE))) { error("must not query disabled provider") }
        assertEquals("STOPPED",state.runtime)
        assertEquals("本人已关闭此来源",state.detail)
    }
    @Test fun diagnosticOmitsExceptionMessagesContainingPrivateData() {
        val error=IllegalArgumentException("notification body password=private-example")
        val detail=failureDetails(error)
        assertTrue(detail.contains("IllegalArgumentException"))
        assertFalse(detail.contains("private-example"))
        assertFalse(detail.contains("notification body"))
    }
    @Test fun fatalVmFailureIsNotSilentlyHidden() {
        val error=OutOfMemoryError("test")
        try { recoverableCall<Unit> { throw error };fail("VM failure was swallowed") }
        catch(actual:OutOfMemoryError) { assertSame(error,actual) }
    }
}
