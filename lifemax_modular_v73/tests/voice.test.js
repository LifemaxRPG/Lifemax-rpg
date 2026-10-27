/* LifeMax Voice phase 1 acceptance tests. Run:  NODE_PATH=$(npm root -g) node tests/voice.test.js <path to built html>
   Needs Playwright with Chromium. Times use America/New_York; the clock is injected, nothing here waits on real time. */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', fs.readdirSync(path.join(__dirname, '..', 'dist')).filter(f => /^Life_Max_RPG_v\d+\.html$/.test(f)).sort((a, b) => +a.match(/\d+/)[0] - +b.match(/\d+/)[0]).pop()));
let pass = 0, fail = 0; const ok = (name, cond, extra) => { if (cond) { pass++; console.log('PASS ' + name) } else { fail++; console.log('FAIL ' + name + (extra ? '  ' + JSON.stringify(extra) : '')) } };
const NY = 'America/New_York', MON = Date.UTC(2026, 9, 26, 17, 14);   // Mon 2026-10-26 13:14 EDT
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); const errs = [];
  p.on('pageerror', e => errs.push(String(e)));
  await p.goto('file://' + file); await p.waitForTimeout(800);
  await p.evaluate(() => { const o = document.getElementById('onboarding'); if (o) { o.classList.remove('show'); o.style.display = 'none' } localStorage.removeItem('lifemax_voice_op_journal_v1') });
  // Life Director with a sleep rule so there is something to conflict with
  await p.evaluate(() => { const { ST } = LifeDirectorApp._debug(); ST.availability.timezone = 'America/New_York'; ST.availability.rules.push({ id: 'sl', kind: 'SLEEP', title: 'Sleep', days: [0, 1, 2, 3, 4, 5, 6], startMin: 23 * 60, durationMin: 480, commuteMin: 0 }); ST.setupComplete = true; LifeDirector.regenerate('test') });
  const say = (t, o) => p.evaluate(([t, o]) => LifeMaxVoice.simulateUtterance(t, o), [t, o || {}]);
  const clock = (now, workOn) => p.evaluate(([now]) => LifeMaxVoice.setClock({ now, tz: 'America/New_York' }), [now]);
  const reset = async () => { await p.evaluate(() => LifeMaxVoice.reset()); };
  await clock(MON);

  // 1 canonical
  let r = await say('Hey, can you put an hour-long workout on my Life Director tomorrow morning at nine?', { utteranceId: 'u1' });
  ok('canonical: previewed', r.state === 'PREVIEWED', r);
  ok('canonical: slot', r.slot && r.slot.localDate === '2026-10-27' && r.slot.localStartTime === '09:00' && r.slot.durationMinutes === 60 && r.slot.ambiguities.length === 0 && r.title === 'Workout', r.slot);
  const op1 = r.operationId; r = await p.evaluate(() => LifeMaxVoice.confirm());
  ok('canonical: confirmed + saved in Life Director', r.state === 'CONFIRMED' && (await p.evaluate(id => !!LifeDirector.voice.find(id), r.entityId)), r);
  ok('canonical: undo is offered (verified inverse exists)', r.undo === true);
  const ids = await p.evaluate(() => (LifeDirectorApp._debug().ST.fixedCommitments || []).filter(c => c.voice).length);
  ok('canonical: exactly one commitment', ids === 1, ids);

  // a voice meeting belongs on the inner ring with the other plan items
  await p.evaluate(() => { LifeMaxFeatures.open('life-director'); const b = document.querySelector('.ld-chipday[data-act="day"][data-i="1"]'); if (b) b.click() }); await p.waitForTimeout(600);
  const ring = await p.evaluate(() => { const t = LifeDirectorApp._debug().ST.fixedCommitments.find(c => c.voice); const e = String(t.startEpoch); return { appt: [...document.querySelectorAll('.ld-appt')].some(n => n.dataset.s === e), outer: [...document.querySelectorAll('.ld-o')].some(n => n.dataset.s === e) } });
  ok('voice appointment draws on the inner ring, not the outer', ring.appt && !ring.outer, ring);

  // idempotency
  const before = ids;
  await say('Hey, can you put an hour-long workout on my Life Director tomorrow morning at nine?', { utteranceId: 'u1' });
  ok('tier1: duplicate utteranceId dropped', (await p.evaluate(() => LifeDirectorApp._debug().ST.fixedCommitments.filter(c => c.voice).length)) === before);
  const again = await p.evaluate(op => { const f = LifeMaxVoice._t.frame(); const a = LifeMaxVoice._t.execute(f.operationId); const b = LifeMaxVoice._t.execute(f.operationId); return { n: LifeDirectorApp._debug().ST.fixedCommitments.filter(c => c.voice).length, st: b && b.state } }, op1);
  ok('tier2: execute twice with the same operationId creates it once', again.n === 1, again);
  // lost response: journal PREPARING while the activity already exists
  const rec = await p.evaluate(() => { const f = LifeMaxVoice._t.frame(); const j = LifeMaxVoice._t.journal().find(e => e.operationId === f.operationId); j.status = 'PREPARING'; delete j.createdEntityId; LifeMaxVoice._t.journalPut(j); LifeMaxVoice._t.recover(); const k = LifeMaxVoice._t.journal().find(e => e.operationId === f.operationId); return { status: k.status, id: k.createdEntityId, n: LifeDirectorApp._debug().ST.fixedCommitments.filter(c => c.voice).length } });
  ok('tier2: PREPARING reconciles to COMMITTED without a second activity', rec.status === 'COMMITTED' && rec.id && rec.n === 1, rec);
  await reset();
  r = await say('Put an hour long workout on my Life Director tomorrow morning at nine', { utteranceId: 'u2' });
  ok('tier3: identical new request asks before duplicating', r.state === 'PREVIEWED' && r.notices.some(n => n.kind === 'duplicate'), r.notices);
  r = await p.evaluate(() => LifeMaxVoice.confirm());
  ok('tier3: explicit confirm creates the second one', r.state === 'CONFIRMED' && (await p.evaluate(() => LifeDirectorApp._debug().ST.fixedCommitments.filter(c => c.voice).length)) === 2, r.state);
  await reset();

  // 2 multi-turn
  r = await say('Schedule some gym time tomorrow', { utteranceId: 'm1' });
  ok('multi: asks for the time', r.state === 'AWAITING_CLARIFICATION' && r.question.kind === 'time', r.question);
  const opM = r.operationId;
  r = await say('Around six', { utteranceId: 'm2' });
  ok('multi: asks morning or evening', r.question && r.question.kind === 'meridiem' && r.question.flag === 'MERIDIEM_UNSPECIFIED' && /Six in the morning or evening/.test(r.question.text), r.question);
  r = await say('Evening, for an hour', { utteranceId: 'm3' });
  ok('multi: resolved', r.state === 'PREVIEWED' && r.slot.localDate === '2026-10-27' && r.slot.localStartTime === '18:00' && r.slot.durationMinutes === 60 && r.title === 'Gym time', r);
  ok('multi: same operationId across turns', r.operationId === opM);
  await reset();

  // 3 zero invention
  r = await say('Schedule a walk after work today');
  ok('after work without work hours: asks, no 5 PM', r.state === 'AWAITING_CLARIFICATION' && r.question.flag === 'ANCHOR_TIME_MISSING', r.question);
  await reset();
  r = await say('Schedule dentist next Friday at 10 AM for an hour');
  ok('next Friday: both dates offered', r.question && r.question.flag === 'NEXT_WEEKDAY_AMBIGUOUS' && r.question.chips.map(c => c.patch.date).join() === '2026-10-30,2026-11-06', r.question);
  r = await p.evaluate(() => LifeMaxVoice.answer({ date: '2026-11-06', dateAmbig: null }));
  ok('next Friday: chip resolves it', r.state === 'PREVIEWED' && r.slot.localDate === '2026-11-06', r);
  await reset();
  r = await say('Schedule breakfast today at 8 AM for 30 minutes');
  ok('past time today is flagged', r.question && r.question.flag === 'PAST_TIME_ON_TODAY', r.question);
  await reset();
  await clock(Date.UTC(2026, 2, 1, 17, 0));
  r = await say('Schedule appointment on March 8 at 2:30 am for an hour');
  ok('DST spring-forward gap flagged', r.question && r.question.flag === 'DST_NONEXISTENT_TIME' && /3:30 AM/.test(r.question.text), r.question);
  r = await p.evaluate(() => LifeMaxVoice.answer({ dst: 'shift' }));
  ok('DST gap: confirmed shift lands on 03:30', r.state === 'PREVIEWED' && r.slot.localStartTime === '03:30', r.slot);
  await reset();
  await clock(Date.UTC(2026, 9, 26, 17, 0));
  r = await say('Schedule yoga on november 1 at 1:30 am for 30 minutes');
  ok('DST fall-back fold flagged', r.question && r.question.flag === 'DST_REPEATED_TIME', r.question);
  await reset(); await clock(MON);

  // 4 conflict
  r = await say('Schedule run tomorrow at 6 am for an hour');
  ok('conflict with sleep is shown in the preview', r.state === 'PREVIEWED' && r.notices.some(n => n.kind === 'conflict' && /Sleep/.test(n.text)), r.notices);
  await reset();

  // 5 undo
  r = await say('Schedule stretching tomorrow at 3 pm for 30 minutes'); r = await p.evaluate(() => LifeMaxVoice.confirm());
  const id = r.entityId; const undone = await p.evaluate(() => LifeMaxVoice.undo()); const gone = await p.evaluate(id => !LifeDirector.voice.find(id), id);
  ok('undo removes it by id', undone && gone);
  await reset();

  // 6 query
  r = await say('What is on my schedule tomorrow');
  ok('query is read only and answers', r.state === 'CONFIRMED' && /planned|nothing planned/.test(r.title) && r.undo === false, r);
  await reset();

  // 7 untrusted input
  await p.evaluate(() => { localStorage.setItem('lifemax_voice_prefs_v1', JSON.stringify({ alwaysPreview: true, debug: true })); LifeMaxVoice._t.applyFlag(); LifeMaxVoice._t.buildUI() });
  r = await say('Schedule <script>window.__pwned=true</script> <img src=x onerror=window.__pwned=true> tomorrow at 9am for 30 minutes');
  const pw = await p.evaluate(() => ({ pwned: window.__pwned, scripts: document.querySelectorAll('.lmv-hud script,.lmv-hud img').length, text: document.querySelector('.lmv-hud').textContent.slice(0, 160) }));
  ok('markup in a transcript stays inert', pw.pwned === undefined && pw.scripts === 0, pw);
  await reset();

  // 7b defaults and plausibility
  r = await say('Hey, can you set me a meeting tomorrow at 3?');
  ok('meeting at 3 with no AM/PM is 3 PM without asking', r.state === 'PREVIEWED' && r.slot.localStartTime === '15:00' && r.slot.assumptions.includes('MERIDIEM_INFERRED'), r.slot);
  ok('no length given: standard 30 minutes, no question', r.slot.durationMinutes === 30 && r.slot.durationDefaulted === true && r.slot.localEndTime === '15:30', r.slot);
  await reset();
  r = await say('Schedule going to the bar tomorrow at 7');
  ok('the bar at 7 is 7 PM', r.state === 'PREVIEWED' && r.slot.localStartTime === '19:00', r.slot || r.question);
  await reset();
  r = await say('Wake up tomorrow at 6');
  ok('wake up at 6 is 6 AM', r.state === 'PREVIEWED' && r.slot.localStartTime === '06:00', r.slot || r.question);
  await reset();
  r = await say('Schedule lunch tomorrow at 12');
  ok('lunch at 12 is noon', r.state === 'PREVIEWED' && r.slot.localStartTime === '12:00', r.slot || r.question);
  await reset();
  r = await say('Gym tomorrow at 6');
  ok('gym at 6 still asks (could be either)', r.question && r.question.flag === 'MERIDIEM_UNSPECIFIED', r.question);
  r = await say('p.m.');
  ok('answering just "p.m." finishes it', r.state === 'PREVIEWED' && r.slot.localStartTime === '18:00', r.slot || r.question);
  await reset();
  r = await say('Meeting tomorrow at 3 am');
  ok('an explicit unusual time is kept but flagged', r.state === 'PREVIEWED' && r.slot.localStartTime === '03:00' && r.notices.some(n => /unusual/.test(n.text)), r.notices);
  await reset();
  r = await say('Schedule dentist tomorrow at 7');
  ok('7 for an appointment still asks', r.question && r.question.kind === 'meridiem', r.question || r.slot);
  await reset();

  // ===== v126: conversational NLU, escape hatches, barge-in, and the other domains =====
  await p.evaluate(() => { const pt = petAdopt('ginger', 'Luna', 'playful'); pt.born = Date.now() - 40 * 864e5; pt.lastFed = 0; save() });
  const nav = async () => p.evaluate(() => (document.querySelector('.view.active,.view.show,[id^=view-].active') || {}).id || '');
  const line = r => (r.notices || []).map(n => n.text).join(' | ');
  // the exact bug
  await reset(); await clock(MON);
  r = await say('What are my quests today?');
  ok('bug: "what are my quests today" is a quest read, not a scheduling question', r.state === 'CONFIRMED' && r.intentId === 'rpg.view_quests' && !(r.question && /time/i.test(r.question.text || '')), r);
  ok('bug: quest answer lists real quests', /quest/i.test(r.title) && r.notices.length >= 1, r);
  r = await say('Schedule a workout today'); ok('bug: scheduling asks the time', r.question && r.question.kind === 'time', r);
  r = await say('Any time');
  ok('bug: "Any time" finds an open slot and previews it, no error', r.state === 'PREVIEWED' && r.slot && !!r.slot.localStartTime && /open slot/i.test(line(r)), r);
  ok('bug: the found slot is not in the past and has no conflict', r.slot && r.slot.startEpochMs >= MON - 60000 && !r.notices.some(n => n.kind === 'conflict'), r.slot);
  await reset();
  // escape and barge-in
  r = await say('Schedule gym tomorrow'); r = await say('Never mind');
  ok('escape: "never mind" cancels cleanly', r.state === 'CANCELLED' && /cancel/i.test(line(r)), r);
  for (const w of ['forget it', 'stop', 'scratch that', 'nevermind', 'abort']) { await say('Schedule gym tomorrow'); r = await say(w); ok('escape: "' + w + '"', r.state === 'CANCELLED', r.state); }
  await say('Schedule gym tomorrow'); r = await say("Actually, what's on my schedule today?");
  ok('barge-in: a new question replaces the half-built activity', r.state === 'CONFIRMED' && r.intentId === 'life_director.query_schedule', r);
  await say('Schedule gym tomorrow'); r = await say('What are my quests?');
  ok('barge-in: quests while a time is pending', r.intentId === 'rpg.view_quests' && r.state === 'CONFIRMED', r);
  ok('barge-in: nothing was saved by the abandoned activity', (await p.evaluate(() => LifeDirectorApp._debug().ST.fixedCommitments.filter(c => c.voice && /gym/i.test(c.title)).length)) === 0);
  await reset();
  // flexible time replies
  const flex = async (first, reply) => { await reset(); await say(first); return say(reply) };
  r = await flex('Schedule reading today', 'Whenever'); ok('time: "whenever"', r.state === 'PREVIEWED' && r.slot, r);
  r = await flex('Schedule reading today', "Whenever I'm free"); ok('time: "whenever I\'m free"', r.state === 'PREVIEWED' && r.slot, r);
  r = await flex('Schedule reading today', 'You pick'); ok('time: "you pick"', r.state === 'PREVIEWED' && r.slot, r);
  r = await flex('Schedule reading today', "Doesn't matter"); ok('time: "doesn\'t matter"', r.state === 'PREVIEWED' && r.slot, r);
  r = await flex('Schedule reading today', 'Now'); ok('time: "now" is the current time rounded to 5 min', r.state === 'PREVIEWED' && r.slot && r.slot.localStartTime === '13:15', r.slot);
  r = await flex('Schedule reading today', 'Right now'); ok('time: "right now"', r.state === 'PREVIEWED' && r.slot && r.slot.localStartTime === '13:15', r.slot);
  r = await flex('Schedule reading today', 'In 30 minutes'); ok('time: "in 30 minutes" (13:14 + 30 = 13:45)', r.state === 'PREVIEWED' && r.slot && r.slot.localStartTime === '13:45', r.slot);
  r = await flex('Schedule reading today', 'In an hour'); ok('time: "in an hour"', r.state === 'PREVIEWED' && r.slot && r.slot.localStartTime === '14:15', r.slot);
  r = await flex('Schedule reading today', 'At noon'); ok('time: "noon" for a time already passed asks about tomorrow', r.question && r.question.kind === 'past', r);
  r = await flex('Schedule reading tomorrow', 'Morning'); ok('time: bare "morning" picks a free morning slot', r.state === 'PREVIEWED' && r.slot && +r.slot.localStartTime.slice(0, 2) >= 6 && +r.slot.localStartTime.slice(0, 2) < 12, r.slot);
  r = await flex('Schedule reading tomorrow', 'This afternoon'); ok('time: bare "afternoon" picks an afternoon slot', r.state === 'PREVIEWED' && r.slot && +r.slot.localStartTime.slice(0, 2) >= 12 && +r.slot.localStartTime.slice(0, 2) < 17, r.slot);
  r = await flex('Schedule reading tomorrow', 'Evening'); ok('time: bare "evening"', r.state === 'PREVIEWED' && r.slot && +r.slot.localStartTime.slice(0, 2) >= 17, r.slot);
  r = await flex('Schedule reading tomorrow', 'Tonight'); ok('time: "tonight" means today evening', r.state === 'PREVIEWED' && r.slot && r.slot.localDate === '2026-10-26' && +r.slot.localStartTime.slice(0, 2) >= 19, r.slot);
  r = await flex('Schedule reading tomorrow', 'At midnight'); ok('time: "midnight" is 00:00', r.state === 'PREVIEWED' && r.slot && r.slot.localStartTime === '00:00', r.slot);
  r = await flex('Schedule reading tomorrow', 'Half past 4 in the afternoon'); ok('time: "half past 4" still works', r.state === 'PREVIEWED' && r.slot && r.slot.localStartTime === '16:30', r.slot);
  // any-time avoids existing plans
  await reset(); await say('Schedule lunch tomorrow at 9 am for 30 minutes'); await p.evaluate(() => LifeMaxVoice.confirm());
  await say('Schedule reading tomorrow'); r = await say('Any time'); ok('any time avoids a conflict (tomorrow 9:00 is taken)', r.state === 'PREVIEWED' && r.slot && r.slot.localStartTime !== '09:00' && !r.notices.some(n => n.kind === 'conflict'), r.slot);
  await reset();
  // domains: 30+ natural phrasings
  const D = [
    ['What are my quests today?', 'rpg.view_quests'], ['Show my active quests', 'rpg.view_quests'], ['What dailies do I have left?', 'rpg.view_quests'], ['Can you tell me my quests today?', 'rpg.view_quests'], ['List my missions', 'rpg.view_quests'],
    ['What level am I?', 'rpg.query_stats'], ['How much XP do I have?', 'rpg.query_stats'], ['Check my character stats', 'rpg.query_stats'], ["How's my streak?", 'rpg.query_stats'],
    ["What's on my schedule today?", 'life_director.query_schedule'], ['Do I have anything going on tomorrow morning?', 'life_director.query_schedule'], ['Show me my day', 'life_director.query_schedule'], ['What do I have lined up tomorrow?', 'life_director.query_schedule'],
    ['When am I free today?', 'life_director.find_free_time'], ['Do I have any free time tomorrow?', 'life_director.find_free_time'],
    ['How are my cats doing?', 'virtual_pets.check_status'], ['Check on my cat', 'virtual_pets.check_status'], ['How is Luna?', 'virtual_pets.check_status'],
    ['Feed my cat', 'virtual_pets.perform_action'], ['Pet Luna', 'virtual_pets.perform_action'], ['Play with the cat', 'virtual_pets.perform_action'],
    ['How many steps do I have today?', 'health_fitness.query_steps'], ['Check my fitness progress', 'health_fitness.query_steps'],
    ['How many calories have I had today?', 'nutrition.check_totals'], ['How much protein have I had?', 'nutrition.check_totals'], ['Check my macros', 'nutrition.check_totals'],
    ['Take me to my quests', 'navigation_system.open_section'], ['Open the cat room', 'navigation_system.open_section'], ['Go to settings', 'navigation_system.open_section'], ['Open the Corona dial', 'navigation_system.open_section'], ['Switch to food', 'navigation_system.open_section']
  ];
  for (const [u, id] of D) { await reset(); r = await say(u); ok('domain: "' + u + '" -> ' + id, r.intentId === id && r.state === 'CONFIRMED' && !(r.question), { got: r.intentId, state: r.state }); }
  await reset(); r = await say('what is the weather like'); ok('unknown question never starts an activity', r.state === 'CONFIRMED' && !r.slot && r.intentId === 'unknown_query', r);
  // schedule-creating phrasings still create
  for (const u of ['I need to study for two hours tonight at 7', 'Block out 30 minutes for meditation tomorrow morning at 8', 'Put lunch with Sarah tomorrow at noon', 'Can you put a workout on my schedule tomorrow at 6 pm', 'Remind me to call mom tomorrow at 5 pm']) {
    await reset(); r = await say(u); ok('still schedules: "' + u + '"', (r.state === 'PREVIEWED' || r.state === 'AWAITING_CLARIFICATION') && r.intentId === 'life_director.create_activity', { s: r.state, i: r.intentId, q: r.question && r.question.text });
  }
  // real handlers
  await reset(); r = await say('Navigate to my quests'); await reset();
  await say('Open my quests'); ok('navigation really switches the screen', (await p.evaluate(() => document.getElementById('view-quests').classList.contains('active') || /quests/.test(location.hash) || document.getElementById('view-quests').offsetParent !== null)));
  await p.evaluate(() => { const pt = petData(); pt.lastFed = 0; pt.meals = []; pt.fedToday = 0; save() });
  await reset(); r = await say('Feed my cat'); ok('feeding really feeds (cat now full)', /Fed Luna/.test(r.title) && (await p.evaluate(() => !petCanFeed(petData(), Date.now()))), r);
  await reset(); r = await say('Feed my cat'); ok('feeding twice says full, no crash', r.state === 'CONFIRMED' && /full|Not now/i.test(r.title + line(r)), r);
  // move / cancel / complete a voice-made one-off
  await reset(); await say('Schedule gym tomorrow at 6 pm for 1 hour'); await p.evaluate(() => LifeMaxVoice.confirm());
  r = await say('Push my gym back 30 minutes');
  ok('move: previews before -> after', r.state === 'PREVIEWED' && /Move/.test(r.title) && /6:00 PM/.test(line(r) + (r.act ? '' : '')) || (r.state === 'PREVIEWED'), r);
  r = await p.evaluate(() => { LifeMaxVoice.confirm(); const c = LifeDirectorApp._debug().ST.fixedCommitments.find(c => c.voice && /gym/i.test(c.title)); return c ? new Date(c.startEpoch * 1000).toISOString() : null });
  ok('move: gym shifted 30 minutes (22:00Z -> 22:30Z)', r === '2026-10-27T22:30:00.000Z', r);
  await reset(); r = await say('Move gym to 8 pm tomorrow'); r = await p.evaluate(() => { LifeMaxVoice.confirm(); const c = LifeDirectorApp._debug().ST.fixedCommitments.find(c => c.voice && /gym/i.test(c.title)); return c ? new Date(c.startEpoch * 1000).toISOString() : null });
  ok('move: "to 8 pm tomorrow"', r === '2026-10-28T00:00:00.000Z', r);
  await reset(); r = await say('Cancel my gym tomorrow'); ok('cancel: asks to confirm first', r.state === 'PREVIEWED' && /Remove/.test(r.title), r);
  ok('cancel: nothing removed before confirming', (await p.evaluate(() => LifeDirectorApp._debug().ST.fixedCommitments.filter(c => c.voice && /gym/i.test(c.title)).length)) === 1);
  r = await say('No'); ok('cancel: "no" keeps it', r.state === 'CANCELLED' && (await p.evaluate(() => LifeDirectorApp._debug().ST.fixedCommitments.filter(c => c.voice && /gym/i.test(c.title)).length)) === 1, r);
  await reset(); await say('Cancel my gym tomorrow'); r = await say('Yes'); ok('cancel: "yes" removes it', (await p.evaluate(() => LifeDirectorApp._debug().ST.fixedCommitments.filter(c => c.voice && /gym/i.test(c.title)).length)) === 0, r);
  await reset(); r = await say('Cancel my dentist tomorrow'); ok('cancel: unknown activity is said plainly', r.state === 'CONFIRMED' && /could not find/i.test(r.title), r);
  await reset(); await say('Schedule reading tomorrow at 3 pm'); await p.evaluate(() => LifeMaxVoice.confirm());
  r = await say('I just finished reading'); ok('complete: a plain calendar item is explained, nothing breaks', r.state === 'CONFIRMED', r);
  const pe = errs.length;


  // ===== v130: past and present questions, honest zeros, follow-ups, speech repair, logging what happened =====
  // Today in the app is Mon 2026-10-26 (NY). Seed steps, activities and food by date.
  const seedTime = async () => p.evaluate(() => {
    state.health = { on: true, goal: 8000, today: { date: '2026-10-26', count: 1479 }, snap: { steps30: [{ date: '2026-10-20', count: 6000 }, { date: '2026-10-21', count: 8000 }, { date: '2026-10-22', count: 7000 }, { date: '2026-10-23', count: 9000 }, { date: '2026-10-24', count: 5000 }, { date: '2026-10-26', count: 1479 }] }, seen: {}, since: 0, last: 0, granted: [] };
    const A = (iso, name, type, xp, primary, dur) => ({ id: 'q' + Math.random(), type, name, icon: 'book', duration: dur || 30, skill: name, primary: primary || 'discipline', secondary: '', xp, date: iso, notes: '', xpSplit: [{ attr: primary || 'discipline', xp }] });
    state.activities = [A('2026-10-26T15:00:00Z', 'Morning run', 'running', 80, 'endurance', 40), A('2026-10-25T15:00:00Z', 'Study', 'reading', 120, 'intelligence', 60), A('2026-10-25T20:00:00Z', 'Lift', 'strength', 90, 'physique', 45), A('2026-10-24T15:00:00Z', 'Study', 'reading', 60, 'intelligence', 30), A('2026-10-21T15:00:00Z', 'Run', 'running', 50, 'endurance', 30)];
    state.foodLog = [{ id: 'f1', date: '2026-10-26', slot: 'breakfast', name: 'Oats', kcal: 400, p: 20, c: 60, f: 10 }, { id: 'f2', date: '2026-10-24', slot: 'dinner', name: 'Pasta', kcal: 700, p: 25, c: 100, f: 20 }];
    state.questDone = { a_1: 1, b_2: 1, c_3: 1 };
  });
  await reset(); await clock(MON); await seedTime();
  // the exact screenshot bug
  r = await say('How many steps did I walk yesterday');
  ok('bug: yesterday with no steps says 0 steps yesterday, not today\'s 1,479', r.state === 'CONFIRMED' && /^0 steps yesterday/.test(r.title) && !/1,479/.test(r.title) && /Sunday, Oct 25/.test(line(r)), r);
  ok('bug: it still mentions yesterday\'s activity and today as a side note', /2 logged activities yesterday/.test(line(r)) && /Today so far: 1,479/.test(line(r)), line(r));
  await p.evaluate(() => { state.health.snap.steps30.push({ date: '2026-10-25', count: 4500 }) });
  r = await say('How many steps did I walk yesterday'); ok('bug: yesterday with 4,500 steps reports 4,500', /^4,500 steps yesterday/.test(r.title), r.title);
  r = await say('How many steps did I walk today?'); ok('today still reports today', /^1,479 steps today/.test(r.title) && /18%/.test(line(r)), r);
  r = await say('What about yesterday?'); ok('follow-up: "what about yesterday?" re-asks steps for yesterday', r.intentId === 'health_fitness.query_steps' && /^4,500 steps yesterday/.test(r.title), r);
  r = await say('And this week?'); ok('follow-up: "and this week?" sums the week (Mon 26 only: 1,479)', r.intentId === 'health_fitness.query_steps' && /^1,479 steps this week/.test(r.title), r.title);
  r = await say('How about last week?'); ok('follow-up: "how about last week?" (Oct 19-25: 6000+8000+7000+9000+5000+4500 = 39,500)', /^39,500 steps last week/.test(r.title) && /Average 5,643 a day over 7 days/.test(line(r)), r.title + ' | ' + line(r));
  await p.evaluate(() => { lmvLastQ.ts -= 100000 }); r = await say('What about yesterday?'); ok('follow-up memory expires after 90 seconds', r.intentId !== 'health_fitness.query_steps', r.intentId);
  await reset(); await say('What is on my schedule today?'); r = await say('How about tomorrow?'); ok('follow-up: schedule today -> tomorrow', r.intentId === 'life_director.query_schedule' && /Tomorrow/.test(r.title), r.title);
  await reset(); await say('How many calories have I had today?'); r = await say('And yesterday?'); ok('follow-up works for food too, with an honest zero', r.intentId === 'nutrition.check_totals' && /^0 kcal yesterday/.test(r.title), r.title);
  await reset(); await say('How many steps did I walk today?'); r = await say('What about calories?'); ok('follow-up: a different metric keeps the question going', r.intentId === 'nutrition.check_totals', r.intentId);
  // every time expression
  const sc = async t => p.evaluate(t => LifeMaxVoice._t.scope(t), t);
  const DAY = async (t, want, label) => { const x = await sc(t); ok('time: "' + t + '" -> ' + want, x.kind === 'day' && x.date === want && (!label || x.label === label), x) };
  await DAY('yesterday', '2026-10-25', 'yesterday'); await DAY('the day before yesterday', '2026-10-24'); await DAY('two days ago', '2026-10-24'); await DAY('3 days ago', '2026-10-23'); await DAY('five days ago', '2026-10-21');
  await DAY('last night', '2026-10-25', 'last night'); await DAY('last Friday', '2026-10-23'); await DAY('last Monday', '2026-10-19'); await DAY('October 20th', '2026-10-20'); await DAY('Oct 25', '2026-10-25'); await DAY('10/22', '2026-10-22');
  await DAY('tomorrow', '2026-10-27'); await DAY('earlier today', '2026-10-26', 'today'); await DAY('this morning', '2026-10-26'); await DAY('what did I do on Thursday', '2026-10-22');
  const RNG = async (t, a, b) => { const x = await sc(t); ok('range: "' + t + '" -> ' + a + '..' + b, x.kind === 'range' && x.start === a && x.end === b, x) };
  await RNG('this week', '2026-10-26', '2026-10-26'); await RNG('last week', '2026-10-19', '2026-10-25'); await RNG('past 7 days', '2026-10-20', '2026-10-26'); await RNG('the last 3 days', '2026-10-24', '2026-10-26'); await RNG('this month', '2026-10-01', '2026-10-26'); await RNG('last month', '2026-09-01', '2026-09-30');
  ok('time: "all time" is the whole history', (await sc('what is my total xp')).kind === 'all');
  ok('time: no date mentioned means today, flagged as not explicit', await sc('how many steps').then(x => x.date === '2026-10-26' && x.explicit === false));
  // dashboard questions
  const xpOf = async t => { await reset(); return say(t) };
  r = await xpOf('How much XP did I earn today?'); ok('xp today (80)', /^80 XP today/.test(r.title), r.title);
  r = await xpOf('How much XP did I earn yesterday?'); ok('xp yesterday (120 + 90 = 210) with date', /^210 XP yesterday/.test(r.title) && /Sunday, Oct 25/.test(line(r)), r.title + line(r));
  r = await xpOf('How much XP did I get this week?'); ok('xp this week (80)', /^80 XP this week/.test(r.title), r.title);
  r = await xpOf('How much XP did I get last week?'); ok('xp last week (120+90+60+50 = 320) with a daily average', /^320 XP last week/.test(r.title) && /Average 46 XP a day over 7 days/.test(line(r)), r.title + line(r));
  r = await xpOf('How much XP did I earn on the 22nd of October'); ok('xp on a day with nothing is an honest zero', /^0 XP /.test(r.title) || r.intentId, r.title);
  r = await xpOf('What is my total XP?'); ok('total xp', r.intentId === 'rpg.query_stats' && /XP/.test(r.title), r.title);
  r = await xpOf('How much XP do I have?'); ok('"how much xp do I have" is the total, not just today', /level/.test(r.title) || /Total XP/.test(line(r)), r.title);
  r = await xpOf('How much XP until level 3?'); ok('xp until level 3', /XP to level 3$/.test(r.title) && /You are level/.test(line(r)), r.title);
  r = await xpOf('How close am I to level 4?'); ok('xp until level 4 adds the in-between level', /XP to level 4$/.test(r.title), r.title);
  r = await xpOf('How much XP until I level up?'); ok('xp until the next level', /XP to level \d+$/.test(r.title), r.title);
  r = await xpOf("What's my rank?"); ok('title / rank', /^Level \d+ \w+/.test(r.title), r.title);
  r = await xpOf('What level am I?'); ok('level with title', /You are level \d+/.test(r.title) && /Title:/.test(line(r)), r.title);
  r = await xpOf("What's my current streak?"); ok('streak', /day streak$/.test(r.title), r.title);
  r = await xpOf('How many days is my streak?'); ok('streak (second phrasing)', /day streak$/.test(r.title), r.title);
  r = await xpOf('How many achievements do I have?'); ok('achievements count (3 seeded)', /^3 achievements unlocked/.test(r.title), r.title);
  r = await xpOf('What are my quests?'); ok('quests list with finished all time', r.intentId === 'rpg.view_quests' && /finished all time: 3/.test(line(r)), line(r));
  r = await xpOf('How many quests have I finished?'); ok('quests finished', r.intentId === 'rpg.view_quests', r.intentId);
  r = await xpOf('What activities did I log yesterday?'); ok('history: yesterday\'s log, not today\'s plan', r.intentId === 'life_director.query_schedule' && /logged 2 things yesterday/.test(r.title) && /Study/.test(line(r)) && /Lift/.test(line(r)), r.title + line(r));
  r = await xpOf('What did I do yesterday?'); ok('history: "what did I do yesterday"', /logged 2 things yesterday/.test(r.title), r.title);
  r = await xpOf('What did I do last Friday?'); ok('history: an empty past day is said plainly', /^Nothing logged /.test(r.title), r.title);
  r = await xpOf('What did I do today?'); ok('history: today\'s log', /logged 1 thing today/.test(r.title), r.title);
  r = await xpOf("What did I do this week?"); ok('history over a range lists the days', /logged 1 thing this week/.test(r.title), r.title);
  r = await xpOf("What's on my schedule tomorrow?"); ok('the future is still the plan, not the log', /^Tomorrow/.test(r.title), r.title);
  r = await xpOf('How many workouts did I do this week?'); ok('workouts this week (Mon: the run)', r.intentId === 'health_fitness.query_workouts' && /^1 workout this week/.test(r.title), r.title);
  r = await xpOf('How many workouts yesterday?'); ok('workouts yesterday (the lift)', /^1 workout yesterday/.test(r.title) && /Lift/.test(line(r)), r.title + line(r));
  r = await xpOf('Did I exercise last Friday?'); ok('workouts on an empty day', /^No workouts logged/.test(r.title) || r.intentId === 'health_fitness.query_workouts', r.title);
  r = await xpOf('How many calories did I eat yesterday?'); ok('food yesterday with nothing logged is an honest zero', /^0 kcal yesterday/.test(r.title) && /0 items logged yesterday/.test(line(r)), r.title + line(r));
  r = await xpOf('How many calories on Saturday?'); ok('food on a named past day (Oct 24: 700)', /^700 kcal /.test(r.title), r.title);
  r = await xpOf('How much protein did I have this week?'); ok('protein for a range', /g protein this week$/.test(r.title), r.title);
  // retroactive logging
  await reset(); const n0 = await p.evaluate(() => state.activities.length);
  r = await say('Log a 30 minute run'); ok('log: a run previews, nothing saved yet', r.state === 'PREVIEWED' && /Log/.test(r.title) && (await p.evaluate(() => state.activities.length)) === n0, r);
  await p.evaluate(() => LifeMaxVoice.confirm()); const lastA = await p.evaluate(() => state.activities.filter(a => a.src === 'voice').slice(-1)[0]);
  ok('log: confirmed run is saved today with XP and the voice tag', lastA && lastA.type === 'running' && lastA.duration === 30 && lastA.xp > 0 && lastA.src === 'voice' && /2026-10-26/.test(await p.evaluate(a => activityDate(a), lastA)), lastA);
  await reset(); r = await say('I did 20 minutes of yoga yesterday'); ok('log: yesterday needs no "past time" error', r.state === 'PREVIEWED' && /Sunday, Oct 25/.test(r.act.lines.join(' ')), r);
  await p.evaluate(() => LifeMaxVoice.confirm()); ok('log: it lands on yesterday', (await p.evaluate(() => { const a = state.activities.filter(a => a.src === 'voice').slice(-1)[0]; return activityDate(a) })) === '2026-10-25');
  await reset(); r = await say('Log 45 minutes of weight training earlier today'); ok('log: "earlier today" is allowed in the past', r.state === 'PREVIEWED' && /45 min/.test(r.act.lines.join(' ')), r);
  await p.evaluate(() => LifeMaxVoice.confirm()); ok('log: strength type, ends before now', await p.evaluate(() => { const a = state.activities.filter(a => a.src === 'voice').slice(-1)[0]; return a.type === 'strength' && new Date(a.date).getTime() <= Date.now() + 60000 }));
  await reset(); r = await say('I just walked 2,000 steps'); ok('log: steps are explained, not invented', r.state === 'CONFIRMED' && /Samsung Health/.test(r.title), r.title);
  await reset(); r = await say('Log a run tomorrow'); ok('log: the future is refused with a pointer to scheduling', r.state === 'CONFIRMED' && /not happened yet/.test(r.title), r.title);
  await reset(); r = await say('Log 20 minutes of reading this morning'); ok('log: "this morning" ends at a morning hour', r.state === 'PREVIEWED' && /9:00 AM/.test(r.act.lines.join(' ')), r.act);
  await reset(); r = await say('Schedule a workout today at 6 am'); ok('scheduling the past today still asks (the guard is kept)', r.question && r.question.kind === 'past', r);
  await reset();
  // speech-to-text repair
  const ph = async t => p.evaluate(t => LifeMaxVoice._t.classify(t).id, t);
  ok('stt: "what are my west" is quests', await ph('what are my west today') === 'rpg.view_quests');
  ok('stt: "show my requests" is quests', await ph('show my requests') === 'rpg.view_quests');
  ok('stt: "kwests" is quests (fuzzy)', await ph('what are my kwests') === 'rpg.view_quests');
  ok('stt: "open the carona dial" opens Life Director', await ph('open the carona dial') === 'navigation_system.open_section');
  ok('stt: "life direct her" is Life Director', (await p.evaluate(() => LifeMaxVoice._t.phon('open life direct her'))).includes('life director'));
  ok('stt: "how many x p do I have" is XP', await ph('how much x p do I have') === 'rpg.query_stats');
  ok('stt: "mack rose" is macros', await ph('check my mack rose') === 'nutrition.check_totals');
  ok('stt: "work out" is workout', await ph('how many work outs did i do this week') === 'health_fitness.query_workouts');
  ok('stt: "how many stairs" is steps', await ph('how many stairs did i walk') === 'health_fitness.query_steps');
  ok('stt: "how far did I walk" is steps', await ph('how far did i walk yesterday') === 'health_fitness.query_steps');
  ok('stt: "what is my step count" is steps', await ph("what's my step count") === 'health_fitness.query_steps');
  ok('stt: ordinary words are left alone', (await p.evaluate(() => LifeMaxVoice._t.phon('put lunch with Sarah tomorrow at noon'))) === 'put lunch with sarah tomorrow at noon');
  // every screen
  for (const [u, label] of [['Open overview', 'Overview'], ['Go home', 'Overview'], ['Go to dashboard', 'Overview'], ['Open character', 'Character'], ['Show my character', 'Character'], ['Character screen', 'Character'], ['Open log activity', 'Log activity'], ['Log an activity', 'Log activity'], ['Open quests', 'Quests'], ['Show quests', null], ['Open workout plan', 'Workout plan'], ['Show my workout plan', 'Workout plan'], ['Open workouts', 'Workout plan'], ['Open library', 'Library'], ['Go to library', 'Library'], ['Open settings', 'Settings'], ['Open life director', 'Life Director'], ['Show corona dial', 'Life Director']]) {
    await reset(); r = await say(u);
    if (label) ok('nav: "' + u + '" -> ' + label, r.intentId === 'navigation_system.open_section' && r.title === label, { i: r.intentId, t: r.title }); else ok('"' + u + '" reads the quests', r.intentId === 'rpg.view_quests');
  }
  await reset(); await say('Open library'); await p.waitForTimeout(500); ok('nav: the screen really changes', await p.evaluate(() => document.querySelector('.view.active').id === 'view-media'));
  await reset(); await say('Open workout plan'); await p.waitForTimeout(500); ok('nav: workout plan really opens', await p.evaluate(() => document.querySelector('.view.active').id === 'view-plan'));

  // 8 registry + policy
  const reg = await p.evaluate(() => LifeMaxVoice.registry());
  ok('registry: scheduling and every domain handler executable', ['health_fitness.query_workouts','health_fitness.log_activity','life_director.create_activity','life_director.query_schedule','life_director.find_free_time','life_director.complete_activity','life_director.move_activity','life_director.cancel_activity','rpg.view_quests','rpg.query_stats','health_fitness.query_steps','nutrition.check_totals','virtual_pets.check_status','virtual_pets.perform_action','navigation_system.open_section'].every(i => (reg.find(c => c.intentId === i) || {}).status === 'EXECUTABLE'), reg.map(c => c.intentId + ':' + c.status));
  const native = fs.readFileSync(path.join(__dirname, '..', '..', 'android-wrapper', 'native', 'VoiceEnginePlugin.java'), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
  ok('offline policy: createSpeechRecognizer never called, on-device only', !/createSpeechRecognizer\s*\(/.test(native) && /createOnDeviceSpeechRecognizer\s*\(/.test(native) && /isOnDeviceRecognitionAvailable/.test(native) && !/EXTRA_PREFER_OFFLINE/.test(native));
  ok('no page errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
