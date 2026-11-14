package com.lifemax.rpg.feature.habits.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.app.shell.rememberImeUp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.PageTheme
import com.lifemax.rpg.core.designsystem.components.PageToast
import com.lifemax.rpg.core.designsystem.components.playToast
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.core.designsystem.components.toggleTarget
import com.lifemax.rpg.core.designsystem.feedback.LocalPageBuzz
import com.lifemax.rpg.core.designsystem.feedback.play
import com.lifemax.rpg.feature.habits.domain.HabitKind
import com.lifemax.rpg.feature.habits.domain.HabitStack
import com.lifemax.rpg.feature.habits.domain.HabitsProjection
import com.lifemax.rpg.feature.habits.domain.Hit
import com.lifemax.rpg.feature.habits.domain.HitKind
import com.lifemax.rpg.feature.habits.domain.PrimaryAction
import com.lifemax.rpg.feature.habits.domain.Suggestion
import com.lifemax.rpg.feature.habits.domain.lapseFraction
import com.lifemax.rpg.feature.habits.domain.lapseWeeks
import com.lifemax.rpg.feature.habits.domain.primaryAction
import com.lifemax.rpg.feature.habits.domain.skyPhase
import com.lifemax.rpg.feature.habits.presentation.scene.Feel
import com.lifemax.rpg.feature.habits.presentation.scene.HabitsStage
import com.lifemax.rpg.feature.habits.presentation.scene.SkyColors
import com.lifemax.rpg.feature.habits.presentation.scene.StageActions
import com.lifemax.rpg.feature.habits.presentation.scene.skyColors

@Composable
fun HabitsRoute(vm: HabitsViewModel, visible: Boolean, topInsetDp: Float, bottomInsetDp: Float) {
    val imeUp = rememberImeUp()
    val projection by vm.projection.collectAsState()
    val ui by vm.ui.collectAsState()
    val p = projection ?: return
    val actions = remember(vm) { HabitsActions(vm) }
    // Back: out of a sheet (to the dials it came from, or away), then out of the city
    BackHandler(enabled = visible && ui.sheet != null) { vm.back() }
    BackHandler(enabled = visible && ui.sheet == null && ui.city) { vm.city(false) }
    HabitsScreen(p, ui, actions, topInsetDp, bottomInsetDp, visible, imeUp)
}

/** Everything the screen can ask for, so the screen itself holds no state. */
class HabitsActions(
    val focus: (stackId: String, byFinger: Boolean) -> Unit = { _, _ -> },
    val select: (blockId: String) -> Unit = {},
    val editStack: (stackId: String) -> Unit = {},
    val newHabit: (stackId: String) -> Unit = {},
    val reward: (stackId: String) -> Unit = {},
    val primary: () -> Unit = {},
    val rough: () -> Unit = {},
    val city: (Boolean) -> Unit = {},
    val lapse: (weeks: Int) -> Unit = {},
    val who: () -> Unit = {},
    val newStack: () -> Unit = {},
    val type: (index: Int, text: String) -> Unit = { _, _ -> },
    val kind: (fade: Boolean) -> Unit = {},
    val suggest: (Suggestion) -> Unit = {},
    val submit: () -> Unit = {},
    val danger: () -> Unit = {},
    val restackTo: (stackId: String) -> Unit = {},
    val size: (Int) -> Unit = {},
    val move: (dir: Int) -> Unit = {},
    val undo: () -> Unit = {},
    val names: () -> Unit = {},
    val pair: () -> Unit = {},
    val restack: () -> Unit = {},
    val retire: () -> Unit = {},
    val back: () -> Unit = {},
    val close: () -> Unit = {},
    val toastShown: (Long) -> Unit = {},
    val played: (Long) -> Unit = {},
) {
    constructor(
        vm: HabitsViewModel
    ) : this(
        vm::focus,
        vm::select,
        vm::editStack,
        vm::newHabit,
        vm::reward,
        vm::primary,
        vm::rough,
        vm::city,
        vm::lapse,
        vm::who,
        vm::newStack,
        vm::type,
        vm::kind,
        vm::suggest,
        vm::submit,
        vm::danger,
        vm::restackTo,
        vm::size,
        vm::move,
        vm::undo,
        vm::names,
        vm::pair,
        vm::restack,
        vm::retire,
        vm::back,
        vm::closeSheet,
        vm::toastShown,
        vm::played,
    )
}

