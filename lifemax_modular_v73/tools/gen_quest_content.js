/* The quest content, read from the real page, for the native quest code (android-wrapper/native/src/main/java/com/lifemax/rpg/core/quests).
   Everything the Daily Hand draw (dhDraw, dhPick, dhMakeQuest, dhEnsure, dhReroll) and the default-quest and class-pack rules (freshState,
   applyClassContent, the dhMig migration, questLocked) read as DATA, so Kotlin never holds a hand copy of it. Regenerate with
     NODE_PATH=$(npm root -g) node tools/gen_quest_content.js [built-page.html]
   Output: tests/fixtures/quest-content.json, and a byte-identical copy at android-wrapper/native/src/main/resources/quests/quest-content.json
   (the file the app reads). tests/quest-content.test.js records it again from the page and fails on any difference, in either file.

   The file is one JSON object, keys in this order:
     schemaVersion  1. Bump it (and the Kotlin decoder) when the shape changes.
     contentHash    SHA-256 (hex) of the compact JSON text of the object made of every other key except counts, in file order. The Kotlin
                    loader recomputes it, so a file edited by hand, or half written, is refused.
     counts         {templates, defaultQuests, classes, contentClasses, classQuests}, for a readable diff and a quick sanity check.
     attributes     ATTRS ids in the page's order (the tie order of dhWeakestAttr).
     hand           {freeRerolls, rerollCost, classAttrs, templates}: DH_FREE_REROLLS, DH_REROLL_COST, DH_CLASS_ATTR and QUEST_LIBRARY (each
                    template {id, attr, tier, name, desc, verify, xp} exactly as dhMakeQuest reads it).
     defaultQuests  defaultQuests (freshState's quests; applyClassContent puts its non-daily ones back for a player with no class).
     stageLevels    stageLvl(0..5): the character level a quest stage unlocks at (questLocked).
     classes        CLASSES as {id, ready}: every class the player can pick, ready or not.
     classQuests    CLASS_CONTENT[class].quests for every class that has content, as the page has them after all of its segments ran
                    (the key order of each quest is the page's; day and stage are present only when the page has them).
   Not here, because the page keeps them in code and not in data: the tier odds and luck constants inside dhPick, the slot order, the
   migration patterns (q1 to q3 and cc_* in dhEnsure, q1 to q6 and cc_* in applyClassContent). The native draw is accepted against golden
   cases for those, not against this file.
   Layout: one template, quest or class per line, so a change shows up in a diff as the entries it changed. */
'use strict';
const fs = require('fs'), path = require('path'), crypto = require('crypto');

const ROOT = path.join(__dirname, '..');
const FILE = path.join(ROOT, 'tests', 'fixtures', 'quest-content.json');
const PACKAGED = path.join(ROOT, '..', 'android-wrapper', 'native', 'src', 'main', 'resources', 'quests', 'quest-content.json');
const SCHEMA_VERSION = 1;

/* Runs in the page: reads the content globals. Fails loudly when one is missing, so a renamed constant is never exported as empty. */
function readContent() {
  const need = (name, v) => { if (v === undefined || v === null) throw new Error('the page has no ' + name); return v };
  const clone = v => JSON.parse(JSON.stringify(v));
  const content = need('CLASS_CONTENT', CLASS_CONTENT), out = {};
  out.attributes = need('ATTRS', ATTRS).map(a => a.id);
  out.hand = {
    freeRerolls: need('DH_FREE_REROLLS', DH_FREE_REROLLS), rerollCost: need('DH_REROLL_COST', DH_REROLL_COST),
    classAttrs: clone(need('DH_CLASS_ATTR', DH_CLASS_ATTR)), templates: clone(need('QUEST_LIBRARY', QUEST_LIBRARY)),
  };
  out.defaultQuests = clone(need('defaultQuests', defaultQuests));
  out.stageLevels = [0, 1, 2, 3, 4, 5].map(stageLvl);
  out.classes = need('CLASSES', CLASSES).map(c => ({ id: c.id, ready: !!c.ready }));
  out.classQuests = {};
  for (const id of Object.keys(content)) out.classQuests[id] = clone(content[id].quests);
  return out;
}

/* Puts the pieces in file order, with the counts and the hash. */
function assemble(c) {
  const body = {
    schemaVersion: SCHEMA_VERSION, attributes: c.attributes, hand: c.hand, defaultQuests: c.defaultQuests,
    stageLevels: c.stageLevels, classes: c.classes, classQuests: c.classQuests,
  };
  const contentHash = crypto.createHash('sha256').update(JSON.stringify(body), 'utf8').digest('hex');
  const counts = {
    templates: c.hand.templates.length, defaultQuests: c.defaultQuests.length, classes: c.classes.length,
    contentClasses: Object.keys(c.classQuests).length, classQuests: Object.values(c.classQuests).reduce((n, q) => n + q.length, 0),
  };
  return { schemaVersion: SCHEMA_VERSION, contentHash, counts, ...Object.fromEntries(Object.entries(body).slice(1)) };
}

/* One entry per line: an array of objects gets a line per element; an object holding such an array gets a line per member. */
const compact = v => JSON.stringify(v);
const holdsLines = v => Array.isArray(v) ? v.some(x => x && typeof x === 'object') : !!v && typeof v === 'object' && Object.values(v).some(holdsLines);
function layout(v, depth) {
  if (!holdsLines(v)) return compact(v);
  const pad = ' '.repeat(depth);
  if (Array.isArray(v)) return '[\n' + v.map(x => pad + compact(x)).join(',\n') + '\n' + pad.slice(1) + ']';
  return '{\n' + Object.entries(v).map(([k, x]) => pad + compact(k) + ':' + layout(x, depth + 1)).join(',\n') + '\n' + pad.slice(1) + '}';
}
const serialize = f => layout(f, 1) + '\n';

/* Records the content in the built page `file`. */
async function generate(file) {
  const { chromium } = require('playwright');
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const p = await (await browser.newContext()).newPage(), errors = [];
    p.on('pageerror', e => errors.push(String(e)));
    await p.goto('file://' + file, { timeout: 120000 });
    await p.waitForFunction(() => typeof state === 'object' && state !== null && typeof QUEST_LIBRARY !== 'undefined', null, { timeout: 60000 });
    const content = await p.evaluate(readContent);
    if (errors.length) throw new Error('the page threw: ' + errors.slice(0, 3).join(' | '));
    return assemble(content);
  } finally { await browser.close() }
}

module.exports = { generate, serialize, assemble, FILE, PACKAGED, SCHEMA_VERSION };

if (require.main === module) {
  const file = require('../tests/current-page.js')(process.argv[2]);
  generate(file).then(f => {
    const text = serialize(f);
    fs.mkdirSync(path.dirname(PACKAGED), { recursive: true });
    fs.writeFileSync(FILE, text); fs.writeFileSync(PACKAGED, text);
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ' and ' + path.relative(process.cwd(), PACKAGED) + ': ' + JSON.stringify(f.counts) + ' ' + f.contentHash);
  }).catch(e => { console.error(e); process.exit(1) });
}
