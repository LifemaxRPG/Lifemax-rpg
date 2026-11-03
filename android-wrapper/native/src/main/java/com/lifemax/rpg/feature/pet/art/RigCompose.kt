package com.lifemax.rpg.feature.pet.art

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Re-assembles a frame of the art from its parts with the proportions of one cat and the pose of
 * its ears, tail and whiskers: a port of the web's `petRigCompose` (`38-pet-rig.js`), pixel for
 * pixel.
 * - ears are scaled along and across their axis and turned about their base;
 * - the tail is bent along its own centre line, so it can be longer or shorter without being
 *   thicker (native adds a bend that grows toward the tip, for a tail that follows through);
 * - whiskers are trimmed, or extended by repeating their last pixel in their own direction;
 * - a band of leg rows and a band of torso columns are stretched, nearest neighbour; then the
 *   outline is repaired where a part was cut away and nothing now covers the cut.
 *
 * The result is a [canvasWidth] x [canvasHeight] picture, bigger than the frame by margins for ears
 * and tails that reach out ([MARGIN_LEFT] and the others): the frame's own top left lies at
 * ([MARGIN_LEFT], [MARGIN_TOP]) in it.
 *
 * An instance owns its scratch buffers and is not thread safe. Composing allocates nothing.
 *
 * @param frameWidth width of the art's frames (72)
 * @param frameHeight their height (48)
 */
class RigCompose(private val frameWidth: Int, private val frameHeight: Int) {
    /** Width of the composed picture. */
    val canvasWidth = frameWidth + MARGIN_LEFT + MARGIN_RIGHT

    /** Height of the composed picture. */
    val canvasHeight = frameHeight + MARGIN_TOP + MARGIN_BOTTOM

    private val cells = canvasWidth * canvasHeight
    private val part = ByteArray(frameWidth * frameHeight)
    private val edge = ByteArray(frameWidth * frameHeight)
    private val body = IntArray(cells)
    private val bodyEdge = ByteArray(cells)
    private val tailLayer = IntArray(cells)
    private val topLayer = IntArray(cells)
    private val cols = IntArray(canvasWidth)
    private val rows = IntArray(canvasHeight)
    private val axisX = DoubleArray(MAX_TAIL_VERTICES)
    private val axisY = DoubleArray(MAX_TAIL_VERTICES)
    private val segLen = DoubleArray(MAX_TAIL_VERTICES)
    private val segDirX = DoubleArray(MAX_TAIL_VERTICES)
    private val segDirY = DoubleArray(MAX_TAIL_VERTICES)
    private val srcLen = DoubleArray(MAX_TAIL_VERTICES)
    private val srcDirX = DoubleArray(MAX_TAIL_VERTICES)
    private val srcDirY = DoubleArray(MAX_TAIL_VERTICES)

    // the stretch of the current frame, set by compose() and read by the part painters
    private var legFrom = 0
    private var legTo = 0
    private var legRows = 0
    private var torsoFrom = 0
    private var torsoTo = 0
    private var torsoCols = 0
    private var shiftY = 0
    private var shiftX = 0

    // where the parts are being painted: canvas pixels of the layer being drawn
    private var target = tailLayer

    /**
     * Compose [frame] of [src] (the art frame's `frameWidth * frameHeight` ARGB pixels) with
     * [morph] and [pose] into [out] (`canvasWidth * canvasHeight` pixels, every one written).
     */
    fun compose(src: IntArray, frame: RigFrame, morph: RigMorph, pose: RigPose, out: IntArray) {
        require(src.size == frameWidth * frameHeight && out.size == cells) { "wrong buffer size" }
        setUp(frame, morph)
        splitBody(src, frame)
        tailLayer.fill(0)
        topLayer.fill(0)
        target = tailLayer
        val tail = frame.tail
        if (tail != null)
            paintTail(
                src,
                tail,
                quantise(if (morph.bob) min(morph.tail, BOB_TAIL) else morph.tail),
                pose,
            )
        target = topLayer
        paintEars(src, frame, morph, pose)
        paintWhiskers(src, frame, quantise(morph.whisker * pose.whisker))
        for (i in 0 until cells) {
            out[i] =
                if (topLayer[i] ushr 24 != 0) topLayer[i]
                else if (body[i] ushr 24 != 0) body[i] else tailLayer[i]
        }
        repairOutline(out)
    }

