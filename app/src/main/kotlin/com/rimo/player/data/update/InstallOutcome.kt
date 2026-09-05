package com.rimo.player.data.update

import android.content.pm.PackageInstaller

/** What the app should do after the system reports the result of an install session. */
enum class InstallDecision {
    /** System needs the user to confirm; launch the confirmation intent it handed us. */
    LaunchUserConfirmation,

    /** Installed. The system will restart the app; nothing to do here. */
    Done,

    /** The user backed out. Keep the APK; offer it again next launch. */
    KeepForLater,

    /** The APK is unusable (bad signature, downgrade, corrupt). Discard it. */
    Discard,
}

/** Pure mapping from a [PackageInstaller] status to an [InstallDecision]; unit-tested without Android. */
object InstallOutcome {
    fun decide(status: Int): InstallDecision = when (status) {
        PackageInstaller.STATUS_PENDING_USER_ACTION -> InstallDecision.LaunchUserConfirmation
        PackageInstaller.STATUS_SUCCESS -> InstallDecision.Done
        PackageInstaller.STATUS_FAILURE_ABORTED -> InstallDecision.KeepForLater
        // STATUS_FAILURE, _BLOCKED, _CONFLICT, _INCOMPATIBLE, _INVALID, _STORAGE and anything else:
        // the file will not install as-is, so stop offering it.
        else -> InstallDecision.Discard
    }
}
