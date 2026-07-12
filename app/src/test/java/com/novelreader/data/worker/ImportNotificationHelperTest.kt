package com.novelreader.data.worker

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.MainActivity
import com.novelreader.domain.usecase.ImportJobSpec
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImportNotificationHelperTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val helper = ImportNotificationHelper(context)

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
    }
}
