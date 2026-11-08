/* Native economy check (beta), the page's half of WP6a (docs/specs/ECONOMY_SHADOW_PLAN.md 2.3-2.5, 3.3, 4). A stand-in for
   NativeShadow (tests/native-shadow-mock.js) keeps a copy built exactly as native's mirror is, and after every action the copy must
   equal the page's own economy keys. Covered: the switch (off by default, Data & advanced, "(beta)"), nothing at all while it is
   off, without the plugin, without the capability, in a demo or with a different content fingerprint; the boot's base and its
   boot.load and boot.decay actions; one envelope per outermost action with its toasts and save answers; nested points; a resync;
   a new epoch after an import and a reset; an async Health sync, alone and interleaved; a too-large envelope; a page queue that
   overflows; turning the switch off; the fingerprint equal to quest-content.json's contentHash; and the cost on a 3 MB save.
   WP6c, with an app that says economy.shadow.quests.v1: today's plan facts with every kind, the toggled quest's note field,
   the journal coach's own plain writes apart from the economy's, a class switch's quest pack, the Daily Hand's redraws that
   change nothing sent one in fifty; and none of that for an app without the capability.
   WP6d, with an app that says economy.shadow.day.v1: the boot's decay with its plan and toasts, a sync's plugin, status, reads
   and their ranges, a read or a status that throws, a sync started while one runs, a check-in's write during a sync, no
   plugin, the decay pass's plan window; and the WP6c shapes for an app without it.
   Run: NODE_PATH=$(npm root -g) node tests/native-shadow.test.js [built-page.html] */
'use strict';
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const mock = require('./native-shadow-mock.js');
const file = require('./current-page')(process.argv[2]);
const CONTENT = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'quest-content.json'), 'utf8'));
const SAVE = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'saves', 'established.json'), 'utf8')).state;
let pass = 0, fail = 0;
const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const PAGE_KEYS = '(' + mock.pageKeys.toString() + ')';

async function open(browser, opts, save) {
  const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-GB', timezoneId: 'Europe/London' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  const s = save || { onboardingComplete: true, name: 'N', tutorialDone: true, classId: 'athlete', tipsSeen: {} };
  await p.addInitScript('localStorage.setItem("lifemax_rpg_prototype_v1", ' + JSON.stringify(JSON.stringify(s)) + ');' + (opts && opts.demo ? 'localStorage.setItem("lm_demo_active","1");' : ''));
  if (opts) await p.addInitScript(mock.script(Object.assign({ fingerprint: CONTENT.contentHash }, opts)));
  await p.goto('file://' + file);
  await p.waitForFunction(() => typeof window.LmaShadow === 'object' && typeof recordActivity === 'function', null, { timeout: 60000 });
  await p.waitForTimeout(1200);
  return p;
}
/* in the page: waits for the flush, then whether the mock's copy equals the page's keys (and which keys differ) */
const same = p => p.evaluate(async pk => {
  await new Promise(r => setTimeout(r, 60));
  const page = eval(pk)(), m = window.__shadowMock.mirror, mir = m && m.keys;
  const diff = mir ? [...new Set([...Object.keys(page), ...Object.keys(mir)])].filter(k => JSON.stringify(page[k]) !== JSON.stringify(mir[k])) : ['(no copy)'];
  return { equal: !diff.length, diff };
}, PAGE_KEYS);
const kinds = p => p.evaluate(() => window.__shadowMock.envelopes.map(e => e.kind));

