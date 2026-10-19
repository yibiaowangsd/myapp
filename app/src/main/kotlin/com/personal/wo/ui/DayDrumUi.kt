package com.personal.wo.ui

import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.personal.wo.core.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DayDrumHome(s:WoUiState,onChapter:(DayChapter)->Unit,statistics:()->Unit,
    refresh:()->Unit,setup:()->Unit,demo:(Boolean)->Unit,onDate:(LocalDate)->Unit={},
    onThreshold:(Int)->Unit={},today:LocalDate=LocalDate.now(ZoneId.of(s.policy.archiveZone)),onAll:()->Unit={}) {
    val day=s.day ?: return
    val date=LocalDate.parse(s.selectedDate)
    val projection=resolvedHomeProjection(s,day)
    if(projection==null) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
        return
    }
    val chapters=projection.chapters
    var calendar by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val pager=key(day.dayDate,s.demo){rememberPagerState(initialPage=1,pageCount={chapters.size+2})}
    val dark=MaterialTheme.colorScheme.background.luminance()<.15f
    TimelineAtmosphere(day,chapters,{pager.currentPage+pager.currentPageOffsetFraction},dark) {
    Column(Modifier.fillMaxSize().testTag("day-drum-home")) {
        Row(Modifier.fillMaxWidth().padding(start=20.dp,end=8.dp,top=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel="选择日期"){calendar=true}.padding(4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("${date.year}年${date.monthValue}月${date.dayOfMonth}日",style=MaterialTheme.typography.titleLarge,modifier=Modifier.testTag("drum-date"))
                    Icon(Icons.Outlined.ExpandMore,null,Modifier.size(20.dp))
                }
                Text("星期${listOf("一","二","三","四","五","六","日")[date.dayOfWeek.value-1]} · ${if(s.demo)"虚构演示" else "这一天，慢慢回看"}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick=statistics,enabled=day.dayDate==s.selectedDate){Icon(Icons.Outlined.BarChart,"一天统计")}
            Box {
                IconButton(onClick={menu=true}){Icon(Icons.Outlined.MoreVert,"时间轴选项")}
                EventCardTheme { DropdownMenu(menu,{menu=false}) {
                    DropdownMenuItem(text={Text("查看全部片段")},onClick={menu=false;onAll()},leadingIcon={Icon(Icons.Outlined.List,null)})
                    DropdownMenuItem(text={Text("应用显示门槛 · ${s.homeAppMinMinutes}分钟")},onClick={menu=false;filter=true},leadingIcon={Icon(Icons.Outlined.Tune,null)})
                    DropdownMenuItem(text={Text("更新记录")},onClick={menu=false;refresh()},enabled=!s.busy&&!s.demo,leadingIcon={Icon(Icons.Outlined.Refresh,null)})
                    if(s.demo)DropdownMenuItem(text={Text("退出虚构演示")},onClick={menu=false;demo(false)})
                } }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={onDate(date.minusDays(1))}){Icon(Icons.Outlined.ChevronLeft,null,Modifier.size(18.dp));Text("前一天")}
            TextButton(onClick={if(s.demo)demo(false);onDate(today)},enabled=date!=today||s.demo){Text(if(date==today&&!s.demo)"今天" else "回到今天")}
            TextButton(onClick={onDate(date.plusDays(1))},enabled=date<today){Text("后一天");Icon(Icons.Outlined.ChevronRight,null,Modifier.size(18.dp))}
        }
        if(day.dayDate!=s.selectedDate) {
            Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
        } else if(chapters.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center) {
                EventCardTheme { Paper {
                    IconTile(Icons.Outlined.Timeline,size=56)
                    Text(if(recapEpisodes(day).isEmpty())"这一天，还没有留下片段" else "小片段，先收在这里",style=MaterialTheme.typography.headlineSmall)
                    Muted("可以换个日期回看。留下的照片、活动和较长的应用时段，会在这条时间轴上浮现。")
                    if(recapEpisodes(day).isNotEmpty())TextButton(onClick=onAll){Text("查看全部 ${recapEpisodes(day).size} 个片段")}
                    TextButton(onClick=setup){Text("开启记录来源")}
                    TextButton(onClick={demo(true)}){Text("转动示例看看")}
                } }
            }
        } else {
            DayDrum(day,chapters,pager,onChapter,onDate,today,Modifier.weight(1f),onAll)
        }
    }
    }
    if(filter)AlertDialog(onDismissRequest={filter=false},title={Text("首页保留较长的应用使用")},text={
        Column {
            Text("单次使用达到所选时长才出现在时间轴。较短的使用仍保留在事件详情和全部片段里。",style=MaterialTheme.typography.bodyMedium)
            homeAppThresholds.forEach { minutes ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable{onThreshold(minutes);filter=false}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=s.homeAppMinMinutes==minutes,onClick={onThreshold(minutes);filter=false})
                    Text("至少 $minutes 分钟${if(minutes==10)"（默认）" else ""}")
                }
            }
        }
    },confirmButton={TextButton(onClick={filter=false}){Text("完成")}})
    if(calendar) {
        // Material date selection is UTC midnight, independent of the archive zone.
        val picker=rememberDatePickerState(initialSelectedDateMillis=date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates=object:SelectableDates {
                override fun isSelectableDate(utcTimeMillis:Long)=Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()<=today
                override fun isSelectableYear(year:Int)=year<=today.year
            })
        DatePickerDialog(onDismissRequest={calendar=false},confirmButton={TextButton(onClick={
            picker.selectedDateMillis?.let{onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())};calendar=false
        },enabled=picker.selectedDateMillis!=null){Text("回看这一天")}},dismissButton={TextButton(onClick={calendar=false}){Text("取消")}}) {
            DatePicker(state=picker,title={Text("选择回看的日期",Modifier.padding(24.dp))})
        }
    }
}

