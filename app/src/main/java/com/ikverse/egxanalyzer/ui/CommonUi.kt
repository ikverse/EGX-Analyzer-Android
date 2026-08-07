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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
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
    /** Says what the pull does. Carried with [onRefresh] so it cannot appear on a page without it. */
    refreshHint: String? = null,
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
    LaunchedEffect(scroll, slop) {
        var mark = scroll.value
        snapshotFlow { scroll.value }.collect { offset ->
            when {
                // The top of a page always shows it: there is nothing to reclaim up here, and a
                // page that opens with no navigation showing looks broken.
                offset <= slop -> navBarVisible.value = true
                offset - mark > slop -> navBarVisible.value = false
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
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(title, style = MaterialTheme.typography.headlineLarge)
                if (onRefresh != null && refreshHint != null) PullHint(refreshHint)
            }
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

/**
 * A gesture with nothing on screen to suggest it is a gesture is one nobody finds.
 *
 * Drawn here rather than only under a page title, because the pull does not always belong to the
 * page as a whole - on Analyze it refreshes one card's contents, and the hint belongs with them.
 */
@Composable
internal fun PullHint(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.ArrowDownward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(PullHintIcon),
        )
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Sized to the label beside it rather than to the icon grid, which would tower over it. */
private val PullHintIcon = 14.dp

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
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
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

/** Placeholder for a screen with nothing to show yet, so empty states explain themselves. */
@Composable
internal fun EmptyState(icon: ImageVector, title: String, detail: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
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
