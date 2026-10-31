/* Recipes projection v1. The page stays the owner of the catalogue, the class rule and the wording: the native screen draws the
   recipes the page shows for the player's class, worded as the page words them, keeps its own search, category, open recipes
   and selected ingredient, opens an ingredient's product link itself, goes to Meals by naming the view, and hands "Log this
   recipe" to the page's add-food sheet (the command log). The tab lifecycle is NativeTabKit's.
   Inventory: docs/architecture/RECIPES_INVENTORY.md. */
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
      the screen's. Once the screen covers the page, the page's own fields go back to empty (emptyPage), so a prefill is taken
      over once, whether the screen arrives or comes back, and stays on the page if the screen fails to open. */
  function takeOver() {
    const q = search(),
      c = category();
    if (q.value || c.value !== "all") filter = { query: q.value, category: c.value, revision: filter.revision + 1 };
  }
  function emptyPage() {
    const q = search(),
      c = category();
    if (!q.value && c.value === "all") return;
    q.value = "";
    c.value = "all";
    renderRecipes();
  }

  /** "Log this recipe": the page opens its add-food sheet over Meals while the screen still covers it, and the hand-over then
      closes the screen, so the classic Recipes page never shows in between. For a recipe the page does not know, nothing
      happens and the screen stays. */
  function log({ id }) {
    addRecipeToMeal(String(id));
    return { ok: lmaView() === "meals" };
  }

  NativeTabKit.create({
    id: "recipes",
    plugin: "NativeRecipes",
    capability: "recipes.projection.v1",
    setting: "nativeRecipes",
    toggle: "nativeRecipesToggle",
    projection,
    commands: { log },
    render: "renderRecipes",
    beforeOpen: takeOver,
    afterOpen: emptyPage,
    globals: { open: "openNativeRecipes", enable: "setNativeRecipesEnabled", projection: "NativeRecipesTabProjection" },
  });
})();
