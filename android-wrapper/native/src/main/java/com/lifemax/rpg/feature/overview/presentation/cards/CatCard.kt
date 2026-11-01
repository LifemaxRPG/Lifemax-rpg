package com.lifemax.rpg.feature.overview.presentation.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import com.lifemax.rpg.feature.overview.domain.CatMode
import com.lifemax.rpg.feature.overview.domain.CatTile

/**
 * The Cat card: a door to the live room, which stays on the page (ADR 0003), so [onOpen] is the
 * page's `{view:'pet'}`. A living cat (or two, "Miso & Pip") is the page's name row with the cat's
 * hearts, its icon standing in for the room's window until phase 3's portrait; a cat that has
 * passed and the stray kitten are the page's own icon cards (petPeekHtml, as the Overview's
 * `.ov-duo` lays them out: icon, words, arrow, one under the other).
 */
@Composable
fun CatCard(tile: CatTile, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    CardsBFrame(modifier, padding = PaddingValues(0.dp), onClick = onOpen) {
        when (tile.mode) {
            CatMode.Alive -> Alive(tile)
            CatMode.Gone -> Door(tile, "heart")
            CatMode.Invite -> Door(tile, "paw")
        }
    }
}

/** `.pet-peek-row`: the cat's icon, its name and its five hearts in one row. */
@Composable
private fun Alive(tile: CatTile) {
    val c = LocalPageColors.current
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Badge("paw")
        Text(
            tile.name,
            Modifier.weight(1f),
            color = c.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 22.5.sp,
        )
        Hearts(tile.hearts)
    }
}

/** `.pet-peek-in` in the Overview's column: the badge, the eyebrow and name, then the arrow. */
@Composable
private fun Door(tile: CatTile, icon: String) {
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
            if (tile.text.isNotEmpty())
                Text(
                    tile.text,
                    Modifier.padding(top = 2.dp),
                    color = c.muted,
                    fontSize = 12.5.sp,
                    lineHeight = 18.75.sp,
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
