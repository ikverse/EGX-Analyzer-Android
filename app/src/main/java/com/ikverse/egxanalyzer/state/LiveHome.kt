package com.ikverse.egxanalyzer.state

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ikverse.egxanalyzer.data.AnalysisRepository
import com.ikverse.egxanalyzer.data.EgxCatalog
import com.ikverse.egxanalyzer.data.HomeCache
import com.ikverse.egxanalyzer.data.HomeCacheStore
import com.ikverse.egxanalyzer.data.HomeMarketRepository
import com.ikverse.egxanalyzer.data.LocalDataStore
import com.ikverse.egxanalyzer.data.MarketBriefPrompt
import com.ikverse.egxanalyzer.data.NewsRepository
import com.ikverse.egxanalyzer.data.OpinionRequest
import com.ikverse.egxanalyzer.model.Headline
import com.ikverse.egxanalyzer.model.IndexQuote
import com.ikverse.egxanalyzer.model.MarketBreadth
import com.ikverse.egxanalyzer.model.MarketBrief
import com.ikverse.egxanalyzer.model.MarketMovers
import com.ikverse.egxanalyzer.model.OutsideQuote
import com.ikverse.egxanalyzer.model.ScheduleClock
import com.ikverse.egxanalyzer.model.marketBreadth
import com.ikverse.egxanalyzer.model.marketMovers
import com.ikverse.egxanalyzer.ui.HomeFeed
import com.ikverse.egxanalyzer.ui.StatusMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * The Home page's own state: the indices, the outside numbers, the movers, the headlines, the brief.
 *
 * The second region to live outside `LiveAppState`, by the pattern [LiveUpdates] set: a
 * sub-interface on `AppState`, everything it needs as a parameter, `by` at the declaration. It
 * could leave for the reason the updater could - nothing here feeds the recompute. It reads no
 * report and no trade and writes nothing to the database; the one thing it takes from the record is
 * the newest two closes of each listing, read-only, when `LiveAppState` says the prices moved.
 *
 * The two things it needs from its host that a constructor default cannot give - which model Ask AI
 * uses, and the tally to refresh after a paid request - arrive through [bind], called once.
 */
