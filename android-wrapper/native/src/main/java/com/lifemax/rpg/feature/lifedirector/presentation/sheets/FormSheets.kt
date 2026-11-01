package com.lifemax.rpg.feature.lifedirector.presentation.sheets

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.core.designsystem.form.FieldValue
import com.lifemax.rpg.core.designsystem.form.FormKitError
import com.lifemax.rpg.core.designsystem.form.LocalFormKitLook
import com.lifemax.rpg.core.designsystem.form.LocalPickerFieldLook
import com.lifemax.rpg.core.designsystem.form.PickerHost
import com.lifemax.rpg.core.designsystem.form.PickerState
import com.lifemax.rpg.core.designsystem.form.SchemaForm
import com.lifemax.rpg.core.designsystem.form.pickers
import com.lifemax.rpg.feature.lifedirector.domain.FormSheetKinds
import com.lifemax.rpg.feature.lifedirector.domain.FormSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.GoalFormSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.GoalTypeOption
import com.lifemax.rpg.feature.lifedirector.domain.GoalTypeSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.domain.RuleSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.answers
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorUi
import com.lifemax.rpg.feature.lifedirector.presentation.FormSheetController
import com.lifemax.rpg.feature.lifedirector.presentation.FormUi
import com.lifemax.rpg.feature.lifedirector.presentation.components.ButtonLook
import com.lifemax.rpg.feature.lifedirector.presentation.components.ConfirmChangeSheet
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorButton
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorSheet
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText
import com.lifemax.rpg.feature.lifedirector.presentation.components.SheetMuted
import com.lifemax.rpg.feature.lifedirector.presentation.components.SheetTitle

/** What My time's sheets can ask for. */
class FormSheetActions(
    val change: (key: String, value: FieldValue) -> Unit = { _, _ -> },
    val save: () -> Unit = {},
    val close: () -> Unit = {},
    val remove: () -> Unit = {},
    val confirmRemove: () -> Unit = {},
    val keep: () -> Unit = {},
    val pick: (arch: String) -> Unit = {},
) {
    internal constructor(
        c: FormSheetController
    ) : this(c::change, c::save, c::close, c::askRemove, c::confirmRemove, c::keep, c::pick)
}

/**
 * My time's sheet the screen asked for (phase 4B), once the page has sent its model: the commitment
 * sheet, or "Add a goal" and its goal form in one sheet. Any other sheet is drawn elsewhere.
 * [animate] false (and reduced motion, and a screenshot) draws the morph settled. [foldOpen] starts
 * a goal form's optional target open (a screenshot shows it).
 */
@Composable
fun BoxScope.FormSheets(
    p: LifeDirectorProjection,
    ui: DirectorUi,
    bottomInset: Dp,
    actions: FormSheetActions,
    animate: Boolean = true,
    pickers: PickerState = remember { PickerState() },
    foldOpen: Boolean = false,
) {
    // Today's sheets are drawn elsewhere
    val open = ui.sheet?.takeIf { it.kind in FORM_KINDS } ?: return
    val model = p.sheet?.takeIf { it.answers(open) }
    // the grid stays while its picked kind's form is on its way, so the sheet never empties
    var lastGrid by remember { mutableStateOf<GoalTypeSheetModel?>(null) }
    if (model is GoalTypeSheetModel) lastGrid = model
    val forms = ui.forms
    CompositionLocalProvider(
        LocalPickerFieldLook provides LocalFormKitLook.current.pickers(),
        LocalTextStyle provides DirectorText,
    ) {
        PickerHost(pickers, Modifier.matchParentSize()) {
            when {
                model is RuleSheetModel -> RuleSheet(model, forms, bottomInset, actions)
                open.kind == FormSheetKinds.GOAL_TYPE || open.kind == FormSheetKinds.GOAL_FORM -> {
                    val grid =
                        (model as? GoalTypeSheetModel) ?: lastGrid?.takeIf { forms.picked != null }
                    val form = model as? GoalFormSheetModel
                    if (form != null || grid != null)
                        GoalSheets(
                            grid,
                            form,
                            forms,
                            bottomInset,
                            actions,
                            animate,
                            p.reducedMotion,
                            foldOpen,
                        )
                }
            }
        }
        forms.confirm?.let { c ->
            ConfirmChangeSheet(c, bottomInset, actions.confirmRemove, actions.keep)
        }
    }
}

/**
 * The page's commitment sheet (ruleSheet): its title, the form (the rotating check shows the days
 * on and off and the first day on, and hides the day chips), its note, then Add or Save, Cancel
 * and, editing, Remove.
 */
@Composable
fun BoxScope.RuleSheet(
    m: RuleSheetModel,
    forms: FormUi,
    bottomInset: Dp,
    actions: FormSheetActions,
) =
    DirectorSheet(m.title, bottomInset, actions.close) {
        SheetTitle(m.title)
        FormBody(m, forms, actions)
    }

