# Food's add-food sheet: native build plan

Status: phases 1a (web) and 1b (native: decoder, search, portion and quick views, notice snack, screenshots) are built and tested; the Native API bump to 33 happens at merge. Phase 1c (review fixes) and phase 2 are open. Written from reading `50-food.js` and `50-native-nutrition.js` at commit `8291c40`. Not yet reviewed by a second reader: before building, have it checked against the code (the way the other plans in this folder were).

## 1. What there is today

Native Food (`NativeNutritionPlugin`, `feature/food`) draws Plan, Log and Shopping. Its "Add food" button, the daily targets
and tapping a logged entry all **hand over to the page**: the screen closes, and the page opens its own sheet
(`fallback()` in `50-native-nutrition.js:445`: `fv2Open(slot, 'search' | 'goal')`, `fv2OpenEntry(id)`).

The page's sheet (`50-food.js:142-247`, about 100 lines of dense code) has six views on one state object `fv2S().sh`
(`view`, `stack`, `q`, `tab`, `list`, `pick`, `grams`, `slot`, `edit`):

| View | What it does | Where the rules are |
|---|---|---|
| search | a search box (local foods plus the USDA database in IndexedDB, 110 ms debounce, ordered by prefix then length), tabs Recent / Favorites / My foods, one-tap re-log | `fv2Search`, `fv2List`, `fv2Recents`, `fv2Favs`, `allFoods`, `usdaDb` |
| portion | the food, its kcal and macros at the chosen grams, serving buttons, minus/plus steps (5, 10 or 25 g by size), a grams field, the meal slot pills, the day's targets with this food added, Log, favourite star | `fv2Portion`, `fv2Live`, `scaleFood` (`08-runtime.js:278`, linear, rounded to 0.1), `fv2Unit`, `fv2DoLog`, `fv2Log` |
| quick | calories and an optional name, logged as a one-off food | `fv2SheetClick` `quicksave` |
| create | a food from its label values | `fv2Create`, `fv2SaveCreate` |
| goal | the daily targets (calories and macros, presets) | `fv2Goal`, `fv2SaveGoal` |
| edit | an existing entry: change grams, save, delete, log again, undo | `fv2OpenEntry`, `fv2SaveEdit`, `del`, `again` |

Logging is `fv2Log` (`50-food.js:34`): it appends to `state.foodLog`, takes the change back if the save fails
(`fv2Saved`), buzzes and shows an Undo snack.

## 2. Decision

**The page keeps every rule and stays the only writer. The sheet's state stays on the page; native draws it and sends
commands.** Same pattern as native Log and Recipes: no rule is copied to Kotlin (not `scaleFood`, not the search order, not
the slot or step rules).

- The projection gains a `sheet` field: `null` when closed, otherwise the page's `sh` rendered as data (view, slot, query,
  tab, the rows with their kcal and macros, the picked food, grams, servings, the live kcal and macros, the day's targets
  with this food added, whether it is a favourite).
- **Food is not on the shared tab kit.** It has its own plugin that works by `action` events from native and `update` pushes
  from the page (every page change calls `scheduleSync`), with no command-and-reply channel. The sheet uses that model, not a
  new one: each sheet action runs on the page and the next update carries the result.
- Actions (all session-checked): `sheetOpen{slot}`, `sheetQuery{q}` (debounced on the native side), `sheetTab{tab}`,
  `sheetPick{index,id}`, `sheetRowAdd{index,id}`, `sheetStep{dir}`, `sheetGrams{grams}`, `sheetServing{grams}`,
  `sheetMult{x}`, `sheetSlot{slot}`, `sheetFav`, `sheetQuick`, `sheetQuickSave{kcal,name}`, `sheetLog`, `sheetBack`,
  `sheetClose`, and `noticeAction{id}` (the page's Undo). A row is addressed by its index and id, so a list that changed
  under the finger is ignored, not mis-tapped.
- Search is the one asynchronous part (the USDA lookup): the page answers at once with its own foods (`searching: true`) and
  sends an update when the database rows arrive; a serial discards stale results. An update asked for while one is in flight
  is sent after it (it used to be dropped).
- **Phase 1 draws search, portion and quick.** Create food, the daily targets and editing an entry keep handing over to the
  page's own sheet (today's behaviour), so nothing is lost while they wait.
