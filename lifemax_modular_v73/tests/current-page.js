/* The page the web tests run: the current source, built (build.py) and forged (forge_features.py) into a temporary file. A test
   given a built page on its command line uses that instead, and tools/run_tests.sh builds once and hands the same page to every
   test through LMA_TEST_PAGE. Use: const file = require('./current-page')(process.argv[2]); */
const fs = require('fs'), http = require('http'), os = require('os'), path = require('path'), { execFileSync } = require('child_process');
const root = path.resolve(__dirname, '..');

function build(out) {
  execFileSync('python3', [path.join(root, 'build.py')], { cwd: root, stdio: 'ignore' });
  execFileSync('python3', [path.join(root, 'forge_features.py'), path.join(root, 'dist', 'Life_Max_RPG_v73_modular_build.html'), out], { cwd: root, stdio: 'ignore' });
  return out;
}

/* Serves a page at http://127.0.0.1 for a test that reloads it: a reload of a file:// page in this Chromium sometimes comes back
   without what the page stored (sessionStorage empty, the last localStorage writes missing). Resolves to { url, close }. */
function serve(file) {
  return new Promise(resolve => {
    const server = http.createServer((q, s) => {
      if (q.url !== '/') { s.writeHead(404); s.end(); return }
      s.writeHead(200, { 'content-type': 'text/html; charset=utf-8' }); fs.createReadStream(file).pipe(s);
    });
    server.listen(0, '127.0.0.1', () => resolve({ url: 'http://127.0.0.1:' + server.address().port + '/', close: () => server.close() }));
  });
}

module.exports = function currentPage(given) {
  if (given) return path.resolve(given);
  if (process.env.LMA_TEST_PAGE) return process.env.LMA_TEST_PAGE;
  const out = build(path.join(os.tmpdir(), 'lm_test_' + process.pid + '.html'));
  process.on('exit', () => { try { fs.unlinkSync(out) } catch (e) { } });
  return out;
};
module.exports.build = build;
module.exports.serve = serve;
