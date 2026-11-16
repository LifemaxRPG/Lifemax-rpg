package com.lifemax.rpg.feature.habits.domain

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The people who walk the pavement, built for a close-up: the same skeleton as the window figures ([FigBuilder]
 * and [Gait] agree on the legs, so the feet land where the gait says), dressed so that the silhouette reads at a
 * hand's width on the phone: a coat with a hem, trousers or a skirt, shoes with a heel and a toe, hair, a bag, a
 * neck, hands that are hands. Limbs bow a little at the thigh and the calf; the far arm and leg are darker than
 * the near ones, so the figure has depth. What hangs loose (the hem, a ponytail, a bag) trails the motion by a
 * moment ([StreetGait.trail]). Pure data and arithmetic; the sprite bakery turns the shapes into pixels once.
 *
 * Space is the street's: units, x forward (the figure faces +x), y down, the feet on y = 0.
 */

/** What a street walker wears on top. */
enum class Coat {
    /** A tee or a jumper: the torso's outline only. */
    NONE,
    /** A short jacket, its hem over the hips. */
    JACKET,
    /** A long coat, its hem at the knees, swinging. */
    LONG,
}

/** What a street walker carries. */
enum class Bag {
    NONE,
    SHOULDER,
    BACKPACK,
    HAND,
}

/** A street walker's hair (or hat). */
enum class StreetHair {
    CROP,
    PONY,
    BUN,
    LONG,
    BEANIE,
    CAP,
}

/** One street walker's look: build, clothes, hair and bag. */
class StreetLook(
    val height: Float,
    val build: Float,
    val head: Float,
    val coat: Coat,
    val hair: StreetHair,
    val bag: Bag,
    val skirt: Boolean,
)

object StreetLooks {
    val ALL =
        arrayOf(
            StreetLook(1.04f, .92f, .98f, Coat.LONG, StreetHair.CROP, Bag.SHOULDER, false),
            StreetLook(.97f, 1.12f, 1.02f, Coat.JACKET, StreetHair.BEANIE, Bag.BACKPACK, false),
            StreetLook(.95f, .9f, 1.04f, Coat.JACKET, StreetHair.PONY, Bag.HAND, true),
            StreetLook(1.02f, .96f, 1f, Coat.NONE, StreetHair.CAP, Bag.NONE, false),
        )

    val COUNT: Int
        get() = ALL.size
}

/**
 * Where a baked walker's parts are, for what the painter hangs on them: the feet (x along the walk,
 * [footNH] and [footFH] their height over the pavement), the near hand (for a dog's lead) and the
 * head's top.
 */
class WalkerJoints {
    var footNX = 0f
    var footNH = 0f
    var footFX = 0f
    var footFH = 0f
    var handNX = 0f
    var handNY = 0f
    var top = 0f
}

/** The shapes of one street walker in one pose, with where its joints are. */
class StreetFigure(val shapes: List<Shape>, val joints: WalkerJoints)

object StreetRig {
    /** The tones of the parts (0 the deepest brown, 1 the lightest). */
    const val SKIN = .98f
    const val HAIR = .3f
    const val COAT = .6f
    const val TEE = .8f
    const val TROUSER = .72f
    const val SKIRT = .66f
    const val SHOE = .1f
    const val BAG = .45f

    /** The far side of the body is this much of the near side's tone. */
    const val FAR = .5f

    /** The legs, as [Gait] has them (units at height 1). */
    private const val THIGH = 5.9f
    private const val SHIN = 5.7f

    /** The figure of [look] at [phi] of its walk. */
    fun figure(look: Int, phi: Float): StreetFigure =
        build(StreetLooks.ALL[look], StreetGait.pose(phi, look), phi)

    /** The figure of [look] in the pose [p], with what hangs loose as at [phi] of a walk. */
    fun figure(look: Int, p: FloatArray, phi: Float): StreetFigure =
        build(StreetLooks.ALL[look], p, phi)

    /** Frames baked after the walk's: the settle from the passing pose to standing still. */
    const val SETTLE_FRAMES = 4

