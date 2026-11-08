#!/usr/bin/env python3
"""The economy check's mutation sweep (docs/specs/ECONOMY_SHADOW_PLAN.md WP6f).

Native's economy, quest, Daily Hand and Health rules and the shadow comparison itself (core/economy, core/quests,
core/shadow) are mutated one small change at a time: a comparison flipped (< and <=, > and >=, == and !=), && and ||
swapped, an integer constant moved by one, + and - swapped, true and false swapped, a branch dropped (an if's condition
made false). Each mutant is compiled and the shadow's replays of the goldens and the real-page corpora are run against
it (KILL_SUITE); a mutant is caught when any of them fails (or runs past the time limit). The working tree is never
touched: every mutant is made in a copy of the sources in the scratch folder.

Mutants are drawn deterministically: every candidate of the three packages is listed in file and line order, shuffled
with a fixed seed, and the first N of each package taken. A mutant that does not compile is stillborn and not counted.
The report (stdout and --report FILE as JSON) lists every survivor with its place and change, for a verdict: equivalent
(no input can tell), or a case to add.

Needs a JDK and the Kotlin compiler, stdlib, JUnit, Hamcrest and org.json jars (the gradle cache's by default; set
KOTLIN_JARS to a colon-separated list of kotlin-compiler-embeddable, kotlin-stdlib, kotlin-script-runtime, kotlin-reflect,
kotlin-daemon-embeddable, trove4j, annotations, coroutines, and TEST_JARS to junit, hamcrest, json).

  python3 tools/shadow_mutation_sweep.py [--per-package N] [--seed S] [--only economy|quests|shadow] [--report FILE]
                                         [--work DIR] [--list] [--mutant ID]
  python3 tools/shadow_mutation_sweep.py --run CLASS[,CLASS...]   compiles without a mutation and runs those shadow test
                                                                  classes (the proof's tests where gradle does not fit)
"""
import argparse, glob, json, os, random, re, shutil, subprocess, sys, tempfile, time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'android-wrapper', 'native', 'src')
MAIN = 'main/java/com/lifemax/rpg'
TEST = 'test/java/com/lifemax/rpg'
PACKAGES = {'economy': MAIN + '/core/economy', 'quests': MAIN + '/core/quests', 'shadow': MAIN + '/core/shadow'}
COMPILED_MAIN = ['core/economy', 'core/quests', 'core/save', 'core/shadow', 'core/adapters', 'app/shadow']
COMPILED_TEST = ['core/shadow', 'core/golden', 'core/economy', 'core/adapters', 'core/quests', 'app/shadow']
# the replays of the goldens and corpora, and the tests that make native wrong on purpose and need the comparison to notice
# (a mutant of the comparison itself that hides differences is caught there)
KILL_SUITE = ['ShadowGoldenCompareTest', 'ShadowQuestsGoldenTest', 'ShadowDayGoldenTest', 'SaveComposeGoldenTest',
              'ShadowCorpusTest', 'ShadowDayCorpusTest', 'ShadowSimCorpusTest', 'ShadowDefectTest',
              'ShadowMutationTest', 'ShadowHandMutationTest', 'ShadowDayMutationTest', 'ShadowComparisonTest']
# files no shadow point runs, or that only word, store or report the outcome: not mutated here, each covered by its own tests
EXCLUDED = {
    'QuestBoard.kt': 'the quest board projection, a read no shadow point wraps (plan 1.2); BoardGoldenTest',
    'BoardView.kt': 'the quest board projection, a read no shadow point wraps (plan 1.2); BoardGoldenTest',
    'ShadowReport.kt': 'the copied report, words only; ShadowReportTest',
    'ShadowLedgerJson.kt': 'the ring file; ShadowRingTest',
    'ShadowCases.kt': 'Save case; ShadowCaseTest',
    'ShadowRedactor.kt': 'redaction of paths and names; ShadowRedactionTest',
}
TIME_LIMIT = 300

