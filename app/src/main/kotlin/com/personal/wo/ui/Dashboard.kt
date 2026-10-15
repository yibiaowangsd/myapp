package com.personal.wo.ui

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.personal.wo.core.*
import java.time.*
import java.util.Locale

@Composable fun TodayDashboard(s:WoUiState, detail:()->Unit, setup:()->Unit, episode:(Episode)->Unit,
    refresh:()->Unit, demo:(Boolean)->Unit, recording:()->Unit) {
    val day=s.day ?: return
    val date=LocalDate.parse(day.dayDate)
    LazyColumn(Modifier.testTag("today-list"),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(top=10.dp,bottom=28.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${date.monthValue}月${date.dayOfMonth}日",style=MaterialTheme.typography.headlineLarge)
                    Muted("星期${listOf("一","二","三","四","五","六","日")[date.dayOfWeek.value-1]} · 截至 ${clock(day.asOfMs,day.zoneId)}")
                }
                StatusPill(if(s.demo)"演示数据" else if(s.policy.paused)"已暂停" else if(s.policy.requested)"记录中" else "未开始")
            }
        }
        if(s.demo)item {
            Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(start=14.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("虚构的一天 · 仅用于预览",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                    TextButton(onClick={demo(false)}){Text("退出演示")}
                }
            }
        }
        if(!s.policy.requested&&!s.demo) {
            item {
                Surface(shape=RoundedCornerShape(28.dp),color=Violet,contentColor=Color.White) {
                    Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                        Icon(Icons.Outlined.DateRange,null,Modifier.size(32.dp))
                        Text("让每一天，\n都有迹可循。",style=MaterialTheme.typography.headlineLarge)
                        Text("选择你想留下的记录。授权后，手机会自动整理足迹、活动与生活片段。",color=Color.White.copy(alpha=.85f))
                        Button(onClick=setup,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Violet)){Text("选择记录内容")}
                    }
                }
            }
            item { OutlinedButton(onClick={demo(true)},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("先看看，一天会怎样呈现")} }
        } else {
            item { UsageHero(day) }
            item {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    val steps=day.metrics.firstOrNull{it.key=="steps"}?.value
                    val photos=day.metrics.firstOrNull{it.key=="photos"}?.value
                    MetricTile("今日步数",steps?.let{String.format(Locale.CHINA,"%,d",it)} ?: "暂无",if(steps==null)"等待步数来源" else "步 · 来源已记录",Icons.Outlined.DirectionsWalk,laneTint(Lane.PLACE),Modifier.weight(1f),setup)
                    MetricTile("照片与视频",photos?.toString() ?: "暂无",if(photos==null)"等待相册来源" else "项 · 当天关联",Icons.Outlined.PhotoLibrary,laneTint(Lane.LIFE),Modifier.weight(1f),setup)
                }
            }
            if(day.sentences.isNotEmpty())item { Paper { Text("今天的回顾",style=MaterialTheme.typography.titleMedium);Muted(day.sentences.take(3).joinToString(""){it.text}) } }
            if(!s.demo)item {
                val usable=s.sources.count{sourceReady(it)&&it.source in s.policy.enabledSources}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick=setup){Text("$usable 项来源可用")}
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick=recording){Text(if(s.policy.paused)"继续记录" else "暂停")}
                    IconButton(onClick=refresh,enabled=!s.busy){Icon(Icons.Outlined.Refresh,"同步记录")}
                }
            }
        }
        if(day.episodes.any{it.kind=="STAY"})item { Section("今天的足迹");PlaceJourney(day,episode) }
        if(appDurations(day).isNotEmpty())item { Section("时间花在了哪里");AppTimeCard(day) }
        item { Section("生活时间线","全部片段",detail) }
        val highlights=day.episodes.filter{it.assertion!=Assertion.UNKNOWN}.sortedBy{it.startMs}.takeLast(6)
        if(highlights.isEmpty())item {
            Paper {
                IconTile(Icons.Outlined.DateRange)
                Text(if(s.policy.requested)"正在等待第一段记录" else "你的第一天，从这里开始",style=MaterialTheme.typography.titleMedium)
                Muted(if(s.policy.requested)"已授权的应用活动可以同步；足迹和步数会在你正常使用手机时逐渐出现。" else "你不用写日记。开启想记录的来源，让片段自然积累。")
                TextButton(onClick=setup){Text(if(s.policy.requested)"查看授权状态" else "设置记录权限")}
            }
        }
        items(highlights,key={it.id}) { e -> EpisodeCard(e,day.zoneId){episode(e)} }
    }
}

