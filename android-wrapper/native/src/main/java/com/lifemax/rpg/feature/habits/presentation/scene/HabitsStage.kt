package com.lifemax.rpg.feature.habits.presentation.scene

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import com.lifemax.rpg.feature.habits.domain.BuildingLayout
import com.lifemax.rpg.feature.habits.domain.BuildingSpec as B
import com.lifemax.rpg.feature.habits.domain.Camera
import com.lifemax.rpg.feature.habits.domain.HabitStack
import com.lifemax.rpg.feature.habits.domain.HabitsProjection
import com.lifemax.rpg.feature.habits.domain.Hit
import com.lifemax.rpg.feature.habits.domain.HitKind
import com.lifemax.rpg.feature.habits.domain.SceneWindow
import com.lifemax.rpg.feature.habits.domain.StreetLayout
import com.lifemax.rpg.feature.habits.domain.StreetView
import com.lifemax.rpg.feature.habits.domain.TOP_FADE
import com.lifemax.rpg.feature.habits.domain.buildingAt
import com.lifemax.rpg.feature.habits.domain.buildingLayout
import com.lifemax.rpg.feature.habits.domain.centred
import com.lifemax.rpg.feature.habits.domain.focusCamera
import com.lifemax.rpg.feature.habits.domain.hit
import com.lifemax.rpg.feature.habits.domain.lerp
import com.lifemax.rpg.feature.habits.domain.orbSpot
import com.lifemax.rpg.feature.habits.domain.position
import com.lifemax.rpg.feature.habits.domain.rubberBand
import com.lifemax.rpg.feature.habits.domain.settle
import com.lifemax.rpg.feature.habits.domain.streetLayout
import com.lifemax.rpg.feature.habits.domain.streetView
import com.lifemax.rpg.feature.habits.domain.towards
import com.lifemax.rpg.feature.habits.presentation.HabitsCopy
import com.lifemax.rpg.feature.habits.presentation.HabitsUi
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** What the scene tells the screen. */
internal class StageActions(
    /** The owner's scroll settled on this building. */
    val swiped: (stackId: String) -> Unit = {},
    /** A tap on a building beside the one in front, or on one of the zoomed-out street. */
    val chosen: (stackId: String) -> Unit = {},
    /** A tap on something of the building in front. */
    val tapped: (stackId: String, hit: Hit) -> Unit = { _, _ -> },
    /** A pinch: out to the whole street (true), or back in (false). */
    val zoomed: (out: Boolean) -> Unit = {},
    /** Play [id] is over. */
    val played: (id: Long) -> Unit = {},
    /** A moment to feel: [Feel]. */
    val feel: (Feel) -> Unit = {},
)

/** The moments the phone buzzes at. */
internal enum class Feel {
    /** The street moved to another building. */
    TICK,

    /** A floor's lights came on. */
    LAND,

    /** A floor changed material, or a reward was claimed. */
    GLOW,

    /** A storey came down. */
    FELL,
}

/**
 * The running parts of a play: a floor being finished, a storey coming down, a floor dropping in.
 */
private class PlayMotion {
    val lights = Animatable(1f)
    val squash = Animatable(0f)
    val burst = Animatable(1f)
    val sweep = Animatable(1f)
    val fell = Animatable(1f)
    val drop = Animatable(1f)
    var landing by mutableStateOf<String?>(null)
    var felled by mutableStateOf<String?>(null)
    var adding by mutableStateOf<String?>(null)
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

    /** This frame's motion of the building in front, written into [into]. */
    fun now(seconds: Float, into: BuildingMotion): BuildingMotion {
        into.seconds = seconds
        into.landing = landing
        into.felled = felled
        into.adding = adding
        into.lights = lights.value
        into.squash = squash.value
        into.burst = burst.value
        into.sweep = sweep.value
        into.swept = swept
        into.fell = fell.value
        into.drop = drop.value
        return into
    }
}

