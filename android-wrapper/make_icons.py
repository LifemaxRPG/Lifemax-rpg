"""Rebuilds every launcher icon from an icon pack: icons/<id>.png (the artwork with its rounded black corners).
Run from android-wrapper:  python3 make_icons.py [id]   (no id = the one named in icons/ACTIVE; an id is remembered there)
Switching packs changes native resources, so also raise VALUE in native/NativeApi.java so phones are offered a new APK.
 - adaptive foreground: the artwork with the corners cropped off, filling the whole 108 dp canvas (Android masks it)
 - legacy square and round icons, and the splash picture, with their own rounded shapes"""
from PIL import Image, ImageDraw
import os, sys
PACK = sys.argv[1] if len(sys.argv) > 1 else open('icons/ACTIVE').read().strip()
open('icons/ACTIVE', 'w').write(PACK + '\n')
SRC = Image.open(f'icons/{PACK}.png').convert('RGB')
INSET = 112                                    # drops the rounded black corners of the supplied picture
ART = SRC.crop((INSET, INSET, SRC.width - INSET, SRC.height - INSET))
def rounded(im, size, frac):
    im = im.resize((size, size), Image.LANCZOS).convert('RGBA')
    big = Image.new('L', (size * 4, size * 4), 0)
    ImageDraw.Draw(big).rounded_rectangle((0, 0, size * 4 - 1, size * 4 - 1), radius=int(size * 4 * frac), fill=255)
    im.putalpha(big.resize((size, size), Image.LANCZOS)); return im
def circle(im, size):
    im = im.resize((size, size), Image.LANCZOS).convert('RGBA')
    big = Image.new('L', (size * 4, size * 4), 0); ImageDraw.Draw(big).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    im.putalpha(big.resize((size, size), Image.LANCZOS)); return im
D = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
for name, k in D.items():
    d = f'res/mipmap-{name}'; os.makedirs(d, exist_ok=True)
    ART.resize((int(108 * k), int(108 * k)), Image.LANCZOS).save(f'{d}/ic_launcher_foreground.png', optimize=True)
    rounded(ART, int(48 * k), .22).save(f'{d}/ic_launcher.png', optimize=True)
    circle(ART, int(48 * k)).save(f'{d}/ic_launcher_round.png', optimize=True)
rounded(ART, 512, .22).save('res/drawable/splash.png', optimize=True)
print('icons written from pack', PACK)
