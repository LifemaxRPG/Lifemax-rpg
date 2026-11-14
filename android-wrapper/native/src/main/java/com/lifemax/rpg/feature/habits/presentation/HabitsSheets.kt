package com.lifemax.rpg.feature.habits.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.core.designsystem.form.FormKitError
import com.lifemax.rpg.core.designsystem.form.FormKitLook
import com.lifemax.rpg.core.designsystem.form.FormTextField
import com.lifemax.rpg.core.designsystem.form.LocalFormKitLook
import com.lifemax.rpg.core.designsystem.gesture.noTabSwipe
import com.lifemax.rpg.feature.habits.domain.HabitBlock
import com.lifemax.rpg.feature.habits.domain.HabitStack
import com.lifemax.rpg.feature.habits.domain.HabitsProjection
import com.lifemax.rpg.feature.habits.domain.TowerSpec

/**
 * The sheet on show, rising from the bottom above the shared bottom bar ([bottomInset]) and never
 * over the top bar ([topInset]). A block's dials leave the scene bright and tappable behind them,
 * so another block can be picked straight away; every other sheet dims it, and a tap on the dimmed
 * scene closes the sheet unless the page is answering. [onHeight] reports the panel's height, so
 * the scene can keep the selected block clear of it.
 */
@Composable
internal fun BoxScope.HabitsSheets(
    p: HabitsProjection,
    sheet: HabitsSheet,
    bottomInset: Dp,
    topInset: Dp,
    actions: HabitsActions,
    onHeight: (Int) -> Unit,
) {
    val colors = LocalPageColors.current
    val words = HabitsCopy.sheet(sheet, p)
    val dials = sheet.kind == SheetKind.TUNE
    if (!dials)
        Box(
            Modifier.matchParentSize()
                .background(Scrim)
                .clearAndSetSemantics {}
                .pointerInput(Unit) { detectTapGestures { actions.back() } }
        )
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Box(
        Modifier.align(Alignment.BottomCenter)
            .padding(top = topInset + 8.dp, bottom = bottomInset)
            .imePadding()
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .onSizeChanged { onHeight(it.height) }
            .clip(shape)
            .background(colors.panel)
            .border(1.dp, colors.line, shape)
            .pointerInput(Unit) { detectTapGestures {} }
            .semantics { paneTitle = words.title }
    ) {
        CompositionLocalProvider(
            LocalFormKitLook provides remember(p.tokens, p.light) { formLook(colors) }
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally)
                        .size(40.dp, 4.dp)
                        .background(colors.line, RoundedCornerShape(4.dp))
                )
                val block = p.block(sheet.target)
                if (dials && block != null) Dials(p, block, p.stackOf(block.id), sheet, actions)
                else Form(p, sheet, words, actions)
            }
        }
    }
}

// ---- the forms: everything that is typed or confirmed ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Form(
    p: HabitsProjection,
    sheet: HabitsSheet,
    words: HabitsCopy.SheetWords,
    actions: HabitsActions,
) {
    val colors = LocalPageColors.current
    val limits = HabitsCopy.limits(sheet, p)
    Title(words.title)
    if (words.lead.isNotEmpty())
        Text(words.lead, color = colors.muted, fontSize = 13.5.sp, lineHeight = 19.sp)
    if (sheet.kind == SheetKind.NEW_HABIT)
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Choice("Build a habit", !sheet.fade, true) { actions.kind(false) }
            Choice("Break a habit", sheet.fade, true) { actions.kind(true) }
        }
    sheet.fields.forEachIndexed { i, value ->
        FormTextField(
            words.labels.getOrElse(i) { "" },
            value,
            { actions.type(i, it) },
            Modifier.noTabSwipe(),
            placeholder = words.hints.getOrNull(i)?.takeIf { it.isNotEmpty() },
            maxLength = limits.getOrNull(i),
            number = sheet.kind == SheetKind.NEW_HABIT && sheet.fade && i == 1,
        )
    }
    if (sheet.kind == SheetKind.NEW_STACK && p.starters.isNotEmpty())
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            p.starters.forEach { cue ->
                Text(
                    cue,
                    Modifier.clip(Pill)
                        .background(colors.panel2)
                        .border(1.dp, colors.line, Pill)
                        .tapTarget(Role.Button, "Lay the foundation $cue") { actions.starter(cue) }
                        .padding(horizontal = 14.dp),
                    color = colors.text,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    if (sheet.kind == SheetKind.RESTACK) {
        val here = p.stackOf(sheet.target)?.id
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            p.stacks.forEach { s -> StackRow(s, s.id == here, true) { actions.restackTo(s.id) } }
        }
    }
    if (sheet.error.isNotEmpty()) FormKitError(sheet.error)
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetButton(
            if (sheet.kind in FROM_DIALS) HabitsCopy.BACK else HabitsCopy.CANCEL,
            ButtonKind.QUIET,
            true,
            actions.back,
        )
        Spacer(Modifier.weight(1f))
        if (words.danger.isNotEmpty())
            SheetButton(words.danger, ButtonKind.DANGER, true, actions.danger)
        if (sheet.fields.isNotEmpty()) SheetButton(words.go, ButtonKind.MAIN, true, actions.submit)
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text,
        Modifier.semantics { heading() },
        color = LocalPageColors.current.text,
        fontSize = 19.sp,
        fontWeight = FontWeight(750),
        lineHeight = 24.sp,
    )
}

