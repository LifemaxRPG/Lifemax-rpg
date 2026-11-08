/* The economy check's corpus (docs/specs/ECONOMY_SHADOW_PLAN.md WP6b): the real built page, on the established fixture save,
   with the check switched on and a stand-in NativeShadow (tests/native-shadow-mock.js), is driven through the economy actions
   native compares, and every base part and envelope text the page sends is written, in order, to
   tests/fixtures/shadow-corpus.json. The Kotlin ShadowCorpusTest replays the file through native's decoder, mirror, runner and
   comparison and expects every action compared and the same: the real transport's shapes (args, env, save answers, toasts,
   minted ids, the Daily Hand's auto-verify inside a save) as the page really sends them.
   The page's clock runs (the transport posts its envelopes on a timer), so instants and ids differ from run to run: the file is
   regenerated on purpose, not compared byte for byte. Regenerate when the page's economy or the transport changes:
     NODE_PATH=$(npm root -g) node tools/gen_shadow_corpus.js [built-page.html] */
'use strict';
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const mock = require('../tests/native-shadow-mock.js');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'shadow-corpus.json');
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

async function generate(file) {
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-GB', timezoneId: 'Europe/London' });
    const p = await ctx.newPage(), errors = [];
    let answer = true;
    p.on('pageerror', e => errors.push(String(e)));
    p.on('dialog', d => (answer ? d.accept() : d.dismiss()).catch(() => { }));
    await p.clock.install({ time: new Date(START) });
    await p.addInitScript('localStorage.setItem("lifemax_rpg_prototype_v1", ' + JSON.stringify(JSON.stringify(SAVE)) + ');');
    await p.addInitScript(mock.script({ fingerprint: CONTENT.contentHash }));
    await p.addInitScript(CAPTURE);
    await p.goto('file://' + file);
    await p.waitForFunction(() => typeof window.LmaShadow === 'object' && LmaShadow.status().live, null, { timeout: 60000 });
    await p.waitForTimeout(1500);
    const steps = [];
    for (const [name, step, opts] of STEPS) {
      answer = !opts || opts.confirm !== false;
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

module.exports = { generate, FILE, STEPS };

if (require.main === module) {
  const file = require('../tests/current-page.js')(process.argv[2]);
  generate(file).then(({ steps, messages }) => {
    fs.writeFileSync(FILE, '[\n' + messages.map(m => JSON.stringify(m)).join(',\n') + '\n]\n');
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ': ' + messages.length + ' messages; ' + steps.map(s => s.name + ' ' + s.messages).join(', '));
  }).catch(e => { console.error(e); process.exit(1) });
}
