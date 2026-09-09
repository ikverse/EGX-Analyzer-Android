package com.ikverse.egxanalyzer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikverse.egxanalyzer.R
import com.ikverse.egxanalyzer.model.isEgx33
import com.ikverse.egxanalyzer.ui.theme.pageAccent
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    /** Stays put while the page scrolls. The content reserves room so it never covers anything. */
    floatingAction: (@Composable () -> Unit)? = null,
    /** Given, the page pulls down to refresh. Its spinner is [refreshing]. */
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    // Where the scrolling area begins on screen, so anything inside it can pin itself there. A
    // table header has no other way to know how far it has been scrolled past.
    var viewportTop by remember { mutableFloatStateOf(0f) }
    // How much of the header has been taken, in pixels, between 0 and [HeaderCollapseTravel].
    //
    // Written from the scroll callback below and read in two places: [PageHeader]'s own
    // composition, and the wash's draw lambda. Never read in this function's composition - the page
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
    // The bottom bar gets out of the way while a page is being read, and comes back the moment it is
    // pulled back up. Taken from this page's own scroll position rather than from the gesture, so
    // scrolling a list inside a card - the chat list, the source list - leaves the bar alone.
    val navBarVisible = LocalNavBarVisible.current
    val slop = with(LocalDensity.current) { NavBarScrollSlop.toPx() }
    // Less scroll than this and the bar stays: the page has less left to read than the bar is
    // covering, so moving it out of the way uncovers nothing worth the movement.
    val worthHiding = with(LocalDensity.current) { NavBarFootprint.toPx() } + slop
    LaunchedEffect(scroll, slop, worthHiding) {
        var mark = scroll.value
        var lastMax = scroll.maxValue
        snapshotFlow { scroll.value to scroll.maxValue }.collect { (offset, max) ->
            // **Only a still page is read as a gesture.** The header collapsing gives this page a
            // taller viewport, which shortens `maxValue` - and at the foot of a page a shorter
            // `maxValue` clamps the offset down by the height the header just gave up. That drop is
            // not a reader pulling the page back; it is the page growing under them. Read as a
            // gesture it showed the chrome again, which shrank the viewport, which pushed the
            // offset back down, which hid it again - the chrome flickering for as long as a finger
            // was moving, and never settling hidden. So a frame where the extent changed only
            // re-marks where the page is: the watcher picks up again from there once the header has
            // finished moving.
            if (max != lastMax) {
                lastMax = max
                mark = offset
                return@collect
            }
            when {
                // The top of a page always shows it: there is nothing hidden behind the bar up
                // here, and a page that opens with no navigation showing looks broken.
                offset <= slop -> navBarVisible.value = true
                offset - mark > slop && max > worthHiding -> navBarVisible.value = false
                mark - offset > slop -> navBarVisible.value = true
                else -> return@collect
            }
            mark = offset
        }
    }
    // Pressing the destination already showing means "take me back to the top". Animated rather
    // than jumped, so it reads as the page travelling rather than as the content being replaced -
    // and the watcher above brings the navigation back on its own as the offset passes the slop.
    // The header comes back with it: the scroll that took it is being undone, so leaving it
    // collapsed would strand the one piece of chrome the press is aimed at.
    val scrollToTop = LocalScrollToTop.current
    LaunchedEffect(scrollToTop) {
        if (scrollToTop <= 0) return@LaunchedEffect
        launch { animate(taken.floatValue, 0f) { value, _ -> taken.floatValue = value } }
        scroll.animateScrollTo(0)
    }

    // The bar floats over the page, so the page has to hold its own content out from under it.
    // Beside a rail there is no bar over anything and nothing to hold clear of.
    val compact = LocalWindowWidth.current == WindowWidth.COMPACT
    val barClearance = if (compact) NavBarFootprint else 0.dp
    val page = @Composable {
        Column(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { viewportTop = it.positionInWindow().y }
                .fadingScrollbar(scroll)
                .verticalScroll(scroll)
                .padding(horizontal = Space.l)
                .padding(
                    top = Space.l,
                    bottom = (if (floatingAction == null) Space.xl else FloatingActionInset) +
                        barClearance,
                ),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            content()
        }
    }
    CompositionLocalProvider(LocalViewportTop provides viewportTop) {
    Box(Modifier.fillMaxSize().nestedScroll(headerScroll)) {
        PageWash(scroll) { taken.floatValue }
        Column(Modifier.fillMaxSize()) {
            PageHeader(
                destination = destination,
                // A lambda, not a value. Read here it would be this function recomposing on every
                // frame of a collapse, and this function composes the whole page.
                collapse = { (taken.floatValue / travel).coerceIn(0f, 1f) },
                // The page's own stock filter, or null where the page has no list to narrow. See
                // PageState.stockFilter.
                search = appState.pages.stockFilter(destination),
                current = appState.destination == destination,
                // The flag the filter icon sets; the page below draws the sheet it opens. Null
                // where the page has no filters at all. See PageState.filtersOpen.
                filters = appState.pages.filtersOpen(destination),
                // Only the filters that live in the sheet, so the dot never reports the stock box
                // the reader can already see. See PageState.filtersInSheet.
                filtered = appState.pages.filtersInSheet(destination),
            )
            // Under the header rather than above it, because the header is the top of the window
            // now. Above the page's own content, so a run starting does not push the first card
            // down the screen.
            if (appState.busyLabel != null) {
                LinearProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    // A tint of the page's own hue rather than Material's grey track, so the bar is
                    // one line with a light running through it rather than a grey rail with a
                    // coloured piece on it.
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = BusyTrackAlpha),
                    strokeCap = StrokeCap.Round,
                    // Material punches a gap either side of the moving piece, which on a bar this
                    // wide reads as three separate bars rather than as one thing in motion.
                    gapSize = 0.dp,
                    // Held to the page's own margin. Edge to edge it was the one thing on the
                    // screen touching the frame, on a page whose every card is inset - which is
                    // what made it read as a system bar dropped on top rather than as the app's.
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l),
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
                    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh) { page() }
                }
            }
        }
        floatingAction?.let {
            if (compact) {
                // The action keeps the bar's side margins and its corner, and **never leaves**. It
                // used to go with the bar on the same scroll, so the one control that starts a run
                // could not be reached from anywhere but the top of the page - a poor trade for
                // chrome that tidies itself away.
                //
                // It follows the bar down instead of holding its height. Above the bar it stands
                // clear of it and then the same gap again, so the two read as one stack; with the
                // bar gone it takes the bar's own place, on the bar's own PillBottomMargin, rather
                // than hovering over the hole it left. The travel is exactly NavBarFootprint, which
                // is what makes it land there and not near there.
                val liftedClear = NavBarFootprint + PillBottomMargin
                val lift by animateDpAsState(
                    if (navBarVisible.value) liftedClear else PillBottomMargin,
                    label = "action lift",
                )
                Box(
                    Modifier.align(Alignment.BottomCenter)
                        .padding(start = PillSideMargin, end = PillSideMargin, bottom = lift),
                ) { it() }
            } else {
                // Further in from the corner on a big screen, where the edge is a long way from the
                // content and a button hard against it reads as stuck to the frame. Not stretched
                // and not pinned: beside a rail the navigation is a column at the side, so there is
                // no bar at the foot to match, nothing to travel with, and a full-width action here
                // would be an 88dp slab the width of an unfolded Fold.
                Box(Modifier.align(Alignment.BottomEnd).padding(Space.xl + Space.s)) { it() }
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
 * Whether the bottom navigation bar is showing.
 *
 * Owned by the shell and written by whichever page is on screen. The bar is drawn outside every
 * screen, so a page being scrolled has no other way to tell it to get out of the way.
 */
internal val LocalNavBarVisible = staticCompositionLocalOf { mutableStateOf(true) }

/**
 * Whether the pager holding the five tabs has come to rest.
 *
 * Owned by the shell and written by `DestinationPager`, the same way round as [LocalNavBarVisible]:
 * something outside every screen knows this, and a page has no other way to ask. True beside a rail
 * and true whenever there is no pager at all, because there is then nothing to arrive from.
 *
 * Read by [revealIfOnScreen], which is the only thing that needs it - see the trap written up
 * there. A page composing is not the same event as a reader arriving on it: on a phone the two are
 * a whole travel apart.
 */
internal val LocalTabsSettled = staticCompositionLocalOf { mutableStateOf(true) }

/**
 * How many times this page has been asked to return to the top, or zero.
 *
 * The counterpart of [LocalNavBarVisible] and provided the other way round: the shell writes this
 * and the page reads it. Zero means "nothing asked"; every press after that is a distinct number,
 * which is what makes a second press restart the effect rather than look like the first one again.
 *
 * Provided **per destination** by the shell, so a page only ever sees presses meant for it - see
 * `DestinationScreen`. Not `static`, because it changes.
 */
internal val LocalScrollToTop = compositionLocalOf { 0 }

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
private const val BusyTrackAlpha = 0.14f

/** Enough movement to be a scroll rather than a wobble, so the bar does not flicker on a nudge. */
private val NavBarScrollSlop = 6.dp

/** Height of an extended action plus its margin, so the last card clears it when scrolled to. */
private val FloatingActionInset = 88.dp

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
                Text(title, style = MaterialTheme.typography.titleSmall)
                summary?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            about?.let { InfoButton(it) }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.Inline),
            )
        }
        AnimatedVisibility(expanded) {
            Column(
                Modifier.padding(bottom = Space.m),
                verticalArrangement = Arrangement.spacedBy(Space.m),
                content = content,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    val hue = accent ?: pageAccent.ink
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        border = cardOutline,
    ) {
        // Drawn behind the content rather than as a `Row` beside it: the edge runs the whole height
        // of the card, which is not known until everything inside it has been laid out, and a column
        // that had to reserve width for it would hold that width open on every card in the app.
        // Clipped by the card's own shape, so it takes the corner radius with it.
        Box(
            Modifier.drawBehind {
                drawRect(hue, size = Size(AccentEdgeWidth.toPx(), size.height))
            },
        ) {
        // Vertical only, so the heading band and the content band can be held in by different
        // amounts. Both were one padding on this Column until the nesting above made the two
        // different questions.
        Column(
            Modifier.padding(vertical = Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            if (title != null) {
                Row(
                    Modifier.padding(horizontal = Space.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        // A tile rather than a bare glyph. An icon tinted on its own is a coloured
                        // mark on a card; the same icon on a field of its own hue is the card's
                        // heading having a place, which is what lets a column of cards be told
                        // apart at the speed they are actually scanned.
                        Box(
                            Modifier
                                .size(AccentTileSize)
                                .background(hue.copy(alpha = AccentTileAlpha), MaterialTheme.shapes.small),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = hue,
                                modifier = Modifier.size(IconSize.Inline),
                            )
                        }
                        Spacer(Modifier.width(Space.s))
                    }
                    Text(
                        title,
                        // Weighted only when something sits after it, so a card without an
                        // explanation keeps a heading that is as wide as its own words.
                        modifier = if (about == null) Modifier else Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // Dense: the 48dp target on a plain IconButton would make this header band
                    // taller than the cards beside it on the page that carry no explanation.
                    about?.let { InfoButton(it, dense = true) }
                }
                // The same hairline the chrome uses to separate the app header from the page, so a
                // card says where its heading ends the way the app does. Only with a title: there is
                // nothing to separate without one.
                HorizontalDivider(
                    Modifier.padding(horizontal = Space.l),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            Column(
                Modifier.padding(horizontal = contentInset),
                verticalArrangement = Arrangement.spacedBy(Space.s),
                content = content,
            )
        }
        }
    }
}

/**
 * The page's own hue at the top of the page, faded to nothing.
 *
 * An arrival cue and not a paint job: it announces which page this is in the moment it appears and
 * then gets out of the way, because a permanent tint behind the first card would be one more thing
 * every card on the page has to be read against.
 *
 * **Faded on the scroll rather than pinned**, and the scroll is read inside the draw lambda for the
 * reason [PageHeader]'s collapse is passed as a lambda: read at composition, every frame of a
 * scroll would recompose the whole page, where here a frame costs one rectangle repainted.
 */
@Composable
private fun PageWash(scroll: ScrollState, taken: () -> Float) {
    val wash = pageAccent.wash
    Box(
        Modifier
            .fillMaxWidth()
            .height(PageWashHeight)
            .drawBehind {
                // What the header ate counts as scroll here, or the wash would sit at full strength
                // through the whole collapse and only begin to fade once the page itself moved.
                val left = (1f - (scroll.value + taken()) / size.height).coerceIn(0f, 1f)
                if (left <= 0f) return@drawBehind
                drawRect(
                    Brush.verticalGradient(
                        listOf(wash.copy(alpha = wash.alpha * left), Color.Transparent),
                    ),
                )
            },
    )
}

/**
 * How far down the page the wash reaches, and so how far it takes to scroll it away.
 *
 * Measured from the top of the *window* rather than from the top of the content, since the page
 * runs up behind the status bar now - see `AppContent`. The extra 40dp over the old 120 is roughly
 * the bar it has to cover before it starts on the page, so the tint fades over the same stretch of
 * reading as it did rather than appearing to burn off faster.
 */
private val PageWashHeight = 160.dp

/** A hairline of the card's own hue. Wider and it is a stripe the content has to sit clear of. */
private val AccentEdgeWidth = 3.dp

/** Room for [IconSize.Inline] with a margin, on the tile behind a section card's icon. */
private val AccentTileSize = 26.dp

/** Enough that the tile is a field of colour, little enough that the glyph on it stays the figure. */
private const val AccentTileAlpha = 0.16f

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
    /**
     * Colour for [icon], where the card's own **state** is what it should be saying.
     *
     * A session on Insights is the case: how that session went is the card's whole subject, and the
     * icon is where it can be said before a word is read. It outranks [accent] when given, because a
     * card reporting its own state has something to say that its place in a column does not.
     *
     * It used to argue that colouring each icon would be a page of noise, and every card took
     * `primary`. That was true of a page of identical grey headings and is not true of a tile per
     * card in a hue the page keeps to - see [accent].
     */
    iconTone: Color? = null,
    /**
     * This card's own hue, on the tile behind [icon] and the edge down its left side.
     *
     * [SectionCard.accent]'s rule exactly, and for the reason given there: chrome, never a figure.
     * Null takes the page's own, which is what the first card on a page should have.
     */
    accent: Color? = null,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    /** One line under the title saying what is inside, so a closed card still informs. */
    summary: String? = null,
    /** Colour for [summary], where the figure itself carries a verdict. */
    summaryTone: Color? = null,
    /**
     * Draws that line itself, for a summary whose parts want different colours.
     *
     * Takes the place of [summary] where it is given. A plain string covers almost every card in the
     * app, and making all of them build one would be paying for a Portfolio card's state counts
     * everywhere else.
     */
    summaryContent: (@Composable () -> Unit)? = null,
    /** Caps the content, for groups of form controls: a text field the width of a desk is unusable. */
    contentMaxWidth: Dp? = null,
    /**
     * How far the content is held in from the card's edge, on [SectionCard]'s rule.
     *
     * [Space.s] where the children are cards - the child's own border is the edge, and a full inset
     * at every level of a nest is what left a trade card 285dp of a 411dp screen. The header row and
     * the rule under it keep their 16 whatever this is.
     */
    contentInset: Dp = Space.l,
    /**
     * What the whole group is for, behind a question mark in the header.
     *
     * Beside the chevron rather than inside the card, on purpose: a section-wide explanation used to
     * be the first thing under the fold, so it was read once and then stood between the reader and
     * the controls on every later visit. Here it is reachable without opening the card at all.
     */
    about: InfoNote? = null,
    /** Hoisted when the layout around it needs to know: an open card claims the whole row. */
    expandedState: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    /**
     * The card's own fill, for one drawn **inside** another card.
     *
     * `surfaceContainer` is right on the page, where the well behind it is darker. Nested in a card
     * of the same fill it disappears into its parent, so a card within a card goes one step up -
     * the rule `OverdueTile` and `EventTile` already follow with `surfaceContainerHigh`. Named
     * rather than assumed, because only the caller knows what it is sitting on.
     */
    containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var localExpanded by remember { mutableStateOf(initiallyExpanded) }
    val expanded = expandedState ?: localExpanded
    val hue = accent ?: pageAccent.ink
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor ?: MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.large,
        border = cardOutline,
    ) {
        Column(
            // SectionCard's edge, drawn the same way and for the same reason.
            Modifier.drawBehind {
                drawRect(hue, size = Size(AccentEdgeWidth.toPx(), size.height))
            },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (onExpandedChange != null) onExpandedChange(!expanded) else localExpanded = !expanded
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    // SectionCard's tile, so a group heading and a card heading are the same object
                    // at a glance. iconTone wins where a card is reporting its own state.
                    val ink = iconTone ?: hue
                    Box(
                        Modifier
                            .size(AccentTileSize)
                            .background(ink.copy(alpha = AccentTileAlpha), MaterialTheme.shapes.small),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = ink,
                            modifier = Modifier.size(IconSize.Inline),
                        )
                    }
                    Spacer(Modifier.width(Space.s))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    when {
                        summaryContent != null -> summaryContent()
                        summary != null -> Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = summaryTone ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                about?.let { InfoButton(it) }
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(expanded) {
                Column {
                    // Inside the reveal rather than above it: a closed card would otherwise carry a
                    // rule along its bottom edge with nothing under it.
                    HorizontalDivider(
                        Modifier.padding(horizontal = Space.l),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Column(
                        Modifier
                            .padding(start = contentInset, end = contentInset, top = Space.s, bottom = Space.l)
                            .then(contentMaxWidth?.let { Modifier.widthIn(max = it) } ?: Modifier),
                        verticalArrangement = Arrangement.spacedBy(Space.s),
                        content = content,
                    )
                }
            }
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
 * @param onShown fires once the flash has run, so the caller can forget the arrival.
 */
@Composable
internal fun arrivalFlash(highlighted: Boolean, onShown: () -> Unit): BorderStroke? {
    if (!highlighted) return null
    val flash = rememberInfiniteTransition(label = "arrival")
    val edge by flash.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(FlashHalfCycleMs), RepeatMode.Reverse),
        label = "edge",
    )
    LaunchedEffect(Unit) {
        delay(FlashDurationMs)
        onShown()
    }
    return BorderStroke(FlashOutline, MaterialTheme.colorScheme.primary.copy(alpha = edge))
}

/** The same weight as the held outline, so a flash does not resize the card it lands on. */
private val FlashOutline = 2.dp

private const val FlashHalfCycleMs = 420

/** Long enough to be caught by someone whose eyes are still moving, short enough not to nag. */
private const val FlashDurationMs = 2_400L

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
@Composable
internal fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.colorScheme.onSurface,
    /** Centred where the tile is one cell of a divided strip, so figures line up under each other. */
    alignment: Alignment.Horizontal = Alignment.Start,
    /**
     * A step down, for a strip that annotates a card rather than being what the card is for.
     *
     * The report card carries four of these over everything it says about a run, where the
     * Portfolio's are the record itself and lead their own page. Same tile either way - a second
     * composable for a smaller figure is how the label under one drifts from the label under the
     * other - and the caller says which of the two jobs it is doing.
     */
    dense: Boolean = false,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(if (dense) DenseFigureGap else Space.xs),
        horizontalAlignment = alignment,
    ) {
        // The role sets the size; the figure sets its own face. Everything a tile ever holds is a
        // number, and the display face is for names.
        Text(
            value,
            style = if (dense) {
                MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFigures)
            } else {
                MaterialTheme.typography.headlineSmall.copy(fontFamily = TabularFigures)
            },
            color = tone,
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Tighter than [Space.xs], which read as two facts where a dense tile is meant to read as one. */
private val DenseFigureGap = 2.dp

/**
 * Every button on a card that changes the record: bought, sold, kept open.
 *
 * One shape and one colour for the three of them, because they are one kind of act. They were a
 * filled tonal button and two outlined ones, which said that recording a purchase is a heavier
 * thing than recording a sale - and on a position card the two sat in the same row disagreeing
 * about it. What a card's buttons say now is what pressing costs: a ring in the app's own cyan
 * changes something, a bare label with an arrow ([DisclosureButton]) only opens something, and
 * violet is still the model's alone. The screen's own action keeps the teal aurora, a tier above
 * anything drawn on a card; a dialog's buttons stay Material's, because that is a convention the
 * app did not invent and must not fork.
 *
 * A ring rather than a fill, for the reason [OutlinePill] is one: a position card can carry three
 * of these at once, and three blocks of colour compete with the figures they sit under.
 *
 * [PillHeight] like every other pill here, and the target under it is still the full 48dp -
 * `minimumInteractiveComponentSize` is what separates how big a button looks from how big a
 * fingertip is.
 */
@Composable
internal fun ActionPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val ink = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .height(PillHeight)
            .border(ActionRing, ink.copy(alpha = ActionRingAlpha), CircleShape)
            // After the edge, so the ripple is bounded by the pill rather than by the row.
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(IconSize.Hint))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A hairline, so a row of these never competes with the edge of the card holding them. */
private val ActionRing = 1.dp

/**
 * Half strength.
 *
 * The label is the button and the ring is only its boundary; at full strength a row of two came out
 * as two bright wires on a dark card - the same fault the action's own edge was taken down for.
 */
private const val ActionRingAlpha = 0.5f

/**
 * An action on a settings card, at the size a settings card wants.
 *
 * Material's own button is 40dp tall with 24dp of padding on each side, which is the size of a
 * button that is the point of the screen it is on. Down a list of settings it is not: it is one row
 * among twenty, standing beside switches 32dp tall, and at the default it was the heaviest thing on
 * a card whose subject is the words next to it. [PillHeight] and [Space.m] put it at the height
 * every other button in this app is already drawn at.
 *
 * It stays an [OutlinedButton] rather than becoming an [ActionPill], and the two are different
 * things rather than one thing drawn twice. A pill sits **on a card about one call or one trade**
 * and takes the page's own hue to say that pressing it changes the record. These sit on a page of
 * settings, several to a card, half of them opening a picker rather than changing anything - and a
 * column of cyan rings down Settings would be the page of coloured glyphs the question mark is
 * muted to avoid.
 *
 * `labelMedium`, which is what both card buttons already carry: at this height Material's
 * `labelLarge` is a 14sp line in a 32dp box, and the button reads as the text having outgrown it.
 *
 * This is the pass of 2026-09-03 finished. It stopped at the cards and left Settings, Channels,
 * Backup and Schedules holding about forty buttons in the old mixed state.
 *
 * @param filled the card's own primary action, where it has one - Save and verify, Sync now,
 *   Download. It carries **both** shapes rather than leaving the filled ones at Material's height,
 *   because they share a row with the outlined ones: Save and verify stands in a `FlowRow` beside
 *   Reset provider and Remove credential, and a row 40dp tall at one end and 32 at the other reads
 *   as a layout fault rather than as an emphasis. Which button is filled is unchanged; only how
 *   tall the row is.
 */
@Composable
internal fun SettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    // Ahead of Material's own `defaultMinSize`, which raises a minimum only where nothing has set
    // one - so this wins by being applied first, and the button is not fighting a 40dp floor it can
    // never get under.
    val sized = modifier.heightIn(min = PillHeight)
    val padding = PaddingValues(horizontal = Space.m)
    // The row is held rather than passed through: `ProvideTextStyle` takes a plain composable, so
    // inside it the button's own `RowScope` is out of scope and an icon's `align` would not resolve.
    val label: @Composable RowScope.() -> Unit = {
        val row = this
        ProvideTextStyle(MaterialTheme.typography.labelMedium) { row.content() }
    }
    if (filled) {
        Button(onClick, sized, enabled, contentPadding = padding, content = label)
    } else {
        OutlinedButton(onClick, sized, enabled, contentPadding = padding, content = label)
    }
}

/**
 * The button that opens or closes a section of the card it sits on, and does nothing else.
 *
 * The arrow is the state rather than the label: down where there is more under it, up where what is
 * under it is showing. No ring, because [ActionPill]'s ring is what says a press changes something,
 * and a control that only moves the page must not borrow it.
 */
@Composable
internal fun DisclosureButton(
    label: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .height(PillHeight)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(IconSize.Hint),
        )
    }
}

