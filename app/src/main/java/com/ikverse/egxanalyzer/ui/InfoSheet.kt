package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.minimumInteractiveComponentSize

import com.ikverse.egxanalyzer.ui.kit.KitSheet
import com.ikverse.egxanalyzer.ui.kit.LocalFoldPosture
import com.ikverse.egxanalyzer.ui.kit.Tile
import com.ikverse.egxanalyzer.ui.theme.palette

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What a control does, kept off the page until it is asked for.
 *
 * Every screen here used to say all of it at once. A checkbox was one line of control under four
 * lines of grey prose, three times in a row, and the effect was that none of it got read: the
 * settings themselves were what you had to hunt for, between the explanations of them. The words
 * were good words - they are all still here, unabridged - but a paragraph that is always on screen
 * is a paragraph that is only useful the first time and in the way every time after.
 *
 * So the explanation moves behind [InfoButton] and the page keeps the control. Nothing is
 * summarised away and nothing is lost; it is one tap further off, and the tap is next to the thing
 * it is about.
 *
 * @param paragraphs kept as a list rather than joined with newlines so the sheet spaces them the way
 *   the design system spaces everything else, instead of by whatever a "\n\n" happens to measure.
 */
internal data class InfoNote(
    val title: String,
    val paragraphs: List<String>,
    /** A notification's example, drawn under the words as "What it looks like". See [withSample]. */
    val sample: NotificationSample? = null,
)

/** [InfoNote] without the list ceremony at the call site, which is every call site. */
internal fun infoNote(title: String, vararg paragraphs: String) =
    InfoNote(title, paragraphs.toList())

/**
 * The one affordance in the app that means "there is more to say about this".
 *
 * A question mark rather than an ⓘ, and deliberately: `Icons.Outlined.Info` is already the About
 * card's own icon in Settings, so the same glyph would have meant "the app's version number" in one
 * place and "explain this" in another. The question mark was already doing this job on the source
 * ranking in Insights - this makes it the rule rather than that one screen's habit.
 *
 * Muted rather than `primary`. It sits beside dozens of controls, and a page of coloured glyphs
 * would be the same clutter in a smaller font.
 *
 * @param dense draw the glyph at its own size instead of inside a 48dp [IconButton]. A card heading
 *   is a [Row] as tall as its title (~24dp); the 48dp touch target inflates that band, so a
 *   [SectionCard] with an explanation ends up with a taller header than the cards beside it that
 *   have none. Dense keeps the footprint at the icon's size - the same bare-glyph treatment
 *   `SubSection` already gives its chevron - so the heading lines up. Left `false` for a settings
 *   row, which sizes the button down to [SettingRow]'s own target instead: a real button, at the
 *   height of the switch beside it rather than at either extreme.
 */
@Composable
internal fun InfoButton(
    note: InfoNote,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    dense: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    // Where a page shows explanations in place of a sheet, the note goes there. See [LocalInfoNoteSink].
    val sink = LocalInfoNoteSink.current
    val show = { if (sink != null) sink(note) else open = true }
    // The kit's look since 5.0: a muted glyph on a raised disc, at a row's own height.
    val glyph = @Composable {
        Icon(
            Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = note.title,
            tint = tint ?: palette.muted,
            modifier = Modifier.size(IconSize.Inline),
        )
    }
    if (dense) {
        Box(
            modifier
                .size(IconSize.Inline)
                .clickable(role = Role.Button) { show() },
            contentAlignment = Alignment.Center,
        ) { glyph() }
    } else {
        Box(
            modifier
                .minimumInteractiveComponentSize()
                .size(32.dp)
                .clip(com.ikverse.egxanalyzer.ui.kit.Kit.Capsule)
                .background(palette.raised)
                .clickable(role = Role.Button, onClickLabel = "Explain") { show() },
            contentAlignment = Alignment.Center,
        ) { glyph() }
    }
    if (open) InfoSheet(note) { open = false }
}

/**
 * Where a pressed question mark sends its note, when the page has somewhere better than a sheet.
 *
 * Null everywhere but Settings in tabletop, where the upright half is for reading and the flat half
 * for pressing: a sheet would rise over the half being pressed, so the note is shown above the
 * crease instead and the controls stay under the finger.
 */