    // ------------------------------------------------------------------ the stretch bands

    private fun setUp(frame: RigFrame, morph: RigMorph) {
        val sBody = if (frame.stretchBody) quantise(morph.body) else 1.0
        val sLeg = if (frame.stretchLegs) quantise(morph.leg) else 1.0
        legFrom = 0
        legTo = 0
        legRows = 0
        torsoFrom = 0
        torsoTo = 0
        torsoCols = 0
        if (frame.legFrom >= 0 && sLeg != 1.0) {
            legFrom = frame.legFrom
            legTo = frame.legTo
            legRows = max(1, jsRound((legTo - legFrom) * sLeg))
        }
        if (frame.torsoFrom >= 0 && sBody != 1.0) {
            torsoFrom = frame.torsoFrom
            torsoTo = frame.torsoTo
            torsoCols = max(1, jsRound((torsoTo - torsoFrom) * sBody))
        }
        shiftY = if (legRows != 0) legRows - (legTo - legFrom) else 0
        shiftX = if (torsoCols != 0) torsoCols - (torsoTo - torsoFrom) else 0
    }

    /** Where a frame column [x] lands after the torso stretch. */
    private fun mapX(x: Double): Double =
        if (torsoCols == 0 || x < torsoFrom) x
        else if (x < torsoTo) torsoFrom + (x - torsoFrom) * torsoCols / (torsoTo - torsoFrom)
        else x + shiftX

    /** Where a frame row [y] lands after the leg stretch. */
    private fun mapY(y: Double): Double =
        if (legRows == 0 || y < legFrom) y - shiftY
        else if (y < legTo) legFrom - shiftY + (y - legFrom) * legRows / (legTo - legFrom) else y

