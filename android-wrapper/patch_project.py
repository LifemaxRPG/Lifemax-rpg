"""Project-level Gradle changes for the Kotlin Health Connect plugin.

usage: patch_project.py <android dir>
 - Kotlin plugin on the build classpath and in the app module
 - Health Connect client and coroutines as dependencies
 - minSdk 26 (Health Connect needs it)
"""
import re, sys
from pathlib import Path

root = Path(sys.argv[1])

# 1. root build.gradle: Kotlin Gradle plugin
p = root / 'build.gradle'
s = p.read_text(encoding='utf-8')
if 'kotlin-gradle-plugin' not in s:
    s, n = re.subn(r"(classpath\s+'com\.android\.tools\.build:gradle:[^']+')", r"\1\n        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21'", s, count=1)
    assert n == 1, 'could not find the Android Gradle plugin classpath'
    p.write_text(s, encoding='utf-8')

# 2. app/build.gradle: apply the plugin, add dependencies and the Kotlin JVM target
p = root / 'app' / 'build.gradle'
s = p.read_text(encoding='utf-8')
if "kotlin-android" not in s:
    s, n = re.subn(r"(apply plugin: 'com\.android\.application')", r"\1\napply plugin: 'kotlin-android'", s, count=1)
    assert n == 1, 'could not find the application plugin line'
    s, n = re.subn(r"(dependencies\s*\{)", r"\1\n    implementation 'androidx.health.connect:connect-client:1.1.0-alpha07'\n    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1'", s, count=1)
    assert n == 1, 'could not find the dependencies block'
    s, n = re.subn(r"(android\s*\{)", r"\1\n    kotlinOptions { jvmTarget = '21' }", s, count=1)
    assert n == 1, 'could not find the android block'
    p.write_text(s, encoding='utf-8')

# 3. variables.gradle: minSdk
p = root / 'variables.gradle'
s = p.read_text(encoding='utf-8')
s, n = re.subn(r"minSdkVersion\s*=\s*\d+", "minSdkVersion = 26", s)
assert n >= 1, 'could not find minSdkVersion'
p.write_text(s, encoding='utf-8')
print('project patched for Kotlin and Health Connect')
