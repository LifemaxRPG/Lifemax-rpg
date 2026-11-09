/* Golden cases for the Life Director engine's demand layer (WP-LD3: tags, goals and their templates, the goal plans, quests and their
   prerequisite cycles, candidate tasks, fresh ids, quest coverage, the per-task constants, the wake cycle's drain arrays, and the exp and
   pow values the energy model reads), recorded from the real page in Chromium, so that the native demand code
   (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector) is accepted only when it gives the same answer for every case.
   Regenerate when the engine's demand code changes on purpose:
     NODE_PATH=$(npm root -g) node tools/gen_ld_demand_golden.js [built-page.html]
   Output: tests/fixtures/ld-demand-golden.json.gz (gzip of one case per line; the text is what serialize() writes), and the engine's demand
   tables (ARCHETYPES, TAG_PROFILE, QUEST_WEIGHT, LOAD_DRAIN) as android-wrapper/native/src/main/resources/lifedirector/demand-tables.json,
   the copy the native code reads (never retyped). tests/ld-demand-golden.test.js records every case and the tables again, in Chromium and
   in node, and fails when either disagrees with the files. The cases are defined in tools/ld_demand_cases.js; the method is WP-LD1's: the
   engine text in a vm with its private helpers hooked (tools/ld_engine_vm.js, DEMAND), run inside the built page in Chromium and in node.

   File: {header, cases}. A case is {id, family, rule, fn, args, out}:
     family  tag | goal | template | expand | goalinfo | quest | cycle | ids | candidates | coverage | prep | drain | float | scenario | fuzz.
     rule    the LIFE_DIRECTOR_ENGINE_SPEC.md rule IDs it exercises, separated by spaces.
     fn      what was called (FN below):
             tagMatches, normalizeGoal, suggestTemplates, normalizeQuest (exported) and tagDepth, tagProfile, goalInfoMap (private): the
               engine's own function with args as given;
             goalTemplates: normalizeGoal(args[0]), then goalTemplates;
             expandGoalActivities: args [goals, prefs, now]; prefs through normalizePreferences first, as the engine's caller does;
             findQuestCycles: args[0] through normalizeQuest, then the set's members in insertion order;
             freshIds: args [used (a list, made a Set; null for none), base, count];
             weeksOf: weeksOf(buildHorizon(args[0], args[1], args[2]));
             compileCandidates: args[0] = {goals, prefs, quests, start, days, tz, now, history, kept, used}: the horizon is buildHorizon(start,
               days, tz), its weeks weeksOf, the plans expandGoalActivities(goals, normalizePreferences(prefs), now).plans, usedIds a Set of
               used; out is the engine's whole result;
             questCoverage: args [quests (through normalizeQuest), planned sessions, the status map];
             prepTask: args [H0, HN, now, task, goalInfo]: a copy of task prepared on {g: {H0, HN}, now}; out is the six members it sets;
             recomputeBwc: args[0] = {n, nowSlot, startSlot, endSlot, blocks, slots}: createGrid of n / 96 UTC days from epoch 0, each slot
               [type, load, tag, taskIP, taskIC] written into its arrays, drainP and drainC filled with 0.25 and runLen and runBack with -1
               first (so an untouched slot shows), blocks as given; out is the four arrays after recomputeBwc({g, nowSlot}, {startSlot,
               endSlot}), drains as the Float32Array holds them;
             exp, pow, square: Math.exp(x), Math.pow(b, e), x ** 2;
             demand: args [input, kept, usedIds]: the front half of generateSchedule (engine.js:1205-1228) on an engine input: its
               deriveAvailability grid, weeksOf, expandGoalActivities, the history filter, compileCandidates (kept and used given, as
               prepareState would find them), goalInfoMap, prepTask on every task, the fairness shares; out is
               {plans (each plan's numbers and its goal and template ids), clarificationPrompts, trajectoryWarnings, tasks, passiveReminders,
               rejected, questStatus, quests, goalInfo, share}.
     args    the arguments, as JSON; {$num: 'NaN' | 'Infinity' | '-Infinity' | '-0'} is that number.
     out     the return value as JSON, numbers in the same {$num} form when JSON cannot hold them, members holding undefined left out,
             or {threw: <error name>}.

   What is held still: nothing here reads a clock or a device zone; the page boots with the usual test save and is not touched. */
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const { defineCases, FAMILIES } = require('./ld_demand_cases.js');
const engineVm = require('./ld_engine_vm.js');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'ld-demand-golden.json.gz');
const TABLES = path.join(__dirname, '..', '..', 'android-wrapper', 'native', 'src', 'main', 'resources', 'lifedirector', 'demand-tables.json');

