package com.lifemax.rpg.feature.pet.body

/**
 * One living cat in a small strip of floor: a [CatBody] and the [Autopilot] that keeps it busy,
 * stepped together. This is everything the Overview card needs besides drawing, and it has no
 * Android types, so it can be stepped to any moment in a test.
 *
 * @param seed the cat's own seed (see [Autopilot.seedFor]).
 * @param weak the hungry look: slower, restful, drawn with the `_w` rows.
 * @param minX left end of the strip the cat may walk in, in room pixels.
 * @param maxX right end.
 * @param autopilot false hands the body to the caller (tests and previews script its plans).
 */
class CatLife(
    seed: Int,
    weak: Boolean,
    val minX: Float = STRIP_MIN_X,
    val maxX: Float = STRIP_MAX_X,
    autopilot: Boolean = true,
) {
    private val pilot = Autopilot(seed, minX, maxX, weak)

    /** The cat's body; its [CatBody.pose] is what to draw. */
    val body = CatBody(seed, pilot.startX)

    private val driven = autopilot

    init {
        body.weak = weak
        body.placeAt(pilot.startX, World.ART_Z, SurfaceId.FLOOR, pilot.startFacing)
    }

    /** The latest pose, refilled in place on every [step]. */
    val pose: CatPose
        get() = body.pose

    /** Advance by [dt] seconds of host time. Allocation free except when the autopilot plans. */
    fun step(dt: Float) {
        body.step(dt)
        if (driven) pilot.update(body, dt)
    }

    /** Step [seconds] at [hz] frames per second, as a host would; for tests and previews. */
    fun advance(seconds: Float, hz: Int = 60) {
        val dt = 1f / hz
        repeat(Math.round(seconds * hz)) { step(dt) }
    }

    companion object {
        /** The strip of the room the card shows: 192 room pixels wide, the cat's feet inside it. */
        const val WINDOW_WIDTH = 192f

        /** Where the cat's feet may go: its 72 px frame keeps a margin inside the window. */
        const val STRIP_MIN_X = 46f
        const val STRIP_MAX_X = 146f
    }
}