    /** Standing still, side on, the arms hanging, the weight on both feet. */
    val STAND: FloatArray =
        pose(
            turn = .94f,
            lean = 1.2f,
            head = -1f,
            an = 3f,
            en = 9f,
            af = -3f,
            ef = 8f,
            ln = 1.5f,
            kn = 3f,
            lf = -1.5f,
            kf = 3f,
        )

    /** The settle's poses: from the passing pose, eased, to [STAND] (the last one). */
    val SETTLE: List<FloatArray> by lazy { settle(-1) }

    /** [SETTLE] from look [look]'s own passing pose. */
    fun settle(look: Int): List<FloatArray> =
        Gait.between(StreetGait.pose(Gait.PASSING, look), STAND, SETTLE_FRAMES)

    private val SETTLES: Array<List<FloatArray>?> = arrayOfNulls(StreetLooks.COUNT)

    private fun settleOf(look: Int): List<FloatArray> =
        SETTLES[look] ?: settle(look).also { SETTLES[look] = it }

    /** Frame [frame] of [look]: the walk's [StreetGait.FRAMES], then the settle's. */
    fun frame(look: Int, frame: Int): StreetFigure =
        if (frame < StreetGait.FRAMES) figure(look, frame / StreetGait.FRAMES.toFloat())
        else figure(look, settleOf(look)[frame - StreetGait.FRAMES], Gait.PASSING)

    /** Every frame a look has. */
    const val TOTAL_FRAMES = StreetGait.FRAMES + SETTLE_FRAMES

    /**
     * How tall [look] stands, in units, at the rig's size: from the pavement to the top of the
     * head.
     */
    fun height(look: Int): Float = -figure(look, Gait.PASSING).joints.top

    private fun rad(d: Float) = d * (PI.toFloat() / 180f)

    private class Out {
        val shapes = ArrayList<Shape>()

        fun add(pts: FloatArray, tone: Float) {
            shapes += Shape(Shape.BODY, pts, tone)
        }
    }

