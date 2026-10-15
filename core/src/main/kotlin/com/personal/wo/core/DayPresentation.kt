package com.personal.wo.core

import java.time.LocalDate
import java.time.ZoneId

data class AppDuration(val name: String, val durationMs: Long)
data class HourUsage(val startMs: Long, val durationMs: Long)

/** Display only observed application time, clipped to the viewed day and its observation cutoff. */
fun observedAppSpans(day: DayView): List<Pair<Episode, Span>> = day.episodes
    .filter { it.kind == "APP" && it.assertion in setOf(Assertion.RECORDED, Assertion.CONFIRMED) && "CLOCK_CONFLICT" !in it.qualityFlags }
    .mapNotNull { e ->
        val start = maxOf(e.startMs, day.windowStartMs)
        val end = minOf(e.endMs, day.windowEndMs, day.asOfMs)
        if (end > start) e to Span(start, end) else null
    }

fun appDurations(day: DayView): List<AppDuration> = observedAppSpans(day)
    .groupBy { (episode, _) -> episode.packageName.ifBlank { episode.title } }
    .values.map { rows -> AppDuration(rows.first().first.title, union(rows.map { it.second }).sumOf { it.duration }) }
    .sortedByDescending { it.durationMs }

fun hourlyUsage(day: DayView): List<HourUsage> {
    val spans = union(observedAppSpans(day).map { it.second })
    return generateSequence(day.windowStartMs) { (it + 3_600_000L).takeIf { time -> time < day.windowEndMs } }
        .takeWhile { it < day.windowEndMs }
        .map { start ->
            val hour = Span(start, minOf(start + 3_600_000L, day.windowEndMs))
            HourUsage(start, spans.sumOf { it.intersect(hour)?.duration ?: 0 })
        }.toList()
}

fun sourceReady(state: SourceState?) = state?.supported == true && state.authorization in setOf("GRANTED", "PARTIAL")

/** Explicit in-memory sample, never passed to the repository. */
fun demoDay(date: LocalDate = LocalDate.now(), zone: String = ZoneId.systemDefault().id): DayView {
    val w = window(date, ZoneId.of(zone))
    fun episode(start: Int, end: Int, lane: Lane, kind: String, title: String, source: Source,
                assertion: Assertion = Assertion.RECORDED) = Episode(
        "demo-$kind-$start", startMs = w.start + start * 60_000L, endMs = w.start + end * 60_000L,
        lane = lane, kind = kind, title = title, assertion = assertion,
        detail = "这是虚构的演示片段，仅用于预览界面。",
        evidence = listOf(Evidence("demo-$start", source, "demo", "演示数据")))
    val episodes = listOf(
        episode(420, 475, Lane.PLACE, "STAY", "家附近停留", Source.LOCATION, Assertion.INFERRED),
        episode(485, 520, Lane.ACTIVITY, "MOVE", "地点间移动", Source.LOCATION, Assertion.INFERRED),
        episode(530, 720, Lane.PLACE, "STAY", "办公室附近停留", Source.LOCATION, Assertion.INFERRED),
        episode(550, 580, Lane.DIGITAL, "APP", "微信", Source.USAGE),
        episode(620, 660, Lane.DIGITAL, "APP", "浏览器", Source.USAGE),
        episode(710, 710, Lane.LIFE, "NOTICE", "收到外卖送达提醒", Source.NOTIFICATION),
        episode(740, 780, Lane.ACTIVITY, "EXERCISE", "午后步行", Source.HEALTH),
        episode(820, 845, Lane.DIGITAL, "APP", "微信", Source.USAGE),
        episode(890, 920, Lane.DIGITAL, "APP", "相册", Source.USAGE),
        episode(1090, 1140, Lane.PLACE, "STAY", "家附近停留", Source.LOCATION, Assertion.INFERRED),
        episode(1200, 1235, Lane.LIFE, "MEDIA", "晚间音乐", Source.MEDIA))
    return DayView(date.toString(), zone, w.start, w.end, w.start + 1290 * 60_000L, "PARTIAL",
        "今天的生活片段", listOf(Sentence("上午在办公室附近停留，午后有一段步行记录，晚上听了35分钟音乐。")),
        emptyList(), listOf(Metric("appMs", 125 * 60_000L, "ms", "已观察应用活动"),
            Metric("mediaMs", 35 * 60_000L, "ms", "媒体播放"), Metric("steps", 4280, "步", "来源步数"),
            Metric("photos", null, "项", "关联媒体")), episodes)
}
