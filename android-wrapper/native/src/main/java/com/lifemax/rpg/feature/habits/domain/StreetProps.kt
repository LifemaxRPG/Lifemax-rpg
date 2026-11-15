package com.lifemax.rpg.feature.habits.domain

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The street's other walkers: a small dog trotting on a lead, and a bicycle with its rider pedalling. The same
 * kind of shapes as [StreetRig], baked by the same rasteriser. Units, x forward, y down, the ground at y = 0.
 */

object DogRig {
    const val BODY = .62f
    const val FAR = .5f

    /** Frame [frame] of the dog: [StreetGait.DOG_FRAMES] of the trot, then the sniff. */
    fun frame(frame: Int): List<Shape> =
        if (frame < StreetGait.DOG_FRAMES) trot(frame / StreetGait.DOG_FRAMES.toFloat())
        else sniff()

    const val SNIFF_FRAME = StreetGait.DOG_FRAMES
    const val TOTAL_FRAMES = StreetGait.DOG_FRAMES + 1

    private fun rad(d: Float) = d * (PI.toFloat() / 180f)

    private fun body(
        out: ArrayList<Shape>,
        legs: FloatArray,
        bob: Float,
        headDown: Float,
        tail: Float,
        sniffing: Boolean,
    ) {
        val s = StreetLife.DOG_SIZE
        val y0 = -5.9f * s - bob
        fun add(pts: FloatArray, tone: Float) {
            out += Shape(Shape.BODY, pts, tone)
        }
        // the far legs (hind at the back, fore at the front)
        leg(out, -3.0f * s, y0 + .5f, legs[3], 2.5f * s, 2.3f * s, BODY * FAR, true)
        leg(out, 2.7f * s, y0 + .7f, legs[1], 2.4f * s, 2.3f * s, BODY * FAR, false)
        // the body: a capsule deeper at the chest, with a tucked belly
        add(
            FigBuilder.capsule(-3.2f * s, y0 - .1f, 1.35f * s, 2.9f * s, y0 - .3f, 1.6f * s, 8),
            BODY,
        )
        // the tail, up and wagging
        val ta = rad(-55f + tail)
        add(
            FigBuilder.capsule(
                -4.3f * s,
                y0 - .8f,
                .4f * s,
                -4.3f * s + cos(ta) * -2.4f * s,
                y0 - .8f + sin(ta) * 2.4f * s,
                .22f * s,
                5,
            ),
            BODY,
        )
        // the neck and the head, the muzzle, an ear
        val hd = rad(headDown)
        val nx = 4.2f * s + cos(hd) * .5f
        val ny = y0 - 2.0f * s + sin(hd) * 2.2f * s
        add(FigBuilder.capsule(3.0f * s, y0 - .9f, .95f * s, nx, ny, .95f * s, 6), BODY)
        add(FigBuilder.ellipse(nx + .3f * s, ny, 1.15f * s, 1.05f * s, headDown, 10), BODY)
        add(
            FigBuilder.capsule(
                nx + .8f * s,
                ny + .15f * s,
                .7f * s,
                nx + cos(hd) * 2.1f * s,
                ny + .25f * s + sin(hd) * 2.1f * s,
                .42f * s,
                5,
            ),
            BODY,
        )
        add(
            FigBuilder.ellipse(nx - .2f * s, ny - .8f * s, .45f * s, .85f * s, headDown - 35f, 7),
            BODY * .8f,
        )
        // the collar, so the lead has somewhere to go
        add(FigBuilder.ellipse(3.3f * s, y0 - 1.3f * s, .4f * s, 1.0f * s, 20f, 6), .3f)
        // the near legs
        leg(out, -2.6f * s, y0 + .5f, legs[2], 2.5f * s, 2.3f * s, BODY, true)
        leg(out, 3.1f * s, y0 + .7f, legs[0], 2.4f * s, 2.3f * s, BODY, false)
    }

