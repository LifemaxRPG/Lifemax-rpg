package com.lifemax.rpg.feature.lifedirector.presentation.sections

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.glowPaint
import com.lifemax.rpg.feature.lifedirector.domain.FirstRun
import com.lifemax.rpg.feature.lifedirector.domain.FirstRunKind
import com.lifemax.rpg.feature.lifedirector.presentation.components.ButtonLook
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorButton
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialFace
import com.lifemax.rpg.feature.lifedirector.presentation.dial.arcPath
import com.lifemax.rpg.feature.lifedirector.presentation.dial.inUnits

/**
 * The first-run card (the page's needSetup, .ld-empty.ld-first): the main dial's face with the
 * page's demo arcs (sleep and work on the outer ring, two sessions on the inner, in the main dial's
 * colours), the title, the text and the button that opens My time ([onOpen]).
 */
@Composable
fun FirstRunCard(f: FirstRun, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = .025f), shape)
            .border(1.dp, P.line, shape)
            .padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DemoDial(f)
        Text(
            f.title,
            Modifier.semantics { heading() },
            style = DirectorText,
            color = P.ink,
            fontSize = 20.sp,
            fontWeight = FontWeight(750),
            lineHeight = 1.2.em,
            letterSpacing = (-.03).em,
            textAlign = TextAlign.Center,
        )
        // .ld-empty p: at most 40 characters wide (about 40 × 0.55 of its 14.7 px)
        Text(
            f.text,
            Modifier.widthIn(max = 330.dp).padding(top = 8.dp, bottom = 16.dp),
            style = DirectorText,
            color = P.ink2,
            fontSize = 14.7.sp,
            textAlign = TextAlign.Center,
        )
        DirectorButton(f.button, ButtonLook.PRIMARY, onOpen, Modifier.fillMaxWidth())
    }
}

/** .ld-first-dial: min(64 %, 230 dp) across, at .95 opacity, nothing for TalkBack (aria-hidden). */
@Composable
private fun DemoDial(f: FirstRun) {
    val text = rememberTextMeasurer()
    val face = remember(f.face) { DialFace(f.face.radii, f.face.hourWords, text) }
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        val side = min(maxWidth * .64f, 230.dp)
        Box(Modifier.size(side).alpha(.95f).clearAndSetSemantics {}) {
            Canvas(Modifier.size(side)) {
                inUnits {
                    face.draw(this)
                    val r = f.face.radii
                    for (a in f.arcs) {
                        val a0 = a.from / 4f
                        val a1 = a.to / 4f
                        if (a.outer)
                            band(
                                r.outer,
                                r.outerWidth,
                                a0,
                                a1,
                                if (a.kind == FirstRunKind.SLEEP) P.sleep else P.work,
                            )
                        else
                            drawPath(arcPath(r.inner, a0, a1), P.task, style = Stroke(r.innerWidth))
                    }
                }
            }
        }
    }
}

/** .ld-o.ld-c-sleep / ld-c-work: the band with a 5 px glow of half its colour. */
private fun DrawScope.band(ring: Float, width: Float, a0: Float, a1: Float, color: Color) {
    val path = arcPath(ring, a0, a1)
    val glow =
        glowPaint(color.copy(alpha = .5f), 5f).apply {
            style = PaintingStyle.Stroke
            strokeWidth = width
        }
    drawIntoCanvas { it.drawPath(path, glow) }
    drawPath(path, color, style = Stroke(width))
}
