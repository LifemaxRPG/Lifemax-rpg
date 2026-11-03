package com.lifemax.rpg.feature.pet.body

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The physics of a toy that is not in a cat's mouth: gravity, air drag, bounces that lose energy,
 * sliding and rolling friction, the walls of the house, the sides of the furniture, landing on the
 * floor and on every surface of the room (and on the overhang of the furniture that carries one:
 * the whole worktop, the whole slab), and coming to rest. It works in room pixels on a fixed 1/120
 * s sub-step; [step] allocates nothing.
 *
 * Landing. A toy that is falling lands on the highest surface whose span it is over, whose depth it
 * is within [ToyTuning.Z_LAND_TOLERANCE] of, and whose line it crossed during the step. A toy in
 * the air with a depth goal ([ToyBody.zGoal], set by an aimed toss) drifts to that depth as it
 * falls, so a toss at the counter from the floor ends on the counter. A toy resting on a surface
 * that rolls past its end falls.
 *
 * Walls. The ends of the house and the sides of every solid (see [Solids]) turn a sideways toy back
 * with [ToyTuning.WALL_RESTITUTION] (the web's 0.6). The top of a solid is a landing, not a wall.
 */
internal object ToyPhysics {
    private const val NO_HIT = -1
    private const val WALL_TOP_SKIM = 2f
    private const val TILT_RATE = 8f
    private const val TWO_PI = Mathx.TWO_PI
    private const val FLUTTER_HZ = 1.8f
    private const val FLUTTER_PHASE_PER_ID = 1.7f
    private const val ROLL_SPEED_FLOOR = 0.5f

    /**
     * Landing span of each surface: its walkable range plus the margin, plus the solid it stands
     * on.
     */
    private val spanLo = FloatArray(SurfaceId.entries.size)
    private val spanHi = FloatArray(SurfaceId.entries.size)
    private val order: IntArray

    init {
        for (id in SurfaceId.entries) {
            val s = World.surface(id)
            var lo = s.x0 - ToyTuning.EDGE_MARGIN
            var hi = s.x1 + ToyTuning.EDGE_MARGIN
            if (id == SurfaceId.FLOOR) {
                lo = World.HOUSE_MIN_X
                hi = World.HOUSE_MAX_X
            }
            for (solid in Solids.all) {
                val own = solid.owners and Solid.bit(id) != 0
                if (own && Integer.bitCount(solid.owners) == 1) {
                    lo = minOf(lo, solid.x0)
                    hi = maxOf(hi, solid.x1)
                }
            }
            spanLo[id.ordinal] = lo
            spanHi[id.ordinal] = hi
        }
        // Surfaces from the highest on screen down, so the first crossed is the first met.
        val raised = SurfaceId.entries.filter { it != SurfaceId.FLOOR }
        order = IntArray(raised.size) { raised.sortedBy { s -> World.surface(s).y }[it].ordinal }
    }

    /** Left end of the span a toy can lie on at [id]. */
    fun spanStart(id: SurfaceId): Float = spanLo[id.ordinal]

    /** Right end of the span a toy can lie on at [id]. */
    fun spanEnd(id: SurfaceId): Float = spanHi[id.ordinal]

    /** Velocity decay factor of one tick for an exponential drag [c] per second (no `exp`). */
    private fun decay(c: Float, dt: Float): Float {
        val a = c * dt
        return 1f / (1f + a + 0.5f * a * a)
    }

    /** Advance [t] by one tick of [dt] seconds. */
    fun step(t: ToyBody, dt: Float) {
        t.bumpedNow = false
        t.age += dt
        t.squashX.step(0f, dt)
        t.squashY.step(0f, dt)
        if (t.state != ToyState.FREE) {
            t.compose()
            return
        }
        if (t.airborne) fly(t, dt) else slide(t, dt)
        followDepthGoal(t, dt)
        if (t.vx > MIRROR_SPEED) t.mirrored = false else if (t.vx < -MIRROR_SPEED) t.mirrored = true
        t.compose()
    }

    private const val MIRROR_SPEED = 8f

    // ---------------------------------------------------------------- in the air

    private fun fly(t: ToyBody, dt: Float) {
        val k = t.kind
        t.vy += ToyTuning.GRAVITY * k.gravityScale * dt
        val drag = decay(k.airDrag, dt)
        t.vx *= drag
        t.vy *= drag
        if (k.flutter > 0f && t.vy > 0f) {
            val phase = t.age * FLUTTER_HZ * TWO_PI + t.id * FLUTTER_PHASE_PER_ID
            t.vx += k.flutter * Mathx.sin(phase) * dt
        }
        t.angle += t.spin * dt
        val footBefore = t.footY()
        val px = t.x
        var nx = t.x + t.vx * dt
        t.y += t.vy * dt
        t.z = Mathx.clamp(t.z + t.vz * dt, ToyTuning.Z_MIN, ToyTuning.Z_MAX)
        nx = walls(t, px, nx)
        t.x = nx
        if (t.vy > 0f) land(t, footBefore)
    }

    /**
     * Side walls: the house ends and the furniture; returns the corrected x and turns the speed.
     */
    private fun walls(t: ToyBody, px: Float, nx: Float): Float {
        var x = nx
        val hw = t.kind.width / 2f * World.depthScale(t.z)
        val lo = World.HOUSE_MIN_X + ToyTuning.WALL_MARGIN
        val hi = World.HOUSE_MAX_X - ToyTuning.WALL_MARGIN
        if (x < lo) {
            x = lo
            if (t.vx < 0f) bounceWall(t)
        } else if (x > hi) {
            x = hi
            if (t.vx > 0f) bounceWall(t)
        }
        val foot = t.footY()
        val top = t.y - t.kind.halfHeight * World.depthScale(t.z)
        for (i in Solids.all.indices) {
            val s = Solids.all[i]
            if (foot <= s.top + WALL_TOP_SKIM || top >= s.bottom) continue
            if (t.vx > 0f && px + hw <= s.x0 && x + hw > s.x0) {
                x = s.x0 - hw
                bounceWall(t)
            } else if (t.vx < 0f && px - hw >= s.x1 && x - hw < s.x1) {
                x = s.x1 + hw
                bounceWall(t)
            }
        }
        return x
    }

    private fun bounceWall(t: ToyBody) {
        noteBump(t, Mathx.abs(t.vx))
        t.vx = -t.vx * ToyTuning.WALL_RESTITUTION
        t.spin *= WALL_SPIN_KEEP
        t.squashX.displace(-minOf(WALL_SQUASH, Mathx.abs(t.vx) * ToyTuning.SQUASH_PER_IMPACT))
    }

    private const val WALL_SPIN_KEEP = 0.6f
    private const val WALL_SQUASH = 0.2f

    private fun noteBump(t: ToyBody, speed: Float) {
        if (speed >= ToyTuning.BOUNCE_MIN_SPEED) {
            t.bumpedNow = true
            t.bumpSpeed = maxOf(t.bumpSpeed, speed)
        }
    }

    /** The surface (or the floor) a falling toy touched this tick, put into effect. */
    private fun land(t: ToyBody, footBefore: Float) {
        val foot = t.footY()
        val half = t.kind.halfHeight * World.depthScale(t.z)
        var hit = NO_HIT
        var hitY = 0f
        for (n in order.indices) {
            val id = order[n]
            val s = World.surface(SurfaceId.entries[id])
            if (t.x < spanLo[id] || t.x > spanHi[id]) continue
            if (Mathx.abs(t.z - s.z) > ToyTuning.Z_LAND_TOLERANCE) continue
            if (footBefore <= s.y + EPS && foot >= s.y) {
                hit = id
                hitY = s.y
                break
            }
        }
        if (hit == NO_HIT) {
            val floor = World.floorY(t.z)
            if (foot < floor) return
            hit = SurfaceId.FLOOR.ordinal
            hitY = floor
        }
        touchDown(t, SurfaceId.entries[hit], hitY - half)
    }

    private const val EPS = 0.001f

    private fun touchDown(t: ToyBody, on: SurfaceId, centreY: Float) {
        val impact = t.vy
        t.surface = on
        if (on != SurfaceId.FLOOR) t.z = World.surface(on).z
        t.zGoal = Float.NaN
        t.y = centreY
        t.vx *= t.kind.impactGrip
        noteBump(t, impact)
        val squash = minOf(ToyTuning.SQUASH_MAX, impact * ToyTuning.SQUASH_PER_IMPACT)
        t.squashY.displace(-squash)
        t.squashX.displace(squash * Tuning.Squash.VOLUME_SHARE)
        if (impact > ToyTuning.BOUNCE_MIN_SPEED) {
            t.vy = -impact * t.kind.restitution
            if (!t.kind.rolls) t.spin += Mathx.sign(t.vx) * impact * ToyTuning.SPIN_PER_IMPACT
        } else {
            t.vy = 0f
            t.airborne = false
        }
    }

    // ---------------------------------------------------------------- on a surface

    private fun slide(t: ToyBody, dt: Float) {
        val k = t.kind
        val drag = decay(k.groundDrag, dt)
        t.vx *= drag
        t.vz *= drag
        if (Mathx.abs(t.vx) < ToyTuning.STOP_SPEED) t.vx = 0f
        if (Mathx.abs(t.vz) < ToyTuning.STOP_SPEED) t.vz = 0f
        val px = t.x
        var nx = t.x + t.vx * dt
        if (t.surface == SurfaceId.FLOOR) {
            t.z = Mathx.clamp(t.z + t.vz * dt, ToyTuning.Z_MIN, ToyTuning.Z_MAX)
        } else {
            t.vz = 0f
        }
        nx = walls(t, px, nx)
        t.x = nx
        t.y = t.restY()
        spinOnGround(t, dt)
        if (t.surface != SurfaceId.FLOOR) {
            val id = t.surface.ordinal
            if (t.x < spanLo[id] || t.x > spanHi[id]) {
                t.airborne = true
                t.vy = 0f
                return
            }
        }
        if (t.vx == 0f && t.vz == 0f && Mathx.abs(t.spin) < REST_SPIN) rest(t) else t.stillFor = 0f
    }

    private const val REST_SPIN = 0.2f

    private fun spinOnGround(t: ToyBody, dt: Float) {
        val k = t.kind
        if (k.rolls) {
            val radius = k.halfHeight * World.depthScale(t.z)
            t.spin = if (Mathx.abs(t.vx) > ROLL_SPEED_FLOOR) t.vx / radius else 0f
            t.angle += t.spin * dt
            return
        }
        t.spin *= decay(ToyTuning.SPIN_DAMP, dt)
        t.angle += t.spin * dt
        // A mouse or a feather ends the right way up: ease to the nearest whole turn.
        val whole = floor(t.angle / TWO_PI + 0.5f) * TWO_PI
        t.angle -= (t.angle - whole) * minOf(1f, TILT_RATE * dt)
    }

    private fun rest(t: ToyBody) {
        t.stillFor += ToyTuning.DT
        t.spin = 0f
        t.state = ToyState.REST
    }

    private fun followDepthGoal(t: ToyBody, dt: Float) {
        if (!t.airborne || t.zGoal.isNaN()) return
        t.z = Mathx.moveToward(t.z, t.zGoal, ToyTuning.Z_PULL * dt)
    }

    // ---------------------------------------------------------------- aiming

    /**
     * The launch velocity that carries a toy of [kind] from centre ([x0], [y0]) onto centre ([x1],
     * [y1]) in a ballistic arc whose peak is [arc] px above the higher end, written to [out] as vx,
     * vy and the flight time. Air drag is compensated for the horizontal speed.
     */
    fun aim(
        kind: ToyKind,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        arc: Float,
        out: FloatArray,
    ) {
        val g = ToyTuning.GRAVITY * kind.gravityScale
        val peak = minOf(y0, y1) - arc
        val vy = -sqrt(2f * g * (y0 - peak))
        val tUp = -vy / g
        val tDown = sqrt(2f * (y1 - peak) / g)
        val total = tUp + tDown
        val x = kind.airDrag * total
        val mean = 1f - x / 2f + x * x / 6f
        out[0] = (x1 - x0) / total / mean
        out[1] = vy
        out[2] = total
    }

    /**
     * Like [aim], but takes the flattest arc, no flatter than a small floor, that does not cross
     * any surface other than [target] on its way down (a toss at the counter must stay under the
     * window sill above it). Starts from [arc] and lowers it until the path is clear.
     */
    fun aimClear(
        kind: ToyKind,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        arc: Float,
        target: SurfaceId,
        out: FloatArray,
    ) {
        var a = arc
        for (i in 0 until ARC_TRIES) {
            aim(kind, x0, y0, x1, y1, a, out)
            if (!crossesOther(kind, x0, y0, out, target)) return
            a *= ARC_LOWER
            if (a < ARC_FLOOR) break
        }
        aim(kind, x0, y0, x1, y1, maxOf(a, ARC_FLOOR), out)
    }

    private const val ARC_TRIES = 8
    private const val ARC_LOWER = 0.65f
    private const val ARC_FLOOR = 3f
    private const val PATH_STEP = 0.02f
    private const val PATH_MARGIN = 10f

    private fun crossesOther(
        kind: ToyKind,
        x0: Float,
        y0: Float,
        v: FloatArray,
        target: SurfaceId,
    ): Boolean {
        val g = ToyTuning.GRAVITY * kind.gravityScale
        val half = kind.halfHeight
        var t = PATH_STEP
        var prevFoot = y0 + half
        while (t < v[2]) {
            val x = x0 + v[0] * t
            val foot = y0 + v[1] * t + 0.5f * g * t * t + half
            if (v[1] + g * t > 0f) {
                for (n in order.indices) {
                    val id = order[n]
                    if (id == target.ordinal) continue
                    val s = World.surface(SurfaceId.entries[id])
                    val over = x >= spanLo[id] - PATH_MARGIN && x <= spanHi[id] + PATH_MARGIN
                    if (over && prevFoot <= s.y && foot >= s.y) return true
                }
            }
            prevFoot = foot
            t += PATH_STEP
        }
        return false
    }
}
