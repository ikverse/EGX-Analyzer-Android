package com.ikverse.egxanalyzer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The comparison the update check is built on.
 *
 * Everything else about updating is plumbing; this is the decision. Get it wrong in one direction
 * and the app offers an update forever, in the other and it never offers one at all.
 */
class AppVersionTest {

    @Test
    fun `a version is read from every spelling this project uses`() {
        // The build's own name, a git tag, and what a sideloaded debug build calls itself.
        assertEquals(AppVersion(1, 0, 1), AppVersion.parse("1.0.1"))
        assertEquals(AppVersion(1, 0, 1), AppVersion.parse("v1.0.1"))
        assertEquals(AppVersion(1, 0, 1), AppVersion.parse("1.0.1-debug"))
        assertEquals(AppVersion(1, 0, 1), AppVersion.parse(" v1.0.1 "))
    }

    @Test
    fun `a version that names no numbers is no version`() {
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("v"))
    }

    /** Two numbers is still a version; the missing one is zero, not a refusal. */
    @Test
    fun `a shortened version reads as the one it means`() {
        assertEquals(AppVersion(2, 1, 0), AppVersion.parse("2.1"))
        assertEquals(AppVersion(2, 0, 0), AppVersion.parse("2"))
    }

    /**
     * The whole reason this is not a string comparison.
     *
     * 1.0.10 is the release immediately after 1.0.9, and as text it sorts before it - so a phone on
     * 1.0.9 would be told it was up to date by the very release meant for it.
     */
    @Test
    fun `ten comes after nine`() {
        assertTrue(AppVersion.parse("1.0.10")!! > AppVersion.parse("1.0.9")!!)
        assertTrue(AppVersion.parse("1.10.0")!! > AppVersion.parse("1.9.0")!!)
        assertTrue(AppVersion.parse("2.0.0")!! > AppVersion.parse("1.99.99")!!)
    }

    @Test
    fun `the same version is not newer than itself`() {
        assertEquals(0, AppVersion.parse("1.0.0")!!.compareTo(AppVersion.parse("v1.0.0")!!))
        // The suffix says which build, never which build is newer.
        assertEquals(0, AppVersion.parse("1.0.0-debug")!!.compareTo(AppVersion.parse("1.0.0")!!))
    }
}