@Composable fun UsageHero(day:DayView) {
    val hours=remember(day){hourlyUsage(day)}
    val known=day.metrics.firstOrNull{it.key=="appMs"}?.value
    val total=remember(day){union(observedAppSpans(day).map{it.second}).sumOf{it.duration}}
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(28.dp),color=Violet,contentColor=Color.White) {
        Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.Smartphone,null,Modifier.size(18.dp))
                Text("手机使用",Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.titleMedium)
                Text("已观察时长",style=MaterialTheme.typography.labelMedium,color=Color.White.copy(alpha=.78f))
            }
            Text(if(known==null&&total==0L)"等待记录" else durationText(total),style=MaterialTheme.typography.headlineLarge)
            Column(Modifier.clearAndSetSemantics { contentDescription="每小时应用活动。"+hours.filter{it.durationMs>0}.joinToString { "${clock(it.startMs,day.zoneId)}，${durationText(it.durationMs)}" } }) {
                Row(Modifier.fillMaxWidth().height(48.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.Bottom) {
                    hours.forEach { hour ->
                        Box(Modifier.weight(1f).height(if(hour.durationMs>0)(hour.durationMs/3_600_000f*44).coerceAtLeast(4f).dp else 2.dp)
                            .clip(RoundedCornerShape(3.dp)).background(if(hour.durationMs>0)Color(0xFFE5DBFF) else Color.White.copy(alpha=.20f)))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top=6.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    (0..4).forEach { i ->
                        val label=if(i==4)"24:00" else clock(day.windowStartMs+(day.windowEndMs-day.windowStartMs)*i/4,day.zoneId)
                        Text(label,style=MaterialTheme.typography.labelSmall,color=Color.White.copy(alpha=.75f))
                    }
                }
            }
            Text("柱高表示每小时使用量 · 空白时段暂无记录",style=MaterialTheme.typography.labelSmall,color=Color.White.copy(alpha=.85f))
        }
    }
}

@Composable private fun MetricTile(title:String,value:String,caption:String,icon:androidx.compose.ui.graphics.vector.ImageVector,color:Color,modifier:Modifier,onClick:()->Unit) {
    Surface(modifier.clickable(onClick=onClick),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Icon(icon,null,Modifier.size(22.dp),tint=color)
            Text(title,style=MaterialTheme.typography.bodyMedium)
            Text(value,style=MaterialTheme.typography.headlineMedium)
            Muted(caption)
        }
    }
}

