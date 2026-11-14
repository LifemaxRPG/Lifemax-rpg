package com.lifemax.rpg.feature.habits.presentation.scene

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import com.lifemax.rpg.feature.habits.domain.Camera
import com.lifemax.rpg.feature.habits.domain.CityLayout
import com.lifemax.rpg.feature.habits.domain.HabitStack
import com.lifemax.rpg.feature.habits.domain.HabitsProjection
import com.lifemax.rpg.feature.habits.domain.Hit
import com.lifemax.rpg.feature.habits.domain.HitKind
import com.lifemax.rpg.feature.habits.domain.PAGE_SHARE
import com.lifemax.rpg.feature.habits.domain.SceneWindow
import com.lifemax.rpg.feature.habits.domain.TOP_FADE
import com.lifemax.rpg.feature.habits.domain.TowerLayout
import com.lifemax.rpg.feature.habits.domain.TowerSpec
import com.lifemax.rpg.feature.habits.domain.cityLayout
import com.lifemax.rpg.feature.habits.domain.focusCamera
import com.lifemax.rpg.feature.habits.domain.hit
import com.lifemax.rpg.feature.habits.domain.lerp
import com.lifemax.rpg.feature.habits.domain.orbSpot
import com.lifemax.rpg.feature.habits.domain.pagerPose
import com.lifemax.rpg.feature.habits.domain.rubberBand
import com.lifemax.rpg.feature.habits.domain.settlePage
import com.lifemax.rpg.feature.habits.domain.skyPhase
import com.lifemax.rpg.feature.habits.domain.towerAt
import com.lifemax.rpg.feature.habits.domain.towerLayout
import com.lifemax.rpg.feature.habits.presentation.HabitsCopy
import com.lifemax.rpg.feature.habits.presentation.HabitsUi
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** What the scene tells the screen. */
internal class StageActions(
    /** The owner's swipe brought this stack in front. */
    val swiped: (stackId: String) -> Unit = {},
    /** A tap on a tower beside the one in front, or on one of the city. */
    val chosen: (stackId: String) -> Unit = {},
    /** A tap on something of the tower in front. */
    val tapped: (stackId: String, hit: Hit) -> Unit = { _, _ -> },
    /** Play [id] is over. */
    val played: (id: Long) -> Unit = {},
    /** A moment to feel: [Feel]. */
    val feel: (Feel) -> Unit = {},
)

/** The moments the phone buzzes at. */
internal enum class Feel {
    /** The towers moved to another one. */
    TICK,

    /** A block landed. */
    LAND,

    /** A block changed material, or a reward was claimed. */
    GLOW,

    /** A floor came down. */
    FELL,
}

/** The running parts of a play: a block landing, or a floor coming down. */
private class PlayMotion {
    val fall = Animatable(1f)
    val squash = Animatable(0f)
    val burst = Animatable(1f)
    val sweep = Animatable(1f)
    val fell = Animatable(1f)
    var landing by mutableStateOf<String?>(null)
    var felled by mutableStateOf<String?>(null)
    var swept by mutableStateOf(false)

    /** The play these parts are running, so one that ends late does not stop the next. */
    var playing = 0L

    /** Play [id] is over or was cut short: nothing is left half way (unless another has begun). */
    fun rest(id: Long) {
        if (playing != id) return
        landing = null
        felled = null
        swept = false
    }

    /** This frame's motion of the tower in front, at [seconds]. */
    fun now(seconds: Float): TowerMotion =
        if (landing == null && felled == null) TowerMotion(seconds)
        else
            TowerMotion(
                seconds,
                landing,
                fall.value,
                squash.value,
                squash.value,
                burst.value,
                sweep.value,
                swept,
                felled,
                fell.value,
            )
}

/**
 * Where a tower is drawn: its foot's pixel, the pixels a tower unit takes, how far it is from the
 * front, and how far what stands on its slab is slid down behind it (in tower units).
 */
private class Place(
    val x: Float,
    val y: Float,
    val unit: Float,
    val away: Float,
    val front: Float,
    val sink: Float,
)

/**
 * The scene's arithmetic for one frame, shared by the drawing and the taps so that a tap lands on
 * what is drawn there. [window] is the part of the view the towers may use, [position] the pager's,
 * [city] 0 (one tower in front) to 1 (the city), [pan] the front tower's, [lift] how far the whole
 * ground is raised to clear a sheet, [inset] the top bar's pixels.
 */
