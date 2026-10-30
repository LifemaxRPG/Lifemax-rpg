from pathlib import Path
import subprocess,tempfile
with tempfile.TemporaryDirectory() as d:
 r=Path(d);(r/'app').mkdir()
 (r/'build.gradle').write_text("buildscript { dependencies { classpath 'com.android.tools.build:gradle:8.7.2' } }")
 (r/'app/build.gradle').write_text("apply plugin: 'com.android.application'\nandroid { }\ndependencies { }\n")
 (r/'variables.gradle').write_text('ext { minSdkVersion = 23 }')
 cmd=['python3','android-wrapper/patch_project.py',d]
 subprocess.run(cmd,check=True,capture_output=True);first=(r/'app/build.gradle').read_text();subprocess.run(cmd,check=True,capture_output=True)
 assert (r/'app/build.gradle').read_text()==first
 assert first.count("apply plugin: 'org.jetbrains.kotlin.plugin.compose'")==1
 assert "buildFeatures { compose true }" in first
 assert 'compose-compiler-gradle-plugin:2.0.21' in (r/'build.gradle').read_text()
 assert "compose-bom:2024.12.01" in first
 print('PASS: Kotlin/Compose Gradle setup, dependencies and repeatable patching')
 p=r/'AndroidManifest.xml';p.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application><activity android:name=".MainActivity" android:launchMode="singleTop"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER" /></intent-filter></activity></application></manifest>''')
 subprocess.run(['python3','android-wrapper/patch_manifest.py',str(p)],check=True,capture_output=True)
 import xml.etree.ElementTree as ET
 xml=ET.fromstring(p.read_text());ns='{http://schemas.android.com/apk/res/android}'
 activities=xml.findall('./application/activity');cash=[a for a in activities if a.get(ns+'name')=='com.lifemax.rpg.feature.cash.CashActivity'];assert len(cash)==1;assert cash[0].get(ns+'exported')=='false'
 print('PASS: native Cash activity declared, private, and manifest parses')
