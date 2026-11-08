/* Loads the Life Director engine (src/features/life-director/engine.js) without a page, for recorders and tests.

   engine.js holds three modules (the engine, the store, the LifeMax host); the engine and the store both assign module.exports, so a
   plain require() returns the store and never the engine (LIFE_DIRECTOR_NATIVE_PLAN.md 4.2(8), defect D5). This loader takes only the first
   module, the pure engine, and runs it in a vm context of its own: no DOM, no storage, no clock (the engine reads performance.now()
   only to time a plan, and the context has none, so that reads 0), no randomness.

   The engine keeps a few helpers private (cyrb53, parseYmd, daysBetween, the slot grid, dayEndAfter). kernelSource() returns the
   engine module with one line added before its final `return Object.freeze(api);` that hangs those helpers on the exported object
   as `__kernel`. Nothing else in the text changes, so every helper is the engine's own function; the recorder runs the same text
   inside the real page in Chromium (tools/gen_ld_kernel_golden.js), so both runners execute identical code under their own Intl.

   Use: const { load, kernelSource } = require('./ld_engine_vm.js'); const E = load(); E.util.localParts(0, 'UTC'); E.__kernel.cyrb53('a', 1) */
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm');

const ENGINE = path.join(__dirname, '..', 'src', 'features', 'life-director', 'engine.js');
const STORE_MARK = '/* ── Life Director Store';
const FREEZE = 'return Object.freeze(api);';
/* The private helpers the time and hash kernel needs; each name must be a function or constant of the engine module. */
const KERNEL = ['SLOT_SEC', 'SLOTS_PER_HOUR', 'cyrb53', 'stableId', 'mulberry32', 'ceilTo15', 'isValidTimeZone', 'localParts', 'tzOffsetSec',
  'zonedToEpoch', 'ymdAdd', 'ymdKey', 'parseYmd', 'daysBetween', 'buildHorizon', 'createGrid', 'slotOf', 'slotCeil', 'epochOf', 'dayEndAfter'];

/* The engine module's text exactly as the file has it (the first of the three modules). */
function engineSource() {
  const text = fs.readFileSync(ENGINE, 'utf8'), end = text.indexOf(STORE_MARK);
  if (end < 0) throw new Error('engine.js: the store module marker is gone; update tools/ld_engine_vm.js');
  return text.slice(0, end);
}

/* The engine module with the kernel helpers exported as api.__kernel (see above). */
function kernelSource() {
  const src = engineSource(), at = src.indexOf(FREEZE);
  if (at < 0 || src.indexOf(FREEZE, at + 1) >= 0) throw new Error('engine.js: `' + FREEZE + '` is not there exactly once');
  return src.slice(0, at) + 'api.__kernel = Object.freeze({ ' + KERNEL.join(', ') + ' });\n' + src.slice(at);
}

/* A fresh engine in its own vm context: { ...the engine's exports, __kernel }. */
function load() {
  const ctx = vm.createContext({});
  vm.runInContext(kernelSource(), ctx, { filename: 'engine.js' });
  if (!ctx.LifeDirectorEngine || !ctx.LifeDirectorEngine.__kernel) throw new Error('engine.js did not define LifeDirectorEngine');
  return ctx.LifeDirectorEngine;
}

module.exports = { load, engineSource, kernelSource, KERNEL, ENGINE };