/* Runs every case; E is the engine (window.LifeDirectorEngine or the vm's), K its hook. Self-contained: it is also sent into the page. */
function runCases(E, K, cases) {
  const NUMS = { NaN: NaN, Infinity: Infinity, '-Infinity': -Infinity, '-0': -0 };
  const decode = v => (Array.isArray(v) ? v.map(decode) : v && typeof v === 'object'
    ? ('$num' in v ? NUMS[v.$num] : Object.fromEntries(Object.entries(v).map(([k, x]) => [k, decode(x)]))) : v);
  const canon = v => {
    if (typeof v === 'number') return Number.isNaN(v) ? { $num: 'NaN' } : v === Infinity ? { $num: 'Infinity' } : v === -Infinity ? { $num: '-Infinity' }
      : Object.is(v, -0) ? { $num: '-0' } : v;
    if (ArrayBuffer.isView(v)) return Array.from(v, canon);
    if (Array.isArray(v)) return v.map(canon);
    if (v && typeof v === 'object') { const o = {}; for (const k of Object.keys(v)) if (v[k] !== undefined) o[k] = canon(v[k]); return o; }
    return v === undefined ? null : v;
  };
  const PREP = ['dOptSlots', 'dMinSlots', '_prio', '_lag', '_urgBase', '_high'];
  const compile = c => {
    const prefs = E.normalizePreferences(c.prefs), days = K.buildHorizon(c.start, c.days, c.tz), weeks = K.weeksOf(days);
    return E.compileCandidates({ usedIds: new Set(c.used), plans: E.expandGoalActivities(c.goals, prefs, c.now).plans, quests: c.quests, weeks, nowEpoch: c.now,
      horizon: days, history: c.history, keptInstances: c.kept });
  };
  const drain = spec => {
    const g = K.createGrid(K.buildHorizon(0, spec.n / 96, 'UTC'), 'UTC');
    g.drainP.fill(0.25); g.drainC.fill(0.25); g.runLen.fill(-1); g.runBack.fill(-1);
    spec.slots.forEach(([type, load, tag, ip, ic], i) => { g.type[i] = type; g.load[i] = load; g.tag[i] = tag; g.taskIP[i] = ip; g.taskIC[i] = ic; });
    g.blocks = spec.blocks;
    K.recomputeBwc({ g, nowSlot: spec.nowSlot }, { startSlot: spec.startSlot, endSlot: spec.endSlot });
    return { drainP: g.drainP, drainC: g.drainC, runLen: g.runLen, runBack: g.runBack };
  };
  const demand = (inp, kept, used) => {
    const d = E.deriveAvailability({ horizonStartEpoch: inp.horizonStartEpoch, horizonDays: inp.horizonDays, timezone: inp.timezone,
      playerAvailability: inp.playerAvailability, fixedCommitments: inp.fixedCommitments, playerPreferences: inp.playerPreferences });
    const g = d.grid, now = inp.currentTimeEpoch, weeks = K.weeksOf(g.days);
    const exp = E.expandGoalActivities(inp.goals || [], d.prefs, now);
    const history = (inp.completionHistory || []).filter(h => h.startEpoch >= g.H0 - 7 * 86400);
    const comp = E.compileCandidates({ usedIds: new Set(used), plans: exp.plans, quests: inp.activeQuests || [], weeks, nowEpoch: now, horizon: g.days, history,
      keptInstances: kept });
    const gi = K.goalInfoMap(inp.goals, now), S = { g, now };
    for (const t of comp.tasks) K.prepTask(S, t, gi);
    const goalIds = [...new Set(comp.tasks.filter(t => t.goalId).map(t => t.goalId))].sort();
    const psum = goalIds.reduce((s, id) => s + (gi[id] ? gi[id].priority : 3), 0) || 1, share = {};
    for (const id of goalIds) share[id] = (gi[id] ? gi[id].priority : 3) / psum;
    return { plans: exp.plans.map(p => ({ goalId: p.goal.id, templateId: p.template.templateId, fMED: p.fMED, base: p.base, stretch: p.stretch, dMin: p.dMin, dOpt: p.dOpt,
      dMax: p.dMax, status: p.status, projection: p.projection, spacingMinH: p.spacingMinH })), clarificationPrompts: exp.clarificationPrompts,
    trajectoryWarnings: exp.trajectoryWarnings, tasks: comp.tasks, passiveReminders: comp.passiveReminders, rejected: comp.rejected, questStatus: comp.questStatus,
    quests: comp.quests, goalInfo: gi, share };
  };
  const FN = {
    tagMatches: (a, b) => E.tagMatches(a, b),
    tagDepth: (a, b) => K.tagDepth(a, b),
    tagProfile: t => K.tagProfile(t),
    normalizeGoal: g => E.normalizeGoal(g),
    suggestTemplates: t => E.suggestTemplates(t),
    goalTemplates: g => K.goalTemplates(E.normalizeGoal(g)),
    expandGoalActivities: (goals, prefs, now) => E.expandGoalActivities(goals, E.normalizePreferences(prefs), now),
    goalInfoMap: (goals, now) => K.goalInfoMap(goals, now),
    normalizeQuest: q => E.normalizeQuest(q),
    findQuestCycles: qs => [...K.findQuestCycles(qs.map(E.normalizeQuest))],
    freshIds: (used, base, count) => K.freshIds(used ? new Set(used) : null, base, count),
    weeksOf: (t, days, tz) => K.weeksOf(K.buildHorizon(t, days, tz)),
    compileCandidates: compile,
    questCoverage: (qs, planned, status) => K.questCoverage(qs.map(E.normalizeQuest), planned, status),
    prepTask: (H0, HN, now, task, gi) => { const t = Object.assign({}, task); K.prepTask({ g: { H0, HN }, now }, t, gi); return Object.fromEntries(PREP.map(k => [k, t[k]])); },
    recomputeBwc: drain,
    exp: x => Math.exp(x),
    pow: (b, e) => Math.pow(b, e),
    square: x => x ** 2,
    demand,
  };
  return cases.map(c => {
    if (!(c.fn in FN)) throw new Error('no adaptor for ' + c.fn);
    let out;
    try { out = canon(FN[c.fn](...decode(c.args))); } catch (e) { out = { threw: (e && e.name) || 'Error' }; }
    return Object.assign({}, c, { out });
  });
}

