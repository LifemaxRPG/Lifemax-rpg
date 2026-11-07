package com.lifemax.rpg.feature.lifedirector.presentation.dial

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.lifemax.rpg.feature.lifedirector.domain.DialArc
import com.lifemax.rpg.feature.lifedirector.domain.DialCentre
import com.lifemax.rpg.feature.lifedirector.domain.DialHandle
import com.lifemax.rpg.feature.lifedirector.domain.DialRadii
import com.lifemax.rpg.feature.lifedirector.domain.DirectorDial
import com.lifemax.rpg.feature.lifedirector.domain.ReturnToTodayMotion
import com.lifemax.rpg.feature.lifedirector.domain.SessionState
import com.lifemax.rpg.feature.lifedirector.domain.StripDay
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.DialHold
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette
import com.lifemax.rpg.feature.lifedirector.presentation.components.LocalDirectorBuzz
import com.lifemax.rpg.feature.lifedirector.presentation.components.rememberPulse
import com.lifemax.rpg.feature.lifedirector.presentation.components.spoken
import com.lifemax.rpg.feature.lifedirector.presentation.drag.DragRemoveController
import com.lifemax.rpg.feature.lifedirector.presentation.drag.removeFromToday
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

/**
 * The 24-hour dial (the web's dialHtml): its 360-unit square scaled to the width, at most 370 dp,
 * midnight at the top. The drawing is prepared whenever its data changes (DialArt) and drawn in
 * layers: the face and plan, what moves with the time, the next session's pulsing halo, the turning
 * hand and the handles. So the 15 s refresh, which only moves the time, redraws the small time
 * layer, and nothing around the dial redraws the face. A tap finds its arc by ring and angle; a
 * touch on a handle drags it ([DialGestures]); every arc and handle is also a node TalkBack can
 * read and use. [hold] is a handle held or let go, [asking] that the page is asked about one (the
 * handles wait), [run] a build playing. [animate] is false while the screen is hidden. [tour] is a
 * step of Today's guide: its layers dim, its name tags pin on and, on the handles step, the handles
 * pulse (TourMode); [centreAnchor] names the middle for the guide's spotlight. [intro] is the
 * glance's arrival playing (DialIntro): the rings, sessions and hand fade in. With a [removal]
 * controller, holding a session's segment lifts it off the day, as holding its row does; the
 * segment stays as a ghost until the card has settled or the page has taken the session off.
 */
