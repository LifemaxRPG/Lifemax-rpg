"""Make LifeMax RPG selectable as the Android home screen (launcher).

Adds the HOME + DEFAULT categories to the main activity's launcher intent
filter and sets launchMode=singleTask so pressing Home returns to the app.
"""
import re, sys

path = sys.argv[1]
xml = open(path, encoding="utf-8").read()

if "android.intent.category.HOME" in xml:
    sys.exit(0)

xml = re.sub(
    r'(<category\s+android:name="android.intent.category.LAUNCHER"\s*/>)',
    r'\1\n                <category android:name="android.intent.category.HOME" />'
    r'\n                <category android:name="android.intent.category.DEFAULT" />',
    xml,
    count=1,
)
xml = re.sub(r'android:launchMode="[^"]*"', 'android:launchMode="singleTask"', xml)
# Android 11+ hides other apps unless we ask to see launchable ones (Apps tab).
xml = xml.replace(
    "<application",
    '<queries>\n        <intent>\n            <action android:name="android.intent.action.MAIN" />\n'
    '            <category android:name="android.intent.category.LAUNCHER" />\n        </intent>\n    </queries>\n\n    <application',
    1,
)

assert "category.HOME" in xml and "<queries>" in xml, "manifest patch failed"
open(path, "w", encoding="utf-8").write(xml)
