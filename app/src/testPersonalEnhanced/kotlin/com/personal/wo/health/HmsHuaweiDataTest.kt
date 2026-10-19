package com.personal.wo.health

import android.app.Application
import com.huawei.hms.hihealth.data.DataType
import com.huawei.hms.hihealth.data.DataCollector
import com.huawei.hms.hihealth.data.Field
import com.huawei.hms.hihealth.data.SamplePoint
import com.huawei.hms.hihealth.data.Scopes
import com.personal.wo.core.window
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit.MILLISECONDS

/** Exercises real SDK value/interval classes, without any account or authorization simulation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class HmsHuaweiDataTest {
    @Test fun scopesMatchTheInstalledNativeSdkReadOnlyConstants() {
        assertEquals(Scopes.HEALTHKIT_STEP_READ,HuaweiScope.STEPS.uri)
        assertEquals(Scopes.HEALTHKIT_ACTIVITY_RECORD_READ,HuaweiScope.EXERCISE.uri)
        assertEquals(Scopes.HEALTHKIT_SLEEP_READ,HuaweiScope.SLEEP.uri)
        assertEquals(Scopes.HEALTHKIT_HISTORYDATA_OPEN_WEEK,HuaweiScope.HISTORY_WEEK.uri)
        assertTrue(HuaweiScope.entries.none { it.uri.endsWith(".write")||it.uri.endsWith(".both") })
    }
    private val date=LocalDate.of(2026,11,1)
    private val day=window(date,ZoneId.of("America/New_York"))
    private fun point(count:Int?,start:Long=day.start,end:Long=day.end):SamplePoint {
        val collector=DataCollector.Builder().setDataType(DataType.DT_CONTINUOUS_STEPS_DELTA)
            .setDataGenerateType(DataCollector.DATA_TYPE_RAW).setPackageName("com.personal.wo").build()
        val builder=SamplePoint.builder(collector).setTimeInterval(start,end,MILLISECONDS)
        if(count!=null)builder.setFieldValue(Field.FIELD_STEPS_DELTA,count)
        return builder.build()
    }
    @Test fun explicitZeroAndInclusiveLastMillisecondAreAccepted() {
        val zero=point(0,end=day.end-1)
        assertEquals("start",day.start,zero.getStartTime(MILLISECONDS))
        assertEquals("end",day.end-1,zero.getEndTime(MILLISECONDS))
        val value=zero.getFieldValue(Field.FIELD_STEPS_DELTA)
        assertTrue("explicit zero must be set: ${zero.fieldValues}",value.isSet)
        assertEquals("integer format",Field.FORMAT_INT32,value.format)
        val result=normalizeHuaweiDailySteps(listOf(zero),day,date.toString())
        assertEquals(0L,result.records.single().steps);assertTrue(result.complete)
        assertEquals(day.end,result.records.single().endMs)
    }
    @Test fun unsetCountOrAbsentDayDoesNotInventZero() {
        assertTrue(normalizeHuaweiDailySteps(listOf(point(null)),day,date.toString()).records.isEmpty())
        assertTrue(normalizeHuaweiDailySteps(emptyList(),day,date.toString()).records.isEmpty())
    }
    @Test fun timezoneMismatchDoesNotBecomeArchiveDayData() {
        val other=point(123,start=day.start+3_600_000,end=day.end+3_600_000)
        assertTrue(normalizeHuaweiDailySteps(listOf(other),day,date.toString()).records.isEmpty())
    }
    @Test fun ambiguousDeviceTotalsAreNotAdded() {
        try { normalizeHuaweiDailySteps(listOf(point(123),point(321)),day,date.toString());fail() }
        catch(_:IllegalArgumentException){}
    }
}
