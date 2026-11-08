/* The economy check's Settings card (docs/specs/ECONOMY_SHADOW_PLAN.md 3.3, WP6e) as the native Settings screen draws it, the shadow
   job of tools/gen_native_fixtures.js: what the page projects (NativeSettingsTabProjection.snapshot()) on Data & advanced at the fixed
   clock 2026-10-28T12:00Z, en-US, UTC, with a NativeShadow that answers summary() (tests/fixtures/settings-android.js, shadow), and
   the card's buttons pressed through the screen's own commands:
     settings-shadow-off.json        the switch off
     settings-shadow-checking.json   on, 1,150 actions checked since 1 Oct, none different
     settings-shadow-different.json  on, 3 different, the last one a quest, two cases held (also the light theme and large text)
     settings-shadow-light.json      the same in the light theme
     settings-shadow-clear.json      after Clear: the card's own question, "Clear now" in the danger style
     settings-shadow-case.json       after Save case...: the warning that the file holds the owner's real data
     settings-shadow-copied.json     after Copy report: "Report copied. Paste it ..."
     settings-shadow-paused.json     paused while Battery Saver is on
     settings-shadow-stopped.json    stopped itself after five errors in a day */
const android = require('../tests/fixtures/settings-android.js');

const SINCE = Date.UTC(2026, 9, 1), LAST = Date.UTC(2026, 9, 27, 9, 31);
const summary = (state, counts, extra) => Object.assign({ v: 1, state, since: SINCE, counts: Object.assign({ seen: 0, applied: 0, skipped: 0, compared: 0, same: 0, allowed: 0, different: 0 }, counts) }, extra);
const CHECKING = summary('on', { seen: 1204, applied: 1204, skipped: 54, compared: 1150, same: 1150 }, { cases: [] });
const DIFFERENT = summary('on', { seen: 1204, applied: 1204, skipped: 54, compared: 1150, same: 1141, allowed: 6, different: 3 }, {
  different: { at: LAST, kind: 'quest.toggle' },
  cases: [{ id: 'm-17', kind: 'quest.toggle', at: LAST, count: 2 }, { id: 'm-3', kind: 'log.record', at: Date.UTC(2026, 9, 20, 20, 5), count: 1 }],
});

module.exports = async function shadow({ b, out, write }) {
  const visit = async (shadowOpts, theme) => {
    const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
    await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') });
    await p.addInitScript(android.script({ theme, shadow: shadowOpts }));
    await p.goto('file://' + out); await p.waitForTimeout(3500);
    await p.evaluate(async () => {
      lmaThumbWarm = async () => { };
      const S = lmaLoad(); S.nativeOverview = false; S.nativeSettings = true; lmaSave();
      navigate('settings');
      for (let t = 0; t < 8000 && !window.__calls.some((c) => c[0] === 'settings' && c[1] === 'open'); t += 50) await new Promise((r) => setTimeout(r, 50));
      await LmaShadow.refresh();
    });
    return p;
  };
  /* the screen's own commands, each with an id of its own */
  const command = (p, list) => p.evaluate(async (list) => {
    for (const [command, payload] of list) {
      const requestId = command + '-' + (window.__rid = (window.__rid || 0) + 1);
      const [reply] = await Capacitor.Plugins.NativeSettings.__emit('command', { command, payload, requestId });
      if (!reply.ok && reply.changed !== false) throw Error(command + ' ' + JSON.stringify(payload) + ': ' + reply.message);
      await new Promise((r) => setTimeout(r, 150));
    }
  }, list);
  const shoot = async (p, name) => {
    await command(p, [['category', { id: 'data' }]]);
    await p.evaluate(async () => {
      Object.keys(lmaThumbs).forEach((k) => delete lmaThumbs[k]);
      await lmaThumbSnapNow('settings');
      lmaThumbRender();
    });
    await p.clock.pauseAt(new Date('2026-10-28T12:01:00Z'));
    await p.waitForTimeout(300);
    write('fixtures/settings-shadow-' + name + '.json', await p.evaluate(() => {
      document.getElementById('toast').classList.remove('show');
      return Object.assign(NativeSettingsTabProjection.snapshot(), { webSessionId: 'settings-fixture', sequence: 1 });
    }));
    await p.context().close();
  };

  await shoot(await visit({ on: false, summary: summary('off', {}) }), 'off');
  await shoot(await visit({ summary: CHECKING }), 'checking');
  await shoot(await visit({ summary: DIFFERENT }), 'different');
  await shoot(await visit({ summary: DIFFERENT }, 'light'), 'light');
  let p = await visit({ summary: DIFFERENT });
  await command(p, [['press', { id: 'nativeShadow.clear' }]]);
  await shoot(p, 'clear');
  p = await visit({ summary: DIFFERENT });
  await command(p, [['press', { id: 'nativeShadow.saveCase' }]]);
  await shoot(p, 'case');
  p = await visit({ summary: DIFFERENT });
  await command(p, [['press', { id: 'nativeShadow.copy' }]]);
  await shoot(p, 'copied');
  await shoot(await visit({ summary: summary('paused', { seen: 310, applied: 310, compared: 296, same: 296 }, { reason: 'battery', cases: [] }) }), 'paused');
  await shoot(await visit({ summary: summary('disabled', { seen: 88, applied: 88, compared: 80, same: 80 }, { reason: 'errors', cases: [] }) }), 'stopped');
};
