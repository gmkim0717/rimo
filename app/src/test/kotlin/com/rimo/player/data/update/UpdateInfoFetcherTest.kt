package com.rimo.player.data.update

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UpdateInfoFetcherTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fetcher(path: String = "/update.json") =
        UpdateInfoFetcher(client, server.url(path).toString(), Dispatchers.IO)

    @Test
    fun `returns body on 200`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"versionCode": 2}"""))
        assertEquals("""{"versionCode": 2}""", fetcher().fetch())
        assertEquals("/update.json", server.takeRequest().path)
    }

    @Test
    fun `returns null on 404`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        assertNull(fetcher().fetch())
    }

    @Test
    fun `returns null on 500`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertNull(fetcher().fetch())
    }

    @Test
    fun `returns null when connection is dropped`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertNull(fetcher().fetch())
    }

    @Test
    fun `returns null when server is unreachable`() = runBlocking {
        val url = server.url("/update.json").toString()
        server.shutdown()
        assertNull(UpdateInfoFetcher(client, url, Dispatchers.IO).fetch())
    }

    @Test
    fun `returns null for oversized body`() = runBlocking {
        server.enqueue(MockResponse().setBody("x".repeat(70 * 1024)))
        assertNull(fetcher().fetch())
    }

    @Test
    fun `returns null for malformed manifest url`() = runBlocking {
        assertNull(UpdateInfoFetcher(client, "not a url", Dispatchers.IO).fetch())
    }
}
