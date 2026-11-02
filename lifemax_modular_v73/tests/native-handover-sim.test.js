/* Hand-over simulation (CONTRACT A23, A28). Fake native plugins with seeded random delays stand in for Android, the page's timers
   run on virtual time (Playwright's clock), and many seeded tab-hopping sequences are replayed. The sequences and the fakes' delays
   are the same on every run; the page's CSS and Web Animations run on real time, so where they meet a hop can differ from run to
   run (how many commands find their screen showing, for one), and a failure is reproduced by replaying its seed. A hop is the
   page going to a tab, or a tap in a native screen that goes to another one (the Meals pill in Recipes, the Recipes pill in Food,
   a native tab's gear to Settings), or a tap on a native tab's switch in native Settings (off and on again: the page's own
   setNativeOverviewEnabled, which must not leave Overview over Settings, nor keep it from the dashboard), or a command that
   changes the save while the screen stays (Life Director's bedtime handle let go and Change it: the page re-plans, saves and
   re-renders under the screen, whatever hand-over is in flight). A command is sent by a screen that shows: if Life Director's does
   not, the hop goes there and the command follows as soon as its screen shows, unless a later hop came first.
   A hop can also be a native tab swipe: the screen on top sends a committed swipe toward the next or previous tab of the last bar
   scene it took, as native does on a release past the catch, and only where native would (that scene says the swipe is on, from
   its own view, toward a native screen). That scene may be out of date, on purpose: another hop moves the tab you are on along the
   bar. The page must answer each swipe once, before it navigates; one it takes must go to the tab next to the source on the bar
   as it is now, while the swipe is on, and its screen must show (CONTRACT A23).
   Phase 2 of the swipe (plans/TAB_SWIPE.md §5): the fakes' swipes say they reveal a web tab, as a phase 2 APK's do, so a swipe
   also goes toward a tab the page draws (Cat, Quests: WANT none showing; the Overview's live shell), which the page answers "web"
   under the same rules; and a hop web-swipe-next/prev is the page's own swipe from a web page (or the live shell's web body) onto
   the next tab, which a native screen may slide in for (enter). Neither swipe-reveal nor native-enter may be left on once settled.
   After each sequence settles, exactly the screen of the last tab must be showing. A screen tapped while it alone showed, with
   nothing in flight, may not close before the tab the tap goes to shows (every tab here is native), and a hidden screen of the
   shared scaffold may receive no update (A24). Every Change it sent runs on the page exactly once: when it is applied the saved
   bedtime becomes the time asked for, when it is refused the save is untouched, one that ran while its screen showed is answered
   unless the screen was closed since, and a reply is the only one and says what the page did. A failing seed prints its sequence so it can be replayed.
   The Overview has two native drawings (plans/OVERVIEW_BODY.md §5, ADR 0012): the live shell (NativeOverview) and the cards
   (NativeOverviewCards, beta). Every seed list runs in four modes, each on a fresh page: "off" (the shell draws the Overview),
   "on" (the cards do), "mixed" (a hop toggle-cards flips the cards' switch from native Settings, the only place it exists
   besides the page's Settings) and "failing" (a cards plugin whose open and update reject: the cards give up and the shell must
   hold the Overview alone). The cards add hops: tap-plan (the glance's plan command: Life Director), tap-move (a move's {view}
   action), swipes from and to the cards, and celebrate (an XP celebration the page hands the cards, or plays itself while they
   are away, whatever hop is in flight). And faults: both drawings showing once the fakes are quiet, the shell opened while the
   cards were wanted, and the screen you left closed before either Overview drawing showed (nothing in flight).
   The Journal (plans/JOURNAL_AND_LIBRARY_SHEETS.md §5, CONTRACT A35) has two modes of its own, with the native Journal on and a
   journal holding canaries: "lock" (the Journal lock on from the start; hops add the Library tab, Media's switch to the Journal
   and back, the app stopped and started again with the page seeing it hidden or missing that, the lock switched on or off in
   Settings while the Journal is away, and unlock from the lock card, ok or cancelled at random, as the page's own prompt on
   arrival is) and "reload" (the same, with page reloads between hops: a reload takes every screen away and starts a new page
   session, while the native plugin outlives it). The fake NativeJournal models native as the plan has it, in Node so that it
   outlives a reload: an open frame or the lock card shown, data dropped whenever the screen stops being drawn, and on app stop
   with lock.on the lock card and a gate refusing that page session's open frames until its withheld frame arrives (an open
   frame from a new session, or one saying the lock is off, clears it). Faults: one of the Journal's open builders running while
   the lock keeps the Journal closed, a withheld frame with a canary, a hidden Journal screen holding open data, open data
   showing once settled while locked, the lock card stuck once settled with the lock off, and the general ones (an update while
   hidden, the Journal not showing on its view). JMUT=projection|fake runs a mutation that must fail: the projection without its
   lock branch, a fake native that drops data only when lock.on. JMUT=onchange (the page without its lock subscription) passes
   here: every relock also draws the Journal, and the kit's render hook sends the withheld frame within 100 ms; what only the
   subscription does (the lock turned off, which draws nothing) is checked by tests/native-journal.test.js.
   The audio dock (plans/JOURNAL_AND_LIBRARY_SHEETS.md phase 4) has a mode of its own, "dock" (the shell draws the Overview, as in
   "off"): the native audiobook controls are on and two books are stored. Hops add the page playing a book (swapping the one
   playing), a dock tap (toggle, skip, minimise, speed, close and seek, only while the dock shows) and Media's own Play command
   (only while its screen shows). The fake NativeAudio models the shell honestly: ONE dock, shown while the owner's switch is on,
   the page's player shows and some native screen is up, and told to the page ({shown, height}) whenever that changes. Faults, once
   settled: the dock showing when it should not, or not when it should (the book's controls vanish), the page's player and the dock
   both showing, the page's class or padding disagreeing with the dock, a push the strict decoder would reject, and the pushed state
   disagreeing with the player. Media must show over a loaded book (no web wall). DMUT=blocked|undocked runs a mutation that must
   fail: the Library kit blocking on a loaded book though the dock carries it, and the page ignoring the dock event.
   Writing (phase 2, library.journal.write.v1, ADR 0013): the fake Journal lists the write capability, so the page says
   edit:"native", and the Journal modes add the notebook field's hops: type (an autosave, final:false, into today's reflection,
   with its base the text the page holds) and leave-field (the final write, which may pay). The fake native also flushes what it has
   typed when the page closes its screen and when the app stops (as the screen does, even if the page has locked meanwhile).
   Faults: an autosave that paid XP or came back with a celebration, XP paid for a text more than once a day, a write's reply to a
   locked Journal carrying a private word, and a flush that did not store. JMUT=autosave runs a mutation that must fail: the
   autosave paying like a final write.
   Needs Playwright (not part of the CI node tests).
   Run: NODE_PATH=$(npm root -g) node tests/native-handover-sim.test.js [seeds=80]   (MODES=off,on,mixed,failing,lock,reload,dock by default) */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path'); const os = require('os');
