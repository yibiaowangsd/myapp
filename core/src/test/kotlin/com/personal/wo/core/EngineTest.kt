package com.personal.wo.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EngineTest {
    private val minute = 60_000L
    private val date = LocalDate.of(2026, 10, 15)
    private val w = window(date, ZoneId.of("Asia/Hong_Kong"))
    private val policy = Policy(archiveZone = "Asia/Hong_Kong")
    private fun event(t: Int, kind: Kind, pkg: String = "a", cls: String = "Main") = Observation("$t-$kind-$pkg-$cls", Source.USAGE, "$t-$kind-$pkg-$cls", kind, w.start + t * minute, payload = Payload(packageName = pkg, className = cls, label = pkg))
    private fun day(os: List<Observation>, p: Policy = policy) = DayEngine().build(date, p, os, emptyList(), emptyList(), emptyList(), emptyList(), w.end)
    @Test fun overlapDoesNotInflate() {
        val d = day(listOf(event(0, Kind.RESUMED), event(2, Kind.RESUMED, "b"), event(10, Kind.PAUSED), event(10, Kind.PAUSED, "b")))
        assertEquals(10 * minute, d.metrics.first { it.key == "appMs" }.value)
    }
    @Test fun packageActivitySwitchIsMerged() {
        val d = day(listOf(event(0, Kind.RESUMED), event(5, Kind.RESUMED, cls = "Other"), event(5, Kind.PAUSED), event(10, Kind.STOPPED, cls = "Other")))
        assertEquals(1, d.episodes.count { it.kind == "APP" }); assertEquals(10 * minute, d.metrics[0].value)
    }
    @Test fun pauseAndStoppedNotDoubleCounted() {
        val d = day(listOf(event(0, Kind.RESUMED), event(5, Kind.PAUSED), event(6, Kind.STOPPED)))
        assertEquals(5 * minute, d.metrics[0].value)
    }
    @Test fun duplicatesAreIdempotent() {
        val es = listOf(event(0, Kind.RESUMED), event(10, Kind.PAUSED))
        assertEquals(day(es), day(es + es))
    }
    @Test fun lockEndsActivity() {
        val d = day(listOf(event(0, Kind.RESUMED), event(3, Kind.LOCK), event(10, Kind.PAUSED)))
        assertEquals(3 * minute, d.metrics[0].value)
    }
    @Test fun unknownTailDoesNotCountAllDay() {
        val d = day(listOf(event(0, Kind.RESUMED)))
        assertEquals(0L, d.metrics[0].value); assertTrue(d.episodes.first().qualityFlags.contains("PROVISIONAL"))
    }
    @Test fun pauseBarrierStopsContinuity() {
        val p = policy.copy(exclusions = listOf(Exclusion("pause", w.start + 3 * minute, w.start + 8 * minute)))
        assertEquals(3 * minute, day(listOf(event(0, Kind.RESUMED), event(10, Kind.PAUSED)), p).metrics[0].value)
    }
    @Test fun staleGenerationInputsCanBeExcluded() {
        val p = policy.copy(exclusions = listOf(Exclusion("delete", w.start, w.end, Source.USAGE)))
        assertTrue(day(listOf(event(0, Kind.RESUMED), event(10, Kind.PAUSED)), p).episodes.isEmpty())
    }
    @Test fun crossMidnightKeepsStableId() {
        val a = event(-10, Kind.RESUMED); val b = event(10, Kind.PAUSED)
        val today = day(listOf(a, b)); val yesterday = DayEngine().build(date.minusDays(1), policy, listOf(a,b), emptyList(), emptyList(), emptyList(), emptyList(), w.end)
        assertEquals(10 * minute, today.metrics[0].value); assertEquals(10 * minute, yesterday.metrics[0].value)
        assertEquals(today.episodes.first().id, yesterday.episodes.first().id)
    }
    @Test fun daylightSavingWindowHasActualLength() {
        assertEquals(23 * 3_600_000L, window(LocalDate.of(2026, 3, 8), ZoneId.of("America/New_York")).duration)
        assertEquals(25 * 3_600_000L, window(LocalDate.of(2026, 11, 1), ZoneId.of("America/New_York")).duration)
    }
    @Test fun stepCounterResetCreatesNewBaseline() {
        val b = StepBaseline(); assertNull(b.update(100, "boot1", 10)); assertEquals(5L, b.update(105,"boot1",20)!!.second)
        assertNull(b.update(2,"boot2",30)); assertNull(b.update(1,"boot2",40)); b.reset(); assertNull(b.update(50,"boot2",50))
    }
    @Test fun crossPauseHealthCountIsNotProRated() {
        val o = Observation("hc",Source.HEALTH,"hc",Kind.HEALTH_STEPS,w.start,w.end,payload = Payload(steps = 1000))
        assertTrue(filterObservation(o,policy.copy(exclusions = listOf(Exclusion("x",w.start+minute,w.start+2*minute)))).isEmpty())
    }
    @Test fun notificationLanguageIsConservative() {
        assertEquals("收到送达提示", NoticeRules.classify("快递已送达", "", true))
        assertEquals("通知片段", NoticeRules.classify("支付成功", "", false))
    }
    @Test fun otpIsRemovedBeforePersistence() {
        val s = NoticeRules.sanitize("验证码", "本次验证码123456")
        assertFalse((s.first+s.second).contains("123456"))
        assertFalse(NoticeRules.sanitize("银行卡", "6222021234567890123").second.contains("622202"))
    }
    @Test fun calendarCannotBecomeFact() {
        val o = Observation("cal", Source.CALENDAR, "cal", Kind.CALENDAR, w.start, w.start + minute, payload = Payload(label="会议"))
        val d = day(listOf(o)); assertEquals(Assertion.PLANNED, d.episodes.first().assertion); assertTrue(d.sentences.isEmpty())
    }
    @Test fun noScreenInteractionCannotBecomeSleep() { assertFalse(day(listOf(event(0,Kind.SCREEN_OFF))).episodes.any { it.kind == "SLEEP" }) }
    @Test fun locationGapDoesNotMakeStay() {
        val ps = listOf(0,30,60).map { t -> Observation("p$t",Source.LOCATION,"p$t",Kind.POSITION,w.start+t*minute,payload=Payload(lat=31.0,lon=121.0,accuracyM=30.0)) }
        assertFalse(day(ps).episodes.any { it.kind == "STAY" })
    }
    @Test fun driftRemainsOneStay() {
        val ps = listOf(0,5,10,15,20).mapIndexed { i,t -> Observation("p$i",Source.LOCATION,"p$i",Kind.POSITION,w.start+t*minute,payload=Payload(lat=31.0+i*.00001,lon=121.0,accuracyM=30.0)) }
        assertEquals(1, day(ps).episodes.count { it.kind == "STAY" })
    }
    @Test fun offlineRouteUnwrapsDateLine() { assertTrue(kotlin.math.abs(project(0.0,-179.0,0.0,179.0).x)<3) }
    @Test fun mediaPauseIsNotPlayback() {
        val os = listOf(0 to Kind.MEDIA_PLAY,5 to Kind.MEDIA_PAUSE,10 to Kind.MEDIA_PLAY,12 to Kind.MEDIA_PAUSE).map { (t,k) -> Observation("m$t",Source.MEDIA,"m$t",k,w.start+t*minute,payload=Payload(sessionKey="s",label="音乐")) }
        assertEquals(7*minute,day(os).metrics.first { it.key == "mediaMs" }.value)
    }
    @Test fun healthAggregationDoesNotDoubleHardwareSteps() {
        val os = listOf(Observation("h",Source.HEALTH,"h",Kind.HEALTH_STEPS,w.start,w.end,payload=Payload(steps=100)),Observation("s",Source.STEPS,"s",Kind.STEP_DELTA,w.start+minute,w.start+2*minute,payload=Payload(steps=40)))
        assertEquals(100L,day(os).metrics.first { it.key == "steps" }.value)
    }
    @Test fun unknownMetricIsNull() { assertNull(day(emptyList()).metrics.first { it.key == "steps" }.value) }
    @Test fun chineseEnglishTokens() { assertTrue(NoticeRules.tokens("今天使用TLS").contains("今天")); assertTrue(NoticeRules.tokens("ML-KEM").contains("kem")) }
    @Test fun intervalsAreHalfOpen() {
        assertFalse(Span(0,10).contains(10)); assertEquals(listOf(Span(0,4),Span(6,10)),subtract(Span(0,10),listOf(Span(4,6))))
        assertEquals(10L,union(listOf(Span(0,8),Span(2,10))).sumOf { it.duration })
    }
    @Test fun mediaRecoveryCannotExtendAnOldSong() {
        val os=listOf(-1000 to Kind.MEDIA_PLAY,100 to Kind.MEDIA_PLAY,115 to Kind.MEDIA_PAUSE).map { (t,k)->Observation("m$t",Source.MEDIA,"m$t",k,w.start+t*minute,payload=Payload(sessionKey="same-session",label="音乐")) }
        assertEquals(15*minute,day(os).metrics.first { it.key=="mediaMs" }.value)
    }
    @Test fun retainedEvidenceStillHonorsDeletionExclusion() {
        val e=Episode("old",startMs=w.start+minute,endMs=w.start+minute,lane=Lane.LIFE,kind="NOTICE",assertion=Assertion.RECORDED,title="旧通知",evidence=listOf(Evidence("x",Source.NOTIFICATION,"x","原始明细已到期",false)))
        val d=DayEngine().build(date,policy.copy(exclusions=listOf(Exclusion("delete",w.start,w.end))),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),w.end,retained=listOf(e))
        assertTrue(d.episodes.isEmpty())
    }
    @Test fun malformedArchivePathsAreRejected() {
        for(name in listOf("../policy.json","/policy.json","payload.exe"))try { ArchiveRules.entry(name,false,mutableSetOf());fail("unsafe path accepted") }catch(expected:IllegalArgumentException){}
        val seen=mutableSetOf<String>();ArchiveRules.entry("policy.json",false,seen)
        try{ArchiveRules.entry("policy.json",false,seen);fail("duplicate accepted")}catch(expected:IllegalArgumentException){}
    }
    @Test fun oversizedOrUnknownArchiveHeadersAreRejected() {
        for(length in listOf(-1,0,65537))try { ArchiveRules.header(length);fail("header length accepted") }catch(expected:IllegalArgumentException){}
        try{ArchiveRules.header(100,algorithm="xor");fail("unknown cipher accepted")}catch(expected:IllegalArgumentException){}
    }
    @Test fun ordinaryPasswordsAreNotPersisted() {
        val result=NoticeRules.sanitize("Account", "password: SecretExample42")
        assertFalse((result.first+result.second).contains("SecretExample42"))
        assertFalse(NoticeRules.sanitize("通知","本次口令 abcdEF").second.contains("abcdEF"))
    }
    @Test fun screenWithoutKeyguardCanResume() {
        val d=day(listOf(event(0,Kind.RESUMED),event(1,Kind.SCREEN_OFF),event(5,Kind.SCREEN_ON),event(5,Kind.RESUMED),event(10,Kind.PAUSED)))
        assertEquals(6*minute,d.metrics.first { it.key=="appMs" }.value)
    }
    @Test fun screenOnDoesNotBypassKnownKeyguard() {
        val d=day(listOf(event(0,Kind.RESUMED),event(1,Kind.LOCK),event(2,Kind.SCREEN_OFF),event(5,Kind.SCREEN_ON),event(5,Kind.RESUMED),event(6,Kind.UNLOCK),event(6,Kind.RESUMED),event(10,Kind.PAUSED)))
        assertEquals(5*minute,d.metrics.first { it.key=="appMs" }.value)
    }
    @Test fun videoCannotBeLabeledAsPhoto() {
        val o=Observation("video",Source.PHOTO,"video",Kind.PHOTO,w.start+minute,payload=Payload(captured=true,category="VIDEO"))
        assertEquals("拍摄的视频",day(listOf(o)).episodes.first().title)
    }
    @Test fun correctedFactsRetainUserConfirmation() {
        val es=listOf(event(0,Kind.RESUMED),event(10,Kind.PAUSED));val original=day(es).episodes.first()
        val ann=Annotation("correction",original.id,1,endMs=w.start+15*minute,confirmed=true)
        val d=DayEngine().build(date,policy,es,emptyList(),emptyList(),emptyList(),listOf(ann),w.end)
        assertEquals(Assertion.CONFIRMED,d.facts.first { it.type=="APP" }.assertion)
    }
}
