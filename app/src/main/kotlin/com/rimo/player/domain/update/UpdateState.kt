package com.rimo.player.domain.update

import java.io.File

/** Observable progress of the self-update flow. Failures collapse back to [Idle]; nothing is surfaced to the user. */
sealed interface UpdateState {
    /** Nothing in progress and nothing ready to install. */
    data object Idle : UpdateState

    /** Fetching and parsing the update manifest. */
    data object Checking : UpdateState

    /** Downloading and verifying the APK for [versionCode]. */
    data class Downloading(val versionCode: Long) : UpdateState

    /** A verified APK is on disk and may be offered to the user. */
    data class ReadyToInstall(
        val versionCode: Long,
        val versionName: String,
        val file: File,
    ) : UpdateState
}
