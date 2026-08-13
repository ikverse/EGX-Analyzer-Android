package com.ikverse.egxanalyzer.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * What a page carries through being torn down and rebuilt - by folding the phone, by turning it, or
 * by the pager dropping a tab two swipes away.
 *
 * Every one of these goes into a `Bundle`, which takes none of the types the screens actually hold,
 * so a saver that does not round-trip means the filter or the open card comes back as its default
 * and looks to the reader exactly like the app forgetting where they were.
 */
class PageStateSaverTest {

    private val scope = SaverScope { true }

    @Test
    fun `a set of channel names survives the round trip`() {
        val filter = setOf("CFI Egypt", "إسأل فني")

        assertEquals(filter, StringSetSaver.roundTrip(filter))
    }

    /**
     * `listSaver` writes nothing at all for an empty list, so a cleared filter is saved as null and
     * comes back as whatever the screen asked for initially. That is harmless here only because
     * every set this saver holds starts empty - the value restored and the value defaulted to are
     * the same set - and it is a trap for anyone who later gives one of them a non-empty default.
     */
    @Test
    fun `a cleared filter saves as nothing, which is the same empty set it started as`() {
        assertNull(saved(StringSetSaver, emptySet<String>()))
        assertNull(saved(LocalDateSetSaver, emptySet<LocalDate>()))
    }

    @Test
    fun `open session cards survive as dates, not as text`() {
        val open = setOf(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 3))

        assertEquals(open, LocalDateSetSaver.roundTrip(open))
    }

    /**
     * A date this build cannot parse is dropped, not thrown on. The rest of the set is still the
     * reader's, and one unreadable entry is not a reason to close every card they had open.
     */
    @Test
    fun `an unparseable date is dropped and the rest of the set survives`() {
        val restored = LocalDateSetSaver.restore(listOf("2026-07-20", "not a date"))

        assertEquals(setOf(LocalDate.of(2026, 7, 20)), restored)
    }

    @Test
    fun `an order survives by name`() {
        assertEquals(RunOrder.TARGET_OLDEST, enumSaver<RunOrder>().roundTrip(RunOrder.TARGET_OLDEST))
    }

    /**
     * Stored by name and not by ordinal, so reordering the entries cannot silently reinterpret one
     * as another - the same rule the stored settings follow. A name this build no longer knows
     * restores as null, which `rememberSaveable` reads as nothing saved and replaces with the
     * default, rather than throwing on the way back to a screen the reader is already looking at.
     */
    @Test
    fun `an order this build no longer knows restores as nothing`() {
        assertNull(enumSaver<RunOrder>().restore("SOME_ORDER_A_LATER_BUILD_ADDED"))
    }

    @Test
    fun `an order is stored under its own name`() {
        assertEquals("RUN_OLDEST", saved(enumSaver<RunOrder>(), RunOrder.RUN_OLDEST))
    }

    private fun <T> Saver<T, Any>.roundTrip(value: T): T? = restore(saved(this, value)!!)

    // `save` takes the scope as its extension receiver and the saver as its dispatch receiver, so
    // the saver is the one that goes in `with` and the scope is written out.
    private fun <T> saved(saver: Saver<T, Any>, value: T): Any? = with(saver) { scope.save(value) }
}