/**
 * The scene's arithmetic for one frame, shared by the drawing and the taps so that a tap lands on
 * what is drawn there. [scroll] is where along the street the view stands (street units), [zoom] 0
 * (the building in front) to 1 (the whole street), [pan] the front building's, [lift] how far the
 * whole street is raised to clear a sheet, [inset] the top bar's pixels.
 */
private class Frame(
    val size: IntSize,
    val density: Float,
    val window: SceneWindow,
    val street: StreetLayout,
    val cameras: List<Camera>,
    val view: StreetView,
    val focus: Int,
    val scroll: Float,
    val zoom: Float,
    pan: Float,
    val lift: Float,
    val inset: Float,
) {
    /** The camera between the two buildings the view stands between. */
    val camera: Camera =
        if (cameras.isEmpty()) Camera(1.15f, 0f, 0f)
        else {
            val at = street.position(scroll)
            val i = floor(at).toInt().coerceIn(0, cameras.size - 1)
            val j = min(i + 1, cameras.size - 1)
            fun cam(k: Int) = if (k == focus) cameras[k].copy(pan = pan) else cameras[k]
            cam(i).towards(cam(j), (at - i).coerceIn(0f, 1f))
        }

    val scale: Float = lerp(camera.scale, view.scale, zoom)
    val unit: Float = scale * density

    /** The street's x in the middle of the view. */
    val middle: Float = lerp(scroll, view.middle, zoom)

    /** The pavement line, lowered by the camera's pan and raised to clear a sheet. */
    val ground: Float = window.ground * density - lift + camera.pan * (1f - zoom) * density

    /** Under the heading's band, where the sky's own things stay. */
    val skyTop: Float = window.windowTop * density

    /**
     * Nothing of a building is drawn above this: the heading's band, or, as the street rises to
     * clear a floor's dials (the heading is put away then), as far up as the top bar.
     */
    val top: Float = max(min(inset, skyTop), skyTop - lift)

    fun screenX(x: Float): Float = size.width / 2f + (x - middle) * unit

    fun streetX(px: Float): Float = middle + (px - size.width / 2f) / unit
}

