package com.novelreader.util

import android.content.Context
import android.content.res.Configuration
import com.novelreader.data.local.preferences.PreferenceAllowlists
import java.util.Locale

/**
 * The app follows the device language until the reader picks one in Settings, which is what
 * [PreferenceAllowlists.LOCALE_SYSTEM] means. A chosen locale is forced over whatever the device
 * asks for, and an untranslated device language lands on the default resources (English), because
 * `values/` holds English and the Portuguese strings live in `values-pt/`.
 */
object LocaleHelper {
    private const val PREFS_NAME = "locale_sync"
    private const val KEY_LOCALE = "locale"

    fun getLocale(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LOCALE, PreferenceAllowlists.LOCALE_SYSTEM)
            ?: PreferenceAllowlists.LOCALE_SYSTEM
    }

    fun applyLocale(context: Context): Context {
        val localeCode = getLocale(context)
        if (localeCode == PreferenceAllowlists.LOCALE_SYSTEM) return context
        val locale = Locale(localeCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
