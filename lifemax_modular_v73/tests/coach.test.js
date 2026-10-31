/* Journal coach acceptance tests. Run:  NODE_PATH=$(npm root -g) node tests/coach.test.js [built html]
   Seeds a fake history into the app (no real time passes) and checks archetypes, wording, prompts, patterns, welcome back,
   rewards (once each, never a streak day), weekly and monthly reviews and the care note. */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x ? '  ' + JSON.stringify(x) : '')) } };
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); const errs = [];
  p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p.goto('file://' + file); await p.waitForTimeout(700);
  // seed: day offsets back from today -> [xp, hour, name, attr, type]
  const seed = async (plan, reset) => p.evaluate(([plan, reset]) => {
    if (reset) { state.activities = []; state.dayReflections = {}; state.journalCoach = {}; state.questDone = state.questDone || {} }
    const mk = (off, xp, hour, name, attr, type) => { const d = new Date(); d.setDate(d.getDate() - off); d.setHours(hour, 5, 0, 0); state.activities.push({ id: 's' + Math.random(), type: type || 'custom', name, icon: 'book', duration: 30, skill: name, primary: attr || 'discipline', secondary: '', xp, date: d.toISOString(), notes: '', xpSplit: [{ attr: attr || 'discipline', xp }] }) };
    plan.forEach(r => mk(...r)); LifeMaxCoach.reset(); renderHistory();
  }, [plan, !!reset]);
  const dstr = off => p.evaluate(o => { const d = new Date(); d.setDate(d.getDate() - o); return d.toLocaleDateString('en-CA') }, off);
  const day = async off => { const d = await dstr(off); return p.evaluate(d => ({ m: LifeMaxCoach.day(d), a: LifeMaxCoach.arch(d), i: LifeMaxCoach.insight(d), q: LifeMaxCoach.prompt(d), d }), d) };
  // 20 ordinary days of ~100 XP (mornings), then specific days
  const base = []; for (let o = 6; o <= 24; o++) if (o % 6 !== 0) base.push([o, 100, 9, 'Study', 'intelligence']);
  await seed(base, true);
  // an overdrive yesterday, a quiet day 2 days ago, an ordinary day today
  await seed([[1, 330, 9, 'Big push', 'physique'], [2, 20, 9, 'Tiny', 'discipline'], [0, 110, 9, 'Study', 'intelligence']]);
  let r = await day(1); ok('OVERDRIVE: 330 XP against ~100 average', r.a === 'OVERDRIVE' && /330/.test(r.i.text), { a: r.a, m: r.m });
  r = await day(0); ok('ordinary day is a steady stride', r.a === 'BALANCED_STRIDE', { a: r.a, xp: r.m.xp, avg: r.m.avg });
  r = await day(2); ok('a 20 XP day is low power, not a failure', r.a === 'LOW_POWER' && !/fail|lazy|should/i.test(r.i.text), r.i);
  // determinism and variants
  const t1 = (await day(0)).i.text, t2 = (await day(0)).i.text; ok('same data gives the same words', t1 === t2);
  const seen = new Set(); for (let o = 0; o < 40; o++) { const d = await dstr(o); seen.add(await p.evaluate(d => { const i = LifeMaxCoach.insight(d); return i ? i.text.replace(/\d+/g, '#') : null }, d)) } ok('different dates use different variants', seen.size >= 4, seen.size);
  r = await day(0); ok('every day with data gets a prompt from the right pool', r.q && /^PRM_C/.test(r.q.id), r.q);
  r = await day(1); ok('overdrive day draws from the energy pool', /^PRM_A/.test(r.q.id), r.q);
  r = await day(2); ok('quiet day draws from the kindness pool', /^PRM_D/.test(r.q.id), r.q);
  // swap cycles within the pool and persists
  const d0 = await dstr(0); const before = (await day(0)).q.id; await p.evaluate(d => jcSwap(d), d0); const after = (await day(0)).q.id; ok('swap changes the question within its pool', before !== after && after.slice(0, 5) === before.slice(0, 5), { before, after });
  // rewards: 40 characters on today pays once; editing later does not pay again; older days pay nothing
  const xp0 = await p.evaluate(() => state.activities.reduce((s, a) => s + a.xp, 0));
  await p.evaluate(d => { document.getElementById('dayReflection').value = 'Short'; saveDayReflection(d, 'Short') }, d0);
  ok('under 40 characters pays nothing', (await p.evaluate(() => state.activities.reduce((s, a) => s + a.xp, 0))) === xp0);
  await p.evaluate(d => saveDayReflection(d, 'Today I focused well in the morning and it felt easy to start.'), d0);
  const xp1 = await p.evaluate(() => state.activities.reduce((s, a) => s + a.xp, 0)); ok('40+ characters on today pays 15 XP', xp1 === xp0 + 15, xp1 - xp0);
  await p.evaluate(d => saveDayReflection(d, 'Edited: today I focused well in the morning and it felt easy to start, again.'), d0);
  ok('editing does not pay twice', (await p.evaluate(() => state.activities.reduce((s, a) => s + a.xp, 0))) === xp1);
  const dOld = await dstr(5); await p.evaluate(d => saveDayReflection(d, 'An old day that I am writing about now, long enough to qualify otherwise.'), dOld);
  ok('old days never pay (no back-farming)', (await p.evaluate(() => state.activities.reduce((s, a) => s + a.xp, 0))) === xp1);
  ok('coach XP is not a training day or a streak day', await p.evaluate(() => state.activities.filter(a => a.type === 'coach').every(a => ecoPassive(a))));
  ok('Sparks follow the coach XP through the normal economy', await p.evaluate(() => { const b = Number(state.sparks) || 0; save(); return (Number(state.sparks) || 0) >= b }));
  // coach XP must not feed its own baseline
  ok('coach XP is excluded from the day totals it is judged by', (await day(0)).m.xp === 110);
  // patterns: 21 days, 60%+ of XP at night
  const night = []; for (let o = 0; o <= 20; o++) night.push([o, 100, 22, 'Night study', 'intelligence']);
  await seed(night, true); r = await day(0); const pats = await p.evaluate(d => LifeMaxCoach.patterns(d).map(x => x.id.split(':')[0]), r.d);
  ok('chronotype pattern found for evening-heavy XP', pats.includes('PAT_CHRONO_PEAK'), pats);
  ok('single-stat tunnel found when 100% in one stat', pats.includes('PAT_MONO_GRIND'), pats);
  // pattern needs data
  await seed([[0, 100, 9, 'a'], [1, 100, 9, 'a'], [2, 100, 9, 'a']], true); r = await day(0);
  ok('no pattern with under 7 active days', (await p.evaluate(d => LifeMaxCoach.patterns(d).length, r.d)) === 0);
  // cooldown: a chip is shown on one day, then rests 7 days
  await seed(night, true); await p.evaluate(() => { const d0 = new Date().toLocaleDateString('en-CA'); jcStore().pats = {}; jcStore().cooldown = {} });
  const c1 = await p.evaluate(d => { const c = jcChip(d); return c && c.id }, (await dstr(0)));
  const c2 = await p.evaluate(d => { const c = jcChip(d); return c && c.id }, (await dstr(1)));
  ok('a chip appears, and the same pattern then rests on the next day', !!c1 && c2 !== c1, { c1, c2 });
  // boom and bust
  const bb = []; for (let o = 0; o <= 20; o++) bb.push([o, o % 4 === 2 ? 400 : o % 4 === 1 ? 5 : 100, 9, 'x', 'discipline']);
  await seed(bb, true); r = await day(0); ok('boom-and-bust found', (await p.evaluate(d => LifeMaxCoach.patterns(d).map(x => x.id), r.d)).includes('PAT_BOOM_BUST'));
  // welcome back
  await seed([[10, 100, 9, 'Study', 'intelligence'], [11, 100, 9, 'Study', 'intelligence'], [12, 100, 9, 'Study', 'intelligence']], true);
  await seed([[0, 60, 9, 'Back', 'discipline']]);
  r = await day(0); ok('3+ quiet days then a log is WELCOME_BACK', r.a === 'WELCOME_BACK', r.a); ok('gap counts the quiet days (9)', (await p.evaluate(d => LifeMaxCoach.gap(d), r.d)) === 9);
  await p.evaluate(() => { histSel = getToday(); renderHistory() });
  const wb = await p.evaluate(() => document.querySelector('.jc-welcome') && document.querySelector('.jc-welcome').textContent);
  ok('welcome card is calm: no penalty language', wb && /Good to see you|Welcome back|New chapter/.test(wb) && !/lost|broken|penalt(y|ies) apply|missed/i.test(wb), wb);
  const w0 = await p.evaluate(() => state.activities.reduce((s, a) => s + a.xp, 0));
  await p.evaluate(() => { const t = jcStore().welcome[getToday()].tier; if (t === 1) jcClaim('pet'); else if (t === 3) jcClaim('fresh') });
  const tier = await p.evaluate(() => jcStore().welcome[getToday()].tier);
  ok('tier 2 for 7 to 13 days', tier === 2, tier);
  // tier 3 resets the baseline
  await seed([[30, 100, 9, 'Study', 'intelligence'], [31, 100, 9, 'Study', 'intelligence'], [32, 100, 9, 'Study', 'intelligence'], [0, 60, 9, 'Back', 'discipline']], true);
  await p.evaluate(() => { histSel = getToday(); renderHistory() });
  ok('a quiet stretch collapses into one line in the day list', await p.evaluate(() => document.querySelectorAll('.jx-quiet').length === 1 && /quiet days/.test(document.querySelector('.jx-quiet').textContent)));
  ok('tier 3 (14+ days) resets the baseline to 120 for a week', await p.evaluate(() => { const t = jcStore().welcome[getToday()]; return t.tier === 3 && jcStore().base.value === 120 }));
  const f0 = await p.evaluate(() => state.activities.reduce((s, a) => s + a.xp, 0)); await p.evaluate(() => { jcClaim('fresh'); jcClaim('fresh') });
  ok('the welcome reward pays once', (await p.evaluate(() => state.activities.reduce((s, a) => s + a.xp, 0))) === f0 + 25);
  // weekly and monthly reviews
  const wk = []; for (let o = 1; o <= 40; o++) wk.push([o, 80 + (o * 7) % 60, 9, 'Study', o % 3 ? 'intelligence' : 'physique']);
  await seed(wk, true);
  const rl = await p.evaluate(() => { const days = journalDays(); return LifeMaxCoach.reviewList(days).map(x => x.key) });
  ok('weekly reviews exist for finished weeks', rl.filter(k => k[0] === 'W').length >= 4, rl.length); ok('a monthly review exists for a finished month', rl.some(k => k[0] === 'M'), rl);
  const html = await p.evaluate(k => LifeMaxCoach.reviewHtml(k), rl.find(k => k[0] === 'W'));
  ok('weekly card has headline, grid and a closing prompt', /XP across/.test(html) && /jc-grid/.test(html) && /Director dial/.test(html), html.slice(0, 200));
  const mh = await p.evaluate(k => LifeMaxCoach.reviewHtml(k), rl.find(k => k[0] === 'M'));
  ok('monthly card has Chronicle, levels and the boss question', /Chronicle/.test(mh) && /Levels gained/.test(mh) && /chapter in your character/.test(mh));
  const wkey = rl.find(k => k[0] === 'W'); const rx = await p.evaluate(() => state.activities.reduce((s, a) => s + a.xp, 0));
  await p.evaluate(k => { jcSaveReview(k, 'x'.repeat(60)); jcSaveReview(k, 'y'.repeat(70)) }, wkey);
  ok('weekly bonus pays once (+30)', (await p.evaluate(() => state.activities.reduce((s, a) => s + a.xp, 0))) === rx + 30);
  // list rendering and selection of a review
  await p.evaluate(k => { histSel = k; renderHistory() }, rl[0]); ok('a review can be opened from the list', await p.evaluate(() => !!document.querySelector('#journalEntry .jc-grid')));
  ok('the day list shows colour dots, quiet stretches and review rows', await p.evaluate(() => document.querySelectorAll('.jx-tile').length > 3 && document.querySelectorAll('.jx-rev').length > 2));
  // text being typed is kept when something else saves meanwhile (a health sync, a payday), and is saved when the box is left
  const typing = async (key, box, words) => {
    await p.evaluate(k => { navigate('history'); histSel = k; renderHistory(); window.__box = document.querySelector('#journalEntry textarea') }, key);
    await p.focus(box); await p.keyboard.type(words); await p.evaluate(() => save());
    return p.evaluate(w => { const t = document.querySelector('#journalEntry textarea'); return t === window.__box && t.value.endsWith(w) && document.activeElement === t }, words);
  };
  const d1 = await dstr(1), said = 'Slept well, trained early and the whole day went smoothly.';
  ok('a save while typing a reflection keeps the box, its text and the focus', await typing(d1, '#dayReflection', said));
  await p.click('#journalHero'); await p.waitForTimeout(50);
  ok('leaving the box saves the reflection and the entry is drawn again', await p.evaluate(([d, w]) => state.dayReflections[d] === w && document.querySelector('#journalEntry textarea') !== window.__box && document.getElementById('dayReflection').value === w && /claimed/.test(document.getElementById('jcCount').textContent), [d1, said]));
  const answer = 'One adjustment: lights out by eleven on weeknights, every week.';
  const wkey2 = rl.filter(k => k[0] === 'W')[1];
  ok('a save while typing a review answer keeps it too', await typing(wkey2, '#journalEntry textarea', answer));
  await p.click('#journalHero'); await p.waitForTimeout(50);
  ok('leaving the answer saves it', await p.evaluate(([k, w]) => jcStore().reviews[k].answer === w, [wkey2, answer]));
  // a reflection that cannot be written says so, and pays nothing for a reflection that is not stored
  const d0x = await dstr(0), xpW = await p.evaluate(() => state.activities.reduce((s, a) => s + a.xp, 0));
  const unsaved = await p.evaluate(d => {
    const real = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
    try { saveDayReflection(d, 'A whole day of good work that I want to remember later on.') } finally { Storage.prototype.setItem = real }
    const full = document.getElementById('toast').textContent;
    saveReadOnly = 'A newer version saved this'; saveReadOnlyTold = true;
    try { saveDayReflection(d, 'Another try, still a long enough reflection for the reward.') } finally { saveReadOnly = ''; saveReadOnlyTold = false }
    return { full, readOnly: document.getElementById('toast').textContent, stored: (JSON.parse(localStorage.getItem(STORE)).dayReflections || {})[d] };
  }, d0x);
  ok('a failed reflection write is reported, naming a read-only save as the reason', unsaved.full === 'Could not save to this browser (storage full?)' && unsaved.readOnly === 'Not saved: this version is read-only for your save' && unsaved.stored === undefined, unsaved);
  ok('and no XP is paid for it', (await p.evaluate(() => state.activities.reduce((s, a) => s + a.xp, 0))) === xpW);
  // care note
  await seed([[0, 100, 9, 'Study', 'intelligence']], true); const dd = await dstr(0);
  await p.evaluate(d => { saveDayReflection(d, 'I feel like there is no reason to live and I want to die'); histSel = d; renderHistory() }, dd);
  const care = await p.evaluate(() => (document.querySelector('.jc-care') || {}).textContent || ''); ok('distress words get a gentle note with a crisis line and no coach card', /988/.test(care) && !(await p.evaluate(() => document.querySelector('.jc-card'))), care.slice(0, 60));
  // loading the app with a saved history must not touch the coach before it exists (this once broke start-up)
  await seed(wk, true); await p.evaluate(() => save());
  const errs2 = []; const p2 = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); p2.on('pageerror', e => errs2.push(String(e)));
  const saved = await p.evaluate(() => localStorage.getItem('lifemax_rpg_prototype_v1')); await p2.addInitScript(v => localStorage.setItem('lifemax_rpg_prototype_v1', v), saved);
  await p2.goto('file://' + file); await p2.waitForTimeout(1500);
  ok('start-up with a saved history has no errors and the app is alive', errs2.length === 0 && await p2.evaluate(() => typeof lmaNavTo === 'function' && !!window.LifeMaxCoach), errs2);
  await p2.evaluate(() => lmaNavTo('history')); await p2.waitForTimeout(400);
  ok('the journal draws hero, tiles and a coach card after a cold start', await p2.evaluate(() => !!document.querySelector('.jx-hero .jx-stat') && document.querySelectorAll('.jx-tile').length > 3 && !!document.querySelector('.jc-card')));
  // never touches food or weight
  const src = fs.readFileSync(path.join(__dirname, '..', 'src', 'runtime', 'segments', '47-journal-coach.js'), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
  ok('the coach never reads weight or food data', !/\b(kcal|calorie|calories|dayFoodLog|mealsMeta|\.wt\b|weigh(?:t|ins?)\b(?!:))/i.test(src.replace(/font-weight/g, '')));
  ok('no page errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
