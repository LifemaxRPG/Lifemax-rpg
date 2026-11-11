/* Timer golden cases for Life Director (WP-LD10: Today.timer, the timer's once-a-second check focusTick that finishes it on its own, the
   clock as the page reads it, and the instants at which the page's tick and timer change something), recorded from logic.js run inside
   the built page in Chromium, so that the native timer (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector/timer) is
   accepted only when it gives the same answer, the same host calls and the same state for every step. Regenerate when logic.js or the
   engine changes on purpose:
     NODE_PATH=$(npm root -g) node tools/gen_ld_timer_golden.js [built-page.html]
   Output: tests/fixtures/ld-timer-golden.json.gz (gzip of one case per line, the host golden's format). tests/ld-timer-golden.test.js
   records every case again, in Chromium and in node, and fails when either disagrees with the file.

   Method: WP-LD9's runner (tools/ld_host_vm.js, logic.js with its three behaviour-neutral hooks, the page's engine and store, a recording
   host, a memory storage that can fail, a clock that stands still between steps and goes where a step says, backwards too), in the built
   page and in node; every case runs twice on fresh modules and must give the same answer both times. Cases: tools/ld_timer_cases.js. */
'use strict';
process.env.TZ = 'UTC'; // the device zone the page and node read (deviceTz), as the Chromium context is set
const fs = require('fs'), path = require('path');
const { defineCases, FAMILIES } = require('./ld_timer_cases.js');
const host = require('./gen_ld_host_golden.js');
const hostVm = require('./ld_host_vm.js');
const zlib = require('zlib');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'ld-timer-golden.json.gz');

function recordInNode(cases) { return hostVm.runHostCases(hostVm.sources(), cases); }
const recordInChromium = (file, cases, onPart) => host.recordInChromium(file, cases, onPart);
const header = browser => Object.assign(host.header(browser), { about: 'Life Director timer golden cases; see tools/gen_ld_timer_golden.js' });
const readFile = () => zlib.gunzipSync(fs.readFileSync(FILE)).toString('utf8');
const readLines = () => readFile().split('\n').filter(l => l.startsWith('{"id"')).map(l => (l.endsWith(',') ? l.slice(0, -1) : l));
const definitions = () => defineCases();

module.exports = { definitions, FAMILIES, recordInNode, recordInChromium, serialize: host.serialize, header, readFile, readLines, compress: host.compress, FILE };

if (require.main === module) {
  const file = require('../tests/current-page.js')(process.argv[2]), cases = definitions();
  console.log('defined ' + cases.length + ' cases');
  recordInChromium(file, cases, n => { if (n % 100 === 0) console.log('recorded ' + n); }).then(({ lines, browser }) => {
    const text = host.serialize(header(browser), lines);
    fs.writeFileSync(FILE, host.compress(text));
    const by = {};
    for (const c of cases) by[c.family] = (by[c.family] || 0) + 1;
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ' (' + text.length + ' bytes unzipped): ' + lines.length + ' cases ' + JSON.stringify(by));
    const node = recordInNode(cases), differ = node.map((l, i) => (l === lines[i] ? null : cases[i].id)).filter(Boolean);
    console.log('node disagrees with Chromium on ' + differ.length + ' cases' + (differ.length ? ', e.g. ' + differ.slice(0, 5).join(', ') : ''));
  }).catch(e => { console.error(e); process.exit(1); });
}
