package com.novelreader.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LoadTokenTest {

    @Test
    fun `stale callback URL is rejected without consuming current load`() {
        val token = LoadToken()
        val first = token.next()
        val second = token.next()

        assertThat(token.shouldAccept(token.baseUrl(first))).isFalse()
        assertThat(token.shouldAccept(token.baseUrl(second))).isTrue()
    }

    @Test
    fun `malformed callback URL is rejected`() {
        assertThat(LoadToken().shouldAccept("about:blank")).isFalse()
    }

    @Test
    fun `malformed numeric callback URL is rejected`() {
        val token = LoadToken()
        token.next()

        assertThat(token.shouldAccept("1/")).isFalse()
    }
}
