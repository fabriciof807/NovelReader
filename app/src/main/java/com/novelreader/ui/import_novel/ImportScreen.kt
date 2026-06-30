package com.novelreader.ui.import_novel

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.novelreader.R
import com.novelreader.ui.webimport.CloudflareChallengeDialog
import com.novelreader.ui.webimport.WebImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onImportComplete: (Long?) -> Unit,
    importViewModel: ImportViewModel = hiltViewModel(),
    webImportViewModel: WebImportViewModel = hiltViewModel()
) {
    val localState by importViewModel.state.collectAsState()
    val webState by webImportViewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(localState.importedNovelId) {
        localState.importedNovelId?.let { id ->
            onImportComplete(id)
            importViewModel.resetState()
        }
    }

    LaunchedEffect(Unit) {
        importViewModel.errorEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.import_tab_local)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.import_tab_web)) }
                )
            }

            when (selectedTab) {
                0 -> LocalImportTab(
                    state = localState,
                    onImportFiles = { uris -> importViewModel.importFiles(uris) }
                )
                1 -> WebImportTab(
                    state = webState,
                    viewModel = webImportViewModel,
                    onImportComplete = onImportComplete
                )
            }
        }
    }
}

@Composable
private fun LocalImportTab(
    state: ImportState,
    onImportFiles: (List<Uri>) -> Unit
) {
    var selectedFiles by remember { mutableStateOf(emptyList<Uri>()) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        selectedFiles = uris.filter { uri ->
            val name = uri.lastPathSegment?.lowercase() ?: ""
            name.endsWith(".html") || name.endsWith(".htm") ||
            name.endsWith(".mht") || name.endsWith(".mhtml")
        }.ifEmpty { uris }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
            enabled = !state.isImporting
        ) {
            Text(stringResource(R.string.select_files))
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedFiles.isNotEmpty() && !state.isImporting) {
            Text(
                stringResource(R.string.files_selected, selectedFiles.size),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onImportFiles(selectedFiles) },
                enabled = !state.isImporting
            ) {
                Text(stringResource(R.string.import_action))
            }
        }

        if (state.isImporting && state.totalFiles > 0) {
            Spacer(modifier = Modifier.height(24.dp))
            LinearProgressIndicator(
                progress = state.processedFiles.toFloat() / state.totalFiles,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${stringResource(R.string.importing)} ${state.processedFiles}/${state.totalFiles}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (state.isImporting && state.totalFiles == 0) {
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.importing), style = MaterialTheme.typography.bodyMedium)
        }

        state.fileErrors.takeLast(3).forEach { error ->
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${error.fileName}: ${error.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        state.error?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun WebImportTab(
    state: com.novelreader.ui.webimport.WebImportState,
    viewModel: WebImportViewModel,
    onImportComplete: (Long?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = state.url,
            onValueChange = { viewModel.updateUrl(it) },
            label = { Text(stringResource(R.string.web_import_url_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.isLoadingChapters && !state.isImporting
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.fetchChapters() },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.url.isNotBlank() && !state.isLoadingChapters && !state.isImporting
        ) {
            Text(
                if (state.isLoadingChapters) stringResource(R.string.web_import_fetching)
                else stringResource(R.string.web_import_fetch)
            )
        }

        if (state.isLoadingChapters) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        if (state.error != null && !state.isLoadingChapters) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (state.chapters.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))

            if (state.novelTitle.isNotBlank()) {
                Text(
                    text = "${stringResource(R.string.web_import_novel_title_label)} ${state.novelTitle}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (state.coverUrl != null) {
                AsyncImage(
                    model = state.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.selectAll() },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isImporting
                ) {
                    Text(stringResource(R.string.web_import_select_all))
                }
                OutlinedButton(
                    onClick = { viewModel.selectNone() },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isImporting
                ) {
                    Text(stringResource(R.string.web_import_deselect_all))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(state.chapters) { _, chapter ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = chapter.url in state.selectedUrls,
                                onCheckedChange = { viewModel.toggleChapter(chapter.url) },
                                enabled = !state.isImporting
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = chapter.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (state.isImporting) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = {
                        if (state.totalToImport > 0) state.importedCount.toFloat() / state.totalToImport
                        else 0f
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${state.importedCount} / ${state.totalToImport}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (state.errors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.web_import_error_count, state.errors.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                val maxErrors = if (state.importComplete) state.errors.size else 3
                state.errors.take(maxErrors).forEach { err ->
                    Text(
                        text = "${err.url.take(50)} - ${err.message.take(120)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (state.importComplete) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.resetState()
                        onImportComplete(state.importedNovelId)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.back))
                }
            } else if (state.isImporting) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onImportComplete(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.web_import_leave_background))
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.startImport() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.selectedUrls.isNotEmpty()
                ) {
                    Text(stringResource(R.string.web_import_import_selected, state.selectedUrls.size))
                }
            }
        }

        state.cloudflareChallenge?.let { challenge ->
            val expectedHost = remember(challenge.url) {
                try { Uri.parse(challenge.url).host ?: "" } catch (_: Exception) { "" }
            }
            CloudflareChallengeDialog(
                url = challenge.url,
                expectedHost = expectedHost,
                onCookiesCollected = viewModel::onCloudflareCookiesCollected,
                onCancel = viewModel::onCloudflareChallengeCancelled
            )
        }
    }
}
