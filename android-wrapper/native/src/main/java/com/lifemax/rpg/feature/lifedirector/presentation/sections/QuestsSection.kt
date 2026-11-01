package com.lifemax.rpg.feature.lifedirector.presentation.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.feature.lifedirector.domain.DirectorQuests
import com.lifemax.rpg.feature.lifedirector.domain.Milestones
import com.lifemax.rpg.feature.lifedirector.domain.QuestRow
import com.lifemax.rpg.feature.lifedirector.domain.QuestTone
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.components.ButtonLook
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorButton
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText

/**
 * The page's Quests (renderQuests): the Quest Board card, each quest and what is planned for it,
 * then the milestones.
 */
@Composable
fun QuestBoardCard(q: DirectorQuests, onTap: (Tap) -> Unit) = DirectorCard {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (q.head.open.isNotEmpty()) {
                PixelIcon("flag", P.ink, Modifier.size(12.dp))
                Box(Modifier.width(6.dp))
            }
            CardTitle(q.head.title)
        }
        Text(
            q.head.text,
            Modifier.padding(top = 6.dp),
            style = DirectorText,
            color = P.ink2,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        if (q.head.open.isNotEmpty())
            DirectorButton(
                q.head.open,
                ButtonLook.GHOST,
                { onTap(Tap("openQuests")) },
                Modifier.padding(top = 10.dp),
                small = true,
            )
    }
}

/**
 * .ld-q: a card with its tone on the left edge (green planned or done, amber tracked all day, red
 * not placed).
 */
@Composable
fun QuestRowView(r: QuestRow) {
    val edge =
        when (r.tone) {
            QuestTone.OK -> P.task
            QuestTone.PAS -> P.work
            QuestTone.NO -> P.block
        }
    QuestCard(edge) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    r.title,
                    style = DirectorText,
                    color = P.ink,
                    fontSize = 14.7.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(r.whenText, style = DirectorText, color = P.ink3, fontSize = 12.2.sp)
            }
            Text(
                r.status,
                style = DirectorText,
                color = P.ink2,
                fontSize = 12.8.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (r.why.isNotEmpty())
            Text(
                r.why,
                Modifier.padding(top = 2.dp),
                style = DirectorText,
                color = P.ink3,
                fontSize = 12.8.sp,
            )
    }
}

@Composable fun QuestsEmpty(text: String) = Muted(text, Modifier.padding(vertical = 12.dp))

/** The milestones heading and its rows: earned in green, in progress in amber. */
@Composable
fun MilestonesHeading(m: Milestones) {
    Column(Modifier.padding(top = 22.dp, start = 2.dp, end = 2.dp)) {
        Row(Modifier.fillMaxWidth().semantics { heading() }, verticalAlignment = Alignment.Bottom) {
            Text(
                "Milestones",
                Modifier.weight(1f),
                style = DirectorText,
                color = P.ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                m.summary,
                style = DirectorText,
                color = P.ink3,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Muted(m.note, Modifier.padding(top = 8.dp))
    }
}

@Composable
fun MilestoneRowView(title: String, desc: String, earned: Boolean, status: String) =
    QuestCard(if (earned) P.task else P.work) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = DirectorText,
                    color = P.ink,
                    fontSize = 14.7.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(desc, style = DirectorText, color = P.ink3, fontSize = 12.2.sp)
            }
            Text(
                status,
                style = DirectorText,
                color = P.ink2,
                fontSize = 12.8.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

@Composable
private fun QuestCard(edge: Color, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = .03f))
            .border(1.dp, P.line, shape)
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(edge))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) { content() }
    }
}
