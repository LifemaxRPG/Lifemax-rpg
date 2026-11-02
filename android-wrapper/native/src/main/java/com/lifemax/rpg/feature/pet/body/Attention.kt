package com.lifemax.rpg.feature.pet.body

/**
 * Where the cat is looking. A look target (the finger, a toy, a bird at the window) becomes a
 * normalised look vector, a body lean and an ear bias. There is a short reaction delay before the
 * cat notices a new target, and the output rides on springs so the head leads and settles.
 *
 * The head is not a rig part yet, so this reports intent and does not move any art: [x] is -1
 * (looking back over the shoulder) to +1 (straight ahead and beyond) in the direction the cat
 * faces, [y] is -1 (up) to +1 (down, toward the viewer). The renderer decides what to do with it.
 */
internal class Attention {
    private val lookX = Spring(Tuning.Springs.LOOK)
    private val lookY = Spring(Tuning.Springs.LOOK)
    private val lean = Spring(Tuning.Springs.LEAN)
    private var tracking = false
    private var targetX = 0f
    private var targetZ = 0f
    private var noticeIn = 0f
    private var glanceX = 0f
    private var glanceY = 0f
    private var glanceLeft = 0f

    /** Look direction along the way the cat faces (soft-limited to -1..1). */
    val x: Float
        get() = lookX.pos

    /** Look direction up (-1) or down (+1). */
    val y: Float
        get() = lookY.pos

    /** Body lean toward the target in radians (positive leans the way the cat faces). */
    val leanRadians: Float
        get() = lean.pos

    /** True while there is a target the cat has noticed. */
    val locked: Boolean
        get() = tracking && noticeIn <= 0f

    /** Start or update a look target at room position ([x], [z]). */
    fun lookAt(x: Float, z: Float) {
        if (!tracking) noticeIn = Tuning.Look.REACTION_DELAY_S
        tracking = true
        targetX = x
        targetZ = z
    }

    /** Stop looking at anything. */
    fun clear() {
        tracking = false
    }

    /** A brief look away or up ([x], [y] in look units) that fades after [seconds]. */
    fun glance(x: Float, y: Float, seconds: Float) {
        glanceX = x
        glanceY = y
        glanceLeft = seconds
    }

    /** Advance one tick for a cat at ([catX], [catZ]) facing [facing] (+1 or -1). */
    fun update(dt: Float, catX: Float, catZ: Float, facing: Int) {
        var aimX = 0f
        var aimY = 0f
        if (tracking) {
            noticeIn -= dt
            if (noticeIn <= 0f) {
                val dx = (targetX - catX) * facing
                val dz = targetZ - catZ
                aimX = dx / (Mathx.abs(dx) + Tuning.Look.SOFT_X_PX)
                aimY = dz / (Mathx.abs(dz) + Tuning.Look.SOFT_Z_UNITS)
            }
        }
        if (glanceLeft > 0f) {
            glanceLeft -= dt
            aimX += glanceX
            aimY += glanceY
        }
        val clampedX = Mathx.clamp(aimX, -1f, 1f)
        lookX.step(clampedX, dt)
        lookY.step(Mathx.clamp(aimY, -1f, 1f), dt)
        lean.step(clampedX * Tuning.Look.LEAN_RADIANS, dt)
    }

    fun reset() {
        tracking = false
        glanceLeft = 0f
        lookX.snap(0f)
        lookY.snap(0f)
        lean.snap(0f)
    }
}
