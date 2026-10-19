package com.personal.wo.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

class DayEngine(val config: EngineConfig = EngineConfig()) {
    private fun evidence(o: Observation, description: String) = Evidence(o.id, o.source, o.sourceKey, description)

    fun episodes(input: List<Observation>, policy: Policy, places: List<Place>, asOf: Long): List<Episode> {
        val observations = selectStepSources(input.flatMap { filterObservation(it, policy) }, ZoneId.of(policy.archiveZone), asOf)
            .filter { it.startMs >= 0 && it.endMs >= it.startMs && it.startMs <= asOf + 8 * 86_400_000L }
            .flatMap { filterObservation(it, policy) }.sortedWith(compareBy({ it.startMs }, { it.id }))
        val result = mutableListOf<Episode>()
        result += usage(observations.filter { it.source == Source.USAGE }, policy)
        result += stays(observations.filter { it.kind == Kind.POSITION }, policy, places)
        result += media(observations.filter { it.source == Source.MEDIA }, policy)
        observations.forEach { o ->
            val p = o.payload
            val episode = when (o.kind) {
                Kind.NOTICE -> Episode(stableId("notice", o.sourceKey), startMs = o.startMs, endMs = o.startMs,
                    lane = Lane.LIFE, kind = "NOTICE", assertion = Assertion.RECORDED,
                    title = p.category.ifBlank { "通知片段" }, detail = listOf(p.label, p.text).filter { it.isNotBlank() }.joinToString(" · "),
                    evidence = listOf(evidence(o, "${p.packageName} 发布的通知片段；不代表完整消息或已完成行为")), packageName = p.packageName)
                Kind.PHOTO -> Episode(stableId("photo", o.sourceKey), startMs = o.startMs, endMs = o.startMs,
                    lane = Lane.LIFE, kind = "PHOTO", assertion = Assertion.RECORDED, title = if(p.category=="VIDEO") if(p.captured) "拍摄的视频" else "添加的视频" else if (p.captured) "拍摄的照片" else "添加的图片",
                    detail = if (p.captured) "按媒体来源提供的拍摄时间关联" else "没有可靠拍摄时间，按媒体添加时间显示",
                    photoUris = listOf(p.uri), evidence = listOf(evidence(o, "MediaStore元数据；原图未复制")))
                Kind.CALENDAR -> Episode(stableId("calendar", o.sourceKey), startMs = o.startMs, endMs = o.endMs,
                    lane = Lane.LIFE, kind = "CALENDAR", assertion = Assertion.PLANNED, title = p.label.ifBlank { "日程安排" },
                    detail = "日历中的计划，实际出席未确认${if (p.allDay) " · 全天" else ""}", evidence = listOf(evidence(o, "只读日历实例")))
                Kind.EXERCISE -> Episode(stableId("exercise", o.sourceKey), startMs = o.startMs, endMs = o.endMs,
                    lane = Lane.ACTIVITY, kind = "EXERCISE", assertion = Assertion.RECORDED, title = "来源记录的运动",
                    detail = p.label, evidence = listOf(evidence(o, "${if(healthProvider(o)==HUAWEI_HEALTH) "华为运动健康" else "Health Connect"}运动记录")))
                Kind.SLEEP -> Episode(stableId("sleep", o.sourceKey), startMs = o.startMs, endMs = o.endMs,
                    lane = Lane.ACTIVITY, kind = "SLEEP", assertion = Assertion.RECORDED, title = "来源记录的睡眠区间",
                    detail = "${p.label}；区间跨度不等于去除清醒后的睡眠时长", evidence = listOf(evidence(o, "${if(healthProvider(o)==HUAWEI_HEALTH) "华为运动健康" else "Health Connect"}睡眠会话")), amount = p.sleepMinutes)
                Kind.HEALTH_STEPS -> Episode(stableId("health.steps", o.sourceKey), startMs=o.startMs, endMs=minOf(o.endMs,asOf),
                    lane=Lane.ACTIVITY, kind="STEPS", assertion=Assertion.RECORDED, title="来源记录到${p.steps}步",
                    detail="${p.label}；当日可见聚合，未叠加手机计步", evidence=listOf(evidence(o,"${p.label}日聚合")), amount=p.steps)
                Kind.STEP_DELTA -> if ((p.steps ?: 0) > 0) Episode(stableId("steps", o.sourceKey), startMs = o.startMs, endMs = o.endMs,
                    lane = Lane.ACTIVITY, kind = "STEPS", assertion = Assertion.RECORDED, title = "设备记录到${p.steps}步",
                    detail = if(p.provider=="STEP_DETECTOR") "手机硬件单步检测记录" else "硬件计步器在同一启动周期中的增量", evidence = listOf(evidence(o, if(p.provider=="STEP_DETECTOR") "单步检测事件" else "累计计步差分")), amount = p.steps) else null
                else -> null
            }
            if (episode != null) result += episode.copy(qualityFlags = o.quality)
        }
        return result.sortedWith(compareBy({ it.startMs }, { it.lane.ordinal }, { it.id }))
    }

