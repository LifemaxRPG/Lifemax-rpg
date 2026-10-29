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

# 2b. the Journal lock uses the AndroidX biometric prompt
p = root / 'app' / 'build.gradle'
s = p.read_text(encoding='utf-8')
if 'androidx.biometric' not in s:
    s, n = re.subn(r"(dependencies\s*\{)", r"\1\n    implementation 'androidx.biometric:biometric:1.1.0'", s, count=1)
    assert n == 1, 'could not find the dependencies block for biometric'
    p.write_text(s, encoding='utf-8')

# 3. variables.gradle: minSdk
p = root / 'variables.gradle'
s = p.read_text(encoding='utf-8')
s, n = re.subn(r"minSdkVersion\s*=\s*\d+", "minSdkVersion = 26", s)
assert n >= 1, 'could not find minSdkVersion'
p.write_text(s, encoding='utf-8')
print('project patched for Kotlin and Health Connect')

# Native Cash: the Compose compiler version follows Kotlin (2.0.21).
p = root / 'build.gradle'
s = p.read_text(encoding='utf-8')
if 'compose-compiler-gradle-plugin' not in s:
    s = s.replace("classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21'", "classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21'\n        classpath 'org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.0.21'")
    p.write_text(s, encoding='utf-8')
p = root / 'app' / 'build.gradle'
s = p.read_text(encoding='utf-8')
if "apply plugin: 'org.jetbrains.kotlin.plugin.compose'" not in s:
    s = s.replace("apply plugin: 'kotlin-android'", "apply plugin: 'kotlin-android'\napply plugin: 'org.jetbrains.kotlin.plugin.compose'")
    s = s.replace('android {', 'android {\n    buildFeatures { compose true }', 1)
    s = s.replace('dependencies {', "dependencies {\n    implementation platform('androidx.compose:compose-bom:2024.12.01')\n    implementation 'androidx.activity:activity-compose:1.9.3'\n    implementation 'androidx.compose.material3:material3'\n    implementation 'androidx.compose.material:material-icons-extended'\n    implementation 'androidx.compose.ui:ui-tooling-preview'", 1)
    p.write_text(s, encoding='utf-8')

# Native Overview uses Android Canvas and native virtual accessibility controls.
p = root / 'app' / 'build.gradle'
s = p.read_text(encoding='utf-8')
if 'androidx.customview:customview' not in s:
    s = s.replace('dependencies {', """dependencies {
    implementation 'androidx.customview:customview:1.2.0'
    androidTestImplementation 'androidx.test:runner:1.6.2'
    androidTestImplementation 'androidx.test:rules:1.6.1'
    androidTestImplementation 'androidx.test.ext:junit:1.2.1'
""", 1)
    s = s.replace('android {', """android {
    defaultConfig { testInstrumentationRunner 'androidx.test.runner.AndroidJUnitRunner' }
""", 1)
    p.write_text(s, encoding='utf-8')

