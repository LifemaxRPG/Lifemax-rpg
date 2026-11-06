'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm'), assert = require('assert/strict');
const base = process.env.DIRECTOR_SOURCE || path.join(__dirname, '../src/features/life-director');
const engine = fs.readFileSync(path.join(base, 'engine.js'), 'utf8');
const logic = fs.readFileSync(path.join(base, 'logic.js'), 'utf8');
const sandbox = { module: { exports: {} } };
vm.runInNewContext(engine.slice(engine.indexOf('/* ── Life Director Store'), engine.indexOf('/* ── Life Director ⇄')), sandbox);
const S = sandbox.module.exports;
const data = new Map();
let failKey = null;
const storage = {
  getItem: k => data.has(k) ? data.get(k) : null,
  setItem(k, v) { if (k === failKey) throw new Error('Storage full'); data.set(k, String(v)); },
  removeItem: k => data.delete(k)
};
const store = S.create(storage);
const opts = { playerId: 'brad', timezone: 'America/New_York', nowEpoch: 12345 };
const old = S.defaultState(opts);
old.setupComplete = true;
old.goals = [{ id: 'old-goal' }];
old.schedule = { saved: true };
old.ui.focus = { id: 'old-timer' };
data.set('lifemax_rpg_prototype_v1', 'main-save-untouched');
assert(store.save(old).ok);
assert(store.clear(opts).ok);
let loaded = S.create(storage).load(opts);
assert.equal(loaded.report.recovered, false);
assert.equal(loaded.state.setupComplete, false);
assert.equal(loaded.state.goals.length, 0);
assert.equal(loaded.state.schedule, null);
assert.equal(loaded.state.ui.focus, undefined);
assert.equal(loaded.state.playerId, 'brad');
assert.equal(loaded.state.availability.timezone, opts.timezone);
assert.equal(data.get('lifemax_rpg_prototype_v1'), 'main-save-untouched');
assert.equal(JSON.parse(data.get(S.BAK)).setupComplete, true, 'keep ordinary recovery backup');
assert(store.clear(opts).ok, 'repeated reset is safe');
assert.equal(S.create(storage).load(opts).report.recovered, false);

// Real action: a refused write must leave both live state and persisted state alone.
assert(store.save(old).ok);
const original = data.get(S.KEY);
const ui = { banner: { kind: 'warn', text: 'old recovery warning' }, sheet: {}, dayIdx: 8, prevPlan: {} };
let boots = 0, renders = 0;
const ctx = { store, Host: { playerId: () => 'brad' }, deviceTz: () => opts.timezone,
  nowEpoch: () => opts.nowEpoch, lastSave: null, ui, nativeCalendarOffset: 40,
  boot() { boots++; loaded = store.load(opts); }, render() { renders++; } };
const start = logic.indexOf('    reset() {');
const end = logic.indexOf('    /* ── My time', start);
assert(start > 0 && end > start);
const action = vm.runInNewContext('({' + logic.slice(start, end) + '})', ctx);
for (const key of [S.BAK, S.KEY]) {
  failKey = key;
  assert.equal(action.reset().ok, false);
  assert.equal(boots, 0, 'never reboot into a failed reset');
  assert.equal(data.get(S.KEY), original);
  assert.equal(ctx.nativeCalendarOffset, 40);
  assert.match(ui.banner.text, /was not reset/);
}
failKey = null;
assert.equal(action.reset().ok, true);
assert.equal(boots, 1);
assert.equal(renders, 3);
assert.equal(loaded.state.setupComplete, false);
assert.equal(loaded.report.recovered, false);
assert.equal(ui.banner, null);
assert.equal(ui.sheet, null);
assert.equal(ui.dayIdx, 0);
assert.equal(ui.prevPlan, null);
assert.equal(ctx.nativeCalendarOffset, null);

// Genuine corruption still recovers; reset does not disable backup protection.
assert(store.save(old).ok);
assert(store.save(S.defaultState(opts)).ok);
data.set(S.KEY, '{broken');
assert.equal(store.load(opts).report.recovered, true);
assert.equal(store.load(opts).state.setupComplete, true);
console.log('PASS reset, reload, repeat, main-save isolation, storage failures, UI cleanup, real recovery');
