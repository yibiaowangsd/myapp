package com.personal.wo.core

import kotlin.math.abs

val homeAppThresholds = listOf(5, 10, 20, 30)
fun checkedHomeAppMinutes(value: Int) = value.takeIf { it in homeAppThresholds } ?: 10

data class ChapterApp(val episode: Episode, val durationMs: Long, val spans: List<Span>)
data class ChapterPhoto(val episode: Episode, val uri: String)
data class DayChapter(
    val id: String,
    val kind: String,
    val part: RecapPart,
    val apps: List<ChapterApp>,
    val photos: List<ChapterPhoto>,
    val summary: String,
    val shortAppCount: Int,
    val durationMs: Long
)

private const val MINUTE = 60_000L
private data class TimedRecord(val episode: Episode, val start: Long, val end: Long) {
    val duration get() = end - start
}
private class EventGroup(val kind: String, val seeds: MutableList<TimedRecord>) {
    val start get() = seeds.minOf { it.start }
    val end get() = seeds.maxOf { it.end }
    val related = mutableListOf<TimedRecord>()
    fun contains(r: TimedRecord) = r.start >= start && r.end <= end &&
        (r.start < end || start == end)
}
private fun priority(kind: String) = when(kind) {
    "EXERCISE" -> 0; "MOVE" -> 1; "STAY" -> 2; "SLEEP" -> 3; "PHOTO" -> 4; else -> 5
}

/** Event-centred, read-only projection. No clock buckets and no synthetic activity.
 * Primary evidence sets the actual bounds. Related evidence never stretches them.
 * Records have one owner; unassigned small fragments remain in the full-day detail.
 * A bounded group prevents a chain of adjacent app sessions/photos swallowing a day. */
