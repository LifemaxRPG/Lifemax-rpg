# Smart Diet, Athlete: fix list for batch 1

Thank you. The structure, the schema and almost all of the nutrition maths are correct, and most days land within the macro and flex requirements. I ran the validator over all three files (catalog, standard, simple). It found real problems, listed below, and I also found some quality problems by reading the meals. Please fix **all** of them and send back the **three complete files again** (catalog, standard, simple), each in its own code block, same schema as before.

Rules for this round:
- **Keep every meal that is not mentioned below exactly as it is** (same id, ingredients, grams, macros, text), except where a fix below says it must change. Do not renumber ids. New meals take the next free number for their slot.
- Everything new must follow the same rules as in the original brief (macros computed from the catalog, flex ranges, limits for each diet, 4 training and 3 rest days, each day within ±5 % of 2,500 kcal and ±10 % of 165 / 290 / 78 g, fiber at least 30 g, and the minimum/maximum flex coverage).
- Every ingredient used must be in the catalog and every catalog ingredient must be used by at least one meal or swap.

## A. Variety (the biggest problem)

Both diets have far too few distinct meals. The brief asked for 5 to 7 breakfasts, 6 to 9 lunches, 6 to 9 dinners, and 5 to 8 snack / pre / post meals in total.

| | Standard now | Simple now | Needed |
|---|---|---|---|
| distinct breakfasts | 3 | 2 | 5 to 7 |
| distinct lunches | 3 | 2 | 6 to 9 |
| distinct dinners | 2 | 2 | 6 to 9 |

Add the missing meals and **rewrite the `week` so no two days in the week have the same dinner, and no lunch or breakfast appears more than twice** (leftover pairs such as cook-once-eat-twice are the only allowed exception, and they must be marked with the `leftover` tag on the second meal and covered by a `prep` entry).

Also vary the protein sources. Right now everything is chicken, beef, salmon, tuna, eggs and dairy. Add meals built on **turkey** (ground turkey and sliced), **tofu or tempeh**, **beans or lentils as the main protein**, **shrimp or white fish**, **pork tenderloin**, **cottage cheese**, and at least one **canned salmon** or **sardine** meal, spread over both diets (the simple diet can use the shortcut versions: canned fish, deli turkey, pre-cooked grains, frozen vegetables). Add any new ingredients to the catalog.

Remember the simple diet's limits: at most 8 ingredients and 15 active minutes per meal, and at most **35 different ingredients across the week**. The week currently uses far fewer than that, so you have room.

## B. Vegetarian swaps do not keep the protein

A swap must keep the meal's protein within about 10 % of the original (the validator rejects more than 20 %). These fail:

- `std_l01` Chicken, Rice and Broccoli: 180 g raw chicken breast is 40 g protein. 350 g cooked lentils is only 32 g. Use about 450 g lentils, or (better) swap to **extra-firm tofu** at the right grams.
- `sim_l01` Rotisserie Chicken Veggie Bowl: 130 g chicken is 32 g protein; 260 g black beans is only 16 g.
- `sim_d01` Beef and Black Bean Rice Bowl: 130 g beef is 26 g protein; 200 g black beans is only 12 g.

Beans are too low in protein to replace meat gram for gram. For all vegetarian swaps use a protein-dense vegetarian ingredient (extra-firm tofu, tempeh, or lentils at a realistic portion plus an extra egg or Greek yogurt where it makes sense) and recompute the grams. Also:
- Add a **dairy-free swap** wherever the meal has Greek yogurt, milk or whey, using `ing_soy_yogurt_plain` and a plant milk or soy milk (add it to the catalog). Right now `ing_soy_yogurt_plain` is in the catalog but no meal uses it, which the validator rejects.
- Give every lunch and dinner at least one swap.

## C. Sodium is too high on the simple diet

The brief set 2,000 to 3,200 mg a day (hard limit 3,400). Simple diet days are over:
- Tuesday 3,982 mg, Thursday 3,634 mg, Sunday 3,634 mg.

The causes: `sim_l02` Deli Turkey Sandwich is 1,644 mg in one meal (800 mg per 100 g for deli turkey is too salty; use a **low-sodium deli turkey** entry at roughly 450 to 500 mg per 100 g, or a different protein), `sim_d02` Rotisserie Chicken Pasta is 1,052 mg, and the standard diet's `std_l03` Tuna Salad Sandwich is 967 mg. Fix the ingredients or portions so that **every day of both diets is at or under 3,200 mg**, and still lands within the macro targets.

## D. Snacks that are really meals

`std_s01` (580 kcal), `std_s02` (620 kcal) and `sim_s02` (569 kcal) are meals, not snacks, which makes the days lopsided and unrealistic. Snacks should be roughly **150 to 400 kcal** (pre and post-workout meals up to about 450). Rebalance: make those snacks smaller and move the calories into the main meals (larger flexible carb and fat portions in lunches and dinners), keeping each day at 2,500 kcal ±5 %. Rest days still need a snack.

## E. Catalog corrections

- `ing_rice_white_cooked`: all meal weights are of **cooked** rice, but the purchase pack is a raw pound (`unit_g` 454). One pound of dry rice makes about **1,350 g cooked**. Set `unit` to "lb (dry)" and `unit_g` to the **cooked grams one pack makes** (about 1350), so the shopping list does not buy three times too much. Check the same for any other ingredient whose meals use a cooked weight but which is bought raw or dry (cooked pasta, lentils, beans, oats), and for `ing_chicken_rotisserie_cooked` (`unit_g` 900 is the whole bird; use the **edible cooked meat**, about 450 g).
- `ing_potato_sweet_raw`: not a pantry item. Set `pantry` to false.
- Every item with `pantry: true` must keep for more than 14 days.

## F. Smaller points

- `prep.covers` must list exactly the meals that use that batch. For example the Wednesday "cook extra chicken for Thursday and Friday" task covers `std_l01`, but Thursday and Friday use other meals. Fix the covers (or the task) so they match the week.
- Make sure each meal's `total_min` is at least its `active_min`.

## G. Before you answer

Run the same self-check as before and add these:
1. Distinct meals per slot meet the minimums in section A, in both diets.
2. No two days share a dinner, and no breakfast or lunch is used on more than two days, except for marked leftover pairs.
3. Every swap keeps protein within 10 % of the original ingredient's protein.
4. Every day of both diets: sodium at or under 3,200 mg, fiber at least 30 g, kcal 2,500 ±5 %, protein, carbs and fat within 10 %, minimum-flex day at or under 2,125 kcal, maximum-flex day at or over 3,250 kcal.
5. No unused catalog ingredient (a swap counts as a use), no missing one.

Reply with the three complete code blocks and a one-line confirmation.
