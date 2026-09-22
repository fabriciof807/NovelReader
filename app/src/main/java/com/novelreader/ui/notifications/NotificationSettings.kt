package com.novelreader.ui.notifications

import android.content.Intent
import android.provider.Settings

object NotificationSettings {
    fun intentFor(packageName: String): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
}
