/* ---------- The cat's memorable moments: the catalogue (scrapbook phase S1, page side; docs/architecture/PET_SCRAPBOOK.md).
   A moment is something that happens between the owner and a cat that is worth a photograph. Each has a TIER: only a HIGH moment ever
   becomes an album entry (50-pet-album.js); mid and low ones are listed here so the tiers are explicit and so the detector can say why
   an everyday event (a meal, a petting beat) never produces a picture.
   A kind has a stable id; a LADDER kind (growth stage, bond level, ...) can happen once per rung, and its rung number n is part of the
   entry id (`<catId>:<kind>:<n>`), so the same rung is never recorded twice.
   reach(p, x) answers the highest rung the cat has reached now (0 = none; 1 for a kind that happens once). It only READS the cat: no
   random numbers, no writes, no brain or likes migration, so the detector can never disturb the cat's seeded behaviour.
   x = {age: days together (fraction), other: the other living cat or null, pair: the pair record when it belongs to these two or null}.
   pose is the hint for the staged portrait (a string, or one per rung); appear says who is in the picture ('self' or 'pair' with the
   other cat). weight orders what a full album drops first (never adopt). text is the caption: {name} {days} {stage} {bond} {other}
   {spot} {toy} {quirk} {years} are filled from the cat's facts; an array gives one caption per rung. */
