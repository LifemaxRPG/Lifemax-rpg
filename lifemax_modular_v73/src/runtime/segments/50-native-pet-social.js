/* Native pair moments (pet.social.v1, docs/specs/CAT_BRAIN_PLAN.md CB5): the native cats meet, chase, groom each other, curl up
   together and settle standoffs in the native room, and tell the page each moment that completed. The page keeps every rule of the
   friendship and stays the only writer:
   - the world block's pair carries `met` (whether the two have met) only for a screen that advertised pet.social.v1, which is how
     native knows the page records its moments (50-native-cat-world.js pair); an older screen gets exactly what it always got.
   - the `social` command {op, kind, cats:[a, b], winner?, at}: kind one of greet, hiss, chase, groom, nap, standoff; cats the two
     living cats' album ids, the one who began it first (the greeter, the chaser, the groomer, the front sleeper, the challenger; a
     hiss is the second's); a standoff names its winner, nothing else does. The page records it exactly as its own pair director
     (29-pet-pair.js, 29-pet-rank.js) would at the end of that moment: the friendship gain with its daily cap (petPairGain), the
     count n[kind] (a standoff's by the rank code), the last line in the page's words (petPairNote) dated `at`, the meeting (met,
     lastGreet) for a nose touch or a hiss, and for a standoff the pecking order through petRankContest with the native winner
     (confidence, wins, upsets, petRankUpdate's leader and settling). ok only once the save stored it, a refused save is taken back;
     repeat-safe by op id, the last 40 kept in the save (state.petSocialOps), so a moment replayed after a reload is answered
     "Already recorded." and never counted twice. The web brain's own feelings (petPairFeel) are not touched: the native brain owns
     the mood while it runs the cats.
   Registered on the Cat tab and the Overview cards (the native world sends its reports through whichever shows).
   Loaded after 50-native-pet-play.js and before 50-native-overview-cards.js. */
(function () {
  const CAP = "pet.social.v1";
  const KINDS = ["greet", "hiss", "chase", "groom", "nap", "standoff"];
  const OP = /^[A-Za-z0-9:._-]{1,80}$/;
  const CAT = /^c[a-z0-9]{1,12}$/;
  const OPS_KEPT = 40; // social operation ids kept with the save
  const FRIENDS = 0.5; // the page's nose touch: from this friendship a cheek rub, below it a touch of noses (petPairGreetEnd)
  const ALREADY = "Already recorded.";

  const own = (o, k) => !!o && Object.prototype.hasOwnProperty.call(o, k);
  const living = () => [state.pet, state.pet2].map((p, slot) => ({ p, slot })).filter((c) => c.p && c.p.alive);
  const answer = (ok, changed, message, why) => ({ ok, changed, message, why });
  const refuse = (why, message) => answer(false, false, message || "The moment was not recorded.", why);
  const ops = () => (Array.isArray(state.petSocialOps) ? state.petSocialOps : []);
  const names = () => [state.pet.name, state.pet2.name];

  /** The command's shape, strictly: exactly its keys, known kind, two different well-formed ids, a whole time, a standoff's
      winner one of the two. */
  function shaped(p) {
    if (!p || typeof p !== "object" || Array.isArray(p)) return false;
    const standoff = p.kind === "standoff";
    if (Object.keys(p).sort().join(",") !== (standoff ? "at,cats,kind,op,winner" : "at,cats,kind,op")) return false;
    if (typeof p.op !== "string" || !OP.test(p.op) || !KINDS.includes(p.kind)) return false;
    if (!Array.isArray(p.cats) || p.cats.length !== 2 || p.cats.some((c) => typeof c !== "string" || !CAT.test(c)) || p.cats[0] === p.cats[1]) return false;
    if (!Number.isInteger(p.at) || p.at < 0) return false;
    return !standoff || (typeof p.winner === "string" && p.cats.includes(p.winner));
  }

  /** What the page's pair director writes at the end of the moment `kind` begun by slot a with slot b, dated `at`. */
  function record(kind, a, b, winner, at) {
    const d = petPairData(), nm = names(), A = nm[a], B = nm[b];
    if (kind === "greet" || kind === "hiss") {
      const first = !d.met;
      d.met = d.met || at;
      d.lastGreet = at;
      if (kind === "hiss") {
        petPairGain("hiss");
        petPairNote("hiss", b, B + " hissed at " + A);
        if (first) toast(B + " and " + A + " have met. A hiss is normal at first.");
      } else {
        petPairGain("greet");
        if (first) toast(A + " and " + B + " have met.");
        petPairNote("greet", a, d.rel < FRIENDS ? A + " and " + B + " touched noses" : A + " rubbed cheeks with " + B);
      }
    } else if (kind === "chase") {
      petPairGain("chase");
      petPairNote("chase", a, A + " and " + B + " played chase");
    } else if (kind === "groom") {
      petPairGain("groom");
      petPairNote("groom", a, A + " groomed " + B);
    } else if (kind === "nap") {
      petPairGain("nap");
      petPairNote("nap", a, A + " and " + B + " napped together");
    } else {
      const res = petRankContest(a, winner);
      if (!res) return;
      d.n.standoff = (d.n.standoff || 0) + 1;
      const W = nm[res.w], L = nm[res.l];
      petPairNote("standoff", res.w, res.upset ? W + " won a standoff against the odds" : W + " stood their ground against " + L);
    }
    if (d.last) d.last.at = at; // when it happened (a moment from the "while you were away" story happened a while ago)
  }

  /** The `social` command for a page whose screen said pet.social.v1 (`has`). */
  function command(has) {
    return function social(p) {
      if (!has(CAP) || !shaped(p)) return refuse("shape");
      if (ops().includes(p.op)) return answer(true, false, ALREADY, "");
      const house = living();
      if (house.length !== 2) return refuse("refused", "There are not two cats here.");
      const slot = (id) => (house.find((c) => petAlbumCatId(c.p) === id) || {}).slot;
      const a = slot(p.cats[0]), b = slot(p.cats[1]), w = p.kind === "standoff" ? slot(p.winner) : -1;
      if (a == null || b == null || a === b) return refuse("refused", "Those cats are not here.");
      const before = JSON.stringify([own(state, "petPair") ? state.petPair : undefined, own(state, "petSocialOps") ? state.petSocialOps : undefined]);
      const write = window.writeSave;
      let stored = true;
      window.writeSave = function () {
        const r = write.apply(this, arguments);
        if (!r) stored = false;
        return r;
      };
      try {
        record(p.kind, a, b, w, Math.min(p.at, Date.now()));
        state.petSocialOps = ops().concat([p.op]).slice(-OPS_KEPT);
        save();
      } finally {
        window.writeSave = write;
      }
      if (!stored) {
        const [pair, kept] = JSON.parse(before);
        if (pair === undefined) delete state.petPair;
        else state.petPair = pair;
        if (kept === undefined) delete state.petSocialOps;
        else state.petSocialOps = kept;
        return refuse("unsaved", unsavedMessage());
      }
      return answer(true, true, "", "");
    };
  }

  window.NativePetSocial = { capability: CAP, command, record };
  if (window.NativePet) NativePet.commands.social = command((c) => NativePet.has(c));
})();