const { execFileSync } = require('child_process');
const directorDay = require('./fixtures/director-day.js');
const { serve } = require('./current-page.js');
const root = path.resolve(__dirname, '..'), out = path.join(os.tmpdir(), 'lm_handover_sim_' + process.pid + '.html');
execFileSync('python3', [path.join(root, 'build.py')], { cwd: root, stdio: 'ignore' });
execFileSync('python3', [path.join(root, 'forge_features.py'), path.join(root, 'dist', 'Life_Max_RPG_v73_modular_build.html'), out], { cwd: root, stdio: 'ignore' });
/* a mutation the Journal's modes must catch (JMUT): the page built without the projection's lock branch or the lock subscription */
const MUTATIONS = { projection: ['if (locked()) return withheldFrame(); // nothing below runs while locked', ''], onchange: ['lock()?.onChange(() => {', 'void (() => {'] ,
    autosave: ['{ award: p.final === true, soft: false }', '{ award: true, soft: false }'] };
const DMUTATIONS = { blocked: ['(audioBusy() && !window.NativeAudioDock?.ready()) ||', 'audioBusy() ||'], undocked: ['root.classList.toggle("audio-docked", on);', ';'] };
if (DMUTATIONS[process.env.DMUT]) { const [a, b2] = DMUTATIONS[process.env.DMUT], html = fs.readFileSync(out, 'utf8'); if (!html.includes(a)) throw Error('mutation ' + process.env.DMUT + ' not found'); fs.writeFileSync(out, html.replace(a, b2)) }
/* mutations the prime checks must catch (PMUT, phase 3): the page opening a neighbour in place of priming it, updating the primed
   screen as if it were open, or handing the bars' scene to the screen it primes */
const PRIME_CALL = 'const r = await plugin.prime({ snapshot: data });';
const PRIME_MUTATIONS = { open: [PRIME_CALL, 'await plugin.open({ snapshot: data, navScene: await navScene() }); const r = { primed: true };'], update: [PRIME_CALL, 'const r = await plugin.prime({ snapshot: data }); await plugin.update({ snapshot: data, navScene: await navScene() });'], chrome: [PRIME_CALL, 'const r = await plugin.prime({ snapshot: data, navScene: await navScene() });'] };
if (PRIME_MUTATIONS[process.env.PMUT]) { const [a, b2] = PRIME_MUTATIONS[process.env.PMUT], html = fs.readFileSync(out, 'utf8'); if (!html.includes(a)) throw Error('mutation ' + process.env.PMUT + ' not found'); fs.writeFileSync(out, html.replace(a, b2)) }
if (MUTATIONS[process.env.JMUT]) { const [a, b2] = MUTATIONS[process.env.JMUT], html = fs.readFileSync(out, 'utf8'); if (!html.includes(a)) throw Error('mutation ' + process.env.JMUT + ' not found'); fs.writeFileSync(out, html.replace(a, b2)) }
const SEEDS = Number(process.argv[2]) || 80;
const WANT = { dashboard: 'overview', character: 'character', meals: 'food', log: 'log', recipes: 'recipes', plan: 'plan', 'life-director': 'director', media: 'media', settings: 'settings', pet: '', quests: '' }; // '': the page draws it, no screen shows
// taps in a native screen: its plugin and screen, the view it shows and the view the tap goes to
const TAPS = { 'tap-meals': { plugin: 'NativeRecipes', screen: 'recipes', from: 'recipes', to: 'meals' }, 'tap-recipes': { plugin: 'NativeNutrition', screen: 'food', from: 'meals', to: 'recipes' },
  'tap-gear': { plugin: 'NativeCharacter', screen: 'character', from: 'character', to: 'settings' },
  'tap-switch': { plugin: 'NativeSettings', screen: 'settings', from: 'settings', commands: [{ id: 'nativeOverview', on: false }, { id: 'nativeOverview', on: true }] } };
