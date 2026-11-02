/* Native Library's Media sheets (plan JOURNAL_AND_LIBRARY_SHEETS §3.4 phase 3), web half: a screen that says it draws the sheets
   (library.media.write.v1) gets edit "native" and the page's form words read from the page's own form; its commands upsert,
   setStatus, remove and removeAudio run the page's owners without closing the screen, answer ok only once the save stored the
   change, recognise a repeat (the add's operation id, sets, ids already gone), take back a failed save, and keep an old item's
   unusual type. A screen that does not say so gets edit "page", no form, and every command refused. A fake NativeMedia plugin
   stands in for Android. Run: NODE_PATH=$(npm root -g) node tests/native-library-sheets.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const ITEMS = [
  { id: 'm_audio', title: 'Project Hail Mary', type: 'Audiobook', creator: 'Andy Weir', status: 'progress', link: 'https://example.com/hail-mary', notes: 'Chapter 12', audio: { name: 'hail-mary.m4b', size: 503316480, type: 'audio/mp4', position: 15000, duration: 58200 } },
  { id: 'm_course', title: 'Learning How to Learn', type: 'Course', creator: 'Coursera', status: 'unread', link: 'javascript:alert(1)', notes: 'Weeks 1-4' },
  { id: 'm_pod', title: 'Huberman Lab', type: 'Podcast', creator: '', status: 'progress', link: 'http://example.org/pod', notes: '' },
  { id: 'm_comic', title: 'Watch "Cities" again', type: 'Comic', creator: '', status: 'unread', link: '', notes: '' },
  { id: 'm_old', title: 'Old import', creator: 'Someone', status: '', link: '', notes: 'no type, no status' },
];
const SAVE = JSON.stringify({ onboardingComplete: true, name: 'N', tutorialDone: true, tipsSeen: { media: true }, mediaSeededV1: true, mediaItems: ITEMS });
async function visit(b, caps) {
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript(`
    localStorage.setItem('lifemax_rpg_prototype_v1', ${JSON.stringify(SAVE)});
    localStorage.setItem('lifemax_android_prefs_v1', JSON.stringify({nativeLibrary:true,nativeOverview:false}));
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
      Plugins:{NativeMedia:mk('media',${JSON.stringify(caps)}),NativeOverview:mk('overview',[])},registerPlugin:()=>({}),convertFileSrc:u=>u};
  `);
  await p.goto('file://' + file); await p.waitForTimeout(2500);
  return { p, errs };
}
/* helpers defined on the page: the screen's commands, the replies and frames it got, and the stored save */
const HELPERS = () => {
  const wait = ms => new Promise(r => setTimeout(r, ms)), P = window.Capacitor.Plugins.NativeMedia;
  const calls = k => window.__calls.filter(c => c[0] === 'media' && c[1] === k);
  const until = async (f, ms = 5000) => { for (let t = 0; t < ms && !f(); t += 50) await wait(50) };
  let n = 0;
  /** a command as the screen sends it; resolves with the reply and the frame that carried it */
  const command = async (name, payload, requestId = 'r' + (++n)) => {
    await P.__emit('command', { command: name, payload, requestId });
    await until(() => calls('update').some(c => c[2].reply && c[2].reply.requestId === requestId));
    const u = calls('update').find(c => c[2].reply && c[2].reply.requestId === requestId);
    return u ? { reply: u[2].reply, frame: u[2].snapshot } : { reply: null, frame: null };
  };
  const stored = () => JSON.parse(localStorage.getItem(STORE)).mediaItems;
  const failing = async (f) => {
    const real = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
    try { return await f() } finally { Storage.prototype.setItem = real }
  };
  window.__h = { wait, calls, until, command, stored, failing };
  lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { }
};
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });

  // a screen that draws the sheets
  const { p, errs } = await visit(b, ['library.media.projection.v1', 'library.media.write.v1']);
  await p.evaluate(HELPERS);
  const proj = await p.evaluate(async () => {
    const { until, calls } = window.__h;
    navigate('dashboard'); navigate('media'); await until(() => calls('open').length);
    const s = calls('open')[0][2].snapshot, f = s.form, $ = id => document.getElementById(id);
    const label = id => document.querySelector('label[for=' + id + ']').textContent;
    const opts = id => [...$(id).options].map(o => o.value + '=' + o.text);
    // the page's own form, filled as its Add and its Edit fill it
    showMediaForm(); const addPage = [$('mediaFormTitle').textContent, $('mediaType').value, $('mediaStatus').value]; hideMediaForm();
    let asked = []; const realConfirm = window.confirm; window.confirm = (m) => { asked.push(m); return false };
    deleteMediaItem('m_comic'); removeMediaAudio('m_audio'); window.confirm = realConfirm;
    return {
      edit: s.edit, f,
      fields: JSON.stringify([f.fields.title, f.fields.creator, f.fields.link, f.fields.notes].map(x => [x.label, x.placeholder, x.maxLength]))
        === JSON.stringify(['mediaTitle', 'mediaCreator', 'mediaLink', 'mediaNotes'].map(id => [label(id), $(id).placeholder, $(id).maxLength])),
      selects: JSON.stringify([f.fields.type, f.fields.status].map(x => [x.label, x.options.map(o => o.id + '=' + o.label)])) === JSON.stringify(['mediaType', 'mediaStatus'].map(id => [label(id), opts(id)])),
      words: f.heading.add === addPage[0] && f.defaults.type === addPage[1] && f.defaults.status === addPage[2] && f.heading.edit === 'Edit media item'
        && f.save === document.querySelector('#mediaForm button[type=submit]').textContent && f.cancel === 'Cancel' && f.fields.title.required === 'Add a title first',
      asked, confirms: f.confirms,
      links: JSON.stringify(f.links) === '{"m_course":"javascript:alert(1)"}',
      itemsUnchanged: s.items.find(x => x.id === 'm_course').link === '' && Object.keys(s.items[0]).join() === 'id,title,type,creator,subtitle,status,statusLabel,finished,notes,link,audio',
    };
  });
  ok('a screen that draws the sheets gets edit "native" and the form', proj.edit === 'native' && !!proj.f, proj);
  ok('the form\'s fields are the page form\'s labels, placeholders and lengths', proj.fields, proj.f && proj.f.fields);
  ok('the form\'s type and status are the page\'s selects', proj.selects, proj.f && proj.f.fields);
  ok('the heading, the defaults, Save and Cancel and the title\'s refusal are the page\'s', proj.words, proj.f);
  const fill = (t, title) => t.split('{title}').join(title);
  ok('the confirms are the page\'s own, without a stray backslash before quotes', proj.asked.length === 2
    && proj.asked[0] === fill(proj.confirms.remove.text, 'Watch "Cities" again') && proj.asked[0] === 'Remove "Watch "Cities" again" from your media library?'
    && proj.asked[1] === fill(proj.confirms.removeAudio.text, 'Project Hail Mary') && proj.confirms.remove.confirm === 'Remove' && proj.confirms.removeAudio.confirm === 'Remove audio' && proj.confirms.cancel === 'Cancel', proj);
  ok('a link the rows do not show comes with the form, so an edit keeps it', proj.links && proj.itemsUnchanged, proj.f && proj.f.links);

  // add: stored with its operation id, said by the page's toast, the screen stays; the same add again answers the same item
  const add = await p.evaluate(async () => {
    const { command, calls, stored } = window.__h, closes = calls('close').length;
    const fields = { title: '  Dune  ', type: 'Book', creator: 'Frank Herbert', status: 'unread', link: 'https://example.com/dune', notes: 'Book one' };
    const a = await command('upsert', { op: 'op-dune', fields });
    const disk = stored().filter(x => x.title === 'Dune');
    const b2 = await command('upsert', { op: 'op-dune', fields });
    const countAfterRepeat = stored().filter(x => x.title === 'Dune').length;
    // the reply was lost and the sheet was edited before saving again (the same operation id): an edit of that item, not a dropped add
    const b3 = await command('upsert', { op: 'op-dune', fields: Object.assign({}, fields, { title: 'Dune Messiah' }) });
    const afterEdit = stored().filter(x => x.addOp === 'op-dune');
    return { a: a.reply, notice: a.frame.notice, row: a.frame.items[0], disk, b: b2.reply, b3: b3.reply, afterEdit, count: countAfterRepeat, stayed: calls('close').length === closes };
  });
  ok('an add is stored with its operation id in the same save, and answered ok and changed', add.a.ok && add.a.changed && add.a.command === 'upsert' && add.disk.length === 1 && add.disk[0].addOp === 'op-dune' && add.a.id === add.disk[0].id && add.a.message === 'Added to your library', add);
  ok('the page\'s toast says it and comes with the reply\'s frame, which shows the item; the screen stays', add.notice && add.notice.text === 'Added to your library' && add.row.title === 'Dune' && add.row.creator === 'Frank Herbert' && add.stayed, add);
  ok('the same add again (a lost reply) answers the same item, unchanged, and adds nothing', add.b.ok && !add.b.changed && add.b.id === add.a.id && add.count === 1, add);
  ok('the same operation with an edited form edits that item (one item, the new title), not a dropped add', add.b3.ok && add.b3.id === add.a.id && add.afterEdit.length === 1 && add.afterEdit[0].title === 'Dune Messiah', add);

  // refusals: in the page's words, by field, nothing saved, no toast (the sheet says it)
  const refused = await p.evaluate(async () => {
    const { command, stored } = window.__h, before = JSON.stringify(stored());
    const toastBefore = document.getElementById('toast').textContent;
    const t = (await command('upsert', { op: 'op-x', fields: { title: '   ', type: 'Book', creator: '', status: 'unread', link: '', notes: '' } })).reply;
    const l = (await command('upsert', { op: 'op-y', fields: { title: 'X', type: 'Book', creator: '', status: 'unread', link: 'ftp://x', notes: '' } })).reply;
    const bad = (await command('upsert', { op: 'op-z', fields: { title: 'X' } })).reply;
    const noOp = (await command('upsert', { fields: { title: 'X', type: 'Book', creator: '', status: 'unread', link: '', notes: '' } })).reply;
    const gone = (await command('upsert', { id: 'nope', fields: { title: 'X', type: 'Book', creator: '', status: 'unread', link: '', notes: '' } })).reply;
    return { t, l, bad, noOp, gone, same: JSON.stringify(stored()) === before, quiet: document.getElementById('toast').textContent === toastBefore, back: !mediaById('nope') };
  });
  ok('a blank title is refused with the page\'s words, on the title', !refused.t.ok && refused.t.field === 'title' && refused.t.message === 'Add a title first', refused.t);
  ok('a link that is not http or https is refused with the page\'s words, on the link', !refused.l.ok && refused.l.field === 'link' && refused.l.message === 'Use a valid http or https link', refused.l);
  ok('a malformed upsert, or an add without its operation id, is refused', !refused.bad.ok && !refused.noOp.ok && refused.bad.message === 'Something went wrong. Nothing was changed.', refused);
  ok('an edit of an item no longer there is refused and does not add it back', !refused.gone.ok && refused.gone.message === 'That item is no longer in your library' && refused.back, refused.gone);
  ok('refusals save nothing and leave the toast to the sheet', refused.same && refused.quiet, refused);

  // edits: a plain set; an unusual stored type is kept; a stored link the rows hide is kept when sent back
  const edit = await p.evaluate(async () => {
    const { command, stored } = window.__h, get = id => stored().find(x => x.id === id);
    const pod = (await command('upsert', { id: 'm_pod', fields: { title: 'Huberman Lab', type: 'Podcast', creator: 'Andrew Huberman', status: 'progress', link: 'http://example.org/pod', notes: '' } })).reply;
    const comic = (await command('upsert', { id: 'm_comic', fields: { title: 'Watch "Cities" again', type: 'Comic', creator: 'Me', status: 'unread', link: '', notes: '' } })).reply;
    const old = (await command('upsert', { id: 'm_old', fields: { title: 'Old import', type: '', creator: 'Someone', status: '', link: '', notes: 'still no type' } })).reply;
    return { pod, comic, old, podDisk: get('m_pod'), comicDisk: get('m_comic'), oldDisk: get('m_old') };
  });
  ok('an edit is stored and answered with the page\'s words', edit.pod.ok && edit.pod.changed && edit.pod.message === 'Media item updated' && edit.podDisk.creator === 'Andrew Huberman' && !edit.podDisk.addOp, edit);
  ok('an old item\'s unusual type is kept, and an item without one stays without', edit.comic.ok && edit.comicDisk.type === 'Comic' && edit.comicDisk.creator === 'Me' && edit.old.ok && edit.oldDisk.type === '' && edit.oldDisk.notes === 'still no type', edit);

  // a failed save: taken back, ok:false with the page's words; the same operation again lands once
  const failed = await p.evaluate(async () => {
    const { command, stored, failing } = window.__h;
    const fields = { title: 'Lost and found', type: 'Article', creator: '', status: 'unread', link: '', notes: '' };
    const a = await failing(() => command('upsert', { op: 'op-lost', fields }));
    const inMemory = state.mediaItems.some(x => x.title === 'Lost and found');
    const b2 = await command('upsert', { op: 'op-lost', fields });
    const ro = await (async () => { saveReadOnly = 'A newer version saved this'; saveReadOnlyTold = true; try { return (await command('setStatus', { id: 'm_pod', status: 'finished' })).reply } finally { saveReadOnly = ''; saveReadOnlyTold = false } })();
    return { a: a.reply, inMemory, b: b2.reply, count: stored().filter(x => x.title === 'Lost and found').length, ro, podStatus: mediaById('m_pod').status };
  });
  ok('an add whose save fails is answered ok:false with the page\'s words and taken back', !failed.a.ok && !failed.a.changed && failed.a.message === 'Could not save to this browser (storage full?)' && !failed.inMemory, failed);
  ok('the retry with the same operation id lands once', failed.b.ok && failed.b.changed && failed.count === 1, failed);
  ok('a read-only save refuses a status with the page\'s reason and keeps the old one', !failed.ro.ok && failed.ro.message === 'Not saved: this version is read-only for your save' && failed.podStatus === 'progress', failed);

  // the tick: a set, said by the page's toast; the same status again changes nothing and saves nothing
  const tick = await p.evaluate(async () => {
    const { command, stored } = window.__h;
    const a = await command('setStatus', { id: 'm_pod', status: 'finished' });
    const at = JSON.parse(localStorage.getItem(STORE)).mediaItems.find(x => x.id === 'm_pod').updatedAt;
    const b2 = await command('setStatus', { id: 'm_pod', status: 'finished' });
    const at2 = stored().find(x => x.id === 'm_pod').updatedAt;
    const c = await command('setStatus', { id: 'm_pod', status: 'progress' });
    const gone = (await command('setStatus', { id: 'nope', status: 'finished' })).reply;
    const unknown = (await command('setStatus', { id: 'm_pod', status: 'weird' })).reply;
    return { a: a.reply, aNotice: a.frame.notice, aRow: a.frame.items.find(x => x.id === 'm_pod'), b: b2.reply, same: at === at2, c: c.reply, gone, unknown };
  });
  ok('setStatus finishes an item, answered ok and changed with the page\'s words, shown in the frame', tick.a.ok && tick.a.changed && tick.a.message === 'Marked as finished' && tick.aNotice.text === 'Marked as finished' && tick.aRow.finished, tick);
  ok('the same status again is ok, unchanged, and saves nothing', tick.b.ok && !tick.b.changed && tick.same, tick);
  ok('unticking moves it back to in progress', tick.c.ok && tick.c.changed && tick.c.message === 'Moved back to in progress', tick.c);
  ok('an unknown item or status is refused', !tick.gone.ok && tick.gone.message === 'That item is no longer in your library' && !tick.unknown.ok, tick);

  // remove and remove audio: idempotent by id, a failed save taken back
  const removal = await p.evaluate(async () => {
    const { command, stored, failing } = window.__h;
    const f = (await failing(() => command('remove', { id: 'm_comic' }))).reply, kept = !!mediaById('m_comic');
    const a = await command('remove', { id: 'm_comic' });
    const b2 = (await command('remove', { id: 'm_comic' })).reply;
    const fa = (await failing(() => command('removeAudio', { id: 'm_audio' }))).reply, keptAudio = !!mediaById('m_audio').audio;
    const ra = await command('removeAudio', { id: 'm_audio' });
    const rb = (await command('removeAudio', { id: 'm_audio' })).reply;
    return { f, kept, a: a.reply, aRows: a.frame.items.map(x => x.id), disk: stored().some(x => x.id === 'm_comic'), b: b2, fa, keptAudio, ra: ra.reply, raRow: ra.frame.items.find(x => x.id === 'm_audio'), rb, diskAudio: !!stored().find(x => x.id === 'm_audio').audio };
  });
  ok('a removal whose save fails is refused and the item kept', !removal.f.ok && removal.kept, removal);
  ok('remove is answered ok and changed, and the frame drops the row', removal.a.ok && removal.a.changed && removal.a.message === 'Removed from library' && !removal.aRows.includes('m_comic') && !removal.disk, removal);
  ok('removing an id already gone is ok and unchanged', removal.b.ok && !removal.b.changed, removal.b);
  ok('remove audio: a failed save keeps it; then ok and changed; again unchanged', !removal.fa.ok && removal.keptAudio && removal.ra.ok && removal.ra.changed && removal.ra.message === 'Audio removed' && removal.raRow.audio === null && !removal.diskAudio && removal.rb.ok && !removal.rb.changed, removal);

  const quiet = await p.evaluate(() => window.__h.calls('close').length);
  ok('no command closed the screen', quiet === 0, quiet);
  ok('no page errors', errs.length === 0, errs);

  // a screen that does not draw the sheets: the page's form, and every command refused
  const older = await visit(b, ['library.media.projection.v1']);
  await older.p.evaluate(HELPERS);
  const old = await older.p.evaluate(async () => {
    const { until, calls, command } = window.__h;
    navigate('dashboard'); navigate('media'); await until(() => calls('open').length);
    const s = calls('open')[0][2].snapshot;
    const r = (await command('upsert', { op: 'op-old', fields: { title: 'Nope', type: 'Book', creator: '', status: 'unread', link: '', notes: '' } })).reply;
    const t = (await command('setStatus', { id: 'm_pod', status: 'finished' })).reply;
    return { edit: s.edit, form: s.form, r, t, added: state.mediaItems.some(x => x.title === 'Nope'), status: mediaById('m_pod').status };
  });
  ok('an older screen gets edit "page" and no form', old.edit === 'page' && old.form === null, old);
  ok('an older screen\'s commands are refused and change nothing', !old.r.ok && !old.t.ok && old.r.message === 'Media cannot do that here.' && !old.added && old.status === 'progress', old);
  ok('no page errors (older screen)', older.errs.length === 0, older.errs);

  await b.close();
  console.log(`\n${pass} passed, ${fail} failed`); process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1) });
