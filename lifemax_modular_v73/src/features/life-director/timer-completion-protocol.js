'use strict';

/* Staged completion protocol. Deliberately not registered on the production bridge yet.
 * Ports must read durable snapshots, commit whole snapshots atomically, and exclude
 * other writers for the duration of this synchronous call. plan/apply are pure.
 * Native delivery is FIFO; (epoch, sequence) identity is never reused after restore. */
const clone = x => JSON.parse(JSON.stringify(x));
const LIMIT = 64;
function canonical(request) {
  if (!request || typeof request.epoch !== 'string' || !request.epoch || request.epoch.length > 128 ||
      !Number.isSafeInteger(request.sequence) || request.sequence < 1 ||
      request.id !== request.epoch + '/' + request.sequence ||
      typeof request.activityId !== 'string' || !request.activityId || request.activityId.length > 512 ||
      !Number.isSafeInteger(request.generation) || request.generation < 1 ||
      !Number.isSafeInteger(request.actualStartEpoch) || request.actualStartEpoch < 0 ||
      !Number.isSafeInteger(request.minutes) || request.minutes < 1) throw Error('Invalid completion');
  return JSON.stringify([request.epoch, request.sequence, request.id, request.activityId,
    request.generation, request.actualStartEpoch, request.minutes]);
}
function meta(state, epoch) {
  const m = state && state.timerAuthority;
  if (!m || m.owner !== 'NATIVE' || m.epoch !== epoch) return null;
  if (!Number.isSafeInteger(m.highWater) || m.highWater < 0 || !Array.isArray(m.receipts)) throw Error('Invalid authority metadata');
  return m;
}
function createReceiver(ports) {
  let receiving = false;
  return function receive(request) {
    const reply = status => ({ status, id: request && request.id, epoch: request && request.epoch });
    let identity;
    try { identity = canonical(request); } catch (_) { return reply('INVALID'); }
    if (receiving) return reply('BUSY');
    receiving = true;
    try {
      let state = ports.readDirector();
      let m = meta(state, request.epoch);
      if (!m || ports.gameEpoch() !== request.epoch) return reply('EPOCH_MISMATCH');
      const prior = m.receipts.find(r => r.id === request.id);
      if (prior) return prior.identity === identity ? Object.assign(reply('COMMITTED'), { identity }) : reply('ID_CONFLICT');
      if (request.sequence <= m.highWater) return reply('EXPIRED');
      if (m.pending && m.pending.id !== request.id) return reply('BUSY');
      if (m.pending && m.pending.identity !== identity) return reply('ID_CONFLICT');
      if (!m.pending) {
        // Pure planning resolves all domain math once, before any reward can be paid.
        const planned = ports.plan(clone(state), clone(request));
        if (!planned || !planned.director || !Array.isArray(planned.effects) || planned.effects.length > 64) return reply('REJECTED');
        if (new Set(planned.effects.map(e => e.id)).size !== planned.effects.length ||
            planned.effects.some(e => !e.id || typeof e.id !== 'string')) return reply('REJECTED');
        state = clone(planned.director);
        state.timerAuthority = Object.assign({}, m, { pending: {
          id: request.id, identity, sequence: request.sequence,
          effects: clone(planned.effects).map(e => Object.assign({}, e, { sequence: request.sequence }))
        } });
        if (ports.writeDirector(state) !== true) return reply('UNKNOWN');
      }
      // Always reread: write completion is not evidence of the receiver's current epoch.
      state = ports.readDirector(); m = meta(state, request.epoch);
      if (!m || ports.gameEpoch() !== request.epoch) return reply('EPOCH_MISMATCH');
      if (!m.pending || m.pending.identity !== identity) return reply('UNKNOWN');
      for (const effect of m.pending.effects) {
        const result = ports.commitEffect(request.epoch, request.id, identity, clone(effect));
        if (!result || result.status !== 'COMMITTED' || result.id !== request.id ||
            result.identity !== identity || result.epoch !== request.epoch || result.effectId !== effect.id) return reply('UNKNOWN');
      }
      const hostReceipt = ports.finalizeEffects(request.epoch, request.id, identity, request.sequence, m.pending.effects.map(e => e.id));
      if (!hostReceipt || hostReceipt.status !== 'COMMITTED' || hostReceipt.epoch !== request.epoch ||
          hostReceipt.id !== request.id || hostReceipt.identity !== identity) return reply('UNKNOWN');
      const current = ports.readDirector(), finalMeta = meta(current, request.epoch);
      if (!finalMeta || ports.gameEpoch() !== request.epoch) return reply('EPOCH_MISMATCH');
      if (!finalMeta.pending || finalMeta.pending.identity !== identity) return reply('UNKNOWN');
      const final = clone(current);
      final.timerAuthority = Object.assign({}, finalMeta, {
        pending: null, highWater: request.sequence,
        receipts: finalMeta.receipts.concat({ id: request.id, identity }).slice(-LIMIT)
      });
      if (ports.writeDirector(final) !== true) return reply('UNKNOWN');
      return Object.assign(reply('COMMITTED'), { identity });
    } catch (_) {
      // Includes an exception after a durable write: keep the exact ID and reconcile.
      return reply('UNKNOWN');
    } finally { receiving = false; }
  };
}

