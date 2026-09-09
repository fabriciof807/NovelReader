package com.novelreader.ui.navigation

import android.content.Intent
import com.novelreader.MainActivity

object DeepLinkIntentParser {

    fun parse(intent: Intent?, expectedToken: String): DeepLinkAction? {
        intent ?: return null
        if (intent.getStringExtra(MainActivity.EXTRA_DEEP_LINK_TOKEN) != expectedToken) return null
        val action = intent.getStringExtra(MainActivity.EXTRA_DEEP_LINK_ACTION) ?: return null
        val novelId = intent.getLongExtra(MainActivity.EXTRA_NOVEL_ID, -1L)
        return when (action) {
            MainActivity.ACTION_OPEN_NOVEL ->
                if (novelId > 0L) DeepLinkAction.ViewNovel(novelId) else null
            MainActivity.ACTION_OPEN_CLOUDFLARE_SOLVER ->
                DeepLinkAction.OpenCloudflareSolver(novelId.takeIf { it > 0L })
            MainActivity.ACTION_OPEN_FAILED_CHAPTERS ->
                if (novelId > 0L) DeepLinkAction.OpenFailedChapters(novelId) else null
            else -> null
        }
    }
}