/** One of two: build or break. */
@Composable
private fun RowScope.Choice(text: String, on: Boolean, enabled: Boolean, pick: () -> Unit) {
    val colors = LocalPageColors.current
    val shape = RoundedCornerShape(12.dp)
    Text(
        text,
        Modifier.weight(1f)
            .clip(shape)
            .background(if (on) colors.accentDim else colors.panel2)
            .border(1.dp, if (on) colors.accent else colors.line, shape)
            .selectable(on, enabled = enabled, role = Role.RadioButton, onClick = pick)
            .heightIn(min = 46.dp)
            .padding(horizontal = 10.dp, vertical = 13.dp),
        color = if (on) colors.text else colors.muted,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

/** A foundation to stand the block on. */
@Composable
private fun StackRow(stack: HabitStack, on: Boolean, enabled: Boolean, pick: () -> Unit) {
    val colors = LocalPageColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(if (on) colors.accentDim else colors.panel2)
            .border(1.dp, if (on) colors.accent else colors.line, shape)
            .selectable(on, enabled = enabled, role = Role.RadioButton, onClick = pick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stack.cue,
            Modifier.weight(1f),
            color = colors.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "${stack.blocks.size} ${if (stack.blocks.size == 1) "habit" else "habits"}",
            color = colors.muted,
            fontSize = 12.5.sp,
        )
    }
}

private enum class ButtonKind {
    MAIN,
    QUIET,
    DANGER,
}

@Composable
private fun SheetButton(
    text: String,
    kind: ButtonKind,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPageColors.current
    val shape = RoundedCornerShape(14.dp)
    Text(
        text,
        modifier
            .alpha(if (enabled) 1f else .5f)
            .clip(shape)
            .background(
                when (kind) {
                    ButtonKind.MAIN -> colors.accent
                    ButtonKind.QUIET -> Color.Transparent
                    ButtonKind.DANGER -> colors.bad.copy(alpha = .16f)
                }
            )
            .tapTarget(Role.Button) { if (enabled) onClick() }
            .padding(horizontal = 16.dp),
        color =
            when (kind) {
                ButtonKind.MAIN -> colors.onAccent
                ButtonKind.QUIET -> colors.muted
                ButtonKind.DANGER -> colors.bad
            },
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
    )
}

// ---- a block's dials ----

/**
 * What can be turned on a habit, drawn as what it does, not as a settings list: its size as three
 * blocks of growing height (the one picked is the habit's own), what it is paired with and the
 * foundation it stands on as two tiles, its place in the order, and, quietly at the bottom, taking
 * today's block back and retiring it. A habit being broken shows its floors instead of a size.
 */
@Composable
private fun Dials(
    p: HabitsProjection,
    b: HabitBlock,
    stack: HabitStack?,
    sheet: HabitsSheet,
    actions: HabitsActions,
) {
    val colors = LocalPageColors.current
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                b.name,
                Modifier.semantics { heading() },
                color = colors.text,
                fontSize = 19.sp,
                fontWeight = FontWeight(750),
                lineHeight = 24.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                HabitsCopy.material(b.material) + " · " + HabitsCopy.count(b),
                color = colors.muted,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
            )
        }
        SheetButton("Rename", ButtonKind.QUIET, true, actions.names)
        Text(
            "×",
            Modifier.clip(RoundedCornerShape(12.dp))
                .tapTarget(Role.Button, onClick = actions.close)
                .padding(horizontal = 12.dp)
                .semantics { contentDescription = HabitsCopy.CLOSE },
            color = colors.muted,
            fontSize = 22.sp,
        )
    }
    val coach = HabitsCopy.coach(b)
    if (coach.isNotEmpty())
        Text(coach, color = colors.textDim, fontSize = 13.5.sp, lineHeight = 19.sp)
    if (b.cleared) {
        SheetButton("Stop tracking this", ButtonKind.DANGER, true, actions.retire)
        return
    }
    if (b.fade) Floors(b)
    else
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HabitsCopy.SIZES.forEachIndexed { i, label ->
                SizeTile(i, label, b.names[i], b.set == i, b.miss >= 2 && i < b.set) {
                    actions.size(i)
                }
            }
        }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile(
            if (b.fade) "INSTEAD" else "PAIRED WITH",
            b.pair.ifEmpty { "Nothing yet" },
            b.pair.isNotEmpty(),
            actions.pair,
        )
        Tile("STANDS ON", stack?.cue.orEmpty(), true, actions.restack, enabled = p.stacks.size > 1)
    }
    val order = stack?.blocks.orEmpty()
    if (order.size > 1)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetButton(
                "↓ Do it earlier",
                ButtonKind.QUIET,
                order.first().id != b.id,
                { actions.move(-1) },
                Modifier.weight(1f),
            )
            SheetButton(
                "Do it later ↑",
                ButtonKind.QUIET,
                order.last().id != b.id,
                { actions.move(1) },
                Modifier.weight(1f),
            )
        }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (b.done) SheetButton("Undo today's block", ButtonKind.QUIET, true, actions.undo)
        Spacer(Modifier.weight(1f))
        SheetButton(
            if (b.fade) "Stop tracking this" else "Retire this habit",
            ButtonKind.DANGER,
            true,
            actions.retire,
        )
    }
}

