package com.personal.wo.health

import android.content.Context
import android.content.Intent
import com.huawei.hmf.tasks.Task
import com.huawei.hms.common.ApiException
import com.huawei.hms.hihealth.HuaweiHiHealth
import com.huawei.hms.hihealth.data.DataType
import com.huawei.hms.hihealth.data.Field
import com.huawei.hms.hihealth.data.HealthDataTypes
import com.huawei.hms.hihealth.data.SamplePoint
import com.huawei.hms.hihealth.options.ActivityRecordReadOptions
import com.huawei.hms.hihealth.options.HealthRecordReadOptions
import com.huawei.hms.support.account.AccountAuthManager
import com.huawei.hms.support.account.request.AccountAuthParamsHelper
import com.personal.wo.core.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Read-only native Android adapter. No cloud secret, manual token storage, or synthetic records. */
class HmsHuaweiGateway(private val c:Context):HuaweiGateway {
    private val settings get()=HuaweiHiHealth.getSettingController(c)
    private val account get()=AccountAuthManager.getService(c,AccountAuthParamsHelper().setAccessToken().createParams())
    override fun authorizationIntent(scopes:Set<HuaweiScope>):Intent =
        settings.requestAuthorizationIntent(scopes.map { it.uri }.toTypedArray(),true)
    override fun authorizationSucceeded(data:Intent?)=data!=null&&settings.parseHealthKitAuthResultFromIntent(data)?.isSuccess==true
    override suspend fun session():HuaweiSession {
        val user=account.silentSignIn().awaitHms()
        val scopes=user.authorizedScopes.orEmpty().map { it.scopeUri }.toSet()
        val authorized=settings.getHealthAppAuthorization().awaitHms()
        return HuaweiSession(user.openId?.takeIf { it.isNotBlank() }?.let { stableId("huawei.account",it) }.orEmpty(),
            HuaweiScope.entries.filter { it.uri in scopes }.toSet(),authorized==true)
    }
    override suspend fun revoke() { account.cancelAuthorization().awaitHms() }
    override suspend fun read(scope:HuaweiScope,span:Span,zone:ZoneId):HuaweiPage=when(scope) {
        HuaweiScope.STEPS->steps(span,zone)
        HuaweiScope.EXERCISE->{
            val options=ActivityRecordReadOptions.Builder().setTimeInterval(span.start,span.end,MILLISECONDS)
                .readActivityRecordsFromAllApps().allowRemoteInquiry().build()
            val reply=HuaweiHiHealth.getActivityRecordsController(c).getActivityRecord(options).awaitHms()
            val records=reply.activityRecords.orEmpty()
            require(records.size<=200) { "Native exercise page requires pagination" }
            HuaweiPage(records.filter { !it.isKeepGoing }.map {
                HuaweiRecord(it.id.orEmpty(),Kind.EXERCISE,it.getStartTime(MILLISECONDS),it.getEndTime(MILLISECONDS),label="华为运动健康 · ${it.name.orEmpty().take(100).ifBlank { it.activityType.orEmpty() }}")
            })
        }
        HuaweiScope.SLEEP->{
            val options=HealthRecordReadOptions.Builder().setTimeInterval(span.start,span.end,MILLISECONDS)
                .readHealthRecordsFromAllApps().readByDataType(HealthDataTypes.DT_HEALTH_RECORD_SLEEP).allowRemoteInquiry().build()
            val reply=HuaweiHiHealth.getHealthRecordController(c).getHealthRecord(options).awaitHms()
            val records=reply.healthRecords.orEmpty()
            require(records.size<=200) { "Native sleep page requires pagination" }
            HuaweiPage(records.map { HuaweiRecord(it.healthRecordId.orEmpty(),Kind.SLEEP,it.getStartTime(MILLISECONDS),
                it.getEndTime(MILLISECONDS),label="华为运动健康睡眠记录") })
        }
        HuaweiScope.HISTORY_WEEK->error("History is a scope, not a data type")
    }
    private suspend fun steps(span:Span,zone:ZoneId):HuaweiPage {
        val date=Instant.ofEpochMilli(span.start).atZone(zone).toLocalDate()
        val day=window(date,zone)
        val encoded=date.format(DateTimeFormatter.BASIC_ISO_DATE).toInt()
        val samples=HuaweiHiHealth.getDataController(c).readDailySummation(DataType.DT_CONTINUOUS_STEPS_DELTA,encoded,encoded).awaitHms().samplePoints.orEmpty()
        return normalizeHuaweiDailySteps(samples,day,date.toString())
    }
}

/** Unset SDK Values can default to 0. Only an explicitly set integer is a real zero. */
internal fun normalizeHuaweiDailySteps(samples:List<SamplePoint>,day:Span,date:String):HuaweiPage {
        // HMS daily boundaries can follow the device zone. Reject a different archive day.
        val points=samples.filter { it.getStartTime(MILLISECONDS)==day.start&&it.getEndTime(MILLISECONDS) in (day.end-1)..day.end }
        require(points.size<=1) { "Ambiguous daily totals; do not add device streams" }
        val point=points.singleOrNull() ?: return HuaweiPage(emptyList())
        // Use SDK field lookup: native maps may encode the field name with a suffix.
        // DT_CONTINUOUS_STEPS_DELTA normally carries FIELD_STEPS_DELTA.
        val values=listOf(Field.FIELD_STEPS_DELTA,Field.FIELD_STEPS).mapNotNull { field->
            runCatching { point.getFieldValue(field) }.getOrNull()?.takeIf { it.isSet&&it.format==Field.FORMAT_INT32 }
        }.map { it.asIntValue().toLong() }.distinct()
        require(values.size<=1) { "Ambiguous step fields" }
        val count=values.singleOrNull() ?: return HuaweiPage(emptyList())
        require(count>=0)
        return HuaweiPage(listOf(HuaweiRecord("daily:$date",Kind.HEALTH_STEPS,day.start,day.end,count,"华为运动健康")),complete=true)
}

private suspend fun <T> Task<T>.awaitHms():T=withTimeout(10_000) {
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if(continuation.isActive)continuation.resume(it) }
        addOnFailureListener { error ->
            val code=(error as? ApiException)?.statusCode
            val denied=code in setOf(4,50000,50037,50045,50046,50047,50064,50065)
            if(continuation.isActive)continuation.resumeWithException(HuaweiAccessException(denied,code))
        }
    }
}
