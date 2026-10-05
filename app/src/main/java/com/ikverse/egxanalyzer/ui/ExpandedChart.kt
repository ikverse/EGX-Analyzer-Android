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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.ikverse.egxanalyzer.model.DailySession
import java.time.LocalDate
import java.time.ZoneOffset
import org.json.JSONArray
import org.json.JSONObject

/**
 * The stock sheet's chart at the size of the screen: candles or a line, a volume pane, and the same
 * levels the sheet draws, on a price axis the reader can scale with a finger.
 *
 * It is KLineChart in a web view, and the only one in the app. The chart in the sheet and on the
 * call and position cards stays the native [PriceChart], because those sit inside scrolling cards
 * where a web view per card is the wrong trade; this is one view on one screen, opened on purpose.
 *
 * **The controls are drawn here and the chart is drawn by the page.** The range, the candle/line
 * choice and the levels switch are chips like the ones in the sheet, and the range and levels are
 * the sheet's own state, so pressing 6M here is 6M there when the dialog is closed. Whatever changes
 * is sent to the page as one new payload, and the page starts again on its auto-fit - a chart that
 * kept a hand-dragged scale across a change of range would be showing a scale for a line it is no
 * longer drawing.
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
    onClose: () -> Unit,
) {
    var candles by remember { mutableStateOf(true) }
    val visible = remember(history, range) { history.within(range) }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(end = Space.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close chart")
                    }
                    Text(ticker, style = MaterialTheme.typography.titleMedium)
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
                    CompactFilterChip(label = "Candles", selected = candles, onClick = { candles = true })
                    CompactFilterChip(label = "Line", selected = !candles, onClick = { candles = false })
                    // Absent rather than disabled where there is nothing to draw, as in the sheet.
                    if (levels != null) {
                        Spacer(Modifier.width(Space.m))
                        CompactFilterChip(
                            label = "Levels",
                            selected = showLevels,
                            onClick = { onLevels(!showLevels) },
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val theme = chartTheme()
                val payload = remember(visible, levels, calls, candles, showLevels, theme, ticker, range) {
                    ExpandedChartJson.build(
                        sessions = visible,
                        levels = levels,
                        calls = calls,
                        line = !candles,
                        showLevels = showLevels && levels != null,
                        theme = theme,
                        key = "$ticker|${range.name}|$candles|$showLevels",
                    )
                }
                if (LocalInspectionMode.current) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Chart: $ticker, ${visible.size} sessions", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    ChartWebView(payload, theme.surface, Modifier.fillMaxSize())
                }
            }
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
private fun ChartWebView(payload: String, background: Color, modifier: Modifier) {
    var loaded by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<WebView?>(null) }
    LaunchedEffect(payload, loaded, view) {
        val web = view
        if (loaded && web != null) web.evaluateJavascript("egxChart.setData($payload)", null)
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
                        loaded = true
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
                view = this
            }
        },
        onRelease = {
            view = null
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
 * What the page is sent: the sessions as candles, the levels as labelled prices, and the dates a
 * call was made on.
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
        line: Boolean,
        showLevels: Boolean,
        theme: ChartTheme,
        key: String,
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
            .put("line", line)
            .put("showLevels", showLevels)
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
