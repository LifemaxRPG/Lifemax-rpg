# Native Food and nutrition logic: audit and plan

Audit date: 2026-11-12. Branch audited: `codex/director-dial-controls` at `1813d99`. Read-only audit; nothing was built and no page file
was changed. Sources read: `lifemax_modular_v73/src/runtime/segments/` (`01`, `02`, `03`, `08`, `44`, `50-food`, `50-food-forms`,
`50-smartdiet`, `50-smartdiet-swap`, `50-native-nutrition`, `50-native-shopping`, `50-native-recipes`, `50-voice-gen4/5`,
`46-voice-nlu`, `50-quests-hand`, `05-shadow-boot`), `src/data/smartDiet.html`, `src/features/life-director/logic.js`, the Kotlin under
`android-wrapper/native/.../feature/food`, `core/save`, `core/lifedirector/hostadapters`, and the economy and Life Director specs. The
only things run were two node snippets that count the built-in tables (section 3.1). Anything not read or run is marked
**unverified**. Line numbers are for this commit; a range runs from a function's first line to the line before the next one.

Goal: native Food logic whose answers are provably identical to the page's, so that saving Food data can later move from the page to
native (a "logic phase"). Same method as the economy, quests and Life Director: spec, golden cases recorded from the real page, fresh
Kotlin that must reproduce every case, hand tests and mutation tests, then a shadow check on the owner's phone, then a cutover.

Terms used below, in plain words.
- **Golden case**: one recorded input and the exact answer the real page gave. Kotlin must give the same answer.
- **Shadow mode**: native recomputes in the background from what the page sends and only compares; it never writes.
- **Logic phase / cutover**: the step where native, not the page, becomes the writer of a piece of saved data.
- **Codec**: the code that reads and writes the save file's text. A byte-for-byte codec keeps fields it does not understand.
- **IndexedDB (IDB)**: the browser database inside the page; the USDA foods live there today.
- **SQLite**: the small database built into Android; **FTS5** is its full-text search add-on; a **B-tree index** is the sorted
  structure that lets a database find a word prefix quickly.
- **Debounce**: wait for typing to pause before searching. **Idempotent**: doing it twice changes nothing the second time.
- **Pure**: depends only on its inputs. **Injected**: the clock or random source is passed in so a test can fix it.

## 1. Summary

- Native Food today is a screen. About 4,200 lines of Kotlin under `feature/food` plus `NativeNutritionPlugin.kt` (237) decode a
  projection the page builds (`50-native-nutrition.js:236-343`) and send repeat-safe commands back (add-food sheet, create food,
  targets, entry edit, Undo, swap, shopping preferences). No Food rule is in Kotlin. The page decides every number and every word.
- The only Food-related logic already native is outside Food: the economy's nutrition bonus (`core/economy/Bonuses.kt`, ECONOMY_SPEC
  11.6.1) and Life Director's meal link (`HostMeals.kt`, HA-22/HA-23), which reads Smart Diet through a replay interface (3.7).
- The logic to port is about 420 lines of dense JavaScript (3.3 to 3.6), plus three data sets: the 363 built-in foods, the 43 recipes
  and the Smart Diet catalogue (3.1), plus the USDA branded-foods search, which is the one piece with an unknown size (section 6).
- Saved data is all in the main save under `foodLog`, `waterLog`, `customFoods` and `mealsMeta` (3.8). The native save codec treats
  `foodLog` as read-only and partial (`date`, `kcal` only; `core/save/SaveFields.kt:46,64-67`), so a Food logic phase must first grow
  the codec. That, plus eleven different page writers (3.8), is the real cutover risk.
- Logging a meal pays nothing and tells nobody: no XP, no Sparks, no Life Director call (3.4). The only link to the economy is the
  daily nutrition bonus, which is unreachable today (ECONOMY_SPEC D02). The port must not change that silently (OQ-F8).
- Recommended first package: WP-F1 (recorder and the data export). Recommended biggest-unknown-first package: WP-F5 (USDA), because it
  needs the owner's real file and phone time.
- Ten work packages (section 5), one new shadow check (section 7), four future features that sit on top (section 9).

## 2. What native does today, and what stays page-only

| Area | Native today | Page (only writer) |
|---|---|---|
| Plan, Log, Shopping panes | draws the projection (`FoodScreen`, `FoodLogPane`, `FoodPlanPane`, `FoodShopPane`) | builds it: `snapshot` `50-native-nutrition.js:236`, `buildPlanSnapshot` 117, `buildShopSnapshot` 203 |
| Add-food sheet | draws search, portion, quick, create, targets, edit (`feature/food/presentation/sheet`) | all rules: `fv2*` in `50-food.js` and `50-food-forms.js`; sheet state `Q` in `50-native-nutrition.js` |
| Writes | sends `sheetLog`, `sheetRowAdd`, `sheetQuickSave`, `sheetCreateSave`, goals, entry save/remove/again, `sdLog`, `sdSkip`, `sdCheck`, `swapPick`, `shopPrefSet` | each runs the page function and answers `{op, ok, changed, message}` (`write` 411); the last 40 operation ids are remembered |
| USDA | none (search runs in the page, rows arrive in the next projection) | IDB `lifemax_usda_branded_v1`, import in Settings |
| Economy | `Bonuses.nutrition` (unreachable on the page, D02); `foodLog` crosses to shadow as `[{date, kcal}]` (`05-shadow-boot.js:13-15`) | `ecoNutritionCheck` `44-rewards.js:47-58` |
| Life Director | `HostMeals` (HA-22/23) resolves a meal block to a Smart Diet meal using a `MealDays` interface, today fed by recorded page answers | `sdDirectorMeal` `50-smartdiet.js:228`, `LifeMaxMeals` frozen object 252 |

## 3. Inventory

Columns: **Kind** is P (pure, only inputs), S (reads or writes the save `state`), T (time or date), R (randomness), D (DOM or toast),
I (IndexedDB). Size is lines of dense JavaScript, approximate. "Port" says whether the function is ported, ported as a read, or left
(presentation or dead code).

### 3.1 Built-in food and recipe tables

