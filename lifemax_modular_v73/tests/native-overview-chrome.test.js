/* The native bars are drawn from a capture of the web top bar, cached by a key. A button that appears without changing any text (the
   voice mic, added once the voice engine reports it is available) must invalidate the cache, or the bars keep an old capture without it.
   Run: NODE_PATH=$(npm root -g) node tests/native-overview-chrome.test.js */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const { execFileSync } = require('child_process');
const root = path.resolve(__dirname, '..'), out = path.join(require('os').tmpdir(), 'lm_chrome_' + process.pid + '.html');
execFileSync('python3', [path.join(root, 'build.py')], { cwd: root, stdio: 'ignore' });
execFileSync('python3', [path.join(root, 'forge_features.py'), path.join(root, 'dist', 'Life_Max_RPG_v73_modular_build.html'), out], { cwd: root, stdio: 'ignore' });
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 400) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript(`localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));
    window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>n==='VoiceEngine',getPlatform:()=>'android',Plugins:{VoiceEngine:{addListener:async()=>({remove(){}}),available:async()=>({available:true}),cancel:async()=>{}}},registerPlugin:()=>({}),convertFileSrc:u=>u};`);
  await p.goto('file://' + out); await p.waitForTimeout(3500);
  const r = await p.evaluate(async () => {
    const fab = document.querySelector('.lmv-fab');
    const near = (scene) => { const f = fab.getBoundingClientRect(); return (scene.header.nodes || []).some(n => n.kind === 'image' && Math.abs(n.x + n.w / 2 - (f.x + f.width / 2)) < 20) };
    fab.style.display = 'none';
    const without = await NativeOverviewSnapshot.snapshot();
    fab.style.display = '';
    const withMic = await NativeOverviewSnapshot.snapshot();
    return { fab: !!fab, before: near(without), after: near(withMic) };
  });
  ok('the voice mic exists in the web top bar', r.fab, r);
  ok('a capture taken while the mic is hidden has no mic', r.before === false, r);
  ok('once the mic appears, the bars are captured again and include it (the cache key sees icon-only buttons)', r.after === true, r);
  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); fs.unlinkSync(out); process.exit(fail ? 1 : 0);
})();
