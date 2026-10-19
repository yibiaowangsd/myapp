package com.personal.wo.data

import com.personal.wo.core.DayView

/** Small process-local cache. Callers serialize access with the repository mutex. */
internal class RecentDayCache(private val capacity:Int=3) {
    private data class Entry(val day:DayView,val version:Long,val generation:Long,val storedAt:Long)
    private val entries=LinkedHashMap<String,Entry>(capacity,0.75f,true)
    fun get(key:String,version:Long,generation:Long,now:Long,today:Boolean):DayView? {
        val entry=entries[key] ?: return null
        if(entry.version!=version||entry.generation!=generation||now<entry.storedAt)return null
        if(today&&now-entry.storedAt>=30_000)return null
        if(entry.storedAt<entry.day.windowEndMs&&now>=entry.day.windowEndMs)return null
        return entry.day
    }
    fun put(key:String,day:DayView,version:Long,generation:Long,now:Long) {
        entries[key]=Entry(day,version,generation,now)
        while(entries.size>capacity)entries.remove(entries.keys.first())
    }
    fun clear()=entries.clear()
    fun invalidate(start:Long,end:Long) {
        entries.entries.removeAll { (_,entry) -> entry.day.windowStartMs<end&&entry.day.windowEndMs>start }
    }
}