private class Frame(
    val size: IntSize,
    val density: Float,
    val window: SceneWindow,
    val layouts: List<TowerLayout>,
    val cameras: List<Camera>,
    val cityLayout: CityLayout,
    val focus: Int,
    val position: Float,
    val city: Float,
    val pan: Float,
    val lift: Float,
    val inset: Float,
) {
    /** The ground line: it never moves with the camera, so the foundations stay where they are. */
    val ground: Float = window.ground * density - lift

    /** Under the heading's band, where the sky's own things stay. */
    val skyTop: Float = window.windowTop * density

    /**
     * Nothing of a tower is drawn above this: the heading's band, or, as the ground rises to clear
     * a block's dials (the heading is put away then), as far up as the top bar.
     */
    val top: Float = max(min(inset, skyTop), skyTop - lift)

    fun place(i: Int): Place {
        val pose = pagerPose(i, position, size.width / density)
        val away = min(1f, abs(i - position))
        val scale = lerp(cameras[i].scale * pose.scale, cityLayout.scale, city)
        val x = size.width / 2f + lerp(pose.x, cityLayout.xs[i], city) * density
        val sink = (if (i == focus) pan else cameras[i].pan) * (1f - city) / cameras[i].scale
        return Place(x, ground, scale * density, away, lerp(pose.alpha, 1f, city), sink)
    }

    /** Whether tower [i], drawn at [at], reaches into the fade under the heading's band. */
    fun fades(i: Int, at: Place): Boolean =
        at.y - (layouts[i].height - at.sink) * at.unit < top + TOP_FADE * density
}

/**
 * The scene: the sky of the hour and the towers. One tower stands in front with its neighbours
 * peeking in at the edges; a sideways drag moves them with the finger and a release settles on a
 * neighbour with the finger's speed; an up-and-down drag slides a tower too tall for the view; a
 * tap picks what is under it. In the city view every tower stands side by side, as it stood
 * [HabitsUi.weeks] weeks ago. [top] and [bottom] are the pixels the screen's own controls take, as
 * they were measured: the towers, the sun, the moon and the clouds keep between them
 * ([SceneWindow]). [cover] is the height of a sheet over the bottom, which the selected block is
 * kept clear of; [inset] the top bar's pixels, as far up as a tower may then reach. Nothing runs
 * while the screen is not [visible] (A24) or motion is reduced.
 */
@Composable
internal fun HabitsStage(
    p: HabitsProjection,
    ui: HabitsUi,
    visible: Boolean,
    top: Float,
    bottom: Float,
    cover: Float,
    inset: Float,
    accent: Color,
    actions: StageActions,
    modifier: Modifier = Modifier,
) {
    // the view's size is known while composing, so the first frame already stands where it should
    BoxWithConstraints(modifier.fillMaxSize()) {
        StageCanvas(
            IntSize(constraints.maxWidth, constraints.maxHeight),
            p,
            ui,
            visible,
            top,
            bottom,
            cover,
            inset,
            accent,
            actions,
        )
    }
}

