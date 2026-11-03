package com.lifemax.rpg.feature.shopping.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** What an amount is counted in. Amounts of different families are never added together. */
enum class UnitFamily(val short: String) {
    /** Stored in grams. */
    MASS("g"),

    /** Stored in millilitres. */
    VOLUME("ml"),

    /** Stored as a count of items, cans, cloves and so on. */
    COUNT(""),
}

/** An amount in the base unit of its [family]: grams, millilitres or a count. */
data class Amount(val value: Double, val family: UnitFamily) {
    operator fun plus(other: Amount): Amount {
        require(other.family == family) { "Cannot add $family to ${other.family}" }
        return Amount(value + other.value, family)
    }

    operator fun times(factor: Double) = Amount(value * factor, family)

    /** Short text for a list row: `454 g`, `1.2 kg`, `250 ml`, `2.5`. */
    fun text(): String =
        when (family) {
            UnitFamily.MASS -> if (value >= 1000) "${trim(value / 1000)} kg" else "${trim(value)} g"
            UnitFamily.VOLUME ->
                if (value >= 1000) "${trim(value / 1000)} L" else "${trim(value)} ml"
            UnitFamily.COUNT -> trim(value)
        }

    private fun trim(x: Double): String {
        val whole = x.roundToInt()
        if (abs(x - whole) < 0.05) return whole.toString()
        return String.format(Locale.US, "%.1f", x)
    }
}

/**
 * One ingredient the lists are built from.
 *
 * [key] identifies the ingredient across lists (and is what a preferred product is stored under);
 * [packSize] is how much one package holds in the same unit as [amount], when known, so the number
 * of packages can be worked out; [pantry] marks staples people usually already have.
 */
data class SourceIngredient(
    val key: String,
    val name: String,
    val amount: Amount,
    val packSize: Double? = null,
    val packLabel: String = "",
    val aisle: String = "",
    val pantry: Boolean = false,
    val origin: String = "",
)

/** One consolidated row of the weekly grocery list. */
data class GroceryItem(
    val key: String,
    val name: String,
    val amount: Amount,
    val packSize: Double?,
    val packLabel: String,
    val aisle: String,
    val pantry: Boolean,
    val origins: List<String>,
) {
    /**
     * How many packages to buy: the amount over the package size, rounded UP with the page's small
     * tolerance; a count rounds up to whole items; an amount with no known package size is 1 until
     * the owner edits it.
     */
    val suggestedQuantity: Int
        get() = packagesFor(amount, packSize)

    /** Whether the number above comes from a known package size or a count. */
    val quantityKnown: Boolean
        get() = packSize != null || amount.family == UnitFamily.COUNT

    companion object {
        /** The page rounds packs up after taking 4 percent off, so 1.03 packs is still one pack. */
        const val ROUNDING_TOLERANCE = 0.04

        fun packagesFor(amount: Amount, packSize: Double?): Int {
            val per = packSize?.takeIf { it > 0.0 && it.isFinite() }
            val raw =
                when {
                    per != null -> amount.value / per - ROUNDING_TOLERANCE
                    amount.family == UnitFamily.COUNT -> amount.value - 1e-9
                    else -> 1.0
                }
            val packs = kotlin.math.ceil(raw)
            return if (packs.isFinite()) packs.toInt().coerceIn(1, 999) else 1
        }
    }
}

/** An ingredient line the parser could not read: kept so the owner can look at it by hand. */
data class ReviewLine(val text: String, val origin: String)

/** The consolidated list and what could not be read. */
data class GroceryList(val items: List<GroceryItem>, val review: List<ReviewLine>)
