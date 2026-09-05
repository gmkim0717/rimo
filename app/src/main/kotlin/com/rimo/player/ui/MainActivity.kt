package com.rimo.player.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rimo.player.R
import com.rimo.player.ui.update.UpdatePromptDialogFragment
import com.rimo.player.ui.update.UpdatePromptGate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var promptGate: UpdatePromptGate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportFragmentManager.setFragmentResultListener(UpdatePromptDialogFragment.REQUEST_KEY, this) { _, result ->
            val versionCode = result.getLong(UpdatePromptDialogFragment.ARG_VERSION_CODE)
            when (result.getString(UpdatePromptDialogFragment.KEY_ACTION)) {
                UpdatePromptDialogFragment.ACTION_INSTALL -> Log.i(TAG, "install requested: $versionCode")
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

    private companion object {
        const val TAG = "UpdatePrompt"
    }
}
