package com.rimo.player.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rimo.player.R
import com.rimo.player.data.update.ApkInstaller
import com.rimo.player.ui.update.UpdatePromptDialogFragment
import com.rimo.player.ui.update.UpdatePromptGate
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var promptGate: UpdatePromptGate

    @Inject
    lateinit var apkInstaller: ApkInstaller

    /** APK waiting to be installed once the user has granted the unknown-sources permission. */
    private var pendingApk: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportFragmentManager.setFragmentResultListener(UpdatePromptDialogFragment.REQUEST_KEY, this) { _, result ->
            val versionCode = result.getLong(UpdatePromptDialogFragment.ARG_VERSION_CODE)
            when (result.getString(UpdatePromptDialogFragment.KEY_ACTION)) {
                UpdatePromptDialogFragment.ACTION_INSTALL -> beginInstall(versionCode)
                else -> Log.i(TAG, "install postponed: $versionCode")
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                promptGate.prompt.collect { ready ->
                    if (supportFragmentManager.findFragmentByTag(UpdatePromptDialogFragment.TAG) == null) {
                        UpdatePromptDialogFragment.newInstance(ready.versionCode, ready.versionName)
                            .show(supportFragmentManager, UpdatePromptDialogFragment.TAG)
                    }
                    promptGate.markPrompted()
                }
            }
        }
    }

    private fun beginInstall(versionCode: Long) {
        val ready = promptGate.readyFile() ?: run {
            Log.w(TAG, "install requested but no ready apk")
            return
        }
        Log.i(TAG, "install requested: $versionCode")
        if (apkInstaller.canInstall()) {
            launchInstall(ready)
        } else {
            pendingApk = ready
            requestUnknownSourcesPermission()
        }
    }

    private fun launchInstall(apk: File) {
        lifecycleScope.launch { apkInstaller.install(apk) }
    }

    private fun requestUnknownSourcesPermission() {
        Log.i(TAG, "requesting unknown-sources permission")
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            Log.w(TAG, "no unknown-sources settings screen: ${e.message}")
            pendingApk = null
        }
    }

    override fun onResume() {
        super.onResume()
        // Returning from the unknown-sources settings screen: retry if permission was granted.
        val apk = pendingApk
        if (apk != null && apkInstaller.canInstall()) {
            pendingApk = null
            launchInstall(apk)
        }
    }

    private companion object {
        const val TAG = "UpdatePrompt"
    }
}
