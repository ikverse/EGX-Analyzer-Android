package com.ikverse.egxanalyzer.data

import android.content.Context
import android.os.Build
import com.ikverse.egxanalyzer.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * What the app was doing when it died, kept until somebody asks.
 *
 * Nothing here logged anything before this existed - not a `Log.e`, not a handler - so a crash left
 * the process and took the only account of itself with it. On this machine that costs a `logcat`;
 * on somebody else's phone it costs the whole report, because "it closed itself" is all they can
 * say and all that can be asked of them. The record has to be on the device before it can be read
 * off one, and this is the file that puts it there.
 *
 * **A plain file, never the database.** The database is a thing that can itself be the reason the
 * app is dying, SQLite in a dying process is the last place to ask for a write, and the record is
 * synced and backed up - a crash is a fact about one phone, and shipping it to every other device
 * is the opposite of what this is for. It sits in `filesDir`, which nothing sweeps: the backup
 * takes a named database and a settings document, so this stays out of one by construction rather
 * than by a filter somebody has to remember.
 *
 * **The handler does as little as it can.** One read, one write, no coroutines, no `AppState`, and
 * every part of it inside `runCatching` - a crash logger that throws replaces the exception the
 * user actually hit with its own, which is worse than having no logger. Then it hands the throwable
 * to whatever handler was already installed, so Android still shows its dialog and still kills the
 * process. Swallowing it would leave a dead app on screen looking alive.
 */
internal object CrashLog {

    private const val FILE_NAME = "crashes.txt"

    /**
     * Newest first, and the oldest fall off the end.
     *
     * Both a size and a count, because either alone has a hole: one enormous stack trace would use
     * the whole budget on its own, and twenty small ones would be twenty entries nobody reads. A
     * phone that crashes in a loop must not fill its own storage saying so.
     */
    private const val MAX_BYTES = 64 * 1024
    private const val MAX_ENTRIES = 20

    /** The start of an entry, at the start of a line. Splitting on it is how entries are counted. */
    private const val HEADER = "=== crash "

    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * Starts recording, and keeps whatever was recording already.
     *
     * Chained rather than replaced. The handler in place at this point is Android's own, which is
     * what shows "app has stopped" and ends the process; a handler that does not call it leaves the
     * app frozen on its last frame with no way out but the task switcher.
     */
    fun install(context: Context) {
        val application = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(application, thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Writes one crash down.
     *
     * Separate from [install] so a test can call it without a dying process, and internal for the
     * same reason.
     */
    fun record(
        context: Context,
        thread: String,
        error: Throwable,
        at: LocalDateTime = LocalDateTime.now(),
    ) {
        val entry = buildString {
            append(HEADER).append(STAMP.format(at))
            append(" · v").append(BuildConfig.VERSION_NAME)
            append(" (").append(BuildConfig.VERSION_CODE).append(") ===\n")
            append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            append(" · Android ").append(Build.VERSION.RELEASE)
            append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("thread: ").append(thread).append('\n')
            // printStackTrace into a writer rather than by hand: it is what walks the causes and
            // the suppressed exceptions, and a hand-rolled version of it always stops one cause
            // short of the one that mattered.
            append(StringWriter().also { text -> PrintWriter(text).use(error::printStackTrace) })
        }
        val file = file(context)
        val kept = trimmed(entry + "\n" + runCatching { file.readText() }.getOrDefault(""))
        file.writeText(kept)
    }

    /** Everything on record, newest first. Empty when the app has never died here. */
    fun read(context: Context): String =
        runCatching { file(context).readText() }.getOrDefault("")

    /**
     * The headline of the newest crash, for the line in Settings that says there is one.
     *
     * Null where nothing has been recorded. The header is read back rather than a date being parsed
     * out of it: it was written to be read by a person, and the line on screen is that same text.
     */
    fun latest(context: Context): String? = read(context)
        .lineSequence()
        .firstOrNull { it.startsWith(HEADER) }
        ?.removePrefix(HEADER)
        ?.removeSuffix(" ===")

    /** How many crashes are on record, which is at most [MAX_ENTRIES]. */
    fun count(context: Context): Int =
        read(context).lineSequence().count { it.startsWith(HEADER) }

    /** Forgets them, for the reader who has handed the file over and wants the line to go away. */
    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /**
     * Drops the oldest entries until the text is within both budgets.
     *
     * Entry by entry, never by character: cutting a file of stack traces at a byte offset leaves a
     * half a trace at the bottom, which reads as a crash that happened rather than as a line that
     * was trimmed. A single entry larger than the whole budget is kept whole and alone - it is the
     * one crash there is, and a truncated copy of it answers nothing.
     */
    private fun trimmed(text: String): String {
        val entries = text.split("\n" + HEADER)
            .filter(String::isNotBlank)
            .mapIndexed { index, entry -> if (index == 0) entry else HEADER + entry }
        val kept = mutableListOf<String>()
        var bytes = 0
        for (entry in entries.take(MAX_ENTRIES)) {
            val size = entry.toByteArray().size
            if (kept.isNotEmpty() && bytes + size > MAX_BYTES) break
            kept += entry.trimEnd('\n')
            bytes += size
        }
        // A blank line between entries: a wall of stack traces with nothing between them is one
        // stack trace as far as the eye reading it is concerned. The split above tolerates it.
        return kept.joinToString("\n\n")
    }
}
