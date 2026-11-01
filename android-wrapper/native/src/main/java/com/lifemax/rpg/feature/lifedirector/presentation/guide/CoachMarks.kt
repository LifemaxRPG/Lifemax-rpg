package com.lifemax.rpg.feature.lifedirector.presentation.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.lifedirector.domain.DirectorGuide
import com.lifemax.rpg.feature.lifedirector.presentation.components.ButtonLook
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorButton
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText
import kotlin.math.roundToInt

/**
 * Where each part a guide can point at is on the screen (in the root's coordinates), as it is laid
 * out.
 */
class GuideAnchors {
    val rects: SnapshotStateMap<String, Rect> = mutableStateMapOf()

    /**
     * The part named [name]: one laid-out piece, or the pieces named `name#…` with it (Week's rows
     * are one block on the page, several list items here), as far as they are on screen.
     */
    fun rect(name: String): Rect? =
        rects.entries
            .filter { (k, r) -> (k == name || k.startsWith("$name#")) && r.height > 0f }
            .map { it.value }
            .reduceOrNull { a, b ->
                Rect(
                    minOf(a.left, b.left),
                    minOf(a.top, b.top),
                    maxOf(a.right, b.right),
                    maxOf(a.bottom, b.bottom),
                )
            }
}

/**
 * Names this part for the guide ([name] as the page's guide data names it: week.rows, week.stats,
 * week.yield; Today's dial, dial.centre, upNext, footer).
 */
fun Modifier.guideAnchor(anchors: GuideAnchors, name: String): Modifier =
    onGloballyPositioned { anchors.rects[name] = it.boundsInRoot() }
        .then(ForgetOnDetach(anchors, name))

/**
 * An item scrolled out of the list forgets its place, so the guide never lights a part where it no
 * longer is.
 */
private data class ForgetOnDetach(val anchors: GuideAnchors, val name: String) :
    androidx.compose.ui.node.ModifierNodeElement<ForgetNode>() {
    override fun create() = ForgetNode(anchors, name)

    override fun update(node: ForgetNode) {
        node.anchors = anchors
        node.name = name
    }
}

private class ForgetNode(var anchors: GuideAnchors, var name: String) : Modifier.Node() {
    override fun onDetach() {
        anchors.rects.remove(name)
    }
}

/**
 * The page's guide (.ld-tour), drawn natively: the screen dims, the part a step explains shows
 * through a lit cut-out (the page's spotlight, the step's padding around the part, corners of 18,
 * or round as the page's is around the dial and its middle), and a card with the step's words sits
 * below the part, or above it when it does not fit below. A tap outside the card goes on, as the
 * page's overlay does; Android Back ends the guide like Skip guide.
 */
@Composable
fun CoachMarks(
    guide: DirectorGuide,
    step: Int,
    anchors: GuideAnchors,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    val s = guide.steps.getOrNull(step) ?: return
    BackHandler(onBack = onSkip)
    val density = LocalDensity.current
    val pad = with(density) { s.pad.dp.toPx() } // the page's 8 around a part; 2 round the dial
    val corner = with(density) { 18.dp.toPx() }
    val spot =
        anchors.rect(s.anchor)?.let {
            Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad)
        }
    val none = remember { MutableInteractionSource() }
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .semantics { paneTitle = s.title }
            .clickable(none, null, onClick = onNext)
    ) {
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
        ) {
            drawRect(P.scrim)
            if (spot != null)
                if (s.round)
                    drawOval(Color.Black, spot.topLeft, spot.size, blendMode = BlendMode.Clear)
                else
                    drawRoundRect(
                        Color.Black,
                        Offset(spot.left, spot.top),
                        Size(spot.width, spot.height),
                        CornerRadius(corner),
                        blendMode = BlendMode.Clear,
                    )
        }
        if (spot != null)
            Canvas(Modifier.fillMaxSize()) {
                val edge = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())
                if (s.round)
                    drawOval(P.sky.copy(alpha = .6f), spot.topLeft, spot.size, style = edge)
                else
                    drawRoundRect(
                        P.sky.copy(alpha = .6f),
                        Offset(spot.left, spot.top),
                        Size(spot.width, spot.height),
                        CornerRadius(corner),
                        style = edge,
                    )
            }
        val screenH = with(density) { maxHeight.toPx() }
        val gap = with(density) { 12.dp.toPx() }
        var cardH by remember { mutableStateOf(0) }
        val top =
            when {
                spot == null -> (screenH - cardH) / 2
                screenH - spot.bottom >= cardH + gap * 2 -> spot.bottom + gap
                spot.top >= cardH + gap * 2 -> spot.top - gap - cardH
                else -> screenH - cardH - gap
            }.coerceIn(gap, (screenH - cardH - gap).coerceAtLeast(gap))
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            GuideCard(
                guide,
                step,
                Modifier.offset { IntOffset(0, top.roundToInt()) }
                    .onSizeChanged { cardH = it.height },
                onNext,
                onBack,
                onSkip,
            )
        }
    }
}

@Composable
private fun GuideCard(
    guide: DirectorGuide,
    step: Int,
    modifier: Modifier,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    val s = guide.steps[step]
    val last = step == guide.steps.lastIndex
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .padding(horizontal = 12.dp)
            .widthIn(max = 420.dp)
            .clip(shape)
            .background(P.toast)
            .border(1.dp, P.line, shape)
            .clickable(
                remember { MutableInteractionSource() },
                null,
            ) {} // a tap on the card itself does not go on
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${step + 1} of ${guide.steps.size}",
                Modifier.weight(1f),
                style = DirectorText,
                color = P.ink3,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                guide.labels.skip,
                Modifier.heightIn(min = 48.dp)
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 8.dp, vertical = 14.dp),
                style = DirectorText,
                color = P.ink2,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            s.title,
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = DirectorText,
            color = P.ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            buildAnnotatedString {
                s.text.forEach {
                    if (it.b)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = P.ink)) {
                            append(it.t)
                        }
                    else append(it.t)
                }
            },
            style = DirectorText,
            color = P.ink2,
            fontSize = 14.5.sp,
            lineHeight = 21.sp,
        )
        if (s.hint) Text(guide.labels.tap, style = DirectorText, color = P.ink3, fontSize = 12.5.sp)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                guide.steps.indices.forEach { k ->
                    Box(
                        Modifier.size(if (k == step) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (k == step) P.sky else if (k < step) P.ink3 else P.line)
                    )
                }
            }
            if (step > 0) DirectorButton(guide.labels.back, ButtonLook.GHOST, onBack, small = true)
            Box(Modifier.width(8.dp))
            DirectorButton(
                if (last) guide.labels.last else guide.labels.next,
                ButtonLook.PRIMARY,
                onNext,
                small = true,
            )
        }
    }
}
