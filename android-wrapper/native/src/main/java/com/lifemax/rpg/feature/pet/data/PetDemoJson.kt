package com.lifemax.rpg.feature.pet.data

import com.lifemax.rpg.feature.pet.domain.PetDemo
import org.json.JSONObject

/*
 * The developer demo button of the Cat tab's projection (`demo`), which a page sends only to a
 * screen that said `pet.demo.v1` and only while its developer mode is on (50-native-pet-demo.js).
 * Read as strictly as the rest: exactly `label` and `hint`, short non-blank text. Absent: hidden.
 */

private val DEMO_KEYS = setOf("label", "hint")
private const val TEXT_MAX = 120

/** The demo button of [j] (its `demo` key), or null when the page sent none. */
internal fun decodeDemo(j: JSONObject): PetDemo? {
    if (!j.has("demo")) return null
    val d = j.obj("demo", DEMO_KEYS)
    val label = d.str("label")
    val hint = d.str("hint")
    require(label.isNotBlank() && label.length <= TEXT_MAX) { "demo label" }
    require(hint.isNotBlank() && hint.length <= TEXT_MAX) { "demo hint" }
    return PetDemo(label, hint)
}
