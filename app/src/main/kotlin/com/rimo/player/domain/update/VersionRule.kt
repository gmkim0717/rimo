package com.rimo.player.domain.update

/** Pure decisions about version codes. Kept free of Android types so it is trivially unit-testable. */
object VersionRule {

    /**
     * Whether the manifest's [candidate] version is worth downloading.
     *
     * It must be newer than the installed [current] build, and newer than any APK that is
     * already downloaded and verified ([ready]) so we never re-download or downgrade a ready file.
     */
    fun shouldDownload(candidate: Long, current: Long, ready: Long? = null): Boolean =
        candidate > current && (ready == null || candidate > ready)

    /** Whether a previously downloaded APK at [ready] is still an upgrade over the installed [current] build. */
    fun isReadyUseful(ready: Long, current: Long): Boolean = ready > current
}
