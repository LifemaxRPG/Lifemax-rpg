# Recipes native slice inventory

Baseline: branch `track/docs` at `d56403c0410c73686ce8c5675d16cbe627e2ebd1` (2026-10-30, Native API 23). Read-only audit; no code was changed.

Reference conventions. Paths are relative to `lifemax_modular_v73/src/`. Runtime segments are cited by file name alone (they all live in `runtime/segments/`, for example `08-runtime.js:185`). Kotlin paths are relative to `android-wrapper/native/src/main/java/com/lifemax/rpg/`. All segments are pasted by `build.py` into ONE `<script>` (`index.template.html:132`), so for a duplicated `function` declaration the last one in load order wins for the whole page, including calls from earlier segments.

How facts were checked. "Verified" means read in the source at this baseline. Facts marked **(run)** were also observed in headless Chromium (Playwright 1.63) on a page assembled from this worktree's `src` into the scratchpad, and facts marked **(sim)** additionally used fake `NativeNutrition`/`NativeOverview` Capacitor plugins (same shape as the fakes in `tests/native-handover-sim.test.js`). None of this is device evidence. Anything not verified is marked **(unverified)**.

---

## 1. Summary

**What the pane is for.** A read-only cookbook. It lists the 10 recipes of the "Fit to Win Athlete Cookbook" plus the recipes of the player's class (marked "(est.)"), lets the player filter by text and category, expand a recipe for ingredients and steps, tap an ingredient (one ingredient carries a Walmart link), and hand a recipe to the Meals logger as one serving. The pane itself never writes the save; its only write path is the delegated Meals add-food sheet.

**Page view.** `recipes` = `<section id="view-recipes" class="view">` (`views/recipes.html:1-5`). There is no `view-food`: the bottom-bar key `food` is a group, `LMA_GROUPS.food=['meals','recipes']` (`34-navigation.js:7`); `lmaTabKey` maps both views to `food` (`34-navigation.js:8`); `lmaResolve('food')` returns `state.foodPane||'meals'` (`34-navigation.js:9`). `state.foodPane` is written by the `navigate` wrapper at `42-scrubnav.js:204` on every visit to either view, so "Food" reopens the last pane, across restarts (section 6).

**Relation to the native Food screen.** Native Food (`50-native-nutrition.js`, Kotlin `feature/food/presentation/*`, plugin `bridge/web/NativeNutritionPlugin.kt`) covers only the `meals` view: it opens only when `lmaView()==='meals'` (`50-native-nutrition.js:345-346`) and registers with `NativeTabs` under `meals` only (`50-native-nutrition.js:601-610`). Its header draws a Meals/Recipes switch (`feature/food/presentation/FoodDateHeader.kt:32-33`); "Recipes" dispatches the command `recipes`, which `onAction` does not handle, so it falls through to `fallback` (`50-native-nutrition.js:578`), which closes the native screen and calls plain `navigate('recipes')` (`50-native-nutrition.js:432-437`). Recipes is therefore always the classic web page today, including for owners who use the Native Food beta. The native Food Log pane does not draw the class diet card, so the diet card's "Recipe" buttons (an entry point into this pane, section 1 table) exist only on the classic web Meals page (verified: no diet fields in `buildPlanSnapshot`/`snapshot`, `50-native-nutrition.js:96-303`, and no diet composable in `feature/food/presentation`).

**Entry points (all verified).**

| Entry | Where | Path into the pane |
|---|---|---|
| Bottom-bar "Food" (icon `heart`) when `state.foodPane==='recipes'` | `index.template.html:46` (`data-view="food"`, `onclick="lmaNavTo('food')"`); phone taps captured by `42-scrubnav.js:189-195` | `lmaNavTo('food')` -> `lmaResolve` -> `navigate('recipes')` |
| Meals/Recipes switch on the classic Meals page | `views/meals.html:2` | `lmaNavTo('recipes')` |
| Meals/Recipes switch on native Food | `FoodDateHeader.kt:33` -> `NativeNutritionPlugin.kt:87` (`action` event) -> `50-native-nutrition.js:455,578,432-437` | close native Food, then `navigate('recipes')` (no view transition; recorded in Back history) **(sim)** |
| Class diet card "Recipe" button (classic web Meals, Log pane only) | `renderDietPlan` `14-runtime.js:81`, mounted by `50-food.js:99-101` | `dietOpenRecipe(id)` `14-runtime.js:69`: writes the recipe name into `#recipeSearch`, then `navigate('recipes')` |
| Native tab bars (Character, Food, Log screens) | `NativeOverviewSnapshot.activate` `50-native-overview.js:165-175` | `lmaNavTo('food',{fast:true})`; with `foodPane==='recipes'` this lands on web Recipes and the native screen closes at once (web destination, A23 item 2) **(sim)** |
| Bottom-bar scrubbing and tab swipe | `42-scrubnav.js:138-146,182`, `34-navigation.js:156,178,184` | through the `food` key and `foodPane`; swiping never moves between Meals and Recipes (one tab) |
| Back history | `34-navigation.js:69-74`, stack kept by `34-navigation.js:77-91` | previous view; from Recipes, Back returns to the view before it |
| Voice "open recipes", "show recipes", "cookbook" | `46-voice-nlu.js:38` (section table, checked before `food` at `:39`), `:65` (`bareNav` includes `recipes`), verbs `:23` | `lmvGo({id:'recipes'})` -> `lmaNavTo('recipes')` (`46-voice-nlu.js:121-127`) |
| Widgets, notifications, deep links, launcher shortcuts, cloud voice "open screen" | none: widget is the Life Director dial (`40-widget.js`); deep-link features are feed/schedule/quests/cats (`50-deeplink.js:7-14`); Gemini enum is `schedule|cats|quests` (`50-voice-gemini.js:17`); no food entry in `android-wrapper/res/xml/shortcuts.xml` | none |
| Developer class switch | `switchClassTo` `14-runtime.js:121` | re-renders Recipes (no navigation) |

