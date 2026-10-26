/* Life Director 1.4.0 — Forge feature for LifeMax RPG. Engine + store + host adapter unchanged from 1.3; interface rebuilt around the chrono dial. */
/*! Life Director Engine v1.0.0 — LifeMax RPG
 *  Offline-first, deterministic, constraint-guided scheduler.
 *  Pure module: no DOM, no storage, no clock reads, no randomness in planning paths.
 *  Implements Gemini's Life Director spec with the corrections listed in IMPLEMENTATION_NOTES.md. */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.LifeDirectorEngine = api;
})(typeof self !== 'undefined' ? self : this, function () {
'use strict';
/* ── Life Director Engine · Part 1: utilities, time, availability ───────────
   Epoch unit: SECONDS everywhere in the engine (spec §13). Slots are 15 min.
   The engine is pure: no DOM, no storage, no Date.now(), no Math.random().
   ------------------------------------------------------------------------- */
const SLOT_SEC = 900;
const SLOTS_PER_HOUR = 4;
const ENGINE_VERSION = '1.0.0';
const SCHEMA_VERSION = 1;

/* Slot codes, ordered by the precedence of spec §3.2 (lower = stronger). */
const SLOT = Object.freeze({
  FREE: 0, EMERGENCY: 1, HARD: 2, COMMUTE: 3, SLEEP: 4, MAINT: 5,
  LOCKED: 6, RECOVERY: 7, PERSONAL: 8, TASK: 9
});
const SLOT_NAMES = ['FREE', 'UNAVAILABLE_EMERGENCY', 'HARD_COMMITMENT', 'COMMUTE_AND_TRANSITION',
  'PROTECTED_SLEEP', 'ESSENTIAL_MAINTENANCE', 'USER_LOCKED_ACTIVITY', 'RECOVERY_PROTECTED',
  'PERSONAL_UNAVAILABLE', 'SCHEDULED_TASK'];
/* Shift load classes (spec §8.2 table). */
const LOAD = { NONE: 0, PHYSICAL_HEAVY: 1, COGNITIVE_HEAVY: 2, MIXED_MODERATE: 3, LIGHT: 4 };
const LOAD_DRAIN = { 1: [0.055, 0.020], 2: [0.015, 0.050], 3: [0.035, 0.030], 4: [0.010, 0.010] };

/* ── deterministic helpers ── */
function cyrb53(str, seed) {
  let h1 = 0xdeadbeef ^ seed, h2 = 0x41c6ce57 ^ seed;
  for (let i = 0; i < str.length; i++) {
    const ch = str.charCodeAt(i);
    h1 = Math.imul(h1 ^ ch, 2654435761);
    h2 = Math.imul(h2 ^ ch, 1597334677);
  }
  h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
  h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
  return 4294967296 * (2097151 & h2) + (h1 >>> 0);
}
/* ~106-bit stable id. Not cryptographic; used for idempotency keys (see NOTES §D6). */
function stableId(prefix, str) {
  return prefix + '_' + cyrb53(str, 1).toString(36).padStart(11, '0') + cyrb53(str, 7).toString(36).padStart(11, '0');
}
function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a = (a + 0x6D2B79F5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const clamp = (x, lo, hi) => (x < lo ? lo : x > hi ? hi : x);
const byId = (a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0);
const cmpStr = (a, b) => (a < b ? -1 : a > b ? 1 : 0);
function deepClone(x) { return x == null ? x : JSON.parse(JSON.stringify(x)); }
function ceilTo15(min) { return Math.max(15, Math.ceil(min / 15) * 15); }

/* ── timezone math (Intl-based; correct across DST) ── */
const _fmtCache = new Map();
const WD = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 };
function _fmt(tz) {
  let f = _fmtCache.get(tz);
  if (!f) {
    f = new Intl.DateTimeFormat('en-US', {
      timeZone: tz, hourCycle: 'h23', year: 'numeric', month: 'numeric', day: 'numeric',
      hour: 'numeric', minute: 'numeric', second: 'numeric', weekday: 'short'
    });
    _fmtCache.set(tz, f);
  }
  return f;
}
function isValidTimeZone(tz) {
  try { _fmt(tz).format(new Date(0)); return true; } catch (e) { return false; }
}
const _lpCache = new Map();
function localParts(epochSec, tz) {
  const ck = tz + '|' + epochSec;
  const hit = _lpCache.get(ck);
  if (hit) return hit;
  if (_lpCache.size > 50000) _lpCache.clear();
  const r = _localParts(epochSec, tz);
  _lpCache.set(ck, r);
  return r;
}
function _localParts(epochSec, tz) {
  const p = _fmt(tz).formatToParts(new Date(epochSec * 1000));
  const o = {};
  for (const x of p) o[x.type] = x.value;
  let h = +o.hour; if (h === 24) h = 0;
  return { y: +o.year, mo: +o.month, d: +o.day, h, mi: +o.minute, s: +o.second, dow: WD[o.weekday] };
}
function tzOffsetSec(epochSec, tz) {
  const p = localParts(epochSec, tz);
  return Date.UTC(p.y, p.mo - 1, p.d, p.h, p.mi, p.s) / 1000 - epochSec;
}
/* Wall-clock → epoch. Minutes may exceed 1440 (overnight rules roll to the next date).
   Non-existent local times (spring-forward gap) resolve to the instant after the gap;
   ambiguous times (fall-back) resolve to the first occurrence. */
const _zCache = new Map();
function zonedToEpoch(y, mo, d, minuteOfDay, tz) {
  const ck = tz + '|' + y + '|' + mo + '|' + d + '|' + minuteOfDay;
  let v = _zCache.get(ck);
  if (v === undefined) { if (_zCache.size > 20000) _zCache.clear(); v = _zonedToEpoch(y, mo, d, minuteOfDay, tz); _zCache.set(ck, v); }
  return v;
}
function _zonedToEpoch(y, mo, d, minuteOfDay, tz) {
  const g = Date.UTC(y, mo - 1, d, 0, minuteOfDay) / 1000;
  const want = new Date(g * 1000);
  const wy = want.getUTCFullYear(), wmo = want.getUTCMonth() + 1, wd = want.getUTCDate(),
    wh = want.getUTCHours(), wmi = want.getUTCMinutes();
  const c1 = g - tzOffsetSec(g, tz);
  const c2 = g - tzOffsetSec(c1, tz);
  const c3 = g - tzOffsetSec(c2, tz);
  const cands = [...new Set([c1, c2, c3])];
  const ok = cands.filter(c => { const p = localParts(c, tz); return p.y === wy && p.mo === wmo && p.d === wd && p.h === wh && p.mi === wmi; });
  if (ok.length) return Math.min(...ok);
  // gap: choose the earliest candidate whose local time is after the wanted wall time
  const after = cands.filter(c => { const p = localParts(c, tz); return Date.UTC(p.y, p.mo - 1, p.d, p.h, p.mi) / 1000 > g; });
  return after.length ? Math.min(...after) : Math.max(...cands);
}
function ymdAdd(ymd, n) {
  const t = new Date(Date.UTC(ymd.y, ymd.mo - 1, ymd.d + n));
  return { y: t.getUTCFullYear(), mo: t.getUTCMonth() + 1, d: t.getUTCDate(), dow: t.getUTCDay() };
}
function ymdKey(ymd) { return ymd.y + '-' + String(ymd.mo).padStart(2, '0') + '-' + String(ymd.d).padStart(2, '0'); }
function parseYmd(key) { const [y, mo, d] = key.split('-').map(Number); return ymdAdd({ y, mo, d }, 0); }
function daysBetween(a, b) { return Math.round((Date.UTC(b.y, b.mo - 1, b.d) - Date.UTC(a.y, a.mo - 1, a.d)) / 86400000); }

/* ── horizon & grid ── */
function buildHorizon(startEpoch, days, tz) {
  const p = localParts(startEpoch, tz);
  let ymd = ymdAdd({ y: p.y, mo: p.mo, d: p.d }, 0);
  const out = [];
  for (let i = 0; i < days; i++) {
    const s = zonedToEpoch(ymd.y, ymd.mo, ymd.d, 0, tz);
    const nx = ymdAdd(ymd, 1);
    const e = zonedToEpoch(nx.y, nx.mo, nx.d, 0, tz);
    out.push({ index: i, ymd, key: ymdKey(ymd), dow: ymd.dow, startEpoch: s, endEpoch: e, slots: (e - s) / SLOT_SEC });
    ymd = nx;
  }
  return out;
}

function createGrid(days, tz) {
  const H0 = days[0].startEpoch, HN = days[days.length - 1].endEpoch;
  const n = (HN - H0) / SLOT_SEC;
  const g = {
    H0, HN, n, tz, days,
    type: new Uint8Array(n),
    load: new Uint8Array(n),        // shift load class for HARD slots
    tag: new Int32Array(n).fill(-1),// index into g.blocks (what painted it)
    taskOwner: new Int32Array(n).fill(-1),
    taskIP: new Uint8Array(n), taskIC: new Uint8Array(n),
    bwcOf: new Int16Array(n).fill(-1),
    localMin: new Int16Array(n), dayOf: new Int16Array(n),
    baseP: new Float32Array(n), baseC: new Float32Array(n),
    drainP: new Float32Array(n), drainC: new Float32Array(n),
    runLen: new Int16Array(n), runBack: new Int16Array(n),
    blocks: []
  };
  for (const day of days) {
    const i0 = (day.startEpoch - H0) / SLOT_SEC;
    const o0 = tzOffsetSec(day.startEpoch, tz), o1 = tzOffsetSec(day.endEpoch - 1, tz);
    for (let k = 0; k < day.slots; k++) {
      const i = i0 + k;
      g.dayOf[i] = day.index;
      if (o0 === o1) g.localMin[i] = k * 15;
      else { const lp = localParts(H0 + i * SLOT_SEC, tz); g.localMin[i] = lp.h * 60 + lp.mi; }
    }
  }
  return g;
}
const slotOf = (g, epoch) => Math.floor((epoch - g.H0) / SLOT_SEC);
const slotCeil = (g, epoch) => Math.ceil((epoch - g.H0) / SLOT_SEC);
const epochOf = (g, slot) => g.H0 + slot * SLOT_SEC;

function paint(g, startEpoch, endEpoch, code, meta) {
  const a = clamp(slotOf(g, startEpoch), 0, g.n), b = clamp(slotCeil(g, endEpoch), 0, g.n);
  if (b <= a) return null;
  const bi = g.blocks.length;
  g.blocks.push(Object.assign({ code, startEpoch: epochOf(g, a), endEpoch: epochOf(g, b) }, meta || {}));
  let wrote = 0;
  for (let i = a; i < b; i++) {
    const cur = g.type[i];
    if (cur === SLOT.FREE || code < cur) {
      g.type[i] = code; g.tag[i] = bi; wrote++;
      if (code === SLOT.HARD && meta && meta.load) g.load[i] = meta.load;
    }
  }
  return wrote ? bi : null;
}

/* ── availability normalisation ── */
const RULE_KINDS = ['WORK', 'CLASS', 'APPOINTMENT', 'UNAVAILABLE', 'SLEEP', 'PREFERRED'];
function normalizeRule(r, idx) {
  const kindMap = { HARD_COMMITMENT: 'WORK', SLEEP_WINDOW: 'SLEEP', UNAVAILABLE: 'UNAVAILABLE', PREFFERED_WINDOW: 'PREFERRED', PREFERRED_WINDOW: 'PREFERRED' };
  const kind = RULE_KINDS.includes(r.kind) ? r.kind : (kindMap[r.ruleType] || 'WORK');
  const days = Array.isArray(r.days) ? r.days.slice() : (r.dayOfWeek != null ? [r.dayOfWeek] : [0, 1, 2, 3, 4, 5, 6]);
  return {
    id: String(r.id || r.ruleId || ('rule' + idx)), kind, title: r.title || kind,
    days: days.filter(d => d >= 0 && d <= 6).sort(),
    startMin: clamp(Math.round(r.startMin != null ? r.startMin : (r.startMinuteOfDay || 0)), 0, 1439),
    durationMin: clamp(Math.round(r.durationMin != null ? r.durationMin : (r.durationMinutes || 60)), 15, 1440),
    commuteMin: clamp(Math.round(r.commuteMin || 0), 0, 240),
    decompressMin: r.decompressMin != null ? clamp(r.decompressMin, 0, 120) : null,
    load: r.load || r.shiftIntensity || 'MIXED_MODERATE',
    rotation: r.rotation && r.rotation.anchorDate && r.rotation.cycleLength > 0 ? {
      anchorDate: r.rotation.anchorDate, cycleLength: Math.round(r.rotation.cycleLength),
      activeDays: (r.rotation.activeDays || []).map(Number)
    } : null,
    validFrom: r.validFrom || null, validTo: r.validTo || null
  };
}
function ruleAppliesOn(rule, ymd) {
  const key = ymdKey(ymd);
  if (rule.validFrom && key < rule.validFrom) return false;
  if (rule.validTo && key > rule.validTo) return false;
  if (rule.rotation) {
    const idx = ((daysBetween(parseYmd(rule.rotation.anchorDate), ymd) % rule.rotation.cycleLength) + rule.rotation.cycleLength) % rule.rotation.cycleLength;
    return rule.rotation.activeDays.includes(idx);
  }
  return rule.days.includes(ymd.dow);
}
function materializeRule(rule, days, tz) {
  const out = [];
  const first = ymdAdd(days[0].ymd, -1);
  for (let i = 0; i < days.length + 2; i++) {
    const ymd = ymdAdd(first, i);
    if (!ruleAppliesOn(rule, ymd)) continue;
    const s = zonedToEpoch(ymd.y, ymd.mo, ymd.d, rule.startMin, tz);
    const e = zonedToEpoch(ymd.y, ymd.mo, ymd.d, rule.startMin + rule.durationMin, tz);
    out.push({ ruleId: rule.id, title: rule.title, kind: rule.kind, startEpoch: s, endEpoch: e, ymd, rule });
  }
  return out;
}

function defaultPreferences() {
  return {
    intensityLevel: 'BALANCED', maxDailyGoalMinutes: 180, minDailyRecoveryMinutes: 90,
    maxConsecutiveHighLoadMinutes: 90, minSleepMinutes: 450, consequenceMode: 'LENIENT',
    preferredRestDays: [], planningHorizonDays: 7, missGraceMinutes: 60,
    maxHighIntensityPerDay: 3, morningRoutineMinutes: 30, windDownMinutes: 30,
    mealMinutes: 30, mealOffsetsMinutes: [60, 330, 630], mealsEnabled: true,
    defaultBedtimeMin: 23 * 60, avoidWindows: [], maxFreeMovesPerTask: 3,
    leadMinutes: 15, partialSatisfiedRatio: 0.75
  };
}
function normalizePreferences(p) {
  const d = defaultPreferences();
  const o = Object.assign({}, d, p || {});
  o.maxDailyGoalMinutes = clamp(+o.maxDailyGoalMinutes || d.maxDailyGoalMinutes, 30, 480);
  o.minDailyRecoveryMinutes = clamp(+o.minDailyRecoveryMinutes || d.minDailyRecoveryMinutes, 30, 360);
  o.maxConsecutiveHighLoadMinutes = clamp(+o.maxConsecutiveHighLoadMinutes || d.maxConsecutiveHighLoadMinutes, 30, 240);
  o.minSleepMinutes = clamp(+o.minSleepMinutes || d.minSleepMinutes, 300, 720);
  o.planningHorizonDays = clamp(Math.round(+o.planningHorizonDays || 7), 1, 28);
  if (!['LIGHT', 'BALANCED', 'AGGRESSIVE', 'HARDCORE'].includes(o.intensityLevel)) o.intensityLevel = 'BALANCED';
  if (!['LENIENT', 'MODERATE', 'HARDCORE_RPG'].includes(o.consequenceMode)) o.consequenceMode = 'LENIENT';
  if (o.intensityLevel === 'HARDCORE') o.maxDailyGoalMinutes = Math.min(480, Math.max(o.maxDailyGoalMinutes, 240));
  // Edge case §12-2: abundant time still gets a volume ceiling.
  o.maxDailyGoalMinutes = Math.min(o.maxDailyGoalMinutes, o.intensityLevel === 'HARDCORE' ? 480 : 300);
  return o;
}

/* Sleep intervals: explicit SLEEP rules, else a default bedtime anchored so the player
   wakes early enough for the next day's first commitment (spec §3.4 "Sleep Placement"). */
function deriveSleep(rules, days, tz, prefs, sleepReqMin, hardIntervals) {
  const explicit = rules.filter(r => r.kind === 'SLEEP');
  let raw = [];
  for (const r of explicit) raw.push(...materializeRule(r, days, tz));
  const warnings = [];
  if (!explicit.length) {
    const first = ymdAdd(days[0].ymd, -1);
    for (let i = 0; i < days.length + 2; i++) {
      const ymd = ymdAdd(first, i);
      let s = zonedToEpoch(ymd.y, ymd.mo, ymd.d, prefs.defaultBedtimeMin, tz);
      let e = s + sleepReqMin * 60;
      const lead = (prefs.morningRoutineMinutes + 15) * 60;
      const conflict = hardIntervals.filter(h => h.startEpoch < e + lead && h.startEpoch >= s).sort((a, b) => a.startEpoch - b.startEpoch)[0];
      if (conflict) {
        const wake = conflict.startEpoch - (conflict.commuteMin || 0) * 60 - lead;
        e = Math.max(s + 60 * 60, wake); s = e - sleepReqMin * 60;
        warnings.push({ code: 'SLEEP_SHIFTED', date: ymdKey(ymd), message: 'Bedtime moved earlier so you can wake in time for ' + conflict.title + '.' });
      }
      raw.push({ ruleId: 'default-sleep', title: 'Sleep', kind: 'SLEEP', startEpoch: s, endEpoch: e, ymd });
    }
  }
  raw.sort((a, b) => a.startEpoch - b.startEpoch);
  const merged = [];
  for (const x of raw) {
    const last = merged[merged.length - 1];
    if (last && x.startEpoch <= last.endEpoch + 30 * 60) last.endEpoch = Math.max(last.endEpoch, x.endEpoch);
    else merged.push(Object.assign({}, x));
  }
  for (const m of merged) if (m.endEpoch - m.startEpoch < prefs.minSleepMinutes * 60 && m.endEpoch - m.startEpoch >= 3 * 3600)
    warnings.push({ code: 'SLEEP_BELOW_FLOOR', startEpoch: m.startEpoch, message: 'A sleep window is shorter than your ' + (prefs.minSleepMinutes / 60) + 'h minimum. Life Director will never schedule into sleep, but cannot add sleep you have not entered.' });
  return { intervals: merged, warnings };
}

/* deriveAvailability — spec §3.4 + §14 #1. Returns the painted grid and wake cycles. */
function deriveAvailability(params) {
  const tz = params.timezone || (params.playerAvailability && params.playerAvailability.timezone) || 'UTC';
  if (!isValidTimeZone(tz)) throw new Error('Invalid IANA timezone: ' + tz);
  const prefs = normalizePreferences(params.playerPreferences);
  const av = params.playerAvailability || {};
  const days = buildHorizon(params.horizonStartEpoch, params.horizonDays || prefs.planningHorizonDays, tz);
  const g = createGrid(days, tz);
  const warnings = [];
  const rules = (av.rules || av.weeklyRecurringRules || []).map(normalizeRule).sort((a, b) => cmpStr(a.id, b.id));

  /* 1. hard rasterisation */
  const hard = [];
  for (const r of rules) {
    if (r.kind === 'SLEEP' || r.kind === 'PREFERRED') continue;
    for (const iv of materializeRule(r, days, tz)) hard.push(Object.assign(iv, { commuteMin: r.commuteMin, load: r.load, decompressMin: r.decompressMin, source: 'rule' }));
  }
  for (const c of (params.fixedCommitments || []).slice().sort((a, b) => a.startEpoch - b.startEpoch || cmpStr(String(a.id || a.commitmentId), String(b.id || b.commitmentId)))) {
    const kind = c.commitmentType === 'EMERGENCY' || c.kind === 'EMERGENCY' ? 'EMERGENCY' : (c.kind || (c.commitmentType === 'CLASS' ? 'CLASS' : 'WORK'));
    hard.push({ ruleId: String(c.id || c.commitmentId), title: c.title || 'Commitment', kind, startEpoch: c.startEpoch, endEpoch: c.endEpoch,
      commuteMin: c.commuteMin || 0, load: c.shiftIntensity || c.load || 'MIXED_MODERATE', decompressMin: c.decompressMin != null ? c.decompressMin : null, source: 'oneoff' });
  }
  for (const b of (av.blackouts || av.oneTimeBlackouts || [])) {
    hard.push({ ruleId: String(b.id || b.blackoutId), title: b.reason || b.title || 'Unavailable', kind: b.kind === 'SICK' ? 'SICK' : 'BLACKOUT', startEpoch: b.startEpoch, endEpoch: b.endEpoch, commuteMin: 0, source: 'blackout' });
  }
  const hardTimed = hard.filter(h => h.endEpoch > g.H0 - 86400 && h.startEpoch < g.HN + 86400);
  for (const h of hardTimed) {
    if (h.kind === 'BLACKOUT' || h.kind === 'SICK' || h.kind === 'EMERGENCY') paint(g, h.startEpoch, h.endEpoch, SLOT.EMERGENCY, { kind: h.kind, title: h.title, refId: h.ruleId });
    else if (h.kind === 'UNAVAILABLE') paint(g, h.startEpoch, h.endEpoch, SLOT.PERSONAL, { kind: 'UNAVAILABLE', title: h.title, refId: h.ruleId });
    else paint(g, h.startEpoch, h.endEpoch, SLOT.HARD, { kind: h.kind, title: h.title, refId: h.ruleId, load: LOAD[h.load] || LOAD.MIXED_MODERATE });
  }
  /* 2. buffer inflation: commute before/after + post-shift decompression */
  for (const h of hardTimed) {
    if (!['WORK', 'CLASS', 'APPOINTMENT'].includes(h.kind)) continue;
    const cm = (h.commuteMin || 0) * 60;
    if (cm) {
      paint(g, h.startEpoch - cm, h.startEpoch, SLOT.COMMUTE, { kind: 'COMMUTE', title: 'Commute', refId: h.ruleId });
      paint(g, h.endEpoch, h.endEpoch + cm, SLOT.COMMUTE, { kind: 'COMMUTE', title: 'Commute home', refId: h.ruleId });
    }
    const isShift = h.kind === 'WORK' && (h.endEpoch - h.startEpoch) >= 3 * 3600;
    const dec = h.decompressMin != null ? h.decompressMin : (isShift && h.load !== 'LIGHT' ? 30 : 0);
    if (dec) paint(g, h.endEpoch + cm, h.endEpoch + cm + dec * 60, SLOT.COMMUTE, { kind: 'DECOMPRESS', title: 'Decompress', refId: h.ruleId, restorative: true });
  }
  /* 3. sleep */
  const sleepRes = deriveSleep(rules, days, tz, prefs, av.sleepRequirementMinutes || 480, hardTimed.filter(h => ['WORK', 'CLASS', 'APPOINTMENT'].includes(h.kind)));
  warnings.push(...sleepRes.warnings);
  for (const sl of sleepRes.intervals) paint(g, sl.startEpoch, sl.endEpoch, SLOT.SLEEP, { kind: 'SLEEP', title: 'Sleep', protected: true });
  for (const h of hardTimed) {
    if (h.kind === 'WORK' || h.kind === 'CLASS') for (const sl of sleepRes.intervals)
      if (h.startEpoch < sl.endEpoch && h.endEpoch > sl.startEpoch && h.endEpoch > g.H0 && h.startEpoch < g.HN)
        warnings.push({ code: 'SLEEP_COMMITMENT_OVERLAP', startEpoch: h.startEpoch, message: h.title + ' overlaps your sleep window. Commitments win; please check your sleep times.' });
  }

  /* 4. wake cycles from main sleep blocks (≥3h; shorter blocks are naps) */
  const mains = sleepRes.intervals.filter(s => s.endEpoch - s.startEpoch >= 3 * 3600);
  const wakeCycles = [];
  for (let i = 0; i < mains.length; i++) {
    const wake = mains[i].endEpoch;
    const sleep = i + 1 < mains.length ? mains[i + 1].startEpoch : wake + 16 * 3600;
    if (sleep <= g.H0 || wake >= g.HN) continue;
    const a = clamp(slotCeil(g, wake), 0, g.n), b = clamp(slotOf(g, sleep), 0, g.n);
    if (b - a < 2) continue;
    const lp = localParts(wake, tz);
    const bw = {
      index: wakeCycles.length, id: 'bwc-' + wake, wakeEpoch: wake, sleepEpoch: sleep, startSlot: a, endSlot: b,
      sleepBeforeMin: (mains[i].endEpoch - mains[i].startEpoch) / 60,
      dateKey: ymdKey(lp), dow: lp.dow, label: null, truncatedStart: wake < g.H0,
      heavyShiftEndSlot: -1, goalMin: 0, highCount: 0, perKey: {}, recoverySlots: 0, freeSlots: 0, maintBlocks: []
    };
    wakeCycles.push(bw);
    for (let k = a; k < b; k++) g.bwcOf[k] = bw.index;
  }
  /* 5. maintenance: routine, meals, wind-down — relative to wake (spec §3.4) */
  for (const bw of wakeCycles) {
    if (bw.truncatedStart) continue;
    paintMaintenance(g, bw, bw.wakeEpoch, bw.wakeEpoch + prefs.morningRoutineMinutes * 60, 'Morning routine', 'ROUTINE');
    if (prefs.mealsEnabled) {
      const names = ['Breakfast', 'Lunch', 'Dinner'];
      prefs.mealOffsetsMinutes.forEach((off, k) => {
        const s = bw.wakeEpoch + off * 60;
        if (s >= bw.sleepEpoch - prefs.windDownMinutes * 60) return;
        placeMeal(g, bw, s, prefs.mealMinutes, names[k] || 'Meal');
      });
    }
    paintMaintenance(g, bw, bw.sleepEpoch - prefs.windDownMinutes * 60, bw.sleepEpoch, 'Wind-down', 'WIND_DOWN');
  }
  /* heavy-shift markers for post-shift protection (spec §8.3) */
  for (const h of hardTimed) {
    if (h.kind !== 'WORK' || h.load !== 'PHYSICAL_HEAVY' || h.endEpoch - h.startEpoch < 6 * 3600) continue;
    const endSlot = clamp(slotCeil(g, h.endEpoch), 0, g.n);
    for (const bw of wakeCycles) if (h.endEpoch > bw.wakeEpoch && h.endEpoch <= bw.sleepEpoch) bw.heavyShiftEndSlot = Math.max(bw.heavyShiftEndSlot, endSlot);
  }
  /* preferred windows */
  const preferredMask = new Uint8Array(g.n);
  for (const r of rules) if (r.kind === 'PREFERRED') for (const iv of materializeRule(r, days, tz)) {
    const a = clamp(slotOf(g, iv.startEpoch), 0, g.n), b = clamp(slotCeil(g, iv.endEpoch), 0, g.n);
    for (let i = a; i < b; i++) preferredMask[i] = 1;
  }
  g.preferredMask = preferredMask;
  return { grid: g, wakeCycles, warnings, prefs, tz, rules };
}
function paintMaintenance(g, bw, s, e, title, kind) {
  const a = clamp(slotOf(g, s), bw.startSlot, bw.endSlot), b = clamp(slotCeil(g, e), bw.startSlot, bw.endSlot);
  let free = 0; for (let i = a; i < b; i++) if (g.type[i] === SLOT.FREE) free++;
  if (!free) return;
  paint(g, epochOf(g, a), epochOf(g, b), SLOT.MAINT, { kind, title, restorative: kind !== 'ROUTINE' });
}
/* Meals that fall inside a shift are assumed eaten on break; otherwise they slide to the
   nearest free window within ±2h. Meals are never compressed to fit tasks (I_2). */
function placeMeal(g, bw, s, minutes, title) {
  const n = Math.ceil(minutes / 15);
  const want = slotOf(g, s);
  if (want >= 0 && want < g.n && g.type[want] === SLOT.HARD) return;
  for (let delta = 0; delta <= 8; delta++) {
    for (const dir of delta === 0 ? [0] : [1, -1]) {
      const a = want + dir * delta;
      if (a < bw.startSlot || a + n > bw.endSlot) continue;
      let ok = true; for (let i = a; i < a + n; i++) if (g.type[i] !== SLOT.FREE) { ok = false; break; }
      if (ok) { paint(g, epochOf(g, a), epochOf(g, a + n), SLOT.MAINT, { kind: 'MEAL', title, restorative: true }); return; }
    }
  }
  bw.maintBlocks.push({ skipped: title });
}
/* ── Life Director Engine · Part 2: demand compilation ──────────────────────
   Goals → ActivityTemplates → CandidateTasks, then quest coalescing (spec §4–5).
   ------------------------------------------------------------------------- */

/* Domain blueprints (spec §4.2). Durations in minutes; f = [MED, target, MRV].
   Numbers are design parameters, not scientific constants (see NOTES §D2). */
const ARCHETYPES = {
  FITNESS_HYPERTROPHY: { title: 'Build Muscle', keywords: ['muscle', 'strength', 'strong', 'gym', 'lift', 'workout', 'fit', 'health'], templates: [
    { key: 'strength', title: 'Resistance Training', tag: 'fitness.strength', d: [45, 60, 75], f: [2, 3, 5], IP: 4, IC: 1, spacingMinH: 24, spacingMinHNovice: 48, spacingIdealH: 48, avoidBeforeSleepMin: 120, phases: ['SECONDARY', 'PEAK'], volumeDriver: false },
    { key: 'mealprep', title: 'Meal Prep Batch', tag: 'nutrition.mealprep', d: [60, 90, 120], f: [1, 1, 2], IP: 2, IC: 1, spacingMinH: 72, spacingIdealH: 96, phases: ['PEAK', 'SECONDARY'], preferRestDay: true },
    { key: 'assess', title: 'Progress Check-in', tag: 'fitness.assessment', d: [15, 15, 15], f: [1, 1, 1], IP: 1, IC: 2, spacingMinH: 120, spacingIdealH: 168, phases: ['PEAK'] }] },
  SKILL_LANGUAGE: { title: 'Learn a Language', keywords: ['language', 'spanish', 'french', 'japanese', 'german', 'vocab', 'speak'], templates: [
    { key: 'vocab', title: 'Vocab Review', tag: 'language.vocab', d: [15, 15, 30], f: [4, 6, 7], IP: 1, IC: 2, spacingMinH: 12, spacingIdealH: 24, phases: ['PEAK', 'WAKE', 'ANY'] },
    { key: 'grammar', title: 'Grammar & Drills', tag: 'language.grammar', d: [30, 45, 60], f: [2, 3, 4], IP: 1, IC: 4, spacingMinH: 20, spacingIdealH: 48, phases: ['PEAK', 'SECONDARY'] },
    { key: 'speaking', title: 'Speaking & Listening', tag: 'language.speaking', d: [30, 30, 45], f: [1, 2, 3], IP: 1, IC: 3, spacingMinH: 20, spacingIdealH: 72, phases: ['SECONDARY', 'PEAK'] },
    { key: 'check', title: 'Weekly Skill Check', tag: 'language.assessment', d: [15, 30, 30], f: [1, 1, 1], IP: 1, IC: 3, spacingMinH: 120, spacingIdealH: 168, phases: ['PEAK'], requiresPrior: { keys: ['vocab', 'grammar', 'speaking'], count: 3 } }] },
  HABIT_READING: { title: 'Read More', keywords: ['read', 'book', 'novel', 'pages'], templates: [
    { key: 'reading', title: 'Focused Reading', tag: 'reading.focused', d: [15, 30, 60], f: [3, 5, 7], IP: 1, IC: 2, spacingMinH: 4, spacingIdealH: 24, maxPerBwc: 2, phases: ['WIND', 'SECONDARY', 'ANY'], volumeDriver: true },
    { key: 'checkpoint', title: 'Reading Checkpoint', tag: 'reading.checkpoint', d: [15, 15, 15], f: [0, 1, 1], IP: 1, IC: 2, spacingMinH: 120, spacingIdealH: 168, phases: ['ANY'] }] },
  ADMIN_FINANCE: { title: 'Improve Finances', keywords: ['money', 'finance', 'budget', 'save', 'debt', 'invest'], templates: [
    { key: 'audit', title: 'Budget Audit', tag: 'admin.finance.audit', d: [15, 30, 45], f: [1, 1, 2], IP: 1, IC: 4, spacingMinH: 48, spacingIdealH: 96, phases: ['PEAK'] },
    { key: 'planning', title: 'Financial Planning', tag: 'admin.finance.planning', d: [30, 45, 60], f: [1, 1, 2], IP: 1, IC: 4, spacingMinH: 48, spacingIdealH: 96, phases: ['PEAK'] }] },
  FITNESS_CARDIO: { title: 'Cardio Endurance', keywords: ['run', 'cardio', 'cycle', 'swim', 'endurance', 'walk', 'health', 'weight'], templates: [
    { key: 'cardio', title: 'Cardio Session', tag: 'fitness.cardio', d: [20, 30, 60], f: [2, 3, 5], IP: 3, IC: 1, spacingMinH: 20, spacingIdealH: 48, avoidBeforeSleepMin: 90, phases: ['SECONDARY', 'PEAK'] }] },
  STUDY_GENERAL: { title: 'Study / Course', keywords: ['study', 'course', 'exam', 'learn', 'class', 'cert'], templates: [
    { key: 'study', title: 'Deep Study Block', tag: 'study.deep', d: [30, 45, 90], f: [2, 4, 6], IP: 1, IC: 4, spacingMinH: 6, spacingIdealH: 24, maxPerBwc: 2, phases: ['PEAK', 'SECONDARY'], volumeDriver: true },
    { key: 'review', title: 'Review & Flashcards', tag: 'study.review', d: [15, 20, 30], f: [2, 4, 7], IP: 1, IC: 2, spacingMinH: 8, spacingIdealH: 24, phases: ['ANY'] }] },
  CREATIVE_PRACTICE: { title: 'Creative Practice', keywords: ['draw', 'paint', 'music', 'guitar', 'piano', 'write', 'art', 'create'], templates: [
    { key: 'practice', title: 'Creative Practice', tag: 'creative.practice', d: [20, 45, 90], f: [2, 4, 6], IP: 1, IC: 3, spacingMinH: 12, spacingIdealH: 24, phases: ['SECONDARY', 'PEAK', 'WIND'], volumeDriver: true }] },
  MINDFULNESS: { title: 'Mindfulness', keywords: ['meditat', 'mindful', 'journal', 'calm', 'stress'], templates: [
    { key: 'meditate', title: 'Meditation / Journal', tag: 'wellbeing.mindfulness', d: [10, 15, 30], f: [3, 5, 7], IP: 1, IC: 1, spacingMinH: 12, spacingIdealH: 24, phases: ['WAKE', 'WIND', 'ANY'] }] }
};
/* Default demand profile per tag root, for quests whose templates we must synthesise. */
const TAG_PROFILE = {
  fitness: { IP: 3, IC: 1, d: 30 }, nutrition: { IP: 2, IC: 1, d: 60 }, language: { IP: 1, IC: 3, d: 30 },
  reading: { IP: 1, IC: 2, d: 30 }, study: { IP: 1, IC: 4, d: 45 }, admin: { IP: 1, IC: 3, d: 30 },
  creative: { IP: 1, IC: 3, d: 45 }, wellbeing: { IP: 1, IC: 1, d: 15 }, chores: { IP: 2, IC: 1, d: 30 },
  social: { IP: 1, IC: 2, d: 60 }, general: { IP: 2, IC: 2, d: 30 }
};
function tagProfile(tag) { return TAG_PROFILE[String(tag || 'general').split('.')[0]] || TAG_PROFILE.general; }

function tagMatches(reqTag, tplTag) {
  if (!reqTag || !tplTag) return false;
  if (reqTag.endsWith('.*')) { const b = reqTag.slice(0, -2); return tplTag === b || tplTag.startsWith(b + '.'); }
  return tplTag === reqTag || tplTag.startsWith(reqTag + '.');
}
function tagDepth(reqTag, tplTag) { return reqTag === tplTag ? 1000 : reqTag.replace('.*', '').split('.').length; }

function suggestTemplates(title) {
  const t = String(title || '').toLowerCase();
  const scored = Object.keys(ARCHETYPES).map(id => ({ id, s: ARCHETYPES[id].keywords.reduce((n, k) => n + (t.includes(k) ? 1 : 0), 0) }))
    .filter(x => x.s > 0).sort((a, b) => b.s - a.s || cmpStr(a.id, b.id));
  return scored.slice(0, 3).map(x => x.id);
}

function normalizeGoal(g) {
  return {
    id: String(g.id || g.goalId), title: String(g.title || 'Goal'),
    archetypeId: g.archetypeId || (g.domainArchetype && g.domainArchetype !== 'CUSTOM' ? g.domainArchetype : null),
    subject: g.subject ? String(g.subject).toLowerCase().replace(/[^a-z0-9_]/g, '') : null,
    priority: clamp(Math.round(g.priority || 3), 1, 5),
    level: ['NOVICE', 'INTERMEDIATE', 'ADVANCED'].includes(g.level || g.experienceLevel) ? (g.level || g.experienceLevel) : 'NOVICE',
    customTemplates: Array.isArray(g.customTemplates) ? g.customTemplates : [],
    disabledTemplates: Array.isArray(g.disabledTemplates) ? g.disabledTemplates : [],
    target: g.target || (g.targetMetricValue ? { units: g.targetMetricValue, completed: g.currentMetricValue || 0, unit: g.metricUnit, deadlineEpoch: g.deadlineEpoch, unitsPerHour: g.unitsPerHour || null } : null),
    createdEpoch: g.createdEpoch || g.startEpoch || null,
    status: g.status || g.schedulingState || 'ACTIVE'
  };
}

function goalTemplates(goal) {
  const out = [];
  const arch = goal.archetypeId && ARCHETYPES[goal.archetypeId];
  if (arch) for (const t of arch.templates) {
    if (goal.disabledTemplates.includes(t.key)) continue;
    const tag = goal.subject && t.tag.startsWith('language.') ? t.tag.replace('language.', 'language.' + goal.subject + '.') : t.tag;
    out.push(Object.assign({}, t, { templateId: goal.id + ':' + t.key, goalId: goal.id, tag, spacingKey: goal.id + ':' + t.key }));
  }
  for (const c of goal.customTemplates) {
    if (!c || !c.tag || !(c.minutes || c.d)) continue;
    const dOpt = ceilTo15(c.minutes || (c.d && c.d[1]) || 30);
    const freq = clamp(Math.round(c.perWeek || (c.f && c.f[1]) || 3), 1, 14);
    const key = String(c.key || c.tag).replace(/[^a-z0-9_.-]/gi, '');
    out.push({ key, title: c.title || goal.title, tag: c.tag, templateId: goal.id + ':' + key, goalId: goal.id, spacingKey: goal.id + ':' + key,
      d: c.d || [Math.max(15, dOpt - 15), dOpt, dOpt + 15], f: c.f || [Math.max(1, freq - 1), freq, Math.min(14, freq + 1)],
      IP: clamp(c.IP || tagProfile(c.tag).IP, 1, 5), IC: clamp(c.IC || tagProfile(c.tag).IC, 1, 5),
      spacingMinH: c.spacingMinH != null ? c.spacingMinH : (freq >= 7 ? 8 : 16), spacingIdealH: c.spacingIdealH || Math.max(12, Math.round(168 / freq)),
      maxPerBwc: c.maxPerBwc || 1, phases: c.phases || ['ANY'], volumeDriver: !!c.volumeDriver });
  }
  return out;
}

/* expandGoalActivities — spec §4.3/4.4 + §14 #2.
   Produces per-template weekly session plans; CandidateTasks are created per week window. */
function expandGoalActivities(goals, prefs, nowEpoch) {
  const plans = [], clarificationPrompts = [], trajectoryWarnings = [];
  for (const raw of goals.slice().sort((a, b) => cmpStr(String(a.id || a.goalId), String(b.id || b.goalId)))) {
    const goal = normalizeGoal(raw);
    if (goal.status !== 'ACTIVE') continue;
    const tpls = goalTemplates(goal);
    if (!tpls.length) {
      clarificationPrompts.push({ goalId: goal.id, title: goal.title, reasonCode: 'MISSING_ACTIVITY_PARAMETERS',
        missingParameters: ['activityTypeTag', 'weeklyFrequency', 'sessionDurationMinutes'], suggestedTemplateIds: suggestTemplates(goal.title) });
      continue;
    }
    for (const t of tpls) {
      const [fMED, fTarget, fMRV] = t.f;
      let [dMin, dOpt, dMax] = t.d.map(ceilTo15);
      let base = goal.level === 'NOVICE' ? fMED : goal.level === 'INTERMEDIATE' ? fTarget : Math.min(fMRV, fTarget + 1);
      let stretch = fMRV;
      if (prefs.intensityLevel === 'LIGHT') { base = fMED; stretch = fMED; }
      else if (prefs.intensityLevel === 'BALANCED') stretch = base;
      // AGGRESSIVE / HARDCORE keep stretch up to MRV (Tier 1)
      let status = 'ON_TRACK', projection = null;
      /* Mode A cumulative pacing for the template that drives the goal's volume */
      if (t.volumeDriver && goal.target && goal.target.units > 0 && goal.target.deadlineEpoch && goal.target.unitsPerHour > 0) {
        const uRem = Math.max(0, goal.target.units - (goal.target.completed || 0));
        const wRem = Math.max(1, (goal.target.deadlineEpoch - nowEpoch) / 604800);
        const mReq = Math.ceil((uRem / goal.target.unitsPerHour) * 60 / wRem);
        const mMED = fMED * dMin, mMRV = fMRV * dMax;
        let mTarget;
        if (mReq > mMRV) {
          status = 'DEADLINE_UNSUSTAINABLE'; mTarget = mMRV;
          const weeks = (uRem / goal.target.unitsPerHour) * 60 / mMRV;
          projection = Math.round(nowEpoch + weeks * 604800);
          trajectoryWarnings.push({ goalId: goal.id, code: status, projectedCompletionEpoch: projection,
            message: 'At a safe maximum of ' + mMRV + ' min/week, ' + goal.title + ' finishes later than your deadline.' });
        } else mTarget = Math.max(mMED, mReq);
        const k = clamp(Math.ceil(mTarget / dOpt), fMED, fMRV);
        const d = clamp(ceilTo15(mTarget / k), dMin, dMax);
        base = Math.max(base, k); stretch = Math.max(stretch, base); dOpt = d;
      }
      plans.push({ goal, template: t, fMED, base: Math.max(fMED, base), stretch: Math.max(base, stretch), dMin, dOpt, dMax, status, projection,
        spacingMinH: goal.level === 'NOVICE' && t.spacingMinHNovice ? t.spacingMinHNovice : t.spacingMinH });
    }
  }
  return { plans, clarificationPrompts, trajectoryWarnings };
}

function normalizeQuest(q) {
  const tierMap = { MAIN_STORY: 'MANDATORY', MANDATORY: 'MANDATORY', CORE: 'CORE', SIDE: 'OPTIONAL', BOUNTY: 'OPTIONAL', OPTIONAL: 'OPTIONAL' };
  const reqs = Array.isArray(q.requirements) && q.requirements.length ? q.requirements : [{
    requirementId: 'r0', activityTypeTag: q.tag || q.activityTypeTag, targetCount: q.requiredCount || q.targetCount || 0,
    currentCount: q.progressCount || q.currentCount || 0, targetMinutes: q.requiredMinutes || 0, currentMinutes: q.progressMinutes || 0 }];
  return {
    id: String(q.id || q.questId), title: String(q.title || 'Quest'), tier: tierMap[q.tier || q.questTier] || 'CORE',
    status: q.status || 'ACTIVE', deadlineEpoch: q.deadlineEpoch || null,
    prerequisiteQuestIds: (q.prerequisiteQuestIds || []).map(String), exclusive: !!(q.exclusive || q.requiresDedicatedExclusiveTime),
    schedulingMode: q.schedulingMode || 'SESSIONS', recurrence: q.recurrence || null,
    minSessionMinutes: q.minSessionMinutes || 0, sessionMinutes: q.sessionMinutes || 0, minIntensity: q.minIntensity || 0,
    appliedEventIds: q.appliedEventIds || [], bounty: q.bounty || null, source: q.source || 'host',
    onlyDays: Array.isArray(q.onlyDays) && q.onlyDays.length ? q.onlyDays.slice() : null, windowStartEpoch: q.windowStartEpoch || null, hostRef: q.hostRef || null,
    requirements: reqs.map((r, i) => ({ id: String(r.requirementId || r.id || ('r' + i)), tag: r.activityTypeTag || r.tag || 'general',
      targetCount: Math.max(0, r.targetCount || 0), currentCount: Math.max(0, r.currentCount || 0),
      targetMinutes: Math.max(0, r.targetMinutes || 0), currentMinutes: Math.max(0, r.currentMinutes || 0),
      minSessionMinutes: r.minSessionMinutes || q.minSessionMinutes || 0, sessionMinutes: r.sessionMinutes || q.sessionMinutes || 0 }))
  };
}
const QUEST_WEIGHT = { MANDATORY: 5, CORE: 4, OPTIONAL: 2 };

/* Tarjan SCC over quest prerequisites (spec Phase 0 / edge case 13). */
function findQuestCycles(quests) {
  const ids = quests.map(q => q.id), index = new Map(), low = new Map(), on = new Set(), stack = [], bad = new Set();
  const adj = new Map(quests.map(q => [q.id, q.prerequisiteQuestIds.filter(p => ids.includes(p))]));
  let i = 0;
  function sc(v) {
    index.set(v, i); low.set(v, i); i++; stack.push(v); on.add(v);
    for (const w of adj.get(v)) {
      if (!index.has(w)) { sc(w); low.set(v, Math.min(low.get(v), low.get(w))); }
      else if (on.has(w)) low.set(v, Math.min(low.get(v), index.get(w)));
    }
    if (low.get(v) === index.get(v)) {
      const comp = []; let w;
      do { w = stack.pop(); on.delete(w); comp.push(w); } while (w !== v);
      if (comp.length > 1 || adj.get(v).includes(v)) comp.forEach(c => bad.add(c));
    }
  }
  for (const v of ids.slice().sort()) if (!index.has(v)) sc(v);
  return bad;
}

/* Build CandidateTasks: goal sessions per week window, then coalesce quest requirements.
   Coalescing validates COMPATIBILITY (tag, duration, intensity, deadline, exclusivity),
   not just tag overlap (NOTES §D8). */
function compileCandidates(ctx) {
  const { plans, quests: rawQuests, weeks, nowEpoch, horizon, history, keptInstances } = ctx;
  const tasks = [], passiveReminders = [], rejected = [], questStatus = {};
  const doneByKeyWeek = new Map(); // templateId|week → count completed or consumed
  const countKW = (tplId, w) => doneByKeyWeek.get(tplId + '|' + w) || 0;
  const bump = (tplId, w) => doneByKeyWeek.set(tplId + '|' + w, countKW(tplId, w) + 1);
  const weekOf = ep => weeks.findIndex(w => ep >= w.startEpoch && ep < w.endEpoch);
  for (const h of history || []) { const w = weekOf(h.startEpoch); if (w >= 0 && h.templateId) bump(h.templateId, w); }
  for (const k of keptInstances || []) { const w = weekOf(k.startEpoch); if (w >= 0 && k.templateId) bump(k.templateId, w); }

  /* goal sessions */
  for (const p of plans) {
    const t = p.template;
    for (let w = 0; w < weeks.length; w++) {
      const wk = weeks[w];
      const scale = wk.days / 7; // partial trailing week (horizon not multiple of 7)
      const total = Math.max(0, Math.round(p.stretch * scale));
      const already = countKW(t.templateId, w);
      const ids = freshIds(ctx.usedIds, t.templateId + '|' + wk.key + '|', Math.max(0, total - already));
      ids.forEach((id, j) => {
        const k = already + j;
        const tier = k < Math.round(p.fMED * scale) ? 3 : k < Math.round(p.base * scale) ? 2 : 1;
        tasks.push(makeTask({
          id, kind: 'GOAL', title: t.title, templateId: t.templateId, tag: t.tag,
          goalId: p.goal.id, goalTitle: p.goal.title, linkedGoalIds: [p.goal.id], priority: p.goal.priority, questWeight: 0,
          tier, tierReason: tier === 3 ? 'WEEKLY_MINIMUM' : tier === 2 ? 'WEEKLY_TARGET' : 'STRETCH_VOLUME',
          dOpt: p.dOpt, dMin: p.dMin, dMax: p.dMax, IP: t.IP, IC: t.IC, spacingKey: t.spacingKey, spacingMinH: p.spacingMinH, spacingIdealH: t.spacingIdealH,
          maxPerBwc: t.maxPerBwc || 1, avoidBeforeSleepMin: t.avoidBeforeSleepMin || 0, phases: t.phases || ['ANY'], preferRestDay: !!t.preferRestDay,
          windowStart: Math.max(wk.startEpoch, nowEpoch), windowEnd: wk.endEpoch, deadlineEpoch: null, week: w, sessionIndex: k,
          requiresPrior: t.requiresPrior ? { templateIds: t.requiresPrior.keys.map(x => p.goal.id + ':' + x), count: t.requiresPrior.count } : null,
          goalStatus: p.status
        }));
      });
    }
  }
  /* quests */
  const quests = rawQuests.map(normalizeQuest).sort(byId);
  const cyc = findQuestCycles(quests);
  const qById = new Map(quests.map(q => [q.id, q]));
  for (const q of quests) {
    if (q.status !== 'ACTIVE') { questStatus[q.id] = q.status; continue; }
    if (cyc.has(q.id)) { questStatus[q.id] = 'DEPENDENCY_CYCLE'; rejected.push(diag(q.id, null, 'DEPENDENCY_CYCLE', q.title + ' is part of a circular prerequisite chain, so it cannot be scheduled.')); continue; }
    const blocking = q.prerequisiteQuestIds.filter(pid => { const p = qById.get(pid); return p && p.status !== 'COMPLETED' && !(p.status === 'ACTIVE'); });
    if (blocking.length) { questStatus[q.id] = 'BLOCKED_BY_PREREQUISITE'; rejected.push(diag(q.id, null, 'BLOCKED_BY_PREREQUISITE', q.title + ' waits for a prerequisite quest that is not active.')); continue; }
    if (q.schedulingMode === 'NONE') { passiveReminders.push({ questId: q.id, title: q.title, anchor: q.anchor || 'WAKE' }); questStatus[q.id] = 'PASSIVE'; continue; }
    if (q.deadlineEpoch && q.deadlineEpoch <= nowEpoch) { questStatus[q.id] = 'DEADLINE_PASSED'; continue; }
    questStatus[q.id] = 'SCHEDULING';
    const windows = [];
    if (q.recurrence === 'DAILY') {
      for (const d of horizon) if (!q.onlyDays || q.onlyDays.includes(d.dow)) windows.push({ start: Math.max(d.startEpoch, nowEpoch), end: d.endEpoch, key: d.key, isToday: nowEpoch >= d.startEpoch && nowEpoch < d.endEpoch });
    } else windows.push({ start: Math.max(nowEpoch, q.windowStartEpoch || 0), end: Math.min(q.deadlineEpoch || Infinity, weeks[weeks.length - 1].endEpoch), key: 'once' });
    for (const r of q.requirements) {
      for (const win of windows) {
        if (win.end <= win.start) continue;
        const fresh = q.recurrence === 'DAILY' && !win.isToday; // future days of a daily quest start from zero
        let remainingCount = fresh ? Math.max(0, r.targetCount || 1) : Math.max(0, r.targetCount - r.currentCount);
        let remainingMin = fresh ? r.targetMinutes : Math.max(0, r.targetMinutes - r.currentMinutes);
        // locked/started instances already carrying this requirement count toward it
        for (const k of keptInstances || []) if ((k.linkedQuestRequirementIds || []).some(x => x.questId === q.id && x.reqId === r.id) && k.startEpoch >= win.start && k.startEpoch < win.end) {
          if (remainingCount > 0) remainingCount--; remainingMin = Math.max(0, remainingMin - Math.round((k.endEpoch - k.startEpoch) / 60));
        }
        const prof = tagProfile(r.tag);
        let sessLen = ceilTo15(r.sessionMinutes || r.minSessionMinutes || prof.d);
        if (!remainingCount && remainingMin) remainingCount = Math.ceil(remainingMin / sessLen);
        if (!remainingCount) continue;
        const qTier = (q.deadlineEpoch && q.tier !== 'OPTIONAL' && (q.deadlineEpoch - nowEpoch) <= 86400 + sessLen * 60) || (q.recurrence === 'DAILY' && win.end - nowEpoch <= 86400 && q.tier !== 'OPTIONAL') ? 4
          : q.tier === 'OPTIONAL' ? 2 : 3;
        const link = { questId: q.id, reqId: r.id, questTitle: q.title, deadlineEpoch: q.recurrence === 'DAILY' ? win.end : q.deadlineEpoch };
        /* coalescing candidates: compatible goal sessions (NOTES §D8: tag + duration + intensity + exclusivity;
           check-in templates such as assessments only satisfy quests that name them exactly) */
        let attached = 0;
        const compatAll = q.exclusive ? [] : tasks.filter(t => t.goalId && !t.exclusive && t.kind !== 'QUEST' && !t.coverable && tagMatches(r.tag, t.tag)
          && (!/\.(assessment|checkpoint)$/.test(t.tag) || t.tag === r.tag)
          && t.dMax >= (r.minSessionMinutes || 0) && Math.max(t.IP, t.IC) >= (q.minIntensity || 0));
        const inWin = t => t.windowStart < win.end && t.windowEnd > win.start;
        let best = null, bestArr = [];
        if (compatAll.length) {
          const byTpl = new Map();
          for (const t of compatAll) { if (!byTpl.has(t.templateId)) byTpl.set(t.templateId, []); byTpl.get(t.templateId).push(t); }
          const ranked = [...byTpl.entries()].map(([id, arr]) => ({ id, arr, depth: tagDepth(r.tag, arr[0].tag), n: arr.filter(inWin).length }))
            .sort((x, y) => y.depth - x.depth || y.n - x.n || cmpStr(x.id, y.id));
          best = ranked[0].arr[0]; bestArr = ranked[0].arr;
        }
        const extraFrom = (tpl, xid, k, cover) => makeTask(Object.assign({}, tpl, {
          id: xid, kind: 'MERGED', sessionIndex: 100 + k, linkedQuestRequirementIds: [link], linkedGoalIds: tpl.linkedGoalIds.slice(), tier: qTier,
          tierReason: qTier === 4 ? 'CRITICAL_DEADLINE' : 'ACTIVE_QUEST', questWeight: QUEST_WEIGHT[q.tier], windowStart: win.start, windowEnd: win.end,
          deadlineEpoch: link.deadlineEpoch, dependsOn: [], requiresPrior: null, coverable: cover || null,
          dOpt: Math.max(tpl.dOpt, ceilTo15(r.minSessionMinutes || 15)), dMin: Math.max(tpl.dMin, ceilTo15(r.minSessionMinutes || 15))
        }));
        if (q.recurrence === 'DAILY') {
          /* Daily quests are COVERED by any compatible goal session that lands that day (resolved at commit time,
             so goal sessions keep their full week window and spacing). Days with no such session get one block. */
          const cover = { reqTag: r.tag, start: win.start, end: win.end, link, exactOnly: !!q.exclusive };
          if (best && (best.spacingMinH || 0) <= 24) {
            for (const [k, xid] of freshIds(ctx.usedIds, 'qx|' + q.id + '|' + r.id + '|' + win.key + '|', remainingCount).entries()) tasks.push(extraFrom(best, xid, k, cover));
            attached = remainingCount;
          } else {
            for (const [k, qid] of freshIds(ctx.usedIds, 'q|' + q.id + '|' + r.id + '|' + win.key + '|', remainingCount).entries()) {
              const t = standaloneQuestTask(q, r, win, link, qTier, sessLen, prof, k, qid, weekOf, best);
              t.coverable = cover; tasks.push(t);
            }
            attached = remainingCount;
          }
        } else if (best) {
          const same = bestArr.filter(t => inWin(t) && !t.linkedQuestRequirementIds.some(x => x.questId === q.id)).sort((a, b) => a.week - b.week || a.sessionIndex - b.sessionIndex);
          for (const t of same) {
            if (attached >= remainingCount) break;
            t.linkedQuestRequirementIds.push(link); t.kind = 'MERGED';
            t.windowStart = Math.max(t.windowStart, win.start); t.windowEnd = Math.min(t.windowEnd, win.end);
            t.deadlineEpoch = link.deadlineEpoch || t.deadlineEpoch;
            if (r.minSessionMinutes) { t.dMin = Math.max(t.dMin, ceilTo15(r.minSessionMinutes)); t.dOpt = Math.max(t.dOpt, t.dMin); t.dMax = Math.max(t.dMax, t.dOpt); }
            if (qTier > t.tier) { t.tier = qTier; t.tierReason = qTier === 4 ? 'CRITICAL_DEADLINE' : 'ACTIVE_QUEST'; }
            t.questWeight = Math.max(t.questWeight, QUEST_WEIGHT[q.tier]);
            attached++;
          }
          /* union envelope: extra goal-linked sessions of the same template (its spacing still applies) */
          for (const [k, xid] of freshIds(ctx.usedIds, 'qx|' + q.id + '|' + r.id + '|' + win.key + '|', remainingCount - attached).entries()) { tasks.push(extraFrom(best, xid, k, null)); attached++; }
        }
        /* standalone quest sessions */
        for (const [k, qid] of freshIds(ctx.usedIds, 'q|' + q.id + '|' + r.id + '|' + win.key + '|', remainingCount - attached).entries())
          tasks.push(standaloneQuestTask(q, r, win, link, qTier, sessLen, prof, k, qid, weekOf, null));
      }
    }
  }
  /* questline DAG: tasks of B depend on all tasks of active prerequisite A (spec §5.4) */
  for (const q of quests) {
    if (questStatus[q.id] !== 'SCHEDULING') continue;
    for (const pid of q.prerequisiteQuestIds) {
      const p = qById.get(pid);
      if (!p || p.status === 'COMPLETED') continue;
      const deps = tasks.filter(t => t.linkedQuestRequirementIds.some(x => x.questId === pid)).map(t => t.id);
      const needs = p.requirements.some(r => (r.targetCount > r.currentCount) || (r.targetMinutes > r.currentMinutes));
      for (const t of tasks) if (t.linkedQuestRequirementIds.some(x => x.questId === q.id)) {
        if (needs && !deps.length) { t.blockedReason = 'BLOCKED_BY_PREREQUISITE'; }
        t.dependsOn.push(...deps); t.minLagMin = Math.max(t.minLagMin, 15);
      }
    }
  }
  return { tasks: tasks.sort(byId), passiveReminders, rejected, questStatus, quests };
}
function standaloneQuestTask(q, r, win, link, qTier, sessLen, prof, k, qid, weekOf, sameKindTpl) {
  const dMin = ceilTo15(r.minSessionMinutes || Math.max(15, sessLen - 15));
  return makeTask({
    id: qid, kind: 'QUEST', title: q.title, templateId: 'quest:' + q.id + ':' + r.id,
    tag: r.tag, goalId: null, linkedGoalIds: [], linkedQuestRequirementIds: [link], priority: 3, questWeight: QUEST_WEIGHT[q.tier],
    tier: qTier, tierReason: qTier === 4 ? 'CRITICAL_DEADLINE' : qTier === 3 ? 'ACTIVE_QUEST' : 'SIDE_QUEST',
    dOpt: Math.max(sessLen, dMin), dMin, dMax: Math.max(sessLen, dMin) + 15, IP: prof.IP, IC: prof.IC,
    /* never let a quest session dodge the recovery spacing of the same activity in a goal (e.g. lifting) */
    spacingKey: sameKindTpl && q.recurrence !== 'DAILY' ? sameKindTpl.spacingKey : 'quest:' + q.id + ':' + r.id,
    spacingMinH: sameKindTpl && q.recurrence !== 'DAILY' ? sameKindTpl.spacingMinH : (q.exclusive ? 0 : 3), spacingIdealH: 24, maxPerBwc: q.exclusive ? 1 : 2,
    avoidBeforeSleepMin: prof.IP >= 4 ? 120 : 0, phases: ['ANY'], windowStart: win.start, windowEnd: win.end,
    deadlineEpoch: link.deadlineEpoch, week: Math.max(0, weekOf(win.start)), sessionIndex: k, exclusive: q.exclusive, questId: q.id
  });
}
/* Deterministic ids that never collide with instances already completed/kept. */
function freshIds(used, base, count) {
  const out = [];
  for (let k = 0; out.length < count && k < count + 200; k++) {
    const id = stableId('t', base + k);
    if (used && used.has('i' + id.slice(1))) continue;
    out.push(id);
  }
  return out;
}
function makeTask(o) {
  return Object.assign({
    kind: 'GOAL', linkedGoalIds: [], linkedQuestRequirementIds: [], dependsOn: [], minLagMin: 0, exclusive: false,
    requiresPrior: null, blockedReason: null, origStartEpoch: null, isPlaced: false, priority: 3, questWeight: 0
  }, o, {
    linkedGoalIds: (o.linkedGoalIds || []).slice(), linkedQuestRequirementIds: (o.linkedQuestRequirementIds || []).map(x => Object.assign({}, x)),
    dependsOn: (o.dependsOn || []).slice(), isPlaced: false
  });
}
function diag(taskId, task, reasonCode, humanExplanation, extra) {
  return Object.assign({ taskId, title: task ? task.title : null, tier: task ? task.tier : null, reasonCode, humanExplanation,
    linkedQuestIds: task ? task.linkedQuestRequirementIds.map(x => x.questId) : [], goalId: task ? task.goalId : null }, extra || {});
}
/* ── Life Director Engine · Part 3: state, energy, feasibility, scoring ───── */
const W = { urg: 24, prio: 18, energy: 18, pref: 12, space: 12, lag: 10, variety: 6 };
const R = { OK: 0, BUSY: 1, WINDOW: 2, CAP: 4, PERDAY: 5, SPACING: 6, BEFORE_SLEEP: 7, FATIGUE: 8, POSTSHIFT: 9,
  CONSEC: 10, DOWNSTREAM: 11, PREREQ: 12, HIGHCOUNT: 13, PRIOR_VOLUME: 14, RECOVERY: 15 };
const R_TEXT = {
  1: 'no free window long enough', 2: 'outside the allowed dates', 4: 'your daily effort limit was already reached',
  5: 'only one session of this kind is allowed per day', 6: 'it needs rest spacing from a similar session',
  7: 'it would end too close to bedtime', 8: 'estimated energy was too low', 9: 'heavy physical work earlier that day',
  10: 'it would extend a long run of demanding activities', 11: 'it would leave a later demanding task under-energised',
  12: 'its prerequisite is not scheduled yet', 13: 'the day already has enough high-intensity sessions',
  14: 'it needs earlier study sessions first', 15: 'the only free time left is protected recovery'
};
const q6 = x => Math.round(x * 1e6) / 1e6; // quantise scores so tiny float drift cannot flip ties

function phaseOf(tauH) { return tauH < 1 ? 'WAKE' : tauH < 5.5 ? 'PEAK' : tauH < 7.5 ? 'DIP' : tauH < 11.5 ? 'SECONDARY' : 'WIND'; }
function baselineP(t) { return t < 1 ? 0.55 + 0.25 * t : t < 5.5 ? 0.90 : t < 7.5 ? 0.80 : t < 11.5 ? 0.90 : t <= 16.5 ? 0.80 - 0.5 * (t - 11.5) / 5 : 0.30; }
function baselineC(t) { return t < 1 ? 0.50 + 0.35 * t : t < 5.5 ? 0.95 : t < 7.5 ? 0.70 : t < 11.5 ? 0.80 : t <= 16.5 ? 0.75 - 0.45 * (t - 11.5) / 5 : 0.30; }

function initEnergy(S) {
  const g = S.g, target = S.sleepTarget;
  for (const bw of S.bw) {
    bw.sleepFactor = clamp(bw.sleepBeforeMin / target, 0.5, 1.05);
    for (let i = bw.startSlot; i < bw.endSlot; i++) {
      const tau = (epochOf(g, i) - bw.wakeEpoch) / 3600;
      g.baseP[i] = baselineP(tau) * bw.sleepFactor; g.baseC[i] = baselineC(tau) * bw.sleepFactor;
    }
    recomputeBwc(S, bw);
  }
}
/* Re-derive drain arrays and free-run lengths for one wake cycle. O(slots in cycle). */
function recomputeBwc(S, bw) {
  const g = S.g;
  let dP = 0, dC = 0;
  for (let i = bw.startSlot; i < bw.endSlot; i++) {
    g.drainP[i] = dP; g.drainC[i] = dC;
    const t = g.type[i];
    if (t === SLOT.HARD && g.load[i]) { const r = LOAD_DRAIN[g.load[i]]; dP += r[0] / 4; dC += r[1] / 4; }
    else if (t === SLOT.TASK || t === SLOT.LOCKED) { dP += 0.015 * g.taskIP[i] / 4; dC += 0.015 * g.taskIC[i] / 4; }
    else if (t === SLOT.RECOVERY || ((t === SLOT.MAINT || t === SLOT.COMMUTE) && g.tag[i] >= 0 && g.blocks[g.tag[i]].restorative)) {
      dP = Math.max(0, dP - 0.06 / 4); dC = Math.max(0, dC - 0.06 / 4);
    }
  }
  let run = 0;
  for (let i = bw.endSlot - 1; i >= bw.startSlot; i--) { run = (g.type[i] === SLOT.FREE && i >= S.nowSlot) ? run + 1 : 0; g.runLen[i] = run; }
  run = 0;
  for (let i = bw.startSlot; i < bw.endSlot; i++) { run = (g.type[i] === SLOT.FREE && i >= S.nowSlot) ? run + 1 : 0; g.runBack[i] = run; }
}
function energyAt(S, i, axis) {
  const g = S.g;
  let e = axis === 0 ? g.baseP[i] - g.drainP[i] : g.baseC[i] - g.drainC[i];
  e = clamp(e, 0, 1);
  const ck = S.checkin;
  if (ck) {
    const t = epochOf(g, i);
    if (t >= ck.epoch) { const a = Math.pow(0.5, (t - ck.epoch) / (6 * 3600)); e = a * ck.value + (1 - a) * e; }
  }
  return e;
}

/* ── state construction ── */
function buildState(engineInput, derived) {
  const prefs = derived.prefs, g = derived.grid;
  const now = engineInput.currentTimeEpoch;
  const lead = prefs.leadMinutes * 60;
  const S = {
    input: engineInput, g, bw: derived.wakeCycles, prefs, tz: derived.tz, now,
    nowSlot: clamp(slotCeil(g, now + lead), 0, g.n), sleepTarget: (engineInput.playerAvailability || {}).sleepRequirementMinutes || 480,
    instances: [], instById: new Map(), spacing: new Map(), tasks: [], taskById: new Map(),
    fair: { goalMin: {}, total: 0, share: {} }, warnings: derived.warnings.slice(), iterations: 0,
    checkin: null
  };
  const ck = engineInput.energyProfile && engineInput.energyProfile.checkin;
  if (ck && ck.epoch && ({ LOW: 1, NORMAL: 1, HIGH: 1 })[ck.level]) S.checkin = { epoch: ck.epoch, value: { LOW: 0.30, NORMAL: 0.65, HIGH: 0.95 }[ck.level] };
  for (const bw of S.bw) { bw.instances = []; bw.perKey = {}; bw.goalMin = 0; bw.highCount = 0; }
  for (const h of engineInput.completionHistory || []) if (h.spacingKey) addSpacing(S, h.spacingKey, h.startEpoch);
  initEnergy(S);
  return S;
}
function addSpacing(S, key, ep) { if (!S.spacing.has(key)) S.spacing.set(key, []); S.spacing.get(key).push(ep); }
function delSpacing(S, key, ep) { const a = S.spacing.get(key); if (!a) return; const i = a.indexOf(ep); if (i >= 0) a.splice(i, 1); }
const isHigh = t => t.IP >= 4 || t.IC >= 4;

/* Reserve recovery BEFORE any goal placement (fixes spec §7.3 ordering; NOTES §D3).
   Takes the latest free time in each wake cycle so evenings stay restful. */
function reserveRecovery(S) { for (const bw of S.bw) reserveRecoveryFor(S, bw); }
function reserveRecoveryFor(S, bw) {
  const g = S.g, need = Math.ceil(S.prefs.minDailyRecoveryMinutes / 15);
  let free = 0;
  for (let i = Math.max(bw.startSlot, S.nowSlot); i < bw.endSlot; i++) if (g.type[i] === SLOT.FREE) free++;
  bw.freeSlots = free;
  let left = Math.min(need, free);
  bw.recoverySlots = left; bw.recoveryShortfall = free < need && bw.endSlot > S.nowSlot && !bw.truncatedStart;
  for (let i = bw.endSlot - 1; i >= Math.max(bw.startSlot, S.nowSlot) && left > 0; i--) {
    if (g.type[i] === SLOT.FREE) { g.type[i] = SLOT.RECOVERY; left--; }
  }
  recomputeBwc(S, bw);
}
function recoveryBlocks(S) {
  const g = S.g, out = [];
  for (let i = 0; i < g.n; i++) {
    if (g.type[i] !== SLOT.RECOVERY) continue;
    let j = i; while (j < g.n && g.type[j] === SLOT.RECOVERY) j++;
    out.push({ startEpoch: epochOf(g, i), endEpoch: epochOf(g, j), recoveryType: 'PROTECTED_DOWNTIME', bwcId: g.bwcOf[i] >= 0 ? S.bw[g.bwcOf[i]].id : null });
    i = j - 1;
  }
  return out;
}

/* ── per-task constants ── */
function prepTask(S, t, goalInfo) {
  t.dOptSlots = Math.ceil(t.dOpt / 15); t.dMinSlots = Math.min(t.dOptSlots, Math.ceil(t.dMin / 15));
  const pg = t.priority || 3;
  t._prio = 0.60 * ((pg - 1) / 4) + 0.40 * ((t.questWeight || 0) / 5);
  const gi = t.goalId && goalInfo[t.goalId];
  t._lag = gi ? gi.lag : 0.5;
  const horizonSec = S.g.HN - S.g.H0;
  t._urgBase = t.deadlineEpoch ? Math.exp(-3 * Math.max(0, (t.deadlineEpoch - S.now - t.dOpt * 60) / horizonSec)) : 0.25;
  t._high = isHigh(t);
}
function taskEarliestLatest(S, t) {
  let earliest = Math.max(t.windowStart, S.now);
  let latest = Math.min(t.windowEnd, t.deadlineEpoch || Infinity, S.g.HN);
  for (const d of t.dependsOn) {
    const inst = S.instById.get('i' + d.slice(1));
    if (!inst) return { blocked: R.PREREQ };
    earliest = Math.max(earliest, inst.endEpoch + (t.minLagMin || 0) * 60);
  }
  if (t.requiresPrior) {
    const ends = [];
    for (const inst of S.instances) if (t.requiresPrior.templateIds.includes(inst.templateId) && inst.startEpoch >= t.windowStart - 0 && inst.startEpoch < t.windowEnd) ends.push(inst.endEpoch);
    for (const h of S.input.completionHistory || []) if (t.requiresPrior.templateIds.includes(h.templateId) && h.startEpoch >= t.windowStart - 7 * 86400 && h.startEpoch < t.windowEnd) ends.push(h.startEpoch);
    ends.sort((a, b) => a - b);
    if (ends.length < t.requiresPrior.count) return { blocked: R.PRIOR_VOLUME };
    earliest = Math.max(earliest, ends[t.requiresPrior.count - 1]);
  }
  return { earliestSlot: Math.max(S.nowSlot, slotCeil(S.g, earliest)), latestSlot: Math.min(S.g.n, slotOf(S.g, latest)) };
}

/* Hard-constraint check for placing t at [s, s+n). Returns R.OK or a reason code. */
function checkHard(S, t, bw, s, n, el) {
  const g = S.g, p = S.prefs;
  if (s < el.earliestSlot || s + n > el.latestSlot) return R.WINDOW;
  if (g.runLen[s] < n) return R.BUSY;
  if (bw.goalMin + n * 15 > p.maxDailyGoalMinutes) return R.CAP;
  if ((bw.perKey[t.spacingKey] || 0) >= t.maxPerBwc) return R.PERDAY;
  if (t._high && bw.highCount >= p.maxHighIntensityPerDay) return R.HIGHCOUNT;
  const st = epochOf(g, s), en = epochOf(g, s + n);
  if (t.avoidBeforeSleepMin && en > bw.sleepEpoch - t.avoidBeforeSleepMin * 60) return R.BEFORE_SLEEP;
  const sp = S.spacing.get(t.spacingKey);
  if (sp && t.spacingMinH > 0) { const lim = t.spacingMinH * 3600; for (const x of sp) if (Math.abs(st - x) < lim) return R.SPACING; }
  if (t.IP >= 4 && bw.heavyShiftEndSlot >= 0 && s >= bw.heavyShiftEndSlot) return R.POSTSHIFT;
  if (t.IP >= 4 || t.IC >= 4) {
    for (let k = 0; k < n; k++) {
      const own = 0.015 * k / 4;
      if (t.IP >= 4 && energyAt(S, s + k, 0) - own * t.IP < 0.45) return R.FATIGUE;
      if (t.IC >= 4 && energyAt(S, s + k, 1) - own * t.IC < 0.45) return R.FATIGUE;
    }
    let run = n;
    for (let i = s - 1; i >= bw.startSlot && (g.type[i] === SLOT.TASK || g.type[i] === SLOT.LOCKED) && (g.taskIP[i] >= 4 || g.taskIC[i] >= 4); i--) run++;
    for (let i = s + n; i < bw.endSlot && (g.type[i] === SLOT.TASK || g.type[i] === SLOT.LOCKED) && (g.taskIP[i] >= 4 || g.taskIC[i] >= 4); i++) run++;
    if (run * 15 > p.maxConsecutiveHighLoadMinutes) return R.CONSEC;
  }
  if (bw.instances.length) {
    const aP = 0.015 * t.IP * n / 4, aC = 0.015 * t.IC * n / 4;
    for (const o of bw.instances) {
      if (o.startEpoch < en || !(o.IP >= 4 || o.IC >= 4)) continue;
      const os = slotOf(g, o.startEpoch);
      if (o.IP >= 4 && energyAt(S, os, 0) - aP < 0.45) return R.DOWNSTREAM;
      if (o.IC >= 4 && energyAt(S, os, 1) - aC < 0.45) return R.DOWNSTREAM;
    }
  }
  return R.OK;
}

function fairnessMult(S, t) {
  if (S.rawMode || !t.goalId || !S.fair.total) return 1;
  const target = S.fair.share[t.goalId] || 0, actual = (S.fair.goalMin[t.goalId] || 0) / S.fair.total;
  return clamp(1 + 1.5 * (target - actual), 0.75, 1.25);
}
function energyFit(avail, I) { const d = avail - 0.18 * I; return d >= 0 ? 1 - 0.30 * d : Math.max(0, 1 - 2.8 * d * d); }
/* Utility U(a,s) on a 0–100 scale (spec §6.2) with documented corrections. */
function scorePlacement(S, t, bw, s, n, detail) {
  const g = S.g, st = epochOf(g, s);
  /* urgency: task-level slack from NOW (spec's slot-level version rewarded late placement; NOTES §D4) */
  let urg = t._urgBase;
  if (t.deadlineEpoch) { const span = Math.max(1, t.deadlineEpoch - S.now); urg *= 0.85 + 0.15 * (1 - clamp((st - S.now) / span, 0, 1)); }
  /* energy fit */
  const eP = energyAt(S, s, 0), eC = energyAt(S, s, 1);
  const energy = Math.min(energyFit(eP, t.IP), energyFit(eC, t.IC));
  /* time-window preference */
  const tau = (st - bw.wakeEpoch) / 3600, ph = phaseOf(tau), lm = g.localMin[s];
  let pref = 0.60;
  if (g.preferredMask[s]) pref = 1.0; else if (t.phases.includes(ph)) pref = 1.0;
  for (const aw of S.prefs.avoidWindows || []) if (lm >= aw.startMin && lm < aw.endMin) pref = 0.15;
  const restDay = (S.prefs.preferredRestDays || []).includes(g.days[g.dayOf[s]].dow);
  if (restDay && t._high) pref = 0.15;
  if (t.preferRestDay && restDay) pref = Math.max(pref, 1.0);
  /* spacing */
  let space = 1;
  const sp = S.spacing.get(t.spacingKey);
  if (sp && sp.length) {
    let near = Infinity; for (const x of sp) near = Math.min(near, Math.abs(st - x) / 3600);
    const sig = Math.max(4, t.spacingIdealH * 0.5);
    space = near >= t.spacingIdealH ? Math.max(0.6, Math.exp(-((near - t.spacingIdealH) ** 2) / (2 * (sig * 2) ** 2))) : Math.exp(-((near - t.spacingIdealH) ** 2) / (2 * sig * sig));
  }
  /* variety: alternate dominant axis with neighbours */
  let variety = 0;
  const axis = t.IP > t.IC ? 0 : 1;
  for (const i of [s - 1, s + n]) if (i >= 0 && i < g.n && g.type[i] === SLOT.TASK && ((g.taskIP[i] > g.taskIC[i] ? 0 : 1) !== axis)) variety = 0.5;
  const wsum = W.urg * urg + W.prio * t._prio + W.energy * energy + W.pref * pref + W.space * space + W.lag * t._lag + W.variety * variety;
  /* penalties */
  const gL = g.runBack[s] - 1, gR = g.runLen[s] - n;
  const psiFrag = (gL === 1 ? 3 : gL === 2 ? 1.5 : 0) + (gR === 1 ? 3 : gR === 2 ? 1.5 : 0);
  let psiMove = 0;
  if (t.origStartEpoch && t.origStartEpoch !== st) {
    const hrs = (t.origStartEpoch - S.now) / 3600;
    psiMove = hrs < 24 ? 25 : clamp(5 + 15 * Math.exp(-(hrs - 24) / 72), 5, 20);
  }
  const psiCompress = (t.dOptSlots - n) * 8;
  const F = fairnessMult(S, t);
  const U = q6(F * (wsum - psiFrag - psiMove - psiCompress));
  if (!detail) return U;
  return { U, F, wsum, factors: { urgency: W.urg * urg, priority: W.prio * t._prio, energy: W.energy * energy, preference: W.pref * pref, spacing: W.space * space, lag: W.lag * t._lag, variety: W.variety * variety },
    penalties: { fragmentation: psiFrag, movement: psiMove, compression: psiCompress }, phase: ph, energyP: eP, energyC: eC, restDay };
}

/* Best placement for t inside one wake cycle. Tries d_opt down to d_min. */
function evalInBwc(S, t, bw, el, tally) {
  let best = -Infinity, bs = -1, bn = 0;
  if (bw.endSlot <= el.earliestSlot || bw.startSlot >= el.latestSlot) { if (tally) tally[R.WINDOW] = (tally[R.WINDOW] || 0) + 1; return { score: best, slot: bs, n: bn }; }
  const a = Math.max(bw.startSlot, el.earliestSlot);
  for (let n = t.dOptSlots; n >= t.dMinSlots; n--) {
    const b = Math.min(bw.endSlot, el.latestSlot) - n;
    for (let s = a; s <= b; s++) {
      if (S.g.runLen[s] < n) { if (tally && n === t.dMinSlots) tally[R.BUSY] = (tally[R.BUSY] || 0) + 1; continue; }
      const r = checkHard(S, t, bw, s, n, el);
      if (r !== R.OK) { if (tally) tally[r] = (tally[r] || 0) + 1; continue; }
      const u = scorePlacement(S, t, bw, s, n, false);
      if (u > best) { best = u; bs = s; bn = n; }
    }
  }
  return { score: best, slot: bs, n: bn };
}
function evalAcross(S, t, onlyBwc) {
  const el = taskEarliestLatest(S, t);
  if (el.blocked) return { feasible: false, reason: el.blocked };
  let best = null, second = -Infinity, count = 0;
  for (const bw of S.bw) {
    if (onlyBwc && !onlyBwc.includes(bw.index)) continue;
    const r = evalInBwc(S, t, bw, el, null);
    if (r.slot < 0) continue;
    count++;
    if (!best || r.score > best.score) { if (best) second = Math.max(second, best.score); best = Object.assign({ bwc: bw.index }, r); }
    else second = Math.max(second, r.score);
  }
  if (!best) return { feasible: false, reason: R.BUSY };
  return { feasible: true, bwc: best.bwc, slot: best.slot, n: best.n, score: best.score, second: second === -Infinity ? 0 : second, count };
}

/* ── commit / remove ── */
function commit(S, t, bwIdx, s, n, meta) {
  const g = S.g, bw = S.bw[bwIdx];
  const st = epochOf(g, s), en = epochOf(g, s + n);
  const inst = Object.assign({
    instanceId: meta && meta.instanceId || ('i' + t.id.slice(1)), taskId: t.id, templateId: t.templateId, title: t.title, tag: t.tag, kind: t.kind,
    goalId: t.goalId, goalTitle: t.goalTitle || null, linkedGoalIds: t.linkedGoalIds.slice(),
    linkedQuestRequirementIds: t.linkedQuestRequirementIds.map(x => Object.assign({}, x)),
    startEpoch: st, endEpoch: en, durationSlots: n, minDurationSlots: t.dMinSlots, optDurationSlots: t.dOptSlots,
    tier: t.tier, lifecycleState: 'PLANNED', isUserLocked: false, bwcId: bw.id, IP: t.IP, IC: t.IC, spacingKey: t.spacingKey,
    compressed: n < t.dOptSlots, origStartEpoch: t.origStartEpoch || null, explanation: null, moves: 0,
    source: taskSnapshot(t)
  }, meta || {});
  occupy(S, inst, bwIdx, inst.isUserLocked ? SLOT.LOCKED : SLOT.TASK);
  t.isPlaced = true;
  /* A goal-linked session covers any compatible daily-quest block for the same day. */
  if (inst.linkedGoalIds.length) for (const u of S.tasks) {
    const c = u.coverable;
    if (!c || u.isPlaced || u === t || inst.startEpoch < c.start || inst.startEpoch >= c.end) continue;
    if (c.exactOnly ? inst.tag !== c.reqTag : !(tagMatches(c.reqTag, inst.tag) && (!/\.(assessment|checkpoint)$/.test(inst.tag) || inst.tag === c.reqTag))) continue;
    if (inst.linkedQuestRequirementIds.some(l => l.questId === c.link.questId && l.reqId === c.link.reqId)) continue;
    u.isPlaced = true; u.coveredBy = inst.instanceId;
    inst.linkedQuestRequirementIds.push(Object.assign({}, c.link));
  }
  return inst;
}
function occupy(S, inst, bwIdx, code) {
  const g = S.g, bw = S.bw[bwIdx];
  const a = clamp(slotOf(g, inst.startEpoch), 0, g.n), b = clamp(slotCeil(g, inst.endEpoch), 0, g.n);
  const idx = S.instances.length;
  for (let i = a; i < b; i++) { g.type[i] = code; g.taskOwner[i] = idx; g.taskIP[i] = inst.IP; g.taskIC[i] = inst.IC; }
  S.instances.push(inst); S.instById.set(inst.instanceId, inst);
  bw.instances.push(inst); bw.perKey[inst.spacingKey] = (bw.perKey[inst.spacingKey] || 0) + 1;
  bw.goalMin += (b - a) * 15; if (inst.IP >= 4 || inst.IC >= 4) bw.highCount++;
  addSpacing(S, inst.spacingKey, inst.startEpoch);
  for (const gid of inst.linkedGoalIds) S.fair.goalMin[gid] = (S.fair.goalMin[gid] || 0) + (b - a) * 15;
  if (inst.linkedGoalIds.length) S.fair.total += (b - a) * 15;
  recomputeBwc(S, bw);
}
function removeInst(S, inst) {
  const g = S.g;
  const bw = S.bw.find(x => x.id === inst.bwcId);
  const a = clamp(slotOf(g, inst.startEpoch), 0, g.n), b = clamp(slotCeil(g, inst.endEpoch), 0, g.n);
  for (let i = a; i < b; i++) { g.type[i] = SLOT.FREE; g.taskOwner[i] = -1; g.taskIP[i] = 0; g.taskIC[i] = 0; }
  S.instances.splice(S.instances.indexOf(inst), 1); S.instById.delete(inst.instanceId);
  if (bw) {
    bw.instances.splice(bw.instances.indexOf(inst), 1); bw.perKey[inst.spacingKey]--; bw.goalMin -= (b - a) * 15;
    if (inst.IP >= 4 || inst.IC >= 4) bw.highCount--;
    recomputeBwc(S, bw);
  }
  delSpacing(S, inst.spacingKey, inst.startEpoch);
  for (const gid of inst.linkedGoalIds) S.fair.goalMin[gid] -= (b - a) * 15;
  if (inst.linkedGoalIds.length) S.fair.total -= (b - a) * 15;
  const t = S.taskById.get(inst.taskId); if (t) t.isPlaced = false;
  for (const u of S.tasks) if (u.coveredBy === inst.instanceId) { u.isPlaced = false; u.coveredBy = null; }
}
/* An instance that a placed task depends on is pinned for repair purposes: moving it could break I_5. */
function anchorsDependents(S, inst) {
  for (const t of S.tasks) if (t.isPlaced && !t.coveredBy && t.dependsOn && t.dependsOn.includes(inst.taskId)) return true;
  return false;
}
function taskSnapshot(t) {
  const keys = ['coverable', 'id', 'kind', 'title', 'templateId', 'tag', 'goalId', 'goalTitle', 'linkedGoalIds', 'linkedQuestRequirementIds', 'priority', 'questWeight', 'tier', 'tierReason',
    'dOpt', 'dMin', 'dMax', 'IP', 'IC', 'spacingKey', 'spacingMinH', 'spacingIdealH', 'maxPerBwc', 'avoidBeforeSleepMin', 'phases', 'preferRestDay',
    'windowStart', 'windowEnd', 'deadlineEpoch', 'week', 'sessionIndex', 'dependsOn', 'minLagMin', 'exclusive', 'requiresPrior'];
  const o = {}; for (const k of keys) if (t[k] !== undefined) o[k] = deepClone(t[k]); return o;
}

/* Transactions: full snapshot of grid, cycles, fairness, spacing and placement flags.
   Fixes spec §7.3 where fairness state and the iterated instance list were not restored (NOTES §D5). */
function snapshot(S) {
  const g = S.g;
  return {
    type: g.type.slice(), taskOwner: g.taskOwner.slice(), taskIP: g.taskIP.slice(), taskIC: g.taskIC.slice(),
    drainP: g.drainP.slice(), drainC: g.drainC.slice(), runLen: g.runLen.slice(), runBack: g.runBack.slice(),
    bw: S.bw.map(b => ({ instances: b.instances.slice(), perKey: Object.assign({}, b.perKey), goalMin: b.goalMin, highCount: b.highCount })),
    instances: S.instances.slice(), fair: deepClone(S.fair),
    spacing: new Map([...S.spacing].map(([k, v]) => [k, v.slice()])), placed: S.tasks.map(t => t.isPlaced), covered: S.tasks.map(t => t.coveredBy || null),
    links: S.instances.map(i => i.linkedQuestRequirementIds.length)
  };
}
function restore(S, z) {
  const g = S.g;
  g.type.set(z.type); g.taskOwner.set(z.taskOwner); g.taskIP.set(z.taskIP); g.taskIC.set(z.taskIC);
  g.drainP.set(z.drainP); g.drainC.set(z.drainC); g.runLen.set(z.runLen); g.runBack.set(z.runBack);
  S.bw.forEach((b, i) => Object.assign(b, { instances: z.bw[i].instances.slice(), perKey: Object.assign({}, z.bw[i].perKey), goalMin: z.bw[i].goalMin, highCount: z.bw[i].highCount }));
  S.instances = z.instances.slice(); S.instById = new Map(S.instances.map(x => [x.instanceId, x]));
  S.fair = deepClone(z.fair); S.spacing = new Map([...z.spacing].map(([k, v]) => [k, v.slice()]));
  S.tasks.forEach((t, i) => { t.isPlaced = z.placed[i]; t.coveredBy = z.covered[i]; });
  S.instances.forEach((inst, i) => { if (inst.linkedQuestRequirementIds.length > z.links[i]) inst.linkedQuestRequirementIds.length = z.links[i]; });
}
/* Stable fingerprint used by tests to prove rollback is exact. */
function stateFingerprint(S) {
  const g = S.g; let h = 0;
  for (let i = 0; i < g.n; i++) h = (Math.imul(h, 31) + g.type[i] * 7 + g.taskIP[i] + (g.runLen[i] << 4)) | 0;
  return [h, S.instances.map(x => x.instanceId + '@' + x.startEpoch + '+' + x.durationSlots).sort().join(','), JSON.stringify(S.fair),
    S.bw.map(b => b.goalMin + ':' + b.highCount + ':' + JSON.stringify(Object.keys(b.perKey).sort().map(k => [k, b.perKey[k]]))).join('|'),
    S.tasks.map(t => t.isPlaced ? 1 : 0).join('')].join('#');
}
/* ── Life Director Engine · Part 4: generateSchedule ─────────────────────── */
const clockMs = () => (typeof performance !== 'undefined' && performance.now ? performance.now() : 0);
const PLANNED_STATES = ['PLANNED', 'IN_PROGRESS'];

function weeksOf(days) {
  const out = [];
  for (let i = 0; i < days.length; i += 7) {
    const chunk = days.slice(i, i + 7);
    out.push({ index: out.length, key: chunk[0].key, startEpoch: chunk[0].startEpoch, endEpoch: chunk[chunk.length - 1].endEpoch, days: chunk.length });
  }
  return out;
}
function bwcIndexAt(S, epoch) { const s = slotOf(S.g, epoch); return s >= 0 && s < S.g.n ? S.g.bwcOf[s] : -1; }

/* Shared front half of generate & repair: availability, kept instances, recovery. */
function prepareState(input) {
  const derived = deriveAvailability({
    horizonStartEpoch: input.horizonStartEpoch, horizonDays: input.horizonDays, timezone: input.timezone,
    playerAvailability: input.playerAvailability, fixedCommitments: input.fixedCommitments, playerPreferences: input.playerPreferences
  });
  const S = buildState(input, derived);
  const g = S.g;
  const existing = (input.existingSchedule || []).slice().sort((a, b) => a.startEpoch - b.startEpoch || cmpStr(a.instanceId, b.instanceId));
  const history = [], kept = [], displaced = [], replannable = new Map();
  for (const inst of existing) {
    if (!PLANNED_STATES.includes(inst.lifecycleState)) { history.push(inst); continue; }
    if (inst.endEpoch <= g.H0 || inst.startEpoch >= g.HN) { if (inst.endEpoch <= S.now) history.push(inst); continue; }
    if (inst.isUserLocked || inst.startEpoch <= S.now) {
      const a = clamp(slotOf(g, inst.startEpoch), 0, g.n), b = clamp(slotCeil(g, inst.endEpoch), 0, g.n);
      let clash = null;
      for (let i = Math.max(a, S.nowSlot); i < b; i++) if (g.type[i] !== SLOT.FREE) { clash = g.type[i]; break; }
      const bwi = bwcIndexAt(S, Math.max(inst.startEpoch, S.now));
      if (clash !== null && inst.endEpoch > S.now) { displaced.push({ inst, slotCode: clash }); continue; }
      if (bwi < 0) { history.push(inst); continue; }
      occupy(S, Object.assign({}, inst, { bwcId: S.bw[bwi].id, kept: true }), bwi, SLOT.LOCKED);
      kept.push(inst);
    } else replannable.set(inst.instanceId, inst);
  }
  reserveRecovery(S);
  return { S, derived, history, kept, displaced, replannable };
}

function goalInfoMap(goals, now) {
  const m = {};
  for (const raw of goals || []) {
    const g = normalizeGoal(raw);
    let lag = 0.5;
    if (g.target && g.target.units > 0 && g.target.deadlineEpoch && g.createdEpoch && g.target.deadlineEpoch > g.createdEpoch) {
      const pe = clamp((now - g.createdEpoch) / (g.target.deadlineEpoch - g.createdEpoch), 0, 1);
      const pa = clamp((g.target.completed || 0) / g.target.units, 0, 1);
      lag = clamp(0.5 + 2 * (pe - pa), 0, 1);
    }
    m[g.id] = { lag, priority: g.priority, title: g.title };
  }
  return m;
}

function generateSchedule(input) {
  const t0 = clockMs();
  const prep = prepareState(input);
  const { S, derived, history, kept, displaced, replannable } = prep;
  const g = S.g, prefs = S.prefs;
  const weeks = weeksOf(g.days);
  /* demand */
  const exp = expandGoalActivities(input.goals || [], prefs, S.now);
  const usedIds = new Set([...history, ...kept].map(x => x.instanceId));
  const hist = (input.completionHistory || []).filter(h => h.startEpoch >= g.H0 - 7 * 86400);
  const comp = compileCandidates({ usedIds, plans: exp.plans, quests: input.activeQuests || [], weeks, nowEpoch: S.now, horizon: g.days, history: hist,
    keptInstances: kept.concat(history.filter(h => h.lifecycleState === 'SKIPPED_VOLUNTARY' && h.tier <= 2)) });
  const gi = goalInfoMap(input.goals, S.now);
  const tasks = comp.tasks;
  S.tasks = tasks; S.taskById = new Map(tasks.map(t => [t.id, t]));
  for (const t of tasks) {
    prepTask(S, t, gi);
    const prev = replannable.get('i' + t.id.slice(1));
    if (prev) t.origStartEpoch = prev.startEpoch;
  }
  const goalIds = [...new Set(tasks.filter(t => t.goalId).map(t => t.goalId))].sort();
  const psum = goalIds.reduce((s, id) => s + (gi[id] ? gi[id].priority : 3), 0) || 1;
  for (const id of goalIds) S.fair.share[id] = (gi[id] ? gi[id].priority : 3) / psum;
  const rejected = comp.rejected.slice();
  const placedMeta = new Map();

  /* PHASE 1 — tier-stratified, regret-guided construction with incremental cache */
  const active = tasks.filter(t => !t.blockedReason);
  const nB = S.bw.length, K = active.length;
  const cS = new Float64Array(K * nB).fill(-Infinity), cSlot = new Int32Array(K * nB).fill(-1), cN = new Int16Array(K * nB);
  const dynamic = active.map(t => t.dependsOn.length > 0 || !!t.requiresPrior);
  const elStatic = active.map(t => (t.dependsOn.length || t.requiresPrior) ? null : taskEarliestLatest(S, t));
  const evalCell = (k, b) => {
    const t = active[k], el = elStatic[k] || taskEarliestLatest(S, t);
    const j = k * nB + b;
    if (el.blocked) { cS[j] = -Infinity; cSlot[j] = -1; return; }
    S.rawMode = true; const r = evalInBwc(S, t, S.bw[b], el, null); S.rawMode = false;
    cS[j] = r.score; cSlot[j] = r.slot; cN[j] = r.n;
  };
  const evalRow = k => { for (let b = 0; b < nB; b++) evalCell(k, b); };
  for (let k = 0; k < K; k++) evalRow(k);
  const medLeft = {}; for (const t of active) if (t.goalId && t.tierReason === 'WEEKLY_MINIMUM') medLeft[t.goalId] = (medLeft[t.goalId] || 0) + 1;
  const capBlocks = (t, n) => {
    if (!t.goalId || goalIds.length < 2 || S.fair.total < 180) return false;
    const others = Object.keys(medLeft).some(id => id !== t.goalId && medLeft[id] > 0);
    if (!others) return false;
    const share = ((S.fair.goalMin[t.goalId] || 0) + n * 15) / (S.fair.total + n * 15);
    return share > Math.max(0.60, (S.fair.share[t.goalId] || 0) + 0.20);
  };
  for (let tier = 4; tier >= 1; tier--) for (const coverPass of [false, true]) {
    let guard = 0;
    while (guard++ < 2000) {
      let win = -1, winKey = -Infinity, winInfo = null, cappedOnly = -1, cappedKey = -Infinity, cappedInfo = null;
      for (let k = 0; k < K; k++) {
        const t = active[k];
        if (t.isPlaced || t.tier !== tier || (!!t.coverable) !== coverPass) continue;
        if (dynamic[k]) evalRow(k);
        let best = -Infinity, bb = -1, second = -Infinity, count = 0;
        for (let b = 0; b < nB; b++) {
          const v = cS[k * nB + b]; if (cSlot[k * nB + b] < 0) continue;
          count++;
          if (v > best) { second = best; best = v; bb = b; } else if (v > second) second = v;
        }
        if (bb < 0) continue;
        const F = fairnessMult(S, t);
        const bestF = q6(best * F), secondF = second === -Infinity ? 0 : q6(second * F);
        const regret = bestF - secondF, scarcity = 15 / Math.max(1, count);
        const key = q6(bestF + 0.35 * regret + scarcity);
        const info = { k, b: bb, slot: cSlot[k * nB + bb], n: cN[k * nB + bb], score: bestF, regret, count, key, F };
        if (capBlocks(t, info.n)) { if (key > cappedKey || (key === cappedKey && t.id < active[cappedOnly].id)) { cappedKey = key; cappedOnly = k; cappedInfo = info; } continue; }
        if (key > winKey || (key === winKey && t.id < active[win].id)) { winKey = key; win = k; winInfo = info; }
      }
      if (win < 0 && cappedOnly >= 0) { win = cappedOnly; winInfo = cappedInfo; winInfo.capRelaxed = true; } // avoid cap deadlock (NOTES §D9)
      if (win < 0) break;
      const t = active[win];
      const inst = commit(S, t, winInfo.b, winInfo.slot, winInfo.n);
      placedMeta.set(inst.instanceId, Object.assign({ phase: 1 }, winInfo));
      if (t.goalId && t.tierReason === 'WEEKLY_MINIMUM') medLeft[t.goalId]--;
      for (let k = 0; k < K; k++) {
        if (active[k].isPlaced) continue;
        if (active[k].spacingKey === t.spacingKey) evalRow(k); else evalCell(k, winInfo.b);
      }
    }
  }
  /* PHASE 2 — bounded, transactional local repair for unplaced Tier 3–4 work.
     Bounded by iteration count, never wall-clock, so results are device-independent (NOTES §D7). */
  const repairLog = runLocalRepair(S, active.filter(t => !t.isPlaced && t.tier >= 3), placedMeta, 400);
  if (repairLog.changed) fillPass(S, active, placedMeta);
  /* PHASE 3 — diagnostics, explanations, validation */
  for (const t of tasks) {
    if (t.isPlaced) continue;
    rejected.push(buildRejection(S, t));
  }
  for (const d of displaced) rejected.push(diag(d.inst.instanceId, null, 'EXCUSED_DISPLACED', '"' + d.inst.title + '" was locked but now conflicts with a commitment, so it was excused without penalty.', { instanceId: d.inst.instanceId }));
  const planned = S.instances.slice().sort((a, b) => a.startEpoch - b.startEpoch || cmpStr(a.instanceId, b.instanceId));
  for (const inst of planned) if (!inst.explanation) inst.explanation = explainInstance(S, inst, placedMeta.get(inst.instanceId));
  const out = assembleOutput(S, input, {
    planned, history, rejected, passiveReminders: comp.passiveReminders, clarificationPrompts: exp.clarificationPrompts,
    trajectoryWarnings: exp.trajectoryWarnings, questStatus: comp.questStatus, quests: comp.quests, displaced: displaced.map(d => d.inst.instanceId),
    plans: exp.plans, repairLog, t0
  });
  return out;
}

function runLocalRepair(S, targets, placedMeta, maxIter) {
  const log = { attempts: 0, successes: 0, rollbacks: 0, changed: false, moves: [] };
  targets.sort((a, b) => b.tier - a.tier || cmpStr(a.id, b.id));
  for (const t of targets) {
    if (t.isPlaced || S.iterations >= maxIter) continue;
    const el = taskEarliestLatest(S, t);
    if (el.blocked) continue;
    /* Move A: elastic compression inside one wake cycle */
    for (const bw of S.bw) {
      if (t.isPlaced || ++S.iterations > maxIter) break;
      if (bw.endSlot <= el.earliestSlot || bw.startSlot >= el.latestSlot) continue;
      const comp = bw.instances.filter(i => !i.isUserLocked && !i.kept && i.startEpoch > S.now && i.durationSlots > i.minDurationSlots && i.tier <= t.tier && S.taskById.has(i.taskId) && !anchorsDependents(S, i))
        .sort((a, b) => a.tier - b.tier || a.startEpoch - b.startEpoch);
      const freed = comp.reduce((n, i) => n + i.durationSlots - i.minDurationSlots, 0);
      if (freed < t.dMinSlots) continue;
      log.attempts++;
      const z = snapshot(S);
      let ok = true;
      for (const inst of comp) {
        const task = S.taskById.get(inst.taskId), s0 = slotOf(S.g, inst.startEpoch);
        removeInst(S, inst);
        const el2 = taskEarliestLatest(S, task);
        if (el2.blocked) { ok = false; break; }
        const r = checkHard(S, task, bw, s0, task.dMinSlots, el2);
        if (r === R.OK) { commit(S, task, bw.index, s0, task.dMinSlots, { instanceId: inst.instanceId, explanation: null }); }
        else { const e = evalInBwc(S, task, bw, el2, null); if (e.slot < 0) { ok = false; break; } commit(S, task, bw.index, e.slot, e.n, { instanceId: inst.instanceId }); }
      }
      if (ok) {
        const r = evalAcross(S, t, [bw.index]);
        if (r.feasible) {
          const inst = commit(S, t, r.bwc, r.slot, r.n);
          placedMeta.set(inst.instanceId, { phase: 2, move: 'COMPRESSION', score: r.score, count: r.count });
          log.successes++; log.changed = true; log.moves.push({ type: 'COMPRESSION', taskId: t.id, bwcId: bw.id }); break;
        }
      }
      restore(S, z); log.rollbacks++;
    }
    if (t.isPlaced) continue;
    /* Move B: 1-for-1 eviction and relocation (iterate a COPY; spec mutated the list it iterated) */
    const incumbents = S.instances.filter(i => !i.isUserLocked && !i.kept && i.startEpoch > S.now && i.tier <= t.tier && S.taskById.has(i.taskId) && !anchorsDependents(S, i))
      .sort((a, b) => a.tier - b.tier || a.startEpoch - b.startEpoch || cmpStr(a.instanceId, b.instanceId));
    for (const inc of incumbents) {
      if (++S.iterations > maxIter) break;
      log.attempts++;
      const z = snapshot(S);
      const incTask = S.taskById.get(inc.taskId);
      removeInst(S, inc);
      const r = evalAcross(S, t);
      if (r.feasible) {
        const placed = commit(S, t, r.bwc, r.slot, r.n);
        const r2 = evalAcross(S, incTask);
        if (r2.feasible) {
          const moved = commit(S, incTask, r2.bwc, r2.slot, r2.n, { instanceId: inc.instanceId });
          placedMeta.set(placed.instanceId, { phase: 2, move: 'SWAP', score: r.score, count: r.count });
          placedMeta.set(moved.instanceId, { phase: 2, move: 'RELOCATED', score: r2.score, count: r2.count });
          log.successes++; log.changed = true; log.moves.push({ type: 'SWAP', taskId: t.id, relocated: inc.instanceId }); break;
        }
        if (inc.tier <= 2 && t.tier >= 3) {
          incTask.displacedBy = t.id;
          placedMeta.set(placed.instanceId, { phase: 2, move: 'EVICT', score: r.score, count: r.count });
          log.successes++; log.changed = true; log.moves.push({ type: 'EVICT', taskId: t.id, shed: inc.instanceId }); break;
        }
      }
      restore(S, z); log.rollbacks++;
    }
  }
  return log;
}
function fillPass(S, tasks, placedMeta) {
  for (let tier = 4; tier >= 1; tier--) {
    for (let guard = 0; guard < 200; guard++) {
      let best = null, bt = null;
      for (const t of tasks) {
        if (t.isPlaced || t.tier !== tier || t.displacedBy) continue;
        const r = evalAcross(S, t); if (!r.feasible) continue;
        const key = q6(r.score + 0.35 * (r.score - r.second) + 15 / Math.max(1, r.count));
        if (!best || key > best.key || (key === best.key && t.id < bt.id)) { best = Object.assign({ key }, r); bt = t; }
      }
      if (!bt) break;
      const inst = commit(S, bt, best.bwc, best.slot, best.n);
      placedMeta.set(inst.instanceId, { phase: 2, move: 'FILL', score: best.score, count: best.count });
    }
  }
}

function buildRejection(S, t) {
  if (t.displacedBy) return diag(t.id, t, 'DISPLACED_BY_HIGHER_PRIORITY', '"' + t.title + '" made room for higher-priority quest or minimum-dose work this week.');
  if (t.blockedReason) return diag(t.id, t, t.blockedReason, '"' + t.title + '" waits on a prerequisite quest.');
  const el = taskEarliestLatest(S, t);
  if (el.blocked) return diag(t.id, t, el.blocked === R.PREREQ ? 'PREREQUISITE_NOT_SCHEDULED' : 'NEEDS_EARLIER_SESSIONS', '"' + t.title + '" could not be placed because ' + R_TEXT[el.blocked] + '.');
  if (el.latestSlot - el.earliestSlot < t.dMinSlots) return diag(t.id, t, 'DEADLINE_TOO_CLOSE', '"' + t.title + '" has no time left in its window' + (t.deadlineEpoch ? ' before the quest deadline' : ' this week') + '.');
  /* Per-day attribution: each wake cycle in the window gets ONE reason (why that day failed),
     then days are counted per reason. "No time on 6 days, effort cap on 1 day" beats a raw tally. */
  const g = S.g, perReason = {};
  const specific = [R.CAP, R.SPACING, R.FATIGUE, R.POSTSHIFT, R.PERDAY, R.HIGHCOUNT, R.BEFORE_SLEEP, R.CONSEC, R.DOWNSTREAM, R.PRIOR_VOLUME];
  for (const bw of S.bw) {
    if (bw.endSlot <= el.earliestSlot || bw.startSlot >= el.latestSlot) continue;
    const tally = {};
    evalInBwc(S, t, bw, el, tally);
    let code = null, max = 0;
    for (const c of specific) if ((tally[c] || 0) > max) { max = tally[c]; code = c; }
    if (code === null) {
      let run = 0, fits = false;
      for (let i = Math.max(bw.startSlot, el.earliestSlot); i < Math.min(bw.endSlot, el.latestSlot); i++) {
        run = (g.type[i] === SLOT.FREE || g.type[i] === SLOT.RECOVERY) ? run + 1 : 0;
        if (run >= t.dMinSlots) { fits = true; break; }
      }
      code = fits ? R.RECOVERY : R.BUSY;
    }
    perReason[code] = (perReason[code] || 0) + 1;
  }
  const ranked = Object.keys(perReason).map(Number).sort((a, b) => perReason[b] - perReason[a] || a - b);
  const code = ranked.length ? ranked[0] : R.BUSY;
  const names = { 1: 'NO_FREE_WINDOW', 4: 'DAILY_EFFORT_LIMIT', 5: 'ONE_PER_DAY_RULE', 6: 'SPACING_RULE', 7: 'TOO_CLOSE_TO_SLEEP', 8: 'ESTIMATED_FATIGUE',
    9: 'POST_SHIFT_PROTECTION', 10: 'CONSECUTIVE_LOAD_CAP', 11: 'DOWNSTREAM_FATIGUE', 12: 'PREREQUISITE_NOT_SCHEDULED', 13: 'HIGH_INTENSITY_DAILY_CAP',
    14: 'NEEDS_EARLIER_SESSIONS', 15: 'RECOVERY_PROTECTED' };
  const hints = { 4: ' Raise “Max daily effort” in Settings, or choose a lighter intensity.', 6: ' Sessions are kept at least ' + t.spacingMinH + 'h apart.',
    8: ' Energy estimates are heuristics; an energy check-in can override them.', 9: ' Heavy lifting is not scheduled after a long physical shift.',
    15: ' Life Director keeps ' + S.prefs.minDailyRecoveryMinutes + ' minutes of downtime per day and will not trade it for tasks.' };
  const linked = t.linkedQuestRequirementIds.length ? ' (quest: ' + t.linkedQuestRequirementIds.map(x => x.questTitle).join(', ') + ')' : '';
  const dayTxt = c => perReason[c] + ' day' + (perReason[c] === 1 ? '' : 's') + ': ' + R_TEXT[c];
  const why = ranked.length > 1 ? dayTxt(ranked[0]) + '; ' + dayTxt(ranked[1]) : R_TEXT[code];
  return diag(t.id, t, names[code], '"' + t.title + '"' + linked + ' could not be scheduled — ' + why + '.' + (hints[code] || ''), { perDay: perReason });
}

const TIER_TEXT = {
  CRITICAL_DEADLINE: 'its quest deadline is less than a day away', ACTIVE_QUEST: 'it advances an active quest', SIDE_QUEST: 'it is an optional side quest placed in spare capacity',
  WEEKLY_MINIMUM: 'it keeps the goal at its weekly minimum', WEEKLY_TARGET: 'it moves the goal toward its weekly target', STRETCH_VOLUME: 'it is optional extra volume your intensity setting allows',
  RESIDUAL: 'it finishes the rest of a partially completed session', REPLACEMENT: 'it replaces a session that was missed or displaced'
};
const PHASE_TEXT = { WAKE: 'just after waking', PEAK: 'your primary energy peak', DIP: 'your mid-cycle dip', SECONDARY: 'your secondary peak', WIND: 'your wind-down hours' };
function explainInstance(S, inst, meta) {
  const t = S.taskById.get(inst.taskId) || Object.assign({}, inst.source || {}, { dOptSlots: inst.optDurationSlots || inst.durationSlots, phases: (inst.source && inst.source.phases) || ['ANY'] });
  if (inst.kept || inst.isUserLocked) return { tier: inst.tier, summary: inst.isUserLocked ? 'You pinned this time, so Life Director never moves it automatically.' : 'Already started; kept in place.', factors: [], locked: true };
  const bwIdx = S.bw.findIndex(b => b.id === inst.bwcId);
  if (!t._urgBase && t._urgBase !== 0) prepTask(S, t, {});
  const s = slotOf(S.g, inst.startEpoch);
  /* score with the instance temporarily removed from its own neighbourhood is unnecessary: factors read energy before the slot */
  const d = scorePlacement(S, t, S.bw[bwIdx], s, inst.durationSlots, true);
  const factors = Object.entries(d.factors).map(([k, v]) => ({ factor: k, points: Math.round(v * 10) / 10 })).sort((a, b) => b.points - a.points);
  const parts = [];
  const quest = inst.linkedQuestRequirementIds.map(x => x.questTitle);
  let why = TIER_TEXT[t.tierReason] || 'it serves your goals';
  if (t.tierReason === 'ACTIVE_QUEST' && quest.length) why = 'it advances ' + quest.map(q => '“' + q + '”').join(' and ');
  if ((t.tierReason || '').startsWith('WEEKLY') && inst.goalTitle) why = why.replace('the goal', '“' + inst.goalTitle + '”');
  parts.push('Scheduled because ' + why + '.');
  parts.push('This time falls in ' + PHASE_TEXT[d.phase] + ' (estimated energy: body ' + Math.round(d.energyP * 100) + '%, mind ' + Math.round(d.energyC * 100) + '%).');
  if (meta && meta.count && meta.count <= 2) parts.push('Only ' + meta.count + ' day' + (meta.count === 1 ? '' : 's') + ' could fit it, so it was placed before more flexible tasks.');
  if (inst.compressed) parts.push('Shortened to ' + inst.durationSlots * 15 + ' min (from ' + (inst.optDurationSlots * 15) + ') to fit the available time.');
  if (S.spacing.get(inst.spacingKey) && S.spacing.get(inst.spacingKey).length > 1 && t.spacingMinH >= 24) parts.push('Kept at least ' + t.spacingMinH + 'h from similar sessions for recovery.');
  if (quest.length && inst.linkedGoalIds.length && t.tierReason !== 'ACTIVE_QUEST') parts.push('Also counts toward ' + quest.map(q => '“' + q + '”').join(', ') + ' — one session, no duplicate scheduling.');
  if (meta && meta.move === 'SWAP') parts.push('Placed by local repair after swapping with a more flexible task.');
  if (meta && meta.move === 'COMPRESSION') parts.push('Placed by shortening flexible tasks that day.');
  if (d.restDay && t._high) parts.push('It lands on a preferred rest day because no better slot existed.');
  return { tier: inst.tier, tierReason: t.tierReason, summary: parts.join(' '), factors: factors.slice(0, 4), fairness: Math.round(d.F * 100) / 100,
    penalties: d.penalties, selection: meta ? { regret: Math.round((meta.regret || 0) * 10) / 10, feasibleDays: meta.count || null, key: meta.key || null, phase: meta.phase, move: meta.move || null } : null,
    energy: { physical: Math.round(d.energyP * 100) / 100, cognitive: Math.round(d.energyC * 100) / 100 }, wakePhase: d.phase };
}

function segments(S) {
  const g = S.g, out = [];
  let i = 0;
  while (i < g.n) {
    const code = g.type[i];
    let j = i + 1;
    const owner = (code === SLOT.TASK || code === SLOT.LOCKED) ? S.instances.find(x => epochOf(g, i) >= x.startEpoch && epochOf(g, i) < x.endEpoch) : null;
    while (j < g.n && g.type[j] === code && g.tag[j] === g.tag[i] && (!owner || epochOf(g, j) < owner.endEpoch)) j++;
    const blk = g.tag[i] >= 0 ? g.blocks[g.tag[i]] : null;
    out.push({ startEpoch: epochOf(g, i), endEpoch: epochOf(g, j), code: SLOT_NAMES[code], title: owner ? owner.title : code === SLOT.FREE ? 'Free time' : code === SLOT.RECOVERY ? 'Protected downtime' : (blk ? blk.title : SLOT_NAMES[code]),
      kind: owner ? 'TASK' : (blk ? blk.kind : code === SLOT.RECOVERY ? 'RECOVERY' : 'FREE'), instanceId: owner ? owner.instanceId : null });
    i = j;
  }
  return out;
}
function capacityByDay(S) {
  const g = S.g;
  return g.days.map(d => {
    const a = (d.startEpoch - g.H0) / SLOT_SEC, c = { dateKey: d.key, dow: d.dow, freeMin: 0, taskMin: 0, recoveryMin: 0, hardMin: 0, sleepMin: 0, maintMin: 0, otherMin: 0 };
    for (let i = a; i < a + d.slots; i++) {
      const t = g.type[i];
      if (t === SLOT.FREE) c.freeMin += 15; else if (t === SLOT.TASK || t === SLOT.LOCKED) c.taskMin += 15; else if (t === SLOT.RECOVERY) c.recoveryMin += 15;
      else if (t === SLOT.HARD || t === SLOT.COMMUTE || t === SLOT.EMERGENCY || t === SLOT.PERSONAL) c.hardMin += 15; else if (t === SLOT.SLEEP) c.sleepMin += 15; else c.maintMin += 15;
    }
    c.usableMin = c.freeMin + c.taskMin + c.recoveryMin;
    return c;
  });
}
function questCoverage(quests, planned, statusMap) {
  return quests.filter(q => q.status === 'ACTIVE').map(q => {
    const sessions = planned.filter(i => i.linkedQuestRequirementIds.some(x => x.questId === q.id));
    const need = q.requirements.reduce((n, r) => n + Math.max(0, r.targetCount - r.currentCount), 0);
    return { questId: q.id, title: q.title, tier: q.tier, status: statusMap[q.id] || q.status, deadlineEpoch: q.deadlineEpoch, scheduledSessions: sessions.length,
      remainingCount: need, recurrence: q.recurrence, covered: q.schedulingMode === 'NONE' ? null : (q.recurrence === 'DAILY' ? sessions.length > 0 : sessions.length >= need) };
  });
}
function assembleOutput(S, input, x) {
  const recovery = recoveryBlocks(S);
  const validation = validateSchedule({ engineInput: input, planned: x.planned, rejected: x.rejected, recoveryBlocks: recovery, S });
  const cap = capacityByDay(S);
  const goalAlloc = {};
  for (const p of x.plans) {
    const a = goalAlloc[p.goal.id] || (goalAlloc[p.goal.id] = { goalId: p.goal.id, title: p.goal.title, priority: p.goal.priority, targetShare: S.fair.share[p.goal.id] || 0, scheduledMin: 0, plannedSessions: 0, status: p.status, projectionEpoch: p.projection, templates: [] });
    a.templates.push({ templateId: p.template.templateId, title: p.template.title, med: p.fMED, base: p.base, stretch: p.stretch, minutes: p.dOpt });
    if (p.status !== 'ON_TRACK') a.status = p.status;
  }
  for (const i of x.planned) for (const gid of i.linkedGoalIds) if (goalAlloc[gid]) { goalAlloc[gid].scheduledMin += i.durationSlots * 15; goalAlloc[gid].plannedSessions++; }
  const totalGoalMin = Object.values(goalAlloc).reduce((n, a) => n + a.scheduledMin, 0) || 1;
  for (const a of Object.values(goalAlloc)) a.actualShare = a.scheduledMin / totalGoalMin;
  const status = !validation.isValid ? 'INVARIANT_VIOLATION' : (validation.warnings.length ? 'CONSTRAINT_WARNING' : 'VALID_SCHEDULE');
  return {
    engineVersion: ENGINE_VERSION, schemaVersion: SCHEMA_VERSION, status, generatedAtEpoch: S.now,
    computeTimeMs: Math.round((clockMs() - x.t0) * 10) / 10, timezone: S.tz, horizonStartEpoch: S.g.H0, horizonEndEpoch: S.g.HN,
    inputHash: inputHash(input), scheduledInstances: x.planned, historyInstances: x.history, recoveryBlocks: recovery,
    passiveReminders: x.passiveReminders, unscheduledDiagnostics: x.rejected, clarificationPrompts: x.clarificationPrompts,
    trajectoryWarnings: x.trajectoryWarnings, warnings: S.warnings, validation, capacity: cap, segments: segments(S),
    goalAllocation: Object.values(goalAlloc).sort((a, b) => cmpStr(a.goalId, b.goalId)), questCoverage: questCoverage(x.quests, x.planned, x.questStatus),
    displacedLockedIds: x.displaced, repair: x.repairLog ? { attempts: x.repairLog.attempts, successes: x.repairLog.successes, rollbacks: x.repairLog.rollbacks, moves: x.repairLog.moves } : null,
    iterations: S.iterations
  };
}
function inputHash(input) {
  const pick = Object.assign({}, input); delete pick.clock;
  return stableId('in', JSON.stringify(pick, Object.keys(pick).sort()) + JSON.stringify(pick));
}
/* ── Life Director Engine · Part 5: validation & local-ripple rescheduling ── */

/* validateSchedule — re-derives availability from the INPUT (not the engine's working grid),
   so a bug in placement cannot hide itself. Spec §2 invariants + §16 gates. */
function validateSchedule(v) {
  const input = v.engineInput, planned = v.planned || [], rejected = v.rejected || [];
  const d = deriveAvailability({ horizonStartEpoch: input.horizonStartEpoch, horizonDays: input.horizonDays, timezone: input.timezone,
    playerAvailability: input.playerAvailability, fixedCommitments: input.fixedCommitments, playerPreferences: input.playerPreferences });
  const g = d.grid, prefs = d.prefs, now = input.currentTimeEpoch;
  const nowSlot = clamp(slotCeil(g, now + prefs.leadMinutes * 60), 0, g.n);
  const violations = [], warnings = [];
  for (const w of d.warnings) if (['SLEEP_BELOW_FLOOR', 'SLEEP_COMMITMENT_OVERLAP'].includes(w.code)) warnings.push(w);
  const occ = new Int32Array(g.n).fill(-1);
  const byBwc = new Map();
  planned.forEach((inst, idx) => {
    const a = slotOf(g, inst.startEpoch), b = slotCeil(g, inst.endEpoch);
    if (b <= a) violations.push({ code: 'I1', instanceId: inst.instanceId, message: 'Zero-length instance' });
    let bw = -2;
    for (let i = Math.max(0, a); i < Math.min(g.n, b); i++) {
      if (i < nowSlot && inst.startEpoch <= now) { occ[i] = idx; continue; } // already started; time is past
      if (g.type[i] !== SLOT.FREE) { violations.push({ code: 'I1', instanceId: inst.instanceId, message: '"' + inst.title + '" overlaps ' + SLOT_NAMES[g.type[i]] }); break; }
      if (occ[i] >= 0 || occ[i] === -2) { violations.push({ code: 'I1_OVERLAP', instanceId: inst.instanceId, message: '"' + inst.title + '" overlaps "' + planned[occ[i]].title + '"' }); break; }
      occ[i] = idx;
      if (bw === -2) bw = g.bwcOf[i]; else if (g.bwcOf[i] !== bw) { violations.push({ code: 'I1_SLEEP_CROSS', instanceId: inst.instanceId, message: 'Crosses a sleep boundary' }); break; }
    }
    if (bw >= 0) { if (!byBwc.has(bw)) byBwc.set(bw, []); byBwc.get(bw).push(inst); }
    if (inst.isUserLocked) for (let i = Math.max(0, a); i < Math.min(g.n, b); i++) if (occ[i] === idx) occ[i] = -2; // player-pinned: player's call (I_7)
    if (!inst.explanation || !inst.explanation.summary) violations.push({ code: 'I10', instanceId: inst.instanceId, message: 'Missing explanation' });
    const src = inst.source || {};
    if (!inst.isUserLocked && !inst.kept && inst.startEpoch > now) {
      if (src.deadlineEpoch && inst.endEpoch > src.deadlineEpoch) violations.push({ code: 'H_DEADLINE', instanceId: inst.instanceId, message: '"' + inst.title + '" ends after its deadline' });
      if (src.windowEnd && inst.endEpoch > src.windowEnd) violations.push({ code: 'H_WINDOW', instanceId: inst.instanceId, message: '"' + inst.title + '" is outside its week window' });
      for (const dep of src.dependsOn || []) {
        const di = planned.find(p => p.taskId === dep || p.instanceId === 'i' + dep.slice(1));
        if (!di) violations.push({ code: 'I5', instanceId: inst.instanceId, message: 'Prerequisite not scheduled' });
        else if (di.endEpoch + (src.minLagMin || 0) * 60 > inst.startEpoch) violations.push({ code: 'I5', instanceId: inst.instanceId, message: '"' + inst.title + '" starts before its prerequisite ends' });
      }
    }
  });
  /* spacing between auto-placed sessions of the same kind */
  const bySpacing = new Map();
  for (const p of planned) if (!p.isUserLocked && p.source && p.source.spacingMinH > 0) { if (!bySpacing.has(p.spacingKey)) bySpacing.set(p.spacingKey, []); bySpacing.get(p.spacingKey).push(p); }
  for (const [, arr] of bySpacing) {
    arr.sort((a, b) => a.startEpoch - b.startEpoch);
    for (let i = 1; i < arr.length; i++) if (arr[i].startEpoch - arr[i - 1].startEpoch < arr[i].source.spacingMinH * 3600 && arr[i].startEpoch > now && !arr[i].kept)
      violations.push({ code: 'H_SPACING', instanceId: arr[i].instanceId, message: '"' + arr[i].title + '" is closer than ' + arr[i].source.spacingMinH + 'h to a similar session' });
  }
  /* I3 recovery + daily effort cap per wake cycle */
  const need = Math.ceil(prefs.minDailyRecoveryMinutes / 15);
  let bwcOk = 0, bwcCount = 0;
  d.wakeCycles.forEach((bw, bi) => {
    if (bw.endSlot <= nowSlot || bw.truncatedStart) return;
    let free = 0, used = 0;
    for (let i = Math.max(bw.startSlot, nowSlot); i < bw.endSlot; i++) if (g.type[i] === SLOT.FREE) { if (occ[i] === -2) continue; free++; if (occ[i] >= 0) used++; }
    bwcCount++;
    if (free - used < Math.min(need, free)) violations.push({ code: 'I3', bwcId: bw.id, message: 'Wake cycle ' + bw.dateKey + ' keeps only ' + (free - used) * 15 + ' min of downtime (needs ' + Math.min(need, free) * 15 + ')' });
    else bwcOk++;
    if (free < need) warnings.push({ code: 'RECOVERY_SHORTFALL', bwcId: bw.id, message: bw.dateKey + ': your commitments leave only ' + free * 15 + ' free minutes, so all of it is kept as downtime.' });
    const auto = (byBwc.get(bi) || []).filter(x => !x.isUserLocked && !x.kept);
    const mins = (byBwc.get(bi) || []).filter(x => !x.isUserLocked).reduce((n, x) => n + x.durationSlots * 15, 0);
    if (auto.length && mins > prefs.maxDailyGoalMinutes) violations.push({ code: 'H_DAILY_CAP', bwcId: bw.id, message: bw.dateKey + ' exceeds the daily effort limit' });
  });
  /* I7 locked instances preserved */
  for (const e of input.existingSchedule || []) {
    if (!e.isUserLocked || !PLANNED_STATES.includes(e.lifecycleState) || e.endEpoch <= g.H0 || e.startEpoch >= g.HN) continue;
    const p = planned.find(x => x.instanceId === e.instanceId);
    const excused = (v.displacedIds || []).includes(e.instanceId) || rejected.some(r => r.instanceId === e.instanceId);
    if (!excused && (!p || p.startEpoch !== e.startEpoch || p.endEpoch !== e.endEpoch)) violations.push({ code: 'I7', instanceId: e.instanceId, message: 'A pinned activity was moved' });
  }
  for (const r of rejected) if (!r.humanExplanation) violations.push({ code: 'I10', taskId: r.taskId, message: 'Rejected task without explanation' });
  /* quality metrics (SQI reconstructed — the spec's formula was lost in transit; NOTES §D10) */
  const req = planned.filter(p => p.tier >= 3).length, reqMiss = rejected.filter(r => r.tier >= 3).length;
  const all = planned.length, allMiss = rejected.filter(r => r.tier).length;
  const coverageRequired = req + reqMiss ? req / (req + reqMiss) : 1, coverageAll = all + allMiss ? all / (all + allMiss) : 1;
  const recoveryCompliance = bwcCount ? bwcOk / bwcCount : 1;
  const compressed = all ? planned.filter(p => p.compressed).length / all : 0;
  let fairness = 1;
  if (v.S) { const ids = Object.keys(v.S.fair.share); if (ids.length > 1 && v.S.fair.total) fairness = 1 - ids.reduce((n, id) => n + Math.abs((v.S.fair.goalMin[id] || 0) / v.S.fair.total - v.S.fair.share[id]), 0) / 2; }
  const sqi = Math.round((40 * coverageRequired + 15 * coverageAll + 20 * recoveryCompliance + 15 * fairness + 10 * (1 - compressed)) * 10) / 10;
  return { isValid: violations.length === 0, violatedInvariants: violations, warnings,
    qualityMetrics: { sqi, coverageRequired, coverageAll, recoveryCompliance, fairness: Math.round(fairness * 1000) / 1000, compressedFraction: compressed } };
}

/* ── rescheduleActivity (spec §9.4 Local Ripple Containment, §14 #6) ─────────
   trigger.type: MISSED | SKIPPED | RESIDUAL | MOVE | NEW_COMMITMENT | BLOCK_HOURS | BLACKOUT_DAY | SICK | QUEST_CHANGED
   Returns { updatedSchedule, engineInput (with any new commitment/blackout), changeRecord, rejectedMove } */
function rescheduleActivity(req) {
  const t0 = clockMs();
  const trig = req.trigger || {};
  const input = deepClone(req.engineInput);
  const current = req.currentOutput;
  const now = input.currentTimeEpoch;
  const prevPlanned = current.scheduledInstances.map(deepClone);
  let history = (current.historyInstances || []).map(deepClone);
  const toPlace = []; // { source, reason, excludeBwcId, preferBwcEpoch, origInstanceId }
  const actions = [];
  let keepPlanned = prevPlanned.slice();
  const findInst = id => keepPlanned.find(x => x.instanceId === id);
  const gone = new Set();
  const retire = (inst, state, extra) => { gone.add(inst.taskId); keepPlanned = keepPlanned.filter(x => x !== inst); history.push(Object.assign({}, inst, { lifecycleState: state, resolvedAtEpoch: now }, extra || {})); };
  let moved = null, mustBlockWindow = null, shedLowTiers = null;

  switch (trig.type) {
    case 'MISSED': case 'SKIPPED': {
      const inst = findInst(trig.instanceId);
      if (!inst) throw new Error('Instance not found: ' + trig.instanceId);
      retire(inst, trig.type === 'MISSED' ? 'MISSED_UNEXPLAINED' : 'SKIPPED_VOLUNTARY');
      actions.push({ action: trig.type === 'MISSED' ? 'MISS' : 'SKIP', instanceId: inst.instanceId });
      if (inst.tier >= 3 && inst.source) toPlace.push({ source: inst.source, reason: 'REPLACEMENT', origInstanceId: inst.instanceId, excludeBwcId: trig.type === 'SKIPPED' ? inst.bwcId : null, preferBwcEpoch: now });
      break;
    }
    case 'RESIDUAL': toPlace.push({ source: trig.task, reason: 'RESIDUAL', origInstanceId: trig.parentInstanceId, preferBwcEpoch: now }); break;
    case 'MOVE': {
      const inst = findInst(trig.instanceId);
      if (!inst) throw new Error('Instance not found: ' + trig.instanceId);
      const dur = inst.endEpoch - inst.startEpoch;
      moved = Object.assign({}, inst, { startEpoch: trig.newStartEpoch, endEpoch: trig.newStartEpoch + dur, isUserLocked: true, rescheduledAdvance: true,
        moves: (inst.moves || 0) + 1, explanation: { tier: inst.tier, summary: 'You moved this here and it is pinned; Life Director will not move it automatically.', factors: [], locked: true } });
      keepPlanned = keepPlanned.filter(x => x !== inst);
      actions.push({ action: 'MOVE', instanceId: inst.instanceId, from: inst.startEpoch, to: moved.startEpoch });
      break;
    }
    case 'NEW_COMMITMENT': case 'BLOCK_HOURS': case 'BLACKOUT_DAY': case 'SICK': {
      const c = trig.commitment;
      if (trig.type === 'NEW_COMMITMENT') (input.fixedCommitments = input.fixedCommitments || []).push(c);
      else { const av = input.playerAvailability = input.playerAvailability || {}; (av.blackouts = av.blackouts || []).push(Object.assign({ kind: trig.type === 'SICK' ? 'SICK' : 'BLACKOUT' }, c)); }
      mustBlockWindow = c;
      if (trig.type === 'BLOCK_HOURS') shedLowTiers = { until: dayEndAfter(input, c.startEpoch) };
      if (trig.type === 'BLACKOUT_DAY' || trig.type === 'SICK') shedLowTiers = { until: Infinity };
      break;
    }
    case 'QUEST_CHANGED': {
      const qs = new Map((input.activeQuests || []).map(q => { const n = normalizeQuest(q); return [n.id, n]; }));
      for (const inst of keepPlanned.slice()) {
        if (inst.isUserLocked || inst.startEpoch <= now || !inst.linkedQuestRequirementIds.length) continue;
        let changed = false;
        const links = inst.linkedQuestRequirementIds.filter(l => { const q = qs.get(l.questId); const keep = q && q.status === 'ACTIVE'; if (!keep) changed = true; return keep; });
        for (const l of links) { const q = qs.get(l.questId); if (q.deadlineEpoch !== l.deadlineEpoch && q.recurrence !== 'DAILY') { l.deadlineEpoch = q.deadlineEpoch; changed = true; } }
        if (!changed) continue;
        if (!links.length && !inst.linkedGoalIds.length) { retire(inst, 'CANCELLED_BY_QUEST'); actions.push({ action: 'DELETE', instanceId: inst.instanceId, reason: 'QUEST_CLOSED' }); continue; }
        inst.linkedQuestRequirementIds = links;
        const dl = links.reduce((m, l) => l.deadlineEpoch ? Math.min(m, l.deadlineEpoch) : m, Infinity);
        inst.source = Object.assign({}, inst.source, { linkedQuestRequirementIds: links, deadlineEpoch: dl === Infinity ? null : dl });
        if (dl !== Infinity && inst.endEpoch > dl) {
          retire(inst, 'EXCUSED_DISPLACED', { excuseReason: 'QUEST_DEADLINE_CHANGED' });
          const tier = (dl - now) <= 86400 + 3600 ? 4 : Math.max(3, inst.tier);
          toPlace.push({ source: Object.assign({}, inst.source, { tier, tierReason: tier === 4 ? 'CRITICAL_DEADLINE' : inst.source.tierReason, windowEnd: Math.min(inst.source.windowEnd || dl, dl) }), reason: 'REPLACEMENT', origInstanceId: inst.instanceId, preferBwcEpoch: now });
          actions.push({ action: 'EXCUSE', instanceId: inst.instanceId, reason: 'QUEST_DEADLINE_CHANGED' });
        } else actions.push({ action: 'RELINK', instanceId: inst.instanceId });
      }
      break;
    }
    default: throw new Error('Unknown trigger ' + trig.type);
  }
  /* displacement by new blocks: anything (even pinned — emergency exception to I_7) now overlapping hard time */
  const holdSchedule = keepPlanned.map(x => Object.assign({}, x, { _hold: true }));
  if (moved) holdSchedule.unshift(moved);
  const probe = prepareStateWithHold(input, holdSchedule);
  let S = probe.S;
  if (moved && probe.displaced.some(d => d.inst.instanceId === moved.instanceId)) {
    return { rejectedMove: { reason: 'BLOCKED_TIME', message: 'That time overlaps sleep, work, a meal or another fixed block.' }, updatedSchedule: current, engineInput: req.engineInput, changeRecord: null };
  }
  if (moved && !S.instById.get(moved.instanceId)) {
    return { rejectedMove: { reason: 'OUTSIDE_PLAN', message: 'That time is outside the current planning window.' }, updatedSchedule: current, engineInput: req.engineInput, changeRecord: null };
  }
  if (moved) {
    const bw = S.bw.find(b => b.id === S.instById.get(moved.instanceId).bwcId);
    if (bw && bw.recoverySlots * 15 < S.prefs.minDailyRecoveryMinutes && !bw.truncatedStart) {
      return { rejectedMove: { reason: 'RECOVERY_PROTECTED', message: 'Moving it there would cut that day below your minimum downtime.' }, updatedSchedule: current, engineInput: req.engineInput, changeRecord: null };
    }
  }
  /* blocks that already ended but are not resolved yet (still inside the miss grace window, or waiting
     their own MISSED repair) sit before the new horizon, so the hold pass cannot see them. Carry them
     through untouched; otherwise each repair silently erased every other unresolved block. */
  const carryPast = keepPlanned.filter(x => x !== moved && x.endEpoch <= probe.S.g.H0 && PLANNED_STATES.includes(x.lifecycleState));
  for (const d of probe.displaced) {
    const inst = d.inst;
    gone.add(inst.taskId);
    history.push(Object.assign({}, inst, { lifecycleState: 'EXCUSED_DISPLACED', resolvedAtEpoch: now, excuseReason: trig.type }));
    actions.push({ action: 'EXCUSE', instanceId: inst.instanceId, reason: trig.type });
    const shed = shedLowTiers && inst.tier <= 2 && inst.startEpoch < shedLowTiers.until;
    if (!shed && inst.source) toPlace.push({ source: inst.source, reason: 'REPLACEMENT', origInstanceId: inst.instanceId, preferBwcEpoch: inst.startEpoch });
    else if (shed) actions.push({ action: 'SHED', instanceId: inst.instanceId, reason: 'LOW_TIER_AFTER_DISRUPTION' });
  }
  /* I_5 cascade: anything that depends on a task that just left the plan leaves too and is re-placed after it */
  for (let changed = true, guard = 0; changed && guard < 20; guard++) {
    changed = false;
    for (const inst of S.instances.slice()) {
      if (inst.kept || inst.isUserLocked || !inst.source || !(inst.source.dependsOn || []).some(d => gone.has(d))) continue;
      removeInst(S, inst); gone.add(inst.taskId); changed = true;
      history.push(Object.assign({}, inst, { lifecycleState: 'EXCUSED_DISPLACED', resolvedAtEpoch: now, excuseReason: 'PREREQUISITE_MOVED' }));
      actions.push({ action: 'EXCUSE', instanceId: inst.instanceId, reason: 'PREREQUISITE_MOVED' });
      toPlace.push({ source: inst.source, reason: 'REPLACEMENT', origInstanceId: inst.instanceId, preferBwcEpoch: inst.startEpoch });
    }
  }
  if (trig.type === 'SICK' && mustBlockWindow) for (const tp of toPlace) tp.notBefore = mustBlockWindow.endEpoch;

  /* place each displaced/replacement task by containment radius */
  const placedMeta = new Map(), rejected = [], radiusUsed = [];
  /* new ids first, so dependents can be re-pointed at their prerequisite's replacement */
  const newId = new Map();
  toPlace.forEach((tp, k) => { tp._newId = stableId('t', (tp.origInstanceId || tp.source.id) + ':r:' + k + ':' + now); if (tp.source.id) newId.set(tp.source.id, tp._newId); });
  const ids = new Set(toPlace.map(tp => tp.source.id));
  const depth = tp => { let d = 0, cur = [tp], seen = new Set(); while (cur.length && d < 20) { const nx = []; for (const c of cur) for (const dp of c.source.dependsOn || []) if (ids.has(dp) && !seen.has(dp)) { seen.add(dp); nx.push(toPlace.find(x => x.source.id === dp)); } if (!nx.length) break; d++; cur = nx; } return d; };
  toPlace.sort((a, b) => depth(a) - depth(b) || (b.source.tier || 0) - (a.source.tier || 0) || cmpStr(a.source.id || '', b.source.id || ''));
  for (const tp of toPlace) {
    const t = makeTask(Object.assign({}, tp.source, { id: tp._newId, tierReason: tp.reason === 'RESIDUAL' ? 'RESIDUAL' : (tp.source.tierReason || 'REPLACEMENT') }));
    if (tp.notBefore) t.windowStart = Math.max(t.windowStart || now, tp.notBefore);
    t.windowStart = Math.max(t.windowStart || now, now);
    if (!t.windowEnd || t.windowEnd <= t.windowStart) t.windowEnd = S.g.HN;
    /* never drop a dependency: re-point it at the replacement, or leave it unresolved so the task is held back */
    t.dependsOn = (t.dependsOn || []).map(dp => newId.get(dp) || dp);
    S.tasks.push(t); S.taskById.set(t.id, t); prepTask(S, t, goalInfoMap(input.goals, now));
    const result = placeByRadius(S, t, tp, placedMeta);
    radiusUsed.push({ taskId: t.id, title: t.title, radius: result.radius, origInstanceId: tp.origInstanceId });
    if (!t.isPlaced) { const r = buildRejection(S, t); r.humanExplanation += result.radius === 4 && t.tier <= 2 ? ' It was dropped for this week to keep your other plans stable.' : ''; rejected.push(r); }
    for (const a of result.actions) actions.push(a);
  }
  /* assemble */
  const inPlan = new Set(S.instances.map(x => x.instanceId));
  const planned = S.instances.map(x => { const c = Object.assign({}, x); delete c._hold; return c; })
    .concat(carryPast.filter(x => !inPlan.has(x.instanceId) && !gone.has(x.taskId)))
    .sort((a, b) => a.startEpoch - b.startEpoch || cmpStr(a.instanceId, b.instanceId));
  for (const inst of planned) if (!inst.explanation || placedMeta.has(inst.instanceId)) inst.explanation = explainInstance(S, inst, placedMeta.get(inst.instanceId));
  const exp = expandGoalActivities(input.goals || [], S.prefs, now);
  const qs = (input.activeQuests || []).map(normalizeQuest);
  const out = assembleOutput(S, input, { planned, history, rejected: rejected.concat((current.unscheduledDiagnostics || []).filter(r => !planned.some(p => p.taskId === r.taskId))),
    passiveReminders: current.passiveReminders || [], clarificationPrompts: current.clarificationPrompts || [], trajectoryWarnings: current.trajectoryWarnings || [],
    questStatus: {}, quests: qs, displaced: probe.displaced.map(d => d.inst.instanceId), plans: exp.plans, repairLog: null, t0 });
  /* churn: previously planned future instances (other than the trigger's) whose time changed or vanished */
  const prevFuture = prevPlanned.filter(p => p.startEpoch > now && p.instanceId !== trig.instanceId);
  const excused = new Set(probe.displaced.map(d => d.inst.instanceId));
  const shedIds = new Set(actions.filter(a => a.action === 'SHED').map(a => a.instanceId));
  /* displacement = start moved or block removed; compression = same start, shorter (Radius 3) */
  const collateral = prevFuture.filter(p => { if (excused.has(p.instanceId)) return false; const n = planned.find(x => x.instanceId === p.instanceId); return !n || n.startEpoch !== p.startEpoch; });
  const compressedIds = prevFuture.filter(p => { const n = planned.find(x => x.instanceId === p.instanceId); return n && n.startEpoch === p.startEpoch && n.endEpoch !== p.endEpoch; }).map(p => p.instanceId);
  void shedIds;
  const changeRecord = {
    changeId: stableId('chg', trig.type + ':' + (trig.instanceId || '') + ':' + now + ':' + actions.length), timestampEpoch: now,
    triggerReason: { MISSED: 'MISSED_TASK_REPAIR', SKIPPED: 'USER_SKIP', RESIDUAL: 'PARTIAL_COMPLETION', MOVE: 'USER_MANUAL_MOVE', NEW_COMMITMENT: 'NEW_COMMITMENT_ADDED',
      BLOCK_HOURS: 'TIME_BLOCKED', BLACKOUT_DAY: 'DAY_BLACKOUT', SICK: 'SICK_MODE_ACTIVATED', QUEST_CHANGED: 'QUEST_MODIFIED' }[trig.type],
    actions, radiusUsed, affectedInstanceIds: [...new Set(actions.map(a => a.instanceId).filter(Boolean))],
    churn: { collateralMoved: collateral.length, compressed: compressedIds.length, futureInstances: prevFuture.length, ratio: prevFuture.length ? collateral.length / prevFuture.length : 0 },
    computeTimeMs: Math.round((clockMs() - t0) * 10) / 10
  };
  out.computeTimeMs = changeRecord.computeTimeMs;
  return { updatedSchedule: out, engineInput: input, changeRecord, rejectedMove: null };
}
function dayEndAfter(input, epoch) {
  const tz = input.timezone || (input.playerAvailability || {}).timezone || 'UTC';
  const p = localParts(epoch, tz), n = ymdAdd({ y: p.y, mo: p.mo, d: p.d }, 1);
  return zonedToEpoch(n.y, n.mo, n.d, 0, tz);
}
/* Like prepareState, but held instances are committed as movable TASK slots with live task objects,
   so Radius 3/4 can compress or evict them. Pinned/started instances stay LOCKED. */
function prepareStateWithHold(input, holdSchedule) {
  const inp = Object.assign({}, input, { existingSchedule: [] });
  const derived = deriveAvailability({ horizonStartEpoch: inp.horizonStartEpoch, horizonDays: inp.horizonDays, timezone: inp.timezone,
    playerAvailability: inp.playerAvailability, fixedCommitments: inp.fixedCommitments, playerPreferences: inp.playerPreferences });
  const S = buildState(inp, derived);
  const g = S.g, displaced = [];
  const gi = goalInfoMap(input.goals, S.now);
  const ordered = holdSchedule.slice().sort((a, b) => (b.isUserLocked ? 1 : 0) - (a.isUserLocked ? 1 : 0) || a.startEpoch - b.startEpoch || cmpStr(a.instanceId, b.instanceId));
  for (const inst of ordered) {
    if (inst.endEpoch <= g.H0 || inst.startEpoch >= g.HN) continue;
    const a = clamp(slotOf(g, inst.startEpoch), 0, g.n), b = clamp(slotCeil(g, inst.endEpoch), 0, g.n);
    let clash = false;
    for (let i = Math.max(a, S.nowSlot); i < b; i++) if (g.type[i] !== SLOT.FREE) { clash = true; break; }
    const bwi = bwcIndexAt(S, Math.max(inst.startEpoch, S.now));
    if ((clash && inst.endEpoch > S.now) || bwi < 0) { displaced.push({ inst }); continue; }
    const locked = inst.isUserLocked || inst.startEpoch <= S.now;
    const copy = Object.assign({}, inst, { bwcId: S.bw[bwi].id, kept: locked });
    if (!locked && inst.source) {
      const t = makeTask(Object.assign({}, inst.source, { id: inst.taskId || inst.source.id }));
      prepTask(S, t, gi); t.isPlaced = true; copy.taskId = t.id;
      S.tasks.push(t); S.taskById.set(t.id, t);
    }
    occupy(S, copy, bwi, locked ? SLOT.LOCKED : SLOT.TASK);
  }
  reserveRecovery(S);
  /* Recovery beats held tasks: if a disruption shrank a day so that protected downtime fell below
     the minimum, excuse that day's lowest-tier movable tasks until downtime is restored. */
  const need = Math.ceil(S.prefs.minDailyRecoveryMinutes / 15);
  for (const bw of S.bw) {
    if (bw.truncatedStart || bw.endSlot <= S.nowSlot) continue;
    let guard = 0;
    while (guard++ < 20) {
      const movable = bw.instances.filter(i => !i.kept && !i.isUserLocked && i.startEpoch > S.now);
      const heldSlots = movable.reduce((n, i) => n + i.durationSlots, 0);
      if (bw.recoverySlots >= Math.min(need, bw.freeSlots + heldSlots) || !movable.length) break;
      const victim = movable.slice().sort((a, b) => a.tier - b.tier || b.startEpoch - a.startEpoch || cmpStr(a.instanceId, b.instanceId))[0];
      removeInst(S, victim);
      const orig = holdSchedule.find(h => h.instanceId === victim.instanceId) || victim;
      displaced.push({ inst: orig, reason: 'RECOVERY_PROTECTION' });
      for (let i = bw.startSlot; i < bw.endSlot; i++) if (g.type[i] === SLOT.RECOVERY) g.type[i] = SLOT.FREE;
      reserveRecoveryFor(S, bw);
    }
  }
  return { S, displaced };
}
function placeByRadius(S, t, tp, placedMeta) {
  const actions = [];
  /* I_8: inside the 24h freeze zone nothing moves — except to rescue a critical (Tier 4) deadline */
  const freezeEnd = (t.tier || 0) >= 4 ? S.now : S.now + 24 * 3600;
  const allowed = S.bw.filter(b => b.id !== tp.excludeBwcId).map(b => b.index);
  const done = (r, radius, extra) => {
    const inst = commit(S, t, r.bwc, r.slot, r.n);
    placedMeta.set(inst.instanceId, Object.assign({ phase: 'R' + radius, score: r.score, count: r.count }, extra || {}));
    actions.push({ action: 'INSERT', instanceId: inst.instanceId, radius, replaces: tp.origInstanceId || null, to: inst.startEpoch });
    return { radius, actions };
  };
  /* Radius 1: same wake cycle */
  const homeIdx = bwcIndexAt(S, Math.max(tp.preferBwcEpoch || S.now, S.now));
  if (homeIdx >= 0 && allowed.includes(homeIdx)) { const r = evalAcross(S, t, [homeIdx]); if (r.feasible) return done(r, 1); }
  /* Radius 2: anywhere in the horizon window, zero displacement */
  { const r = evalAcross(S, t, allowed); if (r.feasible) return done(r, 2); }
  /* Radius 3: one wake cycle outside the freeze zone; compress at most 2 flexible tasks */
  for (const bw of S.bw) {
    if (!allowed.includes(bw.index) || S.iterations++ > 600) continue;
    const comp = bw.instances.filter(i => !i.kept && !i.isUserLocked && i.startEpoch > freezeEnd && i.durationSlots > i.minDurationSlots && S.taskById.has(i.taskId) && !anchorsDependents(S, i))
      .sort((a, b) => a.tier - b.tier || a.startEpoch - b.startEpoch).slice(0, 2);
    if (!comp.length || comp.reduce((n, i) => n + i.durationSlots - i.minDurationSlots, 0) < 1) continue;
    const z = snapshot(S);
    const before = comp.map(c => ({ id: c.instanceId, dur: c.durationSlots }));
    let ok = true;
    for (const inst of comp) {
      const task = S.taskById.get(inst.taskId), s0 = slotOf(S.g, inst.startEpoch);
      removeInst(S, inst);
      const el2 = taskEarliestLatest(S, task);
      if (el2.blocked || checkHard(S, task, bw, s0, task.dMinSlots, el2) !== R.OK) { ok = false; break; }
      commit(S, task, bw.index, s0, task.dMinSlots, { instanceId: inst.instanceId, explanation: inst.explanation, kept: false });
    }
    if (ok) { const r = evalAcross(S, t, [bw.index]); if (r.feasible) { for (const b of before) actions.push({ action: 'COMPRESS', instanceId: b.id, fromSlots: b.dur }); return done(r, 3, { move: 'COMPRESSION' }); } }
    restore(S, z);
  }
  /* Radius 4: low tiers are shed; Tier 3–4 may evict one Tier ≤2 task outside the freeze zone */
  if ((t.tier || 0) <= 2) return { radius: 4, actions };
  const victims = S.instances.filter(i => !i.kept && !i.isUserLocked && i.tier < t.tier && i.tier <= 3 && i.startEpoch > freezeEnd && S.taskById.has(i.taskId) && !anchorsDependents(S, i))
    .sort((a, b) => a.tier - b.tier || b.startEpoch - a.startEpoch || cmpStr(a.instanceId, b.instanceId));
  /* try single evictions, then adjacent pairs in the same wake cycle */
  const sets = victims.map(v => [v]);
  for (let a = 0; a < victims.length; a++) for (let b = a + 1; b < victims.length; b++) if (victims[a].bwcId === victims[b].bwcId) sets.push([victims[a], victims[b]]);
  for (const set of sets) {
    if (S.iterations++ > 900) break;
    const z = snapshot(S);
    const vts = set.map(v => S.taskById.get(v.taskId));
    for (const v of set) removeInst(S, v);
    const r = evalAcross(S, t, allowed);
    if (r.feasible) {
      const res = done(r, 4, { move: 'EVICT' });
      set.forEach((v, k) => {
        const r2 = evalAcross(S, vts[k]);
        if (r2.feasible) { const m = commit(S, vts[k], r2.bwc, r2.slot, r2.n, { instanceId: v.instanceId }); placedMeta.set(m.instanceId, { phase: 'R4', move: 'RELOCATED', score: r2.score, count: r2.count }); actions.push({ action: 'MOVE', instanceId: v.instanceId, from: v.startEpoch, to: m.startEpoch }); }
        else actions.push({ action: 'SHED', instanceId: v.instanceId, reason: 'DISPLACED_BY_HIGHER_PRIORITY' });
      });
      return res;
    }
    restore(S, z);
  }
  return { radius: 4, actions };
}
/* ── Life Director Engine · Part 6: completion ledger & consequences ─────────
   Life Director COORDINATES; it does not award XP. It emits immutable CompletionEvents and a
   suggested effort amount. The host XP engine stays authoritative (brief §4; NOTES §D11). */

function emptyLedger(playerId) {
  return { schemaVersion: SCHEMA_VERSION, playerId: String(playerId || 'player'), events: {}, order: [], bounties: {}, penalties: {},
    restTokens: 1, completionsSinceToken: 0, consecutiveMisses: 0, consequencesByBwc: {}, regroupActive: false, streaks: {} };
}
/* Event identity: scheduled → one event per instance, forever (spec §14.1 included a 15-min time
   bucket, which would let the same instance pay out twice if completed in two buckets).
   Ad-hoc → a durable client id minted once when the log is created (NOT tag+time-bucket, which
   would merge two legitimate sessions). NOTES §D6. */
function completionEventId(playerId, instanceId, clientEventId, revision) {
  if (instanceId) return stableId('ev', 'DONE:' + playerId + ':' + instanceId + (revision ? ':rev' + revision : ''));
  if (!clientEventId) throw new Error('Ad-hoc completions need a clientEventId');
  return stableId('ev', 'ADHOC:' + playerId + ':' + clientEventId);
}
const COMPLETABLE = ['PLANNED', 'IN_PROGRESS', 'MISSED_UNEXPLAINED', 'EXCUSED_BY_TOKEN'];
/* An event whose host-side record was undone (e.g. deleted in the LifeMax Log) is revoked, never deleted:
   the id stays burned forever, and a redo uses a new revision id. */
function revokeEvent(ledgerIn, eventId, reason, nowEpoch) {
  const ledger = deepClone(ledgerIn);
  if (!ledger.events[eventId]) return { ledger: ledgerIn, revoked: false };
  ledger.revoked = ledger.revoked || {};
  if (ledger.revoked[eventId]) return { ledger: ledgerIn, revoked: false };
  ledger.revoked[eventId] = { reason: reason || 'HOST_UNDO', atEpoch: nowEpoch || 0 };
  return { ledger, revoked: true };
}

function recordCompletion(ci) {
  const ledger = ci.ledger ? deepClone(ci.ledger) : emptyLedger(ci.playerId);
  const inst = ci.instance || null;
  const playerId = ledger.playerId;
  const eventId = completionEventId(playerId, inst && inst.instanceId, ci.clientEventId, inst && inst.revision);
  if (ledger.events[eventId]) return { applied: false, reason: 'DUPLICATE_EVENT_ID', eventId, ledger: ci.ledger };
  if (inst && !COMPLETABLE.includes(inst.lifecycleState)) return { applied: false, reason: 'NOT_COMPLETABLE_' + inst.lifecycleState, eventId, ledger: ci.ledger };
  const actualMin = Math.max(1, Math.round(ci.actualDurationMinutes));
  const schedMin = inst ? Math.round((inst.endEpoch - inst.startEpoch) / 60) : actualMin;
  const ratio = clamp(actualMin / schedMin, 0, 1);
  const partial = !!inst && ratio < (ci.satisfiedRatio || 0.75);
  const start = ci.actualStartEpoch || (inst ? inst.startEpoch : ci.nowEpoch);
  let state = 'COMPLETED_ON_TIME';
  if (partial) state = 'PARTIALLY_COMPLETED';
  else if (inst && start + actualMin * 60 <= inst.startEpoch) state = 'COMPLETED_EARLY';
  else if (inst && start >= inst.endEpoch) state = 'COMPLETED_LATE';
  const src = inst || ci.adhoc || {};
  const IP = src.IP || tagProfile(src.tag).IP, IC = src.IC || tagProfile(src.tag).IC;
  const event = Object.freeze({
    eventId, playerId, instanceId: inst ? inst.instanceId : null, clientEventId: inst ? null : ci.clientEventId,
    activityTypeTag: src.tag || 'general', templateId: src.templateId || null, spacingKey: src.spacingKey || null, title: src.title || 'Activity',
    linkedGoalIds: Object.freeze((src.linkedGoalIds || []).slice()), linkedQuestRequirementIds: Object.freeze((src.linkedQuestRequirementIds || []).map(x => Object.freeze({ questId: x.questId, reqId: x.reqId }))),
    actualStartEpoch: start, actualDurationMinutes: actualMin, scheduledDurationMinutes: schedMin, completionRatio: Math.round(ratio * 100) / 100,
    lifecycleState: state, intensityRating: clamp(Math.round(ci.rpe || Math.max(IP, IC)), 1, 5), recordedAtEpoch: ci.nowEpoch, source: inst ? 'SCHEDULE' : 'ADHOC',
    suggestedEffort: Object.freeze({ minutes: actualMin, IP, IC, xpHint: Math.round(actualMin * (1 + 0.1 * (Math.max(IP, IC) - 1))) })
  });
  ledger.events[eventId] = event; ledger.order.push(eventId);
  /* rest tokens: +1 per 5 completions, max 3 (spec §9.3) */
  ledger.completionsSinceToken++;
  let tokenEarned = false;
  if (ledger.completionsSinceToken >= 5) { ledger.completionsSinceToken = 0; if (ledger.restTokens < 3) { ledger.restTokens++; tokenEarned = true; } }
  ledger.consecutiveMisses = 0; ledger.regroupActive = false;
  if (event.spacingKey && !partial) ledger.streaks[event.spacingKey] = (ledger.streaks[event.spacingKey] || 0) + 1;
  /* late completion of a missed task cancels its miss (spec §9.2 #2) and refunds a spent token */
  if (inst && inst.lifecycleState === 'EXCUSED_BY_TOKEN') ledger.restTokens = Math.min(3, ledger.restTokens + 1);
  let residualTask = null;
  if (partial && inst && inst.source && (inst.tier >= 3 || inst.linkedQuestRequirementIds.length)) {
    const rem = ceilTo15(schedMin - actualMin);
    residualTask = Object.assign({}, inst.source, { id: stableId('t', 'residual:' + inst.instanceId), title: inst.title + ' (finish)', dOpt: rem, dMin: rem, dMax: rem,
      tierReason: 'RESIDUAL', windowStart: ci.nowEpoch, parentInstanceId: inst.instanceId });
  }
  compactLedger(ledger, ci.nowEpoch);
  return { applied: true, eventId, event, ledger, lifecycleState: state, residualTask, tokenEarned };
}
/* Keep ids for 400 days (spec said 90; instances older than that can no longer be completed, so
   this window is strictly safer). Older event bodies are dropped, ids are kept in a compact list. */
function compactLedger(ledger, now) {
  const cutoff = now - 400 * 86400;
  if (ledger.order.length < 2000) return;
  ledger.archivedIds = ledger.archivedIds || [];
  ledger.order = ledger.order.filter(id => { const e = ledger.events[id]; if (e && e.recordedAtEpoch < cutoff) { delete ledger.events[id]; ledger.archivedIds.push(id); return false; } return true; });
}
function ledgerHas(ledger, eventId) { return !!(ledger.events[eventId] || (ledger.archivedIds || []).includes(eventId)); }

/* Broadcast one event to quests and goals exactly once each (spec §5.3 Streams 2–3). */
function reconcileQuestProgress(event, quests, goals, futureInstances, opts) {
  opts = opts || {};
  const updatedQuests = [], questBountyRewards = [], questDeltas = [];
  const satisfied = event.completionRatio >= (opts.satisfiedRatio || 0.75);
  for (const raw of quests || []) {
    const q = normalizeQuest(raw);
    const out = deepClone(raw);
    if (q.status !== 'ACTIVE' || q.appliedEventIds.includes(event.eventId)) { updatedQuests.push(out); continue; }
    let touched = false;
    const reqs = q.requirements.map(r => {
      const linked = event.linkedQuestRequirementIds.some(x => x.questId === q.id && x.reqId === r.id);
      const matches = linked || (!q.exclusive && tagMatches(r.tag, event.activityTypeTag));
      if (!matches) return r;
      const nr = Object.assign({}, r);
      if (nr.targetMinutes) { nr.currentMinutes = Math.min(nr.targetMinutes, nr.currentMinutes + event.actualDurationMinutes); touched = true; }
      if (nr.targetCount && satisfied && event.actualDurationMinutes >= (nr.minSessionMinutes || 0)) { nr.currentCount = Math.min(nr.targetCount, nr.currentCount + 1); touched = true; }
      return nr;
    });
    if (!touched) { updatedQuests.push(out); continue; }
    out.appliedEventIds = (q.appliedEventIds || []).concat(event.eventId).slice(-500);
    if (Array.isArray(raw.requirements) && raw.requirements.length) out.requirements = raw.requirements.map((r, i) => Object.assign({}, r, { currentCount: reqs[i].currentCount, currentMinutes: reqs[i].currentMinutes }));
    else { out.progressCount = reqs[0].currentCount; out.progressMinutes = reqs[0].currentMinutes; }
    const done = reqs.every(r => (!r.targetCount || r.currentCount >= r.targetCount) && (!r.targetMinutes || r.currentMinutes >= r.targetMinutes));
    questDeltas.push({ questId: q.id, title: q.title, eventId: event.eventId, before: q.requirements.map(r => [r.currentCount, r.currentMinutes]), after: reqs.map(r => [r.currentCount, r.currentMinutes]) });
    if (done && q.recurrence !== 'DAILY') {
      out.status = 'COMPLETED'; out.completedEventId = event.eventId;
      questBountyRewards.push({ bountyId: stableId('bounty', 'QUEST_BOUNTY:' + q.id), questId: q.id, title: q.title, bounty: q.bounty || null });
    } else if (done) out.completedForDayKey = opts.dayKey || null;
    updatedQuests.push(out);
  }
  const updatedGoals = (goals || []).map(raw => {
    const g = deepClone(raw);
    const linked = event.linkedGoalIds.includes(String(g.id || g.goalId));
    const tagHit = !linked && event.source === 'ADHOC' && goalTemplates(normalizeGoal(raw)).some(t => tagMatches(t.tag, event.activityTypeTag) || tagMatches(event.activityTypeTag, t.tag));
    if (!(linked || tagHit)) return g;
    g.appliedEventIds = g.appliedEventIds || [];
    if (g.appliedEventIds.includes(event.eventId)) return g;
    g.appliedEventIds = g.appliedEventIds.concat(event.eventId).slice(-500);
    g.sessionsCompleted = (g.sessionsCompleted || 0) + (satisfied ? 1 : 0);
    g.minutesCompleted = (g.minutesCompleted || 0) + event.actualDurationMinutes;
    if (g.target && g.target.unitsPerHour) g.target = Object.assign({}, g.target, { completed: Math.min(g.target.units, (g.target.completed || 0) + event.actualDurationMinutes * g.target.unitsPerHour / 60) });
    return g;
  });
  /* prune future blocks of completed quests (spec §5.4) */
  const completedIds = new Set(updatedQuests.filter(q => q.status === 'COMPLETED').map(q => String(q.id || q.questId)));
  const pruned = [], removedInstanceIds = [];
  for (const inst of futureInstances || []) {
    if (inst.lifecycleState !== 'PLANNED' || inst.instanceId === event.instanceId) { pruned.push(inst); continue; }
    const links = inst.linkedQuestRequirementIds.filter(l => !completedIds.has(l.questId));
    if (links.length === inst.linkedQuestRequirementIds.length) { pruned.push(inst); continue; }
    if (!links.length && !inst.linkedGoalIds.length && !inst.isUserLocked) { removedInstanceIds.push(inst.instanceId); continue; }
    const c = deepClone(inst); c.linkedQuestRequirementIds = links;
    if (!links.length && !inst.linkedGoalIds.length) c.orphanWarning = true;
    if (c.source) c.source.linkedQuestRequirementIds = links;
    pruned.push(c);
  }
  return { updatedQuests, updatedGoals, questBountyRewards, prunedScheduleInstances: pruned, removedInstanceIds, questDeltas };
}

/* Misses: PLANNED instances whose end + grace has passed (spec table §9.2 #3). */
function detectMisses(instances, nowEpoch, graceMin) {
  return (instances || []).filter(i => i.lifecycleState === 'PLANNED' && i.endEpoch + (graceMin || 60) * 60 < nowEpoch);
}
/* Bounded, non-destructive consequence policy with circuit breakers (spec §9.3; NOTES §D12). */
function applyMissConsequence(ledgerIn, inst, prefs, nowEpoch, kind) {
  const ledger = deepClone(ledgerIn);
  const p = normalizePreferences(prefs);
  const penaltyId = stableId('pen', 'MISS_PENALTY:' + inst.instanceId);
  const res = { ledger, penaltyId, state: kind === 'SKIP' ? 'SKIPPED_VOLUNTARY' : 'MISSED_UNEXPLAINED', consequence: null, regroupPrompt: false, note: null };
  if (ledger.penalties[penaltyId]) { res.note = 'ALREADY_PROCESSED'; res.ledger = ledgerIn; return res; }
  if (kind !== 'SKIP') {
    ledger.consecutiveMisses++;
    if (ledger.consecutiveMisses >= 3 && !ledger.regroupActive) { ledger.regroupActive = true; res.regroupPrompt = true; }
  }
  const record = c => { ledger.penalties[penaltyId] = { instanceId: inst.instanceId, atEpoch: nowEpoch, consequence: c }; };
  if (p.consequenceMode === 'LENIENT') { record(null); res.note = 'LENIENT_MODE'; return res; }
  if (inst.tier <= 2) { record(null); res.note = 'ABOVE_MINIMUM_NO_PENALTY'; return res; }
  if (ledger.regroupActive && !res.regroupPrompt) { record(null); res.note = 'TACTICAL_REGROUP_PAUSED'; return res; }
  if (ledger.consequencesByBwc[inst.bwcId]) { record(null); res.note = 'ONE_PER_DAY_LIMIT'; return res; }
  ledger.consequencesByBwc[inst.bwcId] = penaltyId;
  if (ledger.restTokens > 0) {
    ledger.restTokens--; res.state = kind === 'SKIP' ? 'SKIPPED_VOLUNTARY' : 'EXCUSED_BY_TOKEN';
    res.consequence = { type: 'REST_TOKEN_SPENT', tokensLeft: ledger.restTokens }; record(res.consequence); return res;
  }
  if (inst.spacingKey) ledger.streaks[inst.spacingKey] = Math.max(0, (ledger.streaks[inst.spacingKey] || 0) - 1);
  res.consequence = p.consequenceMode === 'HARDCORE_RPG' ? { type: 'HP_DRAIN', amount: 10, floorHp: 1, hostApplied: false } : { type: 'STREAK_DECAY', key: inst.spacingKey };
  record(res.consequence);
  return res;
}
/* Allowed lifecycle transitions — anything else is refused. */
const TRANSITIONS = {
  PLANNED: ['IN_PROGRESS', 'COMPLETED_EARLY', 'COMPLETED_ON_TIME', 'COMPLETED_LATE', 'PARTIALLY_COMPLETED', 'SKIPPED_VOLUNTARY', 'MISSED_UNEXPLAINED', 'EXCUSED_DISPLACED', 'EXCUSED_BY_TOKEN', 'CANCELLED_BY_QUEST'],
  IN_PROGRESS: ['COMPLETED_ON_TIME', 'COMPLETED_LATE', 'COMPLETED_EARLY', 'PARTIALLY_COMPLETED', 'EXCUSED_DISPLACED'],
  MISSED_UNEXPLAINED: ['COMPLETED_LATE', 'EXCUSED_BY_TOKEN', 'PARTIALLY_COMPLETED'],
  EXCUSED_BY_TOKEN: ['COMPLETED_LATE', 'PARTIALLY_COMPLETED']
};
function canTransition(from, to) { return (TRANSITIONS[from] || []).includes(to); }
/* ── Life Director Engine · Part 7: seeded simulation scenarios (Simulation Lab) ── */
const WEEKDAYS = [1, 2, 3, 4, 5];
function scenarioBase(seed, tz, start) {
  const rng = mulberry32(seed >>> 0);
  const j = (min, range) => min + Math.round(((rng() * 2) - 1) * range / 15) * 15;
  const ymd = start || { y: 2026, mo: 10, d: 26 }; // a Monday
  const now = zonedToEpoch(ymd.y, ymd.mo, ymd.d, 6 * 60, tz);
  const dayEnd = n => { const x = ymdAdd(ymd, n); return zonedToEpoch(x.y, x.mo, x.d, 23 * 60 + 59, tz); };
  return { rng, j, now, dayEnd, tz, ymd };
}
const G = {
  muscle: (p, lvl) => ({ id: 'g-muscle', title: 'Build Muscle', archetypeId: 'FITNESS_HYPERTROPHY', priority: p, level: lvl || 'NOVICE' }),
  spanish: p => ({ id: 'g-spanish', title: 'Learn Spanish', archetypeId: 'SKILL_LANGUAGE', subject: 'spanish', priority: p, level: 'NOVICE' }),
  reading: (p, now) => ({ id: 'g-reading', title: 'Read 12 Books', archetypeId: 'HABIT_READING', priority: p, level: 'INTERMEDIATE', createdEpoch: now - 60 * 86400,
    target: { units: 3600, completed: 500, unit: 'pages', unitsPerHour: 30, deadlineEpoch: now + 300 * 86400 } }),
  finance: p => ({ id: 'g-finance', title: 'Improve Finances', archetypeId: 'ADMIN_FINANCE', priority: p, level: 'NOVICE' }),
  study: (p, lvl) => ({ id: 'g-study', title: 'CDL / Business Study', archetypeId: 'STUDY_GENERAL', priority: p, level: lvl || 'INTERMEDIATE' }),
  cardio: p => ({ id: 'g-cardio', title: 'Run a 10K', archetypeId: 'FITNESS_CARDIO', priority: p, level: 'NOVICE' }),
  creative: p => ({ id: 'g-guitar', title: 'Learn Guitar', archetypeId: 'CREATIVE_PRACTICE', priority: p, level: 'NOVICE' }),
  mind: p => ({ id: 'g-mind', title: 'Meditate', archetypeId: 'MINDFULNESS', priority: p, level: 'NOVICE' })
};
function officeInput(b, extra) {
  return Object.assign({
    timezone: b.tz, currentTimeEpoch: b.now, horizonStartEpoch: b.now, horizonDays: 7,
    playerAvailability: { timezone: b.tz, sleepRequirementMinutes: 480, rules: [
      { id: 'work', kind: 'WORK', title: 'Office work', days: WEEKDAYS, startMin: b.j(540, 30), durationMin: 480, commuteMin: 30, load: 'MIXED_MODERATE' },
      { id: 'sleep', kind: 'SLEEP', title: 'Sleep', days: [0, 1, 2, 3, 4, 5, 6], startMin: 23 * 60, durationMin: 480 }] },
    fixedCommitments: [], goals: [], activeQuests: [], existingSchedule: [], completionHistory: [], playerPreferences: {}
  }, extra || {});
}
const SCENARIOS = {
  office: { label: 'Standard office worker', about: 'Mon–Fri 9–5, four goals, a weekly strength quest and a daily vocab quest (spec Simulation A).', build(seed) {
    const b = scenarioBase(seed, 'America/New_York');
    const inp = officeInput(b, { goals: [G.muscle(5), G.spanish(4), G.reading(3, b.now), G.finance(3)], activeQuests: [
      { id: 'q-iron', title: 'Iron Discipline: 3 Strength Workouts', tier: 'CORE', tag: 'fitness.strength', requiredCount: 3, deadlineEpoch: b.dayEnd(6), bounty: { xp: 150 } },
      { id: 'q-vocab', title: 'Daily: 15m Spanish Vocab', tier: 'CORE', tag: 'language.spanish.vocab', requiredCount: 1, recurrence: 'DAILY', bounty: { xp: 20 } }] });
    return { input: inp, disruption: { type: 'NEW_COMMITMENT', label: 'Called into overtime Tue 17:00–21:00', commitment: { id: 'ot1', title: 'Overtime', startEpoch: zonedToEpoch(2026, 10, 27, 17 * 60, b.tz), endEpoch: zonedToEpoch(2026, 10, 27, 21 * 60, b.tz), commuteMin: 30, kind: 'WORK' } } };
  } },
  night: { label: 'Night-shift worker', about: 'Mon–Sat 22:00–06:30 heavy warehouse shifts, daytime sleep (spec Simulation B).', build(seed) {
    const b = scenarioBase(seed, 'America/Chicago');
    const inp = {
      timezone: b.tz, currentTimeEpoch: b.now, horizonStartEpoch: b.now, horizonDays: 7,
      playerAvailability: { timezone: b.tz, sleepRequirementMinutes: 480, rules: [
        { id: 'shift', kind: 'WORK', title: 'Warehouse shift', days: [1, 2, 3, 4, 5, 6], startMin: 22 * 60, durationMin: 510, commuteMin: 30, load: 'PHYSICAL_HEAVY' },
        { id: 'sleep', kind: 'SLEEP', title: 'Day sleep', days: [0, 1, 2, 3, 4, 5, 6], startMin: 8 * 60, durationMin: 480 }] },
      fixedCommitments: [], goals: [Object.assign(G.muscle(4, 'NOVICE'), { disabledTemplates: ['mealprep', 'assess'] }), G.study(5), Object.assign(G.reading(3, b.now), { target: null })],
      activeQuests: [], existingSchedule: [], completionHistory: [], playerPreferences: {}
    };
    return { input: inp, disruption: { type: 'BLOCK_HOURS', label: 'Lose 3 hours tomorrow afternoon', commitment: { id: 'blk1', title: 'Car trouble', startEpoch: zonedToEpoch(2026, 10, 27, 16 * 60, b.tz), endEpoch: zonedToEpoch(2026, 10, 27, 19 * 60, b.tz) } } };
  } },
  rotating: { label: 'Rotating-shift worker', about: '4-on-4-off 12-hour day shifts using the cycle-modulo rule.', build(seed) {
    const b = scenarioBase(seed, 'Europe/London');
    const rot = { anchorDate: '2026-10-24', cycleLength: 8, activeDays: [0, 1, 2, 3] };
    const off = { anchorDate: '2026-10-24', cycleLength: 8, activeDays: [4, 5, 6, 7] };
    const inp = {
      timezone: b.tz, currentTimeEpoch: b.now, horizonStartEpoch: b.now, horizonDays: 7,
      playerAvailability: { timezone: b.tz, sleepRequirementMinutes: 480, rules: [
        { id: 'shift', kind: 'WORK', title: '12h shift', startMin: 7 * 60, durationMin: 720, commuteMin: 30, load: 'MIXED_MODERATE', rotation: rot },
        { id: 'sleep-on', kind: 'SLEEP', title: 'Sleep (work days)', startMin: 21 * 60 + 30, durationMin: 480, rotation: { anchorDate: '2026-10-23', cycleLength: 8, activeDays: [0, 1, 2, 3] } },
        { id: 'sleep-off', kind: 'SLEEP', title: 'Sleep (days off)', startMin: 23 * 60, durationMin: 510, rotation: { anchorDate: '2026-10-23', cycleLength: 8, activeDays: [4, 5, 6, 7] } }] },
      fixedCommitments: [], goals: [G.cardio(4), G.creative(3), G.finance(2)], activeQuests: [], existingSchedule: [], completionHistory: [], playerPreferences: {}
    };
    void off;
    return { input: inp, disruption: { type: 'BLACKOUT_DAY', label: 'Family emergency on a day off', commitment: { id: 'bo1', title: 'Family emergency', startEpoch: zonedToEpoch(2026, 10, 29, 0, b.tz), endEpoch: zonedToEpoch(2026, 10, 30, 0, b.tz) } } };
  } },
  student: { label: 'Student', about: 'Split class timetable, weekend part-time job, exam study at high priority.', build(seed) {
    const b = scenarioBase(seed, 'Asia/Kolkata');
    const inp = {
      timezone: b.tz, currentTimeEpoch: b.now, horizonStartEpoch: b.now, horizonDays: 7,
      playerAvailability: { timezone: b.tz, sleepRequirementMinutes: 480, rules: [
        { id: 'cls-mwf', kind: 'CLASS', title: 'Lectures', days: [1, 3, 5], startMin: 9 * 60, durationMin: 180, commuteMin: 20 },
        { id: 'cls-tt', kind: 'CLASS', title: 'Labs', days: [2, 4], startMin: 13 * 60, durationMin: 180, commuteMin: 20 },
        { id: 'job', kind: 'WORK', title: 'Café job', days: [6], startMin: 10 * 60, durationMin: 360, commuteMin: 15, load: 'MIXED_MODERATE' },
        { id: 'sleep', kind: 'SLEEP', title: 'Sleep', days: [0, 1, 2, 3, 4, 5, 6], startMin: b.j(24 * 60 - 30, 30), durationMin: 480 }] },
      fixedCommitments: [], goals: [G.study(5, 'ADVANCED'), G.cardio(3), G.creative(2)], activeQuests: [
        { id: 'q-exam', title: 'Midterm prep: 5 study blocks', tier: 'MANDATORY', tag: 'study.deep', requiredCount: 5, deadlineEpoch: b.dayEnd(4) }],
      existingSchedule: [], completionHistory: [], playerPreferences: {}
    };
    return { input: inp, disruption: { type: 'NEW_COMMITMENT', label: 'Extra lab Wed 14:00–18:00', commitment: { id: 'lab2', title: 'Make-up lab', startEpoch: zonedToEpoch(2026, 10, 28, 14 * 60, b.tz), endEpoch: zonedToEpoch(2026, 10, 28, 18 * 60, b.tz), commuteMin: 20, kind: 'CLASS' } } };
  } },
  abundant: { label: 'Abundant free time', about: 'No job; five goals. Volume ceilings must still protect rest.', build(seed) {
    const b = scenarioBase(seed, 'America/Los_Angeles');
    const inp = {
      timezone: b.tz, currentTimeEpoch: b.now, horizonStartEpoch: b.now, horizonDays: 7,
      playerAvailability: { timezone: b.tz, sleepRequirementMinutes: 480, rules: [{ id: 'sleep', kind: 'SLEEP', title: 'Sleep', days: [0, 1, 2, 3, 4, 5, 6], startMin: 0, durationMin: 480 }] },
      fixedCommitments: [], goals: [G.muscle(4, 'ADVANCED'), G.spanish(4), G.reading(3, b.now), G.creative(3), G.mind(2)], activeQuests: [],
      existingSchedule: [], completionHistory: [], playerPreferences: { intensityLevel: 'AGGRESSIVE' }
    };
    return { input: inp, disruption: { type: 'SICK', label: 'Sick for 2 days', commitment: { id: 'sick1', title: 'Sick', startEpoch: b.now, endEpoch: b.now + 2 * 86400 } } };
  } },
  limited: { label: 'Extremely limited free time', about: '12h days with long commutes; roughly 30–60 free minutes per weekday.', build(seed) {
    const b = scenarioBase(seed, 'America/New_York');
    const inp = {
      timezone: b.tz, currentTimeEpoch: b.now, horizonStartEpoch: b.now, horizonDays: 7,
      playerAvailability: { timezone: b.tz, sleepRequirementMinutes: 480, rules: [
        { id: 'work', kind: 'WORK', title: 'Long shift', days: [1, 2, 3, 4, 5, 6], startMin: 7 * 60, durationMin: 720, commuteMin: 60, load: 'MIXED_MODERATE' },
        { id: 'sleep', kind: 'SLEEP', title: 'Sleep', days: [0, 1, 2, 3, 4, 5, 6], startMin: 21 * 60 + 30, durationMin: 480 }] },
      fixedCommitments: [], goals: [G.muscle(4), G.spanish(3), G.reading(3, b.now)], activeQuests: [
        { id: 'q-read', title: 'Read 3 times this week', tier: 'CORE', tag: 'reading', requiredCount: 3, deadlineEpoch: b.dayEnd(6) }],
      existingSchedule: [], completionHistory: [], playerPreferences: {}
    };
    return { input: inp, disruption: { type: 'NEW_COMMITMENT', label: 'Sunday shift added', commitment: { id: 'sun', title: 'Extra Sunday shift', startEpoch: zonedToEpoch(2026, 11, 1, 9 * 60, b.tz), endEpoch: zonedToEpoch(2026, 11, 1, 15 * 60, b.tz), kind: 'WORK' } } };
  } },
  manygoals: { label: 'Many competing goals', about: 'Office worker with eight active goals: fairness and the dominance cap.', build(seed) {
    const b = scenarioBase(seed, 'America/New_York');
    const inp = officeInput(b, { goals: [G.muscle(5), G.spanish(5), G.reading(4, b.now), G.finance(3), G.study(4), G.cardio(3), G.creative(2), G.mind(2)] });
    return { input: inp, disruption: { type: 'BLOCK_HOURS', label: 'Lose Wednesday evening', commitment: { id: 'b2', title: 'Unexpected errand', startEpoch: zonedToEpoch(2026, 10, 28, 18 * 60, b.tz), endEpoch: zonedToEpoch(2026, 10, 28, 22 * 60, b.tz) } } };
  } },
  deadlines: { label: 'Conflicting quest deadlines', about: 'Several quests due within 48 hours, including an exclusive boss exam.', build(seed) {
    const b = scenarioBase(seed, 'America/New_York');
    const inp = officeInput(b, { goals: [G.spanish(4), G.reading(3, b.now)], activeQuests: [
      { id: 'q-boss', title: 'Boss Exam: Spanish Unit 4', tier: 'MANDATORY', tag: 'language.spanish.assessment', requiredCount: 1, sessionMinutes: 45, exclusive: true, deadlineEpoch: b.now + 30 * 3600 },
      { id: 'q-drill', title: 'Grammar Gauntlet: 2 drills', tier: 'CORE', tag: 'language.spanish.grammar', requiredCount: 2, deadlineEpoch: b.now + 40 * 3600 },
      { id: 'q-tax', title: 'File the tax form', tier: 'MANDATORY', tag: 'admin.finance', requiredCount: 1, sessionMinutes: 60, exclusive: true, deadlineEpoch: b.now + 44 * 3600 },
      { id: 'q-read2', title: 'Read twice by Wednesday', tier: 'OPTIONAL', tag: 'reading', requiredCount: 2, deadlineEpoch: b.now + 48 * 3600 },
      { id: 'q-chain', title: 'Unit 5 (after Unit 4)', tier: 'CORE', tag: 'language.spanish.grammar', requiredCount: 1, prerequisiteQuestIds: ['q-boss'], deadlineEpoch: b.dayEnd(6) }] });
    return { input: inp, disruption: { type: 'QUEST_CHANGED', label: 'Tax deadline pulled forward to tonight', questPatch: { id: 'q-tax', deadlineEpoch: b.now + 16 * 3600 } } };
  } },
  misses: { label: 'Repeatedly missed activities', about: 'Office worker who misses three sessions in a row: tokens, circuit breaker and Tactical Regroup.', build(seed) {
    const s = SCENARIOS.office.build(seed);
    s.disruption = { type: 'MISS_SERIES', label: 'Miss the next 3 sessions', count: 3 };
    s.input.playerPreferences = { consequenceMode: 'MODERATE' };
    return s;
  } },
  workchange: { label: 'Work schedule changes', about: 'Office worker whose job moves to a 12:00–20:00 shift from Wednesday.', build(seed) {
    const s = SCENARIOS.office.build(seed);
    s.disruption = { type: 'RULE_CHANGE', label: 'Shift moves to 12:00–20:00 from Wed', rules: [
      { id: 'work', kind: 'WORK', title: 'Office work', days: WEEKDAYS, startMin: 540, durationMin: 480, commuteMin: 30, validTo: '2026-10-27' },
      { id: 'work-late', kind: 'WORK', title: 'Late shift', days: WEEKDAYS, startMin: 720, durationMin: 480, commuteMin: 30, validFrom: '2026-10-28' }] };
    return s;
  } }
};
function buildScenario(name, seed) {
  const sc = SCENARIOS[name];
  if (!sc) throw new Error('Unknown scenario ' + name);
  const r = sc.build(seed == null ? 1 : seed);
  return Object.assign({ name, label: sc.label, about: sc.about, seed }, r);
}
const api = {
  ENGINE_VERSION, SCHEMA_VERSION, SLOT, SLOT_NAMES, ARCHETYPES, SCENARIOS, R_TEXT,
  deriveAvailability, expandGoalActivities, compileCandidates, generateSchedule, rescheduleActivity, validateSchedule,
  recordCompletion, reconcileQuestProgress, revokeEvent, detectMisses, applyMissConsequence, canTransition, emptyLedger, ledgerHas, completionEventId,
  normalizePreferences, defaultPreferences, normalizeGoal, normalizeQuest, tagMatches, suggestTemplates, buildScenario,
  util: { stableId, mulberry32, localParts, zonedToEpoch, ymdAdd, ymdKey, tzOffsetSec, isValidTimeZone, buildHorizon, ceilTo15, deepClone },
  _internal: { prepareState, snapshot, restore, stateFingerprint, removeInst, evalAcross, commit, runLocalRepair, buildState, prepareStateWithHold, checkHard, taskEarliestLatest, R }
};
return Object.freeze(api);
});