| Data | Where | Size | Notes |
|---|---|---|---|
| `FOOD_DB` | `01-runtime.js:47`, one line of 72 KB | 363 foods, 19 categories, 285 with a serving (`port`) | Per 100 g: `id` (`fdc…`), `n`, `cat`, `kcal`, `p`, `c`, `f`, `fib`, `sug`, `na`, `port[{l,g}]`. **268 of 363 have `kcal` 0**, so they show as estimated (3.3). Counted by a node snippet. |
| `RECIPES` | `02-runtime.js:1` (10 for all classes) plus class packs pushed in `03-runtime.js:1-5` | 43 (10 all, 9 scholar, 8 entrepreneur, 8 explorer, 8 athlete) | `{id n cat yield prep total kcal p c f servingG ingredients[] steps[] cls?}`. Ingredient lines are free text ("¼ cup onion, diced"), not quantities. |
| `RECIPE_FOODS` | `03-runtime.js:6` | derived | Per 100 g from per-serving values: `kcal*100/servingG`, one serving `port`, `recipe: true`, `cls`. Unrounded floats. |
| `RECIPE_LINKS` | `03-runtime.js:8` | 1 recipe, 1 ingredient | Walmart product link by recipe id and ingredient index. |
| Smart Diet catalogue | `src/data/smartDiet.html`, one JSON script, 55 KB, built by `tools/make_smartdiet.py` | 37 ingredients, 2 diets (`standard`, `simple`), 25 meals and 7 days each | Ingredient: per 100 g `{kcal p c f fiber sugar sodium_mg}` and shop data `{aisle unit unit_g pantry keeps_days}`. Meal: `{id name slot ingredients[{ing g flex{min,max} label}] swaps[] steps[] tags[] active_min equipment[]}`. Plan: `{name ref meals week[{day type note meals[]}] prep[{when minutes task}] notes[]}`. |
| Voice estimate table | `50-voice-gen5.js:434` (`G5_EST`, about 15 rows) | 15 | Voice's own food guesses; voice stays page-owned (3.7). |

`allFoods()` (`08-runtime.js:217`) is `FOOD_DB`, then `state.customFoods`, then `RECIPE_FOODS` filtered by `state.classId` (a recipe with no
class shows for everyone). The order is rule-bearing: search ties keep it. All tables are exported from the page to a resource file and
read by Kotlin, never retyped, with a test that fails if the export drifts (the Life Director `demand-tables.json` pattern).

### 3.2 USDA branded foods

| Function | File:line | Size | Kind | Depends on |
|---|---|---|---|---|
| `usdaDb` opens IDB `lifemax_usda_branded_v1` v1, store `foods` (keyPath `id`), indexes `terms` (multiEntry) and `name` | `08-runtime.js:232` | 1 | I | demo mode prefixes the name (`DEMO_MODE.md:12`) |
| `usdaCount`, `usdaStatus` | 233-234 | 2 | I, D | |
| `importUsdaFoods` | 235-245 | 10 | I, D | `DecompressionStream`, file picker, `toast` |
| `mealSearch`, `renderMealHits`, `selectMealFood` (legacy search box) | 246-270 | 25 | I, D | superseded by the sheet; **not ported** |
| `fv2UsdaFoods` (the live USDA search) | `50-food.js:192` | 4 | I | `fv2Match` |
| `fv2Search`, `fv2SheetInput` (110 ms debounce, `serial` discards stale results) | 196-198, 199 | 5 | I, T | |
| `fv2UsdaRender`, `fv2UsdaClear` (Settings card; Remove needs two taps within 4 s) | 282-296 | 15 | I, D | |

**File format** (read from `importUsdaFoods` and the 2-line fixture `tests/fixtures/usda-mini.jsonl.gz`): gzip of UTF-8 text, one JSON
object per line, `.gz` suffix required. Fields used: `id` (string, required), `n` (name, required), `brand`, `cat`, `kcal`, `p`, `c`, `f`
(per 100 g), `serving` (number) with `servingUnit` (default `g`) and `household` (label). `fib`, `sug`, `na` are read by `scaleFood` if present;
whether the real file carries them is **unverified**. A line that is blank, not JSON, or lacks `id` or `n` is skipped without a count.

**Import rules** (US-1..): (1) `port` = `[{l: household or '1 serving', g: serving}]` only when `serving > 0` and the unit lowercased is `g`;
otherwise `[]`. (2) `terms` = the set of `[a-z0-9]+` runs in `lowercase(n + ' ' + brand + ' ' + cat)`; any other character splits a word and
is dropped. (3) Records are `put` by `id`, so a re-import overwrites matching ids and never removes old ones. (4) Batches of 400, progress
text every 5,000 lines. (5) Remove deletes the whole database.

**Search rules** (US-S1..): the query is trimmed, lowercased and split on white space into tokens (`fv2SearchTokens` 187). Local foods first:
`allFoods` where every token is a substring of `lowercase(n + ' ' + brand + ' ' + cat)`, sorted by (name starts with the first token) then by
shorter name, **stable**, first 25 (`fv2LocalFoods` 189-191). Then USDA: read **80 index entries** whose term lies in `[tok0, tok0 + '￿']`
in IDB order, which is term ascending, then primary key ascending, one entry per matching term of a food, so one food can use several of the
80; drop foods already in `local`, drop any that fail the all-tokens test, keep the first **30** distinct ones (192-195). Result =
`local.concat(brand)`. There is no relevance ranking for USDA rows: their order is the index order. Only the first token reaches the index;
the other tokens only filter. A failed read returns `[]`.

**Limits seen**: 80 index entries, 30 USDA rows, 25 local rows, 110 ms debounce, 400-record batches. The cache `usdaFoodsCache` (08:230) is an
in-memory map filled by searches; it is how the edit view finds a USDA food (FD06). Size of the real file: **unknown** and not in the repo
(`SETTINGS_INVENTORY.md:625` also says unmeasured). `tests/usda.test.js` covers import, one search, Remove with a 2-food fixture.

### 3.3 Nutrition math

| Function | File:line | Size | Kind | Rule |
|---|---|---|---|---|
| `foodEnergyInfo` | `08-runtime.js:271` | 6 | P | stated `max(0, Number(kcal)||0)`; if it is 0 and `4p + 4c + 9f > 0` use that, flagged estimated |
| `foodEnergyLabel` | 279 | 3 | P | `≈` and `(est.)` when estimated, rounded |
| `scaleFood(f, grams)` | 283 | 5 | P | `m = (Number(grams)||0)/100`; kcal `Math.round(energy*m)`; each of `p c f fib sug na` is `Math.round((Number(x)||0)*m*10)/10`; result `{kcal, kcalEstimated, p, c, f, fib, sug, na}` |
| `fv2Tot` | `50-food.js:12` | 1 | P | sum of `+e.kcal||0` and the six macros over entries |
| `fv2Goals` | 13 | 1 | S | `cal = max(0, +calGoal||0)`; each macro is its goal if above 0, else `round(cal*0.3/4)`, `round(cal*0.4/4)`, `round(cal*0.3/9)`; water goal or 2000 |
| `fv2Water`, `dayFoodLog` | 14; `08:351` | 2 | S | filter by `date` |
| `fv2Per100` | 17 | 1 | P | per-100 values rebuilt from an entry, `Math.round(x*k*100)/100`, `k = 100/max(1, grams||100)` |
| `fv2Unit`, `fv2PL` | 26-27 | 2 | P | first serving or `{g:100, l:'100 g'}`; `RACC` becomes `serving`, first letter upper-cased |
| `fv2StepGrams`, `fv2MultGrams` | 225, 227 | 2 | P | step 5 g below 50, 10 below 200, else 25; multiples `0.5 1 1.5 2 3` of the first serving |
| `fv2ServingsData`, `fv2ImpactData`, `fv2RowData` | 245-250, 168 | 8 | P/S | first four servings plus `100 g`; bars of the day's targets with this food added |
| `fv2GoalSum`, `fv2PresetGrams`, `fv2GoalStep`, `fv2GoalData` | `50-food-forms.js` 46-59 | 10 | P/S | presets `[.3,.4,.3]`, `[.4,.35,.25]`, `[.3,.25,.45]`; the 30 kcal tolerance of the summary line |
| `fv2Fmt` | `50-food.js:10` | 1 | P | `Math.round(n).toLocaleString()`, i.e. the device locale's digit grouping |
| `fv2Slot` | 11 | 1 | T | breakfast before 10:30, lunch before 15:00, dinner before 21:00, else snacks, from the device clock |
| Energy label for entries, ring geometry | `09-runtime.js`, `50-food.js:74` | | D | presentation, not ported |

