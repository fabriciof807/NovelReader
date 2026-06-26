package com.novelreader.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RelativeTimeTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `now returns agora`() {
        assertThat(formatRelativeTime(now, now)).isEqualTo("agora")
    }

    @Test
    fun `30 seconds ago returns agora`() {
        assertThat(formatRelativeTime(now - 30_000L, now)).isEqualTo("agora")
    }

    @Test
    fun `5 minutes ago returns ha 5 min`() {
        assertThat(formatRelativeTime(now - 5 * 60_000L, now)).isEqualTo("há 5 min")
    }

    @Test
    fun `2 hours ago returns ha 2 h`() {
        assertThat(formatRelativeTime(now - 2 * 3_600_000L, now)).isEqualTo("há 2 h")
    }

    @Test
    fun `3 days ago returns ha 3 dias`() {
        assertThat(formatRelativeTime(now - 3 * 86_400_000L, now)).isEqualTo("há 3 dias")
    }

    @Test
    fun `60 days ago returns null`() {
        assertThat(formatRelativeTime(now - 60L * 86_400_000L, now)).isNull()
    }

    @Test
    fun `negative timestamp returns null`() {
        assertThat(formatRelativeTime(-1L, now)).isNull()
    }

    @Test
    fun `future timestamp returns null`() {
        assertThat(formatRelativeTime(now + 60_000L, now)).isNull()
    }
}
