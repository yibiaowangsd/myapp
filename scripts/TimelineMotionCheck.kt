package com.personal.wo.ui

import kotlin.math.abs

/** Runs the production geometry, not a JavaScript/Python reimplementation. */
fun main() {
    var cases = 0
    for (step in 0..4000) {
        val offset = step / 1000f
        val left = drumPose(-offset)
        val right = drumPose(offset)
        check(abs(left.x + right.x) < .00001f)
        check(abs(left.rotation + right.rotation) < .00001f)
        check(left.scale == right.scale && left.alpha == right.alpha && left.y == right.y)
        check(right.alpha in 0f..1f && right.scale in .64f..1f)
        if (step > 0) {
            val previous = drumPose((step - 1) / 1000f)
            check(right.x >= previous.x) { "Chronological strip folds back at $offset" }
            check(abs(right.alpha - previous.alpha) < .002f) { "Alpha pop at $offset" }
            check(abs(right.scale - previous.scale) < .002f)
        }
        cases++
    }
    check(drumPose(0f).alpha == 1f && drumPose(0f).scale == 1f)
    check(drumPose(2.85f).alpha == 0f && drumPose(-2.85f).alpha == 0f)
    for (boundary in listOf(1.1f, 1.5f, 2f, 2.85f)) {
        check(abs(drumPose(boundary + .0001f).alpha - drumPose(boundary - .0001f).alpha) < .001f)
        cases++
    }
    for (page in 0..50) {
        val sameEventBefore = drumPose(12 - (page + .49999f))
        val sameEventAfter = drumPose(12 - ((page + 1) - .49999f))
        check(abs(sameEventBefore.x - sameEventAfter.x) < .0001f)
        check(abs(sameEventBefore.alpha - sameEventAfter.alpha) < .0001f)
        check(drumLane(page) != drumLane(page + 1))
        cases++
    }
    val epoch = 1_792_000_000_000L
    val times = longArrayOf(epoch, epoch + 300_000L, epoch + 7_200_000L, epoch + 60_000_000L)
    for (step in 0..3000) {
        val position = step / 1000f
        val time = timelineTime(times, position)
        check(abs(timelinePosition(times, time) - position) < .00001f)
        cases++
    }
    check(timelineTime(times, -3f) == times.first())
    check(timelineTime(times, 9f) == times.last())
    check(timelinePosition(times, 0L) == 0f)
    check(timelinePosition(times, Long.MAX_VALUE) == times.lastIndex.toFloat())
    check(timelineTime(longArrayOf(epoch), 5f) == epoch)
    check(timelinePosition(longArrayOf(epoch), epoch) == 0f)
    check(timelinePosition(longArrayOf(0, 0, 100), 50) == 1.5f)
    for (step in 0..1000) check(smoothUnit(step / 1000f) in 0f..1f)
    println("PASS: $cases geometry/time samples; mirrored depth, continuous fade, no foldback, page-boundary continuity, alternating lanes, inverse scrubbing, bounds and duplicate timestamps.")
}
