/* Native Life Director, web half: the Today projection a native screen draws (checked against what the page itself draws),
   when the screen may cover the page, the taps it hands back to Life Director's own handlers, the day command, and (phase 2)
   the dial's handles: what a drag shows, a drop's confirmation and Change it, each against the page's own dial and sheet, a
   repeated Change it, and the build the screen plays against the one the page plays, with a refused save and reduced motion.
   A fake NativeLifeDirector plugin stands in for Android; the page runs on Playwright's clock through the lived-in day of
   tests/fixtures/director-day.js. Run: NODE_PATH=$(npm root -g) node tests/native-director.test.js */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path'); const os = require('os');
const { execFileSync } = require('child_process');
const directorDay = require('./fixtures/director-day.js');
const root = path.resolve(__dirname, '..'), out = path.join(os.tmpdir(), 'lm_ndir_' + process.pid + '.html');
execFileSync('python3', [path.join(root, 'build.py')], { cwd: root, stdio: 'ignore' });
execFileSync('python3', [path.join(root, 'forge_features.py'), path.join(root, 'dist', 'Life_Max_RPG_v73_modular_build.html'), out], { cwd: root, stdio: 'ignore' });
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };

/* The fake plugins record every call; __emit raises one of the screen's events; __since(n) lists the director calls after the nth. */
const FAKES = `
  window.__calls = [];
  window.__since = n => window.__calls.slice(n).filter(c => c[0] === 'director');
  const mk = (name, caps) => { const ls = {}; return {
    __emit: (e, d) => (ls[e] || []).map(f => f(d)),
    addListener: async (e, f) => { (ls[e] = ls[e] || []).push(f); return { remove() {} } },
    open: async a => { window.__calls.push([name, 'open', a]) },
    update: async a => { window.__calls.push([name, 'update', a]) },
    close: async a => { window.__calls.push([name, 'close', a]); (ls.closed || []).forEach(f => f({ session: a && a.session })) },
    capabilities: async () => ({ liveWeb: true, version: 1, authoritativeData: true }),
    handshake: async () => ({ protocol: 1, capabilities: caps }) } };
  window.Capacitor = { isNativePlatform: () => true, isPluginAvailable: n => n === 'NativeLifeDirector' || n === 'NativeOverview', getPlatform: () => 'android',
    Plugins: { NativeLifeDirector: mk('director', ['director.today.v2']), NativeOverview: mk('overview', []) }, registerPlugin: () => ({}), convertFileSrc: u => u };`;

