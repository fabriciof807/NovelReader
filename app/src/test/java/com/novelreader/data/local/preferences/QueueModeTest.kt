package com.novelreader.data.local.preferences

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QueueModeTest {

    @Test fun `fromString defaults to SEQUENTIAL for null and garbage`() {
        assertThat(QueueMode.fromString(null)).isEqualTo(QueueMode.SEQUENTIAL)
        assertThat(QueueMode.fromString("")).isEqualTo(QueueMode.SEQUENTIAL)
        assertThat(QueueMode.fromString("garbage")).isEqualTo(QueueMode.SEQUENTIAL)
    }

    @Test fun `fromString parses values case insensitively`() {
        assertThat(QueueMode.fromString("parallel")).isEqualTo(QueueMode.PARALLEL)
        assertThat(QueueMode.fromString("PARALLEL")).isEqualTo(QueueMode.PARALLEL)
        assertThat(QueueMode.fromString("Sequential")).isEqualTo(QueueMode.SEQUENTIAL)
    }

    @Test fun `enum has both expected entries`() {
        assertThat(QueueMode.entries).containsExactly(QueueMode.SEQUENTIAL, QueueMode.PARALLEL)
    }
}
