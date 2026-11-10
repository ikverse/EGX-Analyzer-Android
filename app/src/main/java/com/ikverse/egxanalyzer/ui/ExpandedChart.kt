package com.ikverse.egxanalyzer.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ikverse.egxanalyzer.model.FoldPosture
import com.ikverse.egxanalyzer.ui.kit.Kit
import com.ikverse.egxanalyzer.ui.kit.KitDialog
import com.ikverse.egxanalyzer.ui.kit.KitIconButton
import com.ikverse.egxanalyzer.ui.kit.KitMenu
import com.ikverse.egxanalyzer.ui.kit.KitSheet
import com.ikverse.egxanalyzer.ui.kit.KitSwitchFace
import com.ikverse.egxanalyzer.ui.kit.KitTextField
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.MenuItem
import com.ikverse.egxanalyzer.ui.kit.SegmentedControl
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.theme.palette
import com.ikverse.egxanalyzer.model.DailySession
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/**
 * The stock sheet's chart at the size of the screen, behaving the way TradingView's does: one finger
 * pans and flings, two pinch, a long press leaves a crosshair behind, both axes stretch under a
 * finger, and the price strip at the top says the session under the crosshair. Five chart types,
 * eight indicators, eight drawing tools, and the same levels the sheet draws.
 *
 * It is KLineChart in a web view, and the only one in the app. The chart in the sheet and on the
 * call and position cards stays the native [PriceChart], because those sit inside scrolling cards
 * where a web view per card is the wrong trade; this is one view on one screen, opened on purpose.
 *
 * **The toolbar is drawn here and the chart is drawn by the page**, along with the controls that sit
 * on it - the scale buttons, the jump to the newest session, the × beside each indicator. The range
 * and the levels are the sheet's own state, so pressing 6M here is 6M there when the dialog is closed.
 *
 * **What the reader changes on the page is read back, not reported.** There is no JavaScript
 * interface: the app asks `egxChart.state()` for the indicators and the drawings when the
 * Indicators sheet opens, when the chart closes, and when the app is put in the background, and
 * keeps them through [onIndicators] and [onDrawings]. Device-local, never synced or backed up.
 */
