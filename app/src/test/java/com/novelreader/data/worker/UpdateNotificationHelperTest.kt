package com.novelreader.data.worker

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.MainActivity
import com.novelreader.ui.navigation.DeepLinkToken
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdateNotificationHelperTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val helper = UpdateNotificationHelper(context, DeepLinkToken(context))

    private val manager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun posted(id: Int) = Shadows.shadowOf(manager).getNotification(id)

    private fun childFor(novelId: Long) = posted(UpdateNotificationHelper.NOTIFICATION_ID_BASE + novelId.toInt())

    private fun summary() = posted(UpdateNotificationHelper.SUMMARY_NOTIFICATION_ID)

    @Test
    fun `a single novel gets its own notification and no summary`() {
        helper.postNewChaptersNotification(novelId = 1L, novelTitle = "One", newChapterCount = 3)

        helper.postNewChaptersGroupSummary(listOf(NewChaptersUpdate("One", 3)))

        assertThat(childFor(1L)).isNotNull()
        assertThat(summary()).isNull()
    }

    @Test
    fun `two novels post a summary naming the count`() {
        helper.postNewChaptersNotification(1L, "One", 3)
        helper.postNewChaptersNotification(2L, "Two", 5)

        helper.postNewChaptersGroupSummary(
            listOf(NewChaptersUpdate("One", 3), NewChaptersUpdate("Two", 5))
        )

        val summary = checkNotNull(summary())
        assertThat(summary.group).isEqualTo(UpdateNotificationHelper.GROUP_VALUE)
        assertThat(summary.flags and Notification.FLAG_GROUP_SUMMARY).isNotEqualTo(0)
        assertThat(summary.extras.getString(Notification.EXTRA_TITLE))
            .isEqualTo("2 novels have new chapters")
    }

    @Test
    fun `the summary lists each novel with its count`() {
        helper.postNewChaptersGroupSummary(
            listOf(NewChaptersUpdate("One", 3), NewChaptersUpdate("Two", 5))
        )

        val lines = checkNotNull(summary())
            .extras
            .getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            .orEmpty()
            .map { it.toString() }

        assertThat(lines).containsExactly("One — 3 new chapter(s)", "Two — 5 new chapter(s)").inOrder()
    }

    @Test
    fun `a summary left over from an earlier run is cancelled when only one novel updates`() {
        helper.postNewChaptersGroupSummary(
            listOf(NewChaptersUpdate("One", 3), NewChaptersUpdate("Two", 5))
        )
        assertThat(summary()).isNotNull()

        helper.postNewChaptersGroupSummary(listOf(NewChaptersUpdate("Three", 1)))

        assertThat(summary()).isNull()
    }

    @Test
    fun `the summary opens the app without a deep link`() {
        helper.postNewChaptersGroupSummary(
            listOf(NewChaptersUpdate("One", 3), NewChaptersUpdate("Two", 5))
        )

        val intent: Intent = Shadows.shadowOf(checkNotNull(summary()).contentIntent).savedIntent

        assertThat(intent.getStringExtra(MainActivity.EXTRA_DEEP_LINK_ACTION)).isNull()
        assertThat(intent.getStringExtra(MainActivity.EXTRA_NOVEL_ID)).isNull()
    }

    @Test
    fun `the per novel notification stays on its channel and group`() {
        helper.postNewChaptersNotification(7L, "Seven", 2)

        val child = checkNotNull(childFor(7L))
        assertThat(child.group).isEqualTo(UpdateNotificationHelper.GROUP_VALUE)
        assertThat(child.channelId).isEqualTo(UpdateNotificationHelper.CHANNEL_ID)
    }
}