**Size.** Markup 5 lines (`views/recipes.html`). Logic about 30 lines: `renderRecipes` `08-runtime.js:185-191`, `selectRecipeIngredient` `08-runtime.js:192-202`, live `addRecipeToMeal` `50-food.js:132` (the copy at `08-runtime.js:203-212` is dead, section 5.1), `dietOpenRecipe` `14-runtime.js:69`. Data: 43 recipes (10 cookbook + 33 class), 29.6 KB as JSON, embedded in JS (`02-runtime.js:1-12`, class arrays in `00-runtime.js:43`, `01-runtime.js:17-26`, `02-runtime.js:13`, `03-runtime.js:1`, merged at `03-runtime.js:2-5`). No CSS of its own: shared `.card`, `.grid`, `.tag`, `.field`, `.libseg` rules plus inline styles. Rendered DOM for a Scholar: 19 cards, about 1,020 elements, 103 KB of HTML, one 6,200 px column at 412 px width **(run)**.

**Native risk: low for presentation, medium for navigation.**
1. Sticky pane routing: once the owner taps "Recipes" anywhere, `state.foodPane='recipes'` is persisted (by the next `save()` or by `markVisit` within 30 s, `05-runtime.js:66,71`), and every later "Food" tap opens the classic web Recipes page instead of native Food, until "Meals" is tapped again **(sim)**. Any native Recipes design has to settle this first (section 8, question 1).
2. "Log this recipe" leaves the owner on classic web Meals, not native Food: the add-food sheet opens over the web Meals page, native Food's `open()` refuses while a `.sheet-ov.show` exists (`50-native-nutrition.js:350`), the `NativeTabs.heal` retries end after about 1.8 s, and nothing reopens native Food after the sheet closes **(sim)**.
3. The pane is fully derived from static data plus `state.classId`, so a projection is small (7-14 KB) and has no write path; the only mutation reachable from it (`fv2Log`) already belongs to the Food slice.

---

## 2. States

| State | What decides it | Where |
|---|---|---|
| Never rendered | Boot calls `renderAll()` (`13-runtime.js:115`), which ends with `renderRecipes()` (`04-runtime.js:182`), so the list exists (hidden) from boot; there is no loading or error state (data is synchronous) | `04-runtime.js:182`, `08-runtime.js:185-186` (returns early if `#recipesList` is missing) |
| Onboarding not finished | `#onboarding` overlay covers the app; the pane is not reachable | `index.template.html:10-36` |
| First visit | No feature tip: `FEATURE_TIPS` has no `recipes` key, so `maybeShowFeatureTip('recipes')` returns at once | `13-runtime.js:29-92,96` |
| No class (`state.classId` null), Disciple, Diplomat | Only the 10 cookbook recipes (`!r.cls`); Disciple and Diplomat have no class recipes | `08-runtime.js:189`, `05-runtime.js:200-247` (6 classes) **(run: 10/10/10)** |
| Athlete / Scholar / Entrepreneur / Explorer | Cookbook plus that class's recipes: 18 / 19 / 18 / 18 | `08-runtime.js:189`, `03-runtime.js:2-5` **(run)** |
| Category filter | `#recipeCategory` value: `all`, `Breakfast`, `Pre-workout`, `Post-workout`, `Lunch & dinner`. There is no `Snack` option although 4 class recipes have `cat:'Snack'` (2 Entrepreneur, 2 Explorer): they show only under "All recipes" or by search (Entrepreneur: All 18, categories sum to 16) | `views/recipes.html:4`, `08-runtime.js:188-189` **(run)** |
| Text filter | `#recipeSearch` value, trimmed, lowercased, one substring tested against name + category + every ingredient joined by spaces; steps and notes are not searched; multi-word queries must be contiguous | `08-runtime.js:187,189` |
| Empty result | "No recipes match that search." (also shown when only the category caused it) | `08-runtime.js:190` **(run)** |
| Recipe collapsed / expanded | native `<details>` per card ("Ingredients & instructions"); no handler, no state kept. Any re-render collapses every card | `08-runtime.js:190` **(run: open card closed after `save()`)** |
| Ingredient selected | outline and `aria-pressed="true"` on one `.ingredient-pick` in the whole list; the card's `#recipeProduct-<id>` panel shown. Selecting in another card clears every outline but leaves the first card's panel open (it is never hidden) | `08-runtime.js:198-201` |
| Product panel with link / without | only `rcp_pesto_chicken` ingredient 0 ("8 oz chicken breast") has a URL; every other ingredient shows the "no link yet" panel | `08-runtime.js:194,201` **(run)** |
| Filters after restart | search and category live only in the DOM; a restart resets them to empty / All | `views/recipes.html:4` |
| Filters after `dietOpenRecipe` | search prefilled with the recipe name; category is NOT reset, so a non-matching category shows the empty state | `14-runtime.js:69`, `08-runtime.js:189` |
| Narrow screen (phone) | `.two-col` becomes one column at max-width 950 px; header wraps; form grid one column at 860 px | `styles/02-layout.css:318-323,325-351` **(run: one 388 px column at 412 px)** |
| Reduced motion | page fade-in stagger off | `styles/02-layout.css:817-827,840,844,853` |
| Under a native cover | not applicable today (no native screen covers `recipes`); `html.native-cover` would stop the fade-in | `styles/02-layout.css:818` |
| Bottom tab hidden by the owner | "Food" can be hidden in Settings (bar editor), leaving voice, the Meals switch and the diet card as entry points | `50-tabs-edit.js:22-24` |
| Save failed | not applicable to the pane itself; the delegated sheet's `fv2Log` ignores `save()`'s return (memory and storage can diverge) | `50-food.js:33`, `04-runtime.js:13` |

---

## 3. Displayed facts

### 3.1 Switch and header (`views/recipes.html:2-3`)