    private fun build(look: StreetLook, p: FloatArray, phi: Float): StreetFigure {
        val o = Out()
        val j = WalkerJoints()
        val f = phi - kotlin.math.floor(phi)
        val hgt = look.height
        val b = look.build
        val hs = look.head
        val torsoL = 7.4f * hgt
        val neck = 1.0f * hgt
        val headRy = 1.65f * hgt * hs
        val headRx = 1.35f * hgt * hs
        val ua = 4.1f * hgt
        val fa = 3.7f * hgt
        val th = THIGH * hgt
        val sh = SHIN * hgt
        val turn = p[K.TURN]
        val lean = p[K.LEAN]
        val coat = look.coat
        val thick = if (coat != Coat.NONE) 1.12f else 1f
        val trail = StreetGait.trail(f)
        val trail2 = StreetGait.trail2(f)

        val hw = (FigBuilder.lerp(1.5f, .4f, turn) + p[K.TWIST]) * b
        val px = p[K.X]
        val py = 0f
        val ux = sin(rad(lean))
        val uy = -cos(rad(lean))
        val nx = cos(rad(lean))
        val ny = sin(rad(lean))
        val sx = px + ux * torsoL
        val sy = py + uy * torsoL
        val sw = (FigBuilder.lerp(2.9f, .5f, turn) + p[K.SHX]) * b

        // ---- the far arm, the far leg, then the near leg (a coat is drawn over the thighs after)
        val farTone = FAR
        arm(
            o,
            j,
            sx - nx * sw,
            sy - ny * sw,
            ua,
            fa,
            b * thick,
            p[K.AF],
            p[K.EF],
            farTone,
            false,
            look,
        )
        leg(o, j, px - hw, py, th, sh, b, hgt, p[K.LF], p[K.KF], p[K.ANF], farTone, false, look)
        if (look.bag == Bag.BACKPACK) backpack(o, px, py, sx, sy, nx, ny, ux, uy, b, hgt, trail2)
        leg(o, j, px + hw, py, th, sh, b, hgt, p[K.LN], p[K.KN], p[K.ANN], 1f, true, look)

        // ---- the skirt over the thighs
        if (look.skirt) skirt(o, px, py, hw, th, hgt, b, p, trail)

        // ---- the torso (and the coat's skirts)
        val shoulderW = FigBuilder.lerp(3.3f, 1.9f, turn) * b * thick
        val chestW = FigBuilder.lerp(3.0f, 2.1f, turn) * b * thick
        val waistW = FigBuilder.lerp(2.4f, 1.8f, turn) * b * (if (coat == Coat.NONE) 1f else 1.06f)
        val hipW = FigBuilder.lerp(2.8f, 2.1f, turn) * b
        val widths = floatArrayOf(hipW, waistW, chestW, shoulderW * .96f)
        val at = floatArrayOf(0f, .36f, .74f, .95f)
        val right = ArrayList<Float>()
        val left = ArrayList<Float>()
        for (i in 0 until 4) {
            val cx = px + ux * torsoL * at[i]
            val cy = py + uy * torsoL * at[i]
            right += cx + nx * widths[i]
            right += cy + ny * widths[i]
            left += cx - nx * widths[i]
            left += cy - ny * widths[i]
        }
        val topx = sx + ux * .75f
        val topy = sy + uy * .75f
        right += topx + nx * shoulderW * .42f
        right += topy + ny * shoulderW * .42f
        left += topx - nx * shoulderW * .42f
        left += topy - ny * shoulderW * .42f
        val outline = ArrayList<Float>(right)
        var k = left.size - 2
        while (k >= 0) {
            outline += left[k]
            outline += left[k + 1]
            k -= 2
        }
        val torsoTone = if (coat == Coat.NONE) TEE else COAT
        o.add(FigBuilder.chaikin(outline.toFloatArray(), 2), torsoTone)
        // the pelvis, where the legs join (trousers or skirt)
        o.add(
            FigBuilder.ellipse(px, py + .2f, hipW * .98f, 1.9f * hgt, lean, 10),
            if (look.skirt) SKIRT else if (coat == Coat.LONG) COAT else TROUSER,
        )
        if (coat != Coat.NONE) {
            // the coat's skirts: from the waist down over the hips to the hem, the back of the hem
            // trailing the walk, the front pushed by the forward thigh
            val drop = if (coat == Coat.LONG) th * .92f else 2.6f * hgt
            val flare = if (coat == Coat.LONG) 1.1f * b else .35f * b
            val legFwd =
                sin(rad(max(p[K.LN], p[K.LF]))) * th * (if (coat == Coat.LONG) .35f else .1f)
            val back = -trail * (if (coat == Coat.LONG) 1.3f else .4f)
            val hem = FloatArray(12)
            val wx = px + ux * torsoL * .36f
            val wy = py + uy * torsoL * .36f
            hem[0] = wx + nx * waistW
            hem[1] = wy + ny * waistW
            hem[2] = px + nx * (hipW + .4f)
            hem[3] = py + ny * (hipW + .4f)
            hem[4] = px + nx * (hipW + flare) + legFwd + ux * drop
            hem[5] = py + ny * (hipW + flare) + uy * drop + .2f
            hem[6] = px - nx * (hipW + flare) + back + ux * drop
            hem[7] = py - ny * (hipW + flare) + uy * drop + .35f
            hem[8] = px - nx * (hipW + .4f) + back * .3f
            hem[9] = py - ny * (hipW + .4f)
            hem[10] = wx - nx * waistW
            hem[11] = wy - ny * waistW
            o.add(FigBuilder.chaikin(hem, 1), COAT)
            // the lapel line: a sliver of the shirt showing at the chest
            val cx = px + ux * torsoL * .8f
            val cy = py + uy * torsoL * .8f
            o.add(
                floatArrayOf(
                    cx + nx * .25f * b,
                    cy + ny * .25f * b,
                    cx + nx * .9f * b - ux * 2.4f * hgt,
                    cy + ny * .9f * b - uy * 2.4f * hgt,
                    cx - nx * .2f * b - ux * 2.4f * hgt,
                    cy - ny * .2f * b - uy * 2.4f * hgt,
                ),
                TEE,
            )
        }

        // ---- the neck and the head, nodding a little behind the body
        val headA = lean + p[K.HEAD]
        val hux = sin(rad(headA))
        val huy = -cos(rad(headA))
        val hfx = cos(rad(headA))
        val hfy = sin(rad(headA))
        val nkx = sx + sin(rad(lean + p[K.HEAD] * .5f)) * (neck * .55f)
        val nky = sy - cos(rad(lean + p[K.HEAD] * .5f)) * (neck * .55f)
        val drop = StreetGait.headDrop(f) * 24f * hgt
        val hx = nkx + hux * (neck * .6f + headRy * .92f)
        val hy = nky + huy * (neck * .6f + headRy * .92f) + drop
        o.add(FigBuilder.capsule(sx, sy, 1.05f * b, hx, hy + .3f, .9f * b, 6), SKIN)
        // a collar over the neck's root
        if (coat != Coat.NONE)
            o.add(
                FigBuilder.ellipse(sx + ux * .4f, sy + uy * .4f, 1.9f * b, .9f * b, lean, 8),
                COAT,
            )
        o.add(head(hx, hy, headRx, headRy, headA, turn), SKIN)
        hair(o, look.hair, hx, hy, headRx, headRy, hux, huy, hfx, hfy, hgt, trail2)
        j.top = hy - headRy * 1.08f

        // ---- the bag, then the near arm over everything
        when (look.bag) {
            Bag.SHOULDER -> shoulderBag(o, px, py, sx, sy, nx, ny, ux, uy, b, hgt, hipW, trail)
            Bag.BACKPACK -> straps(o, sx, sy, nx, ny, ux, uy, b, hgt, shoulderW)
            else -> {}
        }
        arm(o, j, sx + nx * sw, sy + ny * sw, ua, fa, b * thick, p[K.AN], p[K.EN], 1f, true, look)
        if (look.bag == Bag.HAND) handbag(o, j, hgt, b, trail)

        ground(o, j, p[K.LIFT])
        return StreetFigure(o.shapes, j)
    }

