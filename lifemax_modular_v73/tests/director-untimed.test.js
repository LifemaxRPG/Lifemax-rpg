'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert/strict');
const read = (local, source) => fs.readFileSync(fs.existsSync(path.join(__dirname, local)) ? path.join(__dirname, local) : path.join(__dirname, source), 'utf8');
const engine = read('untimed-engine.js', '../src/features/life-director/engine.js');
const logic = read('untimed-logic.js', '../src/features/life-director/logic.js');
const parse = engine.split('\n').find(l => l.includes('function parseMinutes(text)'));
const from = engine.indexOf('  H.isUntimedQuest = function');
const to = engine.indexOf('  H.readQuests = function', from);
assert(parse && from >= 0 && to > from, 'read the actual host classifier');
const host = vm.runInNewContext('const H = {};\n' + parse + '\n' + engine.slice(from, to) + '\nH;');
const base = { hand: true, cadence: 'daily', verify: 'self', name: 'Listen first', desc: 'In your next conversation, ask two questions before talking about yourself.' };
assert.equal(host.isUntimedQuest(base), true);
assert.equal(host.isUntimedQuest({ ...base, desc: 'Read for 15 minutes.' }), false);
assert.equal(host.isUntimedQuest({ ...base, desc: 'Walk for an hour.' }), false);
assert.equal(host.isUntimedQuest({ ...base, desc: 'Focus for ten minutes.' }), false);
assert.equal(host.isUntimedQuest({ ...base, desc: 'Hold for 30 seconds.' }), false);
assert.equal(host.isUntimedQuest({ ...base, cadence: 'weekly' }), false, 'only daily cards become all-day tasks');
assert.equal(host.isUntimedQuest({ ...base, hand: false }), false, 'custom quests retain scheduling');
assert.equal(host.isUntimedQuest({ ...base, verify: 'activity' }), false, 'activity-verified quests retain sessions');
const start = logic.indexOf('  function syncHost() {');
const end = logic.indexOf('\n  }', start) + 4;
const make = (id, extra = {}) => ({ instanceId: id, lifecycleState: 'PLANNED', linkedQuestRequirementIds: [{ questId: 'listen' }], linkedGoalIds: [], ...extra });
const retire = make('automatic');
const pinned = make('pinned', { isUserLocked: true });
const active = make('active');
const mixed = make('mixed', { linkedQuestRequirementIds: [{ questId: 'listen' }, { questId: 'workout' }] });
const goal = make('goal', { linkedGoalIds: ['goal-1'] });
const context = {
  Host: { connected: () => true, activitiesSince: () => [], milestones: () => [] },
  ST: { ui: { focus: { id: 'active' } }, hostSync: { seen: {} }, ledger: { revoked: {} }, schedule: { scheduledInstances: [retire, pinned, active, mixed, goal], historyInstances: [], passiveReminders: [] } },
  nowEpoch: () => 0,
  hostQuests: () => [{ id: 'listen', title: 'Listen first', schedulingMode: 'NONE' }, { id: 'workout', schedulingMode: 'SESSIONS' }],
};
const sync = vm.runInNewContext('(' + logic.slice(start, end).trim() + ')', context);
assert.equal(sync(), true);
assert.deepEqual(context.ST.schedule.scheduledInstances.map(i => i.instanceId), ['pinned', 'active', 'mixed', 'goal']);
assert.equal(context.ST.schedule.passiveReminders.length, 1);
assert.equal(context.ST.schedule.passiveReminders[0].title, 'Listen first');
assert.equal(sync(), false, 'migration is idempotent');
assert.equal(context.ST.schedule.passiveReminders.length, 1);
assert.equal(context.ST.schedule.historyInstances.length, 0, 'no penalty or history event');
console.log('PASS untimed task classification, explicit durations, conservative migration, reminders, and idempotence');