(async () => {
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    /* ---------- off: by default, and every way the check cannot run ---------- */
    {
      const p = await open(browser, { on: false });
      const r = await p.evaluate(() => {
        navigate('settings');
        const t = document.getElementById('nativeShadowToggle'), card = document.getElementById('nativeShadowCard');
        return { calls: window.__shadowMock.calls, wrapped: !!(save.__lmaShadow || toggleQuest.__lmaShadow || recordActivity.__lmaShadow), off: t && !t.checked && !t.disabled,
          label: t && t.closest('.settingsline').textContent, cat: card && setCatOf(card), status: LmaShadow.status() };
      });
      ok('off by default: no call to native, no wrapper', r.calls.length === 0 && !r.wrapped && r.status.installed === 0, r);
      ok('the switch is "Native economy check (beta)", off, in Data & advanced, with its help line', r.off && /Native economy check \(beta\)/.test(r.label) && /Nothing leaves the phone/.test(r.label) && r.cat === 'data', r);
      await p.context().close();
    }
    for (const [name, opts] of [['without the plugin', { mode: 'absent' }], ['without the capability', { mode: 'nocap' }], ['when native was built from other rules (an OTA page)', { mode: 'stale' }], ['in a demo', { demo: true }]]) {
      const p = await open(browser, opts);
      const r = await p.evaluate(() => { toggleQuest(state.quests[0].id, true); return { wrapped: !!save.__lmaShadow, calls: window.__shadowMock.calls, env: window.__shadowMock.envelopes.length, status: LmaShadow.status() } });
      ok('nothing is wrapped or sent ' + name, !r.wrapped && r.env === 0 && r.status.installed === 0 && !r.status.live && !r.calls.includes('base'), r);
      await p.context().close();
    }

    /* ---------- on: the boot, the actions, the copy ---------- */
    const p = await open(browser, {}, SAVE);
    {
      const r = await p.evaluate(async () => ({ fp: await LmaShadow.fingerprint(), status: LmaShadow.status(), calls: window.__shadowMock.calls.slice(), bases: window.__shadowMock.bases, config: window.__shadowMock.config,
        order: save.__lmaShadow && save.__lmaShadow !== save.__lmaShadow.__lmaShadow }));
      ok('the page computes the same fingerprint as quest-content.json (contentHash)', r.fp === CONTENT.contentHash, r.fp);
      ok('handshake, configure, then one base before anything else', r.calls.slice(0, 3).join() === 'handshake,configure,base' && r.bases.length === 1 && r.config.enabled === true && r.config.page.fingerprint === CONTENT.contentHash, r.calls);
      ok('the boot is two actions: boot.load then boot.decay', (await kinds(p)).slice(0, 2).join() === 'boot.load,boot.decay', await kinds(p));
      ok('the save wrapper is outermost, around the page\'s own save wrappers', r.order && r.status.installed > 30);
    }
    {
      const r = await p.evaluate(() => {
        const n = window.__shadowMock.envelopes.length;
        const q = state.quests.find(x => !isQuestDone(x));
        toggleQuest(q.id, true);
        return { n, q: q.id };
      });
      const s = await same(p), e = await p.evaluate(() => window.__shadowMock.envelopes.slice(-1)[0]);
      ok('what the boot\'s drawing changed after decay arrives as the first action\'s before-delta (the touch map\'s "between")', Object.keys(e.before.set).length + Object.keys(e.before.rec).length > 0, e.before);
      ok('a quest toggle is one envelope, quest.toggle, with its toast and its save answer', e.kind === 'quest.toggle' && e.toasts.some(t => /Quest complete/.test(t)) && e.env.saves.length >= 1 && e.env.saves.every(x => x.ok === true) && e.args[0] === r.q, e);
      ok('the copy still equals the page', s.equal, s.diff);
    }
    {
      const before = (await kinds(p)).length;
      await p.evaluate(() => recordActivity({ type: Object.keys(rules)[0], duration: 30, quality: 3, notes: 'A private note about my morning', date: new Date().toISOString() }));
      const k = (await kinds(p)).slice(before), s = await same(p);
      ok('a Log is one envelope (log.record): its inner save() is part of it', k.join() === 'log.record', k);
      ok('the copy still equals the page after a Log', s.equal, s.diff);
      await p.evaluate(() => { save(); });
      ok('a bare save() is save.other', (await kinds(p)).slice(-1)[0] === 'save.other');
      await p.evaluate(() => { ecoTakeRest(); recordMoneyTransaction && 0; });
      const s2 = await same(p);
      ok('rest.take and the copy after it', (await kinds(p)).slice(-1)[0] === 'rest.take' && s2.equal, s2.diff);
      const rest = await p.evaluate(() => window.__shadowMock.envelopes.slice(-1)[0]);
      ok('rest.take records the plan facts native reads (WP6b): today done, rest days, from', rest.env.plan && typeof rest.env.plan.done === 'boolean' && Array.isArray(rest.env.plan.rest) && /^\d{4}-\d\d-\d\d$/.test(rest.env.plan.from), rest.env);
      const one = await p.evaluate(() => {
        const out = {}, last = () => window.__shadowMock.envelopes.slice(-1)[0];
        const rule = document.createElement('input'); rule.id = 'rule-reading'; rule.value = '31'; document.body.appendChild(rule);
        saveRules(); out.rules = last(); rule.remove();
        resetRules(); out.reset = last();
        removeAct('no-such-record'); out.remove = last();
        try { undoCat('no-such-entry') } catch (e) { /* the page's own throw for an unknown entry: still one action */ }
        out.undo = last();
        return out;
      });
      await p.waitForTimeout(60);
      const sent = await p.evaluate(() => window.__shadowMock.envelopes.slice(-4).map(e => ({ kind: e.kind, dom: e.env.dom })));
      ok('one kind is one entry (WP6b): saveRules, resetRules, removeAct and undoCat are rules.save, rules.reset, log.remove, fixed.undo', sent.map(e => e.kind).join() === 'rules.save,rules.reset,log.remove,fixed.undo', sent);
      ok('saveRules records every rule-* input it reads', sent[0].dom && sent[0].dom['rule-reading'] === '31', sent[0]);
    }
    {
      // native asks for a resync: the next action is preceded by a base, and the copy is whole again
      await p.evaluate(() => { window.__shadowMock.opts.resyncAt = [window.__shadowMock.envelopes.length + 1] });
      await p.evaluate(() => toggleQuest(state.quests[1].id, true));
      await p.waitForTimeout(80);
      const bases = await p.evaluate(() => window.__shadowMock.bases.length);
      await p.evaluate(() => toggleQuest(state.quests[1].id, false));
      const r = await p.evaluate(() => ({ bases: window.__shadowMock.bases.length, refused: window.__shadowMock.refused }));
      const s = await same(p);
      ok('after a resync the page sends a base before its next envelope, and the copy is equal', r.bases === bases + 1 && s.equal, { r, s });
    }
    {
      // an import (applyBackup) and a reset: a new epoch, a new base
      const e0 = await p.evaluate(() => LmaShadow.status().epoch);
      await p.evaluate(async () => { window.confirm = () => true; window.lmaLeaveNative = async () => { }; backupBeforeReplacing = async () => true;
        await applyBackup({ app: 'LifeMax', state: Object.assign(freshState(), { name: 'Imported', onboardingComplete: true }) }); });
      await p.evaluate(() => toggleQuest(state.quests[0].id, true));
      const r = await p.evaluate(() => ({ epoch: LmaShadow.status().epoch, last: window.__shadowMock.bases.slice(-1)[0], kinds: window.__shadowMock.envelopes.slice(-2).map(e => e.kind) }));
      const s = await same(p);
      ok('an import raises the epoch, its own saves are not actions, and a base follows', r.epoch === e0 + 1 && r.last.epoch === e0 + 1 && !r.kinds.includes('save.other') && s.equal, { r, s });
      await p.evaluate(async () => { await resetDemo(); });
      await p.evaluate(() => toggleQuest(state.quests[0].id, true));
      const r2 = await p.evaluate(() => LmaShadow.status().epoch), s2 = await same(p);
      ok('a reset raises it again', r2 === e0 + 2 && s2.equal, s2.diff);
    }
    {
      // Health sync: async, its plugin answers recorded; then one with a Log in the middle (interleaved)
      await p.evaluate(() => {
        let slow = null;
        window.__health = { status: async () => ({ sdk: 'available', granted: ['exercise', 'steps', 'sleep', 'weight'] }), readExercise: async () => { if (slow) await slow; return { sessions: [] } },
          readSteps: async () => ({ days: [{ day: getToday(), count: 9000 }] }), readSleep: async () => ({ sessions: [] }), readWeight: async () => ({}) };
        window.__slow = on => { slow = on ? new Promise(r => (window.__release = r)) : null };
        const real = lmaPlugin; lmaPlugin = function (n) { return n === 'HealthSync' ? window.__health : real.apply(this, arguments) };
        const H = lmaHealthCfg(); H.on = true; H.ex = H.steps = H.sleep = H.wt = true;
      });
      await p.evaluate(() => lmaHealthSync(true));
      await p.waitForTimeout(80);
      const e = await p.evaluate(() => window.__shadowMock.envelopes.filter(x => x.kind === 'health.sync').slice(-1)[0]);
      ok('a Health sync is one health.sync envelope with the plugin\'s answers in env.health, its own save inside it', !!e && e.env.health && e.env.health.status && e.env.health.status.sdk === 'available' && Array.isArray(e.env.health.steps) && Array.isArray(e.env.health.exercise) && e.env.health.now >= e.t0 && !e.flags.includes('interleaved') && e.env.saves.length >= 1, e);
      await p.evaluate(async () => { __slow(true); const sync = lmaHealthSync(true); await new Promise(r => setTimeout(r, 20)); toggleQuest(state.quests[2].id, true); __release(); await sync; });
      await p.waitForTimeout(80);
      const last = await p.evaluate(() => window.__shadowMock.envelopes.slice(-2).map(x => [x.kind, x.flags]));
      const s = await same(p);
      ok('an action inside a pending sync marks the sync interleaved, and the copy stays equal', last.some(([k, f]) => k === 'health.sync' && f.includes('interleaved')) && last.some(([k]) => k === 'quest.toggle') && s.equal, { last, s });
    }
    {
      // too large: a 300 KB note makes the envelope too large to send; the next action brings a base
      const b0 = await p.evaluate(() => window.__shadowMock.bases.length);
      await p.evaluate(() => recordActivity({ type: Object.keys(rules)[0], duration: 20, quality: 3, notes: 'x'.repeat(300 * 1024), date: new Date().toISOString() }));
      await p.waitForTimeout(60);
      const st = await p.evaluate(() => LmaShadow.status().stats.skipped);
      await p.evaluate(() => save()); // an action that leaves the big record where it is
      const s = await same(p), b1 = await p.evaluate(() => window.__shadowMock.bases.length);
      ok('an envelope over 256 KB is not sent (skipped.size); a base comes next and the copy is equal', st.size === 1 && b1 === b0 + 1 && s.equal, { st, b0, b1, s });
    }
    {
      // twenty actions in one task: the page's queue holds sixteen, then starts again from a base
      await p.evaluate(() => { for (let i = 0; i < 20; i++) save(); });
      const s = await same(p), st = await p.evaluate(() => LmaShadow.status().stats.skipped);
      ok('a queue overflow drops what waits, counts skipped.queue and resyncs', st.queue >= 1 && s.equal, { st, s });
    }
    {
      // throws are the page's, untouched
      const r = await p.evaluate(() => { try { toggleQuest('no-such-quest', true); return 'no throw' } catch (e) { return 'threw ' + e.message } });
      const direct = await p.evaluate(() => { try { toggleQuest.__lmaShadow('no-such-quest', true); return 'no throw' } catch (e) { return 'threw ' + e.message } });
      ok('a wrapped function answers or throws exactly as the original', r === direct, { r, direct });
    }
    {
      // the switch off: wrappers go, native is told
      await p.evaluate(async () => { await setNativeShadowEnabled(false) });
      const r = await p.evaluate(() => { const n = window.__shadowMock.envelopes.length; toggleQuest(state.quests[0].id, true); return { wrapped: !!save.__lmaShadow, n, after: window.__shadowMock.envelopes.length, config: window.__shadowMock.config, pref: lmaLoad().nativeShadow } });
      await p.waitForTimeout(60);
      ok('turning it off removes every wrapper, tells native and sends nothing more', !r.wrapped && r.after === r.n && r.config.enabled === false && r.pref === false, r);
      ok('the page threw nothing while the check ran', !p.errors.length, p.errors.slice(0, 3));
    }
    await p.context().close();

    /* ---------- the Settings row of an app that compares (WP6b) ---------- */
    {
      const q = await open(browser, { compares: { compared: 1150, different: 3 } }, SAVE);
      const row = await q.evaluate(async () => { navigate('settings'); await LmaShadow.refresh(); return document.getElementById('nativeShadowStatus').textContent });
      ok('the row counts the actions checked and the differences, with the last one\'s day and kind', /^Checked 1,150 actions since 1 Nov · 3 different · last 18 Nov \d\d:\d\d \(quest\)$/.test(row), row);
      await q.context().close();
    }

    /* ---------- WP6c: an app that compares quests and the Daily Hand ---------- */
    for (const quests of [true, false]) {
      const p = await open(browser, { quests });
      const r = await p.evaluate(async () => {
        const settle = () => new Promise(r => setTimeout(r, 60));
        const m = window.__shadowMock, at = () => m.envelopes.length;
        const q = state.quests.find(x => !x.hand && !isQuestDone(x));
        const note = document.getElementById('qnote-' + q.id) || Object.assign(document.body.appendChild(document.createElement('input')), { id: 'qnote-' + q.id });
        note.value = 'my note';
        let n = at(); toggleQuest(q.id, true); await settle(); const toggle = m.envelopes.slice(n).find(e => e.kind === 'quest.toggle');
        n = at(); recordActivity({ type: 'reading', customName: '', duration: '30', quality: '1', skill: '', primary: '', secondary: '', notes: '' }); await settle();
        const log = m.envelopes.slice(n).find(e => e.kind === 'log.record');
        /* the journal's redraw inside the save decides its day and writes it with its own plain write, as on a new day */
        const draw = renderHistory;
        renderHistory = function () { jcWrite(); return draw.apply(this, arguments) };
        n = at(); try { save() } finally { renderHistory = draw }
        await settle(); const bare = m.envelopes.slice(n).find(e => e.kind === 'save.other');
        n = at(); switchClassTo('scholar'); await settle(); const sw = m.envelopes.slice(n).map(e => e.kind);
        n = at(); const skipped = (LmaShadow.status().stats.skipped.noop || 0);
        for (let i = 0; i < 60; i++) { dhEnsure(); dhAutoVerify() }
        await settle();
        const noop = m.envelopes.slice(n).map(e => e.kind), st = LmaShadow.status();
        return { quests: st.quests, toggle: toggle && toggle.env, log: log && log.env, bare: bare && bare.env, sw, noop, skipped: (st.stats.skipped.noop || 0) - skipped, today: getToday(), id: q.id };
      });
      const s = await same(p);
      const word = quests ? 'an app that compares quests' : 'an app without economy.shadow.quests.v1';
      if (quests) {
        ok('WP6c: the page sees the capability', r.quests === true, r);
        ok('WP6c: a toggle sends its quest\'s note field and today\'s plan facts', r.toggle && r.toggle.dom && r.toggle.dom['qnote-' + r.id] === 'my note' && r.toggle.plan && r.toggle.plan.from === r.today, r.toggle);
        ok('WP6c: a Log sends today\'s plan facts (its save may deal and check the Daily Hand)', r.log && r.log.plan && r.log.plan.from === r.today && Array.isArray(r.log.plan.rest), r.log);
        ok('WP6c: the journal coach\'s own plain write is sent apart from the economy\'s writes', r.bare && Array.isArray(r.bare.journal) && r.bare.journal.length >= 1 && r.bare.saves.length === 1, r.bare);
        ok('WP6c: a class switch is its quest pack, then its save', r.sw[0] === 'quest.pack' && r.sw.includes('save.other'), r.sw);
        ok('WP6c: the Daily Hand\'s redraws that change nothing go one in fifty, the rest counted', r.noop.length >= 2 && r.noop.length <= 4 && r.skipped >= 100, { n: r.noop.length, skipped: r.skipped });
      } else {
        ok('WP6c: ' + word + ': no plan facts on a Log, no journal part', r.quests === false && r.log && !r.log.plan && r.bare && !r.bare.journal && r.bare.saves.length >= 2, { log: r.log, bare: r.bare });
      }
      ok('WP6c: ' + word + ': the copy equals the page', s.equal, s.diff);
      ok('WP6c: ' + word + ': the page threw nothing', !p.errors.length, p.errors);
      await p.context().close();
    }

    /* ---------- WP6d: an app that compares the Health sync and the day roll ---------- */
    for (const day of [true, false]) {
      const D = 864e5, now = Date.now(), at = n => new Date(now - n * D).toISOString(), key = n => Math.round((now - n * D) / D);
      // three days without the app after a week of training, one shield in stock: the boot's pass ticks the shields
      const idle = { onboardingComplete: true, name: 'N', tutorialDone: true, classId: 'athlete', tipsSeen: {}, xpSchemaV2: true,
        activities: Array.from({ length: 8 }, (_, i) => ({ id: 'seed-' + i, type: 'reading', name: 'Seed', duration: 30, quality: 1, skill: 'Seed', primary: 'intelligence', secondary: '', xp: 120, date: at(i + 3), notes: '', xpSplit: [{ attr: 'intelligence', xp: 120 }], sp: 1 })),
        decayApplied: { intelligence: key(30) }, shieldTick: key(4), shieldMiss: 0, streakShields: 1, shieldMilestone: 1,
        health: { on: true, ex: true, steps: true, sleep: true, wt: true, seen: {}, last: 0, since: now - 2 * D, msg: '', sdk: '', granted: [], src: '', today: null } };
      const p = await open(browser, { quests: true, day, health: true }, idle);
      const r = await p.evaluate(async () => {
        const settle = (ms) => new Promise(r => setTimeout(r, ms || 60)), m = window.__shadowMock, n0 = () => m.envelopes.length;
        const boot = m.envelopes.find(e => e.kind === 'boot.decay');
        __health.steps = [{ date: getToday(), count: 3000 }];
        let n = n0(); await lmaHealthSync(true); await settle(); const paid = m.envelopes.slice(n).find(e => e.kind === 'health.sync');
        __health.fail = 'readSteps'; n = n0(); await lmaHealthSync(true); await settle(); const failed = m.envelopes.slice(n).find(e => e.kind === 'health.sync');
        __health.fail = 'status'; n = n0(); await lmaHealthSync(true); await settle(); const status = m.envelopes.slice(n).find(e => e.kind === 'health.sync');
        __health.fail = null; __health.delay = 30;
        n = n0(); const first = lmaHealthSync(true); await settle(5); markVisit(); await Promise.all([first, lmaHealthSync(true)]); await settle(120);
        const both = m.envelopes.slice(n).filter(e => e.kind === 'health.sync');
        __health.delay = 0;
        const real = window.lmaPlugin; window.lmaPlugin = function (x) { return x === 'HealthSync' ? null : real.apply(this, arguments) };
        n = n0(); try { await lmaHealthSync(true) } finally { window.lmaPlugin = real } await settle(); const none = m.envelopes.slice(n).find(e => e.kind === 'health.sync');
        state.decayApplied = { intelligence: dayNum(getToday()) - 12 };
        // the earliest day the pass asks about: the walk's first day, or the shield tick's
        const t = dayNum(getToday()), bound = ecoDayKey(Math.min(Math.max(dayNum(lastTrained('intelligence')) + getDecay().grace + 1, t - 11), state.shieldTick + 1));
        n = n0(); applyDecay(); await settle(); const pass = m.envelopes.slice(n).find(e => e.kind === 'decay.apply');
        return { day: LmaShadow.status().day, boot, paid, failed, status, both, none, pass, bound, old: addDays(getToday(), -60), today: getToday() };
      });
      const s = await same(p);
      const word = day ? 'an app that compares the day' : 'an app without economy.shadow.day.v1';
      const ranges = e => ['exercise', 'steps', 'sleep', 'weight'].flatMap(k => (e.env.health[k] || []).map(x => x.to));
      if (day) {
        ok('WP6d: the page sees the capability', r.day === true, r.day);
        ok('WP6d: the boot\'s decay sends the plan as far back as the shield tick walks, and its toasts', r.boot && r.boot.env.plan && r.boot.env.plan.from <= new Date((idle.shieldTick + 1) * D).toISOString().slice(0, 10) && r.boot.toasts.length >= 1, r.boot && { env: r.boot.env, toasts: r.boot.toasts });
        ok('WP6d: a sync records that the plugin answered, the status and each read, every range ending at the sync\'s own clock', r.paid && r.paid.env.health.plugin === true && r.paid.env.health.status.sdk === 'available' && ranges(r.paid).length === 4 && new Set(ranges(r.paid)).size === 1 && ranges(r.paid)[0] >= r.paid.env.health.now && ranges(r.paid)[0] <= r.paid.t1, r.paid && r.paid.env.health);
        ok('WP6d: a read that throws is named with the message the sync shows', r.failed && r.failed.env.health.fail === 'steps' && r.failed.env.health.error === 'Health Connect stopped answering' && !r.failed.env.health.steps, r.failed && r.failed.env.health);
        ok('WP6d: a status that throws is named too', r.status && r.status.env.health.fail === 'status' && /stopped answering/.test(r.status.env.health.error) && !r.status.env.health.status, r.status && r.status.env.health);
        ok('WP6d: a sync started while one runs says so (busy) and saves nothing; the first is interleaved', r.both.length === 2 && r.both.some(e => e.env.health.busy === true && !(e.env.saves || []).length) && r.both.some(e => e.flags.includes('interleaved')), r.both.map(e => ({ h: e.env.health.busy, f: e.flags, saves: e.env.saves })));
        ok('WP6d: a check-in\'s write while a sync waits is not the sync\'s', r.both.every(e => (e.env.saves || []).length <= 1), r.both.map(e => e.env.saves));
        ok('WP6d: without the plugin the sync says so', r.none && r.none.env.health.plugin === false && r.none.toasts.length === 1, r.none && { h: r.none.env.health, t: r.none.toasts });
        ok('WP6d: the decay pass sends the plan from no later than the first day it asks about, not a fixed 60 days', r.pass && r.pass.env.plan && r.pass.env.plan.from <= r.bound && r.pass.env.plan.from > r.old, r.pass && { plan: r.pass.env.plan, bound: r.bound, old: r.old });
      } else {
        ok('WP6d: ' + word + ': the WP6c shapes (no plugin flag, no error, no boot plan or toasts)', r.day === false && r.paid && !('plugin' in r.paid.env.health) && r.failed && !('error' in r.failed.env.health) && r.boot && !r.boot.env.plan && !r.boot.toasts.length, { paid: r.paid && r.paid.env.health, boot: r.boot && r.boot.env });
      }
      ok('WP6d: ' + word + ': the copy equals the page', s.equal, s.diff);
      ok('WP6d: ' + word + ': the page threw nothing', !p.errors.length, p.errors);
      await p.context().close();
    }

    /* ---------- the cost on a 3 MB save ---------- */
    {
      const big = JSON.parse(JSON.stringify(SAVE)), recs = SAVE.activities;
      const per = JSON.stringify(recs).length / recs.length, n = Math.ceil(3 * 1024 * 1024 / per);
      big.activities = Array.from({ length: n }, (_, i) => Object.assign({}, recs[i % recs.length], { id: 'big-' + i }));
      const q = await open(browser, {}, big);
      const r = await q.evaluate(async () => {
        const costs = [];
        for (let i = 0; i < 20; i++) { const t = performance.now(); toggleQuest(state.quests[i % state.quests.length].id, i % 2 === 0); costs.push(performance.now() - t); await new Promise(r => setTimeout(r, 0)); }
        await new Promise(r => setTimeout(r, 100));
        const st = LmaShadow.status(), c = st.stats.costs.slice(-20).sort((a, b) => a - b);
        return { bytes: JSON.stringify(state).length, bases: window.__shadowMock.bases, p50: c[9], p95: c[18], sizes: window.__shadowMock.envelopes.slice(-20).map(e => e.chars), live: st.live };
      });
      const s = await same(q);
      console.log('    3 MB save: ' + (r.bytes / 1048576).toFixed(2) + ' MB, base in ' + r.bases[0].parts + ' parts, shadow cost per action p50 ' + r.p50.toFixed(1) + ' ms p95 ' + r.p95.toFixed(1) + ' ms, envelopes up to ' + Math.max(...r.sizes) + ' chars');
      ok('a 3 MB save crosses as one base in parts under the 32-part limit', r.bytes > 3 * 1024 * 1024 && r.bases.length === 1 && r.bases[0].parts <= 32 && r.live, r.bases);
      ok('each action\'s envelope stays small on a 3 MB save (under 20 KB)', Math.max(...r.sizes) < 20 * 1024, r.sizes);
      ok('the shadow\'s main-thread cost per action stays under the 50 ms hard limit at 3 MB (p95)', r.p95 < 50 && r.live, r);
      ok('and the copy equals the page', s.equal, s.diff);
      await q.context().close();
    }
  } finally { await browser.close() }
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
