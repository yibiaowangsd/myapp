package com.personal.wo.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.personal.wo.collector.*
import com.personal.wo.core.*
import com.personal.wo.MainActivity
import com.personal.wo.health.HuaweiPhase

@Composable fun ConnectionsPage(s:WoUiState,authorize:(Source)->Unit,source:(Source,Boolean)->Unit,
    included:(String,Boolean)->Unit,notices:(String,Boolean)->Unit,reload:()->Unit,permissions:()->Unit,
    reconnectSteps:()->Unit,message:(String)->Unit,revokeHuawei:()->Unit={},syncHuawei:()->Unit={}) {
    val context=LocalContext.current
    var details by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf("") }
    val apps=remember(s.apps,search) { s.apps.filter { it.label.contains(search,true)||it.group.contains(search,true)||it.packageName.contains(search,true) } }
    fun open(intent:Intent) { runCatching { context.startActivity(intent) }.onFailure { message("系统没有提供这个入口，请在手机设置中搜索打开") } }
    fun connect(kind:Source) {
        if(kind !in s.policy.enabledSources)source(kind,true)
        authorize(kind)
    }
    LazyColumn(Modifier.testTag("connections-list"),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(top=8.dp,bottom=28.dp)) {
        item {
            Text("让生活自动留下线索",style=MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(!details,{details=false},label={Text("应用连接")})
                FilterChip(details,{details=true},label={Text("来源与后台")})
            }
        }
        if(details) {
        item {
            Paper {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    IconTile(Icons.Outlined.Hub)
                    Column(Modifier.weight(1f)) { Text("记录来源",style=MaterialTheme.typography.titleMedium);Muted("授权一次，之后按系统能力自动更新") }
                    TextButton(onClick=permissions){Text("管理")}
                }
                SourceConnection(s,Source.USAGE,"应用使用","微信、浏览器、地图等应用的使用时段",::connect)
                SourceConnection(s,Source.MEDIA,"音乐与播客","QQ 音乐、网易云等开放媒体会话的应用",::connect)
                SourceConnection(s,Source.CALENDAR,"日历与日程","读取已同步到系统日历的安排",::connect)
                SourceConnection(s,Source.PHOTO,"图库","按拍摄或加入图库的时间关联照片",::connect)
                SourceConnection(s,Source.HEALTH,"Health Connect","读取兼容应用写入的步数、运动与睡眠",::connect)
            }
        }
        item {
            Paper {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconTile(Icons.Outlined.DirectionsWalk)
                    Column(Modifier.weight(1f)) { Text("手机计步",style=MaterialTheme.typography.titleMedium);Muted(s.sources.firstOrNull{it.source==Source.STEPS}?.detail?.ifBlank{null} ?: "直接读取手机硬件计步器") }
                }
                val step=s.sources.firstOrNull{it.source==Source.STEPS}
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={connect(Source.STEPS)}){Text(if(sourceReady(step))"权限设置" else "允许计步")}
                    if(sourceReady(step)&&step?.supported!=false)TextButton(onClick=reconnectSteps){Text("重连计步器")}
                }
                HorizontalDivider()
                Text("华为运动健康",style=MaterialTheme.typography.titleMedium)
                StatusPill(s.huawei.label)
                Muted(s.huawei.detail)
                Muted("需平台开通与本人授权；手机计步独立工作。个人开发者仅可申请基础数据，睡眠需符合高阶数据资质。")
                if(s.huawei.phase !in setOf(HuaweiPhase.NOT_CONFIGURED,HuaweiPhase.UNAVAILABLE)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick={ (context as? MainActivity)?.requestHuaweiAuthorization() },
                            enabled=s.huawei.phase !in setOf(HuaweiPhase.AUTHORIZING,HuaweiPhase.REVOKE_PENDING)){Text("华为账号授权")}
                        TextButton(onClick=revokeHuawei,enabled=s.huawei.phase!=HuaweiPhase.AUTHORIZING){Text(if(s.huawei.phase==HuaweiPhase.REVOKE_PENDING)"重试撤销授权" else "撤销授权并清理导入")}
                        if(s.huawei.granted.isNotEmpty())TextButton(onClick=syncHuawei){Text("同步账号数据")}
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={
                        val intent=context.packageManager.getLaunchIntentForPackage("com.huawei.health")
                        if(intent!=null)open(intent) else message("未找到可打开的华为运动健康应用")
                    }){Text("打开运动健康")}
                    TextButton(onClick={open(Intent(Intent.ACTION_VIEW,Uri.parse("https://developer.huawei.com/consumer/cn/hms/huaweihealth/")))}){Text("官方接入说明")}
                }
                Muted("授权管理入口：运动健康 → 我的 → 隐私管理 → 数据分享与授权。只有完成接入的应用才会出现在其中。")
            }
        }
        item { BackgroundConnectionCard(s,permissions,{open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))},{open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))}) }
        } else {
        item {
            Section("我的应用","刷新",reload)
            Muted("选择应用来记录使用时段或通知线索。这里的开关不会登录第三方账号，也不会读取聊天历史。")
            OutlinedTextField(search,{search=it},Modifier.fillMaxWidth().padding(top=10.dp),singleLine=true,label={Text("搜索应用或类别")},leadingIcon={Icon(Icons.Outlined.Search,null)})
        }
        if(!s.appsLoaded)item { Muted("正在读取手机上的应用…") }
        else if(apps.isEmpty())item { Empty("没有找到应用","试试搜索应用名称，或点击刷新。") }
        items(apps,key={it.packageName}) { app ->
            val allowed=app.packageName !in s.policy.excludedApps
            val opted=app.packageName in s.policy.noticeApps
            Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().clickable{expanded=if(expanded==app.packageName)"" else app.packageName},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        AppBadge(app.packageName,app.label)
                        Column(Modifier.weight(1f)){Text(app.label,style=MaterialTheme.typography.titleMedium);Muted(connectionStatus(app,s.policy,s.sources))}
                        Icon(if(expanded==app.packageName)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,if(expanded==app.packageName)"收起 ${app.label}" else "设置 ${app.label}")
                    }
                    if(expanded==app.packageName) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)){Text("参与生活记录");Muted("${app.group} · 关闭后排除该应用的线索")}
                            Switch(allowed,{included(app.packageName,it)},Modifier.testTag("include-${app.packageName}"))
                        }
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)){Text("解析通知线索");Muted("保存脱敏后的通知文字，整理行程、送达等提示")}
                            Switch(opted,{on->notices(app.packageName,on);if(on&&!sourceReady(s.sources.firstOrNull{it.source==Source.NOTIFICATION}))authorize(Source.NOTIFICATION)},Modifier.testTag("notice-${app.packageName}"),enabled=allowed)
                        }
                        if(opted&&(!sourceReady(s.sources.firstOrNull{it.source==Source.NOTIFICATION})||Source.NOTIFICATION !in s.policy.enabledSources))TextButton(onClick={connect(Source.NOTIFICATION)}){Text("开启通知来源")}
                        if(allowed&&!sourceReady(s.sources.firstOrNull{it.source==Source.USAGE}))TextButton(onClick={connect(Source.USAGE)}){Text("开启使用时段")}
                    }
                }
            }
        }
        }
    }
}

