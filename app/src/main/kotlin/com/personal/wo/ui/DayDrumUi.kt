package com.personal.wo.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
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
import java.time.LocalDate
import kotlin.math.*

@Composable fun DayDrumHome(s:WoUiState,onEpisode:(Episode)->Unit,statistics:()->Unit,
    refresh:()->Unit,setup:()->Unit,demo:(Boolean)->Unit) {
    val day=s.day ?: return
    val date=LocalDate.parse(day.dayDate)
    val moments=remember(day){dayMoments(day)}
    Column(Modifier.fillMaxSize().testTag("day-drum-home")) {
        Row(Modifier.fillMaxWidth().padding(start=24.dp,end=12.dp,top=8.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text("${date.monthValue}月${date.dayOfMonth}日",style=MaterialTheme.typography.titleLarge)
                Text("星期${listOf("一","二","三","四","五","六","日")[date.dayOfWeek.value-1]} · ${if(s.demo)"虚构演示" else if(s.policy.paused)"已暂停" else if(s.policy.requested)"记录中" else "未开始记录"}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick=refresh,enabled=!s.busy&&!s.demo){Icon(Icons.Outlined.Refresh,"更新今天")}
            IconButton(onClick=statistics){Icon(Icons.Outlined.BarChart,"一天统计")}
        }
        if(s.demo)Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("演示数据不会存入记录",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={demo(false)}){Text("退出演示")}
        }
        if(moments.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center) {
                Paper {
                    IconTile(Icons.Outlined.Timeline,size=56)
                    Text("从第一段经历开始",style=MaterialTheme.typography.headlineSmall)
                    Muted("留下的照片、活动与应用时段，会在这里按时间浮现。")
                    TextButton(onClick=setup){Text("开启记录来源")}
                    TextButton(onClick={demo(true)}){Text("转动示例看看")}
                }
            }
        } else key(day.dayDate,s.demo) {
            val pager=rememberPagerState(pageCount={moments.size})
            DayDrum(day,moments,pager,onEpisode,Modifier.weight(1f))
        }
    }
}

/** A bounded day, laid out on a cylinder. Only nearby pages are composed. */
@Composable internal fun DayDrum(day:DayView,moments:List<DayMoment>,pager:PagerState,
    onEpisode:(Episode)->Unit,modifier:Modifier=Modifier) {
    val scope=rememberCoroutineScope()
    var navigation by remember { mutableStateOf<Job?>(null) }
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val selected=pager.currentPage.coerceIn(moments.indices)
    val active=moments[selected]
    fun move(index:Int,animate:Boolean=true) {
        navigation?.cancel()
        navigation=scope.launch {
            val target=index.coerceIn(moments.indices)
            if(animate)pager.animateScrollToPage(target) else pager.scrollToPage(target)
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide=maxWidth>maxHeight*1.2f
        val controls:@Composable (Modifier)->Unit={ controlModifier ->
            Column(controlModifier.verticalScroll(rememberScrollState()).padding(horizontal=if(wide)12.dp else 24.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={move(selected-1)},enabled=selected>0){Icon(Icons.Outlined.ChevronLeft,"上一段经历")}
                    Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("${active.part} · ${clock(active.anchorMs,day.zoneId)}",style=MaterialTheme.typography.titleMedium,
                            modifier=Modifier.testTag("drum-selected-time"))
                        Text("${selected+1} / ${moments.size}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier=Modifier.testTag("drum-position"))
                    }
                    IconButton(onClick={move(selected+1)},enabled=selected<moments.lastIndex){Icon(Icons.Outlined.ChevronRight,"下一段经历")}
                }
                if(!wide)Text("左右转动，回看这一天",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                val length=(day.windowEndMs-day.windowStartMs).coerceAtLeast(1)
                val fraction=((active.anchorMs-day.windowStartMs).toFloat()/length).coerceIn(0f,1f)
                Slider(value=scrubbing ?: fraction,onValueChange={ value ->
                    scrubbing=value
                    val next=nearestMoment(moments,day.windowStartMs+(length*value).toLong())
                    if(next!=pager.currentPage)move(next,false)
                },onValueChangeFinished={scrubbing=null},enabled=moments.size>1,
                    modifier=Modifier.fillMaxWidth().height(40.dp).testTag("day-time-scrubber").semantics {
                        contentDescription="一天的时间轴"
                        stateDescription="${active.part} ${clock(active.anchorMs,day.zoneId)}"
                    })
                Row(Modifier.fillMaxWidth().padding(bottom=if(wide)4.dp else 16.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    listOf("凌晨","上午","下午","晚上").forEach { part ->
                        val first=moments.indexOfFirst{it.part==part}
                        Text(part,Modifier.testTag("drum-jump-$part").clip(CircleShape).clickable(enabled=first>=0){move(first)}.padding(horizontal=if(wide)4.dp else 8.dp,vertical=12.dp),
                            style=MaterialTheme.typography.labelMedium,color=if(part==active.part)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=if(first>=0)1f else .45f))
                    }
                }
            }
        }
        if(wide)Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically) {
            CylinderPages(day,moments,pager,onEpisode,{move(it)},true,Modifier.weight(.62f).fillMaxHeight())
            controls(Modifier.weight(.38f).fillMaxHeight())
        } else Column(Modifier.fillMaxSize()) {
            CylinderPages(day,moments,pager,onEpisode,{move(it)},false,Modifier.weight(1f).fillMaxWidth())
            controls(Modifier.fillMaxWidth())
        }
    }
}

