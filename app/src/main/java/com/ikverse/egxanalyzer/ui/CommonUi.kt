package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.ui.kit.KitRefreshIndicator

import com.ikverse.egxanalyzer.ui.kit.TickerTape
import com.ikverse.egxanalyzer.ui.theme.palette
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedContent
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.ikverse.egxanalyzer.R
import com.ikverse.egxanalyzer.model.isEgx33
import com.ikverse.egxanalyzer.ui.theme.LocalDarkTheme
import com.ikverse.egxanalyzer.ui.theme.pageAccent
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.LocalContentColor
import com.ikverse.egxanalyzer.ui.kit.KitRule

/**
 * Standard page frame: the page's own name at the top, shrinking as the page is read.
 *
 * The title used to be the first line inside the scroll, under a band that said `EGX Analyzer` on
 * all five tabs. The band is gone as of 2026-09-09 and this is what replaced it - see [PageHeader],
 * which explains the shrink, and `AppContent`, which explains why there is no chrome above it any
 * more.
 *
 * **The header eats the scroll before the page moves.** [HeaderCollapseTravel] of every downward
 * gesture goes into the collapse first and is not passed on, which is the whole reason there is a
 * `NestedScrollConnection` here rather than an arithmetic on `scroll.value`: a header that merely
 * shrank *as* the page scrolled would move the content at twice the speed of the finger for the
 * first 40dp, because the page rises by whatever height the header gives up on top of its own
 * travel.
 *
 * **It grows back only with the page at its top.** Expanding on any upward delta would pop the
 * title open in the middle of a long page, and - on the three screens that pull to refresh - would
 * fight the gesture: this connection is the outer one, so it sees a downward drag before
 * `PullToRefreshBox` does. Gated on `scroll.value == 0` the two take their turns in the order a
 * reader expects, the header first and then the refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Screen(
    /** For the header's stock search, the status line, and the progress hairline under it. */
    appState: AppState,
    /**
     * Which page this is - the title and the icon both come from it.
     *
     * Taken rather than a `title` string so that the name at the top of the page and the label in
     * the navigation cannot drift apart: there is one of each, on [AppDestination].
     */
    destination: AppDestination,
    /** Given, the page pulls down to refresh. Its spinner is [refreshing]. */
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Seeded from PageState rather than starting at 0, so a fold restores exactly where the
    // reader left off instead of resetting to the top - see PageState.scrollOffset for why this
    // was one of the few things on a page still held in a bare `remember`. Read once, since
    // `remember` only evaluates its lambda on this composable's first composition; the effect
    // below is what keeps it current for the fold after this one.
    val savedScroll = appState.pages.scrollOffset(destination)
    val scroll = rememberScrollState(initial = savedScroll.value)
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.value }.collect { savedScroll.value = it }
    }
    // Where the scrolling area begins on screen, so anything inside it can pin itself there. A
    // table header has no other way to know how far it has been scrolled past.
    var viewportTop by remember { mutableFloatStateOf(0f) }
    // How much of the header has been taken, in pixels, between 0 and [HeaderCollapseTravel].
    //
    // Written from the scroll callback below and read in two places: [PageHeader]'s own
    // composition. Never read in this function's composition - the page
    // under it would then recompose on every frame of a collapse.
    val travel = with(LocalDensity.current) { HeaderCollapseTravel.toPx() }
    val taken = remember { mutableFloatStateOf(0f) }
    val headerScroll = remember(travel, scroll) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                return when {
                    // Reading on: the header shrinks before the page underneath is asked to move.
                    delta < 0f && taken.floatValue < travel -> {
                        val used = max(delta, -(travel - taken.floatValue))
                        taken.floatValue -= used
                        Offset(0f, used)
                    }
                    // Back at the top: the title comes back before anything else takes the gesture.
                    delta > 0f && taken.floatValue > 0f && scroll.value == 0 -> {
                        val used = min(delta, taken.floatValue)
                        taken.floatValue -= used
                        Offset(0f, used)
                    }
                    else -> Offset.Zero
                }
            }
        }
    }
    // Pressing the destination already showing means "take me back to the top". Animated rather
    // than jumped, so it reads as the page travelling rather than as the content being replaced.
    // The header comes back with it: the scroll that took it is being undone, so leaving it
    // collapsed would strand the tab row the press was made on.
    val scrollToTop = LocalScrollToTop.current
    LaunchedEffect(scrollToTop) {
        if (scrollToTop <= 0) return@LaunchedEffect
        launch { animate(taken.floatValue, 0f) { value, _ -> taken.floatValue = value } }
        scroll.animateScrollTo(0)
    }

    // What a card scrolled into view is held clear of at the foot: a gap, so the scroll does not
    // stop with the card's bottom edge on the viewport's. Since 5.0 there is no bar over the page.
    val revealClearance = with(LocalDensity.current) { Space.m.toPx() }
    val clearBar = remember(revealClearance) {
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect =
                localRect.copy(bottom = localRect.bottom + revealClearance)

            // Nothing to scroll here: the page's own scroll, one level up, does that with the
            // taller rect this hands it.
            override suspend fun bringChildIntoView(localRect: () -> Rect?) = Unit
        }
    }
    val fadeBand = with(LocalDensity.current) { PageEdgeFade.toPx() }
    val page = @Composable {
        Column(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { viewportTop = it.positionInWindow().y }
                // Soft where the page meets the header above and the gesture strip below, rather
                // than sliced mid-line. Nothing is drawn faded at rest: the top fades only once
                // something has scrolled past it, the foot only while there is more underneath.
                .fadeEdges(
                    fade = PageEdgeFade,
                    top = { scroll.value / fadeBand },
                    bottom = { (scroll.maxValue - scroll.value) / fadeBand },
                )
                .fadingScrollbar(scroll)
                .verticalScroll(scroll)
                .bringIntoViewResponder(clearBar)
                .padding(horizontal = Space.l)
                .padding(top = Space.l, bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            content()
        }
    }
    CompositionLocalProvider(LocalViewportTop provides viewportTop) {
    Box(Modifier.fillMaxSize().nestedScroll(headerScroll)) {
      Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(
                destination = destination,
                // A lambda, not a value. Read here it would be this function recomposing on every
                // frame of a collapse, and this function composes the whole page.
                collapse = { (taken.floatValue / travel).coerceIn(0f, 1f) },
                // The page's own stock box, or null where the page has no list to narrow. See
                // PageState.stockBox - and StockBox for why every part of it is held out there.
                search = appState.pages.stockBox(destination),
                current = appState.destination == destination,
                // The flag the filter icon sets; the page below draws the sheet it opens. Null
                // where the page has no filters at all. See PageState.filtersOpen.
                filters = appState.pages.filtersOpen(destination),
                // Only the filters that live in the sheet, so the dot never reports the stock box
                // the reader can already see. See PageState.filtersInSheet.
                filtered = appState.pages.filtersInSheet(destination),
                // A lambda, for `collapse`'s reason turned the other way round: reading the runs,
                // the calls or the positions here would subscribe every page to all three and walk
                // them on every recomposition. The picker calls it once, when its list opens.
                stocksOnPage = { pageStocks(appState, destination) },
                onNavigate = appState::navigate,
                onScrollToTop = appState::scrollToTop,
            )
            // The price strip, while the market is trading and only then: the movement itself is
            // what says the session is open. Hairlines above and below hold it as a band.
            val session = rememberSessionState()
            if (session.open && appState.tapeQuotes.isNotEmpty()) {
                KitRule(color = palette.ink, thickness = 1.5.dp)
                TickerTape(appState.tapeQuotes)
                KitRule()
            }
            // A run's progress, on every page while it lasts. Above the page's own content, so a
            // run starting does not push the first card down the screen.
            if (appState.analysisStatus == AnalysisStatus.RUNNING) {
                RunProgress(
                    progress = appState.analysisProgress,
                    startedAt = appState.analysisStartedAt,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l).padding(top = Space.s),
                )
            }
            // One line for everything the app is doing or has just done. It sat in the app-name
            // band until that band was removed; it is here, on the page, because the page starts at
            // the top of the window now and there is nowhere above it left to be. Still outside the
            // scroll, so a message can never land off screen.
            AppStatusLine(
                appState = appState,
                onDismiss = appState::consumeStatusMessage,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l),
            )
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (onRefresh == null) {
                    page()
                } else {
                    // The gesture wraps only the scrolling page: a floating button that slid down
                    // with the indicator would look like it had come loose.
                    val pull = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = onRefresh,
                        state = pull,
                        indicator = { KitRefreshIndicator(pull, refreshing) },
                    ) { page() }
                }
            }
        }
      }
    }
    }
}

