package com.novelreader.domain.usecase.webimport

import kotlin.random.Random

private const val DEFAULT_BASE_429_MS = 3_000L
private const val DEFAULT_BASE_5XX_MS = 2_000L
private const val DEFAULT_CAP_MS = 60_000L
private const val DEFAULT_JITTER_MS = 1_000L

fun nextRetryDelayMs(
    attempt: Int,
    statusCode: Int,
    baseMs: Long = if (statusCode == 429) DEFAULT_BASE_429_MS else DEFAULT_BASE_5XX_MS,
    jitter: Long = DEFAULT_JITTER_MS,
    capMs: Long = DEFAULT_CAP_MS,
    random: Random = Random.Default
): Long {
    if (statusCode == 429 || statusCode in 500..599) {
        if (attempt < 1) return 0L
        val shift = (attempt - 1).coerceAtMost(30)
        val raw = baseMs shl shift
        val capped = raw.coerceAtMost(capMs)
        if (jitter <= 0L) return capped
        return capped + random.nextLong(0, jitter + 1)
    }
    return 0L
}
