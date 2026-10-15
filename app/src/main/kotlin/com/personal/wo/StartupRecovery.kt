package com.personal.wo

import com.personal.wo.core.Policy
import com.personal.wo.core.Source
import com.personal.wo.core.SourceState
import kotlinx.coroutines.CancellationException

// Handle optional platform/linker failures while preserving structured cancellation.
// VM failures such as OutOfMemoryError remain fatal instead of being hidden.
internal inline fun <T> recoverableCall(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
} catch (e: LinkageError) {
    Result.failure(e)
}

internal suspend fun <T> recoverableTask(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
} catch (e: LinkageError) {
    Result.failure(e)
}

internal suspend fun probeSource(
    source: Source,
    policy: Policy,
    probe: suspend () -> SourceState
): SourceState {
    if (source !in policy.enabledSources) return SourceState(source, runtime="STOPPED", detail="本人已关闭此来源")
    return recoverableTask(probe).getOrElse {
        SourceState(source, supported=it !is LinkageError && it !is UnsupportedOperationException, authorization="UNKNOWN", runtime="STOPPED",
            detail="系统暂不能查询此来源（${it.javaClass.simpleName}）；其他来源可继续使用")
    }
}

internal fun failureDetails(error: Throwable): String = buildString {
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = error
    var depth = 0
    while (current != null && seen.add(current) && depth++ < 5) {
        appendLine(current.javaClass.name)
        // A linker message contains class/symbol/library information, not collected records.
        if (current is LinkageError) appendLine(current.message.orEmpty().take(600))
        current.stackTrace.take(8).forEach { appendLine("  at $it") }
        current = current.cause
    }
}.take(8000)
