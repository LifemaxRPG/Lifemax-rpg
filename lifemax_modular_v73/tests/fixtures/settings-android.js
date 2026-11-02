/* The Android shell as the Settings page sees it, for the native Settings test and fixtures: Capacitor reports a native platform,
   the phone plugins the Settings cards read answer as on a set-up phone (reminders allowed, a widget on the home screen, this
   build the latest release, nothing staged), and every native tab is a fake that records what the page asks of it
   (window.__calls: [tab, 'open' | 'update' | 'close', argument]). A fake emits like the real plugin: P.__emit(event, data).
   script(prefs) returns the init script; prefs: { save: the core save to start from, theme: 'light' | 'dark', settingsCaps: more capabilities NativeSettings reports }. */
const NATIVE = {
  NativeSettings: ['settings', ['settings.projection.v1']],
  NativeOverview: ['overview', []],
  NativeOverviewCards: ['cards', ['overview.cards.v1']],
  NativeCharacter: ['character', ['character.projection.v1']],
  NativeLog: ['log', ['log.projection.v1']],
  NativePlan: ['plan', ['plan.projection.v1']],
  NativeLifeDirector: ['director', ['director.today.v1']],
  NativeRecipes: ['recipes', ['recipes.projection.v1']],
  NativeMedia: ['media', ['library.media.projection.v1']],
  NativeNutrition: ['food', []],
};

function script({ save, theme, settingsCaps } = {}) {
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
      CalendarSync: { request: async () => ({ granted: true }), status: async () => ({ granted: true }), events: async () => ({ events: [] }) },
      NavHaptics: { pulse: ok, pattern: ok },
      BiometricLock: { status: async () => ({ available: true }) },
      HealthSync: {},
      PageThumb: { snap: async () => ({ img: 'data:image/jpeg;base64,' + 'A'.repeat(600) }) },
    };
    for (const [name, [tab, caps]] of Object.entries(${JSON.stringify(NATIVE)})) plugins[name] = fake(tab, name === 'NativeSettings' ? caps.concat(${JSON.stringify(settingsCaps || [])}) : caps);
    window.Capacitor = { isNativePlatform: () => true, isPluginAvailable: n => n in plugins, getPlatform: () => 'android', Plugins: plugins,
      registerPlugin: () => ({}), convertFileSrc: u => u };`;
}

module.exports = { script, NATIVE };
