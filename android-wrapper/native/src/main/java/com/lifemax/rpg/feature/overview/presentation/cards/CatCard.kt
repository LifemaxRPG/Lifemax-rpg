package com.lifemax.rpg.feature.overview.presentation.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.components.PageEyebrow
import com.lifemax.rpg.core.designsystem.components.pixelIconPath
import com.lifemax.rpg.core.designsystem.glowPaint
import com.lifemax.rpg.core.designsystem.mix
import com.lifemax.rpg.feature.overview.domain.CatCard
import com.lifemax.rpg.feature.overview.domain.CatMode
import com.lifemax.rpg.feature.overview.domain.CatPortrait

/**
 * The Cat card: a door to the live room, which stays on the page (ADR 0003), so [onOpen] is the
 * page's `{view:'pet'}`. A living cat (or two, "Miso & Pip") is the page's stage with the cat
 * sitting ([portrait], the page's petPortrait; a quiet paw while its art loads) over the name and
 * hearts; a cat that has passed is the page's own icon card (petPeekHtml, as the Overview's
 * `.ov-duo` lays it out: icon, words, arrow, one under the other).
 */
@Composable
internal fun CatCardView(
    tile: CatCard,
    portrait: CatPortrait?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OverviewCard(modifier, padding = PaddingValues(0.dp), onClick = onOpen) {
        when (tile.mode) {
            CatMode.ALIVE -> Alive(tile, portrait)
            CatMode.GONE -> Door(tile, "heart")
        }
    }
}

/**
 * `.pet-peek-stage` over `.pet-peek-row`: the room's window (192 x 144 room pixels, scaled to the
 * card's width) with the cat sitting in it at about the room's scale, then its name and its five
 * hearts. The room itself stays on the page (ADR 0003): its dark stage stands in for it here.
 */
@Composable
private fun Alive(tile: CatCard, portrait: CatPortrait?) {
    val c = LocalPageColors.current
    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(STAGE_ASPECT).background(Stage).drawBehind {
            val w = 1.dp.toPx()
            drawRect(c.lineSoft, Offset(0f, size.height - w), Size(size.width, w))
        }
    ) {
        // the portrait is 96 x 64 room pixels, the window 192 wide: half the stage across, standing
        // on the room's floor (about a sixth of the window up from its bottom)
        PetPortrait(
            portrait,
            Modifier.align(Alignment.BottomCenter)
                .padding(bottom = maxWidth * FLOOR)
                .size(maxWidth / 2, maxWidth / 3),
            placeholder = c.muted.copy(alpha = .5f),
        )
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 11.dp, end = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tile.name,
            Modifier.weight(1f, fill = false).padding(end = 9.dp),
            color = c.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 22.5.sp,
        )
        Hearts(tile.hearts ?: 0)
    }
}

/** `.pet-peek-in` in the Overview's column: the badge, the eyebrow and name, then the arrow. */
@Composable
private fun Door(tile: CatCard, icon: String) {
    val c = LocalPageColors.current
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Badge(icon)
        Column {
            PageEyebrow(tile.eyebrow)
            Text(
                tile.name,
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 21.sp,
            )
        }
        Text(
            "→",
            Modifier.clearAndSetSemantics {},
            color = c.muted,
            fontSize = 20.sp,
            lineHeight = 30.sp,
        )
    }
}

/** `.pet-peek-ico`: a 42 px tile tinted with the accent, its pixel icon in the accent. */
@Composable
private fun Badge(icon: String) {
    val c = LocalPageColors.current
    Box(
        Modifier.size(42.dp).background(c.accent.mix(.16f, c.panel2), BadgeShape),
        contentAlignment = Alignment.Center,
    ) {
        CardIcon(icon, c.accent, Modifier.size(22.dp))
    }
}

/**
 * `.pet-hearts.small`: five 13 px pixel hearts 3 px apart, the first [on] lit pink with a soft glow
 * (drop-shadow 0 0 6px), the rest faint.
 */
@Composable
private fun Hearts(on: Int) {
    val c = LocalPageColors.current
    val off = c.muted.copy(alpha = .38f)
    Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 1..5) {
            val lit = i <= on
            Box(
                Modifier.size(13.dp).drawBehind {
                    val path = pixelIconPath("heart", size.width, Offset.Zero)
                    if (lit) drawIntoCanvas { it.drawPath(path, glowPaint(HeartGlow, 6.dp.toPx())) }
                    drawPath(path, if (lit) Heart else off)
                }
            )
        }
    }
}

/** The lit heart's pink (`#ff5f7e`) and its glow (`#ff5f7e66`), fixed in 03-features.css:124. */
private val Heart = Color(0xFFFF5F7E)
private val HeartGlow = Color(0x66FF5F7E)
private val BadgeShape = RoundedCornerShape(12.dp)

/** `.pet-peek-stage`'s fixed dark (#10131c) and the room window's 192 x 144. */
private val Stage = Color(0xFF10131C)
private const val STAGE_ASPECT = 192f / 144f
private const val FLOOR = 0.09f
