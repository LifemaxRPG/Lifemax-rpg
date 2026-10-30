package com.lifemax.rpg.feature.lifedirector.presentation.dial

import androidx.compose.ui.geometry.Offset
import com.lifemax.rpg.feature.lifedirector.domain.DialArc
import com.lifemax.rpg.feature.lifedirector.domain.DialRadii
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The dial's coordinate system, the web's (logic.js DIAL and polar): a 360-unit square centred on
 * (180, 180), angles in degrees clockwise from midnight at the top. A whole turn is the day.
 */
object DialGeometry {
    const val SIDE = 360f
    const val CENTRE = SIDE / 2

    /** The web's margin, in units, between words and the ends of their arc. */
    private const val WORD_MARGIN = 5f

    /** The shortest tap target, in units (one is about a dp), as Android asks of a touch target. */
    private const val TARGET = 48f

    fun point(radius: Float, angle: Float): Offset {
        val t = (angle - 90f) * DEG
        return Offset(CENTRE + radius * cos(t), CENTRE + radius * sin(t))
    }

    /** The angle of [p], from 0 up to 360. */
    fun angleAt(p: Offset): Float {
        val a = atan2(p.y - CENTRE, p.x - CENTRE) / DEG + 90f
        return (a + 360f) % 360f
    }

    fun radiusAt(p: Offset): Float = hypot(p.x - CENTRE, p.y - CENTRE)

    /** Units along radius [radius] between two angles. */
    fun length(radius: Float, a0: Float, a1: Float): Float = (a1 - a0) * DEG * radius

    /**
     * The angle at the middle of each glyph of [advances] (widths in units), written centred along
     * radius [radius] from [a0] to [a1]. A [flip]ped run goes anticlockwise so it reads upright on
     * the lower half. Null when the glyphs do not fit between the web's margins.
     */
    fun alongArc(
        radius: Float,
        a0: Float,
        a1: Float,
        advances: FloatArray,
        flip: Boolean,
    ): FloatArray? {
        val room = length(radius, a0, a1)
        val width = advances.sum()
        if (width > room - 2 * WORD_MARGIN) return null
        var along = (room - width) / 2
        return FloatArray(advances.size) { i ->
            val middle = along + advances[i] / 2
            along += advances[i]
            val turn = middle / radius / DEG
            if (flip) a1 - turn else a0 + turn
        }
    }

    /**
     * The tappable arc at [p], found by ring and angle. Every arc is a target at least [TARGET]
     * units long, so one shorter than that is also reached from just beside it; across its ring, a
     * tap counts up to half a target from the ring's middle.
     */
    fun arcAt(arcs: List<DialArc>, radii: DialRadii, p: Offset): DialArc? {
        val r = radiusAt(p)
        val split = (radii.inner + radii.outer) / 2
        val outer =
            when {
                r >= split && r <= radii.outer + TARGET / 2 -> true
                r < split && r >= radii.inner - TARGET / 2 -> false
                else -> return null
            }
        val ring = if (outer) radii.outer else radii.inner
        val a = angleAt(p)
        return arcs
            .filter { it.outer == outer && it.tappable }
            .map { it to length(ring, 0f, degreesOutside(a, it.a0, it.a1)) }
            .filter { (arc, gap) -> gap <= reach(ring, arc) }
            .minByOrNull { (_, gap) -> gap }
            ?.first
    }

    /** How far beside [arc] a tap still reaches it: what it lacks of a whole target, each side. */
    private fun reach(ring: Float, arc: DialArc): Float =
        ((TARGET - length(ring, arc.a0, arc.a1)) / 2).coerceAtLeast(0f)

    /** How far [a] lies outside [a0, a1], going the short way round. */
    private fun degreesOutside(a: Float, a0: Float, a1: Float): Float =
        if (a in a0..a1) 0f else min(turn(a, a0), turn(a, a1))

    private fun turn(a: Float, b: Float): Float = abs(a - b).let { min(it, 360f - it) }

    private const val DEG = (PI / 180).toFloat()
}
