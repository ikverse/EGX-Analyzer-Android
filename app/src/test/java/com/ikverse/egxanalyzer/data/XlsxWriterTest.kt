package com.ikverse.egxanalyzer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The .xlsx we write, read back as a zip.
 *
 * A spreadsheet that Excel refuses says so with one dialog and no reason, so the checks here are
 * the ones that produce that dialog: a missing part, a part the content types never declared, XML
 * that does not parse, or a number written as text.
 */
class XlsxWriterTest {

    private val bold = CellStyle(colour = "FFB3261E", bold = true, format = "0.###", align = CellAlign.END)

    private fun sheetOf(rows: List<List<Cell>>, autoFilter: Boolean = true) = Sheet(
        name = "EGX 2026-08-11",
        columns = List(rows.firstOrNull()?.size ?: 0) { SheetColumn(12.0) },
        rows = rows,
        freezeRows = 1,
        freezeColumns = 1,
        autoFilter = autoFilter,
    )

    private fun parts(bytes: ByteArray): Map<String, String> {
        val parts = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return parts
    }

    /** Throws if the part is not well-formed, which is the only thing worth asserting about it. */
    private fun parse(xml: String) {
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `every part a workbook needs is present and parses`() {
        val parts = parts(writeXlsx(sheetOf(listOf(listOf(Cell.Text("Stock"))))))

        listOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "xl/workbook.xml",
            "xl/_rels/workbook.xml.rels",
            "xl/styles.xml",
            "xl/worksheets/sheet1.xml",
        ).forEach { name ->
            assertNotNull("missing $name", parts[name])
            parse(parts.getValue(name))
        }
    }

    @Test
    fun `every part is declared in the content types`() {
        val parts = parts(writeXlsx(sheetOf(listOf(listOf(Cell.Text("Stock"))))))
        val types = parts.getValue("[Content_Types].xml")

        parts.keys
            .filterNot { it == "[Content_Types].xml" || it.endsWith(".rels") }
            .forEach { assertTrue("$it is not declared", types.contains("PartName=\"/$it\"")) }
    }

    @Test
    fun `a price is a number cell, not text`() {
        val sheet = sheetOf(listOf(listOf(Cell.Number(1.035, bold))))

        val xml = parts(writeXlsx(sheet)).getValue("xl/worksheets/sheet1.xml")

        assertTrue(xml.contains("<v>1.035</v>"))
        // t="inlineStr" is what turns a cell to text, and a text cell can be neither sorted nor
        // added up - which is the whole reason for exporting a spreadsheet.
        assertTrue(xml.contains("<c r=\"A1\" s=\"1\"><v>1.035</v></c>"))
    }

    @Test
    fun `a date is written on Excel's own epoch`() {
        val sheet = sheetOf(listOf(listOf(Cell.Date(LocalDate.of(1970, 1, 1)))))

        val xml = parts(writeXlsx(sheet)).getValue("xl/worksheets/sheet1.xml")

        // 25569, not 25571: Excel kept Lotus's belief that 1900 was a leap year, and its epoch is
        // shifted two days to absorb it.
        assertTrue(xml.contains("<v>25569</v>"))
    }

    @Test
    fun `Arabic survives as itself`() {
        val notes = "مستهدف أول 1.35"
        val sheet = sheetOf(listOf(listOf(Cell.Text(notes))))

        val xml = parts(writeXlsx(sheet)).getValue("xl/worksheets/sheet1.xml")

        assertTrue(xml.contains(notes))
    }

    @Test
    fun `a channel name holding XML is escaped rather than breaking the file`() {
        val sheet = sheetOf(listOf(listOf(Cell.Text("""Fund & Co <"trading"> 'signals'"""))))

        val xml = parts(writeXlsx(sheet)).getValue("xl/worksheets/sheet1.xml")

        parse(xml)
        assertTrue(xml.contains("Fund &amp; Co &lt;&quot;trading&quot;&gt; &apos;signals&apos;"))
    }

    @Test
    fun `a control character is dropped rather than written into illegal XML`() {
        val sheet = sheetOf(listOf(listOf(Cell.Text("COMI\u0007 buy\u0000"))))

        val xml = parts(writeXlsx(sheet)).getValue("xl/worksheets/sheet1.xml")

        // Illegal in XML 1.0 even escaped, so it would cost the whole file rather than one cell.
        parse(xml)
        assertTrue(xml.contains("COMI buy"))
        assertTrue(xml.none { it == '\u0007' || it == '\u0000' })
    }

