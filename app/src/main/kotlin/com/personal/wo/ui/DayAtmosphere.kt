package com.personal.wo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import com.personal.wo.core.*
import java.time.Instant
import java.time.ZoneId
import kotlin.math.floor

/** The archive's actual local time follows the finger continuously, in either direction. */
internal fun drumTime(day: DayView, chapters: List<DayChapter>, position: Float): Long {
    val times = listOf(day.windowStartMs) + chapters.map { it.part.startMs } + minOf(day.asOfMs, day.windowEndMs)
    val value = position.coerceIn(0f, (times.size - 1).toFloat())
    val index = floor(value).toInt()
    val next = times.getOrElse(index + 1) { times[index] }
    return times[index] + ((next - times[index]) * (value - index)).toLong()
}

internal data class DayAtmosphere(val top: Color, val bottom: Color, val card: Color) {
    val ink get() = readableInk(listOf(top))
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
private data class SkyStop(val hour: Float, val top: Long, val bottom: Long, val card: Long)
private val skyStops = listOf(
    SkyStop(0f,0xFF151D36,0xFF283557,0xFF303B58),
    SkyStop(4f,0xFF222B47,0xFF424366,0xFF3C4563),
    SkyStop(6.5f,0xFFE9C9B6,0xFFF8ECE2,0xFFFFFAF5),
    SkyStop(10f,0xFFCFE4EE,0xFFF2F6F0,0xFFFDFEFE),
    SkyStop(15f,0xFFDFEAD9,0xFFF6F2E7,0xFFFFFEFA),
    SkyStop(18.5f,0xFFEDB79B,0xFFE8CDCD,0xFFFFF7F0),
    SkyStop(20f,0xFF262A48,0xFF3A3859,0xFF3E3F5C),
    SkyStop(24f,0xFF151D36,0xFF283557,0xFF303B58)
)
internal fun dayAtmosphere(timeMs: Long, zone: String, dark: Boolean): DayAtmosphere {
    val local = Instant.ofEpochMilli(timeMs).atZone(ZoneId.of(zone))
    val hour = local.toLocalTime().toSecondOfDay() / 3600f
    val pair = skyStops.zipWithNext().first { hour >= it.first.hour && hour <= it.second.hour }
    val fraction = (hour - pair.first.hour) / (pair.second.hour - pair.first.hour)
    fun shade(a: Long, b: Long) = lerp(Color(a), Color(b), fraction).let {
        if(dark) lerp(Color(0xFF10131F), it, .28f) else it
    }
    return DayAtmosphere(shade(pair.first.top,pair.second.top),shade(pair.first.bottom,pair.second.bottom),shade(pair.first.card,pair.second.card))
}
private val LocalAtmosphere = staticCompositionLocalOf { DayAtmosphere(Color.White,Color.White,Color.White) }

@Composable internal fun TimelineAtmosphere(day: DayView, chapters: List<DayChapter>, position: Float,
    dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val sky = dayAtmosphere(drumTime(day,chapters,position),day.zoneId,dark)
    val ink = sky.ink
    val scheme = MaterialTheme.colorScheme.copy(background=sky.top,onBackground=ink,
        onSurface=ink,onSurfaceVariant=ink,primary=ink,primaryContainer=ink.copy(alpha=.12f),
        onPrimary=sky.top,onPrimaryContainer=ink,outline=ink.copy(alpha=.55f),outlineVariant=ink.copy(alpha=.2f))
    CompositionLocalProvider(LocalAtmosphere provides sky, LocalContentColor provides ink) {
        MaterialTheme(colorScheme=scheme) {
            Box(Modifier.fillMaxSize().background(sky.top).testTag("time-atmosphere")) { content() }
        }
    }
}
@Composable internal fun EventCardTheme(content: @Composable () -> Unit) {
    val sky=LocalAtmosphere.current
    val ink=sky.cardInk
    val scheme=MaterialTheme.colorScheme.copy(surface=sky.card,surfaceContainer=sky.card,onSurface=ink,onSurfaceVariant=ink,
        primary=ink,primaryContainer=ink.copy(alpha=.10f),onPrimaryContainer=ink,outlineVariant=ink.copy(alpha=.16f),background=sky.card)
    CompositionLocalProvider(LocalContentColor provides ink) { MaterialTheme(colorScheme=scheme,content=content) }
}
