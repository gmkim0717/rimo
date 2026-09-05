package com.rimo.player.data.update

import android.content.pm.PackageInstaller.STATUS_FAILURE
import android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED
import android.content.pm.PackageInstaller.STATUS_FAILURE_BLOCKED
import android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT
import android.content.pm.PackageInstaller.STATUS_FAILURE_INCOMPATIBLE
import android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID
import android.content.pm.PackageInstaller.STATUS_FAILURE_STORAGE
import android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION
import android.content.pm.PackageInstaller.STATUS_SUCCESS
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallOutcomeTest {

    @Test
    fun `pending user action launches confirmation`() {
        assertEquals(InstallDecision.LaunchUserConfirmation, InstallOutcome.decide(STATUS_PENDING_USER_ACTION))
    }

    @Test
    fun `success is done`() {
        assertEquals(InstallDecision.Done, InstallOutcome.decide(STATUS_SUCCESS))
    }

    @Test
    fun `user abort keeps the apk for later`() {
        assertEquals(InstallDecision.KeepForLater, InstallOutcome.decide(STATUS_FAILURE_ABORTED))
    }

    @Test
    fun `real failures discard the apk`() {
        val discarded = listOf(
            STATUS_FAILURE,
            STATUS_FAILURE_BLOCKED,
            STATUS_FAILURE_CONFLICT,
            STATUS_FAILURE_INCOMPATIBLE,
            STATUS_FAILURE_INVALID,
            STATUS_FAILURE_STORAGE,
        )
        discarded.forEach { assertEquals("status $it", InstallDecision.Discard, InstallOutcome.decide(it)) }
    }

    @Test
    fun `unknown status is treated as discard`() {
        assertEquals(InstallDecision.Discard, InstallOutcome.decide(9999))
    }
}
