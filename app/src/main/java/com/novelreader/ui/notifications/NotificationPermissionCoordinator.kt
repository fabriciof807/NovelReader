package com.novelreader.ui.notifications

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.novelreader.data.local.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationPermissionCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {
    private val _prompt = MutableStateFlow<NotificationPromptKind?>(null)
    val prompt: StateFlow<NotificationPromptKind?> = _prompt.asStateFlow()

    private var suppressedThisSession = false

    suspend fun onUserInitiatedBackgroundImport() {
        val kind = NotificationPromptPolicy.kind(
            sdkAtLeast33 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            notificationsGranted = notificationsGranted(),
            alreadyAsked = appPreferences.notificationPermissionAsked.first(),
            suppressedThisSession = suppressedThisSession
        )
        if (kind != null) _prompt.value = kind
    }

    fun onDismissed() {
        suppressedThisSession = true
        _prompt.value = null
    }

    suspend fun onSystemPromptLaunched() {
        suppressedThisSession = true
        appPreferences.updateNotificationPermissionAsked(true)
        _prompt.value = null
    }

    private fun notificationsGranted(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
