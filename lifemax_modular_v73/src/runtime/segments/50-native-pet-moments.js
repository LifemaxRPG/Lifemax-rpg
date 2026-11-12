/* Native cat reports to the album and the likes (docs/specs/CAT_BRAIN_PLAN.md 2.2 and 2.5, work package C1): while the native brain runs the
   cats the page's own brain does not tick, so its detectors for the album and the favourites would go quiet. The native brain tells the
   page what the web brain would have noticed, and the page records it exactly as its own functions do. The page stays the only writer:
   - the `moment` command {op, cat, kind, at, quirk?}: one of the album's first moments (first_glass, first_co, first_zoomies, first_belly,
     first_vigil, first_trophy), a returned slow blink (blink), the owner stopping when asked (respect) or an acquired quirk (quirk, named).
     It sets what petBrainEvent / petQuirkCount would have set in the cat's brain (mem.fx, qk.acq, through petMemCore) and lets the album
     look again (petAlbumScan / petAlbumEvent), so a moment becomes an album entry once, by the album's own rules.
   - the `gift` command {op, cat}: the owner tapped the present the cat left. Refused unless the cat may give one (Devoted, once a day:
     the world block's giftOk); then p.gifts counts it and lastGiftDay is set, as the page's own gift tap does (16-runtime.js).
   - the `dwell` command {op, cat, day, spots:{id:s}, toys:{id:s}}: seconds the cat spent in places and with toys on a local day. They feed
     the likes the way the page's daily credit does (25-pet-likes.js petLikesDaily: old habits fade 5 % a day, a place earns by the hour)
     and the favourite place and toy follow (petLikesEvaluate); the album notices a new favourite.
   Every command is ok only once the save stored it (a refused save is taken back) and repeat-safe by op id (the last 40 kept in the save,
   state.petMomentOps): a repeat is answered "Already recorded." and done once. Registered on the Cat tab and the Overview cards.
   Loaded after 50-native-pet-social.js and before 50-native-overview-cards.js. */
