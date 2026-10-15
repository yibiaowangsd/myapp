package com.personal.wo

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.hardware.biometrics.BiometricManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelProvider
import androidx.health.connect.client.PermissionController
import com.personal.wo.collector.Capabilities
import com.personal.wo.core.Source
import com.personal.wo.ui.*
import kotlinx.coroutines.launch

class MainActivity:ComponentActivity() {
    private lateinit var vm:WoViewModel
    private val unlocked=mutableStateOf(false)
    private var stoppedAt=0L
    private var prompt:CancellationSignal?=null
    private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh(true) }
    private val health=registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.refresh(true) }
    private val archive=registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { vm.export(it,true) } }
    private val summary=registerForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri -> uri?.let { vm.export(it,false) } }
    private val summaryJson=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.export(it,false,true) } }
    private val openArchive=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { restoreUri.value=it } }
    private val restoreUri=mutableStateOf<android.net.Uri?>(null)
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge();vm=ViewModelProvider(this)[WoViewModel::class.java]
        setContent {
            val state by vm.state.collectAsState()
            LaunchedEffect(state.loaded,state.policy.lockUi) { if(state.loaded) { if(state.policy.lockUi&&stoppedAt==0L) { unlocked.value=false;authenticate() } else if(!state.policy.lockUi)unlocked.value=true } }
            WoTheme { if(unlocked.value) WoAppUi(vm,{ authorize(it) },{ background() },{ kind -> when(kind) { "archive"->archive.launch("Wo_Backup.woarchive");"json"->summaryJson.launch("Wo_${state.selectedDate}.json");"restore"->openArchive.launch(arrayOf("*/*"));else->summary.launch("Wo_${state.selectedDate}.md") } },restoreUri.value,{restoreUri.value=null},{on->
                if(on&&!getSystemService(KeyguardManager::class.java).isDeviceSecure)vm.message("请先设置手机系统锁屏，再开启查看锁") else vm.lock(on)
            }) else UnlockScreen { authenticate() } }
        }
    }
    override fun onResume() { super.onResume();if(::vm.isInitialized) { if(vm.state.value.policy.lockUi&&stoppedAt>0&&SystemClock.elapsedRealtime()-stoppedAt>60_000) { unlocked.value=false;authenticate() };vm.foreground() } }
    override fun onStop() { stoppedAt=SystemClock.elapsedRealtime();prompt?.cancel();if(::vm.isInitialized)vm.background();ThumbnailCache.cache.evictAll();super.onStop() }
    private fun authenticate() {
        if(!getSystemService(KeyguardManager::class.java).isDeviceSecure) { unlocked.value=true;return }
        val builder=BiometricPrompt.Builder(this).setTitle("查看我的一天").setSubtitle("使用系统生物识别或锁屏凭据")
        if(Build.VERSION.SDK_INT>=30)builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) else builder.setDeviceCredentialAllowed(true)
        val signal=CancellationSignal();prompt=signal
        builder.build().authenticate(signal,mainExecutor,object:BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult) { unlocked.value=true;prompt=null }
        })
    }
    private fun background() { runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName"))) }.onFailure { vm.message("请在系统应用设置中将定位设为始终允许") } }
    private fun authorize(source:Source) {
        val ordinary=when(source) {
            Source.LOCATION->arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION)
            Source.STEPS->arrayOf(Manifest.permission.ACTIVITY_RECOGNITION)
            Source.PHOTO->if(Build.VERSION.SDK_INT>=34)arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            Source.CALENDAR->arrayOf(Manifest.permission.READ_CALENDAR)
            else->emptyArray()
        }
        if(source==Source.HEALTH) { runCatching { health.launch(Capabilities.healthPermissions()) }.onFailure { vm.message("此设备没有可用的Health Connect授权入口") };return }
        if(ordinary.isNotEmpty()) { permissions.launch(ordinary);return }
        runCatching { startActivity(Capabilities.special(this,source)) }.onFailure { vm.message("系统没有提供此授权入口，请使用应用设置") }
    }
    fun requestStatusNotification() { if(Build.VERSION.SDK_INT>=33)permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
    fun requestHealthBackground() { wo().scope.launch { runCatching {
        val client=androidx.health.connect.client.HealthConnectClient.getOrCreate(this@MainActivity)
        if(client.features.getFeatureStatus(androidx.health.connect.client.HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)==androidx.health.connect.client.HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) {
            val request=Capabilities.healthPermissions()+androidx.health.connect.client.permission.HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
            runOnUiThread { health.launch(request) }
        } else vm.message("此健康Provider不支持后台读取，打开应用时仍会同步")
    }.onFailure { vm.message("此设备没有可用的健康后台授权入口") } } }
}
