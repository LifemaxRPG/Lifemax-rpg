/* The native Cat tab's cattery fixtures (pet.cattery.v1): what the page projects for a screen that draws the cattery screens, for each
   screen and state, and what it answers the `cattery` command. Written to android-wrapper/native/src/test/resources/fixtures:
     pet-cattery-entry.json      no screen open: the small `entry` (a second cat may be adopted, one cat has passed)
     pet-cattery-cattery.json    the roster: a home cat and three cattery cats (a male, a female, a sterile male)
     pet-cattery-lab.json        the Breeding lab with a litter of three
     pet-cattery-labNoMate.json  the lab with no fertile male: the page's note
     pet-cattery-labFull.json    a full cattery with a litter: the page's "cattery is full" line
     pet-cattery-adopt.json      adopting a second cat: four kittens of a litter
     pet-cattery-memorial.json   In memory: two cats that have passed
     pet-empty-adopt.json        no cat at home (pet.empty.v1): the intro, the first cat's adoption screen open (WP-C4)
     pet-empty-memorial.json     a cat has passed (pet.empty.v1): the memorial screen open
     pet-cattery-replies.json    breed, a repeat, a stale keep, a release and a refused rename answered, in the kit's envelope
   Random numbers are seeded, so a regeneration is stable.
   Regenerate only when the block or a reply changes:  NODE_PATH=$(npm root -g) node tools/gen_pet_cattery_fixtures.js [built html] */
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const file = require('../tests/current-page')(process.argv[2]);
const { start } = require('../tests/native-pet-panels.capture');
const { AT, BASE, FULL, PAIR, NOCAT, DEAD } = require('../tests/native-pet-cattery.scenarios');
const OUT = path.join(__dirname, '..', '..', 'android-wrapper', 'native', 'src', 'test', 'resources', 'fixtures');
const CAPS = ['pet.projection.v1', 'pet.coat.v1', 'pet.cattery.v1'];
const EMPTY_CAPS = [...CAPS, 'pet.empty.v1'];
const SEED = `window.seed=(n)=>{let s=(n||777)>>>0;Math.random=()=>{s=(s+0x6D2B79F5)>>>0;let t=s;t=Math.imul(t^t>>>15,t|1);t^=t+Math.imul(t^t>>>7,t|61);return((t^t>>>14)>>>0)/4294967296}};window.seed()`;
const MEMORIAL = `state.petMemorial=[{name:'Pepper',coat:'ginger',days:12,passed:now-9*864e5,bond:2,album:null},{name:'Luna',coat:state.pet.coat,days:1,passed:now-3*864e5,bond:5,album:null}]`;
const STATES = {
  entry: [`${BASE(false)};${MEMORIAL}`, ''],
  cattery: [BASE(false), 'cattery'],
  lab: [BASE(true), 'lab'],
  labNoMate: [`${BASE(false)};state.cattery.cats=[]`, 'lab'],
  labFull: [FULL, 'lab'],
  adopt: [`${PAIR}`, 'adopt'],
  memorial: [`${BASE(false)};${MEMORIAL}`, 'memorial'],
};

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    for (const [name, [setup, view]] of Object.entries(STATES)) {
      const p = await start(b, file, { caps: CAPS });
      const snap = await p.evaluate(async ([setup, view, SEED]) => {
        (0, eval)(SEED); await setCats(setup); seed();
        navigate('pet'); await until(() => showing(), 8000);
        if (view) { seed(); await command('cattery', { kind: 'view', view }) }
        return JSON.parse(JSON.stringify(Object.assign(latest(), { webSessionId: 'pet-fixture', sequence: 1 })));
      }, [setup, view, SEED]);
      fs.writeFileSync(path.join(OUT, 'pet-cattery-' + name + '.json'), JSON.stringify(snap, null, 1) + '\n');
      console.log('wrote pet-cattery-' + name, JSON.stringify(snap).length, 'bytes', p.errors.length ? p.errors : '');
      await p.context().close();
    }
    for (const [name, setup, view] of [['adopt', NOCAT, 'adopt'], ['memorial', DEAD, 'memorial']]) {
      const p = await start(b, file, { caps: EMPTY_CAPS });
      const snap = await p.evaluate(async ([setup, view, SEED]) => {
        (0, eval)(SEED); await setCats(setup); seed();
        navigate('pet'); await until(() => showing(), 8000);
        seed(); await command('cattery', { kind: 'view', view });
        return JSON.parse(JSON.stringify(Object.assign(latest(), { webSessionId: 'pet-fixture', sequence: 1 })));
      }, [setup, view, SEED]);
      fs.writeFileSync(path.join(OUT, 'pet-empty-' + name + '.json'), JSON.stringify(snap, null, 1) + '\n');
      console.log('wrote pet-empty-' + name, JSON.stringify(snap).length, 'bytes', p.errors.length ? p.errors : '');
      await p.context().close();
    }
    const p = await start(b, file, { caps: CAPS });
    const replies = await p.evaluate(async ([setup, AT, SEED]) => {
      (0, eval)(SEED); await setCats(setup); seed(); navigate('pet'); await until(() => showing(), 8000);
      const run = async (c, x) => { const n = __calls.length, id = 'f-' + c + '-' + n; await Capacitor.Plugins.NativePet.__emit('command', { command: 'cattery', payload: x, requestId: id });
        await until(() => __calls.slice(n).some((k) => k[0] === 'update' && k[1].reply && k[1].reply.requestId === id), 4000);
        return __calls.slice(n).find((k) => k[0] === 'update' && k[1].reply && k[1].reply.requestId === id)[1].reply };
      const o = {};
      await command('cattery', { kind: 'view', view: 'lab' });
      o.view = await run('view', { kind: 'view', view: 'lab' });
      o.breed = await run('breed', { kind: 'breed', op: 'fx-breed', mom: 'h0', dad: 'c1' });
      o.breedAgain = await run('breed', { kind: 'breed', op: 'fx-breed', mom: 'h0', dad: 'c1' });
      o.keepStale = await run('keep', { kind: 'keep', op: 'fx-keep', at: AT + 1, i: 0 });
      o.release = await run('release', { kind: 'release', op: 'fx-rel', id: 'c1' });
      o.renameBlank = await run('rename', { kind: 'rename', op: 'fx-ren', id: 'c2', name: '  ' });
      return Object.fromEntries(Object.entries(o).map(([k, v]) => [k, { reply: v }]));
    }, [BASE(false), AT, SEED]);
    fs.writeFileSync(path.join(OUT, 'pet-cattery-replies.json'), JSON.stringify(replies, null, 1) + '\n');
    console.log('wrote pet-cattery-replies', Object.entries(replies).map(([k, v]) => k + ':' + v.reply.ok));
    await p.context().close();
  } finally { await b.close() }
})();
