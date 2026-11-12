/* Native Cat tab with no cat at home (pet.empty.v1, docs/specs/CAT_ENGINE_GAPS.md WP-C4), web half, through the real page:
   - gate: a screen without pet.empty.v1 (or without pet.cattery.v1) is kept away from the intro and the memorial as it always was
     (blocked), the tab does not open;
   - with it the tab opens for no cat at all (the intro) and for a cat that has passed (the memorial), with a projection that has no cat
     in it: exactly protocol, webSessionId, sequence, notice, tokens, light, `empty: {screen}` and the cattery block;
   - the screen's `view` command opens the cattery's Adoption (first cat) or Memorial detail; adopting makes a living cat, after which the
     projection is the ordinary Cat card again (no `empty`); "Open the door again" turns the memorial into the intro;
   - a cat that passes while the tab is open turns it into the memorial instead of closing it; a living cat's projection is unchanged.
   Run: NODE_PATH=$(npm root -g) node tests/native-pet-empty.test.js [built html] */
const { chromium } = require('playwright');
const file = require('./current-page')(process.argv[2]);
const { start } = require('./native-pet-panels.capture');
const { BASE, NOCAT, DEAD } = require('./native-pet-cattery.scenarios');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const CAPS = ['pet.projection.v1', 'pet.coat.v1', 'pet.panels.v1', 'pet.cattery.v1'];
const EMPTY = [...CAPS, 'pet.empty.v1'];
const SEED = `window.seed=(n)=>{let s=(n||12345)>>>0;Math.random=()=>{s=(s+0x6D2B79F5)>>>0;let t=s;t=Math.imul(t^t>>>15,t|1);t^=t+Math.imul(t^t>>>7,t|61);return((t^t>>>14)>>>0)/4294967296}};window.seed()`;
const KEYS = ['cattery', 'empty', 'light', 'notice', 'protocol', 'sequence', 'tokens', 'webSessionId'];

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    /* 1. the gate */
    for (const [name, caps] of [['pet.empty.v1', CAPS], ['pet.cattery.v1', ['pet.projection.v1', 'pet.coat.v1', 'pet.panels.v1', 'pet.empty.v1']]]) {
      const p = await start(b, file, { caps });
      await p.evaluate(SEED);
      const r = await p.evaluate(async ([none, dead]) => {
        const out = {};
        await setCats(none); navigate('pet'); await wait(600); out.intro = showing();
        await setCats(dead); navigate('dashboard'); await wait(200); navigate('pet'); await wait(600); out.memorial = showing();
        return out;
      }, [NOCAT, DEAD]);
      ok('a screen without ' + name + ' stays away from the intro and the memorial', !r.intro && !r.memorial, r);
      await p.context().close();
    }

    /* 2. the intro, adopting, the ordinary card after it */
    let p = await start(b, file, { caps: EMPTY });
    await p.evaluate(SEED);
    let r = await p.evaluate(async ([none]) => {
      const o = {};
      await setCats(none); navigate('pet'); o.shown = await until(() => showing(), 6000);
      const s = latest();
      o.keys = Object.keys(s).sort(); o.empty = s.empty; o.entry = s.cattery.entry; o.view = s.cattery.view;
      o.viewReply = await command('cattery', { kind: 'view', view: 'adopt' });
      const d = latest().cattery.detail;
      o.adopt = d && { kind: d.kind, mode: d.mode, kits: d.kits.length, title: d.title };
      o.adopted = await command('cattery', { kind: 'adopt', op: 'e-1', mode: 'first', kit: 1, name: 'Mango' });
      const after = latest();
      o.after = { empty: 'empty' in after, cats: (after.cats || []).length, name: after.name, alive: !!(state.pet && state.pet.alive), pet: state.pet && state.pet.name };
      o.showingAfter = showing();
      return o;
    }, [NOCAT]);
    ok('no cat at all: the tab opens', r.shown, r);
    ok('its projection has no cat in it, only the screen to show and the cattery', JSON.stringify(r.keys) === JSON.stringify(KEYS) && r.empty.screen === 'adopt', r.keys);
    ok('the cattery entry says no second cat is offered (the first cat is the adoption)', r.entry && r.entry.adopt === '' && r.entry.memorial === 1, r.entry);
    ok('the view command opens the first cat\'s adoption: four kittens, the page\'s words', r.viewReply.ok && r.adopt && r.adopt.kind === 'adopt' && r.adopt.mode === 'first' && r.adopt.kits === 4, r.adopt);
    ok('adopting: ok and changed, a living cat is at home', r.adopted.ok && r.adopted.changed && r.after.alive && r.after.pet === 'Mango', r.adopted);
    ok('and the projection is the ordinary Cat card again (no empty, the cat is in it)', !r.after.empty && r.after.cats === 1 && r.after.name === 'Mango' && r.showingAfter, r.after);
    ok('no page errors (intro)', p.errors.length === 0, p.errors);
    await p.context().close();

    /* 3. the memorial and the door */
    p = await start(b, file, { caps: EMPTY });
    await p.evaluate(SEED);
    r = await p.evaluate(async ([dead]) => {
      const o = {};
      await setCats(dead); navigate('pet'); o.shown = await until(() => showing(), 6000);
      o.empty = latest().empty;
      await command('cattery', { kind: 'view', view: 'memorial' });
      const d = latest().cattery.detail;
      o.memorial = d && { kind: d.kind, entries: d.entries.length, canReopen: d.canReopen, text: d.entries[0] && d.entries[0].text };
      o.reopen = await command('cattery', { kind: 'reopen', op: 'e-2' });
      o.after = { empty: latest().empty, hasPet: !!state.pet, memorials: (state.petMemorial || []).length, showing: showing() };
      return o;
    }, [DEAD]);
    ok('a cat that has passed: the tab opens on the memorial', r.shown && r.empty && r.empty.screen === 'memorial', r);
    ok('the memorial lists the cat in the page\'s words and the door can open again', r.memorial && r.memorial.entries === 1 && r.memorial.canReopen && /is gone/.test(r.memorial.text), r.memorial);
    ok('opening the door again: ok, changed, and the tab turns to the first cat\'s adoption', r.reopen.ok && r.reopen.changed && r.after.empty && r.after.empty.screen === 'adopt' && r.after.showing && r.after.memorials === 1, r);
    ok('no page errors (memorial)', p.errors.length === 0, p.errors);
    await p.context().close();

    /* 4. a living cat is unchanged, and a cat that passes while the tab is open turns it into the memorial */
    p = await start(b, file, { caps: EMPTY });
    await p.evaluate(SEED);
    r = await p.evaluate(async ([base]) => {
      const o = {};
      await setCats(base); navigate('pet'); o.shown = await until(() => showing(), 6000);
      const s = latest();
      o.living = { empty: 'empty' in s, cats: s.cats.length, name: s.name };
      state.pet.alive = false; state.pet.passed = Date.now(); state.petMemorial = [{ name: 'Ted', coat: state.pet.coat, days: 40, passed: Date.now(), bond: 3, album: null }];
      save(); renderPetView(true); NativePet.active() && NativeTabKit && (await wait(50));
      o.turned = await until(() => latest() && latest().empty && latest().empty.screen === 'memorial', 4000);
      o.stillShowing = showing();
      return o;
    }, [BASE(false)]);
    ok('a living cat: the ordinary projection, no empty', r.shown && !r.living.empty && r.living.cats === 1 && r.living.name === 'Ted', r.living);
    ok('a cat that passes while the tab is open: the tab turns to the memorial and stays open', r.turned && r.stillShowing, r);
    ok('no page errors (living)', p.errors.length === 0, p.errors);
    await p.context().close();
  } catch (e) {
    fail++; console.log('FAIL threw ' + (e && e.stack || e));
  } finally {
    await b.close();
  }
  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail ? 1 : 0);
})();