/**
 * Habits: the scene fills the screen (the sky of the hour, the towers) and the few controls float
 * on it. Above: who the owner is becoming and today's count. Below: the rough-day switch, the one
 * big button that lays the next block, and a dot for every foundation. A tap on a block opens its
 * dials in a sheet; the city view swaps the button for the time-lapse. The heading and the controls
 * are measured first and the scene is told what they take (at any text size), so a tower stands
 * between them and never under them. While a block's dials are open the heading is put away: the
 * ground rises to keep that block over the sheet, and the tower may reach as far up as the top bar.
 */
@Composable
fun HabitsScreen(
    p: HabitsProjection,
    ui: HabitsUi,
    actions: HabitsActions = HabitsActions(),
    topInsetDp: Float = 64f,
    barDp: Float = 72f,
    visible: Boolean = true,
    imeUp: Boolean = false,
) {
    // The activity already resizes this screen to what is above the keyboard and the shared bottom
    // bar hides while it is up (NAV-5), so the bar's room is dropped; nothing here reads the IME
    // inset again (the host is already shorter by it)
    val bottomInsetDp = barRoomDp(barDp, imeUp)
    PageTheme(remember(p.tokens, p.light) { PageColors(p.tokens, p.light) }) {
        val colors = LocalPageColors.current
        val density = LocalDensity.current.density
        val sky = remember(p.hour) { skyColors(skyPhase(p.hour)) }
        val buzz = LocalPageBuzz.current
        var sheetHeight by remember { mutableIntStateOf(0) }
        val tuning = ui.sheet?.kind == SheetKind.TUNE
        val heading by
            animateFloatAsState(
                if (tuning) 0f else 1f,
                if (p.reducedMotion) snap() else tween(160),
                label = "heading",
            )
        val stage =
            remember(actions, buzz, p.haptics) {
                StageActions(
                    swiped = { actions.focus(it, true) },
                    chosen = { actions.focus(it, false) },
                    tapped = { stack, hit -> tapped(actions, stack, hit) },
                    played = actions.played,
                    feel = { feel ->
                        when (feel) {
                            Feel.TICK -> buzz?.play(p.haptics, 6)
                            Feel.LAND -> buzz?.play(p.haptics, 16)
                            Feel.GLOW -> buzz?.play(p.haptics, 18, 30, 18)
                            Feel.FELL -> buzz?.play(p.haptics, 10, 40, 22)
                        }
                    },
                )
            }
        // the sky's own darkest colour behind everything: the screen covers the page completely
        Box(Modifier.fillMaxSize().background(sky.sky[0])) {
            SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
                val loose = constraints.copy(minWidth = 0, minHeight = 0)
                val head =
                    subcompose(Slot.HEADING) {
                            Column(Modifier.padding(top = topInsetDp.dp, bottom = HUD_GAP_DP.dp)) {
                                Hud(p, ui, sky, actions)
                                if (p.problem.isNotEmpty()) Problem(p.problem)
                            }
                        }
                        .first()
                        .measure(loose)
                val controls =
                    subcompose(Slot.CONTROLS) {
                            Box(Modifier.padding(bottom = bottomInsetDp.dp).widthIn(max = 560.dp)) {
                                if (ui.city) LapseCard(p, ui, actions)
                                else Controls(p, ui, sky, actions)
                            }
                        }
                        .first()
                        .measure(loose)
                val scene =
                    subcompose(Slot.SCENE) {
                            HabitsStage(
                                p,
                                ui,
                                visible,
                                top = head.height.toFloat(),
                                bottom = controls.height.toFloat(),
                                cover = if (tuning) sheetHeight + bottomInsetDp * density else 0f,
                                inset = topInsetDp * density,
                                accent = colors.accent,
                                actions = stage,
                            )
                        }
                        .first()
                        .measure(constraints)
                layout(constraints.maxWidth, constraints.maxHeight) {
                    scene.place(0, 0)
                    // put away, it is not there to be tapped either
                    if (heading > .01f) head.placeWithLayer(0, 0) { alpha = heading }
                    controls.place(
                        (constraints.maxWidth - controls.width) / 2,
                        constraints.maxHeight - controls.height,
                    )
                }
            }
            Toast(p, ui.toast, bottomInsetDp, actions.toastShown)
            ui.sheet?.let {
                HabitsSheets(p, it, bottomInsetDp.dp, topInsetDp.dp, actions) { h ->
                    sheetHeight = h
                }
            }
        }
    }
}

