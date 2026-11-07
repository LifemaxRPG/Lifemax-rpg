'use strict';
const assert = require('assert/strict'), path = require('path');
const { createReceiver, createEffectCommitter, canonical } = require(process.env.COMPLETION_PROTOCOL || '../src/features/life-director/timer-completion-protocol.js');
const clone = x => JSON.parse(JSON.stringify(x));
const request = sequence => ({ epoch: 'epoch', sequence, id: 'epoch/' + sequence,
  activityId: 'walk-' + sequence, generation: 1000, actualStartEpoch: 1, minutes: 15 });
function harness() {
  const authority = () => ({ owner: 'NATIVE', epoch: 'epoch', highWater: 0, receipts: [], pending: null });
  let director = { timerAuthority: authority(), history: [] }, game = { timerAuthority: authority(), xp: 0, quests: 0 };
  let writes = 0, failAt = 0, after = false, planned = 0;
  function write(which, value) {
    const fail = ++writes === failAt;
    if (fail && !after) throw Error('before write');
    if (which === 'director') director = clone(value); else game = clone(value);
    if (fail) throw Error('after write');
    return true;
  }
  const effects = createEffectCommitter({ readGame: () => clone(game), writeGame: s => write('game', s),
    apply(s, effect) { if (effect.id === 'activity') s.xp += 20; else if (effect.id === 'quest') { s.xp += 5; s.quests++; } return s; } });
  const ports = { readDirector: () => clone(director), writeDirector: s => write('director', s),
    gameEpoch: () => game.timerAuthority.epoch, ...effects,
    plan(s, r) { planned++; s.history.push(r.id); return { director: s, effects: [{ id: 'activity' }, { id: 'quest' }] }; } };
  let receive = createReceiver(ports);
  return { receive: r => receive(r), restart() { receive = createReceiver(ports); },
    fault(n, a) { failAt = n; after = a; }, heal() { failAt = 0; },
    get director() { return clone(director); }, get game() { return clone(game); }, get planned() { return planned; },
    rotate() { director.timerAuthority = { ...authority(), epoch: 'new' }; game.timerAuthority = { ...authority(), epoch: 'new' }; },
    ports };
}
let passed = 0;
function test(name, body) { body(); passed++; console.log('PASS ' + name); }

test('normal completion, replay and next completion award each effect once', () => {
  const h = harness(); assert.equal(h.receive(request(2)).status, 'COMMITTED');
  assert.equal(h.receive(request(2)).status, 'COMMITTED');
  assert.equal(h.game.xp, 25); assert.equal(h.game.quests, 1); assert.equal(h.planned, 1);
  assert.equal(h.receive(request(4)).status, 'COMMITTED');
  assert.equal(h.game.xp, 50); assert.equal(h.game.quests, 2); assert.equal(h.director.history.length, 2);
});
// Prepare Director, activity reward, quest reward, finalize host, finalize Director.
for (let stage = 1; stage <= 5; stage++) for (const after of [false, true]) {
  test('restart after interruption ' + (after ? 'after' : 'before') + ' durable write ' + stage, () => {
    const h = harness(); h.fault(stage, after);
    assert.equal(h.receive(request(2)).status, 'UNKNOWN');
    h.heal(); h.restart();
    assert.equal(h.receive(request(2)).status, 'COMMITTED');
    assert.equal(h.game.xp, 25); assert.equal(h.game.quests, 1);
    assert.deepEqual(h.director.history, ['epoch/2']);
    assert.equal(h.director.timerAuthority.pending, null);
    assert.equal(h.receive(request(2)).status, 'COMMITTED');
    assert.equal(h.game.xp, 25);
  });
}
test('same ID with changed payload is rejected while pending and after completion', () => {
  const h = harness(); h.fault(2, false); h.receive(request(2)); h.heal();
  const changed = { ...request(2), minutes: 20 };
  assert.equal(h.receive(changed).status, 'ID_CONFLICT');
  assert.equal(h.receive(request(2)).status, 'COMMITTED');
  assert.equal(h.receive(changed).status, 'ID_CONFLICT'); assert.equal(h.game.xp, 25);
});
test('in-flight completion blocks newer deliveries', () => {
  const h = harness(); h.fault(2, false); h.receive(request(2)); h.heal();
  assert.equal(h.receive(request(4)).status, 'BUSY'); assert.equal(h.game.xp, 0);
  assert.equal(h.receive(request(2)).status, 'COMMITTED');
  assert.equal(h.receive(request(4)).status, 'COMMITTED');
});
test('epoch rotation rejects queued old completions after restore', () => {
  const h = harness(); h.fault(3, false); h.receive(request(2)); h.heal(); h.rotate();
  const before = h.game.xp;
  assert.equal(h.receive(request(2)).status, 'EPOCH_MISMATCH'); assert.equal(h.game.xp, before);
});
test('evicted receipt is expired, never a new award', () => {
  const h = harness(); for (let i = 1; i <= 70; i++) assert.equal(h.receive(request(i)).status, 'COMMITTED');
  assert.equal(h.receive(request(1)).status, 'EXPIRED'); assert.equal(h.game.xp, 70 * 25);
});
test('unexpected reward ACK cannot finalize Director', () => {
  const h = harness(); const real = h.ports.commitEffect;
  h.ports.commitEffect = (...args) => ({ ...real(...args), id: 'other' });
  assert.equal(h.receive(request(2)).status, 'UNKNOWN');
  assert.notEqual(h.director.timerAuthority.pending, null);
  h.ports.commitEffect = real; assert.equal(h.receive(request(2)).status, 'COMMITTED'); assert.equal(h.game.xp, 25);
});
test('invalid payload has no effects', () => {
  const h = harness();
  for (const patch of [{ minutes: NaN }, { minutes: -1 }, { sequence: 0 }, { id: 'other' }, { epoch: '' }, { activityId: '' }]) {
    assert.equal(h.receive({ ...request(2), ...patch }).status, 'INVALID');
  }
  assert.equal(h.planned, 0); assert.equal(h.game.xp, 0);
});
test('canonical identity ignores object property order', () => {
  assert.equal(canonical(request(2)), canonical(Object.fromEntries(Object.entries(request(2)).reverse())));
});
console.log(passed + ' completion delivery cases passed');
