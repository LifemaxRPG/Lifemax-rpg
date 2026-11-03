package com.lifemax.rpg.feature.shopping.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.lifemax.rpg.feature.shopping.data.LaunchOutcome
import com.lifemax.rpg.feature.shopping.data.ShoppingFeed
import com.lifemax.rpg.feature.shopping.data.WalmartCartLauncher
import com.lifemax.rpg.feature.shopping.domain.Amount
import com.lifemax.rpg.feature.shopping.domain.CartLink
import com.lifemax.rpg.feature.shopping.domain.CartPlan
import com.lifemax.rpg.feature.shopping.domain.CartRequest
import com.lifemax.rpg.feature.shopping.domain.GroceryItem
import com.lifemax.rpg.feature.shopping.domain.GroceryListBuilder
import com.lifemax.rpg.feature.shopping.domain.PastedMatch
import com.lifemax.rpg.feature.shopping.domain.PreferredProduct
import com.lifemax.rpg.feature.shopping.domain.ProductMatch
import com.lifemax.rpg.feature.shopping.domain.RecipePick
import com.lifemax.rpg.feature.shopping.domain.ReviewLine
import com.lifemax.rpg.feature.shopping.domain.ShoppingProjection
import com.lifemax.rpg.feature.shopping.domain.SourceIngredient
import com.lifemax.rpg.feature.shopping.domain.UnitFamily
import com.lifemax.rpg.feature.shopping.domain.WalmartCartLinkBuilder
import com.lifemax.rpg.feature.shopping.domain.WalmartProductMatcher
import org.json.JSONObject

/** One row of the weekly grocery list as the screen draws it. */
data class ShoppingRow(
    val item: GroceryItem,
    val match: ProductMatch,
    val quantity: Int,
    val owned: Boolean,
) {
    val key: String
        get() = item.key

    /** Counts toward "added": needed, matched and at least one. */
    val inCart: Boolean
        get() = !owned && match is ProductMatch.Matched && quantity >= 1

    /** Counts toward "need a product". */
    val needsProduct: Boolean
        get() = !owned && match is ProductMatch.Unmatched
}

/** The line under the cart button: how many are in the cart link, how many need a product. */
data class ShoppingSummary(val added: Int, val needProduct: Int, val owned: Int) {
    val text: String
        get() = "$added items added, $needProduct need a product, $owned already owned"
}

/** What the owner is shown after trying to open a cart link. */
sealed interface CartNotice {
    /** The link was handed to the phone; the cart itself is unknown. */
    data class Handed(val link: CartLink) : CartNotice

    /** The same link was opened a moment ago: ask before opening it again. */
    data class ConfirmRepeat(val link: CartLink) : CartNotice

    data class Failed(val reason: String) : CartNotice
}

/**
 * The shopping pane's state: the page's last good projection, the owner's choices on this screen
 * (quantities, removed rows, "already own", recipes picked, matches not yet confirmed by the page)
 * and the cart flow. No Android types except through the injected launcher, so it is tested on the
 * JVM. Saving is the page's: a match is only SAVED when the page's next projection lists it.
 */
