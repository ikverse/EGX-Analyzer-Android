package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ikverse.egxanalyzer.model.FoldPosture
import com.ikverse.egxanalyzer.model.needsYou
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.KitDialog
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.SplitColumns
import com.ikverse.egxanalyzer.ui.kit.popIn

/** The key `HomeMarketRepository` gives the index that leads the page. */
private const val LeadIndex = "EGX30"

/**
 * Home: the market first, then the reader's own book, then the news.
 *
 * The first tab since 2026-11-12 and the page the app opens on. Everything on it is either free -
 * four index series, three outside numbers and four RSS feeds, all public - or already on the
 * phone: the movers and the breadth come off the stored prices, and the book tiles off the same
 * `portfolio` and `sessionDigest` the Portfolio page reads. The one thing that costs anything is
 * the brief, and that is a press through a confirmation.
 *
 * **Three postures.** The cover screen is one column. Unfolded and in book posture the market and
 * the book are on the left and the movers, the news and the brief on the right, split on the hinge
 * in book. Tabletop keeps what is read - the indices and the book - on the upright half and what
 * is pressed - the headlines, the brief button, the trades that need a look - on the flat half.
 *
 * No stock box and no filter sheet: there is no list here to narrow.
 */
@Composable
internal fun HomeScreen(appState: AppState) {
    Screen(
        appState = appState,
        destination = AppDestination.HOME,
        onRefresh = {
            appState.startAction { appState.refreshPrices() }
            appState.refreshHomeMarket(force = true)
            appState.refreshHomeNews(force = true)
        },
        refreshing = appState.pricesRefreshing || appState.homeNewsLoading,
    ) {
        // Both are no-ops while what the page holds is fresh, so arriving here costs nothing twice.
        LaunchedEffect(Unit) {
            appState.refreshHomeMarket()
            appState.refreshHomeNews()
        }

        val posture = LocalFoldPosture.current
        val indices = appState.homeIndices
        val lead = indices.firstOrNull { it.key == LeadIndex }
        val others = indices.filter { it.key != LeadIndex }
        val portfolio = appState.portfolio
        val threshold = appState.appPreferences.approachThresholdPercent
        val needs = remember(portfolio, threshold) { needsYou(portfolio.positions, threshold) }
        val toPortfolio = { appState.navigate(AppDestination.PORTFOLIO) }
        val toInsights = { appState.navigate(AppDestination.INSIGHTS) }
        val openTrade: (String) -> Unit = { appState.openPosition(it) }
        val range = appState.pages.homeRange
        val newsOpen = appState.pages.homeNewsOpen

        // The book's two small tiles: side by side, or the calls alone where nothing needs a look.
        val bookPair: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
                if (needs.isNotEmpty()) NeedsLookTile(needs, openTrade, Modifier.weight(1f))
                CallsTodayTile(appState.sessionDigest, toInsights, Modifier.weight(1f))
            }
        }
        val news: @Composable (Pair<Int, Int>) -> Unit = { limit ->
            NewsTile(
                headlines = appState.homeHeadlines,
                loading = appState.homeNewsLoading,
                fetchedAt = appState.homeNewsAt,
                expanded = newsOpen,
                onOpen = appState::openHeadline,
                onRefresh = { appState.refreshHomeNews(force = true) },
                limit = limit,
            )
        }

        when (posture) {
            is FoldPosture.Unfolded, is FoldPosture.Book -> {
                SplitColumns(posture, unfoldedStart = 0.5f, start = {
                    IndexHeroTile(lead, range, Modifier.popIn(0))
                    IndexRow(others, Modifier.popIn(1))
                    BookTile(portfolio.stats, needs, toPortfolio, Modifier.popIn(2))
                    bookPair()
                    MarketTile(appState.marketBreadth)
                    OutsideRow(appState.homeOutside)
                }, end = {
                    MoversRow(appState.homeMovers, Modifier.popIn(1))
                    news(5 to 3)
                    Brief(appState)
                })
            }
            is FoldPosture.Tabletop -> {
                // Read on the upright half, to the crease; pressed on the flat half.
                Row(
                    Modifier.fillMaxWidth().heightIn(min = tabletopHeroHeight(posture)),
                    horizontalArrangement = Arrangement.spacedBy(Kit.TileGap),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
                        IndexHeroTile(lead, range, Modifier.popIn(0), compact = true)
                        OutsideRow(appState.homeOutside)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Kit.TileGap)) {
                        IndexRow(others, Modifier.popIn(1))
                        BookTile(portfolio.stats, needs, toPortfolio, Modifier.popIn(2))
                    }
                }
                SplitColumns(posture, unfoldedStart = 0.5f, start = {
                    news(3 to 2)
                    Brief(appState)
                }, end = {
                    bookPair()
                    MarketTile(appState.marketBreadth)
                    MoversRow(appState.homeMovers)
                })
            }
            else -> {
                IndexHeroTile(lead, range, Modifier.popIn(0))
                IndexRow(others, Modifier.popIn(1))
                BookTile(portfolio.stats, needs, toPortfolio, Modifier.popIn(2))
                bookPair()
                MarketTile(appState.marketBreadth)
                MoversRow(appState.homeMovers)
                OutsideRow(appState.homeOutside)
                news(5 to 3)
                Brief(appState)
            }
        }
    }
}

/**
 * The model's brief, where there is one, and the button that asks for it.
 *
 * The button never sends anything by itself: it opens a dialog that says what will be sent and
 * that it is billed, and only that dialog's own button makes the request. Held in a plain
 * `remember` because it is transient chrome - a fold closing an unanswered question costs nothing.
 */
@Composable
private fun ColumnScope.Brief(appState: AppState) {
    var confirming by remember { mutableStateOf(false) }
    val brief = appState.homeBrief
    val briefOpen = appState.pages.homeBriefOpen
    // A brief the reader has just asked for arrives open; one kept from an earlier launch, shut.
    var wasPending by remember { mutableStateOf(appState.homeBriefPending) }
    LaunchedEffect(appState.homeBriefPending) {
        if (wasPending && !appState.homeBriefPending && appState.homeBrief != null) briefOpen.value = true
        wasPending = appState.homeBriefPending
    }
    brief?.let { BriefTile(it, briefOpen) }
    AskAiButton(
        label = when {
            appState.homeBriefPending -> "Writing the brief…"
            brief == null -> "Ask AI for a market brief"
            else -> "Ask AI for a new brief"
        },
        onClick = { confirming = true },
        enabled = !appState.homeBriefPending,
        working = appState.homeBriefPending,
        modifier = Modifier.fillMaxWidth(),
    )
    if (confirming) {
        KitDialog(
            title = "Ask AI for a market brief?",
            text = "This sends the index figures, the session's movers and the headlines' titles " +
                "to ${appState.opinionModel().ifBlank { "your Ask AI model" }} and uses credits. " +
                "Nothing from Telegram and none of your trades are sent.",
            confirmLabel = "Ask",
            onConfirm = {
                confirming = false
                appState.askMarketBrief()
            },
            dismissLabel = "Not now",
            onDismiss = { confirming = false },
            posture = LocalFoldPosture.current,
            glyph = "✦",
        )
    }
}
