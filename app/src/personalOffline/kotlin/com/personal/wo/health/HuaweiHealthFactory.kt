package com.personal.wo.health

import android.content.Context
import android.content.Intent
import com.personal.wo.core.Span
import com.personal.wo.data.WoRepository
import java.time.ZoneId

object HuaweiHealthFactory {
    fun create(c:Context,repo:WoRepository)=HuaweiHealthController(HuaweiConfig(),object:HuaweiGateway {
        override fun authorizationIntent(scopes:Set<HuaweiScope>):Intent=error("Huawei SDK absent in offline flavor")
        override fun authorizationSucceeded(data:Intent?)=false
        override suspend fun session():HuaweiSession=error("Huawei SDK absent")
        override suspend fun read(scope:HuaweiScope,span:Span,zone:ZoneId):HuaweiPage=error("Huawei SDK absent")
        override suspend fun revoke()=error("Huawei SDK absent")
    },HuaweiPreferences(c),HuaweiRepositorySink(repo))
}
