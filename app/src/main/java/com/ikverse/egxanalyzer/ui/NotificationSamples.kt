package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.ikverse.egxanalyzer.model.AppPreferences
import com.ikverse.egxanalyzer.ui.kit.Panel
import com.ikverse.egxanalyzer.ui.theme.palette

/** What a notification of one kind looks like: its title and its line. */
internal data class NotificationSample(val title: String, val text: String)

/**
 * Every notification the app can send, as the Settings page offers it: what it is about, when it
 * comes, and an example of it. The examples copy the real notifiers' shapes - `TradeStatusNotifier`,
 * `ApproachNotifier`, `CallAlertNotifier`, `SessionDigestNotifier`, `OverdueNotifier`,
 * `AttentionNotifier` and `ReportReadyNotifier` - with made-up figures.
 */
internal enum class NotificationKind(val label: String, val detail: String, val sample: NotificationSample) {
    TRADE(
        "A trade reaches a target or its stop",
        "as soon as prices show it",
        NotificationSample("COMI took target 1", "Bought at 82.50, now 88.10."),
    ),
    APPROACH(
        "A trade is getting close",
        "near its stop or its second target",
        NotificationSample("COMI is closing on its stop", "1.8% above the stop at 79.00."),
    ),
    CALL(
        "A stock drops into a buying range",
        "for calls you have not bought",
        NotificationSample("COMI is in its buy zone", "Trading at 82.90, inside 82.00 to 83.50."),
    ),
    DIGEST(
        "How the day went",
        "once, after the market closes",
        NotificationSample("9 Nov · what the session did", "3 reached a target, 1 stopped out. 2 of them were yours."),
    ),
    OVERDUE(
        "A trade is past its time",
        "once a day until you decide",
        NotificationSample("1 trade is overdue", "Kept open past the deadline with no sale recorded yet."),
    ),
    FEED(
        "Prices stop arriving",
        "so a quiet app is not mistaken for a quiet market",
        NotificationSample("2 stocks have no usable prices", "ABUK, SWDY have gone quiet, so 3 calls cannot be judged."),
    ),
    REPORT_READY(
        "A report is ready",
        "when a run finishes or fails",
        NotificationSample("Your report is ready", "12 calls found"),
    ),
}

/** Whether [this] kind is switched on. */
internal fun NotificationKind.isOn(prefs: AppPreferences): Boolean = when (this) {
    NotificationKind.TRADE -> prefs.tradeAlertsEnabled
    NotificationKind.APPROACH -> prefs.approachAlertsEnabled
    NotificationKind.CALL -> prefs.callAlertsEnabled
    NotificationKind.DIGEST -> prefs.sessionDigestEnabled
    NotificationKind.OVERDUE -> prefs.overdueRemindersEnabled
    NotificationKind.FEED -> prefs.feedAlertsEnabled
    NotificationKind.REPORT_READY -> prefs.reportReadyEnabled
}

/** Switches [this] kind on or off. */
internal fun NotificationKind.set(appState: AppState, on: Boolean) = when (this) {
    NotificationKind.TRADE -> appState.updateTradeAlerts(on)
    NotificationKind.APPROACH -> appState.updateApproachAlerts(on)
    NotificationKind.CALL -> appState.updateCallAlerts(on)
    NotificationKind.DIGEST -> appState.updateSessionDigest(on)
    NotificationKind.OVERDUE -> appState.updateOverdueReminders(on)
    NotificationKind.FEED -> appState.updateFeedAlerts(on)
    NotificationKind.REPORT_READY -> appState.updateReportReady(on)
}

/** A note with the example of [kind] attached, which the sheet draws under the words. */
internal fun InfoNote.withSample(kind: NotificationKind): InfoNote = copy(sample = kind.sample)

/** A notification as the shade would draw it, in the app's own colours, for "What it looks like". */
@Composable
internal fun NotificationPreview(sample: NotificationSample, modifier: Modifier = Modifier) {
    val p = palette
    Panel(modifier.fillMaxWidth(), color = p.raised) {
        Text("EGX Analyzer", style = MaterialTheme.typography.labelSmall, color = p.muted)
        Text(sample.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = p.ink)
        Text(sample.text, style = MaterialTheme.typography.bodySmall, color = p.muted)
    }
}
