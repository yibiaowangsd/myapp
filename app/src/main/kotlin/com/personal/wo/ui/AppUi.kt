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
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.wo.MainActivity
import com.personal.wo.core.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun WoAppUi(vm:WoViewModel,authorize:(Source)->Unit,background:()->Unit,export:(String)->Unit,restoreUri:Uri?,clearUri:()->Unit,lock:(Boolean)->Unit) {
    val context=LocalContext.current
    val shareScope=rememberCoroutineScope()
    val s by vm.state.collectAsStateWithLifecycle();var tab by key("main-tab-v210") { rememberSaveable { mutableIntStateOf(0) } };var page by rememberSaveable { mutableStateOf("") };var parent by rememberSaveable { mutableStateOf("") }
    var dayParent by rememberSaveable { mutableStateOf("") }
    var setupParent by rememberSaveable { mutableStateOf("") }
    var chapterId by rememberSaveable { mutableStateOf("") }
    var settingsParent by rememberSaveable { mutableStateOf("") }
    val immersive=page.isBlank()&&tab==0
    val achievements:AchievementViewModel=viewModel()
    val awards by achievements.state.collectAsStateWithLifecycle()
    val achievementsVisible=page.isBlank()&&tab==1
    LaunchedEffect(achievementsVisible,s.loaded,s.demo,s.day?.dayDate,s.day?.revision,s.policy.generation) {
        achievements.sync(s,achievementsVisible)
    }
    val snackbar=remember { SnackbarHostState() };val holder=rememberSaveableStateHolder();LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it);vm.message(null) } }
    LaunchedEffect(tab,page) { vm.section(page=="insights",page=="connections",page=="day",page=="settings") }
    fun setup() { if(s.demo)vm.demo(false);setupParent=page;page="setup" }
    fun open(e:Episode) { parent=page;page="episode";vm.episode(e.id) }
    fun all() { dayParent=page;page="day" }
    fun back() {
        page=when(page){"episode"->parent;"day"->dayParent;"setup"->setupParent;"settings"->settingsParent;"data-link"->"settings";"connections","insights"->"settings";else->""}
    }
    BackHandler(page.isBlank()&&tab!=0){tab=0}
    BackHandler(page.isNotBlank()){back()}
    Scaffold(modifier=Modifier.semantics { testTagsAsResourceId=true },containerColor=MaterialTheme.colorScheme.background,snackbarHost={SnackbarHost(snackbar)},bottomBar={ if(page.isBlank()) AppNavigation(tab) { i -> tab=i } }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter) {
            Column(Modifier.widthIn(max=680.dp).fillMaxSize().padding(horizontal=if(immersive)0.dp else 16.dp)) {
                if(!immersive)Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(page.isNotBlank())IconButton(onClick={back()}){Icon(Icons.Outlined.ArrowBack,"返回")}
                    Text(if(page.isBlank())mainPageTitles[tab] else when(page){"settings"->"设置";"data-link"->"数据互通";"chapter"->"事件详情";"summary"->"一天统计";"connections"->"应用与来源";"setup"->"记录权限";"search"->"搜索回忆";"episode"->"生活片段";"insights"->"生活趋势";else->"一天的时间线"},Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    if(page.isBlank()) {
                        IconButton(onClick={settingsParent=page;page="settings"},modifier=Modifier.testTag("open-settings")){Icon(Icons.Outlined.Settings,"设置")}
                    }
                }
                if(s.busy)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                if(!s.loaded)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
                else holder.SaveableStateProvider(if(page.isBlank())"tab-$tab" else page) { when(page){
                    "connections"->ConnectionsPage(s,authorize,vm::source,vm::appIncluded,vm::appNotices,vm::loadApps,{setup()},vm::reconnectSteps,{vm.message(it)},vm::revokeHuawei,vm::permissionsChanged)
                    "setup"->PermissionCenter(s,authorize,vm::source,background,{(context as? MainActivity)?.requestStatusNotification()},{if(s.policy.requested&&!s.policy.paused)vm.refresh() else vm.recording(if(s.policy.paused)"resume" else "start")},{(context as? MainActivity)?.requestHealthBackground()},{(context as? MainActivity)?.requestMissingPermissions()},vm::reconnectSteps)
                    "insights"->InsightPage(s)
                    "search"->MemorySearchPage(s,vm,::open)
                    "episode"->EpisodePage(s,vm,::back)
                    "day"->TimelinePage(s,{vm.date(it)},::open)
                    "chapter"->s.day?.let { day ->
                        val projection=resolvedHomeProjection(s,day)
                        if(projection==null)CircularProgressIndicator() else ChapterDetailsPage(day,projection.chapters.firstOrNull{it.id==chapterId},s.homeAppMinMinutes,::open)
                    }
                    "summary"->DayStatisticsPage(s,::all,{val step=s.sources.firstOrNull{it.source==Source.STEPS};if(sourceReady(step))vm.reconnectSteps() else {vm.source(Source.STEPS,true);authorize(Source.STEPS)}},::open)
                    "settings"->SettingsPage(s,vm,export,lock,{setup()},{page="connections"},{page="insights"},{vm.demo(!s.demo);tab=0;page=""},onDataLink={page="data-link"})
                    "data-link"->SharedDataPage(s.demo)
                    else->when(tab){
                        0->DayDrumHome(s,{chapterId=it.id;page="chapter"},{page="summary"},{vm.refresh()},{setup()},{vm.demo(it)},vm::date,vm::homeAppMinutes,onAll=::all,onSearch={page="search"},onImportPhotos={if(sourceReady(s.sources.firstOrNull{it.source==Source.PHOTO}))vm.importPhotos(30) else (context as? MainActivity)?.requestPhotoJourney()},onCancelImport=vm::cancelPhotoImport,onShare={s.day?.let { day->shareScope.launch { runCatching { DayMemoryShare.share(context,day,s.homeAppMinMinutes) }.onFailure { vm.message("分享图片生成失败，请重试") } } }},onSettings={settingsParent="";page="settings"})
                        1->AchievementsPage(s.copy(achievements=awards.snapshot,achievementsLoading=awards.loading,achievementError=awards.error),
                            achievements::create,achievements::delete,achievements::reading,achievements::showcase,achievements::seen,achievements::refresh,vm::loadApps,
                            {vm.date(it);tab=0;page=""})
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

internal val mainPageTitles=listOf("首页","成就")

@Composable fun AppNavigation(selected: Int, onSelect: (Int)->Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=16.dp,vertical=8.dp)) {
        NavigationBar(modifier=Modifier.clip(RoundedCornerShape(28.dp)).height(70.dp),
            containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp,windowInsets=WindowInsets(0,0,0,0)) {
            val icons=listOf(Icons.Outlined.Home,Icons.Outlined.EmojiEvents)
            mainPageTitles.forEachIndexed { i,label ->
                NavigationBarItem(selected=selected==i,onClick={onSelect(i)},modifier=Modifier.testTag(listOf("nav-home","nav-achievements")[i]),
                    icon={Icon(icons[i],null,Modifier.size(23.dp))},
                    label={Text(label,fontWeight=if(selected==i)FontWeight.Bold else FontWeight.Normal)},
                    colors=NavigationBarItemDefaults.colors(selectedIconColor=Ink,selectedTextColor=MaterialTheme.colorScheme.onSurface,
                        indicatorColor=WoMint,unselectedIconColor=MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor=MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
    }
}
