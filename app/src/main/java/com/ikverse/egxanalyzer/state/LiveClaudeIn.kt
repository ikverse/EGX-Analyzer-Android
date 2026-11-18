package com.ikverse.egxanalyzer.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ikverse.egxanalyzer.data.ClaudeInException
import com.ikverse.egxanalyzer.data.ClaudeInQrScanner
import com.ikverse.egxanalyzer.data.HttpClaudeIn
import com.ikverse.egxanalyzer.data.SettingsRepository
import com.ikverse.egxanalyzer.model.ClaudeInOutcome
import com.ikverse.egxanalyzer.model.ClaudeInPairing
import com.ikverse.egxanalyzer.model.ClaudeInPairingInput
import com.ikverse.egxanalyzer.model.ClaudeInPlan
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.ui.ClaudeInConfirm
import com.ikverse.egxanalyzer.ui.ClaudeInLink
import com.ikverse.egxanalyzer.ui.ClaudeInLinkState
import com.ikverse.egxanalyzer.ui.ClaudeInPaired
import com.ikverse.egxanalyzer.ui.StatusMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * What ClaudeIn says during a run, written by the analysis repository (which is built before
 * [LiveAppState]) and read by the screens. Its own object so both can hold it.
 */
class ClaudeInSignals {
    var working by mutableStateOf<String?>(null)
    var plan by mutableStateOf<ClaudeInPlan?>(null)
}

/**
 * Pairing with ClaudeIn and keeping the card's state, outside [LiveAppState] by the pattern
 * [LiveUpdates] set: `ClaudeInLink by claudeIn` there gives that class these members.
 *
 * The pairing is stored as the [CloudProvider.CLAUDEIN] credential - encrypted, device-local,
 * never synced or backed up - so the repository reads it exactly where it reads an API key.
 */
