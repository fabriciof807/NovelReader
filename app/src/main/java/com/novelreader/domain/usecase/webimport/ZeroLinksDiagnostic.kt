package com.novelreader.domain.usecase.webimport

data class ZeroLinksDiagnostic(
    val callSite: String,
    val homeUrl: String,
    val finalUrl: String?,
    val statusCode: Int,
    val bodyLength: Int,
    val bodySnippet: String,
    val attemptedUrls: List<String>
)

private const val ZERO_LINKS_BODY_SNIPPET_MAX = 256

fun extractZeroLinksDiagnostic(
    callSite: String,
    homeUrl: String,
    finalUrl: String?,
    statusCode: Int,
    body: String,
    attemptedUrls: List<String>
): ZeroLinksDiagnostic {
    val snippet = body.take(ZERO_LINKS_BODY_SNIPPET_MAX)
    return ZeroLinksDiagnostic(
        callSite = callSite,
        homeUrl = homeUrl,
        finalUrl = finalUrl ?: "",
        statusCode = statusCode,
        bodyLength = body.length,
        bodySnippet = snippet,
        attemptedUrls = attemptedUrls
    )
}

fun formatZeroLinksDiagnostic(diag: ZeroLinksDiagnostic): String {
    val body = diag.bodySnippet
        .replace("\r", " ")
        .replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    val urls = diag.attemptedUrls.joinToString(",")
    return buildString {
        append("callSite=").append(diag.callSite)
        append(" status=").append(diag.statusCode)
        append(" homeUrl=").append(diag.homeUrl)
        append(" finalUrl=").append(diag.finalUrl)
        append(" linkCount=0")
        append(" bodyLength=").append(diag.bodyLength)
        append(" body=").append(body)
        append(" attemptedUrls=[").append(urls).append("]")
    }
}
