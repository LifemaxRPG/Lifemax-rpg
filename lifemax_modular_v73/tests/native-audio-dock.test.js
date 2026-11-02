/* Native audio dock (phase 4), web half: the state the page pushes to the shell's dock (checked against the page's own player), the
   commands the dock may send and what they run, the dock event that hides the page's player and pads its body, native Media and the
   Journal no longer stepping aside for a loaded book once the dock carries it, and Media's `play` and `attach` commands (the file
   the native picker chose is fetched and attached by the page; the reply does not wait for it). A fake NativeAudio, NativeMedia and
   NativeJournal stand in for Android. Run: NODE_PATH=$(npm root -g) node tests/native-audio-dock.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const ITEMS = [
  { id: 'm_book', title: 'Project Hail Mary', type: 'Audiobook', creator: 'Andy Weir', status: 'progress', link: '', notes: '', audio: { name: 'hail-mary.m4b', size: 160044, type: 'audio/wav', position: 0, duration: 10 } },
  { id: 'm_other', title: 'Deep Work', type: 'Audiobook', creator: 'Cal Newport', status: 'unread', link: '', notes: '', audio: { name: 'deep-work.m4b', size: 160044, type: 'audio/wav', position: 0, duration: 10 } },
  { id: 'm_new', title: 'Voice memos', type: 'Audiobook', creator: '', status: 'unread', link: '', notes: '' },
];
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--autoplay-policy=no-user-gesture-required'] });
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript(`
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,tipsSeen:{},mediaSeededV1:true,mediaItems:${JSON.stringify(ITEMS)}}));
    window.__calls=[]; window.__audio=[]; window.__configure=[];
    const mk=(name,caps,extra)=>{const ls={};return Object.assign({
      __emit:(e,d)=>Promise.all((ls[e]||[]).map(f=>f(d))),
      __listeners:ls,
      addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
      open:async a=>{window.__calls.push([name,'open',a])},
      update:async a=>{window.__calls.push([name,'update',a])},
      close:async a=>{window.__calls.push([name,'close',a]);(ls.closed||[]).forEach(f=>f({session:a&&a.session}))},
      capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true}),
      handshake:async()=>({protocol:1,capabilities:caps})},extra||{})};
    const audio=mk('audio',['shell.audio.v1'],{state:async s=>{window.__audio.push(s)},configure:async c=>{window.__configure.push(c)}});
    window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>['NativeMedia','NativeJournal','NativeOverview','NativeAudio'].includes(n),getPlatform:()=>'android',
      Plugins:{NativeMedia:mk('media',['library.media.projection.v1']),NativeJournal:mk('journal',['library.journal.projection.v1']),NativeOverview:mk('overview',[]),NativeAudio:audio},registerPlugin:()=>({}),convertFileSrc:u=>u};
  `);
  await p.goto('file://' + file); await p.waitForTimeout(2500);
  const r = await p.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), A = window.Capacitor.Plugins.NativeAudio, M = window.Capacitor.Plugins.NativeMedia, out = {};
    const until = async (f, ms = 5000) => { for (let t = 0; t < ms && !f(); t += 25) await wait(25) };
    const lastState = () => window.__audio[window.__audio.length - 1];
    const mcount = k => window.__calls.filter(c => c[0] === 'media' && c[1] === k).length;
    const mlatest = () => [...window.__calls].reverse().find(c => c[0] === 'media' && (c[1] === 'open' || c[1] === 'update'))[2];
    const mshowing = () => { const o = window.__calls.map((c, i) => [c, i]).filter(([c]) => c[0] === 'media' && c[1] === 'open').pop(), c = window.__calls.map((c, i) => [c, i]).filter(([c]) => c[0] === 'media' && c[1] === 'close').pop(); return !!o && (!c || o[1] > c[1]) };
    const cmd = (command, payload) => A.__emit('command', { command, payload: payload || {} });
    lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { }
    // a minute of silence as a WAV: the page's real <audio> element plays and seeks it
    const wav = () => { const n = 8000 * 60, buf = new ArrayBuffer(44 + n), v = new DataView(buf); const w = (o, s) => [...s].forEach((c, i) => v.setUint8(o + i, c.charCodeAt(0)));
      w(0, 'RIFF'); v.setUint32(4, 36 + n, true); w(8, 'WAVEfmt '); v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true); v.setUint32(24, 8000, true); v.setUint32(28, 8000, true); v.setUint16(32, 1, true); v.setUint16(34, 8, true);
      w(36, 'data'); v.setUint32(40, n, true); new Uint8Array(buf, 44).fill(128); return new Blob([buf], { type: 'audio/wav' }) };
    for (const id of ['m_book', 'm_other']) await audioDbPut(id, { blob: wav(), name: id + '.m4b', type: 'audio/wav', size: 480044, updatedAt: '2026-01-01' });

    // off by default, "(beta)", and nothing is pushed while it is off
    navigate('settings'); await wait(50);
    const toggle = document.getElementById('nativeAudioToggle');
    out.settingOff = !!toggle && !toggle.checked && !toggle.disabled && /Native audiobook controls \(beta\)/.test(toggle.closest('.settingsline').textContent);
    out.notReady = !window.NativeAudioDock.ready();
    await playMediaAudio('m_book'); await wait(300);
    out.silentWhileOff = window.__audio.length === 0 && window.__configure.length === 0;
    closeAudioPlayer(true); await wait(100);

    // the switch: the native side is asked, the page's accept list goes with it
    toggle.checked = true; toggle.dispatchEvent(new Event('change')); await until(() => window.__configure.length);
    out.settingOn = lmaLoad().nativeAudio === true && window.NativeAudioDock.ready();
    out.configured = window.__configure.length === 1 && window.__configure[0].enabled === true && window.__configure[0].accept === document.getElementById('audioFileInput').accept
      && /\.m4b/.test(window.__configure[0].accept) && /audio file/.test(window.__configure[0].refuse);

    // state: what the page's player shows
    await playMediaAudio('m_book'); await until(() => lastState() && lastState().playing);
    const s = lastState(), el = document.getElementById('audioEl');
    out.state = s.v === 1 && s.visible && !s.minimized && !s.loading && s.mediaId === 'm_book' && s.title === 'Project Hail Mary'
      && s.sub === document.getElementById('audioPlayerSub').textContent && /hail-mary\.m4b/.test(s.sub) && s.playing && Math.abs(s.duration - 60) < 0.5 && s.rate === 1
      && JSON.stringify(s.rates) === '[0.8,1,1.25,1.5,1.75,2]' && typeof s.tokens.accent === 'number' && s.tokens.bg > 0 && s.light === false;
    out.stateDetail = s;

    // commands run the page's own functions
    await cmd('toggle'); await until(() => lastState().playing === false);
    out.pause = el.paused && lastState().playing === false;
    await cmd('toggle'); await until(() => lastState().playing); out.resume = !el.paused;
    await cmd('seek', { frac: 0.5 }); await wait(200);
    out.seek = Math.abs(el.currentTime - 30) < 0.5 && Math.abs(lastState().position - el.currentTime) < 0.4;
    const at = el.currentTime; await cmd('skip', { sec: -15 }); await wait(100); out.skipBack = Math.abs(el.currentTime - (at - 15)) < 1.5 && at > 20;
    await cmd('skip', { sec: 30 }); await wait(100); out.skipForward = Math.abs(el.currentTime - (at + 15)) < 1.5;
    await cmd('seek', { frac: 0.1 }); await cmd('skip', { sec: 99999 }); await wait(50); out.hugeSkipIgnored = el.currentTime < 8;
    await cmd('seek', { frac: 7 }); await wait(100); out.seekClamped = el.currentTime <= el.duration + 0.01;
    await cmd('seek', { frac: 0.1 }); await wait(100);
    if (el.paused) await cmd('toggle');
    await cmd('rate', { value: 1.5 }); await until(() => lastState().rate === 1.5);
    out.rate = el.playbackRate === 1.5 && document.getElementById('audioRate').value === '1.5' && lastState().rate === 1.5;
    await cmd('rate', { value: 3 }); await wait(100); out.badRateIgnored = el.playbackRate === 1.5;
    await cmd('nonsense', {}); await cmd('toggle', null); await wait(50);
    await cmd('toggle'); await until(() => !lastState().playing); await cmd('toggle'); await until(() => lastState().playing);
    await cmd('minimize'); await until(() => lastState().minimized); out.minimize = lastState().minimized && document.getElementById('audioPlayer').classList.contains('minimized') && lastState().visible;
    await cmd('minimize'); await until(() => !lastState().minimized); out.expand = !lastState().minimized;

    // loading: swapping the book keeps the dock up and says so
    const real = audioDbGet; let release; const slow = new Promise(res => { release = res });
    audioDbGet = async id => { await slow; return real(id) };
    const swapping = playMediaAudio('m_other'); await until(() => lastState().loading);
    out.loading = lastState().loading && lastState().visible && lastState().sub === 'Loading…' && lastState().title === 'Deep Work' && lastState().mediaId === 'm_other' && !lastState().playing;
    release(); await swapping; audioDbGet = real; await until(() => !lastState().loading && lastState().playing);
    out.swapped = lastState().title === 'Deep Work' && lastState().mediaId === 'm_other' && lastState().visible;

    // the position persists and the book's pushes continue without a push per second
    const n0 = window.__audio.length; await wait(1500); out.noPushPerSecond = window.__audio.length - n0 <= 1;
    const n1 = window.__audio.length; await until(() => window.__audio.length > n1, 12000); out.driftDbg = [window.__audio.length - n1, lastState().playing, lastState().position]; out.driftPush = window.__audio.length > n1 && lastState().playing && lastState().position > 5;

    // the dock event: the page's player gives way and the body clears the dock
    const root = document.documentElement, player = document.getElementById('audioPlayer');
    out.pageHasPlayer = getComputedStyle(player).display !== 'none';
    const padBefore = parseFloat(getComputedStyle(document.querySelector('.app')).paddingBottom);
    await A.__emit('dock', { shown: true, height: 152 }); await wait(50);
    out.docked = root.classList.contains('audio-docked') && getComputedStyle(player).display === 'none'
      && root.style.getPropertyValue('--audio-dock-h') === '152px' && Math.abs(parseFloat(getComputedStyle(document.querySelector('.app')).paddingBottom) - padBefore - 152) < 1;
    await A.__emit('dock', { shown: false, height: 0 }); await wait(50);
    out.undocked = !root.classList.contains('audio-docked') && getComputedStyle(player).display !== 'none' && root.style.getPropertyValue('--audio-dock-h') === '0px';

    // native Media stays open for a loaded book, because the dock carries it; without the dock it steps aside as before
    const settings = lmaLoad(); settings.nativeOverview = false; settings.nativeLibrary = true; lmaSave();
    navigate('dashboard'); navigate('media'); await until(() => mcount('open'));
    out.mediaOpenWithBook = mcount('open') >= 1 && mshowing() && audioBusy();
    settings.nativeJournal = true; lmaSave();
    out.bothWouldOpen = NativeTabs.wouldOpen('history') && NativeTabs.wouldOpen('media');
    out.playerFieldStillThere = mlatest().snapshot.player.visible === true && mlatest().snapshot.player.mediaId === 'm_other';

    // Media's play command: the screen stays open and the book starts in the dock
    closeAudioPlayer(true); await until(() => !lastState().visible);
    out.closed = lastState().visible === false && !audioBusy();
    const closes = mcount('close'), updates = mcount('update');
    await M.__emit('command', { command: 'play', payload: { id: 'm_book' }, requestId: '' });
    await until(() => lastState().visible && lastState().playing);
    const reply = [...window.__calls].reverse().find(c => c[0] === 'media' && c[1] === 'update' && c[2].reply);
    out.playCommand = lastState().mediaId === 'm_book' && mcount('close') === closes && mshowing() && !!reply && reply[2].reply.command === 'play' && reply[2].reply.ok === true;
    await M.__emit('command', { command: 'play', payload: { id: 'm_gone' }, requestId: 'r2' }); await wait(300);
    const bad = [...window.__calls].reverse().find(c => c[0] === 'media' && c[1] === 'update' && c[2].reply && c[2].reply.requestId === 'r2');
    out.playGone = !!bad && bad[2].reply.ok === false;

    // attach: the picked document's address is fetched by the page and attached by its own owner; the reply does not wait
    const item = () => mediaById('m_new'), blobUrl = URL.createObjectURL(wav());
    const realFetch = window.fetch; let finish; const gate = new Promise(res => { finish = res });
    window.fetch = async (u, o) => { await gate; return realFetch(u, o) };
    const t0 = performance.now();
    await M.__emit('command', { command: 'attach', payload: { id: 'm_new', url: blobUrl, name: 'Voice memos.m4b', type: 'audio/mp4', size: 80044 }, requestId: 'a1' });
    await until(() => window.__calls.some(c => c[0] === 'media' && c[1] === 'update' && c[2].reply && c[2].reply.requestId === 'a1'));
    const a1 = window.__calls.find(c => c[0] === 'media' && c[1] === 'update' && c[2].reply && c[2].reply.requestId === 'a1')[2].reply;
    out.attachReplyAtOnce = a1.ok === true && a1.started === true && !item().audio && performance.now() - t0 < 1500 && /Reading the file/.test(document.getElementById('toast').textContent);
    // a command sent while the file is being read is answered (the queue is not held)
    await M.__emit('command', { command: 'play', payload: { id: 'm_other' }, requestId: 'a2' }); await until(() => window.__calls.some(c => c[0] === 'media' && c[1] === 'update' && c[2].reply && c[2].reply.requestId === 'a2'));
    out.queueFree = true;
    await M.__emit('command', { command: 'attach', payload: { id: 'm_book', url: blobUrl, name: 'x.m4b', type: 'audio/mp4', size: 1 }, requestId: 'a3' });
    await until(() => window.__calls.some(c => c[0] === 'media' && c[1] === 'update' && c[2].reply && c[2].reply.requestId === 'a3'));
    const a3 = window.__calls.find(c => c[0] === 'media' && c[1] === 'update' && c[2].reply && c[2].reply.requestId === 'a3')[2].reply;
    out.oneAtATime = a3.ok === false && /Another file/.test(a3.message);
    finish(); await until(() => item().audio && item().audio.name === 'Voice memos.m4b', 8000);
    out.attached = !!item().audio && item().audio.name === 'Voice memos.m4b' && item().audio.type === 'audio/mp4' && item().audio.size > 0 && item().status === 'progress';
    const rec = await audioDbGet('m_new'); out.stored = !!rec && rec.name === 'Voice memos.m4b' && rec.blob.size > 400000;
    await until(() => lastState().mediaId === 'm_new' && lastState().playing, 5000);
    out.playsInDock = lastState().mediaId === 'm_new' && lastState().title === 'Voice memos' && /Voice memos\.m4b/.test(lastState().sub);
    window.fetch = realFetch;

    // too big: refused before any fetch, in the page's words; a failed read is reported and the next attach works
    let fetched = 0; window.fetch = async (u, o) => { fetched++; return realFetch(u, o) };
    const big = await (async () => { await M.__emit('command', { command: 'attach', payload: { id: 'm_other', url: blobUrl, name: 'big.m4b', type: 'audio/mp4', size: AUDIO_MAX_BYTES + 1 }, requestId: 'b1' }); await wait(200);
      return window.__calls.find(c => c[0] === 'media' && c[1] === 'update' && c[2].reply && c[2].reply.requestId === 'b1')[2].reply })();
    out.tooBig = big.ok === false && /too large/.test(document.getElementById('toast').textContent) && fetched === 0;
    await M.__emit('command', { command: 'attach', payload: { id: 'm_other', url: 'blob:http://nowhere/none', name: 'bad.m4b', type: 'audio/mp4', size: 5 }, requestId: 'b2' });
    await until(() => /Could not read that file/.test(document.getElementById('toast').textContent), 4000);
    out.readFails = /Could not read that file/.test(document.getElementById('toast').textContent) && mediaById('m_other').audio.name === 'deep-work.m4b';
    await M.__emit('command', { command: 'attach', payload: { id: 'm_other', url: blobUrl, name: 'again.m4b', type: 'audio/mp4', size: 80044 }, requestId: 'b3' });
    await until(() => mediaById('m_other').audio && mediaById('m_other').audio.name === 'again.m4b', 6000);
    out.retryWorks = mediaById('m_other').audio.name === 'again.m4b';
    await M.__emit('command', { command: 'attach', payload: { id: 'm_missing', url: blobUrl, name: 'z.m4b', type: '', size: 5 }, requestId: 'b4' }); await wait(200);
    out.goneItem = window.__calls.find(c => c[0] === 'media' && c[1] === 'update' && c[2].reply && c[2].reply.requestId === 'b4')[2].reply.ok === false;
    await M.__emit('command', { command: 'attachRefused', payload: { name: 'notes.pdf' }, requestId: 'b5' }); await wait(200);
    out.refused = /That is not an audio file: notes\.pdf/.test(document.getElementById('toast').textContent);
    window.fetch = realFetch;
    out.screenStayedOpen = mshowing();

    // switching the dock off: the native side is told, the page's player is its own again, commands are ignored
    toggle.checked = false; toggle.dispatchEvent(new Event('change')); await until(() => window.__configure.length >= 2);
    const lastConfigure = window.__configure[window.__configure.length - 1];
    out.off = lmaLoad().nativeAudio === false && lastConfigure.enabled === false && !window.NativeAudioDock.ready() && !root.classList.contains('audio-docked');
    const pausedBefore = document.getElementById('audioEl').paused; await cmd('close'); await wait(100);
    out.ignoredWhenOff = pausedBefore === false ? !!document.getElementById('audioEl').src : true;
    out.busyAgain = audioBusy() && !NativeTabs.wouldOpen('history') && !NativeTabs.wouldOpen('media');
    return out;
  });
  const O = r;
  ok('off by default with "(beta)", and not ready', O.settingOff && O.notReady, O);
  ok('while off nothing is configured or pushed', O.silentWhileOff, O);
  ok('the switch turns it on: the native side gets the enabled flag and the page\'s accept list', O.settingOn && O.configured, O);
  ok('the state is what the page\'s player shows (title, file and size, playing, length, speeds, palette)', O.state, O.stateDetail);
  ok('toggle pauses and resumes the page\'s element', O.pause && O.resume, O);
  ok('seek moves the element and the next push says so', O.seek, O);
  ok('skip back and forward run audioSkip', O.skipBack && O.skipForward, O);
  ok('an absurd skip is ignored and a seek is clamped to 0..1', O.hugeSkipIgnored && O.seekClamped, O);
  ok('rate sets the page\'s select and element; a speed it does not offer is ignored', O.rate && O.badRateIgnored, O);
  ok('minimize and expand follow the page\'s player', O.minimize && O.expand, O);
  ok('swapping the book keeps the dock up, says "Loading…", then shows the new book', O.loading && O.swapped, O);
  ok('a playing book pushes on events, not every second', O.noPushPerSecond, O);
  ok('and a drift correction comes while it plays', O.driftPush, O.driftDbg);
  ok('the dock event hides the page\'s player and pads the body by its height; gone it gives both back', O.pageHasPlayer && O.docked && O.undocked, O);
  ok('native Media stays open for a loaded book once the dock carries it', O.mediaOpenWithBook && O.playerFieldStillThere, O);
  ok('the Journal pane and Media would both open over a loaded book with the dock on', O.bothWouldOpen, O);
  ok('closing the player empties the dock', O.closed, O);
  ok('Media\'s play command starts the book in the dock with the screen open, and answers', O.playCommand, O);
  ok('play for an item that is gone answers {ok:false}', O.playGone, O);
  ok('attach replies at once ({started:true}) while the page reads the file', O.attachReplyAtOnce, O);
  ok('a command sent while a file is being read is answered', O.queueFree, O);
  ok('a second attach while one is being read is refused', O.oneAtATime, O);
  ok('the picked file is fetched, stored by the page\'s own owner and named on the item', O.attached && O.stored, O);
  ok('the attached book starts in the dock', O.playsInDock, O);
  ok('a file over the page\'s limit is refused before any fetch, in the page\'s words', O.tooBig, O);
  ok('a failed read is reported and the next attach works', O.readFails && O.retryWorks, O);
  ok('attach for an item that is gone is refused; a refused file is named in the page\'s words', O.goneItem && O.refused, O);
  ok('the screen stayed open through all of it', O.screenStayedOpen, O);
  ok('switching the dock off tells the native side and gives the page its player back', O.off && O.busyAgain, O);
  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
