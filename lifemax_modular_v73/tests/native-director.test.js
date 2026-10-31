/* Native Life Director, web half: the Today projection a native screen draws (checked against what the page itself draws),
   when the screen may cover the page, the taps it hands back to Life Director's own handlers, and the day command.
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
    Plugins: { NativeLifeDirector: mk('director', ['director.today.v1']), NativeOverview: mk('overview', []) }, registerPlugin: () => ({}), convertFileSrc: u => u };`;

async function start(b, planned) {
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
  p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await directorDay.seed(p, { planned });
  await p.addInitScript(FAKES);
  await p.goto('file://' + out);
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
        taps: appt.title === 'Dentist' && appt.tip === 'Dentist · 6:30 PM–7:15 PM' && !appt.act && work.tip === 'Work · 9:00 AM–5:00 PM' && !work.act &&
          meal.act === 'meal' && meal.id === q('.ld-meal:not(.ld-appt)').dataset.ms && t.rows.ahead.find(r => r.kind === 'meal').act === 'meal' && arcs('session').every(a => a.act === 'inst' && a.id),
      },
    };
  });
  ok('opening Life Director on Today opens the native screen once, with the Today projection', m.opened === 1 && !!m.checks, m);
  const said = { frame: 'the header, status dot and clock', tabs: 'the section tabs', strip: 'the day strip and its session dots', rings: 'what is on each ring', angles: 'the arc angles after the dial\'s gaps', sessions: 'each session\'s state and the next one', words: 'the words written on the rings', hand: 'the hand and the current-block ring', core: 'the centre', mix: 'the day mix', upNext: 'the up-next card', reminders: 'the all-day reminders', rows: 'the running order', earlier: 'the earlier-today summary', taps: 'what a tap does (page acts, meal ids, tips on fixed time without a drag hint)' };
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
      untouched: state.activities.length === acts && JSON.stringify(state.questDone) === qd, fulfilled: isQuestDone(card), sparks: state.sparks, rested: state.restedXP };
    state.quests = state.quests.filter(q => q.id !== card.id);
    return out;
  });
  await settle(p, 500);
  ok('a later day\'s reading session logged for today with storage full pays no quest bounty either: it counts toward the reading card in Life Director, but no activity or card is kept, and Sparks and rested XP are untouched',
    !!later && same(r.calls, ['close']) && early.button && early.recorded && early.counted && early.untouched && !early.fulfilled && early.sparks === 100 && early.rested === 40, { later, calls: r.calls, early });

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

  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); fs.unlinkSync(out); process.exit(fail ? 1 : 0);
})();
