package com.ikverse.egxanalyzer.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 5.0 rule, held by the build: no file in `ui/` - every screen, every sheet, every helper they
 * draw - may call a Material component or one of the pre-5.0 controls. Only the kit itself, in
 * `ui/kit/`, builds on Material's primitives, and it is the one place allowed to.
 *
 * Read off the source rather than off a composition, on purpose: a composition only proves what
 * one state of one screen drew, where the source is every branch at once - the empty state, the
 * error, the sheet nobody opened in a test. Since Stage 8 the list is every file in the folder
 * rather than a list of names, so a new file is held to the rule the day it is added.
 */
class NoOldElementsTest {

    private val ui = File("src/main/java/com/ikverse/egxanalyzer/ui")

    /** Every Kotlin file in `ui/` and `ui/theme/`, which is every tab, sheet and shared helper. */
    private val Rebuilt: List<String> =
        (ui.listFiles().orEmpty().toList() + File(ui, "theme").listFiles().orEmpty().toList())
            .filter { it.isFile && it.name.endsWith(".kt") }
            .map { it.relativeTo(ui).path }
            .sorted()

    /** Material's own components and the app's pre-5.0 controls, each as a call. */
    private val Old = listOf(
        "AlertDialog", "ModalBottomSheet", "DatePickerDialog", "DatePicker", "TextButton", "OutlinedButton",
        "IconButton", "Button", "Switch", "Checkbox", "FilterChip", "AssistChip", "Card", "Surface",
        "DropdownMenu", "DropdownMenuItem", "AppMenu", "AppMenuItem", "MoreButton", "ActionPill",
        "DisclosureButton", "SettingsButton", "HorizontalDivider", "CircularProgressIndicator",
        "LinearProgressIndicator", "Slider", "OutlinedTextField", "TextField", "RadioButton",
        "StatStrip", "ShareBar", "FloatingSurface", "StatTile", "PillLabel", "OutlinePill", "FilledPill", "StatusPill", "ToneTag", "pressCorner", "rememberSweep", "Scaffold", "NavigationRail", "NavigationBar",
    )

    private val call = Regex("(^|[^A-Za-z0-9_.])(${Old.joinToString("|")})\\(")

    @Test
    fun `the guard reads the whole folder`() {
        // A path that silently matched nothing would pass the test below with nothing checked.
        assertTrue("only ${Rebuilt.size} files found under $ui", Rebuilt.size > 40)
        listOf("SettingsScreen.kt", "ChannelsSection.kt", "ModelSection.kt", "TickerPicker.kt", "CommonUi.kt")
            .forEach { assertTrue("$it is not read by the guard", it in Rebuilt) }
    }

    @Test
    fun `no ui file calls an old element`() {
        val found = Rebuilt.flatMap { name ->
            val file = File(ui, name)
            assertTrue("$name is missing - rename it in this list too", file.isFile)
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//").trim()
                if (code.startsWith("*") || code.startsWith("/*")) return@mapIndexedNotNull null
                call.find(code)?.let { "$name:${index + 1}  ${it.groupValues[2]}" }
            }
        }
        assertEquals("Old elements still drawn:\n" + found.joinToString("\n"), emptyList<String>(), found)
    }

    @Test
    fun `the full-screen chart's page draws in the kit's face and capsules`() {
        val page = File("src/main/assets/chart/chart.html").readText()
        assertTrue("the chart page still names Roboto", "Roboto" !in page)
        assertTrue("the chart page should load the bundled figures face", "jetbrains_mono.ttf" in page)
        assertTrue("the face the page loads must be bundled beside it", File("src/main/assets/chart/jetbrains_mono.ttf").isFile)
    }

    @Test
    fun `the date pickers are the kit's calendar`() {
        val pickers = listOf("TradeControls.kt", "Filters.kt", "AnalyzeScreen.kt")
        pickers.forEach { name ->
            val text = File(ui, name).readText()
            assertTrue("$name should open the kit calendar", "KitCalendarSheet(" in text)
            assertTrue("$name still reaches the platform picker", "android.app.DatePickerDialog" !in text && "DatePickerDialog(\n        context" !in text)
        }
    }

    /**
     * One pill style since 5.1.4: the kit's tag is always a wash of colour. An outlined pill - a
     * hairline capsule round a word - was the pre-5.0 look; the helpers that drew one are in [Old],
     * and this holds the outlined mode out of the kit's own tag.
     */
    @Test
    fun `every pill is the kit's filled tag`() {
        val tag = File(ui, "kit/Extras.kt").readText().replace("\r\n", "\n")
            .substringAfter("fun KitTag(").substringBefore("\n}\n")
        assertTrue("KitTag has an outlined mode again", "border(" !in tag && "filled" !in tag)
    }
}
