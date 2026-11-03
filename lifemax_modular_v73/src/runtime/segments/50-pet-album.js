/* ---------- The cat's photo album and scrapbook, page side (scrapbook phase S1; docs/architecture/PET_SCRAPBOOK.md).
   The page owns the DATA. Native will later stage a portrait for each pending entry and draw the scrapbook of a cat that has passed;
   until then nothing here is advertised to native and the album only fills up.
   Data (in the save, with the cat, so backups and restores carry it): state.pet.album / state.pet2.album =
     {v:1, id, entries:[{id, kind, n, at, day, caption, pose, facts, photo, op?}], w:{kind: highest rung handled}, seen:{kind: ms}}
   id is the cat's id (c + its birth in base 36); an entry id is `<catId>:<kind>:<n>`; photo is 'pending' until native says it took the
   picture, then 'taken'. w says which rungs are done (recorded or let go), so a moment happens once however often the detector looks.
   The detector (petAlbumScan) READS the cat and writes only the album: no random numbers, no petRng or petMinuteRng, no change to XP,
   timing or any seeded plan. It runs after the page's own events (a meal, petting, play, a brain event, the 60 s check) and finds what
   became true; a moment the weekly cap holds back stays waiting for PET_ALBUM.DEFER_MS and is then let go.
   A cat that already lived here when the album first appeared (an older save) gets a quiet baseline: everything it has already reached
   is marked done, so the album starts with what happens from now on. A cat adopted within PET_ALBUM.FRESH_MS starts with its adoption. */
var PET_ALBUM = {
  on: true, // false turns the detector off (tests compare with and without it)
  v: 1,
  WEEK_CAP: 3, // most moments one cat's album takes in one real week
  WEEK_MS: 7 * 864e5,
  DEBOUNCE_MS: 30 * 60e3, // the same kind is not recorded twice within this
  DEFER_MS: 8 * 864e5, // how long a held-back moment may wait for the weekly cap to free a place
  MAX: 200, // entries kept per cat
  MEM_MAX: 60, // entries copied into a memorial
  MEM_KEEP: 12, // memorials that keep their entries (older ones keep the summary only)
  FRESH_MS: 6 * 36e5,
  CAPTION_MAX: 160,
  busy: false,
};
function petAlbumCatId(p) {
  return "c" + Math.floor(Number(p.born) || 0).toString(36);
}
/* the cats of the house that are alive, with their slot (0 = state.pet, 1 = state.pet2) */
function petAlbumCats() {
  return [state.pet, state.pet2].map((p, i) => ({ p, i })).filter((c) => c.p && c.p.alive);
}
function petAlbumShapeOk(a) {
  return !!a && typeof a === "object" && a.v === PET_ALBUM.v && Array.isArray(a.entries) && a.w && typeof a.w === "object" && a.seen && typeof a.seen === "object" && typeof a.id === "string";
}
/* one entry as the save may hold it, or null when it is not usable (an edited or damaged backup) */
function petAlbumCleanEntry(e) {
  if (!e || typeof e !== "object" || typeof e.id !== "string" || typeof e.kind !== "string" || !petMomentOf(e.kind)) return null;
  const f = e.facts && typeof e.facts === "object" ? e.facts : {};
  const out = {
    id: e.id.slice(0, 60),
    kind: e.kind,
    n: Math.max(0, Math.floor(Number(e.n) || 0)),
    at: Math.max(0, Math.floor(Number(e.at) || 0)),
    day: Math.max(0, Math.floor(Number(e.day) || 0)),
    caption: String(e.caption == null ? "" : e.caption).slice(0, PET_ALBUM.CAPTION_MAX),
    pose: PET_POSES.includes(e.pose) ? e.pose : "sit",
    facts: {},
    photo: e.photo === "taken" ? "taken" : e.photo === "pending" ? "pending" : null,
  };
  for (const k of Object.keys(f)) if (typeof f[k] === "string" || typeof f[k] === "number") out.facts[k] = typeof f[k] === "string" ? f[k].slice(0, 40) : f[k];
  if (typeof e.op === "string" && e.op) out.op = e.op.slice(0, 80);
  return out;
}
/* an album rebuilt from whatever the save holds: the usable entries stay, the rest is made again */
function petAlbumRepair(p, a) {
  const seen = {}, entries = [];
  for (const e of a && Array.isArray(a.entries) ? a.entries : []) {
    const c = petAlbumCleanEntry(e);
    if (c && !seen[c.id]) {
      seen[c.id] = 1;
      entries.push(c);
    }
  }
  const w = {};
  if (a && a.w && typeof a.w === "object") for (const k of Object.keys(a.w)) if (petMomentOf(k) && Number(a.w[k]) > 0) w[k] = Math.floor(Number(a.w[k]));
  for (const e of entries) w[e.kind] = Math.max(w[e.kind] || 0, e.n || 1);
  return { v: PET_ALBUM.v, id: petAlbumCatId(p), entries, w, seen: {} };
}
/* what the page can read about the cat right now */
function petAlbumContext(p, now) {
  const other = state.pet && state.pet2 && state.pet.alive && state.pet2.alive ? (p === state.pet ? state.pet2 : p === state.pet2 ? state.pet : null) : null;
  const d = state.petPair, pair = other && d && d.v === 1 && ((d.a === p.born && d.b === other.born) || (d.b === p.born && d.a === other.born)) ? d : null;
  return { age: Math.max(0, (now + (petNow() - Date.now()) - p.born) / 864e5), other, pair };
}
function petAlbumStageIdx(age) {
  return age >= PET_GROW_DAYS ? -1 : Math.min(5, Math.floor(age / 5));
}
/* the album of a living cat; made when there is none. A cat that is not new gets the quiet baseline: what it has already reached is
   marked done and nothing is photographed for the past. Writes only p.album. */
