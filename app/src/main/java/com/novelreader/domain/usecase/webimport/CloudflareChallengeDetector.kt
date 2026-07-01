package com.novelreader.domain.usecase.webimport

fun isCloudflareChallenge(
    statusCode: Int,
    body: String?,
    headers: Map<String, String>
): Boolean {
    if (headers.keys.any { it.equals("cf-mitigated", ignoreCase = true) }) return true
    if (body == null) return false
    val lowered = body.lowercase()
    val bodyMatches = lowered.contains("just a moment") ||
        lowered.contains("cf-mitigated") ||
        lowered.contains("challenge-platform")
    if (bodyMatches) return true
    if (statusCode in 400..499 || statusCode == 429) {
        return lowered.contains("cloudflare") || lowered.contains("cf-ray")
    }
    return false
}

class CloudflareChallengeRequiredException(
    val url: String,
    val evidence: String
) : RuntimeException("Cloudflare challenge required for $url: $evidence")

class RateLimitedException(
    val url: String,
    val attempts: Int,
    val lastStatusCode: Int
) : RuntimeException("Rate limited (HTTP $lastStatusCode) for $url after $attempts attempts")
