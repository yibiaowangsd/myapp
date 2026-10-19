package com.personal.wo.core

/** Advance presentation time without inventing observations or extending open app sessions. */
fun advanceDayClock(day:DayView,policy:Policy,now:Long):DayView? {
    val until=minOf(now,day.windowEndMs).coerceAtLeast(day.windowStartMs)
    if(until<day.asOfMs)return null
    if(until==day.asOfMs)return day
    // A newly active exclusion needs the engine's full evidence filtering.
    if(policy.exclusions.any { it.startMs>day.asOfMs&&it.startMs<=until })return null
    val warnings=day.warnings.toMutableList()
    fun warn(text:String) { if(text !in warnings)warnings+=text }
    if(day.coverage.any { !it.available&&it.startMs<until&&it.endMs>day.windowStartMs })
        warn("存在来源缺失区间；空白不代表没有活动。")
    if(day.episodes.any { it.kind=="APP"&&it.assertion!=Assertion.UNKNOWN&&"CLOCK_CONFLICT" !in it.qualityFlags }) {
        val covered=union(day.coverage.filter { it.source==Source.USAGE&&it.available }
            .mapNotNull { Span(it.startMs,it.endMs).intersect(Span(day.windowStartMs,until)) }).sumOf { it.duration }
        if(covered<until-day.windowStartMs)warn("数字轨道显示已观察活动，可用查询范围未覆盖整个归档窗口。")
    }
    val episodes=day.episodes.map { e ->
        if(e.kind=="STEPS"&&e.endMs==day.asOfMs&&e.evidence.any { it.source==Source.HEALTH })e.copy(endMs=until) else e
    }
    val status=when {
        policy.paused&&now<day.windowEndMs->"PAUSED"
        episodes.isEmpty()->"EMPTY"
        warnings.isNotEmpty()->"PARTIAL"
        now<day.windowEndMs->"IN_PROGRESS"
        else->"READY"
    }
    return day.copy(asOfMs=until,episodes=episodes,warnings=warnings,status=status)
}
