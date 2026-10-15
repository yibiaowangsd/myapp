package com.personal.wo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.personal.wo.core.*
import java.time.Instant
import java.time.ZoneId

/** Uses observed interval unions, including 23/25-hour days. No synthetic activity. */
@Composable fun DayUsageChart(day:DayView) {
    val hours=remember(day){hourlyUsage(day)}
    val known=day.metrics.any{it.key=="appMs"&&it.value!=null}||hours.any{it.durationMs>0}
    val observed=hours.indices.filter{hours[it].startMs<day.asOfMs}
    var selected by rememberSaveable(day.dayDate,day.zoneId) {
        mutableIntStateOf(observed.maxByOrNull{hours[it].durationMs} ?: 0)
    }
    val index=selected.coerceIn(0,observed.lastOrNull() ?: 0)
    val colors=listOf(partTint("凌晨"),partTint("上午"),partTint("下午"),partTint("晚上"))
    val grid=MaterialTheme.colorScheme.outlineVariant
    val inactive=MaterialTheme.colorScheme.surfaceVariant
    val highlight=MaterialTheme.colorScheme.primary
    val zone=remember(day.zoneId){ZoneId.of(day.zoneId)}
    fun hourText(i:Int):String {
        val h=hours[i]
        val end=minOf(h.startMs+3_600_000L,day.windowEndMs,day.asOfMs)
        val endText=if(end==day.windowEndMs)"24:00" else clock(end,day.zoneId)
        return "${clock(h.startMs,day.zoneId)}–$endText · ${durationText(h.durationMs)}"
    }
    Paper(Modifier.testTag("day-usage-chart")) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.BarChart,null,Modifier.size(20.dp),tint=highlight)
            Text("手机使用节奏",style=MaterialTheme.typography.titleMedium)
        }
        Text(if(known)durationText(hours.sumOf{it.durationMs}) else "—",style=MaterialTheme.typography.headlineMedium)
        if(!known)Muted("还没有应用使用数据，开启使用情况访问后自动整理。")
        else if(hours.isNotEmpty()&&observed.isNotEmpty()) {
            Text("每小时 · 单位：分钟",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Column(Modifier.height(128.dp).width(28.dp),verticalArrangement=Arrangement.SpaceBetween) {
                    Text("60",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("30",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("0",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(128.dp).testTag("hour-chart").semantics {
                        contentDescription="每小时已记录的手机使用。${hourText(index)}。点击柱子查看时段。"
                        customActions=listOf(
                            CustomAccessibilityAction("前一小时") { selected=(index-1).coerceAtLeast(0);true },
                            CustomAccessibilityAction("后一小时") { selected=(index+1).coerceAtMost(observed.last());true }
                        )
                    }.pointerInput(day.dayDate,day.asOfMs,hours) {
                        detectTapGestures { point ->
                            val hit=(point.x/size.width*hours.size).toInt().coerceIn(hours.indices)
                            if(hit in observed)selected=hit
                        }
                    }) {
                        val chartHeight=size.height-4.dp.toPx()
                        val slot=size.width/hours.size
                        listOf(0f,.5f,1f).forEach { fraction ->
                            val y=2.dp.toPx()+chartHeight*fraction
                            drawLine(grid.copy(alpha=.65f),Offset(0f,y),Offset(size.width,y),1.dp.toPx())
                        }
                        hours.forEachIndexed { i,hour ->
                            val x=i*slot+slot*.16f
                            val width=slot*.68f
                            val base=2.dp.toPx()+chartHeight
                            if(i==index)drawRoundRect(highlight.copy(alpha=.08f),Offset(i*slot,0f),Size(slot,size.height),CornerRadius(3.dp.toPx()))
                            if(i in observed) {
                                val height=chartHeight*(hour.durationMs/3_600_000f).coerceIn(0f,1f)
                                val tint=colors[Instant.ofEpochMilli(hour.startMs).atZone(zone).hour/6]
                                if(height>0f)drawRoundRect(tint,Offset(x,base-height),Size(width,height),CornerRadius(2.dp.toPx()))
                                else drawLine(grid,Offset(x,base),Offset(x+width,base),2.dp.toPx())
                            } else drawLine(inactive,Offset(x,base),Offset(x+width,base),2.dp.toPx())
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        listOf(0,hours.size/4,hours.size/2,hours.size*3/4).forEach { i ->
                            Text(clock(hours[i].startMs,day.zoneId).take(2),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("24",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={selected=index-1},enabled=index>0){Icon(Icons.Outlined.ChevronLeft,"前一小时")}
                Text(if(LocalDensity.current.fontScale>=1.3f)hourText(index).replace(" · ","\n") else hourText(index),Modifier.weight(1f).testTag("hour-selection"),style=MaterialTheme.typography.labelLarge)
                IconButton(onClick={selected=index+1},enabled=index<observed.last()){Icon(Icons.Outlined.ChevronRight,"后一小时")}
            }
            Muted("点击柱子查看时段。空白处没有已记录的用量。")
        }
    }
}
