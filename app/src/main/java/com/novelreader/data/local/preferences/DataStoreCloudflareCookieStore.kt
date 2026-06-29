package com.novelreader.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.novelreader.domain.usecase.webimport.CloudflareCookieStore
import com.novelreader.domain.usecase.webimport.StoredCookie
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cloudflareCookieDataStore: DataStore<Preferences> by preferencesDataStore(name = "cloudflare_cookies")

class DataStoreCloudflareCookieStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : CloudflareCookieStore {

    override suspend fun putCookies(url: String, cookies: List<StoredCookie>) {
        if (cookies.isEmpty()) return
        val domain = cookies.first().domain
        val key = stringSetPreferencesKey(domain)
        dataStore.edit { prefs ->
            val existing = prefs[key] ?: emptySet()
            val existingByName = existing.mapNotNull { decode(it) }.associateBy { it.name }
            val updated = (existingByName + cookies.associateBy { it.name }).values.map(::encode)
            prefs[key] = updated.toSet()
        }
    }

    override suspend fun cookiesFor(url: String): List<StoredCookie> {
        val host = try { java.net.URI(url).host } catch (_: Exception) { null } ?: return emptyList()
        val prefs = dataStore.data.first()
        val now = System.currentTimeMillis()
        val key = stringSetPreferencesKey(host)
        val entries = prefs[key] ?: return emptyList()
        return entries.mapNotNull { decode(it) }.filter { it.expiresAt > now }
    }

    override suspend fun clear(domain: String) {
        val key = stringSetPreferencesKey(domain)
        dataStore.edit { prefs -> prefs.remove(key) }
    }

    private fun encode(cookie: StoredCookie): String =
        listOf(cookie.name, cookie.value, cookie.domain, cookie.path, cookie.expiresAt.toString())
            .joinToString(SEP)

    private fun decode(raw: String): StoredCookie? {
        val parts = raw.split(SEP)
        if (parts.size != 5) return null
        val expiresAt = parts[4].toLongOrNull() ?: return null
        return StoredCookie(
            name = parts[0],
            value = parts[1],
            domain = parts[2],
            path = parts[3],
            expiresAt = expiresAt
        )
    }

    companion object {
        private const val SEP = "\u001F"

        fun create(@ApplicationContext context: Context): DataStoreCloudflareCookieStore =
            DataStoreCloudflareCookieStore(context.cloudflareCookieDataStore)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object CloudflareCookieStoreModule {
    @Provides
    @Singleton
    fun provideCloudflareCookieStore(
        @ApplicationContext context: Context
    ): CloudflareCookieStore = DataStoreCloudflareCookieStore.create(context)
}
