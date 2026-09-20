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
    fun `swipeDirection defaults to vertical`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ReaderPreferences(context)
        assertThat(prefs.config.first().swipeDirection).isEqualTo("vertical")
    }

    @Test
    fun `updateSwipeDirection persists all four valid values`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ReaderPreferences(context)
        for (dir in listOf("vertical", "horizontal", "both", "none")) {
            prefs.updateSwipeDirection(dir)
            assertThat(prefs.config.first().swipeDirection).isEqualTo(dir)
        }
    }

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

    @Test
    fun `theme defaults to auto so the reader can follow the app theme`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ReaderPreferences(context)
        assertThat(prefs.config.first().theme).isEqualTo("auto")
    }

    @Test
    fun `updateFontFamily persists an allowlisted family`() = runTest {        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ReaderPreferences(context)
        prefs.updateFontFamily("monospace")
        assertThat(prefs.config.first().fontFamily).isEqualTo("monospace")
    }

    @Test
    fun `updateFontFamily stores the default for a non-allowlisted family`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ReaderPreferences(context)
        prefs.updateFontFamily("serif;} </style><script>alert(1)</script><style>a{")
        assertThat(prefs.config.first().fontFamily).isEqualTo("serif")
    }

    @Test
    fun `brightness defaults to following the device`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ReaderPreferences(context)
        assertThat(prefs.config.first().brightness)
            .isEqualTo(PreferenceAllowlists.BRIGHTNESS_SYSTEM)
    }

    @Test
    fun `updateBrightness persists a fixed level and can return to the system`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ReaderPreferences(context)
        prefs.updateBrightness(35)
        assertThat(prefs.config.first().brightness).isEqualTo(35)
        prefs.updateBrightness(PreferenceAllowlists.BRIGHTNESS_SYSTEM)
        assertThat(prefs.config.first().brightness)
            .isEqualTo(PreferenceAllowlists.BRIGHTNESS_SYSTEM)
    }

    @Test
    fun `updateBrightness refuses an unusable level rather than forcing an override`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ReaderPreferences(context)
        prefs.updateBrightness(0)
        assertThat(prefs.config.first().brightness)
            .isEqualTo(PreferenceAllowlists.BRIGHTNESS_SYSTEM)
    }

    @Test
    fun `tap zones default to off so a tap keeps toggling the controls`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ReaderPreferences(context)
        assertThat(prefs.config.first().tapZones).isFalse()
    }

    @Test
    fun `updateTapZones persists the choice`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ReaderPreferences(context)
        prefs.updateTapZones(true)
        assertThat(prefs.config.first().tapZones).isTrue()
        prefs.updateTapZones(false)
        assertThat(prefs.config.first().tapZones).isFalse()
    }
}
