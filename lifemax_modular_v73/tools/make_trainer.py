#!/usr/bin/env python3
"""Converts a skinned Collada character in art/trainer/src into a trainer pack, art/trainer/trainer-pack.json.
The pack is NOT part of the app or the repo (art/trainer is git-ignored). Load it on the phone from
Settings -> Look & feel -> Guided workout -> Load trainer pack; it is then stored on that device only.
  python3 tools/make_trainer.py
Model space is converted to the gym's space: +x forward, +y up, +z toward the camera side, hip at the origin, scaled to metres."""
import sys, os, io, json, base64, struct, xml.etree.ElementTree as ET
import numpy as np
from PIL import Image, ImageDraw, ImageFont
ns = {'c': 'http://www.collada.org/2005/11/COLLADASchema'}
S = .16
R = np.array([[1, 0, 0], [0, 0, 1], [0, -1, 0]], float)      # model (x fwd, y left, z up) -> gym (x fwd, y up, z right)
def mat(t): return np.array(t.split(), float).reshape(4, 4)
root = ET.parse('art/trainer/src/trainerM.dae').getroot()
sk = root.find('.//c:skin', ns)
names = sk.find('.//c:Name_array', ns).text.split()
srcs = sk.findall('c:source', ns)
ibm = np.array(srcs[1].find('c:float_array', ns).text.split(), float).reshape(-1, 4, 4)
wts = np.array(srcs[2].find('c:float_array', ns).text.split(), float)
bind = np.array([np.linalg.inv(m)[:3, 3] for m in ibm])
hip = bind[names.index('hip')]
G = lambda p: (R @ (p - hip)) * S
parents = {}
def walk(n, par):
    for ch in n.findall('c:node', ns):
        if ch.get('type') == 'JOINT': parents[ch.get('name')] = par; walk(ch, ch.get('name'))
walk(root.find('.//c:visual_scene', ns), None)
joints = [{'n': n, 'p': names.index(parents[n]) if parents[n] else -1, 'b': [round(float(x), 5) for x in G(bind[i])]} for i, n in enumerate(names)]
# every body part is its own mesh with its own skin; merge them into one vertex buffer
geoms = {g.get('id'): g.find('c:mesh', ns) for g in root.findall('.//c:geometry', ns)}
def pack(P, N, T, J, W):
    out = bytearray()
    for i in range(len(P)):
        p = (R @ (P[i] - hip)) * S; n = R @ N[i]
        out += struct.pack('<3f3f2f', *p, *n, T[i][0], 1 - T[i][1]) + bytes(J[i]) + bytes(np.clip(np.round(W[i] * 255), 0, 255).astype(np.uint8))
    return bytes(out)
mats = {'upper': 'upper', 'hair_C': 'hair_C', 'hair': 'hair', 'hair_B': 'hair_B', 'logo': 'logo', 'pants': 'pants', 'body': 'body'}
matname = {m.get('id'): m.find('.//c:user_properties', ns).text.replace('.png', '').replace('trainerM_', '') for m in root.findall('.//c:material', ns)}
verts = bytearray(); idx = bytearray(); groups = []; base = 0; off = 0
for ctl in root.findall('.//c:controller', ns):
    sk = ctl.find('c:skin', ns); gm = geoms[sk.get('source')[1:]]
    ss = sk.findall('c:source', ns); jn = ss[0].find('c:Name_array', ns).text.split()
    wts = np.array(ss[2].find('c:float_array', ns).text.split(), float)
    srcm = {x.get('id'): np.array(x.find('c:float_array', ns).text.split(), float) for x in gm.findall('c:source', ns)}
    vin = {i.get('semantic'): i.get('source')[1:] for i in gm.find('c:vertices', ns)}
    P = srcm[vin['POSITION']].reshape(-1, 3); N = srcm[vin['NORMAL']].reshape(-1, 3); T = srcm[vin['TEXCOORD']].reshape(-1, 2)
    vw = sk.find('c:vertex_weights', ns); vc = list(map(int, vw.find('c:vcount', ns).text.split())); v = list(map(int, vw.find('c:v', ns).text.split()))
    J = np.zeros((len(P), 4), np.uint8); W = np.zeros((len(P), 4), np.float32); k = 0
    for i, c in enumerate(vc):
        pairs = sorted([(wts[v[k + 2 * j + 1]], v[k + 2 * j]) for j in range(c)], reverse=True)[:4]; k += 2 * c
        tot = sum(p_[0] for p_ in pairs) or 1
        for j, (w, jj) in enumerate(pairs): J[i, j] = names.index(jn[jj]); W[i, j] = w / tot
    verts += pack(P, N, T, J, W)
    for t in gm.findall('c:triangles', ns):
        ids = [i + base for i in map(int, t.find('c:p', ns).text.split())]; idx += struct.pack('<%dH' % len(ids), *ids)
        mid = t.get('material')[:-1] if t.get('material').endswith('0') else t.get('material')
        tex = matname[ [m.get('id') for m in root.findall('.//c:material', ns) if m.get('id') == 'VisualMaterial' + (mid.replace('VisualMaterial', '') if False else '')] and 'VisualMaterial' ] if False else None
        groups.append({'src': t.get('material'), 'start': off, 'count': len(ids)}); off += len(ids)
    base += len(P)
