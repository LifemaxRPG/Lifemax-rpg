package com.lifemax.rpg.feature.pet.body

/**
 * The whole-body springs: squash and stretch on each axis, a rotation, and a vertical bob. Pixel
 * art cannot cross-fade, so every change of posture, every footfall and every landing is a nudge to
 * these springs, which then overshoot and settle in about a tenth of a second.
 *
 * Squash and stretch conserve volume loosely: whatever one axis loses the other gains a share of.
 */
internal class BodySprings {
    val squashX = Spring(Tuning.Springs.SQUASH)
    val squashY = Spring(Tuning.Springs.SQUASH)
    val rotation = Spring(Tuning.Springs.ROTATION)
    val bob = Spring(Tuning.Springs.BOB)

    /** Body scale on x: 1 at rest, kept inside sane bounds so a pile-up of kicks cannot distort. */
    val scaleX: Float
        get() = 1f + Mathx.clamp(squashX.pos, -Tuning.Squash.LIMIT, Tuning.Squash.LIMIT)

    val scaleY: Float
        get() = 1f + Mathx.clamp(squashY.pos, -Tuning.Squash.LIMIT, Tuning.Squash.LIMIT)

    /** Advance one tick; [rotationTarget] is the lean the motion asks for. */
    fun step(dt: Float, rotationTarget: Float) {
        squashX.step(0f, dt)
        squashY.step(0f, dt)
        rotation.step(rotationTarget, dt)
        bob.step(0f, dt)
    }

    /**
     * Settle into a posture of a different height: dropping into a loaf compresses the body, rising
     * to a stand stretches it. The change of height becomes a velocity, so the spring does the
     * rest.
     */
    fun kickPosture(fromHeight: Float, toHeight: Float) {
        val dh = toHeight - fromHeight
        if (Mathx.abs(dh) < Tuning.Squash.POSTURE_DEADBAND) return
        squashY.kick(dh * Tuning.Squash.POSTURE_KICK)
        squashX.kick(-dh * Tuning.Squash.POSTURE_KICK * Tuning.Squash.VOLUME_SHARE)
    }

    /** A sharp squash of [strength] (1 is a normal landing): short and wide, then spring back. */
    fun squash(strength: Float) {
        squashY.displace(-Tuning.Squash.LAND_Y * strength)
        squashX.displace(Tuning.Squash.LAND_Y * strength * Tuning.Squash.VOLUME_SHARE)
    }

    /** A sudden stretch of [strength]: tall and thin, as at launch or a startle. */
    fun stretch(strength: Float) {
        squashY.displace(Tuning.Squash.STRETCH_Y * strength)
        squashX.displace(-Tuning.Squash.STRETCH_Y * strength * Tuning.Squash.VOLUME_SHARE)
    }

    /** A small downward tap of the body, as a paw hits the ground. */
    fun footfall(speedShare: Float) {
        bob.kick(-Tuning.Bob.FOOTFALL_KICK * speedShare)
    }

    fun reset() {
        squashX.snap(0f)
        squashY.snap(0f)
        rotation.snap(0f)
        bob.snap(0f)
    }
}
