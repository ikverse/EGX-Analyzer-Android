package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ikverse.egxanalyzer.model.DriveBackup
import com.ikverse.egxanalyzer.model.FoldPosture
import com.ikverse.egxanalyzer.model.ThemeMode
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.preview.FakeAppState
import com.ikverse.egxanalyzer.ui.theme.EgxAnalyzerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Google Drive row of the Backup card. One `setContent` per test. */
@RunWith(RobolectricTestRunner::class)
class DriveBackupControlsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(state: AppState) {
        compose.setContent {
            EgxAnalyzerTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalFoldPosture provides FoldPosture.Cover) {
                    Column { DriveBackupControls(state) }
                }
            }
        }
    }

    @Test
    fun `connect asks for access and does nothing more`() {
        var asked = 0
        var backedUp = 0
        show(FakeAppState(onDriveAccess = { asked++ }, onDriveBackup = { backedUp++ }))
        compose.onNodeWithText("Connect Google Drive").performClick()
        compose.waitForIdle()
        assertEquals(1, asked)
        assertEquals(0, backedUp)
    }

    @Test
    fun `a build that cannot reach Drive cannot connect and says why`() {
        show(FakeAppState(driveAvailable = false))
        compose.onNodeWithText("Connect Google Drive").assertIsNotEnabled()
        compose.onNodeWithText("Google Drive cannot be reached from this build", substring = true).assertExists()
    }

    @Test
    fun `once connected it names the account and the newest copy`() {
        show(
            FakeAppState(
                driveAccount = "owner@example.com",
                driveBackupList = listOf(DriveBackup("b", "egx-backup-2026-11-14.zip"), DriveBackup("a", "egx-backup-2026-11-13.zip")),
            ),
        )
        compose.onNodeWithText("Back up to Drive").assertExists()
        compose.onNodeWithText("Restore from Drive…").assertExists()
        compose.onNodeWithText("Disconnect").assertExists()
        compose.waitForIdle()
        compose.onNodeWithText(
            "Backing up daily to Google Drive as owner@example.com. 2 kept, newest egx-backup-2026-11-14.zip.",
        ).assertExists()
    }

    @Test
    fun `the line is never blank`() {
        assertEquals(
            "Not connected. Connect a Google account and a copy goes to its Drive once a day.",
            driveLine(available = true, account = null, held = null),
        )
        assertEquals("Backing up daily to Google Drive as a@b.c.", driveLine(true, "a@b.c", null))
        assertEquals(
            "Backing up daily to Google Drive as a@b.c. Nothing written yet - the first copy goes there today.",
            driveLine(true, "a@b.c", emptyList()),
        )
    }
}