function petAlbumOf(p, now, create) {
  if (petAlbumShapeOk(p.album) && p.album.id === petAlbumCatId(p)) return p.album;
  if (!create) return null;
  const a = (p.album = petAlbumRepair(p, p.album)), x = petAlbumContext(p, now);
  if (now - p.born >= PET_ALBUM.FRESH_MS) for (const m of PET_MOMENTS) if (!m.event) a.w[m.kind] = Math.max(a.w[m.kind] || 0, m.reach(p, x) || 0);
  return a;
}
function petAlbumFacts(p, m, n, x) {
  const lvl = petLevel(p), st = petAlbumStageIdx(x.age), lk = p.likes && p.likes.v === 1 ? p.likes : null, o = x.other;
  const f = { name: String(p.name).slice(0, 14), days: Math.floor(x.age), stage: PET_STAGE_NAMES[st < 0 ? 6 : st], stageIdx: st, bond: PET_LEVELS[lvl], bondLvl: lvl, coat: String(p.coat).slice(0, 40), trait: p.trait };
  if (o) {
    f.other = String(o.name).slice(0, 14);
    f.otherCoat = String(o.coat).slice(0, 40);
    f.otherStage = petAlbumStageIdx(x.age + (p.born - o.born) / 864e5);
  }
  if (x.pair) f.pair = petPairLevel(x.pair.rel)[1].toLowerCase();
  if (m.kind === "fav_spot" && lk && PET_SPOTS[lk.favSpot]) f.spot = PET_SPOTS[lk.favSpot].name;
  if (m.kind === "fav_toy" && lk && PET_TOYS[lk.favToy]) f.toy = PET_TOYS[lk.favToy].name.toLowerCase();
  if (m.kind === "quirk" && p.brain && p.brain.qk && PET_QUIRKS[p.brain.qk.acq[n - 1]]) f.quirk = PET_QUIRKS[p.brain.qk.acq[n - 1]].name.toLowerCase();
  if (m.kind === "birthday") f.years = n;
  if (m.kind === "recovery") f.hungerStage = 3;
  return f;
}
/* what a moment may do right now: record it, wait (cap or debounce) */
function petAlbumRoom(a, kind, now) {
  const recent = a.entries.filter((e) => e.at > now - PET_ALBUM.WEEK_MS).length;
  if (recent >= PET_ALBUM.WEEK_CAP) return "cap";
  if (a.entries.some((e) => e.kind === kind && Math.abs(now - e.at) < PET_ALBUM.DEBOUNCE_MS)) return "debounce";
  return "";
}
/* the least valuable entries go first when an album is over its size (adoption never goes) */
function petAlbumTrim(a, max) {
  while (a.entries.length > max) {
    let at = -1, wt = 1e9;
    a.entries.forEach((e, i) => {
      const m = petMomentOf(e.kind), w = m ? m.weight : 0;
      if (e.kind !== "adopt" && w < wt) {
        wt = w;
        at = i;
      }
    });
    if (at < 0) break;
    a.entries.splice(at, 1);
  }
}
/* Writes the entry of rung n of kind m into cat p's album. Only the caller decides that it is time; the id keeps it once. */
function petAlbumPut(p, a, m, n, x, now) {
  const id = a.id + ":" + m.kind + ":" + n;
  a.w[m.kind] = Math.max(a.w[m.kind] || 0, n);
  delete a.seen[m.kind];
  if (a.entries.some((e) => e.id === id)) return null;
  const f = petAlbumFacts(p, m, n, x), e = { id, kind: m.kind, n, at: now, day: f.days, caption: petMomentCaption(m, n, f).slice(0, PET_ALBUM.CAPTION_MAX), pose: petMomentPose(m, n), facts: f, photo: "pending" };
  a.entries.push(e);
  petAlbumTrim(a, PET_ALBUM.MAX);
  return e;
}
/* looks at one cat; answers true when the album changed */
function petAlbumScanCat(p, now) {
  const made = !(petAlbumShapeOk(p.album) && p.album.id === petAlbumCatId(p)), a = petAlbumOf(p, now, true), x = petAlbumContext(p, now);
  let changed = made;
  for (const m of PET_MOMENTS) {
    if (m.event) continue;
    const n = m.reach(p, x) || 0;
    if (n <= (a.w[m.kind] || 0)) continue;
    if (!petAlbumRoom(a, m.kind, now)) {
      if (petAlbumPut(p, a, m, n, x, now)) changed = true;
    } else if (!a.seen[m.kind]) {
      a.seen[m.kind] = now;
      changed = true;
    } else if (now - a.seen[m.kind] > PET_ALBUM.DEFER_MS) {
      a.w[m.kind] = n; // let go: it waited its turn and none came
      delete a.seen[m.kind];
      changed = true;
    }
  }
  return changed;
}
/* Looks at every living cat, keeps what is new through the page's normal save. Answers the entries it added. */
function petAlbumScan(now) {
  const added = [];
  if (!PET_ALBUM.on || PET_ALBUM.busy || !state) return added;
  PET_ALBUM.busy = true;
  try {
    now = now || Date.now();
    let changed = false;
    for (const c of petAlbumCats()) {
      const had = new Set(petAlbumShapeOk(c.p.album) ? c.p.album.entries.map((e) => e.id) : []);
      if (petAlbumScanCat(c.p, now)) changed = true;
      for (const e of c.p.album.entries) if (!had.has(e.id)) added.push(e);
    }
    if (petAlbumCompactMemorials()) changed = true;
    if (changed) writeSave();
  } catch (e) {
    console.warn("[pet album]", e);
  } finally {
    PET_ALBUM.busy = false;
  }
  return added;
}
/* An event the page raises. A recovery (fed after real hunger) is a moment of its own; a brain event of a high tier makes the detector
   look at once; everything else is low or mid and never produces an entry. Answers {recorded, why}. */