/**
 * The scene: the living sky of the hour and one street with a building per foundation. The street
 * follows a sideways drag and, let go, coasts and settles softly on the building nearest where it
 * would stop, which is then the one in front; an up-and-down drag tilts the view up a building too
 * tall for it; a pinch (or the Street button) zooms out to the whole street, which shows
 * [HabitsUi.weeks] weeks ago. A tap picks what is under it. [top] and [bottom] are the pixels the
 * screen's own controls take, as they were measured: the buildings, the sun, the moon and the
 * clouds keep between them ([SceneWindow]). [cover] is the height of a sheet over the bottom, which
 * the selected floor is kept clear of; [inset] the top bar's pixels, as far up as a building may
 * then reach. Nothing moves while the screen is not [visible] (A24) or motion is reduced: the
 * street is a still picture then. [ambientAt] holds the street's life at that many seconds (tests).
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
    ambientAt: Float? = null,
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
            ambientAt,
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
    ambientAt: Float?,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 96)
    val text = remember(measurer, density.fontScale) { SceneText(measurer, density.fontScale) }
    val painter = remember { BuildingPainter() }
    val seed = remember(p.day) { p.day.hashCode() }
    val backdrop = remember(seed) { Backdrop(seed) }
    val sky = remember(p.hour) { skyColors(p.hour) }
    val layer = remember { Paint() }
    val scope = rememberCoroutineScope()
    val still = p.reducedMotion
    val zoomedOut = ui.city
    val live = !zoomedOut || ui.weeks == 0

    val stacks =
        remember(p, zoomedOut, ui.weeks) { if (zoomedOut) p.towersAt(ui.weeks) else p.stacks }
    val focus = stacks.indexOfFirst { it.id == ui.focus }.coerceAtLeast(0)
    val layouts =
        remember(stacks, focus, zoomedOut, live) {
            stacks.mapIndexed { i, s -> buildingLayout(s, live, ghost = !zoomedOut && i == focus) }
        }
    val arts =
        remember(layouts, text) { layouts.mapIndexed { i, l -> buildingArt(l, stacks[i], text) } }
    val names =
        remember(stacks, text) { stacks.map { text.layout(it.cue, 11f, FontWeight.SemiBold, B.W) } }
    val street = remember(layouts) { streetLayout(layouts) }
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
    val view =
        remember(street, layouts, size, avail) {
            streetView(street, layouts.map { it.height }, size.width / density.density, avail)
        }

    // the street: where along it the view stands, in street units
    val home = street.centers.getOrElse(focus) { 0f }
    val scroll = remember { Animatable(home) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(ui.jump, focus, home, dragging) {
        // the focus moved by something other than the finger (a tap, the button, a new foundation),
        // or the street changed under it: it settles on the building in front
        if (!dragging && scroll.targetValue != home) {
            if (still) scroll.snapTo(home)
            else scroll.animateTo(home, spring(dampingRatio = .86f, stiffness = 260f))
        }
    }
    val zoom = remember { Animatable(if (zoomedOut) 1f else 0f) }
    LaunchedEffect(zoomedOut) {
        val to = if (zoomedOut) 1f else 0f
        if (still) zoom.snapTo(to)
        else zoom.animateTo(to, spring(dampingRatio = .9f, stiffness = 220f))
    }

    // the front building's pan: the camera's own aim, until the owner drags it
    val front = cameras.getOrNull(focus)
    val pan = remember(ui.focus, size) { Animatable(front?.pan ?: 0f) }
    var panned by remember { mutableStateOf(false) }
    // another building, another floor picked, a floor finished: the camera aims again
    LaunchedEffect(ui.focus, ui.selected, stacks.getOrNull(focus)?.next) { panned = false }
    SideEffect { pan.updateBounds(0f, front?.maxPan ?: 0f) }
    LaunchedEffect(pan, front?.pan, front?.maxPan, panned) {
        val aim = front?.pan ?: 0f
        if (!panned && pan.targetValue != aim) {
            if (still) pan.snapTo(aim)
            else pan.animateTo(aim, spring(dampingRatio = .9f, stiffness = 180f))
        }
    }

    // a sheet over the bottom: the street rises until the selected floor stands clear of it
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

    // the street's own time: stars, people, planes. It ticks about 30 times a second while the tab
    // shows (the finger and the plays redraw on their own), and stands still otherwise
    var seconds by remember { mutableFloatStateOf(ambientAt ?: 0f) }
    LaunchedEffect(visible, still, ambientAt) {
        if (ambientAt != null) seconds = ambientAt
        else if (visible && !still) {
            val start = withFrameNanos { it }
            var last = 0L
            while (true) withFrameNanos {
                if (it - last >= TICK_NANOS) {
                    last = it
                    seconds = (it - start) / 1e9f + .001f
                }
            }
        } else seconds = 0f
    }

    // a floor finished, a storey coming down
    val motion = remember { PlayMotion() }
    val play = ui.play
    LaunchedEffect(play?.id, visible) {
        if (play == null) return@LaunchedEffect
        try {
            if (visible && !still) {
                motion.playing = play.id
                if (play.fx.laid) {
                    motion.lights.snapTo(0f)
                    motion.squash.snapTo(0f)
                    motion.burst.snapTo(0f)
                    motion.sweep.snapTo(0f)
                    motion.felled = null
                    motion.swept = play.fx.upgrade != null
                    motion.landing = play.fx.id
                    actions.feel(Feel.LAND)
                    coroutineScope {
                        // the lights come on window by window and the people arrive
                        launch {
                            motion.lights.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
                        }
                        launch {
                            motion.squash.snapTo(1f)
                            motion.squash.animateTo(
                                0f,
                                spring(dampingRatio = .4f, stiffness = 420f),
                            )
                        }
                        launch { motion.burst.animateTo(1f, tween(760, easing = LinearEasing)) }
                        if (motion.swept)
                            launch {
                                actions.feel(Feel.GLOW)
                                motion.sweep.animateTo(
                                    1f,
                                    tween(760, delayMillis = 260, easing = LinearEasing),
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
            // also when cut short (the screen hid, another floor finished): nothing is left half
            // way
            motion.rest(play.id)
            actions.played(play.id)
        }
    }

    // a habit just stacked (or moved here) drops into its place with a puff of scaffolding dust
    val known = remember { HashMap<String, Set<String>>() }
    LaunchedEffect(p.stacks) {
        var fresh: String? = null
        for (s in p.stacks) {
            val ids = s.blocks.map { it.id }.toSet()
            val before = known[s.id]
            if (before != null && s.id == ui.focus) fresh = (ids - before).firstOrNull() ?: fresh
            known[s.id] = ids
        }
        if (fresh != null && visible && !still) {
            motion.adding = fresh
            try {
                motion.drop.snapTo(0f)
                motion.drop.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            } finally {
                motion.adding = null
            }
        }
    }

    fun frame() =
        Frame(
            size,
            density.density,
            window,
            street,
            cameras,
            view,
            focus,
            scroll.value,
            zoom.value,
            pan.value,
            if (still) liftTo else lift.value,
            inset,
        )

    val describe =
        remember(p, ui.focus, zoomedOut, ui.weeks) {
            HabitsCopy.describe(p, p.stack(ui.focus), zoomedOut, ui.weeks)
        }
    val talkBack =
        remember(stacks, focus, zoomedOut) {
            if (zoomedOut || stacks.isEmpty()) emptyList()
            else
                buildList {
                    val here = stacks[focus]
                    stacks.getOrNull(focus + 1)?.let { s ->
                        add(
                            CustomAccessibilityAction("Next building: ${s.cue}") {
                                actions.chosen(s.id).let { true }
                            }
                        )
                    }
                    stacks.getOrNull(focus - 1)?.let { s ->
                        add(
                            CustomAccessibilityAction("Previous building: ${s.cue}") {
                                actions.chosen(s.id).let { true }
                            }
                        )
                    }
                    here.blocks.forEach { b ->
                        add(
                            CustomAccessibilityAction("Tune ${b.title}") {
                                actions.tapped(here.id, Hit(HitKind.FLOOR, b.id)).let { true }
                            }
                        )
                    }
                    add(
                        CustomAccessibilityAction("Edit foundation ${here.cue}") {
                            actions.tapped(here.id, Hit(HitKind.LOBBY, here.id)).let { true }
                        }
                    )
                    add(
                        CustomAccessibilityAction("Add a floor to ${here.cue}") {
                            actions.tapped(here.id, Hit(HitKind.GHOST, here.id)).let { true }
                        }
                    )
                }
        }

    val rest = remember { BuildingMotion() }
    val lively = remember { BuildingMotion() }
    val fade = remember { FadeBrush() }

    Canvas(
        Modifier.fillMaxSize()
            .semantics {
                contentDescription = describe
                customActions = talkBack
            }
            .pointerInput(stacks, focus, zoomedOut, still, front?.maxPan, pan, street) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val startAt = scroll.value
                    val unit = frame().unit
                    var total = Offset.Zero
                    var axis = 0 // 1 sideways (the street), 2 up and down (the camera), 3 a pinch
                    var lifted = false
                    var spread0 = 0f
                    var spread = 0f
                    var centred = street.centred(startAt)
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            // a second finger: a pinch zooms the street in or out
                            val d = (pressed[0].position - pressed[1].position).getDistance()
                            if (axis != 3) {
                                axis = 3
                                spread0 = d
                            }
                            spread = d
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUp() || !change.pressed) {
                            lifted = true
                            break
                        }
                        if (axis == 3) continue
                        total += change.positionChange()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (
                            axis == 0 &&
                                total.getDistance() > viewConfiguration.touchSlop &&
                                !zoomedOut &&
                                stacks.isNotEmpty()
                        )
                            axis = if (abs(total.x) > abs(total.y)) 1 else 2
                        if (axis == 1) {
                            change.consume()
                            dragging = true
                            val at = street.rubberBand(startAt - total.x / unit)
                            scope.launch { scroll.snapTo(at) }
                            // a light tick as each building passes the middle
                            val now = street.centred(at)
                            if (now != centred) {
                                centred = now
                                actions.feel(Feel.TICK)
                            }
                        } else if (axis == 2 && (front?.maxPan ?: 0f) > 0f) {
                            change.consume()
                            panned = true
                            val to = pan.value + change.positionChange().y / density.density
                            scope.launch { pan.snapTo(to) }
                        }
                    }
                    val velocity = tracker.calculateVelocity()
                    when (axis) {
                        0 ->
                            if (lifted)
                                tap(frame(), arts, stacks, zoomedOut, down.position, actions)
                        1 -> {
                            val speed = if (lifted) -velocity.x / unit else 0f
                            val target = street.settle(scroll.value, speed)
                            val id = stacks.getOrNull(target)?.id
                            if (id != null && target != focus) actions.swiped(id)
                            val to = street.centers.getOrElse(target) { scroll.value }
                            scope.launch {
                                try {
                                    if (still) scroll.snapTo(to)
                                    else
                                        scroll.animateTo(
                                            to,
                                            spring(dampingRatio = .8f, stiffness = 140f),
                                            initialVelocity = speed,
                                        )
                                } finally {
                                    dragging = false
                                }
                            }
                        }
                        2 ->
                            if (lifted && !still && (front?.maxPan ?: 0f) > 0f)
                                scope.launch {
                                    pan.animateDecay(
                                        velocity.y / density.density,
                                        exponentialDecay(frictionMultiplier = 2.2f),
                                    )
                                }
                        3 ->
                            if (spread0 > 0f) {
                                val ratio = spread / spread0
                                if (ratio < PINCH_OUT && !zoomedOut && stacks.isNotEmpty())
                                    actions.zoomed(true)
                                else if (ratio > PINCH_IN && zoomedOut) actions.zoomed(false)
                            }
                    }
                }
            }
    ) {
        if (this.size.width <= 0f) return@Canvas
        val f = frame()
        val d = density.density
        val horizon = f.ground - HORIZON_DP * d * f.scale.coerceAtMost(1f)
        // the sun or the moon stands beside the building in front
        val column = (B.W / 2f + B.DX) * f.camera.scale + 14f
        val orb =
            orbSpot(p.hour, this.size.width / d, window.windowTop, horizon / d, column, f.zoom)
        with(backdrop) {
            backdrop(
                p.hour,
                sky,
                horizon,
                f.skyTop,
                Offset(orb.first * d, orb.second * d),
                f.middle,
                seconds,
            )
        }
        val from = f.streetX(0f) - B.GAP
        val to = f.streetX(this.size.width) + B.GAP
        // the tallest building in view decides whether the street must fade out under the heading
        var reach = 0f
        for (i in layouts.indices) {
            val l = street.lefts[i]
            if (l + layouts[i].lot < from || l > to) continue
            reach = max(reach, layouts[i].height)
        }
        val fades = layouts.isNotEmpty() && f.ground - reach * f.unit < f.top + TOP_FADE * d
        if (fades)
            drawIntoCanvas { it.saveLayer(Rect(0f, 0f, this.size.width, this.size.height), layer) }
        clipRect(0f, f.top, this.size.width, this.size.height) {
            withTransform({
                translate(this@Canvas.size.width / 2f - f.middle * f.unit, f.ground)
                scale(f.unit, f.unit, Offset.Zero)
            }) {
                with(painter) {
                    pavement(sky, from, to, (this@Canvas.size.height - f.ground) / f.unit + 4f)
                    pools(street.furniture, from, to, sky.dark)
                    val detailed = f.scale > .55f
                    for (i in layouts.indices) {
                        val l = street.lefts[i]
                        if (l + layouts[i].lot + B.DX < from || l - 20f > to) continue
                        val motion =
                            if (i == focus) motion.now(seconds, lively) else rest.still(seconds)
                        translate(street.centers[i], 0f) {
                            building(
                                arts[i],
                                motion,
                                ui.selected.takeIf { i == focus && f.zoom == 0f },
                                accent,
                                live,
                                detailed,
                                sky,
                            )
                        }
                    }
                    for (k in street.furniture.indices) {
                        val item = street.furniture[k]
                        if (item.x < from - 30f || item.x > to + 30f) continue
                        furniture(item, sky.dark, seconds)
                    }
                    if (layouts.isEmpty()) emptyLot(sky)
                }
            }
        }
        if (fades) {
            // rub the layer out above the window, and ever less over the fade under it
            val end = f.top + TOP_FADE * d
            drawRect(
                fade.brush(f.top, end),
                Offset.Zero,
                Size(this.size.width, end),
                blendMode = BlendMode.DstOut,
            )
            drawIntoCanvas { it.restore() }
        }
        if (f.zoom > .02f)
            for (i in layouts.indices) streetName(
                names[i],
                f.screenX(street.centers[i]),
                f,
                sky.ink,
            )
    }
}

/** The fade under the heading: its brush, kept until the window moves. */
private class FadeBrush {
    private var key0 = -1f
    private var key1 = -1f
    private var brush: Brush? = null