    /** The body layer: every pixel that is not a part, moved by the stretch. */
    private fun splitBody(src: IntArray, frame: RigFrame) {
        val w = frameWidth
        val h = frameHeight
        part.fill(0)
        for (i in frame.ears.indices) for (p in frame.ears[i].px) part[p] = (1 + i).toByte()
        frame.tail?.let { for (p in it.px) part[p] = TAIL_PART }
        for (k in frame.whiskers) for (p in k.px) part[p] = WHISKER_PART
        edge.fill(0)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (part[i].toInt() != 0 || src[i] ushr 24 == 0) continue
                if (
                    (x > 0 && part[i - 1].toInt() != 0) ||
                        (x < w - 1 && part[i + 1].toInt() != 0) ||
                        (y > 0 && part[i - w].toInt() != 0) ||
                        (y < h - 1 && part[i + w].toInt() != 0)
                ) {
                    edge[i] = 1
                }
            }
        }
        val nCols = w + shiftX
        val nRows = h + shiftY
        for (c in 0 until nCols) {
            cols[c] =
                if (torsoCols == 0 || c < torsoFrom) c
                else if (c < torsoFrom + torsoCols)
                    torsoFrom +
                        floor((c - torsoFrom).toDouble() * (torsoTo - torsoFrom) / torsoCols)
                            .toInt()
                else c - shiftX
        }
        for (r in 0 until nRows) {
            rows[r] =
                if (legRows == 0 || r < legFrom) r
                else if (r < legFrom + legRows)
                    legFrom + floor((r - legFrom).toDouble() * (legTo - legFrom) / legRows).toInt()
                else r - shiftY
        }
        body.fill(0)
        bodyEdge.fill(0)
        for (r in 0 until nRows) {
            val oy = r - shiftY + MARGIN_TOP
            if (oy < 0 || oy >= canvasHeight) continue
            for (c in 0 until nCols) {
                val i = rows[r] * w + cols[c]
                if (part[i].toInt() != 0) continue
                val v = src[i]
                if (v ushr 24 == 0) continue
                val o = oy * canvasWidth + c + MARGIN_LEFT
                body[o] = v
                if (edge[i].toInt() != 0) bodyEdge[o] = 1
            }
        }
    }

    // ------------------------------------------------------------------ painting parts

    private fun put(x: Int, y: Int, v: Int) {
        if (x >= 0 && x < canvasWidth && y >= 0 && y < canvasHeight) target[y * canvasWidth + x] = v
    }

    private fun paintTail(src: IntArray, tail: RigTail, scale: Double, pose: RigPose) {
        val w = frameWidth
        val rad = pose.tail * PI / 180.0
        val curl = pose.tailCurl * PI / 180.0
        val tailCos = Trig.cosDeg(pose.tail)
        val tailSin = Trig.sinDeg(pose.tail)
        val a0x = tail.x[0]
        val a0y = tail.y[0]
        val ca = tailCos
        val sa = tailSin
        val sx = mapX(a0x) - a0x
        val sy = mapY(a0y) - a0y
        if (scale == 1.0 && rad == 0.0 && curl == 0.0) {
            // an untouched tail: the original pixels, no resampling
            for (p in tail.px) {
                put(
                    jsRound(p % w + sx) + MARGIN_LEFT,
                    jsRound((p / w).toDouble() + sy) + MARGIN_TOP,
                    src[p],
                )
            }
            return
        }
        val n = tail.count
        require(n <= MAX_TAIL_VERTICES) { "a tail of $n vertices" }
        if (curl == 0.0) {
            for (i in 0 until n) {
                val dx = (tail.x[i] - a0x) * scale
                val dy = (tail.y[i] - a0y) * scale
                axisX[i] = a0x + dx * ca - dy * sa
                axisY[i] = a0y + dx * sa + dy * ca
            }
        } else {
            // each segment turned by the root's angle plus a share of the curl that grows with the
            // distance from the root, and laid end to end
            axisX[0] = a0x
            axisY[0] = a0y
            for (i in 0 until n - 1) {
                val th = rad + curl * (tail.cum[i] / tail.cum[n - 1].coerceAtLeast(MIN_LENGTH))
                val c = kotlin.math.cos(th) // native's own bend: nothing in the web to match
                val s = kotlin.math.sin(th)
                val dx = (tail.x[i + 1] - tail.x[i]) * scale
                val dy = (tail.y[i + 1] - tail.y[i]) * scale
                axisX[i + 1] = axisX[i] + dx * c - dy * s
                axisY[i + 1] = axisY[i] + dx * s + dy * c
            }
        }
        val total = tail.cum[n - 1] * scale
        for (i in 0 until n - 1) {
            val dx = axisX[i + 1] - axisX[i]
            val dy = axisY[i + 1] - axisY[i]
            val l = nonZero(hypot(dx, dy))
            segLen[i] = l
            segDirX[i] = dx / l
            segDirY[i] = dy / l
        }
        var x0 = 1e9
        var x1 = -1e9
        var y0 = 1e9
        var y1 = -1e9
        for (i in 0 until n) {
            x0 = min(x0, axisX[i])
            x1 = max(x1, axisX[i])
            y0 = min(y0, axisY[i])
            y1 = max(y1, axisY[i])
        }
        for (i in 0 until n - 1) {
            val dx = tail.x[i + 1] - tail.x[i]
            val dy = tail.y[i + 1] - tail.y[i]
            val l = nonZero(hypot(dx, dy))
            srcDirX[i] = dx / l
            srcDirY[i] = dy / l
            srcLen[i] = l
        }
        val reach = TAIL_REACH
        for (y in floor(y0 - reach).toInt()..ceil(y1 + reach).toInt()) {
            for (x in floor(x0 - reach).toInt()..ceil(x1 + reach).toInt()) {
                val qx = x + 0.5
                val qy = y + 0.5
                var best = 1e9
                var bi = 0
                var bl = 0.0
                for (i in 0 until n - 1) {
                    val vx = qx - axisX[i]
                    val vy = qy - axisY[i]
                    val l = vx * segDirX[i] + vy * segDirY[i]
                    val t = max(0.0, min(segLen[i], l))
                    val dx = vx - segDirX[i] * t
                    val dy = vy - segDirY[i] * t
                    val d = dx * dx + dy * dy
                    if (d < best) {
                        best = d
                        bi = i
                        bl = t
                    }
                }
                if (best > reach * reach) continue
                val dx = qx - axisX[bi]
                val dy = qy - axisY[bi]
                val side = segDirX[bi] * dy - segDirY[bi] * dx
                var along = 0.0
                for (i in 0 until bi) along += segLen[i]
                along += bl
                if (along < -0.01 || along > total + 0.01) continue
                val ts = min(tail.cum[n - 1], along / scale)
                var j = 0
                while (j < n - 2 && tail.cum[j + 1] <= ts) j++
                val u = (ts - tail.cum[j]) / nonZero(srcLen[j])
                val px = tail.x[j] + srcDirX[j] * u * srcLen[j] + (-srcDirY[j]) * side
                val py = tail.y[j] + srcDirY[j] * u * srcLen[j] + srcDirX[j] * side
                val ix = floor(px).toInt()
                val iy = floor(py).toInt()
                if (ix < 0 || ix >= frameWidth || iy < 0 || iy >= frameHeight) continue
                val si = iy * w + ix
                if (part[si] == TAIL_PART && src[si] ushr 24 != 0) {
                    put(jsRound(x + sx) + MARGIN_LEFT, jsRound(y + sy) + MARGIN_TOP, src[si])
                }
            }
        }
    }

    private fun paintEars(src: IntArray, frame: RigFrame, morph: RigMorph, pose: RigPose) {
        val w = frameWidth
        val earWidth = quantise(morph.earWidth)
        val earLength = quantise(morph.ear)
        // the visible length: the genes run from tiny to enormous, the drawing is eased so
        // neither end turns into a stub or a spike
        val eased =
            if (earLength >= 1.0) 1 + (earLength - 1) * EASE_LONG
            else 1 - (1 - earLength) * EASE_SHORT
        val flat = pose.flat.coerceIn(0.0, 1.0)
        val flatLength = max(FLAT_MIN_LENGTH, eased * FLAT_LENGTH)
        val length =
            if (flat <= 0.0) eased
            else if (flat >= 1.0) flatLength else eased + (flatLength - eased) * flat
        val widen =
            if (flat <= 0.0) 1.0 else if (flat >= 1.0) FLAT_WIDTH else 1 + (FLAT_WIDTH - 1) * flat
        val width = earWidth * widthFor(earLength, eased) * widen
        for (k in frame.ears.indices) {
            val e = frame.ears[k]
            val bx = e.baseX
            val by = e.baseY
            val ux = e.tipX - bx
            val uy = e.tipY - by
            val l = nonZero(hypot(ux, uy))
            val ax = ux / l
            val ay = uy / l
            val nx = -ay
            val ny = ax
            val degrees = if (k == 0) pose.earLeft else pose.earRight
            val turn = degrees * PI / 180.0
            val ca = Trig.cosDeg(degrees)
            val sa = Trig.sinDeg(degrees)
            val tx = ax * ca - ay * sa
            val ty = ax * sa + ay * ca
            val mx = -ty
            val my = tx
            val sx = mapX(bx) - bx
            val sy = mapY(by) - by
            if (length == 1.0 && width == 1.0 && turn == 0.0) {
                for (p in e.px) {
                    put(
                        jsRound(p % w + sx) + MARGIN_LEFT,
                        jsRound((p / w).toDouble() + sy) + MARGIN_TOP,
                        src[p],
                    )
                }
                continue
            }
            var x0 = 1e9
            var x1 = -1e9
            var y0 = 1e9
            var y1 = -1e9
            for (p in e.px) {
                val x = p % w + 0.5
                val y = (p / w) + 0.5
                val t = (x - bx) * ax + (y - by) * ay
                val s = (x - bx) * nx + (y - by) * ny
                val fx = bx + t * length * tx + s * width * mx
                val fy = by + t * length * ty + s * width * my
                x0 = min(x0, fx)
                x1 = max(x1, fx)
                y0 = min(y0, fy)
                y1 = max(y1, fy)
            }
            val pad = ceil(max(length, width)).toInt() + 1
            for (y in floor(y0 - pad).toInt()..ceil(y1 + pad).toInt()) {
                for (x in floor(x0 - pad).toInt()..ceil(x1 + pad).toInt()) {
                    val dx = x + 0.5 - bx
                    val dy = y + 0.5 - by
                    val t = (dx * tx + dy * ty) / length
                    val s = (dx * mx + dy * my) / width
                    val px = bx + t * ax + s * nx
                    val py = by + t * ay + s * ny
                    val ix = floor(px).toInt()
                    val iy = floor(py).toInt()
                    if (ix < 0 || ix >= frameWidth || iy < 0 || iy >= frameHeight) continue
                    val si = iy * w + ix
                    if (part[si].toInt() == 1 + k && src[si] ushr 24 != 0) {
                        put(jsRound(x + sx) + MARGIN_LEFT, jsRound(y + sy) + MARGIN_TOP, src[si])
                    }
                }
            }
        }
    }

    /** The ear width that goes with an eased length; the same cat asks again every frame. */
    private fun widthFor(earLength: Double, eased: Double): Double {
        val i = Math.round(earLength / EAR_STEP).toInt()
        if (i < 0 || i >= widths.size) return StrictMath.pow(eased, WIDTH_FOLLOWS_LENGTH)
        if (widths[i].isNaN()) widths[i] = StrictMath.pow(eased, WIDTH_FOLLOWS_LENGTH)
        return widths[i]
    }

    /** The widths for each ear length on the quantiser's grid, made when first needed. */
    private val widths = DoubleArray(WIDTH_TABLE) { Double.NaN }

    /** Whiskers: pixels kept in order of distance from the root; longer ones get extra pixels. */
    private fun paintWhiskers(src: IntArray, frame: RigFrame, scale: Double) {
        val w = frameWidth
        for (k in frame.whiskers) {
            val rx = k.baseX
            val ry = k.baseY
            val dx = k.tipX - rx
            val dy = k.tipY - ry
            val l = nonZero(hypot(dx, dy))
            val ux = dx / l
            val uy = dy / l
            val sx = mapX(rx) - rx
            val sy = mapY(ry) - ry
            var last = 0
            for (p in k.px) {
                val x = p % w + 0.5
                val y = (p / w) + 0.5
                val d = hypot(x - rx, y - ry)
                if (scale < 1 && d > l * scale + 0.5) continue
                put(jsRound(x - 0.5 + sx) + MARGIN_LEFT, jsRound(y - 0.5 + sy) + MARGIN_TOP, src[p])
                if (last == 0 || d > hypot(last % w + 0.5 - rx, (last / w) + 0.5 - ry)) last = p
            }
            if (scale > 1 && last != 0) {
                val colour = src[last]
                val extra = jsRound(l * (scale - 1))
                val lx = last % w + 0.5
                val ly = (last / w) + 0.5
                for (s in 1..extra) {
                    put(
                        jsRound(lx - 0.5 + ux * s + sx) + MARGIN_LEFT,
                        jsRound(ly - 0.5 + uy * s + sy) + MARGIN_TOP,
                        colour,
                    )
                }
            }
        }
    }

    /** The outline where a part was cut away and nothing now covers the cut. */
    private fun repairOutline(res: IntArray) {
        val cw = canvasWidth
        val ch = canvasHeight
        for (y in 0 until ch) {
            for (x in 0 until cw) {
                val i = y * cw + x
                if (
                    bodyEdge[i].toInt() == 0 ||
                        res[i] ushr 24 == 0 ||
                        res[i] != body[i] ||
                        topLayer[i] ushr 24 != 0
                ) {
                    continue
                }
                if (
                    x == 0 ||
                        y == 0 ||
                        x == cw - 1 ||
                        y == ch - 1 ||
                        res[i - 1] ushr 24 == 0 ||
                        res[i + 1] ushr 24 == 0 ||
                        res[i - cw] ushr 24 == 0 ||
                        res[i + cw] ushr 24 == 0
                ) {
                    res[i] = OUTLINE
                }
            }
        }
    }

    companion object {
        /** Margins around the frame in the composed picture: room for long ears and a long tail. */
        const val MARGIN_LEFT = 76
        const val MARGIN_RIGHT = 76
        const val MARGIN_TOP = 44
        const val MARGIN_BOTTOM = 8

        /** Palette slot 10, the outline colour, as ARGB: the colour the repair draws. */
        const val OUTLINE = 0xFF1A101C.toInt()

        /** A bobtail's tail is at most this long (the page's `morph.bob ? min(tl, 0.3)`). */
        const val BOB_TAIL = 0.3

        private const val TAIL_PART: Byte = 3
        private const val WHISKER_PART: Byte = 4
        private const val MAX_TAIL_VERTICES = 64
        private const val MIN_LENGTH = 1e-9

        /** How far from the tail's centre line a pixel can lie and still belong to the tail. */
        private const val TAIL_REACH = 7.0
        private const val EASE_LONG = 0.55
        private const val EASE_SHORT = 0.6
        private const val FLAT_LENGTH = 0.7
        private const val FLAT_MIN_LENGTH = 0.55
        private const val FLAT_WIDTH = 1.3
        private const val WIDTH_FOLLOWS_LENGTH = 0.6
        private const val EAR_STEP = 0.05
        private const val WIDTH_TABLE = 64

        /** JavaScript's `Math.round`: halves go up. */
        internal fun jsRound(v: Double): Int = floor(v + 0.5).toInt()

        /** The page's `petRigQ`: a multiplier rounded to 0.05 (0 counts as 1). */
        fun quantise(v: Double): Double =
            floor((if (v == 0.0 || v.isNaN()) 1.0 else v) * 20 + 0.5) / 20

        private fun nonZero(v: Double) = if (v == 0.0) 1.0 else v
    }
}

