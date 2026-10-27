# Smart Diet, Athlete: fix list for batch 2

Big improvement: the variety targets are met in both diets, sodium is under control, the vegetarian swaps now hold their protein, the catalog shipping fixes are right, and every leftover lunch lines up with the dinner it comes from. What is left is a short list of arithmetic and scheduling errors. I recomputed every meal from your catalog and checked every day. Please fix **exactly these** and send back the **three complete files again**. Keep everything not listed here identical (ids, ingredients, grams, text).

## 1. Macro totals that do not match the ingredients

The `macros` of a meal must equal the sum over its ingredients (grams / 100 x the catalog value). These are off by more than the allowed 4 %:

| meal | field | you wrote | the ingredients add up to |
|---|---|---|---|
| `std_b02` | carbs | 49.5 | 53.1 |
| `std_l01` | fat | 13.6 | 15.6 |
| `std_d07` | protein | 44.0 | 48.0 |
| `sim_l02` and `sim_d01` | carbs | 76.8 | 81.9 |
| `sim_l06` and `sim_d05` | fat | 15.3 | 17.1 |
| `sim_d07` | fat | 18.6 | 20.1 |

Either correct the `macros` (and then re-check the day totals in section 3) or adjust the ingredients. Please recompute every meal that you change; do not estimate.

## 2. Week structure errors

**Standard diet.** A rest day contains pre/post-workout meals and a training day has none:
- `thu` is typed `rest` but contains `std_pre01` and `std_post02`.
- `fri` is typed `training` but has only snacks (`std_s02`, `std_s03`).

Fix: make **Thursday a training day** and **Friday a rest day**. The result must be `mon`, `tue`, `thu`, `sat` = training and `wed`, `fri`, `sun` = rest. Training days have a `pre_workout` and/or `post_workout`; rest days have none and use a snack instead.

**Simple diet.** Currently 3 training and 4 rest days. `thu` is typed `rest` but contains `sim_pre02` and `sim_post02`. Fix: make **Thursday a training day** (so training = `mon`, `tue`, `thu`, `sat`; rest = `wed`, `fri`, `sun`).

The `note` of a day must be a clean, short label such as "Training A" or "Rest B". The Thursday note in the simple diet currently contains your working notes ("Wait, treated as Rest, Pre/Post dropped by app but written here? ..."). Remove that. In the standard diet some notes contradict the type (a rest day labelled "Training C", a training day labelled "Rest B"); make every note match its day's type.

## 3. Day totals outside the targets

Every day must be within 5 % of 2,500 kcal and within 10 % of protein 165 g, carbs 290 g, fat 78 g (so fat 70 to 86 g, protein 148 to 182 g, carbs 261 to 319 g). After the macro corrections above and the week changes, these days failed when I summed them (using your macros as written):

**Standard diet**
- `thu`: 2,627 kcal and fat 109 g (too much fat). Lower the fat on that day (for example swap a higher-fat snack or reduce oil and almonds, lean on carbs and protein).
- `sat`: fat 65 g (too little). Raise fat a little (olive oil, avocado or nuts in the meals that already have flex).

**Simple diet**
- `tue`: fat 93 g (too high).
- `wed`: protein 182 g (too high), `fri`: protein 186 g (too high). Both are over by a few grams.
- `sun`: fat 89 g (too high).

Rebalance by changing portions or swapping which meals are used on those days; do not break the leftover pairings (a leftover lunch must still sit the day after its dinner). Recheck **every** day of both diets after your change, including the flex bounds (minimum-flex day at or under 2,125 kcal, maximum-flex day at or over 3,250 kcal), sodium at or under 3,200 mg and fiber at least 30 g.

## 4. Dairy-free protein swap

`std_b01` dairy-free swap replaces 150 g Greek yogurt (15 g protein) with 150 g soy yogurt (5 g protein). A swap must keep protein within about 10 % of the original. Soy yogurt is too low in protein for this. Add a **dairy-free protein ingredient** to the catalog (for example plant-based protein powder from pea or soy, or a high-protein soy yogurt) and use it so the swap holds the protein. Also give the whey-based meals (`std_b03`, `std_s03`, `sim_b01`, `std_post01`, `sim_post02`) a dairy-free swap that keeps their protein with the same plant protein powder.

## 5. Unused catalog ingredients

`ing_turkey_deli_sliced_low_sodium` and `ing_shrimp_raw` are in the catalog but no meal or swap uses them. Use each in at least one meal (a shrimp dinner in the standard diet would be good, and a low-sodium deli turkey sandwich or wrap in the simple diet), or remove them. If you add meals, keep the minimum distinct-meal counts and the leftover pairings, and keep the 35-ingredient limit for the simple diet.

## 6. Before you answer

1. Recompute the `macros` of every meal you touch and every day total, using only the catalog values.
2. 4 training days and 3 rest days in both diets; training days have a pre or post workout meal, rest days have none; notes are clean.
3. Every day in both diets: kcal 2,500 +-5 %, protein/carbs/fat within 10 %, fiber at least 30 g, sodium at most 3,200 mg, minimum-flex day at or under 2,125 kcal, maximum-flex day at or over 3,250 kcal.
4. Every swap keeps protein within 10 %.
5. No unused catalog ingredient (a swap counts as a use), no missing one.

Reply with the three complete code blocks and a one-line confirmation.
