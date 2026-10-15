package com.personal.wo.core

import java.time.LocalDate
import java.time.ZoneId

data class AppPeriod(val episode: Episode, val startMs: Long, val endMs: Long)
data class RecapPart(val label: String, val startMs: Long, val endMs: Long, val episodes: List<Episode>)

/** Keep the real intervals. A gap, even in the same app, is never painted as usage. */
fun appPeriods(day: DayView): List<AppPeriod> = observedAppSpans(day)
    .map { (e, span) -> AppPeriod(e, span.start, span.end) }
    .sortedWith(compareBy({ it.startMs }, { it.endMs }, { it.episode.id }))

fun recapEpisodes(day: DayView): List<Episode> = day.episodes.filter {
    it.assertion !in setOf(Assertion.UNKNOWN, Assertion.PLANNED) && "CLOCK_CONFLICT" !in it.qualityFlags &&
        it.startMs < minOf(day.asOfMs, day.windowEndMs) && it.endMs >= day.windowStartMs
}

/** Local clock boundaries also work on 23/25 hour days. Steps belong to the reported
 * interval's ending part; never divide counts proportionally across parts. */
fun recapParts(day: DayView): List<RecapPart> {
    val date = LocalDate.parse(day.dayDate)
    val zone = ZoneId.of(day.zoneId)
    val entries = recapEpisodes(day)
    return listOf("凌晨", "上午", "下午", "晚上").mapIndexedNotNull { index, label ->
        val start = date.atTime(index * 6, 0).atZone(zone).toInstant().toEpochMilli()
        val end = if(index == 3) day.windowEndMs else date.atTime((index + 1) * 6, 0).atZone(zone).toInstant().toEpochMilli()
        val selected = entries.filter { e ->
            if(e.kind == "STEPS") (maxOf(e.startMs, e.endMs - 1)) in start until end
            else if(e.startMs == e.endMs) e.startMs in start until end
            else e.startMs < end && e.endMs > start
        }.sortedBy { it.startMs }
        if(selected.isEmpty()) null else RecapPart(label, start, minOf(end, day.asOfMs), selected)
    }
}

fun recapPartText(part: RecapPart): String {
    val entries = part.episodes
    val details = buildList {
        val stays = entries.filter { it.kind == "STAY" }.distinctBy { it.title }.take(2)
        if(stays.isNotEmpty()) add(stays.joinToString("、") { it.title.removeSuffix("附近停留") } + "附近有停留" + if(stays.any { it.assertion == Assertion.INFERRED }) "（推测）" else "")
        val exercise = entries.filter { it.kind in setOf("EXERCISE", "SLEEP", "MOVE") }.firstOrNull()
        if(exercise != null) add(exercise.title + if(exercise.assertion == Assertion.INFERRED) "（推测）" else "")
        val steps = entries.filter { it.kind == "STEPS" }.sumOf { it.amount ?: 0 }
        if(steps > 0) add("记录到${steps}步")
        val photos = entries.filter { it.kind == "PHOTO" }.distinctBy { it.id }
        if(photos.isNotEmpty()) add("留下${photos.size}项影像")
        val music = entries.filter { it.kind == "MEDIA" }
        if(music.isNotEmpty()) {
            val spans = music.mapNotNull { Span(it.startMs,it.endMs).intersect(Span(part.startMs, part.endMs)) }
            add("媒体播放${durationText(com.personal.wo.core.union(spans).sumOf { it.duration })}")
        }
        val apps = entries.filter { it.kind == "APP" }.map { it.title }.distinct()
        if(isEmpty() && apps.isNotEmpty()) add("使用了" + apps.take(3).joinToString("、") + if(apps.size > 3) "等${apps.size}个应用" else "")
        if(isEmpty() && entries.isNotEmpty()) add(entries.first().title)
    }
    return details.take(3).joinToString("，").let { if(it.isEmpty()) "" else "$it。" }
}

/** A factual, compact day-level recap. Missing measurements remain missing. */
fun dailyRecap(day: DayView): String {
    val entries = recapEpisodes(day)
    val parts = recapParts(day)
    val fragments = buildList {
        val places = entries.filter { it.kind == "STAY" }
        if(places.isNotEmpty()) add("留下${places.size}段停留记录" + if(places.any { it.assertion == Assertion.INFERRED }) "（推测）" else "")
        day.metrics.firstOrNull { it.key == "steps" }?.value?.let { add("记录到${it}步") }
        day.metrics.firstOrNull { it.key == "photos" }?.value?.takeIf { it > 0 }?.let { add("留下${it}项照片与视频") }
        val exercise = entries.firstOrNull { it.kind == "EXERCISE" }
        if(exercise != null && size < 3) add(exercise.title)
        if(isEmpty()) {
            val apps = appPeriods(day)
            if(apps.isNotEmpty()) add("记录到${apps.size}段应用使用，共${durationText(com.personal.wo.core.union(apps.map { Span(it.startMs,it.endMs) }).sumOf { it.duration })}")
            else if(parts.isNotEmpty()) add(recapPartText(parts.first()).removeSuffix("。"))
        }
    }
    return if(fragments.isEmpty()) "今天的总结会随着记录逐渐丰富。" else fragments.take(3).joinToString("，") + "。"
}