@Composable
fun ChronoDial(
    dial: DirectorDial,
    centre: DialCentre,
    animate: Boolean,
    onArc: (DialArc) -> Unit,
    onTap: (Tap) -> Unit,
    modifier: Modifier = Modifier,
    hold: DialHold? = null,
    asking: Boolean = false,
    handles: HandleActions = HandleActions(),
    run: BuildRun? = null,
    tour: DialTourRun? = null,
    centreAnchor: Modifier = Modifier,
    intro: DialIntroRun? = null,
    removal: DragRemoveController? = null,
    centreContent: (@Composable () -> Unit)? = null,
    centreDismiss: (() -> Unit)? = null,
    days: List<StripDay> = emptyList(),
    selectedDay: Int = 0,
    onDay: ((Int) -> Unit)? = null,
    dayWheelEnabled: Boolean = true,
    calendarDate: String = "",
    onToday: (() -> Unit)? = null,
    todayOffset: Int = 0,
) {
    val text = rememberTextMeasurer()
    val face =
        remember(dial.radii, dial.hourWords) {
            DialFace(dial.radii, dial.hourWords, text, includeBezel = false)
        }
    val bezel = remember(dial.radii, dial.hourWords) { DialBezel(dial.radii, dial.hourWords, text) }
    val plan = remember(dial.radii, dial.arcs, dial.words) { DialPlan(dial, text) }
    val now =
        remember(dial.radii, dial.hand, dial.ring) { DialNow(dial.radii, dial.hand, dial.ring) }
    val next = dial.arcs.firstOrNull { it.next }
    val halo = remember(dial.radii, next) { next?.let { DialHalo(it, dial.radii) } }
    val art = remember(run?.build, dial) { run?.let { BuildArt(it.build, dial) } }
    val introOrder = remember(dial.arcs) { DialIntro.order(dial.arcs) }
    val held = hold?.takeIf { it.held && run == null }
    val look = rememberTourLook(tour)
    val pulse = rememberTourPulse(look, tour, animate)
    val buzz = LocalDirectorBuzz.current
    val view = LocalView.current
    val lift = remember(removal, view) { removal?.let { DialLift(it, view) } }
    val ghost = rememberGhost(removal)
    // Keep the selected segment's location while its asynchronous sheet is loading.
    // Touch and accessibility actions share this entry point.
    var activityOrigin by remember { mutableStateOf<Offset?>(null) }
    val openArc: (DialArc) -> Unit = { arc ->
        val radius = if (arc.outer) dial.radii.outer else dial.radii.inner
        activityOrigin =
            DialGeometry.point(radius, (arc.a0 + arc.a1) / 2) -
                Offset(DialGeometry.SIDE / 2, DialGeometry.SIDE / 2)
        onArc(arc)
    }
    val activityArrival = remember { Animatable(if (centreContent != null) 1f else 0f) }
    LaunchedEffect(centreContent != null, animate) {
        if (centreContent == null) {
            activityArrival.snapTo(0f)
            activityOrigin = null
        } else if (!animate) {
            activityArrival.snapTo(1f)
        } else {
            activityArrival.animateTo(1f, tween(360, easing = EaseOut))
        }
    }
    var scrubbingDays by remember { mutableStateOf(false) }
    var previewDay by remember { mutableStateOf(selectedDay) }
    var bezelDrag by remember { mutableFloatStateOf(0f) }
    var bezelStart by remember { mutableFloatStateOf(0f) }
    val bezelRest = remember { Animatable(0f) }
    val returnScope = rememberCoroutineScope()
    val returnRotation = remember { Animatable(0f) }
    val homeReveal = remember { Animatable(1f) }
    var returningHome by remember { mutableStateOf(false) }
    var revealingHome by remember { mutableStateOf(false) }
    var returnProgress by remember { mutableFloatStateOf(0f) }
    var returnDate by remember { mutableStateOf(LocalDate.of(1970, 1, 1)) }
    var returnDays by remember { mutableStateOf(0) }
    val dateBase =
        remember(calendarDate, dial.start) {
            runCatching { LocalDate.parse(calendarDate) }
                .getOrElse {
                    Instant.ofEpochSecond(dial.start).atZone(ZoneId.systemDefault()).toLocalDate()
                }
        }
    LaunchedEffect(scrubbingDays, animate, returningHome) {
        if (!scrubbingDays && !returningHome) {
            bezelRest.snapTo(bezelDrag)
            val aligned = round(bezelDrag / 360f) * 360f
            if (animate) bezelRest.animateTo(aligned, spring(dampingRatio = .68f, stiffness = 240f))
            else bezelRest.snapTo(aligned)
        }
    }
    LaunchedEffect(selectedDay, days) { if (!scrubbingDays) previewDay = selectedDay }
    val returnToday: (() -> Unit)? =
        onToday?.let { navigate ->
            {
                if (!returningHome) {
                    if (animate && todayOffset != 0) {
                        returnDate = dateBase
                        returnDays = todayOffset
                        returnProgress = 0f
                        revealingHome = false
                        returningHome = true
                        val start = bezelRest.value
                        val travel = ReturnToTodayMotion.forDays(todayOffset)
                        val direction = travel.direction
                        val turns = travel.turns
                        val target = round(start / 360f) * 360f + direction * turns * 360f
                        returnScope.launch {
                            try {
                                homeReveal.snapTo(0f)
                                returnRotation.snapTo(start)
                                returnRotation.animateTo(
                                    target + direction * 12f,
                                    tween(
                                        travel.durationMs,
                                        easing = CubicBezierEasing(.32f, 0f, .12f, 1f),
                                    ),
                                ) {
                                    returnProgress =
                                        ((value - start) / (target - start)).coerceIn(0f, 1f)
                                }
                                returnProgress = 1f
                                returnRotation.animateTo(
                                    target,
                                    spring(dampingRatio = .65f, stiffness = 420f),
                                )
                                bezelRest.snapTo(0f)
                                revealingHome = true
                                homeReveal.animateTo(1f, tween(560, easing = EaseOut))
                            } finally {
                                bezelDrag = 0f
                                returningHome = false
                            }
                        }
                    }
                    navigate()
                }
            }
        }
    val wheel =
        if (
            dayWheelEnabled &&
                !returningHome &&
                days.isNotEmpty() &&
                onDay != null &&
                centreContent == null &&
                tour == null &&
                selectedDay in days.indices
        ) {
            DayWheelInput(
                days.size,
                selectedDay,
                begin = {
                    previewDay = selectedDay
                    scrubbingDays = true
                    bezelStart = bezelRest.value % 360f
                    bezelDrag = bezelStart
                },
                preview = { day, rotation ->
                    previewDay = day
                    bezelDrag = bezelStart + rotation
                },
                end = { chosen ->
                    scrubbingDays = false
                    if (chosen != null && chosen != selectedDay) onDay(chosen)
                    else previewDay = selectedDay
                },
            )
        } else null
    val dayArrival = remember { Animatable(1f) }
    var previousDay by remember { mutableStateOf(dial.start) }
    LaunchedEffect(dial.start, animate) {
        if (previousDay != dial.start && animate) {
            dayArrival.snapTo(.35f)
            dayArrival.animateTo(1f, tween(220, easing = EaseOut))
        } else dayArrival.snapTo(1f)
        previousDay = dial.start
    }
    val input by
        rememberUpdatedState(
            DialInput(
                dial,
                hold,
                asking || (hold != null && !hold.held),
                run,
                handles,
                openArc,
                lift,
                centreDismiss,
                DialGeometry.SIDE * .33f,
                wheel,
                if (dayWheelEnabled && tour == null && run == null && !returningHome) returnToday
                else null,
            )
        )
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val side = min(maxWidth, 370.dp)
        Box(
            Modifier.size(side)
                .onGloballyPositioned {
                    removal
                        ?.anchor
                        ?.dialAt(it, segmentCentres({ input.dial }, it.size.width.toFloat()))
                }
                .pointerInput(animate) { DialGestures(this, { input }, buzz).run() }
        ) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val s = run?.let { BuildTimeline.dialScale(it.build, it.ms) } ?: 1f
                    scaleX = s
                    scaleY = s
                    alpha = dayArrival.value
                }
            ) {
                Canvas(
                    Modifier.fillMaxSize().graphicsLayer().semantics {
                        contentDescription = DIAL_DESCRIPTION
                    }
                ) {
                    inUnits {
                        face.draw(this)
                        if (run != null && art != null) {
                            val t = run.ms
                            if (run.build.full)
                                for (k in 0 until BuildTimeline.TICKS) {
                                    val f = BuildTimeline.tickFlash(run.build, t, k)
                                    if (f > 0f) flashTick(dial.radii, k, f)
                                }
                            art.drawPlan(this, plan, t)
                        } else if (held != null) plan.draw(this, OUTER_HELD, WORDS_HELD, INNER_HELD)
                        else if (intro != null) drawIntro(plan, introOrder, intro.ms)
                        else plan.draw(this, look.outer, look.words, look.inner, ghost)
                    }
                }
                Canvas(
                    Modifier.fillMaxSize().graphicsLayer {
                        rotationZ =
                            when {
                                returningHome -> returnRotation.value
                                scrubbingDays -> bezelDrag
                                else -> bezelRest.value
                            }
                    }
                ) {
                    inUnits { bezel.draw(this) }
                }
                Canvas(
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = run?.let { BuildTimeline.faceShown(it.build, it.ms) } ?: 1f
                    }
                ) {
                    inUnits {
                        now.draw(
                            this,
                            if (returningHome) {
                                if (revealingHome) homeReveal.value else 0f
                            } else 1f,
                        )
                    }
                }
                if (halo != null && next != null) {
                    val pulse = rememberPulse(animate, 2400)
                    Canvas(
                        Modifier.fillMaxSize().graphicsLayer {
                            val shown =
                                when {
                                    run != null -> art?.haloShown(next, run.ms) ?: 1f
                                    held != null -> INNER_HELD
                                    else -> look.inner
                                }
                            val arrived =
                                intro?.let { DialIntro.session(it.ms, introOrder[next] ?: 0) } ?: 1f
                            alpha = lerp(1f, .41f, pulse.value) * shown * arrived
                        }
                    ) {
                        inUnits { halo.draw(this) }
                    }
                }
                if (run != null && art != null)
                    Canvas(Modifier.fillMaxSize()) { inUnits { art.drawEffects(this, run.ms) } }
                held?.span?.let { (a0, a1) ->
                    val color = DirectorPalette.of(held.handle.kind)
                    Canvas(Modifier.fillMaxSize()) {
                        inUnits { dragArc(dial.radii, a0, a1, color) }
                    }
                }
                dial.hand?.let { Hand(it, animate, run, intro) }
                if (dial.handles.isNotEmpty() || hold != null)
                    Canvas(Modifier.fillMaxSize().dimmed(look.handles)) {
                        inUnits { drawHandles(dial, hold, run, pulse.value) }
                    }
                if (run != null && art != null && run.build.full)
                    Canvas(Modifier.fillMaxSize()) {
                        inUnits {
                            art.drawArm(
                                this,
                                BuildTimeline.armAngle(run.build, run.ms),
                                BuildTimeline.armAlpha(run.build, run.ms),
                            )
                        }
                    }
                tour?.let { TourTags(it.step.tags, it.still, animate) }
            }
            ArcTargets(dial.arcs, dial.radii, openArc, removal)
            HandleTargets(dial, hold, asking || run != null, handles)
            val centreBox =
                Modifier.align(Alignment.Center).then(centreAnchor).graphicsLayer {
                    val shown =
                        if (returningHome) {
                            if (revealingHome) homeReveal.value else 0f
                        } else 1f
                    alpha = shown
                    scaleX = lerp(.94f, 1f, shown)
                    scaleY = scaleX
                    translationY = (1f - shown) * 10.dp.toPx()
                }
            when {
                run != null -> {
                    DialCentreView(
                        centre,
                        side / DialGeometry.SIDE,
                        animate,
                        onTap,
                        centreBox.width(side * .42f).graphicsLayer {
                            val (alpha, scale) = BuildTimeline.centre(run.build, run.ms)
                            this.alpha = alpha
                            scaleX = scale
                            scaleY = scale
                        },
                    )
                    BuildHud(run, centreBox.width(side * .44f))
                }
                centreContent != null && hold == null -> {
                    Box(
                        centreBox
                            .size(side * .66f)
                            .graphicsLayer {
                                val progress = if (animate) activityArrival.value else 1f
                                val origin = activityOrigin ?: Offset.Zero
                                val units = side.toPx() / DialGeometry.SIDE
                                translationX = origin.x * units * (1f - progress)
                                translationY = origin.y * units * (1f - progress)
                                val initialScale = if (activityOrigin != null) .08f else .92f
                                scaleX = lerp(initialScale, 1f, progress)
                                scaleY = scaleX
                                alpha = progress
                            }
                            .clip(CircleShape)
                            .background(DirectorPalette.bg0)
                            .border(1.dp, DirectorPalette.line, CircleShape)
                            .pointerInput(centreDismiss) { detectTapGestures(onTap = {}) }
                            .padding(side * .66f * .15f)
                    ) {
                        centreContent()
                    }
                }
                hold != null -> DragCentre(hold.preview, centreBox.width(side * .42f))
                else ->
                    DialCentreView(
                        centre,
                        side / DialGeometry.SIDE,
                        animate,
                        onTap,
                        centreBox.width(side * .42f).dimmed(look.centre),
                    )
            }
            if (scrubbingDays || returningHome) {
                val date =
                    if (returningHome)
                        returnDate.minusDays((returnDays.toDouble() * returnProgress).roundToLong())
                    else dateBase.plusDays((previewDay - selectedDay).toLong())
                Box(
                    Modifier.align(Alignment.Center)
                        .graphicsLayer {
                            val leaving =
                                if (returningHome && revealingHome) homeReveal.value else 0f
                            alpha = 1f - leaving
                            scaleX = 1f - .04f * leaving
                            scaleY = scaleX
                            translationY = -8.dp.toPx() * leaving
                        }
                        .size(side * .48f)
                        .clip(CircleShape)
                        .background(DirectorPalette.bg0),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEE\nd MMM yyyy")),
                        color = DirectorPalette.ink,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** What the dial's handles ask for: the view model's hold, move, release and step. */
class HandleActions(
    val hold: (DialHold) -> Unit = {},
    val move: (Int) -> Unit = {},
    val release: (cancelled: Boolean) -> Unit = {},
    val step: (DialHandle, Int) -> Unit = { _, _ -> },
)

/** The handles: each where it is, a held one where the finger has it; popping in after a build. */
private fun DrawScope.drawHandles(
    dial: DirectorDial,
    hold: DialHold?,
    run: BuildRun?,
    pulse: Float = 1f,
) {
    val ring = dial.radii.outer
    dial.handles.forEachIndexed { k, h ->
        if (h.key == hold?.handle?.key) return@forEachIndexed
        val scale = run?.let { BuildTimeline.handleScale(it.build, it.ms, k) } ?: pulse
        handle(ring, h.a, DirectorPalette.of(h.kind), held = false, scale)
    }
    if (hold != null)
        handle(ring, hold.stops[hold.stop].a, DirectorPalette.of(hold.handle.kind), hold.held)
}

/** The hand, turned by its layer: a small step glides as the web's does, a jump snaps. */
@Composable
private fun Hand(angle: Float, animate: Boolean, run: BuildRun?, intro: DialIntroRun?) {
    val turn = remember { Animatable(angle) }
    LaunchedEffect(angle) {
        if (animate && abs(angle - turn.value) <= 3f)
            turn.animateTo(angle, tween(650, easing = EaseOut))
        else turn.snapTo(angle)
    }
    Canvas(
        Modifier.fillMaxSize().graphicsLayer {
            rotationZ = run?.let { BuildTimeline.handAngle(it.build, it.ms, angle) } ?: turn.value
            alpha =
                run?.let { BuildTimeline.faceShown(it.build, it.ms) }
                    ?: intro?.let { DialIntro.hand(it.ms) }
                    ?: 1f
        }
    ) {
        inUnits { handLayer.draw(this) }
    }
}

/**
 * One 48 dp node per arc, at its middle, carrying its label and a session's state for TalkBack;
 * pressing one does what tapping the arc does, and a session that can be taken off the day offers
 * "Remove from today" as its row does. Touches pass through them to the dial.
 */
@Composable
private fun ArcTargets(
    arcs: List<DialArc>,
    radii: DialRadii,
    onArc: (DialArc) -> Unit,
    removal: DragRemoveController?,
) {
    Layout(
        content = {
            arcs.forEach { arc ->
                Box(
                    Modifier.semantics {
                        contentDescription = arc.label
                        if (arc.state != SessionState.NONE && arc.state != SessionState.PLAN)
                            stateDescription = arc.state.spoken()
                        if (arc.tappable) {
                            role = Role.Button
                            onClick {
                                onArc(arc)
                                true
                            }
                        }
                        // a session the page lets be taken off the day, as its row offers
                        if (removal != null && arc.removable(removal))
                            customActions = listOf(removeFromToday { removal.remove(arc) })
                    }
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val target = 48.dp.roundToPx()
        val scale = unitPx(constraints.maxWidth.toFloat())
        val placeables = measurables.map { it.measure(Constraints.fixed(target, target)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, placeable ->
                val arc = arcs[i]
                val r = if (arc.outer) radii.outer else radii.inner
                val at = DialGeometry.point(r, (arc.a0 + arc.a1) / 2) * scale
                placeable.place(at.x.toInt() - target / 2, at.y.toInt() - target / 2)
            }
        }
    }
}

/** Drawn at [alpha] as one layer (a guide step dims a part of the dial); no layer while opaque. */
private fun Modifier.dimmed(alpha: Float): Modifier =
    if (alpha < 1f) graphicsLayer { this.alpha = alpha } else this

internal fun unitPx(sidePx: Float) = sidePx / DialGeometry.SIDE

/** Draws [block] in dial units. */
internal inline fun DrawScope.inUnits(block: DrawScope.() -> Unit) =
    scale(unitPx(size.width), Offset.Zero, block)

private val EaseOut = CubicBezierEasing(.22f, 1f, .36f, 1f)

/**
 * The web's .ld-dial.drag: the outer ring, the words and the inner ring dim under a held handle.
 */
private const val OUTER_HELD = .3f
private const val WORDS_HELD = .25f
private const val INNER_HELD = .45f

private const val DIAL_DESCRIPTION =
    "24-hour plan. Outer ring: your fixed time. Inner ring: planned sessions."
