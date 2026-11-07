package com.lifemax.rpg.feature.lifedirector.presentation.dial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
internal class DialFace(
    radii: DialRadii,
    hourWords: List<HourWord>,
    text: TextMeasurer,
    includeBezel: Boolean = true,
) :
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
            if (includeBezel) {
                ticks(radii)
                engrave(radii, hourWords, Glyphs(text))
            }
            outline(radii.outer, radii.outerWidth, P.outerTrack)
            outline(radii.inner, radii.innerWidth, P.innerTrack)
            if (includeBezel) sunAndMoon(hourWords, Glyphs(text))
        }
    )

/** The engraved watch bezel rotates independently of the schedule and current-time hand. */
internal class DialBezel(radii: DialRadii, hourWords: List<HourWord>, text: TextMeasurer) :
    DialLayer(
        buildList<Step> {
            ticks(radii)
            engrave(radii, hourWords, Glyphs(text))
            sunAndMoon(hourWords, Glyphs(text))
        }
    )

/**
 * One arc's drawing, with its glow, edges and icon, so a build can move, fade or light it alone.
 */
internal class ArcArt(val arc: DialArc, radii: DialRadii) :
    DialLayer(buildList<Step> { arc(arc, radii) }) {
    /** Drawn as a ghost (a session lifted off the dial) when it is the session [ghost]. */
    fun draw(scope: DrawScope, ghost: String?) {
        if (ghost != null && arc.tap?.id == ghost)
            scope.faded(GHOST_ALPHA) { this@ArcArt.draw(this) }
        else draw(scope)
    }
}

/**
 * The day's arcs and the words written along them, in three groups drawn in this order: the outer
 * ring, the words, the inner ring. A held handle dims each group as the web's .ld-dial.drag does.
 */
internal class DialPlan(dial: DirectorDial, text: TextMeasurer) {
    val outer = dial.arcs.filter { it.outer }.map { ArcArt(it, dial.radii) }
    val words =
        DialLayer(
            buildList<Step> {
                val glyphs = Glyphs(text)
                for (w in dial.words) {
                    val color = wordColor(w.kind, w.inner)
                    val style = if (w.inner) innerWord else outerWord
                    glyphRun(w.radius, w.a0, w.a1, glyphs.of(w.text, style), w.flip)?.let { run ->
                        add { run.draw(this, color) }
                    }
                }
            }
        )
    val inner = dial.arcs.filterNot { it.outer }.map { ArcArt(it, dial.radii) }

    fun draw(
        scope: DrawScope,
        outerAlpha: Float = 1f,
        wordsAlpha: Float = 1f,
        innerAlpha: Float = 1f,
        ghost: String? = null,
    ) {
        scope.faded(outerAlpha) { for (i in outer.indices) outer[i].draw(this, ghost) }
        scope.faded(wordsAlpha) { words.draw(this) }
        scope.faded(innerAlpha) { for (i in inner.indices) inner[i].draw(this, ghost) }
    }
}

/** Draws [block] at [alpha] as one group, as CSS opacity does; directly when it is opaque. */
internal inline fun DrawScope.faded(alpha: Float, block: DrawScope.() -> Unit) {
    if (alpha >= 1f) block()
    else if (alpha > 0f) {
        val canvas = drawContext.canvas
        canvas.saveLayer(Rect(Offset.Zero, size), Paint().also { it.alpha = alpha })
        block()
        canvas.restore()
    }
}

/** The part of today already gone, greyed out, and how much of the current block is left. */
internal class DialNow(radii: DialRadii, hand: Float?, ring: CurrentRing?) {
    private val past =
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
            }
        )
    private val current =
        DialLayer(
            buildList<Step> {
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
    private val revealRadius = (ring?.radius ?: 84f) + 12f

    fun draw(scope: DrawScope, ringReveal: Float = 1f) {
        past.draw(scope)
        if (ringReveal >= 1f) current.draw(scope)
        else if (ringReveal > 0f) {
            val mask =
                Path().apply {
                    moveTo(180f, 180f)
                    arcTo(box(revealRadius), -90f, 360f * ringReveal, false)
                    close()
                }
            with(scope) { clipPath(mask) { current.draw(this) } }
        }
    }
}

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
        val path =
            when {
                k % 24 == 0 -> six
                k % 4 == 0 -> hour
                else -> quarter
            }
        val (x0, y0) = point(tickFrom(radii, k), k * 3.75f)
        val (x1, y1) = point(radii.tickOut, k * 3.75f)
        path.moveTo(x0, y0)
        path.lineTo(x1, y1)
    }
    stroke(quarter, HAIRLINE, P.tick)
    stroke(hour, Stroke(1.4f), P.tickHour)
    stroke(six, Stroke(2f), P.tickSix)
}

