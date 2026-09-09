package com.novelreader.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CloudflareChallengePolicyTest {

    @Test
    fun `allows the expected host and its subdomains over https`() {
        assertThat(
            CloudflareChallengePolicy.isAllowed("https://freewebnovel.com/challenge", "freewebnovel.com")
        ).isTrue()
        assertThat(
            CloudflareChallengePolicy.isAllowed("https://www.freewebnovel.com/cdn-cgi/challenge", "freewebnovel.com")
        ).isTrue()
    }

    @Test
    fun `rejects a different host`() {
        assertThat(
            CloudflareChallengePolicy.isAllowed("https://evil.example/challenge", "freewebnovel.com")
        ).isFalse()
    }

    @Test
    fun `rejects a suffix-spoofed host`() {
        assertThat(
            CloudflareChallengePolicy.isAllowed("https://freewebnovel.com.evil.io/challenge", "freewebnovel.com")
        ).isFalse()
    }

    @Test
    fun `rejects cleartext urls`() {
        assertThat(
            CloudflareChallengePolicy.isAllowed("http://freewebnovel.com/challenge", "freewebnovel.com")
        ).isFalse()
    }

    @Test
    fun `rejects blank and malformed urls`() {
        assertThat(CloudflareChallengePolicy.isAllowed("", "freewebnovel.com")).isFalse()
        assertThat(CloudflareChallengePolicy.isAllowed("not a url", "freewebnovel.com")).isFalse()
        assertThat(CloudflareChallengePolicy.isAllowed("https://freewebnovel.com/x", "")).isFalse()
    }
}
