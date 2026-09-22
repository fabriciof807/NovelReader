package com.novelreader.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.novelreader.MainActivity
import com.novelreader.R
import com.novelreader.ui.navigation.DeepLinkToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class NewChaptersUpdate(val novelTitle: String, val newChapterCount: Int)

@Singleton
class UpdateNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deepLinkToken: DeepLinkToken
) {
    companion object {
        const val CHANNEL_ID = "novel_updates_channel"
        const val NOTIFICATION_ID_BASE = 2000
        const val GROUP_VALUE = "novel_updates"
        const val SUMMARY_NOTIFICATION_ID = NOTIFICATION_ID_BASE - 1
    }

    fun postNewChaptersGroupSummary(updates: List<NewChaptersUpdate>) {
        ensureChannel()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (updates.size < 2) {
            manager.cancel(SUMMARY_NOTIFICATION_ID)
            return
        }

        val title = context.getString(R.string.update_notification_summary_title, updates.size)
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        updates.forEach { update ->
            style.addLine(
                context.getString(
                    R.string.update_notification_summary_line,
                    update.novelTitle,
                    update.newChapterCount
                )
            )
        }

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            SUMMARY_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setStyle(style)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_VALUE)
            .setGroupSummary(true)
            .build()

        manager.notify(SUMMARY_NOTIFICATION_ID, notification)
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.update_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.update_notification_channel_desc)
                    setShowBadge(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun postNewChaptersNotification(novelId: Long, novelTitle: String, newChapterCount: Int) {
        ensureChannel()

        val title = context.getString(R.string.update_notification_title, novelTitle)
        val text = context.getString(R.string.update_notification_text, newChapterCount)

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DEEP_LINK_ACTION, MainActivity.ACTION_OPEN_NOVEL)
            putExtra(MainActivity.EXTRA_NOVEL_ID, novelId)
            putExtra(MainActivity.EXTRA_DEEP_LINK_TOKEN, deepLinkToken.value)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            novelId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_VALUE)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_BASE + novelId.toInt(), notification)
    }

    fun postCloudflareReverifyNotification(novelId: Long, novelTitle: String) {
        ensureChannel()

        val title = context.getString(R.string.cloudflare_reverify_notif_title, novelTitle)
        val text = context.getString(R.string.cloudflare_reverify_notif_body)

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DEEP_LINK_ACTION, MainActivity.ACTION_OPEN_CLOUDFLARE_SOLVER)
            putExtra(MainActivity.EXTRA_NOVEL_ID, novelId)
            putExtra(MainActivity.EXTRA_DEEP_LINK_TOKEN, deepLinkToken.value)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (NOTIFICATION_ID_BASE + 1000 + novelId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_BASE + 1000 + novelId.toInt(), notification)
    }
}
