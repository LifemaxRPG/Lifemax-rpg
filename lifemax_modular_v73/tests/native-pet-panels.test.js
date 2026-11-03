/* Native Cat tab panels (pet.panels.v1), web half (docs/specs/CAT_TAB_PLAN.md, CT6 part A):
   - the page's DOM renderers (the mind row, the inner-life panel, the likes bars, the genetics panel, the Together card, the toy tray)
     were split into data functions and HTML functions; the HTML they write is byte-identical to what they wrote before (golden file
     fixtures/pet-panels-golden.json, captured from the code before the split) for nine cat states;
   - the projection's `panels` block against what the page's DOM shows for those states: young kitten, a stray, a bonded adult,
     hungry, on vacation, two cats (both cards), genetic coats (female, male, sterile) - every word is the page's;
   - the block only with the capability, and the projection still writes nothing;
   - the commands: focus (the Together card's switcher), buyToy (the shop's rules: Sparks, repeat, failed save), expand (the genome);
   - the page's room loop and the existing projection are unchanged.
   A fake NativePet plugin stands in for Android. Run: NODE_PATH=$(npm root -g) node tests/native-pet-panels.test.js [built html] */
const { chromium } = require('playwright');
const file = require('./current-page')(process.argv[2]);
const { start, capture, readGolden, SCENARIOS } = require('./native-pet-panels.capture');
const { CAT: CATJS } = require('./native-pet-panels.scenarios');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const CAPS = ['pet.projection.v1', 'pet.coat.v1', 'pet.panels.v1'];
const CAT_ONE = CATJS('Miso');
const QUICK = !!process.env.QUICK; // QUICK=1 skips the two long per-state loops (the golden HTML and the DOM comparison)

/* PANELMUT=twice|focus runs only the hand-over simulation on a page built with a fault the simulation must catch: the early unlock
   charging twice, or the focus command doing nothing. */
const MUTATIONS = { twice: ['ecoBuy("toy", id);', 'ecoBuy("toy", id); state.sparks -= 200;'], focus: ['petPairFocus(i);', ''] };

/** Random hops between the Cat screen and other views, with focus, buyToy and expand commands sent among them (some while the screen is
    going away) and a level-up now and then. After each step: the Sparks were paid at most once for the toy, the page's focus is the one
    the last accepted focus asked for, a command that was answered was answered once and in the page's words, and the panels still build. */