/**
 * The top of the scrolling area, in window coordinates.
 *
 * Published so a header deep inside the page can hold itself against it. Measuring the window again
 * from down there gets the top of the screen, which is not the same thing once a rail, a status bar
 * or a pull-to-refresh indicator is in the way.
 */
internal val LocalViewportTop = compositionLocalOf { 0f }

/**
 * Whether the pager holding the five tabs has come to rest.
 *
 * Owned by the shell and written by `DestinationPager`: something outside every screen knows this,
 * and a page has no other way to ask.
 *
 * Read by [revealIfOnScreen], which is the only thing that needs it - see the trap written up
 * there. A page composing is not the same event as a reader arriving on it: on a phone the two are
 * a whole travel apart.
 */
internal val LocalTabsSettled = staticCompositionLocalOf { mutableStateOf(true) }

/**
 * How many times this page has been asked to return to the top, or zero.
 *
 * The shell writes this and the page reads it. Zero means "nothing asked"; every press after that is a distinct number,
 * which is what makes a second press restart the effect rather than look like the first one again.
 *
 * Provided **per destination** by the shell, so a page only ever sees presses meant for it - see
 * `DestinationScreen`. Not `static`, because it changes.
 */
internal val LocalScrollToTop = compositionLocalOf { 0 }

/**
 * Whether the system has animation switched off.
 *
 * Read from the animator duration scale at the root, which is where Android puts "Remove
 * animations" - Compose's own transitions already follow it, but the loops this app draws itself do
 * not. Anything that moves for its own sake, the model's breathing and the action's drifting light,
 * holds still when this is true; so does a press, which is a scale all the same. Colour and opacity
 * changes that carry meaning are left alone. Provided by [AppRoot]; false in a preview.
 */
internal val LocalReduceMotion = staticCompositionLocalOf { false }

/** How deep the page's edges fade where content passes under the header and the gesture strip. */
private val PageEdgeFade = 20.dp

/**
 * Whether the frosted chrome should be drawn solid.
 *
 * Android has no public "reduce transparency" switch, so this is a best-effort reading of the two
 * settings that ask for the same thing: animations removed, and high-contrast text. Either one is
 * somebody telling the phone to stop making the screen harder to read, and a bar the page shows
 * through is the first thing that does. Provided by [AppRoot]; false in a preview.
 */
internal val LocalSolidSurfaces = staticCompositionLocalOf { false }

/**
 * How far text sitting loose on a page is set in from the page's own edge.
 *
 * A card holds its outline at the page edge and its contents [Space.l] inside that, so every
 * heading on a page reads down one column that starts an inset further in. The page name and any
 * other text that sits on the page rather than in a card takes the same inset, or it hangs to the
 * left of every heading under it and the page looks unaligned.
 */
internal val PageTextInset = Space.l

/**
 * The unlit part of the busy bar: the page's hue, most of the way down.
 *
 * Faint enough that an idle stretch of the bar is a hint of the line rather than a second object,
 * and the eye follows the one lit piece travelling along it.
 */
