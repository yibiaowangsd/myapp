package com.personal.wo.health

import android.content.Intent
import com.personal.wo.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

enum class HuaweiScope(val uri:String, val kind:Kind?=null) {
    STEPS("https://www.huawei.com/healthkit/step.read",Kind.HEALTH_STEPS),
    EXERCISE("https://www.huawei.com/healthkit/activityrecord.read",Kind.EXERCISE),
    SLEEP("https://www.huawei.com/healthkit/sleep.read",Kind.SLEEP),
    HISTORY_WEEK("https://www.huawei.com/healthkit/historydata.open.week")
}
enum class HuaweiPhase { NOT_CONFIGURED, UNAVAILABLE, DISCONNECTED, AUTHORIZING, AUTHORIZED, PARTIAL, DENIED, CANCELLED, ERROR, REVOKE_PENDING }
data class HuaweiState(val phase:HuaweiPhase=HuaweiPhase.DISCONNECTED,val granted:Set<HuaweiScope> = emptySet(),val detail:String="尚未授权华为账号健康数据",val lastSyncMs:Long?=null) {
    val label:String get()=when(phase) {
        HuaweiPhase.NOT_CONFIGURED->"待平台开通";HuaweiPhase.UNAVAILABLE->"此版本或设备不可用"
        HuaweiPhase.DISCONNECTED->"未连接";HuaweiPhase.AUTHORIZING->"等待官方授权"
        HuaweiPhase.AUTHORIZED->"已授权";HuaweiPhase.PARTIAL->"部分授权"
        HuaweiPhase.DENIED->"授权已失效或未允许";HuaweiPhase.CANCELLED->"已取消授权流程"
        HuaweiPhase.ERROR->"暂时无法验证";HuaweiPhase.REVOKE_PENDING->"已停止读取，撤权待重试"
    }
}
data class HuaweiConfig(val appId:String="",val approved:Set<HuaweiScope> = emptySet(),val sdkAvailable:Boolean=false,val certificateMatches:Boolean=false) {
    val blockingReason:String? get()=when {
        !sdkAvailable->"离线变体不包含华为SDK，请使用增强变体；手机计步继续独立工作"
        !appId.matches(Regex("[0-9]+"))||approved.none { it.kind!=null }->"尚未提供真实App ID和获批读取范围，不能发起账号授权"
        !certificateMatches->"当前APK签名与登记指纹不一致，请使用登记证书签名或登记本机调试证书"
        else->null
    }
}
data class HuaweiSession(val accountKey:String,val granted:Set<HuaweiScope>,val healthAppAuthorized:Boolean)
data class HuaweiRecord(val id:String,val kind:Kind,val startMs:Long,val endMs:Long,val steps:Long?=null,val label:String="")
data class HuaweiPage(val records:List<HuaweiRecord>,val complete:Boolean=false)
class HuaweiAccessException(val denied:Boolean,val code:Int?=null):Exception("Huawei Health API ${code ?: "unavailable"}")

/** Only the enhanced flavor implements these methods with the official SDK. */
interface HuaweiGateway {
    fun authorizationIntent(scopes:Set<HuaweiScope>):Intent
    fun authorizationSucceeded(data:Intent?):Boolean
    suspend fun session():HuaweiSession
    suspend fun read(scope:HuaweiScope,span:Span,zone:ZoneId):HuaweiPage
    suspend fun revoke()
}
interface HuaweiStore {
    var enabled:Boolean
    var revokePending:Boolean
    var accountKey:String
    var consentedAt:Long
}
interface HuaweiSink {
    suspend fun retain(kinds:Set<Kind>)
    suspend fun save(accountKey:String,scope:HuaweiScope,span:Span,page:HuaweiPage,zone:ZoneId,at:Long,generation:Long)
}

