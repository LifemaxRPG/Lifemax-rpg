/* Loads the Life Director engine (src/features/life-director/engine.js) without a page, for recorders and tests.

   engine.js holds three modules (the engine, the store, the LifeMax host); the engine and the store both assign module.exports, so a
   plain require() returns the store and never the engine (LIFE_DIRECTOR_NATIVE_PLAN.md 4.2(8), defect D5). This loader takes only the first
   module, the pure engine, and runs it in a vm context of its own: no DOM, no storage, no clock (the engine reads performance.now()
   only to time a plan, and the context has none, so that reads 0), no randomness.

   The engine keeps a few helpers private (cyrb53, parseYmd, daysBetween, the slot grid, dayEndAfter). kernelSource() returns the
   engine module with one line added before its final `return Object.freeze(api);` that hangs those helpers on the exported object
   as `__kernel`. Nothing else in the text changes, so every helper is the engine's own function; the recorder runs the same text
   inside the real page in Chromium (tools/gen_ld_kernel_golden.js), so both runners execute identical code under their own Intl.

   Use: const { load, kernelSource } = require('./ld_engine_vm.js'); const E = load(); E.util.localParts(0, 'UTC'); E.__kernel.cyrb53('a', 1)
   A recorder for a later part passes its own list of helper names to load(names) and kernelSource(names) (AVAILABILITY for WP-LD2, DEMAND for WP-LD3);
   placement (WP-LD4) uses load(PLACEMENT, placementSource), which also lets the recorder watch generateSchedule at its stage boundaries;
   the schedule recorder (WP-LD5) uses load(SCHEDULE, scheduleSource), whose stage calls watch the repair without stopping the plan.
   The store recorder (WP-LD7, tools/gen_ld_store_golden.js) uses storeSource() and loadStore(): the second module, LifeDirectorStore,
   exactly as the file has it, in a vm context of its own (it reads no clock, zone or randomness; it is given its storage). */
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm');

const ENGINE = path.join(__dirname, '..', 'src', 'features', 'life-director', 'engine.js');
const STORE_MARK = '/* ── Life Director Store';
const HOST_MARK = '/* ── Life Director ⇄';
const FREEZE = 'return Object.freeze(api);';
/* The private helpers the time and hash kernel needs; each name must be a function or constant of the engine module. */
const KERNEL = ['SLOT_SEC', 'SLOTS_PER_HOUR', 'cyrb53', 'stableId', 'mulberry32', 'ceilTo15', 'isValidTimeZone', 'localParts', 'tzOffsetSec',
  'zonedToEpoch', 'ymdAdd', 'ymdKey', 'parseYmd', 'daysBetween', 'buildHorizon', 'createGrid', 'slotOf', 'slotCeil', 'epochOf', 'dayEndAfter'];
/* The private helpers the availability layer needs (WP-LD2, tools/gen_ld_availability_golden.js). */
const AVAILABILITY = ['SLOT', 'clamp', 'cmpStr', 'buildHorizon', 'createGrid', 'paint', 'normalizeRule', 'ruleAppliesOn', 'materializeRule', 'deriveSleep'];

/* The private helpers and tables the demand layer needs (WP-LD3, tools/gen_ld_demand_golden.js). */
const DEMAND = ['buildHorizon', 'createGrid', 'tagProfile', 'tagDepth', 'goalTemplates', 'findQuestCycles', 'freshIds', 'weeksOf', 'goalInfoMap', 'prepTask',
  'recomputeBwc', 'questCoverage', 'TAG_PROFILE', 'QUEST_WEIGHT', 'LOAD_DRAIN'];

/* The private helpers placement needs (WP-LD4, tools/gen_ld_placement_golden.js). */
const PLACEMENT = ['prepareState', 'energyAt', 'taskEarliestLatest', 'evalInBwc', 'evalAcross', 'scorePlacement', 'anchorsDependents', 'buildRejection'];
/* Where placement's recorder watches generateSchedule: after the first scores of every task in every waking day ('cells'), and when the
   first phase, the construction, is over and the repair (WP-LD5) would start ('phase1'). Each is [the line it follows or precedes, the
   call]; the call hands the stage's local state to the function set with __kernel.stage, which may throw to stop the plan there. */
const STAGES = [
  ['after', '  for (let k = 0; k < K; k++) evalRow(k);\n', "  if (__ldStage) __ldStage('cells', { S, active, cS, cSlot, cN, nB });\n"],
  ['before', '  /* PHASE 2 — bounded', "  if (__ldStage) __ldStage('phase1', { S, tasks, active, placedMeta, history, kept, displaced, replannable });\n"]];

