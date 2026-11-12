/* The breeding system reached end to end through the native cattery (docs/specs/CAT_ENGINE_GAPS.md, WP-C0 / the reachability check):
   one cat at home and an empty cattery -> the Breeding lab says there is no mate -> Another stray (from the lab itself) until a mother and a
   fertile father can be paired -> Breed -> Keep a kitten -> Bring it home -> it lives at home as the second cat, is a kitten, and the
   native screen can draw it. Every step is the screen's own command, so this is what a player can do on the native Cat tab.
   Run: NODE_PATH=$(npm root -g) node tests/native-pet-breeding-path.test.js [built html] */
const { chromium } = require('playwright');
const file = require('./current-page')(process.argv[2]);
const { start } = require('./native-pet-panels.capture');
const { CAT } = require('./native-pet-panels.scenarios');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const CAPS = ['pet.projection.v1', 'pet.coat.v1', 'pet.panels.v1', 'pet.cattery.v1'];
const SEED = `window.seed=(n)=>{let s=(n||4242)>>>0;Math.random=()=>{s=(s+0x6D2B79F5)>>>0;let t=s;t=Math.imul(t^t>>>15,t|1);t^=t+Math.imul(t^t>>>7,t|61);return((t^t>>>14)>>>0)/4294967296}};window.seed()`;
/* one adult male at home, nothing in the cattery, nobody else */
const ONE_MALE = `delete state.pet2;state.petPair=null;state.petCatteryOps=[];state.petMemorial=[];state.cattery={v:1,goal:'em+',litter:null,cats:[]};
  state.pet=${CAT('Ted', '{}')};const d=petDnaRandom(petRng(petHash('path-home')),{sex:'XY'});d.gen=0;petDnaRegister(petDnaPheno(d).key);
  state.pet.dna=d;state.pet.sex='m';state.pet.trait=petDnaPheno(d).trait;state.pet.coat=petDnaPheno(d).key;seed()`;

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const p = await start(b, file, { caps: CAPS });
    await p.evaluate(SEED);
    await p.evaluate(async () => { const rt = await petInit(); petPairEnsure(rt) });
    const r = await p.evaluate(async (setup) => {
      const o = { steps: [] }, step = (n, v) => o.steps.push([n, v]);
      await setCats(setup); navigate('pet'); await until(() => showing(), 6000);
      const lab = async () => { await command('cattery', { kind: 'view', view: 'lab' }); return latest().cattery.detail };
      let d = await lab();
      o.noMate = { moms: d.moms.length, dads: d.dads.length, note: d.note };
      // the lab itself is where a mate is found: Another stray, until there is a mother and a father (a stray's sex is a coin toss)
      let n = 0;
      while ((!d.moms.length || !d.dads.length) && n < 14) { const a = await command('cattery', { kind: 'stray', op: 'p-stray-' + n }); if (!a.ok) { o.strayRefused = a; break } n++; d = await lab() }
      o.strays = n; o.moms = d.moms.map((m) => m.key); o.dads = d.dads.map((m) => m.key);
      o.mom = d.mom; o.dad = d.dad; o.forecast = d.forecast;
      const bred = await command('cattery', { kind: 'breed', op: 'p-breed', mom: d.mom, dad: d.dad });
      d = await lab();
      o.bred = bred.ok && bred.changed; o.kits = d.litter ? d.litter.kits.length : 0; o.litterAt = d.litter && d.litter.at;
      const kept = await command('cattery', { kind: 'keep', op: 'p-keep', at: d.litter.at, i: 0 });
      o.kept = kept.ok && kept.changed;
      await command('cattery', { kind: 'view', view: 'cattery' });
      const roster = latest().cattery.detail;
      o.roster = roster.cats.map((c) => [c.name, c.home, c.sub]);
      o.canHome = roster.canHome;
      const kit = roster.cats.filter((c) => !c.home).pop();
      const home = await command('cattery', { kind: 'home', op: 'p-home', id: kit.id });
      o.home = { ok: home.ok, changed: home.changed, message: home.message };
      o.pet2 = state.pet2 ? { alive: state.pet2.alive, name: state.pet2.name, stage: petGrowIdx(state.pet2), ageDays: Math.round((Date.now() - state.pet2.born) / 864e5), parents: state.pet2.dna.par ? Object.keys(state.pet2.dna.par).length : 0, gen: state.pet2.dna.gen } : null;
      o.drawable = !!(state.pet2 && (['ginger', 'gray', 'black', 'tuxedo'].includes(state.pet2.coat) || petCoatKnown(state.pet2.coat)));
      o.inCattery = state.cattery.cats.some((c) => c.id === kit.id);
      // the slot is taken now: a second kitten cannot come home
      const again = await command('cattery', { kind: 'home', op: 'p-home-2', id: roster.cats.filter((c) => !c.home)[0] ? roster.cats.filter((c) => !c.home)[0].id : 'none' });
      o.second = { ok: again.ok, message: again.message };
      return o;
    }, ONE_MALE);
    ok('with one male at home and no cattery the lab says there is no mate', r.noMate.moms === 0 && r.noMate.dads === 1 && /Another stray/.test(r.noMate.note), r.noMate);
    ok('Another stray, asked from the lab, finds a mother and a father to pair (' + r.strays + ' strays)', r.moms.length >= 1 && r.dads.length >= 1 && !r.strayRefused, r);
    ok('the page forecasts the chosen pair', /^Expected /.test(r.forecast), r.forecast);
    ok('Breed rolls a litter of two to six', r.bred && r.kits >= 2 && r.kits <= 6, r);
    ok('Keep puts a kitten in the cattery', r.kept && r.roster.length === 1 + r.strays + 1, r.roster);
    ok('the roster offers Bring home while the second slot is free', r.canHome === true, r);
    ok('Bring home: ok, changed, the kitten lives at home as the second cat and has left the cattery', r.home.ok && r.home.changed && r.pet2 && r.pet2.alive && !r.inCattery, r);
    ok('it is the child of the pair (parents recorded, generation 1) and the screen can draw it', r.pet2 && r.pet2.parents === 2 && r.pet2.gen === 1 && r.drawable, r.pet2);
    ok('the second slot is taken: a second kitten cannot come home', r.second && !r.second.ok, r.second);
    ok('no page errors', p.errors.length === 0, p.errors);
    await p.context().close();
  } catch (e) {
    fail++; console.log('FAIL threw ' + (e && e.stack || e));
  } finally {
    await b.close();
  }
  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail ? 1 : 0);
})();
