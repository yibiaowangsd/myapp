package com.personal.wo.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.personal.wo.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

@Composable fun TodayDashboard(s:WoUiState,detail:()->Unit,setup:()->Unit,episode:(Episode)->Unit,
    refresh:()->Unit,demo:(Boolean)->Unit,recording:()->Unit,connectSteps:()->Unit=setup) {
    val day=s.day ?: return
    val date=LocalDate.parse(day.dayDate)
    val parts=remember(day){recapParts(day)}
    LazyColumn(Modifier.testTag("today-list"),verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(top=8.dp,bottom=28.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                FlowRow(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text("${date.monthValue}月${date.dayOfMonth}日",style=MaterialTheme.typography.titleMedium)
                    Text("星期${listOf("一","二","三","四","五","六","日")[date.dayOfWeek.value-1]}",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(if(s.demo)"演示数据" else if(s.policy.paused)"已暂停" else if(s.policy.requested)"记录中" else "未开始")
                IconButton(onClick=refresh,enabled=!s.busy){Icon(Icons.Outlined.Refresh,"更新今天")}
            }
        }
        if(s.demo)item {
            Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(start=14.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("虚构示例 · 展示一天的样子",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={demo(false)}){Text("退出演示")}
                }
            }
        }
        item { Section("按时间回看","全部片段",detail) }
        if(parts.isEmpty())item {
            Paper {
                IconTile(Icons.Outlined.AutoAwesome,size=44)
                Text("第一段生活，即将出现",style=MaterialTheme.typography.titleLarge)
                Muted("正常使用手机后，这里会按上午、下午和晚上，自动整理地点、活动与照片。")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick=setup){Text("查看记录权限")}
                    TextButton(onClick={demo(true)}){Text("看看示例")}
                }
            }
        }
        itemsIndexed(parts,key={_,part->"part-${part.label}"}) { index,part ->
            RecapTimelineCard(part,day.zoneId,last=index==parts.lastIndex,onEpisode=episode)
        }
        item {
            Section("一天的数据")
            Muted("截至 ${clock(day.asOfMs,day.zoneId)} · 根据已有记录整理",Modifier.padding(bottom=4.dp))
        }
        item { DayStatistics(day,connectSteps,detail) }
        item { DayUsageChart(day) }
        if(appPeriods(day).isNotEmpty())item { AppPeriodsCard(day,episode,detail) }
        if(!s.demo) {
            val step=s.sources.firstOrNull{it.source==Source.STEPS}
            val stepsKnown=day.metrics.firstOrNull{it.key=="steps"}?.value!=null
            if(!stepsKnown||step?.runtime in setOf("ERROR","WAITING"))item {
                Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.5f)) {
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.DirectionsWalk,null,tint=MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(when { step?.supported==false->"此设备尚未开放手机计步";!sourceReady(step)->"开启手机计步";step?.runtime=="ERROR"->"计步连接未成功";step?.runtime in setOf("STOPPED","PAUSED")->"手机计步尚未启动";else->"计步器已准备，走动后更新" },style=MaterialTheme.typography.labelLarge)
                            Muted(step?.detail?.ifBlank { null } ?: "允许身体活动权限后，手机会自动累计步数。")
                        }
                        if(step?.supported!=false)TextButton(onClick=connectSteps){Text(if(sourceReady(step))"重连" else "允许")}
                    }
                }
            }
        }
        if(!s.demo)item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick=setup){Text("记录权限")}
                Spacer(Modifier.weight(1f))
                TextButton(onClick=recording){Text(if(s.policy.paused)"继续记录" else if(s.policy.requested)"暂停记录" else "开始记录")}
            }
        }
    }
}

@Composable fun DailyRecapHero(day:DayView) {
    val entries=recapEpisodes(day)
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),color=Violet,contentColor=Color.White) {
        Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(20.dp),tint=Color(0xFFE7D7FF))
                Text("今日回顾",style=MaterialTheme.typography.titleMedium)
            }
            Text(dailyRecap(day),style=MaterialTheme.typography.headlineSmall,lineHeight=32.sp)
            val highlights=entries.filter{it.kind !in setOf("APP","NOTICE","STEPS")}.distinctBy{it.kind}.take(4)
            if(highlights.isNotEmpty())Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                highlights.forEach { e ->
                    Surface(shape=RoundedCornerShape(14.dp),color=Color.White.copy(alpha=.14f),contentColor=Color.White) {
                        Icon(episodeIcon(e),null,Modifier.padding(12.dp).size(24.dp))
                    }
                }
            }
            Text("由今天已有记录整理",style=MaterialTheme.typography.labelMedium,color=Color.White.copy(alpha=.8f))
        }
    }
}

