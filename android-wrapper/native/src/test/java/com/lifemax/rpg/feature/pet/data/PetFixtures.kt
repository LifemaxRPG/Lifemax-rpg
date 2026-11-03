package com.lifemax.rpg.feature.pet.data

import org.json.JSONObject

/**
 * The Cat tab's projections as the page makes them (tools/gen_pet_fixtures.js, the `pet` job of
 * gen_native_fixtures.js): one JSON per cat state, and the page's reply to every command.
 */
internal object PetFixtures {
    /** The projection named [name] (`alive`, `hungry`, `two-cats`, ...), a fresh copy each time. */
    fun projection(name: String): JSONObject =
        JSONObject(PetFixtures::class.java.getResource("/fixtures/pet-$name.json")!!.readText())

    /**
     * What the page answered to command [key] (`feed`, `blinkSquint`, ...), in the kit's envelope.
     */
    fun reply(key: String): JSONObject =
        JSONObject(PetFixtures::class.java.getResource("/fixtures/pet-replies.json")!!.readText())
            .getJSONObject(key)
            .getJSONObject("reply")

    /**
     * What the page answered to a panels command (`focusSecond`, `buyToy`, `buyToyPoor`,
     * `expandOpen`, ...), in the kit's envelope.
     */
    fun panelReply(key: String): JSONObject =
        JSONObject(
                PetFixtures::class
                    .java
                    .getResource("/fixtures/pet-panels-replies.json")!!
                    .readText()
            )
            .getJSONObject(key)
            .getJSONObject("reply")

    /** The cat states the panels fixtures (`pet-panels-<state>.json`, `pet.panels.v1`) cover. */
    val PANEL_STATES =
        listOf(
            "kitten",
            "stray",
            "bonded",
            "hungry",
            "vacation",
            "twoCats",
            "geneticMale",
            "geneticFemale",
            "geneticSterile",
        )

    /** The fixture states of a cat. */
    val STATES =
        listOf(
            "alive",
            "content",
            "hungry",
            "very-hungry",
            "weak",
            "vacation",
            "kitten",
            "stray",
            "two-cats",
            "dna",
            "fed",
        )
}