/* The engine's demand tables as the native code reads them. */
const tablesOf = (E, K) => ({ archetypes: E.ARCHETYPES, tagProfile: K.TAG_PROFILE, questWeight: K.QUEST_WEIGHT, loadDrain: K.LOAD_DRAIN });
const serializeTables = t => '{"about":"The Life Director engine\'s demand tables (engine.js ARCHETYPES, TAG_PROFILE, QUEST_WEIGHT, LOAD_DRAIN), written by '
  + 'lifemax_modular_v73/tools/gen_ld_demand_golden.js from the page; never edit by hand.",\n'
  + Object.entries(t).map(([k, v]) => '"' + k + '":{\n' + Object.entries(v).map(([kk, vv]) => JSON.stringify(kk) + ':' + JSON.stringify(vv)).join(',\n') + '}').join(',\n') + '}\n';

/* In node: the engine text in a vm. */
function recordInNode(cases) {
  const E = engineVm.load(engineVm.DEMAND);
  return { cases: runCases(E, E.__kernel, cases), tables: serializeTables(tablesOf(E, E.__kernel)) };
}

/* In Chromium: the built page's own LifeDirectorEngine for the exported functions, plus the same engine text (with the hook) evaluated in
   that page for the private helpers. The page must contain the engine text unchanged (tests/ld-demand-golden.test.js checks it). */
