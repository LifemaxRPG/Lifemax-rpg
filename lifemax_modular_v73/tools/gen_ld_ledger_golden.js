/* Golden cases for the Life Director ledger (WP-LD6: emptyLedger, completionEventId, recordCompletion with its ratio, partial state, rest
   tokens, streaks and residual task, revokeEvent, compactLedger, ledgerHas, reconcileQuestProgress, detectMisses, applyMissConsequence,
   canTransition; engine.js:1904-2085), recorded from the real page in Chromium, so that the native code
   (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector, Ledger*.kt) is accepted only when it gives the same answer for
   every case. Regenerate when the engine changes on purpose:
     NODE_PATH=$(npm root -g) node tools/gen_ld_ledger_golden.js [built-page.html]
   Output: tests/fixtures/ld-ledger-golden.json.gz (gzip of one case per line; the text is what serialize() writes).
   tests/ld-ledger-golden.test.js records every case again, in Chromium and in node, and fails when either disagrees with the file.
   The cases are defined in tools/ld_ledger_cases.js; the method is WP-LD1's: the engine text in a vm with its private helpers hooked
   (tools/ld_engine_vm.js kernelSource with LEDGER below: compactLedger is private), run inside the built page in Chromium and in node.

   File: {header, cases}. A case is {id, family, rule, fn, args, out}:
     family  empty | event | transition | record | revoke | has | compact | reconcile | misses | miss | stream | dst | fuzz | malformed.
     rule    the LIFE_DIRECTOR_LEDGER_SPEC.md rule IDs it exercises, separated by spaces.
     fn      the engine function called with args (compactLedger: out is the ledger after it), or 'stream' (runStream below).
     args    the arguments, as JSON (a shorter list stands for trailing undefined arguments).
     out     the result as JSON (canon below: members holding undefined left out, NaN, -0 and the infinities as {$num}), or
             {threw: <error name>}.
   Every case is run twice on fresh state and must give the same answer both times (the recorder throws otherwise).

   What is held still: nothing here reads a clock or a device zone (a stream's day key reads the stream's own zone); the page boots with the
   usual test save and is not touched. */
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const { defineCases, FAMILIES } = require('./ld_ledger_cases.js');
const engineVm = require('./ld_engine_vm.js');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'ld-ledger-golden.json.gz');
/* The private helpers the ledger cases call (engine.js:1979). */
const LEDGER = ['compactLedger'];

