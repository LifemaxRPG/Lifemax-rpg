/* Native Log, web half: the projection a native screen draws from, the preview and submit commands, and what the page does with them.
   The native Log is the default: on in a fresh save, the classic form once the owner turns it off. A fake NativeLog plugin stands in
   for Android. Run: NODE_PATH=$(npm root -g) node tests/native-log.test.js [built html] */
const { chromium } = require('playwright');
const currentPage = require('./current-page'), file = currentPage(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 600) : '')) } };
(async () => {
  const site = await currentPage.serve(file); // served, not file://, so a reload keeps what the page stored (current-page.js)
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript(`
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));
    window.__calls=[];
    /* the one-off thumbnail walk (9 s after launch on Android) visits every tab and ends on the tab it started from: switched off
       as the page's scripts finish, before its timer, however long a busy machine takes to boot (after the reload too) */
    addEventListener('DOMContentLoaded',()=>{lmaThumbWarm=async()=>{}});
    const mk=(name,caps)=>{const ls={};return {
      __emit:(e,d)=>(ls[e]||[]).map(f=>f(d)),
      addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
      open:async a=>{window.__calls.push([name,'open',a])},
      update:async a=>{window.__calls.push([name,'update',a])},
      close:async a=>{window.__calls.push([name,'close',a]);(ls.closed||[]).forEach(f=>f({session:a&&a.session}))},
      capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true}),
      handshake:async()=>({protocol:1,capabilities:caps})}};
    window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>n==='NativeLog'||n==='NativeOverview',getPlatform:()=>'android',
      Plugins:{NativeLog:mk('log',['log.projection.v1']),NativeOverview:mk('overview',[])},registerPlugin:()=>({}),convertFileSrc:u=>u};
  `);
  await p.goto(site.url); await p.waitForTimeout(3000);
  const r = await p.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), L = window.Capacitor.Plugins.NativeLog;
    const last = (k) => [...window.__calls].reverse().find(c => c[0] === 'log' && c[1] === k);
    const opens = () => window.__calls.filter(c => c[0] === 'log' && c[1] === 'open').length;
    const openLog = async () => { const n = opens(); navigate('log'); for (let t = 0; t < 100 && opens() === n; t++) await wait(50) };
    const out = {};
    const S = lmaLoad(); S.nativeOverview = false; lmaSave();
    // a fresh save: the owner never touched the switch, so the native Log is on by default (NativeTabKit's defaultOn)
    const toggle = document.getElementById('nativeLogToggle');
    out.fresh = !('nativeLog' in lmaLoad()) && !('nativeLog' in JSON.parse(localStorage.getItem(LMA_PREF_KEY))) && toggle.checked && !toggle.disabled && NativeTabs.covered('log');
    // a prefill in the web form (as Life Director or a link would do) carries over when the native screen opens
    document.getElementById('activityType').value = 'running'; activityPresetChanged(); document.getElementById('duration').value = 45;
    // the first open captures the Overview top and bottom bars for the screen (navScene, a cache miss), which can take over a second on a busy machine
    await openLog();
    const opened = last('open');
    out.opened = !!opened && opened[2].snapshot.protocol === 1 && opened[2].snapshot.presets.length >= 9 && opened[2].snapshot.attributes.length === 8;
    if (!opened) return out;
    out.prefill = opened[2].snapshot.draft.type === 'running' && opened[2].snapshot.draft.duration === '45' && opened[2].snapshot.draft.primary === 'endurance';
    const shown = opened[2].snapshot, award = activityAward(shown.draft, true);
    out.previewMatchesPage = shown.preview.total === activityPreview(shown.draft).total && shown.preview.award === award.xp && shown.preview.reason === award.msg && JSON.stringify(shown.preview.split) === JSON.stringify(award.split);
    out.limits = shown.minMinutes === ECO.minMin && shown.maxMinutes === 1440 && /up to 3× at 120 min/.test(shown.durationRules) && document.getElementById('durationRules').textContent === shown.durationRules;
    // preview: the page answers with the new award, and the draft revision does not change (so typing is never replaced)
    const rev0 = opened[2].snapshot.draftRevision, d60 = Object.assign({}, opened[2].snapshot.draft, { duration: '60' });
    await Promise.all(L.__emit('command', { command: 'preview', payload: { draft: d60 }, requestId: 'r1' }));
    const reply = (id) => [...window.__calls].reverse().find(c => c[0] === 'log' && c[1] === 'update' && c[2].reply && c[2].reply.requestId === id);
    const up = reply('r1');
    out.preview = !!up && up[2].reply.ok === true && up[2].snapshot.preview.award === activityAward(d60, true).xp && up[2].snapshot.draftRevision === rev0;
    out.noCloseOnCommand = !window.__calls.some(c => c[0] === 'log' && c[1] === 'close');
    // the XP celebration is held back until Overview is showing, not played under the native screen during the save
    // the level-up overlay arrives about a second after a save and would cover the screens this test reopens; it is not under test here
    const realLevelUp = showLevelUp; showLevelUp = function () { };
    const fx = []; const realFx = SFX.xp; SFX.xp = function () { fx.push(lmaView()) };
    // submit: saved by the page's own function, toast, a fresh draft, and on to Overview
    const n0 = state.activities.length, toasts = [], realToast = window.toast; window.toast = m => toasts.push(String(m));
    await Promise.all(L.__emit('command', { command: 'submit', payload: { draft: d60, operationId: 'op-1' }, requestId: 'r2' }));
    const sub = [...window.__calls].reverse().find(c => c[0] === 'log' && c[1] === 'update' && c[2].reply && c[2].reply.requestId === 'r2');
    // counted by operation id: the page may add an activity of its own as the day turns (just after midnight), so the newest is not always ours
    const ours = (op) => state.activities.filter(a => a.opId === op).length;
    out.saved = ours('op-1') === 1 && JSON.parse(localStorage.getItem(STORE)).activities.some(a => a.opId === 'op-1');
    out.reply = !!sub && sub[2].reply.ok === true && /Activity saved/.test(sub[2].reply.message) && sub[2].snapshot.draftRevision > rev0;
    out.toastAndNavigate = toasts.some(m => /Activity saved/.test(m)) && lmaView() === 'dashboard';
    out.fxHeld = fx.length === 0; await wait(600); out.fxAfter = fx.length >= 1 && fx.every(v => v === 'dashboard'); const fxSaved = fx.length; // the page's own entry as the day turns may celebrate too
    // the same operation again (a double tap that got through) records nothing new
    await openLog();
    await Promise.all(L.__emit('command', { command: 'submit', payload: { draft: d60, operationId: 'op-1' }, requestId: 'r3' }));
    out.oncePerOperation = ours('op-1') === 1;
    // a failed save comes back as a failure, nothing is added, and the screen stays
    await openLog();
    const realSet = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return realSet.apply(this, arguments) };
    try { await Promise.all(L.__emit('command', { command: 'submit', payload: { draft: d60, operationId: 'op-2' }, requestId: 'r4' })) } finally { Storage.prototype.setItem = realSet }
    const bad = [...window.__calls].reverse().find(c => c[0] === 'log' && c[1] === 'update' && c[2].reply && c[2].reply.requestId === 'r4');
    out.failure = !!bad && bad[2].reply.ok === false && /Could not save/.test(bad[2].reply.message) && ours('op-2') === 0 && lmaView() === 'log';
    // an unknown command is refused without closing anything
    await Promise.all(L.__emit('command', { command: 'nope', payload: {}, requestId: 'r5' }));
    const unk = [...window.__calls].reverse().find(c => c[0] === 'log' && c[1] === 'update' && c[2].reply && c[2].reply.requestId === 'r5');
    out.unknown = !!unk && unk[2].reply.ok === false;
    // the page cleans a hostile draft: unknown preset, absurd duration, made-up attribute, oversized notes
    const cl = NativeLogProjection.clean({ type: 'hack', duration: '99999', quality: '7', primary: 'root', secondary: 'x', notes: 'n'.repeat(5000) });
    out.clean = cl.type === 'custom' && cl.duration === '1440' && cl.quality === '1' && cl.primary === 'adventure' && cl.secondary === '' && cl.notes.length === 2000;
    window.toast = realToast; SFX.xp = realFx; showLevelUp = realLevelUp; out.fxNotOnFailure = fx.length === fxSaved;
    // Back from a web page to Log brings the native screen back, as the default
    navigate('settings'); await wait(300);
    out.settingsOn = toggle.checked && lmaLoad().nativeLog === undefined;
    const n1 = opens(); lmaBack(); for (let t = 0; t < 100 && opens() === n1; t++) await wait(50);
    out.backOpens = lmaView() === 'log' && opens() === n1 + 1;
    // turned off: stored as an explicit false, and Log, Back to it, the hand-over and the reload safety net all keep the classic form
    navigate('settings'); await wait(300);
    await setNativeLogEnabled(false);
    const n2 = opens(); lmaBack(); await wait(1500);
    out.off = JSON.parse(localStorage.getItem(LMA_PREF_KEY)).nativeLog === false && !toggle.checked && lmaView() === 'log' && opens() === n2 && !NativeTabs.covered('log') && !!document.getElementById('activityType').offsetParent;
    return out;
  });
  // after a restart the owner's off still holds: the check polls for the classic form, however long the page takes to come back
  await p.reload();
  const r2 = await p.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms));
    const until = async (f, ms) => { for (let t = 0; t < ms && !f(); t += 50) await wait(50); return !!f() };
    const opens = () => window.__calls.filter(c => c[0] === 'log' && c[1] === 'open').length;
    const classic = () => lmaView() === 'log' && !!document.getElementById('activityType').offsetParent;
    navigate('settings'); // the kit syncs the switch as Settings shows
    const toggle = document.getElementById('nativeLogToggle'), checked = toggle.checked;
    navigate('log');
    const shown = await until(classic, 10000);
    // were the native screen wanted, it would open on arrival (a handshake, then the bars' capture): give it 1.5 s to (wrongly) come
    await until(() => opens() > 0, 1500);
    return { checked, disabled: toggle.disabled, opens: opens(), covered: NativeTabs.covered('log'), classic: shown && classic() };
  });
  ok('a fresh save has the native Log on: no stored choice, its switch shows on, and the tab counts as covered', r.fresh, r);
  ok('opening sends a full projection (presets, eight attributes)', r.opened, r);
  ok('a prefill in the web form carries over to the native draft', r.prefill, r);
  ok('the projection previews the raw total and the award Save would pay, from the page\'s own functions', r.previewMatchesPage, r);
  ok('the projection carries the duration limits and the rules the web form shows, so the screen hard-codes none', r.limits, r);
  ok('a preview command answers with the new award and does not replace the draft being typed', r.preview && r.noCloseOnCommand, r);
  ok('submit saves through recordActivity with the operation id, persists it, toasts and goes to Overview', r.saved && r.reply && r.toastAndNavigate, r);
  ok('the XP celebration waits until Overview is showing instead of playing during the save', r.fxHeld && r.fxAfter, r);
  ok('a failed save plays no celebration', r.fxNotOnFailure, r);
  ok('the same operation id records nothing new', r.oncePerOperation, r);
  ok('a failed save is reported, adds nothing and keeps the screen', r.failure, r);
  ok('an unknown command is refused', r.unknown, r);
  ok('a hostile draft is cleaned by the page', r.clean, r);
  ok('Back from Settings to Log opens the native screen again, the switch showing on with nothing stored', r.settingsOn && r.backOpens, r);
  ok('turned off, the choice is stored as false and Log shows the classic form, by Back too, with nothing covering it', r.off, r);
  ok('after a restart, a Log turned off still shows the classic form and its switch shows off', !r2.checked && !r2.disabled && r2.opens === 0 && !r2.covered && r2.classic, r2);
  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); site.close(); process.exit(fail ? 1 : 0);
})();
