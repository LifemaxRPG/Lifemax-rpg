/* A Life Director sheet left open when you leave Life Director stays behind in its hidden view, over no other page: the native
   Overview and native Food open over theirs, and Back on another page goes back instead of closing a sheet nobody can see. On Life
   Director the sheet still counts: Back closes it. Fake NativeOverview and NativeNutrition plugins stand in for Android; Life
   Director itself is drawn by the page. Run: NODE_PATH=$(npm root -g) node tests/director-sheet-scope.test.js [built html] */
const { chromium } = require('playwright');
const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
  const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript(`
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete',tipsSeen:{dashboard:true,meals:true}}));
    window.__calls=[];
    const mk=name=>{const ls={};return {
      addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
      open:async a=>{__calls.push([name,'open'])},
      update:async a=>{__calls.push([name,'update'])},
      close:async a=>{__calls.push([name,'close']);(ls.closed||[]).forEach(f=>f({session:a&&a.session,action:''}))},
      capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true})}};
    const plugins={NativeOverview:mk('overview'),NativeNutrition:mk('food')};
    window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>n in plugins,getPlatform:()=>'android',Plugins:plugins,registerPlugin:()=>({}),convertFileSrc:u=>u};
  `);
  await p.goto('file://' + file, { timeout: 120000 });
  await p.waitForFunction(() => typeof lmaBack === 'function' && window.LifeMaxFeatures, null, { timeout: 60000 });
  await p.evaluate(() => { lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { } const S = lmaLoad(); S.nativeOverview = true; S.nativeNutrition = true; lmaSave() });
  const until = (f, arg, ms = 30000) => p.waitForFunction(f, arg, { timeout: ms, polling: 100 }).then(() => true, () => false); // the first capture of the bars is slow headless
  const mark = () => p.evaluate(() => __calls.length);
  const opened = (name, n) => until(([name, n]) => __calls.slice(n).some(c => c[0] === name && c[1] === 'open'), [name, n]);
  const look = () => p.evaluate(() => ({ view: lmaView(), sheet: !!document.querySelector('#view-life-director .ld-sheet.open:not([hidden])'), overlay: !!lmaTopOverlay() }));

  // a sheet on Life Director (Goals, Add a goal), as the page draws it
  await p.evaluate(() => lmaNavTo('life-director', { fast: true }));
  await p.waitForSelector('#view-life-director [data-tab=goals]', { timeout: 30000 });
  await p.evaluate(() => document.querySelector('#view-life-director [data-tab=goals]').click()); await p.waitForTimeout(500);
  await p.evaluate(() => document.querySelector('#view-life-director [data-act=addGoal]').click());
  const sheet = await until(() => !!document.querySelector('#view-life-director .ld-sheet.open'), null, 10000);
  let r = await look();
  ok('on Life Director its open sheet is the popup Back closes', sheet && r.view === 'life-director' && r.sheet && r.overlay, r);

  // leave it open and go to the Overview
  let n = await mark();
  await p.evaluate(() => lmaNavTo('dashboard', { fast: true }));
  const overview = await opened('overview', n);
  r = await look();
  ok('on the Overview the sheet left behind is no popup, and the native Overview opens', overview && r.view === 'dashboard' && r.sheet && !r.overlay, { overview, ...r });

  n = await mark();
  await p.evaluate(() => lmaNavTo('meals', { fast: true }));
  const food = await opened('food', n);
  ok('native Food opens over Meals with the sheet still open on Life Director', food && (await look()).sheet, { food, calls: await p.evaluate(n => __calls.slice(n), n) });

  // Back on Meals goes back to the Overview; the unseen sheet is left alone
  const back = await p.evaluate(() => lmaBack());
  const went = await until(() => lmaView() === 'dashboard', null, 10000);
  r = await look();
  ok('Back on another page goes back a page and leaves the sheet on Life Director alone', back === true && went && r.sheet, { back, went, ...r });

  // back on Life Director the sheet still counts
  await p.evaluate(() => lmaNavTo('life-director', { fast: true }));
  r = await look();
  const closes = await p.evaluate(() => lmaBack());
  const closed = await until(() => !document.querySelector('#view-life-director .ld-sheet.open'), null, 10000);
  ok('back on Life Director the sheet is there and Back closes it, staying on Life Director', r.sheet && r.overlay && closes === true && closed && (await look()).view === 'life-director', { r, closes, closed });

  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
