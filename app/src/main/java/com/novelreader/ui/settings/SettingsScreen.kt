package com.novelreader.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.novelreader.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAboutClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val appTheme by viewModel.appTheme.collectAsState()
    val locale by viewModel.locale.collectAsState()
    val queueMode by viewModel.queueMode.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.app_theme),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            ThemeOption(
                label = stringResource(R.string.system),
                icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(24.dp)) },
                description = stringResource(R.string.system_desc),
                selected = appTheme == "system",
                onClick = { viewModel.updateAppTheme("system") }
            )
            Spacer(modifier = Modifier.height(8.dp))

            ThemeOption(
                label = stringResource(R.string.light),
                icon = { Icon(Icons.Default.BrightnessHigh, contentDescription = null, modifier = Modifier.size(24.dp)) },
                description = stringResource(R.string.light_desc),
                selected = appTheme == "light",
                onClick = { viewModel.updateAppTheme("light") }
            )
            Spacer(modifier = Modifier.height(8.dp))

            ThemeOption(
                label = stringResource(R.string.dark),
                icon = { Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(24.dp)) },
                description = stringResource(R.string.dark_desc),
                selected = appTheme == "dark",
                onClick = { viewModel.updateAppTheme("dark") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                stringResource(R.string.language),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            LangOption(
                label = stringResource(R.string.portuguese),
                selected = locale == "pt",
                onClick = {
                    if (locale != "pt") {
                        val activity = context as? androidx.activity.ComponentActivity
                        activity?.let { viewModel.updateLocale("pt", it) }
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))

            LangOption(
                label = stringResource(R.string.english),
                selected = locale == "en",
                onClick = {
                    if (locale != "en") {
                        val activity = context as? androidx.activity.ComponentActivity
                        activity?.let { viewModel.updateLocale("en", it) }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                stringResource(R.string.import_queue_mode),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            QueueOption(
                label = stringResource(R.string.import_queue_sequential),
                description = stringResource(R.string.import_queue_sequential_desc),
                selected = queueMode == com.novelreader.data.local.preferences.QueueMode.SEQUENTIAL,
                onClick = { viewModel.updateQueueMode(com.novelreader.data.local.preferences.QueueMode.SEQUENTIAL) }
            )
            Spacer(modifier = Modifier.height(8.dp))

            QueueOption(
                label = stringResource(R.string.import_queue_parallel),
                description = stringResource(R.string.import_queue_parallel_desc),
                selected = queueMode == com.novelreader.data.local.preferences.QueueMode.PARALLEL,
                onClick = { viewModel.updateQueueMode(com.novelreader.data.local.preferences.QueueMode.PARALLEL) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAboutClick),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.about),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun LangOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            ),
        shape = RoundedCornerShape(12.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Language,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            RadioButton(
                selected = selected,
                onClick = null
            )
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    icon: @Composable () -> Unit,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            ),
        shape = RoundedCornerShape(12.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(
                selected = selected,
                onClick = null
            )
        }
    }
}

@Composable
private fun QueueOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            ),
        shape = RoundedCornerShape(12.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(
                selected = selected,
                onClick = null
            )
        }
    }
}
