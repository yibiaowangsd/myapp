package com.personal.wo.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Shared, allocation-light geometry for the cards, their stems and their lighting. */
internal data class DrumPose(
    val x: Float, val y: Float, val rotation: Float,
    val scale: Float, val alpha: Float, val depth: Float
)

/** C2-continuous at both ends: there is no visible pop at the culling boundary. */
internal fun smoothUnit(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return (t * t * t * (t * (t * 6f - 15f) + 10f)).coerceIn(0f, 1f)
}

internal fun drumPose(offset: Float): DrumPose {
    // atan keeps a long day's strip monotonic. A sine of an unbounded angle would
    // bring later events back across the screen and reverse chronological order.
    val angle = atan(offset * .78f)
    val depth = cos(angle)
    val fade = 1f - smoothUnit((abs(offset) - 1.1f) / 1.75f)
    return DrumPose(
        x = .57f * sin(angle), y = (1f - depth) * 10f,
        rotation = angle * (180f / PI.toFloat()) * .55f,
        scale = .64f + .36f * depth,
        alpha = fade * (.70f + .30f * depth), depth = depth
    )
}

/** The lane belongs to the event, never to the selected page or the drag direction. */
internal fun drumLane(index: Int): Float = if (index % 2 == 0) -1f else 1f

/** Epoch times, not wall-clock hours, preserve ordering through archive-zone DST. */
internal fun timelineTime(times: LongArray, position: Float): Long {
    require(times.isNotEmpty())
    val value = position.coerceIn(0f, times.lastIndex.toFloat())
    val index = floor(value).toInt()
    val next = times.getOrElse(index + 1) { times[index] }
    return times[index] + ((next - times[index]).toDouble() * (value - index)).toLong()
}

/** Inverse time mapping used by the scrubber: interpolate, don't jump to nearest. */
internal fun timelinePosition(times: LongArray, time: Long): Float {
    require(times.isNotEmpty())
    if (time <= times.first()) return 0f
    if (time >= times.last()) return times.lastIndex.toFloat()
    var low = 0
    var high = times.lastIndex
    while (low + 1 < high) {
        val middle = (low + high) ushr 1
        if (times[middle] <= time) low = middle else high = middle
    }
    val length = times[high] - times[low]
    return if (length <= 0L) high.toFloat()
    else low + ((time - times[low]).toDouble() / length).toFloat()
}
