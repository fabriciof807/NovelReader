package com.novelreader.ui.reader

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

internal class ReaderJsInterface(
    private val onTapCallback: () -> Unit,
    private val onSwipeCallback: (String, String) -> Unit,
    private val onAutoScrollReachedEndCallback: () -> Unit,
    private val onScrollRestoreCompleteCallback: (Int) -> Unit
) {
    @JavascriptInterface
    fun onTap() {
        onTapCallback()
    }

    @JavascriptInterface
    fun onSwipe(direction: String, axis: String) {
        onSwipeCallback(direction, axis)
    }

    @JavascriptInterface
    fun onAutoScrollReachedEnd() {
        onAutoScrollReachedEndCallback()
    }

    @JavascriptInterface
    fun onScrollRestoreComplete(token: Int) {
        onScrollRestoreCompleteCallback(token)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderWebView(
    onScrollChanged: (Float) -> Unit,
    onPageFinished: (WebView, String?) -> Unit,
    onWebViewReady: (WebView) -> Unit,
    onScrollRestoreComplete: (Int) -> Unit = {},
    onTap: () -> Unit = {},
    onSwipe: (String, String) -> Unit = { _, _ -> },
    onAutoScrollReachedEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.databaseEnabled = false
                settings.saveFormData = false
                settings.setGeolocationEnabled(false)
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setSupportMultipleWindows(false)
                settings.setSupportZoom(false)
                settings.setAllowFileAccessFromFileURLs(false)
                settings.setAllowUniversalAccessFromFileURLs(false)
                settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                settings.blockNetworkLoads = true
                settings.safeBrowsingEnabled = true
                setBackgroundColor(Color.TRANSPARENT)
                setOnLongClickListener { true }
                setOnScrollChangeListener { _, _, _, _, _ ->
                    val totalH = (contentHeight * scale).toInt()
                    val visibleH = height
                    val maxScroll = totalH - visibleH
                    val ratio = if (maxScroll > 0) {
                        (scrollY.toFloat() / maxScroll).coerceIn(0f, 1f)
                    } else 0f
                    onScrollChanged(ratio)
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = true

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.let { wv ->
                            // Invoked asynchronously on the WebView's thread; the
                            // composable's lambda captures Compose State<T> and
                            // relies on its stable identity to read current values.
                            onPageFinished(wv, url)
                        }
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onJsAlert(
                        view: WebView?,
                        url: String?,
                        message: String?,
                        result: android.webkit.JsResult?
                    ): Boolean {
                        result?.cancel()
                        return true
                    }

                    override fun onJsConfirm(
                        view: WebView?,
                        url: String?,
                        message: String?,
                        result: android.webkit.JsResult?
                    ): Boolean {
                        result?.cancel()
                        return true
                    }

                    override fun onJsPrompt(
                        view: WebView?,
                        url: String?,
                        message: String?,
                        defaultValue: String?,
                        result: android.webkit.JsPromptResult?
                    ): Boolean {
                        result?.cancel()
                        return true
                    }
                }
                addJavascriptInterface(
                    ReaderJsInterface(
                        onTap,
                        onSwipe,
                        onAutoScrollReachedEnd,
                        { token -> post { onScrollRestoreComplete(token) } }
                    ),
                    "Android"
                )
                onWebViewReady(this)
            }
        },
        modifier = modifier
    )
}
