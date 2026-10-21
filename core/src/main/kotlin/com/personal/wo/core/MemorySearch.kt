package com.personal.wo.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/** Dates narrow the archive window; the remaining words search existing record text. */
data class MemorySearch(val text:String,val window:Span?)

fun memorySearch(input:String,today:LocalDate,zone:ZoneId):MemorySearch {
    val query=input.trim().take(200).lowercase(Locale.ROOT)
    val relative=mapOf("今天" to today,"昨天" to today.minusDays(1),"前天" to today.minusDays(2))
    relative.entries.firstOrNull { query==it.key||query.startsWith(it.key+" ") }?.let { (word,date) ->
        return MemorySearch(query.removePrefix(word).trim(),window(date,zone))
    }
    val date=Regex("^(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})日?(?:\\s+|$)").find(query)
    if(date!=null) {
        val day=runCatching { LocalDate.of(date.groupValues[1].toInt(),date.groupValues[2].toInt(),date.groupValues[3].toInt()) }.getOrNull()
        if(day!=null)return MemorySearch(query.removeRange(date.range).trim(),window(day,zone))
    }
    val month=Regex("^(\\d{4})[-/年](\\d{1,2})月?(?:\\s+|$)").find(query)
    if(month!=null) {
        val m=runCatching { YearMonth.of(month.groupValues[1].toInt(),month.groupValues[2].toInt()) }.getOrNull()
        if(m!=null)return MemorySearch(query.removeRange(month.range).trim(),Span(window(m.atDay(1),zone).start,window(m.atEndOfMonth(),zone).end))
    }
    return MemorySearch(query,null)
}

fun memoryLikePattern(value:String)="%"+value.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"
