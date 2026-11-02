/* Recipes' "Log this recipe", logged through native Food's add-food sheet: web half.
   Checked, with fake NativeRecipes, NativeNutrition and NativeOverview plugins standing in for Android:
   - where native Food says it can open on a sheet (capabilities().sheetSeed) and its screen is on, the command log sets the
     recipe's portion view up, answers {ok, navigate:'meals'}, and Food's first frame carries the sheet: no page sheet, classic
     Meals never shows, Recipes closes only once Food has opened, and nothing is logged until the sheet's own Log;
   - that Log (sheetLog) is the add-food sheet's: the page decides the nutrition (one serving of the recipe), saves, answers ok
     only when saved, answers a repeat "Already recorded.", and gives the Undo;
   - Back closes the sheet and leaves native Food showing; a Food that does not open gives the page's own sheet (never an
     invisible one); leaving Meals first drops the sheet;
   - an older Food (no sheetSeed), Food's screen off, and a recipe the page does not know all behave as before.
   Run: NODE_PATH=$(npm root -g) node tests/native-recipes-log.test.js [built html] */
const { chromium } = require('playwright');
const page = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };

const FAKES = (seed) => `
  window.__calls=[];
  const mk=(name,caps,cap)=>{const ls={};return {
    __emit:(e,d)=>Promise.all((ls[e]||[]).map(f=>f(d))),
    addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
    open:async a=>{window.__calls.push([name,'open',a])},
    update:async a=>{window.__calls.push([name,'update',a])},
    close:async a=>{window.__calls.push([name,'close',a,{sheet:!!document.querySelector('#fv2Ov.show')}]);(ls.closed||[]).forEach(f=>f({session:a&&a.session}))},
    capabilities:async()=>Object.assign({liveWeb:true,version:1,authoritativeData:true},cap),
    handshake:async()=>({protocol:1,capabilities:caps})}};
  window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>['NativeRecipes','NativeNutrition','NativeOverview'].includes(n),getPlatform:()=>'android',
    Plugins:{NativeRecipes:mk('recipes',['recipes.projection.v1'],{}),NativeNutrition:mk('food',[],${seed ? '{sheetSeed:true}' : '{}'}),NativeOverview:mk('overview',[],{})},registerPlugin:()=>({}),convertFileSrc:u=>u};`;

async function start(b, seed) {
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') }); // lunch time, for the slot the add-food sheet picks
  await p.addInitScript(`localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'scholar'}));` + FAKES(seed));
  await p.goto('file://' + page); await p.waitForTimeout(3000);
  return p;
}

