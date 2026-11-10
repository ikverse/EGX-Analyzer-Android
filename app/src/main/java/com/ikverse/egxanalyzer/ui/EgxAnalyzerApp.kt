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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
    // composition and a fold no longer swaps one subtree for another. Read by exactly one thing -
    // see LocalTabsSettled. The pager below writes it.
    val tabsSettled = remember { mutableStateOf(true) }
    // Above the shells and not inside either of them: back means the same thing whichever one is
    // drawn, and a handler in each would be one rule written twice. Enabled only when there is
    // something to undo, so a reader with nothing outstanding gets the system's own behaviour -
    // the app closes - rather than a handler that swallows the press and leaves them pressing it
    // again. What it undoes, and in what order, is AppState.goBack. A modal sheet is not in that
    // list: Compose's own ModalBottomSheet takes back before this ever sees it.
    BackHandler(enabled = appState.canGoBack) { appState.goBack() }
    // A look at Insights ends when the reader leaves it, so the mark is taken on the way out and
    // never on the way in - the pager composes the neighbouring pages, so arriving is not evidence
    // anybody turned to the tab. `MainActivity` takes the same mark when the app goes to the
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
        LocalTabsSettled provides tabsSettled,
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
        // Swiping between tabs on the cover screen only: unfolded, a sideways drag belongs to the
        // tables that scroll sideways, and the tabs are a press away in the header.
        AppContent(appState, swipeBetweenTabs = windowWidth == WindowWidth.COMPACT)
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
private fun AppContent(appState: AppState, swipeBetweenTabs: Boolean) {
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
        DestinationPager(appState, swipeBetweenTabs)
    }
}

/**
 * The destinations laid out side by side, swiped through as well as tapped.
 *
 * The bar's items and the pager are two ways of moving the same pointer, so each follows the other:
 * a tap animates the pager to the page, and a swipe names the page it lands on. One rule keeps the
 * two from chasing each other round - **the bar follows every page turn except the ones a travel
 * started here passes over.** Nothing else is held back, and nothing else needs to be: a page the
 * reader turned is an arrival however it turned, under their hand, on the fling, or as the pager
 * settled.
 *
 * **The guard names the travel and not the gesture, and that is the heart of it.** It used to name
 * the gesture - a flag raised on `DragInteraction.Start` and lowered when the pager reported itself
 * at rest - and that cannot be read reliably from where this code stands. Compose runs a swipe as
 * two scroll sessions, the finger's and the settling fling's, and `isScrollInProgress` reads false
 * in the gap between them. The flag came down in that gap, so a flick - where the page is decided by
 * velocity on the fling rather than by crossing the halfway mark under the hand - turned its page
 * with the guard already closed. The turn was swallowed and the bar stayed lit on the tab the reader
 * had just left, while a slow drag past the halfway mark still worked, which is what made it look
 * intermittent. The same flag dropped a tap made while a swipe was still settling.
 *
 * Keyed on the pager rather than on the destination, which is the older half of the same lesson:
 * keyed on the destination, a swipe restarted the very effect that does the scrolling - because a
 * swipe publishes its own arrival - and the restarted copy compared a target read at recomposition
 * against a page read a frame or more later, so a second swipe arriving inside that window scrolled
 * the reader back to the page they had just left.
 *
 * **Every width since 5.0, swiped on the cover screen only** ([swipe]). Unfolded, the pager still
 * carries the travel between tabs - a press on a tab's name in the header - but takes no drag: a
 * page-wide horizontal drag there belongs to the tables that scroll sideways.
 */
