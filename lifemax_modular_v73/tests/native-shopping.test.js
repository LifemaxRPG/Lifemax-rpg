/* Native Food's Walmart shopping (food.shopping.v1), web half.
   Checked, with a fake NativeNutrition plugin standing in for Android:
   - a screen that advertises food.shopping.v1 gets a `shopping` projection from the page's own Smart Diet list and recipes (the
     same rows, grams and pack sizes the classic Shopping pane works from), and a screen that does not gets none: the classic pane
     and the `shop` projection are as before;
   - shopPrefSet / shopPrefClear are strict and repeat-safe: ok only once stored, "Already recorded." for a repeated operation,
     ok with changed:false for a value already held, refused (nothing stored) for a bad key, a product id that is not 5 to 15
     digits, over-long or non-text notes, or a 201st match; a failed save is taken back and answered not ok, a retry saves;
   - the saved field (state.mealsMeta.walmart) is read defensively (malformed entries are not projected) and travels in backups.
   Run: NODE_PATH=$(npm root -g) node tests/native-shopping.test.js [built html] */
const { chromium } = require('playwright');
const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };

const SEED = `localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));`;
const fakes = (shopping) => `
  window.__calls=[];
  const mk=(name,caps)=>{const ls={};return {
    __emit:(e,d)=>Promise.all((ls[e]||[]).map(f=>f(d))),
    addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
    open:async a=>{window.__calls.push([name,'open',a])},
    update:async a=>{window.__calls.push([name,'update',a])},
    close:async a=>{window.__calls.push([name,'close',a]);(ls.closed||[]).forEach(f=>f({session:a&&a.session}))},
    capabilities:async()=>Object.assign({liveWeb:true,version:1,authoritativeData:true,sheetSeed:true},caps),
    handshake:async()=>({protocol:1,capabilities:[]})}};
  window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>['NativeNutrition','NativeOverview'].includes(n),getPlatform:()=>'android',
    Plugins:{NativeNutrition:mk('food',${shopping ? "{shopping:'food.shopping.v1'}" : '{}'}),NativeOverview:mk('overview',{})},registerPlugin:()=>({}),convertFileSrc:u=>u};`;

async function start(b, shopping) {
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') });
  await p.addInitScript(SEED + fakes(shopping));
  await p.goto('file://' + file); await p.waitForTimeout(3000);
  return p;
}

