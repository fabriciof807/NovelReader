package com.novelreader.ui.library.mvi

import android.net.Uri

sealed interface LibraryIntent {
    data class LoadNovels(val query: String = "") : LibraryIntent
    data class SortNovels(val sortType: String) : LibraryIntent
    data class SetViewMode(val viewMode: String) : LibraryIntent
    data class DeleteNovel(val novelId: Long) : LibraryIntent
    data class RequestDelete(val novelId: Long) : LibraryIntent
    data object CancelDelete : LibraryIntent
    data class ChangeCover(val novelId: Long, val uri: Uri) : LibraryIntent
    data class RequestChangeCover(val novelId: Long) : LibraryIntent
    data class RequestCoverByUrl(val novelId: Long) : LibraryIntent
    data class SaveCoverFromUrl(val novelId: Long, val url: String) : LibraryIntent
    data object CancelUrlDialog : LibraryIntent
    data object ClearCoverError : LibraryIntent
    data class ToggleAutoUpdate(val novelId: Long) : LibraryIntent
    data object CancelBackgroundImport : LibraryIntent

    data class SelectNovel(val novel: com.novelreader.data.local.db.entity.NovelEntity) : LibraryIntent
    data object DeselectNovel : LibraryIntent
    data class LoadChapters(val novelId: Long) : LibraryIntent
    data object ToggleChapterSortOrder : LibraryIntent

    data class LoadCharacters(val novelId: Long) : LibraryIntent
    data class AddCharacter(val novelId: Long, val name: String, val photoPath: String?) : LibraryIntent
    data class DeleteCharacter(val id: Long) : LibraryIntent
    data class UpdateCharacterPhoto(val id: Long, val path: String) : LibraryIntent
    data class BatchAddCharacterPhotos(val characterId: Long, val photoPaths: List<String>) : LibraryIntent
    data class AddCharacterPhoto(val characterId: Long, val photoPath: String) : LibraryIntent
    data class DeleteCharacterPhoto(val photoId: Long, val characterId: Long) : LibraryIntent
    data class UpdateCharacterName(val characterId: Long, val name: String) : LibraryIntent
    data class UpdateCharacterNotes(val characterId: Long, val notes: String?) : LibraryIntent
    data class ToggleCharacterFavorite(val characterId: Long, val isFavorite: Boolean) : LibraryIntent
    data class ImportCharactersFromUrl(val url: String) : LibraryIntent
    data object ClearCharacterImportResult : LibraryIntent

    data class SelectTab(val index: Int) : LibraryIntent
    data object Init : LibraryIntent
}