async function recordInChromium(file, cases) {
  const { chromium } = require('playwright');
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, timezoneId: 'UTC', locale: 'en-US', bypassCSP: true });
    const p = await ctx.newPage(), errors = [];
    p.on('pageerror', e => errors.push(String(e)));
    await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));");
    await p.goto('file://' + file);
    await p.waitForFunction(() => !!(window.LifeDirectorEngine && window.LifeDirectorEngine.util), null, { timeout: 120000 });
    const out = await p.evaluate(({ kernel, cases, runner, tables }) => {
      const holder = {};
      new Function('self', kernel)(holder);
      const K = holder.LifeDirectorEngine.__kernel, run = new Function('return ' + runner)(), tablesOf = new Function('return ' + tables)();
      return { cases: JSON.stringify(run(window.LifeDirectorEngine, K, cases)), tables: tablesOf(window.LifeDirectorEngine, K) };
    }, { kernel: engineVm.kernelSource(engineVm.DEMAND), cases, runner: runCases.toString(), tables: tablesOf.toString() });
    if (errors.length) throw new Error('the page threw: ' + errors.slice(0, 3).join(' | '));
    return { cases: JSON.parse(out.cases), tables: serializeTables(out.tables), browser: browser.version() };
  } finally { await browser.close(); }
}

const header = browser => ({
  about: 'Life Director demand golden cases; see tools/gen_ld_demand_golden.js',
  recordedIn: 'Chromium ' + browser,
  nodeCheck: 'node ' + process.versions.node + ' ICU ' + process.versions.icu + ' tz ' + process.versions.tz,
});
/* one case per line, so a change shows up in a diff of the unzipped text as the cases it changed */
const serialize = (head, cases) => '{"header":' + JSON.stringify(head) + ',"cases":[\n' + cases.map(c => JSON.stringify(c)).join(',\n') + '\n]}\n';
const compress = text => zlib.gzipSync(Buffer.from(text, 'utf8'), { level: 9 });
const readFile = () => zlib.gunzipSync(fs.readFileSync(FILE)).toString('utf8');
const definitions = () => defineCases(engineVm.load(engineVm.DEMAND));

module.exports = { definitions, FAMILIES, runCases, recordInNode, recordInChromium, serialize, header, readFile, compress, FILE, TABLES };

if (require.main === module) {
  const file = require('../tests/current-page.js')(process.argv[2]), cases = definitions();
  recordInChromium(file, cases).then(({ cases: recorded, tables, browser }) => {
    const node = recordInNode(cases), differ = recorded.filter((c, i) => JSON.stringify(c) !== JSON.stringify(node.cases[i])).map(c => c.id);
    if (differ.length) console.log('WARNING: node disagrees with Chromium on ' + differ.length + ' cases, e.g. ' + differ.slice(0, 5).join(', '));
    if (node.tables !== tables) console.log('WARNING: node and Chromium export different tables');
    const text = serialize(header(browser), recorded);
    fs.writeFileSync(FILE, compress(text));
    fs.mkdirSync(path.dirname(TABLES), { recursive: true });
    fs.writeFileSync(TABLES, tables);
    const by = {};
    for (const c of recorded) by[c.family] = (by[c.family] || 0) + 1;
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ' (' + text.length + ' bytes unzipped): ' + recorded.length + ' cases ' + JSON.stringify(by));
  }).catch(e => { console.error(e); process.exit(1); });
}
