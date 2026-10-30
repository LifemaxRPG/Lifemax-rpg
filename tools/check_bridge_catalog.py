#!/usr/bin/env python3
"""Every Capacitor plugin is registered in MainActivity, every native destination plugin (one named Native...) has an
entry in docs/architecture/BRIDGE_CATALOG.json, and every catalog entry names a plugin that exists (CONTRACT A27)."""
import json
import re
import sys
from pathlib import Path

root = Path(__file__).resolve().parent.parent
native = root / "android-wrapper" / "native"
sources = [p for p in native.rglob("*") if p.suffix in (".java", ".kt") and "src/test" not in p.as_posix()]

everything = {}  # plugin name -> class name
for path in sources:
    text = path.read_text(encoding="utf-8")
    for match in re.finditer(r'@CapacitorPlugin\(\s*name\s*=\s*"(\w+)".*?\)\s*(?:public\s+)?class\s+(\w+)', text, re.S):
        everything[match.group(1)] = match.group(2)
plugins = {name: cls for name, cls in everything.items() if name.startswith("Native")}

main = (native / "MainActivity.java").read_text(encoding="utf-8")
registered = set(re.findall(r"registerPlugin\(\s*(?:[\w.]+\.)?(\w+)\.class\s*\)", main))
catalog = {f["nativePlugin"] for f in json.loads((root / "docs/architecture/BRIDGE_CATALOG.json").read_text())["features"] if f.get("nativePlugin")}

problems = [f"{name} ({cls}) is not registered in MainActivity.java" for name, cls in sorted(everything.items()) if cls not in registered]
problems += [f"{name} has no entry in docs/architecture/BRIDGE_CATALOG.json" for name in sorted(plugins) if name not in catalog]
problems += [f"BRIDGE_CATALOG.json names {name}, but no plugin has that name" for name in sorted(catalog - plugins.keys())]
for problem in problems:
    print("FAIL", problem)
if problems:
    sys.exit(1)
print(f"PASS bridge catalog: {len(everything)} plugins registered, {len(plugins)} native destinations catalogued")