    private fun leg(
        out: ArrayList<Shape>,
        x: Float,
        y: Float,
        a: Float,
        upper: Float,
        lower: Float,
        tone: Float,
        hind: Boolean,
    ) {
        // a dog's hind leg bends back at the hock, the fore leg forward at the wrist
        val bend = if (hind) -28f else 18f
        val kx = x + sin(rad(a)) * upper
        val ky = y + cos(rad(a)) * upper
        val fx = kx + sin(rad(a + bend)) * lower
        val fy = ky + cos(rad(a + bend)) * lower
        out += Shape(Shape.BODY, FigBuilder.capsule(x, y, .75f, kx, ky, .45f, 6), tone)
        out += Shape(Shape.BODY, FigBuilder.capsule(kx, ky, .45f, fx, fy, .32f, 6), tone)
        out += Shape(Shape.BODY, FigBuilder.ellipse(fx + .3f, fy, .6f, .32f, 0f, 6), tone)
    }

    fun trot(f: Float): List<Shape> {
        val out = ArrayList<Shape>()
        val legs = FloatArray(4)
        StreetGait.dogLegs(f, legs)
        body(
            out,
            legs,
            StreetGait.dogBob(f),
            8f + 4f * sin(4f * PI.toFloat() * f),
            StreetGait.dogTail(f),
            false,
        )
        ground(out)
        return out
    }

    fun sniff(): List<Shape> {
        val out = ArrayList<Shape>()
        body(out, floatArrayOf(10f, -8f, -6f, 8f), 0f, 58f, 10f, true)
        ground(out)
        return out
    }

    private fun ground(out: ArrayList<Shape>) {
        var low = -1e9f
        for (s in out) {
            var i = 1
            while (i < s.pts.size) {
                low = kotlin.math.max(low, s.pts[i])
                i += 2
            }
        }
        for (s in out) {
            var i = 1
            while (i < s.pts.size) {
                s.pts[i] -= low
                i += 2
            }
        }
    }
}

object CycleRig {
    /** The bicycle's geometry, units from the ground under the middle of the wheelbase. */
    const val WHEELBASE = 19f
    const val R = StreetGait.WHEEL_R
    const val BB_X = .8f
    const val BB_Y = -5.4f
    const val CRANK = 3.2f
    const val SADDLE_X = -3.6f
    const val SADDLE_Y = -13.2f
    const val HEAD_X = 6.4f
    const val HEAD_Y = -12.6f
    const val BAR_X = 7.6f
    const val BAR_Y = -13.4f

    const val FRAME = .5f
    const val FAR = .5f

    private fun rad(d: Float) = d * (PI.toFloat() / 180f)

    /** The near pedal's position at crank angle [a] (radians, 0 at the front, turning forward). */
    fun pedalX(a: Float) = BB_X + cos(a) * CRANK

    fun pedalY(a: Float) = BB_Y + sin(a) * CRANK

    private fun tube(
        out: ArrayList<Shape>,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        r: Float,
        tone: Float,
    ) {
        out += Shape(Shape.BODY, FigBuilder.capsule(x0, y0, r, x1, y1, r, 4), tone)
    }

