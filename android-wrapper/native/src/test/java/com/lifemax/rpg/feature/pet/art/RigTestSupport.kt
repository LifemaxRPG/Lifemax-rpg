package com.lifemax.rpg.feature.pet.art

import com.lifemax.rpg.feature.pet.body.Clips
import org.json.JSONObject

/** The shipped art and rig, and the recorded web renders (`tools/gen_pet_rig_fixtures.js`). */
object RigTestSupport {
    val art: PetArt by lazy { PetArtResources.load() }
    val library: RigLibrary by lazy { PetArtResources.loadRig() }

    /** The rig set of [coat] at kitten [stage] (-1 grown) with [morph], with the depth rows. */
    fun set(coat: String, stage: Int, morph: RigMorph, id: Int = 1): RigSet {
        fun layer(depth: Boolean): RigLayer {
            val kind = SheetKind.of(stage, depth)
            return RigLayer(library.kind(kind.key)!!, art.sheet(coat, kind)!!, art.palette(coat)!!)
        }
        val anims =
            Array(Clips.all.size) { art.growth.resolve(stage, Clips.all[it].name, art.anims)!! }
        return RigSet(id, morph, stage >= 0, art.anims, anims, layer(false), layer(true))
    }

    /** One recorded case: the web's inputs and the picture it composed. */
    class WebCase(
        val kind: String,
        val coat: String,
        val anim: String,
        val frame: Int,
        val pose: RigPose,
        val morph: RigMorph,
        val picture: IntArray,
    ) {
        /** Kitten stage of the kind (`st2`, `dst2` is 2), or -1. */
        val stage: Int
            get() = kind.trimStart('d', 's', 't').toIntOrNull() ?: -1

        override fun toString() = "$kind $coat $anim#$frame"
    }

    val web: List<WebCase> by lazy {
        val json =
            JSONObject(
                String(
                    RigTestSupport::class
                        .java
                        .getResourceAsStream("/pet-rig/cases.json")!!
                        .readBytes()
                )
            )
        val atlas =
            PngImage.decode(
                    RigTestSupport::class
                        .java
                        .getResourceAsStream("/pet-rig/atlas.png")!!
                        .readBytes()
                )
                .toArgb()
        val columns = json.getInt("columns")
        val cw = json.getInt("cellWidth")
        val ch = json.getInt("cellHeight")
        val cases = json.getJSONArray("cases")
        List(cases.length()) { i ->
            val c = cases.getJSONObject(i)
            val pose = c.getJSONObject("pose")
            val m = c.getJSONObject("morph")
            val cell = c.getInt("cell")
            val ox = (cell % columns) * cw
            val oy = (cell / columns) * ch
            WebCase(
                c.getString("kind"),
                c.getString("coat"),
                c.getString("anim"),
                c.getInt("frame"),
                RigPose().apply {
                    earLeft = pose.getDouble("ear0")
                    earRight = pose.getDouble("ear1")
                    flat = pose.getDouble("flat")
                    tail = pose.getDouble("tail")
                },
                RigMorph(
                    m.getDouble("em"),
                    m.getDouble("ew"),
                    m.getDouble("tl"),
                    m.getDouble("lg"),
                    m.getDouble("bl"),
                    m.getDouble("wk"),
                    m.getBoolean("bob"),
                ),
                IntArray(cw * ch) { atlas[ox + it % cw, oy + it / cw] },
            )
        }
    }

    /** The art frame of [case] as the app draws it. */
    fun source(case: WebCase): Pair<IntArray, RigFrame> {
        val stage = case.stage
        val anim = art.growth.resolve(stage, case.anim, art.anims)!!
        val kind = SheetKind.of(stage, anim.depth)
        val rig = library.kind(kind.key)!!
        val pixels = art.frame(case.coat, stage, case.anim, case.frame)!!.pixels
        return pixels to rig.frame(rig.indexOf(anim.name), case.frame)
    }
}
