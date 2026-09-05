package com.rimo.player.domain.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionRuleTest {

    @Test
    fun `newer candidate than installed is downloaded`() {
        assertTrue(VersionRule.shouldDownload(candidate = 2, current = 1))
    }

    @Test
    fun `same version as installed is not downloaded`() {
        assertFalse(VersionRule.shouldDownload(candidate = 1, current = 1))
    }

    @Test
    fun `older candidate than installed is not downloaded`() {
        assertFalse(VersionRule.shouldDownload(candidate = 1, current = 3))
    }

    @Test
    fun `candidate equal to already ready file is not downloaded again`() {
        assertFalse(VersionRule.shouldDownload(candidate = 2, current = 1, ready = 2))
    }

    @Test
    fun `candidate older than ready file is not downloaded`() {
        assertFalse(VersionRule.shouldDownload(candidate = 2, current = 1, ready = 3))
    }

    @Test
    fun `candidate newer than ready file replaces it`() {
        assertTrue(VersionRule.shouldDownload(candidate = 3, current = 1, ready = 2))
    }

    @Test
    fun `ready file is useful only while newer than installed`() {
        assertTrue(VersionRule.isReadyUseful(ready = 2, current = 1))
        assertFalse(VersionRule.isReadyUseful(ready = 2, current = 2))
        assertFalse(VersionRule.isReadyUseful(ready = 1, current = 2))
    }
}
