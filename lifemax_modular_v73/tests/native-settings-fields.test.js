/* Native Settings, the text, number and time fields (settings.fields.v1): without the capability every field is still a
   hand-over (the projection is the old one), with it the Notifications times, the profile, the decay settings and the XP rules are
   edited on the screen through the page's own setters (lmaSet, saveCharacter, saveDecay, saveRules), ok only once stored, refusals
   in the page's words, a repeated request answered once, and the cloud-voice key never in a projection.
   Run: NODE_PATH=$(npm root -g) node tests/native-settings-fields.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
const android = require('./fixtures/settings-android.js');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 1200) : '')) } };

const HELPERS = `
  var wait = ms => new Promise(r => setTimeout(r, ms)), P = Capacitor.Plugins.NativeSettings;
  var of = (tab, k) => window.__calls.filter(c => c[0] === tab && c[1] === k);
  var showing = () => { let o = -1, c = -1; window.__calls.forEach((x, i) => { if (x[0] === 'settings' && x[1] === 'open') o = i; if (x[0] === 'settings' && x[1] === 'close') c = i }); return o > c };
  var latest = () => [...window.__calls].reverse().find(c => c[0] === 'settings' && (c[1] === 'open' || c[1] === 'update'))[2].snapshot;
  var until = async (f, ms = 5000) => { for (let t = 0; t < ms && !f(); t += 50) await wait(50); return !!f() };
  var send = async (command, payload, requestId) => { requestId = requestId || 'q' + (window.__rid = (window.__rid || 0) + 1); const [reply] = await P.__emit('command', { command, payload, requestId }); return reply };
  var controls = s => s.cards.flatMap(c => c.rows.flatMap(r => r.type === 'tab' ? [r.earlier, r.later, r.show].filter(Boolean) : r.controls || []));
  var ctl = (s, id) => controls(s).find(x => x.id === id);
  var prefs = () => JSON.parse(localStorage.getItem(LMA_PREF_KEY));
  var core = () => JSON.parse(localStorage.getItem(STORE));
  var open = async () => { const S = lmaLoad(); S.nativeOverview = false; S.nativeSettings = true; lmaSave(); navigate('settings'); await until(showing, 8000); await wait(1500); };
  lmaThumbWarm = async () => { };
`;

async function visit(b, settingsCaps) {
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); p.errs = []; p.on('pageerror', e => p.errs.push(String(e))); p.on('dialog', d => d.dismiss().catch(() => { }));
  await p.addInitScript(android.script({ settingsCaps })); await p.goto('file://' + file); await p.waitForTimeout(3500);
  await p.evaluate(async (H) => { eval(H); await open(); }, HELPERS);
  return p;
}

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });

  /* ---------- a screen that does not say it edits fields: the projection is the old one, the commands are refused ---------- */
  let p = await visit(b, []);
  const old = await p.evaluate(async (H) => {
    eval(H); const s = latest(), fields = controls(s).filter(k => k.kind === 'field');
    const reply = await send('saveForm', { group: 'profile', values: { characterName: 'X' } }), set = await send('setField', { id: 'reminders.workoutTime', value: '18:00' });
    return { n: fields.length, ops: [...new Set(fields.map(k => k.op))], keys: [...new Set(fields.flatMap(k => Object.keys(k)))].sort(), saves: controls(s).filter(k => k.kind === 'button' && /^Save/.test(k.label)).map(k => k.op),
      ids: fields.filter(k => /Notif/.test(k.id)).map(k => k.id), reply, set, name: core().name };
  }, HELPERS);
  ok('without the capability every field is a hand-over', old.n >= 17 && old.ops.join() === 'reveal', old.ops);
  ok('and carries none of the new keys', old.keys.join() === 'enabled,id,input,kind,label,op,placeholder,value', old.keys);
  ok('the save buttons are hand-overs too, and the times keep their positional ids', old.saves.every(o => o === 'reveal') && old.ids[0] === 'lmaNotifCard/0', [old.saves, old.ids]);
  ok('commands are refused and nothing is changed', !old.reply.ok && !old.set.ok && old.name === 'N', [old.reply, old.set]);
  await p.context().close();

  /* ---------- with it ---------- */
  p = await visit(b, ['settings.fields.v1']);
  const r = await p.evaluate(async (H) => {
    eval(H); const out = {}, s = latest(), all = controls(s);
    const f = id => ctl(s, id);
    out.edit = all.filter(k => k.kind === 'field').map(k => [k.id, k.op, k.group, k.input]);
    out.limits = { name: [f('#characterName').maxLength], weight: [f('#characterWeight').min, f('#characterWeight').max, f('#characterWeight').step], grace: [f('#decayGrace').min, f('#decayGrace').max, f('#decayGrace').step], rule: [f('#rule-reading').min, f('#rule-reading').max], ft: [f('#characterHeightFt').min, f('#characterHeightFt').max], time: [f('reminders.workoutTime').min, f('reminders.workoutTime').group] };
    out.saves = all.filter(k => k.kind === 'button' && /^Save/.test(k.label)).map(k => [k.id, k.op, k.group, k.label]);
    out.reset = all.filter(k => /Reset defaults/.test(k.label)).map(k => k.op);
    out.sliders = all.filter(k => /^#swipeTune/.test(k.id)).map(k => k.op);
    return out;
  }, HELPERS);
  const kinds = r.edit.map(e => e[1] + ':' + e[2]);
  ok('the times, the profile, the decay and the nine XP rules are editable', r.edit.length === 4 + 4 + 2 + 9 && r.edit.every(e => e[1] === 'edit'), r.edit);
  ok('times are immediate, the others belong to their form', JSON.stringify([...new Set(kinds)].sort()) === JSON.stringify(['edit:', 'edit:decay', 'edit:profile', 'edit:rules']), kinds);
  ok('the times have stable ids', r.edit.filter(e => e[3] === 'time').map(e => e[0]).join() === 'reminders.workoutTime,reminders.checkinTime,reminders.quietFrom,reminders.quietTo', r.edit);
  ok('limits come from the page\'s inputs', JSON.stringify(r.limits) === JSON.stringify({ name: [40], weight: [1, 1400, 0.1], grace: [1, 14, null], rule: [1, 200], ft: [1, 8], time: [null, ''] }), r.limits);
  ok('the three Save buttons say which form they save, the reset stays a hand-over', JSON.stringify(r.saves) === JSON.stringify([['save.profile', 'save', 'profile', 'Save character'], ['save.decay', 'save', 'decay', 'Save decay settings'], ['save.rules', 'save', 'rules', 'Save XP rules']]) && r.reset.join() === 'reveal', [r.saves, r.reset]);

  /* a time: the page's lmaSet, stored, a repeat, an unchanged value, refusals */
  const t = await p.evaluate(async (H) => {
    eval(H); const out = {};
    const had = prefs().notif.workoutTime;
    out.set = await send('setField', { id: 'reminders.workoutTime', value: '18:45' }, 'time-1');
    out.stored = prefs().notif.workoutTime; out.had = had;
    prefs().notif.workoutTime = '19:00'; const n = lmaLoad(); n.notif.workoutTime = '19:00'; lmaSave(); // the page moves on
    lmaRenderSettings(); await wait(100); out.again = await send('setField', { id: 'reminders.workoutTime', value: '18:45' }, 'time-1');
    out.afterRepeat = prefs().notif.workoutTime;
    out.same = await send('setField', { id: 'reminders.workoutTime', value: '19:00' });
    out.bad = await send('setField', { id: 'reminders.workoutTime', value: '25:00' });
    out.bad2 = await send('setField', { id: 'reminders.workoutTime', value: 1830 });
    out.unknown = await send('setField', { id: '#characterName', value: '10:00' });
    out.reused = await send('setField', { id: 'reminders.checkinTime', value: '21:00' }, 'time-1');
    await wait(300); out.projected = ctl(latest(), 'reminders.workoutTime').value;
    // quiet hours: two fields, each its own
    out.quiet = [await send('setField', { id: 'reminders.quietFrom', value: '23:00' }), await send('setField', { id: 'reminders.quietTo', value: '06:30' })];
    out.quietStored = [prefs().notif.quietFrom, prefs().notif.quietTo];
    // a disabled field (reminders off) is refused
    await send('toggle', { id: 'reminders', on: false }); await wait(200);
    out.off = await send('setField', { id: 'reminders.checkinTime', value: '21:15' });
    return out;
  }, HELPERS);
  ok('a time is set by the page\'s own handler and stored', t.set.ok && t.set.changed && t.stored === '18:45' && t.had === '17:00', t);
  ok('a request seen twice is answered again, not run again', t.again.ok && t.again.changed === t.set.changed && t.afterRepeat === '19:00', [t.again, t.afterRepeat]);
  ok('the same value is not a change', t.same.ok && t.same.changed === false, t.same);
  ok('a time that is not one, a number, another field are refused in words', !t.bad.ok && /not a time/.test(t.bad.message) && !t.bad2.ok && !t.unknown.ok && /not on the page/.test(t.unknown.message), [t.bad, t.bad2, t.unknown]);
  ok('a request id reused for another request is refused', !t.reused.ok && /already answered/.test(t.reused.message), t.reused);
  ok('the next projection holds the stored time', t.projected === '19:00', t.projected);
  ok('quiet from and to are set one by one', t.quiet.every(x => x.ok && x.changed) && t.quietStored.join() === '23:00,06:30', [t.quiet, t.quietStored]);
  ok('a field the page has disabled is refused', !t.off.ok && /cannot be changed/.test(t.off.message), t.off);

  /* the profile form */
  const f = await p.evaluate(async (H) => {
    eval(H); const out = {};
    out.save = await send('saveForm', { group: 'profile', values: { characterName: '  Ada  ', characterWeight: '175.5', characterHeightFt: '5', characterHeightIn: '11' } });
    const c = core(); out.state = [c.name, c.weight, c.heightFt, c.heightIn];
    out.nothing = await send('saveForm', { group: 'profile', values: { characterName: 'Ada', characterWeight: '175.5', characterHeightFt: '5', characterHeightIn: '11' } });
    out.empty = await send('saveForm', { group: 'profile', values: { characterName: '', characterWeight: '', characterHeightFt: '', characterHeightIn: '' } });
    const e = core(); out.cleared = [e.name, e.weight, e.heightFt, e.heightIn];
    out.badNumber = await send('saveForm', { group: 'profile', values: { characterWeight: '1e3' } });
    out.long = await send('saveForm', { group: 'profile', values: { characterName: 'x'.repeat(41) } });
    out.foreign = await send('saveForm', { group: 'profile', values: { decayGrace: '3' } });
    out.group = await send('saveForm', { group: 'nope', values: {} });
    out.shape = [await send('saveForm', { group: 'profile', values: [] }), await send('saveForm', { group: 'profile', values: { characterName: 5 } }), await send('saveForm', { group: 'profile' })];
    out.unchanged = core().name;
    const key = ctl(latest(), 'save.profile'); out.press = await send('press', { id: 'save.profile' });
    return out;
  }, HELPERS);
  ok('the profile is saved by the page\'s own saveCharacter, in its words', f.save.ok && f.save.changed && f.save.message === 'Character saved' && JSON.stringify(f.state) === JSON.stringify(['Ada', 175.5, 5, 11]), [f.save, f.state]);
  ok('saving the same values again is not a change', f.nothing.ok && f.nothing.changed === false, f.nothing);
  ok('empty boxes become what the page makes of them', JSON.stringify(f.cleared) === JSON.stringify(['The Life Architect', null, null, 0]), f.cleared);
  ok('a malformed number, an over-long name, a field of another form, an unknown form or a bad shape are refused and change nothing',
    [f.badNumber, f.long, f.foreign, f.group, ...f.shape].every(x => !x.ok && x.changed === false) && f.unchanged === 'The Life Architect', [f.badNumber, f.long, f.foreign, f.group, f.shape]);
  ok('a Save button is not pressed by a command', !f.press.ok, f.press);

  /* the decay settings and the XP rules: the page clamps */
  const d = await p.evaluate(async (H) => {
    eval(H); const out = {};
    out.decay = await send('saveForm', { group: 'decay', values: { decayGrace: '99', decayRate: '0' } });
    out.state = [core().decay.grace, core().decay.rate, core().decay.on];
    out.rules = await send('saveForm', { group: 'rules', values: { 'rule-reading': '500', 'rule-business': '', 'rule-strength': '30' } });
    const c = core(); out.base = [c.customRules.reading.base, c.customRules.business.base, c.customRules.strength.base, c.customRules.running.base];
    await wait(300);
    const s = latest(); out.projected = [ctl(s, '#decayGrace').value, ctl(s, '#rule-reading').value, ctl(s, '#rule-business').value];
    return out;
  }, HELPERS);
  ok('decay settings are clamped by the page (1 to 14; 0 is its default 3)', d.decay.ok && d.decay.message === 'Decay settings saved' && JSON.stringify(d.state) === JSON.stringify([14, 3, true]), [d.decay, d.state]);
  ok('XP rules are clamped by the page (1 to 200, empty is 1) and the others kept', d.rules.ok && d.rules.message === 'XP rules saved' && JSON.stringify(d.base) === JSON.stringify([200, 1, 30, 26]), [d.rules, d.base]);
  ok('the next projection holds what the page stored', JSON.stringify(d.projected) === JSON.stringify(['14', '200', '1']), d.projected);

  /* a save that did not reach the device: not ok, not saved, in the page's words, the page's value kept for the screen to compare */
  const w = await p.evaluate(async (H) => {
    eval(H); const out = {}, real = Storage.prototype.setItem;
    Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('full'); return real.apply(this, arguments) };
    try { out.full = await send('saveForm', { group: 'profile', values: { characterName: 'Zed' } }); } finally { Storage.prototype.setItem = real; }
    out.stored = core().name;
    Storage.prototype.setItem = function (k) { if (k === LMA_PREF_KEY) throw new Error('full'); return real.apply(this, arguments) };
    try { { const n = lmaLoad().notif; n.on = true; n.checkin = true; lmaSave(); lmaRenderSettings(); await wait(300) } out.time = await send('setField', { id: 'reminders.checkinTime', value: '05:05' }); } finally { Storage.prototype.setItem = real; }
    return out;
  }, HELPERS);
  ok('a full device is reported as not saved, never ok', !w.full.ok && w.full.saved === false && /Could not save/.test(w.full.message) && w.stored !== 'Zed', [w.full, w.stored]);
  ok('a time that was not stored is reported the same way', !w.time.ok && w.time.saved === false, w.time);

  /* the secrets rule: the cloud-voice key is on the page only */
  const k = await p.evaluate(async (H) => {
    eval(H); const out = {}, SECRET = 'AIzaSyHIDDEN-key-1234567890';
    localStorage.setItem('lifemax_gemini_ui', '1'); localStorage.setItem('lifemax_gemini_key_v1', SECRET); localStorage.setItem('lifemax_gemini_on_v1', 'true');
    lmvSettingsRender(); await wait(300);
    out.onPage = !!document.querySelector('#lmvSettingsBody input[type=password]') && document.querySelector('#lmvSettingsBody input[type=password]').value === SECRET;
    const sent = JSON.stringify(window.__calls.filter(c => c[0] === 'settings').map(c => c[2]));
    out.leak = sent.includes(SECRET) || JSON.stringify(NativeSettingsTabProjection.snapshot()).includes(SECRET);
    const s = NativeSettingsTabProjection.snapshot(); out.passwords = controls(s).filter(x => /key/i.test(x.label || '') || x.input === 'password').length;
    out.cmd = [await send('setField', { id: '#lmvSettingsBody input', value: 'x' }), await send('saveForm', { group: 'profile', values: { 'lmvKey': 'x' } })];
    out.keyKept = localStorage.getItem('lifemax_gemini_key_v1') === SECRET;
    return out;
  }, HELPERS);
  ok('the key row is shown on the page', k.onPage, k);
  ok('the key is in no projection and no control for it exists', !k.leak && k.passwords === 0, k);
  ok('no command can reach it', k.cmd.every(x => !x.ok) && k.keyKept, k.cmd);

  ok('no page errors', p.errs.length === 0, p.errs);
  await b.close();
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); process.exit(fail ? 1 : 0);
})();
