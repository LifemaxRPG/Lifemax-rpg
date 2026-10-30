/* Native Log, web half: the projection a native screen draws from, the preview and submit commands, and what the page does with them.
   A fake NativeLog plugin stands in for Android. Run: NODE_PATH=$(npm root -g) node tests/native-log.test.js */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const { execFileSync } = require('child_process');
const root = path.resolve(__dirname, '..'), out = path.join(require('os').tmpdir(), 'lm_nlog_' + process.pid + '.html');
execFileSync('python3', [path.join(root, 'build.py')], { cwd: root, stdio: 'ignore' });
execFileSync('python3', [path.join(root, 'forge_features.py'), path.join(root, 'dist', 'Life_Max_RPG_v73_modular_build.html'), out], { cwd: root, stdio: 'ignore' });
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 600) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript(`
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));
    window.__calls=[];
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
  await p.goto('file://' + out); await p.waitForTimeout(3000);
  const r = await p.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), L = window.Capacitor.Plugins.NativeLog;
    const last = (k) => [...window.__calls].reverse().find(c => c[0] === 'log' && c[1] === k);
    const out = {};
    try { lmaThumbWarmBusy = false } catch (e) { }
    const S = lmaLoad(); S.nativeLog = true; S.nativeOverview = false; lmaSave();
    // a prefill in the web form (as Life Director or a link would do) carries over when the native screen opens
    document.getElementById('activityType').value = 'running'; activityPresetChanged(); document.getElementById('duration').value = 45;
    navigate('log'); await wait(400);
    const opened = last('open');
    out.opened = !!opened && opened[2].snapshot.protocol === 1 && opened[2].snapshot.presets.length >= 9 && opened[2].snapshot.attributes.length === 8;
    out.prefill = opened && opened[2].snapshot.draft.type === 'running' && opened[2].snapshot.draft.duration === '45' && opened[2].snapshot.draft.primary === 'endurance';
    out.previewMatchesPage = opened && opened[2].snapshot.preview.total === activityPreview(opened[2].snapshot.draft).total;
    // preview: the page answers with the new award, and the draft revision does not change (so typing is never replaced)
    const rev0 = opened[2].snapshot.draftRevision, d60 = Object.assign({}, opened[2].snapshot.draft, { duration: '60' });
    await L.__emit('command', { command: 'preview', payload: { draft: d60 }, requestId: 'r1' }); await wait(200);
    const reply = (id) => [...window.__calls].reverse().find(c => c[0] === 'log' && c[1] === 'update' && c[2].reply && c[2].reply.requestId === id);
    const up = reply('r1');
    out.preview = !!up && up[2].reply.ok === true && up[2].snapshot.preview.total === activityPreview(d60).total && up[2].snapshot.draftRevision === rev0;
    out.noCloseOnCommand = !window.__calls.some(c => c[0] === 'log' && c[1] === 'close');
    // submit: saved by the page's own function, toast, a fresh draft, and on to Overview
    const n0 = state.activities.length; let toasted = ''; const realToast = window.toast; window.toast = m => { toasted = String(m) };
    await L.__emit('command', { command: 'submit', payload: { draft: d60, operationId: 'op-1' }, requestId: 'r2' }); await wait(300);
    const sub = [...window.__calls].reverse().find(c => c[0] === 'log' && c[1] === 'update' && c[2].reply && c[2].reply.requestId === 'r2');
    out.saved = state.activities.length === n0 + 1 && state.activities[state.activities.length - 1].opId === 'op-1' && JSON.parse(localStorage.getItem(STORE)).activities.some(a => a.opId === 'op-1');
    out.reply = !!sub && sub[2].reply.ok === true && /Activity saved/.test(sub[2].reply.message) && sub[2].snapshot.draftRevision > rev0;
    out.toastAndNavigate = /Activity saved/.test(toasted) && lmaView() === 'dashboard';
    // the same operation again (a double tap that got through) records nothing new
    navigate('log'); await wait(400);
    await L.__emit('command', { command: 'submit', payload: { draft: d60, operationId: 'op-1' }, requestId: 'r3' }); await wait(300);
    out.oncePerOperation = state.activities.length === n0 + 1;
    // a failed save comes back as a failure, nothing is added, and the screen stays
    navigate('log'); await wait(400);
    const realSet = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return realSet.apply(this, arguments) };
    try { await L.__emit('command', { command: 'submit', payload: { draft: d60, operationId: 'op-2' }, requestId: 'r4' }); await wait(300) } finally { Storage.prototype.setItem = realSet }
    const bad = [...window.__calls].reverse().find(c => c[0] === 'log' && c[1] === 'update' && c[2].reply && c[2].reply.requestId === 'r4');
    out.failure = !!bad && bad[2].reply.ok === false && /Could not save/.test(bad[2].reply.message) && state.activities.length === n0 + 1 && lmaView() === 'log';
    // an unknown command is refused without closing anything
    await L.__emit('command', { command: 'nope', payload: {}, requestId: 'r5' }); await wait(200);
    const unk = [...window.__calls].reverse().find(c => c[0] === 'log' && c[1] === 'update' && c[2].reply && c[2].reply.requestId === 'r5');
    out.unknown = !!unk && unk[2].reply.ok === false;
    // the page cleans a hostile draft: unknown preset, absurd duration, made-up attribute, oversized notes
    const cl = NativeLogProjection.clean({ type: 'hack', duration: '99999', quality: '7', primary: 'root', secondary: 'x', notes: 'n'.repeat(5000) });
    out.clean = cl.type === 'custom' && cl.duration === '1440' && cl.quality === '1' && cl.primary === 'adventure' && cl.secondary === '' && cl.notes.length === 2000;
    window.toast = realToast;
    return out;
  });
  ok('opening sends a full projection (presets, eight attributes)', r.opened, r);
  ok('a prefill in the web form carries over to the native draft', r.prefill, r);
  ok('the preview in the projection is the page\'s own activityPreview', r.previewMatchesPage, r);
  ok('a preview command answers with the new award and does not replace the draft being typed', r.preview && r.noCloseOnCommand, r);
  ok('submit saves through recordActivity with the operation id, persists it, toasts and goes to Overview', r.saved && r.reply && r.toastAndNavigate, r);
  ok('the same operation id records nothing new', r.oncePerOperation, r);
  ok('a failed save is reported, adds nothing and keeps the screen', r.failure, r);
  ok('an unknown command is refused', r.unknown, r);
  ok('a hostile draft is cleaned by the page', r.clean, r);
  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); fs.unlinkSync(out); process.exit(fail ? 1 : 0);
})();
