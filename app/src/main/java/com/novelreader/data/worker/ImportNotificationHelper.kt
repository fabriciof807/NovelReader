package com.novelreader.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.novelreader.R
import com.novelreader.domain.usecase.ImportJobSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "novel_import_channel"
        const val NOTIFICATION_ID_BASE = 1000
        const val COMPLETION_NOTIFICATION_ID = 9999
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.import_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.import_notification_channel_desc)
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun createForegroundInfo(
        spec: ImportJobSpec,
        progress: Int,
        total: Int,
        pendingInQueue: Int = 0
    ): ForegroundInfo {
        ensureChannel()
        val title = context.getString(R.string.import_notification_title, spec.novelTitle)
        val text = if (total > 0) {
            context.getString(R.string.import_notification_text, progress, total)
        } else {
            context.getString(R.string.import_notification_pending, pendingInQueue)
        }

        val cancelIntent = WorkManagerIntents.cancelIntent(context, spec.id)
        val cancelPending = PendingIntent.getBroadcast(
            context,
            spec.id.hashCode(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(total.coerceAtLeast(1), progress, total == 0)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.import_notification_cancel),
                cancelPending
            )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                notificationId(spec),
                builder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId(spec), builder.build())
        }
    }

    fun postCompletionNotification(
        spec: ImportJobSpec,
        importedCount: Int,
        total: Int,
        errorCount: Int
    ) {
        ensureChannel()
        val title = if (errorCount == 0) {
            context.getString(R.string.import_notification_complete, spec.novelTitle)
        } else {
            context.getString(R.string.import_notification_failed, spec.novelTitle)
        }
        val text = if (errorCount == 0) {
            context.getString(R.string.import_notification_complete_text, importedCount)
        } else {
            context.getString(R.string.import_notification_complete_with_errors, importedCount, errorCount)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(false)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setProgress(0, 0, false)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    fun postFailureNotification(spec: ImportJobSpec) {
        ensureChannel()
        val title = context.getString(R.string.import_notification_failed, spec.novelTitle)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setOngoing(false)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(COMPLETION_NOTIFICATION_ID + 1, notification)
    }

    fun notificationId(spec: ImportJobSpec): Int = NOTIFICATION_ID_BASE + spec.id.hashCode().rem(1000).let { if (it < 0) it + 1000 else it }
}