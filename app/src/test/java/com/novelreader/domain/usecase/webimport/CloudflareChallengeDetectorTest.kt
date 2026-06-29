package com.novelreader.domain.usecase.webimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CloudflareChallengeDetectorTest {

    @Test
    fun returnsTrueWhenBodyContainsJustAMoment() {
        assertThat(isCloudflareChallenge(403, "<html><title>Just a moment...</title></html>", emptyMap())).isTrue()
    }

    @Test
    fun returnsTrueWhenBodyContainsChallengePlatform() {
        assertThat(isCloudflareChallenge(403, "<html>cf-chl-bypass challenge-platform ...</html>", emptyMap())).isTrue()
    }

    @Test
    fun returnsTrueWhenCfMitigatedHeaderPresent() {
        assertThat(isCloudflareChallenge(403, "<html>some html</html>", mapOf("cf-mitigated" to "challenge"))).isTrue()
    }

    @Test
    fun returnsTrueOn200WithChallengeBody() {
        assertThat(isCloudflareChallenge(200, "<html><title>Just a moment...</title></html>", emptyMap())).isTrue()
    }

    @Test
    fun returnsTrueOn200WithCfMitigatedHeader() {
        assertThat(isCloudflareChallenge(200, "<html>some html</html>", mapOf("cf-mitigated" to "challenge"))).isTrue()
    }

    @Test
    fun returnsFalseOn200WithPlainContent() {
        assertThat(isCloudflareChallenge(200, "<html><h1>Real novel content</h1><p>chapter links here</p></html>", emptyMap())).isFalse()
    }

    @Test
    fun returnsFalseOnPlain404() {
        assertThat(isCloudflareChallenge(404, "<html>not found</html>", emptyMap())).isFalse()
    }

    @Test
    fun returnsTrueOn429WithChallengeBody() {
        assertThat(isCloudflareChallenge(429, "<html>Just a moment...</html>", emptyMap())).isTrue()
    }

    @Test
    fun returnsFalseOn403WithPlainBody() {
        assertThat(isCloudflareChallenge(403, "<html>Forbidden</html>", emptyMap())).isFalse()
    }

    @Test
    fun returnsFalseWhenBodyAndHeadersAreEmpty() {
        assertThat(isCloudflareChallenge(403, null, emptyMap())).isFalse()
    }
}
