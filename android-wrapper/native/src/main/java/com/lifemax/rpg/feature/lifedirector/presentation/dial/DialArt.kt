package com.lifemax.rpg.feature.lifedirector.presentation.dial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.pixelIconPath
import com.lifemax.rpg.core.designsystem.glowPaint
import com.lifemax.rpg.feature.lifedirector.domain.ArcStyle
import com.lifemax.rpg.feature.lifedirector.domain.BlockKind
import com.lifemax.rpg.feature.lifedirector.domain.CurrentRing
import com.lifemax.rpg.feature.lifedirector.domain.DialArc
import com.lifemax.rpg.feature.lifedirector.domain.DialRadii
import com.lifemax.rpg.feature.lifedirector.domain.DirectorDial
import com.lifemax.rpg.feature.lifedirector.domain.HourWord
import com.lifemax.rpg.feature.lifedirector.domain.SessionState
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialGeometry.CENTRE
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialGeometry.point
import java.text.BreakIterator
import java.text.BreakIterator.DONE
import kotlin.math.floor
import kotlin.math.min

/*
 * The dial's drawing, prepared in its 360-unit square whenever its data changes and replayed by the
 * canvas without allocating: a list of steps over prepared paths, brushes and glyphs. The face
 * depends only on the hour words, the plan on the arcs and words, and what moves with the time (the
 * past greyed out and the current block's ring) on the hand. Shapes, sizes and colours follow
 * faceHtml and dialHtml in logic.js and style.css.
 */

private typealias Step = DrawScope.() -> Unit

/** Draws its steps in order, in dial units. */
internal open class DialLayer(private val steps: List<Step>) {
    fun draw(scope: DrawScope) {
        for (i in steps.indices) steps[i](scope)
    }
}

/** Bezel, face, depth rings, ticks, hour words, sun and moon, ring tracks. */
internal class DialFace(radii: DialRadii, hourWords: List<HourWord>, text: TextMeasurer) :
    DialLayer(
        buildList<Step> {
            disc(176f, P.bezel)
            outline(176f, 1f, P.bezelStroke)
            disc(158f, P.face)
            outline(149f, 15f, P.chapter)
            edge(157.5f)
            edge(140.5f)
            outline(radii.outer, radii.outerWidth + 3, P.channel)
            edge(119.5f)
            outline(115.5f, 1f, P.divider)
            outline(radii.inner, radii.innerWidth + 3, P.channel)
            edge(112.5f)
            edge(89.5f)
            disc(86.5f, P.hub)
            edge(87.5f)
            outline(82f, 1f, P.lip)
            ticks(radii)
            engrave(radii, hourWords, Glyphs(text))
            outline(radii.outer, radii.outerWidth, P.outerTrack)
            outline(radii.inner, radii.innerWidth, P.innerTrack)
            sunAndMoon(hourWords)
        }
    )

/** The day's arcs with their glows, edges and icons, and the words written along them. */
internal class DialPlan(dial: DirectorDial, text: TextMeasurer) :
    DialLayer(
        buildList<Step> {
            val glyphs = Glyphs(text)
            dial.arcs.filter { it.outer }.forEach { arc(it, dial.radii) }
            for (w in dial.words) {
                val color = wordColor(w.kind, w.inner)
                val style = if (w.inner) innerWord else outerWord
                glyphRun(w.radius, w.a0, w.a1, glyphs.of(w.text, style), w.flip)?.let { run ->
                    add { run.draw(this, color) }
                }
            }
            dial.arcs.filterNot { it.outer }.forEach { arc(it, dial.radii) }
        }
    )

