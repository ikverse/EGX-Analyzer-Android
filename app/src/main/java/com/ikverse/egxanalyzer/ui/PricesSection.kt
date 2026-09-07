package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ikverse.egxanalyzer.model.MarketRefresh
import com.ikverse.egxanalyzer.model.PriceSeriesSummary
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.model.SeriesHarvest
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
        PriceSeriesControls(appState)
    }
}

/**
 * Keeping the five-minute record of every session, and getting it back out.
 *
 * Inside the Prices group rather than beside it, because it is the same subject read at a different
 * resolution - and because the two switches want to be seen together: the one above keeps this
 * phone current with a market while it moves, this one keeps what the feed is about to forget.
 *
 * **The note says what a backup does not carry, and that is the most important sentence here.**
 * Everything else this app stores travels in the zip; this deliberately does not, and a reader who
 * assumes otherwise finds out on the day they have lost the phone. It is said on the switch that
 * starts it rather than only in the source, for that reason.
 */
@Composable
private fun PriceSeriesControls(appState: AppState) {
    val scope = rememberCoroutineScope()
    var summary by remember { mutableStateOf(PriceSeriesSummary.EMPTY) }
    var exporting by remember { mutableStateOf(false) }
    // Read when the switch is on and again after a harvest writes its note, rather than published
    // as state: it is a count over a table that reaches a million rows, and a phone that has never
    // turned this on should not pay for the query to be told it holds nothing.
    LaunchedEffect(appState.priceSeriesEnabled, appState.seriesHarvestNoteAt) {
        summary = if (appState.priceSeriesEnabled) {
            appState.priceSeriesSummary()
        } else {
            PriceSeriesSummary.EMPTY
        }
    }
    SettingToggle(
        label = "Keep the five-minute record of each session",
        checked = appState.priceSeriesEnabled,
        onCheckedChange = appState::updatePriceSeriesEnabled,
        about = infoNote(
            "Keep the five-minute record of each session",
            "Once a trading day, after the close, this copies every stock five-minute bars - " +
                "open, high, low, close and volume - and keeps them. The free public feed serves " +
                "them for about two months and then they are gone for everyone, so a session " +
                "this does not copy cannot be recovered later by anything.",
            "Nothing in the app reads them. No figure, rate or verdict rests on them, and " +
                "switching this off changes nothing you can see. It is here so the record can be " +
                "asked questions nobody has thought of yet.",
            "It is not in your backups, and that is deliberate: it grows by roughly 86 MB a " +
                "year, which would make every daily backup that much larger. Save price series " +
                "below is how you keep a copy of your own.",
            "Free - it reads the same feed the button above does and sends nothing to the AI " +
                "provider.",
        ),
    )
    // Never blank once this is on, the rule the line above it follows and for a sharper reason: a
    // refresh that stops shows up as prices that have not moved, and an archive that stops shows up
    // as nothing at all until somebody goes looking for a session that is no longer anywhere.
    val status = seriesHarvestLine(
        enabled = appState.priceSeriesEnabled,
        summary = summary,
        note = appState.seriesHarvestNote,
        noteAt = appState.seriesHarvestNoteAt,
        now = Instant.now(),
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
    // Only where there is something to save. A button offering to export an empty archive is one
    // that fails for a reason the reader then has to work out.
    if (appState.priceSeriesEnabled && !summary.empty) {
        SettingRow(
            about = infoNote(
                "Save price series",
                "Writes every stored bar to Downloads as a CSV - one row per stock per five " +
                    "minutes, carrying the session date, the time of the bar in UTC, and open, " +
                    "high, low, close and volume.",
                "A spreadsheet opens it and a script reads it, so nothing has to be installed " +
                    "to use it. It can be a large file: a year of bars is over a million rows.",
            ),
        ) {
            OutlinedButton(
                enabled = !exporting,
                onClick = {
                    scope.launch {
                        exporting = true
                        runCatching { appState.exportPriceSeries() }
                            .onSuccess {
                                appState.statusMessage =
                                    StatusMessage("Saved to Downloads/$it", succeeded = true)
                            }
                            .onFailure {
                                appState.statusMessage = StatusMessage(
                                    it.message?.takeIf(String::isNotBlank)
                                        ?: "Could not save the price series",
                                    succeeded = false,
                                )
                            }
                        exporting = false
                    }
                },
            ) {
                Text(if (exporting) "Saving…" else "Save price series")
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * What the archive holds and when it last grew, in one line that is never blank while it is on.
 *
 * Separated from the composable so the wording can be tested, exactly as [marketRefreshLine] is,
 * and it carries the same ranking idea: the state meaning this is not working outranks a cheerful
 * report of the last copy. What differs is which state that is. A refresh has two system
 * permissions that can stop it; this has one failure worth colouring, which is that it is switched
 * on, has run, and has still copied nothing.
 */
internal fun seriesHarvestLine(
    enabled: Boolean,
    summary: PriceSeriesSummary,
    note: String?,
    noteAt: Long,
    now: Instant,
): MarketRefreshStatus = when {
    !enabled -> MarketRefreshStatus(
        "Off. Nothing beyond each day closing figures is kept, and the feed drops five-minute " +
            "bars after about two months.",
        warning = false,
    )

    // Said as its own case, because on the day this is switched on there is nothing to report yet
    // and an empty line reads exactly like one that has stopped working.
    note == null || noteAt <= 0L -> MarketRefreshStatus(
        "On. Nothing copied yet - first copy ${whenLabel(SeriesHarvest.nextFire(now), now)}.",
        warning = false,
    )

    // On, it has run, and the archive is still empty. Every other line here would report that as a
    // success, since the fire happened and wrote its note - which is exactly why it is the one
    // shape of failure worth colouring: it is otherwise completely silent.
    summary.empty -> MarketRefreshStatus(
        "On, but nothing has been copied: $note",
        warning = true,
    )

    else -> MarketRefreshStatus(
        "${summary.bars} bars from ${summary.stocks} stocks" +
            seriesSpan(summary) +
            " · last ${whenLabel(Instant.ofEpochMilli(noteAt), now)} · $note",
        warning = false,
    )
}

/**
 * The span the archive covers, or nothing where it covers a single session.
 *
 * Omitted rather than printed as one date twice: "12 Sep to 12 Sep" is a sentence about a bug.
 */
private fun seriesSpan(summary: PriceSeriesSummary): String {
    val from = summary.from ?: return ""
    val through = summary.through ?: return ""
    return if (from == through) ", $from" else ", $from to $through"
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