internal const val BusyTrackAlpha = 0.14f

/**
 * A group inside a card, for settings that belong to one another.
 *
 * Deliberately lighter than [ExpandableSection]: a card drawn inside a card reads as a mistake, so
 * this is a heading, a chevron, and a rule underneath. It keeps its own open state, because which
 * group someone is reading is not the parent's business.
 */
@Composable
internal fun SubSection(
    title: String,
    /** One line saying what is inside, so a closed group still informs. */
    summary: String? = null,
    /** The group's own explanation, on the same terms as [ExpandableSection]'s. */
    about: InfoNote? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                summary?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.72f),
                    )
                }
            }
            about?.let { InfoButton(it) }
            // The kit's own disclosure: a raised capsule whose chevron turns, as KitExpandable's does.
            val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "subsection")
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(palette.raised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = palette.ink,
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn },
                )
            }
        }
        AnimatedVisibility(expanded, enter = ExpandIn, exit = CollapseOut) {
            Column(
                Modifier.padding(bottom = Space.m),
                verticalArrangement = Arrangement.spacedBy(Space.m),
                content = content,
            )
        }
        KitRule()
    }
}

/**
 * A titled group of related controls, replacing the loose outlined boxes used before.
 *
 * @param about what the group is for, behind the heading's question mark rather than in a paragraph
 *   under it. Ignored without a [title]: there is no heading to hang it on.
 * @param contentInset how far the content is held in from the card's edge.
 *
 * [Space.l] everywhere the content is things to read - controls, figures, sentences - which is what
 * a card normally holds. A card whose children are themselves **cards** passes [Space.s], because
 * the child draws its own border and the parent is then paying for an edge that is already there.
 * The heading and its rule keep the full inset whatever this is: the alternative is a title that
 * sits at a different distance from the card's edge than every other title on the page, which is
 * the almost-aligned this file's spacing scale exists to stop.
 *
 * It matters because these nest. Positions is a card holding session cards holding trade cards, and
 * at a full inset per level the innermost card had 285dp of a 411dp screen.
 *
 * @param accent this card's own hue, on the tile behind [icon] and on the edge down its left side.
 *
 * **A card's accent is chrome and never a figure.** It says which card this is in a column of them;
 * what a number on it means is still said by the tertiary/error/market roles, which are the same on
 * every page. So a card may take any hue without the prices on it changing meaning.
 *
 * Null takes the page's own, which is what the **first** card on a page should have - the rest name
 * one, in the order the screens keep. A default of "the page's hue" rather than a grey is what makes
 * an untouched call site look deliberate rather than unfinished.
 */
@Composable
internal fun SectionCard(
    title: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    about: InfoNote? = null,
    contentInset: Dp = Space.l,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // A white kit tile since 5.0: the heading in display type, its question mark beside it, and
    // the content under it. `accent` is no longer drawn; callers still name their card's place.
    val p = palette
    Column(
        modifier
            .fillMaxWidth()
            .clip(com.ikverse.egxanalyzer.ui.kit.Kit.TileShape)
            .background(p.card)
            .padding(vertical = Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides p.ink) {
            if (title != null) {
                Row(Modifier.padding(horizontal = Space.l), verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, tint = p.muted, modifier = Modifier.size(SectionIconSize))
                        Spacer(Modifier.width(Space.s))
                    }
                    Text(
                        title,
                        modifier = if (about == null) Modifier else Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    about?.let { InfoButton(it, dense = true) }
                }
            }
            Column(
                Modifier.padding(horizontal = contentInset),
                verticalArrangement = Arrangement.spacedBy(Space.s),
                content = content,
            )
        }
    }
}


/** The grey glyph before a section's title: a step under an inline icon, so the title leads. */
private val SectionIconSize = 18.dp

/**
 * A settings group that starts closed.
 *
 * Settings is long enough that showing every control at once buries the one being looked for, so
 * each group opens on demand and the headers act as the index.
 */
@Composable
internal fun ExpandableSection(
    title: String,
    icon: ImageVector? = null,
    /** A verdict on the card's own state, drawn as the dot before its title. */
    iconTone: Color? = null,
    /** No longer drawn since 5.0; callers still name their card's place. */
    accent: Color? = null,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    /** One line under the title saying what is inside, so a closed card still informs. */
    summary: String? = null,
    /** Colour for [summary], where the figure itself carries a verdict. */
    summaryTone: Color? = null,
    /** Draws that line itself, for a summary whose parts want different colours. */
    summaryContent: (@Composable () -> Unit)? = null,
    /** Caps the content, for groups of form controls: a text field the width of a desk is unusable. */
    contentMaxWidth: Dp? = null,
    /** Unused since the kit tile sets its own inset; kept so every caller still compiles. */
    contentInset: Dp = Space.l,
    /** What the whole group is for, behind a question mark in the header. */
    about: InfoNote? = null,
    /** Hoisted when the layout around it needs to know: an open card claims the whole row. */
    expandedState: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    /** Any value marks a card drawn inside another, which takes the page's ground instead of white. */
    containerColor: Color? = null,
    showAccentEdge: Boolean = true,
    /** Holding the header offers something beyond expanding the card - see [HoldPrompt]. */
    onHeaderLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var localExpanded by remember { mutableStateOf(initiallyExpanded) }
    val expanded = expandedState ?: localExpanded
    val p = palette
    com.ikverse.egxanalyzer.ui.kit.KitExpandable(
        title = title,
        expanded = expanded,
        onExpandedChange = { open -> if (onExpandedChange != null) onExpandedChange(open) else localExpanded = open },
        modifier = modifier,
        leading = when {
            iconTone != null -> ({ com.ikverse.egxanalyzer.ui.kit.StateDot(iconTone) })
            icon != null -> ({ Icon(icon, contentDescription = null, tint = p.muted, modifier = Modifier.size(SectionIconSize)) })
            else -> null
        },
        summary = when {
            summaryContent != null -> summaryContent
            summary != null -> ({ Text(summary, style = MaterialTheme.typography.bodySmall, color = summaryTone ?: p.muted) })
            else -> null
        },
        headerExtra = about?.let { note -> { InfoButton(note) } },
        onHeaderLongClick = onHeaderLongClick,
        fill = if (containerColor != null) p.ground else p.card,
    ) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides p.ink) {
            Column(
                if (contentMaxWidth != null) Modifier.widthIn(max = contentMaxWidth) else Modifier,
                verticalArrangement = Arrangement.spacedBy(Space.s),
                content = content,
            )
        }
    }
}