@Composable fun DayStatistics(day:DayView,steps:()->Unit,detail:()->Unit) {
    val observed=recapEpisodes(day)
    val stepValue=day.metrics.firstOrNull{it.key=="steps"}?.value
    val photoValue=day.metrics.firstOrNull{it.key=="photos"}?.value
    val locations=observed.filter{it.kind=="STAY"}
    val periods=appPeriods(day)
    val appKnown=day.metrics.firstOrNull{it.key=="appMs"}?.value!=null||periods.isNotEmpty()
    val appValue=if(appKnown)durationText(union(periods.map{Span(it.startMs,it.endMs)}).sumOf{it.duration}) else "—"
    val stepText=stepValue?.let{String.format(Locale.CHINA,"%,d",it)} ?: "—"
    val placeText=if(locations.isEmpty())"—" else locations.size.toString()
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(LocalDensity.current.fontScale>=1.3f) {
            StatCell("今日步数",stepText,"步",Icons.Outlined.DirectionsWalk,laneTint(Lane.ACTIVITY),Modifier.fillMaxWidth(),steps)
            StatCell("停留记录",placeText,"段",Icons.Outlined.Place,laneTint(Lane.PLACE),Modifier.fillMaxWidth(),detail)
            StatCell("照片与视频",photoValue?.toString() ?: "—","项",Icons.Outlined.PhotoLibrary,laneTint(Lane.LIFE),Modifier.fillMaxWidth(),detail)
            StatCell("手机使用",appValue,"",Icons.Outlined.Smartphone,laneTint(Lane.DIGITAL),Modifier.fillMaxWidth(),detail)
        } else {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                StatCell("今日步数",stepText,"步",Icons.Outlined.DirectionsWalk,laneTint(Lane.ACTIVITY),Modifier.weight(1f),steps)
                StatCell("停留记录",placeText,"段",Icons.Outlined.Place,laneTint(Lane.PLACE),Modifier.weight(1f),detail)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                StatCell("照片与视频",photoValue?.toString() ?: "—","项",Icons.Outlined.PhotoLibrary,laneTint(Lane.LIFE),Modifier.weight(1f),detail)
                StatCell("手机使用",appValue,"",Icons.Outlined.Smartphone,laneTint(Lane.DIGITAL),Modifier.weight(1f),detail)
            }
        }
    }
}

@Composable private fun StatCell(label:String,value:String,unit:String,icon:ImageVector,tint:Color,modifier:Modifier,onClick:()->Unit) {
    Surface(modifier.clickable(onClick=onClick),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(icon,null,Modifier.size(20.dp),tint=tint)
                Text(label,style=MaterialTheme.typography.labelLarge)
            }
            Text(value+if(unit.isNotEmpty()&&value!="—")" $unit" else "",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        }
    }
}

fun episodeIcon(e:Episode):ImageVector=when(e.kind) {
    "STAY"->Icons.Outlined.Place;"MOVE"->Icons.Outlined.DirectionsWalk;"STEPS","EXERCISE"->Icons.Outlined.DirectionsRun
    "SLEEP"->Icons.Outlined.Bedtime;"PHOTO"->Icons.Outlined.PhotoCamera;"MEDIA"->Icons.Outlined.MusicNote
    "CALENDAR"->Icons.Outlined.Event;"NOTICE"->Icons.Outlined.NotificationsNone;else->Icons.Outlined.Smartphone
}