# resolve each triangle group's texture through the visual scene's material bindings
bind_map = {}
for im in root.findall('.//c:instance_material', ns): bind_map[im.get('symbol')] = im.get('target')[1:]
for g in groups: g['tex'] = matname[bind_map[g['src']]]; del g['src']
P = np.zeros((base, 3))
# face (rigid on the head)
fr = ET.parse('art/trainer/src/trainerM_face.dae').getroot(); fmesh = {g.get('id'): g.find('c:mesh', ns) for g in fr.findall('.//c:geometry', ns)}
head = bind[names.index('head')]; fverts = bytearray(); fidx = bytearray(); fgroups = []; base = 0; foff = 0
for gid, tex in (('Geometry', 'eye'), ('Geometry1', 'face')):
    m = fmesh[gid]; s = {x.get('id'): np.array(x.find('c:float_array', ns).text.split(), float) for x in m.findall('c:source', ns)}
    ks = sorted(s); Pf = s[ks[0]].reshape(-1, 3); Nf = s[ks[1]].reshape(-1, 3); Tf = s[ks[2]].reshape(-1, 2)
    Jf = np.zeros((len(Pf), 4), np.uint8); Jf[:, 0] = names.index('head'); Wf = np.zeros((len(Pf), 4), np.float32); Wf[:, 0] = 1
    FR = np.array([[0, 1, 0], [-1, 0, 0], [0, 0, 1]], float)   # the face file is Y-forward, X-sideways
    fverts += pack(Pf @ FR.T + head, Nf @ FR.T, Tf, Jf, Wf)
    ids = [i + base for i in map(int, m.find('c:triangles', ns).find('c:p', ns).text.split())]; fidx += struct.pack('<%dH' % len(ids), *ids)
    fgroups.append({'tex': tex, 'start': foff, 'count': len(ids)}); foff += len(ids); base += len(Pf)
# textures (the chest logo is redrawn to read LIFEMAX)
def webp(im, q=82):
    b = io.BytesIO(); im.save(b, 'WEBP', quality=q, lossless=False if im.mode == 'RGB' else True); return base64.b64encode(b.getvalue()).decode()
def png(im):
    b = io.BytesIO(); im.save(b, 'PNG', optimize=True); return base64.b64encode(b.getvalue()).decode()
def logo():
    im = Image.new('RGBA', (128, 64), (255, 255, 255, 0)); d = ImageDraw.Draw(im)
    f1 = ImageFont.truetype('/usr/share/fonts/truetype/freefont/FreeSansBold.ttf', 25); f2 = ImageFont.truetype('/usr/share/fonts/truetype/freefont/FreeSansBoldOblique.ttf', 25)
    w1 = d.textlength('LIFE', font=f1); w2 = d.textlength('MAX', font=f2); x = (128 - w1 - w2) / 2
    d.text((x, 18), 'LIFE', font=f1, fill=(32, 76, 140, 255)); d.text((x + w1, 18), 'MAX', font=f2, fill=(32, 120, 200, 255)); return im
texs = {}
for n in ('upper', 'hair', 'hair_B', 'hair_C', 'pants', 'body'): texs[n] = png(Image.open(f'art/trainer/src/trainerM_{n}.png').convert('RGBA'))
texs['logo'] = png(logo()); texs['face'] = png(Image.open('art/trainer/src/trainerM_face.png').convert('RGBA')); texs['eye'] = png(Image.open('art/trainer/src/trainerM_eye.1.png').convert('RGBA'))
data = {'joints': joints, 'groups': groups, 'fgroups': fgroups, 'nv': base, 'nfv': len(fverts) // 40, 'verts': base64.b64encode(verts).decode(), 'idx': base64.b64encode(idx).decode(), 'fverts': base64.b64encode(bytes(fverts)).decode(), 'fidx': base64.b64encode(bytes(fidx)).decode(), 'tex': texs}
data['format'] = 'lifemax-trainer-pack'; data['version'] = 1; data['name'] = 'Trainer'
out = 'art/trainer/trainer-pack.json'
open(out, 'w').write(json.dumps(data, separators=(',', ':')))
print('wrote', out, os.path.getsize(out) // 1024, 'KB', base, 'verts')
