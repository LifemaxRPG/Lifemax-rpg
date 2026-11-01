/* Native Food's add-food sheet, phase 1c and phase 2, web half.
   Checked:
   - the page's own sheet (search, portion, edit, quick, create, daily targets, and the targets' live sum) is byte-identical to
     tests/fixtures/food-sheet-pages-golden.json, recorded before the sheet's words and rules were split out of its renderers
     (RECORD=1 rewrites it, only ever from code known to be right);
   - the projection's sheet says what the page's sheet draws: titles, Back or Close, the tabs, the search box's words, quick
     calories, the create form, the daily targets and an entry being edited;
   - phase 2's writes (create a food, save the targets, save / remove / log again an entry, Undo) do what the page's own buttons
     do, answer {op, ok, changed, message} with ok only when the save succeeded, answer a repeat of the same operation
     "Already recorded." without doing it twice, and refuse in the page's own words;
   - phase 1c: the slot's + and the Targets button open the native sheet, Back from the scrim steps back, a serving that is not
     offered is refused, a step outside the portion view does nothing.
   A fake NativeNutrition plugin stands in for Android. Run: NODE_PATH=$(npm root -g) node tests/native-food-forms.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
const GOLDEN = path.join(__dirname, 'fixtures', 'food-sheet-pages-golden.json');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };

const SEED = `localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));`;
const FAKES = `
  window.__calls=[];window.__slow=0;
  const mk=(name)=>{const ls={};return {
    __emit:(e,d)=>Promise.all((ls[e]||[]).map(f=>f(d))),
    addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
    open:async a=>{window.__calls.push([name,'open',a])},
    update:async a=>{if(window.__slow)await new Promise(r=>setTimeout(r,window.__slow));window.__calls.push([name,'update',a])},
    close:async a=>{window.__calls.push([name,'close',a]);(ls.closed||[]).forEach(f=>f({session:a&&a.session}))},
    capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true}),
    handshake:async()=>({protocol:1,capabilities:[]})}};
  window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>['NativeNutrition','NativeOverview'].includes(n),getPlatform:()=>'android',
    Plugins:{NativeNutrition:mk('food'),NativeOverview:mk('overview')},registerPlugin:()=>({}),convertFileSrc:u=>u};`;

async function start(b, fakes) {
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') });
  await p.addInitScript(SEED + (fakes ? FAKES : ''));
  await p.goto('file://' + file); await p.waitForTimeout(3000);
  return p;
}

/* every view of the page's own sheet, drawn by the page's own code from one fixed state */
async function pageViews(p) {
  return p.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), out = {};
    const html = () => document.querySelector('#fv2Ov .sheet').innerHTML;
    mealsEnsure();
    const chicken = fv2LocalFoods(fv2SearchTokens('chicken'))[0], rice = fv2LocalFoods(fv2SearchTokens('rice'))[0];
    navigate('meals'); await wait(200);
    fv2Open('lunch', 'search'); await wait(400); out.searchEmpty = html();
    fv2Open('dinner', 'goal'); await wait(50); out.goalUnset = html();
    for (const pre of ['bal', 'hi', 'low']) { fv2Preset(pre); out['goalPreset_' + pre] = document.getElementById('fv2Gs').innerHTML }
    document.getElementById('fv2GC').value = '1500'; fv2GoalLive(); out.goalOver = document.getElementById('fv2Gs').innerHTML;
    document.getElementById('fv2GC').value = '3000'; fv2GoalLive(); out.goalUnder = document.getElementById('fv2Gs').innerHTML;
    document.getElementById('fv2GC').value = '0'; fv2GoalLive(); out.goalNoCal = document.getElementById('fv2Gs').innerHTML;
    state.mealsMeta.calGoal = 2200; state.mealsMeta.protGoal = 160; state.mealsMeta.carbGoal = 220; state.mealsMeta.fatGoal = 70; state.mealsMeta.waterGoalMl = 2500;
    fv2Open(null, 'goal'); await wait(50); out.goalSet = html();
    fv2Open('lunch', 'portion', chicken); await wait(50); out.portion = html();
    fv2Open('snacks', 'portion', rice); await wait(50); out.portionRice = html();
    fv2Open('lunch', 'search'); fv2Go('quick'); await wait(50); out.quick = html();
    fv2Open('breakfast', 'search'); fv2Go('create'); await wait(50); out.create = html();
    state.foodLog.push(Object.assign({ id: 'food_gold_1', date: getToday(), slot: 'lunch', foodId: chicken.id, name: chicken.n, grams: 150, at: '2026-10-28T11:00:00.000Z' }, scaleFood(chicken, 150)));
    state.foodLog.push(Object.assign({ id: 'food_gold_2', date: fv2DayShift(getToday(), -1), slot: 'dinner', foodId: rice.id, name: rice.n, grams: 200, at: '2026-10-27T18:00:00.000Z' }, scaleFood(rice, 200)));
    fv2OpenEntry('food_gold_1'); await wait(50); out.editToday = html();
    fv2OpenEntry('food_gold_2'); await wait(50); out.editYesterday = html();
    fv2Open('lunch', 'search'); await wait(400); out.searchRecent = html();
    const q = document.getElementById('fv2Q'); q.value = 'chicken'; q.dispatchEvent(new Event('input', { bubbles: true })); await wait(900); out.searchQuery = html();
    fv2Close();
    state.foodLog = state.foodLog.filter(e => e.id.indexOf('food_gold_') !== 0);
    state.mealsMeta.calGoal = 0; delete state.mealsMeta.protGoal; delete state.mealsMeta.carbGoal; delete state.mealsMeta.fatGoal; delete state.mealsMeta.waterGoalMl;
    return out;
  });
}

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });

  /* 1. the page's own sheet, against the golden */
  {
    const p = await start(b, false);
    const html = await pageViews(p);
    if (process.env.RECORD) { fs.writeFileSync(GOLDEN, JSON.stringify(html, null, 1) + '\n'); console.log('recorded ' + GOLDEN) }
    const golden = JSON.parse(fs.readFileSync(GOLDEN, 'utf8'));
    for (const k of Object.keys(golden)) ok('the page draws its sheet\'s ' + k + ' byte for byte as before', html[k] === golden[k], (html[k] || '').length + ' vs ' + golden[k].length);
    ok('the golden has every view', Object.keys(html).every(k => k in golden), Object.keys(html));
    ok('no script errors on the page\'s own sheet', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* 2. the native sheet's projection and writes, driven as the screen drives them */
  {
    const p = await start(b, true);
    const r = await p.evaluate(async () => {
      const wait = ms => new Promise(r => setTimeout(r, ms)), out = {}, F = window.Capacitor.Plugins.NativeNutrition;
      const calls = k => window.__calls.filter(c => c[0] === 'food' && c[1] === k);
      const until = async (f, ms = 5000) => { for (let t = 0; t < ms && !f(); t += 25) await wait(25) };
      const latest = () => [...window.__calls].reverse().find(c => c[0] === 'food' && (c[1] === 'open' || c[1] === 'update'))[2].snapshot;
      const session = () => calls('open').pop()[2].session;
      const act = async (command, more) => { const n = window.__calls.length; await F.__emit('action', Object.assign({ session: session(), command }, more)); await until(() => window.__calls.length > n, 1500); await wait(60); return latest() };
      const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
      const ans = (s, op, ok, changed, message) => !!s.answer && s.answer.op === op && s.answer.ok === ok && s.answer.changed === changed && (message === undefined || s.answer.message === message);
      lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { }
      const S = lmaLoad(); S.nativeOverview = false; S.nativeNutrition = true; lmaSave();
      navigate('meals'); await until(() => calls('open').length, 8000);
      const failing = async (f) => { const real = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) }; try { return await f() } finally { Storage.prototype.setItem = real } };
      out.forms = latest().sheetForms === true && latest().answer === null;

      // the search view's words are the page's
      let s = await act('sheetOpen', { slot: 'lunch', forms: true });
      const w = fv2SheetWords();
      out.searchWords = s.sheet.title === 'Add food' && s.sheet.back === false && same(s.sheet.tabs.map(t => [t.id, t.label]), fv2TabsData())
        && s.sheet.words.search === w.search && s.sheet.words.quick === w.quick && s.sheet.words.create === w.create && s.sheet.words.searching === w.searching
        && s.sheet.quick === null && s.sheet.create === null && s.sheet.goal === null;

      // quick calories: its words, a refusal in the page's words, a save, and the same save again
      s = await act('sheetQuick');
      out.quickWords = s.sheet.title === 'Quick calories' && s.sheet.back === true && same(s.sheet.quick, fv2QuickData('lunch'));
      const n0 = state.foodLog.length;
      s = await act('sheetQuickSave', { op: 'q1', kcal: '0', name: '' });
      out.quickRefused = ans(s, 'q1', false, false, 'Enter the calories') && s.sheet.view === 'quick' && state.foodLog.length === n0;
      s = await act('sheetQuickSave', { op: 'q2', kcal: '450', name: 'Bar' });
      out.quickSaved = ans(s, 'q2', true, true) && s.sheet === null && state.foodLog.length === n0 + 1;
      s = await act('sheetQuickSave', { op: 'q2', kcal: '450', name: 'Bar' });
      out.quickRepeat = ans(s, 'q2', true, false, 'Already recorded.') && state.foodLog.length === n0 + 1 && s.notice.text === 'Already recorded.';
      s = await act('sheetLog', { op: 'stale' });
      out.closedWrite = ans(s, 'stale', false, false) && state.foodLog.length === n0 + 1;

      // create food: the page's form as data, its refusal, the food made, the portion view after it, and Back to search
      s = await act('sheetOpen', { slot: 'dinner', forms: true });
      s = await act('sheetCreate');
      out.createView = s.sheet.view === 'create' && s.sheet.title === 'Create food' && s.sheet.back === true && same(s.sheet.create, fv2CreateData()) && calls('close').length === 0;
      const c0 = state.customFoods.length;
      s = await act('sheetCreateSave', { op: 'c1', name: '  ', brand: '', serving: '100', kcal: '', p: '', c: '', f: '' });
      out.createRefused = ans(s, 'c1', false, false, 'Give it a name') && s.sheet.view === 'create' && state.customFoods.length === c0;
      s = await act('sheetCreateSave', { op: 'c2', name: 'Shake', brand: 'Home', serving: '250', kcal: '300', p: '30', c: '20', f: '5' });
      const made = state.customFoods[0];
      out.created = ans(s, 'c2', true, true) && state.customFoods.length === c0 + 1 && made.n === 'Shake' && made.brand === 'Home' && made.kcal === 120 && made.p === 12
        && s.sheet.view === 'portion' && s.sheet.portion.name === 'Shake' && s.sheet.portion.grams === 250 && s.sheet.portion.edit === null && s.sheet.slot === 'dinner';
      s = await act('sheetCreateSave', { op: 'c2', name: 'Shake', brand: 'Home', serving: '250', kcal: '300', p: '30', c: '20', f: '5' });
      out.createRepeat = ans(s, 'c2', true, false, 'Already recorded.') && state.customFoods.length === c0 + 1;
      s = await act('sheetFav', { op: 'f1' });
      out.favAnswer = ans(s, 'f1', true, true) && s.sheet.portion.fav === true;
      s = await act('sheetServing', { grams: 37 });
      out.servingRefused = s.sheet.portion.grams === 250;
      s = await act('sheetBack');
      out.createBack = s.sheet.view === 'search';
      s = await act('sheetStep', { dir: 1 });
      out.stepOutsidePortion = s.sheet.view === 'search' && s.sheet.portion === null;
      s = await act('sheetClose');

      // the daily targets: the page's boxes, a preset, a typed box and its line, a save that fails, a save, and the same again
      s = await act('sheetGoal');
      out.goalView = s.sheet.view === 'goal' && s.sheet.title === 'Daily targets' && s.sheet.back === false && same(s.sheet.goal, fv2GoalView(fv2GoalDraft()));
      s = await act('sheetGoalPreset', { preset: 'hi' });
      const hi = fv2PresetGrams('hi', '2000');
      out.goalPreset = s.sheet.goal.fields.map(f => f.value).join() === [hi.p, hi.c, hi.f].join() && same(s.sheet.goal.sum, fv2GoalSum('2000', hi.p, hi.c, hi.f));
      s = await act('sheetGoalField', { key: 'cal', value: '1500' });
      out.goalTyped = s.sheet.goal.cal.value === '1500' && s.sheet.goal.sum.bad === true && s.sheet.goal.sum.note === fv2GoalSum('1500', hi.p, hi.c, hi.f).note;
      s = await act('sheetGoalField', { key: 'calGoal', value: '9' });
      out.goalBadKey = s.sheet.goal.cal.value === '1500';
      const m0 = JSON.stringify(state.mealsMeta);
      s = await failing(() => act('sheetGoalSave', { op: 'g0', cal: '2200', p: '160', c: '220', f: '70', water: '2500' }));
      out.goalFailed = ans(s, 'g0', false, false, unsavedMessage()) && s.sheet.view === 'goal' && JSON.stringify(state.mealsMeta) === m0;
      s = await act('sheetGoalSave', { op: 'g1', cal: '2200', p: '160', c: '220', f: '70', water: '2500' });
      out.goalSaved = ans(s, 'g1', true, true) && s.sheet === null && state.mealsMeta.calGoal === 2200 && state.mealsMeta.protGoal === 160 && state.mealsMeta.waterGoalMl === 2500 && s.goals.cal === 2200;
      s = await act('sheetGoalSave', { op: 'g1', cal: '2200', p: '160', c: '220', f: '70', water: '2500' });
      out.goalRepeat = ans(s, 'g1', true, false, 'Already recorded.');

      // an entry: the edit view as the page's, a step, Save, Remove with Undo (once), and Log today
      const rice = fv2LocalFoods(fv2SearchTokens('rice'))[0];
      state.foodLog.push(Object.assign({ id: 'e_today', date: getToday(), slot: 'lunch', foodId: rice.id, name: rice.n, grams: 200, at: '2026-10-28T11:00:00.000Z' }, scaleFood(rice, 200)));
      state.foodLog.push(Object.assign({ id: 'e_yest', date: fv2DayShift(getToday(), -1), slot: 'dinner', foodId: rice.id, name: rice.n, grams: 150, at: '2026-10-27T18:00:00.000Z' }, scaleFood(rice, 150)));
      save();
      s = await act('sheetEntry', { id: 'nope' });
      out.entryUnknown = s.sheet === null;
      s = await act('sheetEntry', { id: 'e_yest' });
      const ey = state.foodLog.find(e => e.id === 'e_yest'), fy = fv2FoodOf(ey);
      out.editView = s.sheet.view === 'edit' && s.sheet.title === 'Edit entry' && s.sheet.back === false && s.sheet.slot === 'dinner'
        && same(s.sheet.portion.edit, { id: 'e_yest', remove: 'Remove', again: 'Log today' }) && s.sheet.portion.grams === 150
        && s.sheet.portion.logLabel === fv2LogLabel(scaleFood(fy, 150), true) && same(s.sheet.portion.impact, fv2ImpactData(fy, 150, ey));
      s = await act('sheetEntry', { id: 'e_today' });
      out.editToday = s.sheet.portion.edit.again === null;
      s = await act('sheetStep', { dir: 1 });
      const g1 = fv2StepGrams(200, 1);
      s = await act('sheetEditSave', { op: 's0', id: 'e_yest' });
      out.editWrongEntry = ans(s, 's0', false, false) && s.sheet.view === 'edit';
      s = await failing(() => act('sheetEditSave', { op: 's1', id: 'e_today' }));
      out.editFailed = ans(s, 's1', false, false, unsavedMessage()) && state.foodLog.find(e => e.id === 'e_today').grams === 200 && s.sheet.view === 'edit';
      s = await act('sheetEditSave', { op: 's2', id: 'e_today' });
      out.editSaved = ans(s, 's2', true, true) && state.foodLog.find(e => e.id === 'e_today').grams === g1 && s.sheet === null;
      s = await act('sheetEntry', { id: 'e_today' });
      const n1 = state.foodLog.length;
      s = await act('sheetEditDelete', { op: 'd1', id: 'e_today' });
      out.removed = ans(s, 'd1', true, true) && state.foodLog.length === n1 - 1 && s.sheet === null && s.notice.action === 'Undo' && s.notice.text === 'Removed ' + rice.n && s.answer.message === s.notice.text;
      s = await act('noticeAction', { op: 'u1', id: s.notice.id });
      out.undone = ans(s, 'u1', true, true) && state.foodLog.length === n1 && state.foodLog.some(e => e.id === 'e_today');
      s = await act('noticeAction', { op: 'u1', id: s.notice.id });
      out.undoRepeat = ans(s, 'u1', true, false, 'Already recorded.') && state.foodLog.length === n1;
      s = await act('sheetEntry', { id: 'e_yest' });
      setMealsDay(fv2DayShift(getToday(), -1));
      s = await act('sheetEditAgain', { op: 'a1', id: 'e_yest' });
      const again = state.foodLog[state.foodLog.length - 1];
      out.again = ans(s, 'a1', true, true) && state.foodLog.length === n1 + 1 && again.date === getToday() && again.grams === 150 && s.notice.text === 'Logged again today' && s.day === getToday() && s.sheet === null;

      // Back from the edit view closes it, as the page's head button does; the screen was never closed by any of this
      s = await act('sheetEntry', { id: 'e_yest' });
      s = await act('sheetBack');
      out.editBack = s.sheet === null;
      out.neverClosed = calls('close').length === 0 && !document.querySelector('#fv2Ov.show');
      // a write from an older screen, without an operation id, still works
      s = await act('sheetOpen', { slot: 'lunch' });
      s = await act('sheetQuick');
      const n2 = state.foodLog.length;
      s = await act('sheetQuickSave', { kcal: 120, name: '' });
      out.noOp = state.foodLog.length === n2 + 1 && s.answer.op === '' && s.answer.ok === true;
      return out;
    });
    const names = {
      forms: 'the projection says the page runs the forms itself, and has no answer yet',
      searchWords: 'the search view\'s title, button, tabs and words are the page\'s',
      quickWords: 'quick calories says the page\'s words', quickRefused: 'no calories: refused in the page\'s words, ok false',
      quickSaved: 'quick calories saved: ok, changed', quickRepeat: 'the same operation again: "Already recorded.", nothing logged twice',
      closedWrite: 'a write to a sheet that is not open answers ok false',
      createView: 'Create food opens in the native sheet with the page\'s form as data', createRefused: 'a food with no name is refused in the page\'s words',
      created: 'a food is made from the label values and its portion view opens at its serving', createRepeat: 'the same create again makes nothing',
      favAnswer: 'the star answers ok', servingRefused: 'a serving the food does not offer is refused', createBack: 'Back from the new food goes to search',
      stepOutsidePortion: 'a step with no food on show does nothing',
      goalView: 'the daily targets open in the native sheet as the page\'s boxes', goalPreset: 'a preset fills the page\'s grams and line',
      goalTyped: 'a typed box changes the line under the boxes', goalBadKey: 'a box that does not exist is ignored',
      goalFailed: 'targets that cannot be saved: ok false, "could not save", nothing kept', goalSaved: 'the targets are saved from the boxes on the screen',
      goalRepeat: 'the same save again: "Already recorded."',
      entryUnknown: 'an entry that is not there opens nothing', editView: 'an entry opens the edit view, as the page\'s: grams, label, impact, Remove, Log today',
      editToday: 'today\'s entry has no Log today', editWrongEntry: 'a save for another entry is refused',
      editFailed: 'an edit that cannot be saved changes nothing and stays open', editSaved: 'an edit is saved and the sheet closes',
      removed: 'Remove takes the entry away with the page\'s Undo', undone: 'Undo brings it back, ok', undoRepeat: 'the same Undo again does nothing twice',
      again: 'Log today logs the entry again today and shows today', editBack: 'Back from the edit view closes it',
      neverClosed: 'the screen was never closed and the page\'s own sheet never opened', noOp: 'a write without an operation id still works'
    };
    for (const k of Object.keys(names)) ok(names[k], r[k] === true, r[k]);
    ok('no script errors with the native sheet', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
