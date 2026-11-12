/* Native cattery (pet.cattery.v1), web half (docs/specs/CAT_TAB_PLAN.md CT6b): the `cattery` block of the Cat tab's projection and the
   `cattery` command, which calls the page's own breeding and adoption handlers with the rules unchanged.
   - the capability gate: without it the projection has no `cattery` (and the old words); with it the small `entry` is always there and
     the `detail` only for the screen the `view` command opened;
   - the words are the page's: the lab's text, forecast, chips and small lines against what petBreedHtml writes for the same state;
   - PARITY: for each action the SAVED state after the command is identical (byte for byte, from localStorage) to the saved state after
     the page's own button handler ran on the same state with the same random numbers: breed, keep, drop, keep all, release all, release,
     rename, bring home, stray, goal, adopting a second cat, adopting the first cat, opening the door again;
   - repeat-safety (same operation id: "Already recorded.", nothing twice, a litter not rolled twice), refusals in the page's words and a
     save the browser refuses taken back, strict command shapes.
   Run: NODE_PATH=$(npm root -g) node tests/native-pet-cattery.test.js [built html] */
const { chromium } = require('playwright');
const file = require('./current-page')(process.argv[2]);
const { start, CLOCK } = require('./native-pet-panels.capture');
const { CAT } = require('./native-pet-panels.scenarios');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const CAPS = ['pet.projection.v1', 'pet.coat.v1', 'pet.panels.v1', 'pet.cattery.v1'];
const AT = Date.parse(CLOCK);

/* a home cat (a genetic female), three cattery cats (a male, a female, a sterile male) and, when asked, a litter of three */
const MK = `const mk=(seed,sex)=>{const d=petDnaRandom(petRng(petHash('ct6b-'+seed)),{sex:sex==='XXY'?'XY':sex});if(sex==='XXY')d.sex='XXY';d.gen=0;petDnaRegister(petDnaPheno(d).key);return d};`;
const BASE = (litter) => `${MK}delete state.pet2;state.petPair=null;state.petCatteryOps=[];state.petMemorial=[];
  state.pet=${CAT('Ted', '{}')};const d0=mk('home','XX');state.pet.dna=d0;state.pet.sex='f';state.pet.trait=petDnaPheno(d0).trait;state.pet.coat=petDnaPheno(d0).key;
  state.cattery={v:1,goal:'em+',litter:${litter ? `{mom:'Ted',dad:'Basil',at:${AT},kits:[mk('k1','XX'),mk('k2','XY'),mk('k3','XY')]}` : 'null'},cats:[
    {id:'c1',name:'Basil',dna:mk('m1','XY'),born:now-5*864e5},{id:'c2',name:'Olive',dna:mk('f1','XX'),born:now-9*864e5},{id:'c3',name:'Fig',dna:mk('x1','XXY'),born:now-2*864e5}]}`;
