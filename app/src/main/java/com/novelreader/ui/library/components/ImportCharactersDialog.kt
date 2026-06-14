package com.novelreader.ui.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novelreader.R

@Composable
fun ImportCharactersDialog(
    importUrl: String,
    onUrlChange: (String) -> Unit,
    isImporting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_characters)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.import_characters_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = importUrl,
                    onValueChange = onUrlChange,
                    placeholder = { Text("https://www.mvlempyr.io/novel/...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isImporting) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.importing_characters),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = importUrl.isNotBlank() && !isImporting
            ) {
                Text(stringResource(R.string.import_characters))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isImporting
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
