package com.ikverse.egxanalyzer.state

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ikverse.egxanalyzer.model.DownloadedApk
import com.ikverse.egxanalyzer.data.SettingsRepository
import com.ikverse.egxanalyzer.data.UpdateRepository
import com.ikverse.egxanalyzer.model.AvailableUpdate
import com.ikverse.egxanalyzer.ui.AppUpdates
import com.ikverse.egxanalyzer.ui.StatusMessage
import com.ikverse.egxanalyzer.model.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Finding, fetching and installing a newer build.
 *
 * **The first piece lifted out of [LiveAppState], on 2026-09-12, and it was chosen because it is
 * the one that owes the rest of that class nothing.** Everything here is a function of the update
 * repository, one preference and the status line: it reads no price, no report and no trade, and
 * nothing recomputes when it moves. The regions around it are not like that - Ask AI reads
 * `performance`, `portfolio` and `runAction`, which are the heart of the class, and handing a
 * collaborator live references back into its host is the same object graph with an extra hop in
 * it. So this went and those stayed, deliberately. See the note in `CLAUDE.md`.
 *
 * `LiveAppState` gets every member of [AppUpdates] by delegating to this, so there is no forwarding
 * code between the two and no chance of a member that forwards to the wrong place.
 *
 * **Its own scope, not the app's.** The app's scope is never cancelled, so a second one alongside
 * it costs nothing, and it is one fewer thing that has to be handed in at construction - a
 * collaborator that needed the host's scope would have to be built first and told about its host
 * afterwards, which is the two-step build this avoids.
 *
 * **The preference is read from the repository rather than cached.** `LiveAppState` holds
 * `appPreferences` in Compose state and writes it through `persistPreferences`; a second copy here
 * would be a second answer free to disagree with it. One disk read per launch is not worth a copy,
 * so `updateAutomaticUpdateChecks` stays on the host with the other preference writers and this
 * asks the repository each time it needs to know.
 */
