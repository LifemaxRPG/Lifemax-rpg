/* Native Settings, web half: the projection a native screen draws from (checked against the page's own Settings, card by card),
   each command against what the page's own control does, each delegation (the screen closes, the page shows the card with the
   control focused, and the screen comes back once the owner's tap there has done its work), the covering rules (never over a
   sheet, a picker, the import, a field being typed in), refused saves, the native tabs' switches (the page's own
   setNativeXEnabled, and no native tab left open over Settings) and the hand-over from a native tab's gear. Fake plugins stand in
   for Android (tests/fixtures/settings-android.js). Run: NODE_PATH=$(npm root -g) node tests/native-settings.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
const android = require('./fixtures/settings-android.js');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 1200) : '')) } };

/* the helpers every evaluate starts with (var: they must outlive the eval that declares them) */
const HELPERS = `
  var wait = ms => new Promise(r => setTimeout(r, ms)), P = Capacitor.Plugins.NativeSettings;
  var of = (tab, k) => window.__calls.filter(c => c[0] === tab && c[1] === k);
  var count = (k, tab = 'settings') => of(tab, k).length;
  var showing = (tab = 'settings') => { let o = -1, c = -1; window.__calls.forEach((x, i) => { if (x[0] === tab && x[1] === 'open') o = i; if (x[0] === tab && x[1] === 'close') c = i }); return o > c };
  var latest = () => [...window.__calls].reverse().find(c => c[0] === 'settings' && (c[1] === 'open' || c[1] === 'update'))[2].snapshot;
  var until = async (f, ms = 5000) => { for (let t = 0; t < ms && !f(); t += 50) await wait(50); return !!f() };
  var back = () => until(showing, 3000);
  var away = async (ms = 700) => { await wait(ms); return !showing() };
  // every request an id of its own for the page's life, as the screen gives them (the page answers a repeated id from its receipt)
  var command = async (command, payload) => { const requestId = 'q' + (window.__rid = (window.__rid || 0) + 1); const [reply] = await P.__emit('command', { command, payload, requestId }); return reply };
  var act = async (e, ms = 250) => { await P.__emit('action', e); await wait(ms) };
  var card = (s, id) => s.cards.find(c => c.id === id);
  var controls = s => s.cards.flatMap(c => c.rows.flatMap(r => r.type === 'tab' ? [r.earlier, r.later, r.show].filter(Boolean) : r.controls || []));
  var ctl = (s, id) => controls(s).find(x => x.id === id);
  var idOf = (s, cardId, title, i = 0) => card(s, cardId).rows.find(x => x.title === title).controls[i].id;
  var text = el => el ? el.textContent.replace(/\\s+/g, ' ').trim() : '';
  var prefs = () => JSON.parse(localStorage.getItem(LMA_PREF_KEY));
  var core = () => JSON.parse(localStorage.getItem(STORE));
  lmaThumbWarm = async () => { };
`;

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  p.on('dialog', d => d.dismiss().catch(() => { }));
  await p.addInitScript(android.script());
  await p.goto('file://' + file); await p.waitForTimeout(3500);

  /* ---------- the setting, and the projection against the page ---------- */
  const r = await p.evaluate(async (H) => {
    eval(H); const out = {};
    const S = lmaLoad(); S.nativeOverview = false; lmaSave();
    navigate('settings'); await wait(300);
    const toggle = document.getElementById('nativeSettingsToggle');
    out.settingOff = !!toggle && !toggle.checked && !toggle.disabled && lmaLoad().nativeSettings !== true
      && text(toggle.closest('.settingsline').querySelector('strong')) === 'Native Settings (beta)' && count('open') === 0;
    toggle.click(); await until(showing);
    out.settingOn = lmaLoad().nativeSettings === true && prefs().nativeSettings === true && showing(); // the screen comes at once

    await wait(1200); // the renderers that wait for a plugin (permission, widget, food count) have drawn, and the screen has followed
    const s = latest(), view = document.getElementById('view-settings');
    out.opened = s.protocol === 1 && typeof s.webSessionId === 'string' && typeof s.tokens.accent === 'number' && s.light === false;
    out.head = JSON.stringify([s.head.eyebrow, s.head.title, s.head.blurb]) === JSON.stringify([...view.querySelectorAll('.pagehead .eyebrow, .pagehead h2, .pagehead p')].map(text));
    out.categories = JSON.stringify(s.categories) === JSON.stringify(SET_CATS.map(c => ({ id: c.id, name: c.name }))) && s.category === setCat
      && JSON.stringify([...document.querySelectorAll('#setCatBar button')].map(text)) === JSON.stringify(s.categories.map(c => c.name));
    const pageCards = [...view.querySelectorAll('.card')].filter(c => !c.parentElement.closest('.card'));
    out.cards = pageCards.length === s.cards.length && pageCards.every((c, i) => text(c.querySelector(':scope > h3')) === s.cards[i].title && (setCatOf(c) || '') === s.cards[i].category);
    out.cardCount = [pageCards.length, s.cards.length];
    // every row the page shows, by its title and hint, in its card; the hidden ones (dev tools while off) are left out
    const visible = el => { for (let e = el; e && e !== view; e = e.parentElement) if (e.hidden || e.style.display === 'none') return false; return true };
    const lines = c => [...c.querySelectorAll('.settingsline:not(.tabrow)')].filter(visible).filter(l => !l.querySelector('input[type=password]'))
      .map(l => text(l.querySelector('strong')) + '|' + [...l.querySelectorAll('.muted')].filter(m => !m.parentElement.closest('.muted') && visible(m)).map(text).filter(Boolean).join(' '));
    const rows = c => c.rows.filter(x => x.type === 'row' && !x.controls.some(k => k.kind === 'avatar')).map(x => x.title + '|' + x.hint.replace(/\n/g, ' '));
    out.rowsMatch = pageCards.map((c, i) => [s.cards[i].id, JSON.stringify(lines(c).filter(l => !/^Profile picture/.test(l))) === JSON.stringify(rows(s.cards[i]))]).filter(x => !x[1]).map(x => x[0]);
    // every control the page shows, in order: checkboxes, selects with their options, buttons with their words
    const pageControls = c => [...c.querySelectorAll('input:not([type=file]):not([type=password]), select, button')].filter(visible)
      .filter(e => !e.closest('[role=group]')).map(e => e.matches('input[type=checkbox]') ? 'switch:' + e.checked + ':' + !e.disabled
        : e.matches('select') ? 'select:' + e.value + ':' + [...e.options].map(o => o.value + '=' + o.textContent.trim()).join(',')
        : e.matches('button') ? 'button:' + text(e) + ':' + !e.disabled : 'field:' + e.value);
    const projControls = c => c.rows.flatMap(x => x.type === 'tab' ? [x.earlier, x.later, x.show].filter(Boolean) : x.controls || [])
      .filter(k => k.kind !== 'avatar' && k.kind !== 'choice').map(k => k.kind === 'switch' ? 'switch:' + k.on + ':' + k.enabled
        : k.kind === 'select' ? 'select:' + k.value + ':' + k.options.map(o => o.value + '=' + o.label).join(',')
        : k.kind === 'button' ? 'button:' + k.label + ':' + k.enabled : 'field:' + k.value);
    out.controlsMatch = pageCards.map((c, i) => [s.cards[i].id, JSON.stringify(pageControls(c)) === JSON.stringify(projControls(s.cards[i]))]).filter(x => !x[1]).map(x => x[0]);
    out.controlsDetail = out.controlsMatch.length ? out.controlsMatch.map(id => { const i = s.cards.findIndex(c => c.id === id); return [pageControls(pageCards[i]), projControls(s.cards[i])] }) : null;
    // words only the renderers know, as the page shows them
    out.words = {
      updates: card(s, 'lmaUpdCard').rows[0].title === 'Installed build 290' && card(s, 'lmaUpdCard').rows[0].hint === 'No newer compatible update was reported by the server.\nLatest release: 290 · Android API 28 / requires 28',
      widget: ctl(s, idOf(s, 'lmaWidgetCard', '1 on your home screen')).label === 'Add widget',
      lead: JSON.stringify(ctl(s, 'reminders.lead').options.map(o => o.label)) === '["At start","5 min","10 min","15 min","30 min","60 min"]' && ctl(s, 'reminders.lead').value === '15',
      strength: JSON.stringify(ctl(s, 'haptics.strength').options) === JSON.stringify(Object.keys(HAP_LEVELS).map(k => ({ value: k, label: HAP_LEVELS[k].label }))) && ctl(s, 'haptics.strength').value === 'normal',
      tries: ['tap', 'double', 'success', 'big'].every(k => ctl(s, 'haptics.try.' + k).op === 'press'),
      bar: JSON.stringify(card(s, 'tabsCard').rows.filter(x => x.type === 'tab').map(x => [x.view, x.label, x.icon, x.visible]))
        === JSON.stringify([...lmaTabsButtons().map(b => [b.dataset.view, text(b), b.querySelector('[data-px]').dataset.px, !b.classList.contains('tab-off')]), ['settings', 'Settings', 'gear', false]]),
      backupNote: card(s, 'lmaBakCard').rows[0].type === 'note' && card(s, 'lmaBakCard').rows[0].warn === true && card(s, 'lmaBakCard').rows[0].text === lmaBackupWarning(),
      intro: card(s, 'lmaHealthCard').intro === 'Turns your Samsung Health activity into XP automatically, through Android’s Health Connect.',
      danger: card(s, 'data-management').rows[4].danger && ctl(s, 'data-management/5').style === 'danger' && ctl(s, 'data-management/0').style === 'secondary',
      fields: JSON.stringify(card(s, 'character-profile').rows.filter(x => x.type === 'field').map(x => x.label)) === '["Character name","Current build","Weight (lb)","Height"]'
        && ctl(s, '#characterName').value === 'N' && ctl(s, '#characterName').op === 'reveal',
      avatar: (k => k.kind === 'avatar' && k.initial === 'N' && k.op === 'reveal')(ctl(s, '#charAvatar')),
    };
    out.ops = {
      bound: ['lightMode', 'swipe', 'scrubSpeed', 'cardEffects', 'questSounds', 'cardDeal', 'cardArt', 'workoutScreen', 'diagnostics3d', 'haptics', 'hdCat', 'voice', 'reminders.sessions', 'reminders.lead', 'nativeCharacter', 'nativeSettings']
        .every(id => ['toggle', 'choose'].includes(ctl(s, id) && ctl(s, id).op)),
      permission: ctl(s, 'reminders').op === 'toggle' && ctl(s, 'calendar').op === 'reveal' && ctl(s, 'health').op === 'reveal', // reminders are on: off is a command
      delegated: ['#jlToggle', '#deviceBackupToggle', '#devModeOn', '#decayOn', '#characterBuild', idOf(s, 'lmaNotifCard', 'Workout time'), 'rewards/0', 'data-management/3', idOf(s, 'gwrCard', 'Trainer pack')].every(id => ctl(s, id) && ctl(s, id).op === 'reveal'),
    };
    out.unnamed = controls(s).filter(k => ['switch', 'select', 'field', 'choice'].includes(k.kind) && !k.label).map(k => k.id);
    out.size = JSON.stringify(s).length;
    // the hidden cloud-voice rows: their key never reaches the screen
    localStorage.setItem('lifemax_gemini_ui', '1'); localStorage.setItem('lifemax_gemini_key_v1', 'AIza-SECRET-KEY-123'); lmvSettingsRender(); await wait(300);
    const g = JSON.stringify(latest());
    out.secret = !g.includes('AIza-SECRET-KEY-123') && card(latest(), 'lmvCard').rows.some(x => x.title === 'Gemini understanding (online)' && x.controls[0].op === 'reveal');
    localStorage.removeItem('lifemax_gemini_ui'); localStorage.removeItem('lifemax_gemini_key_v1'); lmvSettingsRender(); await wait(200);
    // an asynchronous fact arrives with the renderer that learns it
    Capacitor.Plugins.WidgetBridge.status = async () => ({ count: 2, canPin: true }); lmaWidgetRender(); await wait(300);
    out.async = card(latest(), 'lmaWidgetCard').rows.some(x => x.title === '2 on your home screen');
    // the page's toast, under the screen, comes with the projection
    toast('Badge unlocked: Tinkerer'); await wait(300);
    out.notice = latest().notice && latest().notice.text === 'Badge unlocked: Tinkerer';
    return out;
  }, HELPERS);
  ok('Settings shows "Native Settings (beta)", off by default; turned on, the screen comes at once', r.settingOff && r.settingOn, r);
  ok('opening sends the projection with the page\'s palette', r.opened, r);
  ok('the head and the categories are the page\'s', r.head && r.categories, r);
  ok('every card, in the page\'s order, with its title and category', r.cards, r.cardCount);
  ok('every row is worded as the page words it (title and hint)', r.rowsMatch && r.rowsMatch.length === 0, r.rowsMatch);
  ok('every control shows the page\'s state, options and words', r.controlsMatch && r.controlsMatch.length === 0, r.controlsDetail);
  for (const [k, v] of Object.entries(r.words || {})) ok('words the renderers write: ' + k, v, r.words);
  ok('the easy settings are commands; a switch asking Android first is a delegation to turn on; the rest are delegations', r.ops.bound && r.ops.permission && r.ops.delegated, r.ops);
  ok('every switch, select, field and choice has a name for TalkBack', r.unnamed.length === 0, r.unnamed);
  ok('the hidden cloud-voice key never reaches the screen', r.secret, r);
  ok('a fact a renderer learns later arrives with the next projection', r.async, r);
  ok('the page\'s toast comes with the projection', r.notice, r);
  ok('the projection stays small (' + r.size + ' characters)', r.size < 120000, r.size);
  // with developer mode on (its swipe feel sliders among its tools), every field is a kind the screen draws: an unknown kind makes
  // the screen refuse the whole projection
  const kinds = await p.evaluate(async (H) => {
    eval(H);
    const dev = document.getElementById('devModeOn'); dev.checked = true; dev.dispatchEvent(new Event('change')); await wait(400);
    const fields = controls(latest()).filter(k => k.kind === 'field');
    dev.checked = false; dev.dispatchEvent(new Event('change')); await wait(300);
    return { odd: fields.filter(k => !['text', 'number', 'time'].includes(k.input)).map(k => k.id + ':' + k.input), sliders: fields.filter(k => /swipeTune|tune/i.test(k.id)).length };
  }, HELPERS);
  ok('every field is one the screen draws, developer mode\'s sliders included', kinds.odd.length === 0 && kinds.sliders > 0, kinds);

  /* ---------- commands against the page's own result ---------- */
  const c = await p.evaluate(async (H) => {
    eval(H); const out = {};
    const tokens = latest().tokens;
    let q = await command('toggle', { id: 'swipe', on: false });
    out.swipe = q.ok && q.changed && q.message === 'Swipe to switch tabs: off' && !lmaSwipeEnabled() && prefs().swipe === false && !document.getElementById('lmaSwipeToggle').checked
      && ctl(latest(), 'swipe').on === false && latest().notice === null; // the toast went back with the reply, not as a notice
    q = await command('toggle', { id: 'swipe', on: false }); out.repeat = q.ok && q.changed === false;
    q = await command('toggle', { id: 'swipeAnimation', on: false }); out.swipeAnim = q.ok && lmaLoad().swipeAnim === false && prefs().swipeAnim === false;
    q = await command('toggle', { id: 'cardEffects', on: false }); out.cardEffects = q.ok && q.message === 'Quest effects: off' && QFX.enabled() === false && localStorage.getItem('lma_qfx') === 'off';
    q = await command('toggle', { id: 'voice', on: false }); out.voice = q.ok && lmvEnabled() === false && localStorage.getItem('lifemax_voice_enabled') === 'false';
    q = await command('toggle', { id: 'hdCat', on: false }); out.hdCat = q.ok && petHdOn() === false && core().hdCat === false;
    q = await command('toggle', { id: 'haptics', on: false }); out.haptics = q.ok && fxOpt().haptics === false && core().fx.haptics === false && ctl(latest(), 'haptics.strength').enabled === false;
    q = await command('choose', { id: 'haptics.strength', value: 'strong' }); out.strengthOff = !q.ok && fxOpt().hapticLevel !== 'strong'; // off: the page's buttons are disabled
    await command('toggle', { id: 'haptics', on: true });
    q = await command('choose', { id: 'haptics.strength', value: 'strong' }); out.strength = q.ok && fxOpt().hapticLevel === 'strong' && core().fx.hapticLevel === 'strong' && ctl(latest(), 'haptics.strength').value === 'strong';
    const played = []; const realPlay = hapPlay; hapPlay = function (n) { played.push(n); return realPlay.apply(this, arguments) };
    q = await command('press', { id: 'haptics.try.success' }); hapPlay = realPlay; out.tryHaptic = q.ok && JSON.stringify(played) === '["success"]';
    q = await command('toggle', { id: 'lightMode', on: true }); out.light = q.ok && q.message === 'Light mode enabled' && document.documentElement.classList.contains('light-theme')
      && localStorage.getItem('lifeMaxTheme') === 'light' && latest().light === true && latest().tokens.bg !== tokens.bg;
    await command('toggle', { id: 'lightMode', on: false });
    q = await command('choose', { id: 'scrubSpeed', value: 'faster' }); out.scrub = q.ok && lmaLoad().scrubSpeed === 'faster' && prefs().scrubSpeed === 'faster' && document.getElementById('lmaScrubSpeed').value === 'faster';
    q = await command('choose', { id: 'questSounds', value: '0.35' }); out.sounds = q.ok && QSFX.vol() === 0.35;
    q = await command('choose', { id: 'cardDeal', value: 'quick' }); out.deal = q.ok && dhDealMode() === 'quick' && q.message === 'Card deal: quick';
    const pack = Object.keys(QART_PACKS)[0];
    q = await command('choose', { id: 'cardArt', value: pack }); out.art = q.ok && qartPref() === pack;
    q = await command('choose', { id: 'workoutScreen', value: 'off' }); out.workout = q.ok && g3Pref() === false;
    q = await command('choose', { id: 'diagnostics3d', value: 'on' }); out.diag = q.ok && g3DbgOn() === true;
    q = await command('choose', { id: 'reminders.lead', value: '30' }); out.lead = q.ok && lmaLoad().notif.lead === 30 && prefs().notif.lead === 30;
    q = await command('toggle', { id: 'reminders.checkin', on: true }); out.checkin = q.ok && lmaLoad().notif.checkin === true;
    q = await command('choose', { id: 'cardDeal', value: 'nonsense' }); out.badValue = !q.ok && dhDealMode() === 'quick';
    q = await command('toggle', { id: 'nope', on: true }); out.unknown = !q.ok;
    q = await command('toggle', { id: 'swipe', on: 'yes' }); out.badType = !q.ok && !lmaSwipeEnabled();
    q = await command('frobnicate', {}); out.unknownCommand = !q.ok && q.message === 'Unknown command';
    // a switch that asks Android first: off by command, on only on the page
    q = await command('toggle', { id: 'reminders', on: false }); await wait(300);
    out.remindersOff = q.ok && lmaLoad().notif.on === false && ctl(latest(), 'reminders').op === 'reveal' && ctl(latest(), 'reminders.sessions').enabled === false;
    q = await command('toggle', { id: 'reminders', on: true }); out.remindersOn = !q.ok && q.reveal === 'reminders' && lmaLoad().notif.on === false;
    q = await command('toggle', { id: 'calendar', on: true }); out.calendarOn = !q.ok && q.reveal === 'calendar' && !lmcLoad().on;
    // health types follow the master switch, as on the page
    q = await command('toggle', { id: 'health.sleep', on: false }); out.healthOff = !q.ok && lmaHealthCfg().sleep === true;
    state.health.on = true; lmaHealthRender(); await wait(200);
    q = await command('toggle', { id: 'health.sleep', on: false }); out.health = q.ok && lmaHealthCfg().sleep === false && core().health.sleep === false;
    q = await command('choose', { id: 'health.goal', value: '10000' }); out.goal = q.ok && lmaHealthCfg().goal === 10000;
    q = await command('toggle', { id: 'health', on: false }); out.healthMaster = q.ok && lmaHealthCfg().on === false;
    // backup choices need a folder, as on the page
    q = await command('choose', { id: 'backup.days', value: '7' }); out.backupNoFolder = !q.ok && lmaLoad().backup.days === 1;
    Object.assign(lmaLoad().backup, { uri: 'content://tree/backups', folder: 'Drive', verified: Date.now(), last: Date.now(), status: 'ok' }); lmaSave(); lmaRenderBackup();
    q = await command('choose', { id: 'backup.days', value: '7' }); out.backupDays = q.ok && lmaLoad().backup.days === 7 && prefs().backup.days === 7;
    q = await command('toggle', { id: 'backup', on: true }); out.backupOn = q.ok && lmaLoad().backup.on === true;
    // the category chip: the page's own handler keeps it for the page too
    q = await command('category', { id: 'phone' }); out.category = q.ok && setCat === 'phone' && localStorage.getItem('lma_set_cat') === 'phone' && latest().category === 'phone';
    q = await command('category', { id: 'nowhere' }); out.badCategory = !q.ok && setCat === 'phone';

    // the bottom bar: the page's own arrows, boxes and reset; the rule "keep at least two" is the page's
    const order = () => lmaTabsButtons().map(b => b.dataset.view);
    const before = order(), want = before.slice(); want.splice(3, 0, want.splice(2, 1)[0]);
    q = await command('press', { id: 'tabs.' + before[2] + '.later' }); out.move = q.ok && JSON.stringify(order()) === JSON.stringify(want) && JSON.stringify(prefs().tabs.order) === JSON.stringify(want);
    // the same request twice (a retry on the way): answered twice, the tab moved once; the same id for another request is refused
    const once = order(), oneMove = once.slice(); oneMove.splice(3, 0, oneMove.splice(2, 1)[0]);
    const same = { command: 'press', payload: { id: 'tabs.' + once[2] + '.later' }, requestId: 'same-1' };
    const [first] = await P.__emit('command', same), [again] = await P.__emit('command', same);
    const [reused] = await P.__emit('command', { command: 'press', payload: { id: 'tabs.' + oneMove[0] + '.later' }, requestId: 'same-1' });
    out.pressOnce = first.ok && first.changed && again.ok && again.changed && again.requestId === 'same-1' && JSON.stringify(order()) === JSON.stringify(oneMove)
      && JSON.stringify(prefs().tabs.order) === JSON.stringify(oneMove) && !reused.ok && reused.requestId === 'same-1';
    q = await command('press', { id: 'tabs.' + order()[0] + '.earlier' }); out.moveFirst = !q.ok; // the first tab's earlier arrow is disabled on the page
    q = await command('toggle', { id: 'tabs.pet.show', on: false }); out.hide = q.ok && lmaTabsButtons().find(b => b.dataset.view === 'pet').classList.contains('tab-off') && prefs().tabs.hidden.includes('pet');
    const shown = () => lmaTabsButtons().filter(b => !b.classList.contains('tab-off')).map(b => b.dataset.view);
    for (const v of shown().slice(2)) await command('toggle', { id: 'tabs.' + v + '.show', on: false });
    const two = shown();
    q = await command('toggle', { id: 'tabs.' + two[0] + '.show', on: false });
    out.keepTwo = two.length === 2 && !q.ok && q.message === 'Keep at least two tabs in the bar' && JSON.stringify(shown()) === JSON.stringify(two)
      && card(latest(), 'tabsCard').rows.find(x => x.view === two[0]).show.on === true;
    q = await command('toggle', { id: 'tabs.settings.show', on: true }); out.extra = q.ok && !!document.querySelector('.sidebar .navbtn[data-view="settings"]') && lmaLoad().tabs.extra.includes('settings');
    const inBar = card(latest(), 'tabsCard').rows.find(x => x.view === 'settings');
    out.extraInBar = inBar.icon === 'gear' && inBar.visible && !!inBar.earlier && !!inBar.later && card(latest(), 'tabsCard').rows.filter(x => x.type === 'tab').every(x => x.icon);
    q = await command('press', { id: 'tabs.reset' }); out.reset = q.ok && q.message === 'Bottom bar reset' && lmaLoad().tabs === null && JSON.stringify(order()) === JSON.stringify(LMA_TAB_DEFAULT) && !document.querySelector('.sidebar .navbtn[data-view="settings"]');
    out.stillShowing = showing();
    return out;
  }, HELPERS);
  for (const [k, v] of Object.entries(c)) ok('command: ' + k, v, c);

  /* ---------- refused saves ---------- */
  const f = await p.evaluate(async (H) => {
    eval(H); const out = {};
    // a read-only save: the page keeps the change in memory, as after its own box; nothing is stored and the reply says why
    saveReadOnlyTold = true; saveReadOnly = 'Your progress was last saved by a newer version of LifeMax.';
    const stored = localStorage.getItem(STORE);
    let q = await command('toggle', { id: 'hdCat', on: true });
    out.readOnly = !q.ok && q.saved === false && q.message === 'Not saved: this version is read-only for your save' && localStorage.getItem(STORE) === stored
      && petHdOn() === true && ctl(latest(), 'hdCat').on === true;
    saveReadOnly = ''; save();
    // a full storage: the core save, the preferences, a plain key
    const real = Storage.prototype.setItem, full = keys => { Storage.prototype.setItem = function (k) { if (keys.includes(k)) throw new DOMException('The quota has been exceeded.', 'QuotaExceededError'); return real.apply(this, arguments) } };
    try {
      full([STORE]); q = await command('toggle', { id: 'haptics', on: false });
      out.fullCore = !q.ok && q.saved === false && q.message === 'Could not save to this browser (storage full?)' && core().fx.haptics === true;
      full([LMA_PREF_KEY]); q = await command('toggle', { id: 'swipe', on: true });
      out.fullPrefs = !q.ok && q.saved === false && q.message === 'Could not save to this browser (storage full?)' && prefs().swipe === false;
      full(['lma_qdeal']); q = await command('choose', { id: 'cardDeal', value: 'off' });
      out.fullKey = !q.ok && q.saved === false && localStorage.getItem('lma_qdeal') === 'quick';
    } finally { Storage.prototype.setItem = real }
    await command('toggle', { id: 'haptics', on: true });
    await command('toggle', { id: 'swipe', on: false }); await command('toggle', { id: 'swipe', on: true });
    out.recovered = core().fx.haptics === true && prefs().swipe === true && showing();
    return out;
  }, HELPERS);
  ok('a read-only save: not stored, and the reply names the reason', f.readOnly, f);
  ok('a full storage: the core save, the preferences and a plain key each report "not saved"', f.fullCore && f.fullPrefs && f.fullKey, f);
  ok('the next saves store again', f.recovered, f);

  /* ---------- the native tabs' switches: the page's own setters, and no native tab over Settings ---------- */
  const t = await p.evaluate(async (H) => {
    eval(H); const out = {}, spied = [], from = window.__calls.length;
    // Log is the kit's tab on by default (NativeTabKit defaultOn): with no key stored the screen shows its switch on, as the page's box
    out.logDefault = lmaLoad().nativeLog === undefined && document.getElementById('nativeLogToggle').checked && ctl(latest(), 'nativeLog').on === true;
    // the Overview cards' switch (beta): a bound switch, off by default, enabled because the plugin exists (the kit's sync)
    const cardsRow = card(latest(), 'appearance') && card(latest(), 'appearance').rows.find(x => x.title === 'Native Overview cards (beta)');
    out.cardsRow = !!cardsRow && cardsRow.controls[0].kind === 'switch' && cardsRow.controls[0].id === 'nativeOverviewCards' && cardsRow.controls[0].op === 'toggle'
      && cardsRow.controls[0].on === false && cardsRow.controls[0].enabled === true && lmaLoad().nativeOverviewCards === undefined;
    await setNativeLogEnabled(false); // so every tab starts off (Overview since the start), and each switch below turns on, then off
    const TABS = { nativeCharacter: 'setNativeCharacterEnabled', nativeLog: 'setNativeLogEnabled', nativePlan: 'setNativePlanEnabled', nativeDirector: 'setNativeDirectorEnabled',
      nativeRecipes: 'setNativeRecipesEnabled', nativeLibrary: 'setNativeLibraryEnabled', nativeNutrition: 'setNativeNutritionEnabled', nativeOverview: 'setNativeOverviewEnabled',
      nativeOverviewCards: 'setNativeOverviewCardsEnabled' };
    for (const [id, fn] of Object.entries(TABS)) { const real = window[fn]; window[fn] = function (on) { spied.push(id + '=' + on); return real.apply(this, arguments) } }
    const others = () => ['overview', 'cards', 'character', 'log', 'plan', 'director', 'recipes', 'media', 'food'].filter(x => showing(x) || window.__calls.slice(from).some(c => c[0] === x && c[1] === 'open'));
    const results = {};
    for (const id of Object.keys(TABS)) {
      const on = await command('toggle', { id, on: true }); await wait(100);
      const off = await command('toggle', { id, on: false }); await wait(100);
      results[id] = on.ok && off.ok && lmaLoad()[id] === false && prefs()[id] === false;
    }
    await wait(800); // NativeTabs.heal's moment
    out.results = results;
    out.setters = JSON.stringify(spied) === JSON.stringify(Object.keys(TABS).flatMap(id => [id + '=true', id + '=false']));
    out.noneOver = others().length === 0 && showing() && lmaView() === 'settings';
    out.spied = spied; out.others = others();
    // on for the hand-over below, from the screen
    await command('toggle', { id: 'nativeCharacter', on: true });
    return out;
  }, HELPERS);
  ok('native Log\'s switch shows on from its default, with no key stored', t.logDefault, t);
  ok('the Overview cards\' switch (beta) is a bound switch, off by default and enabled', t.cardsRow, t);
  ok('each native tab\'s switch runs the page\'s own setNativeXEnabled, on and off', t.setters && Object.values(t.results).every(Boolean), t);
  ok('no native tab opens over Settings, and Settings stays', t.noneOver, t);

  /* ---------- delegations ---------- */
  const d = await p.evaluate(async (H) => {
    eval(H); const out = {};
    // a text field: the page on its category with the field focused; typing keeps the screen away; Save brings it back with the page's toast
    let closes = count('close');
    await act({ action: 'reveal', id: '#characterName' });
    const name = document.getElementById('characterName');
    out.field = count('close') === closes + 1 && setCat === 'game' && document.activeElement === name && await away();
    name.value = 'Ada Lovelace'; name.dispatchEvent(new Event('input', { bubbles: true }));
    const saveBtn = [...document.querySelectorAll('#view-settings button')].find(x => text(x) === 'Save character');
    saveBtn.focus(); saveBtn.click();
    out.fieldBack = await back() && state.name === 'Ada Lovelace' && ctl(latest(), '#characterName').value === 'Ada Lovelace' && latest().notice && latest().notice.text === 'Character saved' && latest().category === 'game';
    // a time field (a picker on the phone): away while it has the focus, back when it is left
    lmaSet('on', true); await wait(400);
    await act({ action: 'reveal', id: idOf(latest(), 'lmaNotifCard', 'Workout time') });
    out.time = document.activeElement.matches('#lmaNotifBody input[type=time]') && setCat === 'phone' && await away();
    const time = document.activeElement; time.click(); await wait(500); out.timeStays = !showing(); // the owner taps it: the phone's time picker
    time.blur(); out.timeBack = await back();
    // a select in a form: away until the owner's next tap
    await act({ action: 'reveal', id: '#characterBuild' });
    out.select = document.activeElement.id === 'characterBuild' && await away();
    document.activeElement.blur(); document.body.click(); out.selectBack = await back();
    // a button that opens a sheet: the button is shown; the owner taps it; the screen stays away while the sheet is up
    await act({ action: 'reveal', id: 'rewards/0' });
    const open = document.activeElement;
    out.sheetButton = text(open) === 'Open rewards' && await away(500);
    open.click(); await wait(600);
    out.sheet = !!document.querySelector('.hsheet') && !showing();
    lmaTopOverlay().close(); out.sheetBack = await back();
    // a file button: the picker is the page's; the screen waits until it reports (a choice or a cancel)
    await act({ action: 'reveal', id: 'character-profile/0' });
    out.fileButton = text(document.activeElement) === 'Upload photo';
    const avatarFile = document.getElementById('avatarFile');
    avatarFile.dispatchEvent(new MouseEvent('click', { bubbles: true })); // what the page's button does (no real picker here)
    out.picker = await away(900);
    avatarFile.dispatchEvent(new Event('cancel', { bubbles: true })); out.pickerBack = await back();
    // the JSON import: never covered from the pick until the page is done with it (here: the owner cancels the replace)
    const backup = JSON.stringify({ app: 'Life Max RPG Prototype', version: 1, state: { activities: [{ id: 'x', type: 'reading', duration: 10, date: new Date().toISOString() }], attrXP: {} }, rules: {} });
    let during = null; const realConfirm = window.confirm; window.confirm = () => { during = showing(); return false };
    state.activities.push({ id: 'a1', type: 'reading', name: 'R', icon: 'book', duration: 20, quality: 1, skill: 'Knowledge', primary: 'intelligence', secondary: '', xp: 10, date: new Date().toISOString(), notes: '', xpSplit: [] });
    await act({ action: 'reveal', id: 'data-management/3' });
    const importFile = document.getElementById('importFile');
    importFile.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    const dt = new DataTransfer(); dt.items.add(new File([backup], 'life-max.json', { type: 'application/json' })); importFile.files = dt.files;
    importFile.dispatchEvent(new Event('change', { bubbles: true }));
    await until(() => during !== null, 3000); window.confirm = realConfirm;
    out.import = during === false && await back() && importFile.value === '';
    // a switch that asks Android first, while off: the page's switch, focused
    await act({ action: 'reveal', id: 'reminders' });
    out.permission = document.activeElement.matches('#lmaNotifBody input[aria-label="on"]') && await away();
    document.body.click(); out.permissionBack = await back();
    // a disabled control: its card is shown; the screen comes back after the owner's next tap
    await act({ action: 'reveal', id: idOf(latest(), 'lmaCalCard', 'Last check') });
    out.disabled = await away(); document.body.click(); out.disabledBack = await back();
    // an id the page no longer has: the screen comes straight back
    closes = count('close'); const opens = count('open');
    await act({ action: 'reveal', id: 'gone/9' }, 600); out.gone = count('close') === closes + 1 && count('open') === opens + 1 && showing();
    // the classic page: until the owner leaves Settings
    await act({ action: 'classic' });
    out.classic = await away(); document.body.click(); out.classicStays = await away();
    navigate('dashboard'); navigate('settings'); out.classicBack = await back();
    // the tab previews' capture goes through every tab and back (as lmaThumbWarm does): the owner did not leave, the classic page stays
    await act({ action: 'classic' });
    lmaThumbWarmBusy = true; lmaNavTo('dashboard', { fast: true, skip: true }); await wait(200); lmaNavTo('settings', { fast: true, skip: true }); lmaThumbWarmBusy = false; lmaThumbRender();
    out.classicCapture = lmaView() === 'settings' && await away(900);
    // the switch turned off and on again on the page: the screen comes at once
    const toggle = document.getElementById('nativeSettingsToggle');
    toggle.click(); await wait(300); out.classicOff = lmaLoad().nativeSettings === false && !showing();
    toggle.click(); out.classicSwitch = await back() && lmaLoad().nativeSettings === true;
    return out;
  }, HELPERS);
  for (const [k, v] of Object.entries(d)) ok('delegation: ' + k, v, d);

  /* ---------- covering rules while the screen shows ---------- */
  const v = await p.evaluate(async (H) => {
    eval(H); const out = {};
    await until(showing);
    ecoShopOpen(); await wait(300); out.sheet = !showing();
    lmaTopOverlay().close(); out.sheetBack = await back();
    openDevChat(); await wait(300); out.devChat = !showing();
    document.getElementById('devChat').classList.remove('open'); out.devChatBack = await back();
    const lu = document.createElement('div'); lu.className = 'luov'; document.body.appendChild(lu); await wait(300); out.levelUp = !showing();
    lu.remove(); out.levelUpBack = await back();
    resetTips(); await wait(800); out.tutorial = !showing() && !!document.querySelector('#tutorial.show');
    skipTutorial(); out.tutorialBack = await back();
    // the tab previews go through every tab, and a page about to reload opens nothing
    navigate('dashboard'); lmaThumbWarmBusy = true; navigate('settings'); out.capture = await away(600); lmaThumbWarmBusy = false;
    navigate('dashboard'); lmaReloading = true; navigate('settings'); out.reloading = await away(600); lmaReloading = false;
    navigate('dashboard'); navigate('settings'); out.again = await back();
    return out;
  }, HELPERS);
  for (const [k, val] of Object.entries(v)) ok('covering rule: ' + k, val, v);

  /* ---------- the hand-over from a native tab's gear, and the switch off ---------- */
  const h = await p.evaluate(async (H) => {
    eval(H); const out = {};
    navigate('character'); await until(() => showing('character'));
    out.character = showing('character') && !showing();
    const i0 = window.__calls.length;
    await Capacitor.Plugins.NativeCharacter.__emit('action', { view: 'settings' }); await until(showing); await wait(800);
    const after = window.__calls.slice(i0), openAt = after.findIndex(x => x[0] === 'settings' && x[1] === 'open'), closeAt = after.findIndex(x => x[0] === 'character' && x[1] === 'close');
    out.handover = openAt >= 0 && closeAt > openAt && showing() && !showing('character') && lmaView() === 'settings';
    // turned off from the screen: it closes and the classic page stays
    const q = await command('toggle', { id: 'nativeSettings', on: false }); await wait(300);
    out.off = q.ok && lmaLoad().nativeSettings === false && !showing() && lmaView() === 'settings' && !document.getElementById('nativeSettingsToggle').checked;
    const opens = count('open'); navigate('dashboard'); navigate('settings'); await wait(500); out.offStays = count('open') === opens;
    return out;
  }, HELPERS);
  ok('a native tab\'s gear: Settings opens before that tab closes', h.character && h.handover, h);
  ok('turned off from the screen: it closes, the classic page stays', h.off && h.offStays, h);

  /* ---------- in a browser: nothing to open, the switch is disabled ---------- */
  const p2 = await ctx.newPage(); p2.on('pageerror', e => errs.push('browser: ' + String(e)));
  await p2.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,tipsSeen:{}})); localStorage.setItem('lma_set_cat', 'sound');");
  await p2.goto('file://' + file); await p2.waitForTimeout(2500);
  const w = await p2.evaluate(async () => {
    navigate('settings'); await new Promise(r => setTimeout(r, 300));
    const t = document.getElementById('nativeSettingsToggle'), s = NativeSettingsTabProjection.snapshot();
    const notif = s.cards.find(c => c.id === 'lmaNotifCard');
    // a category the chips do not have (a stale or broken lma_set_cat): the page shows its first, and the projection names a chip
    const shownCards = [...document.querySelectorAll('#view-settings .card')].filter(c => c.offsetParent).map(c => setCatOf(c)).filter(Boolean);
    const broken = setCat === 'look' && s.category === 'look' && shownCards.length > 0 && shownCards.every(c => c === 'look');
    setCat = 'sound'; const forced = NativeSettingsTabProjection.snapshot().category; setCat = 'look';
    return { disabled: t.disabled && !t.checked, notes: notif.rows.length === 1 && notif.rows[0].type === 'note' && /Android app/.test(notif.rows[0].text), cards: s.cards.length, broken, forced };
  });
  ok('a category the chips do not have: the page shows its first chip, and the projection always names a chip', w.broken && w.forced === 'look', w);
  ok('in a browser the switch is disabled, and the projection still reads every card', w.disabled && w.notes && w.cards === 23, w);

  ok('no script errors', errs.length === 0, errs.slice(0, 5));
  await b.close();
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})();
