package com.ikverse.egxanalyzer.data

import android.content.Context
import com.ikverse.egxanalyzer.model.DailySession
import com.ikverse.egxanalyzer.model.IntradayBar
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.net.InetSocketAddress
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The one-minute poll, and the one-minute bars the ordering now asks for.
 *
 * Robolectric because the store is real SQLite and the mapping is a real asset. The feed is a
 * server on this machine that answers at whatever granularity it is asked for and writes down what
 * that was, so what the app requested is observable as well as what it stored.
 */
@RunWith(RobolectricTestRunner::class)
class LivePollTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private lateinit var server: HttpServer
    private val asked = CopyOnWriteArrayList<String>()

    /** The bars the feed holds for the session being asked about: (minute, high, low, close). */
    private var feed: List<Bar> = emptyList()

    /** Granularities the feed refuses, the way it refuses one-minute bars past its wall. */
    private var refused: Set<String> = emptySet()

    private data class Bar(val minute: Int, val high: Double, val low: Double, val close: Double)

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val query = exchange.requestURI.query.orEmpty()
                .split("&").associate { it.substringBefore("=") to it.substringAfter("=") }
            val interval = query.getValue("interval")
            asked += interval
            val start = query.getValue("period1").toLong()
            val (status, body) = if (interval in refused) {
                422 to "{}"
            } else {
                200 to payload(interval, start)
            }
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun payload(interval: String, dayStart: Long): String {
        // 08:00 UTC is the bell in Cairo.
        val open = dayStart + 8 * 3600
        fun list(select: (Bar) -> Any) = feed.joinToString(",", "[", "]") { select(it).toString() }
        return """{"chart":{"result":[{"meta":{"dataGranularity":"$interval"},
            "timestamp":${list { open + it.minute * 60L }},
            "indicators":{"quote":[{"open":${list { it.close }},"high":${list { it.high }},
            "low":${list { it.low }},"close":${list { it.close }},"volume":${list { 100 }}}]}}]}}"""
    }

    private fun repository(store: LocalDataStore) = IntradayRepository(
        store,
        SymbolMap(context.assets),
        "http://127.0.0.1:${server.address.port}/%s",
    )

    private fun sourceOf(store: LocalDataStore, on: LocalDate): String? = store.readableDatabase
        .rawQuery(
            "SELECT source FROM daily_prices WHERE ticker = ? AND session_date = ?",
            arrayOf(TICKER, on.toString()),
        )
        .use { if (it.moveToFirst()) it.getString(0) else null }

    @Test
    fun `a live minute stores today's session and its bars, and does not mark it asked`() {
        val store = LocalDataStore(context)
        feed = listOf(Bar(0, 130.5, 130.0, 130.2), Bar(1, 131.0, 130.1, 130.9))

        val changed = runBlocking {
            repository(store).pollLive(listOf(TICKER), TODAY.minusDays(1), TODAY)
        }

        // Nothing stored before today, so there is no close to read a change of scale off.
        assertEquals(LivePoll(changed = 1), changed)
        val session = store.latestSession(TICKER)!!
        assertEquals(TODAY, session.date)
        assertEquals(131.0, session.high!!, 0.0)
        assertEquals(130.0, session.low!!, 0.0)
        assertEquals(130.9, session.close!!, 0.0)
        assertEquals(130.2, session.open!!, 0.0)
        assertFalse(session.derived)
        assertEquals(IntradayRepository.LIVE_SOURCE, sourceOf(store, TODAY))
        assertEquals(2, store.intradayBars().getValue(TICKER to TODAY).size)
        // Half a day. Marked as asked, the afternoon would never be fetched.
        assertTrue(store.intradayFetched().isEmpty())
        assertEquals(listOf(IntradayRepository.ONE_MINUTE), asked.toList())
    }

    @Test
    fun `a minute in which nothing moved changes nothing`() {
        val store = LocalDataStore(context)
        feed = listOf(Bar(0, 130.5, 130.0, 130.2))
        val repository = repository(store)

        runBlocking {
            assertEquals(1, repository.pollLive(listOf(TICKER), TODAY.minusDays(1), TODAY).changed)
            assertEquals(0, repository.pollLive(listOf(TICKER), TODAY.minusDays(1), TODAY).changed)
            feed = feed + Bar(1, 130.8, 130.3, 130.8)
            assertEquals(1, repository.pollLive(listOf(TICKER), TODAY.minusDays(1), TODAY).changed)
        }
        assertEquals(2, store.intradayBars().getValue(TICKER to TODAY).size)
    }

    @Test
    fun `a session the exchange has finished with is never written by the live poll`() {
        val store = LocalDataStore(context)
        store.saveSessions(listOf(reported(TODAY, 129.0)), PriceRepository.SOURCE)
        feed = listOf(Bar(0, 140.0, 120.0, 135.0))

        val changed = runBlocking { repository(store).pollLive(listOf(TICKER), TODAY, TODAY) }

        assertEquals(LivePoll(), changed)
        assertEquals(129.0, store.latestSession(TICKER)!!.close!!, 0.0)
        assertEquals(PriceRepository.SOURCE, sourceOf(store, TODAY))
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `a feed with no bars yet leaves the stored session alone`() {
        val store = LocalDataStore(context)
        store.saveSessions(listOf(reported(TODAY.minusDays(1), 129.0)), PriceRepository.SOURCE)

        val changed = runBlocking {
            repository(store).pollLive(listOf(TICKER), TODAY.minusDays(1), TODAY)
        }

        assertEquals(LivePoll(), changed)
        assertEquals(TODAY.minusDays(1), store.latestSession(TICKER)!!.date)
    }

    @Test
    fun `a live session on a different scale from the last close is held back, not written`() {
        val store = LocalDataStore(context)
        store.saveSessions(listOf(reported(TODAY.minusDays(1), 129.0)), PriceRepository.SOURCE)
        // A two-for-one split, as the feed shows it the morning after: half the price.
        feed = listOf(Bar(0, 65.0, 64.0, 64.5))

        val poll = runBlocking {
            repository(store).pollLive(listOf(TICKER), TODAY.minusDays(1), TODAY)
        }

        assertEquals(LivePoll(changed = 0, heldBack = listOf(TICKER)), poll)
        // Neither half of the write happened: the history is still all in one money, and there
        // are no bars for the scorer to order a session against.
        assertEquals(TODAY.minusDays(1), store.latestSession(TICKER)!!.date)
        assertTrue(store.intradayBars().isEmpty())
    }

    @Test
    fun `a hard but legal session is written`() {
        val store = LocalDataStore(context)
        store.saveSessions(listOf(reported(TODAY.minusDays(1), 129.0)), PriceRepository.SOURCE)
        feed = listOf(Bar(0, 142.0, 140.0, 141.0))

        val poll = runBlocking {
            repository(store).pollLive(listOf(TICKER), TODAY.minusDays(1), TODAY)
        }

        assertEquals(LivePoll(changed = 1), poll)
        assertEquals(TODAY, store.latestSession(TICKER)!!.date)
    }

    @Test
    fun `a last close more than a week old says nothing about scale`() {
        val store = LocalDataStore(context)
        store.saveSessions(listOf(reported(TODAY.minusDays(10), 129.0)), PriceRepository.SOURCE)
        feed = listOf(Bar(0, 65.0, 64.0, 64.5))

        val poll = runBlocking {
            repository(store).pollLive(listOf(TICKER), TODAY.minusDays(1), TODAY)
        }

        assertEquals(LivePoll(changed = 1), poll)
    }

    @Test
    fun `the check is against the last close, not an earlier minute of today`() {
        val store = LocalDataStore(context)
        store.saveSessions(listOf(reported(TODAY.minusDays(1), 129.0)), PriceRepository.SOURCE)
        feed = listOf(Bar(0, 65.0, 64.0, 64.5))
        val repository = repository(store)

        runBlocking {
            // Held back every minute it is asked, not only the first.
            repeat(2) {
                assertEquals(
                    listOf(TICKER),
                    repository.pollLive(listOf(TICKER), TODAY.minusDays(1), TODAY).heldBack,
                )
            }
        }
    }

    @Test
    fun `one-minute bars reach 29 days back and five-minute bars take over from there`() {
        assertEquals("1m", IntradayRepository.intervalFor(TODAY.minusDays(1), TODAY))
        assertEquals("1m", IntradayRepository.intervalFor(TODAY.minusDays(29), TODAY))
        assertEquals("5m", IntradayRepository.intervalFor(TODAY.minusDays(30), TODAY))
        assertEquals("5m", IntradayRepository.intervalFor(TODAY.minusDays(59), TODAY))
    }

    @Test
    fun `ordering asks for one minute where the feed has it and five where it does not`() {
        val store = LocalDataStore(context)
        feed = listOf(Bar(0, 130.5, 130.0, 130.2))
        val recent = UnorderedSession(TICKER, TODAY.minusDays(5))
        val old = UnorderedSession(TICKER, TODAY.minusDays(40))

        val gained = runBlocking { repository(store).fetchMissing(listOf(recent, old), TODAY) }

        assertEquals(2, gained)
        assertEquals(setOf("1m", "5m"), asked.toSet())
        assertEquals(2, asked.size)
        assertEquals(setOf(TICKER to recent.date, TICKER to old.date), store.intradayFetched())
    }

    @Test
    fun `a one-minute request the feed refuses falls back to five minutes`() {
        val store = LocalDataStore(context)
        feed = listOf(Bar(0, 130.5, 130.0, 130.2))
        refused = setOf("1m")
        val edge = UnorderedSession(TICKER, TODAY.minusDays(29))

        val gained = runBlocking { repository(store).fetchMissing(listOf(edge), TODAY) }

        assertEquals(1, gained)
        assertEquals(listOf("1m", "5m"), asked.toList())
    }

    @Test
    fun `a session's bars are replaced whole, never mixed across granularities`() {
        val store = LocalDataStore(context)
        val day = TODAY.minusDays(1)
        val start = day.atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(8 * 3600)
        fun bar(minute: Long) = IntradayBar(TICKER, start.plusSeconds(minute * 60), 131.0, 130.0)

        store.saveLiveBars(TICKER, day, (0L..9L).map(::bar))
        store.saveIntradayBars(TICKER, day, listOf(bar(0), bar(5)))

        assertEquals(2, store.intradayBars().getValue(TICKER to day).size)
    }

    @Test
    fun `leftover live bars are pruned, and bars the ordering asked for are kept`() {
        val store = LocalDataStore(context)
        fun bars(on: LocalDate) = listOf(
            IntradayBar(TICKER, on.atStartOfDay().toInstant(ZoneOffset.UTC), 131.0, 130.0),
        )
        store.saveLiveBars(TICKER, TODAY.minusDays(1), bars(TODAY.minusDays(1)))
        store.saveIntradayBars(TICKER, TODAY.minusDays(2), bars(TODAY.minusDays(2)))
        store.saveLiveBars(TICKER, TODAY, bars(TODAY))

        val removed = store.pruneLiveBars(TODAY)

        assertEquals(1, removed)
        assertEquals(
            setOf(TICKER to TODAY.minusDays(2), TICKER to TODAY),
            store.intradayBars().keys,
        )
    }

    private fun reported(on: LocalDate, close: Double) = DailySession(
        ticker = TICKER,
        date = on,
        high = close + 0.5,
        low = close - 0.5,
        close = close,
        volume = 1_000.0,
        open = close,
        derived = false,
    )

    private companion object {
        /** Mapped to an ISIN symbol in the shipped asset, which is what gives it intraday bars. */
        const val TICKER = "COMI"

        /** A Thursday. */
        val TODAY: LocalDate = LocalDate.of(2026, 11, 12)
    }
}
