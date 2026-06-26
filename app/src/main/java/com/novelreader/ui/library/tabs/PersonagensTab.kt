package com.novelreader.ui.library.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.novelreader.R
import com.novelreader.data.local.db.entity.CharacterEntity
import com.novelreader.data.local.db.entity.CharacterPhotoEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.ui.library.components.AddCharacterDialog
import com.novelreader.ui.library.components.CharacterCard
import com.novelreader.ui.library.components.DeleteCharacterDialog
import com.novelreader.ui.library.components.ImportCharactersDialog

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PersonagensTab(
    characters: List<CharacterEntity>,
    characterPhotos: Map<Long, List<CharacterPhotoEntity>>,
    selectedNovel: NovelEntity?,
    isImporting: Boolean = false,
    importResult: String? = null,
    onClearImportResult: () -> Unit = {},
    onAddCharacter: (String, String?) -> Unit,
    onDeleteCharacter: (Long) -> Unit,
    onAddCharacterPhoto: (Long, String) -> Unit,
    onBatchAddCharacterPhotos: (Long, List<String>) -> Unit,
    onDeleteCharacterPhoto: (Long, Long) -> Unit,
    onUpdateCharacterName: (Long, String) -> Unit,
    onUpdateCharacterNotes: (Long, String?) -> Unit,
    onToggleCharacterFavorite: (Long, Boolean) -> Unit,
    onImportCharacters: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importUrl by remember { mutableStateOf("") }
    var characterToDelete by remember { mutableStateOf<Long?>(null) }
    var expandedCharacterId by remember { mutableStateOf<Long?>(null) }
    var expandedPhotoIndex by remember { mutableStateOf(0) }
    var pendingPhotoCharacterId by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val addPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val charId = pendingPhotoCharacterId ?: return@rememberLauncherForActivityResult
        if (uris.isNotEmpty()) onBatchAddCharacterPhotos(charId, uris.map { it.toString() })
        pendingPhotoCharacterId = null
    }

    LaunchedEffect(showImportDialog) {
        if (showImportDialog && importResult != null) {
            onClearImportResult()
        }
    }

    characterToDelete?.let {
        DeleteCharacterDialog(
            onConfirm = {
                onDeleteCharacter(it)
                characterToDelete = null
            },
            onDismiss = { characterToDelete = null }
        )
    }

    if (showAddDialog) {
        AddCharacterDialog(
            onDismiss = { showAddDialog = false },
            onCreate = { name, photoPath ->
                onAddCharacter(name, photoPath)
                showAddDialog = false
            }
        )
    }

    if (showImportDialog) {
        ImportCharactersDialog(
            importUrl = importUrl,
            onUrlChange = { importUrl = it },
            isImporting = isImporting,
            resultMessage = importResult,
            onConfirm = {
                onImportCharacters?.invoke(importUrl)
            },
            onDismiss = {
                showImportDialog = false
                importUrl = ""
                if (importResult != null) {
                    onClearImportResult()
                }
            }
        )
    }

    expandedCharacterId?.let { expandedId ->
        CharacterPhotoPager(
            characterId = expandedId,
            initialPage = expandedPhotoIndex,
            characterName = characters.find { it.id == expandedId }?.name,
            photos = characterPhotos[expandedId] ?: emptyList(),
            onClose = { expandedCharacterId = null },
            onAddPhoto = {
                pendingPhotoCharacterId = expandedId
                addPhotoPicker.launch("image/*")
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (characters.size > 5) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_characters_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            val filteredCharacters = remember(characters, searchQuery) {
                if (searchQuery.isBlank()) characters
                else characters.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }

            if (filteredCharacters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.no_characters),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 4.dp,
                        top = 4.dp,
                        end = 4.dp,
                        bottom = 140.dp
                    )
                ) {
                    items(filteredCharacters, key = { it.id }) { character ->
                        val photos = characterPhotos[character.id] ?: emptyList()
                        CharacterCard(
                            character = character,
                            photos = photos,
                            onCardClick = {
                                expandedCharacterId = character.id
                                expandedPhotoIndex = 0
                            },
                            onDeleteClick = { characterToDelete = character.id },
                            onToggleFavorite = { fav -> onToggleCharacterFavorite(character.id, fav) },
                            onNameUpdate = { name -> onUpdateCharacterName(character.id, name) },
                            onNotesUpdate = { notes -> onUpdateCharacterNotes(character.id, notes) },
                            onAddPhotoClick = {
                                pendingPhotoCharacterId = character.id
                                addPhotoPicker.launch("image/*")
                            },
                            onPhotoClick = { index ->
                                expandedCharacterId = character.id
                                expandedPhotoIndex = index
                            }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (onImportCharacters != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        importUrl = ""
                        showImportDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        Icons.Default.Public,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.personagens_import_label))
                }
            }
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.personagens_add_label))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CharacterPhotoPager(
    characterId: Long,
    initialPage: Int,
    characterName: String?,
    photos: List<CharacterPhotoEntity>,
    onClose: () -> Unit,
    onAddPhoto: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, maxOf(0, photos.size)),
        pageCount = { photos.size + 1 }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            if (page < photos.size) {
                AsyncImage(
                    model = photos[page].photoPath,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onAddPhoto() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.add_photo_hint),
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        if (characterName != null) {
            Text(
                text = characterName,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp, start = 48.dp, end = 48.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (photos.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(photos.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (pagerState.currentPage == index) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == index) Color.White
                                else Color.White.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.close),
                tint = Color.White
            )
        }
    }
}
