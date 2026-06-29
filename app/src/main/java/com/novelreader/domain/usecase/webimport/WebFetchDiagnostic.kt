package com.novelreader.domain.usecase.webimport

import org.jsoup.HttpStatusException

data class WebFetchDiagnostic(
    val callSite: String,
    val statusCode: Int,
    val url: String,
    val server: String,
    val cfRay: String,
    val setCookie: String,
    val bodySnippet: String,
    val exceptionClass: String
)

fun extractWebFetchDiagnostic(
    throwable: Throwable,
    callSite: String,
    serverHeader: String?,
    cfRayHeader: String?,
    setCookieHeader: String?,
    bodySnippet: String?
): WebFetchDiagnostic {
    val hse = throwable as? HttpStatusException
    return WebFetchDiagnostic(
        callSite = callSite,
        statusCode = hse?.statusCode ?: 0,
        url = hse?.url.orEmpty(),
        server = serverHeader.orEmpty(),
        cfRay = cfRayHeader.orEmpty(),
        setCookie = setCookieHeader.orEmpty(),
        bodySnippet = bodySnippet.orEmpty(),
        exceptionClass = throwable::class.java.simpleName
    )
}

fun formatWebFetchDiagnostic(diag: WebFetchDiagnostic): String {
    val body = diag.bodySnippet.replace("\r", " ").replace("\n", " ").replace(Regex("\\s+"), " ").trim()
    return buildString {
        append("callSite=").append(diag.callSite)
        append(" status=").append(diag.statusCode)
        append(" url=").append(diag.url)
        append(" server=").append(diag.server)
        append(" cfRay=").append(diag.cfRay)
        append(" setCookie=").append(diag.setCookie)
        append(" body=").append(body)
        append(" exception=").append(diag.exceptionClass)
    }
}
