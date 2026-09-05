package com.rimo.player.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.rimo.player.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Hands a verified APK to [PackageInstaller]. Success or failure comes back asynchronously to
 * [InstallResultReceiver]; this class only creates and commits the session.
 */
@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** Whether the OS lets this app install packages. When false the caller must send the user to settings first. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /**
     * Copies [apk] into a new install session and commits it. Returns `false` if the session could
     * not even be created (out of space, I/O error); a committed session reports its real result to
     * [InstallResultReceiver]. Never throws.
     */
    suspend fun install(apk: File): Boolean = withContext(ioDispatcher) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = try {
            installer.createSession(params)
        } catch (e: IOException) {
            return@withContext false
        }
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("rimo.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { input -> input.copyTo(out) }
                    session.fsync(out)
                }
                session.commit(confirmationSender(sessionId).intentSender)
            }
            true
        } catch (e: IOException) {
            runCatching { installer.abandonSession(sessionId) }
            false
        }
    }

    private fun confirmationSender(sessionId: Int): PendingIntent {
        val intent = Intent(context, InstallResultReceiver::class.java).apply {
            action = InstallResultReceiver.ACTION_INSTALL_RESULT
        }
        // Mutable so the system can add STATUS and the confirmation intent to the result extras.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, sessionId, intent, flags)
    }
}
