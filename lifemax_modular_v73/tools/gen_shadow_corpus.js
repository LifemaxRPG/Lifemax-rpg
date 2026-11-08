/* The economy check's corpus (docs/specs/ECONOMY_SHADOW_PLAN.md WP6b): the real built page, on the established fixture save,
   with the check switched on and a stand-in NativeShadow (tests/native-shadow-mock.js), is driven through the economy actions
   native compares, and every base part and envelope text the page sends is written, in order, to
   tests/fixtures/shadow-corpus.json. The Kotlin ShadowCorpusTest replays the file through native's decoder, mirror, runner and
   comparison and expects every action compared and the same: the real transport's shapes (args, env, save answers, toasts,
   minted ids, the Daily Hand's auto-verify inside a save) as the page really sends them.
   The page's clock runs (the transport posts its envelopes on a timer), so instants and ids differ from run to run: the file is
   regenerated on purpose, not compared byte for byte. Regenerate when the page's economy or the transport changes:
     NODE_PATH=$(npm root -g) node tools/gen_shadow_corpus.js [built-page.html] [--quests]
   --quests (WP6c) drives the Daily Hand and the quest board instead, with an app that says economy.shadow.quests.v1, into
   tests/fixtures/shadow-quests-corpus.json: the board drawn (its checks), a reveal, free and paid rerolls, a Log that fulfils a
   card, a card claimed, a quest ticked with a note and unticked, a quest made on the board's form and one refused, a class
   switch (its quest pack, then its save), the day rolling over with the app open (the next redraw deals the new hand), a check
   on its own. */
'use strict';
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const mock = require('../tests/native-shadow-mock.js');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'shadow-corpus.json');
const QUESTS_FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'shadow-quests-corpus.json');
const CONTENT = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'tests', 'fixtures', 'quest-content.json'), 'utf8'));
const SAVE = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'tests', 'fixtures', 'saves', 'established.json'), 'utf8')).state;
const START = '2026-11-08T12:00:00.000Z';

/* keeps every message's text as the page sent it, beside the stand-in's own answers */
const CAPTURE = `(() => { const P = window.Capacitor.Plugins.NativeShadow; window.__corpus = [];
  for (const m of ['base', 'observe']) { const f = P[m]; P[m] = function (a) { window.__corpus.push({ m, text: a.text }); return f.apply(this, arguments) } } })();`;

/* The actions, each run on its own in the page as the owner's tap would run it; ids and values are the established save's. A
   confirm() is answered by the browser's own dialog (accepted or dismissed as the step says), so the transport records the
   answer as it would on the phone. */
const STEPS = [
  ['a Log', () => recordActivity({ type: 'reading', customName: '', duration: '30', quality: '1', skill: '', primary: '', secondary: '', notes: 'A private note' })],
  ['a Log with its own attributes', () => recordActivity({ type: 'strength', customName: '', duration: '45', quality: '1.2', skill: '', primary: 'physique', secondary: 'endurance', notes: '' })],
  ['a rest day', () => ecoTakeRest()],
  ['cat care', () => ecoCare('feed')],
  ['a deposit', () => recordMoneyTransaction('deposit', 75, 'pay')],
  ['a theme bought', () => ecoBuy('theme', 'ember')],
  ['an undo the owner confirms', () => deleteActivity(state.activities[state.activities.length - 1].id), { confirm: true }],
  ['an undo the owner declines', () => deleteActivity(state.activities[0].id), { confirm: false }],
  ['the rules reset', () => resetRules()],
  ['a bare save', () => save()],
  ['a welcome chip', () => jcClaim('pet')],
  ['the nutrition check', () => ecoNutritionCheck()],
  ['a spoken Log', () => lmvLogNow({ type: 'running', name: 'Evening run', duration: 25, day: getToday(), atMs: Date.now() - 60000 })],
  ['a Life Director session', () => LifeMaxHost.awardActivity({ eventId: 'ev-corpus-1', activityTypeTag: 'deep_work', actualStartEpoch: Math.floor(Date.now() / 1000) - 3600, actualDurationMinutes: 50, title: 'Write the plan', lifecycleState: 'COMPLETED' }, { title: 'Write the plan' })],
  ['a Life Director quest for no quest', () => LifeMaxHost.completeQuest({ questId: 'no-such-quest', key: 'x' })],
  ['the decay settings', () => {
    const add = (id, v) => { const el = document.getElementById(id) || Object.assign(document.body.appendChild(document.createElement('input')), { id }); if (typeof v === 'boolean') { el.type = 'checkbox'; el.checked = v } else el.value = v };
    add('decayOn', true); add('decayGrace', '5'); add('decayRate', '2'); saveDecay();
  }],
  ['an ascension refused below level 20', () => ecoAscend('social')],
  ['a Log that the storage refuses', () => {
    const real = Storage.prototype.setItem;
    Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
    try { recordActivity({ type: 'reading', customName: '', duration: '20', quality: '1', skill: '', primary: '', secondary: '', notes: '' }) } finally { Storage.prototype.setItem = real }
  }],
  ['a Log after it', () => recordActivity({ type: 'business', customName: '', duration: '40', quality: '1', skill: '', primary: '', secondary: '', notes: '' })],
];

