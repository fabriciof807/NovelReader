package com.novelreader.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.local.preferences.AppPreferences
import com.novelreader.data.local.preferences.ImportPreferences
import com.novelreader.data.local.preferences.QueueMode
import com.novelreader.domain.usecase.ExportDataUseCase
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
    private val importPreferences: ImportPreferences,
    private val exportDataUseCase: ExportDataUseCase
) : ViewModel() {

    val appTheme: StateFlow<String> = appPreferences.appTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val locale: StateFlow<String> = appPreferences.locale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "pt")

    val queueMode: StateFlow<QueueMode> = importPreferences.queueMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QueueMode.SEQUENTIAL)

    private val _exportedJson = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val exportedJson: SharedFlow<String> = _exportedJson

    private val _exportError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val exportError: SharedFlow<String> = _exportError

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

    fun updateQueueMode(mode: QueueMode) {
        viewModelScope.launch {
            importPreferences.setQueueMode(mode)
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
}
