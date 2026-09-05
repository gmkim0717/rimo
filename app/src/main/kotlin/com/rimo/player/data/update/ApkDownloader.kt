package com.rimo.player.data.update

import com.rimo.player.domain.update.RetryPolicy
import com.rimo.player.domain.update.UpdateInfo
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads an APK to disk, verifying its SHA-256 on the fly.
 *
 * The body is streamed through a small buffer so a 1GB box never holds the APK in memory.
 * Network failures are retried per [retryPolicy], each retry starting from scratch; a checksum
 * mismatch is not retried because the server file itself is wrong.
 *
 * @param sleep how to wait between retries; tests inject a no-op.
 */
class ApkDownloader(
    private val client: OkHttpClient,
    private val retryPolicy: RetryPolicy,
    private val ioDispatcher: CoroutineDispatcher,
    private val sleep: suspend (Duration) -> Unit = { delay(it) },
) {

    sealed interface Result {
        /** [file] exists and its SHA-256 matches the manifest. */
        data class Success(val file: File, val attempts: Int) : Result

        data class Failure(val reason: Reason, val attempts: Int) : Result
    }

    enum class Reason {
        /** Not enough free space for the APK; nothing was attempted. */
        NoSpace,
        /** Every attempt failed with a network or HTTP error. */
        Network,
        /** The download completed but its hash differs from the manifest. */
        ChecksumMismatch,
        /** Local disk error (could not create or rename the file). */
        Disk,
    }

    /**
     * Downloads [info].apkUrl into [target]. A `.part` sibling is used while writing and removed on
     * any failure, so [target] only ever exists as a fully verified file. Never throws except for
     * coroutine cancellation.
     */
    suspend fun download(info: UpdateInfo, target: File): Result = withContext(ioDispatcher) {
        val dir = target.parentFile ?: return@withContext Result.Failure(Reason.Disk, 0)
        if (!dir.isDirectory && !dir.mkdirs()) return@withContext Result.Failure(Reason.Disk, 0)

        val needed = info.apkSizeBytes
        if (needed != null && dir.usableSpace < needed + needed / 5) {
            return@withContext Result.Failure(Reason.NoSpace, 0)
        }

        val part = File(dir, target.name + PART_SUFFIX)
        var attempt = 0
        while (true) {
            attempt++
            ensureActive()
            when (val outcome = attemptOnce(info, part)) {
                AttemptOutcome.Ok -> {
                    if (target.exists() && !target.delete()) {
                        part.delete()
                        return@withContext Result.Failure(Reason.Disk, attempt)
                    }
                    return@withContext if (part.renameTo(target)) {
                        Result.Success(target, attempt)
                    } else {
                        part.delete()
                        Result.Failure(Reason.Disk, attempt)
                    }
                }
                AttemptOutcome.ChecksumMismatch -> {
                    part.delete()
                    return@withContext Result.Failure(Reason.ChecksumMismatch, attempt)
                }
                AttemptOutcome.Disk -> {
                    part.delete()
                    return@withContext Result.Failure(Reason.Disk, attempt)
                }
                AttemptOutcome.Network -> {
                    part.delete()
                    val wait = retryPolicy.delayAfter(attempt)
                        ?: return@withContext Result.Failure(Reason.Network, attempt)
                    sleep(wait)
                }
            }
        }
        // Not reachable: every branch above returns or loops. Kotlin needs an expression here.
        Result.Failure(Reason.Network, attempt)
    }

    private enum class AttemptOutcome { Ok, Network, ChecksumMismatch, Disk }

    private fun attemptOnce(info: UpdateInfo, part: File): AttemptOutcome {
        val request = try {
            Request.Builder().url(info.apkUrl).get().build()
        } catch (e: IllegalArgumentException) {
            return AttemptOutcome.Network
        }
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return AttemptOutcome.Network
                val body = response.body ?: return AttemptOutcome.Network
                try {
                    part.outputStream().buffered().use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(BUFFER_BYTES)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                            }
                        }
                    }
                } catch (e: IOException) {
                    // Distinguish local disk trouble from a dropped connection as best we can.
                    return if (part.parentFile?.let { it.usableSpace == 0L } == true) {
                        AttemptOutcome.Disk
                    } else {
                        AttemptOutcome.Network
                    }
                }
            }
        } catch (e: IOException) {
            return AttemptOutcome.Network
        }
        val actual = digest.digest().toHex()
        return if (actual == info.sha256.lowercase()) AttemptOutcome.Ok else AttemptOutcome.ChecksumMismatch
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val BUFFER_BYTES = 8 * 1024
        const val PART_SUFFIX = ".part"
    }
}
