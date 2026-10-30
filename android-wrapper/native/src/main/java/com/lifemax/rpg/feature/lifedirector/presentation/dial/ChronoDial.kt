package com.lifemax.rpg.feature.lifedirector.presentation.dial

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.util.lerp
import com.lifemax.rpg.feature.lifedirector.domain.DialArc
import com.lifemax.rpg.feature.lifedirector.domain.DialCentre
import com.lifemax.rpg.feature.lifedirector.domain.DialRadii
import com.lifemax.rpg.feature.lifedirector.domain.DirectorDial
import com.lifemax.rpg.feature.lifedirector.domain.SessionState
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.components.rememberPulse
import com.lifemax.rpg.feature.lifedirector.presentation.components.spoken
import kotlin.math.abs

/**
 * The 24-hour dial (the web's dialHtml): its 360-unit square scaled to the width, at most 370 dp,
 * midnight at the top. The drawing is prepared whenever its data changes (DialArt) and drawn in
 * layers: the face and plan, what moves with the time, the next session's pulsing halo and the
 * turning hand. So the 15 s refresh, which only moves the time, redraws the small time layer, and
 * nothing around the dial redraws the face. A tap finds its arc by ring and angle; every arc is
 * also a node TalkBack can read and press. [animate] is false while the screen is hidden.
 */
@Composable
fun ChronoDial(
    dial: DirectorDial,
    centre: DialCentre,
    animate: Boolean,
    onArc: (DialArc) -> Unit,
    onTap: (Tap) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = rememberTextMeasurer()
    val face = remember(dial.radii, dial.hourWords) { DialFace(dial.radii, dial.hourWords, text) }
    val plan = remember(dial.radii, dial.arcs, dial.words) { DialPlan(dial, text) }
    val now =
        remember(dial.radii, dial.hand, dial.ring) { DialNow(dial.radii, dial.hand, dial.ring) }
    val next = dial.arcs.firstOrNull { it.next }
    val halo = remember(dial.radii, next) { next?.let { DialHalo(it, dial.radii) } }
    val arcs by rememberUpdatedState(dial.arcs)
    val radii by rememberUpdatedState(dial.radii)
    val tapped by rememberUpdatedState(onArc)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val side = min(maxWidth, 370.dp)
        Box(
            Modifier.size(side).pointerInput(Unit) {
                detectTapGestures { p ->
                    DialGeometry.arcAt(arcs, radii, p / unitPx(size.width.toFloat()))?.let(tapped)
                }
            }
        ) {
            Canvas(
                Modifier.fillMaxSize().graphicsLayer().semantics {
                    contentDescription = DIAL_DESCRIPTION
                }
            ) {
                inUnits {
                    face.draw(this)
                    plan.draw(this)
                }
            }
            Canvas(Modifier.fillMaxSize().graphicsLayer()) { inUnits { now.draw(this) } }
            if (halo != null) {
                val pulse = rememberPulse(animate, 2400)
                Canvas(
                    Modifier.fillMaxSize().graphicsLayer { alpha = lerp(1f, .41f, pulse.value) }
                ) {
                    inUnits { halo.draw(this) }
                }
            }
            dial.hand?.let { Hand(it, animate) }
            ArcTargets(dial.arcs, dial.radii, onArc)
            DialCentreView(
                centre,
                side / DialGeometry.SIDE,
                animate,
                onTap,
                Modifier.align(Alignment.Center).width(side * .42f),
            )
        }
    }
}

/** The hand, turned by its layer: a small step glides as the web's does, a jump snaps. */
@Composable
private fun Hand(angle: Float, animate: Boolean) {
    val turn = remember { Animatable(angle) }
    LaunchedEffect(angle) {
        if (animate && abs(angle - turn.value) <= 3f)
            turn.animateTo(angle, tween(650, easing = EaseOut))
        else turn.snapTo(angle)
    }
    Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = turn.value }) {
        inUnits { handLayer.draw(this) }
    }
}

/**
 * One 48 dp node per arc, at its middle, carrying its label and a session's state for TalkBack;
 * pressing one does what tapping the arc does. Touches pass through them to the dial.
 */
@Composable
private fun ArcTargets(arcs: List<DialArc>, radii: DialRadii, onArc: (DialArc) -> Unit) {
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

private fun unitPx(sidePx: Float) = sidePx / DialGeometry.SIDE

/** Draws [block] in dial units. */
private inline fun DrawScope.inUnits(block: DrawScope.() -> Unit) =
    scale(unitPx(size.width), Offset.Zero, block)

private val EaseOut = CubicBezierEasing(.22f, 1f, .36f, 1f)

private const val DIAL_DESCRIPTION =
    "24-hour plan. Outer ring: your fixed time. Inner ring: planned sessions."
