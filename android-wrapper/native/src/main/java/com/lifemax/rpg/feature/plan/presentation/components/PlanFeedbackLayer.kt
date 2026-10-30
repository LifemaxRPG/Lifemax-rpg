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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.core.designsystem.cssBlurRadius
import com.lifemax.rpg.feature.plan.domain.Celebration
import com.lifemax.rpg.feature.plan.domain.PagePoint
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The page's answer to a tick, shown where the page would show it: the message as its toast above
 * the bottom bar, and an award the way celebrateXP draws one (07-runtime.js): a ring and a burst at
 * the tapped box in the colour of the largest gain, orbs flying to the top bar's logo ([target]),
 * each bursting there, and a rising "+N XP". Under reduced motion it draws fewer particles, as the
 * page does.
 */
@Composable
internal fun PlanFeedbackLayer(
    feedback: PlanFeedback,
    origin: Offset,
    target: PagePoint,
    bottomInsetDp: Float,
    reduced: Boolean,
    onShown: (Long) -> Unit,
) {
    val density = LocalDensity.current.density
    val particles =
        remember(feedback.id) {
            feedback.awards
                .takeIf { it.isNotEmpty() }
                ?.let { Particles(it, reduced, density, Random(feedback.id)) }
        }
    val clock = remember(feedback.id) { Animatable(0f) }
    val toast = remember(feedback.id) { Animatable(0f) }
    LaunchedEffect(feedback.id) {
        coroutineScope {
            if (particles != null)
                launch {
                    val ms = (particles.duration * 1000).roundToInt()
                    clock.animateTo(particles.duration, tween(ms, easing = LinearEasing))
                }
            if (feedback.message.isNotEmpty())
                launch {
                    toast.animateTo(1f, tween(FADE_MS))
                    delay(TOAST_MS)
                    toast.animateTo(0f, tween(FADE_MS))
                }
        }
        onShown(feedback.id)
    }
    Box(Modifier.fillMaxSize()) {
        if (particles != null) {
            Canvas(Modifier.fillMaxSize()) {
                val from = if (origin.isSpecified) origin else center
                particles.draw(
                    this,
                    clock.value,
                    from,
                    Offset(target.x.dp.toPx(), target.y.dp.toPx()),
                )
            }
            XpLabel(feedback.awards, particles.main, origin) { clock.value }
        }
        if (feedback.message.isNotEmpty())
            PlanToast(
                feedback.message,
                Modifier.align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = bottomInsetDp.dp + 12.dp)
                    .graphicsLayer {
                        alpha = toast.value
                        translationY = (1 - toast.value) * 10.dp.toPx()
                    },
            )
    }
}

