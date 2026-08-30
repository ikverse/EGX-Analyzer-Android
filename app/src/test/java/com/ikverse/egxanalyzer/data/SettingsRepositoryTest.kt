package com.ikverse.egxanalyzer.data

import android.content.Context
import com.ikverse.egxanalyzer.model.AnalysedChannel
import com.ikverse.egxanalyzer.model.AnalysisContentType
import com.ikverse.egxanalyzer.model.AnalysisSchedule
import com.ikverse.egxanalyzer.model.CallOrder
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.JobOutcome
import com.ikverse.egxanalyzer.model.PortfolioOrder
import com.ikverse.egxanalyzer.model.ScheduleClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

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

    // ------------------------------------------------------------------ the analysis schedules

    /**
     * Four of them, each with its own days, kept whole across a restart. Everything the clock
     * reads is in here, so a field lost in the round trip is a run that happens at the wrong time
     * or reads the wrong chats, unattended, with nobody watching.
     */
    @Test
    fun `the schedules survive a restart, days and all`() {
        assertTrue(repository().analysisSchedules().isEmpty())

        val schedules = (1..4).map { index ->
            AnalysisSchedule(
                id = index.toLong(),
                enabled = index % 2 == 0,
                at = LocalTime.of(6 + index, 30),
                days = setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY),
                channels = listOf(AnalysedChannel(index.toLong(), "Signals $index")),
                contentTypes = setOf(AnalysisContentType.entries.first()),
                armedAt = Instant.ofEpochMilli(1_000L * index),
            )
        }
        repository().saveAnalysisSchedules(schedules)

        assertEquals(schedules, repository().analysisSchedules())
    }

    /** The cap is the storage's too: a longer list cannot arrive from a screen that respects it. */
    @Test
    fun `no more than four are ever written`() {
        repository().saveAnalysisSchedules(
            (1..6).map { AnalysisSchedule(id = it.toLong(), at = LocalTime.of(7, 0)) },
        )

        assertEquals(AnalysisSchedule.MAX, repository().analysisSchedules().size)
    }

    /**
     * What a phone updating from the build that could hold only one schedule is holding. It keeps
     * its time, its aim and its switch, and gains the whole trading week - which is the only week
     * that schedule could ever have kept.
     */
    @Test
    fun `the single schedule an older build wrote is read back as a list of one`() {
        context.getSharedPreferences("egx_android_settings", Context.MODE_PRIVATE)
            .edit()
            .putString(
                "analysis_schedule",
                """{"enabled":true,"at":"07:00","channels":[{"id":7,"name":"Signals"}],""" +
                    """"contentTypes":["${AnalysisContentType.entries.first().name}"],""" +
                    """"armedAt":1000}""",
            )
            .commit()

        val carried = repository().analysisSchedules().single()
        assertTrue(carried.enabled)
        assertEquals(LocalTime.of(7, 0), carried.at)
        assertEquals(ScheduleClock.tradingDays, carried.days)
        assertEquals(listOf(7L), carried.channels.map { it.id })
    }

    /**
     * A schedule the app cannot read is one it also cannot spend money on, so the failure is the
     * safe one - and it must not take the screen that draws it down with it.
     */
    @Test
    fun `a stored list this build cannot parse comes back empty rather than throwing`() {
        context.getSharedPreferences("egx_android_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("analysis_schedules", "not json")
            .commit()

        assertTrue(repository().analysisSchedules().isEmpty())
    }

    /**
     * A run records its outcome from a process with no screen in it, while the list on disk may
     * have been edited since. Writing back the whole list it was holding would undo that edit.
     */
    @Test
    fun `recording one outcome leaves the schedules beside it alone`() {
        repository().saveAnalysisSchedules(
            listOf(
                AnalysisSchedule(id = 1, at = LocalTime.of(7, 0)),
                AnalysisSchedule(id = 2, at = LocalTime.of(12, 0)),
            ),
        )

        repository().recordAnalysisSchedule(
            AnalysisSchedule(
                id = 2,
                at = LocalTime.of(12, 0),
                lastOutcome = JobOutcome.SUCCEEDED,
                lastMessage = "Saved 3 calls",
            ),
        )

        val stored = repository().analysisSchedules()
        assertEquals(JobOutcome.NEVER, stored.first { it.id == 1L }.lastOutcome)
        assertEquals("Saved 3 calls", stored.first { it.id == 2L }.lastMessage)
    }

    /** A schedule deleted while its run was going does not come back as that run finishes. */
    @Test
    fun `an outcome for a schedule that has gone is dropped`() {
        repository().saveAnalysisSchedules(listOf(AnalysisSchedule(id = 1, at = LocalTime.of(7, 0))))

        repository().recordAnalysisSchedule(AnalysisSchedule(id = 9, at = LocalTime.of(9, 0)))

        assertEquals(listOf(1L), repository().analysisSchedules().map { it.id })
    }

    private class NoCredentials : CredentialStore {
        override fun contains(provider: CloudProvider) = false
        override fun save(provider: CloudProvider, credential: CharArray) = Unit
        override fun read(provider: CloudProvider): CharArray? = null
        override fun remove(provider: CloudProvider) = Unit
    }
}
