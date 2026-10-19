package com.personal.wo.health

import android.content.Context
import android.content.pm.PackageManager
import com.personal.wo.BuildConfig
import com.personal.wo.core.lowerHex
import com.personal.wo.data.WoRepository
import java.security.MessageDigest

object HuaweiHealthFactory {
    fun create(c:Context,repo:WoRepository):HuaweiHealthController {
        val matches=runCatching {
            val info=c.packageManager.getPackageInfo(c.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
            val signers=info.signingInfo?.apkContentsSigners.orEmpty()
            signers.size==1&&MessageDigest.getInstance("SHA-256").digest(signers.single().toByteArray()).lowerHex().equals(BuildConfig.HUAWEI_CERT_SHA256,true)
        }.getOrDefault(false)
        val approved=BuildConfig.HUAWEI_APPROVED_SCOPES.split(",").mapNotNull { name->HuaweiScope.entries.firstOrNull { it.name==name } }.toSet()
        return HuaweiHealthController(HuaweiConfig(BuildConfig.HUAWEI_APP_ID,approved,true,matches),
            HmsHuaweiGateway(c.applicationContext),HuaweiPreferences(c),HuaweiRepositorySink(repo))
    }
}
