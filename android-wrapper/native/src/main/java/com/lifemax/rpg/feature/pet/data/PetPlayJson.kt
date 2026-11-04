package com.lifemax.rpg.feature.pet.data

import com.lifemax.rpg.feature.pet.domain.PetPlay
import com.lifemax.rpg.feature.pet.domain.PetPlayToy
import org.json.JSONObject

/*
 * The toy tray of the Cat tab's projection (`play`), which a page sends only to a screen that said
 * `pet.play.v1` (50-native-pet-play.js). Read as strictly as the rest: exactly these keys, the
 * page's toy ids, at most the page's seven toys, each once.
 */

private val PLAY_KEYS = setOf("title", "pick", "tired", "toys")
private val TOY_KEYS = setOf("id", "name", "word")
private val TOY_IDS = setOf("ball_a", "mouse", "feather", "ball_b", "yarn", "spring", "wand")
private const val TEXT_MAX = 120

private fun JSONObject.line(key: String): String =
    str(key).also { require(it.length <= TEXT_MAX) { "$key is too long" } }

/** The tray of [j] (its `play` key), or null when the page sent none (an older page). */
internal fun decodePlay(j: JSONObject): PetPlay? {
    if (!j.has("play")) return null
    val p = j.obj("play", PLAY_KEYS)
    val list = p.getJSONArray("toys")
    require(list.length() <= TOY_IDS.size) { "too many toys" }
    val toys =
        (0 until list.length()).map {
            val t = list.get(it) as? JSONObject ?: throw IllegalArgumentException("toy")
            t.exactly(TOY_KEYS, "toy")
            val id = t.str("id")
            require(id in TOY_IDS) { "not one of the page's toys" }
            PetPlayToy(id, t.line("name").also { n -> require(n.isNotBlank()) }, t.line("word"))
        }
    require(toys.map { it.id }.distinct().size == toys.size) { "a toy twice" }
    return PetPlay(p.line("title"), p.line("pick"), p.line("tired"), toys)
}
