package com.lifemax.rpg.feature.lifedirector.presentation.components

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
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.feature.lifedirector.domain.Chip
import com.lifemax.rpg.feature.lifedirector.domain.ChipKind

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
                Modifier.background(DirectorPalette.buttonFill, shape)
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
            ChipKind.PINNED -> DirectorPalette.ink2 to DirectorPalette.chipFill
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
 * A round dot with a soft glow around it ([glow] is the CSS shadow's blur). A [pulse] from
 * [rememberPulse] dims and shrinks it as the web's ld-pulse does.
 */
@Composable
fun GlowDot(size: Dp, color: Color, glow: Dp, pulse: State<Float>? = null) {
    Box(
        Modifier.size(size)
            .graphicsLayer {
                val p = pulse?.value ?: 0f
                alpha = lerp(1f, .45f, p)
                scaleX = lerp(1f, .8f, p)
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