/* ── Life Director Store ────────────────────────────────────────────────────
   Dedicated namespace so LifeMax save data is never touched (spec §18).
   Writes are verified by read-back; the previous good copy is kept as a backup;
   unreadable data is quarantined, never silently discarded. */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.LifeDirectorStore = api;
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';
  const KEY = 'lifemax_director_state_v1';
  const BAK = KEY + '.bak';
  const CURRENT = 1;

  function defaultState(o) {
    o = o || {};
    return {
      schemaVersion: CURRENT, playerId: o.playerId || 'player', createdEpoch: o.nowEpoch || 0, updatedEpoch: o.nowEpoch || 0,
      setupComplete: false,
      availability: { timezone: o.timezone || 'UTC', sleepRequirementMinutes: 480, rules: [], blackouts: [] },
      fixedCommitments: [], goals: [], prefs: {}, schedule: null, ledger: null, changeLog: [], energyCheckin: null,
      questOverlay: {}, labQuests: [], bridge: { enabled: false, source: null, mapping: null },
      lastGeneratedWeekKey: null, ui: { tab: 'today' }
    };
  }
  function validate(s) {
    if (!s || typeof s !== 'object') return 'not an object';
    if (s.schemaVersion !== CURRENT) return 'wrong schemaVersion ' + s.schemaVersion;
    if (!s.availability || !Array.isArray(s.availability.rules)) return 'missing availability';
    if (!Array.isArray(s.goals)) return 'missing goals';
    return null;
  }
  /* v0 = pre-release prototype shape {sleep:{bed,wake}, work:[...], goals:[...]} */
  function migrate(raw) {
    if (!raw || typeof raw !== 'object') return raw;
    let s = raw;
    if (s.schemaVersion == null || s.schemaVersion === 0) {
      const base = defaultState({ playerId: s.playerId, timezone: s.timezone });
      base.goals = Array.isArray(s.goals) ? s.goals : [];
      base.prefs = s.prefs || s.settings || {};
      if (s.sleep && s.sleep.bed != null) base.availability.rules.push({ id: 'sleep', kind: 'SLEEP', title: 'Sleep', days: [0, 1, 2, 3, 4, 5, 6], startMin: s.sleep.bed, durationMin: ((s.sleep.wake - s.sleep.bed + 1440) % 1440) || 480 });
      for (const [i, w] of (s.work || []).entries()) base.availability.rules.push(Object.assign({ id: 'work' + i, kind: 'WORK' }, w));
      base.setupComplete = base.availability.rules.length > 0;
      base.migratedFrom = 0;
      s = base;
    }
    return s;
  }
  function create(storage) {
    let available = true, lastError = null;
    try { const k = KEY + '.probe'; storage.setItem(k, '1'); storage.removeItem(k); } catch (e) { available = false; lastError = e; }
    function load(opts) {
      const report = { source: null, recovered: false, quarantined: null, migratedFrom: null, available };
      if (!available) return { state: defaultState(opts), report: Object.assign(report, { source: 'MEMORY_ONLY', error: String(lastError) }) };
      for (const key of [KEY, BAK]) {
        let raw = null;
        try { raw = storage.getItem(key); } catch (e) { lastError = e; continue; }
        if (raw == null) continue;
        let parsed;
        try { parsed = JSON.parse(raw); } catch (e) {
          const q = 'lifemax_director_quarantine_' + key.replace(/\W/g, '_');
          try { storage.setItem(q, raw); } catch (_) { /* quota: keep original in place */ }
          report.quarantined = report.quarantined || q; continue;
        }
        const before = parsed && parsed.schemaVersion;
        const m = migrate(parsed);
        const err = validate(m);
        if (err) { report.invalid = err; continue; }
        if (before !== CURRENT) report.migratedFrom = before == null ? 0 : before;
        report.source = key; report.recovered = key === BAK;
        return { state: m, report };
      }
      report.source = 'NEW';
      return { state: defaultState(opts), report };
    }
    function save(state) {
      if (!available) return { ok: false, error: 'Browser storage is unavailable here; changes last only until this page closes.' };
      const err = validate(state);
      if (err) return { ok: false, error: 'Refusing to save invalid state: ' + err };
      let json;
      try { json = JSON.stringify(state); } catch (e) { return { ok: false, error: 'Could not serialise state: ' + e.message }; }
      try {
        const prev = storage.getItem(KEY);
        if (prev != null && prev !== json) storage.setItem(BAK, prev);
        storage.setItem(KEY, json);
        if (storage.getItem(KEY) !== json) return { ok: false, error: 'Write verification failed' };
        return { ok: true, bytes: json.length };
      } catch (e) {
        lastError = e;
        return { ok: false, error: /quota/i.test(String(e && (e.name + e.message))) ? 'Storage is full. Export a backup and clear old Life Director history.' : String(e && e.message || e) };
      }
    }
    function exportJson(state) { return JSON.stringify({ format: 'lifemax-director-backup', version: CURRENT, state }, null, 1); }
    function importJson(text) {
      const o = JSON.parse(text);
      const s = migrate(o && o.format === 'lifemax-director-backup' ? o.state : o);
      const err = validate(s); if (err) throw new Error('Backup is not valid: ' + err);
      return s;
    }
    function clear() { try { const prev = storage.getItem(KEY); if (prev) storage.setItem(BAK, prev); storage.removeItem(KEY); } catch (e) { /* ignore */ } }
    return { load, save, exportJson, importJson, clear, get available() { return available; } };
  }
  return { create, defaultState, migrate, validate, KEY, BAK, CURRENT };
});

