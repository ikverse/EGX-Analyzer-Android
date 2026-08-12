package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.AppVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the app is willing to call an update.
 *
 * The answer decides whether a phone offers to replace itself, so everything that is not plainly a
 * newer, installable build has to come back as nothing at all.
 */
class UpdateReleaseTest {

    /** The three files a release carries since it was split by architecture, as GitHub lists them. */
    private val split = listOf(
        "egx-analyzer-1.0.1-arm64-v8a.apk",
        "egx-analyzer-1.0.1-universal.apk",
        "egx-analyzer-1.0.1-x86_64.apk",
    )

    private fun release(
        tag: String = "v1.0.1",
        assets: List<String> = listOf("egx-analyzer-1.0.1.apk"),
        size: Long = 24_500_000,
        notes: String = "Prices heal their own gaps.",
        draft: Boolean = false,
        prerelease: Boolean = false,
    ): String {
        val listed = assets.joinToString(",") { name ->
            """{
                "name": "$name",
                "size": $size,
                "browser_download_url": "https://example.invalid/$name"
            }"""
        }
        return """{
            "tag_name": "$tag",
            "draft": $draft,
            "prerelease": $prerelease,
            "body": "$notes",
            "assets": [$listed]
        }"""
    }

    @Test
    fun `a release is read into what the screen needs to show`() {
        val update = requireNotNull(readRelease(release()))

        assertEquals(AppVersion(1, 0, 1), update.version)
        assertEquals("1.0.1", update.versionName)
        assertEquals("Prices heal their own gaps.", update.notes)
        assertEquals("https://example.invalid/egx-analyzer-1.0.1.apk", update.downloadUrl)
        assertEquals("23.4 MB", update.sizeLabel)
    }

    /**
     * A tag pushed while the build is still running produces exactly this.
     *
     * Offering it would send someone to a Download button with nothing behind it, and the release
     * that follows minutes later would look like the same failure happening twice.
     */
    @Test
    fun `a release with no APK attached is not an update`() {
        assertNull(readRelease(release(assets = emptyList())))
        assertNull(readRelease(release(assets = listOf("release-notes.txt"))))
    }

    @Test
    fun `a draft or a pre-release is not offered`() {
        assertNull(readRelease(release(draft = true)))
        assertNull(readRelease(release(prerelease = true)))
    }

    @Test
    fun `anything that is not a release reads as no release`() {
        assertNull(readRelease("not json at all"))
        assertNull(readRelease("{}"))
        assertNull(readRelease(release(tag = "nightly")))
    }

    @Test
    fun `only a strictly newer build is offered`() {
        assertEquals("1.0.1", updateOffered("1.0.0", release())?.versionName)
        // The version already installed, and one older than it.
        assertNull(updateOffered("1.0.1", release()))
        assertNull(updateOffered("1.1.0", release()))
        // The debug build of the same version is the same version.
        assertNull(updateOffered("1.0.1-debug", release()))
    }

    /** A build that cannot say what it is offers nothing, rather than offering everything. */
    @Test
    fun `a version this build cannot read offers nothing`() {
        assertNull(updateOffered("", release()))
    }

    /**
     * The split is one version in several files, and this is the line that keeps it that way.
     *
     * Downloading the wrong one is not a smaller failure than downloading nothing: Android refuses
     * an APK without the device's own libraries, so the whole download is wasted at the last step.
     */
    @Test
    fun `a device downloads the build for its own chip`() {
        val phone = readRelease(release(assets = split), abis = listOf("arm64-v8a", "armeabi-v7a"))
        val emulator = readRelease(release(assets = split), abis = listOf("x86_64", "x86"))

        assertEquals("https://example.invalid/${split[0]}", phone?.downloadUrl)
        assertEquals("https://example.invalid/${split[2]}", emulator?.downloadUrl)
    }

    /** Best first, as the device itself reports them - not the order GitHub happens to list them in. */
    @Test
    fun `the device's own order decides, not the release page's`() {
        val chosen = readRelease(release(assets = split), abis = listOf("x86_64", "arm64-v8a"))

        assertEquals("https://example.invalid/${split[2]}", chosen?.downloadUrl)
    }

    @Test
    fun `a device the release does not name takes the universal build`() {
        val chosen = readRelease(release(assets = split), abis = listOf("riscv64"))

        assertEquals("https://example.invalid/${split[1]}", chosen?.downloadUrl)
    }

    /** Releases cut before the split carried one APK named for nothing but its version. */
    @Test
    fun `a release from before the split still updates a phone`() {
        val chosen = readRelease(release(), abis = listOf("arm64-v8a"))

        assertEquals("https://example.invalid/egx-analyzer-1.0.1.apk", chosen?.downloadUrl)
    }

    /**
     * An APK for another architecture is not a fallback.
     *
     * Offering it would promise an update that cannot happen - Android refuses to install one -
     * after a download the size of the whole app.
     */
    @Test
    fun `a release with nothing this device can run is not an update`() {
        val chosen = readRelease(
            release(assets = listOf("egx-analyzer-1.0.1-x86_64.apk")),
            abis = listOf("arm64-v8a"),
        )

        assertNull(chosen)
    }

    @Test
    fun `a size is reported in the unit someone decides with`() {
        assertEquals("23.4 MB", readRelease(release(size = 24_500_000))?.sizeLabel)
        assertEquals("512 KB", readRelease(release(size = 524_288))?.sizeLabel)
        assertEquals("size unknown", readRelease(release(size = 0))?.sizeLabel)
    }
}