    /** Puts the figure on the pavement: its lowest point goes to y = -lift. */
    private fun ground(o: Out, j: WalkerJoints, lift: Float) {
        var low = -1e9f
        for (s in o.shapes) {
            var i = 1
            while (i < s.pts.size) {
                low = max(low, s.pts[i])
                i += 2
            }
        }
        val dy = -low - lift
        for (s in o.shapes) {
            var i = 1
            while (i < s.pts.size) {
                s.pts[i] += dy
                i += 2
            }
        }
        j.footNH = -(j.footNH + dy)
        j.footFH = -(j.footFH + dy)
        j.handNY += dy
        j.top += dy
    }

    /**
     * A limb from ([x0], [y0]) to ([x1], [y1]), [r0] to [r1] thick, its middle bowed [bow] units to
     * the side (positive: towards +x, the figure's front).
     */
    fun bowed(
        x0: Float,
        y0: Float,
        r0: Float,
        x1: Float,
        y1: Float,
        r1: Float,
        bow: Float,
    ): FloatArray {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = max(1e-3f, sqrt(dx * dx + dy * dy))
        var nx = dy / len
        var ny = -dx / len
        if (nx < 0f) {
            nx = -nx
            ny = -ny
        }
        val n = 6
        val out = FloatArray((n + 1) * 4 + 8)
        var o = 0
        // the round top, then down one side
        val t0 = atan2(dy, dx)
        for (i in 0..3) {
            val a = t0 + PI.toFloat() / 2f + PI.toFloat() * i / 3f
            out[o++] = x0 + cos(a) * r0
            out[o++] = y0 + sin(a) * r0
        }
        for (i in 1 until n) {
            val t = i / n.toFloat()
            val cx = x0 + dx * t + nx * bow * 4f * t * (1f - t)
            val cy = y0 + dy * t + ny * bow * 4f * t * (1f - t)
            val r = r0 + (r1 - r0) * t
            out[o++] = cx - nx * r
            out[o++] = cy - ny * r
        }
        for (i in 0..3) {
            val a = t0 - PI.toFloat() / 2f + PI.toFloat() * i / 3f
            out[o++] = x1 + cos(a) * r1
            out[o++] = y1 + sin(a) * r1
        }
        for (i in n - 1 downTo 1) {
            val t = i / n.toFloat()
            val cx = x0 + dx * t + nx * bow * 4f * t * (1f - t)
            val cy = y0 + dy * t + ny * bow * 4f * t * (1f - t)
            val r = r0 + (r1 - r0) * t
            out[o++] = cx + nx * r
            out[o++] = cy + ny * r
        }
        return out.copyOf(o)
    }

