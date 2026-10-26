#!/usr/bin/env python3
"""Offline LifeMax assembler. No third-party dependencies, no internet required."""
from pathlib import Path
import re, hashlib, sys
ROOT=Path(__file__).resolve().parent
TEMPLATE=ROOT/"src/index.template.html"
OUTPUT=ROOT/"dist/Life_Max_RPG_v73_modular_build.html"
INCLUDE=re.compile(r"@@INCLUDE:([A-Za-z0-9_./-]+)@@")
def assemble():
    html=TEMPLATE.read_text(encoding="utf-8")
    def sub(match):
        relative=match.group(1)
        path=(ROOT/relative).resolve()
        if not path.is_relative_to(ROOT.resolve()) or not path.is_file():
            raise ValueError(f"Invalid include: {relative}")
        return path.read_text(encoding="utf-8")
    while INCLUDE.search(html):
        html=INCLUDE.sub(sub,html)
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    OUTPUT.write_text(html,encoding="utf-8")
    digest=hashlib.sha256(html.encode("utf-8")).hexdigest()
    print(f"Built: {OUTPUT}")
    print(f"Size: {len(html.encode('utf-8')):,} bytes")
    print(f"SHA256: {digest}")
    return digest
if __name__=="__main__":
    digest=assemble()
    if len(sys.argv)>1 and sys.argv[1]=="--verify":
        expected=(ROOT/"source.sha256").read_text().strip()
        if digest!=expected:
            print("FAIL: output differs from v73 source",file=sys.stderr)
            sys.exit(1)
        print("PASS: byte-for-byte equivalent to uploaded v73")
