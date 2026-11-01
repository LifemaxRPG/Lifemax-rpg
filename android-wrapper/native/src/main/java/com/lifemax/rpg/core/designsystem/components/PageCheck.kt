package com.lifemax.rpg.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.LocalPageColors

/**
 * The page's tick box (.check, 01-base.css:174-181), 20 dp, in PageTheme's colours: an empty box on
 * the page's background with a grey edge, filled green with a dark tick when [on]. The light theme
 * keeps a ticked box white with its usual edge, so only the tick shows that it is on
 * (00-00-foundation.css:136-141 outranks .check:checked). A box [pending] the page's answer shows
 * the state asked for, faded; one that is not [enabled] has its fill and edge dimmed as the page
 * dims a disabled button, while its tick stays at full strength, so a disabled ticked box still
 * reads as ticked (in the light theme the tick is all that shows it). The tick keeps its size
 * whatever the font size, as the box does.
 *
 * It only draws: the row it sits in, or the box itself, is the switch ([toggleTarget]), so TalkBack
 * reads the row's words with the switch's state rather than the box on its own.
 */
@Composable
fun PageCheck(
    on: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pending: Boolean = false,
) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(6.dp)
    val filled = on && !c.light
    // a box waiting for the page fades whole; a disabled one fades its fill and edge, not its tick
    Box(
        modifier
            .size(20.dp)
            .alpha(if (pending && enabled) PENDING_ALPHA else 1f)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.matchParentSize()
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(shape)
                .background(if (filled) c.good else if (c.light) Color.White else c.bg)
                .border(
                    1.5.dp,
                    when {
                        filled -> c.good
                        c.light -> c.line
                        else -> BoxEdge
                    },
                    shape,
                )
        )
        if (on)
            with(LocalDensity.current) {
                Text(
                    "✓",
                    color = CheckInk,
                    fontSize = 12.dp.toSp(),
                    fontWeight = FontWeight(800),
                    lineHeight = 18.dp.toSp(),
                )
            }
    }
}

/** The page's .btn:disabled opacity, which a dimmed box's fill and edge follow. */
private const val DISABLED_ALPHA = .45f

/** A box waiting for the page's answer, as native Workout plan fades its set boxes. */
private const val PENDING_ALPHA = .6f

/**
 * Colours the page's .check fixes: the dark theme's grey edge and the tick (01-base.css:175,181).
 */
private val BoxEdge = Color(0xFF4A5568)
private val CheckInk = Color(0xFF0C0E12)
