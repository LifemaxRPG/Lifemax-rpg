"""Run with Python + Java 21. What MainActivity takes from a launch intent (backlog SEC-3 and NAV-4):
the lma_open extra names only a known screen, every sender's screen is known, and an intent Android hands back after
process death, a re-creation or a reopen from Recents is stripped before the bridge reads it."""
from pathlib import Path
import re, subprocess, tempfile
root = Path(__file__).resolve().parents[2]
native = root / 'android-wrapper/native'
web = root / 'lifemax_modular_v73/src'

# every place that puts the lma_open extra on an intent, and the screen it names
producers = []
for f in list(native.glob('*.java')) + list((native / 'src/main').rglob('*.kt')):
    for m in re.finditer(r'putExtra\(\s*(?:MainActivity\.)?EXTRA_OPEN\s*,\s*"([^"]*)"', f.read_text()):
        producers.append((f.name, m.group(1)))
assert producers, 'no sender of the lma_open extra was found (LifeDialWidget sends one)'

harness = r'''
package com.lifemax.rpg;
public class LaunchRulesCheck {
    static int failures = 0;
    static void require(boolean value, String message) {
        if (value) System.out.println("PASS " + message); else { failures++; System.out.println("FAIL " + message); }
    }
    public static void main(String[] args) {
        require("life-director".equals(LaunchRules.openTarget("life-director")), "the widget's screen is accepted");
        String[] hostile = {null, "", "Life-Director", "life-director ", " life-director", "life-director\u0000", "settings",
                "javascript:alert(1)", "../../files", "life-director;dashboard", "x".repeat(10000)};
        for (String h : hostile)
            require(LaunchRules.openTarget(h) == null, "an unknown screen is refused: " + (h == null ? "null" : h.length() > 40 ? h.substring(0, 40) + "..." : h.replace("\u0000", "\\0")));
        for (String a : args) require(a.equals(LaunchRules.openTarget(a)), "a screen a sender in the repo uses is accepted: " + a);
        require(!LaunchRules.isReplay(false, false), "a fresh launch is delivered");
        require(LaunchRules.isReplay(true, false), "a re-created activity (saved state) does not replay its intent");
        require(LaunchRules.isReplay(false, true), "a reopen from Recents does not replay its intent");
        require(LaunchRules.isReplay(true, true), "both at once do not replay either");
        if (failures > 0) { System.out.println(failures + " failed"); System.exit(1); }
    }
}
'''
with tempfile.TemporaryDirectory() as d:
    pkg = Path(d) / 'src/com/lifemax/rpg'; pkg.mkdir(parents=True)
    (pkg / 'LaunchRules.java').write_text((native / 'LaunchRules.java').read_text())
    (pkg / 'LaunchRulesCheck.java').write_text(harness)
    out = Path(d) / 'out'
    subprocess.run(['javac', '-d', str(out), str(pkg / 'LaunchRules.java'), str(pkg / 'LaunchRulesCheck.java')], check=True, timeout=120)
    subprocess.run(['java', '-cp', str(out), 'com.lifemax.rpg.LaunchRulesCheck'] + [v for _, v in producers], check=True, timeout=60)

fails = []
def check(cond, msg):
    print(('PASS ' if cond else 'FAIL ') + msg)
    if not cond: fails.append(msg)
main = (native / 'MainActivity.java').read_text()
on_create = main[main.index('public void onCreate('):]
before_super = on_create[:on_create.index('super.onCreate(savedInstanceState)')]
check('LaunchRules.isReplay(savedInstanceState != null, launchedFromHistory(launch))' in before_super and 'setIntent(consumed(launch))' in before_super,
      'onCreate strips a replayed intent before super.onCreate, where the bridge reads the launch URL')
check('FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY' in main, 'a reopen from Recents is recognised')
on_new = main[main.index('protected void onNewIntent('):main.index('private void take(')]
check(on_new.index('consumed(intent)') < on_new.index('super.onNewIntent(intent)') and 'take(intent)' in on_new,
      'onNewIntent strips a replayed intent before Capacitor sees it, then takes and consumes the new one')
take = main[main.index('private void take('):main.index('static Intent consumed(')]
check('LaunchRules.openTarget(' in take and 'setIntent(consumed(intent))' in take, 'a delivered intent is marked consumed and only a known screen is kept')
check(len(re.findall(r'launchTarget\s*=(?!\s*null)', main)) == 1 and 'launchTarget = target' in take, 'launchTarget is set in one place, from LaunchRules only')
consumed = main[main.index('static Intent consumed('):main.index('private static boolean launchedFromHistory(')]
check('setData(null)' in consumed and 'removeExtra(EXTRA_OPEN)' in consumed, 'a consumed intent has no link and no screen')
widget = (web / 'runtime/segments/40-widget.js').read_text()
for name, value in producers:
    check(f"r.open==='{value}'" in widget, f'the web side opens the screen {name} asks for ({value})')
if fails:
    raise SystemExit(f'{len(fails)} failed')
