package com.novelreader.ui.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.novelreader.R

@Composable
fun ScanRangeDialog(
    initialFrom: Int,
    initialTo: Int,
    onConfirm: (from: Int, to: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var fromText by remember { mutableStateOf(initialFrom.toString()) }
    var toText by remember { mutableStateOf(initialTo.toString()) }

    val from = fromText.toIntOrNull() ?: initialFrom
    val to = toText.toIntOrNull() ?: initialTo
    val isValid = from >= 1 && to >= from

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.failed_chapters_scan_title)) },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = fromText,
                        onValueChange = { fromText = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text(stringResource(R.string.failed_chapters_scan_range_from)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                    )
                    OutlinedTextField(
                        value = toText,
                        onValueChange = { toText = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text(stringResource(R.string.failed_chapters_scan_range_to)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                    )
                }
                if (!isValid) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.failed_chapters_scan_invalid_range),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (isValid) onConfirm(from, to) },
                enabled = isValid
            ) {
                Text(stringResource(R.string.failed_chapters_scan))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
