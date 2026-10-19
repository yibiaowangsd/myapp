package com.personal.wo.ui

import com.personal.wo.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class HomeProjection(val day:DayView,val minimum:Int,val chapters:List<DayChapter>) {
    fun matches(value:DayView,threshold:Int)=day.dayDate==value.dayDate&&day.zoneId==value.zoneId&&
        day.revision==value.revision&&day.asOfMs==value.asOfMs&&day.episodes==value.episodes&&minimum==threshold
}

/** CPU work is bounded to three day projections and never runs in composition. */
internal class HomeProjectionCache {
    private val mutex=Mutex()
    private val entries=LinkedHashMap<String,HomeProjection>(3,.75f,true)
    suspend fun project(day:DayView,minimum:Int):HomeProjection=withContext(Dispatchers.Default) {
        mutex.withLock {
            val key="${day.zoneId}:${day.dayDate}:$minimum"
            entries[key]?.takeIf { it.day==day } ?: HomeProjection(day,minimum,dayChapters(day,minimum)).also {
                entries[key]=it
                while(entries.size>3)entries.remove(entries.keys.first())
            }
        }
    }
}
