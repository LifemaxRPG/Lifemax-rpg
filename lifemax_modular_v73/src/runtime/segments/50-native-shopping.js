/* Native Shopping projection (capability food.shopping.v1): the Walmart grocery cart on the Food tab's Shopping pane.
   The page stays the owner of everything: the week's list (sdShopList, already scaled by portions and servings), the recipes (RECIPES,
   the class rule of the Recipes page) and the preferred Walmart matches, the only thing saved. The native screen draws a projection
   and sends two strict commands, shopPrefSet and shopPrefClear; the cart link itself is built natively and never touches the save.
   Saved field: state.mealsMeta.walmart = { prefs: { "<ingredient key>": { id, label, note } } }, bounded and read defensively, so it
   travels in backups and an older app that does not know it simply ignores it. Inventory: docs/architecture/RECIPES_INVENTORY.md. */
(function () {
  const CAPABILITY = 'food.shopping.v1';
  const MAX_PREFS = 200;
  const KEY = /^(sd:[A-Za-z0-9_.-]{1,40}|ing:[a-z0-9 %-]{1,50}\|(g|ml|n))$/; // a Smart Diet row, or a recipe ingredient by name and unit
  const ID = /^\d{5,15}$/; // a Walmart item number
  const bootId = 'p' + Date.now().toString(36);
  let revision = 0;

  /** the saved matches, read defensively: anything not well formed is left out (and dropped at the next write) */
  function savedPrefs() {
    const w = state && state.mealsMeta && state.mealsMeta.walmart;
    const raw = w && typeof w === 'object' && w.prefs && typeof w.prefs === 'object' ? w.prefs : {};
    const out = [];
    Object.keys(raw).slice(0, MAX_PREFS * 2).forEach(function (key) {
      const p = raw[key];
      if (!KEY.test(key) || !p || typeof p !== 'object' || !ID.test(String(p.id))) return;
      if (out.length >= MAX_PREFS) return;
      out.push({ key: key, productId: String(p.id), label: typeof p.label === 'string' ? p.label.slice(0, 80) : '', note: typeof p.note === 'string' ? p.note.slice(0, 120) : '' });
    });
    return out;
  }

  /** the rows of the Smart Diet shopping list the Food projection already holds (`shop.items`), as the screen reads them */
  function weekItems(items) {
    return (items || []).map(function (x) {
      return {
        id: String(x.id).slice(0, 40),
        name: String(x.name || x.id).slice(0, 80),
        aisleName: String(x.aisleName || '').slice(0, 40),
        grams: Math.max(0, Math.round(Number(x.grams) || 0)),
        packGrams: Math.max(0, Number(x.packG) || 0),
        unit: String(x.unit || 'pack').slice(0, 40),
        home: !!x.home
      };
    }).filter(function (x) { return /^[A-Za-z0-9_.-]{1,40}$/.test(x.id); }).slice(0, 300);
  }

  /** the recipes the Recipes page shows for this class, with the servings their ingredient lines make */
  function recipes() {
    return recipeHits('', 'all').slice(0, 150).map(function (r) {
      const made = parseInt(r.yield, 10);
      return {
        id: String(r.id).slice(0, 60),
        name: String(r.n).slice(0, 100),
        servings: made >= 1 && made <= 100 ? made : 1,
        lines: (r.ingredients || []).slice(0, 40).map(function (t) { return String(t).slice(0, 200); })
      };
    });
  }

  /** the `shopping` object of the Food projection (rev rises each time, so a late older one is ignored) */
  function projection(shop, answer) {
    return {
      version: 1,
      session: bootId,
      revision: ++revision,
      weekStart: shop.weekStart,
      nextWeek: !!shop.nextWeek,
      items: weekItems(shop.items),
      recipes: recipes(),
      prefs: savedPrefs(),
      answer: answer || null
    };
  }

  function validText(v, max) { return v === undefined || v === null || v === '' || (typeof v === 'string' && v.length <= max); }

  /** shopPrefSet / shopPrefClear. Returns false when nothing was saved (bad input, or the save failed: the change is undone),
      'same' when it already held (answered ok, changed: false), true when saved. Repeat-safe: the same command twice saves once. */
  function command(name, e) {
    if (typeof mealsEnsure !== 'function' || !state || !state.mealsMeta) return false;
    const key = typeof e.key === 'string' ? e.key : '';
    if (!KEY.test(key)) return false;
    const before = state.mealsMeta.walmart;
    const prefs = {};
    savedPrefs().forEach(function (p) { prefs[p.key] = { id: p.productId, label: p.label, note: p.note }; });
    if (name === 'shopPrefClear') {
      if (!prefs[key]) return 'same';
      delete prefs[key];
    } else if (name === 'shopPrefSet') {
      if (typeof e.productId !== 'string' || !ID.test(e.productId) || !validText(e.label, 80) || !validText(e.note, 120)) return false;
      const next = { id: e.productId, label: e.label ? e.label.trim() : '', note: e.note ? e.note.trim() : '' };
      const had = prefs[key];
      if (had && had.id === next.id && had.label === next.label && had.note === next.note) return 'same';
      if (!had && Object.keys(prefs).length >= MAX_PREFS) return false;
      prefs[key] = next;
    } else {
      return false;
    }
    state.mealsMeta.walmart = { prefs: prefs };
    if (save()) return true;
    if (before === undefined) delete state.mealsMeta.walmart; else state.mealsMeta.walmart = before; // not saved: keep what the owner had
    return false;
  }

  window.NativeShopping = Object.freeze({ capability: CAPABILITY, projection: projection, command: command, commands: ['shopPrefSet', 'shopPrefClear'] });
})();
