package com.lifemax.rpg.feature.journal.presentation.body

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.PageInk
import com.lifemax.rpg.core.designsystem.cssLinearGradient
import com.lifemax.rpg.feature.journal.domain.JournalReflect
import com.lifemax.rpg.feature.journal.domain.ReviewEntry

/*
 * The notebook boxes (.j-reflect.jx-reflect): the day's reflection with its question, meter and
 * counter, and a review's answer box. In phase 1 the field is not typed in here: a tap hands over
 * to the page's own field (`write {key}`), which the page focuses on that day.
 */

/**
 * The day's reflection box: the "Reflect" kicker, the question and its ↻ (`swap {d, from}`), or the
 * plain label when the care note quiets the coach; the ruled field; the meter and the counter.
 */
@Composable
internal fun ReflectionBox(
    day: String,
    reflect: JournalReflect,
    onWrite: (String) -> Unit,
    onSwap: (day: String, from: String) -> Unit,
) {
    val c = LocalJournalBodyColors.current
    NotebookBox {
        val prompt = reflect.prompt
        if (prompt != null) {
            Text(
                prompt.kicker.uppercase(),
                color = c.accentStrong,
                fontSize = 10.5.sp,
                fontWeight = FontWeight(800),
                letterSpacing = 1.26.sp,
                lineHeight = 14.7.sp,
            )
            Row(
                Modifier.padding(top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Question(prompt.text, Modifier.weight(1f))
                SwapButton(prompt.swap) { onSwap(day, prompt.id) }
            }
        } else if (reflect.label != null) {
            OptionalLabel(reflect.label)
        }
        NotebookField(reflect.text, reflect.placeholder) { onWrite(day) }
        Meter(reflect.meter)
        Counter(counterLine(reflect), counterOn(reflect), top = 7.dp)
    }
}

/** A review's answer box: the question, the ruled field and the counter. */
@Composable
internal fun ReviewAnswerBox(review: ReviewEntry, onWrite: (String) -> Unit) {
    NotebookBox {
        Question(review.question, Modifier.padding(bottom = 8.dp))
        NotebookField(review.answer, review.placeholder) { onWrite(review.key) }
        Counter(review.counter, review.paid, top = 7.dp)
    }
}

/** The box: the raised ground fading to the panel, its border and the light along its top. */
@Composable
private fun NotebookBox(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalJournalBodyColors.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(cssLinearGradient(180f, 0f to c.bgElev, 1f to c.panel))
            .insetTopEdge(JournalInk.white(0x0D), 20.dp)
            .border(1.dp, c.line, shape)
            .padding(17.dp),
        content = content,
    )
}

@Composable
private fun Question(text: String, modifier: Modifier = Modifier) {
    val c = LocalJournalBodyColors.current
    Text(
        text,
        modifier,
        color = c.text,
        fontSize = 16.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.33).sp,
        lineHeight = 23.1.sp,
    )
}

/**
 * The plain label ("Reflection (optional)"): the page draws the part in brackets lighter and not
 * bold.
 */
@Composable
private fun OptionalLabel(label: String) {
    val c = LocalJournalBodyColors.current
    val cut = label.indexOf(" (")
    Text(
        buildAnnotatedString {
            append(if (cut < 0) label else label.substring(0, cut))
            if (cut >= 0)
                withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = c.muted2)) {
                    append(label.substring(cut))
                }
        },
        Modifier.padding(bottom = 6.dp),
        color = c.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 18.sp,
    )
}

/** The ↻ (.jc-swap): a 34 dp ring, named for TalkBack as the page names it. */
@Composable
private fun SwapButton(label: String, onClick: () -> Unit) {
    val c = LocalJournalBodyColors.current
    Box(
        Modifier.size(34.dp)
            .clip(CircleShape)
            .background(JournalInk.white(0x10))
            .border(1.dp, JournalInk.white(0x2A), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spokenLabel(label)
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "↻",
            color = c.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 24.sp,
        )
    }
}

/**
 * The ruled field: the text (or the page's placeholder) on lines 28 dp apart, at least 124 dp tall,
 * growing with the text. A tap hands over to the page's field.
 */
@Composable
private fun NotebookField(text: String, placeholder: String, onClick: () -> Unit) {
    val c = LocalJournalBodyColors.current
    val shape = RoundedCornerShape(14.dp)
    val rule = JournalInk.white(0x0B)
    Box(
        Modifier.padding(top = 6.dp)
            .fillMaxWidth()
            .heightIn(min = 124.dp)
            .clip(shape)
            .background(c.textarea)
            .drawBehind {
                // repeating-linear-gradient(transparent 0 27px, #ffffff0b 27px 28px) from the
                // field's padding edge, scrolling with the text
                val step = 28.dp.toPx()
                val b = 1.dp.toPx()
                var y = b + step - b
                while (y < size.height - b) {
                    drawRect(rule, Offset(b, y), Size(size.width - 2 * b, b))
                    y += step
                }
            }
            .border(1.dp, c.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 15.dp, vertical = 11.dp)
    ) {
        Text(
            text.ifEmpty { placeholder },
            color = if (text.isEmpty()) PageInk.Placeholder else c.text,
            fontSize = 14.5.sp,
            lineHeight = 28.sp,
        )
    }
}

/** The meter under the reflection (.jx-meter): accent to green, [percent] of the way. */
@Composable
private fun Meter(percent: Double) {
    val c = LocalJournalBodyColors.current
    Box(
        Modifier.padding(top = 10.dp)
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(JournalInk.white(0x10))
    ) {
        if (percent > 0)
            Box(
                Modifier.fillMaxWidth((percent / 100).toFloat().coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(9.dp))
                    .background(cssLinearGradient(90f, 0f to c.accent, 1f to c.good))
            )
    }
}

/** The counter line (.jc-count), green when [on]. */
@Composable
private fun Counter(text: String, on: Boolean, top: androidx.compose.ui.unit.Dp) {
    val c = LocalJournalBodyColors.current
    Text(
        text,
        Modifier.padding(top = top),
        color = if (on) JournalInk.CounterOn else c.muted,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
}
