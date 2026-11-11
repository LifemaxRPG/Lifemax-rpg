/* Runs Life Director's interface controller (src/features/life-director/logic.js) without its screen, for the host-level recorder
   (tools/gen_ld_host_golden.js, WP-LD9) and its web test.

   logic.js is one IIFE (LifeDirectorApp) over the engine (LifeDirectorEngine), the store (LifeDirectorStore) and the LifeMax host
   (LifeMaxHost), with the page's globals (window, document, Date, crypto, timers). runHostCases() below evaluates the three texts with
   `new Function`, every one of those names a parameter, so each case gets fresh modules over a recording host, a storage of its own and a
   clock that stands still between steps. The same function runs in node and inside the built page in Chromium (it is sent there as text),
   so both runners execute identical code; only Intl and the JavaScript engine are the runner's own.

   hostSource() is logic.js with three behaviour-neutral changes, each found exactly once (HOOKS):
     1. the IIFE's returned object also carries __ldHost: the private functions WP-LD9 ports (syncHost, processMisses, maybeRollover,
        applyOutput, finishInstance, logToday, repairEarlyDone, applyDial, tick, boot, persist, Do, Today) and a reader of the
        closure's variables (ST, ui, takeBack, takenBackOp, lastSave, lastFinishRefusal);
     2. tick(true) skips tick's two guards (the view mounted and active), which need a DOM; tick() is unchanged;
     3. closeSheet() returns when there is no root (with a root it is unchanged; without one the page's version throws, and no page path
        calls it without one: applyDial runs only from a mounted Today).
   render() already returns without a root, and say() reaches the host's toast first, so nothing else touches a DOM. */
'use strict';
const fs = require('fs'), path = require('path');
const engineVm = require('./ld_engine_vm.js');

const LOGIC = path.join(__dirname, '..', 'src', 'features', 'life-director', 'logic.js');
const HOOKS = [
  ['  return { mount, glance, widgetSnapshot, voice: voiceApi,',
    '  return { __ldHost: { syncHost, processMisses, maybeRollover, applyOutput, regenerate, finishInstance, logToday, repairEarlyDone, afterEvent, applyDial, boot, persist, tick, Do, Today,\n'
    + '    vars: () => ({ ST, ui, takeBack, takenBackOp, lastSave, lastFinishRefusal }) }, mount, glance, widgetSnapshot, voice: voiceApi,'],
  ["  function tick() {\n    if (!root || !root.isConnected) return;\n    const view = root.closest('.view');\n    if (view && !view.classList.contains('active')) return;\n",
    "  function tick(__ldNow) {\n    if (!__ldNow) { if (!root || !root.isConnected) return;\n    const view = root.closest('.view');\n    if (view && !view.classList.contains('active')) return; }\n"],
  ["  function closeSheet() {\n    const sh = root.querySelector('.ld-sheet');", "  function closeSheet() {\n    if (!root) return;\n    const sh = root.querySelector('.ld-sheet');"],
];

/* logic.js exactly as the file has it. */
const logicText = () => fs.readFileSync(LOGIC, 'utf8');

/* logic.js with HOOKS applied; throws when a hook's text is not there exactly once. */
function hostSource() {
  let src = logicText();
  for (const [from, to] of HOOKS) {
    const at = src.indexOf(from);
    if (at < 0 || src.indexOf(from, at + 1) >= 0) throw new Error('logic.js: `' + from.split('\n')[0].trim() + '` is not there exactly once; update tools/ld_host_vm.js');
    src = src.slice(0, at) + to + src.slice(at + from.length);
  }
  return src;
}

/* The three texts runHostCases evaluates. */
const sources = () => ({ engine: engineVm.engineSource(), store: engineVm.storeSource(), logic: hostSource() });

