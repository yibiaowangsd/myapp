package com.personal.wo.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import com.personal.wo.BuildConfig
import com.personal.wo.core.*
import com.personal.wo.collector.Capabilities
import java.time.*

@Composable fun HistoryPage(s:WoUiState,onDate:(LocalDate)->Unit) {
    val today=LocalDate.now(ZoneId.of(s.policy.archiveZone))
    var monthString by rememberSaveable{mutableStateOf(YearMonth.from(LocalDate.parse(s.selectedDate)).toString())};val month=YearMonth.parse(monthString);val records=s.history.associateBy{it.dayDate};val offset=month.atDay(1).dayOfWeek.value-1
    LazyColumn(contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("把日子，一页页翻回来",style=MaterialTheme.typography.headlineLarge);Muted("点一个日期，重温那天留下的片段。",Modifier.padding(top=10.dp))}
        item{Paper{
            Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={monthString=month.minusMonths(1).toString()}){Text("上月")};Text("${month.year}年${month.monthValue}月",Modifier.weight(1f));TextButton(onClick={monthString=month.plusMonths(1).toString()},enabled=month<YearMonth.from(today)){Text("下月")}}
            BoxWithConstraints{val cell=(maxWidth.value/7).dp;Column{
                Row{listOf("一","二","三","四","五","六","日").forEach{Box(Modifier.width(cell).padding(vertical=8.dp),contentAlignment=Alignment.Center){Muted(it)}}}
                for(week in 0 until (offset+month.lengthOfMonth()+6)/7)Row{
                    for(day in 0..6){val number=week*7+day-offset+1
                        if(number in 1..month.lengthOfMonth()){val date=month.atDay(number);val recorded=records.containsKey(date.toString())
                            Column(Modifier.width(cell).heightIn(min=52.dp).clip(RoundedCornerShape(12.dp)).background(if(date.toString()==s.selectedDate)MaterialTheme.colorScheme.surfaceVariant else Color.Transparent).clickable(enabled=date<=today){onDate(date)}.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(number.toString(),color=MaterialTheme.colorScheme.onSurface.copy(alpha=if(date<=today)1f else .35f));Text(if(recorded)"·" else "",color=MaterialTheme.colorScheme.primary)}
                        }else Spacer(Modifier.width(cell).height(52.dp))
                    }
                }
            }}
        }}
        item{Section("最近整理")}
        if(s.history.isEmpty())item{Empty("还没有历史记录","开始记录后，每天会自动归档在这里。")}
        items(s.history,key={it.dayDate+it.zoneId}){d->Paper(Modifier.clickable{onDate(LocalDate.parse(d.dayDate))}){Text(d.dayDate,fontSize=19.sp);Muted(d.sentences.joinToString(""){it.text}.ifBlank{"${d.episodes.size}个已观察片段"});Muted("${d.episodes.size}个片段 · 截至${clock(d.asOfMs,d.zoneId)}")}}
    }
}
@Composable fun SearchPage(s:WoUiState,vm:WoViewModel,episode:(Episode)->Unit){
    var query by rememberSaveable{mutableStateOf("")};var all by rememberSaveable{mutableStateOf(false)};var lane by rememberSaveable{mutableIntStateOf(-1)}
    LaunchedEffect(query,all){vm.search(query,all)}
    Column{
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("地点、应用或片段关键词")},singleLine=true,shape=RoundedCornerShape(14.dp))
        Row(verticalAlignment=Alignment.CenterVertically){Switch(all,{all=it});Muted(if(all)"查询全部历史" else "最近90天");Spacer(Modifier.weight(1f));Text("${s.results.size}条",fontSize=13.sp)}
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(lane==-1,{lane=-1},label={Text("全部")});Lane.entries.forEach{l->FilterChip(lane==l.ordinal,{lane=l.ordinal},label={Text(laneName(l))})}}
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(top=12.dp,bottom=24.dp)){
            if(query.isNotBlank()&&s.results.isEmpty())item{Empty("没有匹配的片段","可以调整关键词或查询范围。图片里的文字没有自动识别。")}
            items(s.results.filter{lane==-1||it.lane.ordinal==lane},key={it.id}){e->Column{Muted(Instant.ofEpochMilli(e.startMs).atZone(ZoneId.of(s.policy.archiveZone)).toLocalDate().toString(),Modifier.padding(bottom=4.dp));EpisodeCard(e,s.policy.archiveZone){episode(e)}}}
            if(s.results.size in 50..199)item{TextButton(onClick=vm::moreSearch){Text("加载更多")}}
        }
    }
}
@Composable fun InsightPage(s:WoUiState){
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)){
        item{Text("最近的生活节奏",style=MaterialTheme.typography.headlineLarge);Muted("描述有依据的变化，不给一天打分。",Modifier.padding(top=12.dp,bottom=16.dp))}
        item{WeeklyUsageCard(s.history,s.policy.archiveZone)}
        if(s.insights.isEmpty())item{Empty("再多记录几天","这里会在数据足够时，展示手机使用和活动的变化。先从下面的最近一周开始。")}
        items(s.insights){i->Paper{Text(i.title,style=MaterialTheme.typography.titleLarge);Muted(i.detail)}}
        item{Section("已积累的日期");Paper{Text("${s.history.count{it.episodes.isNotEmpty()}}天",style=MaterialTheme.typography.headlineLarge);Muted("这是存在片段的日期数，不代表全天完整覆盖。")}}
    }
}
@Composable fun EpisodePage(s:WoUiState,vm:WoViewModel,back:()->Unit) {
    val e=s.episode
    if(e==null){
        if(s.episodeLoading)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
        else Empty("片段已更新或删除","返回时间轴查看最新记录。")
        return
    }
    val context=LocalContext.current
    var edit by remember(e.id){mutableStateOf(false)}
    var delete by remember(e.id){mutableStateOf(false)}
    var naming by remember(e.id){mutableStateOf(false)}
    var evidence by rememberSaveable(e.id){mutableStateOf(false)}
    var title by remember(e.id){mutableStateOf(e.title)}
    var place by remember(e.id){mutableStateOf("")}
    var start by remember(e.id){mutableStateOf(clock(e.startMs,s.policy.archiveZone))}
    var end by remember(e.id){mutableStateOf(clock(e.endMs,s.policy.archiveZone))}
    val media=remember(e.photoUris){e.photoUris.distinct()}
    fun openMedia(value:String) {
        if(s.demo){vm.message("这是演示影像，导入照片后可以查看原图");return}
        val uri=Uri.parse(value)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri,context.contentResolver.getType(uri) ?: "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch(_:ActivityNotFoundException) {
            vm.message("手机中没有可打开这项媒体的应用")
        } catch(_:SecurityException) {
            vm.message("这项影像暂时无法读取，请在照片来源中重新选择")
        }
    }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(bottom=28.dp)) {
        item {
            StatusPill(assertionName(e.assertion))
            Text(e.title,style=MaterialTheme.typography.headlineLarge,modifier=Modifier.padding(top=12.dp))
            Muted("${clock(e.startMs,s.policy.archiveZone)}${if(e.endMs>e.startMs)" — ${clock(e.endMs,s.policy.archiveZone)}" else ""}",Modifier.padding(top=8.dp))
            if(e.detail.isNotBlank())Text(e.detail,Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodyLarge)
        }
        if(media.isNotEmpty())item {
            Row(Modifier.fillMaxWidth().padding(bottom=10.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("这一刻的影像",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                Muted("${media.size} 项")
            }
            LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                items(media,key={it}) { uri ->
                    Column(Modifier.width(280.dp)) {
                        PhotoThumbnail(uri,Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(24.dp))
                            .clickable(onClickLabel="查看完整影像"){openMedia(uri)},crop=true)
                        TextButton(onClick={openMedia(uri)},modifier=Modifier.align(Alignment.End)) {
                            Text("打开原图 / 视频")
                            Icon(Icons.Outlined.OpenInNew,null,Modifier.padding(start=6.dp).size(16.dp))
                        }
                    }
                }
            }
        }
        if(!s.demo)item {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Button(onClick=vm::favorite) {
                    Icon(if(e.isFavorite)Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,null,Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp));Text(if(e.isFavorite)"已收藏" else "收藏这一刻")
                }
                OutlinedButton(onClick={edit=true}){Text("修正片段")}
                if(e.lat!=null)OutlinedButton(onClick={naming=true}){Text("命名地点")}
            }
        }
        item {
            Paper {
                Row(Modifier.fillMaxWidth().clickable{evidence=!evidence},verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("这段回忆从哪里来",style=MaterialTheme.typography.titleMedium)
                        Muted("${e.evidence.size} 条来源记录",Modifier.padding(top=4.dp))
                    }
                    Icon(if(evidence)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null)
                }
                if(evidence) {
                    if(e.evidence.isEmpty())Muted("没有可用的原始依据")
                    e.evidence.forEach { v ->
                        HorizontalDivider()
                        Text(Capabilities.sourceName(v.source),style=MaterialTheme.typography.labelLarge)
                        Muted(v.description)
                        if(!v.rawAvailable)Muted("原始明细已到期，已保留来源说明。")
                    }
                    if(e.qualityFlags.isNotEmpty())Muted(e.qualityFlags.joinToString("；") {
                        when(it) {
                            "PROVISIONAL"->"结束时间尚未确认";"UNCERTAIN_BOUNDARY"->"起止时间可能存在采样误差"
                            "ROUTE_UNKNOWN"->"没有完整路线";"MEDIA_RELINK_REQUIRED"->"需要重新关联媒体文件"
                            "INSTANCE_AMBIGUOUS"->"同类应用窗口无法完全区分";else->it
                        }
                    })
                }
            }
        }
        if(!s.demo)item{TextButton(onClick={delete=true}){Text("删除这个片段",color=MaterialTheme.colorScheme.error)}}
    }
    if(edit)AlertDialog(onDismissRequest={edit=false},title={Text("修正这一刻")},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title,{title=it},label={Text("片段名称")})
            Muted("保存后标记为你已确认，保留原始来源。")
            OutlinedTextField(start,{start=it},label={Text("开始时间 HH:mm")},singleLine=true)
            OutlinedTextField(end,{end=it},label={Text("结束时间 HH:mm")},singleLine=true)
        }
    },confirmButton={TextButton(onClick={
        runCatching {
            val z=ZoneId.of(s.policy.archiveZone)
            val a=Instant.ofEpochMilli(e.startMs).atZone(z).toLocalDate().atTime(LocalTime.parse(start)).atZone(z).toInstant().toEpochMilli()
            val b=Instant.ofEpochMilli(e.endMs).atZone(z).toLocalDate().atTime(LocalTime.parse(end)).atZone(z).toInstant().toEpochMilli()
            require(b>=a);vm.annotate(title,true,a,b);edit=false
        }.onFailure{vm.message("请输入有效时间，结束不能早于开始")}
    }){Text("保存修正")}},dismissButton={TextButton(onClick={edit=false}){Text("取消")}})
    if(naming)AlertDialog(onDismissRequest={naming=false},title={Text("给这个地方起个名字")},
        text={OutlinedTextField(place,{place=it},label={Text("例如：家、办公室")})},
        confirmButton={TextButton(onClick={if(place.isNotBlank()){vm.namePlace(place);naming=false}}){Text("保存")}},
        dismissButton={TextButton(onClick={naming=false}){Text("取消")}})
    if(delete)AlertDialog(onDismissRequest={delete=false},title={Text("删除这个片段？")},
        text={Text("将移除片段及 ${e.evidence.size} 条关联记录，之后不会再次自动导入。手机相册中的原图保留。")},
        confirmButton={TextButton(onClick={vm.deleteEpisode();delete=false;back()}){Text("删除")}},
        dismissButton={TextButton(onClick={delete=false}){Text("取消")}})
}