internal val LocalInfoNoteSink = androidx.compose.runtime.compositionLocalOf<((InfoNote) -> Unit)?> { null }

/**
 * An [InfoNote] as a sheet, on the same terms as `ChannelScoreSheet`.
 *
 * Same padding, same scroll, same full-height state - a reader who has opened one explanation in
 * this app has opened all of them. It states nothing the page does not: the sheet is where the
 * words moved to, not a second place for them to drift.
 *
 * @param content anything past the paragraphs a particular sheet needs - the per-fault detail and
 *   the two buttons on the "levels to check" sheet, for one. Absent on every other explanation in
 *   the app, which is a title and its prose and nothing more. Ahead of [onDismiss] rather than
 *   after it, on purpose: [onDismiss] is what every other call site hands this as a trailing
 *   lambda, and a defaulted parameter after it would have to be named at every one of those
 *   call sites to keep compiling.
 */
@Composable
internal fun InfoSheet(
    note: InfoNote,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val p = palette
    // The kit's sheet: from the bottom folded, beside the page unfolded. The title on the page's
    // ground, the words in one white tile, and whatever a particular sheet adds under them.
    KitSheet(onDismiss = onDismiss, posture = LocalFoldPosture.current, title = note.title) {
        Text(
            note.title,
            style = MaterialTheme.typography.headlineMedium,
            color = p.ink,
            modifier = Modifier.padding(horizontal = Space.xs),
        )
        Tile(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                note.paragraphs.forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = p.ink)
                }
            }
        }
        note.sample?.let { sample ->
            Text(
                "What it looks like",
                style = MaterialTheme.typography.titleSmall,
                color = p.ink,
                modifier = Modifier.padding(horizontal = Space.xs),
            )
            NotificationPreview(sample)
        }
        content?.let { extra ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.s), content = extra)
        }
    }
}

/**
 * The room a card's chevron takes at its right edge, held open by every question mark drawn inside
 * the card.
 *
 * `ExpandableSection` puts the group's own question mark *before* that chevron, so the affordance
 * that explains a whole card sat 24dp to the left of the ones explaining the controls in it - one
 * column of question marks in the headings, a second one down the contents, on a page built almost
 * entirely of those two things. Reserving the chevron's own width inside the card makes it one
 * column. It is only ever spent on a row that has a question mark, so nothing else on the card
 * gives up any width for it.
 */
private val ChevronGutter: Dp = IconSize.Action

/**
 * The width of a switch, held open after the question mark of a row that has no control.
 *
 * So the marks down a card stand in one column: just inboard of the switches, whether the row they
 * are on has a switch or not. Until 5.3 a row without one spent [ChevronGutter] instead, which put
 * its mark 28dp to the right of the marks on the rows with switches.
 */
private val SwitchGutter: Dp = 52.dp

/**
 * The touch target a settings row's question mark gets.
 *
 * Not [IconButton]'s own 48dp, which is a third taller than anything it sits beside: a switch is
 * 32dp and a [SettingsButton] is 32dp, so the full target made every row that had an explanation
 * taller than every row that did not - one list of settings at two heights, and the taller ones
 * picked out by nothing more meaningful than having a paragraph behind them. 36dp is the largest
 * that does not do that, and it is still a target rather than a bare glyph.
 */
private val InfoTarget: Dp = 36.dp

/**
 * One line of a settings card: whatever the control is, its explanation, and where the control sits.
 *
 * Written because the question marks had drifted. [SettingToggle] and [SettingLabel] put theirs at
 * the trailing edge; the four rows built by hand - Save diagnostics, Restore from a backup, Fetch
 * prices now, Add a schedule - put theirs immediately after the button, so on a card holding both
 * shapes the same affordance appeared in two places and neither read as a column. Here it has one
 * home, and a caller cannot put it anywhere else.
 *
 * The caller decides what stretches: give the element that should fill the line a `weight(1f)`, or
 * add a weighted [Spacer] after a button so the question mark is pushed to the edge.
 *
 * @param trailing the control, when the row is a label with something to set beside it. It is drawn
 *   **after** the question mark and hard against the card's inset, which is the column the heading's
 *   own chevron stands in - so every switch down a card lines up under it. The question mark then
 *   sits inboard of the control, next to the words it explains rather than past the thing it does
 *   not. Null for an action row, whose button carries its own label and leads: there is no text
 *   beside it to align, and a button pushed to the trailing edge would leave the line empty.
 */