/* Host-side adapter: effect receipt and effects MUST share the same game-save write.
 * apply cannot call save(), emit UI effects, or mutate the live game object. The adapter
 * is intentionally unusable with the current impure awardActivity/completeQuest helpers. */
function createEffectCommitter(ports) {
  function commitEffect(epoch, id, identity, effect) {
    const response = status => ({ status, epoch, id, identity, effectId: effect.id });
    const game = ports.readGame(), m = meta(game, epoch);
    if (!m) return response('EPOCH_MISMATCH');
    if (!Number.isSafeInteger(effect.sequence) || effect.sequence < 1 || id !== epoch + '/' + effect.sequence) return response('INVALID');
    // All effects of a completion share its operation sequence; effects arrive in order.
    const key = JSON.stringify([id, effect.id]);
    const body = JSON.stringify(effect);
    const prior = m.receipts.find(r => r.key === key);
    if (prior) return response(prior.identity === identity && prior.body === body ? 'COMMITTED' : 'ID_CONFLICT');
    if (effect.sequence <= m.highWater) return response('EXPIRED');
    if (m.pending && (m.pending.id !== id || m.pending.identity !== identity)) return response('BUSY');
    if (m.pending && m.pending.sequence !== effect.sequence) return response('ID_CONFLICT');
    const next = ports.apply(clone(game), clone(effect));
    if (!next) return response('REJECTED');
    // Up to 64 effects per in-flight completion; keep all its receipts until the next starts.
    const receipts = m.pending ? m.receipts : [];
    if (receipts.length >= LIMIT) return response('FULL');
    next.timerAuthority = Object.assign({}, m, {
      pending: { id, identity, sequence: effect.sequence },
      receipts: receipts.concat({ key, identity, body })
    });
    if (ports.writeGame(next) !== true) return response('UNKNOWN');
    return response('COMMITTED');
  }
  function finalizeEffects(epoch, id, identity, sequence, effectIds) {
    const response = status => ({ status, epoch, id, identity });
    const game = ports.readGame(), m = meta(game, epoch);
    if (!m) return response('EPOCH_MISMATCH');
    if (!Number.isSafeInteger(sequence) || sequence < 1 || id !== epoch + '/' + sequence) return response('INVALID');
    if (m.completed && m.completed.id === id) return response(m.completed.identity === identity ? 'COMMITTED' : 'ID_CONFLICT');
    if (sequence <= m.highWater) return response('EXPIRED');
    if (m.pending && (m.pending.id !== id || m.pending.identity !== identity || m.pending.sequence !== sequence)) return response('BUSY');
    if (effectIds.some(effectId => !m.receipts.some(r => r.key === JSON.stringify([id, effectId]) && r.identity === identity))) return response('INCOMPLETE');
    const next = clone(game);
    next.timerAuthority = Object.assign({}, m, { pending: null, highWater: sequence, completed: { id, identity } });
    if (ports.writeGame(next) !== true) return response('UNKNOWN');
    return response('COMMITTED');
  }
  return { commitEffect, finalizeEffects };
}

module.exports = { createReceiver, createEffectCommitter, canonical };
