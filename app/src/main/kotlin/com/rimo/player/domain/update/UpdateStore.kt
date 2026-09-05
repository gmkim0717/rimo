package com.rimo.player.domain.update

import java.io.File

/** A downloaded, hash-verified APK that is waiting for the user to install it. */
data class ReadyVersion(val versionCode: Long, val versionName: String)

/**
 * Persistence for the update flow: which APK is ready, and where APK files live.
 * Implemented on DataStore + `filesDir/updates` in the data layer; faked in tests.
 */
interface UpdateStore {
    /** The recorded ready version, or `null` when none is recorded. */
    suspend fun readyVersion(): ReadyVersion?

    suspend fun setReady(version: ReadyVersion)

    suspend fun clearReady()

    /** Deterministic location for the APK of [versionCode]; the file may or may not exist. */
    fun apkFile(versionCode: Long): File

    /**
     * Deletes APKs that can no longer be useful: any at or below [currentVersionCode], every
     * leftover `.part` file, and any APK other than [keepVersionCode] when it is given.
     */
    suspend fun cleanup(currentVersionCode: Long, keepVersionCode: Long?)
}