/* ── Life Director ⇄ LifeMax v73 host adapter ───────────────────────────────
   LifeMax keeps its runtime in ONE classic <script>; its top-level let/const/function
   bindings (state, save, rules, questKey, toggleQuest …) are visible to later classic
   scripts such as this Forge feature. Every symbol is capability-checked with typeof,
   so a host change degrades to "not connected" instead of crashing.

   Sovereignty rules (brief §4, spec §5.1):
   • Activity XP is awarded ONLY by appending a normal LifeMax activity and calling the host's
     save(), which rebuilds XP from state.activities. One activity per CompletionEvent, keyed by
     activity.ldEventId — so the guard lives in LifeMax's own save file and survives reloads.
   • Quest bounty XP is awarded ONLY by the host's own toggleQuest(id,true), which already refuses
     to pay twice for the same questKey period.
   • Life Director never edits quest definitions, levels, attributes or decay. */
const LifeMaxHost = (function () {
  'use strict';
  const H = {};
  const DAYS = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'];
  const WORDS = { one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7, eight: 8, nine: 9, ten: 10, once: 1, twice: 2 };

  H.capabilities = function () {
    const c = {
      state: typeof state !== 'undefined' && state && Array.isArray(state.activities) && Array.isArray(state.quests),
      save: typeof save === 'function', rules: typeof rules !== 'undefined' && !!rules,
      questKey: typeof questKey === 'function', isQuestDone: typeof isQuestDone === 'function', toggleQuest: typeof toggleQuest === 'function',
      questLocked: typeof questLocked === 'function', navigate: typeof navigate === 'function', toast: typeof toast === 'function',
      px: typeof px === 'function', planDay: typeof planDay === 'function', dayDone: typeof dayDone === 'function'
    };
    c.connected = c.state && c.save && c.rules && c.questKey && c.isQuestDone && c.toggleQuest;
    c.missing = Object.keys(c).filter(k => k !== 'connected' && k !== 'missing' && !c[k]);
    return c;
  };
  H.connected = () => H.capabilities().connected;
  H.icon = (name, size) => { try { if (typeof px === 'function') return px(name, size || 16); } catch (e) { /* fall through */ } return ''; };
  H.toast = msg => { try { if (typeof toast === 'function') { toast(msg); return true; } } catch (e) { /* ignore */ } return false; };
  H.openView = v => { try { if (typeof navigate === 'function') navigate(v); } catch (e) { /* ignore */ } };
  H.playerId = function () {
    try { if (H.connected()) return 'lm_' + LifeDirectorEngine.util.stableId('p', String(state.created || '') + '|' + String(state.name || '')).slice(2, 14); } catch (e) { /* ignore */ }
    return 'player';
  };

  /* ── tag mapping (LifeMax quest kind / activity type ⇄ Life Director activityTypeTag) ── */
  const KIND_TAG = {
    learning: 'study', knowledge: 'study', recall: 'study.review', synthesis: 'study', research: 'study', logic: 'study', focus: 'study.deep', problem_solving: 'study',
    reading: 'reading', writing: 'creative.writing', articulation: 'language.speaking', skill: 'practical',
    movement: 'fitness', fitness: 'fitness', strength: 'fitness.strength', reps: 'fitness.strength', active: 'fitness', cardio: 'fitness.cardio', endurance: 'fitness.cardio',
    business: 'business', building: 'business', shipping: 'business', outreach: 'business', networking: 'social', connection: 'social', brainstorming: 'business',
    finance: 'admin.finance', audit: 'admin.finance', review: 'admin.review', reflection: 'admin.review', planning: 'admin.review', organization: 'chores', environment: 'chores',
    exploration: 'practical.outdoor', gear: 'practical', safety: 'practical', recovery: 'wellbeing', custom: 'general', milestone: 'general'
  };
  const PASSIVE_KINDS = new Set(['hydration', 'nutrition', 'sleep', 'rest', 'warmup', 'check', 'consistency', 'minutes', 'seconds']);
  const ATTR_TAG = { physique: 'fitness', endurance: 'fitness.cardio', intelligence: 'study', business: 'business', social: 'social', discipline: 'general', adventure: 'practical', wellbeing: 'wellbeing' };
  /* tag → LifeMax activity type (DEFAULT_RULES key) used to award activity XP through the host formula */
  const TAG_TYPE = [['fitness.strength', 'strength'], ['fitness.cardio', 'running'], ['fitness', 'strength'], ['language', 'language'], ['reading', 'reading'], ['study', 'reading'],
    ['business', 'business'], ['admin.finance', 'business'], ['admin', 'task'], ['wellbeing', 'recovery'], ['nutrition', 'practical'], ['practical', 'practical'], ['creative', 'practical'], ['chores', 'task'], ['social', 'custom']];
  const TYPE_TAG = { strength: 'fitness.strength', running: 'fitness.cardio', language: 'language', reading: 'reading', business: 'business', recovery: 'wellbeing', practical: 'practical', task: 'general', custom: 'general' };
  H.typeForTag = function (tag) { for (const [p, t] of TAG_TYPE) if (tag === p || String(tag).startsWith(p + '.')) return t; return 'custom'; };
  H.tagForType = t => TYPE_TAG[t] || 'general';
  function parseCount(text) {
    const m = /\b(\d+|one|two|three|four|five|six|seven|eight|nine|ten|once|twice)\b\s*(?:[\w-]+\s+){0,3}?(sessions?|workouts?|times|blocks?|runs?|days?|lessons?|chapters?|walks?|meals?)\b/i.exec(text || '');
    if (!m) return 1;
    const n = /^\d+$/.test(m[1]) ? +m[1] : WORDS[m[1].toLowerCase()];
    return Math.max(1, Math.min(14, n || 1));
  }
  function parseMinutes(text) { const m = /(\d{1,3})[\s-]*(?:min|minutes?)\b/i.exec(text || ''); return m ? Math.max(5, Math.min(240, +m[1])) : 0; }
  H.mapQuestTag = function (q) {
    if (q.kind && KIND_TAG[q.kind]) return KIND_TAG[q.kind];
    const t = (q.name + ' ' + (q.desc || '')).toLowerCase();
    if (/workout|lift|strength/.test(t)) return 'fitness.strength';
    if (/run|cardio|walk/.test(t)) return 'fitness.cardio';
    if (/read|book|page/.test(t)) return 'reading';
    if (/study|learn|course/.test(t)) return 'study';
    if (/spanish|language|vocab/.test(t)) return 'language';
    return ATTR_TAG[q.attribute] || 'general';
  };

  /* week bounds matching LifeMax questKey (weeks start Monday, local time) */
  function weekBounds(nowEpoch, tz) {
    const U = LifeDirectorEngine.util, p = U.localParts(nowEpoch, tz);
    const mon = U.ymdAdd({ y: p.y, mo: p.mo, d: p.d }, -((p.dow + 6) % 7));
    const next = U.ymdAdd(mon, 7);
    return { start: U.zonedToEpoch(mon.y, mon.mo, mon.d, 0, tz), end: U.zonedToEpoch(next.y, next.mo, next.d, 0, tz), nextEnd: (() => { const n2 = U.ymdAdd(mon, 14); return U.zonedToEpoch(n2.y, n2.mo, n2.d, 0, tz); })(), monKey: U.ymdKey(mon) };
  }
  /* Milestones ("50 Sessions Logged", "First Training Block Complete") complete through
     everything else you do. They are never something to sit down and do, so Life Director
     never plans time for them and never shows them as reminders. */
  const EARNED_RE = /\b(consecutive|cumulative|accumulat\w*|in total|total of|streak|maintain\w*|every day|cover to cover|entire|from start to finish|deload week)\b|\b\d+\s+(?:[a-z-]+\s+){0,3}(sessions?|hours?|weeks?|days?|workouts?)\b|\b(ten|twenty|fifty|hundred)\s+(completed\s+)?(volumes|sessions|hours|weeks|days)\b/i;
  const isMilestoneQuest = q => !!q && (q.kind === 'milestone' || q.cadence === 'epic');
  /* earned by piling up other activity: never planned */
  H.isMilestone = q => isMilestoneQuest(q) && EARNED_RE.test(q.name + ' ' + (q.desc || ''));
  /* a one-off project ("record and review your form"): planned once, as an optional session */
  H.isProjectMilestone = q => isMilestoneQuest(q) && !H.isMilestone(q);
  H.milestones = function () {
    if (!H.connected()) return [];
    return state.quests.filter(H.isMilestone).map(q => { let done = false, locked = false; try { done = isQuestDone(q); } catch (e) { /* ignore */ } try { locked = typeof questLocked === 'function' && questLocked(q); } catch (e) { /* ignore */ } return { id: q.id, title: q.name, desc: q.desc || '', done, locked }; });
  };
  /* Read LifeMax quests as Life Director QuestRecords. overlay = LD's session counts per questKey. */
  H.readQuests = function (nowEpoch, tz, overlay, revoked) {
    if (!H.connected()) return [];
    const out = [], wb = weekBounds(nowEpoch, tz);
    const count = key => ((overlay[key] && overlay[key].eventIds) || []).filter(id => !(revoked && revoked[id])).length;
    for (const q of state.quests) {
      if (!q || !q.id || H.isMilestone(q)) continue;
      let locked = false; try { locked = typeof questLocked === 'function' && questLocked(q); } catch (e) { /* ignore */ }
      let done = false; try { done = isQuestDone(q); } catch (e) { /* ignore */ }
      const key = questKey(q);
      const text = q.name + ' ' + (q.desc || '');
      const passive = PASSIVE_KINDS.has(q.kind) || q.kind === 'nutrition';
      const mins = parseMinutes(text);
      const target = q.cadence === 'daily' ? 1 : parseCount(text);
      const base = { title: q.name, tier: q.kind === 'milestone' ? 'OPTIONAL' : 'CORE', tag: H.mapQuestTag(q), schedulingMode: passive ? 'NONE' : 'SESSIONS',
        sessionMinutes: mins, minSessionMinutes: mins, bounty: { xp: Number(q.xp) || 0 }, source: 'lifemax' };
      if (locked) { out.push(Object.assign({ id: q.id, status: 'LOCKED', requiredCount: target }, base)); continue; }
      if (q.cadence === 'daily') {
        const od = q.day && q.day !== 'any' ? [DAYS.indexOf(q.day)].filter(x => x >= 0) : null;
        out.push(Object.assign({ id: q.id, status: 'ACTIVE', recurrence: 'DAILY', onlyDays: od, requiredCount: 1, progressCount: done ? 1 : Math.min(1, count(key)),
          hostRef: { questId: q.id, key, cadence: 'daily', done } }, base));
      } else if (q.cadence === 'weekly') {
        out.push(Object.assign({ id: q.id, status: done ? 'COMPLETED' : 'ACTIVE', requiredCount: target, progressCount: done ? target : Math.min(target, count(key)), deadlineEpoch: wb.end - 60,
          hostRef: { questId: q.id, key, cadence: 'weekly', done } }, base));
        /* next week's instance of the same weekly quest, for the part of the horizon that crosses Monday */
        out.push(Object.assign({ id: q.id + '@next', status: 'ACTIVE', requiredCount: target, progressCount: 0, windowStartEpoch: wb.end, deadlineEpoch: wb.nextEnd - 60,
          hostRef: { questId: q.id, key: q.id + '_' + LifeDirectorEngine.util.ymdKey(LifeDirectorEngine.util.localParts(wb.end + 3600, tz)), cadence: 'weekly', future: true } }, base));
      } else {
        const once = H.isProjectMilestone(q) ? 1 : target;
        out.push(Object.assign({ id: q.id, status: done ? 'COMPLETED' : 'ACTIVE', requiredCount: once, progressCount: done ? once : Math.min(once, count(key)),
          hostRef: { questId: q.id, key, cadence: q.cadence || 'once', done } }, base, { tier: 'OPTIONAL' }));
      }
    }
    return out;
  };
  /* Class training plan (Plan tab): each upcoming plan day becomes a date-bound session to place. */
  H.readPlanSessions = function (nowEpoch, tz, days) {
    if (!H.connected() || typeof planDay !== 'function' || !state.plan) return [];
    const U = LifeDirectorEngine.util, out = [], p = U.localParts(nowEpoch, tz);
    for (let i = 0; i < days; i++) {
      const ymd = U.ymdAdd({ y: p.y, mo: p.mo, d: p.d }, i), key = U.ymdKey(ymd);
      let d = null; try { d = planDay(key); } catch (e) { d = null; }
      if (!d || !Array.isArray(d.items) || !d.items.length || /rest/i.test(String(d.kind || ''))) continue;
      let done = false; try { done = typeof dayDone === 'function' && dayDone(key); } catch (e) { /* ignore */ }
      const start = U.zonedToEpoch(ymd.y, ymd.mo, ymd.d, 0, tz), nx = U.ymdAdd(ymd, 1), end = U.zonedToEpoch(nx.y, nx.mo, nx.d, 0, tz);
      const tag = KIND_TAG[d.kind] || ATTR_TAG[d.primary] || 'fitness';
      out.push({ id: 'plan:' + key, title: 'Plan: ' + (d.label || 'Session'), tier: 'CORE', tag, status: done ? 'COMPLETED' : 'ACTIVE', requiredCount: 1, progressCount: done ? 1 : 0,
        sessionMinutes: Math.max(30, Math.min(90, (d.min || d.minutes || d.items.length * 8) | 0)), exclusive: true, windowStartEpoch: start, deadlineEpoch: end,
        hostRef: { plan: key, done }, source: 'lifemax-plan' });
    }
    return out;
  };

  /* LifeMax's own XP formula from submitActivity()/calcPreview(), reproduced exactly:
     duration factor 0.4×…3× (30 min = 1×), quality, diminishing returns after 3 and 5 same-type logs per day,
     70/30 primary/secondary split. If LifeMax changes this formula, update ONLY this function. */
  H.xpFor = function (type, minutes, quality, dateKey) {
    const R = rules[type] || rules.custom;
    const dur = Math.max(0, Number(minutes) || 0);
    const durFactor = dur <= 0 ? 0 : Math.min(3, Math.max(0.4, dur / 30));
    const total = Math.max(0, Math.round(R.base * durFactor * (quality || 1)));
    const day = dateKey || new Date().toLocaleDateString('en-CA');
    const todayCount = state.activities.filter(a => a.type === type && new Date(a.date).toLocaleDateString('en-CA') === day).length;
    const factor = todayCount >= 5 ? 0.25 : todayCount >= 3 ? 0.5 : 1;
    const xp = Math.max(1, Math.round(total * factor));
    const p = R.primary, s = R.secondary;
    let pXP = xp, sXP = 0; if (s && s !== p) { pXP = Math.round(xp * 0.7); sXP = xp - pXP; }
    return { xp, rule: R, split: [{ attr: p, xp: pXP }].concat(s && s !== p ? [{ attr: s, xp: sXP }] : []) };
  };
  /* Stream 1 (spec §5.3): exactly one LifeMax activity per CompletionEvent. */
  H.awardActivity = function (event, info) {
    if (!H.connected()) return { awarded: false, reason: 'HOST_NOT_CONNECTED' };
    const existing = state.activities.find(a => a.ldEventId === event.eventId);
    if (existing) return { awarded: false, reason: 'ALREADY_IN_LIFEMAX', activityId: existing.id, xp: existing.xp };
    const type = H.typeForTag(event.activityTypeTag);
    const when = new Date(event.actualStartEpoch * 1000);
    const x = H.xpFor(type, event.actualDurationMinutes, 1, when.toLocaleDateString('en-CA'));
    const act = { id: 'ld_' + event.eventId, ldEventId: event.eventId, type, name: info && info.title || event.title, icon: x.rule.icon, duration: event.actualDurationMinutes, quality: 1,
      skill: x.rule.skill, primary: x.rule.primary, secondary: x.rule.secondary || '', xp: x.xp, date: when.toISOString(),
      notes: 'Logged from Life Director' + (event.lifecycleState === 'PARTIALLY_COMPLETED' ? ' (partial)' : ''), xpSplit: x.split };
    state.activities.push(act);
    try { save(); } catch (e) { state.activities = state.activities.filter(a => a !== act); return { awarded: false, reason: 'HOST_SAVE_FAILED', error: String(e) }; }
    return { awarded: true, activityId: act.id, xp: x.xp };
  };
  /* Stream 3: quest completion bounty through the host's own guarded toggle. */
  H.completeQuest = function (hostRef) {
    if (!H.connected() || !hostRef || !hostRef.questId || hostRef.future) return { completed: false };
    const q = state.quests.find(x => x.id === hostRef.questId);
    if (!q) return { completed: false, reason: 'QUEST_GONE' };
    if (questKey(q) !== hostRef.key) return { completed: false, reason: 'PERIOD_CHANGED' };
    if (isQuestDone(q)) return { completed: false, reason: 'ALREADY_DONE' };
    toggleQuest(q.id, true);
    return { completed: isQuestDone(q), xp: Number(q.xp) || 0, name: q.name };
  };
  /* Activities logged anywhere in LifeMax (Log page, Plan page, quests) since a cursor. */
  H.activitiesSince = function (sinceIso) {
    if (!H.connected()) return [];
    return state.activities.filter(a => a && !a.ldEventId && a.type !== 'quest' && (!sinceIso || String(a.date) > sinceIso))
      .map(a => ({ id: a.id, type: a.type, name: a.name, minutes: Number(a.duration) || 0, iso: a.date, tag: a.type === 'plan' ? (KIND_TAG[a.kind] || 'fitness') : H.tagForType(a.type), planDate: a.planDate || null }));
  };
  H.activityExists = id => H.connected() && state.activities.some(a => a.id === id);
  H.isPlanDayDone = key => { try { return typeof dayDone === 'function' && !!dayDone(key); } catch (e) { return false; } };
  H.openPlanDay = key => { H.openView('plan'); try { if (typeof selectDay === 'function') selectDay(key); } catch (e) { /* ignore */ } };
  H._test = { parseCount, parseMinutes, weekBounds };
  return H;
})();

