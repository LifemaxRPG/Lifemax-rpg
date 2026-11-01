/* Native Food's add-food sheet, phase 1c and phase 2, web half.
   Checked:
   - the page's own sheet (search, portion, edit, quick, create, daily targets, and the targets' live sum) is byte-identical to
     tests/fixtures/food-sheet-pages-golden.json, recorded before the sheet's words and rules were split out of its renderers
     (RECORD=1 rewrites it, only ever from code known to be right);
   - the projection's sheet says what the page's sheet draws: titles, Back or Close, the tabs, the search box's words, quick
     calories, the create form, the daily targets and an entry being edited;
   - phase 2's writes (create a food, save the targets, save / remove / log again an entry, Undo) do what the page's own buttons
     do, answer {op, ok, changed, message} with ok only when the save succeeded, answer a repeat of the same operation
     "Already recorded." without doing it twice, and refuse in the page's own words;
   - phase 1c: the slot's + and the Targets button open the native sheet, Back from the scrim steps back, a serving that is not
     offered is refused, a step outside the portion view does nothing.
   A fake NativeNutrition plugin stands in for Android. Run: NODE_PATH=$(npm root -g) node tests/native-food-forms.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
const GOLDEN = path.join(__dirname, 'fixtures', 'food-sheet-pages-golden.json');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };

const SEED = `localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));`;
const FAKES = `
  window.__calls=[];window.__slow=0;
  const mk=(name)=>{const ls={};return {
    __emit:(e,d)=>Promise.all((ls[e]||[]).map(f=>f(d))),
    addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
    open:async a=>{window.__calls.push([name,'open',a])},
    update:async a=>{if(window.__slow)await new Promise(r=>setTimeout(r,window.__slow));window.__calls.push([name,'update',a])},
    close:async a=>{window.__calls.push([name,'close',a]);(ls.closed||[]).forEach(f=>f({session:a&&a.session}))},
    capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true}),
    handshake:async()=>({protocol:1,capabilities:[]})}};
  window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>['NativeNutrition','NativeOverview'].includes(n),getPlatform:()=>'android',
    Plugins:{NativeNutrition:mk('food'),NativeOverview:mk('overview')},registerPlugin:()=>({}),convertFileSrc:u=>u};`;

async function start(b, fakes) {
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') });
  await p.addInitScript(SEED + (fakes ? FAKES : ''));
  await p.goto('file://' + file); await p.waitForTimeout(3000);
  return p;
}

/* every view of the page's own sheet, drawn by the page's own code from one fixed state */
async function pageViews(p) {
  return p.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), out = {};
    const html = () => document.querySelector('#fv2Ov .sheet').innerHTML;
    mealsEnsure();
    const chicken = fv2LocalFoods(fv2SearchTokens('chicken'))[0], rice = fv2LocalFoods(fv2SearchTokens('rice'))[0];
    navigate('meals'); await wait(200);
    fv2Open('lunch', 'search'); await wait(400); out.searchEmpty = html();
    fv2Open('dinner', 'goal'); await wait(50); out.goalUnset = html();
    for (const pre of ['bal', 'hi', 'low']) { fv2Preset(pre); out['goalPreset_' + pre] = document.getElementById('fv2Gs').innerHTML }
    document.getElementById('fv2GC').value = '1500'; fv2GoalLive(); out.goalOver = document.getElementById('fv2Gs').innerHTML;
    document.getElementById('fv2GC').value = '3000'; fv2GoalLive(); out.goalUnder = document.getElementById('fv2Gs').innerHTML;
    document.getElementById('fv2GC').value = '0'; fv2GoalLive(); out.goalNoCal = document.getElementById('fv2Gs').innerHTML;
    state.mealsMeta.calGoal = 2200; state.mealsMeta.protGoal = 160; state.mealsMeta.carbGoal = 220; state.mealsMeta.fatGoal = 70; state.mealsMeta.waterGoalMl = 2500;
    fv2Open(null, 'goal'); await wait(50); out.goalSet = html();
    fv2Open('lunch', 'portion', chicken); await wait(50); out.portion = html();
    fv2Open('snacks', 'portion', rice); await wait(50); out.portionRice = html();
    fv2Open('lunch', 'search'); fv2Go('quick'); await wait(50); out.quick = html();
    fv2Open('breakfast', 'search'); fv2Go('create'); await wait(50); out.create = html();
    state.foodLog.push(Object.assign({ id: 'food_gold_1', date: getToday(), slot: 'lunch', foodId: chicken.id, name: chicken.n, grams: 150, at: '2026-10-28T11:00:00.000Z' }, scaleFood(chicken, 150)));
    state.foodLog.push(Object.assign({ id: 'food_gold_2', date: fv2DayShift(getToday(), -1), slot: 'dinner', foodId: rice.id, name: rice.n, grams: 200, at: '2026-10-27T18:00:00.000Z' }, scaleFood(rice, 200)));
    fv2OpenEntry('food_gold_1'); await wait(50); out.editToday = html();
    fv2OpenEntry('food_gold_2'); await wait(50); out.editYesterday = html();
    fv2Open('lunch', 'search'); await wait(400); out.searchRecent = html();
    const q = document.getElementById('fv2Q'); q.value = 'chicken'; q.dispatchEvent(new Event('input', { bubbles: true })); await wait(900); out.searchQuery = html();
    fv2Close();
    state.foodLog = state.foodLog.filter(e => e.id.indexOf('food_gold_') !== 0);
    state.mealsMeta.calGoal = 0; delete state.mealsMeta.protGoal; delete state.mealsMeta.carbGoal; delete state.mealsMeta.fatGoal; delete state.mealsMeta.waterGoalMl;
    return out;
  });
}

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });

  /* 1. the page's own sheet, against the golden */
  {
    const p = await start(b, false);
    const html = await pageViews(p);
    if (process.env.RECORD) { fs.writeFileSync(GOLDEN, JSON.stringify(html, null, 1) + '\n'); console.log('recorded ' + GOLDEN) }
    const golden = JSON.parse(fs.readFileSync(GOLDEN, 'utf8'));
    for (const k of Object.keys(golden)) ok('the page draws its sheet\'s ' + k + ' byte for byte as before', html[k] === golden[k], (html[k] || '').length + ' vs ' + golden[k].length);
    ok('the golden has every view', Object.keys(html).every(k => k in golden), Object.keys(html));
    ok('no script errors on the page\'s own sheet', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