/**
 * Sine and cosine of an angle in degrees, exactly as the page's engine computes them (the
 * `StrictMath` functions are the same fdlibm), remembered for angles that are a whole number of
 * tenths of a degree: the grid's angles and the page's all are, and asking again then costs a
 * lookup instead of the library call (which on a desktop JVM allocates a small array each time).
 */
internal object Trig {
    private const val TENTHS = 10
    private const val LIMIT = 180 * TENTHS
    private val cosines = DoubleArray(2 * LIMIT + 1)
    private val sines = DoubleArray(2 * LIMIT + 1)
    private val known = BooleanArray(2 * LIMIT + 1)

    fun cosDeg(degrees: Double): Double {
        if (degrees == 0.0) return 1.0
        val i = slot(degrees)
        if (i < 0) return StrictMath.cos(degrees * PI / 180.0)
        fill(i, degrees)
        return cosines[i]
    }

    fun sinDeg(degrees: Double): Double {
        if (degrees == 0.0) return 0.0
        val i = slot(degrees)
        if (i < 0) return StrictMath.sin(degrees * PI / 180.0)
        fill(i, degrees)
        return sines[i]
    }

    private fun slot(degrees: Double): Int {
        val t = degrees * TENTHS
        val r = Math.rint(t)
        if (r != t || degrees != r / TENTHS || r < -LIMIT || r > LIMIT) return -1
        return r.toInt() + LIMIT
    }

    private fun fill(i: Int, degrees: Double) {
        if (known[i]) return
        val radians = degrees * PI / 180.0
        cosines[i] = StrictMath.cos(radians)
        sines[i] = StrictMath.sin(radians)
        known[i] = true
    }
}