G = '/root/.gradle/caches/modules-2/files-2.1'
DEFAULT_KOTLIN = [
    G + '/org.jetbrains.kotlin/kotlin-compiler-embeddable/2.0.21/*/kotlin-compiler-embeddable-2.0.21.jar',
    G + '/org.jetbrains.kotlin/kotlin-stdlib/2.0.21/*/kotlin-stdlib-2.0.21.jar',
    G + '/org.jetbrains.kotlin/kotlin-script-runtime/2.0.21/*/kotlin-script-runtime-2.0.21.jar',
    G + '/org.jetbrains.kotlin/kotlin-reflect/1.6.10/*/kotlin-reflect-1.6.10.jar',
    G + '/org.jetbrains.kotlin/kotlin-daemon-embeddable/2.0.21/*/kotlin-daemon-embeddable-2.0.21.jar',
    G + '/org.jetbrains.intellij.deps/trove4j/1.0.20200330/*/trove4j-1.0.20200330.jar',
    G + '/org.jetbrains/annotations/13.0/*/annotations-13.0.jar',
    G + '/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.6.4/*/kotlinx-coroutines-core-jvm-1.6.4.jar',
]
DEFAULT_TEST = [
    G + '/junit/junit/4.13.2/*/junit-4.13.2.jar',
    G + '/org.hamcrest/hamcrest-core/1.3/*/hamcrest-core-1.3.jar',
    G + '/org.json/json/20240303/*/json-20240303.jar',
]

# (name, pattern, replacement); each match of a pattern on a code line is one candidate
OPERATORS = [
    ('lt-le', r'(?<=\s)<(?=\s)', '<='), ('le-lt', r'(?<=\s)<=(?=\s)', '<'),
    ('gt-ge', r'(?<=\s)>(?=\s)', '>='), ('ge-gt', r'(?<=\s)>=(?=\s)', '>'),
    ('eq-ne', r'(?<=\s)==(?=\s)', '!='), ('ne-eq', r'(?<=\s)!=(?=\s)', '=='),
    ('and-or', r'(?<=\s)&&(?=\s)', '||'), ('or-and', r'(?<=\s)\|\|(?=\s)', '&&'),
    ('plus-minus', r'(?<=\s)\+(?=\s)', '-'), ('minus-plus', r'(?<=\s)-(?=\s)', '+'),
    ('true-false', r'\btrue\b', 'false'), ('false-true', r'\bfalse\b', 'true'),
    ('int+1', r'(?<![\w.])(\d+)(?![\w.])', None),
    ('drop-if', r'\bif \(', None),
]


def jars(env, defaults):
    if os.environ.get(env):
        return os.environ[env].split(':')
    out = []
    for g in defaults:
        found = sorted(glob.glob(g))
        if not found:
            sys.exit('missing jar ' + g + ' (set ' + env + ')')
        out.append(found[0])
    return out


def code_lines(text):
    """(index, line) of the lines that are code: not comments, KDoc, imports, annotations or strings-only lines."""
    out, doc = [], False
    for i, line in enumerate(text.split('\n')):
        s = line.strip()
        if doc:
            doc = '*/' not in s
            continue
        if s.startswith('/*'):
            doc = '*/' not in s
            continue
        if not s or s.startswith(('//', '*', 'import ', 'package ', '@')):
            continue
        out.append((i, line))
    return out


def strip_strings(line):
    """The line with string literals and trailing comments blanked, so operators inside them are not mutated."""
    blank = re.sub(r'"(\\.|[^"\\])*"', lambda m: ' ' * len(m.group(0)), line)
    cut = blank.find('//')
    return blank if cut < 0 else blank[:cut] + ' ' * (len(blank) - cut)


def matching_paren(line, start):
    depth = 0
    for j in range(start, len(line)):
        if line[j] == '(':
            depth += 1
        elif line[j] == ')':
            depth -= 1
            if depth == 0:
                return j
    return -1


def candidates(package):
    out = []
    for path in sorted(glob.glob(os.path.join(SRC, PACKAGES[package], '*.kt'))):
        if os.path.basename(path) in EXCLUDED:
            continue
        rel = os.path.relpath(path, SRC)
        text = open(path, encoding='utf-8').read()
        for i, line in code_lines(text):
            plain = strip_strings(line)
            for name, pattern, _ in OPERATORS:
                for m in re.finditer(pattern, plain):
                    if name == 'drop-if' and matching_paren(plain, m.end() - 1) < 0:
                        continue
                    if name == 'int+1' and (plain[max(0, m.start() - 1)] in '"$' or int(m.group(1)) > 10 ** 9):
                        continue
                    out.append({'file': rel, 'line': i, 'col': m.start(), 'op': name, 'package': package})
    return out