    private fun leg(
        o: Out,
        j: WalkerJoints,
        hx: Float,
        hy: Float,
        th: Float,
        sh: Float,
        b: Float,
        hgt: Float,
        l: Float,
        kn: Float,
        ankle: Float,
        shade: Float,
        near: Boolean,
        look: StreetLook,
    ) {
        val tx = hx + sin(rad(l)) * th
        val ty = hy + cos(rad(l)) * th
        val sa = l - kn
        val ax = tx + sin(rad(sa)) * sh
        val ay = ty + cos(rad(sa)) * sh
        val trousers = !look.skirt
        val legTone = (if (trousers) TROUSER else SKIN) * shade
        val tr = if (trousers) 1.18f else 1f
        // the thigh fuller at the front, the calf fuller at the back
        o.add(bowed(hx, hy, 1.85f * b * tr, tx, ty, 1.25f * b * tr, .28f * hgt), legTone)
        o.add(bowed(tx, ty, 1.2f * b * tr, ax, ay, .78f * b * tr, -.3f * hgt), legTone)
        // the shoe: heel, sole, toe, instep, in the foot's own frame (forward along the sole,
        // down through the sole), turned by the ankle
        val fx = sin(rad(sa + 90f + ankle))
        val fy = cos(rad(sa + 90f + ankle))
        val dx = sin(rad(sa + ankle))
        val dy = cos(rad(sa + ankle))
        val L = hgt
        val shoe =
            floatArrayOf(
                -.55f,
                -.55f, // the ankle's back
                -.95f,
                .05f, // the heel's back, top
                -1.0f,
                .95f, // the heel, on the ground
                .6f,
                1.0f, // the sole
                2.1f,
                .98f, // the ball
                2.55f,
                .72f, // the toe's tip
                2.35f,
                .25f, // the toe's top
                1.2f,
                -.05f, // the instep
                .45f,
                -.5f, // the ankle's front
            )
        val pts = FloatArray(shoe.size)
        var i = 0
        while (i < shoe.size) {
            val sx = shoe[i] * L
            val sy = shoe[i + 1] * (.85f * b)
            pts[i] = ax + fx * sx + dx * sy
            pts[i + 1] = ay + fy * sx + dy * sy
            i += 2
        }
        o.add(FigBuilder.chaikin(pts, 1), if (near) SHOE else SHOE * FAR)
        // a trouser cuff (or the ankle) over the shoe's top
        if (trousers)
            o.add(FigBuilder.ellipse(ax, ay - .1f, .95f * b * tr, .7f * b, sa, 7), legTone)
        if (near) {
            j.footNX = ax + fx * .8f * L
            j.footNH = ay + dy * .95f * .85f * b
        } else {
            j.footFX = ax + fx * .8f * L
            j.footFH = ay + dy * .95f * .85f * b
        }
    }

    private fun arm(
        o: Out,
        j: WalkerJoints,
        sx: Float,
        sy: Float,
        ua: Float,
        fa: Float,
        b: Float,
        a: Float,
        e: Float,
        shade: Float,
        near: Boolean,
        look: StreetLook,
    ) {
        val ex = sx + sin(rad(a)) * ua
        val ey = sy + cos(rad(a)) * ua
        val fa0 = a + e
        val wx = ex + sin(rad(fa0)) * fa
        val wy = ey + cos(rad(fa0)) * fa
        val sleeve = if (look.coat == Coat.NONE) TEE else COAT
        // the upper arm bows a little outward, the forearm a little in
        o.add(bowed(sx, sy, 1.1f * b, ex, ey, .82f * b, 0f), sleeve * shade)
        o.add(bowed(ex, ey, .82f * b, wx, wy, .55f * b, .08f), sleeve * shade)
        // the hand: a relaxed hand hangs curled a little forward of the wrist, the thumb a small
        // lobe on its front
        val ha = fa0 + 16f
        val hx = wx + sin(rad(ha)) * .75f
        val hy = wy + cos(rad(ha)) * .75f
        val hand = FigBuilder.ellipse(hx, hy, .5f * b, .85f * b, ha, 8)
        val thumb =
            FigBuilder.ellipse(
                hx + cos(rad(ha)) * .45f * b - sin(rad(ha)) * .1f,
                hy - sin(rad(ha)) * .45f * b - cos(rad(ha)) * .1f,
                .22f * b,
                .42f * b,
                ha - 25f,
                6,
            )
        o.add(hand, SKIN * shade)
        o.add(thumb, SKIN * shade)
        if (near) {
            j.handNX = hx + sin(rad(ha)) * .9f * b
            j.handNY = hy + cos(rad(ha)) * .9f * b
        }
    }

