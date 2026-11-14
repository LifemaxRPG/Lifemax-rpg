/* The Habits tab (src/features/habits): habit stacks as towers. This checks the model and the commands, on the app's own clock:
   - an old save with no habits opens the tab empty, and reading it never creates state.habits;
   - a foundation, a habit and a habit to break are stored under state.habits (v 1) by writeSave, and survive a reload;
   - laying a block counts a rep once a day, the material follows total reps, and a new habit starts at its two-minute size;
   - one missed day is a crack, two is a wobble, a missed day never takes a rep away, and coming back leaves a gold seam;
   - a rough day lays the two-minute size without changing the habit's own size;
   - a habit to break loses a floor per clean day and ends cleared;
   - the rooftop reward can be claimed only once the stack is done, once a day;
   - a save that is not stored takes the change back and says so;
   - the header counts today's blocks of the habits being built ("N / M today"); a clean day on a habit being broken is not one;
   - the screen: the big button lays the next block, a tap on a block opens its dials (on screen, above the bottom bar), the city
     view and its time-lapse draw; towers and the sky keep their nodes when the stack changes, so they move instead of being
     redrawn; a tower too tall for the scene keeps a readable size and scrolls;
   - XP, Sparks and activities are untouched by all of it.
   Run: NODE_PATH=$(npm root -g) node tests/habits.test.js [built html] */
