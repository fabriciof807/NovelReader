package com.novelreader.data.local.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("novelId")]
)
@Immutable
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelId: Long,
    val title: String,
    val fileName: String,
    val orderIndex: Int,
    val content: String,
    val isRead: Boolean = false,
    val lastScrollPosition: Int = 0,
    @ColumnInfo(defaultValue = "0") val isNew: Boolean = false
)

data class NovelReadCount(
    val novelId: Long,
    val readCount: Int
)

data class NewChapterItem(
    val novelTitle: String,
    val novelId: Long,
    val chapterTitle: String
)
