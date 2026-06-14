package com.novelreader.data.local.db.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = ChapterEntity::class)
@Entity(tableName = "chapters_fts")
data class ChapterFts(
    val title: String = "",
    val content: String = ""
)