(function () {
  const CAP = "pet.world.v1";
  const OP = /^[A-Za-z0-9:._-]{1,80}$/;
  const CAT = /^c[a-z0-9]{1,12}$/;
  const DAY = /^\d{4}-\d{2}-\d{2}$/;
  const OPS_KEPT = 40; // operation ids kept with the save
  const ALREADY = "Already recorded.";
  const FIRST = { first_glass: 1, first_co: 1, first_zoomies: 1, first_belly: 1, first_vigil: 1, first_trophy: 1 }; // brain fx keys, set once
  const KINDS = ["first_glass", "first_co", "first_zoomies", "first_belly", "first_vigil", "first_trophy", "blink", "respect", "quirk"];
  const DAY_S = 86400;
  const DWELL_DAYS = 7; // a report for a day older than this is not credited (it would earn for habits long gone)
  const SPOT_PER_HOUR = 0.7; // likes points an hour in a place: the page's daily credit gives 1 for a nap, 0.8 a rest, 0.5 otherwise
  const TOY_PER_MIN = 0.5; // likes points a minute with a toy: ten minutes of play make a favourite (PET_TOY_FAV_MIN is 4)
  const FADE = 0.95; // old habits fade by this each day (petLikesDaily)
  const FADE_DAYS_MAX = 10; // petLikesDaily catches up at most ten days

  const own = (o, k) => !!o && Object.prototype.hasOwnProperty.call(o, k);
  const living = () => [state.pet, state.pet2].map((p, slot) => ({ p, slot })).filter((c) => c.p && c.p.alive);
  /** run as cat `slot`, on its data, as the page's pair code does */
  const asCat = (slot, fn) => (typeof petAs === "function" ? petAs(slot, fn, null, true) : fn());
  const answer = (ok, changed, message, why) => ({ ok, changed, message, why });
  const refuse = (why, message) => answer(false, false, message || "That was not recorded.", why);
  const ops = () => (Array.isArray(state.petMomentOps) ? state.petMomentOps : []);
  const exact = (p, keys) => !!p && typeof p === "object" && !Array.isArray(p) && Object.keys(p).sort().join(",") === keys;
  const goodOp = (p) => typeof p.op === "string" && OP.test(p.op) && typeof p.cat === "string" && CAT.test(p.cat);
  const scan = () => {
    if (typeof petAlbumScan === "function") petAlbumScan();
  };

  /** the local midnight (ms) of a "YYYY-MM-DD" day, or NaN for a date that does not exist */
  function midnight(day) {
    const [y, m, d] = day.split("-").map(Number), t = new Date(y, m - 1, d);
    return t.getFullYear() === y && t.getMonth() === m - 1 && t.getDate() === d ? t.getTime() : NaN;
  }
  /** whole local days from `a` to `b` midnights (a DST change makes a day 23 or 25 hours: rounded) */
  const daysBetween = (a, b) => Math.round((b - a) / 864e5);

  /** The seconds of one place or toy map: known ids only, whole seconds, no more than a day in all. */
  function secondsOk(m, known) {
    if (!m || typeof m !== "object" || Array.isArray(m)) return false;
    let total = 0;
    for (const k of Object.keys(m)) {
      const s = m[k];
      if (!own(known, k) || !Number.isInteger(s) || s < 0 || s > DAY_S) return false;
      total += s;
    }
    return total <= DAY_S;
  }

  /** the shape of each command, strictly: exactly its keys, well-formed ids, a known kind */
  function shaped(kind, p) {
    if (kind === "moment") {
      const q = p && p.kind === "quirk";
      if (!exact(p, q ? "at,cat,kind,op,quirk" : "at,cat,kind,op") || !goodOp(p) || !KINDS.includes(p.kind)) return false;
      if (!Number.isInteger(p.at) || p.at < 0) return false;
      return !q || (typeof p.quirk === "string" && PET_QUIRK_ACQ.some((a) => a[0] === p.quirk));
    }
    if (kind === "gift") return exact(p, "cat,op") && goodOp(p);
    return exact(p, "cat,day,op,spots,toys") && goodOp(p) && typeof p.day === "string" && DAY.test(p.day) && isFinite(midnight(p.day)) && secondsOk(p.spots, PET_SPOTS) && secondsOk(p.toys, PET_TOYS);
  }

  /** what a moment sets in the cat's brain, as the web brain would have; answers true when it was news */
  function apply(p, m) {
    const b = petBrain(p);
    if (own(FIRST, m.kind)) return petMemCore(b, m.kind, "");
    if (m.kind === "blink" || m.kind === "respect") {
      const news = petMemCore(b, "first_" + m.kind, "");
      return news;
    }
    // an acquired quirk: valid, once, three at most (petQuirkCount's own limits)
    if (b.qk.acq.indexOf(m.quirk) >= 0 || b.qk.acq.length >= 3) return false;
    b.qk.acq.push(m.quirk);
    petMemCore(b, "quirk:" + m.quirk, "", m.quirk);
    return true;
  }

  /** Credits a day's dwell to the likes the way petLikesDaily does; answers true when anything was credited. */
  function credit(p, day, spots, toys) {
    const lk = petLikes(p), at = midnight(day);
    if (!lk || !isFinite(at)) return false;
    if (!lk.dayT) lk.dayT = at;
    else if (at > lk.dayT) {
      const n = Math.min(FADE_DAYS_MAX, daysBetween(lk.dayT, at));
      for (const id of Object.keys(lk.spots)) lk.spots[id] *= Math.pow(FADE, n);
      lk.dayT = at;
    }
    let any = false;
    for (const id of Object.keys(spots)) {
      if (!spots[id]) continue;
      lk.spots[id] = (lk.spots[id] || 0) + (spots[id] / 3600) * SPOT_PER_HOUR;
      any = true;
    }
    for (const id of Object.keys(toys)) {
      if (!toys[id]) continue;
      lk.toys[id] = (lk.toys[id] || 0) + (toys[id] / 60) * TOY_PER_MIN;
      any = true;
    }
    petLikesEvaluate(p);
    return any;
  }

  /** Runs `change(cat)` for one command under the page's save: ok only once stored, taken back when the save is refused. */
  function record(kind, p, change) {
    if (!OP.test(p.op)) return refuse("shape");
    if (ops().includes(p.op)) return answer(true, false, ALREADY, "");
    const cat = living().find((c) => petAlbumCatId(c.p) === p.cat);
    if (!cat) return refuse("refused", "That cat is not here.");
    const before = JSON.stringify([state.pet, state.pet2 || null, own(state, "petMomentOps") ? state.petMomentOps : undefined]);
    const write = window.writeSave, toastOf = window.toast, said = [];
    let stored = true, news = false;
    window.writeSave = function () {
      const r = write.apply(this, arguments);
      if (!r) stored = false;
      return r;
    };
    // what the page would say is said only once the save stored it, so a moment taken back says nothing and its retry says it once
    window.toast = function () { said.push(arguments) };
    try {
      news = change(cat.p, cat.slot);
      state.petMomentOps = ops().concat([p.op]).slice(-OPS_KEPT);
      if (news) scan();
      save();
    } finally {
      window.writeSave = write;
      window.toast = toastOf;
    }
    if (!stored) {
      const [a, b, kept] = JSON.parse(before);
      state.pet = a;
      if (b) state.pet2 = b;
      else delete state.pet2;
      if (kept === undefined) delete state.petMomentOps;
      else state.petMomentOps = kept;
      if (typeof petRenderStatus === "function") petRenderStatus(true);
      return refuse("unsaved", unsavedMessage());
    }
    said.forEach((a) => toastOf.apply(window, a));
    return answer(true, !!news, news ? "" : ALREADY, "");
  }

  function moment(p) {
    return record("moment", p, (cat) => {
      const news = apply(cat, p);
      if (news && p.kind === "blink") petAlbumEvent("blink");
      else if (news && p.kind === "respect") petAlbumEvent("respect");
      else if (news && p.kind === "quirk") toast(cat.name + " has a new habit: " + PET_QUIRKS[p.quirk].name.toLowerCase() + ".");
      return news;
    });
  }
  function gift(p) {
    const cat = living().find((c) => petAlbumCatId(c.p) === p.cat);
    if (cat && !ops().includes(p.op) && !(petLevel(cat.p) >= 5 && cat.p.lastGiftDay !== petDay())) return refuse("refused", "There is no present to take.");
    return record("gift", p, (c, slot) => {
      c.gifts = (c.gifts || 0) + 1;
      c.lastGiftDay = petDay();
      asCat(slot, () => petBrainEvent("gift")); // the brain event is the page's, for this cat (a second cat's own runtime)
      toast(c.name + " brought you a present. " + c.gifts + " so far.");
      return true;
    });
  }
  function dwell(p) {
    const at = midnight(p.day), tomorrow = midnight(petDay()) + 36 * 36e5;
    if (!isFinite(at) || at > tomorrow || at < midnight(petDay()) - DWELL_DAYS * 864e5) {
      return ops().includes(p.op) ? answer(true, false, ALREADY, "") : refuse("refused", "That day is not credited.");
    }
    return record("dwell", p, (c) => credit(c, p.day, p.spots, p.toys));
  }

  /** The command for kind `kind` ("moment", "gift" or "dwell") on a page whose screen said pet.world.v1 (`has`). */
  function command(kind, has) {
    const run = kind === "moment" ? moment : kind === "gift" ? gift : dwell;
    return function reported(p) {
      if (!has(CAP) || !shaped(kind, p)) return refuse("shape");
      return run(p);
    };
  }

  window.NativePetMoments = { capability: CAP, command, shaped, credit, SPOT_PER_HOUR, TOY_PER_MIN };
  if (window.NativePet) for (const k of ["moment", "gift", "dwell"]) NativePet.commands[k] = command(k, (c) => NativePet.has(c));
})();
