package com.novelreader.ui.reader

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderChapterNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `accepted chapter callback restores the second chapter to the top`() {
        val loadToken = LoadToken()
        var webView: WebView? = null
        var firstPageFinished = false
        var restoreCompleted = false
        var pageLoaded = false
        var expectedRestoreToken: Int? = null
        val firstToken = loadToken.next()

        composeRule.setContent {
            ReaderWebView(
                onScrollChanged = {},
                onPageFinished = { wv, url ->
                    if (!loadToken.shouldAccept(url)) return@ReaderWebView
                    if (url == loadToken.baseUrl(firstToken)) {
                        firstPageFinished = true
                    } else {
                        wv.evaluateJavascript(scrollRestoreJs(0f, expectedRestoreToken), null)
                    }
                },
                onScrollRestoreComplete = { token ->
                    if (token == expectedRestoreToken) {
                        pageLoaded = true
                        restoreCompleted = true
                    }
                },
                onWebViewReady = { webView = it }
            )
        }

        composeRule.waitUntil(timeoutMillis = 10_000) { webView != null }
        composeRule.runOnUiThread {
            webView!!.loadDataWithBaseURL(
                loadToken.baseUrl(firstToken),
                longChapter("first"),
                "text/html",
                "UTF-8",
                null
            )
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { firstPageFinished }

        composeRule.runOnUiThread { webView!!.scrollTo(0, Int.MAX_VALUE) }
        composeRule.waitUntil(timeoutMillis = 10_000) { webView!!.scrollY > 0 }

        val secondToken = loadToken.next()
        expectedRestoreToken = secondToken
        composeRule.runOnUiThread {
            webView!!.loadDataWithBaseURL(
                loadToken.baseUrl(secondToken),
                longChapter("second"),
                "text/html",
                "UTF-8",
                null
            )
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            restoreCompleted && webView!!.scrollY == 0
        }

        assertThat(webView!!.scrollY).isEqualTo(0)
        assertThat(pageLoaded).isTrue()
    }

    private fun longChapter(label: String): String = buildString {
        append("<html><body>")
        repeat(200) {
            append("<p style='font-size:24px;line-height:2'>Chapter $label paragraph $it</p>")
        }
        append("</body></html>")
    }
}
