package com.lifemax.rpg.feature.lifedirector.presentation.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.lifedirector.domain.BlockKind
import com.lifemax.rpg.feature.lifedirector.domain.DirectorWeek
import com.lifemax.rpg.feature.lifedirector.domain.SessionState
import com.lifemax.rpg.feature.lifedirector.domain.WeekDay
import com.lifemax.rpg.feature.lifedirector.domain.WeekFit
import com.lifemax.rpg.feature.lifedirector.domain.WeekSeg
import com.lifemax.rpg.feature.lifedirector.domain.XpYield
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText

/** The page's numbers font (--ld-num), as the rest of the screen draws it. */
internal val NumText =
    DirectorText.copy(fontFamily = FontFamily.Monospace, letterSpacing = (-.03).em)

/**
 * The page's Week (renderWeek): three numbers, a bar per day on one 24-hour scale, the legend,
 * where the XP goes and how the week fits.
 */
@Composable
fun WeekStats(week: DirectorWeek) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        week.stats.forEach { s ->
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = .03f))
                    .border(1.dp, P.line, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Text(
                    s.value,
                    style = NumText,
                    color = P.ink,
                    fontSize = 18.4.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(s.label, style = DirectorText, color = P.ink3, fontSize = 11.8.sp)
            }
        }
    }
}

private val DAY_COL = 50.dp
private val COUNT_COL = 56.dp

/**
 * 00 06 12 18 24 over the bars: the first at the start, the last at the end, the rest centred on
 * their hour.
 */
@Composable
fun WeekTicks(ticks: List<String>) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(DAY_COL))
        BoxWithConstraints(Modifier.weight(1f).height(14.dp)) {
            val w = maxWidth
            ticks.forEachIndexed { k, t ->
                val at = w * (k.toFloat() / (ticks.size - 1).coerceAtLeast(1))
                val shift: Dp =
                    when (k) {
                        0 -> 0.dp
                        ticks.lastIndex -> (-15).dp
                        else -> (-7).dp
                    }
                Text(
                    t,
                    Modifier.offset(x = at + shift),
                    style = NumText,
                    color = P.ink3,
                    fontSize = 10.2.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Box(Modifier.width(COUNT_COL))
    }
}

/** One day's row; a tap opens that day on Today (the page's gotoDay). */
@Composable
fun WeekRow(day: WeekDay, pending: Boolean, onTap: (Int) -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(
                if (day.today) P.free.copy(alpha = .07f) else Color.White.copy(alpha = .025f)
            )
            .border(1.dp, if (day.today) P.free.copy(alpha = .4f) else Color.Transparent, shape)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = day.aria
            }
            .clickable(enabled = !pending) { onTap(day.index) }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(DAY_COL).clearAndSetSemantics {}) {
            Text(
                day.label,
                style = DirectorText,
                color = P.ink,
                fontSize = 13.8.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(day.date.toString(), style = DirectorText, color = P.ink3, fontSize = 11.5.sp)
        }
        DayBar(day.segs, Modifier.weight(1f))
        Column(
            Modifier.width(COUNT_COL).clearAndSetSemantics {},
            horizontalAlignment = Alignment.End,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    day.count,
                    style = NumText,
                    color = P.ink,
                    fontSize = 14.7.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (day.done.isNotEmpty())
                    Text(
                        day.done,
                        Modifier.padding(start = 4.dp),
                        style = DirectorText,
                        color = P.good,
                        fontSize = 11.5.sp,
                    )
            }
            Text(day.minutes, style = DirectorText, color = P.ink3, fontSize = 11.5.sp)
        }
    }
}

/**
 * .ld-wbar: midnight to midnight; sessions stand a dp proud, downtime is a thin line, travel is
 * faded.
 */
