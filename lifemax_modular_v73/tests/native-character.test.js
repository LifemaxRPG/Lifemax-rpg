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
        clearRect() {},
        fillRect() {},
        getImageData: () => ({ data: [124, 156, 255, 255] }),
      }),
    }),
  },
};
c.window = c;
vm.createContext(c);
for (const segment of ["50-native-tabkit.js", "50-native-character.js"])
  vm.runInContext(
    fs.readFileSync(path.join(__dirname, "../src/runtime/segments", segment), "utf8"),
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

(async () => {
  const callbacks = {};
  let opened = 0;
  let closed = 0;
  let delegatedAttribute = "";
  let delegatedView = "";
  c.document.body = { classList: { contains: () => false } };
  c.document.querySelector = () => ({});
  c.NativeOverviewSnapshot = { snapshot: async () => ({ width: 320, header: { nodes: [], hits: [], height: 64, width: 320 }, nav: { nodes: [], hits: [], height: 64, width: 320 } }) };
  c.openAttr = (id) => {
    delegatedAttribute = id;
  };
  c.lmaNavTo = (view) => {
    delegatedView = view;
  };
  c.Capacitor = {
    isNativePlatform: () => true,
    isPluginAvailable: () => true,
    Plugins: {
      NativeCharacter: {
        handshake: async () => ({
          protocol: 1,
          capabilities: ["character.projection.v1"],
        }),
        addListener: async (event, callback) => {
          callbacks[event] = callback;
        },
        open: async () => {
          opened++;
        },
        close: async () => {
          closed++;
          callbacks.closed();
        },
      },
    },
  };
  assert.equal(await c.openNativeCharacter(), true);
  await callbacks.action({ action: "attribute", id: "physique" });
  assert.equal(delegatedAttribute, "physique");
  assert.equal(closed, 1);
  assert.equal(await c.openNativeCharacter(), true);
  await callbacks.action({ view: "dashboard" });
  assert.equal(delegatedView, "dashboard");
  assert.equal(opened, 2);
  assert.equal(closed, 2);
  assert.equal(saves, 0);
  console.log(
    "PASS native Character action closes its host, delegates original attribute/navigation handlers, and performs no gameplay save",
  );
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
