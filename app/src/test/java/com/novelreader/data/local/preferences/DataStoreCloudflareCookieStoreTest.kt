package com.novelreader.data.local.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.domain.usecase.webimport.CloudflareCookieStore
import com.novelreader.domain.usecase.webimport.StoredCookie
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DataStoreCloudflareCookieStoreTest {

    private lateinit var store: CloudflareCookieStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = DataStoreCloudflareCookieStore.create(context)
        runBlocking {
            store.clear("freewebnovel.com")
            store.clear(".freewebnovel.com")
            store.clear("a.com")
            store.clear("b.com")
            store.clear("expired.com")
            store.clear(".readfullnovel.com")
            store.clear("readfullnovel.com")
        }
    }

    @Test
    fun putCookies_thenCookiesForReturnsSame(): Unit = runBlocking {
        val cookies = listOf(
            StoredCookie(name = "cf_clearance", value = "abc", domain = "freewebnovel.com", path = "/", expiresAt = System.currentTimeMillis() + 3_600_000L),
            StoredCookie(name = "cf_bm", value = "xyz", domain = "freewebnovel.com", path = "/", expiresAt = System.currentTimeMillis() + 3_600_000L)
        )
        store.putCookies("https://freewebnovel.com/x", cookies)

        val read = store.cookiesFor("https://freewebnovel.com/y")
        assertThat(read.map { it.name to it.value }).containsExactly("cf_clearance" to "abc", "cf_bm" to "xyz")
    }

    @Test
    fun cookiesForDropsExpiredEntries(): Unit = runBlocking {
        store.putCookies(
            url = "https://expired.com/p",
            cookies = listOf(StoredCookie(name = "old", value = "v", domain = "expired.com", path = "/", expiresAt = System.currentTimeMillis() - 1L))
        )
        val read = store.cookiesFor("https://expired.com/p")
        assertThat(read).isEmpty()
    }

    @Test
    fun cookiesForReturnsEmptyForUnknownDomain(): Unit = runBlocking {
        val read = store.cookiesFor("https://otherdomain.com/")
        assertThat(read).isEmpty()
    }

    @Test
    fun clearRemovesOnlyMatchingDomain(): Unit = runBlocking {
        store.putCookies("https://a.com/x", listOf(StoredCookie("c", "1", "a.com", "/", System.currentTimeMillis() + 3_600_000L)))
        store.putCookies("https://b.com/x", listOf(StoredCookie("c", "2", "b.com", "/", System.currentTimeMillis() + 3_600_000L)))

        store.clear("a.com")

        assertThat(store.cookiesFor("https://a.com/x")).isEmpty()
        assertThat(store.cookiesFor("https://b.com/x")).hasSize(1)
    }

    @Test
    fun cookiesForFindsCookiesWithLeadingDotDomainWhenLookupIsWwwVariant(): Unit = runBlocking {
        val cookies = listOf(
            StoredCookie(
                name = "cf_clearance",
                value = "abc",
                domain = ".readfullnovel.com",
                path = "/",
                expiresAt = System.currentTimeMillis() + 3_600_000L
            )
        )
        store.putCookies("https://readfullnovel.com/", cookies)

        val read = store.cookiesFor("https://www.readfullnovel.com/anything")
        assertThat(read.map { it.name to it.value }).containsExactly("cf_clearance" to "abc")
    }
}
