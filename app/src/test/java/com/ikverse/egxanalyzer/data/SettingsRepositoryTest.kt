package com.ikverse.egxanalyzer.data

import android.content.Context
import com.ikverse.egxanalyzer.model.CallOrder
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.PortfolioOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * That a preference survives the app being closed, and that a stored value the app no longer
 * understands does not take the screen down with it.
 *
 * Robolectric because these are real SharedPreferences: a fake map would round-trip anything and
 * would never see the one thing worth checking, which is what happens to a value written by a build
 * that is no longer installed.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun repository() = SettingsRepository(context, NoCredentials())

    @Test
    fun `the portfolio order is kept across a restart`() {
        // Urgent first until the user says otherwise, which is what the screen has always opened on.
        assertEquals(PortfolioOrder.URGENT, repository().loadPreferences().portfolioOrder)

        repository().let { it.savePreferences(it.loadPreferences().copy(portfolioOrder = PortfolioOrder.OLDEST)) }

        // A fresh repository, which is what the next launch builds.
        assertEquals(PortfolioOrder.OLDEST, repository().loadPreferences().portfolioOrder)
    }

    @Test
    fun `an order this build does not recognise falls back rather than failing`() {
        // What an install left holding a renamed or dropped option would read back. Stored by name
        // precisely so this case is recognisable at all - by ordinal it would silently become
        // whichever option now sits at that index.
        context.getSharedPreferences("egx_android_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("portfolio_order", "BY_TICKER_DESCENDING")
            .commit()

        assertEquals(PortfolioOrder.URGENT, repository().loadPreferences().portfolioOrder)
    }

    @Test
    fun `the call order is kept across a restart and falls back on a value this build lost`() {
        // Ticker until asked otherwise: the record's own order, and what the calculator produces.
        assertEquals(CallOrder.TICKER, repository().loadPreferences().callOrder)

        repository().let { it.savePreferences(it.loadPreferences().copy(callOrder = CallOrder.SOURCE)) }
        assertEquals(CallOrder.SOURCE, repository().loadPreferences().callOrder)

        // Stored by name for the same reason the portfolio order is - by ordinal, reordering the
        // options would silently reinterpret every install's choice rather than failing visibly.
        context.getSharedPreferences("egx_android_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("call_order", "BY_SESSIONS_ELAPSED")
            .commit()

        assertEquals(CallOrder.TICKER, repository().loadPreferences().callOrder)
    }

    /**
     * The difference between a phone that has settings and one that is starting from nothing.
     *
     * Settings began travelling after they had been configured, so an install from before the
     * change holds a full set with no stamp on it. Left unclaimed it looks exactly like the empty
     * set a reinstall starts with, and the settings the owner actually chose would be overwritten
     * by the other device's rather than published.
     */
    @Test
    fun `an install that has configured nothing claims nothing`() {
        repository().claimSettingsIfUnstamped("phone")

        assertEquals(0L, repository().snapshot().updatedAt)
    }

    @Test
    fun `an install that has configured something claims what it holds`() {
        repository().let { it.savePreferences(it.loadPreferences().copy(defaultTradeWindowSessions = 7)) }

        repository().claimSettingsIfUnstamped("phone")

        assertTrue(repository().snapshot().updatedAt > 0L)
        assertEquals("phone", repository().snapshot().updatedBy)
    }

    /** Claiming is a one-off repair, so it must never overtake a real change made on this device. */
    @Test
    fun `a claim leaves a stamp that already exists alone`() {
        repository().recordSettingsChange("tablet", at = 5_000)

        repository().claimSettingsIfUnstamped("phone")

        assertEquals(5_000L, repository().snapshot().updatedAt)
        assertEquals("tablet", repository().snapshot().updatedBy)
    }

    @Test
    fun `a synced endpoint the settings field would refuse is not adopted`() {
        val before = repository().load().endpoint

        repository().adopt(
            SettingsSnapshot(
                preferences = repository().loadPreferences(),
                provider = CloudProvider.QWEN,
                providers = listOf(
                    ProviderSettings(CloudProvider.QWEN, "https://192.168.1.5/v1", "qwen-vl-max"),
                ),
                useDefaultPromptOnly = false,
                updatedAt = 5L,
                updatedBy = "tablet",
            ),
        )

        // The host stays what it was; the model, which carries no key anywhere, is taken.
        assertEquals(before, repository().load().endpoint)
        assertEquals("qwen-vl-max", repository().load().model)
    }

    @Test
    fun `a synced endpoint the settings field would accept is adopted`() {
        repository().adopt(
            SettingsSnapshot(
                preferences = repository().loadPreferences(),
                provider = CloudProvider.QWEN,
                providers = listOf(
                    ProviderSettings(
                        CloudProvider.QWEN,
                        "https://dashscope-us.aliyuncs.com/compatible-mode/v1",
                        "qwen-vl-max",
                    ),
                ),
                useDefaultPromptOnly = false,
                updatedAt = 5L,
                updatedBy = "tablet",
            ),
        )

        assertEquals("https://dashscope-us.aliyuncs.com/compatible-mode/v1", repository().load().endpoint)
    }

    @Test
    fun `the watch list is kept across a restart and starts empty`() {
        assertTrue(repository().watchlist().isEmpty())

        repository().saveWatchlist(setOf("COMI", "AMOC"))

        assertEquals(setOf("COMI", "AMOC"), repository().watchlist())
    }

    @Test
    fun `price alerts are kept across a restart and a bad row is skipped`() {
        repository().saveWatchAlerts(
            listOf(
                com.ikverse.egxanalyzer.model.WatchAlert("COMI", 12.5, above = true),
                com.ikverse.egxanalyzer.model.WatchAlert("AMOC", 9.8, above = false),
            ),
        )
        assertEquals(2, repository().watchAlerts().size)
        assertEquals("COMI", repository().watchAlerts()[0].ticker)

        context.getSharedPreferences("egx_android_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("watch_alerts", """[{"ticker":"COMI","level":"abc"},{"ticker":"AMOC","level":9.8,"above":false}]""")
            .commit()

        assertEquals(listOf("AMOC"), repository().watchAlerts().map { it.ticker })
    }

    private class NoCredentials : CredentialStore {
        override fun contains(provider: CloudProvider) = false
        override fun save(provider: CloudProvider, credential: CharArray) = Unit
        override fun read(provider: CloudProvider): CharArray? = null
        override fun remove(provider: CloudProvider) = Unit
    }
}
