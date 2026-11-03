package com.lifemax.rpg.feature.shopping.domain

/**
 * The ONE place that knows what a Walmart "add several products to the cart" link looks like.
 *
 * The format was confirmed on the owner's Android phone with a two-product prototype (see
 * docs/specs/WALMART_CART_TEST_CHECKLIST.md). It is a link-based mechanism, not an official API:
 * - each cart line is `<itemId>|<quantity>`;
 * - lines are joined with a comma;
 * - the WHOLE joined text is percent-encoded the way JavaScript's `encodeURIComponent` does it (so
 *   `|` becomes `%7C` and `,` becomes `%2C`) and appended to [BASE];
 * - golden example, items 145051970 and 165233284 once each:
 *   `https://affil.walmart.com/cart/addToCart?items=145051970%7C1%2C165233284%7C1`.
 *
 * If Walmart ever changes the format, this file and its golden test (`WalmartCartLinkBuilderTest`)
 * are the only things to change. Nothing else in the app spells out a cart link.
 */
object WalmartCartFormat {
    /** Everything before the encoded items. */
    const val BASE = "https://affil.walmart.com/cart/addToCart?items="

    /** Separates the item id from its quantity inside one line, before encoding. */
    const val FIELD_SEPARATOR = "|"

    /** Separates lines, before encoding. */
    const val LINE_SEPARATOR = ","

    /** A Walmart item id: 5 to 15 digits (the tested envelope of the prototype). */
    val ITEM_ID = Regex("^\\d{5,15}$")

    /** Smallest quantity of one line (the prototype's tested envelope). */
    const val MIN_QUANTITY = 1

    /** Largest quantity of one line (the prototype's tested envelope). */
    const val MAX_QUANTITY = 12

    /**
     * UNVERIFIED, deliberately conservative: how many lines one link carries. The prototype was
     * only tried with two products and has no limit of its own; larger carts are split into several
     * links ("Cart 1 of N") rather than risking one very long link.
     */
    const val MAX_LINES_PER_LINK = 20

    /** UNVERIFIED, conservative: the longest link we hand to the phone. */
    const val MAX_URL_LENGTH = 1800

    private const val HEX = "0123456789ABCDEF"
    private const val UNRESERVED_MARKS = "-_.!~*'()"

    /** One line, before encoding: `145051970|2`. */
    fun line(itemId: String, quantity: Int): String = "$itemId$FIELD_SEPARATOR$quantity"

    /** The complete link for already validated lines. */
    fun url(lines: List<String>): String =
        BASE + encodeUriComponent(lines.joinToString(LINE_SEPARATOR))

    /**
     * `encodeURIComponent`: letters, digits and `- _ . ! ~ * ' ( )` stay, every other byte of the
     * UTF-8 text becomes `%XX` in capitals.
     */
    fun encodeUriComponent(text: String): String {
        val out = StringBuilder(text.length * 2)
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt() and 0xFF
            val ch = c.toChar()
            val plain =
                c < 0x80 &&
                    (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch in UNRESERVED_MARKS)
            if (plain) {
                out.append(ch)
            } else {
                out.append('%').append(HEX[c shr 4]).append(HEX[c and 15])
            }
        }
        return out.toString()
    }

    /** A normal Walmart site search for an ingredient, the per-item fallback. */
    fun searchUrl(ingredient: String): String =
        "https://www.walmart.com/search?q=" + encodeUriComponent(ingredient.trim())
}
