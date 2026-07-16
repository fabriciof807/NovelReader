package com.novelreader.ui.library.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.novelreader.R

@Composable
fun CoverUrlDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    val httpsError = stringResource(R.string.cover_url_https_required)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cover_url_title)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; urlError = null },
                label = { Text(stringResource(R.string.cover_url_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (urlError != null) {
                Text(
                    text = urlError!!,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (url.startsWith("https://", ignoreCase = true)) {
                        urlError = null
                        onConfirm(url)
                    } else {
                        urlError = httpsError
                    }
                },
                enabled = url.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
