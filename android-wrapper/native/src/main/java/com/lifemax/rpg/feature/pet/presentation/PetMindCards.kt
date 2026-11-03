package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.feature.pet.domain.PetInner
import com.lifemax.rpg.feature.pet.domain.PetLikeRow
import com.lifemax.rpg.feature.pet.domain.PetLikes
import com.lifemax.rpg.feature.pet.domain.PetMind
import com.lifemax.rpg.feature.pet.domain.PetPanelWords

/*
 * The cat's mind, as the page's Cat view shows it: the mind row inside the cat's card, and the two
 * collapsible cards under it, what the cat likes and its inner life. All words are the page's
 * (50-native-pet-panels.js); a part the page could not build is simply not drawn.
 */

/** The moods the page tints (`.pet-mood-chip[data-m=...]`); the others stay neutral. */
private val WARM_MOODS = setOf("playful", "bright")
private val CALM_MOODS = setOf("content", "calm", "drowsy")
private val ALARM_MOODS = setOf("overstimulated", "uneasy")

private fun moodTone(mood: String) =
    when (mood) {
        in WARM_MOODS -> MoodWarm
        in CALM_MOODS -> MoodCalm
        in ALARM_MOODS -> MoodAlarm
        else -> null
    }

/**
 * `.pet-mind`: the bond style chip and the mood chip, the sentence about how the cat feels, and,
 * while it is fresh, what it did while the owner was away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PetMindRow(mind: PetMind, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    Column(modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PetChip(mind.styleName, CatPink, upper = true)
            PetChip(
                mind.mood.replaceFirstChar { it.uppercase() },
                moodTone(mind.mood),
                bold = false,
                modifier = Modifier.semantics { stateDescription = "Mood" },
            )
        }
        Text(
            mind.text,
            Modifier.padding(top = 7.dp),
            color = c.textDim,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
        )
        if (mind.away.isNotEmpty())
            PetAwayNote(mind.awayTitle, mind.away, Modifier.padding(top = 7.dp))
    }
}

/** `#petLikes`: the places and toys the cat likes, as bars from the leader down. */
@Composable
internal fun PetLikesCard(
    likes: PetLikes,
    words: PetPanelWords,
    modifier: Modifier = Modifier,
    startOpen: Boolean = false,
) {
    PetCollapsibleCard(words.likes, modifier, startOpen) {
        PetLead(likes.lead, likes.body)
        if (likes.places.isEmpty()) PetNoteLine(likes.noPlaces)
        else {
            PetSectionHeading(likes.placesTitle)
            likes.places.forEach { PetLikeBar(it) }
        }
        if (likes.toys.isNotEmpty()) {
            PetSectionHeading(likes.toysTitle)
            likes.toys.forEach { PetLikeBar(it) }
        }
    }
}

@Composable
private fun PetNoteLine(text: String) {
    Text(
        text,
        Modifier.padding(vertical = 4.dp),
        color = LocalPageColors.current.muted,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
    )
}

/** `.pet-like`: a place or toy with a heart when it is the favourite, and its bar. */
@Composable
private fun PetLikeBar(row: PetLikeRow) {
    val c = LocalPageColors.current
    val mark = if (row.favourite) " ♥" else ""
    val bar: @Composable (Modifier) -> Unit = { PetBarFill(row.pct, it) }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).semantics(mergeDescendants = true) {
            if (row.favourite) stateDescription = "Favorite"
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (panelLargeText()) {
            Text(row.name + mark, color = c.text, fontSize = 12.5.sp, lineHeight = 17.sp)
            bar(Modifier)
        } else
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.name + mark,
                    Modifier.weight(1.3f),
                    color = c.text,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    softWrap = false,
                )
                bar(Modifier.weight(1f))
            }
    }
}

/**
 * `#petInner`: the four threads of the bond, the quirks, the moments remembered and the small
 * print.
 */
@Composable
internal fun PetInnerCard(
    inner: PetInner,
    words: PetPanelWords,
    modifier: Modifier = Modifier,
    startOpen: Boolean = false,
) {
    val c = LocalPageColors.current
    PetCollapsibleCard(words.inner, modifier, startOpen) {
        PetLead(inner.lead, inner.body)
        inner.threads.forEach { PetBarRow(it) }
        PetSectionHeading(inner.quirksTitle)
        inner.quirks.forEach { q ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 5.dp).semantics(
                    mergeDescendants = true
                ) {}
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(q.name, color = c.text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Text(q.source, color = c.muted, fontSize = 10.5.sp, lineHeight = 17.sp)
                }
                Text(q.text, color = c.textDim, fontSize = 12.5.sp, lineHeight = 17.5.sp)
            }
        }
        if (inner.memories.isNotEmpty()) {
            PetSectionHeading(inner.memoriesTitle)
            inner.memories.forEach { m ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(
                        mergeDescendants = true
                    ) {}
                ) {
                    Text(
                        if (m.zone.isEmpty()) m.text else "${m.text} · ${m.zone}",
                        color = c.text,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                    )
                    Text(m.ago, color = c.muted, fontSize = 11.sp)
                }
            }
        }
        if (inner.stats.isNotEmpty())
            Text(
                inner.stats,
                Modifier.padding(top = 10.dp),
                color = c.muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        if (inner.comfort.isNotEmpty())
            Text(
                inner.comfort,
                Modifier.padding(top = 8.dp),
                color = c.muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
    }
}
