"""Run with Python + Java 21. The lma_open extra on MainActivity's intent (backlog SEC-3): LaunchRules accepts only a known
screen, every sender's screen is known to it, and the web side opens each of them. That a launch intent is not replayed
after process death or a reopen from Recents (NAV-4) is checked on the phone."""
from pathlib import Path
import re, subprocess, tempfile
root = Path(__file__).resolve().parents[2]
native = root / 'android-wrapper/native'
rules = native / 'src/main/java/com/lifemax/rpg/app/launch/LaunchRules.java'
web = root / 'lifemax_modular_v73/src'

# every place that puts the lma_open extra on an intent, and the screen it names
producers = []
for f in list(native.glob('*.java')) + list((native / 'src/main').rglob('*.kt')):
    for m in re.finditer(r'putExtra\(\s*(?:MainActivity\.)?EXTRA_OPEN\s*,\s*"([^"]*)"', f.read_text()):
        producers.append((f.name, m.group(1)))
assert producers, 'no sender of the lma_open extra was found (LifeDialWidget sends one)'

harness = r'''
package com.lifemax.rpg.app.launch;
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
        if (failures > 0) { System.out.println(failures + " failed"); System.exit(1); }
    }
}
'''
with tempfile.TemporaryDirectory() as d:
    pkg = Path(d) / 'src/com/lifemax/rpg/app/launch'; pkg.mkdir(parents=True)
    (pkg / 'LaunchRules.java').write_text(rules.read_text())
    (pkg / 'LaunchRulesCheck.java').write_text(harness)
    out = Path(d) / 'out'
    subprocess.run(['javac', '-d', str(out), str(pkg / 'LaunchRules.java'), str(pkg / 'LaunchRulesCheck.java')], check=True, timeout=120)
    subprocess.run(['java', '-cp', str(out), 'com.lifemax.rpg.app.launch.LaunchRulesCheck'] + [v for _, v in producers], check=True, timeout=60)

fails = []
def check(cond, msg):
    print(('PASS ' if cond else 'FAIL ') + msg)
    if not cond: fails.append(msg)
sources = ''.join(f.read_text() for f in native.glob('*.java'))
check(len(re.findall(r'launchTarget\s*=(?!\s*null)', sources)) == 1 and 'LaunchRules.openTarget(' in (native / 'MainActivity.java').read_text(),
      'launchTarget is set in one place, from LaunchRules')
widget = (web / 'runtime/segments/40-widget.js').read_text()
for name, value in producers:
    check(f"r.open==='{value}'" in widget, f'the web side opens the screen {name} asks for ({value})')
if fails:
    raise SystemExit(f'{len(fails)} failed')
