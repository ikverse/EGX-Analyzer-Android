package com.ikverse.egxanalyzer.next

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ikverse.egxanalyzer.ui.AppDestination
import java.util.Locale

/** A press, and the word on it. */
@Immutable
internal data class ShellAction(val label: String, val onClick: () -> Unit)

/**
 * The strip across the top of everything: a new version, and what can be done about it.
 *
 * It lives on the shell rather than on a screen because it is true of the app rather than of
 * anything the reader is looking at.
 */
@Immutable
internal data class ShellBanner(
    val label: String,
    val text: String,
    /** Which role this is speaking in - amber while it waits, red once it has failed. */
    val tone: Color,
    val primary: ShellAction?,
    val secondary: ShellAction?,
)

/** Something happened, it worked, and the reader may want it back. */
@Immutable
internal data class ShellToast(val text: String, val action: ShellAction?)

/**
 * Something stopped, and the reader has to answer before anything else.
 *
 * Distinct from a toast by construction rather than by colour: this one takes the screen.
 */
@Immutable
internal data class ShellError(
    val kicker: String,
    val title: String,
    val body: String,
    val primary: ShellAction?,
    val dismiss: ShellAction,
)

/**
 * The frame every screen is drawn inside.
 *
 * Five destinations, and one arrangement per width: a bar across the bottom on a phone, a rail down
 * the leading edge once the window passes [NextMetrics.railBreakpoint] - which is the phone
 * unfolding, and which the shell treats as one event rather than as two layouts. Measured on the
 * window, so an app resized into a small pane gets the phone arrangement and is right to.
 *
 * The three things that are true of the app rather than of a screen live here and nowhere else: the
 * update banner above the content, a toast above the bar, and a blocking error over all of it. A
 * screen never draws its own.
 *
 * [content] is handed the padding it has to keep clear at the bottom - the bar, plus whatever the
 * system asks for under it. Horizontal padding is applied here, since the banner shares it.
 */
