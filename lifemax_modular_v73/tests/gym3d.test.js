/* 3D gym guided workout. Run: NODE_PATH=$(npm root -g) node tests/gym3d.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', fs.readdirSync(path.join(__dirname, '..', 'dist')).filter(f => /^Life_Max_RPG_v\d+\.html$/.test(f)).sort((a, b) => +a.match(/v(\d+)/)[1] - +b.match(/v(\d+)/)[1]).pop()));
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 300) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const mk = async (init) => { const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); p.errs = []; p.on('pageerror', e => p.errs.push(String(e))); p.on('console', m => { if (/\[gym/.test(m.text())) p.errs.push(m.text().slice(0, 300)) }); await p.addInitScript((init || '') + "localStorage.setItem('lma_qdeal','off');if(!localStorage.getItem('lifemax_rpg_prototype_v1'))localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));"); await p.goto('file://' + file); await p.waitForTimeout(900); await p.evaluate(() => { document.querySelectorAll('.lu-card').forEach(e => e.parentElement.remove()); const d = new Date(), dow = (d.getDay() + 6) % 7, m = new Date(d); m.setDate(d.getDate() - dow); const s = m.toLocaleDateString('en-CA'); state.plan = { id: 'athlete_foundation8', classId: 'athlete', start: s, from: s, checks: {} } }); return p };
  const p = await mk(); const ev = (f, a) => p.evaluate(f, a);
  const click = a => ev(a => { const e = document.querySelector('#gymScene [data-a="' + a + '"]'); e && e.click() }, a);
  ok('exercises map to movement archetypes', await ev(() => g3Archetype('Leg Press or Goblet Squat') === 'legpress' && g3Archetype('Lat Pulldown or Assisted Pull-Up') === 'pulldown' && g3Archetype('Machine or Dumbbell Chest Press') === 'chest' && g3Archetype('Seated Cable Row') === 'row' && g3Archetype('Forearm Plank Hold') === 'plank' && g3Archetype('Treadmill') === 'walk' && g3Archetype('Hamstring Mobility Flow') === 'mobility'));
  ok('the trainer rig keeps every limb a fixed length', await ev(() => { const P = g3Lerp(G3_MOVES.legpress.A, G3_MOVES.legpress.B, .5), f = g3FK(P); const L = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]); return Math.abs(L(f.hip, f.legN.knee) - G3_L.thigh) < 1e-6 && Math.abs(L(f.legN.knee, f.legN.ank) - G3_L.shin) < 1e-6 && Math.abs(L(f.sh, f.armN.el) - G3_L.ua) < 1e-6 }));
  ok('every move has two end poses that differ (so a rep visibly moves)', await ev(() => Object.keys(G3_MOVES).every(k => JSON.stringify(G3_MOVES[k].A) !== JSON.stringify(G3_MOVES[k].B))));
  const hasTrainer = await ev(() => !!TRAINER_DATA);
  if (hasTrainer) {
  ok('the trainer model has a 54-joint skeleton with a LIFEMAX chest logo texture', await ev(() => TRAINER_DATA.joints.length === 54 && !!TRAINER_DATA.tex.logo && TRAINER_DATA.groups.some(g => g.tex === 'logo')));
  ok('every movement poses every joint to finite matrices', await ev(() => { const T = { idx: {}, b: TRAINER_DATA.joints.map(j => j.b), par: TRAINER_DATA.joints.map(j => j.p), mats: new Float32Array(54 * 16) }; TRAINER_DATA.joints.forEach((j, i) => T.idx[j.n] = i); return Object.keys(G3_MOVES).every(k => [0, .5, 1].every(u => { const P = g3Lerp(G3_MOVES[k].A, G3_MOVES[k].B, u); const m = g3TrainerPose(T, P, { mode: P.anchor, x: 0, y: P.pos[1] }); return Array.from(m).every(isFinite) })) }));
  ok('the trainer keeps his hip where the machine seat is', await ev(() => { const T = { idx: {}, b: TRAINER_DATA.joints.map(j => j.b), par: TRAINER_DATA.joints.map(j => j.p), mats: new Float32Array(54 * 16) }; TRAINER_DATA.joints.forEach((j, i) => T.idx[j.n] = i); const P = g3Lerp(G3_MOVES.chest.A, G3_MOVES.chest.B, 0); const m = g3TrainerPose(T, P, { mode: 'hip', x: 1, y: .5 }); const i = T.idx.hip; return Math.abs(m[i * 16 + 12] - 1) < .3 && Math.abs(m[i * 16 + 13] - .66) < .3 }));
  }
  ok('without the trainer data the built-in figure is used', await ev(async () => { const keep = TRAINER_DATA; TRAINER_DATA = undefined; const r = await g3TrainerInit(document.createElement('canvas').getContext('webgl2')); TRAINER_DATA = keep; return r === null }));
  await ev(() => { navigate('plan'); gwrStart(state.plan.start) }); await p.waitForSelector('#gymScene .g3-board', { timeout: 8000 });
  ok('starting a guided workout opens the 3D gym with today\'s board', await ev(() => !!document.querySelector('#gymScene .g3-gl') && document.querySelectorAll('.g3-wb-row').length >= 8));
  ok('the board shows plain text, not HTML entities', await ev(() => !/&amp;/.test(document.querySelector('.g3-board h2').textContent)));
  ok('the classic screen is hidden beneath the gym', await ev(() => getComputedStyle(document.getElementById('gymScene')).zIndex > getComputedStyle(document.getElementById('guidedWorkout')).zIndex));
  await click('begin'); await p.waitForTimeout(800);
  ok('stepping onto the floor starts the first exercise', await ev(() => gwrCurrent().phase === 'ready' && !!document.querySelector('.g3-ex h2')));
  // drive to the leg press
  for (let i = 0; i < 40; i++) { const st = await ev(() => [gwrCurrent().phase, (document.querySelector('.g3-ex h2') || {}).textContent || '']); if (/leg press/i.test(st[1]) && st[0] === 'ready') break; if (st[0] === 'timing') { await ev(() => { gwrCurrent().timerUntil = Date.now() + 150 }); await p.waitForTimeout(700); continue } await click({ ready: 'go', 'timer-done': 'logtimer', rest: 'restskip', active: 'setdone' }[st[0]]); await p.waitForTimeout(350) }
  ok('the camera walked to the leg press', await ev(() => /leg press/i.test(document.querySelector('.g3-ex h2').textContent) && G3.cur === 3));
  await click('go'); await p.waitForTimeout(1800);
  ok('starting a set shows the rep counter and a SET DONE button', await ev(() => gwrCurrent().phase === 'active' && !!document.querySelector('.g3-reps [data-g3-rep]') && !!document.querySelector('[data-a=setdone]')));
  const r1 = await ev(() => +document.querySelector('[data-g3-rep]').textContent); await p.waitForTimeout(4600);
  const r2 = await ev(() => +document.querySelector('[data-g3-rep]').textContent);
  ok('the rep counter follows the trainer\'s pace (about 4 s per rep)', r2 > r1, [r1, r2]);
  await click('setdone'); await p.waitForTimeout(500);
  ok('SET DONE opens the log sheet with weight, reps and effort', await ev(() => !document.querySelector('.g3-sheet').hidden && !!document.querySelector('.g3-step') && document.querySelectorAll('.g3-eff button').length === 5));
  await click('w+5'); await click('w+5'); await click('r+1'); await click('eff8'); await click('save'); await p.waitForTimeout(1200);
  const saved = await ev(() => { const c = state.plan.checks[state.plan.start]; const d = c.setDetails; return d && d.E0 && d.E0[0] });
  ok('the set is saved through the normal guided-workout logger', saved && saved.weight === 10 && saved.reps >= 1 && saved.effort === 8 && saved.kind === 'reps', saved);
  ok('then the gym clock rest timer starts', await ev(() => gwrCurrent().phase === 'rest' && !!document.querySelector('[data-g3-rest]')));
  await p.waitForTimeout(500);
  ok('the rest clock counts down in gym-timer digits', await ev(() => /^\d\d:\d\d$/.test(document.querySelector('[data-g3-rest]').textContent)));
  await click('rest+15'); await click('restskip'); await p.waitForTimeout(600);
  ok('skipping rest returns to the next set of the same exercise', await ev(() => gwrCurrent().phase === 'ready' && /SET 2/.test(document.querySelector('.g3-sets').textContent)));
  await click('go'); await p.waitForTimeout(600); await click('setdone'); await p.waitForTimeout(400); await click('sheetx');
  ok('"Keep going" dismisses the sheet without saving', await ev(() => document.querySelector('.g3-sheet').hidden && gwrCurrent().phase === 'active' && state.plan.checks[state.plan.start].setDetails.E0.length === 1));
  await ev(() => document.querySelector('#gymScene [data-a=menu]').click()); await p.waitForTimeout(200);
  ok('the menu offers undo, skip, swap, itinerary and end session', await ev(() => ['m-itin', 'm-undo', 'm-skip', 'm-sub', 'm-end', 'm-classic'].every(a => !!document.querySelector('[data-a="' + a + '"]'))));
  await ev(() => document.querySelector('[data-a="m-x"]').click());
  await click('pause'); await p.waitForTimeout(600);
  ok('pausing closes the 3D gym and keeps the session', await ev(() => !document.getElementById('gymScene') && gwrCurrent().phase === 'paused'));
  await ev(() => gwrResume()); await p.waitForSelector('#gymScene', { timeout: 6000 });
  ok('resuming reopens the gym where you left off', await ev(() => /leg press/i.test(document.querySelector('.g3-ex h2').textContent)));
  // switch off
  await ev(() => { g3SetPref(false); gwrPause() }); await p.waitForTimeout(400); await ev(() => gwrResume()); await p.waitForTimeout(800);
  ok('with the setting off the classic screen is used', await ev(() => !document.getElementById('gymScene') && document.getElementById('guidedWorkout').classList.contains('open')));
  ok('Settings has the Guided workout choice', await ev(() => { setPickCat('look'); const c = document.getElementById('gwrCard'); return !!c && !c.classList.contains('set-hide') && !!document.getElementById('gwr3dSel') }));
  ok('no page errors', p.errs.length === 0, p.errs);
  console.log(pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
