package com.novelreader.ui.customization

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.local.preferences.AppPreferences
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.data.storage.WallpaperCrop
import com.novelreader.data.storage.WallpaperStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeWallpaperViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val wallpaperStorage: WallpaperStorage
) : ViewModel() {

    val wallpaper: StateFlow<String> = appPreferences.homeWallpaper
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            PreferenceAllowlists.WALLPAPER_NONE
        )

    val blur: StateFlow<Int> = appPreferences.homeWallpaperBlur
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val behindBars: StateFlow<Boolean> = appPreferences.wallpaperBehindBars
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /**
     * Whether the wallpaper asks for the palette's light variants. `null` when there is no wallpaper
     * or its tone cannot be read, in which case the app's own variant stays.
     */
    val wallpaperIsLight: StateFlow<Boolean?> = wallpaper
        .map { toneOf(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private suspend fun toneOf(ref: String): Boolean? = when {
        WallpaperStorage.builtinId(ref) != null -> wallpaperIsLight(ref)
        WallpaperStorage.fileNameOf(ref) != null ->
            sampledWallpaperIsLight(wallpaperStorage.wallpaperLuminance(ref))
        else -> null
    }

    fun updateBehindBars(enabled: Boolean) {
        viewModelScope.launch { appPreferences.updateWallpaperBehindBars(enabled) }
    }

    fun select(ref: String) {
        viewModelScope.launch { appPreferences.updateHomeWallpaper(ref) }
    }

    fun updateBlur(value: Int) {
        viewModelScope.launch { appPreferences.updateHomeWallpaperBlur(value) }
    }

    private val _pendingCrop = MutableStateFlow<Uri?>(null)
    val pendingCrop: StateFlow<Uri?> = _pendingCrop

    private val _cropTone = MutableStateFlow<Boolean?>(null)
    val cropTone: StateFlow<Boolean?> = _cropTone

    fun startCrop(uri: Uri) {
        _pendingCrop.value = uri
        viewModelScope.launch {
            _cropTone.value = sampledWallpaperIsLight(wallpaperStorage.uriLuminance(uri))
        }
    }

    fun cancelCrop() {
        _pendingCrop.value = null
        _cropTone.value = null
    }

    fun applyCrop(crop: WallpaperCrop, targetWidth: Int, targetHeight: Int) {
        val uri = _pendingCrop.value ?: return
        _pendingCrop.value = null
        _cropTone.value = null
        viewModelScope.launch {
            wallpaperStorage.saveCropped(
                slot = WallpaperStorage.SLOT_HOME,
                uri = uri,
                crop = crop,
                targetWidth = targetWidth,
                targetHeight = targetHeight
            )?.let { appPreferences.updateHomeWallpaper(it) }
        }
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            wallpaperStorage.importFromUri(WallpaperStorage.SLOT_HOME, uri)
                ?.let { appPreferences.updateHomeWallpaper(it) }
        }
    }

    fun remove() {
        viewModelScope.launch {
            wallpaperStorage.clearSlot(WallpaperStorage.SLOT_HOME)
            appPreferences.updateHomeWallpaper(PreferenceAllowlists.WALLPAPER_NONE)
        }
    }
}
