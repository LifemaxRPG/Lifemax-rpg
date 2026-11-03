package com.lifemax.rpg.feature.pet.data

import com.lifemax.rpg.feature.overview.data.haptics
import com.lifemax.rpg.feature.pet.domain.PetCounts
import com.lifemax.rpg.feature.pet.domain.PetTouch
import com.lifemax.rpg.feature.pet.domain.PetTouchProfile
import com.lifemax.rpg.feature.pet.domain.PetTouchWords
import org.json.JSONObject

/*
 * The touch part of the Cat tab's projection, which a page sends only to a screen that said
 * `pet.touch.v1`: all three keys together, or none. Read as strictly as the rest.
 */

/** The keys a page with touch adds to the projection. */
internal val TOUCH_KEYS = setOf("touch", "counts", "haptics", "touchWords")

private val PROFILE_KEYS = setOf("level", "satMul", "trust", "sat")
private val COUNT_KEYS = setOf("pets", "petsMax", "plays", "playsMax")
private const val MAX_LEVEL = 5
private const val MAX_COUNT = 99
private const val MAX_SAT = 1.2
private const val MAX_SAT_MUL = 3.0

private fun JSONObject.real(key: String): Double {
    val n = get(key) as? Number ?: throw IllegalArgumentException("$key is not a number")
    return n.toDouble().also { require(it.isFinite()) { "$key is not finite" } }
}

/**
 * The touch fields of [j], one profile for each of the [cats] in the room; null when the page sent
 * none (an older page). Some of the three keys without the others is malformed.
 */
internal fun decodeTouch(j: JSONObject, cats: Int): PetTouch? {
    val present = TOUCH_KEYS.filter { j.has(it) }
    require(present.isEmpty() || present.size == TOUCH_KEYS.size) { "touch keys come together" }
    if (present.isEmpty()) return null
    val list = j.getJSONArray("touch")
    require(list.length() == cats) { "one touch profile for each cat" }
    val profiles =
        (0 until cats).map {
            val p = list.get(it) as? JSONObject ?: throw IllegalArgumentException("touch profile")
            p.exactly(PROFILE_KEYS, "touch profile")
            val level = p.int("level")
            val satMul = p.real("satMul")
            val trust = p.real("trust")
            val sat = p.real("sat")
            require(level in 0..MAX_LEVEL && trust in 0.0..1.0) {
                "a touch profile is out of range"
            }
            require(satMul in 0.0..MAX_SAT_MUL && sat in 0.0..MAX_SAT) { "saturation out of range" }
            PetTouchProfile(level, satMul, trust, sat)
        }
    val c = j.obj("counts", COUNT_KEYS)
    val counts = PetCounts(c.int("pets"), c.int("petsMax"), c.int("plays"), c.int("playsMax"))
    require(
        counts.pets in 0..MAX_COUNT &&
            counts.plays in 0..MAX_COUNT &&
            counts.petsMax in 1..MAX_COUNT &&
            counts.playsMax in 1..MAX_COUNT
    ) {
        "the counts are out of range"
    }
    val haptic = if (j.isNull("haptics")) null else haptics(j.getJSONObject("haptics"))
    val words = j.obj("touchWords", setOf("closeRoom", "countsLine"))
    return PetTouch(
        profiles,
        counts,
        haptic,
        PetTouchWords(words.str("closeRoom"), words.str("countsLine")),
    )
}