@Composable
internal fun ExpandedChart(
    ticker: String,
    history: List<DailySession>,
    levels: ChartLevels?,
    calls: Set<LocalDate>,
    range: ChartRange,
    onRange: (ChartRange) -> Unit,
    showLevels: Boolean,
    onLevels: (Boolean) -> Unit,
    indicators: Set<String>,
    onIndicators: (Set<String>) -> Unit,
    drawings: String?,
    onDrawings: (String?) -> Unit,
    onClose: () -> Unit,
    /** The newest call's session, which is where the Call range starts. Null with no call. */
    newestCall: LocalDate? = null,
) {
    var style by remember { mutableStateOf(ChartStyle.CANDLES) }
    var on by remember { mutableStateOf(ChartIndicator.known(indicators)) }
    // Handed to a page on its first payload; after that the page holds the drawings and the app only
    // reads them back. Kept current from every read rather than frozen at opening, because a fold
    // rebuilds the web view, and the new page is seeded from this - frozen, it lost every line drawn
    // since the chart was opened.
    var seedDrawings by remember { mutableStateOf(drawings) }
    var drawOpen by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf<ChartTool?>(null) }
    var askNote by remember { mutableStateOf(false) }
    var indicatorsOpen by remember { mutableStateOf(false) }
    var styleMenu by remember { mutableStateOf(false) }
    val bridge = remember { ChartBridge() }
    // A stored Call range on a stock nobody has called is a month, never an empty box.
    val effective = if (range == ChartRange.CALL && newestCall == null) ChartRange.MONTH else range
    val visible = remember(history, effective, newestCall) { history.within(effective, newestCall) }
    val move = remember(history) { history.lastMove() }
    val posture = LocalFoldPosture.current
    val p = palette

    val keepIndicators by rememberUpdatedState(onIndicators)
    val keepDrawings by rememberUpdatedState(onDrawings)
    // What the page says the reader changed, taken into the app's keeping.
    fun keep(state: JSONObject?, then: () -> Unit = {}) {
        if (state != null) {
            state.optJSONArray("indicators")?.let { list ->
                val next = ChartIndicator.known((0 until list.length()).map { list.optString(it) }.toSet())
                if (next != on) on = next
                keepIndicators(next)
            }
            state.optJSONArray("drawings")?.let {
                seedDrawings = it.toString()
                keepDrawings(it.toString())
            }
        }
        then()
    }
    val close: () -> Unit = { bridge.pull { keep(it, onClose) } }

    // Put in the background with the chart open: kept now, because the process may not be there to
    // keep them when the reader comes back.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) bridge.pull { keep(it) } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // A tool stays lit until its last point lands, which only the page knows.
    LaunchedEffect(armed) {
        while (armed != null) {
            delay(350)
            bridge.pull { state ->
                if (state != null && !state.optBoolean("drawing")) {
                    // The tool's last point has landed: take the finished line into keeping now.
                    keep(state)
                    armed = null
                }
            }
        }
    }
    fun arm(tool: ChartTool) {
        when {
            armed == tool -> {
                bridge.run("egxChart.cancel()")
                armed = null
            }
            tool == ChartTool.NOTE -> askNote = true
            else -> {
                bridge.run("egxChart.draw('${tool.id}')")
                armed = tool
            }
        }
    }
    val undo = {
        bridge.run("egxChart.undo()")
        armed = null
        bridge.pull { keep(it) }
    }
    val clearAll = {
        bridge.run("egxChart.clearDrawings()")
        armed = null
        bridge.pull { keep(it) }
    }
    fun toggleIndicator(indicator: ChartIndicator, checked: Boolean) {
        on = if (checked) on + indicator.id else on - indicator.id
        onIndicators(on)
    }
    val openIndicators = { bridge.pull { keep(it) { indicatorsOpen = !indicatorsOpen } } }

    @Composable
    fun header(withRange: Boolean) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KitIconButton(Icons.Outlined.Close, label = "Close chart", onClick = close, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(ticker, style = MaterialTheme.typography.headlineSmall, color = p.ink)
                if (move != null) {
                    Text(
                        formatPrice(move.close) + " · " + (if (move.change > 0) "+" else "") + formatPrice(move.change) +
                            " (" + formatPercent(move.percent) + ")",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularFigures),
                        color = if (move.change >= 0) p.gain else p.loss,
                    )
                }
            }
            if (withRange) RangeRow(effective, newestCall != null, onRange, compact = true)
        }
    }

    @Composable
    fun toolbar() {
        Row(
            Modifier.fillMaxWidth().scrollableRow().padding(horizontal = 14.dp).padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                ToolChip(style.label + " ▾", fill = p.highlight, ink = p.onTile, bold = true) { styleMenu = true }
                KitMenu(
                    expanded = styleMenu,
                    onDismiss = { styleMenu = false },
                    items = ChartStyle.entries.map { option ->
                        MenuItem(option.label + if (option == style) "  ✓" else "") { style = option }
                    },
                )
            }
            ToolChip(
                if (on.isEmpty()) "Indicators" else "Indicators · ${on.size}",
                fill = if (indicatorsOpen) p.ink else p.raised,
                ink = if (indicatorsOpen) p.onInk else p.ink,
                onClick = openIndicators,
            )
            ToolChip(
                "Draw",
                fill = if (drawOpen) p.ink else p.raised,
                ink = if (drawOpen) p.onInk else p.ink,
            ) {
                if (drawOpen && armed != null) {
                    bridge.run("egxChart.cancel()")
                    armed = null
                }
                drawOpen = !drawOpen
            }
            // Absent rather than disabled where there is nothing to draw, as in the sheet.
            if (levels != null) {
                ToolChip(
                    if (showLevels) "Levels ✓" else "Levels",
                    fill = if (showLevels) p.hero else p.raised,
                    ink = if (showLevels) p.onHero else p.ink,
                ) { onLevels(!showLevels) }
            }
            ToolChip("Undo", fill = p.raised, ink = p.ink, onClick = undo)
            ToolChip("Clear all", fill = p.raised, ink = p.ink, onClick = clearAll)
        }
    }

    @Composable
    fun chart(modifier: Modifier) {
        val theme = chartTheme()
        val payload = remember(visible, levels, calls, style, showLevels, theme, ticker, effective, on) {
            ExpandedChartJson.build(
                sessions = visible,
                levels = levels,
                calls = calls,
                style = style,
                showLevels = showLevels && levels != null,
                theme = theme,
                key = "$ticker|${effective.name}|${style.id}|$showLevels",
                indicators = on,
                drawings = seedDrawings,
            )
        }
        if (LocalInspectionMode.current) {
            Box(modifier, contentAlignment = Alignment.Center) {
                Text("Chart: $ticker, ${visible.size} sessions", style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
        } else {
            ChartWebView(payload, theme.surface, bridge, modifier)
        }
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalContentColor provides p.ink) {
            Box(Modifier.fillMaxSize().background(p.ground)) {
                when (posture) {
                    is FoldPosture.Tabletop -> {
                        // The chart stands on the upright half; every control lies on the flat one.
                        val crease = posture.hingeStartFraction.coerceIn(0.3f, 0.8f)
                        Column(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxWidth().weight(crease)) {
                                header(withRange = false)
                                chart(Modifier.fillMaxWidth().weight(1f))
                            }
                            Column(
                                Modifier.fillMaxWidth().weight(1f - crease).scrollableColumn().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                RangeGrid(effective, newestCall != null, onRange, levels != null, showLevels, onLevels)
                                SegmentedControl(
                                    options = ChartStyle.entries,
                                    selected = style,
                                    onSelect = { style = it },
                                    label = { it.label.substringBefore(" ") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                ToolChip(
                                    if (on.isEmpty()) "Indicators" else "Indicators · ${on.size}",
                                    fill = p.raised,
                                    ink = p.ink,
                                    onClick = openIndicators,
                                )
                                Text("DRAW", style = MaterialTheme.typography.labelSmall, color = p.muted)
                                ToolGrid(armed, ::arm, undo, clearAll)
                            }
                        }
                    }
                    is FoldPosture.Unfolded, is FoldPosture.Book -> {
                        // The chart with its toolbar on one side; the indicators docked on the other
                        // while they are open, the split falling on the hinge in book posture.
                        val side = if (posture is FoldPosture.Book) 1f - posture.hingeEndFraction else 0.33f
                        Row(Modifier.fillMaxSize()) {
                            Column(Modifier.weight(1f - if (indicatorsOpen) side else 0f).fillMaxHeight()) {
                                header(withRange = true)
                                toolbar()
                                if (drawOpen) Box(Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp)) { ToolGrid(armed, ::arm, undo, clearAll) }
                                chart(Modifier.fillMaxWidth().weight(1f))
                            }
                            if (indicatorsOpen) {
                                Column(
                                    Modifier
                                        .weight(side)
                                        .fillMaxHeight()
                                        .background(p.raised.copy(alpha = 0.5f))
                                        .scrollableColumn()
                                        .padding(16.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Indicators", style = MaterialTheme.typography.headlineSmall, color = p.ink, modifier = Modifier.weight(1f))
                                        KitIconButton(Icons.Outlined.Close, label = "Close indicators", onClick = { indicatorsOpen = false }, size = 36.dp)
                                    }
                                    IndicatorRows(on, ::toggleIndicator)
                                }
                            }
                        }
                    }
                    else -> {
                        Column(Modifier.fillMaxSize()) {
                            header(withRange = false)
                            toolbar()
                            if (drawOpen) Box(Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp)) { ToolGrid(armed, ::arm, undo, clearAll) }
                            chart(Modifier.fillMaxWidth().weight(1f))
                            Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                RangeRow(effective, newestCall != null, onRange, compact = false)
                            }
                        }
                    }
                }
            }
        }
        // Folded and in tabletop the indicators are a sheet; unfolded they are docked beside.
        if (indicatorsOpen && posture !is FoldPosture.Unfolded && posture !is FoldPosture.Book) {
            KitSheet(onDismiss = { indicatorsOpen = false }, posture = posture, title = "Indicators") {
                Text("Indicators", style = MaterialTheme.typography.headlineMedium, color = p.ink)
                Tile(Modifier.fillMaxWidth()) { IndicatorRows(on, ::toggleIndicator) }
            }
        }
        if (askNote) {
            var text by remember { mutableStateOf("") }
            KitDialog(
                title = "Note",
                text = "Type the note, then tap the chart where it goes.",
                confirmLabel = "Place",
                onConfirm = {
                    if (text.isNotBlank()) {
                        bridge.run("egxChart.draw('note', ${JSONObject.quote(text.trim())})")
                        armed = ChartTool.NOTE
                    }
                    askNote = false
                },
                dismissLabel = "Cancel",
                onDismiss = { askNote = false },
                posture = posture,
                glyph = "✎",
            ) {
                KitTextField(
                    value = text,
                    onValueChange = { text = it.take(80) },
                    label = "Note",
                    modifier = Modifier.fillMaxWidth().padding(top = Space.m),
                )
            }
        }
    }
}

