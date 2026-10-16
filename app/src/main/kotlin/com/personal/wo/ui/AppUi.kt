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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.personal.wo.MainActivity
import com.personal.wo.core.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun WoAppUi(vm:WoViewModel,authorize:(Source)->Unit,background:()->Unit,export:(String)->Unit,restoreUri:Uri?,clearUri:()->Unit,lock:(Boolean)->Unit) {
    val context=LocalContext.current
    val s by vm.state.collectAsState();var tab by rememberSaveable { mutableIntStateOf(0) };var page by rememberSaveable { mutableStateOf("") };var parent by rememberSaveable { mutableStateOf("") }
    var dayParent by rememberSaveable { mutableStateOf("") }
    var chapterId by rememberSaveable { mutableStateOf("") }
    val immersive=page.isBlank()&&tab==0
    val snackbar=remember { SnackbarHostState() };val holder=rememberSaveableStateHolder();LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it);vm.message(null) } }
    LaunchedEffect(tab,page) { vm.section(tab==1||tab==3||page=="insights",tab==2&&page.isBlank()) }
    fun setup() { if(s.demo)vm.demo(false);page="setup" }
    fun open(e:Episode) { parent=page;page="episode";vm.episode(e.id) }
    fun all() { dayParent=page;page="day" }
    fun back() {
        page=when(page){"episode"->parent;"day"->dayParent;else->""}
    }
    BackHandler(page.isNotBlank()){back()}
    Scaffold(containerColor=MaterialTheme.colorScheme.background,snackbarHost={SnackbarHost(snackbar)},bottomBar={ if(page.isBlank()) AppNavigation(tab) { i -> tab=i } }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter) {
            Column(Modifier.widthIn(max=680.dp).fillMaxSize().padding(horizontal=if(immersive)0.dp else 16.dp)) {
                if(!immersive)Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(page.isNotBlank())IconButton(onClick={back()}){Icon(Icons.Outlined.ArrowBack,"返回")}
                    Text(if(page.isBlank())listOf("时间轴","回忆","连接","我的")[tab] else when(page){"chapter"->"事件详情";"summary"->"一天统计";"settings"->"更多设置";"setup"->"记录权限";"search"->"搜索回忆";"episode"->"生活片段";"insights"->"生活趋势";else->"一天的时间线"},Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    if(page.isBlank()){IconButton(onClick={page="search"}){Icon(Icons.Outlined.Search,"搜索记录")};IconButton(onClick={setup()}){Icon(Icons.Outlined.Settings,"记录权限")}}
                }
                if(s.busy)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                if(!s.loaded)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
                else holder.SaveableStateProvider(if(page.isBlank())"tab-$tab" else page) { when(page){
                    "settings"->SettingsPage(s,vm,authorize,background,export,lock,false)
                    "setup"->PermissionCenter(s,authorize,vm::source,background,{(context as? MainActivity)?.requestStatusNotification()},{if(s.policy.requested&&!s.policy.paused)vm.refresh() else vm.recording(if(s.policy.paused)"resume" else "start")},{(context as? MainActivity)?.requestHealthBackground()},{(context as? MainActivity)?.requestMissingPermissions()},vm::reconnectSteps)
                    "insights"->InsightPage(s)
                    "search"->SearchPage(s,vm,::open)
                    "episode"->EpisodePage(s,vm,::back)
                    "day"->TimelinePage(s,{vm.date(it)},::open)
                    "chapter"->s.day?.let { day -> ChapterDetailsPage(day,dayChapters(day,s.homeAppMinMinutes).firstOrNull{it.id==chapterId},s.homeAppMinMinutes,::open) }
                    "summary"->DayStatisticsPage(s,::all,{val step=s.sources.firstOrNull{it.source==Source.STEPS};if(sourceReady(step))vm.reconnectSteps() else {vm.source(Source.STEPS,true);authorize(Source.STEPS)}},::open)
                    else->when(tab){
                        0->DayDrumHome(s,{chapterId=it.id;page="chapter"},{page="summary"},{vm.refresh()},{setup()},{vm.demo(it)},vm::date,vm::homeAppMinutes,onAll=::all)
                        1->HistoryPage(s){vm.date(it);tab=0;page=""}
                        2->ConnectionsPage(s,authorize,vm::source,vm::appIncluded,vm::appNotices,vm::loadApps,{setup()},vm::reconnectSteps,{vm.message(it)})
                        else->MePage(s,{setup()},{page="settings"},{page="insights"},{vm.recording(if(s.policy.paused)"resume" else if(!s.policy.requested)"start" else "pause")},{vm.demo(true);tab=0})
                    }
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
@Composable fun UnlockScreen(unlock:()->Unit) { Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Text("我",fontFamily=FontFamily.Serif,fontSize=54.sp);Spacer(Modifier.height(24.dp));Text("你的一天，只由你查看",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(24.dp));Button(onClick=unlock){Text("解锁查看")}}} }

@Composable fun AppNavigation(selected: Int, onSelect: (Int)->Unit) {
    NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp) {
        val icons=listOf(Icons.Outlined.Timeline,Icons.Outlined.DateRange,Icons.Outlined.Hub,Icons.Outlined.Person)
        listOf("时间轴","回忆","连接","我的").forEachIndexed { i,label ->
            NavigationBarItem(selected=selected==i,onClick={onSelect(i)},icon={Icon(icons[i],null)},label={Text(label)},colors=NavigationBarItemDefaults.colors(selectedIconColor=MaterialTheme.colorScheme.primary,selectedTextColor=MaterialTheme.colorScheme.primary,indicatorColor=MaterialTheme.colorScheme.primaryContainer))
        }
    }
}
