package com.rimo.player.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.rimo.player.di.ApplicationScope
import com.rimo.player.domain.update.UpdateCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Receives the result of an install session and acts on it per [InstallOutcome]:
 * launches the system confirmation screen, or tells [UpdateCoordinator] to discard a bad APK.
 */
@AndroidEntryPoint
class InstallResultReceiver : BroadcastReceiver() {

    @Inject
    lateinit var coordinator: UpdateCoordinator

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_RESULT) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)

        when (InstallOutcome.decide(status)) {
            InstallDecision.LaunchUserConfirmation -> launchConfirmation(context, intent)
            InstallDecision.Done -> Log.i(TAG, "install succeeded")
            InstallDecision.KeepForLater -> Log.i(TAG, "install aborted by user; keeping apk")
            InstallDecision.Discard -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w(TAG, "install failed (status=$status): $msg; discarding apk")
                scope.launch { coordinator.discardReady() }
            }
        }
    }

    private fun launchConfirmation(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        if (confirm == null) {
            Log.w(TAG, "pending user action but no confirmation intent")
            return
        }
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(confirm)
        } catch (e: Exception) {
            Log.w(TAG, "could not launch install confirmation: ${e.message}")
        }
    }

    companion object {
        const val TAG = "ApkInstaller"
        const val ACTION_INSTALL_RESULT = "com.rimo.player.INSTALL_RESULT"
    }
}
