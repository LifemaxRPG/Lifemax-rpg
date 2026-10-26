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
  ok('query is read only and answers', r.state === 'CONFIRMED' && /Schedule for/.test(r.title) && r.undo === false, r);
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

  // 8 registry + policy
  const reg = await p.evaluate(() => LifeMaxVoice.registry());
  ok('registry: create_activity executable, future domains planned', reg.find(c => c.intentId === 'life_director.create_activity').status === 'EXECUTABLE' && reg.filter(c => /\.\*$/.test(c.intentId)).every(c => c.status === 'PLANNED_NOT_IMPLEMENTED'), reg);
  const native = fs.readFileSync(path.join(__dirname, '..', '..', 'android-wrapper', 'native', 'VoiceEnginePlugin.java'), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
  ok('offline policy: createSpeechRecognizer never called, on-device only', !/createSpeechRecognizer\s*\(/.test(native) && /createOnDeviceSpeechRecognizer\s*\(/.test(native) && /isOnDeviceRecognitionAvailable/.test(native) && !/EXTRA_PREFER_OFFLINE/.test(native));
  ok('no page errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
