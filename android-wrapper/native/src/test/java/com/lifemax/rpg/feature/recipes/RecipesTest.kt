package com.lifemax.rpg.feature.recipes

import com.lifemax.rpg.feature.recipes.data.WebRecipesRepository
import com.lifemax.rpg.feature.recipes.data.toJson
import com.lifemax.rpg.feature.recipes.domain.RecipeFilter
import com.lifemax.rpg.feature.recipes.domain.RecipesPageAction
import com.lifemax.rpg.feature.recipes.presentation.IngredientRef
import com.lifemax.rpg.feature.recipes.presentation.RecipesViewModel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipesTest {
    private fun fixture(name: String = "scholar") =
        JSONObject(javaClass.getResource("/fixtures/recipes-$name.json")!!.readText())

    /** The fixture with its filter set as the page sets it. */
    private fun JSONObject.filter(query: String, category: String, revision: Long) =
        put(
            "filter",
            JSONObject().put("query", query).put("category", category).put("revision", revision),
        )

    private fun JSONObject.recipe(index: Int): JSONObject =
        getJSONArray("recipes").getJSONObject(index)

    private fun setUp(name: String = "scholar"): Pair<WebRecipesRepository, RecipesViewModel> {
        val repo = WebRecipesRepository().apply { accept(fixture(name), true) }
        return repo to RecipesViewModel(repo).apply { onProjection() }
    }

    private fun RecipesViewModel.ids() = ui.value.hits.map { it.id }

    @Test
    fun decodesTheWebProjection() {
        val p = WebRecipesRepository().apply { accept(fixture(), true) }.state.value!!
        assertEquals(19, p.recipes.size)
        assertEquals(9, p.recipes.count { it.kcal.endsWith(" kcal (est.)") })
        assertEquals(
            listOf("all", "Breakfast", "Pre-workout", "Post-workout", "Lunch & dinner"),
            p.categories.map { it.value },
        )
        assertEquals("All recipes", p.categories.first().label)
        assertEquals(RecipeFilter("", "all"), p.filter)
        assertEquals(1L, p.filterRevision)
        val pesto = p.recipes.first { it.id == "rcp_pesto_chicken" }
        assertEquals("2 servings · Prep 5 min · Total 35 min", pesto.meta)
        assertEquals(
            listOf("350 kcal", "44g", "10g", "16g"),
            listOf(pesto.kcal, pesto.protein, pesto.carbs, pesto.fat),
        )
        assertTrue(pesto.ingredients[0].link.startsWith("https://www.walmart.com/"))
        assertTrue(pesto.ingredients.drop(1).all { it.link.isEmpty() })
        assertEquals(
            "Make ahead and refrigerate or freeze; reheat before serving.",
            p.recipes.first { it.id == "rcp_egg_muffins" }.note,
        )
        assertEquals("Nutrition per serving, as listed in the cookbook.", pesto.footnote)
        val snacks = WebRecipesRepository().apply { accept(fixture("entrepreneur"), true) }
        assertEquals(2, snacks.state.value!!.recipes.count { it.category == "Snack" })
    }

    @Test
    fun olderAndForeignUpdatesAreIgnored() {
        val repo = WebRecipesRepository()
        repo.accept(fixture().put("sequence", 2), true)
        repo.accept(fixture("disciple"))
        assertEquals(19, repo.state.value!!.recipes.size)
        repo.accept(fixture("disciple").put("sequence", 9).put("webSessionId", "other"))
        assertEquals("recipes-fixture", repo.state.value!!.session)
        repo.accept(fixture("disciple").put("webSessionId", "other"), establishSession = true)
        assertEquals(10, repo.state.value!!.recipes.size)
    }

    @Test
    fun malformedProjectionsKeepTheLastGoodOne() {
        val repo = WebRecipesRepository()
        repo.accept(fixture(), true)
        val broken =
            listOf(
                fixture().put("protocol", 2),
                fixture().put("webSessionId", ""),
                fixture().also { it.getJSONArray("recipes").put(it.recipe(0)) },
                fixture().also {
                    it.recipe(0)
                        .getJSONArray("ingredients")
                        .getJSONObject(0)
                        .put("link", "http://example.com/eggs")
                },
                fixture().also { it.recipe(1).remove("steps") },
                fixture().also { it.recipe(2).put("id", " ") },
                fixture().filter("", "Snack", 2),
                fixture().filter("", "all", 0),
                fixture().also { it.getJSONArray("categories").remove(0) },
                fixture().put("categories", JSONArray()),
                fixture().also { it.remove("tokens") },
            )
        for (projection in broken) {
            val sequence = repo.state.value!!.sequence + 1
            runCatching { repo.accept(projection.put("sequence", sequence)) }
            assertEquals(1L, repo.state.value!!.sequence)
        }
        assertEquals(19, repo.state.value!!.recipes.size)
    }

    /** recipes-filters.json: for each fixture, the ids the page's own filter showed per search. */
    private val pageFilters: Map<String, List<Pair<RecipeFilter, List<String>>>> by lazy {
        val json = JSONObject(javaClass.getResource("/fixtures/recipes-filters.json")!!.readText())
        json.keys().asSequence().associateWith { name ->
            val cases = json.getJSONArray(name)
            (0 until cases.length()).map { i ->
                val case = cases.getJSONObject(i)
                val ids = case.getJSONArray("ids")
                RecipeFilter(case.getString("query"), case.getString("category")) to
                    (0 until ids.length()).map(ids::getString)
            }
        }
    }

    @Test
    fun filtersExactlyAsThePage() {
        for ((name, cases) in pageFilters) {
            val (_, vm) = setUp(name)
            for ((filter, ids) in cases) {
                vm.search(filter.query)
                vm.choose(filter.category)
                assertEquals("$name $filter", ids, vm.ids())
            }
        }
    }

    /** The searches that tell the rule apart are in the fixture with the page's answers. */
    @Test
    fun theFixtureHoldsTheTellingSearches() {
        val scholar = pageFilters.getValue("scholar").toMap()
        // the category is searched with the ingredients
        assertEquals(listOf("rcp_egg_muffins"), scholar[RecipeFilter("breakfast 12 eggs")])
        // one phrase, not two words; the steps are not searched
        assertEquals(emptyList<String>(), scholar[RecipeFilter("chicken rice")])
        assertEquals(emptyList<String>(), scholar[RecipeFilter("preheat")])
        assertEquals(
            listOf("rcp_chicken_salad", "rcp_pesto_chicken", "rcp_sheet_pan_chicken"),
            scholar[RecipeFilter("  CHICKEN ")],
        )
    }

    /**
     * JavaScript's trim(): a no-break space, an em space and a byte order mark go; U+001C stays.
     */
    @Test
    fun trimsAsJavaScriptDoes() {
        val (_, vm) = setUp()
        vm.search("\u00A0Chicken\u2003\uFEFF")
        assertEquals(3, vm.ids().size)
        vm.search("\u001Cchicken")
        assertEquals(emptyList<String>(), vm.ids())
    }

    @Test
    fun aCategoryNotOnTheMenuIsIgnored() {
        val (_, vm) = setUp("entrepreneur")
        vm.choose("Snack")
        assertEquals("all", vm.ui.value.filter.category)
        assertEquals(18, vm.ids().size)
    }

    /** The diet card's search is taken over once; the screen's own search then stays. */
    @Test
    fun takesOverAPrefillOnce() {
        val (repo, vm) = setUp("prefill")
        assertEquals(RecipeFilter("Overnight Blueberry Chia Oats", "all"), vm.ui.value.filter)
        assertEquals(listOf("rcp_blueberry_oats"), vm.ids())

        vm.search("egg")
        repo.accept(fixture("prefill").put("sequence", 2))
        vm.onProjection()
        assertEquals("egg", vm.ui.value.filter.query)
        assertEquals(4, vm.ids().size)

        repo.accept(fixture("prefill").filter("", "Breakfast", 3).put("sequence", 3))
        vm.onProjection()
        assertEquals(RecipeFilter("", "Breakfast"), vm.ui.value.filter)
        assertEquals(6, vm.ids().size)

        // a reloaded page starts its revisions again, and its filter is the screen's start
        repo.accept(fixture().put("webSessionId", "reloaded"), establishSession = true)
        vm.onProjection()
        assertEquals(RecipeFilter(), vm.ui.value.filter)
        assertEquals(19, vm.ids().size)
    }

    @Test
    fun openRecipesAndTheSelectedIngredientOutliveUpdates() {
        val (repo, vm) = setUp()
        vm.toggle("rcp_egg_muffins")
        vm.toggle("rcp_pesto_chicken")
        vm.toggle("rcp_egg_muffins")
        vm.select(IngredientRef("rcp_egg_muffins", 2))
        vm.select(IngredientRef("rcp_pesto_chicken", 0))
        repo.accept(fixture().put("sequence", 2))
        vm.onProjection()
        assertEquals(setOf("rcp_pesto_chicken"), vm.ui.value.expanded)
        assertEquals(IngredientRef("rcp_pesto_chicken", 0), vm.ui.value.selected)
    }

    @Test
    fun logReachesThePageAsItReadsIt() {
        assertEquals(
            "rcp_pesto_chicken",
            RecipesPageAction.Log("rcp_pesto_chicken").toJson().getString("id"),
        )
    }
}
