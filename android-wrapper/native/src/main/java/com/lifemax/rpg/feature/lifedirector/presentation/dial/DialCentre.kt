package com.lifemax.rpg.feature.lifedirector.presentation.dial

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.lifemax.rpg.feature.lifedirector.domain.CentreNext
import com.lifemax.rpg.feature.lifedirector.domain.Countdown
import com.lifemax.rpg.feature.lifedirector.domain.DialCentre
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText
import com.lifemax.rpg.feature.lifedirector.presentation.components.GlowDot
import com.lifemax.rpg.feature.lifedirector.presentation.components.clockText
import com.lifemax.rpg.feature.lifedirector.presentation.components.rememberPulse
import com.lifemax.rpg.feature.lifedirector.presentation.components.rememberTickingClock

/**
 * The middle of the dial (the web's coreToday and coreHtml): what is happening now, the time left,
 * and what comes next; for another day, its date and first session. Sizes are in dial units ([unit]
 * each) as the web sizes them for a 360-unit dial, so the centre scales with the dial and not with
 * the font size setting, which the rings around it could not make room for.
 */
@Composable
internal fun DialCentreView(
    centre: DialCentre,
    unit: Dp,
    animate: Boolean,
    onTap: (Tap) -> Unit,
    modifier: Modifier = Modifier,
) {
    val u = DialUnits(unit, LocalDensity.current)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (centre.date) DateCentre(centre, u, onTap) else NowCentre(centre, u, animate, onTap)
    }
}

/** Lengths and type sizes in dial units. */
private class DialUnits(private val unit: Dp, private val density: Density) {
    fun dp(v: Float): Dp = unit * v

    fun sp(v: Float): TextUnit = with(density) { (unit * v).toSp() }
}

@Composable
private fun NowCentre(centre: DialCentre, u: DialUnits, animate: Boolean, onTap: (Tap) -> Unit) {
    val warm = if (centre.warn) P.warn else null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(u.dp(5f)),
    ) {
        val kickColor =
            warm ?: if (centre.kickLive) P.good else centre.kickKind?.let(P::kick) ?: P.ink3
        when {
            centre.kickLive ->
                GlowDot(u.dp(6f), kickColor, 0.dp, rememberPulse(animate, 1400, .45f))
            centre.kickKind != null -> GlowDot(u.dp(5f), kickColor, u.dp(8f))
        }
        Text(
            centre.kick.uppercase(),
            style = DirectorText,
            color = kickColor,
            fontSize = u.sp(8.16f),
            fontWeight = FontWeight(750),
            letterSpacing = .02.em,
            lineHeight = 1.15.em,
            maxLines = 1,
        )
    }
    Text(
        centre.title,
        Modifier.fillMaxWidth().padding(top = u.dp(7f)),
        style = DirectorText,
        fontSize = u.sp(11.04f),
        fontWeight = FontWeight(650),
        letterSpacing = (-.015).em,
        lineHeight = 1.2.em,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    centre.countdown?.let { Countdown(it, u.sp(21f), warm, Modifier.padding(top = u.dp(6f))) }
    centre.timer?.let { timer ->
        val now by rememberTickingClock(animate && timer.paused == null)
        Text(
            clockText(timer.elapsedMs(now)),
            Modifier.padding(top = u.dp(6f)).tappable(timer.tap, onTap),
            style = DirectorText,
            color = Color.White,
            fontSize = u.sp(21f),
            fontWeight = FontWeight.Bold,
            letterSpacing = (-.04).em,
            lineHeight = 1.08.em,
        )
    }
    if (centre.sub.isNotEmpty())
        Text(
            centre.sub,
            Modifier.padding(top = u.dp(6f)),
            style = DirectorText,
            color = P.ink3,
            fontSize = u.sp(8.96f),
            fontWeight = FontWeight(550),
            letterSpacing = .01.em,
            lineHeight = 1.2.em,
            textAlign = TextAlign.Center,
        )
    centre.next?.let { NextLine(it, NowNext, u, animate, onTap) }
    centre.button?.let { button ->
        val shape = RoundedCornerShape(50)
        Text(
            button.label,
            Modifier.padding(top = u.dp(8f))
                .then(
                    if (button.go)
                        Modifier.background(
                            Brush.verticalGradient(listOf(P.goTop, P.goBottom)),
                            shape,
                        )
                    else
                        Modifier.background(Color.White.copy(alpha = .05f), shape)
                            .border(u.dp(1f), P.line2, shape)
                )
                .clickable(role = Role.Button) { onTap(button.tap) }
                .padding(horizontal = u.dp(13f), vertical = u.dp(4f)),
            style = DirectorText,
            color = if (button.go) P.goInk else P.ink2,
            fontSize = u.sp(10f),
            fontWeight = FontWeight(if (button.go) 750 else 700),
            letterSpacing = .02.em,
            lineHeight = 1.1.em,
        )
    }
}

