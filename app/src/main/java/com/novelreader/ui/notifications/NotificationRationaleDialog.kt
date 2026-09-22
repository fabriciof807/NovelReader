package com.novelreader.ui.notifications

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.novelreader.R

@Composable
fun NotificationRationaleDialog(
    kind: NotificationPromptKind,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = when (kind) {
        NotificationPromptKind.REQUEST -> R.string.notification_rationale_request_title
        NotificationPromptKind.OPEN_SETTINGS -> R.string.notification_rationale_settings_title
    }
    val body = when (kind) {
        NotificationPromptKind.REQUEST -> R.string.notification_rationale_request_body
        NotificationPromptKind.OPEN_SETTINGS -> R.string.notification_rationale_settings_body
    }
    val confirmLabel = when (kind) {
        NotificationPromptKind.REQUEST -> R.string.notification_rationale_turn_on
        NotificationPromptKind.OPEN_SETTINGS -> R.string.notification_rationale_open_settings
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(confirmLabel))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.notification_rationale_not_now))
            }
        }
    )
}
