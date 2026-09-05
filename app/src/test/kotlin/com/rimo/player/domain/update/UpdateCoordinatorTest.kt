package com.rimo.player.domain.update

import app.cash.turbine.test
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCoordinatorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val current = 1L

    /** In-memory [UpdateStore] backed by a temp directory. */
    private inner class FakeStore : UpdateStore {
        val dir: File = tmp.newFolder("updates")
        var ready: ReadyVersion? = null
        var cleanupCalls = mutableListOf<Pair<Long, Long?>>()

        override suspend fun readyVersion() = ready
        override suspend fun setReady(version: ReadyVersion) { ready = version }
        override suspend fun clearReady() { ready = null }
        override fun apkFile(versionCode: Long) = File(dir, "$versionCode.apk")
        override suspend fun cleanup(currentVersionCode: Long, keepVersionCode: Long?) {
            cleanupCalls += currentVersionCode to keepVersionCode
        }
    }

    private fun info(code: Long) = UpdateInfo(code, "0.$code.0", "https://example.invalid/$code.apk", "a".repeat(64))

    private fun writingApk(): ApkSource = ApkSource { _, target -> target.writeText("apk"); true }

    private fun coordinator(
        store: FakeStore,
        manifest: ManifestSource,
        apk: ApkSource = writingApk(),
        scope: TestScope,
        logs: MutableList<String> = mutableListOf(),
    ) = UpdateCoordinator(current, manifest, apk, store, scope, log = { logs += it })

    @Test
    fun `happy path goes idle checking downloading ready`() = runTest {
        val store = FakeStore()
        val c = coordinator(store, { info(2) }, scope = this)

        c.state.test {
            assertEquals(UpdateState.Idle, awaitItem())
            c.run()
            assertEquals(UpdateState.Checking, awaitItem())
            assertEquals(UpdateState.Downloading(2), awaitItem())
            val ready = awaitItem() as UpdateState.ReadyToInstall
            assertEquals(2L, ready.versionCode)
            assertEquals("0.2.0", ready.versionName)
            assertEquals(store.apkFile(2), ready.file)
            assertTrue(ready.file.exists())
        }
        assertEquals(ReadyVersion(2, "0.2.0"), store.ready)
        assertEquals(listOf(1L to null), store.cleanupCalls)
    }

    @Test
    fun `manifest failure returns to idle`() = runTest {
        val store = FakeStore()
        val c = coordinator(store, { null }, scope = this)

        c.state.test {
            assertEquals(UpdateState.Idle, awaitItem())
            c.run()
            assertEquals(UpdateState.Checking, awaitItem())
            assertEquals(UpdateState.Idle, awaitItem())
        }
        assertNull(store.ready)
    }

    @Test
    fun `server version not newer means no download`() = runTest {
        val store = FakeStore()
        var downloads = 0
        val c = coordinator(store, { info(1) }, ApkSource { _, _ -> downloads++; true }, scope = this)

        c.state.test {
            assertEquals(UpdateState.Idle, awaitItem())
            c.run()
            assertEquals(UpdateState.Checking, awaitItem())
            assertEquals(UpdateState.Idle, awaitItem())
        }
        assertEquals(0, downloads)
    }

    @Test
    fun `download failure returns to idle and records nothing`() = runTest {
        val store = FakeStore()
        val c = coordinator(store, { info(2) }, ApkSource { _, _ -> false }, scope = this)

        c.state.test {
            assertEquals(UpdateState.Idle, awaitItem())
            c.run()
            assertEquals(UpdateState.Checking, awaitItem())
            assertEquals(UpdateState.Downloading(2), awaitItem())
            assertEquals(UpdateState.Idle, awaitItem())
        }
        assertNull(store.ready)
    }

    @Test
    fun `pre-existing ready apk is exposed immediately and kept while checking`() = runTest {
        val store = FakeStore()
        store.ready = ReadyVersion(2, "0.2.0")
        store.apkFile(2).writeText("old")
        val c = coordinator(store, { info(2) }, scope = this)

        c.state.test {
            assertEquals(UpdateState.Idle, awaitItem())
            c.run()
            val ready = awaitItem() as UpdateState.ReadyToInstall
            assertEquals(2L, ready.versionCode)
            expectNoEvents() // no Checking/Idle flicker while a ready apk exists
        }
        assertEquals(listOf(1L to 2L), store.cleanupCalls)
    }

    @Test
    fun `newer server version replaces the ready apk`() = runTest {
        val store = FakeStore()
        store.ready = ReadyVersion(2, "0.2.0")
        store.apkFile(2).writeText("old")
        val c = coordinator(store, { info(3) }, scope = this)

        c.state.test {
            assertEquals(UpdateState.Idle, awaitItem())
            c.run()
            assertEquals(2L, (awaitItem() as UpdateState.ReadyToInstall).versionCode)
            assertEquals(3L, (awaitItem() as UpdateState.ReadyToInstall).versionCode)
        }
        assertEquals(ReadyVersion(3, "0.3.0"), store.ready)
        assertFalse(store.apkFile(2).exists())
        assertTrue(store.apkFile(3).exists())
    }

    @Test
    fun `ready record without a file is dropped and flow continues`() = runTest {
        val store = FakeStore()
        store.ready = ReadyVersion(2, "0.2.0") // no file on disk
        val c = coordinator(store, { null }, scope = this)

        c.state.test {
            assertEquals(UpdateState.Idle, awaitItem())
            c.run()
            assertEquals(UpdateState.Checking, awaitItem())
            assertEquals(UpdateState.Idle, awaitItem())
        }
        assertNull(store.ready)
    }

    @Test
    fun `ready record not newer than installed is dropped`() = runTest {
        val store = FakeStore()
        store.ready = ReadyVersion(1, "0.1.0")
        store.apkFile(1).writeText("same as installed")
        val c = coordinator(store, { null }, scope = this)

        c.run()

        assertNull(store.ready)
        assertFalse(store.apkFile(1).exists())
    }

    @Test
    fun `exceptions from sources are swallowed and logged`() = runTest {
        val store = FakeStore()
        val logs = mutableListOf<String>()
        val c = coordinator(store, { error("boom") }, scope = this, logs = logs)

        c.run()

        assertEquals(UpdateState.Idle, c.state.value)
        assertTrue(logs.any { it.contains("boom") })
    }

    @Test
    fun `start runs the flow only once`() = runTest {
        val store = FakeStore()
        var fetches = 0
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val c = coordinator(store, { fetches++; null }, scope = scope)

        c.start()
        c.start()
        scope.advanceUntilIdle()

        assertEquals(1, fetches)
    }

    @Test
    fun `discardReady deletes file and clears record`() = runTest {
        val store = FakeStore()
        store.ready = ReadyVersion(2, "0.2.0")
        store.apkFile(2).writeText("apk")
        val c = coordinator(store, { null }, scope = this)
        c.run()
        assertTrue(c.state.value is UpdateState.ReadyToInstall)

        c.discardReady()

        assertEquals(UpdateState.Idle, c.state.value)
        assertNull(store.ready)
        assertFalse(store.apkFile(2).exists())
    }
}
