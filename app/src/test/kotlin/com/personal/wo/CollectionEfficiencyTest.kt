package com.personal.wo

import com.personal.wo.collector.*
import com.personal.wo.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CollectionEfficiencyTest {
    @Test fun overlappingAutomaticPullsRunOnlyOnce()=runBlocking {
        val gate=PullGate({100L});var calls=0
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val first=async { gate.run { calls++;entered.complete(Unit);release.await() } }
        entered.await()
        val second=async { gate.run { calls++ } }
        yield();release.complete(Unit)
        assertTrue(first.await());assertFalse(second.await());assertEquals(1,calls)
    }
    @Test fun manualRefreshBypassesCooldownAndAutomaticPullResumesAfterExpiry()=runBlocking {
        var time=0L;var calls=0;val gate=PullGate({time})
        gate.run { calls++ };time=10_000
        assertFalse(gate.run { calls++ })
        assertTrue(gate.run(force=true) { calls++ })
        time=40_000
        assertTrue(gate.run { calls++ });assertEquals(3,calls)
    }
    @Test fun failedOrCancelledPullDoesNotSuppressRetry()=runBlocking {
        val gate=PullGate({10L})
        try { gate.run { throw IllegalStateException() };fail() } catch(_:IllegalStateException){}
        try { gate.run { throw CancellationException() };fail() } catch(_:CancellationException){}
        assertTrue(gate.run { })
    }
    @Test fun foregroundCanReadHealthDataImmediatelyAfterBackgroundWorker()=runBlocking {
        val gate=PullGate({100L})
        assertTrue(gate.run(foreground=false) { })
        assertTrue(gate.run(foreground=true) { })
        assertFalse(gate.run(foreground=true) { })
    }
    private fun plan(p:Policy,fine:Boolean=true)=recordingPlan(p,true,true,true,fine)
    @Test fun calendarAndNoticeSelectionDoNotRestartSensors() {
        val p=Policy(requested=true)
        val before=plan(p)
        assertEquals(before,plan(p.copy(enabledSources=p.enabledSources-Source.CALENDAR,noticeApps=setOf("a"),generation=20)))
    }
    @Test fun turningOffBothSensorSourcesRequiresNoForegroundService() {
        val p=Policy(requested=true,enabledSources=setOf(Source.USAGE,Source.PHOTO,Source.CALENDAR,Source.MEDIA,Source.NOTIFICATION))
        assertFalse(plan(p).needed)
        assertFalse(plan(p.copy(enabledSources=Source.entries.toSet(),paused=true)).needed)
        assertFalse(plan(p.copy(requested=false)).needed)
    }
    @Test fun permissionAndLocationChangesReconfigureOnlyTheNecessarySensors() {
        val p=Policy(requested=true)
        assertNotEquals(plan(p),plan(p,false))
        assertEquals(RecordingPlan(steps=true),recordingPlan(p,true,false,true,true))
        assertEquals(RecordingPlan(location=true,precise=true),recordingPlan(p,true,true,false,true))
    }
    @Test fun appStatusRequiresAuthorizationAndDoesNotClaimAccountConnection() {
        val app=ConnectedApp("com.huawei.health","华为运动健康")
        val p=Policy(requested=true)
        assertEquals("待开启来源权限",connectionStatus(app,p,emptyList()))
        assertEquals("使用时段已开启",connectionStatus(app,p,listOf(SourceState(Source.USAGE,authorization="GRANTED"))))
    }
    @Test fun disabledUnsupportedAndPausedSourcesNeverAppearActive() {
        val app=ConnectedApp("music","音乐");val p=Policy(requested=true)
        val granted=listOf(SourceState(Source.USAGE,authorization="GRANTED"))
        assertEquals("待开启来源权限",connectionStatus(app,p.copy(enabledSources=emptySet()),granted))
        assertEquals("待开启来源权限",connectionStatus(app,p,granted.map{it.copy(supported=false)}))
        assertEquals("记录已暂停",connectionStatus(app,p.copy(paused=true),granted))
        assertEquals("已排除",connectionStatus(app,p.copy(excludedApps=setOf("music")),granted))
    }
    @Test fun notificationContentRequiresAppSelectionAndGlobalGrant() {
        val app=ConnectedApp("travel","出行");val p=Policy(requested=true,noticeApps=setOf("travel"))
        assertEquals("待开启来源权限",connectionStatus(app,p,emptyList()))
        assertEquals("通知线索已开启",connectionStatus(app,p,listOf(SourceState(Source.NOTIFICATION,authorization="GRANTED"))))
    }
}