    /**
     * The bicycle (without its wheels, which turn on their own) and the rider at crank angle
     * [crank] of frame [frame] of [StreetGait.CRANK_FRAMES].
     */
    fun frame(look: Int, frame: Int): List<Shape> {
        val a = frame / StreetGait.CRANK_FRAMES.toFloat() * 2f * PI.toFloat()
        val out = ArrayList<Shape>()
        val lk = StreetLooks.ALL[look]
        val hgt = lk.height
        val b = lk.build
        val rx = -WHEELBASE / 2f
        val fx = WHEELBASE / 2f
        val ay = -R
        // the far pedal, crank and the rider's far leg behind the frame
        val fa = a + PI.toFloat()
        riderLeg(out, lk, pedalX(fa), pedalY(fa), StreetRig.TROUSER * FAR, StreetRig.SHOE * FAR)
        tube(out, BB_X, BB_Y, pedalX(fa), pedalY(fa), .32f, FRAME * FAR)
        out +=
            Shape(
                Shape.BODY,
                FigBuilder.capsule(
                    pedalX(fa) - .9f,
                    pedalY(fa),
                    .3f,
                    pedalX(fa) + .9f,
                    pedalY(fa),
                    .3f,
                    3,
                ),
                FRAME * FAR,
            )
        // the frame: seat tube, down tube, top tube, stays, fork, seat post, bars
        tube(out, BB_X, BB_Y, SADDLE_X, SADDLE_Y, .42f, FRAME)
        tube(out, BB_X, BB_Y, HEAD_X, HEAD_Y, .45f, FRAME)
        tube(out, SADDLE_X, SADDLE_Y + .6f, HEAD_X, HEAD_Y + .3f, .38f, FRAME)
        tube(out, BB_X, BB_Y, rx, ay, .3f, FRAME)
        tube(out, SADDLE_X, SADDLE_Y + .6f, rx, ay, .3f, FRAME)
        tube(out, HEAD_X, HEAD_Y, fx, ay, .36f, FRAME)
        tube(out, SADDLE_X, SADDLE_Y + .6f, SADDLE_X - .4f, SADDLE_Y - 1.4f, .3f, FRAME)
        out +=
            Shape(
                Shape.BODY,
                FigBuilder.capsule(
                    SADDLE_X - 2.2f,
                    SADDLE_Y - 1.6f,
                    .7f,
                    SADDLE_X + .9f,
                    SADDLE_Y - 1.5f,
                    .5f,
                    5,
                ),
                .25f,
            )
        tube(out, HEAD_X, HEAD_Y, BAR_X, BAR_Y, .36f, FRAME)
        out +=
            Shape(
                Shape.BODY,
                FigBuilder.capsule(BAR_X - .6f, BAR_Y, .4f, BAR_X + 1.8f, BAR_Y + .4f, .4f, 4),
                .25f,
            )
        // the chainring and the chain line
        out += Shape(Shape.BODY, FigBuilder.ellipse(BB_X, BB_Y, 1.9f, 1.9f, 0f, 14), FRAME * .8f)
        tube(out, BB_X, BB_Y - 1.7f, rx, ay - 1.3f, .14f, .3f)
        tube(out, BB_X, BB_Y + 1.7f, rx, ay + 1.3f, .14f, .3f)
        // the rider: hips on the saddle, leaning to the bars
        val hx = SADDLE_X + .6f
        val hy = SADDLE_Y - 2.1f * hgt
        val lean = 38f
        val torsoL = 7.4f * hgt
        val sx = hx + sin(rad(lean)) * torsoL
        val sy = hy - cos(rad(lean)) * torsoL
        val nx = cos(rad(lean))
        val ny = sin(rad(lean))
        // the far arm to the bars
        arm(
            out,
            sx - nx * 1.2f * b,
            sy - ny * 1.2f * b,
            BAR_X + .6f,
            BAR_Y - .3f,
            b * .95f,
            hgt,
            FAR,
        )
        // the torso
        out +=
            Shape(
                Shape.BODY,
                FigBuilder.chaikin(
                    floatArrayOf(
                        hx + nx * 2.2f * b,
                        hy + ny * 2.2f * b,
                        sx + nx * 2.1f * b,
                        sy + ny * 2.1f * b,
                        sx + sin(rad(lean)) * .9f - nx * 2.2f * b,
                        sy - cos(rad(lean)) * .9f - ny * 2.2f * b,
                        hx - nx * 2.1f * b,
                        hy - ny * 2.1f * b,
                    ),
                    2,
                ),
                StreetRig.TEE,
            )
        out +=
            Shape(
                Shape.BODY,
                FigBuilder.ellipse(hx, hy + .3f, 2.6f * b, 1.9f * hgt, lean * .5f, 10),
                StreetRig.TROUSER,
            )
        // the neck and the head, looking up the road, a helmet on it
        val headA = lean - 30f
        val neck = 1.0f * hgt
        val hdx = sx + sin(rad(headA)) * (neck * 1.1f + 1.5f * hgt)
        val hdy = sy - cos(rad(headA)) * (neck * 1.1f + 1.5f * hgt)
        out +=
            Shape(
                Shape.BODY,
                FigBuilder.capsule(sx, sy, 1.0f * b, hdx, hdy + .4f, .85f * b, 5),
                StreetRig.SKIN,
            )
        out +=
            Shape(
                Shape.BODY,
                FigBuilder.ellipse(hdx, hdy, 1.35f * hgt, 1.6f * hgt, headA, 14),
                StreetRig.SKIN,
            )
        out +=
            Shape(
                Shape.BODY,
                FigBuilder.ellipse(
                    hdx - sin(rad(headA)) * .2f,
                    hdy - .5f * hgt,
                    1.55f * hgt,
                    1.15f * hgt,
                    headA,
                    12,
                ),
                .22f,
            )
        // the near leg over the frame, its crank and pedal
        riderLeg(out, lk, pedalX(a), pedalY(a), StreetRig.TROUSER, StreetRig.SHOE)
        tube(out, BB_X, BB_Y, pedalX(a), pedalY(a), .34f, FRAME)
        out +=
            Shape(
                Shape.BODY,
                FigBuilder.capsule(
                    pedalX(a) - 1f,
                    pedalY(a),
                    .32f,
                    pedalX(a) + 1f,
                    pedalY(a),
                    .32f,
                    3,
                ),
                .25f,
            )
        // the near arm
        arm(out, sx + nx * 1.2f * b, sy + ny * 1.2f * b, BAR_X + .9f, BAR_Y - .2f, b, hgt, 1f)
        return out
    }