/* Runs every case and answers each with its steps' outcomes. Self-contained (it is sent into the page as text).

   A case is {id, family, rule, start: {at, deviceTz, saved, storage, host, banner}, steps: [step]}:
     at       the clock (epoch milliseconds) at boot; each step has its own `at`
     saved    the text in lifemax_director_state_v1 before boot (null: nothing saved); bak, the text in its .bak
     storage  'ok' | 'quota' | 'broken' | 'blocked': how the storage answers writes ('blocked': every call throws, from the probe on)
     host     the recording host's script (hostStub below)
   A step is {at, op, ...}: op 'today' and 'do' run a Today or Do command by name with its payload (the native entry points, as a native
   screen calls them); 'tick' runs tick's body; 'syncHost', 'processMisses', 'maybeRollover', 'repairEarlyDone' (its flag cleared first)
   run that function; 'applyDial' holds the change P and runs applyDial; 'applyOutput' runs it with the output given (or the current plan,
   copied) and label and mode; 'finish' and 'logToday' run finishInstance and logToday on the session of that id; 'banner' sets the page's
   banner (as a screen saw it); 'focus' sets the running timer (ST.ui.focus, as Today.timer leaves it); 'storage' changes how the storage answers from then on; 'host' replaces part of the host's script.
   Each step answers {reply, calls, session, state}: the command's answer, the host and store calls it made (with their answers), the
   page's volatile state (banner, notice, the Skip's take-back, the last save, the last refused completion, the tab) and ST in full. */
