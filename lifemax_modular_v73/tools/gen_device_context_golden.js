/* Golden cases for the native context adapters (android-wrapper/native/src/main/java/com/lifemax/rpg/core/adapters), recorded from the
   real page, so the adapters are accepted only when they hand the pure economy and quest code the inputs the page computes. Regenerate when
   the page's clock, day keys, Daily Hand verification, luck or rest-day code changes on purpose:
     NODE_PATH=$(npm root -g) node tools/gen_device_context_golden.js [built-page.html]
   Output: tests/fixtures/device-context-golden.json, one case per line. tests/device-context-golden.test.js records every case again in
   memory and fails when the page and the file disagree, so the file cannot go stale. The cases are defined in tools/device_context_cases.js.

   A case is {id, family, rule, input, output}:
     family  clock | clockmove | zonemove | exotic | facts | factsodd
     input   {now, zone, state?, calls}. now (ISO) is the paused clock, zone the device time zone (set through the browser's own time zone
             override, so a zonemove case can change it between calls). state (facts families) holds the save fields the Daily Hand and the
             rest-day rule read (IN below), after the case's {D<n>} (local day n days from today) and {I<n> HH:MM:SS} (that local wall time as
             an ISO instant) placeholders are resolved in the page. calls run in order: {at?, zone?, probe | facts}.
     probe   {dates}: records what the page derives from the clock and from stored record dates: today (getToday), weekday (getDay),
             weekStart (the weekly quests' period), since6 (the day six days back, as every "last 7 days" window computes it), dayNum
             (dayNum(today)), iso (toISOString), now, and days (activityDate({date: v}) for every v of dates; "<absent>" is a record without
             a date). {T<+-n>h} in dates is the ISO text n hours from the call's now, {M<+-n>h} the same instant in milliseconds.
     facts   {verify, rest, plan}: what dhVerifyMet answers for each check text (verify) on the page's today, luckToday()'s score (a
             number, else null), ecoRestDay for each day of rest, and for each day of plan dayDone(day) and whether planDay(day) is a rest day.
     output  {calls: [...]}, one object per call, the probe's or the facts' answers.

   What is held still: the clock is Playwright's, paused; nothing else of the page runs (save() is not called). */
'use strict';
const fs = require('fs'), path = require('path');
const { defineCases } = require('./device_context_cases.js');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'device-context-golden.json');
const BOOT = '2026-10-28T06:00:00.000Z';
/* the save fields a facts case sets (everything else is as freshState() has it, minus these) */
const IN = ['waterLog', 'mealsMeta', 'pet', 'plan', 'luck', 'restDays', 'activities'];

/* ---------- the page side: runs in the built page, against its own functions ---------- */
function pageRunner({ IN }) {
  const clone = v => (v === undefined ? undefined : JSON.parse(JSON.stringify(v)));
  const day = n => { const d = new Date(); d.setDate(d.getDate() + n); return d.toLocaleDateString('en-CA') };
  const instant = (n, hms) => { const d = new Date(); d.setDate(d.getDate() + n); const [h, m, s] = hms.split(':').map(Number); d.setHours(h, m, s, 0); return d.toISOString() };
  const monday = () => { const d = new Date(); d.setDate(d.getDate() - ((d.getDay() + 6) % 7)); return d.toLocaleDateString('en-CA') };
  const resolve = v => typeof v === 'string'
    ? v.replace(/\{W\}/g, monday).replace(/\{D([+-]?\d+)\}/g, (_, n) => day(+n)).replace(/\{I([+-]?\d+) (\d\d:\d\d:\d\d)\}/g, (_, n, t) => instant(+n, t))
    : Array.isArray(v) ? v.map(resolve) : v && typeof v === 'object' ? Object.fromEntries(Object.entries(v).map(([k, x]) => [resolve(k), resolve(x)])) : v;
  const hours = n => Date.now() + n * 3600000;
  const dateValue = v => {
    if (typeof v !== 'string') return v;
    let m = /^\{T([+-]\d+)h\}$/.exec(v); if (m) return new Date(hours(+m[1])).toISOString();
    m = /^\{M([+-]\d+)h\}$/.exec(v); if (m) return hours(+m[1]);
    return v;
  };
  const planKind = d => { try { const p = planDay(d); return !!(p && p.kind === 'rest') } catch (e) { return false } };
  const done = d => { try { return !!dayDone(d) } catch (e) { return false } };
  const rest = d => { try { return !!ecoRestDay(d) } catch (e) { return null } };
  window.__dc = {
    setup(given, tick) {
      state = Object.assign(freshState(), { xpSchemaV2: true });
      for (const k of IN) delete state[k];
      Object.assign(state, resolve(clone(given || {})));
      for (const t of tick || []) { // tick the sets of a plan day: every required set, or all but one
        const d = resolve(t.day), p = planDay(d);
        if (!p || !p.items) continue;
        const s = {}; let skipped = false;
        for (const i of p.items) { if (i.opt) continue; s[i.k] = Array(i.n).fill(true); if (t.mode === 'allButOne' && !skipped) { s[i.k][i.n - 1] = false; skipped = true } }
        state.plan.checks[d] = { s };
      }
      const s = {};
      for (const k of IN) if (state[k] !== undefined) s[k] = clone(state[k]);
      return s;
    },
    resolve: v => resolve(v),
    probe(c) {
      const dates = (c.dates || []).map(dateValue), d = new Date(), since = new Date(); since.setDate(since.getDate() - 6);
      const days = dates.map(v => { try { return activityDate({ date: v === '<absent>' ? undefined : v }) } catch (e) { return 'threw' } });
      return {
        call: { dates },
        out: { today: getToday(), weekday: d.getDay(), weekStart: weekStart(), since6: since.toLocaleDateString('en-CA'), dayNum: dayNum(getToday()), iso: d.toISOString(), now: Date.now(), days },
      };
    },
    facts(c) {
      const args = resolve(clone(c)), L = luckToday(), t = getToday(), verify = {}, restOut = {}, planOut = {};
      for (const v of args.verify || []) { try { verify[v] = dhVerifyMet({ verify: v }, t) } catch (e) { verify[v] = 'threw' } }
      for (const d of args.rest || []) restOut[d] = rest(d);
      for (const d of args.plan || []) planOut[d] = { done: done(d), rest: planKind(d) };
      return { call: args, out: { today: t, verify, luck: L && typeof L.score === 'number' ? L.score : null, rest: restOut, plan: planOut } };
    },
  };
}