| Element | Shows | Computed by | Reads |
|---|---|---|---|
| `.libseg` Meals / Recipes | two pills; the current one has class `on` | `navigate` wrapper toggles `.on` where `data-lib===view` (`42-scrubnav.js:208`); style `styles/07-finishing.css:119-121` | current view |
| Eyebrow, title | "FUEL YOUR BUILD", "Recipes" | markup | none |
| Intro | "Recipes from the Fit to Win Athlete Cookbook. Nutrition values are shown as printed in the cookbook; recipe variations may change them. Class-plan recipes are marked (est.) and use estimated nutrition." | markup | none |
| Header button | "＋ Log a meal" | markup | none |
| Bottom-bar highlight | "Food" lit, not a `recipes` button (there is none) | `lmaSyncActive` `34-navigation.js:23-25`, `42-scrubnav.js:206-207` **(run)** | `lmaTabKey(view)` |

### 3.2 Filter card (`views/recipes.html:4`)

| Element | Shows | Notes |
|---|---|---|
| `#recipeSearch` | label "Find a recipe", `type=search`, placeholder "Search recipes or ingredients…" | no `maxlength`; Android keyboard; tab swipe is blocked while touching it (`34-navigation.js:101`) |
| `#recipeCategory` | label "Category"; options "All recipes", Breakfast, Pre-workout, Post-workout, Lunch & dinner | the option list is static markup, not derived from the data (hence the missing Snack) |

### 3.3 Recipe card (`renderRecipes` `08-runtime.js:189-190`), one per hit, catalog order

| Fact | Source field / computation | Notes |
|---|---|---|
| Category eyebrow | `r.cat` | escaped with `esc` (`05-runtime.js:1`) |
| Name (h3) | `r.n` | longest 47 characters |
| Meta line | `"<yield> · Prep <prep> · Total <total>"` | free text, e.g. "12–15 bites", "5 min (plus overnight chill)" |
| Calorie tag | `"<kcal> kcal"` plus `" (est.)"` when `r.cls` is set | `.tag` (`styles/01-base.css:161-165`) |
| Macro tiles | Protein / Carbs / Fat, `"<p>g"`, `"<c>g"`, `"<f>g"`, 18 px values | inline grid of 3 columns overrides `.stats` |
| Disclosure | "Ingredients & instructions" (`<details>`, closed) | |
| Ingredients | heading "Ingredients"; one full-width button per ingredient with the text and a right-hand mark "＋", or "🛒" for `rcp_pesto_chicken` ingredient 0 | up to 14 ingredients, longest 55 characters; buttons use `var(--surface-2,…)` and `var(--border)`, neither defined anywhere in `src`, so they draw with the fallback tint and no border **(run: `border-top-style:none`)** |
| Product panel | hidden `div#recipeProduct-<id>` (section 3.4) | |
| Instructions | heading "Instructions"; ordered list of `r.steps` | up to 6 steps, longest 127 characters |
| Note | `r.note` paragraph when present | 3 recipes: `rcp_egg_muffins`, `rcp_granola_bars`, `rcp_recovery_smoothie` |
| Primary button | "＋ Log this recipe" | full width |
| Footnote | "Nutrition per serving, as listed in the cookbook." | shown on class recipes too, which contradicts their "(est.)" tag |

### 3.4 Product panel (`selectRecipeIngredient` `08-runtime.js:192-202`)

| Case | Shows |
|---|---|
| URL known | card with the ingredient text, "Walmart product link attached", and a link button "Open in Walmart ↗" (`href` = `https://www.walmart.com/ip/27935840?sid=edfc41aa-c731-47b8-9fec-6d99adbba129`, `target=_blank`, `rel=noopener noreferrer`) |
| No URL | card with the ingredient text and "No Walmart product link added for this ingredient yet." |

The link table is a literal inside the function (`08-runtime.js:194`), not data.

### 3.5 The catalog (static; `RECIPES` built at load)

| Source | Count | Categories | Where |
|---|---|---|---|
| Cookbook (no `cls`) | 10 | Breakfast 3, Pre-workout 1, Post-workout 3, Lunch & dinner 3 | `02-runtime.js:2-11` |
| Scholar | 9 | Breakfast 3, Lunch & dinner 6 | `00-runtime.js:43` (inside `CLASS_CONTENT`), pushed `03-runtime.js:2` |
| Entrepreneur | 8 | Breakfast 2, Lunch & dinner 4, Snack 2 | `02-runtime.js:13`, pushed `03-runtime.js:3` |
| Explorer | 8 | Breakfast 1, Pre-workout 1, Post-workout 1, Lunch & dinner 3, Snack 2 | `03-runtime.js:1`, pushed `03-runtime.js:4` |
| Athlete | 8 | Breakfast 1, Pre-workout 1, Post-workout 1, Lunch & dinner 5 | `01-runtime.js:17-26`, pushed `03-runtime.js:5` |

Record shape (all 43): `id, n, cat, yield, prep, total, kcal, p, c, f, servingG, ingredients[], steps[]`, optional `note` (3), `cls` added for class recipes. Ids are unique and quote-free (safe inside the inline `onclick` strings). For every recipe the printed `kcal` is within 15 % of 4p+4c+9f. Every class diet menu reference (`CLASS_CONTENT[c].diet.menu[day][i][1]`) resolves to a recipe of the same class (8/9/8/8 ids). (All by evaluating `00`-`03-runtime.js` in Node.)

`RECIPE_FOODS` (`03-runtime.js:6`) turns each recipe into a food per 100 g: `kcal,p,c,f × 100/servingG`, `fib/sug/na = 0`, `port:[{l:'1 serving',g:servingG}]`, `cat:'Recipe · '+cat`, `recipe:true`, `cls`.

### 3.6 Motion and layout

| Effect | Where |
|---|---|
| Page children fade/slide in with 30 ms stagger on every page change (4 children: switch, header, filter card, list) | `styles/02-layout.css:817-827` |
| Swipe drag moves the cards with the finger; swipe arrival slides them in | `styles/02-layout.css:848,851-852`, `34-navigation.js:114-194` |
| Smooth scroll to top on `navigate`, instant on `lmaNavTo` | `04-runtime.js:49`, `34-navigation.js:38` |
| View Transition slide between tabs when supported; none from native Food (plain `navigate`) | `34-navigation.js:31-51`, `50-native-nutrition.js:435` |
| No sounds, haptics, toasts or XP on this page | verified: none of the three functions call `toast`, `LifeMaxHaptics`, sound or `save` |