/* The Daily Hand and the quest board (--quests). A step may move the clock first ({ at }). */
const card = test => 'dhCards().filter(q => !isQuestDone(q)).find(q => ' + test + ')';
const QUEST_STEPS = [
  ['the board drawn', () => { navigate('quests'); renderQuests() }],
  ['a card turned over', () => dhReveal(0)],
  ['a free reroll', () => dhReroll(2)],
  ['a paid reroll', () => { state.sparks = Math.max(Number(state.sparks) || 0, 120); dhReroll(2) }],
  ['a Log that fulfils a card', new Function('const q = ' + card("/^(act|any):/.test(q.verify)") + ';' +
    "const v = q ? q.verify.split(':') : ['any', '20'], type = v[0] === 'act' ? v[1] : 'reading', min = Number(v[v.length - 1]) || 20;" +
    "recordActivity({ type, customName: '', duration: String(min + 5), quality: '1', skill: '', primary: '', secondary: '', notes: '' })")],
  ['a card claimed', new Function('const q = ' + card("/^(self|none)/.test(q.verify || 'self')") + ' || dhCards().find(q => !isQuestDone(q)); if (q) toggleQuest(q.id, true)')],
  ['a quest ticked with a note', () => {
    const q = state.quests.find(x => !x.hand && x.cadence === 'daily' && !isQuestDone(x)) || state.quests.find(x => !x.hand && !isQuestDone(x));
    const el = document.getElementById('qnote-' + q.id) || Object.assign(document.body.appendChild(document.createElement('input')), { id: 'qnote-' + q.id });
    el.value = 'A private note about it'; toggleQuest(q.id, true);
  }],
  ['the same quest unticked', () => { const q = state.quests.find(x => !x.hand && isQuestDone(x) && x.cadence !== 'epic'); if (q) toggleQuest(q.id, false) }],
  ['a quest made on the board', () => {
    const put = (id, v) => { const el = document.getElementById(id) || Object.assign(document.body.appendChild(document.createElement('input')), { id }); el.value = v };
    put('newQuestName', 'Walk the dog'); put('newQuestCadence', 'daily'); put('newQuestXP', '30'); createQuest();
  }],
  ['a quest the board refuses', () => { document.getElementById('newQuestName').value = '  '; createQuest() }],
  ['a class switch', () => switchClassTo(state.classId === 'scholar' ? 'athlete' : 'scholar')],
  ['the Daily Hand checked', () => dhAutoVerify()],
  ['the day rolls over: the next redraw deals', () => renderQuests(), { at: '2026-11-09T00:05:00.000Z' }],
  ['a reroll on the new day', () => dhReroll(0)],
  ['a Log on the new day', () => recordActivity({ type: 'running', customName: '', duration: '25', quality: '1', skill: '', primary: '', secondary: '', notes: '' })],
  ['the board drawn again', () => renderQuests()],
];

async function generate(file, questsScenario) {
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-GB', timezoneId: 'Europe/London' });
    const p = await ctx.newPage(), errors = [];
    let answer = true;
    p.on('pageerror', e => errors.push(String(e)));
    p.on('dialog', d => (answer ? d.accept() : d.dismiss()).catch(() => { }));
    await p.clock.install({ time: new Date(START) });
    await p.addInitScript('localStorage.setItem("lifemax_rpg_prototype_v1", ' + JSON.stringify(JSON.stringify(SAVE)) + ');');
    await p.addInitScript(mock.script({ fingerprint: CONTENT.contentHash, quests: !!questsScenario }));
    await p.addInitScript(CAPTURE);
    await p.goto('file://' + file);
    await p.waitForFunction(() => typeof window.LmaShadow === 'object' && LmaShadow.status().live, null, { timeout: 60000 });
    await p.waitForTimeout(1500);
    const steps = [];
    for (const [name, step, opts] of questsScenario ? QUEST_STEPS : STEPS) {
      answer = !opts || opts.confirm !== false;
      if (opts && opts.at) { await p.clock.setSystemTime(new Date(opts.at)); await p.waitForTimeout(50) }
      const before = await p.evaluate(() => window.__corpus.length);
      await p.evaluate('(' + step.toString() + ')()');
      await p.waitForTimeout(150);
      steps.push({ name, messages: (await p.evaluate(() => window.__corpus.length)) - before });
    }
    await p.waitForTimeout(300);
    if (errors.length) throw new Error('the page threw: ' + errors.slice(0, 3).join(' | '));
    const messages = await p.evaluate(() => window.__corpus);
    const refused = await p.evaluate(() => window.__shadowMock.refused);
    if (refused.length) throw new Error('the stand-in refused: ' + refused.join());
    return { steps, messages };
  } finally { await browser.close() }
}

module.exports = { generate, FILE, QUESTS_FILE, STEPS, QUEST_STEPS };

if (require.main === module) {
  const args = process.argv.slice(2), quests = args.includes('--quests'), out = quests ? QUESTS_FILE : FILE;
  const file = require('../tests/current-page.js')(args.find(a => a !== '--quests'));
  generate(file, quests).then(({ steps, messages }) => {
    fs.writeFileSync(out, '[\n' + messages.map(m => JSON.stringify(m)).join(',\n') + '\n]\n');
    console.log('wrote ' + path.relative(process.cwd(), out) + ': ' + messages.length + ' messages; ' + steps.map(s => s.name + ' ' + s.messages).join(', '));
  }).catch(e => { console.error(e); process.exit(1) });
}
