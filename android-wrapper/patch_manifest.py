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
assert "category.HOME" in xml, "manifest patch failed"
open(path, "w", encoding="utf-8").write(xml)