/* ---------- recording ---------- */
async function openPage(browser, file) {
  const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  p.cdp = await ctx.newCDPSession(p); // the time zone moves through the browser's own override, so one page serves every zone
  await p.cdp.send('Emulation.setTimezoneOverride', { timezoneId: 'UTC' });
  await p.clock.install({ time: new Date(BOOT) });
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));");
  await p.goto('file://' + file);
  await p.waitForFunction(() => typeof dhVerifyMet === 'function' && typeof ecoRestDay === 'function' && typeof luckToday === 'function' && typeof dayDone === 'function', null, { timeout: 120000 });
  await p.clock.pauseAt(new Date('2026-10-28T12:00:00.000Z')); // the boot's own timers have run; from here time moves only when a case moves it
  await p.evaluate(pageRunner, { IN });
  p.zone = 'UTC';
  return p;
}

async function setZone(p, zone) {
  if (p.zone === zone) return;
  await p.cdp.send('Emulation.setTimezoneOverride', { timezoneId: zone });
  p.zone = zone;
}

async function record(p, c) {
  await setZone(p, c.zone);
  await p.clock.setSystemTime(new Date(c.now));
  const state = c.family.startsWith('facts') ? await p.evaluate(x => window.__dc.setup(x.state, x.tick), { state: c.state, tick: c.tick }) : undefined;
  const calls = [], outs = [];
  for (const x of c.calls) {
    if (x.zone) await setZone(p, x.zone);
    await p.clock.setSystemTime(new Date(x.at || c.now));
    const r = await p.evaluate(x => (x.probe ? window.__dc.probe(x.probe) : window.__dc.facts(x.facts)), x);
    calls.push({ ...(x.at ? { at: x.at } : {}), ...(x.zone ? { zone: x.zone } : {}), [x.probe ? 'probe' : 'facts']: r.call }); outs.push(r.out);
  }
  const input = { now: c.now, zone: c.zone, ...(state ? { state } : {}), calls };
  return { id: c.id, family: c.family, rule: c.rule, input, output: { calls: outs } };
}

/* Records every case in the built page `file`; resolves to the list of cases. */
async function generate(file, log = () => { }) {
  const { chromium } = require('playwright');
  const cases = defineCases(), browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const p = await openPage(browser, file), out = [];
    for (const c of cases) {
      out.push(JSON.parse(JSON.stringify(await record(p, c)))); // as the file will hold it
      if (out.length % 50 === 0) log(out.length + ' of ' + cases.length + ' cases recorded');
    }
    if (p.errors.length) throw new Error('the page threw while recording: ' + p.errors.slice(0, 3).join(' | '));
    return out;
  } finally { await browser.close() }
}

/* one case per line, so a change to the page shows up in a diff as the cases it changed */
const serialize = cases => '[\n' + cases.map(c => JSON.stringify(c)).join(',\n') + '\n]\n';

module.exports = { generate, defineCases, serialize, FILE, IN };

if (require.main === module) {
  const file = require('../tests/current-page.js')(process.argv[2]);
  generate(file, m => console.log(m)).then(cases => {
    fs.writeFileSync(FILE, serialize(cases));
    const by = {};
    for (const c of cases) by[c.family] = (by[c.family] || 0) + 1;
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ': ' + cases.length + ' cases ' + JSON.stringify(by));
  }).catch(e => { console.error(e); process.exit(1) });
}
