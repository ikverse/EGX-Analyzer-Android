package com.ikverse.egxanalyzer.data

import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A single-sheet `.xlsx`, written by hand.
 *
 * The format is a zip of XML parts, and the corner of it a report needs - inline strings, a style
 * table, frozen panes and an autofilter - is small enough to write outright. Apache POI is the
 * obvious alternative and costs about 12MB of dex, drags xmlbeans, and needs desugaring, all to
 * produce one sheet. Written this way the whole export is plain Kotlin with no Android in it, which
 * is what lets it be tested without a device.
 *
 * Everything here is deliberately ignorant of what a report is: it takes cells and styles and
 * returns bytes. What a column means lives in `ui/ReportExport.kt`.
 */

/** Where a cell's text sits in its column. Null is Excel's own default, which varies by type. */
internal enum class CellAlign(val attribute: String?) {
    DEFAULT(null),
    START("left"),
    END("right"),
}

/**
 * How one cell is drawn.
 *
 * Deduplicated into Excel's font, fill and number-format tables on the way out, so a style repeated
 * on nine hundred cells is written once and referenced by index - which is also the only way a
 * sheet this wide stays a sensible size.
 *
 * [colour] and [fill] are ARGB, as Excel writes them: `FFB3261E`, alpha first.
 */
internal data class CellStyle(
    val colour: String? = null,
    val bold: Boolean = false,
    val fill: String? = null,
    /** An Excel number format code, e.g. `0.###`. Null leaves the cell General. */
    val format: String? = null,
    val align: CellAlign = CellAlign.DEFAULT,
) {
    companion object {
        /** The style every sheet has whether it uses it or not: Excel's index 0. */
        val Plain = CellStyle()
    }
}

/**
 * One cell.
 *
 * Numbers and dates go in as numbers and dates rather than as their printed form, or the column
 * cannot be sorted, filtered or added up - which is the whole reason for exporting a spreadsheet
 * rather than a table of text. [Blank] is how an absent figure travels: the app draws an em dash,
 * but a dash in a numeric column turns the column to text and files under its own heading in a
 * filter dropdown. Excel's own word for "no value" is an empty cell.
 */
internal sealed interface Cell {
    val style: CellStyle

    data class Text(val value: String, override val style: CellStyle = CellStyle.Plain) : Cell
    data class Number(val value: Double, override val style: CellStyle = CellStyle.Plain) : Cell
    data class Date(val value: LocalDate, override val style: CellStyle = CellStyle.Plain) : Cell
    data class Blank(override val style: CellStyle = CellStyle.Plain) : Cell
}

/** A column's width, in Excel's own unit: roughly one digit of the default font. */
internal data class SheetColumn(val width: Double)

/**
 * The sheet to write.
 *
 * [freezeRows] and [freezeColumns] are counts, not indices: 1 and 1 pins the header row and the
 * leftmost column, which is what keeps a wide table readable while it is scrolled.
 */
internal class Sheet(
    val name: String,
    val columns: List<SheetColumn>,
    /** Row 1 first. Every row is drawn at the length it has; short rows simply end early. */
    val rows: List<List<Cell>>,
    val freezeRows: Int = 0,
    val freezeColumns: Int = 0,
    /** Puts filter dropdowns on row 1, over every column. */
    val autoFilter: Boolean = false,
)

/** The MIME type Android and every mail client know an .xlsx by. */
internal const val XLSX_MIME_TYPE =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** The whole workbook as bytes, ready to be written to a file or handed to a chooser. */
internal fun writeXlsx(sheet: Sheet): ByteArray {
    val styles = StyleTable(sheet.rows)
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
        zip.part("[Content_Types].xml", CONTENT_TYPES)
        zip.part("_rels/.rels", ROOT_RELS)
        zip.part("xl/workbook.xml", workbookXml(sheet))
        zip.part("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
        zip.part("xl/styles.xml", styles.xml())
        zip.part("xl/worksheets/sheet1.xml", sheetXml(sheet, styles))
    }
    return bytes.toByteArray()
}

private fun ZipOutputStream.part(name: String, content: String) {
    putNextEntry(ZipEntry(name))
    write(content.toByteArray(Charsets.UTF_8))
    closeEntry()
}

/**
 * Excel's three style tables, built from the cells that actually use them.
 *
 * Fill indices 0 and 1 are reserved by the format for "none" and "gray125" whether a sheet wants
 * them or not; putting a real fill at either index silently shifts every other fill by one, which
 * shows up as a sheet coloured one column out.
 */
private class StyleTable(rows: List<List<Cell>>) {
    private val styles: List<CellStyle> =
        (listOf(CellStyle.Plain) + rows.flatten().map(Cell::style)).distinct()
    private val fonts: List<Pair<String?, Boolean>> =
        (listOf(null to false) + styles.map { it.colour to it.bold }).distinct()
    private val fills: List<String> = styles.mapNotNull(CellStyle::fill).distinct()
    private val formats: List<String> = styles.mapNotNull(CellStyle::format).distinct()

