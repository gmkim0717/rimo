package com.rimo.player.data.update

import com.rimo.player.domain.update.UpdateInfo
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Turns the raw `update.json` text into an [UpdateInfo], or `null` when the text is not a usable manifest.
 *
 * Unknown fields are ignored so the manifest can grow without breaking older installs.
 *
 * @param allowInsecureUrls accept `http://` APK URLs; only ever true in debug builds so a LAN
 *   server can be used for testing. Release builds accept `https://` only.
 */
class UpdateInfoParser(private val allowInsecureUrls: Boolean = false) {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Manifest(
        val versionCode: Long,
        val versionName: String,
        val apkUrl: String,
        val sha256: String,
        val apkSizeBytes: Long? = null,
        val changelog: String = "",
    )

    /** Returns the parsed manifest or `null` for malformed JSON, missing fields or invalid values. Never throws. */
    fun parse(text: String): UpdateInfo? {
        val m = try {
            json.decodeFromString<Manifest>(text)
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (m.versionCode <= 0) return null
        if (m.versionName.isBlank()) return null
        if (!isAcceptableUrl(m.apkUrl)) return null
        if (!SHA256_HEX.matches(m.sha256)) return null
        if (m.apkSizeBytes != null && m.apkSizeBytes <= 0) return null
        return UpdateInfo(
            versionCode = m.versionCode,
            versionName = m.versionName.trim(),
            apkUrl = m.apkUrl,
            sha256 = m.sha256.lowercase(),
            apkSizeBytes = m.apkSizeBytes,
            changelog = m.changelog,
        )
    }

    private fun isAcceptableUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.startsWith("https://")) return lower.length > "https://".length
        if (allowInsecureUrls && lower.startsWith("http://")) return lower.length > "http://".length
        return false
    }

    private companion object {
        val SHA256_HEX = Regex("^[0-9a-fA-F]{64}$")
    }
}
