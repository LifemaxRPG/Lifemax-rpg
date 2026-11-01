package com.lifemax.rpg.feature.lifedirector.presentation.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.core.designsystem.form.ClockText
import com.lifemax.rpg.core.designsystem.form.DateField
import com.lifemax.rpg.core.designsystem.form.LocalPickerFieldLook
import com.lifemax.rpg.core.designsystem.form.PickerFieldLook
import com.lifemax.rpg.core.designsystem.form.PickerHost
import com.lifemax.rpg.core.designsystem.form.PickerState
import com.lifemax.rpg.core.designsystem.form.StepperField
import com.lifemax.rpg.core.designsystem.form.TimeField
import com.lifemax.rpg.feature.lifedirector.domain.DisruptDate
import com.lifemax.rpg.feature.lifedirector.domain.DisruptInput
import com.lifemax.rpg.feature.lifedirector.domain.DisruptNumber
import com.lifemax.rpg.feature.lifedirector.domain.DisruptOption
import com.lifemax.rpg.feature.lifedirector.domain.DisruptSection
import com.lifemax.rpg.feature.lifedirector.domain.DisruptSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.DisruptTime
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.domain.MoveSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.MoveTime
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorUi
import com.lifemax.rpg.feature.lifedirector.presentation.components.ButtonLook
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorButton
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorSheet
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText
import com.lifemax.rpg.feature.lifedirector.presentation.components.SheetMuted
import com.lifemax.rpg.feature.lifedirector.presentation.components.SheetTitle

/** What the Move and "Something came up" sheets can ask for. */
class MoveDisruptActions(
    val close: () -> Unit = {},
    val moveTo: (MoveTime) -> Unit = {},
    /** a fold's button (no option, with the form's values) or one of its choices */
    val answer: (DisruptSection, Map<String, Any>, DisruptOption?) -> Unit = { _, _, _ -> },
)

/**
 * The Move or "Something came up" sheet the screen asked for, once the page has sent its model (any
 * other sheet is drawn elsewhere). [pending] is Today's write in flight.
 */
@Composable
fun BoxScope.MoveDisruptSheets(
    p: LifeDirectorProjection,
    ui: DirectorUi,
    bottomInset: Dp,
    actions: MoveDisruptActions,
) {
    val open = ui.sheet ?: return
    val model = p.sheet?.takeIf { it.kind == open.kind && it.id == open.id } ?: return
    when (model) {
        is MoveSheetModel -> MoveSheet(model, ui.pendingToday, bottomInset, actions)
        is DisruptSheetModel -> DisruptSheet(model, ui.pendingToday, bottomInset, actions)
        else -> {}
    }
}

/**
 * The page's Move sheet (moveSheet): the title, its note, then a button per open time, or the
 * page's words when none fits.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoxScope.MoveSheet(
    m: MoveSheetModel,
    pending: String?,
    bottomInset: Dp,
    actions: MoveDisruptActions,
) =
    DirectorSheet(m.title, bottomInset, actions.close) {
        SheetTitle(m.title)
        SheetMuted(m.note, 13.76f)
        if (m.times.isEmpty())
            Text(m.none, Modifier.padding(top = 14.dp), style = DirectorText, color = P.ink)
        else
            FlowRow(
                Modifier.padding(top = 10.dp).alpha(waiting(pending)),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                m.times.forEach { t ->
                    DirectorButton(t.label, ButtonLook.PLAIN, { actions.moveTo(t) }, small = true)
                }
            }
    }

/**
 * The page's "Something came up" sheet (disruptSheet): the title and note, then each fold as the
 * page's details, the first open. "Called into work" has its form (date, travel, from and until,
 * with pickers in the page's clock style) and its button; the others their choices or button and
 * note. [pickers] is the picker open over the sheet (a screenshot opens one).
 */
