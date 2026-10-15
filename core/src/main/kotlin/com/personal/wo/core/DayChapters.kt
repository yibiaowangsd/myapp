package com.personal.wo.core

import kotlin.math.abs

val homeAppThresholds = listOf(5, 10, 20, 30)
fun checkedHomeAppMinutes(value: Int) = value.takeIf { it in homeAppThresholds } ?: 10

data class ChapterApp(val episode: Episode, val durationMs: Long, val spans: List<Span>)
data class ChapterPhoto(val episode: Episode, val uri: String)
data class DayChapter(
    val part: RecapPart,
    val apps: List<ChapterApp>,
    val photos: List<ChapterPhoto>,
    val summary: String,
    val shortAppCount: Int
)

/** A presentation projection only. Every original record remains in part.episodes.
 * Qualify individual observed sessions before grouping: repeated short openings
 * never become a long session. Split qualifying sessions at local part boundaries,
 * then union overlaps, so neither gaps nor duplicate observations inflate time. */
fun dayChapters(day: DayView, minimumAppMinutes: Int = 10): List<DayChapter> {
    val minimum = checkedHomeAppMinutes(minimumAppMinutes) * 60_000L
    val qualifying = observedAppSpans(day).filter { it.second.duration >= minimum }
    return recapParts(day).filter { it.endMs > it.startMs }.map { part ->
        val bounds = Span(part.startMs, part.endMs)
        val appRows = qualifying.mapNotNull { (e, span) -> span.intersect(bounds)?.let { e to it } }
        val apps = appRows.groupBy { it.first.packageName.ifBlank { it.first.title } }.values.map { rows ->
            val spans = union(rows.map { it.second })
            ChapterApp(rows.first().first, spans.sumOf { it.duration }, spans)
        }.sortedWith(compareByDescending<ChapterApp> { it.durationMs }.thenBy { it.episode.title })
        val photos = part.episodes.flatMap { e -> e.photoUris.map { ChapterPhoto(e, it) } }.distinctBy { it.uri }
        val highlights = part.episodes.filter { it.kind in setOf("STAY", "MOVE", "EXERCISE", "SLEEP", "STEPS", "MEDIA", "PHOTO") }
        val text = recapPartText(part.copy(episodes = highlights)).ifBlank {
            if(apps.isNotEmpty()) "有${apps.size}个应用的较长使用时段。" else "零散片段已收进这段时间的详情。"
        }
        val qualifiedIds = appRows.map { it.first.id }.toSet()
        DayChapter(part, apps, photos, text, part.episodes.count { it.kind == "APP" && it.id !in qualifiedIds })
    }
}

/** Prefer the containing period; a gap selects the closest actual period. */
fun nearestChapter(chapters: List<DayChapter>, timeMs: Long): Int {
    val containing = chapters.indexOfFirst { timeMs in it.part.startMs until it.part.endMs }
    return if(containing >= 0) containing else chapters.indices.minByOrNull {
        minOf(abs(chapters[it].part.startMs - timeMs), abs(chapters[it].part.endMs - timeMs))
    } ?: -1
}
