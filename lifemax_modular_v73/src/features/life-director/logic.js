
/* ── Life Director · interface controller (1.4 · chrono) ─────────────────────
   Today is a 24-hour dial: outer ring = time you told it about, inner ring = what it planned.
   Everything below render() that touches the plan (persistence, sync, misses, completion,
   re-planning) is unchanged from 1.3; only presentation and a few inputs are new. */
const LifeDirectorApp = (function () {
  'use strict';
  const E = LifeDirectorEngine, Host = LifeMaxHost, U = E.util;
  const FEATURE_ID = 'life-director';
  let root = null, store = null, ST = null, loadReport = null, busy = false, timer = null;
  const ui = { tab: 'today', dayIdx: 0, banner: null, notice: null, animate: false, sheet: null, prevPlan: null,
    fx: { prevTab: null, prevDay: null, nextId: null, justDone: null, glide: {}, daysScroll: null, take: null }, lab: { scenario: 'office', seed: 1, days: 7, result: null, dayIdx: 0, extra: null, all: null } };
  const nowEpoch = () => Math.floor(Date.now() / 1000);
  const deviceTz = () => { try { return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'; } catch (e) { return 'UTC'; } };
  const tz = () => (ST && ST.availability.timezone) || deviceTz();
  const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const icon = (n, s) => Host.icon(n, s);
  const uuid = () => (globalThis.crypto && crypto.randomUUID) ? crypto.randomUUID() : 'c' + Date.now().toString(36) + Math.random().toString(36).slice(2);
  const DOW = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  const fmtCache = {};
  function fmt(opts, z) { const k = JSON.stringify(opts) + (z || tz()); return fmtCache[k] || (fmtCache[k] = new Intl.DateTimeFormat(undefined, Object.assign({ timeZone: z || tz() }, opts))); }
  const fTime = (ep, z) => fmt({ hour: 'numeric', minute: '2-digit' }, z).format(new Date(ep * 1000));
  const fDay = (ep, z) => fmt({ weekday: 'short', month: 'short', day: 'numeric' }, z).format(new Date(ep * 1000));
  const fDate = (ep, z) => fmt({ weekday: 'long', month: 'long', day: 'numeric' }, z).format(new Date(ep * 1000));
  const mins = m => m >= 60 ? Math.floor(m / 60) + 'h' + (m % 60 ? ' ' + (m % 60) + 'm' : '') : m + 'm';
  const hm = m => String(Math.floor(m / 60) % 24).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0');
  const parseHm = v => { const m = /^(\d{1,2}):(\d{2})$/.exec(v || ''); return m ? (+m[1]) * 60 + (+m[2]) : null; };
  /* the last message is also kept, so a native screen over the page can show it */
  function say(msg) { ui.notice = { id: (ui.notice ? ui.notice.id : 0) + 1, text: msg, at: Date.now() }; changed(); if (!Host.toast(msg)) { const t = root && root.querySelector('.ld-toast'); if (t) { const again = t.classList.contains('show'); t.textContent = msg; t.classList.add('show'); if (again) play(t, 'ld-bump'); clearTimeout(t._h); t._h = setTimeout(() => t.classList.remove('show'), 2600); } } }

  /* ── motion helpers (presentation only; every one degrades to an instant change) ── */
  const reduced = () => { try { return matchMedia('(prefers-reduced-motion: reduce)').matches; } catch (e) { return false; } };
  /* restart a CSS animation class on an element */
  function play(el, cls, vars) {
    if (!el || reduced()) return;
    el.classList.remove(cls);
    if (vars) for (const k in vars) el.style.setProperty(k, vars[k]);
    void el.offsetWidth;
    el.classList.add(cls);
    const off = ev => { if (ev.target !== el) return; el.classList.remove(cls); el.removeEventListener('animationend', off); };
    el.addEventListener('animationend', off);
  }
  /* collapse an element out of the layout, then run the real action */
  function leave(el, then) {
    if (!el || reduced() || el.classList.contains('ld-leaving')) return false;
    el.style.height = el.offsetHeight + 'px'; void el.offsetHeight;
    el.classList.add('ld-leaving');
    let done = false; const go = () => { if (!done) { done = true; then(); } };
    el.addEventListener('transitionend', ev => { if (ev.target === el && ev.propertyName === 'height') go(); });
    setTimeout(go, 320);
    return true;
  }
  /* what changed since the last plan, so the timeline can show it */
  function snapshotPlan(sched) {
    const byId = new Map(), seen = new Set();
    const list = [];
    for (const i of sched.scheduledInstances || []) { if (i.lifecycleState !== 'PLANNED') continue; byId.set(i.instanceId, i.startEpoch); seen.add(i.title + '|' + i.startEpoch); list.push({ id: i.instanceId, s: i.startEpoch, e: i.endEpoch, title: i.title }); }
    return { byId, seen, list };
  }
  function placeFx(b, lab) {
    if (!ui.animate || lab) return { cls: '', tag: '' };
    if (ui.animate === 'intro' || !ui.prevPlan) return { cls: ' ld-fx ld-arrive', tag: '' };
    if (!b.inst || b.inst.lifecycleState !== 'PLANNED') return { cls: ' ld-fx ld-pass', tag: '' };
    const P = ui.prevPlan, was = P.byId.get(b.inst.instanceId);
    if (was === b.inst.startEpoch || P.seen.has(b.inst.title + '|' + b.inst.startEpoch)) return { cls: ' ld-fx ld-pass', tag: '' };
    if (was != null) return { cls: ' ld-fx ld-moved', tag: 'Moved', dy: was < b.inst.startEpoch ? -1 : 1 };
    return { cls: ' ld-fx ld-arrive ld-new', tag: 'Added' };
  }
  /* ── persistence ── */
  let lastSave = { ok: true }; // the last write's outcome, which a native command reports
  function persist() {
    if (ST.schedule) {
      const cut = nowEpoch() - 21 * 86400; // keep 3 weeks of history inside the schedule; the ledger keeps everything
      ST.schedule.historyInstances = (ST.schedule.historyInstances || []).filter(h => h.endEpoch > cut);
    }
    ST.changeLog = (ST.changeLog || []).slice(-60);
    ST.updatedEpoch = nowEpoch();
    const r = store.save(ST);
    lastSave = r;
    if (!r.ok) ui.banner = { kind: 'bad', text: 'Not saved: ' + r.error };
    else if (ui.banner && ui.banner.kind === 'bad' && /^Not saved/.test(ui.banner.text)) ui.banner = null;
    return r.ok;
  }
  function boot() {
    let storage = null; try { storage = window.localStorage; } catch (e) { storage = null; }
    store = LifeDirectorStore.create(storage || { getItem() { throw new Error('no storage'); }, setItem() { throw new Error('no storage'); }, removeItem() { } });
    const res = store.load({ playerId: Host.playerId(), timezone: deviceTz(), nowEpoch: nowEpoch() });
    ST = res.state; loadReport = res.report;
    if (!ST.ledger) ST.ledger = E.emptyLedger(ST.playerId);
    ST.questOverlay = ST.questOverlay || {};
    ST.hostSync = ST.hostSync || { cursor: null, seen: {} };
    if (repairEarlyDone()) persist();
    if (res.report.source === 'MEMORY_ONLY') ui.banner = { kind: 'bad', text: 'This browser blocks storage here, so Life Director cannot save. Changes last until you close the app.' };
    else if (res.report.recovered) ui.banner = { kind: 'warn', text: 'Your last save was unreadable, so Life Director restored the previous good copy. The damaged copy was kept as ' + (res.report.quarantined || 'a backup') + '.' };
    else if (res.report.migratedFrom != null) ui.banner = { kind: 'warn', text: 'Life Director data upgraded from version ' + res.report.migratedFrom + '.' };
    if (!Host.connected() && ST.ui.lab === undefined) ST.ui.lab = true; // standalone harness: expose the Lab
    if (!ST.setupComplete) ui.tab = 'setup';
    else ui.tab = ST.ui && ST.ui.tab || 'today';
  }

  /* ── engine input ── */
  function hostQuests(now) {
    const days = E.normalizePreferences(ST.prefs).planningHorizonDays;
    return Host.readQuests(now, tz(), ST.questOverlay, ST.ledger.revoked).concat(Host.readPlanSessions(now, tz(), days));
  }
  function completionHistory() {
    const rev = ST.ledger.revoked || {};
    return ST.ledger.order.map(id => ST.ledger.events[id]).filter(e => e && !rev[e.eventId] && e.completionRatio >= 0.75)
      .map(e => ({ templateId: e.templateId, spacingKey: e.spacingKey, startEpoch: e.actualStartEpoch }));
  }
  function buildInput(now) {
    const s = ST.schedule;
    return {
      timezone: tz(), currentTimeEpoch: now, horizonStartEpoch: now, horizonDays: E.normalizePreferences(ST.prefs).planningHorizonDays,
      playerAvailability: Object.assign({}, ST.availability, { timezone: tz() }), fixedCommitments: ST.fixedCommitments || [],
      goals: ST.goals, activeQuests: hostQuests(now), existingSchedule: s ? s.scheduledInstances.concat(s.historyInstances || []) : [],
      completionHistory: completionHistory(), playerPreferences: ST.prefs, energyProfile: { checkin: ST.energyCheckin }
    };
  }
  function applyOutput(out, label, mode) {
    if (out.status === 'INVARIANT_VIOLATION') {
      ui.banner = { kind: 'bad', text: 'Life Director refused to publish a plan that broke a safety rule (' + out.validation.violatedInvariants.map(v => v.code).join(', ') + '). Your previous plan is kept. Please report this from Settings → Diagnostics.' };
      ST.lastRejected = { at: nowEpoch(), violations: out.validation.violatedInvariants.slice(0, 10) };
      return false;
    }
    ui.prevPlan = ST.schedule ? snapshotPlan(ST.schedule) : null;
    queueBuild(mode || 'build', ui.prevPlan);
    ST.schedule = out; ui.animate = true;
    if (label) ST.changeLog.push({ at: nowEpoch(), label });
    return true;
  }
  function regenerate(reason, quiet) {
    const now = nowEpoch();
    const out = E.generateSchedule(buildInput(now));
    const ok = applyOutput(out, 'Re-planned (' + reason + ')');
    if (ok) ST.lastGeneratedWeekKey = weekKey(now);
    persist();
    if (!quiet && ok) say('Plan updated · ' + out.scheduledInstances.filter(i => i.lifecycleState === 'PLANNED').length + ' activities');
    return ok;
  }
  /* changes to your time rebuild the plan straight away, once setup is done */
  function autoReplan(reason) { if (ST.setupComplete) regenerate(reason, true); else persist(); }
  /* Local repair holds compatible sessions and protects recovery. */
  function planTimeChange(input) {
    return E.rescheduleActivity({ engineInput: input, currentOutput: ST.schedule,
      trigger: { type: 'TIME_CHANGED' } }).updatedSchedule;
  }
  function weekKey(now) { const p = U.localParts(now, tz()); const mon = U.ymdAdd({ y: p.y, mo: p.mo, d: p.d }, -((p.dow + 6) % 7)); return U.ymdKey(mon); }
  function reschedule(trigger) {
    const now = nowEpoch();
    const r = E.rescheduleActivity({ engineInput: buildInput(now), currentOutput: ST.schedule, trigger });
    if (r.rejectedMove) { say(r.rejectedMove.message); return null; }
    if (!applyOutput(r.updatedSchedule, r.changeRecord.triggerReason, 'repair')) { persist(); return null; }
    ST.fixedCommitments = r.engineInput.fixedCommitments || [];
    ST.availability.blackouts = (r.engineInput.playerAvailability && r.engineInput.playerAvailability.blackouts) || ST.availability.blackouts;
    ST.changeLog.push({ at: now, label: r.changeRecord.triggerReason, churn: r.changeRecord.churn, actions: r.changeRecord.actions.length });
    return r;
  }
  function summarize(r) {
    if (!r) return '';
    const a = r.changeRecord.actions, n = t => a.filter(x => x.action === t).length;
    const parts = [];
    if (n('EXCUSE')) parts.push(n('EXCUSE') + ' excused (no penalty)');
    if (n('INSERT')) parts.push(n('INSERT') + ' re-placed');
    if (n('SHED')) parts.push(n('SHED') + ' dropped for this week');
    if (n('COMPRESS')) parts.push(n('COMPRESS') + ' shortened');
    return parts.join(' · ') || 'Nothing else needed to move';
  }

  /* ── sync with the rest of LifeMax (completions from other pages, undo in Log, quest check-offs) ── */
  function syncHost() {
    if (!Host.connected() || !ST.schedule) return false;
    let changed = false;
    const now = nowEpoch(), sync = ST.hostSync;
    /* 1. activities logged elsewhere → mark matching planned block done (no XP: LifeMax already paid) or record ad-hoc */
    for (const a of Host.activitiesSince(sync.cursor)) {
      if (sync.seen[a.id]) continue;
      sync.seen[a.id] = 1; changed = true;
      const ep = Math.floor(Date.parse(a.iso) / 1000);
      const sameDay = i => U.ymdKey(U.localParts(i.startEpoch, tz())) === U.ymdKey(U.localParts(ep, tz()));
      const match = ST.schedule.scheduledInstances.find(i => i.lifecycleState === 'PLANNED' && sameDay(i) && (E.tagMatches(a.tag, i.tag) || E.tagMatches(i.tag, a.tag) || (a.planDate && i.linkedQuestRequirementIds.some(l => l.questId === 'plan:' + a.planDate))));
      if (match) finishInstance(match, a.minutes || match.durationSlots * 15, { hostActivityId: a.id, awardXp: false, startEpoch: ep, silent: true });
      else {
        const r = E.recordCompletion({ ledger: ST.ledger, playerId: ST.playerId, adhoc: { tag: a.tag, title: a.name }, clientEventId: 'host:' + a.id, actualStartEpoch: ep, actualDurationMinutes: Math.max(1, a.minutes || 15), nowEpoch: now });
        if (r.applied) { ST.ledger = r.ledger; afterEvent(r.event, null); }
      }
    }
    const all = Host.activitiesSince(null); if (all.length) sync.cursor = all.reduce((m, a) => a.iso > m ? a.iso : m, sync.cursor || '');
    /* 2. Life Director activities undone in the Log page → revoke the event, put the block back if still upcoming */
    for (const h of ST.schedule.historyInstances || []) {
      if (!h.hostActivityId || !h.completionEventId || (ST.ledger.revoked || {})[h.completionEventId]) continue;
      if (Host.activityExists(h.hostActivityId)) continue;
      const rv = E.revokeEvent(ST.ledger, h.completionEventId, 'UNDONE_IN_LIFEMAX_LOG', now);
      if (!rv.revoked) continue;
      ST.ledger = rv.ledger; changed = true;
      h.undone = true;
      if (h.endEpoch > now) {
        ST.schedule.historyInstances = ST.schedule.historyInstances.filter(x => x !== h);
        ST.schedule.scheduledInstances.push(Object.assign({}, h, { lifecycleState: 'PLANNED', revision: (h.revision || 0) + 1, completionEventId: null, hostActivityId: null, undone: false }));
      }
      say('“' + h.title + '” was undone in your log; Life Director updated its records.');
    }
    /* 3. quests checked off by hand → drop their now-unneeded future blocks */
    const qs = hostQuests(now), done = new Set(qs.filter(q => q.status === 'COMPLETED').map(q => q.id).concat(Host.milestones().map(m => m.id)));
    // Retire only automatic, unstarted blocks that consist entirely of untimed cards.
    // Pinned blocks, mixed goals, history and the active timer are preserved.
    const untimed = new Map(qs.filter(q => q.schedulingMode === 'NONE').map(q => [q.id, q]));
    // Old schedule links can outlive the daily hand that created them.
    if (Host.retiredUntimedQuest) for (const inst of ST.schedule.scheduledInstances) {
      for (const ref of inst.linkedQuestRequirementIds || []) {
        if (untimed.has(ref.questId)) continue;
        const q = Host.retiredUntimedQuest(Object.assign({}, ref, { questTitle: ref.questTitle || inst.title }));
        if (q) untimed.set(q.id, q);
      }
    }
    const retired = ST.schedule.scheduledInstances.filter(i =>
      i.lifecycleState === 'PLANNED' && !i.isUserLocked &&
      !(ST.ui.focus && ST.ui.focus.id === i.instanceId) &&
      !(i.linkedGoalIds || []).length &&
      (i.linkedQuestRequirementIds || []).length &&
      i.linkedQuestRequirementIds.every(ref => untimed.has(ref.questId)));
    if (retired.length) {
      const ids = new Set(retired.map(i => i.instanceId));
      ST.schedule.scheduledInstances = ST.schedule.scheduledInstances.filter(i => !ids.has(i.instanceId));
      const reminders = ST.schedule.passiveReminders || (ST.schedule.passiveReminders = []);
      for (const inst of retired) for (const ref of inst.linkedQuestRequirementIds) {
        const q = untimed.get(ref.questId);
        if (!reminders.some(r => r.questId === q.id)) reminders.push({ questId: q.id, title: q.title, anchor: q.anchor || 'WAKE' });
      }
      changed = true;
    }
    for (const i of ST.schedule.scheduledInstances.slice()) {
      const links = i.linkedQuestRequirementIds.filter(l => !done.has(l.questId));
      if (links.length === i.linkedQuestRequirementIds.length || i.lifecycleState !== 'PLANNED') continue;
      changed = true;
      if (!links.length && !i.linkedGoalIds.length && !i.isUserLocked) ST.schedule.scheduledInstances = ST.schedule.scheduledInstances.filter(x => x !== i);
      else { i.linkedQuestRequirementIds = links; if (!links.length && !i.linkedGoalIds.length) i.orphanWarning = true; }
    }
    return changed;
  }
  /* missed blocks: bounded consequences + local repair (never a regenerate of the whole week) */
  function processMisses() {
    if (!ST.schedule) return false;
    const prefs = E.normalizePreferences(ST.prefs);
    const misses = E.detectMisses(ST.schedule.scheduledInstances, nowEpoch(), prefs.missGraceMinutes);
    let n = 0, regroup = false;
    for (const miss of misses.slice(0, 12)) {
      /* each repair replaces ST.schedule, so look the block up again; skip it if it already left the plan */
      const inst = ST.schedule.scheduledInstances.find(x => x.instanceId === miss.instanceId && x.lifecycleState === 'PLANNED');
      if (!inst) continue;
      let r = null;
      try { r = reschedule({ type: 'MISSED', instanceId: inst.instanceId }); }
      catch (err) {
        /* one bad block must not stall the rest: record it as missed in place and move on */
        console.warn('[Life Director] miss repair failed', inst.instanceId, err);
        ST.schedule.scheduledInstances = ST.schedule.scheduledInstances.filter(x => x !== inst);
        (ST.schedule.historyInstances = ST.schedule.historyInstances || []).push(Object.assign({}, inst, { lifecycleState: 'MISSED_UNEXPLAINED', resolvedAtEpoch: nowEpoch() }));
        r = true;
      }
      const c = E.applyMissConsequence(ST.ledger, inst, ST.prefs, nowEpoch(), 'MISS');
      ST.ledger = c.ledger; if (c.regroupPrompt) regroup = true;
      if (r) { const h = ST.schedule.historyInstances.find(x => x.instanceId === inst.instanceId); if (h) { h.lifecycleState = c.state; h.consequence = c.consequence; } }
      n++;
    }
    if (regroup) ui.banner = { kind: 'warn', regroup: true, text: 'Looks like life got hectic. Want to pause for 48 hours, switch to light mode for this week, or just carry on? Missed-task consequences are paused either way.' };
    if (n) persist();
    return n > 0;
  }
  function maybeRollover() {
    const now = nowEpoch();
    if (!ST.setupComplete) return false;
    if (ST.prefsLightUntil && now > ST.prefsLightUntil) { ST.prefs.intensityLevel = ST.prefsLightRestore || 'BALANCED'; ST.prefsLightUntil = null; }
    if (!ST.schedule || ST.lastGeneratedWeekKey !== weekKey(now) || ST.schedule.horizonEndEpoch - now < 2 * 86400 || ST.schedule.timezone !== tz()) return regenerate('weekly rollover', true);
    return false;
  }

  /* ── completion ── */
  const unsaved = a => a.reason === 'HOST_SAVE_FAILED' || a.reason === 'HOST_READ_ONLY';
  /* what a session's XP came to, in the words its notice uses */
  const XP_WORDS = { HOST_NOT_CONNECTED: 'recorded (XP needs LifeMax)', HOST_READ_ONLY: 'XP not saved: this version is read-only for your save', HOST_SAVE_FAILED: 'XP not saved (storage full?)' };
  const xpWords = a => a.awarded ? '+' + a.xp + ' XP' : XP_WORDS[a.reason] || '';
  function questsSnapshot() { return hostQuests(nowEpoch()); }
  /* a session whose XP could not be saved pays no quest bounty either: the save failed, so nothing it earned is kept */
  function afterEvent(event, inst, bounty = true) {
    const qs = questsSnapshot();
    const future = ST.schedule ? ST.schedule.scheduledInstances : [];
    const rq = E.reconcileQuestProgress(event, qs, ST.goals, future, { dayKey: U.ymdKey(U.localParts(event.actualStartEpoch, tz())) });
    ST.goals = rq.updatedGoals;
    const notes = [];
    for (const d of rq.questDeltas) {
      const q = qs.find(x => x.id === d.questId); if (!q || !q.hostRef) continue;
      const key = q.hostRef.key;
      const ov = ST.questOverlay[key] = ST.questOverlay[key] || { eventIds: [] };
      if (!ov.eventIds.includes(event.eventId)) ov.eventIds.push(event.eventId);
      const uq = rq.updatedQuests.find(x => x.id === d.questId), nq = E.normalizeQuest(uq);
      const met = nq.requirements.every(r => (!r.targetCount || r.currentCount >= r.targetCount) && (!r.targetMinutes || r.currentMinutes >= r.targetMinutes));
      if (met && q.hostRef.questId && bounty) { const c = Host.completeQuest(q.hostRef); if (c.completed) notes.push('Quest complete: ' + c.name + ' +' + c.xp + ' XP'); }
      else if (!met) notes.push(q.title + ' ' + nq.requirements[0].currentCount + '/' + nq.requirements[0].targetCount);
    }
    if (ST.schedule) {
      ST.schedule.scheduledInstances = rq.prunedScheduleInstances.filter(i => !rq.removedInstanceIds.includes(i.instanceId));
    }
    void inst;
    return notes;
  }
  let lastFinishRefusal = null; // why the last finishInstance was refused, for a native screen's answer
  function finishInstance(inst, minutes, opt) {
    opt = opt || {};
    const now = nowEpoch();
    const start = opt.startEpoch || Math.min(inst.startEpoch, now - minutes * 60) || now;
    const r = E.recordCompletion({ ledger: ST.ledger, playerId: ST.playerId, instance: inst, actualStartEpoch: Math.max(start, inst.startEpoch - 12 * 3600), actualDurationMinutes: minutes, nowEpoch: now,
      satisfiedRatio: E.normalizePreferences(ST.prefs).partialSatisfiedRatio });
    lastFinishRefusal = r.applied ? null : r.reason;
    // quietRefusal: a native screen answers a refusal itself (its reply), so the page says nothing then
    if (!r.applied) { if (!opt.silent && !opt.quietRefusal) say(r.reason === 'DUPLICATE_EVENT_ID' ? 'Already recorded.' : 'Cannot complete this block (' + r.reason + ').'); return null; }
    ST.ledger = r.ledger;
    let xpNote = '', hostActivityId = opt.hostActivityId || null, bounty = true;
    if (opt.awardXp !== false) {
      const a = Host.awardActivity(r.event, { title: inst.title });
      bounty = !unsaved(a); xpNote = xpWords(a);
      if (a.awarded) hostActivityId = a.activityId;
    }
    ST.schedule.scheduledInstances = ST.schedule.scheduledInstances.filter(i => i.instanceId !== inst.instanceId);
    ST.schedule.historyInstances = (ST.schedule.historyInstances || []).concat(Object.assign({}, inst, { lifecycleState: r.lifecycleState, completionEventId: r.eventId, hostActivityId,
      actualMinutes: r.event.actualDurationMinutes, resolvedAtEpoch: now }));
    const notes = afterEvent(r.event, inst, bounty);
    if (r.residualTask) { const rr = reschedule({ type: 'RESIDUAL', task: r.residualTask, parentInstanceId: inst.instanceId }); if (rr) notes.push('Rest of it re-planned'); }
    if (r.tokenEarned) notes.push('Rest token earned');
    persist();
    if (!opt.silent) say([inst.title + (r.lifecycleState === 'PARTIALLY_COMPLETED' ? ' partly done' : ' done'), xpNote].concat(notes).filter(Boolean).join(' · '));
    return r;
  }

  /* ── sessions can only be done "now-ish" ─────────────────────────────────────
     A session can be started or marked done from 6 hours before it (enough for
     days that cross midnight, like night shifts). Anything later stays planned for
     its own day; doing it early is logged for today instead (see 'logToday'). */
  const DOABLE_AHEAD = 6 * 3600;
  const doable = (inst, now) => {
    now = now || nowEpoch();
    if (inst.startEpoch - now <= DOABLE_AHEAD) return true;
    return U.ymdKey(U.localParts(inst.startEpoch, tz())) === U.ymdKey(U.localParts(now, tz()));
  };
  /* show completed work on the day it was actually done */
  function showAsDoneNow(entry, minutes, now) {
    const end = now, start = end - Math.max(15, minutes) * 60;
    return Object.assign(entry, { startEpoch: start, endEpoch: end });
  }
  /* one-time repair (1.4.1): sessions completed a day or more ahead of their plan
     go back to planned on their day; the work done shows when it was done. XP stays. */
  function repairEarlyDone() {
    if (ST.fixes && ST.fixes.earlyDone) return false;
    ST.fixes = Object.assign({}, ST.fixes, { earlyDone: 1 });
    const s = ST.schedule; if (!s) return true;
    const now = nowEpoch(), dateOf = ep => U.ymdKey(U.localParts(ep, tz()));
    let n = 0;
    for (const h of s.historyInstances || []) {
      if (!/COMPLETED/.test(h.lifecycleState) || h.adhoc || !h.resolvedAtEpoch) continue;
      if (h.startEpoch - h.resolvedAtEpoch <= DOABLE_AHEAD || dateOf(h.resolvedAtEpoch) >= dateOf(h.startEpoch)) continue;
      const plannedStart = h.startEpoch, plannedEnd = h.endEpoch;
      showAsDoneNow(h, h.actualMinutes || h.durationSlots * 15, h.resolvedAtEpoch);
      h.movedFromEpoch = plannedStart;
      if (plannedStart > now && !s.scheduledInstances.some(i => i.startEpoch < plannedEnd && i.endEpoch > plannedStart)) {
        s.scheduledInstances.push(Object.assign({}, h, { instanceId: h.instanceId + '-r', startEpoch: plannedStart, endEpoch: plannedEnd, lifecycleState: 'PLANNED', revision: (h.revision || 0) + 1,
          completionEventId: null, hostActivityId: null, actualMinutes: null, resolvedAtEpoch: null, movedFromEpoch: null, undone: false }));
      }
      n++;
    }
    if (n) ui.banner = { kind: 'warn', text: n + ' session' + (n === 1 ? ' was' : 's were') + ' marked done before ' + (n === 1 ? 'its' : 'their') + ' day. They’re back on the plan, and the work you did shows on the day you did it. Your XP is kept.' };
    return true;
  }
  /* did it today, though it was planned for a later day: count it now, keep the plan */
  /* op: the screen's operation id, so the same request again is the same ledger event (refused as a duplicate, which the caller
     answers with "Already recorded."); the page's own button mints a new one each time */
  function logToday(inst, minutes, op) {
    const now = nowEpoch();
    const r = E.recordCompletion({ ledger: ST.ledger, playerId: ST.playerId, adhoc: { tag: inst.tag, title: inst.title }, clientEventId: 'ld-today:' + (op || uuid()),
      actualStartEpoch: now - minutes * 60, actualDurationMinutes: minutes, nowEpoch: now });
    if (!r.applied) { if (op && r.reason === 'DUPLICATE_EVENT_ID') return { duplicate: true }; say('Could not log it (' + r.reason + ').'); return null; }
    ST.ledger = r.ledger;
    const a = Host.awardActivity(r.event, { title: inst.title });
    (ST.schedule.historyInstances = ST.schedule.historyInstances || []).push(showAsDoneNow({
      instanceId: 'adhoc-' + r.eventId, taskId: 'adhoc-' + r.eventId, adhoc: true, title: inst.title, tag: inst.tag, goalTitle: inst.goalTitle || null,
      linkedQuestRequirementIds: [], linkedGoalIds: [], durationSlots: Math.ceil(minutes / 15), lifecycleState: 'COMPLETED_ON_TIME',
      completionEventId: r.eventId, hostActivityId: a.activityId || null, actualMinutes: minutes, resolvedAtEpoch: now,
      explanation: { summary: 'Logged for today. The session planned for ' + fDay(inst.startEpoch) + ' is still on your plan.' } }, minutes, now));
    const notes = afterEvent(r.event, null, !unsaved(a));
    persist();
    say([inst.title + ' logged for today', xpWords(a)].concat(notes, 'still planned for ' + fDay(inst.startEpoch)).filter(Boolean).join(' · '));
    return r;
  }

  /* ── day model for the timeline ── */
  function horizonDays(sched, z) {
    const days = U.buildHorizon(sched.horizonStartEpoch, Math.round((sched.horizonEndEpoch - sched.horizonStartEpoch) / 86400), z);
    // A saved plan can span several dates. Its first date is not permanently "Today".
    // Slide only the live calendar; retain historical instances and fixed lab scenarios.
    if (!ST || sched !== ST.schedule) return days;
    const now = nowEpoch();
    const current = days.filter(day => day.endEpoch > now);
    return current.length ? current : U.buildHorizon(now, 1, z);
  }
  function dayBlocks(sched, day, z) {
    const s = day.startEpoch, e = day.endEpoch, out = [];
    for (const g of sched.segments || []) {
      if (g.endEpoch <= s || g.startEpoch >= e) continue;
      if (['FREE', 'SCHEDULED_TASK', 'USER_LOCKED_ACTIVITY'].includes(g.code)) continue;
      out.push({ startEpoch: Math.max(s, g.startEpoch), endEpoch: Math.min(e, g.endEpoch), code: g.code, kind: g.kind, title: g.title, refId: g.refId, mealStartEpoch: g.kind === 'MEAL' ? g.startEpoch : undefined, clipped: g.startEpoch < s || g.endEpoch > e });
    }
    const insts = (sched.scheduledInstances || []).concat(sched.historyInstances || []).filter(i => i.endEpoch > s && i.startEpoch < e && !['EXCUSED_DISPLACED', 'CANCELLED_BY_QUEST'].includes(i.lifecycleState));
    for (const i of insts) out.push({ startEpoch: i.startEpoch, endEpoch: i.endEpoch, code: 'TASK', kind: 'TASK', title: i.title, inst: i });
    out.sort((a, b) => a.startEpoch - b.startEpoch || (a.inst ? 1 : 0) - (b.inst ? 1 : 0));
    /* tasks win over stale downtime segments they now overlap (e.g. after a move) */
    const cleaned = [];
    for (const b of out) {
      if (!b.inst && out.some(o => o.inst && o.startEpoch < b.endEpoch && o.endEpoch > b.startEpoch && o.inst.lifecycleState === 'PLANNED')) {
        const ov = out.filter(o => o.inst && o.startEpoch < b.endEpoch && o.endEpoch > b.startEpoch);
        let cur = b.startEpoch;
        for (const o of ov.sort((x, y) => x.startEpoch - y.startEpoch)) { if (o.startEpoch > cur) cleaned.push(Object.assign({}, b, { startEpoch: cur, endEpoch: o.startEpoch })); cur = Math.max(cur, o.endEpoch); }
        if (cur < b.endEpoch) cleaned.push(Object.assign({}, b, { startEpoch: cur }));
      } else cleaned.push(b);
    }
    cleaned.sort((a, b) => a.startEpoch - b.startEpoch);
    const final = []; let cur = s;
    for (const b of cleaned) {
      if (b.startEpoch > cur && b.startEpoch - cur >= 900) final.push({ startEpoch: cur, endEpoch: b.startEpoch, code: 'FREE', kind: 'FREE', title: 'Free time' });
      if (b.endEpoch > cur || b.inst) final.push(b);
      cur = Math.max(cur, b.endEpoch);
    }
    if (e - cur >= 900) final.push({ startEpoch: cur, endEpoch: e, code: 'FREE', kind: 'FREE', title: 'Free time' });
    return final;
  }
  const KIND_CLASS = { SLEEP: 'sleep', WORK: 'work', CLASS: 'work', APPOINTMENT: 'work', COMMUTE: 'commute', DECOMPRESS: 'commute', MEAL: 'meal', ROUTINE: 'maint', WIND_DOWN: 'maint',
    RECOVERY: 'rest', FREE: 'free', TASK: 'task', BLACKOUT: 'block', SICK: 'block', EMERGENCY: 'block', UNAVAILABLE: 'block' };
  /* LifeMax's icon set has no food icon: a fork and knife drawn on the same 8x8 grid */
  const MEAL_ROWS = [0xA6, 0xA6, 0xE6, 0x46, 0x44, 0x44, 0x44, 0x44];
  function mealPx(s, extra) {
    let r = '';
    MEAL_ROWS.forEach((v, y) => { let x = 0; while (x < 8) { if (v & (128 >> x)) { let e = x; while (e < 8 && (v & (128 >> e))) e++; r += '<rect x="' + x + '" y="' + y + '" width="' + (e - x) + '" height="1"/>'; x = e; } else x++; } });
    return '<svg class="px' + (extra ? ' ' + extra : '') + '" width="' + s + '" height="' + s + '" viewBox="0 0 8 8" fill="currentColor" shape-rendering="crispEdges" aria-hidden="true">' + r + '</svg>';
  }
  const kindIcon = (k, s) => k === 'meal' ? mealPx(s) : icon(KIND_ICON[k] || 'star', s);
  const KIND_ICON = { sleep: 'moon', work: 'briefcase', commute: 'clock', maint: 'smile', rest: 'heart', free: 'star', block: 'lock' };
  const TAG_ICON = [['fitness', 'dumbbell'], ['language', 'chat'], ['reading', 'book'], ['study', 'book'], ['admin', 'coin'], ['business', 'briefcase'], ['creative', 'star'], ['wellbeing', 'moon'], ['nutrition', 'flame'], ['practical', 'mountain']];
  const tagIcon = t => (TAG_ICON.find(([p]) => String(t).startsWith(p)) || [0, 'target'])[1];
  const STATE_LABEL = { PLANNED: '', COMPLETED_ON_TIME: 'Done', COMPLETED_EARLY: 'Done early', COMPLETED_LATE: 'Done late', PARTIALLY_COMPLETED: 'Partly done', SKIPPED_VOLUNTARY: 'Skipped',
    MISSED_UNEXPLAINED: 'Missed', EXCUSED_BY_TOKEN: 'Covered by rest token', EXCUSED_DISPLACED: 'Excused' };

  /* ── rendering ── */
  const TABS = [['today', 'Today'], ['week', 'Week'], ['goals', 'Goals'], ['quests', 'Quests'], ['setup', 'My time'], ['settings', 'Settings']];
  const TAB_ICON = { today: 'clock', week: 'grid', goals: 'target', quests: 'flag', setup: 'moon', settings: 'gear', lab: 'dice' };
  const tabList = () => TABS.concat(ST.ui && ST.ui.lab ? [['lab', 'Lab']] : []);
  function goTab(k) { ui.tab = k; ST.ui.tab = k; persist(); render(); }
  /* the dot and the line under the title: how the plan stands */
  function planStatus(now) {
    const s = ST.schedule;
    if (!ST.setupComplete || !s) return { dot: 'off', line: ST.setupComplete ? 'No plan yet' : 'Not set up yet' };
    const n = s.scheduledInstances.filter(i => i.lifecycleState === 'PLANNED' && i.endEpoch > now).length, valid = s.status === 'VALID_SCHEDULE';
    return { dot: valid ? 'ok' : 'warn', line: n + ' session' + (n === 1 ? '' : 's') + ' ahead' + (valid ? '' : ' · see notes in Week') };
  }
  /* the banner's buttons: the three ways to regroup, or a re-plan after a failure */
  function bannerActions(b) {
    if (b.regroup) return [{ label: 'Pause 48h', act: 'regroup', id: 'pause' }, { label: 'Light mode this week', act: 'regroup', id: 'light' }, { label: 'Carry on', act: 'regroup', id: 'go', ghost: true }];
    return b.kind === 'bad' && ST.setupComplete ? [{ label: 'Re-plan from now', act: 'replan', small: true }] : [];
  }
  function headerHtml() {
    const now = nowEpoch(), p = planStatus(now);
    return '<div class="ld-top"><div class="ld-id"><span class="ld-dot" data-s="' + p.dot + '" aria-hidden="true"></span><div><h2>Life Director</h2><small>' + esc(p.line) + '</small></div></div>' +
      '<div class="ld-top-r"><button class="ld-help" data-act="tour" aria-label="Show the guide for this screen">?</button><div class="ld-clock" aria-label="Current time"><b data-clock>' + esc(fTime(now)) + '</b><small>' + esc(fDay(now)) + '</small></div></div></div>';
  }
  function render() {
    if (!root) return;
    if (ui.bfRun) ui.bfRun.stop();
    const tabs = tabList();
    const mainEl = root.querySelector('.ld-main'), strip = mainEl.querySelector('.ld-strip');
    ui.fx.daysScroll = strip ? strip.scrollLeft : null;
    mainEl.classList.remove('ld-v-in', 'ld-d-in');
    root.dataset.tab = ui.tab;
    root.querySelector('.ld-head').innerHTML = headerHtml();
    const nav = root.querySelector('.ld-tabs'), navKey = tabs.map(t => t[0]).join();
    if (nav.dataset.key !== navKey) {
      nav.dataset.key = navKey; nav.setAttribute('role', 'tablist');
      nav.innerHTML = tabs.map(([k, l]) => '<button class="ld-tab" role="tab" data-act="tab" data-tab="' + k + '">' + '<span aria-hidden="true">' + icon(TAB_ICON[k], 14) + '</span>' + l + '</button>').join('');
    }
    nav.querySelectorAll('.ld-tab').forEach(b => { const on = b.dataset.tab === ui.tab; b.classList.toggle('on', on); b.setAttribute('aria-selected', on); b.tabIndex = on ? 0 : -1; });
    let banner = '';
    if (ui.banner) {
      const acts = bannerActions(ui.banner);
      banner = '<div class="ld-banner ld-b-' + ui.banner.kind + '"><p>' + esc(ui.banner.text) + '</p>' +
        (acts.length ? '<div class="ld-row">' + acts.map(a => '<button class="ld-btn' + (a.ghost ? ' ld-ghost' : '') + (a.small ? ' ld-small' : '') + '" data-act="' + a.act + '"' + (a.id ? ' data-v="' + a.id + '"' : '') + '>' + a.label + '</button>').join('') + '</div>' : '') +
        (ui.banner.regroup ? '' : '<button class="ld-x" data-act="dismissBanner" aria-label="Dismiss">×</button>') + '</div>';
    }
    if (!Host.connected() && ST.setupComplete && !ST.ui.hostNoticeDismissed) banner += '<div class="ld-banner ld-b-warn"><p>LifeMax was not detected on this page, so quests and XP are not connected. Your plan still works; completions are kept in Life Director until you open it inside LifeMax.</p><button class="ld-x" data-act="dismissHost" aria-label="Dismiss">×</button></div>';
    const body = { today: renderToday, week: renderWeek, goals: renderGoals, quests: renderQuests, setup: renderSetup, settings: renderSettings, lab: renderLab }[ui.tab] || renderToday;
    mainEl.innerHTML = banner + body();
    ui.boundary = ui.tab === 'today' && ui.dayIdx === 0 ? nextBoundary() : null;
    afterRender(tabs);
    ui.animate = false; ui.prevPlan = null; ui.fx.justDone = null;
    changed();
  }
  function afterRender(tabs) {
    const fx = ui.fx, main = root.querySelector('.ld-main');
    const dayKey = ui.tab === 'lab' ? ui.lab.dayIdx : ui.dayIdx;
    const tabChanged = fx.prevTab !== null && fx.prevTab !== ui.tab;
    const idx = k => tabs.findIndex(t => t[0] === k);
    if (tabChanged) play(main, 'ld-v-in', { '--dx': idx(ui.tab) > idx(fx.prevTab) ? 1 : -1 });
    else if (fx.prevDay !== null && fx.prevDay !== dayKey) play(main, 'ld-d-in', { '--dx': dayKey > fx.prevDay ? 1 : -1 });
    const strip = main.querySelector('.ld-strip');
    if (strip) {
      if (!tabChanged && fx.daysScroll != null) strip.scrollLeft = fx.daysScroll;
      const on = strip.querySelector('.on');
      if (on && (on.offsetLeft < strip.scrollLeft || on.offsetLeft + on.offsetWidth > strip.scrollLeft + strip.clientWidth)) strip.scrollLeft = Math.max(0, on.offsetLeft - (strip.clientWidth - on.offsetWidth) / 2);
    }
    const tab = root.querySelector('.ld-tab.on');
    if (tab && tabChanged) { try { tab.scrollIntoView({ block: 'nearest', inline: 'nearest' }); } catch (e) { /* ignore */ } }
    const dial = main.querySelector('.ld-dial');
    if (dial) {
      attachDial(dial);
      const bf = ui.tab === 'today' && takeBuild();
      if (bf) { if (!nativeCovers()) runBuild(dial, bf); } // under a native screen the screen plays it (nativeBuild), the page nothing
      else if (ui.animate && !reduced()) play(dial, 'ld-dial-in');
    }
    const mini = main.querySelector('.ld-mini');
    if (mini) attachMini(mini);
    fx.prevTab = ui.tab; fx.prevDay = dayKey;
    if (ui.tour) {
      if (ui.tour.key !== ui.tab) tourEnd(true);
      else { const st = ui.tour.steps[ui.tour.i], el = st.sel && main.parentNode.querySelector(st.sel); if (el) { try { el.scrollIntoView({ block: st.start ? 'start' : 'center', behavior: 'auto' }); } catch (e) { /* ignore */ } } tourPlace(); }
    } else if (!ui.bfRun) tourAuto();
  }

  /* ── the chrono dial ─────────────────────────────────────────────────────
     A 24-hour face, midnight at the top. Outer ring: the time you told Life Director
     about (sleep, work, travel, routines). Inner ring: what Life Director planned into the
     rest (sessions, protected downtime). The drag handles on the outer ring edit the
     recurring rule behind them, after a confirmation. Presentation only. */
  const DIAL = { C: 180, RO: 130, WO: 18, RI: 101, WI: 20, T0: 146, T1: 152, RL: 164 };
  const polar = (r, a) => { const t = (a - 90) * Math.PI / 180; return [DIAL.C + r * Math.cos(t), DIAL.C + r * Math.sin(t)]; };
  function arcD(r, a0, a1) {
    if (a1 - a0 >= 359.95) { const [x0, y0] = polar(r, 0), [x1, y1] = polar(r, 180); return 'M' + x0 + ' ' + y0 + 'A' + r + ' ' + r + ' 0 1 1 ' + x1 + ' ' + y1 + 'A' + r + ' ' + r + ' 0 1 1 ' + x0 + ' ' + y0; }
    const [x0, y0] = polar(r, a0), [x1, y1] = polar(r, a1);
    return 'M' + x0.toFixed(2) + ' ' + y0.toFixed(2) + 'A' + r + ' ' + r + ' 0 ' + (a1 - a0 > 180 ? 1 : 0) + ' 1 ' + x1.toFixed(2) + ' ' + y1.toFixed(2);
  }
  const angOf = (ep, d0, d1) => (Math.max(d0, Math.min(d1, ep)) - d0) / Math.max(1, d1 - d0) * 360;
  function gap(a0, a1, g) { return a1 - a0 > g * 2.5 ? [a0 + g / 2, a1 - g / 2] : [a0, a1]; }
  const OUTER = { sleep: 1, work: 1, commute: 1, maint: 1, block: 1 };
  function blockClass(b) { return b.inst ? 'task' : (KIND_CLASS[b.kind] || (b.code === 'RECOVERY_PROTECTED' ? 'rest' : 'free')); }
  function sesState(st) { return /COMPLETED/.test(st) && !/PARTIAL/.test(st) ? 'done' : /PARTIAL/.test(st) ? 'part' : st === 'PLANNED' ? 'plan' : 'off'; }

  /* rules behind the outer ring, so a handle knows which rule it edits */
  const DRAG_KINDS = { SLEEP: 1, WORK: 1, CLASS: 1, APPOINTMENT: 1, UNAVAILABLE: 1 };
  const ymdDow = ymd => new Date(Date.UTC(ymd.y, ymd.mo - 1, ymd.d)).getUTCDay();
  function ruleOn(r, ymd) {
    const key = U.ymdKey(ymd);
    if (r.validFrom && key < r.validFrom) return false;
    if (r.validTo && key > r.validTo) return false;
    if (r.rotation && r.rotation.anchorDate && r.rotation.cycleLength > 0) {
      const [y, mo, d] = String(r.rotation.anchorDate).split('-').map(Number);
      const n = Math.round((Date.UTC(ymd.y, ymd.mo - 1, ymd.d) - Date.UTC(y, mo - 1, d)) / 86400000), c = r.rotation.cycleLength;
      return (r.rotation.activeDays || []).map(Number).includes(((n % c) + c) % c);
    }
    return (Array.isArray(r.days) ? r.days : [0, 1, 2, 3, 4, 5, 6]).includes(ymdDow(ymd));
  }
  function ruleInstances(day) {
    const out = [], z = tz();
    for (const r of ST.availability.rules || []) {
      if (!DRAG_KINDS[r.kind]) continue;
      for (const ymd of [U.ymdAdd(day.ymd, -1), day.ymd]) {
        if (!ruleOn(r, ymd)) continue;
        const s = U.zonedToEpoch(ymd.y, ymd.mo, ymd.d, r.startMin, z), e = U.zonedToEpoch(ymd.y, ymd.mo, ymd.d, r.startMin + r.durationMin, z);
        if (e <= day.startEpoch || s >= day.endEpoch) continue;
        out.push({ rule: r, startEpoch: s, endEpoch: e, ymd });
      }
    }
    return out;
  }
  function handleLabel(r, end) {
    if (r.kind === 'SLEEP') return end === 'start' ? 'Bedtime' : 'Wake up';
    return (r.title || KIND_LABEL[r.kind] || 'Commitment') + (end === 'start' ? ' starts' : ' ends');
  }
  /* clock-face words: "6 AM", "Noon" in 12-hour places; 06, 12 in 24-hour ones */
  const H12 = (() => { try { const c = new Intl.DateTimeFormat(undefined, { hour: 'numeric' }).resolvedOptions(); return c.hourCycle ? /h1[12]/.test(c.hourCycle) : !!c.hour12; } catch (e) { return true; } })();
  const LOCALE = (() => { try { return new Intl.DateTimeFormat().resolvedOptions().locale; } catch (e) { return 'en-US'; } })(); // the locale times are written in
  const hourWord = h => H12 ? (h === 0 ? 'Midnight' : h === 12 ? 'Noon' : (h % 12) + (h < 12 ? ' AM' : ' PM')) : String(h).padStart(2, '0') + ':00';
  /* "9 AM", "5:30 PM": short times for writing on the rings */
  const fC = ep => fTime(ep).replace(/:00(?=\D|$)/, '').replace(/\s+/g, ' ');
  let finishSeq = 0;
  /* Local paint IDs keep Today, Overview, setup and widget SVGs independent.
     All finish geometry is decorative; time radii and hit areas stay unchanged. */
  function faceHtml(paintId) {
    const id = paintId || 'ldf' + (++finishSeq);
    const gradient = (name, stops) => '<linearGradient id="' + id + '-' + name + '" x1="0" y1="0" x2=".35" y2="1">' + stops.map(([at, color, opacity]) => '<stop offset="' + at + '" stop-color="' + color + '" stop-opacity="' + (opacity == null ? 1 : opacity) + '"/>').join('') + '</linearGradient>';
    const defs = '<defs>' + gradient('face', [[0, '#0b1122'], [.55, '#121826'], [1, '#1b1b22']]) +
      gradient('bezel', [[0, '#171e2c'], [.35, '#0b0e15'], [1, '#080c14']]) +
      gradient('edge', [[0, '#d2e2ff', .26], [.42, '#8093b0', .04], [.7, '#000000', .28], [1, '#000000', .6]]) +
      gradient('hub', [[0, '#141c2e'], [.5, '#101625'], [1, '#0c111c']]) + '</defs>';
    const circle = (cls, r, extra) => '<circle class="' + cls + '" cx="180" cy="180" r="' + r + '"' + (extra || '') + '/>';
    const edge = r => circle('ld-depth-edge', r, ' stroke="url(#' + id + '-edge)"');
    const depth = '<g class="ld-depth" aria-hidden="true" pointer-events="none">' +
      circle('ld-depth-chapter', 149, ' stroke-width="15"') + edge(157.5) + edge(140.5) +
      circle('ld-depth-channel', DIAL.RO, ' stroke-width="' + (DIAL.WO + 3) + '"') + edge(119.5) +
      circle('ld-depth-divider', 115.5) +
      circle('ld-depth-channel', DIAL.RI, ' stroke-width="' + (DIAL.WI + 3) + '"') + edge(112.5) + edge(89.5) +
      circle('ld-depth-hub', 86.5, ' fill="url(#' + id + '-hub)"') + edge(87.5) + circle('ld-depth-lip', 82) + '</g>';
    let t = '';
    for (let k = 0; k < 96; k++) {
      const a = k * 3.75, hr = k % 4 === 0, six = k % 24 === 0;
      const [x0, y0] = polar(six ? DIAL.T0 - 4 : hr ? DIAL.T0 : DIAL.T0 + 3, a), [x1, y1] = polar(DIAL.T1, a);
      t += '<line class="ld-tk' + (six ? ' ld-tk6' : hr ? ' ld-tkh' : '') + '" x1="' + x0.toFixed(1) + '" y1="' + y0.toFixed(1) + '" x2="' + x1.toFixed(1) + '" y2="' + y1.toFixed(1) + '"/>';
    }
    let l = '';
    for (let h = 0; h < 24; h += 3) {
      let [x, y] = polar(DIAL.RL, h * 15), anc = '';
      if (h === 6) { x = 359; anc = ' style="text-anchor:end"'; } else if (h === 18) { x = 1; anc = ' style="text-anchor:start"'; } else if (h === 0) y = 13; else if (h === 12) y = 347;
      if (h % 6 !== 0) {
        // Engrave diagonal hour labels along the circular bezel, rather than
        // placing horizontal text across the edge of the clock face.
        const angle = h * 15;
        const radius = DIAL.RL;
        const start = angle - 13, end = angle + 13;
        const point = deg => polar(radius, deg);
        const p0 = point(start), p1 = point(end);
        // Reverse the lower semicircle so glyphs remain upright.
        const reverse = angle > 90 && angle < 270;
        const a = reverse ? p1 : p0, b = reverse ? p0 : p1;
        const pathId = id + '-hour-' + h;
        l += '<defs><path id="' + pathId + '" d="M' + a[0].toFixed(2) + ' ' + a[1].toFixed(2) +
          ' A' + radius + ' ' + radius + ' 0 0 ' + (reverse ? 0 : 1) + ' ' +
          b[0].toFixed(2) + ' ' + b[1].toFixed(2) + '"/></defs>';
        l += '<text class="ld-hl"><textPath href="#' + pathId +
          '" startOffset="50%" text-anchor="middle">' + hourWord(h) + '</textPath></text>';
      } else {
        l += '<text class="ld-hl ld-hl6" x="' + x.toFixed(1) + '" y="' + y.toFixed(1) + '"' + anc + '>' + hourWord(h) + '</text>';
      }
    }
    /* night above, day below: midnight sits at the top */
    /* small sun and moon beside Noon and Midnight, outside the rings */
    const tw = h => hourWord(h).length * 3.3 + 10;
    const sun = (() => { const cx = 180 - tw(12) - 2, cy = 347; let r = '<circle cx="' + cx.toFixed(1) + '" cy="' + cy + '" r="2.6"/>'; for (let k = 0; k < 8; k++) { const a = k * 45 * Math.PI / 180; r += '<line x1="' + (cx + Math.cos(a) * 4.3).toFixed(1) + '" y1="' + (cy + Math.sin(a) * 4.3).toFixed(1) + '" x2="' + (cx + Math.cos(a) * 6).toFixed(1) + '" y2="' + (cy + Math.sin(a) * 6).toFixed(1) + '"/>'; } return '<g class="ld-sun">' + r + '</g>'; })();
    const moon = (() => { const cx = 180 - tw(0) - 2, cy = 13; return '<path class="ld-moon" d="M' + (cx + 1.5).toFixed(1) + ' ' + (cy - 5) + 'a5 5 0 1 0 3.6 8.6a4 4 0 1 1 -3.6 -8.6z"/>'; })();
    return defs + '<circle class="ld-bezel" cx="180" cy="180" r="176" style="fill:url(#' + id + '-bezel)"/>' +
      '<circle class="ld-face" cx="180" cy="180" r="158" style="fill:url(#' + id + '-face)"/>' + depth + t + l +
      '<circle class="ld-trk" cx="180" cy="180" r="' + DIAL.RO + '" stroke-width="' + DIAL.WO + '"/>' +
      '<circle class="ld-trk ld-trk-in" cx="180" cy="180" r="' + DIAL.RI + '" stroke-width="' + DIAL.WI + '"/>' + moon + sun;
  }
  function surfaceArc(r, width, a0, a1, id, b) {
    const timing = ' data-s="' + b.startEpoch + '" data-e="' + b.endEpoch + '"';
    return '<g class="ld-surface" aria-hidden="true" pointer-events="none">' +
      '<path class="ld-surface-edge"' + timing + ' d="' + arcD(r - width / 2 + .6, a0, a1) + '" stroke="url(#' + id + '-edge)"/>' +
      '<path class="ld-surface-edge"' + timing + ' d="' + arcD(r + width / 2 - .6, a0, a1) + '" stroke="url(#' + id + '-edge)"/></g>';
  }
  /* The hand follows time, never a full backwards turn at midnight. Its SVG
     attribute remains authoritative for redraws, exports and build animations. */
  function updateDialHand(hand, angle) {
    if (!hand) return;
    const from = +((/rotate\(([-\d.]+)/.exec(hand.getAttribute('transform') || '') || [0, angle])[1]);
    hand.setAttribute('transform', 'rotate(' + angle.toFixed(2) + ' 180 180)');
    if (hand._ldMotion) { hand._ldMotion.cancel(); hand._ldMotion = null; }
    const delta = ((angle - from + 540) % 360) - 180;
    if (reduced() || document.hidden || !hand.animate || Math.abs(delta) > 3 || Math.abs(delta) < .001) return;
    const rotation = a => 'translate(180px,180px) rotate(' + a + 'deg) translate(-180px,-180px)';
    hand._ldMotion = hand.animate([{ transform: rotation(angle - delta) }, { transform: rotation(angle) }], { duration: 650, easing: 'cubic-bezier(.22,1,.36,1)' });
  }
  /* words written along an arc, turned so they never read upside down */
  let lblSeq = 0;
  function arcRev(r, a0, a1) { const [x0, y0] = polar(r, a1), [x1, y1] = polar(r, a0); return 'M' + x0.toFixed(2) + ' ' + y0.toFixed(2) + 'A' + r + ' ' + r + ' 0 ' + (a1 - a0 > 180 ? 1 : 0) + ' 0 ' + x1.toFixed(2) + ' ' + y1.toFixed(2); }
  /* the first of the options (longest first) that fits along the arc, and whether it is turned to read upright */
  function arcWords(r, a0, a1, options) {
    const len = (a1 - a0) * Math.PI / 180 * r - 10;
    const text = options.find(o => o && o.length * 6.3 + 6 <= len);
    if (!text) return null;
    const mid = ((a0 + a1) / 2) % 360;
    return { text, flip: mid > 95 && mid < 265 };
  }
  function arcText(r, a0, a1, options, cls) {
    const w = arcWords(r, a0, a1, options);
    if (!w) return '';
    const id = 'ldtp' + (++lblSeq);
    return '<path id="' + id + '" d="' + (w.flip ? arcRev(r, a0, a1) : arcD(r, a0, a1)) + '" fill="none"/><text class="ld-at ' + cls + '"><textPath href="#' + id + '" startOffset="50%">' + esc(w.text) + '</textPath></text>';
  }
  function blockWords(b, d0, d1) {
    const name = b.code === 'SLEEP' || /sleep/i.test(b.title) ? 'Sleep' : b.title;
    const span = b.startEpoch <= d0 && b.endEpoch < d1 ? 'until ' + fC(b.endEpoch) : b.endEpoch >= d1 && b.startEpoch > d0 ? 'from ' + fC(b.startEpoch) : fC(b.startEpoch) + '–' + fC(b.endEpoch);
    return [name + ' · ' + span, name, span];
  }
  /* the words under Today's dial, a handle drawn between them */
  const DIAL_HINT = Object.freeze(['Tap a session for details. Drag a', 'handle to change your sleep or work hours.']);
  /* what a tap on fixed time says (k: its colour class); the hint about dragging only where the dial has handles */
  function anchorTip(k, title, s, e, handles) {
    const hint = k === 'sleep' || k === 'work' ? (handles ? '. Drag its handle to change it.' : '') : k === 'maint' ? '. Routines are set in My time.' : '';
    return title + ' · ' + fTime(s) + '–' + fTime(e) + hint;
  }
  /* a session's icon, sitting on its arc */
  function sesIcon(b, a0, a1, st) {
    const chord = (a1 - a0) * Math.PI / 180 * DIAL.RI;
    if (chord < 9) return '';
    const s = Math.min(12, Math.floor(chord - 1)), [x, y] = polar(DIAL.RI, (a0 + a1) / 2);
    if (st === 'done') return '<path class="ld-schk" d="M' + (x - 4.5).toFixed(1) + ' ' + y.toFixed(1) + 'l3 3l6 -6.5"/>';
    if (st === 'off') return '<path class="ld-sx" d="M' + (x - 3.5).toFixed(1) + ' ' + (y - 3.5).toFixed(1) + 'l7 7m0 -7l-7 7"/>';
    const svg = String(icon(tagIcon(b.inst.tag), s) || '');
    return svg.indexOf('<svg') === 0 ? svg.replace('<svg class="px"', '<svg class="px ld-sico" x="' + (x - s / 2).toFixed(1) + '" y="' + (y - s / 2).toFixed(1) + '"') : '';
  }
  function dialHtml(day, blocks, isToday, now, plain) {
    const paintId = 'ldf' + (++finishSeq);
    const d0 = day.startEpoch, d1 = day.endEpoch;
    const nextId = isToday ? (nextUp(now) || {}).instanceId : null;
    let outer = '', words = '', inner = '', i = 0;
    const voiceIds = new Set(((ST && ST.fixedCommitments) || []).filter(c => c.voice).map(c => String(c.id || c.commitmentId)));
    for (const b of blocks) {
      const k = blockClass(b);
      let [a0, a1] = [angOf(b.startEpoch, d0, d1), angOf(b.endEpoch, d0, d1)];
      if (a1 - a0 < 0.3) continue;
      if (!b.inst && b.refId && voiceIds.has(String(b.refId)) && b.kind === 'APPOINTMENT') {
        /* a one-off you set by voice is a plan item: inner ring, with your other activities */
        [a0, a1] = gap(a0, a1, 1.4);
        const d = arcD(DIAL.RI, a0, a1);
        inner += '<g class="ld-meal ld-appt" data-act="anchor" data-k="work" data-t="' + esc(b.title) + '" data-s="' + b.startEpoch + '" data-e="' + b.endEpoch + '" role="button" tabindex="0" aria-label="' + esc(b.title + ', ' + fTime(b.startEpoch) + ' to ' + fTime(b.endEpoch)) + '"><title>' + esc(b.title + ' ' + fTime(b.startEpoch) + '–' + fTime(b.endEpoch)) + '</title>' +
          '<path class="ld-hit" d="' + d + '" stroke-width="40"/><path class="ld-ap" d="' + d + '" stroke-width="' + DIAL.WI + '"/>' + surfaceArc(DIAL.RI, DIAL.WI, a0, a1, paintId, b) + '</g>';
      } else if (OUTER[k]) {
        [a0, a1] = gap(a0, a1, 0.9);
        const w = arcText(DIAL.RO, a0, a1, blockWords(b, d0, d1), 'ld-c-' + k);
        outer += '<path class="ld-o ld-c-' + k + '" d="' + arcD(DIAL.RO, a0, a1) + '" stroke-width="' + DIAL.WO + '" data-act="anchor" data-t="' + esc(b.title) + '" data-s="' + b.startEpoch + '" data-e="' + b.endEpoch + '" data-k="' + k + '"' + (w ? ' data-lbl="1"' : '') + '><title>' + esc(b.title + ' ' + fTime(b.startEpoch) + '–' + fTime(b.endEpoch)) + '</title></path>' + surfaceArc(DIAL.RO, DIAL.WO, a0, a1, paintId, b);
        words += w;
      } else if (k === 'meal') {
        [a0, a1] = gap(a0, a1, 1.4);
        const d = arcD(DIAL.RI, a0, a1), chord = (a1 - a0) * Math.PI / 180 * DIAL.RI;
        let ic = '';
        if (chord >= 9) { const sz = Math.min(12, Math.floor(chord - 1)), [x, y] = polar(DIAL.RI, (a0 + a1) / 2); ic = mealPx(sz, 'ld-sico').replace('<svg class="px ld-sico"', '<svg class="px ld-sico" x="' + (x - sz / 2).toFixed(1) + '" y="' + (y - sz / 2).toFixed(1) + '"'); }
        inner += '<g class="ld-meal"' + mealAnchor(b) + ' aria-label="' + esc(b.title + ', ' + fTime(b.startEpoch) + ' to ' + fTime(b.endEpoch)) + '">' +
          '<path class="ld-hit" d="' + d + '" stroke-width="40"/><path class="ld-ma" d="' + d + '" stroke-width="' + DIAL.WI + '"/>' + surfaceArc(DIAL.RI, DIAL.WI, a0, a1, paintId, b) + ic + '</g>';
      } else if (k === 'rest') {
        inner += '<path class="ld-rest" d="' + arcD(DIAL.RI, a0, a1) + '" stroke-width="5" data-s="' + b.startEpoch + '" data-e="' + b.endEpoch + '"/>';
        if (b.endEpoch - b.startEpoch >= 3600) words += arcText(DIAL.RI + 9, a0, a1, ['Downtime · ' + mins(Math.round((b.endEpoch - b.startEpoch) / 60)), 'Downtime', 'Rest'], 'ld-at-in ld-c-rest');
      } else if (k === 'free' && b.endEpoch - b.startEpoch >= 5400 && !(isToday && b.endEpoch <= now)) {
        words += arcText(DIAL.RI, a0, a1, ['Free · ' + mins(Math.round((b.endEpoch - b.startEpoch) / 60)), 'Free'], 'ld-at-in ld-c-free');
      } else if (k === 'task') {
        [a0, a1] = gap(a0, a1, 1.4);
        const st = sesState(b.inst.lifecycleState), fx = placeFx(b), nx = b.inst.instanceId === nextId;
        const d = arcD(DIAL.RI, a0, a1);
        inner += '<g class="ld-ses ld-s-' + st + (nx ? ' ld-next' : '') + (fx.cls ? ' ld-ses-fx' : '') + '" data-s="' + b.startEpoch + '" data-e="' + b.endEpoch + '" data-act="inst" data-id="' + esc(b.inst.instanceId) + '" style="--i:' + (i++) + '" role="button" tabindex="0" aria-label="' + esc((nx ? 'Next: ' : '') + b.inst.title + ', ' + fTime(b.startEpoch) + ' to ' + fTime(b.endEpoch)) + '">' +
          '<path class="ld-hit" d="' + d + '" stroke-width="40"/>' + (nx ? '<path class="ld-nx" d="' + d + '" stroke-width="' + (DIAL.WI + 8) + '"/>' : '') + '<path class="ld-sa" d="' + d + '" stroke-width="' + DIAL.WI + '"/>' + (st === 'off' || st === 'part' ? '' : surfaceArc(DIAL.RI, DIAL.WI, a0, a1, paintId, b)) + sesIcon(b, a0, a1, st) + '</g>';
      }
    }
    let handles = '';
    for (const h of plain ? [] : dialHandles(day)) {
      const { ri, end, ep } = h, [x, y] = polar(DIAL.RO, angOf(ep, d0, d1));
      handles += '<g class="ld-hd ld-c-' + h.kind + '" data-rule="' + esc(ri.rule.id) + '" data-end="' + end + '" data-ymd="' + U.ymdKey(ri.ymd) + '" data-s="' + ri.startEpoch + '" data-e="' + ri.endEpoch + '" data-ep="' + ep + '" role="slider" tabindex="0" aria-label="' + esc(handleLabel(ri.rule, end) + ' ' + fTime(ep)) + '">' +
        '<circle class="ld-hd-hit" cx="' + x.toFixed(1) + '" cy="' + y.toFixed(1) + '" r="24"/><circle class="ld-hd-ring" cx="' + x.toFixed(1) + '" cy="' + y.toFixed(1) + '" r="10.5"/><circle class="ld-hd-pip" cx="' + x.toFixed(1) + '" cy="' + y.toFixed(1) + '" r="3.5"/></g>';
    }
    const live = !plain && now >= d0 && now < d1, an = live ? angOf(now, d0, d1) : 0;
    /* the part of today that has already gone, greyed out */
    let past = '';
    if (live && an > 1) {
      const R1 = DIAL.RO + DIAL.WO / 2 + 2, R0 = DIAL.RI - DIAL.WI / 2 - 2, lg = an > 180 ? 1 : 0;
      const [ox0, oy0] = polar(R1, 0), [ox1, oy1] = polar(R1, an), [ix1, iy1] = polar(R0, an), [ix0, iy0] = polar(R0, 0);
      past = '<path class="ld-past" d="M' + ox0.toFixed(1) + ' ' + oy0.toFixed(1) + 'A' + R1 + ' ' + R1 + ' 0 ' + lg + ' 1 ' + ox1.toFixed(1) + ' ' + oy1.toFixed(1) + 'L' + ix1.toFixed(1) + ' ' + iy1.toFixed(1) + 'A' + R0 + ' ' + R0 + ' 0 ' + lg + ' 0 ' + ix0.toFixed(1) + ' ' + iy0.toFixed(1) + 'Z"/>';
    }
    const hand = live ? '<g class="ld-hand" transform="rotate(' + an.toFixed(2) + ' 180 180)"><line x1="180" y1="' + (180 - 84) + '" x2="180" y2="' + (180 - 156) + '"/><circle cx="180" cy="' + (180 - 156) + '" r="3.6"/></g>' : '';
    return '<div class="ld-dial' + (isToday ? '" data-today="1' : '') + '" data-d0="' + d0 + '" data-d1="' + d1 + '">' +
      '<svg viewBox="0 0 360 360" role="group" aria-label="24-hour plan for ' + esc(fDate(d0)) + '. Outer ring: your fixed time. Inner ring: planned sessions.">' +
      faceHtml(paintId) + '<g class="ld-outer">' + outer + '</g><g class="ld-words">' + words + '</g><g class="ld-inner">' + inner + '</g>' + past + (live ? '<g class="ld-cur">' + curRingHtml(nowState(now), now) + '</g>' : '') + '<path class="ld-drag-arc" d="" stroke-width="' + (DIAL.WO + 4) + '"/>' + hand + '<g class="ld-hds">' + handles + '</g></svg>' +
      '<div class="ld-core">' + coreHtml(day, blocks, isToday, now) + '</div></div>';
  }
  /* ── the build ──────────────────────────────────────────────────────────
     Whenever Life Director builds or repairs your plan, the dial shows it happening.
     Full build (any re-plan): a scanner arm sweeps the day from midnight and draws
     your fixed time onto the outer ring as it passes, while the centre reads out the
     hour and what fills it. Then the planner works: each new session launches from the
     centre and spirals out into its slot (ripple, sparks, counter), moved sessions orbit
     from where they were to where they go, dropped ones break up. Then everything locks
     in: a shockwave, a small kick, the hand swings round to now, the centre comes back.
     Repair (skip, move, missed, "Something came up"): the same without the sweep.
     Presentation only: Web Animations on the real dial, nothing is redrawn. Tap the dial
     to skip. Reduced motion, or no Web Animations, means no build at all. */
  const BF = { SWEEP: 1250, FLY: 560, SPAN: 950, GAP_MIN: 70, GAP_MAX: 165, LOCK: 820, KEEP_MIN: 30 * 60 * 1000 };
  let buildId = 0;
  function queueBuild(mode, prev) {
    const q = ui.bfQ;
    /* several changes before you look (or a run of missed-block repairs): one build, diffed against what you last saw */
    if (q && Date.now() - q.at < BF.KEEP_MIN) { if (mode === 'build') q.mode = 'build'; }
    else ui.bfQ = { mode, prev, at: Date.now() };
    /* the same build for a native screen, which plays each id once (nativeBuild): ids rise with the clock, so a page that
       reloaded never repeats one the screen has played */
    buildId = Math.max(buildId + 1, Date.now());
    ui.nativeBuild = { id: buildId, mode: ui.bfQ.mode, prev: ui.bfQ.prev, at: Date.now() };
  }
  /* a change that moved nothing plays no build */
  function dropBuild() { ui.bfQ = null; ui.nativeBuild = null; }
  function takeBuild() {
    const q = ui.bfQ; ui.bfQ = null;
    if (!q || Date.now() - q.at > BF.KEEP_MIN || reduced() || typeof Element === 'undefined' || !Element.prototype.animate) return null;
    return q;
  }
  /* What a build shows on a day's dial (d0..d1) and when, in milliseconds from its start: the sweep and its read-out (a full build),
     the sessions that launch from the centre (new) or orbit from where they were (moved, from: degrees back to where they were),
     those kept, those that left the plan and flash (flips), the dropped ones that break up (ghosts), and the read-out's words at
     each step. q: the queued build ({mode, prev}); sessions: the dial's session arcs [{id, s, e}]; spans: its fixed time
     [{s, e, title}]; today: the dial shows today. runBuild plays it on the page; a native screen plays the same plan (nativeBuild). */
  function buildPlan(q, d0, d1, sessions, spans, today) {
    const span = Math.max(1, d1 - d0), ang = ep => angOf(ep, d0, d1);
    const full = q.mode === 'build', prev = q.prev;
    const all = new Map((ST.schedule.scheduledInstances || []).concat(ST.schedule.historyInstances || []).map(i => [i.instanceId, i]));
    const plannedSeen = new Set((ST.schedule.scheduledInstances || []).filter(i => i.lifecycleState === 'PLANNED').map(i => i.title + '|' + i.startEpoch));
    const fly = [], keep = [], flips = [];
    for (const x of sessions) {
      const inst = all.get(x.id), planned = inst && inst.lifecycleState === 'PLANNED';
      const it = { id: x.id, a0: ang(x.s), a1: ang(x.e), kind: 'keep', from: 0 };
      if (!prev) it.kind = full ? 'new' : 'keep';
      else if (planned) {
        const was = prev.byId.get(x.id);
        if (was === x.s || prev.seen.has(inst.title + '|' + x.s)) it.kind = 'keep';
        else if (was != null && Math.abs((was - x.s) / span * 360) <= 330) { it.kind = 'moved'; it.from = (was - x.s) / span * 360; }
        else it.kind = 'new';
      } else if (prev.byId.has(x.id)) flips.push(it.id);
      (it.kind === 'keep' ? keep : fly).push(it);
    }
    const dropped = prev ? prev.list.filter(p => !all.has(p.id) && !plannedSeen.has(p.title + '|' + p.s) && p.e > d0 && p.s < d1) : [];
    fly.sort((a, b) => a.a0 - b.a0);
    const nNew = fly.filter(f => f.kind === 'new').length, nMoved = fly.length - nNew, total = sessions.length;
    /* timeline */
    const T = full ? BF.SWEEP : 0;
    const tB = full ? T + 160 : 140;
    const gapMs = fly.length > 1 ? Math.max(BF.GAP_MIN, Math.min(BF.GAP_MAX, BF.SPAN / (fly.length - 1))) : 0;
    const tFly = tB + (dropped.length ? 260 : 0);
    const flyDur = it => it.kind === 'moved' ? Math.min(950, BF.FLY + Math.abs(it.from) * 2.2) : BF.FLY;
    let tC = tFly;
    const items = fly.map((it, k) => {
      const t = tFly + k * gapMs, ms = flyDur(it);
      tC = Math.max(tC, t + ms);
      return { id: it.id, kind: it.kind, a0: it.a0, a1: it.a1, from: it.from, at: t, ms, land: t + ms * (it.kind === 'new' ? 0.68 : 0.82) };
    });
    tC = fly.length ? tC + 60 : tB + (dropped.length ? 420 : 80);
    /* the words */
    const counts = [nNew && (prev ? nNew + ' added' : ''), nMoved && nMoved + ' moved', dropped.length && dropped.length + ' dropped'].filter(Boolean).join(' · ') || 'sessions placed';
    const anyChange = fly.length || dropped.length || flips.length;
    let sweep = null;
    if (full) {
      const fixed = spans.map(x => ({ s: x.s, e: x.e, t: /sleep/i.test(x.title) ? 'Sleep' : x.title }));
      const marks = [d0, d1].concat(fixed.flatMap(x => [x.s, x.e]));
      for (let h = Math.ceil(d0 / 3600) * 3600; h < d1; h += 3600) marks.push(h);
      const readout = [];
      for (const ep of [...new Set(marks)].filter(t => t >= d0 && t <= d1).sort((a, b) => a - b)) {
        const hit = fixed.find(x => ep >= x.s && ep < x.e), w = { a: ang(ep), hour: fC(Math.floor(ep / 3600) * 3600), what: hit ? hit.t : 'Open time' };
        const last = readout[readout.length - 1];
        if (!last || last.hour !== w.hour || last.what !== w.what) readout.push(w);
      }
      sweep = { ms: T, kick: 'Mapping your day', readout };
    }
    return {
      mode: q.mode, full, sweep,
      intro: full ? null : { kick: 'Re-planning', big: '…', sub: '' },
      start: { at: tB, kick: !prev ? 'Placing sessions' : 'Re-planning', big: fly.length ? '0' : '–', sub: fly.length || dropped.length ? counts : 'Looking for changes' },
      items, keep: keep.map(it => it.id), flips,
      ghosts: dropped.map(g => ({ a0: ang(g.s), a1: ang(g.e) })).filter(g => g.a1 - g.a0 >= 0.3),
      lock: { at: tC, kick: anyChange || !prev ? 'Locked in' : 'Nothing needed to move', big: String(total), sub: total === 1 ? 'session' : 'sessions' + (today ? ' today' : '') },
      end: tC + BF.LOCK,
    };
  }
  function bfBuzz(ms) { try { if (navigator.vibrate && (!navigator.userActivation || navigator.userActivation.hasBeenActive)) navigator.vibrate(ms); } catch (e) { /* ignore */ } }
  function runBuild(dial, q) {
    const svg = dial.querySelector(':scope>svg'), core = dial.querySelector('.ld-core');
    if (!svg || !core || !ST.schedule) return;
    const NS = 'http://www.w3.org/2000/svg';
    const d0 = +dial.dataset.d0, d1 = +dial.dataset.d1, ang = ep => angOf(ep, d0, d1);
    const anims = [], timers = [], styled = new Set();
    let raf = 0, over = false;
    const A = (el, kf, o) => { if (!el) return null; try { const a = el.animate(kf, Object.assign({ fill: 'both' }, o)); anims.push(a); return a; } catch (e) { return null; } };
    const at = (ms, fn) => timers.push(setTimeout(() => { if (!over) fn(); }, Math.max(0, ms)));
    const css = (el, props) => { Object.assign(el.style, props); styled.add(el); };
    const CEN = { transformBox: 'view-box', transformOrigin: '50% 50%' }, OWN = { transformBox: 'fill-box', transformOrigin: '50% 50%' };
    const layer = document.createElementNS(NS, 'g'); layer.setAttribute('class', 'ld-bf'); layer.setAttribute('aria-hidden', 'true');
    svg.insertBefore(layer, svg.querySelector('.ld-hand') || svg.querySelector('.ld-hds'));
    const mk = (tag, attrs, parent) => { const n = document.createElementNS(NS, tag); for (const k in attrs) n.setAttribute(k, attrs[k]); (parent || layer).appendChild(n); return n; };
    const hud = document.createElement('div'); hud.className = 'ld-bhud'; hud.setAttribute('aria-hidden', 'true');
    hud.innerHTML = '<small class="ld-bh-k"></small><b class="ld-bh-n"></b><span class="ld-bh-s"></span>';
    dial.appendChild(hud);
    const [hk, hn, hs] = hud.children;
    const say3 = (k, n, s2) => { hk.textContent = k; hn.textContent = n; hs.textContent = s2; };
    const sayW = w => say3(w.kick, w.big, w.sub);
    let sweep = null;
    dial.classList.add('ld-building');

    /* what changed and when (buildPlan), on this dial's sessions and fixed time */
    const els = new Map([...svg.querySelectorAll('.ld-inner .ld-ses')].map(el => [el.dataset.id, el]));
    const spans = [...svg.querySelectorAll('.ld-outer .ld-o')].map(p => ({ s: +p.dataset.s, e: +p.dataset.e, title: p.dataset.t }));
    const B = buildPlan(q, d0, d1, [...els.values()].map(el => ({ id: el.dataset.id, s: +el.dataset.s, e: +el.dataset.e })), spans, ui.dayIdx === 0);
    const full = B.full, T = B.sweep ? B.sweep.ms : 0, tB = B.start.at, tC = B.lock.at, tEnd = B.end;
    const fly = B.items.map(it => Object.assign({ el: els.get(it.id) }, it)), keep = B.keep.map(id => ({ el: els.get(id), a0: ang(+els.get(id).dataset.s) }));
    const flips = B.flips.map(id => ({ el: els.get(id) })), ghosts = B.ghosts;

    /* ── phase A: the sweep ── */
    if (full) {
      sweep = document.createElement('div'); sweep.className = 'ld-bf-sweep'; sweep.setAttribute('aria-hidden', 'true'); sweep.innerHTML = '<i></i>';
      dial.insertBefore(sweep, core);
      A(sweep, [{ transform: 'rotate(0deg)' }, { transform: 'rotate(360deg)' }], { duration: T, easing: 'linear' });
      A(sweep, [{ opacity: 0 }, { opacity: 1, offset: 0.06 }, { opacity: 1, offset: 0.9 }, { opacity: 0 }], { duration: T + 160, easing: 'linear' });
      /* the fixed time is drawn exactly as the arm passes it */
      for (const p of svg.querySelectorAll('.ld-outer .ld-o, .ld-outer .ld-surface-edge')) {
        const a0 = ang(+p.dataset.s), a1 = ang(+p.dataset.e);
        let len = 0; try { len = p.getTotalLength(); } catch (e) { len = 0; }
        if (!len) continue;
        css(p, { strokeDasharray: len + ' ' + (len + 2) });
        A(p, [{ strokeDashoffset: len }, { strokeDashoffset: 0 }], { duration: Math.max(60, (a1 - a0) / 360 * T), delay: a0 / 360 * T, easing: 'linear' });
      }
      /* the bezel lights up under the arm */
      const tc = {};
      svg.querySelectorAll('.ld-tk').forEach((t, k) => {
        const c = t.getAttribute('class'); if (!tc[c]) tc[c] = getComputedStyle(t).stroke;
        A(t, [{ stroke: '#e0f2fe' }, { stroke: tc[c] }], { duration: 520, delay: k / 96 * T, easing: 'ease-out', fill: 'none' });
      });
      /* what was already there (kept sessions, meals, downtime) is revealed as the arm passes */
      const reveal = (el, a0) => A(el, [{ opacity: 0 }, { opacity: 1 }], { duration: 260, delay: a0 / 360 * T, easing: 'ease-out' });
      keep.forEach(it => reveal(it.el, it.a0));
      svg.querySelectorAll('.ld-inner .ld-meal, .ld-inner .ld-rest').forEach(el => reveal(el, ang(+el.dataset.s)));
      /* the rest of the face waits for the lock */
      for (const sel of ['.ld-words', '.ld-past', '.ld-cur', '.ld-hand']) A(svg.querySelector(sel), [{ opacity: 0 }, { opacity: 1 }], { duration: 420, delay: tC + 80, easing: 'ease-out' });
      /* live readout: the hour under the arm and what fills it */
      const t0 = performance.now(), R = B.sweep.readout;
      const read = () => {
        if (over) return;
        const f = Math.min(1, (performance.now() - t0) / T), a = f * 360;
        let w = R[0];
        for (const x of R) { if (x.a <= a) w = x; else break; }
        say3(B.sweep.kick, w.hour, w.what);
        if (f < 1) raf = requestAnimationFrame(read);
      };
      read();
    } else sayW(B.intro);

    /* ── phase B: the planner fills the gaps ── */
    at(tB, () => sayW(B.start));
    for (const g of ghosts) {
      const p = mk('path', { class: 'ld-bf-ghost', d: arcD(DIAL.RI, g.a0, g.a1), 'stroke-width': DIAL.WI });
      css(p, CEN);
      A(p, [{ opacity: 0, transform: 'scale(1) rotate(0deg)' }, { opacity: 0.9, offset: 0.2, transform: 'scale(1) rotate(0deg)' }, { opacity: 0, transform: 'scale(1.2) rotate(7deg)', strokeWidth: 2 }], { duration: 640, delay: tB, easing: 'cubic-bezier(.5,0,.9,.4)' });
    }
    let landed = 0;
    fly.forEach(it => {
      const el = it.el, mid = (it.a0 + it.a1) / 2, [px, py] = polar(DIAL.RI, mid), dur = it.ms;
      css(el, CEN);
      if (it.kind === 'new') {
        A(el, [{ transform: 'rotate(-55deg) scale(.1)', opacity: 0 }, { transform: 'rotate(5deg) scale(1.08)', opacity: 1, offset: 0.68 }, { transform: 'rotate(0deg) scale(1)', opacity: 1 }], { duration: dur, delay: it.at, easing: 'cubic-bezier(.2,.75,.25,1)' });
      } else {
        const o = it.from, sg = o > 0 ? 1 : -1;
        A(el, [{ transform: 'rotate(' + o.toFixed(2) + 'deg)', opacity: full ? 0 : 0.55 }, { transform: 'rotate(' + o.toFixed(2) + 'deg)', opacity: 0.7, offset: 0.12 }, { transform: 'rotate(' + (-sg * 2.5) + 'deg)', opacity: 1, offset: 0.82 }, { transform: 'rotate(0deg)', opacity: 1 }], { duration: dur, delay: it.at, easing: 'cubic-bezier(.45,0,.2,1)' });
        const lo = Math.min(it.a0, it.a0 + o), hi = Math.max(it.a1, it.a1 + o);
        if (hi - lo < 359) { const tr = mk('path', { class: 'ld-bf-trail', d: arcD(DIAL.RI, Math.max(0, lo), Math.min(360, hi)) }); A(tr, [{ opacity: 0 }, { opacity: 0.75, offset: 0.25 }, { opacity: 0 }], { duration: dur + 200, delay: it.at }); }
      }
      A(el, [{ filter: 'brightness(2.6)' }, { filter: 'brightness(2.6)', offset: 0.6 }, { filter: 'brightness(1)' }], { duration: dur + 380, delay: it.at, easing: 'ease-out' });
      /* touchdown: ripple, sparks, count */
      const land = it.land;
      const rip = mk('circle', { class: 'ld-bf-rip', cx: px.toFixed(1), cy: py.toFixed(1), r: 7, 'vector-effect': 'non-scaling-stroke' });
      css(rip, OWN);
      A(rip, [{ transform: 'scale(.3)', opacity: 0.95 }, { transform: 'scale(3.4)', opacity: 0 }], { duration: 620, delay: land, easing: 'cubic-bezier(.1,.6,.3,1)', fill: 'none' });
      for (let k = 0; k < 6; k++) {
        const t = (mid + (k - 2.5) * 24 - 90) * Math.PI / 180, dist = 13 + (k % 3) * 5;
        const sp = mk('circle', { class: 'ld-bf-spark', cx: px.toFixed(1), cy: py.toFixed(1), r: k % 2 ? 1.3 : 1.8 });
        A(sp, [{ transform: 'translate(0px,0px)', opacity: 0 }, { opacity: 1, offset: 0.08 }, { transform: 'translate(' + (Math.cos(t) * dist).toFixed(1) + 'px,' + (Math.sin(t) * dist).toFixed(1) + 'px)', opacity: 0 }], { duration: 480, delay: land, easing: 'cubic-bezier(.15,.7,.3,1)', fill: 'none' });
      }
      at(land, () => { landed++; hn.textContent = String(landed); play(hn, 'ld-bh-tick'); bfBuzz(7); });
    });
    flips.forEach(it => A(it.el, [{ filter: 'brightness(1)' }, { filter: 'brightness(2.2)', offset: 0.3 }, { filter: 'brightness(1)' }], { duration: 700, delay: tB }));

    /* ── phase C: lock in ── */
    at(tC, () => {
      sayW(B.lock);
      hud.classList.add('ld-bh-lock'); bfBuzz(full ? [10, 40, 18] : 12);
    });
    const wave = mk('circle', { class: 'ld-bf-wave', cx: 180, cy: 180, r: 152, 'vector-effect': 'non-scaling-stroke' });
    css(wave, CEN);
    A(wave, [{ transform: 'scale(.45)', opacity: 0.85 }, { transform: 'scale(1.16)', opacity: 0 }], { duration: 760, delay: tC, easing: 'cubic-bezier(.1,.6,.3,1)', fill: 'none' });
    A(svg, [{ transform: 'scale(1)' }, { transform: 'scale(1.025)', offset: 0.3 }, { transform: 'scale(1)' }], { duration: 440, delay: tC, easing: 'ease-out', fill: 'none' });
    /* A small stagger in the stationary surfaces makes the existing lock-in
       read as assembled layers. Nothing rotates away from its time position. */
    svg.querySelectorAll('.ld-depth-chapter, .ld-depth-hub').forEach((el, k) => {
      css(el, CEN);
      A(el, [{ transform: 'scale(.992)', opacity: .7 }, { transform: 'scale(1)', opacity: 1 }], { duration: 440, delay: tC + k * 65, easing: 'cubic-bezier(.22,1,.36,1)', fill: 'none' });
    });
    const hand = svg.querySelector('.ld-hand');
    if (full && hand) {
      const an = +((/rotate\(([-\d.]+)/.exec(hand.getAttribute('transform') || '') || [0, 0])[1]);
      css(hand, CEN);
      A(hand, [{ transform: 'rotate(0deg)' }, { transform: 'rotate(' + (an + 4).toFixed(2) + 'deg)', offset: 0.8 }, { transform: 'rotate(' + an.toFixed(2) + 'deg)' }], { duration: 760, delay: tC + 80, easing: 'cubic-bezier(.3,.8,.3,1)' });
    }
    if (full) svg.querySelectorAll('.ld-hds .ld-hd').forEach((h, k) => { css(h, OWN); A(h, [{ transform: 'scale(0)' }, { transform: 'scale(1.3)', offset: 0.65 }, { transform: 'scale(1)' }], { duration: 420, delay: tC + 200 + k * 70, easing: 'ease-out' }); });
    /* the centre: the build readout hands back to the live centre */
    A(core, [{ opacity: 0, transform: 'translate(-50%,-50%) scale(.9)' }, { opacity: 1, transform: 'translate(-50%,-50%) scale(1)' }], { duration: 320, delay: tEnd - 320, easing: 'cubic-bezier(.2,.8,.2,1)' });
    const o1 = Math.max(0.1, (tEnd - 560) / tEnd), o2 = Math.max(o1 + 0.01, (tEnd - 330) / tEnd);
    A(hud, [{ opacity: 0, transform: 'translate(-50%,-50%) scale(.9)', offset: 0 }, { opacity: 1, transform: 'translate(-50%,-50%) scale(1)', offset: Math.min(0.05, o1 / 2) }, { opacity: 1, transform: 'translate(-50%,-50%) scale(1)', offset: o1 }, { opacity: 0, transform: 'translate(-50%,-50%) scale(1.08)', offset: o2 }, { opacity: 0, transform: 'translate(-50%,-50%) scale(1.08)', offset: 1 }], { duration: tEnd, easing: 'linear' });

    /* ── end, skip, or interrupted by a redraw ── */
    function stop() {
      if (over) return; over = true;
      timers.forEach(clearTimeout); cancelAnimationFrame(raf);
      anims.forEach(a => { try { a.cancel(); } catch (e) { /* ignore */ } });
      styled.forEach(el => { el.style.transformBox = ''; el.style.transformOrigin = ''; el.style.strokeDasharray = ''; });
      layer.remove(); hud.remove(); if (sweep) sweep.remove();
      dial.classList.remove('ld-building');
      dial.removeEventListener('pointerdown', skip, true);
      if (ui.bfRun === run) ui.bfRun = null;
    }
    function skip(e) {
      e.stopPropagation(); e.preventDefault();
      /* swallow the click this tap would make, so skipping never opens a session */
      const eat = ev => { ev.stopPropagation(); ev.preventDefault(); };
      dial.addEventListener('click', eat, { capture: true, once: true });
      setTimeout(() => dial.removeEventListener('click', eat, true), 450);
      finish();
    }
    function finish() { stop(); if (!ui.tour && root && root.isConnected) tourAuto(); }
    dial.addEventListener('pointerdown', skip, true);
    const run = { stop };
    ui.bfRun = run;
    at(tEnd + 20, finish);
  }
  /* the centre of the dial: what you should be doing right now, how long is left,
     what comes next, and a warning when something is ending or about to start */
  function nextUp(now) {
    const s = ST.schedule;
    return s ? s.scheduledInstances.filter(i => i.lifecycleState === 'PLANNED' && i.endEpoch > now).sort((a, b) => a.startEpoch - b.startEpoch)[0] || null : null;
  }
  function relText(sec) { const m = Math.max(0, Math.round(sec / 60)); return m < 1 ? 'now' : m < 60 ? m + 'm' : Math.floor(m / 60) + 'h' + (m % 60 ? ' ' + (m % 60) + 'm' : ''); }
  const leftText = sec => sec < 60 ? 'under a minute' : relText(sec);
  /* "7h 55m" with the numbers large and the units small */
  function bigTime(sec) {
    const m = Math.max(0, Math.round(sec / 60));
    if (m < 1) return '<span class="ld-bt">&lt;1<i>m</i></span>';
    const h = Math.floor(m / 60), r = m % 60;
    return '<span class="ld-bt">' + (h ? h + '<i>h</i>' + (r ? ' ' + r + '<i>m</i>' : '') : r + '<i>m</i>') + '</span>';
  }
  const SOON = 15 * 60, ENDING = 10 * 60, LATE = 10 * 60;
  /* today and tomorrow as one list, with pieces split at midnight joined back up */
  function liveTimeline(now) {
    const s = ST.schedule; if (!s) return [];
    const days = horizonDays(s, tz());
    const all = [];
    for (const d of days.slice(0, 2)) for (const b of dayBlocks(s, d, tz())) all.push(Object.assign({}, b));
    all.sort((a, b) => a.startEpoch - b.startEpoch);
    const out = [];
    for (const b of all) {
      const p = out[out.length - 1];
      if (p && !p.inst && !b.inst && p.code === b.code && p.title === b.title && b.startEpoch <= p.endEpoch + 60) { p.endEpoch = Math.max(p.endEpoch, b.endEpoch); continue; }
      out.push(b);
    }
    return out;
  }
  /* Meal blocks retain their original scheduling identity. Recipes are read
     from Smart Diet at display/tap time, including swaps and diet preferences. */
  function scheduledMealRef(b) {
    const start = b.mealStartEpoch || b.startEpoch;
    return { title: b.title, startEpoch: start, day: U.ymdKey(U.localParts(start, tz())) };
  }
  function scheduledMeal(b) {
    if (b.inst || blockClass(b) !== 'meal') return null;
    try { return window.LifeMaxMeals && LifeMaxMeals.resolveScheduled(scheduledMealRef(b)); } catch (e) { return null; }
  }
  function mealAnchor(b) {
    return ' data-act="anchor" data-k="meal" data-t="' + esc(b.title) + '" data-s="' + b.startEpoch + '" data-e="' + b.endEpoch + '" data-ms="' + (b.mealStartEpoch || b.startEpoch) + '" role="button" tabindex="0"';
  }
  function openScheduledMeal(d) {
    if (!window.LifeMaxMeals || typeof LifeMaxMeals.openScheduled !== 'function') return false;
    try { return LifeMaxMeals.openScheduled(scheduledMealRef({title: d.t, startEpoch: +d.s, mealStartEpoch: +d.ms || +d.s})); }
    catch (e) { console.warn('[Life Director] meal link', e); say('Could not open this meal. Try Food → Plan.'); return true; }
  }
  /* a planned meal block b in the page's words, with Smart Diet's recipe when it has one: the page's meal sheet and a native screen's */
  function mealModel(b) {
    const meal = scheduledMeal(b), s = b.startEpoch, e = b.endEpoch;
    return { start: b.mealStartEpoch || s, title: meal ? meal.name : 'Your meal plan', when: fDay(s) + ' · ' + fTime(s) + '–' + fTime(e) + ' · ' + mins(Math.round((e - s) / 60)),
      chips: [{ kind: 'meal', text: b.title || 'Meal' }].concat(meal ? [{ kind: 'kcal', text: meal.kcal + ' kcal' }, { kind: 'state', text: meal.logged ? 'Logged' : meal.skipped ? 'Skipped' : 'On your plan' }] : []),
      open: 'Open meal menu' };
  }
  /* the meal block a native screen names by its first minute (epoch seconds), as its arc and row name it */
  const mealSegment = start => ST.schedule && (ST.schedule.segments || []).find(g => g.kind === 'MEAL' && String(g.startEpoch) === String(start));
  function mealPreview(d) {
    const b = { title: d.t, startEpoch: +d.s, endEpoch: +d.e, mealStartEpoch: +d.ms || +d.s, kind: 'MEAL' };
    const m = mealModel(b);
    const attrs = mealAnchor(b).replace('data-act="anchor"', 'data-act="mealOpen"').replace(' role="button" tabindex="0"', '');
    openSheet('<div class="ld-meal-preview"' + attrs + '><div class="ld-sh-head ld-c-meal"><i class="ld-rail" aria-hidden="true"></i><div><h3>' + mealPx(16) + ' ' + esc(m.title) + '</h3>' +
      '<p class="ld-when">' + m.when + '</p></div></div>' +
      '<div class="ld-chips">' + m.chips.map(c => '<span class="ld-chip">' + esc(c.text) + '</span>').join('') + '</div>' +
      '<div class="ld-row ld-sh-main"><button type="button" class="ld-btn ld-primary"' + attrs + '>' + m.open + '</button></div></div>');
  }
  /* what to call the thing you are doing */
  function doingName(b) {
    if (b.inst) return b.inst.title;
    const k = blockClass(b);
    if (k === 'meal') return b.title || 'Meal';
    if (k === 'free') return 'Free time';
    if (k === 'rest') return 'Downtime';
    if (b.kind === 'SLEEP' || k === 'sleep') return 'Sleeping';
    if (b.kind === 'WORK' && /^work$/i.test(b.title || '')) return 'Working';
    if (b.kind === 'CLASS' && /^(class|school)/i.test(b.title || '')) return 'In class';
    return b.title || 'Busy';
  }
  function nowState(now) {
    const tl = liveTimeline(now);
    const live = b => !(b.inst && sesState(b.inst.lifecycleState) === 'off');
    const cur = tl.find(b => b.inst && live(b) && b.startEpoch <= now && b.endEpoch > now) || tl.find(b => !b.inst && b.startEpoch <= now && b.endEpoch > now) || null;
    const after = cur ? cur.endEpoch : now;
    const next = tl.find(b => b !== cur && b.startEpoch >= after - 60 && blockClass(b) !== 'free' && live(b) && !(b.inst && sesState(b.inst.lifecycleState) === 'done')) || null;
    const nextTask = tl.find(b => b.inst && b !== cur && b.startEpoch > now && b.inst.lifecycleState === 'PLANNED') || null;
    const isSes = cur && cur.inst && cur.inst.lifecycleState === 'PLANNED';
    const f = ST.ui.focus, running = f && !f.paused && cur && cur.inst && f.id === cur.inst.instanceId;
    /* two separate warnings: one about now (top line), one about what's coming (next line) */
    let kick = null;
    if (isSes && !running && !f && now - cur.startEpoch >= LATE) kick = { kind: 'late', sec: now - cur.startEpoch };
    else if (cur && cur.endEpoch - now <= ENDING && next && blockClass(cur) !== 'free') kick = { kind: 'ending', sec: cur.endEpoch - now };
    const soon = nextTask && nextTask.startEpoch - now <= SOON ? { kind: 'soon', b: nextTask, sec: nextTask.startEpoch - now } : null;
    return { cur, next, nextTask, kick, soon, alert: kick || soon, isSes, running };
  }
  /* the depleting ring: how much of what you are doing is left */
  function curRingHtml(st, now) {
    if (!st.cur) return '';
    const span = Math.max(60, st.cur.endEpoch - st.cur.startEpoch), frac = Math.min(1, Math.max(0, (now - st.cur.startEpoch) / span));
    const k = st.cur.inst ? 'task' : blockClass(st.cur), R = 84;
    return '<circle class="ld-cur-trk" cx="180" cy="180" r="' + R + '"/>' + (frac < 0.997 ? '<path class="ld-cur-arc ld-c-' + k + (st.kick ? ' ld-warn' : '') + '" d="' + arcD(R, frac * 360, 360) + '"/>' : '');
  }
  function coreToday(day, blocks, now) {
    const f = ST.ui.focus, fi = f && !f.paused && findInst(f.id);
    const st = nowState(now);
    let k = '', title = '', big = '', sub = '', btn = '', cls = '';
    if (fi) {
      k = '<small class="ld-core-k ld-core-go"><i class="ld-live"></i>Timer running</small>';
      title = fi.title.replace(/^Plan:\s*/i, '').replace(/\s*\([^)]*\)\s*$/, '').replace(/&amp;/gi, '&').trim(); big = '<span class="ld-core-big" data-fclock data-act="focusOpen" role="button" tabindex="0" aria-label="Open running timer">' + clockText(focusMs(f)) + '</span>';
      sub = 'of ' + mins(Math.round((fi.endEpoch - fi.startEpoch) / 60)) + ' planned';
      btn = '';
    } else if (st.cur) {
      const c = st.cur, cc = c.inst ? 'task' : blockClass(c), done = c.inst && sesState(c.inst.lifecycleState) === 'done';
      const left = c.endEpoch - now;
      let kick = done ? '✓ Done' : 'Now';
      if (st.kick && st.kick.kind === 'late') { kick = 'Late by ' + relText(st.kick.sec); cls = ' ld-core-warn'; }
      else if (st.kick && st.kick.kind === 'ending') { kick = 'Wrapping up'; cls = ' ld-core-warn'; }
      k = '<small class="ld-core-k ld-c-' + cc + '"><i class="ld-dotk"></i>' + kick + '</small>';
      title = done ? c.inst.title : doingName(c);
      big = '<span class="ld-core-big">' + bigTime(left) + '</span>';
      sub = (done ? 'free until ' : 'left · until ') + fC(c.endEpoch);
      if (st.isSes) btn = '<button class="ld-core-btn ld-core-btn-go" data-act="focusStart" data-id="' + esc(c.inst.instanceId) + '">Start</button>';
      else if (c.inst) btn = '<button class="ld-core-btn" data-act="inst" data-id="' + esc(c.inst.instanceId) + '">Details</button>';
      else if (cc === 'meal' && window.LifeMaxMeals) btn = '<button class="ld-core-btn"' + mealAnchor(c) + '>View meal</button>';
    } else {
      k = '<small class="ld-core-k">Now</small>'; title = 'Free time'; big = ''; sub = '';
    }
    let nx = '';
    if (st.soon && !(st.cur && st.cur.inst && st.soon.b === st.cur)) {
      const b = st.soon.b;
      nx = '<div class="ld-core-nx ld-core-soon" data-act="inst" data-id="' + esc(b.inst.instanceId) + '"><span><i class="ld-live"></i>' + (st.soon.sec < 90 ? 'Starting now' : 'Starting in ' + relText(st.soon.sec)) + '</span><b>' + esc(b.inst.title) + '</b></div>';
      if (!fi && !st.isSes) btn = '<button class="ld-core-btn ld-core-btn-go" data-act="focusStart" data-id="' + esc(b.inst.instanceId) + '">Start early</button>';
    } else if (st.next) {
      const b = st.next, same = U.ymdKey(U.localParts(b.startEpoch, tz())) === U.ymdKey(U.localParts(now, tz()));
      nx = '<div class="ld-core-nx"' + (!b.inst && blockClass(b) === 'meal' ? mealAnchor(b) : '') + '><span>Next' + (b.startEpoch - now < 3 * 3600 ? ' in ' + relText(b.startEpoch - now) : ' · ' + (same ? '' : 'Tomorrow ') + fC(b.startEpoch)) + '</span><b>' + esc(doingName(b)) + '</b></div>';
    } else nx = fi ? '' : '<div class="ld-core-nx"><span>Nothing else planned today</span></div>';
    return '<div class="ld-core-in' + cls + (btn ? ' ld-has-btn' : '') + '" data-kick="' + (st.kick ? st.kick.kind : '') + '" data-soon="' + (st.soon ? 1 : '') + '">' + k + '<b class="ld-core-t">' + esc(title) + '</b>' + big + (sub ? '<span class="ld-core-s">' + sub + '</span>' : '') + nx + btn + '</div>';
  }
  function coreHtml(day, blocks, isToday, now) {
    if (isToday) return coreToday(day, blocks, now);
    const tasks = blocks.filter(b => b.inst), taskMin = tasks.reduce((n, b) => n + Math.round((b.endEpoch - b.startEpoch) / 60), 0);
    const first = tasks.find(b => b.inst.lifecycleState === 'PLANNED');
    return '<small class="ld-core-k">' + esc(fmt({ weekday: 'long' }).format(new Date(day.startEpoch * 1000))) + '</small><b class="ld-core-t ld-core-date">' + esc(fmt({ month: 'short', day: 'numeric' }).format(new Date(day.startEpoch * 1000))) + '</b>' +
      '<span class="ld-core-s">' + (tasks.length ? tasks.length + ' session' + (tasks.length === 1 ? '' : 's') + ' · ' + mins(taskMin) : 'No sessions planned') + '</span>' +
      (first ? '<div class="ld-core-nx"><span>First · ' + fC(first.startEpoch) + '</span><b>' + esc(first.inst.title) + '</b></div>' : '');
  }
  /* one short buzz when a session is 5 minutes away (once per session, only while you're looking) */
  function alertBuzz(now) {
    if (document.hidden) return;
    const st = nowState(now), a = st.soon;
    if (!a || a.sec > 300) return;
    ui.buzzed = ui.buzzed || {};
    const id = a.b.inst.instanceId; if (ui.buzzed[id]) return;
    ui.buzzed[id] = 1;
    try { if (navigator.vibrate) navigator.vibrate([70, 60, 70]); } catch (e) { /* ignore */ }
  }
  /* the day mix: doubles as the legend for the dial's colours */
  function mixHtml(blocks) {
    const sum = { sleep: 0, work: 0, maint: 0, meal: 0, task: 0, rest: 0, free: 0 };
    for (const b of blocks) { const k = blockClass(b), m = (b.endEpoch - b.startEpoch) / 60; const key = k === 'commute' || k === 'block' ? 'work' : k; if (sum[key] != null) sum[key] += m; }
    const L = [['sleep', 'Sleep'], ['work', 'Work & travel'], ['maint', 'Routines'], ['meal', 'Meals'], ['task', 'Sessions'], ['rest', 'Downtime'], ['free', 'Free']];
    const tot = Object.values(sum).reduce((a, b) => a + b, 0) || 1;
    return '<div class="ld-mix"><div class="ld-mix-bar" aria-hidden="true">' + L.map(([k]) => sum[k] ? '<i class="ld-c-' + k + '" style="flex:' + sum[k].toFixed(0) + '"></i>' : '').join('') + '</div>' +
      '<ul class="ld-mix-l">' + L.map(([k, l]) => '<li class="ld-c-' + k + '"><i></i>' + l + '<b>' + (sum[k] ? mins(Math.round(sum[k] / 15) * 15) : '—') + '</b></li>').join('') + '</ul>' +
      '<p class="ld-sr">' + L.map(([k, l]) => l + ' ' + Math.round(sum[k] / tot * 100) + '%').join(', ') + '</p></div>';
  }
  function stripHtml(days, sel, act, s) {
    return '<div class="ld-strip" role="tablist" aria-label="Days">' + days.map((d, k) => {
      const insts = s ? dayBlocks(s, d, tz()).filter(b => b.inst).map(b => b.inst) : [];
      const dots = insts.slice(0, 5).map(i => '<i class="ld-s-' + sesState(i.lifecycleState) + '"></i>').join('');
      return '<button class="ld-chipday' + (k === sel ? ' on' : '') + '" role="tab" aria-selected="' + (k === sel) + '" data-act="' + act + '" data-i="' + k + '"><span>' + (k === 0 && act === 'day' ? 'Today' : DOW[d.dow]) + '</span><b>' + d.ymd.d + '</b><em>' + dots + '</em></button>';
    }).join('') + '</div>';
  }
  /* projected XP from LifeMax's own formula, so the number matches what you will get */
  function xpOf(inst, minutes) {
    if (!Host.connected() || typeof Host.xpFor !== 'function') return null;
    try { return Host.xpFor(Host.typeForTag(inst.tag), minutes || Math.round((inst.endEpoch - inst.startEpoch) / 60), 1, U.ymdKey(U.localParts(inst.startEpoch, tz()))); } catch (e) { return null; }
  }
  const attrName = a => a ? a.charAt(0).toUpperCase() + a.slice(1) : '';
  function xpChip(inst) { const x = xpOf(inst); return x ? '<span class="ld-chip ld-chip-xp">+' + x.xp + ' XP · ' + esc(attrName(x.rule.primary)) + '</span>' : ''; }
  /* the running order under the dial */
  function roHtml(blocks, z, opts) {
    opts = opts || {};
    const now = nowEpoch();
    return '<ol class="ld-ro' + (opts.cls ? ' ' + opts.cls : '') + '">' + blocks.map(b => {
      const k = blockClass(b), m = Math.round((b.endEpoch - b.startEpoch) / 60);
      if (k === 'free' && m < 15) return '';
      const st = b.inst ? b.inst.lifecycleState : '';
      const isNow = !opts.lab && b.startEpoch <= now && b.endEpoch > now, past = !opts.lab && b.endEpoch <= now;
      const fx = placeFx(b, opts.lab), justDone = b.inst && ui.fx.justDone === b.inst.instanceId ? ' ld-justdone' : '';
      const stl = st && STATE_LABEL[st] ? '<span class="ld-state ld-st-' + sesState(st) + '">' + STATE_LABEL[st] + '</span>' : '';
      let chips = '';
      if (b.inst) {
        const q = b.inst.linkedQuestRequirementIds.length ? '<span class="ld-chip ld-chip-quest">' + icon('flag', 10) + ' ' + esc(b.inst.linkedQuestRequirementIds.map(l => l.questTitle).join(', ')) + '</span>' : '';
        chips = '<div class="ld-chips">' + q + (b.inst.goalTitle ? '<span class="ld-chip">' + esc(b.inst.goalTitle) + '</span>' : '') + (st === 'PLANNED' && !opts.lab ? xpChip(b.inst) : '') + (b.inst.isUserLocked ? '<span class="ld-chip">' + icon('lock', 10) + ' Pinned</span>' : '') + '</div>';
      }
      const meal = !opts.lab && k === 'meal' ? scheduledMeal(b) : null;
      const sub = meal ? '<div class="ld-ri-sub ld-meal-recipe">' + esc(meal.name) + ' · ' + (meal.logged ? 'Logged' : meal.skipped ? 'Skipped' : meal.kcal + ' kcal') + ' · View meal ›</div>' : !b.inst && k === 'rest' ? '<div class="ld-ri-sub">Kept clear. Nothing gets booked here.</div>' : '';
      const attrs = b.inst ? ' data-act="' + (opts.lab ? 'labInst' : 'inst') + '" data-id="' + esc(b.inst.instanceId) + '" role="button" tabindex="0"' : !opts.lab && k === 'meal' ? mealAnchor(b) : '';
      return '<li class="ld-ri ld-c-' + k + (b.inst ? ' ld-ri-ses ld-s-' + sesState(st) : '') + (isNow ? ' ld-now' : '') + (past ? ' ld-past' : '') + fx.cls + justDone + '"' + attrs + (fx.tag ? ' data-fx="' + fx.tag + '"' : '') + '>' +
        '<time><b>' + fTime(b.startEpoch, z) + '</b><small>' + mins(m) + '</small></time><i class="ld-rail" aria-hidden="true"></i>' +
        '<div class="ld-ri-b"><div class="ld-ri-t">' + (b.inst ? icon(tagIcon(b.inst.tag), 13) : kindIcon(k, 13)) + '<span>' + esc(b.title) + '</span>' + stl + (isNow ? '<span class="ld-nowtag">Now</span>' : '') + '</div>' + chips + sub + '</div></li>';
    }).join('') + '</ol>';
  }
  /* the first-run card, decided once for the page (needSetup) and a native screen (nativeFirstRun): the demo dial's arcs in minutes
     after midnight (an arc past midnight ends after 1440), its words, and its button, which opens My time */
  const FIRST_RUN = Object.freeze({
    arcs: [{ ring: 'outer', kind: 'sleep', from: 1380, to: 1800 }, { ring: 'outer', kind: 'work', from: 544, to: 1024 },
      { ring: 'inner', kind: 'session', from: 1120, to: 1200 }, { ring: 'inner', kind: 'session', from: 420, to: 480 }],
    title: 'Tell it when you’re busy. It plans the rest.',
    text: 'Add your sleep and your work or school hours. Life Director fits your goals and quests into the time that’s left and keeps downtime protected. You never have to build the day yourself.',
    button: 'Set up my time', section: 'setup',
  });
  function needSetup() {
    const F = FIRST_RUN, arc = a => a.ring === 'outer' ? '<path class="ld-o ld-c-' + a.kind + '" d="' + arcD(DIAL.RO, a.from / 4, a.to / 4) + '" stroke-width="' + DIAL.WO + '"/>'
      : '<path class="ld-sa ld-ses-demo" d="' + arcD(DIAL.RI, a.from / 4, a.to / 4) + '" stroke-width="' + DIAL.WI + '"/>';
    return '<div class="ld-empty ld-first"><div class="ld-first-dial" aria-hidden="true"><svg viewBox="0 0 360 360">' + faceHtml() + F.arcs.map(arc).join('') + '</svg></div>' +
      '<h3>' + F.title + '</h3><p>' + F.text + '</p><button class="ld-btn ld-primary ld-wide" data-act="tab" data-tab="' + F.section + '">' + F.button + '</button></div>';
  }
  function renderToday() {
    if (!ST.setupComplete || !ST.schedule) return needSetup();
    const s = ST.schedule, days = horizonDays(s, tz());
    ui.dayIdx = Math.min(ui.dayIdx, days.length - 1);
    const day = days[ui.dayIdx], blocks = dayBlocks(s, day, tz());
    const tasks = blocks.filter(b => b.inst);
    const now = nowEpoch(), isToday = ui.dayIdx === 0;
    const next = isToday ? nextUp(now) : null;
    const reminders = (s.passiveReminders || []).length && isToday ? '<div class="ld-card ld-remind"><h4>' + icon('flag', 12) + ' All-day quests</h4><p>' + s.passiveReminders.map(r => esc(reminderText(r))).join(', ') + '</p></div>' : '';
    let upnext = '';
    const f = ST.ui.focus, fi = f && findInst(f.id);
    if (isToday && fi) {
      upnext = '<section class="ld-up ld-up-go"><div class="ld-up-h"><span class="ld-cue"></span>' + (f.paused ? 'Timer paused' : 'Timer running') + '</div><h3>' + esc(fi.title) + '</h3><p class="ld-up-when"><b data-fclock>' + clockText(focusMs(f)) + '</b> of ' + mins(Math.round((fi.endEpoch - fi.startEpoch) / 60)) + ' planned</p>' +
        '<div class="ld-row"><button class="ld-btn ld-primary" data-act="focusOpen">Open timer</button><button class="ld-btn" data-act="focusFinish">Finish and log</button></div></section>';
    } else if (next && !doable(next, now)) {
      const di = days.findIndex(d => next.startEpoch >= d.startEpoch && next.startEpoch < d.endEpoch);
      upnext = '<section class="ld-up ld-up-rest"><div class="ld-up-h"><span class="ld-cue"></span>That’s everything for today</div><h3>Next: ' + esc(next.title) + '</h3>' +
        '<p class="ld-up-when">' + fDay(next.startEpoch) + ', ' + fTime(next.startEpoch) + '–' + fTime(next.endEpoch) + '</p>' +
        (di > 0 ? '<div class="ld-row"><button class="ld-btn" data-act="gotoDay" data-i="' + di + '">See ' + (di === 1 ? 'tomorrow' : DOW[days[di].dow]) + '</button></div>' : '') + '</section>';
    } else if (next) {
      const on = next.startEpoch <= now, quests = next.linkedQuestRequirementIds.map(l => l.questTitle);
      upnext = '<section class="ld-up' + (on ? ' ld-up-go' : '') + '"><div class="ld-up-h"><span class="ld-cue"></span>' + (on ? 'On now · ' + relText(next.endEpoch - now) + ' left' : 'Up next · in ' + relText(next.startEpoch - now)) + '</div>' +
        '<h3>' + esc(next.title) + '</h3><p class="ld-up-when">' + (next.startEpoch >= day.endEpoch ? fDay(next.startEpoch) + ', ' : '') + fTime(next.startEpoch) + '–' + fTime(next.endEpoch) + ' · ' + mins(next.durationSlots * 15) + '</p>' +
        '<div class="ld-chips">' + (quests.length ? '<span class="ld-chip ld-chip-quest">' + icon('flag', 10) + ' ' + esc(quests.join(', ')) + '</span>' : '') + xpChip(next) + '</div>' +
        '<div class="ld-row"><button class="ld-btn ld-primary" data-act="focusStart" data-id="' + esc(next.instanceId) + '">Start</button><button class="ld-btn" data-act="doneQuick" data-id="' + esc(next.instanceId) + '">Done</button><button class="ld-btn ld-ghost" data-act="inst" data-id="' + esc(next.instanceId) + '">Details</button></div></section>';
    }
    const past = isToday ? blocks.filter(b => b.endEpoch <= now) : [];
    const ahead = past.length >= 2 ? blocks.filter(b => b.endEpoch > now) : blocks;
    let earlier = '';
    if (past.length >= 2) {
      const pt = past.filter(b => b.inst), pd = pt.filter(b => /COMPLETED/.test(b.inst.lifecycleState)).length;
      earlier = '<details class="ld-earlier"' + (ui.earlierOpen ? ' open' : '') + '><summary data-act="earlier"><b>Earlier today</b><span>' + (pt.length ? pd + ' of ' + pt.length + ' sessions done' : past.length + ' blocks') + '</span></summary>' + roHtml(past, tz(), { cls: 'ld-ro-past' }) + '</details>';
    }
    return stripHtml(days, ui.dayIdx, 'day', s) + dialHtml(day, blocks, isToday, now) +
      '<p class="ld-dial-hint">' + DIAL_HINT[0] + ' <i class="ld-hint-pip"></i> ' + DIAL_HINT[1] + '</p>' +
      mixHtml(blocks) + upnext + reminders +
      '<h4 class="ld-sec">' + (isToday ? 'Rest of today' : 'Running order') + (tasks.length ? '<small>' + tasks.length + ' session' + (tasks.length === 1 ? '' : 's') + '</small>' : '') + '</h4>' +
      earlier + roHtml(ahead, tz()) +
      '<div class="ld-row ld-foot"><button class="ld-btn" data-act="disrupt">Something came up</button><button class="ld-btn ld-ghost" data-act="replan">Re-plan from now</button></div>';
  }
  /* the week: every day on one 24-hour scale. A day's bar as data: each block that is not free time, its kind (a session
     with its state) and where it sits, in percent of the day, as the bar draws it */
  function weekSegs(day, blocks) {
    const d0 = day.startEpoch, d1 = day.endEpoch, span = Math.max(1, d1 - d0), out = [];
    for (const b of blocks) {
      const k = b.inst ? 'task' : blockClass(b);
      if (k === 'free') continue;
      const l = (Math.max(d0, b.startEpoch) - d0) / span * 100, w = (Math.min(d1, b.endEpoch) - Math.max(d0, b.startEpoch)) / span * 100;
      if (w > 0) out.push({ kind: k, state: b.inst ? sesState(b.inst.lifecycleState) : '', left: l, width: w });
    }
    return out;
  }
  function weekBar(day, blocks) {
    return '<div class="ld-wbar">' + weekSegs(day, blocks).map(g => '<i class="ld-c-' + g.kind + (g.state ? ' ld-s-' + g.state : '') + '" style="left:' + g.left.toFixed(2) + '%;width:' + g.width.toFixed(2) + '%"></i>').join('') + '</div>';
  }
  const WEEK_LEGEND = [['sleep', 'Sleep'], ['work', 'Work & travel'], ['maint', 'Routines'], ['meal', 'Meals'], ['task', 'Sessions'], ['rest', 'Downtime']];
  const WEEK_TICKS = ['00', '06', '12', '18', '24'];
  /* everything Week shows, decided once for the page's Week and the native one (nativeWeek) */
  function weekModel() {
    const s = ST.schedule, days = horizonDays(s, tz());
    let nSes = 0, nDone = 0, minTot = 0, xpTot = 0;
    const byAttr = {};
    const rows = days.map((d, k) => {
      const blocks = dayBlocks(s, d, tz()), insts = blocks.filter(b => b.inst).map(b => b.inst);
      const done = insts.filter(i => sesState(i.lifecycleState) === 'done').length;
      const m = insts.reduce((n, i) => n + Math.round((i.endEpoch - i.startEpoch) / 60), 0);
      nSes += insts.length; nDone += done; minTot += m;
      for (const i of insts) {
        if (i.lifecycleState !== 'PLANNED' && sesState(i.lifecycleState) !== 'done') continue;
        const x = xpOf(i, i.actualMinutes || null); if (!x) continue;
        xpTot += x.xp;
        for (const sp of x.split) { const a = byAttr[sp.attr] = byAttr[sp.attr] || { plan: 0, done: 0 }; a[i.lifecycleState === 'PLANNED' ? 'plan' : 'done'] += sp.xp; }
      }
      return { k, d, blocks, n: insts.length, done, m, name: k === 0 ? 'Today' : DOW[d.dow], aria: (k === 0 ? 'Today' : DOW[d.dow] + ' ' + d.ymd.d) + ': ' + insts.length + ' sessions, ' + mins(m) };
    });
    const tot = s.capacity.reduce((a, c) => ({ free: a.free + c.freeMin, rest: a.rest + c.recoveryMin }), { free: 0, rest: 0 });
    const q = s.validation.qualityMetrics;
    const colOf = a => (typeof ATTR_COL !== 'undefined' && ATTR_COL && ATTR_COL[a]) || '#10B981';
    const attrs = Object.entries(byAttr).sort((a, b) => (b[1].plan + b[1].done) - (a[1].plan + a[1].done));
    const maxA = Math.max(1, ...attrs.map(([, v]) => v.plan + v.done));
    return {
      s, rows, colOf, attrs, maxA,
      stats: [[String(nSes), 'sessions' + (nDone ? ', ' + nDone + ' done' : '')], [mins(minTot), 'planned'], [xpTot ? '≈' + xpTot : '—', 'XP on the table']],
      fitText: mins(tot.rest) + ' of protected downtime and ' + mins(tot.free) + ' left completely free. ' + Math.round(q.coverageRequired * 100) + '% of must-do sessions (weekly minimums and quests) are placed.',
    };
  }
  const YIELD_NOTE = 'Estimated with LifeMax’s own XP rules. Solid is earned, outlined is still planned.';
  function renderWeek() {
    if (!ST.setupComplete || !ST.schedule) return needSetup();
    const W = weekModel();
    const rows = W.rows.map(r =>
      '<button class="ld-wrow' + (r.k === 0 ? ' ld-wtoday' : '') + '" data-act="gotoDay" data-i="' + r.k + '" aria-label="' + esc(r.aria) + '">' +
        '<span class="ld-wd"><b>' + r.name + '</b><small>' + r.d.ymd.d + '</small></span>' + weekBar(r.d, r.blocks) +
        '<span class="ld-wst"><b>' + r.n + (r.done ? '<i>✓' + r.done + '</i>' : '') + '</b><small>' + (r.m ? mins(r.m) : '—') + '</small></span></button>').join('');
    const ticks = '<div class="ld-wticks" aria-hidden="true"><span></span><div>' + WEEK_TICKS.map((h, k) => '<b style="left:' + (k * 25) + '%">' + h + '</b>').join('') + '</div><span></span></div>';
    const yieldCard = W.attrs.length ? '<div class="ld-card ld-card-yield"><h4>Where your XP goes</h4><p class="ld-muted">' + YIELD_NOTE + '</p><ul class="ld-yield">' +
      W.attrs.map(([a, v]) => '<li style="--a:' + W.colOf(a) + '"><span>' + esc(attrName(a)) + '</span><div><i class="ld-y-done" style="width:' + (v.done / W.maxA * 100).toFixed(1) + '%"></i><i class="ld-y-plan" style="width:' + (v.plan / W.maxA * 100).toFixed(1) + '%"></i></div><b>' + Math.round(v.done + v.plan) + '</b></li>').join('') + '</ul></div>' : '';
    return '<div class="ld-stats">' + W.stats.map(([v, l]) => '<div><b>' + v + '</b><small>' + l + '</small></div>').join('') + '</div>' +
      '<div class="ld-week">' + ticks + rows + '</div>' +
      '<ul class="ld-mix-l ld-legend">' + WEEK_LEGEND.map(([k, l]) => '<li class="ld-c-' + k + '"><i></i>' + l + '</li>').join('') + '</ul>' +
      yieldCard +
      '<div class="ld-card ld-card-fit"><h4>How the week fits</h4><p>' + W.fitText + '</p>' + unscheduledHtml(W.s) + '</div>';
  }
  const ALL_FIT = 'Everything requested fits this week.';
  /* the sessions the engine could not place, grouped by what and why, most important tier first */
  function unscheduledGroups(s) {
    const list = (s.unscheduledDiagnostics || []).filter(d => d.tier);
    const groups = {};
    for (const d of list) { const k = d.title + '|' + d.reasonCode; (groups[k] = groups[k] || { d, n: 0 }).n++; }
    return { count: list.length, groups: Object.values(groups).sort((a, b) => (b.d.tier || 0) - (a.d.tier || 0)) };
  }
  const unfitSummary = n => n + ' session' + (n === 1 ? '' : 's') + ' could not fit';
  function unscheduledHtml(s, lab) {
    const U = unscheduledGroups(s);
    if (!U.count) return '<p class="ld-muted">' + ALL_FIT + '</p>';
    return '<details class="ld-details"' + (lab ? ' open' : '') + '><summary>' + unfitSummary(U.count) + '</summary><ul class="ld-why">' +
      U.groups.map(g => '<li><b>' + esc(g.d.title) + (g.n > 1 ? ' ×' + g.n : '') + '</b> <span class="ld-tierlbl">tier ' + g.d.tier + '</span><p>' + esc(g.d.humanExplanation) + '</p></li>').join('') + '</ul></details>';
  }
  const PRIO = ['', 'Low', 'Light', 'Medium', 'High', 'Top'];
  const GOALS_EXPLAIN = { title: 'How your time is shared', text: 'Each goal gets a share of activity time in proportion to its priority. First every goal reaches its weekly minimum, then deadline quests, then weekly targets, and extra volume only if your intensity setting allows it. No single goal may take more than 60% of the time while another is below its minimum.' };
  const GOALS_EMPTY = { title: 'No goals yet', text: 'Goals turn into regular sessions on your calendar. Quests that ask for the same kind of activity share those sessions instead of doubling up.' };
  const GOAL_REMOVE_CONFIRM = 'Remove this goal? Its future sessions are removed; completed ones stay in your history.';
  const CLARIFY_TEXT = 'Life Director needs to know what this goal looks like in practice. Pick the closest match:';
  /* a goal's share of the week: the engine's allocation, with the engine's own rule (priority over the sum of priorities) where a
     local repair left the target share at 0 */
  function goalShare(g, alloc) {
    const a0 = alloc.find(x => x.goalId === g.id);
    return a0 && !a0.targetShare ? Object.assign({}, a0, { targetShare: (g.priority || 3) / Math.max(1, alloc.reduce((n, x) => { const gg = ST.goals.find(y => y.id === x.goalId); return n + ((gg && gg.priority) || 3); }, 0)) }) : a0;
  }
  /* everything a goal's card shows, decided once for the page's Goals and the native one (nativeGoals) */
  function goalCards() {
    const s = ST.schedule, alloc = s ? s.goalAllocation : [];
    const warn = s ? s.trajectoryWarnings : [];
    const clar = s ? s.clarificationPrompts : [];
    return ST.goals.map(g => {
      const a = goalShare(g, alloc), tw = warn.find(w => w.goalId === g.id), cp = clar.find(c => c.goalId === g.id);
      const arch = g.archetypeId && E.ARCHETYPES[g.archetypeId], paused = g.status === 'PAUSED';
      return {
        g, a, paused,
        sub: (arch ? arch.title : 'Custom') + ' · ' + (g.level || 'NOVICE').toLowerCase() + (paused ? ' · paused' : ''),
        line: a ? a.plannedSessions + ' sessions · ' + mins(a.scheduledMin) + ' this week · ' + a.templates.map(t => t.title + ' ' + t.med + (t.base > t.med ? '–' + t.base : '') + '×').join(', ') : '',
        warning: tw ? tw.message + ' Projected finish: ' + fDay(tw.projectedCompletionEpoch) + '.' : null,
        clarify: cp ? (cp.suggestedTemplateIds.length ? cp.suggestedTemplateIds : Object.keys(E.ARCHETYPES).slice(0, 4)).map(t => ({ arch: t, label: E.ARCHETYPES[t].title })) : null,
        pause: paused ? 'Resume' : 'Pause',
      };
    });
  }
  function renderGoals() {
    const pct = v => Math.round((v || 0) * 100);
    const cards = goalCards().map(c => {
      const { g, a } = c;
      const share = a ? '<div class="ld-share"><div><span>Target share</span><em><i style="width:' + pct(a.targetShare) + '%"></i></em><b>' + pct(a.targetShare) + '%</b></div><div><span>Scheduled</span><em><i class="act" style="width:' + pct(a.actualShare) + '%"></i></em><b>' + pct(a.actualShare) + '%</b></div></div><p class="ld-muted">' + esc(c.line) + '</p>' : '';
      return '<div class="ld-card ld-goal' + (c.paused ? ' paused' : '') + '"><div class="ld-goal-h"><div><h4>' + esc(g.title) + '</h4><small>' + esc(c.sub) + '</small></div>' +
        '<select class="ld-sel" data-act="goalPrio" data-id="' + esc(g.id) + '" aria-label="Priority">' + [5, 4, 3, 2, 1].map(p => '<option value="' + p + '"' + (g.priority === p ? ' selected' : '') + '>' + PRIO[p] + '</option>').join('') + '</select></div>' + share +
        (c.warning ? '<p class="ld-note ld-warn-t">' + esc(c.warning) + '</p>' : '') +
        (c.clarify ? '<div class="ld-note"><p>' + CLARIFY_TEXT + '</p><div class="ld-row">' + c.clarify.map(o => '<button class="ld-btn ld-small" data-act="clarify" data-id="' + esc(g.id) + '" data-arch="' + o.arch + '">' + esc(o.label) + '</button>').join('') + '</div></div>' : '') +
        '<div class="ld-row"><button class="ld-btn ld-small ld-ghost" data-act="goalPause" data-id="' + esc(g.id) + '">' + c.pause + '</button><button class="ld-btn ld-small ld-ghost" data-act="goalDel" data-id="' + esc(g.id) + '">Remove</button></div></div>';
    }).join('');
    return '<div class="ld-card ld-explain"><h4>' + GOALS_EXPLAIN.title + '</h4><p>' + GOALS_EXPLAIN.text + '</p></div>' +
      (cards || '<div class="ld-empty"><h3>' + GOALS_EMPTY.title + '</h3><p>' + GOALS_EMPTY.text + '</p></div>') +
      '<button class="ld-btn ld-primary ld-wide" data-act="addGoal">Add a goal</button>';
  }
  const QUEST_HEAD = { title: 'Connected to your Quest Board', text: 'Life Director reads your quests, schedules time for the ones that need it, and checks them off on the Quest Board when the sessions are done. Quest XP is paid by LifeMax once per period, so nothing is counted twice.', open: 'Open Quest Board' };
  const MILESTONE_NOTE = 'These are earned through everything else you do, so Life Director doesn’t plan time for them.';
  /* everything Quests shows, decided once for the page's Quests and the native one (nativeQuests) */
  function questsModel() {
    const cap = Host.capabilities(), s = ST.schedule;
    const head = cap.connected ? QUEST_HEAD : { title: 'Quest Board not detected', text: 'Open Life Director inside LifeMax to connect quests. Missing: ' + cap.missing.join(', ') + '.', open: '' };
    if (!s) return { head, rows: null };
    const rows = s.questCoverage.filter(q => !String(q.questId).endsWith('@next')).map(q => {
      const diag = (s.unscheduledDiagnostics || []).filter(d => (d.linkedQuestIds || []).includes(q.questId) || d.taskId === q.questId);
      const status = q.status === 'PASSIVE' ? 'All-day reminder' : q.status === 'COMPLETED' ? 'Done this period' : q.covered ? q.scheduledSessions + ' session' + (q.scheduledSessions === 1 ? '' : 's') + ' scheduled' : diag.length ? 'Could not fit' : q.status === 'LOCKED' ? 'Locked' : q.scheduledSessions + ' scheduled';
      return { title: q.title, when: q.recurrence === 'DAILY' ? 'Daily' : q.deadlineEpoch ? 'Due ' + fDay(q.deadlineEpoch) : 'No deadline', status,
        tone: q.covered || q.status === 'COMPLETED' ? 'ok' : q.status === 'PASSIVE' ? 'pas' : 'no', why: diag.length && !q.covered ? diag[0].humanExplanation : '' };
    });
    const passive = (s.passiveReminders || []).map(r => ({ title: r.title, when: 'Tracked on the Quest Board', status: 'All-day' }));
    const ms = Host.milestones().filter(m => !m.locked);
    const milestones = ms.length ? { summary: ms.filter(m => m.done).length + ' of ' + ms.length + ' earned', note: MILESTONE_NOTE,
      rows: ms.map(m => ({ title: m.title, desc: m.desc, earned: !!m.done, status: m.done ? 'Earned' : 'In progress' })) } : null;
    return { head, rows, passive, milestones, empty: 'No active quests.' };
  }
  function renderQuests() {
    const Q = questsModel();
    const head = '<div class="ld-card"><h4>' + (Q.head.open ? icon('flag', 12) + ' ' : '') + esc(Q.head.title) + '</h4><p>' + esc(Q.head.text) + '</p>' + (Q.head.open ? '<button class="ld-btn ld-small ld-ghost" data-act="openQuests">' + Q.head.open + '</button>' : '') + '</div>';
    if (!Q.rows) return head;
    const li = (cls, r) => '<li class="ld-q ' + cls + '"><div><b>' + esc(r.title) + '</b><small>' + esc(r.when) + '</small></div><span>' + esc(r.status) + '</span>' + (r.why ? '<p>' + esc(r.why) + '</p>' : '') + '</li>';
    const rows = Q.rows.map(r => li(r.tone, r)).join('');
    const passive = Q.passive.map(r => li('pas', r)).join('');
    const M = Q.milestones;
    const miles = M ? '<h4 class="ld-sec">Milestones<small>' + M.summary + '</small></h4><p class="ld-muted">' + M.note + '</p>' +
      '<ul class="ld-qlist">' + M.rows.map(r => '<li class="ld-q ' + (r.earned ? 'ok' : 'pas') + '"><div><b>' + esc(r.title) + '</b><small>' + esc(r.desc) + '</small></div><span>' + r.status + '</span></li>').join('') + '</ul>' : '';
    return head + '<ul class="ld-qlist">' + (rows || '<li class="ld-muted">' + Q.empty + '</li>') + passive + '</ul>' + miles;
  }

  /* ── my time (setup) ── */
  const KIND_LABEL = { WORK: 'Work', CLASS: 'School / class', APPOINTMENT: 'Regular commitment', UNAVAILABLE: 'Unavailable' };
  function daysText(r) {
    if (r.rotation) return 'rotating ' + r.rotation.activeDays.length + ' on / ' + (r.rotation.cycleLength - r.rotation.activeDays.length) + ' off';
    const d = (r.days || []).slice().sort();
    if (d.length === 7) return 'every day';
    if (d.join() === '1,2,3,4,5') return 'weekdays';
    if (d.join() === '0,6') return 'weekends';
    if (r.kind === 'SLEEP' && d.join() === '0,1,2,3,4') return 'Sun–Thu nights';
    if (r.kind === 'SLEEP' && d.join() === '5,6') return 'Fri and Sat nights';
    return d.map(x => DOW[x]).join(' ');
  }
  function ruleText(r) {
    return daysText(r) + ' · ' + fHm(r.startMin) + '–' + fHm((r.startMin + r.durationMin) % 1440) + (r.commuteMin ? ' · ' + r.commuteMin + 'm travel each way' : '');
  }
  /* 24h minutes → the person's own clock style (12h or 24h) */
  function fHm(m) { const d = new Date(Date.UTC(2020, 0, 1, Math.floor(m / 60) % 24, m % 60)); return fmt({ hour: 'numeric', minute: '2-digit' }, 'UTC').format(d); }
  const sleepRules = () => (ST.availability.rules || []).filter(r => r.kind === 'SLEEP');
  /* the work arcs the mini sleep dial shows for context (a rotating shift, or anything on a weekday), in minutes after midnight */
  const sleepContext = () => (ST.availability.rules || []).filter(r => r.kind !== 'SLEEP' && r.kind !== 'PREFERRED' && (r.rotation || (r.days || []).some(d => d >= 1 && d <= 5)))
    .map(r => ({ from: r.startMin, to: r.startMin + r.durationMin }));
  const MINI_GROUP = 'Sleep window'; // the mini dial's name, for a screen reader
  function miniDialHtml(bed, wake) {
    const a0 = bed / 4, a1 = (wake <= bed ? wake + 1440 : wake) / 4, dur = ((wake - bed) + 1440) % 1440 || 1440;
    const ctx = sleepContext().map(c => '<path class="ld-o ld-c-work ld-ctx" d="' + arcD(DIAL.RO, c.from / 4, c.to / 4) + '" stroke-width="' + DIAL.WO + '"/>').join('');
    const h = (end, a) => { const [x, y] = polar(DIAL.RO, a); return '<g class="ld-hd ld-c-sleep" data-end="' + end + '" role="slider" tabindex="0" aria-label="' + (end === 'start' ? 'Bedtime' : 'Wake up') + '"><circle class="ld-hd-hit" cx="' + x.toFixed(1) + '" cy="' + y.toFixed(1) + '" r="26"/><circle class="ld-hd-ring" cx="' + x.toFixed(1) + '" cy="' + y.toFixed(1) + '" r="12"/><circle class="ld-hd-pip" cx="' + x.toFixed(1) + '" cy="' + y.toFixed(1) + '" r="4"/></g>'; };
    return '<svg viewBox="0 0 360 360" aria-hidden="false" role="group" aria-label="' + MINI_GROUP + '">' + faceHtml() + ctx +
      '<path class="ld-o ld-c-sleep ld-mini-arc" d="' + arcD(DIAL.RO, a0, a1) + '" stroke-width="' + DIAL.WO + '"/>' + h('start', a0) + h('end', a1 % 360) + '</svg>' +
      '<div class="ld-core"><small class="ld-core-k">Sleep</small><b class="ld-core-big ld-mini-dur">' + mins(dur) + '</b><span class="ld-core-s ld-mini-span">' + fHm(bed) + ' – ' + fHm(wake) + '</span></div>';
  }
  /* My time, decided once for the page (renderSetup) and a native screen (nativeTime): set-up's steps, sleep, the commitments, the
     daily routines, goals and the build, in the page's words. Times are minutes after midnight; a stepper sends one label per stop. */
  const ROUTINES = [['morningRoutineMinutes', 0, 120, 5, 'Getting ready', 'After you wake up'], ['windDownMinutes', 0, 120, 5, 'Wind-down', 'Before bed']];
  const MEAL_RANGE = ['mealMinutes', 10, 90, 5, 'Meals', 'Breakfast, lunch and dinner blocks'];
  const stopLabels = (lo, hi, step, f) => { const out = []; for (let v = lo; v <= hi; v += step) out.push(f(v)); return out; };
  function timeModel() {
    const sleeps = sleepRules();
    const wk = sleeps.find(r => r.id === 'sleep-wk') || sleeps[0], we = sleeps.find(r => r.id === 'sleep-we');
    const bed = wk ? wk.startMin : 23 * 60, wake = wk ? (wk.startMin + wk.durationMin) % 1440 : 7 * 60;
    const other = (ST.availability.rules || []).filter(r => r.kind !== 'SLEEP' && r.kind !== 'PREFERRED');
    const p = E.normalizePreferences(ST.prefs), first = !ST.setupComplete;
    const routine = ([key, min, max, step, label, hint]) => ({ key, label, hint, min, max, step, value: p[key], text: p[key] ? mins(p[key]) : 'Off',
      stops: stopLabels(min, max, step, v => v ? mins(v) : 'Off'), less: 'Less', more: 'More' });
    return {
      first,
      steps: first ? [['Sleep', wk ? 'ok' : 'cur'], ['Work & school', other.length ? 'ok' : wk ? 'cur' : ''], ['Goals', ST.goals.length ? 'ok' : wk ? 'cur' : ''], ['Build', '']].map(([label, state]) => ({ label, state })) : [],
      lede: first ? 'Tell Life Director what your time is already spoken for. It plans everything else.' : 'Change anything here and your plan rebuilds around it automatically.',
      sleep: { title: 'Sleep', note: 'Drag the two handles, or type the times. Nothing is ever planned into sleep.', saved: !!wk,
        bed, wake, bedText: fHm(bed), wakeText: fHm(wake), length: mins(((wake - bed) + 1440) % 1440 || 1440),
        weekend: { on: !!we, bed: we ? we.startMin : (bed + 60) % 1440, wake: we ? (we.startMin + we.durationMin) % 1440 : (wake + 60) % 1440 },
        labels: { centre: 'Sleep', bed: we ? 'Weeknight bedtime' : 'Bedtime', wake: 'Wake up', weekend: 'Different on Friday and Saturday nights', weekendBed: 'Weekend bedtime', weekendWake: 'Weekend wake',
          bedHandle: 'Bedtime', wakeHandle: 'Wake up' },
        save: { label: wk ? 'Save sleep' : 'Save sleep and continue', primary: !wk } },
      commitments: { title: 'Work, school and fixed commitments', rows: other.map(r => ({ id: r.id, title: r.title, dot: r.kind === 'UNAVAILABLE' ? 'block' : 'work', line: (KIND_LABEL[r.kind] || r.kind) + ' · ' + ruleText(r) })),
        empty: 'Nothing yet. Add your job, classes, or anything else that happens at the same time each week.', add: 'Add a commitment' },
      routines: { title: 'Daily routines', text: 'Time Life Director keeps around your day so it never books you the minute you wake up.', rows: ROUTINES.map(routine),
        meals: Object.assign(routine(MEAL_RANGE), { on: !!p.mealsEnabled, text: p.mealsEnabled ? mins(p.mealMinutes) : 'Off', toggle: p.mealsEnabled ? 'Turn off' : 'Turn on', less: 'Shorter', more: 'Longer' }),
        note: 'Travel and unwind time after work are set on each commitment.' },
      goals: { title: 'Goals', chips: ST.goals.map(g => g.title), empty: 'Add at least one goal so there is something to plan. Your LifeMax quests are included automatically.',
        add: { label: 'Add a goal', primary: !!wk && !ST.goals.length }, manage: ST.goals.length ? 'Manage goals' : null },
      build: first ? { title: 'Build my week', text: 'Time zone: ' + tz() + '. You can change everything later.', label: 'Build my week', enabled: !!wk } : null,
      replan: first ? null : { label: 'Re-plan now' },
    };
  }
  function renderSetup() {
    const T = timeModel(), S = T.sleep, C = T.commitments, R = T.routines, M = R.meals, G = T.goals;
    const btn = (r, sign) => '<button class="ld-btn ld-small" data-act="routine" data-k="' + r.key + '" data-v="' + sign * r.step + '" data-lo="' + r.min + '" data-hi="' + r.max + '" aria-label="' + (sign < 0 ? r.less : r.more) + '">' + (sign < 0 ? '−' : '+') + '</button>';
    const stepper = r => '<div class="ld-step-row"><div><b>' + r.label + '</b><small>' + r.hint + '</small></div><div class="ld-stepper">' + btn(r, -1) + '<output>' + r.text + '</output>' + btn(r, 1) + '</div></div>';
    return (T.first ? '<ol class="ld-steps">' + T.steps.map((s, k) => '<li' + (k < 3 ? ' class="' + s.state + '"' : '') + '>' + s.label + '</li>').join('') + '</ol>' : '') + // Build never has a state
      '<p class="ld-lede">' + T.lede + '</p>' +
      '<div class="ld-card ld-sleepcard"><h4>' + icon('moon', 14) + ' ' + S.title + '</h4><p class="ld-muted">' + S.note + '</p>' +
      '<div class="ld-mini" data-bed="' + S.bed + '" data-wake="' + S.wake + '">' + miniDialHtml(S.bed, S.wake) + '</div>' +
      '<div class="ld-grid2"><label>' + S.labels.bed + '<input type="time" id="ldBed" value="' + hm(S.bed) + '"></label><label>' + S.labels.wake + '<input type="time" id="ldWake" value="' + hm(S.wake) + '"></label></div>' +
      '<label class="ld-check"><input type="checkbox" id="ldWeekendOn"' + (S.weekend.on ? ' checked' : '') + '> ' + S.labels.weekend + '</label>' +
      '<div class="ld-grid2" id="ldWeekendRow"' + (S.weekend.on ? '' : ' hidden') + '><label>' + S.labels.weekendBed + '<input type="time" id="ldBedWe" value="' + hm(S.weekend.bed) + '"></label><label>' + S.labels.weekendWake + '<input type="time" id="ldWakeWe" value="' + hm(S.weekend.wake) + '"></label></div>' +
      '<button class="ld-btn' + (S.save.primary ? ' ld-primary' : '') + '" data-act="saveSleep">' + S.save.label + '</button></div>' +
      '<div class="ld-card ld-card-commit"><h4>' + icon('briefcase', 14) + ' ' + C.title + '</h4><ul class="ld-rules">' +
      (C.rows.map(r => '<li data-act="editRule" data-id="' + esc(r.id) + '" role="button" tabindex="0"><i class="ld-rdot ld-c-' + r.dot + '"></i><div><b>' + esc(r.title) + '</b><small>' + esc(r.line) + '</small></div><span class="ld-chev" aria-hidden="true">›</span></li>').join('') ||
        '<li class="ld-muted ld-rules-empty">' + C.empty + '</li>') +
      '</ul><button class="ld-btn" data-act="addRule">' + C.add + '</button></div>' +
      '<div class="ld-card ld-card-routines"><h4>' + icon('smile', 14) + ' ' + R.title + '</h4><p class="ld-muted">' + R.text + '</p>' + R.rows.map(stepper).join('') +
      '<div class="ld-step-row"><div><b>' + M.label + '</b><small>' + M.hint + '</small></div><div class="ld-stepper">' + (M.on ? btn(M, -1) + '<output>' + M.text + '</output>' + btn(M, 1) : '<output>' + M.text + '</output>') +
      '<button class="ld-btn ld-small ld-ghost" data-act="meals">' + M.toggle + '</button></div></div>' +
      '<p class="ld-muted">' + R.note + '</p></div>' +
      '<div class="ld-card ld-card-goals"><h4>' + icon('target', 14) + ' ' + G.title + '</h4>' + (G.chips.length ? '<div class="ld-chips ld-goalchips">' + G.chips.map(t => '<span class="ld-chip">' + esc(t) + '</span>').join('') + '</div>' : '<p class="ld-muted">' + G.empty + '</p>') +
      '<div class="ld-row"><button class="ld-btn' + (G.add.primary ? ' ld-primary' : '') + '" data-act="addGoal">' + G.add.label + '</button>' + (G.manage ? '<button class="ld-btn ld-ghost" data-act="tab" data-tab="goals">' + G.manage + '</button>' : '') + '</div></div>' +
      (T.build ? '<div class="ld-card ld-build"><h4>' + T.build.title + '</h4><p class="ld-muted">' + esc(T.build.text) + '</p><button class="ld-btn ld-primary ld-wide" data-act="finishSetup"' + (T.build.enabled ? '' : ' aria-disabled="true"') + '>' + T.build.label + '</button></div>'
        : '<div class="ld-row ld-foot"><button class="ld-btn ld-ghost" data-act="finishSetup">' + T.replan.label + '</button></div>');
  }

  /* ── forms: the commitment and goal sheets, decided once as a schema the page draws (ruleSheet, goalForm) and a native screen draws
     with any form kit (nativeSheet). Every field has key, kind (text, choice, check, days, time, date, number), label, value, required
     and group (fields sharing a group share a row); text adds maxLength and placeholder, number min, max, step and placeholder (null:
     no limit), time the page's text for its value, choice and days their options, and check the fields it reveals and hides. ── */
  function formField(key, kind, label, value, more) {
    const extra = kind === 'time' ? { text: fHm(value) } : { text: { maxLength: null, placeholder: '' }, number: { min: null, max: null, step: 1, placeholder: '' }, check: { reveals: [], hides: [] } }[kind];
    return Object.assign({ key, kind, label, value, required: false, group: null }, extra, more);
  }
  const byKey = fields => Object.fromEntries(fields.map(f => [f.key, f]));
  const choices = list => list.map(([value, label]) => ({ value: String(value), label }));
  const numberInput = (id, f) => '<input type="number" id="' + id + '"' + (f.value != null ? ' value="' + f.value + '"' : '') + (f.min != null ? ' min="' + f.min + '"' : '') + (f.max != null ? ' max="' + f.max + '"' : '') +
    (f.step !== 1 ? ' step="' + f.step + '"' : '') + (f.placeholder ? ' placeholder="' + f.placeholder + '"' : '') + '>';
  const RULE_KINDS = [['WORK', 'Work shift'], ['CLASS', 'School / class'], ['APPOINTMENT', 'Regular commitment'], ['UNAVAILABLE', 'Unavailable (personal)']];
  const RULE_LOADS = [['MIXED_MODERATE', 'Mixed'], ['PHYSICAL_HEAVY', 'Physically hard'], ['COGNITIVE_HEAVY', 'Mentally hard'], ['LIGHT', 'Light']];
  const NEW_RULE = Object.freeze({ title: 'Work', kind: 'WORK', days: [1, 2, 3, 4, 5], startMin: 540, durationMin: 480, commuteMin: 0, load: 'MIXED_MODERATE', rotation: null, decompressMin: null });
  /* the commitment sheet: a new one (no r) or an edit of r; ruleSave takes every field's value by its key */
  function ruleForm(r) {
    const edit = !!r;
    r = r || NEW_RULE;
    const unwind = r.decompressMin != null ? r.decompressMin : (r.kind === 'WORK' && r.load !== 'LIGHT' ? 30 : 0);
    const rot = r.rotation, on = rot ? rot.activeDays.length : 4, off = rot ? rot.cycleLength - rot.activeDays.length : 4;
    return { kind: 'rule', id: edit ? r.id : null, title: edit ? 'Edit ' + r.title : 'Add a commitment', text: '',
      fields: [
        formField('title', 'text', 'Name', r.title, { maxLength: 40 }),
        formField('kind', 'choice', 'Type', r.kind, { options: choices(RULE_KINDS) }),
        formField('rotating', 'check', 'Rotating shift pattern (e.g. 4 on / 4 off)', !!rot, { reveals: ['on', 'off', 'anchor'], hides: ['days'] }),
        formField('days', 'days', 'Days', rot ? [] : (r.days || []).slice(), { options: DOW.map((d, k) => ({ value: k, label: d.slice(0, 2) })), required: true }),
        formField('on', 'number', 'Days on', on, { min: 1, max: 14, group: 0 }),
        formField('off', 'number', 'Days off', off, { min: 1, max: 14, group: 0 }),
        formField('anchor', 'date', 'First day on', rot ? rot.anchorDate : U.ymdKey(U.localParts(nowEpoch(), tz())), { required: true, group: 0 }),
        formField('start', 'time', 'Starts', r.startMin, { required: true, group: 1 }),
        formField('end', 'time', 'Ends', (r.startMin + r.durationMin) % 1440, { required: true, group: 1 }),
        formField('commute', 'number', 'Travel each way (min)', r.commuteMin || 0, { min: 0, max: 240, step: 5, group: 2 }),
        formField('unwind', 'number', 'Unwind after (min)', unwind, { min: 0, max: 120, step: 5, group: 2 }),
        formField('load', 'choice', 'How tiring', r.load || 'MIXED_MODERATE', { options: choices(RULE_LOADS) }),
      ],
      fold: null,
      note: 'Overnight shifts are fine: an end time earlier than the start runs into the next morning. Tiring shifts keep heavy sessions away from the hours after them.',
      save: { label: edit ? 'Save' : 'Add', command: 'ruleSave' }, cancel: 'Cancel', remove: edit ? { label: 'Remove', command: 'ruleRemove' } : null };
  }
  const RULE_INPUT = { title: 'rTitle', kind: 'rKind', rotating: 'rRot', days: 'rDays', on: 'rOn', off: 'rOff', anchor: 'rAnchor', start: 'rStart', end: 'rEnd', commute: 'rCommute', unwind: 'rUnwind', load: 'rLoad' };
  function ruleSheet(r) {
    const F = ruleForm(r), f = byKey(F.fields), rot = f.rotating.value;
    const select = x => '<label>' + x.label + '<select id="' + RULE_INPUT[x.key] + '">' + x.options.map(o => '<option value="' + o.value + '"' + (o.value === x.value ? ' selected' : '') + '>' + o.label + '</option>').join('') + '</select></label>';
    const num = x => '<label>' + x.label + numberInput(RULE_INPUT[x.key], x) + '</label>';
    const time = x => '<label>' + x.label + '<input type="time" id="' + RULE_INPUT[x.key] + '" value="' + hm(x.value) + '"></label>';
    openSheet('<h3>' + esc(F.title) + '</h3><label>' + f.title.label + '<input id="rTitle" value="' + esc(f.title.value) + '" maxlength="' + f.title.maxLength + '"></label>' + select(f.kind) +
      '<label class="ld-check"><input type="checkbox" id="rRot"' + (rot ? ' checked' : '') + '> ' + f.rotating.label + '</label>' +
      '<div id="rDaysWrap"' + (rot ? ' hidden' : '') + '><span class="ld-lbl">' + f.days.label + '</span><div class="ld-dchips">' +
      f.days.options.map(o => '<label class="ld-dchip"><input type="checkbox" name="rDays" value="' + o.value + '"' + (f.days.value.includes(o.value) ? ' checked' : '') + '><span>' + o.label + '</span></label>').join('') + '</div></div>' +
      '<div id="rRotWrap"' + (rot ? '' : ' hidden') + ' class="ld-grid3">' + num(f.on) + num(f.off) + '<label>' + f.anchor.label + '<input type="date" id="rAnchor" value="' + esc(f.anchor.value) + '"></label></div>' +
      '<div class="ld-grid2">' + time(f.start) + time(f.end) + '</div><div class="ld-grid2">' + num(f.commute) + num(f.unwind) + '</div>' + select(f.load) +
      '<p class="ld-muted">' + F.note + '</p>' +
      '<div class="ld-row"><button class="ld-btn ld-primary" data-act="saveRule"' + (F.id ? ' data-id="' + esc(F.id) + '"' : '') + '>' + F.save.label + '</button><button class="ld-btn ld-ghost" data-act="closeSheet">' + F.cancel + '</button>' +
      (F.remove ? '<button class="ld-btn ld-ghost ld-danger" data-act="delRule" data-id="' + esc(F.id) + '">' + F.remove.label + '</button>' : '') + '</div>');
  }

  /* ── goal sheet ── */
  /* the goal kinds "Add a goal" offers: the engine's archetypes, then your own */
  function goalTypeModel() {
    return { kind: 'goalType', title: 'Add a goal', text: 'Pick the closest kind of goal. Life Director turns it into regular, sensibly spaced sessions.',
      options: Object.entries(E.ARCHETYPES).map(([arch, a]) => ({ arch, icon: tagIcon(a.templates[0].tag), title: a.title, sub: a.templates.map(t => t.title).join(', ') }))
        .concat({ arch: 'CUSTOM', icon: 'plus', title: 'Something else', sub: 'Describe your own activity' }) };
  }
  function goalSheet() {
    const T = goalTypeModel();
    openSheet('<h3>' + T.title + '</h3><p class="ld-muted">' + T.text + '</p><div class="ld-arch">' +
      T.options.map(o => '<button class="ld-archbtn" data-act="pickArch" data-arch="' + o.arch + '">' + icon(o.icon, 16) + '<b>' + esc(o.title) + '</b><small>' + esc(o.sub) + '</small></button>').join('') + '</div>');
  }
  const CUSTOM_TAGS = [['fitness.strength', 'Strength training'], ['fitness.cardio', 'Cardio'], ['fitness.mobility', 'Mobility / yoga'], ['study.deep', 'Study'], ['reading', 'Reading'], ['language', 'Language'], ['creative.practice', 'Creative practice'], ['business', 'Business / side project'], ['admin.finance', 'Money admin'], ['chores', 'Household'], ['social', 'Social'], ['wellbeing.mindfulness', 'Mindfulness']];
  const GOAL_LEVELS = [['NOVICE', 'New to this'], ['INTERMEDIATE', 'Some experience'], ['ADVANCED', 'Experienced']];
  /* the goal form for a kind of goal (an archetype, or CUSTOM: your own activity); goalAdd takes every field's value by its key, the
     optional measurable target's (fold) included */
  function goalFormModel(arch) {
    const a = E.ARCHETYPES[arch], vol = a && a.templates.some(t => t.volumeDriver);
    const fields = [formField('title', 'text', 'Name', a ? a.title : '', { maxLength: 50, placeholder: 'e.g. Learn guitar', required: true })];
    if (arch === 'SKILL_LANGUAGE') fields.push(formField('subject', 'text', 'Language', 'spanish', { maxLength: 20 }));
    if (!a) fields.push(formField('tag', 'choice', 'Activity', CUSTOM_TAGS[0][0], { options: choices(CUSTOM_TAGS) }),
      formField('minutes', 'number', 'Minutes per session', 30, { min: 15, max: 180, step: 15, group: 0 }), formField('perWeek', 'number', 'Times per week', 3, { min: 1, max: 14, group: 0 }),
      formField('physical', 'number', 'Physical effort (1–5)', 2, { min: 1, max: 5, group: 1 }), formField('mental', 'number', 'Mental effort (1–5)', 2, { min: 1, max: 5, group: 1 }));
    fields.push(formField('priority', 'choice', 'Priority', '3', { options: [5, 4, 3, 2, 1].map(p => ({ value: String(p), label: PRIO[p] })), group: 2 }),
      formField('level', 'choice', 'Experience', 'NOVICE', { options: choices(GOAL_LEVELS), group: 2 }));
    return { kind: 'goalForm', id: a ? arch : 'CUSTOM', title: a ? a.title : 'Custom goal', text: '', fields,
      fold: vol ? { summary: 'Add a measurable target (optional)', note: 'Life Director paces sessions to hit the date, and tells you honestly if the date is not realistic.',
        fields: [formField('units', 'number', 'Total', null, { placeholder: '3600', group: 3 }), formField('unit', 'text', 'Unit', '', { placeholder: 'pages', group: 3 }),
          formField('rate', 'number', 'Per hour', null, { placeholder: '30', group: 4 }), formField('deadline', 'date', 'By', '', { group: 4 })] } : null,
      note: '', save: { label: 'Add goal', command: 'goalAdd' }, cancel: 'Cancel', remove: null };
  }
  /* the page's own form read back as its fields' values by key, typed as a native screen sends them (time: minutes, number: null
     when empty, days: the ticked days), ids naming each field's control */
  function formValues(fields, ids) {
    const el = id => root.querySelector('#' + id);
    const read = f => f.kind === 'check' ? el(ids[f.key]).checked : f.kind === 'days' ? [...root.querySelectorAll('input[name=' + ids[f.key] + ']:checked')].map(x => +x.value)
      : f.kind === 'time' ? parseHm(el(ids[f.key]).value) : f.kind === 'number' ? (el(ids[f.key]).value === '' ? null : +el(ids[f.key]).value) : el(ids[f.key]).value;
    return Object.fromEntries(fields.map(f => [f.key, read(f)]));
  }
  const GOAL_INPUT = { title: 'gTitle', subject: 'gSubject', tag: 'gTag', minutes: 'gMin', perWeek: 'gFreq', physical: 'gIP', mental: 'gIC', priority: 'gPrio', level: 'gLevel', units: 'gUnits', unit: 'gUnit', rate: 'gRate', deadline: 'gDeadline' };
  function goalForm(arch) {
    const F = goalFormModel(arch), f = byKey(F.fields), t = F.fold && byKey(F.fold.fields);
    const label = (x, input) => '<label>' + x.label + input + '</label>', num = x => label(x, numberInput(GOAL_INPUT[x.key], x));
    // the page marks only the priority's choice: the others start on their first option, as a select does by itself
    const select = (x, mark) => label(x, '<select id="' + GOAL_INPUT[x.key] + '">' + x.options.map(o => '<option value="' + o.value + '"' + (mark && o.value === x.value ? ' selected' : '') + '>' + o.label + '</option>').join('') + '</select>');
    openSheet('<h3>' + esc(F.title) + '</h3>' + label(f.title, '<input id="gTitle" maxlength="' + f.title.maxLength + '" value="' + esc(f.title.value) + '" placeholder="' + f.title.placeholder + '">') +
      (f.subject ? label(f.subject, '<input id="gSubject" value="' + esc(f.subject.value) + '" maxlength="' + f.subject.maxLength + '">') : '') +
      (f.tag ? select(f.tag, false) + '<div class="ld-grid2">' + num(f.minutes) + num(f.perWeek) + '</div><div class="ld-grid2">' + num(f.physical) + num(f.mental) + '</div>' : '') +
      '<div class="ld-grid2">' + select(f.priority, true) + select(f.level, false) + '</div>' +
      (t ? '<details class="ld-details"><summary>' + F.fold.summary + '</summary><div class="ld-grid2">' + num(t.units) + label(t.unit, '<input id="gUnit" placeholder="' + t.unit.placeholder + '">') + '</div>' +
        '<div class="ld-grid2">' + num(t.rate) + label(t.deadline, '<input type="date" id="gDeadline">') + '</div><p class="ld-muted">' + F.fold.note + '</p></details>' : '') +
      '<div class="ld-row"><button class="ld-btn ld-primary" data-act="saveGoal" data-arch="' + esc(arch) + '">' + F.save.label + '</button><button class="ld-btn ld-ghost" data-act="closeSheet">' + F.cancel + '</button></div>');
  }

  /* ── settings ── */
  const INTENSITY = { options: [['LIGHT', 'Light'], ['BALANCED', 'Balanced'], ['AGGRESSIVE', 'Ambitious'], ['HARDCORE', 'All in']], note: 'Light schedules only each goal’s weekly minimum. Ambitious and All in add extra sessions when time allows.' };
  const PREF_RANGES = [
    ['maxDailyGoalMinutes', 30, 480, 15, 'Most activity time per day', 'A hard daily ceiling, even on free days.'],
    ['minDailyRecoveryMinutes', 30, 360, 15, 'Protected downtime per day', 'Kept clear before anything else is planned.'],
    ['maxConsecutiveHighLoadMinutes', 30, 240, 15, 'Longest run of demanding work', 'After this, Life Director inserts something lighter.'],
  ];
  const GRACE = ['missGraceMinutes', 15, 240, 15, 'Grace period before a session counts as missed'];
  const HORIZON = [[7, '7 days'], [14, '14 days']];
  const MISSES = { options: [['LENIENT', 'Gentle'], ['MODERATE', 'Standard'], ['HARDCORE_RPG', 'Hardcore']], text: 'Gentle: no consequences, missed sessions are just re-planned. Standard: a rest token covers a miss; without one, that activity’s streak drops by one. Hardcore: also reports an HP penalty (never below 1 HP). At most one consequence per day, and never any XP or level loss. ' };
  const SETTINGS_TEXT = {
    intensity: 'How much to schedule', restDays: 'Rest days', restNote: 'Hard workouts and heavy study are avoided on these days when possible.', horizon: 'Planning window', misses: 'When I miss something',
    guide: { title: 'Guide', text: 'A short walkthrough of how to read each screen. It shows once on its own; you can replay it here or with the ? button at the top.', button: 'Show the guide again' },
    data: { title: 'Your data', text: 'Saved on this device under its own key, separate from your LifeMax save. ', storageWarning: 'Storage is unavailable here.', copy: 'Copy backup', restore: 'Restore backup', reset: 'Reset Life Director',
      resetConfirm: 'Reset Life Director? Your schedule, goals and settings here are cleared. LifeMax XP and quests are not affected.', restoreTitle: 'Restore backup', restoreText: 'Paste a Life Director backup. This replaces Life Director data only; your LifeMax save is not touched.', restoreButton: 'Restore' },
    diagnostics: 'Diagnostics', lab: 'Show Simulation Lab (developer)',
  };
  const graceLabel = m => m + 'm';
  /* the Diagnostics line, as plain text (the page escapes it) */
  function diagnosticsLine() {
    const s = ST.schedule, L = ST.ledger;
    return 'engine ' + E.ENGINE_VERSION + ' · ' + (s ? s.status + ' · ' + s.computeTimeMs + ' ms · quality ' + s.validation.qualityMetrics.sqi + ' · ' + s.scheduledInstances.length + ' planned' : 'no plan') +
      ' · events ' + L.order.length + ' · host ' + (Host.connected() ? 'connected' : 'missing ' + Host.capabilities().missing.join(',')) + (ST.lastRejected ? ' · last rejected plan: ' + JSON.stringify(ST.lastRejected.violations) : '');
  }
  function renderSettings() {
    const p = E.normalizePreferences(ST.prefs), T = SETTINGS_TEXT;
    const seg = (key, opts) => '<div class="ld-seg">' + opts.map(([v, l]) => '<button class="' + (p[key] === v ? 'on' : '') + '" data-act="pref" data-k="' + key + '" data-v="' + v + '">' + l + '</button>').join('') + '</div>';
    const range = (key, lo, hi, step, label, hint) => '<label class="ld-range">' + label + ' <b id="v-' + key + '">' + mins(p[key]) + '</b><input type="range" min="' + lo + '" max="' + hi + '" step="' + step + '" value="' + p[key] + '" data-act="prefRange" data-k="' + key + '"><small>' + hint + '</small></label>';
    const L = ST.ledger;
    return '<div class="ld-card"><h4>' + T.intensity + '</h4>' + seg('intensityLevel', INTENSITY.options) +
      '<p class="ld-muted">' + INTENSITY.note + '</p>' +
      PREF_RANGES.map(r => range(...r)).join('') + '</div>' +
      '<div class="ld-card"><h4>' + T.restDays + '</h4><p class="ld-muted">' + T.restNote + '</p><div class="ld-dchips">' + DOW.map((d, k) => '<button class="ld-dchip-btn' + (p.preferredRestDays.includes(k) ? ' on' : '') + '" data-act="restDay" data-v="' + k + '">' + d.slice(0, 2) + '</button>').join('') + '</div></div>' +
      '<div class="ld-card"><h4>' + T.horizon + '</h4>' + seg('planningHorizonDays', HORIZON) + '</div>' +
      '<div class="ld-card"><h4>' + T.misses + '</h4>' + seg('consequenceMode', MISSES.options) +
      '<p class="ld-muted">' + MISSES.text + 'Rest tokens: <b>' + L.restTokens + '</b> of 3 (earn one every 5 completions).</p>' +
      '<label class="ld-range">' + GRACE[4] + ' <b>' + graceLabel(p.missGraceMinutes) + '</b><input type="range" min="' + GRACE[1] + '" max="' + GRACE[2] + '" step="' + GRACE[3] + '" value="' + p.missGraceMinutes + '" data-act="prefRange" data-k="missGraceMinutes"></label></div>' +
      '<div class="ld-card"><h4>' + T.guide.title + '</h4><p class="ld-muted">' + T.guide.text + '</p><div class="ld-row"><button class="ld-btn ld-small" data-act="tourReset">' + T.guide.button + '</button></div></div>' +
      '<div class="ld-card"><h4>' + T.data.title + '</h4><p class="ld-muted">' + T.data.text + (loadReport && loadReport.source === 'MEMORY_ONLY' ? '<b>' + T.data.storageWarning + '</b>' : '') + '</p><div class="ld-row"><button class="ld-btn ld-small" data-act="export">' + T.data.copy + '</button><button class="ld-btn ld-small" data-act="import">' + T.data.restore + '</button><button class="ld-btn ld-small ld-danger" data-act="reset">' + T.data.reset + '</button></div></div>' +
      '<details class="ld-card ld-details"><summary>' + T.diagnostics + '</summary><p class="ld-mono">' + esc(diagnosticsLine()) + '</p>' +
      '<label class="ld-check"><input type="checkbox" data-act="labToggle"' + (ST.ui.lab ? ' checked' : '') + '> ' + T.lab + '</label></details>';
  }

  /* ── activity detail ── */
  function instSheet(inst, lab) {
    const x = inst.explanation || {};
    const maxF = 24;
    const factors = (x.factors || []).map(f => '<div class="ld-fbar"><span>' + esc(f.factor) + '</span><i style="width:' + Math.min(100, f.points / maxF * 100) + '%"></i><b>' + f.points + '</b></div>').join('');
    const planned = inst.lifecycleState === 'PLANNED';
    const ref = inst.linkedQuestRequirementIds.find(l => String(l.questId).startsWith('plan:'));
    const running = ST.ui.focus && ST.ui.focus.id === inst.instanceId;
    const chips = (inst.linkedQuestRequirementIds.length ? inst.linkedQuestRequirementIds.map(l => '<span class="ld-chip ld-chip-quest">' + icon('flag', 10) + ' ' + esc(l.questTitle) + '</span>').join('') : '') +
      (inst.goalTitle ? '<span class="ld-chip">Goal: ' + esc(inst.goalTitle) + '</span>' : '') + (planned && !lab ? xpChip(inst) : '');
    openSheet('<div class="ld-sh-head ld-s-' + sesState(inst.lifecycleState) + '"><i class="ld-rail" aria-hidden="true"></i><div><h3>' + icon(tagIcon(inst.tag), 16) + ' ' + esc(inst.title) + '</h3><p class="ld-when">' + fDay(inst.startEpoch, lab) + ' · ' + fTime(inst.startEpoch, lab) + '–' + fTime(inst.endEpoch, lab) + ' · ' + mins(inst.durationSlots * 15) + (inst.compressed ? ' (shortened)' : '') + '</p></div></div>' +
      (chips ? '<div class="ld-chips">' + chips + '</div>' : '') +
      (lab ? '' : planned && !doable(inst) ? '<p class="ld-note">Planned for <b>' + fDay(inst.startEpoch) + ' at ' + fTime(inst.startEpoch) + '</b>. You can start it or mark it done on the day, or from 6 hours before.</p>' +
        '<div class="ld-complete"><label>Did it today anyway? Minutes<input type="number" id="ldDoneMin" value="' + inst.durationSlots * 15 + '" min="1" max="480"></label><button class="ld-btn" data-act="logToday" data-id="' + esc(inst.instanceId) + '">Log it for today</button></div>' +
        '<p class="ld-muted ld-small-print">You get the XP now, and this session stays on ' + DOW[U.localParts(inst.startEpoch, tz()).dow] + '.</p>' +
        '<div class="ld-row"><button class="ld-btn ld-small" data-act="movePick" data-id="' + esc(inst.instanceId) + '">Move</button><button class="ld-btn ld-small" data-act="lock" data-id="' + esc(inst.instanceId) + '">' + (inst.isUserLocked ? 'Unpin' : 'Pin here') + '</button><button class="ld-btn ld-small ld-ghost" data-act="skip" data-id="' + esc(inst.instanceId) + '">Skip</button></div>'
        : planned ? '<div class="ld-row ld-sh-main">' + (running ? '<button class="ld-btn ld-primary" data-act="focusOpen">Open timer</button>' : '<button class="ld-btn ld-primary" data-act="focusStart" data-id="' + esc(inst.instanceId) + '">Start timer</button>') + '</div>' +
        '<div class="ld-complete"><label>Already did it? Minutes<input type="number" id="ldDoneMin" value="' + inst.durationSlots * 15 + '" min="1" max="480"></label><button class="ld-btn" data-act="done" data-id="' + esc(inst.instanceId) + '">Mark done</button></div>' +
        '<div class="ld-row">' + (ref ? '<button class="ld-btn ld-small" data-act="openPlan" data-v="' + esc(ref.questId.slice(5)) + '">Open in Plan</button>' : '') +
        '<button class="ld-btn ld-small" data-act="movePick" data-id="' + esc(inst.instanceId) + '">Move</button><button class="ld-btn ld-small" data-act="lock" data-id="' + esc(inst.instanceId) + '">' + (inst.isUserLocked ? 'Unpin' : 'Pin here') + '</button><button class="ld-btn ld-small ld-ghost" data-act="skip" data-id="' + esc(inst.instanceId) + '">Skip</button></div>'
        : '<p class="ld-muted">' + (STATE_LABEL[inst.lifecycleState] || inst.lifecycleState) + (inst.actualMinutes ? ' · ' + inst.actualMinutes + ' min' : '') + (inst.undone ? ' · undone in your log' : '') + '</p>') +
      '<details class="ld-why-box"' + (lab ? ' open' : '') + '><summary>Why this time?</summary><p>' + esc(x.summary || 'Placed by Life Director.') + '</p>' + (factors ? '<div class="ld-factors">' + factors + '</div>' : '') +
      (x.selection && x.selection.feasibleDays ? '<p class="ld-muted">It could have fitted on ' + x.selection.feasibleDays + ' day' + (x.selection.feasibleDays === 1 ? '' : 's') + '.</p>' : '') + '</details>' +
      (inst.orphanWarning ? '<p class="ld-note ld-warn-t">Its quest was completed or removed. Keep it as free practice or remove it.</p>' : ''));
  }
  /* ── dial interaction ── */
  function dialAngle(svg, e) {
    const r = svg.getBoundingClientRect();
    const x = (e.clientX - r.left) / r.width * 360 - 180, y = (e.clientY - r.top) / r.height * 360 - 180;
    let a = Math.atan2(x, -y) * 180 / Math.PI; if (a < 0) a += 360;
    return a;
  }
  const buzz = () => { try { if (navigator.vibrate) navigator.vibrate(6); } catch (e) { /* ignore */ } };
  /* one drag helper for both dials: onMove(angle) returns a label, onEnd() commits */
  function dragOn(svg, hd, e, onMove, onEnd) {
    e.preventDefault(); e.stopPropagation();
    const host = svg.parentElement;
    host.classList.add('drag'); hd.classList.add('on');
    try { svg.setPointerCapture(e.pointerId); } catch (err) { /* ignore */ }
    ui.drag = true;
    const mv = ev => { if (ev.pointerId === e.pointerId) onMove(dialAngle(svg, ev)); };
    const up = ev => {
      if (ev.pointerId !== e.pointerId) return;
      svg.removeEventListener('pointermove', mv); svg.removeEventListener('pointerup', up); svg.removeEventListener('pointercancel', up); svg.removeEventListener('lostpointercapture', up);
      try { svg.releasePointerCapture(e.pointerId); } catch (err) { /* already released */ }
      host.classList.remove('drag'); hd.classList.remove('on'); ui.drag = false; ui.dragEnd = Date.now();
      onEnd(ev.type === 'pointercancel' || ev.type === 'lostpointercapture');
    };
    svg.addEventListener('pointermove', mv); svg.addEventListener('pointerup', up); svg.addEventListener('pointercancel', up);
    svg.addEventListener('lostpointercapture', up);
    onMove(dialAngle(svg, e));
  }
  function moveHandle(hd, a, r) { const [x, y] = polar(r, a); hd.querySelectorAll('circle').forEach(c => { c.setAttribute('cx', x.toFixed(1)); c.setAttribute('cy', y.toFixed(1)); }); }
  /* ── the handles' rules, one copy for the web dial and a native screen (LifeDirector.native) ──
     Where a handle lands for the instant under the finger: the nearest quarter hour of the day on the dial, never past 23:45. */
  function snapEp(d0, ep) {
    const p = U.localParts(ep, tz());
    let m = Math.round((p.h * 60 + p.mi) / 15) * 15;
    const day = U.localParts(d0, tz());
    if (m >= 1440) m = 1425;
    return { m, ep: U.zonedToEpoch(day.y, day.mo, day.d, m, tz()) };
  }
  /* Every place a handle can land on the day d0..d1, in dial order: the minute of the day, where the handle sits (a) and the angle
     from which the finger chooses it (from), up to the next one's. snapEp decides each; the local minute only changes on whole
     minutes, so it is asked at every whole minute where it may change: a quarter hour's 8th minute on a day with one UTC offset,
     every minute of a day whose clocks change. Kept per day. */
  const stopCache = new Map();
  function dialStops(d0, d1) {
    const z = tz(), ck = z + '|' + d0 + '|' + d1;
    if (stopCache.has(ck)) return stopCache.get(ck);
    const even = d1 - d0 === 86400 && U.localParts(d0, z).h === 0 && U.localParts(d0, z).mi === 0 && U.tzOffsetSec(d0, z) === U.tzOffsetSec(d1 - 60, z);
    const at = even ? [d0].concat(Array.from({ length: 95 }, (_, k) => d0 + (15 * (k + 1) - 7) * 60)) : Array.from({ length: Math.ceil((d1 - d0) / 60) }, (_, k) => d0 + k * 60);
    const out = [];
    for (const t of at) {
      const s = snapEp(d0, t), last = out[out.length - 1];
      if (!last || last.m !== s.m) out.push({ m: s.m, ep: s.ep, a: angOf(s.ep, d0, d1), from: (t - d0) / (d1 - d0) * 360 });
    }
    if (stopCache.size > 30) stopCache.clear();
    stopCache.set(ck, out);
    return out;
  }
  /* What the dial shows while a handle of rule r (where it was: minute om) is held at cur on the day from d0: the length letting go there
     gives the rule, from dialChange as the confirmation and Change it read it (so a bedtime held past midnight is the sleep left before
     the wake-up time, not a span back to yesterday), or that it is too short; and the span ns..ne from the handle that long towards the
     rule's other end, in wall-clock minutes as the rule's instances are placed. The dial keeps it on the day, as it keeps every arc. */
  function dragPreview(r, end, om, d0, cur) {
    const dur = dialChange(r, end, om, cur.m).durationMin, bad = dur < 15, day = U.localParts(d0, tz());
    const other = U.zonedToEpoch(day.y, day.mo, day.d, end === 'start' ? cur.m + dur : cur.m - dur, tz());
    return { ns: end === 'start' ? cur.ep : other, ne: end === 'start' ? other : cur.ep, bad, text: bad ? DRAG_WORDS.tooShort : mins(dur) + (r.kind === 'SLEEP' ? ' of sleep' : '') };
  }
  const DRAG_WORDS = Object.freeze({ tooShort: 'Too short', release: 'Release to apply' });
  /* the change a handle released at minute m of the day asks for (om: where it was) */
  function dialChange(r, end, om, m) {
    const endRel = r.startMin + r.durationMin;
    const startMin = end === 'start' ? m : r.startMin;
    const dur = end === 'start' ? (((endRel - m) % 1440) + 1440) % 1440 : (((m - r.startMin) % 1440) + 1440) % 1440;
    return { ruleId: r.id, end, startMin, durationMin: dur, from: om, to: m };
  }
  /* the change an arrow key asks for: the handle a quarter hour later (step 1) or earlier (step -1) */
  function dialStep(r, end, step) {
    const q = step * 15;
    const startMin = end === 'start' ? (r.startMin + q + 1440) % 1440 : r.startMin;
    const dur = end === 'start' ? r.durationMin - q : r.durationMin + q;
    const om = end === 'start' ? r.startMin : (r.startMin + r.durationMin) % 1440;
    return { ruleId: r.id, end, startMin, durationMin: dur, from: om, to: (om + q + 1440) % 1440 };
  }
  /* the handles on a day's dial: one at each end of every sleep, work, class or appointment rule that starts or ends on it */
  function dialHandles(day) {
    const d0 = day.startEpoch, d1 = day.endEpoch, out = [];
    for (const ri of ruleInstances(day)) {
      for (const end of ['start', 'end']) {
        const ep = end === 'start' ? ri.startEpoch : ri.endEpoch;
        if (ep < d0 || ep >= d1 || (end === 'end' && ep === d0)) continue;
        out.push({ ri, rule: ri.rule, end, ep, kind: ri.rule.kind === 'SLEEP' ? 'sleep' : 'work', key: ri.rule.id + '|' + end + '|' + U.ymdKey(ri.ymd) + '|' + ep });
      }
    }
    return out;
  }
  const minuteOf = ep => { const p = U.localParts(ep, tz()); return p.h * 60 + p.mi; };
  /* Today's dial: drag a sleep or work handle; release asks before the rule changes */
  function attachDial(dial) {
    dial.classList.add('ld-draggable');
    const svg = dial.querySelector('svg'), d0 = +dial.dataset.d0, d1 = +dial.dataset.d1, ghost = svg.querySelector('.ld-drag-arc'), core = dial.querySelector('.ld-core');
    const snap = a => snapEp(d0, d0 + a / 360 * (d1 - d0));
    svg.addEventListener('pointerdown', e => {
      const hd = e.target.closest('.ld-hd'); if (!hd) return;
      const r = (ST.availability.rules || []).find(x => x.id === hd.dataset.rule); if (!r) return;
      const end = hd.dataset.end, s0 = +hd.dataset.s, e0 = +hd.dataset.e, orig = end === 'start' ? s0 : e0;
      const om = minuteOf(orig);
      let cur = { m: om, ep: orig }, lastM = om;
      const coreWas = core.innerHTML;
      ghost.setAttribute('class', 'ld-drag-arc ld-c-' + (r.kind === 'SLEEP' ? 'sleep' : 'work'));
      dragOn(svg, hd, e, a => {
        cur = snap(a);
        if (cur.m !== lastM) { lastM = cur.m; buzz(); }
        const v = dragPreview(r, end, om, d0, cur);
        ghost.setAttribute('d', v.bad ? '' : arcD(DIAL.RO, angOf(v.ns, d0, d1), angOf(v.ne, d0, d1)));
        moveHandle(hd, angOf(cur.ep, d0, d1), DIAL.RO);
        core.innerHTML = '<small class="ld-core-k">' + esc(handleLabel(r, end)) + '</small><b class="ld-core-big">' + fHm(cur.m) + '</b><span class="ld-core-s">' + v.text + '</span><span class="ld-core-s">' + DRAG_WORDS.release + '</span>';
      }, cancelled => {
        ghost.setAttribute('d', '');
        if (cancelled || cur.m === om) { core.innerHTML = coreWas; moveHandle(hd, angOf(orig, d0, d1), DIAL.RO); return; }
        ui.pendingDial = dialChange(r, end, om, cur.m);
        confirmDial();
      });
    });
    svg.addEventListener('keydown', e => {
      const hd = e.target.closest && e.target.closest('.ld-hd'); if (!hd || (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight' && e.key !== 'ArrowUp' && e.key !== 'ArrowDown')) return;
      e.preventDefault(); e.stopPropagation();
      const r = (ST.availability.rules || []).find(x => x.id === hd.dataset.rule); if (!r) return;
      ui.pendingDial = dialStep(r, hd.dataset.end, e.key === 'ArrowRight' || e.key === 'ArrowUp' ? 1 : -1);
      confirmDial();
    });
  }
  /* what the confirmation of a pending dial change says (P: ui.pendingDial, r: its rule): a problem and OK, or Change it and Keep */
  function dialConfirm(P, r) {
    const isSleep = r.kind === 'SLEEP', what = handleLabel(r, P.end).toLowerCase();
    let problem = '';
    if (isSleep && P.durationMin < 300) problem = 'That leaves under 5 hours of sleep.';
    else if (isSleep && P.durationMin > 840) problem = 'That is more than 14 hours of sleep.';
    else if (!isSleep && (P.durationMin < 15 || P.durationMin > 1380)) problem = 'That makes ' + r.title + ' ' + (P.durationMin < 15 ? 'too short' : 'almost a full day') + '.';
    const who = isSleep ? 'your sleep (' + daysText(r) + ')' : r.title + ' (' + daysText(r) + ')';
    return {
      title: isSleep ? (P.end === 'start' ? 'Change your bedtime?' : 'Change your wake-up time?') : 'Change when ' + r.title + (P.end === 'start' ? ' starts?' : ' ends?'),
      from: fHm(P.from), to: fHm(P.to),
      problem: problem ? problem + ' Try a different time.' : '',
      body: problem ? '' : 'This changes the ' + what + ' for ' + who + ', not just this one day. Sessions that still fit stay put. Life Director uses new free time for unmet goals, and adjusts sessions if they no longer fit.',
      note: problem ? '' : 'Only today is different? Close this and use “Something came up” instead.',
      apply: problem ? '' : 'Change it', keep: problem ? 'OK' : 'Keep ' + fHm(P.from),
    };
  }
  function confirmDial() {
    const P = ui.pendingDial, r = P && (ST.availability.rules || []).find(x => x.id === P.ruleId);
    if (!r) return;
    const c = dialConfirm(P, r);
    openSheet('<h3>' + esc(c.title) + '</h3>' +
      '<p class="ld-change"><s>' + c.from + '</s><span aria-hidden="true">→</span><b>' + c.to + '</b></p>' +
      (c.problem ? '<p class="ld-note ld-warn-t">' + esc(c.problem) + '</p><div class="ld-row"><button class="ld-btn ld-primary" data-act="dialCancel">' + c.keep + '</button></div>' :
        '<p class="ld-muted">' + esc(c.body) + '</p>' +
        '<div class="ld-row"><button class="ld-btn ld-primary" data-act="dialApply">' + c.apply + '</button><button class="ld-btn ld-ghost" data-act="dialCancel">' + c.keep + '</button></div>' +
        '<p class="ld-muted ld-small-print">' + esc(c.note) + '</p>'));
  }
  /* Change it: the pending change becomes the rule, the plan is repaired around it and saved. Returns what happened: ok when the plan
     took it and it was saved, changed when the rule changed (also when the save then failed: it stays changed in memory, as the
     banner says), moved when sessions moved, and the page's message (or why nothing was saved). */
  function applyDial(now) {
    const P = ui.pendingDial, r = P && ST.availability.rules.find(x => x.id === P.ruleId);
    ui.pendingDial = null;
    if (!r) return { ok: false, changed: false, moved: false, message: 'There is no change to make. Nothing was changed.' };
    const before = { startMin: r.startMin, durationMin: r.durationMin };
    r.startMin = P.startMin; r.durationMin = P.durationMin;
    if (!ST.setupComplete) { const saved = persist(); closeSheet(); render(); return { ok: saved, changed: true, moved: false, message: saved ? '' : ui.banner.text }; }
    const old = ST.schedule, out = planTimeChange(buildInput(now));
    if (!applyOutput(out, 'Time adjusted', 'repair')) { Object.assign(r, before); persist(); closeSheet(); render(); return { ok: false, changed: false, moved: false, message: ui.banner.text }; }
    const positions = new Map((old ? old.scheduledInstances : []).map(i => [i.instanceId, i.startEpoch + ':' + i.endEpoch]));
    const changed = out.scheduledInstances.some(i => positions.get(i.instanceId) !== i.startEpoch + ':' + i.endEpoch) || positions.size !== out.scheduledInstances.length;
    if (!changed) dropBuild();
    const saved = persist(), message = handleLabel(r, P.end) + ' now ' + fHm(P.to) + (changed ? ' · schedule adjusted' : ' · sessions kept in place');
    closeSheet(); render();
    if (saved) say(message); // a refused save shows its banner alone: no "now 10:30 PM" under "Not saved"
    return { ok: saved, changed: true, moved: changed, message: saved ? message : ui.banner.text };
  }
  /* My time: the sleep dial. Handles move freely, a quarter hour at a time (MINI_SNAP, sent to a native screen too); the inputs follow; Save commits. */
  const MINI_SNAP = 15;
  function attachMini(mini) {
    mini.classList.add('ld-draggable');
    const svg = mini.querySelector('svg');
    const set = (bed, wake) => {
      mini.dataset.bed = bed; mini.dataset.wake = wake;
      const a0 = bed / 4, a1 = (wake <= bed ? wake + 1440 : wake) / 4;
      svg.querySelector('.ld-mini-arc').setAttribute('d', arcD(DIAL.RO, a0, a1));
      moveHandle(svg.querySelector('.ld-hd[data-end=start]'), a0, DIAL.RO); moveHandle(svg.querySelector('.ld-hd[data-end=end]'), a1 % 360, DIAL.RO);
      mini.querySelector('.ld-mini-dur').textContent = mins(((wake - bed) + 1440) % 1440 || 1440);
      mini.querySelector('.ld-mini-span').textContent = fHm(bed) + ' – ' + fHm(wake);
    };
    mini._set = set;
    svg.addEventListener('pointerdown', e => {
      const hd = e.target.closest('.ld-hd'); if (!hd) return;
      let last = -1;
      dragOn(svg, hd, e, a => {
        const m = Math.round(a * 4 / MINI_SNAP) * MINI_SNAP % 1440;
        if (m !== last) { last = m; buzz(); }
        let bed = +mini.dataset.bed, wake = +mini.dataset.wake;
        if (hd.dataset.end === 'start') bed = m; else wake = m;
        set(bed, wake);
        const inp = root.querySelector(hd.dataset.end === 'start' ? '#ldBed' : '#ldWake'); if (inp) inp.value = hm(m);
      }, () => { /* committed with Save sleep */ });
    });
  }

  /* ── focus timer: start a session, the clock runs even if you leave ── */
  const focusMs = f => Math.max(0, (f.paused || Date.now()) - f.start - (f.pausedMs || 0));
  function clockText(ms) { const s = Math.floor(ms / 1000), h = Math.floor(s / 3600), m = Math.floor(s / 60) % 60, x = s % 60; return (h ? h + ':' + String(m).padStart(2, '0') : String(m)) + ':' + String(x).padStart(2, '0'); }
  /* The running timer of session i in the page's words, with the times it runs from (ms), for the page's sheet and a native
     screen's; xp is the estimate now, as spans (the page bolds the XP) */
  function focusModel(f, i) {
    const plannedMs = f.plannedMs || Math.max(60, i.endEpoch - i.startEpoch) * 1000, x = xpOf(i, Math.max(1, Math.round(focusMs(f) / 60000)));
    return { id: i.instanceId, title: i.title, label: f.paused ? 'Paused' : 'In progress', paused: !!f.paused, startedAt: f.start, pausedMs: f.pausedMs || 0, pausedAt: f.paused || null,
      plannedMs, planned: 'of ' + mins(Math.round(plannedMs / 60000)) + ' planned',
      xp: x ? [{ t: 'Logging now earns about ', b: false }, { t: '+' + x.xp + ' XP', b: true }, { t: ' in ' + attrName(x.rule.primary) + '.', b: false }] : null,
      finish: 'Finish and log', pause: f.paused ? 'Resume' : 'Pause', stop: 'Stop without logging', note: 'You can leave this screen. The timer keeps running.' };
  }
  function focusSheet() {
    const f = ST.ui.focus, i = f && findInst(f.id);
    if (!i) { ST.ui.focus = null; persist(); render(); return; }
    const m = focusModel(f, i), C = 2 * Math.PI * 92, p = Math.min(1, focusMs(f) / m.plannedMs);
    openSheet('<div class="ld-focus' + (m.paused ? ' paused' : '') + '"><small class="ld-core-k ld-core-go">' + m.label + '</small><h3>' + esc(m.title) + '</h3>' +
      '<div class="ld-fring"><svg viewBox="0 0 220 220" aria-hidden="true"><circle class="ld-fr-trk" cx="110" cy="110" r="92"/><circle class="ld-fr-val" data-fring cx="110" cy="110" r="92" stroke-dasharray="' + C.toFixed(1) + '" stroke-dashoffset="' + (C * (1 - p)).toFixed(1) + '" transform="rotate(-90 110 110)"/></svg>' +
      '<div><b data-fclock>' + clockText(focusMs(f)) + '</b><small>' + m.planned + '</small></div></div>' +
      (m.xp ? '<p class="ld-muted ld-center">' + m.xp[0].t + '<b data-fxp>' + m.xp[1].t + '</b>' + esc(m.xp[2].t) + '</p>' : '') +
      '<div class="ld-row ld-center"><button class="ld-btn ld-primary" data-act="focusFinish">' + m.finish + '</button><button class="ld-btn" data-act="' + (m.paused ? 'focusResume' : 'focusPause') + '">' + m.pause + '</button></div>' +
      '<div class="ld-row ld-center"><button class="ld-btn ld-ghost ld-small" data-act="focusCancel">' + m.stop + '</button></div>' +
      '<p class="ld-muted ld-center ld-small-print">' + m.note + '</p></div>');
  }
  function focusTick() {
    const f = ST && ST.ui && ST.ui.focus; if (!f) return;
    const ms = focusMs(f);
    const inst = findInst(f.id);
    const target = f.plannedMs || (inst && Math.max(60, inst.endEpoch - inst.startEpoch) * 1000);
    if (!f.paused && target && ms >= target) {
      Today.timer({ act: 'autoFinish', id: f.id, start: f.start });
      return;
    }
    // the Overview's glance shows the running clock too: it ticks with Life Director's own
    document.querySelectorAll('.ld-glance [data-fclock]').forEach(el => { el.textContent = clockText(ms); });
    if (!root || !root.isConnected) return;
    root.querySelectorAll('[data-fclock]').forEach(el => { el.textContent = clockText(ms); });
    const ring = root.querySelector('[data-fring]');
    if (ring) { const i = findInst(f.id); if (i) { const C = 2 * Math.PI * 92, p = Math.min(1, ms / (Math.max(60, i.endEpoch - i.startEpoch) * 1000)); ring.setAttribute('stroke-dashoffset', (C * (1 - p)).toFixed(1));
      const xe = root.querySelector('[data-fxp]'); if (xe && ms % 15000 < 1000) { const x = xpOf(i, Math.max(1, Math.round(ms / 60000))); if (x) xe.textContent = '+' + x.xp + ' XP'; } } }
  }

  /* ── the guide: a spotlight walkthrough, shown once per screen, replayable with ? ──
     Each step points at one part of the screen. Steps whose part is not on screen
     (no handles today, no Up next card) are skipped. Presentation only. */
  const TOURS = {
    setup: [
      { sel: '.ld-steps', title: 'Life Director plans your days for you', text: 'Tell it when you’re busy. It fits your goals into the rest. Four steps, about a minute.', hint: true },
      { sel: '.ld-mini', round: true, pad: 4, mode: 'mini', title: 'Drag to set your sleep', text: 'Move the two handles, then press <b>Save sleep</b>.' },
      { sel: '.ld-card-commit', title: 'Add work or school', text: 'Anything at the same time every week. Shifts can rotate.' },
      { sel: '.ld-card-routines', title: 'Time around your day', text: 'Getting ready, winding down, meals. Use − and +.' },
      { sel: '.ld-card-goals', title: 'Pick what to work on', text: 'Life Director turns each goal into regular sessions. Quests are added for you.' },
      { sel: '.ld-build', title: 'Then build your week', text: 'Your plan appears on Today.' }
    ],
    today: [
      { sel: '.ld-dial', round: true, pad: 2, start: true, mode: 'clock', title: 'This is your day as a clock', text: 'Midnight is at the top and it runs clockwise. The blue hand is now; the greyed part has passed.', hint: true },
      { sel: '.ld-dial', round: true, pad: 2, start: true, mode: 'outer', title: 'Outer ring: time that’s taken', text: 'Sleep, work, travel and daily routines. Nothing gets planned here.' },
      { sel: '.ld-dial', round: true, pad: 2, start: true, mode: 'inner', title: 'Inner ring: what’s planned for you', text: 'Green blocks are sessions, peach ones are meals. Tap one to open it. Gaps are free time.' },
      { sel: '.ld-dial', round: true, pad: 2, start: true, mode: 'handles', need: '.ld-dial .ld-hd', title: 'Drag a handle to change your hours', text: 'It asks first, then re-plans your week.' },
      { sel: '.ld-dial .ld-core', round: true, pad: 14, mode: 'center', title: 'The middle tells you what to do now', text: 'How long is left, what’s next, and a heads-up in amber before something starts or ends.' },
      { sel: '.ld-up:not(.ld-up-rest)', title: 'Ready? Press Start', text: '<b>Start</b> runs a timer. <b>Done</b> logs it straight away.' },
      { sel: '.ld-foot', title: 'Plans changed?', text: 'Tap <b>Something came up</b>. Only what’s affected moves, with no penalty. Tap <b>?</b> at the top to see this again.' }
    ],
    week: [
      { sel: '.ld-week', title: 'Each bar is one day', text: 'Midnight to midnight, same colours as the dial. Tap a day to open it.' },
      { sel: '.ld-stats', title: 'Your week in numbers', text: 'Sessions, time, and XP if you do them all.' },
      { sel: '.ld-card-yield', title: 'Where your XP goes', text: 'Solid is earned. Outlined is still planned.' }
    ]
  };
  /* name tags pinned onto the dial for the step being explained */
  function tourLabels(T) {
    const st = T.steps[T.i], host = st.mode === 'mini' ? root.querySelector('.ld-mini') : root.querySelector('.ld-dial');
    root.querySelectorAll('.ld-tl').forEach(g => { if (!host || g.parentNode !== host.querySelector('svg') || g.dataset.k !== T.key + T.i) g.remove(); });
    if (!host || !st.mode || st.mode === 'center') return;
    const svg = host.querySelector('svg');
    if (svg.querySelector('.ld-tl')) return;
    let out = '';
    for (const l of tourTags(st, host)) out += '<g class="ld-tl-p' + (l.k ? ' ld-c-' + l.k : '') + '"><rect x="' + (l.x - l.w / 2).toFixed(1) + '" y="' + (l.y - 12).toFixed(1) + '" width="' + l.w.toFixed(1) + '" height="24" rx="12"/><text x="' + l.x.toFixed(1) + '" y="' + (l.y + 0.5).toFixed(1) + '">' + esc(l.txt) + '</text></g>';
    const g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    g.setAttribute('class', 'ld-tl'); g.dataset.k = T.key + T.i; g.innerHTML = out;
    svg.appendChild(g);
  }
  /* The name tags of guide step st on its dial (host: the page's .ld-dial, or .ld-mini): each pill's centre in the dial's 360-unit
     square, its width, words and colour key, placed so that no two overlap and none leaves the square. tourLabels draws them on
     the page; a native screen draws the same ones (nativeGuide). */
  function tourTags(st, host) {
    if (!host || !st.mode || st.mode === 'center') return [];
    const L = [], placed = [], tags = [];
    const add = (a, r, txt, k) => L.push({ a, r, txt, k });
    if (st.mode === 'mini') {
      add(+host.dataset.bed / 4, 166, 'Bedtime', 'sleep'); add(+host.dataset.wake / 4, 166, 'Wake up', 'sleep');
    } else {
      const d0 = +host.dataset.d0, d1 = +host.dataset.d1, mid = el => angOf((+el.dataset.s + +el.dataset.e) / 2, d0, d1);
      const span = el => +el.dataset.e - +el.dataset.s;
      if (st.mode === 'clock') {
        const nowA = host.dataset.today && Date.now() / 1000 >= d0 && Date.now() / 1000 < d1 ? angOf(Date.now() / 1000, d0, d1) : null;
        if (nowA != null) { add(nowA, 172, 'Now', 'now'); if (nowA > 40) add(nowA / 2, (DIAL.RO + DIAL.RI) / 2, 'Already passed'); }
      } else if (st.mode === 'outer') {
        const best = {};
        host.querySelectorAll('.ld-o[data-k]').forEach(el => { const k = el.dataset.k; if (!best[k] || span(el) > span(best[k])) best[k] = el; });
        const name = { sleep: 'Sleep', commute: 'Travel', maint: 'Routine', block: 'Blocked' };
        ['sleep', 'work', 'commute', 'maint', 'block'].forEach(k => { const el = best[k]; if (el && !el.dataset.lbl) add(mid(el), DIAL.RO, k === 'work' ? (el.dataset.t || 'Work') : name[k], k); });
      } else if (st.mode === 'inner') {
        { const m = host.querySelector('.ld-meal[data-s]'); if (m) add(mid(m), DIAL.RI, 'Meal', 'meal'); }
        [['plan', 'Session', 'task'], ['done', 'Done', 'task'], ['off', 'Missed', 'block']].forEach(([s, txt, k]) => { const el = host.querySelector('.ld-ses.ld-s-' + s); if (el) add(mid(el), DIAL.RI, txt, k); });
        const r = host.querySelector('.ld-rest[data-s]'); if (r) add(mid(r), DIAL.RI, 'Downtime', 'rest');
      } else if (st.mode === 'handles') {
        host.querySelectorAll('.ld-hd').forEach(h => { const r = (ST.availability.rules || []).find(x => x.id === h.dataset.rule); if (r) add(angOf(+h.dataset.ep, d0, d1), 166, handleLabel(r, h.dataset.end), r.kind === 'SLEEP' ? 'sleep' : 'work'); });
      }
    }
    for (const l of L) {
      let [x, y] = polar(l.r, l.a);
      if (placed.some(([px, py]) => Math.abs(px - x) < 58 && Math.abs(py - y) < 24)) continue;
      const w = l.txt.length * 6.7 + 18;
      x = Math.max(w / 2 + 4, Math.min(356 - w / 2, x)); y = Math.max(14, Math.min(346, y));
      placed.push([x, y]);
      tags.push({ x, y, w, txt: l.txt, k: l.k || '' });
    }
    return tags;
  }
  function tourSteps(key) { return (TOURS[key] || []).filter(s => (!s.sel || root.querySelector(s.sel)) && (!s.need || root.querySelector(s.need))); }
  function tourCanRun(key) {
    if (key === 'today') return !!root.querySelector('.ld-dial');
    if (key === 'setup') return !ST.setupComplete;
    if (key === 'week') return !!root.querySelector('.ld-week');
    return false;
  }
  /* this screen's guide has not been seen yet, so it starts on its own */
  const tourDue = key => !!TOURS[key] && !(ST.ui.tours || {})[key] && tourCanRun(key);
  /* first visit to a screen: offer its guide once */
  function tourAuto() {
    if (ui.tour || ui.drag) return;
    const key = ui.tab;
    if (!tourDue(key) || nativeGuides(key)) return; // a native screen draws this guide itself
    if (!root.querySelector('.ld-sheet').hidden) return;
    clearTimeout(ui.tourT);
    ui.tourT = setTimeout(() => { if (ui.tab === key && !ui.tour && root.querySelector('.ld-sheet').hidden) tourStart(key); }, 500);
  }
  function tourStart(key) {
    const steps = tourSteps(key);
    if (!steps.length) return;
    let ov = root.querySelector('.ld-tour');
    if (!ov) {
      ov = document.createElement('div'); ov.className = 'ld-tour'; ov.setAttribute('role', 'dialog'); ov.setAttribute('aria-modal', 'true'); ov.setAttribute('aria-labelledby', 'ldTourT');
      ov.innerHTML = '<div class="ld-tour-spot" aria-hidden="true"></div><div class="ld-tour-card"><div class="ld-tour-top"><span class="ld-tour-n"></span><button class="ld-tour-skip" data-act="tourEnd">Skip guide</button></div>' +
        '<h3 id="ldTourT"></h3><div class="ld-tour-body"></div><div class="ld-tour-foot"><div class="ld-tour-dots" aria-hidden="true"></div><div class="ld-tour-btns"><button class="ld-btn ld-ghost ld-small" data-act="tourBack">Back</button><button class="ld-btn ld-primary ld-small" data-act="tourNext">Next</button></div></div></div>';
      root.appendChild(ov);
      ov.addEventListener('click', e => { if (ui.tour && !e.target.closest('.ld-tour-card')) tourMove(1); });
    }
    ui.tour = { key, steps, i: 0 };
    ov.hidden = false; void ov.offsetWidth; ov.classList.add('on');
    tourShow();
    changed();
  }
  function tourShow() {
    const T = ui.tour; if (!T) return;
    const st = T.steps[T.i], ov = root.querySelector('.ld-tour');
    ov.querySelector('.ld-tour-n').textContent = (T.i + 1) + ' of ' + T.steps.length;
    ov.querySelector('h3').textContent = st.title;
    ov.querySelector('.ld-tour-body').innerHTML = '<p>' + st.text + '</p>' + (st.hint ? '<p class="ld-tour-hint">Tap anywhere to continue</p>' : '');
    ov.querySelector('.ld-tour-dots').innerHTML = T.steps.map((_, k) => '<i' + (k === T.i ? ' class="on"' : k < T.i ? ' class="past"' : '') + '></i>').join('');
    const back = ov.querySelector('[data-act=tourBack]'), next = ov.querySelector('[data-act=tourNext]');
    back.hidden = T.i === 0; next.textContent = T.i === T.steps.length - 1 ? (T.key === 'setup' ? 'Let’s go' : 'Got it') : 'Next';
    const el = st.sel ? root.querySelector(st.sel) : null;
    if (el) { try { el.scrollIntoView({ block: st.start ? 'start' : 'center', behavior: 'auto' }); } catch (e) { el.scrollIntoView(); } }
    tourPlace();
    /* LifeMax can still be settling its own scroll when the guide opens: aim again once it has */
    const at = T.i;
    setTimeout(() => { if (ui.tour === T && T.i === at && el && el.isConnected) { try { el.scrollIntoView({ block: st.start ? 'start' : 'center', behavior: 'auto' }); } catch (e) { /* ignore */ } tourPlace(); } }, 160);
    const card = ov.querySelector('.ld-tour-card');
    play(card, 'ld-tour-in');
    setTimeout(() => { try { next.focus({ preventScroll: true }); } catch (e) { /* ignore */ } }, 30);
  }
  /* put the spotlight on the step's part and the card wherever it fits */
  function tourPlace() {
    const T = ui.tour; if (!T) return;
    const ov = root.querySelector('.ld-tour'); if (!ov) return;
    const st = T.steps[T.i], spot = ov.querySelector('.ld-tour-spot'), card = ov.querySelector('.ld-tour-card');
    const dial = root.querySelector('.ld-dial');
    if (dial) { if (st.mode && st.mode !== 'mini') dial.dataset.tour = st.mode; else delete dial.dataset.tour; }
    tourLabels(T);
    const vw = window.innerWidth, vh = window.innerHeight, cw = Math.min(vw - 24, 420);
    card.style.width = cw + 'px'; card.style.left = Math.round((vw - cw) / 2) + 'px';
    const ch = card.offsetHeight;
    const el = st.sel ? root.querySelector(st.sel) : null;
    if (!el) {
      ov.classList.add('ld-tour-plain');
      card.style.top = Math.max(12, Math.round((vh - ch) / 2)) + 'px';
      return;
    }
    ov.classList.remove('ld-tour-plain');
    const r = el.getBoundingClientRect(), pad = st.pad != null ? st.pad : 8;
    const box = { l: r.left - pad, t: r.top - pad, w: r.width + pad * 2, h: r.height + pad * 2 };
    spot.style.left = box.l + 'px'; spot.style.top = box.t + 'px'; spot.style.width = box.w + 'px'; spot.style.height = box.h + 'px';
    spot.style.borderRadius = st.round ? '50%' : '18px';
    const gap = 12, below = vh - (box.t + box.h), above = box.t;
    let top;
    if (below >= ch + gap * 2) top = box.t + box.h + gap;
    else if (above >= ch + gap * 2) top = box.t - gap - ch;
    else top = vh - ch - 12;
    card.style.top = Math.max(12, Math.min(vh - ch - 12, top)) + 'px';
  }
  function tourEnd(finished) {
    const T = ui.tour; if (!T) return;
    ST.ui.tours = Object.assign({}, ST.ui.tours, { [T.key]: true });
    persist();
    ui.tour = null;
    const ov = root.querySelector('.ld-tour'), dial = root.querySelector('.ld-dial');
    if (dial) delete dial.dataset.tour;
    root.querySelectorAll('.ld-tl').forEach(g => g.remove());
    if (ov) { ov.classList.remove('on'); setTimeout(() => { if (!ui.tour) ov.hidden = true; }, reduced() ? 0 : 220); }
    if (T.key === 'today' || T.key === 'setup') { try { root.scrollIntoView({ block: 'start' }); } catch (e) { /* ignore */ } }
    if (!finished) say('Guide closed. Tap ? at the top to see it again.');
    changed();
  }
  function tourMove(d) {
    const T = ui.tour; if (!T) return;
    const j = T.i + d;
    if (j < 0) return;
    if (j >= T.steps.length) { tourEnd(true); return; }
    T.i = j; tourShow();
  }
  /* the guide the ? button plays: this screen's, or Today's (set-up's until the week is built) */
  const helpKey = () => TOURS[ui.tab] && (ui.tab === 'setup' || tourCanRun(ui.tab)) ? ui.tab : ST.setupComplete && ST.schedule ? 'today' : 'setup';
  /* the ? button: that guide, on its own screen */
  function tourHelp() {
    const key = helpKey();
    if (key !== ui.tab) { ui.tab = key; ST.ui.tab = key; persist(); render(); }
    tourStart(key);
  }

  /* ── keep the live parts current between renders ── */
  function liveTick() {
    if (!root || !root.isConnected) return;
    const now = nowEpoch();
    const ck = root.querySelector('[data-clock]'); if (ck) ck.textContent = fTime(now);
    if (ui.drag || ui.tour || ui.bfRun) return;
    const dial = root.querySelector('.ld-dial[data-today]');
    if (!dial) return;
    const d0 = +dial.dataset.d0, d1 = +dial.dataset.d1;
    const sheetOpen = !root.querySelector('.ld-sheet').hidden;
    if (now >= d1) { if (!sheetOpen) { tick(); render(); } return; }
    updateDialHand(dial.querySelector('.ld-hand'), angOf(now, d0, d1));
    /* a block started or ended: redraw so "now" and the running order move on */
    const stale = ui.boundary && now >= ui.boundary;
    if (stale && !sheetOpen) { render(); return; }
    if ((!ST.ui.focus || ST.ui.focus.paused) && ST.schedule) {
      const days = horizonDays(ST.schedule, tz()), day = days[0];
      if (day && ui.dayIdx === 0) {
        dial.querySelector('.ld-core').innerHTML = coreHtml(day, dayBlocks(ST.schedule, day, tz()), true, now);
        const ring = dial.querySelector('.ld-cur'); if (ring) ring.innerHTML = curRingHtml(nowState(now), now);
      }
      alertBuzz(now);
    }
  }
  /* the next moment when the running order changes (a block starts or ends) */
  function nextBoundary() {
    if (!ST.schedule) return null;
    const now = nowEpoch(), days = horizonDays(ST.schedule, tz()), day = days[0];
    if (!day) return null;
    let b = day.endEpoch;
    for (const x of dayBlocks(ST.schedule, day, tz())) { if (x.startEpoch > now) b = Math.min(b, x.startEpoch); if (x.endEpoch > now) b = Math.min(b, x.endEpoch); }
    return b;
  }
  /* Where a session can move to: every half hour of free time (or of its own slot) from the next quarter hour on that fits it,
     the first 48, each with the page's label. One list for the page's Move sheet and a native screen's. */
  function moveTimes(inst) {
    const s = ST.schedule, days = horizonDays(s, tz()), now = nowEpoch(), dur = inst.endEpoch - inst.startEpoch;
    const opts = [];
    days.forEach(d => {
      const blocks = dayBlocks(s, d, tz()).filter(b => b.kind === 'FREE' || (b.inst && b.inst.instanceId === inst.instanceId));
      const merged = [];
      for (const b of blocks.sort((a, c) => a.startEpoch - c.startEpoch)) { const l = merged[merged.length - 1]; if (l && l.endEpoch >= b.startEpoch) l.endEpoch = Math.max(l.endEpoch, b.endEpoch); else merged.push({ startEpoch: b.startEpoch, endEpoch: b.endEpoch }); }
      for (const g of merged) for (let t = Math.max(g.startEpoch, Math.ceil((now + 900) / 900) * 900); t + dur <= g.endEpoch; t += 1800) if (t !== inst.startEpoch) opts.push(t);
    });
    return opts.slice(0, 48).map(t => ({ label: fDay(t).replace(/,.*/, '') + ' ' + fTime(t), start: t }));
  }
  const moveModel = inst => ({ id: inst.instanceId, title: 'Move “' + inst.title + '”', times: moveTimes(inst), none: 'No open time fits this week.',
    note: 'Open times that fit ' + mins((inst.endEpoch - inst.startEpoch) / 60) + '. The moved session is pinned so automatic re-planning leaves it alone.' });
  function moveSheet(inst) {
    const m = moveModel(inst);
    openSheet('<h3>' + esc(m.title) + '</h3><p class="ld-muted">' + m.note + '</p><div class="ld-times">' +
      (m.times.map(o => '<button class="ld-btn ld-small" data-act="moveTo" data-id="' + esc(m.id) + '" data-v="' + o.start + '">' + o.label + '</button>').join('') || '<p>' + m.none + '</p>') + '</div>');
  }
  /* Something came up, as parts: what each asks for and offers, in the page's words. The page's sheet draws them and a native screen
     gets them as they are; the actions check a choice against them (Today.disrupt, Today.energy). Times are minutes after midnight. */
  const DISRUPT = { title: 'Something came up', note: 'Nothing here counts against you. Life Director only moves what it has to.' };
  function disruptParts() {
    const field = (key, kind, label, value, more) => Object.assign({ key, kind, label, value }, kind === 'time' ? { text: fHm(value) } : {}, more);
    return [
      { key: 'work', title: 'Called into work or an extra commitment', open: true, command: 'disrupt', button: 'Add and re-plan', form: [
        field('date', 'date', 'Date', U.ymdKey(U.localParts(nowEpoch(), tz())), { group: 0 }), field('commute', 'number', 'Travel each way (min)', 0, { min: 0, step: 5, group: 0 }),
        field('from', 'time', 'From', 17 * 60, { group: 1 }), field('until', 'time', 'Until', 21 * 60, { group: 1 })] },
      { key: 'block', title: 'Lose the next few hours', command: 'disrupt', param: 'hours', options: [1, 2, 3, 4, 6].map(h => ({ value: h, label: h + 'h' })) },
      { key: 'day', title: 'Today is a write-off', command: 'disrupt', button: 'Clear the rest of today' },
      { key: 'sick', title: 'I’m sick', command: 'disrupt', param: 'days', options: [1, 2, 3].map(n => ({ value: n, label: n + ' day' + (n > 1 ? 's' : '') })),
        note: 'Demanding sessions are cleared and only must-do work is moved to after you recover.' },
      { key: 'energy', title: 'Energy check-in', command: 'energy', param: 'level', options: [['LOW', 'Running low'], ['NORMAL', 'Normal'], ['HIGH', 'Full of energy']].map(([value, label]) => ({ value, label })),
        note: 'Adjusts estimates for the next several hours, then fades out.' },
    ];
  }
  const DISRUPT_ACT = { work: 'dWork', block: 'dBlock', day: 'dDay', sick: 'dSick', energy: 'dEnergy' };
  const DISRUPT_INPUT = { date: 'dDate', commute: 'dCommute', from: 'dStart', until: 'dEnd' };
  function disruptSheet() {
    const input = f => '<label>' + f.label + '<input type="' + f.kind + '" id="' + DISRUPT_INPUT[f.key] + '" value="' + (f.kind === 'time' ? hm(f.value) : f.value) + '"' +
      (f.min !== undefined ? ' min="' + f.min + '"' : '') + (f.step ? ' step="' + f.step + '"' : '') + '></label>';
    const rows = form => [0, 1].map(g => '<div class="ld-grid2">' + form.filter(f => f.group === g).map(input).join('') + '</div>').join('');
    openSheet('<h3>' + DISRUPT.title + '</h3><p class="ld-muted">' + DISRUPT.note + '</p>' + disruptParts().map(s => '<details class="ld-details"' + (s.open ? ' open' : '') + '><summary>' + s.title + '</summary>' +
      (s.form ? rows(s.form) : '') + (s.options ? '<div class="ld-row">' + s.options.map(o => '<button class="ld-btn ld-small" data-act="' + DISRUPT_ACT[s.key] + '" data-v="' + o.value + '">' + o.label + '</button>').join('') + '</div>' : '') +
      (s.button ? '<button class="ld-btn" data-act="' + DISRUPT_ACT[s.key] + '">' + s.button + '</button>' : '') + (s.note ? '<p class="ld-muted">' + s.note + '</p>' : '') + '</details>').join(''));
  }

  /* ── sheet ── */
  let sheetTok = 0;
  function openSheet(html) {
    const sh = root.querySelector('.ld-sheet'), panel = sh.querySelector('.ld-sheet-panel'), body = sh.querySelector('.ld-sheet-body');
    grabber(sh);
    const wasOpen = !sh.hidden && sh.classList.contains('open');
    sheetTok++;
    if (wasOpen && !reduced()) {
      /* already open (e.g. goal type → goal form): morph the height and slide the new content in */
      const h0 = panel.offsetHeight;
      body.innerHTML = html; panel.scrollTop = 0;
      const h1 = panel.offsetHeight;
      play(body, 'ld-swap');
      if (Math.abs(h1 - h0) > 2) {
        panel.style.height = h0 + 'px'; void panel.offsetHeight;
        panel.classList.add('ld-sizing'); panel.style.height = h1 + 'px';
        const end = () => { panel.classList.remove('ld-sizing'); panel.style.height = ''; };
        clearTimeout(panel._st); panel._st = setTimeout(end, 340);
      }
    } else {
      body.innerHTML = html;
      panel.style.transform = ''; panel.style.transition = '';
      sh.hidden = false; void sh.offsetWidth; sh.classList.add('open');
    }
    const f = sh.querySelector('.ld-sheet-body button, .ld-sheet-body input, .ld-sheet-body select, .ld-x'); if (f) setTimeout(() => f.focus({ preventScroll: true }), 60);
    changed();
  }
  function closeSheet() {
    const sh = root.querySelector('.ld-sheet');
    if (sh.hidden || !sh.classList.contains('open')) return;
    const tok = ++sheetTok, panel = sh.querySelector('.ld-sheet-panel'), bg = sh.querySelector('.ld-sheet-bg');
    sh.classList.remove('open'); panel.style.transform = ''; panel.style.transition = ''; bg.style.opacity = ''; bg.style.transition = '';
    const fin = () => { if (tok === sheetTok) sh.hidden = true; };
    if (reduced()) fin(); else setTimeout(fin, 280);
    changed();
  }
  /* drag the handle down to dismiss */
  function grabber(sh) {
    const panel = sh.querySelector('.ld-sheet-panel');
    if (panel.querySelector('.ld-grab')) return;
    const g = document.createElement('div'); g.className = 'ld-grab'; g.setAttribute('aria-hidden', 'true');
    panel.insertBefore(g, panel.firstChild);
    const bg = sh.querySelector('.ld-sheet-bg');
    let y0 = 0, dy = 0, t0 = 0, vy = 0, lastY = 0, lastT = 0, dragging = false;
    g.addEventListener('pointerdown', e => {
      if (!sh.classList.contains('open')) return;
      e.preventDefault(); /* keep focus inside the sheet */
      dragging = true; y0 = lastY = e.clientY; t0 = lastT = performance.now(); dy = 0; vy = 0;
      g.setPointerCapture(e.pointerId); panel.style.transition = 'none'; bg.style.transition = 'none';
    });
    g.addEventListener('pointermove', e => {
      if (!dragging) return;
      const now = performance.now(); dy = e.clientY - y0;
      if (now - lastT > 0) vy = (e.clientY - lastY) / (now - lastT);
      lastY = e.clientY; lastT = now;
      const d = dy > 0 ? dy : dy * 0.18;
      panel.style.transform = 'translateY(' + d + 'px)';
      bg.style.opacity = String(Math.max(0, 1 - Math.max(0, dy) / (panel.offsetHeight || 1)));
    });
    const end = () => {
      if (!dragging) return; dragging = false;
      panel.style.transition = ''; bg.style.transition = '';
      if (dy > Math.min(140, panel.offsetHeight * 0.3) || vy > 0.55) closeSheet();
      else { panel.style.transform = ''; bg.style.opacity = ''; }
    };
    g.addEventListener('pointerup', end); g.addEventListener('pointercancel', end);
  }
  const findInst = id => ST.schedule && (ST.schedule.scheduledInstances.find(i => i.instanceId === id) || (ST.schedule.historyInstances || []).find(i => i.instanceId === id));

  /* ── actions ── */
  function onClick(e) {
    const b = e.target.closest('[data-act]');
    if (!b || !root.contains(b) || busy) return;
    /* the finger lifting off a dial handle must not count as a tap on whatever is under it */
    if (ui.dragEnd && Date.now() - ui.dragEnd < 400 && !b.closest('.ld-sheet')) return;
    if (b.tagName === 'SELECT' || (b.tagName === 'INPUT' && b.type !== 'checkbox')) return;
    if ((b.dataset.act === 'dismissBanner' || b.dataset.act === 'dismissHost') && !e.ldLeft) {
      if (leave(b.closest('.ld-banner'), () => onClick({ target: b, ldLeft: true }))) return;
    }
    perform(b);
  }
  /* what the element b with data-act does; b may also be an object that carries only a dataset (see nativeAct) */
  /* ── changes to goals and settings, shared by the page's own controls and a native screen's commands ──
     Each changes what its control changes, re-plans or saves as the control does, renders, and answers {ok, message}: ok only
     when the write went through (a refused write leaves the page's "Not saved" banner, and its words are the message). */
  const saved = (message) => (lastSave.ok ? { ok: true, message } : { ok: false, message: 'Not saved: ' + lastSave.error });
  const goalById = (id) => ST.goals.find(x => x.id === String(id));
  function replanOrSave(reason) { if (ST.setupComplete) regenerate(reason, true); else persist(); }
  const refuse = (message) => ({ ok: false, changed: false, message }); // nothing was changed, and why
  const NO_OP = 'Nothing was added.'; // a new commitment or goal asked for without its operation id
  const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
  const Do = Object.freeze({
    goalPriority({ id, value }) {
      const g = goalById(id), v = Number(value);
      if (!g) return { ok: false, message: 'That goal is gone.' };
      if (![1, 2, 3, 4, 5].includes(v)) return { ok: false, message: 'That is not a priority.' };
      g.priority = v; regenerate('priority', true); render(); return saved('');
    },
    /* paused: the state wanted (a screen sends it, so a repeat changes nothing); without it, the page's toggle */
    goalPause({ id, paused }) {
      const g = goalById(id);
      if (!g) return { ok: false, message: 'That goal is gone.' };
      const want = typeof paused === 'boolean' ? paused : g.status !== 'PAUSED';
      if (want === (g.status === 'PAUSED')) return { ok: true, message: '' };
      g.status = want ? 'PAUSED' : 'ACTIVE'; regenerate('goal changed', true); render(); return saved('');
    },
    goalRemove({ id }) {
      if (!goalById(id)) return { ok: false, message: 'That goal is gone.' };
      ST.goals = ST.goals.filter(x => x.id !== String(id)); regenerate('goal removed', true); render(); return saved('');
    },
    clarify({ id, arch }) {
      const g = goalById(id);
      if (!g) return { ok: false, message: 'That goal is gone.' };
      if (!E.ARCHETYPES[arch]) return { ok: false, message: 'That is not a kind of goal.' };
      g.archetypeId = arch; regenerate('goal clarified', true); render(); return saved('');
    },
    pref({ key, value }) {
      const allowed = { intensityLevel: INTENSITY.options, planningHorizonDays: HORIZON, consequenceMode: MISSES.options }[key];
      if (!allowed || !allowed.some(([v]) => String(v) === String(value))) return { ok: false, message: 'That is not a setting.' };
      ST.prefs[key] = isNaN(+value) ? value : +value;
      if (key === 'intensityLevel') ST.prefsLightUntil = null;
      replanOrSave('settings'); render(); return saved('');
    },
    prefRange({ key, value }) {
      const r = PREF_RANGES.concat([GRACE]).find(x => x[0] === key), v = Number(value);
      if (!r || !Number.isFinite(v) || v < r[1] || v > r[2] || (v - r[1]) % r[3]) return { ok: false, message: 'That is not a setting.' };
      ST.prefs[key] = v; replanOrSave('settings'); render(); return saved('');
    },
    /* on: the state wanted (a screen sends it, so a repeat changes nothing); without it, the page's toggle */
    restDay({ day, on }) {
      const k = Number(day);
      if (!Number.isInteger(k) || k < 0 || k > 6) return { ok: false, message: 'That is not a day.' };
      const p = E.normalizePreferences(ST.prefs), has = p.preferredRestDays.includes(k), want = typeof on === 'boolean' ? on : !has;
      if (want === has) return { ok: true, message: '' };
      ST.prefs.preferredRestDays = has ? p.preferredRestDays.filter(x => x !== k) : p.preferredRestDays.concat(k);
      replanOrSave('settings'); render(); return saved('');
    },
    labToggle({ on }) { ST.ui.lab = !!on; persist(); render(); return saved(''); },
    tourReset() {
      ST.ui.tours = {}; persist();
      if (ST.setupComplete && ST.schedule) { ui.tab = 'today'; ST.ui.tab = 'today'; ui.dayIdx = 0; } else ui.tab = 'setup';
      render(); return saved('');
    },
    /* the backup as text; copying it is the caller's (the page's clipboard, or the phone's) */
    exportBackup() { return { ok: true, message: '', text: store.exportJson(ST) }; },
    importBackup({ text }) {
      let next;
      try { next = store.importJson(String(text || '')); } catch (err) { return { ok: false, message: err.message }; }
      const was = ST; ST = next;
      if (!persist()) { ST = was; render(); return { ok: false, message: 'Not saved: ' + lastSave.error + ' Nothing was restored.' }; }
      render(); say('Backup restored'); return { ok: true, message: 'Backup restored' };
    },
    reset() { store.clear(); boot(); render(); return { ok: true, message: '' }; },
    /* ── My time (phase 4B). Each answers {ok, changed, message}: a refusal changes nothing and its words are the message; a change
       is ok only when saved, and the page's notice says what happened; the same values again change nothing (changed: false). ── */
    /* bed and wake: minutes after midnight; weekend: Friday and Saturday nights' {bed, wake}, or null for every night the same */
    sleepSave({ bed, wake, weekend }) {
      const b = dayMinute(bed), w = dayMinute(wake), we = weekend ? [dayMinute(weekend.bed), dayMinute(weekend.wake)] : null;
      if (b == null || w == null || (we && (we[0] == null || we[1] == null))) return refuse('Enter both times');
      const dur = ((w - b) + 1440) % 1440 || 480;
      if (dur < 300) return refuse('That is under 5 hours of sleep. Please check the times.');
      const sleep = (id, days, startMin, durationMin) => ({ id, kind: 'SLEEP', title: 'Sleep', days, startMin, durationMin });
      const rules = !we ? [sleep('sleep-wk', [0, 1, 2, 3, 4, 5, 6], b, dur)] : [sleep('sleep-wk', [0, 1, 2, 3, 4], b, dur), sleep('sleep-we', [5, 6], we[0], ((we[1] - we[0]) + 1440) % 1440 || 480)];
      if (same(rules, sleepRules()) && ST.availability.sleepRequirementMinutes === dur) return recorded('');
      ST.availability.rules = ST.availability.rules.filter(r => r.kind !== 'SLEEP').concat(rules);
      ST.availability.sleepRequirementMinutes = dur; autoReplan('sleep changed'); render(); say(ST.setupComplete ? 'Sleep saved · plan rebuilt' : 'Sleep saved');
      return applied();
    },
    /* a commitment: the commitment sheet's fields by key (ruleForm). A new one (no id) is named from op, so the same request again
       finds it ("Already added"); an edit names its commitment, and one removed since is refused rather than added again. */
    ruleSave({ id, op, title, kind, rotating, days, on, off, anchor, start, end, commute, unwind, load }) {
      const s0 = dayMinute(start), e0 = dayMinute(end), rot = rotating === true;
      if (s0 == null || e0 == null) return refuse('Enter start and end times');
      const picked = Array.isArray(days) ? days.filter(d => Number.isInteger(d) && d >= 0 && d <= 6) : [];
      if (!rot && !picked.length) return refuse('Pick at least one day');
      if (rot && !/^\d{4}-\d{2}-\d{2}$/.test(String(anchor || ''))) return refuse('Pick the first day on');
      if (!RULE_KINDS.some(([k]) => k === kind) || !RULE_LOADS.some(([k]) => k === load)) return refuse('That is not one of the choices.');
      const was = id ? ST.availability.rules.find(r => r.id === String(id) && r.kind !== 'SLEEP' && r.kind !== 'PREFERRED') : null;
      if (id && !was) return refuse('That commitment was removed.');
      if (!id && !op) return refuse(NO_OP);
      const rid = id ? String(id) : 'r-' + op;
      if (!id && ST.availability.rules.some(r => r.id === rid)) return recorded('Already added');
      const n = Math.max(1, Number(on) || 4), f = Math.max(1, Number(off) || 4);
      const rule = { id: rid, kind, title: String(title || '').trim() || KIND_LABEL[kind], days: rot ? [0, 1, 2, 3, 4, 5, 6] : picked, startMin: s0, durationMin: ((e0 - s0) + 1440) % 1440 || 1440,
        commuteMin: Math.max(0, Math.min(240, Number(commute) || 0)), decompressMin: Math.max(0, Math.min(120, Number(unwind) || 0)), load,
        rotation: rot ? { anchorDate: String(anchor), cycleLength: n + f, activeDays: Array.from({ length: n }, (_, k) => k) } : null };
      if (was && same(was, rule)) return recorded('');
      const at = ST.availability.rules.findIndex(r => r.id === rid);
      if (at >= 0) ST.availability.rules[at] = rule; else ST.availability.rules.push(rule);
      autoReplan(at >= 0 ? 'commitment changed' : 'commitment added'); render();
      say(at >= 0 ? 'Saved' + (ST.setupComplete ? ' · plan rebuilt' : '') : 'Commitment added' + (ST.setupComplete ? ' · plan rebuilt' : ''));
      return applied();
    },
    /* a commitment removed; one already gone changes nothing */
    ruleRemove({ id }) {
      const r = ST.availability.rules.find(x => x.id === String(id));
      if (!r) return recorded('');
      if (r.kind === 'SLEEP' || r.kind === 'PREFERRED') return refuse('That is not a commitment.');
      ST.availability.rules = ST.availability.rules.filter(x => x.id !== r.id); autoReplan('commitment removed'); render(); say('Removed'); return applied();
    },
    /* a daily routine's minutes (getting ready, wind-down, meals), absolute; the page keeps it within the stepper's limits */
    routine({ key, value }) {
      const r = ROUTINES.concat([MEAL_RANGE]).find(x => x[0] === key), v = Number(value);
      if (!r || !Number.isFinite(v)) return refuse('That is not a setting.');
      const want = Math.max(r[1], Math.min(r[2], Math.round(v)));
      if (E.normalizePreferences(ST.prefs)[key] === want) return recorded('');
      ST.prefs[key] = want; autoReplan('routines changed'); render(); return applied();
    },
    /* meal blocks on or off: on is the state wanted (a screen sends it, so a repeat changes nothing); without it, the page's toggle */
    meals({ on }) {
      const has = !!E.normalizePreferences(ST.prefs).mealsEnabled, want = typeof on === 'boolean' ? on : !has;
      if (want === has) return recorded('');
      ST.prefs.mealsEnabled = want; autoReplan('routines changed'); render(); return applied();
    },
    /* a new goal: the goal form's fields by key (goalFormModel), for the kind arch. Its id is named from op (its first 12 characters,
       a UUID's), so the same request again finds it ("Already added"). */
    goalAdd({ op, arch, title, subject, tag, minutes, perWeek, physical, mental, priority, level, units, unit, rate, deadline }) {
      const a = E.ARCHETYPES[arch], name = String(title || '').trim(), prio = Number(priority) || 3, lvl = level || 'NOVICE';
      if (!a && arch !== 'CUSTOM') return refuse('That is not a kind of goal.');
      if (!name) return refuse('Give the goal a name');
      if (![1, 2, 3, 4, 5].includes(prio) || !GOAL_LEVELS.some(([k]) => k === lvl) || (!a && !CUSTOM_TAGS.some(([t]) => t === tag))) return refuse('That is not one of the choices.');
      if (!op) return refuse(NO_OP);
      const id = 'g-' + String(op).slice(0, 12);
      if (goalById(id)) return recorded('Already added');
      const g = { id, title: name, priority: prio, level: lvl, status: 'ACTIVE', createdEpoch: nowEpoch() };
      if (a) g.archetypeId = arch; else g.customTemplates = [{ title: name, tag, minutes: Number(minutes) || 30, perWeek: Number(perWeek) || 3, IP: Number(physical) || 2, IC: Number(mental) || 2 }];
      if (arch === 'SKILL_LANGUAGE') g.subject = String(subject || '').trim() || null;
      const u = Number(units), per = Number(rate);
      if (goalFormModel(arch).fold && u > 0 && per > 0 && /^\d{4}-\d{2}-\d{2}$/.test(String(deadline || ''))) {
        const [y, mo, d] = String(deadline).split('-').map(Number);
        g.target = { units: u, completed: 0, unit: unit || 'units', unitsPerHour: per, deadlineEpoch: U.zonedToEpoch(y, mo, d, 1439, tz()) };
      }
      ST.goals.push(g);
      if (ST.setupComplete) regenerate('goal added', true); else persist();
      render(); say('Goal added'); return applied();
    },
    /* Build my week (and, once set up, Re-plan now): needs sleep first; a second run is a re-plan */
    finishSetup() {
      if (!ST.availability.rules.some(r => r.kind === 'SLEEP')) return refuse('Save your sleep times first');
      ST.setupComplete = true; if (regenerate('setup')) { ui.tab = 'today'; ST.ui.tab = 'today'; ui.dayIdx = 0; } persist(); render(); return applied();
    },
  });
  /* the page's own My time forms: a refusal is said and the sheet stays open (false); anything else lets it close (true) */
  const told = r => { if (!r.ok && r.changed === false) { if (r.message) say(r.message); return false; } return true; };
  /* a new commitment or goal from the page's own form: its operation id is drawn only once the form is complete (asked without one,
     a complete form is refused for the missing id alone), so the page never spends an id on a refusal, as before Do */
  const addFrom = (f, form) => { const r = f(form); return r.message === NO_OP ? f(Object.assign(form, { op: uuid() })) : r; };

  /* the page's own Something came up: a new operation each tap; the sheet closes once the plan has changed, and a refusal the
     repair has not said already is said */
  function disruptFrom(p) { const r = Today.disrupt(Object.assign({ op: uuid() }, p)); if (r.changed) closeSheet(); else if (!r.said) say(r.message); }
  function perform(b) {
    const act = b.dataset.act, id = b.dataset.id, v = b.dataset.v;
    if (act === 'doneQuick' || act === 'done') { ui.fx.justDone = id; if (ST.ui.focus && ST.ui.focus.id === id) ST.ui.focus = null; }
    const val = sel => { const el = root.querySelector(sel); return el ? el.value : ''; };
    const now = nowEpoch();
    switch (act) {
      case 'tab': ui.tab = b.dataset.tab; ST.ui.tab = ui.tab; persist(); render(); break;
      case 'day': ui.dayIdx = +b.dataset.i; render(); break;
      case 'earlier': ui.earlierOpen = !b.parentElement.open; break;
      case 'anchor':
        if (b.dataset.k === 'meal') mealPreview(b.dataset);
        else say(anchorTip(b.dataset.k, b.dataset.t, +b.dataset.s, +b.dataset.e, true));
        break;
      case 'mealOpen': closeSheet(); openScheduledMeal(b.dataset); break;
      case 'dialApply': applyDial(now); break;
      case 'dialCancel': ui.pendingDial = null; closeSheet(); render(); break;
      case 'editRule': { const r = ST.availability.rules.find(x => x.id === id); if (r) ruleSheet(r); break; }
      case 'routine': told(Do.routine({ key: b.dataset.k, value: (+E.normalizePreferences(ST.prefs)[b.dataset.k] || 0) + (+v) })); break;
      case 'meals': told(Do.meals({})); break;
      case 'focusStart': {
        const i = findInst(id); if (!i || i.lifecycleState !== 'PLANNED') break;
        if (!doable(i, now)) { instSheet(i); break; }
        const f = ST.ui.focus;
        if (f && f.id !== id && findInst(f.id)) { say('Another timer is running. Finish or stop it first.'); focusSheet(); break; }
        if (!f || f.id !== id) ST.ui.focus = { id, start: Date.now(), paused: null, pausedMs: 0, plannedMs: Math.max(60, i.endEpoch - i.startEpoch) * 1000 };
        persist(); render(); focusSheet(); break;
      }
      case 'focusOpen': focusSheet(); break;
      case 'focusPause': { const f = ST.ui.focus; if (f && !f.paused) { f.paused = Date.now(); persist(); focusSheet(); } break; }
      case 'focusResume': { const f = ST.ui.focus; if (f && f.paused) { f.pausedMs = (f.pausedMs || 0) + Date.now() - f.paused; f.paused = null; persist(); focusSheet(); } break; }
      case 'focusFinish': {
        const f = ST.ui.focus, i = f && findInst(f.id);
        if (!f) break;
        const minutes = Math.max(1, Math.round(focusMs(f) / 60000));
        ST.ui.focus = null;
        if (!i || i.lifecycleState !== 'PLANNED') { persist(); closeSheet(); render(); say('That session is no longer on the plan. Log it from the Log page.'); break; }
        ui.fx.justDone = i.instanceId;
        finishInstance(i, minutes, { startEpoch: Math.floor(f.start / 1000) }); closeSheet(); render(); break;
      }
      case 'focusCancel': ST.ui.focus = null; persist(); closeSheet(); render(); say('Timer stopped. Nothing was logged.'); break;
      case 'gotoDay': ui.dayIdx = +b.dataset.i; ui.tab = 'today'; render(); break;
      case 'dismissBanner': ui.banner = null; render(); break;
      case 'dismissHost': ST.ui.hostNoticeDismissed = true; persist(); render(); break;
      case 'inst': { const i = findInst(id); if (i) instSheet(i); break; }
      case 'doneQuick': { const i = findInst(id); if (i && !doable(i, now)) { instSheet(i); break; } if (i) { finishInstance(i, i.durationSlots * 15); render(); } break; }
      case 'done': { const i = findInst(id); if (i && !doable(i, now)) { instSheet(i); break; } if (i) { finishInstance(i, Math.max(1, +val('#ldDoneMin') || i.durationSlots * 15)); closeSheet(); render(); } break; }
      case 'logToday': { const i = findInst(id); if (i && logToday(i, Math.max(1, Math.min(480, +val('#ldDoneMin') || i.durationSlots * 15)))) { closeSheet(); render(); } break; }
      case 'skip': { const i = findInst(id); if (!i) break; const c = E.applyMissConsequence(ST.ledger, i, ST.prefs, now, 'SKIP'); ST.ledger = c.ledger; const r = reschedule({ type: 'SKIPPED', instanceId: id }); persist(); closeSheet(); render(); say('Skipped' + (c.consequence && c.consequence.type === 'REST_TOKEN_SPENT' ? ' · used a rest token' : '') + (r ? ' · ' + summarize(r) : '')); break; }
      case 'lock': { const i = findInst(id); if (i) { i.isUserLocked = !i.isUserLocked; if (i.explanation) i.explanation = Object.assign({}, i.explanation, { summary: i.isUserLocked ? 'You pinned this time, so Life Director never moves it automatically.' : i.explanation.summary }); persist(); closeSheet(); render(); say(i.isUserLocked ? 'Pinned' : 'Unpinned'); } break; }
      case 'movePick': { const i = findInst(id); if (i) moveSheet(i); break; }
      case 'moveTo': { const r = reschedule({ type: 'MOVE', instanceId: id, newStartEpoch: +v }); if (r) { persist(); closeSheet(); render(); say('Moved and pinned · ' + summarize(r)); } break; }
      case 'activityOpen': { const inst = findInst(id); if (inst && Host.openActivity) { closeSheet(); if (!Host.openActivity(inst)) say('No linked workout or quest is available.'); } break; }
      case 'openPlan': closeSheet(); Host.openPlanDay(v); break;
      case 'openQuests': Host.openView('quests'); break;
      case 'disrupt': disruptSheet(); break;
      /* Something came up: the page's form and buttons, through the same functions a native screen's commands run */
      case 'dWork': disruptFrom({ kind: 'work', date: val('#dDate'), from: parseHm(val('#dStart')), until: parseHm(val('#dEnd')), commute: +val('#dCommute') || 0 }); break;
      case 'dBlock': disruptFrom({ kind: 'block', hours: +v }); break;
      case 'dDay': disruptFrom({ kind: 'day' }); break;
      case 'dSick': disruptFrom({ kind: 'sick', days: +v }); break;
      case 'dEnergy': closeSheet(); Today.energy({ level: v }); break;
      case 'replan': Today.replan(); break;
      case 'regroup': Today.regroup({ v, op: uuid() }); break;
      /* My time: the page's own forms, read as their fields' values by key, through the same functions a native screen's commands run */
      case 'saveSleep': {
        const we = root.querySelector('#ldWeekendOn').checked;
        told(Do.sleepSave({ bed: parseHm(val('#ldBed')), wake: parseHm(val('#ldWake')), weekend: we ? { bed: parseHm(val('#ldBedWe')), wake: parseHm(val('#ldWakeWe')) } : null }));
        break;
      }
      case 'addRule': ruleSheet(); break;
      case 'saveRule': {
        const form = formValues(ruleForm(null).fields, RULE_INPUT);
        if (told(id ? Do.ruleSave(Object.assign(form, { id })) : addFrom(Do.ruleSave, form))) closeSheet();
        break;
      }
      case 'delRule': if (told(Do.ruleRemove({ id }))) closeSheet(); break;
      case 'finishSetup': {
        if (told(Do.finishSetup())) break;
        const c = !ST.availability.rules.some(r => r.kind === 'SLEEP') && root.querySelector('.ld-sleepcard'); // show where sleep is set
        if (c) { c.scrollIntoView({ block: 'center' }); play(c, 'ld-ping'); }
        break;
      }
      case 'addGoal': goalSheet(); break;
      case 'pickArch': goalForm(b.dataset.arch); break;
      case 'saveGoal': {
        const F = goalFormModel(b.dataset.arch), form = formValues(F.fields.concat(F.fold ? F.fold.fields : []), GOAL_INPUT);
        if (told(addFrom(Do.goalAdd, Object.assign(form, { arch: b.dataset.arch })))) closeSheet();
        break;
      }
      case 'clarify': Do.clarify({ id, arch: b.dataset.arch }); break;
      case 'goalPause': Do.goalPause({ id }); break;
      case 'goalDel': if (confirm(GOAL_REMOVE_CONFIRM)) Do.goalRemove({ id }); break;
      case 'pref': Do.pref({ key: b.dataset.k, value: v }); break;
      case 'restDay': Do.restDay({ day: v }); break;
      case 'labToggle': Do.labToggle({ on: b.checked }); break;
      case 'export': { const txt = Do.exportBackup().text; (navigator.clipboard ? navigator.clipboard.writeText(txt) : Promise.reject()).then(() => say('Backup copied to clipboard'), () => { openSheet('<h3>Backup</h3><textarea class="ld-ta" readonly>' + esc(txt) + '</textarea>'); }); break; }
      case 'import': openSheet('<h3>' + SETTINGS_TEXT.data.restoreTitle + '</h3><p class="ld-muted">' + SETTINGS_TEXT.data.restoreText + '</p><textarea class="ld-ta" id="ldImport"></textarea><button class="ld-btn ld-primary" data-act="doImport">' + SETTINGS_TEXT.data.restoreButton + '</button>'); break;
      case 'doImport': { const r = Do.importBackup({ text: val('#ldImport') }); if (r.ok) closeSheet(); else say(r.message); break; }
      case 'reset': if (confirm(SETTINGS_TEXT.data.resetConfirm)) Do.reset(); break;
      case 'closeSheet': case 'sheetBg': closeSheet(); break;
      case 'tour': tourHelp(); break;
      case 'tourNext': tourMove(1); break;
      case 'tourBack': tourMove(-1); break;
      case 'tourEnd': tourEnd(false); break;
      case 'tourReset': Do.tourReset(); break;
      default: if (act.startsWith('lab')) labAction(act, b);
    }
  }
  function onInput(e) {
    const t = e.target;
    if (t.id === 'ldWeekendOn') { const r = root.querySelector('#ldWeekendRow'); if (r) r.hidden = !t.checked; }
    if (t.id === 'ldBed' || t.id === 'ldWake') { const mini = root.querySelector('.ld-mini'), b0 = parseHm(root.querySelector('#ldBed').value), w0 = parseHm(root.querySelector('#ldWake').value); if (mini && mini._set && b0 != null && w0 != null) mini._set(b0, w0); }
    if (t.id === 'rRot') { root.querySelector('#rRotWrap').hidden = !t.checked; root.querySelector('#rDaysWrap').hidden = t.checked; }
    if (t.dataset && t.dataset.act === 'prefRange') { const lbl = root.querySelector('#v-' + t.dataset.k); if (lbl) lbl.textContent = mins(+t.value); }
  }
  function onChange(e) {
    const t = e.target;
    if (t.dataset && t.dataset.act === 'prefRange') Do.prefRange({ key: t.dataset.k, value: +t.value });
    if (t.dataset && t.dataset.act === 'goalPrio') Do.goalPriority({ id: t.dataset.id, value: +t.value });
    if (t.dataset && t.dataset.act === 'labScenario') { ui.lab.scenario = t.value; ui.lab.result = null; render(); }
  }

  /* ── Simulation Lab (in-memory only; never touches the player's data) ── */
  function labRun() {
    const L = ui.lab, sc = E.buildScenario(L.scenario, +L.seed || 1);
    sc.input.horizonDays = +L.days || 7;
    const t0 = performance.now(); const out = E.generateSchedule(sc.input); const ms = performance.now() - t0;
    L.result = { sc, out, ms }; L.extra = null; L.dayIdx = 0;
  }
  function labAction(act, b) {
    const L = ui.lab;
    if (act === 'labRun') { L.seed = +root.querySelector('#labSeed').value || 1; L.days = +root.querySelector('#labDays').value || 7; labRun(); }
    if (act === 'labDay') L.dayIdx = +b.dataset.i;
    if (act === 'labInst') { const r = L.result; const i = r && (r.out.scheduledInstances.find(x => x.instanceId === b.dataset.id) || (L.extra && L.extra.after && L.extra.after.scheduledInstances.find(x => x.instanceId === b.dataset.id))); if (i) instSheet(i, r.sc.input.timezone); return; }
    if (act === 'labDisrupt' && L.result) L.extra = labDisrupt(L.result);
    if (act === 'labDeterminism' && L.result) {
      const ref = JSON.stringify(L.result.out.scheduledInstances); let same = 0;
      for (let k = 1; k <= 10; k++) {
        const r = U.mulberry32(k), inp = JSON.parse(JSON.stringify(L.result.sc.input));
        const sh = a => { for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(r() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };
        sh(inp.goals); sh(inp.activeQuests); sh(inp.playerAvailability.rules);
        if (JSON.stringify(E.generateSchedule(inp).scheduledInstances) === ref) same++;
      }
      L.extra = { kind: 'det', same };
    }
    if (act === 'labAll') {
      L.all = Object.keys(E.SCENARIOS).map(n => { const sc = E.buildScenario(n, +L.seed || 1); const t0 = performance.now(); const o = E.generateSchedule(sc.input); return { n, label: sc.label, o, ms: performance.now() - t0 }; });
    }
    render();
  }
  function labDisrupt(res) {
    const { sc, out } = res, d = sc.disruption;
    if (d.type === 'MISS_SERIES') {
      let L = Object.assign(E.emptyLedger('lab'), { restTokens: 1 }), cur = out, log = [], inp = sc.input;
      const seen = new Set();
      for (let k = 0; k < d.count; k++) {
        const inst = cur.scheduledInstances.filter(i => i.tier >= 3 && i.startEpoch > inp.currentTimeEpoch && !seen.has(i.bwcId)).sort((a, b) => a.startEpoch - b.startEpoch)[0];
        if (!inst) break;
        seen.add(inst.bwcId);
        const c = E.applyMissConsequence(L, inst, sc.input.playerPreferences, inst.endEpoch + 3700, 'MISS'); L = c.ledger;
        inp = Object.assign({}, inp, { currentTimeEpoch: inst.endEpoch + 3700 });
        const r = E.rescheduleActivity({ engineInput: inp, currentOutput: cur, trigger: { type: 'MISSED', instanceId: inst.instanceId } });
        cur = r.updatedSchedule;
        log.push(inst.title + ' → ' + c.state + (c.consequence ? ' (' + c.consequence.type + ')' : c.note ? ' (' + c.note + ')' : '') + (c.regroupPrompt ? ' · Tactical Regroup prompt' : '') + ' · re-placed via radius ' + r.changeRecord.radiusUsed.map(x => x.radius).join(','));
      }
      return { kind: 'miss', label: d.label, log, after: cur, tokens: L.restTokens };
    }
    if (d.type === 'RULE_CHANGE') {
      const inp = JSON.parse(JSON.stringify(sc.input)); inp.playerAvailability.rules = inp.playerAvailability.rules.filter(r => r.id !== 'work').concat(d.rules); inp.existingSchedule = out.scheduledInstances;
      const t0 = performance.now(); const after = E.generateSchedule(inp); const ms = performance.now() - t0;
      const moved = out.scheduledInstances.filter(p => { const n = after.scheduledInstances.find(x => x.instanceId === p.instanceId); return !n || n.startEpoch !== p.startEpoch; }).length;
      return { kind: 'disrupt', label: d.label, after, ms, churn: { collateralMoved: moved, futureInstances: out.scheduledInstances.length, ratio: moved / Math.max(1, out.scheduledInstances.length) }, radius: [] };
    }
    let input = sc.input, trig = { type: d.type, commitment: d.commitment };
    if (d.type === 'QUEST_CHANGED') { input = JSON.parse(JSON.stringify(sc.input)); Object.assign(input.activeQuests.find(q => q.id === d.questPatch.id), d.questPatch); trig = { type: 'QUEST_CHANGED' }; }
    const t0 = performance.now(); const r = E.rescheduleActivity({ engineInput: input, currentOutput: out, trigger: trig }); const ms = performance.now() - t0;
    return { kind: 'disrupt', label: d.label, after: r.updatedSchedule, ms, churn: r.changeRecord.churn, radius: r.changeRecord.radiusUsed, actions: r.changeRecord.actions };
  }
  function renderLab() {
    const L = ui.lab;
    const opts = Object.entries(E.SCENARIOS).map(([k, s]) => '<option value="' + k + '"' + (k === L.scenario ? ' selected' : '') + '>' + esc(s.label) + '</option>').join('');
    let h = '<div class="ld-card ld-lab"><h4>Simulation Lab</h4><p class="ld-muted">Runs seeded test players entirely in memory. Your own plan and LifeMax save are never touched.</p>' +
      '<label>Scenario<select data-act="labScenario">' + opts + '</select></label><p class="ld-muted">' + esc(E.SCENARIOS[L.scenario].about) + '</p>' +
      '<div class="ld-grid2"><label>Seed<input type="number" id="labSeed" value="' + L.seed + '"></label><label>Days<select id="labDays">' + [7, 14].map(d => '<option' + (+L.days === d ? ' selected' : '') + '>' + d + '</option>').join('') + '</select></label></div>' +
      '<div class="ld-row"><button class="ld-btn ld-primary" data-act="labRun">Run</button><button class="ld-btn" data-act="labAll">Run all 10</button></div></div>';
    if (L.all) h += '<div class="ld-card"><h4>All scenarios · seed ' + L.seed + '</h4><table class="ld-table"><tr><th>Scenario</th><th>Status</th><th>ms</th><th>Placed</th><th>Unfit</th><th>Quality</th></tr>' +
      L.all.map(r => '<tr><td>' + esc(r.label) + '</td><td>' + (r.o.status === 'INVARIANT_VIOLATION' ? '✗' : r.o.status === 'VALID_SCHEDULE' ? '✓' : '✓*') + '</td><td>' + r.ms.toFixed(0) + '</td><td>' + r.o.scheduledInstances.length + '</td><td>' + r.o.unscheduledDiagnostics.filter(d => d.tier).length + '</td><td>' + r.o.validation.qualityMetrics.sqi + '</td></tr>').join('') + '</table><p class="ld-muted">✓* = valid, with notes such as days too full for the full downtime.</p></div>';
    const R = L.result;
    if (!R) return h;
    const o = R.out, z = R.sc.input.timezone, q = o.validation.qualityMetrics;
    const avail = o.capacity.reduce((n, c) => n + c.usableMin, 0), alloc = o.capacity.reduce((n, c) => n + c.taskMin, 0), rest = o.capacity.reduce((n, c) => n + c.recoveryMin, 0);
    const metric = (l, v) => '<div><span>' + l + '</span><b>' + v + '</b></div>';
    h += '<div class="ld-card"><h4>Result · ' + esc(R.sc.label) + '</h4><div class="ld-metrics">' + metric('Status', o.status.replace(/_/g, ' ').toLowerCase()) + metric('Compute', R.ms.toFixed(1) + ' ms') + metric('Quality', q.sqi) +
      metric('Usable time', mins(avail)) + metric('Allocated', mins(alloc) + ' (' + Math.round(alloc / Math.max(1, avail) * 100) + '%)') + metric('Downtime kept', mins(rest)) +
      metric('Placed', o.scheduledInstances.length) + metric('Could not fit', o.unscheduledDiagnostics.filter(d => d.tier).length) + metric('Recovery OK', Math.round(q.recoveryCompliance * 100) + '%') +
      metric('Hard violations', o.validation.violatedInvariants.length) + metric('Must-do coverage', Math.round(q.coverageRequired * 100) + '%') + metric('Fairness', q.fairness) + '</div>' +
      (o.validation.violatedInvariants.length ? '<ul class="ld-why">' + o.validation.violatedInvariants.map(v => '<li><b>' + v.code + '</b> ' + esc(v.message) + '</li>').join('') + '</ul>' : '') +
      (o.warnings.concat(o.validation.warnings).length ? '<details class="ld-details"><summary>' + o.warnings.concat(o.validation.warnings).length + ' notes</summary><ul class="ld-why">' + o.warnings.concat(o.validation.warnings).map(w => '<li>' + esc(w.message) + '</li>').join('') + '</ul></details>' : '') +
      '<div class="ld-row"><button class="ld-btn" data-act="labDisrupt">Disrupt: ' + esc(R.sc.disruption.label) + '</button><button class="ld-btn ld-ghost" data-act="labDeterminism">Determinism ×10</button></div></div>';
    if (L.extra) {
      const x = L.extra;
      if (x.kind === 'det') h += '<div class="ld-card"><h4>Determinism</h4><p>' + x.same + ' of 10 runs with shuffled input order produced an identical schedule.</p></div>';
      else if (x.kind === 'miss') h += '<div class="ld-card"><h4>' + esc(x.label) + '</h4><ol class="ld-why">' + x.log.map(l => '<li>' + esc(l) + '</li>').join('') + '</ol><p class="ld-muted">Rest tokens left: ' + x.tokens + '</p></div>';
      else h += '<div class="ld-card"><h4>' + esc(x.label) + '</h4><p>' + (x.after.status === 'INVARIANT_VIOLATION' ? '✗ invalid' : '✓ still valid') + ' · repaired in ' + x.ms.toFixed(1) + ' ms · moved ' + x.churn.collateralMoved + ' of ' + x.churn.futureInstances + ' other sessions (' + (x.churn.ratio * 100).toFixed(1) + '%)' + (x.churn.compressed ? ' · ' + x.churn.compressed + ' shortened' : '') + '</p>' +
        (x.radius.length ? '<p class="ld-muted">Containment radius used: ' + x.radius.map(r => esc(r.title) + ' → R' + r.radius).join(', ') + '</p>' : '') + '</div>';
    }
    const show = L.extra && L.extra.after ? L.extra.after : o;
    const days = horizonDays(show, z);
    L.dayIdx = Math.min(L.dayIdx, days.length - 1);
    h += stripHtml(days, L.dayIdx, 'labDay', show) +
      (L.extra && L.extra.after ? '<p class="ld-muted">Showing the schedule after the disruption.</p>' : '') + roHtml(dayBlocks(show, days[L.dayIdx], z), z, { lab: true });
    h += '<div class="ld-card"><h4>Quest coverage</h4><ul class="ld-qlist">' + (show.questCoverage.map(c => '<li class="ld-q ' + (c.covered ? 'ok' : c.status === 'PASSIVE' ? 'pas' : 'no') + '"><div><b>' + esc(c.title) + '</b><small>' + esc(c.status) + '</small></div><span>' + c.scheduledSessions + ' sched.</span></li>').join('') || '<li class="ld-muted">No quests in this scenario.</li>') + '</ul>' + unscheduledHtml(show, true) + '</div>';
    return h;
  }

  /* ── the glance: today's dial on the LifeMax Overview ───────────────────
     LifeMax calls LifeDirector.glance(el) when it draws the Overview. The same live
     dial as Today (read-only: no drag handles), refreshed every 30 s while the Overview
     is on screen. Any tap opens Life Director's Today and does the same thing there
     (a session opens its details, Start starts the timer). Works before Life Director
     has ever been opened: it loads the saved plan on its own. */
  let glanceEl = null, glanceTimer = null, clockTimer = null;
  /* the running timer's clocks tick each second, Life Director's and the Overview glance's alike, whichever drew first */
  const tickClocks = () => { if (!clockTimer) clockTimer = setInterval(focusTick, 1000); };
  /* The glance's decisions, which the page's glance (glanceHtml) and a native Overview (native.glance) both draw from: setup
     before set-up is done, none when the plan has no today, else day with today's dial and its foot (sessions left, and done). */
  const GLANCE = { title: 'Today’s plan', link: 'Life Director ›', intro: 'Tell Life Director when you’re busy and it fits your goals into the rest of the day.', button: 'Plan my days', none: 'No plan for today yet.' };
  function glanceData() {
    const head = { title: GLANCE.title, link: GLANCE.link };
    if (!ST.setupComplete || !ST.schedule) return Object.assign({ state: 'setup', intro: GLANCE.intro, button: GLANCE.button }, head);
    const days = horizonDays(ST.schedule, tz()), day = days[0], now = nowEpoch();
    if (!day) return Object.assign({ state: 'none', intro: GLANCE.none, button: null }, head);
    const blocks = dayBlocks(ST.schedule, day, tz()), tasks = blocks.filter(b => b.inst);
    const left = tasks.filter(b => b.inst.lifecycleState === 'PLANNED' && b.endEpoch > now), done = tasks.filter(b => sesState(b.inst.lifecycleState) === 'done').length;
    const m = left.reduce((n, b) => n + Math.round((b.endEpoch - Math.max(now, b.startEpoch)) / 60), 0);
    const text = tasks.length ? (left.length ? left.length + ' session' + (left.length === 1 ? '' : 's') + ' left · ' + mins(Math.round(m / 15) * 15 || m) : 'Every session done') : 'No sessions today';
    return Object.assign({ state: 'day', intro: null, button: null, foot: { text, done: tasks.length && done ? done + ' done' : '' }, day, blocks, now }, head);
  }
  function glanceHtml() {
    const g = glanceData(), head = '<div class="ov-h ld-gl-h"><h3>' + g.title + '</h3><button type="button" class="ov-link" data-act="glanceOpen">' + g.link + '</button></div>';
    if (g.state === 'setup') return head + '<div class="ld-gl-empty"><p>' + g.intro + '</p><button type="button" class="ld-btn ld-primary" data-act="glanceOpen">' + g.button + '</button></div>';
    if (g.state === 'none') return head + '<p class="ld-muted">' + g.intro + '</p>';
    return head + dialHtml(g.day, g.blocks, true, g.now) + '<div class="ld-gl-foot">' + g.foot.text + (g.foot.done ? '<span>' + g.foot.done + '</span>' : '') + '</div>';
  }
  /* The glance for a native Overview: the same decisions, with today's dial and centre as native Today draws them (nativeToday),
     read-only (no handles, no build), and the centre as the glance shows it: the session's title cleaned (the full one kept for
     TalkBack) and no "Nothing else planned today". A pure read: the page's own glance keeps syncing and saving (glance()). */
  function nativeGlance() {
    if (!ST) boot();
    const g = glanceData(), out = { state: g.state, title: g.title, link: g.link, intro: g.intro, button: g.button, dial: null, core: null, foot: null };
    if (g.state !== 'day') return out;
    const t = nativeToday(0), d = t.dial, core = Object.assign({}, t.core);
    const cleaned = String(core.title || '').replace(/^Plan:\s*/i, '').replace(/\s*\([^)]*\)\s*$/, '').trim();
    core.fullTitle = core.title;
    if (cleaned) core.title = cleaned;
    if (core.next && !core.next.act && /nothing else planned today/i.test(core.next.label)) core.next = null;
    // the glance's dial has no handles, so fixed time's tip leaves out the hint about dragging one (anchorTip without handles)
    const arcs = d.arcs.map(a => a.style === 'band' ? Object.assign({}, a, { tip: anchorTip(a.kind, a.title, a.s, a.e, false) }) : a);
    out.dial = { startEpoch: d.startEpoch, endEpoch: d.endEpoch, radii: d.radii, hourWords: d.hourWords, arcs, words: d.words, hand: d.hand, ring: d.ring };
    out.core = core;
    out.foot = g.foot;
    return out;
  }
  function glance(el, opts) {
    if (!el) return false;
    try {
      if (!ST) boot();
      try { let ch = syncHost(); ch = processMisses() || ch; ch = maybeRollover() || ch; if (ch) persist(); } catch (e) { console.warn('[Life Director] glance sync', e); }
      if (glanceEl !== el) {
        glanceEl = el;
        el.classList.add('ld-root', 'ld-glance');
        el.addEventListener('click', e => {
          const t = e.target.closest('[data-act]');
          openFromGlance(t && t.dataset.act !== 'glanceOpen' ? t : null);
        });
        el.addEventListener('keydown', e => { if ((e.key === 'Enter' || e.key === ' ') && e.target.matches('[role=button]')) { e.preventDefault(); e.target.click(); } });
      }
      el.innerHTML = glanceHtml();
      // Overview-only display changes; preserve the full session title in data.
      const glanceCore = el.querySelector('.ld-core-in');
      if (glanceCore) {
        const name = glanceCore.querySelector('.ld-core-t');
        if (name) {
          const cleaned = name.textContent.replace(/^Plan:\s*/i, '').replace(/\s*\([^)]*\)\s*$/, '').trim();
          if (cleaned) { name.title = name.textContent; name.textContent = cleaned; }
        }
        // a separate open-timer button gives way to the clock, which becomes the tap; a clock that already is the tap stays
        const openTimer = glanceCore.querySelector('[data-act="focusOpen"]:not([data-fclock])');
        if (openTimer) {
          openTimer.remove();
          const clock = glanceCore.querySelector('[data-fclock]');
          if (clock) {
            clock.setAttribute('role', 'button');
            clock.setAttribute('tabindex', '0');
            clock.setAttribute('aria-label', 'Open running timer');
            clock.dataset.act = 'focusOpen';
          }
        }
        const redundant = glanceCore.querySelector('.ld-core-nx:not([data-act])');
        if (redundant && /nothing else planned today/i.test(redundant.textContent)) redundant.remove();
      }
      el.hidden = false;
      const dial = el.querySelector('.ld-dial');
      if (dial) { dial.querySelectorAll('.ld-hd').forEach(h => h.remove()); if (opts && opts.intro && !reduced()) play(dial, 'ld-dial-in'); }
      tickClocks();
      if (!glanceTimer) glanceTimer = setInterval(() => {
        const v = glanceEl && glanceEl.closest('.view');
        if (!glanceEl || !glanceEl.isConnected || document.hidden || (v && !v.classList.contains('active'))) return;
        glance(glanceEl);
      }, 30 * 1000);
      return true;
    } catch (err) { console.warn('[Life Director] glance', err); el.hidden = true; return false; }
  }
  /* A glance tap that a native screen drawing Today's sheets (phase 4A) answers with its own sheet: the intent {id, sheet, target}
     it takes once by id. The session's sheet; the running timer's, Start starting it first as the page's Start does (or the
     session's sheet when it cannot start yet, as the page opens it); a meal's. Any other tap (a band's tip) is replayed on the
     page. An intent lasts until the next arrival or INTENT_MS, so an open that never came does not pop a sheet up later. */
  let nativeIntends = () => false, intent = null, intentSeq = 0;
  const INTENT_MS = 10000;
  function glanceIntent(d) {
    if (!ST || !ST.setupComplete || !ST.schedule) return null;
    if (d.act === 'inst' && findInst(d.id)) return { sheet: 'session', target: d.id };
    if (d.act === 'focusOpen' && ST.ui.focus) return { sheet: 'timer', target: ST.ui.focus.id };
    if (d.act === 'anchor' && d.k === 'meal' && mealSegment(d.s)) return { sheet: 'meal', target: d.s };
    if (d.act !== 'focusStart' || !plannedInst(d.id)) return null;
    const f = ST.ui.focus;
    if (f && f.id !== d.id && findInst(f.id)) { say('Another timer is running. Finish or stop it first.'); return { sheet: 'timer', target: f.id }; }
    return Today.timer({ act: 'start', id: d.id }).ok ? { sheet: 'timer', target: d.id } : { sheet: 'session', target: d.id };
  }
  const nativeIntent = () => (intent && Date.now() - intent.at < INTENT_MS ? { id: intent.id, sheet: intent.sheet, target: intent.target } : null);
  /* jump from the Overview into Life Director, then repeat the tap there */
  function openFromGlance(t) {
    if (typeof navigate === 'function') navigate('life-director');
    if (ST) { ui.tab = ST.setupComplete ? 'today' : 'setup'; ui.dayIdx = 0; ST.ui.tab = ui.tab; }
    const v = document.getElementById('view-life-director');
    if (window.LifeMaxFeatures && LifeMaxFeatures.list && LifeMaxFeatures.list().includes(FEATURE_ID)) LifeMaxFeatures.open(FEATURE_ID); else mount(v);
    if (!t || !root) return;
    const it = nativeIntends() ? glanceIntent(t.dataset) : null;
    if (it) { intent = Object.assign({ id: ++intentSeq, at: Date.now() }, it); changed(); return; } // the screen opens its own sheet
    const d = t.dataset, sel = '[data-act="' + d.act + '"]' + (d.id ? '[data-id="' + CSS.escape(d.id) + '"]' : '') + (d.s ? '[data-s="' + d.s + '"]' : '');
    const twin = root.querySelector('.ld-dial ' + sel) || root.querySelector(sel);
    if (twin) setTimeout(() => twin.dispatchEvent(new MouseEvent('click', { bubbles: true })), 30);
  }
  /* A tap on a native Overview's glance (act and id as native.glance() names them; '' or glanceOpen only opens): Life Director's
     Today, mounted, then the tap run there at once (nativeAct), or, when a native screen draws Today's sheets, the intent it opens
     its own sheet from, as a tap on the page's glance does. Answers false for a tap Today cannot run (an item gone since). */
  function nativeFromGlance(act, id) {
    openFromGlance(null);
    act = String(act || ''); id = String(id == null ? '' : id);
    if (!act || act === 'glanceOpen') return true;
    if (!root) return false;
    const d = act === 'meal' ? { act: 'anchor', k: 'meal', s: id } : { act, id };
    const it = nativeIntends() ? glanceIntent(d) : null;
    if (it) { intent = Object.assign({ id: ++intentSeq, at: Date.now() }, it); changed(); return true; }
    return nativeAct(act, id);
  }
  /* ── lifecycle ── */
  function tick() {
    if (!root || !root.isConnected) return;
    const view = root.closest('.view');
    if (view && !view.classList.contains('active')) return;
    let changed = false;
    try { changed = syncHost() || changed; changed = processMisses() || changed; changed = maybeRollover() || changed; } catch (err) { console.error('[Life Director]', err); ui.banner = { kind: 'bad', text: 'Life Director hit an error: ' + err.message }; changed = true; }
    if (changed) { persist(); render(); }
  }
  function mount(el) {
    if (!el) return;
    if (!root || !el.contains(root)) {
      root = el.querySelector('.ld-root');
      if (!root) { root = document.createElement('div'); root.className = 'ld-root'; el.appendChild(root); }
      if (!root.querySelector('.ld-main')) root.innerHTML = '<header class="ld-head"></header><nav class="ld-tabs"></nav><main class="ld-main"></main><div class="ld-sheet" hidden><div class="ld-sheet-bg" data-act="sheetBg"></div><div class="ld-sheet-panel" role="dialog" aria-modal="true"><button class="ld-x" data-act="closeSheet" aria-label="Close">×</button><div class="ld-sheet-body"></div></div></div><div class="ld-toast" role="status"></div>';
      root.addEventListener('click', onClick);
      root.addEventListener('input', onInput);
      root.addEventListener('change', onChange);
      root.addEventListener('keydown', e => { if (ui.tour) { if (e.key === 'Escape') tourEnd(false); else if (e.key === 'ArrowRight') { e.preventDefault(); tourMove(1); } else if (e.key === 'ArrowLeft') { e.preventDefault(); tourMove(-1); } else if (e.key === 'Tab') { const f = [...root.querySelectorAll('.ld-tour button:not([hidden])')]; const i = f.indexOf(document.activeElement); e.preventDefault(); (f[(i + (e.shiftKey ? -1 : 1) + f.length) % f.length] || f[0]).focus(); } return; } if (e.key === 'Escape') closeSheet(); if ((e.key === 'ArrowRight' || e.key === 'ArrowLeft') && e.target.matches('.ld-tab')) { const list = [...root.querySelectorAll('.ld-tab')], i = list.indexOf(e.target), n = list[i + (e.key === 'ArrowRight' ? 1 : -1)]; if (n) { e.preventDefault(); goTab(n.dataset.tab); n.focus(); } } if ((e.key === 'Enter' || e.key === ' ') && e.target.matches('[role=button]')) { e.preventDefault(); e.target.click(); } });
      document.addEventListener('visibilitychange', () => { if (!document.hidden) tick(); });
      window.addEventListener('resize', () => { if (ui.tour) tourPlace(); });
      document.addEventListener('scroll', () => { if (ui.tour) tourPlace(); }, { capture: true, passive: true });
      if (!ST) boot();
      else { ui.tab = ST.setupComplete ? (ST.ui && ST.ui.tab || 'today') : 'setup'; }
      if (!timer) { timer = setInterval(tick, 5 * 60 * 1000); setInterval(liveTick, 15 * 1000); tickClocks(); }
      /* swipe the stage left or right to swing to the neighbouring section */
      const main = root.querySelector('.ld-main');
      let sw = null;
      main.addEventListener('touchstart', e => {
        const t = e.touches[0], el = e.target;
        sw = (e.touches.length === 1 && !el.closest('.ld-strip, .ld-dial, .ld-mini, input, select, textarea, .ld-seg, .ld-dchips, .ld-table, .ld-times, .ld-stepper')) ? { x: t.clientX, y: t.clientY, at: Date.now() } : null;
      }, { passive: true });
      main.addEventListener('touchend', e => {
        if (!sw) return; const t = e.changedTouches[0], dx = t.clientX - sw.x, dy = t.clientY - sw.y, dt = Date.now() - sw.at; sw = null;
        if (dt > 700 || Math.abs(dx) < 70 || Math.abs(dy) > Math.abs(dx) * 0.6) return;
        const list = tabList().map(x => x[0]), i = list.indexOf(ui.tab), j = i + (dx < 0 ? 1 : -1);
        if (i >= 0 && j >= 0 && j < list.length && ST.setupComplete) goTab(list[j]);
      }, { passive: true });
      if (ST.setupComplete && ST.schedule) ui.animate = 'intro';
    }
    tick(); render();
  }
  /* Read-only helpers for LifeMax's Money tab: recurring WORK rules, and the work shifts that ENDED in (fromEpoch, toEpoch].
     One-off commitments of kind WORK count too; shifts mostly covered by a one-off blackout (called off, sick) do not. */
  function workRules() {
    if (!ST) boot();
    return (ST.availability.rules || []).filter(r => r.kind === 'WORK').map(r => ({ id: r.id, title: r.title || 'Work', days: (r.days || []).slice(), startMin: r.startMin, durationMin: r.durationMin }));
  }
  function shifts(fromEpoch, toEpoch) {
    if (!ST) boot();
    const U = LifeDirectorEngine.util, z = tz(), out = [];
    const p0 = U.localParts(fromEpoch, z), p1 = U.localParts(toEpoch, z);
    let ymd = U.ymdAdd({ y: p0.y, mo: p0.mo, d: p0.d }, -1);
    const lastKey = U.ymdKey(U.ymdAdd({ y: p1.y, mo: p1.mo, d: p1.d }, 0));
    for (let guard = 0; guard < 400 && U.ymdKey(ymd) <= lastKey; guard++, ymd = U.ymdAdd(ymd, 1)) {
      for (const r of ST.availability.rules || []) {
        if (r.kind !== 'WORK' || !ruleOn(r, ymd)) continue;
        const s = U.zonedToEpoch(ymd.y, ymd.mo, ymd.d, r.startMin, z), e = U.zonedToEpoch(ymd.y, ymd.mo, ymd.d, r.startMin + r.durationMin, z);
        if (e <= fromEpoch || e > toEpoch) continue;
        out.push({ key: r.id + '|' + U.ymdKey(ymd), ruleId: r.id, title: r.title || 'Work', startEpoch: s, endEpoch: e, durationMin: r.durationMin });
      }
    }
    for (const c of ST.fixedCommitments || []) {
      const k = c.kind || (c.commitmentType === 'CLASS' ? 'CLASS' : c.commitmentType === 'EMERGENCY' ? 'EMERGENCY' : 'WORK');
      if (k !== 'WORK' || !c.startEpoch || !c.endEpoch || c.endEpoch <= fromEpoch || c.endEpoch > toEpoch) continue;
      out.push({ key: 'one|' + String(c.id || c.commitmentId || c.startEpoch), ruleId: null, title: c.title || 'Extra shift', startEpoch: c.startEpoch, endEpoch: c.endEpoch, durationMin: Math.round((c.endEpoch - c.startEpoch) / 60) });
    }
    const off = (ST.availability.blackouts || []).filter(b => b && b.startEpoch && b.endEpoch);
    return out.filter(s => {
      const dur = s.endEpoch - s.startEpoch;
      const hit = off.reduce((a, b) => a + Math.max(0, Math.min(b.endEpoch, s.endEpoch) - Math.max(b.startEpoch, s.startEpoch)), 0);
      return hit < dur * 0.5;
    }).sort((a, b) => a.endEpoch - b.endEpoch);
  }
  /* ── the home-screen widget ─────────────────────────────────────────────
     The Android widget shows this same dial as a picture. The plain dial (no hand, no drag
     handles, no shaded past, no centre) is drawn off screen, its computed styles are copied
     into the SVG so it stands alone, and it is rasterised to a PNG. The widget adds the live
     hand, the shaded past and the centre text itself, so it stays right between app opens. */
  const SNAP_PROPS = ['fill', 'fill-opacity', 'stroke', 'stroke-width', 'stroke-opacity', 'stroke-linecap', 'stroke-linejoin', 'stroke-dasharray', 'stroke-dashoffset', 'opacity', 'display', 'visibility', 'font-family', 'font-size', 'font-weight', 'letter-spacing', 'text-anchor', 'dominant-baseline', 'paint-order'];
  async function widgetSnapshot(size) {
    if (!ST || !ST.setupComplete || !ST.schedule) return null;
    const s = ST.schedule, day = horizonDays(s, tz())[0];
    if (!day) return null;
    const now = nowEpoch(), blocks = dayBlocks(s, day, tz());
    if (!document.getElementById('ldSnapCss')) { const st = document.createElement('style'); st.id = 'ldSnapCss'; st.textContent = '.ld-snap *{animation:none!important;transition:none!important}.ld-snap .ld-dial{width:360px;margin:0}'; document.head.appendChild(st); }
    const host = document.createElement('div');
    host.className = 'ld-root ld-snap';
    host.style.cssText = 'position:fixed;left:-9999px;top:0;width:360px;height:360px;pointer-events:none;background:none';
    host.innerHTML = dialHtml(day, blocks, true, now, true);
    document.body.appendChild(host);
    try {
      const svg = host.querySelector('svg');
      svg.setAttribute('xmlns', 'http://www.w3.org/2000/svg'); svg.setAttribute('width', '380'); svg.setAttribute('height', '380'); svg.setAttribute('viewBox', '-10 -10 380 380');
      svg.querySelectorAll('title,.ld-hit').forEach(n => n.remove());
      for (const el of [svg, ...svg.querySelectorAll('*')]) {
        const cs = getComputedStyle(el);
        for (const p of SNAP_PROPS) { let v = cs.getPropertyValue(p); if (v) { v = v.replace(/url\("?[^"#)]*(#[^")]+)"?\)/g, 'url($1)'); el.style.setProperty(p, v); } }
      }
      const xml = new XMLSerializer().serializeToString(svg);
      const img = new Image();
      await new Promise((ok, no) => { img.onload = ok; img.onerror = () => no(new Error('The dial image could not be drawn')); img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(xml); });
      const px = size || 720, cv = document.createElement('canvas');
      cv.width = cv.height = px;
      cv.getContext('2d').drawImage(img, 0, 0, px, px);
      const sessions = (s.scheduledInstances || []).filter(i => i.lifecycleState === 'PLANNED' && i.endEpoch > now - 3600 && i.startEpoch < now + 36 * 3600)
        .sort((a, b) => a.startEpoch - b.startEpoch).slice(0, 8).map(i => ({ s: i.startEpoch, e: i.endEpoch, t: i.title }));
      return { png: cv.toDataURL('image/png').split(',')[1], json: JSON.stringify({ d0: day.startEpoch, d1: day.endEpoch, at: now, sessions }) };
    } finally { host.remove(); }
  }
  /* ── Native Life Director (phase 1: Today; phase 2: the dial's handles and its build) ──────────
     A native screen draws the frame (header, section tabs, banner, last message) and Today from here, and covers the
     page only while showing() is 'today'. The same decisions and words as headerHtml, render, dialHtml, coreToday,
     mixHtml, stripHtml, renderToday and roHtml, returned as data; nothing is scheduled or saved here. Angles are degrees
     clockwise from midnight at the top, after the same gaps the web dial uses; radii are those of DIAL in a 360-unit
     square. A tap the page must answer carries its web element's act and the one id that act needs (see nativeAct);
     a tap on fixed time only shows its tip. Epochs are seconds; the timer's are milliseconds, as ST.ui.focus keeps them.
     The handles carry every place a drag can land and what the dial shows there (nativeHandles, from the rules attachDial
     drags by); a release is answered by the page (nativeDrop, nativeApply); the build is the plan runBuild plays (nativeBuild). */
  const NOTICE_MS = 4000; // how long the last message stays in the projection
  function nativeFrame() {
    if (!ST) boot();
    const now = nowEpoch(), n = ui.notice;
    return {
      header: planStatus(now),
      clock: { time: fTime(now), day: fDay(now), h12: H12, locale: LOCALE }, // h12 and locale: how a screen writes a time the player picked
      tab: ui.tab,
      tabs: tabList().map(([id, label]) => ({ id, label, icon: TAB_ICON[id] })),
      banner: ui.banner ? { kind: ui.banner.kind, text: ui.banner.text, actions: bannerActions(ui.banner), dismiss: !ui.banner.regroup } : null,
      notice: n && Date.now() - n.at < NOTICE_MS ? { id: n.id, text: n.text } : null,
      reducedMotion: reduced(),
    };
  }
  function nativeToday(dayIdx, opts) {
    if (!ST) boot();
    if (!ST.setupComplete || !ST.schedule) return null;
    const s = ST.schedule, z = tz(), days = horizonDays(s, z), now = nowEpoch();
    const idx = Math.max(0, Math.min(days.length - 1, Math.round(Number(dayIdx) || 0)));
    const day = days[idx], blocks = dayBlocks(s, day, z), isToday = idx === 0, d0 = day.startEpoch, d1 = day.endEpoch;
    const nextId = isToday ? (nextUp(now) || {}).instanceId : null;
    const voiceIds = new Set((ST.fixedCommitments || []).filter(c => c.voice).map(c => String(c.id || c.commitmentId)));
    const handles = dialHandles(day);
    const arcs = [], words = [];
    const word = (r, a0, a1, options, kind, inner) => { const w = arcWords(r, a0, a1, options); if (w) words.push(Object.assign({ r, a0, a1, kind, inner }, w)); };
    for (const b of blocks) {
      const k = blockClass(b), m = Math.round((b.endEpoch - b.startEpoch) / 60);
      let a0 = angOf(b.startEpoch, d0, d1), a1 = angOf(b.endEpoch, d0, d1);
      if (a1 - a0 < 0.3) continue;
      const arc = (ring, style, kind, more) => arcs.push(Object.assign({ ring, style, kind, a0, a1, s: b.startEpoch, e: b.endEpoch, title: b.title || '',
        label: (b.title || '') + ', ' + fTime(b.startEpoch) + ' to ' + fTime(b.endEpoch), state: '', next: false, icon: '', act: '', id: '', tip: '' }, more));
      if (!b.inst && b.refId && voiceIds.has(String(b.refId)) && b.kind === 'APPOINTMENT') {
        [a0, a1] = gap(a0, a1, 1.4);
        arc('inner', 'appt', 'work', { tip: anchorTip('work', b.title, b.startEpoch, b.endEpoch, false) });
      } else if (OUTER[k]) {
        [a0, a1] = gap(a0, a1, 0.9);
        arc('outer', 'band', k, { tip: anchorTip(k, b.title, b.startEpoch, b.endEpoch, true) }); // what the page's band says when tapped
        word(DIAL.RO, a0, a1, blockWords(b, d0, d1), k, false);
      } else if (k === 'meal') {
        [a0, a1] = gap(a0, a1, 1.4);
        arc('inner', 'meal', 'meal', { icon: 'meal', act: 'meal', id: String(b.mealStartEpoch || b.startEpoch) });
      } else if (k === 'rest') {
        arc('inner', 'thin', 'rest');
        if (m >= 60) word(DIAL.RI + 9, a0, a1, ['Downtime · ' + mins(m), 'Downtime', 'Rest'], 'rest', true);
      } else if (k === 'free' && m >= 90 && !(isToday && b.endEpoch <= now)) {
        word(DIAL.RI, a0, a1, ['Free · ' + mins(m), 'Free'], 'free', true);
      } else if (k === 'task') {
        [a0, a1] = gap(a0, a1, 1.4);
        const next = b.inst.instanceId === nextId;
        arc('inner', 'session', 'task', { state: sesState(b.inst.lifecycleState), next, icon: tagIcon(b.inst.tag), act: 'inst', id: b.inst.instanceId,
          label: (next ? 'Next: ' : '') + b.inst.title + ', ' + fTime(b.startEpoch) + ' to ' + fTime(b.endEpoch) });
      }
    }
    const live = now >= d0 && now < d1, st = isToday ? nowState(now) : null;
    const ring = live && st.cur ? { r: 84, frac: Math.min(1, Math.max(0, (now - st.cur.startEpoch) / Math.max(60, st.cur.endEpoch - st.cur.startEpoch))), kind: st.cur.inst ? 'task' : blockClass(st.cur), warn: !!st.kick } : null;
    return {
      now,
      dayIndex: idx,
      isToday,
      strip: days.map((d, k) => ({ label: k === 0 ? 'Today' : DOW[d.dow], date: d.ymd.d, dots: dayBlocks(s, d, z).filter(b => b.inst).slice(0, 5).map(b => sesState(b.inst.lifecycleState)) })),
      dial: Object.assign({ startEpoch: d0, endEpoch: d1, radii: DIAL, hourWords: nativeHourWords(), arcs, words, hand: live ? angOf(now, d0, d1) : null, ring },
        nativeHandles(handles, d0, d1), { build: nativeBuild(d0, d1, arcs, isToday) }),
      hint: DIAL_HINT,
      core: nativeCore(day, blocks, isToday, now, st),
      mix: nativeMix(blocks),
      upNext: isToday ? nativeUpNext(day, days, now) : null,
      reminders: isToday ? (s.passiveReminders || []).map(reminderText) : [],
      sessions: blocks.filter(b => b.inst).length,
      rows: nativeRows(blocks, isToday, now, !!(opts && opts.removal)),
    };
  }
  /* The dial's handles for a native screen, with everything a drag shows, decided here: stops (every place a handle can land on
     this day: its minute m, its angle a, the angle from which the finger picks it, and its time), and for each handle its key (what
     a drop names), where it is (a, minute), the readout's label, its TalkBack label, and for each stop the length the readout shows
     ('' when the span would be too short: then dragWords.tooShort and no arc) and where the lit arc ends (ends: the angle of the
     span's other end, kept on the day; the arc runs from the stop to it for a start handle, from it to the stop for an end handle). */
  function nativeHandles(handles, d0, d1) {
    if (!handles.length) return { handles: [], stops: [], dragWords: DRAG_WORDS };
    const stops = dialStops(d0, d1);
    return {
      handles: handles.map(h => {
        const { rule: r, end, ep } = h, label = handleLabel(r, end), om = minuteOf(ep), held = stops.map(st => dragPreview(r, end, om, d0, st));
        return { key: h.key, rule: String(r.id), end, kind: h.kind, a: angOf(ep, d0, d1), minute: om, label, aria: label + ' ' + fTime(ep),
          lengths: held.map(v => v.bad ? '' : v.text), ends: held.map(v => angOf(end === 'start' ? v.ne : v.ns, d0, d1)) };
      }),
      stops: stops.map(st => ({ m: st.m, a: st.a, from: st.from, time: fHm(st.m) })),
      dragWords: DRAG_WORDS,
    };
  }
  /* The latest build or repair for a native screen to play once (by id) on this day's dial, while the page would still play it
     (BF.KEEP_MIN); null with reduced motion, where the page plays none. Times are whole milliseconds from the build's start. */
  function nativeBuild(d0, d1, arcs, isToday) {
    const q = ui.nativeBuild;
    if (!q || reduced() || Date.now() - q.at > BF.KEEP_MIN) return null;
    const B = buildPlan(q, d0, d1, arcs.filter(a => a.style === 'session').map(a => ({ id: a.id, s: a.s, e: a.e })), arcs.filter(a => a.style === 'band').map(a => ({ s: a.s, e: a.e, title: a.title })), isToday);
    const ms = v => Math.round(v);
    return Object.assign({ id: q.id }, B, {
      start: Object.assign({}, B.start, { at: ms(B.start.at) }), lock: Object.assign({}, B.lock, { at: ms(B.lock.at) }), end: ms(B.end),
      items: B.items.map(it => Object.assign({}, it, { at: ms(it.at), ms: ms(it.ms), land: ms(it.land) })),
    });
  }
  const mealTap = b => ({ act: 'meal', id: String(b.mealStartEpoch || b.startEpoch) });
  const focusTimer = f => ({ startedAt: f.start, pausedMs: f.pausedMs || 0, paused: f.paused || null });
  function nativeCore(day, blocks, isToday, now, st) {
    if (!isToday) {
      const tasks = blocks.filter(b => b.inst), taskMin = tasks.reduce((n, b) => n + Math.round((b.endEpoch - b.startEpoch) / 60), 0), first = tasks.find(b => b.inst.lifecycleState === 'PLANNED');
      return { kick: fmt({ weekday: 'long' }).format(new Date(day.startEpoch * 1000)), kickKind: '', warn: false, title: fmt({ month: 'short', day: 'numeric' }).format(new Date(day.startEpoch * 1000)), date: true, big: null, timer: null,
        sub: tasks.length ? tasks.length + ' session' + (tasks.length === 1 ? '' : 's') + ' · ' + mins(taskMin) : 'No sessions planned', next: first ? { label: 'First · ' + fC(first.startEpoch), title: first.inst.title, live: false, act: '', id: '' } : null, button: null };
    }
    const f = ST.ui.focus, fi = f && !f.paused && findInst(f.id);
    const out = { kick: 'Now', kickKind: '', warn: false, title: 'Free time', date: false, big: null, timer: null, sub: '', next: null, button: null };
    if (fi) {
      Object.assign(out, { kick: 'Timer running', kickKind: 'go', title: fi.title.replace(/^Plan:\s*/i, '').replace(/\s*\([^)]*\)\s*$/, '').replace(/&amp;/gi, '&').trim(),
        timer: Object.assign(focusTimer(f), { act: 'focusOpen' }), sub: 'of ' + mins(Math.round((fi.endEpoch - fi.startEpoch) / 60)) + ' planned' });
    } else if (st.cur) {
      const c = st.cur, cc = c.inst ? 'task' : blockClass(c), done = c.inst && sesState(c.inst.lifecycleState) === 'done', m = Math.max(0, Math.round((c.endEpoch - now) / 60));
      out.kick = done ? '✓ Done' : 'Now'; out.kickKind = cc;
      if (st.kick && st.kick.kind === 'late') { out.kick = 'Late by ' + relText(st.kick.sec); out.warn = true; } else if (st.kick && st.kick.kind === 'ending') { out.kick = 'Wrapping up'; out.warn = true; }
      out.title = done ? c.inst.title : doingName(c);
      out.big = m < 1 ? { h: 0, m: 1, less: true } : { h: Math.floor(m / 60), m: m % 60, less: false };
      out.sub = (done ? 'free until ' : 'left · until ') + fC(c.endEpoch);
      if (st.isSes) out.button = { label: 'Start', act: 'focusStart', id: c.inst.instanceId, go: true };
      else if (c.inst) out.button = { label: 'Details', act: 'inst', id: c.inst.instanceId, go: false };
      else if (cc === 'meal' && window.LifeMaxMeals) out.button = Object.assign({ label: 'View meal', go: false }, mealTap(c));
    }
    if (st.soon && !(st.cur && st.cur.inst && st.soon.b === st.cur)) {
      const b = st.soon.b;
      out.next = { label: st.soon.sec < 90 ? 'Starting now' : 'Starting in ' + relText(st.soon.sec), title: b.inst.title, live: true, act: 'inst', id: b.inst.instanceId };
      if (!fi && !st.isSes) out.button = { label: 'Start early', act: 'focusStart', id: b.inst.instanceId, go: true };
    } else if (st.next) {
      const b = st.next, same = U.ymdKey(U.localParts(b.startEpoch, tz())) === U.ymdKey(U.localParts(now, tz()));
      out.next = Object.assign({ label: 'Next' + (b.startEpoch - now < 3 * 3600 ? ' in ' + relText(b.startEpoch - now) : ' · ' + (same ? '' : 'Tomorrow ') + fC(b.startEpoch)), title: doingName(b), live: false, act: '', id: '' },
        !b.inst && blockClass(b) === 'meal' ? mealTap(b) : null);
    } else if (!fi) out.next = { label: 'Nothing else planned today', title: '', live: false, act: '', id: '' };
    return out;
  }
  function nativeMix(blocks) {
    const sum = { sleep: 0, work: 0, maint: 0, meal: 0, task: 0, rest: 0, free: 0 };
    for (const b of blocks) { const k = blockClass(b), m = (b.endEpoch - b.startEpoch) / 60; const key = k === 'commute' || k === 'block' ? 'work' : k; if (sum[key] != null) sum[key] += m; }
    return [['sleep', 'Sleep'], ['work', 'Work & travel'], ['maint', 'Routines'], ['meal', 'Meals'], ['task', 'Sessions'], ['rest', 'Downtime'], ['free', 'Free']]
      .map(([k, l]) => ({ kind: k, label: l, minutes: Math.round(sum[k]), text: sum[k] ? mins(Math.round(sum[k] / 15) * 15) : '—' }));
  }
  function nativeUpNext(day, days, now) {
    const next = nextUp(now), f = ST.ui.focus, fi = f && findInst(f.id);
    if (fi) return { kind: 'go', cue: f.paused ? 'Timer paused' : 'Timer running', title: fi.title, when: 'of ' + mins(Math.round((fi.endEpoch - fi.startEpoch) / 60)) + ' planned', timer: focusTimer(f), chips: [],
      actions: [{ label: 'Open timer', act: 'focusOpen', primary: true }, { label: 'Finish and log', act: 'focusFinish' }] };
    if (!next) return null;
    if (!doable(next, now)) {
      const di = days.findIndex(d => next.startEpoch >= d.startEpoch && next.startEpoch < d.endEpoch);
      return { kind: 'rest', cue: 'That’s everything for today', title: 'Next: ' + next.title, when: fDay(next.startEpoch) + ', ' + fTime(next.startEpoch) + '–' + fTime(next.endEpoch), timer: null, chips: [],
        actions: di > 0 ? [{ label: 'See ' + (di === 1 ? 'tomorrow' : DOW[days[di].dow]), day: di }] : [] };
    }
    const on = next.startEpoch <= now, quests = next.linkedQuestRequirementIds.map(l => l.questTitle), x = xpOf(next);
    return { kind: on ? 'go' : 'next', cue: on ? 'On now · ' + relText(next.endEpoch - now) + ' left' : 'Up next · in ' + relText(next.startEpoch - now), title: next.title,
      when: (next.startEpoch >= day.endEpoch ? fDay(next.startEpoch) + ', ' : '') + fTime(next.startEpoch) + '–' + fTime(next.endEpoch) + ' · ' + mins(next.durationSlots * 15), timer: null,
      chips: (quests.length ? [{ kind: 'quest', text: quests.join(', ') }] : []).concat(x ? [{ kind: 'xp', text: '+' + x.xp + ' XP · ' + attrName(x.rule.primary) }] : []),
      actions: [{ label: 'Start', act: 'focusStart', id: next.instanceId, primary: true }, { label: 'Done', act: 'doneQuick', id: next.instanceId }, { label: 'Details', act: 'inst', id: next.instanceId, ghost: true }] };
  }
  function nativeRows(blocks, isToday, now, withRemoval) {
    const row = b => {
      const k = blockClass(b), m = Math.round((b.endEpoch - b.startEpoch) / 60), st = b.inst ? b.inst.lifecycleState : '';
      if (k === 'free' && m < 15) return null;
      const meal = k === 'meal' ? scheduledMeal(b) : null, chips = [];
      if (b.inst) {
        if (b.inst.linkedQuestRequirementIds.length) chips.push({ kind: 'quest', text: b.inst.linkedQuestRequirementIds.map(l => l.questTitle).join(', ') });
        if (b.inst.goalTitle) chips.push({ kind: 'goal', text: b.inst.goalTitle });
        if (st === 'PLANNED') { const x = xpOf(b.inst); if (x) chips.push({ kind: 'xp', text: '+' + x.xp + ' XP · ' + attrName(x.rule.primary) }); }
        if (b.inst.isUserLocked) chips.push({ kind: 'pinned', text: 'Pinned' });
      }
      const tap = b.inst ? { act: 'inst', id: b.inst.instanceId } : k === 'meal' ? mealTap(b) : { act: '', id: '' };
      return Object.assign({ s: b.startEpoch, e: b.endEpoch, time: fTime(b.startEpoch, tz()), length: mins(m), kind: k, title: b.title || '', icon: b.inst ? tagIcon(b.inst.tag) : (k === 'meal' ? 'meal' : KIND_ICON[k] || 'star'),
        state: b.inst ? sesState(st) : '', stateLabel: st && STATE_LABEL[st] ? STATE_LABEL[st] : '', now: isToday && b.startEpoch <= now && b.endEpoch > now, past: isToday && b.endEpoch <= now,
        sub: meal ? meal.name + ' · ' + (meal.logged ? 'Logged' : meal.skipped ? 'Skipped' : meal.kcal + ' kcal') + ' · View meal ›' : !b.inst && k === 'rest' ? 'Kept clear. Nothing gets booked here.' : '', chips },
        tap, withRemoval && b.inst ? { remove: nativeRemoval(b.inst) } : null);
    };
    const past = isToday ? blocks.filter(b => b.endEpoch <= now) : [];
    const ahead = past.length >= 2 ? blocks.filter(b => b.endEpoch > now) : blocks;
    const pt = past.filter(b => b.inst), pd = pt.filter(b => /COMPLETED/.test(b.inst.lifecycleState)).length;
    return { ahead: ahead.map(row).filter(Boolean), earlier: past.length >= 2 ? { summary: pt.length ? pd + ' of ' + pt.length + ' sessions done' : past.length + ' blocks', rows: past.map(row).filter(Boolean) } : null };
  }
  /* What the page shows: 'sheet' or 'guide' over everything, 'first-run' while Today has no plan to show, else the
     section on screen (a tab key); null while Life Director is not on the page. A native screen covers the page only
     while this names what it draws. */
  function showing() {
    if (!root || !root.isConnected) return null;
    if (root.querySelector('.ld-sheet.open')) return 'sheet';
    if (ui.tour || (tourDue(ui.tab) && !nativeGuides(ui.tab))) return 'guide'; // a guide the screen draws does not stop it covering
    if ((ui.tab === 'today' || ui.tab === 'week') && !(ST.setupComplete && ST.schedule)) return 'first-run'; // both draw needSetup
    return ui.tab;
  }
  /* A tap handed back by a native screen, run as its web element runs it: the element's act and the one id it needs
     (named here by the data-* attribute it fills). 'meal' opens the meal whose first minute (epoch seconds) is the id,
     as its arc and row do. Returns false for an act Today does not have, and, saying so, for a session or meal that has
     left the plan since the screen drew it. */
  const ACT_ID = new Map([['inst', 'id'], ['focusStart', 'id'], ['doneQuick', 'id'], ['tab', 'tab'], ['regroup', 'v'], ['movePick', 'id'], ['openPlan', 'v'],
    ['activityOpen', 'id'], ['focusOpen', ''], ['focusFinish', ''], ['tour', ''], ['disrupt', ''], ['replan', ''], ['dismissBanner', ''], ['openQuests', ''], ['addGoal', '']]);
  function nativeAct(act, id) {
    if (!root || !root.isConnected) return false;
    const gone = () => { say('That is no longer on the plan.'); return false; };
    let data;
    if (act === 'meal' || act === 'mealOpen') { // the meal's sheet, or (from a screen's own meal sheet) straight to its menu
      const m = mealSegment(id);
      if (!m) return gone();
      data = { act: act === 'meal' ? 'anchor' : 'mealOpen', k: 'meal', t: m.title, s: m.startEpoch, e: m.endEpoch, ms: m.startEpoch };
    } else if (ACT_ID.has(act)) {
      if (ACT_ID.get(act) === 'id' && !findInst(id)) return gone();
      data = ACT_ID.get(act) ? { act, [ACT_ID.get(act)]: id } : { act };
    } else return false;
    perform({ dataset: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, v == null ? '' : String(v)])) }); // strings, as a dataset holds them
    return true;
  }
  /* Listeners hear, once per burst and just after it, that what Life Director shows may have changed: a render, a
     sheet or the guide opening or closing, a message. */
  const watchers = new Set();
  let changeQueued = false;
  function changed() {
    if (changeQueued || !watchers.size) return;
    changeQueued = true;
    queueMicrotask(() => {
      changeQueued = false;
      watchers.forEach(fn => { try { fn(); } catch (e) { console.warn('[Life Director] change listener', e); } });
    });
  }
  /* A handle let go on a native dial, as the page's own release (or arrow key) reads it. key names a handle of
     today(day).dial.handles on any day of the plan, and p is {minute} (a stop's m, where the finger let go) or {step} (1 or -1:
     a quarter hour later or earlier, as the handle's arrow keys move it). Returns {P, rule}, the change the page would hold
     (ui.pendingDial), or {same} for a release where the handle already is, or {message} when the handle or the time is not on
     the dial any more (the rule changed since the screen drew it: its handles have new keys). Only while the page shows Today with
     nothing over it, the state a native screen covers: a change asked for after the screen left it would close what the page shows
     now and apply what nobody saw confirmed. */
  function nativeChange(key, p) {
    if (!root || !root.isConnected || !ST.setupComplete || !ST.schedule) return { message: 'Life Director has no plan to change.' };
    if (showing() !== 'today') return { message: 'Today is not on the page. Nothing was changed.' };
    let day = null, h = null;
    for (const d of horizonDays(ST.schedule, tz())) { h = dialHandles(d).find(x => x.key === key); if (h) { day = d; break; } }
    if (!h) return { message: 'That handle is no longer on the dial. Nothing was changed.' };
    const step = Number(p && p.step), m = Number(p && p.minute), om = minuteOf(h.ep);
    if (step === 1 || step === -1) return { P: dialStep(h.rule, h.end, step), rule: h.rule };
    if (!Number.isInteger(m) || !dialStops(day.startEpoch, day.endEpoch).some(st => st.m === m)) return { message: 'That time is not on the dial. Nothing was changed.' };
    return m === om ? { same: true } : { P: dialChange(h.rule, h.end, om, m), rule: h.rule };
  }
  /* dialDrop: what the page asks when its handle is let go there (dialConfirm), and nothing else: nothing is held or saved */
  function nativeDrop(key, p) {
    const c = nativeChange(key, p);
    if (c.message) return { ok: false, changed: false, message: c.message };
    return c.same ? { ok: true, changed: false } : { ok: true, changed: true, confirm: dialConfirm(c.P, c.rule) };
  }
  /* dialApply: Change it on that confirmation. The same handle and place as the drop, read again, so a change is applied only
     while the rule is still the one the screen drew: the page holds it and its own Change it runs (applyDial), which changes the
     rule, repairs the plan around it and saves. The same request again names a handle that has moved, and is refused, so the save
     changes once. A change with a problem has no Change it on the page, only OK, and is refused with the problem. */
  function nativeApply(key, p) {
    const c = nativeChange(key, p);
    if (c.message) return { ok: false, changed: false, moved: false, message: c.message };
    if (c.same) return { ok: true, changed: false, moved: false, message: '' };
    const problem = dialConfirm(c.P, c.rule).problem;
    if (problem) return { ok: false, changed: false, moved: false, message: problem };
    ui.pendingDial = c.P;
    return applyDial(nowEpoch());
  }
  /* Week as data for a native screen (phase 3A): weekModel's decisions in the page's words, null without a plan. A day row's
     segments are its bar's (weekSegs), in percent of the day; a colour is ARGB, from the attribute colours LifeMax draws with. */
  function nativeWeek() {
    if (!ST) boot();
    if (!ST.setupComplete || !ST.schedule) return null;
    const W = weekModel(), argb = c => (typeof NativeTabKit !== 'undefined' && NativeTabKit.color ? NativeTabKit.color(c) : 0);
    const U = unscheduledGroups(W.s), round2 = v => Math.round(v * 100) / 100;
    return {
      stats: W.stats.map(([value, label]) => ({ value, label })),
      ticks: WEEK_TICKS.slice(),
      days: W.rows.map(r => ({ index: r.k, label: r.name, date: r.d.ymd.d, today: r.k === 0, aria: r.aria, count: String(r.n), done: r.done ? '✓' + r.done : '', minutes: r.m ? mins(r.m) : '—',
        segs: weekSegs(r.d, r.blocks).map(g => ({ kind: g.kind, state: g.state, left: round2(g.left), width: round2(g.width) })) })),
      legend: WEEK_LEGEND.map(([kind, label]) => ({ kind, label })),
      yield: W.attrs.length ? { title: 'Where your XP goes', note: YIELD_NOTE,
        rows: W.attrs.map(([a, v]) => ({ label: attrName(a), color: argb(W.colOf(a)), done: round2(v.done / W.maxA), plan: round2(v.plan / W.maxA), total: String(Math.round(v.done + v.plan)) })) } : null,
      fit: { title: 'How the week fits', text: W.fitText, allFit: ALL_FIT,
        unscheduled: U.count ? { summary: unfitSummary(U.count), groups: U.groups.map(g => ({ title: g.d.title + (g.n > 1 ? ' ×' + g.n : ''), tier: 'tier ' + g.d.tier, text: g.d.humanExplanation })) } : null },
    };
  }
  /* Quests as data for a native screen (phase 3A): questsModel's decisions; rows is [] without a plan */
  function nativeQuests() {
    if (!ST) boot();
    const Q = questsModel();
    return { head: Q.head, rows: Q.rows || [], passive: Q.passive || [], empty: Q.empty || '', milestones: Q.milestones || null };
  }
  /* Section and day changes asked for by a native screen. Each renders once, so the page underneath shows the same, and saves
     nothing: the section rides on the next save, as phase 1's day did (the page's own tab buttons save on every switch). */
  const SECTIONS = new Set(TABS.map(t => t[0]).concat('lab'));
  function nativeSection(id) {
    if (!ST) boot();
    if (!SECTIONS.has(id) || (id === 'lab' && !(ST.ui && ST.ui.lab))) return { ok: false, message: 'That section is not in Life Director.' };
    ui.tab = id; ST.ui.tab = id;
    if (root && root.isConnected) render();
    return { ok: true };
  }
  /* the strip's day (day) or a day from Week or up next (gotoDay, which also goes to Today), as the page's own handlers do */
  function nativeDay(i, toToday) {
    if (!ST) boot();
    const days = ST.setupComplete && ST.schedule ? horizonDays(ST.schedule, tz()) : [];
    if (!Number.isInteger(i) || i < 0 || i >= days.length) return { ok: false, message: 'That day is not on the plan.' };
    ui.dayIdx = i;
    if (toToday) ui.tab = 'today';
    if (root && root.isConnected) render();
    return { ok: true };
  }
  /* ── Today's sheets for a native screen (phase 4A) ──
     The session sheet as data: instSheet's branches (now, later, past) in the page's words. A session that has left the plan
     answers {kind, gone: true}; the screen closes the sheet and says so. */
  const MINUTES_MAX = 480;
  function reminderText(reminder) {
    const description = Host.activityDescription ? Host.activityDescription({ title: reminder.title, linkedQuestRequirementIds: [{ questId: reminder.questId, questTitle: reminder.title }] }) : '';
    return reminder.title + (description ? '\n' + description : '');
  }
  function nativeSessionSheet(id) {
    const inst = findInst(id) || ((ST.schedule && ST.schedule.historyInstances) || []).find(h => h.instanceId === id);
    if (!inst) return { kind: 'session', id: String(id), gone: true };
    const x = inst.explanation || {}, planned = inst.lifecycleState === 'PLANNED', now = nowEpoch();
    const ref = (inst.linkedQuestRequirementIds || []).find(l => String(l.questId).startsWith('plan:'));
    const running = !!(ST.ui.focus && ST.ui.focus.id === inst.instanceId), xp = planned ? xpOf(inst) : null;
    const mode = !planned ? 'past' : doable(inst, now) ? 'now' : 'later';
    const row = [];
    if (mode === 'now' && ref) row.push({ label: 'Open in Plan', command: 'openPlan', key: ref.questId.slice(5) });
    if (planned) row.push({ label: 'Move', sheet: 'move' }, { label: inst.isUserLocked ? 'Unpin' : 'Pin here', command: 'pin', on: !inst.isUserLocked }, { label: 'Skip', command: 'skip' });
    return {
      kind: 'session', id: inst.instanceId, state: sesState(inst.lifecycleState), icon: tagIcon(inst.tag), title: inst.title,
      description: Host.activityDescription ? Host.activityDescription(inst) : '',
      when: fDay(inst.startEpoch) + ' · ' + fTime(inst.startEpoch) + '–' + fTime(inst.endEpoch) + ' · ' + mins(inst.durationSlots * 15) + (inst.compressed ? ' (shortened)' : ''),
      chips: (inst.linkedQuestRequirementIds || []).map(l => ({ kind: 'quest', text: l.questTitle }))
        .concat(inst.goalTitle ? [{ kind: 'goal', text: 'Goal: ' + inst.goalTitle }] : [], xp ? [{ kind: 'xp', text: '+' + xp.xp + ' XP · ' + attrName(xp.rule.primary) }] : []),
      mode,
      note: mode === 'later' ? [{ t: 'Planned for ', b: false }, { t: fDay(inst.startEpoch) + ' at ' + fTime(inst.startEpoch), b: true }, { t: '. You can start it or mark it done on the day, or from 6 hours before.', b: false }] : [],
      minutes: planned ? { label: mode === 'later' ? 'Did it today anyway? Minutes' : 'Already did it? Minutes', value: inst.durationSlots * 15, min: 1, max: MINUTES_MAX } : null,
      primary: mode === 'now' ? (running ? { label: 'Open timer', sheet: 'timer' } : { label: 'Start timer', command: 'timer', act: 'start' }) : null,
      complete: mode === 'now' ? { label: 'Mark done', command: 'done', smallPrint: '' }
        : mode === 'later' ? { label: 'Log it for today', command: 'logToday', smallPrint: 'You get the XP now, and this session stays on ' + DOW[U.localParts(inst.startEpoch, tz()).dow] + '.' } : null,
      row,
      past: mode === 'past' ? (STATE_LABEL[inst.lifecycleState] || inst.lifecycleState) + (inst.actualMinutes ? ' · ' + inst.actualMinutes + ' min' : '') + (inst.undone ? ' · undone in your log' : '') : '',
      why: { title: 'Why this time?', summary: x.summary || 'Placed by Life Director.',
        factors: (x.factors || []).map(f => ({ name: f.factor, points: f.points, frac: Math.min(1, Math.max(0, f.points / 24)) })),
        feasible: x.selection && x.selection.feasibleDays ? 'It could have fitted on ' + x.selection.feasibleDays + ' day' + (x.selection.feasibleDays === 1 ? '' : 's') + '.' : '' },
      orphan: inst.orphanWarning ? 'Its quest was completed or removed. Keep it as free practice or remove it.' : '',
    };
  }
  function nativeSheet(kind, id) {
    if (!ST) boot();
    if (FORM_SHEETS.has(kind)) return nativeForm(kind, String(id || ''));
    if (!ST.setupComplete || !ST.schedule) return null;
    const key = String(id || ''), gone = { kind, gone: true };
    if (kind === 'session') return nativeSessionSheet(key);
    if (kind === 'timer') { // the running timer, of that session (any session when none is named)
      const f = ST.ui.focus, i = f && (!key || f.id === key) && findInst(f.id);
      return i ? Object.assign({ kind }, focusModel(f, i)) : gone;
    }
    if (kind === 'move') { const i = plannedInst(key); return i && i.lifecycleState === 'PLANNED' ? Object.assign({ kind }, moveModel(i)) : gone; }
    if (kind === 'meal') { const m = mealSegment(key); return m ? Object.assign({ kind }, mealModel({ title: m.title, startEpoch: m.startEpoch, endEpoch: m.endEpoch, mealStartEpoch: m.startEpoch, kind: 'MEAL' })) : gone; }
    if (kind === 'disrupt') return Object.assign({ kind }, DISRUPT, { sections: disruptParts() });
    return null;
  }
  /* ── Taking a session off a day (the screen's drag to the trash, and TalkBack's "Remove from today") ──
     Removing is the page's own Skip (Today.skip below: the engine's SKIPPED trigger, its miss consequence and the re-plan, nothing
     new), and the occurrence only: the recurring rule is never touched. A session can be taken off while it is planned and no timer
     runs on it; every row says so (director.remove.v1), with the reason where it cannot, so the screen decides nothing.
     Undo (Today.unskip) puts the plan and the ledger back as they were before that Skip, for as long as nothing else has changed the
     plan: the page keeps the one skip last made, with the plan as it was left, in memory only. */
  const REMOVE_WHY = { running: 'Its timer is running. Stop the timer first.', done: 'It is already done, so it stays.', part: 'It is already part done, so it stays.', off: 'It is already off the plan.' };
  function nativeRemoval(inst) {
    if (ST.ui.focus && ST.ui.focus.id === inst.instanceId) return { ok: false, reason: REMOVE_WHY.running };
    const st = sesState(inst.lifecycleState);
    return st === 'plan' ? { ok: true, reason: '' } : { ok: false, reason: REMOVE_WHY[st] };
  }
  const TAKE_BACK_MS = 120000; // how long after a Skip it can be taken back
  let takeBack = null, takenBackOp = null; // {id, text, at, before: {schedule, ledger}, left: the plan as the Skip left it (JSON)}
  const planJson = () => JSON.stringify(ST.schedule);
  /* a session still on the plan (findInst also finds resolved ones, which no action may touch) */
  const plannedInst = (id) => ST.schedule && ST.schedule.scheduledInstances.find(i => i.instanceId === String(id));
  /* A session already resolved by an earlier, identical request: its history entry */
  const resolved = (id) => ((ST.schedule && ST.schedule.historyInstances) || []).find(h => h.instanceId === id);
  const minutesOf = (inst, m) => Math.max(1, Math.min(MINUTES_MAX, Math.round(Number(m)) || inst.durationSlots * 15));
  const recorded = (message) => ({ ok: persist(), changed: false, message }); // a repeat: is what landed saved now?
  const gone = { ok: false, message: 'That is no longer on the plan.' };
  const applied = () => Object.assign(saved(''), { changed: true }); // a change made: ok when it is saved
  const offered = (part, v) => !!part && part.options.some(o => String(o.value) === String(v));
  const dayMinute = v => (typeof v === 'number' && Number.isInteger(v) && v >= 0 && v < 1440 ? v : null);
  const DISRUPT_ID = { work: 'c', block: 'b', day: 'd', sick: 's' }; // the page's id prefixes for each kind of disruption
  /* a refused completion: a repeat of one that landed, or the ledger's reason in the page's words */
  const refusedFinish = () => lastFinishRefusal === 'DUPLICATE_EVENT_ID' ? recorded('Already recorded.') : { ok: false, message: 'Cannot complete this block (' + lastFinishRefusal + ').' };
  /* Today's writes from a native screen (phase 4A). Each answers {ok, changed, message}: ok only when saved; a repeat of a write
     that already landed answers changed: false with "Already recorded." or "Already skipped."; the page's own notice (say)
     carries what happened on success. */
  const Today = Object.freeze({
    done({ id, minutes }) {
      const inst = plannedInst(id);
      if (!inst) return resolved(id) && /COMPLETED/.test(resolved(id).lifecycleState) ? recorded('Already recorded.') : gone;
      if (inst.lifecycleState !== 'PLANNED') return gone;
      if (!doable(inst, nowEpoch())) return { ok: false, message: 'You can mark it done on the day, or from 6 hours before.' };
      const r = finishInstance(inst, minutesOf(inst, minutes), { quietRefusal: true });
      if (!r) return refusedFinish();
      if (ST.ui.focus && ST.ui.focus.id === inst.instanceId) { ST.ui.focus = null; persist(); }
      render(); return saved('');
    },
    logToday({ id, minutes, op }) {
      const inst = plannedInst(id);
      if (!inst) return gone;
      if (!op) return { ok: false, message: 'Nothing was logged.' };
      const r = logToday(inst, minutesOf(inst, minutes), String(op));
      if (r && r.duplicate) return recorded('Already recorded.');
      if (!r) return { ok: false, message: 'Nothing was logged.' };
      render(); return saved('');
    },
    skip({ id }) {
      const inst = plannedInst(id);
      if (!inst) {
        if (!(resolved(id) && /SKIPPED/.test(resolved(id).lifecycleState))) return gone;
        const r = recorded('Already skipped.'); // a repeat: the take-back it earned is still on offer
        return takeBack && takeBack.id === String(id) && r.ok ? Object.assign(r, { undo: takeBack.text }) : r;
      }
      if (inst.lifecycleState !== 'PLANNED') return gone;
      if (!nativeRemoval(inst).ok) return { ok: false, message: nativeRemoval(inst).reason };
      const before = { schedule: JSON.parse(planJson()), ledger: JSON.parse(JSON.stringify(ST.ledger)) };
      const c = E.applyMissConsequence(ST.ledger, inst, ST.prefs, nowEpoch(), 'SKIP'); ST.ledger = c.ledger;
      const r = reschedule({ type: 'SKIPPED', instanceId: id });
      persist(); render();
      const text = 'Skipped' + (c.consequence && c.consequence.type === 'REST_TOKEN_SPENT' ? ' · used a rest token' : '') + (r ? ' · ' + summarize(r) : '');
      say(text);
      takeBack = { id: String(id), text, at: Date.now(), before, left: planJson() };
      return Object.assign(saved(''), { changed: true, undo: text });
    },
    /* Undo of the last Skip: the plan and the ledger as they were, only while the plan is as the Skip left it (anything since, a
       move, a pin, a re-plan, a session done, makes it too late: the answer says so and changes nothing). The same request again
       finds the session back on the plan and only makes sure it is saved. */
    unskip({ id, op }) {
      if (op && op === takenBackOp) return recorded('Already back on the plan.');
      const t = takeBack;
      if (!t || t.id !== String(id)) return plannedInst(id) ? recorded('Already back on the plan.') : { ok: false, message: 'That can no longer be taken back.' };
      if (Date.now() - t.at > TAKE_BACK_MS || planJson() !== t.left) return { ok: false, message: 'The plan has changed since, so that cannot be taken back.' };
      ST.schedule = t.before.schedule; ST.ledger = t.before.ledger; takeBack = null; takenBackOp = op || null;
      ST.changeLog.push({ at: nowEpoch(), label: 'Skip taken back' });
      persist(); render(); say('Back on the plan');
      return applied();
    },
    pin({ id, on }) {
      const inst = plannedInst(id);
      if (!inst) return gone;
      const want = !!on;
      if (inst.isUserLocked === want) return recorded('');
      inst.isUserLocked = want;
      if (inst.explanation) inst.explanation = Object.assign({}, inst.explanation, { summary: want ? 'You pinned this time, so Life Director never moves it automatically.' : inst.explanation.summary });
      persist(); render(); say(want ? 'Pinned' : 'Unpinned'); return saved('');
    },
    moveTo({ id, start }) {
      const inst = plannedInst(id), at = Number(start);
      if (!inst) return gone;
      if (!Number.isInteger(at)) return { ok: false, message: 'That time is not on the plan.' };
      if (inst.startEpoch === at && inst.isUserLocked) return recorded('');
      const r = reschedule({ type: 'MOVE', instanceId: id, newStartEpoch: at });
      if (!r) return { ok: false, message: 'It does not fit there. Nothing was moved.' };
      persist(); render(); say('Moved and pinned · ' + summarize(r)); return saved('');
    },
    /* the timer: start is set-like for the same session; pause and resume are guarded on state; finish and cancel name the start
       they mean, so a repeat after one landed is not taken for a new timer */
    timer({ act, id, start }) {
      const f = ST.ui.focus;
      const automatic = act === 'autoFinish';
      let automaticMinutes = null;
      if (automatic) {
        if (!f || f.start !== Number(start) || f.id !== id || f.paused) return recorded('');
        const inst = plannedInst(f.id);
        if (!inst) return gone;
        const target = f.plannedMs || Math.max(60, inst.endEpoch - inst.startEpoch) * 1000;
        if (focusMs(f) < target) return recorded('');
        automaticMinutes = Math.max(1, Math.round(target / 60000));
        act = 'finish';
      }
      if (act === 'start') {
        const inst = plannedInst(id);
        if (!inst || inst.lifecycleState !== 'PLANNED') return gone;
        if (!doable(inst, nowEpoch())) return { ok: false, message: 'You can start it on the day, or from 6 hours before.' };
        if (f && f.id === id) return recorded('');
        if (f && plannedInst(f.id)) return { ok: false, message: 'Another timer is running. Finish or stop it first.' };
        ST.ui.focus = { id, start: Date.now(), paused: null, pausedMs: 0, plannedMs: Math.max(60, inst.endEpoch - inst.startEpoch) * 1000 }; persist(); render(); return saved('');
      }
      if (act === 'pause') { if (!f || f.paused) return recorded(''); f.paused = Date.now(); persist(); render(); return saved(''); }
      if (act === 'resume') { if (!f || !f.paused) return recorded(''); f.pausedMs = (f.pausedMs || 0) + Date.now() - f.paused; f.paused = null; persist(); render(); return saved(''); }
      if (act === 'finish' || act === 'cancel') {
        if (!f || f.start !== Number(start)) {
          const done = act === 'finish' && ((ST.schedule && ST.schedule.historyInstances) || []).some(h => h.instanceId === id && /COMPLETED/.test(h.lifecycleState));
          return done ? recorded('Already recorded.') : !f ? recorded('') : { ok: false, message: 'That timer is not running.' };
        }
        if (act === 'cancel') { ST.ui.focus = null; persist(); render(); say('Timer stopped. Nothing was logged.'); return saved(''); }
        const inst = plannedInst(f.id), minutes = automatic ? automaticMinutes : Math.max(1, Math.round(focusMs(f) / 60000));
        ST.ui.focus = null;
        if (!inst || inst.lifecycleState !== 'PLANNED') { persist(); render(); say('That session is no longer on the plan. Log it from the Log page.'); return saved(''); }
        ui.fx.justDone = inst.instanceId;
        const r = finishInstance(inst, minutes, { startEpoch: Math.floor(f.start / 1000), quietRefusal: true });
        if (!r && automatic) { ST.ui.focus = f; persist(); }
        render(); return r ? saved('') : refusedFinish();
      }
      return { ok: false, message: 'Life Director cannot do that.' };
    },
    /* Something came up: the commitment or blackout is named from op, so the same request again finds it ("Already added").
       A choice must be one the sheet offers (disruptParts). A repair that changed nothing has said why already (reschedule's notice,
       or the banner): said tells the page not to say it twice. */
    disrupt({ kind, op, date, from, until, commute, hours, days }) {
      const part = disruptParts().find(s => s.key === kind && s.command === 'disrupt'), now = nowEpoch();
      if (!part) return { ok: false, message: 'Life Director cannot do that.' };
      if (!op) return { ok: false, message: 'Nothing was added.' };
      const id = DISRUPT_ID[kind] + '-' + op;
      if ((ST.fixedCommitments || []).concat(ST.availability.blackouts || []).some(c => String(c.id) === id)) return recorded('Already added');
      if (part.options && !offered(part, kind === 'block' ? hours : days)) return { ok: false, message: 'That is not one of the choices.' };
      let trigger;
      if (kind === 'work') {
        const [y, mo, d] = String(date || '').split('-').map(Number), s0 = dayMinute(from), e0 = dayMinute(until);
        if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || s0 == null || e0 == null) return { ok: false, message: 'Enter a date, start and end' };
        trigger = { type: 'NEW_COMMITMENT', commitment: { id, title: 'Extra shift', startEpoch: U.zonedToEpoch(y, mo, d, s0, tz()), endEpoch: U.zonedToEpoch(y, mo, d, e0 <= s0 ? e0 + 1440 : e0, tz()), commuteMin: Math.max(0, Number(commute) || 0), kind: 'WORK' } };
      } else if (kind === 'block') trigger = { type: 'BLOCK_HOURS', commitment: { id, title: 'Unavailable', startEpoch: now, endEpoch: now + Number(hours) * 3600 } };
      else if (kind === 'day') { const p = U.localParts(now, tz()), nx = U.ymdAdd({ y: p.y, mo: p.mo, d: p.d }, 1); trigger = { type: 'BLACKOUT_DAY', commitment: { id, title: 'Day off the plan', startEpoch: now, endEpoch: U.zonedToEpoch(nx.y, nx.mo, nx.d, 0, tz()) } }; }
      else trigger = { type: 'SICK', commitment: { id, title: 'Sick', startEpoch: now, endEpoch: now + Number(days) * 86400 } };
      const told = ui.notice && ui.notice.id, r = reschedule(trigger);
      if (!r) return { ok: false, said: true, message: ui.notice && ui.notice.id !== told ? ui.notice.text : (ui.banner && ui.banner.text) || 'Nothing was changed.' };
      persist(); render(); say((kind === 'sick' ? 'Rest up · ' : '') + summarize(r)); return applied();
    },
    /* an energy check-in: set, and it re-plans (the same level again re-plans the same way) */
    energy({ level }) {
      if (!offered(disruptParts().find(s => s.key === 'energy'), level)) return { ok: false, message: 'That is not one of the choices.' };
      ST.energyCheckin = { level: String(level), epoch: nowEpoch() }; regenerate('energy check-in', true); render(); say('Energy noted'); return applied();
    },
    /* Re-plan from now (a re-plan twice is the same plan); a plan the engine refused leaves its banner, whose words are the answer */
    replan() {
      if (ui.banner && ui.banner.kind === 'bad') ui.banner = null;
      const ok = regenerate('requested'); render();
      return ok ? applied() : { ok: false, message: (ui.banner && ui.banner.text) || 'Nothing was changed.' };
    },
    /* the regroup question's answers, only while it shows. Answered already, the same answer again is recorded (the pause is named
       from op, light mode and carrying on are states); any other answer is refused, as the question has gone. */
    regroup({ v, op }) {
      const now = nowEpoch(), id = 'p-' + op;
      if (!['pause', 'light', 'go'].includes(v)) return { ok: false, message: 'Life Director cannot do that.' };
      if (v === 'pause' && !op) return { ok: false, message: 'Nothing was changed.' };
      if (!(ui.banner && ui.banner.regroup)) {
        const landed = v === 'pause' ? (ST.availability.blackouts || []).some(c => c.id === id)
          : v === 'light' ? E.normalizePreferences(ST.prefs).intensityLevel === 'LIGHT' && ST.prefsLightUntil > now : !ST.ledger.consecutiveMisses;
        return landed ? recorded(v === 'pause' ? 'Already added' : '') : { ok: false, message: 'That question is no longer showing.' };
      }
      if (v === 'pause') reschedule({ type: 'BLACKOUT_DAY', commitment: { id, title: 'Paused', startEpoch: now, endEpoch: now + 48 * 3600 } });
      if (v === 'light') { ST.prefsLightRestore = E.normalizePreferences(ST.prefs).intensityLevel; ST.prefs.intensityLevel = 'LIGHT'; ST.prefsLightUntil = now + 7 * 86400; regenerate('light mode', true); }
      ST.ledger = Object.assign({}, ST.ledger, { consecutiveMisses: 0 }); ui.banner = null; persist(); render(); return applied();
    },
    /* the banner's ×, only for the banner the screen showed (the regroup question has none: it wants an answer). Nothing is saved:
       the page's own × saves nothing either. */
    dismissBanner({ text }) {
      if (!ui.banner || ui.banner.text !== String(text || '')) return { ok: true, changed: false, message: '' };
      if (ui.banner.regroup) return { ok: false, message: 'Choose one of the answers.' };
      ui.banner = null; render(); return { ok: true, changed: true, message: '' };
    },
  });
  function nativeTodayRun(name, payload) {
    if (!ST) boot();
    if (!Object.prototype.hasOwnProperty.call(Today, name)) return { ok: false, message: 'Life Director cannot do that.' };
    return Today[name](payload && typeof payload === 'object' ? payload : {});
  }
  /* Goals as data for a native screen (phase 3B): goalCards' decisions in the page's words; shares are 0..1 with the page's own
     rounded percentages beside them. "Add a goal" stays the page's sheet. */
  function nativeGoals() {
    if (!ST) boot();
    const pct = v => Math.round((v || 0) * 100);
    const cards = goalCards().map(c => ({
      id: c.g.id, title: c.g.title, sub: c.sub, paused: c.paused, priority: c.g.priority,
      share: c.a ? { target: Math.min(1, Math.max(0, c.a.targetShare || 0)), targetText: pct(c.a.targetShare) + '%', scheduled: Math.min(1, Math.max(0, c.a.actualShare || 0)), scheduledText: pct(c.a.actualShare) + '%', line: c.line } : null,
      warning: c.warning, clarify: c.clarify ? { text: CLARIFY_TEXT, options: c.clarify } : null, pause: c.pause, remove: 'Remove',
    }));
    return { explain: GOALS_EXPLAIN, priorities: [5, 4, 3, 2, 1].map(v => ({ value: v, label: PRIO[v] })), cards, empty: cards.length ? null : GOALS_EMPTY,
      add: 'Add a goal', removeConfirm: GOAL_REMOVE_CONFIRM };
  }
  /* Settings as data for a native screen (phase 3B). A range sends one label per stop, in the page's words, so its label follows
     the finger without the screen formatting time; rich text comes as spans (the rest tokens' bold). */
  function nativeSettings() {
    if (!ST) boot();
    const p = E.normalizePreferences(ST.prefs), T = SETTINGS_TEXT, L = ST.ledger;
    const stops = (lo, hi, step, f) => { const out = []; for (let v = lo; v <= hi; v += step) out.push(f(v)); return out; };
    const range = ([key, lo, hi, step, label, hint], f) => ({ key, label, hint: hint || '', min: lo, max: hi, step, value: p[key], stops: stops(lo, hi, step, f) });
    return {
      intensity: { title: T.intensity, value: p.intensityLevel, options: INTENSITY.options.map(([v, l]) => ({ value: v, label: l })), note: INTENSITY.note },
      ranges: PREF_RANGES.map(r => range(r, mins)),
      restDays: { title: T.restDays, note: T.restNote, days: DOW.map((d, k) => ({ day: k, label: d.slice(0, 2), on: p.preferredRestDays.includes(k) })) },
      horizon: { title: T.horizon, value: String(p.planningHorizonDays), options: HORIZON.map(([v, l]) => ({ value: String(v), label: l })) },
      misses: { title: T.misses, value: p.consequenceMode, options: MISSES.options.map(([v, l]) => ({ value: v, label: l })),
        text: [{ t: MISSES.text + 'Rest tokens: ', b: false }, { t: String(L.restTokens), b: true }, { t: ' of 3 (earn one every 5 completions).', b: false }],
        grace: range(GRACE, graceLabel) },
      guide: T.guide,
      data: Object.assign({}, T.data, { storageUnavailable: !!(loadReport && loadReport.source === 'MEMORY_ONLY') }),
      diagnostics: { title: T.diagnostics, line: diagnosticsLine() },
      lab: { label: T.lab, on: !!ST.ui.lab },
    };
  }
  /* My time as data for a native screen (phase 4B): timeModel's decisions, and what the mini sleep dial needs to follow the finger in
     the page's words: its snap (step, minutes), the page's clock text for every quarter hour (clock[k] is minute 15k), the length
     text for every quarter-hour length (lengths[k] is 15k minutes; 0 is a full day, as the dial draws it) and its work arcs */
  /* the dial face a native screen draws (Today's dial, My time's mini dial, the first-run card): the page's radii and its hour words
     in the person's clock style */
  const nativeHourWords = () => [0, 3, 6, 9, 12, 15, 18, 21].map(h => ({ h, word: hourWord(h) }));
  const nativeFace = () => ({ radii: DIAL, hourWords: nativeHourWords() });
  function nativeTime() {
    if (!ST) boot();
    const T = timeModel(), quarters = f => Array.from({ length: 1440 / MINI_SNAP }, (_, k) => f(k * MINI_SNAP));
    Object.assign(T.sleep, { step: MINI_SNAP, context: sleepContext(), clock: quarters(fHm), lengths: quarters(m => mins(m || 1440)), face: nativeFace() });
    T.sleep.labels.group = MINI_GROUP;
    return T;
  }
  /* the first-run card as data for a native screen: what the page draws while there is no plan (null once there is one) */
  function nativeFirstRun() {
    if (!ST) boot();
    if (ST.setupComplete && ST.schedule) return null;
    return { arcs: FIRST_RUN.arcs.map(a => Object.assign({}, a)), title: FIRST_RUN.title, text: FIRST_RUN.text, button: FIRST_RUN.button, section: FIRST_RUN.section, face: nativeFace() };
  }
  /* My time's sheets as data (phase 4B): the commitment sheet (id: a commitment's, or '' for a new one), the goal-kind grid, and a goal
     form (id: the kind). A commitment removed since answers gone; a kind of goal the page does not know has nothing to draw. */
  const FORM_SHEETS = new Set(['rule', 'goalType', 'goalForm']);
  function nativeForm(kind, id) {
    if (kind === 'goalType') return goalTypeModel();
    if (kind === 'goalForm') return E.ARCHETYPES[id] || id === 'CUSTOM' ? goalFormModel(id) : null;
    if (!id) return ruleForm(null);
    const r = ST.availability.rules.find(x => x.id === id && x.kind !== 'SLEEP' && x.kind !== 'PREFERRED');
    return r ? ruleForm(r) : { kind, id, gone: true };
  }
  /* a native screen's change: one of Do's, with its payload; an unknown name is refused */
  function nativeRun(name, payload) {
    if (!ST) boot();
    if (!Object.prototype.hasOwnProperty.call(Do, name)) return { ok: false, message: 'Life Director cannot do that.' };
    return Do[name](payload && typeof payload === 'object' ? payload : {});
  }
  /* A section's guide as data for a native screen that draws it (phase 3A: Week; 4A: Today; 4B: set-up, its mini-dial tags too): its steps as tourSteps keeps them (a step
     whose part is not on the page is left out), each with the name of the part it points at, and whether it is due to start
     by itself (not seen yet). Text comes as spans: the page's <b> is the only markup in TOURS. */
  const GUIDE_ANCHORS = { '.ld-week': 'week.rows', '.ld-stats': 'week.stats', '.ld-card-yield': 'week.yield',
    '.ld-dial': 'dial', '.ld-dial .ld-core': 'dial.centre', '.ld-up:not(.ld-up-rest)': 'upNext', '.ld-foot': 'footer',
    /* phase 4B, My time: the numbered steps, the mini sleep dial, and the commitments, routines, goals and build cards */
    '.ld-steps': 'time.steps', '.ld-mini': 'time.sleep', '.ld-card-commit': 'time.commitments', '.ld-card-routines': 'time.routines',
    '.ld-card-goals': 'time.goals', '.ld-build': 'time.build' };
  const GUIDE_LABELS = { skip: 'Skip guide', back: 'Back', next: 'Next', last: 'Got it', tap: 'Tap anywhere to continue' };
  const spans = html => String(html).split(/(<b>.*?<\/b>)/).filter(Boolean).map(t => /^<b>/.test(t) ? { t: t.slice(3, -4), b: true } : { t, b: false });
  function nativeGuide(key) {
    if (!ST) boot();
    if (!TOURS[key] || !root || !root.isConnected || ui.tab !== key || !tourCanRun(key)) return null;
    /* phase 4A (Today): how the dial looks on the step (the page's data-tour), the spotlight's shape as tourPlace cuts it, and the
       name tags tourLabels pins, read from the page's own dial underneath the screen */
    const host = st => root.querySelector(st.mode === 'mini' ? '.ld-mini' : '.ld-dial');
    const tags = st => tourTags(st, host(st)).map(l => ({ x: +l.x.toFixed(1), y: +l.y.toFixed(1), text: l.txt, kind: l.k }));
    const steps = tourSteps(key).map(st => ({ anchor: GUIDE_ANCHORS[st.sel] || '', title: st.title, text: spans(st.text), hint: !!st.hint,
      mode: st.mode || '', round: !!st.round, pad: st.pad != null ? st.pad : 8, tags: tags(st) }));
    if (!steps.length || steps.some(st => !st.anchor)) return null; // a step the screen cannot point at: the page keeps this guide
    return { key, auto: tourDue(key), steps, labels: Object.assign({}, GUIDE_LABELS, { last: key === 'setup' ? 'Let’s go' : 'Got it' }) };
  }
  /* The guide the ? button plays (helpKey), when a native screen can draw it there: the section on show's own when nativeGuide has it,
     Today's once there is a plan to show (its dial is then on Today), set-up's while set-up is not finished; else null. The screen
     switches to that section and plays it; with null it hands the ? over and the page plays it. */
  function nativeHelp() {
    if (!ST) boot();
    if (!root || !root.isConnected) return null;
    const key = helpKey();
    const can = key === ui.tab ? !!nativeGuide(key) : key === 'today' ? !!(ST.setupComplete && ST.schedule) : key === 'setup' && !ST.setupComplete;
    return can ? key : null;
  }
  /* the screen finished or skipped a guide it drew: seen, as the page's tourEnd records it, and the same words when skipped */
  function nativeTourSeen(key, finished) {
    if (!ST) boot();
    if (!TOURS[key]) return { ok: false, message: 'There is no such guide.' };
    ST.ui.tours = Object.assign({}, ST.ui.tours, { [key]: true });
    persist();
    if (!finished) say('Guide closed. Tap ? at the top to see it again.');
    changed();
    return { ok: true };
  }
  let nativeGuides = () => false;
  /* A native screen that draws Today plays the build itself: while covers() says it is over Today, the page plays none of its own,
     and cover() stops one already playing (the screen has just come over it) */
  let nativeCovers = () => false;
  const nativeApi = Object.freeze({ frame: nativeFrame, today: (i, o) => nativeToday(i === undefined ? ui.dayIdx : i, o), showing, act: nativeAct, drop: nativeDrop, apply: nativeApply,
    week: nativeWeek, quests: nativeQuests, section: nativeSection, day: i => nativeDay(i, false), gotoDay: i => nativeDay(i, true),
    dayIndex: () => ui.dayIdx,
    /* the player comes to Life Director: today on the strip, before the page shows it (the kit's arrive) */
    arrive() { ui.dayIdx = 0; intent = null; },
    /* fn() is true when a native screen draws Today's sheets: a glance tap then asks for its sheet (intent) instead */
    intendedBy(fn) { nativeIntends = typeof fn === 'function' ? fn : () => false; }, intent: nativeIntent,
    coveredBy(fn) { nativeCovers = typeof fn === 'function' ? fn : () => false; }, cover() { if (ui.bfRun) ui.bfRun.stop(); },
    /* fn(key) is true when a native screen draws that section's guide: the page then starts none of its own there */
    guidedBy(fn) { nativeGuides = typeof fn === 'function' ? fn : () => false; },
    guide: nativeGuide, help: nativeHelp, tourSeen: (key, finished) => nativeTourSeen(String(key || ''), !!finished),
    goals: nativeGoals, settings: nativeSettings, run: nativeRun, time: nativeTime, firstRun: nativeFirstRun,
    sheet: nativeSheet, todayRun: nativeTodayRun,
    glance: nativeGlance, fromGlance: nativeFromGlance, // the Overview's glance, for a native Overview
    onChange(fn) { watchers.add(fn); return () => watchers.delete(fn); } });
  /* ── Voice seam (LifeMax Voice, phase 1) ─────────────────────────────────────
     Everything the voice engine may do to Life Director: add a one-off timed commitment through the engine's own
     NEW_COMMITMENT repair (so the rest of the day is re-planned around it), find it again by id, remove it by id
     (the verified inverse used for Undo), and read what is already in the way. It never touches XP or quests. */
  const voiceApi = {
    ready() { if (!ST) boot(); return !!(ST && ST.setupComplete && ST.schedule); },
    timezone() { if (!ST) boot(); return tz(); },
    find(id) { if (!ST) boot(); return (ST.fixedCommitments || []).find(c => String(c.id) === String(id)) || null; },
    findMatch(title, startEpoch, endEpoch) {
      if (!ST) boot();
      const t = String(title || '').trim().toLowerCase();
      return (ST.fixedCommitments || []).find(c => c.voice && String(c.title || '').trim().toLowerCase() === t && c.startEpoch === startEpoch && c.endEpoch === endEpoch) || null;
    },
    add(c) {
      if (!voiceApi.ready()) return { ok: false, reason: 'NOT_SET_UP' };
      if (voiceApi.find(c.id)) return { ok: true, id: c.id, existing: true };
      const r = reschedule({ type: 'NEW_COMMITMENT', commitment: { id: c.id, title: c.title, startEpoch: c.startEpoch, endEpoch: c.endEpoch, commuteMin: 0, kind: 'APPOINTMENT', voice: true } });
      if (!r) return { ok: false, reason: 'PLAN_REJECTED' };
      persist(); try { if (root) render(); if (glanceEl) glance(glanceEl); } catch (e) { /* the page redraws on its next tick */ }
      return { ok: true, id: c.id };
    },
    remove(id) {
      if (!ST) boot();
      const n = (ST.fixedCommitments || []).length;
      ST.fixedCommitments = (ST.fixedCommitments || []).filter(c => String(c.id) !== String(id));
      if (ST.fixedCommitments.length === n) return false;
      regenerate('voice undo', true);
      try { if (root) render(); if (glanceEl) glance(glanceEl); } catch (e) { /* ignore */ }
      return true;
    },
    /* fixed things already occupying [startEpoch, endEpoch) in seconds: sleep, work, classes, regular and one-off commitments, blackouts */
    overlaps(startEpoch, endEpoch) {
      if (!ST) boot();
      const z = tz(), out = [], p0 = U.localParts(startEpoch, z);
      const days = [-1, 0, 1].map(k => U.ymdAdd({ y: p0.y, mo: p0.mo, d: p0.d }, k));
      const hit = (s, e) => s < endEpoch && e > startEpoch;
      for (const r of ST.availability.rules || []) {
        if (!DRAG_KINDS[r.kind]) continue;
        for (const ymd of days) {
          if (!ruleOn(r, ymd)) continue;
          const s = U.zonedToEpoch(ymd.y, ymd.mo, ymd.d, r.startMin, z), e = U.zonedToEpoch(ymd.y, ymd.mo, ymd.d, r.startMin + r.durationMin, z);
          if (hit(s, e)) out.push({ title: r.kind === 'SLEEP' ? 'Sleep' : (r.title || KIND_LABEL[r.kind] || r.kind), kind: r.kind, startEpoch: s, endEpoch: e });
        }
      }
      for (const c of ST.fixedCommitments || []) if (c.startEpoch && c.endEpoch && hit(c.startEpoch, c.endEpoch)) out.push({ title: c.title || 'Commitment', kind: c.kind || 'COMMITMENT', startEpoch: c.startEpoch, endEpoch: c.endEpoch });
      for (const b of ST.availability.blackouts || []) if (b && b.startEpoch && b.endEpoch && hit(b.startEpoch, b.endEpoch)) out.push({ title: b.reason || b.title || 'Unavailable', kind: 'BLACKOUT', startEpoch: b.startEpoch, endEpoch: b.endEpoch });
      return out.sort((a, b) => a.startEpoch - b.startEpoch);
    },
    /* the work hours Life Director knows for a local date ("YYYY-MM-DD"), or null: never guessed */
    workOn(dateKey) {
      if (!ST) boot();
      const [y, mo, d] = String(dateKey).split('-').map(Number), z = tz(), ymd = { y, mo, d };
      let best = null;
      for (const r of ST.availability.rules || []) {
        if (r.kind !== 'WORK' || !ruleOn(r, ymd)) continue;
        const w = { title: r.title || 'Work', startMin: r.startMin, endMin: r.startMin + r.durationMin };
        if (!best || w.endMin > best.endMin) best = w;
      }
      for (const c of ST.fixedCommitments || []) {
        if ((c.kind || 'WORK') !== 'WORK' || !c.startEpoch) continue;
        const p = U.localParts(c.startEpoch, z);
        if (p.y === y && p.mo === mo && p.d === d) {
          const w = { title: c.title || 'Work', startMin: p.h * 60 + p.mi, endMin: p.h * 60 + p.mi + Math.round((c.endEpoch - c.startEpoch) / 60) };
          if (!best || w.endMin > best.endMin) best = w;
        }
      }
      return best;
    },
    /* planned sessions on a local date, with the ids the actions below take */
    sessions(dateKey) {
      if (!voiceApi.ready()) return [];
      const [y, mo, d] = String(dateKey).split('-').map(Number), z = tz();
      const s0 = U.zonedToEpoch(y, mo, d, 0, z), s1 = U.zonedToEpoch(y, mo, d, 1440, z);
      return (ST.schedule.scheduledInstances || []).filter(i => i.lifecycleState === 'PLANNED' && i.startEpoch < s1 && i.endEpoch > s0)
        .map(i => ({ id: i.instanceId, title: i.title, startEpoch: i.startEpoch, endEpoch: i.endEpoch, kind: 'SESSION' })).sort((a, b) => a.startEpoch - b.startEpoch);
    },
    commitments(dateKey) {
      if (!voiceApi.ready()) return [];
      const [y, mo, d] = String(dateKey).split('-').map(Number), z = tz();
      const s0 = U.zonedToEpoch(y, mo, d, 0, z), s1 = U.zonedToEpoch(y, mo, d, 1440, z);
      return (ST.fixedCommitments || []).filter(c => c.startEpoch && c.endEpoch && c.startEpoch < s1 && c.endEpoch > s0)
        .map(c => ({ id: String(c.id), title: c.title || 'Commitment', startEpoch: c.startEpoch, endEpoch: c.endEpoch, kind: 'COMMITMENT', voice: !!c.voice })).sort((a, b) => a.startEpoch - b.startEpoch);
    },
    now() { return nowEpoch(); },
    /* the same completion Life Director's own button uses, so XP, quests and streak hooks all fire */
    completeSession(id) {
      if (!voiceApi.ready()) return { ok: false, reason: 'NOT_SET_UP' };
      const i = findInst(id); if (!i) return { ok: false, reason: 'NOT_FOUND' };
      if (!doable(i, nowEpoch())) return { ok: false, reason: 'NOT_STARTED' };
      const r = finishInstance(i, i.durationSlots * 15);
      try { render(); } catch (e) { /* ignore */ }
      return { ok: !!r, reason: r ? null : 'REJECTED' };
    },
    skipSession(id) {
      if (!voiceApi.ready()) return { ok: false, reason: 'NOT_SET_UP' };
      const i = findInst(id); if (!i) return { ok: false, reason: 'NOT_FOUND' };
      const c = E.applyMissConsequence(ST.ledger, i, ST.prefs, nowEpoch(), 'SKIP'); ST.ledger = c.ledger;
      const r = reschedule({ type: 'SKIPPED', instanceId: id }); persist();
      try { render(); if (glanceEl) glance(glanceEl); } catch (e) { /* ignore */ }
      return { ok: true, replanned: !!r };
    },
    moveSession(id, newStartEpoch) {
      if (!voiceApi.ready()) return { ok: false, reason: 'NOT_SET_UP' };
      const r = reschedule({ type: 'MOVE', instanceId: id, newStartEpoch: +newStartEpoch });
      if (!r) return { ok: false, reason: 'PLAN_REJECTED' };
      persist(); try { render(); if (glanceEl) glance(glanceEl); } catch (e) { /* ignore */ }
      return { ok: true };
    },
    moveCommitment(id, startEpoch, endEpoch) {
      if (!voiceApi.ready()) return { ok: false, reason: 'NOT_SET_UP' };
      const c = (ST.fixedCommitments || []).find(x => String(x.id) === String(id)); if (!c) return { ok: false, reason: 'NOT_FOUND' };
      c.startEpoch = +startEpoch; c.endEpoch = +endEpoch;
      regenerate('voice move', true);
      try { render(); if (glanceEl) glance(glanceEl); } catch (e) { /* ignore */ }
      return { ok: true };
    },
    /* read-only: what is planned on a local date (fixed items and planned sessions), for life_director.query_schedule */
    dayItems(dateKey) {
      if (!voiceApi.ready()) return null;
      const [y, mo, d] = String(dateKey).split('-').map(Number), z = tz();
      const s0 = U.zonedToEpoch(y, mo, d, 0, z), s1 = U.zonedToEpoch(y, mo, d, 1440, z);
      const items = voiceApi.overlaps(s0, s1).filter(x => x.kind !== 'SLEEP');
      for (const i of ST.schedule.scheduledInstances || []) if (i.lifecycleState === 'PLANNED' && i.startEpoch < s1 && i.endEpoch > s0) items.push({ title: i.title, kind: 'SESSION', startEpoch: i.startEpoch, endEpoch: i.endEpoch });
      return items.sort((a, b) => a.startEpoch - b.startEpoch);
    }
  };
  /* read-only numbers for the Journal coach: minutes of sessions scheduled on a local date and how many were done */
  const coachApi = {
    dayStats(dateKey) {
      if (!voiceApi.ready()) return null;
      const [y, mo, d] = String(dateKey).split('-').map(Number), z = tz();
      const s0 = U.zonedToEpoch(y, mo, d, 0, z), s1 = U.zonedToEpoch(y, mo, d, 1440, z);
      let sched = 0, done = 0, n = 0, doneN = 0;
      const seen = new Set();
      for (const i of (ST.schedule.scheduledInstances || []).concat(ST.schedule.historyInstances || [])) {
        if (!i || seen.has(i.instanceId) || !(i.startEpoch >= s0 && i.startEpoch < s1)) continue;
        if (/EXCUSED|CANCELLED/.test(String(i.lifecycleState))) continue;
        seen.add(i.instanceId);
        const m = (i.durationSlots || 0) * 15, st = String(i.lifecycleState);
        sched += m; n++;
        if (/COMPLETED/.test(st) && !/PARTIAL/.test(st)) { done += m; doneN++; } else if (/PARTIAL/.test(st)) done += Math.round(m / 2);
      }
      return { schedMin: sched, doneMin: done, sessions: n, doneSessions: doneN };
    }
  };
  return { mount, glance, widgetSnapshot, voice: voiceApi, native: nativeApi, coach: coachApi, _debug: () => ({ ST, ui }), regenerate: r => { regenerate(r || 'api'); render(); }, getSchedule: () => ST && ST.schedule, workRules, shifts, FEATURE_ID };
})();

/* Public API for LifeMax or other features */
window.LifeDirector = Object.freeze({
  version: LifeDirectorEngine.ENGINE_VERSION, engine: LifeDirectorEngine,
  getSchedule: () => LifeDirectorApp.getSchedule(), regenerate: r => LifeDirectorApp.regenerate(r),
  glance: (el, opts) => LifeDirectorApp.glance(el, opts),
  widgetSnapshot: size => LifeDirectorApp.widgetSnapshot(size),
  workRules: () => LifeDirectorApp.workRules(), shifts: (a, b) => LifeDirectorApp.shifts(a, b),
  voice: LifeDirectorApp.voice,
  native: LifeDirectorApp.native,
  coach: LifeDirectorApp.coach
});
/* the Overview was drawn before this feature loaded: put today's dial on it now */
try { const g = document.getElementById('ovPlan'); if (g) LifeDirectorApp.glance(g); } catch (e) { /* the Overview will try again on its next draw */ }
if (window.LifeMaxFeatures && typeof window.LifeMaxFeatures.register === 'function') {
  LifeMaxFeatures.register({ id: 'life-director', mount: el => LifeDirectorApp.mount(el) });
} else {
  /* Standalone harness: no Forge registry, mount directly */
  document.addEventListener('DOMContentLoaded', () => { const v = document.getElementById('view-life-director'); if (v) LifeDirectorApp.mount(v); });
}


