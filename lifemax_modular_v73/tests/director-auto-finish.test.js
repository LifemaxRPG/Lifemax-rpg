'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert/strict');
const local = path.join(__dirname, 'logic.js');
const source = fs.readFileSync(fs.existsSync(local) ? local : path.join(__dirname, '../src/features/life-director/logic.js'), 'utf8');
const match = source.match(/    timer\(\{ act, id, start \}\) \{([\s\S]*?)\n    \},\n    \/\* Something came up/);
assert(match, 'authoritative timer command must exist');
const tickStart = source.indexOf('  function focusTick() {');
const tickEnd = source.indexOf('\n  }', tickStart) + 4;
const tickSource = source.slice(tickStart, tickEnd);
function fixture() {
  const inst = { instanceId: 'session-a', lifecycleState: 'PLANNED', startEpoch: 0, endEpoch: 900 };
  const paid = [];
  const c = {
    ST: { ui: { focus: { id: inst.instanceId, start: 1000, paused: null, pausedMs: 0, plannedMs: 900000 } }, schedule: { historyInstances: [] } },
    now: 1000, failSave: false, paid, inst, root: null, ui: { fx: {} },
    document: { querySelectorAll: () => [] },
    persist() {}, render() {}, say() {}, doable: () => true, nowEpoch: () => 0,
    gone: { ok: false }, recorded: () => ({ ok: true, duplicate: true }), saved: () => ({ ok: true }), refusedFinish: () => ({ ok: false }),
  };
  c.Date = { now: () => c.now };
  c.plannedInst = id => id === inst.instanceId && inst.lifecycleState === 'PLANNED' ? inst : null;
  c.findInst = c.plannedInst;
  c.focusMs = f => Math.max(0, (f.paused || c.now) - f.start - (f.pausedMs || 0));
  c.finishInstance = (i, minutes) => { if (c.failSave) return null; paid.push(minutes); i.lifecycleState = 'COMPLETED'; return {}; };
  c.Today = { timer: vm.runInNewContext('(function({ act, id, start }) {' + match[1] + '\n})', c) };
  c.tick = vm.runInNewContext('(' + tickSource.trim() + ')', c);
  return c;
}
let c = fixture();
c.now = 900999; c.tick(); assert.equal(c.paid.length, 0, 'do not finish before target');
c.now = 901000; c.tick(); assert.deepEqual(c.paid, [15], 'finish at target even without a mounted dial');
c.tick(); c.Today.timer({ act: 'autoFinish', id: 'session-a', start: 1000 }); assert.equal(c.paid.length, 1, 'no duplicate completion');
c = fixture(); c.now = 7201000; c.tick(); assert.deepEqual(c.paid, [15], 'resume catch-up pays only planned minutes');
c = fixture(); c.now = 901000; c.ST.ui.focus.paused = 500000; c.tick(); assert.equal(c.paid.length, 0, 'paused timer cannot finish');
c.ST.ui.focus.pausedMs = 500000; c.ST.ui.focus.paused = null; c.tick(); assert.equal(c.paid.length, 0, 'pause duration extends completion deadline');
c.now = 1401000; c.tick(); assert.deepEqual(c.paid, [15], 'finish after remaining active time');
c = fixture(); c.now = 901000; c.Today.timer({ act: 'autoFinish', id: 'another', start: 1000 }); c.Today.timer({ act: 'autoFinish', id: 'session-a', start: 999 }); assert.equal(c.paid.length, 0, 'stale or foreign timer cannot complete');
c = fixture(); c.now = 901000; c.failSave = true; c.tick(); assert(c.ST.ui.focus, 'failed completion retains timer'); assert.equal(c.paid.length, 0); c.failSave = false; c.tick(); assert.deepEqual(c.paid, [15], 'completion recovers after failed save');
c = fixture(); c.inst.endEpoch = 1800; c.now = 901000; c.tick(); assert.deepEqual(c.paid, [15], 'schedule edits do not change an already-started allotment');
c = fixture(); c.ST.ui.focus = null; c.Today.timer({ act: 'start', id: 'session-a' }); assert.equal(c.ST.ui.focus.plannedMs, 900000, 'starting snapshots planned duration');
console.log('PASS authoritative automatic completion: deadline, pause, hidden dial, catch-up, identity, duplicates, save failure, and duration snapshot');
