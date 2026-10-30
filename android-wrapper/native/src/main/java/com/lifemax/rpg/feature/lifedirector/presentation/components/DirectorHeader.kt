package com.lifemax.rpg.feature.lifedirector.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.feature.lifedirector.domain.DirectorBanner
import com.lifemax.rpg.feature.lifedirector.domain.DirectorClock
import com.lifemax.rpg.feature.lifedirector.domain.DirectorHeader
import com.lifemax.rpg.feature.lifedirector.domain.DirectorTab
import com.lifemax.rpg.feature.lifedirector.domain.StatusDot
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P

/** Title, how the plan stands, the guide button and the clock (the web's headerHtml). */
@Composable
fun DirectorTopLine(header: DirectorHeader, clock: DirectorClock, onTap: (Tap) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusLight(header.dot)
            Column {
                Text(
                    "Life Director",
                    style = DirectorText,
                    fontSize = 20.5.sp,
                    fontWeight = FontWeight(750),
                    letterSpacing = (-.03).em,
                    lineHeight = 1.1.em,
                )
                Text(
                    header.line,
                    Modifier.padding(top = 2.dp),
                    style = DirectorText,
                    color = P.ink3,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            Modifier.size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { onTap(Tap("tour")) }
                .semantics { contentDescription = "Show the guide for this screen" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(36.dp)
                    .background(P.glass, CircleShape)
                    .border(1.dp, P.glassEdge, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "?",
                    style = DirectorText,
                    color = P.sky,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        val box = RoundedCornerShape(13.dp)
        Column(
            Modifier.background(P.glass, box)
                .border(1.dp, P.glassEdge.copy(alpha = .2f), box)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                clock.time,
                style = DirectorText,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight(650),
                letterSpacing = (-.01).em,
                lineHeight = 1.1.em,
            )
            Text(clock.day, style = DirectorText, color = P.ink3, fontSize = 11.2.sp)
        }
    }
}

/** The dot before the title: a lit green when the plan holds, amber when it needs a look. */
@Composable
private fun StatusLight(dot: StatusDot) {
    when (dot) {
        StatusDot.OK ->
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(16.dp).background(P.statusOk.copy(alpha = .1f), CircleShape))
                GlowDot(8.dp, P.statusOk, 12.dp)
            }
        StatusDot.WARN ->
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(16.dp).background(P.work.copy(alpha = .12f), CircleShape))
                GlowDot(8.dp, P.work, 0.dp)
            }
        StatusDot.OFF -> GlowDot(8.dp, P.ink3, 0.dp)
    }
}

/**
 * Life Director's sections; every one but the current one opens on the page. 38 dp pills in a 48 dp
 * tall row that scrolls to the panel's edges, on a band of the panel's own black.
 */
@Composable
fun SectionTabs(tabs: List<DirectorTab>, current: String, onTap: (Tap) -> Unit) {
    Row(
        Modifier.bleed(14.dp)
            .background(tabsBand)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { tab ->
            val on = tab.id == current
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier.heightIn(min = 48.dp)
                    .semantics {
                        selected = on
                        role = Role.Tab
                    }
                    .then(if (on) Modifier else Modifier.clickable { onTap(Tap("tab", tab.id)) }),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.heightIn(min = 38.dp)
                        .then(
                            if (on)
                                Modifier.background(P.free.copy(alpha = .13f), shape)
                                    .border(1.dp, P.free.copy(alpha = .38f), shape)
                            else Modifier
                        )
                        .padding(horizontal = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelIcon(tab.icon, if (on) P.sky else P.ink3, Modifier.size(14.dp))
                    Text(
                        tab.label,
                        style = DirectorText,
                        color = if (on) P.ink else P.ink3,
                        fontSize = 13.8.sp,
                        fontWeight = FontWeight(650),
                    )
                }
            }
        }
    }
}

/** linear-gradient(180deg, var(--bg0) 70%, transparent) */
private val tabsBand = Brush.verticalGradient(0f to P.bg0, .7f to P.bg0, 1f to Color.Transparent)

/** A warning over Today, with its buttons and, unless it asks for a choice, a dismiss. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Banner(banner: DirectorBanner, onTap: (Tap) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val tint = if (banner.bad) P.block else P.work
    Box(
        Modifier.fillMaxWidth()
            .background(tint.copy(alpha = if (banner.bad) .1f else .09f), shape)
            .border(1.dp, tint.copy(alpha = if (banner.bad) .35f else .32f), shape)
    ) {
        Column(Modifier.padding(start = 14.dp, top = 12.dp, end = 44.dp, bottom = 12.dp)) {
            Text(banner.text, style = DirectorText, fontSize = 14.4.sp)
            if (banner.actions.isNotEmpty())
                FlowRow(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    banner.actions.forEach {
                        DirectorButton(
                            it.label,
                            if (it.ghost) ButtonLook.GHOST else ButtonLook.PLAIN,
                            { onTap(it.tap) },
                            small = it.small,
                        )
                    }
                }
        }
        if (banner.dismiss)
            Box(
                Modifier.align(Alignment.TopEnd)
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { onTap(Tap("dismissBanner")) }
                    .semantics { contentDescription = "Dismiss" },
                contentAlignment = Alignment.Center,
            ) {
                Text("×", style = DirectorText, color = P.ink3, fontSize = 20.sp)
            }
    }
}

/** The message Life Director just gave, over the bottom of the screen (the web's .ld-toast). */
@Composable
fun DirectorToastView(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(13.dp)
    Text(
        text,
        modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .boxShadow(P.toastShadow, 34.dp, shape, dy = 12.dp)
            .background(P.toast, shape)
            .border(1.dp, P.line2, shape)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        style = DirectorText,
        fontSize = 14.1.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 1.35.em,
    )
}