/* the helpers every scenario shares, in the page */
const HELPERS = `
  window.H = (() => {
    const wait = ms => new Promise(r => setTimeout(r, ms));
    const P = window.Capacitor.Plugins, calls = (n, k) => window.__calls.filter(c => c[0] === n && c[1] === k);
    const last = (n, k) => calls(n, k).pop(), at = (n, k) => window.__calls.lastIndexOf(last(n, k));
    const showing = n => !!last(n, 'open') && (!last(n, 'close') || at(n, 'open') > at(n, 'close'));
    const until = async (f, ms = 6000) => { for (let t = 0; t < ms && !f(); t += 50) await wait(50) };
    const latest = () => [...window.__calls].reverse().find(c => c[0] === 'food' && (c[1] === 'open' || c[1] === 'update'))[2].snapshot;
    const reply = () => calls('recipes', 'update').map(c => c[2].reply).filter(Boolean).pop();
    const session = () => last('food', 'open')[2].session;
    const act = async (command, more) => { const n = window.__calls.length; await P.NativeNutrition.__emit('action', Object.assign({ session: session(), command }, more)); await until(() => window.__calls.length > n, 1500); await wait(80); return latest() };
    const setup = async (food) => {
      lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { }
      const S = lmaLoad(); S.nativeOverview = false; S.nativeRecipes = true; S.nativeNutrition = !!food; lmaSave();
      navigate('recipes'); await until(() => showing('recipes'));
    };
    const log = id => P.NativeRecipes.__emit('command', { command: 'log', payload: { id }, requestId: 'l-' + id });
    return { wait, P, calls, last, at, showing, until, latest, reply, session, act, setup, log };
  })();`;

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });

  /* 1. Food on, and its screen says it can open on a sheet */
  {
    const p = await start(b, true); await p.evaluate(HELPERS);
    const r = await p.evaluate(async () => {
      const { until, showing, calls, last, at, latest, reply, act, setup, log, wait } = H, out = {};
      await setup(true);
      const n0 = state.foodLog.length, id = 'rcp_pesto_chicken';
      window.__calls.length = 0;
      await log(id); await until(() => showing('food') && !showing('recipes'));
      const first = last('food', 'open')[2].snapshot;
      out.reply = reply() && reply().ok === true && reply().navigate === 'meals';
      out.firstFrame = lmaView() === 'meals' && !document.querySelector('#fv2Ov.show') && first.sheet && first.sheet.view === 'portion' && first.sheet.back === true
        && first.sheet.slot === 'lunch' && first.sheet.portion.name === RECIPE_FOODS.find(f => f.id === id).n && first.sheet.portion.grams === 230 && first.sheet.portion.edit === null;
      out.order = at('food', 'open') < at('recipes', 'close') && calls('recipes', 'close').length === 1 && !last('recipes', 'close')[3].sheet;
      out.nothingYet = state.foodLog.length === n0;
      out.detail = window.__calls.map(c => c[0] + ' ' + c[1]);

      // the sheet's Log: the page decides the nutrition and saves; ok only when saved; a repeat is "Already recorded."
      const want = scaleFood(RECIPE_FOODS.find(f => f.id === id), 230);
      let s = await act('sheetLog', { op: 'rl1' });
      const e = state.foodLog[state.foodLog.length - 1];
      out.logged = state.foodLog.length === n0 + 1 && e.foodId === id && e.grams === 230 && e.slot === 'lunch' && e.date === mealsDayKey() && e.kcal === want.kcal && e.p === want.p
        && s.sheet === null && s.answer.op === 'rl1' && s.answer.ok === true && s.answer.changed === true && !!s.notice && s.notice.action === 'Undo';
      const noticeId = s.notice.id;
      out.persisted = JSON.parse(localStorage.getItem(STORE)).foodLog.some(x => x.id === e.id);
      s = await act('noticeAction', { op: 'rl2', id: noticeId });
      out.undo = state.foodLog.length === n0 && s.answer.op === 'rl2' && s.answer.ok === true;
      s = await act('sheetLog', { op: 'rl1' });
      out.repeat = state.foodLog.length === n0 && s.answer.ok === true && s.answer.changed === false && s.answer.message === 'Already recorded.';
      return out;
    });
    ok('Log this recipe, native Food drawing sheets: the command answers ok and navigates to Meals', r.reply, r);
    ok('Food\'s first frame carries the recipe\'s portion view (lunch, one serving), no page sheet over Meals', r.firstFrame, r);
    ok('Recipes closes only after native Food has opened, with no page sheet showing', r.order, r.detail);
    ok('nothing is logged until the sheet\'s Log', r.nothingYet);
    ok('the sheet\'s Log saves one serving with the page\'s nutrition for the page\'s day, and answers ok', r.logged, r);
    ok('the log is persisted', r.persisted);
    ok('the same Log again is answered "Already recorded." and not done twice', r.repeat, r);
    ok('the page\'s Undo (a notice action) takes the entry back, answered', r.undo, r);
    ok('no script errors', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* 2. a failed save is refused in the page's words and takes the entry back; Back closes the sheet and Food stays */
  {
    const p = await start(b, true); await p.evaluate(HELPERS);
    const r = await p.evaluate(async () => {
      const { until, showing, last, latest, act, setup, log } = H, out = {};
      await setup(true);
      const n0 = state.foodLog.length, id = 'rcp_pesto_chicken';
      await log(id); await until(() => showing('food') && !showing('recipes'));
      const real = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
      let s; try { s = await act('sheetLog', { op: 'rf1' }) } finally { Storage.prototype.setItem = real }
      out.failed = state.foodLog.length === n0 && s.answer.op === 'rf1' && s.answer.ok === false && s.answer.changed === false && s.answer.message === unsavedMessage() && s.sheet === null;
      return out;
    });
    ok('a save that fails is answered ok false with the page\'s words, the entry taken back (the sheet closes, as the add-food sheet does)', r.failed, r);
    await p.context().close();
  }
  {
    const p = await start(b, true); await p.evaluate(HELPERS);
    const r = await p.evaluate(async () => {
      const { until, showing, act, setup, log, last } = H, out = {};
      await setup(true);
      await log('rcp_pesto_chicken'); await until(() => showing('food') && !showing('recipes'));
      out.up = NativeFoodSheet.open();
      const s = await act('sheetBack');
      out.closed = s.sheet === null && !NativeFoodSheet.open() && showing('food') && lmaView() === 'meals' && !document.querySelector('#fv2Ov.show');
      return out;
    });
    ok('Back on the seeded sheet closes it and leaves native Food showing', r.up && r.closed, r);
    await p.context().close();
  }

  /* 3. a Food that does not open gives the page's own sheet, never an invisible one */
  {
    const p = await start(b, true); await p.evaluate(HELPERS);
    const r = await p.evaluate(async () => {
      const { until, showing, setup, log, wait, P } = H, out = {};
      await setup(true);
      P.NativeNutrition.open = async () => { throw new Error('the screen would not open') };
      const n0 = state.foodLog.length;
      await log('rcp_pesto_chicken'); await until(() => !!document.querySelector('#fv2Ov.show'), 4000); await wait(200);
      const sh = fv2S().sh;
      out.ownSheet = lmaView() === 'meals' && !!document.querySelector('#fv2Ov.show') && sh.view === 'portion' && sh.pick.id === 'rcp_pesto_chicken' && sh.grams === 230 && state.foodLog.length === n0;
      out.noHidden = NativeNutritionBeta.snapshot().sheet === null;
      return out;
    });
    ok('Food failing to open hands the recipe to the page\'s own add-food sheet (portion view, one serving)', r.ownSheet, r);
    ok('and no sheet is left set up behind the scenes', r.noHidden, r);
    await p.context().close();
  }

  /* 4. the page goes elsewhere before Food opens: the sheet is dropped */
  {
    const p = await start(b, true); await p.evaluate(HELPERS);
    const r = await p.evaluate(async () => {
      const { until, showing, setup, wait, P } = H, out = {};
      await setup(true);
      const ok1 = await NativeNutritionBeta.seedPortion(RECIPE_FOODS.find(f => f.id === 'rcp_pesto_chicken'));
      out.seeded = ok1 === true && NativeNutritionBeta.snapshot().sheet !== null;
      navigate('dashboard'); await wait(300);
      out.dropped = NativeNutritionBeta.snapshot().sheet === null && !document.querySelector('#fv2Ov.show');
      return out;
    });
    ok('a sheet set up for Food is dropped when the page leaves Meals first', r.seeded && r.dropped, r);
    await p.context().close();
  }

  /* 5. before: an older Food (no sheetSeed), Food off, an unknown recipe */
  for (const [label, seed, food] of [['an older native Food (no sheetSeed)', false, true], ['native Food off', true, false]]) {
    const p = await start(b, seed); await p.evaluate(HELPERS);
    const r = await p.evaluate(async ({ food }) => {
      const { until, showing, setup, log, wait, reply, last } = H, out = {};
      await setup(food);
      const n0 = state.foodLog.length;
      await log('rcp_pesto_chicken'); await until(() => !showing('recipes'), 4000); await wait(300);
      const sh = fv2S().sh;
      out.old = !showing('recipes') && lmaView() === 'meals' && !!document.querySelector('#fv2Ov.show') && sh.view === 'portion' && sh.pick.id === 'rcp_pesto_chicken' && sh.grams === 230
        && state.foodLog.length === n0 && (!reply() || reply().navigate === undefined);
      return out;
    }, { food });
    ok('Log this recipe with ' + label + ' keeps handing over: the page\'s sheet opens over Meals and the screen closes', r.old, r);
    await p.context().close();
  }
  {
    const p = await start(b, true); await p.evaluate(HELPERS);
    const r = await p.evaluate(async () => {
      const { until, showing, setup, log, wait, reply, calls } = H, out = {};
      await setup(true);
      const closes = calls('recipes', 'close').length, n0 = state.foodLog.length;
      await log('nope'); await wait(800);
      out.unknown = lmaView() === 'recipes' && showing('recipes') && calls('recipes', 'close').length === closes && reply().ok === false && reply().navigate === undefined
        && !document.querySelector('#fv2Ov.show') && state.foodLog.length === n0 && NativeNutritionBeta.snapshot().sheet === null;
      return out;
    });
    ok('a recipe the page does not know does nothing: the screen stays, no sheet is set up', r.unknown, r);
    await p.context().close();
  }

  await b.close();
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); process.exit(fail ? 1 : 0);
})();
