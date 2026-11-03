package com.lifemax.rpg.feature.shopping.presentation

import com.lifemax.rpg.feature.shopping.domain.ProductMatch

/**
 * The plain-text shopping list for Copy and Share: one line per ingredient still to buy, grouped by
 * aisle, with the chosen Walmart item number when there is one. Ingredients already owned are left
 * out, as are removed ones.
 */
object ShoppingListText {
    fun build(weekStart: String, rows: List<ShoppingRow>): String {
        val wanted = rows.filter { !it.owned }
        val out = StringBuilder("Shopping list, week of $weekStart\n")
        if (wanted.isEmpty()) return out.append("\nNothing to buy.\n").toString()
        wanted
            .groupBy { it.item.aisle.ifBlank { "Other" } }
            .forEach { (aisle, group) ->
                out.append('\n').append(aisle).append('\n')
                group.forEach { row ->
                    out.append("[ ] ").append(row.item.name).append(" - ").append(row.quantity)
                    row.item.packLabel
                        .takeIf { it.isNotBlank() }
                        ?.let { out.append(" x ").append(it) }
                    (row.match as? ProductMatch.Matched)?.let {
                        out.append(" (Walmart item ").append(it.product.productId).append(')')
                    }
                    out.append('\n')
                }
            }
        return out.toString()
    }
}
