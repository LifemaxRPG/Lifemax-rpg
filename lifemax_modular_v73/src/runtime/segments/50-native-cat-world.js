/* Cat world contract (pet.world.v1, docs/specs/CAT_BRAIN_PLAN.md CB0): the page's side of the native cat brain.
   The page keeps the cats' identity and care economics and stays the only writer of the save; behaviour lives in native.
   - petWorldBlock(now): the `world` block, state not a stream, sent in the Cat tab's and the Overview cards' projections, and only
     to a screen that advertised pet.world.v1 (extend). It reads the page's own data through the page's own functions (identity,
     DNA personality scores, bond, hunger inputs, today's counters, care caps, likes, toys, the pair, the timer and rest-day flags,
     the fed cues) and invents no rule. Reads only: a projection writes nothing.
   - petWorldLegacy(p, now): the one-time import of the web brain (plan 2.4), read from p.brain, p.likes and p.bond without
     migratePetBrainState, so it creates nothing: lossy but sensible, in the shape native seeds a cat's world from.
   - the `world` command: native hands the page its opaque, versioned world blob; the page checks the envelope (version, epoch,
     revision, size, the living cats' ids) and stores state.petWorld with writeSave(). ok only once stored, repeat-safe by op.
     A backup restore raises the world epoch (kept beside the save on this phone, so a backup still comes back exactly as made),
     so a world native still holds from before the restore is refused as stale.
   Nothing here changes what any screen shows yet: the current screens decode the block and ignore it.
   Loaded after 50-native-pet.js (NativePet takes the command) and before 50-native-overview-cards.js. */
