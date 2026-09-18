package com.novelreader.util

object CloudflareChallengePolicy {

    fun isAllowed(url: String, expectedHost: String): Boolean {
        if (expectedHost.isBlank()) return false
        val uri = try {
            java.net.URI(url)
        } catch (_: Exception) {
            return false
        }
        if (uri.scheme?.lowercase() != "https") return false
        val host = uri.host ?: return false
        return StringUtils.hostMatchesDomain(host, expectedHost)
    }

    fun shouldBlockNavigation(url: String, expectedHost: String): Boolean =
        !isAllowed(url, expectedHost)
}
