package com.novelreader.ui.library.mvi

import com.novelreader.data.local.db.entity.CharacterEntity
import com.novelreader.data.local.db.entity.CharacterPhotoEntity
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.domain.usecase.BackgroundImportState
import com.novelreader.ui.library.ChapterSortOrder
import com.novelreader.ui.library.LibraryStats
import com.novelreader.ui.library.SortOrder
import com.novelreader.ui.library.ViewMode

data class LibraryState(
    val novels: List<NovelEntity> = emptyList(),
    val stats: LibraryStats = LibraryStats(),
    val sortOrder: SortOrder = SortOrder.LAST_READ,
    val viewMode: ViewMode = ViewMode.GRID,
    val selectedNovel: NovelEntity? = null,
    val selectedTab: Int = 0,
    val chapters: List<ChapterEntity> = emptyList(),
    val chapterSortOrder: ChapterSortOrder = ChapterSortOrder.ASCENDING,
    val bookmarkCounts: Map<Long, Int> = emptyMap(),
    val characters: List<CharacterEntity> = emptyList(),
    val characterPhotos: Map<Long, List<CharacterPhotoEntity>> = emptyMap(),
    val showDeleteDialog: NovelEntity? = null,
    val coverTargetNovel: NovelEntity? = null,
    val showUrlDialog: NovelEntity? = null,
    val coverError: String? = null,
    val isImportingCharacters: Boolean = false,
    val characterImportResult: String? = null,
    val backgroundImportState: BackgroundImportState? = null,
    val readProgress: Map<Long, Float> = emptyMap(),
    val errorEvents: List<String> = emptyList()
)
