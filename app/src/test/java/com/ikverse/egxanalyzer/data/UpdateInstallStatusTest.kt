package com.ikverse.egxanalyzer.data

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app says when an install does not happen.
 *
 * It never used to say anything. The old path handed an APK to another process and heard nothing
 * back, so an installer that refused it looked exactly like a button that did nothing - which cost
 * three releases of guessing at a phone that appeared to ignore a press.
 */
class UpdateInstallStatusTest {

    @Test
    fun `a signing key that does not match says so, and says what it means`() {
        val message = installFailureMessage(
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            "INSTALL_FAILED_UPDATE_INCOMPATIBLE",
        )

        assertEquals(
            "That build is signed with a different key, so it cannot replace this install.",
            message,
        )
    }

    @Test
    fun `a full disk names the space, not the error code`() {
        assertEquals(
            "There is not enough free space to install the update.",
            installFailureMessage(PackageInstaller.STATUS_FAILURE_STORAGE, "INSTALL_FAILED_INSUFFICIENT_STORAGE"),
        )
    }

    /** Pressing cancel is not a fault, and must not read like one. */
    @Test
    fun `a cancelled install is reported plainly`() {
        assertEquals(
            "Install cancelled.",
            installFailureMessage(PackageInstaller.STATUS_FAILURE_ABORTED, null),
        )
    }

    /**
     * A status this build does not recognise still has to say something.
     *
     * The system's own text is carried then, because a diagnostic beats silence - which is what
     * every one of these used to be.
     */
    @Test
    fun `an unrecognised failure carries whatever the system said`() {
        val message = installFailureMessage(PackageInstaller.STATUS_FAILURE, "Something specific")

        assertTrue(message.contains("Something specific"))
    }

    @Test
    fun `an unrecognised failure with nothing to add still says it failed`() {
        assertEquals("The install failed.", installFailureMessage(PackageInstaller.STATUS_FAILURE, null))
        assertEquals("The install failed.", installFailureMessage(PackageInstaller.STATUS_FAILURE, "  "))
    }
}
