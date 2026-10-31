/* Native Library (Media pane), web half: the projection a native screen draws from (checked against the page's own Media pane),
   the filter it is seeded with, when the screen covers the page and when it steps aside (the form, the audio player, a file
   button, a level-up), every delegated action with the screen coming back after it, and the page's toast carried to the
   screen. A fake NativeMedia plugin stands in for Android. Run: NODE_PATH=$(npm root -g) node tests/native-library.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const ITEMS = [
  { id: 'm_audio', title: 'Project Hail Mary', type: 'Audiobook', creator: 'Andy Weir', status: 'progress', link: 'https://example.com/hail-mary', notes: 'Chapter 12', audio: { name: 'hail-mary.m4b', size: 503316480, type: 'audio/mp4', position: 15000, duration: 58200 } },
  { id: 'm_done', title: 'Deep Work', type: 'Book', creator: 'Cal Newport', status: 'finished', link: '', notes: '', finishedAt: '2026-10-01T10:00:00.000Z' },
  { id: 'm_course', title: 'Learning How to Learn', type: 'Course', creator: 'Coursera', status: 'unread', link: 'javascript:alert(1)', notes: 'Weeks 1-4' },
  { id: 'm_pod', title: 'Huberman Lab', type: 'Podcast', creator: '', status: 'progress', link: 'http://example.org/pod', notes: '' },
  { id: 'm_new', title: 'Voice memos', type: 'Audiobook', creator: '', status: 'unread', link: '', notes: '', audio: { name: 'notes.m4a', size: 1048576, type: 'audio/mp4', position: 0, duration: 0 } },
  { id: 'm_old', title: 'Old import', creator: 'Someone', status: '', link: '', notes: 'no type, no status' },
];
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript(`
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,tipsSeen:{},mediaSeededV1:true,mediaItems:${JSON.stringify(ITEMS)}}));
    window.__calls=[];
    const mk=(name,caps)=>{const ls={};return {
      __emit:(e,d)=>Promise.all((ls[e]||[]).map(f=>f(d))),
      addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
      open:async a=>{window.__calls.push([name,'open',a])},
      update:async a=>{window.__calls.push([name,'update',a])},
      close:async a=>{window.__calls.push([name,'close',a]);(ls.closed||[]).forEach(f=>f({session:a&&a.session}))},
      capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true}),
      handshake:async()=>({protocol:1,capabilities:caps})}};
    window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>n==='NativeMedia'||n==='NativeOverview',getPlatform:()=>'android',
      Plugins:{NativeMedia:mk('media',['library.media.projection.v1']),NativeOverview:mk('overview',[])},registerPlugin:()=>({}),convertFileSrc:u=>u};
  `);
  await p.goto('file://' + file); await p.waitForTimeout(2500);
  const r = await p.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), P = window.Capacitor.Plugins.NativeMedia, out = {};
    const count = k => window.__calls.filter(c => c[0] === 'media' && c[1] === k).length;
    const last = k => [...window.__calls].reverse().find(c => c[0] === 'media' && c[1] === k);
    const showing = () => { const o = last('open'), c = last('close'); return !!o && (!c || window.__calls.lastIndexOf(o) > window.__calls.lastIndexOf(c)) };
    const latest = () => [...window.__calls].reverse().find(c => c[0] === 'media' && (c[1] === 'open' || c[1] === 'update'))[2].snapshot;
    const until = async (f, ms = 5000) => { for (let t = 0; t < ms && !f(); t += 50) await wait(50) };
    const text = el => el ? el.textContent.replace(/\s+/g, ' ').trim() : null;
    const act = async (e, ms = 300) => { await P.__emit('action', e); await wait(ms) };
    const back = async () => { await until(showing, 3000); return showing() };
    lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { }
    const realLevelUp = showLevelUp; showLevelUp = function () { };

    navigate('settings'); await wait(50);
    const toggle = document.getElementById('nativeLibraryToggle');
    out.settingOff = !!toggle && !toggle.checked && !toggle.disabled && /Native Library \(beta\)/.test(toggle.closest('.settingsline').textContent);
    navigate('media'); await wait(400);
    out.offStaysWeb = count('open') === 0;
    toggle.checked = true; toggle.dispatchEvent(new Event('change')); const S = lmaLoad(); S.nativeOverview = false; lmaSave();
    out.settingOn = lmaLoad().nativeLibrary === true;

    // the projection says what the page's own Media pane says
    navigate('dashboard'); navigate('media'); await until(() => count('open'));
    const s = last('open') && last('open')[2].snapshot, view = document.getElementById('view-media');
    out.opened = !!s && s.protocol === 1 && typeof s.webSessionId === 'string' && s.items.length === 6 && typeof s.tokens.accent === 'number';
    const rows = [...view.querySelectorAll('#mediaList .media-item')];
    const web = {
      panes: [...view.querySelectorAll('.libseg button')].map(text),
      header: [text(view.querySelector('.pagehead .eyebrow')), text(view.querySelector('.pagehead h2')), text(view.querySelector('.pagehead p')), text(view.querySelector('.pagehead .btn'))],
      tip: [text(view.querySelector('[data-feature-tip="media"] .tip-ico')), text(view.querySelector('[data-feature-tip="media"] strong')), text(view.querySelector('[data-feature-tip="media"] p')), view.querySelector('[data-feature-tip="media"] .tip-x').getAttribute('aria-label')],
      stats: [...view.querySelectorAll('.grid.stats .card')].map(c => [text(c.querySelector('.statlabel')), text(c.querySelector('.statvalue')), text(c.querySelector('.statfoot')) || '']),
      bar: document.getElementById('mediaBar').style.width,
      search: [text(view.querySelector('label[for=mediaSearch]')), document.getElementById('mediaSearch').placeholder],
      filters: [text(view.querySelector('label[for=mediaFilter]')), text(view.querySelector('label[for=mediaTypeFilter]'))],
      options: ['mediaFilter', 'mediaTypeFilter'].map(id => [...document.getElementById(id).options].map(o => o.value + '=' + o.text)),
      rows: rows.map(x => ({
        id: x.dataset.mediaId, title: text(x.querySelector('.media-title')), subtitle: text(x.querySelector('.media-sub')), status: text(x.querySelector('.media-pill')),
        finished: x.querySelector('.media-check').checked, notes: text(x.querySelector('.media-notes')) || '', link: x.querySelector('.media-pill a')?.getAttribute('href') || '',
        progress: x.querySelector('[data-audio-progress]') ? [text(x.querySelector('[data-audio-progress-label]')), text(x.querySelector('[data-audio-progress-time]')), x.querySelector('[data-audio-progress-fill]').style.width] : null,
        meta: text(x.querySelector('.media-audio-meta')) || '', buttons: [...x.querySelectorAll('button')].map(text),
      })),
    };
    const B = s.buttons;
    out.matchesPage = {
      panes: JSON.stringify(web.panes) === JSON.stringify(s.panes.map(x => x.label)) && s.panes[0].current && !s.panes[1].current && s.panes[1].id === 'history',
      header: JSON.stringify(web.header) === JSON.stringify([s.header.eyebrow, s.header.title, s.header.blurb, s.header.add]),
      tip: !!s.tip && JSON.stringify(web.tip) === JSON.stringify([s.tip.icon, s.tip.title, s.tip.body, s.tip.dismiss]),
      stats: JSON.stringify(web.stats) === JSON.stringify(s.stats.map(x => [x.label, x.value, x.foot])) && web.bar === s.stats[3].bar + '%' && s.stats.slice(0, 3).every(x => x.bar === null),
      search: JSON.stringify(web.search) === JSON.stringify([s.search.label, s.search.placeholder]),
      filters: JSON.stringify(web.filters) === JSON.stringify([s.statusFilter.label, s.typeFilter.label])
        && JSON.stringify(web.options) === JSON.stringify([s.statusFilter, s.typeFilter].map(f => f.options.map(o => o.id + '=' + o.label))),
      rows: JSON.stringify(web.rows.map(x => x.id)) === JSON.stringify(s.items.map(x => x.id)) && web.rows.every((w, i) => {
        const x = s.items[i];
        return w.title === x.title && w.subtitle === x.subtitle && w.status === x.statusLabel && w.finished === x.finished && w.notes === x.notes
          && (x.link ? w.link === x.link : true) && JSON.stringify(w.progress && w.progress.slice(0, 2)) === JSON.stringify(x.audio ? [x.audio.progress.label, x.audio.progress.time] : null)
          && (!x.audio || Math.abs(parseFloat(w.progress[2]) - x.audio.progress.pct) < 1e-3)
          && w.meta === (x.audio ? x.audio.meta : '') && JSON.stringify(w.buttons) === JSON.stringify([...(x.audio && x.audio.file ? [B.play, B.replace, B.removeAudio] : [B.attach]), B.edit, B.remove]);
      }),
    };
    out.matchesPageDetail = { web, items: s.items, stats: s.stats, tip: s.tip };
    const it = id => s.items.find(x => x.id === id);
    out.shape = it('m_audio').audio.file === 'hail-mary.m4b' && it('m_audio').audio.meta === 'hail-mary.m4b · 480.0 MB · resume 4:10:00 / 16:10:00' && it('m_audio').audio.progress.label === '26% listened'
      && it('m_new').audio.progress.label === 'Progress available after playback starts' && it('m_done').finished && it('m_done').audio === null
      && it('m_old').type === '' && it('m_old').subtitle === 'Other · Someone' && it('m_old').status === '' && it('m_old').statusLabel === 'Want to read / watch'
      && it('m_course').link === '' && it('m_pod').link === 'http://example.org/pod' && it('m_audio').link === 'https://example.com/hail-mary'
      && JSON.stringify(s.filter) === '{"search":"","status":"all","type":"all","revision":1}' && JSON.stringify(s.player) === '{"mediaId":"","visible":false,"playing":false}'
      && s.notice === null;
    out.unsafeLinkOnPage = web.rows.find(x => x.id === 'm_course').link === 'javascript:alert(1)'; // the page draws it; the projection does not

    // a redraw reaches the screen 100 ms later, once the bars are captured, which a busy machine can make slow: each check waits for its frame
    const kept = state.mediaItems; state.mediaItems = []; renderMedia(); await until(() => !latest().items.length);
    const emptyWeb = text(document.querySelector('#mediaList .empty')), E = latest().empty;
    out.empty = emptyWeb === E.icon + E.title + E.body + E.add && latest().items.length === 0 && latest().stats[0].value === '0';
    state.mediaItems = kept; document.getElementById('mediaSearch').value = 'zzz'; renderMedia(); await until(() => latest().filter.search === 'zzz');
    const none = text(document.querySelector('#mediaList .empty')), M = latest().noMatch;
    out.noMatch = none === M.icon + M.title + M.body;
    document.getElementById('mediaSearch').value = ''; renderMedia(); await until(() => latest().filter.search === '');

    // the page's filter: the screen takes it whenever the page sets it (a voice search, typing on the page), not on a plain redraw
    const rev = latest().filter.revision, seq = latest().sequence;
    renderMedia(); await until(() => latest().sequence > seq); out.redrawKeepsRevision = latest().filter.revision === rev;
    await LifeMaxVoice.simulateUtterance('Search my library for weir'); await until(() => latest().filter.search === 'weir');
    out.voiceSearch = latest().filter.search === 'weir' && latest().filter.revision === rev + 1 && lmaView() === 'media' && showing();
    out.voiceList = JSON.stringify(mediaMatching(state.mediaItems, latest().filter).map(x => x.id)) === '["m_audio"]';
    await LifeMaxVoice.simulateUtterance('Search my library for weir'); await until(() => latest().filter.revision > rev + 1);
    out.voiceAgain = latest().filter.revision === rev + 2; // the same words again still reach a screen whose own search has moved on
    const sel = document.getElementById('mediaFilter'); sel.value = 'progress'; sel.dispatchEvent(new Event('input', { bubbles: true })); sel.dispatchEvent(new Event('change', { bubbles: true })); await until(() => latest().filter.status === 'progress');
    out.pageSelect = latest().filter.status === 'progress' && latest().filter.revision === rev + 3;
    document.getElementById('mediaSearch').value = ''; sel.value = 'all'; renderMedia(); await wait(250);

    // add: the screen closes for the page's form and comes back when the form closes
    let closes = count('close'), opens = count('open');
    await act({ action: 'add' });
    out.add = count('close') === closes + 1 && document.getElementById('mediaFormCard').style.display === 'block' && document.getElementById('mediaFormTitle').textContent === 'Add to your library' && !showing();
    renderMedia(); await wait(300); out.formHolds = !showing() && count('open') === opens;
    hideMediaForm(); out.addBack = await back() && count('open') === opens + 1;

    // edit: the page's form filled with the item; saving brings the screen back with the change
    closes = count('close'); opens = count('open');
    await act({ action: 'edit', id: 'm_pod' });
    out.edit = count('close') === closes + 1 && document.getElementById('mediaEditId').value === 'm_pod' && document.getElementById('mediaTitle').value === 'Huberman Lab' && !showing();
    document.getElementById('mediaCreator').value = 'Andrew Huberman'; document.querySelector('#mediaForm button[type=submit]').click();
    out.editBack = await back() && latest().items.find(x => x.id === 'm_pod').subtitle === 'Podcast · Andrew Huberman';

    // finished and back: the page's checkbox handler, then the screen again
    closes = count('close'); opens = count('open');
    await act({ action: 'finished', id: 'm_pod' });
    out.finished = count('close') === closes + 1 && mediaById('m_pod').status === 'finished' && await back() && latest().items.find(x => x.id === 'm_pod').finished && latest().stats[1].value === '2';
    const done = latest().notice;
    await act({ action: 'unfinished', id: 'm_pod' });
    out.unfinished = mediaById('m_pod').status === 'progress' && await back() && latest().items.find(x => x.id === 'm_pod').statusLabel === 'In progress';
    // the page's toast is under the screen, so it comes with the projection, numbered so that the same words twice are two
    const undone = latest().notice;
    await act({ action: 'finished', id: 'm_pod' }); await back(); const again = latest().notice;
    await act({ action: 'unfinished', id: 'm_pod' }); await back();
    out.notices = { done, undone, again };
    out.notice = done?.text === 'Marked as finished' && undone?.text === 'Moved back to in progress' && again?.text === done.text && again.id > undone.id && undone.id > done.id;
    // a delegated write that is not stored says so on the screen, and nothing changes
    const realSet = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return realSet.apply(this, arguments) };
    try { await act({ action: 'finished', id: 'm_pod' }); await back() } finally { Storage.prototype.setItem = realSet }
    out.unsaved = latest().notice?.text === 'Could not save to this browser (storage full?)' && !latest().items.find(x => x.id === 'm_pod').finished && mediaById('m_pod').status === 'progress';
    toast('Badge unlocked: Bookworm'); await until(() => latest().notice?.text === 'Badge unlocked: Bookworm');
    out.liveNotice = showing() && latest().notice?.text === 'Badge unlocked: Bookworm';

    // remove asks on the page
    const realConfirm = window.confirm; window.confirm = () => false;
    await act({ action: 'remove', id: 'm_old' }); out.removeKept = !!mediaById('m_old') && await back();
    window.confirm = () => true;
    await act({ action: 'remove', id: 'm_old' }); out.removed = !mediaById('m_old') && await back() && !latest().items.some(x => x.id === 'm_old');

    const wav = (() => { const n = 8000, v = new DataView(new ArrayBuffer(44 + n)), w = (o, s) => [...s].forEach((c, i) => v.setUint8(o + i, c.charCodeAt(0)));
      w(0, 'RIFF'); v.setUint32(4, 36 + n, true); w(8, 'WAVEfmt '); v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true); v.setUint32(24, 8000, true); v.setUint32(28, 8000, true); v.setUint16(32, 1, true); v.setUint16(34, 8, true); w(36, 'data'); v.setUint32(40, n, true);
      return new Blob([v.buffer], { type: 'audio/wav' }) })();
    // a file is chosen only by a tap on the page: the row's button is shown and focused, and the screen waits until it is left
    document.getElementById('mediaFilter').value = 'finished'; renderMedia(); await wait(250); const revHidden = latest().filter.revision;
    closes = count('close'); opens = count('open');
    await act({ action: 'attach', id: 'm_course' });
    const fileButton = document.activeElement;
    out.attach = count('close') === closes + 1 && fileButton.matches('[data-media-id="m_course"] .media-audio-row button') && text(fileButton) === '🎧 Attach .m4b / audio' && !showing()
      && document.getElementById('mediaFilter').value === 'all';
    out.attachHint = document.getElementById('toast').textContent === 'Tap "🎧 Attach .m4b / audio" to choose the file' && document.getElementById('toast').classList.contains('show');
    fileButton.blur(); out.attachBack = await back() && latest().filter.revision === revHidden; // the page's filter was cleared quietly
    out.attachHintStays = latest().notice === null; // it points at the page's button, which the screen covers
    // the owner taps the button and picks a file: the page stores it, redraws the list and plays it, and the screen stays away
    // all the while (the redraw takes the focus, then the player is loading), until the player is closed
    await act({ action: 'attach', id: 'm_course' }); opens = count('open');
    await attachAudioFile('m_course', new File([wav], 'lectures.wav', { type: 'audio/wav' })); await wait(600);
    out.attachPlays = count('open') === opens && !showing() && audioCtx.mediaId === 'm_course' && mediaById('m_course').audio.name === 'lectures.wav';
    closeAudioPlayer(); out.attachPlayedBack = await back() && latest().items.find(x => x.id === 'm_course').audio.file === 'lectures.wav';
    await act({ action: 'replace', id: 'm_audio' });
    out.replace = text(document.activeElement) === 'Replace file' && !!document.activeElement.closest('[data-media-id="m_audio"]') && !showing();
    document.activeElement.blur(); out.replaceBack = await back();

    // removing the audio asks on the page
    await act({ action: 'removeAudio', id: 'm_new' }, 500);
    out.removeAudio = !mediaById('m_new').audio && await back() && latest().items.find(x => x.id === 'm_new').audio === null;
    window.confirm = realConfirm;

    // play: the player belongs to the page, so the pane stays on the page while it is loaded or showing
    await audioDbPut('m_audio', { blob: wav, name: 'hail-mary.m4b', type: 'audio/wav', size: wav.size, updatedAt: new Date().toISOString() });
    closes = count('close'); opens = count('open');
    await act({ action: 'play', id: 'm_audio' }, 800);
    out.play = count('close') === closes + 1 && audioCtx.mediaId === 'm_audio' && document.getElementById('audioPlayer').classList.contains('show') && !showing() && count('open') === opens;
    navigate('dashboard'); navigate('media'); await wait(500); out.playerHolds = !showing() && count('open') === opens;
    closeAudioPlayer(); out.playerBack = await back() && JSON.stringify(latest().player) === '{"mediaId":"","visible":false,"playing":false}';
    // with a book loaded the pane is on the page; Play on a row whose file is missing closes the player, and the page says so and
    // saves, which redraws the list: the screen comes back with the page's message
    await act({ action: 'play', id: 'm_audio' }, 800);
    mediaById('m_pod').audio = { name: 'lost.m4b', size: 1024, type: 'audio/mp4', position: 0, duration: 0 }; renderMedia(); await wait(300);
    out.missingHolds = !showing() && audioCtx.mediaId === 'm_audio';
    document.querySelector('[data-media-id="m_pod"] .media-audio-row button').click();
    out.missing = await back() && latest().notice?.text === 'Audio file missing — attach it again' && latest().items.find(x => x.id === 'm_pod').audio === null && !audioCtx.mediaId;

    // the link opens as the row's anchor would; a link that is not http(s) never reaches the screen
    const opened = []; const realClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function () { opened.push([this.href, this.target, this.rel]) };
    await act({ action: 'link', id: 'm_audio' }); await act({ action: 'link', id: 'm_course' });
    HTMLAnchorElement.prototype.click = realClick;
    out.link = JSON.stringify(opened) === '[["https://example.com/hail-mary","_blank","noopener noreferrer"]]' && await back();

    // the tip is dismissed on the page and leaves the projection
    await act({ action: 'tip' });
    out.tip = state.tipsSeen.media === true && !document.querySelector('[data-feature-tip="media"]') && await back() && latest().tip === null;

    // an item the page no longer has does nothing; the switch's Journal is a web destination
    opens = count('open'); await act({ action: 'edit', id: 'gone' }); out.gone = document.getElementById('mediaFormCard').style.display === 'none' && await back();
    closes = count('close'); opens = count('open');
    await act({ action: '', view: 'history' }, 500);
    out.journal = lmaView() === 'history' && count('close') === closes + 1 && !showing() && count('open') === opens;
    navigate('media'); out.backFromJournal = await back();

    // a level-up plays on the page, so the screen steps aside at once and comes back when it closes
    closes = count('close'); opens = count('open');
    realLevelUp({ charLevel: null, from: 1, attrs: [{ id: 'intelligence', short: 'Intellect', from: 1, level: 2 }] }); await wait(150);
    out.levelUp = count('close') === closes + 1;
    await wait(800); document.querySelector('.luov').click(); // it takes a tap only after 0.7 s
    out.levelUpBack = await back() && !document.querySelector('.luov') && count('open') === opens + 1;

    // leaving for a web tab closes the screen; the toggle off closes it and keeps it closed
    navigate('dashboard'); await wait(200); out.leave = !showing();
    navigate('media'); await back(); await setNativeLibraryEnabled(false); await wait(100);
    opens = count('open'); out.off = !showing() && lmaLoad().nativeLibrary === false; navigate('dashboard'); navigate('media'); await wait(400); out.offStays = count('open') === opens;
    showLevelUp = realLevelUp;
    return out;
  });
  ok('Settings shows "Native Library (beta)", off by default; off, the pane stays on the page', r.settingOff && r.offStaysWeb, r);
  ok('the switch turns it on', r.settingOn, r);
  ok('opening sends the projection with the page\'s palette', r.opened, r);
  for (const [k, v] of Object.entries(r.matchesPage || {})) ok('the projection words it as the page does: ' + k, v, r.matchesPageDetail);
  ok('rows carry the stored type and status for the filter, the audio texts, and only http(s) links', r.shape && r.unsafeLinkOnPage, r.matchesPageDetail && r.matchesPageDetail.items);
  ok('an empty library and no matches carry the page\'s words', r.empty && r.noMatch, r);
  ok('a plain redraw keeps the filter revision', r.redrawKeepsRevision, r);
  ok('a voice search reaches the screen: the page\'s search with a new revision, the same list as the page', r.voiceSearch && r.voiceList, r);
  ok('the same voice search again raises the revision again', r.voiceAgain, r);
  ok('a filter set on the page is adopted too', r.pageSelect, r);
  ok('add: the screen closes for the page\'s form, stays away while it is open, and comes back when it closes', r.add && r.formHolds && r.addBack, r);
  ok('edit: the page\'s form with the item; saving brings the screen back with the change', r.edit && r.editBack, r);
  ok('finished and unfinished run the page\'s checkbox handler and come back', r.finished && r.unfinished, r);
  ok('the page\'s toast comes with the projection, a new number for each, the same words twice included', r.notice, r.notices);
  ok('a delegated write that is not stored shows the page\'s message and changes nothing', r.unsaved, r);
  ok('a toast while the screen shows is sent at once', r.liveNotice, r);
  ok('remove asks on the page: cancelled keeps the item, confirmed removes it; back both times', r.removeKept && r.removed, r);
  ok('attach: the row\'s own button, shown (a hiding page filter cleared quietly) and focused; back when it is left', r.attach && r.attachBack, r);
  ok('the page names the button to tap in a toast, which the screen does not repeat', r.attachHint && r.attachHintStays, r);
  ok('a file picked there is stored and played by the page with the screen away throughout, and the screen comes back after', r.attachPlays && r.attachPlayedBack, r);
  ok('replace works the same way', r.replace && r.replaceBack, r);
  ok('remove audio asks on the page and comes back', r.removeAudio, r);
  ok('play: the page\'s player; the pane stays on the page while it is loaded, and comes back when it closes', r.play && r.playerHolds && r.playerBack, r);
  ok('a play started on the page whose file is missing brings the screen back with the page\'s message', r.missingHolds && r.missing, r);
  ok('link: opened as the row\'s anchor does, never a link that is not http(s)', r.link, r);
  ok('the tip is dismissed on the page and leaves the projection', r.tip, r);
  ok('an action on an item that is gone does nothing and comes back', r.gone, r);
  ok('Journal on the switch goes to the page\'s Journal; Media brings the screen back', r.journal && r.backFromJournal, r);
  ok('a level-up overlay makes the screen step aside at once and it comes back after', r.levelUp && r.levelUpBack, r);
  ok('a web tab closes the screen; switched off it closes and stays closed', r.leave && r.off && r.offStays, r);
  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
