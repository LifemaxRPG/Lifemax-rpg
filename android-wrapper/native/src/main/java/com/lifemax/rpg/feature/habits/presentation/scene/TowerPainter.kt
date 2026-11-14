package com.lifemax.rpg.feature.habits.presentation.scene

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.habits.domain.Dice
import com.lifemax.rpg.feature.habits.domain.HabitBlock
import com.lifemax.rpg.feature.habits.domain.HabitMaterial
import com.lifemax.rpg.feature.habits.domain.HabitStack
import com.lifemax.rpg.feature.habits.domain.Piece
import com.lifemax.rpg.feature.habits.domain.PieceKind
import com.lifemax.rpg.feature.habits.domain.TowerLayout
import com.lifemax.rpg.feature.habits.domain.TowerSpec as T
import com.lifemax.rpg.feature.habits.domain.crackLine
import com.lifemax.rpg.feature.habits.presentation.HabitsCopy
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Words measured for the scene. A tower is drawn in its own units (dp at scale 1) under one canvas
 * transform, so its words are measured once at that size (a density of 1: a 13 sp name is 13 units
 * tall) and scale with the tower, instead of being measured again at every frame of a zoom. The
 * owner's text size counts up to [MAX_FONT_SCALE]: a block's face is only so big.
 */
internal class SceneText(private val measurer: TextMeasurer, fontScale: Float) {
    private val density = Density(1f, fontScale.coerceIn(1f, MAX_FONT_SCALE))

    fun layout(
        text: String,
        size: Float,
        weight: FontWeight,
        maxWidth: Float,
        maxLines: Int = 1,
        spacing: Float = 0f,
    ): TextLayoutResult =
        measurer.measure(
            text,
            TextStyle(
                fontSize = size.sp,
                fontWeight = weight,
                letterSpacing = spacing.sp,
                lineHeight = (size * 1.18f).sp,
            ),
            overflow = TextOverflow.Ellipsis,
            softWrap = maxLines > 1,
            maxLines = maxLines,
            constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1)),
            density = density,
        )

    private companion object {
        const val MAX_FONT_SCALE = 1.25f
    }
}

/**
 * What changes from frame to frame on a tower. [seconds] runs while the screen shows and motion is
 * on (0 otherwise: everything stands still). [landing] is the block being laid: [fall] 0 as it
 * starts to drop to 1 as it lands, [squash] and [dip] the spring of the impact (1 at the hit, back
 * to 0), [burst] 0 to 1 for the flash and the dust, [sweep] 0 to 1 for the light that crosses a
 * block that changed material. [felled] is the floor knocked off a habit being broken, [fell] 0 to
 * 1 as it tumbles.
 */
internal class TowerMotion(
    val seconds: Float = 0f,
    val landing: String? = null,
    val fall: Float = 1f,
    val squash: Float = 0f,
    val dip: Float = 0f,
    val burst: Float = 1f,
    val sweep: Float = 1f,
    val swept: Boolean = false,
    val felled: String? = null,
    val fell: Float = 1f,
)

