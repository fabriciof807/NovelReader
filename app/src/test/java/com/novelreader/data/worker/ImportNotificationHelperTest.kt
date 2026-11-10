package com.novelreader.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.MainActivity
import com.novelreader.domain.usecase.ImportJobSpec
import com.novelreader.ui.navigation.DeepLinkToken
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImportNotificationHelperTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val deepLinkToken = DeepLinkToken(context)
    private val helper = ImportNotificationHelper(context, deepLinkToken)

    @Test
    fun `foreground notification has contentIntent when targetNovelId is set`() {
        val spec = ImportJobSpec(
            id = UUID.randomUUID(),
            novelTitle = "Test Novel",
            links = listOf("https://example.com/ch1"),
            chapterNumbers = listOf(1),
            coverUrl = null,
            enqueuedAt = 1000L,
            sourceUrl = "https://example.com",
            domain = "example.com",
            targetNovelId = 42L
        )
        val info = helper.createForegroundInfo(spec, 0, 10)
        val notification = info.notification
        val shadow = Shadows.shadowOf(notification.contentIntent)
        val intent: Intent = shadow.savedIntent

        assertThat(intent.getStringExtra(MainActivity.EXTRA_DEEP_LINK_ACTION))
            .isEqualTo(MainActivity.ACTION_OPEN_NOVEL)
        assertThat(intent.getLongExtra(MainActivity.EXTRA_NOVEL_ID, -1L))
            .isEqualTo(42L)
        assertThat(intent.getStringExtra(MainActivity.EXTRA_DEEP_LINK_TOKEN))
            .isEqualTo(deepLinkToken.value)
    }

    @Test
    fun `foreground notification has launch intent when targetNovelId is null`() {
        val spec = ImportJobSpec(
            id = UUID.randomUUID(),
            novelTitle = "Test Novel",
            links = listOf("https://example.com/ch1"),
            chapterNumbers = listOf(1),
            coverUrl = null,
            enqueuedAt = 1000L,
            sourceUrl = "https://example.com",
            domain = "example.com",
            targetNovelId = null
        )
        val info = helper.createForegroundInfo(spec, 0, 10)
        val shadow = Shadows.shadowOf(info.notification.contentIntent)
        val intent: Intent = shadow.savedIntent

        assertThat(intent.getStringExtra(MainActivity.EXTRA_DEEP_LINK_ACTION)).isNull()
    }

    @Test
    fun `completion notification with errors still reads as a completion`() {
        val spec = ImportJobSpec(
            id = UUID.randomUUID(),
            novelTitle = "Test Novel",
            links = listOf("https://example.com/ch1"),
            chapterNumbers = listOf(1),
            coverUrl = null,
            enqueuedAt = 1000L,
            sourceUrl = "https://example.com",
            domain = "example.com",
            targetNovelId = 42L
        )

        helper.postCompletionNotification(spec, importedCount = 98, total = 100, errorCount = 2)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = checkNotNull(
            Shadows.shadowOf(manager)
                .getNotification(ImportNotificationHelper.COMPLETION_NOTIFICATION_ID)
        )
        assertThat(posted.extras.getString(Notification.EXTRA_TITLE))
            .isEqualTo("Test Novel import complete")
        assertThat(posted.extras.getString(Notification.EXTRA_TEXT))
            .isEqualTo("98 chapters, 2 error(s)")
    }

    @Test
    fun `completion notification has contentIntent with ACTION_OPEN_NOVEL`() {
        val spec = ImportJobSpec(
            id = UUID.randomUUID(),
            novelTitle = "Test Novel",
            links = emptyList(),
            chapterNumbers = emptyList(),
            coverUrl = null,
            enqueuedAt = 1000L,
            sourceUrl = "https://example.com",
            domain = "example.com",
            targetNovelId = 42L
        )
        helper.postCompletionNotification(spec, 5, 10, 0)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = manager.getActiveNotifications()
        val notification = posted.first().notification
        val shadow = Shadows.shadowOf(notification.contentIntent)
        val intent: Intent = shadow.savedIntent

        assertThat(intent.getStringExtra(MainActivity.EXTRA_DEEP_LINK_ACTION))
            .isEqualTo(MainActivity.ACTION_OPEN_NOVEL)
    }

    @Test
    fun `error channel is created lazily by the failure notification with default importance and badge`() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertThat(manager.getNotificationChannel(ImportNotificationHelper.ERROR_CHANNEL_ID)).isNull()

        helper.postFailureNotification(spec())

        val channel = checkNotNull(manager.getNotificationChannel(ImportNotificationHelper.ERROR_CHANNEL_ID))
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
        assertThat(channel.canShowBadge()).isTrue()
        assertThat(channel.name.toString()).isEqualTo("Import failures")
        assertThat(channel.description).isEqualTo("Notifications for import failures")
    }

    @Test
    fun `error channel creation keeps an existing channel`() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val original = NotificationChannel(
            ImportNotificationHelper.ERROR_CHANNEL_ID,
            "Original",
            NotificationManager.IMPORTANCE_MIN
        )
        manager.createNotificationChannel(original)

        helper.ensureErrorChannel()

        val channel = checkNotNull(manager.getNotificationChannel(ImportNotificationHelper.ERROR_CHANNEL_ID))
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_MIN)
        assertThat(channel.name.toString()).isEqualTo("Original")
    }

    @Test
    fun `failure notification posts on the error channel`() {
        helper.postFailureNotification(spec())

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = checkNotNull(
            Shadows.shadowOf(manager).getNotification(ImportNotificationHelper.COMPLETION_NOTIFICATION_ID + 1)
        )
        assertThat(posted.channelId).isEqualTo(ImportNotificationHelper.ERROR_CHANNEL_ID)
        assertThat(posted.category).isEqualTo(Notification.CATEGORY_ERROR)
    }

    @Test
    fun `progress notification stays on the import channel`() {
        val info = helper.createForegroundInfo(spec(), 1, 10)

        assertThat(info.notification.channelId).isEqualTo(ImportNotificationHelper.CHANNEL_ID)
    }

    @Test
    fun `completion notification stays on the import channel`() {
        helper.postCompletionNotification(spec(), importedCount = 10, total = 10, errorCount = 0)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = checkNotNull(
            Shadows.shadowOf(manager).getNotification(ImportNotificationHelper.COMPLETION_NOTIFICATION_ID)
        )
        assertThat(posted.channelId).isEqualTo(ImportNotificationHelper.CHANNEL_ID)
    }

    @Test
    fun `completion notification with errors stays on the import channel`() {
        helper.postCompletionNotification(spec(), importedCount = 8, total = 10, errorCount = 2)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = checkNotNull(
            Shadows.shadowOf(manager).getNotification(ImportNotificationHelper.COMPLETION_NOTIFICATION_ID)
        )
        assertThat(posted.channelId).isEqualTo(ImportNotificationHelper.CHANNEL_ID)
        assertThat(manager.getNotificationChannel(ImportNotificationHelper.ERROR_CHANNEL_ID)).isNull()
    }

    @Test
    fun `no channel is created below sdk 26`() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val originalSdk = Build.VERSION.SDK_INT
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 25)
        try {
            helper.ensureChannel()
            helper.ensureErrorChannel()
            helper.postFailureNotification(spec())

            assertThat(manager.getNotificationChannel(ImportNotificationHelper.CHANNEL_ID)).isNull()
            assertThat(manager.getNotificationChannel(ImportNotificationHelper.ERROR_CHANNEL_ID)).isNull()
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", originalSdk)
        }
    }

    @Test
    fun `failure notification has contentIntent with ACTION_OPEN_FAILED_CHAPTERS`() {
        val spec = ImportJobSpec(
            id = UUID.randomUUID(),
            novelTitle = "Test Novel",
            links = emptyList(),
            chapterNumbers = emptyList(),
            coverUrl = null,
            enqueuedAt = 1000L,
            sourceUrl = "https://example.com",
            domain = "example.com",
            targetNovelId = 42L
        )
        helper.postFailureNotification(spec)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = manager.getActiveNotifications()
        val notification = posted.first().notification
        val shadow = Shadows.shadowOf(notification.contentIntent)
        val intent: Intent = shadow.savedIntent

        assertThat(intent.getStringExtra(MainActivity.EXTRA_DEEP_LINK_ACTION))
            .isEqualTo(MainActivity.ACTION_OPEN_FAILED_CHAPTERS)
        assertThat(intent.getStringExtra(MainActivity.EXTRA_DEEP_LINK_TOKEN))
            .isEqualTo(deepLinkToken.value)
    }

    private fun spec() = ImportJobSpec(
        id = UUID.randomUUID(),
        novelTitle = "Test Novel",
        links = emptyList(),
        chapterNumbers = emptyList(),
        coverUrl = null,
        enqueuedAt = 1000L,
        sourceUrl = "https://example.com",
        domain = "example.com",
        targetNovelId = 42L
    )
}
