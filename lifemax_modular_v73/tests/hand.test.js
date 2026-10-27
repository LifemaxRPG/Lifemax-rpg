/* Daily Hand tests. Run: NODE_PATH=$(npm root -g) node tests/hand.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', fs.readdirSync(path.join(__dirname, '..', 'dist')).filter(f => /^Life_Max_RPG_v\d+\.html$/.test(f)).sort((a, b) => +a.match(/v(\d+)/)[1] - +b.match(/v(\d+)/)[1]).pop()));
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x ? '  ' + JSON.stringify(x) : '')) } };
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, hasTouch: true })).newPage(); const errs = [];
  p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lma_qdeal','quick');localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete',quests:[{id:'q1',name:'Old',cadence:'daily',xp:15,kind:'learning'},{id:'mine',name:'My own thing',cadence:'daily',xp:10},{id:'e1',name:'Epic',cadence:'epic',xp:75}]}));");
  await p.goto('file://' + file); await p.waitForTimeout(700);
  const ev = (f, a) => p.evaluate(f, a);
  ok('library has 50+ templates across all 8 attributes', await ev(() => LifeMaxDailyHand.library.length >= 50 && new Set(LifeMaxDailyHand.library.map(t => t.attr)).size === 8));
  ok('every template has a valid tier and verify tag', await ev(() => LifeMaxDailyHand.library.every(t => [1, 2, 3].includes(t.tier) && /^(self|none|act:\w+:\d+|any:\d+|water:\d+|pet:(feed|pet|play)|plan)$/.test(t.verify))));
  await ev(() => { LifeMaxDailyHand.ensure(); renderQuests() });
  const cards = await ev(() => LifeMaxDailyHand.cards().map(q => ({ id: q.id, slot: q.slot, tpl: q.tpl })));
  ok('exactly three cards, one per slot, no duplicates', cards.length === 3 && new Set(cards.map(c => c.slot)).size === 3 && new Set(cards.map(c => c.tpl)).size === 3, cards);
  ok('cards render in the Quest Board', await ev(() => document.querySelectorAll('#dailyHand .dh-card').length === 3));
  ok('custom daily and epic quests survive; old fixed daily removed', await ev(() => state.quests.some(q => q.id === 'mine') && state.quests.some(q => q.id === 'e1') && !state.quests.some(q => q.id === 'q1')));
  ok('class card draws a class attribute', await ev(() => { const q = LifeMaxDailyHand.cards().find(x => x.slot === 1); return ['physique', 'endurance', 'wellbeing'].includes(q.attribute) }));
  // determinism
  const same = await ev(() => { const d = state.dailyDraw; const a = LifeMaxDailyHand.draw(d.date, 0, 0, []).id, b = LifeMaxDailyHand.draw(d.date, 0, 0, []).id; return a === b });
  ok('same day, luck and nonce draws the same card', same);
  // weakest attribute drives maintenance
  const weak = await ev(() => { state.activities = []; state.activities.push(...ATTRS.filter(a => a.id !== 'physique').map(a => ({ id: 'x' + a.id, type: 'custom', name: 'x', duration: 10, xp: 30, primary: a.id, date: new Date().toISOString(), xpSplit: [{ attr: a.id, xp: 30 }] }))); return LifeMaxDailyHand.weakest() });
  ok('zero-XP attribute becomes the maintenance target', weak === 'physique', weak);
  // auto verification
  const target = await ev(() => { const d = state.dailyDraw, q = LifeMaxDailyHand.cards()[0]; const t = LifeMaxDailyHand.library.find(x => x.verify.startsWith('act:strength:10')); d.cards[0] = { tpl: t.id, id: 'dh_' + d.date + '_0_9', n: 9 }; state.quests = state.quests.filter(x => !x.hand); LifeMaxDailyHand.ensure(); return state.quests.find(x => x.hand && x.slot === 0).id });
  const before = await ev(() => state.activities.filter(a => a.type === 'quest').length);
  await ev(() => { state.activities.push({ id: 'run1', type: 'strength', name: 'Lift', duration: 12, xp: 10, primary: 'physique', date: new Date().toISOString(), xpSplit: [{ attr: 'physique', xp: 10 }] }); save() });
  ok('logging a matching activity completes the card automatically', await ev(id => isQuestDone(state.quests.find(q => q.id === id)), target));
  ok('completion creates a daily quest record for the 3-a-day cap', (await ev(() => state.activities.filter(a => a.type === 'quest' && a.cad === 'daily').length)) === before + 1);
  ok('a too-short activity does not complete a longer card', await ev(() => { const t = LifeMaxDailyHand.library.find(x => x.verify === 'act:strength:45'); return !LifeMaxDailyHand.verifyMet({ verify: t.verify }, getToday()) }));
  ok('a finished card cannot be rerolled', await ev(() => LifeMaxDailyHand.reroll(0) === false));
  // reroll economics
  const r = await ev(() => { state.sparks = 100; state.dailyDraw.rerollsUsed = 0; const free = LifeMaxDailyHand.cost(); const first = LifeMaxDailyHand.reroll(1); const sp1 = state.sparks; const paid = LifeMaxDailyHand.cost(); const second = LifeMaxDailyHand.reroll(2); return { free, first, sp1, paid, second, sp2: state.sparks, n: LifeMaxDailyHand.cards().length } });
  ok('first reroll is free', r.free === 0 && r.first && r.sp1 >= 100, r);
  ok('second reroll costs 50 Sparks', r.paid === 50 && r.second && r.sp2 === r.sp1 - 50 && r.n === 3, r);
  const poor = await ev(() => { state.sparks = 10; const sp = state.sparks; return LifeMaxDailyHand.reroll(2) === false && state.sparks === sp });
  ok('reroll refused without enough Sparks', poor);
  // rest day suppresses tier 3
  const rest = await ev(() => { state.restDays = state.restDays || {}; state.restDays[getToday()] = true; let bad = 0; for (let n = 0; n < 60; n++) for (let s = 0; s < 3; s++) if (LifeMaxDailyHand.draw(getToday(), n, s, []).tier === 3) bad++; return bad });
  ok('rest day never draws a hard (tier 3) card', rest === 0, rest);
  // hold to claim
  await ev(() => { state.restDays = {}; state.quests = state.quests.filter(x => !x.hand); const d = state.dailyDraw; const t = LifeMaxDailyHand.library.find(x => x.verify === 'self'); d.cards[2] = { tpl: t.id, id: 'dh_' + d.date + '_2_7', n: 7 }; LifeMaxDailyHand.ensure(); renderQuests() });
  await ev(() => { state.dailyDraw.dealt = true; navigate('quests'); document.querySelectorAll('.lu-card').forEach(e => e.parentElement.remove()) }); await p.waitForTimeout(500); await ev(() => { LifeMaxDailyHand.cards().forEach(q => dhReveal(q.slot)) }); await p.waitForTimeout(900); const btn = await p.$('#dailyHand .dh-claim[onpointerdown*="_2_7"]'); ok('self-reported card shows a hold-to-claim button', !!btn);
  await btn.scrollIntoViewIfNeeded(); const bb = await btn.boundingBox();
  await p.mouse.move(bb.x + 10, bb.y + 10); await p.mouse.down(); await p.waitForTimeout(300); await p.mouse.up(); await p.waitForTimeout(1100);
  ok('a quick tap does not claim', await ev(() => !isQuestDone(LifeMaxDailyHand.cards().find(x => x.slot === 2))));
  await ev(() => document.querySelectorAll('.lu-card').forEach(e => e.parentElement.remove())); const h2 = await p.$('#dailyHand .dh-claim[onpointerdown*="_2_7"]'); await h2.scrollIntoViewIfNeeded(); await p.waitForTimeout(300); const bb2 = await h2.boundingBox();
  await p.mouse.move(bb2.x + 10, bb2.y + 10); await p.mouse.down(); await p.waitForTimeout(1300); await p.mouse.up(); await p.waitForTimeout(300);
  ok('holding for about a second claims it', await ev(() => isQuestDone(LifeMaxDailyHand.cards().find(x => x.slot === 2))));
  // new day replaces cards, keeps history
  const nd = await ev(() => { const old = state.dailyDraw.date; state.dailyDraw.date = '2000-01-01'; state.quests.forEach(q => { if (q.hand) q.id = q.id.replace(old, '2000-01-01') }); LifeMaxDailyHand.ensure(); return { n: LifeMaxDailyHand.cards().length, ids: LifeMaxDailyHand.cards().every(q => q.id.includes(getToday())), hist: state.activities.filter(a => a.type === 'quest').length } });
  ok('a new day deals three fresh cards and keeps quest history', nd.n === 3 && nd.ids && nd.hist >= 1, nd);
  ok('progress summary counts hand cards', await ev(() => { renderQuests(); return /\/ \d+/.test(document.getElementById('qosDailyProgress').textContent) && !/\/ 0$/.test(document.getElementById('qosDailyProgress').textContent) }));
  // shuffle and deal
  await ev(() => { state.dailyDraw.dealt = false; state.dailyDraw.up = [false, false, false]; renderQuests() });
  await p.waitForTimeout(1400);
  ok('cards stay hidden while the deck shuffles', await ev(() => document.getElementById('dailyHand').classList.contains('fd-pre') && !document.getElementById('dailyStage').hidden));
  ok('the shuffle stage shows a stack of card backs', await ev(() => document.querySelectorAll('#dailyStage .fd-sback').length >= 8));
  await p.waitForTimeout(4600);
  ok('after the deal the stage is gone and the three cards are visible', await ev(() => document.getElementById('dailyStage').hidden && !document.getElementById('dailyHand').classList.contains('fd-pre') && state.dailyDraw.dealt && [...document.querySelectorAll('#dailyHand .dh-card')].every(c => getComputedStyle(c).opacity === '1')));
  ok('dealt cards start face-down and turn over on tap', await ev(() => { const c = document.querySelector('#dailyHand .dh-card:not(.is-done)'); const was = c.classList.contains('is-up'); dhReveal(+c.dataset.slot); return !was && c.classList.contains('is-up') && state.dailyDraw.up[+c.dataset.slot] }));
  ok('a second render does not replay the deal', await ev(() => { renderQuests(); return !document.getElementById('dailyHand').classList.contains('fd-pre') }));
  ok('no page errors', errs.length === 0, errs);
  console.log(pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
