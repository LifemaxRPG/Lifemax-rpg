package com.lifemax.rpg.feature.pet.body

import com.lifemax.rpg.feature.pet.art.LabTable

/**
 * Where the cat's mouth is in each frame of each clip, in frame pixels (the 72x48 sheet frame, side
 * view facing right). The lab rows (`pickup`, `carry`, `toss`, ...) carry measured anchors per
 * frame (`lab.json`, built from the art's head point); every other clip falls back to a table of
 * estimates made from the lab rows that start from the same source frames, so a toy always has a
 * mouth to hang from. Build one with [of].
 */
class MouthAnchors
private constructor(
    private val us: Array<FloatArray>,
    private val vs: Array<FloatArray>,
    private val measured: Array<BooleanArray>,
) {
    /** True when the anchor of [frame] in [clip] was measured from the art (not estimated). */
    fun isMeasured(clip: Int, frame: Int): Boolean = measured[clip][index(clip, frame)]

    /** Frame pixel x of the mouth of [frame] in the clip with id [clip]. */
    fun u(clip: Int, frame: Int): Float = us[clip][index(clip, frame)]

    /** Frame pixel y of the mouth of [frame] in the clip with id [clip]. */
    fun v(clip: Int, frame: Int): Float = vs[clip][index(clip, frame)]

    private fun index(clip: Int, frame: Int): Int = Math.floorMod(frame, us[clip].size)

    companion object {
        /** The sheet frame is 72 wide; the cat's feet are at its middle. */
        const val FRAME_HALF_WIDTH = 36f

        /**
         * Anchors for the cat at growth [stage] ([com.lifemax.rpg.feature.pet.art.Growth.ADULT] or
         * a kitten stage) from the lab table [lab] (null: estimates only).
         */
        fun of(lab: LabTable?, stage: Int): MouthAnchors {
            val clips = Clips.all
            val us = Array(clips.size) { FloatArray(0) }
            val vs = Array(clips.size) { FloatArray(0) }
            val measured = Array(clips.size) { BooleanArray(0) }
            for (clip in clips) {
                val row = lab?.get(clip.name)
                val n = clip.frames
                us[clip.id] = FloatArray(n)
                vs[clip.id] = FloatArray(n)
                measured[clip.id] = BooleanArray(n)
                val guess = estimate(clip.name)
                for (f in 0 until n) {
                    val p = row?.mouth(stage, f)
                    us[clip.id][f] = p?.x ?: guess.first
                    vs[clip.id][f] = p?.y ?: guess.second
                    measured[clip.id][f] = p != null
                }
            }
            return MouthAnchors(us, vs, measured)
        }

        /** Estimates only: what a test or a host without the lab table uses. */
        fun estimated(): MouthAnchors = of(null, 0)

        /**
         * The mouth of an adult in a clip without a lab row, frame px, from the matching lab
         * frames.
         */
        private fun estimate(name: String): Pair<Float, Float> =
            when (name) {
                "walk",
                "walk_w" -> Pair(55f, 25f)
                "run" -> Pair(57f, 25f)
                "sleep",
                "sleep_w" -> Pair(50f, 40f)
                "loaf",
                "loaf_w" -> Pair(50f, 34f)
                "groom" -> Pair(47f, 26f)
                "eat" -> Pair(58f, 38f)
                "play" -> Pair(54f, 33f)
                "swat" -> Pair(60f, 36f)
                "stretch" -> Pair(58f, 34f)
                "lookup" -> Pair(50f, 18f)
                "rub" -> Pair(54f, 30f)
                "knead" -> Pair(50f, 30f)
                "leap" -> Pair(56f, 25f)
                "crouch" -> Pair(54f, 36f)
                "scratch" -> Pair(50f, 26f)
                "roll" -> Pair(36f, 24f)
                "kick" -> Pair(40f, 30f)
                "bat" -> Pair(54f, 28f)
                "walk_front",
                "walk_back",
                "sniff",
                "bonk" -> Pair(FRAME_HALF_WIDTH, 24f)
                else -> Pair(50f, 22f)
            }

        /**
         * Where a frame point ([u], [v]) of a clip whose feet row is [bottom] is in room pixels,
         * for a cat standing at ([footX], [footY]) drawn as the pose says: mirrored by [flip],
         * scaled by [scaleX] and [scaleY], turned by [rotation] about the feet and sized by
         * [depthScale]. This is the exact transform of the sprite drawing (`drawSpriteAt`), so the
         * point sits on the same pixel of the cat's face the screen shows. Writes [out] (x, y).
         */
        fun locate(
            u: Float,
            v: Float,
            bottom: Int,
            flip: Boolean,
            scaleX: Float,
            scaleY: Float,
            rotation: Float,
            depthScale: Float,
            footX: Float,
            footY: Float,
            out: FloatArray,
        ) {
            val side = if (flip) -1f else 1f
            val lx = (u - FRAME_HALF_WIDTH) * scaleX * side
            val ly = (v - bottom + 1f) * scaleY
            val c = Mathx.cos(rotation)
            val s = Mathx.sin(rotation)
            out[0] = footX + (lx * c - ly * s) * depthScale
            out[1] = footY + (lx * s + ly * c) * depthScale
        }
    }
}
