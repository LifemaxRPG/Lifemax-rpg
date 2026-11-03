/* Native Life Director, taking a session off a day (director.remove.v1), web half. A native screen lets the player hold a session in
   Today's running order and drop it on a trash target, or use TalkBack's "Remove from today"; both are the page's own Skip, with
   the take-back this adds. Checked, through the real page on the lived-in day of tests/fixtures/director-day.js:
   - capability: rows carry `remove` only for a screen that says director.remove.v1 (and director.sheets.v1); unskip is refused
     without it; a planned session says ok, one done, part done, off the plan or on a running timer says no, with the page's reason;
   - removing: the skip is the engine's (the session leaves the plan, its history entry is SKIPPED_VOLUNTARY), that occurrence only
     (no rule, goal or commitment changes, no XP is paid), ok only once saved; asked twice it answers "Already skipped."; a running
     timer's session is refused;
   - Undo: the plan and the ledger come back exactly as they were; the same operation again changes nothing; once anything else has
     changed the plan, or a skip is not the last, it is refused and changes nothing; a failed save answers "Not saved" and the same
     request lands it later.
   A fake NativeLifeDirector plugin stands in for Android. Run: NODE_PATH=$(npm root -g) node tests/native-director-remove.test.js [built html] */
const { chromium } = require('playwright');
const file = require('./current-page')(process.argv[2]);
const directorDay = require('./fixtures/director-day.js');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

const FAKES = caps => `
  window.__calls = [];
  window.__since = n => window.__calls.slice(n).filter(c => c[0] === 'director');
  const mk = (name, caps) => { const ls = {}; return {
    __emit: (e, d) => (ls[e] || []).map(f => f(d)),
    addListener: async (e, f) => { (ls[e] = ls[e] || []).push(f); return { remove() {} } },
    open: async a => { window.__calls.push([name, 'open', a]) },
    update: async a => { window.__calls.push([name, 'update', a]) },
    close: async a => { window.__calls.push([name, 'close', a]); (ls.closed || []).forEach(f => f({ session: a && a.session })) },
    capabilities: async () => ({ liveWeb: true, version: 1, authoritativeData: true }),
    handshake: async () => { window.__calls.push([name, 'handshake']); return { protocol: 1, capabilities: caps } } } };
  window.Capacitor = { isNativePlatform: () => true, isPluginAvailable: n => n === 'NativeLifeDirector' || n === 'NativeOverview', getPlatform: () => 'android',
    Plugins: { NativeLifeDirector: mk('director', ${JSON.stringify(caps)}), NativeOverview: mk('overview', []) }, registerPlugin: () => ({}), convertFileSrc: u => u };`;

