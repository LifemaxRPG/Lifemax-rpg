package com.lifemax.rpg.feature.plan.presentation.runner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.core.designsystem.mix
import com.lifemax.rpg.feature.plan.domain.PipState
import com.lifemax.rpg.feature.plan.domain.RunnerSegment
import com.lifemax.rpg.feature.plan.presentation.components.LocalPlanColors

/** The session clock as the page writes it (gwrFmtDuration): mm:ss, and h:mm:ss from an hour. */
internal fun formatDuration(ms: Long): String {
    val sec = (maxOf(0L, ms) / 1000)
    val m = sec / 60
    return if (m >= 60) "%d:%02d:%02d".format(m / 60, m % 60, sec % 60)
    else "%02d:%02d".format(m, sec % 60)
}

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** The big button at the foot of a screen (.gx-btn.primary). */
@Composable
internal fun DockButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val c = LocalPlanColors.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .clip(shape)
            .background(if (enabled) c.accent else c.panel3)
            .clickable(enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) c.onAccent else c.muted,
            fontSize = 18.sp,
            fontWeight = FontWeight(750),
            letterSpacing = (-0.18).sp,
            lineHeight = 18.sp,
        )
    }
}

/** The quiet words under it (.gx-link). */
@Composable
internal fun DockLink(text: String, onClick: () -> Unit) {
    val c = LocalPlanColors.current
    Box(
        Modifier.defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = c.muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp,
        )
    }
}

/** A pill button on the top bar (.gx-top-btn): an icon and, usually, a word. */
@Composable
internal fun TopPill(
    label: String,
    description: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val c = LocalPlanColors.current
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = if (label.isEmpty()) 0.dp else 15.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        if (label.isNotEmpty())
            Text(
                label,
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 14.sp,
            )
    }
}

@Composable
internal fun PauseGlyph() {
    val color = LocalPlanColors.current.text
    Canvas(Modifier.size(14.dp)) {
        val u = size.width / 16f
        drawRect(color, Offset(4 * u, 2 * u), Size(3 * u, 12 * u))
        drawRect(color, Offset(9 * u, 2 * u), Size(3 * u, 12 * u))
    }
}

@Composable
internal fun CloseGlyph() {
    val color = LocalPlanColors.current.text
    Canvas(Modifier.size(14.dp)) {
        val w = 2.dp.toPx()
        drawLine(
            color,
            Offset(size.width * .19f, size.height * .19f),
            Offset(size.width * .81f, size.height * .81f),
            w,
            StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(size.width * .81f, size.height * .19f),
            Offset(size.width * .19f, size.height * .81f),
            w,
            StrokeCap.Round,
        )
    }
}

@Composable
internal fun GridGlyph() = PixelIcon("grid", LocalPlanColors.current.text, Modifier.size(16.dp))

/** A tick, drawn as a stroke, in [color]. */
@Composable
internal fun CheckGlyph(color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val p =
            Path().apply {
                moveTo(this@Canvas.size.width * .2f, this@Canvas.size.height * .54f)
                lineTo(this@Canvas.size.width * .42f, this@Canvas.size.height * .74f)
                lineTo(this@Canvas.size.width * .8f, this@Canvas.size.height * .28f)
            }
        drawPath(p, color, style = Stroke(this.size.width * .12f, cap = StrokeCap.Round))
    }
}

/**
 * The route of the session above the screen (.gx-route): a segment per exercise, as long as its
 * sets.
 */
@Composable
internal fun RunnerRoute(segments: List<RunnerSegment>, description: String) {
    val c = LocalPlanColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp).semantics {
            contentDescription = description
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        segments.forEach { s ->
            val shape = RoundedCornerShape(3.dp)
            Box(
                Modifier.weight(s.weight.toFloat())
                    .height(6.dp)
                    .clip(shape)
                    .then(
                        when (s.state) {
                            "current" -> Modifier.background(c.accent.mix(0.28f, c.panel3))
                            else -> Modifier.background(c.panel3)
                        }
                    )
            ) {
                if (s.state == "skipped")
                    Canvas(Modifier.fillMaxSize()) {
                        var x = -size.height
                        while (x < size.width) {
                            drawLine(
                                c.muted2,
                                Offset(x, size.height),
                                Offset(x + size.height, 0f),
                                1.dp.toPx(),
                            )
                            x += 6.dp.toPx()
                        }
                    }
                // a finished exercise fills in green; the one being done fills in the accent
                if (s.fill > 0)
                    Box(
                        Modifier.fillMaxHeight()
                            .fillMaxWidth(s.fill / 100f)
                            .background(if (s.state == "current") c.accent else c.good)
                    )
            }
        }
    }
}

/** The sets of an exercise as pips (.gx-pips) and "Set 2 of 3". */
@Composable
internal fun SetPips(pips: List<PipState>, setText: String) {
    val c = LocalPlanColors.current
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            setText,
            color = c.textDim,
            fontSize = 13.sp,
            fontWeight = FontWeight(650),
            lineHeight = 13.sp,
            style = Tabular,
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            pips.forEach { p ->
                val shape = RoundedCornerShape(3.dp)
                Box(
                    Modifier.weight(1f)
                        .height(10.dp)
                        .clip(shape)
                        .then(
                            when (p) {
                                PipState.DONE -> Modifier.background(c.good)
                                PipState.CURRENT ->
                                    Modifier.background(c.accent.mix(0.25f, c.panel3))
                                        .border(2.dp, c.accent, shape)
                                PipState.TODO -> Modifier.background(c.panel3)
                            }
                        )
                )
            }
        }
    }
}