/* ── Life Director · interface controller (1.4 · chrono) ─────────────────────
   Today is a 24-hour dial: outer ring = time you told it about, inner ring = what it planned.
   Everything below render() that touches the plan (persistence, sync, misses, completion,
   re-planning) is unchanged from 1.3; only presentation and a few inputs are new. */
const LifeDirectorApp = (function () {
  'use strict';
  const E = LifeDirectorEngine, Host = LifeMaxHost, U = E.util;
  const FEATURE_ID = 'life-director';
  let root = null, store = null, ST = null, loadReport = null, busy = false, timer = null;
  const ui = { tab: 'today', dayIdx: 0, banner: null, animate: false, sheet: null, prevPlan: null,
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
  function say(msg) { if (!Host.toast(msg)) { const t = root && root.querySelector('.ld-toast'); if (t) { const again = t.classList.contains('show'); t.textContent = msg; t.classList.add('show'); if (again) play(t, 'ld-bump'); clearTimeout(t._h); t._h = setTimeout(() => t.classList.remove('show'), 2600); } } }

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
  function persist() {
    if (ST.schedule) {
      const cut = nowEpoch() - 21 * 86400; // keep 3 weeks of history inside the schedule; the ledger keeps everything
      ST.schedule.historyInstances = (ST.schedule.historyInstances || []).filter(h => h.endEpoch > cut);
    }
    ST.changeLog = (ST.changeLog || []).slice(-60);
    ST.updatedEpoch = nowEpoch();
    const r = store.save(ST);
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
  function questsSnapshot() { return hostQuests(nowEpoch()); }
  function afterEvent(event, inst) {
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
      if (met && q.hostRef.questId) { const c = Host.completeQuest(q.hostRef); if (c.completed) notes.push('Quest complete: ' + c.name + ' +' + c.xp + ' XP'); }
      else if (!met) notes.push(q.title + ' ' + nq.requirements[0].currentCount + '/' + nq.requirements[0].targetCount);
    }
    if (ST.schedule) {
      ST.schedule.scheduledInstances = rq.prunedScheduleInstances.filter(i => !rq.removedInstanceIds.includes(i.instanceId));
    }
    void inst;
    return notes;
  }
  function finishInstance(inst, minutes, opt) {
    opt = opt || {};
    const now = nowEpoch();
    const start = opt.startEpoch || Math.min(inst.startEpoch, now - minutes * 60) || now;
    const r = E.recordCompletion({ ledger: ST.ledger, playerId: ST.playerId, instance: inst, actualStartEpoch: Math.max(start, inst.startEpoch - 12 * 3600), actualDurationMinutes: minutes, nowEpoch: now,
      satisfiedRatio: E.normalizePreferences(ST.prefs).partialSatisfiedRatio });
    if (!r.applied) { if (!opt.silent) say(r.reason === 'DUPLICATE_EVENT_ID' ? 'Already recorded.' : 'Cannot complete this block (' + r.reason + ').'); return null; }
    ST.ledger = r.ledger;
    let xpNote = '', hostActivityId = opt.hostActivityId || null;
    if (opt.awardXp !== false) {
      const a = Host.awardActivity(r.event, { title: inst.title });
      if (a.awarded) { xpNote = '+' + a.xp + ' XP'; hostActivityId = a.activityId; }
      else if (a.reason === 'HOST_NOT_CONNECTED') xpNote = 'recorded (XP needs LifeMax)';
    }
    ST.schedule.scheduledInstances = ST.schedule.scheduledInstances.filter(i => i.instanceId !== inst.instanceId);
    ST.schedule.historyInstances = (ST.schedule.historyInstances || []).concat(Object.assign({}, inst, { lifecycleState: r.lifecycleState, completionEventId: r.eventId, hostActivityId,
      actualMinutes: r.event.actualDurationMinutes, resolvedAtEpoch: now }));
    const notes = afterEvent(r.event, inst);
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
  function logToday(inst, minutes) {
    const now = nowEpoch();
    const r = E.recordCompletion({ ledger: ST.ledger, playerId: ST.playerId, adhoc: { tag: inst.tag, title: inst.title }, clientEventId: 'ld-today:' + uuid(),
      actualStartEpoch: now - minutes * 60, actualDurationMinutes: minutes, nowEpoch: now });
    if (!r.applied) { say('Could not log it (' + r.reason + ').'); return null; }
    ST.ledger = r.ledger;
    const a = Host.awardActivity(r.event, { title: inst.title });
    (ST.schedule.historyInstances = ST.schedule.historyInstances || []).push(showAsDoneNow({
      instanceId: 'adhoc-' + r.eventId, taskId: 'adhoc-' + r.eventId, adhoc: true, title: inst.title, tag: inst.tag, goalTitle: inst.goalTitle || null,
      linkedQuestRequirementIds: [], linkedGoalIds: [], durationSlots: Math.ceil(minutes / 15), lifecycleState: 'COMPLETED_ON_TIME',
      completionEventId: r.eventId, hostActivityId: a.activityId || null, actualMinutes: minutes, resolvedAtEpoch: now,
      explanation: { summary: 'Logged for today. The session planned for ' + fDay(inst.startEpoch) + ' is still on your plan.' } }, minutes, now));
    afterEvent(r.event, null);
    persist();
    say(inst.title + ' logged for today' + (a.awarded ? ' · +' + a.xp + ' XP' : '') + ' · still planned for ' + fDay(inst.startEpoch));
    return r;
  }

  /* ── day model for the timeline ── */
  function horizonDays(sched, z) { return U.buildHorizon(sched.horizonStartEpoch, Math.round((sched.horizonEndEpoch - sched.horizonStartEpoch) / 86400), z); }
  function dayBlocks(sched, day, z) {
    const s = day.startEpoch, e = day.endEpoch, out = [];
    for (const g of sched.segments || []) {
      if (g.endEpoch <= s || g.startEpoch >= e) continue;
      if (['FREE', 'SCHEDULED_TASK', 'USER_LOCKED_ACTIVITY'].includes(g.code)) continue;
      out.push({ startEpoch: Math.max(s, g.startEpoch), endEpoch: Math.min(e, g.endEpoch), code: g.code, kind: g.kind, title: g.title, clipped: g.startEpoch < s || g.endEpoch > e });
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
  function goTab(k) { ui.tab = k; ST.ui.tab = k; persist(); render(); }
  function headerHtml() {
    const s = ST.schedule, now = nowEpoch();
    let dot = 'off', line = 'Not set up yet';
    if (ST.setupComplete && s) {
      const n = s.scheduledInstances.filter(i => i.lifecycleState === 'PLANNED' && i.endEpoch > now).length;
      dot = s.status === 'VALID_SCHEDULE' ? 'ok' : 'warn';
      line = n + ' session' + (n === 1 ? '' : 's') + ' ahead' + (s.status === 'VALID_SCHEDULE' ? '' : ' · see notes in Week');
    } else if (ST.setupComplete) line = 'No plan yet';
    return '<div class="ld-top"><div class="ld-id"><span class="ld-dot" data-s="' + dot + '" aria-hidden="true"></span><div><h2>Life Director</h2><small>' + esc(line) + '</small></div></div>' +
      '<div class="ld-top-r"><button class="ld-help" data-act="tour" aria-label="Show the guide for this screen">?</button><div class="ld-clock" aria-label="Current time"><b data-clock>' + esc(fTime(now)) + '</b><small>' + esc(fDay(now)) + '</small></div></div></div>';
  }
  function render() {
    if (!root) return;
    if (ui.bfRun) ui.bfRun.stop();
    const tabs = TABS.concat(ST.ui && ST.ui.lab ? [['lab', 'Lab']] : []);
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
    if (ui.banner) banner = '<div class="ld-banner ld-b-' + ui.banner.kind + '"><p>' + esc(ui.banner.text) + '</p>' + (ui.banner.regroup ?
      '<div class="ld-row"><button class="ld-btn" data-act="regroup" data-v="pause">Pause 48h</button><button class="ld-btn" data-act="regroup" data-v="light">Light mode this week</button><button class="ld-btn ld-ghost" data-act="regroup" data-v="go">Carry on</button></div>' :
      (ui.banner.kind === 'bad' && ST.setupComplete ? '<div class="ld-row"><button class="ld-btn ld-small" data-act="replan">Re-plan from now</button></div>' : '') +
      '<button class="ld-x" data-act="dismissBanner" aria-label="Dismiss">×</button>') + '</div>';
    if (!Host.connected() && ST.setupComplete && !ST.ui.hostNoticeDismissed) banner += '<div class="ld-banner ld-b-warn"><p>LifeMax was not detected on this page, so quests and XP are not connected. Your plan still works; completions are kept in Life Director until you open it inside LifeMax.</p><button class="ld-x" data-act="dismissHost" aria-label="Dismiss">×</button></div>';
    const body = { today: renderToday, week: renderWeek, goals: renderGoals, quests: renderQuests, setup: renderSetup, settings: renderSettings, lab: renderLab }[ui.tab] || renderToday;
    mainEl.innerHTML = banner + body();
    ui.boundary = ui.tab === 'today' && ui.dayIdx === 0 ? nextBoundary() : null;
    afterRender(tabs);
    ui.animate = false; ui.prevPlan = null; ui.fx.justDone = null;
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
      if (bf) runBuild(dial, bf);
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
  const hourWord = h => H12 ? (h === 0 ? 'Midnight' : h === 12 ? 'Noon' : (h % 12) + (h < 12 ? ' AM' : ' PM')) : String(h).padStart(2, '0') + ':00';
  /* "9 AM", "5:30 PM": short times for writing on the rings */
  const fC = ep => fTime(ep).replace(/:00(?=\D|$)/, '').replace(/\s+/g, ' ');
  function faceHtml() {
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
      l += '<text class="ld-hl' + (h % 6 ? '' : ' ld-hl6') + '" x="' + x.toFixed(1) + '" y="' + y.toFixed(1) + '"' + anc + '>' + hourWord(h) + '</text>';
    }
    /* night above, day below: midnight sits at the top */
    /* small sun and moon beside Noon and Midnight, outside the rings */
    const tw = h => hourWord(h).length * 3.3 + 10;
    const sun = (() => { const cx = 180 - tw(12) - 2, cy = 347; let r = '<circle cx="' + cx.toFixed(1) + '" cy="' + cy + '" r="2.6"/>'; for (let k = 0; k < 8; k++) { const a = k * 45 * Math.PI / 180; r += '<line x1="' + (cx + Math.cos(a) * 4.3).toFixed(1) + '" y1="' + (cy + Math.sin(a) * 4.3).toFixed(1) + '" x2="' + (cx + Math.cos(a) * 6).toFixed(1) + '" y2="' + (cy + Math.sin(a) * 6).toFixed(1) + '"/>'; } return '<g class="ld-sun">' + r + '</g>'; })();
    const moon = (() => { const cx = 180 - tw(0) - 2, cy = 13; return '<path class="ld-moon" d="M' + (cx + 1.5).toFixed(1) + ' ' + (cy - 5) + 'a5 5 0 1 0 3.6 8.6a4 4 0 1 1 -3.6 -8.6z"/>'; })();
    return '<circle class="ld-bezel" cx="180" cy="180" r="176"/><circle class="ld-face" cx="180" cy="180" r="158"/>' + t + l +
      '<circle class="ld-trk" cx="180" cy="180" r="' + DIAL.RO + '" stroke-width="' + DIAL.WO + '"/>' +
      '<circle class="ld-trk ld-trk-in" cx="180" cy="180" r="' + DIAL.RI + '" stroke-width="' + DIAL.WI + '"/>' + moon + sun;
  }
  /* words written along an arc, turned so they never read upside down */
  let lblSeq = 0;
  function arcRev(r, a0, a1) { const [x0, y0] = polar(r, a1), [x1, y1] = polar(r, a0); return 'M' + x0.toFixed(2) + ' ' + y0.toFixed(2) + 'A' + r + ' ' + r + ' 0 ' + (a1 - a0 > 180 ? 1 : 0) + ' 0 ' + x1.toFixed(2) + ' ' + y1.toFixed(2); }
  function arcText(r, a0, a1, options, cls) {
    const len = (a1 - a0) * Math.PI / 180 * r - 10;
    const txt = options.find(o => o && o.length * 6.3 + 6 <= len);
    if (!txt) return '';
    const mid = ((a0 + a1) / 2) % 360, flip = mid > 95 && mid < 265, id = 'ldtp' + (++lblSeq);
    return '<path id="' + id + '" d="' + (flip ? arcRev(r, a0, a1) : arcD(r, a0, a1)) + '" fill="none"/><text class="ld-at ' + cls + '"><textPath href="#' + id + '" startOffset="50%">' + esc(txt) + '</textPath></text>';
  }
  function blockWords(b, d0, d1) {
    const name = b.code === 'SLEEP' || /sleep/i.test(b.title) ? 'Sleep' : b.title;
    const span = b.startEpoch <= d0 && b.endEpoch < d1 ? 'until ' + fC(b.endEpoch) : b.endEpoch >= d1 && b.startEpoch > d0 ? 'from ' + fC(b.startEpoch) : fC(b.startEpoch) + '–' + fC(b.endEpoch);
    return [name + ' · ' + span, name, span];
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
  function dialHtml(day, blocks, isToday, now) {
    const d0 = day.startEpoch, d1 = day.endEpoch;
    const nextId = isToday ? (nextUp(now) || {}).instanceId : null;
    let outer = '', words = '', inner = '', i = 0;
    for (const b of blocks) {
      const k = blockClass(b);
      let [a0, a1] = [angOf(b.startEpoch, d0, d1), angOf(b.endEpoch, d0, d1)];
      if (a1 - a0 < 0.3) continue;
      if (OUTER[k]) {
        [a0, a1] = gap(a0, a1, 0.9);
        const w = arcText(DIAL.RO, a0, a1, blockWords(b, d0, d1), 'ld-c-' + k);
        outer += '<path class="ld-o ld-c-' + k + '" d="' + arcD(DIAL.RO, a0, a1) + '" stroke-width="' + DIAL.WO + '" data-act="anchor" data-t="' + esc(b.title) + '" data-s="' + b.startEpoch + '" data-e="' + b.endEpoch + '" data-k="' + k + '"' + (w ? ' data-lbl="1"' : '') + '><title>' + esc(b.title + ' ' + fTime(b.startEpoch) + '–' + fTime(b.endEpoch)) + '</title></path>';
        words += w;
      } else if (k === 'meal') {
        [a0, a1] = gap(a0, a1, 1.4);
        const d = arcD(DIAL.RI, a0, a1), chord = (a1 - a0) * Math.PI / 180 * DIAL.RI;
        let ic = '';
        if (chord >= 9) { const sz = Math.min(12, Math.floor(chord - 1)), [x, y] = polar(DIAL.RI, (a0 + a1) / 2); ic = mealPx(sz, 'ld-sico').replace('<svg class="px ld-sico"', '<svg class="px ld-sico" x="' + (x - sz / 2).toFixed(1) + '" y="' + (y - sz / 2).toFixed(1) + '"'); }
        inner += '<g class="ld-meal" data-act="anchor" data-k="meal" data-t="' + esc(b.title) + '" data-s="' + b.startEpoch + '" data-e="' + b.endEpoch + '" role="button" tabindex="0" aria-label="' + esc(b.title + ', ' + fTime(b.startEpoch) + ' to ' + fTime(b.endEpoch)) + '">' +
          '<path class="ld-hit" d="' + d + '" stroke-width="40"/><path class="ld-ma" d="' + d + '" stroke-width="' + DIAL.WI + '"/>' + ic + '</g>';
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
          '<path class="ld-hit" d="' + d + '" stroke-width="40"/>' + (nx ? '<path class="ld-nx" d="' + d + '" stroke-width="' + (DIAL.WI + 8) + '"/>' : '') + '<path class="ld-sa" d="' + d + '" stroke-width="' + DIAL.WI + '"/>' + sesIcon(b, a0, a1, st) + '</g>';
      }
    }
    let handles = '';
    for (const ri of ruleInstances(day)) {
      for (const end of ['start', 'end']) {
        const ep = end === 'start' ? ri.startEpoch : ri.endEpoch;
        if (ep < d0 || ep >= d1 || (end === 'end' && ep === d0)) continue;
        const [x, y] = polar(DIAL.RO, angOf(ep, d0, d1)), k = ri.rule.kind === 'SLEEP' ? 'sleep' : 'work';
        handles += '<g class="ld-hd ld-c-' + k + '" data-rule="' + esc(ri.rule.id) + '" data-end="' + end + '" data-ymd="' + U.ymdKey(ri.ymd) + '" data-s="' + ri.startEpoch + '" data-e="' + ri.endEpoch + '" data-ep="' + ep + '" role="slider" tabindex="0" aria-label="' + esc(handleLabel(ri.rule, end) + ' ' + fTime(ep)) + '">' +
          '<circle class="ld-hd-hit" cx="' + x.toFixed(1) + '" cy="' + y.toFixed(1) + '" r="24"/><circle class="ld-hd-ring" cx="' + x.toFixed(1) + '" cy="' + y.toFixed(1) + '" r="10.5"/><circle class="ld-hd-pip" cx="' + x.toFixed(1) + '" cy="' + y.toFixed(1) + '" r="3.5"/></g>';
      }
    }
    const live = isToday && now >= d0 && now < d1, an = live ? angOf(now, d0, d1) : 0;
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
      '<defs><linearGradient id="ldFace" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0b1122"/><stop offset=".55" stop-color="#121826"/><stop offset="1" stop-color="#1b1b22"/></linearGradient></defs>' +
      faceHtml() + '<g class="ld-outer">' + outer + '</g><g class="ld-words">' + words + '</g><g class="ld-inner">' + inner + '</g>' + past + (live ? '<g class="ld-cur">' + curRingHtml(nowState(now), now) + '</g>' : '') + '<path class="ld-drag-arc" d="" stroke-width="' + (DIAL.WO + 4) + '"/>' + hand + '<g class="ld-hds">' + handles + '</g></svg>' +
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
  function queueBuild(mode, prev) {
    const q = ui.bfQ;
    /* several changes before you look (or a run of missed-block repairs): one build, diffed against what you last saw */
    if (q && Date.now() - q.at < BF.KEEP_MIN) { if (mode === 'build') q.mode = 'build'; return; }
    ui.bfQ = { mode, prev, at: Date.now() };
  }
  function takeBuild() {
    const q = ui.bfQ; ui.bfQ = null;
    if (!q || Date.now() - q.at > BF.KEEP_MIN || reduced() || typeof Element === 'undefined' || !Element.prototype.animate) return null;
    return q;
  }
  function bfBuzz(ms) { try { if (navigator.vibrate && (!navigator.userActivation || navigator.userActivation.hasBeenActive)) navigator.vibrate(ms); } catch (e) { /* ignore */ } }
  function runBuild(dial, q) {
    const svg = dial.querySelector(':scope>svg'), core = dial.querySelector('.ld-core');
    if (!svg || !core || !ST.schedule) return;
    const NS = 'http://www.w3.org/2000/svg';
    const d0 = +dial.dataset.d0, d1 = +dial.dataset.d1, span = Math.max(1, d1 - d0), ang = ep => angOf(ep, d0, d1);
    const full = q.mode === 'build', prev = q.prev;
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
    let sweep = null;
    dial.classList.add('ld-building');

    /* what changed: sessions to launch, to orbit, to break up */
    const all = new Map((ST.schedule.scheduledInstances || []).concat(ST.schedule.historyInstances || []).map(i => [i.instanceId, i]));
    const plannedSeen = new Set((ST.schedule.scheduledInstances || []).filter(i => i.lifecycleState === 'PLANNED').map(i => i.title + '|' + i.startEpoch));
    const fly = [], keep = [], flips = [];
    for (const el of svg.querySelectorAll('.ld-inner .ld-ses')) {
      const s = +el.dataset.s, e = +el.dataset.e, a0 = ang(s), a1 = ang(e), inst = all.get(el.dataset.id);
      const planned = inst && inst.lifecycleState === 'PLANNED';
      const it = { el, a0, a1, kind: 'keep', from: 0 };
      if (!prev) it.kind = full ? 'new' : 'keep';
      else if (planned) {
        const was = prev.byId.get(el.dataset.id);
        if (was === s || prev.seen.has(inst.title + '|' + s)) it.kind = 'keep';
        else if (was != null && Math.abs((was - s) / span * 360) <= 330) { it.kind = 'moved'; it.from = (was - s) / span * 360; }
        else it.kind = 'new';
      } else if (prev.byId.has(el.dataset.id)) flips.push(it);
      (it.kind === 'keep' ? keep : fly).push(it);
    }
    const ghosts = prev ? prev.list.filter(p => !all.has(p.id) && !plannedSeen.has(p.title + '|' + p.s) && p.e > d0 && p.s < d1) : [];
    fly.sort((a, b) => a.a0 - b.a0);
    const nNew = fly.filter(f => f.kind === 'new').length, nMoved = fly.length - nNew;
    const total = svg.querySelectorAll('.ld-inner .ld-ses').length;

    /* timeline */
    const T = full ? BF.SWEEP : 0;
    const tB = full ? T + 160 : 140;
    const gapMs = fly.length > 1 ? Math.max(BF.GAP_MIN, Math.min(BF.GAP_MAX, BF.SPAN / (fly.length - 1))) : 0;
    const tFly = tB + (ghosts.length ? 260 : 0);
    const flyDur = it => it.kind === 'moved' ? Math.min(950, BF.FLY + Math.abs(it.from) * 2.2) : BF.FLY;
    let tC = tFly;
    fly.forEach((it, k) => { it.t = tFly + k * gapMs; tC = Math.max(tC, it.t + flyDur(it)); });
    tC = fly.length ? tC + 60 : tB + (ghosts.length ? 420 : 80);
    const tEnd = tC + BF.LOCK;

    /* ── phase A: the sweep ── */
    if (full) {
      sweep = document.createElement('div'); sweep.className = 'ld-bf-sweep'; sweep.setAttribute('aria-hidden', 'true'); sweep.innerHTML = '<i></i>';
      dial.insertBefore(sweep, core);
      A(sweep, [{ transform: 'rotate(0deg)' }, { transform: 'rotate(360deg)' }], { duration: T, easing: 'linear' });
      A(sweep, [{ opacity: 0 }, { opacity: 1, offset: 0.06 }, { opacity: 1, offset: 0.9 }, { opacity: 0 }], { duration: T + 160, easing: 'linear' });
      /* the fixed time is drawn exactly as the arm passes it */
      for (const p of svg.querySelectorAll('.ld-outer .ld-o')) {
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
      const spans = [...svg.querySelectorAll('.ld-outer .ld-o')].map(p => ({ s: +p.dataset.s, e: +p.dataset.e, t: /sleep/i.test(p.dataset.t) ? 'Sleep' : p.dataset.t }));
      const t0 = performance.now();
      const read = () => {
        if (over) return;
        const f = Math.min(1, (performance.now() - t0) / T), ep = d0 + f * span;
        const hit = spans.find(x => ep >= x.s && ep < x.e);
        say3('Mapping your day', fC(Math.floor(ep / 3600) * 3600), hit ? hit.t : 'Open time');
        if (f < 1) raf = requestAnimationFrame(read);
      };
      read();
    } else say3('Re-planning', '…', '');

    /* ── phase B: the planner fills the gaps ── */
    const label = !prev ? 'Placing sessions' : 'Re-planning';
    const counts = n => [nNew && (prev ? nNew + ' added' : ''), nMoved && nMoved + ' moved', ghosts.length && ghosts.length + ' dropped'].filter(Boolean).join(' · ') || (n === 1 ? 'session placed' : 'sessions placed');
    at(tB, () => say3(label, fly.length ? '0' : '–', fly.length || ghosts.length ? counts() : 'Looking for changes'));
    for (const g of ghosts) {
      const a0 = ang(g.s), a1 = ang(g.e);
      if (a1 - a0 < 0.3) continue;
      const p = mk('path', { class: 'ld-bf-ghost', d: arcD(DIAL.RI, a0, a1), 'stroke-width': DIAL.WI });
      css(p, CEN);
      A(p, [{ opacity: 0, transform: 'scale(1) rotate(0deg)' }, { opacity: 0.9, offset: 0.2, transform: 'scale(1) rotate(0deg)' }, { opacity: 0, transform: 'scale(1.2) rotate(7deg)', strokeWidth: 2 }], { duration: 640, delay: tB, easing: 'cubic-bezier(.5,0,.9,.4)' });
    }
    let landed = 0;
    fly.forEach(it => {
      const el = it.el, mid = (it.a0 + it.a1) / 2, [px, py] = polar(DIAL.RI, mid), dur = flyDur(it);
      css(el, CEN);
      if (it.kind === 'new') {
        A(el, [{ transform: 'rotate(-55deg) scale(.1)', opacity: 0 }, { transform: 'rotate(5deg) scale(1.08)', opacity: 1, offset: 0.68 }, { transform: 'rotate(0deg) scale(1)', opacity: 1 }], { duration: dur, delay: it.t, easing: 'cubic-bezier(.2,.75,.25,1)' });
      } else {
        const o = it.from, sg = o > 0 ? 1 : -1;
        A(el, [{ transform: 'rotate(' + o.toFixed(2) + 'deg)', opacity: full ? 0 : 0.55 }, { transform: 'rotate(' + o.toFixed(2) + 'deg)', opacity: 0.7, offset: 0.12 }, { transform: 'rotate(' + (-sg * 2.5) + 'deg)', opacity: 1, offset: 0.82 }, { transform: 'rotate(0deg)', opacity: 1 }], { duration: dur, delay: it.t, easing: 'cubic-bezier(.45,0,.2,1)' });
        const lo = Math.min(it.a0, it.a0 + o), hi = Math.max(it.a1, it.a1 + o);
        if (hi - lo < 359) { const tr = mk('path', { class: 'ld-bf-trail', d: arcD(DIAL.RI, Math.max(0, lo), Math.min(360, hi)) }); A(tr, [{ opacity: 0 }, { opacity: 0.75, offset: 0.25 }, { opacity: 0 }], { duration: dur + 200, delay: it.t }); }
      }
      A(el, [{ filter: 'brightness(2.6)' }, { filter: 'brightness(2.6)', offset: 0.6 }, { filter: 'brightness(1)' }], { duration: dur + 380, delay: it.t, easing: 'ease-out' });
      /* touchdown: ripple, sparks, count */
      const land = it.t + dur * (it.kind === 'new' ? 0.68 : 0.82);
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
    const anyChange = fly.length || ghosts.length || flips.length;
    at(tC, () => {
      say3(anyChange || !prev ? 'Locked in' : 'Nothing needed to move', String(total), total === 1 ? 'session' : 'sessions' + (ui.dayIdx === 0 ? ' today' : ''));
      hud.classList.add('ld-bh-lock'); bfBuzz(full ? [10, 40, 18] : 12);
    });
    const wave = mk('circle', { class: 'ld-bf-wave', cx: 180, cy: 180, r: 152, 'vector-effect': 'non-scaling-stroke' });
    css(wave, CEN);
    A(wave, [{ transform: 'scale(.45)', opacity: 0.85 }, { transform: 'scale(1.16)', opacity: 0 }], { duration: 760, delay: tC, easing: 'cubic-bezier(.1,.6,.3,1)', fill: 'none' });
    A(svg, [{ transform: 'scale(1)' }, { transform: 'scale(1.025)', offset: 0.3 }, { transform: 'scale(1)' }], { duration: 440, delay: tC, easing: 'ease-out', fill: 'none' });
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
  /* what to call the thing you are doing */
  function doingName(b) {
    if (b.inst) return b.inst.title;
    const k = blockClass(b);
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
    const f = ST.ui.focus, running = f && cur && cur.inst && f.id === cur.inst.instanceId;
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
    const f = ST.ui.focus, fi = f && findInst(f.id);
    const st = nowState(now);
    let k = '', title = '', big = '', sub = '', btn = '', cls = '';
    if (fi) {
      k = '<small class="ld-core-k ld-core-go"><i class="ld-live"></i>Timer running</small>';
      title = fi.title; big = '<span class="ld-core-big" data-fclock>' + clockText(focusMs(f)) + '</span>';
      sub = 'of ' + mins(Math.round((fi.endEpoch - fi.startEpoch) / 60)) + ' planned';
      btn = '<button class="ld-core-btn ld-core-btn-go" data-act="focusOpen">Open timer</button>';
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
      nx = '<div class="ld-core-nx"><span>Next' + (b.startEpoch - now < 3 * 3600 ? ' in ' + relText(b.startEpoch - now) : ' · ' + (same ? '' : 'Tomorrow ') + fC(b.startEpoch)) + '</span><b>' + esc(doingName(b)) + '</b></div>';
    } else nx = '<div class="ld-core-nx"><span>Nothing else planned today</span></div>';
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
      const sub = !b.inst && k === 'rest' ? '<div class="ld-ri-sub">Kept clear. Nothing gets booked here.</div>' : '';
      const attrs = b.inst ? ' data-act="' + (opts.lab ? 'labInst' : 'inst') + '" data-id="' + esc(b.inst.instanceId) + '" role="button" tabindex="0"' : '';
      return '<li class="ld-ri ld-c-' + k + (b.inst ? ' ld-ri-ses ld-s-' + sesState(st) : '') + (isNow ? ' ld-now' : '') + (past ? ' ld-past' : '') + fx.cls + justDone + '"' + attrs + (fx.tag ? ' data-fx="' + fx.tag + '"' : '') + '>' +
        '<time><b>' + fTime(b.startEpoch, z) + '</b><small>' + mins(m) + '</small></time><i class="ld-rail" aria-hidden="true"></i>' +
        '<div class="ld-ri-b"><div class="ld-ri-t">' + (b.inst ? icon(tagIcon(b.inst.tag), 13) : kindIcon(k, 13)) + '<span>' + esc(b.title) + '</span>' + stl + (isNow ? '<span class="ld-nowtag">Now</span>' : '') + '</div>' + chips + sub + '</div></li>';
    }).join('') + '</ol>';
  }
  function needSetup() {
    return '<div class="ld-empty ld-first"><div class="ld-first-dial" aria-hidden="true"><svg viewBox="0 0 360 360">' + faceHtml() +
      '<path class="ld-o ld-c-sleep" d="' + arcD(DIAL.RO, 345, 450 - 360 + 360) + '" stroke-width="' + DIAL.WO + '"/><path class="ld-o ld-c-work" d="' + arcD(DIAL.RO, 136, 256) + '" stroke-width="' + DIAL.WO + '"/>' +
      '<path class="ld-sa ld-ses-demo" d="' + arcD(DIAL.RI, 280, 300) + '" stroke-width="' + DIAL.WI + '"/><path class="ld-sa ld-ses-demo" d="' + arcD(DIAL.RI, 105, 120) + '" stroke-width="' + DIAL.WI + '"/></svg></div>' +
      '<h3>Tell it when you’re busy. It plans the rest.</h3><p>Add your sleep and your work or school hours. Life Director fits your goals and quests into the time that’s left and keeps downtime protected. You never have to build the day yourself.</p>' +
      '<button class="ld-btn ld-primary ld-wide" data-act="tab" data-tab="setup">Set up my time</button></div>';
  }
  function renderToday() {
    if (!ST.setupComplete || !ST.schedule) return needSetup();
    const s = ST.schedule, days = horizonDays(s, tz());
    ui.dayIdx = Math.min(ui.dayIdx, days.length - 1);
    const day = days[ui.dayIdx], blocks = dayBlocks(s, day, tz());
    const tasks = blocks.filter(b => b.inst);
    const now = nowEpoch(), isToday = ui.dayIdx === 0;
    const next = isToday ? nextUp(now) : null;
    const reminders = (s.passiveReminders || []).length && isToday ? '<div class="ld-card ld-remind"><h4>' + icon('flag', 12) + ' All-day quests</h4><p>' + s.passiveReminders.map(r => esc(r.title)).join(', ') + '</p></div>' : '';
    let upnext = '';
    const f = ST.ui.focus, fi = f && findInst(f.id);
    if (isToday && fi) {
      upnext = '<section class="ld-up ld-up-go"><div class="ld-up-h"><span class="ld-cue"></span>Timer running</div><h3>' + esc(fi.title) + '</h3><p class="ld-up-when"><b data-fclock>' + clockText(focusMs(f)) + '</b> of ' + mins(Math.round((fi.endEpoch - fi.startEpoch) / 60)) + ' planned</p>' +
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
      '<p class="ld-dial-hint">Tap a session for details. Drag a <i class="ld-hint-pip"></i> handle to change your sleep or work hours.</p>' +
      mixHtml(blocks) + upnext + reminders +
      '<h4 class="ld-sec">' + (isToday ? 'Rest of today' : 'Running order') + (tasks.length ? '<small>' + tasks.length + ' session' + (tasks.length === 1 ? '' : 's') + '</small>' : '') + '</h4>' +
      earlier + roHtml(ahead, tz()) +
      '<div class="ld-row ld-foot"><button class="ld-btn" data-act="disrupt">Something came up</button><button class="ld-btn ld-ghost" data-act="replan">Re-plan from now</button></div>';
  }
  /* the week: every day on one 24-hour scale */
  function weekBar(day, blocks) {
    const d0 = day.startEpoch, d1 = day.endEpoch, span = Math.max(1, d1 - d0);
    return '<div class="ld-wbar">' + blocks.map(b => {
      const k = b.inst ? 'task ld-s-' + sesState(b.inst.lifecycleState) : blockClass(b);
      if (k === 'free') return '';
      const l = (Math.max(d0, b.startEpoch) - d0) / span * 100, w = (Math.min(d1, b.endEpoch) - Math.max(d0, b.startEpoch)) / span * 100;
      return w > 0 ? '<i class="ld-c-' + k + '" style="left:' + l.toFixed(2) + '%;width:' + w.toFixed(2) + '%"></i>' : '';
    }).join('') + '</div>';
  }
  function renderWeek() {
    if (!ST.setupComplete || !ST.schedule) return needSetup();
    const s = ST.schedule, days = horizonDays(s, tz()), now = nowEpoch();
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
      return '<button class="ld-wrow' + (k === 0 ? ' ld-wtoday' : '') + '" data-act="gotoDay" data-i="' + k + '" aria-label="' + esc((k === 0 ? 'Today' : DOW[d.dow] + ' ' + d.ymd.d) + ': ' + insts.length + ' sessions, ' + mins(m)) + '">' +
        '<span class="ld-wd"><b>' + (k === 0 ? 'Today' : DOW[d.dow]) + '</b><small>' + d.ymd.d + '</small></span>' + weekBar(d, blocks) +
        '<span class="ld-wst"><b>' + insts.length + (done ? '<i>✓' + done + '</i>' : '') + '</b><small>' + (m ? mins(m) : '—') + '</small></span></button>';
    }).join('');
    const tot = s.capacity.reduce((a, c) => ({ free: a.free + c.freeMin, rest: a.rest + c.recoveryMin }), { free: 0, rest: 0 });
    const q = s.validation.qualityMetrics;
    const ticks = '<div class="ld-wticks" aria-hidden="true"><span></span><div>' + ['00', '06', '12', '18', '24'].map((h, k) => '<b style="left:' + (k * 25) + '%">' + h + '</b>').join('') + '</div><span></span></div>';
    const colOf = a => (typeof ATTR_COL !== 'undefined' && ATTR_COL && ATTR_COL[a]) || '#10B981';
    const attrs = Object.entries(byAttr).sort((a, b) => (b[1].plan + b[1].done) - (a[1].plan + a[1].done));
    const maxA = Math.max(1, ...attrs.map(([, v]) => v.plan + v.done));
    const yieldCard = attrs.length ? '<div class="ld-card ld-card-yield"><h4>Where your XP goes</h4><p class="ld-muted">Estimated with LifeMax’s own XP rules. Solid is earned, outlined is still planned.</p><ul class="ld-yield">' +
      attrs.map(([a, v]) => '<li style="--a:' + colOf(a) + '"><span>' + esc(attrName(a)) + '</span><div><i class="ld-y-done" style="width:' + (v.done / maxA * 100).toFixed(1) + '%"></i><i class="ld-y-plan" style="width:' + (v.plan / maxA * 100).toFixed(1) + '%"></i></div><b>' + Math.round(v.done + v.plan) + '</b></li>').join('') + '</ul></div>' : '';
    return '<div class="ld-stats"><div><b>' + nSes + '</b><small>sessions' + (nDone ? ', ' + nDone + ' done' : '') + '</small></div><div><b>' + mins(minTot) + '</b><small>planned</small></div><div><b>' + (xpTot ? '≈' + xpTot : '—') + '</b><small>XP on the table</small></div></div>' +
      '<div class="ld-week">' + ticks + rows + '</div>' +
      '<ul class="ld-mix-l ld-legend">' + [['sleep', 'Sleep'], ['work', 'Work & travel'], ['maint', 'Routines'], ['meal', 'Meals'], ['task', 'Sessions'], ['rest', 'Downtime']].map(([k, l]) => '<li class="ld-c-' + k + '"><i></i>' + l + '</li>').join('') + '</ul>' +
      yieldCard +
      '<div class="ld-card ld-card-fit"><h4>How the week fits</h4><p>' + mins(tot.rest) + ' of protected downtime and ' + mins(tot.free) + ' left completely free. ' +
      Math.round(q.coverageRequired * 100) + '% of must-do sessions (weekly minimums and quests) are placed.</p>' + unscheduledHtml(s) + '</div>';
    void now;
  }
  function unscheduledHtml(s, lab) {
    const list = (s.unscheduledDiagnostics || []).filter(d => d.tier);
    if (!list.length) return '<p class="ld-muted">Everything requested fits this week.</p>';
    const groups = {};
    for (const d of list) { const k = d.title + '|' + d.reasonCode; (groups[k] = groups[k] || { d, n: 0 }).n++; }
    return '<details class="ld-details"' + (lab ? ' open' : '') + '><summary>' + list.length + ' session' + (list.length === 1 ? '' : 's') + ' could not fit</summary><ul class="ld-why">' +
      Object.values(groups).sort((a, b) => (b.d.tier || 0) - (a.d.tier || 0)).map(g => '<li><b>' + esc(g.d.title) + (g.n > 1 ? ' ×' + g.n : '') + '</b> <span class="ld-tierlbl">tier ' + g.d.tier + '</span><p>' + esc(g.d.humanExplanation) + '</p></li>').join('') + '</ul></details>';
  }
  const PRIO = ['', 'Low', 'Light', 'Medium', 'High', 'Top'];
  function renderGoals() {
    const s = ST.schedule, alloc = s ? s.goalAllocation : [];
    const warn = s ? s.trajectoryWarnings : [];
    const clar = s ? s.clarificationPrompts : [];
    const cards = ST.goals.map(g => {
      const a0 = alloc.find(x => x.goalId === g.id), tw = warn.find(w => w.goalId === g.id), cp = clar.find(c => c.goalId === g.id);
      /* local repairs do not recompute target shares; use the engine's own rule (priority / sum of priorities) */
      const a = a0 && !a0.targetShare ? Object.assign({}, a0, { targetShare: (g.priority || 3) / Math.max(1, alloc.reduce((n, x) => { const gg = ST.goals.find(y => y.id === x.goalId); return n + ((gg && gg.priority) || 3); }, 0)) }) : a0;
      const arch = g.archetypeId && E.ARCHETYPES[g.archetypeId];
      const share = a ? '<div class="ld-share"><div><span>Target share</span><em><i style="width:' + Math.round(a.targetShare * 100) + '%"></i></em><b>' + Math.round(a.targetShare * 100) + '%</b></div><div><span>Scheduled</span><em><i class="act" style="width:' + Math.round((a.actualShare || 0) * 100) + '%"></i></em><b>' + Math.round((a.actualShare || 0) * 100) + '%</b></div></div><p class="ld-muted">' +
        a.plannedSessions + ' sessions · ' + mins(a.scheduledMin) + ' this week · ' + a.templates.map(t => esc(t.title) + ' ' + t.med + (t.base > t.med ? '–' + t.base : '') + '×').join(', ') + '</p>' : '';
      return '<div class="ld-card ld-goal' + (g.status === 'PAUSED' ? ' paused' : '') + '"><div class="ld-goal-h"><div><h4>' + esc(g.title) + '</h4><small>' + (arch ? esc(arch.title) : 'Custom') + ' · ' + esc((g.level || 'NOVICE').toLowerCase()) + (g.status === 'PAUSED' ? ' · paused' : '') + '</small></div>' +
        '<select class="ld-sel" data-act="goalPrio" data-id="' + esc(g.id) + '" aria-label="Priority">' + [5, 4, 3, 2, 1].map(p => '<option value="' + p + '"' + (g.priority === p ? ' selected' : '') + '>' + PRIO[p] + '</option>').join('') + '</select></div>' + share +
        (tw ? '<p class="ld-note ld-warn-t">' + esc(tw.message) + ' Projected finish: ' + fDay(tw.projectedCompletionEpoch) + '.</p>' : '') +
        (cp ? '<div class="ld-note"><p>Life Director needs to know what this goal looks like in practice. Pick the closest match:</p><div class="ld-row">' + (cp.suggestedTemplateIds.length ? cp.suggestedTemplateIds : Object.keys(E.ARCHETYPES).slice(0, 4)).map(t => '<button class="ld-btn ld-small" data-act="clarify" data-id="' + esc(g.id) + '" data-arch="' + t + '">' + esc(E.ARCHETYPES[t].title) + '</button>').join('') + '</div></div>' : '') +
        '<div class="ld-row"><button class="ld-btn ld-small ld-ghost" data-act="goalPause" data-id="' + esc(g.id) + '">' + (g.status === 'PAUSED' ? 'Resume' : 'Pause') + '</button><button class="ld-btn ld-small ld-ghost" data-act="goalDel" data-id="' + esc(g.id) + '">Remove</button></div></div>';
    }).join('');
    return '<div class="ld-card ld-explain"><h4>How your time is shared</h4><p>Each goal gets a share of activity time in proportion to its priority. First every goal reaches its weekly minimum, then deadline quests, then weekly targets, and extra volume only if your intensity setting allows it. No single goal may take more than 60% of the time while another is below its minimum.</p></div>' +
      (cards || '<div class="ld-empty"><h3>No goals yet</h3><p>Goals turn into regular sessions on your calendar. Quests that ask for the same kind of activity share those sessions instead of doubling up.</p></div>') +
      '<button class="ld-btn ld-primary ld-wide" data-act="addGoal">Add a goal</button>';
  }
  function renderQuests() {
    const cap = Host.capabilities(), s = ST.schedule;
    const head = cap.connected ? '<div class="ld-card"><h4>' + icon('flag', 12) + ' Connected to your Quest Board</h4><p>Life Director reads your quests, schedules time for the ones that need it, and checks them off on the Quest Board when the sessions are done. Quest XP is paid by LifeMax once per period, so nothing is counted twice.</p><button class="ld-btn ld-small ld-ghost" data-act="openQuests">Open Quest Board</button></div>'
      : '<div class="ld-card"><h4>Quest Board not detected</h4><p>Open Life Director inside LifeMax to connect quests. Missing: ' + esc(cap.missing.join(', ')) + '.</p></div>';
    if (!s) return head;
    const cov = s.questCoverage.filter(q => !String(q.questId).endsWith('@next'));
    const rows = cov.map(q => {
      const diag = (s.unscheduledDiagnostics || []).filter(d => (d.linkedQuestIds || []).includes(q.questId) || d.taskId === q.questId);
      const status = q.status === 'PASSIVE' ? 'All-day reminder' : q.status === 'COMPLETED' ? 'Done this period' : q.covered ? q.scheduledSessions + ' session' + (q.scheduledSessions === 1 ? '' : 's') + ' scheduled' : diag.length ? 'Could not fit' : q.status === 'LOCKED' ? 'Locked' : q.scheduledSessions + ' scheduled';
      return '<li class="ld-q ' + (q.covered || q.status === 'COMPLETED' ? 'ok' : q.status === 'PASSIVE' ? 'pas' : 'no') + '"><div><b>' + esc(q.title) + '</b><small>' + (q.recurrence === 'DAILY' ? 'Daily' : q.deadlineEpoch ? 'Due ' + fDay(q.deadlineEpoch) : 'No deadline') + '</small></div><span>' + status + '</span>' + (diag.length && !q.covered ? '<p>' + esc(diag[0].humanExplanation) + '</p>' : '') + '</li>';
    }).join('');
    const passive = (s.passiveReminders || []).map(r => '<li class="ld-q pas"><div><b>' + esc(r.title) + '</b><small>Tracked on the Quest Board</small></div><span>All-day</span></li>').join('');
    const ms = Host.milestones().filter(m => !m.locked);
    const miles = ms.length ? '<h4 class="ld-sec">Milestones<small>' + ms.filter(m => m.done).length + ' of ' + ms.length + ' earned</small></h4><p class="ld-muted">These are earned through everything else you do, so Life Director doesn’t plan time for them.</p>' +
      '<ul class="ld-qlist">' + ms.map(m => '<li class="ld-q ' + (m.done ? 'ok' : 'pas') + '"><div><b>' + esc(m.title) + '</b><small>' + esc(m.desc) + '</small></div><span>' + (m.done ? 'Earned' : 'In progress') + '</span></li>').join('') + '</ul>' : '';
    return head + '<ul class="ld-qlist">' + (rows || '<li class="ld-muted">No active quests.</li>') + passive + '</ul>' + miles;
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
  function miniDialHtml(bed, wake) {
    const a0 = bed / 4, a1 = (wake <= bed ? wake + 1440 : wake) / 4, dur = ((wake - bed) + 1440) % 1440 || 1440;
    const ctx = (ST.availability.rules || []).filter(r => r.kind !== 'SLEEP' && r.kind !== 'PREFERRED' && (r.rotation || (r.days || []).some(d => d >= 1 && d <= 5)))
      .map(r => { const s = r.startMin / 4, e = (r.startMin + r.durationMin) / 4; return '<path class="ld-o ld-c-work ld-ctx" d="' + arcD(DIAL.RO, s, e) + '" stroke-width="' + DIAL.WO + '"/>'; }).join('');
    const h = (end, a) => { const [x, y] = polar(DIAL.RO, a); return '<g class="ld-hd ld-c-sleep" data-end="' + end + '" role="slider" tabindex="0" aria-label="' + (end === 'start' ? 'Bedtime' : 'Wake up') + '"><circle class="ld-hd-hit" cx="' + x.toFixed(1) + '" cy="' + y.toFixed(1) + '" r="26"/><circle class="ld-hd-ring" cx="' + x.toFixed(1) + '" cy="' + y.toFixed(1) + '" r="12"/><circle class="ld-hd-pip" cx="' + x.toFixed(1) + '" cy="' + y.toFixed(1) + '" r="4"/></g>'; };
    return '<svg viewBox="0 0 360 360" aria-hidden="false" role="group" aria-label="Sleep window">' + faceHtml() + ctx +
      '<path class="ld-o ld-c-sleep ld-mini-arc" d="' + arcD(DIAL.RO, a0, a1) + '" stroke-width="' + DIAL.WO + '"/>' + h('start', a0) + h('end', a1 % 360) + '</svg>' +
      '<div class="ld-core"><small class="ld-core-k">Sleep</small><b class="ld-core-big ld-mini-dur">' + mins(dur) + '</b><span class="ld-core-s ld-mini-span">' + fHm(bed) + ' – ' + fHm(wake) + '</span></div>';
  }
  function renderSetup() {
    const sleeps = sleepRules();
    const wk = sleeps.find(r => r.id === 'sleep-wk') || sleeps[0], we = sleeps.find(r => r.id === 'sleep-we');
    const bed = wk ? wk.startMin : 23 * 60, wake = wk ? (wk.startMin + wk.durationMin) % 1440 : 7 * 60;
    const other = (ST.availability.rules || []).filter(r => r.kind !== 'SLEEP' && r.kind !== 'PREFERRED');
    const p = E.normalizePreferences(ST.prefs);
    const first = !ST.setupComplete;
    const steps = first ? '<ol class="ld-steps"><li class="' + (wk ? 'ok' : 'cur') + '">Sleep</li><li class="' + (other.length ? 'ok' : wk ? 'cur' : '') + '">Work & school</li><li class="' + (ST.goals.length ? 'ok' : wk ? 'cur' : '') + '">Goals</li><li>Build</li></ol>' +
      '<p class="ld-lede">Tell Life Director what your time is already spoken for. It plans everything else.</p>' : '<p class="ld-lede">Change anything here and your plan rebuilds around it automatically.</p>';
    const stepper = (k, lo, hi, st, label, hint) => '<div class="ld-step-row"><div><b>' + label + '</b><small>' + hint + '</small></div><div class="ld-stepper"><button class="ld-btn ld-small" data-act="routine" data-k="' + k + '" data-v="' + (-st) + '" data-lo="' + lo + '" data-hi="' + hi + '" aria-label="Less">−</button><output>' + (p[k] ? mins(p[k]) : 'Off') + '</output><button class="ld-btn ld-small" data-act="routine" data-k="' + k + '" data-v="' + st + '" data-lo="' + lo + '" data-hi="' + hi + '" aria-label="More">+</button></div></div>';
    return steps +
      '<div class="ld-card ld-sleepcard"><h4>' + icon('moon', 14) + ' Sleep</h4><p class="ld-muted">Drag the two handles, or type the times. Nothing is ever planned into sleep.</p>' +
      '<div class="ld-mini" data-bed="' + bed + '" data-wake="' + wake + '">' + miniDialHtml(bed, wake) + '</div>' +
      '<div class="ld-grid2"><label>' + (we ? 'Weeknight bedtime' : 'Bedtime') + '<input type="time" id="ldBed" value="' + hm(bed) + '"></label><label>Wake up<input type="time" id="ldWake" value="' + hm(wake) + '"></label></div>' +
      '<label class="ld-check"><input type="checkbox" id="ldWeekendOn"' + (we ? ' checked' : '') + '> Different on Friday and Saturday nights</label>' +
      '<div class="ld-grid2" id="ldWeekendRow"' + (we ? '' : ' hidden') + '><label>Weekend bedtime<input type="time" id="ldBedWe" value="' + hm(we ? we.startMin : (bed + 60) % 1440) + '"></label><label>Weekend wake<input type="time" id="ldWakeWe" value="' + hm(we ? (we.startMin + we.durationMin) % 1440 : (wake + 60) % 1440) + '"></label></div>' +
      '<button class="ld-btn' + (wk ? '' : ' ld-primary') + '" data-act="saveSleep">' + (wk ? 'Save sleep' : 'Save sleep and continue') + '</button></div>' +
      '<div class="ld-card ld-card-commit"><h4>' + icon('briefcase', 14) + ' Work, school and fixed commitments</h4><ul class="ld-rules">' +
      (other.map(r => '<li data-act="editRule" data-id="' + esc(r.id) + '" role="button" tabindex="0"><i class="ld-rdot ld-c-' + (r.kind === 'UNAVAILABLE' ? 'block' : 'work') + '"></i><div><b>' + esc(r.title) + '</b><small>' + esc(KIND_LABEL[r.kind] || r.kind) + ' · ' + esc(ruleText(r)) + '</small></div><span class="ld-chev" aria-hidden="true">›</span></li>').join('') ||
        '<li class="ld-muted ld-rules-empty">Nothing yet. Add your job, classes, or anything else that happens at the same time each week.</li>') +
      '</ul><button class="ld-btn" data-act="addRule">Add a commitment</button></div>' +
      '<div class="ld-card ld-card-routines"><h4>' + icon('smile', 14) + ' Daily routines</h4><p class="ld-muted">Time Life Director keeps around your day so it never books you the minute you wake up.</p>' +
      stepper('morningRoutineMinutes', 0, 120, 5, 'Getting ready', 'After you wake up') +
      stepper('windDownMinutes', 0, 120, 5, 'Wind-down', 'Before bed') +
      '<div class="ld-step-row"><div><b>Meals</b><small>Breakfast, lunch and dinner blocks</small></div><div class="ld-stepper">' + (p.mealsEnabled ? '<button class="ld-btn ld-small" data-act="routine" data-k="mealMinutes" data-v="-5" data-lo="10" data-hi="90" aria-label="Shorter">−</button><output>' + mins(p.mealMinutes) + '</output><button class="ld-btn ld-small" data-act="routine" data-k="mealMinutes" data-v="5" data-lo="10" data-hi="90" aria-label="Longer">+</button>' : '<output>Off</output>') +
      '<button class="ld-btn ld-small ld-ghost" data-act="meals">' + (p.mealsEnabled ? 'Turn off' : 'Turn on') + '</button></div></div>' +
      '<p class="ld-muted">Travel and unwind time after work are set on each commitment.</p></div>' +
      '<div class="ld-card ld-card-goals"><h4>' + icon('target', 14) + ' Goals</h4>' + (ST.goals.length ? '<div class="ld-chips ld-goalchips">' + ST.goals.map(g => '<span class="ld-chip">' + esc(g.title) + '</span>').join('') + '</div>' : '<p class="ld-muted">Add at least one goal so there is something to plan. Your LifeMax quests are included automatically.</p>') +
      '<div class="ld-row"><button class="ld-btn' + (wk && !ST.goals.length ? ' ld-primary' : '') + '" data-act="addGoal">Add a goal</button>' + (ST.goals.length ? '<button class="ld-btn ld-ghost" data-act="tab" data-tab="goals">Manage goals</button>' : '') + '</div></div>' +
      (first ? '<div class="ld-card ld-build"><h4>Build my week</h4><p class="ld-muted">Time zone: ' + esc(tz()) + '. You can change everything later.</p><button class="ld-btn ld-primary ld-wide" data-act="finishSetup"' + (wk ? '' : ' aria-disabled="true"') + '>Build my week</button></div>'
        : '<div class="ld-row ld-foot"><button class="ld-btn ld-ghost" data-act="finishSetup">Re-plan now</button></div>');
  }
  function ruleSheet(r) {
    const edit = !!r;
    r = r || { title: 'Work', kind: 'WORK', days: [1, 2, 3, 4, 5], startMin: 540, durationMin: 480, commuteMin: 0, load: 'MIXED_MODERATE', rotation: null, decompressMin: null };
    const unwind = r.decompressMin != null ? r.decompressMin : (r.kind === 'WORK' && r.load !== 'LIGHT' ? 30 : 0);
    const rot = r.rotation, on = rot ? rot.activeDays.length : 4, off = rot ? rot.cycleLength - rot.activeDays.length : 4;
    const dayChips = id => DOW.map((d, k) => '<label class="ld-dchip"><input type="checkbox" name="' + id + '" value="' + k + '"' + ((r.days || []).includes(k) && !rot ? ' checked' : '') + '><span>' + d.slice(0, 2) + '</span></label>').join('');
    const opt = (v, l, cur) => '<option value="' + v + '"' + (v === cur ? ' selected' : '') + '>' + l + '</option>';
    openSheet('<h3>' + (edit ? 'Edit ' + esc(r.title) : 'Add a commitment') + '</h3><label>Name<input id="rTitle" value="' + esc(r.title) + '" maxlength="40"></label>' +
      '<label>Type<select id="rKind">' + opt('WORK', 'Work shift', r.kind) + opt('CLASS', 'School / class', r.kind) + opt('APPOINTMENT', 'Regular commitment', r.kind) + opt('UNAVAILABLE', 'Unavailable (personal)', r.kind) + '</select></label>' +
      '<label class="ld-check"><input type="checkbox" id="rRot"' + (rot ? ' checked' : '') + '> Rotating shift pattern (e.g. 4 on / 4 off)</label>' +
      '<div id="rDaysWrap"' + (rot ? ' hidden' : '') + '><span class="ld-lbl">Days</span><div class="ld-dchips">' + dayChips('rDays') + '</div></div>' +
      '<div id="rRotWrap"' + (rot ? '' : ' hidden') + ' class="ld-grid3"><label>Days on<input type="number" id="rOn" value="' + on + '" min="1" max="14"></label><label>Days off<input type="number" id="rOff" value="' + off + '" min="1" max="14"></label><label>First day on<input type="date" id="rAnchor" value="' + esc(rot ? rot.anchorDate : U.ymdKey(U.localParts(nowEpoch(), tz()))) + '"></label></div>' +
      '<div class="ld-grid2"><label>Starts<input type="time" id="rStart" value="' + hm(r.startMin) + '"></label><label>Ends<input type="time" id="rEnd" value="' + hm((r.startMin + r.durationMin) % 1440) + '"></label></div>' +
      '<div class="ld-grid2"><label>Travel each way (min)<input type="number" id="rCommute" value="' + (r.commuteMin || 0) + '" min="0" max="240" step="5"></label><label>Unwind after (min)<input type="number" id="rUnwind" value="' + unwind + '" min="0" max="120" step="5"></label></div>' +
      '<label>How tiring<select id="rLoad">' + opt('MIXED_MODERATE', 'Mixed', r.load) + opt('PHYSICAL_HEAVY', 'Physically hard', r.load) + opt('COGNITIVE_HEAVY', 'Mentally hard', r.load) + opt('LIGHT', 'Light', r.load) + '</select></label>' +
      '<p class="ld-muted">Overnight shifts are fine: an end time earlier than the start runs into the next morning. Tiring shifts keep heavy sessions away from the hours after them.</p>' +
      '<div class="ld-row"><button class="ld-btn ld-primary" data-act="saveRule"' + (edit ? ' data-id="' + esc(r.id) + '"' : '') + '>' + (edit ? 'Save' : 'Add') + '</button><button class="ld-btn ld-ghost" data-act="closeSheet">Cancel</button>' +
      (edit ? '<button class="ld-btn ld-ghost ld-danger" data-act="delRule" data-id="' + esc(r.id) + '">Remove</button>' : '') + '</div>');
  }

  /* ── goal sheet ── */
  function goalSheet() {
    openSheet('<h3>Add a goal</h3><p class="ld-muted">Pick the closest kind of goal. Life Director turns it into regular, sensibly spaced sessions.</p><div class="ld-arch">' +
      Object.entries(E.ARCHETYPES).map(([id, a]) => '<button class="ld-archbtn" data-act="pickArch" data-arch="' + id + '">' + icon(tagIcon(a.templates[0].tag), 16) + '<b>' + esc(a.title) + '</b><small>' + esc(a.templates.map(t => t.title).join(', ')) + '</small></button>').join('') +
      '<button class="ld-archbtn" data-act="pickArch" data-arch="CUSTOM">' + icon('plus', 16) + '<b>Something else</b><small>Describe your own activity</small></button></div>');
  }
  const CUSTOM_TAGS = [['fitness.strength', 'Strength training'], ['fitness.cardio', 'Cardio'], ['fitness.mobility', 'Mobility / yoga'], ['study.deep', 'Study'], ['reading', 'Reading'], ['language', 'Language'], ['creative.practice', 'Creative practice'], ['business', 'Business / side project'], ['admin.finance', 'Money admin'], ['chores', 'Household'], ['social', 'Social'], ['wellbeing.mindfulness', 'Mindfulness']];
  function goalForm(arch) {
    const a = E.ARCHETYPES[arch];
    const vol = a && a.templates.some(t => t.volumeDriver);
    openSheet('<h3>' + (a ? esc(a.title) : 'Custom goal') + '</h3><label>Name<input id="gTitle" maxlength="50" value="' + esc(a ? a.title : '') + '" placeholder="e.g. Learn guitar"></label>' +
      (arch === 'SKILL_LANGUAGE' ? '<label>Language<input id="gSubject" value="spanish" maxlength="20"></label>' : '') +
      (a ? '' : '<label>Activity<select id="gTag">' + CUSTOM_TAGS.map(([t, l]) => '<option value="' + t + '">' + l + '</option>').join('') + '</select></label><div class="ld-grid2"><label>Minutes per session<input type="number" id="gMin" value="30" min="15" max="180" step="15"></label><label>Times per week<input type="number" id="gFreq" value="3" min="1" max="14"></label></div><div class="ld-grid2"><label>Physical effort (1–5)<input type="number" id="gIP" value="2" min="1" max="5"></label><label>Mental effort (1–5)<input type="number" id="gIC" value="2" min="1" max="5"></label></div>') +
      '<div class="ld-grid2"><label>Priority<select id="gPrio">' + [5, 4, 3, 2, 1].map(p => '<option value="' + p + '"' + (p === 3 ? ' selected' : '') + '>' + PRIO[p] + '</option>').join('') + '</select></label><label>Experience<select id="gLevel"><option value="NOVICE">New to this</option><option value="INTERMEDIATE">Some experience</option><option value="ADVANCED">Experienced</option></select></label></div>' +
      (vol ? '<details class="ld-details"><summary>Add a measurable target (optional)</summary><div class="ld-grid2"><label>Total<input type="number" id="gUnits" placeholder="3600"></label><label>Unit<input id="gUnit" placeholder="pages"></label></div><div class="ld-grid2"><label>Per hour<input type="number" id="gRate" placeholder="30"></label><label>By<input type="date" id="gDeadline"></label></div><p class="ld-muted">Life Director paces sessions to hit the date, and tells you honestly if the date is not realistic.</p></details>' : '') +
      '<div class="ld-row"><button class="ld-btn ld-primary" data-act="saveGoal" data-arch="' + esc(arch) + '">Add goal</button><button class="ld-btn ld-ghost" data-act="closeSheet">Cancel</button></div>');
  }

  /* ── settings ── */
  function renderSettings() {
    const p = E.normalizePreferences(ST.prefs);
    const seg = (key, opts) => '<div class="ld-seg">' + opts.map(([v, l]) => '<button class="' + (p[key] === v ? 'on' : '') + '" data-act="pref" data-k="' + key + '" data-v="' + v + '">' + l + '</button>').join('') + '</div>';
    const range = (key, lo, hi, step, label, hint) => '<label class="ld-range">' + label + ' <b id="v-' + key + '">' + mins(p[key]) + '</b><input type="range" min="' + lo + '" max="' + hi + '" step="' + step + '" value="' + p[key] + '" data-act="prefRange" data-k="' + key + '"><small>' + hint + '</small></label>';
    const s = ST.schedule, L = ST.ledger;
    return '<div class="ld-card"><h4>How much to schedule</h4>' + seg('intensityLevel', [['LIGHT', 'Light'], ['BALANCED', 'Balanced'], ['AGGRESSIVE', 'Ambitious'], ['HARDCORE', 'All in']]) +
      '<p class="ld-muted">Light schedules only each goal’s weekly minimum. Ambitious and All in add extra sessions when time allows.</p>' +
      range('maxDailyGoalMinutes', 30, 480, 15, 'Most activity time per day', 'A hard daily ceiling, even on free days.') +
      range('minDailyRecoveryMinutes', 30, 360, 15, 'Protected downtime per day', 'Kept clear before anything else is planned.') +
      range('maxConsecutiveHighLoadMinutes', 30, 240, 15, 'Longest run of demanding work', 'After this, Life Director inserts something lighter.') + '</div>' +
      '<div class="ld-card"><h4>Rest days</h4><p class="ld-muted">Hard workouts and heavy study are avoided on these days when possible.</p><div class="ld-dchips">' + DOW.map((d, k) => '<button class="ld-dchip-btn' + (p.preferredRestDays.includes(k) ? ' on' : '') + '" data-act="restDay" data-v="' + k + '">' + d.slice(0, 2) + '</button>').join('') + '</div></div>' +
      '<div class="ld-card"><h4>Planning window</h4>' + seg('planningHorizonDays', [[7, '7 days'], [14, '14 days']]) + '</div>' +
      '<div class="ld-card"><h4>When I miss something</h4>' + seg('consequenceMode', [['LENIENT', 'Gentle'], ['MODERATE', 'Standard'], ['HARDCORE_RPG', 'Hardcore']]) +
      '<p class="ld-muted">Gentle: no consequences, missed sessions are just re-planned. Standard: a rest token covers a miss; without one, that activity’s streak drops by one. Hardcore: also reports an HP penalty (never below 1 HP). At most one consequence per day, and never any XP or level loss. Rest tokens: <b>' + L.restTokens + '</b> of 3 (earn one every 5 completions).</p>' +
      '<label class="ld-range">Grace period before a session counts as missed <b>' + p.missGraceMinutes + 'm</b><input type="range" min="15" max="240" step="15" value="' + p.missGraceMinutes + '" data-act="prefRange" data-k="missGraceMinutes"></label></div>' +
      '<div class="ld-card"><h4>Guide</h4><p class="ld-muted">A short walkthrough of how to read each screen. It shows once on its own; you can replay it here or with the ? button at the top.</p><div class="ld-row"><button class="ld-btn ld-small" data-act="tourReset">Show the guide again</button></div></div>' +
      '<div class="ld-card"><h4>Your data</h4><p class="ld-muted">Saved on this device under its own key, separate from your LifeMax save. ' + (loadReport && loadReport.source === 'MEMORY_ONLY' ? '<b>Storage is unavailable here.</b>' : '') + '</p><div class="ld-row"><button class="ld-btn ld-small" data-act="export">Copy backup</button><button class="ld-btn ld-small" data-act="import">Restore backup</button><button class="ld-btn ld-small ld-danger" data-act="reset">Reset Life Director</button></div></div>' +
      '<details class="ld-card ld-details"><summary>Diagnostics</summary><p class="ld-mono">engine ' + E.ENGINE_VERSION + ' · ' + (s ? s.status + ' · ' + s.computeTimeMs + ' ms · quality ' + s.validation.qualityMetrics.sqi + ' · ' + s.scheduledInstances.length + ' planned' : 'no plan') +
      ' · events ' + L.order.length + ' · host ' + (Host.connected() ? 'connected' : 'missing ' + Host.capabilities().missing.join(',')) + (ST.lastRejected ? ' · last rejected plan: ' + esc(JSON.stringify(ST.lastRejected.violations)) : '') + '</p>' +
      '<label class="ld-check"><input type="checkbox" data-act="labToggle"' + (ST.ui.lab ? ' checked' : '') + '> Show Simulation Lab (developer)</label></details>';
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
      svg.removeEventListener('pointermove', mv); svg.removeEventListener('pointerup', up); svg.removeEventListener('pointercancel', up);
      host.classList.remove('drag'); hd.classList.remove('on'); ui.drag = false; ui.dragEnd = Date.now();
      onEnd(ev.type === 'pointercancel');
    };
    svg.addEventListener('pointermove', mv); svg.addEventListener('pointerup', up); svg.addEventListener('pointercancel', up);
    onMove(dialAngle(svg, e));
  }
  function moveHandle(hd, a, r) { const [x, y] = polar(r, a); hd.querySelectorAll('circle').forEach(c => { c.setAttribute('cx', x.toFixed(1)); c.setAttribute('cy', y.toFixed(1)); }); }
  /* Today's dial: drag a sleep or work handle; release asks before the rule changes */
  function attachDial(dial) {
    const svg = dial.querySelector('svg'), d0 = +dial.dataset.d0, d1 = +dial.dataset.d1, ghost = svg.querySelector('.ld-drag-arc'), core = dial.querySelector('.ld-core');
    const snap = a => {
      const ep = d0 + a / 360 * (d1 - d0), p = U.localParts(ep, tz());
      let m = Math.round((p.h * 60 + p.mi) / 15) * 15;
      const day = U.localParts(d0, tz());
      if (m >= 1440) m = 1425;
      return { m, ep: U.zonedToEpoch(day.y, day.mo, day.d, m, tz()) };
    };
    svg.addEventListener('pointerdown', e => {
      const hd = e.target.closest('.ld-hd'); if (!hd) return;
      const r = (ST.availability.rules || []).find(x => x.id === hd.dataset.rule); if (!r) return;
      const end = hd.dataset.end, s0 = +hd.dataset.s, e0 = +hd.dataset.e, orig = end === 'start' ? s0 : e0;
      const om = (() => { const p = U.localParts(orig, tz()); return p.h * 60 + p.mi; })();
      let cur = { m: om, ep: orig }, lastM = om;
      const coreWas = core.innerHTML;
      ghost.setAttribute('class', 'ld-drag-arc ld-c-' + (r.kind === 'SLEEP' ? 'sleep' : 'work'));
      dragOn(svg, hd, e, a => {
        cur = snap(a);
        if (cur.m !== lastM) { lastM = cur.m; buzz(); }
        const ns = end === 'start' ? cur.ep : s0, ne = end === 'end' ? cur.ep : e0, bad = ne - ns < 900;
        ghost.setAttribute('d', bad ? '' : arcD(DIAL.RO, angOf(ns, d0, d1), angOf(ne, d0, d1)));
        moveHandle(hd, angOf(cur.ep, d0, d1), DIAL.RO);
        core.innerHTML = '<small class="ld-core-k">' + esc(handleLabel(r, end)) + '</small><b class="ld-core-big">' + fHm(cur.m) + '</b><span class="ld-core-s">' + (bad ? 'Too short' : mins(Math.round((ne - ns) / 60)) + (r.kind === 'SLEEP' ? ' of sleep' : '')) + '</span><span class="ld-core-s">Release to apply</span>';
      }, cancelled => {
        ghost.setAttribute('d', '');
        if (cancelled || cur.m === om) { core.innerHTML = coreWas; moveHandle(hd, angOf(orig, d0, d1), DIAL.RO); return; }
        const endRel = r.startMin + r.durationMin;
        const startMin = end === 'start' ? cur.m : r.startMin;
        const dur = end === 'start' ? (((endRel - cur.m) % 1440) + 1440) % 1440 : (((cur.m - r.startMin) % 1440) + 1440) % 1440;
        ui.pendingDial = { ruleId: r.id, end, startMin, durationMin: dur, from: om, to: cur.m };
        confirmDial();
      });
    });
    svg.addEventListener('keydown', e => {
      const hd = e.target.closest && e.target.closest('.ld-hd'); if (!hd || (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight' && e.key !== 'ArrowUp' && e.key !== 'ArrowDown')) return;
      e.preventDefault(); e.stopPropagation();
      const r = (ST.availability.rules || []).find(x => x.id === hd.dataset.rule); if (!r) return;
      const step = e.key === 'ArrowRight' || e.key === 'ArrowUp' ? 15 : -15, end = hd.dataset.end;
      const startMin = end === 'start' ? (r.startMin + step + 1440) % 1440 : r.startMin;
      const dur = end === 'start' ? r.durationMin - step : r.durationMin + step;
      const om = end === 'start' ? r.startMin : (r.startMin + r.durationMin) % 1440;
      ui.pendingDial = { ruleId: r.id, end, startMin, durationMin: dur, from: om, to: (om + step + 1440) % 1440 };
      confirmDial();
    });
  }
  function confirmDial() {
    const P = ui.pendingDial, r = P && (ST.availability.rules || []).find(x => x.id === P.ruleId);
    if (!r) return;
    const isSleep = r.kind === 'SLEEP', what = handleLabel(r, P.end).toLowerCase();
    let problem = '';
    if (isSleep && P.durationMin < 300) problem = 'That leaves under 5 hours of sleep.';
    else if (isSleep && P.durationMin > 840) problem = 'That is more than 14 hours of sleep.';
    else if (!isSleep && (P.durationMin < 15 || P.durationMin > 1380)) problem = 'That makes ' + esc(r.title) + ' ' + (P.durationMin < 15 ? 'too short' : 'almost a full day') + '.';
    const who = isSleep ? 'your sleep (' + daysText(r) + ')' : esc(r.title) + ' (' + daysText(r) + ')';
    openSheet('<h3>' + (isSleep ? (P.end === 'start' ? 'Change your bedtime?' : 'Change your wake-up time?') : 'Change when ' + esc(r.title) + (P.end === 'start' ? ' starts?' : ' ends?')) + '</h3>' +
      '<p class="ld-change"><s>' + fHm(P.from) + '</s><span aria-hidden="true">→</span><b>' + fHm(P.to) + '</b></p>' +
      (problem ? '<p class="ld-note ld-warn-t">' + problem + ' Try a different time.</p><div class="ld-row"><button class="ld-btn ld-primary" data-act="dialCancel">OK</button></div>' :
        '<p class="ld-muted">This changes the ' + what + ' for ' + who + ', not just this one day. Life Director re-plans around it right away.</p>' +
        '<div class="ld-row"><button class="ld-btn ld-primary" data-act="dialApply">Change it</button><button class="ld-btn ld-ghost" data-act="dialCancel">Keep ' + fHm(P.from) + '</button></div>' +
        '<p class="ld-muted ld-small-print">Only today is different? Close this and use “Something came up” instead.</p>'));
  }
  /* My time: the sleep dial. Handles move freely; the inputs follow; Save commits. */
  function attachMini(mini) {
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
        const m = Math.round(a * 4 / 15) * 15 % 1440;
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
  function focusSheet() {
    const f = ST.ui.focus, i = f && findInst(f.id);
    if (!i) { ST.ui.focus = null; persist(); render(); return; }
    const plan = Math.max(60, i.endEpoch - i.startEpoch) * 1000, C = 2 * Math.PI * 92;
    const p = Math.min(1, focusMs(f) / plan), x = xpOf(i, Math.max(1, Math.round(focusMs(f) / 60000)));
    openSheet('<div class="ld-focus' + (f.paused ? ' paused' : '') + '"><small class="ld-core-k ld-core-go">' + (f.paused ? 'Paused' : 'In progress') + '</small><h3>' + esc(i.title) + '</h3>' +
      '<div class="ld-fring"><svg viewBox="0 0 220 220" aria-hidden="true"><circle class="ld-fr-trk" cx="110" cy="110" r="92"/><circle class="ld-fr-val" data-fring cx="110" cy="110" r="92" stroke-dasharray="' + C.toFixed(1) + '" stroke-dashoffset="' + (C * (1 - p)).toFixed(1) + '" transform="rotate(-90 110 110)"/></svg>' +
      '<div><b data-fclock>' + clockText(focusMs(f)) + '</b><small>of ' + mins(Math.round(plan / 60000)) + ' planned</small></div></div>' +
      (x ? '<p class="ld-muted ld-center">Logging now earns about <b data-fxp>+' + x.xp + ' XP</b> in ' + esc(attrName(x.rule.primary)) + '.</p>' : '') +
      '<div class="ld-row ld-center"><button class="ld-btn ld-primary" data-act="focusFinish">Finish and log</button><button class="ld-btn" data-act="' + (f.paused ? 'focusResume' : 'focusPause') + '">' + (f.paused ? 'Resume' : 'Pause') + '</button></div>' +
      '<div class="ld-row ld-center"><button class="ld-btn ld-ghost ld-small" data-act="focusCancel">Stop without logging</button></div>' +
      '<p class="ld-muted ld-center ld-small-print">You can leave this screen. The timer keeps running.</p></div>');
  }
  function focusTick() {
    const f = ST && ST.ui && ST.ui.focus; if (!f || !root || !root.isConnected) return;
    const ms = focusMs(f);
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
    const L = [], placed = [];
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
    let out = '';
    for (const l of L) {
      let [x, y] = polar(l.r, l.a);
      if (placed.some(([px, py]) => Math.abs(px - x) < 58 && Math.abs(py - y) < 24)) continue;
      const w = l.txt.length * 6.7 + 18;
      x = Math.max(w / 2 + 4, Math.min(356 - w / 2, x)); y = Math.max(14, Math.min(346, y));
      placed.push([x, y]);
      out += '<g class="ld-tl-p' + (l.k ? ' ld-c-' + l.k : '') + '"><rect x="' + (x - w / 2).toFixed(1) + '" y="' + (y - 12).toFixed(1) + '" width="' + w.toFixed(1) + '" height="24" rx="12"/><text x="' + x.toFixed(1) + '" y="' + (y + 0.5).toFixed(1) + '">' + esc(l.txt) + '</text></g>';
    }
    const g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    g.setAttribute('class', 'ld-tl'); g.dataset.k = T.key + T.i; g.innerHTML = out;
    svg.appendChild(g);
  }
  function tourSteps(key) { return (TOURS[key] || []).filter(s => (!s.sel || root.querySelector(s.sel)) && (!s.need || root.querySelector(s.need))); }
  function tourCanRun(key) {
    if (key === 'today') return !!root.querySelector('.ld-dial');
    if (key === 'setup') return !ST.setupComplete;
    if (key === 'week') return !!root.querySelector('.ld-week');
    return false;
  }
  /* first visit to a screen: offer its guide once */
  function tourAuto() {
    if (ui.tour || ui.drag) return;
    const key = ui.tab, seen = ST.ui.tours || {};
    if (!TOURS[key] || seen[key] || !tourCanRun(key)) return;
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
  }
  function tourMove(d) {
    const T = ui.tour; if (!T) return;
    const j = T.i + d;
    if (j < 0) return;
    if (j >= T.steps.length) { tourEnd(true); return; }
    T.i = j; tourShow();
  }
  /* the ? button: the guide for this screen, or Today's */
  function tourHelp() {
    const key = TOURS[ui.tab] && (ui.tab === 'setup' || tourCanRun(ui.tab)) ? ui.tab : ST.setupComplete && ST.schedule ? 'today' : 'setup';
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
    const hand = dial.querySelector('.ld-hand'); if (hand) hand.setAttribute('transform', 'rotate(' + angOf(now, d0, d1).toFixed(2) + ' 180 180)');
    /* a block started or ended: redraw so "now" and the running order move on */
    const stale = ui.boundary && now >= ui.boundary;
    if (stale && !sheetOpen) { render(); return; }
    if (!ST.ui.focus && ST.schedule) {
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
  function moveSheet(inst) {
    const s = ST.schedule, days = horizonDays(s, tz()), now = nowEpoch(), dur = inst.endEpoch - inst.startEpoch;
    const opts = [];
    days.forEach(d => {
      const blocks = dayBlocks(s, d, tz()).filter(b => b.kind === 'FREE' || (b.inst && b.inst.instanceId === inst.instanceId));
      const merged = [];
      for (const b of blocks.sort((a, c) => a.startEpoch - c.startEpoch)) { const l = merged[merged.length - 1]; if (l && l.endEpoch >= b.startEpoch) l.endEpoch = Math.max(l.endEpoch, b.endEpoch); else merged.push({ startEpoch: b.startEpoch, endEpoch: b.endEpoch }); }
      for (const g of merged) for (let t = Math.max(g.startEpoch, Math.ceil((now + 900) / 900) * 900); t + dur <= g.endEpoch; t += 1800) if (t !== inst.startEpoch) opts.push(t);
    });
    openSheet('<h3>Move “' + esc(inst.title) + '”</h3><p class="ld-muted">Open times that fit ' + mins(dur / 60) + '. The moved session is pinned so automatic re-planning leaves it alone.</p><div class="ld-times">' +
      (opts.slice(0, 48).map(t => '<button class="ld-btn ld-small" data-act="moveTo" data-id="' + esc(inst.instanceId) + '" data-v="' + t + '">' + fDay(t).replace(/,.*/, '') + ' ' + fTime(t) + '</button>').join('') || '<p>No open time fits this week.</p>') + '</div>');
  }
  function disruptSheet() {
    const d = U.ymdKey(U.localParts(nowEpoch(), tz()));
    openSheet('<h3>Something came up</h3><p class="ld-muted">Nothing here counts against you. Life Director only moves what it has to.</p>' +
      '<details class="ld-details" open><summary>Called into work or an extra commitment</summary><div class="ld-grid2"><label>Date<input type="date" id="dDate" value="' + d + '"></label><label>Travel each way (min)<input type="number" id="dCommute" value="0" min="0" step="5"></label></div><div class="ld-grid2"><label>From<input type="time" id="dStart" value="17:00"></label><label>Until<input type="time" id="dEnd" value="21:00"></label></div><button class="ld-btn" data-act="dWork">Add and re-plan</button></details>' +
      '<details class="ld-details"><summary>Lose the next few hours</summary><div class="ld-row">' + [1, 2, 3, 4, 6].map(h => '<button class="ld-btn ld-small" data-act="dBlock" data-v="' + h + '">' + h + 'h</button>').join('') + '</div></details>' +
      '<details class="ld-details"><summary>Today is a write-off</summary><button class="ld-btn" data-act="dDay">Clear the rest of today</button></details>' +
      '<details class="ld-details"><summary>I’m sick</summary><div class="ld-row">' + [1, 2, 3].map(n => '<button class="ld-btn ld-small" data-act="dSick" data-v="' + n + '">' + n + ' day' + (n > 1 ? 's' : '') + '</button>').join('') + '</div><p class="ld-muted">Demanding sessions are cleared and only must-do work is moved to after you recover.</p></details>' +
      '<details class="ld-details"><summary>Energy check-in</summary><div class="ld-row">' + [['LOW', 'Running low'], ['NORMAL', 'Normal'], ['HIGH', 'Full of energy']].map(([v, l]) => '<button class="ld-btn ld-small" data-act="dEnergy" data-v="' + v + '">' + l + '</button>').join('') + '</div><p class="ld-muted">Adjusts estimates for the next several hours, then fades out.</p></details>');
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
  }
  function closeSheet() {
    const sh = root.querySelector('.ld-sheet');
    if (sh.hidden || !sh.classList.contains('open')) return;
    const tok = ++sheetTok, panel = sh.querySelector('.ld-sheet-panel'), bg = sh.querySelector('.ld-sheet-bg');
    sh.classList.remove('open'); panel.style.transform = ''; panel.style.transition = ''; bg.style.opacity = ''; bg.style.transition = '';
    const fin = () => { if (tok === sheetTok) sh.hidden = true; };
    if (reduced()) fin(); else setTimeout(fin, 280);
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
    const act = b.dataset.act, id = b.dataset.id, v = b.dataset.v;
    if ((act === 'dismissBanner' || act === 'dismissHost') && !e.ldLeft) {
      if (leave(b.closest('.ld-banner'), () => onClick({ target: b, ldLeft: true }))) return;
    }
    if (act === 'doneQuick' || act === 'done') { ui.fx.justDone = id; if (ST.ui.focus && ST.ui.focus.id === id) ST.ui.focus = null; }
    const val = sel => { const el = root.querySelector(sel); return el ? el.value : ''; };
    const now = nowEpoch();
    switch (act) {
      case 'tab': ui.tab = b.dataset.tab; ST.ui.tab = ui.tab; persist(); render(); break;
      case 'day': ui.dayIdx = +b.dataset.i; render(); break;
      case 'earlier': ui.earlierOpen = !b.parentElement.open; break;
      case 'anchor': {
        const k = b.dataset.k, when = fTime(+b.dataset.s) + '–' + fTime(+b.dataset.e);
        say(b.dataset.t + ' · ' + when + (k === 'sleep' || k === 'work' ? '. Drag its handle to change it.' : k === 'maint' || k === 'meal' ? '. ' + (k === 'meal' ? 'Meals' : 'Routines') + ' are set in My time.' : ''));
        break;
      }
      case 'dialApply': {
        const P = ui.pendingDial, r = P && ST.availability.rules.find(x => x.id === P.ruleId);
        ui.pendingDial = null;
        if (r) { r.startMin = P.startMin; r.durationMin = P.durationMin; if (r.kind === 'SLEEP' && r.id === 'sleep-wk') ST.availability.sleepRequirementMinutes = P.durationMin; closeSheet(); autoReplan('time changed'); render(); say(handleLabel(r, P.end) + ' now ' + fHm(P.to) + ' · plan rebuilt'); }
        break;
      }
      case 'dialCancel': ui.pendingDial = null; closeSheet(); render(); break;
      case 'editRule': { const r = ST.availability.rules.find(x => x.id === id); if (r) ruleSheet(r); break; }
      case 'routine': {
        const k = b.dataset.k, p = E.normalizePreferences(ST.prefs);
        ST.prefs[k] = Math.max(+b.dataset.lo, Math.min(+b.dataset.hi, (+p[k] || 0) + (+v)));
        autoReplan('routines changed'); render(); break;
      }
      case 'meals': { const p = E.normalizePreferences(ST.prefs); ST.prefs.mealsEnabled = !p.mealsEnabled; autoReplan('routines changed'); render(); break; }
      case 'focusStart': {
        const i = findInst(id); if (!i || i.lifecycleState !== 'PLANNED') break;
        if (!doable(i, now)) { instSheet(i); break; }
        const f = ST.ui.focus;
        if (f && f.id !== id && findInst(f.id)) { say('Another timer is running. Finish or stop it first.'); focusSheet(); break; }
        if (!f || f.id !== id) ST.ui.focus = { id, start: Date.now(), paused: null, pausedMs: 0 };
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
      case 'openPlan': closeSheet(); Host.openPlanDay(v); break;
      case 'openQuests': Host.openView('quests'); break;
      case 'disrupt': disruptSheet(); break;
      case 'dWork': {
        const [y, mo, d] = val('#dDate').split('-').map(Number), s0 = parseHm(val('#dStart')), e0 = parseHm(val('#dEnd'));
        if (!y || s0 == null || e0 == null) { say('Enter a date, start and end'); break; }
        const st = U.zonedToEpoch(y, mo, d, s0, tz()), en = U.zonedToEpoch(y, mo, d, e0 <= s0 ? e0 + 1440 : e0, tz());
        const r = reschedule({ type: 'NEW_COMMITMENT', commitment: { id: 'c-' + uuid(), title: 'Extra shift', startEpoch: st, endEpoch: en, commuteMin: +val('#dCommute') || 0, kind: 'WORK' } });
        if (r) { persist(); closeSheet(); render(); say(summarize(r)); } break;
      }
      case 'dBlock': { const r = reschedule({ type: 'BLOCK_HOURS', commitment: { id: 'b-' + uuid(), title: 'Unavailable', startEpoch: now, endEpoch: now + (+v) * 3600 } }); if (r) { persist(); closeSheet(); render(); say(summarize(r)); } break; }
      case 'dDay': { const p = U.localParts(now, tz()), nx = U.ymdAdd({ y: p.y, mo: p.mo, d: p.d }, 1); const r = reschedule({ type: 'BLACKOUT_DAY', commitment: { id: 'd-' + uuid(), title: 'Day off the plan', startEpoch: now, endEpoch: U.zonedToEpoch(nx.y, nx.mo, nx.d, 0, tz()) } }); if (r) { persist(); closeSheet(); render(); say(summarize(r)); } break; }
      case 'dSick': { const r = reschedule({ type: 'SICK', commitment: { id: 's-' + uuid(), title: 'Sick', startEpoch: now, endEpoch: now + (+v) * 86400 } }); if (r) { persist(); closeSheet(); render(); say('Rest up · ' + summarize(r)); } break; }
      case 'dEnergy': ST.energyCheckin = { level: v, epoch: now }; closeSheet(); regenerate('energy check-in', true); render(); say('Energy noted'); break;
      case 'replan': if (ui.banner && ui.banner.kind === 'bad') ui.banner = null; regenerate('requested'); render(); break;
      case 'regroup': {
        if (v === 'pause') reschedule({ type: 'BLACKOUT_DAY', commitment: { id: 'p-' + uuid(), title: 'Paused', startEpoch: now, endEpoch: now + 48 * 3600 } });
        if (v === 'light') { ST.prefsLightRestore = E.normalizePreferences(ST.prefs).intensityLevel; ST.prefs.intensityLevel = 'LIGHT'; ST.prefsLightUntil = now + 7 * 86400; regenerate('light mode', true); }
        ST.ledger = Object.assign({}, ST.ledger, { consecutiveMisses: 0 }); ui.banner = null; persist(); render(); break;
      }
      case 'saveSleep': {
        const bed = parseHm(val('#ldBed')), wake = parseHm(val('#ldWake'));
        if (bed == null || wake == null) { say('Enter both times'); break; }
        const dur = ((wake - bed) + 1440) % 1440 || 480;
        if (dur < 300) { say('That is under 5 hours of sleep. Please check the times.'); break; }
        const we = root.querySelector('#ldWeekendOn').checked;
        ST.availability.rules = ST.availability.rules.filter(r => r.kind !== 'SLEEP');
        if (!we) ST.availability.rules.push({ id: 'sleep-wk', kind: 'SLEEP', title: 'Sleep', days: [0, 1, 2, 3, 4, 5, 6], startMin: bed, durationMin: dur });
        else {
          const b2 = parseHm(val('#ldBedWe')), w2 = parseHm(val('#ldWakeWe')), d2 = ((w2 - b2) + 1440) % 1440 || 480;
          ST.availability.rules.push({ id: 'sleep-wk', kind: 'SLEEP', title: 'Sleep', days: [0, 1, 2, 3, 4], startMin: bed, durationMin: dur }, { id: 'sleep-we', kind: 'SLEEP', title: 'Sleep', days: [5, 6], startMin: b2, durationMin: d2 });
        }
        ST.availability.sleepRequirementMinutes = dur; autoReplan('sleep changed'); render(); say(ST.setupComplete ? 'Sleep saved · plan rebuilt' : 'Sleep saved'); break;
      }
      case 'addRule': ruleSheet(); break;
      case 'saveRule': {
        const s0 = parseHm(val('#rStart')), e0 = parseHm(val('#rEnd'));
        if (s0 == null || e0 == null) { say('Enter start and end times'); break; }
        const rot = root.querySelector('#rRot').checked;
        const days = [...root.querySelectorAll('input[name=rDays]:checked')].map(x => +x.value);
        if (!rot && !days.length) { say('Pick at least one day'); break; }
        const on = Math.max(1, +val('#rOn') || 4), off = Math.max(1, +val('#rOff') || 4);
        const kind = val('#rKind');
        if (rot && !/^\d{4}-\d{2}-\d{2}$/.test(val('#rAnchor'))) { say('Pick the first day on'); break; }
        const rule = { id: id || 'r-' + uuid(), kind, title: val('#rTitle').trim() || KIND_LABEL[kind], days: rot ? [0, 1, 2, 3, 4, 5, 6] : days, startMin: s0, durationMin: ((e0 - s0) + 1440) % 1440 || 1440,
          commuteMin: Math.max(0, Math.min(240, +val('#rCommute') || 0)), decompressMin: Math.max(0, Math.min(120, +val('#rUnwind') || 0)), load: val('#rLoad'), rotation: rot ? { anchorDate: val('#rAnchor'), cycleLength: on + off, activeDays: Array.from({ length: on }, (_, k) => k) } : null };
        const at = ST.availability.rules.findIndex(r => r.id === rule.id);
        if (at >= 0) ST.availability.rules[at] = rule; else ST.availability.rules.push(rule);
        closeSheet(); autoReplan(at >= 0 ? 'commitment changed' : 'commitment added'); render(); say(at >= 0 ? 'Saved' + (ST.setupComplete ? ' · plan rebuilt' : '') : 'Commitment added' + (ST.setupComplete ? ' · plan rebuilt' : '')); break;
      }
      case 'delRule': ST.availability.rules = ST.availability.rules.filter(r => r.id !== id); closeSheet(); autoReplan('commitment removed'); render(); say('Removed'); break;
      case 'finishSetup': {
        if (!ST.availability.rules.some(r => r.kind === 'SLEEP')) { say('Save your sleep times first'); const c = root.querySelector('.ld-sleepcard'); if (c) { c.scrollIntoView({ block: 'center' }); play(c, 'ld-ping'); } break; }
        ST.setupComplete = true; if (regenerate('setup')) { ui.tab = 'today'; ST.ui.tab = 'today'; ui.dayIdx = 0; } persist(); render(); break;
      }
      case 'addGoal': goalSheet(); break;
      case 'pickArch': goalForm(b.dataset.arch); break;
      case 'saveGoal': {
        const arch = b.dataset.arch, title = val('#gTitle').trim();
        if (!title) { say('Give the goal a name'); break; }
        const g = { id: 'g-' + uuid().slice(0, 12), title, priority: +val('#gPrio') || 3, level: val('#gLevel') || 'NOVICE', status: 'ACTIVE', createdEpoch: now };
        if (arch !== 'CUSTOM') g.archetypeId = arch; else g.customTemplates = [{ title, tag: val('#gTag'), minutes: +val('#gMin') || 30, perWeek: +val('#gFreq') || 3, IP: +val('#gIP') || 2, IC: +val('#gIC') || 2 }];
        if (root.querySelector('#gSubject')) g.subject = val('#gSubject').trim() || null;
        const units = +val('#gUnits'), rate = +val('#gRate'), dl = val('#gDeadline');
        if (units > 0 && rate > 0 && dl) { const [y, mo, d] = dl.split('-').map(Number); g.target = { units, completed: 0, unit: val('#gUnit') || 'units', unitsPerHour: rate, deadlineEpoch: U.zonedToEpoch(y, mo, d, 1439, tz()) }; }
        ST.goals.push(g); closeSheet();
        if (ST.setupComplete) regenerate('goal added', true); else persist();
        render(); say('Goal added'); break;
      }
      case 'clarify': { const g = ST.goals.find(x => x.id === id); if (g) { g.archetypeId = b.dataset.arch; regenerate('goal clarified', true); render(); } break; }
      case 'goalPause': { const g = ST.goals.find(x => x.id === id); if (g) { g.status = g.status === 'PAUSED' ? 'ACTIVE' : 'PAUSED'; regenerate('goal changed', true); render(); } break; }
      case 'goalDel': { if (!confirm('Remove this goal? Its future sessions are removed; completed ones stay in your history.')) break; ST.goals = ST.goals.filter(x => x.id !== id); regenerate('goal removed', true); render(); break; }
      case 'pref': { const key = b.dataset.k; ST.prefs[key] = isNaN(+v) ? v : +v; if (key === 'intensityLevel') ST.prefsLightUntil = null; if (ST.setupComplete) regenerate('settings', true); else persist(); render(); break; }
      case 'restDay': { const p = E.normalizePreferences(ST.prefs); const k = +v; ST.prefs.preferredRestDays = p.preferredRestDays.includes(k) ? p.preferredRestDays.filter(x => x !== k) : p.preferredRestDays.concat(k); if (ST.setupComplete) regenerate('settings', true); else persist(); render(); break; }
      case 'labToggle': ST.ui.lab = b.checked; persist(); render(); break;
      case 'export': { const txt = store.exportJson(ST); (navigator.clipboard ? navigator.clipboard.writeText(txt) : Promise.reject()).then(() => say('Backup copied to clipboard'), () => { openSheet('<h3>Backup</h3><textarea class="ld-ta" readonly>' + esc(txt) + '</textarea>'); }); break; }
      case 'import': openSheet('<h3>Restore backup</h3><p class="ld-muted">Paste a Life Director backup. This replaces Life Director data only; your LifeMax save is not touched.</p><textarea class="ld-ta" id="ldImport"></textarea><button class="ld-btn ld-primary" data-act="doImport">Restore</button>'); break;
      case 'doImport': { try { ST = store.importJson(val('#ldImport')); persist(); closeSheet(); render(); say('Backup restored'); } catch (err) { say(err.message); } break; }
      case 'reset': if (confirm('Reset Life Director? Your schedule, goals and settings here are cleared. LifeMax XP and quests are not affected.')) { store.clear(); boot(); render(); } break;
      case 'closeSheet': case 'sheetBg': closeSheet(); break;
      case 'tour': tourHelp(); break;
      case 'tourNext': tourMove(1); break;
      case 'tourBack': tourMove(-1); break;
      case 'tourEnd': tourEnd(false); break;
      case 'tourReset': ST.ui.tours = {}; persist(); if (ST.setupComplete && ST.schedule) { ui.tab = 'today'; ST.ui.tab = 'today'; ui.dayIdx = 0; render(); } else { ui.tab = 'setup'; render(); } break;
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
    if (t.dataset && t.dataset.act === 'prefRange') { ST.prefs[t.dataset.k] = +t.value; if (ST.setupComplete) regenerate('settings', true); else persist(); render(); }
    if (t.dataset && t.dataset.act === 'goalPrio') { const g = ST.goals.find(x => x.id === t.dataset.id); if (g) { g.priority = +t.value; regenerate('priority', true); render(); } }
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
  let glanceEl = null, glanceTimer = null;
  function glanceHtml() {
    const head = t => '<div class="ov-h ld-gl-h"><h3>' + t + '</h3><button type="button" class="ov-link" data-act="glanceOpen">Life Director ›</button></div>';
    if (!ST.setupComplete || !ST.schedule) {
      return head('Today’s plan') + '<div class="ld-gl-empty"><p>Tell Life Director when you’re busy and it fits your goals into the rest of the day.</p><button type="button" class="ld-btn ld-primary" data-act="glanceOpen">Plan my days</button></div>';
    }
    const days = horizonDays(ST.schedule, tz()), day = days[0], now = nowEpoch();
    if (!day) return head('Today’s plan') + '<p class="ld-muted">No plan for today yet.</p>';
    const blocks = dayBlocks(ST.schedule, day, tz()), tasks = blocks.filter(b => b.inst);
    const left = tasks.filter(b => b.inst.lifecycleState === 'PLANNED' && b.endEpoch > now), done = tasks.filter(b => sesState(b.inst.lifecycleState) === 'done').length;
    const m = left.reduce((n, b) => n + Math.round((b.endEpoch - Math.max(now, b.startEpoch)) / 60), 0);
    const foot = tasks.length ? (left.length ? left.length + ' session' + (left.length === 1 ? '' : 's') + ' left · ' + mins(Math.round(m / 15) * 15 || m) : 'Every session done') + (done ? '<span>' + done + ' done</span>' : '') : 'No sessions today';
    return head('Today’s plan') + dialHtml(day, blocks, true, now) + '<div class="ld-gl-foot">' + foot + '</div>';
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
      el.hidden = false;
      const dial = el.querySelector('.ld-dial');
      if (dial) { dial.querySelectorAll('.ld-hd').forEach(h => h.remove()); if (opts && opts.intro && !reduced()) play(dial, 'ld-dial-in'); }
      if (!glanceTimer) glanceTimer = setInterval(() => {
        const v = glanceEl && glanceEl.closest('.view');
        if (!glanceEl || !glanceEl.isConnected || document.hidden || (v && !v.classList.contains('active'))) return;
        glance(glanceEl);
      }, 30 * 1000);
      return true;
    } catch (err) { console.warn('[Life Director] glance', err); el.hidden = true; return false; }
  }
  /* jump from the Overview into Life Director, then repeat the tap there */
  function openFromGlance(t) {
    if (typeof navigate === 'function') navigate('life-director');
    if (ST) { ui.tab = ST.setupComplete ? 'today' : 'setup'; ui.dayIdx = 0; ST.ui.tab = ui.tab; }
    const v = document.getElementById('view-life-director');
    if (window.LifeMaxFeatures && LifeMaxFeatures.list && LifeMaxFeatures.list().includes(FEATURE_ID)) LifeMaxFeatures.open(FEATURE_ID); else mount(v);
    if (!t || !root) return;
    const d = t.dataset, sel = '[data-act="' + d.act + '"]' + (d.id ? '[data-id="' + CSS.escape(d.id) + '"]' : '') + (d.s ? '[data-s="' + d.s + '"]' : '');
    const twin = root.querySelector('.ld-dial ' + sel) || root.querySelector(sel);
    if (twin) setTimeout(() => twin.dispatchEvent(new MouseEvent('click', { bubbles: true })), 30);
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
      if (!timer) { timer = setInterval(tick, 5 * 60 * 1000); setInterval(liveTick, 15 * 1000); setInterval(focusTick, 1000); }
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
        const list = TABS.concat(ST.ui && ST.ui.lab ? [['lab', 'Lab']] : []).map(x => x[0]), i = list.indexOf(ui.tab), j = i + (dx < 0 ? 1 : -1);
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
  return { mount, glance, _debug: () => ({ ST, ui }), regenerate: r => { regenerate(r || 'api'); render(); }, getSchedule: () => ST && ST.schedule, workRules, shifts, FEATURE_ID };
})();

/* Public API for LifeMax or other features */
window.LifeDirector = Object.freeze({
  version: LifeDirectorEngine.ENGINE_VERSION, engine: LifeDirectorEngine,
  getSchedule: () => LifeDirectorApp.getSchedule(), regenerate: r => LifeDirectorApp.regenerate(r),
  glance: (el, opts) => LifeDirectorApp.glance(el, opts),
  workRules: () => LifeDirectorApp.workRules(), shifts: (a, b) => LifeDirectorApp.shifts(a, b)
});
/* the Overview was drawn before this feature loaded: put today's dial on it now */
try { const g = document.getElementById('ovPlan'); if (g) LifeDirectorApp.glance(g); } catch (e) { /* the Overview will try again on its next draw */ }
if (window.LifeMaxFeatures && typeof window.LifeMaxFeatures.register === 'function') {
  LifeMaxFeatures.register({ id: 'life-director', mount: el => LifeDirectorApp.mount(el) });
} else {
  /* Standalone harness: no Forge registry, mount directly */
  document.addEventListener('DOMContentLoaded', () => { const v = document.getElementById('view-life-director'); if (v) LifeDirectorApp.mount(v); });
}