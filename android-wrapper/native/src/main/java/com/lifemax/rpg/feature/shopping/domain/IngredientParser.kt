package com.lifemax.rpg.feature.shopping.domain

/** What one written ingredient line turned into. */
sealed interface ParsedIngredient {
    /** A line with a readable amount; [name] is for display, [key] identifies the ingredient. */
    data class Parsed(val key: String, val name: String, val amount: Amount) : ParsedIngredient

    /** A line we could not turn into an amount (`Pinch of salt`): kept for the owner to check. */
    data object Unreadable : ParsedIngredient
}

/**
 * Reads the ingredient lines of the recipe book, written like `1/2 cup rolled oats (40g)`, `1 lb
 * boneless skinless chicken breast, cubed (454g)` or `2 large whole eggs (100g)`.
 * - quantities may be whole, decimal, a fraction (`1/2`), mixed (`1 1/2`), a unicode fraction (`½`)
 *   or a range (`2-3`, the larger end is taken);
 * - a weight in brackets wins over a volume (`1/2 cup rolled oats (40g)` is 40 g), except for
 *   things bought whole (cans, jars, bags), which stay counted;
 * - descriptions after a comma (`, cubed`) and sizes (`large`) are dropped from the name;
 * - anything without a leading quantity is [ParsedIngredient.Unreadable], never guessed.
 */
object IngredientParser {
    private const val MAX_LINE = 200
    private const val MAX_NAME = 50
    private val GRAMS = Regex("\\((\\d+(?:\\.\\d+)?)\\s*g\\)", RegexOption.IGNORE_CASE)
    private val BRACKETS = Regex("\\([^)]*\\)")
    private val UNICODE =
        mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '⅛' to 0.125)
    private val NUMBER = Regex("^(\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+(?:\\.\\d+)?[½¼¾⅓⅔⅛]?|[½¼¾⅓⅔⅛])")
    private val RANGE = Regex("^\\s*[-–]\\s*")

    fun parse(line: String): ParsedIngredient {
        val text = line.trim()
        if (text.isEmpty() || text.length > MAX_LINE) return ParsedIngredient.Unreadable
        val grams = GRAMS.findAll(text).lastOrNull()?.groupValues?.get(1)?.toDoubleOrNull()
        val head = text.replace(BRACKETS, " ").substringBefore(',').trim()
        val (qty, rest) = readQuantity(head) ?: return ParsedIngredient.Unreadable
        if (!(qty > 0.0 && qty.isFinite())) return ParsedIngredient.Unreadable

        val words = rest.split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        if (
            words.size >= 2 && words[0].equals("fl", true) && words[1].trim('.').equals("oz", true)
        ) {
            words[0] = "floz"
            words.removeAt(1)
        }
        val unit = words.firstOrNull()?.let { UnitConverter.lookup(it) }
        val amount: Amount
        if (unit != null) {
            words.removeAt(0)
            amount =
                if (grams != null && !unit.container) Amount(grams, UnitFamily.MASS)
                else Amount(qty * unit.factor, unit.family)
        } else {
            amount = Amount(qty, UnitFamily.COUNT)
        }
        if (words.firstOrNull().equals("of", true)) words.removeAt(0)
        val name = IngredientNames.display(words.joinToString(" "))
        if (name.isEmpty()) return ParsedIngredient.Unreadable
        val key = IngredientNames.key(name, amount.family)
        return ParsedIngredient.Parsed(key, name, amount)
    }

    /** The number at the start of [text] and the text after it, or null when there is none. */
    private fun readQuantity(text: String): Pair<Double, String>? {
        val m = NUMBER.find(text)
        if (m == null) {
            val low = text.lowercase()
            return when {
                low.startsWith("a ") -> 1.0 to text.drop(2)
                low.startsWith("an ") -> 1.0 to text.drop(3)
                else -> null
            }
        }
        var value = numberOf(m.value) ?: return null
        var rest = text.substring(m.range.last + 1)
        val range = RANGE.find(rest)
        if (range != null) {
            val after = rest.substring(range.range.last + 1)
            val upper = NUMBER.find(after)
            if (upper != null) {
                value = maxOf(value, numberOf(upper.value) ?: value)
                rest = after.substring(upper.range.last + 1)
            }
        }
        return value to rest.trim()
    }

    private fun numberOf(token: String): Double? {
        val t = token.trim()
        UNICODE[t.lastOrNull()]?.let { frac ->
            val whole = t.dropLast(1)
            return if (whole.isEmpty()) frac else (whole.toDoubleOrNull() ?: return null) + frac
        }
        if (t.contains(' ')) {
            val (whole, frac) = t.split(Regex("\\s+"), limit = 2)
            return (whole.toDoubleOrNull() ?: return null) + (fractionOf(frac) ?: return null)
        }
        return if (t.contains('/')) fractionOf(t) else t.toDoubleOrNull()
    }

    private fun fractionOf(token: String): Double? {
        val parts = token.split('/')
        if (parts.size != 2) return null
        val n = parts[0].toDoubleOrNull() ?: return null
        val d = parts[1].toDoubleOrNull() ?: return null
        return if (d == 0.0) null else n / d
    }

    internal fun maxName() = MAX_NAME
}

/** Names of ingredients: the words shown, and the key that makes equal ingredients one. */
object IngredientNames {
    private val SIZES = setOf("small", "medium", "large", "extra", "fresh", "raw", "ripe")
    private val PLAIN = Regex("[^a-z0-9 %-]")

    /** Lower case words without sizes and cooking words, the last one in the singular. */
    fun display(raw: String): String {
        val words =
            raw.lowercase().replace(PLAIN, " ").split(Regex("\\s+")).filter {
                it.isNotEmpty() && it !in SIZES
            }
        if (words.isEmpty()) return ""
        val last = singular(words.last())
        return (words.dropLast(1) + last).joinToString(" ").take(IngredientParser.maxName())
    }

    /** The key a preferred product is stored under: `ing:<name>|<g|ml|n>`. */
    fun key(name: String, family: UnitFamily): String =
        "ing:" + name + "|" + (family.short.ifEmpty { "n" })

    private fun singular(word: String): String =
        when {
            word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
            word.length > 4 && word.endsWith("oes") -> word.dropLast(2)
            word.length > 3 && word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us") ->
                word.dropLast(1)
            else -> word
        }
}
