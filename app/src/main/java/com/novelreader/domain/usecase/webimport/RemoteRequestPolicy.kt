package com.novelreader.domain.usecase.webimport

import com.novelreader.util.StringUtils
import java.net.URI

sealed interface RemoteRequestPolicy {
    fun allows(url: String): Boolean

    data object AnyPublicHttps : RemoteRequestPolicy {
        override fun allows(url: String): Boolean = httpsHost(url) != null
    }

    data class SameNovelDomain(val expectedHost: String) : RemoteRequestPolicy {
        override fun allows(url: String): Boolean {
            val host = httpsHost(url) ?: return false
            return StringUtils.hostMatchesDomain(host, expectedHost)
        }
    }
}

class RemoteRequestRejectedException(message: String) : SecurityException(message)

private fun httpsHost(url: String): String? = try {
    val uri = URI(url)
    if (uri.scheme?.lowercase() != "https") null else uri.host
} catch (_: Exception) {
    null
}

internal fun RemoteRequestPolicy.allows(url: String, allowCleartextForTests: Boolean): Boolean {
    if (!allowCleartextForTests) return allows(url)
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val host = uri.host ?: return false
    return when (this) {
        RemoteRequestPolicy.AnyPublicHttps -> uri.scheme in setOf("http", "https")
        is RemoteRequestPolicy.SameNovelDomain ->
            uri.scheme in setOf("http", "https") && StringUtils.hostMatchesDomain(host, expectedHost)
    }
}
