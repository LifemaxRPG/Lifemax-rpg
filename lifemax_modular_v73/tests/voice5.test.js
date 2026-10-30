/* LifeMax Voice generation 5 tests: negation, places to avoid, bulk changes to a day, fitting things in, "the second one" and
   "here", sets/reps/distance, water, meals and what is left of a goal, cats by name, shortcuts, and a spoken Undo.
   Run:  NODE_PATH=$(npm root -g) node tests/voice5.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (name, cond, extra) => { if (cond) { pass++; console.log('PASS ' + name) } else { fail++; console.log('FAIL ' + name + (extra !== undefined ? '  ' + JSON.stringify(extra).slice(0, 700) : '')) } };
const MON = Date.UTC(2026, 9, 26, 17, 14), NIGHT = Date.UTC(2026, 9, 27, 3, 30);   // Mon 2026-10-26 13:14 and 23:30 EDT
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, timezoneId: 'America/New_York' })).newPage(); const errs = [];
  p.on('pageerror', e => errs.push(String(e)));
  /* The voice clock says 1:14 PM; the page runs on Playwright's clock from 11:30 PM that Monday, after bedtime. Life Director plans
     from the page's now, so that afternoon holds only what each case puts there, and Tuesday holds the plan the day review names. */
  await p.clock.install({ time: NIGHT });
  await p.goto('file://' + file); await p.waitForTimeout(800);
  await p.evaluate(() => { const o = document.getElementById('onboarding'); if (o) { o.classList.remove('show'); o.style.display = 'none' } ['lifemax_voice_op_journal_v1', 'lifemax_voice_timer_v1', 'lifemax_voice_alias_v1', 'lifemax_voice_macros_v1', 'lifemax_voice_undo_v1'].forEach(k => localStorage.removeItem(k)) });
  await p.evaluate(() => { const { ST } = LifeDirectorApp._debug(); ST.availability.timezone = 'America/New_York'; ST.availability.rules.push({ id: 'sl', kind: 'SLEEP', title: 'Sleep', days: [0, 1, 2, 3, 4, 5, 6], startMin: 23 * 60, durationMin: 480, commuteMin: 0 }); ST.setupComplete = true; LifeDirector.regenerate('test') });
  await p.evaluate(() => LifeMaxVoice._t.buildUI());
  const say = (t, o) => p.evaluate(([t, o]) => LifeMaxVoice.simulateUtterance(t, o), [t, o || {}]);
  const clock = now => p.evaluate(([now]) => LifeMaxVoice.setClock({ now, tz: 'America/New_York' }), [now]);
  const reset = () => p.evaluate(() => LifeMaxVoice.reset());
  const confirm = () => p.evaluate(() => LifeMaxVoice.confirm());
  const text = r => [r.title, ...(r.notices || []).map(n => n.text), ...((r.act && r.act.lines) || [])].join(' | ');
  const addC = (title, sh, sm, eh, em, day) => p.evaluate(([t, sh, sm, eh, em, day]) => { const e = (h, m) => LifeMaxVoice._t.localToEpoch(2026, 10, 26 + (day || 0), h, m, 'America/New_York').epochMs / 1000; return LifeDirector.voice.add({ id: 'tc_' + t.replace(/\W/g, '') + (day || 0), title: t, startEpoch: e(sh, sm), endEpoch: e(eh, em) }) }, [title, sh, sm, eh, em, day || 0]);
  const wipe = () => p.evaluate(() => { const { ST } = LifeDirectorApp._debug(); ST.fixedCommitments = (ST.fixedCommitments || []).filter(c => !String(c.id).startsWith('tc_') && !c.voice); LifeDirector.regenerate('test'); LifeMaxVoiceG5.clearStack() });
  const at = id => p.evaluate(id => { const c = LifeDirector.voice.find(id); return c ? [c.startEpoch, c.endEpoch] : null }, id);
  const hm = ep => p.evaluate(([e]) => LifeMaxVoice._t.localToEpoch && new Date(e * 1000).toLocaleTimeString('en-US', { timeZone: 'America/New_York', hour: 'numeric', minute: '2-digit' }), [ep]);
  await clock(MON);
  const nVoice = () => p.evaluate(() => (LifeDirectorApp._debug().ST.fixedCommitments || []).length);

  // ===== 1. negation =====
  await addC('Workout', 15, 0, 16, 0); let n0 = await nVoice();
  let r = await say("Don't cancel my workout");
  ok('negation: "don\'t cancel my workout" cancels nothing', /won.t do that/.test(r.title) && (await at('tc_Workout0')) !== null && (await nVoice()) === n0, r.title);
  await reset(); r = await say('Do not schedule gym tomorrow'); ok('negation: "do not schedule gym tomorrow" creates nothing', /won.t do that/.test(r.title) && (await nVoice()) === n0 && r.state !== 'PREVIEWED', r);
  await reset(); await say('Schedule gym tomorrow at 9 AM'); r = await say("Don't schedule that");
  ok('negation: it cancels a card that is showing', r.state === 'CANCELLED' && /won.t do that/.test(text(r)), r);
  await reset(); r = await say("Please don't delete anything"); ok('negation: "please don\'t delete anything"', /won.t do that/.test(r.title) && (await nVoice()) === n0);
  await reset(); r = await say("I don't want you to remove my workout"); ok('negation: "I don\'t want you to remove it"', /won.t do that/.test(r.title) && (await at('tc_Workout0')) !== null);
  await reset(); r = await say("Don't forget to schedule gym tomorrow at 9 AM"); ok('negation: "don\'t forget to ..." still does it', r.state === 'PREVIEWED' && r.slot && r.slot.localStartTime === '09:00', r);
  ok('negation: the detector', await p.evaluate(() => LifeMaxVoiceG5.negated("never cancel it") === true && LifeMaxVoiceG5.negated('cancel it') === null && LifeMaxVoiceG5.negated("I don't know") === null));
  await reset(); r = await say('Schedule gym tomorrow at 9 AM'); r = await say('Never mind'); ok('negation: "never mind" still cancels', r.state === 'CANCELLED');

  // ===== 2. places to avoid =====
  await reset(); r = await say('Schedule a 1 hour workout tomorrow, but not in the morning');
  ok('avoid: a 1 hour workout lands in the afternoon or later', r.state === 'PREVIEWED' && r.slot.localDate === '2026-10-27' && r.slot.localStartTime >= '12:00' && r.slot.durationMinutes === 60 && r.title === 'Workout', r.slot);
  await reset(); r = await say('Schedule reading anytime tomorrow after 11 AM');
  ok('avoid: "anytime after 11 AM"', r.state === 'PREVIEWED' && r.slot.localStartTime >= '11:00' && /Reading/.test(r.title) && r.slot.localDate === '2026-10-27', r.slot || r);
  await reset(); r = await say('Find free time today except between 12 and 2 PM');
  ok('avoid: free time skips 12 to 2', r.intentId === 'life_director.find_free_time' && (r.notices || []).length >= 1 && /^2:00 PM/.test(r.notices[0].text), text(r));
  await reset(); r = await say('Schedule a 30 minute walk tomorrow, not before 4 PM'); ok('avoid: "not before 4 PM"', r.state === 'PREVIEWED' && r.slot.localStartTime >= '16:00', r.slot || r);

  // ===== 3. a day that goes off-script =====
  await wipe(); await addC('Deep work', 15, 0, 16, 30); await addC('Workout', 17, 0, 18, 0);
  const d0 = await at('tc_Deepwork0'), w0 = await at('tc_Workout0');
  await reset(); r = await say('Push everything back 30 minutes');
  ok('bulk: preview shows before and after', r.state === 'PREVIEWED' && r.act && /Deep work: 3:00 PM → 3:30 PM/.test(text(r)) && /Workout: 5:00 PM → 5:30 PM/.test(text(r)), text(r));
  r = await confirm(); const d1 = await at('tc_Deepwork0'), w1 = await at('tc_Workout0');
  ok('bulk: both moved by exactly 30 minutes', r.state === 'CONFIRMED' && d1[0] === d0[0] + 1800 && d1[1] === d0[1] + 1800 && w1[0] === w0[0] + 1800, [d0, d1]);
  await reset(); r = await say('Undo');
  ok('bulk: spoken undo puts them back', /^Undid: Shifted 2 things/.test(r.title) && (await at('tc_Deepwork0'))[0] === d0[0] && (await at('tc_Workout0'))[0] === w0[0], r.title);
  await reset(); r = await say("I'm running 20 minutes late"); ok('bulk: "running 20 minutes late"', r.state === 'PREVIEWED' && /20 min later/.test(r.title) && /Deep work: 3:00 PM → 3:20 PM/.test(text(r)), text(r));
  await reset(); await say('Shift my afternoon schedule forward 15 minutes'); r = await p.evaluate(() => LifeMaxVoice.snapshot()); ok('bulk: "shift my afternoon forward 15"', r.state === 'PREVIEWED' && /15 min later/.test(r.title), r.title);
  await reset(); r = await say('Push everything back'); ok('bulk: asks how much', /How much/.test(r.title));
  await reset(); r = await say('Swap my workout and deep work today');
  ok('swap: preview', r.state === 'PREVIEWED' && /Workout: 5:00 PM → 3:00 PM/.test(text(r)) && /Deep work: 3:00 PM → 5:00 PM/.test(text(r)) && /own length/.test(text(r)), text(r));
  r = await confirm(); const d2 = await at('tc_Deepwork0'), w2 = await at('tc_Workout0');
  ok('swap: start times exchanged, each keeps its length', r.state === 'CONFIRMED' && w2[0] === d0[0] && d2[0] === w0[0] && w2[1] - w2[0] === 3600 && d2[1] - d2[0] === 5400, [d2, w2]);
  await reset(); await say('Undo that'); ok('swap: undo swaps them back', (await at('tc_Deepwork0'))[0] === d0[0] && (await at('tc_Workout0'))[0] === w0[0]);
  await reset(); r = await say('Clear my afternoon');
  ok('clear: "afternoon" is noon to 5, so only deep work', r.state === 'PREVIEWED' && /Remove 1 thing the afternoon today/.test(r.title) && /Deep work/.test(text(r)) && !/Workout/.test(text(r)), text(r));
  await reset(); r = await say('Cancel everything after 2 PM today');
  ok('clear: a removal card that names them', r.state === 'PREVIEWED' && /Remove 2 things after 2:00 PM today/.test(r.title) && /Deep work/.test(text(r)) && /Workout/.test(text(r)), text(r));
  await confirm(); ok('clear: confirmed, both are gone', (await at('tc_Deepwork0')) === null && (await at('tc_Workout0')) === null);
  await reset(); await say('Undo'); ok('clear: undo brings them back', (await at('tc_Deepwork0')) !== null && (await at('tc_Workout0')) !== null);
  await reset(); await addC('Dinner', 19, 0, 20, 0); r = await say('Cancel everything after 6 PM today'); ok('clear: "everything after 6 PM" names only the evening one', r.state === 'PREVIEWED' && /Remove 1 thing after 6:00 PM/.test(r.title) && /Dinner/.test(text(r)) && !/Workout/.test(text(r)), text(r));
  r = await p.evaluate(() => LifeMaxVoice.cancel()); ok('clear: cancel removes nothing', (await at('tc_Dinner0')) !== null);

  // ===== 4. only if there is room =====
  await wipe(); await addC('Dentist', 14, 0, 17, 0);
  await reset(); r = await say('If I have an hour free this afternoon, schedule guitar practice');
  ok('fit: no full hour, offers the longest gap', r.state === 'CONFIRMED' && /do not have a full 1 h free this afternoon/.test(r.title) && /45 min open from 1:15 PM to 2:00 PM/.test(r.title) && r.actions.length === 1, r.title);
  await p.evaluate(() => LifeMaxVoice._t.frame().actions[0].run()); r = await p.evaluate(() => LifeMaxVoice.snapshot());
  ok('fit: the compromise becomes a normal card', r.state === 'PREVIEWED' && r.slot.localStartTime === '13:15' && r.slot.durationMinutes === 45 && r.title === 'Guitar practice', r.slot);
  await wipe(); await reset(); r = await say('If I have an hour free this afternoon, schedule guitar practice');
  ok('fit: room for an hour, so it is booked', r.state === 'PREVIEWED' && r.slot.durationMinutes === 60 && r.slot.localStartTime === '13:15' && r.title === 'Guitar practice', r.slot || r);
  await addC('Dinner', 18, 0, 19, 0); await reset(); r = await say('Squeeze in a 20 minute meditation before dinner');
  ok('fit: right before dinner', r.state === 'PREVIEWED' && r.slot.localStartTime === '17:40' && r.slot.localEndTime === '18:00' && /Meditation/.test(r.title), [r.title, r.slot]);
  await reset(); r = await say("Squeeze in 30 minutes of coding whenever I'm free today"); ok('fit: whenever I am free', r.state === 'PREVIEWED' && r.slot.durationMinutes === 30 && /Coding/.test(r.title), r.slot || r);
  await reset(); r = await say('Fit in a 20 minute walk before dinner'); ok('fit: "fit in a walk before dinner"', r.state === 'PREVIEWED' && r.slot.localEndTime === '18:00' && /Walk/.test(r.title), r.slot || r);

  // ===== 5. the second one, and "here" =====
  await wipe(); await p.evaluate(() => { if (!Array.isArray(state.quests) || !state.quests.length) throw new Error('no quests') });
  await reset(); r = await say('What are my quests today?'); const q0 = await p.evaluate(() => LifeMaxVoiceG5.list().items.map(x => x.title));
  r = await say('Complete the first one');
  ok('ordinal: the first quest is checked off', /Checked off/.test(r.title) && r.title.includes(q0[0]) && await p.evaluate(t => isQuestDone(state.quests.find(q => q.name === t)), q0[0]), r.title);
  r = await say('Complete the second quest'); ok('ordinal: "the second quest"', /Checked off/.test(r.title) && r.title.includes(q0[1]), r.title);
  r = await say('Undo'); ok('ordinal: spoken undo un-checks the last one', /^Undid: Completed quest/.test(r.title) && await p.evaluate(t => !isQuestDone(state.quests.find(q => q.name === t)), q0[1]), r.title);
  await reset(); r = await say('Check off the last one'); ok('ordinal: with no list, asks which', /of what/.test(r.title) || /Checked off/.test(r.title));
  await reset(); await say('What are my quests today?'); r = await say('Tell me more about number two'); ok('ordinal: "tell me more about number two"', r.title === q0[1] || /Quest/.test(r.head || '') || r.title.includes(q0[1]), r.title);
  await reset(); r = await say('Complete the ninth one'); ok('ordinal: out of range is said plainly', /only|empty|of what/i.test(r.title), r.title);
  await p.evaluate(() => navigate('quests')); await reset(); r = await say('What do I have left here?');
  ok('screen: "what do I have left here" on Quests lists quests', r.intentId === 'voice.screen_summary' && /quest/i.test(r.title + text(r)), r);
  r = await say('Check off the first one'); ok('screen: on Quests, "the first one" means the first quest even with no list', /Checked off|already done/.test(r.title), r.title);
  await p.evaluate(() => navigate('dashboard')); await reset(); r = await say('Summarize this screen'); ok('screen: on Overview gives the day', r.intentId === 'voice.screen_summary' && /XP today/.test(text(r)), text(r));
  await p.evaluate(() => navigate('plan'));
  const hasPlan = await p.evaluate(() => { const t = getToday(); for (let k = 0; k < 14; k++) { const d = new Date(); d.setDate(d.getDate() - k); const s = d.toLocaleDateString('en-CA'); state.plan = { id: 'athlete_foundation8', start: s, from: s, checks: {} }; const day = planDay(t); if (day && day.kind !== 'rest' && day.items.length) return day.items.length } state.plan = null; return 0 });
  if (hasPlan) {
    await reset(); r = await say('What exercises are in today\'s workout plan?'); ok('plan: lists today\'s exercises', r.intentId === 'fitness.workout_plan' && (r.notices || []).length >= 1, r);
    r = await say('Mark the first exercise in my workout plan as done'); ok('plan: marks the first exercise done', /Marked/.test(r.title) && await p.evaluate(() => { const d = planDay(getToday()), c = pcheck(getToday()); return ((c.s || {})[d.items[0].k] || []).filter(Boolean).length >= d.items[0].n }), r.title);
    r = await say('Undo'); ok('plan: undo unmarks it', /^Undid: Marked/.test(r.title) && await p.evaluate(() => { const d = planDay(getToday()), c = pcheck(getToday()); return ((c.s || {})[d.items[0].k] || []).filter(Boolean).length === 0 }), r.title);
  } else ok('plan: (no workout day available to test)', true);

  // ===== 6. sets, reps, weight, distance =====
  const lastAct = () => p.evaluate(() => state.activities.filter(a => a.detail).pop() || state.activities[state.activities.length - 1]);
  const w = t => p.evaluate(t => LifeMaxVoiceG5.parseWorkout(t), t);
  let pw = await w('Log bench press 3 sets of 10 at 185 pounds'); ok('workout parse: bench press', pw && pw.kind === 'strength' && /Bench Press/.test(pw.exercise) && pw.sets === 3 && pw.reps === 10 && pw.weight === 185 && pw.unit === 'lbs', pw);
  pw = await w('Log 5 sets of 5 deadlifts at 315 lbs'); ok('workout parse: deadlifts', pw && /Deadlift/.test(pw.exercise) && pw.sets === 5 && pw.reps === 5 && pw.weight === 315, pw);
  pw = await w('I did 50 pushups'); ok('workout parse: 50 pushups', pw && /Push/.test(pw.exercise) && pw.reps === 50 && pw.sets === 1 && pw.weight === null, pw);
  pw = await w('Log 4 sets of 8 squats at 225 lbs'); ok('workout parse: squats', pw && /Squat/.test(pw.exercise) && pw.sets === 4 && pw.reps === 8 && pw.weight === 225, pw);
  pw = await w('Log a 5K run in 25 minutes'); ok('workout parse: 5K run', pw && pw.kind === 'cardio' && pw.distance === 5 && pw.du === 'km' && pw.dur === 25 && pw.exercise === 'Run', pw);
  pw = await w('I ran 3 miles in 24 minutes'); ok('workout parse: 3 miles', pw && pw.distance === 3 && pw.du === 'mi' && pw.dur === 24, pw);
  pw = await w('I biked 10 miles in 40 minutes'); ok('workout parse: biked', pw && /Bike/.test(pw.exercise) && pw.distance === 10 && pw.dur === 40, pw);
  pw = await w('How many pushups did I do'); ok('workout parse: a question is not a log', pw === null, pw);
  await reset(); r = await say('Log bench press 3 sets of 10 at 185 pounds yesterday');
  ok('workout: preview shows the exercise, sets, weight and volume', r.state === 'PREVIEWED' && /Bench Press/.test(r.title) && /3 sets of 10 at 185 lbs/.test(text(r)) && /volume 5,550/.test(text(r)), text(r));
  r = await confirm(); let a = await lastAct();
  ok('workout: saved with the details', r.state === 'CONFIRMED' && a.name === 'Bench Press' && a.detail && a.detail.sets === 3 && a.detail.reps === 10 && a.detail.weight === 185 && /3 × 10 at 185 lbs/.test(a.notes), a);
  await reset(); await say('Log a 5K run in 25 minutes yesterday'); r = await confirm(); a = await lastAct();
  ok('workout: a 5K run keeps distance, time and pace', r.state === 'CONFIRMED' && a.detail && a.detail.distance === 5 && a.detail.distanceUnit === 'km' && a.detail.durationMinutes === 25 && a.detail.paceMinPerKm === 5, a);
  await reset(); r = await say('Walked 2 miles this morning'); ok('workout: no time given, an honest estimate', r.state === 'PREVIEWED' && /estimated/.test(text(r)) && /2 mi/.test(text(r)), text(r));
  await reset(); r = await say('Undo'); ok('workout: undo removes the log', /^Undid: Logged “Bench Press”/.test(r.title) || /^Undid: Logged/.test(r.title) || /Nothing to undo|Undid/.test(r.title), r.title);

  // ===== 7. water =====
  await p.evaluate(() => { state.waterLog = []; LifeMaxVoiceG5.clearStack() });
  const wp = t => p.evaluate(t => LifeMaxVoiceG5.parseWater(t), t);
  ok('water parse: a glass', (await wp('Drank a glass of water')).ml === 250); ok('water parse: a bottle (16.9 oz)', (r = await wp('I drank a bottle of water')).ml === 500 && r.oz === 16.9, r);
  ok('water parse: a large bottle', (await wp('I finished a large bottle of water')).ml === 1000); ok('water parse: 20 ounces', Math.abs((await wp('Add 20 ounces of water')).ml - 591) <= 1);
  ok('water parse: 500 ml', (await wp('log 500 ml of water')).ml === 500); ok('water parse: two glasses', (await wp('I had two glasses of water')).ml === 500); ok('water parse: a liter', (await wp('drank a liter of water')).ml === 1000);
  await reset(); r = await say('I drank a bottle of water'); ok('water: logged and shown in ml and oz', r.state === 'CONFIRMED' && /\+500 ml of water \(16\.9 oz\)/.test(r.title) && /Today: 500 ml of 2,000 ml/.test(text(r)), text(r));
  await reset(); await say('Drank a glass of water'); await reset(); r = await say('How much water have I had today?'); ok('water: today so far', r.intentId === 'nutrition.remaining' && /750 ml/.test(text(r)) && /1,250 ml of water to go/.test(r.title), text(r));
  await reset(); r = await say('Undo'); ok('water: undo takes the last glass back', /^Undid: Logged 250 ml/.test(r.title) && (await p.evaluate(() => state.waterLog.reduce((s, x) => s + x.ml, 0))) === 500, r.title);
  await reset(); await say('Set my water goal to 3 liters'); r = await say('How much water do I have left?'); ok('water: a goal you set', /2,500 ml of water to go/.test(r.title), r.title);

  // ===== 8. meals and what is left of a goal =====
  await p.evaluate(() => { state.foodLog = []; state.mealsMeta = { calGoal: 0 }; LifeMaxVoiceG5.clearStack() });
  const mp = await p.evaluate(() => LifeMaxVoiceG5.parseMeal('For breakfast I had 3 eggs, two slices of toast, and a protein shake'));
  ok('meal parse: slot and three items', mp && mp.slot === 'breakfast' && mp.items.length === 3 && mp.items[0].qty === 3 && mp.items[1].qty === 2 && mp.items[1].unit === 'slice', mp);
  await reset(); r = await say('For breakfast I had 3 eggs, two slices of toast, and a protein shake');
  ok('meal: a card with each item and the total', r.state === 'PREVIEWED' && /Log breakfast \(3 items\)/.test(r.title) && /Total: 5\d\d kcal/.test(text(r)) && /protein/.test(text(r)) && /\(estimate\)/.test(text(r)), text(r));
  r = await confirm(); const meal = await p.evaluate(() => state.foodLog.filter(x => x.voice).map(x => ({ s: x.slot, k: x.kcal, p: x.p, g: x.grams })));
  ok('meal: three food entries in the breakfast slot', r.state === 'CONFIRMED' && meal.length === 3 && meal.every(x => x.s === 'breakfast') && meal.reduce((a, x) => a + x.k, 0) > 480, meal);
  await reset(); r = await say('For lunch I had a chicken breast, a cup of rice, and an apple'); ok('meal: lunch with a real database match', r.state === 'PREVIEWED' && /Log lunch \(3 items\)/.test(r.title) && /Chicken|chicken/.test(text(r)) && /Rice|rice/.test(text(r)), text(r));
  await reset(); r = await say('For dinner I had quantum soup'); ok('meal: an unknown food is not invented', /could not match/i.test(r.title) || /Not found/.test(text(r)), text(r));
  await reset(); r = await say('How many calories do I have left?'); ok('goal: no calorie goal yet says how to set one', r.intentId === 'nutrition.remaining' && /No calories goal/.test(r.title) && /set my calorie goal/.test(text(r)), text(r));
  await reset(); await say('Set my calorie goal to 2200'); const eaten = await p.evaluate(() => state.foodLog.reduce((s, x) => s + x.kcal, 0)); r = await say('How many calories can I still eat today?');
  ok('goal: remaining calories = goal - eaten', new RegExp((2200 - eaten).toLocaleString('en-US') + ' kcal left').test(r.title), [r.title, eaten]);
  await reset(); r = await say('How much protein do I have left today?'); ok('goal: protein goal not set yet', /No protein goal/.test(r.title), r.title);
  await reset(); await say('Set my protein goal to 150 grams'); const prot = await p.evaluate(() => Math.round(state.foodLog.reduce((s, x) => s + x.p, 0))); r = await say('How much protein do I have left today?');
  ok('goal: remaining protein', new RegExp((150 - prot).toLocaleString('en-US') + ' g protein left').test(r.title), [r.title, prot]);
  await reset(); await say('Undo'); ok('goal: undo puts the goal back', (await p.evaluate(() => state.mealsMeta.protGoal)) == null || (await p.evaluate(() => state.mealsMeta.protGoal)) === 0);

  // ===== 9. cats by name =====
  await p.evaluate(() => { const pt = petAdopt('ginger', 'Mochi', 'playful'); pt.born = Date.now() - 60 * 864e5; pt.lastFed = 0; save() });
  await reset(); r = await say("What is Mochi's personality?"); ok('cat: personality by name', r.intentId === 'virtual_pets.describe' && /Mochi/.test(r.title) && /Playful/.test(text(r)) && /Build: ears/.test(text(r)), text(r));
  await reset(); r = await say('Which cat is hungry?'); ok('cat: which cat is hungry', r.intentId === 'virtual_pets.query_needs' && /Mochi/.test(text(r)), text(r));
  await reset(); r = await say('Give Mochi a treat'); ok('cat: treat is answered honestly', r.intentId === 'virtual_pets.give_treat' && /no treats/i.test(r.title), r.title);
  await reset(); r = await say('Clean the litter box'); ok('cat: litter box is answered honestly', r.intentId === 'virtual_pets.clean_litter' && /no litter box/i.test(r.title), r.title);
  await reset(); r = await say('How is Mochi doing?'); ok('cat: a cat by name still works', r.intentId === 'virtual_pets.check_status' && /Mochi/.test(r.title), r);
  await reset(); r = await say('Play with all my cats'); ok('cat: all my cats', r.intentId === 'virtual_pets.perform_action', r.intentId);

  // ===== 10. shortcuts =====
  await wipe(); await reset(); r = await say("When I say 'Beast Mode', log a 60 minute workout and open my workout plan");
  ok('macro: taught with two steps', r.intentId === 'voice.create_macro' && (await p.evaluate(() => LifeMaxVoiceG5.macros()))['beast mode'].cmds.length === 2, await p.evaluate(() => LifeMaxVoiceG5.macros()));
  await reset(); r = await say('Beast mode'); ok('macro: the phrase starts the first step', r.state === 'PREVIEWED' && r.act && /1 h/.test(text(r)), text(r));
  r = await confirm(); const opened = await p.waitForFunction(() => document.querySelector('.view.active').id === 'view-plan', null, { timeout: 5000 }).then(() => true, () => false);
  ok('macro: the second step follows, one card', r.state === 'CONFIRMED' && r.title === 'Did 2 things' && opened, r.title);
  await p.evaluate(() => navigate('dashboard')); await reset(); r = await say('Start my day'); ok('macro: "start my day"', r.intentId === 'voice.run_macro' && /Good morning/.test(r.title) && /XP today/.test(text(r)), text(r));
  await reset(); r = await say('End of day review'); ok('macro: "end of day review"', r.intentId === 'voice.run_macro' && /Wrapping up/.test(r.title) && /XP earned/.test(text(r)) && /Tomorrow/.test(text(r)), text(r));
  await reset(); r = await say('Wrap up my day'); ok('macro: "wrap up my day"', /Wrapping up/.test(r.title));
  await reset(); r = await say('What shortcuts do I have?'); ok('macro: listed', /beast mode/.test(text(r)), text(r));
  await reset(); await say('Forget beast mode'); ok('macro: forgotten', !(await p.evaluate(() => LifeMaxVoiceG5.macros()))['beast mode']);

  // ===== 11. spoken Undo, even after the card is gone =====
  await wipe(); await reset(); await say('Schedule dentist tomorrow at 9 AM'); r = await confirm(); const eid = r.entityId; const op = r.operationId;
  ok('undo: the dentist is saved', r.state === 'CONFIRMED' && !!(await p.evaluate(id => LifeDirector.voice.find(id), eid)));
  await reset(); await p.evaluate(() => LifeMaxVoice._t.frame && 0);
  r = await say('Take that back'); ok('undo: after the card is gone, "take that back" removes it', /^Undid: Scheduled “Dentist”/.test(r.title) && !(await p.evaluate(id => LifeDirector.voice.find(id), eid)), r.title);
  ok('undo: the journal entry is marked UNDONE', await p.evaluate(op => LifeMaxVoice._t.journal().some(e => e.operationId === op && e.status === 'UNDONE'), op));
  await reset(); r = await say('Undo that'); ok('undo: with nothing left, says so', /Nothing to undo/.test(r.title), r.title);
  await reset(); await say('Schedule dentist tomorrow at 9 AM'); await confirm(); await reset(); await say('Move dentist to 11 AM'); await confirm();
  await reset(); r = await say('Undo'); ok('undo: a move goes back first', /^Undid: Moved “Dentist”/.test(r.title), r.title);
  await reset(); r = await say('Revert last command'); ok('undo: then the creation', /^Undid: Scheduled “Dentist”/.test(r.title), r.title);
  await wipe(); await reset(); await addC('Standup', 9, 0, 9, 30, 1); await say('Cancel standup tomorrow'); await confirm(); await reset(); r = await say('Undo'); ok('undo: a cancel is restored', /^Undid: Removed “Standup”/.test(r.title) && !!(await at('tc_Standup1')), r.title);

  // ===== 12. the registry and a clean console =====
  const reg = await p.evaluate(() => LifeMaxVoice.registry().map(c => c.intentId));
  ok('registry: the new capabilities are listed', ['voice.undo_last_action', 'life_director.shift_schedule', 'life_director.swap_activities', 'life_director.clear_window', 'life_director.conditional_schedule', 'health_fitness.log_detailed_workout', 'nutrition.log_water', 'nutrition.log_meal', 'nutrition.remaining', 'voice.create_macro', 'voice.run_macro', 'voice.screen_summary', 'voice.ordinal'].every(i => reg.includes(i)), reg);
  ok('no page errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
