package com.novelreader.domain.usecase.importnovel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileCharsetDetector @Inject constructor() {

    fun readContent(uri: Uri, context: Context): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = inputStream.readBytes()
            val charset = detectCharset(bytes)
            String(bytes, charset)
        } catch (e: Exception) {
            null
        }
    }

    fun getFileName(uri: Uri, context: Context): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    return it.getString(nameIndex) ?: uri.lastPathSegment ?: "unknown"
                }
            }
        }
        return uri.lastPathSegment ?: "unknown"
    }

    private fun detectCharset(bytes: ByteArray): Charset {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) return Charsets.UTF_8

        val header = bytes.take(4096).toByteArray().toString(Charsets.ISO_8859_1)

        val metaPattern = Regex(
            """<meta[^>]+charset\s*=\s*['"]?\s*([^'"\s>]+)""",
            RegexOption.IGNORE_CASE
        )
        metaPattern.find(header)?.let {
            val name = it.groupValues[1].trim()
            return try { Charset.forName(name) } catch (_: Exception) { Charsets.UTF_8 }
        }

        val xmlPattern = Regex(
            """<\?xml[^>]+encoding\s*=\s*['"]\s*([^'"]+)""",
            RegexOption.IGNORE_CASE
        )
        xmlPattern.find(header)?.let {
            val name = it.groupValues[1].trim()
            return try { Charset.forName(name) } catch (_: Exception) { Charsets.UTF_8 }
        }

        return Charsets.UTF_8
    }
}