    @Test
    fun `the filter spans every row the sheet holds`() {
        val rows = List(9) { row -> List(3) { Cell.Text("r$row") } }

        val bytes = writeXlsx(sheetOf(rows))
        val sheetXml = parts(bytes).getValue("xl/worksheets/sheet1.xml")
        val workbook = parts(bytes).getValue("xl/workbook.xml")

        assertTrue(sheetXml.contains("<autoFilter ref=\"A1:C9\"/>"))
        assertTrue(workbook.contains("'EGX 2026-08-11'!\$A\$1:\$C\$9"))
    }

    @Test
    fun `the header row and the first column are frozen`() {
        val xml = parts(writeXlsx(sheetOf(listOf(listOf(Cell.Text("Stock"))))))
            .getValue("xl/worksheets/sheet1.xml")

        assertTrue(xml.contains("xSplit=\"1\""))
        assertTrue(xml.contains("ySplit=\"1\""))
        assertTrue(xml.contains("topLeftCell=\"B2\""))
    }

    @Test
    fun `the reserved fills keep their places`() {
        val filled = CellStyle(fill = "FFE5EAF0")
        val xml = parts(writeXlsx(sheetOf(listOf(listOf(Cell.Text("Stock", filled))))))
            .getValue("xl/styles.xml")

        // A real fill at index 0 or 1 shifts every other fill by one, which draws the sheet a
        // column out rather than failing.
        assertTrue(xml.contains("<fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"))
        assertTrue(xml.contains("fillId=\"2\""))
    }

    @Test
    fun `the workbook declares a default style`() {
        val xml = parts(writeXlsx(sheetOf(listOf(listOf(Cell.Text("Stock"))))))
            .getValue("xl/styles.xml")

        // Excel infers one when it is missing; a stricter reader stops and reports a workbook with
        // no default style, on a file that is otherwise perfectly good.
        assertTrue(xml.contains("<cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/>"))
    }

    @Test
    fun `one style used many times is written once`() {
        val rows = List(50) { listOf(Cell.Number(1.0, bold), Cell.Number(2.0, bold)) }

        val xml = parts(writeXlsx(sheetOf(rows))).getValue("xl/styles.xml")

        // The default plus the one in use. Without the deduplication a wide report writes a style
        // table larger than its data.
        assertTrue(xml.contains("<cellXfs count=\"2\">"))
    }

    @Test
    fun `an empty cell with nothing to say is left out`() {
        val xml = parts(writeXlsx(sheetOf(listOf(listOf(Cell.Text("A"), Cell.Blank(), Cell.Text("C"))))))
            .getValue("xl/worksheets/sheet1.xml")

        assertTrue(xml.contains("r=\"A1\""))
        assertTrue(xml.contains("r=\"C1\""))
        assertTrue("the blank was written", !xml.contains("r=\"B1\""))
    }

    @Test
    fun `an empty cell carrying a band is written, so the band does not stop at it`() {
        val banded = Cell.Blank(CellStyle(fill = "FFF1F4F8"))
        val xml = parts(writeXlsx(sheetOf(listOf(listOf(Cell.Text("A"), banded)))))
            .getValue("xl/worksheets/sheet1.xml")

        assertTrue(xml.contains("r=\"B1\""))
    }

    @Test
    fun `column letters carry past Z`() {
        assertEquals("A", columnName(0))
        assertEquals("R", columnName(17))
        assertEquals("Z", columnName(25))
        assertEquals("AA", columnName(26))
        assertEquals("AB", columnName(27))
        assertEquals("BA", columnName(52))
    }

    @Test
    fun `a sheet name Excel would refuse is cleaned rather than passed on`() {
        val sheet = Sheet(
            name = "EGX 2026/08/11 [target]: the whole of a very long session name",
            columns = listOf(SheetColumn(12.0)),
            rows = listOf(listOf(Cell.Text("Stock"))),
        )

        val workbook = parts(writeXlsx(sheet)).getValue("xl/workbook.xml")
        val name = Regex("<sheet name=\"([^\"]*)\"").find(workbook)!!.groupValues[1]

        assertTrue(name.length <= 31)
        assertTrue(name.none { it in "[]:*?/\\" })
    }
}
