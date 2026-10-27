"""Make LifeMax selectable as the Android home screen (launcher).

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
    '            <category android:name="android.intent.category.LAUNCHER" />\n        </intent>\n        <intent>\n            <action android:name="android.speech.RecognitionService" />\n        </intent>\n        <package android:name="com.google.android.apps.healthdata" />\n    </queries>\n\n'
    '    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />\n'
    '    <uses-permission android:name="android.permission.health.READ_EXERCISE" />\n'
    '    <uses-permission android:name="android.permission.health.READ_STEPS" />\n'
    '    <uses-permission android:name="android.permission.health.READ_SLEEP" />\n'
    '    <uses-permission android:name="android.permission.health.READ_WEIGHT" />\n'
    '    <uses-permission android:name="android.permission.RECORD_AUDIO" />\n'
    '    <uses-permission android:name="android.permission.VIBRATE" />\n\n    <application',
    1,
)

# Health Connect asks the app for a page that explains why it wants the data, and a "permission usage" entry point
xml = xml.replace(
    "</application>",
    '    <activity-alias android:name=".PermissionsRationaleActivity" android:exported="true" android:targetActivity=".MainActivity">\n'
    '            <intent-filter>\n                <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />\n            </intent-filter>\n'
    '        </activity-alias>\n'
    '        <activity-alias android:name="ViewPermissionUsageActivity" android:exported="true" android:targetActivity=".MainActivity" android:permission="android.permission.START_VIEW_PERMISSION_USAGE">\n'
    '            <intent-filter>\n                <action android:name="android.intent.action.VIEW_PERMISSION_USAGE" />\n                <category android:name="android.intent.category.HEALTH_PERMISSIONS" />\n            </intent-filter>\n'
    '        </activity-alias>\n'
    '        <receiver android:name=".LifeDialWidget" android:exported="false">\n'
    '            <intent-filter>\n                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />\n            </intent-filter>\n'
    '            <meta-data android:name="android.appwidget.provider" android:resource="@xml/lifemax_dial_widget_info" />\n'
    '        </receiver>\n    </application>',
    1,
)
assert "category.HOME" in xml and "<queries>" in xml and "REQUEST_INSTALL_PACKAGES" in xml and "health.READ_STEPS" in xml and "SHOW_PERMISSIONS_RATIONALE" in xml and "RECORD_AUDIO" in xml and "RecognitionService" in xml, "manifest patch failed"
open(path, "w", encoding="utf-8").write(xml)
