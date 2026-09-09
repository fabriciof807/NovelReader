package com.novelreader.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DeepLinkIntentParserTest {

    private val token = "test-token"

    private fun intent(
        action: String? = MainActivity.ACTION_OPEN_NOVEL,
        novelId: Long? = 42L,
        token: String? = this.token
    ): Intent {
        val intent = Intent()
        action?.let { intent.putExtra(MainActivity.EXTRA_DEEP_LINK_ACTION, it) }
        novelId?.let { intent.putExtra(MainActivity.EXTRA_NOVEL_ID, it) }
        token?.let { intent.putExtra(MainActivity.EXTRA_DEEP_LINK_TOKEN, it) }
        return intent
    }

    @Test
    fun `ignores an intent without a token`() {
        val parsed = DeepLinkIntentParser.parse(intent(token = null), token)

        assertThat(parsed).isNull()
    }

    @Test
    fun `ignores an intent with a wrong token`() {
        val parsed = DeepLinkIntentParser.parse(intent(token = "other"), token)

        assertThat(parsed).isNull()
    }

    @Test
    fun `parses open novel with a valid token`() {
        val parsed = DeepLinkIntentParser.parse(intent(novelId = 42L), token)

        assertThat(parsed).isEqualTo(DeepLinkAction.ViewNovel(42L))
    }

    @Test
    fun `parses failed chapters with a valid token`() {
        val parsed = DeepLinkIntentParser.parse(
            intent(action = MainActivity.ACTION_OPEN_FAILED_CHAPTERS, novelId = 7L),
            token
        )

        assertThat(parsed).isEqualTo(DeepLinkAction.OpenFailedChapters(7L))
    }

    @Test
    fun `parses the cloudflare solver without a novel id`() {
        val parsed = DeepLinkIntentParser.parse(
            intent(action = MainActivity.ACTION_OPEN_CLOUDFLARE_SOLVER, novelId = -1L),
            token
        )

        assertThat(parsed).isEqualTo(DeepLinkAction.OpenCloudflareSolver(null))
    }

    @Test
    fun `ignores an unknown action`() {
        val parsed = DeepLinkIntentParser.parse(intent(action = "drop_database"), token)

        assertThat(parsed).isNull()
    }

    @Test
    fun `ignores a null intent`() {
        assertThat(DeepLinkIntentParser.parse(null, token)).isNull()
    }

    @Test
    fun `generated tokens are non-blank and stable per install`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = DeepLinkToken(context).value
        val second = DeepLinkToken(context).value

        assertThat(first).isNotEmpty()
        assertThat(second).isEqualTo(first)
    }
}