def apply(text, mutant):
    lines = text.split('\n')
    line, col, op = lines[mutant['line']], mutant['col'], mutant['op']
    pattern, repl = next((p, r) for n, p, r in OPERATORS if n == op)
    m = re.compile(pattern).match(line, col)
    if op == 'int+1':
        new = line[:m.start()] + str(int(m.group(1)) + 1) + line[m.end():]
    elif op == 'drop-if':
        close = matching_paren(line, m.end() - 1)
        new = line[:m.end()] + 'false && (' + line[m.end():close] + ')' + line[close:]
    else:
        new = line[:m.start()] + repl + line[m.end():]
    mutant['before'], mutant['after'] = line.strip(), new.strip()
    lines[mutant['line']] = new
    return '\n'.join(lines)


def kotlinc(kotlin, cp, out, sources, friend=None):
    """Compiles [sources] into [out]; one module name throughout, so the tests (compiled once, with the main classes as their
    friend) reach `internal` declarations of every mutant's main classes."""
    shutil.rmtree(out, ignore_errors=True)
    os.makedirs(out)
    cmd = ['java', '-cp', ':'.join(kotlin), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
           '-jvm-target', '17', '-nowarn', '-module-name', 'shadowproof', '-cp', ':'.join(cp), '-d', out]
    cmd += (['-Xfriend-paths=' + friend] if friend else []) + sources
    r = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    return r.returncode == 0 and not re.search(r'^.*error:', r.stdout, re.M), r.stdout


def run_suite(cp, classes):
    cmd = ['java', '-cp', ':'.join(cp), 'org.junit.runner.JUnitCore'] + [c if '.' in c else 'com.lifemax.rpg.core.shadow.' + c for c in classes]
    try:
        r = subprocess.run(cmd, cwd=os.path.join(ROOT, 'android-wrapper'), stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                           text=True, timeout=TIME_LIMIT)
    except subprocess.TimeoutExpired:
        return False, 'timeout'
    failed = re.findall(r'^\d+\) (\w+)\(com\.lifemax\.rpg\.[\w.]*\.(\w+)\)', r.stdout, re.M)
    return r.returncode == 0, ' '.join(sorted(set(c for _, c in failed)))


