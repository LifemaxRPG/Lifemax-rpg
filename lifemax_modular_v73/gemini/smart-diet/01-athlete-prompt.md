# TASK: write the "Smart Diet" data for LifeMax's Athlete class: two complete 7-day diets plus one shared ingredient catalog

You are a sports dietitian and a meticulous data author. I am building LifeMax, a mobile fitness-and-life app. Its Food tab will have a feature called **Smart Diet**: it tells the user exactly what to eat today, lets them log each planned meal in one tap, and builds a weekly shopping list. Your output is the data that feature runs on. It is pasted straight into code, so the JSON format below must be followed exactly, and the numbers must be internally consistent (I will run a validator over every file you give me; anything it rejects comes back to you).

Scope of this task: the **Athlete** class only, **two diets**, one shared ingredient catalog. Other classes and the activity-based tailoring logic come later, but your data has to make that tailoring possible (section 3).

## 1. Who this is for

An active adult who trains 3 to 6 days a week (lifting, running, sport, or a mix) and wants performance, recovery and a lean, strong body. They shop in a **US grocery store** and cook at home. Assume a stovetop, an oven, a microwave, a basic blender and a rice cooker or pot; no air fryer, no sous vide, no specialty equipment. Assume a normal budget: no wagyu, no exotic superfoods.

Reference day (the plan as written, before tailoring):
- **2,500 kcal**, **165 g protein**, **290 g carbohydrate**, **78 g fat**, **at least 35 g fiber**.
- Protein spread across 4 or more eating moments, roughly 30 to 45 g at the main meals and 15 to 30 g at snacks.
- Carbs weighted toward breakfast, the pre/post-workout slots and dinner. Fat kept lower around training, higher at other times.
- Sodium roughly 2,000 to 3,200 mg a day, added sugar low, mostly whole foods.
- Every day must land within **±5 % of 2,500 kcal** and within **±10 %** of the protein, carb and fat targets (the validator checks this).

## 2. The two diets

Write both. They share one ingredient catalog (section 4) and the same JSON shapes (section 5), but they are different products:

**Diet 1: `standard`** (the "Standard Diet")
Balanced whole-food eating with variety. Real recipes, but still practical: active cooking time **40 minutes or less** per meal, up to 14 ingredients per meal, at most 3 steps of batch cooking per week. Leftovers and batch prep are encouraged (cook once, eat twice). Across the week use no more than **60 different ingredients**. Meals should feel like food a person is happy to eat, not diet food. Vary protein sources across the week (chicken, turkey, beef, fish, eggs, dairy, beans/lentils, tofu).

**Diet 2: `simple`** (the "Simple Recipes" diet)
The most basic meals that still hit the same macros. Minimal cooking and minimal shopping:
- **Active time 15 minutes or less** per meal. Many meals should be 0 to 5 minutes (assemble, microwave, or heat and eat).
- **At most 8 ingredients per meal** (water, salt, pepper and cooking spray do not count and should not be listed).
- Across the week use no more than **35 different ingredients**, and reuse them heavily so the shopping list is short (for example the same rice, the same frozen vegetables, the same chicken, the same yogurt).
- No more than **6 short steps** per meal. Prefer one pan, one pot, one bowl.
- Smart shortcuts are welcome and expected: microwave rice pouches, rotisserie chicken, canned tuna and beans, frozen vegetables, pre-washed greens, instant oats, deli turkey, pre-cooked grains. List the exact product type in the ingredient name so it is clear (for example "Rice, white, microwave pouch, cooked").
- One protein shake a day is allowed (flag it, see `tags`), and nothing else that needs a supplement.

Both diets: no required supplements except the optional shake above, no alcohol, no energy drinks. Coffee and tea are fine but do not count toward macros.

## 3. It must be tailorable (this is a design requirement, not an extra)

Later the app will scale the plan to the user's activity level (rest day, light, moderate, hard training day) by changing portions. To make that possible, every ingredient line carries a `flex` range: the minimum and maximum multiplier the app may apply to that ingredient's grams without breaking the meal. Rules:
- Put flex on the **carbohydrate sources and fat sources that scale naturally**: rice, oats, bread, pasta, potatoes, fruit, olive oil, nuts, nut butter, granola. Typical range `{"min": 0.6, "max": 1.6}`.
- Put flex on **protein sources too, but with a narrower range** (for example `{"min": 0.85, "max": 1.3}`), because protein should not swing as much.
- Vegetables, sauces, spices, eggs counted in whole units, and anything that would make the meal weird if scaled get `"flex": null`.
- **The validator requires this:** if every flexible ingredient on a day is set to its minimum, that day must come in at or below **2,125 kcal** (85 % of reference); at its maximum, at or above **3,250 kcal** (130 % of reference). So a day must contain enough flexible food to cover roughly 2,100 to 3,250 kcal.
- Mark each day's `type` as `training` or `rest`. Include some training days with `pre_workout` and `post_workout` meals (the app will drop them on rest days). In a week, use 4 `training` days and 3 `rest` days. A `rest` day must not contain pre/post-workout meals; its slots are breakfast, lunch, dinner and at least one snack.
- Every day, training or rest, is still written as a **2,500 kcal day at flex = 1**. The app does the tailoring; you do not need to write extra tiers.

