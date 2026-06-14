package com.novelreader.ui.import_novel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.domain.usecase.ImportNovelUseCase
import com.novelreader.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportError(
    val fileName: String,
    val message: String
)

data class ImportState(
    val isImporting: Boolean = false,
    val importedNovelId: Long? = null,
    val importedTitle: String? = null,
    val error: String? = null,
    val totalFiles: Int = 0,
    val processedFiles: Int = 0,
    val fileErrors: List<ImportError> = emptyList()
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    application: Application,
    private val importNovelUseCase: ImportNovelUseCase
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state

    private val _errorEvents = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    fun importFiles(uris: List<Uri>) {
        viewModelScope.launch {
            _state.value = ImportState(
                isImporting = true,
                totalFiles = uris.size
            )
            try {
                val fileErrors = mutableListOf<ImportError>()
                val results = importNovelUseCase.importFromFiles(
                    uris = uris,
                    context = getApplication(),
                    onProgress = { processed, total ->
                        _state.value = _state.value.copy(processedFiles = processed)
                    },
                    onFileError = { fileName, message ->
                        fileErrors.add(ImportError(fileName, message))
                    }
                )
                val firstEntry = results.entries.firstOrNull()
                if (firstEntry != null) {
                    _state.value = ImportState(
                        importedNovelId = firstEntry.value,
                        importedTitle = firstEntry.key,
                        fileErrors = fileErrors
                    )
                } else {
                    _state.value = ImportState(
                        error = if (fileErrors.isEmpty()) {
                            getApplication<Application>().getString(R.string.import_no_valid_chapters)
                        } else {
                            getApplication<Application>().getString(R.string.import_no_valid_chapters_with_errors)
                        },
                        fileErrors = fileErrors,
                        totalFiles = uris.size,
                        processedFiles = uris.size
                    )
                }
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: getApplication<Application>().getString(R.string.import_generic_error))
                _state.value = ImportState(
                    error = e.message ?: getApplication<Application>().getString(R.string.import_generic_error),
                    totalFiles = uris.size,
                    processedFiles = uris.size
                )
            }
        }
    }

    fun resetState() {
        _state.value = ImportState()
    }
}
