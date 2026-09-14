package com.novelreader

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.novelreader.data.local.preferences.AppPreferences
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.ui.navigation.DeepLinkBus
import com.novelreader.ui.navigation.DeepLinkIntentParser
import com.novelreader.ui.navigation.DeepLinkToken
import com.novelreader.ui.navigation.NovelReaderNavGraph
import com.novelreader.ui.theme.NovelReaderTheme
import com.novelreader.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var deepLinkBus: DeepLinkBus

    @Inject
    lateinit var deepLinkToken: DeepLinkToken

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored; import will still work without notification */ }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        handleIntent(intent)
        setContent {
            val appTheme by appPreferences.appTheme.collectAsState(initial = "system")
            val appPalette by appPreferences.appPalette.collectAsState(
                initial = PreferenceAllowlists.PALETTE_DYNAMIC
            )
            val accentColor by appPreferences.accentColor.collectAsState(initial = null)

            NovelReaderTheme(
                appTheme = appTheme,
                palette = appPalette,
                accentColor = accentColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NovelReaderNavGraph(
                        navController = navController,
                        deepLinkBus = deepLinkBus
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val action = DeepLinkIntentParser.parse(intent, deepLinkToken.value) ?: return
        deepLinkBus.emit(action)
        intent.removeExtra(EXTRA_DEEP_LINK_ACTION)
        intent.removeExtra(EXTRA_NOVEL_ID)
        intent.removeExtra(EXTRA_DEEP_LINK_TOKEN)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_DEEP_LINK_ACTION = "deep_link_action"
        const val EXTRA_NOVEL_ID = "deep_link_novel_id"
        const val EXTRA_DEEP_LINK_TOKEN = "deep_link_token"
        const val ACTION_OPEN_NOVEL = "open_novel"
        const val ACTION_OPEN_CLOUDFLARE_SOLVER = "open_cloudflare_solver"
        const val ACTION_OPEN_FAILED_CHAPTERS = "open_failed_chapters"
    }
}