const { chromium } = require('playwright');
const currentPage = require('./current-page'); const file = currentPage(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 600) : '')) } };
const DAY = 24 * 3600 * 1000;
(async () => {
  const b = await chromium.launch(); const srv = await currentPage.serve(file);
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
  const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.clock.install({ time: new Date('2026-03-02T09:00:00Z') });
  await p.addInitScript(`if(!localStorage.getItem('lifemax_rpg_prototype_v1'))localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));`);
  await p.goto(srv.url); await p.clock.runFor(3000);
  const open = async () => { await p.evaluate(() => { navigate('habits'); LifeMaxFeatures.open('habits') }); await p.clock.runFor(700) };
  const tomorrow = async () => { await p.clock.fastForward(DAY); await p.evaluate(() => Habits.render()) };
  await open();
  const xp0 = await p.evaluate(() => JSON.stringify([state.activities.length, state.attrXP, state.sparks]));
  ok('an old save opens the tab empty and reading creates nothing', await p.evaluate(() => Habits.model().stacks.length === 0 && state.habits === undefined && !!document.querySelector('#view-habits .hb-strike[data-act=newstack]')));
  ok('the tab is on the bar', await p.evaluate(() => !!document.querySelector('.navbtn[data-view=habits]')));

  const ids = await p.evaluate(() => {
    const s = Habits.addStack('After coffee'), r = Habits.addHabit(s, { name: 'Read 10 pages', small: 'Read 1 page' }), j = Habits.addHabit(s, { name: 'Journal 5 min' });
    const e = Habits.addStack('At lights out'), f = Habits.addHabit(e, { name: 'Phone in bed', kind: 'fade', floors: 3 });
    return { s, r, j, e, f };
  });
  let m = await p.evaluate(() => Habits.model());
  ok('a foundation and its habits are stored, v 1', await p.evaluate(() => state.habits.v === 1 && JSON.parse(localStorage.getItem('lifemax_rpg_prototype_v1')).habits.items.length === 3));
  ok('a new habit starts at its two-minute size, glass, nothing laid', m.stacks[0].blocks[0].name === 'Read 1 page' && m.stacks[0].blocks[0].mat === 'glass' && m.laid === 0 && m.stacks[0].next.id === ids.r, m.stacks[0]);
  ok('empty names and unknown stacks are refused', await p.evaluate(() => Habits.addStack('  ') === null && Habits.addHabit('nope', { name: 'x' }) === null && Habits.addHabit(state.habits.stacks[0].id, { name: '' }) === null));

  ok('laying a block counts once a day', await p.evaluate(id => Habits.lay(id) === true && Habits.lay(id) === null && Habits.model().stacks[0].blocks[0].reps === 1 && Habits.model().stacks[0].blocks[0].done, ids.r));
  ok('undo takes today’s block back', await p.evaluate(id => Habits.undo(id) === true && Habits.model().stacks[0].blocks[0].reps === 0 && Habits.lay(id) === true, ids.r));
  await tomorrow(); m = await p.evaluate(() => Habits.model());
  ok('a new day: nothing laid, the rep is kept, the one not done yesterday is cracked', m.laid === 0 && m.stacks[0].blocks[0].reps === 1 && !m.stacks[0].blocks[0].done && m.stacks[0].blocks[0].miss === 0 && m.stacks[0].blocks[1].miss === 1, m.stacks[0].blocks);
  await tomorrow(); m = await p.evaluate(() => Habits.model());
  ok('two missed days wobble and take no rep away', m.stacks[0].blocks[0].miss === 1 && m.stacks[0].blocks[0].reps === 1 && m.stacks[0].blocks[1].miss === 2, m.stacks[0].blocks);
  ok('coming back leaves a gold seam', await p.evaluate(id => Habits.lay(id) && Habits.model().stacks[0].blocks[1].scar === true && Habits.model().stacks[0].blocks[1].miss === 0, ids.j));

  ok('a block moves earlier or later within its stack', await p.evaluate(({ r, j }) => {
    const order = () => Habits.model().stacks[0].blocks.map(b => b.id).join();
    return Habits.move(j, -1) === true && order() === j + ',' + r && Habits.move(j, -1) === null && Habits.move(j, 1) === true && order() === r + ',' + j;
  }, ids));
  ok('size is the habit’s own; a rough day lays the two-minute one and leaves it alone', await p.evaluate(id => {
    Habits.editHabit(id, { size: 2, names: ['Read 1 page', 'Read 10 pages', 'Read 30 min'] }); const big = Habits.model().stacks[0].blocks[0].name;
    Habits.setRough(true); const small = Habits.model().stacks[0].blocks[0].name; Habits.lay(id);
    const logged = state.habits.log[getToday()][id]; Habits.setRough(false);
    return big === 'Read 30 min' && small === 'Read 1 page' && logged === 1 && state.habits.items.find(x => x.id === id).size === 2 && Habits.model().stacks[0].blocks[0].name === 'Read 1 page';
  }, ids.r));

  ok('the material follows total reps', await p.evaluate(id => {
    const t = getToday(); for (let i = 1; i <= 70; i++) (state.habits.log[addDays(t, -i - 5)] = state.habits.log[addDays(t, -i - 5)] || {})[id] = 2;
    state.habits.items.find(x => x.id === id).born = addDays(t, -80);
    const at = n => Habits.model(addDays(t, -76 + n)).stacks[0].blocks[0].mat;
    return [at(3), at(12), at(40), Habits.model().stacks[0].blocks[0].mat].join() === 'glass,clay,stone,gold';
  }, ids.r));

  ok('the rooftop reward waits for the whole stack, then is claimed once', await p.evaluate(s => {
    Habits.editStack(s, { treat: 'One episode' }); const st = () => Habits.model().stacks[0];
    return !st().next && Habits.claim(s) === true && st().claimed && Habits.claim(s) === null;
  }, ids.s));
  await tomorrow();
  ok('tomorrow it is locked again until the stack is done', await p.evaluate(s => { const st = Habits.model().stacks[0]; return !st.claimed && !!st.next && Habits.claim(s) === null }, ids.s));

  ok('a clean day on a habit to break is not a block laid', await p.evaluate(f => {
    const a = Habits.model(); Habits.lay(f); const b = Habits.model(); Habits.undo(f);
    return b.laid === a.laid + 1 && b.built === a.built && a.due === 2 && b.due === 2;
  }, ids.f));
  ok('a habit to break loses a floor per clean day and ends cleared', await (async () => {
    const out = [];
    for (let i = 0; i < 3; i++) { out.push(await p.evaluate(f => { Habits.lay(f); const x = Habits.model().stacks[1].blocks[0]; return x.left + ':' + x.cleared }, ids.f)); await tomorrow() }
    return out.join() === '2:false,1:false,0:true' && await p.evaluate(() => Habits.model().stacks[1].next === null);
  })());

  ok('a save that is not stored takes the change back and says so', await p.evaluate(() => {
    const before = JSON.stringify(state.habits), real = Storage.prototype.setItem; let said = ''; const t = window.toast; window.toast = s => { said = s };
    Storage.prototype.setItem = () => { throw new Error('full') };
    const r = [Habits.addStack('After lunch'), Habits.lay(state.habits.items[0].id), Habits.setWho('a reader')];
    Storage.prototype.setItem = real; window.toast = t;
    return r.every(x => x === null) && JSON.stringify(state.habits) === before && said.length > 0;
  }));

  // the screen
  await p.evaluate(() => Habits.setWho('a reader')); await open();
  ok('the header says who and counts today', await p.evaluate(() => document.querySelector('.hb-who b').textContent === 'a reader' && document.querySelector('.hb-count').textContent.replace(/\s+/g, ' ').trim() === Habits.model().built + ' / ' + Habits.model().due + ' today'));
  const before = await p.evaluate(() => Habits.model().laid);
  await p.click('#view-habits .hb-strike[data-act=lay]'); await p.clock.runFor(700);
  ok('the big button lays the next block, and the header counts it', await p.evaluate(n => Habits.model().laid === n + 1 && document.querySelector('.hb-count strong').textContent === String(Habits.model().built) && Habits.model().built === 1, before));
  await p.evaluate(() => document.querySelector('#view-habits .hb-tower[data-i="0"] [data-b]').dispatchEvent(new MouseEvent('click', { bubbles: true }))); await p.clock.runFor(300);
  ok('a tap on a block opens its four dials', await p.evaluate(() => document.querySelectorAll('#view-habits .hb-knob').length === 4));
  ok('the dials are on screen above the bottom bar, and the rough-day pill makes room', await p.evaluate(() => [...document.querySelectorAll('#view-habits .hb-knob .dial')].every(d => { const r = d.getBoundingClientRect(); return r.top > 0 && r.bottom < innerHeight - 64 }) && !document.querySelector('#view-habits .hb-rough') && !!document.querySelector('#view-habits .hb-knob .dial svg')));
  await p.click('#view-habits .hb-knob[data-k=size]'); await p.clock.runFor(200);
  ok('the size dial turns the habit down a size', await p.evaluate(id => state.habits.items.find(x => x.id === id).size === 1, ids.r));
  const stood = await p.evaluate(() => { window.__tw = [...document.querySelectorAll('#view-habits .hb-tower')]; window.__sky = document.querySelector('#view-habits .hb-bg').firstChild; return window.__tw.map(g => g.style.transform).join('|') });
  await p.click('#view-habits .hb-nav.next'); await p.clock.runFor(600);
  ok('towers and the sky keep their nodes across a stack switch, and move', await p.evaluate(was => {
    const g = [...document.querySelectorAll('#view-habits .hb-tower')];
    return g.length === 2 && g.every((x, i) => x === window.__tw[i]) && g.map(x => x.style.transform).join('|') !== was && g[1].dataset.i === '1' && document.querySelector('#view-habits .hb-bg').firstChild === window.__sky;
  }, stood));
  await p.click('#view-habits .hb-nav.prev'); await p.clock.runFor(600);
  await p.click('#view-habits [data-act=view]'); await p.clock.runFor(700);
  ok('the city view shows every tower and a time-lapse', await p.evaluate(() => document.querySelectorAll('#view-habits .hb-tower').length === 2 && !!document.querySelector('#view-habits [data-act=lapse]') && !document.querySelector('#view-habits .hb-strike')));
  ok('a habit is not drawn before its first day', await p.evaluate(() => Habits.model(addDays(getToday(), -30)).stacks[1].blocks.length === 0));
  await p.click('#view-habits [data-act=view]'); await p.clock.runFor(400);
  await p.click('#view-habits [data-act=who]'); await p.clock.runFor(200);
  await p.fill('.hb-host .hb-sheet input', 'an athlete who reads'); await p.click('.hb-host .hb-go'); await p.clock.runFor(200);
  ok('a sheet saves what was typed and closes', await p.evaluate(() => state.habits.who === 'an athlete who reads' && !document.querySelector('.hb-sheet')));
  ok('the sky does not start a tab swipe', await p.evaluate(() => lmaSwipeBlocked(document.querySelector('#view-habits .hb-sky svg')) === true));

  ok('XP, Sparks and activities are untouched', await p.evaluate(x => JSON.stringify([state.activities.length, state.attrXP, state.sparks]) === x, xp0));
  await p.reload(); await p.clock.runFor(3000);
  ok('it survives a reload', await p.evaluate(() => Habits.model().stacks.length === 2 && Habits.model().who === 'an athlete who reads'));
  ok('retiring a habit removes it and its log', await p.evaluate(id => Habits.retire(id) === true && !JSON.stringify(state.habits).includes('"' + id + '"'), ids.j));
  ok('a foundation with habits is not removed; an empty one is', await p.evaluate(({ s, e }) => Habits.removeStack(s) === null && Habits.retire(state.habits.items.find(x => x.stack === e).id) && Habits.removeStack(e) === true, ids));
  await open();
  ok('a tall tower keeps a readable size and scrolls with a vertical drag', await p.evaluate(() => {
    const s = Habits.addStack('After lunch'); for (let i = 0; i < 7; i++) Habits.addHabit(s, { name: 'Habit ' + i }); Habits.render();
    for (let b; (b = document.querySelector('#view-habits .hb-nav.next'));) b.click();
    const sky = document.querySelector('#view-habits .hb-sky'), g = document.querySelector('#view-habits .hb-tower[data-id="' + s + '"]'), at = () => g.style.transform;
    const scale = +/scale\(([\d.]+)\)/.exec(at())[1], was = at(), ev = (t, y) => sky.querySelector('svg').dispatchEvent(new PointerEvent(t, { bubbles: true, clientX: 200, clientY: y, pointerType: 'touch' }));
    ev('pointerdown', 300); ev('pointermove', 330); ev('pointermove', 360); ev('pointerup', 360);
    return scale >= 0.9 && sky.classList.contains('tall') && at() !== was && Habits.removeStack(s) === null;
  }));
  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); srv.close(); process.exit(fail ? 1 : 0);
})();
