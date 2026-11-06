'use strict';
// Extract the real shipped handler, not a second implementation of its rules.
const fs = require('fs'), path = require('path'), vm = require('vm'), assert = require('assert/strict');
const source = fs.readFileSync(process.env.DIRECTOR_LOGIC || path.join(__dirname, '../src/features/life-director/logic.js'), 'utf8');
const begin = source.indexOf('    timer({ act, id, start }) {');
const end = source.indexOf('    /* Something came up:', begin);
assert(begin > 0 && end > begin, 'Today.timer extraction boundary changed');
const elapsed = source.match(/const focusMs = f => ([^;]+);/);
assert(elapsed, 'focusMs extraction boundary changed');
const base = 1800000000000;
let now = base, completion = null;
const ctx = {
  ST: { ui: { focus: null }, schedule: { historyInstances: [] } }, ui: { fx: {} },
  Date: { now: () => now }, Math, Number,
  plannedInst: id => ({ instanceId: id, lifecycleState: 'PLANNED', startEpoch: base / 1000, endEpoch: base / 1000 + 900 }),
  doable: () => true, nowEpoch: () => Math.floor(now / 1000),
  persist: () => true, render() {}, say() {}, saved: () => ({ ok: true }), recorded: () => ({ ok: true }),
  gone: { ok: false }, refusedFinish: () => ({ ok: false }),
  finishInstance: (inst, minutes, opt) => {
    completion = { activityId: inst.instanceId, actualStartEpoch: opt.startEpoch, minutes };
    return { applied: true };
  },
};
vm.createContext(ctx);
ctx.focusMs = vm.runInContext('(f => ' + elapsed[1] + ')', ctx);
const timer = vm.runInContext('({' + source.slice(begin, end) + '}).timer', ctx);
const rows = [];
function step(act, offset, id = 'walk') {
  now = base + offset;
  const before = ctx.ST.ui.focus ? JSON.parse(JSON.stringify(ctx.ST.ui.focus)) : null;
  const args = { act, id, start: before && before.start };
  completion = null;
  const reply = timer(args);
  assert.equal(reply.ok, true);
  rows.push(JSON.parse(JSON.stringify({ act, now, id, before, after: ctx.ST.ui.focus, completion })));
}
step('start', 0);
step('pause', 60000);
step('pause', 120000);
step('resume', 600000);
step('resume', 660000);
step('autoFinish', 700000); // not due
step('reset', 700000);
step('autoFinish', 10000000); // paused, even after a long gap
step('resume', 10000000);
step('autoFinish', 20000000); // catch up, credit only planned duration
step('start', 21000000);
step('replace', 21010000, 'read');
step('finish', 21100000, 'read'); // ninety seconds rounds to two minutes
step('start', 22000000);
step('cancel', 22001000);
const fixture = JSON.stringify(rows, null, 2) + '\n';
const dest = process.env.TIMER_FIXTURE || path.resolve(__dirname, '../../android-wrapper/native/src/test/resources/fixtures/director-timer-rules.json');
if (process.argv.includes('--write')) fs.writeFileSync(dest, fixture);
else assert.equal(fs.readFileSync(dest, 'utf8'), fixture, 'Timer behavior changed; review Kotlin parity before regenerating');
console.log('PASS actual JavaScript timer parity: ' + rows.length + ' transitions');