@Composable
internal fun NextShell(
    destination: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    /** What the app is doing, shown in the rail's mark. Empty when there is nothing to say. */
    status: String,
    statusTone: Color,
    banner: ShellBanner?,
    toast: ShellToast?,
    error: ShellError?,
    /**
     * Whatever the current screen is asking about.
     *
     * On the shell rather than inside the screen because a modal covers the navigation too - a
     * sheet asking whether to erase a trade, drawn inside the content pane, would leave the bar
     * live underneath it and the reader able to walk away mid-question.
     */
    modal: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = LocalNextColors.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.ground),
    ) {
        val rail = maxWidth >= NextMetrics.railBreakpoint

        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val direction = LocalLayoutDirection.current
        val insetStart = insets.calculateStartPadding(direction)
        val insetEnd = insets.calculateEndPadding(direction)
        val insetTop = insets.calculateTopPadding()
        val insetBottom = insets.calculateBottomPadding()

        // The fold, as four beats rather than one cut.
        //
        // The navigation goes first and everything the page is inset by follows it, each one a
        // stagger step behind the last: the rail arrives, the leading edge gives way to it, the
        // trailing edge catches up, and the bar's old space at the bottom closes last. Same curve
        // throughout - it is one movement seen in four places, not four animations.
        fun beat(step: Int) = tween<Dp>(
            durationMillis = NextMotion.OPEN_MILLIS,
            delayMillis = NextMotion.STAGGER_MILLIS * step,
            easing = NextMotion.hinge,
        )
        val contentStart by animateDpAsState(
            targetValue = insetStart + if (rail) {
                NextMetrics.navRailWidth + NextMetrics.screenPaddingWide
            } else {
                NextMetrics.screenPadding
            },
            animationSpec = beat(1),
            label = "shellContentStart",
        )
        val contentEnd by animateDpAsState(
            targetValue = insetEnd +
                if (rail) NextMetrics.screenPaddingWide else NextMetrics.screenPadding,
            animationSpec = beat(2),
            label = "shellContentEnd",
        )
        val contentBottom by animateDpAsState(
            targetValue = insetBottom + NextMetrics.space7 +
                if (rail) 0.dp else NextMetrics.navBarHeight,
            animationSpec = beat(3),
            label = "shellContentBottom",
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(start = contentStart, end = contentEnd, top = insetTop),
        ) {
            if (banner != null) ShellUpdateBanner(banner)
            Box(Modifier.fillMaxSize()) {
                content(PaddingValues(bottom = contentBottom))
            }
        }

        AnimatedVisibility(
            visible = !rail,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(NextMotion.DESTINATION_MILLIS, easing = NextMotion.linear)) +
                slideInVertically(tween(NextMotion.FOLD_MILLIS, easing = NextMotion.hinge)) { it },
            exit = fadeOut(tween(NextMotion.EXIT_MILLIS, easing = NextMotion.linear)) +
                slideOutVertically(tween(NextMotion.FOLD_MILLIS, easing = NextMotion.exit)) { it },
        ) {
            NavBar(
                destination = destination,
                onNavigate = onNavigate,
                insetBottom = insetBottom,
            )
        }

        AnimatedVisibility(
            visible = rail,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = fadeIn(tween(NextMotion.DESTINATION_MILLIS, easing = NextMotion.linear)) +
                slideInHorizontally(tween(NextMotion.FOLD_MILLIS, easing = NextMotion.hinge)) { -it },
            exit = fadeOut(tween(NextMotion.EXIT_MILLIS, easing = NextMotion.linear)) +
                slideOutHorizontally(tween(NextMotion.FOLD_MILLIS, easing = NextMotion.exit)) { -it },
        ) {
            NavRail(
                destination = destination,
                onNavigate = onNavigate,
                status = status,
                statusTone = statusTone,
                insetStart = insetStart,
                insetTop = insetTop,
                insetBottom = insetBottom,
            )
        }

        modal()

        // Both overlays outlive the state that raised them, for exactly as long as it takes them to
        // leave. Without this the toast would vanish on the frame its message was cleared, which is
        // the one thing an undo affordance must not do.
        var lastToast by remember { mutableStateOf<ShellToast?>(null) }
        if (toast != null) lastToast = toast
        AnimatedVisibility(
            visible = toast != null,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = contentStart,
                    end = contentEnd,
                    bottom = insetBottom + NextMetrics.space5 +
                        if (rail) 0.dp else NextMetrics.navBarHeight,
                ),
            enter = fadeIn(tween(NextMotion.DESTINATION_MILLIS, easing = NextMotion.linear)) +
                slideInVertically(
                    tween(NextMotion.DESTINATION_MILLIS, easing = NextMotion.hinge),
                ) { it / 3 },
            exit = fadeOut(tween(NextMotion.EXIT_MILLIS, easing = NextMotion.linear)),
        ) {
            lastToast?.let { ShellToastBar(it) }
        }

        var lastError by remember { mutableStateOf<ShellError?>(null) }
        if (error != null) lastError = error
        AnimatedVisibility(
            visible = error != null,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(NextMotion.FIGURE_MILLIS, easing = NextMotion.linear)),
            exit = fadeOut(tween(NextMotion.EXIT_MILLIS, easing = NextMotion.linear)),
        ) {
            lastError?.let { ShellErrorModal(it, wide = rail) }
        }
    }
}

/**
 * The bar, on a phone.
 *
 * Chrome at 94 percent rather than opaque: the one place in the app where a surface admits there is
 * something underneath it, because a list that ends exactly at the bar reads as a list that ended.
 */
