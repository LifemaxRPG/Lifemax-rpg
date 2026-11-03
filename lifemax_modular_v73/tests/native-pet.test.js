/* Native Cat tab (pet.projection.v1), web half (docs/specs/CAT_TAB_PLAN.md, CT1):
   - the setting (off by default, "(beta)"), the tab opening for a living cat, and staying away from the intro (no cat) and the
     memorial (a cat that has passed, also when it passes while the screen shows);
   - the projection against the page's own Cat card: every word the card shows (mood, eyebrow, hearts, bond, last meal, meals today,
     days together, favourites, hint, the Feed button, a kitten's growth line) is the one the DOM holds, for each cat state, and the
     projection writes nothing (no save, no state change);
   - every command: Feed (served through the page's own petFeedClick, ok only once the save stored it, taken back when it did not,
     a repeat of the same operation answered "Already recorded." with one meal, the page's refusal in its words, both cats of a house
     of two), Slow blink (the page's answer, a toast under the screen coming back as the message and shown once), Vacation mode, Rename
     (1 to 14 characters), the coat command; and the fed cue every route to the bowl leaves (the voice's included);
   - the page's room loop stopped while the screen covers the view, and running again once it does not;
   - what is handed to the page: Play (its tray), the full-screen room (the screen comes back when it closes), the rest of the Cat
     page (the screen comes back on the next visit to the tab); a level-up steps the screen aside;
   - a screen without the capability, or without the coat capability for a genetic coat, leaves the page's Cat view.
   A fake NativePet plugin stands in for Android. Run: NODE_PATH=$(npm root -g) node tests/native-pet.test.js [built html] */
