package com.novelreader.ui.reader

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private class ReaderJsInterface(
    private val onTextSelected: (String) -> Unit,
    private val onTap: () -> Unit,
    private val onSwipe: (String) -> Unit,
    private val onAutoScrollReachedEnd: () -> Unit
) {
    @JavascriptInterface
    fun onTextSelected(text: String) {
        onTextSelected(text)
    }

    @JavascriptInterface
    fun onTap() {
        onTap()
    }

    @JavascriptInterface
    fun onSwipe(direction: String) {
        onSwipe(direction)
    }

    @JavascriptInterface
    fun onAutoScrollReachedEnd() {
        onAutoScrollReachedEnd()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderWebView(
    onTextSelected: (String) -> Unit,
    onScrollChanged: (Float) -> Unit,
    onPageFinished: (WebView, Float) -> Unit,
    onWebViewReady: (WebView) -> Unit,
    onSearchHighlight: (WebView, String) -> Unit,
    onTap: () -> Unit = {},
    onSwipe: (String) -> Unit = {},
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
                settings.allowContentAccess = false
                settings.allowFileAccess = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = false
                settings.databaseEnabled = false
                settings.domStorageEnabled = false
                @Suppress("DEPRECATION")
                settings.savePassword = false
                @Suppress("DEPRECATION")
                settings.saveFormData = false
                setBackgroundColor(Color.TRANSPARENT)
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
                            onPageFinished(wv, 0f)
                        }
                    }
                }
                addJavascriptInterface(
                    ReaderJsInterface(onTextSelected, onTap, onSwipe, onAutoScrollReachedEnd),
                    "Android"
                )
                onWebViewReady(this)
            }
        },
        modifier = modifier
    )
}
