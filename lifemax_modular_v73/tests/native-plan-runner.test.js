/* Native Workout plan, the last hand-overs, web half: the guided workout runner (plan.runner.v1), the value and session-log fields
   (plan.fields.v1), Leave plan (plan.leave.v1) and the sign-up offers (plan.offers.v1). Checked against the page's own screens
   and functions: the runner the page projects says what its overlay shows in every phase; every act runs the overlay button's own
   function; the screen's commands are refused as the page refuses, answer ok only when saved, and are safe to repeat; a page
   or screen without a capability hands over exactly as before. A fake NativePlan plugin stands in for Android.
   Run: NODE_PATH=$(npm root -g) node tests/native-plan-runner.test.js [built html] */
const { chromium } = require('playwright');
const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 1200) : '')) } };
const ALL = ['plan.projection.v1', 'plan.runner.v1', 'plan.fields.v1', 'plan.leave.v1', 'plan.offers.v1'];

const FAKES = (caps, plan) => `
  localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete',
    ${plan ? "plan:{id:'athlete_foundation8',classId:'athlete',start:'2026-10-26',from:'2026-10-26',checks:{}}" : 'plan:null'}}));
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
    Plugins:{NativePlan:mk('plan',${JSON.stringify(caps)}),NativeOverview:mk('overview',[])},registerPlugin:()=>({}),convertFileSrc:u=>u};`;

/* the screen's side, inside the page: the calls the fake plugin saw, and a command sent as the screen sends it */
const HELPERS = `
  window.__h = (() => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), P = () => Capacitor.Plugins.NativePlan;
    const count = k => window.__calls.filter(c => c[0] === 'plan' && c[1] === k).length;
    const last = k => [...window.__calls].reverse().find(c => c[0] === 'plan' && c[1] === k);
    const latest = () => [...window.__calls].reverse().find(c => c[0] === 'plan' && (c[1] === 'open' || c[1] === 'update'))[2].snapshot;
    const showing = () => { const o = last('open'), c = last('close'); return !!o && (!c || window.__calls.lastIndexOf(o) > window.__calls.lastIndexOf(c)) };
    let n = 0;
    const send = async (command, payload) => {
      const id = 'q' + (++n);
      await P().__emit('command', { command, payload, requestId: id }); await wait(20);
      const u = [...window.__calls].reverse().find(c => c[0] === 'plan' && c[1] === 'update' && c[2].reply && c[2].reply.requestId === id);
      return u ? { reply: u[2].reply, snapshot: u[2].snapshot } : { reply: null, snapshot: latest() };
    };
    const run = (act, extra) => send('runner', Object.assign({ act }, extra));
    const until = async (f, ms = 5000) => { for (let t = 0; t < ms && !f(); t += 50) await wait(50) };
    const text = el => el ? el.textContent.replace(/\\s+/g, ' ').trim() : null;
    /* what the page's own overlay would show for the session as it stands now (the screen hosts it, so the overlay is drawn only here) */
    const classic = () => {
      const host = gwrHost, root = document.getElementById('guidedWorkout'); gwrHost = null;
      try {
        root.innerHTML = ''; root.classList.add('open'); gwrRender();
        const q = sel => text(root.querySelector(sel)), all = sel => [...root.querySelectorAll(sel)];
        return {
          title: q('.gx-top-title strong').replace(/&amp;/g, '&'), step: q('.gx-step'), heading: (q('.gx-title') || q('.gx-ex-name') || '').replace(/&amp;/g, '&'), rx: q('.gx-ex-rx'), sets: q('.gx-sets span'),
          pips: all('.gx-pips i').map(i => i.className.replace(/^is-/, '')), route: all('.gx-route i').map(i => i.className.replace(/^is-/, '') + ':' + i.style.flexGrow),
          routeLabel: root.querySelector('.gx-route') && root.querySelector('.gx-route').getAttribute('aria-label'),
          hero: q('.gx-hero'), last: q('.gx-last'), cue: q('.gx-cue'), summary: all('.gx-summary > div').map(d => text(d)),
          dock: all('.gx-dock .gx-btn, .gx-dock .gx-link').map(b => text(b)), days: all('#gwrSessionDate option').map(o => text(o)), selected: root.querySelector('#gwrSessionDate') && root.querySelector('#gwrSessionDate').value,
          log: all('.gx-log li').map(li => [text(li.querySelector('.gx-log-name')), text(li.querySelector('.gx-log-rx')), li.classList.contains('is-done')]),
          ring: q('.gx-ring-label strong'), restTag: q('.gx-rest-tag'), next: q('.gx-next > div'), confirm: all('.gx-confirm strong, .gx-confirm span:last-child').map(e => text(e)),
          fields: all('.gx-stepper').map(f => [text(f.querySelector('label')), f.querySelector('input').value]), xp: q('.gx-xp'), note: q('.gx-note'), live: q('.gx-live'),
        };
      } finally { root.classList.remove('open'); root.innerHTML = ''; document.body.classList.remove('noscroll'); gwrHost = host; }
    };
    return { wait, P, count, last, latest, showing, send, run, until, text, classic };
  })();`;