/**
 * A form sheet's form, note, the page's refusal and its buttons; dimmed while its write is in
 * flight.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormBody(
    m: FormSheetModel,
    forms: FormUi,
    actions: FormSheetActions,
    foldOpen: Boolean = false,
) {
    val values =
        forms.drafts[com.lifemax.rpg.feature.lifedirector.domain.OpenSheet(m.kind, m.id)]
            ?: m.schema.initialValues()
    val waiting = forms.pending != null
    SchemaForm(
        m.schema,
        values,
        actions.change,
        errors = forms.errors,
        enabled = !waiting,
        foldOpen = foldOpen,
        typedInThrees = true,
    )
    SheetMuted(m.note, 13.76f)
    forms.refusal?.let { FormKitError(it) }
    // .ld-row: wraps, 8 dp apart, 10 dp under the form
    FlowRow(
        Modifier.padding(top = 10.dp).alpha(if (waiting) .45f else 1f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DirectorButton(m.save.label, ButtonLook.PRIMARY, actions.save)
        DirectorButton(m.cancel, ButtonLook.GHOST, actions.close)
        m.remove?.let { DirectorButton(it.label, ButtonLook.DANGER, actions.remove) }
    }
}

/**
 * "Add a goal" and its goal form, one sheet: the grid of kinds (goalSheet), and once one is picked
 * its form (goalForm), which grows out of the picked tile over 300 ms while the sheet's height
 * follows (plan §2, "Goal sheets"). With reduced motion the form cross-fades in instead; settled at
 * once when not animated.
 */
@Composable
private fun BoxScope.GoalSheets(
    grid: GoalTypeSheetModel?,
    form: GoalFormSheetModel?,
    forms: FormUi,
    bottomInset: Dp,
    actions: FormSheetActions,
    animate: Boolean,
    reduced: Boolean,
    foldOpen: Boolean,
) {
    val tiles = remember { mutableStateMapOf<String, Rect>() }
    var area by remember { mutableStateOf<Rect?>(null) }
    val title = form?.title ?: grid?.title.orEmpty()
    DirectorSheet(title, bottomInset, actions.close) {
        val settled = LocalInspectionMode.current || !animate
        Column(
            Modifier.fillMaxWidth()
                .then(
                    if (settled || reduced) Modifier
                    else Modifier.animateContentSize(tween(MORPH_MS))
                )
        ) {
            if (form == null && grid != null) {
                SheetTitle(grid.title)
                SheetMuted(grid.text, 13.76f)
                GoalGrid(grid.options, forms.picked, actions.pick) { arch, r -> tiles[arch] = r }
            } else if (form != null) {
                val grow = remember(form.arch) { Animatable(if (settled) 1f else 0f) }
                LaunchedEffect(form.arch) { grow.animateTo(1f, tween(MORPH_MS)) }
                val from = forms.picked?.let { tiles[it] }
                Column(
                    Modifier.fillMaxWidth()
                        .onGloballyPositioned { area = it.boundsInRoot() }
                        .graphicsLayer {
                            val v = grow.value
                            val to = area
                            if (v >= 1f) return@graphicsLayer
                            if (
                                reduced ||
                                    from == null ||
                                    to == null ||
                                    to.width <= 0f ||
                                    to.height <= 0f
                            ) {
                                alpha = v
                                return@graphicsLayer
                            }
                            // from the tile's place and size to the form's
                            transformOrigin =
                                TransformOrigin(
                                    ((from.center.x - to.left) / to.width).coerceIn(0f, 1f),
                                    ((from.center.y - to.top) / to.height).coerceIn(0f, 1f),
                                )
                            scaleX = lerp(from.width / to.width, 1f, v)
                            scaleY = lerp((from.height / to.height).coerceAtMost(1f), 1f, v)
                            alpha = lerp(.35f, 1f, v)
                        }
                ) {
                    SheetTitle(form.title)
                    FormBody(form, forms, actions, foldOpen)
                }
            }
        }
    }
}

/**
 * The page's grid of kinds (.ld-arch): two columns 8 dp apart, each kind a tile with its icon in
 * the task colour, its name and the sessions it plans. A tile picked shows it while its form is on
 * its way.
 */
@Composable
private fun GoalGrid(
    options: List<GoalTypeOption>,
    picked: String?,
    onPick: (String) -> Unit,
    placed: (String, Rect) -> Unit,
) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { o ->
                    Tile(
                        o,
                        o.arch == picked,
                        { onPick(o.arch) },
                        Modifier.weight(1f).fillMaxHeight().onGloballyPositioned {
                            placed(o.arch, it.boundsInRoot())
                        },
                    )
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** .ld-archbtn: 12 dp in, 14 dp round, a faint fill inside the second line. */
@Composable
private fun Tile(o: GoalTypeOption, picked: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = .035f), shape)
            .border(1.dp, if (picked) P.task else P.line2, shape)
            .tapTarget(Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PixelIcon(o.icon, P.task, Modifier.size(16.dp))
        Text(
            o.title,
            style = DirectorText,
            color = P.ink,
            fontSize = 14.4.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(o.sub, style = DirectorText, color = P.ink3, fontSize = 11.5.sp, lineHeight = 1.35.em)
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** The morph's length (plan §3.7: a height morph on replace, 300 ms). */
private const val MORPH_MS = 300

private val FORM_KINDS =
    setOf(FormSheetKinds.RULE, FormSheetKinds.GOAL_TYPE, FormSheetKinds.GOAL_FORM)
