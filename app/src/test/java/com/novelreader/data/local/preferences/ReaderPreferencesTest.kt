package com.novelreader.data.local.preferences

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReaderPreferencesTest {

    @Test
    fun `keepScreenOn defaults to true`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ReaderPreferences(context)
        assertThat(prefs.config.first().keepScreenOn).isTrue()
    }

    @Test
    fun `updateKeepScreenOn false persists`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ReaderPreferences(context)
        prefs.updateKeepScreenOn(false)
        assertThat(prefs.config.first().keepScreenOn).isFalse()
        prefs.updateKeepScreenOn(true)
        assertThat(prefs.config.first().keepScreenOn).isTrue()
    }
}
