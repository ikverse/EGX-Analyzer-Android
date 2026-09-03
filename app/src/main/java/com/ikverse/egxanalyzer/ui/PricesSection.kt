package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.ikverse.egxanalyzer.model.MarketRefresh
import com.ikverse.egxanalyzer.model.ScheduleClock
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Where prices come from and when: the market-hours refresh, what it is doing, and Fetch now.
 *
 * A group inside **General** rather than a card of its own. It sits beside Sync because the two are
 * the same kind of thing - the free, unpaid ways this device keeps its own copy current - and
 * neither is worth a card holding one control.
 *
 * **The per-stock fault list that used to lead this section is gone**, on the owner's decision of
 * 2026-09-03. `PriceHealth` is still computed and still raises the "price feed has gone quiet"
 * notification; what went is the page that named every affected stock and explained each fault in
 * words. The one line below is what is left of it: how many stocks are not coming through and how
 * much of the record they are holding, which is the half of that card anybody acted on.
 */
@Composable
internal fun PricesSubSection(appState: AppState) {
    val scope = rememberCoroutineScope()
    val health = appState.priceHealth
    val held = health.callsHeld
    SubSection(
        "Prices",
        // A fault outranks the schedule here. Which minutes the refresh runs on is worth reading
        // once; stocks the feed has gone quiet about are worth seeing without opening anything.
        summary = when {
            !health.clean ->
                "${health.faults.size} of ${health.stocksNamed} stocks not coming through"

            appState.marketRefreshEnabled -> "Kept fresh while the market is open"
            else -> "Fetched once a day"
        },
        about = infoNote(
            "Prices",
            "Prices come from a free public feed and nothing here is sent to the AI provider, so " +
                "neither the refresh nor the button costs anything.",
            "A stock the feed has gone quiet about is not being judged wrongly - it is not being " +
                "judged at all. Any call on one of them sits outside every rate on the Insights " +
                "tab until its prices come back.",
        ),
    ) {
        // The whole of the configuration. What this replaced was a job in a schedule table, set up
        // through a form with a trigger kind, a day picker, a window and an interval - for the one
        // answer everybody was going to give it.
        SettingToggle(
            label = "Keep prices fresh while the market is open",
            checked = appState.marketRefreshEnabled,
            onCheckedChange = appState::updateMarketRefreshEnabled,
            about = infoNote(
                "Keep prices fresh while the market is open",
                "Every ${MarketRefresh.EVERY_MINUTES} minutes, Sunday to Thursday, " +
                    "${ScheduleClock.clock(ScheduleClock.sessionStart)} to " +
                    "${ScheduleClock.clock(ScheduleClock.sessionEnd)} Cairo time.",
                "It reads the same free public feed the Fetch prices button does, so it costs " +
                    "nothing and sends nothing to the AI provider.",
                "Off, prices are fetched once a day when you open the app.",
            ),
        )
        // Never blank, which is the point of it. The failure mode of everything that runs while
        // the app is closed is silence - the phone puts it to sleep, nothing fires, and nothing
        // says so - and a line that always reports something is the only way to tell from the
        // outside that it is working.
        val status = marketRefreshLine(
            enabled = appState.marketRefreshEnabled,
            note = appState.marketRefreshNote,
            noteAt = appState.marketRefreshNoteAt,
            now = Instant.now(),
            exactAlarms = appState.exactAlarmsAllowed(),
            batteryExempt = appState.batteryOptimizationExempt(),
        )
        Text(
            status.text,
            style = MaterialTheme.typography.bodySmall,
            color = if (status.warning) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        // Only once it is switched on: two system pages offered beside a checkbox nobody has
        // ticked is a section that reads as a list of chores rather than as a setting.
        if (appState.marketRefreshEnabled) SystemPermissions(appState)
        // All that is left of the fault list, and only when there is something to say. Said in
        // calls as well as in stocks: a count of broken symbols is trivia, and how much of the
        // record they are holding is the reason anybody would read it at all.
        if (!health.clean) {
            Text(
                "${health.faults.size} of ${health.stocksNamed} stocks are not coming through" +
                    if (held > 0) {
                        " · $held ${if (held == 1) "call" else "calls"} cannot be judged"
                    } else {
                        " · no call is waiting on them"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = if (held > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        // Offered whatever the state, because it is also how a reader confirms nothing has changed.
        // A price fetch costs nothing: it reads a free public feed and sends nothing to the model.
        SettingRow(
            about = infoNote(
                "Fetch prices now",
                "Prices come from a free public feed and nothing is sent to the AI provider, " +
                    "so fetching them costs nothing.",
                "It is the one thing that can help a stock the feed has never priced. A stock " +
                    "that keeps answering with the same day is trading under a new code, and " +
                    "fetching again cannot bring the old one back.",
            ),
        ) {
            OutlinedButton(
                onClick = { scope.launch { appState.refreshPrices() } },
                enabled = !appState.pricesRefreshing,
            ) {
                Text(if (appState.pricesRefreshing) "Fetching…" else "Fetch prices now")
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * What the market-hours refresh is doing, in one line that is never blank.
 *
 * Three things a reader can want from it and they are ranked, because a line that reports the last
 * fetch over a phone that is going to sleep between them is a line that lies quietly. So: the two
 * ways the system can stop this working are said first and in the error colour, and only a setup
 * that can actually keep its promise gets to report on the fetches.
 *
 * Separated from the composable so the wording can be tested, which matters here more than
 * anywhere else on the page: these are the sentences that will be read on the morning somebody
 * wonders why the prices have not moved.
 */
internal data class MarketRefreshStatus(val text: String, val warning: Boolean)

internal fun marketRefreshLine(
    enabled: Boolean,
    note: String?,
    noteAt: Long,
    now: Instant,
    exactAlarms: Boolean = true,
    batteryExempt: Boolean = true,
): MarketRefreshStatus = when {
    !enabled -> MarketRefreshStatus(
        "Off. Prices are fetched once a day, the first time you open the app.",
        warning = false,
    )

    !exactAlarms -> MarketRefreshStatus(
        "On, but exact alarms are off - a fetch can arrive up to an hour late, which for a " +
            "quarter-hourly refresh means most of them will not happen.",
        warning = true,
    )

    !batteryExempt -> MarketRefreshStatus(
        "On, but battery optimization can put this app to sleep, and a sleeping app fetches " +
            "nothing at all.",
        warning = true,
    )

    // Said as its own case rather than left blank: on the day this is switched on there is
    // nothing to report yet, and an empty line reads exactly like one that has stopped working.
    note == null || noteAt <= 0L -> MarketRefreshStatus(
        "On. Nothing fetched yet - next ${whenLabel(MarketRefresh.nextFire(now), now)}.",
        warning = false,
    )

    else -> MarketRefreshStatus(
        "Last ${whenLabel(Instant.ofEpochMilli(noteAt), now)} · $note · " +
            "next ${whenLabel(MarketRefresh.nextFire(now), now)}",
        warning = false,
    )
}
