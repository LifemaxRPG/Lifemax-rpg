package com.lifemax.rpg.feature.lifedirector.presentation.components

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.feature.lifedirector.domain.Chip
import com.lifemax.rpg.feature.lifedirector.domain.ChipKind
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Life Director's body text as .ld-root sets it: 15 sp, line height 1.42, tabular figures. Lines
 * are spaced as CSS spaces them, the extra height shared above and below.
 */
val DirectorText =
    TextStyle(
        color = DirectorPalette.ink,
        fontSize = 15.sp,
        lineHeight = 1.42.em,
        letterSpacing = (-.005).em,
        fontFeatureSettings = "tnum",
        lineHeightStyle =
            LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

/** Which part of a card a list item is, so a card can span several items of a lazy list. */
enum class Slice {
    WHOLE,
    TOP,
    MIDDLE,
    BOTTOM,
}

/**
 * Draws this item's part of a rounded card: the corners of the parts it continues into are pushed
 * out of sight, so the parts join into one card. [washes] are painted over the fill, inside it.
 */
fun Modifier.cardSlice(
    slice: Slice,
    radius: Dp,
    fill: Brush,
    border: Color,
    washes: List<Brush> = emptyList(),
): Modifier = drawWithCache {
    val r = radius.toPx()
    val above = if (slice == Slice.MIDDLE || slice == Slice.BOTTOM) 2 * r else 0f
    val below = if (slice == Slice.TOP || slice == Slice.MIDDLE) 2 * r else 0f
    val hairline = 1.dp.toPx()
    val inset = hairline / 2
    val outline =
        Path().apply {
            val box = Rect(inset, inset - above, size.width - inset, size.height + below - inset)
            addRoundRect(RoundRect(box, CornerRadius(r)))
        }
    val edge = Stroke(hairline)
    onDrawBehind {
        clipRect {
            drawPath(outline, fill)
            washes.forEach { drawPath(outline, it) }
            drawPath(outline, border, style = edge)
        }
    }
}

/**
 * A CSS radial-gradient ellipse from [color] to transparent at 60% of its radii. Centre and radii
 * are in widths of the box it fills, so it keeps its shape at any width.
 */
fun ellipseWash(cx: Float, cy: Float, rx: Float, ry: Float, color: Color): Brush =
    object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val w = size.width
            val centre = Offset(cx * w, cy * w)
            return RadialGradientShader(
                    centre,
                    rx * w,
                    listOf(color, color.copy(alpha = 0f)),
                    listOf(0f, .6f),
                )
                .apply {
                    setLocalMatrix(Matrix().apply { setScale(1f, ry / rx, centre.x, centre.y) })
                }
        }
    }

/**
 * A CSS linear-gradient at [degrees] (0 points up, 90 right) over whatever box it fills, its line
 * long enough that the corners take the end colours, as in CSS.
 */
fun cssLinearGradient(degrees: Float, vararg stops: Pair<Float, Color>): Brush =
    object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val t = Math.toRadians(degrees.toDouble())
            val direction = Offset(sin(t).toFloat(), -cos(t).toFloat())
            val length = abs(size.width * direction.x) + abs(size.height * direction.y)
            val half = direction * (length / 2)
            return LinearGradientShader(
                size.center - half,
                size.center + half,
                stops.map { it.second },
                stops.map { it.first },
            )
        }
    }

/**
 * Lets content run [by] past both sides of its parent's padding, as the web's negative margins do.
 */
fun Modifier.bleed(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = by.roundToPx() * 2
    val placeable =
        measurable.measure(
            constraints.copy(
                minWidth = constraints.minWidth + extra,
                maxWidth = constraints.maxWidth + extra,
            )
        )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

enum class ButtonLook {
    PRIMARY,
    PLAIN,
    GHOST,
}

/** The web's .ld-btn, at least 48 dp tall; [small] is .ld-small. */
@Composable
fun DirectorButton(
    label: String,
    look: ButtonLook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    val shape = RoundedCornerShape(if (small) 10.dp else 12.dp)
    val surface =
        when (look) {
            ButtonLook.PRIMARY ->
                Modifier.softShadow(
                        DirectorPalette.task.copy(alpha = .22f),
                        18.dp,
                        12.dp,
                        dy = 6.dp,
                    )
                    .background(
                        Brush.verticalGradient(
                            listOf(DirectorPalette.goTop, DirectorPalette.goBottom)
                        ),
                        shape,
                    )
            ButtonLook.PLAIN ->
                Modifier.background(Color.White.copy(alpha = .045f), shape)
                    .border(1.dp, DirectorPalette.line2, shape)
            ButtonLook.GHOST -> Modifier
        }
    Box(
        modifier
            .heightIn(min = 48.dp)
            .then(surface)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = DirectorText,
            color =
                when (look) {
                    ButtonLook.PRIMARY -> DirectorPalette.goInk
                    ButtonLook.PLAIN -> DirectorPalette.ink
                    ButtonLook.GHOST -> DirectorPalette.ink2
                },
            fontSize = if (small) 13.1.sp else 14.4.sp,
            fontWeight = FontWeight(650),
            maxLines = 1,
        )
    }
}