    private fun usage(events: List<Observation>, policy: Policy): List<Episode> {
        data class Active(val start: Observation, val classes: MutableSet<String>, var ambiguous: Boolean = false)
        data class Item(val time: Long, val observation: Observation? = null, val barrier: Exclusion? = null)
        val active = mutableMapOf<String, Active>()
        val result = mutableListOf<Episode>()
        val barriers = policy.exclusions.filter { it.source == null || it.source == Source.USAGE }.map { Item(it.startMs, barrier = it) }
        val items = (events.map { Item(it.startMs, it) } + barriers).sortedWith(compareBy({ it.time }, { when {
            it.barrier!=null->-1
            it.observation?.kind in setOf(Kind.SCREEN_ON,Kind.UNLOCK)->0
            it.observation?.kind in setOf(Kind.LOCK,Kind.SCREEN_OFF,Kind.SHUTDOWN,Kind.BOOT)->1
            it.observation?.kind in setOf(Kind.PAUSED,Kind.STOPPED)->2
            else->3
        } }))
        fun close(pkg: String, t: Long, boundary: Observation? = null) {
            val a = active.remove(pkg) ?: return
            if (t <= a.start.startMs) return
            result += Episode(stableId("usage", a.start.sourceKey), startMs = a.start.startMs, endMs = t,
                lane = Lane.DIGITAL, kind = "APP", assertion = Assertion.RECORDED,
                title = a.start.payload.label.ifBlank { pkg }, detail = "已观察应用活动；分屏可能重叠",
                evidence = listOfNotNull(evidence(a.start, "活动进入RESUMED"), boundary?.let { evidence(it, "活动结束或屏幕边界") }),
                qualityFlags = if (a.ambiguous) listOf("INSTANCE_AMBIGUOUS") else emptyList(), packageName = pkg)
        }
        var locked = false
        var keyguard=false
        items.forEach { item ->
            val x = item.barrier
            if (x != null) {
                active.keys.toList().filter { x.packageName == null || x.packageName == it }.forEach { close(it, item.time) }
                return@forEach
            }
            val o = item.observation ?: return@forEach; val pkg = o.payload.packageName
            when (o.kind) {
                Kind.LOCK -> { active.keys.toList().forEach { close(it,o.startMs,o) };keyguard=true;locked=true }
                Kind.SCREEN_OFF, Kind.SHUTDOWN, Kind.BOOT -> { active.keys.toList().forEach { close(it, o.startMs, o) }; locked = true }
                Kind.UNLOCK -> { keyguard=false;locked=false }
                Kind.SCREEN_ON -> locked=keyguard
                Kind.RESUMED -> if (!locked && pkg.isNotBlank()) {
                    val a = active[pkg]
                    if (a == null) active[pkg] = Active(o, mutableSetOf(o.payload.className))
                    else if (!a.classes.add(o.payload.className)) a.ambiguous = true
                }
                Kind.PAUSED, Kind.STOPPED -> {
                    val a = active[pkg]
                    if (a != null && a.classes.remove(o.payload.className) && a.classes.isEmpty()) close(pkg, o.startMs, o)
                }
                else -> Unit
            }
        }
        active.forEach { (pkg, a) -> result += Episode(stableId("usage", a.start.sourceKey), startMs = a.start.startMs, endMs = a.start.startMs,
            lane = Lane.DIGITAL, kind = "APP", assertion = Assertion.UNKNOWN, title = a.start.payload.label.ifBlank { pkg },
            detail = "结束时间不确定；尚不计入活动时长", evidence = listOf(evidence(a.start, "仅有活动开始事件")), qualityFlags = listOf("PROVISIONAL"), packageName = pkg) }
        val merged = mutableListOf<Episode>()
        result.groupBy { it.packageName }.forEach { (_, es) -> es.sortedBy { it.startMs }.forEach { e ->
            val p = merged.lastOrNull()
            val barrierBetween = events.any { it.kind in setOf(Kind.LOCK, Kind.SCREEN_OFF, Kind.BOOT, Kind.SHUTDOWN) && it.startMs == e.startMs }
            if (p != null && p.packageName == e.packageName && p.endMs == e.startMs && e.endMs > e.startMs && p.assertion == e.assertion && !barrierBetween)
                merged[merged.lastIndex] = p.copy(endMs = e.endMs, evidence = (p.evidence + e.evidence).distinctBy { it.id })
            else merged += e
        } }
        return merged
    }

