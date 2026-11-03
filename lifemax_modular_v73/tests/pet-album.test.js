/* The cat's photo album and scrapbook data, page side (50-pet-moments.js, 50-pet-album.js; docs/architecture/PET_SCRAPBOOK.md), on the real page:
   - the catalogue: every high moment fires once at the right time (adoption, each growth stage, each bond level, the first gift, a
     slow blink, a respected boundary, a favourite place and toy, a new habit, 30/100/200 days and the birthdays, a recovery after real
     hunger, the two cats' first nap together, each friendship level, the pecking order settling) and mid/low events never do;
   - the caps: at most WEEK_CAP moments per cat per real week (the rest wait and come when a place frees, or are let go after
     DEFER_MS), duplicates inside the debounce window wait, a rung is recorded once however often the detector looks;
   - idempotence across a reload, two cats with an album each, a cat that passes leaving its album in state.petMemorial, an older
     save without an album (a quiet baseline, not a flood of old moments), a damaged album repaired, an album kept to its size;
   - the album riding in the backup file and coming back through an import;
   - the API: petAlbumList, petAlbumPending, petAlbumPhotoTaken (repeat-safe by op, ok only when the save stored it),
     petScrapbookData, and the strict command shapes;
   - no change to anything else: with the detector off and on, a simulated week of feeding, petting, play and brain events leaves the
     cat, its seeded day plans and the random stream byte-identical (the album field aside).
   Run: NODE_PATH=$(npm root -g) node tests/pet-album.test.js [built html] */
const { chromium } = require('playwright');
const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const DAY = 864e5, BASE = 1760000000000;
/* a page whose clock the test drives (window.__now) and whose random numbers are a fixed stream */
const INIT = (save) => `
  window.__now = ${BASE}; Date.now = () => window.__now;
  let s = 12345; Math.random = () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296 };
  if (!localStorage.getItem('lifemax_rpg_prototype_v1')) localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify(${JSON.stringify(save || { onboardingComplete: true, name: 'N', tutorialDone: true })}));`;

