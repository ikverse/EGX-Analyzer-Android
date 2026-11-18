package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ClaudeInPlan
import com.ikverse.egxanalyzer.ui.kit.ButtonKind
import com.ikverse.egxanalyzer.ui.kit.ButtonSize
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.KitButton
import com.ikverse.egxanalyzer.ui.kit.KitDialog
import com.ikverse.egxanalyzer.ui.kit.KitProgress
import com.ikverse.egxanalyzer.ui.kit.KitSheet
import com.ikverse.egxanalyzer.ui.kit.KitSpinner
import com.ikverse.egxanalyzer.ui.kit.KitTag
import com.ikverse.egxanalyzer.ui.kit.KitTextField
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.kit.TileTone
import com.ikverse.egxanalyzer.ui.kit.tileColors
import com.ikverse.egxanalyzer.ui.theme.palette
import kotlin.math.roundToInt

/**
 * Pairing with ClaudeIn, in Settings → Analysis when ClaudeIn is the provider, in place of the API
 * key field. Two ways in on one card: Scan (the main button) reads ClaudeIn's own QR code, which
 * names the certificate in full; Type it instead takes the address and the code and then asks the
 * owner to compare six characters of the fingerprint. Either way the phone keeps that certificate
 * and trusts no other afterwards.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClaudeInPairingCard(appState: AppState, modifier: Modifier = Modifier) {
    val state = appState.claudeInState
    val paired = appState.claudeInPaired
    var scanning by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    val busy = state == ClaudeInLinkState.CHECKING
    val alert = state == ClaudeInLinkState.NOT_AVAILABLE || state == ClaudeInLinkState.WRONG_APP
    val tone = if (alert) TileTone.ALERT else TileTone.CARD

    if (scanning) {
        ClaudeInScanSheet(
            appState,
            onCode = { text ->
                scanning = false
                appState.pairClaudeInFromQr(text)
            },
            onType = {
                scanning = false
                typing = true
            },
            onDismiss = { scanning = false },
        )
    }
    ClaudeInConfirmDialog(appState)

    Tile(modifier.fillMaxWidth(), tone = tone) {
        val ink = LocalContentColor.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "ClaudeIn on your PC",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
            ClaudeInStateTag(state)
            InfoButton(claudeInAbout)
        }
        Column(Modifier.padding(top = Space.m), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            if (paired != null) {
                Column {
                    Text(paired.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        paired.address,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
                        color = ink.copy(alpha = 0.72f),
                    )
                    Text(
                        "Certificate …${paired.shortFingerprint}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFigures),
                        color = ink.copy(alpha = 0.72f),
                    )
                }
            } else {
                Text(
                    "Uses Claude on your own Claude plan, through ClaudeIn on your PC. No API key.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink.copy(alpha = 0.72f),
                )
            }
            appState.claudeInMessage?.let { said ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    if (busy) KitSpinner()
                    Text(said, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.86f))
                }
            }
            if (typing) {
                ClaudeInTypedForm(
                    enabled = !busy,
                    onPair = { address, code -> appState.pairClaudeInTyped(address, code) },
                    onCancel = { typing = false },
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                if (paired == null) {
                    KitButton("Scan QR code", onClick = { typing = false; scanning = true }, enabled = !busy)
                    if (!typing) {
                        KitButton(
                            "Type it instead",
                            onClick = { typing = true },
                            kind = ButtonKind.SECONDARY,
                            size = ButtonSize.SMALL,
                            enabled = !busy,
                        )
                    }
                } else {
                    KitButton("Check the PC", onClick = appState::checkClaudeIn, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL, enabled = !busy)
                    KitButton("Pair again", onClick = { typing = false; scanning = true }, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL, enabled = !busy)
                    KitButton("Forget", onClick = appState::forgetClaudeIn, kind = ButtonKind.DANGER, size = ButtonSize.SMALL)
                }
            }
        }
    }
}

@Composable
private fun ClaudeInStateTag(state: ClaudeInLinkState) {
    val (text, tone) = when (state) {
        ClaudeInLinkState.NOT_PAIRED -> "Not paired" to TileTone.TILE1
        ClaudeInLinkState.CHECKING -> "Checking" to TileTone.TILE3
        ClaudeInLinkState.PAIRED -> "Paired" to TileTone.HIGHLIGHT
        ClaudeInLinkState.NOT_AVAILABLE -> "PC not available" to TileTone.CARD
        ClaudeInLinkState.WRONG_APP -> "Wrong app" to TileTone.CARD
    }
    val (fill, ink) = tileColors(tone)
    KitTag(text, tone = fill, ink = ink, modifier = Modifier.padding(end = Space.xs))
}

@Composable
private fun ClaudeInTypedForm(enabled: Boolean, onPair: (String, String) -> Unit, onCancel: () -> Unit) {
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val submit = { onPair(address.trim(), code.trim()) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        KitTextField(
            value = address,
            onValueChange = { address = it.filterNot(Char::isWhitespace) },
            label = "PC address, as ClaudeIn shows it",
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next,
            enabled = enabled,
        )
        KitTextField(
            value = code,
            onValueChange = { code = it.uppercase().take(12) },
            label = "Pairing code",
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
            onImeAction = { if (address.isNotBlank() && code.isNotBlank()) submit() },
            enabled = enabled,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            KitButton("Pair", onClick = submit, size = ButtonSize.SMALL, enabled = enabled && address.isNotBlank() && code.isNotBlank())
            KitButton("Cancel", onClick = onCancel, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
        }
    }
}

/**
 * The camera, in the kit's sheet: a bottom sheet folded, a side panel unfolded or in book posture.
 * In tabletop the picture is what the sheet shows first and its buttons sit at its foot, which on a
 * half-open phone is the flat half - [KitSheet]'s own rule for that posture.
 */
