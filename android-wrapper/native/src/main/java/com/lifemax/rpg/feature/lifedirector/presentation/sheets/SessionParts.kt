package com.lifemax.rpg.feature.lifedirector.presentation.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.feature.lifedirector.domain.SheetWhy
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText
import com.lifemax.rpg.feature.lifedirector.presentation.components.SheetMuted

/**
 * The minutes field (the page's number input, 1..480): five minutes fewer, the field, five more.
 * The page keeps the limits; the screen sends what is typed, kept within them.
 */
@Composable
internal fun MinutesStepper(
    text: String,
    label: String,
    onText: (String) -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", FEWER) { onStep(-STEP) }
        val shape = RoundedCornerShape(11.dp)
        BasicTextField(
            text,
            onText,
            Modifier.weight(1f)
                .heightIn(min = 44.dp)
                .clip(shape)
                .background(FIELD, shape)
                .border(1.dp, P.line2, shape)
                .padding(horizontal = 12.dp, vertical = 11.dp)
                .semantics { contentDescription = label },
            textStyle =
                DirectorText.copy(color = P.ink, fontSize = 15.2.sp, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            cursorBrush = SolidColor(P.sky),
        )
        StepButton("+", MORE) { onStep(STEP) }
    }
}

@Composable
private fun StepButton(sign: String, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        Modifier.size(44.dp)
            .clip(shape)
            .background(P.buttonFill, shape)
            .border(1.dp, P.line2, shape)
            .tapTarget(Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            sign,
            Modifier.clearAndSetSemantics {},
            style = DirectorText,
            color = P.ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * "Why this time?" (.ld-why-box), folded as the page's details are: the engine's summary, a bar per
 * factor on the 24-point scale with its points, and on how many days it could have fitted.
 */
@Composable
internal fun WhyFold(why: SheetWhy, initiallyOpen: Boolean) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.padding(top = 14.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(WHY_FILL, shape)
            .border(1.dp, P.line, shape)
    ) {
        Row(
            Modifier.fillMaxWidth()
                .tapTarget(Role.Button) { open = !open }
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Marker(open)
            Text(why.title, style = DirectorText, color = P.ink2, fontWeight = FontWeight(650))
        }
        if (open)
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp)) {
                Text(why.summary, style = DirectorText, color = P.ink2, fontSize = 14.08.sp)
                if (why.factors.isNotEmpty())
                    Column(
                        Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        why.factors.forEach { f ->
                            Row(
                                Modifier.semantics(mergeDescendants = true) {},
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    f.name,
                                    Modifier.weight(1f),
                                    style = DirectorText,
                                    color = P.ink3,
                                    fontSize = 12.16.sp,
                                )
                                Box(Modifier.size(90.dp, 6.dp)) {
                                    Box(
                                        Modifier.fillMaxWidth(f.frac)
                                            .fillMaxHeight()
                                            .background(P.task, RoundedCornerShape(99.dp))
                                    )
                                }
                                Text(
                                    f.points,
                                    Modifier.widthIn(min = 24.dp),
                                    style = DirectorText,
                                    color = P.ink2,
                                    fontSize = 12.16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                SheetMuted(why.feasible, 13.76f)
            }
    }
}

/** The details' disclosure triangle: pointing right when folded, down when open. */
@Composable
internal fun Marker(open: Boolean) =
    Canvas(Modifier.size(8.dp).rotate(if (open) 90f else 0f)) {
        val path =
            Path().apply {
                moveTo(size.width * .15f, 0f)
                lineTo(size.width, size.height / 2)
                lineTo(size.width * .15f, size.height)
                close()
            }
        drawPath(path, P.ink2)
    }

private const val STEP = 5
private const val FEWER = "5 minutes fewer"
private const val MORE = "5 minutes more"
private val FIELD = Color(0xFF0B1019)
private val WHY_FILL = Color.White.copy(alpha = .025f)
