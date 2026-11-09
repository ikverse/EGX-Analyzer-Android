package com.ikverse.egxanalyzer.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
@OptIn(ExperimentalMaterial3Api::class)
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
) {
    var style by remember { mutableStateOf(ChartStyle.CANDLES) }
    var on by remember { mutableStateOf(ChartIndicator.known(indicators)) }
    // Handed to the page once, on its first payload; after that the page holds the drawings and the
    // app only ever reads them back.
    val firstDrawings = remember { drawings }
    var drawOpen by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf<ChartTool?>(null) }
    var askNote by remember { mutableStateOf(false) }
    var indicatorsOpen by remember { mutableStateOf(false) }
    val bridge = remember { ChartBridge() }
    val visible = remember(history, range) { history.within(range) }
    val move = remember(history) { history.lastMove() }

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
            state.optJSONArray("drawings")?.let { keepDrawings(it.toString()) }
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
            bridge.pull { state -> if (state != null && !state.optBoolean("drawing")) armed = null }
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

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(end = Space.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // A round tonal button, as in the page header (2026-11 redesign).
                    IconButton(
                        onClick = close,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.padding(Space.xs),
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close chart")
                    }
                    Text(ticker, style = MaterialTheme.typography.titleLarge)
                    // The last close and the day it made, as TradingView's symbol header has them.
                    if (move != null) {
                        Spacer(Modifier.width(Space.m))
                        Text(
                            formatPrice(move.close),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
                        )
                        Spacer(Modifier.width(Space.s))
                        Text(
                            (if (move.change > 0) "+" else "") + formatPrice(move.change) + " (" + formatPercent(move.percent) + ")",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures),
                            color = PriceRole.forReturn(move.change),
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().scrollableRow().padding(horizontal = Space.m),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChartRange.entries.forEach { option ->
                        CompactFilterChip(
                            label = option.label,
                            selected = option == range,
                            onClick = { onRange(option) },
                        )
                    }
                    Spacer(Modifier.width(Space.m))
                    Box {
                        var menu by remember { mutableStateOf(false) }
                        CompactFilterChip(label = style.label + " ▾", selected = false, onClick = { menu = true })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            ChartStyle.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label, style = MaterialTheme.typography.bodyMedium) },
                                    onClick = {
                                        style = option
                                        menu = false
                                    },
                                    trailingIcon = if (option == style) ({ Text("✓") }) else null,
                                )
                            }
                        }
                    }
                    CompactFilterChip(
                        label = if (on.isEmpty()) "Indicators" else "Indicators · ${on.size}",
                        selected = false,
                        onClick = { bridge.pull { keep(it) { indicatorsOpen = true } } },
                    )
                    CompactFilterChip(
                        label = "Draw",
                        selected = drawOpen,
                        toggle = true,
                        onClick = {
                            if (drawOpen && armed != null) {
                                bridge.run("egxChart.cancel()")
                                armed = null
                            }
                            drawOpen = !drawOpen
                        },
                    )
                    // Absent rather than disabled where there is nothing to draw, as in the sheet.
                    if (levels != null) {
                        CompactFilterChip(
                            label = "Levels",
                            selected = showLevels,
                            toggle = true,
                            onClick = { onLevels(!showLevels) },
                        )
                    }
                }
                if (drawOpen) {
                    Row(
                        Modifier.fillMaxWidth().scrollableRow().padding(horizontal = Space.m),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChartTool.entries.forEach { tool ->
                            CompactFilterChip(label = tool.label, selected = armed == tool, onClick = { arm(tool) })
                        }
                        Spacer(Modifier.width(Space.m))
                        CompactFilterChip(label = "Undo", selected = false, onClick = {
                            bridge.run("egxChart.undo()")
                            armed = null
                        })
                        CompactFilterChip(label = "Clear all", selected = false, onClick = {
                            bridge.run("egxChart.clearDrawings()")
                            armed = null
                        })
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val theme = chartTheme()
                val payload = remember(visible, levels, calls, style, showLevels, theme, ticker, range, on) {
                    ExpandedChartJson.build(
                        sessions = visible,
                        levels = levels,
                        calls = calls,
                        style = style,
                        showLevels = showLevels && levels != null,
                        theme = theme,
                        key = "$ticker|${range.name}|${style.id}|$showLevels",
                        indicators = on,
                        drawings = firstDrawings,
                    )
                }
                if (LocalInspectionMode.current) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Chart: $ticker, ${visible.size} sessions", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    ChartWebView(payload, theme.surface, bridge, Modifier.fillMaxSize())
                }
            }
        }
        if (indicatorsOpen) {
            ModalBottomSheet(
                onDismissRequest = { indicatorsOpen = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                shape = SheetShape,
                containerColor = MaterialTheme.colorScheme.background,
                dragHandle = { SheetHandle() },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .scrollableColumn()
                        .padding(horizontal = Space.l)
                        .padding(bottom = Space.xl),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    Text("Indicators", style = MaterialTheme.typography.headlineSmall)
                    listOf(true to "On the price", false to "Below the price").forEach { (overPrice, heading) ->
                        Text(
                            heading,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.s),
                        )
                        ChartIndicator.entries.filter { it.overPrice == overPrice }.forEach { indicator ->
                            SettingToggle(
                                label = indicator.label,
                                checked = indicator.id in on,
                                onCheckedChange = { checked ->
                                    on = if (checked) on + indicator.id else on - indicator.id
                                    onIndicators(on)
                                },
                            )
                        }
                    }
                }
            }
        }
        if (askNote) {
            var text by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { askNote = false },
                title = { Text("Note") },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(80) },
                        singleLine = true,
                        placeholder = { Text("Then tap the chart where it goes") },
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = text.isNotBlank(),
                        onClick = {
                            bridge.run("egxChart.draw('note', ${JSONObject.quote(text.trim())})")
                            armed = ChartTool.NOTE
                            askNote = false
                        },
                    ) { Text("Place") }
                },
                dismissButton = { TextButton(onClick = { askNote = false }) { Text("Cancel") } },
            )
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
                        bridge.loaded = true
                    }

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        if (request.url.toString().startsWith("file:///android_asset/")) {
                            null
                        } else {
                            WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                        }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                }
                loadUrl("file:///android_asset/chart/chart.html")
                bridge.view = this
            }
        },
        onRelease = {
            bridge.view = null
            bridge.loaded = false
            it.destroy()
        },
    )
}

/** The sessions inside [range], counted back from the newest one held rather than from today. */
internal fun List<DailySession>.within(range: ChartRange): List<DailySession> {
    val anchor = lastOrNull()?.date ?: return this
    val since = range.since(anchor)
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
)

@Composable
private fun chartTheme(): ChartTheme {
    val scheme = MaterialTheme.colorScheme
    return ChartTheme(
        surface = scheme.surface,
        text = hex(scheme.onSurface),
        muted = hex(scheme.onSurfaceVariant),
        rule = hex(scheme.outlineVariant),
        // The same two colours the sheet draws a gain and a loss in.
        up = hex(PriceRole.target),
        down = hex(PriceRole.stop),
        line = hex(PriceRole.market),
        stop = hex(PriceRole.stop),
        target = hex(PriceRole.target),
        entry = hex(PriceRole.entry),
        paid = hex(scheme.outline),
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
                    .put("entry", theme.entry).put("paid", theme.paid),
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