class LiveHome(
    private val context: Context,
    private val localDataStore: LocalDataStore,
    private val analysisRepository: AnalysisRepository,
    private val status: StatusChannel,
    private val market: HomeMarketRepository = HomeMarketRepository(),
    private val news: NewsRepository = NewsRepository(),
    private val cache: HomeCacheStore = HomeCacheStore(File(context.filesDir, HomeCacheStore.FILE_NAME)),
) : HomeFeed {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override var homeIndices by mutableStateOf(emptyList<IndexQuote>())
        private set
    override var homeOutside by mutableStateOf(emptyList<OutsideQuote>())
        private set
    override var homeMovers by mutableStateOf<MarketMovers?>(null)
        private set
    override var homeHeadlines by mutableStateOf(emptyList<Headline>())
        private set
    override var homeNewsLoading by mutableStateOf(false)
        private set
    override var homeNewsAt by mutableStateOf(0L)
        private set
    override var homeBrief by mutableStateOf<MarketBrief?>(null)
        private set
    override var homeBriefPending by mutableStateOf(false)
        private set

    private var marketAt = 0L
    private var marketLoading = false
    private var breadth: MarketBreadth? = null
    private var briefModel: () -> String = { "" }
    private var briefLanguage: () -> String = { "Arabic" }
    private var afterBrief: () -> Unit = {}

    init {
        // What the page showed last time, so the launch page has figures before the network does.
        // Anything a fetch has already delivered by the time the file is read wins over the file.
        scope.launch {
            val kept = withContext(Dispatchers.IO) { cache.load() }
            if (homeIndices.isEmpty()) homeIndices = kept.indices
            if (homeOutside.isEmpty()) homeOutside = kept.outside
            if (marketAt == 0L) marketAt = kept.marketAt
            if (homeHeadlines.isEmpty()) {
                homeHeadlines = kept.headlines
                homeNewsAt = kept.newsAt
            }
            if (homeBrief == null) homeBrief = kept.brief
        }
    }

    /** The host's answers to what a default parameter cannot reach. See the class comment. */
    fun bind(model: () -> String, language: () -> String, spent: () -> Unit) {
        briefModel = model
        briefLanguage = language
        afterBrief = spent
    }

    /** The movers and the breadth off the stored prices. Called after every portfolio pass. */
    suspend fun recomputeMovers() {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val lastTwo = localDataStore.lastTwoCloses()
                marketMovers(lastTwo, nameOf = { EgxCatalog.find(it)?.nameEnglish }) to marketBreadth(lastTwo)
            }.getOrNull()
        } ?: return
        homeMovers = result.first
        breadth = result.second
    }

    override fun refreshHomeMarket(force: Boolean) {
        if (marketLoading) return
        if (!force && System.currentTimeMillis() - marketAt < MARKET_FRESH_MILLIS) return
        marketLoading = true
        scope.launch {
            try {
                val indices = market.indices()
                val outside = market.outside()
                // A feed that did not answer leaves the page as it was rather than emptying it.
                if (indices.isNotEmpty()) homeIndices = indices
                if (outside.isNotEmpty()) homeOutside = outside
                if (indices.isNotEmpty() || outside.isNotEmpty()) {
                    marketAt = System.currentTimeMillis()
                    persist()
                }
            } finally {
                marketLoading = false
            }
        }
    }

    override fun refreshHomeNews(force: Boolean) {
        if (homeNewsLoading) return
        if (!force && System.currentTimeMillis() - homeNewsAt < NEWS_FRESH_MILLIS) return
        homeNewsLoading = true
        scope.launch {
            try {
                val fetched = news.headlines()
                if (fetched != null) {
                    homeHeadlines = fetched
                    homeNewsAt = System.currentTimeMillis()
                    persist()
                } else if (force) {
                    status.message = StatusMessage("Could not reach the news feeds", succeeded = false)
                }
            } finally {
                homeNewsLoading = false
            }
        }
    }

    override fun askMarketBrief() {
        if (homeBriefPending) return
        if (homeIndices.isEmpty() && homeHeadlines.isEmpty()) {
            status.message = StatusMessage("Nothing to brief yet. Pull down to fetch the market first.", succeeded = false)
            return
        }
        homeBriefPending = true
        scope.launch {
            try {
                val model = briefModel()
                val answer = analysisRepository.ask(
                    OpinionRequest(
                        requestId = "home-brief",
                        systemPrompt = MarketBriefPrompt.system(briefLanguage()),
                        question = MarketBriefPrompt.question(
                            today = LocalDate.now(ScheduleClock.ZONE),
                            indices = homeIndices,
                            breadth = breadth,
                            movers = homeMovers,
                            outside = homeOutside,
                            headlines = homeHeadlines,
                        ),
                        model = model,
                        // The headlines are the news. A search on top would be paid for twice.
                        search = false,
                    ),
                )
                homeBrief = MarketBriefPrompt.parse(answer, model, System.currentTimeMillis())
                persist()
                // The brief spends too, and leaves no report behind to record it.
                afterBrief()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                status.message = StatusMessage(
                    failure.message?.takeIf(String::isNotBlank) ?: "The brief could not be written. Try again.",
                    succeeded = false,
                )
            } finally {
                homeBriefPending = false
            }
        }
    }

    override fun openHeadline(headline: Headline) {
        val link = headline.link
        // A feed is somebody else's document: only a web address is ever handed to the system.
        if (!link.startsWith("https://") && !link.startsWith("http://")) return
        val uri = Uri.parse(link)
        // A Custom Tab: the browser's own page drawn over the app, with a close button back to Home.
        // The article stays in the browser's process, never in a web view of this app's. Where no
        // installed browser offers tabs, `launchUrl` opens the ordinary browser instead.
        runCatching {
            val toolbar = { color: Int -> CustomTabColorSchemeParams.Builder().setToolbarColor(color).build() }
            val tab = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .setDefaultColorSchemeParams(toolbar(TabPaper))
                .setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_DARK, toolbar(TabGraphite))
                .build()
            // Launched from the application's context, so it needs a task of its own.
            tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            tab.launchUrl(context, uri)
        }.recoverCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            status.message = StatusMessage("No browser could open that article", succeeded = false)
        }
    }

    private suspend fun persist() {
        val snapshot = HomeCache(homeIndices, homeOutside, marketAt, homeHeadlines, homeNewsAt, homeBrief)
        withContext(Dispatchers.IO) { cache.save(snapshot) }
    }

    private companion object {
        /** Ten minutes: the feed is delayed by more than that, and the page asks on every arrival. */
        const val MARKET_FRESH_MILLIS = 10L * 60 * 1000

        const val NEWS_FRESH_MILLIS = 30L * 60 * 1000

        /** The article tab's toolbar: the page's paper by day, its graphite at night. */
        const val TabPaper = 0xFFEFEDE6.toInt()
        const val TabGraphite = 0xFF141518.toInt()
    }
}
