/* Native Food's Smart Diet swap sheet and Recipes' "Log this recipe", the foodswap job of tools/gen_native_fixtures.js: what the page
   projects (NativeNutritionBeta.snapshot()) at the fixed clock 2026-10-28T12:00Z, en-US, UTC, on the Plan pane of the standard diet
   at moderate fuel with a 2,500 kcal target:
     food-swap-open.json      the swap sheet open on the second meal (lunch), with the page's alternatives
     food-swap-reset.json     the same meal after one swap: the sheet open again, with "Back to the plan"
     food-swap-none.json      a meal with nothing else to offer ("No other options")
     food-swap-notice.json    the page's message after a swap: "Swapped to …" with Undo, the sheet closed
     food-swap-answer.json    a swap's answer {op, ok, changed, message}, and a refused one (the meal is not on offer)
     food-recipe-seed.json    a recipe's portion view opened before the screen (Recipes' "Log this recipe"), for dinner */
module.exports = async function foodswap({ b, out, write }) {
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') });
  // a Food plugin that only answers the handshake, for the recipe's seeded sheet (the screen itself is never opened here)
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));"
    + "const f={addListener:async()=>({remove(){}}),open:async()=>{},update:async()=>{},close:async()=>{},capabilities:async()=>({version:1,authoritativeData:true,sheetSeed:true})};"
    + "window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>n==='NativeNutrition',getPlatform:()=>'android',Plugins:{NativeNutrition:f},registerPlugin:()=>({}),convertFileSrc:u=>u};");
  await p.goto('file://' + out); await p.waitForTimeout(2500);
  const shots = await p.evaluate(async () => {
    const B = NativeNutritionBeta, wait = (ms) => new Promise((r) => setTimeout(r, ms));
    const snap = () => { const d = JSON.parse(JSON.stringify(B.snapshot())); d.shop = null; return d; };
    const res = {};
    mealsEnsure(); state.mealsMeta.calGoal = 2500; state.foodLog = [];
    const S = sdS(); S.diet = 'standard'; S.level = 'moderate'; S.veg = false; S.df = false; S.over = {};
    fv2S().pane = 'plan';
    // the screen is not open, so the swap sheet is driven through the same seam the add-food sheet is
    B.swapAct('swapOpen', { index: 1 }); res.open = snap();
    const first = res.open.swap.rows[0];
    B.swapAct('swapPick', { op: 'swap-1', index: 1, id: first.id }); res.notice = snap();
    B.swapAct('swapOpen', { index: 1 }); res.reset = snap();
    B.swapAct('swapPick', { op: 'swap-2', index: 1, id: 'no-such-meal' }); res.answer = snap(); // refused: not on offer
    B.swapAct('swapClose');
    const real = sdMealOk; sdMealOk = () => false;
    try { B.swapAct('swapOpen', { index: 1 }); res.none = snap(); B.swapAct('swapClose'); } finally { sdMealOk = real }
    S.over = {};
    // Recipes' "Log this recipe": the recipe's portion view, opened before the screen, for dinner
    const f = RECIPE_FOODS[0];
    lmaLoad().nativeNutrition = true;
    if (!await B.seedPortion(f)) throw Error('the recipe sheet was not set up');
    res.recipe = snap();
    B.sheetAct('sheetClose');
    return res;
  });
  write('food-swap-open.json', shots.open);
  write('food-swap-reset.json', shots.reset);
  write('food-swap-none.json', shots.none);
  write('food-swap-notice.json', shots.notice);
  write('food-swap-answer.json', shots.answer);
  write('food-recipe-seed.json', shots.recipe);
};