@Composable fun RecapTimelineCard(part:RecapPart,zone:String,last:Boolean=false,onEpisode:(Episode)->Unit) {
    val photos=part.episodes.filter{it.photoUris.isNotEmpty()}.distinctBy{it.id}
    val chosen=part.episodes.filter{it.kind !in setOf("APP","STEPS","PHOTO","NOTICE")}.distinctBy{it.title}.take(3)
    val apps=part.episodes.filter{it.kind=="APP"}.distinctBy{it.packageName.ifBlank{it.title}}
    val tint=partTint(part.label)
    val line=MaterialTheme.colorScheme.outlineVariant
    var expanded by rememberSaveable(part.startMs){mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().testTag("recap-${part.label}").drawBehind {
        if(!last)drawLine(line,Offset(18.dp.toPx(),46.dp.toPx()),Offset(18.dp.toPx(),size.height+16.dp.toPx()),1.dp.toPx())
    },horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Column(Modifier.width(36.dp).padding(top=4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            val icon=when(part.label){"凌晨"->Icons.Outlined.Bedtime;"上午"->Icons.Outlined.WbSunny;"下午"->Icons.Outlined.LightMode;else->Icons.Outlined.DarkMode}
            IconTile(icon,tint,36)
        }
        Surface(Modifier.weight(1f),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface,
            border=BorderStroke(1.dp,line.copy(alpha=.35f))) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(part.label,style=MaterialTheme.typography.titleLarge,color=tint)
                    Surface(Modifier.align(Alignment.CenterVertically),shape=CircleShape,color=tint.copy(alpha=.08f)) {
                        Text("${clock(part.startMs,zone)}–${if(part.label=="晚上"&&part.endMs-part.startMs>=6*3_600_000)"24:00" else clock(part.endMs,zone)}",Modifier.padding(horizontal=9.dp,vertical=4.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(recapPartText(part),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(photos.isNotEmpty()) {
                    val visible=if(expanded)photos else photos.take(2)
                    visible.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            pair.forEach { photo ->
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClickLabel="查看 ${clock(photo.startMs,zone)} 的影像"){onEpisode(photo)},verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                    PhotoThumbnail(photo.photoUris.first(),Modifier.fillMaxWidth().aspectRatio(if(photos.size==1)1.5f else 1f),crop=true)
                                    Row(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.PhotoCamera,null,Modifier.size(12.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(clock(photo.startMs,zone),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if(pair.size==1&&photos.size>1)Spacer(Modifier.weight(1f))
                        }
                    }
                    if(photos.size>2)TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth()) {
                        Text(if(expanded)"收起影像" else "展开全部 ${photos.size} 项影像")
                        Icon(if(expanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null,Modifier.size(18.dp))
                    }
                }
                chosen.forEach { e ->
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable{onEpisode(e)}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Icon(episodeIcon(e),null,Modifier.size(18.dp),tint=laneTint(e.lane))
                        Text(e.title.removeSuffix("附近停留"),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                        Text(clock(maxOf(e.startMs,part.startMs),zone),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if(apps.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        apps.forEach { app ->
                            Column(Modifier.width(52.dp).clip(RoundedCornerShape(10.dp)).clickable{onEpisode(app)}.padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                AppBadge(app.packageName,app.title,Modifier.size(32.dp))
                                Text(app.title,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun partTint(label:String):Color=when(label) {
    "上午"->laneTint(Lane.ACTIVITY)
    "下午"->laneTint(Lane.PLACE)
    "晚上"->laneTint(Lane.LIFE)
    else->MaterialTheme.colorScheme.primary
}

private val appIconCache=LruCache<String,Bitmap>(48)
@Composable fun AppBadge(packageName:String,label:String,modifier:Modifier=Modifier.size(36.dp)) {
    val context=LocalContext.current
    val bitmap by produceState<Bitmap?>(appIconCache.get(packageName),packageName) {
        if(packageName.isNotBlank()&&value==null)value=withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96,96).also{appIconCache.put(packageName,it)} }.getOrNull()
        }
    }
    if(bitmap!=null)Image(bitmap!!.asImageBitmap(),"$label 图标",modifier.clip(RoundedCornerShape(9.dp)))
    else Box(modifier.clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center) {
        Icon(Icons.Outlined.Smartphone,"$label 图标暂不可用",Modifier.padding(7.dp).fillMaxSize(),tint=MaterialTheme.colorScheme.primary)
    }
}

@Composable fun AppPeriodsCard(day:DayView,onEpisode:(Episode)->Unit,onAll:()->Unit={}) {
    val periods=remember(day){appPeriods(day)}
    val visible=periods.takeLast(8)
    Paper {
        Text("什么时候，打开了什么",style=MaterialTheme.typography.titleMedium)
        visible.forEach { period ->
            Column(Modifier.fillMaxWidth().clickable{onEpisode(period.episode)}.padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                    AppBadge(period.episode.packageName,period.episode.title)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text(period.episode.title,style=MaterialTheme.typography.titleMedium)
                        Text("${clock(period.startMs,day.zoneId)}–${clock(period.endMs,day.zoneId)}",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(durationText(period.endMs-period.startMs),style=MaterialTheme.typography.labelLarge)
                }
                BoxWithConstraints(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    val total=(day.windowEndMs-day.windowStartMs).toFloat()
                    Box(Modifier.offset(x=maxWidth*((period.startMs-day.windowStartMs)/total)).width((maxWidth*((period.endMs-period.startMs)/total)).coerceAtLeast(1.dp)).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("00:00",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("12:00",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("24:00",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(periods.size>8)TextButton(onClick=onAll,modifier=Modifier.fillMaxWidth()) { Text("查看全部 ${periods.size} 段使用") }
        Muted("色条标出当天的使用时间段；空白时间保留。")
    }
}
