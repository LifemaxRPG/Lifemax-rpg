package com.lifemax.rpg.feature.lifedirector.presentation.time

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import com.lifemax.rpg.core.designsystem.glowPaint
import com.lifemax.rpg.feature.lifedirector.domain.DialRadii
import com.lifemax.rpg.feature.lifedirector.domain.GuideTag
import com.lifemax.rpg.feature.lifedirector.domain.HourWord
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText
import com.lifemax.rpg.feature.lifedirector.presentation.components.LocalDirectorBuzz
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialFace
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialGeometry
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DragGeometry
import com.lifemax.rpg.feature.lifedirector.presentation.dial.EARLIER
import com.lifemax.rpg.feature.lifedirector.presentation.dial.LATER
import com.lifemax.rpg.feature.lifedirector.presentation.dial.TourTags
import com.lifemax.rpg.feature.lifedirector.presentation.dial.arcPath
import com.lifemax.rpg.feature.lifedirector.presentation.dial.faded
import com.lifemax.rpg.feature.lifedirector.presentation.dial.handle
import com.lifemax.rpg.feature.lifedirector.presentation.dial.inUnits
import com.lifemax.rpg.feature.lifedirector.presentation.dial.unitPx

/**
 * My time's sleep dial (the page's .ld-mini): the main dial's face at most 300 dp across, the work
 * arcs faint behind, the sleep arc on the outer ring from [bed] to [wake] and a handle at each end;
 * in the middle "Sleep", the length and the two times, in the page's words for each stop
 * ([MiniDialMath.texts]). A touch on a handle holds it (it fills, as the page's .ld-hd.on, over 150
 * ms unless [reducedMotion]) and it follows the finger from stop to stop with the page's 6 ms buzz,
 * reporting each new pair to [onChange]; nothing is saved until the sheet's Save sleep. Any other
 * touch goes on to the list, so it still scrolls. Each handle is also a TalkBack slider over the
 * day's stops, moved a stop later or earlier by its adjust gestures or its two actions. [radii] and
 * [hourWords] are the main dial's, so the face is the same. [tags] are the name tags set-up's guide
 * pins on it (Bedtime, Wake up: [miniGuideTags]), drawn as the main dial's guide draws its own;
 * none by default. [dialModifier] goes on the dial's own square (not the row it is centred in), for
 * what must fit the dial itself, such as set-up's guide anchor (time.sleep, a round spotlight).
 */
@Composable
fun MiniSleepDial(
    dial: MiniDial,
    bed: Int,
    wake: Int,
    radii: DialRadii,
    hourWords: List<HourWord>,
    onChange: (bed: Int, wake: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reducedMotion: Boolean = false,
    tags: List<GuideTag> = emptyList(),
    dialModifier: Modifier = Modifier,
) {
    val text = rememberTextMeasurer()
    val face = remember(radii, hourWords) { DialFace(radii, hourWords, text) }
    val buzz = LocalDirectorBuzz.current
    var held by remember { mutableStateOf<End?>(null) }
    val fill by
        animateFloatAsState(
            if (held != null) 1f else 0f,
            if (reducedMotion) snap() else tween(HOLD_MS),
            label = "hold",
        )
    val latest by rememberUpdatedState(Triple(bed, wake, onChange))
    val texts = MiniDialMath.texts(dial, bed, wake)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val side = min(maxWidth, MAX_SIDE)
        Box(
            Modifier.size(side)
                .then(dialModifier)
                .alpha(if (enabled) 1f else DISABLED)
                .semantics { contentDescription = dial.words.group }
                .pointerInput(enabled, dial.step) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down =
                            awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull {
                                it.changedToDown()
                            } ?: return@awaitEachGesture
                        val unit = unitPx(size.width.toFloat())
                        val (b0, w0) = latest
                        val radius = maxOf(DragGeometry.HIT_RADIUS, HIT.toPx() / unit)
                        val end =
                            listOf(End.BED to b0, End.WAKE to w0)
                                .map { (e, m) ->
                                    e to
                                        (DialGeometry.point(radii.outer, MiniDialMath.angle(m)) -
                                                down.position / unit)
                                            .getDistance()
                                }
                                .filter { it.second <= radius }
                                .minByOrNull { it.second }
                                ?.first ?: return@awaitEachGesture
                        down.consume()
                        held = end
                        var moved = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (
                                    !moved &&
                                        (change.position - down.position).getDistance() >
                                            viewConfiguration.touchSlop
                                )
                                    moved = true
                                if (moved) {
                                    val m =
                                        MiniDialMath.snap(
                                            DialGeometry.angleAt(change.position / unit),
                                            dial.step,
                                        )
                                    val (b, w, report) = latest
                                    val now = if (end == End.BED) b else w
                                    if (m != now) {
                                        buzz?.buzz(BUZZ_MS)
                                        if (end == End.BED) report(m, w) else report(b, m)
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } finally {
                            held = null
                        }
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                inUnits {
                    face.draw(this)
                    faded(CONTEXT_ALPHA) {
                        for (c in dial.context) band(
                            radii,
                            MiniDialMath.angle(c.from),
                            MiniDialMath.angle(c.to),
                            true,
                        )
                    }
                    val (a0, a1) = MiniDialMath.arc(bed, wake)
                    band(radii, a0, a1, false)
                    knob(radii.outer, MiniDialMath.angle(bed), fill.takeIf { held == End.BED })
                    knob(radii.outer, MiniDialMath.angle(wake), fill.takeIf { held == End.WAKE })
                }
            }
            TourTags(tags, still = reducedMotion)
            HandleNodes(dial, bed, wake, texts, radii, enabled) { b, w ->
                buzz?.buzz(BUZZ_MS)
                onChange(b, w)
            }
            Centre(dial.words.kick, texts, Modifier.align(Alignment.Center).width(side * CORE))
        }
    }
}

