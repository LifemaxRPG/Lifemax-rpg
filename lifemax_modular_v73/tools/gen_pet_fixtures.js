/* The native Cat tab's fixtures (pet.projection.v1), the pet job of tools/gen_native_fixtures.js: what the page projects for a screen
   that draws the Cat tab (NativePetProjection.snapshot()), on the established golden save at a fixed clock, one cat state at a time,
   and what the page answers each of the screen's commands:
     pet-alive.json          Miso, a ginger cat, Friendly (3 hearts), last fed 5 h ago: the Feed button is open
     pet-content.json        fed 1 h ago: "Not hungry yet", the button waiting
     pet-hungry.json         20 h without a meal (stage 2): the hungry mood line, the cat not yet weak
     pet-very-hungry.json    30 h (stage 3): very hungry, the weak rows in the room
     pet-weak.json           70 h (stage 4): weak and tired
     pet-vacation.json       Vacation mode on: "Vacation", "clock paused", "Vacation mode" on the button
     pet-kitten.json         Pip, a tuxedo kitten 12 days old: KITTEN eyebrow and the growth line
     pet-stray.json          a new stray (Stray, 0 hearts): the first hint
     pet-two-cats.json       Ted and Lily, both alive: both in the room, "Feed both", the line about sharing the house
     pet-dna.json            a genetic (calico) coat the screen asks the page for (pet.coat.v1)
     pet-fed.json            after Feed through the command: the fed cue the room plays once
     pet-touched.json        a shy cat petted twice and played with once today: the touch fields (pet.touch.v1) and the counts line
     pet-petted.json         after a pet through the touch command: the day's count and the brain's saturation
     pet-no-touch.json       the first release's app (no pet.touch.v1): none of the touch fields
     pet-play.json           the touched cat for a screen with pet.play.v1: the toy tray (unlocked toys only, the page's words)
     pet-play-tired.json     a very hungry cat with pet.play.v1: the tray with the page's "too tired to play" refusal
     pet-demo.json           the touched cat for a screen with pet.play.v1 and pet.demo.v1 while developer mode is on: the Demo parkour button
     pet-replies.json        every command answered, in the envelope NativeTabKit sends a reply in
   pet-panels-<state>.json the same projection for a screen that also draws the panels (pet.panels.v1): the mind row, likes,
                           inner life, genetics, Together card and toys for the cat states of tests/native-pet-panels.scenarios.js
                           (kitten, stray, bonded, hungry, vacation, twoCats, geneticMale, geneticFemale, geneticSterile), the
                           genome table open (geneticFemaleGenome), the second cat in focus (twoCatsSecond), and the toy shop's
                           early unlock bought (strayBought)
   pet-panels-replies.json focus, buyToy and expand answered, in the same envelope
   A fake NativePet plugin lets the tab open; its handshake lists both capabilities. Regenerate only when the projection or a reply
   changes:  NODE_PATH=$(npm root -g) node tools/gen_native_fixtures.js pet */
const STORE = 'lifemax_rpg_prototype_v1';
const CLOCK = '2026-10-30T12:00:00Z';
const CAPS = ['pet.projection.v1', 'pet.coat.v1', 'pet.touch.v1'];
const CAPS_CT1 = ['pet.projection.v1', 'pet.coat.v1']; // an app of the first release: the projection has none of the touch fields
const PANEL_CAPS = [...CAPS_CT1, 'pet.panels.v1']; // the panels' fixtures as they were made, without the touch fields
const { SCENARIOS } = require('../tests/native-pet-panels.scenarios');
const cat = (name, o) => `Object.assign({v:1,name:${JSON.stringify(name)},coat:'ginger',trait:'curious',born:now-40*864e5,lastFed:now-5*36e5,meals:[],day:getToday(),fedToday:0,petsToday:0,playsToday:0,bondPts:PET_BOND_AT[3],care:0,vacation:{on:false,since:0},gifts:0,lastGiftDay:'',alive:true,passed:0,seen:{},greeted:0},${o})`;
/* the cat states, set up in the page before the tab opens (now is the fixed clock) */
const STATES = {
  alive: `state.pet=${cat('Miso', '{}')}`,
  content: `state.pet=${cat('Miso', '{lastFed:now-36e5,fedToday:1}')}`,
  hungry: `state.pet=${cat('Miso', '{lastFed:now-20*36e5}')}`,
  'very-hungry': `state.pet=${cat('Miso', '{lastFed:now-30*36e5}')}`,
  weak: `state.pet=${cat('Miso', '{lastFed:now-70*36e5}')}`,
  vacation: `state.pet=${cat('Miso', '{vacation:{on:true,since:now-3*36e5}}')}`,
  kitten: `state.pet=${cat('Pip', '{coat:"tuxedo",born:now-12*864e5,bondPts:PET_BOND_AT[1]}')}`,
  stray: `state.pet=${cat('Miso', '{born:now-864e5,bondPts:0}')}`,
  'two-cats': `state.pet=${cat('Ted', '{bondPts:PET_BOND_AT[2]}')};state.pet2=${cat('Lily', '{coat:"gray"}')}`,
  dna: `state.pet=${cat('Miso', '{coat:"dna_black_1_n_2_0_n_0_3"}')};petDnaRegister(state.pet.coat)`,
  touched: `state.pet=${cat('Miso', '{trait:"shy",petsToday:2,playsToday:1,bondPts:PET_BOND_AT[4]}')}`,
};
const fake = (caps) => `(() => { const ls = {}; window.__calls = [];
  const plugin = { __emit: (e, d) => Promise.all((ls[e] || []).map(f => f(d))), addListener: async (e, f) => { (ls[e] = ls[e] || []).push(f); return { remove() {} } },
    handshake: async () => ({ protocol: 1, capabilities: ${JSON.stringify(caps)} }), open: async a => { window.__calls.push(['open', a]) },
    update: async a => { window.__calls.push(['update', a]) }, close: async () => { (ls.closed || []).forEach(f => f({})) } };
  window.Capacitor = { isNativePlatform: () => true, isPluginAvailable: n => n === 'NativePet', getPlatform: () => 'android', registerPlugin: () => ({}),
    convertFileSrc: u => u, Plugins: { NativePet: plugin } } })()`;

