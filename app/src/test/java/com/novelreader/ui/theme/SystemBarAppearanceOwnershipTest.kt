package com.novelreader.ui.theme

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SystemBarAppearanceOwnershipTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private class Screen : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun lightIcons(activity: Activity): Boolean =
        WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars

    private fun resume(screen: Screen) = composeTestRule.runOnUiThread {
        screen.registry.currentState = Lifecycle.State.RESUMED
    }

    private fun leave(screen: Screen) = composeTestRule.runOnUiThread {
        screen.registry.currentState = Lifecycle.State.CREATED
    }

    @Test
    fun `reader to reader to library leaves the library with its own status bar appearance`() {
        val library = Screen()
        val reader1 = Screen()
        val reader2 = Screen()
        listOf(library, reader1, reader2).forEach {
            composeTestRule.runOnUiThread { it.registry.currentState = Lifecycle.State.CREATED }
        }

        composeTestRule.setContent {
            Box {
                CompositionLocalProvider(LocalLifecycleOwner provides library) { SystemBarAppearance(dark = true) }
                CompositionLocalProvider(LocalLifecycleOwner provides reader1) { SystemBarAppearance(dark = false) }
                CompositionLocalProvider(LocalLifecycleOwner provides reader2) { SystemBarAppearance(dark = true) }
            }
        }
        composeTestRule.waitForIdle()

        resume(reader1)
        assertThat(lightIcons(composeTestRule.activity)).isTrue()

        leave(reader1)
        resume(reader2)
        assertThat(lightIcons(composeTestRule.activity)).isFalse()

        leave(reader2)
        resume(reader1)
        assertThat(lightIcons(composeTestRule.activity)).isTrue()

        leave(reader1)
        resume(library)
        assertThat(lightIcons(composeTestRule.activity)).isFalse()
    }
}
