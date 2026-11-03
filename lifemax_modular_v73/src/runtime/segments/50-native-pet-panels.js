/* Cat tab panels (pet.panels.v1): the read-mostly cards of the web Cat view, drawn natively under the cat's own card: the mind row
   (bond style, mood, "while you were away"), the Together card with its switcher, the likes bars, the inner life, the genetics
   panel and the toy list. Everything shown is page data: the same pure functions that fill the page's own DOM (petMindData,
   petLikesData, petInnerData, petDnaPanelData, petPairCardData, petToysData) build the `panels` block of the projection, so the
   words cannot differ. The page stays the only writer: `focus` switches the card between two cats, `buyToy` is the Rewards shop's
   own rule (ecoBuy), `expand` asks for the full genome table, which is long and sent only while open (docs/specs/CAT_TAB_PLAN.md
   CT6). Loaded after 50-native-pet.js, which calls extend() on its projection and takes the commands. */
(function () {
  const CAP = "pet.panels.v1";
  const GENOME_OPEN_MS = 30 * 60000; // a genome table left open on a screen that went away is not sent for ever
  const ALREADY = "Already recorded.";
  const WORDS = {
    together: "Together",
    switcher: "Which cat",
    likes: "Likes",
    inner: "Inner life",
    dna: "Genetics",
    toys: "Cat toys",
    toysSub: "unlock early",
    sparks: "Sparks",
    buy: "Unlock",
    bought: "Unlocked",
    genomeShow: "Show full genome",
    genomeHide: "Hide full genome",
    moreText: "The toy tray, the cattery and adopting another cat open on the page.",
  };
  let genomeOpenAt = 0;
  let dnaMemo = { key: "", data: null };

  const ok = (extra) => Object.assign({ ok: true, changed: false, message: "", why: "", inHours: 0, reaction: "" }, extra);
  const no = (message) => ok({ ok: false, message });

  /** a section that fails to build is left out, not the whole projection */
  function guarded(name, build) {
    try {
      return build();
    } catch (e) {
      console.warn("[pet panels] " + name, e);
      return null;
    }
  }
  /** the text of a cat's panels is written in "he"; a female cat's is "she", as the page's DOM fixer makes it (petDnaFixText) */
  function pronouns(value, female) {
    if (!female || typeof petDnaHer !== "function") return value;
    if (typeof value === "string") return petDnaHer(value);
    if (Array.isArray(value)) return value.map((v) => pronouns(v, female));
    if (value && typeof value === "object") return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, pronouns(v, female)]));
    return value;
  }
  const genomeOpen = () => Date.now() - genomeOpenAt < GENOME_OPEN_MS;

  /** the DNA panel, kept until the cat, its growth stage or the table's openness changes (it is a few milliseconds of genetics) */
  function dnaData(view) {
    const open = genomeOpen(), key = [view.name, view.coat, view.dna && view.dna.id, petGrowIdx(view), open].join("|");
    if (dnaMemo.key !== key) dnaMemo = { key, data: petDnaPanelData(view, open) };
    return dnaMemo.data;
  }
  /** the toy list: the tray's words plus what the Rewards shop adds (bought early, the price, whether the Sparks cover it) */
  function toysData(view) {
    const price = typeof ECO_TOY_PRICE !== "undefined" ? ECO_TOY_PRICE : {}, sparks = typeof ecoSparks === "function" ? ecoSparks() : 0;
    const bought = (state.toysBought && typeof state.toysBought === "object") ? state.toysBought : {};
    return {
      sparks,
      items: petToysData(view).map((t) => ({ id: t.id, name: t.name, unlocked: t.unlocked, word: t.word, bought: !!bought[t.id],
        price: price[t.id] || 0, affordable: sparks >= (price[t.id] || 0) })),
    };
  }
  /** the Together card with the switcher's faces (name and coat of each cat) and the focused cat */
  function pairData(f, now) {
    const card = petPairCardData(now);
    return card && { card, focus: f, cats: [state.pet, state.pet2].map((c) => ({ name: String(c.name).slice(0, 14), coat: c.coat })) };
  }

  /** The `panels` block for the cat whose card shows (cat, house index f); null without two readable parts is not an error here. */
  function build(cat, f, now) {
    // petAs points petData() and PET_RT at the focused cat; the panels read a copy, so nothing the page keeps is touched (the DOM's
    // renderers create a brain, a likes record or genes on first sight and save them; a projection must not)
    return petAs(f, function () {
      const view = Object.assign({}, cat), female = typeof petSexOf === "function" && petSexOf(view) === "f";
      const brain = guarded("brain", () => petBrain(view));
      return {
        words: WORDS,
        mind: brain && guarded("mind", () => pronouns(petMindData(view, brain, PET_RT, now), female)),
        likes: guarded("likes", () => pronouns(petLikesData(view), female)),
        inner: brain && guarded("inner", () => pronouns(petInnerData(view, brain), female)),
        dna: guarded("dna", () => Object.assign({ morph: null }, dnaData(view), { genomeOpen: genomeOpen() })),
        pair: state.pet2 && state.pet2.alive ? guarded("pair", () => pairData(f, now)) : null,
        toys: guarded("toys", () => toysData(view)),
      };
    }, []);
  }

  /** Adds the block to the projection `out` of 50-native-pet.js when the screen draws it, and takes back the words that say the
      panels are on the page: they are here now. */
  function extend(out, cat, f, now) {
    if (!window.NativePet || !window.NativePet.has(CAP)) return out;
    out.panels = build(cat, f, now);
    out.together = "";
    out.words = Object.assign({}, out.words, { moreText: WORDS.moreText });
    return out;
  }

  const commands = {
    /** Switch the card between the two cats, as the page's switcher does (petPairFocus). */
    focus(p) {
      const i = p.i;
      if (i !== 0 && i !== 1) return no("There is no such cat.");
      if (i === 1 && !petPairOn()) return no("There is only one cat.");
      if (PET_PAIR.focus === i) return ok({ message: ALREADY });
      petPairFocus(i);
      return ok({ changed: true });
    },
    /** Unlock a toy early with Sparks: the Rewards shop's own ecoBuy (price, balance and the save are its). It is ok only once the save
        stored it; a save the browser refuses is taken back; a toy already bought changes nothing. */
    buyToy(p) {
      const id = typeof p.id === "string" ? p.id : "";
      if (typeof ECO_TOY_PRICE === "undefined" || !Object.prototype.hasOwnProperty.call(ECO_TOY_PRICE, id) || !PET_TOYS[id]) return no("That toy is not for sale.");
      state.toysBought = state.toysBought && typeof state.toysBought === "object" ? state.toysBought : {};
      if (state.toysBought[id]) return ok({ message: ALREADY });
      const sparks = state.sparks, said = NativePet.toasts(), write = window.writeSave;
      let stored = true;
      window.writeSave = function () {
        const r = write.apply(this, arguments);
        if (!r) stored = false;
        return r;
      };
      try {
        ecoBuy("toy", id);
      } finally {
        window.writeSave = write;
      }
      if (!stored) {
        state.sparks = sparks;
        delete state.toysBought[id];
        return no(unsavedMessage());
      }
      const message = NativePet.claimToast(said);
      return state.toysBought[id] ? ok({ changed: true, message }) : no(message || "Not enough Sparks.");
    },
    /** The full genome table opens or closes on the screen: it is in the projection only while open. */
    expand(p) {
      if (typeof p.open !== "boolean") return no("Something went wrong. Nothing was changed.");
      genomeOpenAt = p.open ? Date.now() : 0;
      return ok();
    },
  };
  if (window.NativePet) Object.assign(window.NativePet.commands, commands);
  window.NativePetPanels = { extend, build, commands, capability: CAP };
})();