/**
 * The edge a card wears when the app has just brought the reader to it.
 *
 * A flash rather than a permanent tint: it answers "which one" on arrival and then gets out of the
 * way, instead of leaving a card looking special long after the reason has passed. Null when there
 * is nothing to point out, so a card falls back to whatever edge it draws for itself - the held
 * outline on a traded call, the hairline everywhere else.
 *
 * The animation is composed only while it is wanted. Left running behind every card it would be a
 * frame callback each, on screens that draw dozens of them, to animate nothing.
 *
 * @param statusColor the card's own held-outline colour, when it has one, so the flash reads as
 *   that outline announcing itself rather than as a second, unrelated colour landing on top of it.
 *   Null falls back to the page accent, for a card with no status of its own.
 * @param onShown fires once the flash has run, so the caller can forget the arrival.
 */
@Composable
internal fun arrivalFlash(
    highlighted: Boolean,
    onShown: () -> Unit,
    statusColor: Color? = null,
): BorderStroke? {
    if (!highlighted) return null
    val edge = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        edge.animateTo(
            1f,
            // Linear, not the tween default: RepeatMode.Reverse chains this segment back to back
            // with itself, and the default FastOutSlowIn easing is asymmetric - the gentle tail
            // easing into the peak collided with the same curve's steep opening dropping away from
            // it, which read as a snap at the top of each blink rather than a smooth reversal.
            animationSpec = repeatable(
                FlashBlinks * 2,
                tween(FlashHalfCycleMs, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
        )
        onShown()
    }
    val color = statusColor ?: MaterialTheme.colorScheme.primary
    return BorderStroke(FlashOutline, color.copy(alpha = edge.value))
}

/** The same weight as the held outline, so a flash does not resize the card it lands on. */
private val FlashOutline = 0.5.dp

/** Fast enough that two of them read as a blink rather than a fade. */
private const val FlashHalfCycleMs = 250

/** Two blinks, not a pulse held open: enough to catch the eye without nagging at it. */
private const val FlashBlinks = 2

/**
 * How long to let a card's own reveal finish before scrolling to it.
 *
 * A jump opens the section holding the card and scrolls to it in one go, and a request made while
 * that section is still unfolding is measured against a height it is about to leave behind - which
 * lands the reader short of the card they asked for. Material's default reveal is 300ms.
 */
internal const val REVEAL_SETTLE_MS = 320L

/**
 * Scrolls to a card, unless the reader has left the tab it is drawn on.
 *
 * A [BringIntoViewRequester]'s request travels up through every scrollable ancestor, and on a phone
 * the outermost one is the pager holding the five tabs. Fired from a page the reader is no longer
 * looking at, it scrolls that page back into view - which is the pager travelling back to the tab
 * they had just left. The pager keeps its neighbours composed, so a page walked away from is still
 * alive and still running the effect that reveals a card on it. See `DestinationPager`.
 *
 * It won against a tab press rather than losing to one: a press animates the pager at the same
 * priority a reveal scrolls it, so the later of the two wins, and the reveal was the later one. A
 * swipe survived, which is what made this look like the bar alone was broken - a drag holds the
 * pager at a priority no reveal can take.
 *
 * Dropped rather than held for the reader's return, which is the rule [NavStop] already states
 * about the trip back: revealing the wrong card is worse than revealing none, and a press answered
 * minutes late is a card flashing for a reason nobody remembers. The section holding it is left
 * unfolded either way, which is most of what the reveal was for.
 *
 * **The tab alone was not enough, and that is the whole of [settled].** A destination names where
 * the reader is going, not whether they have got there: a press sets it in the same breath it
 * starts the travel, so a page arriving *during* that travel passes the check and cancels the very
 * scroll that is carrying the reader to it. The pager, barely off the tab they pressed from, then
 * snaps back to it. Only a tab two or more pages away can do this - a neighbour is already
 * composed and its effects do not run again - which is why the Portfolio reached Insights and
 * never Results. So the request waits out the pager and asks again on the other side, rather than
 * being dropped: an arrival that should reveal, a notification opening a saved report among them,
 * composes its page mid-travel too, and dropping the request would answer the notification with a
 * page scrolled to wherever it was left.
 */
@OptIn(ExperimentalFoundationApi::class)
internal suspend fun BringIntoViewRequester.revealIfOnScreen(
    appState: AppState,
    tab: AppDestination,
    settled: State<Boolean>,
) {
    if (appState.destination != tab) return
    snapshotFlow { settled.value }.first { it }
    // Asked again on the other side of the wait: a travel is long enough for the reader to press
    // somewhere else, and the tab this was for may no longer be the tab on screen.
    if (appState.destination != tab) return
    bringIntoView()
}

/** A single figure with its label, for wherever a screen summarises counts or totals. */
/** Tighter than [Space.xs], which read as two facts where a dense tile is meant to read as one. */
private val DenseFigureGap = 2.dp

/**
 * A figure that rolls to its new value instead of jumping, when it changes while on screen (2026-11
 * redesign): the old value slides up and out as the new one slides up and in. The first value is
 * simply drawn, and with reduce motion on every change is instant.
 */
@Composable
internal fun RollingText(
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val still = LocalReduceMotion.current
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            if (still) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (slideInVertically(uiSpring()) { it / 2 } + fadeIn(tween(EnterMs, easing = UiEase))) togetherWith
                    (slideOutVertically(uiSpring()) { -it / 2 } + fadeOut(tween(ExitMs, easing = UiEase)))
            }.using(SizeTransform(clip = true))
        },
        label = "rollingText",
    ) { shown ->
        Text(shown, style = style, color = color)
    }
}