internal enum class StatusTone { GOOD, BAD, NEUTRAL }

/** Short status line, coloured by whether it reports something good, bad, or neutral. */
@Composable
internal fun StatusPill(text: String, tone: StatusTone = StatusTone.NEUTRAL) {
    val outline = when (tone) {
        StatusTone.GOOD -> MaterialTheme.colorScheme.tertiary
        StatusTone.BAD -> MaterialTheme.colorScheme.error
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.outline
    }
    val ink = when (tone) {
        StatusTone.GOOD -> MaterialTheme.colorScheme.onTertiaryContainer
        StatusTone.BAD -> MaterialTheme.colorScheme.onErrorContainer
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    OutlinePill(text, outline = outline, textColor = ink)
}

/**
 * Every pill in the app, and the only place the shape is described.
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
    val label: @Composable () -> Unit = {
        Text(
            text,
            // A step under `labelMedium`, set as a copy of it rather than as `labelSmall`: that
            // style is tracked out for uppercase keys over figures, and a pill is a sentence.
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = PillText,
                lineHeight = PillLine,
            ),
            modifier = Modifier.padding(horizontal = PillPaddingH, vertical = PillPaddingV),
        )
    }
    val ring = BorderStroke(PillOutline, outline)
    if (onClick == null) {
        Surface(
            color = Color.Transparent,
            contentColor = textColor,
            shape = CircleShape,
            border = ring,
            content = label,
        )
    } else {
        Surface(
            onClick = onClick,
            color = Color.Transparent,
            contentColor = textColor,
            shape = CircleShape,
            border = ring,
            content = label,
        )
    }
}

// 20dp of pill: an 11sp line with 3dp of air over and under it, inside a hairline.
private val PillText = 11.sp
private val PillLine = 14.sp
private val PillPaddingH = 6.dp
private val PillPaddingV = 3.dp

/** A hairline. A card's own held outline is twice it, so a pill never competes with the edge. */
private val PillOutline = 1.dp

/**
 * The gap in a stack of pills, which is tighter than [Space.xs].
 *
 * Rings need less air between them than blocks of colour did: at 4dp the stack read as three
 * separate marks rather than one column saying three things about the same trade.
 */
internal val PillStackGap: Dp = 3.dp

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
 * Square rather than the round ring the other pills use, and that is not a free choice. Round, its
 * outline sat concentric with the eight-pointed star inside it, and two rings around a small dark
 * shape is the shape of a settings cog - which is what it read as. The corner is the theme's own
 * `extraSmall`, so no new radius enters the shape scale.
 */
@Composable
internal fun Egx33Badge(ticker: String, modifier: Modifier = Modifier) {
    if (!isEgx33(ticker)) return
    Box(
        modifier
            .size(Egx33BadgeSize)
            .border(PillOutline, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_egx33),
            // Said in full, because nothing on screen says it. The glyph is the only place this
            // fact appears, so a reader who cannot see it would otherwise not be told at all.
            contentDescription = "EGX 33 Shariah index",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Egx33GlyphSize),
        )
    }
}

