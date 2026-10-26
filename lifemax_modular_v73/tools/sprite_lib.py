"""Shared helpers for editing the cat sprite sheets (8-bit index PNGs inside src/data/*.html)."""
import re, json, base64, io
import numpy as np
from PIL import Image
from pathlib import Path
from collections import deque
ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / 'src/data'
W, H = 72, 48
FACE = {90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210}   # white, pink, pupil, eyes, whisker, mouth, tongue
FUR = {20, 30, 40, 50, 60, 70, 80}
OUT = 10
PAL = {10: ' ', 0: '.'}

def load_block(name, tag):
    s = (DATA / name).read_text(encoding='utf-8')
    m = re.search(r'(<script[^>]*id="%s"[^>]*>)(.*)(</script>)' % tag, s, re.S)
    return s, m, json.loads(m.group(2))

def save_block(name, tag, J):
    s = (DATA / name).read_text(encoding='utf-8')
    m = re.search(r'(<script[^>]*id="%s"[^>]*>)(.*)(</script>)' % tag, s, re.S)
    new = s[:m.start(2)] + json.dumps(J, separators=(',', ':')) + s[m.end(2):]
    (DATA / name).write_text(new, encoding='utf-8')

def dec(u): return np.array(Image.open(io.BytesIO(base64.b64decode(u.split(',')[1]))))
def enc(a):
    b = io.BytesIO(); Image.fromarray(a.astype(np.uint8), 'L').save(b, 'PNG', optimize=True)
    return 'data:image/png;base64,' + base64.b64encode(b.getvalue()).decode()

def frame(a, row, f): return a[row * H:(row + 1) * H, f * W:(f + 1) * W].copy()
def put(a, row, f, fr): a[row * H:(row + 1) * H, f * W:(f + 1) * W] = fr

def inpaint(fr, kill, ys=None):
    """Replace pixels where kill is True by the surrounding fur (nearest non-killed opaque pixel, majority of neighbours)."""
    fr = fr.copy(); todo = kill.copy() & (fr != 0)
    keep = (fr != 0) & ~todo & (fr != OUT)
    for _ in range(40):
        if not todo.any(): break
        new = []
        ys_, xs_ = np.nonzero(todo)
        for y, x in zip(ys_, xs_):
            c = {}
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    yy, xx = y + dy, x + dx
                    if (dy or dx) and 0 <= yy < fr.shape[0] and 0 <= xx < fr.shape[1] and keep[yy, xx]:
                        c[fr[yy, xx]] = c.get(fr[yy, xx], 0) + (2 if (dy == 0 or dx == 0) else 1)
            if c: new.append((y, x, max(c, key=c.get)))
        if not new: break
        for y, x, v in new: fr[y, x] = v; keep[y, x] = True; todo[y, x] = False
    return fr

def preview(frames, scale=6, path='preview.png', bg=(43, 49, 64), pal=None):
    pal = pal or {0: None, 10: (26, 16, 28), 20: (122, 50, 20), 30: (176, 84, 30), 40: (222, 128, 48), 50: (246, 176, 96), 60: (92, 34, 14), 70: (134, 46, 14), 80: (176, 84, 30), 90: (170, 150, 140), 100: (236, 222, 204), 110: (255, 248, 236), 120: (150, 70, 79), 130: (222, 120, 130), 140: (250, 176, 176), 150: (20, 14, 22), 160: (60, 130, 70), 170: (140, 210, 110), 180: (255, 255, 255), 190: (233, 228, 238), 200: (74, 34, 48), 210: (226, 86, 110)}
    n = len(frames); h, w = frames[0].shape
    im = Image.new('RGB', (n * (w * scale + 4), h * scale), bg); px = im.load()
    for i, fr in enumerate(frames):
        for y in range(h):
            for x in range(w):
                c = pal.get(int(fr[y, x]))
                if c:
                    for dy in range(scale):
                        for dx in range(scale): px[i * (w * scale + 4) + x * scale + dx, y * scale + dy] = c
    im.save(path)