/**
 * The countdown ring (.gx-ring): [fraction] of the circle drawn from the top, with the time inside.
 */
@Composable
internal fun RunnerRing(
    fraction: Float,
    color: Color,
    time: String,
    caption: String,
    final: Boolean = false,
) {
    val c = LocalPlanColors.current
    val ink = if (final) c.gold else c.text
    Box(Modifier.size(236.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx() * size.width / 170.dp.toPx()
            val inset = stroke / 2 + 13.dp.toPx() * size.width / 170.dp.toPx()
            val arc = Size(size.width - 2 * inset, size.height - 2 * inset)
            drawArc(c.panel3, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(
                color,
                -90f,
                360f * fraction.coerceIn(0f, 1f),
                false,
                Offset(inset, inset),
                arc,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                time,
                color = ink,
                fontSize = 52.sp,
                fontWeight = FontWeight(800),
                letterSpacing = (-1.56).sp,
                lineHeight = 52.sp,
                style = Tabular,
            )
            Text(caption, color = c.muted, fontSize = 13.sp, lineHeight = 15.6.sp)
        }
    }
}

/**
 * A weight or amount box with − and + (.gx-stepper): the page steps by [step], never below [min].
 */
@Composable
internal fun NumberStepper(
    label: String,
    value: String,
    placeholder: String,
    step: Double,
    min: Double,
    onChange: (String) -> Unit,
) {
    val c = LocalPlanColors.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            Modifier.padding(bottom = 8.dp),
            color = c.textDim,
            fontSize = 14.sp,
            fontWeight = FontWeight(650),
            lineHeight = 18.2.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NudgeButton("−", "Decrease $label") { onChange(nudged(value, -step, min)) }
            val shape = RoundedCornerShape(16.dp)
            BasicTextField(
                value,
                { onChange(keepDecimal(it)) },
                Modifier.weight(1f)
                    .height(72.dp)
                    .clip(shape)
                    .background(c.bgElev)
                    .border(2.dp, c.line, shape)
                    .semantics { contentDescription = label },
                textStyle =
                    TextStyle(
                        color = c.text,
                        fontSize = 40.sp,
                        fontWeight = FontWeight(800),
                        textAlign = TextAlign.Center,
                        fontFeatureSettings = "tnum",
                    ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(c.accentStrong),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxSize().padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (value.isEmpty())
                            Text(
                                placeholder,
                                color = c.muted2,
                                fontSize = 18.sp,
                                fontWeight = FontWeight(600),
                            )
                        inner()
                    }
                },
            )
            NudgeButton("+", "Increase $label") { onChange(nudged(value, step, min)) }
        }
    }
}

@Composable
private fun NudgeButton(text: String, description: String, onClick: () -> Unit) {
    val c = LocalPlanColors.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.width(64.dp)
            .height(72.dp)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = c.text,
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 30.sp,
        )
    }
}

/** What is typed in a number box: digits and one point. */
internal fun keepDecimal(typed: String): String {
    val kept = typed.filter { it.isDigit() || it == '.' }
    val point = kept.indexOf('.')
    return if (point < 0) kept
    else kept.substring(0, point + 1) + kept.substring(point + 1).replace(".", "")
}

/** The page's gwrNudged: one step from [value], never below [min], to a tenth. */
internal fun nudged(value: String, delta: Double, min: Double): String {
    val v = value.toDoubleOrNull() ?: 0.0
    val next = maxOf(min, Math.round((v + delta) * 10.0) / 10.0)
    return if (next == Math.rint(next)) next.toLong().toString() else next.toString()
}

/** A heading in the screen's big type (.gx-title). */
@Composable
internal fun BigTitle(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(
        text,
        modifier.semantics { heading() },
        color = LocalPlanColors.current.text,
        fontSize = 34.sp,
        fontWeight = FontWeight(800),
        letterSpacing = (-1.02).sp,
        lineHeight = 35.4.sp,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )
}

@Composable
internal fun StatBand(stats: List<Triple<String, String, Color?>>, center: Boolean = false) {
    val c = LocalPlanColors.current
    Row(Modifier.fillMaxWidth().padding(top = 22.dp).drawTopBottom(c.lineSoft)) {
        stats.forEachIndexed { i, (value, caption, tint) ->
            Column(
                Modifier.weight(1f)
                    .padding(vertical = 14.dp)
                    .then(
                        if (i > 0) Modifier.padding(start = if (center) 0.dp else 16.dp)
                        else Modifier
                    ),
                horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                Text(
                    value,
                    color = tint ?: c.text,
                    fontSize = 26.sp,
                    fontWeight = FontWeight(800),
                    letterSpacing = (-0.52).sp,
                    lineHeight = 27.3.sp,
                    style = Tabular,
                )
                Text(
                    caption,
                    Modifier.padding(top = 4.dp),
                    color = c.muted,
                    fontSize = 13.sp,
                    lineHeight = 15.6.sp,
                )
            }
        }
    }
}

private fun Modifier.drawTopBottom(color: Color): Modifier = drawBehind {
    val w = 1.dp.toPx()
    drawRect(color, Offset.Zero, Size(size.width, w))
    drawRect(color, Offset(0f, size.height - w), Size(size.width, w))
}
