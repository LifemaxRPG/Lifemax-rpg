package com.lifemax.rpg.feature.shopping.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.feature.shopping.domain.PreferredProduct
import com.lifemax.rpg.feature.shopping.domain.RecipeSource
import com.lifemax.rpg.feature.shopping.domain.ShoppingProjection
import com.lifemax.rpg.feature.shopping.domain.WalmartCartFormat
import com.lifemax.rpg.feature.shopping.domain.WeekItem
import com.lifemax.rpg.feature.shopping.domain.WriteAnswer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Strict decoder for the page's shopping projection. Anything malformed throws
 * ([IllegalArgumentException] or a JSON exception): wrong version, a missing field, a field of the
 * wrong type, a value out of range, a duplicate key, or more rows than the bounds allow.
 */
object ShoppingProjectionDecoder {
    private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun decode(j: JSONObject): ShoppingProjection {
        require(j.getInt("version") == 1) { "Unsupported shopping version" }
        val session = j.getString("session")
        val revision = j.getLong("revision")
        require(session.isNotBlank() && session.length <= 80 && revision >= 1)
        val weekStart = j.getString("weekStart")
        require(DATE.matches(weekStart)) { "weekStart is not a date" }

        val items =
            j.getJSONArray("items").bounded(ShoppingProjection.MAX_WEEK_ITEMS).map(::weekItem)
        require(items.map { it.id }.let { it.size == it.toSet().size }) { "Duplicate item id" }
        val recipes =
            j.getJSONArray("recipes").bounded(ShoppingProjection.MAX_RECIPES).map(::recipe)
        require(recipes.map { it.id }.let { it.size == it.toSet().size }) { "Duplicate recipe id" }
        val prefs = j.getJSONArray("prefs").bounded(ShoppingProjection.MAX_PREFS).map(::pref)
        require(prefs.map { it.key }.let { it.size == it.toSet().size }) { "Duplicate preference" }

        return ShoppingProjection(
            session = session,
            revision = revision,
            weekStart = weekStart,
            nextWeek = j.getBoolean("nextWeek"),
            weekItems = items,
            recipes = recipes,
            prefs = prefs,
            answer = j.optJSONObject("answer")?.let(::answer),
        )
    }

    private fun JSONArray.bounded(max: Int): List<JSONObject> {
        require(length() <= max) { "Too many rows" }
        return objects()
    }

    private fun weekItem(o: JSONObject): WeekItem {
        val id = o.getString("id")
        val grams = o.getDouble("grams")
        val pack = o.getDouble("packGrams")
        require(id.isNotBlank() && id.length <= 40 && grams.isFinite() && grams in 0.0..1.0e6)
        require(pack.isFinite() && pack in 0.0..1.0e6)
        return WeekItem(
            id = id,
            name = o.getString("name").also { require(it.isNotBlank() && it.length <= 80) },
            aisleName = o.getString("aisleName").also { require(it.length <= 40) },
            grams = grams,
            packGrams = pack.takeIf { it > 0.0 },
            packLabel = o.getString("unit").also { require(it.length <= 40) },
            home = o.getBoolean("home"),
        )
    }

    private fun recipe(o: JSONObject): RecipeSource {
        val servings = o.getInt("servings")
        require(servings in 1..100)
        val lines = o.getJSONArray("lines")
        require(lines.length() <= ShoppingProjection.MAX_RECIPE_LINES)
        return RecipeSource(
            id = o.getString("id").also { require(it.isNotBlank() && it.length <= 60) },
            name = o.getString("name").also { require(it.isNotBlank() && it.length <= 100) },
            servings = servings,
            lines =
                (0 until lines.length()).map {
                    lines.getString(it).also { s -> require(s.length <= 200) }
                },
        )
    }

    private fun pref(o: JSONObject): PreferredProduct {
        val key = o.getString("key")
        val id = o.getString("productId")
        require(key.isNotBlank() && key.length <= ShoppingProjection.MAX_KEY)
        require(WalmartCartFormat.ITEM_ID.matches(id)) { "Bad product id" }
        return PreferredProduct(
            key,
            id,
            o.getString("label").also { require(it.length <= 80) },
            o.getString("note").also { require(it.length <= 120) },
        )
    }

    private fun answer(o: JSONObject) =
        WriteAnswer(
            op = o.getString("op"),
            ok = o.getBoolean("ok"),
            changed = o.getBoolean("changed"),
            message = o.getString("message").take(200),
        )
}

/**
 * Holds the last projection that decoded. A malformed one, or an older one from the same page load,
 * is ignored and the screen keeps drawing what it had.
 */
class ShoppingFeed {
    var current: ShoppingProjection? = null
        private set

    /** The reason the last offered projection was refused, or null when it was taken. */
    var lastRejection: String? = null
        private set

    /** Offers a raw projection; returns whether it was taken. */
    fun accept(json: JSONObject?): Boolean {
        if (json == null) {
            lastRejection = "No shopping projection"
            return false
        }
        val next =
            try {
                ShoppingProjectionDecoder.decode(json)
            } catch (e: Exception) {
                lastRejection = e.message ?: "Malformed shopping projection"
                return false
            }
        val held = current
        if (held != null && held.session == next.session && next.revision < held.revision) {
            lastRejection = "Older projection"
            return false
        }
        current = next
        lastRejection = null
        return true
    }
}