def main():
    a = argparse.ArgumentParser()
    a.add_argument('--per-package', type=int, default=35)
    a.add_argument('--seed', type=int, default=6)
    a.add_argument('--only', choices=sorted(PACKAGES))
    a.add_argument('--report')
    a.add_argument('--work', default=None)
    a.add_argument('--list', action='store_true')
    a.add_argument('--mutant', help='run only these mutant ids (package:index, comma-separated)')
    a.add_argument('--suite', help='run these test classes (fully named, comma-separated) in place of the kill suite')
    a.add_argument('--start', type=int, default=0, help='skip the first N drawn mutants (to resume a sweep)')
    a.add_argument('--run', help='no mutation: compile and run these test classes of core/shadow, comma-separated')
    args = a.parse_args()
    kotlin, tests = jars('KOTLIN_JARS', DEFAULT_KOTLIN), jars('TEST_JARS', DEFAULT_TEST)
    std = [j for j in kotlin if 'kotlin-stdlib' in j]

    chosen = []
    for package in sorted(PACKAGES):
        if args.only and package != args.only:
            continue
        cands = candidates(package)
        random.Random(args.seed).shuffle(cands)
        for k, m in enumerate(cands[:args.per_package]):
            m['id'] = package + ':' + str(k)
            chosen.append(m)
    if args.mutant:
        chosen = [m for m in chosen if m['id'] in args.mutant.split(',')]
    chosen = chosen[args.start:]
    if args.list:
        for name, why in sorted(EXCLUDED.items()):
            print('excluded', name + ':', why)
        for m in chosen:
            print(m['id'], m['file'] + ':' + str(m['line'] + 1), m['op'])
        return

    work = args.work or tempfile.mkdtemp(prefix='shadow-mutants-')
    copy = os.path.join(work, 'src')
    shutil.rmtree(copy, ignore_errors=True)
    shutil.copytree(SRC + '/main', copy + '/main', ignore=shutil.ignore_patterns('res', 'assets'))
    main_sources = [os.path.join(copy, MAIN, p) for p in COMPILED_MAIN]
    test_sources = [os.path.join(SRC, TEST, p) for p in COMPILED_TEST]
    base_out, test_out, mutant_out = (os.path.join(work, d) for d in ('main', 'tests', 'mutant'))
    resources = os.path.join(SRC, 'main', 'resources')

    ok, log = kotlinc(kotlin, std + tests, base_out, main_sources)
    if not ok:
        sys.exit('the unmutated sources do not compile:\n' + log[-3000:])
    ok, log = kotlinc(kotlin, std + tests + [base_out], test_out, test_sources, friend=base_out)
    if not ok:
        sys.exit('the tests do not compile:\n' + log[-3000:])
    if os.path.isdir(os.path.join(resources, 'quests')):
        shutil.copytree(os.path.join(resources, 'quests'), os.path.join(base_out, 'quests'), dirs_exist_ok=True)
    if args.run:
        cmd = ['java', '-cp', ':'.join([base_out, test_out] + std + tests), 'org.junit.runner.JUnitCore']
        cmd += [c if '.' in c else 'com.lifemax.rpg.core.shadow.' + c for c in args.run.split(',')]
        code = subprocess.call(cmd, cwd=os.path.join(ROOT, 'android-wrapper'))
        if not args.work:
            shutil.rmtree(work, ignore_errors=True)
        sys.exit(code)
    suite = args.suite.split(',') if args.suite else KILL_SUITE
    green, why = run_suite([base_out, test_out] + std + tests, suite)
    if not green:
        sys.exit('the kill suite fails without a mutation: ' + why)

    results = []
    started = time.time()
    for n, m in enumerate(chosen):
        path = os.path.join(copy, m['file'])
        original = open(path, encoding='utf-8').read()
        open(path, 'w', encoding='utf-8').write(apply(original, m))
        try:
            compiled, _ = kotlinc(kotlin, std + tests, mutant_out, main_sources)
            if not compiled:
                m['result'] = 'stillborn'
            else:
                if os.path.isdir(os.path.join(resources, 'quests')):
                    shutil.copytree(os.path.join(resources, 'quests'), os.path.join(mutant_out, 'quests'), dirs_exist_ok=True)
                passed, why = run_suite([mutant_out, test_out] + std + tests, suite)
                m['result'] = 'survived' if passed else 'caught'
                m['by'] = why
        finally:
            open(path, 'w', encoding='utf-8').write(original)
        results.append(m)
        print('[%d/%d %ds] %s %s %s:%d %s | %s -> %s %s' % (n + 1, len(chosen), time.time() - started, m['result'], m['id'],
              m['file'].split('/')[-1], m['line'] + 1, m['op'], m['before'][:70], m['after'][:70], m.get('by', '')), flush=True)
    shutil.rmtree(mutant_out, ignore_errors=True)

    live = [m for m in results if m['result'] != 'stillborn']
    caught = [m for m in live if m['result'] == 'caught']
    print('\nmutants: %d drawn, %d stillborn, %d compiled; caught %d, survived %d (%.1f%%)' %
          (len(results), len(results) - len(live), len(live), len(caught), len(live) - len(caught),
           100.0 * len(caught) / max(1, len(live))))
    for package in sorted(PACKAGES):
        p = [m for m in live if m['package'] == package]
        if p:
            print('  %s: %d of %d caught' % (package, sum(m['result'] == 'caught' for m in p), len(p)))
    for m in live:
        if m['result'] == 'survived':
            print('  survivor %s %s:%d %s: %s  ->  %s' % (m['id'], m['file'], m['line'] + 1, m['op'], m['before'], m['after']))
    if args.report:
        json.dump(results, open(args.report, 'w'), indent=1)
    if not args.work:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    main()
