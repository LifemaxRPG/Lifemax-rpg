/* Life Director 1.4.0 — Forge feature for LifeMax. Engine + store + host adapter unchanged from 1.3; interface rebuilt around the chrono dial. */
/*! Life Director Engine v1.0.0 — LifeMax
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
      kind: owner ? 'TASK' : (blk ? blk.kind : code === SLOT.RECOVERY ? 'RECOVERY' : 'FREE'), instanceId: owner ? owner.instanceId : null, refId: blk && blk.refId != null ? blk.refId : null });
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
   trigger.type: MISSED | SKIPPED | RESIDUAL | MOVE | NEW_COMMITMENT | BLOCK_HOURS | BLACKOUT_DAY | SICK | QUEST_CHANGED | TIME_CHANGED
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
    case 'TIME_CHANGED': {
      // Existing sessions are held by the repair pass below. Only unmet tasks
      // that the new availability can support are proposed as additions.
      const existing = new Set(prevPlanned.map(i => i.taskId));
      const proposed = generateSchedule(input);
      if (proposed.status !== 'INVARIANT_VIOLATION') for (const i of proposed.scheduledInstances) {
        if (i.source && !existing.has(i.taskId)) toPlace.push({ source: i.source, reason: 'TIME_FREED', preferBwcEpoch: now });
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
      // Standalone hosts without the shared fence must fail closed for native-owned saves.
      let blocked;
      try {
        if (typeof timerWriteFence === 'function') blocked = timerWriteFence(storage, 'director', state);
        else {
          const current = JSON.parse(storage.getItem(KEY) || 'null');
          const game = JSON.parse(storage.getItem('lifemax_rpg_prototype_v1') || 'null');
          if ([state, current, game].some(s => s && s.timerAuthority && s.timerAuthority.owner === 'NATIVE'))
            blocked = 'Native timer save guard is unavailable.';
        }
      } catch (_) { blocked = 'Timer save state could not be verified.'; }
      if (blocked) return { ok: false, error: blocked };
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
    /* A reset is a durable fresh state, not a missing primary key: load() would
       otherwise interpret the missing key as damage and resurrect BAK. Keep the
       previous state as the ordinary recovery copy; never delete before saving. */
    function clear(opts) { return save(defaultState(opts)); }
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
  /* Self-confirmed daily cards without a duration are tasks, not timed practice. */
  H.isUntimedQuest = function (q) {
    if (!q || q.hand !== true || q.cadence !== 'daily' || q.verify !== 'self') return false;
    const text = String(q.name || '') + ' ' + String(q.desc || '');
    return !parseMinutes(text) && !/\b(?:minutes?|mins?|hours?|hrs?|seconds?|secs?)\b/i.test(text);
  };
  // Resolve retired daily cards by an exact, unique authored template, never by guesswork.
  H.retiredUntimedQuest = function (ref) {
    const id = String(ref && ref.questId || '');
    if (!/^dh_/.test(id) || typeof QUEST_LIBRARY === 'undefined' ||
        state.quests.some(q => q && String(q.id) === id)) return null;
    const matches = QUEST_LIBRARY.filter(q => q.name === ref.questTitle);
    if (matches.length !== 1 || !H.isUntimedQuest(Object.assign({}, matches[0], { hand: true, cadence: 'daily' }))) return null;
    return { id, title: matches[0].name, schedulingMode: 'NONE', anchor: 'WAKE' };
  };
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
      const passive = PASSIVE_KINDS.has(q.kind) || q.kind === 'nutrition' || H.isUntimedQuest(q);
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
  H.xpFor = function (type, minutes, quality, dateKey, commit) {
    const R = rules[type] || rules.custom;
    const dur = Math.max(0, Number(minutes) || 0);
    const q = dur < 20 ? Math.min(1, quality || 1) : (quality || 1);
    const total = Math.max(0, Math.round(R.base * ecoDur(dur) * q));
    const day = dateKey || new Date().toLocaleDateString('en-CA');
    const ec = total > 0 ? ecoAward(type, total, dur, { day: day, peek: !commit }) : { xp: 0 };
    let xp = ec.xp;
    const p = R.primary, s = R.secondary;
    let pXP = xp, sXP = 0; if (s && s !== p) { pXP = Math.round(xp * 0.7); sXP = xp - pXP; }
    pXP = ecoStarXP(p, pXP); if (s && s !== p) sXP = ecoStarXP(s, sXP);
    xp = pXP + sXP;
    return { xp, rule: R, split: [{ attr: p, xp: pXP }].concat(s && s !== p ? [{ attr: s, xp: sXP }] : []) };
  };
  /* Stream 1 (spec §5.3): exactly one LifeMax activity per CompletionEvent. */
  H.awardActivity = function (event, info) {
    if (!H.connected()) return { awarded: false, reason: 'HOST_NOT_CONNECTED' };
    const existing = state.activities.find(a => a.ldEventId === event.eventId);
    if (existing) return { awarded: false, reason: 'ALREADY_IN_LIFEMAX', activityId: existing.id, xp: existing.xp };
    const type = H.typeForTag(event.activityTypeTag);
    const when = new Date(event.actualStartEpoch * 1000);
    const was = JSON.stringify(state); // before the award spends any rested XP
    const x = H.xpFor(type, event.actualDurationMinutes, 1, when.toLocaleDateString('en-CA'), true);
    const act = { id: 'ld_' + event.eventId, ldEventId: event.eventId, type, name: info && info.title || event.title, icon: x.rule.icon, duration: event.actualDurationMinutes, quality: 1,
      skill: x.rule.skill, primary: x.rule.primary, secondary: x.rule.secondary || '', xp: x.xp, date: when.toISOString(),
      notes: 'Logged from Life Director' + (event.lifecycleState === 'PARTIALLY_COMPLETED' ? ' (partial)' : ''), xpSplit: x.split };
    /* execution bonus: +10% (+20% with Executive Edge) of the session's XP to Discipline, 2 to 5 XP, up to 15 a day */
    if (x.xp > 0) {
      const dayKey = when.toLocaleDateString('en-CA');
      const used = state.activities.filter(a => a.ldBonus && new Date(a.date).toLocaleDateString('en-CA') === dayKey).reduce((n, a) => n + a.ldBonus, 0);
      const b = Math.min(Math.max(0, 15 - used), Math.max(2, Math.min(5, Math.round(x.xp * (ecoPerk('exec') ? 0.2 : 0.1)))));
      if (b > 0) { act.xp += b; act.ldBonus = b; act.xpSplit = act.xpSplit.concat([{ attr: 'discipline', xp: b }]); }
    }
    state.activities.push(act);
    let saved = false;
    try { saved = save(); } catch (e) { saved = false; }
    if (!saved) { /* taken back whole by takeBack, as Log's recordActivity is: nothing the award or its save paid or spent is kept */
      takeBack(was);
      return { awarded: false, reason: typeof saveReadOnly !== 'undefined' && saveReadOnly ? 'HOST_READ_ONLY' : 'HOST_SAVE_FAILED' };
    }
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
  /* Native timer migration adapter. Dormant until BOTH saves have an explicit NATIVE
     authority epoch. Reuses the existing award functions in a synchronous sandbox:
     candidate globals are restored in finally; no real save or toast is allowed while
     preparing. No callback, await, render, or plugin may be added to these helpers. */
  let preparingTimerReward = false;
  H.prepareTimerEffect = function (input, effect) {
    if (preparingTimerReward) throw new Error('Timer reward preparation is already running');
    if (!input || !input.timerAuthority || input.timerAuthority.owner !== 'NATIVE' || !input.timerAuthority.epoch || !input.xpSchemaV2) throw new Error('Timer reward authority is not ready');
    if (!effect || !['activity', 'quest'].includes(effect.kind)) throw new Error('Unknown timer reward');
    const previous = state, previousRules = rules, previousSave = save, previousToast = toast;
    const candidate = JSON.parse(JSON.stringify(input));
    preparingTimerReward = true;
    try {
      state = candidate;
      rules = JSON.parse(JSON.stringify(candidate.customRules || previousRules));
      save = () => true;
      toast = () => {};
      if (effect.kind === 'activity') {
        const event = effect.event;
        if (!event || typeof event.eventId !== 'string' || !event.eventId || !Number.isSafeInteger(event.actualStartEpoch) || event.actualStartEpoch < 0 ||
            !Number.isSafeInteger(event.actualDurationMinutes) || event.actualDurationMinutes < 1) throw new Error('Invalid timer event');
        const result = H.awardActivity(event, { title: effect.title });
        if (!result.awarded && result.reason !== 'ALREADY_IN_LIFEMAX') throw new Error(result.reason || 'Activity reward refused');
      } else {
        const ref = effect.hostRef;
        if (!ref || !ref.questId || !ref.key || ref.future) throw new Error('Invalid quest reference');
        const quest = state.quests.find(q => q.id === ref.questId);
        // A delayed completion must not award a different day/week's quest.
        if (!quest || questKey(quest) !== ref.key) throw new Error('Quest period changed; reconciliation required');
        if (!isQuestDone(quest) && questLocked(quest)) throw new Error('Quest is locked');
        const result = H.completeQuest(ref);
        if (!result.completed && result.reason !== 'ALREADY_DONE') throw new Error(result.reason || 'Quest reward refused');
      }
      return candidate;
    } finally {
      state = previous; rules = previousRules; save = previousSave; toast = previousToast;
      preparingTimerReward = false;
    }
  };

  /* A game effect and its protocol receipt are normalized and written together. This
     avoids save()'s renderAll()/Daily Hand re-entry before the receipt has committed.
     The caller publishes UI effects only after the whole completion is acknowledged. */
  H.createTimerGameStore = function () {
    let observed = null, uncertain = false;
    const owned = value => value && value.xpSchemaV2 && value.timerAuthority && value.timerAuthority.owner === 'NATIVE' && typeof value.timerAuthority.epoch === 'string' && value.timerAuthority.epoch;
    return {
      readGame() {
        if (uncertain) throw new Error('Reconcile uncertain timer game write first');
        const raw = localStorage.getItem(STORE), value = raw && JSON.parse(raw);
        if (!owned(value) || JSON.stringify(state) !== raw) throw new Error('Game state is not a clean native-timer snapshot');
        observed = raw;
        return value;
      },
      writeGame(input) {
        if (uncertain || observed === null || saveReadOnly || !owned(input)) return false;
        if (localStorage.getItem(STORE) !== observed || JSON.stringify(state) !== observed) return false;
        const old = state, oldRules = rules;
        if (input.timerAuthority.epoch !== old.timerAuthority.epoch) return false;
        let committed = false, attempted = false;
        try {
          state = JSON.parse(JSON.stringify(input));
          rules = JSON.parse(JSON.stringify(state.customRules || oldRules));
          rebuildAttrXP(); rebuildSkillXP();
          // Migration must already be complete before ownership changes.
          ecoSparksSync(); state.customRules = rules; checkBadges(true);
          const serialized = JSON.stringify(state);
          attempted = true;
          localStorage.setItem(STORE, serialized);
          if (localStorage.getItem(STORE) !== serialized) throw new Error('Timer game write verification failed');
          observed = serialized;
          committed = true;
          return true;
        } catch (_) {
          uncertain = attempted;
          return false;
        } finally {
          if (!committed) { state = old; rules = oldRules; }
        }
      },
      reconcile() {
        if (!uncertain) return;
        const raw = localStorage.getItem(STORE), value = raw && JSON.parse(raw);
        if (!owned(value) || value.timerAuthority.epoch !== state.timerAuthority.epoch) throw new Error('Game epoch changed during timer commit');
        state = value; rules = JSON.parse(JSON.stringify(value.customRules || rules));
        observed = raw; uncertain = false;
      }
    };
  };

  /* Activities logged anywhere in LifeMax (Log page, Plan page, quests) since a cursor. */
  H.activitiesSince = function (sinceIso) {
    if (!H.connected()) return [];
    return state.activities.filter(a => a && !a.ldEventId && a.type !== 'quest' && (!sinceIso || String(a.date) > sinceIso))
      .map(a => ({ id: a.id, type: a.type, name: a.name, minutes: Number(a.duration) || 0, iso: a.date, tag: a.type === 'plan' ? (KIND_TAG[a.kind] || 'fitness') : H.tagForType(a.type), planDate: a.planDate || null }));
  };
  H.activityExists = id => H.connected() && state.activities.some(a => a.id === id);
  H.isPlanDayDone = key => { try { return typeof dayDone === 'function' && !!dayDone(key); } catch (e) { return false; } };
  /* Display the source activity's instructions, never the scheduler's placement explanation. */
  H.activityDescription = function (inst) {
    if (!H.connected() || !inst) return '';
    const refs = inst.linkedQuestRequirementIds || [];
    for (const ref of refs) {
      const id = String(ref.questId || '').replace(/@next$/, '');
      if (id.startsWith('plan:') && typeof planDay === 'function') {
        let day = null;
        try { day = planDay(id.slice(5)); } catch (e) { /* an unavailable plan has no instructions */ }
        if (day) {
          if (typeof day.description === 'string' && day.description.trim()) return day.description.trim();
          const names = (day.items || []).map(item => typeof item === 'string' ? item : item.name || item.title || '').filter(Boolean);
          if (names.length) return names.join(' · ');
        }
      }
      const quest = state.quests.find(q => q && String(q.id) === id);
      if (quest && typeof quest.desc === 'string' && quest.desc.trim()) return quest.desc.trim();
      // Daily-hand IDs expire on rotation; recover only an unambiguous authored template.
      if (/^dh_/.test(id) && typeof QUEST_LIBRARY !== 'undefined') {
        const title = ref.questTitle || inst.title;
        const matches = QUEST_LIBRARY.filter(q => q.name === title && typeof q.desc === 'string' && q.desc.trim());
        if (matches.length === 1) return matches[0].desc.trim();
      }
    }
    return '';
  };
  H.openActivity = function (inst) {
    if (!H.connected() || !inst) return false;
    for (const ref of inst.linkedQuestRequirementIds || []) {
      const id = String(ref.questId || '').replace(/@next$/, '');
      if (id.startsWith('plan:')) { H.openPlanDay(id.slice(5)); return true; }
      if (state.quests.some(q => q && String(q.id) === id)) { H.openView('quests'); return true; }
    }
    return false;
  };
  H.openPlanDay = key => {
    H.openView('plan');
    if (typeof selectDay !== 'function' || typeof planDay !== 'function' || !planDay(key)) return;
    selectDay(key);
    // Selection renders the right workout below the calendar; reveal it after layout.
    requestAnimationFrame(() => {
      const view = document.getElementById('view-plan');
      if (!view || !view.classList.contains('active') || planSel !== key) return;
      const details = view.querySelector('.plan-calendar-card + .card');
      if (!details) return;
      details.scrollIntoView({ block: 'start', behavior: typeof fxReduced === 'function' && fxReduced() ? 'auto' : 'smooth' });
      const heading = details.querySelector('h3');
      if (heading) { heading.setAttribute('tabindex', '-1'); heading.focus({ preventScroll: true }); }
    });
  };
  H._test = { parseCount, parseMinutes, weekBounds };
  return H;
})();


