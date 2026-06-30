package com.novelreader.ui.webimport

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.domain.usecase.ChapterLink
import com.novelreader.domain.usecase.FetchResult
import com.novelreader.domain.usecase.WebImportUseCase
import com.novelreader.domain.usecase.BackgroundImportManager
import com.novelreader.domain.usecase.webimport.CloudflareChallengeRequiredException
import com.novelreader.domain.usecase.webimport.CloudflareCookieStore
import com.novelreader.domain.usecase.webimport.StoredCookie
import com.novelreader.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WebImportError(
    val url: String,
    val message: String
)

data class CloudflareChallenge(
    val url: String
)

data class WebImportState(
    val url: String = "",
    val isLoadingChapters: Boolean = false,
    val chapters: List<ChapterLink> = emptyList(),
    val novelTitle: String = "",
    val coverUrl: String? = null,
    val selectedUrls: Set<String> = emptySet(),
    val isImporting: Boolean = false,
    val importedCount: Int = 0,
    val totalToImport: Int = 0,
    val errors: List<WebImportError> = emptyList(),
    val importedNovelId: Long? = null,
    val importComplete: Boolean = false,
    val error: String? = null,
    val cloudflareChallenge: CloudflareChallenge? = null,
    val mergeTargetNovelId: Long? = null
)

@HiltViewModel
class WebImportViewModel @Inject constructor(
    application: Application,
    private val webImportUseCase: WebImportUseCase,
    private val backgroundImportManager: BackgroundImportManager,
    private val cookieStore: CloudflareCookieStore,
    private val novelDao: com.novelreader.data.local.db.dao.NovelDao
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(WebImportState())
    val state: StateFlow<WebImportState> = _state

    init {
        viewModelScope.launch {
            backgroundImportManager.state.collect { bgState ->
                _state.update { current ->
                    if (!current.isImporting) return@update current
                    val updated = current.copy(
                        importedCount = bgState.importedCount,
                        totalToImport = bgState.totalToImport,
                        errors = bgState.errorDetails.map { WebImportError(it.url, it.message) }
                    )
                    if (!bgState.running && bgState.completed) {
                        updated.copy(isImporting = false, importComplete = true)
                    } else {
                        updated
                    }
                }
            }
        }
    }

    fun updateUrl(url: String) {
        _state.value = _state.value.copy(url = url)
    }

    fun fetchChapters() {
        val url = _state.value.url.trim()
        if (url.isBlank()) return

        _state.value = _state.value.copy(
            isLoadingChapters = true,
            error = null,
            chapters = emptyList(),
            novelTitle = "",
            selectedUrls = emptySet(),
            cloudflareChallenge = null,
            mergeTargetNovelId = null
        )

        viewModelScope.launch {
            val result = webImportUseCase.fetchChapterList(url)
            result.fold(
                onSuccess = { fetchResult ->
                    val allUrls = fetchResult.chapters.map { it.url }.toSet()
                    val title = fetchResult.novelTitle
                        ?: detectNovelTitle(fetchResult.chapters)
                    val existing = if (title.isNotBlank()) novelDao.getNovelByTitleIgnoreCase(title) else null
                    _state.value = _state.value.copy(
                        isLoadingChapters = false,
                        chapters = fetchResult.chapters,
                        novelTitle = title,
                        coverUrl = fetchResult.coverUrl,
                        selectedUrls = allUrls,
                        mergeTargetNovelId = existing?.id
                    )
                },
                onFailure = { e ->
                    if (e is CloudflareChallengeRequiredException) {
                        _state.value = _state.value.copy(
                            isLoadingChapters = false,
                            cloudflareChallenge = CloudflareChallenge(url = e.url)
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isLoadingChapters = false,
                            error = e.message ?: getApplication<Application>().getString(R.string.web_import_error_fetch)
                        )
                    }
                }
            )
        }
    }

    fun onCloudflareCookiesCollected(cookies: List<Pair<String, String>>) {
        val challenge = _state.value.cloudflareChallenge ?: return
        val stored = cookies.map { (name, value) ->
            StoredCookie(
                name = name,
                value = value,
                domain = java.net.URI(challenge.url).host.orEmpty(),
                path = "/",
                expiresAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
            )
        }
        viewModelScope.launch {
            cookieStore.putCookies(challenge.url, stored)
            _state.value = _state.value.copy(cloudflareChallenge = null)
            fetchChapters()
        }
    }

    fun onCloudflareChallengeCancelled() {
        _state.value = _state.value.copy(cloudflareChallenge = null)
    }

    fun toggleChapter(url: String) {
        val current = _state.value.selectedUrls.toMutableSet()
        if (url in current) current.remove(url) else current.add(url)
        _state.value = _state.value.copy(selectedUrls = current)
    }

    fun selectAll() {
        _state.value = _state.value.copy(
            selectedUrls = _state.value.chapters.map { it.url }.toSet()
        )
    }

    fun selectNone() {
        _state.value = _state.value.copy(selectedUrls = emptySet())
    }

    fun startImport() {
        val selected = _state.value.selectedUrls.toList()
        if (selected.isEmpty()) return
        val selectedLinks = _state.value.chapters.filter { it.url in selected }
        val title = _state.value.novelTitle.ifBlank {
            val first = selectedLinks.firstOrNull()
            if (first != null) detectNovelTitle(_state.value.chapters) else getApplication<Application>().getString(R.string.web_import_unknown_novel)
        }
        val sourceUrl = _state.value.url
        val domain = try {
            java.net.URI(sourceUrl).host?.removePrefix("www.") ?: ""
        } catch (_: Exception) { "" }
        val targetNovelId = _state.value.mergeTargetNovelId

        _state.value = _state.value.copy(
            isImporting = true,
            importComplete = false,
            importedCount = 0,
            totalToImport = selectedLinks.size,
            errors = emptyList()
        )

        viewModelScope.launch {
            backgroundImportManager.startImport(
                novelTitle = title,
                links = selectedLinks,
                coverUrl = _state.value.coverUrl,
                sourceUrl = sourceUrl,
                domain = domain,
                targetNovelId = targetNovelId
            )
        }
    }

    fun resetState() {
        _state.value = WebImportState()
    }

    private fun detectNovelTitle(links: List<ChapterLink>): String {
        val fallback = getApplication<Application>().getString(R.string.web_import_unknown_novel)
        if (links.isEmpty()) return fallback
        val uri = Uri.parse(links.first().url)
        val segments = uri.pathSegments ?: emptyList()
        val novelSegment = segments.firstOrNull {
            it.length > 3 && !it.all { c -> c.isDigit() || c == '-' }
        }
        if (novelSegment != null) {
            return novelSegment.replace('-', ' ')
                .replace('_', ' ')
                .split(' ')
                .filter { it.length > 1 }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                .trim()
                .ifEmpty { fallback }
        }
        return fallback
    }
}
