package com.novelreader.data.local.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "novels")
data class NovelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String? = null,
    val coverPath: String? = null,
    val sourceFolder: String = "",
    val totalChapters: Int = 0,
    val lastChapterId: Long? = null,
    val lastReadAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val sourceUrl: String = "",
    val lastCheckedAt: Long = 0,
    val autoUpdate: Boolean = false,
    val hasUpdates: Boolean = false
)