---

## 4. Actions

### 4.1 Pane controls

| Control | Handler | Reads | Writes | Side effects | Opens |
|---|---|---|---|---|---|
| "Meals" pill | inline `lmaNavTo('meals')` (`views/recipes.html:2`) | `foodPane` not used (explicit view) | `state.foodPane='meals'` in memory (`42-scrubnav.js:204`) | slide transition; Back stack push (`34-navigation.js:84-87`); native Food opens if its beta is on (`50-native-nutrition.js:593-596`) | Meals |
| "Recipes" pill | `lmaNavTo('recipes')` | none | `foodPane='recipes'` (already) | none visible (same view: `lmaNavTo` runs `navigate` again and re-renders, `34-navigation.js:42`) | none |
| "＋ Log a meal" | inline `navigate('meals')` (`views/recipes.html:3`) | none | `foodPane='meals'` | no View Transition (plain `navigate`); Back stack push; native Food opens if enabled | Meals (log pane or plan pane, whichever `fv2S().pane` / `sdDay` selects, `50-food.js:75`) |
| Type in search | `oninput="renderRecipes()"` | `RECIPES`, `state.classId`, both filter values | DOM only (`#recipesList.innerHTML`) | every keystroke rebuilds the whole list, closing open cards and clearing the selected ingredient | none |
| Change category | `onchange="renderRecipes()"` | same | DOM only | same | none |
| Expand / collapse a card | native `<details>` toggle | none | none | none | inline disclosure |
| Tap an ingredient | inline `selectRecipeIngredient(id,i,this)` (`08-runtime.js:192-202`) | `RECIPES`, literal link table | DOM: outline and `aria-pressed` on every `.ingredient-pick`; panel `display:block` | none | product panel (3.4) |
| "Open in Walmart ↗" | plain anchor, `target=_blank` | none | none | leaves the app: in the Capacitor WebView an external `https` URL is handed to Android (external browser or Walmart app) by Capacitor's default URL handling **(unverified on device)**; no `window.open`/`Browser` code exists in `src` | external app |
| "＋ Log this recipe" | inline `addRecipeToMeal(id)`; the live definition is `50-food.js:132` | `RECIPE_FOODS` (not class-filtered; any id works **(run)**), clock (`fv2Slot` `50-food.js:11`: before 10:30 breakfast, before 15:00 lunch, before 21:00 dinner, else snacks) | `foodPane='meals'`; sheet state `window.__fv2.sh` | `navigate('meals')` (no transition), then the add-food sheet in its portion view; native Food stays closed (section 1, risk 2) **(sim)** | Add-food sheet, portion view (4.2) |
| Back (system) | `lmaBack` (`34-navigation.js:69-74`) | nav stack | none | closes the top `.sheet-ov.show` first if any; else previous view | |
| Horizontal swipe | `34-navigation.js:136-196` | tab list | none | goes to the neighbouring bottom tab of `food`; blocked when the touch starts on the input or select | |

### 4.2 Sheet opened by "Log this recipe": Food's add-food sheet, portion view

`fv2Open(fv2Slot(),'portion',food)` (`50-food.js:138-141`) builds `#fv2Ov.sheet-ov.fv2-ov` (`50-food.js:135-137`), adds `body.noscroll`, and paints `fv2Portion` (`50-food.js:200-211`). The sheet belongs to the Food slice; it is listed so that a native Recipes screen knows exactly what its delegated action shows.

| Field / control | Detail | Handler |
|---|---|---|
| Header | "‹" (back; with an empty stack it closes) and title "Add food" | `fv2Head` `50-food.js:147`, `fv2Back` `:144` |
| Food heading | recipe name; subtitle `"Recipe · <category>"` | `50-food.js:205` **(run)** |
| Favourite star | toggles a full per-100 g copy in `state.mealsMeta.favs`, then `save()` | `fv2ToggleFav` `50-food.js:24`, click `:185` |
| Big number | kcal for the chosen grams (`scaleFood` `08-runtime.js:284-289`) | `fv2Live` `50-food.js:214-218` |
| Grams input `#fv2G` | starts at `servingG` (for example 230 g for Quick Pesto Chicken); "−"/"+" steps of 5/10/25 g by size | `50-food.js:186,207`, input `:175` |
| Serving chips | "1 serving · <servingG> g", "100 g" | `50-food.js:201-202,188` **(run)** |
| Multipliers | ½×, 1×, 1.5×, 2×, 3× of one serving | `50-food.js:187,208` |
| Macro impact bars | protein, carbs, fat: today's logged amount plus this portion against the targets (`fv2Goals` `50-food.js:13`) | `50-food.js:204,216` |
| Fibre, sugar, sodium | always 0 for recipes (`RECIPE_FOODS` sets them to 0) | `fv2Small` `50-food.js:212` |
| Slot pills | Breakfast, Lunch, Dinner, Snacks; default from the clock | `fv2SlotPills` `50-food.js:146`, click `:195` |
| "Add to <slot> · <kcal> kcal" | `fv2DoLog` -> `fv2Log(food,grams,slot)` | `50-food.js:219-221,30-36` |
| Close | tap outside, Back, Escape | `50-food.js:136,240`, `34-navigation.js:64-65` |

`fv2Log` writes: pushes `{id:'food_<ms>_<4 chars>', date: mealsDayKey(), slot, foodId:'rcp_…', name, grams, kcal, kcalEstimated, p, c, f, fib:0, sug:0, na:0, at: ISO}` to `state.foodLog` **(run)**, then `save()` (which re-runs `rebuildAttrXP`, badges, level-ups and `renderAll`, `04-runtime.js:13`), haptic `success`, `renderMeals()`, and a 4.2 s snackbar "<name> · <kcal> kcal" with "Undo" (`50-food.js:37-39`; undo removes the entry and saves). No XP, no quest, no notification. `date` is `mealsDay`, the day the Meals page was last showing (`mealsDayKey` `08-runtime.js:223`), so a recipe logged while Meals was left on another day is logged on that day **(from code reading)**. The "Nutrition goal hit" XP hook (`ecoNutritionCheck`) is not reached from this path (section 5.6).

