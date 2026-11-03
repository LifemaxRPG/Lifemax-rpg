package com.lifemax.rpg.feature.pet.body

import kotlin.math.atan2

/** What a toy is doing. */
enum class ToyState {
    /** Lying still on a surface. */
    REST,

    /** Flying, bouncing, sliding or rolling under physics. */
    FREE,

    /** Rising from the floor into a cat's mouth along an arc (the bite). */
    LIFT,

    /** Held by a cat: carried on the mouth anchor. */
    HELD,
}

/**
 * One toy: its place, its motion and everything a renderer needs to draw it. Pure state, refilled
 * in place; [ToyPhysics] moves it while it is free, [Carry] while a cat holds it, and the cat's
 * [ToyDirector] hands it between the two.
 *
 * **Drawing.** Take the sprite of [kind] (`ball_a`, `ball_b`, `mouse`, `feather` from the room
 * atlas; yarn and spring have none yet), move to ([drawX], [drawY]) in room pixels, rotate by
 * [drawAngle] radians (clockwise), scale by [drawScaleX] (negative when mirrored) and [drawScaleY]
 * and by [depthScale], and draw the sprite so that its pixel ([pivotU], [pivotV]) is on that point.
 * Draw it after the cat when [overCat] (it is in or at the mouth) and sorted by depth [z] with the
 * cat otherwise. Yarn also draws a polyline through [thread] ([threadCount] points of x, y pairs)
 * from the ball.
 *
 * Positions are room pixels; [x], [y] is the **centre** of the sprite while free and at rest, [y]
 * grows downward like every screen row.
 */
