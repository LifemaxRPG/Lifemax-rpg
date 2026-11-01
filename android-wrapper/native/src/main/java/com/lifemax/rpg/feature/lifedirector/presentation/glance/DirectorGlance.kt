package com.lifemax.rpg.feature.lifedirector.presentation.glance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.lifedirector.domain.ArcStyle
import com.lifemax.rpg.feature.lifedirector.domain.DirectorGlance
import com.lifemax.rpg.feature.lifedirector.domain.GlanceFoot
import com.lifemax.rpg.feature.lifedirector.domain.GlanceState
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.components.ButtonLook
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorButton
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText
import com.lifemax.rpg.feature.lifedirector.presentation.dial.ChronoDial
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialIntroLedger
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialIntroRun
import com.lifemax.rpg.feature.lifedirector.presentation.dial.rememberDialIntro

/**
 * Life Director's glance on the native Overview (the page's LifeDirector.glance, `#ovPlan`): the
 * head and its link, then today's dial at glance size, read-only (no handles, no build) with its
 * middle and the foot, or the empty state's words and button. It stays Life Director's dark panel
 * in both LifeMax themes, as `.ld-root.ld-glance` does.
 *
 * Every tap is [onTap] with the page's act and id, which the Overview sends as its `plan {act, id}`
 * command (LifeDirector.native.fromGlance): Life Director opens on Today and on the tapped
 * session's, meal's or running timer's sheet; the head, the button and anywhere else only open it.
 *
 * The running timer's clock ticks once a second while [visible] and the timer is not paused, each
 * tick derived from the page's startedAt and pausedMs (Today's TimerClock), never counted; hidden,
 * nothing on the glance moves. [arrival] is the Overview's arrival counter: each new value plays
 * the dial's arrival (DialIntro) once, if the glance shows and [reducedMotion] is off. [intro]
 * stops the arrival at a given moment (screenshots).
 */
@Composable
fun DirectorGlance(
    glance: DirectorGlance,
    visible: Boolean,
    onTap: (Tap) -> Unit,
    modifier: Modifier = Modifier,
    arrival: Long = 0,
    reducedMotion: Boolean = false,
    introLedger: DialIntroLedger = remember { DialIntroLedger() },
    intro: DialIntroRun? =
        rememberDialIntro(
            arrival,
            visible && !reducedMotion && glance.dial != null,
            glance.dial?.arcs?.count { it.style == ArcStyle.SESSION } ?: 0,
            introLedger,
        ),
) {
    val tap by rememberUpdatedState(onTap)
    Column(
        modifier
            .fillMaxWidth()
            .drawWithCache {
                val shape =
                    Path().apply {
                        addRoundRect(RoundRect(size.toRect(), CornerRadius(20.dp.toPx())))
                    }
                val edge = Stroke(1.dp.toPx())
                onDrawBehind {
                    clipPath(shape) {
                        drawRect(P.bg0)
                        drawRect(BlueWash)
                        drawRect(VioletWash)
                    }
                    drawPath(shape, P.panelEdge, style = edge)
                }
            }
            // a tap anywhere else on the card opens Life Director, as the page's glance does
            .pointerInput(Unit) { detectTapGestures { tap(GlanceTaps.open) } }
            .padding(start = 12.dp, top = 14.dp, end = 12.dp, bottom = 14.dp)
    ) {
        Head(glance, onTap)
        when (glance.state) {
            GlanceState.SETUP -> Empty(glance.intro.orEmpty(), glance.button, onTap)
            GlanceState.NONE -> Empty(glance.intro.orEmpty(), null, onTap)
            GlanceState.DAY -> {
                val dial = requireNotNull(glance.dial)
                val centre = requireNotNull(glance.centre)
                val full = glance.fullTitle?.takeIf { it != centre.title }
                ChronoDial(
                    dial = dial,
                    centre = centre,
                    animate = visible,
                    onArc = { onTap(GlanceTaps.arc(it)) },
                    onTap = { onTap(GlanceTaps.centre(it)) },
                    modifier =
                        Modifier.align(Alignment.CenterHorizontally)
                            .padding(top = 4.dp)
                            .widthIn(max = 350.dp)
                            .then(
                                if (full == null) Modifier
                                else Modifier.semantics { contentDescription = full }
                            ),
                    intro = intro,
                )
                glance.foot?.let { Foot(it) }
            }
        }
    }
}

/** The page's `.ov-h`: the title, and the link to Life Director on the right. */
@Composable
private fun Head(glance: DirectorGlance, onTap: (Tap) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(bottom = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            glance.title,
            Modifier.weight(1f).alignByBaseline().semantics { heading() },
            style = DirectorText,
            fontSize = 16.sp,
            fontWeight = FontWeight(750),
            letterSpacing = (-.02).em,
        )
        Text(
            glance.link,
            Modifier.alignByBaseline().clickable(role = Role.Button) { onTap(GlanceTaps.open) },
            style = DirectorText,
            color = Accent,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The page's `.ld-gl-empty` (before set-up) or its muted line (no day to show). */
@Composable
private fun Empty(intro: String, button: String?, onTap: (Tap) -> Unit) {
    Column(Modifier.padding(start = 4.dp, top = 6.dp, end = 4.dp, bottom = 2.dp)) {
        Text(
            intro,
            Modifier.padding(top = 4.dp, bottom = if (button != null) 12.dp else 0.dp),
            style = DirectorText,
            color = if (button != null) P.ink2 else P.ink3,
            fontSize = 14.08.sp,
            lineHeight = 1.5.em,
        )
        button?.let { DirectorButton(it, ButtonLook.PRIMARY, { onTap(GlanceTaps.open) }) }
    }
}

/** The page's `.ld-gl-foot`: sessions left and their time, then how many are done, in green. */
@Composable
private fun Foot(foot: GlanceFoot) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        Text(
            foot.text,
            style = DirectorText,
            color = P.ink2,
            fontSize = 12.8.sp,
            fontWeight = FontWeight(600),
        )
        if (foot.done.isNotEmpty())
            Text(
                foot.done,
                style = DirectorText,
                color = P.good,
                fontSize = 12.8.sp,
                fontWeight = FontWeight(600),
            )
    }
}

/** Life Director's own accent (`.ld-root`'s --accent), which its glance's link is written in. */
private val Accent = Color(0xFF5B9BFF)

/** `.ld-root`'s washes: radial-gradient(90% 40% at 85% -6%, …) and (70% 30% at 0% 18%, …). */
private val BlueWash = cssEllipse(.85f, -.06f, .9f, .4f, Color(0xFF3B82F6).copy(alpha = .11f))
private val VioletWash = cssEllipse(0f, .18f, .7f, .3f, Color(0xFF8B5CF6).copy(alpha = .06f))

/**
 * A CSS radial-gradient ellipse from [color] to transparent at 60%: its centre and radii in
 * fractions of the box's width (x) and height (y), as CSS percentages are.
 */
private fun cssEllipse(cx: Float, cy: Float, rx: Float, ry: Float, color: Color): Brush =
    object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val centre = Offset(cx * size.width, cy * size.height)
            val radiusX = rx * size.width
            return RadialGradientShader(
                    centre,
                    radiusX,
                    listOf(color, color.copy(alpha = 0f)),
                    listOf(0f, .6f),
                )
                .also {
                    it.setLocalMatrix(
                        android.graphics.Matrix().apply {
                            setScale(1f, ry * size.height / radiusX, centre.x, centre.y)
                        }
                    )
                }
        }
    }

private fun Size.toRect() = androidx.compose.ui.geometry.Rect(Offset.Zero, this)
