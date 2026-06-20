package com.novelreader.domain.usecase

data class ChapterLink(
    val title: String,
    val url: String,
    val chapterNumber: Int = Int.MAX_VALUE
)

data class FetchResult(
    val chapters: List<ChapterLink>,
    val coverUrl: String? = null,
    val novelTitle: String? = null
)
