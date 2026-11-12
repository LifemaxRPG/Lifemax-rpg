/* Native cattery (pet.cattery.v1, docs/specs/CAT_TAB_PLAN.md CT6b): the Cat tab's Cattery, Breeding lab, Adoption and Memorial screens.
   The rules are the page's, unchanged (docs/specs/CAT_ENGINE_GAPS.md section 1): the page rolls every litter and every genome, native only
   draws what it is shown and asks. The page stays the only writer.
   - extend(out, tab): the `cattery` block of the projection for a screen that advertised pet.cattery.v1: always the small `entry`
     (counts, whether a second cat may be adopted) and the page's words; the screen that is open (`view`) also gets its `detail`, which
     is long (a cat's words and shape chips) and so is sent only while that screen shows (the `view` command), like the genome table.
     Every cat comes with the coat key and the proportions native draws its preview from; a genetic coat's sheets are the page's
     (`coat` command), so every key shown is registered with the page first (petDnaRegister, memory only).
   - the `cattery` command {kind, ...}: each kind calls the page's own handler (petBreedGo, petBreedKeep, petBreedDrop, petBreedKeepAll,
     petBreedDropAll, petBreedRelease, petBreedRename, petBreedHome, petBreedStray, petBreedSetGoal, petBreedPick, petPickKit,
     petAdoptWith, petPairAdoptWith, petAdoptNew), so the saved state is what the page's own buttons make. The page's confirm() and
     prompt() are answered by the screen's own dialog before the command is sent. A command that changes things carries an operation id
     kept with the save (state.petCatteryOps, the last 40), so a repeat after a lost answer is "Already recorded."; it is ok only once
     the save stored the change, and a change the save refused is taken back. A litter is named by its `at` stamp: a keep for a litter
     that was replaced is refused, not applied to the wrong kitten. Loaded after 50-native-pet.js, which calls extend(). */
