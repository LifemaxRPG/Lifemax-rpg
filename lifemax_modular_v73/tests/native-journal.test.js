/* Native Journal (Library's Journal pane), web half (plans/JOURNAL_AND_LIBRARY_SHEETS.md §3, §5): the projection a native screen
   draws from, checked against the page's own Journal for every kind of day and review; that building it changes nothing; the memo of
   the hero and the list; select; the lock (a withheld frame with no private string, a relock pushed at once, the prompt's status on
   the card, unlock, every other command refused); when the screen covers the page and when it steps aside; write and the hand-backs
   with their way back after the celebrations; the queue under a pending Save as; the shared notices; Media's switch; the swipe.
   Fake NativeJournal, NativeMedia, BiometricLock and FileSaver plugins stand in for Android. The page runs on a fixed clock
   (Wednesday 2026-10-28, 12:00 UTC), in en-US and UTC, on the lived-in journal of tests/fixtures/journal-lived.js.
   Run: NODE_PATH=$(npm root -g) node tests/native-journal.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
const { lived } = require('./fixtures/journal-lived.js');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 1200) : '')) } };
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const FAKES = `
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
  const P={NativeJournal:mk('journal',['library.journal.projection.v1','shell.swipe.v1']),NativeMedia:mk('media',['library.media.projection.v1','shell.swipe.v1']),
    BiometricLock:{status:async()=>({available:true,kind:'ok'}),authenticate:async o=>{window.__bio.asked++;return window.__bio.next(o)},secure:async o=>{window.__bio.secure.push(o.on)}},
    FileSaver:{saveFile:o=>new Promise(r=>window.__saves.push(r))}};
  window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>!!P[n],getPlatform:()=>'android',Plugins:P,registerPlugin:()=>({}),convertFileSrc:u=>u};`;

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
  const text = el => el ? el.textContent.replace(/\s+/g, ' ').trim() : null;
  const col = el => NativeTabKit.color(el.style.getPropertyValue('--c').trim() || el.style.getPropertyValue('--jc').trim());
  /* the page's Journal as the projection would say it */
  window.webHero = () => {
    const h = document.getElementById('journalHero');
    return {
      eyebrow: text(h.querySelector('.eyebrow')), title: text(h.querySelector('h2')), backup: { label: text(h.querySelector('.jx-ghost')), spoken: h.querySelector('.jx-ghost').getAttribute('aria-label') },
      stats: [...h.querySelectorAll('.jx-stat')].map(s => ({ value: text(s.querySelector('b')), label: text(s.querySelector('small')), color: col(s) })),
      rhythm: { title: text(h.querySelector('.jx-rhythm-h span')), legend: text(h.querySelector('.jx-legend')),
        cells: [...h.querySelectorAll('.jx-cell')].map(c => ({ color: c.classList.contains('off') ? 0 : col(c), alpha: c.classList.contains('off') ? 0 : Number(c.style.getPropertyValue('--o')), off: c.classList.contains('off'), today: c.classList.contains('now'), label: c.getAttribute('title') })) },
    };
  };
  window.webList = () => [...document.getElementById('journalDays').children].map(el => {
    if (el.classList.contains('jx-quiet')) return { kind: 'quiet', text: text(el) };
    const key = /selectJournalDay\('([^']+)'\)/.exec(el.getAttribute('onclick'))[1];
    if (el.classList.contains('jx-rev')) return { kind: 'review', key, badge: text(el.querySelector('.jx-rev-ico')), title: text(el.querySelector('.jx-rev-t')), sub: text(el.querySelector('small')) };
    const pen = !!el.querySelector('.jx-tf u');
    return { kind: 'day', key, weekday: text(el.querySelector('.jx-tw')), date: text(el.querySelector('.jx-td')), bar: parseFloat(el.querySelector('.jx-tbar i').style.width), xp: text(el.querySelector('.jx-tf')).replace('✎', ''), pen, color: col(el), today: el.classList.contains('today'), spoken: el.getAttribute('aria-label') };
  });
  window.webSelected = () => { const on = document.querySelector('#journalDays .on'); return on ? /selectJournalDay\('([^']+)'\)/.exec(on.getAttribute('onclick'))[1] : null };
  /* the entry compared piece by piece with what the projection says of it: {piece: [page, projection]} */
  window.compareEntry = (e) => {
    const root = document.getElementById('journalEntry'), q = s => root.querySelector(s), qa = s => [...root.querySelectorAll(s)], out = {};
    if (e.kind === 'review') {
      out.head = [[text(q('.jx-bignum')), text(q('.jx-dayname b')), text(q('.jx-dayname span'))], [e.head.badge, e.head.title, e.head.range]];
      if (e.chart.kind === 'bars') out.chart = [qa('.jx-bar').map(b => ({ label: text(b.querySelector('small')), value: text(b.querySelector('.jx-bv')), height: parseFloat(b.querySelector('i').style.height), color: col(b), today: b.classList.contains('now') })), e.chart.bars];
      else out.chart = [{ weekdays: qa('.jx-heat-w small').map(text), lead: qa('.jx-hc.void').length, cells: qa('.jx-hc:not(.void)').map(h => ({ day: text(h.querySelector('b')), color: col(h), alpha: Number(h.style.getPropertyValue('--o')), off: h.classList.contains('off'), label: h.getAttribute('title') })) },
        { weekdays: e.chart.weekdays, lead: e.chart.lead, cells: e.chart.cells }];
      out.grid = [qa('.jc-grid > div').map(g => ({ label: text(g.querySelector('small')), value: text(g.querySelector('b')) })), e.grid];
      out.coach = [[text(q('.jc-card .jc-badge')), text(q('.jc-card p'))], [e.coach.badge, e.coach.text]];
      const ta = q('textarea');
      out.box = [[text(q('.jc-q span')), ta.value, ta.placeholder, Number(ta.maxLength), text(q('.jc-count')), q('.jc-count').classList.contains('on')], [e.question, e.answer, e.placeholder, e.max, e.counter, e.paid]];
      return out;
    }
    out.head = [[text(q('.jx-bignum')), text(q('.jx-dayname b')), text(q('.jx-dayname span')), text(q('.jx-dayxp b')), text(q('.jx-dayxp small'))], [e.head.number, e.head.weekday, e.head.month, e.head.xp, e.head.xpLabel]];
    out.meta = [qa('.jx-meta span').map(text), e.meta];
    out.care = [q('.jc-care') ? [text(q('.jc-care b')), text(q('.jc-care'))] : null, e.care ? [e.care.lead, e.care.lead + ' ' + e.care.text] : null];
    out.welcome = [q('.jc-welcome') ? { head: text(q('.jc-wh')), body: text(q('.jc-welcome p')), chips: qa('.jc-chip').map(c => ({ label: text(c), done: c.disabled })) } : null,
      e.welcome ? { head: e.welcome.head, body: e.welcome.body, chips: e.welcome.chips.map(c => ({ label: c.label, done: c.done })) } : null];
    const card = qa('.jc-card')[0];
    out.insight = [card ? { label: text(card.querySelector('.jc-badge')), color: col(card), text: text(card.querySelector('p')), pattern: text(card.querySelector('.jc-pat')) } : null, e.insight];
    const r = e.reflect, ta = q('#dayReflection'), n = r.text.trim().length, C = r.counter;
    const line = r.awarded ? C.awarded : !r.canPay ? C.old : n >= r.min ? C.ready : C.short; // the page's choice (jcCounterText), not typing
    out.prompt = [q('.jc-q') ? { kicker: text(q('.jx-qmark')), text: text(q('.jc-q span:nth-child(2)')), swap: q('.jc-swap').getAttribute('aria-label') } : null, r.prompt ? { kicker: r.prompt.kicker, text: r.prompt.text, swap: r.prompt.swap } : null];
    out.label = [q('label[for=dayReflection]') ? text(q('label[for=dayReflection]')) : null, r.label];
    out.box = [[ta.value, ta.placeholder, Number(ta.maxLength), parseFloat(q('#jcMeter').style.width), text(q('#jcCount')), q('#jcCount').classList.contains('on')], [r.text, r.placeholder, r.max, r.meter, line, r.awarded]];
    out.timeline = [qa('.jx-item').map(it => ({ time: text(it.querySelector('.jx-time')), color: col(it), name: text(it.querySelector('strong')), attr: it.querySelector('.jx-pill') ? text(it.querySelector('.jx-pill')) : null,
      sub: [...it.querySelectorAll('.jx-sub > span:not(.jx-pill)')].map(text), xp: text(it.querySelector('.jx-xpv')), notes: text(it.querySelector('.j-notes')), plan: it.querySelector('.j-plan-logs') ? [...it.querySelectorAll('.j-plan-logs > div')].map(text) : null,
      icon: it.querySelector('.acticon svg').innerHTML })),
      e.timeline.map(t => ({ time: t.time, color: t.color, name: t.name, attr: t.attr && t.attr.text, sub: [t.minutes, t.skill].filter(Boolean), xp: t.xp + 'XP', notes: t.notes, plan: t.plan && t.plan.map(x => x.label + (x.weight ? ' · ' + x.weight : '') + (x.note ? ' — ' + x.note : '')),
        icon: (() => { const d = document.createElement('div'); d.innerHTML = px(t.icon, 18); return d.firstChild.innerHTML })() }))];
    out.attrColors = [true, e.timeline.every(t => !t.attr || t.attr.color === t.color)];
    out.workoutNotes = [q('.jx-plan') ? { title: text(q('.jx-plan strong')), items: [...q('.jx-plan').querySelectorAll('.j-plan-logs > div')].map(text) } : null,
      e.workoutNotes ? { title: e.workoutNotes.title, items: e.workoutNotes.items.map(x => x.label + (x.weight ? ' · ' + x.weight : '') + (x.note ? ' — ' + x.note : '')) } : null];
    out.blank = [q('.jx-empty') ? [text(q('.jx-empty-ico')), text(q('.jx-empty strong')), text(q('.jx-empty span'))] : null, e.blank ? [e.blank.icon, e.blank.title, e.blank.text] : null];
    return out;
  };
};

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); const errs = [], warns = []; p.on('pageerror', e => errs.push(String(e))); p.on('console', m => { if (m.type() === 'warning' && /coach|journal/.test(m.text())) warns.push(m.text().slice(0, 300)) });
  await p.clock.setFixedTime(new Date('2026-10-28T12:00:00Z'));
  await p.addInitScript(FAKES);
  await p.goto('file://' + file); await p.waitForFunction(() => window.LifeMaxCoach && window.NativeJournalProjection); await p.waitForTimeout(2200);
  await p.evaluate(HELPERS);
  const seed = await p.evaluate(`(${lived})()`);
  await p.evaluate(() => { lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { } const S = lmaLoad(); S.nativeOverview = false; lmaSave() });

  /* ---------- Settings: the switch ---------- */
  const row = await p.evaluate(async () => {
    navigate('settings'); await wait(60);
    const t = document.getElementById('nativeJournalToggle'), line = t.closest('.settingsline'), lib = document.getElementById('nativeLibraryToggle').closest('.settingsline');
    const r = { label: line.querySelector('strong').textContent, hint: line.querySelector('.muted').textContent, checked: t.checked, disabled: t.disabled, stored: lmaLoad().nativeJournal, afterLibrary: lib.nextElementSibling === line, libHint: lib.querySelector('.muted').textContent };
    navigate('history'); await wait(300); r.offStaysWeb = count('journal', 'open') === 0;
    t.checked = true; t.dispatchEvent(new Event('change')); r.on = lmaLoad().nativeJournal === true;
    return r;
  });
  ok('Settings has "Native Journal (beta)" right after the Library row, off by default', row.label === 'Native Journal (beta)' && row.afterLibrary && !row.checked && !row.disabled && row.stored === undefined, row);
  ok('its hint says what is native and what stays on the page', row.hint === 'The native Journal: your days, the coach and the reviews, behind the Journal lock. Writing a reflection or a review answer and the welcome-back chips use the classic page. Turn off for the classic Journal.', row.hint);
  ok('the Library hint no longer says the Journal stays classic', /The Journal has its own switch\./.test(row.libHint) && !/Journal stays classic/.test(row.libHint), row.libHint);
  ok('switched off, the Journal stays the page', row.offStaysWeb && row.on, row);

  /* ---------- the projection against the page's own Journal ---------- */
  const first = await p.evaluate(async (Y0) => {
    navigate('dashboard'); navigate('history'); await until(() => showing());
    const s = last('journal', 'open')[2].snapshot;
    return { s, hero: webHero(), list: webList(), sel: webSelected(), Y0, histSel, entry: compareEntry(s.entry), tipWeb: (() => { const t = document.querySelector('[data-feature-tip="history"]'); return t && [t.querySelector('.tip-ico').textContent, t.querySelector('strong').textContent, t.querySelector('p').textContent, t.querySelector('.tip-x').getAttribute('aria-label')] })(),
      lockCard: (() => { const o = document.getElementById('jlOverlay'); return o ? [o.querySelector('h3').textContent, o.querySelector('p').textContent, o.querySelector('#jlUnlockBtn').textContent] : null })() };
  }, seed.Y0);
  const s = first.s;
  ok('opens with the kit\'s envelope and an open frame', s.protocol === 1 && /^history-web-/.test(s.webSessionId) && typeof s.tokens.bg === 'number' && s.withheld === false && s.edit === 'page' && typeof s.motion === 'boolean' && 'notice' in s, { motion: s.motion, notice: s.notice, edit: s.edit });
  ok('the switch: Media and Journal, the Journal current', same(s.panes, [{ id: 'media', label: 'Media', current: false }, { id: 'history', label: 'Journal', current: true }]), s.panes);
  ok('the lock: off, with the card\'s words', s.lock.on === false && same(s.lock.card, { title: 'Journal locked', body: 'Your reflections are private. Unlock with your fingerprint or screen lock.', unlock: 'Unlock' }), s.lock);
  ok('the hero is the page\'s hero: words, the four stats in their colours, the 28 cells (colour, opacity, title, today, off)', same(first.hero, s.hero) && s.hero.rhythm.cells.length === 28 && s.hero.rhythm.cells[27].today, { web: first.hero, proj: s.hero });
  ok('the list is the page\'s tiles, quiet pills and reviews, in order', same(first.list, s.list.items) && s.list.items.some(x => x.kind === 'quiet') && s.list.items.some(x => x.kind === 'review' && x.badge === 'W') && s.list.items.some(x => x.kind === 'review' && x.badge === 'M'), { web: first.list.slice(0, 6), proj: s.list.items.slice(0, 6) });
  ok('selection is top level: the page\'s histSel, today', s.selection === seed.Y0 && first.histSel === seed.Y0 && first.sel === seed.Y0 && !('selection' in s.list), s.selection);
  ok('list.rev names histRev, the date and the 20:00 cutoff', /^\d+\|2026-10-28\|0$/.test(s.list.rev), s.list.rev);
  ok('the tip is the page\'s tip', same(first.tipWeb, s.tip && [s.tip.icon, s.tip.title, s.tip.body, s.tip.dismiss]), [first.tipWeb, s.tip]);
  ok('the lock card\'s words are the page\'s cover', first.lockCard === null || same(first.lockCard, [s.lock.card.title, s.lock.card.body, s.lock.card.unlock]), first.lockCard);
  // a failure names the piece and, for a list, the first item that differs and how
  const firstDiff = (a, b) => { if (!Array.isArray(a) || !Array.isArray(b)) return { page: a, projection: b }; const i = a.findIndex((x, j) => !same(x, b[j])); const x = a[i] || {}, y = b[i] || {}; return { at: i < 0 ? 'length ' + a.length + '/' + b.length : i, fields: Object.keys(Object.assign({}, x, y)).filter(k => !same(x[k], y[k])).map(k => ({ [k]: [x[k], y[k]] })) } };
  const entryOk = (name, cmp) => { const bad = Object.keys(cmp).filter(k => !same(cmp[k][0], cmp[k][1])); ok(name, bad.length === 0, bad.map(k => ({ [k]: firstDiff(cmp[k][0], cmp[k][1]) }))) };
  entryOk('today: head, meta, insight and pattern, prompt, box and counter, timeline (with an emoji icon resolved), as the page draws them', first.entry);
  ok('today\'s entry has its canary texts (an open frame carries the selected day\'s words)', s.entry.kind === 'day' && /Canary reflection/.test(s.entry.reflect.text) && s.entry.timeline.some(t => t.name === 'Canary sprint drills' && t.icon === 'dumbbell' && t.notes === 'Canary note: knees felt odd'), s.entry.timeline);
  ok('the counter carries the page\'s four lines', same(s.entry.reflect.counter, { awarded: '+15 XP & +2 bond claimed', ready: 'Saves for +15 XP', short: 'Write 40+ characters for +15 XP', old: 'Reflections on older days earn no XP' }) && s.entry.reflect.min === 40 && s.entry.reflect.max === 4000, s.entry.reflect);

  // every other kind of entry, selected through the command, compared with the page's entry drawn under the screen
  const Y = o => new Date(Date.UTC(2026, 9, 28 - o)).toISOString().slice(0, 10);
  const cases = [['yesterday (a big push)', Y(1)], ['quests', Y(2)], ['escaped text', Y(4)], ['a plan session with its notes', Y(seed.withAct)], ['workout notes on their own card', Y(seed.notesOnly)],
    ['an older day written about (no XP)', Y(6)], ['a rest day', Y(9)], ['a blank page (only a reflection)', Y(11)], ['the weekly review (answered, paid)', seed.week], ['the monthly chronicle', seed.month]];
  for (const [name, key] of cases) {
    const r = await p.evaluate(async (key) => { const reply = await command('select', { key }); return { reply, cmp: reply.frame && reply.frame.entry ? compareEntry(reply.frame.entry) : null, sel: webSelected(), kind: reply.frame && reply.frame.entry && reply.frame.entry.kind } }, key);
    if (!r.cmp) { ok(name + ': answered', false, r.reply); continue }
    ok(name + ': select answers ok and the page shows that entry', r.reply.ok === true && r.reply.frame.selection === key && r.sel === key, { reply: r.reply.ok, sel: r.sel });
    entryOk(name + ': the entry is the page\'s', r.cmp);
  }
  const shapes = await p.evaluate(async ({ withAct, notesOnly, week, month }) => {
    const Y = o => new Date(Date.UTC(2026, 9, 28 - o)).toISOString().slice(0, 10);
    const e = async key => (await command('select', { key })).frame.entry;
    return { plan: await e(Y(withAct)), notes: await e(Y(notesOnly)), blank: await e(Y(11)), week: await e(week), month: await e(month), old: await e(Y(6)) };
  }, seed);
  ok('a plan session carries its plan notes ({label, weight, note})', shapes.plan.timeline.some(t => t.plan && t.plan.some(x => x.note === 'Canary plan note, kept 2 in reserve' && x.weight === '60 kg')), shapes.plan.timeline);
  ok('workout notes on their own: {title, icon, items}', shapes.notes.workoutNotes && shapes.notes.workoutNotes.title === 'Workout notes' && shapes.notes.workoutNotes.icon === 'dumbbell' && shapes.notes.workoutNotes.items.length >= 1, shapes.notes.workoutNotes);
  ok('a blank page: {icon, title, text} and no timeline', same(shapes.blank.blank, { icon: '✎', title: 'Nothing logged this day yet', text: 'A blank page is still a page.' }) && shapes.blank.timeline.length === 0, shapes.blank);
  ok('an older day cannot pay', shapes.old.reflect.canPay === false && shapes.old.reflect.awarded === false, shapes.old.reflect);
  ok('the weekly review: bars, grid, coach, its answer and the paid counter', shapes.week.kind === 'review' && shapes.week.chart.kind === 'bars' && shapes.week.chart.bars.length === 7 && shapes.week.paid === true && shapes.week.counter === '+30 XP claimed' && shapes.week.min === 50 && shapes.week.max === 2000 && /Canary answer/.test(shapes.week.answer) && shapes.week.coach.badge === 'Coach', shapes.week);
  ok('the monthly chronicle: a heat grid with its lead and weekdays', shapes.month.chart.kind === 'heat' && shapes.month.chart.weekdays.length === 7 && shapes.month.chart.cells.length >= 28 && shapes.month.min === 60 && shapes.month.paid === false, shapes.month.chart.lead);

  /* ---------- building a projection changes nothing ---------- */
  const pure = await p.evaluate(() => {
    const before = JSON.stringify(state), stored = localStorage.getItem('lifemax_rpg_prototype_v1'), set = localStorage.setItem.bind(localStorage); let writes = 0;
    localStorage.setItem = function () { writes++; return set.apply(this, arguments) };
    try { NativeJournalTabProjection.snapshot(); histSel = Object.keys(state.journalCoach.reviews || {})[0] || histSel; NativeJournalTabProjection.snapshot() } finally { localStorage.setItem = set }
    return { same: JSON.stringify(state) === before, stored: localStorage.getItem('lifemax_rpg_prototype_v1') === stored, writes };
  });
  ok('snapshot() changes nothing: the state, the stored save, no localStorage write', pure.same && pure.stored && pure.writes === 0, pure);

  /* ---------- memo ---------- */
  const memo = await p.evaluate(async (Y6) => {
    await command('select', { key: getToday() });
    const real = jcListData; let built = 0; jcListData = function () { built++; return real.apply(this, arguments) };
    const out = {};
    try {
      const a = NativeJournalTabProjection.snapshot(); NativeJournalTabProjection.snapshot(); out.noChange = built; out.rev = a.list.rev;
      const before = a.hero.stats[3].value, tile = a.list.items.find(x => x.key === Y6);
      saveDayReflection(Y6, 'Short.'); // an older day, under 40 characters: no render, only histRev (the page redraws its own list)
      const b0 = built, c = NativeJournalTabProjection.snapshot(); out.afterShort = built - b0; out.refs = [before, c.hero.stats[3].value]; out.pen = [tile.pen, c.list.items.find(x => x.key === Y6).pen];
      saveDayReflection(Y6, '');
      const d = NativeJournalTabProjection.snapshot(); out.penGone = d.list.items.find(x => x.key === Y6).pen === false && d.hero.stats[3].value === before;
    } finally { jcListData = real }
    return out;
  }, Y(3));
  ok('memo: a refresh with nothing new does not build the list again', memo.noChange === 0, memo);
  ok('memo: a short reflection on an older day (no render) updates its tile\'s pen and the hero\'s count', memo.afterShort === 1 && memo.refs[1] === String(Number(memo.refs[0]) + 1) && memo.pen[0] === false && memo.pen[1] === true && memo.penGone, memo);
  await p.clock.setFixedTime(new Date('2026-10-28T20:30:00Z'));
  const eve = await p.evaluate(() => { const real = jcListData; let built = 0; jcListData = function () { built++; return real.apply(this, arguments) }; try { const s = NativeJournalTabProjection.snapshot(); return { built, rev: s.list.rev } } finally { jcListData = real } });
  ok('memo: the clock crossing 20:00 builds the list again', eve.built === 1 && /\|2026-10-28\|1$/.test(eve.rev), eve);
  await p.clock.setFixedTime(new Date('2026-10-29T00:05:00Z'));
  const night = await p.evaluate(() => { const real = jcListData; let built = 0; jcListData = function () { built++; return real.apply(this, arguments) }; try { const s = NativeJournalTabProjection.snapshot(); return { built, rev: s.list.rev, today: [s.list.items[0].weekday, s.list.items[0].today, s.list.items[0].spoken] } } finally { jcListData = real } });
  ok('memo: midnight builds it again (the tile Today becomes Wed, spoken Yesterday)', night.built === 1 && /\|2026-10-29\|0$/.test(night.rev) && same(night.today, ['Wed', false, 'Yesterday, ' + night.today[2].split(', ')[1]]), night);
  // from here the page's clock flows again from noon: a celebration ends when Date.now() passes its end (afterCelebrations)
  await p.clock.setSystemTime(new Date('2026-10-28T12:00:00Z')); await p.clock.resume();

  /* ---------- select ---------- */
  const sel = await p.evaluate(async (Y2) => {
    await command('select', { key: getToday() });
    const rev = latest().list.rev, set = localStorage.setItem.bind(localStorage); let writes = 0;
    localStorage.setItem = function () { writes++; return set.apply(this, arguments) };
    const real = renderHistory; let renders = 0; renderHistory = function () { renders++; return real.apply(this, arguments) };
    try {
      const a = await command('select', { key: Y2 }), again = await command('select', { key: Y2 }), unknown = await command('select', { key: '1999-01-01' });
      return { a: [a.ok, a.changed, a.frame.selection, a.frame.list.rev === rev], again: [again.ok, again.changed], unknown: [unknown.ok, unknown.message, unknown.frame.selection], page: document.querySelector('#journalEntry .jx-bignum').textContent, writes, renders };
    } finally { localStorage.setItem = set; renderHistory = real }
  }, Y(2));
  ok('select: the reply carries the new selection with the list unchanged (same rev)', same(sel.a, [true, true, Y(2), true]), sel);
  ok('select: the page shows that day under the screen, drawn without a full render', sel.page === '26' && sel.renders === 0, sel);
  ok('select: the same key again changes nothing and writes nothing', same(sel.again, [true, false]) && sel.writes === 0, sel);
  ok('select: an unknown day is refused with the page\'s words, the selection kept', same(sel.unknown, [false, 'That day is not in your Journal.', Y(2)]), sel.unknown);

  /* ---------- tip, swap ---------- */
  const tipSwap = await p.evaluate(async () => {
    await command('select', { key: getToday() });
    const d = getToday(), from = latest().entry.reflect.prompt.id;
    const a = await command('swap', { d, from }), b2 = await command('swap', { d, from }), stored = JSON.parse(localStorage.getItem('lifemax_rpg_prototype_v1')).journalCoach.days[d].promptId;
    const t1 = await command('tip', {}), t2 = await command('tip', {});
    return { a: [a.ok, a.changed, a.frame.entry.reflect.prompt.id !== from], b: [b2.ok, b2.changed], stored: stored === a.frame.entry.reflect.prompt.id, page: document.querySelector('#journalEntry .jc-q span:nth-child(2)').textContent === a.frame.entry.reflect.prompt.text,
      t: [t1.ok, t1.changed, t1.frame.tip, t2.ok, t2.changed], seen: JSON.parse(localStorage.getItem('lifemax_rpg_prototype_v1')).tipsSeen.history };
  });
  ok('swap: moves the question once (compare-and-set), stored, and the page\'s entry follows', same(tipSwap.a, [true, true, true]) && same(tipSwap.b, [true, false]) && tipSwap.stored && tipSwap.page, tipSwap);
  ok('tip: dismissed and stored once; a repeat changes nothing', same(tipSwap.t, [true, true, null, true, false]) && tipSwap.seen === true, tipSwap);

  /* ---------- covering ---------- */
  const cover = await p.evaluate(async () => {
    const w = () => NativeTabs.wouldOpen('history'), r = { base: w() };
    const lu = document.createElement('div'); lu.className = 'luov'; document.body.appendChild(lu); r.levelUp = w(); lu.remove();
    const was = audioCtx.mediaId; audioCtx.mediaId = 'm1'; r.book = w(); audioCtx.mediaId = was;
    r.covered = NativeTabs.covered('history');
    await until(() => showing()); r.after = w();
    return r;
  });
  ok('covering: native over the Journal; a level-up or a loaded book keeps the page (audioBusy)', cover.base && !cover.levelUp && !cover.book && cover.covered && cover.after, cover);

  /* ---------- the queue: backup under a pending Save as ---------- */
  const q = await p.evaluate(async () => {
    const t0 = performance.now(), a = await command('backup', {}), took = performance.now() - t0;
    const b2 = await command('select', { key: Object.keys(state.dayReflections).sort()[0] });
    const pending = __saves.length;
    __saves.splice(0).forEach(r => r({ saved: true }));
    await until(() => latest().notice && latest().notice.text === 'Backup saved', 4000);
    return { a: [a.ok, took < 4000], b: b2.ok, pending, notice: latest().notice };
  });
  ok('backup replies at once, before Save as settles, and a select behind it is answered', q.a[0] && q.a[1] && q.b === true && q.pending === 1, q);
  ok('the Save as toast comes later as the notice', q.notice && q.notice.text === 'Backup saved' && typeof q.notice.id === 'number', q.notice);

  /* ---------- the lock ---------- */
  const lk = await p.evaluate(async (privates) => {
    const out = {};
    await command('select', { key: getToday() });
    state.journalLock = { on: true }; jlUnlocked = true; save(); await wait(250); // as the Settings card turns it on: this session unlocked
    out.openOn = latest().withheld === false && latest().lock.on === true;
    const n = frames().length; LifeMaxJournalLock.relock();
    await until(() => frames().length > n, 1500); const w = frames()[n];
    out.pushedAtOnce = !!w && w.withheld === true;
    out.keys = Object.keys(w).sort();
    out.withheld = w;
    const json = JSON.stringify(w);
    out.leaks = privates.filter(x => json.includes(x)).concat(/\d{4}-\d\d-\d\d/.test(json) ? ['a date key'] : []);
    // later refreshes while locked stay withheld
    await wait(300); const later = frames().slice(n); out.allWithheld = later.every(f => f.withheld === true); out.laterLeaks = privates.filter(x => JSON.stringify(later).includes(x));
    // every command but unlock is refused
    out.refused = {};
    for (const [c, payload] of [['select', { key: getToday() }], ['write', { key: getToday() }], ['swap', { d: getToday(), from: 'x' }], ['backup', {}], ['tip', {}]]) { const r = await command(c, payload); out.refused[c] = [r.ok, r.message, r.frame.withheld] }
    out.savesAfterRefusal = __saves.length;
    // the page's own prompt on arrival, failing: the error on the card at once; a cancel clears it
    __bio.next = () => ({ ok: false });
    navigate('dashboard'); await wait(200); navigate('history'); await until(() => showing() && latest().status && latest().status.error);
    out.failed = latest().status;
    out.prompted = frames().some(f => f.withheld && f.status.prompting === true);
    __bio.next = () => ({ ok: false, cancelled: true });
    const c = await command('unlock', {}); out.cancel = [c.ok, c.frame.withheld, c.frame.status];
    // a notice rides in a withheld frame
    toast('Backup saved'); await until(() => latest().notice && latest().notice.text === 'Backup saved', 2000); out.noticeLocked = [latest().withheld, latest().notice && latest().notice.text];
    // unlock: the first open frame already has today's chip decided and drawn, and the next frame says the same
    delete state.journalCoach.pats[getToday()];
    __bio.next = () => ({ ok: true });
    const before = frames().length, u = await command('unlock', {});
    const firstOpen = frames().slice(before).find(f => !f.withheld);
    out.unlock = [u.ok, u.frame.withheld, !!firstOpen];
    out.chipDecided = getToday() in state.journalCoach.pats;
    const pat = state.journalCoach.pats[getToday()];
    out.chipInFirst = firstOpen && firstOpen.entry.insight && firstOpen.entry.insight.pattern === (pat ? pat.text : null);
    await wait(250); const n2 = frames().length; NativeJournalTabProjection.snapshot(); window.__refresh = true;
    const strip = f => { const x = Object.assign({}, f); delete x.sequence; delete x.notice; return JSON.stringify(x) };
    J.__emit('refresh'); await until(() => frames().length > n2, 2000);
    out.nextSame = strip(firstOpen) === strip(latest());
    // the page thinks it is open (native locked itself when the app stopped): unlock relocks first, then prompts
    const m = frames().length, r2 = await command('unlock', {});
    const between = frames().slice(m);
    out.relockFirst = [r2.ok, between.some(f => f.withheld), between[between.length - 1].withheld];
    // an ignored prompt (one already up) answers {ok:false}, never undefined
    LifeMaxJournalLock.relock(); await wait(100);
    let release; __bio.next = () => new Promise(r => { release = r });
    LifeMaxJournalLock.prompt();
    const ig = await command('unlock', {});
    out.ignored = [ig.ok, 'ok' in ig];
    release({ ok: true }); await until(() => !latest().withheld, 2000);
    out.openAgain = latest().withheld === false;
    // the lock turned off: no drawing follows (nothing to empty), so only the lock's onChange tells the screen, at once
    await wait(600); // the unlock's own frames have gone
    const nOff = frames().length; state.journalLock = { on: false }; LifeMaxJournalLock.apply();
    await until(() => frames().length > nOff && latest().lock.on === false, 1000); out.offFrame = latest().lock.on === false && latest().withheld === false; save();
    return out;
  }, seed.privates);
  ok('lock on and unlocked: the open frame says lock.on', lk.openOn, lk);
  ok('a relock pushes a withheld frame at once', lk.pushedAtOnce, lk);
  ok('the withheld frame has only its fields (no tip, hero, list, selection or entry)', same(lk.keys, ['light', 'lock', 'notice', 'panes', 'protocol', 'sequence', 'status', 'tokens', 'webSessionId', 'withheld']), lk.keys);
  ok('the withheld frame holds no private string of the journal (every name, note, reflection, answer, plan note) and no date', lk.leaks.length === 0 && lk.laterLeaks.length === 0 && seed.privates.length > 10, { leaks: lk.leaks, n: seed.privates.length });
  ok('later refreshes while locked are withheld too', lk.allWithheld, lk);
  ok('the withheld frame: switch, lock card, status', same(lk.withheld.panes.map(x => x.id), ['media', 'history']) && lk.withheld.lock.on === true && same(lk.withheld.status, { prompting: false, error: '' }), lk.withheld);
  ok('while locked every command but unlock is refused {ok:false}, and backup saves nothing', Object.values(lk.refused).every(r => r[0] === false && r[1] === 'Unlock the Journal first.' && r[2] === true) && lk.savesAfterRefusal === 0, lk.refused);
  ok('the page\'s auto-prompt reaches the card: prompting, then its failure as the error line', lk.prompted && same(lk.failed, { prompting: false, error: 'Could not unlock. Try again.' }), lk.failed);
  ok('a cancelled unlock replies {ok:false} and clears the error', same(lk.cancel, [false, true, { prompting: false, error: '' }]), lk.cancel);
  ok('notices ride in withheld frames', same(lk.noticeLocked, [true, 'Backup saved']), lk.noticeLocked);
  ok('unlock replies ok with the open frame', same(lk.unlock, [true, false, true]), lk.unlock);
  ok('the first open frame after unlock already has the day decided and its pattern drawn', lk.chipDecided && lk.chipInFirst, lk);
  ok('and the next frame says the same', lk.nextSame, lk);
  ok('unlock relocks first when the page thinks it is open (its withheld frame goes out), then unlocks', lk.relockFirst[0] === true && lk.relockFirst[1] && lk.relockFirst[2] === false, lk.relockFirst);
  ok('unlock while a prompt is already up answers ok:false, never undefined', same(lk.ignored, [false, true]) && lk.openAgain, lk.ignored);
  ok('the lock turned off reaches the screen at once (the lock\'s onChange, with no drawing to follow): lock.on false', lk.offFrame, lk);

  /* ---------- write and the way back ---------- */
  const wr = await p.evaluate(async () => {
    const out = {};
    await command('select', { key: getToday() });
    const closes = count('journal', 'close'), from = __calls.length, r = await command('write', { key: getToday() });
    out.reply = [r.ok, r.frame.selection];
    out.focused = document.activeElement && document.activeElement.id === 'dayReflection';
    out.pageDay = document.querySelector('#journalEntry .jx-bignum').textContent;
    await until(() => count('journal', 'close') > closes, 2000);
    const seq = __calls.slice(from).filter(c => c[0] === 'journal').map(c => c[1] + (c[2] && c[2].reply ? ':reply' : ''));
    out.order = seq.indexOf('update:reply') >= 0 && seq.indexOf('update:reply') < seq.indexOf('close');
    out.closed = !showing();
    // typing on the page and leaving the field: a reflection on a fresh day pays, the orbs play on the page
    delete (state.journalCoach.days[getToday()] || {}).xpAwarded;
    const ta = document.getElementById('dayReflection'), opens = count('journal', 'open'), xp0 = totalXP(); out.awardedBefore = !!(state.journalCoach.days[getToday()] || {}).xpAwarded;
    ta.value = ta.value + ' And then I wrote a little more for the coach today.'; ta.dispatchEvent(new Event('input'));
    ta.dispatchEvent(new Event('change')); ta.blur();
    out.celebrating = celebrating(); out.gain = totalXP() - xp0; // at once: a busy machine may take longer than the orbs to come back here
    let reopenedWhile = false; const t0 = Date.now();
    while (celebrating() && Date.now() - t0 < 6000) { if (count('journal', 'open') > opens) reopenedWhile = true; await wait(40) }
    out.reopenedWhile = reopenedWhile;
    out.back = await until(() => showing(), 6000);
    out.text = /a little more for the coach/.test(latest().entry.reflect.text) && latest().entry.reflect.awarded === true;
    return out;
  });
  ok('write: replies ok, then the page\'s field for that day is focused under the screen', same(wr.reply, [true, Y(0)]) && wr.focused && wr.pageDay === '28', wr);
  ok('write: the screen steps aside only after the reply went', wr.order && wr.closed, wr);
  ok('the way back: no return while the orbs play, back after them with the text', wr.celebrating && !wr.reopenedWhile && wr.back && wr.text, wr);

  /* ---------- hand-backs: claim and catch-up, and a toast raised meanwhile ---------- */
  const hb = await p.evaluate(async () => {
    const out = {};
    // a welcome back: 5 quiet days before today
    const at = o => new Date(Date.now() - o * 864e5).toISOString();
    state.activities = []; state.dayReflections = {}; state.journalCoach = {};
    for (let o = 6; o <= 20; o++) state.activities.push({ id: 'w' + o, type: 'custom', name: 'Study', icon: 'book', duration: 30, quality: 1, skill: 'Knowledge', primary: 'intelligence', secondary: '', xp: 100, date: at(o), notes: '', xpSplit: [{ attr: 'intelligence', xp: 100 }] });
    state.activities.push({ id: 'w0', type: 'custom', name: 'Back at it', icon: 'book', duration: 30, quality: 1, skill: 'Back', primary: 'discipline', secondary: '', xp: 60, date: at(0), notes: '', xpSplit: [{ attr: 'discipline', xp: 60 }] });
    histSel = getToday(); save(); await until(() => latest().entry && latest().entry.welcome, 3000);
    const w = latest().entry.welcome; out.welcome = [w.tier, w.chips.map(c => c.id + ':' + c.done)];
    out.cmp = compareEntry(latest().entry).welcome;
    const closes = count('journal', 'close'), opens = count('journal', 'open'), xp = totalXP();
    await J.__emit('action', { action: 'claim', id: 'pet' });
    out.closed = count('journal', 'close') > closes; out.paid = totalXP() === xp + 10;
    let reopenedWhile = false; const t0 = Date.now();
    while (celebrating() && Date.now() - t0 < 6000) { if (count('journal', 'open') > opens) reopenedWhile = true; await wait(40) }
    out.reopenedWhile = reopenedWhile;
    out.back = await until(() => showing(), 6000);
    const f = last('journal', 'open')[2].snapshot;
    out.done = f.entry.welcome.chips[0].done;
    out.notice = f.notice && f.notice.text;
    // tier 2: the catch-up asks on the page (window.prompt), then the screen comes back
    state.journalCoach = {}; state.activities = state.activities.filter(a => a.type !== 'coach');
    state.activities.forEach(a => { if (a.id !== 'w0') a.date = new Date(Date.parse(a.date) - 4 * 864e5).toISOString() });
    save(); await until(() => latest().entry && latest().entry.welcome && latest().entry.welcome.tier === 2, 3000);
    window.prompt = () => 'I was travelling for work all week long.';
    const c2 = count('journal', 'close');
    await J.__emit('action', { action: 'catchUp' });
    out.catchUp = [count('journal', 'close') > c2, /travelling for work/.test(state.dayReflections[getToday()] || '')];
    out.back2 = await until(() => showing() && !celebrating(), 6000) && await until(() => showing(), 3000);
    out.lineDone = latest().entry.welcome.chips.find(c => c.id === 'line').done;
    out.director = latest().entry.welcome.chips.find(c => c.id === 'director');
    return out;
  });
  ok('the welcome card is the page\'s (tier 1, Pet/Claim chip)', hb.welcome[0] === 1 && same(hb.cmp[0], hb.cmp[1]), hb);
  ok('claim: the screen closes, the page pays once', hb.closed && hb.paid, hb);
  ok('claim: no return while the orbs play, back after them with the chip done', !hb.reopenedWhile && hb.back && hb.done === true, hb);
  ok('a toast raised while the screen was away comes with the frame that brings it back', hb.notice === '+10 XP · Welcome back', hb.notice);
  ok('catch-up: the page asks, stores the sentence, and the screen comes back with the chip done', hb.catchUp[0] && hb.catchUp[1] && hb.back2 && hb.lineDone === true && hb.director && hb.director.label === 'Open Life Director', hb);

  /* ---------- empty journal ---------- */
  const empty = await p.evaluate(async () => {
    state.activities = []; state.dayReflections = {}; state.journalCoach = {}; state.plan = null; save();
    await until(() => latest().empty, 3000);
    const s = latest(), el = document.querySelector('#journalEntry .empty');
    return { empty: s.empty, entry: s.entry, items: s.list.items.length, web: el && el.textContent.replace(/\s+/g, ' ').trim(), sel: s.selection };
  });
  ok('an empty journal: the page\'s empty state, no list, no entry', empty.empty && empty.web === empty.empty.icon + empty.empty.title + empty.empty.body && empty.entry === null && empty.items === 0 && empty.sel === null, empty);

  /* ---------- notices across a hand-over, Media's switch ---------- */
  const nm = await p.evaluate(async () => {
    const out = {};
    await setNativeLibraryEnabled(true);
    navigate('media'); await until(() => showing('media') && !showing('journal'), 5000);
    toast('Marked as finished'); await until(() => latest('media').notice, 2000);
    out.media = latest('media').notice && latest('media').notice.text;
    const from = __calls.length;
    Capacitor.Plugins.NativeMedia.__emit('action', { view: 'history' });
    await until(() => showing('journal'), 5000); await until(() => !showing('media'), 5000);
    const seq = __calls.slice(from).filter(c => (c[0] === 'journal' && c[1] === 'open') || (c[0] === 'media' && c[1] === 'close')).map(c => c[0] + ':' + c[1]);
    out.makeBeforeBreak = seq[0] === 'journal:open';
    out.journalNotice = __calls.slice(from).filter(c => c[0] === 'journal' && (c[1] === 'open' || c[1] === 'update')).map(c => c[2].snapshot.notice);
    out.toastStill = document.getElementById('toast').classList.contains('show');
    return out;
  });
  ok('Media\'s switch reaches the native Journal with no close before it opens', nm.makeBeforeBreak, nm);
  ok('a toast shown by native Media is not shown again by the Journal it hands over to', nm.media === 'Marked as finished' && nm.journalNotice.length > 0 && nm.journalNotice.every(n => n === null) && nm.toastStill, nm);

  /* ---------- swipe ---------- */
  const sw = await p.evaluate(async () => {
    lmaLoad().nativeSwipe = true; lmaLoad().swipe = true; lmaSave();
    const out = {}, was = state.libPane;
    out.kind = NativeSwipe.kind('history');
    state.libPane = 'history'; out.library = NativeSwipe.state().tabs.find(t => t.key === 'library'); state.libPane = was;
    // from the native lock card a swipe may start; from a page under a lock cover it may not
    state.journalLock = { on: true }; save(); LifeMaxJournalLock.relock(); await until(() => latest().withheld, 2000);
    out.locked = document.querySelector('.view.active').classList.contains('jlocked') && showing();
    out.fromCard = NativeSwipe.state().on;
    await J.__emit('closed', {}); out.fromCover = NativeSwipe.state().on;
    state.journalLock = { on: false }; save();
    return out;
  });
  ok('swipe: with the switch on, the Journal is a native neighbour (and the Library tab on the Journal)', sw.kind === 'native' && sw.library && sw.library.view === 'history' && sw.library.kind === 'native', sw);
  ok('swipe: may start from the native lock card, not from the page\'s cover', sw.locked && sw.fromCard === true && sw.fromCover === false, sw);

  ok('no page errors', errs.length === 0, errs);
  ok('no warnings from the coach or the Journal', warns.length === 0, warns);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
