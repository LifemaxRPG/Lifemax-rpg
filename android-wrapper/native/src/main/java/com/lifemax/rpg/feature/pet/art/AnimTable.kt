package com.lifemax.rpg.feature.pet.art

import org.json.JSONObject

/**
 * One animation row of a cat sheet.
 *
 * @property name the web's animation name (`idle_sit`, `walk`, ..., or a depth row such as
 *   `walk_front`)
 * @property row the row of the sheet this animation lives on (frames run left to right)
 * @property frames how many frames the row has
 * @property fps the playback rate the web uses for the row (frames per second)
 * @property bottom the row of a frame (0 is the top) where the paws stand; the cat is anchored by
 *   it
 * @property depth true for the depth rows (front and back views), which live on the depth sheet
 */
data class Anim(
    val name: String,
    val row: Int,
    val frames: Int,
    val fps: Double,
    val bottom: Int,
    val depth: Boolean = false,
)

/** Where a frame sits in its sheet, in sheet pixels. */
data class FrameRect(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * The cat's animation rows: the 28 adult rows of the main sheet and the 4 depth rows of the depth
 * sheet, in the web's order (`petAssets.anims` and `petAssets.depth.anims`).
 *
 * Every frame is [frameWidth] x [frameHeight] pixels; the main sheet is [adultColumns] frames wide,
 * the depth sheet [depthColumns]. Names are unique across both lists.
 */
class AnimTable(
    val frameWidth: Int,
    val frameHeight: Int,
    val adultColumns: Int,
    val depthColumns: Int,
    val adult: List<Anim>,
    val depth: List<Anim>,
) {
    private val byName: Map<String, Anim> = (adult + depth).associateBy { it.name }

    /** Every animation name, adult rows first, each list in sheet order. */
    val names: List<String>
        get() = adult.map { it.name } + depth.map { it.name }

    /** The animation called [name], or null. */
    operator fun get(name: String): Anim? = byName[name]

    /** True if [name] is a depth row (front or back view, sniff, bonk). */
    fun isDepth(name: String): Boolean = byName[name]?.depth == true

    /**
     * The frame [frame] of [anim] in its sheet. The index wraps like the web's `% n`, and a
     * negative index counts from the end, so any integer is a valid frame number.
     */
    fun frameRect(anim: Anim, frame: Int): FrameRect =
        FrameRect(
            Math.floorMod(frame, anim.frames) * frameWidth,
            anim.row * frameHeight,
            frameWidth,
            frameHeight,
        )

    /** The frame the web shows for a clock: `floor(seconds * fps) % frames`. */
    fun frameAt(anim: Anim, seconds: Double): Int =
        Math.floorMod(Math.floor(seconds * anim.fps).toLong(), anim.frames.toLong()).toInt()

    companion object {
        /** Reads `anims.json` as `tools/gen_pet_native_assets.js` writes it. */
        fun parse(text: String): AnimTable {
            val json = JSONObject(text)

            fun rows(key: String, depth: Boolean): List<Anim> {
                val array = json.getJSONArray(key)
                return List(array.length()) {
                    val a = array.getJSONObject(it)
                    Anim(
                        a.getString("name"),
                        a.getInt("row"),
                        a.getInt("frames"),
                        a.getDouble("fps"),
                        a.getInt("bottom"),
                        depth,
                    )
                }
            }
            return AnimTable(
                json.getInt("frameWidth"),
                json.getInt("frameHeight"),
                json.getInt("adultColumns"),
                json.getInt("depthColumns"),
                rows("adult", depth = false),
                rows("depth", depth = true),
            )
        }
    }
}
