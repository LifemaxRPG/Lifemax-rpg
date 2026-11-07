#!/usr/bin/env python3
"""Offline LifeMax assembler. No third-party dependencies, no internet required."""
from pathlib import Path
import re, hashlib, sys, json, os
from html import escape
ROOT=Path(__file__).resolve().parent
TEMPLATE=ROOT/"src/index.template.html"
OUTPUT=ROOT/"dist/Life_Max_RPG_v73_modular_build.html"
INCLUDE=re.compile(r"@@INCLUDE:([A-Za-z0-9_./-]+)@@")
def changelog_html():
    data=json.loads((ROOT/'src/data/update-history.json').read_text(encoding='utf-8'))
    sections=[]
    for entry in data['entries']:
        build=str(entry['build'])
        if build=='current':
            build=os.environ.get('GITHUB_RUN_NUMBER','Development')
        heading=('Development build' if build=='Development' else 'Build '+build)
        changes=entry['changes']
        if not changes or not all(isinstance(c,str) and c.strip() for c in changes):
            raise ValueError('Every update needs nonempty user-facing changes')
        sections.append('<div class="settingsline"><div><strong>'+escape(heading)+' · '+escape(entry['title'])+'</strong><div class="muted" style="font-size:12px">'+escape(entry['date'])+'</div></div></div>'+''.join('<p style="margin:8px 0;line-height:1.5">• '+escape(c)+'</p>' for c in changes))
    return ''.join(sections)

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
    html=html.replace("@@UPDATE_HISTORY@@",changelog_html())
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