Rule-bearing quirks the spec must list: JavaScript `Math.round` rounds halves up (Kotlin `roundToInt` and `Math.round` differ on negative
halves; values here are non-negative, so use `floor(x + 0.5)`); the multiplication order `x * m * 10` is part of the answer in doubles;
`Number("")`, `Number(null)` and `Number("12")` are 0, 0, 12, and `NaN || 0` is 0; `+e.kcal||0` treats a string as a number.
`toLocaleString()` and `toLocaleDateString('en-CA')` depend on the device's ICU data (FD09).

### 3.4 Meal logging, and what it writes and pays

| Function | File:line | Size | Kind | Writes | Notice / undo |
|---|---|---|---|---|---|
| `fv2Log(food, grams, slot)` | `50-food.js:34-44` | 10 | S, T, R, D | pushes `{id:'food_'+ms+'_'+4 base-36, date, slot, foodId, name, grams, ...scaleFood, at}` to `foodLog`; grams = `max(1, round(grams*10)/10)` | snack "name (cut at 26) · N kcal" with Undo that removes it; a failed `save()` restores (`fv2Saved` 33) |
| `deleteMealEntry`, `fv2RemoveEntry` | 45; `50-food-forms.js:66` | 3 | S | filters `foodLog` | "Removed name" with Undo that pushes it back |
| `fv2EditEntry` | forms 63 | 2 | S | assigns grams, slot and the scaled numbers onto the entry | none on success; a failed save restores |
| `fv2LogAgain` | forms 69 | 2 | S, T, R | copies the entry to today with a new id (copies **every** field, including `sd`) | "Logged again today" |
| `fv2CopySlot` | `50-food.js:46-49` | 4 | S, T, R | copies yesterday's entries of a slot (every field) | "Copied N items from yesterday", Undo |
| `fv2CreateFood` | forms 38-41 | 4 | S, T | `customFoods.unshift({id:'custom_'+ms, n, brand, cat:'Custom', per-100 values from a serving, port})`; kcal from macros when blank | "Give it a name" |
| `fv2QuickFood` | `50-food.js:229` | 1 | T | one-off food `quick_<ms>`, logged as 100 g | "Enter the calories" |
| `fv2ToggleFav` | 24 | 2 | S | `mealsMeta.favs` (copy of the food's numbers) | |
| `fv2SetGoals` | forms 59-61 | 3 | S | `calGoal protGoal carbGoal fatGoal waterGoalMl` each `max(0, round(+x||0))`, water `|| 2000` | |
| `fv2AddWater` | `50-food.js:50-52` | 3 | S, T, R | `waterLog.push({id:'water_'+ms+'_'+4, date, ml, at})` | "+N ml water", Undo |
| `fv2Recents`, `fv2FoodOf`, `fv2TabFoods` | 19-21, 173 | 8 | S, T | read only; recents newest first by `new Date(at||date)`, deduped by `foodId` (voice estimates by name) | |
| Dead copies: `addMealEntry`, `saveCustomFood`, `saveMealsGoal`, `openMealSheet`, `renderMealSheetList`, `recentFoods`, `mealSheetAdd`, a second `renderMeals` in `10-runtime.js:265` | `08-runtime.js:289-478` | about 190 | | | overridden or unreachable (`addMealEntry` is wrapped at `44-rewards.js:150` but nothing calls it); **not ported** |

What logging pays: **nothing**. `fv2Log` calls haptics and a snackbar only. The one economy tie is `ecoNutritionCheck` (a day within 85 to 115
percent of `calGoal` gives 8 XP through take-then-cap, once a day via `nutriDone`), triggered only by the dead `addMealEntry` wrapper (ECONOMY_SPEC
11.6.1, D02, D25). The Life Director does not log meals either: it only resolves and opens a meal (3.7).

### 3.5 Smart Diet (Plan and Shopping panes), `50-smartdiet.js`, `50-smartdiet-swap.js`

| Function | Lines | Size | Kind | What it does |
|---|---|---|---|---|
| `sdData`, `sdS`, `sdLevels` | 8-10 | 3 | S | parse the JSON once; `mealsMeta.sd = {diet, level, veg, df, over, skip, shop}` created on first use; levels rest 0.9, light 1, moderate 1.12, hard 1.25 |
| `sdWeekday`, `sdMonday`, `sdSlotOf` | 12-15 | 3 | T | weekday of a date key at local noon; Monday of the week |
| `sdSwapOn`, `sdMealIngs`, `sdMealOk`, `sdFit` | 17-31 | 14 | P | apply Vegetarian and Dairy-free swaps; replace a meal that cannot be made to fit by another of the same kind, chosen by `k % count` |
| `sdFactor`, `sdTotals`, `sdSolve` | 32-37 | 5 | P | each ingredient scaled by one factor clamped to its `flex`; `sdSolve` is a 40-step bisection between 0.3 and 2 for the factor that lands on a calorie target |
| `sdActivityMin`, `sdAutoLevel` | 38-41 | 3 | S, T | minutes of physique, endurance or adventure activity logged that local day (reads `state.activities`, an economy key): 60 or more hard, 30 moderate, above 0 light, else by the day's type |
| `sdDay(day, forShop)` | 43-61 | 18 | S | the whole day: meals after swaps and skips, logged meals keep what was logged, the open meals rescale so the day lands on `round(base*level/10)*10` given what is already eaten (`base` = `calGoal` or the plan's reference) |
| `sdLogMeal`, logall, unlog, skip, unskip | 62-68, 178-196 | 12 | S, T, R | pushes an entry with `foodId:'smartdiet:'+meal`, `sd:{diet, meal, i}`; skip/over/shop maps keyed by date |
| `sdShopList` | 154-160 | 7 | P/S | seven days of grams per ingredient, `packs = max(1, ceil(g/unit_g - 0.04))`, "buy twice" when `keeps_days <= 3` and used over a span of 3 or more days; `sdShopText` 174 builds the copy text |
| `sdSwapView`, `sdSwapApply` | swap 12-35 | 22 | S | the alternatives of the same kind and their numbers; the swap itself with a failed-save undo |
| `sdDirectorDay`, `sdDirectorSlot`, `sdDirectorMeal`, `sdOpenDirectorMeal`, `LifeMaxMeals` | 214-252 | 40 | S, T, D | Life Director's meal link (HA-22) |
| Plan/shop HTML, coach line, ring, cards | 69-153, 162-173 | about 90 | D | presentation; the coach text (`sdCoach` 95) is words with branching and is ported with the page's words only if the owner wants it native (OQ-F13) |

### 3.6 Recipes, shopping and meal prep that exist today

| What | Where | Notes |
|---|---|---|
| Recipe list, class rule, search | `recipeHits` `08-runtime.js:195`: class filter, category, substring over name, category and ingredient lines | the native Recipes screen draws the page's list (`50-native-recipes.js`) |
| Recipe card words | `recipeCard` 196 | "(est.)" is added for every class recipe; "Nutrition per serving, as listed in the cookbook" |
| Log a recipe | `addRecipeToMeal` `50-food.js:139`, native `seedPortion` | logs through the ordinary sheet as a `RECIPE_FOODS` food, one serving |
| Weekly shopping | `sdShopList` (Smart Diet rows only) and the native cart (`50-native-shopping.js`): the page projects the week's list and `recipes()` with `servings = parseInt(yield)`, the cart link is built in Kotlin | recipes are **not** part of the list yet |
| Saved shopping choices | `mealsMeta.walmart.prefs`, keys `sd:<id>` or `ing:<name>|g|ml|n` (regex `50-native-shopping.js:12`) | max 200, command `shopPrefSet`/`shopPrefClear` |
| Meal prep | `plan.prep[{when: weekday, minutes, task}]` shown as text on the Plan pane (`sdPlanHtml` 134) | text only; recipes' `prep`/`total` are strings like "15 min" |
| Cook-along | none | `steps[]` exist per recipe and meal as plain strings |

The `ing:<name>|unit` key already assumes a normalised ingredient name and unit, but nothing builds one from the free-text lines yet.

### 3.7 Who else reads or writes Food data

| Consumer | Where | What it touches | Plan |
|---|---|---|---|
| Life Director meal link | `logic.js:1113-1130`, `mealModel` 1131; `HostMeals.kt:40-97` resolves the day and slot, `MealDays` (173) answers `sdDay` | read only: `{day,index,slot,name,kcal,logged,skipped}` from `sdDay`; opening a meal just navigates | WP-F8: a native `MealDays` over the ported `sdDay`, replacing recorded answers; LD goldens must still pass |
| Economy nutrition bonus | `44-rewards.js:47`; `Bonuses.nutrition` | reads `calGoal` and today's `foodLog` kcal; writes `nutriDone` and an activity | unchanged; uses the ported day total (WP-F3) |
| Voice | `50-voice-gen5.js:30-31, 427-428, 434-518` (own estimate table, `allFoods()`, writes `foodLog` with `voice:true`, `foodId:'voice_est'`, and `waterLog`), `50-voice-gen4.js:334`, `46-voice-nlu.js:400` | writes and reads | stays page-owned; its writes must keep the entry shape (3.8); not ported in this plan |
| Quests | `50-quests-hand.js:138` | reads `waterLog` against `waterGoalMl` | page-owned |
| Settings, backup | Food database card; `backupJson` copies whole `state` but **not** the USDA foods (`SETTINGS_INVENTORY.md:744`) | | native store is outside backups too (OQ-F4) |
| Demo mode | IDB names get a demo prefix (`DEMO_MODE.md:12`) | | the native store needs the same separation |
| Widget, Overview, Health | grep found no Food reads | | **unverified** beyond the grep |

### 3.8 Saved data: keys, shapes and every writer

All under the main save `lifemax_rpg_prototype_v1` (`STORE`, `01-runtime.js`), written by `save()` (`04-runtime.js:44`), which returns whether it
stored. USDA foods are in IDB, outside the save.

| Key | Shape | Writers |
|---|---|---|
| `foodLog` | array of `{id, date 'YYYY-MM-DD', slot (breakfast lunch dinner snacks), foodId, name, grams, kcal, kcalEstimated, p, c, f, fib, sug, na, at ISO, sd?{diet,meal,i}, voice?}` | `fv2Log`, `fv2EditEntry`, `fv2RemoveEntry`, `deleteMealEntry`, `fv2LogAgain`, `fv2CopySlot`, `sdLogMeal`, sd logall/unlog, voice (gen5 510, 30), native `write()` wrapper, dead legacy copies |
| `customFoods` | array of food objects (per 100 g, `cat:'Custom'`, `port[]`) | `fv2CreateFood` (and dead `saveCustomFood`) |
| `waterLog` | array `{id, date, ml, at, voice?}` | `fv2AddWater`, voice (gen5 427, 31) |
| `mealsMeta` | `{calGoal, protGoal, carbGoal, fatGoal, waterGoalMl, favs[], sd{diet,level,veg,df,over{date:{i:mealId}},skip{date:{i:true}},shop{weekStart:{ingId:true}}}, walmart{prefs}}` | `fv2SetGoals`, `fv2ToggleFav`, `sdAct` (diet, level, pref, skip, unskip, check, clear), `sdSwapApply`, native sd commands (`50-native-nutrition.js` about 1000-1050), `shopPrefSet` |
| `nutriDone` | `{date: 1}` | economy only |
| read, not owned | `classId`, `activities` | economy |

`foodLog` is also the only Food key the native save codec knows, as `PARTIAL` (`[].date`, `[].kcal`) in `core/save/SaveFields.kt`; every other
Food key rides through as unknown text. Eleven distinct writer sites; the page-is-only-writer rule holds today because every path ends in the
page's `save()`.

## 4. Candidate page defects (nothing fixed; each is a ruling for the owner)

Found by reading. "Not run" means the behaviour is read from the code, not reproduced; each gets a page test before it is ruled.

| ID | Where | What |
|---|---|---|
| FD01 | `50-food.js:192-195` | USDA rows come in index order (alphabetical term, then id), not by relevance; only the first typed word probes the index; the 80-entry cap is spent on repeated entries of one food (a food with "milk" and "milkshake" counts twice for "milk"), so for a common first word good matches can be cut off. Not run. |
| FD02 | `08-runtime.js:242` (import), `50-food.js:187` | Terms are `[a-z0-9]+`, so "jalapeño" is stored as "jalape" and "o", and a typed "jalapeño" or "m&m's" never matches the index; branded foods whose first typed word has an accent or a symbol are unreachable by USDA search. Not run. |
| FD03 | `08-runtime.js:239-245` | The import does not say how many lines it skipped (bad JSON, missing `id` or `n`), and a re-import never removes old foods. |
| FD04 | `01-runtime.js:47` | 268 of 363 built-in foods have energy 0 and show as estimated from 4/4/9 on macros (counted). Probably an export gap in the table, not a decision. |
| FD05 | `50-food.js:46-49`, `50-food-forms.js:69` vs `50-smartdiet.js:42,61` | "Copy yesterday" and "Log today" copy the whole entry including its `sd` tag; on another day that entry can count as the plan's logged meal, or as neither plan nor extra (`extra` skips entries with `sd`). Not run. |
| FD06 | `50-food.js:19`, `usdaFoodsCache` | The edit view and re-log find a USDA food only if a search this session cached it; after a restart they rebuild the food from the entry's rounded numbers (`fv2Per100`), losing precision, brand and servings. A native store that finds the real food would behave differently (OQ-F6). |
| FD07 | `50-smartdiet.js:65,178-196`, native twins `50-native-nutrition.js` about 1000-1050 | Smart Diet writes (log one meal, diet, level, preferences, skip, unskip, unlog, tick, clear) ignore the result of `save()`; the page's `fv2Saved` take-back is not used, so a failed save shows success. Voice food and water writes also ignore it (`50-voice-gen5.js:30,31,428`). |
| FD08 | `44-rewards.js:150` | Logging pays nothing and the nutrition bonus cannot fire (ECONOMY D02, D25). Recorded here so the port does not "fix" it unasked. |
| FD09 | `04-runtime.js:84`, `fv2Fmt`, `fv2DayShift` | Day keys come from `toLocaleDateString('en-CA')` and digit grouping from `toLocaleString()`, both device-ICU dependent; recents compare `new Date(at)` (a UTC instant) with `new Date(date)` (UTC midnight of a local date). Native must pin the formats. |
| FD10 | `mealsMeta.sd.skip/over/shop`, `foodLog`, `waterLog` | Nothing is ever pruned, and every `save()` rewrites the whole state; growth rate unmeasured. |
| FD11 | `50-smartdiet.js:134` | The empty state says the plan is built for the Athlete class, but `sdDay` is not class-gated. Informational. |
| FD12 | `custom_`, `quick_` ids | `'custom_' + Date.now()` and `'quick_' + Date.now()`: two created in the same millisecond collide. Low. |

## 5. Work packages

Sizes: S under 3 days, M 3 to 8, L over 8 (of focused work). Each package ends with a spec section, golden cases, mutation checks and a
phone check. Rule IDs by area: FN (nutrition), FD (days), FC (catalogue and local search), FU (USDA), FW (writes), FS (Smart Diet), FH (host).
Defect IDs are FD01 and up (section 4). Nothing below is wired to a screen until WP-F10.

Order: F1 → F2 → F3 → F4 → (F5 in parallel with F6) → F7 → F8 → F9 → F10. F5 starts as soon as the owner supplies the file facts.

### WP-F1. Recorder, loaders and data export (M, risk medium) - recommended first

Scope. `tools/gen_food_golden.js` and `tools/food_vm.js`: the recorder drives the real built page in Chromium (as `gen_ld_*` and
`gen_economy_golden.js` do) and node runs a second copy where possible. Important: the segments from 06 to 12 are cut in the middle of
statements (`05-shadow-boot.js` header), so `08-runtime.js` cannot be loaded alone; the vm loader slices named functions out of the segment
text (and checks they are still there), while `50-food*.js` and `50-smartdiet*.js` are function-only and load whole. A stub page supplies
`state`, `save()` (returns true or false on request), `renderMeals`, `fv2Snack` (records every notice), an injected clock and a seeded
`Math.random`. Export `FOOD_DB`, `RECIPES`, `RECIPE_FOODS` fields and the Smart Diet JSON to
`android-wrapper/native/src/main/resources/food/*.json` with a drift test. Kotlin: `GoldenFiles`-style loader, `Clock` and `RandomSource`
interfaces, the JavaScript-number helpers (`jsRound`, `num0` for `Number(x)||0`).
Golden cases: header with versions and ICU; a self-check that two runs record the same bytes (every case recorded twice).
Size S to M. Owner check on the phone: none.

### WP-F2. Nutrition kernel (S to M, risk low-medium)

Scope. `foodEnergyInfo`, `foodEnergyLabel`, `scaleFood`, `fv2Tot`, `fv2Per100`, `fv2Unit`, `fv2PL`, `fv2StepGrams`, `fv2MultGrams`,
`fv2ServingsData`, `fv2ImpactData`, `fv2RowData`, the goal helpers (`fv2GoalSum`, `fv2PresetGrams`, `fv2GoalStep`, goal defaults), the
10,000-gram and zero cases. Golden cases recorded from the page's own functions: every built-in food and recipe food at about 60 amounts
(including 0, fractions, 0.05 steps, huge, NaN text), foods with strings and nulls for numbers, 4/4/9 estimate boundaries, fuzzed foods
(seeded generator), about 25,000 cases. Mutation checks: flip rounding, drop the estimate rule, swap `*m*10` order. Size S to M. Phone
check: none (nothing is wired); a one-time instrumented run of the corpus for floating point.

### WP-F3. Days, totals, targets and clock rules (S, risk low)

Scope. `dayFoodLog`, `fv2Water`, `fv2Goals`, `fv2DayShift`, `sdMonday`, `sdWeekday`, `fv2Slot`, the week strip and 7-day average inputs, "left or over"
words' numbers. Date keys through `java.time` with the Life Director kernel's `CalendarMath` where it applies. Golden cases: states with
entries on many days and edge dates (month ends, leap day, DST days in eight zones, an entry dated by an earlier zone); `fv2Slot` for every
half hour. Pin the digit grouping decision (FD09, OQ-F13). Size S. Phone check: none.

### WP-F4. Catalogue, local search, recents and favourites (M, risk medium)

Scope. `allFoods` order, `foodById`, `fv2Match`, `fv2SearchTokens`, `fv2LocalFoods` (stable sort), `fv2Recents`, `fv2FoodOf`, `fv2Favs`, tab lists, `fv2NoneWords`,
Quick food, `recipeHits`. Golden cases: a query corpus (single and multi-word, upper case, accents, symbols, empty tokens, repeated spaces)
over the built-ins with and without custom foods and in each class; recents with equal timestamps and voice estimates. The case files carry
result ids in order. Risk: JavaScript's `toLowerCase` against Java's (use `Locale.ROOT`; special cases such as final sigma and dotted I are tested).
Size M. Phone check: none.

### WP-F5. USDA import, store and search (L, risk high; needs the owner's file facts)

Scope and design in section 6. Golden cases: the page imports a **generated** sample (about 5,000 foods built by `tools/gen_usda_sample.js`, seeded, with
accents, symbols, duplicates, ids that sort differently as text and as numbers, non-gram servings, missing fields, bad lines) into IDB in
Chromium; the recorder then runs `fv2UsdaFoods`/`fv2Search` for a corpus of about 4,000 queries and records the ordered id lists and counts, and
the import counts. Kotlin builds its store from the same gzip file in the test and must return the same ordered ids for every query.
Mutation checks: change the LIMIT, the order key, the tokenizer, the dedupe. The real check is on the phone (section 7). Size L.
Phone check: pick the real file in the native importer; read the count (must equal the page's Settings count unless lines were skipped; the
importer lists skipped lines); run 20 searches the owner chooses and compare by eye with the page; watch battery, time and storage.

### WP-F6. Smart Diet engine (L, risk medium-high)

Scope. Everything in 3.5 except presentation and the coach words: `sdMealIngs`, `sdMealOk`, `sdFit`, `sdTotals`, `sdSolve`, `sdActivityMin`, `sdAutoLevel`,
`sdDay`, `sdShopList`, `sdShopText`, `sdSwapView`, `sdSwapApply`, and the state changes of the logging, skip, unlog, level, diet and preference commands,
with failed-save branches. Golden cases from the page: both diets × 7 days × four levels × vegetarian/dairy-free × skips, swaps, logged meals,
extra food, meals logged out of order, calorie goals from 1,200 to 4,000, base plan reference, activities on that day, week boundaries; shopping lists
for this and next week; about 6,000 cases. The 40-step bisection is deterministic in doubles; sums must be added in the page's order. Mutation checks on the
clamp, the `-0.04` pack rule, the `k % count` pick, the level table. Size L. Phone check: open Plan and Shopping on the same days in the page's and the
native screens (shadow does the rest).

### WP-F7. Write operations and the Food part of the save codec (L, risk high)

Scope. The logging family of 3.4 as pure functions `(state, op, clock, random, saveResult) -> (state', reply, notice)`: log, edit, remove, again,
copy slot, create, favourite, targets, water, plus the idempotency list of 40 operation ids and the Undo closures as data. Extend
`core/save` (`SaveFields`, `EconomySaveReader/Writer`) so `foodLog`, `waterLog`, `customFoods` and `mealsMeta` are owned and written with unknown fields kept
byte for byte (the Life Director store spec's round-trip rule). Golden cases: state before, op, injected ids and clock, `save` true or false →
state after, reply, notice text; about 3,000 cases including every failed-save take-back. Plus codec round trips on real saves captured from Chromium.
Not wired. Mutation checks: skip the take-back, change id format, mutate slot. Size L. Phone check: none until cutover; the shadow compare (F9) covers it.

### WP-F8. Adapters and consumers (S to M, risk low-medium)

Scope. A native `MealDays` over WP-F6's `sdDay` for `HostMeals`; the day total the economy's nutrition bonus reads; a read model for the Overview and widget if
they ever need calories (none found today, unverified). Goldens: re-run the existing LD `meals` family and `economy-golden` with the real `MealDays`; add
cases where a meal is logged and skipped between calls. Size S to M. Phone check: none.

### WP-F9. Food shadow mode (M to L, risk medium)

Scope and design in section 7. Page module `50-native-food-shadow.js`, native `core/foodshadow`, a ring with redaction, a Settings switch. Size M to L.
Phone check: switch it on and use Food normally (section 12).

### WP-F10. Cutover (L, risk high)

Prerequisites in section 8. Moves the sheet and Smart Diet commands to a native writer, one family at a time (read model, then each write), each behind its
own switch with the page kept as a read-only fallback for one release. Size L. Phone check: a full day of real logging on both sides, then the rollback test.

## 6. USDA design

**Recommendation: native reads the same `.jsonl.gz` and builds its own SQLite database with a sorted `terms` table that reproduces the page's
index order exactly. Do not start with FTS5.**

Why. The page's USDA result is defined by an index scan (3.2): first token as a prefix, 80 entries in (term, id) order, then an
all-tokens filter and a cap of 30. A table `terms(term TEXT, id TEXT, PRIMARY KEY (term, id)) WITHOUT ROWID` read with
`WHERE term >= ? AND term < ? ORDER BY term, id LIMIT 80` gives the same 80 entries in the same order, so parity is checkable and exact
(`BINARY` collation; both sides compare ASCII terms; `id` stored as TEXT, never a number). FTS5 would give a better ranking (bm25, prefix queries,
phrase queries) but a different result list, so every parity case would fail and the shadow check would have nothing to compare. Better ranking is a
separate, owner-approved change made on the page first with new goldens (OQ-F1), and then FTS5 is worth adding next to the same tables.

Why not an in-memory index: a hash map of 500,000 foods with strings costs roughly 200 to 400 MB of Java heap once object overhead is counted and has to be
rebuilt at every start (10 seconds or more), which a phone will kill. A memory-mapped custom file would work but is more code than SQLite for no gain. (Estimates, unmeasured.)

**Tables.** `foods(id TEXT PRIMARY KEY, n, brand, cat, kcal, p, c, f, fib, sug, na, port_g, port_label)` (or one JSON text column; recommended: columns, since only these
fields are read by the sheet), `terms` as above, `meta(key, value)` holding a build id, the count, the skipped-line count and the source file's size and name.
The all-tokens test runs in Kotlin on the 80 candidates' `lowercase(n + ' ' + brand + ' ' + cat)` exactly as `fv2Match` does. A session map plays
`usdaFoodsCache`'s role until OQ-F6 is ruled.

**Import.** Android file picker (Storage Access Framework) → streaming gunzip → line reader → a streaming JSON reader (not the whole file in memory) →
batches of 5,000 in one transaction. Same line rules as the page (US-1..): skip blank, bad JSON, missing `id`/`n`; count skipped lines and show the count (FD03); same
`port` rule; same tokenizer. To make bulk load fast, load `foods`, insert `terms` in sorted batches (or drop and rebuild the `terms` index after the load), and
write `meta` last, so a half-built database is never read. Run as a foreground job with a notification, progress, Cancel, resume from the last committed batch, a free-space
check before starting (needs about 1.2 times the estimate below), and no search against a store whose `meta` is missing. The page's own import stays until cutover.

**Estimate for a realistic file** (not measured; the real file is on the owner's phone, not in the repo). Assumptions: about 200 bytes of JSON per food, 7 terms per
food, gzip about 4.5 to 1.

| Foods | JSONL (unzipped) | `.gz` | Native SQLite on disk | Import time on a mid-range phone | Heap while importing | One search |
|---|---|---|---|---|---|---|
| 100,000 | 20 MB | 5 MB | 40 to 60 MB | 10 to 30 s | under 50 MB | under 10 ms |
| 500,000 | 100 MB | 22 MB | 200 to 300 MB | 1 to 3 min | under 50 MB | under 20 ms |
| 2,000,000 (the full USDA branded set, if unfiltered) | 400 MB | 90 MB | 0.8 to 1.2 GB | 5 to 12 min | under 50 MB | under 50 ms |

The page's IDB copy costs about the same again, so until cutover the phone holds two copies: warn before building, and offer "Free page copy" after cutover (OQ-F4). The real
file's food count is shown by the page's Settings card, so the first owner step is to read that number (section 12).

**Parity risks specific to USDA.** (1) IDB orders string keys by UTF-16 code unit, SQLite `BINARY` by UTF-8 byte; they agree for ASCII and for the Basic Multilingual
Plane except U+E000 to U+FFFF against characters above it; terms are ASCII, ids are unverified, so the sample generator includes odd ids and the shadow compares real
ones. (2) Duplicate index entries for one food must be reproduced (they are, because `terms` has one row per distinct term). (3) `lowercase()` differences (use `Locale.ROOT`).
(4) A query whose first token is empty after trimming (the page returns early). (5) Demo mode must use a separate database file name. (6) Storage, battery and time are
phone matters that tests cannot show.

## 7. Shadow-mode design (WP-F9)

Modelled on `ECONOMY_SHADOW_PLAN.md` and `LIFE_DIRECTOR_SHADOW_SPEC.md`: its own switch in Settings, Data and advanced ("Native Food check (beta)", off by default), the page module sends, native
compares in `core/foodshadow`, never writes, keeps a redacted ring, and disables itself after repeated faults or when the page's frame budget is exceeded. It joins the existing
`NativeShadow` plugin with `scope: "food"`, independent of the economy and Life Director checks (OQ-F12).

**What is compared** (each is a check family with its own counters):
1. **Save text (after WP-F7).** On each save the page sends the exact stored text of `foodLog`, `waterLog`, `customFoods` and `mealsMeta`; native runs the codec's strict read and
   writes it back and requires byte equality. Differences show as a path and a kind, not content.
2. **Read models (after WP-F2 to F4, F6).** At most once a minute and on change: the page sends its own day totals, goals, water, week ring fractions, recents (ids and grams),
   tab lists (ids), the Smart Diet day (`sdDay` summary: target, level, factor, each meal's kcal and state) and the week's shopping rows (id, grams, packs); native recomputes from the state it holds and compares.
3. **Search (after WP-F5).** The page sends each *settled* sheet query (the one whose `serial` survived the debounce, not every keystroke) with its ordered result ids and the local/USDA split;
   native runs the same query on its own store and compares id lists, counts and order. Sampled to at most 30 queries a day and a first-use sweep of 50 fixed words the owner can run once.
4. **Commands (after WP-F7).** For each logged, edited, removed, copied, created, water, goals and Smart Diet write, the page sends the state before, the payload, the injected clock and ids, and the save result,
   and its state after and notice; native replays and compares state after, reply and notice text (WP-LD12b's method).
5. **Failed saves.** A synthetic switch (debug builds only) makes `save()` fail once so the take-back is compared.

**Privacy.** The messages travel inside the app on the phone, never over the network. Everything that is *kept* (the ring, the report, anything shared with the assistant) is redacted
the economy way: never a food name, brand, entry name, custom food name, recipe note, typed query or free text. They appear only as `<text n=12 #a1b2c3>` (length and the first six hex
digits of SHA-256 with a per-install salt). Allowed raw: numbers (kcal, grams, macros, counts, packs), slots, the Smart Diet level and diet ids, day keys, rule IDs, and the ids of **built-in and USDA foods**
(public data; OQ-F11); ids of custom, quick and voice foods are hashed. Paths into maps keyed by dates use the date key; keyed by free text use `*#hash`. A test feeds every golden state and every
query with sentinel names and asserts none appears in the ring or report output (the `Redactor` test). Messages over 384 KB are not sent; the `foodLog` is sent whole only to the codec check, in the
page's exact JSON text, and is not copied to the ring.

**Limits.** Same style as the other checks: a message budget per minute, a size cap, a timeout, self-disable after N consecutive faults, and the page never waits for native.

**Exit criteria (recommended).** 30 consecutive days of the owner's own use with zero unexplained differences, covering: at least 200 compared USDA queries including a one-letter query, a four-word query and
an accented query; one week rollover of the Smart Diet with a skip, a swap and a "log the rest of the day"; a create-food, an edit, a remove with Undo, and a copy-yesterday; one failed-save take-back; a day
with DST or a zone change if one occurs (else a simulated one in the corpus). Every difference is either fixed in native or ruled a page defect (fixed on the page first, with a new golden).

## 8. Cutover prerequisites (WP-F10)

1. WP-F1 to F9 done; shadow exit criteria met; the corpus run once as an instrumented test on the phone (floating point, `Locale.ROOT`, tz data).
2. The Food keys owned by the native save codec (WP-F7) and a decision for the other eleven writer sites: voice and native commands that write `foodLog` must either go through the native writer or stay
   page-written with the page still the only writer of that key. A key has exactly one writer at a time; cutover is per family, never half a family.
3. The USDA store built on the phone, parity shown by shadow, and the page copy kept for one release.
4. All page consumers re-pointed or confirmed unaffected: economy nutrition bonus, voice, quests' water, Life Director's meal link (WP-F8), backup (what a backup contains, OQ-F4), demo mode.
5. Owner rulings on FD05, FD06, FD07 and OQ-F1 to OQ-F9.
6. A rollback: the page's Food stays in the build for one release, read-only, with a switch that makes it the writer again; native writes the same shapes so the page can load them.

## 9. Features that sit on top of the ported logic (not part of the port)

None is built or specified yet; the task list names them (cook-along recipes, weekly shopping, meal-prep kits) and this audit found no document for them (**unverified** that none exists elsewhere).
They should be native-first features with their own spec, written *after* WP-F2, F4, F6 and F7, so they call the same nutrition kernel instead of copying it.
- **A shared new piece: the ingredient parser.** Recipe ingredient lines are free text with fractions and units ("¼ cup onion, diced", "30 oz canned black beans (about 2 cans)").
  A pure `parseIngredient(line) -> {qty, unit, name, note}` plus a unit table to grams for common foods is needed by all three features, and the shopping keys
  (`ing:<name>|g|ml|n`, `50-native-shopping.js:12`) already assume it. It has its own golden cases (all 43 recipes' lines, edge forms) and a known-error list; recipes whose lines cannot be parsed stay text.
- **Weekly shopping.** `sdShopList` (Smart Diet rows) plus parsed recipe lines for the recipes the owner plans, merged by ingredient and aisle, rounded to packs by the same rule, with the existing Walmart match preferences.
  Pure function of (the week's plan, chosen recipes, servings, preferences); the saved choices stay in `mealsMeta` (a new bounded key, written through the page until cutover, like `walmart.prefs`, so older apps ignore it).
- **Meal-prep kits.** A kit = chosen recipes with servings → scaled ingredient list, total prep and cook minutes (parse "15 min" / "1 hr 5 min"), a step schedule, storage days (`keeps_days` from the catalogue, else a recipe field),
  and planned portions to log later. Life Director could place the prep as a session through its existing meal and plan session links. Needs the parser and a time-text parser.
- **Cook-along.** A step-by-step screen over `steps[]` with servings scaling (ingredient quantities from the parser), timers detected from the step text ("bake 15–20 minutes") or added by hand, a keep-screen-awake setting, and a final "Log this recipe" through WP-F7's log.
  Timers should reuse the Life Director timer work (WP-LD10), not a new clock. Step text stays as it is; no new nutrition numbers are made, only the cookbook's per-serving values scaled by the kernel.
- **Rule for all four:** anything new that must be saved is written by the page (a bounded, defensively read key) until the Food cutover, and by native after it; nothing writes a key that has two writers.

## 10. Risks

| # | Risk | Mitigation |
|---|---|---|
| R1 | USDA parity: the page's order is an IndexedDB index scan with an 80-entry cap, first-token-only probing and duplicate entries; the real file's size, fields and ids are unknown and not in the repo | the `terms` table copies the order; a generated adversarial sample; shadow search compare on the real file; the owner supplies file facts first |
| R2 | Moving writes: the codec must own `foodLog`, `waterLog`, `customFoods`, `mealsMeta`; eleven writer sites, three of them outside Food (voice, native commands, dead code); a slip corrupts the saved game | codec round-trip goldens on real saves; unknown fields kept byte for byte; one writer per family; shadow save-text check; rollback switch |
| R3 | JavaScript against Kotlin semantics: rounding, `Number()`, `||`, `toLocaleString`, `toLowerCase`, ICU dates, stable sort, double sums in the Smart Diet solver | a number helper layer, order-preserving sums, `Locale.ROOT`, goldens that include odd inputs, an on-phone corpus run |
| R4 | Phone resources: a second USDA copy (tens to hundreds of MB), import time and battery | disk check, foreground job, resume, cancel, "Free page copy" after cutover |
| R5 | Scope creep: improving search, fixing defects and porting in one step makes parity unprovable | port as is; every defect is a ruling; fixes land on the page first with a new golden |
| R6 | The Food screen is still being changed (add-food sheet phases, swap, shopping) | build on current branch heads; the loader fails loudly when a named function disappears |
| R7 | Merge conflicts in `50-native-nutrition.js` and the shared tab kit | page seams only (read-only exports), as `FOOD_ADD_SHEET.md` did |

## 11. Open questions for the owner

Each has a recommendation; the default if not ruled is "port as is".

| ID | Question | Recommendation |
|---|---|---|
| OQ-F1 | Port the USDA search exactly as it is (index order, first word only), or improve ranking in the same step? | Exactly as is. Better ranking later, on the page first, with a new golden; then add FTS5. |
| OQ-F2 | SQLite with a parity `terms` table, or FTS5 now? | The `terms` table (section 6). |
| OQ-F3 | How does native get the USDA foods for the check: pick the file again in a native importer, or stream them out of the page's database? | Pick the file again (one time, simple, resumable). Streaming from IDB is heavy and ties native to the page. |
| OQ-F4 | After cutover, keep the page's USDA copy? Should backups include either copy? | Keep it one release as rollback, then offer to free it. Backups stay without USDA foods (as today). |
| OQ-F5 | Fix accents and symbols in USDA search (FD02)? | Port as is; fix on the page after the check, with a new golden (normalise accents, tokenise the query like the index). |
| OQ-F6 | Editing or re-logging a USDA entry after a restart (FD06): keep the lossy rebuild, or look the food up by id? | Fix on the page first (look up by id in IDB when the cache is empty), then port the fixed behaviour. |
| OQ-F7 | The 268 built-in foods with energy 0 (FD04): fix the table? | A data fix, separately, after the port; it changes new entries only. |
| OQ-F8 | Logging pays nothing and the nutrition bonus cannot fire (FD08): keep? | Keep in the port. Decide separately whether logging should earn anything. |
| OQ-F9 | Copy yesterday and Log again carry the `sd` tag (FD05) and Smart Diet writes ignore failed saves (FD07): fix? | Yes, on the page first (drop `sd` on copy; use the take-back pattern), each with a test, before their goldens are recorded. |
| OQ-F10 | Which package first: Smart Diet or USDA? | USDA's recorder and sample generator first, in parallel with Smart Diet; USDA has the phone time and the unknown. |
| OQ-F11 | In the shadow ring, may ids of built-in and USDA foods appear raw? | Yes (public data); custom, quick and voice ids hashed; names never. |
| OQ-F12 | Run the Food check alongside the economy and Life Director checks? | Yes, separate switch, separate budget; turn all three off on a slow phone. |
| OQ-F13 | Numbers and words: until cutover native draws the page's words. At cutover, which locales must native format (digit grouping, dates, "Today / Yesterday")? | Tell us the device locales you use; pin them in goldens; others fall back to the page's strings. |
| OQ-F14 | The coach line and the Plan pane's words (`sdCoach`, 95-106) are logic wrapped in prose: port them natively or keep them from the page? | Keep from the page until after cutover; they change often and are not data. |
| OQ-F15 | Where do new feature data (meal-prep kits, cook-along history) live? | New bounded keys under `mealsMeta`, written by the page until cutover. |

## 12. What the owner does

1. Open Settings, Data and advanced, Food database and write down the food count; tell us the file's size and where it came from (and any script that made it). Send the first 200 lines of the file if you are
   willing (it is public USDA data; the file never goes in the repository).
2. Rule OQ-F1 to OQ-F15 (or say "recommendations").
3. WP-F5: pick the real file in the native importer when asked, with the phone charging (it works offline); check the count and 20 searches you choose against the page.
4. WP-F9: turn on "Native Food check (beta)" and use Food as usual for 30 days: log meals, search packaged foods, use Plan and Shopping, create a food, edit and undo. Once a week open the check's report and share it.
5. Before cutover: one day of real logging with native as the writer and the page as the fallback; then try the rollback switch once.
6. Report anything that looks different from before, even a rounding of 1 kcal.

## 13. Not verified in this audit

- The real USDA file: size, count, field set, id format, `fib/sug/na` presence, and every time or size estimate in section 6.
- That IndexedDB returns multiEntry index entries in (term, id) order with one entry per matching term in this WebView's engine: this follows the specification and the code's own use, but was not run (FD01, FD02, FD06 likewise).
- Whether anything outside the files listed in 3.7 reads Food data (the grep covered `src/`, not the Android widget or native-side callers).
- Voice's food matching and the `G5_EST` table beyond their call sites.
- That no document elsewhere already specifies cook-along, meal-prep kits or weekly shopping from recipes.
- Growth of `foodLog` and `mealsMeta.sd` in a long-lived save (FD10).