- A prerequisite: native Food has **no message channel** today. The page's toasts and the Undo snack are drawn on the page,
  underneath the native screen, so a failed save inside the native sheet would say nothing. The kit's `notice` field (used
  by Settings and Library) must be added to Food first, and the Undo snack needs a native counterpart.

Why not copy the maths to Kotlin so grams steps feel instant: `scaleFood` is a few lines, but it also decides rounding and the
energy estimate (`foodEnergyInfo`), and the rule is that native never owns a rule before the logic phase. A command per step
is the same cost as native Log's preview command; debounce the typed grams, answer steps at once.

## 3. What the owner sees

Tap Add food: a native bottom sheet rises over Food (no close and reopen). Search types as you go, Recent / Favorites / My
foods as tabs, tapping a row opens the portion view, Log closes the sheet and the entry appears with an Undo. Create food,
the daily targets and tapping a logged entry still open the classic sheet until phase 2.

## 4. Files

Web (`lifemax_modular_v73`): `50-native-nutrition.js` (the `sheet` projection, the commands, the notice relay),
`50-food.js` (small seams only: a read-only `fv2SheetData()` that returns what the sheet shows, so the projection reads the
page's own state; no behaviour change), tests `native-nutrition-sheet.test.js`, the hand-over simulation's Food fake, fixtures
`food-sheet-*.json` from `tools/gen_native_fixtures.js`.

Native (`android-wrapper/native`): `feature/food/domain` (sheet models), `feature/food/data` (the decoder, strict like the
others), `feature/food/presentation/sheet/` (`FoodSheet.kt`, `SheetSearch.kt`, `SheetPortion.kt`, `SheetQuick.kt`), the view
model's command flow (operation ids per A14, repeat-safe), `NativeNutritionPlugin` (the new capability), Paparazzi tests
for each view in dark and light and at large text, `BRIDGE_CATALOG.json`, `CONTRACT.md` and `MIGRATION.md` rows.
A Native API bump at merge.

## 5. Phases and effort

| Phase | Contents | Steps |
|---|---|---|
| 1a, web (built) | the notice relay, the `sheet` projection, the commands, tests, fixtures, the simulation | 2 |
| 1b, native (built) | decoder, search and portion and quick views, notice snack, screenshots, docs | 3 |
| 1c | review fixes | 1 |
| 2 | create food, the daily targets, edit / delete / again with a native Undo | 3 |

Phase 1 is about **6 steps**; the whole sheet about 9. (The earlier estimate of 4 to 6 steps for "Food's add-food and swap
sheets plus Character's sheets" was too low for this sheet alone.)

## 6. Risks and open questions

- **The keyboard over the shared bottom bar** (backlog NAV-5): the sheet has a text field. Decide whether the sheet covers
  the bars or the bars hide while the keyboard is up.
- **Latency of a command per keystroke**: the 110 ms debounce is the page's own; measure on the phone.
- **The Undo snack** needs a native counterpart and a command (`undoLast`) that the page answers.
- **USDA lookups on a cold database**: the first search after install can be slow; the sheet should show a quiet "Searching"
  state, not an empty list.
- **Back and Android Back** inside the sheet: one step back through `stack`, then close (the page's `fv2Back`).
- **Merge conflicts**: `50-native-nutrition.js` is Food's own copy of the kit's hooks and changed in the swipe and integration
  merges; build on current main.
- Open: should "My foods" creation (phase 2) come before search, since an empty "My foods" tab is the first thing a new
  player sees? The plan says no, because search and Recent cover the common path.
