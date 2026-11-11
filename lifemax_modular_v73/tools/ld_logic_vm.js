/* The Life Director controller module (src/features/life-director/logic.js) as text, for the read-model recorder (WP-LD8a,
   tools/gen_ld_readmodels_golden.js) and its web test.

   logic.js holds the controller (`const LifeDirectorApp = (function () { ... })();`) and, after it, the page wiring (window.LifeDirector,
   the Overview, the feature registry). appSource() returns the controller exactly as the file has it. readModelSource() returns the same
   text with one statement added just before the controller's final `return { mount, ...`: when a function named __ldReadModels is in
   scope it is handed the controller's own read-model functions and tables (READ_MODELS below) and a setter for the controller's state
   ST. Nothing else changes, so every function the recorder calls is the page's own, and with no __ldReadModels in scope the statement does
   nothing. The recorder evaluates that text inside the built page in Chromium with new Function, giving the controller three names of its
   own: LifeDirectorEngine (the page's engine), LifeMaxHost (the page's host with connected() and xpFor() watched) and Date (the page's
   Date with now() and new Date() pinned to the case's moment); the page's own controller and its saved state are never touched.

   Use: const { readModelSource, appSource } = require('./ld_logic_vm.js'); */
'use strict';
const fs = require('fs'), path = require('path');

const LOGIC = path.join(__dirname, '..', 'src', 'features', 'life-director', 'logic.js');
const START = 'const LifeDirectorApp = (function () {';
const END = '\n})();\n';
const RETURN = '  return { mount, glance, widgetSnapshot,';
/* What the hook hands out: the read models of WP-LD8a, the helpers they are made of, and the tables and functions whose words the
   recorder exports (tools/gen_ld_readmodels_golden.js WORDS). Each name must be a function or constant of the controller. */
const READ_MODELS = ['horizonDays', 'unplannedSegments', 'dayBlocks', 'liveTimeline', 'nowState', 'weekModel', 'weekSegs', 'nativeWeek',
  'unscheduledGroups', 'unfitSummary', 'calendarDays', 'blockClass', 'sesState', 'xpOf', 'mins', 'attrName', 'tz', 'nowEpoch',
  'DOW', 'KIND_CLASS', 'WEEK_LEGEND', 'WEEK_TICKS', 'YIELD_NOTE', 'ALL_FIT', 'SOON', 'ENDING', 'LATE'];

/* The controller module's text exactly as the file has it. */
function appSource() {
  const text = fs.readFileSync(LOGIC, 'utf8'), start = text.indexOf(START), end = text.indexOf(END, start);
  if (start < 0 || end < 0 || text.indexOf(START, start + 1) >= 0) throw new Error('logic.js: the controller module markers moved; update tools/ld_logic_vm.js');
  return text.slice(start, end + END.length);
}

/* The controller with the read-model hook put in before its final return (see above). */
function readModelSource(names) {
  const src = appSource(), at = src.indexOf(RETURN);
  if (at < 0 || src.indexOf(RETURN, at + 1) >= 0) throw new Error('logic.js: `' + RETURN.trim() + '` is not there exactly once; update tools/ld_logic_vm.js');
  return src.slice(0, at) + "  if (typeof __ldReadModels === 'function') __ldReadModels({ " + (names || READ_MODELS).join(', ')
    + ', setState: s => { ST = s; } });\n' + src.slice(at);
}

module.exports = { appSource, readModelSource, READ_MODELS, LOGIC };
