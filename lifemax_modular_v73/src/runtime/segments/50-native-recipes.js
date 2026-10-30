/* Recipes projection v1. The page stays the owner of the catalogue, the class rule and the wording: the native screen draws the
   recipes the page shows for the player's class, worded as the page words them, keeps its own search, category, open recipes
   and selected ingredient, opens an ingredient's product link itself, goes to Meals by naming the view, and hands "Log this
   recipe" to the page's add-food sheet. The tab lifecycle is NativeTabKit's. Inventory: docs/architecture/RECIPES_INVENTORY.md. */
(function () {
  const search = () => document.getElementById("recipeSearch");
  const category = () => document.getElementById("recipeCategory");
  let filter = { query: "", category: "all", revision: 1 }; // what the screen starts from; revision rises with each prefill

  function recipeOf(r) {
    return Object.assign({ id: r.id, name: r.n, category: r.cat }, recipeCard(r), {
      ingredients: r.ingredients.map((text, i) => ({ text, link: RECIPE_LINKS[r.id]?.[i] || "" })),
      steps: r.steps,
      note: r.note || "",
      searchText: recipeHaystack(r),
    });
  }
  function projection() {
    return {
      categories: [...category().options].map((o) => ({ value: o.value, label: o.textContent })),
      filter,
      recipes: recipeHits("", "all").map(recipeOf),
    };
  }

  /** A search or category the page set before the screen opened (the diet card's Recipe button writes the recipe's name) becomes
      the screen's. The page's own fields go back to empty, so a prefill is taken over once, whether the screen arrives or comes back. */
  function takeOver() {
    const q = search(),
      c = category();
    if (!q.value && c.value === "all") return;
    filter = { query: q.value, category: c.value, revision: filter.revision + 1 };
    q.value = "";
    c.value = "all";
    renderRecipes();
  }

  /** "Log this recipe", after the screen has closed: the page's add-food sheet over Meals; for a recipe it does not know, the
      page stays on Recipes and the screen comes back */
  function onAction(e) {
    if (e.action === "log") addRecipeToMeal(String(e.id));
    tab.revisit();
  }

  const tab = NativeTabKit.create({
    id: "recipes",
    plugin: "NativeRecipes",
    capability: "recipes.projection.v1",
    setting: "nativeRecipes",
    toggle: "nativeRecipesToggle",
    projection,
    onAction,
    render: "renderRecipes",
    beforeOpen: takeOver,
    globals: { open: "openNativeRecipes", enable: "setNativeRecipesEnabled", projection: "NativeRecipesTabProjection" },
  });
})();