/** The part of today already gone, greyed out, and how much of the current block is left. */
internal class DialNow(radii: DialRadii, hand: Float?, ring: CurrentRing?) :
    DialLayer(
        buildList<Step> {
            if (hand != null && hand > 1f) {
                val outer = radii.outer + radii.outerWidth / 2 + 2
                val inner = radii.inner - radii.innerWidth / 2 - 2
                val (x, y) = point(inner, hand)
                val sector =
                    Path().apply {
                        arcTo(box(outer), -90f, hand, true)
                        lineTo(x, y)
                        arcTo(box(inner), hand - 90f, -hand, false)
                        close()
                    }
                add { drawPath(sector, P.past) }
            }
            if (ring != null) {
                outline(ring.radius, 3f, P.ringTrack)
                if (ring.frac < .997f) {
                    val color =
                        when {
                            ring.warn -> P.warn
                            ring.kind == BlockKind.FREE -> P.ringFree
                            else -> P.of(ring.kind)
                        }
                    val arc = arcPath(ring.radius, ring.frac * 360f, 360f)
                    glow(arc, 3.5f, color, if (ring.warn) 5f else 4f, StrokeCap.Round)
                    stroke(arc, Stroke(3.5f, cap = StrokeCap.Round), color, .9f)
                }
            }
        }
    )

/** The hand, drawn pointing at midnight; its layer turns it to the time. */
internal val handLayer =
    DialLayer(
        buildList<Step> {
            val line =
                Path().apply {
                    moveTo(CENTRE, CENTRE - 84f)
                    lineTo(CENTRE, CENTRE - 156f)
                }
            glow(line, 2f, P.handGlow, 4f, StrokeCap.Round)
            stroke(line, Stroke(2f, cap = StrokeCap.Round), P.hand)
            val tip = Path().apply { addOval(Rect(Offset(CENTRE, CENTRE - 156f), 3.6f)) }
            glow(tip, null, P.handGlow, 5f)
            add { drawPath(tip, P.handTip) }
        }
    )

/** The rim of the next session's halo; its layer pulses it. The arc itself covers the middle. */
internal class DialHalo(arc: DialArc, radii: DialRadii) :
    DialLayer(
        buildList<Step> {
            for (side in floatArrayOf(-1f, 1f)) {
                val r = radii.inner + side * (radii.innerWidth / 2 + 2)
                stroke(arcPath(r, arc.a0, arc.a1), Stroke(4f), P.halo)
            }
        }
    )

/**
 * Dial text is laid out in units and drawn scaled up, so its glyphs are placed linearly, unhinted.
 */
private val dialText = TextStyle(textMotion = TextMotion.Animated)
private val outerWord =
    dialText.copy(fontSize = 10.5.sp, fontWeight = FontWeight(750), letterSpacing = .01.em)
private val innerWord =
    dialText.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = .01.em)
private val hourWord = dialText.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
private val hourWordMain = dialText.copy(fontSize = 11.sp, fontWeight = FontWeight(750))

private fun wordColor(kind: BlockKind, inner: Boolean): Color =
    when {
        inner -> if (kind == BlockKind.REST) P.wordRest else P.wordFree
        kind == BlockKind.WORK -> P.wordDark
        kind == BlockKind.MAINT -> P.wordOnMaint
        kind == BlockKind.COMMUTE -> P.wordOnCommute
        else -> Color.White
    }

private fun MutableList<Step>.arc(arc: DialArc, radii: DialRadii) {
    val r = if (arc.outer) radii.outer else radii.inner
    val width = if (arc.outer) radii.outerWidth else radii.innerWidth
    val path = arcPath(r, arc.a0, arc.a1)
    when (arc.style) {
        ArcStyle.BAND -> {
            val color = P.of(arc.kind)
            if (arc.kind == BlockKind.SLEEP || arc.kind == BlockKind.WORK)
                glow(path, width, color.copy(alpha = .5f), 5f)
            val alpha =
                when (arc.kind) {
                    BlockKind.COMMUTE -> .95f
                    BlockKind.MAINT -> .7f
                    else -> 1f
                }
            stroke(path, Stroke(width), color, alpha)
            surface(arc, r, width, 1f)
        }
        ArcStyle.THIN -> stroke(path, Stroke(5f), P.rest, .6f)
        ArcStyle.APPT -> {
            glow(path, width, P.appointment.copy(alpha = .45f), 5f)
            stroke(path, Stroke(width), P.appointment)
            surface(arc, r, width, 1f)
        }
        ArcStyle.MEAL -> {
            glow(path, width, P.meal.copy(alpha = .45f), 5f)
            stroke(path, Stroke(width), P.meal)
            surface(arc, r, width, 1f)
            icon(arc, r, P.iconOnMeal)
        }
        ArcStyle.SESSION -> session(arc, path, r, width)
    }
}

