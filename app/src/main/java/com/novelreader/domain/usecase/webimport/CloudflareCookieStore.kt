package com.novelreader.domain.usecase.webimport

interface CloudflareCookieStore {
    suspend fun putCookies(url: String, cookies: List<StoredCookie>)
    suspend fun cookiesFor(url: String): List<StoredCookie>
    suspend fun clear(domain: String)
}

data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long
)

data class HttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String>,
    val finalUrl: String?,
    val bodyBytes: ByteArray? = null
)
