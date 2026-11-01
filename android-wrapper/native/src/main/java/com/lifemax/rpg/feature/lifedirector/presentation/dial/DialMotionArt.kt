package com.lifemax.rpg.feature.lifedirector.presentation.dial

import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import com.lifemax.rpg.core.designsystem.glowPaint
import com.lifemax.rpg.feature.lifedirector.domain.ArcStyle
import com.lifemax.rpg.feature.lifedirector.domain.BuildItem
import com.lifemax.rpg.feature.lifedirector.domain.DialArc
import com.lifemax.rpg.feature.lifedirector.domain.DialBuild
import com.lifemax.rpg.feature.lifedirector.domain.DialRadii
import com.lifemax.rpg.feature.lifedirector.domain.DirectorDial
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialGeometry.CENTRE
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialGeometry.point
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * What moves on the dial, drawn in its 360-unit square: the handles and the arc a held one spans
 * (style.css .ld-hd, .ld-drag-arc), and the build (runBuild in logic.js, .ld-bf-* in style.css)
 * at a moment of its timeline (BuildTimeline).
 */

/**
 * A handle (.ld-hd) at angle [a] on ring [ring] in its rule's [color]: a dark ring with a coloured
 * pip, or while [held] a coloured ring edged in white with a dark pip. [scale] pops it in. The ring
 * casts the page's shadow below it and the pip a faint light above it (.ld-hd-ring and .ld-hd-pip's
 * drop-shadow), from Android 9, which can blur them.
 */
internal fun DrawScope.handle(
    ring: Float,
    a: Float,
    color: Color,
    held: Boolean,
    scale: Float = 1f,
) {
    if (scale <= 0f) return
    val at = point(ring, a)
    scale(scale, at) {
        if (HANDLE_SHADOWS)
            drawIntoCanvas {
                it.drawCircle(at + Offset(0f, 1.5f), 10.5f + HANDLE_EDGE.width / 2, RING_SHADOW)
            }
        drawCircle(if (held) color else P.handleFill, 10.5f, at)
        drawCircle(if (held) Color.White else color, 10.5f, at, style = HANDLE_EDGE)
        if (HANDLE_SHADOWS) drawIntoCanvas { it.drawCircle(at + Offset(0f, -1f), 3.5f, PIP_LIGHT) }
        drawCircle(if (held) P.handleFill else color, 3.5f, at)
    }
}

/** The span a held handle would give its rule (.ld-drag-arc): wide, glowing, on the outer ring. */
internal fun DrawScope.dragArc(radii: DialRadii, a0: Float, a1: Float, color: Color) {
    val path = arcPath(radii.outer, a0, a1)
    val width = radii.outerWidth + 4
    drawIntoCanvas { it.drawPath(path, strokeGlow(color, 10f, width)) }
    drawPath(path, color, style = Stroke(width))
}

/** The angle of [epoch] on [dial], kept on the dial (the web's angOf). */
internal fun angleOf(dial: DirectorDial, epoch: Long): Float =
    (epoch.coerceIn(dial.start, dial.end) - dial.start).toFloat() /
        (dial.end - dial.start).coerceAtLeast(1) * 360f

/**
 * The build's pieces for one dial, prepared once: which arcs fly, stay or flash, and the trails'
 * and ghosts' paths.
 */
internal class BuildArt(val build: DialBuild, private val dial: DirectorDial) {
    private val items = build.items.associateBy { it.id }
    private val keep = build.keep.toSet()
    private val flips = build.flips.toSet()
    private val ghostPaths = build.ghosts.map { arcPath(dial.radii.inner, it.a0, it.a1) }

    private fun sessionId(arc: DialArc) = if (arc.style == ArcStyle.SESSION) arc.tap?.id else null

