package com.lifemax.rpg.feature.shopping.domain

/** A product the owner chose for an ingredient. */
data class PreferredProduct(
    val key: String,
    val productId: String,
    val label: String = "",
    val note: String = "",
)

/** Where a match came from. */
enum class MatchSource {
    /** Kept in the owner's save. */
    SAVED,

    /** Chosen this session and not (yet) confirmed as saved. */
    SESSION,
}

/** The product for one ingredient, or none. */
sealed interface ProductMatch {
    data class Matched(val product: PreferredProduct, val source: MatchSource) : ProductMatch

    /** No product chosen: the ingredient needs one. Never filled in by guessing. */
    data object Unmatched : ProductMatch
}

/**
 * Ingredient to Walmart product. The only matches are products the owner pasted or confirmed: the
 * matcher never searches, never guesses from a name and never substitutes a different product.
 *
 * [saved] are the matches from the owner's save (the page's projection); [session] are ones chosen
 * on this screen that the page has not confirmed yet. A session match wins over a saved one so the
 * owner's latest choice shows at once.
 */
class WalmartProductMatcher(
    saved: Collection<PreferredProduct> = emptyList(),
    session: Collection<PreferredProduct> = emptyList(),
) {
    private val savedByKey = saved.associateBy { it.key }
    private val sessionByKey = session.associateBy { it.key }

    fun matchFor(key: String): ProductMatch {
        sessionByKey[key]?.let {
            return ProductMatch.Matched(it, MatchSource.SESSION)
        }
        savedByKey[key]?.let {
            return ProductMatch.Matched(it, MatchSource.SAVED)
        }
        return ProductMatch.Unmatched
    }

    /**
     * A match built from what the owner pasted, or the reason it is not usable. [label] and [note]
     * are trimmed and bounded the same way the page bounds them.
     */
    fun fromPasted(
        key: String,
        pasted: String,
        label: String = "",
        note: String = "",
    ): PastedMatch =
        when (val parsed = WalmartProductLink.parse(pasted)) {
            is ProductParse.Invalid -> PastedMatch.Rejected(parsed.reason)
            is ProductParse.Valid ->
                PastedMatch.Accepted(
                    PreferredProduct(
                        key = key,
                        productId = parsed.productId,
                        label = label.trim().take(MAX_LABEL),
                        note = note.trim().take(MAX_NOTE),
                    )
                )
        }

    /** A normal Walmart site search for the ingredient: the fallback when nothing is matched. */
    fun searchUrl(ingredientName: String): String = WalmartCartFormat.searchUrl(ingredientName)

    companion object {
        const val MAX_LABEL = 80
        const val MAX_NOTE = 120
    }
}

/** The outcome of [WalmartProductMatcher.fromPasted]. */
sealed interface PastedMatch {
    data class Accepted(val product: PreferredProduct) : PastedMatch

    data class Rejected(val reason: String) : PastedMatch
}