@Composable
private fun DestinationPager(appState: AppState, swipe: Boolean) {
    val destinations = AppDestination.entries
    val pager = rememberPagerState(
        initialPage = destinations.indexOf(appState.destination),
        pageCount = { destinations.size },
    )
    // The page a travel started here is heading for, and null whenever nothing in this shell is
    // moving the pager. The one thing held back from the bar, and the only thing that has to be:
    // the pages a tap crosses on its way are not arrivals, and neither is the page a travel is
    // abandoned on when a second send replaces it - left to speak, that one wins the race against
    // the send that cancelled it and takes the reader somewhere neither send named.
    //
    // Written by the only coroutine that scrolls, before it suspends. Nothing about how Compose
    // splits a gesture into scroll sessions can reach it, which is exactly what the flag it replaces
    // could not say - see the note above.
    val travelling = remember { mutableStateOf<Int?>(null) }
    // Whether the pages have stopped moving, for the one thing outside this function that has to
    // know a page composing is not a reader arriving on it. Every way the pager moves is covered,
    // a travel and a hand alike, because it is read off the pager itself rather than off the flags
    // above. Put back on the way out: folding the phone disposes this whole subtree for the rail's
    // AnimatedContent, and a flag left false there would hold every later reveal forever.
    val settled = LocalTabsSettled.current
    LaunchedEffect(pager, settled) {
        snapshotFlow { pager.isScrollInProgress }.collect { settled.value = !it }
    }
    DisposableEffect(settled) { onDispose { settled.value = true } }

    LaunchedEffect(pager) {
        launch {
            // Every page turn but a travel's. Under the reader's hand the current page turns over at
            // the halfway mark, so the bar lights up the tab being dragged towards rather than
            // waiting for the pages to settle; on a flick it turns over during the fling; and either
            // way the page it comes to rest on is the last one published.
            //
            // The page and the guard are read in the same snapshot, so a turn can never be delivered
            // against a guard that changed after the turn was taken.
            snapshotFlow { pager.currentPage to travelling.value }.collect { (page, travel) ->
                if (travel == null) appState.navigate(destinations[page])
            }
        }
        // Tapped, or sent here by the app itself - a run finishing, a card carrying a press to its
        // counterpart. `collectLatest` so a second tap during the first one's travel wins rather than
        // being undone when the first arrives, and so a cancelled travel always has a replacement
        // scroll to snap it rather than being left parked between two pages.
        snapshotFlow { destinations.indexOf(appState.destination) }.collectLatest { target ->
            // Already there, and settled rather than mid-travel: nothing to do, and this is also what
            // keeps first composition from scrolling the pager to the page it opened on.
            if (pager.currentPage == target && pager.currentPageOffsetFraction == 0f) {
                return@collectLatest
            }
            // Raised before anything here suspends. `collectLatest` starts each block undispatched,
            // so a replacement raises it again in the same continuation that the cancelled block
            // lowered it in, and the page an abandoned travel was parked on never gets a frame in
            // which to name itself an arrival.
            travelling.value = target
            try {
                // A travel this shell starts outranks nothing and is outranked by nothing except a
                // hand: it takes the pager from its own predecessor's settling, and from the reader's
                // fling, so a tab tapped while the pages are still coasting is answered rather than
                // dropped.
                pager.animateScrollToPage(target)
            } catch (_: CancellationException) {
                // Refused rather than interrupted: a drag holds the pager at a priority a scroll
                // started here cannot take. A real cancellation still has to pass.
                currentCoroutineContext().ensureActive()
                // Let them have it - but the send still has to end somewhere the bar and the pager
                // agree on. Their gesture names where they land, unless it puts the pager back on the
                // page it set out from: that turns no page and so names nothing, and the bar would be
                // left lit on a tab the pager never travelled to. So the guard comes down first, and
                // then wherever the gesture comes to rest is published whether it turned a page or
                // not. Read in the gap between the finger and the fling this publishes early, and the
                // page the fling then turns is published after it - either way the two agree.
                travelling.value = null
                snapshotFlow { pager.isScrollInProgress }.first { !it }
                appState.navigate(destinations[pager.currentPage])
            } finally {
                travelling.value = null
            }
        }
    }
    HorizontalPager(
        pager,
        Modifier.fillMaxSize(),
        // The neighbours are built while the reader is sitting still rather than on the first frame
        // of their drag. A screen's first composition is expensive - measured at 150ms and worse on
        // a cold start, against a budget of 8ms - and with nothing held beyond the viewport that
        // whole cost landed in the middle of the gesture. It is the same work either way; this only
        // moves it somewhere there is no gesture for it to stutter.
        beyondViewportPageCount = 1,
        userScrollEnabled = swipe,
    ) { page ->
        Box(Modifier.fillMaxSize().sidewaysGesturesStayOnThePage()) {
            DestinationScreen(destinations[page], appState)
        }
    }
}

/**
 * Keeps a sideways gesture that began on something inside the page from becoming a page turn.
 *
 * **This is the fix of 2026-09-12 - "results to insights I have to scroll a long distance and it
 * jumps two pages to portfolio".** A page here is full of things that scroll sideways in their own
 * right: the stack of readings behind a session (`SavedRunStack`), the tables a wide row is read in
 * (`scrollableRow`), the outcome chips on Insights, the sparkline in a recommendation. Compose hands
 * whatever a child scroller could not use **to its parent within the same gesture** - the leftover
 * distance through `onPostScroll`, and the leftover velocity of the fling through `onPostFling` -
 * and the pager's own `scrollable` takes both. So a swipe that started on one of those spent its
 * first inches running that thing to its end, which is the long distance, and then poured the rest
 * of the finger's travel into the pager. A drag is not clamped to one page, so the remainder plus
 * the child's leftover fling carried the reader past the tab they were swiping towards and onto the
 * one after it.
 *
 * **Only what comes up from inside the page is eaten, and only sideways.** A swipe over ordinary
 * page content never passes through here at all - the pager's own gesture detector owns it, above
 * this connection - so page swiping is untouched, and what stops is one gesture doing two jobs.
 * Vertical is passed on whole, because three things upstream live on it: the header's collapse, the
 * navigation bar getting out of the way, and pull to refresh.
 *
 * Every source is eaten rather than just `UserInput`. A child flung to its end goes on dispatching
 * leftovers for the length of its own settling animation, and those are the same gesture arriving a
 * few frames later.
 */
@Composable
private fun Modifier.sidewaysGesturesStayOnThePage(): Modifier {
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = Offset(available.x, 0f)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(available.x, 0f)
        }
    }
    return this.nestedScroll(connection)
}

@Composable
private fun DestinationScreen(
    destination: AppDestination,
    appState: AppState,
) {
    // The one place both shells build a page, and so the only place that knows which destination is
    // being composed. Published from here rather than read from `AppState` inside `Screen`, because
    // the pager keeps the neighbouring pages composed: a page that read the request directly would
    // answer a press meant for the tab beside it and throw away a scroll position nobody touched.
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