### 4.3 Writers outside the pane that change what it shows

| Writer | Effect on Recipes |
|---|---|
| Onboarding class choice (`12-runtime.js:170`) | sets `state.classId`, so the class recipes |
| Developer `switchClassTo` (`14-runtime.js:121`) | sets `classId`, saves, calls `renderRecipes()` |
| Backup import (`importData` `04-runtime.js:265`, through `applyBackup`) | replaces `state`, including `classId` and `foodPane` **(the re-render path after import was not traced)** |
| Any `save()` anywhere (`04-runtime.js:13` -> `renderAll` `:182`) | rebuilds the list: closes open cards, hides product panels |
| `dietOpenRecipe` (`14-runtime.js:69`) | writes the search box |
| OTA web bundle update | can change the catalog (it is web code), without an APK update |

---

## 5. Cross-tab couplings

### 5.1 Redefinitions and wrappers

| Symbol | Definitions, in load order | Effective |
|---|---|---|
| `renderRecipes` | `08-runtime.js:185` only | never reassigned or wrapped (verified: no `renderRecipes=`) |
| `selectRecipeIngredient` | `08-runtime.js:192` only | |
| `addRecipeToMeal` | `08-runtime.js:203-212` (first Food tab: fills `#mealSearch`, `#mealGrams`, `#mealPortion`, which no longer exist in `views/meals.html`) and `50-food.js:132` | the `50-food.js` one, by hoisting, from the first line of the script **(run: `String(addRecipeToMeal)` is the 50-food version)**. The 08 copy would throw if it ever ran |
| `fuelOpenLab`, `fuelCancelMotion`, `renderMeals`, `setMealsDay` (reached from this pane through `navigate` and the sheet) | `10-runtime.js:210,22,265`, `08-runtime.js:224` and again in `50-food.js:129,128,67,126` | the `50-food.js` ones |
| `renderMeals`, `save` | wrapped by native Food to schedule a native update (`50-native-nutrition.js:612-628`) | harmless for Recipes (`scheduleSync` returns unless Food is open) |
| `renderAll` | `04-runtime.js:182` only, not wrapped | |
| `navigate` | base `04-runtime.js:49` plus 21 wrappers (A30). Those that act on `recipes`: base (shows the view, calls `renderRecipes`, scrolls, tip check, `petSync`); `34-navigation.js:77-91` (Back stack, tab highlight, bar scroll); `42-scrubnav.js:198-212` (writes `state.foodPane`, lights `food`, toggles `.libseg`, thumbnail); `50-native-overview.js:36` (`NativeTabs.heal` 600 ms later: closes any native tab still showing, since none is registered for `recipes`); `50-native-overview.js:146` (removes `native-cover`; closes Overview through the hand-over); `50-native-nutrition.js:588-600` (closes Food through `NativeTabs.leave('meals','recipes',…)`, immediate because `recipes` is not covered); the two `NativeTabKit` wrappers (`50-native-tabkit.js:191-201`, Character and Log close the same way). The rest only act on `settings`, `money`, `quests`, `dashboard` or the journal | |
| `lmaResolve` | `34-navigation.js:9`, a `const` (cannot be wrapped) | callers: `lmaNavTo` `:32`, `nativeView` `50-native-overview.js:145`, `activate` `:171` |

### 5.2 Render triggers

`renderRecipes` runs on: `navigate('recipes')` (`04-runtime.js:49`); every `save()` via `renderAll` (`04-runtime.js:182`), whether or not Recipes is showing; `switchClassTo` (`14-runtime.js:121`); every search keystroke and category change. Background saves exist, so an open recipe can collapse while the owner reads it: Health Connect sync every 30 min when enabled saves in its `finally` (`39-health.js:104,133`), paycheck clearing saves when due (`36-money-pay.js:55-67,131`), the pet death check saves (`15-runtime.js:111`, timer `17-runtime.js:326`). Cost about 5 ms per render on desktop headless **(run)**; phone cost not measured.

### 5.3 Timers, observers and global listeners touching this pane

| Item | Effect |
|---|---|
| `setInterval(markVisit…,30000)` and on `visibilitychange` (`05-runtime.js:66,71-72`) | writes the whole state to `localStorage` directly, which persists `foodPane` |
| `NativeTabs.heal` (`50-native-overview.js:24-34`) | after any navigation: on Recipes it only closes stray native tabs |
| Document touch listeners for tab swipe (`34-navigation.js:136-196`) and the bar scrubber (`42-scrubnav.js:148-195`) | as for every web page |
| Tab thumbnail (`42-scrubnav.js:41-50`, store `lifemax_thumbs_v1` `:25`) | keyed by tab key `food`, so the scrub veil may show a picture of Recipes for "Food"; the `recipes` icon in `LMA_TAB_ICONS` (`42-scrubnav.js:90`) is never used (the veil always asks for `food`, `:95`) |
| No observers, no intervals, no `requestAnimationFrame` loops belong to this pane | verified |

### 5.4 Other code that reads the recipe data

| Reader | What |
|---|---|
| Add-food search (Meals sheet) | `allFoods()` (`08-runtime.js:218-221`) appends class-filtered `RECIPE_FOODS`; `fv2Search` (`50-food.js:167-172`) matches name/brand/category, so typing "recipe" lists every visible recipe |
| Recents and favourites | `fv2FoodOf` -> `foodById` (`50-food.js:19`, `08-runtime.js:222`) resolves `foodId:'rcp_…'` through the class filter; after a class change another class's recipe is rebuilt from the entry (`cat:'Logged'`, no serving chip) |
| Voice food logging | `g5FoodFind` (`50-voice-gen5.js:450-466`) scans `allFoods()`, so "I had a recovery smoothie" can log the recipe food; grams default to the first portion, one serving |
| Voice "what's on this screen" on Recipes | `g5Screen` maps `recipes` to `food` (`50-voice-gen5.js:167`) and answers with today's food totals (`50-voice-gen5.js:217-223`, `lmvHFood` `46-voice-nlu.js:395`), not with recipes |
| Class diet card | menu items link to recipe ids (`14-runtime.js:81`) |
| Smart Diet / Life Director meals | separate catalog (`data/smartDiet.html`, 55 KB, read by `50-smartdiet.js`); Life Director meal links go to Food, Plan (`50-smartdiet.js:236-252`, `features/life-director/logic.js:3483-3499`). Not related to `RECIPES`, although the native Plan pane also shows ingredients and steps |
| Tests | no test covers the Recipes pane; `tests/swipe.test.js:73` only checks that Recipes lights "Food" |

