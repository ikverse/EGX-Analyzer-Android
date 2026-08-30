package com.ikverse.egxanalyzer.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ikverse.egxanalyzer.data.backupFolderLabel
import com.ikverse.egxanalyzer.data.backupsInFolder
import com.ikverse.egxanalyzer.data.holdsBackupFolder
import com.ikverse.egxanalyzer.data.readBackup
import com.ikverse.egxanalyzer.data.writeBackupTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val folder = appState.backupFolder?.let(Uri::parse)
    // Re-read on every composition rather than remembered: the grant can be revoked from a system
    // page, and a card that went on naming a folder this app can no longer write to would report a
    // phone as backed up on the one day that mattered.
    val holdsFolder = folder != null && holdsBackupFolder(context, folder)
    val held = if (holdsFolder && folder != null) backupsInFolder(context, folder) else emptyList()

    val chooseFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { picked ->
        if (picked == null) return@rememberLauncherForActivityResult
        // Persisted, or the grant dies with this process and the daily backup silently stops on the
        // next launch - the failure this whole feature exists to prevent, reproduced inside it.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                picked,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        appState.saveBackupFolder(picked.toString())
        appState.statusMessage = StatusMessage(
            "Backing up to ${backupFolderLabel(picked)}",
            succeeded = true,
        )
    }

    // Every type, deliberately. A zip that has been round a cloud folder, a chat and a downloads
    // directory arrives labelled anything at all - `application/octet-stream` is common - and a
    // filter would hide the user's own backup from the picker they opened to find it. What the file
    // actually is gets decided by reading its first bytes, in `readBackup`.
    val pickBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { picked -> pendingRestore = picked }

    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        runCatching {
                            withContext(Dispatchers.IO) {
                                writeBackupTo(
                                    context = context,
                                    folder = folder?.takeIf { holdsFolder },
                                    database = appState.databaseFile(),
                                    settings = appState.settingsDocument(),
                                    device = appState.backupDevice(),
                                    checkpoint = appState::checkpointDatabase,
                                )
                            }
                        }
                            .onSuccess {
                                appState.recordBackupDay()
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
            ) {
                Text(if (busy) "Working…" else "Back up now")
            }
            OutlinedButton(enabled = !busy, onClick = { chooseFolder.launch(null) }) {
                Text(if (holdsFolder) "Change folder" else "Choose a folder")
            }
            OutlinedButton(enabled = !busy, onClick = { pickBackup.launch(arrayOf("*/*")) }) {
                Text("Restore from a backup")
            }
            // In the row with the buttons it describes. It was a second grey paragraph under the
            // status line, so the card said what a backup is every time somebody came to read
            // whether one had been written.
            InfoButton(
                infoNote(
                    "What a backup holds",
                    "Every report, trade, rule and setting.",
                    "Your provider API key is not in it and never leaves this phone.",
                ),
            )
        }

        Text(
            when {
                holdsFolder && held.isNotEmpty() ->
                    "Backing up daily to ${backupFolderLabel(folder!!)}. ${held.size} kept, " +
                        "newest ${held.first()}."
                holdsFolder ->
                    "Backing up daily to ${backupFolderLabel(folder!!)}. Nothing written yet - " +
                        "the first copy goes there today."
                // Not an error: the app works perfectly well like this, and a red warning about a
                // folder nobody has chosen yet would be scolding someone for the default.
                appState.backupFolder != null ->
                    "The backup folder is no longer reachable, so backups go to Downloads. Choose " +
                        "it again to start writing there."
                else ->
                    "Backups go to Downloads. Choose a folder your cloud app syncs - OneDrive, " +
                        "Dropbox, Nextcloud, an SD card - and a copy is written there once a day."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    pendingRestore?.let { source ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore from this backup?") },
            // Says what it will not do, because that is the question somebody hesitating over this
            // button is actually asking. A restore that could delete is one nobody dares press.
            text = {
                Text(
                    "Anything in the backup that this phone is missing is added back. Nothing " +
                        "already here is deleted or overwritten with an older version.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        busy = true
                        appState.runAction(
                            label = "Restoring from a backup",
                            success = { outcome -> outcome.summary },
                        ) {
                            val record = withContext(Dispatchers.IO) { readBackup(context, source) }
                            appState.restoreFrom(record)
                        }
                        busy = false
                    }
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Cancel") }
            },
        )
    }
}