    /**
     * The day's arcs at [t]: bands drawn as the arm passes, words in after the lock, sessions
     * flying, staying, flashing or shown as the arm passes them (DialPlan's order).
     */
    fun drawPlan(scope: DrawScope, plan: DialPlan, t: Float) =
        with(scope) {
            for (art in plan.outer) {
                val arc = art.arc
                val drawn =
                    BuildTimeline.bandDrawn(
                        build,
                        t,
                        angleOf(dial, arc.start),
                        angleOf(dial, arc.end),
                    )
                if (drawn >= 1f) art.draw(this)
                else if (drawn > 0f)
                    clipPath(wedge(arc.a0, arc.a0 + (arc.a1 - arc.a0) * drawn)) { art.draw(this) }
            }
            faded(BuildTimeline.faceShown(build, t)) { plan.words.draw(this) }
            for (art in plan.inner) {
                val id = sessionId(art.arc)
                val item = id?.let { items[it] }
                if (item != null) {
                    val f = BuildTimeline.flight(build, item, t)
                    lit(f.alpha, f.brightness) {
                        withTransform({
                            rotate(f.rotation, CENTRE_POINT)
                            scale(f.scale, f.scale, CENTRE_POINT)
                        }) {
                            art.draw(this)
                        }
                    }
                } else {
                    val shown =
                        if (id == null || id in keep)
                            BuildTimeline.revealed(build, t, angleOf(dial, art.arc.start))
                        else 1f
                    val bright = if (id in flips) BuildTimeline.flipBrightness(build, t) else 1f
                    lit(shown, bright) { art.draw(this) }
                }
            }
        }

    /** How much of the next session's halo shows: none until its session has landed. */
    fun haloShown(arc: DialArc, t: Float): Float {
        val item = sessionId(arc)?.let { items[it] }
        return when {
            item != null -> if (t >= item.at + item.ms) 1f else 0f
            else -> BuildTimeline.revealed(build, t, angleOf(dial, arc.start))
        }
    }

    /**
     * What the build adds over the plan at [t] (.ld-bf, under the hand): moved sessions' trails,
     * dropped ones breaking up, touchdown ripples and sparks, and the lock's shockwave.
     */
    fun drawEffects(scope: DrawScope, t: Float) =
        with(scope) {
            val radii = dial.radii
            for (item in build.items) {
                BuildTimeline.trail(item, t)?.let {
                    drawPath(arcPath(radii.inner, it.a0, it.a1), P.trail, it.alpha, TRAIL)
                }
            }
            if (ghostPaths.isNotEmpty()) {
                val g = BuildTimeline.ghost(build, t, radii.innerWidth)
                if (g.alpha > 0f) {
                    val stroke = Stroke(g.strokeWidth, pathEffect = GHOST_DASH)
                    withTransform({
                        rotate(g.rotation, CENTRE_POINT)
                        scale(g.scale, g.scale, CENTRE_POINT)
                    }) {
                        ghostPaths.forEach { drawPath(it, P.block, g.alpha, stroke) }
                    }
                }
            }
            for (item in build.items) touchdown(item, radii, t)
            BuildTimeline.wave(build, t)?.let { (scale, alpha) ->
                drawCircle(P.wave, 152f * scale, CENTRE_POINT, alpha, Stroke(2f))
            }
        }

    private fun DrawScope.touchdown(item: BuildItem, radii: DialRadii, t: Float) {
        val mid = (item.a0 + item.a1) / 2
        val at = point(radii.inner, mid)
        BuildTimeline.ripple(item, t)?.let { (scale, alpha) ->
            drawCircle(P.ripple, 7f * scale, at, alpha, Stroke(1.6f))
        }
        BuildTimeline.sparks(item, t)?.let { (flown, alpha) ->
            for (k in 0 until 6) {
                val turn = ((mid + (k - 2.5f) * 24f - 90f) * PI / 180).toFloat()
                val dist = (13 + (k % 3) * 5) * flown
                val p = at + Offset(cos(turn) * dist, sin(turn) * dist)
                drawCircle(P.spark, if (k % 2 == 1) 1.3f else 1.8f, p, alpha)
            }
        }
    }

