package com.novelreader.domain.usecase.webimport

class InMemoryCloudflareCookieStore : CloudflareCookieStore {
    private val entries = mutableMapOf<String, MutableList<StoredCookie>>()

    override suspend fun putCookies(url: String, cookies: List<StoredCookie>) {
        val domain = cookies.firstOrNull()?.domain ?: return
        val list = entries.getOrPut(domain) { mutableListOf() }
        for (c in cookies) {
            list.removeAll { it.name == c.name }
            list.add(c)
        }
    }

    override suspend fun cookiesFor(url: String): List<StoredCookie> {
        val host = try { java.net.URI(url).host } catch (_: Exception) { null } ?: return emptyList()
        val now = System.currentTimeMillis()
        return entries[host]?.filter { it.expiresAt > now } ?: emptyList()
    }

    override suspend fun clear(domain: String) {
        entries.remove(domain)
    }
}
