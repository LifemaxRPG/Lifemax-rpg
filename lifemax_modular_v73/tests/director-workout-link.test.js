'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm'), assert = require('assert/strict');
const source = fs.readFileSync(process.env.DIRECTOR_ENGINE || path.join(__dirname, '../src/features/life-director/engine.js'), 'utf8');
const from = source.indexOf('  H.openPlanDay = key =>');
const to = source.indexOf('\n  H._test', from);
let active = true, scrolled = 0, focused = 0;
const frames = [], calls = [];
const heading = { setAttribute() {}, focus(o) { assert.equal(o.preventScroll, true); focused++; } };
const details = { scrollIntoView(o) { assert.equal(o.block, 'start'); assert.equal(o.behavior, 'auto'); scrolled++; }, querySelector: () => heading };
const context = { H: { openView: v => calls.push(v) }, planDay: key => key === '2026-11-06' ? {} : null,
  selectDay: key => { context.planSel = key; }, planSel: null, fxReduced: () => true,
  requestAnimationFrame: fn => frames.push(fn),
  document: { getElementById: () => ({ classList: { contains: () => active }, querySelector: () => details }) } };
vm.runInNewContext(source.slice(from, to), context);
context.H.openPlanDay('2026-11-06');
assert.equal(context.planSel, '2026-11-06');
assert.equal(scrolled, 0);
frames.shift()();
assert.equal(scrolled, 1); assert.equal(focused, 1); assert.deepEqual(calls, ['plan']);
context.H.openPlanDay('2026-11-06'); active = false; frames.shift()(); assert.equal(scrolled, 1, 'leaving the tab cancels reveal');
active = true; context.H.openPlanDay('2026-11-06'); context.planSel = '2026-11-07'; frames.shift()(); assert.equal(scrolled, 1, 'newer day selection wins');
context.H.openPlanDay('missing'); assert.equal(frames.length, 0, 'missing workout does not reveal a different day');
console.log('PASS exact workout selection, reveal, focus, reduced motion and stale-navigation guards');
