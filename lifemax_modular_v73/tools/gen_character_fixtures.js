/* Character's actions fixtures, from the web app's own projection and commands: the projection a screen opens with
   (actions on, three benchmarks) before and after Daily Luck is rolled, and the replies the page gives to each command.
   Regenerate only when the projection or a reply changes:
     NODE_PATH=$(npm root -g) node tools/gen_character_fixtures.js
   Output in android-wrapper/native/src/test/resources/fixtures: character-actions.json, character-actions-rolled.json and
   character-replies.json (a map of name to reply). The dice are fixed (3, 6, 1, 4) so the roll is the same every time. */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path'); const os = require('os');
const currentPage = require('../tests/current-page');
const root = path.resolve(__dirname, '..'), target = path.resolve(root, '../android-wrapper/native/src/test/resources/fixtures');
const write = (name, data) => { fs.mkdirSync(target, { recursive: true }); fs.writeFileSync(path.join(target, name), JSON.stringify(data, null, 1) + '\n'); console.log('wrote ' + name) };
(async () => {
  const file = currentPage.build(path.join(os.tmpdir(), 'lm_charfx_' + process.pid + '.html'));
  const site = await currentPage.serve(file);
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') });
  await p.addInitScript(`
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'Mara',tutorialDone:true,classId:'athlete',
      benchmarks:[{name:'5K run time',value:'28:30',date:'2026-10-01T00:00:00.000Z'},{name:'Bench press',value:'185 lb',date:'2026-10-02T00:00:00.000Z'},{name:'Pull-ups',value:'8',date:'2026-10-03T00:00:00.000Z'}]}));
    localStorage.setItem('lifemax_android_prefs_v1', JSON.stringify({nativeCharacter:true}));
    crypto.getRandomValues = a => { [2, 5, 0, 3, 2, 5, 0, 3].forEach((v, i) => { a[i] = v }); return a };
    window.__calls=[]; addEventListener('DOMContentLoaded',()=>{lmaThumbWarm=async()=>{}});
    const ls={}; const plugin={__emit:(e,d)=>(ls[e]||[]).map(f=>f(d)),addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
      open:async a=>{window.__calls.push(['open',a])},update:async a=>{window.__calls.push(['update',a])},
      close:async a=>{(ls.closed||[]).forEach(f=>f({session:a&&a.session}))},capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true}),
      handshake:async()=>({protocol:1,capabilities:['character.projection.v1','character.actions.v1']})};
    window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>n==='NativeCharacter'||n==='NativeLog',getPlatform:()=>'android',
      Plugins:{NativeCharacter:plugin,NativeLog:Object.assign({},plugin,{__emit:()=>[]})},registerPlugin:()=>({}),convertFileSrc:u=>u};`);
  await p.goto(site.url); await p.waitForTimeout(3000);
  const out = await p.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), C = window.Capacitor.Plugins.NativeCharacter;
    showLevelUp = function () { };
    navigate('character'); for (let t = 0; t < 120 && !window.__calls.some(c => c[0] === 'open'); t++) await wait(50);
    const clean = o => JSON.parse(JSON.stringify(o)), opened = clean(window.__calls.find(c => c[0] === 'open')[1].snapshot);
    let n = 0; const replies = {};
    const send = async (name, command, payload) => { const id = 'r' + (++n); await Promise.all(C.__emit('command', { command, payload, requestId: id }));
      const u = [...window.__calls].reverse().find(c => c[0] === 'update' && c[1].reply && c[1].reply.requestId === id); if (!u) throw new Error('no reply for ' + name + ' ' + JSON.stringify(window.__calls.map(c => c[0])) + ' view ' + lmaView()); replies[name] = clean(u[1].reply); return u[1] };
    const day = getToday();
    const realSet = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return realSet.apply(this, arguments) };
    try { await send('luckRefusedStore', 'luck', { op: 'l0', day }); await send('benchmarkRefusedStore', 'benchmarkAdd', { op: 'b0', name: 'Squat', value: '300 lb' }) } finally { Storage.prototype.setItem = realSet }
    await send('luckStale', 'luck', { op: 'l1', day: '2000-01-01' });
    const rolled = await send('luckFresh', 'luck', { op: 'l2', day });
    await send('luckRepeat', 'luck', { op: 'l3', day });
    await send('benchmarkAdded', 'benchmarkAdd', { op: 'b1', name: 'Squat', value: '300 lb' });
    await send('benchmarkRepeat', 'benchmarkAdd', { op: 'b1', name: 'Squat', value: '300 lb' });
    await send('benchmarkEmpty', 'benchmarkAdd', { op: 'b2', name: 'Plank', value: ' ' });
    await send('benchmarkTooLong', 'benchmarkAdd', { op: 'b3', name: 'x'.repeat(61), value: '1' });
    await send('benchmarkSet', 'benchmarkSet', { index: 0, name: '5K run time', value: '27:45' });
    await send('benchmarkSetSame', 'benchmarkSet', { index: 0, name: '5K run time', value: '27:45' });
    await send('benchmarkChanged', 'benchmarkSet', { index: 0, name: 'Something else', value: '1' });
    await send('benchmarkRemoved', 'benchmarkRemove', { index: 3, name: 'Squat' });
    await send('logUnknown', 'log', { attribute: 'nope' });
    await send('logPrefilled', 'log', { attribute: 'endurance' });
    return { opened, rolled: clean(rolled.snapshot), replies };
  });
  write('character-actions.json', out.opened);
  write('character-actions-rolled.json', out.rolled);
  write('character-replies.json', out.replies);
  await b.close(); site.close();
})();
