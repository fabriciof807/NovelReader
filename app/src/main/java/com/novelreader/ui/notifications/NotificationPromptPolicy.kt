package com.novelreader.ui.notifications

enum class NotificationPromptKind { REQUEST, OPEN_SETTINGS }

object NotificationPromptPolicy {
    fun kind(
        sdkAtLeast33: Boolean,
        notificationsGranted: Boolean,
        alreadyAsked: Boolean,
        suppressedThisSession: Boolean
    ): NotificationPromptKind? = when {
        !sdkAtLeast33 -> null
        notificationsGranted -> null
        suppressedThisSession -> null
        alreadyAsked -> NotificationPromptKind.OPEN_SETTINGS
        else -> NotificationPromptKind.REQUEST
    }
}
