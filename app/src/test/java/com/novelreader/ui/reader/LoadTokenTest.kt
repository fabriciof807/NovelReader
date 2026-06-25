package com.novelreader.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LoadTokenTest {

    @Test
    fun `fresh token rejects any observed value`() {
        val token = LoadToken()
        assertThat(token.shouldAccept(0)).isFalse()
    }

    @Test
    fun `next returns a new token and shouldAccept matches it`() {
        val token = LoadToken()
        val issued = token.next()
        assertThat(token.shouldAccept(issued)).isTrue()
        assertThat(token.shouldAccept(issued - 1)).isFalse()
    }

    @Test
    fun `stale onPageFinished from a previous load is rejected after next`() {
        val token = LoadToken()
        val first = token.next()
        token.shouldAccept(first)
        val second = token.next()
        assertThat(token.shouldAccept(first)).isFalse()
        assertThat(token.shouldAccept(second)).isTrue()
    }
}
