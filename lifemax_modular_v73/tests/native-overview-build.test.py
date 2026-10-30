from pathlib import Path
import subprocess, tempfile, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory() as d:
 p=Path(d);(p/'app').mkdir()
 (p/'build.gradle').write_text("buildscript { dependencies { classpath 'com.android.tools.build:gradle:8.7.2' } }")
 (p/'app/build.gradle').write_text("apply plugin: 'com.android.application'\nandroid {}\ndependencies {}")
 (p/'variables.gradle').write_text('ext { minSdkVersion = 23 }')
 script=root/'android-wrapper/patch_project.py'
 subprocess.run(['python3',str(script),str(p)],check=True,capture_output=True)
 first=(p/'app/build.gradle').read_text()
 subprocess.run(['python3',str(script),str(p)],check=True,capture_output=True)
 assert first==(p/'app/build.gradle').read_text()
 assert first.count('androidx.customview:customview')==1
 assert 'AndroidJUnitRunner' in first
 manifest=p/'AndroidManifest.xml'
 manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application><activity android:name=".MainActivity" android:launchMode="singleTop"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER" /></intent-filter></activity></application></manifest>')
 cmd=['python3',str(root/'android-wrapper/patch_manifest.py'),str(manifest)]
 subprocess.run(cmd,check=True,capture_output=True)
 before=manifest.read_text();subprocess.run(cmd,check=True,capture_output=True);assert before==manifest.read_text()
 ns='{http://schemas.android.com/apk/res/android}'
 activities=ET.fromstring(before).findall('./application/activity')
 assert not [a for a in activities if 'OverviewActivity' in a.get(ns+'name','')],'the retired Canvas Overview activity must not be declared'
 assert 'NativeOverviewPlugin.class' in (root/'android-wrapper/native/MainActivity.java').read_text()
 assert '50-native-overview.js@@' in (root/'lifemax_modular_v73/src/index.template.html').read_text()
 print('PASS native Overview dependencies, instrumentation runner, repeatable patches, no retired Canvas activity, registration and source include')
