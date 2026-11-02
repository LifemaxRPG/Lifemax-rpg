package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.body.CatBody
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * The room's camera: a horizontal pan that eases after a point of interest, as the web room's does
 * (`petCamUpdate` in 17-runtime.js). The camera keeps `cx`, the world x at the centre of the view.
 * - It leads a little in the direction the cat walks ([LEAD] px, [RUN_LEAD] when running).
 * - It follows at a rate that depends on what the cat does: slowly when resting, faster when
 *   walking, faster still when running; the pan is an exponential ease, so it is independent of the
 *   frame rate.
 * - A cat that rests never makes the room drift: while nothing travels, offsets under [DEAD_ZONE]
 *   are left alone, and once the camera has started it settles on a whole pixel.
 * - A jump of more than [CUT] px (a cat put somewhere new) is a cut, not a pan.
 * - [left] keeps the view inside the house.
 *
 * Plain arithmetic on a few floats: nothing here allocates.
 */
internal class RoomCamera(startX: Float = 0f) {
    /** World x at the centre of the view. */
    var cx = startX
        private set

    private var going = false

    /** Put the camera at [x] at once. */
    fun cutTo(x: Float) {
        cx = x
        going = false
    }

    /**
     * Move toward [target] (a world x, with any lead already added) for [dt] seconds. [moving] and
     * [running] say how the followed cat is travelling.
     */
    fun update(target: Float, moving: Boolean, running: Boolean, dt: Float) {
        val d = target - cx
        if (abs(d) > CUT) {
            cx = target
            going = false
            return
        }
        if (!moving && !going && abs(d) < DEAD_ZONE) return
        going = true
        val rate = if (running) RUN_RATE else if (moving) WALK_RATE else REST_RATE
        cx += d * (1f - exp(-rate * dt))
        if (!moving && abs(target - cx) < SETTLE) {
            cx = target.roundToInt().toFloat()
            going = false
        }
    }

    /** Left edge of a view [viewWidth] world pixels wide, kept inside the house. */
    fun left(viewWidth: Float): Float {
        val lo = RoomLayout.HOUSE_X0.toFloat()
        val hi = lo + RoomLayout.HOUSE_WIDTH - viewWidth
        return if (hi <= lo) lo else (cx - viewWidth / 2f).coerceIn(lo, hi)
    }

    companion object {
        const val LEAD = 14f
        const val RUN_LEAD = 26f
        const val CUT = 260f
        const val DEAD_ZONE = 14f
        const val SETTLE = 0.6f
        const val REST_RATE = 1.6f
        const val WALK_RATE = 2.6f
        const val RUN_RATE = 4.2f
    }
}

/**
 * Decides what the card's camera looks at with one or two cats in the house.
 * - One cat: the web's own rule, following it with a lead.
 * - Two cats close together (within [CLOSE] px, which fits both in a 192 px window): the midpoint
 *   between them, no lead, so the pair stays framed as they move about each other.
 * - Two cats far apart: the one that moves. The camera stays with a cat until it has been still for
 *   [PATIENCE] seconds while the other travels, so a cat that stops for a moment is not abandoned,
 *   and two cats walking at once do not make the view flick between them. When neither moves the
 *   camera stays where it is.
 */
internal class CameraDirector(val camera: RoomCamera = RoomCamera()) {
    private var focus = 0
    private var stillFor = 0f
    private var placed = false

    /** Follow [first] (and [second], if any) for [dt] seconds. */
    fun update(first: CatBody, second: CatBody?, dt: Float) {
        if (!placed) {
            placed = true
            camera.cutTo(startTarget(first, second))
        }
        if (second == null) {
            followOne(first, dt)
            return
        }
        val a = first.pose.x
        val b = second.pose.x
        if (abs(a - b) <= CLOSE) {
            val moving = first.isTravelling || second.isTravelling
            camera.update((a + b) / 2f, moving, first.isRunning || second.isRunning, dt)
            return
        }
        choose(first, second, dt)
        followOne(if (focus == 0) first else second, dt)
    }

    private fun startTarget(first: CatBody, second: CatBody?): Float =
        if (second != null && abs(first.pose.x - second.pose.x) <= CLOSE) {
            (first.pose.x + second.pose.x) / 2f
        } else {
            first.pose.x
        }

    private fun choose(first: CatBody, second: CatBody, dt: Float) {
        val mine = if (focus == 0) first else second
        val other = if (focus == 0) second else first
        if (mine.isTravelling || !other.isTravelling) {
            stillFor = 0f
            return
        }
        stillFor += dt
        if (stillFor >= PATIENCE) {
            focus = 1 - focus
            stillFor = 0f
        }
    }

    private fun followOne(cat: CatBody, dt: Float) {
        val moving = cat.isTravelling
        val lead = if (cat.isRunning) RoomCamera.RUN_LEAD else RoomCamera.LEAD
        val target = cat.pose.x + if (moving) cat.pose.facing * lead else 0f
        camera.update(target, moving, cat.isRunning, dt)
    }

    companion object {
        /** Two cats this close (feet to feet) share the frame. */
        const val CLOSE = 110f
        const val PATIENCE = 0.6f
    }
}
