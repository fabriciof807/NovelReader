package com.novelreader.data.local.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "failed_chapters",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("novelId"), Index("fileName")]
)
@Immutable
data class FailedChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelId: Long,
    val title: String,
    val fileName: String,
    val url: String? = null,
    val sourceType: String,
    val chapterNumber: Int = Int.MAX_VALUE,
    val errorType: String,
    val errorMessage: String,
    val attemptedAt: Long = System.currentTimeMillis()
)

object FailedChapterErrorType {
    const val NETWORK = "network"
    const val PARSE = "parse"
    const val IO = "io"
    const val MISSING_NUMBER = "missing_number"
    const val EMPTY_CONTENT = "empty_content"

    fun classify(e: Throwable): String {
        val msg = (e.message ?: "").lowercase()
        return when {
            msg.contains("timeout") || msg.contains("connect") || msg.contains("network") ||
                msg.contains("unreachable") || msg.contains("refused") -> NETWORK
            msg.contains("https") || msg.contains("security") -> NETWORK
            msg.contains("parse") || msg.contains("html") -> PARSE
            else -> IO
        }
    }
}
