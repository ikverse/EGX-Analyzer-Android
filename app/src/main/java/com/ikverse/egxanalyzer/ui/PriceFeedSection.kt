package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import android.os.PowerManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.ikverse.egxanalyzer.data.JobScheduler
import com.ikverse.egxanalyzer.data.FeedFault
import com.ikverse.egxanalyzer.data.StockHealth
import com.ikverse.egxanalyzer.model.MarketRefresh
import com.ikverse.egxanalyzer.model.ScheduleClock
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Which stocks the price feed has gone quiet about, and why - in Settings, with the diagnostics.
 *
 * It used to sit on Insights, under the ranking. That was the wrong home twice over. It **explains
 * a figure rather than being one**, so every reader of the record scrolled past a fault report
 * about four stocks to reach it; and it is consulted when something looks wrong, which is what
 * Settings is for. The record it was qualifying still says the same thing without it - a call on a
 * stock with no prices is reported as unjudged on its own card, where the reader is actually
 * looking at that call.
 *
 * **Only the stocks with something wrong.** The catalog holds every Cairo listing and the record
 * names a few dozen; a page listing the healthy ones is a page nobody reads to the bottom of.
 *
 * Unlike the Insights version this card is **present even when nothing is wrong**, and that is the
 * one deliberate difference. There it was absent on a healthy day, because a section reading "0
 * problems" over a record is a section that stops being read on the day it says something. Here it
 * is a place to go and look, and a diagnostic that vanishes when it is working leaves the reader
 * unable to tell "everything is fine" from "the app forgot to check".
 *
 * The explanations are written for someone who wants to know **why a price is missing**, not for
 * someone who knows what a symbol migration is. Each says what happened, what it costs, and whether
 * fetching again can do anything about it - which for two of the three faults it cannot.
 */
@Composable
internal fun PriceFeedSettingsSection(appState: AppState, contentMaxWidth: Dp) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val health = appState.priceHealth
    val held = health.callsHeld
    ExpandableSection(
        "Price feed",
        icon = Icons.Outlined.CloudOff,
        summary = when {
            health.stocksNamed == 0 -> "No stocks to price yet"
            health.clean -> "All ${health.stocksNamed} stocks priced"
            // The count of broken stocks is the small half of this. What matters is the second
            // clause: a rate resting on fewer calls than the reader thinks is what a quiet feed
            // actually does.
            else -> "${health.faults.size} of ${health.stocksNamed} stocks not coming through" +
                if (held > 0) {
                    " · $held ${if (held == 1) "call" else "calls"} cannot be judged"
                } else {
                    " · no call is waiting on them"
                }
        },
        summaryTone = if (held > 0) MaterialTheme.colorScheme.error else null,
        contentMaxWidth = contentMaxWidth,
        about = infoNote(
            "Price feed",
            "A stock the feed has gone quiet about is not being judged wrongly - it is not being " +
                "judged at all. Any call on one of them sits outside every rate on the Insights " +
                "tab until its prices come back.",
            "The list below is present even when nothing is wrong. A diagnostic that vanishes " +
                "when it is working leaves you unable to tell \"everything is fine\" from \"the " +
                "app forgot to check\".",
        ),
    ) {
        // The state itself stays on the page: it is what somebody opened this card to read. What
        // moved behind the question mark is the standing explanation of what a fault costs.
        if (health.clean) {
            Text(
                if (health.stocksNamed == 0) {
                    "Nothing has been analysed yet, so there are no stocks to fetch prices for."
                } else {
                    "Every stock your saved analyses name has prices, and all of them are current."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            health.faults.forEach { stock ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FeedFaultRow(stock)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
            exactAlarms = JobScheduler(context).canScheduleExact(),
            batteryExempt = context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName),
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
        if (appState.marketRefreshEnabled) SystemPermissions()
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Offered whatever the state, because it is also how a reader confirms nothing has changed.
        // A price fetch costs nothing: it reads a free public feed and sends nothing to the model.
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { scope.launch { appState.refreshPrices() } },
                enabled = !appState.pricesRefreshing,
            ) {
                Text(if (appState.pricesRefreshing) "Fetching…" else "Fetch prices now")
            }
            InfoButton(
                infoNote(
                    "Fetch prices now",
                    "Prices come from a free public feed and nothing is sent to the AI provider, " +
                        "so fetching them costs nothing.",
                ),
            )
        }
    }
}

/** One stock, what has happened to its prices, and whether anything can be done about it. */
@Composable
private fun FeedFaultRow(stock: StockHealth) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalAlignment = Alignment.Top,
    ) {
        StockLogo(stock.ticker, LogoSize.Row)
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(stock.ticker, style = MaterialTheme.typography.titleSmall)
            // Every fault it has, not only the worst: a stock can be both frozen and split, and
            // naming one would hide the other on the stocks with most wrong with them.
            stock.faults.forEach { fault ->
                Text(
                    fault.plainly(stock),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // What it is costing, said in calls rather than in stocks. A count of broken symbols is
            // trivia; how much of the record they are holding is the reason to read this at all.
            if (stock.callsHeld > 0) {
                Text(
                    "${stock.callsHeld} ${if (stock.callsHeld == 1) "call" else "calls"} on this " +
                        "stock cannot be scored until this is fixed.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * What went wrong with this stock's prices, said the way it would be said out loud.
 *
 * [FeedFault.detail] is the short form the record keeps; this is the same fact with the stock's own
 * dates in it and the jargon taken out. A reader who opens this card wants to know why a price is
 * missing and whether pressing the button will help - "that is what a symbol that has been retired
 * looks like from here" answers neither question for anyone who has not met the phrase before.
 */
private fun FeedFault.plainly(stock: StockHealth): String = when (this) {
    FeedFault.UNPRICED ->
        "No prices have ever arrived for this stock, so nothing called on it can be judged. " +
            "This is the one fault a fetch can fix - try the button below. If it keeps saying " +
            "this, the exchange code the app holds for the stock is no longer one the feed knows."

    FeedFault.STALE -> {
        val since = stock.newestSession?.let { session ->
            val age = stock.ageDays
            "The last day it has is $session" + (age?.let { ", ${it} ${it.dayWord()} ago" } ?: "") +
                ". "
        } ?: ""
        "The feed still answers for this stock, and keeps sending the same day back. " + since +
            "That is what happens when a company starts trading under a new code: the old code " +
            "stays alive and simply stops moving. Fetching again cannot help - nothing new is " +
            "coming down that line."
    }

    FeedFault.SCALE_CHANGED ->
        "This stock split its shares, or handed out bonus ones, part way through the period " +
            "being measured. Prices before that day and after it are in different money, so a " +
            "call that spans it would read as a collapse that never happened. The app leaves " +
            "those calls unjudged rather than guessing the ratio and rewriting a year of prices."
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