async function start(b, caps, plan = true) {
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') });
  await p.addInitScript(FAKES(caps, plan));
  await p.goto('file://' + file); await p.waitForFunction(() => { try { lmaLoad(); return typeof Capacitor !== 'undefined' } catch (e) { return false } }, null, { timeout: 60000 }); await p.waitForTimeout(1500);
  await p.evaluate(HELPERS);
  await p.evaluate(async () => {
    lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { }
    showLevelUp = function () { };
    const S = lmaLoad(); S.nativeOverview = false; lmaSave();
    await setNativePlanEnabled(true); // the Settings switch, as the player turns it on
    navigate('plan'); await __h.until(() => __h.count('open'));
  });
  return p;
}

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });

  /* ---- fields, Leave plan and the offers ---- */
  {
    const p = await start(b, ALL);
    const r = await p.evaluate(async () => {
      const { wait, count, showing, latest, send, text } = __h, out = {}, TODAY = '2026-10-28';
      const s0 = latest(), item = planDay(TODAY).items.find(i => i.wt), noValue = planDay(TODAY).items.find(i => !i.wt && !i.min);
      out.features = JSON.stringify(s0.features) === JSON.stringify({ runner: true, fields: true, leave: true, offers: true }) && s0.runner === null && s0.offer === null;
      out.words = s0.leaveAsk === LEAVE_PLAN_ASK && s0.logMax === PLAN_LOG_MAX && document.querySelector('#planBody .plog').maxLength === s0.logMax;

      // the value field
      const v = await send('value', { date: TODAY, key: item.k, text: '  72.5 lb ' });
      out.value = v.reply.ok && v.reply.changed && pcheck(TODAY).v[item.k] === '72.5 lb' && JSON.parse(localStorage.getItem(STORE)).plan.checks[TODAY].v[item.k] === '72.5 lb'
        && v.snapshot.weeks.flatMap(w => w.days).find(d => d && d.date === TODAY).items.find(i => i.key === item.k).value.text === '72.5 lb';
      const again = await send('value', { date: TODAY, key: item.k, text: '72.5 lb' });
      out.valueRepeat = again.reply.ok && again.reply.changed === false && again.reply.message === 'Already recorded.';
      const cleared = await send('value', { date: TODAY, key: item.k, text: '' });
      out.valueCleared = cleared.reply.ok && cleared.reply.changed && pcheck(TODAY).v[item.k] === '';
      const nov = await send('value', { date: TODAY, key: noValue.k, text: '5' });
      out.valueRefused = !nov.reply.ok && /not in your plan/.test(nov.reply.message) && !(pcheck(TODAY).v || {})[noValue.k];
      const future = await send('value', { date: '2026-10-29', key: planDay('2026-10-29').items.find(i => i.wt || i.min).k, text: '1' });
      out.valueLocked = !future.reply.ok && /Locked/.test(future.reply.message);
      out.valueJunk = !(await send('value', { date: '<b>', key: item.k, text: '1' })).reply.ok && !(await send('value', { date: TODAY, key: item.k, text: 7 })).reply.ok;

      // the session log
      const l = await send('log', { date: TODAY, key: item.k, text: '3×10 @ 72.5 lb, felt strong' });
      out.log = l.reply.ok && l.reply.changed && pcheck(TODAY).log[item.k] === '3×10 @ 72.5 lb, felt strong';
      out.logRepeat = (await send('log', { date: TODAY, key: item.k, text: '3×10 @ 72.5 lb, felt strong ' })).reply.changed === false;
      const gone = await send('log', { date: TODAY, key: item.k, text: '   ' });
      out.logCleared = gone.reply.ok && gone.reply.changed && !(pcheck(TODAY).log || {})[item.k];
      const longer = await send('log', { date: TODAY, key: item.k, text: 'x'.repeat(PLAN_LOG_MAX + 1) });
      out.logTooLong = !longer.reply.ok && /400 characters/.test(longer.reply.message) && !(pcheck(TODAY).log || {})[item.k];
      const exact = await send('log', { date: TODAY, key: item.k, text: 'x'.repeat(PLAN_LOG_MAX) });
      out.logLimit = exact.reply.ok && pcheck(TODAY).log[item.k].length === PLAN_LOG_MAX;
      // the Journal reads the session log the same way as after the page's own field
      out.logReadByJournal = planLogsForDay(TODAY).length >= 0;

      // a save that fails is said so; the change stays in memory, as after the page's own field
      const real = Storage.prototype.setItem;
      Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
      const failed = await send('value', { date: TODAY, key: item.k, text: '80 lb' });
      Storage.prototype.setItem = real;
      out.failedSave = !failed.reply.ok && failed.reply.message === 'Could not save to this browser (storage full?)' && pcheck(TODAY).v[item.k] === '80 lb'
        && failed.snapshot.weeks.flatMap(w => w.days).find(d => d && d.date === TODAY).items.find(i => i.key === item.k).value.text === '80 lb';
      out.keepsScreen = showing() && count('close') === 0;
      // the steppers' arithmetic, which the native screen repeats; the same cases are in PlanRunnerTest.theSteppersStepAsThePagesDo
      out.nudge = JSON.stringify([gwrNudged('', 5, 0), gwrNudged('60', -5, 0), gwrNudged('2', -5, 0), gwrNudged('7.25', 0.5, 1), gwrNudged('100', 2.5, 0), gwrNudged('0.2', -1, 1)]) === JSON.stringify([5, 55, 0, 7.8, 102.5, 1]);

      return out;
    });
    ok('the page tells the screen what it may do itself, and a session log holds what the page\'s field holds', r.features && r.words, r);
    ok('a value is trimmed and saved through planVal and shown in the projection', r.value, r);
    ok('the same value again is "Already recorded." and changes nothing', r.valueRepeat, r);
    ok('an emptied value is saved empty, as the page\'s field does', r.valueCleared, r);
    ok('an exercise without a value field, a future day, a bad date and a non-text value are refused', r.valueRefused && r.valueLocked && r.valueJunk, r);
    ok('a session log is saved through planLog, a repeat changes nothing, an emptied one is removed', r.log && r.logRepeat && r.logCleared, r);
    ok('a session log over the page\'s 400 characters is refused, exactly 400 is kept', r.logTooLong && r.logLimit, r);
    ok('a failed save answers not saved with the page\'s words and keeps the field as the page holds it', r.failedSave, r);
    ok('field commands keep the screen open', r.keepsScreen, r);
    ok('the steppers step as the page\'s gwrNudged, which the screen repeats for its + and − buttons', r.nudge, r);
    ok('no script errors', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* ---- Leave plan, then the offers it brings back, then signing up ---- */
  {
    const p = await start(b, ALL);
    const r = await p.evaluate(async () => {
      const { wait, count, showing, latest, send, until, text } = __h, out = {}, TODAY = '2026-10-28';
      const first = planDay(TODAY).items[0];
      planToggle(TODAY, first.k, 0, true); await wait(150);
      const xp = totalXP(), acts = state.activities.length, id = 'leave-1';
      // the confirm is the page's words
      let asked = ''; const realConfirm = window.confirm; window.confirm = m => { asked = m; return false };
      leavePlan(); window.confirm = realConfirm;
      out.ask = asked === latest().leaveAsk && asked.length > 20 && !!state.plan;
      const left = await send('leave', { op: id });
      out.left = left.reply.ok && left.reply.changed && left.reply.message === 'Plan ended' && state.plan === null && planSel === null
        && JSON.parse(localStorage.getItem(STORE)).plan === null && totalXP() === xp && state.activities.length === acts;
      out.closedNothing = count('close') === 0 && showing();
      // with no plan the screen shows the page's offer, drawn from the same data
      await until(() => latest().offer);
      const o = latest().offer, card = document.querySelector('#planBody .card');
      out.offerShown = !!o && latest().weeks === undefined && o.title === text(card.querySelector('h2')) && o.eyebrow === text(card.querySelector('.eyebrow'))
        && o.blurb === text(card.querySelector('p.muted')) && o.note === text(card.querySelector('.note')) && o.button === text(card.querySelector('button.btn'))
        && o.startLabel === text(card.querySelector('label')) && o.start === document.getElementById('planStart').value
        && JSON.stringify(o.rows.map(r => [r.when, r.what])) === JSON.stringify([...card.querySelectorAll('.minirow')].map(r => [text(r.querySelector('span')), text(r.querySelector('small'))]));
      const again = await send('leave', { op: id });
      out.leaveRepeat = again.reply.ok && again.reply.changed === false && again.reply.message === 'Already recorded.';
      const other = await send('leave', { op: 'leave-2' });
      out.leaveNoPlan = other.reply.ok && other.reply.changed === false;

      // each class's offer reads as the page's offer for it
      out.classes = {};
      for (const c of ['athlete', 'scholar', 'entrepreneur', 'explorer']) {
        state.classId = c; renderPlan(); await wait(250);
        const x = latest().offer, k = document.querySelector('#planBody .card');
        out.classes[c] = x.title === text(k.querySelector('h2')) && x.rows.length === k.querySelectorAll('.minirow').length && x.note === text(k.querySelector('.note'))
          && x.rows.every((r, i) => r.what === text(k.querySelectorAll('.minirow small')[i]));
      }
      state.classId = 'athlete'; renderPlan(); await wait(250);

      // signing up
      const bad = await send('signUp', { start: '2026-02-30', op: 's0' });
      out.badDate = !bad.reply.ok && /start date/.test(bad.reply.message) && state.plan === null;
      const junk = await send('signUp', { start: 'tomorrow', op: 's0' });
      out.junkDate = !junk.reply.ok && state.plan === null;
      const real = Storage.prototype.setItem;
      Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
      const failed = await send('signUp', { start: '2026-11-04', op: 's-fail' });
      Storage.prototype.setItem = real;
      out.failedSave = !failed.reply.ok && failed.reply.message === 'Could not save to this browser (storage full?)' && !!state.plan;
      state.plan = null; planSel = null; save();
      const up = await send('signUp', { start: '2026-11-04', op: 's1' });
      out.signedUp = up.reply.ok && up.reply.changed && up.reply.message === 'Plan started · good luck!' && state.plan.from === '2026-11-04' && state.plan.start === '2026-11-02'
        && state.plan.id === (classPlanId('athlete') || 'athlete_beginner8') && JSON.stringify(state.plan.checks) === '{}' && JSON.parse(localStorage.getItem(STORE)).plan.from === '2026-11-04';
      out.planShown = up.snapshot.weeks && up.snapshot.weeks.length === 8 && up.snapshot.offer === null && up.snapshot.status.startsWith('Starts');
      const dup = await send('signUp', { start: '2026-11-04', op: 's1' });
      out.signUpRepeat = dup.reply.ok && dup.reply.changed === false && dup.reply.message === 'Already recorded.';
      const dup2 = await send('signUp', { start: '2026-11-04', op: 's2' });
      out.signUpSameStart = dup2.reply.ok && dup2.reply.changed === false;
      const dup3 = await send('signUp', { start: '2026-11-11', op: 's3' });
      out.signUpOther = !dup3.reply.ok && /already follow/.test(dup3.reply.message) && state.plan.from === '2026-11-04';
      const scholar = (() => { state.plan = null; state.classId = 'scholar'; return send('signUp', { start: '2026-11-03', op: 's4' }) })();
      const sc = await scholar;
      out.classPlan = sc.reply.ok && state.plan.id === classPlanId('scholar') && state.plan.classId === 'scholar' && state.plan.start === '2026-11-02';
      return out;
    });
    ok('Leave plan asks in the page\'s words (leaveAsk is the page\'s own confirm)', r.ask, r);
    ok('leave ends the plan through leavePlan, keeps the XP, saves, and keeps the screen open', r.left && r.closedNothing, r);
    ok('with no plan the screen is sent the page\'s offer, worded as the page draws it', r.offerShown, r);
    ok('leave again is "Already recorded."; with no plan any leave changes nothing', r.leaveRepeat && r.leaveNoPlan, r);
    ok('each class\'s offer is the page\'s offer for that class', Object.values(r.classes || {}).length === 4 && Object.values(r.classes).every(Boolean), r);
    ok('a start date that is not a date is refused', r.badDate && r.junkDate, r);
    ok('a sign-up whose save fails says so and the plan stays in memory, as the page\'s button leaves it', r.failedSave, r);
    ok('signUp starts the plan through startPlan from that Monday and sends the plan', r.signedUp && r.planShown, r);
    ok('signing up again is "Already recorded."; another date with a plan is refused', r.signUpRepeat && r.signUpSameStart && r.signUpOther, r);
    ok('a class with a light program signs up for its own plan', r.classPlan, r);
    ok('no script errors', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* ---- the guided workout ---- */
  const COMPARE = `
    window.__cmp = (rn, c) => {
      const bad = [], same = (k, a, b) => { if (JSON.stringify(a) !== JSON.stringify(b)) bad.push(rn.phase + ' ' + k + ': ' + JSON.stringify(a) + ' vs ' + JSON.stringify(b)) };
      same('title', rn.title, c.title);
      same('route', rn.route.map(r => r.state + ':' + r.weight), c.route);
      same('routeLabel', rn.progress.done + ' of ' + rn.progress.total + ' required sets done', c.routeLabel);
      if (rn.phase === 'intro') {
        const i = rn.intro;
        same('step', i.step, c.step); same('title', i.title, c.heading);
        same('summary', [i.exercises + ' ' + rn.words.exercises, i.sets + ' ' + rn.words.sets, '+' + i.xp + ' ' + rn.words.xp], c.summary.map(x => x.replace(/^([+\\d]+)/, '$1 ')));
        same('days', i.days.map(d => d.label), c.days); same('selected', i.days.find(d => d.selected).date, c.selected);
        same('log', i.log.map(l => [l.name + (l.optional ? ' ' + rn.words.optional : ''), l.prescription, l.done]), c.log);
      } else if (rn.phase === 'complete') {
        const k = rn.complete;
        same('heading', k.full ? rn.words.completeFull : rn.words.completePartial, c.heading);
        same('note', k.full ? rn.words.completeNoteFull : rn.words.completeNotePartial, c.note);
        const nb = x => x.replace(/ /g, '');
        same('xp', nb('+' + k.xp + ' ' + rn.words.xp), nb(c.xp));
        same('summary', [k.sets + ' ' + rn.words.sets, k.time + ' ' + rn.words.time, k.volume + ' ' + rn.words.volume].map(nb), c.summary.slice(-3).map(nb));
        same('dock', k.full ? [rn.words.backToPlan] : [rn.words.backToPlan, rn.words.finishRemaining], c.dock);
      } else if (rn.phase === 'rest') {
        const st = rn.step, nb = x => (x || '').replace(/ /g, '');
        same('tag', rn.words.restTag, c.restTag);
        same('next', nb(rn.words.upNext + st.name + st.setText + ' · ' + st.prescription), nb(c.next));
        same('total', rn.rest.totalMs / 1000, gwrCurrent().restLength);
        same('dock', [rn.words.nextSet, rn.words.undo], c.dock);
      } else {
        const st = rn.step;
        same('step', st.heading + (c.live || ''), c.step);
        same('headingParts', st.heading.startsWith('Exercise ' + st.number + ' of ' + st.count), true);
        same('name', st.name, c.heading);
        same('rx', st.prescription, c.rx);
        same('sets', st.setText, c.sets);
        same('setParts', st.setText, 'Set ' + st.set + ' of ' + st.sets);
        same('pips', st.pips.map(x => x === 'done' ? 'done' : x === 'current' ? 'current' : ''), c.pips);
        if (rn.phase === 'ready') {
          same('hero', st.suggest ? st.suggest + st.unit : rn.words.checkHero, c.hero);
          same('last', st.lastReady, c.last || '');
          same('cue', rn.words.coach + st.cue, c.cue);
          same('dock', [st.timed ? rn.words.startTimer : rn.words.startSet, rn.words.swap, rn.words.skip], c.dock);
        } else if (rn.phase === 'active') {
          same('last', st.lastActive, c.last || '');
          const w = st.weight;
          same('fields', [...(st.kind === 'check' ? [] : [[st.kind === 'reps' ? rn.words.repsDone : st.label, st.suggest || '']]), ...(w ? [[rn.words.weight, w.suggest]] : [])], c.fields);
          same('dock', [rn.words.logSet, rn.words.back], c.dock);
        } else if (rn.phase === 'timing') {
          same('dock', [rn.words.timerStop, rn.words.timerCancel], c.dock);
          same('time', Math.round(rn.timer.remainingMs / 1000), Math.round((gwrCurrent().timerUntil - Date.now()) / 1000));
        } else if (rn.phase === 'timer-done') {
          same('review', [rn.review.time, rn.review.full ? rn.words.intervalDone : rn.words.stoppedEarly], c.confirm);
          same('dock', [rn.words.log + ' ' + rn.review.time, rn.words.restart], c.dock);
        }
      }
      return bad;
    };`;
  {
    const p = await start(b, ALL);
    await p.evaluate(COMPARE);
    const r1 = await p.evaluate(async () => {
      const { wait, count, showing, latest, run, until, classic } = __h, out = { bad: [], phases: [] }, TODAY = '2026-10-28';
      const d = planDay(TODAY), total = d.items.reduce((n, it) => n + it.n, 0);
      out.noOverlay = () => !document.getElementById('guidedWorkout').classList.contains('open') && !document.body.classList.contains('noscroll');
      const check = () => { const rn = latest().runner; out.bad.push(...__cmp(rn, classic())); out.phases.push(rn.phase); };
      const quiet = () => !document.getElementById('guidedWorkout').classList.contains('open') && !document.body.classList.contains('noscroll') && count('close') === 0 && showing();

      // the launcher starts a session: nothing opens on the page, the screen is sent the runner
      const launch = latest().weeks.flatMap(w => w.days).find(x => x && x.date === TODAY).guided;
      out.launcher = launch.button === 'Start' && launch.enabled;
      const wrong = await run('start', { date: '2026-10-29', op: 'x1' });
      out.startFuture = !wrong.reply.ok && /available training day/.test(wrong.reply.message) && !state.guidedWorkout;
      const rest = await run('start', { date: '2026-10-25', op: 'x2' }); // before the plan began: no such day
      out.startRest = !rest.reply.ok && !state.guidedWorkout;
      const st = await run('start', { date: TODAY, op: 'start-1' });
      out.startDebug = [st.reply, st.snapshot.runner && st.snapshot.runner.phase, state.guidedWorkout && state.guidedWorkout.phase, quiet(), count('close')];
      out.started = st.reply.ok && st.reply.changed && st.snapshot.runner.phase === 'intro' && state.guidedWorkout.date === TODAY && quiet();
      check();
      const rep = await run('start', { date: TODAY, op: 'start-1' });
      out.startRepeat = rep.reply.ok && rep.reply.changed === false && rep.reply.message === 'Already recorded.';

      // pick another day of the week, and back
      const mon = await run('date', { date: '2026-10-26', op: 'd1' });
      out.date = mon.reply.ok && mon.snapshot.runner.date === '2026-10-26' && mon.snapshot.runner.intro.days.find(x => x.selected).date === '2026-10-26';
      check();
      out.dateRefused = !(await run('date', { date: '2026-10-29' })).reply.ok;
      await run('date', { date: TODAY, op: 'd2' });

      const begin = await run('begin', { op: 'b1' });
      out.begin = begin.reply.ok && begin.snapshot.runner.phase === 'ready' && begin.snapshot.runner.step.number === 1 && begin.snapshot.runner.step.set === 1;
      check();
      out.saved = state.guidedWorkout.phase === 'ready' && JSON.parse(localStorage.getItem(STORE)).guidedWorkout.phase === 'ready';
      const early = await run('log', { amount: 5, op: 'l0' });
      out.logNotYet = !early.reply.ok && !pcheck(TODAY).s;
      // the first exercise: step through to its first logged set, whichever kind it is
      out.first = latest().runner.step;
      if (latest().runner.step.askDuration) { const go = await run('go', { amount: 0 }); out.goDuration = !go.reply.ok && /valid duration/.test(go.reply.message) && latest().runner.phase === 'ready' } else out.goDuration = true;
      const go2 = await run('go', latest().runner.step.askDuration ? { amount: 2, op: 'g1' } : { op: 'g1' });
      out.go = go2.reply.ok && go2.reply.changed && ['timing', 'active'].includes(go2.snapshot.runner.phase);
      check();
      out.quiet = quiet(); out.goDebug = [go2.reply, go2.snapshot.runner && go2.snapshot.runner.phase];
      return out;
    });
    ok('the launcher says Start; a future or rest day is refused and starts nothing', r1.launcher && r1.startFuture && r1.startRest, r1);
    ok('start runs gwrStart without opening the page\'s overlay; the screen is sent the intro', r1.started, r1);
    ok('starting again with the same operation is "Already recorded."', r1.startRepeat, r1);
    ok('the intro lets the day be chosen among the available days of the week', r1.date && r1.dateRefused, r1);
    ok('Start workout moves to the first set; the session is saved', r1.begin && r1.saved, r1);
    ok('logging a set that is not being done is refused', r1.logNotYet, r1);
    ok('a timer without a prescribed amount asks for a valid duration', r1.goDuration, r1);
    ok('Start set begins the set; the overlay, the page\'s scroll lock and the screen stay as they were', r1.go && r1.quiet, r1);
    ok('every phase shown so far reads as the page\'s own screen for it', r1.bad.length === 0, r1.bad);
    console.log('   phases: ' + r1.phases.join(', ') + '; go: ' + JSON.stringify(r1.goDebug));

    // the timer runs on the page: it ends by itself, and the screen is sent the review without asking
    const updates = await p.evaluate(() => __h.count('update'));
    await p.clock.runFor(305000);
    const r2 = await p.evaluate(async (updates) => {
      const { count, latest, classic } = __h, out = {};
      out.timerDone = latest().runner.phase === 'timer-done' && count('update') > updates && state.guidedWorkout.phase === 'timer-done';
      out.review = latest().runner.review.full === true && latest().runner.review.time === '05:00' && __cmp(latest().runner, classic()).length === 0;
      return out;
    }, updates);
    ok('a timer that runs out moves the session on, and the screen is sent it without asking', r2.timerDone, r2);
    ok('the finished interval reads as the page\'s review', r2.review, r2);

    // the whole session: each phase against the page's screen for it, until it completes
    const drive = `async (stopAt) => {
      const { latest, run, classic, count, showing } = __h, bad = [], phases = [], TODAY = '2026-10-28';
      let guard = 0, n = 0, celebrated = null, logs = 0;
      while (guard++ < 120) {
        const rn = latest().runner; if (!rn) break;
        bad.push(...__cmp(rn, classic())); phases.push(rn.phase);
        if (rn.phase === stopAt) break;
        if (rn.phase === 'complete') break;
        const st = rn.step, op = 'op-' + (++n);
        if (rn.phase === 'intro') await run('begin', { op });
        else if (rn.phase === 'ready') await run('go', st.askDuration ? { amount: 1, op } : { op });
        else if (rn.phase === 'timing') await run('stop', { op });
        else if (rn.phase === 'timer-done' || rn.phase === 'active') {
          const r = await run('log', { amount: st.kind === 'check' ? undefined : Number(st.suggest) || 8, weight: st.weight ? Number(st.weight.suggest) || 45 : undefined, op });
          logs++;
          if (r.reply.celebrations) celebrated = r.reply.celebrations;
          if (!r.reply.ok) bad.push('log refused: ' + JSON.stringify(r.reply));
        } else if (rn.phase === 'rest') await run('rest', { op });
      }
      return { bad, phases, celebrated, logs, quiet: !document.getElementById('guidedWorkout').classList.contains('open') && !document.body.classList.contains('noscroll') && count('close') === 0 && showing() };
    }`;
    await p.evaluate('window.__drive = ' + drive);
    const r3 = await p.evaluate(async () => __drive('rest'));
    ok('the phases up to the first rest read as the page\'s own screens', r3.bad.length === 0 && r3.phases.includes('rest'), r3.bad);
    const restUpdates = await p.evaluate(() => __h.count('update'));
    const restLeft = await p.evaluate(() => __h.latest().runner.rest);
    await p.clock.runFor(100000);
    const r4 = await p.evaluate(async (u) => ({ ready: __h.latest().runner.phase === 'ready', pushed: __h.count('update') > u, saved: JSON.parse(localStorage.getItem(STORE)).guidedWorkout.phase === 'ready' }), restUpdates);
    ok('a rest has the page\'s length and ends by itself, saved and sent to the screen', restLeft && restLeft.totalMs >= 15000 && restLeft.remainingMs > 0 && r4.ready && r4.pushed && r4.saved, { restLeft, r4 });
    const r5 = await p.evaluate(async () => {
      const out = await __drive(); const { latest, classic } = __h, TODAY = '2026-10-28', d = planDay(TODAY);
      const act = state.activities.filter(a => a.id === 'plan_' + TODAY);
      out.finished = latest().runner.phase === 'complete' && dayDone(TODAY) && act.length === 1 && act[0].xp === d.xp;
      out.celebration = !!out.celebrated && out.celebrated.length >= 1 && out.celebrated[0].xp === d.xp;
      out.noFx = document.querySelectorAll('.fx-xp').length === 0;
      out.details = d.items.every(it => Array.from({ length: it.n }, (_, i) => ((pcheck(TODAY).setDetails || {})[it.k] || [])[i]).every(Boolean));
      out.weights = d.items.filter(it => it.wt).every(it => /^[\d.]+ lb$/.test((pcheck(TODAY).v || {})[it.k]));
      out.history = state.workoutHistory.length === 1 && state.workoutHistory[0].date === TODAY && !state.workoutHistory[0].partial;
      out.complete = latest().runner.complete;
      const ex = await __h.run('exit', { op: 'exit-1' });
      out.exited = ex.reply.ok && ex.reply.changed && ex.snapshot.runner === null && state.guidedWorkout === null && ex.snapshot.session === null;
      const exAgain = await __h.run('exit', { op: 'exit-1' });
      out.exitRepeat = exAgain.reply.changed === false;
      return out;
    });
    ok('every phase of the whole session reads as the page\'s own screen for it', r5.bad.length === 0, r5.bad);
    ok('logging the last set completes the session: the day is done, its XP paid once, the history recorded', r5.finished && r5.history, r5);
    ok('the reply that completes the day carries the XP celebration, which the page does not draw', r5.celebration && r5.noFx, r5);
    ok('every set has its details and each weight is saved as the page saves it', r5.details && r5.weights, r5);
    ok('the page\'s overlay never opened, the page\'s scroll lock never set and the screen never closed', r5.quiet, r5);
    ok('Back to plan ends the session and the runner; a repeat changes nothing', r5.exited && r5.exitRepeat, r5);
    ok('no script errors', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* ---- acts one by one: refusals, repeats, undo, skip, swap, pause, leaving ---- */
  {
    const p = await start(b, ALL);
    await p.evaluate(COMPARE);
    const r = await p.evaluate(async () => {
      const { wait, count, showing, latest, run, classic } = __h, out = {}, DAY = '2026-10-26', d = planDay(DAY);
      const sets = () => Object.values(pcheck(DAY).s || {}).flat().filter(Boolean).length;
      const undo0 = await run('undo', { op: 'u0' });
      out.undoNothing = !undo0.reply.ok;
      await run('start', { date: DAY, op: 's' }); await run('begin', { op: 'b' });
      const now = () => latest().runner;
      // drive to the first set of an exercise that takes a weight, in the "active" phase
      let guard = 0;
      while (guard++ < 60 && !(now().phase === 'active' && now().step.weight)) {
        const rn = now(), st = rn.step, op = 'p' + guard;
        if (rn.phase === 'ready' && st.timed) await run('skip', { op }); // a timer needs the clock to run; the other test runs one out
        else if (rn.phase === 'ready') await run('go', { op });
        else if (rn.phase === 'active') await run('log', { amount: st.kind === 'check' ? undefined : Number(st.suggest) || 8, op });
        else if (rn.phase === 'rest') await run('rest', { op });
      }
      out.atWeighted = now().phase === 'active' && !!now().step.weight; out.where = [guard, now().phase, now().step && now().step.name, now().step && now().step.weight];
      const before = sets();
      const empty = await run('log', { amount: '', op: 'e1' });
      out.refuseEmpty = !empty.reply.ok && empty.reply.message === 'Enter what you actually completed' && empty.reply.changed !== true && sets() === before;
      const huge = await run('log', { amount: 9999, op: 'e2' });
      out.refuseHuge = !huge.reply.ok && huge.reply.message === 'Please check the reps or time.' && sets() === before;
      const heavy = await run('log', { amount: 8, weight: 5000, op: 'e3' });
      out.refuseWeight = !heavy.reply.ok && heavy.reply.message === 'Enter a valid weight in pounds' && sets() === before && !(pcheck(DAY).v || {})[now().step.key];
      out.refusedThenRetry = (await run('log', { amount: 8, weight: 60, op: 'e1' })).reply.ok; // a refused operation id may be used again
      out.logged = sets() === before + 1 && (pcheck(DAY).v || {})[d.items.find(it => it.wt).k] === '60 lb';
      const restNow = now().phase === 'rest';
      const repeat = await run('log', { amount: 8, weight: 60, op: 'e1' });
      out.repeat = repeat.reply.ok && repeat.reply.changed === false && repeat.reply.message === 'Already recorded.' && sets() === before + 1 && restNow;

      // undo takes the last guided set back, with the page's own function
      const u = await run('undo', { op: 'u1' });
      out.undo = u.reply.ok && u.reply.changed && sets() === before && now().phase === 'ready' && !(((pcheck(DAY).setDetails || {})[d.items.find(it => it.wt).k] || [])[0]);
      const ua = await run('undo', { op: 'u1' });
      out.undoRepeat = ua.reply.changed === false && sets() === before;
      // the step goes to the active set and back
      await run('go', { op: 'g' }); out.activeAgain = now().phase === 'active';
      const back = await run('back', { op: 'k' }); out.back = back.reply.ok && now().phase === 'ready';

      // substitute: recorded under the planned movement, the step shows the new name
      const key = now().step.key, swapAsk = now().step.swapAsk;
      out.swapEmpty = !(await run('swap', { name: '   ' })).reply.ok;
      const swap = await run('swap', { name: '  Leg press machine  ', op: 'w1' });
      out.swap = swap.reply.ok && swap.reply.changed && pcheck(DAY).substitute[key] === 'Leg press machine' && now().step.name === 'Leg press machine' && now().step.swapFrom === 'Leg press machine';
      let asked = ''; const realPrompt = window.prompt; window.prompt = (m, v) => { asked = m; return null };
      gwrSubstituteExercise(); window.prompt = realPrompt;
      out.swapAsk = asked === swapAsk;
      out.compareSwap = __cmp(now(), classic()).length === 0;

      // skip: the page's question is the screen's, the exercise is skipped and left out of the full-session XP
      let skipAsked = ''; const realConfirm = window.confirm; window.confirm = m => { skipAsked = m; return false };
      gwrSkipExercise(); window.confirm = realConfirm;
      out.skipAsk = skipAsked === now().step.skipAsk && /^Skip (optional )?exercise: /.test(skipAsked);
      const sk = await run('skip', { op: 'k1' });
      out.skip = sk.reply.ok && sk.reply.changed && state.guidedWorkout.skip[key] === true && now().step.key !== key && now().route.some(r => r.state === 'skipped');
      out.skipRow = !!now().itinerary.rows.find(r => r.status === 'SKIPPED') && __cmp(now(), classic()).length === 0;

      // pause: saved, the runner goes, the launcher offers Resume, and the clock stops
      const elapsed = now().elapsed;
      const pause = await run('pause', { op: 'pa1' });
      out.paused = pause.reply.ok && pause.reply.message === GWR_PAUSED && pause.snapshot.runner === null && state.guidedWorkout.phase === 'paused' && pause.snapshot.session && pause.snapshot.session.button === 'Resume';
      out.wasRunning = elapsed.running === true;
      const resume = await run('resume', { op: 'r1' });
      out.resumed = resume.reply.ok && resume.snapshot.runner && resume.snapshot.runner.phase === 'ready' && resume.snapshot.runner.elapsed.running && resume.snapshot.runner.elapsed.ms >= elapsed.ms;

      // leaving the tab with a session running pauses it (a clock must not run where nothing shows it)
      navigate('settings'); await wait(400);
      out.leavePauses = state.guidedWorkout.phase === 'paused' && !document.getElementById('guidedWorkout').classList.contains('open');
      navigate('plan'); await wait(600);
      out.comesBack = showing() && latest().runner === null && latest().session && latest().session.title === 'Workout in progress';

      // the resume offered by the launcher
      const res2 = await run('resume', { op: 'r2' });
      out.resume2 = res2.reply.ok && res2.snapshot.runner && ['ready', 'rest'].includes(res2.snapshot.runner.phase);

      // a save that fails: not saved, the session stays as the page holds it
      const real = Storage.prototype.setItem;
      Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
      const lost = await run('skip', { op: 'k2' });
      Storage.prototype.setItem = real;
      out.failedSave = !lost.reply.ok && lost.reply.message === 'Could not save to this browser (storage full?)' && !!lost.snapshot.runner;

      // a set logged while the save fails is in the page's memory: sending the same operation again must not log the next one
      let g1 = 0; while (g1++ < 8 && latest().runner.phase !== 'active') { const rn = latest().runner; if (rn.phase === 'rest') await run('rest', {}); else if (rn.phase === 'ready') await run(rn.step.timed ? 'skip' : 'go', { op: 'w' + g1 }); else break }
      if (latest().runner.phase === 'active') {
        const setsBefore = sets();
        Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
        const lostLog = await run('log', { amount: 8, weight: 50, op: 'fl' });
        Storage.prototype.setItem = real;
        out.lostLog = !lostLog.reply.ok && lostLog.reply.changed === true && sets() === setsBefore + 1;
        const again2 = await run('log', { amount: 8, weight: 50, op: 'fl' });
        out.lostLogRepeat = again2.reply.ok && again2.reply.changed === false && again2.reply.message === 'Already recorded.' && sets() === setsBefore + 1;
      } else out.lostLog = out.lostLogRepeat = 'no active set';

      // end the session partly: every remaining exercise skipped
      let g2 = 0; while (g2++ < 12 && latest().runner && latest().runner.phase !== 'complete') {
        const rn = latest().runner; if (rn.phase === 'rest') await run('rest', {}); else if (rn.phase === 'ready' || rn.phase === 'active') await run('skip', { op: 'sk' + g2 }); else if (rn.phase === 'timing') await run('cancel', {}); else await run('back', {});
      }
      const part = latest().runner;
      out.partial = !!part && part.phase === 'complete' && part.complete.full === false && !state.activities.some(a => a.id === 'plan_' + DAY) && __cmp(part, classic()).length === 0 && state.workoutHistory[0].partial === true;
      const reopen = await run('reopen', { op: 'ro' });
      out.reopen = reopen.reply.ok && reopen.snapshot.runner.phase === 'ready' && Object.keys(state.guidedWorkout.skip).length === 0;
      out.quiet = !document.getElementById('guidedWorkout').classList.contains('open') && !document.body.classList.contains('noscroll');
      return out;
    });
    ok('logging with nothing to undo is refused', r.undoNothing, r);
    ok('a set with nothing typed, a huge amount or a bad weight is refused with the page\'s words, saves nothing, and the operation may be used again', r.refuseEmpty && r.refuseHuge && r.refuseWeight && r.refusedThenRetry, r);
    console.log('   where: ' + JSON.stringify(r.where));
    ok('Log set records the set with its weight through the page\'s gwrLogSet and planToggle', r.atWeighted && r.logged, r);
    ok('the same Log set operation sent again is "Already recorded." and logs nothing more', r.repeat, r);
    ok('Undo last set takes the last guided set back through the page\'s function; its repeat changes nothing', r.undo && r.undoRepeat, r);
    ok('Start set and Back move between ready and active', r.activeAgain && r.back, r);
    ok('Swap records the movement under the planned one; empty is refused; its question is the page\'s', r.swap && r.swapEmpty && r.swapAsk && r.compareSwap, r);
    ok('Skip asks in the page\'s words (sent with the step), skips the exercise and marks it in the route', r.skipAsk && r.skip && r.skipRow, r);
    ok('Pause saves, ends the runner and offers Resume; Resume brings it back with the clock running', r.paused && r.wasRunning && r.resumed, r);
    ok('leaving the tab with a session running pauses it; coming back offers Resume', r.leavePauses && r.comesBack && r.resume2, r);
    ok('a failed save is not ok and keeps the session as the page holds it', r.failedSave, r);
    ok('a set logged while the save failed counts once: the same operation sent again is "Already recorded."', r.lostLog === true && r.lostLogRepeat === true, r);
    ok('ending with required sets skipped is a partial session: no XP, history marked, the page\'s screen agrees; Finish remaining sets reopens', r.partial && r.reopen, r);
    ok('the page\'s overlay and scroll lock stayed unused', r.quiet, r);
    ok('no script errors', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* ---- a screen or page without a capability hands over exactly as before ---- */
  {
    const p = await start(b, ['plan.projection.v1']);
    const r = await p.evaluate(async () => {
      const { wait, count, showing, latest, send, run } = __h, out = {}, TODAY = '2026-10-28';
      const s = latest();
      out.features = JSON.stringify(s.features) === JSON.stringify({ runner: false, fields: false, leave: false, offers: false }) && s.runner === null;
      const item = planDay(TODAY).items.find(i => i.wt);
      out.refused = [(await run('start', { date: TODAY })).reply.ok, (await send('value', { date: TODAY, key: item.k, text: '5' })).reply.ok, (await send('log', { date: TODAY, key: item.k, text: 'x' })).reply.ok,
        (await send('leave', {})).reply.ok, (await send('signUp', { start: '2026-11-04' })).reply.ok].every(x => x === false) && !!state.plan && !state.guidedWorkout && !pcheck(TODAY).v;
      // the guided workout still closes the screen and opens the page's overlay
      const closes = count('close');
      await Capacitor.Plugins.NativePlan.__emit('action', { action: 'guided', id: TODAY }); await wait(300);
      out.handsOver = count('close') === closes + 1 && document.getElementById('guidedWorkout').classList.contains('open') && state.guidedWorkout.date === TODAY;
      gwrPause(); await wait(400);
      // no plan: the page's offer stays, the screen does not open
      const opens = count('open');
      state.plan = null; save(); navigate('dashboard'); await wait(200); navigate('plan'); await wait(500);
      out.offerStaysWeb = count('open') === opens && !showing() && !!document.getElementById('planStart');
      return out;
    });
    ok('without the capabilities the page offers nothing to draw and refuses every new command', r.features && r.refused, r);
    ok('the guided workout still closes the screen and runs on the page', r.handsOver, r);
    ok('with no plan the page\'s own offer stays, as before', r.offerStaysWeb, r);
    ok('no script errors', p.errors.length === 0, p.errors);
    await p.context().close();
  }
  {
    // the 3D gym is the page's: while it is wanted the screen is not offered the runner and the command is refused
    const p = await start(b, ALL);
    const r = await p.evaluate(async () => {
      const { wait, latest, run } = __h, out = {};
      g3Want = () => true; renderPlan(); await wait(300);
      out.notOffered = latest().features.runner === false && latest().features.fields === true;
      const st = await run('start', { date: '2026-10-28', op: 'g3' });
      out.refused = !st.reply.ok && !state.guidedWorkout && !document.getElementById('guidedWorkout').classList.contains('open');
      return out;
    });
    ok('while the 3D gym is wanted the runner stays the page\'s: not offered, and the command is refused', r.notOffered && r.refused, r);
    ok('no script errors', p.errors.length === 0, p.errors);
    await p.context().close();
  }
  {
    // an offer-only screen: the offer without a plan is drawn; the other hand-overs stay the page's
    const p = await start(b, ['plan.projection.v1', 'plan.offers.v1'], false);
    const r = await p.evaluate(async () => {
      const { latest, send, count, showing } = __h, out = {};
      out.opens = showing() && !!latest().offer && JSON.stringify(latest().features) === JSON.stringify({ runner: false, fields: false, leave: false, offers: true });
      const up = await send('signUp', { start: '2026-10-28', op: 'o1' });
      out.signed = up.reply.ok && !!state.plan && !!up.snapshot.weeks && showing() && count('close') === 0;
      return out;
    });
    ok('a screen that draws only the offers opens with no plan and signs up', r.opens && r.signed, r);
    ok('no script errors', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
