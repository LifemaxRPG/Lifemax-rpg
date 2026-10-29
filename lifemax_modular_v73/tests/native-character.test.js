const fs = require("node:fs"),
  vm = require("node:vm"),
  assert = require("node:assert/strict"),
  path = require("node:path");
const ids = [
  "physique",
  "endurance",
  "intelligence",
  "business",
  "social",
  "discipline",
  "adventure",
  "wellbeing",
];
const attrs = ids.map((id) => ({ id, name: id, short: id }));
let saves = 0;
const c = {
  console,
  Date,
  Math,
  Object,
  Number,
  String,
  setTimeout() {},
  clearTimeout() {},
  setInterval() {},
  clearInterval() {},
  state: {
    onboardingComplete: true,
    name: "Fixture",
    classId: null,
    weight: 180,
    heightFt: 6,
    heightIn: 0,
    skillXP: { Reading: 123 },
    benchmarks: [{ name: "5K", value: "28:30" }],
  },
  ATTRS: attrs,
  ATTR_COL: Object.fromEntries(ids.map((id) => [id, "#7c9cff"])),
  TITLES: [[99, "Legend"]],
  chCompute: () => ({
    wk: Object.fromEntries(ids.map((id) => [id, 12])),
    prev: Object.fromEntries(ids.map((id) => [id, 40])),
    per: Object.fromEntries(ids.map((id) => [id, [0, 1, 2, 3, 4, 5, 6]])),
    total: 96,
  }),
  chFocus: () => ["physique"],
  characterLevel: () => ({ level: 7, into: 31, needed: 200, pct: 15.5 }),
  attrLevel: () => ({ level: 4, into: 44, needed: 175, pct: 25 }),
  chDecay: () => ({ txt: "Safe", sub: "today", rank: 3 }),
  attrXP: () => 345,
  levelData: () => ({ level: 3, pct: 20 }),
  chCap: () => 8,
  chArchetype: () => ({ n: "Balanced" }),
  luckToday: () => null,
  titleFor: () => "Explorer",
  streak: () => 8,
  totalXP: () => 9876,
  navigate() {},
  renderCharacterPage() {},
  lmaView: () => "character",
  lmaTopOverlay: () => null,
  lmaLoad: () => ({}),
  lmaSave: () => saves++,
  getComputedStyle: () => ({ getPropertyValue: () => "#7c9cff" }),
  lmaTabList: () => [],
  document: {
    documentElement: { classList: { contains: () => false } },
    getElementById: () => null,
    addEventListener() {},
    createElement: () => ({
      getContext: () => ({
        fillRect() {},
        getImageData: () => ({ data: [124, 156, 255, 255] }),
      }),
    }),
  },
};
c.window = c;
vm.createContext(c);
vm.runInContext(
  fs.readFileSync(
    path.join(__dirname, "../src/runtime/segments/50-native-character.js"),
    "utf8",
  ),
  c,
);
const before = JSON.stringify(c.state),
  a = c.NativeCharacterProjection.snapshot(),
  b = c.NativeCharacterProjection.snapshot();
assert.equal(a.protocol, 1);
assert.equal(b.sequence, a.sequence + 1);
assert.equal(a.webSessionId, b.webSessionId);
assert.equal(a.level, 7);
assert.equal(a.totalXP, "9,876");
assert.deepEqual(
  Array.from(a.attributes, (x) => x.id),
  ids,
);
assert.equal(a.attributes[0].total, 345);
assert.equal(a.attributes[0].level, 4);
assert.equal(a.skills[0].level, 3);
assert.equal(a.benchmarks[0].value, "28:30");
assert.equal(a.weight, "180 lb");
assert.equal(JSON.stringify(c.state), before);
assert.equal(saves, 0);
console.log(
  "PASS Character protocol, session sequencing, all eight projected attributes, owner-derived values, legacy units and zero save writes",
);

if (process.env.CHARACTER_FIXTURE)
  fs.writeFileSync(process.env.CHARACTER_FIXTURE, JSON.stringify(a, null, 2));
