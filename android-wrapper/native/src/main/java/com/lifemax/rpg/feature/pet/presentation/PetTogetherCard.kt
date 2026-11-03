package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.components.PageCard
import com.lifemax.rpg.core.designsystem.components.PageEyebrow
import com.lifemax.rpg.core.designsystem.components.ProgressBar
import com.lifemax.rpg.feature.pet.domain.PetPair
import com.lifemax.rpg.feature.pet.domain.PetPanelWords
import com.lifemax.rpg.feature.pet.domain.PetRank

/*
 * The Together card (`#petPair`) with the switcher between the two cats (`#petSwitch`): how well
 * the two get on, what they have done together, who is the confident one, and which cat the card
 * above is about. The pair's behaviour is the page's (and a later phase's); this is the card only.
 */

private val FocusPink = Color(0xFFFF7F9A)
private val PillShape = RoundedCornerShape(50)

/** The Together card for the two cats; a tap on a cat's pill asks the page to focus it. */
@Composable
internal fun PetTogetherCard(
    pair: PetPair,
    words: PetPanelWords,
    waiting: Boolean,
    onFocus: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val card = pair.card
    PageCard(modifier.fillMaxWidth(), padding = 14.dp) {
        Switcher(pair, words.switcher, waiting, onFocus)
        Header(card.title, card.first, card.second, card.level)
        ProgressBar(
            card.pct / 100f,
            Brush.horizontalGradient(listOf(TogetherFrom, TogetherTo)),
            Modifier.padding(top = 10.dp),
            track = c.bg,
            outline = c.lineSoft,
        )
        Text(
            card.text,
            Modifier.padding(start = 2.dp, top = 9.dp, end = 2.dp),
            color = c.text,
            fontSize = 13.sp,
            lineHeight = 18.85.sp,
        )
        if (card.away.isNotEmpty())
            PetAwayNote(card.awayTitle, card.away, Modifier.padding(top = 8.dp))
        if (card.counts.isNotEmpty())
            Text(
                card.counts,
                Modifier.padding(top = 8.dp),
                color = c.muted,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
            )
        card.rank?.let { Rank(it) }
        if (card.last.isNotEmpty())
            Text(
                card.last,
                Modifier.padding(top = 8.dp),
                color = c.muted,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
            )
    }
}

/**
 * `.pet-switch`: one pill per cat, the focused one outlined in pink; they stack with large text.
 */
@Composable
private fun Switcher(pair: PetPair, label: String, waiting: Boolean, onFocus: (Int) -> Unit) {
    val pill: @Composable (Modifier, Int) -> Unit = { m, i -> Pill(pair, i, waiting, onFocus, m) }
    val spacing = Arrangement.spacedBy(8.dp)
    if (panelLargeText())
        Column(
            Modifier.fillMaxWidth().padding(bottom = 10.dp).selectableGroup().semantics {
                contentDescription = label
            },
            verticalArrangement = spacing,
        ) {
            pair.cats.indices.forEach { pill(Modifier.fillMaxWidth(), it) }
        }
    else
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp).selectableGroup().semantics {
                contentDescription = label
            },
            horizontalArrangement = spacing,
        ) {
            pair.cats.indices.forEach { pill(Modifier.weight(1f), it) }
        }
}

@Composable
private fun Pill(
    pair: PetPair,
    i: Int,
    waiting: Boolean,
    onFocus: (Int) -> Unit,
    modifier: Modifier,
) {
    val c = LocalPageColors.current
    val on = pair.focus == i
    Box(
        modifier
            .alpha(if (waiting && !on) .6f else 1f)
            .clip(PillShape)
            .background(if (on) FocusPink.copy(alpha = .12f) else c.panel2)
            .border(1.dp, if (on) FocusPink else c.line, PillShape)
            .selectable(selected = on, enabled = !waiting, role = Role.Tab) { onFocus(i) }
            .minimumInteractiveComponentSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            pair.cats[i].name,
            color = if (on) c.text else c.textDim,
            fontSize = 13.sp,
            fontWeight = FontWeight(650),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * `.pet-pair-head`: TOGETHER over "A & B", and the friendship level beside it (under it with large
 * text).
 */
@Composable
private fun Header(title: String, first: String, second: String, level: String) {
    val c = LocalPageColors.current
    val names: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            PageEyebrow(title)
            Text(
                "$first & $second",
                Modifier.padding(top = 2.dp).semantics { heading() },
                color = c.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 21.sp,
            )
        }
    }
    if (panelLargeText())
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            names(Modifier)
            PetChip(level, LevelOrange, upper = true)
        }
    else
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            names(Modifier.weight(1f).padding(end = 10.dp))
            PetChip(level, LevelOrange, upper = true)
        }
}

/** `.pet-rank`: who is the confident one, in the page's line, and each cat's confidence. */
@Composable
private fun Rank(rank: PetRank) {
    val c = LocalPageColors.current
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        PageEyebrow(rank.title)
        Text(
            buildAnnotatedString {
                if (rank.line.bold.isNotEmpty())
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(rank.line.bold) }
                append(rank.line.rest)
            },
            Modifier.padding(top = 6.dp, bottom = 4.dp),
            color = c.text,
            fontSize = 13.sp,
            lineHeight = 18.85.sp,
        )
        rank.rows.forEach { PetBarRow(it) }
    }
}