/**
 * One size: a block as tall as the habit at that size, its name there, and whether it is the one
 * set.
 */
@Composable
private fun RowScope.SizeTile(
    index: Int,
    label: String,
    name: String,
    on: Boolean,
    easier: Boolean,
    pick: () -> Unit,
) {
    val colors = LocalPageColors.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.weight(1f)
            .clip(shape)
            .background(if (on) colors.accentDim else colors.panel2)
            .border(
                1.dp,
                if (on) colors.accent else if (easier) colors.gold else colors.line,
                shape,
            )
            .selectable(on, role = Role.RadioButton, onClick = pick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val fill = if (on) colors.accent else colors.muted2
        Canvas(Modifier.size(width = 44.dp, height = 40.dp).clearAndSetSemantics {}) {
            val h = size.height * TowerSpec.HEIGHTS[index] / TowerSpec.HEIGHTS[2]
            drawRoundRect(
                fill,
                Offset(0f, size.height - h),
                Size(size.width, h),
                CornerRadius(4.dp.toPx()),
            )
        }
        Text(
            label,
            Modifier.padding(top = 6.dp),
            color = colors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            name,
            color = colors.muted,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** A habit being broken: the floors still standing of those it started with. */
@Composable
private fun Floors(b: HabitBlock) {
    val colors = LocalPageColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "${b.left} of ${b.floors} floors still standing",
            color = colors.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Canvas(Modifier.fillMaxWidth().height(10.dp).clearAndSetSemantics {}) {
            val gap = 2.dp.toPx()
            val shown = b.floors.coerceIn(1, 60)
            val w = (size.width - gap * (shown - 1)) / shown
            for (i in 0 until shown) drawRoundRect(
                if (i < b.left * shown / b.floors.coerceAtLeast(1)) colors.bad else colors.line,
                Offset(i * (w + gap), 0f),
                Size(w, size.height),
                CornerRadius(2.dp.toPx()),
            )
        }
    }
}

/** A dial that opens a sheet of its own: what it is, and what it is set to. */
@Composable
private fun RowScope.Tile(
    label: String,
    value: String,
    set: Boolean,
    open: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = LocalPageColors.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.weight(1f)
            .clip(shape)
            .background(colors.panel2)
            .border(1.dp, colors.line, shape)
            .then(
                if (enabled) Modifier.tapTarget(Role.Button, "Change", open)
                else Modifier.heightIn(min = 48.dp)
            )
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Text(
            label,
            color = colors.muted2,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Text(
            value,
            color = if (set) colors.text else colors.muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The form kit in the page's palette, so a field looks the same in the dark and the light theme.
 */
private fun formLook(c: PageColors) =
    FormKitLook(
        ink = c.text,
        ink2 = c.muted,
        ink3 = c.muted2,
        well = c.field,
        edge = c.line,
        segEdge = c.line,
        focus = c.accent,
        focusRing = c.accentDim,
        error = c.bad,
        cursor = c.accent,
    )

private val FROM_DIALS = setOf(SheetKind.NAMES, SheetKind.PAIR, SheetKind.RESTACK, SheetKind.RETIRE)
private val Scrim = Color.Black.copy(alpha = .5f)
private val Pill = RoundedCornerShape(50)
