package com.lifemax.rpg.feature.shopping

import org.json.JSONArray
import org.json.JSONObject

/** Builds the page's `shopping` projection the way 50-native-shopping.js shapes it. */
object ShoppingFixtures {
    fun item(
        id: String,
        name: String = id.replaceFirstChar { it.uppercase() },
        grams: Double = 500.0,
        packGrams: Double = 500.0,
        unit: String = "pack",
        home: Boolean = false,
        aisle: String = "Pantry",
    ): JSONObject =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("aisleName", aisle)
            .put("grams", grams)
            .put("packGrams", packGrams)
            .put("unit", unit)
            .put("home", home)

    fun recipe(id: String, name: String, servings: Int, vararg lines: String): JSONObject =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("servings", servings)
            .put("lines", JSONArray(lines.toList()))

    fun pref(key: String, productId: String, label: String = "", note: String = ""): JSONObject =
        JSONObject()
            .put("key", key)
            .put("productId", productId)
            .put("label", label)
            .put("note", note)

    fun answer(op: String, ok: Boolean, changed: Boolean = ok, message: String = ""): JSONObject =
        JSONObject().put("op", op).put("ok", ok).put("changed", changed).put("message", message)

    fun projection(
        items: List<JSONObject> = listOf(item("oats"), item("rice"), item("salt", home = true)),
        recipes: List<JSONObject> = emptyList(),
        prefs: List<JSONObject> = emptyList(),
        revision: Long = 1,
        session: String = "p1",
        answer: JSONObject? = null,
    ): JSONObject =
        JSONObject()
            .put("version", 1)
            .put("session", session)
            .put("revision", revision)
            .put("weekStart", "2026-11-02")
            .put("nextWeek", false)
            .put("items", JSONArray(items))
            .put("recipes", JSONArray(recipes))
            .put("prefs", JSONArray(prefs))
            .apply { if (answer != null) put("answer", answer) }
}
