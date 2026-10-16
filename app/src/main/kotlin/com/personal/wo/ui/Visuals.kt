package com.personal.wo.ui

import android.graphics.Bitmap
import android.content.ComponentCallbacks2
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

object ThumbnailCache {
    val cache=object:LruCache<String,Bitmap>(24*1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount}
    val gate=Semaphore(1)
    fun trim(level:Int) {
        when {
            level==ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> Unit
            level>=ComponentCallbacks2.TRIM_MEMORY_COMPLETE||level==ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> cache.evictAll()
            level>=ComponentCallbacks2.TRIM_MEMORY_BACKGROUND||level==ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW||level==ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> cache.trimToSize(cache.maxSize()/2)
        }
    }
}
@Composable fun PhotoThumbnail(uri:String,modifier:Modifier=Modifier.fillMaxWidth().heightIn(min=100.dp,max=260.dp),crop:Boolean=false) {
    val context=LocalContext.current
    var finished by remember(uri){mutableStateOf(false)}
    val bitmap by produceState<Bitmap?>(ThumbnailCache.cache.get(uri),uri) {
        value=value?:withContext(Dispatchers.IO){ThumbnailCache.gate.withPermit{
            ThumbnailCache.cache.get(uri) ?: runCatching{context.contentResolver.loadThumbnail(Uri.parse(uri),AndroidSize(512,512),null).also{ThumbnailCache.cache.put(uri,it)}}.getOrNull()
        }}
        finished=true
    }
    if(bitmap!=null)Image(bitmap!!.asImageBitmap(),"关联媒体缩略图",modifier.clip(RoundedCornerShape(14.dp)),contentScale=if(crop)ContentScale.Crop else ContentScale.Fit)
    else Surface(modifier.clip(RoundedCornerShape(14.dp)),color=MaterialTheme.colorScheme.surfaceVariant) {
        Box(Modifier.padding(12.dp),contentAlignment=Alignment.Center){Text(if(finished)"照片暂不可访问" else "照片加载中",style=MaterialTheme.typography.labelSmall)}
    }
}
@Composable fun Footprints(d:DayView,positions:List<Observation>,open:(Episode)->Unit){
    val stays=d.episodes.filter{it.lat!=null&&it.lon!=null};val valid=positions.filter{(it.payload.accuracyM?:9999.0)<=200&&!it.payload.mock&&it.payload.lat!=null&&it.payload.lon!=null};val anchorLat=stays.firstOrNull()?.lat?:valid.firstOrNull()?.payload?.lat?:return;val anchorLon=stays.firstOrNull()?.lon?:valid.firstOrNull()?.payload?.lon?:return
    val points=stays.map{project(it.lat!!,it.lon!!,anchorLat,anchorLon)}+valid.map{project(it.payload.lat!!,it.payload.lon!!,anchorLat,anchorLon)};val minX=points.minOf{it.x};val maxX=points.maxOf{it.x};val minY=points.minOf{it.y};val maxY=points.maxOf{it.y};val span=maxOf(maxX-minX,maxY-minY,.001)
    var zoom by remember(d.dayDate){mutableFloatStateOf(1f)};var pan by remember(d.dayDate){mutableStateOf(Offset.Zero)};val color=MaterialTheme.colorScheme.primary;val bg=MaterialTheme.colorScheme.surfaceVariant
    Column{Canvas(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(18.dp)).background(bg).semantics{contentDescription="足迹示意，${stays.size}个停留地点，无道路底图。"}.pointerInput(d.dayDate){detectTransformGestures{_,move,scale,_->zoom=(zoom*scale).coerceIn(.5f,8f);pan+=move}}.pointerInput(d.episodes,zoom,pan){detectTapGestures(onLongPress={p->stays.minByOrNull{e->val point=project(e.lat!!,e.lon!!,anchorLat,anchorLon);val x=((point.x-(minX+maxX)/2)/span*size.width*.8*zoom+size.width/2+pan.x).toFloat();val y=((point.y-(minY+maxY)/2)/span*size.width*.8*zoom+size.height/2+pan.y).toFloat();(Offset(x,y)-p).getDistance()}?.let(open)})}){
        fun at(lat:Double,lon:Double):Offset{val p=project(lat,lon,anchorLat,anchorLon);return Offset(((p.x-(minX+maxX)/2)/span*size.width*.8*zoom+size.width/2+pan.x).toFloat(),((p.y-(minY+maxY)/2)/span*size.width*.8*zoom+size.height/2+pan.y).toFloat())}
        valid.sortedBy{it.startMs}.zipWithNext().filter{(a,b)->b.startMs-a.startMs<=5*60_000L}.forEach{(a,b)->drawLine(color.copy(alpha=.4f),at(a.payload.lat!!,a.payload.lon!!),at(b.payload.lat!!,b.payload.lon!!),2.dp.toPx())};valid.forEach{drawCircle(color.copy(alpha=.3f),2.dp.toPx(),at(it.payload.lat!!,it.payload.lon!!))};stays.forEach{e->val p=at(e.lat!!,e.lon!!);drawCircle(color.copy(alpha=.15f),13.dp.toPx(),p);drawCircle(color,5.dp.toPx(),p)}
    };Muted("足迹示意 · 无底图 · 双指缩放 · 长按地点查看与命名",Modifier.padding(top=8.dp));stays.take(6).forEach{Muted("${it.title} · ${durationText(it.endMs-it.startMs)}",Modifier.padding(top=6.dp))}}
}
