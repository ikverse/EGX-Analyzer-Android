package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.ui.theme.palette
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import com.ikverse.egxanalyzer.ui.kit.Kit
import androidx.compose.ui.draw.clip
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikverse.egxanalyzer.ui.theme.LocalDarkTheme
import com.ikverse.egxanalyzer.ui.theme.PageTheme
import com.ikverse.egxanalyzer.ui.theme.accentFor
import com.ikverse.egxanalyzer.ui.theme.groundAlpha
import com.ikverse.egxanalyzer.ui.theme.LightGroundLift
import com.ikverse.egxanalyzer.ui.theme.GroundLift
import androidx.window.layout.FoldingFeature
import com.ikverse.egxanalyzer.model.HingeReport
import com.ikverse.egxanalyzer.model.foldPosture
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo

internal enum class WindowWidth { COMPACT, MEDIUM, EXPANDED }

/**
 * How wide the window is, for screens that size themselves by it.
 *
 * The shell already works this out to choose a rail or a bar; publishing it saves every screen
 * measuring the window again and disagreeing about where the line falls.
 */
internal val LocalWindowWidth = staticCompositionLocalOf { WindowWidth.COMPACT }

/**
 * Width at which the layout switches to its wide form.
 *
 * Below Material's 840dp expanded breakpoint on purpose: a Fold 7 unfolded in portrait is roughly
 * 750dp, so an 840dp threshold would leave the largest screen the app runs on using the compact
 * layout.
 */
internal const val WIDE_LAYOUT_DP = 700f

@Composable
fun EgxAnalyzerApp(appState: AppState) {
    val density = LocalDensity.current
    val width = with(density) { LocalWindowInfo.current.containerSize.width.toDp().value }
    val windowWidth = when {
        width >= WIDE_LAYOUT_DP -> WindowWidth.EXPANDED
        width >= 600 -> WindowWidth.MEDIUM
        else -> WindowWidth.COMPACT
    }
    // Taken from the composition rather than passed in: the fold is the one thing on this screen
    // that genuinely needs an activity, and threading one through every screen to reach it was what
    // kept the whole UI unable to be drawn without a running app. Null in a preview, which simply
    // means no fold rather than a crash.
    val activity = LocalContext.current as? Activity
    val layoutInfo by produceState<WindowLayoutInfo?>(null, activity) {
        val host = activity ?: return@produceState
        WindowInfoTracker.getOrCreate(host).windowLayoutInfo(host).collect { value = it }
    }
    val separatingFold = layoutInfo?.displayFeatures.orEmpty().filterIsInstance<FoldingFeature>()
        .firstOrNull(FoldingFeature::isSeparating)
    // The 5.0 layouts read the hinge through one value: cover, unfolded, tabletop or book. Reduced
    // to plain numbers here so the decision itself is `foldPosture`, a function a test can call.
    val windowPx = LocalWindowInfo.current.containerSize
    val hinge = layoutInfo?.displayFeatures.orEmpty().filterIsInstance<FoldingFeature>().firstOrNull()
    val posture = foldPosture(
        widthDp = width,
        hinge = hinge?.let { feature ->
            val across = feature.orientation == FoldingFeature.Orientation.HORIZONTAL
            HingeReport(
                halfOpened = feature.state == FoldingFeature.State.HALF_OPENED,
                runsAcross = across,
                start = (if (across) feature.bounds.top else feature.bounds.left).toFloat(),
                end = (if (across) feature.bounds.bottom else feature.bounds.right).toFloat(),
                windowExtent = (if (across) windowPx.height else windowPx.width).toFloat(),
            )
        },
    )

    // **One shell at every width since 5.0.** The rail and the floating bar are gone; every page
    // carries its own tab row in its header, so the wide layout and the narrow one are the same
    // composition and a fold no longer swaps one subtree for another.
    // Above the shells and not inside either of them: back means the same thing whichever one is
    // drawn, and a handler in each would be one rule written twice. Enabled only when there is
    // something to undo, so a reader with nothing outstanding gets the system's own behaviour -
    // the app closes - rather than a handler that swallows the press and leaves them pressing it
    // again. What it undoes, and in what order, is AppState.goBack. A modal sheet is not in that
    // list: Compose's own ModalBottomSheet takes back before this ever sees it.
    BackHandler(enabled = appState.canGoBack) { appState.goBack() }
    // A look at Insights ends when the reader leaves it, so the mark is taken on the way out and
    // never on the way in. `MainActivity` takes the same mark when the app goes to the
    // background, which is the other way a look ends. See AppState.markInsightsSeen.
    val destination = appState.destination
    LaunchedEffect(destination) {
        if (destination != AppDestination.INSIGHTS) return@LaunchedEffect
        // Runs on leaving, which is when this effect is cancelled and disposed.
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            appState.markInsightsSeen()
        }
    }
    // What each page has open lives on AppState rather than anywhere below this line - see
    // PageState, which explains why the two shells below cannot hold it between them.
    CompositionLocalProvider(
        LocalFoldPosture provides posture,
        LocalWindowWidth provides windowWidth,
        // Remembered on the state rather than rebuilt each frame: a new lambda every recomposition
        // is a new value for a static local, which invalidates every reader of it - and the readers
        // here are every ticker on every card on the page.
        LocalOpenStock provides remember(appState) { appState::openStock },
    ) {
      // Inside the provider since 5.0: outside it the sheet read the default posture and always laid
      // out for the cover screen, and its full-screen chart with it.
      appState.openStockTicker?.let { ticker ->
          StockSheet(ticker, appState, onDismiss = appState::closeStock)
      }
      // The ground, drawn once across the whole window - the rail included - because a ground
      // that began at the page's edge would put a seam down the side of every wide window.
      Box(
          Modifier
              .fillMaxSize()
              // Plain paper or ink since the 2026-11 redesign: no lights behind the page and no
              // band down the rail. One flat ground for the whole window still means no seam.
              .background(MaterialTheme.colorScheme.background),
      ) {
        AppContent(appState)
      }
    }
}

