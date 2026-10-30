#!/usr/bin/env python3
"""Source consistency checks; run before building or handing off changes."""
from pathlib import Path
import subprocess, sys
root=Path(__file__).resolve().parent
sys.path.insert(0,str(root/'tools'))
from gen_module_map import MODULE_MAP, SYMBOL_INDEX, dump, maps, segment_files
parts=segment_files()
assert len(parts)>1 and len(set(parts))==len(parts)
for p in parts:
 f=root/p
 assert f.is_file(), f
script=''.join((root/p).read_text(encoding='utf-8') for p in parts)
node=subprocess.run(['node','--check','-'],input=script,text=True,capture_output=True)
if node.returncode: print(node.stderr,file=sys.stderr);sys.exit(node.returncode)
for path,data in zip((MODULE_MAP,SYMBOL_INDEX),maps()):
 if path.read_text(encoding='utf-8')!=dump(data):
  sys.exit(f'FAIL: {path.relative_to(root)} does not match src/index.template.html and the segments; run python3 tools/gen_module_map.py')
print(f'PASS: {len(parts)} ordered JavaScript segments; concatenated runtime syntax valid; module map and symbol index current')
