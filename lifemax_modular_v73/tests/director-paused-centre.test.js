'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm'), assert = require('assert/strict');
const source = fs.readFileSync(process.env.DIRECTOR_LOGIC || path.join(__dirname, '../src/features/life-director/logic.js'), 'utf8');
function extract(name) { const a = source.indexOf('  function ' + name + '('), b = source.indexOf('\n  function ', a + 1); return source.slice(a, b); }
const focus = { id: 'walk', start: 1000, paused: 2000, pausedMs: 0 };
const context = { ST: { ui: { focus } }, findInst: () => ({ title: 'Brisk walk', startEpoch: 0, endEpoch: 900 }),
 nowState: () => ({ cur: null }), esc: x => x, mins: x => x + 'm', focusTimer: f => ({ startedAt: f.start, paused: f.paused }), focusMs: () => 1000, clockText: () => '0:01' };
const core = vm.runInNewContext('(' + extract('nativeCore') + ')', context);
const web = vm.runInNewContext('(' + extract('coreToday') + ')', context);
assert.equal(core({}, [], true, 0, { cur: null }).timer, null);
assert.equal(core({}, [], true, 0, { cur: null }).title, 'Free time');
assert(!web({}, [], 0).includes('Timer running'));
assert.equal(context.ST.ui.focus, focus, 'paused progress is retained');
focus.paused = null;
assert.equal(core({}, [], true, 0, { cur: null }).kick, 'Timer running');
assert(web({}, [], 0).includes('Timer running'));
console.log('PASS paused timers leave both dial centres; resumed timers return; progress preserved');
