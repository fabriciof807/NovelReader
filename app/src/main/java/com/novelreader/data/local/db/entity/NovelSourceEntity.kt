package com.novelreader.data.local.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "novel_sources",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("novelId"),
        Index(value = ["novelId", "sourceUrl"], unique = true)
    ]
)
data class NovelSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelId: Long,
    val sourceUrl: String,
    val domain: String = "",
    val isPrimary: Boolean = false,
    val lastCheckedAt: Long = 0,
    val autoUpdate: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)