@Composable
fun BoxScope.DisruptSheet(
    d: DisruptSheetModel,
    pending: String?,
    bottomInset: Dp,
    actions: MoveDisruptActions,
    pickers: PickerState = remember { PickerState() },
    allOpen: Boolean = false,
) =
    CompositionLocalProvider(
        LocalPickerFieldLook provides DIRECTOR_FIELDS,
        LocalTextStyle provides DirectorText,
    ) {
        PickerHost(pickers, Modifier.matchParentSize()) {
            DirectorSheet(d.title, bottomInset, actions.close) {
                SheetTitle(d.title)
                SheetMuted(d.note, 13.76f)
                Column(Modifier.alpha(waiting(pending))) {
                    d.sections.forEach { s -> Fold(s, allOpen, actions) }
                }
            }
        }
    }

/** One details fold: its summary toggles it; open, it shows what the section asks for. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Fold(s: DisruptSection, allOpen: Boolean, actions: MoveDisruptActions) {
    var open by rememberSaveable(s.key) { mutableStateOf(s.open || allOpen) }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .tapTarget(Role.Button) { open = !open }
            .semantics {
                heading()
                stateDescription = if (open) "Expanded" else "Collapsed"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Marker(open)
        Text(s.title, style = DirectorText, color = P.ink2, fontWeight = FontWeight(650))
    }
    if (!open) return
    Column(Modifier.padding(bottom = 8.dp)) {
        // the form's values as the player sets them, from the page's own
        val values =
            remember(s.form) {
                mutableStateMapOf(*s.form.map { it.key to it.initial }.toTypedArray())
            }
        if (s.form.isNotEmpty()) FormRows(s.form, values)
        if (s.options.isNotEmpty())
            FlowRow(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                s.options.forEach { o ->
                    DirectorButton(
                        o.label,
                        ButtonLook.PLAIN,
                        { actions.answer(s, emptyMap(), o) },
                        small = true,
                    )
                }
            }
        s.button?.let { b ->
            DirectorButton(
                b,
                ButtonLook.PLAIN,
                { actions.answer(s, values.toMap(), null) },
                Modifier.padding(top = 12.dp),
            )
        }
        SheetMuted(s.note, 13.76f)
    }
}

/** The form's two rows of two (.ld-grid2), each field with its picker or stepper. */
@Composable
private fun FormRows(form: List<DisruptInput>, values: MutableMap<String, Any>) {
    for (group in 0..1) {
        val row = form.filter { it.group == group }
        if (row.isEmpty()) continue
        Row(
            Modifier.padding(top = 10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            row.forEach { f -> Field(f, values, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Field(f: DisruptInput, values: MutableMap<String, Any>, modifier: Modifier) {
    val set = { v: Any -> values[f.key] = v }
    when (f) {
        is DisruptDate -> DateField(f.label, values[f.key] as? String ?: f.value, set, modifier)
        is DisruptNumber ->
            StepperField(
                f.label,
                values[f.key] as? Int ?: f.value,
                set,
                modifier,
                min = f.min,
                step = f.step ?: 1,
            )
        is DisruptTime -> {
            val v = values[f.key] as? Int ?: f.value
            TimeField(
                f.label,
                v,
                set,
                modifier,
                h12 = ClockText.is12h(f.text),
                // the page's words while the time is the page's
                text = f.text.takeIf { v == f.value },
            )
        }
    }
}

private val DisruptInput.initial: Any
    get() =
        when (this) {
            is DisruptDate -> value
            is DisruptNumber -> value
            is DisruptTime -> value
        }

/** The buttons wait, dimmed, while a write is in flight. */
private fun waiting(pending: String?) = if (pending != null) .45f else 1f

/** The page's form look on Life Director's panel (.ld-root label, its inputs, the sheet). */
private val DIRECTOR_FIELDS =
    PickerFieldLook(
        label = P.ink2,
        ink = P.ink,
        fill = Color(0xFF0B1019),
        border = P.line2,
        accent = P.free,
        onAccent = Color.White,
        button = P.buttonFill,
        panel = P.sheetTop,
        scrim = P.scrim,
        muted = P.ink3,
    )
