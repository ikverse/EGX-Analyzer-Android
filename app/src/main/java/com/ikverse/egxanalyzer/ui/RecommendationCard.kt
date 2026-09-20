package com.ikverse.egxanalyzer.ui

import com.ikverse.egxanalyzer.model.timing

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikverse.egxanalyzer.model.ConsolidatedRecommendation
import com.ikverse.egxanalyzer.model.RecommendationDataPoint
import kotlinx.coroutines.delay

/**
 * Every occurrence of one stock, one card each, swiped through sideways.
 *
 * A stock is often called twice in a report - by two channels, or once as a watch and once for a
 * named date - and those calls have different entries and different targets. Folding them into a
 * single card showed the first and silently dropped the rest, which is the one thing a table row
 * never did.
 */
@Composable
internal fun RecommendationCards(
    stock: ConsolidatedRecommendation,
    /** The channel behind an occurrence, looked up by the message the model cited. */
    channelFor: (String?) -> String?,
    /** The stored photo behind an occurrence, looked up by the reference the model cited. */
    imagePathFor: (Int?) -> String?,
    /** Records what the user did about a call. Absent, the cards are read-only. */
    trades: TradeBook? = null,
    /** Corrects what the model read off the card. Absent, the figures cannot be changed. */
    editor: CallEditor? = null,
    modifier: Modifier = Modifier,
) {
    val points = stock.dataPoints
    if (points.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            border = cardOutline,
        ) {
            Column(Modifier.padding(Space.l)) {
                StockHeader(
                    stock,
                    point = null,
                    page = 0,
                    pageCount = 0,
                    session = null,
                    editor = null,
                )
            }
        }
        return
    }

    val pager = rememberPagerState(pageCount = { points.size })
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s)) {
        HorizontalPager(
            state = pager,
            // No peek, deliberately: insetting only the stocks with several occurrences made their
            // cards narrower than the rest, and a row of cards that do not match reads as a fault
            // before it reads as a hint. The dots in the header carry the hint instead.
            pageSpacing = Space.s,
            verticalAlignment = Alignment.Top,
        ) { page ->
            val point = points[page]
            RecommendationCard(
                stock = stock,
                point = point,
                channel = channelFor(point.sourceMessageId),
                imagePath = imagePathFor(point.sourceImageRef),
                trades = trades,
                editor = editor,
                page = page,
                pageCount = points.size,
            )
        }
    }
}

/**
 * One occurrence: what this source, on this date, actually said.
 *
 * Collapsed it answers "what is the trade"; expanded it answers "where did this come from",
 * which matters because the app's whole claim is that every number is traceable to a source.
 */
