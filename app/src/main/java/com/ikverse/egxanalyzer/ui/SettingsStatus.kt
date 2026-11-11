package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.ScheduleClock
import java.time.Instant
import java.time.ZoneId

/**
 * One line of the Settings status tile: what it says, whether it is good, bad or neither, and the
 * section that answers it. [ok] null is neutral - a key saved but not yet checked, prices being
 * fetched right now - which is said but is not a fault.
 */
internal data class StatusLine(val text: String, val ok: Boolean?, val section: SettingsSection)

/** The status tile's headline and its lines. */
internal data class SettingsStatus(val headline: String, val lines: List<StatusLine>) {
    /** The first section with something wrong, which a press on the tile opens; null when all is well. */
    val firstFault: SettingsSection? get() = lines.firstOrNull { it.ok == false }?.section
}

/**
 * Whether the three things this app cannot work without are working: Telegram, the AI that reads
 * the posts, and the prices. A function of its arguments, so it is tested without composing.
 *
 * **Prices that stopped coming through outrank when they last came**: a refresh this morning that
 * left four stocks unpriced is the fault, and "prices arrived today" over it would be the screen
 * saying everything was fine about the one thing that is not.
 */
internal fun settingsStatus(
    telegramReady: Boolean,
    credentialVerified: Boolean?,
    hasCredential: Boolean,
    model: String,
    lastPriceRefreshAt: Long,
    pricesRefreshing: Boolean,
    feedClean: Boolean,
    feedFaults: Int,
    stocksNamed: Int,
    now: Instant,
    zone: ZoneId = ScheduleClock.ZONE,
): SettingsStatus {
    val telegram = if (telegramReady) {
        StatusLine("Telegram is connected", true, SettingsSection.TELEGRAM)
    } else {
        StatusLine("Telegram is signed out", false, SettingsSection.TELEGRAM)
    }
    val reader = when {
        credentialVerified == false -> StatusLine("The key was rejected", false, SettingsSection.ANALYSIS)
        !hasCredential -> StatusLine("No key saved yet", false, SettingsSection.ANALYSIS)
        model.isBlank() -> StatusLine("No AI chosen", false, SettingsSection.ANALYSIS)
        credentialVerified == null -> StatusLine("The key is saved but not checked", null, SettingsSection.ANALYSIS)
        else -> StatusLine("The reader is ready", true, SettingsSection.ANALYSIS)
    }
    val prices = when {
        !feedClean -> StatusLine("$feedFaults of $stocksNamed stocks not coming through", false, SettingsSection.GENERAL)
        pricesRefreshing -> StatusLine("Fetching prices…", null, SettingsSection.GENERAL)
        lastPriceRefreshAt <= 0L -> StatusLine("Prices not fetched yet", null, SettingsSection.GENERAL)
        else -> {
            val at = Instant.ofEpochMilli(lastPriceRefreshAt)
            val today = now.atZone(zone).toLocalDate()
            val fetched = at.atZone(zone).toLocalDate()
            // Late only once a trading session has closed since: a Saturday, a Sunday morning or a
            // weekday before the bell has nothing new to fetch. Holidays are not known here.
            var owed = ScheduleClock.lastFinalSession(now, zone)
            while (owed.dayOfWeek !in ScheduleClock.tradingDays) owed = owed.minusDays(1)
            if (at < owed.atTime(ScheduleClock.sessionEnd).atZone(zone).toInstant()) {
                StatusLine("Prices last arrived ${whenLabel(at, now, zone)}", false, SettingsSection.GENERAL)
            } else if (fetched < today) {
                StatusLine("Prices last arrived ${whenLabel(at, now, zone)}", true, SettingsSection.GENERAL)
            } else {
                StatusLine("Prices arrived ${whenLabel(at, now, zone)}", true, SettingsSection.GENERAL)
            }
        }
    }
    val lines = listOf(telegram, reader, prices)
    val faults = lines.count { it.ok == false }
    val headline = when (faults) {
        0 -> "Everything is working"
        1 -> "1 thing needs a look"
        else -> "$faults things need a look"
    }
    return SettingsStatus(headline, lines)
}