class ToyBody(
    /** The toy's index in its [ToyField]. */
    val id: Int,
    val kind: ToyKind,
) {
    var state = ToyState.REST
        internal set

    /** Centre of the toy. */
    var x = 0f
    var y = 0f

    /** Depth, 0 at the back wall to 100 at the glass. */
    var z = World.ART_Z

    var vx = 0f
    var vy = 0f
    var vz = 0f

    /** Turn of the sprite, radians, and how fast it turns. */
    var angle = 0f
    var spin = 0f

    /** The surface it rests on, or last touched. */
    var surface = SurfaceId.FLOOR
        internal set

    /** True while the toy is clear of every surface. */
    var airborne = false
        internal set

    /** The depth the toy drifts to while it falls toward a surface; NaN for none. */
    var zGoal = Float.NaN

    /** The sprite faces left (set from the last sideways motion, as the web's `flip`). */
    var mirrored = false

    /** True on the tick the toy hit something hard enough to be heard. */
    var bumpedNow = false
        internal set

    /** How hard that was, px/s. */
    var bumpSpeed = 0f
        internal set

    internal val squashX = Spring(ToyTuning.SQUASH)
    internal val squashY = Spring(ToyTuning.SQUASH)

    /** The toy's own clock, for the flutter of light toys. */
    internal var age = 0f

    /** How long the toy has been still, seconds. */
    internal var stillFor = 0f

    // held state
    /** True while the toy is drawn over the cat. */
    var overCat = false
        internal set

    /** The mouth anchor the held toy follows, room px. */
    internal var anchorX = 0f
    internal var anchorY = 0f

    /** How fast the anchor moves, px/s (the cat's head, from its last two ticks). */
    internal var anchorVx = 0f
    internal var anchorVy = 0f

    /** Row of the ground under the holder (a carried toy never goes through it). */
    internal var holderGround = World.FLOOR_Y_ART

    /** +1 or -1, flipped by every footfall so the sway alternates. */
    internal var footSide = 1f

    /** The pivot of a held toy (the mouth) and its direction, for drawing. */
    internal var pivotX = 0f
    internal var pivotY = 0f

    /** +1 when the cat faces right, -1 left, while held. */
    internal var holderDir = 1

    /** Progress of the lift, 0..1, and where it began. */
    internal var liftT = 0f
    internal var liftX = 0f
    internal var liftY = 0f

    /** The thread of a yarn ball: points x, y, and the points before them (Verlet). */
    val thread = FloatArray(THREAD_POINTS * 2)
    internal val threadPrev = FloatArray(THREAD_POINTS * 2)

    /** How many thread points are valid (0 when the toy has no thread). */
    var threadCount = 0
        private set

    // ---- drawing outputs
    var drawX = 0f
        private set

    var drawY = 0f
        private set

    var drawAngle = 0f
        private set

    var drawScaleX = 1f
        private set

    var drawScaleY = 1f
        private set

    var depthScale = 1f
        private set

    var pivotU = kind.width / 2f
        private set

    var pivotV = kind.height / 2f
        private set

    /** How high the toy's lowest point is above the ground line under it, px. */
    var height = 0f
        private set

    init {
        if (kind.thread) threadCount = THREAD_POINTS
        resetThread()
    }

    /** Put the toy down at rest on [on] at [x], depth [z] (the floor row is found for it). */
    fun place(x: Float, on: SurfaceId = SurfaceId.FLOOR, z: Float = World.ART_Z) {
        this.x = x
        this.surface = on
        this.z = if (on == SurfaceId.FLOOR) z else World.surface(on).z
        y = restY()
        vx = 0f
        vy = 0f
        vz = 0f
        spin = 0f
        angle = 0f
        state = ToyState.REST
        airborne = false
        overCat = false
        zGoal = Float.NaN
        stillFor = 0f
        squashX.snap(0f)
        squashY.snap(0f)
        resetThread()
        compose()
    }

    /** Centre row when the toy sits on its surface at its depth. */
    fun restY(): Float = World.groundY(surface, z) - kind.halfHeight * World.depthScale(z)

    /** Row of the toy's lowest point. */
    fun footY(): Float = y + kind.halfHeight * World.depthScale(z)

    /** True when a cat may pick the toy up: lying on a surface, or all but still. */
    val canBePicked: Boolean
        get() =
            state == ToyState.REST || (state == ToyState.FREE && !airborne && speed() < PICK_SPEED)

    /** Speed in px/s. */
    fun speed(): Float = Mathx.abs(vx) + Mathx.abs(vy)

    /** Throw the toy: it is free from now on, moving at ([vx], [vy]) and turning at [spin]. */
    fun launch(vx: Float, vy: Float, spin: Float = 0f) {
        this.vx = vx
        this.vy = vy
        this.spin = spin
        state = ToyState.FREE
        airborne = vy < 0f || footY() < World.groundY(surface, z) - AIR_EPS
        stillFor = 0f
        overCat = false
        if (vx > 1f) mirrored = false else if (vx < -1f) mirrored = true
    }

    /** Make the thread lie straight behind the toy (after a placement or a catch). */
    internal fun resetThread() {
        for (i in 0 until THREAD_POINTS) {
            thread[i * 2] = x - (i + 1) * THREAD_SEGMENT
            thread[i * 2 + 1] = y + kind.halfHeight
            threadPrev[i * 2] = thread[i * 2]
            threadPrev[i * 2 + 1] = thread[i * 2 + 1]
        }
    }

    /** Fill the draw outputs from the state; call after every change of state or place. */
    internal fun compose() {
        val k = World.depthScale(z)
        depthScale = k
        val sx = 1f + Mathx.clamp(squashX.pos, -ToyTuning.SQUASH_MAX, ToyTuning.SQUASH_MAX)
        val sy = 1f + Mathx.clamp(squashY.pos, -ToyTuning.SQUASH_MAX, ToyTuning.SQUASH_MAX)
        drawScaleX = if (mirrored) -sx else sx
        drawScaleY = sy
        if (state == ToyState.HELD && kind.hold == HoldStyle.HANG) {
            drawX = pivotX
            drawY = pivotY
            pivotU = kind.gripU
            pivotV = kind.gripV
            val dx = x - pivotX
            val dy = y - pivotY
            val rod = atan2(dy, dx)
            val rest = if (mirrored) Mathx.PI - kind.restAngle else kind.restAngle
            drawAngle = rod - rest
        } else {
            drawX = x
            drawY = y
            pivotU = kind.width / 2f
            pivotV = kind.height / 2f
            drawAngle = angle
        }
        height = maxOf(0f, World.groundY(surface, z) - footY())
    }

    companion object {
        /** Points in a yarn thread, and the length of one piece, px. */
        const val THREAD_POINTS = 6
        const val THREAD_SEGMENT = 2.6f

        /** A free toy slower than this may be picked up. */
        const val PICK_SPEED = 25f
        private const val AIR_EPS = 0.05f
    }
}