/** Between the header above the line and the first card under it. */
private val StatusLineGap = 6.dp

/**
 * One line for everything the app is doing or has just done.
 *
 * A running action always wins the line. `busyLabel` and `statusMessage` describe the same activity
 * a moment apart - `runAction` sets the first, then clears it and sets the second - so showing them
 * in one place is what stops the header saying "Fetching prices" above a page while a tick sits
 * beside it reporting the previous fetch.
 *
 * A run started with `announce = false` says neither, and the line stays empty from the press until
 * whatever it opens arrives. What it does not suppress is a failure: an action that produced nothing
 * has to say why somewhere, and there is nowhere else.
 *
 * Whether it worked is carried by one tinted glyph, exactly as the toast carried it - a tick or a
 * cross, and nothing while the run is still going, because the bar over this line is already saying
 * that. Colouring the text would make every routine confirmation the loudest thing on a screen that
 * raises one after almost every tap.
 *
 * **It is drawn by [Screen] now, under the page's own name**, rather than in a band above the page.
 * The band is gone and the page starts at the top of the window, so there is nowhere above the page
 * left to be. It is still outside the scroll, which is the property that mattered: a message landing
 * while the reader is halfway down a page is never announced off screen.
 *
 * One thing did change with the move. This sits inside the page's theme, so an undo's label - the
 * one thing on this line allowed to carry `primary` - wears the hue of the page it was raised on
 * rather than cyan everywhere. That is the accent scheme working as written: what a figure means
 * never moves, and chrome takes the colour of where it is.
 */
