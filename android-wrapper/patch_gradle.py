"""Gradle tweaks applied after `cap add android`.

usage: patch_gradle.py <app/build.gradle> <build number> <sign: 0|1>
 - versionCode = the CI run number, so every build is an upgrade of the last one
 - sign: use the permanent keystore (android/app/lifemax.p12) for debug builds, so updates install over each other
"""
import re, sys

path, build, sign = sys.argv[1], sys.argv[2], sys.argv[3] == "1"
g = open(path, encoding="utf-8").read()

g, n1 = re.subn(r"versionCode\s+\d+", "versionCode " + build, g, count=1)
g, n2 = re.subn(r'versionName\s+"[^"]*"', 'versionName "1.' + build + '"', g, count=1)
assert n1 == 1 and n2 == 1, "could not set the version"

if sign and "signingConfigs.lifemax" not in g:
    block = (
        "signingConfigs {\n"
        "        lifemax {\n"
        "            storeFile file('lifemax.p12')\n"
        "            storeType 'pkcs12'\n"
        "            storePassword 'lifemax-rpg'\n"
        "            keyAlias 'lifemax'\n"
        "            keyPassword 'lifemax-rpg'\n"
        "        }\n"
        "    }\n"
        "    buildTypes {\n"
        "        debug {\n"
        "            signingConfig signingConfigs.lifemax\n"
        "            debuggable false\n"
        "        }"
    )
    g, n3 = re.subn(r"buildTypes\s*\{", lambda m: block, g, count=1)
    assert n3 == 1, "could not find buildTypes"

# A debuggable build runs Jetpack Compose several times slower (no ART optimisation of the Compose runtime, extra
# debug checks), which is felt on every native screen. The shipped APK is signed with the permanent key either way.
if "debuggable false" not in g:
    g, n4 = re.subn(r"buildTypes\s*\{", "buildTypes {\n        debug {\n            debuggable false\n        }", g, count=1)
    assert n4 == 1, "could not find buildTypes for debuggable"

open(path, "w", encoding="utf-8").write(g)
print("patched", path, "build", build, "signed" if sign else "debug key")