// a native tab swipe toward the next or previous tab, and the tab you are on moved one place along the bar
const SWIPES = { 'swipe-next': 1, 'swipe-prev': -1 }, BAR = { 'bar-earlier': -1, 'bar-later': 1 }, WEB_SWIPES = { 'web-swipe-next': 1, 'web-swipe-prev': -1 };
// commands sent from a native screen, which never leave its view: Life Director's bedtime moved between 10:30 and 11 PM
const COMMANDS = { 'drag-director': { plugin: 'NativeLifeDirector', screen: 'director', view: 'life-director' } };
// the Overview cards' taps: the glance's plan command (to Life Director) and a move's {view} action (to Log)
const CARDS = { 'tap-plan': { command: 'plan', payload: { act: '', id: '' }, to: 'life-director' }, 'tap-move': { action: { view: 'log' }, to: 'log' } };
const HOPS = [...Object.keys(WANT), 'food', ...Object.keys(TAPS), ...Object.keys(SWIPES), ...Object.keys(WEB_SWIPES), ...Object.keys(BAR), ...Object.keys(COMMANDS)]; // the Food tab lands on Meals or Recipes, whichever was visited last
const MODES = (process.env.MODES || 'off,on,mixed,failing,lock,reload,dock').split(',');
// the dock mode's hops: the page plays a book, a tap in the dock, Media's own Play command
const DOCKHOPS = { 'play': 1, 'dock-cmd': 1, 'tap-play': 1 };
// the Journal's modes: Media's switch to the Journal and the Journal's back, and the lock's hops (the app stopped and started, the
// lock switched in Settings, unlock from the lock card), plus page reloads in "reload"
const JTAPS = { 'tap-journal': { plugin: 'NativeMedia', screen: 'media', from: 'media', to: 'history' }, 'tap-media': { plugin: 'NativeJournal', screen: 'journal', from: 'history', to: 'media' } };
const JLOCK = { 'relock-vis': 1, 'lock-toggle': 1, 'unlock': 1, 'type': 1, 'leave-field': 1 };
const journalMode = mode => mode === 'lock' || mode === 'reload';
// a mode's hops: the cards' taps where the cards can be wanted, an XP celebration in every mode, the cards' switch in mixed
const hopsOf = mode => journalMode(mode)
  ? [...Object.keys(WANT), 'history', 'history', 'history', 'food', 'library', 'library', ...Object.keys(TAPS), ...Object.keys(JTAPS), ...Object.keys(JTAPS), ...Object.keys(SWIPES), 'celebrate',
      'relock-vis', 'relock-vis', 'relock-vis', 'lock-toggle', 'unlock', 'unlock', 'type', 'type', 'type', 'leave-field', 'leave-field', ...(mode === 'reload' ? ['reload', 'reload'] : [])]
  : HOPS.concat(mode === 'off' || mode === 'dock' ? [] : Object.keys(CARDS), 'celebrate', mode === 'mixed' ? ['toggle-cards', 'toggle-cards'] : [],
    mode === 'dock' ? ['play', 'play', 'dock-cmd', 'dock-cmd', 'dock-cmd', 'tap-play', 'library', 'library'] : []);
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--autoplay-policy=no-user-gesture-required'] });
  let failed = false;
  for (const mode of MODES) failed = (await run(b, mode)) || failed;
  await b.close(); fs.unlinkSync(out); process.exit(failed ? 1 : 0);
})();
async function run(b, mode) {
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') });
  /* the native Journal as the plan has it (JournalFrame Open, Locked or Resting; the stop gate), kept here so it outlives a page load */
  const jn = { state: 'resting', session: '', relockFor: null, lockOn: false, shown: false, refused: 0, cleared: 0, stops: 0, open: 0, withheld: 0 };
  await p.exposeFunction('__jnModel', (op, x) => {
    if (op === 'frame') {
      jn.shown = true;
      jn[x.withheld ? 'withheld' : 'open']++;
      if (x.withheld) { jn.state = 'locked'; jn.relockFor = null; jn.lockOn = true; jn.session = x.session }
      else if (jn.relockFor && jn.relockFor === x.session && x.lockOn) jn.refused++; // the gate: an open frame of the session that stopped
      else { if (jn.relockFor) jn.cleared++; jn.relockFor = null; jn.state = 'open'; jn.lockOn = x.lockOn; jn.session = x.session }
    } else if (op === 'rest') { if (!(process.env.JMUT === 'fake' && !jn.lockOn)) jn.state = 'resting'; jn.shown = false }
    else if (op === 'stop') { if (jn.shown && jn.state === 'open' && jn.lockOn) { jn.state = 'locked'; jn.relockFor = jn.session; jn.stops++ } }
    return Object.assign({}, jn);
  });
  // the starting save, on the first load only in the Journal's modes, whose reloads keep what the page stored
  await p.addInitScript(`if(!${journalMode(mode)}||!localStorage.getItem('lifemax_rpg_prototype_v1')){
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete',
      plan:{id:'athlete_foundation8',classId:'athlete',start:'2026-10-26',from:'2026-10-26',checks:{}}}));
    localStorage.setItem('lifemax_director_state_v1', ${JSON.stringify(JSON.stringify(directorDay.state()))});}
    window.__seed=1;const rnd=()=>{let t=window.__seed+=0x6D2B79F5;t=Math.imul(t^t>>>15,t|1);t^=t+Math.imul(t^t>>>7,t|61);return((t^t>>>14)>>>0)/4294967296};
    window.__rnd=(a,b)=>a+rnd()*(b-a);window.__mode=${JSON.stringify(mode)};window.__vis={overview:false,cards:false,character:false,food:false,log:false,recipes:false,plan:false,director:false,media:false,settings:false,journal:false};window.__busy=0;window.__composed={};window.__primes={};window.__log=[];window.__faults=[];window.__tapped='';window.__cmds={sent:0,answered:0};window.__cardHops={taps:0,toggles:0,celebrations:0,shown:0,opens:0};window.__runs={};window.__replies={};window.__closes={};
    /* every reply: one per command, to a command that ran, saying what its run did */
    window.__replied=r=>{if(!/^sim-/.test(r.requestId||''))return;window.__cmds.answered++;const run=window.__runs[r.requestId];if(window.__replies[r.requestId])fault('two replies to '+r.requestId);window.__replies[r.requestId]=true;
      if(!run)fault('a reply to '+r.requestId+', which never ran');else if(!!r.ok!==run.ok||(r.message||'')!==run.message)fault('the reply to '+r.requestId+' says ok='+r.ok+' ('+r.message+'), its run ok='+run.ok)};window.__onShow={};window.__t=()=>Math.round(performance.now());
    /* swipes: the screens in the order they last showed (the last one on top), the swipe waiting for its answer (answered: '' or
       its outcome), the view of the last swipe the page took, and what the page answered */
    window.__want=${JSON.stringify(WANT)};window.__z=[];
    /* the screen a view wants: the Overview's is the cards while they are wanted (on; mixed with the switch on), else the shell */
    window.__wantOf=v=>v==='history'?(window.__mode==='lock'||window.__mode==='reload'?'journal':''):v==='dashboard'?(window.__mode==='on'||window.__mode==='mixed'&&!!lmaLoad().nativeOverviewCards?'cards':'overview'):window.__want[v];
    /* both Overview drawings showing is a fault once the fakes have been quiet a while (a hand-over overlaps them for a moment) */
    let both=0;const bothCheck=()=>{if(window.__busy||!(window.__vis.overview&&window.__vis.cards))return;clearTimeout(both);both=setTimeout(()=>{if(!window.__busy&&window.__vis.overview&&window.__vis.cards)fault('the shell and the cards both show')},300)};window.__swipe=null;window.__accepted='';window.__tokens=0;window.__answers={native:0,web:0,refused:0};window.__enters=0;
    const wait=ms=>new Promise(r=>setTimeout(r,ms));
    /* Capacitor hands a plugin's calls to the main thread in the order they were made, so each fake does its calls one after another */
    /* Overview and Food only hide on a close that names the session they currently hold (as the Kotlin plugins do); the close of Character,
       Log, Plan, Life Director, Recipes, Library and Settings (ComposeTabPlugin) is unconditional, and an update reaching one of those while hidden is a fault */
    const fault=window.__fault=m=>{window.__faults.push(m);window.__log.push(window.__t()+' !! '+m)};
    /* the native Journal's notebook field (phase 2): what is typed waits in a draft and is sent as the screen would send it. An autosave
       (final:false) may store the words and nothing more; a final write may pay, once for a text; a reply to a locked Journal names no word */
    window.__draft=null;window.__writes={autosaves:0,finals:0,paid:0,flushes:0};
    const nid=p=>p+(window.__rid=(window.__rid||0)+1);
    window.__jsend=(final,d)=>{const id=nid(final?'jwf':'jwa');window.__wsent=window.__wsent||{};window.__wsent[id]={final,d:Object.assign({},d)};
      Capacitor.Plugins.NativeJournal.__emit('command',{command:'reflect',payload:{d:d.d,text:d.text,base:d.base,final},requestId:id})};
    window.__wreply=(r,snap)=>{const s=(window.__wsent||{})[r.requestId];if(!s)return;
      if(!s.final&&(r.paid||(r.celebrations&&r.celebrations.length)))fault('an autosave paid XP or came back with a celebration');
      if(snap&&snap.withheld&&/Canary/.test(JSON.stringify(r)))fault('a write reply to a locked Journal carried a private word');
      if(r.ok){window.__writes[s.final?'finals':'autosaves']++;if(r.paid)window.__writes.paid++;if(window.__draft&&window.__draft.d===s.d.d&&!s.final)window.__draft.base=s.d.text}};
    window.__flush=why=>{const d=window.__draft;if(!d)return;window.__log.push(window.__t()+' >> flush the typed words ('+why+')');window.__writes.flushes++;window.__jsend(true,d);window.__draft=null};
    /* A swipe's answer (swipeSettle) is checked the moment the page makes it, as it then goes to Android ahead of anything the page
       does next: the swipe it answers was not answered before; a swipe taken is answered before the page navigates, while the swipe
       is on, toward the tab next to the source on the bar as it is now; and the destination's open is not asked for before it. */
    const checkAnswer=a=>{const s=window.__swipe;if(!s||!a||a.token!==s.token)return;
      if(s.answered){fault('the page answered '+s.token+' twice');return}
      s.answered=a.outcome;window.__answers[a.outcome]=(window.__answers[a.outcome]||0)+1;if(a.outcome!=='native'&&a.outcome!=='web')return;
      if(lmaView()!==s.from)fault('the page answered '+s.token+' after it had navigated');
      if(!NativeSwipe.state()?.on)fault('the page took '+s.token+' while the swipe was off');
      const bar=lmaTabList(),i=bar.findIndex(b=>b.dataset.view===lmaTabKey(s.from));
      if(i<0||bar[i+s.dir]?.dataset.view!==s.to)fault('the page took '+s.token+' to '+s.to+', which is not next to '+s.from+' on the bar');
      window.__accepted=lmaResolve(s.to)};
    const mk=(name,vis,sessions,fails,model)=>{const ls={};let current='';let chain=Promise.resolve();const inOrder=fn=>{window.__busy++;const r=chain.then(fn);chain=r.catch(()=>{});return r.finally(()=>{window.__busy--;bothCheck()})};const emit=(e,d)=>(ls[e]||[]).forEach(f=>{try{f(d)}catch(x){}});
      let scene=null;const take=a=>{const s=a&&(a.navScene||a.chromeScene);if(s)scene=s}; // the last bar scene this screen took, which its swipes go by
      return {
      __emit:emit,__session:()=>current,__scene:()=>scene,
      addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
      open:a=>{const s=window.__swipe;if(a&&a.enter)window.__enters++;if(s&&!s.answered&&s.toVis===vis)fault(name+' was asked to open before the page answered '+s.token);take(a);
        if(vis==='overview'&&window.__wantOf('dashboard')==='cards')fault('the shell was opened while the cards were wanted');
        if(vis==='cards'){window.__cardHops.opens++;if(window.__cardsOpen)fault('the cards were asked to open twice with no close between (two owners of the dashboard)');window.__cardsOpen=true}
        return inOrder(async()=>{current=a&&a.session;window.__log.push(window.__t()+' '+name+' open>');await wait(window.__rnd(10,120));if(fails){window.__log.push(window.__t()+' '+name+' open rejected');throw Error('decoder rejected')}window.__vis[vis]=true;window.__composed[vis]=true;window.__dockRecompute&&window.__dockRecompute();if(vis==='cards')window.__cardHops.shown++;window.__z=window.__z.filter(k=>k!==vis).concat(vis);const shown=window.__onShow[vis];delete window.__onShow[vis];if(shown)setTimeout(shown,0);if(model)await model.frame(a);window.__log.push(window.__t()+' '+name+' open<')})},
      update:async(a)=>{take(a);if(a&&a.reply){window.__replied(a.reply);if(/^jw/.test(a.reply.requestId||''))window.__wreply(a.reply,a.snapshot)}if(!sessions)await inOrder(async()=>{if(!window.__vis[vis]){fault(name+' updated while hidden');return}if(fails)throw Error('decoder rejected');if(model)await model.frame(a)})},
      /* prime (shell.prime.v1): composes the screen ahead, hidden. It takes only the projection (no bars), shows nothing and is not open;
         a screen composed already (opened before) answers composed. A hidden screen takes no update (the fault above). */
      prime:a=>{if(a&&(a.navScene||a.chromeScene))fault(name+' was primed with the bars\\' scene');
        return inOrder(async()=>{if(window.__composed[vis])return {primed:false,reason:'composed'};await wait(window.__rnd(10,120));window.__composed[vis]=true;window.__primes[vis]=(window.__primes[vis]||0)+1;window.__log.push(window.__t()+' '+name+' primed');return {primed:true,javaKb:1,nativeKb:2,ms:3}})},
      swipeSettle:a=>{checkAnswer(a);return inOrder(async()=>{window.__log.push(window.__t()+' '+name+' swipeSettle '+(a&&a.token)+' '+(a&&a.outcome)+(a&&a.reason?' ('+a.reason+')':''))})},
      close:a=>{window.__closes[vis]=(window.__closes[vis]||0)+1;if(vis==='cards')window.__cardsOpen=false;return inOrder(async()=>{const stale=sessions&&(!a||a.session!==current);window.__log.push(window.__t()+' '+name+' close>'+(stale?' (stale session, ignored)':''));await wait(window.__rnd(5,40));
        if(!stale&&window.__vis[vis]){if(name==='journal'&&window.__flush)window.__flush('the screen closed');window.__vis[vis]=false;window.__dockRecompute&&window.__dockRecompute();if(model)await model.rest();if(vis===window.__tapped&&!Object.values(window.__vis).some(Boolean))fault(name+' closed before the tab its tap went to showed')}window.__log.push(window.__t()+' '+name+' close<')}).then(()=>emit('closed',{session:a&&a.session}))},
      capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true,swipe:2}),
      handshake:async()=>({protocol:1,capabilities:['character.projection.v1','log.projection.v1','plan.projection.v1','director.today.v2','recipes.projection.v1','library.media.projection.v1','library.journal.projection.v1','library.journal.write.v1','settings.projection.v1','overview.cards.v1','shell.swipe.v1','shell.swipe.v2','shell.prime.v1']})}};
    /* the native Journal's frames go to the model (in Node): a withheld frame may carry no canary, and a screen that stops being
       drawn must hold no open data */
    const jmodel={frame:async a=>{const s=a&&a.snapshot;if(!s)return;const json=JSON.stringify(s),canary=/Canary/.test(json);if(s.withheld&&canary)fault('a withheld Journal frame carries a canary');
        const st=await window.__jnModel('frame',{session:s.webSessionId,withheld:!!s.withheld,lockOn:!!(s.lock&&s.lock.on),canary});window.__log.push(window.__t()+' journal model '+st.state+(st.relockFor?' (gated)':''))},
      rest:async()=>{const st=await window.__jnModel('rest');if(st.state==='open')fault('a hidden Journal screen holds open data')}};
    /* Android's prompt: ok or cancelled at random, after a moment */
    const bio={status:async()=>({available:true,kind:'ok'}),authenticate:async()=>{await wait(window.__rnd(30,300));const ok=window.__rnd(0,1)<0.6;window.__log.push(window.__t()+' prompt '+(ok?'ok':'cancelled'));return ok?{ok:true}:{ok:false,cancelled:true}},secure:async()=>{}};
    /* the shell's dock as the plan has it: ONE dock, on while the owner's switch is on, the page's player shows and some native screen
       is up; told to the page whenever that changes. Strict about what the page pushes. */
    const dm=window.__dock={enabled:false,state:null,shown:false,height:0,pushes:0};const audioLs={};
    window.__dockRecompute=()=>{const s=dm.state,up=Object.values(window.__vis).some(Boolean),shown=dm.enabled&&!!s&&s.visible&&up,height=shown?(s.minimized?68:152):0;
      if(shown!==dm.shown||height!==dm.height){dm.shown=shown;dm.height=height;(audioLs.dock||[]).forEach(f=>{try{f({shown,height})}catch(x){}})}};
    const audio={__emit:(e,d)=>Promise.all((audioLs[e]||[]).map(f=>f(d))),addListener:async(e,f)=>{(audioLs[e]=audioLs[e]||[]).push(f);return {remove(){}}},
      handshake:async()=>({protocol:1,capabilities:['shell.audio.v1']}),
      configure:async c=>{dm.enabled=!!c.enabled;if(!c.enabled)dm.state=null;window.__dockRecompute()},
      state:async s=>{dm.pushes++;for(const k of ['v','visible','minimized','loading','mediaId','title','sub','playing','position','duration','rate','rates','light'])if(!(k in s))fault('NativeAudio.state without '+k);
        if(s.v!==1)fault('NativeAudio.state v='+s.v);if(s.visible&&!(s.tokens&&s.tokens.accent))fault('a visible dock state without the palette');if(!(s.position>=0&&s.duration>=0&&s.rate>0))fault('a state out of range');
        dm.state=s;window.__dockRecompute()}};
    window.__visState='visible';Object.defineProperty(document,'visibilityState',{get:()=>window.__visState,configurable:true});
    window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:()=>true,getPlatform:()=>'android',Plugins:{NativeOverview:mk('overview','overview',true),NativeOverviewCards:mk('cards','cards',false,window.__mode==='failing'),NativeCharacter:mk('character','character',false),NativeNutrition:mk('food','food',true),NativeLog:mk('log','log',false),NativePlan:mk('plan','plan',false),NativeLifeDirector:mk('director','director',false),NativeRecipes:mk('recipes','recipes',false),NativeMedia:mk('media','media',false),NativeSettings:mk('settings','settings',false),NativeJournal:mk('journal','journal',false,false,jmodel),NativeAudio:window.__mode==='dock'?audio:undefined,BiometricLock:bio},registerPlugin:()=>({}),convertFileSrc:u=>u};
  `);
  // the Journal's modes reload the page: served over http, since a reloaded file:// page can lose what it stored (current-page.js)
  const server = journalMode(mode) ? await serve(out) : null;
  await p.goto(server ? server.url : 'file://' + out);
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
  await p.evaluate(() => { try { lmaThumbWarmBusy = false } catch (e) { } const L = lmaLoad(); L.nativeOverview = true; L.nativeCharacter = true; L.nativeNutrition = true; L.nativePlan = true; L.nativeDirector = true; L.nativeRecipes = true; L.nativeLibrary = true; L.nativeSettings = true; L.nativeSwipe = true; L.nativeOverviewCards = window.__mode === 'on' || window.__mode === 'failing'; lmaSave(); navigate('dashboard') }); // Log runs on its default (on)
  if (mode === 'dock') {
    // ten seconds of silence, twice, as the page stores a book (IndexedDB); the owner's switch for the dock turned on as in Settings
    await p.evaluate(async () => {
      const wav = () => { const n = 8000 * 10, buf = new ArrayBuffer(44 + n), v = new DataView(buf); const w = (o, s) => [...s].forEach((c, i) => v.setUint8(o + i, c.charCodeAt(0)));
        w(0, 'RIFF'); v.setUint32(4, 36 + n, true); w(8, 'WAVEfmt '); v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true); v.setUint32(24, 8000, true); v.setUint32(28, 8000, true); v.setUint16(32, 1, true); v.setUint16(34, 8, true);
        w(36, 'data'); v.setUint32(40, n, true); new Uint8Array(buf, 44).fill(128); return new Blob([buf], { type: 'audio/wav' }) };
      for (const [id, title] of [['sim_book1', 'Book one'], ['sim_book2', 'Book two']]) {
        await audioDbPut(id, { blob: wav(), name: id + '.m4b', type: 'audio/wav', size: 80044, updatedAt: '2026-01-01' });
        state.mediaItems.push({ id, title, type: 'Audiobook', creator: '', status: 'progress', link: '', notes: '', audio: { name: id + '.m4b', size: 80044, type: 'audio/wav', position: 0, duration: 10 } });
      }
      state.mediaSeededV1 = true; writeSave();
      await setNativeAudioEnabled(true);
    });
  }
  const settle = async (ms) => { for (let t = 0; t < ms; t += 50) { await p.clock.runFor(50); if (t % 400 === 0) await p.waitForTimeout(5) } await p.waitForTimeout(20) };
  await settle(4000);
  /* every tab once before the seeds: a bar scene's first capture rasterises its images in real time, which on the virtual clock can
     outlast the hand-over's 2.5 s safety net and close the screen being left early (a cold first visit, not a hand-over fault) */
  const warmTabs = async () => {
    for (const v of ['character', 'food', 'log', 'plan', 'library', 'life-director', 'recipes', 'pet', 'quests', 'settings', 'dashboard']) {
      await p.evaluate(v => lmaNavTo(v, { fast: true }), v); await settle(1500);
    }
    // the visits only warmed the page: native's screens count as never composed, so the idle prime has screens to compose ahead
    await p.evaluate(() => { window.__composed = {} });
  };
  await warmTabs();
  /* the Journal's modes: what every page load needs (no thumbnail pass; a fault whenever one of the Journal's open builders runs while
     the lock keeps the Journal closed), then the switch on, a journal with canaries and the lock on */
  const journal = journalMode(mode);
  const afterLoad = () => p.evaluate(() => {
    try { lmaThumbWarm = async () => { }; lmaThumbWarmBusy = false } catch (e) { }
    window.__sentNow = [];
    for (const name of ['jcHeroData', 'jcListData', 'journalDayData', 'jcDayData', 'jcReviewData']) {
      const real = globalThis[name];
      globalThis[name] = function () { if (LifeMaxJournalLock.blocksNative()) window.__fault('the Journal\'s open builder ' + name + ' ran while the lock keeps it closed'); return real.apply(this, arguments) };
    }
  });
  let reloads = 0;
  if (journal) {
    await afterLoad();
    await p.evaluate(() => {
      const L = lmaLoad(); L.nativeJournal = true; lmaSave();
      state.activities.push({ id: 'canary1', type: 'custom', name: 'Canary walk', icon: 'book', duration: 20, quality: 1, skill: 'Walk', primary: 'adventure', secondary: '', xp: 40, date: new Date().toISOString(), notes: 'Canary note', xpSplit: [{ attr: 'adventure', xp: 40 }] });
      state.dayReflections = { [getToday()]: 'Canary reflection, private words.' };
      state.journalLock = { on: true }; save(); LifeMaxJournalLock.relock();
    });
    await settle(1000);
  }
  const failures = []; let hops = 0;
  const rng = (s => () => { let t = s += 0x6D2B79F5; t = Math.imul(t ^ t >>> 15, t | 1); t ^= t + Math.imul(t ^ t >>> 7, t | 61); return ((t ^ t >>> 14) >>> 0) / 4294967296 })(Number(process.env.SEED_BASE) || 12345);
  for (let seed = 1; seed <= SEEDS; seed++) {
    await p.evaluate(s => { window.__seed = s; window.__log = []; window.__faults = []; window.__tapped = ''; window.__swipe = null; window.__accepted = ''; window.__sentNow = []; window.__composed = {}; NativePrime.reset() /* every seed starts with nothing composed: the idle prime has its screens to compose */ }, seed + (Number(process.env.SEED_BASE) || 0));
    const n = 3 + Math.floor(rng() * 9), seq = [];
    const hops_ = hopsOf(mode);
    for (let i = 0; i < n; i++) seq.push({ to: hops_[Math.floor(rng() * hops_.length)], gap: Math.floor(rng() * 4) * 25 });
    let view = '', carried = { faults: [], log: [] };
    for (const step of seq) {
      if (step.to === 'reload') {
        // a page load takes every screen away and starts a new page session; the native plugin, and its gate, stay
        const was = await p.evaluate(() => ({ faults: window.__faults, log: window.__log }));
        carried.faults.push(...was.faults); carried.log.push(...was.log, '>> reload');
        await p.reload(); reloads++;
        Object.assign(jn, { state: 'resting', shown: false }); // the old page's screens are gone (after anything it still sent); the gate stays
        await p.evaluate(s => { window.__seed = s }, seed * 977 + reloads);
        // a new page draws every bar scene cold, as at the start: each tab once, so a first capture cannot outlast the hand-over's net
        await afterLoad(); await settle(2500); await warmTabs();
        await p.evaluate(() => { window.__log.push(window.__t() + ' >> warmed up after the reload') });
        view = await p.evaluate(() => lmaView());
        hops++; continue;
      }
      // a tap is only possible on a screen that shows; the view after the hop is where it went (Food's screen ignores a tap it no longer owns)
      view = await p.evaluate(async ([v, tap, cmd, dir, move, card, web, jl, dk]) => {
        window.__onShow = {}; // a command waiting for a screen that has not shown yet is dropped by any later hop
        if (dk) {
          window.__tapped = '';
          const D = window.__dock, id = window.__rnd(0, 1) < 0.5 ? 'sim_book1' : 'sim_book2';
          if (v === 'play') { window.__log.push(window.__t() + ' >> the page plays ' + id); playMediaAudio(id); }
          else if (v === 'tap-play') {
            if (!window.__vis.media || lmaView() !== 'media') return lmaView();
            window.__log.push(window.__t() + ' >> tap Play on ' + id + ' in Media');
            Capacitor.Plugins.NativeMedia.__emit('command', { command: 'play', payload: { id }, requestId: 'play' + (window.__rid = (window.__rid || 0) + 1) });
          } else {
            if (!D.shown) return lmaView(); // a tap needs a dock on screen
            const all = [['toggle', {}], ['skip', { sec: -15 }], ['skip', { sec: 30 }], ['minimize', {}], ['rate', { value: 1.5 }], ['close', {}], ['seek', { frac: 0.5 }]], c = all[Math.floor(window.__rnd(0, all.length))];
            window.__log.push(window.__t() + ' >> tap ' + c[0] + ' in the dock');
            Capacitor.Plugins.NativeAudio.__emit('command', { command: c[0], payload: c[1] });
          }
          return lmaView();
        }
        if (jl) {
          window.__tapped = '';
          if (v === 'relock-vis') {
            // the app stopped (native locks a showing lock.on screen itself) and started again; the page sees it hidden, or misses that
            if (window.__draft) window.__flush('the app stopped');
            await window.__jnModel('stop');
            const missed = window.__rnd(0, 1) < 0.3;
            window.__log.push(window.__t() + ' >> app stopped and started' + (missed ? ' (the page missed hidden)' : ''));
            if (!missed) { window.__visState = 'hidden'; document.dispatchEvent(new Event('visibilitychange')) }
            window.__visState = 'visible'; document.dispatchEvent(new Event('visibilitychange'));
          } else if (v === 'lock-toggle') {
            // only in Settings, never while the Journal shows (48-journal-lock.js's card): turned on, this session is unlocked
            if (lmaView() === 'history') return lmaView();
            const on = !jlOn(); window.__log.push(window.__t() + ' >> lock switched ' + (on ? 'on' : 'off') + ' in Settings');
            state.journalLock = { on }; if (on) jlUnlocked = true; save(); jlApply();
          } else if (v === 'type' || v === 'leave-field') {
            // the notebook field, on the open Journal only: typing is autosaved into today's reflection; leaving the field sends the final write
            const st = await window.__jnModel('get');
            if (!window.__vis.journal || lmaView() !== 'history' || st.state !== 'open' || !st.shown) return lmaView();
            const d = getToday();
            if (v === 'type') {
              const base = (state.dayReflections || {})[d] || '', n = (window.__draft ? window.__draft.n : 0) + 1;
              window.__draft = { d, base: window.__draft && window.__draft.d === d ? window.__draft.base : base, n, owed: true,
                text: 'Canary typed ' + n + ': a long enough paragraph about how today went, with a good number of words in it.' };
              window.__log.push(window.__t() + ' >> type (autosave ' + n + ')');
              window.__jsend(false, window.__draft);
            } else if (window.__draft) {
              window.__log.push(window.__t() + ' >> leave the field (the final write)');
              window.__flush('left the field');
            }
          } else if (v === 'unlock') {
            if (!window.__vis.journal || lmaView() !== 'history') return lmaView();
            window.__log.push(window.__t() + ' >> unlock on the lock card');
            Capacitor.Plugins.NativeJournal.__emit('command', { command: 'unlock', payload: {}, requestId: 'unlock' + (window.__rid = (window.__rid || 0) + 1) });
          }
          return lmaView();
        }
        window.__endView = null; // where a tap made once its screen showed went (it went after this hop returned its view)
        const alone = s => !window.__busy && Object.keys(window.__vis).every(k => window.__vis[k] === (k === s)); // s alone shows, nothing in flight
        if (v === 'celebrate') { window.__log.push(window.__t() + ' >> an XP celebration on ' + lmaView()); window.__cardHops.celebrations++; window.__tapped = ''; /* the cards step aside for it and the hand-over may close what a tap left showing: no earlier tap to check */ celebrateXP(12, [{ id: 'physique', xp: 12 }]); return lmaView() }
        if (v === 'toggle-cards') {
          // the cards' switch exists only in Settings: native Settings' own toggle command, as a tap on its switch sends
          if (!window.__vis.settings || lmaView() !== 'settings') return lmaView();
          const on = !lmaLoad().nativeOverviewCards; window.__cardHops.toggles++; window.__log.push(window.__t() + ' >> tap the cards\' switch ' + (on ? 'on' : 'off') + ' in settings');
          Capacitor.Plugins.NativeSettings.__emit('command', { command: 'toggle', payload: { id: 'nativeOverviewCards', on }, requestId: 'sim' + (window.__rid = (window.__rid || 0) + 1) });
          return lmaView();
        }
        if (card) {
          // a tap in the cards, which goes to another native tab; if they do not show, the hop goes to the Overview and taps as soon
          // as they show, unless a later hop came first (as Life Director's command does)
          const tapCard = () => {
            if (!window.__vis.cards || lmaView() !== 'dashboard') return;
            window.__log.push(window.__t() + ' >> tap ' + (card.command || 'a move') + ' in cards');
            window.__tapped = alone('cards') ? 'cards' : ''; window.__cardHops.taps++;
            const P = Capacitor.Plugins.NativeOverviewCards;
            if (card.command) P.__emit('command', { command: card.command, payload: card.payload, requestId: 'cards-' + (window.__rid = (window.__rid || 0) + 1) });
            else P.__emit('action', card.action);
            window.__endView = card.to; // a command runs after its await: the page is not there yet
          };
          if (window.__vis.cards) tapCard();
          else if (window.__wantOf('dashboard') === 'cards') { window.__log.push(window.__t() + ' >> go dashboard, then a tap once the cards show'); window.__tapped = ''; window.__onShow.cards = tapCard; lmaNavTo('dashboard', { fast: true }) }
          return lmaView();
        }
        if (web) {
          // (never onto Money: it leaves for Cash, which the sim has no fake of)
          // the page's own swipe, from a page it draws under no native screen (or the live shell's web body), onto the next tab
          const shown = Object.keys(window.__vis).filter(k => window.__vis[k]), bar = lmaTabList(), i = bar.findIndex(b => b.dataset.view === lmaTabKey(lmaView())), to = i < 0 ? null : bar[i + web];
          if (window.__busy || shown.some(k => k !== 'overview') || !to || !(lmaResolve(to.dataset.view) in window.__want)) { window.__log.push(window.__t() + ' >> no web swipe on ' + lmaView()); return lmaView() }
          window.__log.push(window.__t() + ' >> web swipe from ' + lmaView() + ' to ' + to.dataset.view); window.__tapped = '';
          lmaSwipeGo(to.dataset.view, web, 1.2);
          return lmaView();
        }
        if (move) { const key = lmaTabKey(lmaView()); window.__log.push(window.__t() + ' >> move the ' + key + ' tab ' + (move < 0 ? 'earlier' : 'later')); lmaTabMove(key, move); return lmaView() }
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
        if (dir) {
          // the screen on top is under the finger, and swipes only where native would, by the last scene it took
          const PLUGINS = { cards: 'NativeOverviewCards', character: 'NativeCharacter', food: 'NativeNutrition', log: 'NativeLog', recipes: 'NativeRecipes', plan: 'NativePlan', director: 'NativeLifeDirector', media: 'NativeMedia', journal: 'NativeJournal' };
          const owner = window.__z.filter(k => window.__vis[k]).pop(), P = PLUGINS[owner] && Capacitor.Plugins[PLUGINS[owner]], scene = P && P.__scene(), sw = scene && scene.swipe;
          const tabs = sw ? sw.tabs : [], i = sw ? tabs.findIndex(t => t.key === sw.active) : -1, to = i < 0 ? null : tabs[i + dir];
          // a native tab, or (phase 2: the page takes it) one the page draws; never Money
          if (!sw || sw.v !== 1 || !sw.on || window.__wantOf(sw.view) !== owner || i < 0 || tabs[i].source === false || !to || lmaResolve(to.key) === 'money' /* a stop on the phone (NativeCash), which the sim does not fake */ || !(to.kind === 'native' || (to.kind === 'web' && sw.reveal))) { window.__log.push(window.__t() + ' >> no swipe ' + (dir > 0 ? 'next' : 'prev') + ' on ' + (owner || 'no screen')); return lmaView() }
          const token = owner + '#' + (++window.__tokens);
          window.__log.push(window.__t() + ' >> swipe ' + token + ' from ' + sw.view + ' to ' + to.key + ' (scene ' + scene.sequence + ')');
          const settled = !window.__busy && lmaView() === sw.view && Object.keys(window.__vis).every(k => window.__vis[k] === (k === owner));
          window.__tapped = settled ? owner : '';
          const s = window.__swipe = { token, from: sw.view, to: to.key, dir, toVis: window.__wantOf(lmaResolve(to.key)), answered: '' };
          P.__emit('swipe', Object.assign({ token, from: sw.view, to: to.key, dir, sequence: scene.sequence, via: 'drag', reveal: true }, owner === 'food' && { session: P.__session() }));
          // answered at once, and a swipe taken goes to its tab at once: native waits for that tab's screen
          if (!s.answered) window.__fault('the page did not answer ' + token);
          else if (s.answered !== 'refused' && lmaView() !== window.__accepted) window.__fault('the page took ' + token + ' and stayed on ' + lmaView());
          if (s.answered !== 'native') window.__tapped = ''; // revealed: a tab the page draws, so the source closes at once (A23 item 2)
          return lmaView();
        }
        if (!tap) {
          // to the Overview from a native screen that alone shows: that screen may not close before an Overview drawing shows, unless
          // the page is playing a celebration, which the cards step aside for (the page shows it, and the cards come back after)
          const from = window.__wantOf(lmaView());
          window.__log.push(window.__t() + ' >> go ' + v); window.__tapped = lmaResolve(v) === 'dashboard' && lmaView() !== 'dashboard' && from && alone(from) && !celebrating() ? from : ''; lmaNavTo(v, { fast: true }); return lmaView();
        }
        const { plugin, screen, from, to, commands } = tap, P = Capacitor.Plugins[plugin];
        if (!window.__vis[screen]) return lmaView();
        window.__log.push(window.__t() + ' >> tap ' + (to || 'a switch') + ' in ' + screen);
        const settled = !window.__busy && lmaView() === from && Object.keys(window.__vis).every(k => window.__vis[k] === (k === screen));
        window.__tapped = settled && to ? screen : ''; // a switch goes nowhere: no hand-over to check
        if (commands) commands.forEach((payload) => P.__emit('command', { command: 'toggle', payload, requestId: 'sim' + (window.__rid = (window.__rid || 0) + 1) })); // each its own id, as the screen's
        else P.__emit('action', plugin === 'NativeNutrition' ? { session: P.__session(), command: to } : { view: to });
        return lmaView();
      }, [step.to, TAPS[step.to] || JTAPS[step.to], COMMANDS[step.to], SWIPES[step.to], BAR[step.to], CARDS[step.to], WEB_SWIPES[step.to], JLOCK[step.to], DOCKHOPS[step.to]]);
      if (step.gap) await settle(step.gap);
      hops++;
    }
    await settle(4500);
    view = (await p.evaluate(() => window.__endView)) || view;
    if (journal) await p.evaluate(async () => {
      const st = await window.__jnModel('get'), locked = LifeMaxJournalLock.blocksNative();
      if (st.shown && st.state === 'open' && locked) window.__fault('open Journal data shows once settled while the lock keeps it closed');
      if (st.shown && st.state === 'locked' && !jlOn() && lmaView() === 'history') window.__fault('the Journal is stuck on the lock card with the lock off');
      if (!window.__vis.journal && st.shown) window.__fault('the Journal model shows while its screen is hidden');
      const paid = {}; state.activities.filter(a => a.type === 'coach' && a.name === 'Journal reflection').forEach(a => { paid[a.date.slice(0, 10)] = (paid[a.date.slice(0, 10)] || 0) + 1 });
      if (Object.values(paid).some(n => n > 1)) window.__fault('a reflection was paid more than once for a day: ' + JSON.stringify(paid));
    });
    const r = await p.evaluate(() => {
      const vis = Object.keys(window.__vis).filter(k => window.__vis[k]);
      if (window.__mode === 'dock') {
        const D = window.__dock, root = document.documentElement, bar = document.getElementById('audioPlayer'), up = vis.length > 0;
        const playing = bar.classList.contains('show') || !!audioCtx.loading, pageShows = getComputedStyle(bar).display !== 'none';
        if (D.shown !== (playing && up)) window.__fault('the dock ' + (D.shown ? 'shows' : 'is gone') + ' but should ' + (playing && up ? 'show' : 'not show') + ' (player ' + playing + ', native screens ' + vis.join('+') + ')');
        if (playing && !D.shown && !pageShows) window.__fault('a loaded book has no controls anywhere: no dock and the page\'s player hidden');
        if (D.shown && bar.classList.contains('show') && pageShows) window.__fault('the page\'s player and the dock both show');
        if (root.classList.contains('audio-docked') !== D.shown) window.__fault('the page ' + (root.classList.contains('audio-docked') ? 'thinks' : 'does not think') + ' the dock shows, which disagrees with it');
        if (D.shown && root.style.getPropertyValue('--audio-dock-h') !== D.height + 'px') window.__fault('the page pads ' + root.style.getPropertyValue('--audio-dock-h') + ' for a dock of ' + D.height);
        if (D.state && D.state.visible !== playing) window.__fault('the last pushed player state says visible=' + D.state.visible + ', the player ' + playing);
        if (D.state && D.state.minimized !== bar.classList.contains('minimized') && bar.classList.contains('show')) window.__fault('the pushed state disagrees about minimised');
      }
      if (!vis.length && window.__wantOf(lmaView())) window.__fault('no screen shows once settled');
      if (window.__accepted && window.__accepted === lmaView() && window.__wantOf(window.__accepted) && !window.__vis[window.__wantOf(window.__accepted)]) window.__fault('the swipe taken to ' + window.__accepted + ' never showed its screen');
      for (const mark of ['swipe-reveal', 'native-enter']) if (document.documentElement.classList.contains(mark)) window.__fault(mark + ' is still on once settled');
      return { vis: vis.join('+'), want: window.__wantOf(lmaView()), busy: window.__busy, view: lmaView(), log: window.__log,
        faults: window.__faults.concat(window.__sentNow.filter(id => !window.__runs[id]).map(id => 'Change it ' + id + ' never ran'),
          /* a Change it that ran while its screen showed is answered, unless the screen was sent a close since (the kit replies to an open screen only) */
          window.__sentNow.filter(id => { const run = window.__runs[id]; return run && run.shown && !window.__replies[id] && (window.__closes.director || 0) === run.closes; })
            .map(id => 'no reply to ' + id + ', which ran while its screen showed, and the screen was never closed')) };
    });
    const want = r.want;
    r.faults = carried.faults.concat(r.faults); r.log = carried.log.concat(r.log);
    if (r.vis !== want || r.busy !== 0 || r.view !== view || r.faults.length) failures.push({ seed, seq: seq.map(s => s.to + '+' + s.gap).join(' '), showing: r.vis || 'NONE', want, busy: r.busy, view: r.view, faults: r.faults, log: r.log });
  }
  const answers = await p.evaluate(() => window.__answers), enters = await p.evaluate(() => window.__enters);
  const cmds = await p.evaluate(() => Object.assign({ applied: Object.values(window.__runs).filter(r => r.ok).length, shown: Object.values(window.__runs).filter(r => r.shown).length }, window.__cmds));
  console.log((failures.length ? 'FAIL' : 'PASS') + ' cards ' + mode + ': ' + SEEDS + ' seeded sequences (' + hops + ' hops, swipes taken ' + answers.native + ', revealed ' + answers.web + ', refused ' + answers.refused + ', web swipes slid in ' + enters + '; ' + cmds.sent + ' Life Director Change its mid-hop, each run once, ' + cmds.applied + ' applied and saved, ' + cmds.shown + ' ran on a showing screen, ' + cmds.answered + ' answered on it): the last tab alone is showing after each');
  const primes = await p.evaluate(() => window.__primes);
  console.log('     prime ' + mode + ': ' + Object.values(primes).reduce((a, n) => a + n, 0) + ' screens composed ahead (' + (Object.keys(primes).sort().join(', ') || 'none') + ')');
  const cards = await p.evaluate(() => Object.assign({ gaveUp: NativeOverviewCards.diagnostics().gaveUp }, window.__cardHops));
  console.log('     cards ' + mode + ': shown ' + cards.shown + ' times, ' + cards.taps + ' taps in them, ' + cards.toggles + ' switch taps, ' + cards.celebrations + ' celebrations' + (cards.gaveUp ? ', gave up' : ''));
  if (mode === 'failing' && !cards.gaveUp) failures.push({ seed: 0, seq: '', showing: '', want: '', view: '', faults: ['the failing cards never gave up'], log: [] });
  // given up, the cards are not tried again for the session (no retry on a body change, a celebration's end or a hop)
  if (mode === 'failing' && cards.opens !== 1) failures.push({ seed: 0, seq: '', showing: '', want: '', view: '', faults: ['the failing cards were asked to open ' + cards.opens + ' times, not once'], log: [] });
  if ((mode === 'off' || mode === 'failing') && cards.shown) failures.push({ seed: 0, seq: '', showing: '', want: '', view: '', faults: ['the cards showed in mode ' + mode], log: [] });
  if (process.env.TRACE) failures.slice(0, 2).forEach(f => console.log('  trace seed ' + f.seed + ':\n    ' + f.log.join('\n    ')));
  failures.slice(0, 6).forEach(f => console.log('  seed ' + f.seed + ': ' + f.seq + ' -> showing ' + f.showing + ', wanted ' + f.want + (f.busy ? ' (busy ' + f.busy + ')' : '') + ' view=' + f.view + (f.faults.length ? ' faults: ' + f.faults.join('; ') : '')));
  if (mode === 'dock') console.log('     dock ' + mode + ': ' + (await p.evaluate(() => window.__dock.pushes)) + ' states pushed' + (process.env.DMUT ? ' (mutation ' + process.env.DMUT + ')' : ''));
  if (journal) {
    const st = await p.evaluate(() => window.__jnModel('get'));
    const wr = await p.evaluate(() => window.__writes);
    console.log('     journal ' + mode + ' writing: ' + wr.autosaves + ' autosaves and ' + wr.finals + ' final writes answered ok, ' + wr.paid + ' paid, ' + wr.flushes + ' flushes');
    console.log('     journal ' + mode + ': ' + st.open + ' open and ' + st.withheld + ' withheld frames drawn, ' + st.stops + ' app stops locked the screen, ' + st.refused + ' open frames refused by the gate, ' + st.cleared + ' gates cleared by a new session or the lock off, ' + reloads + ' reloads' + (process.env.JMUT ? ' (mutation ' + process.env.JMUT + ')' : ''));
  }
  loads -= reloads; // the reload mode's own reloads; any other load is a tab switch that loaded the page
  console.log((loads ? 'FAIL' : 'PASS') + ' cards ' + mode + ': no tab switch loaded a page (' + loads + ' loads)');
  if (errs.length) console.log('FAIL cards ' + mode + ': script errors', errs.slice(0, 3));
  await p.context().close();
  if (server) server.close();
  return !!(failures.length || errs.length || loads);
}
