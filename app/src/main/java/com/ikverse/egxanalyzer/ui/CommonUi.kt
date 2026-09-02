package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import com.ikverse.egxanalyzer.R
import com.ikverse.egxanalyzer.data.EgxCatalog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import java.time.LocalDate

/**
 * Standard page frame: a large title that scrolls away with the content.
 *
 * The title scrolls rather than sitting in a fixed app bar so these screens, which are long, get
 * the full height on a phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Screen(
    title: String,
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
        snapshotFlow { scroll.value to scroll.maxValue }.collect { (offset, max) ->
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
    val scrollToTop = LocalScrollToTop.current
    LaunchedEffect(scrollToTop) {
        if (scrollToTop > 0) scroll.animateScrollTo(0)
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
            Text(title, Modifier.padding(start = PageTextInset), style = MaterialTheme.typography.headlineLarge)
            content()
        }
    }
    CompositionLocalProvider(LocalViewportTop provides viewportTop) {
    Box(Modifier.fillMaxSize()) {
        if (onRefresh == null) {
            page()
        } else {
            // The gesture wraps only the scrolling page: a floating button that slid down with the
            // indicator would look like it had come loose.
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh) { page() }
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
                //
                // This is the rule `toastClearance` in the shell already follows, for the same
                // reason and in the same words - "so a toast raised on a scrolled page does not
                // hang over the gap where the bar used to be". The action was the one piece of
                // floating chrome not following it.
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
 */
@Composable
internal fun SectionCard(
    title: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    about: InfoNote? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        border = cardOutline,
    ) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(IconSize.Inline),
                        )
                        Spacer(Modifier.width(Space.s))
                    }
                    Text(
                        title,
                        // Weighted only when something sits after it, so a card without an
                        // explanation keeps a heading that is as wide as its own words.
                        modifier = if (about == null) Modifier else Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    about?.let { InfoButton(it) }
                }
                // The same hairline the chrome uses to separate the app header from the page, so a
                // card says where its heading ends the way the app does. Only with a title: there is
                // nothing to separate without one.
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            content()
        }
    }
}

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
     * Colour for [icon], where the card's own state is what it should be saying.
     *
     * `primary` everywhere else, and that is the right default: on a page of settings the icon is a
     * bullet, and colouring each one would be a page of noise. A session on Insights is different -
     * how that session went is the card's whole subject, and the icon is where it can be said
     * before a word is read.
     */
    iconTone: Color? = null,
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
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor ?: MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.large,
        border = cardOutline,
    ) {
        Column {
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
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTone ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(IconSize.Inline),
                    )
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
                            .padding(start = Space.l, end = Space.l, top = Space.s, bottom = Space.l)
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

/** A single figure with its label, for wherever a screen summarises counts or totals. */
@Composable
internal fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.colorScheme.onSurface,
    /** Centred where the tile is one cell of a divided strip, so figures line up under each other. */
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(Space.xs),
        horizontalAlignment = alignment,
    ) {
        // The role sets the size; the figure sets its own face. Everything a tile ever holds is a
        // number, and the display face is for names.
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = TabularFigures),
            color = tone,
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    if (!EgxCatalog.isEgx33(ticker)) return
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