(function () {
  const CAP = "pet.world.v1";
  const VERSION = 1; // the world blob versions this page stores
  const BLOB_MAX = 8192; // bytes of the blob's JSON
  const OP = /^[A-Za-z0-9:._-]{1,80}$/;
  const LANDED = 40; // operation ids remembered in memory (the last landed one is also kept with the stored world)
  const STAGE_AT_H = [8, 14, 26, 50, 120]; // petStageOf's hunger stage limits, in hours since the last meal
  const FEED_GAP_H = 4; // petCanFeed: a meal at most every four hours
  const PLAYS_PER_DAY = 2; // petPlayDone counts two a day
  const GIFT_LEVEL = 5; // a gift comes from a Devoted cat, once a day (15-runtime.js petThink)
  const LEGACY_SPOTS = 6; // the heaviest places of the web brain's memory carried over
  const LEGACY_TOYS = 3; // the toys the web brain had left lying about (b.items keeps three)
  const POS_FRESH_MS = 10 * 60000; // a saved position younger than this is where the cat still is (24-pet-depth.js)
  const RHYTHM_FRESH = 5; // a fresh brain's visiting rhythm, 0.05 an hour
  const ENERGY_FRESH = 0.5;
  const VOICE_MIN = 0.88, VOICE_MAX = 1.14; // the voice pitch the web brain draws within
  const ALREADY = "Already recorded.";
  const EPOCH_KEY = "lifemax_pet_world_epoch_v1"; // this phone's world epoch (not in backups: see epochNow)
  let seq = 0, seqKey = "";
  let dnaMemo = { key: "", ps: null, morph: null, sex: "m" };
  const landed = [];

  const r3 = (v) => Math.round(v * 1000) / 1000;
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
  const num = (v, d) => (typeof v === "number" && isFinite(v) ? v : d);
  const own = (o, k) => !!o && Object.prototype.hasOwnProperty.call(o, k);
  const bytes = (s) => new TextEncoder().encode(s).length;
  const today = () => petDay();
  const catId = (p) => petAlbumCatId(p);
  /** the living cats of the house with their slot (0 = state.pet, 1 = state.pet2) */
  const living = () => [state.pet, state.pet2].map((p, slot) => ({ p, slot })).filter((c) => c.p && c.p.alive);
  /** The world's epoch on this phone. It lives beside the save, not in it (EPOCH_KEY), so a backup comes back exactly as it was made
      and a restore, which raises it, is the only thing that changes it. */
  function epochNow() {
    try {
      const n = Number(localStorage.getItem(EPOCH_KEY));
      return Number.isInteger(n) && n >= 0 ? n : 0;
    } catch (e) {
      return 0;
    }
  }
  /** the stored world, when it has the envelope this page writes */
  function stored() {
    const w = state.petWorld;
    return w && typeof w === "object" && Number.isInteger(w.epoch) && Number.isInteger(w.rev) && w.blob && typeof w.blob === "object" ? w : null;
  }

  /** the cat's personality scores, proportions and sex from its genes; computed on a copy (petDnaOf writes a legacy cat's genes) */
  function genes(p) {
    const key = [p.born, p.coat, p.trait, p.dna && p.dna.id].join("|");
    if (dnaMemo.key !== key) {
      const view = Object.assign({}, p, { dna: p.dna ? JSON.parse(JSON.stringify(p.dna)) : p.dna });
      const ph = petPheno(view) || {}, ps = {}, m = ph.morph || {};
      PET_PERSONA.forEach((k) => (ps[k] = r3(clamp(num(ph.ps && ph.ps[k], 0.5), 0, 1))));
      dnaMemo = { key, ps, morph: { lg: r3(num(m.lg, 1)), bl: r3(num(m.bl, 1)) }, sex: petSexOf(view) === "f" ? "f" : "m" };
    }
    return dnaMemo;
  }
  /** scores as shares of the leader (0..100), the ids the page knows only, at most `max` of them */
  function shares(scores, known, max) {
    const e = Object.keys(scores || {}).filter((k) => own(known, k) && num(scores[k], -1) >= 0).map((k) => [k, scores[k]]).sort((a, b) => b[1] - a[1]).slice(0, max);
    const top = Math.max(1, e.length ? e[0][1] : 1), out = {};
    e.forEach(([k, v]) => (out[k] = Math.round((v / top) * 100)));
    return out;
  }
  function likes(p) {
    const lk = p.likes && p.likes.v === 1 ? p.likes : null;
    return {
      spots: shares(lk && lk.spots, PET_SPOTS, Object.keys(PET_SPOTS).length),
      toys: shares(lk && lk.toys, PET_TOYS, Object.keys(PET_TOYS).length),
      favSpot: lk && own(PET_SPOTS, lk.favSpot) ? lk.favSpot : null,
      favToy: lk && own(PET_TOYS, lk.favToy) ? lk.favToy : null,
    };
  }
  /** hunger as the page counts it: the stage now, the inputs, and when the next stage begins (none on vacation, the clock paused) */
  function hunger(p, now) {
    const stage = petStage(p, now), vacation = !!(p.vacation && p.vacation.on);
    return { stage, lastFed: Math.round(p.lastFed), vacation, nextStageAt: vacation || stage >= STAGE_AT_H.length ? null : Math.round(p.lastFed + STAGE_AT_H[stage] * PET_H) };
  }
  /** today's counters without rolling the day (a new day reads 0) and what the care rules allow now */
  function care(p) {
    const d = p.day === today(), plays = d ? p.playsToday | 0 : 0;
    return { fedToday: d ? p.fedToday | 0 : 0, petsToday: d ? p.petsToday | 0 : 0, playsToday: plays, feedAt: Math.round(p.lastFed + FEED_GAP_H * PET_H),
      canPlay: plays < PLAYS_PER_DAY, giftOk: petLevel(p) >= GIFT_LEVEL && p.lastGiftDay !== today() };
  }
  function acquired(p) {
    const acq = p.brain && p.brain.qk && Array.isArray(p.brain.qk.acq) ? p.brain.qk.acq : [];
    return acq.filter((id, i) => own(PET_QUIRKS, id) && PET_QUIRKS[id].acq && acq.indexOf(id) === i);
  }
  function cat(p, slot, now, withLegacy) {
    const g = genes(p);
    const o = {
      id: catId(p), slot, name: String(p.name).slice(0, 14), born: Math.round(p.born), sex: g.sex, coat: p.coat, stage: petGrowIdx(p),
      grownAt: Math.round(p.born + PET_GROW_DAYS * 864e5), ps: g.ps, morph: g.morph, bond: { lvl: petLevel(p), pct: petLevelPct(p) },
      zMax: petDepthMaxZ(p), hunger: hunger(p, now), care: care(p), likes: likes(p),
      toys: Object.keys(PET_TOYS).filter((id) => petToyUnlocked(p, id)), quirks: { acq: acquired(p) },
    };
    if (withLegacy) o.legacy = petWorldLegacy(p, now);
    return o;
  }
  /** the friendship of a house of two as the page keeps it (read only: a house without a record yet reads the page's start) */
  function pair() {
    const a = state.pet, b = state.pet2;
    if (!a || !b || !a.alive || !b.alive) return null;
    const d = state.petPair && state.petPair.v === 1 && state.petPair.a === a.born && state.petPair.b === b.born ? state.petPair : null;
    const rel = r3(clamp(num(d && d.rel, PET_PAIR_CFG.REL_START), 0, 1)), rk = d && d.rk && d.rk.v === 1 ? d.rk : null;
    const gain = d && d.day === today() ? num(d.gain, 0) : 0;
    return { rel, level: PET_PAIR_LEVELS.indexOf(petPairLevel(rel)), lead: rk && (rk.lead === 0 || rk.lead === 1) ? rk.lead : -1, settled: !!(rk && rk.settled),
      gainLeft: r3(clamp(PET_PAIR_CFG.DAY_CAP - gain, 0, PET_PAIR_CFG.DAY_CAP)) };
  }
  /** what the owner is doing that the cats may answer (plan 1.9): a guided workout's clock running, and today's rest day */
  function context() {
    let timer = false;
    try {
      const g = typeof gwrCurrent === "function" ? gwrCurrent() : null;
      timer = !!(g && g.unpausedAt);
    } catch (e) {}
    return { timer, restDay: !!(state.restDays && state.restDays[today()]) };
  }

  /** The `world` block (plan 2.1). seq rises whenever its content changes (with the clock, so a reloaded page never repeats one). */
  function petWorldBlock(now) {
    now = now || Date.now();
    const w = stored(), storedIds = w && w.blob.cats && typeof w.blob.cats === "object" ? w.blob.cats : {};
    const cats = living().map(({ p, slot }) => cat(p, slot, now, !own(storedIds, catId(p))));
    const fed = window.NativePet && typeof NativePet.fedCues === "function" ? NativePet.fedCues(now) : [];
    const block = { v: VERSION, seq: 0, epoch: epochNow(), clockOffsetMs: Math.round((PET_RT && PET_RT.shift) || 0), cats, pair: pair(), context: context(),
      cue: { fed, cheer: [] }, stored: w ? { epoch: w.epoch, rev: w.rev, blob: w.blob } : null };
    const key = JSON.stringify(Object.assign({}, block, { cue: { fed: fed.map((c) => c.id), cheer: [] } }));
    if (key !== seqKey) {
      seqKey = key;
      seq = Math.max(seq + 1, now);
    }
    block.seq = seq;
    return block;
  }

  /** the two innate quirks and the voice pitch migratePetBrainState would draw for this cat (the same seeded draws, nothing kept) */
  function brainDefaults(p, T) {
    const r = petRng(petHash(p.born + "|brain")), pool = PET_QUIRK_INNATE.slice(), inn = [];
    while (inn.length < 2) inn.push(pool.splice(Math.floor(r() * pool.length), 1)[0]);
    return { inn, voice: r3(clamp(T.pitch * (0.92 + r() * 0.16), 0.88, 1.14)) };
  }
  /** The web brain of one cat as native seeds its world from it, read without creating anything (plan 2.4). A cat without a brain
      (a fresh stray, a v77 save with bond points only), or a brain missing a part, gets the page's own defaults for its trait and
      bond: the values migratePetBrainState would have given it. */
  function petWorldLegacy(p, now) {
    now = now || Date.now();
    const b = p && p.brain && typeof p.brain === "object" ? p.brain : null, T = PET_TRAIT_BASE[p.trait] || PET_TRAIT_BASE.curious;
    const f = clamp((p.bondPts || 0) / PET_BOND_AT[PET_BOND_AT.length - 1], 0, 1); // migratePetBrainState's seed from bond points
    const af = (b && b.af) || {}, at = (b && b.at) || {}, mem = b && b.mem && typeof b.mem === "object" ? b.mem : {}, qk = (b && b.qk) || {};
    const unit = (v, d) => r3(clamp(num(v, d), 0, 1));
    const w = mem.w && typeof mem.w === "object" ? mem.w : null, spots = {};
    let glass = 0;
    if (w) {
      Object.keys(w).filter((k) => own(PET_SPOTS, k) && num(w[k], -1) >= 0).sort((x, y) => w[y] - w[x] || (x < y ? -1 : 1)).slice(0, LEGACY_SPOTS)
        .forEach((k) => (spots[k] = Math.round(clamp(w[k], 0, 1) * 100)));
      glass = Math.round(clamp(Object.keys(w).filter((k) => /^front_/.test(k)).reduce((s, k) => s + clamp(num(w[k], 0), 0, 1), 0), 0, 1) * 100);
    } else Object.assign(spots, shares(p.likes && p.likes.v === 1 ? p.likes.spots : null, PET_SPOTS, LEGACY_SPOTS));
    const fx = mem.fx && typeof mem.fx === "object" ? Object.keys(mem.fx).filter((k) => /^first_/.test(k) && own(PET_CORE_TEXT, k) && mem.fx[k]).sort() : [];
    const dflt = brainDefaults(p, T), vo = b && b.vo ? num(b.vo.p, 0) : 0;
    const kept = Array.isArray(qk.inn) ? qk.inn.filter((id, i) => PET_QUIRK_INNATE.includes(id) && qk.inn.indexOf(id) === i).slice(0, 2) : [];
    const inn = kept.length ? kept : dflt.inn;
    const c = qk.c && typeof qk.c === "object" ? qk.c : {}, count = (v) => Math.max(0, Math.floor(num(v, 0)));
    const h = b && b.rt && Array.isArray(b.rt.h) && b.rt.h.length === 24 ? b.rt.h : null;
    const items = b && Array.isArray(b.items) ? b.items : [];
    const bond = p.bond, fresh = bond && typeof bond.posZ === "number" && now - num(bond.at, 0) < POS_FRESH_MS;
    return {
      src: b ? "brain" : "fresh",
      mood: { v: r3(clamp(num(af.v, T.v), -1, 1)), a: r3(clamp(num(af.a, -0.1), -1, 1)) },
      trust: { fam: unit(at.fam, 0.15 + 0.75 * f), tru: unit(at.tru, 0.2 + 0.7 * f), sec: unit(at.sec, 0.2 + 0.65 * f), aut: unit(at.aut, T.aut) },
      energy: unit(b && b.zm && b.zm.e, ENERGY_FRESH),
      spots,
      glass,
      fx,
      qk: { inn, c: { n: count(c.n), t: count(c.t), g: count(c.g), k: count(c.k) } },
      rhythm: h ? h.map((v) => Math.round(clamp(num(v, 0), 0, 1) * 100)) : new Array(24).fill(RHYTHM_FRESH),
      voice: vo >= VOICE_MIN && vo <= VOICE_MAX ? r3(vo) : dflt.voice,
      toys: items.filter((it) => Array.isArray(it) && own(PET_TOYS, it[0]) && isFinite(it[1]) && isFinite(it[2])).slice(-LEGACY_TOYS)
        .map((it) => ({ id: it[0], x: Math.round(petClampHouse(it[1])), z: Math.round(clamp(it[2], 0, 100) * 10) / 10 })),
      pos: fresh ? { x: Math.round(petXOfPosX(num(bond.posX, 0))), z: Math.round(clamp(bond.posZ, 0, 100) * 10) / 10, elev: Math.max(0, Math.round(num(bond.elevationY, 0))) } : null,
    };
  }

  const answer = (ok, changed, message, why) => {
    const w = stored();
    return { ok, changed, message, why, epoch: epochNow(), rev: w ? w.rev : 0 };
  };
  const refuse = (why, message) => answer(false, false, message || "The cats' world was not stored.", why);
  /** The `world` command for a screen whose capabilities `has` answers: {op, epoch, rev, blob}. Checks, in order: the shape (every
      key, an op, whole numbers, a known version, the living cats' ids exactly), the size, then that it is not stale (this epoch,
      a newer revision). Stores the blob as it came, never reading inside it. */
  function command(has) {
    return function world(p) {
      if (!has(CAP) || !p || typeof p !== "object") return refuse("shape");
      const keys = Object.keys(p).sort().join(",");
      if (keys !== "blob,epoch,op,rev" || typeof p.op !== "string" || !OP.test(p.op)) return refuse("shape");
      const w = stored();
      if (landed.includes(p.op) || (w && w.op === p.op)) return answer(true, false, ALREADY, "");
      const blob = p.blob;
      if (!Number.isInteger(p.epoch) || !Number.isInteger(p.rev) || p.rev < 1 || !blob || typeof blob !== "object" || Array.isArray(blob)) return refuse("shape");
      if (!Number.isInteger(blob.v) || blob.v < 1 || blob.v > VERSION || !blob.cats || typeof blob.cats !== "object" || Array.isArray(blob.cats)) return refuse("shape");
      const ids = living().map((c) => catId(c.p)).sort().join(",");
      if (Object.keys(blob.cats).sort().join(",") !== ids) return refuse("shape");
      const json = JSON.stringify(blob);
      if (bytes(json) > BLOB_MAX) return refuse("size");
      if (p.epoch !== epochNow() || (w && p.rev <= w.rev)) return refuse("stale");
      const had = own(state, "petWorld"), before = state.petWorld;
      state.petWorld = { epoch: p.epoch, rev: p.rev, at: Date.now(), op: p.op, blob: JSON.parse(json) };
      if (!writeSave()) {
        if (had) state.petWorld = before;
        else delete state.petWorld;
        return refuse("unsaved", unsavedMessage());
      }
      landed.push(p.op);
      if (landed.length > LANDED) landed.shift();
      return answer(true, true, "", "");
    };
  }

  /** Adds the block to a projection `out` for a screen (its NativeTabKit tab) that advertised pet.world.v1; else leaves it as it was,
      so an older screen gets exactly what it always got. A block that fails to build is left out, not the projection. */
  function extend(out, tab) {
    if (!tab || typeof tab.has !== "function" || !tab.has(CAP)) return out;
    try {
      out.world = petWorldBlock(Date.now());
    } catch (e) {
      console.warn("[cat world] block", e);
    }
    return out;
  }

  /* a backup restore replaces the world: a new epoch, so a world native held from before is refused as stale */
  const applyOnce = window.applyBackup;
  if (typeof applyOnce === "function") {
    window.applyBackup = async function () {
      const r = await applyOnce.apply(this, arguments);
      if (r && r.ok) {
        try {
          localStorage.setItem(EPOCH_KEY, String(epochNow() + 1));
        } catch (e) {
          console.warn("[cat world] epoch not stored", e); // native's next write may then land on the restored world
        }
      }
      return r;
    };
  }

  window.petWorldBlock = petWorldBlock;
  window.petWorldLegacy = petWorldLegacy;
  window.NativeCatWorld = { capability: CAP, extend, command, block: petWorldBlock, legacy: petWorldLegacy };
  if (window.NativePet) NativePet.commands.world = command((c) => NativePet.has(c));
})();