const { chromium } = require('playwright'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const golden = require('./fixtures/saves/established.json');

const FAKE = (caps) => `
  window.__calls = []; const ls = {};
  const plugin = { __emit: (e, d) => Promise.all((ls[e] || []).map(f => f(d))), addListener: async (e, f) => { (ls[e] = ls[e] || []).push(f); return { remove() {} } },
    handshake: async () => ({ protocol: 1, capabilities: window.__caps }), open: async a => { window.__calls.push(['open', a]) },
    update: async a => { window.__calls.push(['update', a]) }, close: async () => { window.__calls.push(['close']); (ls.closed || []).forEach(f => f({})) } };
  window.__caps = ${JSON.stringify(caps)};
  window.Capacitor = { isNativePlatform: () => true, isPluginAvailable: n => n === 'NativePet', getPlatform: () => 'android', registerPlugin: () => ({}), convertFileSrc: u => u, Plugins: { NativePet: plugin } };`;
const CAT = (name, o = '{}') => `Object.assign({v:1,name:${JSON.stringify(name)},coat:'ginger',trait:'curious',born:now-40*864e5,lastFed:now-5*36e5,meals:[],day:getToday(),fedToday:0,petsToday:0,playsToday:0,bondPts:PET_BOND_AT[3],care:0,vacation:{on:false,since:0},gifts:0,lastGiftDay:'',alive:true,passed:0,seen:{},greeted:0},${o})`;
const GONE = CAT('Miso', '{alive:false,passed:now-864e5}');
const OLD_DAY = CAT('Miso', '{day:"2000-01-01",fedToday:2}');
const ON_VACATION = CAT('Miso', '{vacation:{on:true,since:now-3*36e5}}');
const NEARLY_FOND = CAT('Miso', '{lastFed:now-9*36e5,bondPts:PET_BOND_AT[3]-1}');
const HELPERS = `
  window.wait = ms => new Promise(r => setTimeout(r, ms));
  window.count = k => __calls.filter(c => c[0] === k).length;
  window.showing = () => { const o = __calls.map(c => c[0]).lastIndexOf('open'), c = __calls.map(c => c[0]).lastIndexOf('close'); return o >= 0 && o > c };
  window.until = async (f, ms = 5000) => { for (let t = 0; t < ms && !f(); t += 25) await wait(25); return !!f() };
  window.latest = () => { const c = [...__calls].reverse().find(c => c[0] === 'open' || c[0] === 'update'); return c && c[1].snapshot };
  window.command = async (command, payload) => { const n = __calls.length, id = 'r-' + n; await Capacitor.Plugins.NativePet.__emit('command', { command, payload, requestId: id });
    await until(() => __calls.slice(n).some(c => c[0] === 'update' && c[1].reply && c[1].reply.requestId === id), 3000);
    const u = __calls.slice(n).find(c => c[0] === 'update' && c[1].reply && c[1].reply.requestId === id); return u ? u[1].reply : null };
  window.action = async a => { await Capacitor.Plugins.NativePet.__emit('action', a); await wait(150) };
  window.setCats = js => { (0, eval)('(function(){const now=Date.now();' + js + '})()'); save(); renderPetView(true); return wait(250) };
  NativeOverviewSnapshot.snapshot = async () => ({ width: 320, header: { nodes: [], hits: [], height: 64, width: 320 }, nav: { nodes: [], hits: [], height: 64, width: 320 } });`;

async function start(b, { on = true, /* null: the switch never touched */ caps = ['pet.projection.v1', 'pet.coat.v1'], pet = CAT('Miso'), pet2 = '' } = {}) {
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await p.addInitScript(([save, extras, prefs]) => {
    localStorage.setItem('lifemax_rpg_prototype_v1', save); for (const k in extras) localStorage.setItem(k, extras[k]);
    localStorage.setItem('lifemax_android_prefs_v1', prefs);
  }, [JSON.stringify(Object.assign({}, golden.state, { tutorialDone: true, tipsSeen: { dashboard: true } })), golden.extras || {}, JSON.stringify(Object.assign({ nativeOverview: false, nativeOverviewCards: false, apps: [] }, on === null ? {} : { nativePet: on }))]);
  await p.addInitScript(FAKE(caps));
  await p.goto('file://' + file, { timeout: 120000 });
  await p.evaluate(HELPERS);
  await p.evaluate(async ([pet, pet2]) => { lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { } if (pet) await setCats('state.pet=' + pet + (pet2 ? ';state.pet2=' + pet2 : '')); await petInit() }, [pet, pet2]);
  return p;
}
const toPet = (p) => p.evaluate(async () => { navigate('pet'); await until(() => showing(), 5000); return showing() });

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    /* 1. the switch: off by default, the page's Cat view while it is off, the screen once it is on */
    let p = await start(b, { on: null });
    let r = await p.evaluate(async () => { navigate('pet'); await wait(800); const off = { opened: count('open'), view: lmaView() };
      const row = document.getElementById('nativePetSetting'), t = document.getElementById('nativePetToggle');
      const sw = { label: row && row.textContent, checked: t && t.checked, off: lmaLoad().nativePet };
      await setNativePetEnabled(true); navigate('dashboard'); await wait(300); navigate('pet'); const opened = await until(() => showing(), 5000);
      return { off, sw, opened, now: lmaLoad().nativePet } });
    ok('the switch is off by default: the page shows its own Cat view', r.off.opened === 0 && r.off.view === 'pet' && r.sw.off === undefined && r.sw.checked === false, r);
    ok('the Settings row says "Native Cat (beta)" and that the cats live their own day until the brain link', /Native Cat \(beta\)/.test(r.sw.label) && /their own day/.test(r.sw.label) && /brain link/.test(r.sw.label), r.sw.label);
    ok('turning the switch on opens the screen on the Cat tab', r.opened && r.now === true, r);
    await p.evaluate(() => setNativePetEnabled(false)); await p.waitForTimeout(300);
    ok('turning it off closes the screen again', await p.evaluate(() => !showing() && lmaLoad().nativePet === false));
    await p.context().close();

    /* 2. the screen opens for a living cat only; the intro and the memorial are the page's */
    p = await start(b, { pet: '' });
    r = await p.evaluate(async () => { delete state.pet; save(); renderPetView(true); navigate('pet'); await wait(900); return { opened: count('open'), mode: document.getElementById('view-pet').dataset.stamp } });
    ok('no cat yet: the page shows its intro (a visitor), no screen', r.opened === 0 && /^intro/.test(r.mode), r);
    r = await p.evaluate(async (gone) => { await setCats('state.pet=' + gone); navigate('dashboard'); await wait(200); navigate('pet'); await wait(900); return { opened: count('open'), mode: document.getElementById('view-pet').dataset.stamp } }, GONE);
    ok('a cat that has passed: the page shows its memorial, no screen', r.opened === 0 && /^memorial/.test(r.mode), r);
    await p.context().close();
    p = await start(b);
    r = await p.evaluate(async () => { navigate('pet'); await until(() => showing()); const was = showing();
      state.pet.lastFed = Date.now() - 200 * 36e5; petCheck(); save(); renderPetAll(); await until(() => !showing(), 3000);
      return { was, gone: !showing(), mode: document.getElementById('view-pet').dataset.stamp } });
    ok('a cat that passes while the screen shows: the screen steps aside for the memorial', r.was && r.gone && /^memorial/.test(r.mode), r);
    await p.context().close();

    /* 3. the projection against the page's own card, state by state; and it writes nothing */
    const STATES = { content: "{lastFed:now-36e5,fedToday:1}", alive: "{}", hungry: "{lastFed:now-20*36e5}", veryHungry: "{lastFed:now-30*36e5}", weak: "{lastFed:now-70*36e5}",
      vacation: "{vacation:{on:true,since:now-3*36e5}}", kitten: "{coat:'tuxedo',born:now-12*864e5,bondPts:PET_BOND_AT[1]}", stray: "{born:now-864e5,bondPts:0}", devoted: "{bondPts:PET_BOND_AT[5]}",
      withLikes: "{likes:{v:1,spots:{},toys:{},plays:{},seen:{},dayT:0,favSpot:'tree',favToy:'feather',news:[]}}" };
    p = await start(b);
    for (const [name, o] of Object.entries(STATES)) {
      r = await p.evaluate(async ([name, set]) => {
        await setCats('state.pet=' + set); navigate('dashboard'); await wait(150); navigate('pet'); await until(() => showing(), 4000);
        const snap = NativePetProjection.snapshot(), txt = id => (document.getElementById(id) || {}).textContent;
        petRenderStatus(true); const favs = [...document.querySelectorAll('#petFavs b')].map(e => e.textContent);
        const g = document.getElementById('petGrow');
        return { name, dom: { mood: txt('petMood'), eyebrow: txt('petEyebrow'), bondText: txt('petBondText'), bar: document.getElementById('petBondBar').style.width, fed: txt('petStatFed'), fedL: txt('petStatFedL'),
          today: txt('petStatToday'), days: txt('petStatDays'), feed: txt('petFeedBtn'), wait: document.getElementById('petFeedBtn').classList.contains('is-wait'), hint: txt('petHint'),
          hearts: document.querySelectorAll('#petHearts span.on').length, favs, grow: g && g.style.display !== 'none' ? g.textContent : '' },
          snap: { mood: snap.mood, eyebrow: snap.eyebrow, bondText: snap.bond.text, bar: snap.bond.pct + '%', fed: snap.lastMeal.value, fedL: snap.lastMeal.label, today: snap.mealsToday, days: snap.days,
            feed: snap.feed.label, wait: snap.feed.wait, hint: snap.hint, hearts: snap.hearts, favs: [snap.favs.place, snap.favs.toy], grow: snap.grow ? snap.grow.label + snap.grow.day + snap.grow.text : '' } };
      }, [name, CAT('Miso', o)]);
      const d = r.dom, s = r.snap;
      ok('projection equals the page card: ' + name, JSON.stringify([d.mood, d.eyebrow, d.bondText, d.bar, d.fed, d.fedL, d.today, d.days, d.feed, d.wait, d.hint, d.hearts, d.favs, d.grow.replace(/\s+/g, '')]) ===
        JSON.stringify([s.mood, s.eyebrow, s.bondText, s.bar, s.fed, s.fedL, s.today, s.days, s.feed, s.wait, s.hint, s.hearts, s.favs, s.grow.replace(/\s+/g, '')]), r);
    }
    r = await p.evaluate(async (oldDay) => {
      await setCats('state.pet=' + oldDay);
      const before = JSON.stringify(state), writes = []; const set = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { writes.push(k); return set.apply(this, arguments) };
      const a = NativePetProjection.snapshot(), c = NativePetProjection.snapshot(); Storage.prototype.setItem = set;
      return { same: JSON.stringify(state) === before, writes: writes.length, seq: c.sequence - a.sequence, day: a.mealsToday } }, OLD_DAY);
    ok('the projection writes nothing and changes nothing (a new day shows 0 meals without rolling it)', r.same && r.writes === 0 && r.seq === 1 && r.day === '0 / 2', r);
    await p.context().close();

    /* 4. the page's loop is stopped while the screen covers the view, and runs when it does not */
    p = await start(b);
    r = await p.evaluate(async () => { const before = petWantRun(); navigate('pet'); await until(() => showing()); const covered = petWantRun(); const cov = NativePet.covering();
      await Capacitor.Plugins.NativePet.close(); await wait(100); return { before, covered, cov, after: NativePet.covering() } });
    ok('the room loop is stopped while the screen covers the view', r.covered === false && r.cov === true && r.after === false, r);
    await p.context().close();

    /* 5. Feed */
    p = await start(b);
    await toPet(p);
    r = await p.evaluate(async () => {
      const out = {}; const was = { lastFed: state.pet.lastFed, meals: state.pet.meals.length };
      out.fed = await command('feed', { op: 'op-1' }); out.after = { lastFed: state.pet.lastFed > was.lastFed, meals: state.pet.meals.length - was.meals, today: state.pet.fedToday, saved: JSON.parse(localStorage.getItem('lifemax_rpg_prototype_v1')).pet.fedToday };
      const snap = latest(); out.cue = snap.cue.fed; out.label = snap.feed;
      out.again = await command('feed', { op: 'op-1' }); out.meals = state.pet.meals.length - was.meals;
      out.full = await command('feed', { op: 'op-2' }); out.mealsAfterFull = state.pet.meals.length - was.meals;
      out.noOp = await command('feed', {});
      return out });
    ok('Feed: the page serves the meal through petFeedClick, saved, ok and changed', r.fed.ok && r.fed.changed && r.after.lastFed && r.after.meals === 1 && r.after.today === 1 && r.after.saved === 1, r);
    ok('Feed: the projection after it carries one fed cue (cat 0) and the Feed button waiting', r.cue.length === 1 && r.cue[0].cat === 0 && r.cue[0].id === 1 && r.label.wait === true && r.label.label === 'Not hungry yet', r);
    ok('Feed: the same operation again answers "Already recorded." and feeds no second time', r.again.ok && !r.again.changed && r.again.message === 'Already recorded.' && r.meals === 1, r.again);
    ok('Feed: a new operation while the cat is full is refused in the page\'s words, with why and the hours, and no meal', !r.full.ok && r.full.why === 'full' && /is not hungry yet\. A meal in about/.test(r.full.message) && r.full.inHours > 0 && r.full.reaction === 'bowl' && r.mealsAfterFull === 1, r.full);
    ok('Feed: a command without an operation id is refused', !r.noOp.ok && /Nothing was changed/.test(r.noOp.message), r.noOp);
    r = await p.evaluate(async (vac) => {
      const out = {}; await setCats('state.pet=' + vac);
      out.vac = await command('feed', { op: 'op-v' }); out.meals = state.pet.meals.length; return out }, ON_VACATION);
    ok('Feed: on vacation it is refused in the page\'s words', !r.vac.ok && r.vac.why === 'vacation' && r.vac.message === 'Vacation mode is on. Turn it off to feed Miso.' && r.meals === 0, r.vac);
    r = await p.evaluate(async (nearly) => {
      await setCats('state.pet=' + nearly);
      const before = JSON.stringify([state.pet.lastFed, state.pet.meals, state.pet.fedToday, state.pet.bondPts]);
      const real = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
      let reply; try { reply = await command('feed', { op: 'op-q' }) } finally { Storage.prototype.setItem = real }
      const after = JSON.stringify([state.pet.lastFed, state.pet.meals, state.pet.fedToday, state.pet.bondPts]);
      const retry = await command('feed', { op: 'op-q' });
      return { reply, same: before === after, retry, meals: state.pet.meals.length } }, NEARLY_FOND);
    ok('Feed: a save the browser refuses is taken back: not ok, nothing changed, the page\'s words', !r.reply.ok && !r.reply.changed && /Could not save/.test(r.reply.message) && r.same, r);
    ok('Feed: the same operation sent again after a failed save feeds once', r.retry.ok && r.retry.changed && r.meals === 1, r.retry);
    await p.context().close();

    /* a house of two: whoever is hungry is fed, each with a cue; the one Feed button says "Feed both" */
    p = await start(b, { pet: CAT('Ted'), pet2: CAT('Lily', '{coat:"gray"}') });
    await toPet(p);
    r = await p.evaluate(async () => {
      const out = { snap: latest() }; out.fed = await command('feed', { op: 'two-1' });
      const s = latest(); out.cues = s.cue.fed; out.meals = [state.pet.meals.length, state.pet2.meals.length]; out.label = s.feed; return out });
    ok('two cats: the house has both, the button says "Feed both"', r.snap.cats.length === 2 && r.snap.feed.label === 'Feed both' && /Ted and Lily share the house/.test(r.snap.together), r.snap.feed);
    ok('two cats: one Feed serves both, a cue for each', r.fed.ok && r.meals[0] === 1 && r.meals[1] === 1 && r.cues.map(c => c.cat).join() === '0,1' && r.cues[1].id === r.cues[0].id + 1 && r.label.label === 'Not hungry yet', r);
    await p.context().close();

    /* every route to the bowl leaves a cue: the voice's feed as well */
    p = await start(b);
    await toPet(p);
    r = await p.evaluate(async () => { const n = latest().cue.fed.length; lmvHPetAct({ lines: [], chips: [] }, { act: 'feed' }); window.NativePetProjection.snapshot(); return { before: n, after: NativePetProjection.snapshot().cue.fed.length } });
    ok('the voice\'s feed leaves a fed cue too (petFeed is the one function every route calls)', r.before === 0 && r.after === 1, r);
    await p.context().close();

    /* 6. Slow blink, Vacation, Rename */
    p = await start(b);
    await toPet(p);
    r = await p.evaluate(async () => {
      const out = {}; out.blink = await command('blink', {}); out.blink2 = await command('blink', {}); out.squint = await command('blink', {});
      out.toast = document.getElementById('toast').textContent; out.notice = latest().notice;
      out.vacOn = await command('vacation', { on: true }); out.vacOnSaved = JSON.parse(localStorage.getItem('lifemax_rpg_prototype_v1')).pet.vacation.on; out.snap = latest().care.vacation + '|' + latest().feed.label;
      out.vacAgain = await command('vacation', { on: true }); out.vacBad = await command('vacation', { on: 'yes' });
      out.vacOff = await command('vacation', { on: false });
      out.rename = await command('rename', { name: '  Mochi ' }); out.name = [state.pet.name, latest().name, latest().care.name];
      out.same = await command('rename', { name: 'Mochi' }); out.empty = await command('rename', { name: '   ' }); out.long = await command('rename', { name: 'Fifteen letters!' }); out.num = await command('rename', { name: 5 });
      return out });
    ok('Blink: the cat blinks back (reaction "blink"), no message', r.blink.ok && r.blink.reaction === 'blink' && r.blink.message === '' && r.blink2.reaction === 'blink', r.blink);
    ok('Blink: the third in six seconds is the page\'s refusal ("squints"), in the message, with no reaction', r.squint.ok && r.squint.reaction === '' && /squints at you/.test(r.squint.message), r.squint);
    ok('Blink: the page\'s toast is the reply\'s message and is not also sent as the screen\'s notice', r.notice === null, r.notice);
    ok('Vacation: on for the cat, saved, the Feed button says so; asking for it again changes nothing; a non-boolean is refused', r.vacOn.ok && r.vacOn.changed && r.vacOnSaved === true && r.snap === 'true|Vacation mode' && r.vacAgain.message === 'Already recorded.' && !r.vacAgain.changed && !r.vacBad.ok && r.vacOff.changed, r);
    ok('Rename: trimmed, saved on the cat, shown in the projection; the same name changes nothing; empty, long and non-text names are refused', r.rename.ok && r.rename.changed && r.name.join() === 'Mochi,Mochi,Mochi' && r.same.message === 'Already recorded.' && !r.empty.ok && !r.long.ok && /1 to 14 characters/.test(r.long.message) && !r.num.ok, r);
    r = await p.evaluate(async () => {
      const real = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
      let v, n; try { v = await command('vacation', { on: true }); n = await command('rename', { name: 'Biscuit' }) } finally { Storage.prototype.setItem = real }
      return { v, n, vac: state.pet.vacation.on, name: state.pet.name } });
    ok('Vacation and Rename: a save the browser refuses is taken back (not ok, the cat unchanged)', !r.v.ok && !r.n.ok && r.vac === false && r.name === 'Mochi', r);
    await p.context().close();

    /* 7. the coat command, and a genetic coat needing the capability */
    p = await start(b, { pet: CAT('Miso', "{coat:'dna_black_1_n_2_0_n_0_3'}") });
    r = await p.evaluate(async () => { petDnaRegister(state.pet.coat); navigate('pet'); const shown = await until(() => showing(), 6000);
      const sheet = await command('coat', { key: state.pet.coat, kind: 'adult' }), legacy = await command('coat', { key: 'ginger', kind: 'adult' }), bad = await command('coat', { key: state.pet.coat, kind: 'nope' });
      return { shown, ok: sheet.ok, kind: sheet.kind, png: (sheet.png || '').length > 100, palette: Object.keys(sheet.palette || {}).length, legacy: legacy.ok, bad: bad.ok } });
    ok('a genetic coat opens with pet.coat.v1, and the coat command answers its sheet (never a legacy coat, never an unknown sheet)', r.shown && r.ok && r.kind === 'adult' && r.png && r.palette > 3 && !r.legacy && !r.bad, r);
    await p.context().close();
    p = await start(b, { pet: CAT('Miso', "{coat:'dna_black_1_n_2_0_n_0_3'}"), caps: ['pet.projection.v1'] });
    r = await p.evaluate(async () => { petDnaRegister(state.pet.coat); navigate('pet'); await wait(1200); return { opened: count('open'), view: lmaView() } });
    ok('a screen that cannot ask for genetic coats leaves such a cat to the page\'s Cat view', r.opened === 0 && r.view === 'pet', r);
    await p.context().close();
    p = await start(b, { caps: ['character.projection.v1'] });
    r = await p.evaluate(async () => { navigate('pet'); await wait(1200); return { opened: count('open'), view: lmaView() } });
    ok('an app without pet.projection.v1 keeps the page\'s Cat view', r.opened === 0 && r.view === 'pet', r);
    await p.context().close();

    /* 8. handing over: Play, the full-screen room, the rest of the page; and a level-up */
    p = await start(b);
    await toPet(p);
    r = await p.evaluate(async () => {
      const out = {}; let played = 0; const orig = window.petPlayClick; window.petPlayClick = function () { played++; return orig.apply(this, arguments) };
      await action({ action: 'play', id: '' }); out.played = played; out.gone = !showing(); out.loop = petWantRun();
      await wait(600); out.stays = !showing();
      navigate('dashboard'); await wait(300); navigate('pet'); out.back = await until(() => showing(), 4000);
      return out });
    ok('Play: the screen steps aside for the page\'s Cat view and its tray, stays away, and comes back on the next visit to the tab', r.played === 1 && r.gone && r.stays && r.back, r);
    r = await p.evaluate(async () => {
      const out = {}; await action({ action: 'room', id: '' }); await wait(300);
      out.open = document.body.classList.contains('pet-full-open'); out.gone = !showing(); await wait(500); out.stays = !showing();
      petRoomFullscreenClose(); out.back = await until(() => showing(), 4000); return out });
    ok('View room: the page\'s full-screen room opens over the view, and the screen comes back when it closes', r.open && r.gone && r.stays && r.back, r);
    r = await p.evaluate(async () => { await action({ action: 'page', id: '' }); const gone = !showing(); navigate('dashboard'); await wait(300); navigate('pet'); return { gone, back: await until(() => showing(), 4000) } });
    ok('More on the Cat page: the page\'s own view, and the screen on the next visit', r.gone && r.back, r);
    r = await p.evaluate(async () => { const ov = document.createElement('div'); ov.className = 'luov'; document.body.appendChild(ov); await until(() => !showing(), 3000); const gone = !showing(); ov.remove(); return { gone, back: await until(() => showing(), 4000) } });
    ok('a level-up over the view steps the screen aside and it comes back after', r.gone && r.back, r);
    ok('no page errors', p.errors.length === 0, p.errors);
    await p.context().close();
  } finally { await b.close() }
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); process.exit(fail ? 1 : 0);
})();
