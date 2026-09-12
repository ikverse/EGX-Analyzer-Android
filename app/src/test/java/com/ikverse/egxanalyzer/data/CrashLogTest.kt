package com.ikverse.egxanalyzer.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDateTime

/**
 * That a crash leaves something behind, and that crashing repeatedly does not fill the phone.
 *
 * Robolectric because this writes a real file into a real `filesDir`, and the trimming is the part
 * worth a test: it runs only on a device that has already crashed several times, which is the
 * device nobody has in front of them.
 */
@RunWith(RobolectricTestRunner::class)
class CrashLogTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val noon: LocalDateTime = LocalDateTime.of(2026, 9, 12, 12, 0, 0)

    @Before
    fun clean() = CrashLog.clear(context)

    @Test
    fun `a phone that has never crashed has nothing to say`() {
        assertNull(CrashLog.latest(context))
        assertEquals(0, CrashLog.count(context))
        assertTrue(CrashLog.read(context).isBlank())
    }

    @Test
    fun `a crash is recorded with its thread and its stack`() {
        CrashLog.record(context, "main", IllegalStateException("the table was not there"), noon)

        val text = CrashLog.read(context)
        assertTrue(text, "thread: main" in text)
        assertTrue(text, "IllegalStateException: the table was not there" in text)
        assertTrue(text, "at com.ikverse.egxanalyzer.data.CrashLogTest" in text)
        assertEquals(1, CrashLog.count(context))
    }

    /**
     * The cause is the half that says why, and a hand-rolled walk of the causes always stops one
     * short of it. This is what says `printStackTrace` is doing that work.
     */
    @Test
    fun `the cause travels with the crash`() {
        val cause = IllegalArgumentException("VLMRA has no legacy symbol")
        CrashLog.record(context, "price-refresh", RuntimeException("refresh failed", cause), noon)

        val text = CrashLog.read(context)
        assertTrue(text, "Caused by" in text)
        assertTrue(text, "VLMRA has no legacy symbol" in text)
    }

    @Test
    fun `the newest crash is the one on top, and the one Settings names`() {
        CrashLog.record(context, "main", IllegalStateException("first"), noon)
        CrashLog.record(context, "main", IllegalStateException("second"), noon.plusHours(1))

        val text = CrashLog.read(context)
        assertTrue(text, text.indexOf("second") < text.indexOf("first"))
        assertEquals(2, CrashLog.count(context))
        val latest = CrashLog.latest(context)
        assertTrue("$latest", latest!!.startsWith("2026-09-12 13:00:00 · v"))
    }

    /**
     * A phone crashing in a loop writes this file on every launch. Twenty is the ceiling, and the
     * ones dropped are the oldest - the newest crash is the one somebody is about to be asked about.
     */
    @Test
    fun `only the newest twenty are kept`() {
        repeat(25) { index ->
            CrashLog.record(
                context,
                "main",
                IllegalStateException("crash number $index"),
                noon.plusMinutes(index.toLong()),
            )
        }

        val text = CrashLog.read(context)
        assertEquals(20, CrashLog.count(context))
        assertTrue(text, "crash number 24" in text)
        assertTrue(text, "crash number 5" in text)
        assertTrue(text, "crash number 4" !in text)
        assertTrue(text, "crash number 0" !in text)
    }

    /**
     * An entry larger than the whole budget is kept whole and alone. Truncating it would leave a
     * stack trace ending mid-frame, which reads as a crash inside the logger rather than a trim.
     */
    @Test
    fun `one enormous crash is kept rather than cut in half`() {
        CrashLog.record(context, "main", IllegalStateException("x".repeat(200_000)), noon)

        assertEquals(1, CrashLog.count(context))
        assertTrue("x".repeat(200_000) in CrashLog.read(context))
    }

    @Test
    fun `forgetting leaves nothing behind`() {
        CrashLog.record(context, "main", IllegalStateException("gone"), noon)
        CrashLog.clear(context)

        assertNull(CrashLog.latest(context))
        assertEquals(0, CrashLog.count(context))
    }

    /**
     * The handler must not become the crash. A throwable whose own `printStackTrace` fails is the
     * shape of thing that turns a diagnosable error into an unrelated one.
     */
    @Test
    fun `a throwable that cannot print itself does not take the process with it`() {
        val hostile = object : RuntimeException("hostile") {
            override fun printStackTrace(writer: java.io.PrintWriter) = error("no")
        }
        // Put back afterwards. This is process-wide state and every test after this one in the
        // same JVM would otherwise be running under a handler this test installed.
        val installed = Thread.getDefaultUncaughtExceptionHandler()
        try {
            Thread.setDefaultUncaughtExceptionHandler(null)
            CrashLog.install(context)
            Thread.getDefaultUncaughtExceptionHandler()!!
                .uncaughtException(Thread.currentThread(), hostile)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(installed)
        }
    }
}