/* The private helpers the repair, output and validation stages need (WP-LD5, tools/gen_ld_schedule_golden.js). */
const SCHEDULE = ['prepareState', 'evalAcross', 'inputHash', 'stateFingerprint', 'explainInstance', 'segments', 'capacityByDay', 'recoveryBlocks'];
/* Where the schedule recorder watches generateSchedule (as STAGES; these only look, they never stop the plan): at the start of each
   repair target ('target'), when the repair is over ('repaired') and when the fill pass is over ('filled'). */
const SCHEDULE_STAGES = [
  ['after', '  for (const t of targets) {\n', "    if (__ldStage) __ldStage('target', { S, t, log });\n"],
  ['after', '  const repairLog = runLocalRepair(S, active.filter(t => !t.isPlaced && t.tier >= 3), placedMeta, 400);\n',
    "  if (__ldStage) __ldStage('repaired', { S, repairLog, placedMeta, active });\n"],
  ['before', '  /* PHASE 3 — diagnostics', "  if (__ldStage) __ldStage('filled', { S, placedMeta });\n"]];

/* The engine module's text exactly as the file has it (the first of the three modules). */
function engineSource() {
  const text = fs.readFileSync(ENGINE, 'utf8'), end = text.indexOf(STORE_MARK);
  if (end < 0) throw new Error('engine.js: the store module marker is gone; update tools/ld_engine_vm.js');
  return text.slice(0, end);
}

/* The store module's text exactly as the file has it (the second of the three modules, LifeDirectorStore, engine.js:2234-2340). */
function storeSource() {
  const text = fs.readFileSync(ENGINE, 'utf8'), start = text.indexOf(STORE_MARK), end = text.indexOf(HOST_MARK);
  if (start < 0 || end < start || text.indexOf(STORE_MARK, start + 1) >= 0) throw new Error('engine.js: the store module markers moved; update tools/ld_engine_vm.js');
  return text.slice(start, end);
}

/* A fresh LifeDirectorStore in its own vm context ({ create, defaultState, migrate, validate, KEY, BAK, CURRENT }). The module picks
   `self` when it exists (as in the page) and module.exports otherwise; the context has neither, so it is given a `self` to hang on. */
function loadStore() {
  const ctx = vm.createContext({ self: {} });
  vm.runInContext(storeSource(), ctx, { filename: 'engine.js#store' });
  if (!ctx.self.LifeDirectorStore) throw new Error('engine.js did not define LifeDirectorStore');
  return ctx.self.LifeDirectorStore;
}

/* The engine module with the named private helpers (KERNEL unless given) exported as api.__kernel (see above). */
function kernelSource(names) {
  const src = engineSource(), at = src.indexOf(FREEZE);
  if (at < 0 || src.indexOf(FREEZE, at + 1) >= 0) throw new Error('engine.js: `' + FREEZE + '` is not there exactly once');
  return src.slice(0, at) + 'api.__kernel = Object.freeze({ ' + (names || KERNEL).join(', ') + ' });\n' + src.slice(at);
}

/* The engine module with the named helpers exported (as kernelSource) and placement's stage calls (STAGES) put in: nothing else changes,
   and with no stage function set the calls do nothing, so every function is the engine's own. */
function placementSource(names, stages) {
  let src = engineSource();
  for (const [where, mark, call] of stages || STAGES) {
    const at = src.indexOf(mark);
    if (at < 0 || src.indexOf(mark, at + 1) >= 0) throw new Error('engine.js: `' + mark.trim() + '` is not there exactly once; update tools/ld_engine_vm.js');
    const cut = where === 'after' ? at + mark.length : at;
    src = src.slice(0, cut) + call + src.slice(cut);
  }
  const at = src.indexOf(FREEZE);
  if (at < 0 || src.indexOf(FREEZE, at + 1) >= 0) throw new Error('engine.js: `' + FREEZE + '` is not there exactly once');
  return src.slice(0, at) + 'let __ldStage = null;\napi.__kernel = Object.freeze({ ' + (names || PLACEMENT).join(', ')
    + ', stage: f => { __ldStage = f; } });\n' + src.slice(at);
}

/* placementSource with the schedule recorder's helpers and stage calls (SCHEDULE, SCHEDULE_STAGES). */
const scheduleSource = names => placementSource(names || SCHEDULE, SCHEDULE_STAGES);

/* A fresh engine in its own vm context: { ...the engine's exports, __kernel } (KERNEL's helpers unless names are given). */
function load(names, source) {
  const ctx = vm.createContext({});
  vm.runInContext((source || kernelSource)(names), ctx, { filename: 'engine.js' });
  if (!ctx.LifeDirectorEngine || !ctx.LifeDirectorEngine.__kernel) throw new Error('engine.js did not define LifeDirectorEngine');
  return ctx.LifeDirectorEngine;
}

module.exports = { load, engineSource, storeSource, loadStore, kernelSource, placementSource, scheduleSource, KERNEL, AVAILABILITY, DEMAND, PLACEMENT, SCHEDULE, ENGINE };
