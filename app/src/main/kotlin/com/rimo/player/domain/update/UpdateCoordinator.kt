package com.rimo.player.domain.update

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs the self-update flow once per process:
 *
 * 1. delete APKs that are no longer useful
 * 2. if a verified APK is already on disk, expose it as [UpdateState.ReadyToInstall] right away
 * 3. fetch the manifest; if it is newer than both the installed build and the ready APK, download it
 * 4. on success record the new ready version and expose it
 *
 * Every failure is swallowed and logged. The user never sees an error; the next launch tries again.
 * While a ready APK exists, its [UpdateState.ReadyToInstall] is kept visible during any background
 * check or download so the UI never loses an offer it could make.
 */
class UpdateCoordinator(
    private val currentVersionCode: Long,
    private val manifest: ManifestSource,
    private val apk: ApkSource,
    private val store: UpdateStore,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {},
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val started = AtomicBoolean(false)

    /** Launches [run] on [scope]. Safe to call repeatedly; only the first call does anything. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { run() }
    }

    /** The full flow, exposed for tests. Never throws except for cancellation. */
    suspend fun run() {
        var ready: UpdateState.ReadyToInstall? = null
        try {
            ready = restoreReady()
            store.cleanup(currentVersionCode, ready?.versionCode)

            setProgress(ready, UpdateState.Checking)
            val info = manifest.fetch()
            if (info == null) {
                log("manifest unavailable or invalid")
                return
            }
            if (!VersionRule.shouldDownload(info.versionCode, currentVersionCode, ready?.versionCode)) {
                log("no newer version: server=${info.versionCode} installed=$currentVersionCode ready=${ready?.versionCode}")
                return
            }

            setProgress(ready, UpdateState.Downloading(info.versionCode))
            log("downloading ${info.versionCode} (${info.versionName})")
            val target = store.apkFile(info.versionCode)
            if (!apk.download(info, target)) {
                log("download of ${info.versionCode} failed")
                return
            }

            store.setReady(ReadyVersion(info.versionCode, info.versionName))
            ready?.let { old -> if (old.versionCode != info.versionCode) old.file.delete() }
            ready = UpdateState.ReadyToInstall(info.versionCode, info.versionName, target)
            log("ready to install ${info.versionCode}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log("update flow aborted: ${e::class.simpleName}: ${e.message}")
        } finally {
            _state.value = ready ?: UpdateState.Idle
        }
    }

    /**
     * Called when the system refused to install the ready APK (bad signature, downgrade, ...).
     * The file is useless, so forget it; the next launch will re-download whatever the server offers.
     */
    suspend fun discardReady() {
        val current = _state.value
        if (current is UpdateState.ReadyToInstall) current.file.delete()
        store.clearReady()
        _state.value = UpdateState.Idle
        log("ready apk discarded")
    }

    private suspend fun restoreReady(): UpdateState.ReadyToInstall? {
        val recorded = store.readyVersion() ?: return null
        val file = store.apkFile(recorded.versionCode)
        if (!VersionRule.isReadyUseful(recorded.versionCode, currentVersionCode) || !file.isFile) {
            log("stale ready record ${recorded.versionCode} dropped")
            store.clearReady()
            file.delete()
            return null
        }
        val ready = UpdateState.ReadyToInstall(recorded.versionCode, recorded.versionName, file)
        _state.value = ready
        log("restored ready apk ${recorded.versionCode}")
        return ready
    }

    private fun setProgress(ready: UpdateState.ReadyToInstall?, progress: UpdateState) {
        if (ready == null) _state.value = progress
    }
}
