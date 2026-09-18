package com.novelreader.ui.webimport

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.novelreader.R
import com.novelreader.util.CloudflareChallengePolicy
import com.novelreader.util.StringUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CloudflareChallengeDialog(
    url: String,
    expectedHost: String,
    onCookiesCollected: (cookies: List<Pair<String, String>>) -> Unit,
    onCancel: () -> Unit
) {
    var isVerifying by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val isAllowed = remember(url, expectedHost) {
        CloudflareChallengePolicy.isAllowed(url, expectedHost)
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.cloudflare_challenge_title)) },
        text = {
            Column {
                Text(stringResource(R.string.cloudflare_challenge_body))
                Spacer(Modifier.height(8.dp))
                if (isVerifying) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.cloudflare_challenge_waiting),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                if (!isAllowed) {
                    Text(
                        text = stringResource(R.string.cloudflare_challenge_blocked),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                400
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowContentAccess = false
                            settings.allowFileAccess = false
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: android.webkit.WebResourceRequest
                                ): Boolean = CloudflareChallengePolicy.shouldBlockNavigation(
                                    request.url.toString(),
                                    expectedHost
                                )

                                override fun onPageFinished(view: WebView, loadedUrl: String) {
                                    scope.launch {
                                        delay(2000)
                                        CookieManager.getInstance().flush()
                                        val cookies = CookieManager.getInstance().getCookie(url)
                                            ?.split(";")
                                            ?.mapNotNull { entry ->
                                                val parts = entry.trim().split("=", limit = 2)
                                                if (parts.size == 2) parts[0] to parts[1] else null
                                            } ?: emptyList()
                                        if (StringUtils.hostMatchesDomain(Uri.parse(loadedUrl).host ?: "", expectedHost) &&
                                            !loadedUrl.contains("challenge") &&
                                            !loadedUrl.contains("cf-")
                                        ) {
                                            isVerifying = false
                                            onCookiesCollected(cookies)
                                        }
                                    }
                                }
                            }
                            loadUrl(url)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(400.dp)
                )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cloudflare_challenge_cancel))
            }
        }
    )
}