@Composable
internal fun SettingRow(
    modifier: Modifier = Modifier,
    about: InfoNote? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        content()
        about?.let {
            InfoButton(
                it,
                Modifier
                    // Outside the size and not inside it: padding applied after would eat the
                    // target down to 12dp rather than move it. Only a row that ends at the question
                    // mark spends the gutter - where a control follows, the control is what has to
                    // reach the chevron's column, and padding the glyph holds it short of it.
                    .padding(end = if (trailing == null) SwitchGutter else 0.dp)
                    .size(InfoTarget),
            )
        }
        trailing?.invoke(this)
    }
}

/**
 * A setting that is on or off, its name, and where its explanation went.
 *
 * Written once because it was written fifteen times: a `Row` holding a control and a `Text`, then a
 * paragraph underneath in `bodySmall` and `onSurfaceVariant`. The paragraph is now [about] and the
 * row is now level - the fifteen hand-built ones had drifted into a control leading here, one
 * trailing there, and two different gaps between the control and its label.
 *
 * **The control is a switch in every case.** It used to be a checkbox unless the setting armed the
 * phone to act on its own, which is a distinction the page could not carry: two shapes of control
 * down one list of settings read as two kinds of list, not as heavy settings and light ones.
 *
 * **The name leads and the switch trails.** The switch used to lead, which put every label 52dp in
 * from the card's edge - past the heading above it, past the sentences under it, past the buttons on
 * the same card - so the one column a settings card is actually read down, the names of the
 * settings, was the only column on it that started nowhere in particular. The names now start where
 * everything else on the card starts, and the switches make their own column at the trailing edge,
 * under the heading's chevron.
 *
 * @param about absent for a setting whose label is the whole of it. Most have one; "Text messages"
 *   in the content-type list does not, and giving it a question mark to open two words would be
 *   worse than the paragraph this replaces.
 */
@Composable
internal fun SettingToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    about: InfoNote? = null,
    enabled: Boolean = true,
    /** A second, quieter line under the label: when the thing it switches happens. */
    detail: String? = null,
    /** On the cobalt tile, where the ordinary track is cobalt on cobalt. See `KitSwitchFace`. */
    onHero: Boolean = false,
) {
    SettingRow(
        // The row is the switch: one accessible node that carries the name, the state and the
        // press, read as "<label>, switch, on". Split across a Text and a bare Switch it was an
        // unnamed switch beside words that did nothing when pressed.
        modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        about,
        trailing = { com.ikverse.egxanalyzer.ui.kit.KitSwitchFace(checked = checked, enabled = enabled, onHero = onHero) },
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = Space.s),
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (onHero) androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.78f) else palette.muted,
                )
            }
        }
    }
}

/**
 * A line of text with its explanation folded behind it, for anything that is not a toggle.
 *
 * Where a whole block needs a sentence - the chip rows that pick a lookback, the slider above a
 * timeout - rather than each control in it needing its own.
 *
 * @param style `labelLarge` for a heading over a group of controls, which is what most of these
 *   are. A caller passes `bodyLarge` where the line is a **value** rather than a heading - a
 *   version number, a slider's current reading - because those were bodyLarge before they gained a
 *   question mark, and shrinking a figure to make room for an affordance beside it is the affordance
 *   changing what it was added to explain.
 */
@Composable
internal fun SettingLabel(
    text: String,
    about: InfoNote? = null,
    style: TextStyle = MaterialTheme.typography.labelLarge,
) {
    SettingRow(about = about) {
        Text(text, Modifier.weight(1f), style = style)
    }
}