    private fun skirt(
        o: Out,
        px: Float,
        py: Float,
        hw: Float,
        th: Float,
        hgt: Float,
        b: Float,
        p: FloatArray,
        trail: Float,
    ) {
        val ln = p[K.LN]
        val lf = p[K.LF]
        val hem = th * .8f
        val ax = px + sin(rad(ln)) * hem
        val ay = py + cos(rad(ln)) * hem
        val bx = px + sin(rad(lf)) * hem
        val by = py + cos(rad(lf)) * hem
        val hipW = FigBuilder.lerp(2.8f, 2.1f, p[K.TURN]) * b
        val w = hipW * 1.15f
        val back = -trail * .7f
        o.add(
            FigBuilder.hull(
                floatArrayOf(
                    px - hipW,
                    py - .6f,
                    px + hipW,
                    py - .6f,
                    ax + w,
                    ay + .2f,
                    ax - w * .5f + back,
                    ay + .5f,
                    bx + w * .5f,
                    by + .2f,
                    bx - w + back,
                    by + .5f,
                )
            ),
            SKIRT,
        )
    }

    private fun head(
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        ang: Float,
        turn: Float,
    ): FloatArray {
        val n = 18
        val out = FloatArray(n * 2 + 6)
        val ca = cos(rad(ang))
        val sa = sin(rad(ang))
        for (i in 0 until n) {
            val t = (i / n.toFloat()) * 2f * PI.toFloat()
            val down = max(0f, sin(t))
            val lx = cos(t) * rx * (1f - .22f * down)
            val ly = sin(t) * ry * (1f + .04f * down)
            out[i * 2] = cx + lx * ca - ly * sa
            out[i * 2 + 1] = cy + lx * sa + ly * ca
        }
        // the brow, the nose and the chin in profile
        val lx = rx * (.98f + .3f * turn)
        val ly = ry * .08f
        out[n * 2] = cx + (rx * .92f) * ca - (-ry * .3f) * sa
        out[n * 2 + 1] = cy + (rx * .92f) * sa + (-ry * .3f) * ca
        out[n * 2 + 2] = cx + lx * ca - ly * sa
        out[n * 2 + 3] = cy + lx * sa + ly * ca
        out[n * 2 + 4] = cx + (rx * .85f) * ca - (ry * .45f) * sa
        out[n * 2 + 5] = cy + (rx * .85f) * sa + (ry * .45f) * ca
        return FigBuilder.hull(out)
    }

