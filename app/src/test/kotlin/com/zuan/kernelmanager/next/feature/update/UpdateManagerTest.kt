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
        assertFalse(isNewerVersion("1.0.0", "1.0.0-beta"))
    }

    @Test
    fun apkAssetIsChosenByExtensionNotPosition() {
        val assets = JSONArray("""[{"name":"notes.txt"},{"name":"ZKM-1.0.1.APK"}]""")
        assertEquals("ZKM-1.0.1.APK", pickApkAsset(assets)?.getString("name"))
        assertNull(pickApkAsset(JSONArray("""[{"name":"source.zip"}]""")))
        assertNull(pickApkAsset(null))
    }

    @Test
    fun sha256IsReadFromGithubDigestField() {
        val hex = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        val asset = JSONObject("""{"name":"ZKM.apk","digest":"sha256:$hex"}""")
        assertEquals(hex, extractSha256(asset, ""))
    }

    @Test
    fun sha256FallsBackToChecksumLineInReleaseNotes() {
        val hex = "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"
        val asset = JSONObject("""{"name":"ZKM.apk"}""")
        assertEquals(hex.lowercase(), extractSha256(asset, "Changelog\n$hex  ZKM.apk\n"))
        assertNull(extractSha256(asset, "no checksum here"))
        assertNull(extractSha256(null, hex))
    }

    @Test
    fun sha256HexMatchesKnownVector() {
        val actual = sha256Hex("abc".byteInputStream())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", actual)
    }
}