async function hopSim(b, page, seeds) {
  for (let seed = 1; seed <= seeds; seed++) {
    const p = await start(b, page, { caps: CAPS });
    const r = await p.evaluate(async ([js, seed]) => {
      await setCats(js + ';state.sparks=1000;state.toysBought={}'); const rt = await petInit(); petPairEnsure(rt); await until(() => petPairOn(), 5000);
      let s = seed * 2654435761 >>> 0; const rnd = () => { s = (s + 0x6D2B79F5) >>> 0; let t = s; t = Math.imul(t ^ t >>> 15, t | 1); t ^= t + Math.imul(t ^ t >>> 7, t | 61); return ((t ^ t >>> 14) >>> 0) / 4294967296 };
      const faults = [], log = [], fault = (m) => faults.push(m + ' after [' + log.slice(-4).join(', ') + ']');
      let wantFocus = 0;
      const check = () => {
        const owed = state.toysBought.feather ? 200 : 0;
        if (state.sparks !== 1000 - owed) fault('Sparks ' + state.sparks + ' with feather ' + !!state.toysBought.feather);
        if (PET_PAIR.focus !== wantFocus) fault('page focus ' + PET_PAIR.focus + ', last accepted ' + wantFocus);
        try { const snap = NativePetProjection.snapshot(); if (!snap.panels || !snap.panels.dna || !snap.panels.pair || snap.panels.pair.focus !== snap.focus) fault('the panels do not build for the focus') } catch (e) { fault('projection threw ' + e) }
      };
      const send = async (command, payload) => {
        if (!showing()) return null;
        const n = __calls.length, id = 'h-' + n + '-' + command; const closes = count('close');
        await Capacitor.Plugins.NativePet.__emit('command', { command, payload, requestId: id });
        await until(() => __calls.slice(n).filter(c => c[0] === 'update' && c[1].reply && c[1].reply.requestId === id).length > 0 || count('close') !== closes, 1500);
        const replies = __calls.slice(n).filter(c => c[0] === 'update' && c[1].reply && c[1].reply.requestId === id).map(c => c[1].reply);
        if (replies.length > 1) fault('two replies to ' + id);
        if (!replies.length && count('close') === closes) fault('no reply to ' + id + ' on a screen that stayed open');
        return replies[0] || null;
      };
      for (let step = 0; step < 16; step++) {
        const pick = Math.floor(rnd() * 9); log.push(pick);
        if (pick === 0 || !showing()) { navigate('pet'); await until(() => showing(), 4000) }
        else if (pick === 1) { navigate('dashboard'); await wait(150 + rnd() * 200) }
        else if (pick === 2) { const f = rnd() < 0.5 ? 0 : 1, rep = await send('focus', { i: f }); if (rep && rep.ok) wantFocus = f; if (rep && !rep.ok) fault('focus refused: ' + rep.message) }
        else if (pick === 3) { const rep = await send('buyToy', { id: 'feather' }); if (rep && !rep.ok) fault('buyToy refused: ' + rep.message) }
        else if (pick === 4) { /* the same purchase twice in a row, the second before the first answered */
          const n = __calls.length; for (const i of [1, 2]) Capacitor.Plugins.NativePet.__emit('command', { command: 'buyToy', payload: { id: 'feather' }, requestId: 'twin-' + n + i }); await wait(500) }
        else if (pick === 5) await send('expand', { open: rnd() < 0.5 });
        else if (pick === 6) { const ov = document.createElement('div'); ov.className = 'luov'; document.body.appendChild(ov); await wait(300); ov.remove(); await wait(300) }
        else if (pick === 7) { /* a command, and the screen goes away before it is answered */
          const f = rnd() < 0.5 ? 0 : 1; if (showing()) { Capacitor.Plugins.NativePet.__emit('command', { command: 'focus', payload: { i: f }, requestId: 'gone-' + step }); navigate('dashboard'); await wait(400); wantFocus = PET_PAIR.focus } }
        else { await wait(100) }
        await wait(50); check();
      }
      return { faults, log: log.join(''), sparks: state.sparks };
    }, [SCENARIOS.twoCats, seed]);
    ok('hand-over simulation, sequence ' + seed + ' (' + r.log + ')', r.faults.length === 0, r.faults.slice(0, 3));
    ok('no page errors in sequence ' + seed, p.errors.length === 0, p.errors);
    await p.context().close();
  }
}

