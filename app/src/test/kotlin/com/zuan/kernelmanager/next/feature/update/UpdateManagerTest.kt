package com.zuan.kernelmanager.next.feature.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun newerVersionUsesNumericComponents() {
        assertTrue(isNewerVersion("v1.10.0", "1.9.9"))
        assertTrue(isNewerVersion("1.0.1", "1.0.0"))
    }

    @Test
    fun equalAndOlderVersionsAreNotUpdates() {
        assertFalse(isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(isNewerVersion("1.9.0", "1.10.0"))
        assertFalse(isNewerVersion("1.1.0-beta.1", "1.1.0"))
        assertFalse(isNewerVersion("1.1.0-beta.1", "1.1.0-beta.1"))
    }

    @Test
    fun betaOrderingFollowsPreReleaseSuffix() {
        assertTrue(isNewerVersion("1.0.0", "1.0.0-beta"))
        assertTrue(isNewerVersion("1.1.0-beta.2", "1.1.0-beta.1"))
        assertTrue(isNewerVersion("1.1.0-beta.10", "1.1.0-beta.9"))
        assertTrue(isNewerVersion("1.1.0-beta.1", "1.0.0"))
    }

    @Test
    fun apkAssetIsChosenByExtensionNotPosition() {
        val assets = JSONArray("""[{"name":"notes.txt"},{"name":"ZKMNext-1.0.1.APK"}]""")
        assertEquals("ZKMNext-1.0.1.APK", pickApkAsset(assets)?.getString("name"))
        assertNull(pickApkAsset(JSONArray("""[{"name":"source.zip"}]""")))
        assertNull(pickApkAsset(null))
    }

    @Test
    fun sha256IsReadFromGithubDigestField() {
        val hex = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        val asset = JSONObject("""{"name":"ZKMNext.apk","digest":"sha256:$hex"}""")
        assertEquals(hex, extractSha256(asset, ""))
    }

    @Test
    fun sha256FallsBackToChecksumLineInReleaseNotes() {
        val hex = "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"
        val asset = JSONObject("""{"name":"ZKMNext.apk"}""")
        assertEquals(hex.lowercase(), extractSha256(asset, "Changelog\n$hex  ZKMNext.apk\n"))
        assertNull(extractSha256(asset, "no checksum here"))
        assertNull(extractSha256(null, hex))
    }

    @Test
    fun sha256HexMatchesKnownVector() {
        val actual = sha256Hex("abc".byteInputStream())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", actual)
    }
}
