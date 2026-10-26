#!/usr/bin/env python3
"""Source consistency checks; run before building or handing off changes."""
from pathlib import Path
import json, re, subprocess, sys
root=Path(__file__).resolve().parent
manifest=json.loads((root/'src/runtime/module-map.json').read_text())
parts=manifest['segments']
assert len(parts)>1 and len({x['file'] for x in parts})==len(parts)
for p in parts:
 f=root/p['file']
 assert f.is_file(), f
script=''.join((root/x['file']).read_text() for x in parts)
assert script
assert (root/'src/index.template.html').read_text().count('@@INCLUDE:src/runtime/segments/')==len(parts)
node=subprocess.run(['node','--check','-'],input=script,text=True,capture_output=True)
if node.returncode: print(node.stderr,file=sys.stderr);sys.exit(node.returncode)
print(f'PASS: {len(parts)} ordered JavaScript segments; concatenated runtime syntax valid')
