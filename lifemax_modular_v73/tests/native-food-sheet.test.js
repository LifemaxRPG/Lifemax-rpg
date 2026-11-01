/* Native Food's add-food sheet, web half: the sheet is drawn by the native screen from the projection's `sheet` field and driven by
   actions, while the page keeps every rule (the search order, the steps, the macros, the logging, Undo). Checked here: the sheet opens
   without closing the screen or opening the page's own sheet, search and its tabs, a food's portion view against the page's own
   numbers, steps and servings and multiples, the slot, favourites, logging (and Undo, and a save that fails), quick calories, Back,
   a stale row, an action for another session, and an update asked for while one is in flight.
   A fake NativeNutrition plugin stands in for Android.
   Run: NODE_PATH=$(npm root -g) node tests/native-food-sheet.test.js */
const { chromium } = require('playwright');
const page = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') });
  await p.addInitScript(`
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));
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
      Plugins:{NativeNutrition:mk('food'),NativeOverview:mk('overview')},registerPlugin:()=>({}),convertFileSrc:u=>u};
  `);
  await p.goto('file://' + page); await p.waitForTimeout(3000);
  const r = await p.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), out = {}, F = window.Capacitor.Plugins.NativeNutrition;
    const calls = k => window.__calls.filter(c => c[0] === 'food' && c[1] === k);
    const until = async (f, ms = 5000) => { for (let t = 0; t < ms && !f(); t += 25) await wait(25) };
    const latest = () => [...window.__calls].reverse().find(c => c[0] === 'food' && (c[1] === 'open' || c[1] === 'update'))[2].snapshot;
    const session = () => calls('open').pop()[2].session;
    const act = async (command, more) => { const n = window.__calls.length; await F.__emit('action', Object.assign({ session: session(), command }, more)); await until(() => window.__calls.length > n, 1500); await wait(60); return latest() };
    lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { }
    const S = lmaLoad(); S.nativeOverview = false; S.nativeNutrition = true; lmaSave();
    navigate('meals'); await until(() => calls('open').length, 8000);
    out.opened = calls('open').length === 1;
    const s0 = latest();
    out.closedSheet = s0.sheet === null && s0.notice === null;

    // opening the sheet: the native sheet, not the page's, and the screen stays
    let s = await act('sheetOpen', { slot: 'dinner' });
    out.open = !!s.sheet && s.sheet.view === 'search' && s.sheet.slot === 'dinner' && s.sheet.slots.map(x => x.id).join() === 'breakfast,lunch,dinner,snacks'
      && s.sheet.tab === 'recent' && s.sheet.rows.length === 0 && s.sheet.empty.title === 'Nothing logged yet' && !s.sheet.searching;
    out.noPageSheet = !document.querySelector('#fv2Ov.show') && calls('close').length === 0;

    // search: the page's own foods, in the page's own order, worded as the page words a row
    s = await act('sheetQuery', { q: 'chicken' });
    const toks = fv2SearchTokens('chicken'), want = fv2LocalFoods(toks);
    out.searchRows = s.sheet.rows.length >= 1 && s.sheet.rows.slice(0, want.length).map(x => x.id).join() === want.map(f => String(f.id)).join() && s.sheet.query === 'chicken';
    const d0 = fv2RowData(want[0], '');
    out.rowWords = JSON.stringify([s.sheet.rows[0].name, s.sheet.rows[0].detail, s.sheet.rows[0].grams, s.sheet.rows[0].kcal, s.sheet.rows[0].p, s.sheet.rows[0].c, s.sheet.rows[0].f])
      === JSON.stringify([d0.name, d0.detail, d0.grams, Math.round(d0.kcal), d0.p, d0.c, d0.f]);
    await until(() => latest().sheet && !latest().sheet.searching, 4000);
    out.searchDone = latest().sheet.searching === false;
    s = await act('sheetQuery', { q: 'zzzqx' });
    out.noMatch = s.sheet.rows.length === 0 && (await until(() => !latest().sheet.searching, 4000), latest().sheet.empty.title === 'No matches yet');
    s = await act('sheetTab', { tab: 'fav' });
    out.tabFav = s.sheet.tab === 'fav' && s.sheet.query === '' && s.sheet.empty.title === 'No favorites yet';

    // a food's portion view: the page's numbers
    s = await act('sheetQuery', { q: 'chicken' });
    const row = s.sheet.rows[0], food = want[0];
    s = await act('sheetPick', { index: row.index, id: 'not-' + row.id });
    out.staleRow = s.sheet.view === 'search';
    s = await act('sheetPick', { index: row.index, id: row.id });
    let pt = s.sheet.portion;
    const g0 = fv2Unit(food).g;
    out.portion = s.sheet.view === 'portion' && pt.name === food.n && pt.grams === g0 && pt.kcal === Math.round(scaleFood(food, g0).kcal)
      && JSON.stringify(pt.impact) === JSON.stringify(fv2ImpactData(food, g0, null)) && JSON.stringify(pt.servings) === JSON.stringify(fv2ServingsData(food, g0))
      && pt.logLabel === fv2LogLabel(scaleFood(food, g0), false, 'dinner') && pt.mults.map(m => m.label).join() === '½×,1×,1.5×,2×,3×';
    s = await act('sheetStep', { dir: 1 });
    out.step = s.sheet.portion.grams === fv2StepGrams(g0, 1) && s.sheet.portion.kcal === Math.round(scaleFood(food, fv2StepGrams(g0, 1)).kcal);
    s = await act('sheetMult', { x: 2 });
    out.mult = s.sheet.portion.grams === fv2MultGrams(food, 2);
    s = await act('sheetMult', { x: 7 });
    out.multRefused = s.sheet.portion.grams === fv2MultGrams(food, 2);
    s = await act('sheetGrams', { grams: 175 });
    out.grams = s.sheet.portion.grams === 175 && s.sheet.portion.servings.every(x => x.on === (Math.abs(175 - x.grams) < .5));
    s = await act('sheetSlot', { slot: 'snacks' });
    out.slot = s.sheet.slot === 'snacks' && /Add to Snacks · /.test(s.sheet.portion.logLabel);
    s = await act('sheetSlot', { slot: 'brunch' });
    out.slotRefused = s.sheet.slot === 'snacks';
    s = await act('sheetFav');
    out.fav = s.sheet.portion.fav === true && fv2IsFav(food);
    s = await act('sheetFav');
    out.unfav = s.sheet.portion.fav === false && !fv2IsFav(food);

    // logging: the page's own entry, its Undo, and the sheet closed
    const before = state.foodLog.length;
    s = await act('sheetLog');
    const e = state.foodLog[state.foodLog.length - 1];
    out.logged = state.foodLog.length === before + 1 && e.name === food.n && e.grams === 175 && e.slot === 'snacks' && s.sheet === null;
    out.notice = !!s.notice && s.notice.action === 'Undo' && s.notice.text.indexOf(' kcal') > 0;
    const nid = s.notice.id;
    s = await act('noticeAction', { id: nid + 1 });
    out.noticeOther = state.foodLog.length === before + 1;
    s = await act('noticeAction', { id: nid });
    out.undone = state.foodLog.length === before && s.entries.length === before;

    // the recent tab now has what was logged, and a row adds in one tap
    s = await act('sheetOpen', { slot: 'lunch' });
    out.recentTab = s.sheet.tab === 'recent' && s.sheet.rows.length === 0; // the entry was undone: nothing logged yet
    await act('sheetQuery', { q: 'chicken' });
    s = await act('sheetRowAdd', { index: 0, id: String(want[0].id) });
    out.rowAdd = state.foodLog.length === before + 1 && state.foodLog[state.foodLog.length - 1].grams === d0.grams && s.sheet === null;

    // a save that fails: nothing is kept, and the page's message reaches the screen
    const real = Storage.prototype.setItem;
    s = await act('sheetOpen', { slot: 'lunch' });
    await act('sheetQuery', { q: 'rice' });
    s = await act('sheetPick', { index: 0, id: String(fv2LocalFoods(fv2SearchTokens('rice'))[0].id) });
    Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
    const n1 = state.foodLog.length;
    try { s = await act('sheetLog') } finally { Storage.prototype.setItem = real }
    out.failedSave = state.foodLog.length === n1 && !!s.notice && s.notice.text === unsavedMessage() && s.sheet === null;

    // quick calories
    s = await act('sheetOpen', { slot: 'lunch' });
    s = await act('sheetQuick');
    out.quickView = s.sheet.view === 'quick';
    s = await act('sheetQuickSave', { kcal: 0, name: '' });
    out.quickEmpty = s.sheet.view === 'quick' && s.notice.text === 'Enter the calories';
    s = await act('sheetQuickSave', { kcal: 450, name: ' Snack bar ' });
    const q = state.foodLog[state.foodLog.length - 1];
    out.quickSaved = q.name === 'Snack bar' && q.kcal === 450 && q.grams === 100 && s.sheet === null;

    // Back: a view back, then the sheet closes; a sheet that is not open ignores its actions
    s = await act('sheetOpen', { slot: 'lunch' });
    await act('sheetQuery', { q: 'chicken' });
    s = await act('sheetPick', { index: 0, id: String(want[0].id) });
    s = await act('sheetBack');
    out.backView = s.sheet && s.sheet.view === 'search';
    s = await act('sheetBack');
    out.backClose = s.sheet === null;
    const n2 = state.foodLog.length;
    await F.__emit('action', { session: session(), command: 'sheetLog' }); await wait(120);
    out.closedIgnores = state.foodLog.length === n2;

    // an action from another session does nothing
    const m = window.__calls.length;
    await F.__emit('action', { session: 'someone-else', command: 'sheetOpen', slot: 'lunch' }); await wait(150);
    out.otherSession = window.__calls.length === m && latest().sheet === null;

    // an update asked for while one is in flight is not lost
    await act('sheetOpen', { slot: 'lunch' });
    window.__slow = 250;
    F.__emit('action', { session: session(), command: 'sheetQuery', q: 'a' });
    await wait(60);
    F.__emit('action', { session: session(), command: 'sheetQuery', q: 'abc' });
    await wait(1200);
    window.__slow = 0;
    out.noLostUpdate = latest().sheet.query === 'abc';

    // the screen was never closed by any of this, and the page's own sheet never opened
    out.neverClosed = calls('close').length === 0 && !document.querySelector('#fv2Ov.show');
    // the page's own sheet still works the same (the refactor kept it)
    navigate('meals'); fv2Open('lunch', 'search'); await wait(400);
    document.getElementById('fv2Q').value = 'chicken'; document.getElementById('fv2Q').dispatchEvent(new Event('input', { bubbles: true })); await wait(500);
    out.pageSheet = document.querySelectorAll('#fv2List .fv2-r').length >= 1;
    fv2Close();
    return out;
  });
  const names = {
    opened: 'native Food opens', closedSheet: 'the projection has no sheet and no notice until one opens',
    open: 'sheetOpen opens the native sheet on search, at the slot asked, with an honest empty list',
    noPageSheet: 'the page\'s own sheet does not open and the screen is not closed',
    searchRows: 'a search lists the page\'s own foods in the page\'s own order',
    rowWords: 'a row says what the page\'s row says: name, words, amount, kcal and macros',
    searchDone: 'the database part of a search ends', noMatch: 'a search with no match says so in the page\'s words',
    tabFav: 'a tab clears the search and says its own empty words', staleRow: 'a row that is no longer there is ignored',
    portion: 'the portion view is the page\'s: kcal, macro impact, servings, the log label and the multiples',
    step: 'a step moves the amount as the page\'s step does', mult: 'a multiple takes the page\'s amount', multRefused: 'a multiple that is not offered is refused',
    grams: 'a typed amount marks the matching serving', slot: 'the slot changes the log label', slotRefused: 'a slot that does not exist is refused',
    fav: 'the star keeps the food as a favourite', unfav: 'and takes it away again',
    logged: 'Log adds the page\'s entry at the amount and slot and closes the sheet', notice: 'the page\'s message, with Undo, reaches the screen',
    noticeOther: 'an Undo for another message does nothing', undone: 'Undo takes the entry back',
    recentTab: 'the recent tab is the page\'s (nothing logged now)', rowAdd: 'a row\'s + logs it at its amount in one tap',
    failedSave: 'a save that fails keeps nothing and the page\'s "could not save" reaches the screen',
    quickView: 'Quick calories opens its view', quickEmpty: 'no calories: the page\'s message, and the view stays', quickSaved: 'quick calories log a named one-off food as 100 g',
    backView: 'Back goes a view back', backClose: 'Back at the start closes the sheet', closedIgnores: 'a closed sheet ignores Log',
    otherSession: 'an action from another session does nothing', noLostUpdate: 'an update asked for while one is in flight is sent after it',
    neverClosed: 'the screen was never closed, and the page\'s own sheet never opened', pageSheet: 'the page\'s own sheet still searches the same'
  };
  for (const k of Object.keys(names)) ok(names[k], r[k] === true, r[k]);
  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
