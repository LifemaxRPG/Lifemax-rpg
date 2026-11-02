package com.lifemax.rpg.feature.journal.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.core.designsystem.components.PageEyebrow
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.core.designsystem.cssLinearGradient
import com.lifemax.rpg.core.designsystem.mix
import com.lifemax.rpg.feature.journal.domain.HeroRhythm
import com.lifemax.rpg.feature.journal.domain.HeroStat
import com.lifemax.rpg.feature.journal.domain.JournalHero
import com.lifemax.rpg.feature.journal.domain.RhythmCell

/**
 * The Journal's hero (.jx-hero): the chronicle's title and the Backup button, the four stats in
 * their colours, and the last four weeks as 14 × 2 cells in each day's archetype colour, brighter
 * for a bigger day, glowing, today outlined.
 */
@Composable
internal fun JournalHero(hero: JournalHero, onBackup: () -> Unit) {
    val c = LocalJournalColors.current
    Column(
        Modifier.padding(bottom = 14.dp)
            .fillMaxWidth()
            .then(with(c) { Modifier.journalShadow(HeroShape) })
            .clip(HeroShape)
            .background(c.panelFade)
            .background(c.heroGlowAmber)
            .background(c.heroGlowBlue)
            .innerTopEdge(HeroShape, c.heroEdge)
            .border(1.dp, c.line, HeroShape)
            .padding(start = 19.dp, end = 19.dp, top = 19.dp, bottom = 17.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                PageEyebrow(hero.eyebrow)
                Text(
                    hero.title,
                    Modifier.padding(top = 2.dp).semantics { heading() },
                    color = c.text,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.2).sp,
                    lineHeight = 31.5.sp,
                )
            }
            GhostButton(hero.backup.label, hero.backup.spoken, onBackup)
        }
        // four across as on the page; with large text (a font scale of 1.5 or more) two across, so
        // the numbers and labels keep whole words
        val across = if (LocalDensity.current.fontScale >= 1.5f) 2 else 4
        Column(
            Modifier.padding(top = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            hero.stats.chunked(across).forEach { row ->
                Row(
                    Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { HeroStatCard(it, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
        Rhythm(hero.rhythm)
    }
}

/** The hero's Backup (.jx-ghost): a quiet pill. */
@Composable
private fun GhostButton(text: String, spoken: String, onClick: () -> Unit) {
    val c = LocalJournalColors.current
    val shape = RoundedCornerShape(99.dp)
    Box(
        // the 48 dp touch area lies around the drawn 34 dp pill, as on the page
        Modifier.padding(start = 10.dp)
            .tapTarget(Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken }
            .clip(shape)
            .background(c.ghostFill)
            .border(1.dp, c.line, shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = c.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.48.sp,
            lineHeight = 18.sp,
        )
    }
}

/** A stat (.jx-stat): its colour washing down from the top, the number, the label in capitals. */
@Composable
private fun HeroStatCard(stat: HeroStat, modifier: Modifier) {
    val c = LocalJournalColors.current
    val tint = argb(stat.color)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(c.statBase)
            // linear-gradient(180deg, tint 14%, transparent 120%): at the bottom edge 1/6 of 14% is
            // left
            .background(
                cssLinearGradient(
                    180f,
                    0f to tint.copy(alpha = 0.14f),
                    1f to tint.copy(alpha = 0.14f / 6),
                )
            )
            .border(1.dp, tint.mix(0.22f, c.line), shape)
            .padding(start = 9.dp, end = 9.dp, top = 11.dp, bottom = 10.dp)
    ) {
        Text(
            stat.value,
            color = c.text,
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.63).sp,
            lineHeight = 23.1.sp,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
        // a one-word label wider than the card (Roboto is wider than the page's Inter) runs into
        // the
        // padding rather than breaking mid-word; labels of several words wrap between words
        val oneWord = ' ' !in stat.label.trim()
        Text(
            stat.label.uppercase(),
            Modifier.padding(top = 3.dp)
                .then(
                    if (oneWord) Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                    else Modifier
                ),
            color = c.muted,
            softWrap = !oneWord,
            fontSize = 10.sp,
            letterSpacing = 0.4.sp,
            lineHeight = 15.sp,
        )
    }
}

/** The last four weeks (.jx-rhythm): the heading and legend, then 14 × 2 cells. */
@Composable
private fun Rhythm(rhythm: HeroRhythm) {
    val c = LocalJournalColors.current
    Row(Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
        Text(
            rhythm.title.uppercase(),
            Modifier.weight(1f),
            color = c.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.66.sp,
            lineHeight = 16.5.sp,
        )
        Text(
            rhythm.legend,
            color = c.muted2,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.5.sp,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rhythm.cells.chunked(COLUMNS).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { RhythmCellBox(it, Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * A day (.jx-cell): its colour at its opacity with a soft glow around it (never under it, the cell
 * being translucent), or the faint empty fill; today outlined 1 dp outside its edge.
 */
@Composable
private fun RhythmCellBox(cell: RhythmCell, modifier: Modifier) {
    val c = LocalJournalColors.current
    val tint = if (cell.off) null else argb(cell.color)
    Box(
        modifier
            .aspectRatio(1f)
            .then(if (cell.today) Modifier.todayOutline(c.cellToday) else Modifier)
            .then(
                if (tint == null) Modifier
                else Modifier.boxShadow(tint, 10.dp, CellShape, spread = (-3).dp, clipOutBox = true)
            )
            .clip(CellShape)
            .background(if (tint == null) c.cellOff else tint.copy(alpha = tint.alpha * cell.alpha))
            .semantics { contentDescription = cell.label }
    )
}

/** CSS `outline: 2px solid; outline-offset: 1px` around a cell of 5 dp corners. */
private fun Modifier.todayOutline(color: androidx.compose.ui.graphics.Color) = drawWithCache {
    val w = 2.dp.toPx()
    val reach = 1.dp.toPx() + w / 2
    val r = CornerRadius(5.dp.toPx() + reach)
    onDrawBehind {
        drawRoundRect(
            color,
            Offset(-reach, -reach),
            Size(size.width + 2 * reach, size.height + 2 * reach),
            r,
            style = Stroke(w),
        )
    }
}

/**
 * CSS `box-shadow: inset 0 1px 0 color` on a box of [shape]: the band between the shape and the
 * shape moved down 1 dp, along the top edge and its corners.
 */
internal fun Modifier.innerTopEdge(shape: Shape, color: androidx.compose.ui.graphics.Color) =
    drawWithCache {
        val box =
            Path().apply {
                addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
            }
        val lower = Path().apply { addPath(box, Offset(0f, 1.dp.toPx())) }
        val band = Path().apply { op(box, lower, PathOperation.Difference) }
        onDrawWithContent {
            drawContent()
            drawPath(band, color)
        }
    }

private val HeroShape = RoundedCornerShape(24.dp)
private val CellShape = RoundedCornerShape(5.dp)
private const val COLUMNS = 14
