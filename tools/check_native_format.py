"""Check only the canonical new Kotlin source tree; legacy files migrate separately."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
files = sorted((root / 'android-wrapper/native/src').rglob('*.kt'))
if not files:
    raise SystemExit('No canonical Kotlin sources found')
raise SystemExit(subprocess.run([
    'java', '-jar', sys.argv[1], '--kotlinlang-style', '--dry-run',
    '--set-exit-if-changed', *(str(path) for path in files),
]).returncode)