/** The page's toast (.toast), with the reply's message. */
@Composable
internal fun PlanToast(message: String, modifier: Modifier = Modifier) {
    val c = LocalPlanColors.current
    val shape = RoundedCornerShape(12.dp)
    Text(
        message,
        modifier
            .fillMaxWidth()
            .boxShadow(c.toastShadow, 40.dp, shape, 12.dp)
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

/**
 * The rising "+N XP" at the tapped box, one label for every award of the tick where the page would
 * stack one per award, with the gain's short name under it when there is a single gain. The toast
 * says the same for TalkBack.
 */
@Composable
private fun XpLabel(awards: List<Celebration>, glow: Color, origin: Offset, clock: () -> Float) {
    val density = LocalDensity.current.density
    val text = "+${awards.sumOf { it.xp }} XP"
    // the page's text-shadow, the first on top: 0 0 10px glow, 0 0 28px glow, 0 2px 0 #0007
    val shadows =
        remember(glow, density) {
            listOf(
                Shadow(LabelDrop, Offset(0f, 2 * density), 0f),
                Shadow(glow, blurRadius = cssBlurRadius(28 * density)),
                Shadow(glow, blurRadius = cssBlurRadius(10 * density)),
            )
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
            .graphicsLayer { rise(clock() / LABEL_S) }
            .clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            for (shadow in shadows) Text(
                text,
                style =
                    TextStyle(
                        color = FxWhite,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        shadow = shadow,
                    ),
            )
        }
        awards
            .flatMap { it.gains }
            .singleOrNull()
            ?.let {
                Text(it.short.uppercase(), color = glow, fontSize = 11.sp, letterSpacing = 3.sp)
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
    /** A spark of FX.burst, thrown at [start] seconds. */
    private class Spark(
        val start: Float,
        val vx: Float,
        val vy: Float,
        val gravity: Float,
        val size: Float,
        val color: Color,
        val life: Float,
    )

    private class Orb(val color: Color, val delay: Float, val life: Float, val bend: Float)

    private fun Random.between(a: Float, b: Float) = a + nextFloat() * (b - a)

    /** FX.burst: [n] sparks of [color] (one in five white) thrown at up to [speed] dp a second. */
    private fun Random.burst(n: Int, color: Color, speed: Float, gravity: Float, start: Float) =
        List(n) {
            val angle = between(0f, 2 * PI.toFloat())
            val v = speed * between(0.3f, 1f)
            Spark(
                start,
                cos(angle) * v,
                sin(angle) * v - speed * 0.25f,
                gravity,
                between(3f, 7f).roundToInt().toFloat(),
                if (nextFloat() < 0.2f) FxWhite else color,
                between(0.5f, 1.2f),
            )
        }

    /** Each award's colour: its largest gain's. */
    private val colors = awards.map { award -> Color(award.gains.maxBy { it.xp }.color.toInt()) }
    val main = colors.first()

    private val sparks = colors.flatMap { random.burst(if (reduced) 8 else 30, it, 280f, 420f, 0f) }

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

    /** The small burst each orb makes as it reaches the logo. */
    private val arrivals = orbs.flatMap { random.burst(3, it.color, 130f, 80f, it.delay + it.life) }

    /** The ring thins from 6 dp to 1 dp as it grows. */
    private val strokes = List(6) { Stroke((it + 1) * unit) }

    /** Seconds until the last particle has gone and the label has risen. */
    val duration =
        maxOf(
            LABEL_S,
            RING_LIFE,
            (sparks + arrivals).maxOf { it.start + it.life },
            orbs.maxOf { it.delay + it.life },
        )

    fun draw(scope: DrawScope, t: Float, from: Offset, to: Offset) =
        with(scope) {
            if (t < RING_LIFE) {
                val k = t / RING_LIFE
                val grown = 1 - (1 - k) * (1 - k) * (1 - k)
                val stroke = strokes[(5 * (1 - k)).roundToInt().coerceIn(0, 5)]
                for (i in colors.indices) drawCircle(
                    colors[i],
                    (6 + 74 * grown) * unit,
                    from,
                    (1 - k) * 0.9f,
                    stroke,
                )
            }
            drawSparks(sparks, from, t)
            val dx = to.x - from.x
            val dy = to.y - from.y
            val d = max(1f, hypot(dx, dy))
            for (i in orbs.indices) {
                val o = orbs[i]
                val k = (t - o.delay) / o.life
                if (k < 0 || k >= 1) continue
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
                drawCircle(FxWhite, r * 0.45f, at)
            }
            drawSparks(arrivals, to, t)
        }

    private fun DrawScope.drawSparks(sparks: List<Spark>, at: Offset, t: Float) {
        for (i in sparks.indices) {
            val s = sparks[i]
            val age = t - s.start
            if (age < 0 || age >= s.life) continue
            val k = age / s.life
            val x = at.x + s.vx * (1 - exp(-DRAG * age)) / DRAG * unit
            val y = at.y + (s.vy * age + s.gravity / 2 * age * age) * unit
            val side = s.size * (1 - k * 0.55f) * unit
            val halo = side * 1.6f
            drawRect(s.color, Offset(x - halo, y - halo), Size(2 * halo, 2 * halo), (1 - k) * 0.28f)
            drawRect(s.color, Offset(x - side / 2, y - side / 2), Size(side, side), 1 - k)
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

private const val DRAG = 1.6f
private const val RING_LIFE = 0.55f
private const val LABEL_S = 1.5f
private const val FADE_MS = 200
private const val TOAST_MS = 2200L
