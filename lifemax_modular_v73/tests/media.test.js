/* Media pane (Library) tests: the reading list seeded once, adding and editing through the form (mediaUpsert underneath), finishing
   and unfinishing, removing, the search and the two filters, a save that fails for each writer (attaching and replacing a file
   included), the owners a native screen calls (mediaSetStatus, mediaRemove, mediaRemoveAudio, and mediaUpsert's operation id), a
   stored type the form does not offer, audioBusy(), and the audio progress words.
   Run:  NODE_PATH=$(npm root -g) node tests/media.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 700) : '')) } };
(async () => {
  const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 412, height: 860 } }); const errs = [];
  const open = async (save) => {
    const p = await ctx.newPage(); p.on('pageerror', e => errs.push(String(e)));
    await p.addInitScript(s => { localStorage.clear(); localStorage.setItem('lifemax_rpg_prototype_v1', s) }, save);
    await p.goto('file://' + file); await p.waitForTimeout(1500);
    return p;
  };
  const profile = extra => JSON.stringify(Object.assign({ onboardingComplete: true, name: 'N', tutorialDone: true, tipsSeen: { media: true } }, extra));

  // the reading list: added once, never again once the owner has changed it
  let p = await open(profile({}));
  const first = await p.evaluate(() => ({ n: state.mediaItems.length, flag: state.mediaSeededV1, ids: state.mediaItems.map(x => x.id), stored: JSON.parse(localStorage.getItem(STORE)).mediaItems.length }));
  ok('a new save gets the 15 default books once, flagged and stored', first.n === 15 && first.flag === true && first.stored === 15 && first.ids[0] === 'reading_atomic_habits', first);
  const stored = await p.evaluate(() => { window.confirm = () => true; deleteMediaItem('reading_sapiens'); return localStorage.getItem(STORE) });
  await p.close(); p = await open(stored); // a restart with that save (a reload of a file:// page can lose storage)
  ok('a removed default book stays removed after a restart', await p.evaluate(() => state.mediaItems.length === 14 && !state.mediaItems.some(x => x.id === 'reading_sapiens')));
  await p.close();
  p = await open(profile({ mediaItems: [{ id: 'mine', title: '  atomic HABITS ', type: 'Audiobook', creator: '', status: 'progress', link: '', notes: '' }] }));
  ok('a save from before the flag gets only the books it lacks (titles compared without case or spaces)', await p.evaluate(() => state.mediaItems.length === 15 && state.mediaItems[0].id === 'mine' && !state.mediaItems.some(x => x.id === 'reading_atomic_habits') && state.mediaSeededV1));

  // the page as the owner uses it
  await p.evaluate(() => navigate('media'));
  const rows = () => p.evaluate(() => [...document.querySelectorAll('#mediaList .media-item')].map(r => r.dataset.mediaId));
  const stats = () => p.evaluate(() => ['mediaTotal', 'mediaFinished', 'mediaProgress', 'mediaPct'].map(id => document.getElementById(id).textContent).join(' ') + ' ' + document.getElementById('mediaBar').style.width);
  const toast = () => p.evaluate(() => document.getElementById('toast').textContent);
  const fill = (f) => p.evaluate(f => { for (const [k, v] of Object.entries(f)) document.getElementById(k).value = v; document.querySelector('#mediaForm button[type=submit]').click() }, f);
  ok('the list shows every item in stored order, and the stats count them', JSON.stringify(await rows()) === JSON.stringify(await p.evaluate(() => state.mediaItems.map(x => x.id))) && await stats() === '15 0 1 0% 0%', await stats());

  await p.click('#view-media .pagehead .btn'); await p.waitForTimeout(300);
  ok('"＋ Add media" opens an empty form', await p.evaluate(() => document.getElementById('mediaFormCard').style.display === 'block' && document.getElementById('mediaFormTitle').textContent === 'Add to your library' && document.getElementById('mediaEditId').value === '' && document.getElementById('mediaType').value === 'Book'));
  await fill({ mediaTitle: '   ' });
  ok('a title is required; nothing is added and the form stays', await toast() === 'Add a title first' && (await rows()).length === 15 && await p.evaluate(() => document.getElementById('mediaFormCard').style.display === 'block'));
  await fill({ mediaTitle: 'Dune', mediaLink: 'javascript:alert(1)' });
  ok('a link must be http or https', await toast() === 'Use a valid http or https link' && (await rows()).length === 15);
  await fill({ mediaTitle: '  Dune ', mediaType: 'Audiobook', mediaCreator: ' Frank Herbert ', mediaStatus: 'progress', mediaLink: ' https://example.com/dune ', mediaNotes: ' Part one ' });
  const added = await p.evaluate(() => ({ item: state.mediaItems[0], stored: JSON.parse(localStorage.getItem(STORE)).mediaItems[0], form: document.getElementById('mediaFormCard').style.display }));
  ok('saving adds the item at the top, trimmed, and stores it', added.item.title === 'Dune' && added.item.creator === 'Frank Herbert' && added.item.link === 'https://example.com/dune' && added.item.notes === 'Part one' && added.item.type === 'Audiobook' && added.item.status === 'progress' && /^media_\d+_[a-z0-9]+$/.test(added.item.id) && added.stored.id === added.item.id, added);
  ok('the form closes, the page says so and the row is first', added.form === 'none' && await toast() === 'Added to your library' && (await rows())[0] === added.item.id);
  const dune = added.item.id;
  ok('the row says what the page holds: title, type · creator, status, link, notes', await p.evaluate(id => { const r = document.querySelector(`[data-media-id="${id}"]`); return r.querySelector('.media-title').textContent === 'Dune' && r.querySelector('.media-sub').textContent === 'Audiobook · Frank Herbert' && r.querySelector('.media-pill').textContent === 'In progress' && r.querySelector('a').href === 'https://example.com/dune' && r.querySelector('.media-notes').textContent === 'Part one' }, dune));

  // editing keeps the item's place, its id, its audio and when it was finished
  await p.evaluate(id => { const x = mediaById(id); x.audio = { name: 'dune.m4b', size: 2048, type: 'audio/mp4', position: 30, duration: 120 }; x.finishedAt = '2026-01-01T00:00:00.000Z'; state.mediaItems.push(state.mediaItems.shift()); renderMedia() }, dune);
  await p.evaluate(id => document.querySelector(`[data-media-id="${id}"] .media-actions .btn`).click(), dune); await p.waitForTimeout(300);
  ok('Edit fills the form with the item', await p.evaluate(id => document.getElementById('mediaFormTitle').textContent === 'Edit media item' && document.getElementById('mediaEditId').value === id && document.getElementById('mediaTitle').value === 'Dune' && document.getElementById('mediaCreator').value === 'Frank Herbert' && document.getElementById('mediaStatus').value === 'progress', dune));
  await fill({ mediaTitle: 'Dune Messiah', mediaNotes: '' });
  const edited = await p.evaluate(id => ({ at: state.mediaItems.findIndex(x => x.id === id), n: state.mediaItems.length, item: mediaById(id) }), dune);
  ok('saving an edit changes it in place and keeps its audio and finish date', edited.at === edited.n - 1 && edited.n === 16 && edited.item.title === 'Dune Messiah' && edited.item.notes === '' && edited.item.audio.name === 'dune.m4b' && edited.item.finishedAt === '2026-01-01T00:00:00.000Z' && await toast() === 'Media item updated', edited);

  // finished and back
  await p.evaluate(id => document.querySelector(`[data-media-id="${id}"] .media-check`).click(), 'reading_outlive');
  ok('ticking finishes the item, saves it and counts it', await p.evaluate(() => mediaById('reading_outlive').status === 'finished' && JSON.parse(localStorage.getItem(STORE)).mediaItems.find(x => x.id === 'reading_outlive').status === 'finished') && await toast() === 'Marked as finished' && await stats() === '16 1 2 6% 6%', await stats());
  await p.evaluate(id => document.querySelector(`[data-media-id="${id}"] .media-check`).click(), 'reading_outlive');
  ok('unticking moves it to in progress, not back to want to read', await p.evaluate(() => mediaById('reading_outlive').status === 'progress') && await toast() === 'Moved back to in progress' && await stats() === '16 0 3 0% 0%', await stats());

  // removing asks first
  await p.evaluate(() => { window.confirm = () => false; deleteMediaItem('reading_mastery') });
  ok('Remove cancelled keeps the item', await p.evaluate(() => !!mediaById('reading_mastery')));
  const asked = await p.evaluate(() => { let q = ''; window.confirm = m => { q = m; return true }; document.querySelector('[data-media-id="reading_mastery"] .media-actions .btn:last-child').click(); return q });
  ok('Remove confirmed takes it out, stores that and says so', asked === 'Remove "Mastery" from your media library?' && await p.evaluate(() => !mediaById('reading_mastery') && !JSON.parse(localStorage.getItem(STORE)).mediaItems.some(x => x.id === 'reading_mastery')) && await toast() === 'Removed from library', asked);

  // the search and the filters
  const filter = (f) => p.evaluate(f => { document.getElementById('mediaSearch').value = f.search || ''; document.getElementById('mediaFilter').value = f.status || 'all'; document.getElementById('mediaTypeFilter').value = f.type || 'all'; renderMedia(); return [...document.querySelectorAll('#mediaList .media-item')].map(r => r.dataset.mediaId) }, f);
  ok('search finds a title piece, without case, ignoring spaces around it', JSON.stringify(await filter({ search: '  SAPIENS ' })) === '["reading_sapiens"]' && JSON.stringify(await filter({ search: 'messiah' })) === JSON.stringify([dune]));
  ok('search reads the creator, the notes and the type too', JSON.stringify(await filter({ search: 'goggins' })) === '["reading_cant_hurt_me"]' && (await filter({ search: 'month 3' })).length === 4 && (await filter({ search: 'audiobook' })).length === 2);
  const byStatus = { progress: await filter({ status: 'progress' }), unread: await filter({ status: 'unread' }), finished: await filter({ status: 'finished' }) };
  ok('the status filter shows only that status', JSON.stringify(byStatus.progress) === JSON.stringify(['mine', 'reading_outlive', dune]) && byStatus.unread.length === 12 && byStatus.finished.length === 0, byStatus);
  ok('the type filter shows only that type, and combines with the others', JSON.stringify(await filter({ type: 'Audiobook' })) === JSON.stringify(['mine', dune]) && JSON.stringify(await filter({ type: 'Audiobook', search: 'herbert' })) === JSON.stringify([dune]) && (await filter({ type: 'Book', status: 'progress' })).length === 1);
  ok('the page\'s filter is what mediaFilterOnPage reads and mediaMatching applies', await p.evaluate(() => JSON.stringify(mediaFilterOnPage()) === '{"search":"","status":"progress","type":"Book"}' && mediaMatching(state.mediaItems, mediaFilterOnPage()).map(x => x.id).join() === 'reading_outlive'));
  await filter({ search: 'nothing like this' });
  ok('no match says so', await p.evaluate(() => /No matching items/.test(document.getElementById('mediaList').textContent) && /Try changing your search or filters/.test(document.getElementById('mediaList').textContent)));
  await p.evaluate(() => { state.mediaItems = []; document.getElementById('mediaSearch').value = ''; renderMedia() });
  ok('an empty library offers the first item', await p.evaluate(() => /Your library is ready/.test(document.getElementById('mediaList').textContent) && !!document.querySelector('#mediaList button[onclick="showMediaForm()"]')) && await stats() === '0 0 0 0% 0%');

  // mediaUpsert, the owner the form calls
  await p.evaluate(() => { state.mediaItems = [{ id: 'a', title: 'A', type: 'Book', creator: '', status: 'unread', link: '', notes: '' }]; save() });
  const owner = await p.evaluate(() => {
    const r1 = mediaUpsert({ id: 'a', title: 'A2', type: 'Book', status: 'progress' }), r2 = mediaUpsert({ id: 'a', title: 'A2', type: 'Book', status: 'progress' });
    const long = mediaUpsert({ title: 'x'.repeat(200), creator: 'c'.repeat(200), notes: 'n'.repeat(2000), link: 'https://e.com/' + 'l'.repeat(600) }), item = mediaById(long.id);
    return { r1, r2, n: state.mediaItems.length, a: mediaById('a'), sizes: [item.title.length, item.creator.length, item.notes.length, item.link.length], long };
  });
  ok('the same edit twice leaves one item as asked', owner.r1.ok && owner.r2.ok && owner.r1.message === 'Media item updated' && owner.a.title === 'A2' && owner.a.status === 'progress' && owner.n === 2, owner);
  ok('fields are held to the form\'s limits', owner.long.ok && JSON.stringify(owner.sizes) === '[160,120,1000,500]', owner.sizes);
  const failed = await p.evaluate(() => {
    const before = JSON.stringify(state.mediaItems), real = Storage.prototype.setItem;
    Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
    let add, edit; try { add = mediaUpsert({ title: 'Lost' }); edit = mediaUpsert({ id: 'a', title: 'Changed' }) } finally { Storage.prototype.setItem = real }
    saveReadOnly = 'A newer version saved this'; saveReadOnlyTold = true; let ro; try { ro = mediaUpsert({ title: 'Lost too' }) } finally { saveReadOnly = ''; saveReadOnlyTold = false }
    return { add, edit, ro, same: JSON.stringify(state.mediaItems) === before, rows: document.querySelectorAll('#mediaList .media-item').length };
  });
  ok('a failed save is taken back out and reported', !failed.add.ok && failed.add.message === 'Could not save to this browser (storage full?)' && !failed.edit.ok && failed.same && failed.rows === 2, failed);
  ok('a read-only save is named as the reason', !failed.ro.ok && failed.ro.message === 'Not saved: this version is read-only for your save', failed.ro);
  await p.evaluate(() => { showMediaForm(); document.getElementById('mediaTitle').value = 'Kept'; const real = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) }; try { document.querySelector('#mediaForm button[type=submit]').click() } finally { Storage.prototype.setItem = real } });
  ok('the form stays open with what was typed when its save fails', await toast() === 'Could not save to this browser (storage full?)' && await p.evaluate(() => document.getElementById('mediaFormCard').style.display === 'block' && document.getElementById('mediaTitle').value === 'Kept' && !state.mediaItems.some(x => x.title === 'Kept')));
  await p.evaluate(() => hideMediaForm());

  // finishing, removing and removing the audio: a save that fails takes the change back and says so, and keeps the audio file
  const writers = await p.evaluate(async () => {
    const real = Storage.prototype.setItem, said = () => document.getElementById('toast').textContent;
    const a = mediaById('a'); a.audio = { name: 'a.m4b', size: 2048, type: 'audio/mp4', position: 30, duration: 120 }; save();
    await audioDbPut('a', { blob: new Blob(['a']), name: 'a.m4b', type: 'audio/mp4', size: 1, updatedAt: a.updatedAt });
    window.confirm = () => true;
    const held = JSON.stringify(state.mediaItems), stored = localStorage.getItem(STORE);
    const tryAll = async () => {
      const out = [];
      toggleMediaFinished('a', true); out.push(said());
      deleteMediaItem('a'); out.push(said());
      await removeMediaAudio('a'); out.push(said());
      return out;
    };
    Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
    let full; try { full = await tryAll() } finally { Storage.prototype.setItem = real }
    saveReadOnly = 'A newer version saved this'; saveReadOnlyTold = true;
    let ro; try { ro = await tryAll() } finally { saveReadOnly = ''; saveReadOnlyTold = false }
    const row = document.querySelector('[data-media-id="a"]');
    return {
      full, ro, same: JSON.stringify(state.mediaItems) === held, stored: localStorage.getItem(STORE) === stored, file: !!(await audioDbGet('a')),
      row: !!row && !row.querySelector('.media-check').checked && row.querySelector('.media-pill').textContent === 'In progress' && !!row.querySelector('.media-audio-meta'),
    };
  });
  ok('a failed save of a tick, a removal or an audio removal is taken back and reported, and the file is kept', writers.full.every(m => m === 'Could not save to this browser (storage full?)') && writers.same && writers.stored && writers.file && writers.row, writers);
  ok('a read-only save is named as the reason for each of them', writers.ro.every(m => m === 'Not saved: this version is read-only for your save'), writers.ro);

  // attaching or replacing a file (the file button behind native Attach and Replace file): a save that cannot name the new file
  // leaves the save, the item and the file it names as they were, and says so
  const attached = await p.evaluate(async () => {
    const real = Storage.prototype.setItem, said = () => document.getElementById('toast').textContent;
    const fresh = state.mediaItems.find(x => x.id !== 'a' && !x.audio), file = name => new File([new Uint8Array(2048)], name, { type: 'audio/mp4' });
    const held = JSON.stringify(state.mediaItems), stored = localStorage.getItem(STORE);
    const tryBoth = async () => {
      const out = [];
      await attachAudioFile(fresh.id, file('new.m4b')); out.push(said());
      await attachAudioFile('a', file('new.m4b')); out.push(said());
      return out;
    };
    Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
    let full; try { full = await tryBoth() } finally { Storage.prototype.setItem = real }
    saveReadOnly = 'A newer version saved this'; saveReadOnlyTold = true;
    let ro; try { ro = await tryBoth() } finally { saveReadOnly = ''; saveReadOnlyTold = false }
    const kept = await audioDbGet('a'), none = await audioDbGet(fresh.id), row = document.querySelector('[data-media-id="' + fresh.id + '"]');
    return { full, ro, same: JSON.stringify(state.mediaItems) === held, stored: localStorage.getItem(STORE) === stored, kept: kept && [kept.name, kept.size], none, attach: !!row && !row.querySelector('.media-audio-meta') };
  });
  ok('a failed save of an attached or a replaced file is taken back and reported, and the old file is kept', attached.full.every(m => m === 'Could not save to this browser (storage full?)') && attached.same && attached.stored && JSON.stringify(attached.kept) === '["a.m4b",1]' && attached.none === null && attached.attach, attached);
  ok('a read-only save is named as the reason for attaching and replacing, and no file is stored', attached.ro.every(m => m === 'Not saved: this version is read-only for your save'), attached.ro);
  const removed = await p.evaluate(async () => { await removeMediaAudio('a'); return { said: document.getElementById('toast').textContent, audio: mediaById('a').audio, file: await audioDbGet('a'), stored: JSON.parse(localStorage.getItem(STORE)).mediaItems.find(x => x.id === 'a').audio } });
  ok('a stored audio removal deletes the file and says so', removed.said === 'Audio removed' && removed.audio === undefined && removed.file === null && removed.stored === undefined, removed);

  // the owners a native screen calls: each answers {ok, changed, message} in the page's words; a repeat changes nothing and saves nothing
  const owners = await p.evaluate(async () => {
    const real = Storage.prototype.setItem; let writes = 0;
    state.mediaItems = [{ id: 'o1', title: 'One', type: 'Book', creator: '', status: 'unread', link: '', notes: '' }, { id: 'o2', title: 'Two', type: 'Audiobook', creator: '', status: 'progress', link: '', notes: '', audio: { name: 'two.m4b', size: 1, type: 'audio/mp4', position: 5, duration: 9 } }];
    save(); await audioDbPut('o2', { blob: new Blob(['2']), name: 'two.m4b', type: 'audio/mp4', size: 1 });
    Storage.prototype.setItem = function (k) { if (k === STORE) writes++; return real.apply(this, arguments) };
    const run = async f => { const w = writes, r = await f(); return JSON.stringify(Object.assign({}, r, { writes: writes - w })) };
    const out = {};
    try {
      for (const [k, f] of Object.entries({
        finish: () => mediaSetStatus('o1', 'finished'), finishAgain: () => mediaSetStatus('o1', 'finished'), back: () => mediaSetStatus('o1', 'progress'),
        unread: () => mediaSetStatus('o1', 'unread'), gone: () => mediaSetStatus('nope', 'finished'), bad: () => mediaSetStatus('o1', 'done'),
        audio: () => mediaRemoveAudio('o2'), audioAgain: () => mediaRemoveAudio('o2'), remove: () => mediaRemove('o1'), removeAgain: () => mediaRemove('o1'),
      })) out[k] = await run(f);
    } finally { Storage.prototype.setItem = real }
    out.file = await audioDbGet('o2'); out.items = JSON.stringify(state.mediaItems.map(x => [x.id, x.status, !!x.audio]));
    return out;
  });
  const said = (ok, changed, message, writes) => JSON.stringify({ ok, changed, message, writes });
  ok('mediaSetStatus sets a status once, in the page\'s words; the same status again saves nothing', owners.finish === said(true, true, 'Marked as finished', 1) && owners.finishAgain === said(true, false, 'Already finished', 0) && owners.back === said(true, true, 'Moved back to in progress', 1) && owners.unread === said(true, true, 'Status updated', 1), owners);
  ok('mediaSetStatus refuses an item that is gone and a status the page does not have', owners.gone === said(false, false, 'That item is no longer in your library', 0) && owners.bad === said(false, false, 'Unknown status', 0), owners);
  ok('mediaRemoveAudio and mediaRemove change once; again, they answer done and save nothing', owners.audio === said(true, true, 'Audio removed', 1) && owners.audioAgain === said(true, false, 'Audio removed', 0) && owners.remove === said(true, true, 'Removed from library', 1) && owners.removeAgain === said(true, false, 'Removed from library', 0) && owners.file === null && owners.items === '[["o2","progress",false]]', owners);
  const ownersFail = await p.evaluate(async () => {
    const real = Storage.prototype.setItem;
    state.mediaItems = [{ id: 'f1', title: 'F', type: 'Audiobook', creator: '', status: 'progress', link: '', notes: '', audio: { name: 'f.m4b', size: 1, type: 'audio/mp4', position: 5, duration: 9 } }]; save();
    const held = JSON.stringify(state.mediaItems), all = async () => JSON.stringify([mediaSetStatus('f1', 'finished'), mediaRemove('f1'), await mediaRemoveAudio('f1')]);
    Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
    let full; try { full = await all() } finally { Storage.prototype.setItem = real }
    saveReadOnly = 'A newer version saved this'; saveReadOnlyTold = true;
    let ro; try { ro = await all() } finally { saveReadOnly = ''; saveReadOnlyTold = false }
    return { full, ro, same: JSON.stringify(state.mediaItems) === held };
  });
  const thrice = m => JSON.stringify([0, 1, 2].map(() => ({ ok: false, changed: false, message: m })));
  ok('each owner answers a failed and a read-only save with nothing changed', ownersFail.full === thrice('Could not save to this browser (storage full?)') && ownersFail.ro === thrice('Not saved: this version is read-only for your save') && ownersFail.same, ownersFail);

  // an add carrying an operation id stores it on the item in the same save: the same add again answers that item, adding nothing
  const receipt = await p.evaluate(() => {
    const real = Storage.prototype.setItem; let writes = 0;
    state.mediaItems = []; save();
    Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
    let lost; try { lost = mediaUpsert({ op: 'op-1', title: 'Dune' }) } finally { Storage.prototype.setItem = real }
    const afterLost = state.mediaItems.length;
    Storage.prototype.setItem = function (k) { if (k === STORE) writes++; return real.apply(this, arguments) };
    let first, again, edit, other, w1, w2;
    try {
      first = mediaUpsert({ op: 'op-1', title: 'Dune' }); w1 = writes; again = mediaUpsert({ op: 'op-1', title: 'Dune' }); w2 = writes;
      edit = mediaUpsert({ id: first.id, op: 'op-2', title: 'Dune Messiah', type: 'Book' }); other = mediaUpsert({ op: 'op-3', title: 'Dune' });
    } finally { Storage.prototype.setItem = real }
    return { lost, afterLost, first, again, w1, w2, edit, other, stored: JSON.parse(localStorage.getItem(STORE)).mediaItems.map(x => [x.title, x.addOp || null]) };
  });
  ok('a failed add stores no receipt, so its retry adds', !receipt.lost.ok && receipt.afterLost === 0 && receipt.first.ok && receipt.first.message === 'Added to your library' && receipt.w1 === 1, receipt);
  ok('the same add again answers the item it made, with no second item and no save', receipt.again.ok && receipt.again.id === receipt.first.id && receipt.again.duplicate === true && receipt.again.message === 'Added to your library' && receipt.w2 === receipt.w1, receipt);
  ok('an edit is a plain set that keeps the add\'s receipt, and another operation adds another item', receipt.edit.ok && receipt.edit.message === 'Media item updated' && receipt.other.ok && receipt.other.id !== receipt.first.id && JSON.stringify(receipt.stored) === '[["Dune","op-3"],["Dune Messiah","op-1"]]', receipt);

  // a stored type the form does not offer: the form's list shows none, and saving the form keeps it until a type is chosen
  const odd = await p.evaluate(() => {
    state.mediaItems = [{ id: 'odd', title: 'Watchmen', type: 'Comic', creator: '', status: 'unread', link: '', notes: '' }]; save(); navigate('media');
    showMediaForm(mediaById('odd')); const shown = document.getElementById('mediaType').value;
    document.getElementById('mediaNotes').value = 'Graphic novel'; document.querySelector('#mediaForm button[type=submit]').click();
    const viaForm = [mediaById('odd').type, mediaById('odd').notes];
    mediaUpsert({ id: 'odd', title: 'Watchmen', status: 'unread', type: 'Book' });
    return { shown, viaForm, chosen: mediaById('odd').type, stored: JSON.parse(localStorage.getItem(STORE)).mediaItems[0].type };
  });
  ok('an edit keeps a stored type the form does not offer, and a type chosen replaces it', odd.shown === '' && JSON.stringify(odd.viaForm) === '["Comic","Graphic novel"]' && odd.chosen === 'Book' && odd.stored === 'Book', odd);

  // Remove asks with the title as it is
  const quoted = await p.evaluate(() => { let q = ''; window.confirm = m => { q = m; return false }; state.mediaItems = [{ id: 'q', title: 'The "Big" Book', type: 'Book', creator: '', status: 'unread', link: '', notes: '' }]; deleteMediaItem('q'); return q });
  ok('Remove asks with the title\'s quotes as they are, no backslash before them', quoted === 'Remove "The "Big" Book" from your media library?', quoted);

  // audioBusy(): the player loading, loaded or showing (a native screen over the Library steps aside while it is)
  const busy = await p.evaluate(() => {
    closeAudioPlayer(true); const bar = document.getElementById('audioPlayer'), out = [audioBusy()];
    audioCtx.loading = 'x'; out.push(audioBusy()); audioCtx.loading = null;
    audioCtx.mediaId = 'x'; out.push(audioBusy()); audioCtx.mediaId = null;
    bar.classList.add('show'); out.push(audioBusy()); bar.classList.remove('show'); out.push(audioBusy());
    return out;
  });
  ok('audioBusy: free at rest, busy while a file loads, while one is loaded and while the player shows', JSON.stringify(busy) === '[false,true,true,true,false]', busy);

  // audio: the progress words and the meta line, drawn and updated live from one function
  const words = await p.evaluate(() => [mediaAudioProgress('progress', 0, 0), mediaAudioProgress('progress', 1800, 3600), mediaAudioProgress('progress', 3599, 3600), mediaAudioProgress('finished', 10, 3600), mediaAudioProgress('unread', 9999, 3600)].map(w => Object.assign(w, { pct: +w.pct.toFixed(2) })));
  ok('audio progress words: no duration yet, a share, almost, finished; the bar never runs past the end', JSON.stringify(words) === JSON.stringify([
    { pct: 0, label: 'Progress available after playback starts', time: '—' }, { pct: 50, label: '50% listened', time: '30:00 / 1:00:00' },
    { pct: 99.97, label: 'Almost finished', time: '59:59 / 1:00:00' }, { pct: 100, label: 'Finished', time: '0:10 / 1:00:00' }, { pct: 100, label: 'Almost finished', time: '2:46:39 / 1:00:00' }]), words);
  const live = await p.evaluate(() => {
    state.mediaItems = [{ id: 'ab', title: 'Book', type: 'Audiobook', creator: '', status: 'progress', link: '', notes: '', audio: { name: 'book.m4b', size: 3145728, type: 'audio/mp4', position: 65, duration: 0 } }]; renderMedia();
    const row = () => document.querySelector('[data-media-id="ab"]'), read = () => [row().querySelector('[data-audio-progress-label]').textContent, row().querySelector('[data-audio-progress-time]').textContent, row().querySelector('[data-audio-progress-fill]').style.width];
    const drawn = read(), meta = row().querySelector('.media-audio-meta').textContent;
    updateMediaAudioProgress('ab', 900, 3600);
    return { drawn, meta, live: read(), held: mediaById('ab').audio };
  });
  ok('a row with audio shows its progress and file line', JSON.stringify(live.drawn) === JSON.stringify(['Progress available after playback starts', '—', '0%']) && live.meta === 'book.m4b · 3.0 MB · resume 1:05', live);
  ok('playback updates the row and the item in memory', JSON.stringify(live.live) === JSON.stringify(['25% listened', '15:00 / 1:00:00', '25%']) && live.held.position === 900 && live.held.duration === 3600, live);

  ok('no page errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
