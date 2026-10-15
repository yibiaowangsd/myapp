package com.personal.wo.ui

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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.personal.wo.core.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.*
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DayDrumHome(s:WoUiState,onChapter:(DayChapter)->Unit,statistics:()->Unit,
    refresh:()->Unit,setup:()->Unit,demo:(Boolean)->Unit,onDate:(LocalDate)->Unit={},
    onThreshold:(Int)->Unit={},today:LocalDate=LocalDate.now(ZoneId.of(s.policy.archiveZone))) {
    val day=s.day ?: return
    val date=LocalDate.parse(s.selectedDate)
    val chapters=remember(day,s.homeAppMinMinutes){dayChapters(day,s.homeAppMinMinutes)}
    var calendar by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
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
                DropdownMenu(menu,{menu=false}) {
                    DropdownMenuItem(text={Text("应用显示门槛 · ${s.homeAppMinMinutes}分钟")},onClick={menu=false;filter=true},leadingIcon={Icon(Icons.Outlined.Tune,null)})
                    DropdownMenuItem(text={Text("更新记录")},onClick={menu=false;refresh()},enabled=!s.busy&&!s.demo,leadingIcon={Icon(Icons.Outlined.Refresh,null)})
                    if(s.demo)DropdownMenuItem(text={Text("退出虚构演示")},onClick={menu=false;demo(false)})
                }
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
                Paper {
                    IconTile(Icons.Outlined.Timeline,size=56)
                    Text("这一天，还没有留下片段",style=MaterialTheme.typography.headlineSmall)
                    Muted("可以换个日期回看。留下的照片、活动和较长的应用时段，会在这条时间轴上浮现。")
                    TextButton(onClick=setup){Text("开启记录来源")}
                    TextButton(onClick={demo(true)}){Text("转动示例看看")}
                }
            }
        } else key(day.dayDate,s.demo) {
            val pager=rememberPagerState(initialPage=1,pageCount={chapters.size+2})
            DayDrum(day,chapters,pager,onChapter,onDate,today,Modifier.weight(1f))
        }
    }
    if(filter)AlertDialog(onDismissRequest={filter=false},title={Text("首页保留较长的应用使用")},text={
        Column {
            Text("单次使用达到所选时长才出现在时间轴。较短的使用仍保留在时段详情里。",style=MaterialTheme.typography.bodyMedium)
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

/** Signed horizontal displacement; all other depth properties are mirror-symmetric. */
internal data class DrumPose(val x:Float,val y:Float,val rotation:Float,val scale:Float,val alpha:Float,val depth:Float)
internal fun drumPose(offset:Float):DrumPose {
    val angle=offset.coerceIn(-1.5f,1.5f)*58f
    val radians=angle*PI.toFloat()/180f
    val depth=cos(radians).coerceAtLeast(0f)
    return DrumPose(sin(radians)*.80f,(1f-depth)*18f,angle,.80f+.20f*depth,
        if(abs(offset)>1.5f)0f else .32f+.68f*depth,depth)
}

@Composable internal fun DayDrum(day:DayView,chapters:List<DayChapter>,pager:PagerState,
    onChapter:(DayChapter)->Unit,onDate:(LocalDate)->Unit,today:LocalDate,modifier:Modifier=Modifier) {
    val scope=rememberCoroutineScope()
    var navigation by remember { mutableStateOf<Job?>(null) }
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val selected=pager.currentPage.coerceIn(0,chapters.size+1)
    val active=chapters.getOrNull(selected-1)
    val date=LocalDate.parse(day.dayDate)
    fun move(index:Int,animate:Boolean=true) {
        navigation?.cancel()
        navigation=scope.launch {
            val target=index.coerceIn(0,chapters.size+1)
            if(animate)pager.animateScrollToPage(target) else pager.scrollToPage(target)
        }
    }
    fun activate(index:Int) {
        // Read the live pager state; a cached lazy-item callback may outlive the
        // composition that selected it. Native dragging already cancels taps.
        if(index!=pager.currentPage){move(index);return}
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
                        Text(active?.part?.let{"${it.label} · ${partRange(it,day.zoneId)}"} ?: if(selected==0)"回看前一天" else if(date<today)"继续到后一天" else "今天，先到这里",
                            style=MaterialTheme.typography.titleSmall,modifier=Modifier.testTag("drum-selected-time"))
                        Text(if(active!=null)"$selected / ${chapters.size} 个时段" else "日期之间，也可以转动",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier=Modifier.testTag("drum-position"))
                    }
                    IconButton(onClick={move(selected+1)},enabled=selected<chapters.size+1){Icon(Icons.Outlined.ChevronRight,"下一段经历")}
                }
                if(!wide)Text("左右转动 · 轻触展开这一段",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                val length=(day.windowEndMs-day.windowStartMs).coerceAtLeast(1)
                val fraction=active?.let{((it.part.startMs-day.windowStartMs).toFloat()/length).coerceIn(0f,1f)} ?: if(selected==0)0f else 1f
                Slider(value=scrubbing ?: fraction,onValueChange={ value ->
                    scrubbing=value
                    val next=nearestChapter(chapters,day.windowStartMs+(length*value).toLong())+1
                    if(next!=pager.currentPage)move(next,false)
                },onValueChangeFinished={scrubbing=null},enabled=chapters.size>1,
                    modifier=Modifier.fillMaxWidth().height(40.dp).testTag("day-time-scrubber").semantics {
                        contentDescription="一天的时间轴"
                        stateDescription=active?.part?.label ?: "日期边界"
                    })
                Row(Modifier.fillMaxWidth().padding(bottom=if(wide)4.dp else 12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    listOf("凌晨","上午","下午","晚上").forEach { part ->
                        val first=chapters.indexOfFirst{it.part.label==part}
                        Text(part,Modifier.testTag("drum-jump-$part").clip(CircleShape).clickable(enabled=first>=0){move(first+1)}.padding(horizontal=if(wide)4.dp else 8.dp,vertical=12.dp),
                            style=MaterialTheme.typography.labelMedium,color=if(part==active?.part?.label)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=if(first>=0)1f else .45f))
                    }
                }
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

@Composable private fun CylinderPages(day:DayView,chapters:List<DayChapter>,pager:PagerState,
    activate:(Int)->Unit,hasNextDate:Boolean,compact:Boolean,modifier:Modifier) {
    val latest by rememberUpdatedState(chapters)
    val activateLatest by rememberUpdatedState(activate)
    val displayDensity=LocalDensity.current
    val largeType=LocalDensity.current.fontScale>=1.3f
    BoxWithConstraints(modifier.testTag("drum-scene").clipToBounds(),contentAlignment=Alignment.Center) {
        val width=(maxWidth*.74f).coerceAtMost(380.dp)
        val widthPx=with(displayDensity){width.toPx()}
        val padding=(maxWidth-width)/2
        val height=(maxHeight-28.dp).coerceAtMost(if(compact||largeType)540.dp else width*1.40f).coerceAtLeast(48.dp)
        // Visuals are siblings on a shared canvas, never transformed outside a lazy
        // page's clipping rectangle. Both sides use exactly the same depth function.
        val visible=(pager.currentPage-2..pager.currentPage+2).filter{it in 0..chapters.size+1}
        visible.forEach { index ->
            val offset=(index-pager.currentPage)-pager.currentPageOffsetFraction
            val pose=drumPose(offset)
            if(pose.alpha>0f)key(chapters.getOrNull(index-1)?.part?.startMs ?: "edge-$index") {
                val visual=Modifier.offset { IntOffset((pose.x*widthPx).roundToInt(),with(displayDensity){pose.y.dp.roundToPx()}) }
                    .width(width).height(height).zIndex(pose.depth).graphicsLayer {
                    rotationY=pose.rotation;cameraDistance=14f*density
                    scaleX=pose.scale;scaleY=pose.scale;alpha=pose.alpha
                }.clearAndSetSemantics { }
                val chapter=chapters.getOrNull(index-1)
                if(chapter!=null)ChapterFace(chapter,day.zoneId,visual,compact,largeType)
                else DateFace(day,index==0,hasNextDate,visual,compact)
            }
        }
        // Transparent native pager owns dragging, snapping, keys and accessibility.
        // Its hit areas align with the centered card and the two exposed edges.
        HorizontalPager(state=pager,pageSize=PageSize.Fixed(width),contentPadding=PaddingValues(horizontal=padding,vertical=14.dp),
            beyondViewportPageCount=1,flingBehavior=PagerDefaults.flingBehavior(pager,pagerSnapDistance=PagerSnapDistance.atMost(4)),
            key={ index -> latest.getOrNull(index-1)?.part?.startMs ?: when(index){0->"previous-day";latest.size+1->"end-day";else->"pending-$index"} },
            modifier=Modifier.fillMaxSize().zIndex(2f).testTag("experience-drum")) { index ->
            val chapter=latest.getOrNull(index-1)
            val center=index==pager.currentPage
            Box(Modifier.fillMaxWidth().height(height).then(if(center)Modifier.testTag("chapter-open").semantics {
                contentDescription=chapter?.let { "${it.part.label}，${partRange(it.part,day.zoneId)}。${it.summary}"+
                    it.apps.joinToString("，"){a->"${a.episode.title}${durationText(a.durationMs)}"}+
                    if(it.photos.isNotEmpty())"，${it.photos.size}项影像" else "" } ?: if(index==0)"回看前一天" else if(hasNextDate)"继续到后一天" else "今天，先到这里"
            } else Modifier.clearAndSetSemantics { }).clickable(enabled=chapter!=null||index==0||hasNextDate||!center,
                onClickLabel=if(chapter!=null)"查看这个时段的全部片段" else "查看日期"){activateLatest(index)})
        }
    }
}

internal fun partRange(part:RecapPart,zone:String)="${clock(part.startMs,zone)}–${if(clock(part.endMs,zone)=="00:00")"24:00" else clock(part.endMs,zone)}"
private fun partIcon(label:String)=when(label){"凌晨"->Icons.Outlined.Bedtime;"上午"->Icons.Outlined.WbSunny;"下午"->Icons.Outlined.LightMode;else->Icons.Outlined.DarkMode}

@Composable private fun ChapterFace(chapter:DayChapter,zone:String,modifier:Modifier,compact:Boolean,largeType:Boolean) {
    val part=chapter.part;val tint=partTint(part.label)
    Surface(modifier,shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,tint.copy(alpha=.18f)),shadowElevation=4.dp) {
        BoxWithConstraints {
            if(compact||(largeType&&maxHeight<440.dp))Row(Modifier.fillMaxSize().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
                if(chapter.photos.isNotEmpty())PhotoThumbnail(chapter.photos.first().uri,Modifier.size(76.dp),crop=true)
                else IconTile(partIcon(part.label),tint,48)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(part.label,style=MaterialTheme.typography.titleLarge,color=tint)
                    Text(partRange(part,zone),style=MaterialTheme.typography.labelMedium)
                    Text(chapter.summary,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
                    Text("${part.episodes.size}个片段 · 轻触展开",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else Column(Modifier.fillMaxSize().padding(if(largeType)16.dp else 20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconTile(partIcon(part.label),tint,40)
                    Column(Modifier.weight(1f)) {
                        Text(part.label,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Medium,color=tint)
                        Text(partRange(part,zone),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(chapter.summary,style=MaterialTheme.typography.bodyLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(chapter.photos.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        chapter.photos.take(2).forEach { photo ->
                            Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                PhotoThumbnail(photo.uri,Modifier.fillMaxWidth().weight(1f),crop=true)
                                Text(clock(photo.episode.startMs,zone),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if(chapter.apps.isNotEmpty()) {
                    val shown=chapter.apps.take(if(largeType||chapter.photos.isNotEmpty())1 else 3)
                    shown.forEach { app ->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                            AppBadge(app.episode.packageName,app.episode.title,Modifier.size(28.dp))
                            Text(app.episode.title,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelLarge)
                            Text(durationText(app.durationMs),style=MaterialTheme.typography.labelMedium,color=tint)
                        }
                    }
                    if(chapter.apps.size>shown.size)Text("另有${chapter.apps.size-shown.size}个应用的较长使用",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(chapter.photos.isEmpty())Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("${part.episodes.size}个片段${if(chapter.photos.size>2)" · ${chapter.photos.size}项影像" else ""}",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("展开",style=MaterialTheme.typography.labelLarge,color=tint)
                    Icon(Icons.Outlined.ChevronRight,null,Modifier.size(18.dp),tint=tint)
                }
            }
        }
    }
}

@Composable private fun DateFace(day:DayView,previous:Boolean,hasNext:Boolean,modifier:Modifier,compact:Boolean) {
    val date=LocalDate.parse(day.dayDate).plusDays(if(previous)-1 else 1)
    val end=!previous&&!hasNext
    Surface(modifier,shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainer,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxSize().padding(if(compact)16.dp else 24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
            Icon(if(end)Icons.Outlined.CheckCircle else Icons.Outlined.DateRange,null,Modifier.size(if(compact)28.dp else 40.dp),tint=MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(if(end)"今天，先到这里" else "${date.monthValue}月${date.dayOfMonth}日",style=MaterialTheme.typography.titleLarge)
            Text(if(end)"截至 ${clock(day.asOfMs,day.zoneId)}" else if(previous)"回看前一天" else "继续到后一天",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(!compact)Text(if(end)"新的经历，会继续在这里浮现。" else "轻触翻开这一天",Modifier.padding(top=20.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun ChapterDetailsPage(day:DayView,chapter:DayChapter?,minimum:Int,onEpisode:(Episode)->Unit) {
    if(chapter==null){Empty("这个时段已更新","返回时间轴查看最新片段。");return}
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
