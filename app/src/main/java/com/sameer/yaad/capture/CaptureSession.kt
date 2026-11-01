package com.sameer.yaad.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface CaptureState {
    data object Idle : CaptureState
    data class Recording(val elapsedMs: Long, val level: Float) : CaptureState
    data object Saved : CaptureState
    data object Cancelled : CaptureState
    data class Failed(val message: String) : CaptureState
}

enum class StopRequest { SAVE, CANCEL }

/** The one live recording, shared between the service that runs it and the screen that shows it. */
object CaptureSession {
    private val mutableState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = mutableState

    @Volatile
    var stopRequest: StopRequest? = null
        private set

    val isRecording: Boolean get() = mutableState.value is CaptureState.Recording

    fun begin() {
        stopRequest = null
        mutableState.value = CaptureState.Recording(elapsedMs = 0, level = 0f)
    }

    fun update(elapsedMs: Long, level: Float) {
        mutableState.value = CaptureState.Recording(elapsedMs, level)
    }

    fun requestStop(request: StopRequest) {
        stopRequest = request
    }

    fun end(result: CaptureState) {
        mutableState.value = result
    }

    fun clearResult() {
        if (!isRecording) mutableState.value = CaptureState.Idle
    }
}
