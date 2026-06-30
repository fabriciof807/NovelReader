package com.novelreader.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StringUtilsTest {

    @Test fun `hostMatchesDomain handles www and bare-host variants`() {
        assertThat(StringUtils.hostMatchesDomain("www.readfullnovel.com", "readfullnovel.com")).isTrue()
        assertThat(StringUtils.hostMatchesDomain("readfullnovel.com", "www.readfullnovel.com")).isTrue()
        assertThat(StringUtils.hostMatchesDomain("freewebnovel.com", "freewebnovel.com")).isTrue()
    }

    @Test fun `hostMatchesDomain is case-insensitive`() {
        assertThat(StringUtils.hostMatchesDomain("WWW.FreeWebNovel.com", "freewebnovel.com")).isTrue()
    }

    @Test fun `hostMatchesDomain rejects unrelated domains`() {
        assertThat(StringUtils.hostMatchesDomain("freewebnovel.com", "readfullnovel.com")).isFalse()
        assertThat(StringUtils.hostMatchesDomain("completely-other.com", "freewebnovel.com")).isFalse()
    }

    @Test fun `hostMatchesDomain rejects suffix-spoofed domains`() {
        assertThat(StringUtils.hostMatchesDomain("maliciousfreewebnovel.com", "freewebnovel.com")).isFalse()
        assertThat(StringUtils.hostMatchesDomain("freewebnovel.com.evil.com", "freewebnovel.com")).isFalse()
    }
}
