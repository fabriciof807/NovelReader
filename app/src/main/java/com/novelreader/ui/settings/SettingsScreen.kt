package com.novelreader.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.novelreader.R
import com.novelreader.domain.usecase.ExportOptions
import com.novelreader.domain.usecase.ImportPreview
import com.novelreader.domain.usecase.ImportPreviewEntry
import com.novelreader.domain.usecase.ImportResult
import com.novelreader.data.storage.WallpaperStorage
import com.novelreader.ui.customization.AccentColorPicker
import com.novelreader.ui.customization.BlurSlider
import com.novelreader.ui.customization.HomeWallpaperViewModel
import com.novelreader.ui.customization.SavedThemesSection
import com.novelreader.ui.customization.WallpaperBehindBarsRow
import com.novelreader.ui.customization.WallpaperChoiceRow
import com.novelreader.ui.customization.PalettePicker
import com.novelreader.ui.customization.appPaletteChoices
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAboutClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val appTheme by viewModel.appTheme.collectAsState()
    val appPalette by viewModel.appPalette.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val savedThemes by viewModel.savedThemes.collectAsState()
    val isDarkTheme = when (appTheme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val locale by viewModel.locale.collectAsState()
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val jsonRef = remember { mutableStateOf<String?>(null) }

    val wallpaperViewModel: HomeWallpaperViewModel = hiltViewModel()
    val homeWallpaper by wallpaperViewModel.wallpaper.collectAsState()
    val homeWallpaperBlur by wallpaperViewModel.blur.collectAsState()
    val homeWallpaperBehindBars by wallpaperViewModel.behindBars.collectAsState()
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { wallpaperViewModel.importFromUri(it) }
    }

    val paletteExpanded = remember { mutableStateOf(true) }
    val themeExpanded = remember { mutableStateOf(true) }
    val langExpanded = remember { mutableStateOf(false) }
    var showExportConfirm by remember { mutableStateOf(false) }
    var exportNovels by remember { mutableStateOf(true) }
    var exportBookmarks by remember { mutableStateOf(true) }
    var exportCharacters by remember { mutableStateOf(true) }
    var exportCollections by remember { mutableStateOf(true) }
    var exportSettings by remember { mutableStateOf(true) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var importPreview by remember { mutableStateOf<ImportPreview?>(null) }
    var selectedImportTitles by remember { mutableStateOf<Set<String>>(emptySet()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isImporting by viewModel.isImporting.collectAsState()
    val lastImportResult by viewModel.lastImportResult.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            jsonRef.value?.let { json ->
                scope.launch {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(json.toByteArray())
                        }
                        snackbarHostState.showSnackbar(context.getString(R.string.export_success))
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(context.getString(R.string.export_error))
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.previewImport(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportedJson.collect { json ->
            jsonRef.value = json
            exportLauncher.launch("novelreader_backup.json")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportError.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.importResult.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.importError.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.importPreview.collect { preview ->
            importPreview = preview
            selectedImportTitles = preview.novels.map { it.title }.toSet()
        }
    }

    if (showExportConfirm) {
        val canExport = exportNovels || exportBookmarks || exportCharacters ||
            exportCollections || exportSettings
        AlertDialog(
            onDismissRequest = { showExportConfirm = false },
            title = { Text(stringResource(R.string.export_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.export_confirm_msg))
                    Spacer(modifier = Modifier.height(8.dp))
                    ExportOptionRow(
                        label = stringResource(R.string.export_novels),
                        description = stringResource(R.string.export_novels_desc),
                        checked = exportNovels,
                        onCheckedChange = { exportNovels = it }
                    )
                    ExportOptionRow(
                        label = stringResource(R.string.export_bookmarks),
                        description = stringResource(R.string.export_bookmarks_desc),
                        checked = exportBookmarks,
                        onCheckedChange = { exportBookmarks = it }
                    )
                    ExportOptionRow(
                        label = stringResource(R.string.export_characters),
                        description = stringResource(R.string.export_characters_desc),
                        checked = exportCharacters,
                        onCheckedChange = { exportCharacters = it }
                    )
                    ExportOptionRow(
                        label = stringResource(R.string.export_collections),
                        description = stringResource(R.string.export_collections_desc),
                        checked = exportCollections,
                        onCheckedChange = { exportCollections = it }
                    )
                    ExportOptionRow(
                        label = stringResource(R.string.export_settings),
                        description = stringResource(R.string.export_settings_desc),
                        checked = exportSettings,
                        onCheckedChange = { exportSettings = it }
                    )
                    if (!canExport) {
                        Text(
                            text = stringResource(R.string.export_select_at_least_one),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportConfirm = false
                    viewModel.exportData(
                        ExportOptions(
                            novels = exportNovels,
                            bookmarks = exportBookmarks,
                            characters = exportCharacters,
                            collections = exportCollections,
                            settings = exportSettings
                        )
                    )
                }, enabled = canExport) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showExportConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { Text(stringResource(R.string.import_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    importLauncher.launch(arrayOf("application/json"))
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (importPreview != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { importPreview = null },
            sheetState = sheetState
        ) {
            val preview = importPreview!!
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.import_preview_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                Text(
                    text = stringResource(
                        R.string.import_preview_count,
                        preview.novels.size,
                        preview.bookmarksCount,
                        preview.charactersCount,
                        preview.collectionsCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(preview.novels, key = { it.title }) { entry ->
                        val checked = entry.title in selectedImportTitles
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedImportTitles = if (checked)
                                        selectedImportTitles - entry.title
                                    else
                                        selectedImportTitles + entry.title
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selectedImportTitles = if (checked)
                                        selectedImportTitles - entry.title
                                    else
                                        selectedImportTitles + entry.title
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (entry.sourceUrl.isNotBlank()) {
                                    Text(
                                        text = entry.sourceUrl,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val titles = selectedImportTitles.toSet()
                        importPreview = null
                        viewModel.importSelected(titles)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    enabled = selectedImportTitles.isNotEmpty() || preview.novels.isEmpty()
                ) {
                    Text(
                        if (preview.novels.isEmpty()) stringResource(R.string.import_preview_import_no_novels)
                        else stringResource(R.string.import_preview_import, selectedImportTitles.size)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (lastImportResult != null) {
        val result = lastImportResult!!
        AlertDialog(
            onDismissRequest = { viewModel.clearImportResult() },
            title = { Text(stringResource(R.string.import_result_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.import_result_novels, result.novelsQueued.size))
                    if (result.novelsFailed.isNotEmpty()) {
                        Text(
                            stringResource(R.string.import_result_failed, result.novelsFailed.size),
                            color = MaterialTheme.colorScheme.error
                        )
                        result.novelsFailed.forEach { name ->
                            Text(
                                text = "• $name",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (result.novelsLocal.isNotEmpty()) {
                        Text(
                            stringResource(R.string.import_result_local, result.novelsLocal.size),
                            color = MaterialTheme.colorScheme.error
                        )
                        result.novelsLocal.forEach { name ->
                            Text(
                                text = "• $name",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (result.settingsApplied) {
                        Text(stringResource(R.string.import_result_settings))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(
                            R.string.import_result_bookmarks,
                            result.bookmarksRestored,
                            result.bookmarksPending
                        )
                    )
                    Text(
                        stringResource(
                            R.string.import_result_characters,
                            result.charactersRestored,
                            result.charactersPending
                        )
                    )
                    if (result.collectionLinksRestored > 0 || result.collectionLinksPending > 0) {
                        Text(
                            stringResource(
                                R.string.import_result_collections,
                                result.collectionLinksRestored,
                                result.collectionLinksPending
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearImportResult() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(
                title = stringResource(R.string.app_theme),
                expanded = themeExpanded.value,
                onToggle = { themeExpanded.value = !themeExpanded.value }
            ) {
                ThemeOption(
                    label = stringResource(R.string.system),
                    icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(24.dp)) },
                    description = stringResource(R.string.system_desc),
                    selected = appTheme == "system",
                    onClick = { viewModel.updateAppTheme("system") }
                )
                Spacer(modifier = Modifier.height(4.dp))
                ThemeOption(
                    label = stringResource(R.string.light),
                    icon = { Icon(Icons.Default.BrightnessHigh, contentDescription = null, modifier = Modifier.size(24.dp)) },
                    description = stringResource(R.string.light_desc),
                    selected = appTheme == "light",
                    onClick = { viewModel.updateAppTheme("light") }
                )
                Spacer(modifier = Modifier.height(4.dp))
                ThemeOption(
                    label = stringResource(R.string.dark),
                    icon = { Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(24.dp)) },
                    description = stringResource(R.string.dark_desc),
                    selected = appTheme == "dark",
                    onClick = { viewModel.updateAppTheme("dark") }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSection(
                title = stringResource(R.string.appearance),
                expanded = paletteExpanded.value,
                onToggle = { paletteExpanded.value = !paletteExpanded.value }
            ) {
                PalettePicker(
                    selectedId = appPalette,
                    choices = appPaletteChoices(isDarkTheme, isAndroid12OrLater),
                    onSelect = { viewModel.updateAppPalette(it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.accent_color),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                AccentColorPicker(
                    selected = accentColor,
                    background = MaterialTheme.colorScheme.background,
                    fallback = MaterialTheme.colorScheme.secondary,
                    onSelect = { viewModel.updateAccentColor(it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.wallpaper_home),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                WallpaperChoiceRow(
                    selectedRef = homeWallpaper,
                    hasImage = WallpaperStorage.fileNameOf(homeWallpaper) != null,
                    onPickImage = { wallpaperPicker.launch("image/*") },
                    onSelectBuiltin = { wallpaperViewModel.select(it) },
                    onRemove = { wallpaperViewModel.remove() }
                )
                Spacer(modifier = Modifier.height(16.dp))
                SavedThemesSection(
                    themes = savedThemes,
                    dark = isDarkTheme,
                    onSave = { viewModel.saveTheme(it) },
                    onApply = { viewModel.applyTheme(it) },
                    onDelete = { viewModel.deleteTheme(it) },
                    onReset = { viewModel.resetAppearance() }
                )

                Spacer(modifier = Modifier.height(8.dp))
                BlurSlider(
                    initial = homeWallpaperBlur,
                    onCommit = { wallpaperViewModel.updateBlur(it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                WallpaperBehindBarsRow(
                    checked = homeWallpaperBehindBars,
                    onCheckedChange = { wallpaperViewModel.updateBehindBars(it) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSection(
                title = stringResource(R.string.language),
                expanded = langExpanded.value,
                onToggle = { langExpanded.value = !langExpanded.value }
            ) {
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
                Spacer(modifier = Modifier.height(4.dp))
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
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        exportNovels = true
                        exportBookmarks = true
                        exportCharacters = true
                        exportCollections = true
                        exportSettings = true
                        showExportConfirm = true
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            stringResource(R.string.export_data),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            stringResource(R.string.export_data_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showImportConfirm = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            stringResource(R.string.import_data),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            stringResource(R.string.import_data_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAboutClick),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
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

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (isImporting) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
    }
}

@Composable
private fun ExportOptionRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Checkbox
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null
        )
        Column(modifier = Modifier.padding(end = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Column {
            content()
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
                .padding(12.dp),
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
                .padding(12.dp),
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