private enum class End {
    BED,
    WAKE,
}

/**
 * An outer-ring band (.ld-o): sleep, or a work arc ([work]), with the page's 5 px glow of half its
 * colour.
 */
private fun DrawScope.band(radii: DialRadii, a0: Float, a1: Float, work: Boolean) {
    if (a1 <= a0) return
    val color = if (work) P.work else P.sleep
    val path = arcPath(radii.outer, a0, a1)
    val glow =
        glowPaint(color.copy(alpha = .5f), 5f).apply {
            style = PaintingStyle.Stroke
            strokeWidth = radii.outerWidth
        }
    drawIntoCanvas { it.drawPath(path, glow) }
    drawPath(path, color, style = Stroke(radii.outerWidth))
}

/**
 * A handle in sleep's colour; a held one fills by [fill] (null when not held), as .ld-hd.on's 150
 * ms fade.
 */
private fun DrawScope.knob(ring: Float, a: Float, fill: Float?) {
    handle(ring, a, P.sleep, held = false)
    if (fill != null && fill > 0f) faded(fill) { handle(ring, a, P.sleep, held = true) }
}

/**
 * The page's .ld-core over the mini dial: "Sleep", the length in the number font, the two times.
 */
@Composable
private fun Centre(kick: String, texts: MiniDialTexts, modifier: Modifier) {
    val density = LocalDensity.current
    // the page sizes these in rem, so they keep their size whatever the dial's or the font's
    fun px(v: Float) = with(density) { v.dp.toSp() }
    val narrow = LocalConfiguration.current.screenWidthDp <= NARROW_DP
    Column(
        modifier.clearAndSetSemantics {
            contentDescription =
                listOf(kick, texts.length, texts.bed + SPAN + texts.wake).joinToString(", ")
            liveRegion = LiveRegionMode.Polite
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            kick,
            style = DirectorText,
            color = P.ink3,
            fontSize = px(11.2f),
            fontWeight = FontWeight.Bold,
            letterSpacing = .01.em,
            maxLines = 1,
        )
        Text(
            texts.length,
            Modifier.padding(top = 2.dp),
            style = DirectorText.copy(fontFamily = FontFamily.Monospace),
            fontSize = px(if (narrow) 18.4f else 21.6f),
            fontWeight = FontWeight.Bold,
            letterSpacing = (-.03).em,
            lineHeight = 1.1.em,
            maxLines = 1,
        )
        Text(
            texts.bed + SPAN + texts.wake,
            style = DirectorText,
            color = P.ink2,
            fontSize = px(11.84f),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * A 48 dp TalkBack node on each handle (the page's role="slider" with its aria-label): its label
 * and time, a slider over the day's stops, and a stop later or earlier by the adjust gestures and
 * the two actions, round the clock. Touches pass through to the dial.
 */
@Composable
private fun HandleNodes(
    dial: MiniDial,
    bed: Int,
    wake: Int,
    texts: MiniDialTexts,
    radii: DialRadii,
    enabled: Boolean,
    onChange: (Int, Int) -> Unit,
) {
    val stops = MiniDialMath.DAY / dial.step
    val ends =
        listOf(
            Triple(dial.words.bed, bed, texts.bed) to { m: Int -> onChange(m, wake) },
            Triple(dial.words.wake, wake, texts.wake) to { m: Int -> onChange(bed, m) },
        )
    Layout(
        content = {
            ends.forEach { (end, set) ->
                val (label, m, said) = end
                val move = { by: Int ->
                    if (enabled) set(MiniDialMath.stepped(m, by, dial.step))
                    enabled
                }
                Box(
                    Modifier.semantics {
                        contentDescription = label
                        stateDescription = said
                        progressBarRangeInfo =
                            ProgressBarRangeInfo(
                                (m / dial.step).toFloat(),
                                0f..(stops - 1).toFloat(),
                                (stops - 2).coerceAtLeast(0),
                            )
                        setProgress { target ->
                            val k = target.toInt().coerceIn(0, stops - 1)
                            if (!enabled || k * dial.step == m) false
                            else {
                                set(k * dial.step)
                                true
                            }
                        }
                        customActions =
                            listOf(
                                CustomAccessibilityAction(LATER) { move(1) },
                                CustomAccessibilityAction(EARLIER) { move(-1) },
                            )
                    }
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val target = 48.dp.roundToPx()
        val scale = unitPx(constraints.maxWidth.toFloat())
        val placeables = measurables.map { it.measure(Constraints.fixed(target, target)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, placeable ->
                val m = if (i == 0) bed else wake
                val at: Offset = DialGeometry.point(radii.outer, MiniDialMath.angle(m)) * scale
                placeable.place(at.x.toInt() - target / 2, at.y.toInt() - target / 2)
            }
        }
    }
}

/** .ld-mini: min(100%, 300px); its .ld-core 46 % of that. */
private val MAX_SIDE = 300.dp
private const val CORE = .46f

/** A handle's touch target, at least 48 dp across however small the dial is drawn. */
private val HIT = 24.dp

/** .ld-ctx's opacity: work behind sleep, faint. */
private const val CONTEXT_ALPHA = .28f

/** .ld-hd-ring's fill transition. */
private const val HOLD_MS = 150

/** The page's buzz as a held handle passes a stop (buzz() in attachMini). */
private const val BUZZ_MS = 6L

/** A dial that cannot be used, as the page dims a disabled control. */
private const val DISABLED = .45f

/** The page's join between the two times (fHm(bed) + ' – ' + fHm(wake)). */
private const val SPAN = " – "

/** The page's (max-width:380px) rule, which makes .ld-core-big 1.15rem. */
private const val NARROW_DP = 380