/* Runs every case; E is the hooked engine, K its __kernel. Self-contained: it is also sent into the page. */
function runCases(E, K, cases) {
  const canon = v => {
    if (typeof v === 'number') return Number.isNaN(v) ? { $num: 'NaN' } : v === Infinity ? { $num: 'Infinity' } : v === -Infinity ? { $num: '-Infinity' }
      : Object.is(v, -0) ? { $num: '-0' } : v;
    if (Array.isArray(v)) return v.map(canon);
    if (v && typeof v === 'object') { const o = {}; for (const k of Object.keys(v)) if (v[k] !== undefined) o[k] = canon(v[k]); return o; }
    return v === undefined ? null : v;
  };
  /* A stream: the host's flows (logic.js:184-199, 245-262, 284-325, 2590) reduced to the engine calls, on one state. Steps:
       complete {id, start, minutes, now}: the planned session of that id (else the last kept copy of it, as a repeated request holds it) is
         completed (recordCompletion with the preferences' partialSatisfiedRatio); when applied it leaves the plan for the history and the
         event goes to quests and goals (reconcileQuestProgress with the day key of the event's start in the stream's zone, logic.js:287).
       adhoc {client, tag, title, minutes, start, now}: an ad-hoc completion with that client id (logic.js:184), then the same.
       revoke {ref, reason, now}: revokeEvent of the event id the ref-th complete or adhoc step gave.
       reopen {id}: a session whose completion was revoked goes back to the plan with the next revision (logic.js:191-199).
       skip {id, now}: applyMissConsequence 'SKIP' for the planned session; it goes to the history in the state given.
       tick {now}: detectMisses with the preferences' grace, then applyMissConsequence 'MISS' for the first 12 (logic.js:245-262).
       has {ref}: ledgerHas of the ref-th event id (an unknown one past the end).
     Each step's answer is what the calls gave (the new event in full) with the ledger after it but its event bodies (written once, never
     changed), the number of them, and the plan and history as id:state:revision; the stream's answer ends with everything in full. */
  const runStream = s => {
    const prefs = E.normalizePreferences(s.prefs);
    let ledger = s.ledger || E.emptyLedger(s.playerId), planned = s.planned.slice(), history = [], quests = s.quests, goals = s.goals;
    const refs = [], steps = [];
    const brief = i => i.instanceId + ':' + i.lifecycleState + ':' + (i.revision || 0);
    const view = () => {
      const L = Object.assign({}, ledger); delete L.events;
      return { ledger: L, events: Object.keys(ledger.events).length, planned: planned.map(brief), history: history.map(brief) };
    };
    const after = event => {
      const dayKey = E.util.ymdKey(E.util.localParts(event.actualStartEpoch, s.tz));
      const rq = E.reconcileQuestProgress(event, quests, goals, planned, { dayKey });
      const questsChanged = rq.updatedQuests.map((q, i) => (JSON.stringify(q) === JSON.stringify(quests[i]) ? null : q));
      const goalsChanged = rq.updatedGoals.map((g, i) => (JSON.stringify(g) === JSON.stringify(goals[i]) ? null : g));
      const sessionsChanged = rq.prunedScheduleInstances.filter(i => !planned.includes(i));
      quests = rq.updatedQuests; goals = rq.updatedGoals;
      planned = rq.prunedScheduleInstances.filter(i => !rq.removedInstanceIds.includes(i.instanceId));
      return { dayKey, questsChanged, goalsChanged, sessionsChanged, questBountyRewards: rq.questBountyRewards, removedInstanceIds: rq.removedInstanceIds, questDeltas: rq.questDeltas };
    };
    const lastKept = id => { for (let k = history.length - 1; k >= 0; k--) if (history[k].instanceId === id) return history[k]; return null; };
    const settle = (inst, state, more) => {
      planned = planned.filter(i => i.instanceId !== inst.instanceId);
      history.push(Object.assign({}, inst, { lifecycleState: state }, more));
    };
    for (const st of s.steps) {
      let out;
      if (st.op === 'complete' || st.op === 'adhoc') {
        const inst = st.op === 'adhoc' ? null : planned.find(i => i.instanceId === st.id) || lastKept(st.id);
        if (st.op === 'complete' && !inst) out = { found: false };
        else {
          const r = E.recordCompletion(inst ? { ledger, playerId: s.playerId, instance: inst, actualStartEpoch: st.start, actualDurationMinutes: st.minutes, nowEpoch: st.now,
            satisfiedRatio: prefs.partialSatisfiedRatio }
            : { ledger, playerId: s.playerId, adhoc: { tag: st.tag, title: st.title }, clientEventId: st.client, actualStartEpoch: st.start, actualDurationMinutes: st.minutes, nowEpoch: st.now });
          refs.push(r.eventId);
          out = { applied: r.applied, reason: r.reason, eventId: r.eventId };
          if (inst) out.transition = E.canTransition(inst.lifecycleState, r.lifecycleState || 'COMPLETED_ON_TIME');
          if (r.applied) {
            ledger = r.ledger;
            Object.assign(out, { event: r.event, lifecycleState: r.lifecycleState, tokenEarned: r.tokenEarned, residualTask: r.residualTask });
            if (inst) settle(inst, r.lifecycleState, { completionEventId: r.eventId });
            out.progress = after(r.event);
          }
        }
      } else if (st.op === 'revoke') {
        const r = E.revokeEvent(ledger, refs[st.ref], st.reason, st.now);
        ledger = r.ledger; out = { revoked: r.revoked, eventId: refs[st.ref] };
      } else if (st.op === 'reopen') {
        const h = lastKept(st.id);
        const ok = !!(h && h.completionEventId && (ledger.revoked || {})[h.completionEventId]) && !planned.some(i => i.instanceId === st.id);
        if (ok) planned.push(Object.assign({}, h, { lifecycleState: 'PLANNED', revision: (h.revision || 0) + 1, completionEventId: null }));
        out = { reopened: ok };
      } else if (st.op === 'skip') {
        const inst = planned.find(i => i.instanceId === st.id);
        if (!inst) out = { found: false };
        else {
          const c = E.applyMissConsequence(ledger, inst, s.prefs, st.now, 'SKIP');
          ledger = c.ledger; settle(inst, c.state, { consequence: c.consequence });
          out = { penaltyId: c.penaltyId, state: c.state, consequence: c.consequence, regroupPrompt: c.regroupPrompt, note: c.note };
        }
      } else if (st.op === 'tick') {
        const misses = E.detectMisses(planned, st.now, prefs.missGraceMinutes);
        out = { misses: misses.map(i => i.instanceId), consequences: [] };
        for (const inst of misses.slice(0, 12)) {
          const c = E.applyMissConsequence(ledger, inst, s.prefs, st.now, 'MISS');
          ledger = c.ledger; settle(inst, c.state, { consequence: c.consequence });
          out.consequences.push({ penaltyId: c.penaltyId, state: c.state, consequence: c.consequence, regroupPrompt: c.regroupPrompt, note: c.note });
        }
      } else if (st.op === 'has') {
        out = { has: E.ledgerHas(ledger, st.ref < refs.length ? refs[st.ref] : 'ev_unknown') };
      } else throw new Error('unknown step ' + st.op);
      steps.push(Object.assign({ op: st.op }, out, view()));
    }
    return { steps, ledger, quests, goals, planned, history };
  };
  const FN = {
    emptyLedger: (...a) => E.emptyLedger(...a),
    completionEventId: (...a) => E.completionEventId(...a),
    canTransition: (...a) => E.canTransition(...a),
    recordCompletion: (...a) => E.recordCompletion(...a),
    revokeEvent: (...a) => E.revokeEvent(...a),
    ledgerHas: (...a) => E.ledgerHas(...a),
    compactLedger: (ledger, now) => { K.compactLedger(ledger, now); return ledger; },
    reconcileQuestProgress: (...a) => E.reconcileQuestProgress(...a),
    detectMisses: (...a) => E.detectMisses(...a),
    applyMissConsequence: (...a) => E.applyMissConsequence(...a),
    stream: runStream,
  };
  const once = c => { try { return JSON.stringify(canon(FN[c.fn](...JSON.parse(JSON.stringify(c.args))))); } catch (e) { return JSON.stringify({ threw: (e && e.name) || 'Error' }); } };
  return cases.map(c => {
    if (!(c.fn in FN)) throw new Error('no adaptor for ' + c.fn);
    const out = once(c);
    if (once(c) !== out) throw new Error('not deterministic: ' + c.id);
    return Object.assign({}, c, { out: JSON.parse(out) });
  });
}

