package com.rimo.player.data.update

import com.rimo.player.domain.update.RetryPolicy
import com.rimo.player.domain.update.UpdateInfo
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var dir: File
    private val slept = mutableListOf<Duration>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    private val payload: ByteArray = Random(42).nextBytes(300_000)
    private val payloadSha = sha256Hex(payload)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dir = tmp.newFolder("updates")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun downloader(policy: RetryPolicy = RetryPolicy()) =
        ApkDownloader(client, policy, Dispatchers.IO) { slept += it }

    private fun info(sha: String = payloadSha, size: Long? = null, path: String = "/rimo.apk") = UpdateInfo(
        versionCode = 2,
        versionName = "0.2.0",
        apkUrl = server.url(path).toString(),
        sha256 = sha,
        apkSizeBytes = size,
    )

    private fun body(bytes: ByteArray) = MockResponse().setBody(Buffer().write(bytes))

    private fun target() = File(dir, "2.apk")
    private fun part() = File(dir, "2.apk.part")

    @Test
    fun `downloads and verifies on first attempt`() = runBlocking {
        server.enqueue(body(payload))

        val result = downloader().download(info(), target())

        val success = result as ApkDownloader.Result.Success
        assertEquals(1, success.attempts)
        assertEquals(target(), success.file)
        assertArrayEquals(payload, target().readBytes())
        assertFalse(part().exists())
        assertTrue(slept.isEmpty())
    }

    @Test
    fun `retries after dropped connections and succeeds on third attempt`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(body(payload).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        server.enqueue(body(payload))

        val result = downloader().download(info(), target())

        val success = result as ApkDownloader.Result.Success
        assertEquals(3, success.attempts)
        assertArrayEquals(payload, target().readBytes())
        assertFalse(part().exists())
        assertEquals(2, slept.size)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `gives up after retry budget with no files left behind`() = runBlocking {
        repeat(6) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }

        val result = downloader().download(info(), target())

        val failure = result as ApkDownloader.Result.Failure
        assertEquals(ApkDownloader.Reason.Network, failure.reason)
        assertEquals(6, failure.attempts) // 1 initial + 5 retries
        assertEquals(5, slept.size)
        assertEquals(6, server.requestCount)
        assertFalse(target().exists())
        assertFalse(part().exists())
    }

    @Test
    fun `http error is treated as network failure and retried`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(body(payload))

        val result = downloader().download(info(), target())

        assertTrue(result is ApkDownloader.Result.Success)
        assertEquals(2, (result as ApkDownloader.Result.Success).attempts)
    }

    @Test
    fun `checksum mismatch discards file without retrying`() = runBlocking {
        server.enqueue(body(payload))
        server.enqueue(body(payload))

        val result = downloader().download(info(sha = "0".repeat(64)), target())

        val failure = result as ApkDownloader.Result.Failure
        assertEquals(ApkDownloader.Reason.ChecksumMismatch, failure.reason)
        assertEquals(1, failure.attempts)
        assertEquals(1, server.requestCount)
        assertFalse(target().exists())
        assertFalse(part().exists())
    }

    @Test
    fun `refuses to start when free space is insufficient`() = runBlocking {
        server.enqueue(body(payload))

        val result = downloader().download(info(size = Long.MAX_VALUE / 2), target())

        val failure = result as ApkDownloader.Result.Failure
        assertEquals(ApkDownloader.Reason.NoSpace, failure.reason)
        assertEquals(0, failure.attempts)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `size hint within free space does not block download`() = runBlocking {
        server.enqueue(body(payload))

        val result = downloader().download(info(size = payload.size.toLong()), target())

        assertTrue(result is ApkDownloader.Result.Success)
    }

    @Test
    fun `replaces an existing target file`() = runBlocking {
        target().writeBytes(byteArrayOf(1, 2, 3))
        server.enqueue(body(payload))

        val result = downloader().download(info(), target())

        assertTrue(result is ApkDownloader.Result.Success)
        assertArrayEquals(payload, target().readBytes())
    }

    @Test
    fun `creates missing parent directory`() = runBlocking {
        val nested = File(dir, "deeper/2.apk")
        server.enqueue(body(payload))

        val result = downloader().download(info(), nested)

        assertTrue(result is ApkDownloader.Result.Success)
        assertTrue(nested.exists())
    }

    @Test
    fun `streams a large body without holding it in memory`() = runBlocking {
        // 16 MB is larger than any realistic heap we would want to spend on a single buffer;
        // the 8 KB streaming path must handle it and still produce the right hash.
        val big = ByteArray(16 * 1024 * 1024) { (it % 251).toByte() }
        server.enqueue(body(big))

        val result = downloader().download(info(sha = sha256Hex(big)), target())

        assertTrue(result is ApkDownloader.Result.Success)
        assertEquals(big.size.toLong(), target().length())
    }

    @Test
    fun `zero retries policy fails after single attempt`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = downloader(RetryPolicy(maxRetries = 0)).download(info(), target())

        val failure = result as ApkDownloader.Result.Failure
        assertEquals(1, failure.attempts)
        assertTrue(slept.isEmpty())
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