function petAlbumEvent(kind, o) {
  o = o || {};
  const m = petMomentOf(kind);
  if (m && m.event) {
    const p = o.cat || petData();
    if (!PET_ALBUM.on || !p || !p.alive || PET_ALBUM.busy) return { recorded: false, why: "off" };
    const now = o.now || Date.now(), a = petAlbumOf(p, now, true);
    if ((a.w[m.kind] || 0) >= 1) return { recorded: false, why: "done" };
    if (petAlbumRoom(a, m.kind, now)) return { recorded: false, why: "room" };
    const e = petAlbumPut(p, a, m, 1, petAlbumContext(p, now), now);
    if (e) writeSave();
    return { recorded: !!e, why: "" };
  }
  const tier = PET_MOMENT_EVENTS[kind] || "low";
  if (tier !== "high") return { recorded: false, why: "tier" };
  return { recorded: petAlbumScan(o.now).length > 0, why: "" };
}
/* ----- wrappers: the page's own events call the detector after they have done their work; their results are returned unchanged ----- */
(function () {
  function safely(fn) {
    try {
      return fn();
    } catch (e) {
      console.warn("[pet album]", e);
      return null;
    }
  }
  /** wraps window[name]: before(args) may take a note, after(result, note, args) runs once the original has finished */
  function around(name, before, after) {
    const orig = window[name];
    if (typeof orig !== "function") return;
    window[name] = function () {
      const note = before ? safely(() => before(arguments)) : null, r = orig.apply(this, arguments);
      safely(() => after(r, note, arguments));
      return r;
    };
  }
  around(
    "petFeed",
    (args) => {
      const p = petData();
      return p && p.alive ? { p, hungry: petStage(p, args[0] || Date.now()) >= 3 } : null;
    },
    (r, note) => {
      if (!r || !r.ok) return;
      if (note && note.hungry) petAlbumEvent("recovery", { cat: note.p });
      petAlbumScan();
    }
  );
  for (const name of ["petPetDone", "petPlayDone"]) around(name, null, (r) => r && r.ok && petAlbumScan());
  around("petCheck", null, () => petAlbumScan());
  around("petBrainEvent", null, (r, note, args) => petAlbumEvent(args[0]));
  around("petAdopt", null, () => petAlbumScan());
})();
/* ----- memorial: a cat that passes leaves its album behind in state.petMemorial (called by petCheck at the moment of passing) ----- */
function petAlbumSummary(p) {
  const lk = p.likes && p.likes.v === 1 ? p.likes : null, acq = p.brain && p.brain.qk && Array.isArray(p.brain.qk.acq) ? p.brain.qk.acq : [];
  return {
    spot: lk && PET_SPOTS[lk.favSpot] ? PET_SPOTS[lk.favSpot].name : "",
    toy: lk && PET_TOYS[lk.favToy] ? PET_TOYS[lk.favToy].name : "",
    quirks: acq.filter((q) => PET_QUIRKS[q]).map((q) => PET_QUIRKS[q].name),
    trait: String(p.trait || ""),
    gifts: Math.max(0, Math.floor(p.gifts || 0)),
  };
}
function petAlbumMemorial(p) {
  const a = petAlbumShapeOk(p.album) ? p.album : petAlbumRepair(p, p.album), entries = a.entries.map(petAlbumCleanEntry).filter(Boolean);
  const copy = { v: PET_ALBUM.v, id: a.id, entries, w: {}, seen: {}, sum: petAlbumSummary(p) };
  petAlbumTrim(copy, PET_ALBUM.MEM_MAX);
  return copy;
}
/* memorials beyond the newest MEM_KEEP keep their summary but not their entries, so the save cannot grow without limit */
function petAlbumCompactMemorials() {
  const list = state.petMemorial;
  let changed = false;
  if (!Array.isArray(list)) return false;
  list.slice(0, Math.max(0, list.length - PET_ALBUM.MEM_KEEP)).forEach((m) => {
    if (m && m.album && m.album.entries && m.album.entries.length) {
      m.album.count = m.album.entries.length;
      m.album.entries = [];
      changed = true;
    }
  });
  return changed;
}
/* ----- the page API for the native phase that comes later (pure reads, and one command) ----- */
function petAlbumView(e, extra) {
  return Object.assign({ id: e.id, kind: e.kind, n: e.n, at: e.at, day: e.day, caption: e.caption, pose: e.pose, photo: e.photo, facts: Object.assign({}, e.facts) }, extra || {});
}
function petAlbumSorted(entries) {
  return entries.slice().sort((a, b) => a.at - b.at || (a.id < b.id ? -1 : 1));
}
/* the entries of a living cat (0 = state.pet, 1 = state.pet2), oldest first; [] for no cat or no album yet */
function petAlbumList(catIndex) {
  const p = catIndex === 1 ? state.pet2 : catIndex === 0 ? state.pet : null;
  if (!p || !p.alive || !petAlbumShapeOk(p.album)) return [];
  return petAlbumSorted(p.album.entries.map(petAlbumCleanEntry).filter(Boolean)).map((e) => petAlbumView(e));
}
function petAlbumLook(e) {
  const f = e.facts || {}, m = petMomentOf(e.kind);
  return { coat: f.coat || "", stage: typeof f.stageIdx === "number" ? f.stageIdx : -1, trait: f.trait || "", other: m && m.appear === "pair" && f.other ? { coat: f.otherCoat || "", stage: typeof f.otherStage === "number" ? f.otherStage : -1 } : null };
}
/* what native should photograph: every entry still waiting, with the pose and how the cat(s) looked then; the cats of the house first,
   then cats that have passed (their pictures can still be staged from the entry's facts) */
