package com.novelreader.ui.navigation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepLinkToken @Inject constructor(
    @ApplicationContext context: Context
) {

    val value: String

    init {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_TOKEN, null)
        value = stored ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_TOKEN, it).apply()
        }
    }

    private companion object {
        const val PREFS_NAME = "deep_link_token"
        const val KEY_TOKEN = "token"
    }
}