    private fun media(events: List<Observation>, policy: Policy): List<Episode> {
        val starts = mutableMapOf<String, Observation>(); val out = mutableListOf<Episode>()
        events.forEach { o ->
            val key = o.payload.sessionKey.ifBlank { o.payload.packageName }
            if (o.kind == Kind.MEDIA_PLAY) {
                val prior=starts.put(key,o)
                if(prior!=null && prior.sourceKey!=o.sourceKey) out+=Episode(stableId("media",prior.sourceKey),startMs=prior.startMs,endMs=prior.startMs,
                    lane=Lane.LIFE,kind="MEDIA",assertion=Assertion.UNKNOWN,title="媒体播放 · ${prior.payload.label}",detail="观察中断，旧播放段的结束时间不确定",
                    evidence=listOf(evidence(prior,"中断前的播放状态")),qualityFlags=listOf("PROVISIONAL"))
            }
            if (o.kind == Kind.MEDIA_PAUSE) {
                val start = starts.remove(key)
                if (start != null && o.startMs > start.startMs) {
                    val xs = policy.exclusions.filter { exclusionApplies(start, it) }.map { Span(it.startMs, it.endMs ?: Long.MAX_VALUE) }
                    // A pause/disconnect breaks the session. Never extend it across a stopped collector.
                    val end = minOf(o.startMs, xs.filter { it.start >= start.startMs }.minOfOrNull { it.start } ?: o.startMs)
                    if (end > start.startMs) out += Episode(stableId("media", start.sourceKey), startMs = start.startMs, endMs = end,
                        lane = Lane.LIFE, kind = "MEDIA", assertion = Assertion.RECORDED, title = "媒体播放 · ${start.payload.label.ifBlank { start.payload.packageName }}",
                        detail = "仅记录播放状态区间，不代表实际收听", evidence = listOf(evidence(start, "STATE_PLAYING"), evidence(o, "暂停、停止或会话断开")), packageName = start.payload.packageName)
                }
            }
        }
        starts.values.forEach { o -> out += Episode(stableId("media", o.sourceKey), startMs = o.startMs, endMs = o.startMs,
            lane = Lane.LIFE, kind = "MEDIA", assertion = Assertion.UNKNOWN, title = "媒体播放 · ${o.payload.label}", detail = "播放结束边界尚未确认",
            evidence = listOf(evidence(o, "已观察到播放状态")), qualityFlags = listOf("PROVISIONAL")) }
        return out
    }

