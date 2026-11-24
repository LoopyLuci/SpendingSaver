package com.spendingsaver.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

internal class UpdateLogicTest {
    @Test fun versionOrdering() {
        assertTrue(UpdateLogic.isNewer("1.2.0", "1.1.9"))
        assertTrue(UpdateLogic.isNewer("v2.0", "1.9.9"))
        assertTrue(UpdateLogic.isNewer("1.10.0", "1.9.0"))
        assertFalse(UpdateLogic.isNewer("1.1.0", "1.1.0"))
        assertFalse(UpdateLogic.isNewer("1.1", "1.1.0"))
        assertFalse(UpdateLogic.isNewer("1.0.9", "1.1.0"))
        assertFalse(UpdateLogic.isNewer("nightly", "1.0"))
    }

    @Test fun parseVersionIgnoresSuffix() {
        assertEquals(listOf(1, 2, 3), UpdateLogic.parseVersion("v1.2.3-beta.1"))
        assertNull(UpdateLogic.parseVersion("latest"))
    }

    @Test fun onlyHttpsGithubHostsAreAllowed() {
        assertTrue(UpdateLogic.isAllowedUrl("https://github.com/o/r/releases/download/v1/a.apk"))
        assertTrue(UpdateLogic.isAllowedUrl("https://objects.githubusercontent.com/x"))
        assertFalse(UpdateLogic.isAllowedUrl("http://github.com/o/r/a.apk"))
        assertFalse(UpdateLogic.isAllowedUrl("https://github.com.evil.example/a.apk"))
        assertFalse(UpdateLogic.isAllowedUrl("https://evil.example/github.com/a.apk"))
        assertFalse(UpdateLogic.isAllowedUrl("not a url"))
    }

    @Test fun repoValidation() {
        assertTrue(UpdateLogic.isValidRepo("LoopyLuci/SpendingSaver"))
        assertFalse(UpdateLogic.isValidRepo("no-slash"))
        assertFalse(UpdateLogic.isValidRepo("a/b/c"))
        assertFalse(UpdateLogic.isValidRepo("a/b?x=1"))
    }

    private val sha = "a".repeat(64)
    private fun release(extra: String = "", assets: String = """[{"name":"SpendingSaver-1.2.0.apk","size":12345,"browser_download_url":"https://github.com/o/r/releases/download/v1.2.0/SpendingSaver-1.2.0.apk","url":"https://api.github.com/repos/o/r/releases/assets/9","digest":"sha256:$sha"}]""") =
        """{"tag_name":"v1.2.0","body":"Fixes","published_at":"2026-11-24T00:00:00Z","draft":false,"prerelease":false$extra,"assets":$assets}"""

    @Test fun parsesReleaseWithDigest() {
        val r = UpdateLogic.parseRelease(release())!!
        assertEquals("1.2.0", r.version)
        assertEquals(12345L, r.apkSize)
        assertEquals(sha, r.sha256)
        assertNotNull(r.apiUrl)
        assertEquals("Fixes", r.notes)
    }

    @Test fun rejectsReleaseWithoutApkOrUntrustedHost() {
        assertNull(UpdateLogic.parseRelease(release(assets = "[]")))
        assertNull(UpdateLogic.parseRelease(release(assets = """[{"name":"a.apk","size":1,"browser_download_url":"https://evil.example/a.apk"}]""")))
        assertNull(UpdateLogic.parseRelease("{ not json"))
    }

    @Test fun skipsDraftsAndPrereleases() {
        assertNull(UpdateLogic.parseRelease(release().replace("\"prerelease\":false", "\"prerelease\":true")))
    }

    @Test fun prefersReleaseApkOverDebug() {
        val assets = """[{"name":"app-debug.apk","size":1,"browser_download_url":"https://github.com/o/r/d.apk"},{"name":"app-release.apk","size":2,"browser_download_url":"https://github.com/o/r/r.apk"}]"""
        assertEquals("app-release.apk", UpdateLogic.parseRelease(release(assets = assets))!!.apkName)
    }

    @Test fun sha256OfKnownBytes() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UpdateLogic.sha256(ByteArrayInputStream("abc".toByteArray())))
    }

    @Test fun badDigestIsIgnored() {
        assertNull(UpdateLogic.parseRelease(release().replace(sha, "zz"))!!.sha256)
    }
}
