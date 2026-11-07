'use strict';

/* Pure preparation for the staged receiver. No save(), DOM, toast, or host mutation.
 * context.quests is a snapshot from hostQuests; residualInput supplies buildInput-like
 * data for the copied candidate. Caller must hold the cross-store writer exclusion. */
function planTimerCompletion(engine, state, request, context) {
  const clone = x => JSON.parse(JSON.stringify(x));
  const next = clone(state), now = context.nowEpoch;
  if (!Number.isSafeInteger(now) || now < 1 || !next.schedule || !next.ledger) return null;
  const inst = next.schedule.scheduledInstances.find(i => i.instanceId === request.activityId);
  if (!inst || inst.lifecycleState !== 'PLANNED') return null;
  const eventId = engine.completionEventId(next.playerId, inst.instanceId, null, inst.revision);
  // Include archived IDs: an old completion must never become a new award after compaction.
  if (engine.ledgerHas(next.ledger, eventId)) return null;
  const result = engine.recordCompletion({ ledger: next.ledger, playerId: next.playerId, instance: inst,
    actualStartEpoch: Math.max(request.actualStartEpoch, inst.startEpoch - 12 * 3600),
    actualDurationMinutes: request.minutes, nowEpoch: now,
    satisfiedRatio: engine.normalizePreferences(next.prefs).partialSatisfiedRatio });
  if (!result.applied) return null;
  next.ledger = result.ledger;
  const remaining = next.schedule.scheduledInstances.filter(i => i.instanceId !== inst.instanceId);
  const quests = clone(context.quests || []);
  const rq = engine.reconcileQuestProgress(result.event, quests, next.goals, remaining, { dayKey: context.dayKey });
  next.goals = rq.updatedGoals;
  next.questOverlay = next.questOverlay || {};
  const effects = [{ id: 'activity', kind: 'activity', event: result.event, title: inst.title }];
  for (const delta of rq.questDeltas) {
    const quest = quests.find(q => q.id === delta.questId);
    if (!quest || !quest.hostRef) continue;
    const overlay = next.questOverlay[quest.hostRef.key] = next.questOverlay[quest.hostRef.key] || { eventIds: [] };
    if (!overlay.eventIds.includes(result.eventId)) overlay.eventIds.push(result.eventId);
    const normalized = engine.normalizeQuest(rq.updatedQuests.find(q => q.id === delta.questId));
    const met = normalized.requirements.every(r => (!r.targetCount || r.currentCount >= r.targetCount) && (!r.targetMinutes || r.currentMinutes >= r.targetMinutes));
    if (met && quest.hostRef.questId && !quest.hostRef.future) effects.push({ id: 'quest:' + quest.hostRef.key, kind: 'quest', hostRef: clone(quest.hostRef) });
  }
  next.schedule.scheduledInstances = rq.prunedScheduleInstances.filter(i => !rq.removedInstanceIds.includes(i.instanceId));
  next.schedule.historyInstances = (next.schedule.historyInstances || []).concat(Object.assign({}, inst, {
    lifecycleState: result.lifecycleState, completionEventId: result.eventId,
    hostActivityId: 'ld_' + result.eventId, actualMinutes: result.event.actualDurationMinutes, resolvedAtEpoch: now
  }));
  if (result.residualTask) {
    if (typeof context.residualInput !== 'function') return null;
    const repair = engine.rescheduleActivity({ engineInput: context.residualInput(clone(next)), currentOutput: next.schedule,
      trigger: { type: 'RESIDUAL', task: result.residualTask, parentInstanceId: inst.instanceId } });
    if (!repair || repair.rejectedMove || !repair.updatedSchedule || repair.updatedSchedule.status === 'INVARIANT_VIOLATION') return null;
    next.schedule = repair.updatedSchedule;
    next.fixedCommitments = repair.engineInput.fixedCommitments || [];
    next.availability.blackouts = (repair.engineInput.playerAvailability && repair.engineInput.playerAvailability.blackouts) || next.availability.blackouts;
    next.changeLog = (next.changeLog || []).concat({ at: now, label: repair.changeRecord.triggerReason, churn: repair.changeRecord.churn, actions: repair.changeRecord.actions.length });
  }
  if (next.ui && next.ui.focus && next.ui.focus.id === request.activityId && next.ui.focus.start === request.generation) next.ui.focus = null;
  next.updatedEpoch = now;
  return { director: next, effects };
}
module.exports = { planTimerCompletion };
