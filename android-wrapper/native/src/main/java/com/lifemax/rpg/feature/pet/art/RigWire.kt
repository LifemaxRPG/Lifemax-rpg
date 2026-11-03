package com.lifemax.rpg.feature.pet.art

import org.json.JSONObject

/**
 * Reading a cat's proportions as the page sends them (`pet.rig.v1`, `overview.cat.rig.v1`): an
 * object with exactly the keys `em` (ear length), `ew` (ear width), `tl` (tail), `lg` (legs), `bl`
 * (body), `wk` (whiskers) and `bob` (a bobtail), the multipliers between 0.35 and 2.8 the page's
 * `petRigWire` computed from the cat's genes and age. Anything else throws, so a page that changed
 * the shape is refused rather than drawn wrong.
 */
object RigWire {
    private val KEYS = setOf("em", "ew", "tl", "lg", "bl", "wk", "bob")

    /** The proportions in [j], or throws [IllegalArgumentException]. */
    fun decode(j: JSONObject): RigMorph {
        val have = j.keys().asSequence().toSet()
        require(have == KEYS) { "rig keys differ: unknown ${have - KEYS}, missing ${KEYS - have}" }
        return RigMorph(
            multiplier(j, "em"),
            multiplier(j, "ew"),
            multiplier(j, "tl"),
            multiplier(j, "lg"),
            multiplier(j, "bl"),
            multiplier(j, "wk"),
            j.get("bob") as? Boolean ?: throw IllegalArgumentException("bob is not a boolean"),
        )
    }

    private fun multiplier(j: JSONObject, key: String): Double {
        val n = j.get(key) as? Number ?: throw IllegalArgumentException("$key is not a number")
        val v = n.toDouble()
        require(v in RigMorph.MIN..RigMorph.MAX) { "$key is out of range" }
        return v
    }
}
