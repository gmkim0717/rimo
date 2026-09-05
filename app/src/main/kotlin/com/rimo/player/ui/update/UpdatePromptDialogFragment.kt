package com.rimo.player.ui.update

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.rimo.player.R

/**
 * "A new version is ready. Install now?" with exactly two choices.
 *
 * Reports the choice through the Fragment Result API under [REQUEST_KEY]:
 * [ACTION_INSTALL] or [ACTION_LATER]. BACK counts as "later". Initial focus is on "later" so a
 * stray OK press never starts an install.
 */
class UpdatePromptDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.dialog_update_prompt, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val versionName = requireArguments().getString(ARG_VERSION_NAME).orEmpty()
        val versionCode = requireArguments().getLong(ARG_VERSION_CODE)

        view.findViewById<TextView>(R.id.update_body).text = getString(R.string.update_ready_body, versionName)

        val later = view.findViewById<Button>(R.id.update_later)
        val install = view.findViewById<Button>(R.id.update_install_now)
        listOf(later, install).forEach { it.onFocusChangeListener = FocusScale }

        later.setOnClickListener { finish(ACTION_LATER, versionCode) }
        install.setOnClickListener { finish(ACTION_INSTALL, versionCode) }
    }

    override fun onStart() {
        super.onStart()
        // After the window is attached, so the request is not lost before the dialog has focus.
        view?.findViewById<Button>(R.id.update_later)?.let { later -> later.post { later.requestFocus() } }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        setFragmentResult(REQUEST_KEY, bundleOf(KEY_ACTION to ACTION_LATER, ARG_VERSION_CODE to requireArguments().getLong(ARG_VERSION_CODE)))
    }

    private fun finish(action: String, versionCode: Long) {
        setFragmentResult(REQUEST_KEY, bundleOf(KEY_ACTION to action, ARG_VERSION_CODE to versionCode))
        dismiss()
    }

    /** Grows the focused button slightly; the border comes from the state-list background. */
    private object FocusScale : View.OnFocusChangeListener {
        override fun onFocusChange(v: View, hasFocus: Boolean) {
            val scale = if (hasFocus) 1.05f else 1f
            v.animate().scaleX(scale).scaleY(scale).setDuration(100).start()
        }
    }

    companion object {
        const val TAG = "UpdatePromptDialog"
        const val REQUEST_KEY = "update_prompt"
        const val KEY_ACTION = "action"
        const val ACTION_INSTALL = "install"
        const val ACTION_LATER = "later"
        const val ARG_VERSION_CODE = "versionCode"
        private const val ARG_VERSION_NAME = "versionName"

        fun newInstance(versionCode: Long, versionName: String) = UpdatePromptDialogFragment().apply {
            arguments = bundleOf(ARG_VERSION_CODE to versionCode, ARG_VERSION_NAME to versionName)
        }
    }
}