    /** The `s` attribute for a cell, i.e. its index into `cellXfs`. */
    fun indexOf(style: CellStyle): Int = styles.indexOf(style).coerceAtLeast(0)

    fun xml(): String = buildString {
        append(XML_DECLARATION)
        append("<styleSheet xmlns=\"$SPREADSHEET_NS\">")
        if (formats.isNotEmpty()) {
            append("<numFmts count=\"${formats.size}\">")
            formats.forEachIndexed { index, code ->
                append("<numFmt numFmtId=\"${FIRST_CUSTOM_FORMAT + index}\" formatCode=\"${escape(code)}\"/>")
            }
            append("</numFmts>")
        }
        append("<fonts count=\"${fonts.size}\">")
        fonts.forEach { (colour, bold) ->
            append("<font><sz val=\"11\"/>")
            if (bold) append("<b/>")
            if (colour != null) append("<color rgb=\"$colour\"/>")
            append("<name val=\"Calibri\"/><family val=\"2\"/></font>")
        }
        append("</fonts>")
        append("<fills count=\"${fills.size + RESERVED_FILLS}\">")
        append("<fill><patternFill patternType=\"none\"/></fill>")
        append("<fill><patternFill patternType=\"gray125\"/></fill>")
        fills.forEach { colour ->
            append("<fill><patternFill patternType=\"solid\">")
            append("<fgColor rgb=\"$colour\"/><bgColor indexed=\"64\"/>")
            append("</patternFill></fill>")
        }
        append("</fills>")
        append("<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>")
        append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>")
        append("<cellXfs count=\"${styles.size}\">")
        styles.forEach { style ->
            val font = fonts.indexOf(style.colour to style.bold)
            val fill = style.fill?.let { fills.indexOf(it) + RESERVED_FILLS } ?: 0
            val format = style.format?.let { FIRST_CUSTOM_FORMAT + formats.indexOf(it) } ?: 0
            append("<xf numFmtId=\"$format\" fontId=\"$font\" fillId=\"$fill\" borderId=\"0\" xfId=\"0\"")
            if (format != 0) append(" applyNumberFormat=\"1\"")
            if (font != 0) append(" applyFont=\"1\"")
            if (fill != 0) append(" applyFill=\"1\"")
            append(" applyAlignment=\"1\"><alignment vertical=\"center\"")
            style.align.attribute?.let { append(" horizontal=\"$it\"") }
            append("/></xf>")
        }
        append("</cellXfs>")
        // The workbook's default style. Excel infers one when it is missing, but a stricter reader
        // stops and says the workbook has no default style, which is a warning on a file that is
        // otherwise fine - and the fix is one element.
        append("<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>")
        append("</styleSheet>")
    }
}

private fun workbookXml(sheet: Sheet): String = buildString {
    val name = sheetName(sheet.name)
    append(XML_DECLARATION)
    append("<workbook xmlns=\"$SPREADSHEET_NS\" xmlns:r=\"$RELATIONSHIP_NS\">")
    append("<sheets><sheet name=\"${escape(name)}\" sheetId=\"1\" r:id=\"rId1\"/></sheets>")
    // Excel writes this alongside every autofilter and quietly drops the dropdowns on some files
    // that lack it, so it is written rather than relied on being inferred.
    if (sheet.autoFilter && sheet.rows.isNotEmpty()) {
        val reference = "'${escape(name.replace("'", "''"))}'!${absoluteRange(sheet)}"
        append("<definedNames>")
        append("<definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"0\" hidden=\"1\">$reference</definedName>")
        append("</definedNames>")
    }
    append("</workbook>")
}

private fun sheetXml(sheet: Sheet, styles: StyleTable): String = buildString {
    append(XML_DECLARATION)
    append("<worksheet xmlns=\"$SPREADSHEET_NS\">")
    append("<dimension ref=\"${range(sheet)}\"/>")
    append("<sheetViews><sheetView tabSelected=\"1\" workbookViewId=\"0\">")
    if (sheet.freezeRows > 0 || sheet.freezeColumns > 0) {
        val corner = "${columnName(sheet.freezeColumns)}${sheet.freezeRows + 1}"
        append("<pane")
        if (sheet.freezeColumns > 0) append(" xSplit=\"${sheet.freezeColumns}\"")
        if (sheet.freezeRows > 0) append(" ySplit=\"${sheet.freezeRows}\"")
        append(" topLeftCell=\"$corner\" activePane=\"bottomRight\" state=\"frozen\"/>")
        append("<selection pane=\"bottomRight\" activeCell=\"$corner\" sqref=\"$corner\"/>")
    }
    append("</sheetView></sheetViews>")
    append("<sheetFormatPr defaultRowHeight=\"15\"/>")
    if (sheet.columns.isNotEmpty()) {
        append("<cols>")
        sheet.columns.forEachIndexed { index, column ->
            append("<col min=\"${index + 1}\" max=\"${index + 1}\" width=\"${column.width}\" customWidth=\"1\"/>")
        }
        append("</cols>")
    }
    append("<sheetData>")
    sheet.rows.forEachIndexed { rowIndex, cells ->
        append("<row r=\"${rowIndex + 1}\">")
        cells.forEachIndexed { columnIndex, cell ->
            appendCell(cell, columnName(columnIndex) + (rowIndex + 1), styles.indexOf(cell.style))
        }
        append("</row>")
    }
    append("</sheetData>")
    // After sheetData, which is where the schema puts it; before it, Excel calls the file corrupt.
    if (sheet.autoFilter && sheet.rows.isNotEmpty()) append("<autoFilter ref=\"${range(sheet)}\"/>")
    append("</worksheet>")
}