/** No token or cached GRANTED flag is persisted. Every foreground sync rechecks HMS and Health. */
class HuaweiHealthController(val config:HuaweiConfig,private val gateway:HuaweiGateway,private val store:HuaweiStore,
    private val sink:HuaweiSink,private val clock:()->Long=System::currentTimeMillis) {
    private val operations=Mutex()
    @Volatile private var epoch=0L
    private var retainedKinds:Set<Kind>?=null
    private val mutable=MutableStateFlow(initialState())
    val state=mutable.asStateFlow()
    private fun initialState()=config.blockingReason?.let { HuaweiState(if(config.sdkAvailable)HuaweiPhase.NOT_CONFIGURED else HuaweiPhase.UNAVAILABLE,detail=it) }
        ?: if(store.revokePending)HuaweiState(HuaweiPhase.REVOKE_PENDING,detail="本机已停止读取，请重试向华为撤销授权") else HuaweiState()
    fun beginAuthorization():Intent {
        check(!store.revokePending) { "请先重试撤销授权，再连接账号" }
        check(config.blockingReason==null) { config.blockingReason.orEmpty() }
        epoch++
        store.enabled=false
        mutable.value=HuaweiState(HuaweiPhase.AUTHORIZING,detail="请在华为官方页面选择允许的数据")
        return try { gateway.authorizationIntent(config.approved) } catch(e:Exception) {
            mutable.value=HuaweiState(HuaweiPhase.ERROR,detail="官方授权页面暂不可用，请检查HMS Core和运动健康版本");throw e
        }
    }
    suspend fun finishAuthorization(ok:Boolean,data:Intent?) {
        // A callback after local disconnect/revoke must not reconnect the app.
        if(mutable.value.phase!=HuaweiPhase.AUTHORIZING)return
        if(!ok||data==null) {
            epoch++;store.enabled=false
            operations.withLock { sink.retain(emptySet()) }
            mutable.value=HuaweiState(HuaweiPhase.CANCELLED,detail="未取得新的授权；手机计步继续使用")
            return
        }
        val succeeded=try { gateway.authorizationSucceeded(data) } catch(_:Exception) { false }
        if(!succeeded) {
            epoch++;store.enabled=false
            operations.withLock { sink.retain(emptySet()) }
            mutable.value=HuaweiState(HuaweiPhase.DENIED,detail="华为未确认授权，请检查平台开通与用户选择")
            return
        }
        store.enabled=true
        if(store.consentedAt==0L)store.consentedAt=clock()
        verifyAndSync(null,0,read=false)
    }
    suspend fun sync(zone:ZoneId,generation:Long,foreground:Boolean) {
        // No background sign-in, hidden authorization UI, or continuous HMS polling.
        if(!foreground||!store.enabled||store.revokePending||config.blockingReason!=null)return
        verifyAndSync(zone,generation,read=true)
    }
    private suspend fun verifyAndSync(zone:ZoneId?,generation:Long,read:Boolean)=operations.withLock {
        val ticket=epoch
        try {
            val session=gateway.session()
            if(ticket!=epoch||!store.enabled||store.revokePending)return@withLock
            if(!session.healthAppAuthorized||session.accountKey.isBlank())throw HuaweiAccessException(true)
            val granted=session.granted.intersect(config.approved)
            val dataScopes=granted.filter { it.kind!=null }.toSet()
            if(dataScopes.isEmpty())throw HuaweiAccessException(true)
            if(store.accountKey.isNotBlank()&&store.accountKey!=session.accountKey) {
                sink.retain(emptySet());store.consentedAt=clock()
            }
            store.accountKey=session.accountKey
            val kinds=dataScopes.mapNotNull { it.kind }.toSet()
            if(retainedKinds!=kinds) { sink.retain(kinds);retainedKinds=kinds }
            mutable.value=HuaweiState(if(granted.containsAll(config.approved))HuaweiPhase.AUTHORIZED else HuaweiPhase.PARTIAL,
                granted,"已验证允许读取：${dataScopes.joinToString { when(it) { HuaweiScope.STEPS->"步数";HuaweiScope.EXERCISE->"运动";else->"睡眠" } }}；不代表已有数据")
            if(!read||zone==null)return@withLock
            val now=clock();val today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            var anyData=false;var failed=false
            for(scope in dataScopes) {
                // Bounded day requests also preserve DST and cross-midnight sessions.
                for(i in 6 downTo 0) {
                    val day=window(today.minusDays(i.toLong()),zone)
                    val from=if(HuaweiScope.HISTORY_WEEK in granted)day.start else maxOf(day.start,store.consentedAt)
                    val until=minOf(day.end,now)
                    if(from>=until)continue
                    // A partial first day is not misrepresented as a whole-day step total.
                    if(scope==HuaweiScope.STEPS&&from!=day.start)continue
                    if(ticket!=epoch||!store.enabled)return@withLock
                    try {
                        val page=gateway.read(scope,Span(from,until),zone)
                        if(ticket!=epoch||!store.enabled)return@withLock
                        require(page.records.size<=200) { "Huawei result limit" }
                        require(page.records.all { it.kind==scope.kind&&it.id.isNotBlank()&&it.startMs>=0&&it.endMs>it.startMs&&
                            it.startMs<until&&it.endMs>from&&(it.steps==null||it.steps>=0)&&
                            (scope!=HuaweiScope.STEPS||(it.startMs==day.start&&it.endMs==day.end&&it.steps!=null))&&
                            (scope==HuaweiScope.STEPS||it.endMs<=now)&&
                            (HuaweiScope.HISTORY_WEEK in granted||it.startMs>=store.consentedAt) }) { "Invalid Huawei records" }
                        sink.save(session.accountKey,scope,if(scope==HuaweiScope.STEPS)day else Span(from,until),page,zone,now,generation)
                        anyData=anyData||page.records.isNotEmpty()
                    } catch(e:CancellationException) { throw e } catch(e:HuaweiAccessException) {
                        if(e.denied)throw e
                        failed=true
                    } catch(_:Exception) { failed=true }
                }
            }
            mutable.value=mutable.value.copy(lastSyncMs=if(failed)null else now,detail=when {
                failed->"部分查询未完成，已有记录保留；请检查网络、授权范围或稍后重试"
                anyData->"已读取获准范围的数据；华为聚合步数不与手机计步相加"
                else->"授权有效，查询范围内暂无可读数据；没有补造步数或睡眠记录"
            })
        } catch(e:CancellationException) { throw e } catch(e:Exception) {
            if(ticket!=epoch)return@withLock
            val denied=e is HuaweiAccessException&&e.denied
            if(denied) { store.enabled=false;sink.retain(emptySet()) }
            mutable.value=HuaweiState(if(denied)HuaweiPhase.DENIED else HuaweiPhase.ERROR,detail=if(denied)
                "授权失效、被撤回或无读取权限；已停止导入并清理本机华为派生数据" else "暂时无法验证华为授权，未发起数据读取；请检查网络后重试")
        }
    }
    suspend fun revoke() {
        // Invalidate reads immediately, before waiting for any outstanding SDK task.
        epoch++;store.enabled=false;store.revokePending=true
        mutable.value=HuaweiState(HuaweiPhase.REVOKE_PENDING,detail="已停止读取，正在清理本机华为数据并向华为撤权")
        operations.withLock {
            sink.retain(emptySet())
            try {
                gateway.revoke()
                store.revokePending=false;store.accountKey="";store.consentedAt=0
                mutable.value=HuaweiState(detail="已撤销华为授权，手机计步继续使用")
            } catch(e:CancellationException) { throw e } catch(_:Exception) {
                mutable.value=HuaweiState(HuaweiPhase.REVOKE_PENDING,detail="本机已停止读取；华为端撤权未确认，请重试或到运动健康授权管理中撤销")
            }
        }
    }
}
