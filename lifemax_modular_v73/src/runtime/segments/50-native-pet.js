/* Cat tab projection v1 (pet.projection.v1) and its commands. The page stays the only writer of the cat: the screen draws the
   facts petStatusData and its siblings give (the same functions that fill the page's own Cat card) and asks the page, with
   commands, to feed, to blink, to pause the clock and to rename; a command is ok only once the save stored it. In this first
   release the cats in the room live a day of their own on the phone (a local autopilot): they are not the page's cats, so
   play, touch, the toys and the pair stay on the page (docs/specs/CAT_TAB_PLAN.md, CT1). The intro (no cat yet) and the memorial
   (a cat that has passed) are the page's: blocked() keeps the screen away from them. The tab lifecycle is NativeTabKit's. */
(function () {
  const COAT = "pet.coat.v1"; // a genetic (dna_) coat's sheets, asked of the page with the `coat` command
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
  function projection() {
    const now = Date.now(), p = state.pet, cats = house();
    // whose card the screen shows: the cat the page's switcher has focused, as the page's own commands act on it
    const f = cats.length > 1 && petPairOn() ? PET_PAIR.focus : 0, cat = cats[f], d = petStatusData(cat, now);
    const both = petPairFeedData(now);
    return {
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
    };
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
  };
  const focusOf = () => (house().length > 1 && petPairOn() ? PET_PAIR.focus : 0);

  /** What the screen hands to the page: the full-screen room, the Play button's tray, or the page's own Cat view for the rest. */
  async function onAction(e) {
    if (e.action === "room") {
      petRoomFullscreenOpen();
      return;
    }
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
    },
    // the full-screen room closing gives the view back to the screen
    watch() {},
    afterOpen: () => petSync(),
    afterClose: () => petSync(),
    globals: { open: "openNativePet", enable: "setNativePetEnabled", projection: "NativePetProjection" },
  });

  /* the page's room loop stops while the screen covers the view (petWantRun), so the cat is not drawn twice */
  window.NativePet = { covering: () => tab.active(), active: () => tab.active(), commands };
})();
