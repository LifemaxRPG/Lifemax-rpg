package com.lifemax.rpg.feature.shopping.domain

/**
 * Turns the units written in recipes into the base unit of their family: grams, millilitres or a
 * count. Compatible units (cups and tablespoons, pounds and grams) then add up; incompatible ones
 * (a volume and a mass) never do.
 */
object UnitConverter {
    private class U(val family: UnitFamily, val factor: Double, val container: Boolean = false)

    private val UNITS: Map<String, U> = buildMap {
        fun put(u: U, vararg names: String) = names.forEach { put(it, u) }
        put(U(UnitFamily.MASS, 1.0), "g", "gram", "grams", "gm")
        put(U(UnitFamily.MASS, 1000.0), "kg", "kilogram", "kilograms", "kilo")
        put(U(UnitFamily.MASS, 28.3495), "oz", "ounce", "ounces")
        put(U(UnitFamily.MASS, 453.592), "lb", "lbs", "pound", "pounds")
        put(U(UnitFamily.VOLUME, 1.0), "ml", "milliliter", "milliliters", "millilitre")
        put(U(UnitFamily.VOLUME, 1000.0), "l", "liter", "liters", "litre", "litres")
        put(U(UnitFamily.VOLUME, 4.92892), "tsp", "teaspoon", "teaspoons")
        put(U(UnitFamily.VOLUME, 14.7868), "tbsp", "tablespoon", "tablespoons", "tbs")
        put(U(UnitFamily.VOLUME, 236.588), "cup", "cups")
        put(U(UnitFamily.VOLUME, 29.5735), "floz")
        put(U(UnitFamily.VOLUME, 946.353), "quart", "quarts", "qt")
        // things bought whole: counted, and a weight written beside them is ignored
        val count = U(UnitFamily.COUNT, 1.0, container = true)
        put(count, "can", "cans", "jar", "jars", "bottle", "bottles", "bag", "bags")
        put(count, "package", "packages", "pack", "packs", "box", "boxes", "carton", "cartons")
        val piece = U(UnitFamily.COUNT, 1.0)
        put(piece, "slice", "slices", "clove", "cloves", "piece", "pieces", "head", "heads")
        put(piece, "stalk", "stalks", "sprig", "sprigs", "bunch", "bunches", "fillet", "fillets")
    }

    /** A unit word read from a recipe, or null if the word is not a unit. */
    fun lookup(word: String): Resolved? {
        val key = word.lowercase().trim('.', ',', ' ')
        val u = UNITS[key] ?: return null
        return Resolved(u.family, u.factor, u.container)
    }

    /** A unit with its family, its size in base units and whether it is a container. */
    data class Resolved(val family: UnitFamily, val factor: Double, val container: Boolean)

    /** [value] of [unit] in base units, or null if the unit is unknown. */
    fun toBase(value: Double, unit: String): Amount? =
        lookup(unit)?.let { Amount(value * it.factor, it.family) }
}
