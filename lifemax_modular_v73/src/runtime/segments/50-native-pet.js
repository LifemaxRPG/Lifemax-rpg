/* Cat tab projection v1 (pet.projection.v1) and its commands. The page stays the only writer of the cat: the screen draws the
   facts petStatusData and its siblings give (the same functions that fill the page's own Cat card) and asks the page, with
   commands, to feed, to blink, to pause the clock and to rename; a command is ok only once the save stored it. In this first
   release the cats in the room live a day of their own on the phone (a local autopilot): they are not the page's cats, so
   play, the toys and the pair stay on the page (docs/specs/CAT_TAB_PLAN.md, CT1). Touch (CT2, pet.touch.v1): the screen's cats
   answer a finger by themselves, and the `touch` command is the page's side of it: the brain's petting rules and the day's count
   (petPetDone), and the sound of the purr, which stays here. The intro (no cat yet) and the memorial
   (a cat that has passed) are the page's: blocked() keeps the screen away from them. The tab lifecycle is NativeTabKit's. */
(function () {
  const COAT = "pet.coat.v1"; // a genetic (dna_) coat's sheets, asked of the page with the `coat` command
  const TOUCH = "pet.touch.v1"; // touch: the screen's cats answer a finger at once and tell the page, which keeps the books
  const PETS_PER_DAY = 3; // petPetDone counts three a day
  const PLAYS_PER_DAY = 2;
  const HOLD_MAX_S = 600; // the longest hold a stroke may claim
  const PET_COUNTS_MS = 1100; // a touch this long is a pet (16-runtime.js, petPointer 'up')
  const BEAT_MS = 850; // a heart (and the brain's pet_beat) every 0.85 s of petting; a shy cat's every 1.6 s
  const BEAT_SHY_MS = 1600;
  const BEATS_MAX = 24; // pet_beat events one stroke may raise
  const SAT_CAP = 1.2; // the most saturation the brain keeps (petBrainTouch, petBrainHold)
  const SAT_ASLEEP = 0.6; // a sleeping cat that stays asleep takes this share of the hold's saturation
  const PURR_KEEP_MS = 100; // the page's purr loop is fed this often while the screen covers the room loop
  const PURR_LIMIT_MS = 120000; // a purr no stroke ever ended (the screen died) stops by itself
  const GENTLE_GAP_MS = 20 * 60000; // a gentle stroke counts toward the cat's quirks once in 20 minutes
  const RIG = "pet.rig.v1"; // each cat's proportions (ear, tail, leg, body, whisker multipliers), so native re-assembles the art as the page does
  const LEGACY_COATS = ["ginger", "gray", "black", "tuxedo"]; // the coats native ships art for
  const NAME_MAX = 14; // what petRename keeps
  const CUES = 2; // fed cues sent with a projection: one button can feed both cats in the same moment
  const LANDED = 40; // operation ids remembered, so a repeat of a feed that landed is answered, not done twice
  const ALREADY = "Already recorded.";
  const WORDS = {
    room: "View room",
    play: "Play",
    blink: "Slow blink",
    blinkHint: "A slow blink is how cats say they feel safe",
    careTitle: "Care and settings",
    vacationTitle: "Vacation mode",
    nameLabel: "Name",
    rename: "Rename",
    moreTitle: "More on the Cat page",
    moreText: "Moods, likes, DNA, the toy tray and the cattery open on the page.",
  };
  let tab = null;
  let handedOver = false; // this visit went to the page's own Cat view (Play, the room, more): the screen stays away until the owner comes back to the tab
  let fedSeq = 0;
  let since = Date.now(); // when this visit began: saturation wears off from here (the room loop that does it is stopped)
  const touchedAt = [0, 0]; // when the screen last touched each cat
  let purrTimer = 0;
  let purrStarted = 0;
  const fed = []; // the latest meals, {cat, id, at}, from every way a cat is fed (this screen, the page, a deep link, the voice)
  const landed = [];

  const ok = (extra) => Object.assign({ ok: true, changed: false, message: "", why: "", inHours: 0, reaction: "" }, extra);
  const no = (message, extra) => Object.assign({ ok: false, changed: false, message, why: "", inHours: 0, reaction: "" }, extra);
  const generic = () => no("Something went wrong. Nothing was changed.");

  /** a meal, whoever served it: petFeed is the one function every route to the bowl calls, so the cue is made here */
  const feedOnce = window.petFeed;
  window.petFeed = function () {
    const r = feedOnce.apply(this, arguments);
    if (r && r.ok) {
      fed.push({ cat: PET_CTX ? 1 : 0, id: ++fedSeq, at: Date.now() });
      if (fed.length > CUES * 2) fed.shift();
    }
    return r;
  };

  /** whether native can draw this cat: a living cat with one of the shipped coats, or a genetic coat the page holds and the screen can ask for */
  function drawable(p) {
    if (!p || !p.alive) return false;
    return LEGACY_COATS.includes(p.coat) || (!!tab && tab.has(COAT) && typeof petCoatKnown === "function" && petCoatKnown(p.coat));
  }
  /** one cat as the room needs it: its coat, growth stage (-1 grown, 0..5 a kitten) and hunger (the weak rows), and its name, which seeds its habits;
      for a screen with pet.rig.v1 also its proportions (rig), left out for a cat the art fits as drawn */
  function look(p) {
    const o = { name: String(p.name).slice(0, NAME_MAX), coat: p.coat, stage: petGrowIdx(p), weak: petStage(p) >= 3 };
    const rig = tab && tab.has(RIG) && typeof petRigWire === "function" ? petRigWire(p) : null;
    if (rig) o.rig = rig;
    return o;
  }
  /** the cats of the house native draws: the first, and the second when it is alive and drawable too */
  function house() {
    const cats = [state.pet];
    if (drawable(state.pet2)) cats.push(state.pet2);
    return cats;
  }
  /** the owner's haptic strength (HAP_LEVELS), or null with haptics off: a native buzz is as long and strong as the page's */
  function haptics() {
    if (!fxOpt().haptics) return null;
    const L = HAP_LEVELS[hapLevelKey()];
    return { dm: L.dm, add: L.add, amp: L.amp, min: L.min, max: HAP_MAX };
  }
  /** the brain's petting saturation of a cat now: what it holds less what has worn off since the screen last touched it (reads only) */
  function satNow(c, i, now) {
    const s = c.brain && c.brain.af && typeof c.brain.af.s === "number" ? c.brain.af.s : 0;
    return Math.max(0, Math.round((s - (PET_BRAIN.SAT_DECAY * Math.max(0, now - Math.max(touchedAt[i], since))) / 60000) * 1000) / 1000);
  }
  /** how a cat takes touch, in the page's numbers: trust level, the trait's share of saturation, the trust and the saturation (reads only) */
  function touchData(c, i, now) {
    const T = PET_TRAIT_BASE[c.trait] || PET_TRAIT_BASE.curious;
    const tru = c.brain && c.brain.at && typeof c.brain.at.tru === "number" ? c.brain.at.tru : 0.3;
    return { level: petLevel(c), satMul: T.sat, trust: Math.max(0, Math.min(1, tru)), sat: Math.min(SAT_CAP, satNow(c, i, now)) };
  }
  /** "Petted 2 of 3 · Played 1 of 2 today", or nothing before the day's first pet or play */
  function countsLine(c) {
    const today = c.day === petDay(), pets = today ? c.petsToday | 0 : 0, plays = today ? c.playsToday | 0 : 0;
    const parts = [];
    if (pets) parts.push("Petted " + Math.min(pets, PETS_PER_DAY) + " of " + PETS_PER_DAY);
    if (plays) parts.push("Played " + Math.min(plays, PLAYS_PER_DAY) + " of " + PLAYS_PER_DAY);
    return parts.length ? parts.join(" · ") + " today" : "";
  }
  function projection() {
    const now = Date.now(), p = state.pet, cats = house();
    // whose card the screen shows: the cat the page's switcher has focused, as the page's own commands act on it
    const f = cats.length > 1 && petPairOn() ? PET_PAIR.focus : 0, cat = cats[f], d = petStatusData(cat, now);
    const both = petPairFeedData(now);
    // the touch fields exist only for a screen that said pet.touch.v1: an older screen refuses a projection with keys it does not know
    const touch = tab && tab.has(TOUCH)
      ? {
          touch: cats.map((c, i) => touchData(c, i, now)),
          counts: {
            pets: cat.day === petDay() ? Math.min(cat.petsToday | 0, PETS_PER_DAY) : 0,
            petsMax: PETS_PER_DAY,
            plays: cat.day === petDay() ? Math.min(cat.playsToday | 0, PLAYS_PER_DAY) : 0,
            playsMax: PLAYS_PER_DAY,
          },
          haptics: haptics(),
          touchWords: { closeRoom: "Close room", countsLine: countsLine(cat) },
        }
      : {};
    const out = Object.assign({
      cats: cats.map(look),
      focus: f,
      name: cat.name,
      eyebrow: d.eyebrow,
      hearts: d.level,
      mood: d.mood,
      bond: { pct: d.bondPct, text: d.bondText },
      grow: petGrowData(cat),
      lastMeal: d.lastMeal,
      mealsToday: d.mealsToday,
      days: d.days,
      favs: petFavsData(cat),
      hint: d.hint,
      feed: both || d.feed,
      care: { how: PET_CARE_TEXT.how, bond: PET_CARE_TEXT.bond, vacationHint: PET_CARE_TEXT.vacation, vacation: !!p.vacation.on, name: cat.name, nameMax: NAME_MAX },
      together: state.pet2 && state.pet2.alive ? p.name + " and " + state.pet2.name + " share the house. Switching cats and the Together card are on the Cat page." : "",
      words: WORDS,
      cue: { fed: fed.slice(-CUES).map((c) => ({ cat: c.cat, id: c.id, ago: Math.max(0, now - c.at) })) },
    }, touch);
    const panels = window.NativePetPanels ? NativePetPanels.extend(out, cat, f, now) : out;
    const full = window.NativePetPlay ? NativePetPlay.extend(panels, cat, tab) : panels; // the toy tray, for a screen with pet.play.v1
    return window.NativeCatWorld ? NativeCatWorld.extend(full, tab) : full; // the world block, for a screen with pet.world.v1
  }

  /** Runs change() and says whether every write it made reached storage (the answers of writeSave; true when it made none). */
  function storedBy(change) {
    const write = window.writeSave;
    let stored = true;
    window.writeSave = function () {
      const r = write.apply(this, arguments);
      if (!r) stored = false;
      return r;
    };
    try {
      change();
    } finally {
      window.writeSave = write;
    }
    return stored;
  }
  const keep = () => JSON.stringify([state.pet, state.pet2 || null]);
  /** the cats as they were before a change whose save did not store, so the screen and the page agree */
  function restore(json) {
    const [a, b] = JSON.parse(json);
    state.pet = a;
    if (b) state.pet2 = b;
    else delete state.pet2;
    petRenderStatus(true);
  }
  /** a change to the cats, kept only if the page could store it (the save decides): otherwise it is taken back */
  function persisting(change) {
    const before = keep();
    if (storedBy(change)) return ok({ changed: true });
    restore(before);
    return no(unsavedMessage());
  }
  /** the words of a toast the page raised since the count was taken, taken from the toast so no screen shows it twice */
  function toastSince(count) {
    if (tab.toasts() === count) return "";
    NativeTabKit.claimNotice();
    return document.getElementById("toast")?.textContent || "";
  }
  /** the page's rt is built (its art loaded) and, with two cats, both of its runtimes exist, which feeding both needs */
  async function runtimes() {
    const rt = await petInit();
    if (typeof petPairEnsure === "function") petPairEnsure(rt);
    return rt;
  }
  /** why no cat can be fed now, in the page's words: every cat on vacation, else the cat whose next meal is nearest; null when one can */
  function refusal(now) {
    const cats = [state.pet, state.pet2].filter((c) => c && c.alive);
    if (cats.some((c) => !c.vacation.on && petCanFeed(c, now))) return null;
    const waiting = cats.filter((c) => !c.vacation.on);
    if (!waiting.length) return no(petFeedRefusal(cats[0], { why: "vacation" }), { why: "vacation" });
    const c = waiting.reduce((a, b) => (petNextMealIn(b, now) < petNextMealIn(a, now) ? b : a));
    const inHours = Math.round(petNextMealIn(c, now) * 10) / 10;
    // the page sends its cat to the bowl for a groom when it is not hungry, and so does the screen's
    return no(petFeedRefusal(c, { why: "full", inHours: petNextMealIn(c, now) }), { why: "full", inHours, reaction: "bowl" });
  }

  const commands = {
    /** Feed: the page's own petFeedClick (one cat, or whoever is hungry of two) once its gate says a meal is due, else the page's
        refusal in its words. Which cat was fed arrives as the cue of the next projection, and the screen's cat goes to its bowl. */
    async feed(p) {
      const op = String(p.op || "").slice(0, 80);
      if (!op) return generic();
      if (landed.includes(op)) return ok({ message: ALREADY });
      if (!state.pet || !state.pet.alive) return no("There is no cat to feed.");
      const refused = refusal(Date.now());
      if (refused) return refused;
      const rt = await runtimes(), before = keep(), meals = fedSeq, said = tab.toasts();
      if (!storedBy(() => petFeedClick())) {
        restore(before);
        if (rt.bowl) rt.bowl.food = false;
        return no(unsavedMessage());
      }
      if (fedSeq === meals) return generic(); // the page fed nobody (it was not ready)
      landed.push(op);
      if (landed.length > LANDED) landed.shift();
      return ok({ changed: true, message: toastSince(said) });
    },
    /** Slow blink: the page's petBlinkClick. Its answer is a toast when it will not (asleep, or blinked at three times in six
        seconds), which comes back as the message; else the cat blinks back and the screen's cat does too. */
    async blink() {
      if (!state.pet || !state.pet.alive) return no("There is no cat.");
      await runtimes();
      const said = tab.toasts();
      petBlinkClick();
      const message = toastSince(said);
      return message ? ok({ message }) : ok({ reaction: "blink" });
    },
    /** Vacation mode, for both cats as the page's switch does (petSetVacation); the state asked for again changes nothing */
    vacation(p) {
      if (typeof p.on !== "boolean") return generic();
      if (!!state.pet.vacation.on === p.on) return ok({ message: ALREADY });
      return persisting(() => petSetVacation(p.on));
    },
    /** Rename the cat whose card the screen shows (petRename: 1 to 14 characters) */
    rename(p) {
      if (typeof p.name !== "string") return generic();
      const name = p.name.trim();
      if (!name || name.length > NAME_MAX) return no("A name needs 1 to " + NAME_MAX + " characters.");
      const cat = house()[focusOf()];
      if (cat.name === name) return ok({ message: ALREADY });
      return persisting(() => petRename(name));
    },
    /** One sheet of a genetic coat for the live cat: the page composes it (50-pet-coat.js) and stays the only one that does */
    coat: (p) => petCoatAnswer(p),
    /** The screen's cats answered a finger by themselves; this is the page's side of it. Two kinds:
        `purr` (the sound, which stays in the page: the cat starts or stops purring) and `stroke` (a touch ended: the brain's
        petting rules and the day's count, which only the page keeps). */
    async touch(p) {
      if (!tab.has(TOUCH) || !p || !Number.isInteger(p.cat) || p.cat < 0 || p.cat >= house().length) return generic();
      if (p.kind === "purr") return purr(p);
      if (p.kind === "stroke") return stroke(p);
      return generic();
    },
  };

  /** Run as cat `i`, as the page does for its second cat (the same swap the pair uses) */
  const asCat = (i, fn) => (typeof petAs === "function" && i === 1 ? petAs(1, fn) : fn());
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));

  /** The page's purr loop, which its room loop would feed (petPurrTick) but is stopped while the screen covers the room: a timer feeds it. */
  function purrStop() {
    clearInterval(purrTimer);
    purrTimer = 0;
    [0, 1].forEach((i) => {
      try {
        asCat(i, () => PET_RT && petPurr(PET_RT, 0));
      } catch (e) {}
    });
  }
  async function purr(p) {
    if (typeof p.on !== "boolean" || typeof p.level !== "number" || !(p.level >= 0 && p.level <= 1)) return generic();
    if (!p.on) {
      purrStop();
      return ok();
    }
    await runtimes();
    asCat(p.cat, () => PET_RT && petPurr(PET_RT, p.level));
    if (!purrTimer) {
      purrStarted = Date.now();
      purrTimer = setInterval(() => {
        if (Date.now() - purrStarted > PURR_LIMIT_MS) return purrStop();
        asCat(p.cat, () => PET_RT && petPurrTick(PET_RT));
      }, PURR_KEEP_MS);
    }
    return ok();
  }

  /** A touch ended. The page's rules, from what the screen saw: saturation (a landing, then the hold), the brain's events, and a pet
      the day counts when the finger stayed 1.1 s and the cat did not leave (3 a day, +2 bond, a level-up). Once per operation id. */
  async function stroke(p) {
    const op = String(p.op || "").slice(0, 80);
    const left = p.left === "" || p.left === "over" || p.left === "shy" ? p.left : null;
    const held = typeof p.heldMs === "number" && p.heldMs >= 0 ? Math.min(p.heldMs, HOLD_MAX_S * 1000) : -1;
    if (!op || left === null || held < 0 || typeof p.keepSleep !== "boolean" || typeof p.ignored !== "boolean" || typeof p.spam !== "boolean") return generic();
    if (landed.includes(op)) return ok({ message: ALREADY });
    const cat = house()[p.cat];
    if (!cat || !cat.alive) return no("There is no cat to pet.");
    const rt = await runtimes(), before = keep(), said = tab.toasts(), now = Date.now();
    let counted = false, levelUp = 0;
    const done = storedBy(() => {
      asCat(p.cat, () => {
        const pc = petData(), b = petBrain(pc), br = petBR(rt), L = petLevel(pc), T = PET_TRAIT_BASE[pc.trait] || PET_TRAIT_BASE.curious;
        const wornOff = Math.max(0, now - Math.max(touchedAt[p.cat], since));
        b.af.s = Math.max(0, b.af.s - (PET_BRAIN.SAT_DECAY * wornOff) / 60000);
        touchedAt[p.cat] = now;
        br.taps.push(now);
        if (L === 0) {
          petPlay(rt, "hiss", 0.95);
          petBrainEvent("startle");
          return;
        }
        if (p.spam) petBrainEvent("spam");
        const sec = held / 1000, beat = L === 1 ? BEAT_SHY_MS : BEAT_MS;
        b.af.s = Math.min(SAT_CAP, b.af.s + PET_BRAIN.SAT_TAP * T.sat);
        b.af.s = Math.min(SAT_CAP, b.af.s + PET_BRAIN.SAT_HOLD * sec * T.sat * (1.25 - 0.5 * b.at.tru) * (p.keepSleep ? SAT_ASLEEP : 1));
        b.af.s = petR3(b.af.s);
        for (let k = 0, n = Math.min(BEATS_MAX, Math.floor(held / beat)); k < n && b.af.s < PET_BRAIN.SAT_WARN; k++) petBrainEvent("pet_beat");
        if (p.left === "over") petBrainEvent("overstim", { ignored: p.ignored });
        else if (b.af.s >= PET_BRAIN.SAT_WARN && b.af.s <= PET_BRAIN.SAT_MAX) br.respectAt = now; // you stopped when the cat asked
        else if (held >= PET_COUNTS_MS && b.af.s < PET_BRAIN.SAT_WARN && now - (br.gentleAt || 0) > GENTLE_GAP_MS) {
          br.gentleAt = now;
          petQuirkCount(b, "g");
        }
        br.lastCatTap = now;
        br.dirty = true;
        if (held >= PET_COUNTS_MS && p.left === "") {
          const r = petPetDone();
          if (r.counted) {
            counted = true;
            levelUp = r.levelUp || 0;
            save();
          }
        }
      });
    });
    if (!done) {
      restore(before);
      return no(unsavedMessage());
    }
    if (!counted) asCat(p.cat, petBrainPersist);
    landed.push(op);
    if (landed.length > LANDED) landed.shift();
    if (levelUp) {
      const buzz = window.haptic; // the screen buzzes the level-up itself, in the owner's strength
      window.haptic = () => {};
      try {
        asCat(p.cat, () => petLevelUp(rt, levelUp));
      } finally {
        window.haptic = buzz;
      }
    }
    return ok({ changed: counted, message: toastSince(said), levelUp });
  }
  const focusOf = () => (house().length > 1 && petPairOn() ? PET_PAIR.focus : 0);

  /** What the screen hands to the page: the full-screen room, the Play button's tray (an older screen), or the page's own Cat view for the rest. */
  async function onAction(e) {
    // the full-screen room is the screen's own when it can touch; an older screen still hands it to the page's
    if (e.action === "room" && tab.has(TOUCH)) return;
    if (e.action === "room") {
      petRoomFullscreenOpen();
      return;
    }
    // a screen with pet.play.v1 plays with the toys itself (it never sends Play): the visit is never handed to the page's tray,
    // and a Play that arrives all the same only brings the screen back (the kit closed it for the action)
    if (e.action === "play" && tab.has("pet.play.v1")) return void openNativePet();
    handedOver = true;
    if (e.action === "play") petPlayClick();
  }

  tab = NativeTabKit.create({
    id: "pet",
    plugin: "NativePet",
    capability: "pet.projection.v1",
    setting: "nativePet",
    toggle: "nativePetToggle",
    projection,
    onAction,
    commands,
    warm: true, // the screen says at once whether it can ask for a genetic coat
    notices: true,
    render: "renderPetView",
    // the intro and the memorial are the page's; so is a visit the owner handed to the page, the full-screen room and a level-up
    blocked: () => handedOver || !drawable(state.pet) || document.body.classList.contains("pet-full-open") || !!document.querySelector(".luov"),
    arrive() {
      handedOver = false;
      since = Date.now();
    },
    // the full-screen room closing gives the view back to the screen
    watch() {},
    afterOpen: () => petSync(),
    afterClose() {
      purrStop(); // a purr the screen started stops with it
      petSync();
    },
    globals: { open: "openNativePet", enable: "setNativePetEnabled", projection: "NativePetProjection" },
  });

  /* the page's room loop stops while the screen covers the view (petWantRun), so the cat is not drawn twice */
  window.NativePet = { covering: () => tab.active(), active: () => tab.active(), commands, has: (c) => tab.has(c), toasts: () => tab.toasts(), claimToast: toastSince,
    fedCues: (now) => fed.slice(-CUES).map((c) => ({ cat: c.cat, id: c.id, ago: Math.max(0, now - c.at) })) }; // for the world block
})();
