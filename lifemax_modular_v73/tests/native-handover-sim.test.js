/* Hand-over simulation (CONTRACT A23, A28). Fake native plugins with seeded random delays stand in for Android, the page's timers
   run on virtual time (Playwright's clock), and many seeded tab-hopping sequences are replayed. The sequences and the fakes' delays
   are the same on every run; the page's CSS and Web Animations run on real time, so where they meet a hop can differ from run to
   run (how many commands find their screen showing, for one), and a failure is reproduced by replaying its seed. A hop is the
   page going to a tab, or a tap in a native screen that goes to another one (the Meals pill in Recipes, the Recipes pill in Food),
   or a command that changes the save while the screen stays (Life Director's bedtime handle let go and Change it: the page
   re-plans, saves and re-renders under the screen, whatever hand-over is in flight). A command is sent by a screen that shows: if
   Life Director's does not, the hop goes there and the command follows as soon as its screen shows, unless a later hop came first.
   After each sequence settles, exactly the screen of the last tab must be showing. A screen tapped while it alone showed, with
   nothing in flight, may not close before the tab the tap goes to shows (every tab here is native), and a hidden screen of the
   shared scaffold may receive no update (A24). Every Change it sent runs on the page exactly once: when it is applied the saved
   bedtime becomes the time asked for, when it is refused the save is untouched, one that ran while its screen showed is answered
   unless the screen was closed since, and a reply is the only one and says what the page did. A failing seed prints its sequence so it can be replayed.
   Needs Playwright (not part of the CI node tests).
   Run: NODE_PATH=$(npm root -g) node tests/native-handover-sim.test.js [seeds=80] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path'); const os = require('os');
const { execFileSync } = require('child_process');
const directorDay = require('./fixtures/director-day.js');
const root = path.resolve(__dirname, '..'), out = path.join(os.tmpdir(), 'lm_handover_sim_' + process.pid + '.html');
execFileSync('python3', [path.join(root, 'build.py')], { cwd: root, stdio: 'ignore' });
execFileSync('python3', [path.join(root, 'forge_features.py'), path.join(root, 'dist', 'Life_Max_RPG_v73_modular_build.html'), out], { cwd: root, stdio: 'ignore' });
const SEEDS = Number(process.argv[2]) || 80;
const WANT = { dashboard: 'overview', character: 'character', meals: 'food', log: 'log', recipes: 'recipes', plan: 'plan', 'life-director': 'director', media: 'media' };
// taps in a native screen: its plugin and screen, the view it shows and the view the tap goes to
const TAPS = { 'tap-meals': { plugin: 'NativeRecipes', screen: 'recipes', from: 'recipes', to: 'meals' }, 'tap-recipes': { plugin: 'NativeNutrition', screen: 'food', from: 'meals', to: 'recipes' } };
// commands sent from a native screen, which never leave its view: Life Director's bedtime moved between 10:30 and 11 PM
const COMMANDS = { 'drag-director': { plugin: 'NativeLifeDirector', screen: 'director', view: 'life-director' } };
const HOPS = [...Object.keys(WANT), 'food', ...Object.keys(TAPS), ...Object.keys(COMMANDS)]; // the Food tab lands on Meals or Recipes, whichever was visited last
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') });
  await p.addInitScript(`
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete',
      plan:{id:'athlete_foundation8',classId:'athlete',start:'2026-10-26',from:'2026-10-26',checks:{}}}));
    localStorage.setItem('lifemax_director_state_v1', ${JSON.stringify(JSON.stringify(directorDay.state()))});
    window.__seed=1;const rnd=()=>{let t=window.__seed+=0x6D2B79F5;t=Math.imul(t^t>>>15,t|1);t^=t+Math.imul(t^t>>>7,t|61);return((t^t>>>14)>>>0)/4294967296};
    window.__rnd=(a,b)=>a+rnd()*(b-a);window.__vis={overview:false,character:false,food:false,log:false,recipes:false,plan:false,director:false,media:false};window.__busy=0;window.__log=[];window.__faults=[];window.__tapped='';window.__cmds={sent:0,answered:0};window.__runs={};window.__replies={};window.__closes={};
    /* every reply: one per command, to a command that ran, saying what its run did */
    window.__replied=r=>{if(!/^sim-/.test(r.requestId||''))return;const run=window.__runs[r.requestId];if(window.__replies[r.requestId])fault('two replies to '+r.requestId);window.__replies[r.requestId]=true;
      if(!run)fault('a reply to '+r.requestId+', which never ran');else if(!!r.ok!==run.ok||(r.message||'')!==run.message)fault('the reply to '+r.requestId+' says ok='+r.ok+' ('+r.message+'), its run ok='+run.ok)};window.__onShow={};window.__t=()=>Math.round(performance.now());
    const wait=ms=>new Promise(r=>setTimeout(r,ms));
    /* Capacitor hands a plugin's calls to the main thread in the order they were made, so each fake does its calls one after another */
    /* Overview and Food only hide on a close that names the session they currently hold (as the Kotlin plugins do); the close of Character,
       Log, Plan, Life Director, Recipes and Library (ComposeTabPlugin) is unconditional, and an update reaching one of those while hidden is a fault */
    const fault=m=>{window.__faults.push(m);window.__log.push(window.__t()+' !! '+m)};
    const mk=(name,vis,sessions)=>{const ls={};let current='';let chain=Promise.resolve();const inOrder=fn=>{window.__busy++;const r=chain.then(fn);chain=r.catch(()=>{});return r.finally(()=>{window.__busy--})};const emit=(e,d)=>(ls[e]||[]).forEach(f=>{try{f(d)}catch(x){}});return {
      __emit:emit,__session:()=>current,
      addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
      open:a=>inOrder(async()=>{current=a&&a.session;window.__log.push(window.__t()+' '+name+' open>');await wait(window.__rnd(10,120));window.__vis[vis]=true;const shown=window.__onShow[vis];delete window.__onShow[vis];if(shown)setTimeout(shown,0);window.__log.push(window.__t()+' '+name+' open<')}),
      update:async(a)=>{if(a&&a.reply){window.__cmds.answered++;window.__replied(a.reply)}if(!sessions)await inOrder(async()=>{if(!window.__vis[vis])fault(name+' updated while hidden')})},
      close:a=>{window.__closes[vis]=(window.__closes[vis]||0)+1;return inOrder(async()=>{const stale=sessions&&(!a||a.session!==current);window.__log.push(window.__t()+' '+name+' close>'+(stale?' (stale session, ignored)':''));await wait(window.__rnd(5,40));
        if(!stale&&window.__vis[vis]){window.__vis[vis]=false;if(vis===window.__tapped&&!Object.values(window.__vis).some(Boolean))fault(name+' closed before the tab its tap went to showed')}window.__log.push(window.__t()+' '+name+' close<')}).then(()=>emit('closed',{session:a&&a.session}))},
      capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true}),
      handshake:async()=>({protocol:1,capabilities:['character.projection.v1','log.projection.v1','plan.projection.v1','director.today.v2','recipes.projection.v1','library.media.projection.v1']})}};
    window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:()=>true,getPlatform:()=>'android',Plugins:{NativeOverview:mk('overview','overview',true),NativeCharacter:mk('character','character',false),NativeNutrition:mk('food','food',true),NativeLog:mk('log','log',false),NativePlan:mk('plan','plan',false),NativeLifeDirector:mk('director','director',false),NativeRecipes:mk('recipes','recipes',false),NativeMedia:mk('media','media',false)},registerPlugin:()=>({}),convertFileSrc:u=>u};
  `);
  await p.goto('file://' + out);
  let loads = 0; p.on('load', () => loads++);   /* on Android a page load takes every native screen away (NativeScreens), so no tab switch may load one */
  for (let i = 0; i < 40; i++) { await p.clock.runFor(500); await p.waitForTimeout(60); }
  if (process.env.TRACE) await p.evaluate(() => { const T = window.NativeTabs, L = m => window.__log.push(window.__t() + ' ' + m); for (const k of ['leave', 'cancel', 'flush']) { const f = T[k]; T[k] = function () { const a = [...arguments].filter(x => typeof x !== 'function'); L('   NT.' + k + '(' + a.join(',') + ')' + (k === 'leave' ? ' covered=' + T.covered(a[1]) : '')); return f.apply(this, arguments) } } });
  /* every Change it the page runs, against the save: applied, the saved bedtime is the time asked for and it moved; refused, the save is
     untouched; and whether its screen showed as it ran, and how many closes the screen had been sent, so a lost reply is caught */
  await p.evaluate(() => {
    const C = NativeDirectorProjection.commands, apply = C.dialApply, bed = () => JSON.parse(localStorage.getItem('lifemax_director_state_v1')).availability.rules.find(r => r.id === 'sleep').startMin;
    C.dialApply = function (x) {
      const id = x && x.sim, before = bed(), r = apply.apply(this, arguments), after = bed();
      if (!id) return r;
      if (window.__runs[id]) window.__faults.push('Change it ' + id + ' ran twice');
      window.__runs[id] = { ok: !!r.ok, message: r.message || '', shown: window.__vis.director, closes: window.__closes.director || 0 };
      if (r.ok ? !(r.changed && after === x.minute && before !== after) : after !== before) window.__faults.push('Change it ' + id + ' to ' + x.minute + ': ok=' + r.ok + ', saved bedtime ' + before + ' -> ' + after);
      return r;
    };
  });
  await p.evaluate(() => { try { lmaThumbWarmBusy = false } catch (e) { } const L = lmaLoad(); L.nativeOverview = true; L.nativeCharacter = true; L.nativeNutrition = true; L.nativeLog = true; L.nativePlan = true; L.nativeDirector = true; L.nativeRecipes = true; L.nativeLibrary = true; lmaSave(); navigate('dashboard') });
  const settle = async (ms) => { for (let t = 0; t < ms; t += 50) { await p.clock.runFor(50); if (t % 400 === 0) await p.waitForTimeout(5) } await p.waitForTimeout(20) };
  await settle(4000);
  const failures = []; let hops = 0;
  const rng = (s => () => { let t = s += 0x6D2B79F5; t = Math.imul(t ^ t >>> 15, t | 1); t ^= t + Math.imul(t ^ t >>> 7, t | 61); return ((t ^ t >>> 14) >>> 0) / 4294967296 })(Number(process.env.SEED_BASE) || 12345);
  for (let seed = 1; seed <= SEEDS; seed++) {
    await p.evaluate(s => { window.__seed = s; window.__log = []; window.__faults = []; window.__tapped = ''; window.__sentNow = [] }, seed + (Number(process.env.SEED_BASE) || 0));
    const n = 3 + Math.floor(rng() * 9), seq = [];
    for (let i = 0; i < n; i++) seq.push({ to: HOPS[Math.floor(rng() * HOPS.length)], gap: Math.floor(rng() * 4) * 25 });
    let view = '';
    for (const step of seq) {
      // a tap is only possible on a screen that shows; the view after the hop is where it went (Food's screen ignores a tap it no longer owns)
      view = await p.evaluate(([v, tap, cmd]) => {
        window.__onShow = {}; // a command waiting for a screen that has not shown yet is dropped by any later hop
        if (cmd) {
          const send = () => {
            if (!window.__vis[cmd.screen] || lmaView() !== cmd.view) return;
            const bed = LifeDirector.native.today(0).dial.handles.find(h => h.rule === 'sleep' && h.end === 'start');
            window.__log.push(window.__t() + ' >> command dialApply in ' + cmd.screen + (bed ? ' ' + bed.key : ' (no handle)'));
            if (!bed) return;
            const id = 'sim-' + (++window.__cmds.sent);
            window.__sentNow.push(id);
            Capacitor.Plugins[cmd.plugin].__emit('command', { command: 'dialApply', payload: { key: bed.key, minute: bed.minute === 1350 ? 1380 : 1350, sim: id }, requestId: id });
          };
          if (window.__vis[cmd.screen]) send();
          else { window.__log.push(window.__t() + ' >> go ' + cmd.view + ', then a command once its screen shows'); window.__tapped = ''; window.__onShow[cmd.screen] = send; lmaNavTo(cmd.view, { fast: true }); }
          return lmaView();
        }
        if (!tap) { window.__log.push(window.__t() + ' >> go ' + v); window.__tapped = ''; lmaNavTo(v, { fast: true }); return lmaView() }
        const { plugin, screen, from, to } = tap, P = Capacitor.Plugins[plugin];
        if (!window.__vis[screen]) return lmaView();
        window.__log.push(window.__t() + ' >> tap ' + to + ' in ' + screen);
        const settled = !window.__busy && lmaView() === from && Object.keys(window.__vis).every(k => window.__vis[k] === (k === screen));
        window.__tapped = settled ? screen : '';
        P.__emit('action', plugin === 'NativeNutrition' ? { session: P.__session(), command: to } : { view: to });
        return lmaView();
      }, [step.to, TAPS[step.to], COMMANDS[step.to]]);
      if (step.gap) await settle(step.gap);
      hops++;
    }
    await settle(4500);
    const r = await p.evaluate(() => ({ vis: Object.keys(window.__vis).filter(k => window.__vis[k]).join('+'), busy: window.__busy, view: lmaView(), log: window.__log,
      faults: window.__faults.concat(window.__sentNow.filter(id => !window.__runs[id]).map(id => 'Change it ' + id + ' never ran'),
        /* a Change it that ran while its screen showed is answered, unless the screen was sent a close since (the kit replies to an open screen only) */
        window.__sentNow.filter(id => { const run = window.__runs[id]; return run && run.shown && !window.__replies[id] && (window.__closes.director || 0) === run.closes; })
          .map(id => 'no reply to ' + id + ', which ran while its screen showed, and the screen was never closed')) }));
    const want = WANT[view];
    if (r.vis !== want || r.busy !== 0 || r.view !== view || r.faults.length) failures.push({ seed, seq: seq.map(s => s.to + '+' + s.gap).join(' '), showing: r.vis || 'NONE', want, busy: r.busy, view: r.view, faults: r.faults, log: r.log });
  }
  const cmds = await p.evaluate(() => Object.assign({ applied: Object.values(window.__runs).filter(r => r.ok).length, shown: Object.values(window.__runs).filter(r => r.shown).length }, window.__cmds));
  console.log((failures.length ? 'FAIL' : 'PASS') + ' ' + SEEDS + ' seeded sequences (' + hops + ' hops, ' + cmds.sent + ' Life Director Change its mid-hop, each run once, ' + cmds.applied + ' applied and saved, ' + cmds.shown + ' ran on a showing screen, ' + cmds.answered + ' answered on it): the last tab alone is showing after each');
  if (process.env.TRACE) failures.slice(0, 2).forEach(f => console.log('  trace seed ' + f.seed + ':\n    ' + f.log.join('\n    ')));
  failures.slice(0, 6).forEach(f => console.log('  seed ' + f.seed + ': ' + f.seq + ' -> showing ' + f.showing + ', wanted ' + f.want + (f.busy ? ' (busy ' + f.busy + ')' : '') + ' view=' + f.view + (f.faults.length ? ' faults: ' + f.faults.join('; ') : '')));
  console.log((loads ? 'FAIL' : 'PASS') + ' no tab switch loaded a page (' + loads + ' loads)');
  if (errs.length) console.log('FAIL script errors', errs.slice(0, 3));
  await b.close(); fs.unlinkSync(out); process.exit(failures.length || errs.length || loads ? 1 : 0);
})();