@Composable
private fun DayBar(segs: List<WeekSeg>, modifier: Modifier) {
    BoxWithConstraints(
        modifier.height(16.dp).clip(RoundedCornerShape(5.dp)).background(P.free.copy(alpha = .08f))
    ) {
        val w = maxWidth
        for (g in segs) {
            val color =
                when {
                    g.kind == BlockKind.TASK && g.state == SessionState.DONE -> Color(0xFF167A5B)
                    g.kind == BlockKind.TASK && g.state == SessionState.OFF ->
                        P.block.copy(alpha = .5f)
                    g.kind == BlockKind.COMMUTE -> P.commute.copy(alpha = .6f)
                    g.kind == BlockKind.REST -> P.rest.copy(alpha = .7f)
                    else -> P.of(g.kind)
                }
            val inset = if (g.kind == BlockKind.REST) 6.dp else 0.dp
            Box(
                Modifier.offset(x = w * (g.left / 100f))
                    .width(w * (g.width / 100f))
                    .fillMaxHeight()
                    .padding(vertical = inset)
                    .background(color)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekLegend(week: DirectorWeek) {
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 2.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        week.legend.forEach { e ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(P.of(e.kind)))
                Text(e.label, style = DirectorText, color = P.ink2, fontSize = 12.5.sp)
            }
        }
    }
}

/** A card in the page's .ld-card look. */
@Composable
fun DirectorCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.fillMaxWidth()
            .padding(vertical = 12.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = .025f))
            .border(1.dp, P.line, shape)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun CardTitle(text: String) =
    Text(text, style = DirectorText, color = P.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(
        text,
        modifier,
        style = DirectorText,
        color = P.ink3,
        fontSize = 13.8.sp,
        lineHeight = 20.sp,
    )

/**
 * Where your XP goes: solid is earned, outlined is still planned, each attribute in its own colour.
 */
@Composable
fun XpYieldCard(y: XpYield) = DirectorCard {
    Column {
        CardTitle(y.title)
        Muted(y.note, Modifier.padding(top = 4.dp))
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            y.rows.forEach { r ->
                val c = Color(r.color.toInt())
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        r.label,
                        Modifier.width(86.dp),
                        style = DirectorText,
                        color = P.ink2,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                    Row(
                        Modifier.weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color.White.copy(alpha = .04f))
                    ) {
                        if (r.done > 0f) Box(Modifier.weight(r.done).fillMaxHeight().background(c))
                        if (r.plan > 0f)
                            Box(
                                Modifier.weight(r.plan)
                                    .fillMaxHeight()
                                    .background(c.copy(alpha = .22f))
                                    .border(
                                        1.dp,
                                        c.copy(alpha = .7f),
                                        RoundedCornerShape(topEnd = 99.dp, bottomEnd = 99.dp),
                                    )
                            )
                        val rest = 1f - r.done - r.plan
                        if (rest > 0.0001f) Box(Modifier.weight(rest))
                    }
                    Text(
                        r.total,
                        Modifier.width(42.dp),
                        style = NumText,
                        color = P.ink,
                        fontSize = 12.8.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
        }
    }
}

/** How the week fits, and what could not fit (folded, as the page's details element). */
@Composable
fun WeekFitCard(fit: WeekFit) = DirectorCard {
    Column {
        CardTitle(fit.title)
        Text(
            fit.text,
            Modifier.padding(top = 6.dp),
            style = DirectorText,
            color = P.ink2,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        val u = fit.unscheduled
        if (u == null) Muted(fit.allFit, Modifier.padding(top = 6.dp))
        else {
            var open by remember { mutableStateOf(false) }
            Text(
                (if (open) "▾ " else "▸ ") + u.summary,
                Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { open = !open }
                    .padding(vertical = 12.dp),
                style = DirectorText,
                color = P.ink2,
                fontSize = 14.sp,
                fontWeight = FontWeight(650),
            )
            if (open)
                Column(
                    Modifier.padding(start = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    u.groups.forEach { g ->
                        Column {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                Text(
                                    g.title,
                                    style = DirectorText,
                                    color = P.ink2,
                                    fontSize = 13.8.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    g.tier,
                                    style = DirectorText,
                                    color = P.ink3,
                                    fontSize = 11.5.sp,
                                )
                            }
                            Text(g.text, style = DirectorText, color = P.ink3, fontSize = 13.8.sp)
                        }
                    }
                }
        }
    }
}
