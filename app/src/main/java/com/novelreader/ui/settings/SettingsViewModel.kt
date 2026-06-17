package com.novelreader.ui.settings

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.local.preferences.AppPreferences
import com.novelreader.domain.usecase.ExportDataUseCase
import com.novelreader.domain.usecase.ImportDataUseCase
import com.novelreader.domain.usecase.ImportPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val exportDataUseCase: ExportDataUseCase,
    private val importDataUseCase: ImportDataUseCase
) : ViewModel() {

    val appTheme: StateFlow<String> = appPreferences.appTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val locale: StateFlow<String> = appPreferences.locale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "pt")

    private val _exportedJson = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val exportedJson: SharedFlow<String> = _exportedJson

    private val _exportError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val exportError: SharedFlow<String> = _exportError

    private val _importResult = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val importResult: SharedFlow<String> = _importResult

    private val _importError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val importError: SharedFlow<String> = _importError

    private val _importPreview = MutableSharedFlow<ImportPreview>(extraBufferCapacity = 1)
    val importPreview: SharedFlow<ImportPreview> = _importPreview

    private var pendingImportUri: Uri? = null

    fun updateAppTheme(theme: String) {
        viewModelScope.launch {
            appPreferences.updateAppTheme(theme)
        }
    }

    fun updateLocale(locale: String, activity: Activity) {
        viewModelScope.launch {
            appPreferences.updateLocale(locale)
            activity.recreate()
        }
    }

    fun exportData() {
        viewModelScope.launch {
            try {
                val json = exportDataUseCase.execute()
                _exportedJson.emit(json)
            } catch (e: Exception) {
                _exportError.emit(e.message ?: "Erro ao exportar dados")
            }
        }
    }

    fun previewImport(uri: Uri) {
        viewModelScope.launch {
            try {
                val preview = importDataUseCase.previewImport(uri)
                pendingImportUri = uri
                _importPreview.emit(preview)
            } catch (e: Exception) {
                _importError.emit(e.message ?: "Erro ao ler arquivo")
            }
        }
    }

    fun importSelected(selectedTitles: Set<String>) {
        val uri = pendingImportUri ?: return
        pendingImportUri = null
        viewModelScope.launch {
            try {
                val result = importDataUseCase.execute(uri, selectedTitles)
                val sb = StringBuilder()
                if (result.novelsQueued.isNotEmpty()) {
                    sb.append("${result.novelsQueued.size} novel(is) na fila. ")
                }
                if (result.novelsFailed.isNotEmpty()) {
                    sb.append("${result.novelsFailed.size} falha(s). ")
                }
                if (result.bookmarksPending > 0) {
                    sb.append("${result.bookmarksPending} bookmark(s) pendente(s). ")
                }
                if (result.charactersPending > 0) {
                    sb.append("${result.charactersPending} personagen(s) pendente(s).")
                }
                _importResult.emit(sb.toString().trimEnd())
            } catch (e: Exception) {
                _importError.emit(e.message ?: "Erro ao importar dados")
            }
        }
    }
}