    fun brush(top: Float, end: Float): Brush {
        val b = brush
        if (b != null && key0 == top && key1 == end) return b
        key0 = top
        key1 = end
        return Brush.verticalGradient(
                0f to Color.Black,
                (top / end).coerceIn(0f, 1f) to Color.Black,
                1f to Color.Transparent,
                startY = 0f,
                endY = end,
            )
            .also { brush = it }
    }
}

/** Nothing built yet: the empty lot where the first foundation will go, fenced off. */
private fun DrawScope.emptyLot(sky: SkyColors) {
    val half = B.W / 2f
    drawRect(sky.ground[1], Offset(-half, -4f), Size(B.W, 4f))
    var x = -half
    while (x <= half) {
        drawRect(SceneInk.Scaffold, Offset(x, -22f), Size(1.6f, 22f), alpha = .8f)
        x += 12f
    }
    drawRect(SceneInk.Scaffold, Offset(-half, -18f), Size(B.W, 1.4f), alpha = .8f)
    drawRect(SceneInk.Scaffold, Offset(-half, -9f), Size(B.W, 1.4f), alpha = .8f)
}

/** A building's name under it on the zoomed-out street, fading in as the street zooms out. */
private fun DrawScope.streetName(words: TextLayoutResult, x: Float, f: Frame, ink: Color) {
    val d = f.density
    val scale = (f.unit / d * 1.4f).coerceIn(.62f, 1f) * d
    withTransform({
        translate(x, f.ground + (BuildingPainter.PAVEMENT + 6f) * f.unit)
        scale(scale, scale, Offset.Zero)
    }) {
        drawText(words, ink, Offset(-words.size.width / 2f, 0f), alpha = f.zoom)
    }
}