    private fun stays(points: List<Observation>, policy: Policy, places: List<Place>): List<Episode> {
        val good = points.filter { p ->
            val v = p.payload
            v.lat != null && v.lon != null && v.accuracyM != null && v.lat in -90.0..90.0 && v.lon in -180.0..180.0 && v.accuracyM in 0.1..200.0 && !v.mock && v.coordinateSystem == "WGS84"
        }
        val out = mutableListOf<Episode>(); var group = mutableListOf<Observation>(); var outside = mutableListOf<Observation>()
        fun center(g: List<Observation>): Pair<Double, Double> {
            val anchor = g.first().payload.lon!!
            var a = 0.0; var b = 0.0; var w = 0.0
            g.forEach { val p = it.payload; val weight = 1.0 / max(p.accuracyM!!, 20.0).let { x -> x * x }
                val d = project(p.lat!!, p.lon!!, p.lat, anchor).x / kotlin.math.cos(Math.toRadians(p.lat)).coerceAtLeast(0.001)
                a += p.lat * weight; b += (anchor + d) * weight; w += weight }
            var lon = b / w; while (lon > 180) lon -= 360; while (lon < -180) lon += 360
            return a / w to lon
        }
        fun flush() {
            if (group.size >= config.stayMinPoints && group.last().startMs - group.first().startMs >= config.stayMinMinutes * 60_000L) {
                val (lat, lon) = center(group)
                val place = places.minByOrNull { distanceM(lat, lon, it.lat, it.lon) }?.takeIf { distanceM(lat, lon, it.lat, it.lon) <= it.radiusM }
                out += Episode(stableId("stay", group.first().sourceKey), startMs = group.first().startMs, endMs = group.last().startMs,
                    lane = Lane.PLACE, kind = "STAY", assertion = Assertion.INFERRED, title = "${place?.name ?: "停留地点"}附近停留",
                    detail = "${group.size}个有效位置支持；起止为可见边界，精度约${group.map { it.payload.accuracyM!! }.sorted()[group.size / 2].toInt()}米",
                    lat = lat, lon = lon, evidence = group.map { evidence(it, "框架位置 · ${it.payload.provider} · 精度${it.payload.accuracyM?.toInt()}米") }, qualityFlags = listOf("UNCERTAIN_BOUNDARY"))
            }
            group = mutableListOf(); outside = mutableListOf()
        }
        good.forEach { p ->
            if (group.isEmpty()) { group += p; return@forEach }
            val gap = p.startMs - group.last().startMs
            val crossesExclusion = policy.exclusions.any { x -> exclusionApplies(p, x) && Span(group.last().startMs, p.startMs).intersect(Span(x.startMs, x.endMs ?: Long.MAX_VALUE)) != null }
            if (gap > config.stayMaxGapMinutes * 60_000L || crossesExclusion) { flush(); group += p; return@forEach }
            val (lat, lon) = center(group)
            val accuracies = (group + p).map { it.payload.accuracyM!! }.sorted()
            val radius = (2 * accuracies[accuracies.size / 2]).coerceIn(config.stayRadiusMinM, config.stayRadiusMaxM)
            if (distanceM(lat, lon, p.payload.lat!!, p.payload.lon!!) <= radius) { group += p; outside.clear() }
            else {
                outside += p
                if (outside.size >= 2 || group.size < config.stayMinPoints) {
                    val next = outside.toMutableList(); flush(); group = next
                }
            }
        }
        flush()
        val moves = mutableListOf<Episode>()
        out.zipWithNext().forEach { (a, b) ->
            val hasExclusion = policy.exclusions.any { it.source == null || it.source == Source.LOCATION }.let { _ -> policy.exclusions.any { (it.source == null || it.source == Source.LOCATION) && Span(a.endMs, b.startMs).intersect(Span(it.startMs, it.endMs ?: Long.MAX_VALUE)) != null } }
            if (b.startMs > a.endMs && !hasExclusion && distanceM(a.lat!!, a.lon!!, b.lat!!, b.lon!!) > config.stayRadiusMinM) moves += Episode(stableId("move", a.id, b.id), startMs = a.endMs, endMs = b.startMs,
                lane = Lane.ACTIVITY, kind = "MOVE", assertion = Assertion.INFERRED, title = "地点间移动，路线未知", detail = "两段停留地点存在位移；没有证据认定出行方式",
                evidence = (a.evidence.takeLast(1) + b.evidence.take(1)), qualityFlags = listOf("ROUTE_UNKNOWN"))
        }
        return out + moves
    }

