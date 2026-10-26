"""Give the Android 12+ splash screen the app's dark background (only if the template uses androidx SplashScreen)."""
import re, sys

path = sys.argv[1]
try:
    s = open(path, encoding="utf-8").read()
except FileNotFoundError:
    print("splash theme: no styles.xml")
    sys.exit(0)
m = re.search(r'(<style\s+name="AppTheme\.NoActionBarLaunch"\s+parent="Theme\.SplashScreen"[^>]*>)', s)
if not m or "windowSplashScreenBackground" in s:
    print("splash theme: nothing to change")
    sys.exit(0)
s = s.replace(m.group(1), m.group(1) + '\n        <item name="windowSplashScreenBackground">@color/ic_launcher_background</item>', 1)
open(path, "w", encoding="utf-8").write(s)
print("splash theme patched")
