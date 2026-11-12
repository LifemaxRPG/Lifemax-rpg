/* The Android shell as the Settings page sees it, for the native Settings test and fixtures: Capacitor reports a native platform,
   the phone plugins the Settings cards read answer as on a set-up phone (reminders allowed, a widget on the home screen, this
   build the latest release, nothing staged), and every native tab is a fake that records what the page asks of it
   (window.__calls: [tab, 'open' | 'update' | 'close', argument]). A fake emits like the real plugin: P.__emit(event, data).
   script(prefs) returns the init script; prefs: { save: the core save to start from, theme: 'light' | 'dark', settingsCaps: more capabilities NativeSettings reports,
   shadow: a NativeShadow that answers summary() with shadow.summary (the economy check's Settings card, WP6e), the switch on unless
   shadow.on is false, economy.shadow.report.v1 unless shadow.report is false, and copyReport, clear and saveCase recorded in
   window.__shadowAsked and answered ok or with shadow.answers[method] }. */
const NATIVE = {
  NativeSettings: ['settings', ['settings.projection.v1']],
  NativeOverview: ['overview', []],
  NativeOverviewCards: ['cards', ['overview.cards.v1']],
  NativeCharacter: ['character', ['character.projection.v1']],
  NativePet: ['pet', ['pet.projection.v1']],
  NativeLog: ['log', ['log.projection.v1']],
  NativePlan: ['plan', ['plan.projection.v1']],
  NativeLifeDirector: ['director', ['director.today.v1']],
  NativeRecipes: ['recipes', ['recipes.projection.v1']],
  NativeMedia: ['media', ['library.media.projection.v1']],
  NativeNutrition: ['food', []],
};

function script({ save, theme, settingsCaps, shadow } = {}) {
  return `
    localStorage.setItem('lifemax_rpg_prototype_v1', ${JSON.stringify(JSON.stringify(save || { onboardingComplete: true, name: 'N', tutorialDone: true, tipsSeen: {}, classId: 'athlete' }))});
    ${theme ? `localStorage.setItem('lifeMaxTheme', ${JSON.stringify(theme)});` : ''}
    window.__calls = []; window.__bootAt = Date.now(); // when the page began, for waiting on its boot timers
    const fake = (name, caps) => { const ls = {}; return {
      __emit: (e, d) => Promise.all((ls[e] || []).map(f => f(d))),
      addListener: async (e, f) => { (ls[e] = ls[e] || []).push(f); return { remove() {} } },
      open: async a => { window.__calls.push([name, 'open', a]) },
      update: async a => { window.__calls.push([name, 'update', a]) },
      close: async a => { window.__calls.push([name, 'close', a]); (ls.closed || []).forEach(f => f({ session: a && a.session })) },
      capabilities: async () => ({ liveWeb: true, version: 1, authoritativeData: true }),
      handshake: async () => ({ protocol: 1, capabilities: caps }),
      timing: async () => ({}), setTimingToast: async () => ({}) } };
    const ok = async () => ({});
    const plugins = {
      LocalNotifications: { checkPermissions: async () => ({ display: 'granted' }), requestPermissions: async () => ({ display: 'granted' }),
        checkExactNotificationSetting: async () => ({ exact_alarm: 'granted' }), changeExactNotificationSetting: ok, getPending: async () => ({ notifications: [] }),
        cancel: ok, schedule: ok, createChannel: ok },
      WidgetBridge: { status: async () => ({ count: 1, canPin: true }), update: ok, pin: async () => ({ requested: true }), consumeLaunch: ok },
      Updater: { check: async () => ({ current: 290, latest: 290, nativeApi: 28, latestNativeApi: 28, apk: 'https://example.com/lifemax-290.apk', stable: true }) },
      LiveWeb: { status: async () => ({ installedBuild: 290, activeBuild: 0, nativeApi: 28 }) },
      BackupFolder: { pickFolder: async () => ({ picked: false }) },
      DeviceBackup: { get: async () => ({ allowed: false }), set: async (a) => ({ allowed: !!a.allowed }) },
      FoodData: { check: async () => ({ foods: 2, bytes: 100, sha256: "0".repeat(64) }), download: async () => ({}), read: async () => ({ data: "", done: true }), discard: async () => ({}), addListener: () => Promise.resolve({ remove() {} }) },
      CalendarSync: { request: async () => ({ granted: true }), status: async () => ({ granted: true }), events: async () => ({ events: [] }) },
      NavHaptics: { pulse: ok, pattern: ok },
      BiometricLock: { status: async () => ({ available: true }) },
      HealthSync: {},
      PageThumb: { snap: async () => ({ img: 'data:image/jpeg;base64,' + 'A'.repeat(600) }) },
    };
    for (const [name, [tab, caps]] of Object.entries(${JSON.stringify(NATIVE)})) plugins[name] = fake(tab, name === 'NativeSettings' ? caps.concat(${JSON.stringify(settingsCaps || [])}) : caps);
    ${shadow ? `(() => {
      const sh = ${JSON.stringify(shadow)};
      if (sh.on !== false) localStorage.setItem('lifemax_android_prefs_v1', JSON.stringify(Object.assign(JSON.parse(localStorage.getItem('lifemax_android_prefs_v1') || '{}'), { nativeShadow: true })));
      window.__shadowAsked = [];
      const reply = (name, a) => { window.__shadowAsked.push([name, a || {}]); return Promise.resolve(Object.assign({ ok: true }, (sh.answers || {})[name])) };
      plugins.NativeShadow = {
        handshake: async () => ({ protocol: 1, capabilities: ['economy.shadow.v1', 'economy.shadow.quests.v1', 'economy.shadow.day.v1'].concat(sh.report === false ? [] : ['economy.shadow.report.v1']), fingerprint: '', state: 'off' }),
        configure: async ({ text }) => ({ ok: true, state: JSON.parse(text).enabled ? sh.summary.state : 'off' }),
        base: async () => ({ ok: true, state: 'on' }), observe: async () => ({ accepted: true }),
        summary: async () => JSON.parse(JSON.stringify(sh.summary)),
        copyReport: (a) => reply('copyReport', a), clear: (a) => reply('clear', a), saveCase: (a) => reply('saveCase', a) };
    })();` : ''}
    window.Capacitor = { isNativePlatform: () => true, isPluginAvailable: n => n in plugins, getPlatform: () => 'android', Plugins: plugins,
      registerPlugin: () => ({}), convertFileSrc: u => u };`;
}

module.exports = { script, NATIVE };