    fun build(date: LocalDate, policy: Policy, observations: List<Observation>, places: List<Place>, states: List<SourceState>, coverage: List<Coverage>, annotations: List<Annotation>, now: Long, revision: Long = 1, retained:List<Episode> = emptyList(),retainedMetrics:List<Metric> = emptyList(),freshEpisodes:List<Episode>?=null): DayView {
        val zone = ZoneId.of(policy.archiveZone); val w = window(date, zone); val asOf = minOf(now, w.end).coerceAtLeast(w.start)
        val fresh = freshEpisodes ?: episodes(observations, policy, places, now)
        val original = fresh + retained.filter { e -> fresh.none { it.id==e.id } && policy.exclusions.none { x ->
            (x.source==null||e.evidence.any { it.source==x.source }) && (x.packageName==null||e.packageName==x.packageName) && (x.sourceKey==null||e.evidence.any { it.sourceKey==x.sourceKey }) &&
            (if(e.startMs==e.endMs)Span(x.startMs,x.endMs?:Long.MAX_VALUE).contains(e.startMs) else Span(e.startMs,e.endMs).intersect(Span(x.startMs,x.endMs?:Long.MAX_VALUE))!=null)
        } }
        val mapped = original.map { e ->
            val a = annotations.lastOrNull { it.episodeId == e.id }
            if (a == null) e else e.copy(title = a.title ?: e.title, startMs = a.startMs ?: e.startMs, endMs = a.endMs ?: e.endMs, assertion = if (a.confirmed) Assertion.CONFIRMED else e.assertion, isFavorite = a.favorite ?: e.isFavorite, revision = e.revision + 1)
        }
        val episodes = mapped.mapNotNull { e ->
            if (e.startMs == e.endMs) e.takeIf { w.contains(e.startMs) }
            else Span(e.startMs, e.endMs).intersect(w)?.let { e.copy(startMs = it.start, endMs = it.end) }
        }.sortedBy { it.startMs }
        val app = episodes.filter { it.kind == "APP" && it.assertion != Assertion.UNKNOWN && "CLOCK_CONFLICT" !in it.qualityFlags }
        val media = episodes.filter { it.kind == "MEDIA" && it.assertion != Assertion.UNKNOWN }
        val raw = selectStepSources(observations.flatMap { filterObservation(it, policy) },zone,now)
        val hc = raw.filter { it.kind == Kind.HEALTH_STEPS && it.startMs == w.start && it.endMs == w.end }.maxByOrNull { it.receivedMs }
        val step = raw.filter { it.kind == Kind.STEP_DELTA && it.startMs >= w.start && it.endMs <= w.end }
        val archiveSteps=retainedMetrics.firstOrNull { it.key=="steps" }
        fun available(source: Source): Boolean = states.any { it.source == source && it.lastSuccessMs!=null && it.lastSuccessMs in w.start until w.end } || coverage.any { it.source==source && it.available && it.startMs<w.end&&it.endMs>w.start } || raw.any { it.source == source && (it.startMs in w.start until w.end || it.endMs in w.start + 1..w.end) }
        val metrics = listOf(
            Metric("appMs", if (app.isNotEmpty()||available(Source.USAGE)) union(app.map { Span(it.startMs, it.endMs) }).sumOf { it.duration } else null, "ms", "已观察应用活动", app.flatMap { it.evidence }.map { it.id }),
            Metric("mediaMs", if (media.isNotEmpty()) union(media.map { Span(it.startMs, it.endMs) }).sumOf { it.duration } else null, "ms", "媒体播放", media.flatMap { it.evidence }.map { it.id }),
            Metric("steps", hc?.payload?.steps ?: if (step.isNotEmpty()) step.sumOf { it.payload.steps ?: 0 } else archiveSteps?.value, "步", if (hc != null) "健康来源聚合步数" else if(archiveSteps!=null) "归档来源步数" else "设备观察步数", hc?.let { listOf(it.id) } ?: if(step.isNotEmpty())step.map { it.id } else archiveSteps?.evidenceIds ?: emptyList()),
            Metric("photos", if (available(Source.PHOTO)) episodes.count { it.kind == "PHOTO" }.toLong() else null, "项", "关联媒体", episodes.filter { it.kind == "PHOTO" }.flatMap { it.evidence }.map { it.id })
        )
        val facts = mutableListOf<Fact>(); val sentences = mutableListOf<Sentence>()
        fun sentence(key: String, type: String, value: String, text: String, es: List<Episode>, assertion: Assertion = Assertion.RECORDED) {
            facts += Fact(key, type, value, es.flatMap { it.evidence }.map { it.id }, assertion); sentences += Sentence(text, listOf(key))
        }
        val placeEpisodes = episodes.filter { it.kind == "STAY" }
        if (placeEpisodes.isNotEmpty()) {
            val names = placeEpisodes.groupBy { it.title }.entries.sortedByDescending { (_, es) -> es.sumOf { it.endMs - it.startMs } }.take(2).map { it.key.removeSuffix("附近停留") }
            sentence("places", "PLACE", names.joinToString("、"), "记录到${names.joinToString("与")}附近的停留。", placeEpisodes, Assertion.INFERRED)
        }
        val steps = metrics.first { it.key == "steps" }
        if (steps.value != null && steps.value > 0) {
            facts += Fact("steps", "STEPS", "${steps.value}步", steps.evidenceIds, Assertion.RECORDED)
            sentences += Sentence("${steps.label}为${steps.value}步。", listOf("steps"))
        }
        if (app.isNotEmpty()) { val confirmed=app.any { it.assertion==Assertion.CONFIRMED };sentence("apps", "APP", durationText(metrics[0].value ?: 0), "应用活动记录共${durationText(metrics[0].value ?: 0)}${if(confirmed) "（含本人修正）" else ""}，重叠区间已去重。", app,if(confirmed)Assertion.CONFIRMED else Assertion.RECORDED) }
        if (media.isNotEmpty()) { val confirmed=media.any { it.assertion==Assertion.CONFIRMED };sentence("media", "MEDIA", durationText(metrics[1].value ?: 0), "媒体播放记录约${durationText(metrics[1].value ?: 0)}${if(confirmed) "（含本人修正）" else ""}。", media,if(confirmed)Assertion.CONFIRMED else Assertion.RECORDED) }
        val photos = episodes.filter { it.kind == "PHOTO" }
        if (photos.isNotEmpty() && sentences.size < config.summaryMaxSentences) sentence("photos", "PHOTO", "${photos.size}项", "有${photos.size}项按媒体时间关联的照片或视频。", photos)
        val warnings = buildList {
            if (raw.any { it.kind == Kind.STEP_DELTA && it.startMs < w.end && it.endMs > w.start && (it.startMs < w.start || it.endMs > w.end) }) add("有跨日步数增量待分配，未按时长均分。")
            if (episodes.any { it.assertion == Assertion.UNKNOWN }) add("部分片段的结束边界尚不确定，未计入精确时长。")
            if (coverage.any { !it.available && it.startMs < asOf && it.endMs > w.start }) add("存在来源缺失区间；空白不代表没有活动。")
            if (available(Source.LOCATION) && placeEpisodes.isEmpty()) add("还没有足够的有效位置确认停留地点。")
            if (policy.exclusions.any { it.source == null && it.startMs < asOf && (it.endMs ?: Long.MAX_VALUE) > w.start }) add("已排除的时间范围不参与采集和补采。")
            val covered=com.personal.wo.core.union(coverage.filter { it.source==Source.USAGE&&it.available }.mapNotNull { Span(it.startMs,it.endMs).intersect(Span(w.start,asOf)) }).sumOf { it.duration }
            if(app.isNotEmpty()&&covered<asOf-w.start) add("数字轨道显示已观察活动，可用查询范围未覆盖整个归档窗口。")
        }
        return DayView(date.toString(), zone.id, w.start, w.end, asOf,
            if (policy.paused && date == LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now), zone)) "PAUSED" else if (episodes.isEmpty()) "EMPTY" else if (warnings.isNotEmpty()) "PARTIAL" else if (now < w.end) "IN_PROGRESS" else "READY",
            if (episodes.isEmpty()) "一天正在留下痕迹" else "这一天，有迹可循", sentences.take(config.summaryMaxSentences), facts, metrics, episodes, coverage.filter { it.startMs < w.end && it.endMs > w.start }, warnings, revision = revision)
    }

    fun highlights(day: DayView): List<Episode> = day.episodes.sortedByDescending { e ->
        (if (e.isFavorite) 100 else 0) + (if (e.assertion == Assertion.CONFIRMED) 40 else 0) +
        (if (e.kind == "STAY") 20 else 0) + (if (e.endMs - e.startMs >= 1_200_000) 15 else 0) +
        (if (e.photoUris.isNotEmpty()) 15 else 0) - (if (e.kind == "NOTICE") 20 else 0) - (if (e.assertion == Assertion.UNKNOWN) 30 else 0)
    }.distinctBy { it.startMs / 3_600_000 to it.kind }.take(8).sortedBy { it.startMs }
}

