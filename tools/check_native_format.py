"""Check every native Kotlin file: the canonical src tree and the files still waiting to move into it."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
native = root / 'android-wrapper/native'
files = sorted([*native.glob('*.kt'), *(native / 'src').rglob('*.kt')])
if not files:
    raise SystemExit('No native Kotlin sources found')
raise SystemExit(subprocess.run([
    'java', '-jar', sys.argv[1], '--kotlinlang-style', '--dry-run',
    '--set-exit-if-changed', *(str(path) for path in files),
]).returncode)