var PET_POSES = ["sit", "loaf", "sleep", "play", "groom", "stretch", "blink", "gaze", "pounce", "zoom", "belly", "eat", "gift", "portrait", "sit_with_other", "groom_other", "curl_with_other"];
var PET_STAGE_NAMES = ["tiny kitten", "small kitten", "young kitten", "growing kitten", "big kitten", "nearly grown kitten", "grown cat"];
var PET_BIRTHDAY_DAYS = 365;
var PET_DAY_MILESTONES = [30, 100, 200];
/* what the brain remembers once, for good (b.mem.fx in 26-pet-brain.js), read without creating the brain */
function petMomentFx(p, key) {
  const b = p && p.brain;
  return b && b.mem && b.mem.fx && b.mem.fx[key] ? 1 : 0;
}
function petMomentCore(kind, key, pose, text, weight) {
  return { kind, tier: "high", pose, appear: "self", weight, text, reach: (p) => petMomentFx(p, key) };
}
var PET_MOMENTS = [
  { kind: "adopt", tier: "high", pose: "sit", appear: "self", weight: 9, text: "{name} came home today. Day one together.", reach: () => 1 },
  {
    kind: "grow", tier: "high", ladder: true, appear: "self", weight: 6,
    pose: ["play", "play", "stretch", "sit", "sit", "portrait"],
    text: ["{name} is a {stage} now, {days} days in.", "{name} is a {stage} now, {days} days in.", "{name} is a {stage} now, {days} days in.", "{name} is a {stage} now, {days} days in.", "{name} is a {stage} now, {days} days in.", "{name} is all grown up after {days} days with you."],
    reach: (p, x) => (x.age >= PET_GROW_DAYS ? 6 : Math.min(5, Math.floor(x.age / 5))),
  },
  {
    kind: "bond", tier: "high", ladder: true, appear: "self", weight: 6,
    pose: ["sit", "sit", "play", "blink", "loaf"],
    text: "Your bond with {name} reached \"{bond}\". Day {days} together.",
    reach: (p) => petLevel(p),
  },
  { kind: "gift", tier: "high", pose: "gift", appear: "self", weight: 4, text: "{name} brought you a present for the first time.", reach: (p) => ((p.gifts || 0) >= 1 ? 1 : 0) },
  petMomentCore("blink", "first_blink", "blink", "You and {name} shared a slow blink. It means: I feel safe with you.", 4),
  petMomentCore("respect", "first_respect", "loaf", "You stopped when {name} asked you to. They noticed.", 3),
  petMomentCore("glass", "first_glass", "gaze", "{name} looked out through the glass at you.", 3),
  petMomentCore("comfort", "first_co", "sit", "{name} came to sit near you, just to be close.", 4),
  petMomentCore("trophy", "first_trophy", "pounce", "{name} caught something and carried it to the glass for you.", 3),
  petMomentCore("zoomies", "first_zoomies", "zoom", "{name} had the zoomies for the first time.", 3),
  petMomentCore("belly", "first_belly", "belly", "{name} rolled over and showed you a belly. That is trust.", 4),
  petMomentCore("vigil", "first_vigil", "gaze", "{name} was already waiting at the glass when you came.", 4),
  {
    kind: "fav_spot", tier: "high", pose: "loaf", appear: "self", weight: 4,
    text: "{name} has a favourite place: {spot}.",
    reach: (p) => (p.likes && p.likes.favSpot ? 1 : 0),
  },
  {
    kind: "fav_toy", tier: "high", pose: "play", appear: "self", weight: 4,
    text: "{name} has a favourite toy: the {toy}.",
    reach: (p) => (p.likes && p.likes.favToy ? 1 : 0),
  },
  {
    kind: "quirk", tier: "high", ladder: true, pose: "sit", appear: "self", weight: 3,
    text: "{name} picked up a new habit: {quirk}.",
    reach: (p) => (p.brain && p.brain.qk && Array.isArray(p.brain.qk.acq) ? p.brain.qk.acq.length : 0),
  },
  {
    kind: "days", tier: "high", ladder: true, pose: "portrait", appear: "self", weight: 5,
    text: "{days} days together with {name}.",
    reach: (p, x) => PET_DAY_MILESTONES.filter((d) => x.age >= d).pop() || 0,
  },
  {
    kind: "birthday", tier: "high", ladder: true, pose: "portrait", appear: "self", weight: 8,
    text: "{name} turned {years}. {days} days together.",
    reach: (p, x) => Math.floor(x.age / PET_BIRTHDAY_DAYS),
  },
  { kind: "recovery", tier: "high", event: true, pose: "eat", appear: "self", weight: 2, text: "{name} was very hungry, and your meal came just in time.", reach: () => 0 },
  {
    kind: "pair_nap", tier: "high", pose: "curl_with_other", appear: "pair", weight: 5,
    text: "{name} and {other} napped side by side for the first time.",
    reach: (p, x) => (x.pair && x.pair.n && x.pair.n.nap >= 1 ? 1 : 0),
  },
  {
    kind: "pair_level", tier: "high", ladder: true, appear: "pair", weight: 5,
    pose: ["sit_with_other", "sit_with_other", "groom_other", "curl_with_other"],
    text: "{name} and {other} are now {pair}.",
    reach: (p, x) => (x.pair ? Math.max(0, PET_PAIR_LEVELS.indexOf(petPairLevel(x.pair.rel))) : 0),
  },
  {
    kind: "rank", tier: "high", pose: "sit_with_other", appear: "pair", weight: 3,
    text: "{name} and {other} have settled who is in charge.",
    reach: (p, x) => (x.pair && x.pair.rk && x.pair.rk.settled ? 1 : 0),
  },
];
/* The everyday brain events (petBrainEvent kinds) and what tier each is. Only a "high" one can make the detector look again at once
   (the rest never produce an entry on their own); any kind not listed counts as low. */
var PET_MOMENT_EVENTS = {
  feed: "low", play: "low", toy: "low", toy_meh: "low", pet_beat: "low", startle: "low", greet: "low", overstim: "low", spam: "low",
  auto_done: "low", auto_cut: "low", comfort_arrive: "low", levelup: "mid",
  gift: "high", blink: "high", respect: "high",
};
function petMomentOf(kind) {
  return PET_MOMENTS.find((m) => m.kind === kind) || null;
}
/* the pose of a rung (a kind has one pose, or one per rung) */
function petMomentPose(m, n) {
  return Array.isArray(m.pose) ? m.pose[Math.max(0, Math.min(m.pose.length, n) - 1)] : m.pose;
}
function petMomentFill(text, f) {
  return text.replace(/\{(\w+)\}/g, (all, k) => (f[k] == null ? "" : String(f[k])));
}
/* the caption of rung n of kind m for cat p, from the facts the entry will keep */
function petMomentCaption(m, n, f) {
  const t = Array.isArray(m.text) ? m.text[Math.max(0, Math.min(m.text.length, n) - 1)] : m.text;
  return petMomentFill(t, f);
}