### 5.5 Interplay with native Food (the important coupling)

Observed with fake plugins **(sim)**, Native Food beta on:
1. On native Food, "Recipes" -> native Food closes, web Recipes shows, `foodPane='recipes'`, Back stack ends with `meals`.
2. Later, "Food" from another tab -> web Recipes again; native Food is not opened.
3. "＋ Log this recipe" -> web Meals with the add-food sheet; 2.5 s later native Food is still closed.
4. After "Add to …" the sheet closes and the classic web Meals page stays; native Food does not come back until the next navigation to `meals`.
5. An `action` event naming a stale session is ignored (`50-native-nutrition.js:456`).

Consequences: the Native Food beta silently turns into Classic Food after a single visit to Recipes, until the owner taps "Meals". The hand-off from native Food is break-before-make (close, then navigate), which is correct today because the destination is web (A23 item 2), but must change once Recipes is native (7.6).

### 5.6 Dead or inconsistent couplings found

| Finding | Evidence |
|---|---|
| "Nutrition goal hit" XP never fires: `ecoNutritionCheck` is hooked only onto `addMealEntry` (`44-rewards.js:147`), the first Food tab's handler (`08-runtime.js:290`), which nothing calls any more; Food v2 logs through `fv2Log` | verified by grep: no other caller of `ecoNutritionCheck` or `addMealEntry` in `src` |
| Snack recipes are unreachable through the category filter | 3.2, **(run)** |
| Class recipes say "(est.)" but the footnote says "as listed in the cookbook" | `08-runtime.js:190` |
| Ingredient buttons reference undefined CSS variables `--surface-2` and `--border` | grep of `src`; **(run)** |
| Selecting an ingredient in a second card leaves the first card's product panel open | `08-runtime.js:198-200` |
| Dead first-Food-tab code reachable by name only | `08-runtime.js:203-212` |
| The bridge catalog's Food entry does not list the `recipes` fallback among its delegated interactions | `docs/architecture/BRIDGE_CATALOG.json` (`food.delegatedInteractions` = `fv2Open`, `sdOpenSwap`) |
| Recipes has no row in the migration ledger | `docs/architecture/MIGRATION.md`, ledger as of Native API 17 |

---

## 6. Persistence