function petAlbumPending() {
  const out = [];
  const take = (entries, where) => petAlbumSorted(entries.map(petAlbumCleanEntry).filter(Boolean)).forEach((e) => e.photo === "pending" && out.push(petAlbumView(e, Object.assign({ catId: e.id.split(":")[0], look: petAlbumLook(e) }, where))));
  for (const c of petAlbumCats()) if (petAlbumShapeOk(c.p.album)) take(c.p.album.entries, { cat: c.i, memorial: -1 });
  (state.petMemorial || []).forEach((m, i) => m && m.album && Array.isArray(m.album.entries) && take(m.album.entries, { cat: -1, memorial: i }));
  return out;
}
var PET_ALBUM_ALREADY = "Already recorded.";
/* Marks one entry's picture as taken, once the save has stored it. Repeat-safe by op: the same op again answers ok and changes nothing.
   Answers {ok, changed, message}. */
function petAlbumPhotoTaken(id, op) {
  const no = (message) => ({ ok: false, changed: false, message }), good = (changed, message) => ({ ok: true, changed, message: message || "" });
  if (typeof id !== "string" || !/^c[0-9a-z]{1,12}:[a-z_]{1,20}:\d{1,5}$/.test(id) || typeof op !== "string" || !op || op.length > 80) return no("Something went wrong. Nothing was changed.");
  let hit = null;
  for (const c of petAlbumCats()) if (petAlbumShapeOk(c.p.album)) hit = hit || c.p.album.entries.find((e) => e.id === id);
  if (!hit) (state.petMemorial || []).forEach((m) => m && m.album && Array.isArray(m.album.entries) && (hit = hit || m.album.entries.find((e) => e.id === id)));
  if (!hit) return no("That moment is not in the album.");
  if (hit.photo === "taken") return good(false, PET_ALBUM_ALREADY);
  if (hit.photo !== "pending") return no("That moment has no picture to take.");
  hit.photo = "taken";
  hit.op = op;
  if (writeSave()) return good(true);
  hit.photo = "pending";
  delete hit.op;
  return no(unsavedMessage());
}
/* The scrapbook of a cat: ref {memorial: index} (a cat that has passed) or {cat: 0|1} (a living cat's, to look at while it is here).
   A bare number is a memorial index. Answers null when there is nothing there. */
