package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Checkbox
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
 * A setting that is on or off, its name, and where its explanation went.
 *
 * Written once because it was written fifteen times: a `Row` holding a `Checkbox` and a `Text`,
 * then a paragraph underneath in `bodySmall` and `onSurfaceVariant`. The paragraph is now [about]
 * and the row is now level - the fifteen hand-built ones had drifted into a checkbox leading here,
 * a switch trailing there, and two different gaps between the control and its label.
 *
 * The control leads in every case, switch or checkbox. Which of the two is used says how heavy the
 * setting is, not where it sits: a switch is for something that arms the phone to act on its own,
 * which is the distinction `SchedulesSection` drew and the reason it is kept.
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
    /** A switch instead of a checkbox, for a setting that lets the phone act while nobody is looking. */
    switch: Boolean = false,
    enabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (switch) {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        } else {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
        Text(
            label,
            modifier = Modifier
                .weight(1f)
                // A switch draws no padding of its own, where a checkbox ships with it. Without
                // this the two rows put their labels at two different offsets on the same card.
                .padding(start = if (switch) Space.m else 0.dp)
                .padding(end = Space.s),
            style = MaterialTheme.typography.bodyLarge,
        )
        about?.let { InfoButton(it) }
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = style)
        about?.let { InfoButton(it) }
    }
}
