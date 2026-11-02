/* The Workout plan's own markup, byte for byte, before and after its data was split out for the native screen: the four
   sign-up offers (what renderPlan draws with no plan, for each class) and the guided workout's screens, one per phase.
   tests/fixtures/plan-golden.json was recorded (RECORD=1) from the code as it was before the split; only ever re-record from
   code known to be right. Run: NODE_PATH=$(npm root -g) node tests/native-plan-golden.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
const GOLDEN = path.join(__dirname, 'fixtures', 'plan-golden.json');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
  const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') });
  await p.addInitScript(`
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete',
      plan:{id:'athlete_foundation8',classId:'athlete',start:'2026-10-26',from:'2026-10-26',checks:{}}}));
    localStorage.setItem('lma_gwr3d','off');`);
  await p.goto('file://' + file); await p.waitForTimeout(3000);
  await p.clock.pauseAt(new Date('2026-10-28T12:00:10Z')); // time stands still, so countdowns read the same every run
  const html = await p.evaluate(() => {
    const out = { offers: {}, runner: {} };
    lmaThumbWarm = async () => { };
    const plan = state.plan;
    // the offers: renderPlan with no plan, for each class
    state.plan = null;
    for (const c of ['athlete', 'scholar', 'entrepreneur', 'explorer']) { state.classId = c; renderPlan(); out.offers[c] = document.getElementById('planBody').innerHTML }
    state.classId = 'athlete'; state.plan = plan;
    // the runner: each phase of a session on Wednesday, with what each shows
    const root = document.getElementById('guidedWorkout'), date = '2026-10-28', draw = name => { root.innerHTML = ''; gwrRender(); out.runner[name] = root.innerHTML };
    gwrStart(date);
    root.classList.add('open');
    const g = () => state.guidedWorkout;
    draw('intro');
    gwrBegin(); draw('ready');
    // up to the first exercise that takes a weight, then its sets: active, timing, timer-done, rest
    const d = planDay(date);
    for (const it of d.items) { if (it.wt) break; for (let i = 0; i < it.n; i++) planToggle(date, it.k, i, true) }
    g().phase = 'ready'; draw('ready-weighted');
    g().phase = 'active'; g().resumePhase = 'active'; draw('active-weighted');
    pcheck(date).setDetails = { [gwrStepNext(g()).it.k]: [{ weight: 65, reps: 10 }] };
    draw('active-after-set');
    g().phase = 'timing'; g().timerTargetMs = 60000; g().timerUntil = Date.now() + 45000; draw('timing');
    g().phase = 'timer-done'; g().timerTargetMs = 60000; g().timerActualMs = 30000; g().timerUntil = 0; draw('timer-done-early');
    g().timerActualMs = 60000; draw('timer-done-full');
    g().phase = 'rest'; g().restLength = 90; g().restUntil = Date.now() + 40000; draw('rest');
    g().skip = { [gwrStepNext(g()).it.k]: true }; g().phase = 'ready'; draw('ready-after-skip');
    g().skip = {}; d.items.forEach(it => { for (let i = 0; i < it.n; i++) planToggle(date, it.k, i, true) });
    gwrComplete(); draw('complete-full');
    return out;
  });
  if (process.env.RECORD) { fs.writeFileSync(GOLDEN, JSON.stringify(html, null, 1) + '\n'); console.log('recorded ' + GOLDEN) }
  const golden = JSON.parse(fs.readFileSync(GOLDEN, 'utf8'));
  for (const k of Object.keys(golden.offers)) ok('the ' + k + ' offer is byte-identical to the golden', golden.offers[k] === html.offers[k]);
  for (const k of Object.keys(golden.runner)) ok('the guided workout\'s ' + k + ' screen is byte-identical to the golden', golden.runner[k] === html.runner[k]);
  ok('the golden has the four offers and the runner\'s screens', Object.keys(golden.offers).length === 4 && Object.keys(golden.runner).length >= 10);
  ok('no page errors', errs.length === 0, errs);
  console.log(pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
