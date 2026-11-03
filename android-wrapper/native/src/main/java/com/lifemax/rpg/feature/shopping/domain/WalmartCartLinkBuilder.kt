package com.lifemax.rpg.feature.shopping.domain

/** One product the owner wants in the cart, before validation. */
data class CartRequest(val productId: String, val quantity: Int, val label: String = "")

/** One validated cart line. */
data class CartLine(val productId: String, val quantity: Int, val label: String)

/** One link to open: [index] counts from 1 of [count] links. */
data class CartLink(val url: String, val lines: List<CartLine>, val index: Int, val count: Int)

/** Something about the cart the owner must be told; [message] is shown as is. */
sealed class CartIssue(val message: String) {
    /** A request left out because its product id is not a Walmart item id. */
    class InvalidId(label: String) :
        CartIssue("$label has no valid Walmart item number, so it was left out.")

    /** A request left out because its quantity is not a whole number of at least 1. */
    class InvalidQuantity(label: String) :
        CartIssue(
            "$label has a quantity that is not a whole number of 1 or more, so it was left out."
        )

    /** A line whose quantity (after adding duplicates) was cut to the most one line may carry. */
    class QuantityCapped(label: String, requested: Int) :
        CartIssue(
            "$label: $requested requested, but a cart line carries at most " +
                "${WalmartCartFormat.MAX_QUANTITY}. Added ${WalmartCartFormat.MAX_QUANTITY}; add the rest in Walmart."
        )

    /** The cart did not fit in one link and was split. */
    class Split(count: Int) :
        CartIssue(
            "This cart is split into $count links. Open them one after another and check your cart each time."
        )
}

/** The result of [WalmartCartLinkBuilder.build]: no links means nothing to open. */
data class CartPlan(val links: List<CartLink>, val issues: List<CartIssue>) {
    val isEmpty: Boolean
        get() = links.isEmpty()

    val lineCount: Int
        get() = links.sumOf { it.lines.size }
}

/**
 * Turns the matched products and their quantities into Walmart cart links. Pure: it only builds
 * text. It never opens anything and never claims the cart was changed.
 * - invalid ids and quantities are left out and reported, never repaired;
 * - the same product listed twice becomes one line with the quantities added up, cut to
 *   [WalmartCartFormat.MAX_QUANTITY] with a visible note;
 * - a cart with more lines than [maxLines] (or a link longer than [maxUrlLength]) is split into
 *   several links and says so.
 */
class WalmartCartLinkBuilder(
    private val maxLines: Int = WalmartCartFormat.MAX_LINES_PER_LINK,
    private val maxUrlLength: Int = WalmartCartFormat.MAX_URL_LENGTH,
) {
    fun build(requests: List<CartRequest>): CartPlan {
        val issues = mutableListOf<CartIssue>()
        val order = LinkedHashMap<String, Pair<Int, String>>()
        for (r in requests) {
            val name = r.label.ifBlank { "Item ${r.productId}" }
            if (!WalmartCartFormat.ITEM_ID.matches(r.productId)) {
                issues += CartIssue.InvalidId(name)
            } else if (r.quantity < WalmartCartFormat.MIN_QUANTITY) {
                issues += CartIssue.InvalidQuantity(name)
            } else {
                val (sum, first) = order[r.productId] ?: (0 to name)
                // Long arithmetic: two huge quantities must not wrap around into a valid one.
                order[r.productId] =
                    (sum.toLong() + r.quantity).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() to
                        first
            }
        }
        val lines =
            order.map { (id, v) ->
                val (sum, name) = v
                if (sum > WalmartCartFormat.MAX_QUANTITY)
                    issues += CartIssue.QuantityCapped(name, sum)
                CartLine(id, sum.coerceAtMost(WalmartCartFormat.MAX_QUANTITY), name)
            }
        val chunks = split(lines)
        if (chunks.size > 1) issues += CartIssue.Split(chunks.size)
        val links =
            chunks.mapIndexed { i, chunk ->
                CartLink(urlOf(chunk), chunk, index = i + 1, count = chunks.size)
            }
        return CartPlan(links, issues)
    }

    private fun urlOf(lines: List<CartLine>) =
        WalmartCartFormat.url(lines.map { WalmartCartFormat.line(it.productId, it.quantity) })

    /** Greedy chunks that respect both the line count and the link length. */
    private fun split(lines: List<CartLine>): List<List<CartLine>> {
        val chunks = mutableListOf<MutableList<CartLine>>()
        for (line in lines) {
            val current = chunks.lastOrNull()
            val fits =
                current != null &&
                    current.size < maxLines &&
                    urlOf(current + line).length <= maxUrlLength
            if (fits) current!!.add(line) else chunks.add(mutableListOf(line))
        }
        return chunks
    }
}