    private fun riderLeg(
        out: ArrayList<Shape>,
        lk: StreetLook,
        px: Float,
        py: Float,
        tone: Float,
        shoe: Float,
    ) {
        val hgt = lk.height
        val b = lk.build
        val hx = SADDLE_X + .6f
        val hy = SADDLE_Y - 2.1f * hgt
        val th = 5.9f * hgt
        val sh = 5.7f * hgt
        // the knee by the two legs' lengths, forward of the line from the hip to the pedal
        val dx = px - hx
        val dy = py - (hy + .4f)
        val d = min(sqrt(dx * dx + dy * dy), (th + sh) * .995f)
        val base = atan2(dy, dx)
        val k = acos(((th * th + d * d - sh * sh) / (2f * th * d)).coerceIn(-1f, 1f))
        val kx = hx + cos(base - k) * th
        val ky = hy + .4f + sin(base - k) * th
        out +=
            Shape(Shape.BODY, StreetRig.bowed(hx, hy + .4f, 1.7f * b, kx, ky, 1.15f * b, .2f), tone)
        out +=
            Shape(Shape.BODY, StreetRig.bowed(kx, ky, 1.1f * b, px, py - .6f, .75f * b, -.2f), tone)
        out +=
            Shape(
                Shape.BODY,
                FigBuilder.capsule(px - 1.1f, py - .55f, .55f, px + 1.3f, py - .5f, .45f, 5),
                shoe,
            )
    }

    private fun arm(
        out: ArrayList<Shape>,
        sx: Float,
        sy: Float,
        hx: Float,
        hy: Float,
        b: Float,
        hgt: Float,
        shade: Float,
    ) {
        val ua = 4.1f * hgt
        val fa = 3.7f * hgt
        val dx = hx - sx
        val dy = hy - sy
        val d = min(sqrt(dx * dx + dy * dy), (ua + fa) * .99f)
        val base = atan2(dy, dx)
        val k = acos(((ua * ua + d * d - fa * fa) / (2f * ua * d)).coerceIn(-1f, 1f))
        // the elbow hangs below the line to the bars
        val ex = sx + cos(base + k) * ua
        val ey = sy + sin(base + k) * ua
        out +=
            Shape(
                Shape.BODY,
                FigBuilder.capsule(sx, sy, 1.05f * b, ex, ey, .8f * b, 5),
                StreetRig.TEE * shade,
            )
        out +=
            Shape(
                Shape.BODY,
                FigBuilder.capsule(ex, ey, .8f * b, hx, hy, .55f * b, 5),
                StreetRig.TEE * shade,
            )
        out +=
            Shape(
                Shape.BODY,
                FigBuilder.ellipse(hx + .3f, hy, .7f * b, .5f * b, 0f, 6),
                StreetRig.SKIN * shade,
            )
    }
}
