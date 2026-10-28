/* Calendar import: events from the phone's calendar become Life Director blocks (native plugin and Life Director stubbed).
   Run: NODE_PATH=$(npm root -g) node tests/calsync.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', fs.readdirSync(path.join(__dirname, '..', 'dist')).filter(f => /^Life_Max_RPG_v\d+\.html$/.test(f)).sort((a, b) => +a.match(/v(\d+)/)[1] - +b.match(/v(\d+)/)[1]).pop()));
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 300) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] }); const p = await (await b.newContext({ viewport: { width: 393, height: 844 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p.goto('file://' + file); await p.waitForTimeout(1200);
  const H = 3600e3;
  /* the filter */
  const k = await p.evaluate(() => { const now = Date.now(), f = e => LifeMaxCalendar.keep(Object.assign({ title: 'X', start: now + 3600e3, end: now + 7200e3, allDay: false, declined: false }, e), now); return { good: f({}), allDay: f({ allDay: true }), declined: f({ declined: true }), untitled: f({ title: '  ' }), past: f({ start: now - 7200e3, end: now - 3600e3 }), long: f({ end: now + 20 * 3600e3 }), zero: f({ end: now + 3600e3 }), runningNow: f({ start: now - 1800e3 }) } });
  ok('timed future events are kept; all-day, declined, untitled, past, 12h+ and zero-length are not', k.good && k.runningNow && !k.allDay && !k.declined && !k.untitled && !k.past && !k.long && !k.zero, k);
  ok('the id is stable for an event and changes when it moves', await p.evaluate(() => { const a = { id: 7, start: 1700000000000 }; return LifeMaxCalendar.id(a) === LifeMaxCalendar.id({ id: 7, start: 1700000000000 }) && LifeMaxCalendar.id(a) !== LifeMaxCalendar.id({ id: 7, start: 1700000600000 }) }));
  /* stub the native plugin and Life Director */
  await p.evaluate(() => {
    window.__cal = []; window.__ld = {}; window.__rm = [];
    lmaNative = () => true;
    lmaPlugin = n => n === 'CalendarSync' ? { status: async () => ({ granted: true }), request: async () => ({ granted: true }), events: async () => ({ events: __cal }) } : null;
    lmcApi = () => ({ ready: () => true, add: c => { __ld[c.id] = c; return { ok: true, id: c.id } }, remove: id => { delete __ld[id]; __rm.push(id); return true } });
    localStorage.removeItem('lifemax_calsync_v1'); localStorage.setItem('lifemax_calsync_v1', JSON.stringify({ on: true, ids: {} }));
  });
  const now = Date.now();
  await p.evaluate(n => { __cal = [{ id: 1, title: 'Dentist', start: n + 5 * 3600e3, end: n + 6 * 3600e3, allDay: false, declined: false }, { id: 2, title: 'Birthday', start: n + 3600e3, end: n + 86400e3, allDay: true, declined: false }, { id: 3, title: 'Gym', start: n + 8 * 3600e3, end: n + 9 * 3600e3, allDay: false, declined: false }] }, now);
  let r = await p.evaluate(() => lmcSync(false));
  ok('two timed events were added, the all-day one was not', r.added === 2 && await p.evaluate(() => Object.values(__ld).map(c => c.title).sort().join()) === 'Dentist,Gym', r);
  ok('epochs are in seconds and match the event', await p.evaluate(n => { const c = Object.values(__ld).find(c => c.title === 'Dentist'); return c.startEpoch === Math.floor((n + 5 * 3600e3) / 1000) && c.endEpoch - c.startEpoch === 3600 }, now));
  r = await p.evaluate(() => lmcSync(false)); ok('a second check adds nothing new', r.added === 0 && await p.evaluate(() => Object.keys(__ld).length) === 2, r);
  /* moved and deleted */
  await p.evaluate(n => { __cal = [{ id: 1, title: 'Dentist', start: n + 7 * 3600e3, end: n + 8 * 3600e3, allDay: false, declined: false }] }, now);
  r = await p.evaluate(() => lmcSync(false));
  ok('a moved event is re-added at the new time and the old block removed', r.added === 1 && r.removed === 2 || (r.added === 1 && r.removed >= 1), r);
  ok('a deleted event (Gym) is taken out', await p.evaluate(() => Object.values(__ld).every(c => c.title !== 'Gym') && Object.values(__ld).filter(c => c.title === 'Dentist').length === 1));
  /* switched off: nothing runs */
  await p.evaluate(() => { localStorage.setItem('lifemax_calsync_v1', JSON.stringify({ on: false, ids: {} })); __cal = [{ id: 9, title: 'Ghost', start: Date.now() + 3600e3, end: Date.now() + 7200e3 }] });
  r = await p.evaluate(() => lmcSync(false)); ok('with the switch off nothing is imported', r === null && await p.evaluate(() => Object.values(__ld).every(c => c.title !== 'Ghost')));
  /* Life Director not set up */
  await p.evaluate(() => { localStorage.setItem('lifemax_calsync_v1', JSON.stringify({ on: true, ids: {} })); lmcApi = () => ({ ready: () => false }) });
  r = await p.evaluate(() => lmcSync(false)); ok('before Life Director is set up nothing is lost and a message explains', r.added === 0 && /Set up Life Director/.test(await p.evaluate(() => lmcLoad().msg)));
  ok('the settings card renders', await p.evaluate(() => { lmcRender(); return /Import from my calendar/.test(document.getElementById('lmaCalBody').textContent) }));
  ok('the card sits in the Phone & sync group', await p.evaluate(() => SET_CATS.find(c => c.id === 'phone').cards.indexOf('Calendar import') >= 0));
  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
