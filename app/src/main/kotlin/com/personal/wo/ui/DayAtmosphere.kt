package com.personal.wo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import com.personal.wo.core.*
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.sin

internal fun drumTimes(day: DayView, chapters: List<DayChapter>): LongArray {
    val start = day.windowStartMs
    val end = minOf(day.asOfMs, day.windowEndMs).coerceAtLeast(chapters.lastOrNull()?.part?.startMs ?: start)
    return longArrayOf(start) + chapters.map { it.part.startMs }.toLongArray() + end
}

/** Actual archive time, continuously interpolated in both directions. */
internal fun drumTime(day: DayView, chapters: List<DayChapter>, position: Float): Long =
    timelineTime(drumTimes(day, chapters), position)

internal data class DayAtmosphere(val top: Color, val bottom: Color, val card: Color) {
    val ink get() = readableInk(listOf(top, bottom))
    val cardInk get() = readableInk(listOf(card))
}
private fun readableInk(backgrounds: List<Color>): Color {
    val dark = Color(0xFF151B2B)
    fun contrast(a: Color, b: Color) = (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
    return when {
        backgrounds.all { contrast(it, dark) >= 4.5f } -> dark
        backgrounds.all { contrast(it, Color.White) >= 4.5f } -> Color.White
        else -> Color.Black
    }
}
private data class SkyStop(val hour: Float, val top: Long, val bottom: Long)
private val skyStops = listOf(
    SkyStop(0f, 0xFF151D36, 0xFF283557),
    SkyStop(4f, 0xFF222B47, 0xFF424366),
    SkyStop(6.5f, 0xFFE9C9B6, 0xFFF8ECE2),
    SkyStop(10f, 0xFFCFE4EE, 0xFFF2F6F0),
    SkyStop(15f, 0xFFDFEAD9, 0xFFF6F2E7),
    SkyStop(18.5f, 0xFFEDB79B, 0xFFE8CDCD),
    SkyStop(20f, 0xFF262A48, 0xFF3A3859),
    SkyStop(24f, 0xFF151D36, 0xFF283557)
)
private fun localHour(timeMs: Long, zone: ZoneId): Float =
    Instant.ofEpochMilli(timeMs).atZone(zone).toLocalTime().toNanoOfDay() / 3_600_000_000_000f

internal fun dayAtmosphere(timeMs: Long, zone: String, dark: Boolean): DayAtmosphere =
    atmosphereAtHour(localHour(timeMs, ZoneId.of(zone)), dark)

private fun atmosphereAtHour(hour: Float, dark: Boolean): DayAtmosphere {
    val right = skyStops.indexOfFirst { it.hour >= hour }.coerceAtLeast(1)
    val a = skyStops[right - 1]
    val b = skyStops[right]
    val fraction = smoothUnit((hour - a.hour) / (b.hour - a.hour))
    val base = if (dark) Color(0xFF10131F) else Color(0xFFF5F4EF)
    // Time changes the light, not the polarity of the entire interface. Keeping the
    // surfaces in their system-theme range prevents black/white text threshold flashes.
    fun shade(first: Long, second: Long) =
        lerp(base, lerp(Color(first), Color(second), fraction), if (dark) .20f else .36f)
    return DayAtmosphere(shade(a.top, b.top), shade(a.bottom, b.bottom),
        if (dark) Color(0xFF232938) else Color(0xFFFCFBF7))
}
private val LocalAtmosphere = staticCompositionLocalOf { DayAtmosphere(Color.White, Color.White, Color.White) }

/** Float overload retained for callers and existing tests. */
@Composable internal fun TimelineAtmosphere(day: DayView, chapters: List<DayChapter>, position: Float,
    dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) =
    TimelineAtmosphere(day, chapters, { position }, dark, content)

@Composable internal fun TimelineAtmosphere(day: DayView, chapters: List<DayChapter>, position: () -> Float,
    dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val times = remember(day, chapters) { drumTimes(day, chapters) }
    val zone = remember(day.zoneId) { ZoneId.of(day.zoneId) }
    val hour = localHour(timelineTime(times, position()), zone)
    val sky = atmosphereAtHour(hour, dark)
    // Spring retargeting preserves continuity, even when a calendar/tick jump replaces
    // a drag. These finite animations respect the system's animation duration scale.
    val top = animateColorAsState(sky.top, spring(dampingRatio = 1f, stiffness = 110f), label = "sky-top")
    val bottom = animateColorAsState(sky.bottom, spring(dampingRatio = 1f, stiffness = 110f), label = "sky-bottom")
    val lightX = animateFloatAsState(.5f + .18f * sin(hour / 24f * 2f * PI.toFloat()),
        spring(dampingRatio = 1f, stiffness = 90f), label = "sky-light-position")
    val paper = remember(dark) {
        val card = if (dark) Color(0xFF232938) else Color(0xFFFCFBF7)
        DayAtmosphere(card, card, card)
    }
    val ink = paper.cardInk
    val scheme = MaterialTheme.colorScheme.copy(background = paper.card, onBackground = ink,
        onSurface = ink, onSurfaceVariant = ink.copy(alpha = .85f), primary = ink,
        primaryContainer = ink.copy(alpha = .12f), onPrimary = paper.card, onPrimaryContainer = ink,
        outline = ink.copy(alpha = .55f), outlineVariant = ink.copy(alpha = .2f))
    // Only this stable paper is provided to the cards. Moving sunlight must not
    // re-theme every thumbnail and text composable on every animation frame.
    CompositionLocalProvider(LocalAtmosphere provides paper, LocalContentColor provides ink) {
        MaterialTheme(colorScheme = scheme) {
            Box(Modifier.fillMaxSize().testTag("time-atmosphere").drawWithCache {
                val background = Brush.verticalGradient(listOf(top.value, bottom.value))
                val light = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = if (dark) .035f else .12f), Color.Transparent),
                    center = Offset(size.width * lightX.value, size.height * .42f),
                    radius = (maxOf(size.width, size.height) * .75f).coerceAtLeast(1f)
                )
                onDrawBehind { drawRect(background); drawRect(light) }
            }) { content() }
        }
    }
}

@Composable internal fun EventCardTheme(content: @Composable () -> Unit) {
    val sky = LocalAtmosphere.current
    val ink = sky.cardInk
    val scheme = MaterialTheme.colorScheme.copy(surface = sky.card, surfaceContainer = sky.card,
        onSurface = ink, onSurfaceVariant = ink.copy(alpha = .85f), primary = ink,
        primaryContainer = ink.copy(alpha = .10f), onPrimaryContainer = ink,
        outlineVariant = ink.copy(alpha = .16f), background = sky.card)
    CompositionLocalProvider(LocalContentColor provides ink) { MaterialTheme(colorScheme = scheme, content = content) }
}
