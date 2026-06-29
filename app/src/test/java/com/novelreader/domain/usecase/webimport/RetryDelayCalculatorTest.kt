package com.novelreader.domain.usecase.webimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RetryDelayCalculatorTest {

    @Test
    fun nextRetryDelayMs_returnsZeroForOther4xx() {
        assertThat(nextRetryDelayMs(1, 403, jitter = 0L)).isEqualTo(0L)
        assertThat(nextRetryDelayMs(2, 404, jitter = 0L)).isEqualTo(0L)
    }

    @Test
    fun nextRetryDelayMs_doublesBaseForEachAttemptOn429() {
        val base = 3000L
        assertThat(nextRetryDelayMs(1, 429, baseMs = base, jitter = 0L)).isEqualTo(3000L)
        assertThat(nextRetryDelayMs(2, 429, baseMs = base, jitter = 0L)).isEqualTo(6000L)
        assertThat(nextRetryDelayMs(3, 429, baseMs = base, jitter = 0L)).isEqualTo(12000L)
        assertThat(nextRetryDelayMs(4, 429, baseMs = base, jitter = 0L)).isEqualTo(24000L)
    }

    @Test
    fun nextRetryDelayMs_doublesBaseForEachAttemptOn5xx() {
        val base = 2000L
        assertThat(nextRetryDelayMs(1, 500, baseMs = base, jitter = 0L)).isEqualTo(2000L)
        assertThat(nextRetryDelayMs(2, 502, baseMs = base, jitter = 0L)).isEqualTo(4000L)
        assertThat(nextRetryDelayMs(3, 503, baseMs = base, jitter = 0L)).isEqualTo(8000L)
    }

    @Test
    fun nextRetryDelayMs_capsAtCapMs() {
        val base = 3000L
        assertThat(nextRetryDelayMs(20, 429, baseMs = base, jitter = 0L, capMs = 60_000L)).isEqualTo(60_000L)
    }

    @Test
    fun nextRetryDelayMs_addsJitterWithinRange() {
        for (i in 1..20) {
            val delay = nextRetryDelayMs(2, 429, baseMs = 3000L, jitter = 1000L)
            assertThat(delay).isAtLeast(6000L)
            assertThat(delay).isAtMost(7000L)
        }
    }

    @Test
    fun nextRetryDelayMs_returnsZeroFor2xx() {
        assertThat(nextRetryDelayMs(1, 200, jitter = 0L)).isEqualTo(0L)
    }
}
