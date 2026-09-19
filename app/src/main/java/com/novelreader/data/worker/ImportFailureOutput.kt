package com.novelreader.data.worker

import androidx.work.Data
import androidx.work.workDataOf

object ImportFailureOutput {
    const val MAX_MESSAGE_CHARS = 512

    fun build(errorType: String, message: String?): Data = workDataOf(
        ChapterImportWorker.KEY_ERROR_TYPE to errorType,
        ChapterImportWorker.KEY_ERROR_MSG to bound(message ?: "Unknown error")
    )

    private fun bound(message: String): String {
        if (message.length <= MAX_MESSAGE_CHARS) return message
        var end = MAX_MESSAGE_CHARS
        if (message[end - 1].isHighSurrogate()) end -= 1
        return message.take(end) + "…"
    }
}