## 4. The shared ingredient catalog

One list of every ingredient used by either diet, so nutrition is stored once and the shopping list can add things up. Rules:
- Ingredient names should be **generic and match USDA FoodData Central naming** where possible ("Chicken, breast, boneless, skinless, raw", "Rice, brown, long-grain, cooked", "Oil, olive, extra virgin", "Eggs, whole, raw, fresh"), so I can link them to the app's food database. Put the nearest USDA name you are confident about in `usda_hint`. **Do not invent FDC ids.** If you are not sure of a name, say so in `usda_hint` as "approximate: ...".
- `per100g` gives kcal, protein, carbs, fat, fiber, sugar (total, g) and sodium (mg) **per 100 grams of the ingredient in the state you list**. Use realistic USDA-style values.
- `state` is one of `raw`, `cooked`, `packaged`. **All gram weights in meals are of the ingredient in that state** (raw chicken weighed raw, cooked rice weighed cooked). Be consistent.
- `shop` tells me how to buy it: `aisle` (one of `produce`, `meat_seafood`, `dairy_eggs`, `bakery_grains`, `canned_jarred`, `frozen`, `pantry`, `snacks`, `condiments_spices`), `unit` (a real purchase unit: "lb", "oz bag", "dozen", "15 oz can", "16 oz tub", "each", "bunch"), `unit_g` (grams in one purchase unit, as sold), `pantry` (true if a normal household already has it: salt, pepper, oil, common spices, flour, sugar, honey) and `keeps_days` (how many days it lasts once bought, for the shopping list to warn about fresh items).
- Every ingredient used by any meal must be in the catalog, and every catalog entry must be used by at least one meal.

## 5. Exact JSON formats

Output three separate JSON files, each in its own code block, in this order: **(A) catalog, (B) standard diet, (C) simple diet**. Each must be complete, valid JSON (double quotes, no trailing commas, no comments). If one file is too long for a single reply, I will say "continue" and you continue from the same point; never summarise, never write "etc.", never leave a placeholder.

### (A) `athlete-catalog.json`
```json
{
  "schema": "smartdiet.catalog.v1",
  "ingredients": [
    {
      "id": "ing_chicken_breast_raw",
      "name": "Chicken, breast, boneless, skinless, raw",
      "usda_hint": "Chicken, broilers or fryers, breast, skinless, boneless, meat only, raw",
      "state": "raw",
      "per100g": { "kcal": 120, "p": 22.5, "c": 0, "f": 2.6, "fiber": 0, "sugar": 0, "sodium_mg": 45 },
      "shop": { "aisle": "meat_seafood", "unit": "lb", "unit_g": 454, "pantry": false, "keeps_days": 2 }
    }
  ]
}
```
Ids are `ing_` + lowercase snake_case, unique.

