package com.lifemax.rpg.feature.habits.domain

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * How a street walker is put together each frame from its baked layers. The legs and the body are baked for every
 * frame of the walk ([StreetRig.body]); the arms (an upper arm and a forearm each, the hand open, closed round
 * something or in a pocket), the head (in profile, or glanced round), a ponytail and what the hands hold (a phone,
 * a coffee, a handbag) are baked once and turned about their joints as they are drawn. So the arms can swing as
 * the walk has them or do whatever the walker is doing ([StreetActs]), eased between the two by the act's envelope
 * in the joints' angles (a hand rises to the phone along an arc, it does not fade from one place to another), and
 * the head bows over the phone or lifts to sip. The far arm goes behind the body, the near arm and whatever it
 * holds in front of everything. Pure arithmetic, no allocation: the painter and the previews read the [Layers]
 * this fills.
 */

/** The layers baked for each look, by index ([StreetLayers.part]). */
object Part {
    const val UPPER_N = 0
    const val FORE_N = 1 // + the hand: OPEN, GRIP, POCKET
    const val UPPER_F = 4
    const val FORE_F = 5
    const val HEAD = 8
    const val HEAD_GLANCE = 9
    const val TAIL = 10
    const val HANDBAG = 11
    const val PHONE = 12
    const val CUP = 13
    const val COUNT = 14
}

/**
 * One walker's layers this moment, back to front: each a [part] (or the body's frame: [body] for a
 * negative part), where its joint is from the feet ([x], [y], rig units), turned [deg] degrees
 * (clockwise on the screen, the figure facing +x) and how strong ([alpha]). Also where the near
 * hand's fingers are (for a dog's lead).
 */
class Layers {
    val part = IntArray(MAX)
    val x = FloatArray(MAX)
    val y = FloatArray(MAX)
    val deg = FloatArray(MAX)
    val alpha = FloatArray(MAX)
    var count = 0
    var handX = 0f
    var handY = 0f

    /** The body frame a layer with a negative [part] shows: -1 - part. */
    fun body(i: Int): Int = -1 - part[i]

    internal fun add(p: Int, px: Float, py: Float, d: Float, a: Float) {
        if (count >= MAX || a <= .02f) return
        part[count] = p
        x[count] = px
        y[count] = py
        deg[count] = d
        alpha[count] = a.coerceAtMost(1f)
        count++
    }

    // scratch for the arms
    internal var ikA = 0f
    internal var ikE = 0f
    internal var nA = 0f
    internal var nE = 0f
    internal var fA = 0f
    internal var fE = 0f

    companion object {
        const val MAX = 20
    }
}

object StreetLayers {
    private fun rad(d: Float) = d * (PI.toFloat() / 180f)

    private fun deg(r: Float) = r * (180f / PI.toFloat())

