package com.personal.wo.collector

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import com.personal.wo.core.*
import com.personal.wo.probeSource

object Capabilities {
    fun granted(c:Context,p:String)=ContextCompat.checkSelfPermission(c,p)==PackageManager.PERMISSION_GRANTED
    fun usage(c:Context):Boolean=c.getSystemService(AppOpsManager::class.java).unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),c.packageName)==AppOpsManager.MODE_ALLOWED
    fun notices(c:Context)=c.packageName in NotificationManagerCompat.getEnabledListenerPackages(c)
    fun window(c:Context):Boolean=Settings.Secure.getString(c.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.split(":")?.any { ComponentName.unflattenFromString(it)==ComponentName(c,WindowListener::class.java) } ?: false
    fun location(c:Context)=granted(c,Manifest.permission.ACCESS_COARSE_LOCATION) || granted(c,Manifest.permission.ACCESS_FINE_LOCATION)
    fun steps(c:Context)=granted(c,Manifest.permission.ACTIVITY_RECOGNITION) && StepSensors.supported(c)
    fun healthPermissions():Set<String> = setOf(HealthPermission.getReadPermission(StepsRecord::class),HealthPermission.getReadPermission(SleepSessionRecord::class),HealthPermission.getReadPermission(ExerciseSessionRecord::class))
    suspend fun probe(c:Context,p:Policy):List<SourceState> {
        fun s(source:Source,grant:Boolean,support:Boolean=true,detail:String="",partial:Boolean=false)=SourceState(source,support,if(grant) if(partial) "PARTIAL" else "GRANTED" else "DENIED",if(!p.requested||source !in p.enabledSources) "STOPPED" else if(p.paused) "PAUSED" else if(grant&&support) "RUNNING" else "STOPPED",detail=detail)
        return Source.entries.map { source -> probeSource(source,p) { when(source) {
            Source.USAGE -> s(source,usage(c),detail="公开UsageEvents；统计活动区间并去除重叠")
            Source.LOCATION -> {
                val lm=c.getSystemService(LocationManager::class.java)
                s(source,location(c),lm!=null&&lm.allProviders.any { it!=LocationManager.PASSIVE_PROVIDER },if(lm?.isLocationEnabled!=true) "系统定位开关关闭" else "框架定位；无需Google服务")
            }
            Source.STEPS -> {
                val grant=granted(c,Manifest.permission.ACTIVITY_RECOGNITION)
                // Some vendor builds hide sensors until activity permission is granted.
                // Keep the authorization entry available before the first grant.
                val supported=!grant||StepSensors.supported(c)
                val active=p.requested&&!p.paused&&source in p.enabledSources&&grant&&supported
                s(source,grant,supported,when {
                    !supported->"系统没有向此应用开放硬件计步器或单步检测器"
                    !grant->"允许身体活动权限即可使用手机计步，无需 Health Connect"
                    active->StepSensors.detail
                    else->"手机计步独立工作，无需连接健康服务"
                }).let { if(active)it.copy(runtime=StepSensors.connection) else it }
            }
            Source.NOTIFICATION -> s(source,notices(c),detail="只记录授权后的通知片段；正文解析需要单独选择应用")
            Source.MEDIA -> s(source,notices(c),detail="通过媒体会话被动观察；不控制播放")
            Source.PHOTO -> {
                val photo=if(Build.VERSION.SDK_INT>=33)granted(c,Manifest.permission.READ_MEDIA_IMAGES)||granted(c,Manifest.permission.READ_MEDIA_VIDEO) else granted(c,Manifest.permission.READ_EXTERNAL_STORAGE)
                val partial=Build.VERSION.SDK_INT>=34&&granted(c,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)&&!photo
                s(source,photo||partial,detail=if(partial)"仅访问本人选定的媒体" else "索引元数据，不复制图库",partial=partial)
            }
            Source.CALENDAR -> s(source,granted(c,Manifest.permission.READ_CALENDAR),detail="日历只作为计划，不认定实际出席")
            Source.HEALTH -> {
                val available=HealthConnectClient.getSdkStatus(c)==HealthConnectClient.SDK_AVAILABLE
                val permissions=if(available)HealthConnectClient.getOrCreate(c).permissionController.getGrantedPermissions() else emptySet()
                s(source,permissions.isNotEmpty()&&permissions.any { it in healthPermissions() },available,if(!available)"Health Connect未提供；其他功能可以继续" else "步数聚合、运动与睡眠来源")
            }
            Source.ACCESSIBILITY -> s(source,window(c),detail="可选窗口包名；不读取屏幕正文、不执行手势")
        } } }
    }
    fun sourceName(s:Source)=when(s) { Source.USAGE->"应用活动";Source.LOCATION->"位置与足迹";Source.STEPS->"硬件步数";Source.NOTIFICATION->"通知线索";Source.MEDIA->"媒体播放";Source.PHOTO->"照片与视频";Source.CALENDAR->"日历计划";Source.HEALTH->"活动与健康";Source.ACCESSIBILITY->"窗口切换增强" }
    fun special(c:Context,source:Source):Intent=when(source) {
        Source.USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,android.net.Uri.parse("package:${c.packageName}"))
        Source.NOTIFICATION,Source.MEDIA -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        Source.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        Source.HEALTH -> Intent("android.health.connect.action.HEALTH_HOME_SETTINGS")
        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${c.packageName}"))
    }
}
