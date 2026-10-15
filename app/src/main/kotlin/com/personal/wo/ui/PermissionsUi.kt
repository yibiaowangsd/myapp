package com.personal.wo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.personal.wo.core.*

internal val primarySources=listOf(Source.USAGE,Source.LOCATION,Source.STEPS,Source.PHOTO)
internal fun sourceTitle(source:Source)=when(source) {
    Source.USAGE->"手机使用";Source.LOCATION->"位置与足迹";Source.STEPS->"步数";
    Source.PHOTO->"照片与视频";Source.NOTIFICATION->"通知线索";Source.MEDIA->"音乐与播放";
    Source.CALENDAR->"日历计划";Source.HEALTH->"健康数据（可选）";Source.ACCESSIBILITY->"应用识别增强"
}
internal fun sourceBenefit(source:Source)=when(source) {
    Source.USAGE->"看见每个应用用了多久，时间花在了哪里。"
    Source.LOCATION->"按时间串起停留地点，留下今天的足迹。"
    Source.STEPS->"手机直接计步，无需连接 Health Connect 或华为运动健康。"
    Source.PHOTO->"将当天的照片和视频关联到生活片段。"
    Source.NOTIFICATION->"从通知中留下送达、出行等生活线索。"
    Source.MEDIA->"记录音乐和视频的播放片段。"
    Source.CALENDAR->"在时间线显示日历计划。计划会单独标注。"
    Source.HEALTH->"读取健康来源提供的运动、步数与睡眠记录。"
    Source.ACCESSIBILITY->"补充应用窗口切换记录，通常无需开启。"
}
@Composable fun PermissionCenter(s:WoUiState,authorize:(Source)->Unit,onEnabled:(Source,Boolean)->Unit,background:()->Unit,statusNotification:()->Unit,record:()->Unit,healthBackground:()->Unit={},requestAll:()->Unit={},reconnectSteps:()->Unit={}) {
    Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f).testTag("permissions-list"),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item {
            Text("记录权限",style=MaterialTheme.typography.headlineMedium)
            Muted("直接打开系统授权，允许后自动生效。未开启的来源也可单独调整。",Modifier.padding(top=8.dp,bottom=8.dp))
            Button(onClick=requestAll,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("request-all")){Text("申请未开启的权限")}
        }
        items(primarySources+Source.entries.filter{it !in primarySources},key={it.name}) { source ->
            val state=s.sources.firstOrNull{it.source==source}
            SourcePermissionCard(source,state,source in s.policy.enabledSources,{on ->
                onEnabled(source,on)
                if(on&&!sourceReady(state))authorize(source)
            },{
                if(source !in s.policy.enabledSources)onEnabled(source,true)
                authorize(source)
            })
            if(source==Source.STEPS&&sourceReady(state)) {
                TextButton(onClick=reconnectSteps,modifier=Modifier.fillMaxWidth().testTag("reconnect-steps")){Text("重新连接手机计步")}
            }
        }
        item { Section("后台记录");Paper {
            Text("让锁屏后的片段继续积累",style=MaterialTheme.typography.titleMedium)
            Muted("华为手机可在系统应用启动管理中允许“我”后台运行。位置来源需要先取得前台定位权限。")
            OutlinedButton(onClick=background,enabled=sourceReady(s.sources.firstOrNull{it.source==Source.LOCATION}),modifier=Modifier.fillMaxWidth()){Text("设置后台定位与运行")}
            TextButton(onClick=statusNotification,modifier=Modifier.fillMaxWidth()){Text("允许记录状态通知")}
            if(sourceReady(s.sources.firstOrNull{it.source==Source.HEALTH}))TextButton(onClick=healthBackground,modifier=Modifier.fillMaxWidth()){Text("允许健康来源在后台读取")}
        } }
    }
    Surface(color=MaterialTheme.colorScheme.background) {
        Button(onClick=record,enabled=!s.busy&&s.sources.any{sourceReady(it)&&it.source in s.policy.enabledSources},modifier=Modifier.fillMaxWidth().padding(vertical=12.dp).heightIn(min=52.dp)) {
            Text(if(!s.policy.requested) "开始记录" else if(s.policy.paused) "继续记录" else "同步已授权的来源")
        }
    }
    }
}

@Composable internal fun SourcePermissionCard(source:Source,state:SourceState?,selected:Boolean,onSelected:(Boolean)->Unit,onAuthorize:()->Unit) {
    val supported=state?.supported!=false
    val ready=sourceReady(state)
    Paper {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            IconTile(sourceIcon(source))
            Text(sourceTitle(source),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            Switch(selected,onCheckedChange=onSelected,enabled=supported,modifier=Modifier.semantics { contentDescription=sourceTitle(source)+"参与记录" })
        }
        Muted(if(source==Source.STEPS)state?.detail?.ifBlank { sourceBenefit(source) } ?: sourceBenefit(source) else sourceBenefit(source))
        Row(verticalAlignment=Alignment.CenterVertically) {
            val label=when { !supported->"设备暂不支持";ready&&!selected->"已授权 · 记录关闭";state?.authorization=="PARTIAL"->"部分授权";ready&&source==Source.STEPS&&state?.runtime=="ERROR"->"计步连接失败";ready&&source==Source.STEPS&&state?.runtime=="WAITING"->"已连接 · 等待走动";ready&&source==Source.STEPS&&state?.runtime=="RUNNING"->"计步正常";ready->"已授权";else->"尚未授权" }
            Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=if(ready&&selected)MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
            if(supported)TextButton(onClick=onAuthorize){Text(if(ready)"管理授权" else "去授权")}
        }
    }
}