/** Another day: its weekday, its date large, its sessions, and the first of them (coreHtml). */
@Composable
private fun DateCentre(centre: DialCentre, u: DialUnits, onTap: (Tap) -> Unit) {
    Text(
        centre.kick,
        style = DirectorText,
        color = P.ink3,
        fontSize = u.sp(11.2f),
        fontWeight = FontWeight.Bold,
        letterSpacing = .01.em,
        maxLines = 1,
    )
    Text(
        centre.title,
        Modifier.padding(top = u.dp(1f)),
        style = DirectorText,
        fontSize = u.sp(28f),
        fontWeight = FontWeight.Bold,
        letterSpacing = (-.04).em,
        lineHeight = 1.18.em,
        maxLines = 1,
    )
    Text(
        centre.sub,
        Modifier.padding(top = u.dp(1f)),
        style = DirectorText,
        color = P.ink2,
        fontSize = u.sp(11.84f),
        textAlign = TextAlign.Center,
    )
    centre.next?.let { NextLine(it, DateNext, u, animate = false, onTap) }
}

/** The next line's spacing and type in units: under the time left, or under another day's date. */
private class NextType(
    val above: Float,
    val below: Float,
    val label: Float,
    val tracking: Float,
    val title: Float,
)

private val NowNext =
    NextType(above = 9f, below = 6f, label = 8.24f, tracking = .045f, title = 10.71f)
private val DateNext =
    NextType(above = 8f, below = 7f, label = 8.96f, tracking = .1f, title = 11.84f)

/** What comes next, under a short rule. A session about to start says so in amber, pulsing. */
@Composable
private fun NextLine(
    next: CentreNext,
    type: NextType,
    u: DialUnits,
    animate: Boolean,
    onTap: (Tap) -> Unit,
) {
    Box(
        Modifier.padding(top = u.dp(type.above))
            .size(u.dp(26f), u.dp(1f))
            .background(Color.White.copy(alpha = .14f))
    )
    Column(
        Modifier.fillMaxWidth(.94f).padding(top = u.dp(type.below)).tappable(next.tap, onTap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(u.dp(5f)),
        ) {
            if (next.live) GlowDot(u.dp(6f), P.warn, 0.dp, rememberPulse(animate, 1400, .45f))
            Text(
                next.label.uppercase(),
                style = DirectorText,
                color = if (next.live) P.warn else P.ink3,
                fontSize = u.sp(type.label),
                fontWeight = FontWeight(750),
                letterSpacing = type.tracking.em,
                lineHeight = 1.2.em,
                maxLines = 1,
            )
        }
        if (next.title.isNotEmpty()) {
            val fade = rememberPulse(animate && next.live, 2200, .6f)
            Text(
                next.title,
                Modifier.padding(top = u.dp(2f)).graphicsLayer { alpha = fade.value },
                style = DirectorText,
                color = if (next.live) P.warnBright else P.ink2,
                fontSize = u.sp(type.title),
                fontWeight = FontWeight.SemiBold,
                lineHeight = 1.15.em,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "1h 50m" with small units, or "<1m". */
@Composable
private fun Countdown(c: Countdown, size: TextUnit, warm: Color?, modifier: Modifier) {
    val unitStyle =
        SpanStyle(
            fontSize = size * .5f,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.em,
            color = warm ?: P.ink2,
        )
    val text = buildAnnotatedString {
        fun part(n: String, unit: String) {
            append(n)
            withStyle(unitStyle) { append(unit) }
        }
        when {
            c.less -> part("<1", "m")
            c.hours > 0 -> {
                part("${c.hours}", "h")
                if (c.minutes > 0) {
                    append(" ")
                    part("${c.minutes}", "m")
                }
            }
            else -> part("${c.minutes}", "m")
        }
    }
    Text(
        text,
        modifier,
        style = DirectorText,
        color = if (warm != null) P.warnBright else Color.White,
        fontSize = size,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-.04).em,
        lineHeight = 1.08.em,
    )
}

private fun Modifier.tappable(tap: Tap?, onTap: (Tap) -> Unit): Modifier =
    if (tap == null) this else clickable(role = Role.Button) { onTap(tap) }
