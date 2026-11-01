package com.lifemax.rpg.feature.lifedirector.presentation.dial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.lifemax.rpg.core.designsystem.cssBlurRadius
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText

/**
 * The centre while a handle is held or let go (attachDial's core): its label, the time it is over
 * (in the monospace face, as .ld-core-big's var(--ld-num)), the length the rule would have (or that
 * it is too short), and how to apply. Sizes are the page's .ld-core-k, .ld-core-big and .ld-core-s,
 * fixed rem whatever the dial's size (in dp, so they follow the font scale no more than the rest of
 * the dial does), the time smaller on a phone 380 dp wide or less as the page's (max-width:380px)
 * rule makes it.
 */
@Composable
internal fun DragCentre(preview: DragPreview, modifier: Modifier = Modifier) {
    val u = pagePx(LocalDensity.current)
    val narrow = LocalConfiguration.current.screenWidthDp <= NARROW_DP
    Column(
        modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(u.dp(1f)),
    ) {
        Text(
            preview.kick,
            style = DirectorText,
            color = P.ink3,
            fontSize = u.sp(11.2f),
            fontWeight = FontWeight.Bold,
            letterSpacing = .01.em,
            maxLines = 1,
        )
        Text(
            preview.time,
            Modifier.padding(top = u.dp(2f)),
            style = DirectorText.copy(fontFamily = FontFamily.Monospace),
            fontSize = u.sp(if (narrow) 18.4f else 21.6f),
            fontWeight = FontWeight.Bold,
            letterSpacing = (-.03).em,
            lineHeight = 1.1.em,
            maxLines = 1,
        )
        for (line in listOf(preview.length, preview.release)) Text(
            line,
            style = DirectorText,
            color = P.ink2,
            fontSize = u.sp(11.84f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * The build's read-out over the centre (.ld-bhud): the phase in small capitals, a big line (the
 * hour under the arm, the sessions landed, their count at the lock) and what it means. It fades in,
 * holds, and grows away before the end; each touchdown ticks the count. Only its words recompose,
 * when they change; the rest follows the clock as it is drawn. Sizes are the page's fixed rem, as
 * in [DragCentre]; the big line is smaller on a phone 400 dp wide or less, as the page's
 * (max-width:400px) rule makes .ld-bh-n.
 */
@Composable
internal fun BuildHud(run: BuildRun, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val u = pagePx(density)
    val big = if (LocalConfiguration.current.screenWidthDp <= HUD_NARROW_DP) 30.4f else 36.8f
    val words by remember(run) { derivedStateOf { BuildTimeline.words(run.build, run.ms) } }
    Column(
        modifier
            .graphicsLayer {
                val (alpha, scale) = BuildTimeline.hud(run.build, run.ms)
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(u.dp(2f)),
    ) {
        Text(
            words.kick.uppercase(),
            style = DirectorText,
            color = if (words.lock) P.good else P.hudKick,
            fontSize = u.sp(10.88f),
            fontWeight = FontWeight.Bold,
            letterSpacing = .08.em,
            maxLines = 1,
        )
        val glow =
            if (!words.lock) null
            else Shadow(P.lockGlow, Offset.Zero, cssBlurRadius(with(density) { u.dp(18f).toPx() }))
        BasicText(
            words.big,
            Modifier.graphicsLayer {
                val (scale, _) = BuildTimeline.counterTick(run.build, run.ms)
                scaleX = scale
                scaleY = scale
            },
            style =
                DirectorText.copy(
                    fontSize = u.sp(big),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-.04).em,
                    lineHeight = 1.05.em,
                    shadow = glow,
                ),
            softWrap = false,
            maxLines = 1,
            color = { lerp(P.ink, P.trail, BuildTimeline.counterTick(run.build, run.ms).second) },
        )
        Text(
            words.sub,
            style = DirectorText,
            color = P.ink2,
            fontSize = u.sp(12.16f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The page's CSS px as dp (a rem is 16), for words the page sizes in rem over the dial: they keep
 * their size when the dial is drawn smaller or larger, as on the page, and do not grow with the
 * font scale, as nothing on the dial does.
 */
private fun pagePx(density: Density) = DialUnits(1.dp, density)

/**
 * The page's narrow phones: (max-width:380px) for the held time, (max-width:400px) for .ld-bh-n.
 */
private const val NARROW_DP = 380
private const val HUD_NARROW_DP = 400