/* In node: the engine text in a vm. */
function recordInNode(cases) {
  const E = engineVm.load(LEDGER);
  return { cases: runCases(E, E.__kernel, cases) };
}

/* In Chromium: the same engine text with the hook, evaluated in the built page (which must contain the engine text unchanged; the web test
   checks it), so the page's own JavaScript engine and Intl compute every value. Cases go in parts, and each part's answer comes back as text
   and is kept as text, so memory stays low. */
async function recordInChromium(file, cases, onPart) {
  const { chromium } = require('playwright');
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, timezoneId: 'UTC', locale: 'en-US', bypassCSP: true });
    const p = await ctx.newPage(), errors = [];
    p.on('pageerror', e => errors.push(String(e)));
    await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));");
    await p.goto('file://' + file);
    await p.waitForFunction(() => !!(window.LifeDirectorEngine && window.LifeDirectorEngine.util), null, { timeout: 120000 });
    await p.evaluate(({ src, runner }) => {
      const holder = {};
      new Function('self', src)(holder);
      window.__ldLedger = { E: holder.LifeDirectorEngine, run: new Function('return ' + runner)() };
    }, { src: engineVm.kernelSource(LEDGER), runner: runCases.toString() });
    const out = [];
    for (let i = 0; i < cases.length; i += 50) {
      const part = await p.evaluate(cs => { const L = window.__ldLedger; return L.run(L.E, L.E.__kernel, cs).map(c => JSON.stringify(c)); }, cases.slice(i, i + 50));
      if (onPart) onPart(i + part.length);
      out.push(...part);
    }
    if (errors.length) throw new Error('the page threw: ' + errors.slice(0, 3).join(' | '));
    return { lines: out, browser: browser.version() };
  } finally { await browser.close(); }
}

const header = browser => ({
  about: 'Life Director ledger golden cases; see tools/gen_ld_ledger_golden.js',
  recordedIn: 'Chromium ' + browser,
  nodeCheck: 'node ' + process.versions.node + ' ICU ' + process.versions.icu + ' tz ' + process.versions.tz,
});
/* one case per line, so a change shows up in a diff of the unzipped text as the cases it changed; lines are the cases' JSON texts */
const serialize = (head, lines) => '{"header":' + JSON.stringify(head) + ',"cases":[\n' + lines.join(',\n') + '\n]}\n';
const compress = text => zlib.gzipSync(Buffer.from(text, 'utf8'), { level: 9 });
const readFile = () => zlib.gunzipSync(fs.readFileSync(FILE)).toString('utf8');
/* The file's case lines (each a case's JSON text). */
const readLines = () => readFile().split('\n').filter(l => l.startsWith('{"id"')).map(l => (l.endsWith(',') ? l.slice(0, -1) : l));
const definitions = () => defineCases(engineVm.load(engineVm.DEMAND));

module.exports = { definitions, FAMILIES, LEDGER, runCases, recordInNode, recordInChromium, serialize, header, readFile, readLines, compress, FILE };

if (require.main === module) {
  const file = require('../tests/current-page.js')(process.argv[2]), cases = definitions();
  console.log('defined ' + cases.length + ' cases');
  recordInChromium(file, cases, n => { if (n % 1000 === 0) console.log('recorded ' + n); }).then(({ lines, browser }) => {
    const text = serialize(header(browser), lines);
    fs.writeFileSync(FILE, compress(text));
    const by = {};
    for (const c of cases) by[c.family] = (by[c.family] || 0) + 1;
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ' (' + text.length + ' bytes unzipped): ' + lines.length + ' cases ' + JSON.stringify(by));
    const node = recordInNode(cases).cases.map(c => JSON.stringify(c)), differ = node.map((l, i) => (l === lines[i] ? null : cases[i].id)).filter(Boolean);
    console.log('node disagrees with Chromium on ' + differ.length + ' cases' + (differ.length ? ', e.g. ' + differ.slice(0, 5).join(', ') : ''));
  }).catch(e => { console.error(e); process.exit(1); });
}
