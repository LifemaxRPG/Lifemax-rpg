package com.lifemax.rpg.feature.pet.art

import org.json.JSONObject

/**
 * One growth stage of a kitten.
 *
 * @property index 0..5
 * @property fromDay the first day of age the stage covers (stage i covers days 5i to 5i+4)
 * @property age the fraction of growth reached at [fromDay] (`fromDay / growDays`)
 * @property scale the web's `sx`: 0.6 for the smallest kitten up to 0.9333; the kitten art is
 *   already drawn small, so this only sizes the shadow (and the adult art when a depth kitten sheet
 *   is not there yet)
 * @property bottom the foot row of each adult animation at this stage; it differs from the adult
 *   table and has no `scratch` entry
 */
data class KittenStage(
    val index: Int,
    val fromDay: Int,
    val age: Double,
    val scale: Double,
    val bottom: Map<String, Int>,
)

/**
 * The kitten growth stages (`petGrowth.stages`) and the rules that pick and apply one.
 *
 * A cat is a kitten for [growDays] days. Native draws a kitten from the stage sheets of [stage];
 * once grown ([ADULT]) it uses the adult sheets.
 */
class Growth(val growDays: Int, val stages: List<KittenStage>) {

    /**
     * The stage for a cat that is [ageDays] days old, or [ADULT] (-1) once it is [growDays] old:
     * the web's `petGrowIdx`, `min(5, floor(days / 5))`. A negative age counts as 0.
     */
    fun stageForAge(ageDays: Double): Int =
        if (ageDays >= growDays) ADULT
        else minOf(stages.size - 1, Math.floor(maxOf(0.0, ageDays) / DAYS_PER_STAGE).toInt())

    /** The web's `sx` for [stage]; 1.0 for an adult. */
    fun scale(stage: Int): Double = stages.getOrNull(stage)?.scale ?: 1.0

    /**
     * The animation the web draws for [name] at [stage], as [table] knows it. An adult, or a depth
     * row, is the table's own. A kitten stage has its own foot row, and a name the stage has no
     * foot row for (`scratch`) is drawn as `idle_sit`, row, frames, fps and foot row alike
     * (`petDrawCat`). An unknown name is `idle_sit` too. Returns null only if [table] has no
     * `idle_sit`.
     */
    fun resolve(stage: Int, name: String, table: AnimTable): Anim? {
        val own = table[name]
        if (own != null && own.depth) return own
        val idle = table["idle_sit"]
        val base = own ?: idle ?: return null
        val feet = stages.getOrNull(stage)?.bottom ?: return base
        val idleFeet = feet["idle_sit"]
        val bottom = feet[name]
        return when {
            bottom != null -> base.copy(bottom = bottom)
            idleFeet != null && idle != null -> idle.copy(bottom = idleFeet)
            else -> base
        }
    }

    companion object {
        /** The stage number of a grown cat: it draws the adult sheets. */
        const val ADULT = -1

        private const val DAYS_PER_STAGE = 5.0

        /** Reads `growth.json` as `tools/gen_pet_native_assets.js` writes it. */
        fun parse(text: String): Growth {
            val json = JSONObject(text)
            val array = json.getJSONArray("stages")
            val stages =
                List(array.length()) {
                    val s = array.getJSONObject(it)
                    val feet = s.getJSONObject("bottom")
                    KittenStage(
                        s.getInt("index"),
                        s.getInt("fromDay"),
                        s.getDouble("age"),
                        s.getDouble("scale"),
                        feet.keys().asSequence().associateWith { name -> feet.getInt(name) },
                    )
                }
            return Growth(json.getInt("growDays"), stages)
        }
    }
}