private fun MutableList<Step>.session(arc: DialArc, path: Path, r: Float, width: Float) {
    when (arc.state) {
        SessionState.DONE -> stroke(path, Stroke(width), P.done)
        SessionState.PART -> stroke(path, Stroke(width, pathEffect = dashes(7f, 4f)), P.task)
        SessionState.OFF -> stroke(path, Stroke(width, pathEffect = dashes(3f, 3f)), P.block, .45f)
        SessionState.PLAN,
        SessionState.NONE -> {
            if (arc.next) glow(path, width, P.nextGlow, 9f)
            else glow(path, width, P.task.copy(alpha = .55f), 6f)
            stroke(path, Stroke(width), P.task)
        }
    }
    if (arc.state != SessionState.OFF && arc.state != SessionState.PART)
        surface(arc, r, width, if (arc.state == SessionState.DONE) .45f else 1f)
    if (iconSize(arc, r) == 0f) return
    val (x, y) = point(r, (arc.a0 + arc.a1) / 2)
    when (arc.state) {
        SessionState.DONE -> {
            val check =
                Path().apply {
                    moveTo(x - 4.5f, y)
                    relativeLineTo(3f, 3f)
                    relativeLineTo(6f, -6.5f)
                }
            stroke(check, Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round), P.check)
        }
        SessionState.OFF -> {
            val cross =
                Path().apply {
                    moveTo(x - 3.5f, y - 3.5f)
                    relativeLineTo(7f, 7f)
                    relativeMoveTo(0f, -7f)
                    relativeLineTo(-7f, 7f)
                }
            stroke(cross, Stroke(2f, cap = StrokeCap.Round), P.cross)
        }
        else -> icon(arc, r, P.iconOnSession)
    }
}

/** The arc's icon, upright at its middle: up to 12 units, none on an arc under 9 units long. */
private fun MutableList<Step>.icon(arc: DialArc, r: Float, color: Color) {
    val size = iconSize(arc, r)
    if (size == 0f || arc.icon.isEmpty()) return
    val (x, y) = point(r, (arc.a0 + arc.a1) / 2)
    val path = pixelIconPath(arc.icon, size, Offset(x - size / 2, y - size / 2))
    add { drawPath(path, color) }
}

private fun iconSize(arc: DialArc, r: Float): Float {
    val chord = DialGeometry.length(r, arc.a0, arc.a1)
    return if (chord < 9f) 0f else min(12f, floor(chord - 1))
}

/** The lit inner and outer edges that give an arc its depth. */
private fun MutableList<Step>.surface(arc: DialArc, r: Float, width: Float, alpha: Float) {
    for (edgeRadius in floatArrayOf(r - width / 2 + .6f, r + width / 2 - .6f)) {
        val path = arcPath(edgeRadius, arc.a0, arc.a1)
        val brush = edgeBrush(path.getBounds())
        add { drawPath(path, brush, alpha, HAIRLINE) }
    }
}

private fun MutableList<Step>.ticks(radii: DialRadii) {
    val quarter = Path()
    val hour = Path()
    val six = Path()
    for (k in 0 until 96) {
        val a = k * 3.75f
        val (path, from) =
            when {
                k % 24 == 0 -> six to radii.tickIn - 4
                k % 4 == 0 -> hour to radii.tickIn
                else -> quarter to radii.tickIn + 3
            }
        val (x0, y0) = point(from, a)
        val (x1, y1) = point(radii.tickOut, a)
        path.moveTo(x0, y0)
        path.lineTo(x1, y1)
    }
    stroke(quarter, HAIRLINE, P.tick)
    stroke(hour, Stroke(1.4f), P.tickHour)
    stroke(six, Stroke(2f), P.tickSix)
}