@Composable
private fun StageCanvas(
    size: IntSize,
    p: HabitsProjection,
    ui: HabitsUi,
    visible: Boolean,
    top: Float,
    bottom: Float,
    cover: Float,
    inset: Float,
    accent: Color,
    actions: StageActions,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 96)
    val text = remember(measurer, density.fontScale) { SceneText(measurer, density.fontScale) }
    val painter = remember(text) { TowerPainter(text) }
    val backdrop = remember { Backdrop() }
    val layer = remember { Paint() }
    val scope = rememberCoroutineScope()
    val still = p.reducedMotion
    val live = !ui.city || ui.weeks == 0

    val stacks = remember(p, ui.city, ui.weeks) { if (ui.city) p.towersAt(ui.weeks) else p.stacks }
    val focus = stacks.indexOfFirst { it.id == ui.focus }.coerceAtLeast(0)
    val layouts =
        remember(stacks, focus, ui.city, live) {
            stacks.mapIndexed { i, s -> towerLayout(s, live, ghost = !ui.city && i == focus) }
        }
    val window =
        remember(size, top, bottom, density.density) {
            SceneWindow(
                size.height / density.density,
                top / density.density,
                bottom / density.density,
            )
        }
    val avail = window.avail
    val cameras =
        remember(layouts, stacks, avail, ui.selected) {
            layouts.mapIndexed { i, l ->
                val aim =
                    ui.selected?.let(l::span)
                        ?: stacks[i].next.takeIf { it.isNotEmpty() }?.let(l::span)
                focusCamera(l.height, avail, aim)
            }
        }
    val cityPlan =
        remember(layouts, size, avail) {
            cityLayout(layouts.map { it.height }, size.width / density.density, avail)
        }

    // the pager: where the towers are, in towers
    val pager = remember { Animatable(focus.toFloat()) }
    LaunchedEffect(ui.jump, focus, stacks.size) {
        // the focus moved by something other than the finger (a tap, the button, a new foundation)
        if (pager.targetValue != focus.toFloat()) {
            if (still) pager.snapTo(focus.toFloat())
            else pager.animateTo(focus.toFloat(), spring(dampingRatio = .86f, stiffness = 320f))
        }
    }
    val cityT = remember { Animatable(if (ui.city) 1f else 0f) }
    LaunchedEffect(ui.city) {
        val to = if (ui.city) 1f else 0f
        if (still) cityT.snapTo(to)
        else cityT.animateTo(to, spring(dampingRatio = .88f, stiffness = 260f))
    }

    // the front tower's pan: the camera's own aim, until the owner drags it
    val front = cameras.getOrNull(focus)
    val pan = remember(ui.focus, size) { Animatable(front?.pan ?: 0f) }
    var dragged by remember { mutableStateOf(false) }
    // another tower, another block picked, a block laid: the camera aims again
    LaunchedEffect(ui.focus, ui.selected, stacks.getOrNull(focus)?.next) { dragged = false }
    SideEffect { pan.updateBounds(0f, front?.maxPan ?: 0f) }
    LaunchedEffect(pan, front?.pan, front?.maxPan, dragged) {
        val aim = front?.pan ?: 0f
        if (!dragged && pan.targetValue != aim) {
            if (still) pan.snapTo(aim)
            else pan.animateTo(aim, spring(dampingRatio = .9f, stiffness = 180f))
        }
    }

    // a sheet over the bottom: the ground rises until the selected block stands clear of it
    val liftTo = run {
        val piece = ui.selected?.let { id -> layouts.getOrNull(focus)?.labelled(id) }
        if (cover <= 0f || piece == null || front == null) 0f
        else {
            val foot = (window.ground - front.above(piece.bottom)) * density.density
            max(0f, foot + 18f * density.density - (size.height - cover))
        }
    }
    val lift = remember { Animatable(0f) }
    LaunchedEffect(liftTo) {
        if (still) lift.snapTo(liftTo)
        else lift.animateTo(liftTo, spring(dampingRatio = .9f, stiffness = 300f))
    }

    // time, for what lives by itself: the glow of the next block, a wobble, stars
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(visible, still) {
        if (visible && !still) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { seconds = (it - start) / 1e9f + .001f }
        } else seconds = 0f
    }

    // a block landing, a floor coming down
    val motion = remember { PlayMotion() }
    val play = ui.play
    LaunchedEffect(play?.id, visible) {
        if (play == null) return@LaunchedEffect
        try {
            if (visible && !still) {
                motion.playing = play.id
                if (play.fx.laid) {
                    motion.fall.snapTo(0f)
                    motion.squash.snapTo(0f)
                    motion.burst.snapTo(0f)
                    motion.sweep.snapTo(0f)
                    motion.felled = null
                    motion.swept = play.fx.upgrade != null
                    motion.landing = play.fx.id
                    // it falls faster and faster, hits, and the tower takes the blow on a spring
                    motion.fall.animateTo(1f, tween(260, easing = FastOutLinearInEasing))
                    actions.feel(Feel.LAND)
                    coroutineScope {
                        launch {
                            motion.squash.snapTo(1f)
                            motion.squash.animateTo(
                                0f,
                                spring(dampingRatio = .36f, stiffness = 520f),
                            )
                        }
                        launch { motion.burst.animateTo(1f, tween(520, easing = LinearEasing)) }
                        if (motion.swept)
                            launch {
                                actions.feel(Feel.GLOW)
                                motion.sweep.animateTo(
                                    1f,
                                    tween(760, delayMillis = 140, easing = LinearEasing),
                                )
                            }
                    }
                } else {
                    motion.fell.snapTo(0f)
                    motion.landing = null
                    motion.felled = play.fx.id
                    actions.feel(Feel.FELL)
                    motion.fell.animateTo(1f, tween(680, easing = LinearEasing))
                }
            } else if (visible) actions.feel(if (play.fx.laid) Feel.LAND else Feel.FELL)
        } finally {
            // also when cut short (the screen hid, another block landed): nothing is left half way
            motion.rest(play.id)
            actions.played(play.id)
        }
    }

    fun frame() =
        Frame(
            size,
            density.density,
            window,
            layouts,
            cameras,
            cityPlan,
            focus,
            pager.value,
            cityT.value,
            pan.value,
            if (still) liftTo else lift.value,
            inset,
        )

    val describe =
        remember(p, ui.focus, ui.city, ui.weeks) {
            HabitsCopy.describe(p, p.stack(ui.focus), ui.city, ui.weeks)
        }
    val talkBack =
        remember(stacks, focus, ui.city) {
            if (ui.city || stacks.isEmpty()) emptyList()
            else
                buildList {
                    val here = stacks[focus]
                    stacks.getOrNull(focus + 1)?.let { s ->
                        add(
                            CustomAccessibilityAction("Next foundation: ${s.cue}") {
                                actions.chosen(s.id).let { true }
                            }
                        )
                    }
                    stacks.getOrNull(focus - 1)?.let { s ->
                        add(
                            CustomAccessibilityAction("Previous foundation: ${s.cue}") {
                                actions.chosen(s.id).let { true }
                            }
                        )
                    }
                    here.blocks.forEach { b ->
                        add(
                            CustomAccessibilityAction("Tune ${b.title}") {
                                actions.tapped(here.id, Hit(HitKind.BLOCK, b.id)).let { true }
                            }
                        )
                    }
                    add(
                        CustomAccessibilityAction("Edit foundation ${here.cue}") {
                            actions.tapped(here.id, Hit(HitKind.SLAB, here.id)).let { true }
                        }
                    )
                    add(
                        CustomAccessibilityAction("Stack a habit on ${here.cue}") {
                            actions.tapped(here.id, Hit(HitKind.GHOST, here.id)).let { true }
                        }
                    )
                }
        }

    Canvas(
        Modifier.fillMaxSize()
            .semantics {
                contentDescription = describe
                customActions = talkBack
            }
            .pointerInput(stacks, focus, ui.city, still, front?.maxPan, pan) {
                val pageWidth = this.size.width * PAGE_SHARE
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val startPage =
                        pager.targetValue.roundToInt().coerceIn(0, max(0, stacks.size - 1))
                    val startAt = pager.value
                    var total = Offset.Zero
                    var axis = 0 // 1 sideways (the towers), 2 up and down (the camera)
                    var lifted = false
                    while (true) {
                        val change =
                            awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUp()) {
                            lifted = true
                            break
                        }
                        total += change.positionChange()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (
                            axis == 0 &&
                                total.getDistance() > viewConfiguration.touchSlop &&
                                !ui.city &&
                                stacks.isNotEmpty()
                        )
                            axis = if (abs(total.x) > abs(total.y)) 1 else 2
                        if (axis == 1) {
                            change.consume()
                            val at = rubberBand(startAt - total.x / pageWidth, stacks.size)
                            scope.launch { pager.snapTo(at) }
                        } else if (axis == 2 && (front?.maxPan ?: 0f) > 0f) {
                            change.consume()
                            dragged = true
                            val to = pan.value + change.positionChange().y / density.density
                            scope.launch { pan.snapTo(to) }
                        }
                    }
                    val velocity = tracker.calculateVelocity()
                    when (axis) {
                        0 -> if (lifted) tap(frame(), stacks, ui.city, down.position, actions)
                        1 -> {
                            val speed = -velocity.x / pageWidth
                            val target =
                                settlePage(
                                    pager.value,
                                    if (lifted) speed else 0f,
                                    startPage,
                                    stacks.size,
                                )
                            if (target != focus) {
                                actions.feel(Feel.TICK)
                                actions.swiped(stacks[target].id)
                            }
                            scope.launch {
                                if (still) pager.snapTo(target.toFloat())
                                else
                                    pager.animateTo(
                                        target.toFloat(),
                                        spring(dampingRatio = .82f, stiffness = 380f),
                                        initialVelocity = speed,
                                    )
                            }
                        }
                        else ->
                            if (lifted && !still && (front?.maxPan ?: 0f) > 0f)
                                scope.launch {
                                    pan.animateDecay(
                                        velocity.y / density.density,
                                        exponentialDecay(frictionMultiplier = 2.2f),
                                    )
                                }
                    }
                }
            }
    ) {
        if (this.size.width <= 0f) return@Canvas
        val f = frame()
        val phase = skyPhase(p.hour)
        val sky = skyColors(phase)
        val horizon = f.ground - HORIZON_DP * density.density
        // the sun or the moon stands beside the tower in front, never behind it
        val column =
            (TowerSpec.W / 2f + TowerSpec.SLAB_OVER + TowerSpec.DX) * (front?.scale ?: 1.15f) + 14f
        val orb =
            orbSpot(
                p.hour,
                this.size.width / density.density,
                window.windowTop,
                horizon / density.density,
                column,
                f.city,
            )
        with(backdrop) {
            backdrop(
                phase,
                sky,
                horizon,
                f.skyTop,
                Offset(orb.first * density.density, orb.second * density.density),
                f.position * (1f - f.city),
                seconds,
            )
        }
        if (layouts.isEmpty()) {
            // nothing built yet: the empty lot where the first foundation will go
            withTransform({
                translate(this@Canvas.size.width / 2f, f.ground)
                scale(1.15f * density.density, 1.15f * density.density, Offset.Zero)
            }) {
                with(painter) { lot(sky, TowerSpec.W + 2 * TowerSpec.SLAB_OVER) }
            }
            return@Canvas
        }
        val tower = motion.now(seconds)
        // the furthest first, so the tower in front is drawn over its neighbours
        for (i in layouts.indices.sortedByDescending { abs(it - f.position) }) {
            val at = f.place(i)
            if (at.front <= .01f) continue
            val left = at.x - (layouts[i].pieces.first().width / 2f + 120f) * at.unit
            val right = at.x + (layouts[i].pieces.first().width / 2f + 140f) * at.unit
            if (right < 0f || left > this.size.width) continue
            // distance: a tower that is not in front is drawn fainter, as one picture; so is one
            // too tall for its window, which fades out under the heading instead of running into it
            val faint = at.front < .99f
            val fades = f.fades(i, at)
            if (faint || fades) {
                layer.alpha = if (faint) at.front else 1f
                drawIntoCanvas { it.saveLayer(Rect(left, 0f, right, this.size.height), layer) }
            }
            withTransform({
                translate(at.x, at.y)
                scale(at.unit, at.unit, Offset.Zero)
            }) {
                with(painter) {
                    lot(sky, layouts[i].pieces.first().width)
                    tower(
                        layouts[i],
                        stacks[i],
                        if (i == focus || f.city > 0f) tower else TowerMotion(seconds),
                        ui.selected.takeIf { i == focus && f.city == 0f },
                        accent,
                        live,
                        detailed = at.unit / density.density > .45f,
                        sink = at.sink,
                        cue = 1f - f.city,
                    )
                }
            }
            if (fades) {
                // rub the layer out above the window, and ever less over the fade under it
                val end = f.top + TOP_FADE * density.density
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Black,
                        f.top / end to Color.Black,
                        1f to Color.Transparent,
                        startY = 0f,
                        endY = end,
                    ),
                    Offset(left, 0f),
                    Size(right - left, end),
                    blendMode = BlendMode.DstOut,
                )
            }
            if (faint || fades) drawIntoCanvas { it.restore() }
            if (f.city > .02f) cityName(text, stacks[i].cue, at.x, at.y, f, sky.ink)
        }
    }
}