    private fun smooth(v: Float): Float {
        val c = v.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** The arm's angles that put the wrist at ([dx], [dy]) from the shoulder (the elbow down). */
    internal fun ik(look: Int, dx: Float, dy: Float, out: Layers) {
        val ua = StreetRig.upperLength(look)
        val fa = StreetRig.foreLength(look)
        val d = sqrt(dx * dx + dy * dy).coerceIn(ua - fa + .3f, ua + fa - .05f)
        var phi = deg(atan2(dx, dy))
        if (phi < -60f) phi += 360f
        val al = deg(acos(((ua * ua + d * d - fa * fa) / (2f * ua * d)).coerceIn(-1f, 1f)))
        val ga = deg(acos(((ua * ua + fa * fa - d * d) / (2f * ua * fa)).coerceIn(-1f, 1f)))
        out.ikA = phi - al
        out.ikE = 180f - ga
    }

    /** The [near] (or far) arm's angles that put its wrist at ([x], [y]), into [out]. */
    private fun reach(look: Int, out: Layers, near: Boolean, x: Float, y: Float) {
        if (near) {
            ik(look, x - shNX, y - shNY, out)
            out.nA = out.ikA
            out.nE = out.ikE
        } else {
            ik(look, x - shFX, y - shFY, out)
            out.fA = out.ikA
            out.fE = out.ikE
        }
    }

    // the walk's joints between the two frames shown
    private var shNX = 0f
    private var shNY = 0f
    private var shFX = 0f
    private var shFY = 0f
    private var neckX = 0f
    private var neckY = 0f
    private var headA = 0f
    private var an = 0f
    private var en = 0f
    private var af = 0f
    private var ef = 0f
    private var tail = 0f

    private fun joints(a: WalkerJoints, b: WalkerJoints, t: Float) {
        shNX = lerp(a.shNX, b.shNX, t)
        shNY = lerp(a.shNY, b.shNY, t)
        shFX = lerp(a.shFX, b.shFX, t)
        shFY = lerp(a.shFY, b.shFY, t)
        neckX = lerp(a.neckX, b.neckX, t)
        neckY = lerp(a.neckY, b.neckY, t)
        headA = lerp(a.headA, b.headA, t)
        an = lerp(a.an, b.an, t)
        en = lerp(a.en, b.en, t)
        af = lerp(a.af, b.af, t)
        ef = lerp(a.ef, b.ef, t)
        tail = lerp(a.tail, b.tail, t)
    }

    /**
     * Walker [look]'s layers: the walk's [frame] with [next] mixed in by [mix] (or, [still] past 0,
     * settling to a stand), doing [g], the near hand [busy] with a lead or a bag; written into
     * [out].
     */
    @Synchronized
    fun lay(look: Int, frame: Int, next: Int, mix: Float, still: Float, g: Gesture, out: Layers) {
        out.count = 0
        val js = StreetRig.joints(look)
        val l = StreetLooks.ALL[look]
        // ---- the body's frames, and the joints between them
        var bodyA: Int
        var bodyB: Int
        var bodyMix: Float
        if (still > .01f) {
            val q = still * StreetRig.SETTLE_FRAMES
            val i = q.toInt().coerceIn(0, StreetRig.SETTLE_FRAMES - 1)
            bodyA = if (i == 0) frame else StreetGait.FRAMES + i - 1
            bodyB = StreetGait.FRAMES + i
            bodyMix = q - i
            joints(js[bodyA], js[bodyB], bodyMix)
        } else {
            bodyA = frame
            bodyB = next
            bodyMix = if (next != frame) mix else 0f
            joints(js[bodyA], js[bodyB], bodyMix)
        }

        // ---- what the act asks of the arms and the head
        val hgt = l.height
        val b = l.build
        val w = g.w
        val act = if (w > 0f) g.act else Act.RELAX
        val t = g.t
        var nearW = 0f
        var farW = 0f
        var nearA = 0f
        var nearE = 0f
        var farA = 0f
        var farE = 0f
        var nearHand = StreetRig.OPEN
        var farHand = StreetRig.OPEN
        var tilt = 0f
        var glance = 0f
        var nearProp = -1
        var farProp = -1
        var propDeg = 0f
        var propAlpha = 0f
        var nearSwing = 1f
        var farSwing = 1f
        // the head: where it will be, so a hand can reach it
        val rx = 1.35f * hgt * l.head
        val ry = 1.65f * hgt * l.head
        val headUp = hgt * .6f + ry * .92f
        when (act) {
            Act.PHONE,
            Act.TEXT -> tilt = 24f * w
            Act.FAR_PHONE -> tilt = 22f * w
            Act.WATCH,
            Act.FAR_WATCH -> tilt = 20f * smooth((w - .3f) / .7f)
            Act.SIP -> tilt = -9f * smooth((w - .5f) / .5f)
            Act.SCRATCH -> tilt = 5f * w
            Act.RUB -> tilt = 8f * w
            Act.GLANCE -> {
                glance = w
                tilt = -3f * w
            }
            Act.WAVE -> glance = w
            Act.CALL -> tilt = 4f * w
            else -> {}
        }
        val hA = headA + tilt
        val hcx = neckX + sin(rad(hA)) * headUp
        val hcy = neckY - cos(rad(hA)) * headUp
        // a point on the head, [fx] forward and [fy] down in its radii
        fun headX(fx: Float, fy: Float) = hcx + fx * rx * cos(rad(hA)) - fy * ry * sin(rad(hA))
        fun headY(fx: Float, fy: Float) = hcy + fx * rx * sin(rad(hA)) + fy * ry * cos(rad(hA))
        val pi2 = 2f * PI.toFloat()
        when (act) {
            Act.PHONE -> {
                nearW = w
                reach(look, out, true, shNX + 2.5f * hgt, shNY + 2.9f * hgt)
                nearHand = StreetRig.GRIP
                nearProp = Part.PHONE
                propDeg = 48f
                farSwing = 1f - .35f * w
            }
            Act.TEXT -> {
                nearW = w
                farW = w
                reach(look, out, true, shNX + 2.6f * hgt, shNY + 2.7f * hgt)
                reach(look, out, false, shNX + 2.9f * hgt, shNY + 2.75f * hgt)
                nearHand = StreetRig.GRIP
                farHand = StreetRig.GRIP
                nearProp = Part.PHONE
                propDeg = 55f
                // the thumbs at work
                nearE += 3f * sin(pi2 * 3.1f * t) * w
            }
            Act.FAR_PHONE -> {
                farW = w
                reach(look, out, false, shFX + 3.3f * hgt, shFY + 2.8f * hgt)
                farHand = StreetRig.GRIP
                farProp = Part.PHONE
                propDeg = 50f
                nearSwing = 1f - .3f * w
            }
            Act.CALL -> {
                nearW = w
                reach(look, out, true, headX(.15f, 1.05f), headY(.15f, 1.05f))
                nearHand = StreetRig.GRIP
                nearProp = Part.PHONE
                propDeg = -18f
                farSwing = 1f - .3f * w
            }
            Act.SCRATCH -> {
                // the forearm folded up, the fingers at the jaw, working at it (set by angle: the
                // jaw is too near the shoulder for a reach to hold steady)
                nearW = w
                val s = sin(pi2 * 3.6f * t) * smooth((t - .3f) / .2f)
                nearA = 38f + 4f * g.vary
                nearE = 152f - 8f * g.vary + 6f * s
            }
            Act.RUB -> {
                nearW = w
                val s = .22f * sin(pi2 * 3.2f * t) * smooth((t - .3f) / .2f)
                reach(look, out, true, headX(-.3f + s, -.8f), headY(-.3f + s, -.8f))
                farSwing = 1f - .3f * w
            }
            Act.HAIR -> {
                nearW = w
                when (l.hair) {
                    StreetHair.CAP -> {
                        // a tug at the peak
                        val s = .2f * smooth((t - .35f) / .3f) * (1f - smooth((t - .8f) / .3f))
                        reach(
                            look,
                            out,
                            true,
                            headX(.95f + s, -.15f + s),
                            headY(.95f + s, -.15f + s),
                        )
                    }
                    StreetHair.BEANIE -> {
                        val s = .18f * sin(pi2 * 1.6f * t)
                        reach(look, out, true, headX(.2f + s, -.55f), headY(.2f + s, -.55f))
                    }
                    else -> {
                        // a hand swept back through the hair
                        val s = smooth((t - .2f) / (g.dur - .5f).coerceAtLeast(.3f))
                        val fx = lerp(.55f, -.5f, s)
                        reach(look, out, true, headX(fx, -.75f), headY(fx, -.75f))
                    }
                }
            }
            Act.WATCH -> {
                nearW = w
                reach(look, out, true, shNX + 2.7f * hgt, shNY + 1.7f * hgt)
            }
            Act.FAR_WATCH -> {
                farW = w
                reach(look, out, false, shFX + 3.2f * hgt, shFY + 1.6f * hgt)
            }
            Act.WAVE -> {
                nearW = w
                nearA = 32f + 10f * g.vary
                nearE = 125f + 16f * sin(pi2 * 2.2f * t) - 10f * g.vary
            }
            Act.POCKETS -> {
                nearW = w
                farW = w
                val py =
                    if (l.coat == Coat.LONG) 5.7f else if (l.coat == Coat.JACKET) 6.1f else 6.4f
                reach(look, out, true, shNX + 1.05f * b, shNY + py * hgt)
                reach(look, out, false, shFX + 1.25f * b, shFY + py * hgt)
                nearHand = StreetRig.POCKET
                farHand = StreetRig.POCKET
                nearSwing = 1f - .88f * w
                farSwing = 1f - .88f * w
            }
            Act.STRAP -> {
                nearW = w
                if (l.bag == Bag.BACKPACK)
                    reach(look, out, true, shNX + 1.5f * b, shNY + 2.1f * hgt)
                else reach(look, out, true, shNX + 1.25f * b, shNY + 2.6f * hgt)
                nearHand = StreetRig.GRIP
                farSwing = 1f - .2f * w
            }
            Act.SIP -> {
                nearW = w
                reach(look, out, true, headX(1.1f, 1.55f), headY(1.1f, 1.55f))
            }
            else -> {}
        }
        if (nearW > 0f && act != Act.WAVE && act != Act.SCRATCH) {
            nearA = out.nA
            nearE += out.nE
        }
        if (farW > 0f) {
            farA = out.fA
            farE = out.fE
        }
        // a coffee carried: the near arm's rest is the carry, the cup upright in front
        var cupA = 0f
        var cupE = 0f
        if (g.cup) {
            ik(look, (2.6f + .4f * an / 25f) * hgt, 4.6f * hgt, out)
            cupA = out.ikA
            cupE = out.ikE
        }
        val nearRestA = if (g.cup) cupA else an * nearSwing
        val nearRestE = if (g.cup) cupE else en
        val nA = lerp(nearRestA, nearA, nearW)
        // a hand on its way up to the head folds the elbow as it goes (it does not reach out
        // straight in front on the way)
        val fold = if (nearA > 60f) 70f * 4f * nearW * (1f - nearW) else 0f
        val nE = lerp(nearRestE, nearE, nearW) + fold
        val fA = lerp(af * farSwing, farA, farW)
        val fE = lerp(ef, farE, farW)
        if (g.cup) {
            nearHand = StreetRig.GRIP
            nearProp = Part.CUP
        }
        val handW = if (g.cup) 1f else nearW
        propAlpha = if (g.cup) 1f else smooth((w - .45f) / .3f)

        // ---- back to front: the far arm (and what it holds), the body, the head, the near arm
        arm(look, out, false, shFX, shFY, fA, fE, farHand, farW, farProp, propDeg, propAlpha)
        if (bodyMix > .03f) {
            if (still > .01f) {
                out.add(-1 - bodyA, 0f, 0f, 0f, 1f)
                out.add(-1 - bodyB, 0f, 0f, 0f, bodyMix)
            } else {
                out.add(-1 - bodyB, 0f, 0f, 0f, bodyMix)
                out.add(-1 - bodyA, 0f, 0f, 0f, 1f)
            }
        } else out.add(-1 - bodyA, 0f, 0f, 0f, 1f)
        // the head turns about the neck's pivot; a ponytail swings from its root
        if (l.hair == StreetHair.PONY) {
            val c = cos(rad(hA))
            val s = sin(rad(hA))
            val rtx = StreetRig.tailRootX(look)
            val rty = StreetRig.tailRootY(look)
            val tx = neckX + rtx * c - rty * s
            val ty = neckY + rtx * s + rty * c
            out.add(Part.TAIL, tx, ty, hA + StreetRig.TAIL_SWING * tail + tilt * -.6f, 1f)
        }
        // the face comes round: the glanced head over the one in profile (their outlines are
        // the same, only the features move)
        if (glance < .98f) out.add(Part.HEAD, neckX, neckY, hA, 1f)
        out.add(Part.HEAD_GLANCE, neckX, neckY, hA, smooth(glance))
        arm(look, out, true, shNX, shNY, nA, nE, nearHand, handW, nearProp, propDeg, propAlpha, g)
        if (l.bag == Bag.HAND)
            out.add(
                Part.HANDBAG,
                out.handX,
                out.handY,
                StreetRig.BAG_SWING * StreetGait.trail(phaseOf(bodyA, bodyB, bodyMix)),
                1f,
            )
    }

    /** The walk's phase at the frames shown, for what hangs loose. */
    private fun phaseOf(a: Int, b: Int, t: Float): Float {
        if (a >= StreetGait.FRAMES) return Gait.PASSING
        var fb = if (b >= StreetGait.FRAMES) a.toFloat() else b.toFloat()
        if (fb - a > StreetGait.FRAMES / 2) fb -= StreetGait.FRAMES
        if (a - fb > StreetGait.FRAMES / 2) fb += StreetGait.FRAMES
        return lerp(a.toFloat(), fb, t) / StreetGait.FRAMES
    }

    /**
     * One arm's layers from the shoulder at ([sx], [sy]): the upper arm, then what the hand holds
     * (it closes over it), then the forearm in the [hand] it has as [handW] goes over half way (the
     * relaxed hand under it fading), the near hand's fingers recorded.
     */
    private fun arm(
        look: Int,
        out: Layers,
        near: Boolean,
        sx: Float,
        sy: Float,
        a: Float,
        e: Float,
        hand: Int,
        handW: Float,
        prop: Int,
        propDeg: Float,
        propAlpha: Float,
        g: Gesture? = null,
    ) {
        val ua = StreetRig.upperLength(look)
        val fa = StreetRig.foreLength(look)
        val ex = sx + sin(rad(a)) * ua
        val ey = sy + cos(rad(a)) * ua
        val f0 = a + e
        val wx = ex + sin(rad(f0)) * fa
        val wy = ey + cos(rad(f0)) * fa
        val upper = if (near) Part.UPPER_N else Part.UPPER_F
        val fore = if (near) Part.FORE_N else Part.FORE_F
        out.add(upper, sx, sy, -a, 1f)
        // the hand's centre, as the rig places it for a closed hand
        val gx = wx + sin(rad(f0 + 6f)) * .62f
        val gy = wy + cos(rad(f0 + 6f)) * .62f
        if (prop >= 0) {
            var d = propDeg
            if (prop == Part.CUP) {
                d = 0f
                if (g != null && g.act == Act.SIP) d = -38f * smooth((g.w - .55f) / .45f)
            }
            out.add(prop, gx, gy, d, propAlpha)
        }
        val m = smooth((handW - .35f) / .3f)
        if (hand == StreetRig.OPEN || m <= .02f) out.add(fore + StreetRig.OPEN, ex, ey, -f0, 1f)
        else if (m >= .98f) out.add(fore + hand, ex, ey, -f0, 1f)
        else if (m < .5f) {
            out.add(fore + StreetRig.OPEN, ex, ey, -f0, 1f)
            out.add(fore + hand, ex, ey, -f0, m * 2f)
        } else {
            out.add(fore + StreetRig.OPEN, ex, ey, -f0, (1f - m) * 2f)
            out.add(fore + hand, ex, ey, -f0, 1f)
        }
        if (near) {
            val b = StreetLooks.ALL[look].build
            val ha = f0 + 16f
            out.handX = wx + sin(rad(ha)) * (.75f + .9f * b)
            out.handY = wy + cos(rad(ha)) * (.75f + .9f * b)
        }
    }
}