(async () => {
  if (process.env.PANELMUT) {
    const [a, b2] = MUTATIONS[process.env.PANELMUT], fs = require('fs'), html = fs.readFileSync(file, 'utf8');
    if (!html.includes(a)) throw Error('mutation ' + process.env.PANELMUT + ' not found');
    const mutated = require('path').join(require('os').tmpdir(), 'lm_panelmut_' + process.pid + '.html'); fs.writeFileSync(mutated, html.replace(a, b2));
    const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
    try { await hopSim(b, mutated, 6) } finally { await b.close(); fs.unlinkSync(mutated) }
    console.log('\n' + pass + ' passed, ' + fail + ' failed (mutation ' + process.env.PANELMUT + ': a failure is the right answer)'); process.exit(fail ? 0 : 1);
  }
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    /* 1. the DOM is byte-identical to the golden capture, state by state */
    const golden = readGolden();
    for (const [name, js] of Object.entries(QUICK ? {} : SCENARIOS)) {
      const p = await start(b, file);
      const got = await capture(p, js, { focus: name === 'twoCats' });
      for (const part of Object.keys(golden[name])) for (const k of Object.keys(golden[name][part])) {
        ok('golden HTML ' + name + '/' + part + '/' + k + (golden[name][part][k] === null ? ' (absent)' : ''), got[part][k] === golden[name][part][k],
          got[part][k] === golden[name][part][k] ? '' : { want: String(golden[name][part][k]).slice(0, 300), got: String(got[part][k]).slice(0, 300) });
      }
      ok('no page errors in ' + name, p.errors.length === 0, p.errors);
      await p.context().close();
    }

    /* 2. the projection's panels against what the page's DOM shows, state by state (the female cats' texts are in "she", as the DOM's) */
    for (const [name, js] of Object.entries(QUICK ? {} : SCENARIOS)) {
      const p = await start(b, file, { caps: CAPS });
      const r = await p.evaluate(async (js) => {
        await setCats(js); const rt = await petInit(); if (state.pet2) { petPairEnsure(rt); await until(() => petPairOn(), 5000) }
        navigate('pet'); await until(() => showing(), 6000);
        petBR(PET_RT).awayFresh = Date.now(); petRenderStatus(true); petToyTrayRender(); await wait(400);   /* the DOM fixer for "she" runs in a microtask */
        await Capacitor.Plugins.NativePet.__emit('refresh', {}); await wait(300);
        const s = latest(), P = s.panels, bad = [], q = (sel, root) => [...(root || document).querySelectorAll(sel)];
        const t = (sel, root) => { const e = (root || document).querySelector(sel); return e ? e.textContent : null };
        const eq = (what, a, b) => { if (JSON.stringify(a) !== JSON.stringify(b)) bad.push({ what, dom: a, panel: b }) };
        const wid = e => +e.querySelector('em').style.width.replace('%', '');
        /* the mind row */
        eq('mind style', t('#petMind .pet-style'), P.mind.style.name); eq('mind mood', t('#petMind .pet-mood-chip'), P.mind.mood);
        eq('mind text', t('#petMind .pet-mind-text'), P.mind.text);
        eq('mind away', t('#petMind .pet-away') ? t('#petMind .pet-away').replace(P.mind.awayTitle, '').trim() : '', P.mind.away);
        /* likes */
        const likeRows = q('#petLikes .pet-like').map(e => [e.querySelector('span').textContent, wid(e)]);
        eq('likes rows', likeRows, P.likes.places.concat(P.likes.toys).map(r => [r.name + (r.fav ? ' ♥' : ''), r.pct]));
        eq('likes lead', document.querySelector('#petLikes p').textContent, P.likes.lead + P.likes.body);
        eq('likes heads', q('#petLikes .pet-likes-h').map(e => e.textContent), [P.likes.places.length ? P.likes.placesTitle : null, P.likes.toys.length ? P.likes.toysTitle : null].filter(Boolean));
        /* inner life */
        eq('inner threads', q('#petInner .pet-thread').map(e => [e.querySelector('span').textContent, wid(e), e.querySelector('small').textContent]), P.inner.threads.map(r => [r.label, r.pct, r.word]));
        eq('inner quirks', q('#petInner .pet-quirk').map(e => [e.querySelector('b').textContent, e.querySelector('small').textContent, e.querySelector('span').textContent]), P.inner.quirks.map(r => [r.name, r.source, r.text]));
        eq('inner memories', q('#petInner .pet-memory').map(e => [e.querySelector('span').textContent, e.querySelector('small').textContent]), P.inner.memories.map(r => [r.text + (r.zone ? ' ' + r.zone : ''), r.ago]));
        eq('inner small print', q('#petInner .pet-brain-stats').map(e => e.textContent), [P.inner.stats, P.inner.comfort].filter(Boolean));
        eq('inner lead', document.querySelector('#petInner p').textContent, P.inner.lead + P.inner.body);
        /* genetics */
        const D = P.dna;
        eq('dna top', q('#petDna .pet-dna-top span').map(e => e.textContent), [D.sex, D.karyotype, D.coat]);
        eq('dna sub', t('#petDna .pet-dna-sub'), D.sub); eq('dna note', t('#petDna .pet-dna-note') || '', D.sterile);
        eq('dna genes', q('#petDna .pet-gene').slice(0, D.rows.length).map(e => [e.querySelector('b').textContent, e.querySelector('code').textContent, e.querySelector('span').textContent]), D.rows.map(r => [r.label, r.gt, r.text]));
        const subs = q('#petDna .pet-dna-sub').map(e => e.textContent);
        eq('dna carries', subs.filter(x => /^Carries/.test(x)), D.carries ? [D.carries.bold + D.carries.rest] : []);
        eq('dna hidden', subs.filter(x => /^Hidden by other genes/.test(x)), D.hidden ? [D.hidden.bold + D.hidden.rest] : []);
        eq('dna temperament', subs.filter(x => /^About half/.test(x)), [D.temperament.intro + D.temperament.reads + '.']);
        const threads = q('#petDna .pet-dna-p').map(e => [e.querySelector('span').textContent, wid(e), e.querySelector('small').textContent]);
        eq('dna bars', threads, D.morph.rows.map(r => [r.label, r.pct, r.note]).concat(D.persona.map(r => [r.label, r.pct, r.word])));
        const tail = q('#petDna .pet-gene').pop();
        eq('dna tail', [tail.querySelector('b').textContent, tail.querySelector('code').textContent, tail.querySelector('span').textContent], [D.morph.tail.label, D.morph.tail.gt, D.morph.tail.text]);
        eq('dna mutations', subs.filter(x => /^New mutation/.test(x)), D.mutations ? [D.mutations.lead + D.mutations.list + '.'] : []);
        eq('dna genome title', t('#petDna .pet-genome summary'), D.genome.title); eq('dna genome closed', D.genome.loci.length, 0);
        eq('dna genome count', q('#petDna .pet-genome-t > div').length - 1, D.genome.count);
        /* the Together card */
        if (state.pet2) {
          const C = P.pair.card;
          eq('pair names', t('#petPair h3'), C.a + ' & ' + C.b); eq('pair level', t('#petPair .pet-pair-lvl'), C.level);
          eq('pair bar', +document.querySelector('#petPair .pet-pair-bar i').style.width.replace('%', ''), C.pct);
          eq('pair text', t('#petPair > .pet-pair-text'), C.text);
          eq('pair away', t('#petPair > .pet-away') ? t('#petPair > .pet-away').replace(C.awayTitle, '') : '', C.away);
          eq('pair counts', t('#petPair > .pet-pair-counts'), C.counts);
          eq('pair rank', q('#petPair .pet-rank .pet-thread').map(e => [e.querySelector('span').textContent, wid(e), e.querySelector('small').textContent]), C.rank.rows.map(r => [r.name, r.pct, r.word]));
          eq('pair rank line', t('#petPair .pet-rank .pet-pair-text'), C.rank.line.bold + C.rank.line.rest);
          eq('pair last', q('#petPair > .pet-pair-counts').pop().textContent, C.last);
          eq('pair switcher', P.pair.cats.map(c => c.name), [state.pet.name, state.pet2.name]);
        } else eq('no pair with one cat', P.pair, null);
        /* the toys */
        eq('toys', q('#petToyGrid .pet-toy:not(.pet-toy-any)').map(e => [e.querySelector('b').textContent, e.querySelector('small').textContent, !e.classList.contains('locked')]), P.toys.items.map(r => [r.name, r.word, r.unlocked]));
        eq('sparks', P.toys.sparks, ecoSparks());
        return { bad, size: JSON.stringify(P).length, female: petSexOf(state.pet) === 'f', text: P.mind.text };
      }, js);
      ok('panels equal the page DOM: ' + name + ' (' + r.size + ' bytes)', r.bad.length === 0, r.bad.slice(0, 3));
      if (name === 'geneticFemale') ok('a female cat\'s panels are in "she", as the DOM\'s', r.female && /\b(she|her)\b/i.test(r.text) && !/\b(he|his|him)\b/i.test(r.text), r.text);
      ok('no page errors in ' + name, p.errors.length === 0, p.errors);
      await p.context().close();
    }

    /* 3. the capability: without it the projection is the old one, with it the block comes and the old one-line words give way */
    let p = await start(b, file, { caps: ['pet.projection.v1', 'pet.coat.v1'] });
    let r = await p.evaluate(async (js) => { await setCats(js); const rt = await petInit(); petPairEnsure(rt); await until(() => petPairOn(), 5000); navigate('pet'); await until(() => showing(), 6000); const s = latest(); return { panels: 'panels' in s, together: s.together, more: s.words.moreText } }, SCENARIOS.twoCats);
    ok('an app without pet.panels.v1 gets the projection it always did (no panels, the old line about two cats and the page)', !r.panels && /share the house/.test(r.together) && /Moods, likes, DNA/.test(r.more), r);
    await p.context().close();
    p = await start(b, file, { caps: CAPS });
    r = await p.evaluate(async (js) => { await setCats(js); const rt = await petInit(); petPairEnsure(rt); await until(() => petPairOn(), 5000); navigate('pet'); await until(() => showing(), 6000); const s = latest(); return { panels: 'panels' in s, together: s.together, more: s.words.moreText } }, SCENARIOS.twoCats);
    ok('with pet.panels.v1 the block comes, and the line that sent the owner to the page for the Together card is gone', r.panels && r.together === '' && !/Moods, likes, DNA/.test(r.more), r);

    /* 4. the projection with panels writes nothing and changes nothing, for a cat with no brain, likes or genes record yet */
    r = await p.evaluate(async (js) => {
      await setCats(js); await wait(600); delete state.pet.brain; delete state.pet.likes; delete state.pet.dna;   /* the page's own view has made them by now */
      const before = JSON.stringify(state), writes = []; const set = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { writes.push(k); return set.apply(this, arguments) };
      const a = NativePetProjection.snapshot(), c = NativePetProjection.snapshot(); Storage.prototype.setItem = set;
      return { same: JSON.stringify(state) === before, writes: writes.length, hasBrain: !!state.pet.brain, hasDna: !!state.pet.dna, hasLikes: !!state.pet.likes, panels: !!a.panels && !!a.panels.mind && !!a.panels.dna && !!a.panels.inner, seq: c.sequence - a.sequence } }, SCENARIOS.stray);
    ok('the panels write nothing and make no brain, genes or likes record on the cat', r.same && r.writes === 0 && !r.hasBrain && !r.hasDna && !r.hasLikes && r.panels && r.seq === 1, r);
    r = await p.evaluate(() => { const a = JSON.stringify(NativePetPanels.build(state.pet, 0, Date.now()).dna), b = JSON.stringify(NativePetPanels.build(state.pet, 0, Date.now()).dna); return a === b });
    ok('the same cat gives the same genetics panel twice', r === true);
    await p.context().close();

    /* 5. focus: the Together card's switcher */
    p = await start(b, file, { caps: CAPS });
    r = await p.evaluate(async (js) => {
      await setCats(js); const rt = await petInit(); petPairEnsure(rt); await until(() => petPairOn(), 5000); navigate('pet'); await until(() => showing(), 6000);
      const out = {}; out.first = [latest().focus, latest().name, latest().panels.pair.focus];
      out.one = await command('focus', { i: 1 }); const s = latest();
      out.after = [s.focus, s.name, s.panels.pair.focus, s.panels.mind.text.split(' ')[0], s.panels.likes.lead, s.panels.inner.lead, s.care.name, PET_PAIR.focus];
      out.again = await command('focus', { i: 1 }); out.bad = await command('focus', { i: 2 }); out.str = await command('focus', { i: '1' }); out.none = await command('focus', {});
      out.zero = await command('focus', { i: 0 }); out.back = [latest().focus, latest().name];
      return out }, SCENARIOS.twoCats);
    ok('focus 1: the page\'s switcher runs (petPairFocus), the next projection is about the second cat, every panel with it', r.first.join() === '0,Ted,0' && r.one.ok && r.one.changed && r.after.join() === '1,Lily,1,Lily,What Lily likes.,Lily’s inner world.,Lily,1', r);
    ok('focus: the same cat again changes nothing, a cat that does not exist, a text and nothing are refused, 0 brings the first cat back', r.again.message === 'Already recorded.' && !r.again.changed && !r.bad.ok && !r.str.ok && !r.none.ok && r.zero.ok && r.back.join() === '0,Ted', r);
    await p.context().close();
    p = await start(b, file, { caps: CAPS });
    r = await p.evaluate(async (one) => { await setCats('state.pet=' + one); navigate('pet'); await until(() => showing(), 6000); return await command('focus', { i: 1 }) }, CAT_ONE);
    ok('focus 1 with one cat is refused in words', !r.ok && /only one cat/.test(r.message), r);
    await p.context().close();

    /* 6. buyToy: the Rewards shop's rule, ok only once saved */
    p = await start(b, file, { caps: CAPS });
    r = await p.evaluate(async (js) => {
      await setCats(js + ';state.sparks=1000;state.toysBought={}'); navigate('pet'); await until(() => showing(), 6000);
      const out = {}, toy = id => latest().panels.toys.items.find(t => t.id === id);
      out.before = toy('feather'); out.buy = await command('buyToy', { id: 'feather' }); out.notice = latest().notice;
      out.after = toy('feather'); out.sparks = [state.sparks, latest().panels.toys.sparks, state.toysBought.feather, JSON.parse(localStorage.getItem('lifemax_rpg_prototype_v1')).toysBought.feather];
      out.again = await command('buyToy', { id: 'feather' }); out.sparksAgain = state.sparks;
      out.free = await command('buyToy', { id: 'ball_a' }); out.unknown = await command('buyToy', { id: 'nope' }); out.num = await command('buyToy', { id: 5 }); out.none = await command('buyToy', {});
      state.sparks = 10; save(); out.poor = await command('buyToy', { id: 'spring' }); out.poorState = [state.sparks, !!state.toysBought.spring];
      state.sparks = 1000; const real = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
      try { out.unsaved = await command('buyToy', { id: 'spring' }) } finally { Storage.prototype.setItem = real }
      out.unsavedState = [state.sparks, !!state.toysBought.spring];
      return out }, SCENARIOS.stray);
    ok('buyToy: locked before, unlocked and bought after, the price paid, saved, ok and changed', r.before.unlocked === false && r.before.bought === false && r.before.price === 200 && /^Unlocks at /.test(r.before.word) && r.buy.ok && r.buy.changed && r.after.bought && r.after.unlocked && r.sparks.join() === '800,800,1,1', r);
    ok('buyToy: the same toy again changes nothing and costs nothing; a toy with no price, an unknown id, a number and nothing are refused', r.again.message === 'Already recorded.' && !r.again.changed && r.sparksAgain === 800 && !r.free.ok && !r.unknown.ok && !r.num.ok && !r.none.ok, r);
    ok('buyToy: too few Sparks is the shop\'s refusal in its words and changes nothing', !r.poor.ok && /Not enough Sparks/.test(r.poor.message) && r.poorState.join() === '10,false', r);
    ok('buyToy: a save the browser refuses is taken back (the Sparks and the toy as they were)', !r.unsaved.ok && r.unsavedState.join() === '1000,false', r);
    ok('buyToy: the shop\'s toast is the reply\'s message, not also the screen\'s notice', r.buy.message === 'Toy unlocked for your cat' && r.notice === null, [r.buy.message, r.notice]);
    await p.context().close();

    /* 7. expand: the genome table is in the projection only while open */
    p = await start(b, file, { caps: CAPS });
    r = await p.evaluate(async (js) => {
      await setCats(js); navigate('pet'); await until(() => showing(), 6000);
      const out = {}, g = () => latest().panels.dna.genome;
      out.closed = [g().loci.length, g().count, latest().panels.dna.genomeOpen, JSON.stringify(latest()).length];
      out.open = await command('expand', { open: true }); out.opened = [g().loci.length, latest().panels.dna.genomeOpen, JSON.stringify(latest()).length, g().loci[0]];
      out.shut = await command('expand', { open: false }); out.shutLoci = g().loci.length; out.bad = await command('expand', { open: 'yes' }); out.none = await command('expand', {});
      return out }, SCENARIOS.geneticFemale);
    ok('expand: the genome rows come only while the table is open (and go when it closes)', r.closed[0] === 0 && r.closed[1] >= 20 && r.closed[2] === false && r.open.ok && r.opened[0] === r.closed[1] && r.opened[1] === true && r.opened[2] > r.closed[3] && r.shut.ok && r.shutLoci === 0, r);
    ok('expand: a locus row has the page\'s words; a non-boolean and nothing are refused', typeof r.opened[3].locus === 'string' && 'mom' in r.opened[3] && !r.bad.ok && !r.none.ok, r.opened[3]);
    ok('no page errors', p.errors.length === 0, p.errors);
    await p.context().close();

    /* 8. hand-over: the panel commands among tab hops, a closing screen and a level-up (a seeded simulation, 12 sequences of 16 steps) */
    await hopSim(b, file, 12);
  } finally { await b.close() }
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); process.exit(fail ? 1 : 0);
})();
