package com.lifemax.rpg.feature.shopping.domain

import java.net.URI

/** What a pasted product link or id turned out to be. */
sealed interface ProductParse {
    /** A valid Walmart item id. */
    data class Valid(val productId: String) : ProductParse

    /** Not usable; [reason] is shown to the owner as is. */
    data class Invalid(val reason: String) : ProductParse
}

/**
 * Reads what the owner pastes for a product: a plain item id, `https://www.walmart.com/ip/<id>` or
 * `https://www.walmart.com/ip/<slug>/<id>`, each with an optional query or fragment.
 *
 * Strict: the host must be walmart.com or a subdomain of it, the scheme http(s), and the id 5 to 15
 * digits. Nothing is ever guessed from a product name.
 */
object WalmartProductLink {
    private val HOST = Regex("(^|\\.)walmart\\.com$", RegexOption.IGNORE_CASE)
    private val PATH = Regex("^/ip/(?:[^/]+/)?(\\d{5,15})(?:/.*)?$", RegexOption.IGNORE_CASE)
    private const val MAX_LENGTH = 600

    fun parse(input: String): ProductParse {
        val text = input.trim()
        if (text.isEmpty()) {
            return ProductParse.Invalid("Paste a Walmart product link or item number.")
        }
        if (text.length > MAX_LENGTH) return ProductParse.Invalid("That link is too long.")
        if (WalmartCartFormat.ITEM_ID.matches(text)) return ProductParse.Valid(text)
        if (text.all { it.isDigit() }) {
            return ProductParse.Invalid("A Walmart item number has 5 to 15 digits.")
        }
        val uri =
            try {
                URI(text)
            } catch (e: Exception) {
                return ProductParse.Invalid("That is not a valid link.")
            }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") {
            return ProductParse.Invalid("Use a link that starts with https://www.walmart.com/ip/")
        }
        val host = uri.host ?: return ProductParse.Invalid("That is not a valid link.")
        if (!HOST.containsMatchIn(host)) {
            return ProductParse.Invalid("That link is not from walmart.com.")
        }
        val id =
            PATH.find(uri.rawPath ?: "")?.groupValues?.get(1)
                ?: return ProductParse.Invalid(
                    "That is not a Walmart product link (it looks like walmart.com/ip/name/number)."
                )
        return ProductParse.Valid(id)
    }
}
