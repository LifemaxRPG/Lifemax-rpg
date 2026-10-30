#!/usr/bin/env bash
# Run the web app's tests the way CI does.
#   tools/run_tests.sh        every tests/*.test.js and tests/*.test.py except those in tests/SKIP_IN_CI.txt
#   tools/run_tests.sh sim    the hand-over simulation with a fixed seed base (SEEDS=n to change how many)
# Needs node and python3; most tests need Playwright with Chromium (NODE_PATH must reach it).
set -uo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
repo="$(cd "$here/.." && pwd)"
export NODE_PATH="${NODE_PATH:-$(npm root -g)}"

if [ "${1:-}" = sim ]; then
  SEED_BASE="${SEED_BASE:-777}" node "$here/tests/native-handover-sim.test.js" "${SEEDS:-40}"
  exit $?
fi

# Build the current page once; every test that loads the app uses it (tests/current-page.js)
export LMA_TEST_PAGE="$(mktemp --suffix=.html)"
trap 'rm -f "$LMA_TEST_PAGE"' EXIT
node -e "require('$here/tests/current-page.js').build(process.env.LMA_TEST_PAGE)" || { echo "FAIL could not build the page"; exit 1; }

skip=$(sed -e 's/#.*//' -e 's/[[:space:]]*$//' "$here/tests/SKIP_IN_CI.txt" | grep -v '^$' || true)
failed=()
passed=0
for t in "$here"/tests/*.test.js "$here"/tests/*.test.py; do
  name=$(basename "$t")
  if grep -qx "$name" <<<"$skip"; then echo "SKIP $name"; continue; fi
  start=$SECONDS
  if [[ $name == *.py ]]; then (cd "$repo" && python3 "$t") >/tmp/lm_test.out 2>&1; else node "$t" >/tmp/lm_test.out 2>&1; fi
  rc=$?
  if [ $rc -eq 0 ]; then
    passed=$((passed + 1)); echo "ok   $name ($((SECONDS - start)) s)"
  else
    failed+=("$name"); echo "FAIL $name (exit $rc)"; tail -20 /tmp/lm_test.out | sed 's/^/     /'
  fi
done
if [ ${#failed[@]} -eq 0 ]; then echo "$passed passed, 0 failed"; else echo "$passed passed, ${#failed[@]} failed: ${failed[*]}"; fi
[ ${#failed[@]} -eq 0 ]
