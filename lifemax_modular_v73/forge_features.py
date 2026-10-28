#!/usr/bin/env python3
"""Offline equivalent of LifeMax Forge's feature step.

build.py assembles the core app. The shipped dist file additionally contains the
Forge "tab" features in src/features/*/ (currently Life Director). This script
injects them exactly the way Forge does, so a terminal build produces the same
file as Forge. Verified: build.py output + this script == the original
dist/Life_Max_RPG_v77.html, byte-for-byte.

Usage: python3 forge_features.py <input.html> <output.html>
"""
from pathlib import Path
import json, sys, hashlib

ROOT = Path(__file__).resolve().parent
REGISTRY = ("<script>window.LifeMaxFeatures=(()=>{const features=new Map();return Object.freeze({register(x){if(!x||typeof x.id!=='string'||features.has(x.id)||typeof x.mount!=='function')throw Error('Invalid feature registration');features.set(x.id,x)},open(id){const x=features.get(id);if(x)x.mount(document.getElementById('view-'+id))},list(){return [...features.keys()]}})})();document.addEventListener('click',e=>{const b=e.target.closest('[data-view]');if(b&&b.dataset.view&&document.getElementById('view-'+b.dataset.view)?.classList.contains('lmf-feature'))queueMicrotask(()=>LifeMaxFeatures.open(b.dataset.view))});</script>")
NOTE = '      <div class="sidebar-note">'
DASH = '      <section id="view-dashboard" class="view active">'


def once(html, needle, repl, what):
    if html.count(needle) != 1:
        raise SystemExit(f"forge_features: anchor for {what} not found exactly once")
    return html.replace(needle, repl)


def inject(html):
    feats = []
    for fj in sorted((ROOT / "src/features").glob("*/feature.json")):
        meta = json.loads(fj.read_text(encoding="utf-8"))
        d = fj.parent
        scripts = meta.get("scripts") or ["logic.js"]
        feats.append((meta, (d / "view.html").read_text(encoding="utf-8"),
                      (d / "style.css").read_text(encoding="utf-8"),
                      "".join((d / n).read_text(encoding="utf-8") for n in scripts)))
    if not feats:
        return html
    css = '<style id="lifemax-feature-styles">\n' + "".join(
        f"/* Feature {m['id']} v{m['version']} */\n{c}\n" for m, v, c, j in feats) + "</style>\n</head>"
    html = once(html, "</style>\n</head>", "</style>\n" + css, "styles")
    nav = "".join(f"""      <button class="navbtn" data-view="{m['id']}" onclick="navigate('{m['id']}')"><span class="ico">{('<i data-px="%s" data-s="18"></i>' % m['px']) if m.get('px') else m.get('icon','✦')}</span>{m['name']}</button>\n""" for m, v, c, j in feats if m.get('nav', True))
    html = once(html, NOTE, nav + NOTE.lstrip(), "nav")
    secs = "".join(f'      <section id="view-{m["id"]}" class="view lmf-feature" data-feature="{m["id"]}">{v}</section>\n' for m, v, c, j in feats)
    html = once(html, DASH, secs + DASH.lstrip(), "views")
    scripts = REGISTRY + "\n" + "".join(f"<script>\n{j}\n</script>\n" for m, v, c, j in feats)
    html = once(html, "</script>\n</body>", "</script>\n" + scripts + "</body>", "scripts")
    return html


if __name__ == "__main__":
    src, dst = Path(sys.argv[1]), Path(sys.argv[2])
    out = inject(src.read_text(encoding="utf-8"))
    dst.write_text(out, encoding="utf-8")
    print(f"Forge features injected -> {dst}  ({len(out.encode()):,} bytes, sha256 {hashlib.sha256(out.encode()).hexdigest()[:16]}…)")
