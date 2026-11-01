package com.lifemax.rpg.feature.lifedirector.presentation.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.lifedirector.domain.Choice
import com.lifemax.rpg.feature.lifedirector.domain.ChoiceSetting
import com.lifemax.rpg.feature.lifedirector.domain.DirectorSettings
import com.lifemax.rpg.feature.lifedirector.domain.SettingRange
import com.lifemax.rpg.feature.lifedirector.presentation.components.ButtonLook
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorButton
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText
import kotlin.math.roundToInt

/** What Settings can ask for. Every change goes to the page's own function (Do in logic.js). */
class SettingsActions(
    val choose: (key: String, value: String) -> Unit = { _, _ -> },
    val range: (key: String, value: Int) -> Unit = { _, _ -> },
    val restDay: (day: Int, on: Boolean) -> Unit = { _, _ -> },
    val replayGuides: () -> Unit = {},
    val copy: () -> Unit = {},
    val restore: () -> Unit = {},
    val reset: () -> Unit = {},
    val lab: (Boolean) -> Unit = {},
)

/** How much to schedule: the intensity, its note and the three daily limits. */
@Composable
fun IntensityCard(s: DirectorSettings, pending: Boolean, a: SettingsActions) = DirectorCard {
    Column {
        CardTitle(s.intensity.title)
        Segmented(
            s.intensity.options,
            s.intensity.value,
            !pending,
            Modifier.padding(top = 10.dp, bottom = 4.dp),
        ) {
            a.choose("intensityLevel", it)
        }
        Muted(s.intensityNote)
        s.ranges.forEach { RangeRow(it, pending, a) }
    }
}