(async () => {
  const b = await chromium.launch(), errs = [];
  const open = async (save, hold) => {
    const ctx = await b.newContext({ viewport: { width: 412, height: 860 } }), p = await ctx.newPage();
    p.on('pageerror', (e) => errs.push(String(e)));
    p.on('console', (m) => { if (/pet album/.test(m.text())) errs.push(m.text()) });
    await p.addInitScript(INIT(save));
    await p.goto('file://' + file, { timeout: 120000 }); await p.waitForTimeout(700);
    return p;
  };
  const days = (p, d) => p.evaluate((ms) => { window.__now += ms }, d * DAY);
  const ids = (p, i = 0) => p.evaluate((c) => petAlbumList(c).map((e) => e.id.split(':').slice(1).join(':')), i);
  const wide = (p) => p.evaluate(() => { PET_ALBUM.WEEK_CAP = 99; PET_ALBUM.DEBOUNCE_MS = 0 });

  /* ---------- 1. adoption, every ladder, every single moment ---------- */
  {
    const p = await open();
    await wide(p);
    const a0 = await p.evaluate(() => { petAdopt('ginger', 'Mo', 'playful'); const l = petAlbumList(0); return { l, stored: JSON.parse(localStorage.getItem(STORE)).pet.album.entries.length } });
    ok('adopting a cat records the adoption, with its caption, pose and a picture waiting', a0.l.length === 1 && a0.l[0].kind === 'adopt' && /^c[0-9a-z]+:adopt:1$/.test(a0.l[0].id) && /Mo came home/.test(a0.l[0].caption) && a0.l[0].pose === 'sit' && a0.l[0].photo === 'pending' && a0.l[0].day === 0, a0.l);
    ok('the album is stored with the save', a0.stored === 1, a0);
    ok('scanning again changes nothing (idempotent)', await p.evaluate(() => { const n = JSON.stringify(state.pet.album); const r = petAlbumScan(); return r.length === 0 && JSON.stringify(state.pet.album) === n }));

    for (const [i, d] of [5.1, 10.1, 15.1, 20.1, 25.1, 30.1].entries()) {
      await days(p, i ? d - [5.1, 10.1, 15.1, 20.1, 25.1, 30.1][i - 1] : d);
      await p.evaluate(() => petAlbumScan());
    }
    const grow = await p.evaluate(() => petAlbumList(0).filter((e) => e.kind === 'grow'));
    ok('each growth stage is recorded once, in order, up to grown up', grow.map((e) => e.n).join() === '1,2,3,4,5,6' && /grown up/.test(grow[5].caption) && /young kitten/.test(grow[1].caption) && grow[5].facts.stageIdx === -1, grow.map((e) => [e.n, e.caption]));
    ok('the 30-day milestone arrives with the grown cat', (await ids(p)).includes('days:30'));

    const bonds = [];
    for (const [pts, n] of [[30, 1], [90, 2], [200, 3], [360, 4], [580, 5]]) { bonds.push(await p.evaluate((x) => { state.pet.bondPts = x; petAlbumScan(); return petAlbumList(0).filter((e) => e.kind === 'bond').map((e) => e.n).join() }, pts)) }
    ok('each bond level reached is recorded once (Wary to Devoted)', bonds.join('|') === '1|1,2|1,2,3|1,2,3,4|1,2,3,4,5', bonds);
    ok('a bond caption names the level', await p.evaluate(() => /Devoted/.test(petAlbumList(0).find((e) => e.id.endsWith('bond:5')).caption)));
    const gap = await p.evaluate(() => { state.pet.bondPts = 0; const n = petAlbumList(0).length; state.pet.bondPts = 580; petAlbumScan(); return petAlbumList(0).length === n });
    ok('a bond the cat already reached is not recorded again', gap);

    const single = await p.evaluate(() => {
      const q = state.pet, before = petAlbumList(0).length;
      q.gifts = 1; petAlbumScan();
      petBrainEvent('blink'); petBrainEvent('respect');
      const b = petBrain(q); ['first_glass', 'first_co', 'first_trophy', 'first_zoomies', 'first_belly', 'first_vigil'].forEach((k) => { b.mem.fx[k] = 1 });
      const lk = petLikes(q); lk.favSpot = 'bed'; lk.favToy = 'mouse';
      petQuirkCount(b, 'k', 12);
      petAlbumScan();
      const l = petAlbumList(0);
      return { added: l.length - before, kinds: l.map((e) => e.kind), cap: l.filter((e) => e.kind === 'fav_spot' || e.kind === 'fav_toy' || e.kind === 'quirk' || e.kind === 'gift').map((e) => e.caption) };
    });
    const want = ['gift', 'blink', 'respect', 'glass', 'comfort', 'trophy', 'zoomies', 'belly', 'vigil', 'fav_spot', 'fav_toy', 'quirk'];
    ok('a gift, a slow blink, a respected boundary, six core memories, a favourite place and toy and a new habit are each recorded', want.every((k) => single.kinds.includes(k)), single);
    ok('captions use the facts (place, toy, habit)', single.cap.some((c) => /favourite place: the /.test(c)) && single.cap.some((c) => /toy mouse/.test(c)) && single.cap.some((c) => /bowl inspector/.test(c)) && single.cap.some((c) => /present/.test(c)), single.cap);
    const again = await p.evaluate(() => { const n = petAlbumList(0).length; const b = petBrain(state.pet); b.mem.fx.first_blink = 1; petBrainEvent('blink'); petBrainEvent('respect'); state.pet.gifts = 5; petAlbumScan(); return petAlbumList(0).length === n });
    ok('the same moments again add nothing', again);

    /* milestone days and birthdays: move the clock */
    await days(p, 70); await p.evaluate(() => petAlbumScan());
    await days(p, 100); await p.evaluate(() => petAlbumScan());
    await days(p, 170); await p.evaluate(() => petAlbumScan());
    await days(p, 400); await p.evaluate(() => petAlbumScan());
    const ms = await p.evaluate(() => petAlbumList(0).filter((e) => e.kind === 'days' || e.kind === 'birthday').map((e) => e.kind + ':' + e.n + '@' + e.day));
    ok('30, 100 and 200 days and then each birthday are recorded', ms.join() === 'days:30@30,days:100@100,days:200@200,birthday:1@370,birthday:2@770'.replace('days:200@200', 'days:200@' + ms[2].split('@')[1]).replace('birthday:1@370', 'birthday:1@' + ms[3].split('@')[1]).replace('birthday:2@770', 'birthday:2@' + ms[4].split('@')[1]) && ms.length === 5, ms);
    ok('a birthday caption says the age', await p.evaluate(() => /turned 2/.test(petAlbumList(0).find((e) => e.id.endsWith('birthday:2')).caption)));
    await p.context().close();
  }

  /* ---------- 2. recovery, tiers, low events ---------- */
  {
    const p = await open();
    await wide(p);
    const r = await p.evaluate(() => {
      petAdopt('gray', 'Pip', 'shy');
      state.pet.born -= 0; state.pet.lastFed = Date.now() - 30 * 36e5;
      const f = petFeed();
      return { f: f.ok, kinds: petAlbumList(0).map((e) => e.kind), cap: (petAlbumList(0).find((e) => e.kind === 'recovery') || {}).caption };
    });
    ok('a meal after real hunger (stage 3 or more) records a recovery', r.f && r.kinds.includes('recovery') && /very hungry/.test(r.cap), r);
    const mild = await p.evaluate(() => { window.__now += 20 * 36e5; state.pet.lastFed = Date.now() - 10 * 36e5; const n = petAlbumList(0).length; petFeed(); return petAlbumList(0).length === n });
    ok('an ordinary meal (peckish) records nothing', mild);
    const low = await p.evaluate(() => {
      const n = petAlbumList(0).length, out = {};
      for (const k of ['feed', 'play', 'toy', 'toy_meh', 'pet_beat', 'startle', 'greet', 'overstim', 'spam', 'levelup', 'auto_done', 'comfort_arrive', 'nothing_at_all']) { petBrainEvent(k); out[k] = petAlbumEvent(k).why }
      petPetDone(); petPetDone(); petPlayDone(); petCheck();
      return { same: petAlbumList(0).length === n, out };
    });
    ok('everyday events (meals, petting, play, toys, startles, level-ups) are low or mid tier and never produce an entry', low.same && Object.values(low.out).every((w) => w === 'tier'), low);
    ok('the catalogue marks only high moments as album kinds', await p.evaluate(() => PET_MOMENTS.every((m) => m.tier === 'high') && Object.values(PET_MOMENT_EVENTS).filter((t) => t === 'high').length === 3 && PET_MOMENTS.every((m) => !Array.isArray(m.pose) ? PET_POSES.includes(m.pose) : m.pose.every((x) => PET_POSES.includes(x)))));
    await p.context().close();
  }

  /* ---------- 3. caps, debounce, deferral ---------- */
  {
    const p = await open();
    const r = await p.evaluate(() => {
      petAdopt('ginger', 'Mo', 'playful');
      const q = state.pet; q.gifts = 1; const b = petBrain(q); b.mem.fx.first_blink = 1; b.mem.fx.first_respect = 1; const lk = petLikes(q); lk.favSpot = 'bed'; lk.favToy = 'mouse';
      petAlbumScan(); return { kinds: petAlbumList(0).map((e) => e.kind), seen: Object.keys(q.album.seen).sort() };
    });
    ok('at most WEEK_CAP (3) moments in a real week; the rest wait, in catalogue order', r.kinds.slice().sort().join() === 'adopt,blink,gift' && r.seen.join() === 'fav_spot,fav_toy,respect', r);
    const wk = await p.evaluate(() => { window.__now += 7.1 * 864e5; petAlbumScan(); return petAlbumList(0).map((e) => e.kind).sort().join() });
    ok('a week later the waiting moments take their turn, in catalogue order, up to the cap again (the new growth stage first)', wk === 'adopt,blink,fav_spot,gift,grow,respect', wk);
    const drop = await p.evaluate(() => {
      PET_ALBUM.WEEK_CAP = 0; const q = state.pet; q.bondPts = 30; petAlbumScan(); const held = Object.keys(q.album.seen).join();
      window.__now += 8.5 * 864e5; petAlbumScan(); PET_ALBUM.WEEK_CAP = 3; const n = petAlbumList(0).length; petAlbumScan();
      return { held, letGo: q.album.w.bond === 1, bond: petAlbumList(0).some((e) => e.kind === 'bond') };
    });
    ok('a moment held back longer than DEFER_MS is let go, not recorded late', drop.held.includes('bond') && drop.letGo && !drop.bond, drop);
    const deb = await p.evaluate(() => {
      PET_ALBUM.WEEK_CAP = 99; const q = state.pet; window.__now += 30 * DAY_MS(); q.bondPts = 90; petAlbumScan(); const a = petAlbumList(0).filter((e) => e.kind === 'bond').length;
      q.bondPts = 200; petAlbumScan(); const b = petAlbumList(0).filter((e) => e.kind === 'bond').length;
      window.__now += 31 * 60e3; petAlbumScan(); const c = petAlbumList(0).filter((e) => e.kind === 'bond').length;
      function DAY_MS() { return 864e5 }
      return [a, b, c];
    });
    ok('the same kind twice inside the debounce window waits for it, then is recorded', deb.join() === '1,1,2', deb);
    await p.context().close();
  }

  /* ---------- 4. two cats ---------- */
  {
    const p = await open();
    await wide(p);
    const r = await p.evaluate(() => {
      petAdopt('ginger', 'Mo', 'playful'); window.__now += 2 * 864e5;
      petPairAdopt('gray', 'Pip', 'shy'); petAlbumScan();
      const d = petPairData();
      return { a: petAlbumList(0).map((e) => e.kind), b: petAlbumList(1).map((e) => e.kind), idsDiffer: state.pet.album.id !== state.pet2.album.id, other: state.pet2.album.entries[0].facts.other };
    });
    ok('each cat has its own album with its own adoption; the second cat\'s is recorded when it moves in', r.a.join() === 'adopt' && r.b.join() === 'adopt' && r.idsDiffer && r.other === 'Mo', r);
    const pair = await p.evaluate(() => {
      window.__now += 3 * 36e5; const d = petPairData(); d.n.nap = 1; d.rel = 0.35; petAlbumScan();
      const kinds = (i) => petAlbumList(i).map((e) => e.kind + (e.kind === 'pair_level' ? ':' + e.n : ''));
      window.__now += 3 * 36e5; d.rel = 0.9; d.rk = { v: 1, cf: [0.5, 0.5], n: 5, w: [3, 2], ups: 0, flips: 0, lead: 0, settled: true, hist: [], day: '' }; petAlbumScan();
      return { first: [kinds(0), kinds(1)], last: kinds(0), cap: petAlbumList(0).find((e) => e.kind === 'pair_nap').caption, poses: petAlbumList(0).filter((e) => e.kind.startsWith('pair') || e.kind === 'rank').map((e) => e.pose) };
    });
    ok('the first nap together and the friendship level reached are in both cats\' albums', pair.first.every((k) => k.includes('pair_nap') && k.includes('pair_level:2')), pair);
    ok('Inseparable and the pecking order settling follow; captions name the other cat; poses are the pair ones', pair.last.includes('pair_level:4') && pair.last.includes('rank') && /Mo and Pip/.test(pair.cap) && pair.poses.every((x) => /other/.test(x)), pair);
    const pend = await p.evaluate(() => petAlbumPending().filter((e) => e.kind === 'pair_nap' && e.cat === 0)[0]);
    ok('a pending pair picture says what to stage: the pose, the cat\'s look and the other cat\'s', pend && pend.pose === 'curl_with_other' && pend.look.coat === 'ginger' && pend.look.other && pend.look.other.coat === 'gray' && typeof pend.look.stage === 'number', pend);
    /* the first cat passes, the second takes over its place with its album */
    const pass = await p.evaluate(() => {
      const mo = state.pet; mo.lastFed = Date.now() - 170 * 36e5; const pipAlbum = JSON.stringify(state.pet2.album);
      petCheck(); window.__now += 1e3;
      return { mem: state.petMemorial.length, memAlbum: state.petMemorial[0].album.entries.length, same: JSON.stringify(state.pet.album) === pipAlbum, name: state.pet.name, hasPet2: !!state.pet2, id: state.petMemorial[0].album.id === mo.album.id };
    });
    ok('when the first cat passes its album is copied into the memorial and the second cat keeps its own', pass.mem === 1 && pass.memAlbum >= 4 && pass.same && pass.name === 'Pip' && !pass.hasPet2 && pass.id, pass);
    await p.context().close();
  }

  /* ---------- 5. memorial, scrapbook, API ---------- */
  {
    const p = await open();
    await wide(p);
    const setup = await p.evaluate(() => {
      petAdopt('tuxedo', 'Miso', 'lazy'); const q = state.pet, b = petBrain(q); const lk = petLikes(q); lk.favSpot = 'couch'; lk.favToy = 'feather'; q.gifts = 2;
      window.__now += 40 * 864e5; q.bondPts = 360; b.mem.fx.first_blink = 1; petAlbumScan();
      const alive = petScrapbookData({ cat: 0 });
      window.__now += 2 * 864e5; q.lastFed = Date.now() - 170 * 36e5; const res = petCheck();
      return { res, alive: alive && alive.alive && alive.moments.length > 4, count: state.petMemorial[0].album.entries.length };
    });
    ok('a cat that goes unfed a week passes; the scrapbook of a living cat can be read meanwhile', setup.res === 'passed' && setup.alive, setup);
    const sc = await p.evaluate(() => petScrapbookData(0));
    ok('the scrapbook of a memorial: ordered moments with captions, the summary and the passing date', sc && sc.title === "Miso's scrapbook" && !sc.alive && sc.passed > 0 && sc.bond.name === 'Fond' && sc.favSpot === 'the sofa' && sc.favToy === 'Feather' && sc.moments.length === sc.moments.slice().sort((a, b) => a.at - b.at).length && sc.moments[0].kind === 'adopt' && sc.moments.every((m) => m.caption && m.pose && m.look && m.taken === false) && sc.days >= 1, sc && { t: sc.title, b: sc.bond, s: sc.favSpot, n: sc.moments.length });
    ok('the scrapbook asks for nothing that is not there', await p.evaluate(() => petScrapbookData(5) === null && petScrapbookData({ memorial: 3 }) === null && petScrapbookData({ cat: 1 }) === null && petScrapbookData({ cat: 0 }) === null && petScrapbookData(null) === null && petScrapbookData({ bogus: 1 }) === null));
    const pend = await p.evaluate(() => petAlbumPending().length);
    ok('pending pictures of a cat that has passed can still be staged (they come from the entry\'s facts)', pend === setup.count && await p.evaluate(() => petAlbumPending().every((e) => e.memorial === 0 && e.cat === -1 && e.look.coat === 'tuxedo')));
    const tk = await p.evaluate(() => {
      const e = petAlbumPending()[0], r1 = petAlbumPhotoTaken(e.id, 'op-1'), r2 = petAlbumPhotoTaken(e.id, 'op-1'), r3 = petAlbumPhotoTaken(e.id, 'op-2');
      const raw = JSON.parse(localStorage.getItem(STORE)).petMemorial[0].album.entries.find((x) => x.id === e.id);
      return { r1, r2, r3, stored: raw.photo, n: petAlbumPending().length, taken: petScrapbookData(0).photosTaken };
    });
    ok('a photo taken is stored with the save; the same op again, or another op for the same picture, changes nothing', tk.r1.ok && tk.r1.changed && tk.r2.ok && !tk.r2.changed && tk.r3.ok && !tk.r3.changed && tk.stored === 'taken' && tk.n === setup.count - 1 && tk.taken === 1, tk);
    const bad = await p.evaluate(() => {
      const id = petAlbumPending()[0].id, set = Storage.prototype.setItem; const out = {};
      out.noOp = petAlbumPhotoTaken(id, ''); out.badId = petAlbumPhotoTaken('nope', 'x'); out.unknown = petAlbumPhotoTaken('cabc:adopt:1', 'x'); out.num = petAlbumPhotoTaken(5, 'x'); out.longOp = petAlbumPhotoTaken(id, 'x'.repeat(81));
      Storage.prototype.setItem = function () { throw new Error('full') };
      try { out.full = petAlbumPhotoTaken(id, 'op-9'); out.still = petAlbumPending().some((e) => e.id === id) } finally { Storage.prototype.setItem = set }
      return out;
    });
    ok('bad input is refused; a save that did not store leaves the picture pending and says so', !bad.noOp.ok && !bad.badId.ok && !bad.unknown.ok && !bad.num.ok && !bad.longOp.ok && !bad.full.ok && bad.still && /save/i.test(bad.full.message), bad);
    const cmd = await p.evaluate(() => {
      const c = PetAlbum.commands, id = petAlbumPending()[0].id;
      return {
        list: c.list({ cat: 0 }).ok, listBad: !c.list({ cat: 2 }).ok && !c.list({}).ok && !c.list({ cat: 0, x: 1 }).ok,
        pending: c.pending({}).ok && c.pending({}).entries.length > 0, pendingBad: !c.pending({ a: 1 }).ok && !c.pending(null).ok,
        photo: c.photoTaken({ id, op: 'c-1' }).ok, photoBad: !c.photoTaken({ id }).ok && !c.photoTaken({ id, op: 'a', extra: 1 }).ok,
        book: c.scrapbook({ memorial: 0 }).scrapbook.name === 'Miso', bookBad: !c.scrapbook({ memorial: 0, cat: 1 }).ok && !c.scrapbook({}).ok && !c.scrapbook({ memorial: 99 }).ok,
      };
    });
    ok('the commands decode strictly (unknown keys, missing keys and bad values are refused)', Object.values(cmd).every(Boolean), cmd);
    ok('nothing is offered to native yet: no plugin, capability or bridge hook mentions the album', await p.evaluate(() => !('NativePetAlbum' in window) && !JSON.stringify(Object.keys(window)).includes('AlbumProjection') && typeof PetAlbum.commands.list === 'function'));
    await p.context().close();
  }

  /* ---------- 6. migration, damage, size, reload ---------- */
  {
    const old = { onboardingComplete: true, name: 'N', tutorialDone: true };
    const p = await open(old);
    const a = await p.evaluate(() => {
      const now = Date.now(); state.pet = { v: 1, name: 'Old', coat: 'black', trait: 'curious', born: now - 100 * 864e5, lastFed: now - 36e5, meals: [], day: getToday(), fedToday: 0, petsToday: 0, playsToday: 0, bondPts: 400, care: 3, vacation: { on: false, since: 0 }, gifts: 2, lastGiftDay: '', alive: true, passed: 0, seen: {}, greeted: 0 };
      save(); const none = petAlbumList(0).length; const r = petAlbumScan();
      return { none, added: r.length, album: !!state.pet.album && state.pet.album.v === 1 && state.pet.album.entries.length === 0, w: Object.keys(state.pet.album.w).sort().join() };
    });
    ok('an older save with a cat and no album gets a quiet baseline: nothing is photographed for the past', a.none === 0 && a.added === 0 && a.album && /adopt/.test(a.w) && /bond/.test(a.w), a);
    const later = await p.evaluate(() => { state.pet.bondPts = 580; petAlbumScan(); return petAlbumList(0).map((e) => e.kind + ':' + e.n).join() });
    ok('what happens afterwards is recorded (Devoted), not the old moments', later === 'bond:5', later);
    const young = await p.evaluate(() => { const now = Date.now(); const q = JSON.parse(JSON.stringify(state.pet)); q.born = now - 36e5; q.bondPts = 0; q.gifts = 0; delete q.album; state.pet = q; petAlbumScan(); return petAlbumList(0).map((e) => e.kind).join() });
    ok('a cat of an older save that arrived in the last hours still gets its adoption', young === 'adopt', young);
    /* damaged album */
    const dmg = await p.evaluate(() => {
      const q = state.pet; q.album = { v: 1, id: 'x', entries: [null, 7, { id: 'a', kind: 'nosuchkind' }, { id: q.album.id + ':blink:1', kind: 'blink', n: 1, at: 5, day: 1, caption: 'c'.repeat(500), pose: 'wrong', facts: { name: 'Old', evil: { a: 1 } }, photo: 'maybe' }], w: 'bad', seen: null };
      petAlbumScan(); const l = petAlbumList(0).filter((e) => e.kind === 'blink');
      return { n: l.length, cap: l[0] && l[0].caption.length, pose: l[0] && l[0].pose, photo: l[0] && l[0].photo, evil: l[0] && 'evil' in l[0].facts, shape: petAlbumShapeOk(q.album) };
    });
    ok('a damaged album is repaired: unusable entries dropped, strings bounded, unknown poses and photo states cleaned', dmg.n === 1 && dmg.cap === 160 && dmg.pose === 'sit' && dmg.photo === null && !dmg.evil && dmg.shape, dmg);
    const size = await p.evaluate(() => {
      PET_ALBUM.WEEK_CAP = 999; PET_ALBUM.DEBOUNCE_MS = 0; const q = state.pet, a = q.album, kinds = ['quirk', 'days', 'bond', 'blink', 'birthday', 'grow'];
      a.entries = [{ id: a.id + ':adopt:1', kind: 'adopt', n: 1, at: 1, day: 0, caption: 'first', pose: 'sit', facts: {}, photo: 'taken' }];
      for (let i = 0; i < 260; i++) a.entries.push({ id: a.id + ':' + kinds[i % 6] + ':' + (i + 100), kind: kinds[i % 6], n: i + 100, at: 10 + i, day: i, caption: 'x', pose: 'sit', facts: {}, photo: 'pending' });
      const m = petMomentOf('gift'); a.w.gift = 0; q.gifts = 1; petAlbumScan();
      return { n: a.entries.length, adopt: a.entries.some((e) => e.kind === 'adopt'), gift: a.entries.some((e) => e.kind === 'gift'), lowGone: a.entries.filter((e) => e.kind === 'quirk').length };
    });
    ok('an album is kept to 200 entries: the least valuable (new habits, then the rest by weight, oldest first) go; adoption and the newest stay', size.n === 200 && size.adopt && size.gift && size.lowGone === 0, size);
    const sz = await p.evaluate(() => { const q = state.pet; q.lastFed = Date.now() - 170 * 36e5; petCheck(); return { n: state.petMemorial[0].album.entries.length, json: JSON.stringify(state.petMemorial[0]).length } });
    ok('a memorial copy is bounded (60 entries, a few kilobytes)', sz.n <= 60 && sz.json < 30000, sz);
    await p.context().close();
  }

  /* ---------- 7. reload and backup ---------- */
  {
    const p = await open();
    await wide(p);
    await p.evaluate(() => { petAdopt('ginger', 'Mo', 'playful'); state.pet.gifts = 1; const b = petBrain(state.pet); b.mem.fx.first_blink = 1; petAlbumScan(); const e = petAlbumList(0).find((x) => x.kind === 'gift'); petAlbumPhotoTaken(e.id, 'keep-1') });
    const before = await p.evaluate(() => ({ album: JSON.stringify(state.pet.album), stored: localStorage.getItem(STORE) }));
    const p2 = await open(JSON.parse(before.stored));
    const after = await p2.evaluate(() => { const was = JSON.stringify(state.pet.album); const r = petAlbumScan(); return { same: was, added: r.length, now: JSON.stringify(state.pet.album) } });
    ok('after a reload the album is the same and the detector adds nothing', after.same === before.album && after.added === 0 && after.now === before.album, { a: after.added });
    ok('a photo marked taken survives the reload', await p2.evaluate(() => petAlbumList(0).find((x) => x.kind === 'gift').photo === 'taken'));
    const bk = await p2.evaluate(async () => {
      const file = JSON.parse(backupJson()), inFile = JSON.stringify(file.state.pet.album);
      window.confirm = () => true; localStorage.removeItem(STORE + '_safety');
      const live = JSON.stringify(state.pet.album);
      state.pet.album.entries = []; state.pet.album.w = {}; // the cat in the app has lost it; the file brings it back
      const r = await applyBackup(file);
      return { inFile: inFile === live, ok: r.ok, restored: JSON.stringify(state.pet.album) === live, stored: JSON.stringify(JSON.parse(localStorage.getItem(STORE)).pet.album) === live };
    });
    ok('the album is in the backup file and an import brings it back (metadata round-trips)', bk.inFile && bk.ok && bk.restored && bk.stored, bk);
    const mem = await p2.evaluate(async () => {
      state.pet.lastFed = Date.now() - 170 * 36e5; petCheck(); const file = JSON.parse(backupJson()); const live = JSON.stringify(state.petMemorial);
      window.confirm = () => true; state.petMemorial = []; const r = await applyBackup(file);
      return { ok: r.ok, same: JSON.stringify(state.petMemorial) === live, has: state.petMemorial[0].album.entries.length > 0 };
    });
    ok('a memorial and its album round-trip through a backup too', mem.ok && mem.same && mem.has, mem);
    await p.context().close(); await p2.context().close();
  }

  /* ---------- 8. nothing else changes: the same week with the detector off and on ---------- */
  {
    const week = async (on) => {
      const p = await open();
      const out = await p.evaluate((on) => {
        PET_ALBUM.on = on; PET_ALBUM.WEEK_CAP = 3;
        const strip = (o) => { const c = JSON.parse(JSON.stringify(o)); delete c.album; return c };
        petAdopt('ginger', 'Mo', 'playful'); petPairAdopt('gray', 'Pip', 'shy');
        const log = [], base = Date.now();
        for (let d = 0; d < 8; d++) {
          window.__now = base + d * 864e5 + 8 * 36e5;
          for (const as of [0, 1]) petAs(as, () => {
            petFeed(); petPetDone(); petPetDone(); petPetDone(); petPlayDone(); petPlayDone();
            for (const k of ['feed', 'blink', 'toy', 'pet_beat', 'gift', 'respect', 'levelup', 'greet']) petBrainEvent(k);
            petQuirkCount(petBrain(petData()), 'k', 3);
          }, [], true);
          window.__now += 5 * 36e5;
          for (const as of [0, 1]) petAs(as, () => { petFeed(); petLikesBump(petData(), 'spot', 'bed', 3); petLikesBump(petData(), 'toy', 'mouse', 2) }, [], true);
          petCheck(base + d * 864e5 + 14 * 36e5);
          const plans = [state.pet, state.pet2].map((q) => JSON.stringify(petBuildDay(q, 'day' + d)));
          log.push(JSON.stringify([strip(state.pet), strip(state.pet2), strip(state.petPair), plans]));
        }
        return { log, rnd: [Math.random(), Math.random(), Math.random()], albums: [state.pet.album ? state.pet.album.entries.length : 0, state.pet2.album ? state.pet2.album.entries.length : 0] };
      }, on);
      await p.context().close(); return out;
    };
    const off = await week(false), on = await week(true);
    ok('with the detector on, a week of care and events leaves the cats, their seeded day plans and the pair record byte-identical to the detector off', off.log.length === 8 && off.log.every((l, i) => l === on.log[i]), { n: off.log.length, diff: off.log.findIndex((l, i) => l !== on.log[i]) });
    ok('the detector draws no random numbers: the stream is where it would have been', JSON.stringify(off.rnd) === JSON.stringify(on.rnd), { off: off.rnd, on: on.rnd });
    ok('the run is not vacuous: the albums filled with the detector on and stayed absent with it off', on.albums[0] >= 3 && on.albums[1] >= 3 && off.albums[0] === 0 && off.albums[1] === 0, { on: on.albums, off: off.albums });
  }

  ok('no page errors, no album warnings', errs.length === 0, errs.slice(0, 5));
  await b.close();
  console.log(`\n${pass} passed, ${fail} failed`); process.exit(fail ? 1 : 0);
})();
