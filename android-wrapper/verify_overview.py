"""Run emulator parity checks and preserve diagnostics even when a test fails."""
from pathlib import Path
import base64, subprocess, sys
status=subprocess.run(['./android-wrapper/android/gradlew','-p','android-wrapper/android',':app:connectedDebugAndroidTest','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m','-Pandroid.testInstrumentationRunnerArguments.class=com.lifemax.rpg.OverviewParityTest']).returncode
out=Path('native-overview-review');out.mkdir(exist_ok=True)
for name in ['overview-web.png','overview-native.png','overview-software.png','overview-parity.json','character-web.png','character-native.png']:
    result=subprocess.run(['adb','exec-out','cat','/sdcard/Download/'+name],capture_output=True)
    if result.returncode==0 and result.stdout and (not name.endswith('.png') or result.stdout.startswith(b'\x89PNG')):
        (out/name).write_bytes(result.stdout)
        if name.endswith('.json'): print(result.stdout.decode())
        else:
            encoded=base64.b64encode(result.stdout).decode()
            for index,start in enumerate(range(0,len(encoded),8000)): print('OVERVIEW_CHUNK_'+name+':'+str(index)+':'+encoded[start:start+8000])
log=subprocess.run(['adb','logcat','-d','-s','NativeOverview:E','AndroidRuntime:E'],capture_output=True,text=True)
print(log.stdout[-12000:])
sys.exit(status)