class LiveUpdates(
    private val repository: UpdateRepository?,
    private val status: StatusChannel,
    private val settingsRepository: SettingsRepository,
) : AppUpdates {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** How far the app has got with finding, fetching and checking a newer build. */
    override var updateState by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /**
     * Asks GitHub whether a newer build exists, and answers either way.
     *
     * The button is a question, so "you are on the newest version" is an answer worth giving. The
     * launch check is not, which is why it is [checkQuietly] and not this.
     */
    override fun checkForUpdate() {
        val updates = repository ?: return
        if (updateState is UpdateState.Checking || updateState is UpdateState.Downloading) return
        updateState = UpdateState.Checking
        scope.launch {
            updateState = runCatching { updates.check() }.fold(
                onSuccess = { update ->
                    if (update == null) {
                        UpdateState.UpToDate(updates.currentVersionName)
                    } else {
                        UpdateState.Available(update)
                    }
                },
                onFailure = { error ->
                    UpdateState.Failed(
                        error.message?.takeIf(String::isNotBlank) ?: "The update check failed.",
                    )
                },
            )
        }
    }

    /**
     * The launch check, which speaks only when there is something new.
     *
     * The same rule the launch sync follows: telling someone who never asked that nothing has
     * changed is a notification about nothing. A failure is silent for the same reason - the phone
     * was offline, which is not news either.
     */
    fun checkQuietly() {
        val updates = repository ?: return
        scope.launch {
            // The disk before the network, and whatever the setting says: an update already fetched
            // was asked for by someone, and the only thing left to do with it is install it. This is
            // what carries a download through the app being restarted by the permission grant that
            // was needed to install it.
            val waiting = runCatching { updates.downloaded() }.getOrNull()
            if (waiting != null) {
                updateState = UpdateState.Ready(waiting.first, waiting.second)
                status.message = StatusMessage(
                    "Version ${waiting.first.versionName} is downloaded and ready to install",
                    succeeded = true,
                )
            }
            if (!settingsRepository.loadPreferences().updateChecksEnabled) return@launch
            val update = runCatching { updates.check() }.getOrNull() ?: return@launch
            // A download in hand beats an offer of the same version, and loses to a newer one.
            val ready = (updateState as? UpdateState.Ready)?.update?.version
            if (ready != null && ready >= update.version) return@launch
            updateState = UpdateState.Available(update)
            status.message = StatusMessage(
                "Version ${update.versionName} is available",
                succeeded = true,
            )
        }
    }

    /**
     * Fetches the APK and checks it before offering to install it.
     *
     * The signing check is what turns Android's "App not installed" into a sentence that says what
     * to do about it. It is not a second opinion on Android's own check - it is the same check,
     * made early enough to be explained.
     */
    override fun downloadUpdate(update: AvailableUpdate) {
        val updates = repository ?: return
        if (updateState is UpdateState.Downloading) return
        updateState = UpdateState.Downloading(update, 0f)
        scope.launch {
            updateState = runCatching {
                // Nothing to fetch if it is already here. A download interrupted by the permission
                // grant used to be paid for twice, at seventy megabytes a time.
                val waiting = runCatching { updates.downloaded() }.getOrNull()
                if (waiting != null && waiting.first.version >= update.version) {
                    return@runCatching UpdateState.Ready(waiting.first, waiting.second)
                }
                // The progress callback arrives on the thread doing the reading. Compose state
                // takes a write from any thread, and marshalling each percent back to the main one
                // would cost a coroutine per percent to move a number nobody is racing for.
                val file = updates.download(update) { progress ->
                    updateState = UpdateState.Downloading(update, progress)
                }
                when (updates.inspect(file)) {
                    DownloadedApk.MATCHES -> UpdateState.Ready(update, file)
                    // Damaged and wrong-key used to be the same sentence, and it was this one -
                    // so an interrupted download accused the release of being signed by someone
                    // else, which was true of nothing and sent the search a long way from the
                    // network fault that caused it.
                    DownloadedApk.WRONG_KEY -> {
                        file.delete()
                        UpdateState.Failed(
                            "Version ${update.versionName} is signed with a different key, so " +
                                "Android will not install it over this build. Uninstall this one " +
                                "and install that release by hand.",
                        )
                    }
                    DownloadedApk.DAMAGED -> {
                        file.delete()
                        UpdateState.Failed(
                            "The download of version ${update.versionName} arrived damaged. " +
                                "Press Download to fetch it again.",
                        )
                    }
                }
            }.getOrElse { error ->
                UpdateState.Failed(
                    error.message?.takeIf(String::isNotBlank) ?: "The download failed.",
                )
            }
        }
    }

    /** Puts the card back to the button, after an answer has been read. */
    override fun dismissUpdate() {
        updateState = UpdateState.Idle
    }

    /**
     * Hands the downloaded APK to Android to install.
     *
     * The confirmation is Android's own and arrives a moment later, through
     * [com.ikverse.egxanalyzer.data.UpdateInstallReceiver]. A failure to even start says so here,
     * because a button that appears to do nothing is what this whole path cost three releases.
     */
    override fun installUpdate(file: File) {
        val updates = repository ?: return
        scope.launch {
            runCatching { updates.install(file) }.onFailure { error ->
                reportUpdateProblem(
                    error.message?.takeIf(String::isNotBlank)
                        ?: "The install could not be started.",
                )
            }
        }
    }

    /**
     * Says why a button could not do what it says, without throwing away what the card holds.
     *
     * Android refusing to open the installer used to be invisible: the system closed it without a
     * word and the phone looked like it had ignored the press. A downloaded update is still a
     * downloaded update afterwards, so this speaks rather than resetting anything.
     */
    override fun reportUpdateProblem(reason: String) {
        status.message = StatusMessage(reason, succeeded = false)
    }

    /** True once the user has allowed this app to install apps; Android is the only one who can ask. */
    override fun canInstallUpdates(): Boolean = repository?.canInstall() ?: false

    override fun installPermissionIntent(): Intent? = repository?.permissionIntent()

    override fun releasesPageIntent(): Intent? = repository?.releasesPageIntent()
}
