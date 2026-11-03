package com.lifemax.rpg.feature.pet.art

import org.json.JSONArray
import org.json.JSONObject

/** A point in frame pixels (side view facing +x; mirror it with the cat). */
data class FramePoint(val x: Float, val y: Float)

/**
 * The metadata of one lab row (`petAssets.lab.rows`, built by `tools/pet_art/build.js`; see
 * docs/specs/PET_ART_LAB.md).
 *
 * @property name the row's name, also its clip name.
 * @property fps the base frame rate.
 * @property loop true for cycles (carry, groom_other); false rows play once and hold the last
 *   frame.
 * @property planted frames on which the paws stand on the floor (the body must not move the cat
 *   vertically then; for carry they are the two footfalls of the stride).
 * @property chainFrom rows this one is meant to follow.
 * @property chainTo rows that may follow it.
 * @property flipAtEnd true for `turn`: all its frames are drawn with the old facing (the last one
 *   is already mirrored in the sheet) and the facing flips when it ends.
 * @property bite pickup: the frame on which the toy is taken into the mouth, or -1.
 * @property release toss: the frame on which the toy leaves the mouth, or -1.
 * @property touch bump, nose_touch: the frame of contact, or -1.
 */
class LabRow(
    val name: String,
    val fps: Double,
    val loop: Boolean,
    val planted: IntArray,
    val chainFrom: List<String>,
    val chainTo: List<String>,
    val flipAtEnd: Boolean,
    val bite: Int,
    val release: Int,
    val touch: Int,
    private val mouths: Map<Int, List<FramePoint?>>,
) {
    /**
     * The mouth (toy) anchor of [frame] at [stage] ([Growth.ADULT] or a kitten stage 0..5), in
     * frame pixels, or null if that frame has no face point.
     */
    fun mouth(stage: Int, frame: Int): FramePoint? {
        val list = mouths[stage] ?: return null
        return list[Math.floorMod(frame, list.size)]
    }

    /** Frames of the row (as many as it has mouth anchors). */
    val frames: Int
        get() = mouths[Growth.ADULT]?.size ?: 0
}

/**
 * A pose for two cats (`petAssets.lab.pairs`): cat A plays [a], cat B plays [b] with its frame
 * origin [offset] pixels from A's (x along A's facing; B faces the other way when [mirrorB]), drawn
 * behind A unless [bFront].
 */
class LabPair(
    val name: String,
    val a: String,
    val b: String,
    val mirrorB: Boolean,
    val bFront: Boolean,
    val contactFrame: Int,
    private val offsets: Map<Int, FramePoint>,
) {
    /** B's frame origin relative to A's at [stage] (frame pixels, x along A's facing). */
    fun offset(stage: Int): FramePoint = offsets[stage] ?: offsets.getValue(Growth.ADULT)
}

/**
 * The lab rows' metadata and the pair poses, as `tools/gen_pet_native_assets.js` writes `lab.json`.
 */
class LabTable(val rows: Map<String, LabRow>, val pairs: Map<String, LabPair>) {

    /** The row called [name], or null if it is not a lab row. */
    operator fun get(name: String): LabRow? = rows[name]

    companion object {
        private const val KIND_ADULT = "adult"
        private const val KIND_STAGE = "st"

        /** The stage index a kind name stands for: adult, st0..st5. */
        private fun stageOf(kind: String): Int =
            if (kind == KIND_ADULT) Growth.ADULT else kind.removePrefix(KIND_STAGE).toInt()

        private fun ints(a: JSONArray?): IntArray =
            if (a == null) IntArray(0) else IntArray(a.length()) { a.getInt(it) }

        private fun strings(a: JSONArray?): List<String> =
            if (a == null) emptyList() else List(a.length()) { a.getString(it) }

        private fun point(a: JSONArray?): FramePoint? =
            if (a == null) null else FramePoint(a.getDouble(0).toFloat(), a.getDouble(1).toFloat())

        private fun row(name: String, o: JSONObject): LabRow {
            val chain = o.optJSONObject("chain")
            val mouth = o.getJSONObject("mouth")
            val mouths =
                mouth.keys().asSequence().associate { kind ->
                    val list = mouth.getJSONArray(kind)
                    stageOf(kind) to List(list.length()) { point(list.optJSONArray(it)) }
                }
            return LabRow(
                name,
                o.getDouble("fps"),
                o.getBoolean("loop"),
                ints(o.optJSONArray("contacts")),
                strings(chain?.optJSONArray("from")),
                strings(chain?.optJSONArray("to")),
                o.optBoolean("flipAtEnd", false),
                o.optInt("bite", -1),
                o.optInt("release", -1),
                o.optInt("touch", -1),
                mouths,
            )
        }

        private fun pair(name: String, o: JSONObject): LabPair {
            val off = o.getJSONObject("offset")
            val offsets =
                off.keys().asSequence().associate { kind ->
                    val p = off.getJSONObject(kind)
                    stageOf(kind) to FramePoint(p.getInt("dx").toFloat(), p.getInt("dy").toFloat())
                }
            return LabPair(
                name,
                o.getString("a"),
                o.getString("b"),
                o.getBoolean("mirrorB"),
                o.getBoolean("bFront"),
                o.getInt("contactFrame"),
                offsets,
            )
        }

        /** Reads `lab.json`. */
        fun parse(text: String): LabTable {
            val json = JSONObject(text)
            val rows = json.getJSONObject("rows")
            val pairs = json.getJSONObject("pairs")
            return LabTable(
                rows.keys().asSequence().associateWith { row(it, rows.getJSONObject(it)) },
                pairs.keys().asSequence().associateWith { pair(it, pairs.getJSONObject(it)) },
            )
        }
    }
}
