package com.rimo.player.domain.update

import java.io.File

/** Fetches and parses the remote manifest. `null` means "nothing usable this run". Never throws. */
fun interface ManifestSource {
    suspend fun fetch(): UpdateInfo?
}

/** Downloads and verifies the APK described by [UpdateInfo] into the given file. Never throws. */
fun interface ApkSource {
    suspend fun download(info: UpdateInfo, target: File): Boolean
}
