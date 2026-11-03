package com.lifemax.rpg.feature.shopping

import com.lifemax.rpg.feature.shopping.domain.Amount
import com.lifemax.rpg.feature.shopping.domain.GroceryItem
import com.lifemax.rpg.feature.shopping.domain.GroceryListBuilder
import com.lifemax.rpg.feature.shopping.domain.IngredientParser
import com.lifemax.rpg.feature.shopping.domain.ParsedIngredient
import com.lifemax.rpg.feature.shopping.domain.RecipePick
import com.lifemax.rpg.feature.shopping.domain.SourceIngredient
import com.lifemax.rpg.feature.shopping.domain.UnitConverter
import com.lifemax.rpg.feature.shopping.domain.UnitFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroceryListBuilderTest {
    private fun parsed(line: String) = IngredientParser.parse(line) as ParsedIngredient.Parsed

    private fun pick(id: String, made: Int, wanted: Double, vararg lines: String) =
        RecipePick(id, id, made, wanted, lines.toList())

    // ---- parsing and unit conversion ---------------------------------------------------------

    @Test
    fun aWeightInBracketsWinsOverTheVolume() {
        val p = parsed("1/2 cup rolled oats (40g)")
        assertEquals(40.0, p.amount.value, 1e-9)
        assertEquals(UnitFamily.MASS, p.amount.family)
        assertEquals("rolled oat", p.name)
    }

    @Test
    fun poundsOuncesAndKilosBecomeGrams() {
        assertEquals(453.592, parsed("1 lb chicken breast").amount.value, 1e-6)
        assertEquals(113.398, parsed("4 oz cheddar").amount.value, 1e-3)
        assertEquals(1500.0, parsed("1.5 kg potatoes").amount.value, 1e-9)
    }

    @Test
    fun volumesBecomeMillilitres() {
        assertEquals(14.7868, UnitConverter.toBase(1.0, "tablespoon")!!.value, 1e-9)
        assertEquals(4.92892, UnitConverter.toBase(1.0, "tsp")!!.value, 1e-9)
        assertEquals(473.176, parsed("2 cups milk").amount.value, 1e-3)
        assertEquals(UnitFamily.VOLUME, parsed("2 cups milk").amount.family)
        assertEquals(29.5735, parsed("1 fl oz vanilla").amount.value, 1e-4)
    }

    @Test
    fun fractionsOfEveryWrittenKindAreRead() {
        assertEquals(0.5 * 236.588, parsed("1/2 cup milk").amount.value, 1e-3)
        assertEquals(1.5 * 236.588, parsed("1 1/2 cups milk").amount.value, 1e-3)
        assertEquals(1.5 * 236.588, parsed("1.5 cups milk").amount.value, 1e-3)
        assertEquals(0.5 * 236.588, parsed("½ cup milk").amount.value, 1e-3)
        assertEquals(1.5 * 236.588, parsed("1½ cups milk").amount.value, 1e-3)
        assertEquals(3.0, parsed("2-3 bananas").amount.value, 1e-9)
        assertEquals(0.25, parsed("1/4 medium Hass avocado, diced (34g)").amount.value, 1e-9)
    }

    @Test
    fun sizesAndDescriptionsAreDroppedFromTheName() {
        val p = parsed("2 large whole eggs (100g)")
        assertEquals("whole egg", p.name)
        assertEquals(Amount(2.0, UnitFamily.COUNT), p.amount)
        assertEquals(
            "boneless skinless chicken breast",
            parsed("1 lb boneless skinless chicken breast, cubed (454g)").name,
        )
    }

    @Test
    fun thingsBoughtWholeStayCountedEvenWithAWeightBeside() {
        val p = parsed("1 can chickpeas, rinsed and drained (240g)")
        assertEquals(Amount(1.0, UnitFamily.COUNT), p.amount)
        assertEquals("chickpea", p.name)
    }

    @Test
    fun linesWithoutAReadableAmountAreUnreadable() {
        assertEquals(
            ParsedIngredient.Unreadable,
            IngredientParser.parse("Pinch of salt and black pepper"),
        )
        assertEquals(ParsedIngredient.Unreadable, IngredientParser.parse(""))
        assertEquals(ParsedIngredient.Unreadable, IngredientParser.parse("0 cups milk"))
        assertEquals(ParsedIngredient.Unreadable, IngredientParser.parse("1/0 cup milk"))
        assertEquals(ParsedIngredient.Unreadable, IngredientParser.parse("x".repeat(500)))
    }

    // ---- consolidation, scaling, packages -----------------------------------------------------

    @Test
    fun identicalIngredientsOfAllRecipesBecomeOneRow() {
        val list =
            GroceryListBuilder.build(
                emptyList(),
                listOf(
                    pick("a", 1, 1.0, "1/2 cup rolled oats (40g)", "1 teaspoon honey (7g)"),
                    pick("b", 1, 1.0, "1/2 cup rolled oats (40g)"),
                ),
            )
        val oats = list.items.single { it.name == "rolled oat" }
        assertEquals(80.0, oats.amount.value, 1e-9)
        assertEquals(listOf("a", "b"), oats.origins)
        assertEquals(2, list.items.size)
    }

    @Test
    fun compatibleUnitsAddUpAndIncompatibleOnesStaySeparate() {
        val list =
            GroceryListBuilder.build(
                emptyList(),
                listOf(
                    pick(
                        "a",
                        1,
                        1.0,
                        "1 cup milk",
                        "2 tablespoons milk",
                        "1 can milk",
                        "100 g milk",
                    )
                ),
            )
        val milk = list.items.filter { it.name == "milk" }
        assertEquals(3, milk.size) // volume, count and mass are never added together
        assertEquals(
            236.588 + 2 * 14.7868,
            milk.single { it.amount.family == UnitFamily.VOLUME }.amount.value,
            1e-3,
        )
    }

    @Test
    fun servingsScaleEveryLineOfARecipe() {
        val twice =
            GroceryListBuilder.build(emptyList(), listOf(pick("a", 2, 4.0, "100 g rice", "2 eggs")))
        assertEquals(200.0, twice.items.single { it.name == "rice" }.amount.value, 1e-9)
        assertEquals(4.0, twice.items.single { it.name == "egg" }.amount.value, 1e-9)
        val half = GroceryListBuilder.build(emptyList(), listOf(pick("a", 4, 2.0, "100 g rice")))
        assertEquals(50.0, half.items.single().amount.value, 1e-9)
    }

    @Test
    fun anUnusableServingsRequestScalesByOne() {
        val list = GroceryListBuilder.build(emptyList(), listOf(pick("a", 2, 0.0, "100 g rice")))
        assertEquals(100.0, list.items.single().amount.value, 1e-9)
        val nan =
            GroceryListBuilder.build(emptyList(), listOf(pick("a", 0, Double.NaN, "100 g rice")))
        assertEquals(100.0, nan.items.single().amount.value, 1e-9)
    }

    @Test
    fun theWeekListIsUsedAsTheSourceAndAddsToTheSameKey() {
        val week =
            listOf(
                SourceIngredient(
                    "sd:oats",
                    "Oats",
                    Amount(300.0, UnitFamily.MASS),
                    500.0,
                    "bag",
                    "Pantry",
                ),
                SourceIngredient(
                    "sd:oats",
                    "Oats",
                    Amount(250.0, UnitFamily.MASS),
                    500.0,
                    "bag",
                    "Pantry",
                ),
            )
        val list = GroceryListBuilder.build(week, emptyList())
        assertEquals(550.0, list.items.single().amount.value, 1e-9)
        assertEquals(2, list.items.single().suggestedQuantity)
    }

    @Test
    fun packagesRoundUpWithThePagesTolerance() {
        fun packs(grams: Double, pack: Double?) =
            GroceryItem.packagesFor(Amount(grams, UnitFamily.MASS), pack)
        assertEquals(1, packs(1.0, 500.0))
        assertEquals(1, packs(500.0, 500.0))
        assertEquals(1, packs(515.0, 500.0)) // 3 percent over: still one pack, as on the page
        assertEquals(2, packs(530.0, 500.0))
        assertEquals(2, packs(1001.0, 500.0)) // 0.4 percent over two packs: still two
        assertEquals(3, packs(1100.0, 500.0))
        assertEquals(1, packs(0.0, 500.0))
        assertEquals(1, packs(5000.0, null)) // no package size known: one, to be edited
        assertEquals(1, packs(100.0, 0.0))
    }

    @Test
    fun countsRoundUpToWholeItems() {
        assertEquals(3, GroceryItem.packagesFor(Amount(2.25, UnitFamily.COUNT), null))
        assertEquals(2, GroceryItem.packagesFor(Amount(2.0, UnitFamily.COUNT), null))
        assertEquals(1, GroceryItem.packagesFor(Amount(0.25, UnitFamily.COUNT), null))
    }

    @Test
    fun unreadableLinesAreKeptForReviewWithTheirRecipe() {
        val list =
            GroceryListBuilder.build(
                emptyList(),
                listOf(pick("Pasta", 1, 1.0, "Pinch of salt", "200 g pasta")),
            )
        assertEquals(1, list.items.size)
        assertEquals("Pinch of salt", list.review.single().text)
        assertEquals("Pasta", list.review.single().origin)
    }

    @Test
    fun amountsReadCleanly() {
        assertEquals("454 g", Amount(454.0, UnitFamily.MASS).text())
        assertEquals("1.5 kg", Amount(1500.0, UnitFamily.MASS).text())
        assertEquals("250 ml", Amount(250.0, UnitFamily.VOLUME).text())
        assertEquals("2.5", Amount(2.5, UnitFamily.COUNT).text())
        assertTrue(Amount(0.99, UnitFamily.COUNT).text() == "1")
    }
}
