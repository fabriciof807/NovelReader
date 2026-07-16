package com.novelreader.ui.library.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.novelreader.R
import com.novelreader.data.local.db.entity.CharacterEntity
import com.novelreader.data.local.db.entity.CharacterPhotoEntity

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CharacterCard(
    character: CharacterEntity,
    photos: List<CharacterPhotoEntity>,
    onCardClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onNameUpdate: (String) -> Unit,
    onNotesUpdate: (String?) -> Unit,
    onAddPhotoClick: () -> Unit,
    onPhotoClick: (Int) -> Unit
) {
    var isEditingName by remember { mutableStateOf(false) }
    var isEditingNotes by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(character.name) }
    var editNotes by remember { mutableStateOf(character.notes ?: "") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditingName) {
        if (isEditingName) focusRequester.requestFocus()
    }

    Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable(onClick = onCardClick),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
                    .animateContentSize(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (!character.photoPath.isNullOrBlank()) {
                        AsyncImage(
                            model = character.photoPath,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (photos.size > 1) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "+${photos.size - 1}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.add_photo_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (isEditingName) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (editName.isNotBlank()) {
                                    onNameUpdate(editName.trim())
                                }
                                isEditingName = false
                            }),
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (editName.isNotBlank()) {
                                        onNameUpdate(editName.trim())
                                    }
                                    isEditingName = false
                                }) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = stringResource(R.string.confirm)
                                    )
                                }
                            }
                        )
                    } else {
                        Text(
                            text = character.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (photos.size > 1 || (photos.size == 1 && character.photoPath.isNullOrBlank())) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val thumbnails = if (photos.size <= 4) photos
                            else photos.take(3) + photos.last()
                            thumbnails.forEachIndexed { index, photo ->
                                if (index == 3 && photos.size > 4) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { onPhotoClick(index) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+${photos.size - 3}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { onPhotoClick(index) }
                                    ) {
                                        AsyncImage(
                                            model = photo.photoPath,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    if (isEditingNotes) {
                        OutlinedTextField(
                            value = editNotes,
                            onValueChange = { editNotes = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodySmall,
                            minLines = 1,
                            maxLines = 3,
                            placeholder = { Text(stringResource(R.string.add_note_hint)) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                onNotesUpdate(editNotes.trim().ifBlank { null })
                                isEditingNotes = false
                            }),
                            trailingIcon = {
                                IconButton(onClick = {
                                    onNotesUpdate(editNotes.trim().ifBlank { null })
                                    isEditingNotes = false
                                }) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = stringResource(R.string.confirm),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )
                    } else {
                        Text(
                            text = if (!character.notes.isNullOrBlank()) character.notes
                                   else stringResource(R.string.add_note_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!character.notes.isNullOrBlank())
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    IconButton(
                        onClick = { onToggleFavorite(!character.isFavorite) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (character.isFavorite) Icons.Filled.Star
                                          else Icons.Outlined.Star,
                            contentDescription = if (character.isFavorite)
                                stringResource(R.string.unfavorite)
                            else stringResource(R.string.favorite),
                            tint = if (character.isFavorite) Color(0xFFFFD700)
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onAddPhotoClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = stringResource(R.string.add_photo),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            editName = character.name
                            isEditingName = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit_character),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
}
