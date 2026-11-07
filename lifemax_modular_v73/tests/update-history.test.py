import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

root = Path(os.environ.get('HISTORY_SOURCE', Path(__file__).resolve().parents[1]))
spec = importlib.util.spec_from_file_location('history_build', root / 'build.py')
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


class UpdateHistoryTest(unittest.TestCase):
    def test_current_build_and_readable_bullets(self):
        with patch.dict(os.environ, {'GITHUB_RUN_NUMBER': '999'}):
            html = build.changelog_html()
        self.assertIn('Build 999', html)
        self.assertIn('• ', html)
        self.assertNotIn('@@', html)
        data = json.loads((root / 'src/data/update-history.json').read_text())
        self.assertEqual(1, sum(e['build'] == 'current' for e in data['entries']))

    def test_history_is_escaped_and_previous_builds_preserved(self):
        with tempfile.TemporaryDirectory() as d:
            folder = Path(d)
            (folder / 'src/data').mkdir(parents=True)
            (folder / 'src/data/update-history.json').write_text(json.dumps({'entries': [
                {'build': '123', 'date': '2026-11-07', 'title': '<script>bad</script>', 'changes': ['A & B <button>']}
            ]}))
            with patch.object(build, 'ROOT', folder):
                html = build.changelog_html()
            self.assertIn('Build 123', html)
            self.assertNotIn('<script>', html)
            self.assertIn('A &amp; B &lt;button&gt;', html)


if __name__ == '__main__':
    unittest.main()
