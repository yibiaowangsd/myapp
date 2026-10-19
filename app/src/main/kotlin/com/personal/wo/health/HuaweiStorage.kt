package com.personal.wo.health

import android.content.Context
import com.personal.wo.core.*
import com.personal.wo.data.WoRepository
import java.time.ZoneId

class HuaweiPreferences(c:Context):HuaweiStore {
    private val prefs=c.getSharedPreferences("huawei_health_connection",Context.MODE_PRIVATE)
    override var enabled:Boolean get()=prefs.getBoolean("enabled",false);set(v){prefs.edit().putBoolean("enabled",v).commit()}
    override var revokePending:Boolean get()=prefs.getBoolean("revokePending",false);set(v){prefs.edit().putBoolean("revokePending",v).commit()}
    override var accountKey:String get()=prefs.getString("accountKey","").orEmpty();set(v){prefs.edit().putString("accountKey",v).commit()}
    override var consentedAt:Long get()=prefs.getLong("consentedAt",0);set(v){prefs.edit().putLong("consentedAt",v).commit()}
}
class HuaweiRepositorySink(private val repo:WoRepository,
    private val sourceKey:(String,String,Kind,String)->String={account,zone,kind,id->repo.vault.sourceKey("huawei.health",account,zone,kind,id)}):HuaweiSink {
    override suspend fun retain(kinds:Set<Kind>) { repo.retainHuaweiHealth(kinds) }
    override suspend fun save(accountKey:String,scope:HuaweiScope,span:Span,page:HuaweiPage,zone:ZoneId,at:Long,generation:Long) {
        val rows=page.records.map { r ->
            val key=sourceKey(accountKey,zone.id,r.kind,r.id)
            Observation(stableId(Source.HEALTH,key),Source.HEALTH,key,r.kind,r.startMs,r.endMs,at,eventZoneId=zone.id,
                quality=listOf(HUAWEI_HEALTH),payload=Payload(provider=HUAWEI_HEALTH,steps=r.steps,label=r.label))
        }
        // Empty or unconfirmed native pages never delete previously read sessions.
        repo.ingest(rows,generation,Source.HEALTH,replaceSpan=if(page.complete)span else null,
            replaceHealthProvider=HUAWEI_HEALTH,replaceKinds=setOfNotNull(scope.kind))
    }
}
