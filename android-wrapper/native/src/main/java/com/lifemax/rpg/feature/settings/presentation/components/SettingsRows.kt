package com.lifemax.rpg.feature.settings.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.components.PageCard
import com.lifemax.rpg.core.designsystem.components.PageCheck
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.core.designsystem.components.toggleTarget
import com.lifemax.rpg.feature.settings.domain.SettingsCard
import com.lifemax.rpg.feature.settings.domain.SettingsControl
import com.lifemax.rpg.feature.settings.domain.SettingsRow
import com.lifemax.rpg.feature.settings.presentation.SettingsUi
import kotlin.math.max

/*
 * A card's rows as the page lays them out at phone width. Each row is an item of the list, so the
 * spaces between rows are worked out here the way CSS works them out, margins collapsing.
 */

/** Inside a card: its 15 dp padding and its 1 dp edge. */
internal val CardInset = 16.dp

/**
 * How a row sits in its card: the space above it (its share of the gap to the row before, plus its
 * own padding), the space below it (its padding, and the card's own at the end), and whether a line
 * runs under it.
 */
data class RowBox(val top: Dp, val bottom: Dp, val line: Boolean)

/** The margin under the card's title, or under its intro when it has one. */
internal fun SettingsCard.headGap(): Dp =
    when {
        intro.isEmpty() -> 12.dp // .card h3's margin-bottom
        rows.firstOrNull() is SettingsRow.Tab -> 8.dp // the bar editor's intro (margin:-5px 0 8px)
        else -> 12.dp // a <p class="muted">'s own margin
    }

/** Whether a row is the profile picture's: the avatar at its left, not a .settingsline. */
internal fun SettingsRow.Line.isAvatar(): Boolean = controls.firstOrNull() is SettingsControl.Avatar

/**
 * The space above and below each of [card]'s rows. A row's margins collapse with the row before
 * (the larger wins), as CSS margins do between siblings; paddings never do. The values are the
 * page's: .settingsline's 13 dp padding and the line under it (except under the card's last row),
 * the profile picture's 14 and 16 dp margins, a note's 10 dp under it (12 above when rows come
 * first), a muted paragraph's 8 dp above, the haptic strength's 10 above and 8 under, buttons on
 * their own 12 dp under what comes before them (none under more buttons), a form field's 12 dp.
 */
fun rowBoxes(card: SettingsCard): List<RowBox> {
    var before = card.headGap()
    return card.rows.mapIndexed { i, row ->
        val last = i == card.rows.lastIndex
        val m = margins(row, i, last, card.rows.getOrNull(i - 1))
        val top = max(before.value, m.top.value).dp + m.padding
        before = m.bottom
        RowBox(top, m.padding + if (last) m.bottom + CardInset else 0.dp, m.line && !last)
    }
}

private class Margins(
    val top: Dp,
    val bottom: Dp,
    val padding: Dp = 0.dp,
    val line: Boolean = false,
)

private fun margins(row: SettingsRow, index: Int, last: Boolean, before: SettingsRow?): Margins =
    when (row) {
        is SettingsRow.Line ->
            if (row.isAvatar()) Margins(14.dp, 16.dp) else Margins(0.dp, 0.dp, 13.dp, line = true)
        is SettingsRow.Tab -> Margins(0.dp, 0.dp, 13.dp, line = true)
        is SettingsRow.Note -> Margins(if (index == 0) 0.dp else 12.dp, if (last) 0.dp else 10.dp)
        is SettingsRow.Text -> Margins(8.dp, 0.dp)
        is SettingsRow.Controls ->
            if (row.controls.any { it is SettingsControl.Choice }) Margins(10.dp, 8.dp)
            else Margins(if (before is SettingsRow.Controls) 0.dp else 12.dp, 0.dp)
        is SettingsRow.Field -> Margins(12.dp, 0.dp)
    }

/**
 * A key for the row that stays with it while rows come, go and move: its first control's id (each
 * is the page's own and used once), or its place in its card for a row without controls.
 */