const HELPERS = `
  window.H = (() => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), F = window.Capacitor.Plugins.NativeNutrition;
    const calls = k => window.__calls.filter(c => c[0] === 'food' && c[1] === k);
    const until = async (f, ms = 5000) => { for (let t = 0; t < ms && !f(); t += 25) await wait(25) };
    const latest = () => [...window.__calls].reverse().find(c => c[0] === 'food' && (c[1] === 'open' || c[1] === 'update'))[2].snapshot;
    const session = () => calls('open').pop()[2].session;
    const act = async (command, more) => { const n = window.__calls.length; await F.__emit('action', Object.assign({ session: session(), command }, more)); await until(() => window.__calls.length > n, 1500); await wait(60); return latest() };
    const stored = () => (JSON.parse(localStorage.getItem(STORE)).mealsMeta || {}).walmart;
    const failing = async (f) => { const real = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) }; try { return await f() } finally { Storage.prototype.setItem = real } };
    const setup = async () => {
      lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { }
      const S0 = lmaLoad(); S0.nativeOverview = false; S0.nativeNutrition = true; lmaSave();
      mealsEnsure(); state.mealsMeta.calGoal = 2500; fv2S().pane = 'shop'; save();
      navigate('meals'); await until(() => calls('open').length, 8000);
    };
    const ans = (s, op, ok, changed) => !!s.shopping && !!s.shopping.answer && s.shopping.answer.op === op && s.shopping.answer.ok === ok && s.shopping.answer.changed === changed;
    return { wait, calls, until, latest, session, act, stored, failing, setup, ans };
  })();`;

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });

  /* 1. a screen that advertises food.shopping.v1 */
  {
    const p = await start(b, true);
    await p.evaluate(HELPERS);
    const r = await p.evaluate(async () => {
      const { act, latest, stored, failing, setup, ans, wait } = H, out = {};
      await setup();
      let s = latest(), sh = s.shopping;
      out.shape = !!sh && sh.version === 1 && typeof sh.session === 'string' && sh.session.length > 0 && sh.revision >= 1 && sh.weekStart === s.shop.weekStart && sh.nextWeek === s.shop.nextWeek
        && Array.isArray(sh.items) && Array.isArray(sh.recipes) && Array.isArray(sh.prefs) && sh.prefs.length === 0 && sh.answer === null;
      out.items = sh.items.length > 10 && sh.items.length === s.shop.items.length
        && sh.items.every((x, i) => x.id === s.shop.items[i].id && x.name === s.shop.items[i].name && x.grams === s.shop.items[i].grams && x.home === s.shop.items[i].home && x.unit === s.shop.items[i].unit && x.packGrams > 0)
        && sdShopList(s.shop.weekStart).every((x, i) => x.pg === sh.items[i].packGrams);
      out.recipes = sh.recipes.length === recipeHits('', 'all').length && sh.recipes.length > 3
        && sh.recipes.every(r => r.id && r.name && r.servings >= 1 && r.servings <= 100 && r.lines.length > 0 && r.lines.every(l => typeof l === 'string' && l.length <= 200));
      out.sizeOk = JSON.stringify(s).length < 245000;
      out.classicKept = s.shop && s.shop.items.length === sh.items.length && typeof sdShopHtml() === 'string';
      const rev1 = sh.revision; await act('pane', { pane: 'shop' });
      out.revisionRises = latest().shopping.revision > rev1;

      const key = 'sd:' + sh.items[0].id;
      // a match is stored, answered ok, projected back and persisted
      s = await act('shopPrefSet', { op: 'p1', key, productId: '145051970', label: 'Quaker', note: '42 oz' });
      out.saved = ans(s, 'p1', true, true) && JSON.stringify(s.shopping.prefs) === JSON.stringify([{ key, productId: '145051970', label: 'Quaker', note: '42 oz' }])
        && H.stored().prefs[key].id === '145051970' && H.stored().prefs[key].label === 'Quaker';
      // the same operation again: not done twice
      const before = JSON.stringify(H.stored());
      s = await act('shopPrefSet', { op: 'p1', key, productId: '999999999', label: 'Other' });
      out.repeat = ans(s, 'p1', true, false) && s.shopping.answer.message === 'Already recorded.' && JSON.stringify(H.stored()) === before;
      // the same value as a new operation: ok, nothing changed
      s = await act('shopPrefSet', { op: 'p2', key, productId: '145051970', label: 'Quaker', note: '42 oz' });
      out.same = ans(s, 'p2', true, false) && JSON.stringify(H.stored()) === before;
      // changing the preferred product
      s = await act('shopPrefSet', { op: 'p3', key, productId: '165233284' });
      out.changed = ans(s, 'p3', true, true) && s.shopping.prefs[0].productId === '165233284' && s.shopping.prefs[0].label === '' && H.stored().prefs[key].id === '165233284';

      // strict: nothing is stored for any of these
      const bad = [
        ['badKey', { key: 'oats', productId: '11111' }],
        ['badKey2', { key: 'sd:', productId: '11111' }],
        ['badKey3', { key: 'sd:a b', productId: '11111' }],
        ['badKey4', { key: 'ing:Oats|g', productId: '11111' }],
        ['noKey', { productId: '11111' }],
        ['shortId', { key, productId: '1234' }],
        ['longId', { key, productId: '1234567890123456' }],
        ['alphaId', { key, productId: '12345abc' }],
        ['urlId', { key, productId: 'https://www.walmart.com/ip/165233284' }],
        ['numberId', { key, productId: 165233284 }],
        ['noId', { key }],
        ['longLabel', { key, productId: '11111', label: 'x'.repeat(81) }],
        ['longNote', { key, productId: '11111', note: 'x'.repeat(121) }],
        ['objLabel', { key, productId: '11111', label: { a: 1 } }]
      ];
      out.rejected = [];
      for (const [name, more] of bad) {
        s = await act('shopPrefSet', Object.assign({ op: name }, more));
        if (!(ans(s, name, false, false) && H.stored().prefs[key].id === '165233284' && Object.keys(H.stored().prefs).length === 1)) out.rejected.push(name);
      }
      s = await act('shopPrefSet', { op: 'other', key: 'ing:rolled oat|g', productId: '22222222' });
      out.second = ans(s, 'other', true, true) && s.shopping.prefs.length === 2 && H.stored().prefs['ing:rolled oat|g'].id === '22222222';

      // a failed save is taken back, answered not ok; a retry (new operation) saves
      s = await failing(() => act('shopPrefSet', { op: 'f1', key: 'sd:zzz', productId: '33333' }));
      out.failed = ans(s, 'f1', false, false) && !H.stored().prefs['sd:zzz'] && !state.mealsMeta.walmart.prefs['sd:zzz'] && s.shopping.prefs.length === 2;
      s = await act('shopPrefSet', { op: 'f2', key: 'sd:zzz', productId: '33333' });
      out.retry = ans(s, 'f2', true, true) && H.stored().prefs['sd:zzz'].id === '33333';

      // clearing
      s = await act('shopPrefClear', { op: 'c1', key: 'sd:zzz' });
      out.cleared = ans(s, 'c1', true, true) && !H.stored().prefs['sd:zzz'] && s.shopping.prefs.length === 2;
      s = await act('shopPrefClear', { op: 'c2', key: 'sd:zzz' });
      out.clearAgain = ans(s, 'c2', true, false);
      s = await act('shopPrefClear', { op: 'c3', key: 'bad key' });
      out.clearBadKey = ans(s, 'c3', false, false);

      // backups carry the field
      const backup = JSON.parse(backupJson()), saved = backup.state ? backup.state : backup;
      out.backup = !!saved.mealsMeta.walmart && saved.mealsMeta.walmart.prefs[key].id === '165233284';

      // a save written by something else: only the well formed entries are projected
      state.mealsMeta.walmart = { prefs: { 'bad key': { id: '11111' }, 'sd:ok': { id: '44444', label: 7, note: 'n' }, 'sd:no': { id: 'abc12' }, 'sd:null': null, 'sd:big': { id: '123456789012345678' } } };
      save(); await wait(200);
      s = latest();
      out.defensive = JSON.stringify(s.shopping.prefs) === JSON.stringify([{ key: 'sd:ok', productId: '44444', label: '', note: 'n' }]);
      state.mealsMeta.walmart = 'garbage'; save(); await wait(200);
      out.garbage = latest().shopping.prefs.length === 0;
      state.mealsMeta.walmart = [1, 2]; save(); await wait(200);
      out.arrayField = latest().shopping.prefs.length === 0;
      delete state.mealsMeta.walmart; save();

      // the bound: 200 matches, a 201st is refused, changing one of the 200 is not
      for (let i = 1; i <= 200; i++) { state.mealsMeta.walmart = state.mealsMeta.walmart || { prefs: {} }; state.mealsMeta.walmart.prefs['sd:k' + i] = { id: '55555', label: '', note: '' } }
      save(); await wait(200);
      out.full = latest().shopping.prefs.length === 200;
      s = await act('shopPrefSet', { op: 'n201', key: 'sd:k201', productId: '55555' });
      out.bound = ans(s, 'n201', false, false) && !state.mealsMeta.walmart.prefs['sd:k201'];
      s = await act('shopPrefSet', { op: 'chg', key: 'sd:k7', productId: '66666' });
      out.changeAtBound = ans(s, 'chg', true, true) && state.mealsMeta.walmart.prefs['sd:k7'].id === '66666';
      return out;
    });
    const t = (n, k) => ok(n, r[k] === true, r);
    t('the projection has the version, session, revision, week and empty prefs', 'shape');
    t('the items are the page\'s own Smart Diet rows, with grams and pack sizes', 'items');
    t('the recipes are the Recipes page\'s list for the class, with their ingredient lines', 'recipes');
    t('the Food projection stays under the size cap', 'sizeOk');
    t('the classic shop projection and pane are untouched', 'classicKept');
    t('the revision rises with each projection', 'revisionRises');
    t('shopPrefSet stores the match, answers ok, projects it back and persists it', 'saved');
    t('a repeated operation is answered "Already recorded." and stores nothing', 'repeat');
    t('the same value again is ok and changes nothing', 'same');
    t('the preferred product can be changed (a missing label clears the old one)', 'changed');
    ok('every bad command is refused and stores nothing', Array.isArray(r.rejected) && r.rejected.length === 0, r.rejected);
    t('a second ingredient is stored beside the first', 'second');
    t('a failed save is taken back and answered not ok', 'failed');
    t('and a retry saves', 'retry');
    t('shopPrefClear removes the match', 'cleared');
    t('clearing what is not there is ok and changes nothing', 'clearAgain');
    t('clearing a bad key is refused', 'clearBadKey');
    t('the matches are in the backup', 'backup');
    t('malformed saved entries are not projected', 'defensive');
    t('a saved field that is not an object is ignored', 'garbage');
    t('a saved field that is an array is ignored', 'arrayField');
    t('200 matches are projected', 'full');
    t('a 201st match is refused', 'bound');
    t('one of the 200 can still be changed', 'changeAtBound');
    ok('no script errors with the capability on', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* 2. a screen without the capability: the classic projection only */
  {
    const p = await start(b, false);
    await p.evaluate(HELPERS);
    const r = await p.evaluate(async () => {
      const { act, latest, setup, calls } = H, out = {};
      await setup();
      const s = latest();
      out.none = s.shopping === undefined && !!s.shop && s.shop.items.length > 10;
      const n = calls('update').length;
      await act('shopPrefSet', { op: 'x', key: 'sd:oats', productId: '11111' });
      out.stored = !!(JSON.parse(localStorage.getItem(STORE)).mealsMeta || {}).walmart;
      out.stillNone = latest().shopping === undefined;
      return out;
    });
    ok('without food.shopping.v1 the projection has no shopping field and keeps the classic shop', r.none, r);
    ok('and a stray shopPrefSet from such a screen stores nothing', r.stored === false, r);
    ok('and the field stays absent', r.stillNone, r);
    ok('no script errors with the capability off', p.errors.length === 0, p.errors);
    await p.context().close();
  }
  await b.close();
  console.log(`\n${pass} passed, ${fail} failed`); process.exit(fail ? 1 : 0);
})();
