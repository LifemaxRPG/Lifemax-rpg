/* The Workout plan's guided workout, fields, Leave plan and sign-up offers, as the page projects them to a screen that draws them
   (plan.runner.v1, plan.fields.v1, plan.leave.v1, plan.offers.v1): a fake NativePlan plugin with every capability stands in for
   the APK, the session is driven by the screen's own `runner` command, and each fixture is the projection the page sent at that
   moment. The replies the page gives (the envelope the plugin delivers) are kept in plan-replies.json.
     fixtures/plan-runner-{intro,ready,active,active-refused,check,timing,timer-done,timer-done-early,rest,complete,partial,partial-after}.json
     fixtures/plan-offer-{athlete,scholar,explorer}.json   fixtures/plan-replies.json
   Run through tools/gen_native_fixtures.js: node tools/gen_native_fixtures.js planrunner */
module.exports = async function planRunner({ b, out, write }) {
  const FAKES = `
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
      Plugins:{NativePlan:mk('plan',['plan.projection.v1','plan.runner.v1','plan.fields.v1','plan.leave.v1','plan.offers.v1']),NativeOverview:mk('overview',[])},registerPlugin:()=>({}),convertFileSrc:u=>u};`;
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') });
  await p.addInitScript(FAKES);
  await p.goto('file://' + out);
  await p.waitForFunction(() => { try { lmaLoad(); return typeof Capacitor !== 'undefined' } catch (e) { return false } }, null, { timeout: 60000 });
  await p.waitForTimeout(1500);
  const res = await p.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), P = Capacitor.Plugins.NativePlan, TODAY = '2026-10-28';
    const calls = () => window.__calls.filter(c => c[0] === 'plan');
    const latest = () => JSON.parse(JSON.stringify([...calls()].reverse().find(c => c[1] === 'open' || c[1] === 'update')[2].snapshot));
    let n = 0; const replies = {};
    const send = async (command, payload, keep) => {
      const id = 'f' + (++n);
      await P.__emit('command', { command, payload, requestId: id }); await wait(20);
      const u = [...calls()].reverse().find(c => c[1] === 'update' && c[2].reply && c[2].reply.requestId === id);
      const reply = JSON.parse(JSON.stringify(u[2].reply)); if (keep) replies[keep] = reply;
      return { reply, snapshot: JSON.parse(JSON.stringify(u[2].snapshot)) };
    };
    const run = (act, extra, keep) => send('runner', Object.assign({ act }, extra), keep);
    lmaThumbWarm = async () => { }; showLevelUp = function () { };
    const S = lmaLoad(); S.nativeOverview = false; lmaSave();
    await setNativePlanEnabled(true); navigate('plan');
    for (let t = 0; t < 8000 && !calls().some(c => c[1] === 'open'); t += 50) await wait(50);
    const shots = {};
    const keepShot = (name) => { shots[name] = latest() };

    // the plan as an exercise's fields leave it, and the replies of the fields
    const item = planDay(TODAY).items.find(i => i.wt);
    await send('value', { date: TODAY, key: item.k, text: '65 lb', op: 'fv1' }, 'value');
    await send('value', { date: TODAY, key: item.k, text: '65 lb', op: 'fv2' }, 'valueRepeat');
    await send('log', { date: TODAY, key: item.k, text: 'x'.repeat(401), op: 'fl1' }, 'logTooLong');
    await send('log', { date: TODAY, key: item.k, text: 'Felt strong, added 5 lb', op: 'fl2' }, 'log');
    await send('value', { date: '2026-10-29', key: planDay('2026-10-29').items.find(i => i.wt || i.min).k, text: '1', op: 'fv3' }, 'valueLocked');

    // the runner: intro, then the warm-up (timed), then the first weighted exercise
    await run('start', { date: TODAY, op: 'r1' }, 'start');
    keepShot('intro');
    await run('begin', { op: 'r2' });
    keepShot('ready');
    await run('go', { op: 'r3' });
    keepShot('timing');
    await run('stop', { op: 'r4' });
    shots['timer-done-early'] = latest();
    state.guidedWorkout.timerActualMs = state.guidedWorkout.timerTargetMs = 300000; // a full five minutes
    gwrRender(); await wait(200); shots['timer-done'] = latest();
    await run('log', { op: 'r5' }, 'logTimer');
    keepShot('rest');
    // up to a set of an exercise with a weight
    let guard = 0;
    while (guard++ < 80 && !(latest().runner.phase === 'active' && latest().runner.step.weight)) {
      const rn = latest().runner, st = rn.step, op = 'd' + guard;
      if (rn.phase === 'ready' && st.timed) { await run('skip', { op }) }
      else if (rn.phase === 'ready') await run('go', { op });
      else if (rn.phase === 'active') await run('log', { amount: Number(st.suggest) || 10, op });
      else if (rn.phase === 'rest') await run('rest', { op });
    }
    keepShot('active');
    await run('log', { amount: '', op: 'e1' }, 'logRefused');
    shots['active-refused'] = latest();
    await run('log', { amount: 8, weight: 65, op: 'e2' }, 'logSet');
    await run('log', { amount: 8, weight: 65, op: 'e2' }, 'logRepeat');
    // an exercise that is only confirmed
    guard = 0;
    while (guard++ < 80 && !(latest().runner.phase === 'ready' && latest().runner.step.kind === 'check')) {
      const rn = latest().runner, st = rn.step, op = 'c' + guard;
      if (rn.phase === 'ready') { if (st.kind === 'check') break; await run(st.timed ? 'skip' : 'go', { op }) }
      else if (rn.phase === 'active') await run('log', { amount: Number(st.suggest) || 10, weight: st.weight ? 45 : undefined, op });
      else if (rn.phase === 'rest') await run('rest', { op });
    }
    if (latest().runner.step.kind === 'check') { keepShot('check') }
    // the rest of the session with every exercise skipped from here: required sets are left, so it is a partial session
    guard = 0;
    while (guard++ < 120 && latest().runner && latest().runner.phase !== 'complete') {
      const rn = latest().runner, op = 'z' + guard;
      if (rn.phase === 'ready' || rn.phase === 'active') await run('skip', { op });
      else if (rn.phase === 'rest') await run('rest', { op });
      else if (rn.phase === 'intro') await run('begin', { op });
      else if (rn.phase === 'timer-done') await run('restart', { op });
      else if (rn.phase === 'timing') await run('cancel', { op });
    }
    keepShot('partial');
    await run('reopen', { op: 'ro1' }, 'reopen');
    keepShot('partial-after'); // Finish remaining sets: the skips are lifted, the session is ready again
    const done = latest();
    // put that session away (skip what is left, Back to plan), and run a complete one on Monday, every set logged
    guard = 0;
    while (guard++ < 60 && latest().runner && latest().runner.phase !== 'complete') {
      const rn = latest().runner, op = 'y' + guard;
      if (rn.phase === 'rest') await run('rest', { op }); else if (rn.phase === 'timer-done') await run('restart', { op }); else if (rn.phase === 'timing') await run('cancel', { op }); else await run('skip', { op });
    }
    await run('exit', { op: 'x1' });
    await run('start', { date: '2026-10-26', op: 'm1' });
    await run('begin', { op: 'm2' });
    guard = 0; let last;
    while (guard++ < 200 && latest().runner && latest().runner.phase !== 'complete') {
      const rn = latest().runner, st = rn.step, op = 'm' + (10 + guard);
      if (rn.phase === 'ready') await run('go', { op });
      else if (rn.phase === 'active') last = await run('log', { amount: st.kind === 'check' ? undefined : Number(st.suggest) || 10, weight: st.weight ? 45 : undefined, op }, 'logLast');
      else if (rn.phase === 'rest') await run('rest', { op });
      else if (rn.phase === 'timing') { state.guidedWorkout.timerUntil = Date.now() - 1; gwrTick(); await wait(60) }
      else if (rn.phase === 'timer-done') await run('log', { op });
      else if (rn.phase === 'intro') await run('begin', { op });
    }
    keepShot('complete');
    await run('exit', { op: 'x2' }, 'exit');

    // Leave plan, then the offers, then signing up
    const left = await send('leave', { op: 'lv1' }, 'leave');
    await wait(250); shots['offer-athlete'] = latest();
    await send('leave', { op: 'lv1' }, 'leaveRepeat');
    for (const c of ['scholar', 'explorer']) { state.classId = c; renderPlan(); await wait(250); shots['offer-' + c] = latest() }
    state.classId = 'athlete'; renderPlan(); await wait(250);
    await send('signUp', { start: 'tomorrow', op: 's1' }, 'signUpRefused');
    await send('signUp', { start: '2026-11-04', op: 's2' }, 'signUp');
    await send('signUp', { start: '2026-11-04', op: 's2' }, 'signUpRepeat');
    return { shots, replies, session: done.webSessionId };
  });
  for (const [name, data] of Object.entries(res.shots)) {
    Object.assign(data, { webSessionId: 'plan-fixture', sequence: 1 });
    if (data.selected) data.selected.revision = 1;
    write('fixtures/plan-' + (name.startsWith('offer') ? name : 'runner-' + name) + '.json', data);
  }
  write('fixtures/plan-replies.json', res.replies);
};