module.exports = async function pet({ b, out, write }) {
  const fs = require('fs'), path = require('path');
  const golden = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'tests', 'fixtures', 'saves', 'established.json'), 'utf8'));
  const visit = async (setup, caps = CAPS) => {
    const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
    await p.clock.setFixedTime(new Date(CLOCK));
    const save = { ...golden.state, tutorialDone: true, tipsSeen: { ...(golden.state.tipsSeen || {}), dashboard: true } };
    await p.addInitScript(([STORE, save, extras]) => {
      localStorage.setItem(STORE, save); for (const k in extras) localStorage.setItem(k, extras[k]);
      localStorage.setItem('lifemax_android_prefs_v1', JSON.stringify({ nativePet: true, nativeOverview: false, nativeOverviewCards: false, apps: [] }));
    }, [STORE, JSON.stringify(save), golden.extras || {}]);
    await p.addInitScript(fake(caps));
    await p.goto('file://' + out, { timeout: 120000 });
    await p.evaluate(() => { lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { } });
    await p.evaluate(async (setup) => {
      (0, eval)('(function(){const now=Date.now();' + setup + '})()');
      NativeOverviewSnapshot.snapshot = async () => ({ width: 320, header: { nodes: [], hits: [], height: 64, width: 320 }, nav: { nodes: [], hits: [], height: 64, width: 320 } });
      save(); renderPetView(true);
      const rt = await petInit();
      if (state.pet2) { petPairEnsure(rt); for (let t = 0; t < 5000 && !petPairOn(); t += 25) await new Promise((r) => setTimeout(r, 25)) }
    }, setup);
    return p;
  };
  /* the projection a screen is opened with, as the page builds it now; the session and sequence are fixed so a regeneration is stable */
  const open = (p) => p.evaluate(async () => {
    const wait = (ms) => new Promise((r) => setTimeout(r, ms));
    await openNativePet();
    for (let t = 0; t < 6000 && !__calls.some((c) => c[0] === 'open'); t += 50) await wait(50);
    const o = __calls.find((c) => c[0] === 'open');
    if (!o) throw Error('the Cat tab did not open: ' + JSON.stringify({ blocked: NativePet && false, view: lmaView() }));
    return JSON.parse(JSON.stringify(Object.assign(o[1].snapshot, { webSessionId: 'pet-fixture', sequence: 1 })));
  });

  for (const [name, setup] of Object.entries(STATES)) {
    const p = await visit(setup);
    await p.evaluate(() => navigate('pet'));
    write('fixtures/pet-' + name + '.json', await open(p));
    await p.context().close();
  }
  { // the first release's app (no pet.touch.v1): the projection without any touch field
    const p = await visit(STATES.alive, CAPS_CT1);
    await p.evaluate(() => navigate('pet'));
    write('fixtures/pet-no-touch.json', await open(p));
    await p.context().close();
  }
  /* native Play (pet.play.v1): the toy tray for a cat that may play, and for one too hungry to */
  for (const [name, setup] of Object.entries({ play: STATES.touched, 'play-tired': STATES['very-hungry'] })) {
    const p = await visit(setup, [...CAPS, 'pet.play.v1']);
    await p.evaluate(() => navigate('pet'));
    write('fixtures/pet-' + name + '.json', await open(p));
    await p.context().close();
  }
  /* the developer demo (pet.demo.v1): the button, projected only while developer mode is on */
  {
    const p = await visit(STATES.touched + ';state.devMode=true', [...CAPS, 'pet.play.v1', 'pet.demo.v1']);
    await p.evaluate(() => navigate('pet'));
    write('fixtures/pet-demo.json', await open(p));
    await p.context().close();
  }

  /* the panels (pet.panels.v1) for each cat state, then the same screen's commands */
  const panelStates = { ...SCENARIOS, geneticFemaleGenome: SCENARIOS.geneticFemale, twoCatsSecond: SCENARIOS.twoCats, strayBought: SCENARIOS.stray };
  for (const [name, setup] of Object.entries(panelStates)) {
    const p = await visit(setup + (name === 'strayBought' ? ';state.sparks=1000;state.toysBought={feather:1}' : ''), PANEL_CAPS);
    // the cats that were out of sight a moment ago have a fresh "while you were away" line
    if (['bonded', 'twoCats', 'geneticFemale', 'geneticFemaleGenome'].includes(name)) await p.evaluate(() => { petBR(PET_RT).awayFresh = Date.now() });
    await p.evaluate(() => navigate('pet'));
    if (name === 'geneticFemaleGenome') {
      await open(p);
      await p.evaluate(async () => { await Capacitor.Plugins.NativePet.__emit('command', { command: 'expand', payload: { open: true }, requestId: 'x' }); await new Promise((r) => setTimeout(r, 300)) });
      write('fixtures/pet-panels-' + name + '.json', await p.evaluate(() => JSON.parse(JSON.stringify(Object.assign(__calls.filter((c) => c[0] === 'update').pop()[1].snapshot, { webSessionId: 'pet-fixture', sequence: 1 })))));
    } else if (name === 'twoCatsSecond') {
      await open(p);
      await p.evaluate(async () => { await Capacitor.Plugins.NativePet.__emit('command', { command: 'focus', payload: { i: 1 }, requestId: 'x' }); await new Promise((r) => setTimeout(r, 300)) });
      write('fixtures/pet-panels-' + name + '.json', await p.evaluate(() => JSON.parse(JSON.stringify(Object.assign(__calls.filter((c) => c[0] === 'update').pop()[1].snapshot, { webSessionId: 'pet-fixture', sequence: 1 })))));
    } else write('fixtures/pet-panels-' + name + '.json', await open(p));
    await p.context().close();
  }
  {
    const p = await visit(SCENARIOS.twoCats + ';state.sparks=1000;state.toysBought={}', PANEL_CAPS);
    await p.evaluate(() => navigate('pet'));
    await open(p);
    const answer = (key, command, payload, before) => p.evaluate(async ([key, command, payload, before]) => {
      const wait = (ms) => new Promise((r) => setTimeout(r, ms));
      if (before) (0, eval)('(function(){const now=Date.now();' + before + '})()');
      const n = __calls.length, id = 'req-' + key;
      await Capacitor.Plugins.NativePet.__emit('command', { command, payload, requestId: id });
      for (let t = 0; t < 4000 && !__calls.slice(n).some((c) => c[0] === 'update' && c[1].reply && c[1].reply.requestId === id); t += 25) await wait(25);
      const u = __calls.slice(n).find((c) => c[0] === 'update' && c[1].reply && c[1].reply.requestId === id);
      if (!u) throw Error('no reply for ' + key);
      return { command, payload, reply: JSON.parse(JSON.stringify(u[1].reply)) };
    }, [key, command, payload, before]);
    const panelReplies = {};
    panelReplies.focusSecond = await answer('focusSecond', 'focus', { i: 1 });
    panelReplies.focusAgain = await answer('focusAgain', 'focus', { i: 1 });
    panelReplies.focusNoSuchCat = await answer('focusNoSuchCat', 'focus', { i: 2 });
    panelReplies.buyToy = await answer('buyToy', 'buyToy', { id: 'feather' }, 'state.pet.bondPts=0');
    panelReplies.buyToyAgain = await answer('buyToyAgain', 'buyToy', { id: 'feather' });
    panelReplies.buyToyNotForSale = await answer('buyToyNotForSale', 'buyToy', { id: 'ball_a' });
    panelReplies.buyToyPoor = await answer('buyToyPoor', 'buyToy', { id: 'spring' }, 'state.sparks=10');
    panelReplies.expandOpen = await answer('expandOpen', 'expand', { open: true });
    panelReplies.expandBad = await answer('expandBad', 'expand', { open: 'yes' });
    write('fixtures/pet-panels-replies.json', panelReplies);
    await p.context().close();
  }

  /* the commands, in the envelope NativeTabKit sends a reply in ({command, requestId, ok, ...}) */
  const p = await visit(STATES.alive);
  await p.evaluate(() => navigate('pet'));
  await open(p);
  const run = (key, command, payload, before) => p.evaluate(async ([key, command, payload, before]) => {
    const wait = (ms) => new Promise((r) => setTimeout(r, ms));
    if (before) (0, eval)('(function(){const now=Date.now();' + before + '})()');
    const n = __calls.length, id = 'req-' + key;
    await Capacitor.Plugins.NativePet.__emit('command', { command, payload, requestId: id });
    for (let t = 0; t < 4000 && !__calls.slice(n).some((c) => c[0] === 'update' && c[1].reply && c[1].reply.requestId === id); t += 25) await wait(25);
    const u = __calls.slice(n).find((c) => c[0] === 'update' && c[1].reply && c[1].reply.requestId === id);
    if (!u) throw Error('no reply for ' + key);
    return { command, payload, reply: JSON.parse(JSON.stringify(u[1].reply)), snapshot: JSON.parse(JSON.stringify(u[1].snapshot)) };
  }, [key, command, payload, before]);
  const replies = {};
  const keep = async (key, command, payload, before) => (replies[key] = await run(key, command, payload, before));
  await keep('feedRefusedFull', 'feed', { op: 'op-0' }, 'state.pet.lastFed=now-36e5;state.pet.fedToday=1');
  await keep('feedRefusedVacation', 'feed', { op: 'op-1' }, 'state.pet.lastFed=now-6*36e5;petSetVacation(true)');
  await keep('vacationOff', 'vacation', { on: false });
  await keep('vacationAgain', 'vacation', { on: false });
  const fed = await keep('feed', 'feed', { op: 'op-2' }, 'state.pet.lastFed=now-9*36e5;state.pet.fedToday=0');
  await keep('feedAgain', 'feed', { op: 'op-2' });
  write('fixtures/pet-fed.json', Object.assign(fed.snapshot, { webSessionId: 'pet-fixture', sequence: 2 }));
  await keep('blink', 'blink', {});
  await keep('blinkSecond', 'blink', {});
  await keep('blinkSquint', 'blink', {}); // the third in six seconds is refused
  await keep('vacationOn', 'vacation', { on: true });
  await keep('vacationOffAgain', 'vacation', { on: false });
  await keep('rename', 'rename', { name: 'Mochi' });
  await keep('renameSame', 'rename', { name: 'Mochi' });
  await keep('renameEmpty', 'rename', { name: '  ' });
  await keep('renameLong', 'rename', { name: 'A name that is far too long' });
  // touch (pet.touch.v1): a tap, a pet, the same pet again, a cat that left, a refusal, a level-up, the purr's sound
  const stroke = (o) => Object.assign({ kind: 'stroke', cat: 0, heldMs: 1500, left: '', ignored: false, spam: false, keepSleep: false }, o);
  await keep('strokeTap', 'touch', stroke({ op: 's-1', heldMs: 200 }));
  await keep('strokePet', 'touch', stroke({ op: 's-2' }));
  await keep('strokePetAgain', 'touch', stroke({ op: 's-2' }));
  await keep('strokeLeft', 'touch', stroke({ op: 's-3', heldMs: 2500, left: 'over', ignored: true }));
  await keep('strokeRefused', 'touch', stroke({ op: '' }));
  await keep('strokeLevelUp', 'touch', stroke({ op: 's-4' }), 'state.pet.petsToday=0;state.pet.bondPts=PET_BOND_AT[4]-1');
  await keep('purrOn', 'touch', { kind: 'purr', cat: 0, on: true, level: 0.75 });
  await keep('purrOff', 'touch', { kind: 'purr', cat: 0, on: false, level: 0 });
  write('fixtures/pet-petted.json', Object.assign(replies.strokePet.snapshot, { webSessionId: 'pet-fixture', sequence: 3 }));
  // a save the browser refuses (storage full): nothing is changed and the reply says why
  await p.evaluate(() => { window.__realSet = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === 'lifemax_rpg_prototype_v1') throw new Error('QuotaExceededError'); return window.__realSet.apply(this, arguments) } });
  await keep('vacationUnsaved', 'vacation', { on: true });
  await keep('feedUnsaved', 'feed', { op: 'op-3' }, 'state.pet.lastFed=now-9*36e5');
  await keep('strokeUnsaved', 'touch', stroke({ op: 's-5' }), 'state.pet.petsToday=0');
  await p.evaluate(() => { Storage.prototype.setItem = window.__realSet });
  await keep('unknown', 'dance', {});
  write('fixtures/pet-replies.json', Object.fromEntries(Object.entries(replies).map(([k, v]) => [k, { command: v.command, payload: v.payload, reply: v.reply }])));
  await p.context().close();
};
