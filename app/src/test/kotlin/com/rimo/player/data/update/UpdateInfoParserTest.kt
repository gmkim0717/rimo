package com.rimo.player.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateInfoParserTest {

    private val parser = UpdateInfoParser()
    private val sha = "a".repeat(64)

    private fun manifest(
        versionCode: String = "2",
        versionName: String = "\"0.2.0\"",
        apkUrl: String = "\"https://example.invalid/rimo-0.2.0.apk\"",
        sha256: String = "\"$sha\"",
        extra: String = "",
    ) = """
        {
          "versionCode": $versionCode,
          "versionName": $versionName,
          "apkUrl": $apkUrl,
          "sha256": $sha256
          $extra
        }
    """.trimIndent()

    @Test
    fun `full manifest parses`() {
        val info = parser.parse(
            manifest(extra = """, "apkSizeBytes": 12345678, "changelog": "fixes"""")
        )
        assertNotNull(info)
        assertEquals(2L, info!!.versionCode)
        assertEquals("0.2.0", info.versionName)
        assertEquals("https://example.invalid/rimo-0.2.0.apk", info.apkUrl)
        assertEquals(sha, info.sha256)
        assertEquals(12345678L, info.apkSizeBytes)
        assertEquals("fixes", info.changelog)
    }

    @Test
    fun `optional fields may be omitted`() {
        val info = parser.parse(manifest())
        assertNotNull(info)
        assertNull(info!!.apkSizeBytes)
        assertEquals("", info.changelog)
    }

    @Test
    fun `unknown fields are ignored`() {
        assertNotNull(parser.parse(manifest(extra = """, "futureField": {"a": 1}, "another": [1,2]""")))
    }

    @Test
    fun `missing required field is rejected`() {
        val text = """{"versionCode": 2, "versionName": "0.2.0", "sha256": "$sha"}"""
        assertNull(parser.parse(text))
    }

    @Test
    fun `wrong type is rejected`() {
        assertNull(parser.parse(manifest(versionCode = "\"two\"")))
    }

    @Test
    fun `empty and garbage input are rejected`() {
        assertNull(parser.parse(""))
        assertNull(parser.parse("   "))
        assertNull(parser.parse("<html>not json</html>"))
        assertNull(parser.parse("null"))
        assertNull(parser.parse("[]"))
    }

    @Test
    fun `http apk url is rejected by default`() {
        assertNull(parser.parse(manifest(apkUrl = "\"http://example.invalid/a.apk\"")))
    }

    @Test
    fun `http apk url is accepted when insecure urls are allowed`() {
        val debugParser = UpdateInfoParser(allowInsecureUrls = true)
        assertNotNull(debugParser.parse(manifest(apkUrl = "\"http://192.168.1.10:8000/a.apk\"")))
        assertNotNull(debugParser.parse(manifest()))
    }

    @Test
    fun `non http schemes are rejected`() {
        assertNull(parser.parse(manifest(apkUrl = "\"ftp://example.invalid/a.apk\"")))
        assertNull(parser.parse(manifest(apkUrl = "\"file:///sdcard/a.apk\"")))
        assertNull(parser.parse(manifest(apkUrl = "\"https://\"")))
    }

    @Test
    fun `malformed sha256 is rejected`() {
        assertNull(parser.parse(manifest(sha256 = "\"abc\"")))
        assertNull(parser.parse(manifest(sha256 = "\"${"g".repeat(64)}\"")))
        assertNull(parser.parse(manifest(sha256 = "\"\"")))
    }

    @Test
    fun `sha256 is normalised to lowercase`() {
        val info = parser.parse(manifest(sha256 = "\"${"AB".repeat(32)}\""))
        assertEquals("ab".repeat(32), info!!.sha256)
    }

    @Test
    fun `non positive version code or size is rejected`() {
        assertNull(parser.parse(manifest(versionCode = "0")))
        assertNull(parser.parse(manifest(versionCode = "-1")))
        assertNull(parser.parse(manifest(extra = """, "apkSizeBytes": 0""")))
    }

    @Test
    fun `blank version name is rejected`() {
        assertNull(parser.parse(manifest(versionName = "\"   \"")))
    }
}
