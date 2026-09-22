package com.novelreader.ui.notifications

import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.test.ComposeUiTestBase
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "pt-rBR-w400dp-h800dp")
class NotificationRationaleDialogTest : ComposeUiTestBase() {

    @Test
    fun `the request variant explains why before asking`() {
        setNovelReaderContent {
            NotificationRationaleDialog(
                kind = NotificationPromptKind.REQUEST,
                onConfirm = {},
                onDismiss = {}
            )
        }

        assertTextDisplayed("Ativar notificações?")
        assertTextDisplayed("Ativar")
        assertTextDisplayed("Agora não")
    }

    @Test
    fun `the request variant confirms the system prompt`() {
        var confirmed = false
        setNovelReaderContent {
            NotificationRationaleDialog(
                kind = NotificationPromptKind.REQUEST,
                onConfirm = { confirmed = true },
                onDismiss = {}
            )
        }

        performClick("Ativar")

        assertThat(confirmed).isTrue()
    }

    @Test
    fun `the request variant can be dismissed`() {
        var dismissed = false
        setNovelReaderContent {
            NotificationRationaleDialog(
                kind = NotificationPromptKind.REQUEST,
                onConfirm = {},
                onDismiss = { dismissed = true }
            )
        }

        performClick("Agora não")

        assertThat(dismissed).isTrue()
    }

    @Test
    fun `the settings variant sends the user to the system screen`() {
        setNovelReaderContent {
            NotificationRationaleDialog(
                kind = NotificationPromptKind.OPEN_SETTINGS,
                onConfirm = {},
                onDismiss = {}
            )
        }

        assertTextDisplayed("Notificações desativadas")
        assertTextDisplayed("Abrir configurações")
        assertTextDisplayed("Agora não")
        assertTextNotDisplayed("Ativar")
    }

    @Test
    fun `the settings variant confirms opening the settings`() {
        var confirmed = false
        setNovelReaderContent {
            NotificationRationaleDialog(
                kind = NotificationPromptKind.OPEN_SETTINGS,
                onConfirm = { confirmed = true },
                onDismiss = {}
            )
        }

        performClick("Abrir configurações")

        assertThat(confirmed).isTrue()
    }
}