fun SettingsRow.key(card: SettingsCard, index: Int): String {
    val first =
        when (this) {
            is SettingsRow.Tab -> show
            is SettingsRow.Line -> controls.firstOrNull()
            is SettingsRow.Note -> controls.firstOrNull()
            is SettingsRow.Controls -> controls.firstOrNull()
            is SettingsRow.Field -> controls.firstOrNull()
            is SettingsRow.Text -> null
        }
    return if (first != null) "control/${first.id}" else "row/${card.id}/$index"
}

/**
 * The top of a card: its title (.card h3: 13.5/650 text-dim, a heading for TalkBack) and its intro
 * (12/18 muted, 7 dp under the title). The space under it belongs to the first row; a card with no
 * rows ends here.
 */
@Composable
internal fun CardTop(card: SettingsCard) {
    val c = LocalPageColors.current
    Column(
        Modifier.fillMaxWidth()
            .padding(
                start = CardInset,
                end = CardInset,
                top = CardInset,
                bottom = if (card.rows.isEmpty()) card.headGap() + CardInset else 0.dp,
            )
    ) {
        Text(
            card.title,
            Modifier.semantics { heading() },
            color = c.textDim,
            fontSize = 13.5.sp,
            fontWeight = FontWeight(650),
            letterSpacing = (-0.135).sp,
            lineHeight = 20.25.sp,
        )
        if (card.intro.isNotEmpty())
            Text(
                card.intro,
                Modifier.padding(top = 7.dp),
                color = c.muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
    }
}

/** One row of a card, inside the card's padding, with the space [box] gives it. */
@Composable
internal fun RowView(row: SettingsRow, box: RowBox, ui: SettingsUi, taps: ControlTaps) {
    val c = LocalPageColors.current
    val frame = Modifier.fillMaxWidth().padding(horizontal = CardInset).rowLine(c, box.line)
    val inside = Modifier.padding(top = box.top, bottom = box.bottom)
    when (row) {
        is SettingsRow.Line ->
            if (row.isAvatar()) AvatarRow(row, ui, taps, frame.then(inside))
            else LineRow(row, ui, taps, frame, inside)
        is SettingsRow.Tab -> TabRow(row, ui, taps, frame.then(inside))
        is SettingsRow.Note -> NoteRow(row, ui, taps, frame.then(inside))
        is SettingsRow.Text -> MutedText(row.text, frame.then(inside))
        is SettingsRow.Controls -> ControlsRow(row, ui, taps, frame.then(inside))
        is SettingsRow.Field -> FieldRow(row, ui, taps, frame.then(inside))
    }
}

/**
 * A .settingsline: the title (14/700, in the bad colour for a danger row) and the hint (12/18
 * muted) on the left, the controls on the right, 14 dp apart and centred; the controls go under the
 * words when they would leave the words less than two fifths of the row. A row whose only control
 * is a switch is the switch, the whole row a target TalkBack reads as the switch named by the
 * page's label, with the hint after it.
 */
@Composable
private fun LineRow(
    row: SettingsRow.Line,
    ui: SettingsUi,
    taps: ControlTaps,
    frame: Modifier,
    inside: Modifier,
) {
    val switch = row.controls.singleOrNull() as? SettingsControl.Switch
    if (switch != null) {
        val pending = ui.pending(switch)
        Box(
            frame
                .toggleTarget(ui.on(switch), enabled = switch.enabled && !pending) {
                    taps.toggle(switch)
                }
                .semantics { switchState(switch, pending, ui)?.let { stateDescription = it } }
                .then(inside)
        ) {
            WordsAndControls(
                { Words(row.title, row.hint, row.danger, switch.label) },
                {
                    PageCheck(
                        ui.on(switch),
                        Modifier.padding(top = 1.dp),
                        enabled = switch.enabled,
                        pending = pending,
                    )
                },
            )
        }
        return
    }
    Box(frame.then(inside)) {
        WordsAndControls(
            { Words(row.title, row.hint, row.danger) },
            if (row.controls.isEmpty()) null
            else {
                { Controls(row.controls, ui, taps, Place.Row) }
            },
        )
    }
}

/**
 * A row's words: the title, then the hint lines. [name], when given, is what TalkBack reads in
 * place of the title: the page's own name for the row's switch.
 */
@Composable
private fun Words(title: String, hint: String, danger: Boolean, name: String? = null) {
    val c = LocalPageColors.current
    Column {
        if (title.isNotEmpty())
            Text(
                title,
                if (name.isNullOrBlank() || name == title) Modifier
                else Modifier.clearAndSetSemantics { text = AnnotatedString(name) },
                color = if (danger) c.bad else c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 21.sp,
            )
        else if (!name.isNullOrBlank()) Box(Modifier.semantics { text = AnnotatedString(name) })
        if (hint.isNotEmpty()) MutedText(hint)
    }
}

@Composable
private fun MutedText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier,
        color = LocalPageColors.current.muted,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
}

