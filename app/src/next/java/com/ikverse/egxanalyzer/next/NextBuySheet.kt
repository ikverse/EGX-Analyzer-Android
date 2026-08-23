package com.ikverse.egxanalyzer.next

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.model.ScoredCall
import com.ikverse.egxanalyzer.model.positionId
import com.ikverse.egxanalyzer.ui.AppState
import java.time.LocalDate

/**
 * Recording a trade against a call.
 *
 * The one sheet that **adds** a record rather than changing or removing one, so it is the accent.
 *
 * Two things it is careful about. The price it offers is the call's own entry midpoint, not the
 * market's current price - the entry band is what the channel asked for, and the whole reason this
 * feature exists is that what the reader actually paid is a different figure. And the window is
 * offered from the setting but **snapshotted here**: changing the global scoring window afterwards
 * re-judges the channel's record and must never move a deadline the reader has already been given.
 */
@Composable
internal fun NextBuySheet(appState: AppState, page: NextPageState) {
    val colors = LocalNextColors.current
    val wanted = page.buyingCall ?: return
    val call = remember(wanted, appState.performance) {
        appState.performance.sessions
            .flatMap { it.calls }
            .firstOrNull { it.positionId == wanted }
    }
    if (call == null) {
        page.buyingCall = null
        return
    }
    val dismiss = { page.buyingCall = null }

    val suggested = midpoint(call)
    var price by remember(wanted) { mutableStateOf(suggested?.let(::plainPrice).orEmpty()) }
    var date by remember(wanted) { mutableStateOf(LocalDate.now()) }
    var window by remember(wanted) {
        mutableStateOf(appState.appPreferences.defaultTradeWindowSessions)
    }
    var pickingDate by remember(wanted) { mutableStateOf(false) }

    val parsed = price.toDoubleOrNull()
    val ready = parsed != null && parsed > 0

    if (pickingDate) {
        NextDateSheet(
            kicker = "Bought on",
            initial = date,
            onPick = { date = it },
            onDismiss = { pickingDate = false },
            earliest = call.openedOn,
            latest = LocalDate.now(),
            )
        return
    }

    NextModal(
        kicker = "Record a buy · ${call.ticker}",
        tone = colors.accent,
        onDismiss = dismiss,
        meta = "called ${formatDay(call.openedOn)}",
        body = {
            NextText(
                "You are recording your own trade against this call. The call itself does not " +
                    "change, and neither does the channel's score — it is graded on the levels it " +
                    "printed, not on your execution.",
                NextType.name,
                colors.ink2,
            )

            NextFigureGrid(
                columns = 2,
                cells = listOf(
                    {
                        NextFigureCell(
                            "Entry zone",
                            if (call.entryLow != null && call.entryHigh != null) {
                                "${formatPrice(call.entryLow)}–${formatPrice(call.entryHigh)}"
                            } else {
                                formatPrice(call.entryLow ?: call.entryHigh)
                            },
                            colors.entry,
                        )
                    },
                    { NextFigureCell("Target 1", formatPrice(call.target1), colors.target) },
                    { NextFigureCell("Target 2", formatPrice(call.target2), colors.target) },
                    { NextFigureCell("Stop", formatPrice(call.stopLoss), colors.stop) },
                ),
            )

            NextModalDivider()

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
            ) {
                NextField(
                    label = "You paid",
                    value = price,
                    onValueChange = { price = it },
                    modifier = Modifier.weight(1f),
                    problem = if (price.isNotBlank() && parsed == null) "not a price" else null,
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NextMetrics.space2),
                ) {
                    NextLabel("On", color = colors.figMuted)
                    NextButton(
                        label = formatFullDay(date),
                        onClick = { pickingDate = true },
                        tone = colors.rule,
                        labelColor = colors.ink,
                        minHeight = 40.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            NextLabel("Deadline, in trading sessions")
            NextChipStrip(
                options = WINDOWS.map(Int::toString),
                selectedIndex = WINDOWS.indexOf(window),
                onPick = { index -> window = WINDOWS[index] },
                tone = colors.market,
            )
            NextText(
                "Counted from the session the call was made for, not from today — buying two days " +
                    "late does not buy two extra days. Kept as it is now, whatever the setting " +
                    "does later.",
                NextType.meta,
                colors.figMuted,
            )

            parsed?.let { paid ->
                NextModalDivider()
                NextConsequence(
                    mark = "→",
                    markTone = colors.accent,
                    what = "Against the call's entry",
                    where = midpoint(call)?.let { mid ->
                        formatPercent((paid - mid) / mid * 100) + " slippage"
                    } ?: DASH,
                )
            }
        },
        actions = {
            NextButton(
                label = "Record the buy",
                onClick = {
                    parsed?.let { paid ->
                        appState.recordPurchase(
                            ticker = call.ticker,
                            companyEnglish = call.companyEnglish,
                            companyArabic = call.companyArabic,
                            channel = call.channel,
                            recommendationDate = call.openedOn,
                            entryPrice = paid,
                            entryDate = date,
                            entryLow = call.entryLow,
                            entryHigh = call.entryHigh,
                            target1 = call.target1,
                            target2 = call.target2,
                            stopLoss = call.stopLoss,
                            windowSessions = window,
                        )
                        dismiss()
                    }
                },
                tone = colors.accent,
                fill = NextFill.SOLID,
                enabled = ready,
                modifier = Modifier.weight(1f),
            )
            NextButton("Cancel", dismiss, tone = colors.rule, labelColor = colors.ink3)
        },
    )
}

private val WINDOWS = listOf(5, 10, 15, 20, 30)

/** What the call asked you to pay, as one figure. */
private fun midpoint(call: ScoredCall): Double? {
    val low = call.entryLow
    val high = call.entryHigh
    return when {
        low != null && high != null -> (low + high) / 2
        else -> low ?: high
    }
}

private fun plainPrice(value: Double): String {
    val rounded = Math.round(value * 1000.0) / 1000.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}