function petScrapbookData(ref) {
  if (typeof ref === "number") ref = { memorial: ref };
  if (!ref || typeof ref !== "object") return null;
  let name, coat, days, passed, bond, sum, entries, born, alive;
  if (typeof ref.memorial === "number") {
    const m = (state.petMemorial || [])[ref.memorial];
    if (!m) return null;
    const a = m.album && Array.isArray(m.album.entries) ? m.album : { entries: [], sum: {} };
    [name, coat, days, passed, bond, sum, entries, born, alive] = [m.name, m.coat, m.days, m.passed, m.bond, a.sum || {}, a.entries, 0, false];
  } else if (ref.cat === 0 || ref.cat === 1) {
    const p = ref.cat === 1 ? state.pet2 : state.pet;
    if (!p || !p.alive) return null;
    [name, coat, days, passed, bond, sum, entries, born, alive] = [p.name, p.coat, Math.max(1, Math.round((Date.now() - p.born) / 864e5)), 0, petLevel(p), petAlbumSummary(p), petAlbumShapeOk(p.album) ? p.album.entries : [], p.born, true];
  } else return null;
  const moments = petAlbumSorted(entries.map(petAlbumCleanEntry).filter(Boolean)).map((e) => petAlbumView(e, { taken: e.photo === "taken", look: petAlbumLook(e) }));
  return {
    name: String(name || ""),
    coat: String(coat || ""),
    alive,
    days: Number(days) || 0,
    passed: Number(passed) || 0,
    born,
    bond: { level: Number(bond) || 0, name: PET_LEVELS[Number(bond) || 0] || PET_LEVELS[0] },
    favSpot: sum.spot || "",
    favToy: sum.toy || "",
    quirks: Array.isArray(sum.quirks) ? sum.quirks.slice(0, 4) : [],
    title: String(name || "") + "'s scrapbook",
    moments,
    photosTaken: moments.filter((m) => m.taken).length,
  };
}
/* Commands in the shape the Cat tab's other commands have (strict keys, an {ok, message} answer). No plugin offers them yet: native
   consumers come in a later phase and will register them behind a capability of their own. */
(function () {
  const keys = (p, want) => !!p && typeof p === "object" && Object.keys(p).sort().join() === want.slice().sort().join();
  const bad = () => ({ ok: false, changed: false, message: "Something went wrong. Nothing was changed." });
  window.PetAlbum = {
    commands: {
      list: (p) => (keys(p, ["cat"]) && (p.cat === 0 || p.cat === 1) ? { ok: true, entries: petAlbumList(p.cat) } : bad()),
      pending: (p) => (keys(p, []) ? { ok: true, entries: petAlbumPending() } : bad()),
      photoTaken: (p) => (keys(p, ["id", "op"]) ? petAlbumPhotoTaken(p.id, p.op) : bad()),
      scrapbook: (p) => {
        const r = keys(p, ["memorial"]) || keys(p, ["cat"]) ? petScrapbookData(p) : null;
        return r ? { ok: true, scrapbook: r } : bad();
      },
    },
  };
})();