@Composable fun AppTimeCard(day:DayView) {
    val rows=remember(day){appDurations(day).take(5)}
    val max=rows.maxOfOrNull{it.durationMs}?.coerceAtLeast(1) ?: 1L
    Paper {
        rows.forEach { app ->
            Column(Modifier.padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(app.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    Text(durationText(app.durationMs),style=MaterialTheme.typography.bodyMedium)
                }
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(app.durationMs.toFloat()/max).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        Muted("按已观察的使用时长排序；分屏应用可能同时使用。")
    }
}

@Composable fun PlaceJourney(day:DayView,onEpisode:(Episode)->Unit) {
    val stays=day.episodes.filter{it.kind=="STAY"&&it.assertion!=Assertion.UNKNOWN}.sortedBy{it.startMs}
    Paper {
        stays.take(4).forEachIndexed { index,e ->
            Row(Modifier.fillMaxWidth().clickable{onEpisode(e)}.padding(vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.size(32.dp),shape=CircleShape,color=laneTint(Lane.PLACE).copy(alpha=.12f)){
                    Box(contentAlignment=Alignment.Center){Text("${index+1}",color=laneTint(Lane.PLACE),fontWeight=FontWeight.Bold)}
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(e.title.removeSuffix("附近停留"),style=MaterialTheme.typography.titleMedium)
                    Muted("${clock(e.startMs,day.zoneId)}–${clock(e.endMs,day.zoneId)} · ${durationText(e.endMs-e.startMs)}")
                }
                Text(if(e.assertion==Assertion.INFERRED)"推测" else "确认",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(index<minOf(stays.size,4)-1)Box(Modifier.padding(start=15.dp).width(2.dp).height(12.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Muted("按停留先后排列 · 点击可查看依据、命名地点")
    }
}

@Composable fun EpisodeCard(e:Episode,z:String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(44.dp).padding(top=16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(clock(e.startMs,z),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.size(8.dp).background(laneTint(e.lane),CircleShape))
            Box(Modifier.padding(top=6.dp).width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Surface(Modifier.weight(1f).clickable(onClick=onClick),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                IconTile(laneIcon(e.lane),laneTint(e.lane),36)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(e.title,style=MaterialTheme.typography.titleMedium)
                    Muted("${laneName(e.lane)}${if(e.endMs>e.startMs)" · "+durationText(e.endMs-e.startMs) else ""}")
                    if(e.assertion!=Assertion.RECORDED)Text(assertionName(e.assertion),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                    if(e.isFavorite)Icon(Icons.Outlined.Favorite,"已收藏",Modifier.size(16.dp),tint=laneTint(Lane.LIFE))
                    if(e.photoUris.isNotEmpty())PhotoThumbnail(e.photoUris.first())
                }
            }
        }
    }
}

@Composable fun TimelinePage(s:WoUiState,onDate:(LocalDate)->Unit,onEpisode:(Episode)->Unit) {
    val d=s.day ?: return
    var selected by rememberSaveable{mutableIntStateOf(-1)}
    var showMap by rememberSaveable{mutableStateOf(false)}
    val entries=d.episodes.filter{selected<0||it.lane.ordinal==selected}.sortedBy{it.startMs}
    LazyColumn(Modifier.testTag("timeline-list"),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={onDate(LocalDate.parse(d.dayDate).minusDays(1))}){Icon(Icons.Outlined.KeyboardArrowLeft,"前一天")}
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(d.dayDate,style=MaterialTheme.typography.titleLarge)
                    Muted("${d.episodes.size} 个片段")
                }
                IconButton(onClick={onDate(LocalDate.parse(d.dayDate).plusDays(1))},enabled=LocalDate.parse(d.dayDate)<LocalDate.now(ZoneId.of(d.zoneId))){Icon(Icons.Outlined.KeyboardArrowRight,"后一天")}
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected<0,{selected=-1},label={Text("全部")})
                Lane.entries.forEach { l -> FilterChip(selected==l.ordinal,{selected=l.ordinal},label={Text(laneName(l))}) }
            }
        }
        if(d.episodes.any{it.lat!=null}||s.positions.isNotEmpty())item {
            TextButton(onClick={showMap=!showMap}){Text(if(showMap)"收起位置分布" else "查看位置分布")}
            if(showMap)Footprints(d,s.positions,onEpisode)
        }
        if(entries.isEmpty())item{Empty("这个日期暂无相关片段","换一个日期或分类查看。")}
        val groups=entries.groupBy {
            when(Instant.ofEpochMilli(it.startMs).atZone(ZoneId.of(d.zoneId)).hour){in 0..5->"凌晨";in 6..11->"上午";in 12..17->"下午";else->"晚上"}
        }
        groups.forEach { (label,es) ->
            item(key=label){Section(label)}
            items(es,key={it.id}){EpisodeCard(it,d.zoneId){onEpisode(it)}}
        }
    }
}

@Composable fun MePage(s:WoUiState,permissions:()->Unit,settings:()->Unit,insights:()->Unit,recording:()->Unit,demo:()->Unit) {
    LazyColumn(verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(top=16.dp,bottom=24.dp)) {
        item { Paper {
            IconTile(Icons.Outlined.Person,size=56)
            Text("我的生活档案",style=MaterialTheme.typography.headlineMedium)
            Muted("已经留下 ${s.history.count{it.episodes.isNotEmpty()}} 天的生活片段")
            Row(verticalAlignment=Alignment.CenterVertically) {
                StatusPill(if(s.policy.paused)"记录已暂停" else if(s.policy.requested)"自动记录中" else "尚未开始记录")
                Spacer(Modifier.weight(1f))
                TextButton(onClick=if(s.policy.requested)recording else permissions){Text(if(s.policy.paused)"继续" else if(s.policy.requested)"暂停" else "去开启")}
            }
        } }
        item { Paper {
            MenuRow(Icons.Outlined.Settings,"记录权限","选择你想留下的内容",permissions)
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            MenuRow(Icons.Outlined.BarChart,"生活趋势","看看最近的时间分配",insights)
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            MenuRow(Icons.Outlined.Lock,"数据与隐私","备份、恢复、排除与更多设置",settings)
        } }
        item { Paper { MenuRow(Icons.Outlined.PlayArrow,"体验完整的一天","使用独立的虚构数据预览",demo) } }
        item { Muted("我 · ${com.personal.wo.BuildConfig.VERSION_NAME}\n生活记录保存在这部手机上。",Modifier.padding(horizontal=8.dp)) }
    }
}

@Composable private fun MenuRow(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,detail:String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Icon(icon,null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium);Muted(detail)}
        Icon(Icons.Outlined.KeyboardArrowRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun WeeklyUsageCard(history:List<DayView>,zone:String) {
    val today=LocalDate.now(ZoneId.of(zone))
    val days=(6 downTo 0).map{today.minusDays(it.toLong())}
    val values=days.map{date->history.firstOrNull{it.dayDate==date.toString()}?.metrics?.firstOrNull{it.key=="appMs"}?.value}
    val max=values.filterNotNull().maxOrNull()?.coerceAtLeast(1) ?: 1L
    Paper {
        Text("最近7天 · 手机使用",style=MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().height(144.dp).clearAndSetSemantics {
            contentDescription=days.zip(values).joinToString { (date,value)->"$date，${value?.let{durationText(it)} ?: "暂无记录"}" }
        },horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Bottom) {
            days.forEachIndexed { index,date ->
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(values[index]?.let{"${it/60_000}分"} ?: "—",style=MaterialTheme.typography.labelSmall)
                    Box(Modifier.fillMaxWidth().height(values[index]?.let{(it.toFloat()/max*90f).coerceAtLeast(3f).dp} ?: 3.dp)
                        .clip(RoundedCornerShape(6.dp)).background(if(values[index]==null)MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary))
                    Text(if(date==today)"今天" else "${date.dayOfMonth}日",style=MaterialTheme.typography.labelSmall)
                }
            }
        }
        Muted("— 表示暂无记录；柱高按实际使用时长比较。")
    }
}
