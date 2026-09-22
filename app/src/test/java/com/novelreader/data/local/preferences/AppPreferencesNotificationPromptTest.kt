package com.novelreader.data.local.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppPreferencesNotificationPromptTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = AppPreferences(context)

    @After
    fun reset() = runTest {
        prefs.updateNotificationPermissionAsked(false)
    }

    @Test
    fun `notificationPermissionAsked defaults to false`() = runTest {
        assertThat(prefs.notificationPermissionAsked.first()).isFalse()
    }

    @Test
    fun `updateNotificationPermissionAsked true persists`() = runTest {
        prefs.updateNotificationPermissionAsked(true)

        assertThat(prefs.notificationPermissionAsked.first()).isTrue()
    }
}
