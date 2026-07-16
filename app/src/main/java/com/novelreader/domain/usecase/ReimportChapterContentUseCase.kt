package com.novelreader.domain.usecase

import android.content.Context
import android.net.Uri
import com.novelreader.data.local.db.dao.ChapterDao
import com.novelreader.data.parser.MhtParser
import com.novelreader.data.parser.ParserRegistry
import com.novelreader.domain.usecase.importnovel.FileCharsetDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReimportChapterContentUseCase @Inject constructor(
    private val fileCharsetDetector: FileCharsetDetector,
    private val parserRegistry: ParserRegistry,
    private val mhtParser: MhtParser,
    private val chapterDao: ChapterDao,
    @ApplicationContext private val context: Context
) {
    suspend fun importFile(chapterId: Long, novelId: Long, uri: Uri): Result<Unit> {
        return try {
            val fileName = fileCharsetDetector.getFileName(uri, context)
            val raw = fileCharsetDetector.readContent(uri, context)
            val parsed = if (mhtParser.isMhtFile(fileName)) {
                parserRegistry.parseRaw(raw, fileName)
            } else {
                parserRegistry.parse(raw, fileName)
            }
            chapterDao.updateContent(chapterId, parsed.content, parsed.chapterTitle)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
