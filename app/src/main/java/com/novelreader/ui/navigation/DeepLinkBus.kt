package com.novelreader.ui.navigation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class DeepLinkAction {
    data class ViewNovel(val novelId: Long) : DeepLinkAction()
    data class OpenCloudflareSolver(val novelId: Long?) : DeepLinkAction()
}

@Singleton
class DeepLinkBus @Inject constructor() {
    private val _events = MutableSharedFlow<DeepLinkAction>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<DeepLinkAction> = _events.asSharedFlow()

    fun emit(action: DeepLinkAction) {
        _events.tryEmit(action)
    }
}
