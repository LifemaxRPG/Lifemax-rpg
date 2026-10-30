package com.lifemax.rpg.feature.plan.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.plan.domain.Celebration
import com.lifemax.rpg.feature.plan.presentation.PlanFeedback
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * The page's answer to a tick, shown where the page would show it: the message as its toast above
 * the bottom bar, and an award the way celebrateXP draws one (07-runtime.js): a ring and a burst at
 * the tapped box in the colour of the largest gain, orbs flying to the top bar, and a rising "+N
 * XP". Under reduced motion it draws fewer particles, as the page does.
 */
@Composable
internal fun PlanFeedbackLayer(
    feedback: PlanFeedback,
    origin: Offset,
    topInsetDp: Float,
    bottomInsetDp: Float,
    reduced: Boolean,
    onShown: (Long) -> Unit,
) {
    val toast = remember(feedback.id) { Animatable(0f) }
    LaunchedEffect(feedback.id) {
        if (feedback.message.isEmpty()) delay(BURST_MS.toLong())
        else {
            toast.animateTo(1f, tween(FADE_MS))
            delay(TOAST_MS)
            toast.animateTo(0f, tween(FADE_MS))
        }
        onShown(feedback.id)
    }
    Box(Modifier.fillMaxSize()) {
        if (feedback.awards.isNotEmpty())
            Burst(feedback.id, feedback.awards, origin, topInsetDp, reduced)
        if (feedback.message.isNotEmpty()) {
            val c = LocalPlanColors.current
            val shape = RoundedCornerShape(12.dp)
            Text(
                feedback.message,
                Modifier.align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = bottomInsetDp.dp + 12.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = toast.value
                        translationY = (1 - toast.value) * 10.dp.toPx()
                    }
                    .shadow(12.dp, shape)
                    .clip(shape)
                    .background(c.toast)
                    .border(1.dp, c.line, shape)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = c.text,
                fontSize = 13.5.sp,
                fontWeight = FontWeight(650),
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun Burst(
    id: Long,
    awards: List<Celebration>,
    origin: Offset,
    topInsetDp: Float,
    reduced: Boolean,
) {
    val density = LocalDensity.current.density
    val clock = remember(id) { Animatable(0f) }
    val particles = remember(id) { Particles(awards, reduced, density, Random(id)) }
    LaunchedEffect(id) { clock.animateTo(BURST_MS / 1000f, tween(BURST_MS, easing = LinearEasing)) }
    Canvas(Modifier.fillMaxSize()) {
        val from = if (origin.isSpecified) origin else center
        particles.draw(this, clock.value, from, Offset(center.x, topInsetDp.dp.toPx() / 2))
    }
    Column(
        Modifier.layout { measurable, constraints ->
                val label = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val width = constraints.maxWidth.toFloat()
                    val at =
                        if (origin.isSpecified) origin
                        else Offset(width / 2, constraints.maxHeight / 2f)
                    val edge = 70.dp.toPx()
                    val x = at.x.coerceIn(edge, max(edge, width - edge))
                    val y = max(110.dp.toPx(), at.y - 24.dp.toPx())
                    label.place((x - label.width / 2f).roundToInt(), y.roundToInt())
                }
            }
            .graphicsLayer { rise(clock.value / LABEL_S) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // one label for every award of the tick, where the page would stack one per award
        Text(
            "+${awards.sumOf { it.xp }} XP",
            style =
                TextStyle(
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    shadow = Shadow(particles.main, blurRadius = 28 * density),
                ),
        )
        awards
            .flatMap { it.gains }
            .singleOrNull()
            ?.let {
                Text(
                    it.short.uppercase(),
                    color = particles.main,
                    fontSize = 11.sp,
                    letterSpacing = 3.sp,
                )
            }
    }
}

/** The page's fxXp keyframes over [t] (0 to 1): pop in, settle, then drift up and fade. */
private fun GraphicsLayerScope.rise(t: Float) {
    fun ease(from: Float, to: Float) =
        ((t - from) / (to - from)).coerceIn(0f, 1f).let { 1 - (1 - it) * (1 - it) }
    val pop = ease(0f, 0.15f)
    val settle = ease(0.15f, 0.32f)
    val drift = ease(0.32f, 1f)
    alpha = if (t < 0.15f) pop else 1 - ease(0.15f, 1f)
    translationY = (-0.6f - 0.4f * pop - 0.08f * settle - 1.22f * drift) * size.height
    scaleX = 0.5f + 0.8f * pop - 0.3f * settle
    scaleY = scaleX
}

/**
 * celebrateXP's particles, decided once when the award arrives; a frame only reads the clock and
 * allocates nothing. Speeds, sizes and lives are the page's, in dp and seconds.
 */
private class Particles(
    awards: List<Celebration>,
    reduced: Boolean,
    private val unit: Float,
    random: Random,
) {
    private class Spark(
        val vx: Float,
        val vy: Float,
        val size: Float,
        val color: Color,
        val life: Float,
    )

    private class Orb(val color: Color, val delay: Float, val life: Float, val bend: Float)

    private fun Random.between(a: Float, b: Float) = a + nextFloat() * (b - a)

    /** Each award's colour: its largest gain's. */
    private val colors = awards.map { award -> Color(award.gains.maxBy { it.xp }.color.toInt()) }
    val main = colors.first()

    private val sparks =
        colors.flatMap { color ->
            List(if (reduced) 8 else 30) {
                val angle = random.between(0f, 2 * PI.toFloat())
                val speed = BURST_SPEED * random.between(0.3f, 1f)
                Spark(
                    cos(angle) * speed,
                    sin(angle) * speed - BURST_SPEED * 0.25f,
                    random.between(3f, 7f).roundToInt().toFloat(),
                    if (random.nextFloat() < 0.2f) Color.White else color,
                    random.between(0.5f, 1.2f),
                )
            }
        }

    private val orbs =
        awards.flatMap { award ->
            award.gains.flatMap { gain ->
                List(min(if (reduced) 4 else 16, 3 + (gain.xp / 3f).roundToInt())) {
                    Orb(
                        Color(gain.color.toInt()),
                        it * 0.038f,
                        random.between(0.65f, 0.95f),
                        random.between(-0.45f, 0.45f),
                    )
                }
            }
        }

    /** The ring thins from 6 dp to 1 dp as it grows. */
    private val strokes = List(6) { Stroke((it + 1) * unit) }

    fun draw(scope: DrawScope, t: Float, from: Offset, to: Offset) =
        with(scope) {
            if (t < RING_LIFE) {
                val k = t / RING_LIFE
                val grown = 1 - (1 - k) * (1 - k) * (1 - k)
                val stroke = strokes[(5 * (1 - k)).roundToInt().coerceIn(0, 5)]
                colors.forEach {
                    drawCircle(it, (6 + 74 * grown) * unit, from, (1 - k) * 0.9f, stroke)
                }
            }
            sparks.forEach { s ->
                if (t >= s.life) return@forEach
                val k = t / s.life
                val x = from.x + s.vx * (1 - exp(-DRAG * t)) / DRAG * unit
                val y = from.y + (s.vy * t + GRAVITY / 2 * t * t) * unit
                val side = s.size * (1 - k * 0.55f) * unit
                val halo = side * 1.6f
                drawRect(
                    s.color,
                    Offset(x - halo, y - halo),
                    Size(2 * halo, 2 * halo),
                    (1 - k) * 0.28f,
                )
                drawRect(s.color, Offset(x - side / 2, y - side / 2), Size(side, side), 1 - k)
            }
            val dx = to.x - from.x
            val dy = to.y - from.y
            val d = max(1f, hypot(dx, dy))
            orbs.forEach { o ->
                val k = (t - o.delay) / o.life
                if (k < 0 || k >= 1) return@forEach
                val bend = o.bend * d
                val control =
                    Offset(
                        (from.x + to.x) / 2 - dy / d * bend,
                        (from.y + to.y) / 2 + dx / d * bend - d * 0.15f,
                    )
                val r = (7 + (1 - k) * 4) * unit
                for (echo in 1..3) {
                    val behind = k - echo * 0.04f
                    if (behind > 0)
                        drawCircle(
                            o.color,
                            r * 0.45f,
                            curve(from, control, to, behind),
                            0.5f / echo,
                        )
                }
                val at = curve(from, control, to, k)
                drawCircle(o.color, r * 2.6f, at, 0.22f)
                drawCircle(o.color, r * 1.1f, at, 0.9f)
                drawCircle(Color.White, r * 0.45f, at)
            }
        }

    /** An orb's path: a quadratic curve it runs faster and faster (the page's u = k²). */
    private fun curve(from: Offset, control: Offset, to: Offset, k: Float): Offset {
        val u = k * k
        val v = 1 - u
        return Offset(
            v * v * from.x + 2 * v * u * control.x + u * u * to.x,
            v * v * from.y + 2 * v * u * control.y + u * u * to.y,
        )
    }
}

private const val BURST_SPEED = 280f
private const val GRAVITY = 420f
private const val DRAG = 1.6f
private const val RING_LIFE = 0.55f
private const val LABEL_S = 1.5f
private const val BURST_MS = 1600
private const val FADE_MS = 200
private const val TOAST_MS = 2200L
