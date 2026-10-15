package com.personal.wo.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import com.personal.wo.MainActivity
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
@Composable fun EpisodePage(s:WoUiState,vm:WoViewModel,back:()->Unit){
    val e=s.episode
    if(e==null){Empty("片段已更新或删除","返回时间轴查看最新记录。");return}
    var edit by remember{mutableStateOf(false)};var delete by remember{mutableStateOf(false)};var naming by remember{mutableStateOf(false)}
    var title by remember(e.id){mutableStateOf(e.title)};var place by remember{mutableStateOf("")};var start by remember(e.id){mutableStateOf(clock(e.startMs,s.policy.archiveZone))};var end by remember(e.id){mutableStateOf(clock(e.endMs,s.policy.archiveZone))}
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)){
        item{Text(e.title,style=MaterialTheme.typography.headlineLarge);Muted("${clock(e.startMs,s.policy.archiveZone)}${if(e.endMs>e.startMs)"—${clock(e.endMs,s.policy.archiveZone)}" else ""} · ${assertionName(e.assertion)}",Modifier.padding(top=12.dp));Text(e.detail,Modifier.padding(top=12.dp))}
        item{Section("记录依据");Paper{if(e.evidence.isEmpty())Muted("没有可用的原始依据")else e.evidence.forEach{v->Text(Capabilities.sourceName(v.source));Muted(v.description);Muted(if(v.rawAvailable)"原始观察可用" else "原始明细已到期，保留最小证据说明");HorizontalDivider(Modifier.padding(vertical=6.dp))};if(e.qualityFlags.isNotEmpty())Muted("边界或来源说明：${e.qualityFlags.joinToString{when(it){"PROVISIONAL"->"结束边界未确认";"UNCERTAIN_BOUNDARY"->"起止存在采样误差";"ROUTE_UNKNOWN"->"路线未知";"MEDIA_RELINK_REQUIRED"->"媒体文件待重新关联";"INSTANCE_AMBIGUOUS"->"同类窗口实例无法完全区分";else->it}}}")}}
        if(e.photoUris.isNotEmpty())item{Section("关联媒体");e.photoUris.take(3).forEach{PhotoThumbnail(it)}}
        if(!s.demo)item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={edit=true}){Text("修正片段")};OutlinedButton(onClick=vm::favorite){Text(if(e.isFavorite)"取消收藏" else "收藏")};if(e.lat!=null)OutlinedButton(onClick={naming=true}){Text("命名地点")};TextButton(onClick={delete=true}){Text("删除")}}}
    }
    if(edit)AlertDialog(onDismissRequest={edit=false},title={Text("本人确认与更正")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){OutlinedTextField(title,{title=it},label={Text("片段名称")});Muted("修正标记为本人确认，原始观察保留。");OutlinedTextField(start,{start=it},label={Text("开始时间 HH:mm")},singleLine=true);OutlinedTextField(end,{end=it},label={Text("结束时间 HH:mm")},singleLine=true)}},confirmButton={TextButton(onClick={runCatching{val z=ZoneId.of(s.policy.archiveZone);val a=Instant.ofEpochMilli(e.startMs).atZone(z).toLocalDate().atTime(LocalTime.parse(start)).atZone(z).toInstant().toEpochMilli();val b=Instant.ofEpochMilli(e.endMs).atZone(z).toLocalDate().atTime(LocalTime.parse(end)).atZone(z).toInstant().toEpochMilli();require(b>=a);vm.annotate(title,true,a,b);edit=false}.onFailure{vm.message("请输入有效时间，结束不能早于开始")}}){Text("保存确认")}},dismissButton={TextButton(onClick={edit=false}){Text("取消")}})
    if(naming)AlertDialog(onDismissRequest={naming=false},title={Text("给停留地点命名")},text={OutlinedTextField(place,{place=it},label={Text("例如：家、办公室")})},confirmButton={TextButton(onClick={if(place.isNotBlank()){vm.namePlace(place);naming=false}}){Text("保存")}},dismissButton={TextButton(onClick={naming=false}){Text("取消")}})
    if(delete)AlertDialog(onDismissRequest={delete=false},title={Text("删除这个片段")},text={Text("将删除此片段、${e.evidence.size}条关联观察及索引，阻止这些来源记录再次导入；相关摘要重新整理。")},confirmButton={TextButton(onClick={vm.deleteEpisode();delete=false;back()}){Text("删除")}},dismissButton={TextButton(onClick={delete=false}){Text("取消")}})
}
@Composable fun SettingsPage(s:WoUiState,vm:WoViewModel,authorize:(Source)->Unit,background:()->Unit,export:(String)->Unit,lock:(Boolean)->Unit,setup:Boolean){
    val context=LocalContext.current;var notices by remember(s.policy.noticeApps){mutableStateOf(s.policy.noticeApps.joinToString("\n"))};var excluded by remember(s.policy.excludedApps){mutableStateOf(s.policy.excludedApps.joinToString("\n"))};var delete by remember{mutableStateOf(false)}
    var timeExclude by remember{mutableStateOf(false)};var fromTime by remember{mutableStateOf("12:00")};var toTime by remember{mutableStateOf("13:00")}
    var endpoint by rememberSaveable{mutableStateOf("")};var model by rememberSaveable{mutableStateOf("")};var apiKey by remember{mutableStateOf("")}
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=28.dp)){
        item{Text(if(setup)"一次配置，自动整理" else "记录由你掌握",style=MaterialTheme.typography.headlineLarge);Muted("默认在手机本地处理。后台使用低功耗来源，不持续录音、录像或截屏。",Modifier.padding(top=12.dp))}
        item{Paper{Text(if(s.policy.paused)"记录已暂停" else if(s.policy.requested)"自动整理已开启" else "记录尚未开启",style=MaterialTheme.typography.titleLarge);Muted("开始后有一条静默状态通知。重启后点击通知继续位置与计步记录；强停期间部分数据无法补回。");FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={vm.recording(if(!s.policy.requested)"start" else if(s.policy.paused)"resume" else "pause")}){Text(if(!s.policy.requested)"开始记录" else if(s.policy.paused)"继续记录" else "暂停记录")};if(s.policy.requested)TextButton(onClick={vm.recording("stop")}){Text("停止记录")};TextButton(onClick={vm.refresh()}){Text("同步来源")}}}}
        item{Section("通知与排除");Paper{Muted("只有本人选定的应用才保存经过脱敏的必要通知正文并解析线索。其他应用只保留元数据。请输入应用包名，每行一个。");OutlinedTextField(notices,{notices=it},Modifier.fillMaxWidth(),label={Text("通知解析允许的包名")},minLines=2);TextButton(onClick={vm.noticeApps(notices.lines().toSet())}){Text("保存通知来源")};OutlinedTextField(excluded,{excluded=it},Modifier.fillMaxWidth(),label={Text("排除的应用包名")},minLines=2);TextButton(onClick={vm.excludeApps(excluded.lines().toSet())}){Text("保存应用排除")};TextButton(onClick={timeExclude=true}){Text("排除指定时段")};Muted("暂停、停止和删除的范围，历史补采也不会越过。")}}
        item{Section("隐私与归档");Paper{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("查看时验证身份");Muted("离开超过60秒后，使用系统锁屏凭据或生物识别")};Switch(s.policy.lockUi,lock)};Muted("归档时区：${s.policy.archiveZone}");Muted("数据库加密，本机密钥保护。系统自动备份与迁移已排除。");Muted("数据库约${"%.1f".format(s.databaseBytes/1024.0/1024)}MB。原始位置与应用事件保留30天，通知正文7天。")}}
        item{Section("导出与恢复");Paper{Muted("日摘要是可读副本，默认去除通知正文、精确坐标与图片引用。完整备份采用流式认证加密，恢复码需单独保存；不包含照片原图。");FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={export("md")}){Text("导出日摘要")};OutlinedButton(onClick={export("json")}){Text("导出日JSON")};Button(onClick={export("archive")}){Text("加密完整备份")};TextButton(onClick={export("restore")}){Text("从备份恢复")}}}}
        item{Section("资源与运行状态");Paper{Muted("低电与高温自动降频。GNSS每次最多20秒，每小时120秒、每天600秒；没有永久唤醒锁。");TextButton(onClick={runCatching{context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}}){Text("打开系统应用设置")};s.diagnostics.take(10).forEach{Muted(it)};Muted("耗电、后台稳定性与准确性需在目标手机上实际测量。")}}
        if(BuildConfig.ENHANCED)item{Section("可选摘要增强");Paper{Muted("仅发送已有结构化事实，让端点返回事实ID顺序。本地生成文字；每天最多3次。HTTPS端点协议见源码说明。");OutlinedTextField(endpoint,{endpoint=it},Modifier.fillMaxWidth(),label={Text("HTTPS事实整理端点")});OutlinedTextField(model,{model=it},Modifier.fillMaxWidth(),label={Text("模型标识（可选）")});OutlinedTextField(apiKey,{apiKey=it},Modifier.fillMaxWidth(),label={Text("访问密钥（留空沿用已保存密钥）")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation());TextButton(onClick={vm.enhancedConfig(endpoint,model,apiKey);apiKey=""}){Text("保存增强配置")};Button(onClick=vm::enhance){Text("整理当前摘要顺序")}}}
        item{TextButton(onClick={delete=true}){Text("删除当前查看日期的全部数据",color=MaterialTheme.colorScheme.error)};Muted("我 · ${BuildConfig.VERSION_NAME} · 本人使用")}
    }
    if(delete)AlertDialog(onDismissRequest={delete=false},title={Text("删除${s.day?.dayDate}的记录")},text={Text("包括${s.day?.episodes?.size ?: 0}个片段及关联观察、摘要与索引。此日期不会在后续补采中重新导入。")},confirmButton={TextButton(onClick={vm.deleteDay();delete=false}){Text("删除这一天")}},dismissButton={TextButton(onClick={delete=false}){Text("取消")}})
    if(timeExclude)AlertDialog(onDismissRequest={timeExclude=false},title={Text("排除${s.selectedDate}的时段")},text={Column{OutlinedTextField(fromTime,{fromTime=it},label={Text("开始 HH:mm")});OutlinedTextField(toTime,{toTime=it},label={Text("结束 HH:mm")});Muted("现有原始记录保留，但此范围不参与整理和补采；要移除原始记录请使用删除。")}},confirmButton={TextButton(onClick={runCatching{val date=LocalDate.parse(s.selectedDate);val zone=ZoneId.of(s.policy.archiveZone);val a=date.atTime(LocalTime.parse(fromTime)).atZone(zone).toInstant().toEpochMilli();val b=date.atTime(LocalTime.parse(toTime)).atZone(zone).toInstant().toEpochMilli();require(b>a);vm.excludeRange(a,b);timeExclude=false}.onFailure{vm.message("请输入有效时段，结束应晚于开始")}}){Text("保存排除")}},dismissButton={TextButton(onClick={timeExclude=false}){Text("取消")}})
}
