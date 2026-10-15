package com.personal.wo.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.personal.wo.core.*
import java.time.*
import java.time.format.DateTimeFormatter

val laneColors=listOf(Color(0xFF55796A),Color(0xFFA07464),Color(0xFF748BB0),Color(0xFFC29B5A))
fun laneName(l:Lane)=listOf("地点","活动","数字","生活")[l.ordinal]
fun assertionName(a:Assertion)=listOf("来源记录","规则推测","本人确认","计划","信息不足")[a.ordinal]
fun clock(t:Long,z:String)=Instant.ofEpochMilli(t).atZone(ZoneId.of(z)).format(DateTimeFormatter.ofPattern("HH:mm"))
@Composable fun WoTheme(content: @Composable () -> Unit) {
    val light=lightColorScheme(primary=Color(0xFF476A61),onPrimary=Color.White,background=Color(0xFFF6F4EE),onBackground=Color(0xFF272E2A),surface=Color(0xFFFCFAF5),surfaceVariant=Color(0xFFECEEE7),onSurface=Color(0xFF272E2A),onSurfaceVariant=Color(0xFF777F77),outline=Color(0xFFCACFC5))
    val dark=darkColorScheme(primary=Color(0xFFA8C8B9),background=Color(0xFF181D1A),surface=Color(0xFF222A25),surfaceVariant=Color(0xFF2E3932),onBackground=Color(0xFFE6ECE4),onSurface=Color(0xFFE6ECE4),onSurfaceVariant=Color(0xFFA8B4A9),outline=Color(0xFF536257))
    MaterialTheme(colorScheme=if(isSystemInDarkTheme())dark else light,typography=Typography(bodyLarge=androidx.compose.ui.text.TextStyle(fontSize=16.sp,lineHeight=26.sp),bodyMedium=androidx.compose.ui.text.TextStyle(fontSize=14.sp,lineHeight=22.sp),headlineLarge=androidx.compose.ui.text.TextStyle(fontSize=30.sp,lineHeight=40.sp,fontFamily=FontFamily.Serif),headlineSmall=androidx.compose.ui.text.TextStyle(fontSize=22.sp,lineHeight=30.sp,fontWeight=FontWeight.Medium)),content=content)
}
@Composable fun Muted(text:String,modifier:Modifier=Modifier) { Text(text,modifier,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable fun Section(title:String,action:String?=null,onClick:()->Unit={}) { Row(Modifier.fillMaxWidth().padding(top=18.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) { Text(title,Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall);if(action!=null)TextButton(onClick){Text(action)} } }
@Composable fun Paper(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) { Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content) } }
@Composable fun Empty(title:String,text:String) { Paper { Text(title,style=MaterialTheme.typography.titleLarge);Muted(text) } }

@Composable fun WoAppUi(vm:WoViewModel,authorize:(Source)->Unit,background:()->Unit,export:(String)->Unit,restoreUri:Uri?,clearUri:()->Unit,lock:(Boolean)->Unit) {
    val s by vm.state.collectAsState();var tab by rememberSaveable { mutableIntStateOf(0) };var page by rememberSaveable { mutableStateOf("") };var parent by rememberSaveable { mutableStateOf("") }
    val snackbar=remember { SnackbarHostState() };val holder=rememberSaveableStateHolder();LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it);vm.message(null) } }
    fun open(e:Episode) { parent=page;page="episode";vm.episode(e.id) }
    fun back() { page=if(page=="episode")parent else "" }
    BackHandler(page.isNotBlank()){back()}
    Scaffold(containerColor=MaterialTheme.colorScheme.background,snackbarHost={SnackbarHost(snackbar)},bottomBar={ if(page.isBlank())NavigationBar(containerColor=MaterialTheme.colorScheme.background) { listOf("今天","回看","规律").forEachIndexed { i,label->NavigationBarItem(selected=tab==i,onClick={tab=i;if(i==0&&!s.demo)vm.date(LocalDate.now(ZoneId.of(s.policy.archiveZone)))},icon={Text(listOf("◉","▦","◴")[i],fontSize=21.sp)},label={Text(label)}) } } }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter) {
            Column(Modifier.widthIn(max=840.dp).fillMaxSize().padding(horizontal=16.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(page.isNotBlank())TextButton(onClick={back()}){Text("返回")}
                    Text(if(page.isBlank())"我" else when(page){"settings"->"数据与设置";"setup"->"开始记录";"search"->"搜索";"episode"->"片段详情";else->"一天的细节"},Modifier.weight(1f),fontSize=23.sp,fontFamily=FontFamily.Serif)
                    if(page.isBlank()){TextButton(onClick={page="search"}){Text("搜索")};TextButton(onClick={page="settings"}){Text("设置")}}
                }
                if(s.busy)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                if(!s.loaded)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
                else holder.SaveableStateProvider(if(page.isBlank())"tab-$tab" else page) { when(page){
                    "settings","setup"->SettingsPage(s,vm,authorize,background,export,lock,page=="setup")
                    "search"->SearchPage(s,vm,::open)
                    "episode"->EpisodePage(s,vm,::back)
                    "day"->DayPage(s,vm,::open)
                    else->when(tab){0->TodayPage(s,vm,{page="day"},{page="setup"},::open);1->HistoryPage(s){vm.date(it);page="day"};else->InsightPage(s)}
                } }
            }
        }
    }
    var code by remember { mutableStateOf("") }
    if(restoreUri!=null)AlertDialog(onDismissRequest=clearUri,title={Text("打开加密备份")},text={Column { Muted("先验证归档，再确认替换记录。");OutlinedTextField(code,{code=it},label={Text("恢复码")},modifier=Modifier.fillMaxWidth(),minLines=2) }},confirmButton={TextButton(onClick={vm.inspect(restoreUri,code);code="";clearUri()}){Text("预检")}},dismissButton={TextButton(onClick=clearUri){Text("取消")}})
    s.inspection?.let { i->AlertDialog(onDismissRequest=vm::cancelRestore,title={Text("确认恢复记录")},text={Text("归档校验通过，共${i.count}条记录。恢复将替换当前记录，完成后需要重新确认来源授权并开始记录。照片原图不在备份中。")},confirmButton={TextButton(onClick=vm::restore){Text("恢复")}},dismissButton={TextButton(onClick=vm::cancelRestore){Text("取消")}}) }
    var show by remember { mutableStateOf(true) };LaunchedEffect(s.recoveryCode){show=true}
    if(s.recoveryCode!=null&&show)AlertDialog(onDismissRequest={show=false},title={Text("单独保存恢复码")},text={Column { Muted("恢复码不在备份文件内。请单独保存，缺失后无法恢复。");androidx.compose.foundation.text.selection.SelectionContainer { Text(s.recoveryCode!!,Modifier.padding(top=12.dp)) } }},confirmButton={TextButton(onClick={show=false}){Text("已保存")}})
}
@Composable fun TodayPage(s:WoUiState,vm:WoViewModel,detail:()->Unit,setup:()->Unit,episode:(Episode)->Unit) {
    val d=s.day ?: return;val date=LocalDate.parse(d.dayDate)
    LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top=18.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${date.monthValue}月${date.dayOfMonth}日",Modifier.weight(1f),fontSize=28.sp,fontFamily=FontFamily.Serif)
                Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=CircleShape){Text(if(s.demo)"演示" else if(s.policy.paused)"已暂停" else if(s.policy.requested)"自动整理中" else "尚未开始",Modifier.padding(10.dp),fontSize=13.sp)}
            }
            Muted("${date.year}年 · 星期${listOf("一","二","三","四","五","六","日")[date.dayOfWeek.value-1]}  /  截至${clock(d.asOfMs,d.zoneId)}",Modifier.padding(top=6.dp,bottom=22.dp))
            Text(d.headline,style=MaterialTheme.typography.headlineLarge)
            if(d.sentences.isNotEmpty())Text(d.sentences.joinToString(""){it.text},Modifier.padding(top=14.dp))
            else Muted(if(s.policy.requested)"手机正常使用时，系统来源会自动积累片段。已有结果会在这里更新。" else "完成一次授权，之后打开就能看到自动整理的一天。",Modifier.padding(top=14.dp))
        }
        if(s.demo)item { Paper { Text("演示 · 虚构记录",color=laneColors[3]);Muted("此日视图不会写入正式记录。");TextButton(onClick={vm.demo(false)}){Text("退出演示")} } }
        if(!s.policy.requested&&!s.demo)item { FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick=setup){Text("配置并开始记录")};OutlinedButton(onClick={vm.demo(true)}){Text("看看演示")}} }
        item { Section("时间的形状","展开",detail);Paper { Overview(d,detail);d.warnings.forEach { Muted(it) } } }
        item { FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){d.metrics.forEach { m->Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceVariant){Column(Modifier.padding(14.dp)){Text(m.value?.let { if(m.unit=="ms")durationText(it) else "$it${m.unit}" } ?: "暂无来源数据",fontSize=19.sp);Muted(m.label)}}}} }
        item { Section("这一天的片段","查看全部",detail) }
        val highlights=DayEngine().highlights(d)
        if(highlights.isEmpty())item { Empty("还没有足够的片段","已授权但暂无数据会保留为空，不用虚构活动填满页面。") }
        items(highlights,key={it.id}) { e->EpisodeCard(e,d.zoneId){episode(e)} }
        if(s.policy.requested&&!s.demo)item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick={vm.refresh()}){Text("同步来源")};TextButton(onClick={vm.recording(if(s.policy.paused)"resume" else "pause")}){Text(if(s.policy.paused)"继续记录" else "暂停记录")}} }
    }
}
@Composable fun EpisodeCard(e:Episode,z:String,onClick:()->Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick=onClick),shape=RoundedCornerShape(17.dp),color=MaterialTheme.colorScheme.surface){Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)){
        Column(Modifier.width(46.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(clock(e.startMs,z),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(8.dp));Box(Modifier.size(9.dp).background(laneColors[e.lane.ordinal],CircleShape))}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(e.title,style=MaterialTheme.typography.titleMedium);Muted("${laneName(e.lane)} · ${assertionName(e.assertion)}${if(e.endMs>e.startMs)" · ${durationText(e.endMs-e.startMs)}" else ""}${if(e.isFavorite)" · 已收藏" else ""}");if(e.detail.isNotBlank())Muted(e.detail.take(140))}
    }}
}
@Composable fun UnlockScreen(unlock:()->Unit) { Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Text("我",fontFamily=FontFamily.Serif,fontSize=54.sp);Spacer(Modifier.height(24.dp));Text("你的一天，只由你查看",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(24.dp));Button(onClick=unlock){Text("解锁查看")}}} }
