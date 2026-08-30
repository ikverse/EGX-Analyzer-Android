package com.ikverse.egxanalyzer.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikverse.egxanalyzer.R
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
fun EgxAnalyzerApp(activity: Activity, appState: AppState) {
    val density = LocalDensity.current
    val width = with(density) { LocalWindowInfo.current.containerSize.width.toDp().value }
    val windowWidth = when {
        width >= WIDE_LAYOUT_DP -> WindowWidth.EXPANDED
        width >= 600 -> WindowWidth.MEDIUM
        else -> WindowWidth.COMPACT
    }
    val layoutInfo by produceState<WindowLayoutInfo?>(null, activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { value = it }
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
    // What each page has open lives on AppState rather than anywhere below this line - see
    // PageState, which explains why the two shells below cannot hold it between them.
    CompositionLocalProvider(
        LocalWindowWidth provides windowWidth,
        LocalNavBarVisible provides navBarVisible,
    ) {
        if (rail) {
            NavigationSuiteScaffoldLayout(
                navigationSuite = { AppRail(appState) },
                navigationSuiteType = NavigationSuiteType.NavigationRail,
            ) {
                // The suite scaffold used to paint this; the layout does not. Without it the window
                // background shows through behind the status and navigation bars as a pale band. It
                // is the chrome colour rather than the page's, because this is what shows behind the
                // system bars, and through the rounded corners where the page stops short of them.
                Surface(
                    // The layout does not do this for us the way the full NavigationSuiteScaffold
                    // does, and without it the page keeps clear of the gesture strip that the rail
                    // beside it is already holding: a second empty band, the width of the strip, in
                    // the chrome colour, between the bottom of the page and the first row of icons.
                    Modifier.fillMaxSize().consumeWindowInsets(
                        NavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start),
                    ),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    AppContent(activity, appState, rail = true)
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
                    AppContent(activity, appState, rail = false)
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
 * Fixed, where the page titles under it scroll away with their content: a name that goes with them
 * leaves the top of a long page saying nothing at all. The mark is the launcher artwork reduced to
 * one colour: the full tile in the rail read as a sticker among the flat navigation glyphs, and it
 * only ever appeared on the wide layout, where this says the same thing on both.
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
private fun AppHeader(appState: AppState, onDismissStatus: () -> Unit) {
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
                Icon(
                    painterResource(R.drawable.ic_egx_notification),
                    // The name is right beside it, and a reader announcing both says it twice.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(AppMarkSize),
                )
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
        Row(
            Modifier
                // A working line reflects live state and there is nothing to dismiss; the other two
                // are messages, and a message the reader has finished with should go when tapped.
                .then(if (glyph == StatusStage.WORKING) Modifier else Modifier.clickable(onClick = onDismiss))
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
private fun AppRail(appState: AppState) {
    // Material leaves a rail the page's own colour; this one carries the header's, so the two meet
    // as one piece of chrome around the page rather than as a rail that has vanished into it.
    // Destinations only - the app's mark is in the header, where both layouts have one.
    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
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
private fun AppContent(activity: Activity, appState: AppState, rail: Boolean) {
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
            AppHeader(appState, onDismissStatus = appState::consumeStatusMessage)
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
                        DestinationScreen(destination, activity, appState)
                    }
                } else {
                    DestinationPager(activity, appState)
                }
            }
        }
    }
}

/**
 * The destinations laid out side by side, swiped through as well as tapped.
 *
 * The bar's items and the pager are two ways of moving the same pointer, so each follows the other:
 * a tap animates the pager to the page, and a swipe names the page it lands on. What keeps the two
 * from chasing each other round is that exactly one coroutine ever moves the pager, nothing the
 * pager itself says can restart that coroutine, and only a scroll the reader started is allowed to
 * name a destination.
 *
 * **Keyed on the pager rather than on the destination, and that is the heart of it.** Keyed on the
 * destination, a swipe restarted the very effect that does the scrolling - because a swipe publishes
 * its own arrival, and the destination was that effect's key. The restarted copy then compared a
 * target read at recomposition against a page read a frame or more later, so a second swipe arriving
 * before the restart scrolled the reader back to the page they had just left. That was "swiping goes
 * to a random page". The flag that used to guard it was raised inside the scrolling coroutine, a
 * frame after the scroll had started, and a page that turned over inside that gap was swallowed and
 * never said again - which is how the bar came to be lit on a tab the reader was not on.
 *
 * Compact only. A tab a swipe away is the phone's gesture; beside a rail there is no reason to expect
 * it and a page-wide horizontal drag would be caught by the tables that scroll sideways.
 */
@Composable
private fun DestinationPager(activity: Activity, appState: AppState) {
    val destinations = AppDestination.entries
    val pager = rememberPagerState(
        initialPage = destinations.indexOf(appState.destination),
        pageCount = { destinations.size },
    )
    // Whether the reader is the one moving the pager: set the moment they touch it, cleared when it
    // comes to rest, so it spans the fling as well as the finger.
    //
    // Read from the pager's own interactions rather than raised by the code that scrolls it, which is
    // the difference that matters - a flag raised inside the scrolling coroutine goes up a frame
    // after the scroll has started, and the page that turned over inside that frame was lost.
    //
    // Everything else here hangs off it. A page only names a destination while this is set, so the
    // pages a tap travels over cannot report themselves as arrivals, and neither can the page a tap
    // was abandoned on when a second tap replaced it - that one is not an arrival either, and left to
    // speak it would win the race against the tap that cancelled it.
    val readerScroll = remember { mutableStateOf(false) }

    LaunchedEffect(pager) {
        launch {
            pager.interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is DragInteraction.Start -> readerScroll.value = true
                    // A drag that ended without moving the pager at all has nothing to come to rest,
                    // so it is closed here; one that flung is closed below, when the pager stops.
                    is DragInteraction.Stop, is DragInteraction.Cancel ->
                        if (!pager.isScrollInProgress) readerScroll.value = false
                    else -> Unit
                }
            }
        }
        launch {
            // Under the reader's hand the current page turns over at the halfway mark, so the bar
            // lights up the tab being dragged towards rather than waiting for the pages to settle.
            snapshotFlow { pager.currentPage to readerScroll.value }.collect { (page, reader) ->
                if (reader) appState.navigate(destinations[page])
            }
        }
        launch {
            // Wherever it came to rest, in case the last page turn landed in the same frame as the
            // stop and the collector above read the two out of step. The page is published before the
            // scroll is closed, in one collector, so there is no frame between them for that to
            // happen again here.
            snapshotFlow { pager.isScrollInProgress }.collect { moving ->
                if (moving) return@collect
                if (readerScroll.value) appState.navigate(destinations[pager.currentPage])
                readerScroll.value = false
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
            // The pager is already moving, and who is moving it decides whether this travel is
            // still wanted.
            //
            // Under a hand or a fling it is not: a drag outranks anything started here and would
            // refuse it anyway, and where it comes to rest is what the destination will read a
            // moment later. Dropping the travel there costs nothing, because the arrival that
            // follows leaves the bar and the pager naming the same page.
            //
            // Anything else moving it is this coroutine's own predecessor, cancelled a frame ago and
            // still settling - and dropping the travel there was how the bar came to be lit on a tab
            // the reader was not on. Nothing publishes a destination that no reader scrolled to, so a
            // target abandoned here was never asked for again: `destination` kept the page it had
            // been sent to, the pager kept the page it was on, and the two stayed that way for as
            // long as the app was open. Two sends arriving together is all it takes - a run finishing
            // while a notification is being opened, both of them travelling to RESULTS.
            //
            // So wait it out instead, and read the pager again on the other side. `collectLatest`
            // still cancels the wait the moment a newer target arrives, so this holds nothing up and
            // the one-coroutine rule above is untouched.
            if (pager.isScrollInProgress) {
                if (readerScroll.value) return@collectLatest
                snapshotFlow { pager.isScrollInProgress }.first { !it }
                // The reader took hold while this was waiting, or the settling came to rest on the
                // target anyway. Either way the travel is no longer this coroutine's to make.
                if (readerScroll.value) return@collectLatest
                if (pager.currentPage == target && pager.currentPageOffsetFraction == 0f) {
                    return@collectLatest
                }
            }
            try {
                pager.animateScrollToPage(target)
            } catch (_: CancellationException) {
                // The reader took hold of the pager mid-travel: a drag holds it at a priority a
                // scroll started here cannot take, so this is refused outright rather than
                // interrupted. Let them have it - but a real cancellation still has to pass.
                currentCoroutineContext().ensureActive()
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
        DestinationScreen(destinations[page], activity, appState)
    }
}

@Composable
private fun DestinationScreen(
    destination: AppDestination,
    activity: Activity,
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
            AppDestination.ANALYZE -> AnalyzeScreen(activity, appState)
            AppDestination.RESULTS -> ResultsScreen(activity, appState)
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
