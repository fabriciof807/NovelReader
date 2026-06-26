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
class AppPreferencesDynamicColorTest {

    @Test
    fun `dynamicColorEnabled defaults to true`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = AppPreferences(context)
        assertThat(prefs.dynamicColorEnabled.first()).isTrue()
    }

    @Test
    fun `updateDynamicColorEnabled false persists`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = AppPreferences(context)
        prefs.updateDynamicColorEnabled(false)
        assertThat(prefs.dynamicColorEnabled.first()).isFalse()
        prefs.updateDynamicColorEnabled(true)
    }
}
