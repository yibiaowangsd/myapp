package com.personal.wo.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size as AndroidSize
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.personal.wo.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun Overview(d:DayView,onClick:()->Unit){
    val span=(d.windowEndMs-d.windowStartMs).toDouble();val bg=MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.clickable(onClick=onClick).semantics{contentDescription="四轨时间总览，${d.episodes.size}个片段，点击展开。"},verticalArrangement=Arrangement.spacedBy(16.dp)){
        Lane.entries.forEach{lane->Row(verticalAlignment=Alignment.CenterVertically){Text(laneName(lane),Modifier.width(42.dp),fontSize=13.sp);Canvas(Modifier.weight(1f).height(25.dp)){
            drawRoundRect(bg,cornerRadius=CornerRadius(4.dp.toPx()),size=size)
            d.episodes.filter{it.lane==lane}.forEach{e->val x=((e.startMs-d.windowStartMs)/span*size.width).toFloat().coerceIn(0f,size.width);val width=((e.endMs-e.startMs)/span*size.width).toFloat().coerceAtLeast(3.dp.toPx());val c=laneColors[lane.ordinal]
                when{e.startMs==e.endMs->drawCircle(c,3.dp.toPx(),Offset(x,size.height/2));e.assertion==Assertion.PLANNED->drawRect(c,Offset(x,4.dp.toPx()),Size(width,size.height-8.dp.toPx()),style=Stroke(1.dp.toPx()));e.assertion==Assertion.INFERRED->drawLine(c,Offset(x,size.height/2),Offset((x+width).coerceAtMost(size.width),size.height/2),6.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(),3.dp.toPx())));else->drawRect(c.copy(alpha=if(e.assertion==Assertion.UNKNOWN).3f else .85f),Offset(x,4.dp.toPx()),Size(width,size.height-8.dp.toPx()))}
            };val marker=((d.asOfMs-d.windowStartMs)/span*size.width).toFloat();drawLine(laneColors[0].copy(alpha=.5f),Offset(marker,0f),Offset(marker,size.height),1.dp.toPx())
        }}}
        Row(Modifier.fillMaxWidth().padding(start=42.dp),horizontalArrangement=Arrangement.SpaceBetween){(0..4).forEach{index->Muted(clock(d.windowStartMs+((d.windowEndMs-d.windowStartMs)*index/4),d.zoneId))}}
        Muted("实线记录 · 虚线推测 · 空心计划")
    }
}
@Composable fun DayPage(s:WoUiState,vm:WoViewModel,episode:(Episode)->Unit){
    val d=s.day ?: return;var list by rememberSaveable{mutableStateOf(false)};var scale by rememberSaveable{mutableFloatStateOf(.8f)};var play by remember{mutableStateOf(false)};var progress by rememberSaveable(d.dayDate){mutableFloatStateOf(0f)};var speed by rememberSaveable{mutableIntStateOf(1)}
    LaunchedEffect(play,d.dayDate,speed){if(play){while(progress<1&&play){delay(50);progress=(progress+speed/900f).coerceAtMost(1f)};play=false}}
    DisposableEffect(d.dayDate){onDispose{play=false}}
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner){val observer=androidx.lifecycle.LifecycleEventObserver { _,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP)play=false };owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}}
    val hours=remember(d.dayDate,d.zoneId){generateSequence(d.windowStartMs){t->(t+3_600_000L).takeIf{it<d.windowEndMs}}.toList()}
    LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
        item{Text(d.dayDate,style=MaterialTheme.typography.headlineLarge);Muted("${d.zoneId} · ${d.episodes.size}个片段");Row{TextButton(onClick={vm.date(LocalDate.parse(d.dayDate).minusDays(1))}){Text("前一天")};TextButton(onClick={vm.date(LocalDate.parse(d.dayDate).plusDays(1))}){Text("后一天")};TextButton(onClick={list=!list}){Text(if(list)"多轨时间轴" else "片段列表")}}}
        if(d.warnings.isNotEmpty())item{Paper{d.warnings.forEach{Muted(it)}}}
        item{Paper{Row(verticalAlignment=Alignment.CenterVertically){Text("一天回放",Modifier.weight(1f));TextButton(onClick={if(progress>=1)progress=0f;play=!play}){Text(if(play)"暂停" else "播放")};TextButton(onClick={speed=if(speed==1)2 else if(speed==2)4 else 1}){Text("${speed}倍")}};Slider(progress,{progress=it;play=false});Muted("游标 ${clock(d.windowStartMs+((d.windowEndMs-d.windowStartMs)*progress).toLong(),d.zoneId)} · 不播放音视频")}}
        if(d.episodes.any{it.lat!=null}||s.positions.isNotEmpty())item{Section("足迹示意");Footprints(d,s.positions,episode)}
        if(list)items(d.episodes,key={it.id}){e->EpisodeCard(e,d.zoneId){episode(e)}}else{
            item{Row(verticalAlignment=Alignment.CenterVertically){Muted("时间轴缩放",Modifier.width(84.dp));Slider(scale,{scale=it},valueRange=.4f..2f,modifier=Modifier.weight(1f))};Row(Modifier.fillMaxWidth().padding(start=48.dp)){Lane.entries.forEach{Text(laneName(it),Modifier.weight(1f),fontSize=13.sp,color=laneColors[it.ordinal])}}}
            items(hours,key={"${d.dayDate}-${d.zoneId}-$it"}){h->HourBucket(d,h,scale,progress,episode)}
        }
        if(d.episodes.isEmpty())item{Empty("这个日期还没有记录","选择其他日期，或在今天完成授权后开始记录。")}
        val apps=d.episodes.filter{it.kind=="APP"&&it.assertion!=Assertion.UNKNOWN}.groupBy{it.title}.entries.sortedByDescending{it.value.sumOf{e->e.endMs-e.startMs}}.take(8)
        if(apps.isNotEmpty())item{Section("应用活动明细");Paper{apps.forEach{(name,es)->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(name,Modifier.weight(1f));Text(durationText(union(es.map{Span(it.startMs,it.endMs)}).sumOf{it.duration}))}};Muted("分屏应用可重叠；活动总量取并集。")}}
    }
}
@Composable fun HourBucket(d:DayView,h:Long,scale:Float,progress:Float,onClick:(Episode)->Unit){
    val end=minOf(h+3_600_000,d.windowEndMs);val es=d.episodes.filter{if(it.endMs==it.startMs)it.startMs in h until end else it.startMs<end&&it.endMs>h};val bg=MaterialTheme.colorScheme.surfaceVariant
    Row(Modifier.fillMaxWidth()){
        val label=Instant.ofEpochMilli(h).atZone(ZoneId.of(d.zoneId)).format(DateTimeFormatter.ofPattern(if(d.windowEndMs-d.windowStartMs==86_400_000L)"HH:mm" else "HH:mm\nXXX"));Muted(label,Modifier.width(48.dp))
        Canvas(Modifier.weight(1f).height((60*scale).dp).semantics{contentDescription="$label，${es.joinToString{it.title+"，"+assertionName(it.assertion)}}"}.pointerInput(h,es){detectTapGestures{p->val lane=(p.x/size.width*4).toInt().coerceIn(0,3);val time=h+(p.y/size.height*(end-h)).toLong();es.filter{it.lane.ordinal==lane}.minByOrNull{if(time in it.startMs..it.endMs)0 else kotlin.math.abs(it.startMs-time)}?.let(onClick)}}){
            val cw=size.width/4;val span=(end-h).toDouble();for(l in 0..3)drawRect(bg.copy(alpha=.45f),Offset(l*cw+2,0f),Size(cw-4,size.height))
            es.forEach{e->val y=((maxOf(e.startMs,h)-h)/span*size.height).toFloat();val bottom=((minOf(e.endMs,end)-h)/span*size.height).toFloat();val x=e.lane.ordinal*cw+5;val c=laneColors[e.lane.ordinal]
                if(e.startMs==e.endMs)drawCircle(c,3.dp.toPx(),Offset(x+cw/2-5,y))else if(e.assertion==Assertion.INFERRED)drawLine(c,Offset(x+cw/2,y),Offset(x+cw/2,bottom),6.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(),3.dp.toPx())))else drawRect(c.copy(alpha=if(e.assertion==Assertion.UNKNOWN).2f else .7f),Offset(x,y),Size(cw-10,(bottom-y).coerceAtLeast(2f)),style=if(e.assertion==Assertion.PLANNED)Stroke(1.dp.toPx())else androidx.compose.ui.graphics.drawscope.Fill)
            };val t=d.windowStartMs+((d.windowEndMs-d.windowStartMs)*progress).toLong();if(t in h until end)drawLine(laneColors[0],Offset(0f,((t-h)/span*size.height).toFloat()),Offset(size.width,((t-h)/span*size.height).toFloat()),2.dp.toPx())
        }
    }
}
object ThumbnailCache{val cache=object:LruCache<String,Bitmap>(24*1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount};val gate=Semaphore(1)}
@Composable fun PhotoThumbnail(uri:String){val c=LocalContext.current;val bitmap by produceState<Bitmap?>(null,uri){value=ThumbnailCache.cache.get(uri)?:withContext(Dispatchers.IO){ThumbnailCache.gate.withPermit{runCatching{c.contentResolver.loadThumbnail(Uri.parse(uri),AndroidSize(512,512),null).also{ThumbnailCache.cache.put(uri,it)}}.getOrNull()}}};if(bitmap!=null)Image(bitmap!!.asImageBitmap(),"关联媒体缩略图",Modifier.fillMaxWidth().heightIn(max=260.dp).clip(RoundedCornerShape(14.dp)),contentScale=ContentScale.Fit)else Text("正在加载或原文件不可访问",Modifier.padding(18.dp))}
@Composable fun Footprints(d:DayView,positions:List<Observation>,open:(Episode)->Unit){
    val stays=d.episodes.filter{it.lat!=null&&it.lon!=null};val valid=positions.filter{(it.payload.accuracyM?:9999.0)<=200&&!it.payload.mock&&it.payload.lat!=null&&it.payload.lon!=null};val anchorLat=stays.firstOrNull()?.lat?:valid.firstOrNull()?.payload?.lat?:return;val anchorLon=stays.firstOrNull()?.lon?:valid.firstOrNull()?.payload?.lon?:return
    val points=stays.map{project(it.lat!!,it.lon!!,anchorLat,anchorLon)}+valid.map{project(it.payload.lat!!,it.payload.lon!!,anchorLat,anchorLon)};val minX=points.minOf{it.x};val maxX=points.maxOf{it.x};val minY=points.minOf{it.y};val maxY=points.maxOf{it.y};val span=maxOf(maxX-minX,maxY-minY,.001)
    var zoom by remember(d.dayDate){mutableFloatStateOf(1f)};var pan by remember(d.dayDate){mutableStateOf(Offset.Zero)};val color=MaterialTheme.colorScheme.primary;val bg=MaterialTheme.colorScheme.surfaceVariant
    Column{Canvas(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(18.dp)).background(bg).semantics{contentDescription="足迹示意，${stays.size}个停留地点，无道路底图。"}.pointerInput(d.dayDate){detectTransformGestures{_,move,scale,_->zoom=(zoom*scale).coerceIn(.5f,8f);pan+=move}}.pointerInput(d.episodes,zoom,pan){detectTapGestures(onLongPress={p->stays.minByOrNull{e->val point=project(e.lat!!,e.lon!!,anchorLat,anchorLon);val x=((point.x-(minX+maxX)/2)/span*size.width*.8*zoom+size.width/2+pan.x).toFloat();val y=((point.y-(minY+maxY)/2)/span*size.width*.8*zoom+size.height/2+pan.y).toFloat();(Offset(x,y)-p).getDistance()}?.let(open)})}){
        fun at(lat:Double,lon:Double):Offset{val p=project(lat,lon,anchorLat,anchorLon);return Offset(((p.x-(minX+maxX)/2)/span*size.width*.8*zoom+size.width/2+pan.x).toFloat(),((p.y-(minY+maxY)/2)/span*size.width*.8*zoom+size.height/2+pan.y).toFloat())}
        valid.sortedBy{it.startMs}.zipWithNext().filter{(a,b)->b.startMs-a.startMs<=5*60_000L}.forEach{(a,b)->drawLine(color.copy(alpha=.4f),at(a.payload.lat!!,a.payload.lon!!),at(b.payload.lat!!,b.payload.lon!!),2.dp.toPx())};valid.forEach{drawCircle(color.copy(alpha=.3f),2.dp.toPx(),at(it.payload.lat!!,it.payload.lon!!))};stays.forEach{e->val p=at(e.lat!!,e.lon!!);drawCircle(color.copy(alpha=.15f),13.dp.toPx(),p);drawCircle(color,5.dp.toPx(),p)}
    };Muted("足迹示意 · 无底图 · 双指缩放 · 长按地点查看与命名",Modifier.padding(top=8.dp));stays.take(6).forEach{Muted("${it.title} · ${durationText(it.endMs-it.startMs)}",Modifier.padding(top=6.dp))}}
}
