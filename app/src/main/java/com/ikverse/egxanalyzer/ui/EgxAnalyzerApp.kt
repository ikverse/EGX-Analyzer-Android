package com.ikverse.egxanalyzer.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikverse.egxanalyzer.R
import com.ikverse.egxanalyzer.ui.theme.extraColors
import kotlin.math.roundToInt
import androidx.window.layout.FoldingFeature
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

    // A rail beside the page on anything wider than a phone, and a floating bar over the page on a
    // phone, at the same breakpoint the suite used when it chose for itself. The two are laid out
    // differently enough - one takes width from the page, the other takes nothing at all - that they
    // are separate shells rather than one shell with a choice of navigation in it.
    val rail = windowWidth != WindowWidth.COMPACT
    // Held here rather than inside the pages: the bar is drawn over them, and it has to survive a
    // change of destination.
    val navBarVisible = remember { mutableStateOf(true) }
    // Held here for the same reason, and read by exactly one thing - see LocalTabsSettled. The pager
    // below writes it; beside a rail nothing does, and nothing there needs to.
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
    // Drawn once for the whole app, above both shells, for the reason the handler above is: a
    // ticker is pressable on four tabs and one sheet must serve all four. It is outside the
    // CompositionLocalProvider's content but inside the same composition, so it survives a fold
    // exactly as the state naming it does. See AppState.openStockTicker.
    appState.openStockTicker?.let { ticker ->
        StockSheet(ticker, appState, onDismiss = appState::closeStock)
    }
    // Whether the header is on screen, worked out once above both shells.
    //
    // It leaves on the same scroll that takes the floating pill and comes back with it, and it stays
    // put whenever the app has something to say - the status line lives in it, so a message landing
    // on a scrolled page would otherwise be announced off screen.
    //
    // Read here rather than inside `AppContent` because the rail's mark travels against the same
    // signal from outside the header entirely (see the rail branch below), and two copies of this
    // rule would let the two part company mid-travel. It costs the shell nothing that was not
    // already being paid: `AppContent` has always read both of these properties.
    val headerVisible = navBarVisible.value ||
        appState.busyLabel != null ||
        appState.statusMessage != null
    // What each page has open lives on AppState rather than anywhere below this line - see
    // PageState, which explains why the two shells below cannot hold it between them.
    CompositionLocalProvider(
        LocalWindowWidth provides windowWidth,
        LocalNavBarVisible provides navBarVisible,
        LocalTabsSettled provides tabsSettled,
        // Remembered on the state rather than rebuilt each frame: a new lambda every recomposition
        // is a new value for a static local, which invalidates every reader of it - and the readers
        // here are every ticker on every card on the page.
        LocalOpenStock provides remember(appState) { appState::openStock },
    ) {
        if (rail) {
            // **The mark does not leave with the header here.** On the phone the header collapses
            // and takes the name, the status line and the mark up out of the window together, which
            // is right: there is nowhere else on that layout for a mark to be. Beside a rail there
            // is - the rail's own top gap, which `RailTopInset` already holds open so that the rail
            // reads as the header band turning the corner. With the band gone the mark is what is
            // left holding that corner, so it travels left into the gap and stays put there, at the
            // height it already had, rather than vanishing along with the words.
            //
            // It is therefore drawn **here** and not in the header: it has to outlive a collapse,
            // and it has to cross the start edge of the content pane, which is exactly where the
            // rail ends and what the pane clips at.
            var railWidth by remember { mutableIntStateOf(0) }
            var shellOrigin by remember { mutableStateOf(Offset.Zero) }
            // Where the header holds the mark's place. Window coordinates, measured off the slot the
            // header leaves rather than worked out from its paddings: a figure derived from the
            // header's own arithmetic would be wrong the first time a status line, a font scale or a
            // cutout changed how tall that row is. Null until the first layout, and nothing is drawn
            // until then - a mark placed at the origin for one frame is a glyph flashing in the
            // corner of every cold start.
            var markAnchor by remember { mutableStateOf<Offset?>(null) }
            val markSize = with(density) { AppMarkSize.roundToPx() }
            Box(Modifier.fillMaxSize().onGloballyPositioned { shellOrigin = it.positionInWindow() }) {
                NavigationSuiteScaffoldLayout(
                    navigationSuite = {
                        AppRail(appState, Modifier.onSizeChanged { railWidth = it.width })
                    },
                    navigationSuiteType = NavigationSuiteType.NavigationRail,
                ) {
                    // The suite scaffold used to paint this; the layout does not. Without it the
                    // window background shows through behind the status and navigation bars as a
                    // pale band. It is the chrome colour rather than the page's, because this is
                    // what shows behind the system bars, and through the rounded corners where the
                    // page stops short of them.
                    Surface(
                        // The layout does not do this for us the way the full NavigationSuiteScaffold
                        // does, and without it the page keeps clear of the gesture strip that the
                        // rail beside it is already holding: a second empty band, the width of the
                        // strip, in the chrome colour, between the bottom of the page and the first
                        // row of icons.
                        Modifier.fillMaxSize().consumeWindowInsets(
                            NavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start),
                        ),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        AppContent(
                            appState,
                            rail = true,
                            headerVisible = headerVisible,
                            // Recorded only while the header is at rest showing. It reports on every
                            // layout pass, and a collapse is a run of them: left ungated, the anchor
                            // would be dragged upward frame by frame as the header left, and the
                            // mark would set off from wherever the header had got to rather than
                            // from where it sits.
                            onMarkAnchor = { if (headerVisible) markAnchor = it },
                        )
                    }
                }
                val anchor = markAnchor
                if (anchor != null && railWidth > 0) {
                    // Only x moves. The mark keeps the height it has in the header, which is what
                    // puts it in the rail's top gap rather than level with the first destination.
                    val restX = (railWidth - markSize) / 2
                    val travelled by animateIntAsState(
                        if (headerVisible) (anchor.x - shellOrigin.x).roundToInt() else restX,
                        // The header's own tween, so the two are one movement rather than two
                        // pieces of chrome leaving at slightly different moments.
                        animationSpec = tween(HeaderMoveMilliseconds),
                        label = "mark travel",
                    )
                    AppMark(
                        Modifier.offset {
                            IntOffset(travelled, (anchor.y - shellOrigin.y).roundToInt())
                        },
                    )
                }
            }
        } else {
            // The bar floats over the page rather than sitting under it, so the page is the full
            // height of the window and nothing is laid out around the bar. It consumes no insets of
            // its own: both it and the page clear the gesture strip separately, and neither hands
            // the other anything when the bar hides.
            Box(Modifier.fillMaxSize()) {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    // No anchor: on this layout the mark is drawn inside the header and leaves with
                    // it, because there is no rail for it to travel into.
                    AppContent(appState, rail = false, headerVisible = headerVisible)
                }
                FloatingNavBar(appState, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** Icons carry the rail on a glance, so they are a size up from the Material default of 24dp. */
private val NavigationIconSize = 28.dp

/**
 * The pill's own height.
 *
 * Material leaves a bar 80dp. This one is drawn over the page rather than under it, so its height is
 * page it is covering, and a shorter pill covers less of it.
 */
private val NavigationBarHeight = 74.dp

/** How far the pill is held in from the sides of the window, which is what makes it read as a pill. */
internal val PillSideMargin = 12.dp

/** Room under the pill, above whatever the gesture strip claims, so it floats rather than sits. */
internal val PillBottomMargin = 10.dp

/**
 * The bottom of a page the pill is covering.
 *
 * Nothing is laid out around a floating bar, so a page that padded itself by nothing would end its
 * last card underneath one. Deliberately not the gesture strip's inset as well: the page already
 * clears that through `safeDrawing`, and the pill is measured up from the same line.
 *
 * Published because a page has to know it twice over - to hold its content clear of the pill, and to
 * decide whether hiding the pill is worth it at all. A page with less than this left to scroll shows
 * nothing new by hiding it.
 */
internal val NavBarFootprint = NavigationBarHeight + PillBottomMargin

/** A shade under the rail's 28dp: the pill has to leave room for a label under the glyph. */
private val BarIconSize = 26.dp

/**
 * A size down from Material's 12sp `labelMedium`.
 *
 * The pill is [PillSideMargin] narrower on each side than the bar it replaced, and five destinations
 * divide what is left of a 411dp cover screen into slots too narrow for "Portfolio" at the full
 * size. Shrinking the label rather than dropping it keeps all five named.
 */
private val BarLabelSize = 11.sp

/**
 * How far the rail's items sit below the top of the window.
 *
 * A rail item is an icon stacked over a label, so left at the top its icon lands beside the blank
 * space above the page title rather than beside the title itself. This drops the column until the
 * first icon meets the heading next to it: past the header band, the page's own top padding, and
 * half a heading. The gap it leaves is beside the band, in the same colour, so the rail reads as the
 * band turning the corner rather than as a rail that starts late.
 */
private val RailTopInset = 70.dp

/** Sized to the name beside it rather than to the navigation grid, which it does not belong to. */
private val AppMarkSize = 24.dp

/**
 * Says which app this is, and what it is doing, above whatever page is showing.
 *
 * It leaves on the same scroll that takes the navigation and comes back with it, on both layouts -
 * see the call site, which explains why one signal drives the two. The mark is the launcher artwork
 * reduced to a single shape: the full tile in the rail read as a sticker among the flat navigation
 * glyphs, and it only ever appeared on the wide layout, where this says the same thing on both.
 *
 * @param onMarkAnchor given beside a rail, and what makes the mark outlast this header. The row
 *   holds the mark's place open with a spacer and reports where that place is; the shell draws the
 *   real mark over the top and slides it into the rail as this collapses. Null on the phone, where
 *   the mark is drawn inline and leaves with everything else.
 *
 * **The status line lives here now**, where a floating toast used to carry it. Two things put it
 * here. It was the only piece of chrome that had to be lifted clear of the navigation bar and
 * lowered again as that bar came and went, which is a whole mechanism existing to keep one
 * transient message off one transient bar. And an app that says something after almost every tap
 * was answering from the far end of the screen from the button that had just been pressed - on the
 * unfolded panel, the better part of a foot away from it.
 *
 * @see AppStatusLine for what it draws, and [StatusStage] for how long each kind stays.
 */
@Composable
private fun AppHeader(
    appState: AppState,
    onDismissStatus: () -> Unit,
    onMarkAnchor: ((Offset) -> Unit)? = null,
) {
    // A step up from the page it sits on, which separates it without a rule underneath as well.
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        // The window's own width, not this header's, and not measured again here: the shell already
        // works it out to choose a rail or a bar, and LocalWindowWidth is published so that two
        // parts of the app cannot disagree about where the line falls.
        val beside = LocalWindowWidth.current != WindowWidth.COMPACT
        // No arrangement spacing: the gap above a message belongs to the message, and spacing here
        // would hold 6dp open under the name on every idle compact header. It is applied inside the
        // line instead, where it collapses along with it.
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onMarkAnchor == null) {
                    AppMark()
                } else {
                    // The mark's place, held open so the name and the status line sit exactly where
                    // they would with the glyph in the row. The glyph itself is drawn by the shell,
                    // over this, and reported to it from here.
                    Spacer(
                        Modifier
                            .size(AppMarkSize)
                            .onGloballyPositioned { onMarkAnchor(it.positionInWindow()) },
                    )
                }
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                // Beside the name where there is width for it, taking the surplus rather than a
                // share of it - the name is what it is, and everything left over is the message's.
                // Below 600dp the cover screen has under 200dp spare after a 22sp title, which is
                // most of these messages truncated, so there the line drops underneath instead.
                if (beside) {
                    AppStatusLine(
                        appState = appState,
                        alignEnd = true,
                        onDismiss = onDismissStatus,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (!beside) {
                AppStatusLine(
                    appState = appState,
                    alignEnd = false,
                    onDismiss = onDismissStatus,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The app's own mark, with the aurora moving through it.
 *
 * One composable for both layouts: inline in the phone's header, and drawn over the shell on the
 * wide one so it can outlive the header and travel into the rail. A second copy for the travelling
 * one is how the two would end up different marks.
 *
 * **The gradient is painted through the glyph rather than into the asset.** `ic_egx_notification` is
 * a one-colour vector and stays one; the layer below it is drawn, then a rectangle of the brush is
 * laid over it with `SrcIn`, which keeps the brush inside whatever the vector covers. The offscreen
 * compositing strategy is not optional - without a layer for the blend to be confined to, that
 * rectangle lands over the header instead of inside the mark.
 *
 * **`phase` is read inside the draw lambda, and that is what makes a permanent animation
 * affordable.** This is chrome that never leaves the screen, so the sweep runs for as long as the
 * app is in front - and a state read in the composition phase would recompose the header, and
 * everything the header's own recomposition reaches, on every frame of it. Read here the invalidation
 * is confined to the draw phase: a frame costs one 24dp glyph redrawn and no recomposition at all.
 * `arrivalFlash` in `CommonUi` makes the same argument from the other end, by composing itself only
 * while it is wanted.
 *
 * The tint underneath is the flat `primary` this mark used to be. Nothing normally sees it, since
 * `SrcIn` replaces it wholesale; it is what the mark falls back to rather than a blank space if the
 * blend is ever refused.
 */
@Composable
private fun AppMark(modifier: Modifier = Modifier) {
    val hues = extraColors.markAurora
    val sweep = rememberInfiniteTransition(label = "mark aurora")
    val phase by sweep.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // Eased rather than linear, because it reverses: a linear ramp changes direction at the
            // turn hard enough to be the one frame of this that catches the eye.
            tween(MarkSweepMilliseconds, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "sweep",
    )
    Icon(
        painterResource(R.drawable.ic_egx_notification),
        // The name is right beside it, and a reader announcing both says it twice.
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .size(AppMarkSize)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // A ramp several times the glyph, slid diagonally across it, so what a reader sees
                // is colour travelling *through* the mark. A ramp the size of the mark would change
                // the whole glyph's hue at once, which reads as a light being switched rather than
                // as one moving.
                val span = size.maxDimension * MarkSweepSpan
                val at = -span + (size.maxDimension + span) * phase
                drawRect(
                    Brush.linearGradient(
                        colors = hues,
                        start = Offset(at, at),
                        end = Offset(at + span, at + span),
                    ),
                    blendMode = BlendMode.SrcIn,
                )
            },
    )
}

/**
 * Twelve seconds each way, and deliberately that slow.
 *
 * This is on chrome that is always on screen, so the test it has to pass is that a reader never
 * catches it moving - they look up and the mark is a different colour from the one they remember.
 * Anything brisk enough to be seen as an animation would be the app's own name flashing beside a
 * page of figures, which is the last place in this app that should.
 */
private const val MarkSweepMilliseconds = 12_000

/** How many marks wide the ramp is. Enough that no more than one of its three hues is ever inside. */
private const val MarkSweepSpan = 3f

/**
 * How long the header takes to leave or return.
 *
 * Deliberately brief. See the note at the call site: this is also how long the page's own scroll
 * watcher spends ignoring the reader, because the movement changes the extent it reads.
 */
private const val HeaderMoveMilliseconds = 180

/** Between the name and a message sitting under it, on the layout where one does. */
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
 * Whether it worked is carried by one tinted glyph, exactly as the toast carried it. Colouring the
 * text would make every routine confirmation the loudest thing on a screen that raises one after
 * almost every tap - and this line now sits beside the app's own name, which is the last place that
 * should flash.
 */
@Composable
private fun AppStatusLine(
    appState: AppState,
    alignEnd: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A quiet run is running with nothing to say: the screen that started it is already saying it,
    // and the bar under this header is still drawn from `busyLabel` itself.
    val busy = appState.busyLabel?.takeIf { appState.busyAnnounced }
    val message = appState.statusMessage
    val text = busy ?: message?.text
    val stage = when {
        busy != null -> StatusStage.WORKING
        else -> message?.stage
    }
    AnimatedVisibility(
        visible = text != null && stage != null,
        // Height as well as opacity. On the compact layout the line has a row of its own, so
        // without this the header jumps a line taller the instant a message lands and shorter again
        // when it clears - which reads as the page below it twitching rather than as an
        // announcement. On the wide layout the row is already as tall as the title beside it, so
        // the expansion costs nothing there.
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
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
                // Inside the animated content, so the gap under the name arrives and leaves with
                // the message rather than being held open under an idle header.
                .padding(top = if (alignEnd) 0.dp else StatusLineGap)
                // Full width so the arrangement below can push the line against the header's end on
                // the wide layout. Capping the width instead left it stranded mid-header: a capped
                // row inside a weighted slot sits at the start of that slot, not at its end.
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, if (alignEnd) Alignment.End else Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusGlyph(glyph)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
                // Two lines is the ceiling, as it was on the toast. Anything needing more than that
                // is a screen, not a status line, and a provider's own error can run to a paragraph.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // fill = false, or a three-word confirmation is padded out to the full width and
                // the glyph beside it ends up a long way from the words it qualifies.
                modifier = Modifier.weight(1f, fill = false),
            )
            if (undo != null) {
                // A plain text button in the app's own accent, which is the one thing on this line
                // allowed to carry colour - the glyph is tinted and the words never are, and an
                // action the reader has seconds to notice is the exception that earns it.
                TextButton(
                    onClick = {
                        undo.action()
                        onDismiss()
                    },
                    contentPadding = PaddingValues(horizontal = Space.s, vertical = 0.dp),
                ) {
                    Text(undo.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun StatusGlyph(stage: StatusStage) {
    when (stage) {
        StatusStage.WORKING -> CircularProgressIndicator(
            modifier = Modifier.size(StatusGlyphSize),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        StatusStage.DONE -> Icon(
            Icons.Outlined.CheckCircle,
            // The message says what happened; a reader announcing the tone as well says it twice.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(StatusGlyphSize),
        )
        StatusStage.FAILED -> Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(StatusGlyphSize),
        )
    }
}

/** Smaller than an inline icon: it sits against body text, not against a control. */
private val StatusGlyphSize = 14.dp

/** Room between rail items, where Material leaves 4dp and the four destinations read as one block. */
private val RailItemGap = 16.dp

@Composable
private fun AppRail(appState: AppState, modifier: Modifier = Modifier) {
    // Material leaves a rail the page's own colour; this one carries the header's, so the two meet
    // as one piece of chrome around the page rather than as a rail that has vanished into it.
    // Destinations only - the app's mark is drawn over this rather than in it, so that it can be in
    // the header while there is one and here once the header has gone. See the rail shell.
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Spacer(Modifier.height(RailTopInset))
        AppDestination.entries.forEachIndexed { index, destination ->
            if (index > 0) Spacer(Modifier.height(RailItemGap))
            val selected = appState.destination == destination
            NavigationRailItem(
                selected = selected,
                // Pressing the destination you are already on means "back to the top" - the same
                // press every bottom bar on this platform answers that way. See AppState.scrollToTop.
                onClick = {
                    if (selected) appState.scrollToTop(destination) else appState.navigate(destination)
                },
                icon = { NavigationIcon(destination, selected) },
                label = { Text(destination.label) },
            )
        }
    }
}

/**
 * The phone's navigation: a pill lying on the page rather than a bar holding it up.
 *
 * Slightly see-through, so what is underneath carries on being visible through it and the pill reads
 * as sitting on the page rather than as a hole cut in it. A flat tint and not a blur - Compose has no
 * backdrop blur - so it is kept opaque enough that a dense card scrolling under it cannot make the
 * labels hard to read.
 */
@Composable
private fun FloatingNavBar(appState: AppState, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        // Slides away while a page is scrolled down and comes back on the way up. Only the pill
        // moves: the page is the full height of the window either way, so there is nothing to
        // collapse and nothing underneath waiting for the room.
        visible = LocalNavBarVisible.current.value,
        modifier = modifier
            // The strip is the pill's to clear, the same as it is the page's - neither is laid out
            // around the other.
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .padding(start = PillSideMargin, end = PillSideMargin, bottom = PillBottomMargin),
        enter = fadeIn() + slideInVertically { it },
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        FloatingSurface(
            // The same corner the action button above it takes, which is the page's own card radius.
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.height(NavigationBarHeight),
        ) {
            // No icon here: five destinations already divide a 411dp cover screen, and a sixth thing
            // taking width from them buys nothing the header above does not already say.
            Row(
                Modifier.fillMaxWidth().selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppDestination.entries.forEach { destination ->
                    val selected = appState.destination == destination
                    PillItem(
                        destination = destination,
                        selected = selected,
                        // As on the rail: already here means take me back to the top.
                        onClick = {
                            if (selected) appState.scrollToTop(destination) else appState.navigate(destination)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * One destination in the pill.
 *
 * Material's own `NavigationBarItem` asks for around 82dp - a fixed 16dp above and below a 32dp
 * indicator - and a bar that tall would cover a fifth of the page it floats over. Given less, it
 * overflows rather than shrinks, and the indicator came out tangent to the bar's top edge with the
 * label sitting on the bottom one. This is the same item built to the height available, so the block
 * sits centred with room on both sides.
 */
@Composable
private fun PillItem(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(IndicatorWidth, IndicatorHeight)
                .background(
                    if (selected) colors.secondaryContainer else Color.Transparent,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // A size down from the rail's, which has the room for it. At the pill's height an icon
            // over a label needs the smaller glyph or the label clips.
            NavigationIcon(
                destination,
                selected,
                BarIconSize,
                if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(IndicatorLabelGap))
        Text(
            destination.label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = BarLabelSize),
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Wide enough to hold the icon clear of a five-slot 411dp screen's edges, and no wider. */
private val IndicatorWidth = 60.dp

/** Material leaves 32dp around a 24dp icon; this holds the same margin around a 26dp one. */
private val IndicatorHeight = 34.dp

/** Close enough that the icon and its name read as one item, far enough that they do not touch. */
private val IndicatorLabelGap = 3.dp

@Composable
private fun NavigationIcon(
    destination: AppDestination,
    selected: Boolean,
    size: Dp = NavigationIconSize,
    // The rail's items colour their own content; the pill's are drawn by hand and have to say.
    tint: Color = LocalContentColor.current,
) {
    Icon(
        if (selected) destination.selectedIcon else destination.icon,
        contentDescription = destination.label,
        modifier = Modifier.size(size),
        tint = tint,
    )
}

@Composable
private fun AppContent(
    appState: AppState,
    rail: Boolean,
    headerVisible: Boolean,
    onMarkAnchor: ((Offset) -> Unit)? = null,
) {
    // Arriving somewhere new with the navigation still hidden reads as the bar having gone missing,
    // and the page that hid it is no longer on screen to bring it back.
    val navBarVisible = LocalNavBarVisible.current
    LaunchedEffect(appState.destination) { navBarVisible.value = true }

    // How long the header keeps a message. A confirmation gets out of the way on its own; a failure
    // does not, because it is the one kind worth reading twice and the one kind that can arrive
    // while the reader is looking somewhere else - a provider's refusal is often the only account
    // of why nothing happened. It waits to be tapped instead. Keyed on the message, so a second
    // outcome arriving cancels the first one's clock rather than clearing the new line early.
    LaunchedEffect(appState.statusMessage) {
        val message = appState.statusMessage ?: return@LaunchedEffect
        if (message.stage != StatusStage.DONE) return@LaunchedEffect
        delay(StatusDoneMilliseconds)
        appState.consumeStatusMessage()
    }

    Scaffold(
        // The navigation suite draws edge to edge, so the content keeps itself clear of the status
        // bar and any cutout.
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // The header gets out of the way on the same scroll that takes the pill, and comes back
            // with it. One signal for both: two rules would let the two pieces of chrome leave at
            // slightly different moments, which reads as them coming loose from each other rather
            // than as the page filling the window. Unlike the pill it is laid out in this column
            // rather than floating over the page, so the well grows into the room it leaves - which
            // is why it collapses its height as well as fading, and collapses towards the top so the
            // name travels up out of the window rather than sinking behind the well.
            //
            // Never hidden while the app has something to say. The status line lives in this header,
            // so a message landing on a page that has been scrolled down would otherwise be
            // announced off screen - and a failure is the one kind that arrives unbidden.
            //
            // **Beside a rail it hides too.** It did not, on the reasoning that there is no pill
            // over there to leave with and a header vanishing alone would be the only thing moving -
            // which was true of a header that left alone, and stopped being true once the mark
            // survived it. `Screen` has always written the flag regardless of layout, so the wide
            // layout needed nothing new to drive this; what it needed was somewhere for the mark to
            // go, and the rail's own top gap is it. See the rail shell.
            AnimatedVisibility(
                visible = headerVisible,
                // A short tween rather than the default spring, and the reason is not the look of
                // it. This animates the height of the page below, so every frame of it moves the
                // extent that `Screen`'s watcher is reading - and that watcher stands down for as
                // long as the extent keeps changing. A spring's tail would leave it standing down
                // well after the header had visibly finished; a fixed tween bounds the deaf window
                // to the movement itself.
                enter = fadeIn(tween(HeaderMoveMilliseconds)) +
                    expandVertically(tween(HeaderMoveMilliseconds), expandFrom = Alignment.Top),
                exit = shrinkVertically(tween(HeaderMoveMilliseconds), shrinkTowards = Alignment.Top) +
                    fadeOut(tween(HeaderMoveMilliseconds)),
            ) {
                AppHeader(
                    appState,
                    onDismissStatus = appState::consumeStatusMessage,
                    onMarkAnchor = onMarkAnchor,
                )
            }
            // Above the well rather than inside it, so a run starting does not push the rounded
            // edge down the screen. The bar carries no label any more - the header above it names
            // what is running, and the two said the same thing a few pixels apart.
            if (appState.busyLabel != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            // The page sits in the chrome the way a card sits on the page, and takes the corner
            // radius the cards themselves use. The hairline is what keeps that edge drawn when a
            // card scrolls up under it, since a card is the same colour as the chrome around it.
            val wellShape = MaterialTheme.shapes.large.copy(
                bottomStart = CornerSize(0.dp),
                bottomEnd = CornerSize(0.dp),
            )
            Surface(
                Modifier.fillMaxWidth().weight(1f)
                    .wellOutline(wellShape, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.background,
                shape = wellShape,
            ) {
                if (rail) {
                    // Screens cross-fade and rise slightly, so changing destination reads as moving
                    // somewhere rather than the content being swapped underneath. Beside a rail the
                    // destinations are a column of buttons rather than a row of pages, and sliding
                    // between them sideways would answer a gesture this layout does not offer.
                    AnimatedContent(
                        targetState = appState.destination,
                        transitionSpec = {
                            (fadeIn(initialAlpha = 0.4f) + slideInVertically { it / 24 })
                                .togetherWith(fadeOut())
                                .using(SizeTransform(clip = false))
                        },
                        label = "destination",
                    ) { destination ->
                        DestinationScreen(destination, appState)
                    }
                } else {
                    DestinationPager(appState)
                }
            }
        }
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
 * Compact only. A tab a swipe away is the phone's gesture; beside a rail there is no reason to expect
 * it and a page-wide horizontal drag would be caught by the tables that scroll sideways.
 */
@Composable
private fun DestinationPager(appState: AppState) {
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
    ) { page ->
        DestinationScreen(destinations[page], appState)
    }
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
        when (destination) {
            AppDestination.ANALYZE -> AnalyzeScreen(appState)
            AppDestination.RESULTS -> ResultsScreen(appState)
            AppDestination.INSIGHTS -> InsightsScreen(appState)
            AppDestination.PORTFOLIO -> PortfolioScreen(appState)
            AppDestination.SETTINGS -> SettingsScreen(appState)
        }
    }
}

/**
 * The page well's edge, drawn on every side but the bottom.
 *
 * A Surface border strokes the whole shape, and the bottom of this one runs along the foot of the
 * window, where a line reads as the frame of the screen rather than as the edge of the page. The
 * other three sides still earn their place: a card scrolled up under the header is the same colour as
 * the chrome, and without the top edge there is nothing to say where the page starts.
 */
private fun Modifier.wellOutline(
    shape: CornerBasedShape,
    color: Color,
    width: Dp = 1.dp,
): Modifier = drawWithContent {
    drawContent()
    val stroke = width.toPx()
    // Half the stroke, so the line lands inside the bounds the way a border does rather than
    // straddling them and losing its outer half to the clip.
    val edge = stroke / 2
    val radius = shape.topStart.toPx(size, this)
    val path = Path().apply {
        // Up the left side, round the top, and back down the right - left open at the bottom.
        moveTo(edge, size.height)
        lineTo(edge, edge + radius)
        arcTo(Rect(edge, edge, edge + 2 * radius, edge + 2 * radius), 180f, 90f, false)
        lineTo(size.width - edge - radius, edge)
        arcTo(
            Rect(size.width - edge - 2 * radius, edge, size.width - edge, edge + 2 * radius),
            270f,
            90f,
            false,
        )
        lineTo(size.width - edge, size.height)
    }
    drawPath(path, color, style = Stroke(stroke))
}

/**
 * How long a confirmation stays in the header before it clears itself.
 *
 * Material's own short snackbar, which is what these messages used to be shown for.
 */
private const val StatusDoneMilliseconds = 4_000L

private val AppDestination.icon: ImageVector
    get() = when (this) {
        AppDestination.ANALYZE -> Icons.Outlined.AutoGraph
        AppDestination.RESULTS -> Icons.Outlined.Assessment
        AppDestination.INSIGHTS -> Icons.Outlined.Insights
        // A wallet rather than a chart: the other three destinations are all readings of the market,
        // and this one is the only place holding anything of the user's own.
        AppDestination.PORTFOLIO -> Icons.Outlined.AccountBalanceWallet
        AppDestination.SETTINGS -> Icons.Outlined.Settings
    }

private val AppDestination.selectedIcon: ImageVector
    get() = when (this) {
        AppDestination.ANALYZE -> Icons.Filled.AutoGraph
        AppDestination.RESULTS -> Icons.Filled.Assessment
        AppDestination.INSIGHTS -> Icons.Filled.Insights
        AppDestination.PORTFOLIO -> Icons.Filled.AccountBalanceWallet
        AppDestination.SETTINGS -> Icons.Filled.Settings
    }
