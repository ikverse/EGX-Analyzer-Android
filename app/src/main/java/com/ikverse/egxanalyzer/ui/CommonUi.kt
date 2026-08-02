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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
@Composable
internal fun Screen(
    title: String,
    subtitle: String,
    /** Stays put while the page scrolls. The content reserves room so it never covers anything. */
    floatingAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .fadingScrollbar(scroll)
                .padding(horizontal = Space.l)
                .padding(top = Space.l, bottom = if (floatingAction == null) Space.xl else FloatingActionInset),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(title, style = MaterialTheme.typography.headlineLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
        floatingAction?.let {
            Box(Modifier.align(Alignment.BottomEnd).padding(Space.l)) { it() }
        }
    }
}

/** Height of an extended action plus its margin, so the last card clears it when scrolled to. */
private val FloatingActionInset = 88.dp

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
    /** Larger type for a group that leads a screen rather than sitting in a list of peers. */
    prominent: Boolean = false,
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
                        modifier = Modifier.size(if (prominent) IconSize.Action else IconSize.Inline),
                    )
                    Spacer(Modifier.width(Space.s))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = if (prominent) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                    )
                    if (summary != null) {
                        Text(
                            summary,
                            style = if (prominent) {
                                MaterialTheme.typography.bodyLarge
                            } else {
                                MaterialTheme.typography.bodySmall
                            },
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
                Column(
                    Modifier
                        .padding(start = Space.l, end = Space.l, bottom = Space.l)
                        .then(contentMaxWidth?.let { Modifier.widthIn(max = it) } ?: Modifier),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                    content = content,
                )
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
