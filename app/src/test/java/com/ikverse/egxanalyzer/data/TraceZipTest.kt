package com.ikverse.egxanalyzer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * The traces folder as a zip: one folder per run, kept together, and nothing at all when there is
 * nothing to send.
 */
class TraceZipTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun zipEntries(bytes: ByteArray): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entries[it.name] = zip.readBytes().decodeToString() }
        }
        return entries
    }

    @Test
    fun `each run's request and response travel under the run's own folder`() {
        val root = temp.newFolder("traces")
        val run = java.io.File(root, "run-1").apply { mkdirs() }
        java.io.File(run, "consolidation-request.json").writeText("{\"asked\":1}")
        java.io.File(run, "consolidation-response.json").writeText("not json {")

        val out = ByteArrayOutputStream()
        zipTraces(root, out)

        assertEquals(
            mapOf(
                "run-1/consolidation-request.json" to "{\"asked\":1}",
                "run-1/consolidation-response.json" to "not json {",
            ),
            zipEntries(out.toByteArray()),
        )
    }

    @Test
    fun `an empty or missing folder has no trace files`() {
        assertTrue(traceFiles(temp.newFolder("empty")).isEmpty())
        assertTrue(traceFiles(java.io.File(temp.root, "missing")).isEmpty())
    }
}