/**
 * A tap at [at]: a building of the zoomed-out street, a building beside the one in front, or a part
 * of the one in front.
 */
private fun tap(
    f: Frame,
    arts: List<BuildingArt>,
    stacks: List<HabitStack>,
    zoomedOut: Boolean,
    at: Offset,
    actions: StageActions,
) {
    if (stacks.isEmpty()) return
    val x = f.streetX(at.x)
    if (zoomedOut) {
        f.street.buildingAt(x, arts.map { it.layout })?.let { actions.chosen(stacks[it].id) }
        return
    }
    // nothing of a building shows in the heading's band
    if (at.y < f.top) return
    val y = (f.ground - at.y) / f.unit
    // the building in front first
    for (i in stacks.indices.sortedBy { abs(it - f.focus) }) {
        val l: BuildingLayout = arts[i].layout
        val hit = l.hit(x - f.street.centers[i], y) ?: continue
        if (i == f.focus) actions.tapped(stacks[i].id, hit) else actions.chosen(stacks[i].id)
        return
    }
}

/** How far above the pavement line the horizon is drawn, at full size. */
private const val HORIZON_DP = 30f

/** The street's own clock ticks no faster than this (about 30 a second). */
private const val TICK_NANOS = 33_000_000L

/**
 * A pinch closing to this share of its start zooms out to the street; opening past the other, back.
 */
private const val PINCH_OUT = .82f
private const val PINCH_IN = 1.2f
