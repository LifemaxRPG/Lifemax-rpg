package com.lifemax.rpg.feature.lifedirector.presentation.drag

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.feature.lifedirector.domain.DragModel
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText

/**
 * The trash a lifted session is dropped on: a circle with a can in it and a label above it. Idle it
 * is a quiet red ring; [armed] (the finger is over it) it fills, glows and grows a little, and the
 * can opens its lid. [reduced] motion keeps the colours and drops the growing. [description] is
 * what TalkBack reads, and re-reads when it changes (the circle is a live region).
 */
@Composable
internal fun RemoveTarget(
    armed: Boolean,
    idleLabel: String,
    armedLabel: String,
    description: String,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val grow = if (armed && !reduced) 1.14f else 1f
    Box(
        modifier
            .size(DragModel.TARGET_DP.dp)
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        val fill = if (armed) P.block else P.block.copy(alpha = .16f)
        Box(
            Modifier.size(DragModel.TARGET_DP.dp)
                .then(
                    if (armed) Modifier.boxShadow(P.block.copy(alpha = .55f), 24.dp, CircleShape)
                    else Modifier
                )
                .background(fill, CircleShape)
                .border(1.5.dp, if (armed) P.rose else P.block.copy(alpha = .55f), CircleShape)
        )
        TrashCan(if (armed) Color.White else P.rose, open = armed, Modifier.size(32.dp))
        Text(
            if (armed) armedLabel else idleLabel,
            Modifier.align(Alignment.TopCenter)
                .wrapContentSize(unbounded = true, align = Alignment.BottomCenter)
                .graphicsLayer { translationY = -(size.height + LABEL_GAP.toPx()) }
                .background(P.toast, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            style = DirectorText,
            color = if (armed) P.rose else P.ink2,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

/** A trash can in strokes, its lid lifted and turned when [open]. */
@Composable
private fun TrashCan(color: Color, open: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val line = Stroke(width = w * .085f, cap = StrokeCap.Round)
        // the bin: a slightly tapering body with two ribs
        val l = w * .24f
        val r = w * .76f
        val top = w * .36f
        val bottom = w * .84f
        val body =
            Path().apply {
                moveTo(l, top)
                lineTo(l + w * .05f, bottom)
                lineTo(r - w * .05f, bottom)
                lineTo(r, top)
            }
        drawPath(body, color, style = line)
        drawLine(
            color,
            Offset(w * .41f, w * .46f),
            Offset(w * .42f, w * .74f),
            line.width,
            StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(w * .59f, w * .46f),
            Offset(w * .58f, w * .74f),
            line.width,
            StrokeCap.Round,
        )
        // the lid and its handle, turned about its left end when open
        rotate(if (open) -24f else 0f, pivot = Offset(l - w * .04f, w * .26f)) {
            drawLine(
                color,
                Offset(l - w * .06f, w * .26f),
                Offset(r + w * .06f, w * .26f),
                line.width,
                StrokeCap.Round,
            )
            drawLine(
                color,
                Offset(w * .42f, w * .26f),
                Offset(w * .42f, w * .17f),
                line.width,
                StrokeCap.Round,
            )
            drawLine(
                color,
                Offset(w * .42f, w * .17f),
                Offset(w * .58f, w * .17f),
                line.width,
                StrokeCap.Round,
            )
            drawLine(
                color,
                Offset(w * .58f, w * .17f),
                Offset(w * .58f, w * .26f),
                line.width,
                StrokeCap.Round,
            )
        }
    }
}

private val LABEL_GAP: Dp = 12.dp