private fun StringBuilder.appendCell(cell: Cell, reference: String, style: Int) {
    // A blank with nothing to say is left out entirely: a sheet of a thousand rows carries a lot of
    // absent figures, and an empty cell and a missing one read the same in Excel. One carrying a
    // fill has to be written, or the row's banding stops at its first gap.
    if (cell is Cell.Blank && style == 0) return
    val attributes = "r=\"$reference\"" + if (style != 0) " s=\"$style\"" else ""
    when (cell) {
        is Cell.Blank -> append("<c $attributes/>")
        is Cell.Number -> append("<c $attributes><v>${number(cell.value)}</v></c>")
        is Cell.Date -> append("<c $attributes><v>${excelSerial(cell.value)}</v></c>")
        is Cell.Text -> {
            val text = clean(cell.value)
            if (text.isEmpty()) {
                append("<c $attributes/>")
                return
            }
            val preserve = if (text != text.trim()) " xml:space=\"preserve\"" else ""
            append("<c $attributes t=\"inlineStr\"><is><t$preserve>${escape(text)}</t></is></c>")
        }
    }
}

/** Plain decimal, never scientific notation, which Excel reads as text. */
private fun number(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/**
 * Excel counts days from 1899-12-30.
 *
 * Not 1900-01-01, which is the date the format claims: Lotus 1-2-3 treated 1900 as a leap year,
 * Excel kept the bug for compatibility, and the epoch is shifted two days to absorb it.
 */
private fun excelSerial(date: LocalDate): Long = date.toEpochDay() + 25_569

/** `A1:R42` over everything the sheet holds, which both the dimension and the filter want. */
private fun range(sheet: Sheet): String {
    if (sheet.rows.isEmpty()) return "A1"
    val width = sheet.rows.maxOf { it.size }.coerceAtLeast(1)
    return "A1:${columnName(width - 1)}${sheet.rows.size}"
}

/** The same range with absolute markers, which is the only form a defined name accepts. */
private fun absoluteRange(sheet: Sheet): String {
    val width = sheet.rows.maxOf { it.size }.coerceAtLeast(1)
    return "\$A\$1:\$${columnName(width - 1)}\$${sheet.rows.size}"
}

/** 0 is A, 25 is Z, 26 is AA. */
internal fun columnName(index: Int): String {
    var remaining = index
    val name = StringBuilder()
    while (true) {
        name.append('A' + remaining % 26)
        remaining = remaining / 26 - 1
        if (remaining < 0) break
    }
    return name.reverse().toString()
}

/** Excel refuses a sheet name over 31 characters or holding any of `[]:*?/\`. */
private fun sheetName(requested: String): String {
    val cleaned = requested.filterNot { it in "[]:*?/\\" }.trim().take(31)
    return cleaned.ifBlank { "Sheet1" }
}

/**
 * Text an XML parser will accept.
 *
 * Notes and channel names arrive from Telegram, so they hold whatever someone typed - and a control
 * character is illegal in XML 1.0 even escaped, which makes the whole file unopenable rather than
 * one cell wrong.
 */
private fun clean(value: String): String =
    value.filter { it >= ' ' || it == '\t' || it == '\n' || it == '\r' }

private fun escape(value: String): String = buildString(value.length) {
    value.forEach { character ->
        when (character) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(character)
        }
    }
}

private const val XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
private const val SPREADSHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
private const val RELATIONSHIP_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

/** Ids below 164 are Excel's own built-in formats and cannot be redefined. */
private const val FIRST_CUSTOM_FORMAT = 164

/** "none" and "gray125", which the format requires at indices 0 and 1. */
private const val RESERVED_FILLS = 2

private val CONTENT_TYPES = """
    $XML_DECLARATION<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
    <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
    </Types>
""".trimIndent().replace("\n", "")

private val ROOT_RELS = """
    $XML_DECLARATION<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="$RELATIONSHIP_NS/officeDocument" Target="xl/workbook.xml"/>
    </Relationships>
""".trimIndent().replace("\n", "")

private val WORKBOOK_RELS = """
    $XML_DECLARATION<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="$RELATIONSHIP_NS/worksheet" Target="worksheets/sheet1.xml"/>
    <Relationship Id="rId2" Type="$RELATIONSHIP_NS/styles" Target="styles.xml"/>
    </Relationships>
""".trimIndent().replace("\n", "")
