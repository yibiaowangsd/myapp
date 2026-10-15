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
                if(e.kind=="APP")AppBadge(e.packageName,e.title) else IconTile(episodeIcon(e),laneTint(e.lane),36)
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
