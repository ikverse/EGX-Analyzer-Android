package com.ikverse.egxanalyzer.data

import android.content.Context
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

    private class NoCredentials : CredentialStore {
        override fun contains(provider: CloudProvider) = false
        override fun save(provider: CloudProvider, credential: CharArray) = Unit
        override fun read(provider: CloudProvider): CharArray? = null
        override fun remove(provider: CloudProvider) = Unit
    }
}
