package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
internal data class InfoNote(val title: String, val paragraphs: List<String>)

/** [InfoNote] without the list ceremony at the call site, which is every call site. */
internal fun infoNote(title: String, vararg paragraphs: String) =
    InfoNote(title, paragraphs.toList())

/**
 * The one affordance in the app that means "there is more to say about this".
 *
 * A question mark rather than an ⓘ, and deliberately: `Icons.Outlined.Info` is already the About
 * card's own icon in Settings, so the same glyph would have meant "the app's version number" in one
 * place and "explain this" in another. The question mark was already doing this job on the source
 * ranking and on "Does it matter?" in Insights - this makes it the rule rather than those two
 * screens' habit.
 *
 * Muted rather than `primary`. It sits beside dozens of controls, and a page of coloured glyphs
 * would be the same clutter in a smaller font.
 */
@Composable
internal fun InfoButton(
    note: InfoNote,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(
            Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = note.title,
            tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.Inline),
        )
    }
    if (open) InfoSheet(note) { open = false }
}

/**
 * An [InfoNote] as a sheet, on the same terms as `ChannelScoreSheet`.
 *
 * Same padding, same scroll, same full-height state - a reader who has opened one explanation in
 * this app has opened all of them. It states nothing the page does not: the sheet is where the
 * words moved to, not a second place for them to drift.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InfoSheet(note: InfoNote, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .scrollableColumn()
                .padding(horizontal = Space.l)
                .padding(bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Text(note.title, style = MaterialTheme.typography.headlineSmall)
            note.paragraphs.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
 * The leading column a control stands in, as wide as the widest of them.
 *
 * A switch is 52dp wide, and every setting here is now one. The column is kept rather than folded
 * into the switch's own width so that a row built by hand - a button, a slider - can stand in the
 * same place and start its label where the switch rows start theirs.
 */
private val ControlColumn: Dp = 52.dp

/**
 * One line of a settings card: whatever the control is, and its explanation in the card's gutter.
 *
 * Written because the question marks had drifted. [SettingToggle] and [SettingLabel] put theirs at
 * the trailing edge; the four rows built by hand - Save diagnostics, Restore from a backup, Fetch
 * prices now, Add a schedule - put theirs immediately after the button, so on a card holding both
 * shapes the same affordance appeared in two places and neither read as a column. Here it has one
 * home, and a caller cannot put it anywhere else.
 *
 * The caller decides what stretches: give the element that should fill the line a `weight(1f)`, or
 * add a weighted [Spacer] after a button so the question mark is pushed to the edge.
 */
@Composable
internal fun SettingRow(
    modifier: Modifier = Modifier,
    about: InfoNote? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        content()
        about?.let { InfoButton(it, Modifier.padding(end = ChevronGutter)) }
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
) {
    SettingRow(modifier, about) {
        Box(Modifier.width(ControlColumn), contentAlignment = Alignment.CenterStart) {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
        Text(
            label,
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.m, end = Space.s),
            style = MaterialTheme.typography.bodyLarge,
        )
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
