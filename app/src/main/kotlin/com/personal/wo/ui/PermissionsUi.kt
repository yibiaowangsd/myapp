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
    Source.CALENDAR->"日历计划";Source.HEALTH->"运动与睡眠";Source.ACCESSIBILITY->"应用识别增强"
}
internal fun sourceBenefit(source:Source)=when(source) {
    Source.USAGE->"看见每个应用用了多久，时间花在了哪里。"
    Source.LOCATION->"按时间串起停留地点，留下今天的足迹。"
    Source.STEPS->"汇总走过的步数，在活动卡片中查看。"
    Source.PHOTO->"将当天的照片和视频关联到生活片段。"
    Source.NOTIFICATION->"从通知中留下送达、出行等生活线索。"
    Source.MEDIA->"记录音乐和视频的播放片段。"
    Source.CALENDAR->"在时间线显示日历计划。计划会单独标注。"
    Source.HEALTH->"读取健康来源提供的运动、步数与睡眠记录。"
    Source.ACCESSIBILITY->"补充应用窗口切换记录，通常无需开启。"
}
internal fun sourceExplanation(source:Source)=when(source) {
    Source.USAGE->"接下来打开系统的“使用情况访问权限”。找到“我”并允许访问，然后返回这里。只读取应用名称与活动时间。"
    Source.LOCATION->"接下来由系统询问位置权限，你可以选择精确或大致位置。要在锁屏后继续记录，可稍后在“后台记录”中设置始终允许。"
    Source.STEPS->"接下来由系统询问身体活动权限。使用手机硬件计步器；没有计步器时会显示不可用。"
    Source.PHOTO->"接下来由系统询问照片和视频访问权限。支持的系统可以只选择部分照片；原图保留在你的相册中。"
    Source.NOTIFICATION->"接下来打开系统的通知使用权设置。开启“我”后返回。默认保留通知元数据；通知正文需要在更多设置中单独选择应用。"
    Source.MEDIA->"系统需要通知使用权来访问正在播放的媒体信息。它与“通知线索”共用系统权限，但是否记录可分别关闭。"
    Source.CALENDAR->"接下来由系统询问读取日历权限。已有日程只作为计划显示，不代表你实际参加。"
    Source.HEALTH->"接下来打开设备提供的 Health Connect 授权页面。部分华为手机没有这项服务，可以跳过，其他来源仍可使用。"
    Source.ACCESSIBILITY->"接下来打开系统无障碍设置。只读取窗口所属应用，不读取屏幕正文，也不执行点击或手势。"
}

