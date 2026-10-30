/* Native Workout plan, web half: the projection a native screen draws from (checked against the page's own Workout plan), when
   the screen covers the page and when it steps aside, the toggle command, the delegated actions and the screen coming back.
   A fake NativePlan plugin stands in for Android. Run: NODE_PATH=$(npm root -g) node tests/native-plan.test.js */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const { execFileSync } = require('child_process');
const root = path.resolve(__dirname, '..'), out = path.join(require('os').tmpdir(), 'lm_nplan_' + process.pid + '.html');
execFileSync('python3', [path.join(root, 'build.py')], { cwd: root, stdio: 'ignore' });
execFileSync('python3', [path.join(root, 'forge_features.py'), path.join(root, 'dist', 'Life_Max_RPG_v73_modular_build.html'), out], { cwd: root, stdio: 'ignore' });
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') }); // a Wednesday in week 1 of the plan below
  await p.addInitScript(`
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete',
      plan:{id:'athlete_foundation8',classId:'athlete',start:'2026-10-26',from:'2026-10-26',checks:{}}}));
    localStorage.setItem('lma_gwr3d','off');
    window.__calls=[];
    const mk=(name,caps)=>{const ls={};return {
      __emit:(e,d)=>Promise.all((ls[e]||[]).map(f=>f(d))),
      addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
      open:async a=>{window.__calls.push([name,'open',a])},
      update:async a=>{window.__calls.push([name,'update',a])},
      close:async a=>{window.__calls.push([name,'close',a]);(ls.closed||[]).forEach(f=>f({session:a&&a.session}))},
      capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true}),
      handshake:async()=>({protocol:1,capabilities:caps})}};
    window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>n==='NativePlan'||n==='NativeOverview',getPlatform:()=>'android',
      Plugins:{NativePlan:mk('plan',['plan.projection.v1']),NativeOverview:mk('overview',[])},registerPlugin:()=>({}),convertFileSrc:u=>u};
  `);
  await p.goto('file://' + out); await p.waitForTimeout(3000);
  const r = await p.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), P = window.Capacitor.Plugins.NativePlan, out = {};
    const count = k => window.__calls.filter(c => c[0] === 'plan' && c[1] === k).length;
    const last = k => [...window.__calls].reverse().find(c => c[0] === 'plan' && c[1] === k);
    const reply = id => [...window.__calls].reverse().find(c => c[0] === 'plan' && c[1] === 'update' && c[2].reply && c[2].reply.requestId === id);
    const showing = () => { const o = last('open'), c = last('close'); return !!o && (!c || window.__calls.lastIndexOf(o) > window.__calls.lastIndexOf(c)) };
    const latest = () => [...window.__calls].reverse().find(c => c[0] === 'plan' && (c[1] === 'open' || c[1] === 'update'))[2].snapshot;
    const dayIn = (s, date) => s.weeks.flatMap(w => w.days).find(d => d && d.date === date);
    let n = 0; const command = async (payload) => { const id = 'r' + (++n); await P.__emit('command', { command: 'toggle', payload, requestId: id }); await wait(150); return reply(id)[2] };
    const text = el => el ? el.textContent.replace(/\s+/g, ' ').trim() : null;
    const until = async (f, ms = 5000) => { for (let t = 0; t < ms && !f(); t += 50) await wait(50) };
    // the one-off thumbnail walk visits every tab; the level-up overlay is under test on its own, further down
    lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { }
    const realLevelUp = showLevelUp; showLevelUp = function () { };
    const S = lmaLoad(); S.nativePlan = true; S.nativeOverview = false; lmaSave();
    navigate('settings'); await wait(50);
    out.setting = document.getElementById('nativePlanToggle').checked && !document.getElementById('nativePlanToggle').disabled;

    // the projection says what the page's own Workout plan says
    navigate('plan'); await until(() => count('open')); // the first capture of the shared bars is slow headless
    const s = last('open') && last('open')[2].snapshot, today = s && dayIn(s, '2026-10-28');
    out.opened = !!s && s.protocol === 1 && s.weeks.length === 8 && s.weeks.every(w => w.days.length === 7) && s.selected.date === '2026-10-28' && !!today;
    const body = document.getElementById('planBody'), web = {
      program: text(body.querySelector('.card h3')), xptext: [...body.querySelectorAll('.xptext span')].map(text), count: text(body.querySelector('.plan-cal-count strong')),
      weeks: [...body.querySelectorAll('.plan-week')].map(w => [text(w.querySelector('.pw-label')), text(w.querySelector('.pw-meta'))]),
      range: text(body.querySelector('.plan-day-heading small')),
      tiles: [...body.querySelectorAll('.plan-day-tile')].map(t => [t.getAttribute('aria-label'), text(t.querySelector('.pdt-kind')), text(t.querySelector('.pdt-code')) || '']),
      title: text(body.querySelectorAll('.card')[2].querySelector('h3')), tag: text(body.querySelectorAll('.card')[2].querySelector('.tag')),
      items: [...body.querySelectorAll('.pit')].map(i => [text(i.querySelector('strong')), text(i.querySelector('.muted'))]),
      // gwrLauncher escapes a label this program has already escaped, so the page shows "&amp;"; the screen shows the label
      launcher: [text(body.querySelector('.gx-launch-text strong')), text(body.querySelector('.gx-launch-text span')).replace(/&amp;/g, '&'), text(body.querySelector('.gx-launch-btn'))],
      rules: [...body.querySelectorAll('details li')].map(text), progression: [...body.querySelectorAll('details div p')].map(text),
    };
    const week0 = s.weeks[0];
    out.matchesPage = {
      program: web.program === s.program,
      xptext: web.xptext[0] === s.status && web.xptext[1] === s.summary,
      count: web.count === s.sessions.done + '/' + s.sessions.total,
      weeks: JSON.stringify(web.weeks) === JSON.stringify(s.weeks.map(w => [w.label, w.meta])),
      range: web.range === week0.label + ' / ' + week0.range,
      tiles: JSON.stringify(web.tiles) === JSON.stringify(week0.days.map(d => [d.description, d.kindLabel, d.glyph])),
      title: web.title === today.title, tag: web.tag === today.tag,
      items: JSON.stringify(web.items) === JSON.stringify(today.items.map(i => [i.title + (i.optional ? ' optional' : ''), i.detail])),
      launcher: JSON.stringify(web.launcher) === JSON.stringify([today.guided.title, today.guided.detail, today.guided.button]),
      rules: JSON.stringify(web.rules) === JSON.stringify(s.rules.items.map(x => (x.lead ? x.lead + ' ' : '') + x.text)),
      progression: JSON.stringify(web.progression) === JSON.stringify(s.rules.progression.map(x => x.lead + ' ' + x.text)),
    };
    out.matchesPageDetail = { web, s: { program: s.program, status: s.status, summary: s.summary, week0: week0.label + ' / ' + week0.range, title: today.title, tag: today.tag } };
    out.shape = today.kind === 'strength' && today.status === 'today' && today.today && !today.locked && dayIn(s, '2026-10-29').locked && today.items.every(i => i.sets.length >= 1 && i.sets.every(x => x === false))
      && s.weeks[0].current && !s.weeks[1].current && s.session === null && s.key[0].label === 'A / C / E · Strength';
    const logo = document.getElementById('brandLogo').getBoundingClientRect();
    out.target = s.celebrationTarget.x === logo.x + logo.width / 2 && s.celebrationTarget.y === logo.y + scrollY + logo.height / 2 && logo.width > 0;

    // toggle: the page's own planToggle, with set semantics
    const mon = planDay('2026-10-26'), sets = mon.items.flatMap(it => Array.from({ length: it.n }, (_, i) => [it.k, i])), xp0 = totalXP(), acts0 = state.activities.length;
    const first = await command({ date: '2026-10-26', key: sets[0][0], set: sets[0][1], checked: true });
    out.tick = first.reply.ok === true && first.reply.changed === true && pcheck('2026-10-26').s[sets[0][0]][sets[0][1]] === true
      && JSON.parse(localStorage.getItem(STORE)).plan.checks['2026-10-26'].s[sets[0][0]][sets[0][1]] === true && dayIn(first.snapshot, '2026-10-26').items[0].sets[0] === true;
    const saved = localStorage.getItem(STORE), again = await command({ date: '2026-10-26', key: sets[0][0], set: sets[0][1], checked: true });
    out.repeatTick = again.reply.ok === true && again.reply.changed === false && localStorage.getItem(STORE) === saved;
    const cues = []; const realCue = SFX.xp; SFX.xp = d => cues.push(d);
    const updates = count('update'); await command({ date: '2026-10-26', key: sets[1][0], set: sets[1][1], checked: true }); await wait(200);
    out.oneUpdate = count('update') === updates + 1; // the reply's projection, and no second one from the page's render
    for (const [k, i] of sets.slice(2, -1)) await command({ date: '2026-10-26', key: k, set: i, checked: true });
    const done = await command({ date: '2026-10-26', key: sets[sets.length - 1][0], set: sets[sets.length - 1][1], checked: true });
    const award = state.activities.filter(a => a.id === 'plan_2026-10-26');
    out.complete = done.reply.ok && done.reply.changed && /complete · \+40 XP/.test(done.reply.message) && award.length === 1 && award[0].xp === 40 && totalXP() === xp0 + 40
      && done.reply.celebrations.length >= 1 && done.reply.celebrations[0].xp === 40 && done.reply.celebrations[0].gains.some(g => g.attribute === 'physique' && g.short && typeof g.color === 'number')
      && dayIn(done.snapshot, '2026-10-26').status === 'done' && done.snapshot.sessions.done === 1;
    out.celebrationHeld = document.querySelectorAll('.fx-xp').length === 0;
    await wait(200); out.cuePlayed = cues.length >= 1 && cues[0] === 40;
    const repeat = await command({ date: '2026-10-26', key: sets[sets.length - 1][0], set: sets[sets.length - 1][1], checked: true });
    out.xpOnce = repeat.reply.changed === false && state.activities.filter(a => a.id === 'plan_2026-10-26').length === 1 && totalXP() === xp0 + 40 && state.activities.length === acts0 + 1;
    const undo = await command({ date: '2026-10-26', key: sets[0][0], set: sets[0][1], checked: false });
    out.reopen = undo.reply.ok && /Session reopened · XP removed/.test(undo.reply.message) && !state.activities.some(a => a.id === 'plan_2026-10-26') && totalXP() === xp0;
    const locked = await command({ date: '2026-10-29', key: planDay('2026-10-29').items[0].k, set: 0, checked: true });
    out.locked = locked.reply.ok === false && /Locked/.test(locked.reply.message) && !(pcheck('2026-10-29').s || {})[planDay('2026-10-29').items[0].k];
    const bogus = await command({ date: '2026-10-26', key: 'nope', set: 0, checked: true }), junk = await command({ date: '<b>', key: 'E0', set: 0, checked: true });
    out.refused = bogus.reply.ok === false && junk.reply.ok === false;
    const realSet = Storage.prototype.setItem, full = async (payload) => {
      Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return realSet.apply(this, arguments) };
      try { return await command(payload) } finally { Storage.prototype.setItem = realSet }
    };
    const failed = await full({ date: '2026-10-26', key: sets[0][0], set: sets[0][1], checked: true });
    out.failedSave = failed.reply.ok === false && failed.reply.message === 'Could not save to this browser (storage full?)' && !failed.reply.celebrations
      && pcheck('2026-10-26').s[sets[0][0]][sets[0][1]] === true && dayIn(failed.snapshot, '2026-10-26').items[0].sets[0] === true
      && !JSON.parse(localStorage.getItem(STORE)).plan.checks['2026-10-26'].s[sets[0][0]][sets[0][1]];
    SFX.xp = realCue;
    out.commandsKeepScreen = showing() && count('close') === 0;

    // a selection made on the page (Life Director, voice) is followed; the screen's own selection is otherwise left alone
    const rev = latest().selected.revision;
    selectDay('2026-10-27'); await wait(250);
    out.followsPage = latest().selected.date === '2026-10-27' && latest().selected.revision === rev + 1;
    renderPlan(); await wait(250); out.keepsOwn = latest().selected.revision === rev + 1;

    // the guided workout is the page's: the screen closes for it and comes back when it is paused
    let closes = count('close'), opens = count('open');
    await P.__emit('action', { action: 'guided', id: '2026-10-28' }); await wait(300);
    out.guided = count('close') === closes + 1 && document.getElementById('guidedWorkout').classList.contains('open') && state.guidedWorkout.date === '2026-10-28' && count('open') === opens;
    gwrPause(); await wait(400);
    out.guidedBack = count('open') === opens + 1 && showing() && latest().session && latest().session.title === 'Workout in progress' && latest().session.button === 'Resume';

    // editing a value is the page's field: the screen stays away while it has the focus and comes back after
    closes = count('close'); opens = count('open');
    await P.__emit('action', { action: 'value', id: '2026-10-28/E0' }); await wait(300);
    const field = document.activeElement;
    out.edit = count('close') === closes + 1 && planSel === '2026-10-28' && field.matches('#planBody .pv') && count('open') === opens;
    field.value = '95 lb'; field.dispatchEvent(new Event('change', { bubbles: true })); field.blur(); await wait(400);
    out.editBack = (pcheck('2026-10-28').v || {}).E0 === '95 lb' && count('open') === opens + 1 && dayIn(latest(), '2026-10-28').items.find(i => i.key === 'E0').value.text === '95 lb';
    await P.__emit('action', { action: 'sessionLog', id: '2026-10-28/E0' }); await wait(300);
    out.editLog = document.activeElement.matches('#planBody .plog') && !showing();
    document.activeElement.blur(); await wait(400); out.editLogBack = showing();

    // a level-up plays on the page, so the screen steps aside for it at once and comes back when it closes
    closes = count('close'); opens = count('open');
    realLevelUp({ charLevel: null, from: 1, attrs: [{ id: 'physique', short: 'Physique', from: 1, level: 2 }] }); await wait(80);
    out.levelUp = count('close') === closes + 1;
    await wait(800); document.querySelector('.luov').click(); await wait(700);
    out.levelUpBack = !document.querySelector('.luov') && count('open') === opens + 1 && showing();

    // leaving the plan asks on the page; kept, the screen comes back; left, the offers stay on the page until a plan is started
    const realConfirm = window.confirm; window.confirm = () => false;
    opens = count('open');
    await P.__emit('action', { action: 'leave', id: '' }); await wait(300);
    out.leaveKept = !!state.plan && count('open') === opens + 1 && showing();
    window.confirm = () => true;
    await P.__emit('action', { action: 'leave', id: '' }); await wait(400);
    out.left = state.plan === null && !showing() && !!document.getElementById('planStart');
    navigate('dashboard'); await wait(200); opens = count('open'); navigate('plan'); await wait(400);
    out.offersStayWeb = count('open') === opens && !showing();
    window.confirm = realConfirm;
    startPlan(); await wait(400);
    out.startedBack = !!state.plan && count('open') === opens + 1 && showing();

    // a save that fails as today's last set completes the day: the page keeps the whole change, as after its own checkbox (the
    // day's XP, the daily-hand quest the save completed, the badges), and the next save stores it all
    dhEnsure(); const card = dhMakeQuest(QUEST_LIBRARY.find(t => t.verify === 'plan'), getToday(), 0, 9); state.quests.push(card);
    const todaySets = planDay('2026-10-28').items.flatMap(it => Array.from({ length: it.n }, (_, i) => [it.k, i])), [lastKey, lastSet] = todaySets.pop();
    for (const [k, i] of todaySets) await command({ date: '2026-10-28', key: k, set: i, checked: true });
    const xpBefore = totalXP(), lost = await full({ date: '2026-10-28', key: lastKey, set: lastSet, checked: true });
    const dayAct = state.activities.find(a => a.id === 'plan_2026-10-28'), questAct = state.activities.find(a => a.id === state.questDone[questKey(card)]);
    out.failedSaveKept = lost.reply.ok === false && lost.reply.message === 'Could not save to this browser (storage full?)' && dayDone('2026-10-28')
      && dayIn(lost.snapshot, '2026-10-28').status === 'done' && !!dayAct && !!questAct && totalXP() === xpBefore + dayAct.xp + questAct.xp;
    const badges = JSON.stringify(state.badges); save(); const stored = JSON.parse(localStorage.getItem(STORE));
    out.failedSaveStoredLater = !!dayAct && !!questAct && stored.plan.checks['2026-10-28'].actId === dayAct.id && stored.activities.some(a => a.id === dayAct.id)
      && stored.questDone[questKey(card)] === questAct.id && JSON.stringify(stored.badges) === badges;

    showLevelUp = realLevelUp;
    return out;
  });
  ok('Settings shows the Native Workout plan (beta) switch, on', r.setting, r);
  ok('opening sends a projection of all eight weeks with today selected', r.opened, r);
  for (const [k, v] of Object.entries(r.matchesPage || {})) ok('the projection words it as the page does: ' + k, v, r.matchesPageDetail);
  ok('days carry their kind, status, lock and sets; the current week, no guided session, the program key', r.shape, r);
  ok('the celebration flies where the page\'s does: the top bar\'s logo', r.target, r);
  ok('toggle ticks a set through planToggle, saves it and answers with the fresh projection', r.tick, r);
  ok('a tick sends the screen one projection, the one with the reply', r.oneUpdate, r);
  ok('the same toggle again changes nothing and saves nothing', r.repeatTick, r);
  ok('completing a day pays its XP once, with the page\'s message and the celebration in the reply', r.complete, r);
  ok('the celebration is not drawn under the screen; the page plays its sound and buzz after the reply', r.celebrationHeld && r.cuePlayed, r);
  ok('ticking the last set again pays nothing more', r.xpOnce, r);
  ok('clearing a set of a completed day reopens it and takes the XP back', r.reopen, r);
  ok('a future day is locked', r.locked, r);
  ok('a set that is not in the plan, or a malformed date, is refused', r.refused, r);
  ok('a failed save is reported as not saved; the page keeps the tick in memory, as after its own checkbox', r.failedSave, r);
  ok('commands never close the screen', r.commandsKeepScreen, r);
  ok('a day selected on the page is followed (revision rises); a plain refresh keeps the screen\'s own', r.followsPage && r.keepsOwn, r);
  ok('guided workout: the screen closes and the page\'s runner opens for that day', r.guided, r);
  ok('pausing the runner brings the screen back, showing the session to resume', r.guidedBack, r);
  ok('editing a value: the screen closes and the page\'s field is focused on that day', r.edit, r);
  ok('leaving the field saves through the page and brings the screen back with the value', r.editBack, r);
  ok('editing a session log works the same way', r.editLog && r.editLogBack, r);
  ok('a level-up overlay makes the screen step aside at once and it comes back after', r.levelUp && r.levelUpBack, r);
  ok('leave plan cancelled: the screen comes back', r.leaveKept, r);
  ok('leave plan confirmed: the offers stay on the page, also on a later visit', r.left && r.offersStayWeb, r);
  ok('starting a plan from an offer brings the screen back', r.startedBack, r);
  ok('a failed save that completes the day keeps the day, its XP and the daily-hand quest together', r.failedSaveKept, r);
  ok('the next save stores the day, the quest and the badges', r.failedSaveStoredLater, r);
  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); fs.unlinkSync(out); process.exit(fail ? 1 : 0);
})();
