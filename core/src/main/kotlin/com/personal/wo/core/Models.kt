package com.personal.wo.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import java.security.MessageDigest

val WoJson = Json { ignoreUnknownKeys = false; encodeDefaults = true }

@Serializable enum class Source { USAGE, LOCATION, STEPS, NOTIFICATION, MEDIA, PHOTO, CALENDAR, HEALTH, ACCESSIBILITY }
@Serializable enum class Kind { RESUMED, PAUSED, STOPPED, SCREEN_ON, SCREEN_OFF, LOCK, UNLOCK, BOOT, SHUTDOWN, WINDOW, POSITION, STEP_DELTA, NOTICE, MEDIA_PLAY, MEDIA_PAUSE, PHOTO, CALENDAR, HEALTH_STEPS, EXERCISE, SLEEP }
@Serializable enum class Lane { PLACE, ACTIVITY, DIGITAL, LIFE }
@Serializable enum class Assertion { RECORDED, INFERRED, CONFIRMED, PLANNED, UNKNOWN }

@Serializable data class Payload(
    val packageName: String = "", val className: String = "", val label: String = "",
    val text: String = "", val category: String = "", val uri: String = "",
    val lat: Double? = null, val lon: Double? = null, val accuracyM: Double? = null,
    val provider: String = "", val coordinateSystem: String = "WGS84", val mock: Boolean = false,
    val steps: Long? = null, val sessionKey: String = "", val captured: Boolean = false,
    val allDay: Boolean = false, val sleepMinutes: Long? = null
)
@Serializable data class Observation(
    val id: String, val source: Source, val sourceKey: String, val kind: Kind,
    val startMs: Long, val endMs: Long = startMs, val receivedMs: Long = startMs,
    val eventZoneId: String = "Asia/Hong_Kong", val bootId: String = "",
    val elapsedRealtimeNs: Long? = null, val quality: List<String> = emptyList(),
    val payload: Payload = Payload(), val schemaVersion: Int = 1,val deviceId:String=""
)
@Serializable data class Exclusion(
    val id: String, val startMs: Long = 0, val endMs: Long? = null,
    val source: Source? = null, val packageName: String? = null,
    val sourceKey: String? = null, val reason: String = "PAUSE"
)
@Serializable data class Policy(
    val requested: Boolean = false, val paused: Boolean = false, val generation: Long = 0,
    val archiveZone: String = ZoneId.systemDefault().id, val startedMs: Long? = null,
    val enabledSources: Set<Source> = Source.entries.toSet(),
    val noticeApps: Set<String> = emptySet(), val excludedApps: Set<String> = emptySet(),
    val lockUi: Boolean = false, val lastAliveMs: Long = 0,
    val exclusions: List<Exclusion> = emptyList()
)
@Serializable data class Place(val id: String, val name: String, val lat: Double, val lon: Double, val radiusM: Double = 150.0)
@Serializable data class Evidence(val id: String, val source: Source, val sourceKey: String, val description: String, val rawAvailable: Boolean = true)
@Serializable data class Episode(
    val id: String, val revision: Long = 1, val startMs: Long, val endMs: Long,
    val lane: Lane, val kind: String, val assertion: Assertion, val title: String,
    val detail: String = "", val evidence: List<Evidence> = emptyList(),
    val qualityFlags: List<String> = emptyList(), val isFavorite: Boolean = false,
    val lat: Double? = null, val lon: Double? = null, val photoUris: List<String> = emptyList(),
    val packageName: String = "", val amount: Long? = null
)
@Serializable data class Annotation(val id: String, val episodeId: String, val expectedRevision: Long, val title: String? = null, val startMs: Long? = null, val endMs: Long? = null, val favorite: Boolean? = null, val confirmed: Boolean = false)
@Serializable data class Metric(val key: String, val value: Long?, val unit: String, val label: String, val evidenceIds: List<String> = emptyList())
@Serializable data class Sentence(val text: String, val factIds: List<String> = emptyList())
@Serializable data class Fact(val id: String, val type: String, val displayValue: String, val sourceRefs: List<String>, val assertion: Assertion)
@Serializable data class Coverage(val source: Source, val startMs: Long, val endMs: Long, val available: Boolean, val detail: String = "")
@Serializable data class SourceState(val source: Source, val supported: Boolean = true, val authorization: String = "DENIED", val runtime: String = "STOPPED", val lastSuccessMs: Long? = null, val detail: String = "", val cursor: String = "", val freshness: String = "NO_DATA")
@Serializable data class DayView(
    val dayDate: String, val zoneId: String, val windowStartMs: Long, val windowEndMs: Long,
    val asOfMs: Long, val status: String, val headline: String, val sentences: List<Sentence>,
    val facts: List<Fact>, val metrics: List<Metric>, val episodes: List<Episode>,
    val coverage: List<Coverage> = emptyList(), val warnings: List<String> = emptyList(),
    val schemaVersion: Int = 1, val revision: Long = 1
)
@Serializable data class EngineConfig(
    val version: String = "wo-engine-1.0.0", val stayMinPoints: Int = 3,
    val stayMinMinutes: Int = 10, val stayMaxGapMinutes: Int = 20,
    val stayRadiusMinM: Double = 80.0, val stayRadiusMaxM: Double = 300.0,
    val routeMaxLinkGapMinutes: Int = 5, val gnssBurstSeconds: Int = 20,
    val gnssCooldownMinutes: Int = 10, val gnssHourlySeconds: Int = 120,
    val gnssDailySeconds: Int = 600, val lowBattery: Int = 20,
    val passiveBattery: Int = 10, val recoveryBattery: Int = 25,
    val rawRetentionDays: Int = 30, val noticeTextDays: Int = 7,
    val queueCapacity: Int = 512, val pageSize: Int = 200, val summaryMaxSentences: Int = 4,
    val candidateMinDistanceM:Float=0f,val stayMinDistanceM:Float=0f,
    val databaseSoftLimitMB:Int=800,val episodeRetentionDays:Int=730
) {
    fun checked(): EngineConfig = if (stayMinPoints in 3..20 && stayMinMinutes in 5..60 && stayMaxGapMinutes in 5..60 && stayRadiusMaxM in 80.0..300.0 && gnssBurstSeconds in 1..20 && gnssHourlySeconds in 20..120 && gnssDailySeconds in 120..600 && pageSize in 1..200 && queueCapacity in 32..512) this else EngineConfig()
}

fun window(date: LocalDate, zone: ZoneId): Span = Span(date.atStartOfDay(zone).toInstant().toEpochMilli(), date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
fun stableId(vararg values: Any?): String = MessageDigest.getInstance("SHA-256").digest(values.joinToString("\u0000").toByteArray()).copyOf(16).lowerHex()
fun durationText(ms: Long): String {
    val m = (ms.coerceAtLeast(0) + 30_000) / 60_000
    return if (m >= 60) "${m / 60}小时${if (m % 60 > 0) "${m % 60}分钟" else ""}" else "${m}分钟"
}

/** Lowercase byte encoding without allocating a Formatter for every byte. */
fun ByteArray.lowerHex():String {
    val digits="0123456789abcdef"
    return CharArray(size*2) { index ->
        val byte=this[index/2].toInt() and 255
        digits[if(index%2==0)byte ushr 4 else byte and 15]
    }.concatToString()
}