@Composable fun SettingsPage(s:WoUiState,vm:WoViewModel,export:(String)->Unit,lock:(Boolean)->Unit,permissions:()->Unit,connections:()->Unit,insights:()->Unit,demo:()->Unit) {
    val context=LocalContext.current
    var delete by remember{mutableStateOf(false)}
    var timeExclude by remember{mutableStateOf(false)}
    var runtime by rememberSaveable{mutableStateOf(false)}
    var fromTime by remember{mutableStateOf("12:00")}
    var toTime by remember{mutableStateOf("13:00")}
    var endpoint by rememberSaveable{mutableStateOf("")}
    var model by rememberSaveable{mutableStateOf("")}
    var apiKey by remember{mutableStateOf("")}
    LazyColumn(verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(bottom=28.dp)) {
        item {
            Text("按你的方式，\n留下生活。",style=MaterialTheme.typography.headlineLarge)
            Muted("记录什么、什么时候暂停，都由你决定。",Modifier.padding(top=10.dp))
        }
        item {
            Surface(color=Ink,contentColor=WoPaper,shape=RoundedCornerShape(28.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(if(s.policy.requested&&!s.policy.paused)WoMint else WoPaper.copy(alpha=.5f),RoundedCornerShape(4.dp)))
                        Text(if(s.policy.paused)"留一点空白" else if(s.policy.requested)"生活正在被记住" else "从今天开始",Modifier.padding(start=10.dp),style=MaterialTheme.typography.titleLarge)
                    }
                    Text(if(s.policy.paused)"自动记录已暂停，过去的回忆随时可以翻看。" else if(s.policy.requested)"只整理你已开启的来源。可以随时暂停。" else "先开启一个来源，就能慢慢拼起自己的时间轴。",color=WoPaper.copy(alpha=.76f),style=MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Button(onClick={if(!s.policy.requested)permissions() else vm.recording(if(s.policy.paused)"resume" else "pause")},
                            colors=ButtonDefaults.buttonColors(containerColor=WoMint,contentColor=Ink)) {
                            Text(if(!s.policy.requested)"选择记录来源" else if(s.policy.paused)"继续记录" else "暂停记录")
                        }
                        if(s.policy.requested)TextButton(onClick={vm.recording("stop")},colors=ButtonDefaults.textButtonColors(contentColor=WoPaper)){Text("停止")}
                        if(s.policy.requested)TextButton(onClick={vm.refresh()},enabled=!s.busy&&!s.demo,colors=ButtonDefaults.textButtonColors(contentColor=WoMint)){Text(if(s.busy)"整理中…" else "立即整理")}
                    }
                }
            }
        }
        item{SettingsShortcuts(permissions,connections,insights)}
        item {
            Section("只属于你的记录")
            Paper {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    IconTile(Icons.Outlined.Lock)
                    Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                        Text("打开时验证身份",style=MaterialTheme.typography.titleMedium)
                        Muted("离开超过一分钟后再次验证")
                    }
                    Switch(s.policy.lockUi,lock)
                }
                HorizontalDivider()
                Muted("默认在手机本地整理，数据库加密保存。照片保留在你的相册里。")
                TextButton(onClick={timeExclude=true}){Text("让一段时间不被记录")}
            }
        }
        item {
            Section("把回忆带走")
            Paper {
                Text("换手机，也能接着翻看",style=MaterialTheme.typography.titleMedium)
                Muted("加密备份保存记录与修正，请单独保管恢复码。备份不包含相册原图。")
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Button(onClick={export("archive")}){Text("备份我的记录")}
                    OutlinedButton(onClick={export("restore")}){Text("恢复备份")}
                    TextButton(onClick={export("md")}){Text("导出这一天")}
                }
            }
        }
        item {
            Paper {
                Row(Modifier.fillMaxWidth().clickable{runtime=!runtime}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Tune,null,Modifier.size(22.dp))
                    Column(Modifier.weight(1f).padding(start=12.dp)) {
                        Text("运行详情",style=MaterialTheme.typography.titleMedium)
                        Muted("存储、后台运行与高级选项")
                    }
                    Icon(if(runtime)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null)
                }
                if(runtime) {
                    HorizontalDivider()
                    Muted("归档时区：${s.policy.archiveZone} · 数据库 ${"%.1f".format(s.databaseBytes/1024.0/1024)} MB")
                    Muted("原始位置与应用事件保留30天，通知正文保留7天。系统自动备份与迁移已关闭，请使用上方加密备份。")
                    Muted("连续位置与计步启用时显示静默状态通知。重启后可从通知继续记录；强制停止期间，部分记录可能缺失。")
                    Muted("低电量与高温时减少采样。GNSS每次最多20秒，每小时120秒、每天600秒；没有永久唤醒锁。")
                    TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}){Text("系统应用设置")}
                    TextButton(onClick={export("json")}){Text("导出这一天的 JSON 数据")}
                    if(s.diagnostics.isNotEmpty()) {
                        Text("最近运行情况",style=MaterialTheme.typography.labelLarge)
                        s.diagnostics.take(10).forEach{Muted(it)}
                    }
                }
            }
        }
        if(runtime&&BuildConfig.ENHANCED)item {
            Paper {
                Text("可选摘要增强",style=MaterialTheme.typography.titleMedium)
                Muted("主动使用时，向你配置的服务发送已有结构化事实，以调整摘要顺序。每天最多3次。")
                OutlinedTextField(endpoint,{endpoint=it},Modifier.fillMaxWidth(),label={Text("HTTPS 服务地址")})
                OutlinedTextField(model,{model=it},Modifier.fillMaxWidth(),label={Text("模型标识（可选）")})
                OutlinedTextField(apiKey,{apiKey=it},Modifier.fillMaxWidth(),label={Text("访问密钥（留空保留原值）")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
                TextButton(onClick={vm.enhancedConfig(endpoint,model,apiKey);apiKey=""}){Text("保存配置")}
                OutlinedButton(onClick=vm::enhance){Text("整理当前摘要顺序")}
            }
        }
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick=demo){Text(if(s.demo)"退出演示" else "看看示例")}
                Text("我  /  ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(!s.demo)TextButton(onClick={delete=true}){Text("删除 ${s.selectedDate} 的记录",color=MaterialTheme.colorScheme.error)}
        }
    }
    if(delete)AlertDialog(onDismissRequest={delete=false},title={Text("删除这一天？")},
        text={Text("将删除 ${s.selectedDate} 的 ${s.day?.episodes?.size ?: 0} 个片段及关联记录，之后不会再次自动导入。手机相册中的原图保留。")},
        confirmButton={TextButton(onClick={vm.deleteDay();delete=false}){Text("删除这一天")}},
        dismissButton={TextButton(onClick={delete=false}){Text("取消")}})
    if(timeExclude)AlertDialog(onDismissRequest={timeExclude=false},title={Text("留一段空白")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(s.selectedDate)
            OutlinedTextField(fromTime,{fromTime=it},label={Text("开始 HH:mm")},singleLine=true)
            OutlinedTextField(toTime,{toTime=it},label={Text("结束 HH:mm")},singleLine=true)
            Muted("这段时间不参与整理和补采。已保存的原始记录仍然保留，可单独删除。")
        }
    },confirmButton={TextButton(onClick={
        runCatching {
            val date=LocalDate.parse(s.selectedDate);val zone=ZoneId.of(s.policy.archiveZone)
            val a=date.atTime(LocalTime.parse(fromTime)).atZone(zone).toInstant().toEpochMilli()
            val b=date.atTime(LocalTime.parse(toTime)).atZone(zone).toInstant().toEpochMilli()
            require(b>a);vm.excludeRange(a,b);timeExclude=false
        }.onFailure{vm.message("请输入有效时段，结束应晚于开始")}
    }){Text("保存时段")}},dismissButton={TextButton(onClick={timeExclude=false}){Text("取消")}})
}

@Composable fun SettingsShortcuts(permissions:()->Unit,connections:()->Unit,insights:()->Unit) {
    Paper {
        SettingsShortcut(Icons.Outlined.PhotoLibrary,"记录来源", "从照片开始，逐步丰富时间轴", permissions)
        HorizontalDivider()
        SettingsShortcut(Icons.Outlined.Link,"应用与连接", "选择应用、运动与通知线索", connections)
        HorizontalDivider()
        SettingsShortcut(Icons.Outlined.Insights,"生活趋势", "看看最近的生活节奏", insights)
    }
}

@Composable private fun SettingsShortcut(icon:ImageVector,title:String,detail:String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        IconTile(icon,size=42)
        Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium)
            Muted(detail,Modifier.padding(top=3.dp))
        }
        Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