class ShoppingState(
    private val launcher: WalmartCartLauncher,
    private val send: (command: String, payload: Map<String, Any>) -> Unit,
    private val linkBuilder: WalmartCartLinkBuilder = WalmartCartLinkBuilder(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val feed = ShoppingFeed()
    private var projection by mutableStateOf<ShoppingProjection?>(null)
    private val quantities: SnapshotStateMap<String, Int> = mutableStateMapOf()
    private val removed: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    private val ownedChoice: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    private val picked: SnapshotStateMap<String, Double> = mutableStateMapOf()
    private val sessionMatches: SnapshotStateMap<String, PreferredProduct> = mutableStateMapOf()
    private val cleared: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    private val pendingOps = HashMap<String, String>()
    private var opCounter = 0

    /** The cart link number being worked through, from 0. */
    var cartIndex by mutableStateOf(0)
        private set

    /** Plain words for the owner after a save or a launch. */
    var message by mutableStateOf<String?>(null)
        private set

    var notice by mutableStateOf<CartNotice?>(null)
        private set

    /** Takes the page's `shopping` object; a bad one is ignored and the last good state kept. */
    fun accept(json: JSONObject?) {
        if (!feed.accept(json)) return
        val next = feed.current ?: return
        projection = next
        val saved = next.prefs.associateBy { it.key }
        sessionMatches.keys.toList().forEach {
            if (saved[it] == sessionMatches[it]) sessionMatches.remove(it)
        }
        cleared.keys.toList().forEach { if (it !in saved) cleared.remove(it) }
        next.answer?.let { answered ->
            if (pendingOps.values.remove(answered.op)) {
                message =
                    if (answered.ok) answered.message.takeIf { it.isNotBlank() }
                    else answered.message.ifBlank { "Could not save that product choice." }
            }
        }
    }

    /** Whether a good projection has arrived; until then the classic pane is drawn. */
    val ready: Boolean
        get() = projection != null

    val weekStart: String
        get() = projection?.weekStart.orEmpty()

    val recipes
        get() = projection?.recipes.orEmpty()

    val prefsSavedCount: Int
        get() = projection?.prefs?.size ?: 0

    fun pickedServings(recipeId: String): Double? = picked[recipeId]

    fun pickRecipe(recipeId: String, servings: Double?) {
        if (servings == null || servings <= 0.0) picked.remove(recipeId)
        else picked[recipeId] = servings.coerceAtMost(MAX_SERVINGS)
        cartIndex = 0
    }

    private fun matcher(): WalmartProductMatcher {
        val saved = projection?.prefs.orEmpty().filter { cleared[it.key] != true }
        return WalmartProductMatcher(saved, sessionMatches.values)
    }

    /** Everything the list is built from: the week's rows plus the picked recipes. */
    private fun grocery() =
        projection.let { p ->
            if (p == null) return@let null
            val week =
                p.weekItems.map {
                    SourceIngredient(
                        key = it.key,
                        name = it.name,
                        amount = Amount(it.grams, UnitFamily.MASS),
                        packSize = it.packGrams,
                        packLabel = it.packLabel,
                        aisle = it.aisleName,
                        pantry = it.home,
                        origin = "This week's plan",
                    )
                }
            val recipePicks =
                p.recipes.mapNotNull { r ->
                    picked[r.id]?.let { RecipePick(r.id, r.name, r.servings, it, r.lines) }
                }
            GroceryListBuilder.build(week, recipePicks)
        }

    /** The rows to draw, in list order, without the ones the owner removed. */
    val rows: List<ShoppingRow>
        get() {
            val matcher = matcher()
            return grocery()
                ?.items
                .orEmpty()
                .filter { removed[it.key] != true }
                .map { item ->
                    ShoppingRow(
                        item = item,
                        match = matcher.matchFor(item.key),
                        quantity = quantities[item.key] ?: item.suggestedQuantity,
                        owned = ownedChoice[item.key] ?: item.pantry,
                    )
                }
        }

    /** Lines that could not be read, kept for the owner to look at. */
    val review: List<ReviewLine>
        get() = grocery()?.review.orEmpty()

    val summary: ShoppingSummary
        get() =
            rows.let { r ->
                ShoppingSummary(
                    r.count { it.inCart },
                    r.count { it.needsProduct },
                    r.count { it.owned },
                )
            }

    /** The cart links for what is matched and wanted, with every issue found. */
    val plan: CartPlan
        get() =
            linkBuilder.build(
                rows
                    .filter { it.inCart }
                    .map {
                        CartRequest(
                            (it.match as ProductMatch.Matched).product.productId,
                            it.quantity,
                            it.item.name,
                        )
                    }
            )

    /** The Add All button is on when there is something to add. */
    val canAddAll: Boolean
        get() = plan.links.isNotEmpty()

    fun setQuantity(key: String, quantity: Int) {
        quantities[key] = quantity.coerceIn(1, MAX_QUANTITY_EDIT)
        cartIndex = 0
    }

    fun setOwned(key: String, owned: Boolean) {
        ownedChoice[key] = owned
        cartIndex = 0
    }

    fun remove(key: String) {
        removed[key] = true
        cartIndex = 0
    }

    fun restoreRemoved() = removed.clear()

    val removedCount: Int
        get() = removed.size

    /**
     * Sets the preferred product from what the owner pasted. Returns the reason when it is not a
     * valid Walmart link or number (nothing changes then), or null when accepted and sent to the
     * page to save.
     */
    fun setProduct(key: String, pasted: String, label: String = "", note: String = ""): String? {
        return when (val m = matcher().fromPasted(key, pasted, label, note)) {
            is PastedMatch.Rejected -> m.reason
            is PastedMatch.Accepted -> {
                sessionMatches[key] = m.product
                cleared.remove(key)
                cartIndex = 0
                message = null
                send(
                    "shopPrefSet",
                    mapOf(
                        "op" to opFor("shopPrefSet", key),
                        "key" to key,
                        "productId" to m.product.productId,
                        "label" to m.product.label,
                        "note" to m.product.note,
                    ),
                )
                null
            }
        }
    }

    /** Forgets the preferred product; the row becomes unmatched at once. */
    fun clearProduct(key: String) {
        sessionMatches.remove(key)
        cleared[key] = true
        cartIndex = 0
        send("shopPrefClear", mapOf("op" to opFor("shopPrefClear", key), "key" to key))
    }

    /** One operation id per (command, target) until the page answers, so a retry records once. */
    private fun opFor(command: String, key: String) =
        pendingOps.getOrPut("$command:$key") { "shop-${clock()}-${++opCounter}" }

    // ---- cart flow ----------------------------------------------------------------------------

    /** Opens link number [index] of the plan (from 0); asks first if it was opened a moment ago. */
    fun openCart(index: Int = cartIndex, openAgainAnyway: Boolean = false) {
        val link = plan.links.getOrNull(index) ?: return
        notice =
            when (val outcome = launcher.launch(link.url, openAgainAnyway)) {
                is LaunchOutcome.Handed -> {
                    cartIndex = (index + 1).coerceAtMost(link.count - 1)
                    CartNotice.Handed(link)
                }
                LaunchOutcome.NeedsConfirmation -> CartNotice.ConfirmRepeat(link)
                is LaunchOutcome.Failed -> CartNotice.Failed(outcome.reason)
            }
    }

    fun dismissNotice() {
        notice = null
    }

    /** Shows a short plain message under the cart button. */
    fun announce(text: String) {
        message = text
    }

    /** Opens the Walmart site search for an ingredient; never touches the cart. */
    fun search(name: String) {
        val url = matcher().searchUrl(name)
        notice =
            (launcher.launch(url, openAgainAnyway = true) as? LaunchOutcome.Failed)?.let {
                CartNotice.Failed(it.reason)
            }
    }

    companion object {
        const val MAX_QUANTITY_EDIT = 99
        const val MAX_SERVINGS = 100.0
    }
}