/** Controls in the page's order, 6 dp apart, wrapping when the row is too narrow for them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(
    controls: List<SettingsControl>,
    ui: SettingsUi,
    taps: ControlTaps,
    place: Place,
    modifier: Modifier = Modifier,
) {
    val regular = controls.any { it is SettingsControl.Button && !it.small }
    FlowRow(
        modifier,
        // standalone regular buttons sit 7 dp apart (the XP rules' margin-left), the rest 6
        horizontalArrangement = Arrangement.spacedBy(if (regular) 7.dp else 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        controls.forEach {
            ControlView(it, ui, taps, place, Modifier.align(Alignment.CenterVertically))
        }
    }
}

/**
 * The words on the left and the controls on the right, 14 dp apart and centred on each other, as
 * .settingsline lays them out; the controls move under the words, 10 dp below, when they need more
 * than three fifths of the row (large text, two time fields).
 */
@Composable
private fun WordsAndControls(words: @Composable () -> Unit, controls: (@Composable () -> Unit)?) {
    if (controls == null) {
        Box(Modifier.fillMaxWidth()) { words() }
        return
    }
    Layout(listOf(words, controls), Modifier.fillMaxWidth()) { (w, ctl), constraints ->
        val width = constraints.maxWidth
        val gap = 14.dp.roundToPx()
        val wordsMeasurable = w.first()
        val controlsMeasurable = ctl.first()
        val needed = controlsMeasurable.maxIntrinsicWidth(Constraints.Infinity)
        if (needed <= (width - gap) * 3 / 5) {
            val c = controlsMeasurable.measure(Constraints(maxWidth = needed))
            val t = wordsMeasurable.measure(Constraints(maxWidth = max(0, width - gap - c.width)))
            val height = max(t.height, c.height)
            layout(width, height) {
                t.placeRelative(0, (height - t.height) / 2)
                c.placeRelative(width - c.width, (height - c.height) / 2)
            }
        } else {
            val t = wordsMeasurable.measure(Constraints(maxWidth = width))
            val c = controlsMeasurable.measure(Constraints(maxWidth = width))
            val below = 10.dp.roundToPx()
            layout(width, t.height + below + c.height) {
                t.placeRelative(0, 0)
                c.placeRelative(0, t.height + below)
            }
        }
    }
}

/**
 * The profile picture's row: the 64 dp picture on the left, 14 dp from the title, the hint (2 dp
 * under the title, 8 above the buttons) and the small buttons side by side under it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvatarRow(
    row: SettingsRow.Line,
    ui: SettingsUi,
    taps: ControlTaps,
    modifier: Modifier,
) {
    val c = LocalPageColors.current
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlView(row.controls.first(), ui, taps, Place.Loose)
        Column(Modifier.weight(1f)) {
            if (row.title.isNotEmpty())
                Text(
                    row.title,
                    color = if (row.danger) c.bad else c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 21.sp,
                )
            if (row.hint.isNotEmpty())
                MutedText(row.hint, Modifier.padding(top = 2.dp, bottom = 8.dp))
            val rest = row.controls.drop(1)
            if (rest.isNotEmpty())
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    rest.forEach { ControlView(it, ui, taps, Place.Loose) }
                }
        }
    }
}

/**
 * A bottom-bar row (.tabrow): the tab's pixel icon in muted (24 dp wide, 18 dp icon), its name, the
 * ◀ and ▶ that move it (none for a tab not in the bar) and the box that shows it, 6 dp apart; a
 * hidden tab's row at .55 opacity. The box is the row's switch, by the page's label.
 */
