package com.lifemax.rpg.feature.lifedirector.presentation.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.feature.lifedirector.domain.GuideSpan
import com.lifemax.rpg.feature.lifedirector.domain.SessionSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.SessionState
import com.lifemax.rpg.feature.lifedirector.domain.SheetRowButton
import com.lifemax.rpg.feature.lifedirector.presentation.components.ButtonLook
import com.lifemax.rpg.feature.lifedirector.presentation.components.ChipRow
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorButton
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorSheet
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText
import com.lifemax.rpg.feature.lifedirector.presentation.components.SheetMuted

/** What the session sheet can ask for. */
class SessionActions(
    val close: () -> Unit = {},
    val minutes: (String) -> Unit = {},
    val step: (Int) -> Unit = {},
    val primary: () -> Unit = {},
    val complete: () -> Unit = {},
    val row: (SheetRowButton) -> Unit = {},
)

/**
 * The page's session sheet (instSheet) on Life Director's sheet: the header with its state's rail
 * and the session's icon, when, the chips, then its mode's body (Start timer or Open timer, the
 * minutes and Mark done, and the row; or the note, Log it for today and its small print; or what
 * happened), "Why this time?" folded, and the orphan warning. [minutes] is the field's text;
 * [pending] is the write in flight, while the buttons wait.
 */
@Composable
fun BoxScope.SessionSheet(
    s: SessionSheetModel,
    minutes: String,
    pending: String?,
    bottomInset: Dp,
    actions: SessionActions,
    whyOpen: Boolean = false,
) =
    DirectorSheet(s.title, bottomInset, actions.close) {
        SessionHead(s)
        if (s.chips.isNotEmpty()) ChipRow(s.chips, Modifier.padding(top = 6.dp))
        if (s.note.isNotEmpty()) Note(s.note, P.ink, NOTE_FILL)
        val wait = Modifier.alpha(if (pending != null) .45f else 1f)
        s.primary?.let {
            DirectorButton(
                it.label,
                ButtonLook.PRIMARY,
                actions.primary,
                wait.padding(top = 16.dp).fillMaxWidth(),
            )
        }
        val m = s.minutes
        val c = s.complete
        if (m != null && c != null)
            CompleteBox(
                m.label,
                minutes,
                c.label,
                wait,
                actions.minutes,
                actions.step,
                actions.complete,
            )
        c?.smallPrint?.let { SheetMuted(it, 12.48f) }
        if (s.row.isNotEmpty()) SessionRow(s.row, wait, actions.row)
        SheetMuted(s.past, 13.76f)
        WhyFold(s.why, whyOpen)
        if (s.orphan.isNotEmpty()) Note(listOf(GuideSpan(s.orphan, false)), P.amber, P.warnNote)
    }

/** .ld-sh-head: a 4 dp glowing rail in the state's colour beside the title and when. */
@Composable
private fun SessionHead(s: SessionSheetModel) {
    val rail = if (s.state == SessionState.OFF) P.block else P.task
    val shape = RoundedCornerShape(4.dp)
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier.width(4.dp)
                .fillMaxHeight()
                .boxShadow(rail, 10.dp, shape)
                .background(rail, shape)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Row(
                Modifier.padding(end = 40.dp).semantics(mergeDescendants = true) { heading() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PixelIcon(s.icon, P.ink, Modifier.size(16.dp))
                Text(
                    s.title,
                    style = DirectorText,
                    fontSize = 20.8.sp,
                    fontWeight = FontWeight(750),
                    letterSpacing = (-.03).em,
                    lineHeight = 1.2.em,
                )
            }
            Text(
                s.`when`,
                Modifier.padding(top = 5.dp),
                style = DirectorText,
                color = P.ink2,
                fontSize = 14.08.sp,
            )
        }
    }
}

/** .ld-note: a soft box of text with its bold spans; amber on a warm wash for a warning. */
@Composable
private fun Note(spans: List<GuideSpan>, ink: Color, fill: Color) =
    Text(
        buildAnnotatedString {
            spans.forEach {
                if (it.b) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(it.t) }
                else append(it.t)
            }
        },
        Modifier.padding(vertical = 10.dp)
            .fillMaxWidth()
            .background(fill, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        style = DirectorText,
        color = ink,
        fontSize = 14.08.sp,
    )

/** .ld-complete: the minutes, with a stepper, and Mark done or Log it for today beside them. */
@Composable
private fun CompleteBox(
    label: String,
    minutes: String,
    button: String,
    wait: Modifier,
    onMinutes: (String) -> Unit,
    onStep: (Int) -> Unit,
    onComplete: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.padding(top = 12.dp)
            .fillMaxWidth()
            .background(P.restCard, shape)
            .border(1.dp, P.line, shape)
            .padding(12.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = DirectorText,
                color = P.ink2,
                fontSize = 13.12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            MinutesStepper(minutes, label, onMinutes, onStep, Modifier.padding(top = 5.dp))
        }
        DirectorButton(button, ButtonLook.PLAIN, onComplete, wait)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionRow(row: List<SheetRowButton>, wait: Modifier, onTap: (SheetRowButton) -> Unit) =
    FlowRow(
        Modifier.padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        row.forEach { b ->
            DirectorButton(
                b.label,
                if (b.command == "skip") ButtonLook.GHOST else ButtonLook.PLAIN,
                { onTap(b) },
                wait,
                small = true,
            )
        }
    }

private val NOTE_FILL = Color.White.copy(alpha = .04f)
