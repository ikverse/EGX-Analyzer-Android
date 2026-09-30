package com.ikverse.egxanalyzer.data

import com.ikverse.egxanalyzer.model.PromptVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The prompts a restored install has to be able to show.
 *
 * Every saved report names the prompt version it was judged under. Without these travelling, a
 * reinstalled phone downloads its whole record and can no longer show what produced any of it.
 */
class PromptVersionSyncTest {

    private fun version(
        id: String = "0eb9d2ec",
        sequence: Int = 4,
        schemaVersion: Int? = 4,
    ) = SyncedPromptVersion(
        PromptVersion(
            id = id,
            sequence = sequence,
            text = "Read every card and report the levels as printed.",
            schemaVersion = schemaVersion,
            ruleIds = listOf("legacy:source_drop:hit", "user:stale"),
            reason = "Added \"حقق المستهدف\"",
            device = "Samsung SM-F966B",
            createdAt = 1_700_000_000_000,
        ),
    )

    @Test
    fun `a prompt survives the round trip`() {
        val original = version()

        assertEquals(original, SyncedPromptVersion.fromDocument(original.toDocument()))
    }

    /** A prompt composed before the schema was recorded still has to travel. */
    @Test
    fun `a prompt with no schema version survives too`() {
        val original = version(schemaVersion = null)

        assertEquals(original, SyncedPromptVersion.fromDocument(original.toDocument()))
    }

    @Test
    fun `the file name carries the id`() {
        val name = version().fileName

        assertEquals("prompt-0eb9d2ec.json", name)
        assertEquals("0eb9d2ec", SyncedPromptVersion.promptIdOf(name))
    }

    /**
     * Both sides of "does the channel already have this one" go through the same function.
     *
     * Asking with an id a file name cannot carry would answer no forever, and the same prompt would
     * be uploaded again on every single sync.
     */
    @Test
    fun `an id a file name cannot carry is asked about in the form it is stored under`() {
        val awkward = version(id = "rules/2026 08")

        assertEquals("prompt-rules_2026_08.json", awkward.fileName)
        assertEquals(
            SyncedPromptVersion.promptIdOf(awkward.fileName),
            SyncedPromptVersion.keyFor(awkward.version.id),
        )
    }

    /** A channel holds whatever anyone dropped in it; only this app's prompts are ours to read. */
    @Test
    fun `a file that is not a prompt is ignored rather than guessed at`() {
        assertNull(SyncedPromptVersion.promptIdOf("holiday-photo.jpg"))
        assertNull(SyncedPromptVersion.promptIdOf("0eb9d2ec.json"))
        assertNull(SyncedPromptVersion.fromDocument("not json at all"))
        assertNull(SyncedPromptVersion.fromDocument("""{"text":"no id"}"""))
    }
}
