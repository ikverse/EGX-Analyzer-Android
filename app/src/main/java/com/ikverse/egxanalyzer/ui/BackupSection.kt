package com.ikverse.egxanalyzer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ikverse.egxanalyzer.ui.theme.palette
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.KitDialog
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.KitRule
import com.ikverse.egxanalyzer.ui.kit.KitSheet
import com.ikverse.egxanalyzer.model.DriveBackup
import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Backing the record up, and getting it back.
 *
 * Everything already travels through a private Telegram channel, and for anyone using this app that
 * is the one cloud account there is certain to be - signing into Telegram is what makes it work at
 * all. What the channel cannot survive is the Telegram account itself going, and what it never
 * offered is a copy the user holds. These three buttons are that copy and the way back from it.
 *
 * **No account, no key, nothing to configure**, which is the whole design. Picking a folder goes
 * through Android's own document picker, so OneDrive, Dropbox, Nextcloud, an SD card and a plain
 * local folder all appear in the same list: a user ends up backed up to whatever cloud they already
 * have while this app learns none of them, holds no credential for any of them, and costs nothing
 * per person to run.
 */
@Composable
fun BackupControls(appState: AppState) {
    var busy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    // Bumped after a manual backup actually writes, which is the one change below that does not
    // already come with a new folder to key on.
    var written by remember { mutableStateOf(0) }

    // Re-read on every composition rather than remembered: the grant can be revoked from a system
    // page, and a card that went on naming a folder this app can no longer write to would report a
    // phone as backed up on the one day that mattered. Cheap either way - a lookup in a list Android
    // already holds in memory, not a query to the folder itself.
    val holdsFolder = appState.holdsBackupFolder()
    // The folder's own file list is a real query to whatever app actually holds it - local storage,
    // or a cloud provider over its own connection - and reading it on every recomposition of a
    // settings screen blocked the frame it landed on to ask a folder the reader had not touched.
    // Read once, off the main thread, when the card first appears or the folder or its contents
    // actually change.
    val held by produceState(initialValue = emptyList<String>(), appState.backupFolder, written) {
        value = withContext(Dispatchers.IO) { appState.backupsInFolder() }
    }

    val chooseFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { picked ->
        if (picked == null) return@rememberLauncherForActivityResult
        appState.keepBackupFolder(picked.toString())
    }

    // Every type, deliberately. A zip that has been round a cloud folder, a chat and a downloads
    // directory arrives labelled anything at all - `application/octet-stream` is common - and a
    // filter would hide the user's own backup from the picker they opened to find it. What the file
    // actually is gets decided by reading its first bytes, in `readBackup`.
    val pickBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { picked -> pendingRestore = picked }

    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        // In the row with the buttons it describes. It was a second grey paragraph under the
        // status line, so the card said what a backup is every time somebody came to read whether
        // one had been written.
        SettingRow(
            about = infoNote(
                "What a backup holds",
                "Every report, trade, rule and setting.",
                "Your provider API key is not in it and never leaves this phone.",
            ),
        ) {
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                KitButton(
                    if (busy) "Working…" else "Back up now",
                    size = ButtonSize.SMALL,
                    enabled = !busy,
                    onClick = {
                        appState.startAction {
                            busy = true
                            runCatching { appState.writeBackup() }
                                .onSuccess {
                                    appState.recordBackupDay()
                                    written++
                                    appState.statusMessage = StatusMessage("Saved to $it", succeeded = true)
                                }
                                .onFailure {
                                    appState.statusMessage = StatusMessage(
                                        it.message?.takeIf(String::isNotBlank) ?: "Could not write a backup",
                                        succeeded = false,
                                    )
                                }
                            busy = false
                        }
                    },
                )
                KitButton(
                    if (holdsFolder) "Change folder" else "Choose a folder",
                    onClick = { chooseFolder.launch(null) },
                    kind = ButtonKind.SECONDARY,
                    size = ButtonSize.SMALL,
                    enabled = !busy,
                )
                KitButton(
                    "Restore…",
                    onClick = { pickBackup.launch(arrayOf("*/*")) },
                    kind = ButtonKind.SECONDARY,
                    size = ButtonSize.SMALL,
                    enabled = !busy,
                )
            }
        }

        Text(
            when {
                holdsFolder && held.isNotEmpty() ->
                    "Backing up daily to ${appState.backupFolderLabel(appState.backupFolder!!)}. " +
                        "${held.size} kept, " +
                        "newest ${held.first()}."
                holdsFolder ->
                    "Backing up daily to ${appState.backupFolderLabel(appState.backupFolder!!)}. " +
                        "Nothing written yet - " +
                        "the first copy goes there today."
                // Not an error: the app works perfectly well like this, and a red warning about a
                // folder nobody has chosen yet would be scolding someone for the default. Neither
                // branch below claims a daily copy goes to Downloads on its own - only the button
                // above falls back there, and only when pressed.
                appState.backupFolder != null ->
                    "Automatic backups have stopped: the folder can no longer be reached. Choose " +
                        "it again to start writing there."
                else ->
                    "No automatic backup yet. Choose a folder your cloud app syncs - OneDrive, " +
                        "Dropbox, Nextcloud, an SD card - and a copy is written there once a day. " +
                        "Back up now saves to Downloads instead."
            },
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
        )

        KitRule()
        DriveBackupControls(appState)
    }

    pendingRestore?.let { source ->
        // Says what it will not do, because that is the question somebody hesitating over this
        // button is actually asking. A restore that could delete is one nobody dares press.
        KitDialog(
            title = "Restore from this backup?",
            text = "Anything in the backup that this phone is missing is added back. Nothing " +
                "already here is deleted or overwritten with an older version.",
            confirmLabel = "Restore",
            dismissLabel = "Cancel",
            onDismiss = { pendingRestore = null },
            posture = LocalFoldPosture.current,
            tone = TileTone.TILE3,
            onConfirm = {
                    pendingRestore = null
                    appState.startAction {
                        busy = true
                        appState.runAction(
                            label = "Restoring from a backup",
                            success = { outcome -> outcome.summary },
                        ) {
                            appState.restoreFromBackup(source.toString())
                        }
                        busy = false
                    }
            },
        )
    }
}


