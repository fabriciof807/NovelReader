package com.novelreader.util

import android.text.format.DateUtils
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RelativeTimeTest {

    private val now = 1_700_000_000_000L
    private var originalLocale: Locale? = null

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        ApplicationProvider.getApplicationContext<android.content.Context>()
    }

    @After
    fun tearDown() {
        originalLocale?.let { Locale.setDefault(it) }
    }

    @Test
    fun `now returns non-empty string`() {
        Locale.setDefault(Locale.US)
        val result = formatRelativeTime(now, now)
        assertThat(result).isNotNull()
        assertThat(result).isNotEmpty()
    }

    @Test
    fun `30 seconds ago returns non-empty string`() {
        Locale.setDefault(Locale.US)
        val result = formatRelativeTime(now - 30_000L, now)
        assertThat(result).isNotNull()
        assertThat(result).isNotEmpty()
    }

    @Test
    fun `5 minutes ago in en contains the number and minutes word`() {
        Locale.setDefault(Locale.US)
        val result = formatRelativeTime(now - 5 * 60_000L, now)
        assertThat(result).isNotNull()
        assertThat(result!!).contains("5")
        assertThat(result.lowercase()).contains("min")
    }

    @Test
    fun `2 hours ago in en returns a non-empty string containing the number`() {
        Locale.setDefault(Locale.US)
        val result = formatRelativeTime(now - 2L * 3_600_000L, now)
        assertThat(result).isNotNull()
        assertThat(result).isNotEmpty()
        assertThat(result!!).contains("2")
    }

    @Test
    fun `3 days ago in en contains the number and days word`() {
        Locale.setDefault(Locale.US)
        val result = formatRelativeTime(now - 3L * 86_400_000L, now)
        assertThat(result).isNotNull()
        assertThat(result!!).contains("3")
        assertThat(result.lowercase()).contains("day")
    }

    @Test
    fun `5 minutes ago in pt-BR returns a non-empty string containing the number`() {
        Locale.setDefault(Locale("pt", "BR"))
        val result = formatRelativeTime(now - 5 * 60_000L, now)
        assertThat(result).isNotNull()
        assertThat(result).isNotEmpty()
        assertThat(result!!).contains("5")
    }

    @Test
    fun `60 days ago returns a non-null string from DateUtils`() {
        Locale.setDefault(Locale.US)
        val result = formatRelativeTime(now - 60L * 86_400_000L, now)
        assertThat(result).isNotNull()
        assertThat(result).isNotEmpty()
    }

    @Test
    fun `non-positive timestamp returns null`() {
        assertThat(formatRelativeTime(0L, now)).isNull()
        assertThat(formatRelativeTime(-1L, now)).isNull()
    }

    @Test
    fun `future timestamp returns null`() {
        assertThat(formatRelativeTime(now + 60_000L, now)).isNull()
    }
}