/** A rounded label: the "Now" tag, a session's state. */
@Composable
fun Pill(
    text: String,
    color: Color,
    fill: Color,
    modifier: Modifier = Modifier,
    weight: Int = 700,
) {
    Text(
        text,
        modifier
            .background(fill, RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 1.dp),
        style = DirectorText,
        color = color,
        fontSize = 10.9.sp,
        fontWeight = FontWeight(weight),
        maxLines = 1,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(chips: List<Chip>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        chips.forEach { DirectorChip(it) }
    }
}

/** A quest, goal, XP or pinned chip (.ld-chip). */
@Composable
fun DirectorChip(chip: Chip) {
    val (color, fill) =
        when (chip.kind) {
            ChipKind.QUEST -> DirectorPalette.amber to DirectorPalette.work.copy(alpha = .12f)
            ChipKind.XP -> DirectorPalette.mint to DirectorPalette.task.copy(alpha = .12f)
            ChipKind.GOAL,
            ChipKind.PINNED -> DirectorPalette.ink2 to Color.White.copy(alpha = .06f)
        }
    val icon =
        when (chip.kind) {
            ChipKind.QUEST -> "flag"
            ChipKind.PINNED -> "lock"
            else -> null
        }
    Row(
        Modifier.background(fill, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { PixelIcon(it, color, Modifier.size(10.dp)) }
        Text(
            chip.text,
            style = DirectorText,
            color = color,
            fontSize = 11.8.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A round dot with a soft glow around it ([glow] is the CSS shadow's blur), optionally pulsing as
 * the web's ld-pulse does.
 */
@Composable
fun GlowDot(size: Dp, color: Color, glow: Dp, pulse: State<Float>? = null) {
    Box(
        Modifier.size(size)
            .graphicsLayer {
                val p = pulse?.value ?: 1f
                alpha = p
                scaleX = .8f + .2f * (p - .45f) / .55f
                scaleY = scaleX
            }
            .drawWithCache {
                val halo = if (glow > 0.dp) glowPaint(color, glow.toPx()) else null
                onDrawBehind {
                    halo?.let { paint ->
                        drawIntoCanvas { it.drawCircle(center, size.toPx() / 2, paint) }
                    }
                    drawCircle(color)
                }
            }
    )
}

/** A CSS box-shadow: this rounded box, blurred by [blur] in [color], [dy] lower, behind it. */
fun Modifier.softShadow(color: Color, blur: Dp, corner: Dp, dy: Dp = 0.dp): Modifier =
    drawWithCache {
        val paint = glowPaint(color, blur.toPx())
        val r = corner.toPx()
        val drop = dy.toPx()
        onDrawBehind {
            drawIntoCanvas {
                it.drawRoundRect(0f, drop, size.width, size.height + drop, r, r, paint)
            }
        }
    }

/**
 * A paint that blurs what it draws as a CSS shadow of [blurPx] does: CSS blurs with a deviation of
 * half the blur, and Android turns a radius r into a deviation of 0.57735 r + 0.5.
 */
fun glowPaint(color: Color, blurPx: Float): Paint =
    Paint().apply {
        this.color = color
        asFrameworkPaint().maskFilter =
            BlurMaskFilter(
                ((blurPx / 2 - .5f) / .57735f).coerceAtLeast(.5f),
                BlurMaskFilter.Blur.NORMAL,
            )
    }

/**
 * A value that breathes from 1 down to [low] and back over [periodMs] while [running] is true; 1
 * and still otherwise, or when the phone is set to remove animations. Read it in a layer so a pulse
 * redraws only that layer.
 */
@Composable
fun rememberPulse(running: Boolean, periodMs: Int, low: Float): State<Float> {
    val context = LocalContext.current
    val still = remember {
        runCatching {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                )
            }
            .getOrDefault(1f) == 0f
    }
    if (!running || still) return remember { mutableFloatStateOf(1f) }
    return rememberInfiniteTransition("pulse")
        .animateFloat(
            1f,
            low,
            infiniteRepeatable(
                tween(periodMs / 2, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            "pulse",
        )
}

/** The wall clock in milliseconds, ticking each second while [running]. */
@Composable
fun rememberTickingClock(running: Boolean): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        while (running) {
            now.longValue = System.currentTimeMillis()
            delay(1000 - now.longValue % 1000)
        }
    }
    return now
}

/** A running timer as the web writes it: 4:05, 1:02:09. */
fun clockText(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = s / 60 % 60
    val sec = (s % 60).toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$sec" else "$m:$sec"
}
