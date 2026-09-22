package com.novelreader.ui.notifications

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationPromptPolicyTest {

    @Test
    fun `below Android 13 never prompts`() {
        val kind = NotificationPromptPolicy.kind(
            sdkAtLeast33 = false,
            notificationsGranted = false,
            alreadyAsked = false,
            suppressedThisSession = false
        )

        assertThat(kind).isNull()
    }

    @Test
    fun `granted notifications never prompt`() {
        val kind = NotificationPromptPolicy.kind(
            sdkAtLeast33 = true,
            notificationsGranted = true,
            alreadyAsked = false,
            suppressedThisSession = false
        )

        assertThat(kind).isNull()
    }

    @Test
    fun `a granted permission stays quiet even after an earlier refusal`() {
        val kind = NotificationPromptPolicy.kind(
            sdkAtLeast33 = true,
            notificationsGranted = true,
            alreadyAsked = true,
            suppressedThisSession = false
        )

        assertThat(kind).isNull()
    }

    @Test
    fun `session suppression silences the first ask`() {
        val kind = NotificationPromptPolicy.kind(
            sdkAtLeast33 = true,
            notificationsGranted = false,
            alreadyAsked = false,
            suppressedThisSession = true
        )

        assertThat(kind).isNull()
    }

    @Test
    fun `session suppression silences the settings offer`() {
        val kind = NotificationPromptPolicy.kind(
            sdkAtLeast33 = true,
            notificationsGranted = false,
            alreadyAsked = true,
            suppressedThisSession = true
        )

        assertThat(kind).isNull()
    }

    @Test
    fun `the first ask requests the system prompt`() {
        val kind = NotificationPromptPolicy.kind(
            sdkAtLeast33 = true,
            notificationsGranted = false,
            alreadyAsked = false,
            suppressedThisSession = false
        )

        assertThat(kind).isEqualTo(NotificationPromptKind.REQUEST)
    }

    @Test
    fun `after the first ask the dialog offers the system settings`() {
        val kind = NotificationPromptPolicy.kind(
            sdkAtLeast33 = true,
            notificationsGranted = false,
            alreadyAsked = true,
            suppressedThisSession = false
        )

        assertThat(kind).isEqualTo(NotificationPromptKind.OPEN_SETTINGS)
    }
}
