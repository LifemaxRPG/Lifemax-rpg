package com.lifemax.rpg.feature.overview.presentation.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.components.ProgressBar
import com.lifemax.rpg.core.designsystem.components.barFade
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.feature.overview.domain.AttrGem
import com.lifemax.rpg.feature.overview.domain.AttrsTile

/**
 * The Attributes card (ovAttrsHtml): two columns of gems, each its attribute's level, this week's
 * XP, a status dot and its level bar, and the dot legend. [onDetails] is "Details ›" (the page's
 * `{view:'character'}`); [onGem] a gem's tap (the `attr` command, which opens its sheet).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttributesCard(
    tile: AttrsTile,
    onDetails: () -> Unit,
    onGem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    CardsBFrame(modifier) {
        CardsBHead(tile.title) { CardsBLink(tile.link, onDetails) }
        Column(verticalArrangement = Arrangement.spacedBy(GemGap)) {
            tile.gems.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(GemGap)) {
                    pair.forEach { g -> Gem(g, { onGem(g.id) }, Modifier.weight(1f)) }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        FlowRow(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tile.legend.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(l.kind, c)
                    Text(
                        l.label,
                        Modifier.padding(start = 6.dp),
                        color = c.muted,
                        fontSize = 11.sp,
                        lineHeight = 16.5.sp,
                    )
                }
            }
        }
    }
}

/**
 * `.ov-gem`: a button with its attribute's colour down the left edge. TalkBack reads its name,
 * level and week, then the dot's words (the page's `title`).
 */
@Composable
private fun Gem(g: AttrGem, onClick: () -> Unit, modifier: Modifier) {
    val c = LocalPageColors.current
    val color = Color(g.color.toInt())
    Column(
        modifier
            .clip(GemShape)
            .background(c.panel2)
            .border(1.dp, c.lineSoft, GemShape)
            .drawBehind { drawRect(color, size = Size(3.dp.toPx(), size.height)) }
            .tapTarget(Role.Button, onClick = onClick)
            .padding(start = 13.dp, top = 10.dp, end = 11.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardIcon(g.icon, color, Modifier.size(18.dp))
            Text(
                buildAnnotatedString {
                    append(g.short)
                    if (g.focus)
                        withStyle(SpanStyle(color = c.gold, fontSize = 11.sp)) { append(" ★") }
                },
                Modifier.padding(horizontal = 7.dp).weight(1f),
                color = c.text,
                fontSize = 12.5.sp,
                fontWeight = FontWeight(750),
                lineHeight = 18.75.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusDot(g.dot.kind, c, Modifier.semantics { contentDescription = g.dot.text })
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                g.level,
                Modifier.alignByBaseline(),
                color = color,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.34).sp,
                lineHeight = 25.5.sp,
            )
            Text(
                g.week,
                Modifier.alignByBaseline(),
                color = c.muted,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 15.75.sp,
            )
        }
        ProgressBar(
            g.pct / 100f,
            barFade(color),
            Modifier.semantics { invisibleToUser() },
            height = 6.dp,
            track = c.bg,
            outline = c.lineSoft,
            inset = 1.dp,
        )
    }
}

/**
 * `.ov-dot.s-<kind>`: an 8 px dot, ringed 3 px (box-shadow 0 0 0 3px) for today, due soon and
 * fading; a kind the page does not style is its plain muted dot.
 */
@Composable
internal fun StatusDot(kind: String, c: PageColors, modifier: Modifier = Modifier) {
    val (dot, ring) =
        when (kind) {
            "today" -> c.good to c.good.copy(alpha = .25f)
            "ok" -> SafeBlue.copy(alpha = .95f) to Color.Transparent
            "soon" -> c.gold to c.gold.copy(alpha = .25f)
            "decay" -> c.bad to c.bad.copy(alpha = .28f)
            else -> c.muted.copy(alpha = .5f) to Color.Transparent
        }
    Box(
        modifier.size(8.dp).drawBehind {
            if (ring.alpha > 0f) drawCircle(ring, radius = size.minDimension / 2 + 3.dp.toPx())
            drawCircle(dot)
        }
    )
}

/** `.ov-dot.s-ok`'s fixed blue (03-features.css:240). */
private val SafeBlue = Color(0xFF6EA0FF)
private val GemShape = RoundedCornerShape(13.dp)
private val GemGap = 9.dp
