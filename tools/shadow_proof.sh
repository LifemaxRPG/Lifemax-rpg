#!/bin/bash
# The economy check's proof harness (docs/specs/ECONOMY_SHADOW_PLAN.md WP6f), one command from the repository root:
#
#   tools/shadow_proof.sh [--record] [--mutants] [--gradle]
#
# 1. builds the page once (tests/current-page.js);
# 2. with --record, records the real-page corpora again (tools/gen_shadow_sim_corpus.js: the 40 seeds, every fixture save's
#    scripted day, the defects of 5.1 point 3), about half an hour; without it the committed corpora are replayed;
# 3. the page's own proof: tests/native-shadow-perf.test.js (the cost under 1x, 4x and 6x CPU throttling on 1 MB and 3 MB saves
#    and the self-disable rules) and tests/native-shadow-transparent.test.js;
# 4. native's proof: ShadowSimCorpusTest, ShadowDefectTest, ShadowFuzzTest, ShadowRandomSaveTest and the earlier replays
#    (goldens, corpora), with gradle (--gradle) or straight through kotlinc and JUnit (tools/shadow_mutation_sweep.py --run);
# 5. with --mutants, the mutation sweep (tools/shadow_mutation_sweep.py, 35 mutants a package, about 95 minutes).
#
# One seed again: node lifemax_modular_v73/tools/gen_shadow_sim_corpus.js --seed N --out /tmp/seed.json.gz, then the Kotlin
# test with -Dshadow.sim.file=/tmp/seed.json.gz (gradle: add it to the test JVM's arguments; kotlinc: java -D... as below).
set -u
cd "$(dirname "$0")/.."
RECORD=0 MUTANTS=0 GRADLE=0
for a in "$@"; do case $a in --record) RECORD=1;; --mutants) MUTANTS=1;; --gradle) GRADLE=1;; *) echo "unknown $a"; exit 2;; esac; done
export NODE_PATH=${NODE_PATH:-$(npm root -g)}
PAGE=$(mktemp --suffix=.html)
trap 'rm -f "$PAGE"' EXIT
PAGE="$PAGE" node -e "require('./lifemax_modular_v73/tests/current-page.js').build(process.env.PAGE)" || exit 1
git checkout -q lifemax_modular_v73/dist 2>/dev/null
status=0
if [ $RECORD = 1 ]; then
  for mode in "" --fixtures --defects; do
    (cd lifemax_modular_v73 && node tools/gen_shadow_sim_corpus.js "$PAGE" $mode) || status=1
  done
fi
for t in native-shadow-perf native-shadow-transparent; do
  (cd lifemax_modular_v73 && node tests/$t.test.js "$PAGE") | grep -E '^(PERF|FAIL)|passed, ' || status=1
done
CLASSES=ShadowSimCorpusTest,ShadowDefectTest,ShadowFuzzTest,ShadowRandomSaveTest,ShadowGoldenCompareTest,ShadowQuestsGoldenTest,ShadowDayGoldenTest,SaveComposeGoldenTest,ShadowCorpusTest,ShadowDayCorpusTest,ShadowMutationTest,ShadowHandMutationTest,ShadowDayMutationTest
if [ $GRADLE = 1 ]; then
  (cd android-wrapper/android && ./gradlew :app:testDebugUnitTest $(echo $CLASSES | tr ',' '\n' | sed 's/^/--tests *./') --console=plain) || status=1
else
  python3 tools/shadow_mutation_sweep.py --run $CLASSES || status=1
fi
if [ $MUTANTS = 1 ]; then python3 tools/shadow_mutation_sweep.py --report "${TMPDIR:-/tmp}/shadow-mutants.json" || status=1; fi
exit $status
