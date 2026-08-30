package com.ikverse.egxanalyzer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every explanation the app hides is still an explanation, and has to survive being hidden.
 *
 * The screens used to say all of this out loud, so a paragraph that had gone missing was missing
 * from the page and somebody would see it. Behind a question mark nobody sees it: the icon draws,
 * the sheet opens, and it is empty - which looks like a feature nobody finished rather than a word
 * that got deleted. So the notes are checked at the source, where they are written.
 *
 * Read off the files rather than off the composables, because almost every note in the app is built
 * inside one and a unit test cannot compose. That makes this a lint rather than a test of
 * behaviour, and it is worth having as one: the failure it catches is silent everywhere else.
 */
class InfoNoteTest {
    private val uiSources: List<File>
        get() = File("src/main/java/com/ikverse/egxanalyzer/ui")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    @Test
    fun `the ui source is where it is expected to be`() {
        // Guards the rest of this class: a walk over a directory that has moved finds no notes and
        // passes every assertion below by having nothing to check.
        assertTrue("no ui sources found - has the package moved?", uiSources.size > 20)
    }

    @Test
    fun `every note carries a title and at least one paragraph`() {
        val empty = mutableListOf<String>()
        uiSources.forEach { file ->
            file.readText().infoNoteArguments().forEach { arguments ->
                val strings = arguments.stringLiterals()
                // Two, not one: `infoNote` takes the title first and the prose after it, so a call
                // holding a single literal is a heading with nothing behind it.
                if (strings.size < 2 || strings.any(String::isBlank)) {
                    empty += "${file.name}: ${strings.joinToString(" | ").take(80)}"
                }
            }
        }
        assertEquals("notes with a blank title or no paragraph", emptyList<String>(), empty)
    }

    @Test
    fun `the notes exported for another file's heading are intact`() {
        listOf(WordingFlowNote, GeneratedPromptNote).forEach { note ->
            assertTrue(note.title, note.title.isNotBlank())
            assertTrue(note.title, note.paragraphs.isNotEmpty())
            assertTrue(note.title, note.paragraphs.none(String::isBlank))
        }
    }

    /**
     * The argument list of every `infoNote(` call in one file.
     *
     * Balanced on parentheses and aware of string literals, so a note whose prose contains a
     * bracket is read whole rather than cut off at it.
     */
    private fun String.infoNoteArguments(): List<String> {
        val calls = mutableListOf<String>()
        var at = indexOf(CALL)
        while (at >= 0) {
            // The declaration in InfoSheet.kt matches this too, and its "arguments" are a parameter
            // list holding no prose at all - which every check below would then report as an empty
            // note.
            if (at >= DECLARATION.length && regionMatches(at - DECLARATION.length, DECLARATION, 0, DECLARATION.length)) {
                at = indexOf(CALL, at + CALL.length)
                continue
            }
            val open = at + CALL.length
            var depth = 1
            var i = open
            var inString = false
            while (i < length && depth > 0) {
                val c = this[i]
                when {
                    inString && c == '\\' -> i++
                    c == '"' -> inString = !inString
                    inString -> Unit
                    c == '(' -> depth++
                    c == ')' -> depth--
                }
                i++
            }
            if (depth == 0) calls += substring(open, i - 1)
            at = indexOf(CALL, at + CALL.length)
        }
        return calls
    }

    /** Every double-quoted literal in an argument list, escapes honoured. */
    private fun String.stringLiterals(): List<String> {
        val found = mutableListOf<String>()
        var i = 0
        while (i < length) {
            if (this[i] != '"') {
                i++
                continue
            }
            val start = ++i
            while (i < length && this[i] != '"') {
                if (this[i] == '\\') i++
                i++
            }
            if (i >= length) break
            found += substring(start, i)
            i++
        }
        return found
    }

    private companion object {
        const val CALL = "infoNote("
        const val DECLARATION = "fun "
    }
}
