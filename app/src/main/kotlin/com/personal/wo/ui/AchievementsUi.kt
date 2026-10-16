package com.personal.wo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.wo.core.*
import java.time.LocalDate

@Composable fun AchievementPreview(snapshot:AchievementSnapshot?,open:()->Unit) {
    val earned=snapshot?.unlocked.orEmpty()
    val featured=snapshot?.settings?.showcaseIds.orEmpty().mapNotNull{id->earned.firstOrNull{it.goal.id==id}}
        .ifEmpty{earned.sortedByDescending{it.goal.tier}.take(3)}
    Surface(Modifier.fillMaxWidth().testTag("achievement-entry").clickable(onClickLabel="打开我的成就",onClick=open),
        shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("我的成就",style=MaterialTheme.typography.titleLarge)
                    Text(if(snapshot==null)"日常的积累，值得被看见" else "已点亮 ${earned.size} / ${snapshot.items.size} 枚${if(snapshot.demo)" · 演示" else ""}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight,"进入成就馆")
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                if(featured.isEmpty())listOf(AchievementMetric.DAYS,AchievementMetric.STEPS,AchievementMetric.READING).forEach {
                    AchievementBadge(it,1,false,Modifier.size(64.dp))
                } else featured.forEach{AchievementBadge(it.goal.metric,it.goal.tier,true,Modifier.size(64.dp))}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AchievementsPage(
    s:WoUiState,onCreate:(AchievementGoal)->Unit,onDelete:(String)->Unit,onReadingApps:(Set<String>)->Unit,
    onShowcase:(String)->Unit,onSeen:(Set<String>)->Unit,onRetry:()->Unit,onLoadApps:()->Unit,onDate:(LocalDate)->Unit
) {
    val snapshot=s.achievements
    var family by rememberSaveable { mutableStateOf("ALL") }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var reading by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    if(creating) {
        BackHandler { creating=false }
        AchievementCreator(onDismiss={creating=false},onSave={onCreate(it);creating=false})
        return
    }
    val large=LocalDensity.current.fontScale>=1.3f
    val displayed=snapshot?.items.orEmpty().filter {
        (family=="ALL"||it.goal.metric.name==family)&&when(filter){"EARNED"->it.unlocked;"PENDING"->!it.unlocked;"CUSTOM"->it.goal.custom;else->true}
    }
    val showcased=snapshot?.settings?.showcaseIds.orEmpty().mapNotNull { id ->
        snapshot?.unlocked?.firstOrNull { it.goal.id==id }
    }
    LazyVerticalGrid(columns=GridCells.Adaptive(if(large)180.dp else 144.dp),modifier=Modifier.fillMaxSize().testTag("achievement-gallery"),
        contentPadding=PaddingValues(top=12.dp,bottom=28.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(span={GridItemSpan(maxLineSpan)}) {
            Surface(shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("把日常，收成勋章",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Medium)
                    Text("不与别人比较，只看见自己的积累。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if(snapshot==null)"正在整理你的成就…" else "${snapshot.unlocked.size} 枚已点亮 · ${snapshot.items.size} 枚可收藏",style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("achievement-count"))
                    if(s.demo)Text("虚构演示 · 不写入真实成就",style=MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick={creating=true},enabled=snapshot!=null&&!s.demo&&!s.achievementsLoading&&snapshot.settings.customGoals.size<12,modifier=Modifier.testTag("create-achievement")){Text("自定义成就")}
                        TextButton(onClick={onLoadApps();reading=true},enabled=snapshot!=null&&!s.demo){Text("阅读应用")}
                    }
                }
            }
        }
        if(s.achievementError!=null)item(span={GridItemSpan(maxLineSpan)}) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                Text(s.achievementError,color=MaterialTheme.colorScheme.error)
                TextButton(onClick=onRetry){Text("重新整理成就")}
            }
        }
        if(showcased.isNotEmpty())item(span={GridItemSpan(maxLineSpan)}) {
            Column(Modifier.fillMaxWidth().testTag("achievement-showcase"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("我的展台",style=MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                    showcased.forEach { progress ->
                        Column(Modifier.weight(1f).clickable(onClickLabel="查看${progress.goal.name}"){selectedId=progress.goal.id}.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                            AchievementBadge(progress.goal.metric,progress.goal.tier,true,Modifier.size(72.dp))
                            Text(progress.goal.name,style=MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        if(s.achievementsLoading)item(span={GridItemSpan(maxLineSpan)}){LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(snapshot!=null&&snapshot.newIds.isNotEmpty())item(span={GridItemSpan(maxLineSpan)}) {
            Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("有 ${snapshot.newIds.size} 枚徽章新点亮",style=MaterialTheme.typography.titleSmall)
                        Text("来自已保存的日常，不必额外打卡。",style=MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick={onSeen(snapshot.newIds)},modifier=Modifier.testTag("achievement-acknowledge")){Text("收下")}
                }
            }
        }
        item(span={GridItemSpan(maxLineSpan)}) {
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("ALL" to "全部","EARNED" to "已点亮","PENDING" to "进行中","CUSTOM" to "自定义").forEach { (id,label)->
                        FilterChip(selected=filter==id,onClick={filter=id},label={Text(label)},modifier=Modifier.testTag("achievement-filter-$id"))
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=family=="ALL",onClick={family="ALL"},label={Text("所有系列")})
                    AchievementMetric.entries.forEach { metric->FilterChip(selected=family==metric.name,onClick={family=metric.name},label={Text(metric.label)}) }
                }
            }
        }
        items(displayed,key={it.goal.id}) { progress ->
            Surface(Modifier.fillMaxWidth().testTag("medal-${progress.goal.id}").clickable(onClickLabel="查看${progress.goal.name}的条件"){selectedId=progress.goal.id},
                shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    AchievementBadge(progress.goal.metric,progress.goal.tier,progress.unlocked,Modifier.size(if(large)92.dp else 86.dp))
                    Text(progress.goal.name,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Medium)
                    Text(if(progress.unlocked)"已点亮" else if(!progress.hasEvidence)"等待记录" else "正在积累",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress={progress.fraction},modifier=Modifier.fillMaxWidth().height(3.dp))
                    Text("${progress.current} / ${progress.goal.target} ${progress.goal.metric.unit}",style=MaterialTheme.typography.labelSmall)
                    if(progress.goal.id in snapshot?.settings?.showcaseIds.orEmpty())Text("展台中",style=MaterialTheme.typography.labelSmall)
                }
            }
        }
        if(snapshot!=null&&displayed.isEmpty())item(span={GridItemSpan(maxLineSpan)}) {
            Text(if(filter=="CUSTOM")"还没有自定义成就。给自己的日常取一个特别的名字。" else "这里暂时没有徽章。慢慢来，每一段积累都算数。",Modifier.padding(18.dp),style=MaterialTheme.typography.bodyMedium)
        }
        item(span={GridItemSpan(maxLineSpan)}) {
            Column(Modifier.padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("只依据已保存的记录",style=MaterialTheme.typography.titleSmall)
                if(snapshot!=null&&snapshot.fromDate.isNotEmpty())Text("统计范围：${snapshot.fromDate} 至 ${snapshot.toDate}",style=MaterialTheme.typography.bodySmall)
                Text("徽章在本机计算，不额外采集数据。未授权或缺失的数据不会被当作达成；删除、排除记录或取消收藏后会重新计算。新排除时段涉及步数时，相应日汇总不计入，避免猜测步数。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick=onRetry,enabled=!s.achievementsLoading){Text("更新成就进度")}
            }
        }
    }
    val selected=snapshot?.items?.firstOrNull{it.goal.id==selectedId}
    if(selected!=null)ModalBottomSheet(onDismissRequest={selectedId=null},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        var reveal by remember(selected.goal.id){mutableStateOf(false)}
        LaunchedEffect(selected.goal.id){reveal=true}
        val scale by animateFloatAsState(if(reveal)1f else .94f,tween(420),label="medal-reveal")
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=32.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            AchievementBadge(selected.goal.metric,selected.goal.tier,selected.unlocked,Modifier.size(136.dp).graphicsLayer{scaleX=scale;scaleY=scale})
            Text(selected.goal.name,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Medium)
            Text(selected.goal.description,style=MaterialTheme.typography.bodyLarge)
            Text(selected.goal.rule,style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("achievement-rule"))
            Text(if(selected.unlocked)"达成日期 · ${selected.earnedOn}" else "${selected.current} / ${selected.goal.target} ${selected.goal.metric.unit}",style=MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress={selected.fraction},modifier=Modifier.fillMaxWidth())
            Text(selected.goal.metric.note,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(selected.goal.metric==AchievementMetric.READING)Text("不会读取微信读书账号，也不导入第三方官方勋章。",style=MaterialTheme.typography.bodySmall)
            if(selected.unlocked) {
                FilledTonalButton(onClick={onShowcase(selected.goal.id)},enabled=!s.demo,modifier=Modifier.fillMaxWidth()) {
                    Text(if(selected.goal.id in snapshot.settings.showcaseIds)"从我的展台取下" else "放到我的展台（最多3枚）")
                }
                TextButton(onClick={selectedId=null;onDate(LocalDate.parse(selected.earnedOn))}){Text("回看达成这一天")}
            }
            if(selected.goal.custom)TextButton(onClick={deleting=selected.goal.id},enabled=!s.demo){Text("删除这个自定义成就",color=MaterialTheme.colorScheme.error)}
        }
    }
    if(reading&&snapshot!=null)ReadingAchievementApps(s,snapshot.settings.readingPackages,{reading=false}){onReadingApps(it);reading=false}
    if(deleting!=null)AlertDialog(onDismissRequest={deleting=null},title={Text("删除自定义成就？")},text={Text("只删除这个目标和展台展示，不会删除任何生活记录。")},
        confirmButton={TextButton(onClick={deleting?.let(onDelete);deleting=null;selectedId=null}){Text("删除")}},dismissButton={TextButton(onClick={deleting=null}){Text("取消")}})
}

@Composable private fun ReadingAchievementApps(s:WoUiState,current:Set<String>,dismiss:()->Unit,save:(Set<String>)->Unit) {
    var selected by remember(current){mutableStateOf(current)}
    val choices=(listOf("com.tencent.weread" to "微信读书")+s.apps.map{it.packageName to it.label}+current.map{it to it}).distinctBy{it.first}.sortedBy{it.second}
    AlertDialog(onDismissRequest=dismiss,title={Text("哪些应用用于阅读")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("只统计已授权的应用前台使用，重叠时段去重；不会读取账号、书籍或官方阅读进度。",style=MaterialTheme.typography.bodySmall)
            if(!s.appsLoaded)Text("正在读取可选应用…",style=MaterialTheme.typography.bodySmall)
            choices.forEach { (pkg,label)->
                Row(Modifier.fillMaxWidth().clickable{selected=if(pkg in selected)selected-pkg else if(selected.size<32)selected+pkg else selected}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=pkg in selected,onCheckedChange=null)
                    Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                }
            }
            Text("已选 ${selected.size} 个，最多32个。取消选择会重算阅读类成就。",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(onClick={save(selected)}){Text("保存")}},dismissButton={TextButton(onClick=dismiss){Text("取消")}})
}