async function start(b, planned) {
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
  p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await directorDay.seed(p, { planned });
  await p.addInitScript(FAKES);
  await p.goto('file://' + out, { timeout: 120000 }); // a whole app loading, on a machine that may be busy
  // the one-off walk through every tab is not under test here, and a level-up only where asked for (__levelUp)
  await p.evaluate(() => { lmaThumbWarm = async () => { }; window.__levelUp = showLevelUp; showLevelUp = function () { }; const S = lmaLoad(); S.nativeDirector = true; S.nativeOverview = false; lmaSave() });
  if (planned) await directorDay.play(p);
  return p;
}
/* the page's clock in small steps, with real time in between for the promise work of a bar capture */
async function settle(p, ms = 500) { for (let t = 0; t < ms; t += 50) { await p.clock.runFor(50); if (t % 200 === 0) await p.waitForTimeout(5) } await p.waitForTimeout(10) }
/* go to a view, then settle: a tab change runs in a view transition, whose page change waits for a real frame, not the page's clock */
async function go(p, v, ms) { await p.evaluate(v => lmaNavTo(v), v); await p.waitForFunction(v => lmaView() === v, v); await settle(p, ms) }
const mark = p => p.evaluate(() => window.__calls.length);

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });

  const fresh = await start(b, false);
  await go(fresh, 'life-director', 1500);
  const f = await fresh.evaluate(() => ({ tab: LifeDirector.native.frame().tab, shows: LifeDirector.native.showing(), opens: __since(0).filter(c => c[1] === 'open').length, today: LifeDirector.native.today(0) }));
  ok('before setup the page keeps its own screen: set-up and its guide show and the native screen never opens', f.tab === 'setup' && f.shows === 'guide' && f.opens === 0 && f.today === null, f);
  ok('no script errors before setup', fresh.errors.length === 0, fresh.errors);
  await fresh.context().close(); // one page at a time: each holds a whole app

  const p = await start(b, true);
  await go(p, 'life-director', 1500);
  const m = await p.evaluate(() => {
    const open = __since(0).filter(c => c[1] === 'open'), s = open.length && open[0][2].snapshot, t = s && s.today;
    if (open.length !== 1 || !t) return { opened: open.length, shows: LifeDirector.native.showing() };
    const ld = document.querySelector('#view-life-director .ld-root'), q = sel => ld.querySelector(sel), all = sel => [...ld.querySelectorAll(sel)];
    const text = sel => all(sel).map(e => e.textContent.trim()), same = (a, b) => JSON.stringify(a) === JSON.stringify(b), opt = (e, sel) => (e.querySelector(sel) || { textContent: '' }).textContent;
    const polar = (r, a) => [Math.cos((a - 90) * Math.PI / 180), Math.sin((a - 90) * Math.PI / 180)].map(v => (180 + r * v).toFixed(2)).join(' ');
    const starts = sel => all(sel).map(e => e.getAttribute('d').slice(1).split('A')[0]);
    const arcs = style => t.dial.arcs.filter(a => a.style === style), big = t.core.big;
    const appt = arcs('appt')[0], work = arcs('band').find(a => a.kind === 'work'), meal = arcs('meal')[0];
    return {
      opened: 1, now: t.now, hand: t.dial.hand, next: t.upNext.actions[0].id, nextTitle: t.upNext.title, meal: meal.id,
      checks: {
        frame: s.protocol === 1 && s.tab === 'today' && s.banner === null && s.notice === null && s.header.line === q('.ld-id small').textContent && s.header.dot === q('.ld-dot').dataset.s && s.clock.time === q('[data-clock]').textContent,
        tabs: same(s.tabs.map(x => [x.id, x.label]), all('.ld-tab').map(e => [e.dataset.tab, e.textContent.trim()])),
        strip: t.isToday && t.dayIndex === 0 && same(t.strip.map(d => [d.label, String(d.date), d.dots.length]), all('.ld-chipday').map(e => [e.querySelector('span').textContent, e.querySelector('b').textContent, e.querySelectorAll('em i').length])),
        rings: arcs('band').length === all('.ld-outer .ld-o').length && arcs('session').length === all('.ld-ses').length && arcs('meal').length === all('.ld-meal:not(.ld-appt)').length && arcs('appt').length === all('.ld-appt').length && arcs('thin').length === all('.ld-rest').length,
        angles: same(arcs('band').map(a => polar(130, a.a0)), starts('.ld-outer .ld-o')) && same(arcs('session').map(a => polar(101, a.a0)), starts('.ld-ses .ld-sa')),
        sessions: same(arcs('session').map(a => [a.id, a.state, a.next]), all('.ld-ses').map(e => [e.dataset.id, ['plan', 'done', 'part', 'off'].find(k => e.classList.contains('ld-s-' + k)), e.classList.contains('ld-next')])),
        words: same(t.dial.words.map(w => w.text), text('.ld-words textPath')),
        hand: t.dial.hand.toFixed(2) === /rotate\(([\d.]+)/.exec(q('.ld-hand').getAttribute('transform'))[1] && q('.ld-cur-arc').classList.contains('ld-c-' + t.dial.ring.kind),
        core: t.core.kick === q('.ld-core-k').textContent && t.core.title === q('.ld-core-t').textContent && t.core.sub === q('.ld-core-s').textContent && t.core.next.label === q('.ld-core-nx span').textContent && t.core.next.title === q('.ld-core-nx b').textContent &&
          (big.h ? big.h + 'h' + (big.m ? ' ' + big.m + 'm' : '') : big.m + 'm') === q('.ld-core-big').textContent,
        mix: same(t.mix.map(x => [x.label, x.text]), all('.ld-mix-l li').map(e => [e.textContent.slice(0, -e.querySelector('b').textContent.length), e.querySelector('b').textContent])),
        upNext: t.upNext.cue === q('.ld-up-h').textContent && t.upNext.title === q('.ld-up h3').textContent && t.upNext.when === q('.ld-up-when').textContent && same(t.upNext.actions.map(a => a.label), text('.ld-up button')) && same(t.upNext.chips.map(c => c.text), text('.ld-up .ld-chip')),
        reminders: t.reminders.length > 0 && t.reminders.join(', ') === q('.ld-remind p').textContent,
        rows: same(t.rows.ahead.map(r => [r.time, r.length, r.title, r.stateLabel, r.sub]), all('ol.ld-ro:not(.ld-ro-past) > li').map(e => [opt(e, 'time b'), opt(e, 'time small'), opt(e, '.ld-ri-t > span'), opt(e, '.ld-state'), opt(e, '.ld-ri-sub')])),
        earlier: t.rows.earlier.summary === q('.ld-earlier summary span').textContent && t.rows.earlier.rows.length === all('.ld-ro-past > li').length,
        taps: appt.title === 'Dentist' && appt.tip === 'Dentist · 6:30 PM–7:15 PM' && !appt.act && work.tip === 'Work · 9:00 AM–5:00 PM. Drag its handle to change it.' && !work.act &&
          arcs('band').filter(a => a.kind === 'sleep').every(a => a.tip.endsWith('. Drag its handle to change it.')) &&
          meal.act === 'meal' && meal.id === q('.ld-meal:not(.ld-appt)').dataset.ms && t.rows.ahead.find(r => r.kind === 'meal').act === 'meal' && arcs('session').every(a => a.act === 'inst' && a.id),
      },
    };
  });
  ok('opening Life Director on Today opens the native screen once, with the Today projection', m.opened === 1 && !!m.checks, m);
  const said = { frame: 'the header, status dot and clock', tabs: 'the section tabs', strip: 'the day strip and its session dots', rings: 'what is on each ring', angles: 'the arc angles after the dial\'s gaps', sessions: 'each session\'s state and the next one', words: 'the words written on the rings', hand: 'the hand and the current-block ring', core: 'the centre', mix: 'the day mix', upNext: 'the up-next card', reminders: 'the all-day reminders', rows: 'the running order', earlier: 'the earlier-today summary', taps: 'what a tap does (page acts, meal ids, tips on fixed time, with the drag hint where the dial has a handle for it)' };
  for (const [k, v] of Object.entries((m && m.checks) || {})) ok('the projection matches the page: ' + said[k], v, k);

  let n = await mark(p);
  await p.clock.runFor(15200); await settle(p, 300);
  const live = await p.evaluate(n => { const u = __since(n).filter(c => c[1] === 'update').pop(); return u && { now: u[2].snapshot.today.now, hand: u[2].snapshot.today.dial.hand } }, n);
  ok('the 15-second refresh moves now and the hand on', !!live && live.now - m.now >= 15 && Math.abs(live.hand - m.hand - (live.now - m.now) / 240) < 1e-6, { live, now: m.now, hand: m.hand });

  const reply = (n, id) => p.evaluate(([n, id]) => { const u = __since(n).find(c => c[1] === 'update' && c[2].reply && c[2].reply.requestId === id); return u && { reply: u[2].reply, today: u[2].snapshot.today } }, [n, id]);
  const emit = (event, data) => p.evaluate(([e, d]) => { Capacitor.Plugins.NativeLifeDirector.__emit(e, d) }, [event, data]);
  n = await mark(p);
  await emit('command', { command: 'day', payload: { index: 1 }, requestId: 'd1' }); await settle(p, 300);
  const d1 = await reply(n, 'd1'), kept = await p.evaluate(n => ({ closes: __since(n).filter(c => c[1] === 'close').length, webDay: [...document.querySelectorAll('#view-life-director .ld-chipday')].findIndex(e => e.classList.contains('on')) }), n);
  ok('the day command shows tomorrow: not today, a date in the centre, no hand, up-next or earlier rows', !!d1 && d1.reply.ok === true && d1.today.dayIndex === 1 && !d1.today.isToday && d1.today.core.date && d1.today.dial.hand === null && d1.today.upNext === null && d1.today.rows.earlier === null, d1 && d1.reply);
  ok('choosing a day keeps the screen and leaves the page\'s own day alone', kept.closes === 0 && kept.webDay === 0, kept);
  n = await mark(p);
  for (const [id, index] of [['d2', 7], ['d3', -1], ['d4', 'x']]) await emit('command', { command: 'day', payload: { index }, requestId: id });
  await settle(p, 300);
  const bad = await Promise.all(['d2', 'd3', 'd4'].map(id => reply(n, id)));
  ok('a day that is not on the strip is refused and the day stays', bad.every(r => r && r.reply.ok === false && /not on the plan/.test(r.reply.message) && r.today.dayIndex === 1), bad.map(r => r && r.reply));

  const state = (n) => p.evaluate(n => {
    const calls = __since(n), open = calls.filter(c => c[1] === 'open').pop(), body = document.querySelector('#view-life-director .ld-sheet.open .ld-sheet-body');
    return { calls: calls.map(c => c[1]).filter(k => k !== 'update'), shows: LifeDirector.native.showing(), sheet: body ? body.textContent : '', today: open && open[2].snapshot.today, frame: open && { banner: open[2].snapshot.banner, notice: open[2].snapshot.notice } };
  }, n);
  const tap = async (action, id) => { const n = await mark(p); await emit('action', { action, id: id || '' }); await settle(p, 500); return state(n) };
  const click = async sel => { const n = await mark(p); await p.evaluate(sel => document.querySelector('#view-life-director ' + sel).dispatchEvent(new MouseEvent('click', { bubbles: true })), sel); await settle(p, 500); return state(n) };
  const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

  let r = await click('.ld-ses.ld-s-plan');
  ok('a sheet opened on the page closes the screen at once, without waiting for a hand-over', same(r.calls, ['close']) && r.shows === 'sheet', r.calls);
  r = await click('.ld-sheet-bg');
  ok('closing the sheet brings the screen back, on the day it showed', same(r.calls, ['open']) && r.today.dayIndex === 1, r.calls);
  n = await mark(p);
  await go(p, 'dashboard', 500); await go(p, 'life-director', 1500);
  r = await state(n);
  ok('coming back to Life Director shows today again', same(r.calls, ['close', 'open']) && r.today.dayIndex === 0, r.calls);

  r = await tap('inst', m.next);
  ok('a session (arc, row or Details) closes the screen and opens the page\'s session sheet', same(r.calls, ['close']) && r.shows === 'sheet' && r.sheet.includes(m.nextTitle) && r.sheet.includes('Start timer'), r);
  await click('.ld-sheet-bg');
  r = await tap('meal', m.meal);
  ok('a meal (arc, row or View meal) opens the page\'s meal preview', same(r.calls, ['close']) && r.shows === 'sheet' && r.sheet.includes('Open meal menu'), r);
  await click('.ld-sheet-bg');

  r = await tap('focusStart', m.next);
  const focus = await p.evaluate(() => LifeDirectorApp._debug().ST.ui.focus);
  ok('Start runs the page\'s timer and shows its timer sheet', same(r.calls, ['close']) && !!focus && focus.id === m.next && r.sheet.includes('Finish and log'), { r, focus });
  r = await click('.ld-sheet-bg');
  ok('with a timer running the screen comes back with it in the centre and the up-next card', same(r.calls, ['open']) && r.today.core.kick === 'Timer running' && r.today.core.timer.act === 'focusOpen' && r.today.core.timer.startedAt === focus.start &&
    r.today.upNext.kind === 'go' && r.today.upNext.timer.startedAt === focus.start && same(r.today.upNext.actions.map(a => a.act), ['focusOpen', 'focusFinish']), r.today && { core: r.today.core, upNext: r.today.upNext });
  r = await tap('focusOpen');
  ok('the running timer opens the page\'s timer sheet', same(r.calls, ['close']) && r.sheet.includes('Stop without logging'), r.calls);
  r = await click('[data-act=focusCancel]');
  ok('stopping the timer on the page brings the screen back without it', same(r.calls, ['open']) && r.today.core.timer === null && r.today.upNext.kind === 'next', r.calls);

  n = await mark(p);
  await emit('action', { action: 'doneQuick', id: m.next }); await settle(p, 500);
  const xp = () => p.evaluate(t => ({ logged: state.activities.some(a => a.name === t && a.ldEventId), flying: !!document.querySelector('.fx-xp') }), m.nextTitle);
  const during = await xp(), held = await state(n);
  ok('Done completes the session through the page (XP logged), and its XP celebration plays on the page with the screen away', same(held.calls, ['close']) && during.logged && during.flying, { calls: held.calls, during });
  await settle(p, 1500);
  r = await state(n);
  ok('the screen comes back once the celebration has played, with the session done', same(r.calls, ['close', 'open']) && !(await xp()).flying && r.today.dial.arcs.find(a => a.id === m.next).state === 'done', r.calls);
  ok('the page\'s message about it comes back with the screen', !!r.frame.notice && r.frame.notice.text.startsWith(m.nextTitle + ' done'), r.frame);
  await p.evaluate(() => { window.__calm = 0; __levelUp({ charLevel: null, from: 1, attrs: [{ id: 'physique', short: 'Physique', from: 1, level: 2 }] }); afterCelebrations(() => __calm++) });
  await settle(p, 2000);
  const levelUp = await p.evaluate(() => ({ calm: __calm, shown: !!document.querySelector('.luov') }));
  await settle(p, 3500);
  const after = await p.evaluate(() => ({ calm: __calm, shown: !!document.querySelector('.luov') }));
  ok('a level-up keeps the screen waiting until it has closed', levelUp.calm === 0 && levelUp.shown && after.calm === 1 && !after.shown, { levelUp, after });
  // The session it completes is the day's last planned one, Focused Reading (30 minutes, reading.focused), and the Daily Hand card "Focused
  // reading" (read for 25 minutes) is on the table. Life Director reads that card as a 25-minute reading quest and counts the session
  // toward it (its quest overlay gets the session's event), which meets it, so the session reaches the quest bounty: before the fix
  // the bounty was paid after the award had been taken back (the card done, Sparks and rested XP spent, "Quest complete" said).
  const refused = await p.evaluate((day) => {
    const card = dhMakeQuest(QUEST_LIBRARY.find(t => t.verify === 'act:reading:25'), getToday(), 0, 'bounty');
    state.quests = state.quests.concat(card); Object.assign(state, { sparks: 100, restedXP: 40 });
    saveReadOnly = 'A newer version saved this'; saveReadOnlyTold = true;
    const before = state.activities.length; let r = null, done = null;
    for (const s of LifeDirector.voice.sessions(day)) { r = LifeDirector.voice.completeSession(s.id); if (r.ok) { done = s; break } }
    const ST = LifeDirectorApp._debug().ST, h = done && ST.schedule.historyInstances.find(x => x.instanceId === done.id), ov = ST.questOverlay[questKey(card)];
    const out = { ok: !!(r && r.ok), session: done && done.title, logged: state.activities.length !== before, flying: !!document.querySelector('.fx-xp'), notice: (LifeDirector.native.frame().notice || {}).text || '',
      counted: !!(h && h.completionEventId && ov && ov.eventIds.includes(h.completionEventId)), fulfilled: isQuestDone(card), sparks: state.sparks, rested: state.restedXP };
    saveReadOnly = ''; saveReadOnlyTold = false; state.quests = state.quests.filter(q => q.id !== card.id);
    return out;
  }, directorDay.DAY);
  ok('a Done the save refuses (read-only) is taken back and says so, with no XP and no celebration', refused.ok && !refused.logged && !refused.flying && /XP not saved: this version is read-only/.test(refused.notice), refused);
  ok('...and it pays no quest bounty: the reading session counts toward the reading card in Life Director, but the card stays open, Sparks and rested XP are untouched, and the notice does not say "Quest complete"',
    refused.ok && refused.session === 'Focused Reading' && refused.counted && !refused.fulfilled && refused.sparks === 100 && refused.rested === 40 && !/Quest complete/.test(refused.notice), refused);
  // Life Director's award, on its own: with a Daily Hand card it fulfils and Sparks and rested XP for it, a save that fails (storage
  // full or read-only) is taken back whole, as Log's is, and the page is left saying so rather than "Quest complete"
  for (const how of ['full', 'read-only']) {
    const award = await p.evaluate(how => {
      const card = dhMakeQuest(QUEST_LIBRARY.find(t => t.verify === 'act:reading:25'), getToday(), 0, 9);
      state.quests = state.quests.filter(q => q.id !== card.id).concat(card); Object.assign(state, { sparks: 100, restedXP: 40 });
      const was = JSON.stringify(state), real = Storage.prototype.setItem;
      if (how === 'full') Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) }; else { saveReadOnly = 'A newer version saved this'; saveReadOnlyTold = true }
      let a; try { a = LifeMaxHost.awardActivity({ eventId: 'refused-' + how, activityTypeTag: 'reading.focused', actualStartEpoch: Date.now() / 1000 - 1800, actualDurationMinutes: 30 }, { title: 'Focused Reading' }) }
      finally { Storage.prototype.setItem = real; saveReadOnly = ''; saveReadOnlyTold = false }
      return { reason: a.reason, same: JSON.stringify(state) === was, card: isQuestDone(card), sparks: state.sparks, rested: state.restedXP, shown: document.getElementById('toast').textContent };
    }, how);
    ok('a Life Director award whose save fails (' + how + ') is taken back whole: no Daily Hand card fulfilled, no Sparks paid, no rested XP spent, and the page says it was not saved',
      award.reason === (how === 'full' ? 'HOST_SAVE_FAILED' : 'HOST_READ_ONLY') && award.same && !award.card && award.sparks === 100 && award.rested === 40 && /^(Could not save|Not saved)/.test(award.shown), award);
  }
  // the other way Life Director logs a session: a reading session planned for a later day, done today ("Log it for today" in its
  // sheet), with storage full. It counts toward a reading card on the table as the Done above does, and pays no bounty either
  const later = await p.evaluate(day => {
    for (let i = 1; i < 7; i++) { const s = LifeDirector.voice.sessions(new Date(Date.parse(day) + i * 864e5).toISOString().slice(0, 10)).find(x => x.title === 'Focused Reading'); if (s) return s.id }
    return null;
  }, directorDay.DAY);
  r = await tap('inst', later);
  const early = await p.evaluate(() => {
    const card = dhMakeQuest(QUEST_LIBRARY.find(t => t.verify === 'act:reading:25'), getToday(), 0, 'early');
    state.quests = state.quests.concat(card); Object.assign(state, { sparks: 100, restedXP: 40 });
    const adhoc = () => (LifeDirectorApp._debug().ST.schedule.historyInstances || []).filter(x => x.adhoc), n = adhoc().length;
    const acts = state.activities.length, qd = JSON.stringify(state.questDone), btn = document.querySelector('#view-life-director .ld-sheet.open [data-act=logToday]'), real = Storage.prototype.setItem;
    Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
    try { if (btn) btn.dispatchEvent(new MouseEvent('click', { bubbles: true })) } finally { Storage.prototype.setItem = real }
    const h = adhoc()[n], ov = LifeDirectorApp._debug().ST.questOverlay[questKey(card)];
    const out = { button: !!btn, recorded: !!h && h.title === 'Focused Reading' && h.hostActivityId === null, counted: !!(h && ov && ov.eventIds.includes(h.completionEventId)),
      untouched: state.activities.length === acts && JSON.stringify(state.questDone) === qd, fulfilled: isQuestDone(card), sparks: state.sparks, rested: state.restedXP,
      notice: (LifeDirector.native.frame().notice || {}).text || '' };
    state.quests = state.quests.filter(q => q.id !== card.id);
    return out;
  });
  await settle(p, 500);
  ok('a later day\'s reading session logged for today with storage full pays no quest bounty either: it counts toward the reading card in Life Director, but no activity or card is kept, and Sparks and rested XP are untouched',
    !!later && same(r.calls, ['close']) && early.button && early.recorded && early.counted && early.untouched && !early.fulfilled && early.sparks === 100 && early.rested === 40, { later, calls: r.calls, early });
  ok('...and its notice says the XP was not saved', /logged for today · XP not saved \(storage full\?\)/.test(early.notice), early.notice);
  // the control: the same "Log it for today" with the save working does pay the bounty. The card is marked verify 'self', so the
  // Daily Hand's own check cannot complete it from the saved activity: only Life Director's bounty can
  r = await tap('inst', later);
  const paid = await p.evaluate(() => {
    const card = Object.assign(dhMakeQuest(QUEST_LIBRARY.find(t => t.verify === 'act:reading:25'), getToday(), 0, 'paid'), { verify: 'self' });
    state.quests = state.quests.concat(card);
    const btn = document.querySelector('#view-life-director .ld-sheet.open [data-act=logToday]');
    if (btn) btn.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    const out = { button: !!btn, fulfilled: isQuestDone(card), notice: (LifeDirector.native.frame().notice || {}).text || '' };
    return out;
  });
  await settle(p, 2000); // its XP celebration plays out on the page, which the screen waits for
  ok('with the save working, the same "Log it for today" pays the quest bounty, and its notice says so', paid.button && paid.fulfilled && /Quest complete/.test(paid.notice), paid);

  r = await tap('tab', 'week');
  ok('a section tab closes the screen and the page shows that section', same(r.calls, ['close']) && r.shows === 'week', r);
  r = await click('.ld-tab[data-tab=today]');
  ok('coming back to Today on the page brings the screen back', same(r.calls, ['open']), r.calls);

  r = await tap('tour');
  ok('the guide (?) closes the screen and runs on the page', same(r.calls, ['close']) && r.shows === 'guide', r);
  r = await click('[data-act=tourEnd]');
  ok('ending the guide brings the screen back with the page\'s message', same(r.calls, ['open']) && /Guide closed/.test((r.frame.notice || {}).text), r);

  r = await tap('disrupt');
  ok('Something came up opens the page\'s sheet', same(r.calls, ['close']) && r.sheet.includes('Something came up'), r.calls);
  await click('.ld-sheet-bg');
  r = await tap('replan');
  ok('Re-plan re-plans on the page and the screen comes straight back with its message', same(r.calls, ['close', 'open']) && /Plan updated/.test((r.frame.notice || {}).text), r);

  await p.evaluate(() => { const real = Storage.prototype.setItem; window.__realSet = real; Storage.prototype.setItem = function (k) { if (k === 'lifemax_director_state_v1') throw new Error('quota'); return real.apply(this, arguments) } });
  r = await tap('replan');
  await p.evaluate(() => { Storage.prototype.setItem = window.__realSet });
  const banner = r.frame && r.frame.banner;
  ok('a banner on the page is in the projection with its buttons', !!banner && banner.kind === 'bad' && /^Not saved/.test(banner.text) && banner.dismiss === true && same(banner.actions.map(a => a.act), ['replan']), banner);
  r = await tap('dismissBanner');
  ok('dismissing it on the page takes it away and the screen comes back', same(r.calls, ['close', 'open']) && r.frame.banner === null, r);

  r = await tap('nope');
  ok('a tap Today does not have changes nothing and the screen comes straight back', same(r.calls, ['close', 'open']), r.calls);
  r = await tap('inst', 'i_replanned_away');
  const meal = await tap('meal', '1');
  ok('a session or meal that left the plan since the screen drew it says so, and the screen comes straight back', [r, meal].every(x => same(x.calls, ['close', 'open']) && (x.frame.notice || {}).text === 'That is no longer on the plan.'), [r.frame, meal.frame]);

  n = await mark(p);
  await go(p, 'dashboard', 500);
  const hidden = await mark(p);
  await p.clock.runFor(16000); await settle(p, 300);
  const away = await p.evaluate(([n, h]) => ({ closed: __since(n).some(c => c[1] === 'close'), after: __since(h).map(c => c[1]) }), [n, hidden]);
  ok('leaving Life Director closes the screen, and a hidden screen gets no updates', away.closed && away.after.length === 0, away);
  ok('no script errors', p.errors.length === 0, p.errors);
  await fresh.context().close(); await p.context().close();

  // The phone check of build 292: Done on a session that has started, in the page's own session sheet. A tap on the session hands
  // it to the page, which opens the sheet; Mark done there earns the XP, and its celebration plays on the page from the button. The
  // screen stays away until it has played, then comes back with the session done (it used to come straight back, over it).
  const q = await start(b, true);
  await go(q, 'life-director', 1500);
  const on = await q.evaluate(day => LifeDirector.voice.sessions(day).find(s => s.startEpoch * 1000 > Date.now()), directorDay.DAY);
  await q.clock.setSystemTime(on.startEpoch * 1000 + 5 * 60000); await settle(q, 300);
  const sheetDone = () => q.evaluate(() => document.querySelector('#view-life-director .ld-sheet.open [data-act=done]').dispatchEvent(new MouseEvent('click', { bubbles: true })));
  const since = k => q.evaluate(([k, t]) => {
    const calls = __since(k), open = calls.filter(c => c[1] === 'open').pop(), arc = open && open[2].snapshot.today.dial.arcs.find(a => a.title === t);
    return { calls: calls.map(c => c[1]).filter(c => c !== 'update'), shows: LifeDirector.native.showing(), logged: state.activities.some(a => a.name === t && a.ldEventId), flying: !!document.querySelector('.fx-xp'), state: arc ? arc.state : '' };
  }, [k, on.title]);
  let k = await mark(q);
  await q.evaluate(id => Capacitor.Plugins.NativeLifeDirector.__emit('action', { action: 'inst', id }), on.id); await settle(q, 500);
  const inSheet = await since(k);
  await sheetDone(); await settle(q, 1300); // the celebration plays for 1.55 s
  const playing = await since(k);
  await settle(q, 700);
  const back = await since(k);
  ok('Done in the page\'s sheet on a session that has started: its XP celebration plays on the page, and the screen stays away while it does',
    inSheet.shows === 'sheet' && playing.logged && playing.flying && same(playing.calls, ['close']), { inSheet, playing });
  ok('...then the screen comes back, with the session done', same(back.calls, ['close', 'open']) && !back.flying && back.state === 'done', back);
  // the classic page (no native screen) celebrates the same Done the same way: the celebration comes from the save, as Log's does
  const last = await q.evaluate(day => { setNativeDirectorEnabled(false); return LifeDirector.voice.sessions(day)[0] }, directorDay.DAY);
  await settle(q, 300);
  k = await mark(q);
  await q.evaluate(id => document.querySelector('#view-life-director .ld-ses[data-id="' + id + '"]').dispatchEvent(new MouseEvent('click', { bubbles: true })), last.id); await settle(q, 300);
  await sheetDone(); await settle(q, 300);
  const classic = await q.evaluate(([k, t]) => ({ calls: __since(k).length, logged: state.activities.some(a => a.name === t && a.ldEventId), flying: !!document.querySelector('.fx-xp') }), [k, last.title]);
  ok('the classic page\'s Done celebrates its XP the same way', !!last && classic.calls === 0 && classic.logged && classic.flying, { last, classic });
  ok('no script errors around Done in the sheet', q.errors.length === 0, q.errors);
  await q.context().close();

  /* ── phase 2: the dial's handles ──
     WEB drives the page's own dial and sheet: drag(key, angle, release) holds the handle at an angle with pointer events and reads
     the centre and the arc as the page draws them (then lets go or cancels); key(key, dir) presses an arrow on the handle; sheet()
     reads the confirmation the page opened, and keep() closes it with its own Keep or OK. */
  const WEB = `
    window.__ld = () => document.querySelector('#view-life-director .ld-root');
    window.__hd = key => { const [rule, end, ymd, ep] = key.split('|'); return [...__ld().querySelectorAll('.ld-dial .ld-hd')].find(h => h.dataset.rule === rule && h.dataset.end === end && h.dataset.ymd === ymd && h.dataset.ep === ep) };
    window.__drag = (key, angle, release) => {
      const dial = __ld().querySelector('.ld-dial'), svg = dial.querySelector('svg'), r = svg.getBoundingClientRect(), t = (angle - 90) * Math.PI / 180;
      const at = { clientX: r.left + (180 + 130 * Math.cos(t)) / 360 * r.width, clientY: r.top + (180 + 130 * Math.sin(t)) / 360 * r.height };
      const ev = (type, el) => el.dispatchEvent(new PointerEvent(type, Object.assign({ pointerId: 7, bubbles: true, cancelable: true }, at)));
      ev('pointerdown', __hd(key).querySelector('.ld-hd-ring')); ev('pointermove', svg);
      const core = dial.querySelector('.ld-core'), seen = { kick: core.querySelector('.ld-core-k').textContent, time: core.querySelector('.ld-core-big').textContent,
        subs: [...core.querySelectorAll('.ld-core-s')].map(e => e.textContent), ghost: svg.querySelector('.ld-drag-arc').getAttribute('d') || '' };
      ev(release ? 'pointerup' : 'pointercancel', svg);
      return seen;
    };
    window.__key = (key, dir) => __hd(key).dispatchEvent(new KeyboardEvent('keydown', { key: dir, bubbles: true, cancelable: true }));
    window.__sheet = () => { const b = __ld().querySelector('.ld-sheet.open .ld-sheet-body'), q = sel => (b.querySelector(sel) || { textContent: '' }).textContent;
      return b && { title: q('h3'), from: q('.ld-change s'), to: q('.ld-change b'), problem: q('.ld-note'), body: q('p.ld-muted:not(.ld-small-print)'), note: q('.ld-small-print'), apply: q('[data-act=dialApply]'), keep: q('[data-act=dialCancel]') } };
    window.__keep = () => __ld().querySelector('.ld-sheet.open [data-act=dialCancel]').click();
    window.__hud = () => { const h = __ld().querySelector('.ld-bhud'); return h && [...h.children].map(e => e.textContent) };`;
  const page2 = async (native) => {
    const q = await start(b, true);
    await q.evaluate(WEB);
    if (!native) await q.evaluate(() => { lmaLoad().nativeDirector = false; lmaSave() });
    await q.evaluate(() => lmaNavTo('life-director')); await settle(q, 1500);
    return q;
  };
  const A = await page2(true);
  const cmd = async (command, payload) => { const n = await mark(A); await A.evaluate(([c, x, id]) => Capacitor.Plugins.NativeLifeDirector.__emit('command', { command: c, payload: x, requestId: id }), [command, payload, command + n]); await settle(A, 300);
    return A.evaluate(([n, id]) => { const calls = __since(n), u = calls.find(c => c[1] === 'update' && c[2].reply && c[2].reply.requestId === id); return { reply: u && u[2].reply, snap: u && u[2].snapshot, closed: calls.some(c => c[1] === 'close') } }, [n, command + n]) };
  const handle = (snap, rule, end) => snap.today.dial.handles.find(h => h.rule === rule && h.end === end);
  const seen = await A.evaluate(() => NativeDirectorTabProjection.snapshot());
  const H = { bed: handle(seen, 'sleep', 'start'), wake: handle(seen, 'sleep', 'end'), work0: handle(seen, 'work', 'start'), work1: handle(seen, 'work', 'end') };

  const drawn = await A.evaluate((dial) => {
    const hds = [...__ld().querySelectorAll('.ld-dial .ld-hd')], polar = a => [Math.cos((a - 90) * Math.PI / 180), Math.sin((a - 90) * Math.PI / 180)].map(v => (180 + 130 * v).toFixed(1)).join(' ');
    return { count: dial.handles.length === hds.length, keys: JSON.stringify(dial.handles.map(h => h.key)) === JSON.stringify(hds.map(h => [h.dataset.rule, h.dataset.end, h.dataset.ymd, h.dataset.ep].join('|'))),
      places: dial.handles.every((h, i) => polar(h.a) === hds[i].querySelector('circle').getAttribute('cx') + ' ' + hds[i].querySelector('circle').getAttribute('cy')),
      labels: dial.handles.every((h, i) => h.aria === hds[i].getAttribute('aria-label')),
      stops: dial.stops.length === 96 && dial.stops[0].from === 0 && dial.stops.every((s, i) => i === 0 || (s.from > dial.stops[i - 1].from && s.m === dial.stops[i - 1].m + 15)) && dial.handles.every(h => h.lengths.length === dial.stops.length) };
  }, seen.today.dial);
  ok('the handles in the projection are the page\'s: the same handles, places and TalkBack labels, and a stop for every quarter hour', Object.values(drawn).every(Boolean) && Object.values(H).every(Boolean), drawn);

  /* what a drag shows, predicted from the projection alone, against the page's own dial held at the same angle: inside stops, on
     either side of the edges between them, around the handle and at both ends of the day */
  const predicted = (dial, h, a) => {
    const i = dial.stops.reduce((k, s, j) => (s.from <= a ? j : k), 0), st = dial.stops[i], len = h.lengths[i];
    const polar = x => [Math.cos((x - 90) * Math.PI / 180), Math.sin((x - 90) * Math.PI / 180)].map(v => (180 + 130 * v).toFixed(2)).join(' ');
    const [g0, g1] = [h.end === 'start' ? st.a : h.ends[i], h.end === 'end' ? st.a : h.ends[i]];
    return { kick: h.label, time: st.time, subs: [len || dial.dragWords.tooShort, dial.dragWords.release], ghost: !len ? '' : g1 - g0 >= 359.95 ? 'ring' : polar(g0) + '|' + polar(g1) };
  };
  const misses = [];
  for (const h of Object.values(H)) {
    const i = seen.today.dial.stops.findIndex(s => s.m === h.minute), edges = [i - 2, i + 1, i + 3, 40, 95].filter(k => k > 0 && k < 96).map(k => seen.today.dial.stops[k].from);
    for (const a of [h.a, h.a + 0.9, h.a - 7.3, h.a + 41.2, (h.a + 180) % 360, 0.1, 359.9].concat(edges.flatMap(e => [e - 0.05, e + 0.05]))) {
      const web = await A.evaluate(([k, x]) => __drag(k, x, false), [h.key, (a + 360) % 360]), want = predicted(seen.today.dial, h, (a + 360) % 360);
      const g = /^M([\d.]+) ([\d.]+)A[\d.]+ [\d.]+ 0 [01] 1 ([\d.]+) ([\d.]+)$/.exec(web.ghost), ring = /^M180 50A130 130 0 1 1 180 310A130 130 0 1 1 180 50$/.test(web.ghost); // a whole day: the page's full ring
      const got = Object.assign({}, web, { ghost: ring ? 'ring' : g ? g[1] + ' ' + g[2] + '|' + g[3] + ' ' + g[4] : web.ghost });
      if (JSON.stringify(got) !== JSON.stringify(want)) misses.push({ handle: h.key, a, got, want });
    }
  }
  ok('a drag shows what the page\'s own dial shows at every angle tried: the time, the length or "Too short", the arc, "Release to apply"', misses.length === 0, misses.slice(0, 3));

  /* the length a drag shows at each stop is the length letting go there gives the rule: the page's own release holds the change its
     confirmation asks about and its Change it applies (ui.pendingDial), so a bedtime held past midnight reads the sleep left before the
     wake-up time; the lit arc runs from the stop that long towards the rule's other end, kept on the day */
  const lengths = await A.evaluate((dial) => {
    const { ST, ui } = LifeDirectorApp._debug(), wrong = [], even = dial.stops.every(st => st.a === st.m / 4);
    const minutes = t => { const m = /^(?:(\d+)h)? ?(?:(\d+)m)?/.exec(t); return (+m[1] || 0) * 60 + (+m[2] || 0); };
    for (const h of dial.handles) {
      const r = ST.availability.rules.find(x => x.id === h.rule);
      dial.stops.forEach((st, i) => {
        ui.pendingDial = null;
        __drag(h.key, st.a, true);
        const dur = ui.pendingDial ? ui.pendingDial.durationMin : st.m === h.minute ? r.durationMin : NaN, len = h.lengths[i];
        const end = h.end === 'start' ? Math.min(360, (st.m + dur) / 4) : Math.max(0, (st.m - dur) / 4);
        if (!(dur < 15 ? len === '' : minutes(len) === dur) || (even && Math.abs(h.ends[i] - end) > 1e-9)) wrong.push({ key: h.key, m: st.m, len, dur, end: h.ends[i], want: end });
      });
    }
    __keep();
    return { even, checked: dial.handles.length * dial.stops.length, wrong };
  }, seen.today.dial);
  await settle(A, 500);
  const bed60 = H.bed.lengths[seen.today.dial.stops.findIndex(s => s.m === 60)];
  ok('the length a drag shows at every stop is the length letting go there gives the rule (1:00 AM bedtime: "6h of sleep"), and the arc spans it', lengths.even && lengths.checked === 4 * 96 && lengths.wrong.length === 0 && bed60 === '6h of sleep', { bed60, wrong: lengths.wrong.slice(0, 4) });

  /* a drop's confirmation against the sheet the page opens when its own handle is let go at the same place */
  const confirmations = [];
  for (const [h, m] of [[H.bed, 1350], [H.bed, 240], [H.work1, 1080], [H.wake, 450], [H.work0, 1035], [H.work1, 540]]) {
    const r = await cmd('dialDrop', { key: h.key, minute: m });
    await A.evaluate(([k, x]) => __drag(k, x, true), [h.key, seen.today.dial.stops.find(s => s.m === m).a]); await settle(A, 300);
    const sheet = await A.evaluate(() => __sheet());
    await A.evaluate(() => __keep()); await settle(A, 500);
    confirmations.push({ m, ok: r.reply.ok && r.reply.changed && !r.closed && JSON.stringify(r.reply.confirm) === JSON.stringify(sheet), reply: r.reply, sheet });
  }
  ok('a drop answers with the page\'s own confirmation (a time change, under 5 hours of sleep, almost a full day of work, work too short), saving nothing and keeping the screen', confirmations.every(c => c.ok), confirmations.filter(c => !c.ok));
  const keys = [];
  for (const [step, dir] of [[1, 'ArrowRight'], [-1, 'ArrowLeft']]) {
    const r = await cmd('dialDrop', { key: H.wake.key, step });
    await A.evaluate(([k, d]) => __key(k, d), [H.wake.key, dir]); await settle(A, 300);
    const sheet = await A.evaluate(() => __sheet());
    await A.evaluate(() => __keep()); await settle(A, 500);
    keys.push({ step, ok: r.reply.ok && JSON.stringify(r.reply.confirm) === JSON.stringify(sheet), reply: r.reply.confirm, sheet });
  }
  ok('a TalkBack step asks what the handle\'s arrow keys ask, a quarter hour either way', keys.every(k => k.ok), keys);
  const same0 = await cmd('dialDrop', { key: H.bed.key, minute: H.bed.minute });
  const stale = await cmd('dialDrop', { key: 'sleep|start|2026-10-28|1', minute: 1350 });
  const offGrid = await cmd('dialDrop', { key: H.bed.key, minute: 1352 });
  const noPlace = await cmd('dialDrop', { key: H.bed.key });
  ok('a drop where the handle was changes nothing; a handle or a time that is not on the dial is refused', same0.reply.ok && same0.reply.changed === false && !same0.reply.confirm &&
    stale.reply.ok === false && /no longer on the dial/.test(stale.reply.message) && offGrid.reply.ok === false && /not on the dial/.test(offGrid.reply.message) && noPlace.reply.ok === false, [same0.reply, stale.reply, offGrid.reply, noPlace.reply]);
  const rule = (id = 'sleep') => A.evaluate((id) => { const r = LifeDirectorApp._debug().ST.availability.rules.find(x => x.id === id), saved = JSON.parse(localStorage.getItem('lifemax_director_state_v1')).availability.rules.find(x => x.id === id); return { now: [r.startMin, r.durationMin], saved: [saved.startMin, saved.durationMin] } }, id);
  const dropped = await cmd('dialDrop', { key: H.bed.key, minute: 1350 }), pending = await A.evaluate(() => LifeDirectorApp._debug().ui.pendingDial);
  ok('a drop holds nothing on the page: Keep (or OK) needs no command', dropped.reply.changed && pending == null, pending);
  const before = await rule(), beforeWork = await rule('work');
  const problem = await cmd('dialApply', { key: H.bed.key, minute: 240 });
  const tooLong = await cmd('dialApply', { key: H.work0.key, minute: 1035 }); // work from 5:15 PM to 5 PM the next day
  const where = await cmd('dialApply', { key: H.bed.key, minute: H.bed.minute });
  const gone = await cmd('dialApply', { key: 'sleep|start|2026-10-28|1', minute: 1350 });
  ok('Change it is refused for a change with a problem (the page offers only OK there) and for a handle not on the dial, and changes nothing where the handle already is', problem.reply.ok === false && problem.reply.message === 'That leaves under 5 hours of sleep. Try a different time.' &&
    tooLong.reply.ok === false && /almost a full day/.test(tooLong.reply.message) && where.reply.ok && where.reply.changed === false && gone.reply.ok === false && /no longer on the dial/.test(gone.reply.message) &&
    !problem.closed && !gone.closed && JSON.stringify(await rule()) === JSON.stringify(before) && JSON.stringify(await rule('work')) === JSON.stringify(beforeWork), [problem.reply, tooLong.reply, where.reply, gone.reply]);

  /* a band of fixed time says what the page's own band says when tapped, the drag hint included now that the screen has handles;
     the words under the dial are the page's */
  const bands = await A.evaluate((dial) => dial.arcs.filter(a => a.style === 'band' && (a.kind === 'sleep' || a.kind === 'work')).map(a => {
    const el = [...__ld().querySelectorAll('.ld-dial .ld-outer .ld-o')].find(e => +e.dataset.s === a.s && +e.dataset.e === a.e && e.dataset.k === a.kind);
    el.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    return { tip: a.tip, page: LifeDirectorApp._debug().ui.notice.text };
  }), seen.today.dial);
  const hint = await A.evaluate(() => __ld().querySelector('.ld-dial-hint').textContent.replace(/\s+/g, ' ').trim());
  ok('a band\'s tip is what the page\'s band says when tapped ("Drag its handle to change it."), and the hint under the dial is the page\'s', bands.length >= 3 && bands.every(x => x.tip === x.page && x.tip.endsWith('. Drag its handle to change it.')) &&
    seen.today.hint.join(' ') === hint, { bands, hint, projected: seen.today.hint });
  await settle(A, 500);

  /* Change it on the screen against Change it on the page: B has no native screen, so the page also plays its own build */
  const B = await page2(false);
  const applied = await cmd('dialApply', { key: H.bed.key, minute: 1350 });
  const again = await cmd('dialApply', { key: H.bed.key, minute: 1350 });
  const afterAgain = await rule();
  const pageA = await A.evaluate(() => ({ hud: __hud(), running: !!LifeDirectorApp._debug().ui.bfRun }));
  await B.evaluate(([k, a]) => __drag(k, a, true), [H.bed.key, seen.today.dial.stops.find(s => s.m === 1350).a]); await settle(B, 300);
  await B.evaluate(() => document.querySelector('#view-life-director .ld-sheet.open [data-act=dialApply]').click());
  // sessions by title and time: the engine numbers the sessions it adds from its own count on each page
  const plan = q => q.evaluate(() => { const { ST, ui } = LifeDirectorApp._debug(); return { rules: ST.availability.rules.map(r => [r.id, r.startMin, r.durationMin]), planned: ST.schedule.scheduledInstances.filter(i => i.lifecycleState === 'PLANNED').map(i => i.title + '@' + i.startEpoch + '-' + i.endEpoch), notice: ui.notice && ui.notice.text } });
  const [pa, pb] = [await plan(A), await plan(B)];
  const build = applied.snap && applied.snap.today.dial.build, bed = applied.snap && handle(applied.snap, 'sleep', 'start');
  ok('Change it on the screen does what the page\'s Change it does: the same rule, the same plan, the same message, in the reply and the frame', applied.reply.ok && applied.reply.changed && applied.reply.moved && !applied.closed && applied.reply.message === 'Bedtime now 10:30 PM · schedule adjusted' &&
    JSON.stringify(pa) === JSON.stringify(pb) && pa.notice === applied.reply.message && applied.snap.notice.text === applied.reply.message, { reply: applied.reply, rules: [pa.rules, pb.rules], notice: [pa.notice, pb.notice], planned: pa.planned.map((x, i) => x === pb.planned[i] ? '' : x + ' / ' + pb.planned[i]).filter(Boolean).slice(0, 4) });
  ok('the frame sent with the reply has the handle where it was dropped, and the change is saved', bed && bed.minute === 1350 && bed.a === 337.5 && bed.key !== H.bed.key && JSON.stringify((await rule()).saved) === JSON.stringify([1350, 510]), { bed, rule: await rule() });
  ok('the same Change it again is refused (its handle has moved), so the save changes once', again.reply.ok === false && /no longer on the dial/.test(again.reply.message) && JSON.stringify(afterAgain.saved) === JSON.stringify([1350, 510]) && !again.closed, { again: again.reply, afterAgain });
  /* the page's own build on B, read at the moments the projection gives */
  const hudAt = async (q, ms, from) => { await q.clock.runFor(ms - from); await q.waitForTimeout(20); return q.evaluate(() => __hud()) };
  const h0 = await hudAt(B, 5, 0), h1 = build && await hudAt(B, build.start.at + 10, 5), h2 = build && await hudAt(B, build.lock.at + 10, build.start.at + 10);
  const bOwn = await B.evaluate(() => LifeDirector.native.today(0).dial.build);
  const sessionIds = new Set(applied.snap.today.dial.arcs.filter(a => a.style === 'session').map(a => a.id)), unnamed = x => JSON.stringify(Object.assign({}, x, { id: 0, items: x.items.map(it => Object.assign({}, it, { id: '' })) }));
  ok('the build in the reply\'s frame is the repair the page plays: its words before, at the start and at the lock, and the same plan the page would project', !!build && build.mode === 'repair' && !build.sweep && build.items.length > 0 &&
    JSON.stringify(h0) === JSON.stringify([build.intro.kick, build.intro.big, build.intro.sub]) && JSON.stringify(h1) === JSON.stringify([build.start.kick, build.start.big, build.start.sub]) &&
    JSON.stringify(h2) === JSON.stringify([build.lock.kick, build.lock.big, build.lock.sub]) && unnamed(bOwn) === unnamed(build) && build.items.concat(build.keep.map(id => ({ id }))).every(it => sessionIds.has(it.id)), { build, bOwn });
  ok('under the native screen the page plays no build of its own', pageA.hud === null && !pageA.running, pageA);

  /* a full build: Re-plan on the screen (a tap the page runs) and on the page */
  let n2 = await mark(A);
  await A.evaluate(() => Capacitor.Plugins.NativeLifeDirector.__emit('action', { action: 'replan', id: '' })); await settle(A, 500);
  const full = await A.evaluate(n => { const open = __since(n).filter(c => c[1] === 'open').pop(); return { calls: __since(n).map(c => c[1]).filter(k => k !== 'update'), build: open && open[2].snapshot.today.dial.build, hud: __hud(), running: !!LifeDirectorApp._debug().ui.bfRun } }, n2);
  ok('Re-plan comes back with a full build (the sweep and its read-out) and stops the build the page began meanwhile', JSON.stringify(full.calls) === JSON.stringify(['close', 'open']) && full.build && full.build.mode === 'build' && full.build.full &&
    full.build.sweep.kick === 'Mapping your day' && full.build.sweep.readout[0].a === 0 && full.build.id > build.id && full.hud === null && !full.running, full);
  await B.clock.runFor(3000);
  await B.evaluate(() => document.querySelector('#view-life-director [data-act=replan]').click());
  const fb = await B.evaluate(() => LifeDirector.native.today(0).dial.build);
  const R = fb.sweep.readout, probes = R.map((w, i) => [w, R[i + 1] ? R[i + 1].a : 360]).filter(([w, next]) => next - w.a > 12).slice(0, 5);
  const reads = [];
  let t = 0;
  for (const [w, next] of probes) { const at = Math.round((w.a + next) / 2 / 360 * fb.sweep.ms); reads.push({ want: [fb.sweep.kick, w.hour, w.what], got: await hudAt(B, at, t) }); t = at; }
  ok('the sweep\'s read-out in the projection is what the page reads out as its own arm passes', probes.length >= 3 && reads.every(r => JSON.stringify(r.want) === JSON.stringify(r.got)), reads);
  await B.clock.runFor(fb.end + 100 - t); await settle(B, 300); // the page's build ends (a tap on a building dial only skips it)
  ok('the page\'s own build ends when the projection says it does', (await B.evaluate(() => __hud())) === null);

  /* a refused save: the reply says so with the page's own words, the page's banner is in the frame, and nothing is saved */
  await A.evaluate(() => { window.__realSet = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === 'lifemax_director_state_v1') throw new DOMException('The quota has been exceeded.', 'QuotaExceededError'); return window.__realSet.apply(this, arguments) } });
  const savedBefore = (await rule()).saved;
  const refusedSave = await cmd('dialApply', { key: handle(applied.snap, 'sleep', 'start').key, minute: 1320 });
  await A.evaluate(() => { Storage.prototype.setItem = window.__realSet });
  const refusedBanner = refusedSave.snap && refusedSave.snap.banner;
  const refusedNotice = refusedSave.snap && refusedSave.snap.notice;
  ok('a Change it the save refuses replies not ok with the page\'s reason, the page\'s banner comes with it and no message that it changed, the screen stays and the saved rule is untouched', refusedSave.reply.ok === false && refusedSave.reply.changed === true && /^Not saved: Storage is full/.test(refusedSave.reply.message) &&
    !!refusedBanner && refusedBanner.kind === 'bad' && refusedBanner.text === refusedSave.reply.message && !(refusedNotice && /now 10:00 PM/.test(refusedNotice.text)) && !refusedSave.closed && JSON.stringify((await rule()).saved) === JSON.stringify(savedBefore), { reply: refusedSave.reply, banner: refusedBanner, notice: refusedNotice });

  /* reduced motion: the frame says so and no build is projected, as the page plays none */
  await A.emulateMedia({ reducedMotion: 'reduce' }); await B.emulateMedia({ reducedMotion: 'reduce' });
  const calm = await cmd('dialApply', { key: H.work1.key, minute: 1050 });
  await B.evaluate(([k, a]) => __drag(k, a, true), [H.work1.key, seen.today.dial.stops.find(s => s.m === 1050).a]); await settle(B, 300);
  await B.evaluate(() => document.querySelector('#view-life-director .ld-sheet.open [data-act=dialApply]').click()); await B.clock.runFor(100);
  const bCalm = await B.evaluate(() => ({ hud: __hud(), build: LifeDirector.native.today(0).dial.build, reduced: LifeDirector.native.frame().reducedMotion }));
  ok('with reduced motion the frame says so and carries no build, and the page plays none either', calm.reply.ok && calm.snap.reducedMotion === true && calm.snap.today.dial.build === null && bCalm.reduced && bCalm.build === null && bCalm.hud === null, { reply: calm.reply, bCalm });

  /* a dial command changes the rule only while the page shows Today with nothing over it, the state the screen covers: Change it
     would otherwise close the page's sheet and apply what was confirmed over something else */
  const bedNow = handle(await A.evaluate(() => NativeDirectorTabProjection.snapshot()), 'sleep', 'start'), ruleNow = await rule();
  const underSheet = await A.evaluate((k) => { LifeDirector.native.act('disrupt', ''); const r = { showing: LifeDirector.native.showing(), drop: LifeDirector.native.drop(k, { minute: 1380 }), apply: LifeDirector.native.apply(k, { minute: 1380 }), sheet: !!__ld().querySelector('.ld-sheet.open') };
    __ld().querySelector('.ld-sheet.open [data-act=closeSheet]').click(); return r; }, bedNow.key);
  await settle(A, 800);
  ok('a dial command is refused while a sheet is over Today, which stays open, and the rule is untouched', underSheet.showing === 'sheet' && underSheet.sheet && underSheet.drop.ok === false && underSheet.apply.ok === false && /not on the page/.test(underSheet.apply.message) &&
    JSON.stringify(await rule()) === JSON.stringify(ruleNow), { underSheet, rule: [ruleNow, await rule()] });
  ok('no script errors in phase 2', A.errors.length === 0 && B.errors.length === 0, A.errors.concat(B.errors));

  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); fs.unlinkSync(out); process.exit(fail ? 1 : 0);
})();