@Composable
internal fun AppStatusLine(
    appState: AppState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A quiet run is running with nothing to say: the screen that started it is already saying it,
    // and the bar under this header is still drawn from `busyLabel` itself.
    // An analysis says itself through RunProgress above this line, with its batches and its clock,
    // so its busy label is not said a second time here.
    val busy = appState.busyLabel?.takeIf {
        appState.busyAnnounced && appState.analysisStatus != AnalysisStatus.RUNNING
    }
    val message = appState.statusMessage
    val text = busy ?: message?.text
    val stage = when {
        busy != null -> StatusStage.WORKING
        else -> message?.stage
    }
    AnimatedVisibility(
        visible = text != null && stage != null,
        // Height as well as opacity. The line has a row of its own, so without this the page jumps
        // a line the instant a message lands and again when it clears - which reads as the content
        // twitching rather than as an announcement.
        enter = ExpandIn,
        exit = CollapseOut,
        modifier = modifier,
    ) {
        // Held from the last non-null pair so the line fades out reading what it read, rather than
        // emptying a frame before it goes.
        val shown = remember { mutableStateOf(text.orEmpty() to (stage ?: StatusStage.DONE)) }
        if (text != null && stage != null) shown.value = text to stage
        val (label, glyph) = shown.value
        // Only ever on the live message, never on the one held for the fade: a button on a line
        // that is already leaving is a button whose press lands on nothing.
        val undo = message?.undo?.takeIf { busy == null }
        Row(
            Modifier
                // A working line reflects live state and there is nothing to dismiss; the other two
                // are messages, and a message the reader has finished with should go when tapped.
                // A working line reflects live state and there is nothing to dismiss. Neither is a
                // line carrying an undo: the whole row being tappable beside a word that says
                // "Undo" is two targets one of which quietly throws the offer away.
                .then(
                    if (glyph == StatusStage.WORKING || undo != null) {
                        Modifier
                    } else {
                        Modifier.clickable(onClick = onDismiss)
                    },
                )
                // Inside the animated content, so the gap arrives and leaves with the message
                // rather than being held open under an idle header.
                .padding(top = StatusLineGap)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusGlyph(glyph)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = if (glyph == StatusStage.FAILED) palette.loss else palette.muted,
                textAlign = TextAlign.Start,
                // Two lines is the ceiling, as it was on the toast. Anything needing more than that
                // is a screen, not a status line, and a provider's own error can run to a paragraph.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // fill = false, or a three-word confirmation is padded out to the full width and
                // the glyph beside it ends up a long way from the words it qualifies.
                // Announced as it changes: this line is how the app says what it just did, and a
                // screen reader never heard it.
                modifier = Modifier
                    .weight(1f, fill = false)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (undo != null) {
                Spacer(Modifier.weight(1f))
                UndoButton(
                    label = undo.label,
                    message = message,
                    onClick = {
                        undo.action()
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * Undo, as an ink capsule with a ring that empties over the seconds the offer stands - so the
 * reader can see how long they have rather than finding the button gone. The ring restarts for
 * each new message, and holds full with reduce motion on.
 */
@Composable
private fun UndoButton(label: String, message: StatusMessage?, onClick: () -> Unit) {
    val p = palette
    val still = LocalReduceMotion.current
    val left = remember(message) { Animatable(1f) }
    LaunchedEffect(message, still) {
        if (!still) left.animateTo(0f, tween(StatusUndoMilliseconds.toInt(), easing = LinearEasing))
    }
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .pressScale(press)
            .height(34.dp)
            .clip(Kit.Capsule)
            .background(p.ink)
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = p.onInk)
        Canvas(Modifier.size(22.dp)) {
            val stroke = 3.dp.toPx()
            drawCircle(p.onInk.copy(alpha = 0.25f), radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
            drawArc(p.onInk, startAngle = -90f, sweepAngle = 360f * left.value, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun StatusGlyph(stage: StatusStage) {
    val p = palette
    when (stage) {
        // A small cobalt spinner: the line is the only thing saying the app is busy now that the
        // generic progress bar under the header is reserved for an analysis.
        StatusStage.WORKING -> {
            val still = LocalReduceMotion.current
            val turn = if (still) 0f else {
                val t = rememberInfiniteTransition(label = "statusSpin")
                val v by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spin")
                v
            }
            Canvas(Modifier.size(StatusGlyphSize)) {
                val stroke = 2.5.dp.toPx()
                drawArc(p.hero, startAngle = turn, sweepAngle = 270f, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        // The message says what happened; a reader announcing the tone as well says it twice.
        StatusStage.DONE -> Box(
            Modifier.size(StatusGlyphSize + 4.dp).clip(Kit.Capsule).background(p.gain),
            contentAlignment = Alignment.Center,
        ) { Text("✓", color = Color.White, style = MaterialTheme.typography.labelSmall) }
        StatusStage.FAILED -> Box(
            Modifier.size(StatusGlyphSize + 4.dp).clip(Kit.Capsule).background(p.loss),
            contentAlignment = Alignment.Center,
        ) { Text("!", color = Color.White, style = MaterialTheme.typography.labelSmall) }
    }
}

/** Smaller than an inline icon: it sits against body text, not against a control. */
private val StatusGlyphSize = 16.dp

@Composable
private fun AppContent(appState: AppState) {
    // How long the header keeps a message. A confirmation gets out of the way on its own; a failure
    // does not, because it is the one kind worth reading twice and the one kind that can arrive
    // while the reader is looking somewhere else - a provider's refusal is often the only account
    // of why nothing happened. It waits to be tapped instead. Keyed on the message, so a second
    // outcome arriving cancels the first one's clock rather than clearing the new line early.
    LaunchedEffect(appState.statusMessage) {
        val message = appState.statusMessage ?: return@LaunchedEffect
        if (message.stage != StatusStage.DONE) return@LaunchedEffect
        delay(if (message.undo != null) StatusUndoMilliseconds else StatusDoneMilliseconds)
        appState.consumeStatusMessage()
    }

    // **Every side but the top.** The page runs up behind the status bar on purpose, so its own
    // ground carries on up behind the clock and the battery instead of stopping under a strip of
    // chrome. What keeps the title out from under the clock is [PageHeader], which pads itself by
    // the top inset; it reads `safeDrawing` rather than `statusBars` so a tall cutout is cleared too.
    //
    // A plain box since 5.0, where a transparent Material `Scaffold` stood: it carried no bar, no
    // snackbar and no colour, and its only job - holding these insets - is this one modifier. The
    // window's ground is already under it; see appGround.
    Box(
        Modifier.fillMaxSize().windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        ),
    ) {
        DestinationSwitch(appState)
    }
}

/**
 * The page the reader is on, changed in place.
 *
 * **No pager since 2026-11-11.** The five pages used to sit side by side in a `HorizontalPager`: a
 * press slid the whole window sideways across every page in between, header and all, and on the
 * cover screen a finger could drag them. That was the pre-5.0 navigation carried over untouched,
 * and it is not what the design draws. The mockups show one header, identical on every page, with
 * only the large name moving, and a page whose tiles pop in - and nothing anywhere that slides
 * sideways. So a change of tab is now two things happening in place: the emphasis travels along
 * the tab row ([TabTravel]), and the page is built afresh so its tiles arrive on `popIn`.
 *
 * **Only the current page is composed**, keyed on the destination so the one being left is
 * disposed whole. That is what makes the tiles pop on every arrival rather than only the first,
 * and it is why nothing a page holds may live in a `remember` - see `PageState`, whose rule this
 * now leans on at every press rather than only at a fold. The scroll position comes back from
 * there too.
 *
 * With one page composed there is no neighbour to answer a press meant for another tab, and no
 * sideways scroller above a page for a `bringIntoView` or a child's leftover fling to turn - the
 * guards that existed for both went with the pager.
 */
@Composable
private fun DestinationSwitch(appState: AppState) {
    val destination = appState.destination
    val still = LocalReduceMotion.current
    val travel = remember { TabTravel(destination) }
    LaunchedEffect(destination) { travel.goTo(destination, still) }
    CompositionLocalProvider(LocalTabTravel provides travel) {
        key(destination) {
            Box(Modifier.fillMaxSize()) {
                DestinationScreen(destination, appState)
            }
        }
    }
}

@Composable
private fun DestinationScreen(
    destination: AppDestination,
    appState: AppState,
) {
    // The one place a page is built, and so the only place that knows which destination is being
    // composed. Published from here rather than read from `AppState` inside `Screen`, so a page only
    // ever sees a press meant for it.
    val request = appState.scrollToTopRequest
    CompositionLocalProvider(
        LocalScrollToTop provides (request?.takeIf { it.first == destination }?.second ?: 0),
    ) {
        // The page's own hue, put into `primary` and `secondary` for the length of this page and no
        // further. Here rather than around the whole shell, because the header's status line and any
        // sheet raised over the top are not on a page and should not take the hue of whatever
        // happens to be behind them.
        PageTheme(destination.accent) {
            when (destination) {
                AppDestination.ANALYZE -> AnalyzeScreen(appState)
                AppDestination.RESULTS -> ResultsScreen(appState)
                AppDestination.INSIGHTS -> InsightsScreen(appState)
                AppDestination.PORTFOLIO -> PortfolioScreen(appState)
                AppDestination.SETTINGS -> SettingsScreen(appState)
            }
        }
    }
}

/**
 * How long a confirmation stays on the line before it clears itself.
 *
 * Material's own short snackbar, which is what these messages used to be shown for.
 */
private const val StatusDoneMilliseconds = 4_000L

/**
 * How long a message carrying an undo stays before it clears itself.
 *
 * Longer than an ordinary confirmation on purpose: this is the only way back from something the
 * reader cannot simply redo, and four seconds asked them to have already noticed, read and decided
 * on it in the time it takes to glance up from what they just pressed.
 */
private const val StatusUndoMilliseconds = 10_000L

/** Read by the navigation, on both layouts. */
internal val AppDestination.icon: ImageVector
    get() = when (this) {
        AppDestination.ANALYZE -> Icons.Outlined.AutoGraph
        AppDestination.RESULTS -> Icons.Outlined.Assessment
        AppDestination.INSIGHTS -> Icons.Outlined.Insights
        // A wallet rather than a chart: the other three destinations are all readings of the market,
        // and this one is the only place holding anything of the user's own.
        AppDestination.PORTFOLIO -> Icons.Outlined.AccountBalanceWallet
        AppDestination.SETTINGS -> Icons.Outlined.Settings
    }

/** Read by the navigation and by [PageHeader], which is what puts the page's own glyph beside its
 * name. One property, so the two cannot disagree about what a destination looks like. */
internal val AppDestination.selectedIcon: ImageVector
    get() = when (this) {
        AppDestination.ANALYZE -> Icons.Filled.AutoGraph
        AppDestination.RESULTS -> Icons.Filled.Assessment
        AppDestination.INSIGHTS -> Icons.Filled.Insights
        AppDestination.PORTFOLIO -> Icons.Filled.AccountBalanceWallet
        AppDestination.SETTINGS -> Icons.Filled.Settings
    }
