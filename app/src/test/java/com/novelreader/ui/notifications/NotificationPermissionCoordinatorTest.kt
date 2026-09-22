package com.novelreader.ui.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.data.local.preferences.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationPermissionCoordinatorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var appPreferences: AppPreferences
    private lateinit var coordinator: NotificationPermissionCoordinator

    @Before
    fun setUp() {
        setNotificationsEnabled(false)
        appPreferences = AppPreferences(context)
        runBlocking { appPreferences.updateNotificationPermissionAsked(false) }
        coordinator = NotificationPermissionCoordinator(context, appPreferences)
    }

    @After
    fun tearDown() = runTest {
        appPreferences.updateNotificationPermissionAsked(false)
    }

    @Test
    fun `the first user import publishes the system prompt request`() = runTest {
        coordinator.onUserInitiatedBackgroundImport()

        assertThat(coordinator.prompt.value).isEqualTo(NotificationPromptKind.REQUEST)
    }

    @Test
    fun `an earlier ask publishes the settings offer instead`() = runTest {
        appPreferences.updateNotificationPermissionAsked(true)

        coordinator.onUserInitiatedBackgroundImport()

        assertThat(coordinator.prompt.value).isEqualTo(NotificationPromptKind.OPEN_SETTINGS)
    }

    @Test
    fun `granted notifications publish nothing`() = runTest {
        setNotificationsEnabled(true)

        coordinator.onUserInitiatedBackgroundImport()

        assertThat(coordinator.prompt.value).isNull()
    }

    @Test
    @Config(sdk = [32])
    fun `below Android 13 nothing is published`() = runTest {
        coordinator.onUserInitiatedBackgroundImport()

        assertThat(coordinator.prompt.value).isNull()
    }

    @Test
    fun `dismissing silences the next import in the same session`() = runTest {
        coordinator.onUserInitiatedBackgroundImport()
        coordinator.onDismissed()

        coordinator.onUserInitiatedBackgroundImport()

        assertThat(coordinator.prompt.value).isNull()
    }

    @Test
    fun `launching the system prompt closes the dialog and records the ask`() = runTest {
        coordinator.onUserInitiatedBackgroundImport()

        coordinator.onSystemPromptLaunched()

        assertThat(coordinator.prompt.value).isNull()
        assertThat(appPreferences.notificationPermissionAsked.first()).isTrue()
    }

    @Test
    fun `a later session after a denial offers the settings`() = runTest {
        coordinator.onUserInitiatedBackgroundImport()
        coordinator.onSystemPromptLaunched()

        val nextSession = NotificationPermissionCoordinator(context, appPreferences)
        nextSession.onUserInitiatedBackgroundImport()

        assertThat(nextSession.prompt.value).isEqualTo(NotificationPromptKind.OPEN_SETTINGS)
    }

    private fun setNotificationsEnabled(enabled: Boolean) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Shadows.shadowOf(manager).setNotificationsEnabled(enabled)
    }
}
