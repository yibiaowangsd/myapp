package com.personal.wo.collector

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.personal.wo.recoverableCall

// Construct the optional Health Connect contract only when the user requests it.
// Registration in MainActivity must also work on devices without that provider.
class OptionalHealthPermissionContract : ActivityResultContract<Set<String>, Set<String>>() {
    private var delegate: ActivityResultContract<Set<String>, Set<String>>? = null
    private fun contract() = delegate ?: PermissionController.createRequestPermissionResultContract().also { delegate=it }

    override fun createIntent(context: Context, input: Set<String>): Intent {
        check(HealthConnectClient.getSdkStatus(context)==HealthConnectClient.SDK_AVAILABLE) { "此设备没有可用的Health Connect授权入口" }
        return contract().createIntent(context,input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Set<String> =
        recoverableCall { contract().parseResult(resultCode,intent) }.getOrDefault(emptySet())
}
