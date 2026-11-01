package com.lifemax.rpg.feature.lifedirector.presentation.dial

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.lifedirector.domain.BlockKind
import com.lifemax.rpg.feature.lifedirector.domain.DialTour
import com.lifemax.rpg.feature.lifedirector.domain.GuideStep
import com.lifemax.rpg.feature.lifedirector.domain.GuideTag
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.domain.Sections
import com.lifemax.rpg.feature.lifedirector.presentation.GuideRun
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.rememberPulse

/**
 * A step of Today's guide shown on the dial (the page's `.ld-dial[data-tour]`): [step] says how the
 * dial looks and which name tags it wears; [still] when motion is reduced, so nothing fades or
 * pulses.
 */
@Immutable class DialTourRun(val step: GuideStep, val still: Boolean)

/** The dial's part of the guide step being shown ([run]), when it is Today's guide; else none. */
internal fun dialTour(p: LifeDirectorProjection, run: GuideRun?): DialTourRun? {
    val g = p.guide ?: return null
    if (run == null || g.key != Sections.TODAY || run.key != g.key) return null
    return g.steps.getOrNull(run.step)?.let { DialTourRun(it, p.reducedMotion) }
}

/**
 * The step's look ([DialTour.of] its mode), each layer fading to its opacity over the page's 300
 * ms, at once when still. No step: the dial as it is.
 */
@Composable
internal fun rememberTourLook(tour: DialTourRun?): DialTour {
    val target = tour?.let { DialTour.of(it.step.mode) } ?: DialTour.NONE
    val spec = if (tour == null || tour.still) snap<Float>() else tween(FADE_MS)
    @Composable
    fun fade(v: Float, label: String) = animateFloatAsState(v, spec, label = label).value
    return DialTour(
        outer = fade(target.outer, "tour outer"),
        words = fade(target.words, "tour words"),
        inner = fade(target.inner, "tour inner"),
        centre = fade(target.centre, "tour centre"),
        handles = fade(target.handles, "tour handles"),
        pulse = target.pulse,
    )
}

/**
 * The handle rings' pulse on the handles step (ld-hd-pulse: up to 1.35 times their size and back
 * over 1.4 s), read while drawing; 1 and still under reduced motion, when the dial does not
 * [animate], or on any other step.
 */
@Composable
internal fun rememberTourPulse(look: DialTour, tour: DialTourRun?, animate: Boolean): State<Float> {
    val phase = rememberPulse(look.pulse && animate && tour?.still == false, PULSE_MS)
    return remember(phase) {
        object : State<Float> {
            override val value: Float
                get() = 1f + PULSE_GROWTH * phase.value
        }
    }
}

/**
 * The step's name tags (.ld-tl-p): pills in the guide's dark fill, edged in the colour of what they
 * name, the words in white, each where the page placed it in the dial's square. They fade in one
 * after another as the page's do; drawn settled under reduced motion and in previews.
 */
@Composable
internal fun TourTags(tags: List<GuideTag>, still: Boolean, animate: Boolean = true) {
    if (tags.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val pills = remember(tags) { tags.map { TagPill(it, measurer) } }
    val settled = still || !animate || LocalInspectionMode.current
    val total = (FADE_IN_MS + STAGGER_MS * (tags.size - 1)).toFloat()
    val clock = remember(tags) { Animatable(if (settled) total else 0f) }
    LaunchedEffect(tags, settled) {
        if (settled) clock.snapTo(total)
        else clock.animateTo(total, tween(total.toInt(), easing = LinearEasing))
    }
    Canvas(Modifier.fillMaxSize()) {
        inUnits {
            pills.forEachIndexed { k, pill ->
                val a = ((clock.value - STAGGER_MS * k) / FADE_IN_MS).coerceIn(0f, 1f)
                if (a > 0f) pill.draw(this, a)
            }
        }
    }
}

/**
 * One tag laid out in dial units: its words measured once, the pill around them kept on the dial.
 */
private class TagPill(tag: GuideTag, measurer: TextMeasurer) {
    private val words: TextLayoutResult =
        measurer.measure(tag.text, TAG_TEXT, softWrap = false, maxLines = 1, density = UNITS)
    private val width = words.size.width + 2 * TAG_PAD
    // the page keeps a pill 4 units inside the square; the measured words may be a little wider
    // than
    // the page's estimate, so the same rule is applied to the pill as drawn
    private val x =
        tag.x.coerceIn(
            width / 2 + 4f,
            (DialGeometry.SIDE - 4f - width / 2).coerceAtLeast(width / 2 + 4f),
        )
    private val y = tag.y
    private val edge = tagColor(tag.kind)

    fun draw(scope: DrawScope, alpha: Float) =
        with(scope) {
            val topLeft = Offset(x - width / 2, y - TAG_HEIGHT / 2)
            val size = Size(width, TAG_HEIGHT)
            val corner = CornerRadius(TAG_HEIGHT / 2)
            drawRoundRect(TAG_FILL, topLeft, size, corner, alpha = alpha)
            drawRoundRect(edge, topLeft, size, corner, style = TAG_EDGE, alpha = alpha)
            drawText(
                words,
                color = TAG_INK,
                topLeft = Offset(x - words.size.width / 2f, y - words.size.height / 2f),
                alpha = alpha,
            )
        }
}

/** The page's tag colours: a block kind's own, the now hand's blue, or the guide's blue. */
internal fun tagColor(kind: String): Color =
    when (kind) {
        "now" -> P.nowBlue
        "" -> P.sky
        else ->
            BlockKind.entries.firstOrNull { it.name.equals(kind, ignoreCase = true) }?.let(P::of)
                ?: P.sky
    }

private val UNITS = Density(1f, 1f)
private val TAG_TEXT =
    TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-.01).em,
        textMotion = TextMotion.Animated,
    )
private val TAG_FILL = Color(0xFF0B0F17)
private val TAG_INK = Color(0xFFF5F8FF)
private val TAG_EDGE = Stroke(1.6f)
private const val TAG_HEIGHT = 24f
private const val TAG_PAD = 9f
private const val FADE_MS = 300
private const val FADE_IN_MS = 350
private const val STAGGER_MS = 60
private const val PULSE_MS = 1400
private const val PULSE_GROWTH = .35f
