package com.personal.wo.core

/** Interpret only the selected hardware stream; counter and detector are never added together. */
class StepReadings(private val counter: Boolean) {
    private val baseline = StepBaseline()
    private var lastNs = -1L
    fun accept(value: Float, sensorNs: Long, nowMs: Long, elapsedNs: Long, boot: String): Pair<Span,Long>? {
        if(!value.isFinite() || value < 0 || sensorNs <= lastNs || sensorNs > elapsedNs) return null
        lastNs = sensorNs
        val at = nowMs - (elapsedNs - sensorNs) / 1_000_000
        if(at < 0) return null
        if(counter) return baseline.update(value.toLong(), boot, at)
        // Android TYPE_STEP_DETECTOR reports exactly 1.0 per step. Reject malformed events.
        return if(value == 1f) Span(at, at + 1) to 1L else null
    }
}