/**
 * The Google Drive half of the Backup card: connect, back up, restore, disconnect.
 *
 * Beside the folder rather than instead of it, because Drive does not appear in Android's folder
 * picker and the tablet has no Play services to sign in with. Signing in shows Google's own consent
 * screen, asking for `drive.file` alone - the app sees the files it made and nothing else in Drive.
 *
 * Connecting runs on the composition's own scope rather than `startAction`, because what it may end
 * in is launching the consent screen from this composition's launcher.
 */
@Composable
internal fun DriveBackupControls(appState: AppState) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var written by remember { mutableStateOf(0) }
    var listOpen by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<DriveBackup?>(null) }
    val account = appState.driveAccount

    fun fail(error: Throwable, fallback: String) {
        appState.statusMessage = StatusMessage(
            error.message?.takeIf(String::isNotBlank) ?: fallback,
            succeeded = false,
        )
    }

    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            busy = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                appState.finishDriveAccess(result.data)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                fail(error, "Google Drive was not connected")
            }
            busy = false
        }
    }

    // A request to Drive, made when the card appears or a backup lands. Null while it is out or if
    // it failed, and the line below then names the account without a count.
    val held by produceState<List<DriveBackup>?>(initialValue = null, account, written) {
        value = if (account == null) null else runCatching { appState.driveBackups() }.getOrNull()
    }

    SettingRow(
        about = infoNote(
            "Google Drive",
            "Backups go to a folder called EGX Analyzer in your Google Drive, once a day and " +
                "whenever you press Back up to Drive. Seven are kept.",
            "The app can see only the files it put there, nothing else in your Drive. Your " +
                "provider API key is not in a backup.",
        ),
    ) {
        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            val waiting = appState.driveSignInCode
            if (account == null && waiting != null) {
                // A device with no Play services: Google is waiting for this code to be typed.
                KitButton(
                    "Open ${waiting.verificationUrl.removePrefix("https://").removePrefix("www.")}",
                    size = ButtonSize.SMALL,
                    onClick = appState::openDriveSignInPage,
                )
                KitButton(
                    "Cancel",
                    onClick = appState::cancelDriveSignIn,
                    kind = ButtonKind.SECONDARY,
                    size = ButtonSize.SMALL,
                )
            } else if (account == null) {
                KitButton(
                    if (busy) "Connecting…" else "Connect Google Drive",
                    size = ButtonSize.SMALL,
                    enabled = !busy && appState.driveAvailable,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val sender = appState.requestDriveAccess()
                                if (sender != null) {
                                    consent.launch(IntentSenderRequest.Builder(sender).build())
                                    return@launch
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                fail(error, "Google Drive was not connected")
                            }
                            busy = false
                        }
                    },
                )
            } else {
                KitButton(
                    if (busy) "Working…" else "Back up to Drive",
                    size = ButtonSize.SMALL,
                    enabled = !busy,
                    onClick = {
                        appState.startAction {
                            busy = true
                            runCatching { appState.backUpToDrive() }
                                .onSuccess {
                                    written++
                                    appState.statusMessage = StatusMessage("Saved to $it", succeeded = true)
                                }
                                .onFailure { fail(it, "Could not back up to Google Drive") }
                            busy = false
                        }
                    },
                )
                KitButton(
                    "Restore from Drive…",
                    onClick = { listOpen = true },
                    kind = ButtonKind.SECONDARY,
                    size = ButtonSize.SMALL,
                    enabled = !busy,
                )
                KitButton(
                    "Disconnect",
                    onClick = { appState.disconnectDrive() },
                    kind = ButtonKind.SECONDARY,
                    size = ButtonSize.SMALL,
                    enabled = !busy,
                )
            }
        }
    }

    appState.driveSignInCode?.takeIf { account == null }?.let { waiting ->
        Text(
            "Type this code at ${waiting.verificationUrl.removePrefix("https://")}, on this device " +
                "or any other, and press Allow. This connects by itself once you do.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(waiting.userCode, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = TabularFigures))
    } ?: Text(
        driveLine(appState.driveAvailable, account, held),
        style = MaterialTheme.typography.bodySmall,
        color = palette.muted,
    )

    if (listOpen) {
        KitSheet(
            onDismiss = { listOpen = false },
            posture = LocalFoldPosture.current,
            title = "Restore from Google Drive",
        ) {
            val backups = held
            when {
                backups == null -> Text("Reading the folder…", color = palette.muted)
                backups.isEmpty() -> Text("No backups in Google Drive yet.", color = palette.muted)
                else -> backups.forEach { backup ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            backup.name + (backup.bytes?.let { " · ${megabytes(it)}" } ?: ""),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        KitButton(
                            "Restore",
                            onClick = {
                                listOpen = false
                                pending = backup
                            },
                            kind = ButtonKind.SECONDARY,
                            size = ButtonSize.SMALL,
                        )
                    }
                }
            }
        }
    }

    pending?.let { backup ->
        KitDialog(
            title = "Restore ${backup.name}?",
            text = "Anything in the backup that this phone is missing is added back. Nothing " +
                "already here is deleted or overwritten with an older version.",
            confirmLabel = "Restore",
            dismissLabel = "Cancel",
            onDismiss = { pending = null },
            posture = LocalFoldPosture.current,
            tone = TileTone.TILE3,
            onConfirm = {
                pending = null
                appState.startAction {
                    busy = true
                    appState.runAction(
                        label = "Restoring from Google Drive",
                        success = { outcome -> outcome.summary },
                    ) {
                        appState.restoreFromDrive(backup.id)
                    }
                    busy = false
                }
            },
        )
    }
}

/** The line under the Drive buttons. Never blank: an empty line reads like one that stopped. */
internal fun driveLine(available: Boolean, account: String?, held: List<DriveBackup>?): String = when {
    !available -> "Google Drive cannot be reached from this build."
    account == null ->
        "Not connected. Connect a Google account and a copy goes to its Drive once a day."
    held == null -> "Backing up daily to Google Drive as $account."
    held.isEmpty() -> "Backing up daily to Google Drive as $account. Nothing written yet - " +
        "the first copy goes there today."
    else -> "Backing up daily to Google Drive as $account. ${held.size} kept, newest ${held.first().name}."
}

private fun megabytes(bytes: Long): String = "%.1f MB".format(bytes / 1_048_576.0)