(function () {
  const CAP = "pet.cattery.v1";
  const OP = /^[A-Za-z0-9:._-]{1,80}$/;
  const OPS_KEPT = 40;
  const OPEN_MS = 30 * 60000; // a screen that went away without closing is not served for ever
  const NAME_MAX = 14;
  const MEMORIAL_MAX = 40;
  const ALREADY = "Already recorded.";
  const NOTHING = "Nothing was changed.";
  const GONE = "That litter has changed.";
  const VIEWS = ["cattery", "lab", "adopt", "memorial"];
  const SHAPES = {
    view: "view", goal: "value", pick: "key,who", breed: "dad,mom,op", keep: "at,i,op", drop: "at,i,op", keepAll: "at,op", dropAll: "at,op",
    release: "id,op", rename: "id,name,op", home: "id,op", stray: "op", newLitter: "", pickKit: "i", adopt: "kit,mode,name,op", reopen: "op",
  };
  const WORDS = {
    cattery: "Cattery", lab: "Breeding lab", memorial: "In memory", adoptTitle: "A second cat",
    adoptHint: "Another stray has been seen by the kitchen window. Two cats keep each other company: they meet, play chase, groom each other and nap together.",
    adoptButton: "Meet", mother: "Mother", father: "Father", goal: "Goal", breed: "Breed", keep: "Keep", release: "Release", keepAll: "Keep all",
    releaseAll: "Release all", home: "Bring home", homeTaken: "Your second cat slot is taken", stray: "Another stray",
    strayHint: "A random unrelated adult joins the cattery. Handy for fresh genes.", rename: "Rename", nameLabel: "Name", newLitter: "Another litter",
    adopt: "Welcome home", letIn: "Let them in", reopen: "Open the door again", none: "No cats of yours have passed away.", cancel: "Cancel",
    save: "Save", back: "Back",
  };
  let view = "";
  let viewAt = 0;

  const own = (o, k) => !!o && Object.prototype.hasOwnProperty.call(o, k);
  const ok = (extra) => Object.assign({ ok: true, changed: false, message: "", why: "", inHours: 0, reaction: "" }, extra);
  const no = (message) => ok({ ok: false, message });
  const generic = () => no("Something went wrong. Nothing was changed.");
  const ops = () => (Array.isArray(state.petCatteryOps) ? state.petCatteryOps : []);
  const alive = (c) => !!(c && c.alive);
  const viewOpen = () => (VIEWS.includes(view) && Date.now() - viewAt < OPEN_MS ? view : "");
  /** which adoption the page offers: the first cat (no cat at all), a second (one living cat), or none */
  const adoptMode = () => (!state.pet ? "first" : alive(state.pet) && !alive(state.pet2) ? "pair" : "");
  const canHome = () => !!(alive(state.pet) && !alive(state.pet2));

  /** the proportions as the screen's art takes them (petRigWire's numbers, for a genome shown at full strength), null for a cat as drawn */
  function wire(m) {
    if (!m || petRigNeutral(m)) return null;
    const r = (v) => Math.round(Math.max(0.35, Math.min(2.8, v == null ? 1 : v)) * 100) / 100;
    return { em: r(m.em), ew: r(m.ew), tl: r(m.tl), lg: r(m.lg), bl: r(m.bl), wk: r(m.wk), bob: !!m.bob };
  }
  /** a cat card as petBreedCard words it: name, sex, the small line, the shape chips and what the preview draws from */
  function card(e, mode, goalK) {
    const ph = petDnaPheno(e.dna), f = ph.sex === "f";
    const age = Math.max(0, Math.floor((Date.now() - (e.born || Date.now())) / 86400000));
    petDnaRegister(ph.key);
    const chips = PET_MORPH_KEYS.map((k) => {
      const v = k === "tl" && ph.morph.bob ? ph.morph.tl : ph.morph[k];
      return { label: PET_MORPH[k].n + " " + v.toFixed(1) + "×", title: PET_MORPH[k].name, goal: k === goalK };
    });
    if (ph.morph.bob) chips.push({ label: "bobtail", title: "bobtail", goal: true });
    return {
      id: e.key, name: e.name, female: f,
      sub: ph.coat + " · generation " + (e.dna.gen || 0) + (e.home ? " · at home" : "") + (mode === "roster" && !e.home ? " · " + age + " d old" : "") + (ph.sterile ? " · sterile" : ""),
      chips, preview: { coat: ph.key, stage: -1, rig: wire(ph.morph), sleep: false }, home: !!e.home,
      ask: "Release " + e.name + "? " + (f ? "She" : "He") + " will not come back.",
    };
  }

  function roster() {
    const C = petCattery(), g = petBreedGoal(), R = petBreedRoster();
    return { kind: "cattery", cats: R.map((e) => card(e, "roster", g.k)), count: C.cats.length, max: PET_CATTERY_MAX, canHome: canHome(), nameMax: NAME_MAX,
      header: "Your cats", small: R.length + " · cattery " + C.cats.length + "/" + PET_CATTERY_MAX };
  }
  function lab() {
    const C = petCattery(), R = petBreedRoster(), g = petBreedGoal();
    const moms = R.filter((e) => petDnaSexOf(e.dna) === "f"), dads = R.filter((e) => petDnaSexOf(e.dna) === "m" && petDnaFertile(e.dna));
    const pickOf = (list, want) => (list.some((e) => e.key === want) ? want : list.length ? list[0].key : "");
    const mom = pickOf(moms, PET_BREED.mom), dad = pickOf(dads, PET_BREED.dad);
    const listed = (list) => list.map((e) => ({ key: e.key, name: e.name, home: !!e.home }));
    const options = [];
    PET_MORPH_KEYS.forEach((k) => [["+", PET_MORPH[k].hi.replace(/^(very|extremely) /, "")], ["-", PET_MORPH[k].lo]].forEach((x) =>
      options.push({ value: k + x[0], label: (x[0] === "+" ? "Longest/largest " : "Shortest/smallest ") + PET_MORPH[k].n + " (" + x[1] + ")" })));
    const best = R.slice().sort((a, b) => petBreedGoalVal(petDnaPheno(b.dna)) - petBreedGoalVal(petDnaPheno(a.dna)))[0];
    const m = petBreedFind(mom), f = petBreedFind(dad);
    let forecast = "";
    if (m && f) {
      const x = petBreedForecast(m.dna, f.dna);
      forecast = "Expected " + PET_MORPH[g.k].n + ": " + x.mean.toFixed(2) + "× on average, usually between " + x.lo.toFixed(2) + "× and " + x.hi.toFixed(2) + "×.";
    }
    const L = C.litter;
    const litter = L && L.kits && L.kits.length
      ? { at: L.at || 0, title: "Litter of " + L.kits.length, parents: L.mom + " × " + L.dad,
          kits: L.kits.map((d, i) => card({ key: "lit" + i, name: "Kitten " + (i + 1), dna: d, born: Date.now(), home: false }, "litter", g.k)),
          full: C.cats.length >= PET_CATTERY_MAX, fullNote: "The cattery is full (" + PET_CATTERY_MAX + "). Release a cat before keeping more." }
      : null;
    return {
      kind: "lab",
      intro: "Pair a mother with a father to roll a litter. Each kitten gets a random half of each parent's genes, so keep the ones that lean toward your goal and breed those. Cats you keep wait here as genomes; bring one home when your second cat slot is free.",
      goal: g.k + (g.up ? "+" : "-"), options,
      best: best ? "Best in your roster: " + best.name + " at " + petDnaPheno(best.dna).morph[g.k].toFixed(2) + "× " + PET_MORPH[g.k].n + " (generation " + (best.dna.gen || 0) + ")." : "",
      note: moms.length && dads.length ? "" : "You need at least one female and one fertile male. " + (moms.length ? "" : "No female yet. ") + (dads.length ? "" : "No fertile male yet. ") + "Use Another stray below to find a mate.",
      moms: listed(moms), dads: listed(dads), mom, dad, forecast, litter,
    };
  }
  const litterOf = () => window.__petLitter || (window.__petLitter = petDnaNewLitter(4));
  /** the first words of a page builder's HTML: its eyebrow, title and first paragraph, so the words are the page's own */
  function words(html) {
    const d = document.createElement("div");
    d.innerHTML = html;
    const t = (s) => (d.querySelector(s) || {}).textContent || "";
    return { eyebrow: t(".eyebrow"), title: t("h2"), body: t("p.muted") };
  }
  function adopt() {
    const mode = adoptMode();
    if (!mode) return { kind: "adopt", mode: "", eyebrow: "", title: "", body: "", parents: "", kits: [], pick: 0, placeholder: "", nameMax: NAME_MAX, memory: "" };
    const L = litterOf(), w = words(mode === "pair" ? petPairAdoptHtml() : petIntroHtml());
    if (!L.ph) L.ph = PET_NAMES.filter((n) => !state.pet || n !== state.pet.name)[Math.floor(Math.random() * (PET_NAMES.length - 1))];
    const mem = (state.petMemorial || []).slice(-1)[0];
    return {
      kind: "adopt", mode, eyebrow: w.eyebrow, title: w.title, body: w.body,
      parents: "Mother: " + L.mom.coat.toLowerCase() + " · Father: " + L.dad.coat.toLowerCase(),
      kits: L.kits.map((d, i) => {
        const ph = petDnaPheno(d);
        petDnaRegister(ph.key);
        return { i, female: ph.sex === "f", coat: ph.coat, trait: PET_TRAITS[ph.trait] || "", persona: petDnaTopPersona(ph), preview: { coat: ph.key, stage: 0, rig: null, sleep: false } };
      }),
      pick: Math.max(0, Math.min(L.kits.length - 1, L.pick | 0)), placeholder: L.ph, nameMax: NAME_MAX,
      memory: mode === "first" && mem ? "In memory: " + mem.name + "." : "",
    };
  }
  function memorial() {
    const list = Array.isArray(state.petMemorial) ? state.petMemorial : [];
    const entries = list.filter((m) => m && typeof m === "object").slice(-MEMORIAL_MAX).reverse().map((m) => {
      const name = String(m.name || "").slice(0, 40), days = Math.max(1, m.days | 0), lv = PET_LEVELS[Math.max(0, Math.min(5, m.bond | 0))];
      let known = !!PET_DNA_LEGACY[m.coat];
      if (!known && typeof m.coat === "string") {
        petDnaRegister(m.coat);
        known = !!PET_DNA_KEYS[m.coat];
      }
      const legacy = PET_COATS.find((c) => c[0] === m.coat);
      return { name, coat: legacy ? legacy[1] : "", preview: known ? { coat: m.coat, stage: -1, rig: null, sleep: true } : null,
        text: name + " is gone. You shared " + days + " day" + (days === 1 ? "" : "s") + " together, and " + name + ' reached the "' + lv + '" stage of trust.' };
    });
    return { kind: "memorial", entries, canReopen: !!(state.pet && !state.pet.alive) };
  }
  const detail = (v) => (v === "cattery" ? roster() : v === "lab" ? lab() : v === "adopt" ? adopt() : v === "memorial" ? memorial() : null);

  /** Adds the block to the Cat tab's projection `out` for a screen that advertised pet.cattery.v1 (else leaves it as it was). */
  function extend(out, tab) {
    if (!tab || typeof tab.has !== "function" || !tab.has(CAP)) return out;
    try {
      const v = viewOpen();
      out.cattery = {
        words: WORDS,
        entry: { cats: petCattery().cats.length, max: PET_CATTERY_MAX, memorial: (state.petMemorial || []).length, adopt: adoptMode() === "pair" ? "pair" : "" },
        view: v,
        detail: v ? detail(v) : null,
      };
      out.words = Object.assign({}, out.words, { moreText: "Anything else about your cats opens on the page." });
    } catch (e) {
      console.warn("[pet cattery]", e);
    }
    return out;
  }

  /* ---- commands ---- */
  const snapshot = () => JSON.stringify([state.pet || null, state.pet2 || null, state.cattery || null, state.petMemorial || null, state.petCatteryOps || null]);
  const data = () => JSON.stringify([state.pet || null, state.pet2 || null, state.cattery || null, state.petMemorial || null]);
  function restore(json) {
    const keys = ["pet", "pet2", "cattery", "petMemorial", "petCatteryOps"], v = JSON.parse(json);
    keys.forEach((k, i) => (v[i] === null && k !== "pet" ? delete state[k] : (state[k] = v[i])));
  }
  /** Runs a page handler that writes: ok only once the save stored a change; refused (a string from run, or nothing changed) or unstored: taken back. */
  function change(p, run, needOp) {
    if (needOp) {
      if (!OP.test(p.op)) return generic();
      if (ops().includes(p.op)) return ok({ message: ALREADY });
    }
    const before = data(), all = snapshot(), said = NativePet.toasts();
    let refused = "", stored = false;
    if (needOp) state.petCatteryOps = ops().concat([p.op]).slice(-OPS_KEPT); // the handler's own save writes it with the change
    try {
      stored = NativePet.storedBy(() => {
        refused = run() || "";
      });
    } catch (e) {
      console.warn("[pet cattery] command", e);
      restore(all);
      return generic();
    }
    if (refused || data() === before) {
      restore(all);
      return no(refused || NativePet.claimToast(said) || NOTHING);
    }
    if (!stored) {
      restore(all);
      return no(unsavedMessage());
    }
    return ok({ changed: true, message: NativePet.claimToast(said) });
  }
  const litterFor = (p) => {
    const L = petCattery().litter;
    return L && L.kits && (L.at || 0) === p.at ? L : null;
  };
  const kitIn = (p) => !!(litterFor(p) && litterFor(p).kits[p.i]);
  const nameOf = (v) => (typeof v === "string" ? v.trim() : "");
  const inCattery = (id) => typeof id === "string" && petCattery().cats.some((c) => c.id === id);
  /** runs fn with the page's confirm() or prompt() answered (the screen asked its own dialog first) */
  function answering(kind, value, fn) {
    const was = window[kind];
    window[kind] = () => value;
    try {
      return fn();
    } finally {
      window[kind] = was;
    }
  }
  function freshLitter() {
    window.__petLitter = petDnaNewLitter(4); // what the page does when the intro or the second-cat card opens
    window.__petLegacy = false;
    petPickKit(0);
  }

  const kinds = {
    view(p) {
      if (p.view !== "" && !VIEWS.includes(p.view)) return generic();
      if (p.view === "adopt") {
        if (!adoptMode()) return no("A second cat cannot move in now.");
        freshLitter();
      }
      view = p.view;
      viewAt = Date.now();
      return ok();
    },
    goal(p) {
      if (typeof p.value !== "string" || !/^(em|ew|tl|lg|bl|wk)[+-]$/.test(p.value)) return generic();
      if ((petCattery().goal || "em+") === p.value) return ok({ message: ALREADY });
      return change(p, () => petBreedSetGoal(p.value), false);
    },
    pick(p) {
      if (p.who !== "mom" && p.who !== "dad") return generic();
      const e = petBreedFind(p.key), sex = e && petDnaSexOf(e.dna);
      if (!e || sex !== (p.who === "mom" ? "f" : "m") || !petDnaFertile(e.dna)) return no("Pick a " + (p.who === "mom" ? "mother" : "father") + " from the list.");
      petBreedPick(p.who, p.key);
      return ok();
    },
    breed: (p) => change(p, () => {
      const m = petBreedFind(p.mom), f = petBreedFind(p.dad);
      if (!m || !f || petDnaSexOf(m.dna) !== "f" || petDnaSexOf(f.dna) !== "m" || !petDnaFertile(f.dna)) return "Pick a mother and a father.";
      PET_BREED.mom = p.mom;
      PET_BREED.dad = p.dad;
      petBreedGo();
    }, true),
    keep: (p) => change(p, () => (kitIn(p) ? petBreedKeep(p.i) : GONE), true),
    drop: (p) => change(p, () => (kitIn(p) ? petBreedDrop(p.i) : GONE), true),
    keepAll: (p) => change(p, () => (litterFor(p) && litterFor(p).kits.length ? petBreedKeepAll() : GONE), true),
    dropAll: (p) => change(p, () => (litterFor(p) && litterFor(p).kits.length ? petBreedDropAll() : GONE), true),
    release: (p) => change(p, () => (inCattery(p.id) ? answering("confirm", true, () => petBreedRelease(p.id)) : "That cat is not in the cattery."), true),
    rename(p) {
      const n = nameOf(p.name);
      if (!n || n.length > NAME_MAX) return no("A name needs 1 to " + NAME_MAX + " characters.");
      const c = petCattery().cats.find((x) => x.id === p.id);
      return change(p, () => (c ? answering("prompt", n, () => petBreedRename(p.id)) : "That cat is not in the cattery."), true);
    },
    home: (p) => change(p, () => {
      if (!canHome()) return "Your second cat slot is taken.";
      return inCattery(p.id) ? petBreedHome(p.id) : "That cat is not in the cattery.";
    }, true),
    stray: (p) => change(p, () => petBreedStray(), true),
    newLitter() {
      if (!adoptMode()) return no("There is no one to adopt now.");
      freshLitter();
      return ok();
    },
    pickKit(p) {
      const L = window.__petLitter;
      if (!adoptMode() || !L || !Number.isInteger(p.i) || !L.kits[p.i]) return generic();
      petPickKit(p.i);
      return ok();
    },
    adopt(p) {
      const mode = adoptMode(), L = window.__petLitter, n = typeof p.name === "string" ? p.name.trim() : null;
      if (p.mode !== "first" && p.mode !== "pair") return generic();
      if (n === null || n.length > NAME_MAX) return no("A name needs 1 to " + NAME_MAX + " characters, or none.");
      if (p.mode !== mode) return no(p.mode === "pair" ? "A second cat cannot move in now." : "There is a cat at home already.");
      if (!L || !Number.isInteger(p.kit) || !L.kits[p.kit]) return no("Pick one of the kittens.");
      return change(p, () => {
        petPickKit(p.kit);
        if (mode === "pair") petPairAdoptWith(n);
        else petAdoptWith(n);
      }, true);
    },
    reopen: (p) => change(p, () => (state.pet && !state.pet.alive ? petAdoptNew() : "There is no cat to say goodbye to."), true),
  };

  /** The `cattery` command: strict on its shape, then the page's own handler for `kind`. */
  function command(has) {
    return function cattery(p) {
      if (!has(CAP) || !p || typeof p !== "object" || Array.isArray(p) || typeof p.kind !== "string" || !own(SHAPES, p.kind)) return generic();
      const keys = Object.keys(p).filter((k) => k !== "kind").sort().join(",");
      if (keys !== SHAPES[p.kind] || !own(kinds, p.kind)) return generic();
      if (["op", "id", "key", "name", "value"].some((k) => own(p, k) && typeof p[k] !== "string")) return generic();
      return kinds[p.kind](p);
    };
  }
  window.NativePetCattery = { extend, command, capability: CAP, SHAPES, OPS_KEPT, VIEWS };
  if (window.NativePet) window.NativePet.commands.cattery = command((c) => window.NativePet.has(c));
})();
