package com.novelreader.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    private const val PREFS_NAME = "locale_sync"
    private const val KEY_LOCALE = "locale"

    fun getLocale(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LOCALE, "pt") ?: "pt"
    }

    fun applyLocale(context: Context): Context {
        val localeCode = getLocale(context)
        val locale = Locale(localeCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
