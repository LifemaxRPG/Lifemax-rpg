/* Native Play (pet.play.v1, docs/specs/CAT_BRAIN_PLAN.md CB4 part B, first slice): the Cat tab's Play button opens the screen's own
   toy tray and the native cats play with the toy in the shared room. The page keeps every rule and stays the only writer:
   - extend(out, cat, tab): the tray for a screen that advertised pet.play.v1, in the page's words: the toys the cat may play with
     (petToyUnlocked: unlocked, or bought early in the Rewards shop) with the tray's own words for each (petToysData), "Let <name>
     pick", and the page's refusal while the cat is too hungry to play (petPlayClick's). An older screen gets nothing new.
   - the `play` command {op, cat, toy, played}: a game the native brain played to its end. The page's own functions do what a game
     on the page does: petLikesPlayed (the toy's liking, the web brain's mood) and, when the cat really played, petPlayDone (two a
     day, the bond, the Rewards care credit and the album, by their own wrappers). ok only once the save stored it, a refused save
     is taken back; repeat-safe by op id, the last 40 kept in the save (state.petPlayOps) so a game replayed after a reload is
     answered "Already recorded." and never counted twice. Registered on the Cat tab and the Overview cards (the native world sends
     its reports through whichever shows).
   Loaded after 50-native-cat-world.js and before 50-native-overview-cards.js. */
(function () {
  const CAP = "pet.play.v1";
  const KEYS = "cat,op,played,toy"; // the command's keys, sorted
  const OP = /^[A-Za-z0-9:._-]{1,80}$/;
  const OPS_KEPT = 40; // play operation ids kept with the save
  const SNIFF = 0.25; // a game the cat left at a sniff: the share of a toy's liking the page's own tray gives one (petOfferToy)
  const ALREADY = "Already recorded.";

  const own = (o, k) => !!o && Object.prototype.hasOwnProperty.call(o, k);
  const living = () => [state.pet, state.pet2].map((p, slot) => ({ p, slot })).filter((c) => c.p && c.p.alive);
  /** run as cat `slot`, on its data, as the page's pair code does */
  const asCat = (slot, fn) => (typeof petAs === "function" ? petAs(slot, fn, null, true) : fn());
  const answer = (ok, changed, message, why, levelUp) => ({ ok, changed, message, why, levelUp: levelUp || 0 });
  const refuse = (why, message) => answer(false, false, message || "The game was not recorded.", why);
  const ops = () => (Array.isArray(state.petPlayOps) ? state.petPlayOps : []);

  /** The tray for cat `p`, in the page's words: only the toys it may play with */
  function tray(p) {
    const toys = petToysData(p).filter((t) => t.unlocked).map((t) => ({ id: t.id, name: t.name, word: t.word }));
    return {
      title: "Toss a toy to " + p.name,
      pick: "Let " + p.name + " pick",
      tired: petStage(p) >= 3 ? p.name + " is too tired to play. A meal first." : "",
      toys,
    };
  }
  /** Adds the tray to the Cat tab's projection `out` for a screen that advertised pet.play.v1 (else leaves it as it was) */
  function extend(out, p, tab) {
    if (!tab || typeof tab.has !== "function" || !tab.has(CAP) || !p) return out;
    try {
      out.play = tray(p);
    } catch (e) {
      console.warn("[native play] tray", e);
    }
    return out;
  }

  /** the page's rt is built and, with two cats, both runtimes exist (a level-up is drawn by the cat's own) */
  async function runtimes() {
    const rt = await petInit();
    if (typeof petPairEnsure === "function") petPairEnsure(rt);
    return rt;
  }

  /** The `play` command for a page whose Cat tab said pet.play.v1 (`has`). */
  function command(has) {
    return async function play(p) {
      if (!has(CAP) || !p || typeof p !== "object" || Array.isArray(p) || Object.keys(p).sort().join(",") !== KEYS) return refuse("shape");
      if (typeof p.op !== "string" || !OP.test(p.op) || typeof p.cat !== "string" || typeof p.toy !== "string" || !own(PET_TOYS, p.toy) ||
        typeof p.played !== "boolean") return refuse("shape");
      if (ops().includes(p.op)) return answer(true, false, ALREADY, "");
      const cat = living().find((c) => petAlbumCatId(c.p) === p.cat);
      if (!cat) return refuse("refused", "That cat is not here.");
      const rt = await runtimes();
      const before = JSON.stringify([state.pet, state.pet2 || null, own(state, "petPlayOps") ? state.petPlayOps : undefined]);
      const write = window.writeSave;
      let stored = true, counted = false, levelUp = 0;
      window.writeSave = function () {
        const r = write.apply(this, arguments);
        if (!r) stored = false;
        return r;
      };
      try {
        asCat(cat.slot, () => {
          petLikesPlayed(p.toy, true, p.played ? 1 : SNIFF);
          if (p.played) {
            const r = petPlayDone();
            counted = !!(r && r.counted);
            levelUp = (r && r.levelUp) || 0;
          }
        });
        state.petPlayOps = ops().concat([p.op]).slice(-OPS_KEPT);
        save();
      } finally {
        window.writeSave = write;
      }
      if (!stored) {
        const [a, b, kept] = JSON.parse(before);
        state.pet = a;
        if (b) state.pet2 = b;
        else delete state.pet2;
        if (kept === undefined) delete state.petPlayOps;
        else state.petPlayOps = kept;
        if (typeof petRenderStatus === "function") petRenderStatus(true);
        return refuse("unsaved", unsavedMessage());
      }
      if (levelUp && rt) {
        const buzz = window.haptic; // the screen buzzes a level-up itself, in the owner's strength
        window.haptic = () => {};
        try {
          asCat(cat.slot, () => petLevelUp(rt, levelUp));
        } finally {
          window.haptic = buzz;
        }
      }
      return answer(true, true, "", "", counted ? levelUp : 0);
    };
  }

  window.NativePetPlay = { capability: CAP, extend, command, tray };
  if (window.NativePet) NativePet.commands.play = command((c) => NativePet.has(c));
})();
