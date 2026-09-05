package com.rimo.player.data.update

import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads the raw text of the update manifest. Returns `null` on any network or HTTP failure;
 * the caller treats that as "no update this run" and never shows an error.
 */
class UpdateInfoFetcher(
    private val client: OkHttpClient,
    private val manifestUrl: String,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /** Fetches [manifestUrl]. `null` for non-2xx responses, oversized bodies, or I/O errors. Never throws. */
    suspend fun fetch(): String? = withContext(ioDispatcher) {
        try {
            val request = Request.Builder()
                .url(manifestUrl)
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                if (body.contentLength() > MAX_BODY_BYTES) return@withContext null
                val text = body.string()
                if (text.length > MAX_BODY_BYTES) null else text
            }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            // Malformed manifestUrl (e.g. placeholder that is not a valid URL).
            null
        }
    }

    private companion object {
        /** A real manifest is a few hundred bytes; anything past this is not ours. */
        const val MAX_BODY_BYTES = 64 * 1024
    }
}