@Composable internal fun resolvedHomeProjection(s:WoUiState,day:DayView):HomeProjection? {
    s.homeProjection?.takeIf { it.matches(day,s.homeAppMinMinutes) }?.let { return it }
    // Standalone previews and a threshold changed during a reload use the same
    // background projection contract; no fallback aggregation on the UI thread.
    val projection by produceState<HomeProjection?>(null,day,s.homeAppMinMinutes) {
        value=withContext(Dispatchers.Default){HomeProjection(day,s.homeAppMinMinutes,dayChapters(day,s.homeAppMinMinutes))}
    }
    return projection
}

@Composable internal fun DayDrum(day:DayView,chapters:List<DayChapter>,pager:PagerState,
    onChapter:(DayChapter)->Unit,onDate:(LocalDate)->Unit,today:LocalDate,modifier:Modifier=Modifier,onAll:()->Unit={}) {
    val scope=rememberCoroutineScope()
    var navigation by remember { mutableStateOf<Job?>(null) }
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val selected=pager.currentPage.coerceIn(0,chapters.size+1)
    val active=chapters.getOrNull(selected-1)
    val date=LocalDate.parse(day.dayDate)
    val chapterTimes=remember(chapters){chapters.map{it.part.startMs}.toLongArray()}
    val times=remember(day,chapters){drumTimes(day,chapters)}
    fun move(index:Int) {
        navigation?.cancel()
        navigation=scope.launch {
            pager.animateScrollToPage(index.coerceIn(0,chapters.size+1),
                animationSpec=spring(dampingRatio=1f,stiffness=240f,visibilityThreshold=.1f))
        }
    }
    fun activate(index:Int) {
        if(index!=pager.currentPage){move(index);return}
        // A partly rotated card is brought to rest before opening its details.
        if(pager.isScrollInProgress||kotlin.math.abs(pager.currentPageOffsetFraction)>.02f){move(index);return}
        navigation?.cancel()
        when(index){0->onDate(date.minusDays(1));chapters.size+1->if(date<today)onDate(date.plusDays(1));else->chapters.getOrNull(index-1)?.let(onChapter)}
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide=maxWidth>maxHeight*1.2f
        val controls:@Composable (Modifier)->Unit={ controlModifier ->
            Column(controlModifier.verticalScroll(rememberScrollState()).padding(horizontal=if(wide)12.dp else 24.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={move(selected-1)},enabled=selected>0){Icon(Icons.Outlined.ChevronLeft,"上一段经历")}
                    Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(active?.part?.let{partRange(it,day.zoneId)} ?: if(selected==0)"回看前一天" else if(date<today)"继续到后一天" else "今天，先到这里",
                            style=MaterialTheme.typography.titleSmall,modifier=Modifier.testTag("drum-selected-time"))
                        Text(if(active!=null)"$selected / ${chapters.size} 个大事件" else "日期之间，也可以转动",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier=Modifier.testTag("drum-position"))
                    }
                    IconButton(onClick={move(selected+1)},enabled=selected<chapters.size+1){Icon(Icons.Outlined.ChevronRight,"下一段经历")}
                }
                if(!wide)Text("左右转动 · 轻触展开这一段",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                val length=(day.windowEndMs-day.windowStartMs).coerceAtLeast(1)
                val fraction=((timelineTime(times,pager.currentPage+pager.currentPageOffsetFraction)-day.windowStartMs).toFloat()/length).coerceIn(0f,1f)
                Slider(value=scrubbing ?: fraction,onValueChange={ value ->
                    scrubbing=value
                    // Keep the position fractional while the thumb is held. Repeated
                    // nearest-page jumps were a second source of light/motion flashes.
                    val position=timelinePosition(chapterTimes,day.windowStartMs+(length*value).toLong())+1f
                    val page=position.roundToInt()
                    navigation?.cancel()
                    navigation=scope.launch{pager.scrollToPage(page,position-page)}
                },onValueChangeFinished={
                    val value=scrubbing
                    scrubbing=null
                    if(value!=null)move(nearestChapter(chapters,day.windowStartMs+(length*value).toLong())+1)
                },enabled=chapters.size>1,
                    modifier=Modifier.fillMaxWidth().height(40.dp).testTag("day-time-scrubber").semantics {
                        contentDescription="一天的时间轴"
                        stateDescription=active?.part?.let{partRange(it,day.zoneId)} ?: "日期边界"
                    })
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    listOf(0f,.25f,.5f,.75f,1f).forEach { tick ->
                        val time=day.windowStartMs+(length*tick).toLong()
                        Text(if(tick==1f)"24:00" else clock(time,day.zoneId),Modifier.testTag("drum-tick-${(tick*100).toInt()}").clip(CircleShape)
                            .clickable{move(nearestChapter(chapters,time)+1)}.padding(horizontal=4.dp,vertical=8.dp),style=MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(onClick=onAll,modifier=Modifier.testTag("all-fragments")){Text("查看全部片段",style=MaterialTheme.typography.labelMedium)}
            }
        }
        if(wide)Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically) {
            CylinderPages(day,chapters,pager,::activate,date<today,true,Modifier.weight(.62f).fillMaxHeight())
            controls(Modifier.weight(.38f).fillMaxHeight())
        } else Column(Modifier.fillMaxSize()) {
            CylinderPages(day,chapters,pager,::activate,date<today,false,Modifier.weight(1f).fillMaxWidth())
            controls(Modifier.fillMaxWidth())
        }
    }
}