/**
 * Midnight, 6 AM, Noon and 6 PM sit straight at the edges; the hours between are engraved along the
 * bezel, turned upright on the lower half.
 */
private fun MutableList<Step>.engrave(radii: DialRadii, words: List<HourWord>, glyphs: Glyphs) {
    for ((hour, word) in words) {
        if (hour % 6 == 0) {
            val layout = glyphs.line(word, hourWordMain)
            val w = layout.size.width.toFloat()
            val h = layout.size.height.toFloat()
            val centre =
                when (hour) {
                    0 -> Offset(CENTRE, 13f)
                    6 -> Offset(359f - w / 2, CENTRE)
                    12 -> Offset(CENTRE, 347f)
                    else -> Offset(1f + w / 2, CENTRE)
                }
            val topLeft = centre - Offset(w / 2, h / 2)
            add { drawText(layout, P.hourWordMain, topLeft) }
        } else {
            val angle = hour * 15f
            val flip = angle > 90f && angle < 270f
            glyphRun(radii.hourWords, angle - 13f, angle + 13f, glyphs.of(word, hourWord), flip)
                ?.let { run -> add { run.draw(this, P.hourWord) } }
        }
    }
}

/** A small moon beside Midnight and sun beside Noon, placed by the length of the words. */
private fun MutableList<Step>.sunAndMoon(words: List<HourWord>) {
    fun beside(hour: Int) =
        CENTRE - ((words.firstOrNull { it.hour == hour }?.word?.length ?: 0) * 3.3f + 10f) - 2f
    val mx = beside(0)
    val moon =
        Path.combine(
            PathOperation.Difference,
            Path().apply { addOval(Rect(Offset(mx + 1.6f, 13f), 5f)) },
            Path().apply { addOval(Rect(Offset(mx + 3.3f, 12.3f), 4.66f)) },
        )
    add { drawPath(moon, P.moon) }
    val sx = beside(12)
    val rays = Path()
    for (k in 0 until 8) {
        val (x0, y0) = point(4.3f, k * 45f) - Offset(CENTRE, CENTRE)
        val (x1, y1) = point(6f, k * 45f) - Offset(CENTRE, CENTRE)
        rays.moveTo(sx + x0, 347f + y0)
        rays.lineTo(sx + x1, 347f + y1)
    }
    val rayStroke = Stroke(1.3f, cap = StrokeCap.Round)
    add {
        drawCircle(P.sun, 2.6f, Offset(sx, 347f))
        drawPath(rays, P.sun, style = rayStroke)
    }
}

/** Glyphs placed one by one along an arc, each centred on the ring and turned to its angle. */
private class GlyphRun(
    private val word: Spelled,
    private val angles: FloatArray,
    radius: Float,
    private val flip: Boolean,
) {
    private val onRing = Offset(CENTRE, CENTRE - radius)
    private val topLefts =
        word.glyphs.mapIndexed { i, glyph ->
            onRing - Offset(word.advances[i] / 2, glyph.size.height / 2f)
        }

    fun draw(scope: DrawScope, color: Color) =
        with(scope) {
            for (i in angles.indices) {
                val glyph = word.glyphs[i]
                rotate(angles[i], CENTRE_POINT) {
                    if (flip) rotate(180f, onRing) { drawText(glyph, color, topLefts[i]) }
                    else drawText(glyph, color, topLefts[i])
                }
            }
        }
}

/**
 * Glyphs along [radius] from [a0] to [a1], or null when they do not fit: the web chose the words
 * with an estimate of their width, and the measured glyphs have the last word.
 */
