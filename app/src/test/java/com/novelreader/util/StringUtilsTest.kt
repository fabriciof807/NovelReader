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

    @Test fun `hostMatchesDomain ignores port and trailing dot`() {
        assertThat(StringUtils.hostMatchesDomain("freewebnovel.com:443", "freewebnovel.com")).isTrue()
        assertThat(StringUtils.hostMatchesDomain("FreeWebNovel.com.", "freewebnovel.com")).isTrue()
        assertThat(StringUtils.hostMatchesDomain("m.freewebnovel.com:8080", "freewebnovel.com")).isTrue()
    }

    @Test fun `hostMatchesDomain requires exact equality for bracketed IPv6 literals`() {
        assertThat(StringUtils.hostMatchesDomain("[2001:4860:4860::8888]", "[2001:4860:4860::8888]")).isTrue()
        assertThat(StringUtils.hostMatchesDomain("[2001:4860:4860::8888]", "[2001:4860:4860::8844]")).isFalse()
        assertThat(StringUtils.hostMatchesDomain("[2001:4860:4860::8888]", "freewebnovel.com")).isFalse()
    }
}