async function start(b, caps) {
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
  p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await directorDay.seed(p, { planned: true });
  await p.addInitScript(FAKES(caps));
  await p.goto('file://' + file, { timeout: 120000 });
  await p.evaluate(() => { lmaThumbWarm = async () => { }; showLevelUp = function () { }; lmaLoad().nativeOverview = false; lmaSave() });
  await p.evaluate(() => setNativeDirectorEnabled(true));
  await directorDay.play(p);
  return p;
}
async function settle(p, ms = 500) { for (let t = 0; t < ms; t += 50) { await p.clock.runFor(50); if (t % 200 === 0) await p.waitForTimeout(5) } await p.waitForTimeout(10) }
async function go(p, v, ms) { await p.evaluate(v => lmaNavTo(v), v); await p.waitForFunction(v => lmaView() === v, v); await settle(p, ms) }
const mark = p => p.evaluate(() => window.__calls.length);
async function command(p, command, payload) {
  const n = await mark(p);
  await p.evaluate(([c, pl]) => Capacitor.Plugins.NativeLifeDirector.__emit('command', { command: c, payload: pl, requestId: 'r-' + c }), [command, payload]);
  await settle(p, 400);
  return p.evaluate(n => { const u = __since(n).filter(c => c[1] === 'update' && c[2].reply); return { reply: u.length ? u[0][2].reply : null, snapshot: u.length ? u[0][2].snapshot : null } }, n);
}
const stored = p => p.evaluate(() => JSON.parse(localStorage.getItem('lifemax_director_state_v1')));
/* the rows of a day the way a screen asks for them, with or without the capability */
const rowsOf = (p, day, removal) => p.evaluate(([d, r]) => { const t = LifeDirector.native.today(d, { removal: r }); return t.rows.ahead.concat(t.rows.earlier ? t.rows.earlier.rows : []).filter(x => x.act === 'inst') }, [day, removal]);

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const CAPS = ['director.today.v2', 'director.week.v1', 'director.goals.v1', 'director.sheets.v1', 'director.remove.v1'];

  /* 1. what a screen is told */
  {
    const p = await start(b, ['director.today.v2', 'director.week.v1', 'director.goals.v1', 'director.sheets.v1']);
    await go(p, 'life-director', 1500);
    let r = await command(p, 'day', { index: 0 });
    ok('without director.remove.v1 no row says whether it can be removed', r.snapshot.today.rows.ahead.every(x => !('remove' in x)) && r.snapshot.today.rows.earlier.rows.every(x => !('remove' in x)), r.snapshot.today.rows.ahead[0]);
    const tomorrow = await rowsOf(p, 1, true);
    r = await command(p, 'today', { name: 'unskip', id: tomorrow[0].id, op: 'op-x' });
    ok('without director.remove.v1 unskip is refused', r.reply && r.reply.ok === false && /cannot do that/.test(r.reply.message), r.reply);
    ok('no script errors without the capability', p.errors.length === 0, p.errors);
    await p.context().close();
  }
  {
    const p = await start(b, ['director.today.v2', 'director.week.v1', 'director.goals.v1', 'director.remove.v1']);
    await go(p, 'life-director', 1500);
    const r = await command(p, 'day', { index: 0 });
    ok('director.remove.v1 without director.sheets.v1 (nothing to run the removal through) marks nothing', r.snapshot.today.rows.ahead.every(x => !('remove' in x)), r.snapshot.today.rows.ahead[0]);
    await p.context().close();
  }

  /* 2-4. the rows, removing and Undo, on a screen that says it can */
  {
    const p = await start(b, CAPS);
    await go(p, 'life-director', 1500);
    let r = await command(p, 'day', { index: 0 });
    const today = r.snapshot.today.rows, sessions = today.ahead.concat(today.earlier ? today.earlier.rows : []).filter(x => x.act === 'inst');
    const nonSessions = today.ahead.filter(x => x.act !== 'inst');
    ok('every session row says whether it can be removed; other blocks say nothing', sessions.length > 1 && sessions.every(x => x.remove && typeof x.remove.ok === 'boolean' && typeof x.remove.reason === 'string') && nonSessions.every(x => !('remove' in x)), sessions);
    const done = sessions.find(x => x.state === 'done'), planned = sessions.find(x => x.state === 'plan' && x.remove.ok);
    ok('a session already done cannot, with the page\'s reason', done && done.remove.ok === false && /already done/.test(done.remove.reason), done);
    ok('a planned session can, and has no reason', planned && planned.remove.ok === true && planned.remove.reason === '', planned);

    const tomorrow = await rowsOf(p, 1, true), [a, b2, c] = tomorrow.map(x => x.id);
    const start0 = await stored(p);
    const xp0 = await p.evaluate(() => state.activities.length);
    r = await command(p, 'today', { name: 'skip', id: a, op: 'op-skip-1' });
    const skipReply = r.reply, after = await stored(p);
    const gone = !after.schedule.scheduledInstances.some(i => i.instanceId === a);
    const entry = after.schedule.historyInstances.find(h => h.instanceId === a);
    ok('removing: the session leaves the plan as the engine\'s skip (SKIPPED_VOLUNTARY), saved, with the page\'s words as the way to take it back', skipReply.ok && skipReply.changed === true && gone && entry && entry.lifecycleState === 'SKIPPED_VOLUNTARY' && /^Skipped/.test(skipReply.undo), { skipReply, entry: entry && entry.lifecycleState });
    ok('that occurrence only: goals, rules and commitments are as they were, and no XP was paid', same(after.goals, start0.goals) && same(after.availability.rules, start0.availability.rules) && same(after.fixedCommitments, start0.fixedCommitments) && await p.evaluate(() => state.activities.length) === xp0, { goals: same(after.goals, start0.goals) });
    ok('the page says so in its own words (the notice)', r.snapshot.notice && r.snapshot.notice.text === skipReply.undo, r.snapshot.notice);
    r = await command(p, 'today', { name: 'skip', id: a, op: 'op-skip-1' });
    ok('removing again (a repeat after a lost reply): ok, changed false, "Already skipped.", and the take-back is still on offer', r.reply.ok && r.reply.changed === false && r.reply.message === 'Already skipped.' && r.reply.undo === skipReply.undo, r.reply);

    r = await command(p, 'today', { name: 'unskip', id: a, op: 'op-undo-1' });
    const back = await stored(p);
    ok('Undo: the plan and the ledger are exactly as they were before the skip', r.reply.ok && r.reply.changed === true && same(back.schedule, start0.schedule) && same(back.ledger, start0.ledger), { reply: r.reply, plan: same(back.schedule, start0.schedule), ledger: same(back.ledger, start0.ledger) });
    ok('Undo says so in the page\'s words', r.snapshot.notice && r.snapshot.notice.text === 'Back on the plan', r.snapshot.notice);
    r = await command(p, 'today', { name: 'unskip', id: a, op: 'op-undo-1' });
    ok('Undo again with the same operation id changes nothing ("Already back on the plan.")', r.reply.ok && r.reply.changed === false && r.reply.message === 'Already back on the plan.' && same((await stored(p)).schedule, start0.schedule), r.reply);
    r = await command(p, 'today', { name: 'unskip', id: a, op: 'op-undo-2' });
    ok('Undo of a session that is on the plan already is recorded, not an error', r.reply.ok && r.reply.changed === false, r.reply);
    r = await command(p, 'today', { name: 'unskip', id: 'nowhere', op: 'op-undo-3' });
    ok('Undo of something never removed is refused', r.reply.ok === false && /no longer be taken back/.test(r.reply.message), r.reply);

    // too late: the plan changed after the skip
    r = await command(p, 'today', { name: 'skip', id: b2, op: 'op-skip-2' });
    const skipped2 = await stored(p);
    await command(p, 'today', { name: 'pin', id: c, on: true });
    const pinned = await stored(p);
    r = await command(p, 'today', { name: 'unskip', id: b2, op: 'op-undo-4' });
    ok('Undo after anything else changed the plan is refused, and changes nothing', r.reply.ok === false && /plan has changed/.test(r.reply.message) && same((await stored(p)).schedule, pinned.schedule) && !same(pinned.schedule, skipped2.schedule), r.reply);

    // a newer skip replaces the one that could be taken back
    r = await command(p, 'today', { name: 'skip', id: c, op: 'op-skip-3' });
    r = await command(p, 'today', { name: 'unskip', id: b2, op: 'op-undo-5' });
    ok('only the last skip can be taken back', r.reply.ok === false, r.reply);
    r = await command(p, 'today', { name: 'unskip', id: c, op: 'op-undo-6' });
    ok('and that one still can', r.reply.ok && r.reply.changed === true, r.reply);

    // two minutes later it is too late
    const t2 = (await rowsOf(p, 1, true)).find(x => x.id !== c);
    await command(p, 'today', { name: 'skip', id: t2.id, op: 'op-skip-4' });
    await settle(p, 125000);
    r = await command(p, 'today', { name: 'unskip', id: t2.id, op: 'op-undo-7' });
    ok('two minutes on it is too late', r.reply.ok === false, r.reply);
    ok('no script errors removing and taking back', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* 5. a running timer, a session done, a save that fails */
  {
    const p = await start(b, CAPS);
    await go(p, 'life-director', 1500);
    const next = await p.evaluate(() => { const a = LifeDirector.native.today(0).upNext; return a && (a.actions.find(x => x.act === 'focusStart') || {}).id });
    let r = await command(p, 'today', { name: 'timer', act: 'start', id: next });
    const row = (await rowsOf(p, 0, true)).find(x => x.id === next);
    ok('a session on a running timer says it cannot be removed, and why', row && row.remove.ok === false && /timer is running/.test(row.remove.reason), row);
    const before = await stored(p);
    r = await command(p, 'today', { name: 'skip', id: next, op: 'op-run' });
    ok('removing it is refused with that reason and changes nothing', r.reply.ok === false && /timer is running/.test(r.reply.message) && same((await stored(p)).schedule, before.schedule), r.reply);
    const doneRow = (await rowsOf(p, 0, true)).find(x => x.state === 'done');
    r = await command(p, 'today', { name: 'skip', id: doneRow.id, op: 'op-done' });
    ok('removing a session already done is refused', r.reply.ok === false, r.reply);

    // a save the store refuses: the change is in memory, the answer says not saved, and the same request lands it
    const target = (await rowsOf(p, 1, true))[0].id;
    await p.evaluate(() => { const real = Storage.prototype.setItem; window.__realSet = real; Storage.prototype.setItem = function (k) { if (k === 'lifemax_director_state_v1') throw new DOMException('The quota has been exceeded.', 'QuotaExceededError'); return real.apply(this, arguments) } });
    r = await command(p, 'today', { name: 'skip', id: target, op: 'op-fail' });
    ok('a save that fails: ok false with the page\'s "Not saved" words', r.reply.ok === false && /^Not saved/.test(r.reply.message), r.reply);
    await p.evaluate(() => { Storage.prototype.setItem = window.__realSet });
    r = await command(p, 'today', { name: 'skip', id: target, op: 'op-fail' });
    const kept = await stored(p);
    ok('the same request once the store works lands it: ok, saved, and the take-back still on offer', r.reply.ok && r.reply.message === 'Already skipped.' && /^Skipped/.test(r.reply.undo) && !kept.schedule.scheduledInstances.some(i => i.instanceId === target) && kept.schedule.historyInstances.some(h => h.instanceId === target), r.reply);
    ok('no script errors with refusals and a failed save', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  await b.close();
  console.log('\n' + pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1) });