function runHostCases(src, cases) {
  const canon = v => {
    if (typeof v === 'number') return Number.isNaN(v) ? { $num: 'NaN' } : v === Infinity ? { $num: 'Infinity' } : v === -Infinity ? { $num: '-Infinity' }
      : Object.is(v, -0) ? { $num: '-0' } : v;
    if (Array.isArray(v)) return v.map(canon);
    if (v && typeof v === 'object') { const o = {}; for (const k of Object.keys(v)) if (v[k] !== undefined && typeof v[k] !== 'function') o[k] = canon(v[k]); return o; }
    return v === undefined ? null : v;
  };
  const copy = v => (v === undefined ? undefined : JSON.parse(JSON.stringify(v)));
  const KEY = 'lifemax_director_state_v1';
  const RealDate = Date;
  function runCase(c) {
    let nowMs = c.start.at, calls = [], storageMode = c.start.storage || 'ok', script = copy(c.start.host || {}), awards = 0;
    const note = (fn, args, out) => { calls.push({ fn, args: canon(copy(args)), out: canon(copy(out)) }); return out; };
    function FakeDate(...a) { if (!new.target) return new RealDate(nowMs).toString(); return a.length ? new RealDate(...a) : new RealDate(nowMs); }
    FakeDate.now = () => nowMs; FakeDate.parse = RealDate.parse; FakeDate.UTC = RealDate.UTC; FakeDate.prototype = RealDate.prototype;
    const engineHolder = {}, storeHolder = {};
    new Function('self', 'performance', 'Date', src.engine)(engineHolder, { now: () => 0 }, FakeDate);
    new Function('self', src.store)(storeHolder);
    const E = engineHolder.LifeDirectorEngine, RealStore = storeHolder.LifeDirectorStore;
    const mem = new Map();
    if (c.start.saved != null) mem.set(KEY, c.start.saved);
    if (c.start.bak != null) mem.set(KEY + '.bak', c.start.bak);
    const fail = () => { const e = new Error(storageMode === 'quota' ? 'The quota has been exceeded.' : 'disk error'); e.name = storageMode === 'quota' ? 'QuotaExceededError' : 'Error'; throw e; };
    const storage = {
      getItem: k => { if (storageMode === 'blocked') fail(); return mem.has(k) ? mem.get(k) : null; },
      setItem: (k, v) => { if (storageMode !== 'ok') fail(); mem.set(k, String(v)); },
      removeItem: k => { if (storageMode === 'blocked') fail(); mem.delete(k); },
    };
    /* the store is the page's own, over that storage; each call it answers is noted (a state as its id only: the step's state is ST) */
    const Store = Object.freeze({
      create: st => {
        const s = RealStore.create(st);
        return {
          load: o => { const r = s.load(o); note('store.load', [o], { state: r.state, report: r.report }); return r; },
          save: state => { const r = s.save(state); note('store.save', [], { ok: r.ok, error: r.error }); return r; },
          clear: o => { const r = s.clear(o); note('store.clear', [o], { ok: r.ok, error: r.error }); return r; },
          exportJson: state => note('store.exportJson', [], s.exportJson(state)),
          importJson: text => { try { const r = s.importJson(text); note('store.importJson', [text], { state: r }); return r; } catch (e) { note('store.importJson', [text], { error: e.message }); throw e; } },
          get available() { return s.available; },
        };
      },
      defaultState: RealStore.defaultState, migrate: RealStore.migrate, validate: RealStore.validate, KEY: RealStore.KEY, BAK: RealStore.BAK, CURRENT: RealStore.CURRENT,
    });
    /* the recording host (LifeMaxHost's surface that logic.js calls), answering from the case's script */
    const award = event => {
      const list = Array.isArray(script.award) ? script.award : [script.award || 'ok'], mode = list[Math.min(awards++, list.length - 1)];
      if (mode === 'failed') return { awarded: false, reason: 'HOST_SAVE_FAILED' };
      if (mode === 'readonly') return { awarded: false, reason: 'HOST_READ_ONLY' };
      if (mode === 'offline') return { awarded: false, reason: 'HOST_NOT_CONNECTED' };
      if (mode === 'already') return { awarded: false, reason: 'ALREADY_IN_LIFEMAX', activityId: 'act-earlier', xp: 7 };
      return { awarded: true, activityId: 'ld_' + event.eventId, xp: Math.round(event.actualDurationMinutes) };
    };
    const Host = {
      connected: () => note('connected', [], script.connected !== false),
      toast: msg => note('toast', [msg], script.toast !== false),
      playerId: () => note('playerId', [], script.playerId || 'player'),
      icon: () => '',
      readQuests: (now, z, overlay, revoked) => note('readQuests', [now, z, overlay, revoked], copy(script.quests || [])),
      readPlanSessions: (now, z, days) => note('readPlanSessions', [now, z, days], copy(script.planSessions || [])),
      awardActivity: (event, info) => note('awardActivity', [event, info], award(event)),
      completeQuest: ref => note('completeQuest', [ref], script.completeQuest === false ? { completed: false } : { completed: true, xp: 25, name: 'Quest ' + ref.questId }),
      activitiesSince: since => note('activitiesSince', [since], copy((script.activities || []).filter(a => !since || String(a.iso) > since))),
      activityExists: id => note('activityExists', [id], (script.existing || []).includes(id)),
      milestones: () => note('milestones', [], copy(script.milestones || [])),
    };
    if (script.retired) Host.retiredUntimedQuest = ref => note('retiredUntimedQuest', [ref], copy(script.retired[ref.questId] || null));
    const noop = () => 0;
    const doc = { getElementById: () => null, addEventListener: noop, querySelector: () => null, hidden: false };
    const win = { localStorage: storageMode === 'blocked' ? undefined : storage, addEventListener: noop };
    if (storageMode === 'blocked') Object.defineProperty(win, 'localStorage', { get: () => { throw new Error('blocked'); } });
    let uuids = 0;
    const cryptoStub = { randomUUID: () => '00000000-0000-4000-8000-' + String(++uuids).padStart(12, '0') };
    const quiet = { log: noop, warn: noop, error: noop, info: noop };
    const names = ['LifeDirectorEngine', 'LifeMaxHost', 'LifeDirectorStore', 'window', 'document', 'localStorage', 'sessionStorage', 'navigator', 'matchMedia',
      'requestAnimationFrame', 'cancelAnimationFrame', 'performance', 'crypto', 'globalThis', 'queueMicrotask', 'setTimeout', 'setInterval', 'clearTimeout',
      'clearInterval', 'getComputedStyle', 'LifeMaxMeals', 'LifeMaxFeatures', 'Date', 'Image', 'XMLSerializer', 'console'];
    const values = [E, Host, Store, win, doc, undefined, undefined, { vibrate: noop }, () => ({ matches: false }), noop, noop, { now: () => 0 }, cryptoStub,
      { crypto: cryptoStub }, noop, noop, noop, noop, noop, () => ({}), undefined, undefined, FakeDate, function () { }, function () { }, quiet];
    const App = new Function(...names, src.logic + '\n;return LifeDirectorApp;')(...values);
    const L = App.__ldHost, V = () => L.vars();
    const brief = () => {
      const v = V(), ui = v.ui, t = v.takeBack;
      return {
        banner: ui.banner || null, notice: ui.notice ? { id: ui.notice.id, text: ui.notice.text, at: ui.notice.at } : null, tab: ui.tab, dayIdx: ui.dayIdx,
        pendingDial: ui.pendingDial || null,
        takeBack: t ? { id: t.id, text: t.text, at: t.at, before: t.before, left: t.left } : null, takenBackOp: v.takenBackOp,
        lastSave: { ok: v.lastSave.ok, error: v.lastSave.error }, lastFinishRefusal: v.lastFinishRefusal,
      };
    };
    const planned = id => V().ST.schedule && V().ST.schedule.scheduledInstances.find(i => i.instanceId === id);
    /* {$ref: list, n, field, plus}: a value of the state when the step runs (list 'planned' | 'history' | 'rule' | 'goal' | 'commitment' |
       'blackout'; n counts from the end when negative; field defaults to the id; plus is added to a number). Absent: '$missing'. */
    const LISTS = { planned: st => st.schedule && st.schedule.scheduledInstances, history: st => st.schedule && st.schedule.historyInstances,
      rule: st => st.availability.rules, goal: st => st.goals, commitment: st => st.fixedCommitments, blackout: st => st.availability.blackouts };
    const ID = { planned: 'instanceId', history: 'instanceId', rule: 'id', goal: 'id', commitment: 'id', blackout: 'id' };
    const resolve = v => {
      if (Array.isArray(v)) return v.map(resolve);
      if (!v || typeof v !== 'object') return v;
      if (!('$ref' in v)) { const o = {}; for (const k of Object.keys(v)) o[k] = resolve(v[k]); return o; }
      const list = (LISTS[v.$ref](V().ST) || []), item = list[v.n < 0 ? list.length + v.n : v.n];
      if (!item) return '$missing';
      const x = item[v.field || ID[v.$ref]];
      return typeof x === 'number' && v.plus ? x + v.plus : x;
    };
    const out = [];
    const steps = [{ at: c.start.at, op: 'boot' }].concat(c.steps);
    let prevAt = c.start.at, prevState = {};
    for (const raw of steps) {
      const at = typeof raw.at === 'number' ? raw.at : raw.at && raw.at.$ref ? resolve(raw.at) * 1000 : prevAt + ((raw.at && raw.at.plus) || 0) * 1000;
      const s = resolve(Object.assign({}, raw, { at: 0 }));
      nowMs = prevAt = at; calls = [];
      let reply;
      try {
        const v = V();
        switch (s.op) {
          case 'boot': if (c.start.banner === undefined) { L.boot(); } else { L.boot(); v.ui.banner = c.start.banner; } reply = null; break;
          case 'today': reply = App.native.todayRun(s.name, s.payload); break;
          case 'do': reply = App.native.run(s.name, s.payload); break;
          case 'tick': L.tick(true); reply = null; break;
          case 'syncHost': reply = L.syncHost(); break;
          case 'processMisses': reply = L.processMisses(); break;
          case 'maybeRollover': reply = L.maybeRollover(); break;
          case 'repairEarlyDone': if (v.ST.fixes) delete v.ST.fixes.earlyDone; reply = L.repairEarlyDone(); break;
          case 'applyDial': v.ui.pendingDial = copy(s.P); reply = L.applyDial(Math.floor(nowMs / 1000)); break;
          case 'applyOutput': reply = L.applyOutput(s.out === undefined ? copy(v.ST.schedule) : copy(s.out), s.label, s.mode); break;
          case 'finish': { const i = planned(s.id); reply = i ? L.finishInstance(i, s.minutes, copy(s.opt)) : 'absent'; break; }
          case 'logToday': { const i = planned(s.id); reply = i ? L.logToday(i, s.minutes, s.opId) : 'absent'; break; }
          case 'banner': v.ui.banner = copy(s.banner); reply = null; break;
          case 'focus': v.ST.ui.focus = copy(s.focus); reply = null; break;
          case 'storage': storageMode = s.mode; reply = null; break;
          case 'host': Object.assign(script, copy(s.host)); reply = null; break;
          default: throw new Error('unknown step ' + s.op);
        }
      } catch (e) { reply = { threw: (e && e.name) || 'Error', message: String(e && e.message) }; }
      /* the state as what changed since the step before, by top-level member (set: the member's new value, removed: gone) */
      const now = canon(copy(V().ST)), state = { set: {}, removed: Object.keys(prevState).filter(k => !(k in now)) };
      for (const k of Object.keys(now)) if (JSON.stringify(now[k]) !== JSON.stringify(prevState[k])) state.set[k] = now[k];
      prevState = now;
      out.push({ at, reply: canon(copy(reply === undefined ? null : reply)), calls, session: canon(copy(brief())), state });
    }
    return out;
  }
  return cases.map(c => {
    const a = JSON.stringify(runCase(c));
    if (JSON.stringify(runCase(c)) !== a) throw new Error('not deterministic: ' + c.id);
    return JSON.stringify(Object.assign({}, c, { out: JSON.parse(a) }));
  });
}

module.exports = { HOOKS, LOGIC, logicText, hostSource, sources, runHostCases };