@Composable
private fun ClaudeInScanSheet(
    appState: AppState,
    onCode: (String) -> Unit,
    onType: () -> Unit,
    onDismiss: () -> Unit,
) {
    var denied by remember { mutableStateOf(false) }
    KitSheet(onDismiss = onDismiss, posture = LocalFoldPosture.current, title = "Scan ClaudeIn's code") {
        Text("Scan ClaudeIn's code", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(
            "On the PC, open ClaudeIn from the tray, choose Pair a device, and pick EGX Analyzer as the app. Then point the camera at the code.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.muted,
        )
        if (denied) {
            Tile(Modifier.fillMaxWidth(), tone = TileTone.ALERT) {
                Text(
                    "The camera is off for EGX Analyzer. Allow it in the phone's settings, or type the address and code instead.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .aspectRatio(1f)
                    .clip(Kit.TileShape)
                    .semantics { contentDescription = "Camera, looking for ClaudeIn's QR code" },
            ) {
                appState.ClaudeInScanner(Modifier.fillMaxWidth().aspectRatio(1f), onCode = onCode, onDenied = { denied = true })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            KitButton("Type it instead", onClick = onType, kind = ButtonKind.SECONDARY)
            KitButton("Cancel", onClick = onDismiss, kind = ButtonKind.SECONDARY)
        }
    }
}

/** The typed route's last step: the owner compares six characters with ClaudeIn's window. */
@Composable
private fun ClaudeInConfirmDialog(appState: AppState) {
    val confirm = appState.claudeInConfirm ?: return
    KitDialog(
        title = "Does ClaudeIn show these?",
        text = "Compare with the end of the fingerprint in ClaudeIn's pairing window on the PC. Pair only if they are the same.",
        confirmLabel = "They match",
        onConfirm = appState::confirmClaudeIn,
        dismissLabel = "They don't",
        onDismiss = appState::cancelClaudeInConfirm,
        posture = LocalFoldPosture.current,
        glyph = "?",
        extra = {
            Text(
                confirm.shortFingerprint.chunked(3).joinToString(" "),
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = TabularFigures),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = Space.s),
            )
            Text(
                confirm.address,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFigures),
                color = palette.muted,
            )
        },
    )
}

/**
 * How full the Claude plan is, on Analyze while ClaudeIn is the provider: the 5-hour and the 7-day
 * windows, as ClaudeIn last reported them. While a run waits or searches, what the PC says.
 */
@Composable
internal fun ClaudeInPlanTile(appState: AppState, modifier: Modifier = Modifier) {
    val plan = appState.claudeInPlan
    val working = appState.claudeInWorking
    Tile(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Your Claude plan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
            InfoButton(claudeInPlanAbout)
        }
        Column(Modifier.padding(top = Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            if (plan == null) {
                Text(
                    "Not known yet. ClaudeIn reports it after a run.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.72f),
                )
            } else {
                PlanRow("5 hours", plan.fiveHour)
                PlanRow("7 days", plan.sevenDay)
            }
            working?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    KitSpinner()
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PlanRow(label: String, share: Double?) {
    if (share == null) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
        KitProgress(
            fraction = share.toFloat(),
            modifier = Modifier.weight(1f),
            fill = if (share >= PLAN_NEARLY_FULL) palette.loss else palette.highlight,
        )
        Text(
            "${(share * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
            modifier = Modifier.width(44.dp),
        )
    }
}

/** Past this, the bar takes the loss colour: a run is likely to hit the limit part-way. */
private const val PLAN_NEARLY_FULL = 0.85

private val claudeInAbout = infoNote(
    "What is ClaudeIn?",
    "ClaudeIn is a small program on your Windows PC. It lets this app use Claude through Claude " +
        "Code, signed in with your own Claude plan, so there is no API key and no per-run bill.",
    "When the PC is off, asleep, or ClaudeIn isn't running, the app says PC not available and runs " +
        "nothing. It never falls back to another provider.",
    "When you pair, pick EGX Analyzer as the app name in ClaudeIn. A pairing made for another app " +
        "is refused on the first run.",
    "The pairing is kept encrypted on this phone. It is never synced to your other devices and " +
        "never written into a backup.",
)

private val claudeInPlanAbout = infoNote(
    "Your Claude plan",
    "Claude plans have a 5-hour and a 7-day limit. These bars are how full each one was when " +
        "ClaudeIn last said - after a run, or when the PC was checked.",
    "A run sends its screenshots in batches of eight, each one a question to Claude, so a large " +
        "run uses a real share of the 5-hour window. When a limit is reached, the run stops and the " +
        "app says when it resets; it does not keep retrying.",
)