/** The kit result bar's height, so a share and a verdict drawn on one screen are one object. */
private val ShareBarHeight = 8.dp

/** A pill corner would round a bar this short into a capsule; 3dp cuts it without closing it. */
private val ShareBarCorner = 3.dp

/**
 * Enough that the remainder reads as the same quantity, unspent - and deliberately above
 * [BusyTrackAlpha].
 *
 * A progress track is scenery behind the part that moves, so it wants to be nearly invisible. Both
 * halves of a share are the answer, so the smaller half has to be legible on its own: at the track's
 * own 0.14 the 10% end of a lopsided split read as the bar simply stopping short.
 */
private const val ShareRestAlpha = 0.22f

internal enum class StatusTone { GOOD, BAD, NEUTRAL }

/** Short status line, coloured by whether it reports something good, bad, or neutral. */
@Composable
internal fun StatusPill(text: String, tone: StatusTone = StatusTone.NEUTRAL) {
    val p = palette
    com.ikverse.egxanalyzer.ui.kit.KitTag(
        text,
        when (tone) {
            StatusTone.GOOD -> p.gain
            StatusTone.BAD -> p.loss
            StatusTone.NEUTRAL -> p.muted
        },
    )
}

/**
 * Every label pill in the app. The shape and the height are [PillShape] and [LabelPillHeight],
 * beside the spacing scale, because the buttons and the badge are cut to them too.
 *
 * A ring rather than a block of colour. A card can carry three of these at once - what the position
 * did, how late it is, why it is still open - and three filled pills stacked beside the name were
 * competing with the figures they are supposed to annotate. The outline says the same thing in the
 * same colour without spending a colour's worth of area on it.
 *
 * [outline] is the hue the state means and [textColor] is the ink the filled pill already used, so
 * the wording keeps the weight it reads at while the colour moves to the edge. [onClick] is for the
 * one pill that explains itself when pressed; the rest are labels and take no press.
 */
@Composable
internal fun OutlinePill(
    text: String,
    outline: Color,
    textColor: Color,
    onClick: (() -> Unit)? = null,
) {
    // The kit's tag since 5.0: a hairline capsule in the colour the state means, its word in the
    // same colour. [textColor] is kept for callers and no longer differs from the edge.
    com.ikverse.egxanalyzer.ui.kit.KitTag(text, outline, onClick = onClick)
}

/**
 * The same pill with the colour inside the edge rather than on it.
 *
 * Two surfaces draw a pill as a block of colour - the stock sheet's flags, and the table's own
 * marks - and both were spelling their own corner, their own padding and their own text size. They
 * are not [OutlinePill] with a fill parameter, because a fill and a ring say different things here:
 * a ring annotates the call it sits on and a fill flags a standing fact about the stock. What they
 * must not differ in is their shape, and that is what this shares.
 *
 * [onClick] mirrors [OutlinePill]'s own: absent for the marks that are simply true of the stock -
 * a rank, a badge - and given for the table's own timing pill, which explains itself the same way
 * the card's [OutlinePill]-drawn one does.
 */
@Composable
internal fun FilledPill(
    text: String,
    container: Color,
    content: Color,
    onClick: (() -> Unit)? = null,
) {
    // A wash of the content colour, the kit's filled tag - a standing fact rather than a note.
    com.ikverse.egxanalyzer.ui.kit.KitTag(text, content, filled = true, onClick = onClick)
}

/**
 * One choice in a row where exactly one is chosen - a price chart's range and its levels toggle.
 *
 * Not Material's own `FilterChip`: its padding is fixed and generous, which is what made five
 * ranges and a toggle need 290dp of scrolling room on a card that can have as little as 313dp to
 * give in total. Drawn at [PillHeight] and [PillShape] instead, the size every other pressed pill
 * on a card already carries, filled when chosen and ringed when not - the same two states
 * [FilledPill] and [OutlinePill] already draw, so choosing an option reads the same way reading one
 * does. [minimumInteractiveComponentSize] keeps the tap target at 48dp regardless, the same trick
 * [ActionPill] and [DisclosureButton] already rely on.
 */