data class Insight(val title: String, val detail: String)
fun insights(days: List<DayView>): List<Insight> {
    if (days.isEmpty()) return emptyList()
    val recentDate = LocalDate.parse(days.maxBy { it.dayDate }.dayDate)
    val out = mutableListOf<Insight>()
    for (weekend in listOf(false, true)) {
        fun valid(d: DayView): Boolean {
            val date = LocalDate.parse(d.dayDate)
            if ((date.dayOfWeek.value >= 6) != weekend) return false
            val span = Span(d.windowStartMs, minOf(d.asOfMs, d.windowEndMs))
            if (span.duration == 0L) return false
            val online = union(d.coverage.filter { it.source == Source.USAGE && it.available }.mapNotNull { Span(it.startMs, it.endMs).intersect(span) }).sumOf { it.duration }
            return online.toDouble() / span.duration >= .8
        }
        val recent = days.filter { valid(it) && LocalDate.parse(it.dayDate) >= recentDate.minusDays(6) }
        val base = days.filter { valid(it) && LocalDate.parse(it.dayDate) < recentDate.minusDays(6) && LocalDate.parse(it.dayDate) >= recentDate.minusDays(27) }
        if (base.size < 10 || recent.size < 4) continue
        fun evening(d: DayView): Long {
            val z = ZoneId.of(d.zoneId); val t = LocalDate.parse(d.dayDate).atTime(20, 0).atZone(z).toInstant().toEpochMilli()
            return union(d.episodes.filter { it.kind == "APP" && it.assertion != Assertion.UNKNOWN }.mapNotNull { Span(it.startMs, it.endMs).intersect(Span(t, d.windowEndMs)) }).sumOf { it.duration }
        }
        fun median(a: List<Long>): Long { val s = a.sorted(); return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 }
        val a = median(base.map(::evening)); val b = median(recent.map(::evening)); val delta = b - a
        if (kotlin.math.abs(delta) >= 1_800_000 || (a > 0 && kotlin.math.abs(delta).toDouble() / a >= .2)) out += Insight("晚间应用活动${if (delta > 0) "增加" else "减少"}", "${if (weekend) "周末" else "工作日"}：最近7天${recent.size}个有效日期中位数${durationText(b)}；此前${base.size}个有效日期${durationText(a)}。对应来源在线覆盖≥80%，不能推断变化原因。")
    }
    // Independent dimensions need their own source coverage, rather than borrowing usage coverage.
    for(source in listOf(Source.LOCATION,Source.MEDIA)) {
        fun covered(d:DayView):Boolean {
            val date=LocalDate.parse(d.dayDate)
            if(date.dayOfWeek.value>=6)return false
            val expected=Span(d.windowStartMs,minOf(d.asOfMs,d.windowEndMs));if(expected.duration<=0)return false
            val observed=union(d.coverage.filter { it.source==source&&it.available }.mapNotNull { Span(it.startMs,it.endMs).intersect(expected) }).sumOf { it.duration }
            return observed.toDouble()/expected.duration>=.8
        }
        val recent=days.filter { covered(it)&&LocalDate.parse(it.dayDate)>=recentDate.minusDays(6) }
        val base=days.filter { covered(it)&&LocalDate.parse(it.dayDate)<recentDate.minusDays(6)&&LocalDate.parse(it.dayDate)>=recentDate.minusDays(27) }
        if(base.size<10||recent.size<4)continue
        val anchor=if(source==Source.LOCATION)base.flatMap { it.episodes }.filter { it.kind=="STAY"&&it.lat!=null&&it.lon!=null }.maxByOrNull { it.endMs-it.startMs } else null
        fun value(d:DayView):Long? {
            val e=if(source==Source.LOCATION) {
                if(anchor==null)return null
                d.episodes.filter { it.kind=="STAY"&&it.lat!=null&&it.lon!=null&&distanceM(it.lat,it.lon,anchor.lat!!,anchor.lon!!)<=150 }.minByOrNull { it.startMs }
            } else d.episodes.filter { it.kind=="MEDIA"&&it.assertion==Assertion.RECORDED }.minByOrNull { it.startMs }
            return e?.let { java.time.Instant.ofEpochMilli(it.startMs).atZone(ZoneId.of(d.zoneId)).toLocalTime().toSecondOfDay()/60L }
        }
        val a=base.mapNotNull(::value).sorted();val b=recent.mapNotNull(::value).sorted()
        if(a.size<10||b.size<4)continue
        fun median(xs:List<Long>)=if(xs.size%2==1)xs[xs.size/2] else (xs[xs.size/2-1]+xs[xs.size/2])/2
        val av=median(a);val bv=median(b);val delta=bv-av
        if(kotlin.math.abs(delta)<30 && (av==0L||kotlin.math.abs(delta).toDouble()/av<.2))continue
        fun hhmm(minutes:Long)="%02d:%02d".format(minutes/60,minutes%60)
        val label=if(source==Source.LOCATION)"主要地点的停留开始时间" else "媒体播放的开始时段"
        out+=Insight("${label}${if(delta>0)"变晚" else "变早"}","工作日，${if(source==Source.LOCATION)anchor!!.title else "有媒体记录的日期"}：最近7天${b.size}个有效日期的中位时刻${hhmm(bv)}，此前${a.size}个有效日期${hhmm(av)}。近期四分位区间${hhmm(b[b.size/4])}—${hhmm(b[(b.size*3/4).coerceAtMost(b.lastIndex)])}。对应来源在线覆盖≥80%，无法据此推断原因。")
    }
    return out.take(3)
}
