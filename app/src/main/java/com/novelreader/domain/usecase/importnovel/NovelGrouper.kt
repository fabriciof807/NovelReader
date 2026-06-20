package com.novelreader.domain.usecase.importnovel

import android.content.Context
import android.net.Uri
import com.novelreader.data.parser.HtmlSanitizer
import com.novelreader.data.parser.MhtParser
import com.novelreader.data.parser.ParsedChapter
import com.novelreader.data.parser.ParserRegistry
import com.novelreader.util.StringUtils
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedFile(
    val uri: Uri,
    val fileName: String,
    val parsed: ParsedChapter
)

@Singleton
class NovelGrouper @Inject constructor(
    private val parserRegistry: ParserRegistry,
    private val mhtParser: MhtParser,
    private val fileCharsetDetector: FileCharsetDetector
) {
    fun parseAndGroup(
        uris: List<Uri>,
        context: Context,
        onFileError: ((fileName: String, error: String) -> Unit)? = null
    ): Map<String, List<ParsedFile>> {
        val groups = mutableMapOf<String, MutableList<ParsedFile>>()

        for (uri in uris) {
            val fileName = fileCharsetDetector.getFileName(uri, context)
            try {
                val parsed = parseUri(uri, context, fileName) ?: continue
                groups.getOrPut(parsed.novelTitle) { mutableListOf() }
                    .add(ParsedFile(uri, fileName, parsed))
            } catch (e: Exception) {
                onFileError?.invoke(fileName, e.message ?: "Erro desconhecido")
            }
        }

        return groups
    }

    private fun parseUri(uri: Uri, context: Context, fileName: String): ParsedChapter? {
        val raw = fileCharsetDetector.readContent(uri, context) ?: return null
        val fromFileName = fromFileName(fileName)

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
            val fixedNovelTitle = parsed.novelTitle.ifBlank { fromFileName }
            val fixedChapterTitle = parsed.chapterTitle.ifBlank { fromFileName }
            val fixedContent = if (parsed.content.isBlank() && mhtParser.isMhtFile(fileName)) {
                mhtParser.extractHtml(raw)?.let { HtmlSanitizer.sanitizeHtml(it) } ?: parsed.content
            } else parsed.content
            return ParsedChapter(
                novelTitle = fixedNovelTitle,
                chapterTitle = fixedChapterTitle,
                content = fixedContent
            )
        }

        if (mhtParser.isMhtFile(fileName)) {
            val subject = mhtParser.extractSubject(raw) ?: fromFileName
            val html = mhtParser.extractHtml(raw)
            val content = HtmlSanitizer.sanitizeHtml(html ?: raw)
            return ParsedChapter(
                novelTitle = subject,
                chapterTitle = fromFileName,
                content = content
            )
        }

        return null
    }

    private fun fromFileName(fileName: String): String {
        return StringUtils.fromFileName(fileName)
    }
}
