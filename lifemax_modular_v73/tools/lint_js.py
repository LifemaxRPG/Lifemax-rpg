#!/usr/bin/env python3
"""Lint the built page's classic <script> blocks with ESLint and ratchet the findings.

A rule's count may go down (then run with --update to lock it in) but never up, so the existing
wrapper pattern (functions re-assigned by later segments) stays as is while new mistakes fail.
    python3 tools/lint_js.py            # check against tools/lint-baseline.json
    python3 tools/lint_js.py --update   # write the current counts as the new baseline
Needs node and network access to fetch the pinned ESLint once (npx).
"""
import collections, json, re, subprocess, sys, tempfile
from pathlib import Path

ESLINT = 'eslint@9.17.0'
root = Path(__file__).resolve().parents[1]
baseline_path = root / 'tools/lint-baseline.json'

subprocess.run([sys.executable, str(root / 'build.py')], check=True, stdout=subprocess.DEVNULL)
html = (root / 'dist/Life_Max_RPG_v73_modular_build.html').read_text(encoding='utf-8')
blocks = [
    body for attrs, body in re.findall(r'<script([^>]*)>(.*?)</script>', html, flags=re.S)
    if 'src=' not in attrs and 'application/json' not in attrs
]
with tempfile.TemporaryDirectory(dir=root / 'tools', prefix='.lint-') as tmp:
    for i, body in enumerate(blocks):
        (Path(tmp) / f'script{i}.js').write_text(body, encoding='utf-8')
    run = subprocess.run(
        ['npx', '--yes', ESLINT, '--no-config-lookup', '-c', str(root / 'tools/eslint.config.mjs'), '-f', 'json', tmp],
        capture_output=True, text=True,
    )
    try:
        report = json.loads(run.stdout)
    except json.JSONDecodeError:
        sys.exit('ESLint did not run:\n' + run.stderr[-2000:])

counts = collections.Counter()
for file in report:
    for message in file['messages']:
        counts[message['ruleId'] or 'syntax-error'] += 1
counts = dict(sorted(counts.items()))

if '--update' in sys.argv:
    baseline_path.write_text(json.dumps(counts, indent=1) + '\n')
    print('baseline written:', counts)
    sys.exit(0)

baseline = json.loads(baseline_path.read_text())
worse = {r: (baseline.get(r, 0), n) for r, n in counts.items() if n > baseline.get(r, 0)}
better = {r: (b, counts.get(r, 0)) for r, b in baseline.items() if counts.get(r, 0) < b}
for rule, (was, now) in worse.items():
    print(f'FAIL {rule}: {now} findings, baseline allows {was}')
if better:
    print('improved (run with --update to lock in):', better)
if worse:
    sys.exit(1)
print('PASS JavaScript lint ratchet:', counts)
