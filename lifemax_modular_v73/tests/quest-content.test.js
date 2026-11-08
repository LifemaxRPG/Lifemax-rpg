/* The quest content file (fixtures/quest-content.json, written by tools/gen_quest_content.js) still describes the page: it is read from
   the current page again, in memory, and must come out byte for byte the same, so the native quest code never works from content the
   page no longer has. The copy the app packages (android-wrapper/native/src/main/resources/quests/quest-content.json) must be the same
   bytes. Also checked without the page: the hash, the counts, unique ids, known verify kinds, and class attributes for every class
   that has content. (The Kotlin tests check the same file again with the strict decoder.)
   Run: NODE_PATH=$(npm root -g) node tests/quest-content.test.js [built-page.html]
   Regenerate after an intended change: NODE_PATH=$(npm root -g) node tools/gen_quest_content.js */
'use strict';
const fs = require('fs'), crypto = require('crypto');
const gen = require('../tools/gen_quest_content.js');

let pass = 0, fail = 0;
const ok = (name, cond, extra) => {
  if (cond) { pass++; console.log('PASS ' + name) } else { fail++; console.log('FAIL ' + name + (extra !== undefined ? '\n     ' + JSON.stringify(extra).slice(0, 1500) : '')) }
};
const dupes = list => list.filter((x, i) => list.indexOf(x) !== i);
const VERIFY = { self: 0, none: 0, act: 2, any: 1, water: 1, pet: 1, plan: 0 }; /* kind -> number of arguments (dhVerifyMet) */
const PET = ['feed', 'pet', 'play'], CADENCE = ['daily', 'weekly', 'epic'], DAYS = ['any', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'];

(async () => {
  const raw = fs.readFileSync(gen.FILE, 'utf8'), file = JSON.parse(raw);
  ok('the file is written exactly as the generator lays it out', gen.serialize(file) === raw);
  ok('the packaged copy is the same bytes as the fixture', fs.existsSync(gen.PACKAGED) && fs.readFileSync(gen.PACKAGED).equals(fs.readFileSync(gen.FILE)));

  const { contentHash, counts, ...rest } = file, body = { ...rest };
  ok('the schema version is ' + gen.SCHEMA_VERSION, file.schemaVersion === gen.SCHEMA_VERSION);
  ok('the content hash is the SHA-256 of the content', contentHash === crypto.createHash('sha256').update(JSON.stringify(body), 'utf8').digest('hex'));
  const t = file.hand.templates, classQuests = Object.values(file.classQuests).flat();
  ok('the counts match the content', JSON.stringify(counts) === JSON.stringify({ templates: t.length, defaultQuests: file.defaultQuests.length,
    classes: file.classes.length, contentClasses: Object.keys(file.classQuests).length, classQuests: classQuests.length }));

  ok('every template id is unique (t1 to t' + t.length + ', in order)', t.every((x, i) => x.id === 't' + (i + 1)));
  ok('every template has a known attribute, tier 1 to 3 and the XP of its tier (10, 20, 30)', t.every(x => file.attributes.includes(x.attr) && [1, 2, 3].includes(x.tier) && x.xp === x.tier * 10));
  const badVerify = t.map(x => x.verify).filter(v => {
    const [kind, ...args] = v.split(':');
    return !(kind in VERIFY) || args.length !== VERIFY[kind] || (kind === 'pet' && !PET.includes(args[0])) || args.some((a, i) => (kind === 'pet' ? false : !/^\d+$/.test(a) && !(kind === 'act' && i === 0)));
  });
  ok('every verify text is of a kind dhVerifyMet knows, with its arguments', !badVerify.length, badVerify);
  ok('every attribute has at least one template of each tier it is drawn from (a Maintenance draw never runs dry)', file.attributes.every(a => t.some(x => x.attr === a && x.tier < 3)));

  const ready = file.classes.filter(c => c.ready).map(c => c.id), withContent = Object.keys(file.classQuests);
  ok('class ids are unique and the ready ones are exactly those with content', !dupes(file.classes.map(c => c.id)).length && JSON.stringify(ready.slice().sort()) === JSON.stringify(withContent.slice().sort()), { ready, withContent });
  ok('every class with content has three known attributes for its Class card', withContent.every(c => (file.hand.classAttrs[c] || []).length === 3 && file.hand.classAttrs[c].every(a => file.attributes.includes(a))));
  ok('no class attribute entry is for a class without content', Object.keys(file.hand.classAttrs).every(c => withContent.includes(c)));
  ok('quest ids are unique across the default quests and every class pack', !dupes([...file.defaultQuests, ...classQuests].map(q => q.id)).length);
  ok('every class quest is shaped alike (cadence, XP, attribute; day and stage only when valid)', classQuests.every(q =>
    CADENCE.includes(q.cadence) && Number.isFinite(q.xp) && q.xp > 0 && file.attributes.includes(q.attribute) && /^cc_/.test(q.id) &&
    (q.day === undefined || DAYS.includes(q.day)) && (q.stage === undefined || [1, 2, 3, 4, 5].includes(q.stage))));
  ok('the default quests are q1 to q6, three daily, two weekly, one epic', file.defaultQuests.map(q => q.id).join() === 'q1,q2,q3,q4,q5,q6' &&
    ['daily', 'daily', 'daily', 'weekly', 'weekly', 'epic'].every((c, i) => file.defaultQuests[i].cadence === c));
  ok('the stage levels are 1, 1, 3, 5, 8, 12', file.stageLevels.join() === '1,1,3,5,8,12');

  const fresh = await gen.generate(require('./current-page.js')(process.argv[2]));
  const text = gen.serialize(fresh);
  ok('the page gives the content as the file has it (' + fresh.counts.templates + ' templates, ' + fresh.counts.classQuests + ' class quests)', text === raw,
    { at: [...text].findIndex((c, i) => c !== raw[i]), hint: 'regenerate with node tools/gen_quest_content.js if the change is intended' });
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