| Key / store | Shape | Writer | Notes |
|---|---|---|---|
| Recipe catalog | none persisted: `RECIPES`, `RECIPE_FOODS` are rebuilt from JS at every load | `02-runtime.js`, `03-runtime.js` | 29.6 KB JSON in the bundle; changes ship by OTA web update |
| `lifemax_rpg_prototype_v1` -> `foodPane` | `'meals'`, `'recipes'` or absent (absent means `meals`) | `42-scrubnav.js:204` (memory), persisted by any `save()` (`04-runtime.js:13`) or `markVisit` (`05-runtime.js:66`) **(run: persisted after `markVisit`)** | a UI preference inside the gameplay save; not in `freshState` (`04-runtime.js:9`); never reset |
| `lifemax_rpg_prototype_v1` -> `foodLog[]` entries from recipes | see 4.2; `foodId` is the recipe id | `fv2Log` `50-food.js:30-36` | owned by the Food slice |
| `lifemax_rpg_prototype_v1` -> `mealsMeta.favs[]` | per-100 g copy of the recipe food | `fv2ToggleFav` `50-food.js:24` | Food slice |
| `lifemax_rpg_prototype_v1` -> `classId` | class id or null | onboarding, `switchClassTo` | decides the visible recipes |
| `lifemax_android_prefs_v1` -> `nativeNutrition` | boolean | `setNativeNutritionEnabled` `50-native-nutrition.js:47-58` | decides whether "Meals" is native |
| `lifemax_thumbs_v1` -> `food` | blurred tab picture | `42-scrubnav.js:28,36` | may be a picture of Recipes |
| Filters, expanded cards, selected ingredient | DOM only | | lost on any re-render and on restart |
| IndexedDB | none for Recipes (the USDA store is only searched from the sheet's search view, which "Log this recipe" skips) | | |
| Migrations | none touch recipes; `load()` merges the saved state over `freshState` (`04-runtime.js:12`) | | |

---

## 7. Native readiness

### 7.1 Easy

Header and intro copy; filter field and category chips; recipe cards (category, name, meta line, kcal tag with "(est.)", three macro tiles); expandable ingredients and steps; note; footnote; empty state. All data is plain strings and numbers, at most 19 recipes (7-14 KB of JSON by class), no images, no charts, no timers, no background work. Filtering, expansion and ingredient selection are presentation state (like Character's sorting) and can live in Compose `rememberSaveable` state, which also fixes the collapse-on-save behaviour of the web page.

### 7.2 Medium

| Item | Why |
|---|---|
| Hand-over inside one bottom tab | `meals` and `recipes` are two views of one tab. A native Recipes registered with `NativeTabs` makes Meals<->Recipes a native-to-native hand-over (A23), which only stays make-before-break if neither side closes itself before navigating (7.5) |
| Search field | Compose text input with IME; must reproduce the web semantics exactly (trim, lowercase, single substring over name, category and ingredients) or change them deliberately (question 8) |
| Long list | `LazyColumn` keyed by recipe id; expansion state per id survives projection updates |
| External link | open the Walmart URL with an Android `ACTION_VIEW` intent (a platform UI interaction, allowed in presentation, A05); accept `https` only |
| Light, dark and accent | tokens from `NativeTabKit.tokens()`; the web uses `.card`, `.tag` and `--accent` |
| Prefill from the diet card | `dietOpenRecipe` writes `#recipeSearch` before navigating; the projection must carry the box's value so the native screen takes it over (as Log takes over a prefilled draft, `beforeOpen`) |

### 7.3 Hard

Nothing in this pane is hard (no canvas, 3D, drag, background timers or embedded assets). The hard part is outside it: the add-food sheet is a web sheet in the Food slice, and native Food still delegates it (`BRIDGE_CATALOG.json` food entry).

### 7.4 Web dependencies that stay delegated in phase 1

`addRecipeToMeal` and the whole add-food portion sheet (`fv2Open`, `fv2Log`, favourites); the `food` routing rule (`lmaResolve`, `state.foodPane`); `renderRecipes` stays the classic fallback; the catalog and the class rule stay web (`RECIPES`, `r.cls===state.classId`).

### 7.5 Hazards a native screen must respect

1. **Sticky pane.** With native Recipes registered under `recipes`, `nativeView('food')` becomes true whenever `foodPane==='recipes'` (`50-native-overview.js:145`), so "Food" would open native Recipes; that is consistent, but the owner may expect "Food" to mean Meals (question 1). Without native Recipes, the stickiness keeps turning Native Food into Classic (5.5).
2. **Break-before-make paths.** Native Food's `fallback('recipes')` closes first (`50-native-nutrition.js:433`), and a `NativeTabKit` `action` closes before it navigates (`50-native-tabkit.js:133-136`). Used between two native screens, both would flash the web page. Use the kit's `commands` with `reply.navigate` (followed with `lmaNavTo(…,{fast:true})` and no close, `50-native-tabkit.js:110`) for "Meals" and "Log a meal", and change native Food's `recipes` command to `navigate('recipes')` without `close()` (its own `navigate` wrapper already hands over through `NativeTabs.leave`, `50-native-nutrition.js:591`).
3. **Delegated sheet returns to classic Meals.** "Log this recipe" must close native Recipes and run `addRecipeToMeal` (web sheet over web Meals); native Food will not reopen afterwards (5.5 item 4). Record it as a hybrid dependency; fixing it belongs to Food (reopen on sheet close, or a native portion sheet with an A14 command).
4. **Projection must not write and must not depend on the DOM list.** Read `RECIPES`, `state.classId` and the two filter inputs only; never call `renderRecipes` from the projection.
5. **Freshness.** Hooking `render: "renderRecipes"` makes every `save()` schedule an update (via `renderAll`); harmless but frequent. The class can change while the screen is hidden (onboarding, developer switch, import), so `show()` must take a fresh projection (A24).
6. **Back.** Native Recipes must not take Back; `MainActivity` sends it to `lmaBack` (as for Food, `tests/native-nutrition-integration.test.js` asserts no `BackHandler`).

### 7.6 Where native Recipes should live

| Option | How | For | Against |
|---|---|---|---|
| A. Own small tab (recommended for phase 1) | `50-native-recipes.js` with `NativeTabKit.create({id:'recipes'})`, `NativeRecipesPlugin` extending `ComposeTabPlugin`, `feature/recipes/{domain,data,presentation}`, reusing the Food header's switch look | follows the guide exactly; no change to the Food plugin (not yet on the kit); hand-over, sessions, refresh and settings come free | two Compose hosts for one bottom tab; Meals<->Recipes is a full native hand-over (budget A25: warm switch at most 100 ms p95, to be measured) |
| B. A pane of native Food | extend `NativeNutrition` to cover both views and carry a `recipes` block in its snapshot | one host, instant pane switch | needs `open()`, `view()` checks, `NativeTabs.cover/register` and the snapshot changed for two views in a plugin that is not on `NativeTabKit`; best done when Food moves onto the kit ("to do as each is converted", MIGRATION.md) |

### 7.7 Proposed phasing

- **Phase 0 (owner decisions, section 8).** Questions 1 (what "Food" opens), 2 (Snack), 9 (toggle).
- **Phase 1: presentation only, opt-in.** Native draws the pane from the projection; search, category, expansion and ingredient selection are native local state; "Log this recipe" delegates (close, run `addRecipeToMeal`); "Meals" and "Log a meal" are page commands that reply `navigate:'meals'`; the Walmart link opens by intent. Native Food's `recipes` command is changed to hand over without closing first.
- **Phase 2: one-tap logging behind A14/A15** (if the owner wants it): a `logRecipe {opId, recipeId, grams, slot, day}` command whose owner runs `fv2Log` with an operation id stored in the entry, reporting outcome-unknown honestly; or wait for a native Food portion sheet and reuse it.
- **Phase 3: domain conversion** (after every tab's visuals): the catalog becomes a versioned native asset or Room table only when catalog authority moves; until then it stays web data delivered by projection, so OTA web updates keep working (A19).

### 7.8 Phase 1 sketch (proposal; nothing here is implemented)

`src/runtime/segments/50-native-recipes.js`, added to `index.template.html` after `50-native-tabkit.js` and after `50-food.js`:

```js
(function () {
  const LINKS = { rcp_pesto_chicken: { 0: "https://www.walmart.com/ip/27935840?sid=edfc41aa-c731-47b8-9fec-6d99adbba129" } };
  // better: move the literal out of selectRecipeIngredient (08-runtime.js:194) into one shared table both read
  function projection() {
    const q = document.getElementById("recipeSearch"), c = document.getElementById("recipeCategory");
    const cls = state.classId ? classById(state.classId) : null;
    return {
      classId: state.classId || "", className: cls ? cls.name : "",
      query: q ? q.value : "", category: c ? c.value : "all",          // prefill (dietOpenRecipe), taken over on open
      categories: [...(c ? c.options : [])].map((o) => ({ value: o.value, label: o.textContent })),
      recipes: RECIPES.filter((r) => !r.cls || r.cls === state.classId).map((r) => ({
        id: r.id, name: r.n, cat: r.cat, yield: r.yield, prep: r.prep, total: r.total,
        kcal: r.kcal, p: r.p, c: r.c, f: r.f, servingG: r.servingG, estimated: !!r.cls,
        ingredients: r.ingredients.map((t, i) => ({ text: t, link: (LINKS[r.id] || {})[i] || "" })),
        steps: r.steps, note: r.note || "",
      })),
      tabs: lmaTabList().map((b) => ({ id: b.dataset.view, label: b.textContent.trim() })),
    };
  }
  async function onAction(e) {                 // the kit has already closed the screen
    if (e.action === "log" && RECIPES.some((r) => r.id === e.id)) addRecipeToMeal(e.id);
  }
  const commands = {                           // run without closing; reply.navigate is followed with lmaNavTo(fast)
    meals: async () => ({ ok: true, navigate: "meals" }),     // the Meals pill and "＋ Log a meal"
  };
  NativeTabKit.create({
    id: "recipes", plugin: "NativeRecipes", capability: "recipes.projection.v1",
    setting: "nativeRecipes", toggle: "nativeRecipesToggle",   // or reuse nativeNutrition (question 9)
    projection, onAction, commands, render: "renderRecipes",
    globals: { open: "openNativeRecipes", enable: "setNativeRecipesEnabled", projection: "NativeRecipesProjection" },
  });
})();
```

Projection example (Scholar, trimmed):

```json
{
  "protocol": 1, "webSessionId": "recipes-web-…", "sequence": 7,
  "classId": "scholar", "className": "Scholar", "query": "", "category": "all",
  "categories": [{"value":"all","label":"All recipes"},{"value":"Breakfast","label":"Breakfast"},
                 {"value":"Pre-workout","label":"Pre-workout"},{"value":"Post-workout","label":"Post-workout"},
                 {"value":"Lunch & dinner","label":"Lunch & dinner"}],
  "recipes": [
    {"id":"rcp_pesto_chicken","name":"Quick Pesto Chicken","cat":"Lunch & dinner","yield":"2 servings",
     "prep":"5 min","total":"35 min","kcal":350,"p":44,"c":10,"f":16,"servingG":230,"estimated":false,
     "ingredients":[{"text":"8 oz chicken breast","link":"https://www.walmart.com/ip/27935840?sid=…"},
                    {"text":"1 oz pesto","link":""}],
     "steps":["Preheat oven to 375°F …"],"note":""}
  ],
  "tabs": [{"id":"dashboard","label":"Overview"}, {"id":"food","label":"Food"}],
  "tokens": {"bg": 4278716941, "accent": 4286356735}, "light": false
}
```

Native events:

| User does | Native emits | Page does |
|---|---|---|
| "＋ Log this recipe" | `action {action:'log', id}` | kit closes; `addRecipeToMeal(id)` (web sheet over web Meals; recorded hybrid dependency) |
| "Meals" pill, "＋ Log a meal" | `command meals` | reply `{ok:true, navigate:'meals'}`; native Food opens before Recipes closes |
| Ingredient tap | none (local state) | none |
| "Open in Walmart" | none (native `ACTION_VIEW` on the projected `https` URL) | none |
| Search, category, expand | none (local state; start from `query`/`category`) | none |
| Bottom-bar tap | `nav-action` (kit) | `NativeOverviewSnapshot.activate` |

Also in phase 1: change `50-native-nutrition.js:434-436` so the `recipes` command calls `navigate('recipes')` without `close()` (its wrapper hands over); add a `recipes` bridge-catalog entry (methods, events, `writes: []`, `delegatedInteractions: ['addRecipeToMeal/fv2Open']`) and a ledger row; tests: a node projection test (copy `tests/native-character.test.js`, with fixtures for no class, Scholar and Entrepreneur including a Snack), decoder and view-model unit tests (expansion survives an update with a higher sequence, older sequences ignored), a `RecipesScreenshotTest` from `tools/gen_native_fixtures.js` output, and `recipes` added to the fakes of `tests/native-handover-sim.test.js` (Meals<->Recipes rapid switching, Food tab with `foodPane='recipes'`).

---

## 8. Open questions for the owner

1. **What should the Food tab open?** Today it reopens the last pane you used (saved in your save file), so one visit to Recipes makes Food open Recipes, and with the Native Food beta on it opens the classic Recipes page instead of the new Food screen. Keep "last pane", or always open Meals?
2. **Snack recipes.** Four class recipes (two Entrepreneur, two Explorer) are filed under "Snack", but the category list has no Snack, so they only appear under "All recipes" or by search. Add a Snack category?
3. **After "Log this recipe".** It opens the add-food sheet with one serving and leaves you on the classic Meals page (not the native Food screen). Would you rather log one serving in one tap and stay on Recipes (with an Undo), keep the portion sheet, or return to Food afterwards?
4. **Nutrition goal XP.** The "Nutrition goal hit" bonus (8 XP when a day's calories land within 15 % of your goal) is wired only to the old Food tab, so it has not been awarded since the new Food tab replaced it. Should it come back? (This changes XP, so it needs your decision.)
5. **Walmart link.** Only "8 oz chicken breast" in Quick Pesto Chicken has a link, and it carries a session id from when it was copied. Keep it, remove it, or grow it into a proper shopping feature (Smart Diet already has a shopping list)?
6. **Two recipe collections.** The Recipes page (cookbook plus class recipes) and the Smart Diet meal plan (Food, Plan, with its own ingredients and steps) are separate. Keep both, or should one feed the other?
7. **Wording and coverage.** Class recipes are marked "(est.)" but each card ends with "as listed in the cookbook". Fix the footnote for class recipes? Disciple and Diplomat have no class recipes: intended?
8. **Search.** Search finds one exact phrase in names, categories and ingredients ("chicken rice" finds nothing unless those words are adjacent) and ignores the steps. Keep this, or match each word separately?
9. **Opt-in.** Should a native Recipes page follow the existing Native Nutrition (Beta) switch, so Food is either all native or all classic, or get its own switch?
10. **Open recipes stay open.** On the web page an open recipe closes whenever the app saves in the background (for example a Health Connect sync). The native page would keep it open. Is that what you want (almost certainly yes)?