/** Where tick [k] starts: the six-hour ticks reach furthest in, the quarter hours least. */
private fun tickFrom(radii: DialRadii, k: Int): Float =
    when {
        k % 24 == 0 -> radii.tickIn - 4
        k % 4 == 0 -> radii.tickIn
        else -> radii.tickIn + 3
    }

/** Tick [k] lit by the build's arm: drawn over the face in the flash colour at [alpha]. */
internal fun DrawScope.flashTick(radii: DialRadii, k: Int, alpha: Float) {
    val width =
        when {
            k % 24 == 0 -> 2f
            k % 4 == 0 -> 1.4f
            else -> 1f
        }
    drawLine(
        P.flash,
        point(tickFrom(radii, k), k * 3.75f),
        point(radii.tickOut, k * 3.75f),
        width,
        alpha = alpha,
    )
}

/**
 * The cardinal hour labels follow the bezel. The side labels remain horizontal for readability,
 * while Midnight and Noon use the same curved glyph layout as the other engraved hours.
 */
private fun MutableList<Step>.engrave(radii: DialRadii, words: List<HourWord>, glyphs: Glyphs) {
    for ((hour, word) in words) {
        if (hour == 0 || hour == 12) {
            val centreAngle = if (hour == 0) 0f else 180f
            glyphRun(
                    167f,
                    centreAngle - 17f,
                    centreAngle + 17f,
                    glyphs.of(word, hourWordMain),
                    hour == 12,
                )
                ?.let { run -> add { run.draw(this, P.hourWordMain) } }
        } else if (hour % 6 == 0) {
            val angle = hour * 15f
            glyphRun(167f, angle - 17f, angle + 17f, glyphs.of(word, hourWordMain), hour == 18)
                ?.let { run -> add { run.draw(this, P.hourWordMain) } }
        } else {
            val angle = hour * 15f
            val flip = angle > 90f && angle < 270f
            glyphRun(radii.hourWords, angle - 13f, angle + 13f, glyphs.of(word, hourWord), flip)
                ?.let { run -> add { run.draw(this, P.hourWord) } }
        }
    }
}

/** The moon and sun follow the same bezel arc, just before their respective curved labels. */
private fun MutableList<Step>.sunAndMoon(words: List<HourWord>, glyphs: Glyphs) {
    fun iconAngle(hour: Int): Float {
        val word = words.firstOrNull { it.hour == hour }?.word.orEmpty()
        val width = glyphs.of(word, hourWordMain).advances.sum()
        val halfAngle = Math.toDegrees((width / (2f * 167f)).toDouble()).toFloat()
        val gapAngle = Math.toDegrees((11f / 167f).toDouble()).toFloat()
        return if (hour == 0) -halfAngle - gapAngle else 180f + halfAngle + gapAngle
    }
    val moonAt = point(167f, iconAngle(0))
    val moon =
        Path.combine(
            PathOperation.Difference,
            Path().apply { addOval(Rect(moonAt + Offset(-2.4f, -2.5f), 5f)) },
            Path().apply { addOval(Rect(moonAt + Offset(-0.7f, -3.2f), 4.66f)) },
        )
    add { drawPath(moon, P.moon) }
    val sunAt = point(167f, iconAngle(12))
    val rays = Path()
    for (k in 0 until 8) {
        val (x0, y0) = point(4.3f, k * 45f) - Offset(CENTRE, CENTRE)
        val (x1, y1) = point(6f, k * 45f) - Offset(CENTRE, CENTRE)
        rays.moveTo(sunAt.x + x0, sunAt.y + y0)
        rays.lineTo(sunAt.x + x1, sunAt.y + y1)
    }
    val rayStroke = Stroke(1.3f, cap = StrokeCap.Round)
    add {
        drawCircle(P.sun, 2.6f, sunAt)
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
internal val CENTRE_POINT = Offset(CENTRE, CENTRE)
private val HAIRLINE = Stroke(1f)

internal fun box(r: Float) = Rect(CENTRE_POINT, r)

internal fun arcPath(r: Float, a0: Float, a1: Float): Path =
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