@Composable private fun CylinderPages(day:DayView,moments:List<DayMoment>,pager:PagerState,
    onEpisode:(Episode)->Unit,move:(Int)->Unit,compact:Boolean,modifier:Modifier) {
    // Pager's count can change before an older item-provider lambda is discarded.
    // Read the latest snapshot in both key and content callbacks during refresh.
    val latest by rememberUpdatedState(moments)
    val largeType=LocalDensity.current.fontScale>=1.3f
    BoxWithConstraints(modifier) {
        val width=(maxWidth*.78f).coerceAtMost(380.dp)
        val padding=(maxWidth-width)/2
        val height=(maxHeight-36.dp).coerceAtMost(if(compact||largeType)520.dp else width*1.40f).coerceAtLeast(48.dp)
        HorizontalPager(state=pager,pageSize=PageSize.Fixed(width),
            contentPadding=PaddingValues(horizontal=padding,vertical=18.dp),
            beyondViewportPageCount=1,
            flingBehavior=PagerDefaults.flingBehavior(pager,pagerSnapDistance=PagerSnapDistance.atMost(4)),
            key={latest.getOrNull(it)?.episode?.id ?: "pending-$it"},
            modifier=Modifier.fillMaxSize().testTag("experience-drum")) { page ->
            val moment=latest.getOrNull(page) ?: return@HorizontalPager
            val centered=page==pager.currentPage
            Box(Modifier.fillMaxSize().zIndex(if(centered)2f else 1f),contentAlignment=Alignment.Center) {
                val visual=Modifier.fillMaxWidth().height(height).graphicsLayer {
                    val offset=(page-pager.currentPage)-pager.currentPageOffsetFraction
                    val angle=offset.coerceIn(-1.5f,1.5f)*62f
                    val radians=angle*PI.toFloat()/180f
                    val depth=cos(radians).coerceAtLeast(0f)
                    translationX=sin(radians)*size.width*.82f-offset*size.width
                    translationY=(1f-depth)*18.dp.toPx()
                    rotationY=angle
                    cameraDistance=14f*density
                    scaleX=.80f+.20f*depth
                    scaleY=.80f+.20f*depth
                    alpha=if(abs(offset)>1.45f)0f else .25f+.75f*depth
                }.then(if(centered)Modifier else Modifier.clearAndSetSemantics { })
                MomentFace(moment,day.zoneId,visual,compact) {
                    if(centered&&!pager.isScrollInProgress)onEpisode(moment.episode) else move(page)
                }
            }
        }
    }
}