/** The room kept for the shared bottom bar: none while the keyboard is up and the bar is away. */
internal fun barRoomDp(bottomInsetDp: Float, imeUp: Boolean): Float =
    if (imeUp) 0f else bottomInsetDp

private fun tapped(actions: HabitsActions, stackId: String, hit: Hit) =
    when (hit.kind) {
        HitKind.BLOCK,
        HitKind.TREE -> actions.select(hit.id)
        HitKind.SLAB -> actions.editStack(stackId)
        HitKind.GHOST -> actions.newHabit(stackId)
        HitKind.REWARD -> actions.reward(stackId)
    }

/** Who the owner is becoming, today's count, and the way to the city. */
@Composable
private fun Hud(
    p: HabitsProjection,
    ui: HabitsUi,
    sky: SkyColors,
    actions: HabitsActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val who = p.who.ifEmpty { p.whoHint }
        Column(
            Modifier.weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .tapTarget(Role.Button, "Say who you are becoming", actions.who)
                .padding(vertical = 4.dp)
                .semantics(mergeDescendants = true) {}
        ) {
            Text(
                HabitsCopy.BECOMING,
                color = sky.inkSoft,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            Text(
                who.ifEmpty { HabitsCopy.WHO_UNSAID },
                color = sky.ink,
                fontSize = 20.sp,
                fontWeight = FontWeight(750),
                lineHeight = 24.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (p.due > 0)
            Row(
                Modifier.clip(PillShape)
                    .background(Glass)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    p.built.toString(),
                    color = sky.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 20.sp,
                )
                Text(
                    " / ${p.due} ${HabitsCopy.TODAY}",
                    color = sky.inkSoft,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        if (p.stacks.isNotEmpty())
            Text(
                if (ui.city) HabitsCopy.TOWER else HabitsCopy.CITY,
                Modifier.clip(PillShape)
                    .background(Glass)
                    .tapTarget(
                        Role.Button,
                        if (ui.city) HabitsCopy.TOWER_LABEL else HabitsCopy.CITY_LABEL,
                    ) {
                        actions.city(!ui.city)
                    }
                    .padding(horizontal = 14.dp),
                color = sky.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
    }
}

/** Something is wrong with the habits file: said plainly, and it stays until it is put right. */
@Composable
private fun Problem(text: String) {
    val colors = LocalPageColors.current
    Text(
        text,
        Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.panel)
            .border(1.dp, colors.bad, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = colors.text,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    )
}

/** Under the scene: the rough-day switch, the big button, the foundations' dots. */
@Composable
private fun Controls(p: HabitsProjection, ui: HabitsUi, sky: SkyColors, actions: HabitsActions) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (p.stacks.isEmpty())
            Text(
                HabitsCopy.EMPTY_HINT,
                color = sky.inkSoft,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        else if (p.stacks.any { s -> s.blocks.any { it.kind == HabitKind.BUILD } })
            RoughSwitch(p.rough, sky, actions.rough)
        PrimaryButton(primaryAction(p.stacks, ui.focus), ui, sky, actions.primary)
        if (p.stacks.isNotEmpty()) Dots(p.stacks, ui.focus, sky, actions)
    }
}

@Composable
private fun RoughSwitch(on: Boolean, sky: SkyColors, toggle: () -> Unit) {
    val colors = LocalPageColors.current
    Row(
        Modifier.clip(PillShape)
            .background(if (on) colors.accent.copy(alpha = .28f) else Glass)
            .border(1.dp, if (on) colors.accent else Color.Transparent, PillShape)
            .toggleTarget(on, reserve = false) { toggle() }
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (on) HabitsCopy.ROUGH_ON else HabitsCopy.ROUGH_OFF,
            color = sky.ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (on) HabitsCopy.ROUGH_ON_HINT else HabitsCopy.ROUGH_OFF_HINT,
            color = sky.inkSoft,
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The one big button: what it does is on it. It lays the next block in the accent, knocks a floor
 * off in green, claims a reward in gold, and rests, dim, once every block is laid. A press pushes
 * it in a little.
 */
@Composable
private fun PrimaryButton(
    action: PrimaryAction,
    ui: HabitsUi,
    sky: SkyColors,
    onClick: () -> Unit,
) {
    val colors = LocalPageColors.current
    val (small, big) = HabitsCopy.primary(action, ui.combo)
    val rest = action == PrimaryAction.Complete
    val quiet = rest || action is PrimaryAction.GoTo
    val fill =
        when (action) {
            is PrimaryAction.Knock -> Brush.horizontalGradient(listOf(colors.good, colors.good))
            is PrimaryAction.Claim -> Brush.horizontalGradient(listOf(colors.gold, colors.gold))
            is PrimaryAction.GoTo,
            PrimaryAction.Complete -> Brush.horizontalGradient(listOf(Glass, Glass))
            else -> Brush.horizontalGradient(listOf(colors.accent, colors.accentStrong))
        }
    val ink = if (quiet) sky.ink else colors.onAccent
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            if (pressed && !rest) .97f else 1f,
            spring(dampingRatio = .6f, stiffness = 700f),
            label = "press",
        )
    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(ButtonShape)
            .background(fill)
            .clickable(
                source,
                LocalIndication.current,
                enabled = !rest,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = 68.dp)
            .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                small,
                color = ink.copy(alpha = .78f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                big,
                color = ink,
                fontSize = 18.sp,
                fontWeight = FontWeight(750),
                lineHeight = 23.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!rest)
            BlockGlyph(
                ink,
                action,
                Modifier.padding(start = 12.dp).size(34.dp).clearAndSetSemantics {},
            )
    }
}

/**
 * The button's picture: a block coming down on a stack, a floor going, a star, an arrow, a plus.
 */
@Composable
private fun BlockGlyph(ink: Color, action: PrimaryAction, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val u = w / 28f
        fun prism(y: Float, alphaTop: Float) {
            val top =
                Path().apply {
                    moveTo(14 * u, y)
                    lineTo(25 * u, y + 5.5f * u)
                    lineTo(14 * u, y + 11 * u)
                    lineTo(3 * u, y + 5.5f * u)
                    close()
                }
            drawPath(top, ink, alpha = alphaTop)
        }
        when (action) {
            is PrimaryAction.Lay -> {
                prism(1 * u, .95f)
                prism(9 * u, .55f)
                prism(15 * u, .35f)
            }
            is PrimaryAction.Knock -> {
                prism(13 * u, .5f)
                drawLine(ink, Offset(14 * u, 1 * u), Offset(14 * u, 11 * u), 2.4f * u)
                drawLine(ink, Offset(9.5f * u, 7 * u), Offset(14 * u, 11.5f * u), 2.4f * u)
                drawLine(ink, Offset(18.5f * u, 7 * u), Offset(14 * u, 11.5f * u), 2.4f * u)
            }
            is PrimaryAction.Claim -> {
                val star =
                    Path().apply {
                        moveTo(14 * u, 3 * u)
                        lineTo(17 * u, 10.5f * u)
                        lineTo(25 * u, 11 * u)
                        lineTo(19 * u, 16 * u)
                        lineTo(21 * u, 24 * u)
                        lineTo(14 * u, 19.5f * u)
                        lineTo(7 * u, 24 * u)
                        lineTo(9 * u, 16 * u)
                        lineTo(3 * u, 11 * u)
                        lineTo(11 * u, 10.5f * u)
                        close()
                    }
                drawPath(star, ink)
            }
            is PrimaryAction.GoTo -> {
                drawLine(ink, Offset(5 * u, 14 * u), Offset(22 * u, 14 * u), 2.4f * u)
                drawLine(ink, Offset(15 * u, 7 * u), Offset(22.5f * u, 14 * u), 2.4f * u)
                drawLine(ink, Offset(15 * u, 21 * u), Offset(22.5f * u, 14 * u), 2.4f * u)
            }
            else -> {
                drawRect(ink, Offset(12.6f * u, 5 * u), Size(2.8f * u, 18 * u))
                drawRect(ink, Offset(5 * u, 12.6f * u), Size(18 * u, 2.8f * u))
            }
        }
    }
}

/** A dot for every foundation (a tap brings it in front), and a plus that lays a new one. */
@Composable
private fun Dots(stacks: List<HabitStack>, focus: String?, sky: SkyColors, actions: HabitsActions) {
    Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
        stacks.forEach { s ->
            val on = s.id == focus
            Box(
                Modifier.selectable(on, role = Role.Tab) { actions.focus(s.id, false) }
                    .semantics { contentDescription = s.cue }
                    .size(width = if (stacks.size > 8) 18.dp else 26.dp, height = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(if (on) 9.dp else 6.dp)
                        .background(
                            if (on) sky.ink else sky.inkSoft.copy(alpha = .55f),
                            CircleShape,
                        )
                )
            }
        }
        Text(
            "+",
            Modifier.padding(start = 4.dp)
                .clip(CircleShape)
                .tapTarget(Role.Button, "Lay a new foundation", actions.newStack)
                .padding(horizontal = 14.dp)
                .semantics { contentDescription = "New foundation" },
            color = sky.ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The city's time-lapse: drag back through the weeks and watch the skyline rise. */
@Composable
private fun LapseCard(p: HabitsProjection, ui: HabitsUi, actions: HabitsActions) {
    val colors = LocalPageColors.current
    val max = p.rules.weeks
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.panel.copy(alpha = .92f))
            .border(1.dp, colors.line, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                HabitsCopy.LAPSE,
                color = colors.muted,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                HabitsCopy.weeksAgo(ui.weeks, max),
                color = colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = lapseFraction(ui.weeks, max),
            onValueChange = { actions.lapse(lapseWeeks(it, max)) },
            steps = (max - 1).coerceAtLeast(0),
            colors =
                SliderDefaults.colors(
                    thumbColor = colors.accent,
                    activeTrackColor = colors.accent,
                    inactiveTrackColor = colors.line,
                    activeTickColor = colors.onAccent.copy(alpha = .5f),
                    inactiveTickColor = colors.muted2,
                ),
            modifier =
                Modifier.fillMaxWidth().semantics {
                    stateDescription = HabitsCopy.weeksAgo(ui.weeks, max)
                },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$max weeks ago",
                color = colors.muted2,
                fontSize = 11.5.sp,
                modifier = Modifier.weight(1f),
            )
            Text(HabitsCopy.TODAY, color = colors.muted2, fontSize = 11.5.sp)
        }
        Text(
            HabitsCopy.NEW_FOUNDATION,
            Modifier.padding(top = 4.dp)
                .clip(PillShape)
                .tapTarget(Role.Button, onClick = actions.newStack)
                .padding(horizontal = 4.dp),
            color = colors.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * The page's words, shown once over the controls: a save that failed, a block that turned to stone.
 */
@Composable
private fun BoxScope.Toast(
    p: HabitsProjection,
    toast: HabitsToast?,
    bottomInsetDp: Float,
    shown: (Long) -> Unit,
) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(toast?.id) {
        val id = toast?.id ?: return@LaunchedEffect
        rise.snapTo(0f)
        rise.playToast()
        shown(id)
    }
    if (toast != null)
        PageToast(
            toast.text,
            p.tokens,
            p.light,
            Modifier.align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = (bottomInsetDp + CONTROLS_DP + 6f).dp)
                .widthIn(max = 520.dp),
            shown = { rise.value },
        )
}

/** The screen's parts, measured in this order: the scene last, with what the others take. */
private enum class Slot {
    HEADING,
    CONTROLS,
    SCENE,
}

/** Air between the heading and the top of the towers' window. */
private const val HUD_GAP_DP = 6f

/** What the controls take above the bottom bar at the usual text size: a toast rises over them. */
private const val CONTROLS_DP = 172f

/** Dark glass over the sky, under words that must read at any hour. */
private val Glass = Color(0x47000A1E)
private val PillShape = RoundedCornerShape(50)
private val ButtonShape = RoundedCornerShape(24.dp)