const FULL = `${BASE(true)};while(state.cattery.cats.length<PET_CATTERY_MAX)state.cattery.cats.push({id:'z'+state.cattery.cats.length,name:'Z'+state.cattery.cats.length,dna:mk('z'+state.cattery.cats.length,'XY'),born:now})`;
const NOCAT = `${MK}state.pet=null;delete state.pet2;state.petCatteryOps=[];state.petMemorial=[{name:'Pepper',coat:'ginger',days:12,passed:now-864e5,bond:2,album:null}];seed();window.__petLitter=petDnaNewLitter(4)`;
const PAIR = `${BASE(false)};seed();window.__petLitter=petDnaNewLitter(4)`;
const DEAD = `${BASE(false)};state.pet.alive=false;state.pet.passed=now;state.petMemorial=[{name:'Ted',coat:state.pet.coat,days:40,passed:now,bond:3,album:null}]`;
const NAME = (n) => `{document.querySelectorAll('#petNameIn').forEach(e=>e.remove());const i=document.createElement('input');i.id='petNameIn';i.value='${n}';document.body.appendChild(i);try{`; // the page's click reads the name from its field
/* each case: the setup, what the page's own button does, the command that must do the same */
const CASES = [
  ['breed', BASE(false), `petBreedPick('mom','h0');petBreedPick('dad','c1');petBreedGo()`, { kind: 'breed', op: 'o-breed', mom: 'h0', dad: 'c1' }],
  ['keep', BASE(true), `petBreedKeep(1)`, { kind: 'keep', op: 'o-keep', at: AT, i: 1 }],
  ['drop', BASE(true), `petBreedDrop(0)`, { kind: 'drop', op: 'o-drop', at: AT, i: 0 }],
  ['keep all', BASE(true), `petBreedKeepAll()`, { kind: 'keepAll', op: 'o-ka', at: AT }],
  ['release all', BASE(true), `petBreedDropAll()`, { kind: 'dropAll', op: 'o-da', at: AT }],
  ['release', BASE(false), `window.confirm=()=>true;petBreedRelease('c1')`, { kind: 'release', op: 'o-rel', id: 'c1' }],
  ['rename', BASE(false), `window.prompt=()=>'  Basilisk  ';petBreedRename('c1')`, { kind: 'rename', op: 'o-ren', id: 'c1', name: 'Basilisk' }],
  ['bring home', BASE(false), `petBreedHome('c2')`, { kind: 'home', op: 'o-home', id: 'c2' }],
  ['stray', BASE(false), `petBreedStray()`, { kind: 'stray', op: 'o-stray' }],
  ['goal', BASE(false), `petBreedSetGoal('lg-')`, { kind: 'goal', value: 'lg-' }],
  ['adopt a second cat', PAIR, `petPickKit(2);${NAME('Nori')}petPairAdoptClick()}finally{document.getElementById('petNameIn').remove()}}`, { kind: 'adopt', op: 'o-pair', mode: 'pair', kit: 2, name: 'Nori' }],
  ['adopt the first cat', NOCAT, `petPickKit(1);${NAME('Mango')}petAdoptClick()}finally{document.getElementById('petNameIn').remove()}}`, { kind: 'adopt', op: 'o-first', mode: 'first', kit: 1, name: 'Mango' }],
  ['open the door again', DEAD, `petAdoptNew()`, { kind: 'reopen', op: 'o-door' }],
];

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    /* the same random numbers for the button and for the command */
    const PREP = `window.seed=(n)=>{let s=(n||12345)>>>0;Math.random=()=>{s=(s+0x6D2B79F5)>>>0;let t=s;t=Math.imul(t^t>>>15,t|1);t^=t+Math.imul(t^t>>>7,t|61);return((t^t>>>14)>>>0)/4294967296}};window.seed();
      window.saved=()=>{const s=JSON.parse(localStorage.getItem('lifemax_rpg_prototype_v1'));const core=(c)=>c&&{name:c.name,coat:c.coat,trait:c.trait,born:c.born,alive:c.alive,passed:c.passed,dna:c.dna,sex:c.sex,bondPts:c.bondPts};
        const calm=(c)=>{if(!c)return null;const o=Object.assign({},c);delete o.brain;delete o.likes;delete o.seen;return o};
        return JSON.stringify([core(s.pet),calm(s.pet2),s.cattery||null,s.petMemorial||null])};
      window.run=async(js,after)=>{await setCats(js);seed();return null};`;
    let p = await start(b, file, { caps: CAPS });
    await p.evaluate(PREP);
    await p.evaluate(async () => { const rt = await petInit(); petPairEnsure(rt) });

    /* 1. parity */
    for (const [name, setup, button, payload] of CASES) {
      const r = await p.evaluate(async ([setup, button, payload]) => {
        await setCats(setup); seed(); (0, eval)(button); await wait(80); const a = saved();
        await setCats(setup); seed(); const reply = await NativePet.commands.cattery(payload); await wait(80); const c = saved();
        return { same: a === c, a: a === c ? '' : [a.slice(0,700), c.slice(0,700)], reply, changed: true, size: a.length, ops: state.petCatteryOps };
      }, [setup, button, payload]);
      ok('parity: ' + name + ' leaves the saved state the page\'s own button leaves (ok and changed)', r.same && r.reply.ok === true && r.reply.changed === true, r.same ? r.reply : r.a);
      if (payload.op) ok('parity: ' + name + ' keeps its operation id with the save', r.ops.includes(payload.op), r.ops);
    }
    ok('no page errors in the parity runs', p.errors.length === 0, p.errors);

    /* 2. repeat-safety: the same operation twice records once; a litter is not rolled twice */
    let r = await p.evaluate(async ([setup, AT]) => {
      await setCats(setup); seed();
      const pay = { kind: 'breed', op: 'rep-1', mom: 'h0', dad: 'c1' };
      const a = await NativePet.commands.cattery(pay), s1 = saved(), L1 = JSON.stringify(state.cattery.litter);
      seed(99); const b = await NativePet.commands.cattery(pay), same = JSON.stringify(state.cattery.litter) === L1;
      const ka = { kind: 'keep', op: 'rep-2', at: state.cattery.litter.at, i: 0 }, k1 = await NativePet.commands.cattery(ka), n1 = state.cattery.cats.length, k2 = await NativePet.commands.cattery(ka);
      const ren = { kind: 'rename', op: 'rep-3', id: 'c1', name: 'Bas' }, r1 = await NativePet.commands.cattery(ren), r2 = await NativePet.commands.cattery(ren);
      /* a reload: the operation ids are in the save, so a command repeated after the page restarted is still not done twice */
      const kept = JSON.parse(localStorage.getItem('lifemax_rpg_prototype_v1')).petCatteryOps;
      return { a, b, same, k1, k2, sameCats: state.cattery.cats.length === n1, r1, r2, kept };
    }, [BASE(false), AT]);
    ok('repeat: a breed repeated with its id is answered "Already recorded." and rolls nothing', r.a.ok && r.b.ok && r.b.message === 'Already recorded.' && !r.b.changed && r.same, [r.a, r.same]);
    ok('repeat: a keep repeated keeps once (the cattery grew by one)', r.k1.ok && r.k2.message === 'Already recorded.' && r.sameCats, [r.k1, r.k2]);
    ok('repeat: a rename repeated is recorded once; the ids are in the save', r.r2.message === 'Already recorded.' && ['rep-1', 'rep-2', 'rep-3'].every(o => r.kept.includes(o)), [r.r2, r.kept]);

    /* 3. refusals: the page's words, nothing changed */
    r = await p.evaluate(async ([setup, full, AT]) => {
      const go = (x) => NativePet.commands.cattery(x), o = {};
      await setCats(setup); seed();
      const before = saved(), same = () => saved() === before;
      o.stale = await go({ kind: 'keep', op: 'r1', at: AT, i: 0 }); o.noKit = await go({ kind: 'keep', op: 'r2', at: AT, i: 9 });
      o.dadAsMom = await go({ kind: 'breed', op: 'r3', mom: 'c1', dad: 'c1' }); o.sterile = await go({ kind: 'breed', op: 'r4', mom: 'h0', dad: 'c3' }); o.nobody = await go({ kind: 'breed', op: 'r5', mom: 'zz', dad: 'c1' });
      o.release = await go({ kind: 'release', op: 'r6', id: 'nope' }); o.home = await go({ kind: 'rename', op: 'r7', id: 'c1', name: '   ' }); o.long = await go({ kind: 'rename', op: 'r8', id: 'c1', name: 'x'.repeat(15) });
      o.homeHome = await go({ kind: 'home', op: 'r9', id: 'zz' }); o.goal = await go({ kind: 'goal', value: 'xx+' }); o.pick = await go({ kind: 'pick', who: 'mom', key: 'c1' });
      o.kept = same(); o.opsLeft = (state.petCatteryOps || []).length;
      /* a litter replaced while the screen was showing the old one */
      await setCats(setup.replace('litter:null', "litter:{mom:'Ted',dad:'Basil',at:" + (AT + 5) + ",kits:[mk('q1','XX')]}")); const old = await go({ kind: 'keep', op: 'r10', at: AT, i: 0 }); o.oldLitter = old; o.oldKept = state.cattery.litter.kits.length;
      /* a full cattery refuses a keep with the page's toast */
      await setCats(full); const f = await go({ kind: 'keep', op: 'r11', at: AT, i: 0 }); o.full = f; o.fullKept = [state.cattery.litter.kits.length, state.cattery.cats.length];
      const g = await go({ kind: 'stray', op: 'r12' }); o.fullStray = g;
      /* two cats at home: no second adoption, no bringing home */
      await setCats(setup + ';state.pet2=' + JSON.stringify(Object.assign({}, state.pet, { name: 'Lily', alive: true })) + ';petPairData()'); o.slot = await go({ kind: 'home', op: 'r13', id: 'c1' }); o.view = await go({ kind: 'view', view: 'adopt' });
      return o;
    }, [BASE(false).replace('litter:null', 'litter:null'), FULL, AT]);
    ok('refusal: a keep for a litter that is not the one on the screen (or a kitten that is not there) is refused', !r.stale.ok && !r.noKit.ok && r.stale.message === 'That litter has changed.' && r.oldLitter.message === 'That litter has changed.' && r.oldKept === 1, [r.stale, r.oldLitter]);
    ok('refusal: a breed needs a mother and a fertile father from the roster', !r.dadAsMom.ok && !r.sterile.ok && !r.nobody.ok && r.dadAsMom.message === 'Pick a mother and a father.', [r.dadAsMom, r.sterile]);
    ok('refusal: release of a cat that is not there, a blank or a long name, a goal that is not one, a mother that is male', !r.release.ok && !r.home.ok && !r.long.ok && !r.goal.ok && !r.pick.ok && /1 to 14/.test(r.home.message), [r.release, r.home, r.long, r.goal, r.pick]);
    ok('refusals change nothing and leave no operation id behind', r.kept && r.opsLeft === 0, [r.kept, r.opsLeft]);
    ok('refusal: a full cattery refuses a keep and a stray in the page\'s words, the litter and the cats unchanged', !r.full.ok && /full/.test(r.full.message) && r.fullKept.join() === '3,16' && !r.fullStray.ok && /full/.test(r.fullStray.message), [r.full, r.fullKept, r.fullStray]);
    ok('refusal: with two cats at home nobody comes home and no one else is adopted', !r.slot.ok && /second cat slot/.test(r.slot.message) && !r.view.ok, [r.slot, r.view]);

    /* 4. a save the browser refuses is taken back: the state as it was, a refusal in the page's words */
    r = await p.evaluate(async ([setup]) => {
      await setCats(setup); seed(); const before = JSON.stringify([state.pet, state.pet2 || null, state.cattery, state.petCatteryOps]), real = Storage.prototype.setItem;
      Storage.prototype.setItem = () => { throw new Error('full') };
      let out; try { out = await NativePet.commands.cattery({ kind: 'breed', op: 'u1', mom: 'h0', dad: 'c1' }); out.stray = await NativePet.commands.cattery({ kind: 'stray', op: 'u2' }) } finally { Storage.prototype.setItem = real }
      const after = JSON.stringify([state.pet, state.pet2 || null, state.cattery, state.petCatteryOps]);
      const again = await NativePet.commands.cattery({ kind: 'breed', op: 'u1', mom: 'h0', dad: 'c1' });
      return { out, same: before === after, again };
    }, [BASE(false)]);
    ok('unsaved: a breed and a stray the browser refuses are taken back, refused in the page\'s words', !r.out.ok && !r.out.stray.ok && r.same && /Could not save/.test(r.out.message), r.out);
    ok('unsaved: the same operation id sent again after the refusal works (it was never recorded)', r.again.ok && r.again.changed, r.again);

    /* 5. strict shapes */
    r = await p.evaluate(async ([setup]) => {
      await setCats(setup); const go = (x) => NativePet.commands.cattery(x), o = [];
      for (const bad of [null, [], 5, {}, { kind: 'nope' }, { kind: 'stray' }, { kind: 'stray', op: 'x', extra: 1 }, { kind: 'stray', op: 5 }, { kind: 'stray', op: '' }, { kind: 'stray', op: 'a b' }, { kind: 'view' }, { kind: 'view', view: 'x' },
        { kind: 'goal', value: 5 }, { kind: 'keep', op: 'x', at: 'a', i: 0 }, { kind: 'adopt', op: 'x', mode: 'both', kit: 0, name: 'A' }, { kind: 'rename', op: 'x', id: 'c1' }, { kind: 'pick', who: 'son', key: 'c1' }, { kind: 'constructor' }, { kind: '__proto__' }])
        o.push([JSON.stringify(bad), (await go(bad)).ok]);
      return { bad: o.filter(x => x[1]), ops: state.petCatteryOps.length };
    }, [BASE(false)]);
    ok('shapes: malformed commands are refused and change nothing (' + JSON.stringify(r.bad) + ')', r.bad.length === 0 && r.ops === 0, r);
    await p.context().close();

    /* 6. the capability gate and the block, through the kit */
    p = await start(b, file, { caps: ['pet.projection.v1', 'pet.coat.v1', 'pet.panels.v1'] });
    await p.evaluate(PREP);
    r = await p.evaluate(async ([setup]) => { await setCats(setup); navigate('pet'); await until(() => showing(), 6000); const s = latest(); return { has: 'cattery' in s, more: s.words.moreText, ok: (await command('cattery', { kind: 'view', view: 'lab' })).ok } }, [BASE(false)]);
    ok('without pet.cattery.v1 the projection has no cattery and the command is refused', !r.has && /Moods, likes, DNA|toy tray|cattery/.test(r.more) && r.ok === false, r);
    await p.context().close();

    p = await start(b, file, { caps: CAPS });
    await p.evaluate(PREP);
    r = await p.evaluate(async ([setup]) => {
      await setCats(setup); navigate('pet'); await until(() => showing(), 6000);
      const o = { first: latest().cattery };
      o.view = await command('cattery', { kind: 'view', view: 'lab' }); o.lab = latest().cattery;
      for (const v of ['cattery', 'adopt', 'memorial']) { await command('cattery', { kind: 'view', view: v }); o[v] = latest().cattery.detail }
      await command('cattery', { kind: 'view', view: '' }); o.closed = latest().cattery;
      /* the words the page's own lab writes for the same state */
      const dom = new DOMParser().parseFromString('<div>' + (await (async () => { await command('cattery', { kind: 'view', view: 'lab' }); return petBreedHtml() })()) + '</div>', 'text/html').body.textContent;
      const L = latest().cattery.detail, text = (s) => dom.includes(s);
      o.words = { intro: text(L.intro), best: text(L.best), forecast: text(L.forecast), subs: L.litter ? 1 : 0 };
      await command('cattery', { kind: 'view', view: 'cattery' });
      const R = latest().cattery.detail, doms = new DOMParser().parseFromString('<div>' + petBreedHtml() + '</div>', 'text/html');
      o.roster = R.cats.map(c => c.sub); o.pageSubs = [...doms.querySelectorAll('.pet-bl')].pop() ? [...[...doms.querySelectorAll('.pet-bl')].pop().querySelectorAll('.pet-bci small')].map(e => e.textContent) : [];
      o.chips = R.cats.map(c => c.chips.map(x => x.label).join('|')); o.pageChips = [...[...doms.querySelectorAll('.pet-bl')].pop().querySelectorAll('.pet-bci')].map(e => [...e.querySelectorAll('.pet-chip')].map(x => x.textContent).join('|'));
      o.size = JSON.stringify(latest()).length; o.previews = R.cats.every(c => c.preview && typeof c.preview.coat === 'string');
      return o;
    }, [BASE(true)]);
    ok('with the capability the block is there: the entry always, the detail only while a screen is open', r.first.view === '' && r.first.detail === null && r.first.entry.cats === 3 && r.first.entry.max === 16 && r.first.entry.adopt === 'pair' && r.view.ok && r.lab.view === 'lab' && r.lab.detail.kind === 'lab' && r.closed.detail === null, [r.first.entry, r.closed]);
    ok('the lab: goal, pickers (a female mother and a fertile father only), the forecast and the litter of three', r.lab.detail.moms.map(m => m.key).join() === 'h0,c2' && r.lab.detail.dads.map(m => m.key).join() === 'c1' && /^Expected ears: /.test(r.lab.detail.forecast) && r.lab.detail.litter.kits.length === 3 && r.lab.detail.litter.at === AT && r.lab.detail.options.length === 12, r.lab.detail.moms);
    ok('the page\'s words: the lab\'s text, best-in-roster line and forecast are in the page\'s own lab', r.words.intro && r.words.best && r.words.forecast, r.words);
    ok('the roster\'s small lines and shape chips are the page\'s cat cards\' words', JSON.stringify(r.roster) === JSON.stringify(r.pageSubs) && JSON.stringify(r.chips) === JSON.stringify(r.pageChips) && r.previews, [r.roster, r.pageSubs]);
    ok('the adoption detail is the pair\'s (four kittens with the page\'s words), the memorial\'s is empty', r.adopt.mode === 'pair' && r.adopt.kits.length === 4 && r.adopt.title === 'More strays by the kitchen window' && r.memorial.entries.length === 0, r.adopt);
    ok('a projection with a detail stays small (' + r.size + ' bytes)', r.size < 60000, r.size);
    ok('no page errors', p.errors.length === 0, p.errors);
    await p.context().close();
  } finally { await b.close() }
  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail ? 1 : 0);
})();
