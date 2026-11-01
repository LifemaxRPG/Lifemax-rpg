package com.lifemax.rpg.feature.lifedirector.presentation.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.lifedirector.domain.DirectorGoals
import com.lifemax.rpg.feature.lifedirector.domain.GoalCard
import com.lifemax.rpg.feature.lifedirector.domain.TitledText
import com.lifemax.rpg.feature.lifedirector.presentation.components.ButtonLook
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorButton
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText

/**
 * What a goal card can ask for: a priority, pause or resume, remove (which asks first), a kind of
 * goal.
 */
class GoalActions(
    val priority: (id: String, value: Int) -> Unit = { _, _ -> },
    val pause: (id: String, paused: Boolean) -> Unit = { _, _ -> },
    val remove: (id: String) -> Unit = {},
    val clarify: (id: String, arch: String) -> Unit = { _, _ -> },
)

/** .ld-explain: how time is shared between goals. */
@Composable
fun GoalsExplain(t: TitledText) = DirectorCard {
    Column {
        CardTitle(t.title)
        Text(
            t.text,
            Modifier.padding(top = 6.dp),
            style = DirectorText,
            color = P.ink2,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
    }
}

/** No goals yet (.ld-empty). */
@Composable
fun GoalsEmpty(t: TitledText) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth()
            .padding(vertical = 10.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = .025f))
            .border(1.dp, P.line, shape)
            .padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            t.title,
            style = DirectorText,
            color = P.ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            t.text,
            Modifier.padding(top = 8.dp),
            style = DirectorText,
            color = P.ink2,
            fontSize = 14.7.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A goal (.ld-goal): its name, kind and level, its priority, its share of the week, a warning or a
 * question, pause and remove.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalCardView(card: GoalCard, goals: DirectorGoals, pending: Boolean, actions: GoalActions) =
    DirectorCard {
        Column(Modifier.alpha(if (card.paused) .6f else 1f)) {
            Text(
                card.title,
                style = DirectorText,
                color = P.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(card.sub, style = DirectorText, color = P.ink3, fontSize = 12.5.sp)
            // the page's priority select, as the same five words in a row
            Segmented(
                goals.priorities,
                card.priority.toString(),
                !pending,
                Modifier.padding(top = 10.dp),
            ) {
                actions.priority(card.id, it.toInt())
            }
            card.share?.let { s ->
                Column(
                    Modifier.padding(top = 12.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ShareBar("Target share", s.target, s.targetText, Color.White.copy(alpha = .28f))
                    ShareBar("Scheduled", s.scheduled, s.scheduledText, P.task)
                }
                Muted(s.line)
            }
            card.warning?.let { Note(it, P.amber, P.work.copy(alpha = .08f)) }
            card.clarify?.let { c ->
                Column(
                    Modifier.fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = .04f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(c.text, style = DirectorText, color = P.ink2, fontSize = 14.sp)
                    FlowRow(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        c.options.forEach { o ->
                            DirectorButton(
                                o.label,
                                ButtonLook.GHOST,
                                { if (!pending) actions.clarify(card.id, o.value) },
                                small = true,
                            )
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DirectorButton(
                    card.pause,
                    ButtonLook.GHOST,
                    { if (!pending) actions.pause(card.id, !card.paused) },
                    small = true,
                )
                DirectorButton(
                    card.remove,
                    ButtonLook.GHOST,
                    { if (!pending) actions.remove(card.id) },
                    small = true,
                )
            }
        }
    }

/** .ld-share: a label, the bar and the page's percentage. */
@Composable
private fun ShareBar(label: String, value: Float, text: String, fill: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, Modifier.width(90.dp), style = DirectorText, color = P.ink3, fontSize = 12.5.sp)
        Row(
            Modifier.weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = .06f))
        ) {
            if (value > 0f)
                Box(
                    Modifier.weight(value)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(99.dp))
                        .background(fill)
                )
            if (value < 1f) Box(Modifier.weight(1f - value))
        }
        Text(
            text,
            Modifier.width(42.dp),
            style = DirectorText,
            color = P.ink2,
            fontSize = 12.8.sp,
            textAlign = TextAlign.End,
        )
    }
}

/** .ld-note, optionally tinted (.ld-warn-t). */
@Composable
internal fun Note(text: String, color: Color, fill: Color) =
    Text(
        text,
        Modifier.fillMaxWidth()
            .padding(vertical = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        style = DirectorText,
        color = color,
        fontSize = 14.sp,
    )