@Composable
fun RestDaysCard(s: DirectorSettings, pending: Boolean, a: SettingsActions) = DirectorCard {
    Column {
        CardTitle(s.restDaysTitle)
        Muted(s.restDaysNote, Modifier.padding(top = 4.dp))
        Row(
            Modifier.widthIn(max = 330.dp).padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            s.restDays.forEach { d ->
                val shape = RoundedCornerShape(11.dp)
                Box(
                    Modifier.weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(shape)
                        .background(if (d.on) P.task else Color.White.copy(alpha = .04f))
                        .border(1.dp, if (d.on) Color.Transparent else P.line2, shape)
                        .toggleable(d.on, enabled = !pending, role = Role.Checkbox) {
                            a.restDay(d.day, it)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        d.label,
                        style = DirectorText,
                        color = if (d.on) Color(0xFF04150F) else P.ink2,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
fun ChoiceCard(
    c: ChoiceSetting,
    key: String,
    pending: Boolean,
    a: SettingsActions,
    below: @Composable () -> Unit = {},
) = DirectorCard {
    Column {
        CardTitle(c.title)
        Segmented(c.options, c.value, !pending, Modifier.padding(top = 10.dp, bottom = 4.dp)) {
            a.choose(key, it)
        }
        below()
    }
}

/**
 * When I miss something: the choice, what each costs with the rest tokens in bold, and the grace
 * period.
 */
@Composable
fun MissesCard(s: DirectorSettings, pending: Boolean, a: SettingsActions) =
    ChoiceCard(s.misses, "consequenceMode", pending, a) {
        Text(
            buildAnnotatedString {
                s.missesText.forEach {
                    if (it.b)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = P.ink)) {
                            append(it.t)
                        }
                    else append(it.t)
                }
            },
            style = DirectorText,
            color = P.ink3,
            fontSize = 13.8.sp,
            lineHeight = 20.sp,
        )
        RangeRow(s.grace, pending, a)
    }

@Composable
fun GuideCard(s: DirectorSettings, pending: Boolean, a: SettingsActions) = DirectorCard {
    Column {
        CardTitle(s.guide.title)
        Muted(s.guide.text, Modifier.padding(top = 4.dp))
        DirectorButton(
            s.guideButton,
            ButtonLook.GHOST,
            { if (!pending) a.replayGuides() },
            Modifier.padding(top = 8.dp),
            small = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataCard(s: DirectorSettings, pending: Boolean, a: SettingsActions) = DirectorCard {
    Column {
        CardTitle(s.data.title)
        Text(
            buildAnnotatedString {
                append(s.data.text)
                if (s.data.storageUnavailable)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = P.ink)) {
                        append(s.data.storageWarning)
                    }
            },
            Modifier.padding(top = 4.dp),
            style = DirectorText,
            color = P.ink3,
            fontSize = 13.8.sp,
            lineHeight = 20.sp,
        )
        FlowRow(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DirectorButton(s.data.copy, ButtonLook.GHOST, { if (!pending) a.copy() }, small = true)
            DirectorButton(
                s.data.restore,
                ButtonLook.GHOST,
                { if (!pending) a.restore() },
                small = true,
            )
            DirectorButton(
                s.data.reset,
                ButtonLook.GHOST,
                { if (!pending) a.reset() },
                small = true,
            )
        }
    }
}

/**
 * Diagnostics, folded as the page's details: the engine line and the developer's Simulation Lab
 * switch.
 */
@Composable
fun DiagnosticsCard(s: DirectorSettings, pending: Boolean, a: SettingsActions) = DirectorCard {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(
            (if (open) "▾ " else "▸ ") + s.diagnosticsTitle,
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { open = !open }
                .padding(vertical = 12.dp),
            style = DirectorText,
            color = P.ink2,
            fontSize = 14.sp,
            fontWeight = FontWeight(650),
        )
        if (open) {
            Text(
                s.diagnostics,
                style = NumText,
                color = P.ink3,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp).toggleable(
                    s.labOn,
                    enabled = !pending,
                    role = Role.Switch,
                ) {
                    a.lab(it)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    s.labLabel,
                    Modifier.weight(1f),
                    style = DirectorText,
                    color = P.ink2,
                    fontSize = 14.sp,
                    fontWeight = FontWeight(550),
                )
                Switch(s.labOn, null, colors = SwitchDefaults.colors(checkedTrackColor = P.task))
            }
        }
    }
}

/**
 * .ld-range: the label and the value's words, then the slider. The words follow the finger from the
 * page's labels for each stop; the change goes to the page when the finger lets go (one re-plan,
 * not one per stop).
 */
@Composable
private fun RangeRow(r: SettingRange, pending: Boolean, a: SettingsActions) {
    var held by remember(r.key, r.value) { mutableFloatStateOf(r.value.toFloat()) }
    val stop = (((held - r.min) / r.step).roundToInt() * r.step + r.min).coerceIn(r.min, r.max)
    Column(Modifier.padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                r.label,
                Modifier.weight(1f),
                style = DirectorText,
                color = P.ink2,
                fontSize = 14.sp,
                fontWeight = FontWeight(550),
            )
            Text(
                r.labelAt(stop),
                style = DirectorText,
                color = P.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = held,
            onValueChange = { held = it },
            onValueChangeFinished = { if (stop != r.value) a.range(r.key, stop) },
            enabled = !pending,
            valueRange = r.min.toFloat()..r.max.toFloat(),
            steps = (r.stopCount - 2).coerceAtLeast(0),
            modifier = Modifier.semantics { stateDescription = r.labelAt(stop) },
            colors =
                SliderDefaults.colors(
                    thumbColor = P.task,
                    activeTrackColor = P.task,
                    inactiveTrackColor = Color.White.copy(alpha = .1f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
        )
        if (r.hint.isNotEmpty())
            Text(r.hint, style = DirectorText, color = P.ink3, fontSize = 12.5.sp)
    }
}

/** .ld-seg: choices side by side, the chosen one lit. */
@Composable
internal fun Segmented(
    options: List<Choice>,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit,
) {
    val outer = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(outer)
            .background(Color(0xFF0B1019))
            .border(1.dp, P.line, outer)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { o ->
            val on = o.value == value
            val shape = RoundedCornerShape(9.dp)
            Box(
                Modifier.weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(shape)
                    .then(
                        if (on)
                            Modifier.background(P.free.copy(alpha = .18f))
                                .border(1.dp, P.free.copy(alpha = .4f), shape)
                        else Modifier
                    )
                    .selectable(on, enabled = enabled, role = Role.RadioButton) {
                        if (!on) onPick(o.value)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    o.label,
                    style = DirectorText,
                    color = if (on) P.ink else P.ink3,
                    fontSize = 13.4.sp,
                    fontWeight = FontWeight(650),
                    maxLines = 1,
                )
            }
        }
    }
}
