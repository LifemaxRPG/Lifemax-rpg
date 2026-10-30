package com.lifemax.rpg.feature.lifedirector.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.feature.lifedirector.domain.UpNext
import com.lifemax.rpg.feature.lifedirector.domain.UpNextAction
import com.lifemax.rpg.feature.lifedirector.domain.UpNextKind
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P

/**
 * What to do next (the web's .ld-up): the cue, the session and when, its quest and XP, and its
 * buttons. A running timer ticks here each second while [animate]. "See tomorrow" asks for a day.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpNextCard(up: UpNext, animate: Boolean, onAction: (UpNextAction) -> Unit) {
    val rest = up.kind == UpNextKind.REST
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp) // 16 on the web, collapsed with the mix's 4
            .then(
                if (rest) Modifier.background(P.restCard, shape).border(1.dp, P.line, shape)
                else
                    Modifier.background(greenWash, shape)
                        .border(1.dp, P.task.copy(alpha = .24f), shape)
            )
            .padding(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (rest) GlowDot(8.dp, P.ink3, 0.dp)
            else
                GlowDot(
                    8.dp,
                    P.task,
                    10.dp,
                    rememberPulse(animate && up.kind == UpNextKind.GO, 2000),
                )
            Text(
                up.cue,
                style = DirectorText,
                color = if (rest) P.ink2 else P.mint,
                fontSize = 12.8.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            up.title,
            Modifier.padding(top = 6.dp).semantics { heading() },
            style = DirectorText,
            fontSize = if (rest) 17.6.sp else 21.1.sp,
            fontWeight = FontWeight(750),
            letterSpacing = (-.03).em,
            lineHeight = 1.15.em,
        )
        val timer = up.timer
        if (timer == null) Timing(null, up.timing)
        else TimerClock(timer, animate) { clock -> Timing(clock, up.timing) }
        if (up.chips.isNotEmpty()) ChipRow(up.chips, Modifier.padding(top = 1.dp))
        if (up.actions.isNotEmpty())
            FlowRow(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                up.actions.forEach { action ->
                    DirectorButton(
                        action.label,
                        when {
                            action.primary -> ButtonLook.PRIMARY
                            action.ghost -> ButtonLook.GHOST
                            else -> ButtonLook.PLAIN
                        },
                        { onAction(action) },
                    )
                }
            }
    }
}

/** When, after a running timer's [clock] when there is one. */
@Composable
private fun Timing(clock: String?, timing: String) {
    Text(
        buildAnnotatedString {
            if (clock != null) {
                withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                ) {
                    append(clock)
                }
                append(" ")
            }
            append(timing)
        },
        Modifier.padding(vertical = 5.dp),
        style = DirectorText,
        color = P.ink2,
        fontSize = 14.1.sp,
    )
}

/** The quests that run all day, in one line (the web's .ld-remind); nothing here is a button. */
@Composable
fun RemindersCard(reminders: List<String>) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth()
            .padding(top = 6.dp, bottom = 12.dp) // 12 on the web, collapsed with what is above
            .background(cardWash, shape)
            .border(1.dp, P.work.copy(alpha = .22f), shape)
            .padding(16.dp)
    ) {
        Row(
            Modifier.padding(bottom = 4.dp).semantics(mergeDescendants = true) { heading() },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelIcon("flag", P.ink3, Modifier.size(12.dp))
            Text(
                "All-day quests",
                style = DirectorText,
                fontSize = 16.sp,
                fontWeight = FontWeight(750),
            )
        }
        Text(reminders.joinToString(", "), Modifier.padding(vertical = 5.dp), style = DirectorText)
    }
}

/** linear-gradient(160deg, rgba(16,185,129,.1), rgba(16,185,129,.02) 60%) */
private val greenWash =
    cssLinearGradient(160f, 0f to P.task.copy(alpha = .1f), .6f to P.task.copy(alpha = .02f))

/**
 * The web's .ld-card fill: linear-gradient(180deg, rgba(255,255,255,.035), rgba(255,255,255,.015)).
 */
private val cardWash = Brush.verticalGradient(listOf(P.cardTop, P.cardBottom))