@Composable
internal fun CompactFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** A switch rather than one of a set: drawn with a tick when on, never as the chosen range. */
    toggle: Boolean = false,
) {
    // The kit's segment look at a card's density: ink when chosen, a hairline when not, and a
    // toggle that is on carries a tick so it cannot be mistaken for the chosen range beside it.
    val p = palette
    val press = remember { MutableInteractionSource() }
    val chosen = selected && !toggle
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .pressScale(press)
            .height(PillHeight)
            .clip(com.ikverse.egxanalyzer.ui.kit.Kit.Capsule)
            .background(if (chosen) p.ink else Color.Transparent)
            .then(if (chosen) Modifier else Modifier.border(1.dp, if (toggle && selected) p.hero else p.line, com.ikverse.egxanalyzer.ui.kit.Kit.Capsule))
            .clickable(interactionSource = press, indication = null, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { selectedState(selected) }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            (if (toggle && selected) "✓ " else "") + label,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = TabularFigures),
            color = when {
                chosen -> p.onInk
                toggle && selected -> p.heroInk
                else -> p.ink
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A hairline, matching every other pill's own edge. */
private val ChipOutline = 1.dp

/** One label, one height, one padding - the whole of what the two pills have in common. */
// An 11sp line, centred in [LabelPillHeight] rather than propping the box open from inside. The
// height is the pill's and no longer the text's: two pills whose words set at different heights
// used to close to different sizes, and that is what a card of them read as.
private val PillText = 11.sp
private val PillLine = 14.sp

/** A hairline. A card's own held outline is twice it, so a pill never competes with the edge. */
private val PillOutline = 1.dp

/**
 * The mark a stock carries when the exchange counts it in the EGX 33 Shariah index.
 *
 * Draws nothing for a stock outside the index, so the three places that show it each cost one line
 * rather than one line and a condition. The ticker is the whole input: membership is a property of
 * the company, not of the call, the trade, or the run being looked at.
 *
 * A glyph with no wording, which is the one pill in the app that carries none. Every other pill is
 * a sentence about what happened to a call - reached target 1, expired, still open - and is read.
 * This is a standing fact about the company, repeated on every card that names it, and spelling
 * "EGX33" out beside a ticker on all of them adds a third thing to a line that already holds a
 * logo and a name. The reader learns one mark once; a screen reader still hears the whole phrase.
 *
 * Square rather than the ring the pills wear, and that is not a free choice. Round, its outline sat
 * concentric with the eight-pointed star inside it, and two rings around a small dark shape is the
 * shape of a settings cog - which is what it read as.
 *
 * **It is deliberately outside [PillShape], and it was folded in once and had to come back out.**
 * The pill pass of 2026-09-11 gave it the shared 6dp on the reasoning that one corner everywhere is
 * one object everywhere, which took it from 4dp to 6 - rounder, on the one mark in the app whose
 * whole history is that it must not be round. It read as a gear on the device inside the hour. The
 * rule the pills follow is about labels; this carries no wording, and a glyph in a ring is a
 * different object from a word in one however the two are cut.
 *
 * **No ring at all now, and the glyph is bigger inside the same box.** The recommendation card
 * dropped the ring and grew the glyph to 16dp on its own on 2026-09-20; asked for everywhere two
 * days later, since a mark that changes shape depending on which screen names the stock is the same
 * fault the pill pass fixed for wording, just for this one glyph instead.
 *
 * **Tappable since 2026-09-24**, like every other pill in the app that used to draw a fact and
 * nothing else: a glyph with no wording is the one on this whole app most in need of a press that
 * explains it.
 */
@Composable
internal fun Egx33Badge(ticker: String, modifier: Modifier = Modifier) {
    if (!isEgx33(ticker)) return
    var explaining by remember { mutableStateOf(false) }
    // A small ink square with the lime star since 5.0. Square, never round: round, the star inside
    // a ring reads as a settings cog.
    val p = palette
    Box(
        modifier
            .size(Egx33BadgeSize)
            .clip(RoundedCornerShape(5.dp))
            .background(p.ink)
            .clickable { explaining = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_egx33),
            // Said in full, because nothing on screen says it.
            contentDescription = "EGX 33 Shariah index",
            tint = p.highlight,
            modifier = Modifier.size(Egx33GlyphSize),
        )
    }
    if (explaining) {
        InfoSheet(
            infoNote(
                "EGX 33 Shariah index",
                "The exchange counts this stock among the thirty-three companies whose business " +
                    "and financing meet its Shariah screening criteria, reviewed every March and " +
                    "September.",
                "A property of the company, not of this call, this trade, or the run being " +
                    "looked at - which is why the mark follows the ticker everywhere it appears.",
            ),
        ) { explaining = false }
    }
}

/**
 * [LabelPillHeight], which is every other pill on the card.
 *
 * One size on all three screens rather than one per ticker style. It shares a line with a 27dp
 * headline on Results and a titleSmall on Insights, and sized to each it was two different marks.
 *
 * It was 24dp, which is [LogoSize.Row], on the reasoning that the logo at the head of the line is
 * what a mark at the end of it should match. That was the wrong thing to match: the logo is the
 * stock's own picture and this is a label about it, so the badge stood 4dp over every ring on the
 * card while agreeing with the one thing on the line nobody reads it against. Matching the pills
 * was the other way to make it one object, and it is the way that does not ask them to grow.
 */
private val Egx33BadgeSize: Dp = LabelPillHeight

/** 16dp of glyph in the 20dp box, the size the recommendation card grew it to. */
private val Egx33GlyphSize: Dp = 13.dp


/** Placeholder for a screen with nothing to show yet, so empty states explain themselves. */
@Composable
internal fun EmptyState(
    icon: ImageVector,
    title: String,
    detail: String,
    /** The one thing that undoes the emptiness, where there is one - Clear filters, most often. */
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    // A white kit tile since 5.0: the glyph on a raised disc, the title, the line under it, and the
    // action that gets the reader out of it.
    val p = palette
    com.ikverse.egxanalyzer.ui.kit.Tile(Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(p.raised),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = p.ink, modifier = Modifier.size(24.dp)) }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = Space.m))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = p.muted, modifier = Modifier.padding(top = Space.xs))
        if (actionLabel != null && onAction != null) {
            com.ikverse.egxanalyzer.ui.kit.KitButton(
                actionLabel,
                onClick = onAction,
                kind = com.ikverse.egxanalyzer.ui.kit.ButtonKind.SECONDARY,
                size = com.ikverse.egxanalyzer.ui.kit.ButtonSize.SMALL,
                modifier = Modifier.padding(top = Space.m),
            )
        }
    }
}

