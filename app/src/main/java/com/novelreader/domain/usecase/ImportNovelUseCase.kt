package com.novelreader.domain.usecase

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.novelreader.data.local.db.entity.ChapterEntity
import com.novelreader.data.local.db.entity.NovelEntity
import com.novelreader.data.parser.HtmlSanitizer
import com.novelreader.data.parser.MhtParser
import com.novelreader.data.parser.ParsedChapter
import com.novelreader.data.parser.ParserRegistry
import com.novelreader.data.parser.ChapterNumberExtractor
import com.novelreader.data.repository.ChapterRepository
import com.novelreader.data.repository.NovelRepository
import com.novelreader.R
import com.novelreader.di.qualifiers.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.InputStream
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportNovelUseCase @Inject constructor(
    private val novelRepository: NovelRepository,
    private val chapterRepository: ChapterRepository,
    private val parserRegistry: ParserRegistry,
    private val mhtParser: MhtParser,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    suspend fun importFromFiles(
        uris: List<Uri>,
        context: Context,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
        onFileError: ((fileName: String, error: String) -> Unit)? = null
    ): Map<String, Long> = withContext(io) {
        val results = mutableMapOf<String, Long>()
        val novelGroups = mutableMapOf<String, MutableList<Uri>>()
        val contentCache = mutableMapOf<Uri, ParsedChapter>()
        val fileNameCache = mutableMapOf<Uri, String>()

        for ((index, uri) in uris.withIndex()) {
            val fileName = getFileName(uri, context)
            try {
                val parsed = parseUri(uri, context, fileName)
                if (parsed == null) {
                    onFileError?.invoke(fileName, context.getString(R.string.import_file_error))
                    continue
                }
                fileNameCache[uri] = fileName
                contentCache[uri] = parsed
                novelGroups.getOrPut(parsed.novelTitle) { mutableListOf() }.add(uri)
            } catch (e: Exception) {
                onFileError?.invoke(fileName, e.message ?: context.getString(R.string.import_unknown_error))
            }
            onProgress?.invoke(index + 1, uris.size)
        }

        for ((novelTitle, chapterUris) in novelGroups) {
            var existingNovel = novelRepository.getNovelByTitle(novelTitle)
            var novelId: Long
            val existingChapters = mutableListOf<ChapterEntity>()
            val existingFileNames: Set<String>

            if (existingNovel != null) {
                novelId = existingNovel.id
                existingChapters.addAll(chapterRepository.getChaptersByNovelSync(novelId))
                existingFileNames = existingChapters.map { it.fileName }.toSet()
            } else {
                val novelEntity = NovelEntity(
                    title = novelTitle,
                    sourceFolder = "",
                    totalChapters = 0
                )
                novelId = novelRepository.insert(novelEntity)
                existingFileNames = emptySet()
            }

            val allEntries = mutableListOf<ChapterEntry>()
            for (ch in existingChapters) {
                allEntries.add(ChapterEntry(
                    parsed = ParsedChapter(
                        novelTitle = novelTitle,
                        chapterTitle = ch.title,
                        content = ch.content
                    ),
                    fileName = ch.fileName,
                    existingId = ch.id
                ))
            }
            val seenInBatch = mutableSetOf<String>()
            for (uri in chapterUris) {
                val parsed = contentCache[uri] ?: continue
                val fileName = fileNameCache[uri] ?: continue
                if (fileName in existingFileNames) continue
                if (!seenInBatch.add(fileName)) continue
                allEntries.add(ChapterEntry(parsed, fileName))
            }

            allEntries.sortBy { extractChapterNumber(it.parsed.chapterTitle, it.fileName) }

            val inserts = mutableListOf<ChapterEntity>()
            for ((index, entry) in allEntries.withIndex()) {
                if (entry.existingId != null) {
                    chapterRepository.updateOrderIndex(entry.existingId, index)
                } else {
                    inserts.add(ChapterEntity(
                        novelId = novelId,
                        title = entry.parsed.chapterTitle,
                        fileName = entry.fileName,
                        orderIndex = index,
                        content = entry.parsed.content
                    ))
                }
            }

            if (inserts.isNotEmpty()) {
                chapterRepository.insertAll(inserts)
            }
            novelRepository.updateChapterCount(novelId, allEntries.size)
            results[novelTitle] = novelId
        }

        results
    }

    private data class ChapterEntry(
        val parsed: ParsedChapter,
        val fileName: String,
        val existingId: Long? = null
    )

    private fun extractChapterNumber(title: String, fileName: String): Int {
        return ChapterNumberExtractor.extract(title = title, fileName = fileName)
    }

    private fun parseUri(uri: Uri, context: Context, fileName: String): ParsedChapter? {
        val raw = readContent(uri, context) ?: return null

        val parsed = try {
            if (mhtParser.isMhtFile(fileName)) {
                parserRegistry.parseRaw(raw, fileName)
            } else {
                parserRegistry.parse(raw, fileName)
            }
        } catch (e: Exception) {
            null
        }

        if (parsed != null) {
            val fixedNovelTitle = parsed.novelTitle.ifBlank { fromFileName(fileName) }
            val fixedChapterTitle = parsed.chapterTitle.ifBlank { fromFileName(fileName) }
            val fixedContent = if (parsed.content.isBlank() && mhtParser.isMhtFile(fileName)) {
                mhtParser.extractHtml(raw)?.let { sanitizeRawHtml(it) } ?: parsed.content
            } else parsed.content
            return ParsedChapter(
                novelTitle = fixedNovelTitle,
                chapterTitle = fixedChapterTitle,
                content = fixedContent
            )
        }

        if (mhtParser.isMhtFile(fileName)) {
            val subject = mhtParser.extractSubject(raw) ?: fromFileName(fileName)
            val html = mhtParser.extractHtml(raw)
            val content = sanitizeRawHtml(html ?: raw)
            return ParsedChapter(
                novelTitle = subject,
                chapterTitle = fromFileName(fileName),
                content = content
            )
        }

        return null
    }

    private fun fromFileName(fileName: String): String {
        return fileName.substringBeforeLast(".")
            .replace("-", " ")
            .replace("_", " ")
            .trim()
            .ifEmpty { "Unknown" }
    }

    private fun readContent(uri: Uri, context: Context): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = inputStream.readBytes()
            val charset = detectCharset(bytes)
            String(bytes, charset)
        } catch (e: Exception) {
            null
        }
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

    private fun sanitizeRawHtml(html: String): String = HtmlSanitizer.sanitizeHtml(html)

    private fun getFileName(uri: Uri, context: Context): String {
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
}
