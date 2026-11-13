/* The native bottom bar scrubs along the tabs from what each bar scene says (scene.scrub, v1): the tabs in the order the bar shows
   them, the id of each button, the tab the page is on, the owner's scrub speed and haptics switch, and the look of the selected pill and
   of the bar. This checks the page's half: the list follows the bar as it is (Quests last by default, hidden tabs left out, a moved tab
   moved), the ids answer to the real buttons, the speed names are the ones native maps, and the bar's tabs line up (icon and label at the
   same height on every tab, one hairline on top, a solid surface, no shadow).
   Run: NODE_PATH=$(npm root -g) node tests/native-bar-scrub.test.js */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const { execFileSync } = require('child_process');
const root = path.resolve(__dirname, '..'), out = path.join(require('os').tmpdir(), 'lm_barscrub_' + process.pid + '.html');
execFileSync('python3', [path.join(root, 'build.py')], { cwd: root, stdio: 'ignore' });
execFileSync('python3', [path.join(root, 'forge_features.py'), path.join(root, 'dist', 'Life_Max_RPG_v73_modular_build.html'), out], { cwd: root, stdio: 'ignore' });
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 400) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  for (const theme of ['dark', 'light']) {
    const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
    await p.addInitScript(`localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));
      window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:()=>false,getPlatform:()=>'android',Plugins:{},registerPlugin:()=>({}),convertFileSrc:u=>u};`);
    await p.goto('file://' + out); await p.waitForTimeout(3500);
    if (theme === 'light') { await p.evaluate(() => document.documentElement.classList.add('light-theme')); await p.waitForTimeout(400) }
    const scene = () => p.evaluate(async () => { const s = await NativeOverviewSnapshot.snapshot(); return { scrub: s.scrub, navHits: s.nav.hits.length } });
    const bar = () => p.evaluate(() => [...document.querySelectorAll('.sidebar .navbtn[data-view]')].filter(b => b.offsetWidth > 0).map(b => b.dataset.view));
    const t = theme + ': ';
    let r = await scene(), shown = await bar();
    ok(t + 'the scene carries the scrub state', !!r.scrub && r.scrub.v === 1, r);
    ok(t + 'its tabs are the bar as it is, in order, Quests the very last', r.scrub.tabs.map(x => x.key).join() === shown.join() && shown[shown.length - 1] === 'quests', [r.scrub.tabs.map(x => x.key), shown]);
    ok(t + 'every tab has an id that is a hit of the bar and a real button', await p.evaluate(async () => {
      const s = await NativeOverviewSnapshot.snapshot(), ids = new Set(s.nav.hits.map(h => h.id));
      return s.scrub.tabs.length > 5 && s.scrub.tabs.every(x => ids.has(x.id));
    }));
    ok(t + 'the active tab is the one the page is on', r.scrub.active === 'dashboard', r.scrub.active);
    ok(t + 'the speed is one of the names native maps, fast by default', r.scrub.speed === 'fast', r.scrub.speed);
    for (const k of ['normal', 'faster']) { await p.evaluate(k => lmaSetScrubSpeed(k), k); r = await scene(); ok(t + 'the speed setting reaches the scene: ' + k, r.scrub.speed === k, r.scrub.speed) }
    await p.evaluate(() => { lmaLoad().scrubSpeed = 'nonsense' }); r = await scene(); ok(t + 'an unknown speed is fast, as on the page', r.scrub.speed === 'fast', r.scrub.speed);
    await p.evaluate(() => { state.fx = { ...(state.fx || {}), haptics: false } }); r = await scene(); ok(t + 'haptics off reaches the scene', r.scrub.haptics === false);
    await p.evaluate(() => { state.fx.haptics = true });
    ok(t + 'the pill look is a colour and a radius, the surface is opaque', r.scrub.pill && r.scrub.pill.color > 0 && r.scrub.pill.radius >= 10 && Math.floor(r.scrub.surface / 2 ** 24) === 255, r.scrub);
    await p.evaluate(() => lmaTabShow('money', false)); r = await scene();
    ok(t + 'a hidden tab is not in the list', !r.scrub.tabs.some(x => x.key === 'money'), r.scrub.tabs.map(x => x.key));
    await p.evaluate(() => lmaTabMove('plan', 1)); r = await scene(); shown = await bar();
    ok(t + 'a moved tab is moved in the list', r.scrub.tabs.map(x => x.key).join() === shown.join(), [r.scrub.tabs.map(x => x.key), shown]);
    await p.evaluate(() => lmaTabsReset()); await p.evaluate(() => navigate('pet')); await p.waitForTimeout(500); r = await scene();
    ok(t + 'on another tab, that tab is the active one', r.scrub.active === 'pet', r.scrub.active);
    // revealing a tab also reveals the tabs either side of it, so you always see where you came from and what is next
    const nb = await p.evaluate(() => {
      lmaScrubInit(); lmaScrubItems(); const S = lmaScrub, sb = S.sb, items = S.items, out = [];
      const seen = k => { const b = items[k], l = b.offsetLeft, r = l + b.offsetWidth; return l >= sb.scrollLeft - 1 && r <= sb.scrollLeft + sb.clientWidth + 1 };
      for (const i of [1, 4, items.length - 2, items.length - 1, 0]) {
        for (const start of ['left', 'right']) {
          cancelAnimationFrame(lmaBarRaf); lmaBarRaf = 0; S.drag = null;
          sb.scrollLeft = start === 'left' ? items[i].offsetLeft - 3 : items[i].offsetLeft + items[i].offsetWidth + 3 - sb.clientWidth;
          lmaScrubReveal(i);
          out.push({ i, start, cur: seen(i), prev: i === 0 || seen(i - 1), next: i === items.length - 1 || seen(i + 1) });
        }
      }
      return { out, n: items.length, fits: sb.scrollWidth > sb.clientWidth };
    });
    ok(t + 'the bar scrolls (there are more tabs than fit)', nb.fits && nb.n > 5, nb);
    ok(t + 'a revealed tab and both its neighbours are fully on the bar, from either side', nb.out.every(x => x.cur && x.prev && x.next), nb.out.filter(x => !(x.cur && x.prev && x.next)));
    // the look of the bar
    const look = await p.evaluate(() => {
      const bar = document.querySelector('.sidebar'), cs = getComputedStyle(bar), tabs = [...bar.querySelectorAll('.navbtn[data-view]')].filter(b => b.offsetWidth > 0);
      const rows = tabs.map(b => ({ icon: b.querySelector('.ico').offsetTop - b.offsetTop, h: b.offsetHeight }));
      return { shadow: cs.boxShadow, top: cs.borderTopWidth, sides: [cs.borderLeftWidth, cs.borderRightWidth, cs.borderBottomWidth].join(), bg: cs.backgroundColor, height: Math.round(bar.getBoundingClientRect().height), bottom: Math.round(innerHeight - bar.getBoundingClientRect().bottom), icons: [...new Set(rows.map(x => x.icon))], hs: [...new Set(rows.map(x => x.h))], blur: cs.backdropFilter };
    });
    ok(t + 'the bar sits on the bottom edge, solid, one hairline, no shadow, no blur', look.bottom === 0 && look.shadow === 'none' && look.top === '1px' && look.sides === '0px,0px,0px' && (look.blur === 'none' || !look.blur) && !/rgba\(.*,\s*0\.\d+\)/.test(look.bg), look);
    ok(t + 'the bar is as tall as the page reserves for it', look.height === 64, look);
    ok(t + 'every tab has its icon at the same height and is as tall as the others', look.icons.length === 1 && look.hs.length === 1, look);
    ok(t + 'no script errors', errs.length === 0, errs);
    if (process.env.BAR_SHOTS) await p.screenshot({ path: path.join(process.env.BAR_SHOTS, 'bar-' + theme + '.png'), clip: { x: 0, y: 760, width: 412, height: 100 } });
    await p.context().close();
  }
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); fs.unlinkSync(out); process.exit(fail ? 1 : 0);
})();