/**
 * Figures that belong together, laid out four across or two when the width cannot take four.
 *
 * The cover screen has plenty of height and little width, so wrapping beats shrinking: four
 * columns at 443dp truncates every price it is supposed to show.
 *
 * Four was once the whole story and is now only the cap - a group can carry five - and a group
 * whose last row is short spreads that row across the width rather than padding it out to the
 * column count.
 *
 * One implementation, for the three screens that draw the same thing. Insights, the Portfolio and
 * the occurrence sheet each grew their own, and two of them declared the same 420dp threshold
 * under different names: the wrap point of a price row is a fact about the device, not about the
 * screen that happens to be asking.
 */
@Composable
internal fun FigureGroup(
    title: String,
    figures: List<@Composable RowScope.() -> Unit>,
    /** No longer drawn differently since 5.0: every group's title is the same muted key. */
    titleColor: Color = palette.muted,
) {
    // The kit's figure grid since 5.0: the group's key, then its figures in ground cells, four
    // across where four prices fit and two where they do not.
    val p = palette
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = p.muted)
        BoxWithConstraints {
            val perRow = if (maxWidth >= FourFiguresMinWidth) minOf(figures.size, MaxFiguresPerRow) else 2
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                figures.chunked(perRow).forEach { row ->
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        row.forEach { figure -> figure() }
                    }
                }
            }
        }
    }
}

/** Four prices need this much before the digits start truncating. */
internal val FourFiguresMinWidth = 420.dp

/** What [FourFiguresMinWidth] was measured against, so a longer group wraps rather than shrinks. */
private const val MaxFiguresPerRow = 4

/**
 * One labelled figure, with room under it for the one thing that makes it mean something.
 *
 * [on] is the session a high or a low was set on - a price without its date says half of it - and
 * [caption] takes its place wherever the line has more to say than a date.
 */
@Composable
internal fun Figure(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: Color = palette.ink,
    on: LocalDate? = null,
    caption: String? = null,
    /** Overridden only where the value is not a price; the default sets it in the figures face. */
    valueStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures),
) {
    // A cell of the page's ground since 5.0: the key over the figure, and the date or note under it.
    val p = palette
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(p.ground)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = p.muted, maxLines = 2)
        Text(tightPoint(value), style = valueStyle, color = tone, textAlign = TextAlign.Start, modifier = Modifier.padding(top = 2.dp))
        val note = caption ?: on?.let(AppDates.DayMonth::format)
        if (note != null) {
            Text(note, style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * A figure with its decimal point drawn closer to its digits.
 *
 * The figures face is monospaced, which gives the point a whole digit's width of its own: 52.3
 * reads as "52 . 3" and the eye takes it for two numbers. The point and the digit before it each
 * give back a little advance, so the figure holds together while every digit keeps its column.
 * Applied to [Figure] only - the hero figures are drawn elsewhere and are left as they were.
 */
internal fun tightPoint(text: String): AnnotatedString =
    // Left as it is: JetBrains Mono draws its point as a full column on purpose, and the figure
    // reads as one number because every figure on the page shares that same grid.
    AnnotatedString(text)

/**
 * One line of text that shrinks to fit rather than ending in an ellipsis.
 *
 * For the cells of a fixed-column table, where the alternative is worse than small type. A price
 * read as `123.4…` is not a smaller price, it is a different one, and the column it sits in is the
 * one thing the reader is there to compare - so where a cell cannot hold its figure at the row's
 * size, the figure keeps its digits and gives up a point of type instead. Widths come first: every
 * column that uses this is sized to hold its longest real value outright, and the shrink is what
 * catches the value nobody sized for and the large system font scales.
 *
 * It starts at the size the caller asked for and steps down only as far as it has to, so a table
 * whose values all fit is drawn at exactly the size it was before this existed.
 *
 * [TextOverflow.Ellipsis] is still set, and is now a floor rather than a first resort: it can only
 * appear on text that does not fit at [minFontSize], which prices never reach and a long channel
 * name can. Three dots read better there than a glyph cut in half.
 */
@Composable
internal fun AutoSizeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    minFontSize: TextUnit = MinAutoFontSize,
) {
    BasicText(
        text,
        modifier = modifier,
        style = style.merge(color = color, textAlign = textAlign),
        maxLines = 1,
        // No wrapping at any size. With a break allowed, an entry range would split at the space
        // around its dash and the shrink would chase a second line instead of the width.
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = minFontSize,
            // The caller's own size is the ceiling: this may only make text smaller, never larger
            // than the style it shares with everything around it.
            maxFontSize = style.fontSize.takeOrElse { DefaultAutoFontSize },
            // Half a point. A whole one overshoots on a cell that is a hair too narrow and drops a
            // row of figures a visible step below the row above it for a pixel of overflow.
            stepSize = 0.5.sp,
        ),
    )
}

/**
 * How small [AutoSizeText] may go before it gives up and truncates.
 *
 * Set where a price stops being readable at arm's length rather than where it stops being drawn.
 * A cell that needs less than this is too narrow to be a column, and that is a width bug to fix
 * where the width is declared.
 */
internal val MinAutoFontSize = 9.sp

/** Only for a style with no size of its own, which none of the app's typography has. */
private val DefaultAutoFontSize = 14.sp


/** Drawn size only. The touch target stays at the platform minimum, which is larger. */
private val MoreButtonSize = 40.dp

/** Enough to read as floating over a card without the card showing through it. */
private val MenuShadow = 8.dp

/**
 * One action offered on a [HoldPrompt]: its label, and whether it leads.
 *
 * [primary] draws the filled button the one action most likely to be chosen wears - the shape
 * a hold's own "Edit call" has drawn since before this was a shared component. Every other action,
 * and Cancel always, draws the outlined button beside it: a prompt of four filled buttons in a
 * column is four equally loud claims, which says nothing about which one a reader actually wants.
 */
internal data class HoldAction(val label: String, val primary: Boolean = false, val onClick: () -> Unit)

