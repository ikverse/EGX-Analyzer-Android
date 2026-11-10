package com.ikverse.egxanalyzer.ui.kit

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.ikverse.egxanalyzer.model.FoldPosture
import com.ikverse.egxanalyzer.ui.theme.palette

/** The grab handle at the top of every sheet. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .size(width = 44.dp, height = 5.dp)
            .clip(Kit.Capsule)
            .background(palette.ink.copy(alpha = 0.25f)),
    )
}

/**
 * The one sheet every surface opens, and where it opens depends on the fold.
 *
 * - **Cover screen and tabletop:** rises from the bottom over a dimmed page, 32dp top corners. In
 *   tabletop the sheet's content is expected to put what is read in its upper part and its buttons
 *   at the foot, which on a half-open phone is the flat half.
 * - **Unfolded and book posture:** opens as a panel on the right half, so whatever it was opened
 *   from stays readable and pressable on the left. In book posture the panel is exactly the half
 *   past the hinge.
 *
 * The bottom variant is Material's sheet underneath - its drag, fling and back handling are
 * correct and not worth rewriting - drawn entirely in the kit's shape, colour and handle. Nothing
 * outside this file creates a `ModalBottomSheet`; that is pinned by the clean-up test.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitSheet(
    onDismiss: () -> Unit,
    posture: FoldPosture,
    title: String,
    modifier: Modifier = Modifier,
    /**
     * False for a sheet holding typed input: a stray tap outside, or a swipe down, must not throw
     * away two prices and a date. Back and the sheet's own Cancel still close it.
     */
    dismissOnOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = palette
    if (posture.opensSheetsAsPanel) {
        SidePanel(onDismiss = onDismiss, title = title, splitAtFraction = posture.panelStartFraction, dismissOnOutside = dismissOnOutside, content = content)
        return
    }
    val state = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { dismissOnOutside || it != androidx.compose.material3.SheetValue.Hidden },
    )
    // Back reaches the sheet's own window, never the activity, so a BackHandler here heard nothing
    // and back did nothing on a sheet holding typed input. The sheet's back path ends in
    // onDismissRequest; a press on the scrim and a swipe down ask confirmValueChange first, which
    // refuses Hidden when [dismissOnOutside] is false, so only back and Cancel close one of those.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = Kit.SheetCorner, topEnd = Kit.SheetCorner),
        containerColor = p.ground,
        contentColor = p.ink,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = { SheetHandle() },
        modifier = modifier.semantics { paneTitle = title },
    ) {
        CompositionLocalProvider(LocalContentColor provides p.ink) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Kit.PageInset)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(Kit.TileGap),
                content = content,
            )
        }
    }
}

@Composable
private fun SidePanel(
    onDismiss: () -> Unit,
    title: String,
    splitAtFraction: Float,
    dismissOnOutside: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = palette
    BackHandler(onBack = onDismiss)
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val panelWidth = with(density) { (window.width * (1f - splitAtFraction)).toDp() }
    val panelHeight = with(density) { window.height.toDp() }
    // A popup the width of the panel alone, anchored to the window's right edge, so it is drawn
    // over the whole page wherever the caller composed it - a sheet is usually asked for from deep
    // inside a scrolling list. Focusable, because sheets hold text fields; a press on the page side
    // closes the panel, and the press after that reaches the page.
    Popup(
        // Against the window's own right edge. `alignment` lines a popup up with the layout that
        // composed it, which for a sheet asked for from inside a card was the card's edge.
        popupPositionProvider = WindowRightEdge,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnClickOutside = dismissOnOutside),
    ) {
        AnimatedVisibility(
            visibleState = shown,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            Column(
                Modifier
                    .width(panelWidth)
                    .height(panelHeight)
                    .background(p.ground)
                    .semantics { paneTitle = title }
                    .verticalScroll(rememberScrollState())
                    .padding(Kit.PageInset),
                verticalArrangement = Arrangement.spacedBy(Kit.TileGap),
            ) {
                CompositionLocalProvider(LocalContentColor provides p.ink) {
                    content()
                }
            }
        }
    }
}