@Composable fun OnboardingFlow(s:WoUiState,authorize:(Source)->Unit,onStart:(Set<Source>)->Unit,onBrowse:()->Unit,onDemo:()->Unit,snackbar:SnackbarHostState=remember { SnackbarHostState() }) {
    var step by rememberSaveable{mutableIntStateOf(0)}
    var selectedNames by rememberSaveable { mutableStateOf((if(s.policy.requested)s.policy.enabledSources else primarySources.toSet()).map{it.name}) }
    val chosen=selectedNames.map{Source.valueOf(it)}.toSet()
    var prompt by rememberSaveable{mutableStateOf<String?>(null)}
    var more by rememberSaveable{mutableStateOf(false)}
    BackHandler { if(step>0)step=0 else onBrowse() }
    Scaffold(containerColor=MaterialTheme.colorScheme.background,snackbarHost={SnackbarHost(snackbar)},bottomBar={
        Surface(color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.navigationBarsPadding().padding(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                if(step==0) {
                    Button(onClick={step=1},modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text("设置记录权限")}
                    TextButton(onClick=onDemo,modifier=Modifier.fillMaxWidth()){Text("先看看示例")}
                } else {
                    val ready=chosen.count{source -> sourceReady(s.sources.firstOrNull{it.source==source})}
                    Button(onClick={onStart(chosen)},enabled=ready>0&&!s.busy,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("start-recording")){Text(if(ready>0)"用这 $ready 项来源开始记录" else "授权一项，即可开始")}
                    TextButton(onClick=onBrowse,modifier=Modifier.fillMaxWidth()){Text("暂时跳过，先进入首页")}
                }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("onboarding-list"),contentPadding=PaddingValues(horizontal=24.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    if(step>0)IconButton(onClick={step=0}){Icon(Icons.Outlined.ArrowBack,"上一步")}
                    Text("我",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    Text("${step+1} / 2",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                    if(step==0)TextButton(onClick=onBrowse){Text("跳过")}
                }
            }
            if(step==0) {
                item { IconTile(Icons.Outlined.FavoriteBorder,size=64) }
                item { Text("不用写日记，\n也能记住今天。",style=MaterialTheme.typography.headlineLarge) }
                item { Muted("把手机里已有的生活线索，整理成你看得懂的一天。") }
                item { Paper {
                    Text("一天，可以这样回看",style=MaterialTheme.typography.titleMedium)
                    StatusPill("以下为虚构示例")
                    val sample=demoDay()
                    sample.episodes.filter{it.kind in setOf("STAY","EXERCISE","MEDIA")}.take(3).forEach { e ->
                        Row(Modifier.padding(vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            IconTile(laneIcon(e.lane),laneTint(e.lane),38)
                            Column { Text(e.title,style=MaterialTheme.typography.titleMedium);Muted("${clock(e.startMs,sample.zoneId)} · ${durationText(e.endMs-e.startMs)}") }
                        }
                    }
                } }
                item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Lock,null,tint=MaterialTheme.colorScheme.primary)
                    Muted("你选择记录什么。每一项权限，都由你点击后确认。")
                } }
            } else {
                item { Text("选择想留下的记录",style=MaterialTheme.typography.headlineMedium);Muted("勾选想记录的内容，再逐项去授权。可以跳过，以后随时调整。",Modifier.padding(top=8.dp)) }
                items(primarySources,key={it.name}) { source ->
                    SourcePermissionCard(source,s.sources.firstOrNull{it.source==source},source in chosen,
                        {on -> selectedNames=if(on)(selectedNames+source.name).distinct() else selectedNames-source.name},
                        {prompt=source.name})
                }
                item { TextButton(onClick={more=!more},modifier=Modifier.fillMaxWidth()){Text(if(more)"收起其他来源" else "还有通知、音乐、日历等来源")} }
                if(more)items(Source.entries.filter{it !in primarySources},key={it.name}) { source ->
                    SourcePermissionCard(source,s.sources.firstOrNull{it.source==source},source in chosen,
                        {on -> selectedNames=if(on)(selectedNames+source.name).distinct() else selectedNames-source.name},
                        {prompt=source.name})
                }
            }
        }
    }
    prompt?.let { name ->
        SourcePermissionSheet(Source.valueOf(name),{prompt=null}) {
            if(name !in selectedNames)selectedNames=selectedNames+name
            prompt=null;authorize(Source.valueOf(name))
        }
    }
}

@Composable fun PermissionCenter(s:WoUiState,authorize:(Source)->Unit,onEnabled:(Source,Boolean)->Unit,background:()->Unit,statusNotification:()->Unit,record:()->Unit,healthBackground:()->Unit={}) {
    var prompt by rememberSaveable{mutableStateOf<String?>(null)}
    Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f).testTag("permissions-list"),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item {
            Text("想记什么，由你决定",style=MaterialTheme.typography.headlineMedium)
            Muted("点“去授权”后由系统确认。开关决定此来源是否参与记录。",Modifier.padding(top=8.dp,bottom=8.dp))
        }
        items(primarySources+Source.entries.filter{it !in primarySources},key={it.name}) { source ->
            SourcePermissionCard(source,s.sources.firstOrNull{it.source==source},source in s.policy.enabledSources,{onEnabled(source,it)},{prompt=source.name})
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
    prompt?.let { name -> SourcePermissionSheet(Source.valueOf(name),{prompt=null}) { prompt=null;authorize(Source.valueOf(name)) } }
}

@Composable internal fun SourcePermissionCard(source:Source,state:SourceState?,selected:Boolean,onSelected:(Boolean)->Unit,onAuthorize:()->Unit) {
    val supported=state?.supported!=false
    val ready=sourceReady(state)
    Paper {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            IconTile(sourceIcon(source))
            Text(sourceTitle(source),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            Checkbox(selected,onCheckedChange=onSelected,enabled=supported,modifier=Modifier.semantics { contentDescription=sourceTitle(source)+"参与记录" })
        }
        Muted(sourceBenefit(source))
        Row(verticalAlignment=Alignment.CenterVertically) {
            val label=when { !supported->"设备暂不支持";ready&&!selected->"已授权 · 记录关闭";state?.authorization=="PARTIAL"->"部分授权";ready->"已授权";else->"尚未授权" }
            Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=if(ready&&selected)MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
            if(supported)TextButton(onClick=onAuthorize){Text(if(ready)"管理授权" else "去授权")}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SourcePermissionSheet(source:Source,dismiss:()->Unit,authorize:()->Unit) {
    ModalBottomSheet(onDismissRequest=dismiss,containerColor=MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            IconTile(sourceIcon(source),size=56)
            Text("开启${sourceTitle(source)}",style=MaterialTheme.typography.headlineSmall)
            Text(sourceBenefit(source),style=MaterialTheme.typography.bodyLarge)
            Muted(sourceExplanation(source))
            Button(onClick=authorize,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("confirm-permission")){Text("继续，到系统确认")}
            TextButton(onClick=dismiss,modifier=Modifier.fillMaxWidth()){Text("暂时不用")}
        }
    }
}
