/* Food tab v2: rings, add-food sheet, portion/edit/create/goal flows.
   Run: NODE_PATH=$(npm root -g) node tests/food.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 300) : '')) } };
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 412, height: 880 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));");
  await p.goto('file://' + file); await p.waitForTimeout(900);
  await p.evaluate(() => { document.querySelectorAll('.lu-card').forEach(e => e.parentElement.remove()); navigate('meals') }); await p.waitForTimeout(400);
  ok('the Plan pane is the default when a plan exists', await p.evaluate(() => !!document.querySelector('.sd-next') && document.querySelector('.sd-panes button.on').dataset.v === 'plan' && [...document.querySelectorAll('.sd-panes button')].map(b => b.dataset.v).join() === 'plan,log,shop'));
  await p.click('[data-a=pane][data-v=log]'); await p.waitForTimeout(400);
  ok('the new Food tab renders the dial, week strip and four meals', await p.evaluate(() => !!document.querySelector('.fv2-svg') && document.querySelectorAll('.fv2-d').length === 7 && document.querySelectorAll('.fv2-meal').length === 4));
  ok('empty rings draw no stray dots', await p.evaluate(() => [...document.querySelectorAll('.fv2-arc')].every(a => getComputedStyle(a).opacity === '0')));
  await p.click('.fv2-mid'); await p.waitForTimeout(400); await p.click('[data-s2=preset][data-p=hi]'); await p.click('[data-s2=goalsave]'); await p.waitForTimeout(500);
  ok('targets save calories, protein, carbs, fat and water', await p.evaluate(() => { const m = state.mealsMeta; return m.calGoal === 2000 && m.protGoal > 0 && m.carbGoal > 0 && m.fatGoal > 0 && m.waterGoalMl === 2000 }));
  await p.click('#fv2Dock'); await p.waitForTimeout(400); await p.fill('#fv2Q', 'banana'); await p.waitForTimeout(400); await p.click('.fv2-r .q'); await p.waitForTimeout(500);
  ok('the + on a search row logs one default serving', await p.evaluate(() => dayFoodLog(getToday()).length === 1));
  await p.click('.fv2-meal .add[data-s=dinner]'); await p.waitForTimeout(400); await p.fill('#fv2Q', 'salmon'); await p.waitForTimeout(400); await p.click('.fv2-r .m'); await p.waitForTimeout(300);
  const g0 = await p.evaluate(() => sh0 = fv2S().sh.grams); await p.click('[data-s2=mult][data-x="2"]');
  ok('the 2x chip doubles the portion', await p.evaluate(g => fv2S().sh.grams >= g * 1.9, g0));
  await p.click('[data-s2=fav]'); await p.click('[data-s2=log]'); await p.waitForTimeout(500);
  ok('the portion screen logs into the chosen meal', await p.evaluate(() => dayFoodLog(getToday()).some(e => e.slot === 'dinner')));
  ok('starring a food keeps it in Favorites', await p.evaluate(() => state.mealsMeta.favs.length === 1));
  await p.click('.fv2-e'); await p.waitForTimeout(300); await p.click('[data-s2=step][data-d="1"]'); await p.click('[data-s2=save]'); await p.waitForTimeout(400);
  ok('editing an entry changes its grams and calories', await p.evaluate(() => { const e = dayFoodLog(getToday())[0]; return e && e.grams > 0 && e.kcal > 0 }));
  await p.click('#fv2Dock'); await p.waitForTimeout(300); await p.click('[data-s2=create]'); await p.waitForTimeout(300);
  await p.fill('#fv2CN', 'Protein shake'); await p.fill('#fv2CS', '300'); await p.fill('#fv2CK', '160'); await p.fill('#fv2CP', '30'); await p.click('[data-s2=createsave]'); await p.waitForTimeout(300);
  ok('a created food is stored per 100 g from its label serving', await p.evaluate(() => { const f = state.customFoods[0]; return f && Math.abs(f.kcal - 53.33) < .1 && f.port[0].g === 300 }));
  await p.click('[data-s2=log]'); await p.waitForTimeout(500);
  ok('and logs the full label calories for its serving', await p.evaluate(() => dayFoodLog(getToday()).some(e => e.name === 'Protein shake' && e.kcal === 160)));
  await p.click('[data-a=water][data-ml="500"]'); await p.waitForTimeout(200);
  ok('water adds to the water log', await p.evaluate(() => (state.waterLog || []).length === 1));
  await p.click('#fv2Dock'); await p.waitForTimeout(300); await p.click('[data-s2=quick]'); await p.waitForTimeout(200); await p.fill('#fv2QK', '450'); await p.click('[data-s2=quicksave]'); await p.waitForTimeout(400);
  ok('quick calories logs a plain kcal entry', await p.evaluate(() => dayFoodLog(getToday()).some(e => e.kcal === 450)));
  await p.evaluate(() => { const e = state.foodLog.find(x => x.name === 'Quick calories'); e.date = fv2DayShift(getToday(), -1); e.slot = ['breakfast','lunch','dinner','snacks'].find(s => !dayFoodLog(getToday()).some(x => x.slot === s)); save(); renderMeals() });
  await p.click('[data-a=step][data-n="-1"]'); await p.waitForTimeout(300); await p.click('[data-a=step][data-n="1"]'); await p.waitForTimeout(300);
  await p.click('[data-a=copy]'); await p.waitForTimeout(200);
  ok('Copy yesterday brings a meal forward', await p.evaluate(() => dayFoodLog(getToday()).filter(e => e.kcal === 450).length >= 1));
  await p.evaluate(() => { window.scrollTo(0, 0); document.querySelector('.fv2-svg').scrollIntoView({ block: 'center' }) }); await p.waitForTimeout(300);
  { const bx = await p.locator('.fv2-svg').boundingBox(), sc = bx.width / 320; await p.mouse.click(bx.x + bx.width / 2 + 101 * sc, bx.y + bx.height / 2); } await p.waitForTimeout(300);
  ok('tapping a ring puts that macro in the middle', await p.evaluate(() => { const m = document.querySelector('.fv2-mid.sel'); return !!m && /CARBS/.test(m.textContent) && /GRAMS/.test(m.textContent) && document.querySelectorAll('.fv2-ring.dim').length === 2 }));
  await p.click('.fv2-mid.sel'); await p.waitForTimeout(300);
  ok('tapping the middle goes back to calories', await p.evaluate(() => /KCAL/.test(document.querySelector('.fv2-mid').textContent) && !document.querySelector('.fv2-mid.sel')));
  // a Food write that cannot be stored is taken back and the snackbar says why, also while the save is read-only (save() is silent then)
  const unsaved = await p.evaluate(async () => {
    const real = Storage.prototype.setItem, said = () => document.querySelector('#fv2Snack span').textContent, wait = ms => new Promise(r => setTimeout(r, ms));
    const yesterday = state.foodLog.find(e => e.date === fv2DayShift(getToday(), -1)), test = { id: 'unsaved_food', n: 'Unsaved food', cat: 'Test', kcal: 100, p: 1, c: 1, f: 1, port: [] };
    const sheet = async action => { document.querySelector('.fv2-e').click(); await wait(400); if (action === 'save') document.querySelector('[data-s2=step][data-d="1"]').click(); document.querySelector('[data-s2=' + action + ']').click(); await wait(50); const open = !!document.querySelector('#fv2Ov.show'); fv2Close(); await wait(400); return open };
    const modes = {
      full: [() => { Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) } }, () => { Storage.prototype.setItem = real }],
      ro: [() => { saveReadOnly = 'A newer version saved this'; saveReadOnlyTold = true }, () => { saveReadOnly = ''; saveReadOnlyTold = false }],
    };
    // starring, creating a food and the daily targets: each sheet stays as it was (the star unchanged, the form still open)
    const star = async () => { fv2Open('lunch', null, test); await wait(400); const was = fv2IsFav(test); document.querySelector('[data-s2=fav]').click(); await wait(50); const kept = fv2IsFav(test) === was && document.querySelector('.fv2-star').classList.contains('on') === was; fv2Close(); await wait(400); return kept };
    const create = async () => { fv2Open('lunch'); await wait(400); fv2Go('create'); document.getElementById('fv2CN').value = 'Unsaved shake'; document.getElementById('fv2CK').value = '120'; document.querySelector('[data-s2=createsave]').click(); await wait(50); const kept = fv2S().sh.view === 'create' && !!document.querySelector('#fv2Ov.show') && document.getElementById('fv2CN').value === 'Unsaved shake'; fv2Close(); await wait(400); return kept };
    const goal = async () => { fv2Open(null, 'goal'); await wait(400); document.getElementById('fv2GC').value = '3100'; document.querySelector('[data-s2=goalsave]').click(); await wait(50); const kept = !!document.querySelector('#fv2Ov.show') && document.getElementById('fv2GC').value === '3100'; fv2Close(); await wait(400); return kept };
    const out = {};
    for (const [mode, [on, off]] of Object.entries(modes)) {
      const before = JSON.stringify([state.foodLog, state.waterLog, state.customFoods, state.mealsMeta]), stored = localStorage.getItem(STORE), r = {};
      on();
      try {
        r.log = [fv2Log(test, 100, 'lunch'), said()];
        fv2AddWater(250); r.water = said();
        fv2CopySlot(yesterday.slot); r.copy = said();
        r.edit = [await sheet('save'), said()];
        r.del = [await sheet('del'), said()];
        r.star = [await star(), said()];
        r.create = [await create(), said()];
        r.goal = [await goal(), said()];
      } finally { off() }
      const logged = fv2Log(test, 100, 'lunch');
      on(); try { document.querySelector('#fv2Snack button').click(); r.undo = [state.foodLog.includes(logged), said()] } finally { off() }
      deleteMealEntry(logged.id);
      out[mode] = Object.assign(r, { same: JSON.stringify([state.foodLog, state.waterLog, state.customFoods, state.mealsMeta]) === before, stored: localStorage.getItem(STORE) === stored });
    }
    return out;
  });
  for (const [mode, message] of [['full', 'Could not save to this browser (storage full?)'], ['ro', 'Not saved: this version is read-only for your save']]) {
    const r = unsaved[mode];
    ok('a ' + (mode === 'full' ? 'failed' : 'read-only') + ' save of a logged food, water, a copied meal, an edit, a removal, a star, a created food, the targets or an Undo is taken back and the snackbar says why',
      r.log[0] === null && [r.log[1], r.water, r.copy, r.edit[1], r.del[1], r.star[1], r.create[1], r.goal[1], r.undo[1]].every(m => m === message) && r.edit[0] && r.del[0] && r.star[0] && r.create[0] && r.goal[0] && r.undo[0] && r.same && r.stored, r);
  }
  ok('no script errors', errs.length === 0, errs);
  console.log(pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
