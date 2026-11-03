package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.components.ProgressBar
import com.lifemax.rpg.core.designsystem.components.pixelIconPath
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.core.designsystem.glowPaint
import com.lifemax.rpg.feature.pet.domain.PetGrowth

/*
 * The small pieces of the Cat card, as the page's CSS draws them (.pet-hearts, .pet-bond,
 * .pet-grow, .pet-stats, .pet-favs, .btn.secondary): colours the page's styles fix are named
 * here, the rest are the page's tokens.
 */

/** The lit heart's pink and its glow (.pet-hearts span.on), and the bond bar's two pinks. */
private val HeartOn = Color(0xFFFF5F7E)
private val HeartGlow = Color(0x66FF5F7E)
private val BondFrom = Color(0xFFFF9BB0)
private val GrowLabel = Color(0xFFFFB0C8)
private val GrowFrom = Color(0xFFFFB0C8)
private val GrowTo = Color(0xFFFFD27A)

private val BoxShape = RoundedCornerShape(12.dp)
private val ButtonShape = RoundedCornerShape(8.dp)

/** `.pet-hearts`: five pixel hearts [size] wide 3 dp apart, the first [on] lit pink with a glow. */
@Composable
internal fun PetHearts(on: Int, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val c = LocalPageColors.current
    val off = c.muted.copy(alpha = .38f)
    Row(modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 1..5) {
            val lit = i <= on
            Box(
                Modifier.size(size).drawBehind {
                    val path = pixelIconPath("heart", this.size.width, Offset.Zero)
                    if (lit) drawIntoCanvas { it.drawPath(path, glowPaint(HeartGlow, 6.dp.toPx())) }
                    drawPath(path, if (lit) HeartOn else off)
                }
            )
        }
    }
}

/** `.pet-bond`: the bar, pink on the page's background, and its words under it. */
@Composable
internal fun PetBondBar(pct: Int, text: String, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    Column(modifier.fillMaxWidth()) {
        ProgressBar(
            pct / 100f,
            Brush.horizontalGradient(listOf(BondFrom, HeartOn)),
            track = c.bg,
            outline = c.lineSoft,
        )
        Text(
            text,
            Modifier.padding(top = 6.dp),
            color = c.muted,
            fontSize = 11.5.sp,
            lineHeight = 17.25.sp,
        )
    }
}

/** `.pet-grow`: a kitten's day count and the growth bar, under a hairline. */
@Composable
internal fun PetGrowthLine(g: PetGrowth, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind { drawRect(c.lineSoft, size = Size(size.width, 1.dp.toPx())) }
            .padding(top = 13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Mono(g.label, GrowLabel, Modifier.weight(1f, fill = false))
            Mono(g.day, c.muted)
        }
        ProgressBar(
            g.pct / 100f,
            Brush.horizontalGradient(listOf(GrowFrom, GrowTo)),
            Modifier.padding(vertical = 8.dp),
            height = 6.dp,
            track = c.line,
            outline = Color.Transparent,
        )
        Text(g.text, color = c.textDim, fontSize = 11.5.sp, lineHeight = 16.7.sp)
    }
}

@Composable
private fun Mono(text: String, color: Color, modifier: Modifier = Modifier) =
    Text(
        text,
        modifier,
        color = color,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.26.sp,
    )

/** At this font scale and above the boxes of a row stack one under the other, so no word breaks. */
private const val LARGE_TEXT = 1.3f

@Composable private fun largeText(): Boolean = LocalDensity.current.fontScale >= LARGE_TEXT

/**
 * `.pet-stats`: three boxes side by side, each the value large over its caption small and upper
 * case. With large text they stack, each box a row with the caption at the left and the value at
 * the right, so a caption never breaks across lines.
 */
@Composable
internal fun PetStats(stats: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    if (largeText())
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, label) in stats) Box(
                Modifier.fillMaxWidth().tile(c).padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Caption(label, Modifier.weight(1f, fill = false).padding(end = 8.dp))
                    Text(value, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    else
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, label) in stats) Column(
                Modifier.weight(1f).tile(c).padding(horizontal = 6.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    value,
                    color = c.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                Caption(label, Modifier.padding(top = 3.dp), TextAlign.Center)
            }
        }
}

/**
 * `.pet-favs`: two boxes side by side, a small caption over the cat's favourite; one under the
 * other with large text.
 */
@Composable
internal fun PetFavourites(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    val box: @Composable (Modifier, String, String) -> Unit = { m, label, value ->
        Column(m.tile(c).padding(horizontal = 10.dp, vertical = 9.dp)) {
            Caption(label)
            Text(
                value,
                Modifier.padding(top = 3.dp),
                color = c.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 16.25.sp,
            )
        }
    }
    if (largeText())
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, value) in items) box(Modifier.fillMaxWidth(), label, value)
        }
    else
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, value) in items) box(Modifier.weight(1f), label, value)
        }
}

/** A box of the stats and favourites: panel2, a hairline edge, 12 dp corners. */
private fun Modifier.tile(c: PageColors): Modifier =
    clip(BoxShape).background(c.panel2).border(1.dp, c.lineSoft, BoxShape)

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier, align: TextAlign? = null) =
    Text(
        text.uppercase(),
        modifier,
        color = LocalPageColors.current.muted,
        fontSize = 9.5.sp,
        letterSpacing = 1.05.sp,
        textAlign = align,
    )

/**
 * `.btn.secondary`: the page's second button, panel2 with the text colour and a hairline edge (the
 * panel in the light theme). [small] is the slow blink's quieter size (13 px, a little less
 * padding, 92 % opaque). At least 48 dp tall.
 */
@Composable
internal fun PetSecondaryButton(
    text: String,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    val c = LocalPageColors.current
    Box(
        modifier
            .alpha(if (small) .92f else 1f)
            .clip(ButtonShape)
            .background(if (c.light) c.panel else c.panel2)
            .border(1.dp, c.line, ButtonShape)
            .semantics { contentDescription = hint }
            .tapTarget(Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = if (small) 9.dp else 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = c.text,
            fontSize = if (small) 13.sp else 14.sp,
            fontWeight = FontWeight(650),
            lineHeight = 21.sp,
            softWrap = false,
        )
    }
}

/** A line of the page's muted small print. */
@Composable
internal fun PetNote(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 12.5.sp,
    color: Color = LocalPageColors.current.muted,
) = Text(text, modifier, color = color, fontSize = size, lineHeight = size * 1.5f)
