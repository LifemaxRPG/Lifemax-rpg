/* LifeMax Voice generation 4 tests: self-corrections, two things at once, editing the card on screen, "right after that",
   briefing / what's next / goals / suggestions, comparisons, plan, library, timer, other apps, help, shortcuts.
   Run:  NODE_PATH=$(npm root -g) node tests/voice4.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', fs.readdirSync(path.join(__dirname, '..', 'dist')).filter(f => /^Life_Max_RPG_v\d+\.html$/.test(f)).sort((a, b) => +a.match(/\d+/)[0] - +b.match(/\d+/)[0]).pop()));
let pass = 0, fail = 0; const ok = (name, cond, extra) => { if (cond) { pass++; console.log('PASS ' + name) } else { fail++; console.log('FAIL ' + name + (extra !== undefined ? '  ' + JSON.stringify(extra).slice(0, 600) : '')) } };
const MON = Date.UTC(2026, 9, 26, 17, 14);   // Mon 2026-10-26 13:14 EDT
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); const errs = [];
  p.on('pageerror', e => errs.push(String(e)));
  await p.goto('file://' + file); await p.waitForTimeout(800);
  await p.evaluate(() => { const o = document.getElementById('onboarding'); if (o) { o.classList.remove('show'); o.style.display = 'none' } localStorage.removeItem('lifemax_voice_op_journal_v1'); localStorage.removeItem('lifemax_voice_timer_v1'); localStorage.removeItem('lifemax_voice_alias_v1') });
  await p.evaluate(() => { const { ST } = LifeDirectorApp._debug(); ST.availability.timezone = 'America/New_York'; ST.availability.rules.push({ id: 'sl', kind: 'SLEEP', title: 'Sleep', days: [0, 1, 2, 3, 4, 5, 6], startMin: 23 * 60, durationMin: 480, commuteMin: 0 }); ST.setupComplete = true; LifeDirector.regenerate('test') });
  await p.evaluate(() => LifeMaxVoice._t.buildUI());
  const say = (t, o) => p.evaluate(([t, o]) => LifeMaxVoice.simulateUtterance(t, o), [t, o || {}]);
  const clock = now => p.evaluate(([now]) => LifeMaxVoice.setClock({ now, tz: 'America/New_York' }), [now]);
  const reset = () => p.evaluate(() => LifeMaxVoice.reset());
  const confirm = () => p.evaluate(() => LifeMaxVoice.confirm());
  const text = r => [r.title, ...(r.notices || []).map(n => n.text), ...((r.act && r.act.lines) || [])].join(' | ');
  await clock(MON);
  const seed = () => p.evaluate(() => {
    const A = (iso, name, type, xp, primary, dur) => ({ id: 'q' + Math.random(), type, name, icon: 'book', duration: dur || 30, skill: name, primary: primary || 'discipline', secondary: '', xp, date: iso, notes: '', xpSplit: [{ attr: primary || 'discipline', xp }] });
    state.activities = [A('2026-10-26T15:00:00Z', 'Morning run', 'running', 80, 'endurance', 40), A('2026-10-25T15:00:00Z', 'Study', 'reading', 120, 'intelligence', 60), A('2026-10-24T15:00:00Z', 'Study', 'reading', 60, 'intelligence', 30)];
  });
  const clearVoice = () => p.evaluate(() => { const { ST } = LifeDirectorApp._debug(); ST.fixedCommitments = (ST.fixedCommitments || []).filter(c => !c.voice); LifeDirector.regenerate('test') });
  const pace = () => p.evaluate(() => ovPace(ovMap()));
  await p.evaluate(() => {
    state.health = { on: true, goal: 8000, today: { date: '2026-10-26', count: 1479 }, snap: { steps30: [{ date: '2026-10-20', count: 6000 }, { date: '2026-10-21', count: 8000 }, { date: '2026-10-22', count: 7000 }, { date: '2026-10-23', count: 9000 }, { date: '2026-10-24', count: 5000 }, { date: '2026-10-26', count: 1479 }] }, seen: {}, since: 0, last: 0, granted: [] };
    const A = (iso, name, type, xp, primary, dur) => ({ id: 'q' + Math.random(), type, name, icon: 'book', duration: dur || 30, skill: name, primary: primary || 'discipline', secondary: '', xp, date: iso, notes: '', xpSplit: [{ attr: primary || 'discipline', xp }] });
    state.activities = [A('2026-10-26T15:00:00Z', 'Morning run', 'running', 80, 'endurance', 40), A('2026-10-25T15:00:00Z', 'Study', 'reading', 120, 'intelligence', 60), A('2026-10-24T15:00:00Z', 'Study', 'reading', 60, 'intelligence', 30)];
    state.mediaItems = [{ id: 'm1', title: 'Dune', creator: 'Frank Herbert', type: 'book', status: 'finished', notes: '' }, { id: 'm2', title: 'Cooking', creator: 'x', type: 'book', status: 'unread', notes: '' }];
  });

  // ===== 1. self-correction =====
  let r = await say('Schedule a workout tomorrow at 8 AM, wait no make it 9 AM for 45 minutes');
  ok('repair: later time and length win, the title stays', r.state === 'PREVIEWED' && r.slot && r.slot.localStartTime === '09:00' && r.slot.durationMinutes === 45 && r.title === 'Workout' && r.slot.localDate === '2026-10-27', r.slot);
  await reset(); r = await say('Log 20 minutes of reading, actually make that 45 minutes');
  ok('repair: log, "actually make that 45 minutes" replaces 20', r.act && /45 min/.test(text(r)) && !/\b20 min/.test(text(r)), text(r));
  await reset(); r = await say('Put gym on Thursday, sorry I meant Friday at 6 PM');
  ok('repair: a new day and time replace the old day', r.slot && r.slot.localDate === '2026-10-30' && r.slot.localStartTime === '18:00' && r.title === 'Gym', r.slot || r);
  await reset(); r = await say('Schedule yoga tomorrow at 7 AM for an hour, no wait, make it 30 minutes');
  ok('repair: only the length changes, time and day stay', r.slot && r.slot.durationMinutes === 30 && r.slot.localStartTime === '07:00' && r.slot.localDate === '2026-10-27', r.slot);
  await reset(); r = await say('Schedule a workout tomorrow at 9 AM');
  ok('repair: a plain sentence is left alone', r.slot && r.slot.localStartTime === '09:00' && r.title === 'Workout');

  // ===== 2. two things at once =====
  const sp = t => p.evaluate(t => LifeMaxVoiceG4.split(t), t);
  ok('split: never inside "stretching and mobility"', await sp('Stretching and mobility tomorrow at 9 AM for 30 minutes') === null);
  ok('split: never inside "peanut butter and jelly"', await sp('Put peanut butter and jelly tomorrow at noon') === null);
  ok('split: never inside "between 2 and 4 PM"', await sp('Schedule lunch between 2 and 4 PM') === null);
  ok('split: never inside "an hour and a half"', await sp('Schedule a study block tomorrow at 4 PM for an hour and a half') === null);
  const s1 = await sp('Complete my workout and check my quests'); ok('split: two clear requests', s1 && /^Complete my workout/.test(s1.a) && /^check my quests/i.test(s1.b), s1);
  await reset(); const byVoice = () => p.evaluate(() => state.activities.filter(a => a.notes === 'Logged by voice').length); const nBefore = await byVoice();
  r = await say('Log a 30 minute walk and check my XP');
  ok('compound: the first request waits for a yes', r.state === 'PREVIEWED' && r.act && /30 min/.test(text(r)), r);
  r = await confirm();
  ok('compound: both done, one card', r.state === 'CONFIRMED' && r.title === 'Did 2 things' && /Logged/.test(text(r)) && /XP/.test(text(r)), text(r));
  ok('compound: the walk really was logged once', (await byVoice()) === nBefore + 1);
  await reset(); r = await say('Stretching and mobility tomorrow at 9 AM for 30 minutes');
  ok('compound: "stretching and mobility" stays one activity', r.state === 'PREVIEWED' && r.slot && r.slot.localStartTime === '09:00' && /Stretching/.test(r.title) && /mobility/i.test(r.title), r.title);
  await reset(); r = await say('How many steps do I have and what is my level');
  ok('compound: two questions answered on one card', r.state === 'CONFIRMED' && r.title === 'Did 2 things' && /1,479/.test(text(r)) && /level/i.test(text(r)), text(r));
  await reset(); r = await say('Schedule coding tomorrow at 2 PM for an hour and a 15 minute walk at 3:30 PM');
  ok('compound: first scheduling request previewed', r.state === 'PREVIEWED' && r.title === 'Coding' && r.slot.localStartTime === '14:00', r.slot);
  r = await confirm();
  ok('compound: the second one follows, on the same day', r.state === 'PREVIEWED' && r.slot && r.slot.localStartTime === '15:30' && r.slot.durationMinutes === 15 && r.slot.localDate === '2026-10-27' && /Walk/.test(r.title), r.slot || r);
  r = await confirm();
  ok('compound: both scheduled', r.state === 'CONFIRMED' && r.title === 'Did 2 things', r.title);
  await reset(); r = await say('Log a 20 minute run and check my XP'); r = await p.evaluate(() => LifeMaxVoice.cancel());
  ok('compound: cancelling the first drops the second', r.state === 'CANCELLED');

  // ===== 3. briefing, what is next, goals, suggestions =====
  await seed(); await clearVoice(); const PACE = await pace();
  await reset(); r = await say('How am I doing today?');
  ok('briefing: routed', r.intentId === 'assistant.daily_briefing', r.intentId);
  ok('briefing: XP, streak, steps, quests, next', new RegExp('XP today: 80 / ' + PACE).test(text(r)) && /day streak/.test(text(r)) && /Steps: 1,479 \/ 8,000/.test(text(r)) && /Quests:/.test(text(r)) && /Nothing else scheduled|Next:|Now:/.test(text(r)), text(r));
  for (const u of ['Give me my daily briefing', 'Status report', 'Catch me up', "How's my day looking?"]) { await reset(); r = await say(u); ok('briefing: "' + u + '"', r.intentId === 'assistant.daily_briefing', r.intentId) }
  await reset(); await say('Schedule dentist today at 3 PM for 30 minutes'); await confirm();
  await reset(); r = await say("What's next?");
  ok('next: names the next activity and how far away it is', r.intentId === 'life_director.whats_next' && /Next: Dentist at 3:00 PM, in 1 h 46 min/.test(text(r)), text(r));
  await clock(Date.UTC(2026, 9, 26, 19, 10));
  await reset(); r = await say('What am I supposed to be doing right now?');
  ok('next: in the middle of one, says so and how long is left', /Right now: Dentist/.test(text(r)) && /20 min left/.test(text(r)), text(r));
  await clock(Date.UTC(2026, 9, 26, 20, 30));
  await reset(); r = await say("What's up next on my schedule?");
  ok('next: nothing left today', /nothing else scheduled for the rest of today/i.test(text(r)), text(r));
  await clock(MON);
  await reset(); r = await say('How close am I to my goals?');
  ok('goals: exact amounts left', r.intentId === 'assistant.check_goal_progress' && new RegExp('XP: 80 of ' + PACE + ' · ' + (PACE > 80 ? (PACE - 80) + ' to go' : 'goal reached')).test(text(r)) && /Steps: 1,479 of 8,000 · 6,521 to go/.test(text(r)), text(r));
  await reset(); r = await say('Did I hit my daily goal?'); ok('goals: "did I hit my daily goal"', r.intentId === 'assistant.check_goal_progress' && /Not there yet/.test(r.title), r.title);
  await reset(); r = await say('How many more steps do I need today?'); ok('goals: more steps', r.intentId === 'assistant.check_goal_progress' && /6,521/.test(text(r)));
  await reset(); r = await say('I have 30 minutes free, what should I do?');
  ok('suggest: fits the window and offers an action', r.intentId === 'assistant.recommend_action' && /^With 30 min/.test(r.title) && r.actions.length >= 1 && (r.notices || []).length >= 1, text(r));
  for (const u of ["I'm bored", 'Suggest something to do', 'How can I get more XP today?']) { await reset(); r = await say(u); ok('suggest: "' + u + '"', r.intentId === 'assistant.recommend_action', r.intentId) }

  // ===== 4. edit the card on screen =====
  await reset(); r = await say('Schedule a workout tomorrow at 9 AM for 60 minutes'); const op = r.operationId;
  r = await say('Make it 45 minutes'); ok('edit: length only', r.state === 'PREVIEWED' && r.operationId === op && r.slot.localStartTime === '09:00' && r.slot.localEndTime === '09:45' && r.slot.durationMinutes === 45 && r.title === 'Workout', r.slot);
  r = await say('Actually at 9:30'); ok('edit: start time only (length kept)', r.slot.localStartTime === '09:30' && r.slot.localEndTime === '10:15' && r.slot.durationMinutes === 45, r.slot);
  r = await say('Change the title to Leg Day'); ok('edit: title only', r.title === 'Leg Day' && r.slot.localStartTime === '09:30' && r.slot.durationMinutes === 45, r.title);
  r = await say('Move it to Wednesday'); ok('edit: day only', r.slot.localDate === '2026-10-28' && r.slot.localStartTime === '09:30' && r.title === 'Leg Day', r.slot);
  r = await say('Push it back 30 minutes'); ok('edit: shift by minutes', r.slot.localStartTime === '10:00' && r.slot.localEndTime === '10:45', r.slot);
  r = await say('Make it tomorrow instead'); ok('edit: "tomorrow instead"', r.slot.localDate === '2026-10-27', r.slot);
  r = await say('For an hour and a half instead'); ok('edit: "an hour and a half"', r.slot.durationMinutes === 90 && r.slot.localEndTime === '11:30', r.slot);
  r = await say('Call it Deep Work'); ok('edit: "call it"', r.title === 'Deep Work');
  r = await say('Make it 2 PM'); ok('edit: a new hour', r.slot.localStartTime === '14:00', r.slot);
  r = await confirm(); ok('edit: what is saved is the edited card', r.state === 'CONFIRMED' && r.title === 'Deep Work' && r.slot.localStartTime === '14:00' && r.slot.durationMinutes === 90 && r.slot.localDate === '2026-10-27', r.slot);
  await reset(); await say('Schedule a workout tomorrow at 9 AM for 60 minutes'); r = await say("What's on my schedule tomorrow?");
  ok('edit: a question is not mistaken for an edit', r.intentId === 'life_director.query_schedule', r.intentId);
  await reset(); await say('Schedule a workout tomorrow at 9 AM for 60 minutes'); r = await say('Yes'); ok('edit: "yes" still saves', r.state === 'CONFIRMED');

  // ===== 5. right after / right before =====
  await clearVoice();
  await reset(); await say('Schedule deep work tomorrow from 9 AM to 11 AM'); r = await confirm();
  ok('chain: deep work saved', r.state === 'CONFIRMED' && r.slot.localStartTime === '09:00' && r.slot.localEndTime === '11:00', r.slot);
  r = await say('Put a 20 minute break right after that');
  ok('chain: break from 11:00 to 11:20', r.state === 'PREVIEWED' && r.slot.localStartTime === '11:00' && r.slot.localEndTime === '11:20' && r.slot.localDate === '2026-10-27' && /Break/i.test(r.title), r.slot || r);
  await confirm();
  r = await say('And 15 minutes of prep right before deep work');
  ok('chain: prep from 8:45 to 9:00, found by name', r.state === 'PREVIEWED' && r.slot.localStartTime === '08:45' && r.slot.localEndTime === '09:00' && /Prep/i.test(r.title), r.slot || r);
  await confirm();
  r = await say('Put 10 minutes of warmup right before that');
  ok('chain: "that" is the last thing scheduled (the prep, 8:45)', r.state === 'PREVIEWED' && r.slot.localStartTime === '08:35' && r.slot.localEndTime === '08:45', r.slot || r);
  await reset(); r = await say('Schedule a walk after work today'); ok('chain: "after work" is still an anchor', r.state === 'AWAITING_CLARIFICATION' && r.question.flag === 'ANCHOR_TIME_MISSING', r.question);
  await reset(); await p.evaluate(() => { /* nothing scheduled to point at */ }); const g0 = await p.evaluate(() => LifeMaxVoiceG4.last());

  // ===== 6. comparisons =====
  await seed(); await clearVoice();
  await reset(); r = await say('Did I walk more today than yesterday?');
  ok('compare: today 1,479 vs yesterday 0', r.intentId === 'analytics.compare_periods' && /Today: 1,479 steps vs\. Yesterday: 0 steps/.test(r.title) && /\+1,479 steps today/.test(text(r)), text(r));
  await p.evaluate(() => { state.health.snap.steps30.push({ date: '2026-10-25', count: 4500 }) });
  await reset(); r = await say('Did I walk more today than yesterday?'); ok('compare: behind yesterday', /3,021 fewer steps today/.test(text(r)), text(r));
  await reset(); r = await say('What was my best day for steps last week?');
  ok('compare: best day last week is Friday Oct 23 with 9,000', r.intentId === 'analytics.compare_periods' && /Fri/.test(r.title) && /Oct 23/.test(r.title) && /9,000/.test(r.title), r.title);
  await reset(); r = await say('What was my worst day for steps last week?'); ok('compare: lowest day is the unlogged Monday', /Mon/.test(r.title) && /Oct 19/.test(r.title) && /\b0 steps/.test(r.title), r.title);
  await reset(); r = await say('Compare my XP this week to last week'); ok('compare: XP this week vs last (same days)', r.intentId === 'analytics.compare_periods' && /80 XP/.test(r.title) && /\+80 XP/.test(text(r)), text(r));
  await reset(); r = await say('What is my average daily steps last week?'); ok('compare: average daily steps', /5,643 steps a day/.test(r.title), r.title);
  await reset(); r = await say('How many steps did I walk yesterday'); ok('compare: the old step question still works', r.intentId === 'health_fitness.query_steps', r.intentId);

  // ===== 7. plan, library, help =====
  await reset(); r = await say("What's on my workout plan?"); ok('plan: question answered, no plan yet', r.intentId === 'fitness.workout_plan' && /no active workout plan/i.test(r.title), r.title);
  await reset(); r = await say('Search my library for dune'); await p.waitForTimeout(300);
  ok('library: search runs and the screen opens', r.intentId === 'library.search' && /1 match/.test(r.title) && /Dune/.test(text(r)) && await p.evaluate(() => document.querySelector('.view.active').id === 'view-media' && document.getElementById('mediaSearch').value === 'dune'), text(r));
  await reset(); r = await say('Open library'); ok('library: "open library" is still navigation', r.intentId === 'navigation_system.open_section');
  await reset(); r = await say('What can I say?');
  const hb = await p.evaluate(() => ({ n: document.querySelectorAll('.lmv-help .lmv-chip').length, cats: document.querySelectorAll('.lmv-hcat').length }));
  ok('help: a categorized list of examples in the card', r.intentId === 'assistant.voice_help' && hb.n >= 25 && hb.cats >= 8, hb);
  await p.evaluate(() => { [...document.querySelectorAll('.lmv-help .lmv-chip')].find(b => b.textContent === 'How is my cat?').click() });
  ok('help: tapping an example runs it', (await p.evaluate(() => LifeMaxVoice.snapshot().intentId)) === 'virtual_pets.check_status');
  for (const u of ['Voice commands', 'What can you do?', 'Help']) { await reset(); r = await say(u); ok('help: "' + u + '"', r.intentId === 'assistant.voice_help', r.intentId) }

  // ===== 8. timer =====
  await reset(); r = await say('Start a 25 minute focus timer');
  ok('timer: started', r.intentId === 'navigation_system.start_focus_session' && /^25 minute focus session started/.test(r.title) && (await p.evaluate(() => LifeMaxVoiceG4.timer().total)) === 25, r.title);
  await reset(); r = await say('How much time is left?'); ok('timer: time left', /\d+ min \d+ s left/.test(r.title) && /^2[345] min/.test(r.title), r.title);
  await reset(); r = await say('Set a timer for 10 minutes'); ok('timer: replaces the earlier one', /^10 minute timer started/.test(r.title) && /Replaced/.test(text(r)) && (await p.evaluate(() => LifeMaxVoiceG4.timer().total)) === 10, text(r));
  await reset(); r = await say('Start a 45 minute Pomodoro for coding'); ok('timer: pomodoro with a name', /45 minute pomodoro started/.test(r.title) && /Coding/.test(text(r)), text(r));
  await p.evaluate(() => LifeMaxVoiceG4.fireTimer()); ok('timer: when it ends the card says so', await p.evaluate(() => LifeMaxVoice.snapshot().title === 'Time is up' && !LifeMaxVoiceG4.timer()));
  await reset(); await say('Start a 5 minute timer'); r = await say('Cancel timer'); ok('timer: cancel', /cancelled/i.test(r.title) && !(await p.evaluate(() => LifeMaxVoiceG4.timer())), r.title);
  await reset(); r = await say('How much time is left?'); ok('timer: none running', /No timer/.test(r.title), r.title);

  // ===== 9. other apps =====
  await p.evaluate(() => { window.__l = []; LifeMaxVoiceG4.setLauncher(pkg => window.__l.push(pkg)); LifeMaxVoiceG4.setApps([{ pkg: 'org.mozilla.firefox', label: 'Firefox' }, { pkg: 'com.anthropic.claude', label: 'Claude' }, { pkg: 'com.google.android.apps.bard', label: 'Gemini' }, { pkg: 'com.android.settings', label: 'Settings' }, { pkg: 'com.hyperion.myspoti', label: 'Myspoti' }]) });
  const launched = () => p.evaluate(() => window.__l[window.__l.length - 1]);
  for (const [u, pkg] of [['Open Firefox', 'org.mozilla.firefox'], ['Launch Claude', 'com.anthropic.claude'], ['Open Gemini', 'com.google.android.apps.bard'], ['Open Android settings', 'com.android.settings'], ['Open Spotify', 'com.hyperion.myspoti'], ['Open Myspoti', 'com.hyperion.myspoti'], ['Start the browser', 'org.mozilla.firefox']]) {
    await reset(); r = await say(u); ok('apps: "' + u + '" opens ' + pkg, r.intentId === 'navigation_system.launch_external_app' && await launched() === pkg, { i: r.intentId, l: await launched() });
  }
  await reset(); const n0 = await p.evaluate(() => window.__l.length); r = await say('Open settings'); ok('apps: "open settings" is still this app\'s Settings', r.intentId === 'navigation_system.open_section' && (await p.evaluate(() => window.__l.length)) === n0, r.intentId);
  await reset(); r = await say('Open Quake Arena'); ok('apps: an unknown app is not invented', r.intentId !== 'navigation_system.launch_external_app' || /could not find/i.test(r.title), r.title);

  // ===== 10. routines and shortcuts you teach it =====
  await reset(); r = await say('Start my morning routine'); ok('routine: morning brief with the quests', r.intentId === 'assistant.routine' && /Good morning/.test(r.title) && /XP today/.test(text(r)), text(r));
  await reset(); r = await say('When I say gym time, do log a 45 minute workout'); ok('shortcut: taught', r.intentId === 'assistant.teach_alias' && (await p.evaluate(() => LifeMaxVoiceG4.aliases()))['gym time'] === 'log a 45 minute workout', r.title);
  await reset(); r = await say('Gym time'); ok('shortcut: the phrase runs the command', r.act && /45 min/.test(text(r)) && /Workout|Strength/i.test(text(r)), text(r));
  await reset(); r = await say('What shortcuts do I have?'); ok('shortcut: listed', /gym time/.test(text(r)), text(r));
  await reset(); r = await say('Forget gym time'); ok('shortcut: forgotten', !(await p.evaluate(() => LifeMaxVoiceG4.aliases()))['gym time']);

  // ===== 11. the registry and a clean console =====
  const reg = await p.evaluate(() => LifeMaxVoice.registry().map(c => c.intentId));
  ok('registry: every new capability is listed', ['assistant.daily_briefing', 'life_director.whats_next', 'assistant.check_goal_progress', 'assistant.recommend_action', 'analytics.compare_periods', 'fitness.workout_plan', 'library.search', 'navigation_system.start_focus_session', 'navigation_system.launch_external_app', 'assistant.voice_help'].every(i => reg.includes(i)), reg);
  ok('no page errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
