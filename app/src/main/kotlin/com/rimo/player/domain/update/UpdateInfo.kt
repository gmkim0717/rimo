package com.rimo.player.domain.update

/**
 * A parsed update manifest: what the server says the newest build is.
 *
 * @property versionCode integer compared against the installed build; must strictly increase per release
 * @property versionName human-readable version shown in the install prompt
 * @property apkUrl https URL of the signed APK
 * @property sha256 lowercase hex SHA-256 of the APK; a download is discarded unless it matches
 * @property apkSizeBytes optional size used for a free-space check before downloading
 * @property changelog free text; carried but not shown in the first release
 */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val apkSizeBytes: Long? = null,
    val changelog: String = "",
)