@Composable
private fun TabRow(row: SettingsRow.Tab, ui: SettingsUi, taps: ControlTaps, modifier: Modifier) {
    val c = LocalPageColors.current
    Row(
        modifier.alpha(if (row.visible) 1f else .55f),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            if (row.icon.isNotEmpty()) PixelIcon(row.icon, c.muted, Modifier.size(18.dp))
        }
        Text(
            row.label,
            Modifier.weight(1f),
            color = c.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 21.sp,
        )
        row.earlier?.let { ArrowButton(it, ui, taps) }
        row.later?.let { ArrowButton(it, ui, taps) }
        SwitchBox(row.show, ui, taps)
    }
}

/**
 * A .note: bg-elev with a line edge, 10 dp corners, 12 by 14 padding, 12.5/18.75 muted (in the
 * light theme a pale blue box with text-dim). A warning note has the --warn edge and the text
 * colour. Its buttons follow its words.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteRow(row: SettingsRow.Note, ui: SettingsUi, taps: ControlTaps, modifier: Modifier) {
    val c = LocalPageColors.current
    val ink = SettingsInk(c)
    val shape = RoundedCornerShape(10.dp)
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(ink.noteFill)
                .border(1.dp, if (row.warn) ink.warn else ink.noteEdge, shape)
                .padding(horizontal = 15.dp, vertical = 13.dp)
        ) {
            Text(
                row.text,
                color = if (row.warn) c.text else ink.noteText,
                fontSize = 12.5.sp,
                lineHeight = 18.75.sp,
            )
            if (row.controls.isNotEmpty())
                FlowRow(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.controls.forEach { ControlView(it, ui, taps, Place.Loose) }
                }
        }
    }
}

/** Controls standing on their own: buttons, or the haptic strength. */
@Composable
private fun ControlsRow(
    row: SettingsRow.Controls,
    ui: SettingsUi,
    taps: ControlTaps,
    modifier: Modifier,
) {
    Box(modifier) { Controls(row.controls, ui, taps, Place.Loose) }
}

/**
 * A form field (.field at phone width): its label (12/550 muted) 6 dp above the input, or above two
 * inputs side by side 8 dp apart (Height: feet and inches).
 */
@Composable
private fun FieldRow(
    row: SettingsRow.Field,
    ui: SettingsUi,
    taps: ControlTaps,
    modifier: Modifier,
) {
    val c = LocalPageColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (row.label.isNotEmpty())
            Text(
                row.label,
                Modifier.clearAndSetSemantics {},
                color = c.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight(550),
                lineHeight = 18.sp,
            )
        Row(horizontalArrangement = Arrangement.spacedBy(FormGap)) {
            row.controls.forEach { ControlView(it, ui, taps, Place.Form, Modifier.weight(1f)) }
        }
    }
}

/**
 * The screen's own row, the way back to the page it is drawn from: a card with the row's words and
 * a chevron, the whole card a button.
 */
@Composable
internal fun ClassicCard(label: String, hint: String, onOpen: () -> Unit) {
    val c = LocalPageColors.current
    PageCard(Modifier.padding(bottom = 14.dp), padding = 0.dp) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClickLabel = "Open", role = Role.Button) { onOpen() }
                .padding(CardInset),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    color = c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 21.sp,
                )
                if (hint.isNotEmpty()) MutedText(hint)
            }
            Text(
                "›",
                Modifier.clearAndSetSemantics {},
                color = c.muted,
                fontSize = 22.sp,
                lineHeight = 22.sp,
            )
        }
    }
}