/** A chip on the chart's toolbar: a capsule in [fill], its word in [ink]. */
@Composable
private fun ToolChip(label: String, fill: Color, ink: Color, bold: Boolean = false, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .pressScale(press)
            .height(40.dp)
            .clip(Kit.Capsule)
            .background(fill)
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = ink,
            maxLines = 1,
        )
    }
}

/** The ranges as one row of capsules, the chosen one in ink. [compact] sizes them for a header. */
@Composable
private fun RangeRow(range: ChartRange, hasCall: Boolean, onRange: (ChartRange) -> Unit, compact: Boolean) {
    val p = palette
    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
        ChartRange.entries.filter { hasCall || it != ChartRange.CALL }.forEach { option ->
            val chosen = option == range
            val press = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .then(if (compact) Modifier else Modifier.weight(1f))
                    .minimumInteractiveComponentSize()
                    .pressScale(press)
                    .height(40.dp)
                    .clip(Kit.Capsule)
                    .background(if (chosen) p.ink else p.raised)
                    .clickable(interactionSource = press, indication = null, role = Role.Button) { onRange(option) }
                    .semantics { selected = chosen }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularFigures),
                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                    color = if (chosen) p.onInk else p.ink,
                )
            }
        }
    }
}

/** Tabletop's ranges: a grid of tall capsules, with Levels as its last cell where there are levels. */
@Composable
private fun RangeGrid(
    range: ChartRange,
    hasCall: Boolean,
    onRange: (ChartRange) -> Unit,
    hasLevels: Boolean,
    showLevels: Boolean,
    onLevels: (Boolean) -> Unit,
) {
    val p = palette
    val cells = ChartRange.entries.filter { hasCall || it != ChartRange.CALL }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.forEach { option ->
            val chosen = option == range
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(Kit.Capsule)
                    .background(if (chosen) p.ink else p.raised)
                    .clickable(role = Role.Button) { onRange(option) }
                    .semantics { selected = chosen },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = TabularFigures),
                    color = if (chosen) p.onInk else p.ink,
                )
            }
        }
        if (hasLevels) {
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(Kit.Capsule)
                    .background(if (showLevels) p.hero else p.raised)
                    .clickable(role = Role.Button) { onLevels(!showLevels) },
                contentAlignment = Alignment.Center,
            ) {
                Text("Levels", style = MaterialTheme.typography.labelLarge, color = if (showLevels) p.onHero else p.ink)
            }
        }
    }
}

