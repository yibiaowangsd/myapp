package com.personal.wo.core

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** Presentation-only; the original episode remains the source of truth for details. */
data class DayMoment(val episode:Episode,val startMs:Long,val endMs:Long,val anchorMs:Long,val part:String)

fun dayMoments(day:DayView):List<DayMoment> {
    val cutoff=minOf(day.asOfMs,day.windowEndMs)
    val zone=ZoneId.of(day.zoneId)
    return recapEpisodes(day).mapNotNull { e ->
        val start=maxOf(e.startMs,day.windowStartMs)
        val end=minOf(e.endMs,cutoff)
        val point=e.startMs==e.endMs
        if(start>=cutoff||end<start||(!point&&end==start))return@mapNotNull null
        // A step interval appears once, at its reporting end; never split its count.
        val anchor=if(e.kind=="STEPS")maxOf(start,end-1) else start
        val part=listOf("凌晨","上午","下午","晚上")[Instant.ofEpochMilli(anchor).atZone(zone).hour/6]
        DayMoment(e,start,end,anchor,part)
    }.sortedWith(compareBy({it.anchorMs},{it.endMs},{it.episode.id}))
}

/** Empty parts of a day stay empty. Scrubbing selects an actual nearby experience. */
fun nearestMoment(moments:List<DayMoment>,timeMs:Long):Int =
    moments.indices.minByOrNull { abs(moments[it].anchorMs-timeMs) } ?: -1
