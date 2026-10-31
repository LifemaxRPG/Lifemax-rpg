/* The Quest Board as a 3D table: opening gate, reading, resting table, inspecting cards, ledger, new quests.
   Run: NODE_PATH=$(npm root -g) node tests/table3d.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 300) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const mk = async (init, mode, pre) => { const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); p.errs = []; p.on('pageerror', e => p.errs.push(String(e))); p.on('console', m => { if (/\[table|\[deal|\[hand|\[cine|\[rest/.test(m.text())) p.errs.push(m.text().slice(0, 300)) }); await p.addInitScript((init || '') + (mode ? "localStorage.setItem('lma_qdeal','" + mode + "');" : '') + "localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));"); await p.goto('file://' + file); await p.waitForTimeout(800); await p.evaluate(pre || (() => { document.querySelectorAll('.lu-card').forEach(e => e.parentElement.remove()); navigate('quests') })); return p };
  // WebGL here is software-rendered: a scene that goes can take seconds to give up its context, and each section closes its page
  // when it is done, so that only one scene renders at a time
  const sceneGone = (p, t) => p.waitForFunction(() => !document.getElementById('fateScene'), null, { timeout: t || 8000 });
  // ---- first visit: the gate is the opening shot
  let p = await mk();
  await p.waitForSelector('#fateScene.fs-3d .gate3-btn', { timeout: 8000 }); await p.waitForTimeout(2200);
  ok('the first visit of the day opens on the 3D table with a "Get your quests" button', await p.evaluate(() => /Get your quests/i.test(document.querySelector('.gate3-btn').getAttribute('aria-label')) && !!document.querySelector('#fateScene .fs-gl')));
  ok('the scene stops above the bottom bar so you can always leave', await p.evaluate(() => { const s = document.getElementById('fateScene').getBoundingClientRect(), r = document.querySelector('.sidebar').getBoundingClientRect(); return s.bottom <= r.top + 1 }));
  ok('the old Quest Board page is fully covered', await p.evaluate(() => { const s = document.getElementById('fateScene').getBoundingClientRect(); return s.top <= 0 && s.left <= 0 && s.right >= innerWidth }));
  ok('Skip stays hidden until the button is pressed', await p.evaluate(() => getComputedStyle(document.querySelector('.fs-skip')).display === 'none'));
  ok('swipe-to-change-tab ignores the scene', await p.evaluate(() => !!document.getElementById('fateScene').closest('[data-noswipe]')));
  await p.click('.gate3-btn'); await p.waitForSelector('#fateScene[data-await="flip"]', { timeout: 40000 });
  ok('the reading waits for you to turn the card', true);
  const drag = async (dx, dy, steps) => { await p.mouse.move(206, 400); await p.mouse.down(); for (let i = 1; i <= steps; i++) { await p.mouse.move(206 + dx * i / steps, 400 + dy * i / steps); await p.waitForTimeout(16) } await p.mouse.up() };
  await drag(24, 0, 5); await p.waitForTimeout(900);
  ok('a small nudge springs back', await p.evaluate(() => document.getElementById('fateScene').dataset.await === 'flip'));
  const t = Date.now(); await p.click('.fs-skip'); await p.waitForSelector('#fateScene.rest', { timeout: 8000 });
  ok('Skip jumps to the finished table', Date.now() - t < 5000, Date.now() - t);
  ok('and the quests are recorded as dealt', await p.evaluate(() => state.dailyDraw.dealt && state.dailyDraw.up.every(Boolean)));
  await p.waitForTimeout(900);
  // ---- the resting table
  ok('the scene stays as the Quest Board, with the progress line', await p.evaluate(() => !!document.querySelector('#fateScene.rest .rest-prog') && /FULFILLED/i.test(document.querySelector('.rest-prog .qpxt').getAttribute('aria-label'))));
  ok('Ledger, New quest and Reshuffle buttons are there', await p.evaluate(() => document.querySelectorAll('.rest-btn[data-a]').length === 3));
  const center = i => p.evaluate(i => { const c = null; return window.__q3 ? window.__q3(i) : null }, i);
  // find where card 1 is on screen by sampling taps: the cards sit near y=445
  const tapCard = async () => { for (const [x, y] of [[198, 445], [200, 430], [200, 460], [130, 450], [270, 450]]) { await p.mouse.click(x, y); await p.waitForTimeout(900); if (await p.evaluate(() => !document.querySelector('.fs-dock3').hidden)) return true } return false };
  ok('tapping a card lifts it and shows its controls', await tapCard());
  ok('the dock shows the quest XP and a way to put the card back', await p.evaluate(() => /XP/i.test(document.querySelector('.fs-dock3 .dh-xp .qpxt').getAttribute('aria-label')) && !!document.querySelector('.fs-dock3 .dock-x')));
  await drag(200, 120, 9); await p.waitForTimeout(900);
  ok('an inspected card can be turned on any axis and settles upright', await p.evaluate(() => !document.querySelector('.fs-dock3').hidden));
  await p.click('.dock-x'); await p.waitForTimeout(1200);
  ok('Put back returns the card to the table', await p.evaluate(() => document.querySelector('.fs-dock3').hidden));
  // ---- the ledger
  await p.click('.rest-btn[data-a=ledger]'); await p.waitForTimeout(400);
  ok('the Ledger lists weekly missions, your own quests, epics and badges', await p.evaluate(() => { const l = document.querySelector('.fs-ledger'); return !l.hidden && l.querySelectorAll('.lg-row').length >= 3 && /WEEKLY/i.test(l.innerHTML) && /EPIC/i.test(l.innerHTML) && /BADGES/i.test(l.innerHTML) }));
  const wk = await p.evaluate(() => state.quests.find(q => q.cadence === 'weekly' && q.kind !== 'nutrition').id);
  await p.evaluate(id => dhLedgerToggle(id), wk); await p.waitForTimeout(400);
  ok('completing a weekly from the Ledger works and updates the row', await p.evaluate(id => isQuestDone(state.quests.find(q => q.id === id)) && !!document.querySelector('.lg-row.done'), wk));
  // a Ledger longer than the screen keeps its place: the scene's sync runs every 450 ms, and the list is redrawn only when what it
  // shows changes
  await p.evaluate(() => { for (let i = 0; i < 14; i++) state.quests.push({ id: 'lgfill_' + i, name: 'Long list ' + i, desc: 'Makes the Ledger longer than the screen.', cadence: 'daily', xp: 5, kind: 'custom' }) });
  ok('quests added while the Ledger is open appear at the next sync', await p.waitForFunction(() => document.querySelectorAll('.lg-chk[data-id^="lgfill_"]').length === 14, null, { timeout: 4000 }).then(() => true, () => false));
  // counts the Ledger's redraws, and marks what is on screen so a redraw shows
  const lgNow = () => p.evaluate(() => { const b = document.querySelector('.lg-body'), r = window.__lgPick && b.querySelector('.lg-chk[data-id="' + window.__lgPick + '"]');
    return { top: b.scrollTop, max: b.scrollHeight - b.clientHeight, redraws: window.__lgRedraws, sameRow: !!r && r === window.__lgRow, sameClose: document.querySelector('.lg-close') === window.__lgClose, focused: !!r && document.activeElement === r, done: !!r && r.closest('.lg-row').classList.contains('done') } });
  const top0 = await p.evaluate(() => { const l = document.querySelector('.fs-ledger'), b = l.querySelector('.lg-body'); b.scrollTop = Math.round((b.scrollHeight - b.clientHeight) / 2);
    const box = b.getBoundingClientRect(), r = [...b.querySelectorAll('.lg-chk[data-id^="lgfill_"]')].find(x => { const q = x.getBoundingClientRect(); return q.top >= box.top + 4 && q.bottom <= box.bottom - 4 });
    window.__lgPick = r && r.dataset.id; window.__lgRow = r; window.__lgClose = l.querySelector('.lg-close'); window.__lgRedraws = 0;
    new MutationObserver(m => { window.__lgRedraws += m.length }).observe(l, { childList: true, subtree: true }); return b.scrollTop });
  await p.waitForTimeout(1600);
  const still = await lgNow();
  ok('a scrolled Ledger keeps its place across several sync ticks, nothing in it redrawn', still.max > 300 && top0 > 100 && still.top === top0 && still.redraws === 0 && still.sameRow && still.sameClose, Object.assign({ top0 }, still));
  // ticking a row the owner can see, with a plain click, redraws the list where it was, the focus staying on that row
  await p.click('.lg-chk[data-id="' + await p.evaluate(() => window.__lgPick) + '"]');
  await p.waitForFunction(() => window.__lgRedraws > 0, null, { timeout: 4000 }).catch(() => {}); await p.waitForTimeout(600);
  const ticked = await lgNow();
  ok('completing a quest redraws the Ledger in place: the row shows it, the scroll and the focus stay', ticked.redraws > 0 && !ticked.sameRow && ticked.done && ticked.top === top0 && ticked.focused && ticked.sameClose, Object.assign({ top0 }, ticked));
  // the close button is drawn once, with the scene, and holds still, so a plain click lands; the Ledger hiding shows that it did
  const closeLedger = async () => { await p.click('.lg-close'); await p.waitForSelector('.fs-ledger', { state: 'hidden', timeout: 2000 }) };
  await closeLedger();
  await p.click('.rest-btn[data-a=ledger]'); await p.waitForSelector('.fs-ledger', { state: 'visible', timeout: 2000 });
  ok('the Ledger opens at the top again', await p.evaluate(() => document.querySelector('.lg-body').scrollTop === 0));
  await p.evaluate(() => { for (let i = state.quests.length - 1; i >= 0; i--) if (state.quests[i].id.startsWith('lgfill_')) state.quests.splice(i, 1) }); await closeLedger();
  // ---- new quest
  await p.click('.rest-btn[data-a=new]'); await p.waitForTimeout(300);
  await p.fill('#nqName', 'Read the old letters'); await p.click('.nq-card .rest-btn:not(.dim)'); await p.waitForTimeout(700);
  ok('a new quest made in the scene is saved and appears in the Ledger', await p.evaluate(() => state.quests.some(q => q.name === 'Read the old letters') && /Read the old letters/.test(document.querySelector('.fs-ledger').innerHTML) && !document.querySelector('.fs-ledger').hidden));
  await closeLedger();
  // ---- leaving and coming back
  await p.evaluate(() => navigate('dashboard')); await sceneGone(p);
  ok('leaving the Quest Board closes the scene', true);
  await p.evaluate(() => navigate('quests')); await p.waitForSelector('#fateScene.rest', { timeout: 12000 });
  ok('coming back opens the table again, no gate and no reading', await p.evaluate(() => !document.querySelector('.gate3-btn') && !!document.querySelector('.rest-bar')));
  // ---- reshuffle
  await p.waitForTimeout(2200); await p.click('.rest-btn[data-a=again]');
  await p.waitForSelector('#fateScene.fs-3d:not(.rest)', { timeout: 8000 });
  ok('Reshuffle plays the reading again, without the gate', await p.evaluate(() => !document.querySelector('.gate3-btn')));
  await p.waitForTimeout(600); await p.click('.fs-skip'); await p.waitForSelector('#fateScene.rest', { timeout: 8000 });
  ok('the scene raises no page errors', p.errs.length === 0, p.errs); await p.context().close();
  // ---- gate choices
  const g = await mk(); await g.waitForSelector('.gate3-skip', { timeout: 8000 }); await g.waitForTimeout(600);
  await g.evaluate(() => navigate('dashboard')); await sceneGone(g);
  ok('leaving during the gate closes the scene and the day is not used up', await g.evaluate(() => !state.dailyDraw.dealt));
  await g.evaluate(() => navigate('quests')); await g.waitForSelector('.gate3-skip', { timeout: 8000 }); await g.waitForTimeout(800); await g.click('.gate3-skip'); await sceneGone(g);
  ok('"Not now" closes the scene and marks the day as dealt', await g.evaluate(() => state.dailyDraw.dealt));
  await g.evaluate(() => { navigate('dashboard'); navigate('quests') }); await g.waitForSelector('#fateScene.rest', { timeout: 12000 });
  ok('and the next visit shows the table directly', true); await g.context().close();
  // ---- leaving in the middle of the reading
  const lv = await mk(); await lv.waitForSelector('.gate3-btn', { timeout: 8000 }); await lv.waitForTimeout(800); await lv.click('.gate3-btn'); await lv.waitForTimeout(5200);
  ok('the reading is playing', await lv.evaluate(() => !!document.getElementById('fateScene') && !document.querySelector('#fateScene.rest')));
  await lv.evaluate(() => navigate('food')); await sceneGone(lv);
  ok('leaving the Quests tab in the middle of the reading ends it', await lv.evaluate(() => !document.getElementById('fateScene')));
  ok('and the day is not used up, so the reading plays when you come back', await lv.evaluate(() => !state.dailyDraw.dealt));
  await lv.evaluate(() => { document.documentElement.classList.add('sv-on') });
  ok('the scrubbing veil hides the 3D table layer', await lv.evaluate(() => { const e = document.createElement('div'); e.id = 'fateScene'; document.body.appendChild(e); const hidden = getComputedStyle(e).visibility === 'hidden'; e.remove(); document.documentElement.classList.remove('sv-on'); return hidden })); await lv.context().close();
  // ---- no WebGL2: the old flat page and splash
  const f = await mk("(function(){var o=HTMLCanvasElement.prototype.getContext;HTMLCanvasElement.prototype.getContext=function(t){if(t==='webgl2'&&this.classList.contains('fs-gl'))return null;return o.apply(this,arguments)}})();");
  await f.waitForSelector('#dhGate .gate-btn', { timeout: 8000 });
  ok('without WebGL2 it falls back to the flat splash', true);
  await f.click('#dhGate .gate-btn'); await f.waitForSelector('#fateScene .cine-card', { timeout: 8000 }); await f.click('.fs-skip'); await sceneGone(f, 9000);
  ok('and the flat reading, ending on the normal page', await f.evaluate(() => [...document.querySelectorAll('#dailyHand .dh-card')].every(c => c.classList.contains('is-up')))); await f.context().close();
  // ---- other deal modes keep the old page
  const qk = await mk(null, 'quick'); await qk.waitForTimeout(1800);
  ok('Quick mode keeps the old Quest Board page', await qk.evaluate(() => !document.getElementById('fateScene')));
  await qk.evaluate(() => { dhReveal(0) }); await qk.evaluate(() => document.querySelector('#dailyHand').scrollIntoView({ block: 'center' })); await qk.waitForTimeout(500);
  await qk.click('#dailyHand .dh-card[data-slot="0"] .dh-corner.tl'); await qk.waitForTimeout(900);
  ok('tapping a card corner turns it over to its back (old page)', await qk.evaluate(() => document.querySelector('#dailyHand .dh-card[data-slot="0"]').classList.contains('is-turned')));
  ok('Settings lists the 3D table choice', await qk.evaluate(() => { setPickCat('look'); const o = [...document.querySelectorAll('#qdealSel option')].map(x => x.value); return o.includes('table') && o.includes('cine') && o.includes('quick') && o.includes('off') })); await qk.context().close();
  // the flat page never flashes
  const fl = await mk(null, null, () => { document.querySelectorAll('.lu-card').forEach(e => e.parentElement.remove()); window.__vis = []; const v = document.getElementById('view-quests'); const sample = () => window.__vis.push(getComputedStyle(v).visibility + '|' + v.classList.contains('active') + '|' + !!document.getElementById('fateScene')); const t = setInterval(sample, 16); window.__stop = () => clearInterval(t); navigate('quests') });
  await fl.waitForSelector('#fateScene.fs-3d .gate3-btn', { timeout: 8000 }); await fl.waitForTimeout(500);
  const bad1 = await fl.evaluate(() => { window.__stop(); return window.__vis.filter(s => s.startsWith('visible|true|false')).length });
  ok('the flat page is never visible while the table opens (first visit)', bad1 === 0, bad1);
  await fl.click('.gate3-skip'); await sceneGone(fl); await fl.evaluate(() => { window.__vis = []; const v = document.getElementById('view-quests'); window.__t2 = setInterval(() => window.__vis.push(getComputedStyle(v).visibility + '|' + v.classList.contains('active') + '|' + !!document.getElementById('fateScene')), 16); navigate('dashboard'); navigate('quests') });
  await fl.waitForSelector('#fateScene.rest', { timeout: 12000 }); await fl.waitForTimeout(2200);
  const bad2 = await fl.evaluate(() => window.__vis.filter(s => s.startsWith('visible|true|false')).length); ok('nor when you come back to the resting table', bad2 === 0, bad2);
  await fl.evaluate(() => { window.__vis.length = 0 }); await fl.click('.rest-btn[data-a=again]'); await fl.waitForSelector('#fateScene.fs-3d:not(.rest)', { timeout: 8000 });
  const bad3 = await fl.evaluate(() => window.__vis.filter(s => s.startsWith('visible|true|false')).length); ok('nor during Reshuffle', bad3 === 0, bad3);
  // ---- leaving while the resting table is still swooping in (a scrub passing through Quests) must not leave the scene up
  await fl.evaluate(() => navigate('character')); await fl.waitForTimeout(3000);
  await fl.evaluate(() => navigate('quests')); await fl.waitForTimeout(300); await fl.evaluate(() => navigate('meals')); await fl.waitForTimeout(900);
  ok('leaving the Quests tab during the table swoop removes the scene', await fl.evaluate(() => !document.getElementById('fateScene')));
  console.log(pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
