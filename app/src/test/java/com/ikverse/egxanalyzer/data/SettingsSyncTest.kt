package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.AnalysisLanguage
import com.ikverse.egxanalyzer.model.AppPreferences
import com.ikverse.egxanalyzer.model.CloudProvider
import com.ikverse.egxanalyzer.model.PortfolioOrder
import com.ikverse.egxanalyzer.model.PromptSnapshot
import com.ikverse.egxanalyzer.model.ThemeMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a reinstalled phone gets back.
 *
 * Reports, rules and trades already travel; these are the settings they are read under. The case
 * every test here is really about is the install with nothing on it, which has to end up
 * configured the way its owner left the last one - and must never hand its emptiness to the device
 * that still knows better.
 */
class SettingsSyncTest {

    private fun snapshot(
        at: Long = 1_000,
        by: String = "phone",
        window: Int = 12,
        theme: ThemeMode = ThemeMode.DARK,
        unknown: String = "{}",
    ) = SettingsSnapshot(
        preferences = AppPreferences(
            themeMode = theme,
            analysisLanguage = AnalysisLanguage.ARABIC,
            responseTimeoutSeconds = 420,
            customSystemPrompt = "Read the levels as printed.",
            correctionRetries = 2,
            catalogEnrichmentEnabled = false,
            scoringWindowSessions = window,
            overdueRemindersEnabled = false,
            updateChecksEnabled = false,
            portfolioOrder = PortfolioOrder.OLDEST,
        ),
        provider = CloudProvider.OPENROUTER,
        providers = listOf(
            ProviderSettings(
                provider = CloudProvider.OPENROUTER,
                endpoint = "https://openrouter.ai/api/v1",
                model = "some/model",
                models = listOf("some/model", "other/model"),
            ),
        ),
        useDefaultPromptOnly = true,
        promptHistory = listOf(PromptSnapshot("system", "in", "out", 42)),
        updatedAt = at,
        updatedBy = by,
        unknown = unknown,
    )

    @Test
    fun `settings survive the round trip`() {
        val original = snapshot()

        assertEquals(original, SettingsSnapshot.fromDocument(original.toDocument()))
    }

    @Test
    fun `the file name carries the device and the moment`() {
        val name = snapshot(at = 42, by = "Samsung SM-F966B").fileName

        // The hyphen goes too: it is what separates the device from the moment in the name, so a
        // device whose own name contains one would otherwise split in the wrong place.
        assertEquals("settings-Samsung_SM_F966B-42.json", name)
        assertEquals(SettingsStamp(42, "Samsung_SM_F966B"), SettingsSnapshot.stampOf(name))
    }

    /** A channel holds whatever anyone dropped in it; only this app's settings are ours to read. */
    @Test
    fun `a file that is not settings is ignored rather than guessed at`() {
        assertNull(SettingsSnapshot.stampOf("holiday-photo.jpg"))
        assertNull(SettingsSnapshot.stampOf("rule-legacy-42.json"))
        assertNull(SettingsSnapshot.stampOf("settings-phone-never.json"))
        assertNull(SettingsSnapshot.fromDocument("not json at all"))
        assertNull(SettingsSnapshot.fromDocument("""{"themeMode":"DARK"}"""))
    }

    @Test
    fun `the settings saved last are the ones that count`() {
        val merged = mergeSettings(
            listOf(snapshot(at = 1_000, window = 5), snapshot(at = 2_000, window = 9)),
        )

        assertEquals(9, merged?.preferences?.scoringWindowSessions)
    }

    /** Two devices saving in the same millisecond still have to reach the same answer. */
    @Test
    fun `a tie is broken the same way on every device`() {
        val both = listOf(snapshot(by = "phone", window = 5), snapshot(by = "tablet", window = 9))

        assertEquals(mergeSettings(both), mergeSettings(both.reversed()))
        assertEquals(9, mergeSettings(both)?.preferences?.scoringWindowSessions)
    }

    /**
     * The reinstall, which is the reason this exists.
     *
     * A device that has never saved a setting has a stamp of zero. It has to take everything the
     * channel holds, and it must never upload its own emptiness over settings the other phone is
     * still using.
     */
    @Test
    fun `an install with nothing on it defends nothing`() {
        val fresh = snapshot(at = 0, by = "")
        val theirs = snapshot(at = 5_000, by = "tablet")

        assertTrue(theirs.stamp > fresh.stamp)
        assertFalse(settingsWorthUploading(fresh, theirs))
        assertFalse(settingsWorthUploading(fresh, null))
    }

    @Test
    fun `only what this device knows better is uploaded`() {
        assertTrue(settingsWorthUploading(snapshot(at = 3_000), snapshot(at = 2_000)))
        assertFalse(settingsWorthUploading(snapshot(at = 2_000), snapshot(at = 3_000)))
        assertTrue(settingsWorthUploading(snapshot(at = 3_000), null))
    }

    /**
     * An older app must not quietly strip what a newer one wrote.
     *
     * Reading settings it only half understands and saving them back is how a setting added in an
     * update disappears from every device that has not updated yet.
     */
    @Test
    fun `a field this version does not know is carried through untouched`() {
        val document = JSONObject(snapshot().toDocument())
            .put("somethingNewer", "keep me")
            .toString()

        val parsed = requireNotNull(SettingsSnapshot.fromDocument(document))

        assertEquals("keep me", JSONObject(parsed.toDocument()).getString("somethingNewer"))
    }

    /**
     * One unreadable setting costs that setting and nothing else.
     *
     * Refusing the whole document over a renamed option would leave the reinstalled phone with no
     * settings at all, which is precisely the failure this is here to prevent.
     */
    @Test
    fun `an option this build does not recognise falls back on its own`() {
        val document = JSONObject(snapshot(window = 7).toDocument())
            .put("themeMode", "NEON")
            .put("portfolioOrder", "BY_TICKER_DESCENDING")
            .toString()

        val parsed = requireNotNull(SettingsSnapshot.fromDocument(document))

        assertEquals(ThemeMode.SYSTEM, parsed.preferences.themeMode)
        assertEquals(PortfolioOrder.URGENT, parsed.preferences.portfolioOrder)
        // Everything readable is still read.
        assertEquals(7, parsed.preferences.scoringWindowSessions)
        assertEquals(AnalysisLanguage.ARABIC, parsed.preferences.analysisLanguage)
    }

    /**
     * The credential stays on the device that holds it.
     *
     * Syncing it would put a live cloud key in a chat to save typing one field once after a
     * reinstall. The connection travels; what pays for it does not.
     */
    @Test
    fun `no credential travels with the settings`() {
        val document = snapshot().toDocument()

        assertFalse(document.contains("credential"))
        assertFalse(document.contains("apiKey"))
        // Nor the day this device last fetched prices: carried to an install with no prices at all,
        // it would claim they were fetched today and leave the phone unpriced until tomorrow.
        assertFalse(document.contains("last_price_refresh"))
    }
}