    private fun hair(
        o: Out,
        h: StreetHair,
        hx: Float,
        hy: Float,
        rx: Float,
        ry: Float,
        ux: Float,
        uy: Float,
        fx: Float,
        fy: Float,
        hgt: Float,
        trail2: Float,
    ) {
        val ang = atan2(ux, -uy) * 180f / PI.toFloat()
        when (h) {
            StreetHair.CROP ->
                o.add(
                    FigBuilder.ellipse(
                        hx + ux * ry * .3f - fx * rx * .22f,
                        hy + uy * ry * .3f - fy * rx * .22f,
                        rx * 1.1f,
                        ry * .82f,
                        ang,
                        14,
                    ),
                    HAIR,
                )
            StreetHair.BUN -> {
                o.add(
                    FigBuilder.ellipse(
                        hx + ux * ry * .27f - fx * rx * .2f,
                        hy + uy * ry * .27f - fy * rx * .2f,
                        rx * 1.08f,
                        ry * .8f,
                        ang,
                        14,
                    ),
                    HAIR,
                )
                o.add(
                    FigBuilder.ellipse(
                        hx + ux * ry * .95f - fx * rx * .75f,
                        hy + uy * ry * .95f - fy * rx * .75f,
                        1.0f * hgt,
                        .95f * hgt,
                        ang,
                        10,
                    ),
                    HAIR,
                )
            }
            StreetHair.PONY -> {
                o.add(
                    FigBuilder.ellipse(
                        hx + ux * ry * .3f - fx * rx * .25f,
                        hy + uy * ry * .3f - fy * rx * .25f,
                        rx * 1.1f,
                        ry * .84f,
                        ang,
                        14,
                    ),
                    HAIR,
                )
                // the tail swings behind the bob
                val x0 = hx - fx * rx * .95f + ux * ry * .3f
                val y0 = hy - fy * rx * .95f + uy * ry * .3f
                val swing = 14f * trail2
                val a1 = ang + 205f + swing
                val a2 = a1 + 18f + swing * .8f
                val x1 = x0 + sin(rad(a1)) * 2.0f * hgt
                val y1 = y0 - cos(rad(a1)) * 2.0f * hgt
                val x2 = x1 + sin(rad(a2)) * 2.6f * hgt
                val y2 = y1 - cos(rad(a2)) * 2.6f * hgt
                o.add(FigBuilder.capsule(x0, y0, .9f, x1, y1, .85f, 6), HAIR)
                o.add(FigBuilder.capsule(x1, y1, .85f, x2, y2, .3f, 6), HAIR)
            }
            StreetHair.LONG -> {
                o.add(
                    FigBuilder.ellipse(
                        hx + ux * ry * .28f - fx * rx * .25f,
                        hy + uy * ry * .28f - fy * rx * .25f,
                        rx * 1.12f,
                        ry * .86f,
                        ang,
                        14,
                    ),
                    HAIR,
                )
                // the hair down the back, swinging a little behind the walk
                val x0 = hx - fx * rx * .6f
                val y0 = hy - fy * rx * .6f
                val sw = .9f * trail2
                val x1 = x0 - fx * .5f - ux * (ry * 2.2f) + sw
                val y1 = y0 - fy * .5f - uy * (ry * 2.2f)
                o.add(FigBuilder.capsule(x0, y0, 1.3f, x1, y1, 1.1f, 7), HAIR)
            }
            StreetHair.BEANIE -> {
                o.add(
                    FigBuilder.ellipse(
                        hx + ux * ry * .48f,
                        hy + uy * ry * .48f,
                        rx * 1.16f,
                        ry * .7f,
                        ang,
                        14,
                    ),
                    HAIR,
                )
                // the turned-up brim and the bobble
                o.add(
                    FigBuilder.ellipse(
                        hx + ux * ry * .18f,
                        hy + uy * ry * .18f,
                        rx * 1.2f,
                        ry * .28f,
                        ang,
                        12,
                    ),
                    HAIR * 1.4f,
                )
                o.add(
                    FigBuilder.ellipse(
                        hx + ux * ry * 1.15f,
                        hy + uy * ry * 1.15f,
                        .55f,
                        .55f,
                        ang,
                        7,
                    ),
                    HAIR,
                )
            }
            StreetHair.CAP -> {
                o.add(
                    FigBuilder.ellipse(
                        hx + ux * ry * .44f - fx * rx * .08f,
                        hy + uy * ry * .44f - fy * rx * .08f,
                        rx * 1.14f,
                        ry * .64f,
                        ang,
                        14,
                    ),
                    HAIR,
                )
                val bx = hx + ux * ry * .36f + fx * rx * .85f
                val by = hy + uy * ry * .36f + fy * rx * .85f
                o.add(
                    FigBuilder.capsule(bx, by, .45f, bx + fx * 2.1f, by + fy * 2.1f + .25f, .3f, 5),
                    HAIR,
                )
                // the short hair under the cap at the back
                o.add(
                    FigBuilder.ellipse(
                        hx - fx * rx * .7f,
                        hy - fy * rx * .7f + ry * .15f,
                        rx * .55f,
                        ry * .55f,
                        ang,
                        9,
                    ),
                    HAIR,
                )
            }
        }
    }