@Composable
private fun RecommendationCard(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint,
    channel: String?,
    imagePath: String?,
    trades: TradeBook?,
    editor: CallEditor?,
    page: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(point) { mutableStateOf(false) }
    var viewingImage by remember(point) { mutableStateOf(false) }
    var editing by remember(point) { mutableStateOf(false) }
    // Held on the card rather than reached through the menu: the owner's own habit is that Edit
    // call is the item in CallMenu actually worth pressing and Copy call is not, so the one worth
    // a shortcut gets one.
    var quickEditOpen by remember(point) { mutableStateOf(false) }
    val held = trades?.heldFor(stock, point)

    Card(
        modifier = modifier.fillMaxWidth().combinedClickable(
            onClick = { expanded = !expanded },
            // Absent rather than always-on where there is no editor to open: a read-only card
            // holding on it would show a shortcut to a screen it cannot reach, same guard CallMenu
            // already puts around its own Edit call item.
            onLongClick = editor?.let { { quickEditOpen = true } },
        ),
        // A step up in container rather than a shadow. These sit inside the report's own card, which
        // is why they were elevated; the step is what separates them now that nothing on the page
        // casts a shadow.
        // A **second-level** surface: it sits inside the report's card, so what shows through it is
        // that card rather than the page, and it takes the container role's own alpha and nothing
        // else. See GlassSection.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        // A stock the user is actually in is outlined in the colour of where it stands, so a page
        // of calls can be read for what is held before any card is opened. Everything else carries
        // the hairline every other card on the page is drawn with.
        border = heldBorder(held) ?: cardOutline,
    ) {
        Column(Modifier.padding(Space.m)) {
            // The session the call was made for, from the same source the Bought button
            // reads it from, so the copied text and the trade agree about which day.
            StockHeader(
                stock, point, page, pageCount, trades?.dateOf(point), editor,
                onEdit = { editing = true },
            )
            Spacer(Modifier.height(Space.m))

            // What the channel printed, set off from what it printed it about.
            HorizontalDivider()
            Spacer(Modifier.height(Space.m))

            // Full width rather than sharing the header's own left column: it answers a
            // different question from the identity block above it and reads cramped squeezed
            // under the ticker's own width. Below the header's rule rather than above it, so
            // it reads as an answer to what the header just named rather than a third line
            // inside the header itself.
            channel?.takeIf(String::isNotBlank)?.let {
                SourceLine(it)
                Spacer(Modifier.height(Space.m))
            }

            // No ladder here, deliberately. This card is a row of the report that would not fit as
            // a row: what it owes the reader is the call's figures, and a drawing of the same five
            // numbers doubled the card's height to say what LevelGrid says underneath it. The
            // occurrence sheet and the Portfolio card still draw it, where a single call is the
            // whole subject rather than one of a dozen being scanned.
            //
            // Tinted, in the same recipe Insights' own call card tints "The call" and "The market"
            // panels with (see SectionPanelShape in InsightsScreen.kt) - a card's six busiest
            // figures earn the same grouping those panels give a call's figures there. Only at this
            // call site: the occurrence sheet's copy of LevelGrid is a single call read in full and
            // was not asked to change.
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(10.dp))
                    .padding(Space.m),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    LevelGrid(point)
                    // Inside the same panel as the six prices it is measured from, rather than a
                    // line loose on the card below it - tinted the market's own blue, the same
                    // treatment Insights gives risk : reward on its own call card.
                    point.riskRewardRatio()?.let { ratio ->
                        Level(
                            "Risk / reward",
                            "1 : ${"%.1f".format(ratio)}",
                            PriceRole.market,
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.m))

            // A line under the figures, because the two things below it are the only parts of this
            // card that answer a press. Everything above is the call as the channel printed it.
            HorizontalDivider()
            Spacer(Modifier.height(Space.m))

            // One row, and the two ends of it are the two kinds of press this card offers: what it
            // records, and what it opens. They sat on separate lines and read as two afterthoughts.
            //
            // A FlowRow rather than a Row, for the reason the position card's controls are one: a
            // held call puts its status, its overdue and its price-scale pills on the left of this
            // line, and on a cover screen three of those beside the source button is a line that
            // has to wrap rather than one that may clip.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                // Only where the call has a session to belong to: an occurrence the model left
                // undated cannot be scored, so a trade filed against it would have no deadline to
                // run to. A spacer holds the source button on the right where there is no trade
                // control, rather than letting it slide into the ticker's column.
                if (trades != null && trades.dateOf(point) != null) {
                    TradeAction(
                        held = held,
                        suggestedEntry = point.entryMidpoint(),
                        defaultWindow = trades.windowFor(point),
                        tPlusOne = point.isTPlusOne,
                        onBuy = { price, date, window ->
                            trades.buy(stock, point, channel, price, date, window)
                        },
                        onSell = { sale -> held?.let { trades.sell(it, sale) } },
                        // Recording a purchase belongs on the card the call was read off; closing a
                        // position does not. A sale is the end of a trade, and it is made where the
                        // trade lives - Portfolio, or the occurrence sheet - not off a card being
                        // scanned for what to buy next. The held and overdue chips still show here.
                        canSell = false,
                    )
                } else {
                    Spacer(Modifier.width(0.dp))
                }
                // The same press the whole card already answers, said in a place the reader can
                // aim at - and the arrow is what makes the card's own press discoverable at all.
                DisclosureButton("Source", expanded) { expanded = !expanded }
            }

            AnimatedVisibility(expanded) {
                Column(
                    Modifier.padding(top = Space.m),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    HorizontalDivider()
                    OccurrenceDetail(point, imagePath) { viewingImage = true }
                }
            }
            // A dedicated Space.s here rather than a shared spacedBy on the outer column: the
            // collapsed AnimatedVisibility above is still a layout node even at zero height, and
            // a uniform arrangement charged a gap on both sides of it - 24dp of dead air between
            // this row and the dots for a card showing nothing in between. Asked for on 2026-09-20.
            if (pageCount > 1) {
                Spacer(Modifier.height(Space.s))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PageDots(page, pageCount)
                }
            }
        }
    }

    if (viewingImage) {
        SourceImageViewer(imagePath, point.sourceImageRef, onDismiss = { viewingImage = false })
    }
    if (quickEditOpen) {
        QuickEditPrompt(
            onEdit = { quickEditOpen = false; editing = true },
            onDismiss = { quickEditOpen = false },
        )
    }
    if (editing && editor != null) {
        EditCallSheet(stock, point, editor, onDismiss = { editing = false })
    }
}