/**
 * A dialog: an icon tile, a title, the explanation, and two buttons side by side with the
 * dismissing one first. The confirming one is [ButtonKind.DANGER] when [destructive].
 *
 * Centred on the cover screen and unfolded. **On a half-open phone in tabletop it is centred on the
 * lower half instead**, never across the crease: the platform centres a dialog in the whole window,
 * so the card is laid inside a full-window box and placed below the hinge here.
 */
@Composable
fun KitDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    posture: FoldPosture = FoldPosture.Cover,
    tone: TileTone = TileTone.TILE2,
    glyph: String = "!",
    destructive: Boolean = false,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val card: @Composable () -> Unit = {
            AnimatedVisibility(visibleState = shown, enter = scaleIn(popSpring(), initialScale = 0.88f) + fadeIn(), exit = scaleOut() + fadeOut()) {
                DialogCard(title, text, confirmLabel, onConfirm, dismissLabel, onDismiss, modifier, tone, glyph, destructive, extra)
            }
        }
        if (posture is FoldPosture.Tabletop) {
            // The column fills the dialog's window, so a press on the dimmed area never reached
            // the dialog's own outside-tap. It answers here instead, and the card swallows presses
            // on itself so its body does not count as outside.
            val outside = Modifier.pointerInput(Unit) { detectTapGestures { onDismiss() } }
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.weight(posture.hingeEndFraction.coerceIn(0.05f, 0.95f)).fillMaxWidth().then(outside))
                Box(
                    Modifier.weight(1f - posture.hingeEndFraction.coerceIn(0.05f, 0.95f)).fillMaxWidth().then(outside),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.pointerInput(Unit) { detectTapGestures { } }) { card() }
                }
            }
        } else {
            card()
        }
    }
}

@Composable
private fun DialogCard(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier,
    tone: TileTone,
    glyph: String,
    destructive: Boolean,
    extra: @Composable ColumnScope.() -> Unit,
) {
    val p = palette
    Column(
        modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 420.dp)
            .clip(Kit.DialogShape)
            .background(p.ground)
            .padding(22.dp)
            .semantics { paneTitle = title },
    ) {
        val (fill, onFill) = tileColors(if (destructive) TileTone.ALERT else tone)
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(fill),
            contentAlignment = Alignment.Center,
        ) { Text(glyph, color = onFill, style = MaterialTheme.typography.headlineSmall) }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = p.ink, modifier = Modifier.padding(top = 12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = p.muted, modifier = Modifier.padding(top = 8.dp))
        CompositionLocalProvider(LocalContentColor provides p.ink) { extra() }
        Row(Modifier.padding(top = 18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KitButton(dismissLabel, onDismiss, Modifier.weight(1f), kind = ButtonKind.SECONDARY)
            KitButton(
                confirmLabel,
                onConfirm,
                Modifier.weight(1.4f),
                kind = if (destructive) ButtonKind.DANGER else ButtonKind.PRIMARY,
            )
        }
    }
}

/** One row of a [KitMenu]. [danger] draws it in the alert colour, for Remove and the like. */
data class MenuItem(val label: String, val danger: Boolean = false, val onClick: () -> Unit)

/** The ⋮ menu: an ink card of rows that pops out of its anchor. */
@Composable
fun KitMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<MenuItem>,
) {
    if (!expanded) return
    val p = palette
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    Popup(alignment = Alignment.TopEnd, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        AnimatedVisibility(visibleState = shown, enter = scaleIn(popSpring(), initialScale = 0.8f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)) + fadeIn()) {
            Column(
                Modifier
                    .width(240.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(p.ink)
                    .padding(6.dp),
            ) {
                items.forEach { item ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (item.danger) p.alert.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                            ) {
                                onDismiss()
                                item.onClick()
                            }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            item.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (item.danger) p.alert else p.ground,
                        )
                    }
                }
            }
        }
    }
}

/** Space a sheet's content may use without running under the gesture strip. */
@Composable
fun SheetFooterSpacer() = Spacer(Modifier.height(12.dp))

/** Places a side panel flush with the top right of the window, wherever it was composed from. */
private object WindowRightEdge : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ): androidx.compose.ui.unit.IntOffset =
        androidx.compose.ui.unit.IntOffset((windowSize.width - popupContentSize.width).coerceAtLeast(0), 0)
}