/**
 * 24dp, which is [LogoSize.Row].
 *
 * One size on all three screens rather than one per ticker style. It shares a line with a 27dp
 * headline on Results and a titleSmall on Insights, and sized to each it was two different marks;
 * sized to the row logo it is the same object wherever the reader meets it, and never the tallest
 * thing on its line.
 */
private val Egx33BadgeSize: Dp = 24.dp

/** 14dp of glyph, which leaves 5dp of air on every side of it inside the hairline. */
private val Egx33GlyphSize: Dp = 14.dp


/** Placeholder for a screen with nothing to show yet, so empty states explain themselves. */
@Composable
internal fun EmptyState(icon: ImageVector, title: String, detail: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        border = cardOutline,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = CircleShape) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp).size(28.dp),
                    )
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
internal fun FigureGroup(title: String, figures: List<@Composable RowScope.() -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints {
            // Taken as a list rather than a row of slots, because wrapping means splitting them -
            // and a lambda that draws four figures cannot be cut in half.
            //
            // Capped rather than "all of them on a wide screen": the threshold below was measured
            // for four prices, and a group of five would put all five on one row at a width that
            // was only ever proved to hold four.
            val perRow = if (maxWidth >= FourFiguresMinWidth) {
                minOf(figures.size, MaxFiguresPerRow)
            } else {
                2
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                figures.chunked(perRow).forEach { row ->
                    // A short last row spreads across the width rather than being padded out to
                    // the column count. Every figure here already carries `weight(1f)`, so dropping
                    // the spacers is what lets the odd one out have the room.
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
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
    tone: Color = MaterialTheme.colorScheme.onSurface,
    on: LocalDate? = null,
    caption: String? = null,
    /**
     * Overridden only where the value is not a price.
     *
     * The default monospaces the digits, because a column of figures is read down as well as
     * across and proportional digits put the same price at a different width on every card. A
     * deadline reads "3 of 10 left", and monospacing prose sets it apart from the prices for no
     * reason; the occurrence sheet sets its figures larger, being a sheet about one call.
     */
    valueStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFigures),
) {
    Column(modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = valueStyle, color = tone, textAlign = TextAlign.Start)
        val note = caption ?: on?.let(AppDates.DayMonth::format)
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