/**
 * Two buttons over a blurred card, reached by holding rather than opening [CallMenu].
 *
 * Blurs what is behind it rather than dimming it flat - the platform's own way of saying "answer
 * this and you're straight back", where a full-screen scrim would read as a new place navigated
 * to. `Window.setBackgroundBlurRadius` is API 31, which is this app's `minSdk`, so there is no
 * older path to fall back to.
 *
 * The dialog does not leave the moment a button is pressed: `visible` drives the exit animation
 * and the dialog itself is only asked to close once that animation has actually run, so a press
 * is answered by a shrink-and-fade rather than a cut.
 */
@Composable
private fun QuickEditPrompt(onEdit: () -> Unit, onDismiss: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var editRequested by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(visible) {
        if (!visible) {
            delay(QuickEditExitMs.toLong())
            if (editRequested) onEdit() else onDismiss()
        }
    }
    Dialog(
        onDismissRequest = { visible = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val view = LocalView.current
        DisposableEffect(Unit) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window?.setBackgroundBlurRadius(QuickEditBlurRadius)
            window?.setDimAmount(QuickEditDim)
            onDispose {}
        }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { visible = false },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(QuickEditEnterMs)) +
                    scaleIn(initialScale = 0.85f, animationSpec = tween(QuickEditEnterMs)),
                exit = fadeOut(tween(QuickEditExitMs)) +
                    scaleOut(targetScale = 0.85f, animationSpec = tween(QuickEditExitMs)),
            ) {
                Column(
                    Modifier
                        .padding(Space.xl)
                        .background(
                            Glass.solid(MaterialTheme.colorScheme.surfaceContainerHigh),
                            RoundedCornerShape(24.dp),
                        )
                        // Swallows a press so it does not fall through to the scrim behind the
                        // buttons and dismiss the dialog it landed on.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {}
                        .padding(Space.l),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    Button(
                        onClick = { editRequested = true; visible = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Edit call")
                    }
                    OutlinedButton(
                        onClick = { visible = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

private const val QuickEditEnterMs = 220
private const val QuickEditExitMs = 160
private const val QuickEditDim = 0.32f
private const val QuickEditBlurRadius = 48

@Composable
private fun StockHeader(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint?,
    page: Int,
    pageCount: Int,
    /** The session this occurrence was made for, which the copied text names. */
    session: java.time.LocalDate?,
    editor: CallEditor?,
    onEdit: () -> Unit = {},
) {
    // Ticker style and name style match the identity block Insights draws on its own call card
    // (InsightsScreen.kt's ScoredCallRow) - same stock, two screens, one look. The pills sit where
    // the ⋮ menu used to: that menu is gone from this card (Copy call went unused and Edit call is
    // reached by holding the card instead - see QuickEditPrompt), so the corner it occupied is free
    // for the one thing worth a glance without opening anything.
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            val openStock = LocalOpenStock.current
            // The logo sits beside the ticker-and-name pair rather than the ticker alone, and
            // CenterVertically is what centers it against both lines rather than just the
            // first - which is also what puts the name flush under the ticker with no padding
            // hack: it is simply the next line in the same column.
            Row(
                Modifier.clickable { openStock(stock.stockCode) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StockLogo(stock.stockCode, LogoSize.Row, Modifier.padding(end = Space.s))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stock.stockCode, style = MaterialTheme.typography.titleSmall)
                        Egx33Badge(stock.stockCode, Modifier.padding(start = Space.s))
                    }
                    stock.stockNameArabic?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (point != null) {
            Spacer(Modifier.width(Space.s))
            // Grouped and left-aligned to each other rather than centred, so Edited (narrower)
            // starts at the same edge as Watching/T+1 instead of wandering to the middle under it.
            // Top-aligned with the ticker: the outer Row's own Alignment.Top is what puts this
            // group where the ⋮ menu used to sit. The trailing padding is its own: with nothing
            // else on this side of the card, the pills sat flush against the card's own edge.
            Column(
                Modifier.padding(end = Space.s),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                TimingChip(point)
                if (editor?.editFor(stock, point) != null) {
                    EditedChip(onEdit)
                }
            }
        }
    }
}

/**
 * The channel this occurrence came from, on its own line below the header and its rule.
 *
 * Left inside [StockHeader] until 2026-09-20, squeezed under the ticker's own column width and
 * sharing it with the timing pills on the other side of the row. It answers a different question
 * from the identity block above it, so it gets the card's full width instead of the narrower one.
 */
@Composable
private fun SourceLine(channel: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Source:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            channel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Copies this call as plain text. See [CallText] for what it says and why.
 *
 * `ClipboardManager` through the composition local rather than the system service: it is what
 * Compose offers, and it is what puts the copy on the same clipboard the text fields in this app
 * paste from. Android 13 and later show their own confirmation of a copy, so the status line is
 * deliberately left alone - two announcements of one press is one too many, and the system's own
 * cannot be turned off.
 */
@Composable
internal fun CallMenu(
    stock: ConsolidatedRecommendation,
    point: RecommendationDataPoint,
    channel: String?,
    session: java.time.LocalDate?,
    editor: CallEditor?,
    onEdit: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Box {
        MoreButton(onClick = { open = true })
        AppMenu(expanded = open, onDismissRequest = { open = false }) {
            AppMenuItem(
                "Copy call",
                Icons.Outlined.ContentCopy,
                onClick = {
                    open = false
                    clipboard.setText(
                        AnnotatedString(CallText.of(stock, point, channel, session)),
                    )
                },
            )
            // The second thing worth doing to a call the card cannot already do: correcting what
            // the model read off the screenshot. It lives here rather than only in the occurrence
            // sheet because the sheet opens from the table, and the table is not drawn at all below
            // 600dp - which is every phone in portrait, and so most of the time this is used.
            if (editor != null) {
                AppMenuItem(
                    "Edit call",
                    Icons.Outlined.Edit,
                    onClick = {
                        open = false
                        onEdit()
                    },
                )
                if (editor.hasEdits) {
                    AppMenuItem(
                        "Undo all edits",
                        Icons.Outlined.Undo,
                        onClick = {
                            open = false
                            editor.undoAll()
                        },
                    )
                }
            }
        }
    }
}

/**
 * What dated the call, in place of the buy/sell signal.
 *
 * Every occurrence in a report is a buy, so the old chip said the same word on every card; which
 * session a call is for is the thing that actually differs between two rows of the same stock.
 */
@Composable
internal fun TimingChip(point: RecommendationDataPoint) {
    // Falls back to the signal only when the model recorded no basis at all, so the chip is
    // never blank.
    val label = timing(point) ?: point.recommendationType?.uppercase() ?: "-"
    // [OutlinePill], which is what every other card in this app annotates itself with. As an
    // AssistChip this stood 32dp tall in 14sp of filled surfaceVariant beside a 24dp button, so the
    // note about where a date came from was the largest object in the corner of the card and the
    // heaviest thing on a header whose figures are the point. A ring at 20dp says the same word.
    //
    // Neutral, like every other note pill on this card. It was `primary` - the app's own voice,
    // on the reasoning that a T+1 changes what the reader has to do - and that made one pill in
    // the app a different colour from its neighbours for a reason none of them showed. The
    // wording is what says this call names its own deadline; the hue was saying it twice, in a
    // language the card spends on prices everywhere else. Asked for on 2026-09-11.
    OutlinePill(
        label,
        outline = MaterialTheme.colorScheme.outline,
        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Entry beside its stop, then the two targets, as labelled figures, each with its percentage where
 * the source gave one, followed by the two market levels the call was drawn against.
 *
 * Six fixed slots in two columns, not a flow. A tile is as wide as its number happens to print, so
 * flowing them put the same figure in a different place on every card - support trailing the stop
 * on one, alone on a line of its own on the next - and a column of cards read as six loose numbers
 * rather than one shape repeated. Fixed slots mean a card can be read down as well as across, and
 * the market levels always land last. Each row pairs the levels that are read against one another
 * - the entry with the stop it risks, then the two targets it is aiming at - which is how the
 * portfolio card already sets a held position out. The table and the export still print left to
 * right in their own order, because both are read down a column; **the occurrence sheet draws this
 * grid**, and did not until 2026-09-11 - it flowed six figures at whatever width they happened to
 * print, which left Resistance stranded on a line of its own. A sheet is the one surface where a
 * single call is the whole subject, so if this pairing reads on a card being scanned it reads
 * there.
 *
 * Every slot is drawn whether or not the source filled it: a card whose rows move depending on what
 * the channel happened to publish is the thing this layout exists to stop, and a dash says "no
 * figure given" where a closed gap says nothing at all.
 *
 * Support and resistance belong here rather than under the source trace: they are figures the call
 * was made on, not evidence for where it came from. A stop sitting a hair under support is the
 * reason that stop is where it is, and that only reads when the two are a glance apart.
 */
@Composable
internal fun LevelGrid(point: RecommendationDataPoint) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        LevelPair(
            { Level("Entry", entryText(point), PriceRole.entry, it) },
            { Level("Stop loss", levelText(point.stopLoss, point.riskPct), PriceRole.stop, it) },
        )
        LevelPair(
            { Level("Target 1", levelText(point.target1, targetReturn(point, point.target1, point.returnTp1Pct)), PriceRole.target, it) },
            { Level("Target 2", levelText(point.target2, targetReturn(point, point.target2, point.returnTp2Pct)), PriceRole.target, it) },
        )
        // Plain, with no percentage beside them. The other four are distances from the entry, which
        // is what a percentage measures here; a support is simply a price the stock has held at.
        LevelPair(
            { Level("Support", formatPrice(point.support), PriceRole.market, it) },
            { Level("Resistance", formatPrice(point.resistance), PriceRole.market, it) },
        )
    }
}

/**
 * One row of the grid: two slots of equal width, whatever their numbers print at.
 *
 * The width is handed to each slot rather than taken by it, so the columns are the row's business
 * and a [Level] stays a label over a figure.
 */
@Composable
private fun LevelPair(
    left: @Composable (Modifier) -> Unit,
    right: @Composable (Modifier) -> Unit,
) {
    // IntrinsicSize.Min on the row plus fillMaxHeight on each side is what makes the shorter tile's
    // bar stretch to match the taller one - without it, a value that wraps to two lines (more
    // likely at the folded, narrower width) leaves its neighbour's bar shorter than it should be.
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        left(Modifier.weight(1f).fillMaxHeight())
        right(Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun Level(
    label: String,
    value: String,
    tone: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    // A key rather than a plain label: six of these are read down the card in a fixed order, and
    // the colour is what lets that order be skipped - the reader's eye can go straight to the
    // green targets or the red stop. The height comes entirely from the caller now: LevelPair
    // shares one height across both its tiles via IntrinsicSize.Min, and the caller drawing this
    // alone (the risk/reward row) supplies its own IntrinsicSize.Min instead.
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(LevelKeyWidth)
                .background(tone, RoundedCornerShape(percent = 50)),
        )
        Column {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = LevelFigure,
                    lineHeight = LevelFigureLine,
                ),
                fontWeight = FontWeight.SemiBold,
                color = tone,
            )
        }
    }
}

private val LevelKeyWidth = 3.dp

/**
 * A step under `titleMedium`, on a line tighter than the scale gives it.
 *
 * Six of these are the tallest thing on the card, and the card is one of a dozen being scanned.
 * The scale's line heights are deliberately generous because Arabic carries marks above and below
 * the line - see `AppTypography` - and a price never does, so this is the one place where taking
 * the line in clips nothing. Set as a copy of the role rather than as a role of its own: the face
 * and the weight are still the scale's, and only the size is this card's business.
 */
private val LevelFigure = 15.sp
private val LevelFigureLine = 18.sp

@Composable
private fun OccurrenceDetail(
    point: RecommendationDataPoint,
    imagePath: String?,
    onOpenImage: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(
            listOfNotNull(
                point.visibleSourceDate?.let { "Source date $it" },
                point.sourceImageRef?.let { "Image $it" },
                point.sourceMessageId?.let { "Message $it" },
            ).joinToString(" · ").ifBlank { "Source not recorded" },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The card the numbers were read off, where a pressed table row would have shown it. The
        // thumbnail says `#4` when the photo has gone from Telegram's cache rather than a blank.
        SourceImageThumbnail(
            path = imagePath,
            reference = point.sourceImageRef,
            size = 88.dp,
            onOpen = onOpenImage,
        )
        point.recommendationEvidence?.let {
            Text("“$it”", style = MaterialTheme.typography.bodySmall)
        }
        point.timingEvidence?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

/**
 * The buy band as one string, or the single price where the source printed one.
 *
 * Internal because the occurrence sheet kept its own copy of this until 2026-09-11, and two
 * functions printing one call's entry are two that agree until somebody changes the dash.
 */
internal fun entryText(point: RecommendationDataPoint): String {
    val low = point.buyPriceLow
    val high = point.buyPriceHigh
    return when {
        low != null && high != null && low != high -> "${formatPrice(low)} – ${formatPrice(high)}"
        else -> formatPrice(point.buyPrice ?: low ?: high)
    }
}

/** A price with its percentage, or the dash where the source gave no such level at all. */
internal fun levelText(value: Double?, percent: Double?): String =
    if (value == null || percent == null) {
        formatPrice(value)
    } else {
        "${formatPrice(value)}  (${formatPercent(percent)})"
    }

/**
 * What a source printed against a target, or what the entry implies where it printed nothing.
 *
 * The occurrence sheet read `returnTp1Pct` and `returnTp2Pct` straight until 2026-09-11, so the
 * card showed a percentage beside a target and the sheet showed the bare price - two readings of
 * one call, differing only on whether the channel had happened to print the figure.
 */
internal fun targetReturn(point: RecommendationDataPoint, target: Double?, stated: Double?): Double? =
    stated ?: impliedReturn(point, target)

/** Entry midpoint to target, so a card shows the upside even when the source never printed it. */
private fun impliedReturn(point: RecommendationDataPoint, target: Double?): Double? {
    if (target == null) return null
    val low = point.buyPriceLow ?: point.buyPrice
    val high = point.buyPriceHigh ?: point.buyPrice
    val entry = when {
        low != null && high != null -> (low + high) / 2
        else -> low ?: high ?: return null
    }
    return if (entry == 0.0) null else (target - entry) / entry * 100
}