internal fun partRange(part:RecapPart,zone:String)=if(part.startMs==part.endMs)clock(part.startMs,zone) else "${clock(part.startMs,zone)}–${if(clock(part.endMs,zone)=="00:00")"24:00" else clock(part.endMs,zone)}"

@Composable fun ChapterDetailsPage(day:DayView,chapter:DayChapter?,minimum:Int,onEpisode:(Episode)->Unit) {
    if(chapter==null){Empty("这个大事件已更新","返回时间轴查看最新片段。");return}
    LazyColumn(Modifier.testTag("chapter-details"),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(top=8.dp,bottom=24.dp)) {
        item { Text(chapter.part.label,style=MaterialTheme.typography.headlineLarge);Muted("${day.dayDate} · ${partRange(chapter.part,day.zoneId)}");Text(chapter.summary,Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodyLarge) }
        if(chapter.photos.isNotEmpty()) {
            item { Section("这一段的影像") }
            items(chapter.photos.chunked(2),key={it.first().uri}) { pair ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    pair.forEach { photo ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClickLabel="查看影像详情"){onEpisode(photo.episode)},verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            PhotoThumbnail(photo.uri,Modifier.fillMaxWidth().aspectRatio(1f),crop=true)
                            Text(clock(photo.episode.startMs,day.zoneId),style=MaterialTheme.typography.labelMedium)
                        }
                    }
                    if(pair.size==1)Spacer(Modifier.weight(1f))
                }
            }
        }
        item { Section("全部片段");Muted("包括首页收起的短使用；首页应用门槛为单次至少${minimum}分钟。") }
        items(chapter.part.episodes,key={it.id}) { e->EpisodeCard(e,day.zoneId){onEpisode(e)} }
    }
}

@Composable fun DayStatisticsPage(s:WoUiState,all:()->Unit,steps:()->Unit,onEpisode:(Episode)->Unit) {
    val day=s.day ?: return
    LazyColumn(Modifier.testTag("statistics-list"),verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(top=12.dp,bottom=24.dp)) {
        item { Muted("${day.dayDate} · 截至 ${clock(day.asOfMs,day.zoneId)}${if(s.demo)" · 虚构演示" else ""}") }
        item { DayStatistics(day,steps,all) }
        item { DayUsageChart(day) }
        if(appPeriods(day).isNotEmpty())item { AppPeriodsCard(day,onEpisode,all) }
        item { TextButton(onClick=all,modifier=Modifier.fillMaxWidth()){Text("查看全部片段") } }
    }
}
