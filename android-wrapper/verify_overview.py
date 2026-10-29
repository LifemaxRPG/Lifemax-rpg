"""Run emulator parity checks and preserve diagnostics even when a test fails."""
from pathlib import Path
import base64, subprocess, sys
status=subprocess.run(['./android-wrapper/android/gradlew','-p','android-wrapper/android',':app:connectedDebugAndroidTest','-Pandroid.testInstrumentationRunnerArguments.class=com.lifemax.rpg.OverviewParityTest']).returncode
out=Path('native-overview-review');out.mkdir(exist_ok=True)
for name in ['overview-web.png','overview-native.png','overview-parity.json']:
    result=subprocess.run(['adb','exec-out','run-as','com.lifemax.rpg','cat','files/'+name],capture_output=True)
    if result.returncode==0 and result.stdout and (not name.endswith('.png') or result.stdout.startswith(b'\x89PNG')):
        (out/name).write_bytes(result.stdout)
        if name.endswith('.json'): print(result.stdout.decode())
        else: print('OVERVIEW_IMAGE_'+name+':'+base64.b64encode(result.stdout).decode())
log=subprocess.run(['adb','logcat','-d','-s','NativeOverview:E','AndroidRuntime:E'],capture_output=True,text=True)
print(log.stdout[-12000:])
sys.exit(status)