@Composable
private fun NavBar(
    destination: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    insetBottom: Dp,
) {
    val colors = LocalNextColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.chrome.copy(alpha = 0.94f))
            .drawBehind {
                drawRect(
                    color = colors.rule,
                    size = Size(size.width, NextMetrics.hairline.toPx()),
                )
            }
            .padding(bottom = insetBottom)
            .selectableGroup(),
    ) {
        AppDestination.entries.forEach { entry ->
            NavItem(
                destination = entry,
                selected = entry == destination,
                rail = false,
                onClick = { onNavigate(entry) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The rail, once the window opens.
 *
 * It carries a mark the bar has no room for: the app's own name, and one line saying what it is
 * doing. On a screen this size the reader is further away from the content and a running analysis
 * is worth a permanent line rather than a toast that has already gone.
 */
@Composable
private fun NavRail(
    destination: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    status: String,
    statusTone: Color,
    insetStart: Dp,
    insetTop: Dp,
    insetBottom: Dp,
) {
    val colors = LocalNextColors.current
    Column(
        Modifier
            .fillMaxHeight()
            .width(NextMetrics.navRailWidth + insetStart)
            .background(colors.chrome.copy(alpha = 0.94f))
            .drawBehind {
                val edge = NextMetrics.hairline.toPx()
                drawRect(
                    color = colors.rule,
                    topLeft = Offset(size.width - edge, 0f),
                    size = Size(edge, size.height),
                )
            }
            .padding(start = insetStart, top = insetTop, bottom = insetBottom)
            .selectableGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = NextMetrics.space5)
                .padding(top = NextMetrics.space6, bottom = NextMetrics.space5)
                .drawBehind {
                    val edge = NextMetrics.hairline.toPx()
                    drawRect(
                        color = colors.ruleSoft,
                        topLeft = Offset(0f, size.height - edge),
                        size = Size(size.width, edge),
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NextMetrics.space1),
        ) {
            NextText("EGX", NextType.navMark.copy(letterSpacing = NextType.columnLabel.letterSpacing), colors.ink2)
            if (status.isNotBlank()) {
                NextText(
                    text = status.uppercase(Locale.ROOT),
                    style = NextType.navLabel,
                    color = statusTone,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        Spacer(Modifier.height(NextMetrics.space3))
        AppDestination.entries.forEach { entry ->
            NavItem(
                destination = entry,
                selected = entry == destination,
                rail = true,
                onClick = { onNavigate(entry) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One destination.
 *
 * A two-letter mark over a caption, because at this size the mark is what is actually read and the
 * word underneath is what confirms it. No icons: five glyphs drawn to say Analyze, Results,
 * Insights, Portfolio and Settings would each be somebody's guess, and the app already has a
 * two-letter name for every one of them.
 *
 * Pressing fills to the well over 90ms and lets go over 120. There is no ripple - the well is the
 * app's only press, and it is the same well a card drops into when it opens.
 */
@Composable
private fun NavItem(
    destination: AppDestination,
    selected: Boolean,
    rail: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNextColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val fill by animateColorAsState(
        targetValue = if (selected || pressed) colors.well else Color.Transparent,
        animationSpec = tween(
            durationMillis = if (pressed) NextMotion.PRESS_MILLIS else NextMotion.RELEASE_MILLIS,
            easing = NextMotion.linear,
        ),
        label = "navItemFill",
    )
    val spine by animateColorAsState(
        targetValue = if (selected) colors.accent else Color.Transparent,
        animationSpec = tween(NextMotion.PRESS_MILLIS, easing = NextMotion.linear),
        label = "navItemSpine",
    )

    Column(
        modifier
            .heightIn(min = NextMetrics.navItemHeight)
            .background(fill)
            .drawBehind {
                val thickness = NextMetrics.spine.toPx()
                if (rail) {
                    // On the inner edge, so the mark points at the content it opened.
                    drawRect(
                        color = spine,
                        topLeft = Offset(size.width - thickness, 0f),
                        size = Size(thickness, size.height),
                    )
                } else {
                    drawRect(color = spine, size = Size(size.width, thickness))
                }
            }
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(
                horizontal = NextMetrics.space1,
                vertical = if (rail) NextMetrics.space4 else NextMetrics.space3,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NextText(
            text = destination.shortLabel,
            style = NextType.navMark,
            color = if (selected) colors.ink else colors.ink3,
        )
        Spacer(Modifier.height(NextMetrics.space1))
        NextText(
            text = destination.label.uppercase(Locale.ROOT),
            style = NextType.navLabel,
            color = if (selected) colors.accent else colors.ink3,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** A new version, and the two things that can be done about it. */
@Composable
private fun ShellUpdateBanner(banner: ShellBanner) {
    val colors = LocalNextColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.chrome)
            .drawBehind {
                drawRect(color = banner.tone, size = Size(size.width, NextMetrics.spine.toPx()))
                val edge = NextMetrics.hairline.toPx()
                drawRect(
                    color = colors.rule,
                    topLeft = Offset(0f, size.height - edge),
                    size = Size(size.width, edge),
                )
            }
            .padding(vertical = NextMetrics.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space5),
    ) {
        NextText(banner.label.uppercase(Locale.ROOT), NextType.columnLabel, banner.tone)
        NextText(
            text = banner.text,
            style = NextType.meta.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        banner.primary?.let {
            NextButton(it.label, it.onClick, tone = banner.tone, labelColor = banner.tone, minHeight = 32.dp)
        }
        banner.secondary?.let {
            NextButton(it.label, it.onClick, tone = colors.ink3, minHeight = 32.dp)
        }
    }
}

/** It worked, it is already done, and here is the way back. */
@Composable
private fun ShellToastBar(toast: ShellToast) {
    val colors = LocalNextColors.current
    Row(
        Modifier
            .widthIn(max = NextMetrics.toastWidth)
            .background(colors.chrome)
            .border(NextMetrics.hairline, colors.rule)
            .drawBehind {
                drawRect(color = colors.accent, size = Size(size.width, NextMetrics.spine.toPx()))
            }
            .padding(horizontal = NextMetrics.space5, vertical = NextMetrics.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
    ) {
        NextText(
            text = toast.text,
            style = NextType.name,
            color = colors.ink,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        toast.action?.let {
            NextButton(it.label, it.onClick, tone = colors.accent, labelColor = colors.accent, minHeight = 32.dp)
        }
    }
}

/** It stopped, and nothing else happens until this is answered. */
@Composable
private fun ShellErrorModal(error: ShellError, wide: Boolean) {
    val colors = LocalNextColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.scrim)
            // A blocking error blocks. The empty click is what stops a press landing on the screen
            // underneath; the way out is stated on the panel, in words.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(NextMetrics.space6),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = if (wide) NextMetrics.modalWidth else Dp.Infinity)
                .background(colors.chrome)
                .border(NextMetrics.hairline, colors.rule)
                .drawBehind {
                    drawRect(color = colors.stop, size = Size(size.width, NextMetrics.spine.toPx()))
                },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val edge = NextMetrics.hairline.toPx()
                        drawRect(
                            color = colors.ruleSoft,
                            topLeft = Offset(0f, size.height - edge),
                            size = Size(size.width, edge),
                        )
                    }
                    .padding(NextMetrics.space6),
                verticalArrangement = Arrangement.spacedBy(NextMetrics.space3),
            ) {
                NextText(error.kicker.uppercase(Locale.ROOT), NextType.columnLabel, colors.stop)
                NextText(error.title, NextType.ticker.copy(fontFamily = Sans), colors.ink)
                // Absent whenever the failure spoke for itself. A second paragraph restating the
                // first would read as the app padding out bad news.
                if (error.body.isNotBlank()) NextText(error.body, NextType.name, colors.ink2)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(NextMetrics.space6),
                horizontalArrangement = Arrangement.spacedBy(NextMetrics.space4),
            ) {
                error.primary?.let {
                    NextButton(
                        label = it.label,
                        onClick = it.onClick,
                        tone = colors.accent,
                        fill = NextFill.WASH,
                        modifier = Modifier.weight(1f),
                    )
                }
                NextButton(
                    label = error.dismiss.label,
                    onClick = error.dismiss.onClick,
                    tone = colors.rule,
                    labelColor = colors.ink3,
                )
            }
        }
    }
}
