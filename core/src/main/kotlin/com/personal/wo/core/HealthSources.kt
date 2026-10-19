package com.personal.wo.core

import java.time.Instant
import java.time.ZoneId

const val HUAWEI_HEALTH = "HUAWEI_HEALTH"
const val HEALTH_CONNECT = "HEALTH_CONNECT"

/** V1 Health Connect rows did not carry a provider. Keep them readable. */
fun healthProvider(o: Observation): String = o.payload.provider.ifBlank { HEALTH_CONNECT }

/** A daily account total replaces overlapping phone deltas; independent totals are never added.
 * Prefer Huawei, then Health Connect, then hardware. Corrections may decrease a total.
 * Only exact archive-day totals are eligible (including 23/25-hour DST days).
 */
fun selectStepSources(input: List<Observation>, zone: ZoneId, asOf: Long): List<Observation> {
    val unique = input.groupBy { it.source to it.sourceKey }.values.map { rows->rows.maxBy { it.receivedMs } }
    val totals = unique.filter { o ->
        if(o.kind != Kind.HEALTH_STEPS || o.source != Source.HEALTH || o.startMs > asOf ||
            o.receivedMs > asOf || o.payload.steps == null || o.payload.steps < 0 ||
            healthProvider(o) !in setOf(HUAWEI_HEALTH, HEALTH_CONNECT)) false
        else window(Instant.ofEpochMilli(o.startMs).atZone(zone).toLocalDate(), zone).let {
            o.startMs == it.start && o.endMs == it.end
        }
    }.groupBy { it.startMs to it.endMs }.values.map { rows ->
        val provider = if(rows.any { healthProvider(it) == HUAWEI_HEALTH }) HUAWEI_HEALTH else HEALTH_CONNECT
        rows.filter { healthProvider(it) == provider }.maxWith(compareBy({ it.receivedMs }, { it.sourceKey }))
    }
    return unique.filter { o -> when(o.kind) {
        Kind.HEALTH_STEPS -> false
        Kind.STEP_DELTA -> o.startMs <= asOf && (o.payload.steps ?: -1) >= 0 &&
            totals.none { o.startMs >= it.startMs && o.endMs <= it.endMs }
        else -> true
    } } + totals
}
