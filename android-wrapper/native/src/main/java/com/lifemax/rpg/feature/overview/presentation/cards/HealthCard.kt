package com.lifemax.rpg.feature.overview.presentation.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.feature.overview.domain.HealthDay
import com.lifemax.rpg.feature.overview.domain.HealthTile

/**
 * The Activity today card (ovHealthHtml). The whole card opens the Health page ([onOpen], the
 * `health` command), as the page's does, but for its button: with health sync off that is "Set up
 * health sync" ([onSetup], `{view:'settings'}`). With it on: today's steps against the goal, the
 * last seven days as mini-bars, and the latest workouts, sleep and weight.
 */
@Composable
fun HealthCard(
    tile: HealthTile,
    onOpen: () -> Unit,
    onSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    CardsBFrame(modifier, onClick = onOpen) {
        when (tile) {
            is HealthTile.Off -> {
                CardsBHead(tile.title)
                Text(
                    tile.text,
                    Modifier.padding(bottom = 10.dp),
                    color = c.muted,
                    fontSize = 13.sp,
                    lineHeight = 19.5.sp,
                )
                CardsBSmallButton(tile.button, onSetup)
            }
            is HealthTile.On -> On(tile)
        }
    }
}

@Composable
private fun On(tile: HealthTile.On) {
    val c = LocalPageColors.current
    CardsBHead(tile.title, below = 6.dp, gap = 8.dp) { CardsBNote(tile.meta) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = c.text,
                        fontSize = 30.sp,
                        fontWeight = FontWeight(750),
                        letterSpacing = (-0.5).sp,
                    )
                ) {
                    append(tile.steps)
                }
                withStyle(SpanStyle(color = c.muted, fontSize = 13.sp)) { append(" " + tile.unit) }
            },
            Modifier.alignByBaseline(),
            lineHeight = 45.sp,
            softWrap = false,
        )
        Text(
            tile.sub,
            Modifier.alignByBaseline().weight(1f),
            color = c.muted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.End,
        )
    }
    // `.ovh-bar`: today against the goal
    Box(
        Modifier.padding(top = 6.dp, bottom = 10.dp)
            .fillMaxWidth()
            .height(8.dp)
            .background(c.lineSoft, CircleShape)
            .clearAndSetSemantics {}
    ) {
        Box(
            Modifier.fillMaxWidth(tile.pct.coerceIn(0, 100) / 100f)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(StepsFaint, Steps)), CircleShape)
        )
    }
    if (tile.days.isNotEmpty()) Days(tile.days)
    CardsBChips(tile.chips)
    Text(
        tile.more,
        Modifier.fillMaxWidth().padding(top = 10.dp),
        color = c.muted,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        textAlign = TextAlign.End,
    )
}

/**
 * `.ovh-days`: the mini-bars, at most 26 px wide, today's in green; TalkBack reads each day's steps
 * (the page's `title`).
 */
@Composable
private fun Days(days: List<HealthDay>) {
    val c = LocalPageColors.current
    // 56 px holds the 38 px bar, the gap and the letter's 15 px line; a larger letter adds its
    // growth
    val letter = with(LocalDensity.current) { (10.sp * 1.5f).toDp() }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).height(56.dp + letter - 15.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEach { d ->
            Column(
                Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) {
                    contentDescription = d.label + " " + d.a11y
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.Bottom),
            ) {
                Box(
                    Modifier.widthIn(max = CardsBGeometry.HEALTH_WIDEST.dp)
                        .fillMaxWidth()
                        .height(CardsBGeometry.healthBar(d.frac).dp)
                        .background(if (d.now) Steps else c.panel3, DayShape)
                )
                Text(
                    d.label,
                    Modifier.clearAndSetSemantics {},
                    color = c.muted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

/** Health's green (`#34d399`) and its faded start (`#34d39988`), fixed in the page's styles. */
private val Steps = Color(0xFF34D399)
private val StepsFaint = Color(0x8834D399)
private val DayShape = RoundedCornerShape(5.dp, 5.dp, 2.dp, 2.dp)
