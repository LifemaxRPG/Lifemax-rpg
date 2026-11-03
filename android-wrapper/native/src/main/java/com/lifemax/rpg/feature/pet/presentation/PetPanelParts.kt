package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.components.PageCard
import com.lifemax.rpg.core.designsystem.components.ProgressBar
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.feature.pet.domain.PetBar
import com.lifemax.rpg.feature.pet.domain.PetLine

/*
 * The small pieces the Cat tab's panels share, as the page's CSS draws them (.pet-likes-h,
 * .pet-thread, .pet-like, .pet-style, .pet-mood-chip, .pet-away): the pink the page fixes for the
 * cat's own things is named here, the rest are the page's tokens.
 */

/** The pink of the cat's bars and chips (`#f0718f`) and the page's mood colours. */
internal val CatPink = Color(0xFFF0718F)
internal val MoodWarm = Color(0xFFE9A23B)
internal val MoodCalm = Color(0xFF6FBF8E)
internal val MoodAlarm = Color(0xFFE06C6C)

/** The orange of the friendship level (`#e8a04f`) and the Together bar's two ends. */
internal val LevelOrange = Color(0xFFE8A04F)
internal val TogetherFrom = Color(0xFFFFD27A)
internal val TogetherTo = Color(0xFFFF9B6B)

/** At this font scale and above a row's two halves stack so that no word breaks. */
private const val LARGE_TEXT = 1.3f

@Composable internal fun panelLargeText(): Boolean = LocalDensity.current.fontScale >= LARGE_TEXT

private val BarFill = Brush.horizontalGradient(listOf(CatPink.copy(alpha = .6f), CatPink))
private val ChipShape = RoundedCornerShape(50)
private val BoxShape = RoundedCornerShape(12.dp)

/**
 * A collapsible card, closed to begin with unless [startOpen], as the page's "Care and settings"
 * is: a heading row that opens and closes it (announced as open or closed) over [content].
 */
@Composable
internal fun PetCollapsibleCard(
    title: String,
    modifier: Modifier = Modifier,
    startOpen: Boolean = false,
    onToggled: (Boolean) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalPageColors.current
    var open by remember { mutableStateOf(startOpen) }
    PageCard(modifier.fillMaxWidth(), padding = 15.dp) {
        Row(
            Modifier.fillMaxWidth()
                .semantics { stateDescription = if (open) "Open" else "Closed" }
                .tapTarget(Role.Button) {
                    open = !open
                    onToggled(open)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                title,
                Modifier.weight(1f, fill = false).semantics { heading() },
                color = c.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(if (open) "▾" else "▸", color = c.muted, fontSize = 15.sp)
        }
        if (open) Column(Modifier.padding(top = 4.dp), content = content)
    }
}

/** `.pet-likes-h`: a small upper-case heading inside a panel. */
@Composable
internal fun PetSectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
        color = LocalPageColors.current.muted,
        fontSize = 11.sp,
        letterSpacing = .88.sp,
    )
}

/** A paragraph with a bold lead-in, the page's way of opening a panel ("What Miso likes. ..."). */
@Composable
internal fun PetLead(lead: String, body: String, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(lead) }
            append(body)
        },
        modifier.padding(vertical = 5.dp),
        color = LocalPageColors.current.textDim,
        fontSize = 13.sp,
        lineHeight = 19.5.sp,
    )
}

/** A small-print line with a bold lead-in ([PetLine]); nothing when the line is not there. */
@Composable
internal fun PetFinePrint(line: PetLine?, modifier: Modifier = Modifier) {
    if (line == null) return
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.bold) }
            append(line.rest)
        },
        modifier.padding(vertical = 3.dp),
        color = LocalPageColors.current.muted,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
}

/** `.pet-thread`: the name, a pink bar and the page's word for it under both. */
@Composable
internal fun PetBarRow(bar: PetBar, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    Column(
        modifier.fillMaxWidth().padding(vertical = 5.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (panelLargeText()) {
            Text(bar.label, color = c.text, fontSize = 12.5.sp, lineHeight = 17.sp)
            PetBarFill(bar.pct)
        } else
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    bar.label,
                    Modifier.weight(.9f),
                    color = c.text,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                )
                PetBarFill(bar.pct, Modifier.weight(.8f))
            }
        Text(bar.word, color = c.muted, fontSize = 11.5.sp, lineHeight = 16.sp)
    }
}

/** The pink bar (`.pet-thread i`): 6 dp, full width unless given a weight. */
@Composable
internal fun PetBarFill(pct: Int, modifier: Modifier = Modifier) {
    ProgressBar(
        pct / 100f,
        BarFill,
        modifier,
        height = 6.dp,
        track = LocalPageColors.current.lineSoft,
        outline = Color.Transparent,
    )
}

/**
 * A rounded chip: [text] in [tone] on a faint wash of it, with a border; [plain] uses the page's.
 */
@Composable
internal fun PetChip(
    text: String,
    tone: Color?,
    modifier: Modifier = Modifier,
    upper: Boolean = false,
    bold: Boolean = true,
) {
    val c = LocalPageColors.current
    Text(
        if (upper) text.uppercase() else text,
        modifier
            .clip(ChipShape)
            .background(tone?.copy(alpha = .13f) ?: c.panel2)
            .border(1.dp, tone?.copy(alpha = .4f) ?: c.lineSoft, ChipShape)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        color = tone ?: c.textDim,
        fontSize = 11.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight(650),
        letterSpacing = if (upper) .66.sp else 0.sp,
    )
}

/** `.pet-away`: the "while you were away" note, a titled box. */
@Composable
internal fun PetAwayNote(title: String, text: String, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(BoxShape)
            .background(c.panel2)
            .border(1.dp, c.lineSoft, BoxShape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Text(
            title.uppercase(),
            color = c.muted,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Text(text, color = c.text, fontSize = 12.5.sp, lineHeight = 18.sp)
    }
}
