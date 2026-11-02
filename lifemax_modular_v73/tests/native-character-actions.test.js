/* Native Character, web half of its actions: Luck rolled by the page and answered, benchmark add/edit/remove through the page's own
   functions, and "Log <attribute> activity" going to the native Log with the web form prefilled. Fake NativeCharacter and NativeLog
   plugins stand in for Android. Run: NODE_PATH=$(npm root -g) node tests/native-character-actions.test.js [built html] */
const { chromium } = require('playwright');
const currentPage = require('./current-page'), file = currentPage(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 700) : '')) } };
(async () => {
  const site = await currentPage.serve(file);
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript(`
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete',benchmarks:[{name:'5K',value:'28:30',date:'2026-10-01T00:00:00.000Z'}]}));
    localStorage.setItem('lifemax_android_prefs_v1', JSON.stringify({nativeCharacter:true}));
    window.__calls=[]; window.__caps=sessionStorage.getItem('full')?['character.projection.v1','character.actions.v1']:['character.projection.v1'];
    addEventListener('DOMContentLoaded',()=>{lmaThumbWarm=async()=>{}});
    const mk=(name,caps)=>{const ls={};return {
      __emit:(e,d)=>(ls[e]||[]).map(f=>f(d)),
      addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
      open:async a=>{window.__calls.push([name,'open',a])},
      update:async a=>{window.__calls.push([name,'update',a])},
      close:async a=>{window.__calls.push([name,'close',a]);(ls.closed||[]).forEach(f=>f({session:a&&a.session}))},
      capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true}),
      handshake:async()=>({protocol:1,capabilities:caps()})}};
    window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>n==='NativeLog'||n==='NativeCharacter'||n==='NativeOverview',getPlatform:()=>'android',
      Plugins:{NativeLog:mk('log',()=>['log.projection.v1']),NativeCharacter:mk('character',()=>window.__caps),NativeOverview:mk('overview',()=>[])},registerPlugin:()=>({}),convertFileSrc:u=>u};
  `);
  await p.goto(site.url); await p.waitForTimeout(3000);
  const r = await p.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), C = window.Capacitor.Plugins.NativeCharacter;
    const calls = (name, k) => window.__calls.filter(c => c[0] === name && c[1] === k);
    const out = {}, S = lmaLoad(); S.nativeOverview = false; lmaSave();
    const openChar = async () => { const n = calls('character', 'open').length; navigate('character'); for (let t = 0; t < 120 && calls('character', 'open').length === n; t++) await wait(50) };
    // an app that does not list the actions keeps handing over: the projection says so
    await openChar();
    const old = calls('character', 'open')[0];
    out.oldApp = !!old && old[2].snapshot.actions === false;
    return out;
  });
  ok('an app without the actions capability gets actions=false (the taps keep handing over)', r.oldApp, r);
  // A new page load so the handshake is asked again with the full capability list
  await p.evaluate(() => sessionStorage.setItem('full', '1'));
  await p.reload(); await p.waitForTimeout(3000);
  const r2 = await p.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), C = window.Capacitor.Plugins.NativeCharacter, L = window.Capacitor.Plugins.NativeLog;
    const calls = (name, k) => window.__calls.filter(c => c[0] === name && c[1] === k);
    const out = {}, S = lmaLoad(); S.nativeOverview = false; lmaSave();
    const openChar = async () => { const n = calls('character', 'open').length; navigate('character'); for (let t = 0; t < 120 && calls('character', 'open').length === n; t++) await wait(50) };
    let seq = 0;
    const send = async (command, payload) => { const id = 'q' + (++seq); await Promise.all(C.__emit('command', { command, payload, requestId: id })); const u = [...window.__calls].reverse().find(c => c[0] === 'character' && c[1] === 'update' && c[2].reply && c[2].reply.requestId === id); return u && { reply: u[2].reply, snapshot: u[2].snapshot } };
    const stored = () => JSON.parse(localStorage.getItem(STORE));
    const realLevelUp = showLevelUp; showLevelUp = function () { };
    await openChar();
    const o = calls('character', 'open')[0];
    out.opened = !!o && o[2].snapshot.actions === true && o[2].snapshot.today === getToday() && o[2].snapshot.luckRoll === null && o[2].snapshot.benchmarkLimits.name === 60 && o[2].snapshot.benchmarkLimits.value === 40;
    if (!o) return out;
    const day = getToday();

    /* ---- Luck ---- */
    const noClose = () => !calls('character', 'close').length;
    out.stale = (await send('luck', { op: 'l0', day: '2000-01-01' })).reply.ok === false && !state.luck;
    out.noDay = (await send('luck', { op: 'l0' })).reply.ok === false && !state.luck;
    // a roll that cannot be stored changes nothing and can be tried again
    const realSet = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return realSet.apply(this, arguments) };
    let bad; try { bad = await send('luck', { op: 'l1', day }) } finally { Storage.prototype.setItem = realSet }
    out.failed = bad.reply.ok === false && bad.reply.message === unsavedMessage() && !state.luck && !(state.luckLog && state.luckLog[day]) && !stored().luck;
    const fresh = await send('luck', { op: 'l2', day });
    const L1 = state.luck;
    out.rolled = fresh.reply.ok === true && fresh.reply.changed === true && !!L1 && L1.date === day && stored().luck.score === L1.score && stored().luckLog[day] === L1.score;
    out.reply = JSON.stringify(fresh.reply.luck.dice) === JSON.stringify(L1.dice) && fresh.reply.luck.score === luckFmt(L1.score) && fresh.reply.luck.tier === luckTier(L1.score).name && fresh.reply.luck.message === luckMsg(L1.score) && typeof fresh.reply.luck.color === 'number' && fresh.reply.luck.dice.length === 4;
    out.projection = fresh.snapshot.luck === luckFmt(L1.score) && fresh.snapshot.luckRoll.score === luckFmt(L1.score);
    const again = await send('luck', { op: 'l3', day });
    out.repeat = again.reply.ok === true && again.reply.changed === false && /Already rolled today/.test(again.reply.message) && state.luck.score === L1.score && JSON.stringify(again.reply.luck) === JSON.stringify(fresh.reply.luck);
    out.noOverlay = !document.body.classList.contains('noscroll') && noClose();

    /* ---- benchmarks ---- */
    const bm = () => stored().benchmarks;
    const add = await send('benchmarkAdd', { op: 'b1', name: '  Bench press ', value: ' 225 lb ' });
    out.add = add.reply.ok && add.reply.changed && bm().length === 2 && bm()[1].name === 'Bench press' && bm()[1].value === '225 lb' && add.snapshot.benchmarks.length === 2 && add.snapshot.benchmarks[1].value === '225 lb';
    const dup = await send('benchmarkAdd', { op: 'b1', name: 'Bench press', value: '225 lb' });
    out.addOnce = dup.reply.ok && !dup.reply.changed && dup.reply.message === 'Already recorded.' && bm().length === 2;
    const empty = await send('benchmarkAdd', { op: 'b2', name: 'Pull-ups', value: '   ' });
    out.refusedInTheWordsOfThePage = !empty.reply.ok && empty.reply.message === 'Enter a name and result' && bm().length === 2;
    const long = await send('benchmarkAdd', { op: 'b3', name: 'x'.repeat(61), value: '1' });
    out.tooLong = !long.reply.ok && /60/.test(long.reply.message) && bm().length === 2;
    const noop = await send('benchmarkAdd', { name: 'n', value: 'v' });
    out.needsOp = !noop.reply.ok && bm().length === 2;
    const set = await send('benchmarkSet', { index: 0, name: '5K', value: ' 27:45 ' });
    out.set = set.reply.ok && set.reply.changed && bm()[0].value === '27:45' && bm()[0].name === '5K' && set.snapshot.benchmarks[0].value === '27:45';
    const same = await send('benchmarkSet', { index: 0, name: '5K', value: '27:45' });
    out.setSame = same.reply.ok && !same.reply.changed && same.reply.message === 'Already recorded.';
    const blank = await send('benchmarkSet', { index: 0, name: '5K', value: '' });
    out.setBlank = !blank.reply.ok && blank.reply.message === 'Enter a result' && bm()[0].value === '27:45';
    const wrong = await send('benchmarkSet', { index: 0, name: 'Something else', value: '1' });
    out.setGuard = !wrong.reply.ok && /changed/.test(wrong.reply.message) && bm()[0].value === '27:45';
    const outOfRange = await send('benchmarkSet', { index: 9, name: '5K', value: '1' });
    out.setRange = !outOfRange.reply.ok;
    // a failed store is taken back: the page and the screen both show the old list
    Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return realSet.apply(this, arguments) };
    let f1, f2, f3; try {
      f1 = await send('benchmarkSet', { index: 0, name: '5K', value: '26:00' });
      f2 = await send('benchmarkAdd', { op: 'b9', name: 'Squat', value: '300' });
      f3 = await send('benchmarkRemove', { index: 0, name: '5K' });
    } finally { Storage.prototype.setItem = realSet }
    out.storeFails = [f1, f2, f3].every(f => !f.reply.ok && f.reply.message === unsavedMessage()) && state.benchmarks.length === 2 && state.benchmarks[0].value === '27:45' && bm().length === 2 && f3.snapshot.benchmarks.length === 2;
    out.retryAfterFailure = (await send('benchmarkAdd', { op: 'b9', name: 'Squat', value: '300' })).reply.changed === true && bm().length === 3;
    const rm = await send('benchmarkRemove', { index: 1, name: 'Bench press' });
    out.remove = rm.reply.ok && rm.reply.changed && bm().map(x => x.name).join() === '5K,Squat';
    const rmWrong = await send('benchmarkRemove', { index: 1, name: 'Bench press' });
    out.removeGuard = !rmWrong.reply.ok && bm().length === 2;
    // the page's own form still works and shares the rules
    document.getElementById('benchName').value = 'Web one'; document.getElementById('benchValue').value = '1';
    addBenchmark(); out.webAdd = state.benchmarks.some(x => x.name === 'Web one');
    document.getElementById('benchName').value = 'x'; document.getElementById('benchValue').value = '';
    const n = state.benchmarks.length; addBenchmark(); out.webRefuse = state.benchmarks.length === n;
    deleteBenchmark(state.benchmarks.findIndex(x => x.name === 'Web one')); out.webDelete = !state.benchmarks.some(x => x.name === 'Web one');
    out.stillOpen = noClose();

    /* ---- Log ---- */
    const logOpens = () => calls('log', 'open').length, l0 = logOpens();
    const lg = await send('log', { attribute: 'endurance' });
    out.logReply = lg.reply.ok === true && lg.reply.navigate === 'log';
    for (let t = 0; t < 120 && logOpens() === l0; t++) await wait(50);
    const lo = calls('log', 'open')[l0];
    out.logOpens = lmaView() === 'log' && !!lo && lo[2].snapshot.draft.primary === 'endurance' && rules[lo[2].snapshot.draft.type].primary === 'endurance';
    // an attribute no preset trains falls back to a custom activity for that attribute
    const saved = rules; const clone = structuredClone(rules); Object.keys(clone).forEach(k => { if (k !== 'custom' && clone[k].primary === 'social') clone[k].primary = 'business' }); rules = clone;
    await openChar();
    const l1 = logOpens();
    await send('log', { attribute: 'social' });
    for (let t = 0; t < 120 && logOpens() === l1; t++) await wait(50);
    const lo2 = calls('log', 'open')[l1];
    out.logCustom = !!lo2 && lo2[2].snapshot.draft.type === 'custom' && lo2[2].snapshot.draft.primary === 'social';
    rules = saved;
    await openChar();
    const bad2 = await send('log', { attribute: 'nope' });
    out.logUnknown = !bad2.reply.ok && !bad2.reply.navigate;
    const unk = await send('nope', {});
    out.unknown = !unk.reply.ok;
    showLevelUp = realLevelUp;
    return out;
  });
  const t = (n, k) => ok(n, r2[k], r2);
  ok('the capability is read from the handshake: a screen of the page that has the new list says actions', r2.opened, r2);
  t('a roll for another day, or none, is refused and rolls nothing', 'stale'); t('(no day sent)', 'noDay');
  t('a roll that cannot be stored changes nothing and is refused in the page\'s words', 'failed');
  t('the page rolls and saves Luck, once, with the result in the reply', 'rolled'); t('the reply carries the dice, number, tier, words and colour', 'reply');
  t('the next projection shows the roll', 'projection'); t('a second roll the same day shows the same result and changes nothing', 'repeat');
  t('no dice overlay and the screen stays open', 'noOverlay');
  t('a benchmark is added through the page\'s own rule, trimmed', 'add'); t('the same operation adds nothing twice', 'addOnce');
  t('an empty result is refused in the page\'s words', 'refusedInTheWordsOfThePage'); t('a name or result that is too long is refused', 'tooLong'); t('an add without an operation id is refused', 'needsOp');
  t('a result is edited', 'set'); t('editing to the same result says it is recorded', 'setSame'); t('a blank result is refused', 'setBlank');
  t('an edit of a benchmark that is not the one seen is refused', 'setGuard'); t('an index out of range is refused', 'setRange');
  t('a change that could not be stored is taken back, in memory and on the screen', 'storeFails'); t('and the same operation can be tried again', 'retryAfterFailure');
  t('a benchmark is removed', 'remove'); t('removing one that is not the one seen is refused', 'removeGuard');
  t('the web form and its Remove button keep working', 'webAdd'); t('(refusal)', 'webRefuse'); t('(delete)', 'webDelete');
  t('the screen is never closed by a command', 'stillOpen');
  t('Log: the reply names the Log tab', 'logReply'); t('the native Log opens with the attribute\'s own preset prefilled', 'logOpens');
  t('an attribute no preset trains opens a custom activity for it', 'logCustom'); t('an unknown attribute is refused', 'logUnknown'); t('an unknown command is refused', 'unknown');
  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); site.close(); process.exit(fail ? 1 : 0);
})();
