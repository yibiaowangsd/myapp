package com.personal.wo.core

data class Span(val start: Long, val end: Long) {
    init { require(end >= start) }
    val duration: Long get() = end - start
    fun intersect(other: Span): Span? {
        val a = maxOf(start, other.start); val b = minOf(end, other.end)
        return if (b > a) Span(a, b) else null
    }
    fun contains(t: Long): Boolean = t >= start && t < end
}
fun union(input: List<Span>): List<Span> {
    val result = mutableListOf<Span>()
    input.filter { it.duration > 0 }.sortedBy { it.start }.forEach { s ->
        val p = result.lastOrNull()
        if (p != null && s.start <= p.end) result[result.lastIndex] = Span(p.start, maxOf(s.end, p.end)) else result += s
    }
    return result
}
fun subtract(span: Span, excluded: List<Span>): List<Span> {
    var result = listOf(span)
    union(excluded).forEach { x -> result = result.flatMap { s ->
        if (s.intersect(x) == null) listOf(s) else buildList {
            if (x.start > s.start) add(Span(s.start, minOf(x.start, s.end)))
            if (x.end < s.end) add(Span(maxOf(x.end, s.start), s.end))
        }
    } }
    return result.filter { it.duration > 0 }
}
fun exclusionApplies(o: Observation, x: Exclusion): Boolean =
    (x.source == null || x.source == o.source) && (x.packageName == null || x.packageName == o.payload.packageName) && (x.sourceKey == null || x.sourceKey == o.sourceKey)
fun filterObservation(o: Observation, policy: Policy): List<Observation> {
    if (o.source !in policy.enabledSources || o.payload.packageName in policy.excludedApps) return emptyList()
    val xs = policy.exclusions.filter { exclusionApplies(o, it) }.map { Span(it.startMs, it.endMs ?: Long.MAX_VALUE) }
    if (o.endMs == o.startMs) return if (xs.any { it.contains(o.startMs) }) emptyList() else listOf(o)
    if (xs.none { it.intersect(Span(o.startMs, o.endMs)) != null }) return listOf(o)
    if (o.kind in setOf(Kind.STEP_DELTA, Kind.HEALTH_STEPS, Kind.SLEEP, Kind.EXERCISE, Kind.NOTICE, Kind.PHOTO, Kind.CALENDAR)) return emptyList()
    return subtract(Span(o.startMs, o.endMs), xs).map { o.copy(id = stableId(o.id, it.start), startMs = it.start, endMs = it.end, sourceKey = "${o.sourceKey}:${it.start}") }
}

class StepBaseline {
    private var value: Long? = null
    private var boot = ""
    private var time = 0L
    fun reset() { value = null }
    fun update(next: Long, bootId: String, at: Long): Pair<Span, Long>? {
        val old = value; val t = time; val b = boot
        value = next; boot = bootId; time = at
        return if (old != null && bootId == b && next >= old && at > t) Span(t, at) to (next - old) else null
    }
}

data class Projection(val x: Double, val y: Double)
fun project(lat: Double, lon: Double, anchorLat: Double, anchorLon: Double): Projection {
    var delta = lon - anchorLon
    while (delta > 180) delta -= 360
    while (delta < -180) delta += 360
    return Projection(delta * kotlin.math.cos(Math.toRadians(anchorLat)), -lat + anchorLat)
}
fun distanceM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val dLat = Math.toRadians(bLat - aLat); val dLon = Math.toRadians(bLon - aLon)
    val a = kotlin.math.sin(dLat / 2).let { it * it } + kotlin.math.cos(Math.toRadians(aLat)) * kotlin.math.cos(Math.toRadians(bLat)) * kotlin.math.sin(dLon / 2).let { it * it }
    return 6_371_000 * 2 * kotlin.math.asin(kotlin.math.sqrt(a.coerceIn(0.0, 1.0)))
}
