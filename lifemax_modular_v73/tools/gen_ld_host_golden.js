/* Host-level golden cases for Life Director (WP-LD9: Today's and Do's commands, finishInstance, logToday, the Skip's take-back, afterEvent,
   syncHost, processMisses, maybeRollover and the tick, applyOutput with its refusal, repairEarlyDone, the dial's applyDial, regroup),
   recorded from logic.js run inside the built page in Chromium, so that the native host commands
   (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector/host) are accepted only when they give the same answer, the
   same host calls and the same state for every step. Regenerate when logic.js or the engine changes on purpose:
     NODE_PATH=$(npm root -g) node tools/gen_ld_host_golden.js [built-page.html]
   Output: tests/fixtures/ld-host-golden.json.gz (gzip of one case per line). tests/ld-host-golden.test.js records every case again, in
   Chromium and in node, and fails when either disagrees with the file.

   Method (tools/ld_host_vm.js): logic.js with three behaviour-neutral hooks, over the page's engine and store texts and a recording host,
   evaluated with `new Function` per case, in the built page (whose own JavaScript engine and Intl compute every value; the page must carry
   logic.js and the engine exactly as the files have them, which the web test checks) and in node. Every case runs twice on fresh modules
   and must give the same answer both times (the recorder throws otherwise).

   File: {header, cases}. A case is {id, family, rule, start, steps, out}; out holds one entry per step (the boot first): {at, reply,
   calls, session, state}, state being what changed in ST since the step before ({set, removed}). Cases are defined in
   tools/ld_host_cases.js. */
'use strict';
process.env.TZ = 'UTC'; // the device zone the page and node read (deviceTz), as the Chromium context is set
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const { defineCases, FAMILIES } = require('./ld_host_cases.js');
const hostVm = require('./ld_host_vm.js');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'ld-host-golden.json.gz');

function recordInNode(cases) { return hostVm.runHostCases(hostVm.sources(), cases); }

/* In Chromium: the same runner and texts, evaluated in the built page; parts of 20 cases, each answer kept as text. */
async function recordInChromium(file, cases, onPart) {
  const { chromium } = require('playwright');
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, timezoneId: 'UTC', locale: 'en-US', bypassCSP: true });
    const p = await ctx.newPage(), errors = [];
    p.on('pageerror', e => errors.push(String(e)));
    await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));");
    await p.goto('file://' + file);
    await p.waitForFunction(() => !!(window.LifeDirectorEngine && window.LifeDirector), null, { timeout: 120000 });
    await p.evaluate(({ src, runner }) => { window.__ldHost = { src, run: new Function('return ' + runner)() }; }, { src: hostVm.sources(), runner: hostVm.runHostCases.toString() });
    const out = [];
    for (let i = 0; i < cases.length; i += 20) {
      const part = await p.evaluate(cs => window.__ldHost.run(window.__ldHost.src, cs), cases.slice(i, i + 20));
      if (onPart) onPart(i + part.length);
      out.push(...part);
    }
    if (errors.length) throw new Error('the page threw: ' + errors.slice(0, 3).join(' | '));
    return { lines: out, browser: browser.version() };
  } finally { await browser.close(); }
}

const header = browser => ({
  about: 'Life Director host-level golden cases; see tools/gen_ld_host_golden.js',
  recordedIn: 'Chromium ' + browser,
  nodeCheck: 'node ' + process.versions.node + ' ICU ' + process.versions.icu + ' tz ' + process.versions.tz,
});
const serialize = (head, lines) => '{"header":' + JSON.stringify(head) + ',"cases":[\n' + lines.join(',\n') + '\n]}\n';
const compress = text => zlib.gzipSync(Buffer.from(text, 'utf8'), { level: 9 });
const readFile = () => zlib.gunzipSync(fs.readFileSync(FILE)).toString('utf8');
const readLines = () => readFile().split('\n').filter(l => l.startsWith('{"id"')).map(l => (l.endsWith(',') ? l.slice(0, -1) : l));
const definitions = () => defineCases();

module.exports = { definitions, FAMILIES, recordInNode, recordInChromium, serialize, header, readFile, readLines, compress, FILE };

if (require.main === module) {
  const file = require('../tests/current-page.js')(process.argv[2]), cases = definitions();
  console.log('defined ' + cases.length + ' cases');
  recordInChromium(file, cases, n => { if (n % 200 === 0) console.log('recorded ' + n); }).then(({ lines, browser }) => {
    const text = serialize(header(browser), lines);
    fs.writeFileSync(FILE, compress(text));
    const by = {};
    for (const c of cases) by[c.family] = (by[c.family] || 0) + 1;
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ' (' + text.length + ' bytes unzipped): ' + lines.length + ' cases ' + JSON.stringify(by));
    const node = recordInNode(cases), differ = node.map((l, i) => (l === lines[i] ? null : cases[i].id)).filter(Boolean);
    console.log('node disagrees with Chromium on ' + differ.length + ' cases' + (differ.length ? ', e.g. ' + differ.slice(0, 5).join(', ') : ''));
  }).catch(e => { console.error(e); process.exit(1); });
}
