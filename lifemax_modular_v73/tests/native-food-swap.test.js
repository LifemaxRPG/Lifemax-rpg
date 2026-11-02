/* Native Food's Smart Diet swap sheet, web half.
   Checked:
   - the page's own swap sheet (sdOpenSwap) is byte-identical to tests/fixtures/food-swap-pages-golden.json, recorded before its
     alternatives and words were split out into sdSwapView (RECORD=1 rewrites it, only ever from code known to be right);
   - the projection's `swap` says what the page's sheet draws: the same words, the same alternatives in the same order;
   - swapOpen / swapPick / swapReset / swapClose act as the page's own buttons do, answer {op, ok, changed, message} with ok only
     once saved, answer a repeat "Already recorded.", refuse in the page's words, and give an Undo that the page answers;
   - Back closes the sheet; a screen that does not draw it still hands over (sdSwap), and the page's own sheet swaps as before.
   A fake NativeNutrition plugin stands in for Android. Run: NODE_PATH=$(npm root -g) node tests/native-food-swap.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
const GOLDEN = path.join(__dirname, 'fixtures', 'food-swap-pages-golden.json');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };

const SEED = `localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));`;
const FAKES = `
  window.__calls=[];
  const mk=(name)=>{const ls={};return {
    __emit:(e,d)=>Promise.all((ls[e]||[]).map(f=>f(d))),
    addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
    open:async a=>{window.__calls.push([name,'open',a])},
    update:async a=>{window.__calls.push([name,'update',a])},
    close:async a=>{window.__calls.push([name,'close',a]);(ls.closed||[]).forEach(f=>f({session:a&&a.session}))},
    capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true,sheet:true}),
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

/* the page's own swap sheet for several plans, drawn by the page's own code */
async function pageViews(p) {
  return p.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), out = {};
    const html = () => document.querySelector('#sdOv .sheet').innerHTML;
    mealsEnsure(); state.mealsMeta.calGoal = 2500;
    navigate('meals'); await wait(200);
    const S = sdS(); S.diet = 'standard'; S.level = 'moderate'; S.veg = false; S.df = false; S.over = {};
    const day = mealsDayKey();
    for (const i of [0, 1, 2, 3]) { sdOpenSwap(i); await wait(30); out['standard' + i] = html(); sdCloseSwap() }
    sdOpenSwap(1); const pick = document.querySelector('#sdOv [data-sw]:not([data-sw=x])').dataset.sw; sdCloseSwap();
    S.over[day] = { 1: pick }; sdOpenSwap(1); await wait(30); out.withOverride = html(); sdCloseSwap();
    S.over = {}; S.veg = true; S.df = true; sdOpenSwap(2); await wait(30); out.vegDf = html(); sdCloseSwap();
    S.veg = false; S.df = false; S.diet = 'simple'; sdOpenSwap(1); await wait(30); out.simple = html(); sdCloseSwap();
    S.diet = 'standard'; S.over = {};
    return out;
  });
}

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });

  /* 1. the page's own swap sheet, against the golden */
  {
    const p = await start(b, false);
    const html = await pageViews(p);
    if (process.env.RECORD) { fs.writeFileSync(GOLDEN, JSON.stringify(html, null, 1) + '\n'); console.log('recorded ' + GOLDEN) }
    const golden = JSON.parse(fs.readFileSync(GOLDEN, 'utf8'));
    for (const k of Object.keys(golden)) ok('the page draws its swap sheet\'s ' + k + ' byte for byte as before', html[k] === golden[k], (html[k] || '').length + ' vs ' + golden[k].length);
    ok('the golden has every view', Object.keys(html).every(k => k in golden), Object.keys(html));
    ok('the golden is not empty', Object.values(golden).every(v => v.length > 200 && v.indexOf('data-sw') > 0));
    ok('no script errors on the page\'s own swap sheet', p.errors.length === 0, p.errors);
    await p.context().close();
  }
  /* 2. the native swap sheet's projection and writes, driven as the screen drives them */
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
      const S0 = lmaLoad(); S0.nativeOverview = false; S0.nativeNutrition = true; lmaSave();
      mealsEnsure(); state.mealsMeta.calGoal = 2500; state.foodLog = [];
      const S = sdS(); S.diet = 'standard'; S.level = 'moderate'; S.veg = false; S.df = false; S.over = {}; fv2S().pane = 'plan'; save();
      navigate('meals'); await until(() => calls('open').length, 8000);
      const day = mealsDayKey();
      const failing = async (f) => { const real = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) }; try { return await f() } finally { Storage.prototype.setItem = real } };
      const pageIds = i => { sdOpenSwap(i); const ids = [...document.querySelectorAll('#sdOv [data-sw]')].map(x => x.dataset.sw).filter(x => x !== 'x' && x !== 'reset'); sdCloseSwap(); return ids };
      out.closed = latest().swap === null;

      // open: the page's words and alternatives, the same the page's own sheet offers, in its order
      let s = await act('swapOpen', { index: 1 });
      const v = sdSwapView(day, 1);
      out.view = same(s.swap, v) && s.swap.rows.length > 2 && s.swap.title === 'Swap lunch' && s.swap.close === 'Close' && s.swap.reset === null && s.swap.none === null;
      out.sameAsPage = same(s.swap.rows.map(x => x.id), pageIds(1));
      out.words = s.swap.lead === 'Instead of ' && s.swap.tail === '. The rest of the day rescales to keep your target.' && s.swap.name === sdDay(day).items[1].meal.name
        && s.swap.rows.every(x => x.unit === 'kcal' && /^\d+ min · P \d+ C \d+ F \d+/.test(x.detail) && /^[\d,]+$/.test(x.kcal));
      out.noAddSheet = s.sheet === null;
      out.back = NativeFoodSheet.open() === true;
      await act('swapClose'); out.closes = latest().swap === null && NativeFoodSheet.open() === false;
      await act('swapOpen', { index: 99 }); out.badIndex = latest().swap === null;
      await act('swapOpen', { index: -1 }); out.badIndex2 = latest().swap === null;
      await act('swapOpen', { index: 'x' }); out.badIndex3 = latest().swap === null;

      // a swap: saved, the sheet closes, ok only once saved, the page's snackbar with Undo
      s = await act('swapOpen', { index: 1 });
      const pick = s.swap.rows[1], cur = sdDay(day).items[1].meal.id;
      s = await act('swapPick', { op: 'sw1', index: 1, id: pick.id });
      out.swapped = ans(s, 'sw1', true, true, 'Swapped to ' + pick.name) && S.over[day][1] === pick.id && s.swap === null && sdDay(day).items[1].meal.id === pick.id
        && !!s.notice && s.notice.text === 'Swapped to ' + pick.name && s.notice.action === 'Undo';
      out.persisted = JSON.parse(localStorage.getItem(STORE)).mealsMeta.sd.over[day][1] === pick.id;
      out.dayHolds = Math.abs(sdDay(day).tot.kcal - sdDay(day).target) < 12;
      // the same operation again (an answer that was lost): not done twice
      s = await act('swapPick', { op: 'sw1', index: 1, id: pick.id });
      out.repeat = ans(s, 'sw1', true, false, 'Already recorded.') && S.over[day][1] === pick.id;
      // Undo is the page's: it puts the meal back
      s = await act('swapOpen', { index: 1 }); const pick2 = s.swap.rows[0];
      s = await act('swapPick', { op: 'sw2', index: 1, id: pick2.id });
      const undoId = s.notice.id;
      s = await act('noticeAction', { op: 'u1', id: undoId });
      out.undo = ans(s, 'u1', true, true) && S.over[day][1] === pick.id && JSON.parse(localStorage.getItem(STORE)).mealsMeta.sd.over[day][1] === pick.id;

      // Back to the plan: only while swapped; saved; with its own Undo
      s = await act('swapOpen', { index: 1 });
      out.resetShown = s.swap.reset === 'Back to the plan';
      s = await act('swapReset', { op: 'r1', index: 1 });
      out.reset = ans(s, 'r1', true, true, 'Back to the plan') && S.over[day][1] === undefined && s.swap === null && s.notice.action === 'Undo' && sdDay(day).items[1].meal.id === cur;
      s = await act('noticeAction', { op: 'u2', id: s.notice.id });
      out.resetUndo = ans(s, 'u2', true, true) && S.over[day][1] === pick.id;
      delete S.over[day][1]; save();
      s = await act('swapOpen', { index: 1 }); out.noResetWhenPlanned = s.swap.reset === null;
      s = await act('swapReset', { op: 'r2', index: 1 });
      out.resetRefused = ans(s, 'r2', false, false) && s.swap !== null && S.over[day][1] === undefined;

      // a meal the sheet does not offer (the plan changed under the tap) is refused, the sheet stays, nothing changes
      s = await act('swapPick', { op: 'bad1', index: 1, id: 'no-such-meal' });
      out.stale = ans(s, 'bad1', false, false) && s.swap !== null && S.over[day][1] === undefined;
      s = await act('swapPick', { op: 'bad2', index: 1, id: cur });
      out.sameMeal = ans(s, 'bad2', false, false) && S.over[day][1] === undefined;
      s = await act('swapPick', { op: 'bad3', index: 2, id: pick.id });
      out.wrongIndex = ans(s, 'bad3', false, false) && S.over[day][2] === undefined && S.over[day][1] === undefined;
      s = await act('swapPick', { op: 'bad4', index: 1 });
      out.noId = ans(s, 'bad4', false, false);

      // a failed save: taken back, ok false, the page's words, the sheet kept for a retry as a new operation
      s = await failing(() => act('swapPick', { op: 'f1', index: 1, id: pick.id }));
      out.failed = ans(s, 'f1', false, false, unsavedMessage()) && S.over[day][1] === undefined && s.swap !== null && sdDay(day).items[1].meal.id === cur;
      s = await act('swapPick', { op: 'f2', index: 1, id: pick.id });
      out.retry = ans(s, 'f2', true, true) && S.over[day][1] === pick.id && s.swap === null;
      delete S.over[day][1]; save();

      // a write with the sheet closed changes nothing
      s = await act('swapPick', { op: 'c1', index: 1, id: pick.id });
      out.closedWrite = ans(s, 'c1', false, false) && S.over[day][1] === undefined;

      // Android Back and the add-food sheet
      await act('swapOpen', { index: 1 }); NativeFoodSheet.back(); await wait(150);
      out.androidBack = latest().swap === null && !NativeFoodSheet.open();
      await act('swapOpen', { index: 1 });
      s = await act('sheetOpen', { slot: 'lunch', forms: true });
      out.addClosesSwap = s.swap === null && s.sheet !== null;
      await act('swapOpen', { index: 1 }); s = latest();
      out.noSwapOverAdd = s.swap === null && s.sheet !== null;
      await act('sheetClose');

      // a swap that carries no operation id (an older screen) is still done, and answered with an empty op
      await act('swapOpen', { index: 1 });
      s = await act('swapPick', { index: 1, id: pick.id });
      out.noOp = ans(s, '', true, true) && S.over[day][1] === pick.id;
      delete S.over[day][1]; save();
      return out;
    });
    const t = (n, k) => ok(n, r[k] === true, r);
    t('the swap field is null while no swap sheet is open', 'closed');
    t('open: the projection is the page\'s sdSwapView (words and alternatives), nothing else open', 'view');
    t('the alternatives are the ones, in the order, the page\'s own sheet offers', 'sameAsPage');
    t('the words are the page\'s: Swap lunch, Instead of …, the rescale note, kcal rows', 'words');
    t('the add-food sheet stays closed under it', 'noAddSheet');
    t('Android Back sees the sheet', 'back');
    t('swapClose closes it', 'closes');
    t('a meal the plan does not have is not opened (out of range, negative, not a number)', 'badIndex');
    t('negative index', 'badIndex2');
    t('index that is not a number', 'badIndex3');
    t('a swap is saved, answered ok with the page\'s words, closes the sheet, the day still hits its target and the snackbar offers Undo', 'swapped');
    t('the swap is persisted', 'persisted');
    t('the rest of the day rescales to the target', 'dayHolds');
    t('the same operation again is answered "Already recorded." and not done twice', 'repeat');
    t('Undo (the page\'s own) puts the earlier meal back, answered and saved', 'undo');
    t('Back to the plan is shown only while swapped', 'resetShown');
    t('Back to the plan restores the planned meal, answered, with Undo', 'reset');
    t('and that Undo swaps again', 'resetUndo');
    t('no Back to the plan on a meal as planned', 'noResetWhenPlanned');
    t('Back to the plan on a meal as planned is refused', 'resetRefused');
    t('a meal that is not on offer is refused and nothing changes', 'stale');
    t('the meal itself is not an alternative', 'sameMeal');
    t('a pick for another meal than the sheet\'s is refused', 'wrongIndex');
    t('a pick with no meal is refused', 'noId');
    t('a failed save is taken back, answered ok false in the page\'s words, the sheet kept', 'failed');
    t('and the retry (a new operation) saves', 'retry');
    t('a write with the sheet closed changes nothing', 'closedWrite');
    t('Android Back closes the sheet', 'androidBack');
    t('the add-food sheet closes the swap sheet when it opens', 'addClosesSwap');
    t('the swap sheet does not open over the add-food sheet', 'noSwapOverAdd');
    t('a swap from a screen that sends no operation id still works', 'noOp');
    ok('no script errors on the native swap sheet', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* 3. without the native sheet: the page's own sheet is as before, with a failed save taken back; a hand-over still works */
  {
    const p = await start(b, true);
    const r = await p.evaluate(async () => {
      const wait = ms => new Promise(r => setTimeout(r, ms)), out = {}, F = window.Capacitor.Plugins.NativeNutrition;
      const calls = k => window.__calls.filter(c => c[0] === 'food' && c[1] === k);
      const until = async (f, ms = 5000) => { for (let t = 0; t < ms && !f(); t += 25) await wait(25) };
      const session = () => calls('open').pop()[2].session;
      lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { }
      const S0 = lmaLoad(); S0.nativeOverview = false; S0.nativeNutrition = true; lmaSave();
      mealsEnsure(); state.mealsMeta.calGoal = 2500; const S = sdS(); S.over = {}; fv2S().pane = 'plan'; save();
      navigate('meals'); await until(() => calls('open').length, 8000);
      const day = mealsDayKey();
      // an older screen (no swap field) sends sdSwap: the screen closes and the page's own sheet opens
      await F.__emit('action', { session: session(), command: 'sdSwap', index: 1 }); await until(() => !!document.querySelector('#sdOv.show'), 3000);
      out.handsOver = !!document.querySelector('#sdOv.show') && lmaView() === 'meals' && calls('close').length === 1;
      // the page's own click: the swap is made and nothing else appears (no snackbar, as before)
      const snack = document.getElementById('fv2Snack'); if (snack) snack.classList.remove('show');
      const b = document.querySelector('#sdOv [data-sw]:not([data-sw=x])'); const id = b.dataset.sw; b.click(); await wait(100);
      out.pageClick = S.over[day][1] === id && !document.querySelector('#sdOv.show') && !(document.getElementById('fv2Snack') && document.getElementById('fv2Snack').classList.contains('show'));
      // and Back to the plan from it
      sdOpenSwap(1); document.querySelector('#sdOv [data-sw=reset]').click(); await wait(100);
      out.pageReset = S.over[day][1] === undefined;
      // a save that fails is taken back on the page's own sheet too (it used to be kept in memory only)
      sdOpenSwap(1); const id2 = document.querySelector('#sdOv [data-sw]:not([data-sw=x])').dataset.sw;
      const real = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
      try { document.querySelector('#sdOv [data-sw="' + id2 + '"]').click(); await wait(100) } finally { Storage.prototype.setItem = real }
      out.pageFailed = S.over[day][1] === undefined;
      return out;
    });
    ok('an older screen\'s Swap (sdSwap) still hands over to the page\'s own sheet', r.handsOver, r);
    ok('the page\'s own sheet swaps as before, without a snackbar', r.pageClick, r);
    ok('and Back to the plan works there', r.pageReset, r);
    ok('and a swap that cannot be saved is taken back there too', r.pageFailed, r);
    ok('no script errors on the hand-over', p.errors.length === 0, p.errors);
    await p.context().close();
  }
  await b.close();
  console.log(`\n${pass} passed, ${fail} failed`); process.exit(fail ? 1 : 0);
})();
