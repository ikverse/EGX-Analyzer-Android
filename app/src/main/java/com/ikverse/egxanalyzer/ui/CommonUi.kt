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
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
    // Less scroll than this and the bar stays: the page has less to give than the bar hands back, so
    // hiding it shows nothing new and leaves too little scroll to bring it back.
    val worthHiding = with(LocalDensity.current) { NavBarReclaimedHeight.toPx() } + slop
    LaunchedEffect(scroll, slop, worthHiding) {
        var mark = scroll.value
        var extent = scroll.maxValue
        snapshotFlow { scroll.value to scroll.maxValue }.collect { (offset, max) ->
            // A change in how far the page can scroll is the layout moving the page, not the reader
            // scrolling it. The bar leaving hands its height back, the page grows by it, and the
            // scroll range shrinks by it; at the foot of a page the offset is then clamped to the
            // shorter range and drops the height of the bar. Read as a scroll that would be a scroll
            // upwards, which fetches the bar straight back, and the next scroll down sends it away
            // again - the bar flickering on and off at the end of every page.
            val relaid = max != extent
            extent = max
            when {
                // The top of a page always shows it: there is nothing to reclaim up here, and a
                // page that opens with no navigation showing looks broken. Ahead of the relayout
                // check, so content shrinking under a hidden bar cannot strand it hidden up here -
                // and safe there because a page short enough for the clamp to reach the top is a
                // page the bar never hides on.
                offset <= slop -> navBarVisible.value = true
                relaid -> Unit
                offset - mark > slop && max > worthHiding -> navBarVisible.value = false
                mark - offset > slop -> navBarVisible.value = true
                else -> return@collect
            }
            mark = offset
        }
    }
    val page = @Composable {
        Column(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { viewportTop = it.positionInWindow().y }
                .fadingScrollbar(scroll)
                .verticalScroll(scroll)
                .padding(horizontal = Space.l)
                .padding(top = Space.l, bottom = if (floatingAction == null) Space.xl else FloatingActionInset),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
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
            // Further in from the corner on a big screen, where the edge is a long way from the
            // content and a button hard against it reads as stuck to the frame.
            val inset = if (LocalWindowWidth.current == WindowWidth.COMPACT) Space.l else Space.xl + Space.s
            Box(Modifier.align(Alignment.BottomEnd).padding(inset)) { it() }
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

/** A titled group of related controls, replacing the loose outlined boxes used before. */
@Composable
internal fun SectionCard(
    title: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
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
                    Text(title, style = MaterialTheme.typography.titleMedium)
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
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    /** One line under the title saying what is inside, so a closed card still informs. */
    summary: String? = null,
    /** Colour for [summary], where the figure itself carries a verdict. */
    summaryTone: Color? = null,
    /** Caps the content, for groups of form controls: a text field the width of a desk is unusable. */
    contentMaxWidth: Dp? = null,
    /** Hoisted when the layout around it needs to know: an open card claims the whole row. */
    expandedState: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var localExpanded by remember { mutableStateOf(initiallyExpanded) }
    val expanded = expandedState ?: localExpanded
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(IconSize.Inline),
                    )
                    Spacer(Modifier.width(Space.s))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (summary != null) {
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = summaryTone ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
        Text(value, style = MaterialTheme.typography.headlineSmall, color = tone)
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
    val container = when (tone) {
        StatusTone.GOOD -> MaterialTheme.colorScheme.tertiaryContainer
        StatusTone.BAD -> MaterialTheme.colorScheme.errorContainer
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val onContainer = when (tone) {
        StatusTone.GOOD -> MaterialTheme.colorScheme.onTertiaryContainer
        StatusTone.BAD -> MaterialTheme.colorScheme.onErrorContainer
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = container, shape = CircleShape) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = onContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * What an action reports, and whether it worked.
 *
 * Material's snackbar carries a string and nothing else, so a tone the state already knows is lost
 * on the way to the toast that draws it unless it travels as visuals.
 */
internal class ToastVisuals(
    override val message: String,
    val succeeded: Boolean,
) : SnackbarVisuals {
    override val actionLabel: String? = null

    // Drawn by the toast itself as a glyph. Material's dismiss action is a text button, which is
    // wider than most of these messages are.
    override val withDismissAction: Boolean = false

    /** A confirmation gets out of the way; a failure stays long enough to be read. */
    override val duration: SnackbarDuration =
        if (succeeded) SnackbarDuration.Short else SnackbarDuration.Long
}

/**
 * The app's own toast, in the app's own colours.
 *
 * Material draws its snackbar on `inverseSurface`, which a dark scheme leaves as the baseline
 * near-white: a light slab in the middle of a dark app. This is the card recipe instead - a
 * container step, a hairline, the medium corner - so a message reads as part of the screen it
 * interrupts rather than as something pasted over it.
 *
 * Whether the action worked is carried by one tinted glyph. Colouring the whole bar would make
 * every routine confirmation the loudest thing on screen, and there is one after almost every tap.
 */
@Composable
internal fun AppToast(data: SnackbarData) {
    // Anything not raised through ToastVisuals is a plain message with nothing to report against it.
    val succeeded = (data.visuals as? ToastVisuals)?.succeeded != false
    Box(Modifier.fillMaxWidth().padding(Space.m)) {
        Surface(
            // Sized to its message and left where the content starts, rather than stretched across
            // it: on the inner display and on a tablet a full-width bar for three words is a stripe.
            Modifier.widthIn(max = ToastMaxWidth).clickable { data.dismiss() },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = ToastElevation,
        ) {
            Row(
                Modifier.padding(horizontal = Space.m, vertical = Space.s),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (succeeded) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    // The message says what happened; a reader announcing the tone as well says it
                    // twice.
                    contentDescription = null,
                    tint = if (succeeded) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(IconSize.Inline),
                )
                Text(
                    data.visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                    // Two lines is the ceiling. Anything needing more than that is a screen, not a
                    // toast, and a provider's own error message can run to a paragraph.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    // fill = false so a short message keeps a short toast: weight alone would pad
                    // every one of them out to the full width.
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Inline),
                )
            }
        }
    }
}

/** Wide enough for two lines of a short message, and no wider. */
private val ToastMaxWidth = 400.dp

/** Enough to lift it off the page it covers. The hairline does the rest of the separating. */
private val ToastElevation = 6.dp

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
