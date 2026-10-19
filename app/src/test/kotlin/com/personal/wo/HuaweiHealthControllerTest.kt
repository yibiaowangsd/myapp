package com.personal.wo

import android.app.Application
import android.content.Intent
import com.personal.wo.core.*
import com.personal.wo.health.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class HuaweiHealthControllerTest {
    private val scopes=setOf(HuaweiScope.STEPS,HuaweiScope.EXERCISE,HuaweiScope.SLEEP,HuaweiScope.HISTORY_WEEK)
    private val zone=ZoneId.of("Asia/Hong_Kong")
    private val now=window(LocalDate.of(2026,10,19),zone).end-1000
    private class Store:HuaweiStore {
        override var enabled=true;override var revokePending=false;override var accountKey=""
        override var consentedAt=0L
    }
    private class Sink:HuaweiSink {
        val retained=mutableListOf<Set<Kind>>();val saved=mutableListOf<HuaweiPage>()
        override suspend fun retain(kinds:Set<Kind>){retained+=kinds;if(kinds.isEmpty())saved.clear()}
        override suspend fun save(accountKey:String,scope:HuaweiScope,span:Span,page:HuaweiPage,zone:ZoneId,at:Long,generation:Long){saved+=page}
    }
    private inner class Gateway:HuaweiGateway {
        var current=HuaweiSession("account-A",scopes,true)
        var failure:Exception?=null;var revokeFailure=false;var accepted=true
        var sessions=0;var revocations=0;var requested=emptySet<HuaweiScope>()
        val reads=mutableListOf<Pair<HuaweiScope,Span>>()
        var readBlock:(suspend(HuaweiScope,Span)->HuaweiPage)?=null
        override fun authorizationIntent(scopes:Set<HuaweiScope>):Intent {requested=scopes;return Intent("test.huawei.authorization")}
        override fun authorizationSucceeded(data:Intent?)=accepted
        override suspend fun session():HuaweiSession {sessions++;failure?.let { throw it };return current}
        override suspend fun read(scope:HuaweiScope,span:Span,zone:ZoneId):HuaweiPage {reads+=scope to span;return readBlock?.invoke(scope,span) ?: HuaweiPage(emptyList())}
        override suspend fun revoke(){revocations++;if(revokeFailure)throw IllegalStateException("offline")}
    }
    private inner class Fixture(config:HuaweiConfig=HuaweiConfig("123456789",scopes,true,true)) {
        val store=Store();val gateway=Gateway();val sink=Sink()
        val controller=HuaweiHealthController(config,gateway,store,sink){now}
        suspend fun sync()=controller.sync(zone,0,true)
    }
    @Test fun unconfiguredAndSignatureMismatchNeverInvokeSdk()=runBlocking {
        for(config in listOf(HuaweiConfig(),HuaweiConfig("",scopes,true,true),HuaweiConfig("123",scopes,true,false))) {
            val f=Fixture(config);f.sync()
            assertEquals(0,f.gateway.sessions);assertTrue(f.gateway.reads.isEmpty())
            try { f.controller.beginAuthorization();fail() } catch(_:IllegalStateException){}
        }
    }
    @Test fun processRestartHasNoTrustedGrantedStateAndBackgroundNeverReads()=runBlocking {
        val f=Fixture();assertEquals(HuaweiPhase.DISCONNECTED,f.controller.state.value.phase)
        f.controller.sync(zone,0,false);assertEquals(0,f.gateway.sessions)
        f.sync();assertEquals(HuaweiPhase.AUTHORIZED,f.controller.state.value.phase)
        f.sync();assertEquals(2,f.gateway.sessions)
    }
    @Test fun onlyApprovedAndActuallyGrantedReadScopesAreUsed()=runBlocking {
        val f=Fixture();f.gateway.current=f.gateway.current.copy(granted=setOf(HuaweiScope.STEPS,HuaweiScope.HISTORY_WEEK))
        f.sync();assertEquals(HuaweiPhase.PARTIAL,f.controller.state.value.phase)
        assertEquals(setOf(HuaweiScope.STEPS),f.gateway.reads.map { it.first }.toSet())
        assertEquals(7,f.gateway.reads.size)
        val stepOnly=Fixture(HuaweiConfig("123",setOf(HuaweiScope.STEPS,HuaweiScope.HISTORY_WEEK),true,true))
        stepOnly.sync();assertTrue(stepOnly.gateway.reads.all { it.first==HuaweiScope.STEPS })
    }
    @Test fun approvalAndDeviceInstallationAreNotUserAuthorization()=runBlocking {
        val f=Fixture();f.gateway.current=f.gateway.current.copy(healthAppAuthorized=false)
        f.sync();assertEquals(HuaweiPhase.DENIED,f.controller.state.value.phase)
        assertTrue(f.gateway.reads.isEmpty());assertFalse(f.store.enabled)
        assertEquals(emptySet<Kind>(),f.sink.retained.last())
    }
    @Test fun sdkSuccessCallbackStillNeedsAnActualScopeAndHealthAppCheck()=runBlocking {
        val f=Fixture();f.store.enabled=false
        f.controller.beginAuthorization();assertEquals(scopes,f.gateway.requested)
        f.gateway.current=f.gateway.current.copy(granted=emptySet())
        f.controller.finishAuthorization(true,Intent())
        assertEquals(HuaweiPhase.DENIED,f.controller.state.value.phase);assertTrue(f.gateway.reads.isEmpty())
    }
    @Test fun cancelAndRejectDoNotProduceAConnection()=runBlocking {
        for(ok in listOf(false,true)) {
            val f=Fixture();f.gateway.accepted=false;f.controller.beginAuthorization()
            f.controller.finishAuthorization(ok,if(ok)Intent() else null)
            assertEquals(if(ok)HuaweiPhase.DENIED else HuaweiPhase.CANCELLED,f.controller.state.value.phase)
            assertEquals(0,f.gateway.sessions);assertTrue(f.gateway.reads.isEmpty())
        }
    }
    @Test fun noHistoryScopePreventsQueriesBeforeConsentAndPartialDailyTotals()=runBlocking {
        val f=Fixture();f.gateway.current=f.gateway.current.copy(granted=setOf(HuaweiScope.STEPS,HuaweiScope.EXERCISE))
        f.store.consentedAt=now-86_400_000L
        f.sync()
        assertTrue(f.gateway.reads.all { it.second.start>=f.store.consentedAt })
        assertEquals(1,f.gateway.reads.count { it.first==HuaweiScope.STEPS })
    }
    @Test fun emptySuccessfulQueryIsNotSyntheticData()=runBlocking {
        val f=Fixture();f.sync()
        assertTrue(f.sink.saved.all { it.records.isEmpty() })
        assertTrue(f.controller.state.value.detail.contains("暂无可读数据"))
    }
    @Test fun networkFailureIsRetryableAndNeverReadAfterFailedValidation()=runBlocking {
        val f=Fixture();f.gateway.failure=HuaweiAccessException(false,50001)
        f.sync();assertEquals(HuaweiPhase.ERROR,f.controller.state.value.phase)
        assertTrue(f.store.enabled);assertTrue(f.gateway.reads.isEmpty());assertTrue(f.sink.retained.isEmpty())
        f.gateway.failure=null;f.sync();assertEquals(HuaweiPhase.AUTHORIZED,f.controller.state.value.phase)
    }
    @Test fun externalRevocationPurgesOnlyHuaweiSinkAndStopsFutureReads()=runBlocking {
        val f=Fixture();f.sync();val readCount=f.gateway.reads.size
        f.gateway.failure=HuaweiAccessException(true,50037);f.sync()
        assertFalse(f.store.enabled);assertTrue(f.controller.state.value.granted.isEmpty())
        assertEquals(emptySet<Kind>(),f.sink.retained.last());f.sync();assertEquals(readCount,f.gateway.reads.size)
    }
    @Test fun accountSwitchAndPartialScopeWithdrawalClearOldImportedData()=runBlocking {
        val f=Fixture();f.sync()
        f.gateway.current=HuaweiSession("account-B",setOf(HuaweiScope.STEPS,HuaweiScope.HISTORY_WEEK),true)
        f.sync();assertEquals("account-B",f.store.accountKey)
        assertTrue(f.sink.retained.any { it.isEmpty() });assertEquals(setOf(Kind.HEALTH_STEPS),f.sink.retained.last())
    }
    @Test fun revocationFailureStaysPendingAcrossRestartUntilOfficialConfirmation()=runBlocking {
        val f=Fixture();f.gateway.revokeFailure=true;f.controller.revoke()
        assertFalse(f.store.enabled);assertTrue(f.store.revokePending)
        assertEquals(HuaweiPhase.REVOKE_PENDING,f.controller.state.value.phase)
        f.sync();assertEquals(0,f.gateway.sessions)
        val restarted=HuaweiHealthController(f.controller.config,f.gateway,f.store,f.sink){now}
        assertEquals(HuaweiPhase.REVOKE_PENDING,restarted.state.value.phase)
        f.gateway.revokeFailure=false;restarted.revoke()
        assertFalse(f.store.revokePending);assertEquals(HuaweiPhase.DISCONNECTED,restarted.state.value.phase)
    }
    @Test fun revokedInFlightResultCannotWriteOrReconnect()=runBlocking {
        val f=Fixture();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        f.gateway.readBlock={_,span->entered.complete(Unit);release.await();HuaweiPage(listOf(HuaweiRecord("late",Kind.HEALTH_STEPS,span.start,span.end,123)))}
        val read=async { f.sync() };entered.await()
        val revoke=async(start=CoroutineStart.UNDISPATCHED) { f.controller.revoke() }
        release.complete(Unit);read.await();revoke.await()
        assertTrue(f.sink.saved.isEmpty());assertFalse(f.store.enabled)
        f.controller.finishAuthorization(true,Intent());assertFalse(f.store.enabled)
    }
    @Test fun cancellationIsNotReportedAsSuccessfulSync()=runBlocking {
        val f=Fixture();f.gateway.failure=CancellationException("cancel test")
        try { f.sync();fail() } catch(_:CancellationException){}
        assertNull(f.controller.state.value.lastSyncMs);assertTrue(f.sink.saved.isEmpty())
    }
}
