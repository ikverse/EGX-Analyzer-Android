package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.Scoring

/**
 * Which stocks a tab actually holds something about.
 *
 * Read by the header's ticker picker, which puts these at the top of its list under **On this
 * page**: three screens keep three different records of the same market - saved runs, scored calls,
 * trades taken - and a reader hunting for a stock on one of them is hunting inside that record, not
 * inside the exchange's listing of every company.
 *
 * **Out here rather than on any of the three screens**, and for `PageState.filtersActive`'s reason
 * restated: the question is asked from *above* the screen that owns the answer. The header is drawn
 * by `Screen`, which knows only which destination it is topping. Three screens each publishing
 * their own version of "the stocks I hold" is three that agree until one gains a source of stocks
 * and the header silently stops seeing it.
 *
 * **Normalized through [Scoring.normalizeTicker]**, which is what makes `COMI` and `COMI.CA` one
 * stock here as they are everywhere else. The catalog's own codes are already in that form, so the
 * two sides of the `in` test cannot disagree.
 *
 * Not remembered here: this walks every saved run, every scored call or every open position, so the
 * caller does it once when the list opens rather than once per keystroke. See `TickerPickerList`.
 */
internal fun pageStocks(appState: AppState, destination: AppDestination): Set<String> =
    when (destination) {
        AppDestination.RESULTS -> appState.savedResults.flatMapTo(mutableSetOf()) { saved ->
            saved.result.consolidated.map { stock ->
                Scoring.normalizeTicker(stock.stockCode)
            }
        }

        AppDestination.INSIGHTS -> appState.performance.sessions.flatMapTo(mutableSetOf()) { session ->
            session.calls.map { call -> Scoring.normalizeTicker(call.ticker) }
        }

        AppDestination.PORTFOLIO -> appState.portfolio.positions.mapTo(mutableSetOf()) { held ->
            Scoring.normalizeTicker(held.ticker)
        }

        // The two pages with no list to narrow, which get no search icon at all and so never ask.
        AppDestination.ANALYZE, AppDestination.SETTINGS -> emptySet()
    }
