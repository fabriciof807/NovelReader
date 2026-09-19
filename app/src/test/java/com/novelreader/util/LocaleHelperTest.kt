package com.novelreader.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.novelreader.R
import com.novelreader.data.local.preferences.PreferenceAllowlists
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The app follows the device language until the reader picks one, and an untranslated device language
 * lands on English — `values/` holds English and the Portuguese strings live in `values-pt/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "en-rUS-w400dp-h800dp")
class LocaleHelperTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun languageOf(applied: Context) = applied.getString(R.string.language)

    @Test
    fun `without a choice the app uses the language the device is in`() {
        assertThat(LocaleHelper.getLocale(context)).isEqualTo(PreferenceAllowlists.LOCALE_SYSTEM)
        assertThat(languageOf(LocaleHelper.applyLocale(context))).isEqualTo("Language")
    }

    @Test
    @Config(qualifiers = "pt-rBR-w400dp-h800dp")
    fun `a device in Portuguese reads Portuguese`() {
        assertThat(languageOf(LocaleHelper.applyLocale(context))).isEqualTo("Idioma")
    }

    @Test
    @Config(qualifiers = "de-rDE-w400dp-h800dp")
    fun `a device in a language we do not translate falls back to English`() {
        assertThat(languageOf(LocaleHelper.applyLocale(context))).isEqualTo("Language")
    }

    @Test
    fun `a chosen language is forced over the device one`() {
        val originalDefault = Locale.getDefault()
        try {
            context.getSharedPreferences("locale_sync", Context.MODE_PRIVATE)
                .edit()
                .putString("locale", "pt")
                .commit()

            assertThat(LocaleHelper.getLocale(context)).isEqualTo("pt")
            assertThat(languageOf(LocaleHelper.applyLocale(context))).isEqualTo("Idioma")
        } finally {
            Locale.setDefault(originalDefault)
        }
    }
}
