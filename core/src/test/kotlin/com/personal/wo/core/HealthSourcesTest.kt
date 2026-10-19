package com.personal.wo.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HealthSourcesTest {
    private val zone=ZoneId.of("Asia/Hong_Kong")
    private val date=LocalDate.of(2026,10,19)
    private val w=window(date,zone)
    private val now=w.end-1
    private fun total(provider:String,count:Long,key:String=provider,received:Long=now-100)=
        Observation(key,Source.HEALTH,key,Kind.HEALTH_STEPS,w.start,w.end,received,payload=Payload(provider=provider,steps=count,label=provider))
    private fun phone(count:Long=200)=Observation("phone",Source.STEPS,"phone",Kind.STEP_DELTA,w.start+1,w.start+1000,now-200,payload=Payload(steps=count))
    private fun build(rows:List<Observation>,policy:Policy=Policy(archiveZone=zone.id))=DayEngine().build(date,policy,rows,emptyList(),emptyList(),emptyList(),emptyList(),now)
    @Test fun independentDailyTotalsAndSensorDeltasAreNeverAdded() {
        val day=build(listOf(total(HUAWEI_HEALTH,1000),total(HEALTH_CONNECT,1800),phone()))
        assertEquals(1000L,day.metrics.first { it.key=="steps" }.value)
        assertEquals(1000L,day.episodes.filter { it.kind=="STEPS" }.sumOf { it.amount ?: 0 })
        assertEquals(listOf(HUAWEI_HEALTH),day.metrics.first { it.key=="steps" }.evidenceIds)
    }
    @Test fun latestCorrectionCanDecreaseAndRealZeroDoesNotFallBack() {
        val corrected=total(HUAWEI_HEALTH,10,received=now-2)
        assertEquals(10L,build(listOf(total(HUAWEI_HEALTH,100),corrected,phone())).metrics.first { it.key=="steps" }.value)
        assertEquals(0L,build(listOf(total(HUAWEI_HEALTH,0),phone())).metrics.first { it.key=="steps" }.value)
    }
    @Test fun absentInvalidOrFutureTotalsFallBackWithoutInventingZero() {
        val malformed=total(HUAWEI_HEALTH,100).copy(endMs=w.end-1000)
        val future=total(HUAWEI_HEALTH,100).copy(receivedMs=now+1)
        assertEquals(200L,build(listOf(total(HUAWEI_HEALTH,-1),malformed,future,phone())).metrics.first { it.key=="steps" }.value)
        assertNull(build(emptyList()).metrics.first { it.key=="steps" }.value)
    }
    @Test fun legacyHealthConnectRowsAndSensorOnlyModeRemainReadable() {
        assertEquals(300L,build(listOf(total("",300),phone())).metrics.first { it.key=="steps" }.value)
        assertEquals(200L,build(listOf(phone(),phone())).metrics.first { it.key=="steps" }.value)
    }
    @Test fun disabledOrExcludedAccountSourceFallsBackToHardware() {
        val rows=listOf(total(HUAWEI_HEALTH,1000),phone())
        val disabled=Policy(archiveZone=zone.id,enabledSources=setOf(Source.STEPS))
        assertEquals(200L,build(rows,disabled).metrics.first { it.key=="steps" }.value)
        val excluded=Policy(archiveZone=zone.id,exclusions=listOf(Exclusion("x",w.start,w.end,Source.HEALTH)))
        assertEquals(200L,build(rows,excluded).metrics.first { it.key=="steps" }.value)
    }
    @Test fun archiveDayBoundariesRespectTimeZoneAndDst() {
        val ny=ZoneId.of("America/New_York");val day=window(LocalDate.of(2026,11,1),ny)
        assertEquals(25*3_600_000L,day.duration)
        val row=total(HUAWEI_HEALTH,100).copy(startMs=day.start,endMs=day.end,receivedMs=day.end-2)
        assertEquals(listOf(row),selectStepSources(listOf(row),ny,day.end-1))
        assertTrue(selectStepSources(listOf(row),ZoneId.of("UTC"),day.end-1).isEmpty())
    }
    @Test fun providerSnapshotsDoNotEraseTheOtherDay() {
        val yesterday=window(date.minusDays(1),zone)
        val previous=phone(50).copy(id="old",sourceKey="old",startMs=yesterday.start+10,endMs=yesterday.start+20)
        val selected=selectStepSources(listOf(total(HUAWEI_HEALTH,1000),phone(),previous),zone,now)
        assertTrue(previous in selected);assertFalse(phone() in selected)
    }
}