    private fun shoulderBag(
        o: Out,
        px: Float,
        py: Float,
        sx: Float,
        sy: Float,
        nx: Float,
        ny: Float,
        ux: Float,
        uy: Float,
        b: Float,
        hgt: Float,
        hipW: Float,
        trail: Float,
    ) {
        // the strap over the near shoulder down to the far hip, the bag hanging there and swinging
        val x0 = sx + nx * .9f * b + ux * .3f
        val y0 = sy + ny * .9f * b + uy * .3f
        val bx = px - nx * (hipW + .9f * b) - trail * .5f
        val by = py - ny * (hipW + .9f * b) + 1.2f * hgt
        o.add(FigBuilder.capsule(x0, y0, .5f * b, bx, by - 1.6f * hgt, .42f * b, 5), BAG)
        o.add(box(bx, by, 1.8f * hgt, 1.3f * hgt, -trail * 7f), BAG)
    }

    private fun backpack(
        o: Out,
        px: Float,
        py: Float,
        sx: Float,
        sy: Float,
        nx: Float,
        ny: Float,
        ux: Float,
        uy: Float,
        b: Float,
        hgt: Float,
        trail2: Float,
    ) {
        // the pack on the back, from the shoulders down to the waist, a little behind the torso
        val top = .88f
        val bottom = .34f
        val out = 1.5f * b + .12f * trail2
        val tx = px + ux * (7.4f * hgt * top)
        val ty = py + uy * (7.4f * hgt * top)
        val bx = px + ux * (7.4f * hgt * bottom)
        val by = py + uy * (7.4f * hgt * bottom)
        val w0 = 2.4f * b
        val w1 = 2.1f * b
        o.add(
            FigBuilder.chaikin(
                floatArrayOf(
                    tx - nx * w0,
                    ty - ny * w0,
                    tx - nx * (w0 + out * .8f),
                    ty - ny * (w0 + out * .8f) + .9f,
                    bx - nx * (w1 + out),
                    by - ny * (w1 + out) - .4f,
                    bx - nx * (w1 + out * .5f),
                    by - ny * (w1 + out * .5f) + .4f,
                    bx - nx * w1,
                    by - ny * w1,
                ),
                2,
            ),
            BAG,
        )
    }

    private fun straps(
        o: Out,
        sx: Float,
        sy: Float,
        nx: Float,
        ny: Float,
        ux: Float,
        uy: Float,
        b: Float,
        hgt: Float,
        shoulderW: Float,
    ) {
        val x0 = sx + nx * shoulderW * .55f + ux * .2f
        val y0 = sy + ny * shoulderW * .55f + uy * .2f
        val x1 = x0 + nx * .4f * b - ux * 4.2f * hgt
        val y1 = y0 + ny * .4f * b - uy * 4.2f * hgt
        o.add(FigBuilder.capsule(x0, y0, .55f * b, x1, y1, .5f * b, 5), BAG)
    }

    private fun handbag(o: Out, j: WalkerJoints, hgt: Float, b: Float, trail: Float) {
        // hanging from the near hand's fingers, swinging behind the arm
        val ang = -trail * 9f
        val x0 = j.handNX
        val y0 = j.handNY
        val x1 = x0 + sin(rad(ang)) * 1.6f * hgt
        val y1 = y0 + cos(rad(ang)) * 1.6f * hgt
        o.add(FigBuilder.capsule(x0, y0, .22f, x1, y1 - .2f, .22f, 4), BAG)
        o.add(box(x1, y1 + 1.0f * hgt, 1.5f * hgt, 1.0f * hgt, ang), BAG)
    }

    /** A rounded box [hw] by [hh] about ([cx], [cy]), turned [ang] degrees. */
    private fun box(cx: Float, cy: Float, hw: Float, hh: Float, ang: Float): FloatArray {
        val c = cos(rad(ang))
        val s = sin(rad(ang))
        return FigBuilder.chaikin(
            floatArrayOf(
                cx + (-hw * c - -hh * s),
                cy + (-hw * s + -hh * c),
                cx + (hw * c - -hh * s),
                cy + (hw * s + -hh * c),
                cx + (hw * c - hh * s),
                cy + (hw * s + hh * c),
                cx + (-hw * c - hh * s),
                cy + (-hw * s + hh * c),
            ),
            2,
        )
    }
}