@Composable private fun MomentFace(moment:DayMoment,zone:String,modifier:Modifier,compact:Boolean,onClick:()->Unit) {
    val e=moment.episode
    val tint=partTint(moment.part)
    val shape=RoundedCornerShape(30.dp)
    val largeType=LocalDensity.current.fontScale>=1.3f
    val title=e.title.removeSuffix("附近停留").ifBlank{laneName(e.lane)}
    val caption=when {
        e.kind=="STEPS"&&e.amount!=null->"${e.amount} 步 · 这段时间的记录"
        moment.endMs>moment.startMs->"${clock(moment.startMs,zone)}–${if(clock(moment.endMs,zone)=="00:00")"24:00" else clock(moment.endMs,zone)} · ${durationText(moment.endMs-moment.startMs)}"
        e.kind=="PHOTO"->if(e.photoUris.size>1)"${e.photoUris.size} 项关联影像" else "这一刻的影像"
        else->laneName(e.lane)
    }
    Surface(modifier.testTag("moment-${e.id}"),shape=shape,color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f)),shadowElevation=3.dp) {
        BoxWithConstraints {
            val photoHeight=(maxHeight*(if(largeType).24f else .38f)).coerceIn(48.dp,220.dp)
            val iconHeight=(maxHeight*.20f).coerceIn(48.dp,100.dp)
            if(compact)Row(Modifier.fillMaxSize().clickable(onClickLabel="查看这段经历",onClick=onClick)
                .verticalScroll(rememberScrollState()).padding(16.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                if(e.photoUris.isNotEmpty())PhotoThumbnail(e.photoUris.first(),Modifier.size(56.dp),crop=true)
                else if(e.kind=="APP")AppBadge(e.packageName,e.title,Modifier.size(48.dp))
                else IconTile(episodeIcon(e),laneTint(e.lane),48)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("${moment.part} · ${clock(moment.anchorMs,zone)}",style=MaterialTheme.typography.labelLarge,color=tint)
                    Text(title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(caption,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(e.assertion!=Assertion.RECORDED)Text(assertionName(e.assertion),style=MaterialTheme.typography.labelSmall,color=tint)
                }
            } else Column(Modifier.fillMaxSize().clickable(onClickLabel="查看这段经历",onClick=onClick)
                .verticalScroll(rememberScrollState()).padding(if(largeType)16.dp else 22.dp),
                verticalArrangement=Arrangement.spacedBy(if(largeType)10.dp else 14.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Icon(when(moment.part){"凌晨"->Icons.Outlined.Bedtime;"上午"->Icons.Outlined.WbSunny;"下午"->Icons.Outlined.LightMode;else->Icons.Outlined.DarkMode},null,Modifier.size(18.dp),tint=tint)
                    Text(moment.part,style=MaterialTheme.typography.labelLarge,color=tint)
                    if(largeType){Spacer(Modifier.weight(1f));Text(clock(moment.anchorMs,zone),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Medium,color=tint)}
                }
                if(!largeType)Text(clock(moment.anchorMs,zone),style=MaterialTheme.typography.headlineLarge,
                    fontWeight=FontWeight.Medium,color=tint)
                if(e.photoUris.isNotEmpty()) {
                    PhotoThumbnail(e.photoUris.first(),Modifier.fillMaxWidth().height(photoHeight),crop=true)
                } else {
                    Box(Modifier.fillMaxWidth().height(iconHeight),contentAlignment=Alignment.Center) {
                        if(e.kind=="APP")AppBadge(e.packageName,e.title,Modifier.size(if(compact)52.dp else 72.dp))
                        else IconTile(episodeIcon(e),laneTint(e.lane),if(compact)52 else 72)
                    }
                }
                Text(title,style=MaterialTheme.typography.headlineSmall,maxLines=3,overflow=TextOverflow.Ellipsis)
                Text(caption,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(e.assertion!=Assertion.RECORDED)Text(assertionName(e.assertion),style=MaterialTheme.typography.labelSmall,color=tint)
                Text("轻触查看这一段",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable fun DayStatisticsPage(s:WoUiState,all:()->Unit,steps:()->Unit,onEpisode:(Episode)->Unit) {
    val day=s.day ?: return
    androidx.compose.foundation.lazy.LazyColumn(Modifier.testTag("statistics-list"),verticalArrangement=Arrangement.spacedBy(16.dp),
        contentPadding=PaddingValues(top=12.dp,bottom=24.dp)) {
        item { Muted("${day.dayDate} · 截至 ${clock(day.asOfMs,day.zoneId)}${if(s.demo)" · 虚构演示" else ""}") }
        item { DayStatistics(day,steps,all) }
        item { DayUsageChart(day) }
        if(appPeriods(day).isNotEmpty())item { AppPeriodsCard(day,onEpisode,all) }
        item { TextButton(onClick=all,modifier=Modifier.fillMaxWidth()){Text("查看全部片段") } }
    }
}