@Composable private fun SourceConnection(s:WoUiState,kind:Source,title:String,detail:String,connect:(Source)->Unit) {
    val state=s.sources.firstOrNull{it.source==kind}
    val enabled=kind in s.policy.enabledSources
    val status=when { !enabled->"未开启";state?.supported==false->"此设备不可用";!sourceReady(state)->"待授权";state?.runtime=="ERROR"->"暂不可用";s.policy.paused->"已暂停";!s.policy.requested->"待开始记录";else->"已授权" }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleSmall);Muted(detail)}
        TextButton(onClick={connect(kind)},enabled=state?.supported!=false||!enabled){Text(status)}
    }
}

@Composable fun BackgroundConnectionCard(s:WoUiState,permissions:()->Unit,appSettings:()->Unit,batterySettings:()->Unit) {
    val sensors=setOf(Source.STEPS,Source.LOCATION).intersect(s.policy.enabledSources)
    Paper {
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            IconTile(Icons.Outlined.BatteryChargingFull)
            Column(Modifier.weight(1f)) { Text("需要一直开着吗？",style=MaterialTheme.typography.titleMedium);Muted(if(sensors.isEmpty())"当前未启用位置和手机计步" else "无需亮屏；步数与位置需要后台运行") }
        }
        Muted("应用使用、图库和日历：打开时补采，后台约每 30 分钟尝试更新，实际时间由系统安排。\n手机步数和位置：需要持续的后台记录服务。\n通知和音乐：由系统监听，新通知和播放记录中断后通常无法补回。")
        Muted("华为手机：在设置中搜索「应用启动管理」，找到「我」，改为手动管理并允许自启动、关联启动和后台活动。连续计步仍不稳定时，再设置电池优化并锁定最近任务。")
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick=appSettings){Text("应用设置")}
            TextButton(onClick=batterySettings){Text("电池优化")}
            TextButton(onClick=permissions){Text("选择记录内容")}
        }
    }
}
