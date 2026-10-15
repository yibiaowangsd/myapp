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
import com.personal.wo.collector.Capabilities
import com.personal.wo.collector.DirectPermissionPlan
import com.personal.wo.collector.OptionalHealthPermissionContract
import com.personal.wo.core.Source
import com.personal.wo.ui.*
import kotlinx.coroutines.launch

class MainActivity:ComponentActivity() {
    private lateinit var vm:WoViewModel
    private val unlocked=mutableStateOf(false)
    private var stoppedAt=0L
    private var prompt:CancellationSignal?=null
    private var quickActive=false
    private var quickStages=ArrayDeque<Source>()
    private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if(::vm.isInitialized) {
            if(quickActive)advanceQuickPermissions() else {
                vm.permissionsChanged()
                vm.message(if(result.values.any{it})"权限已更新" else "保留你的选择，稍后可在记录权限中调整")
            }
        }
    }
    private val health=registerForActivityResult(OptionalHealthPermissionContract()) { if(::vm.isInitialized)vm.permissionsChanged() }
    private val settingsResult=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { if(::vm.isInitialized) { if(quickActive)advanceQuickPermissions() else vm.permissionsChanged() } }
    private val archive=registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { vm.export(it,true) } }
    private val summary=registerForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri -> uri?.let { vm.export(it,false) } }
    private val summaryJson=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.export(it,false,true) } }
    private val openArchive=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { restoreUri.value=it } }
    private val restoreUri=mutableStateOf<android.net.Uri?>(null)
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        quickActive=savedInstanceState?.getBoolean("quickActive") ?: false
        quickStages=ArrayDeque(savedInstanceState?.getStringArrayList("quickStages")?.mapNotNull { runCatching { Source.valueOf(it) }.getOrNull() } ?: emptyList())
        recoverableCall { enableEdgeToEdge() }.onFailure { wo().diagnostics.record("窗口适配",it) }
        val created=recoverableCall { ViewModelProvider(this)[WoViewModel::class.java] }
        created.exceptionOrNull()?.let { error ->
            val report=wo().diagnostics.record("页面状态初始化",error)
            setContent { WoTheme { StartupFailureScreen(report,{recreate()},{copyDiagnostic(report)}) } }
            return
        }
        vm=created.getOrThrow()
        setContent {
            val state by vm.state.collectAsState()
            LaunchedEffect(state.loaded,state.policy.lockUi,state.startupError) { if(state.loaded&&state.startupError==null) { if(state.policy.lockUi&&stoppedAt==0L) { unlocked.value=false;authenticate() } else if(!state.policy.lockUi)unlocked.value=true } }
            LaunchedEffect(state.loaded,state.sources.size,unlocked.value) {
                if(state.loaded&&state.startupError==null&&unlocked.value&&state.sources.size==Source.entries.size&&
                    !getSharedPreferences("ui",MODE_PRIVATE).getBoolean("direct_permissions_v2",false)) requestMissingPermissions()
            }
            WoTheme { if(state.startupError!=null) StartupFailureScreen(state.startupError!!,vm::retryStartup,{copyDiagnostic(state.startupError!!)})
            else if(!state.loaded) StartupLoadingScreen()
            else if(unlocked.value) WoAppUi(vm,{ authorize(it) },{ background() },{ kind -> when(kind) { "archive"->archive.launch("Wo_Backup.woarchive");"json"->summaryJson.launch("Wo_${state.selectedDate}.json");"restore"->openArchive.launch(arrayOf("*/*"));else->summary.launch("Wo_${state.selectedDate}.md") } },restoreUri.value,{restoreUri.value=null},{on->
                if(on&&!getSystemService(KeyguardManager::class.java).isDeviceSecure)vm.message("请先设置手机系统锁屏，再开启查看锁") else vm.lock(on)
            }) else UnlockScreen { authenticate() } }
        }
    }
    override fun onSaveInstanceState(outState:Bundle) {
        outState.putBoolean("quickActive",quickActive)
        outState.putStringArrayList("quickStages",ArrayList(quickStages.map { it.name }))
        super.onSaveInstanceState(outState)
    }
    fun requestMissingPermissions() {
        if(quickActive||!::vm.isInitialized)return
        getSharedPreferences("ui",MODE_PRIVATE).edit().putBoolean("direct_permissions_v2",true).apply()
        val state=vm.state.value
        val enabled=state.policy.enabledSources
        quickActive=true
        quickStages=ArrayDeque(DirectPermissionPlan.special(enabled,state.sources))
        val asked=getSharedPreferences("permission_requests",MODE_PRIVATE)
        val wanted=DirectPermissionPlan.ordinary(Build.VERSION.SDK_INT,enabled,state.sources.filter { it.supported }.map { it.source }.toSet())
            .filter { !Capabilities.granted(this,it)&&(!asked.getBoolean(it,false)||shouldShowRequestPermissionRationale(it)) }.toMutableList()
        if(Manifest.permission.ACCESS_FINE_LOCATION in wanted&&Manifest.permission.ACCESS_COARSE_LOCATION !in wanted)wanted+=Manifest.permission.ACCESS_COARSE_LOCATION
        if(wanted.isEmpty())advanceQuickPermissions()
        else {
            asked.edit().apply { wanted.forEach { putBoolean(it,true) } }.apply()
            recoverableCall { permissions.launch(wanted.toTypedArray()) }.onFailure { advanceQuickPermissions() }
        }
    }
    private fun advanceQuickPermissions() {
        val source=quickStages.removeFirstOrNull()
        if(source!=null) {
            recoverableCall { settingsResult.launch(Capabilities.special(this,source)) }.onFailure {
                // One attempt per page; a missing OEM activity must never trap the user.
                advanceQuickPermissions()
            }
        } else {
            quickActive=false
            vm.completeDirectSetup()
        }
    }
    private fun copyDiagnostic(report:String) {
        recoverableCall {
            getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("我 · 启动诊断",report))
            android.widget.Toast.makeText(this,"诊断已复制，不含生活记录与密钥",android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    override fun onResume() { super.onResume();if(::vm.isInitialized) { if(vm.state.value.policy.lockUi&&stoppedAt>0&&SystemClock.elapsedRealtime()-stoppedAt>60_000) { unlocked.value=false;authenticate() };vm.foreground() } }
    override fun onStop() { stoppedAt=SystemClock.elapsedRealtime();prompt?.cancel();if(::vm.isInitialized)vm.background();ThumbnailCache.cache.evictAll();super.onStop() }
    private fun authenticate() {
        recoverableCall { showAuthentication() }.onFailure { wo().diagnostics.record("查看身份验证",it);if(::vm.isInitialized)vm.message("系统身份验证暂不可用，请重试") }
    }
    private fun showAuthentication() {
        if(!getSystemService(KeyguardManager::class.java).isDeviceSecure) { unlocked.value=true;return }
        val builder=BiometricPrompt.Builder(this).setTitle("查看我的一天").setSubtitle("使用系统生物识别或锁屏凭据")
        if(Build.VERSION.SDK_INT>=30)builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) else builder.setDeviceCredentialAllowed(true)
        val signal=CancellationSignal();prompt=signal
        builder.build().authenticate(signal,mainExecutor,object:BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult) { unlocked.value=true;prompt=null }
        })
    }
    private fun background() { runCatching { settingsResult.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName"))) }.onFailure { vm.message("请在系统应用设置中将定位设为始终允许") } }
    private fun authorize(source:Source) {
        val ordinary=when(source) {
            Source.LOCATION->arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION)
            Source.STEPS->arrayOf(Manifest.permission.ACTIVITY_RECOGNITION)
            Source.PHOTO->if(Build.VERSION.SDK_INT>=34)arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            Source.CALENDAR->arrayOf(Manifest.permission.READ_CALENDAR)
            else->emptyArray()
        }
        if(source==Source.HEALTH) { recoverableCall { health.launch(Capabilities.healthPermissions()) }.onFailure { vm.message("此设备没有可用的Health Connect授权入口") };return }
        if(ordinary.isNotEmpty()) {
            val asked=getSharedPreferences("permission_requests",MODE_PRIVATE)
            val missing=ordinary.filterNot { Capabilities.granted(this,it) }
            val ready=if(source==Source.LOCATION)Capabilities.location(this) else missing.isEmpty()
            val blocked=missing.any { asked.getBoolean(it,false)&&!shouldShowRequestPermissionRationale(it) }
            if(ready||blocked) {
                runCatching { settingsResult.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName"))) }
                    .onFailure { vm.message("请在系统设置中打开“我”的应用权限") }
            } else {
                asked.edit().apply { ordinary.forEach { putBoolean(it,true) } }.apply()
                recoverableCall { permissions.launch(ordinary) }.onFailure { vm.message("权限页面暂时无法打开，请稍后重试") }
            }
            return
        }
        runCatching { settingsResult.launch(Capabilities.special(this,source)) }.onFailure { vm.message("系统没有提供此授权入口，请使用应用设置") }
    }
    fun requestStatusNotification() {
        val permission=Manifest.permission.POST_NOTIFICATIONS
        val asked=getSharedPreferences("permission_requests",MODE_PRIVATE)
        val canRequest=Build.VERSION.SDK_INT>=33&&!Capabilities.granted(this,permission)&&
            (!asked.getBoolean(permission,false)||shouldShowRequestPermissionRationale(permission))
        recoverableCall {
            if(canRequest) {
                asked.edit().putBoolean(permission,true).apply()
                permissions.launch(arrayOf(permission))
            } else {
                settingsResult.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
            }
        }.onFailure { vm.message("请在系统通知设置中查看“我”的记录状态通知") }
    }
    fun requestHealthBackground() { wo().scope.launch { runCatching {
        val client=androidx.health.connect.client.HealthConnectClient.getOrCreate(this@MainActivity)
        if(client.features.getFeatureStatus(androidx.health.connect.client.HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)==androidx.health.connect.client.HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) {
            val request=Capabilities.healthPermissions()+androidx.health.connect.client.permission.HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
            runOnUiThread { health.launch(request) }
        } else vm.message("此健康Provider不支持后台读取，打开应用时仍会同步")
    }.onFailure { vm.message("此设备没有可用的健康后台授权入口") } } }
}