fun dayChapters(day: DayView, minimumAppMinutes: Int = 10): List<DayChapter> {
    val cutoff = minOf(day.asOfMs, day.windowEndMs)
    val records = recapEpisodes(day).distinctBy { it.id }.mapNotNull { e ->
        val start = maxOf(day.windowStartMs, e.startMs)
        val end = minOf(cutoff, e.endMs)
        if(start >= cutoff || end < start || (e.endMs > e.startMs && end == start)) null
        else TimedRecord(e, start, end)
    }.sortedWith(compareBy({ it.start }, { it.end }, { it.episode.id }))
    val qualifyingApps = observedAppSpans(day).filter { it.second.duration >= checkedHomeAppMinutes(minimumAppMinutes) * MINUTE }
        .map { it.first.id }.toSet()
    val groups = mutableListOf<EventGroup>()
    val assigned = mutableSetOf<String>()
    fun seed(kind: String, rows: List<TimedRecord>, gap: Long, limit: Long, same: (TimedRecord, TimedRecord) -> Boolean) {
        var current: EventGroup? = null
        rows.forEach { row ->
            val previous = current
            if(previous != null && row.start <= previous.end + gap &&
                maxOf(row.end, previous.end) - previous.start <= limit && same(previous.seeds.first(), row)) {
                previous.seeds += row
            } else {
                current = EventGroup(kind, mutableListOf(row)); groups += current!!
            }
            assigned += row.episode.id
        }
    }
    for(kind in listOf("EXERCISE", "MOVE", "STAY", "SLEEP")) {
        val minimum = when(kind) { "STAY", "SLEEP" -> 20; "MOVE" -> 10; else -> 5 } * MINUTE
        seed(kind, records.filter { it.episode.kind == kind && it.duration >= minimum }, 5 * MINUTE, 6 * 60 * MINUTE) { a, b ->
            a.episode.title == b.episode.title && a.episode.assertion == b.episode.assertion
        }
    }
    fun attach(row: TimedRecord): Boolean {
        val owner = groups.filter { it.contains(row) }.minWithOrNull(
            compareBy<EventGroup>({ priority(it.kind) }, { it.end - it.start }, { it.start }, { it.seeds.first().episode.id })) ?: return false
        owner.related += row; assigned += row.episode.id
        return true
    }
    // Long sessions fully within a real-world event enrich that event. Crossing
    // sessions keep their own actual interval instead of being counted twice.
    val digital = records.filter { it.episode.id in qualifyingApps || (it.episode.kind == "MEDIA" && it.duration >= 5 * MINUTE) }
    seed("DIGITAL", digital.filterNot { attach(it) }, 10 * MINUTE, 120 * MINUTE) { _, _ -> true }
    val photos = records.filter { it.episode.kind == "PHOTO" || it.episode.photoUris.isNotEmpty() }
    seed("PHOTO", photos.filter { it.episode.id !in assigned && !attach(it) }, 30 * MINUTE, 90 * MINUTE) { _, _ -> true }
    records.filter { it.episode.id !in assigned }.forEach { attach(it) }

    return groups.map { group ->
        val rows = (group.seeds + group.related).sortedWith(compareBy({ it.start }, { it.episode.id }))
        val entries = rows.map { it.episode }
        val apps = rows.filter { it.episode.id in qualifyingApps }.groupBy { it.episode.packageName.ifBlank { it.episode.title } }.values.map { appRows ->
            val spans = union(appRows.map { Span(it.start, it.end) })
            ChapterApp(appRows.first().episode, spans.sumOf { it.duration }, spans)
        }.sortedWith(compareByDescending<ChapterApp> { it.durationMs }.thenBy { it.episode.title })
        val images = entries.flatMap { e -> e.photoUris.map { ChapterPhoto(e, it) } }.distinctBy { it.uri }
        val primary = group.seeds.first().episode
        val kind = if(group.kind == "DIGITAL") if(apps.isEmpty()) "MEDIA" else "APP" else group.kind
        val inferred = group.seeds.any { it.episode.assertion == Assertion.INFERRED }
        val title = when(group.kind) {
            "PHOTO" -> if(images.size > 1 || group.seeds.size > 1) "一组影像" else "留下一项影像"
            "DIGITAL" -> when {
                apps.isEmpty() -> if(group.seeds.map { it.episode.title }.distinct().size == 1) primary.title else "一段媒体时光"
                group.seeds.any { it.episode.kind == "MEDIA" } -> "一段屏幕时光"
                apps.size == 1 -> apps.first().episode.title
                else -> "一段应用时光"
            }
            else -> primary.title
        } + if(inferred) "（推测）" else ""
        val summary = buildList<String> {
            when(group.kind) {
                "STAY" -> add("停留${durationText(com.personal.wo.core.union(group.seeds.map { Span(it.start,it.end) }).sumOf { it.duration })}${if(inferred)"（推测）" else ""}")
                "EXERCISE", "MOVE", "SLEEP" -> add("${if(kind=="SLEEP")"睡眠" else if(kind=="MOVE")"移动" else "活动"}${durationText(com.personal.wo.core.union(group.seeds.map { Span(it.start,it.end) }).sumOf { it.duration })}${if(inferred)"（推测）" else ""}")
                "DIGITAL" -> if(apps.isNotEmpty()) add("较长使用${apps.size}个应用，共${durationText(com.personal.wo.core.union(apps.flatMap { it.spans }).sumOf { it.duration })}")
            }
            if(images.isNotEmpty()) add("留下${images.size}项影像")
            val media = rows.filter { it.episode.kind == "MEDIA" }
            if(media.isNotEmpty()) add("媒体播放${durationText(com.personal.wo.core.union(media.map { Span(it.start,it.end) }).sumOf { it.duration })}")
            if(isEmpty()) add("保留了${group.seeds.size}项影像记录")
        }.take(3).joinToString("，", postfix="。")
        DayChapter("${group.kind}:${primary.id}", kind, RecapPart(title, group.start, group.end, entries), apps, images, summary,
            entries.count { it.kind == "APP" && it.id !in qualifyingApps },
            union(group.seeds.map { Span(it.start,it.end) }).sumOf { it.duration })
    }.sortedWith(compareBy({ it.part.startMs }, { it.part.endMs }, { it.id }))
}

/** Seek the nearest event start, not a fixed period or an all-day overlapping stay. */
fun nearestChapter(chapters: List<DayChapter>, timeMs: Long): Int =
    chapters.indices.minByOrNull { abs(chapters[it].part.startMs - timeMs) } ?: -1