### (B) `athlete-standard.json` and (C) `athlete-simple.json`
```json
{
  "schema": "smartdiet.plan.v1",
  "plan_id": "athlete",
  "diet_id": "standard",
  "name": "Standard Diet",
  "summary": "One or two sentences on how this diet eats.",
  "reference": { "kcal": 2500, "protein_g": 165, "carbs_g": 290, "fat_g": 78, "fiber_g": 35 },
  "meals": [
    {
      "id": "std_b01",
      "slot": "breakfast",
      "name": "Veggie Egg Scramble with Toast",
      "tags": ["high_protein", "quick"],
      "allergens": ["egg", "gluten"],
      "active_min": 10,
      "total_min": 12,
      "equipment": ["nonstick pan", "toaster"],
      "ingredients": [
        { "ing": "ing_eggs_whole_raw", "g": 200, "label": "4 large eggs", "flex": { "min": 0.75, "max": 1.25 } },
        { "ing": "ing_bread_whole_wheat", "g": 80, "label": "2 slices whole wheat bread", "flex": { "min": 0.5, "max": 1.5 } },
        { "ing": "ing_spinach_raw", "g": 60, "label": "2 big handfuls baby spinach", "flex": null }
      ],
      "steps": ["Whisk the eggs. Wilt the spinach in a hot nonstick pan, add the eggs, stir until set.", "Serve with the toast."],
      "leftover_ok": false,
      "macros": { "kcal": 0, "p": 0, "c": 0, "f": 0, "fiber": 0, "sugar": 0, "sodium_mg": 0 },
      "swaps": [
        { "label": "Dairy-free", "replace": "ing_greek_yogurt_plain", "with": "ing_soy_yogurt_plain", "g": 245 }
      ]
    }
  ],
  "week": [
    { "day": "mon", "type": "training", "meals": ["std_b01", "std_l01", "std_pre01", "std_post01", "std_d01"], "note": "Heavy lifting day: the carbs sit around the workout." }
  ],
  "prep": [
    { "when": "sun", "task": "Cook 3 cups of brown rice and bake 2 lb of chicken thighs.", "minutes": 45, "covers": ["std_l01", "std_d02"] }
  ],
  "shopping_notes": ["Buy the chicken for Monday to Wednesday fresh and freeze the rest."]
}
```
Field rules:
- `slot` is one of `breakfast`, `lunch`, `dinner`, `snack`, `pre_workout`, `post_workout`. `tags` are free lowercase words; use `"shake"` on any meal that uses protein powder, `"leftover"` on a meal designed to reuse another meal's batch, `"no_cook"` where nothing is heated.
- `allergens` use only: `egg`, `dairy`, `gluten`, `soy`, `tree_nut`, `peanut`, `fish`, `shellfish`, `sesame`.
- `ingredients[].g` is grams **of that ingredient in the catalog state**. `label` is the human line the user reads (US units plus the item, for example "1 cup cooked brown rice", "6 oz chicken breast"). The label and the grams must agree.
- `macros` are the **totals for that meal as written (flex = 1)**, computed by you from the catalog's `per100g` values: sum of (g / 100 × per100g) over the ingredients. Round kcal to the nearest whole number and grams to one decimal. Do not guess; compute. The validator recomputes and rejects anything off by more than 4 % (or 8 kcal).
- `swaps` (optional but wanted): for each lunch and dinner give a vegetarian and/or a dairy-free swap where it makes sense. A swap replaces one ingredient with another catalog ingredient at a gram amount that keeps the meal's protein within about 10 %.
- `steps` are short, imperative sentences, with the real temperatures, times and doneness cues ("Bake at 425°F for 20 to 25 minutes until 165°F inside"). Food safety matters: chicken and ground meat 165°F, pork 145°F, fish 145°F.
- `week` has exactly seven entries `mon` to `sun`, in order, each `meals` list in **eating order**. Every day has `breakfast`, `lunch`, `dinner` and at least one `snack` or a `pre_workout`/`post_workout` slot; training days have a pre or post workout meal (or both), rest days do not (see section 3). Meals may be reused on several days (and are, for leftovers). Every meal in `meals` must appear in at least one day, and every id in `week` must exist in `meals`.
- `prep` lists the weekly batch-cooking tasks (for `standard`: 1 to 3 tasks; for `simple`: 0 to 2 tasks, each under 30 minutes). `covers` lists the meal ids that use that batch.
- Meal ids: `std_` for the standard diet, `sim_` for the simple diet, then a slot hint and a number (`std_b01`, `std_l03`, `sim_pre02`). The two diets must not share meal ids.
- Roughly 5 to 7 distinct breakfasts, 6 to 9 lunches, 6 to 9 dinners and 5 to 8 snacks/pre/post meals per diet is the right size; a week reuses some of them. The simple diet should reuse more.

## 6. Self-check before you answer (do all of this silently, then fix anything that fails)

1. Every day's summed macros (add up the `macros` of that day's meals at flex = 1) are within ±5 % of 2,500 kcal and ±10 % of 165 / 290 / 78 g (P / C / F). Fiber at least 30 g each day.
2. Each meal's `macros` equal the sum of its ingredients from the catalog (recompute, do not estimate).
3. Min and max flex days: min flex gives a day at or below 2,125 kcal, max flex gives a day at or above 3,250 kcal.
4. Every `ing` id exists in the catalog; every catalog ingredient is used; ids are unique; JSON is valid.
5. Limits: standard has at most 14 ingredients and 40 active minutes per meal and at most 60 distinct ingredients across the week; simple has at most 8 ingredients, 15 active minutes and 6 steps per meal and at most 35 distinct ingredients across the week.
6. 4 training days and 3 rest days; rest days contain no pre/post-workout meals.
7. The food is real and appealing, the portions are believable (a 2,500 kcal day that fits on plates a person can actually eat), and the cooking steps are correct and safe.

## 7. Delivery

Reply with the three code blocks (A, B, C) and nothing else except a final one-line confirmation that you ran the self-check. If something in this brief is impossible (for example a limit that cannot be met together with a macro target), say exactly which rule and propose the smallest relaxation instead of silently breaking it.