    /**
     * The sweep's arm (.ld-bf-sweep): a light trailing behind a bright line with a glowing tip,
     * turned to [angle], at [alpha].
     */
    fun drawArm(scope: DrawScope, angle: Float, alpha: Float) {
        if (alpha <= 0f) return
        with(scope) {
            faded(alpha) {
                rotate(angle - 90f, CENTRE_POINT) {
                    drawCircle(
                        SWEEP_LIGHT,
                        (ARM_IN + ARM_OUT) / 2,
                        CENTRE_POINT,
                        style = SWEEP_BAND,
                    )
                }
                rotate(angle, CENTRE_POINT) {
                    val top = Offset(CENTRE, CENTRE - ARM_OUT)
                    val bottom = Offset(CENTRE, CENTRE - ARM_REACH)
                    val arm = Rect(top.x - 1f, top.y, top.x + 1f, bottom.y)
                    drawIntoCanvas {
                        it.drawRect(arm, glowPaint(P.handGlow, 10f))
                        it.drawRect(arm, glowPaint(P.handGlow.copy(alpha = .6f), 22f))
                    }
                    drawRect(
                        Brush.verticalGradient(
                            listOf(P.armTop, P.nowBlue.copy(alpha = .2f)),
                            top.y,
                            bottom.y,
                        ),
                        arm.topLeft,
                        arm.size,
                    )
                    val tip = Offset(CENTRE, top.y + 1f)
                    drawIntoCanvas {
                        it.drawCircle(tip, 4f, glowPaint(P.nowBlue, 12f))
                        it.drawCircle(tip, 4f, glowPaint(P.handGlow, 26f))
                    }
                    drawCircle(P.armTop, 4f, tip)
                }
            }
        }
    }
}

/**
 * Draws [block] at [alpha] and CSS brightness [brightness] (its colours multiplied), as one group.
 */
internal inline fun DrawScope.lit(alpha: Float, brightness: Float, block: DrawScope.() -> Unit) {
    if (brightness == 1f) return faded(alpha, block)
    if (alpha <= 0f) return
    val b = brightness
    val paint =
        Paint().also {
            it.alpha = alpha.coerceAtMost(1f)
            it.colorFilter =
                ColorFilter.colorMatrix(
                    ColorMatrix(
                        floatArrayOf(
                            b,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            b,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            b,
                            0f,
                            0f,
                            0f,
                            0f,
                            0f,
                            1f,
                            0f,
                        )
                    )
                )
        }
    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(Offset.Zero, size), paint)
    block()
    canvas.restore()
}

/** A wedge from the centre between two angles, wide enough to hold a band and its glow. */
private fun wedge(a0: Float, a1: Float): Path =
    Path().apply {
        moveTo(CENTRE, CENTRE)
        arcTo(box(CENTRE), a0 - 90f, a1 - a0, false)
        close()
    }

private fun strokeGlow(color: Color, blur: Float, width: Float) =
    glowPaint(color, blur).apply {
        style = PaintingStyle.Stroke
        strokeWidth = width
    }

private val HANDLE_EDGE = Stroke(2.5f)

/** drop-shadow(0 1.5px 1.5px rgba(0,0,0,.7)) under the ring, edge included. */
private val RING_SHADOW = glowPaint(Color.Black.copy(alpha = .7f), 1.5f)

/** drop-shadow(0 -1px 1px rgba(255,255,255,.12)) under the pip. */
private val PIP_LIGHT = glowPaint(Color.White.copy(alpha = .12f), 1f)

/** Unblurred before Android 9, the ring's shadow would be a hard dark crescent: none there. */
private val HANDLE_SHADOWS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
private val TRAIL =
    Stroke(2f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 5f)))
private val GHOST_DASH = PathEffect.dashPathEffect(floatArrayOf(3f, 4f))

/*
 * The sweep (.ld-bf-sweep): a disc inset 6.1 % of the dial, so 158 units across from its centre,
 * shown from 46 % of its corner distance out (103 units); the arm runs 27 % of the disc down from
 * its edge. Its light is a conic gradient trailing 75 degrees behind the arm.
 */
private const val ARM_OUT = 158f
private const val ARM_IN = 103f
private const val ARM_REACH = ARM_OUT - .27f * 2 * ARM_OUT
private val SWEEP_BAND = Stroke(ARM_OUT - ARM_IN)
private val SWEEP_LIGHT =
    Brush.sweepGradient(
        0f to Color.Transparent,
        285f / 360 to Color.Transparent,
        300f / 360 to Color(0x0D38BDF8),
        357f / 360 to Color(0x4D7DD3FC),
        1f to Color(0x8CE0F2FE),
        center = Offset(CENTRE, CENTRE),
    )