/** Draws towers in tower units: x from the middle of the front face, y down from the ground (0). */
internal class TowerPainter(private val text: SceneText) {
    private val path = Path()
    private val path2 = Path()
    private val dash = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))

    /** The y under which the tower being drawn is hidden behind its slab (none: [FAR]). */
    private var hidden = FAR

    /** The paved lot a tower stands on and its shadow, falling to the right. */
    fun DrawScope.lot(sky: SkyColors, width: Float) {
        val w = width / 2f + 34f
        val d = T.DY * 2.6f
        val lean = T.DX * 2.6f
        path.rewind()
        path.moveTo(-w - lean * .2f, d * .45f)
        path.lineTo(-w + lean * .8f, -d * .55f)
        path.lineTo(w + lean * .8f, -d * .55f)
        path.lineTo(w - lean * .2f, d * .45f)
        path.close()
        drawPath(path, sky.lot)
        drawPath(path, sky.lotEdge, style = Stroke(1.2f))
        // paving joints
        for (i in 1..3) {
            val u = i / 4f
            drawLine(
                sky.line,
                Offset(-w - lean * .2f + lean * u, d * .45f - d * u),
                Offset(w - lean * .2f + lean * u, d * .45f - d * u),
                1f,
            )
        }
        for (i in 1..5) {
            val x = -w + 2 * w * i / 6f
            drawLine(
                sky.line,
                Offset(x - lean * .2f, d * .45f),
                Offset(x + lean * .8f, -d * .55f),
                1f,
            )
        }
        drawOval(
            Brush.radialGradient(
                listOf(SceneInk.Shadow, Color.Transparent),
                Offset(width * .22f, -2f),
                width * .8f,
            ),
            Offset(width * .22f - width * .8f, -2f - T.DY * 1.3f),
            Size(width * 1.6f, T.DY * 2.6f),
        )
    }

    /**
     * One tower: its trees, every piece from the slab up, and what is playing on it. The slab
     * stands still; everything above it is slid down by [sink] (the camera's, for a tower too tall
     * for its window) and hidden where it has gone under the slab's top. [cue] is how strongly the
     * foundation's name shows on its slab (the city names its towers under them instead).
     */
    fun DrawScope.tower(
        layout: TowerLayout,
        stack: HabitStack,
        motion: TowerMotion,
        selected: String?,
        accent: Color,
        live: Boolean,
        detailed: Boolean,
        sink: Float = 0f,
        cue: Float = 1f,
    ) {
        for (tree in layout.trees) tree(tree.x, selected == tree.id, accent)
        val landingIndex =
            layout.pieces.indexOfFirst { it.id == motion.landing && it.kind == PieceKind.BLOCK }
        fun pieces(slab: Boolean) =
            layout.pieces.forEachIndexed { i, p ->
                if ((p.kind == PieceKind.SLAB) != slab) return@forEachIndexed
                val landing = i == landingIndex
                // the blocks under a landing one give a little, the nearest most
                val give = if (landingIndex > i) motion.dip * 4.5f / (landingIndex - i) else 0f
                val drop = if (landing) (1f - motion.fall) * DROP else 0f
                val alpha = if (landing) min(1f, motion.fall * 4f) else 1f
                translate(0f, give - drop) {
                    val squash = if (landing) motion.squash else 0f
                    scale(1f + .05f * squash, 1f - .13f * squash, Offset(0f, -p.bottom)) {
                        val sway = wobble(p, motion.seconds)
                        rotate(sway, Offset(0f, -p.bottom)) {
                            piece(
                                p,
                                stack,
                                motion,
                                selected,
                                accent,
                                live,
                                alpha,
                                detailed,
                                landing,
                                cue,
                            )
                        }
                    }
                }
            }
        fun above() {
            pieces(slab = false)
            if (landingIndex >= 0 && motion.burst < 1f)
                dust(layout.pieces[landingIndex], motion.burst)
            val gone = motion.felled
            if (gone != null && motion.fell < 1f)
                fallen(layout.labelled(gone)?.top ?: layout.roof, gone, motion.fell)
        }
        pieces(slab = true)
        if (sink <= 0f) above()
        else {
            hidden = -T.SLAB_H - sink
            clipRect(-FAR, -FAR, FAR, -T.SLAB_H) { translate(0f, sink) { above() } }
            hidden = FAR
        }
    }

    /** Two missed days: the block sits askew and, with motion on, sways. */
    private fun wobble(p: Piece, seconds: Float): Float {
        val b = p.block ?: return 0f
        if (p.kind != PieceKind.BLOCK || b.miss < 2 || b.done) return 0f
        return 1.1f + if (seconds > 0f) sin(seconds * 2.4f + p.bottom) * 1.2f else 0f
    }

    private fun DrawScope.piece(
        p: Piece,
        stack: HabitStack,
        motion: TowerMotion,
        selected: String?,
        accent: Color,
        live: Boolean,
        alpha: Float,
        detailed: Boolean,
        landing: Boolean,
        cue: Float,
    ) {
        val half = p.width / 2f
        val top = -p.top
        val bottom = -p.bottom
        when (p.kind) {
            PieceKind.SLAB -> {
                solid(
                    half,
                    top,
                    bottom,
                    SceneInk.SlabFrontTop,
                    SceneInk.SlabFrontBottom,
                    SceneInk.SlabTop,
                    SceneInk.SlabSide,
                    1f,
                )
                edges(half, top, bottom, 1f)
                if (detailed && cue > .02f) {
                    val name =
                        text.layout(
                            stack.cue.uppercase(),
                            10.5f,
                            FontWeight.Bold,
                            p.width - 20f,
                            spacing = 1.1f,
                        )
                    drawText(
                        name,
                        SceneInk.SlabText,
                        Offset(-name.size.width / 2f, (top + bottom) / 2f - name.size.height / 2f),
                        alpha = cue,
                    )
                }
            }
            PieceKind.GHOST -> {
                drawRoundRect(
                    SceneInk.PlanFill,
                    Offset(-half, top),
                    Size(p.width, p.height),
                    CornerRadius(8f),
                )
                drawRoundRect(
                    SceneInk.PlanLine,
                    Offset(-half, top),
                    Size(p.width, p.height),
                    CornerRadius(8f),
                    style = Stroke(1.4f, pathEffect = dash),
                )
                val words =
                    text.layout(HabitsCopy.STACK_HERE, 11.5f, FontWeight.SemiBold, p.width - 16f)
                drawText(
                    words,
                    SceneInk.PlanText,
                    Offset(-words.size.width / 2f, (top + bottom) / 2f - words.size.height / 2f),
                )
            }
            PieceKind.REWARD -> reward(p, stack, motion.seconds, detailed)
            PieceKind.BLOCK,
            PieceKind.FLOOR -> {
                val b = p.block ?: return
                val f = faces(b.material)
                val isNext = live && stack.next == b.id && p.labelled
                if (p.plan) blueprint(half, top, bottom, f, isNext, motion.seconds)
                else {
                    if (b.material == HabitMaterial.GLASS)
                        drawRect(
                            SceneInk.GlassBacking,
                            Offset(-half, top),
                            Size(p.width, p.height),
                            alpha = alpha,
                        )
                    solid(half, top, bottom, f.frontTop, f.frontBottom, f.top, f.side, alpha)
                    if (detailed) texture(p, b, half, top, bottom, alpha, motion.seconds)
                    edges(half, top, bottom, alpha)
                    if (b.material == HabitMaterial.GLASS)
                        outline(
                            half,
                            top,
                            bottom,
                            SceneInk.GlassEdge.copy(alpha = alpha * .8f),
                            1.3f,
                        )
                }
                if (p.labelled) {
                    if (!b.fade && (b.miss >= 1 || b.scar)) crack(p, b, stack.id, half, top)
                    if (detailed) label(p, b, f, half, top, bottom, alpha)
                }
                if (isNext && !p.plan && b.fade) glow(half, top, bottom, f.tint, motion.seconds)
                if (landing && motion.burst < 1f) {
                    topFace(half, top)
                    drawPath(path, Color.White, alpha = (1f - motion.burst) * .9f)
                }
                if (landing && motion.swept && motion.sweep < 1f)
                    sheen(half, top, bottom, motion.sweep)
                if (selected == b.id && (p.labelled || b.fade))
                    outline(half, top, bottom, accent, 2.6f)
            }
        }
    }

    private fun topFace(half: Float, top: Float) {
        path.rewind()
        path.moveTo(-half, top)
        path.lineTo(-half + T.DX, top - T.DY)
        path.lineTo(half + T.DX, top - T.DY)
        path.lineTo(half, top)
        path.close()
    }

    private fun sideFace(half: Float, top: Float, bottom: Float) {
        path2.rewind()
        path2.moveTo(half, top)
        path2.lineTo(half + T.DX, top - T.DY)
        path2.lineTo(half + T.DX, bottom - T.DY)
        path2.lineTo(half, bottom)
        path2.close()
    }

    /** The silhouette of a piece: its front, top and side faces as one outline. */
    private fun silhouette(half: Float, top: Float, bottom: Float) {
        path.rewind()
        path.moveTo(-half, bottom)
        path.lineTo(-half, top)
        path.lineTo(-half + T.DX, top - T.DY)
        path.lineTo(half + T.DX, top - T.DY)
        path.lineTo(half + T.DX, bottom - T.DY)
        path.lineTo(half, bottom)
        path.close()
    }

    private fun DrawScope.solid(
        half: Float,
        top: Float,
        bottom: Float,
        frontTop: Color,
        frontBottom: Color,
        topColor: Color,
        side: Color,
        alpha: Float,
    ) {
        drawRect(
            Brush.verticalGradient(listOf(frontTop, frontBottom), top, bottom),
            Offset(-half, top),
            Size(half * 2f, bottom - top),
            alpha = alpha,
        )
        topFace(half, top)
        drawPath(path, topColor, alpha = alpha)
        sideFace(half, top, bottom)
        drawPath(path2, side, alpha = alpha)
    }

    /** Light catching the top edge, and the dark line where a piece meets the one under it. */
    private fun DrawScope.edges(half: Float, top: Float, bottom: Float, alpha: Float) {
        drawLine(
            SceneInk.Highlight,
            Offset(-half, top + .5f),
            Offset(half, top + .5f),
            1f,
            alpha = alpha,
        )
        drawLine(
            SceneInk.Occlusion,
            Offset(-half, bottom - 1f),
            Offset(half, bottom - 1f),
            2f,
            alpha = alpha,
        )
        drawLine(
            SceneInk.Occlusion,
            Offset(half, top),
            Offset(half, bottom),
            1f,
            alpha = alpha * .6f,
        )
    }

    private fun DrawScope.outline(
        half: Float,
        top: Float,
        bottom: Float,
        color: Color,
        width: Float,
    ) {
        silhouette(half, top, bottom)
        drawPath(path, color, style = Stroke(width, join = StrokeJoin.Round))
    }

    /**
     * A block not laid yet today: a wash, a dashed outline, its material at its foot. The next one
     * glows.
     */
    private fun DrawScope.blueprint(
        half: Float,
        top: Float,
        bottom: Float,
        f: Faces,
        next: Boolean,
        seconds: Float,
    ) {
        drawRect(SceneInk.PlanFill, Offset(-half, top), Size(half * 2f, bottom - top))
        topFace(half, top)
        drawPath(path, SceneInk.PlanFill)
        silhouette(half, top, bottom)
        drawPath(
            path,
            SceneInk.PlanLine,
            style = Stroke(1.2f, pathEffect = dash, join = StrokeJoin.Round),
        )
        drawLine(SceneInk.PlanLine, Offset(-half, top), Offset(half, top), 1f, pathEffect = dash)
        drawLine(SceneInk.PlanLine, Offset(half, top), Offset(half, bottom), 1f, pathEffect = dash)
        drawLine(
            f.tint,
            Offset(-half, bottom - 1.5f),
            Offset(half, bottom - 1.5f),
            3f,
            cap = StrokeCap.Round,
            alpha = .85f,
        )
        if (next) glow(half, top, bottom, f.tint, seconds)
    }

    /**
     * The next block to lay: its outline lit in its material's colour, breathing while motion is
     * on.
     */
    private fun DrawScope.glow(
        half: Float,
        top: Float,
        bottom: Float,
        tint: Color,
        seconds: Float,
    ) {
        val pulse = if (seconds > 0f) .62f + .38f * sin(seconds * 3.1f) else 1f
        silhouette(half, top, bottom)
        drawPath(path, tint, alpha = .22f * pulse, style = Stroke(9f, join = StrokeJoin.Round))
        drawPath(path, tint, alpha = .4f * pulse, style = Stroke(4.5f, join = StrokeJoin.Round))
        drawPath(
            path,
            tint,
            alpha = .55f + .45f * pulse,
            style = Stroke(1.8f, join = StrokeJoin.Round),
        )
    }

    /**
     * What a material looks like up close: bricks, cut stone, the shine of glass and gold, lit
     * windows.
     */
    private fun DrawScope.texture(
        p: Piece,
        b: HabitBlock,
        half: Float,
        top: Float,
        bottom: Float,
        alpha: Float,
        seconds: Float,
    ) {
        val h = bottom - top
        when (b.material) {
            HabitMaterial.CLAY -> {
                // courses of bricks, the joints of one over the middle of the bricks below
                var row = 0
                var y = top + 11f
                while (y < bottom - 3f) {
                    drawLine(MORTAR, Offset(-half, y), Offset(half, y), 1f, alpha = alpha)
                    y += 11f
                    row++
                }
                for (r in 0..row) {
                    val y0 = top + r * 11f
                    val y1 = min(bottom, y0 + 11f)
                    var x = -half + if (r % 2 == 0) 28f else 14f
                    while (x < half - 2f) {
                        drawLine(MORTAR, Offset(x, y0), Offset(x, y1), 1f, alpha = alpha)
                        x += 28f
                    }
                }
            }
            HabitMaterial.STONE -> {
                val dice = Dice(b.id + "s")
                val a = top + h * (.38f + dice.next() * .24f)
                drawLine(JOINT, Offset(-half, a), Offset(half, a), 1.1f, alpha = alpha)
                drawLine(
                    JOINT,
                    Offset(-half + p.width * (.2f + dice.next() * .25f), top),
                    Offset(-half + p.width * .3f, a),
                    1.1f,
                    alpha = alpha,
                )
                val x2 = -half + p.width * (.5f + dice.next() * .3f)
                drawLine(JOINT, Offset(x2, a), Offset(x2, bottom), 1.1f, alpha = alpha)
                val x3 = -half + p.width * (.62f + dice.next() * .25f)
                drawLine(JOINT, Offset(x3, top), Offset(x3, a), 1.1f, alpha = alpha)
            }
            HabitMaterial.GLASS -> {
                clipRect(-half, top, half, bottom) {
                    streak(-half + p.width * .1f, p.width * .16f, top, bottom, .2f * alpha)
                    streak(-half + p.width * .32f, p.width * .05f, top, bottom, .13f * alpha)
                }
            }
            HabitMaterial.GOLD -> {
                // a strip of light that crosses the block now and then
                if (seconds > 0f) {
                    val phase = ((seconds * .22f + Dice(b.id).next()) % 1f)
                    if (phase < .35f) sheen(half, top, bottom, phase / .35f)
                }
            }
            HabitMaterial.FADE -> {
                val dice = Dice(b.id + "w" + p.bottom.toInt())
                val wide = (p.width - 28f) / 5f
                for (i in 0 until 5) {
                    val lit = dice.next()
                    drawRect(
                        if (lit < .55f) SceneInk.WindowLit
                        else if (lit < .8f) SceneInk.WindowDim else SceneInk.WindowDark,
                        Offset(-half + 14f + i * wide + 3f, top + 7f),
                        Size(wide - 6f, h - 14f),
                        alpha = alpha,
                    )
                }
            }
        }
    }

    private fun DrawScope.streak(x: Float, w: Float, top: Float, bottom: Float, alpha: Float) {
        val lean = (bottom - top) * .45f
        path.rewind()
        path.moveTo(x, top)
        path.lineTo(x + w, top)
        path.lineTo(x + w + lean, bottom)
        path.lineTo(x + lean, bottom)
        path.close()
        drawPath(path, Color.White, alpha = alpha)
    }

    /**
     * A band of light across the front face, [at] 0 as it enters at the left to 1 as it leaves at
     * the right.
     */
    private fun DrawScope.sheen(half: Float, top: Float, bottom: Float, at: Float) {
        val band = 34f
        val x = -half - band + (half * 2f + band * 2f) * at
        clipRect(-half, top, half, bottom) {
            val lean = (bottom - top) * .5f
            path.rewind()
            path.moveTo(x, bottom)
            path.lineTo(x + lean, top)
            path.lineTo(x + lean + band, top)
            path.lineTo(x + band, bottom)
            path.close()
            drawPath(
                path,
                Brush.horizontalGradient(
                    listOf(Color.Transparent, SceneInk.Sheen, Color.Transparent),
                    x,
                    x + lean + band,
                ),
            )
        }
    }

    /**
     * A crack for a missed day, or the gold seam left by coming back: each block's its own, and
     * kept to that block's front face.
     */
    private fun DrawScope.crack(p: Piece, b: HabitBlock, stackId: String, half: Float, top: Float) {
        val points = crackLine(stackId + b.id, p.height > 60f)
        path.rewind()
        points.forEachIndexed { i, (u, v) ->
            val x = -half + u * p.width
            val y = top + v * p.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        // a short branch off its middle turn, away from the way the crack goes on
        val turn = points.size / 2
        val (u, v) = points[turn]
        val away = if (points[turn + 1].first > u) -1f else 1f
        path.moveTo(-half + u * p.width, top + v * p.height)
        path.lineTo(-half + u * p.width + away * 9f, top + v * p.height + 6f)
        clipRect(-half, top, half, top + p.height) {
            if (b.miss >= 1)
                drawPath(
                    path,
                    if (p.plan) SceneInk.CrackOnPlan else SceneInk.Crack,
                    style = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            else {
                drawPath(
                    path,
                    SceneInk.SeamGlow,
                    style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawPath(
                    path,
                    SceneInk.Seam,
                    style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }

    /**
     * The habit's name on the front face, on two lines where the block is tall enough, and its
     * count under it: in the middle of what shows of the face, and only what fits there whole (a
     * block half behind the slab keeps its name, or says nothing).
     */
    private fun DrawScope.label(
        p: Piece,
        b: HabitBlock,
        f: Faces,
        half: Float,
        top: Float,
        bottom: Float,
        alpha: Float,
    ) {
        val ink = if (p.plan) SceneInk.PlanText else f.ink
        val soft = if (p.plan) SceneInk.PlanText.copy(alpha = .7f) else f.inkSoft
        val width = p.width - 2 * PAD
        val count = text.layout(HabitsCopy.count(b), 9.5f, FontWeight.Medium, width)
        val low = min(bottom, hidden)
        val roomy = low - top >= 60f
        val name =
            text.layout(
                b.name,
                if (p.kind == PieceKind.FLOOR) 11f else 13.5f,
                FontWeight.Bold,
                width,
                if (roomy) 2 else 1,
            )
        if (p.kind == PieceKind.FLOOR) {
            // a floor is low: the name alone, on a dark plate over the windows
            if (low < bottom) return
            val y = (top + bottom) / 2f - name.size.height / 2f
            drawRoundRect(
                PLATE,
                Offset(-half + PAD - 5f, y - 1.5f),
                Size(name.size.width + 10f, name.size.height + 3f),
                CornerRadius(4f),
                alpha = alpha,
            )
            drawText(name, ink, Offset(-half + PAD, y), alpha = alpha)
            return
        }
        val total = name.size.height + 2f + count.size.height
        if (low - top >= total + 6f) {
            val y = (top + low) / 2f - total / 2f
            drawText(name, ink, Offset(-half + PAD, y), alpha = alpha)
            drawText(count, soft, Offset(-half + PAD, y + name.size.height + 2f), alpha = alpha)
        } else if (low - top >= name.size.height + 6f)
            drawText(
                name,
                ink,
                Offset(-half + PAD, (top + low) / 2f - name.size.height / 2f),
                alpha = alpha,
            )
    }

    /**
     * The rooftop reward: dull until the stack is done, gold when it can be claimed, flagged once
     * it has been.
     */
    private fun DrawScope.reward(p: Piece, stack: HabitStack, seconds: Float, detailed: Boolean) {
        val half = p.width / 2f
        val top = -p.top
        val bottom = -p.bottom
        val open = stack.claimable || stack.claimed
        if (open) {
            val g = faces(HabitMaterial.GOLD)
            solid(half, top, bottom, g.frontTop, g.frontBottom, g.top, g.side, 1f)
        } else
            solid(
                half,
                top,
                bottom,
                SceneInk.RewardLockFront,
                SceneInk.RewardLockFront,
                SceneInk.RewardLockTop,
                SceneInk.RewardLockSide,
                1f,
            )
        edges(half, top, bottom, 1f)
        if (stack.claimable) glow(half, top, bottom, faces(HabitMaterial.GOLD).tint, seconds)
        if (detailed) {
            val words =
                text.layout(
                    if (stack.claimed) HabitsCopy.ENJOYED else stack.treat,
                    10.5f,
                    FontWeight.Bold,
                    p.width - 14f,
                )
            drawText(
                words,
                if (open) faces(HabitMaterial.GOLD).ink else SceneInk.RewardLockText,
                Offset(-words.size.width / 2f, (top + bottom) / 2f - words.size.height / 2f),
            )
        }
        if (stack.claimed) {
            val x = half * .7f + T.DX * .5f
            val y = top - T.DY * .5f
            drawLine(SceneInk.FlagPole, Offset(x, y), Offset(x, y - 26f), 1.6f)
            val wave = if (seconds > 0f) sin(seconds * 4f) * 1.5f else 0f
            path.rewind()
            path.moveTo(x, y - 26f)
            path.lineTo(x + 13f, y - 21.5f + wave)
            path.lineTo(x, y - 17f)
            path.close()
            drawPath(path, SceneInk.Flag)
        }
    }

    /** A habit that is gone: a tree on the lot beside the tower. */
    private fun DrawScope.tree(x: Float, selected: Boolean, accent: Color) {
        if (selected) drawOval(accent, Offset(x - 19f, -5f), Size(38f, 11f), style = Stroke(2f))
        drawRect(SceneInk.TreeTrunk, Offset(x - 2.5f, -24f), Size(5f, 24f))
        drawCircle(SceneInk.TreeB, 10f, Offset(x - 9f, -27f))
        drawCircle(SceneInk.TreeC, 11f, Offset(x + 9f, -28f))
        drawCircle(SceneInk.TreeA, 15f, Offset(x, -36f))
    }

    /** Dust thrown out along a block's base as it lands. */
    private fun DrawScope.dust(p: Piece, burst: Float) {
        val half = p.width / 2f
        val alpha = (1f - burst) * .7f
        for (i in 0 until 10) {
            val left = i < 5
            val u = (i % 5 + .5f) / 5f
            val x = (if (left) -1f else 1f) * (half - 18f * u + burst * (26f + 9f * (i % 3)))
            drawCircle(
                SceneInk.Dust,
                2.5f + i % 3 + burst * 5f,
                Offset(x, -p.bottom - 2f - burst * 9f * u),
                alpha,
            )
        }
    }

    /** The floor that has just been knocked off, tumbling away to the right, and its rubble. */
    private fun DrawScope.fallen(from: Float, key: String, at: Float) {
        val f = faces(HabitMaterial.FADE)
        val half = T.W / 2f
        val ease = at * at
        translate(70f * at, 210f * ease) {
            rotate(46f * at, Offset(0f, -from - T.FLOOR / 2f)) {
                solid(
                    half,
                    -from - T.FLOOR,
                    -from,
                    f.frontTop,
                    f.frontBottom,
                    f.top,
                    f.side,
                    1f - ease,
                )
            }
        }
        val dice = Dice(key + "f")
        for (i in 0 until 7) {
            val dx = (dice.next() - .5f) * 150f
            val up = dice.next() * 60f
            drawRect(
                if (i % 2 == 0) f.top else f.side,
                Offset(
                    (dice.next() * 2f - 1f) * half * .8f + dx * at,
                    -from - T.FLOOR / 2f - up * sin(at * PI.toFloat()) + 120f * ease,
                ),
                Size(4f + i % 3 * 2f, 3f + i % 2 * 2f),
                alpha = 1f - ease,
            )
        }
    }

    private companion object {
        const val DROP = 230f

        /** Further than anything of a tower is drawn, for a clip open on three sides. */
        const val FAR = 100000f
        const val PAD = 12f
        val MORTAR = Color(0x3D3A1A0B)
        val JOINT = Color(0x4018233D)
        val PLATE = Color(0x9E2A0F1C)
    }
}