private fun glyphRun(radius: Float, a0: Float, a1: Float, word: Spelled, flip: Boolean): GlyphRun? {
    val angles = DialGeometry.alongArc(radius, a0, a1, word.advances, flip) ?: return null
    return GlyphRun(word, angles, radius, flip)
}

/** A word as separate glyphs, each with the advance it has within the word (spaces and kerning). */
private class Spelled(val glyphs: List<TextLayoutResult>, val advances: FloatArray)

/** Text measured in dial units (one unit is one pixel at density 1), each glyph once. */
private class Glyphs(private val measurer: TextMeasurer) {
    private val cache = HashMap<Pair<String, TextStyle>, TextLayoutResult>()

    fun of(text: String, style: TextStyle): Spelled {
        val whole = line(text, style)
        val clusters = BreakIterator.getCharacterInstance().apply { setText(text) }
        val edges = generateSequence(clusters.first()) { clusters.next().takeIf { it != DONE } }
        val spans = edges.zipWithNext().toList()
        return Spelled(
            spans.map { (start, end) ->
                val glyph = text.substring(start, end)
                cache.getOrPut(glyph to style) { line(glyph, style) }
            },
            FloatArray(spans.size) {
                val (start, end) = spans[it]
                whole.getHorizontalPosition(end, true) - whole.getHorizontalPosition(start, true)
            },
        )
    }

    fun line(text: String, style: TextStyle): TextLayoutResult =
        measurer.measure(text, style, softWrap = false, maxLines = 1, density = UNITS)
}

private val UNITS = Density(1f, 1f)
private val CENTRE_POINT = Offset(CENTRE, CENTRE)
private val HAIRLINE = Stroke(1f)

private fun box(r: Float) = Rect(CENTRE_POINT, r)

private fun arcPath(r: Float, a0: Float, a1: Float): Path =
    Path().apply {
        if (a1 - a0 >= 359.95f) addOval(box(r)) else arcTo(box(r), a0 - 90f, a1 - a0, true)
    }

private fun dashes(on: Float, off: Float) = PathEffect.dashPathEffect(floatArrayOf(on, off))

private fun MutableList<Step>.stroke(path: Path, stroke: Stroke, color: Color, alpha: Float = 1f) =
    add {
        drawPath(path, color, alpha, stroke)
    }

private fun MutableList<Step>.outline(r: Float, width: Float, color: Color) {
    val stroke = Stroke(width)
    add { drawCircle(color, r, CENTRE_POINT, style = stroke) }
}

/** A disc filled with a linear gradient across its box, as the web's SVG gradients run. */
private fun MutableList<Step>.disc(r: Float, stops: List<Pair<Float, Color>>) {
    val brush = boxGradient(box(r), stops)
    add { drawCircle(brush, r, CENTRE_POINT) }
}

private fun MutableList<Step>.edge(r: Float) {
    val brush = edgeBrush(box(r))
    add { drawCircle(brush, r, CENTRE_POINT, style = HAIRLINE) }
}

private fun edgeBrush(box: Rect) = boxGradient(box, P.edge)

/** The web's gradient direction: from the box's top left to 35% across its bottom. */
private fun boxGradient(box: Rect, stops: List<Pair<Float, Color>>) =
    Brush.linearGradient(
        *stops.toTypedArray(),
        start = box.topLeft,
        end = Offset(box.left + box.width * .35f, box.bottom),
    )

/**
 * A CSS drop-shadow(0 0 [blur]) under [path]: the same shape blurred in [color]. [width] is the
 * stroke's width, or null to fill.
 */
private fun MutableList<Step>.glow(
    path: Path,
    width: Float?,
    color: Color,
    blur: Float,
    cap: StrokeCap = StrokeCap.Butt,
) {
    val paint =
        glowPaint(color, blur).apply {
            if (width != null) {
                style = PaintingStyle.Stroke
                strokeWidth = width
                strokeCap = cap
            }
        }
    add { drawIntoCanvas { it.drawPath(path, paint) } }
}