/**
 * The drawing tools as a grid of tiles, four across: the tool's glyph over its name, the armed one
 * in sunflower, then Undo, and Clear all in the loss colour.
 */
@Composable
private fun ToolGrid(armed: ChartTool?, onArm: (ChartTool) -> Unit, onUndo: () -> Unit, onClear: () -> Unit) {
    val p = palette
    data class Cell(val glyph: String, val label: String, val lit: Boolean, val danger: Boolean, val onClick: () -> Unit)
    val cells = ChartTool.entries.map { tool ->
        Cell(tool.glyph(), tool.label, armed == tool, false) { onArm(tool) }
    } + listOf(
        Cell("↶", "Undo", false, false, onUndo),
        Cell("⌫", "Clear all", false, true, onClear),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cell ->
                    Column(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                when {
                                    cell.lit -> p.tile1
                                    cell.danger -> p.loss.copy(alpha = 0.14f)
                                    else -> p.raised
                                },
                            )
                            .clickable(role = Role.Button, onClick = cell.onClick)
                            .semantics { selected = cell.lit },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val ink = when {
                            cell.lit -> p.onTile
                            cell.danger -> p.loss
                            else -> p.ink
                        }
                        Text(cell.glyph, style = MaterialTheme.typography.titleMedium, color = ink)
                        Text(cell.label, style = MaterialTheme.typography.labelSmall, color = ink, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A drawing tool's glyph on its tile. */
private fun ChartTool.glyph(): String = when (this) {
    ChartTool.TREND -> "╱"
    ChartTool.RAY -> "↗"
    ChartTool.HLINE -> "━"
    ChartTool.HRAY -> "→"
    ChartTool.VLINE -> "┃"
    ChartTool.CHANNEL -> "⫽"
    ChartTool.FIB -> "≡"
    ChartTool.NOTE -> "✎"
}

/** The indicators, over the price and under it, each with a kit switch. */
@Composable
private fun IndicatorRows(on: Set<String>, onToggle: (ChartIndicator, Boolean) -> Unit) {
    val p = palette
    listOf(true to "ON THE PRICE", false to "BELOW THE PRICE").forEach { (overPrice, heading) ->
        Text(heading, style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
        ChartIndicator.entries.filter { it.overPrice == overPrice }.forEach { indicator ->
            val checked = indicator.id in on
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(p.ground)
                    .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle(indicator, it) })
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(indicator.label, style = MaterialTheme.typography.bodyMedium, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
                KitSwitchFace(checked)
            }
        }
    }
}

/** How the sessions are drawn. [id] is the page's own word for it. */
internal enum class ChartStyle(val label: String, val id: String) {
    CANDLES("Candles", "candles"),
    HOLLOW("Hollow candles", "hollow"),
    BARS("Bars", "bars"),
    LINE("Line", "line"),
    AREA("Area", "area"),
}

/**
 * What can be put on the chart, by the chart library's own names. Those drawn over the price share
 * its pane; the rest each get a pane under it, as in TradingView. The periods are set in
 * `chart.html` and the labels here must say the same ones.
 */
internal enum class ChartIndicator(val id: String, val label: String, val overPrice: Boolean) {
    MA("MA", "Moving averages (5, 10, 30)", true),
    EMA("EMA", "Exponential moving averages (9, 21, 50)", true),
    BOLL("BOLL", "Bollinger Bands (20, 2)", true),
    SAR("SAR", "Parabolic SAR", true),
    VOL("VOL", "Volume", false),
    MACD("MACD", "MACD (12, 26, 9)", false),
    RSI("RSI", "RSI (14)", false),
    KDJ("KDJ", "Stochastic KDJ (9, 3, 3)", false),
    ;

    companion object {
        /** [names] without anything this build does not draw, so a stale or garbled entry is dropped. */
        fun known(names: Set<String>): Set<String> =
            entries.map { it.id }.filterTo(linkedSetOf()) { it in names }
    }
}

/** The drawing tools. [id] is the page's own word for each. */
internal enum class ChartTool(val label: String, val id: String) {
    TREND("Trend line", "trend"),
    RAY("Ray", "ray"),
    HLINE("Horizontal line", "hline"),
    HRAY("Horizontal ray", "hray"),
    VLINE("Vertical line", "vline"),
    CHANNEL("Parallel channel", "channel"),
    FIB("Fib retracement", "fib"),
    NOTE("Note", "note"),
}

/** The last close and how it moved from the session before, for the chart's header. */
internal data class LastMove(val close: Double, val change: Double, val percent: Double)

internal fun List<DailySession>.lastMove(): LastMove? {
    val priced = filter { it.close != null }.sortedBy { it.date }.takeLast(2)
    if (priced.size < 2) return null
    val previous = priced[0].close!!
    val last = priced[1].close!!
    if (previous <= 0.0) return null
    return LastMove(last, last - previous, (last - previous) / previous * 100)
}

/**
 * The one way the app talks to the page: [run] sends it something, [pull] asks it for
 * `egxChart.state()`. Before the page has loaded, a pull answers null at once, so closing a chart
 * that never finished loading still closes it.
 */
@Stable
internal class ChartBridge {
    var view by mutableStateOf<WebView?>(null)
    var loaded by mutableStateOf(false)

    fun run(script: String) {
        val web = view
        if (loaded && web != null) web.evaluateJavascript(script, null)
    }

    fun pull(answer: (JSONObject?) -> Unit) {
        val web = view
        if (!loaded || web == null) {
            answer(null)
            return
        }
        web.evaluateJavascript("egxChart.state()") { result ->
            answer(runCatching { JSONObject(result) }.getOrNull())
        }
    }
}

/**
 * One web view with the bundled chart page in it, sent [payload] whenever it changes.
 *
 * Only the app's own page and script load: the page is `file:///android_asset/chart/chart.html`, any
 * other request - the network, a file elsewhere - is answered with nothing, and a link goes nowhere.
 * The page needs no network, and the app holds a network permission for other reasons.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ChartWebView(payload: String, background: Color, bridge: ChartBridge, modifier: Modifier) {
    LaunchedEffect(payload, bridge.loaded, bridge.view) {
        bridge.run("egxChart.setData($payload)")
    }
    AndroidView(
        modifier = modifier.semantics { contentDescription = "Price chart" },
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(background.toArgb())
                overScrollMode = android.view.View.OVER_SCROLL_NEVER
                settings.apply {
                    javaScriptEnabled = true // the page is the app's own, bundled in it
                    allowFileAccess = true // needed to read the bundled page from the app's assets
                    allowContentAccess = false
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = false
                    blockNetworkLoads = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    setSupportZoom(false)
                    domStorageEnabled = false
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        // Only the view the bridge holds: a page finishing in a view already
                        // being replaced must not mark the new one ready before it is.
                        if (bridge.view === view) bridge.loaded = true
                    }

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        if (request.url.toString().startsWith("file:///android_asset/")) {
                            null
                        } else {
                            WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                        }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                }
                bridge.loaded = false
                bridge.view = this
                loadUrl("file:///android_asset/chart/chart.html")
            }
        },
        onRelease = {
            // A fold moves the chart into another branch of the layout, so a new web view is built
            // and this one released - in either order. Only the view the bridge still holds is
            // taken off it; clearing it unconditionally left the bridge empty and the chart blank.
            if (bridge.view === it) {
                bridge.view = null
                bridge.loaded = false
            }
            it.destroy()
        },
    )
}

/** The sessions inside [range], counted back from the newest one held rather than from today. */
internal fun List<DailySession>.within(range: ChartRange, callOn: LocalDate? = null): List<DailySession> {
    val anchor = lastOrNull()?.date ?: return this
    val since = range.since(anchor, callOn)
    return filter { !it.date.isBefore(since) }
}

/** The colours the page draws with, as `#rrggbb` text, taken from the theme the sheet is in. */
internal data class ChartTheme(
    val surface: Color,
    val text: String,
    val muted: String,
    val rule: String,
    val up: String,
    val down: String,
    val line: String,
    val stop: String,
    val target: String,
    val entry: String,
    val paid: String,
    /** The page's own buttons: their fill, and the ink of the one that is on. */
    val raised: String = "#E2DFD5",
    val onInk: String = "#FFFFFF",
)

@Composable
private fun chartTheme(): ChartTheme {
    // The kit palette in force, so the chart follows the app's light or dark theme: the page's
    // ground, ink and muted grey, the hairline, gain and loss, cobalt for the line, and the levels
    // in the colours the sheet draws them in.
    val p = palette
    return ChartTheme(
        surface = p.ground,
        text = hex(p.ink),
        muted = hex(p.muted),
        rule = hex(p.line),
        up = hex(p.gain),
        down = hex(p.loss),
        line = hex(p.hero),
        stop = hex(p.loss),
        target = hex(p.gain),
        entry = hex(p.ink),
        paid = hex(p.muted),
        raised = hex(p.raised),
        onInk = hex(p.onInk),
    )
}

private fun hex(color: Color): String = String.format(java.util.Locale.ROOT, "#%06X", color.toArgb() and 0xFFFFFF)

/**
 * What the page is sent: the sessions as candles, the levels as labelled prices, the dates a call
 * was made on, how to draw them, which indicators are on, and - read once, on the first payload - the
 * lines drawn on this stock before.
 *
 * Kept apart from the composable so it can be read, and tested, as the plain function it is.
 */
internal object ExpandedChartJson {
    /**
     * How far the price axis may grow to take the levels in, as a multiple of the candles' own span.
     * The figure [PriceChart] uses for the same reason - a target the stock has been nowhere near
     * would flatten the candles the chart exists to show - so a level past it is pinned to the edge
     * with an arrow instead.
     */
    const val ScaleGrowth = 2.5

    private const val NoonMs = 12 * 60 * 60 * 1000L

    /**
     * A session is a day, and the page is given it at noon UTC: whatever timezone the phone is in
     * the day it reads off that moment is the same one, which midnight would not give it.
     */
    fun timestamp(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + NoonMs

    fun build(
        sessions: List<DailySession>,
        levels: ChartLevels?,
        calls: Set<LocalDate>,
        style: ChartStyle,
        showLevels: Boolean,
        theme: ChartTheme,
        key: String,
        indicators: Set<String> = setOf(ChartIndicator.VOL.id),
        drawings: String? = null,
    ): String {
        val priced = sessions.filter { it.close != null }.sortedBy { it.date }
        val candles = JSONArray()
        priced.forEach { session ->
            val close = session.close!!
            val open = session.open ?: close
            // A feed row missing its extremes, or carrying ones that do not enclose its own open
            // and close, is drawn as the body alone rather than as a wick that contradicts it. A
            // zero is the feed's way of saying a session was still in progress, not a price.
            val high = maxOf(session.high?.takeIf { it > 0.0 } ?: close, open, close)
            val low = minOf(session.low?.takeIf { it > 0.0 } ?: close, open, close)
            if (!(open.isFinite() && high.isFinite() && low.isFinite() && close.isFinite())) return@forEach
            candles.put(
                JSONArray()
                    .put(timestamp(session.date)).put(open).put(high).put(low).put(close)
                    .put(session.volume?.takeIf { it.isFinite() } ?: 0.0),
            )
        }
        val rows = JSONArray()
        if (levels != null) levelRows(levels).forEach { rows.put(it) }
        return JSONObject()
            .put("key", key)
            .put("candles", candles)
            .put("calls", JSONArray(priced.filter { it.date in calls }.map { timestamp(it.date) }))
            .put("levels", rows)
            .put("style", style.id)
            .put("showLevels", showLevels)
            .put("indicators", JSONArray(ChartIndicator.known(indicators).toList()))
            // A stored list that no longer parses is dropped rather than handed to the page broken.
            .put("drawings", drawings?.let { runCatching { JSONArray(it) }.getOrNull() } ?: JSONArray())
            .put("growth", ScaleGrowth)
            .put(
                "theme",
                JSONObject()
                    .put("bg", hex(theme.surface))
                    .put("text", theme.text).put("muted", theme.muted).put("rule", theme.rule)
                    .put("up", theme.up).put("down", theme.down).put("line", theme.line)
                    .put("stop", theme.stop).put("target", theme.target)
                    .put("entry", theme.entry).put("paid", theme.paid)
                    .put("raised", theme.raised).put("onInk", theme.onInk),
            )
            .toString()
    }

    /** The labelled prices, worded as the sheet's own chart words them. */
    fun levelRows(levels: ChartLevels): List<JSONObject> = buildList {
        fun add(kind: String, label: String, price: Double) {
            if (price.isFinite()) add(JSONObject().put("kind", kind).put("label", label).put("price", price))
        }
        levels.stopLoss?.let { add("stop", "stop " + formatPrice(it), it) }
        val low = levels.entryLow
        val high = levels.entryHigh
        if (low != null && high != null && low != high) {
            add("entry", "entry " + formatPrice(low) + "–" + formatPrice(high), (low + high) / 2)
        } else {
            (low ?: high)?.let { add("entry", "entry " + formatPrice(it), it) }
        }
        levels.paid?.let { add("paid", "you paid " + formatPrice(it), it) }
        levels.target1?.let { add("target", "t1 " + formatPrice(it), it) }
        levels.target2?.let { add("target", "t2 " + formatPrice(it), it) }
    }
}
