/* Native Journal, phase 2: writing is native (plans/JOURNAL_AND_LIBRARY_SHEETS.md §3.4, §5; ADR 0013). The commands a screen that
   lists library.journal.write.v1 sends instead of handing over to the page's field: reflect and answer (the words, autosaved as
   final:false and paying nothing; the final write on leaving the field, the tab or the app, which may pay, once), claim and
   catchUp. Checked: the handshake that decides who types; autosave stores the words only (no full render, no reward, however long);
   the final write pays once in its own save and the reply carries the celebration; a repeat changes nothing; the pay window over
   midnight; conflict (the page holds neither the base nor the words); a failed save (ok only when stored, the words kept, the retry
   lands once); a reward that cannot be stored taken back alone; review answers; the welcome chips and the catch-up; locked (the
   words still stored, nothing private in the reply); nothing steps the screen aside. The 1.5 s debounce and the leave triggers are
   the screen's (JournalDraftTest, Kotlin); here the page is driven the way the screen drives it, at the times it drives it.
   Fake NativeJournal, NativeMedia, BiometricLock and FileSaver plugins stand in for Android; the page runs on a fixed clock
   (Wednesday 2026-10-28, 12:00 UTC), in en-US and UTC, on tests/fixtures/journal-lived.js.
   Run: NODE_PATH=$(npm root -g) node tests/native-journal-write.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
const { lived } = require('./fixtures/journal-lived.js');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 1200) : '')) } };
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const fakes = (WRITE) => `
  localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete',tipsSeen:{}}));
  window.__calls=[];window.__bio={secure:[],asked:0,next:()=>({ok:false,cancelled:true})};window.__saves=[];
  const mk=(name,caps)=>{const ls={};return {
    __emit:(e,d)=>Promise.all((ls[e]||[]).map(f=>f(d))),
    addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
    open:async a=>{window.__calls.push([name,'open',JSON.parse(JSON.stringify(a))])},
    update:async a=>{window.__calls.push([name,'update',JSON.parse(JSON.stringify(a))])},
    close:async a=>{window.__calls.push([name,'close',a]);(ls.closed||[]).forEach(f=>f({session:a&&a.session}))},
    swipeSettle:async a=>{window.__calls.push([name,'swipeSettle',a])},
    capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true,swipe:1}),
    handshake:async()=>({protocol:1,capabilities:caps})}};
  const P={NativeJournal:mk('journal',${WRITE?"['library.journal.projection.v1','library.journal.write.v1','shell.swipe.v1']":"['library.journal.projection.v1','shell.swipe.v1']"}),NativeMedia:mk('media',['library.media.projection.v1','shell.swipe.v1']),
    BiometricLock:{status:async()=>({available:true,kind:'ok'}),authenticate:async o=>{window.__bio.asked++;return window.__bio.next(o)},secure:async o=>{window.__bio.secure.push(o.on)}},
    FileSaver:{saveFile:o=>new Promise(r=>window.__saves.push(r))}};
  window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>!!P[n],getPlatform:()=>'android',Plugins:P,registerPlugin:()=>({}),convertFileSrc:u=>u};`;

/* In the page: helpers over the fake plugins' calls */
/* In the page: helpers over the fake plugins' calls, and the page's own Journal read back from its DOM */
const HELPERS = () => {
  window.wait = ms => new Promise(r => setTimeout(r, ms));
  window.count = (n, k) => __calls.filter(c => c[0] === n && c[1] === k).length;
  window.last = (n, k) => [...__calls].reverse().find(c => c[0] === n && (!k || c[1] === k));
  window.showing = (n = 'journal') => { const o = last(n, 'open'), c = last(n, 'close'); return !!o && (!c || __calls.lastIndexOf(o) > __calls.lastIndexOf(c)) };
  window.frames = (n = 'journal') => __calls.filter(c => c[0] === n && (c[1] === 'open' || c[1] === 'update')).map(c => c[2].snapshot);
  window.latest = (n = 'journal') => { const f = frames(n); return f[f.length - 1] };
  window.until = async (f, ms = 6000) => { for (let t = 0; t < ms && !f(); t += 40) await wait(40); return !!f() };
  window.J = Capacitor.Plugins.NativeJournal;
  let rid = 0;
  /* a command as the screen sends it; resolves to its reply (the page sends it with the next projection) */
  window.command = async (command, payload) => {
    const requestId = 'j' + (++rid), from = __calls.length;
    J.__emit('command', { command, payload, requestId });
    await until(() => __calls.slice(from).some(c => c[0] === 'journal' && c[2] && c[2].reply && c[2].reply.requestId === requestId), 8000);
    const c = __calls.slice(from).find(c => c[0] === 'journal' && c[2] && c[2].reply && c[2].reply.requestId === requestId);
    return c ? Object.assign({ frame: c[2].snapshot }, c[2].reply) : { none: true };
  };
};

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const open = async (write) => {
    const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
    const p = await ctx.newPage(); p.on('pageerror', e => errs.push(String(e))); p.on('console', m => { if (m.type() === 'warning' && /coach|journal/.test(m.text()) && !/\[save\] not stored/.test(m.text())) warns.push(m.text().slice(0, 300)) });
    await p.clock.setFixedTime(new Date('2026-10-28T12:00:00Z'));
    await p.addInitScript(fakes(write));
    await p.goto('file://' + file); await p.waitForFunction(() => window.LifeMaxCoach && window.NativeJournalProjection); await p.waitForTimeout(2200);
    await p.evaluate(HELPERS);
    const seed = await p.evaluate(`(${lived})()`);
    await p.evaluate(() => { lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { } const S = lmaLoad(); S.nativeOverview = false; S.nativeJournal = true; lmaSave(); navigate('dashboard'); navigate('history') });
    await p.evaluate(() => until(() => showing()));
    return { p, seed };
  };
  const errs = [], warns = [];

  /* ---------- the handshake decides who types ---------- */
  const old = await open(false);
  const pageMode = await old.p.evaluate(() => { const s = latest(); return { edit: s.edit, write: s.write } });
  ok('without library.journal.write.v1 in the handshake the frame says edit:"page", with no write words', pageMode.edit === 'page' && pageMode.write === null, pageMode);
  await old.p.context().close();

  const { p, seed } = await open(true);
  const Y = o => new Date(Date.UTC(2026, 9, 28 - o)).toISOString().slice(0, 10);
  const first = await p.evaluate(() => { const s = latest(); return { edit: s.edit, write: s.write } });
  ok('with it the frame says edit:"native" and carries the catch-up\'s words (the page\'s prompt, its 12-character minimum) and where the XP flies', first.edit === 'native' && same(first.write.catchUp, { prompt: 'One sentence: what have you been up to?', min: 12, max: 4000, ok: 'OK', cancel: 'Cancel' }) && typeof first.write.celebrationTarget.x === 'number' && typeof first.write.celebrationTarget.y === 'number', first);

  /* ---------- text-only autosave, then the final write that pays ---------- */
  const T = 'Reflecting on the whole day: the morning study block went well and I kept my phone away.';
  const auto = await p.evaluate(async ({ T }) => {
    const out = {}, d = getToday();
    await command('select', { key: d });
    delete state.dayReflections[d]; (state.journalCoach.days[d] || {}).xpAwarded = false; delete (state.journalCoach.days[d] || {}).xpAwarded;
    state.activities = state.activities.filter(a => a.type !== 'coach'); save(); await wait(150);
    const closes = count('journal', 'close'), xp0 = totalXP(), acts0 = state.activities.length;
    const real = renderHistory; let renders = 0; renderHistory = function () { renders++; return real.apply(this, arguments) };
    const set = localStorage.setItem.bind(localStorage); let writes = 0; localStorage.setItem = function () { writes++; return set.apply(this, arguments) };
    const stored = () => JSON.parse(localStorage.getItem('lifemax_rpg_prototype_v1')).dayReflections[d];
    try {
      const a = await command('reflect', { d, text: 'Short', base: '', final: false });
      out.a = [a.ok, a.changed, a.paid, a.celebrations.length, stored(), state.dayReflections[d]];
      out.aWork = [renders, writes, totalXP() - xp0, state.activities.length - acts0];
      const long = await command('reflect', { d, text: T, base: 'Short', final: false });
      out.long = [long.ok, long.changed, long.paid, long.celebrations.length, stored() === T];
      out.longWork = [renders, writes, totalXP() - xp0, state.activities.length - acts0, !!(state.journalCoach.days[d] || {}).xpAwarded];
      out.memo = latest().entry.reflect.text === T && latest().list.items.find(x => x.key === d).pen === true && latest().entry.reflect.awarded === false;
      const fin = await command('reflect', { d, text: T, base: T, final: true });
      out.fin = [fin.ok, fin.changed, fin.paid, fin.celebrations.map(c => [c.xp, c.gains.length, typeof c.gains[0].color, c.gains[0].short])];
      out.finWork = [totalXP() - xp0, state.activities.length - acts0, state.activities.filter(a => a.type === 'coach').length, (state.journalCoach.days[d] || {}).xpAwarded === true, storedAward()];
      function storedAward() { return JSON.parse(localStorage.getItem('lifemax_rpg_prototype_v1')).journalCoach.days[d].xpAwarded === true }
      out.toast = document.getElementById('toast').textContent;
      out.playedOnPage = celebrating();
      out.awardedFrame = latest().entry.reflect.awarded === true && latest().entry.reflect.meter === 100;
      const again = await command('reflect', { d, text: T, base: T, final: true });
      out.again = [again.ok, again.changed, again.paid, again.celebrations.length, totalXP() - xp0, state.activities.filter(a => a.type === 'coach').length];
      const tail = await command('reflect', { d, text: T + ' And an evening walk.', base: T, final: true });
      out.tail = [tail.ok, tail.changed, tail.paid, totalXP() - xp0];
    } finally { renderHistory = real; localStorage.setItem = set }
    out.stayed = count('journal', 'close') === closes && showing() && document.activeElement.tagName !== 'TEXTAREA';
    return out;
  }, { T });
  ok('autosave (final:false) stores the words only: ok, changed, no reward, no celebration, no full render', same(auto.a, [true, true, false, 0, 'Short', 'Short']) && auto.aWork[0] === 0 && auto.aWork[1] === 1 && auto.aWork[2] === 0 && auto.aWork[3] === 0, auto);
  ok('a long text autosaved still pays nothing (no XP mid-sentence), however long it is', same(auto.long, [true, true, false, 0, true]) && same(auto.longWork, [0, 2, 0, 0, false]), auto);
  ok('the projection after an autosave already has the words, the day\'s pen and an unpaid meter (the memo follows histRev)', auto.memo, auto);
  ok('the final write pays +15 once, in its own save, and the reply carries the celebration (xp 15 with its gains, colours as ARGB)', auto.fin[0] && auto.fin[1] === false && auto.fin[2] === true && auto.fin[3].length === 1 && auto.fin[3][0][0] === 15 && auto.fin[3][0][1] >= 1 && auto.fin[3][0][2] === 'number', auto.fin);
  ok('and stores the receipt (days[d].xpAwarded) with the coach activity: stored on disk, one coach activity, +15 XP', auto.finWork[0] === 15 && auto.finWork[1] === 1 && auto.finWork[2] === 1 && auto.finWork[3] && auto.finWork[4], auto.finWork);
  ok('the page says it in its words and does not play the orbs itself (the screen draws them)', auto.toast === '+15 XP · +2 bond · Reflection saved' && auto.playedOnPage === false && auto.awardedFrame, auto);
  ok('a repeat of the final write changes nothing and pays nothing ("Already recorded" is the screen\'s: ok, changed false, no celebration)', same(auto.again, [true, false, false, 0, 15, 1]), auto.again);
  ok('words added after the reward are stored and pay nothing more', same(auto.tail, [true, true, false, 15]), auto.tail);
  ok('nothing in a write steps the screen aside or focuses the page\'s field', auto.stayed, auto);

  /* ---------- a final write too short to pay, and the pay window over midnight ---------- */
  const D = '2026-10-28', short0 = 'Too short to pay.';
  const win = await p.evaluate(async ({ D, short0 }) => {
    const out = {};
    delete (state.journalCoach.days[D] || {}).xpAwarded; state.activities = state.activities.filter(a => a.type !== 'coach'); save(); await wait(100);
    const xp0 = totalXP(), r = await command('reflect', { d: D, text: short0, base: state.dayReflections[D] || '', final: true });
    out.short = [r.ok, r.paid, totalXP() - xp0];
    return out;
  }, { D, short0 });
  ok('a final write under 40 characters stores the words and pays nothing', win.short[0] === true && win.short[1] === false && win.short[2] === 0, win);
  await p.clock.setFixedTime(new Date('2026-10-28T23:59:58Z'));
  const text = 'Typed just before midnight: a long and honest paragraph about the big push at the gym.';
  const typed = await p.evaluate(async ({ D, text, short0 }) => { const a = await command('reflect', { d: D, text, base: short0, final: false }); return [a.ok, a.paid, totalXP()] }, { D, text, short0 });
  await p.clock.setFixedTime(new Date('2026-10-29T00:00:05Z'));
  const over = await p.evaluate(async ({ D, text }) => {
    const xp0 = totalXP(), r = await command('reflect', { d: D, text, base: text, final: true });
    return { r: [r.ok, r.paid, r.celebrations.length], gain: totalXP() - xp0 };
  }, { D, text });
  ok('autosave just before midnight pays nothing; the final write after it pays, the day typed on being yesterday by then', typed[0] === true && typed[1] === false && over.r[0] && over.r[1] && over.r[2] === 1 && over.gain === 15, { typed, over });
  await p.clock.setFixedTime(new Date('2026-10-31T10:00:00Z'));
  const late = await p.evaluate(async ({ D }) => {
    const xp0 = totalXP(), d = '2026-10-27', r = await command('reflect', { d, text: 'A long enough reflection typed on a day that is now four days old, so no reward is owed for it.', base: state.dayReflections[d] || '', final: true });
    return { r: [r.ok, r.paid], gain: totalXP() - xp0, stored: state.dayReflections[d] };
  }, { D });
  ok('a day older than yesterday stores the words and pays nothing', late.r[0] === true && late.r[1] === false && late.gain === 0 && /four days old/.test(late.stored), late);
  await p.clock.setFixedTime(new Date('2026-10-28T12:00:00Z'));

  /* ---------- conflict ---------- */
  const cf = await p.evaluate(async () => {
    const d = getToday(), out = {};
    state.dayReflections[d] = 'From the catch-up: back from a trip.'; save(); await wait(100);
    const before = JSON.stringify(state.dayReflections), w0 = JSON.parse(localStorage.getItem('lifemax_rpg_prototype_v1')).dayReflections[d];
    const r = await command('reflect', { d, text: 'My own new words for the day, typed over the old ones.', base: 'Short', final: true });
    out.r = [r.ok, r.changed, r.conflict, r.stored, r.message, r.paid];
    out.unchanged = JSON.stringify(state.dayReflections) === before && JSON.parse(localStorage.getItem('lifemax_rpg_prototype_v1')).dayReflections[d] === w0;
    const retry = await command('reflect', { d, text: 'My own new words for the day, typed over the old ones.', base: r.stored, final: false });
    out.retry = [retry.ok, retry.changed, state.dayReflections[d]];
    const same = await command('reflect', { d, text: 'My own new words for the day, typed over the old ones.', base: 'Something else entirely', final: false });
    out.sameWords = [same.ok, same.conflict];
    return out;
  });
  ok('conflict: the page holds neither the base nor the new words, so nothing is written, and the reply carries the stored words and the page\'s message', cf.r[0] === false && cf.r[1] === false && cf.r[2] === true && cf.r[3] === 'From the catch-up: back from a trip.' && cf.r[4] === 'This day was changed elsewhere. Your words are kept here; saving again will replace it.' && cf.r[5] === undefined && cf.unchanged, cf);
  ok('the retry with the stored words as its base goes through; words already stored are never a conflict', cf.retry[0] && cf.retry[1] && cf.retry[2].startsWith('My own new words') && cf.sameWords[0] === true && !cf.sameWords[1], cf);

  /* ---------- a save that fails: ok only when it was stored, the words kept, the retry lands once ---------- */
  const fl = await p.evaluate(async ({ d }) => {
    const out = {}, set = localStorage.setItem.bind(localStorage), STORE = 'lifemax_rpg_prototype_v1';
    const stored = () => JSON.parse(localStorage.getItem(STORE)).dayReflections[d];
    state.dayReflections[d] = 'Stored before.'; save(); await wait(100);
    let fail = true; localStorage.setItem = function (k, v) { if (fail) throw new Error('quota'); return set.apply(this, arguments) };
    try {
      const a = await command('reflect', { d, text: 'Typed while the storage is full.', base: 'Stored before.', final: false });
      out.a = [a.ok, a.changed, a.message, a.paid];
      out.memory = state.dayReflections[d]; out.disk = stored(); out.toast = document.getElementById('toast').textContent;
      fail = false;
      const b = await command('reflect', { d, text: 'Typed while the storage is full.', base: 'Stored before.', final: false });
      out.b = [b.ok, b.message];
      out.diskAfter = stored();
    } finally { localStorage.setItem = set }
    return out;
  }, { d: Y(2) });
  ok('a failed save answers ok:false with the page\'s words, keeps the words in memory and stores nothing', fl.a[0] === false && fl.a[2] === 'Could not save to this browser (storage full?)' && fl.memory === 'Typed while the storage is full.' && fl.disk === 'Stored before.' && fl.toast === 'Could not save to this browser (storage full?)', fl);
  ok('the same request sent again writes them (a repeat of words in memory is not "already stored" when their write failed)', fl.b[0] === true && fl.diskAfter === 'Typed while the storage is full.', fl);

  const pf = await p.evaluate(async ({ d }) => {
    const out = {}, set = localStorage.setItem.bind(localStorage), STORE = 'lifemax_rpg_prototype_v1';
    const text = 'A long enough reflection for yesterday: the words must survive a reward that cannot be stored.';
    delete (state.journalCoach.days[d] || {}).xpAwarded; state.activities = state.activities.filter(a => !(a.type === 'coach' && a.name === 'Journal reflection' && String(a.date).slice(0, 10) === getToday())); save(); await wait(100);
    const xp0 = totalXP(), acts0 = state.activities.length;
    let fail = true; localStorage.setItem = function (k, v) { if (fail && /"xpAwarded":true/.test(v)) throw new Error('quota'); return set.apply(this, arguments) };
    try {
      const a = await command('reflect', { d, text, base: state.dayReflections[d] || '', final: true });
      out.a = [a.ok, a.changed, a.paid, a.message, a.celebrations.length];
      out.kept = state.dayReflections[d] === text && JSON.parse(localStorage.getItem(STORE)).dayReflections[d] === text;
      out.takenBack = [totalXP() - xp0, state.activities.length - acts0, !!(state.journalCoach.days[d] || {}).xpAwarded];
      out.toast = document.getElementById('toast').textContent;
      fail = false;
      const b = await command('reflect', { d, text, base: text, final: true });
      out.b = [b.ok, b.paid, b.celebrations.length, totalXP() - xp0];
      const c = await command('reflect', { d, text, base: text, final: true });
      out.c = [c.ok, c.paid, totalXP() - xp0];
    } finally { localStorage.setItem = set }
    return out;
  }, { d: getTodayKey() });
  function getTodayKey() { return '2026-10-28' }
  ok('a reward that cannot be stored is taken back alone: the words stay (memory and disk), no XP, no activity, ok with paid:false and the page\'s words', pf.a[0] === true && pf.a[2] === false && pf.a[3] === 'Reflection saved · XP not saved (storage full?)' && pf.kept && same(pf.takenBack, [0, 0, false]) && pf.toast === 'Reflection saved · XP not saved (storage full?)', pf);
  ok('and a retry of the same final write pays it, once', pf.b[0] && pf.b[1] === true && pf.b[2] === 1 && pf.b[3] === 15 && pf.c[1] === false && pf.c[2] === 15, pf);

  /* ---------- review answers ---------- */
  const rv = await p.evaluate(async ({ key, week }) => {
    await command('select', { key });
    const out = {}, xp0 = totalXP(), A = 'Lights out by eleven, a walk after dinner, and no phone in the bedroom: that is the plan for the coming weeks.';
    const a = await command('answer', { key, text: 'Short note', base: '', final: false });
    out.a = [a.ok, a.changed, a.paid, a.celebrations.length, totalXP() - xp0];
    const b = await command('answer', { key, text: A, base: 'Short note', final: false });
    out.b = [b.ok, b.paid, totalXP() - xp0, (LifeMaxCoach.store().reviews[key] || {}).paid];
    const c = await command('answer', { key, text: A, base: A, final: true });
    out.c = [c.ok, c.paid, c.celebrations.map(x => x.xp), totalXP() - xp0, LifeMaxCoach.store().reviews[key].paid, c.frame.entry.paid, c.frame.entry.counter];
    const d = await command('answer', { key, text: A, base: A, final: true });
    out.d = [d.ok, d.changed, d.paid, totalXP() - xp0];
    const e = await command('answer', { key, text: 'x', base: 'not it', final: false });
    out.e = [e.ok, e.conflict, e.stored === A];
    const f = await command('answer', { key: 'M:1999-01', text: 'x', base: '', final: true });
    out.f = [f.ok, f.message];
    const g = await command('answer', { key: week, text: 'Another take on a week already paid, long enough to count, but the bonus was paid once already.', base: LifeMaxCoach.store().reviews[week].answer, final: true });
    out.g = [g.ok, g.paid, totalXP() - xp0];
    return out;
  }, { key: seed.month, week: seed.week });
  ok('a review answer: autosave pays nothing, the final write pays the review\'s bonus (50 for a month) once, in the reply\'s celebration', same(rv.a, [true, true, false, 0, 0]) && rv.b[0] && rv.b[1] === false && rv.b[2] === 0 && !rv.b[3] && rv.c[0] && rv.c[1] === true && same(rv.c[2], [50]) && rv.c[3] === 50 && rv.c[4] === true && rv.c[5] === true, rv);
  ok('its repeat pays nothing, a stale base is a conflict with the stored answer, an unknown review is refused, and a week already paid pays no more', rv.d[0] && rv.d[1] === false && rv.d[2] === false && rv.d[3] === 50 && rv.e[0] === false && rv.e[1] === true && rv.e[2] && rv.f[0] === false && rv.f[1] === 'That day is not in your Journal.' && rv.g[0] && rv.g[1] === false && rv.g[2] === 50, rv);

  /* ---------- the welcome chips and the catch-up ---------- */
  const wl = await p.evaluate(async () => {
    const out = {}, at = o => new Date(Date.now() - o * 864e5).toISOString();
    state.activities = []; state.dayReflections = {}; state.journalCoach = {};
    for (let o = 6; o <= 20; o++) state.activities.push({ id: 'w' + o, type: 'custom', name: 'Study', icon: 'book', duration: 30, quality: 1, skill: 'Knowledge', primary: 'intelligence', secondary: '', xp: 100, date: at(o), notes: '', xpSplit: [{ attr: 'intelligence', xp: 100 }] });
    state.activities.push({ id: 'w0', type: 'custom', name: 'Back at it', icon: 'book', duration: 30, quality: 1, skill: 'Back', primary: 'discipline', secondary: '', xp: 60, date: at(0), notes: '', xpSplit: [{ attr: 'discipline', xp: 60 }] });
    histSel = getToday(); save(); await until(() => latest().entry && latest().entry.welcome, 3000);
    const closes = count('journal', 'close'), xp0 = totalXP();
    out.tier = latest().entry.welcome.tier;
    const wrong = await command('claim', { kind: 'fresh' });
    out.wrong = [wrong.ok, wrong.message, totalXP() - xp0];
    const a = await command('claim', { kind: 'pet' });
    out.a = [a.ok, a.changed, a.paid, a.celebrations.map(x => x.xp), totalXP() - xp0, a.frame.entry.welcome.chips[0].done, document.getElementById('toast').textContent];
    const again = await command('claim', { kind: 'pet' });
    out.again = [again.ok, again.changed, again.message, totalXP() - xp0];
    const bad = await command('claim', { kind: 'nope' });
    out.bad = [bad.ok, bad.message];
    // tier 2: the catch-up
    state.journalCoach = {}; state.activities = state.activities.filter(x => x.type !== 'coach');
    state.activities.forEach(x => { if (x.id !== 'w0') x.date = new Date(Date.parse(x.date) - 4 * 864e5).toISOString() });
    save(); await until(() => latest().entry && latest().entry.welcome && latest().entry.welcome.tier === 2, 3000);
    const xp1 = totalXP();
    const sh = await command('catchUp', { text: 'too short' });
    out.short = [sh.ok, sh.message, totalXP() - xp1, state.dayReflections[getToday()] || null];
    const S = 'I was travelling for work all week long.';
    const set = localStorage.setItem.bind(localStorage), STORE = 'lifemax_rpg_prototype_v1';
    localStorage.setItem = function (k, v) { if (/"claimed":\{"line":1/.test(v)) throw new Error('quota'); return set.apply(this, arguments) };
    let f;
    try { f = await command('catchUp', { text: S }) } finally { localStorage.setItem = set }
    out.failed = [f.ok, f.paid, f.message, totalXP() - xp1, state.dayReflections[getToday()], JSON.parse(localStorage.getItem(STORE)).dayReflections[getToday()]];
    const c = await command('catchUp', { text: S });
    out.c = [c.ok, c.changed, c.paid, c.celebrations.map(x => x.xp), totalXP() - xp1, state.dayReflections[getToday()], c.frame.entry.welcome.chips.find(x => x.id === 'line').done];
    const r = await command('catchUp', { text: S });
    out.repeat = [r.ok, r.changed, r.message, totalXP() - xp1, state.dayReflections[getToday()]];
    out.stayed = count('journal', 'close') === closes && showing();
    return out;
  });
  ok('claim: a chip the card does not offer is refused; the offered one pays once with its celebration and the page\'s toast; a repeat is "Already recorded."', wl.tier === 1 && wl.wrong[0] === false && wl.wrong[1] === 'That is not offered today.' && wl.wrong[2] === 0 && wl.a[0] && wl.a[1] && wl.a[2] === true && same(wl.a[3], [10]) && wl.a[4] === 10 && wl.a[5] === true && wl.a[6] === '+10 XP · Welcome back' && wl.again[0] && wl.again[1] === false && wl.again[2] === 'Already recorded.' && wl.again[3] === 10 && wl.bad[0] === false, wl);
  ok('catch-up: under 12 characters is refused in the page\'s words and writes nothing', wl.short[0] === false && wl.short[1] === 'Write a full sentence (12+ characters)' && wl.short[2] === 0 && wl.short[3] === null, wl.short);
  ok('catch-up whose reward cannot be stored keeps the sentence (memory and disk) and pays nothing; the retry pays +20 once and does not add the sentence twice', wl.failed[0] === true && wl.failed[1] === false && /XP not saved/.test(wl.failed[2]) && wl.failed[3] === 0 && wl.failed[4] === wl.failed[5] && wl.c[0] && wl.c[2] === true && same(wl.c[3], [20]) && wl.c[4] === 20 && wl.c[5] === 'I was travelling for work all week long.' && wl.c[6] === true, wl);
  ok('a repeat of the catch-up changes nothing', wl.repeat[0] && wl.repeat[1] === false && wl.repeat[2] === 'Already recorded.' && wl.repeat[3] === 20 && wl.repeat[4] === 'I was travelling for work all week long.', wl.repeat);
  ok('no write, claim or catch-up closed the screen or took the page\'s focus', wl.stayed, wl);

  /* ---------- the flush of a screen that has just closed: the page plays the celebration, once ---------- */
  const fh = await p.evaluate(async () => {
    const out = {}, d = getToday();
    const text = 'Typed just as the owner switched tabs: a long enough paragraph about the day that was.';
    delete state.dayReflections[d]; delete (state.journalCoach.days[d] || {}).xpAwarded; state.activities = state.activities.filter(a => a.type !== 'coach'); save(); await wait(150);
    navigate('dashboard'); await until(() => !showing(), 3000);
    const xp0 = totalXP(), seq = __calls.length;
    J.__emit('command', { command: 'reflect', payload: { d, text, base: '', final: true }, requestId: 'flush1' });
    await until(() => totalXP() > xp0, 3000);
    out.paid = [totalXP() - xp0, state.dayReflections[d] === text, (state.journalCoach.days[d] || {}).xpAwarded === true];
    out.playedOnPage = celebrating();
    out.noUpdateToClosedScreen = !__calls.slice(seq).some(c => c[0] === 'journal' && c[1] === 'update');
    J.__emit('command', { command: 'reflect', payload: { d, text, base: text, final: true }, requestId: 'flush2' });
    await wait(300);
    out.once = totalXP() - xp0;
    navigate('history'); await until(() => showing(), 5000);
    out.back = latest().entry.reflect.text === text && latest().entry.reflect.awarded === true;
    return out;
  });
  ok('a flush after the screen closed stores the words and pays once; the page plays the celebration (no screen is showing it), and nothing is sent to the closed screen', fh.paid[0] === 15 && fh.paid[1] && fh.paid[2] && fh.playedOnPage && fh.noUpdateToClosedScreen && fh.once === 15 && fh.back, fh);

  /* ---------- the write command is the page-field path only ---------- */
  const wc = await p.evaluate(async () => { const r = await command('write', { key: getToday() }); return { ok: r.ok, focused: document.activeElement && document.activeElement.tagName, showing: showing() } });
  ok('write (the step-aside to the page\'s field) is refused once the screen types for itself, and focuses nothing', wc.ok === false && wc.focused !== 'TEXTAREA' && wc.showing, wc);

  /* ---------- locked: the words are still stored, nothing private comes back ---------- */
  const lk = await p.evaluate(async () => {
    const out = {}, d = getToday();
    state.dayReflections[d] = 'Canary stored word.'; delete (state.journalCoach.days[d] || {}).xpAwarded; save(); await wait(100);
    state.journalLock = { on: true }; jlUnlocked = true; save(); await wait(150);
    LifeMaxJournalLock.relock(); await until(() => latest().withheld, 2000);
    const xp0 = totalXP();
    const a = await command('reflect', { d, text: 'Typed at the moment the app was left, with the field still open: this is a long one.', base: 'Canary stored word.', final: true });
    out.a = [a.ok, a.paid, 'celebrations' in a, a.frame.withheld, JSON.stringify(a).includes('Canary'), totalXP() - xp0, state.dayReflections[d]];
    const c = await command('reflect', { d, text: 'Another try with a stale base.', base: 'stale', final: false });
    out.conflict = [c.ok, c.conflict, c.stored, JSON.stringify(c).includes('Typed at the moment')];
    const cl = await command('claim', { kind: 'pet' }), cu = await command('catchUp', { text: 'A sentence long enough to count.' });
    out.refused = [cl.ok, cl.message, cu.ok, cu.message];
    state.journalLock = { on: false }; save(); LifeMaxJournalLock.apply(); await wait(200);
    return out;
  });
  ok('locked: the owner\'s words are still stored (and paid, the page playing it unseen) and the reply carries no celebration and no word of the Journal', lk.a[0] && lk.a[1] === true && lk.a[2] === false && lk.a[3] === true && lk.a[4] === false && lk.a[5] === 15 && /^Typed at the moment/.test(lk.a[6]), lk);
  ok('locked: a conflict carries no stored words, and claim and catch-up are refused', lk.conflict[0] === false && lk.conflict[1] === true && lk.conflict[2] === '' && lk.conflict[3] === false && lk.refused[0] === false && lk.refused[1] === 'Unlock the Journal first.' && lk.refused[2] === false, lk);

  ok('no page errors', errs.length === 0, errs);
  ok('no warnings from the coach or the Journal', warns.length === 0, warns);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
