package com.novelreader.ui.library.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Extra breathing room on top of the Scaffold's own 16dp lift, so the button does not read as
// flush against the stats bar. Kept below the 16dp lift so the two read as one rhythm.
val LibraryFabClearance: Dp = 12.dp

@Composable
fun LibraryFab(onClick: () -> Unit) {
    Box(modifier = Modifier.padding(bottom = LibraryFabClearance)) {
        FloatingActionButton(onClick = onClick) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}