class LiveClaudeIn(
    private val settingsRepository: SettingsRepository,
    private val status: StatusChannel,
    private val signals: ClaudeInSignals = ClaudeInSignals(),
    private val client: HttpClaudeIn = HttpClaudeIn(),
) : ClaudeInLink {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    /** Told when a pairing is saved or forgotten, so the host reloads its configuration. */
    private var onChanged: () -> Unit = {}

    /** The pairing a typed route found, waiting for the owner's confirmation. Holds the code. */
    private var pending: ClaudeInPairing? = null

    override var claudeInState by mutableStateOf(ClaudeInLinkState.NOT_PAIRED)
        private set
    override var claudeInPaired by mutableStateOf<ClaudeInPaired?>(null)
        private set
    override var claudeInMessage by mutableStateOf<String?>(null)
        private set
    override var claudeInConfirm by mutableStateOf<ClaudeInConfirm?>(null)
        private set
    override val claudeInPlan: ClaudeInPlan? get() = signals.plan
    override val claudeInWorking: String? get() = signals.working

    fun bind(onChanged: () -> Unit) {
        this.onChanged = onChanged
        refreshPaired()
    }

    private fun stored(): ClaudeInPairing? {
        val chars = settingsRepository.readCredential(CloudProvider.CLAUDEIN) ?: return null
        return try {
            ClaudeInPairing.fromJson(String(chars))
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun refreshPaired() {
        val pairing = stored()
        claudeInPaired = pairing?.let {
            ClaudeInPaired(label = it.label, address = it.addresses.first(), shortFingerprint = it.shortFingerprint)
        }
        if (pairing == null) claudeInState = ClaudeInLinkState.NOT_PAIRED
        else if (claudeInState == ClaudeInLinkState.NOT_PAIRED) claudeInState = ClaudeInLinkState.PAIRED
    }

    override fun pairClaudeInFromQr(text: String) {
        val pairing = try {
            ClaudeInPairingInput.fromQr(text)
        } catch (bad: IllegalArgumentException) {
            claudeInMessage = bad.message
            return
        }
        launch("Checking the PC…") {
            // The QR named the certificate in full, so there is nothing for the owner to compare:
            // a PC that shows any other certificate is refused before the code is sent.
            val reached = pairing.bases.firstNotNullOfOrNull { base ->
                runCatching { client.helloPinned(base, pairing.fingerprint) }.getOrNull()
            } ?: throw ClaudeInException("${ClaudeInOutcome.PC_NOT_AVAILABLE} Check that ClaudeIn is open on the PC and this phone is on Tailscale or the same Wi-Fi.")
            if (!reached.isClaudeIn) throw ClaudeInException("Something other than ClaudeIn answered.")
            client.check(pairing)
            save(pairing)
        }
    }

    override fun pairClaudeInTyped(address: String, code: String) {
        val (host, port) = ClaudeInPairingInput.parseAddress(address) ?: run {
            claudeInMessage = "That isn't an address. Type it as ClaudeIn shows it, such as 100.101.102.103:8790."
            return
        }
        if (!ClaudeInPairingInput.isCode(code)) {
            claudeInMessage = "The code is the 10 letters and numbers ClaudeIn shows, such as ABCDE-FGH23."
            return
        }
        launch("Reaching the PC…") {
            val hello = client.hello(host, port)
            if (!hello.isClaudeIn) throw ClaudeInException("Something other than ClaudeIn answered at $host.")
            val pairing = ClaudeInPairing(
                addresses = listOf(host),
                port = port,
                code = ClaudeInPairingInput.normaliseCode(code),
                fingerprint = hello.fingerprint,
            )
            // The same certificate as the first contact, and the code must be right.
            client.check(pairing)
            pending = pairing
            claudeInConfirm = ClaudeInConfirm(address = "$host:$port", shortFingerprint = pairing.shortFingerprint)
            claudeInState = if (stored() == null) ClaudeInLinkState.NOT_PAIRED else ClaudeInLinkState.PAIRED
            claudeInMessage = null
        }
    }

    override fun confirmClaudeIn() {
        val pairing = pending ?: return
        pending = null
        claudeInConfirm = null
        save(pairing)
    }

    override fun cancelClaudeInConfirm() {
        pending = null
        claudeInConfirm = null
        claudeInMessage = "Not paired. The fingerprint has to match what ClaudeIn shows."
    }

    override fun forgetClaudeIn() {
        job?.cancel()
        pending = null
        claudeInConfirm = null
        settingsRepository.removeCredential(CloudProvider.CLAUDEIN)
        signals.plan = null
        claudeInPaired = null
        claudeInState = ClaudeInLinkState.NOT_PAIRED
        claudeInMessage = "Forgotten. Pair again to use ClaudeIn."
        onChanged()
    }

    override fun checkClaudeIn() {
        val pairing = stored() ?: run {
            claudeInState = ClaudeInLinkState.NOT_PAIRED
            return
        }
        launch("Checking the PC…") {
            val check = client.check(pairing)
            ClaudeInPlan.from(check.optJSONObject("limits"), System.currentTimeMillis())?.let { signals.plan = it }
            claudeInState = ClaudeInLinkState.PAIRED
            claudeInMessage = "The PC is answering."
        }
    }

    @Composable
    override fun ClaudeInScanner(modifier: Modifier, onCode: (String) -> Unit, onDenied: () -> Unit) {
        ClaudeInQrScanner(modifier, onCode, onDenied)
    }

    private fun save(pairing: ClaudeInPairing) {
        settingsRepository.saveCredential(CloudProvider.CLAUDEIN, pairing.toJson().toCharArray())
        claudeInState = ClaudeInLinkState.PAIRED
        claudeInMessage = "Paired with ${pairing.label}."
        refreshPaired()
        status.message = StatusMessage("Paired with ClaudeIn", succeeded = true)
        onChanged()
    }

    /** Runs [block] as the one pairing action in flight, saying what it is doing meanwhile. */
    private fun launch(doing: String, block: suspend () -> Unit) {
        job?.cancel()
        val before = claudeInState
        claudeInState = ClaudeInLinkState.CHECKING
        claudeInMessage = doing
        job = scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                val said = failure.message ?: "ClaudeIn couldn't be reached."
                claudeInMessage = said
                claudeInState = when {
                    said == ClaudeInOutcome.WRONG_APP -> ClaudeInLinkState.WRONG_APP
                    said.startsWith(ClaudeInOutcome.PC_NOT_AVAILABLE) && stored() != null -> ClaudeInLinkState.NOT_AVAILABLE
                    stored() != null -> if (before == ClaudeInLinkState.CHECKING) ClaudeInLinkState.PAIRED else before
                    else -> ClaudeInLinkState.NOT_PAIRED
                }
            }
        }
    }

    /** A run's own failure, so the card says what the status line said. Called by the host. */
    fun noteRunFailure(message: String?) {
        if (message == null || stored() == null) return
        when {
            message == ClaudeInOutcome.WRONG_APP -> claudeInState = ClaudeInLinkState.WRONG_APP
            message.startsWith(ClaudeInOutcome.PC_NOT_AVAILABLE) -> claudeInState = ClaudeInLinkState.NOT_AVAILABLE
            else -> return
        }
        claudeInMessage = message
    }
}
