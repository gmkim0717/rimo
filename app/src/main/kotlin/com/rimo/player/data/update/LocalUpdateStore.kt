package com.rimo.player.data.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rimo.player.domain.update.ReadyVersion
import com.rimo.player.domain.update.UpdateStore
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * [UpdateStore] on DataStore Preferences plus `<filesDir>/updates/<versionCode>.apk`.
 *
 * `filesDir` (not `cacheDir`) is deliberate: the system may purge the cache under pressure, and a
 * purged ready APK would force a re-download the spec promises not to do.
 */
class LocalUpdateStore(
    private val dataStore: DataStore<Preferences>,
    filesDir: File,
    private val ioDispatcher: CoroutineDispatcher,
) : UpdateStore {

    private val updatesDir = File(filesDir, "updates")

    override suspend fun readyVersion(): ReadyVersion? {
        val prefs = dataStore.data.first()
        val code = prefs[KEY_READY_CODE] ?: return null
        val name = prefs[KEY_READY_NAME] ?: return null
        return ReadyVersion(code, name)
    }

    override suspend fun setReady(version: ReadyVersion) {
        dataStore.edit {
            it[KEY_READY_CODE] = version.versionCode
            it[KEY_READY_NAME] = version.versionName
        }
    }

    override suspend fun clearReady() {
        dataStore.edit {
            it.remove(KEY_READY_CODE)
            it.remove(KEY_READY_NAME)
        }
    }

    override fun apkFile(versionCode: Long): File = File(updatesDir, "$versionCode.apk")

    override suspend fun cleanup(currentVersionCode: Long, keepVersionCode: Long?) = withContext(ioDispatcher) {
        val files = updatesDir.listFiles() ?: return@withContext
        for (f in files) {
            val code = versionCodeOf(f)
            val obsolete = when {
                f.name.endsWith(".part") -> true
                code == null -> true
                code <= currentVersionCode -> true
                keepVersionCode != null && code != keepVersionCode -> true
                else -> false
            }
            if (obsolete) f.delete()
        }
    }

    private fun versionCodeOf(f: File): Long? =
        if (f.name.endsWith(".apk")) f.name.removeSuffix(".apk").toLongOrNull() else null

    private companion object {
        val KEY_READY_CODE = longPreferencesKey("ready_version_code")
        val KEY_READY_NAME = stringPreferencesKey("ready_version_name")
    }
}
