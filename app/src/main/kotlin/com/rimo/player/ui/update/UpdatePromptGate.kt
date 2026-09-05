package com.rimo.player.ui.update

import com.rimo.player.domain.update.UpdateState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/**
 * Decides when the install prompt may be shown.
 *
 * Rules (spec S2/S3): show at most once per process, and never while video is playing.
 * Process-scoped on purpose: a finished-and-relaunched Activity within the same process
 * must not re-ask, so this is not an Activity ViewModel.
 */
class UpdatePromptGate(updateState: StateFlow<UpdateState>) {

    private val prompted = MutableStateFlow(false)
    private val playbackActive = MutableStateFlow(false)

    /** Emits the ready update exactly when the UI should put the prompt on screen. */
    val prompt: Flow<UpdateState.ReadyToInstall> =
        combine(updateState, prompted, playbackActive) { state, wasPrompted, playing ->
            if (state is UpdateState.ReadyToInstall && !wasPrompted && !playing) state else null
        }.filterNotNull().distinctUntilChanged()

    /** Call as soon as the prompt is shown; suppresses any further prompt this process. */
    fun markPrompted() {
        prompted.value = true
    }

    /** The player screen toggles this; while true the prompt is held back. */
    fun setPlaybackActive(active: Boolean) {
        playbackActive.value = active
    }
}