/** A tower's name under it in the city view, fading in with the city. */
private fun DrawScope.cityName(
    text: SceneText,
    cue: String,
    x: Float,
    y: Float,
    f: Frame,
    ink: Color,
) {
    val d = f.density
    val words =
        text.layout(cue, 11f, FontWeight.SemiBold, (f.cityLayout.slot - 6f).coerceAtLeast(30f))
    withTransform({
        translate(x, y + 14f * d)
        scale(d, d, Offset.Zero)
    }) {
        drawText(words, ink, Offset(-words.size.width / 2f, 0f), alpha = f.city)
    }
}

/**
 * A tap at [at]: a tower of the city, a tower beside the one in front, or a piece of the one in
 * front.
 */
private fun tap(
    f: Frame,
    stacks: List<HabitStack>,
    city: Boolean,
    at: Offset,
    actions: StageActions,
) {
    if (stacks.isEmpty()) return
    if (city) {
        f.cityLayout.towerAt((at.x - f.size.width / 2f) / f.density)?.let {
            actions.chosen(stacks[it].id)
        }
        return
    }
    // nothing of a tower shows in the heading's band
    if (at.y < f.top) return
    // the tower in front first: it is drawn over its neighbours
    for (i in stacks.indices.sortedBy { abs(it - f.position) }) {
        val place = f.place(i)
        val hit =
            f.layouts[i].hit(
                (at.x - place.x) / place.unit,
                (place.y - at.y) / place.unit,
                place.sink,
            ) ?: continue
        if (i == f.focus) actions.tapped(stacks[i].id, hit) else actions.chosen(stacks[i].id)
        return
    }
}

/** How far above the ground line the horizon is drawn. */
private const val HORIZON_DP = 30f
