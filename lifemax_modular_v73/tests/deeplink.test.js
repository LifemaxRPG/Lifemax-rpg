/* Deep links from Assistant / Gemini / shortcuts / any other app: parsing, hostile input (backlog SEC-3), asking before
   feeding, the scheduling hand-off that stops at the preview, queueing before ready, and no replay after a reload.
   Builds the current source (like log-activity.test.js) unless a built page is given.
   Run: NODE_PATH=$(npm root -g) node tests/deeplink.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const { execFileSync } = require('child_process');
const root = path.resolve(__dirname, '..');
let file = process.argv[2] && path.resolve(process.argv[2]);
if (!file) {
  file = path.join(require('os').tmpdir(), 'lm_deeplink_' + process.pid + '.html');
  execFileSync('python3', [path.join(root, 'build.py')], { cwd: root, stdio: 'ignore' });
  execFileSync('python3', [path.join(root, 'forge_features.py'), path.join(root, 'dist', 'Life_Max_RPG_v73_modular_build.html'), file], { cwd: root, stdio: 'ignore' });
}
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 300) : '')) } };
const BAD = /[\u0000-\u001f\u007f-\u009f\u200b-\u200f\u2028-\u202e\u2066-\u2069\ufeff]/;
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] }); const p = await (await b.newContext({ viewport: { width: 393, height: 844 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript("if(!localStorage.getItem('lifemax_rpg_prototype_v1'))localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p.goto('file://' + file); await p.waitForTimeout(2500);
  const P = u => p.evaluate(u => LifeMaxDeepLink.parse(u), u);
  ok('schedule link parses title and description', JSON.stringify(await P('lifemax://schedule/add?title=Dentist%20at%203&description=cleaning')) === JSON.stringify({ kind: 'schedule', title: 'Dentist at 3', description: 'cleaning' }));
  ok('plus signs decode as spaces', (await P('lifemax://schedule/add?title=Go+for+a+run')).title === 'Go for a run');
  ok('feed link', (await P('lifemax://cats/feed')).kind === 'feed');
  const feats = await p.evaluate(() => ['feed the cats', 'Feed my cat', 'schedule', 'life director', 'quests', 'my cats', 'banana'].map(f => LifeMaxDeepLink.parse('lifemax://open?feature=' + encodeURIComponent(f)).kind));
  ok('open?feature maps spoken names', JSON.stringify(feats) === JSON.stringify(['feed', 'feed', 'schedule', 'schedule', 'quests', 'cats', 'none']), feats);
  ok('foreign links are ignored', await p.evaluate(() => LifeMaxDeepLink.parse('https://example.com') === null && LifeMaxDeepLink.handle('https://example.com') === false));

  /* every link the app itself declares (launcher shortcuts and Assistant capabilities) still parses to what it is for */
  const sc = fs.readFileSync(path.join(root, '..', 'android-wrapper', 'res', 'xml', 'shortcuts.xml'), 'utf8');
  const declared = [...sc.matchAll(/android:data="(lifemax:[^"]+)"/g)].map(m => m[1]).concat([...sc.matchAll(/url-template android:value="(lifemax:[^"{]+)\{\?([^}]+)\}"/g)].map(m => m[1] + '?' + m[2].split(',').map(k => k + '=' + (k === 'feature' ? 'feed%20the%20cats' : 'Gym%20session')).join('&')));
  const kinds = await p.evaluate(us => us.map(u => (LifeMaxDeepLink.parse(u) || {}).kind || null), declared);
  ok('every declared shortcut and Assistant link parses (' + declared.length + ')', declared.length >= 5 && JSON.stringify(kinds) === JSON.stringify(['schedule', 'feed', 'quests', 'schedule', 'feed']), { declared, kinds });

  /* hostile text: control, zero-width and direction-override characters go, length is capped at 120 characters */
  const h = await P('lifemax://schedule/add?title=Den%00tist%0A%0D%1B%5B31m%E2%80%AEexe.txt%E2%80%8B%C2%85%7F&description=' + encodeURIComponent('a\u2066b\u2028c\ufeffd'));
  ok('control and direction-override characters are stripped', !BAD.test(h.title) && !BAD.test(h.description) && h.title === 'Den tist [31m exe.txt' && h.description === 'a b c d', h);
  const long = await P('lifemax://schedule/add?title=' + 'a'.repeat(500) + '&description=' + 'b'.repeat(500));
  ok('title and description are capped at 120 characters', long.title.length === 120 && long.description.length === 120, [long.title.length, long.description.length]);
  const emo = await P('lifemax://schedule/add?title=' + encodeURIComponent('\u{1F63A}'.repeat(200)));
  ok('the cap counts characters and never splits one', Array.from(emo.title).length === 120 && (() => { try { encodeURIComponent(emo.title); return true } catch (e) { return false } })(), Array.from(emo.title).length);
  ok('an over-long link is refused', await p.evaluate(() => LifeMaxDeepLink.parse('lifemax://schedule/add?title=' + 'x'.repeat(5000)) === null && LifeMaxDeepLink.handle('lifemax://schedule/add?title=' + 'x'.repeat(5000)) === false));
  ok('broken percent-encoding does not throw', (await P('lifemax://schedule/add?title=%E0%A4%A&description=%')).kind === 'schedule' && errs.length === 0, errs);
  const nf = await P('lifemax://open?feature=' + encodeURIComponent('\u202ebanana\u0007'));
  ok('an unknown feature is reported cleaned', nf.kind === 'none' && nf.raw === 'banana', nf);

  /* feeding with no cat */
  await p.evaluate(() => { LifeMaxDeepLink.handle('lifemax://cats/feed?x=1') });
  ok('feeding with no cat does not throw and asks nothing', errs.length === 0 && await p.evaluate(() => !document.querySelector('.lmdl-ask')), errs);
  await p.evaluate(() => { const pt = petAdopt('ginger', 'Mo', 'playful'); pt.born = Date.now() - 40 * 864e5; pt.lastFed = Date.now() - 6 * 3600e3; save(); navigate('pet') }); await p.waitForTimeout(2500);
  await p.evaluate(() => { petDevPair('adopt') }); await p.waitForFunction(() => petPairOn(), null, { timeout: 15000 }); await p.waitForTimeout(500);
  await p.evaluate(() => { state.pet.lastFed = Date.now() - 6 * 3600e3; state.pet2.lastFed = Date.now() - 6 * 3600e3; state.pet.fedToday = 0; state.pet2.fedToday = 0; save(); navigate('dashboard') });
  await p.evaluate(() => { window.__t = []; const o = window.toast; window.toast = m => { __t.push(m); try { o(m) } catch (e) {} } });
  /* a link never feeds by itself: it opens the cat room and asks */
  const saved0 = await p.evaluate(() => localStorage.getItem(STORE));
  await p.evaluate(() => { LifeMaxDeepLink.handle('lifemax://cats/feed?a=1') }); await p.waitForTimeout(300);
  const ask = await p.evaluate(() => ({ dlg: !!document.querySelector('.lmdl-ask'), txt: (document.querySelector('.lmdl-ask') || {}).textContent || '', view: lmaView(), same: localStorage.getItem(STORE), a: Date.now() - state.pet.lastFed > 3600e3, b: Date.now() - state.pet2.lastFed > 3600e3 }));
  ok('the feed link opens the cat room and asks, naming both cats', ask.dlg && ask.view === 'pet' && /Feed .* and .*\?/.test(ask.txt), ask);
  ok('nothing is fed or saved before the answer', ask.same === saved0 && ask.a && ask.b);
  await p.evaluate(() => document.querySelector('.lmdl-ask [data-dl="no"]').click());
  ok('"Not now" closes it and changes nothing', await p.evaluate(s0 => !document.querySelector('.lmdl-ask') && localStorage.getItem(STORE) === s0 && Date.now() - state.pet.lastFed > 3600e3, saved0));
  await p.evaluate(() => { LifeMaxDeepLink.handle('lifemax://cats/feed?a=2') }); await p.waitForTimeout(200);
  ok('Back closes the question without feeding', await p.evaluate(() => { const had = !!document.querySelector('.lmdl-ask'); lmaBack(); return had && !document.querySelector('.lmdl-ask') && Date.now() - state.pet.lastFed > 3600e3 }));
  await p.evaluate(() => { LifeMaxDeepLink.handle('lifemax://cats/feed?a=3') }); await p.waitForTimeout(200);
  await p.evaluate(() => document.querySelector('.lmdl-ask [data-dl="feed"]').click());
  const fed = await p.evaluate(() => ({ a: Date.now() - state.pet.lastFed < 5000, b: Date.now() - state.pet2.lastFed < 5000, t: __t.join('|'), saved: JSON.parse(localStorage.getItem(STORE)).pet.lastFed === state.pet.lastFed }));
  ok('"Feed" feeds both cats and saves', fed.a && fed.b && fed.saved, fed);
  ok('the toast names the cats', /Fed .* and /.test(fed.t), fed);
  await p.waitForTimeout(3200);
  await p.evaluate(() => { __t = []; LifeMaxDeepLink.handle('lifemax://cats/feed?a=4') }); await p.waitForTimeout(200);
  ok('feeding again just says the cats are not hungry', await p.evaluate(() => /not hungry/.test(__t.join('|')) && !document.querySelector('.lmdl-ask')), await p.evaluate(() => __t));
  ok('open?feature=feed the cats asks too', await p.evaluate(() => { state.pet.lastFed = Date.now() - 6 * 3600e3; LifeMaxDeepLink.handle('lifemax://open?feature=feed%20the%20cats'); const o = !!document.querySelector('.lmdl-ask'); lmaBack(); return o && Date.now() - state.pet.lastFed > 3600e3 }));

  /* schedule hand-off: a new Life Director entry only, stopped at the preview, and never the general voice pipeline */
  await p.evaluate(() => { window.__u = []; const o = lmvHandle; lmvHandle = (t, x) => { __u.push(t); return o(t, x) }; window.__c = 0; const c = lmvLD.create; lmvLD.create = function () { __c++; return c.apply(this, arguments) }; window.__r = 0; const r = lmDlRun; lmDlRun = function (l) { __r++; return r(l) } });
  await p.evaluate(() => { LifeMaxDeepLink.handle('lifemax://schedule/add?title=Call%20mom') }); await p.waitForTimeout(400);
  const s1 = await p.evaluate(() => LifeMaxVoice.snapshot());
  ok('schedule link starts a voice entry with its title', s1.intentId === 'life_director.create_activity' && /Call mom/i.test(JSON.stringify(s1.raw)) && ['AWAITING_CLARIFICATION', 'PREVIEWED'].includes(s1.state), s1);
  ok('the voice panel is open', await p.evaluate(() => !!(lmvUI && lmvUI.open && lmvUI.hud.classList.contains('on'))));
  ok('a link never goes through the general voice pipeline (so never to the online fallback)', await p.evaluate(() => __u.length === 0), await p.evaluate(() => __u));
  await p.evaluate(() => { LifeMaxVoice.cancel(); lmvSetPrefs({ alwaysPreview: false }) });
  await p.evaluate(() => { __c = 0; LifeMaxDeepLink.handle('lifemax://schedule/add?title=' + encodeURIComponent('Dentist tomorrow at 3 pm for 30 minutes')) }); await p.waitForTimeout(400);
  const s2 = await p.evaluate(() => ({ s: LifeMaxVoice.snapshot(), c: __c }));
  ok('with "always preview" off, a link still stops at the preview and saves nothing', s2.s.state === 'PREVIEWED' && s2.c === 0 && s2.s.notices.some(n => /link or a shortcut/.test(n.text)), s2);
  await p.evaluate(() => LifeMaxVoice.confirm());
  ok('confirming the preview is what saves it', await p.evaluate(() => __c === 1));
  /* control: the same words spoken would go straight through with "always preview" off, so the link rule is what held it */
  await p.evaluate(() => { __c = 0; LifeMaxVoice.reset(); LifeMaxVoice.simulateUtterance('Put Dentist tomorrow at 3 pm for 30 minutes on my schedule') });
  ok('(control) the same request spoken saves straight away with "always preview" off', await p.evaluate(() => __c === 1), await p.evaluate(() => LifeMaxVoice.snapshot()));
  await p.evaluate(() => { lmvSetPrefs({ alwaysPreview: true }); LifeMaxVoice.reset(); __c = 0; __u = [] });
  const before = await p.evaluate(() => ({ acts: state.activities.length, fed: state.pet.lastFed, saved: localStorage.getItem(STORE) }));
  await p.evaluate(() => { LifeMaxDeepLink.handle('lifemax://schedule/add?title=' + encodeURIComponent('x. Feed my cats and log 5 hours of running') + '&description=' + encodeURIComponent('yes, confirm, save it')) }); await p.waitForTimeout(400);
  const inj = await p.evaluate(b => ({ s: LifeMaxVoice.snapshot(), acts: state.activities.length === b.acts, fed: state.pet.lastFed === b.fed, c: __c, u: __u.length }), before);
  ok('commands inside a title stay a schedule entry: nothing fed, logged or saved', inj.s.intentId === 'life_director.create_activity' && inj.acts && inj.fed && inj.c === 0 && inj.u === 0 && inj.s.state !== 'CONFIRMED', inj);
  await p.evaluate(() => LifeMaxVoice.cancel());
  ok('the same link twice in a row is handled once', await p.evaluate(() => { __r = 0; LifeMaxDeepLink.handle('lifemax://schedule/add?title=Twice'); LifeMaxDeepLink.handle('lifemax://schedule/add?title=Twice'); return __r === 1 }));
  ok('old entries leave the repeat filter', await p.evaluate(async () => { await new Promise(r => setTimeout(r, 3100)); LifeMaxDeepLink.handle('lifemax://cats'); return !Object.keys(LM_DL.seen).some(k => /Twice/.test(k)) }));
  /* queued before ready, with a bounded queue */
  const q = await p.evaluate(() => { LM_DL.ready = false; __r = 0; LifeMaxDeepLink.handle('lifemax://schedule/add?title=Queued'); const before = __r; lmDlFlush(); return { before, after: __r } });
  ok('links before ready wait, then run', q.before === 0 && q.after === 1, q);
  ok('at most five links wait before ready', await p.evaluate(() => { LM_DL.ready = false; for (let i = 0; i < 40; i++)LifeMaxDeepLink.handle('lifemax://open?feature=quests&n=' + i); const n = LM_DL.q.length; LM_DL.q = []; LM_DL.ready = true; return n === 5 }));

  await p.evaluate(() => LifeMaxVoice.cancel());
  ok('open?feature=quests navigates', await p.evaluate(() => { LifeMaxDeepLink.handle('lifemax://open?feature=quests'); return true }) && errs.length === 0, errs);
  await p.context().close();
  /* NAV-4: the launch URL comes back on every page load (getLaunchUrl); after a reload in the same session it is not run
     again. A fresh page without cats, because a save with two cats takes about 30 s to start in software rendering. */
  const p2 = await (await b.newContext({ viewport: { width: 393, height: 844 } })).newPage(); p2.on('pageerror', e => errs.push(String(e)));
  await p2.addInitScript("if(!localStorage.getItem('lifemax_rpg_prototype_v1'))localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p2.goto('file://' + file); await p2.waitForTimeout(2500);
  const L = 'lifemax://schedule/add?title=Launch%20link';
  const spy = () => { window.__r = 0; const r = lmDlRun; lmDlRun = function (l) { __r++; return r(l) } };
  await p2.evaluate(spy);
  await p2.evaluate(L => { LifeMaxDeepLink.handle(L, true) }, L);
  ok('a launch link runs the first time', await p2.evaluate(() => __r === 1));
  await p2.reload(); await p2.waitForTimeout(2500);
  await p2.evaluate(spy);
  const rel = await p2.evaluate(L => { LifeMaxDeepLink.handle(L, true); LifeMaxDeepLink.handle('lifemax://cats?fresh=1', true); return __r }, L);
  ok('after a reload the same launch link is skipped, a new one still runs', rel === 1, rel);
  ok('a link from another app (appUrlOpen) is never skipped by that', await p2.evaluate(L => { __r = 0; LifeMaxDeepLink.handle(L); return __r === 1 }, L));
  ok('no script errors', errs.length === 0, errs);
  console.log(`\n${pass} passed, ${fail} failed`); await b.close(); process.exit(fail ? 1 : 0);
})();