/**
 * What holding something in this app now opens: its name, then what can be done about it.
 *
 * Generalizes the blurred, two-button prompt a hold on a Results call card has opened since
 * `QuickEditPrompt` first drew it - the same blur, the same scale-and-fade, the same delayed
 * dispatch - into the one shape a hold on a card, a tile or a ticker now shares, each supplying its
 * own [title] and its own [actions] rather than each writing the animation and the blur again.
 * Blurs what is behind it rather than dimming it flat - the platform's own way of saying "answer
 * this and you're straight back", where a full-screen scrim would read as a new place navigated to.
 * `Window.setBackgroundBlurRadius` is API 31, which is this app's `minSdk`, so there is no older
 * path to fall back to.
 *
 * The dialog does not leave the moment a button is pressed: [visible] drives the exit animation and
 * the dialog itself is only asked to close once that animation has actually run, so a press is
 * answered by a shrink-and-fade rather than a cut, and the chosen action fires only once the dialog
 * is already gone - which is what lets an action open a dialog of its own without the two fighting
 * over one window.
 */
@Composable
internal fun HoldPrompt(title: String, actions: List<HoldAction>, onDismiss: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(visible) {
        if (!visible) {
            delay(HoldPromptExitMs.toLong())
            chosen?.invoke() ?: onDismiss()
        }
    }
    // The kit's ink action card since 5.0, the ⋮ menu's own look: the title, one row per action
    // with the primary one in the highlight, and Cancel. Over a dimmed page, centred.
    val p = palette
    Dialog(
        onDismissRequest = { visible = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { visible = false },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(HoldPromptEnterMs, easing = UiEase)) +
                    scaleIn(initialScale = 0.85f, animationSpec = com.ikverse.egxanalyzer.ui.kit.popSpring()),
                exit = fadeOut(tween(HoldPromptExitMs, easing = UiEase)) +
                    scaleOut(targetScale = 0.85f, animationSpec = tween(HoldPromptExitMs, easing = UiEase)),
            ) {
                Column(
                    Modifier
                        .padding(Space.xl)
                        .widthIn(max = 320.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(p.ink)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .padding(8.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = p.onInk.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                    (actions.map { it.label to (it.primary to it.onClick) } + ("Cancel" to (false to {})))
                        .forEachIndexed { index, (label, rest) ->
                            val (primary, action) = rest
                            val isCancel = index == actions.size
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (primary) p.highlight else Color.Transparent)
                                    .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                        if (!isCancel) chosen = action
                                        visible = false
                                    }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = when {
                                        primary -> p.onTile
                                        isCancel -> p.onInk.copy(alpha = 0.7f)
                                        else -> p.onInk
                                    },
                                )
                            }
                        }
                }
            }
        }
    }
}

private const val HoldPromptEnterMs = 220
private const val HoldPromptExitMs = 160
private const val HoldPromptDim = 0.32f
private const val HoldPromptBlurRadius = 48

/**
 * One band of a sheet's scroll, on the cards' own surface.
 *
 * The stock sheet's sections were divided by rules and read as one long column that happened to
 * have lines in it; a card each is what the rest of the app uses to say "this is one thing", and it
 * is what lets the heading of a section sit inside the thing it heads rather than floating above
 * the gap. It lives here rather than beside either sheet because two sheets now draw it - the stock
 * sheet and the occurrence sheet - and a card shape spelled twice is two that agree until one is
 * adjusted.
 *
 * Not [SectionCard], which is a page's card: that one carries an icon tile, an accent edge and a
 * title in the page's own hue, and a sheet is raised over a page rather than on one - see
 * **A hue per page**.
 */
@Composable
internal fun SheetSection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    // A white kit panel inside a sheet since 5.0.
    val p = palette
    Column(
        modifier.fillMaxWidth().clip(com.ikverse.egxanalyzer.ui.kit.Kit.PanelShape).background(p.card).padding(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides p.ink) { content() }
    }
}

/** What a [SheetSection] is called, in the key style every label over a figure already uses. */
@Composable
internal fun SheetSectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = palette.muted)
}

/**
 * What a call risks against what it seeks, drawn as a length rather than a number to divide.
 *
 * Built for the table's own risk column and now drawn on the occurrence sheet as well, which is the
 * one surface where a single call is the whole subject: 1 : 3.0 is arithmetic, and two segments are
 * a proportion the eye reads without doing any.
 *
 * Clamped off both ends so a lopsided call still draws two segments: at 1 : 20 the risk side rounds
 * to nothing, and a bar that is entirely one colour says "no risk" rather than "little". Only ever
 * drawn where the ratio is real - an empty track under a dash would be a proportion made out of
 * levels the source never printed.
 */
@Composable
internal fun RiskRewardBar(ratio: Double, modifier: Modifier = Modifier) {
    // Two palette segments on a capsule since 5.0, the risk in loss red and the reward in gain
    // green, a gap between them, growing in from the left.
    val p = palette
    val riskShare = (1.0 / (1.0 + ratio)).coerceIn(0.08, 0.92).toFloat()
    val grow = com.ikverse.egxanalyzer.ui.kit.rememberDrawIn(key = ratio, durationMs = 700)
    Row(
        modifier
            .fillMaxWidth()
            .height(RiskBarHeight)
            .graphicsLayer {
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                scaleX = grow.value
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(Modifier.weight(riskShare).fillMaxHeight().clip(com.ikverse.egxanalyzer.ui.kit.Kit.Capsule).background(p.loss))
        Box(Modifier.weight(1f - riskShare).fillMaxHeight().clip(com.ikverse.egxanalyzer.ui.kit.Kit.Capsule).background(p.gain))
    }
}

private val RiskBarHeight = 8.dp

/** Marks a chip as the chosen one for a screen reader. */
private fun androidx.compose.ui.semantics.SemanticsPropertyReceiver.selectedState(value: Boolean) {
    androidx.compose.ui.semantics.SemanticsProperties.Selected.let { key -> this[key] = value }
}
