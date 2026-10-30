/* Android device backup: off by default, a Settings switch that shows and changes the choice kept on the Android side and
   what Android last did, and a manifest that lets Android back up only through the agent that honours it.
   Run: node tests/device-backup.test.js */
const fs = require("node:fs"), vm = require("node:vm"), os = require("node:os"), path = require("node:path");
const assert = require("node:assert/strict"), { execFileSync } = require("node:child_process");
const root = path.join(__dirname, ".."), repo = path.join(root, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");
const native = (p) => fs.readFileSync(path.join(repo, "android-wrapper", p), "utf8");
const settle = () => new Promise((r) => setTimeout(r, 5));
let passed = 0;
const check = async (name, fn) => { await fn(); passed++; console.log("PASS", name); };

function page({ inApp = true, stored = false, failGet = false, failSet = false, record = {}, folder = { on: true, uri: "content://drive" } } = {}) {
  const input = { checked: true, disabled: false }, hint = { textContent: "" };
  const toasts = [], sets = [], listeners = {}, observers = [];
  let timer = null, gate = null;
  const plugin = {
    async get() { if (failGet) throw Error("unavailable"); return Object.assign({ allowed: stored }, record); },
    async set(o) { sets.push(o); if (gate) await gate; if (failSet) throw Error("disk full"); stored = o.allowed; return { allowed: stored }; },
  };
  const s = {
    window: {}, toast: (m) => toasts.push(m), lmaAgo: () => "2 hours ago", lmaLoad: () => ({ backup: folder }),
    Capacitor: { isNativePlatform: () => inApp, isPluginAvailable: (n) => inApp && n === "DeviceBackup", Plugins: { DeviceBackup: plugin } },
    document: {
      visibilityState: "visible",
      getElementById: (id) => ({ deviceBackupToggle: input, deviceBackupNote: hint, lmaBakBody: {} })[id] || null,
      addEventListener: (type, fn) => { listeners[type] = fn; },
    },
    MutationObserver: function (fn) { this.observe = () => observers.push(fn); },
    setTimeout: (fn) => { timer = fn; return 1; },
  };
  vm.createContext(s);
  vm.runInContext(read("src/runtime/segments/50-device-backup.js"), s);
  return {
    input, hint, toasts, sets, listeners, observers,
    start: async () => { timer(); await settle(); },
    set: (on) => s.window.setDeviceBackupAllowed(on),
    hold: () => { let open; gate = new Promise((r) => { open = r; }); return () => { gate = null; open(); }; },
    stored: () => stored,
    change: (patch) => { if ("stored" in patch) stored = patch.stored; Object.assign(record, patch.record || {}); },
  };
}

(async () => {
  await check("off by default: the switch shows the Android side's choice, which starts false", async () => {
    const p = page();
    await p.start();
    assert.equal(p.input.checked, false);
    assert.equal(p.input.disabled, false);
  });
  await check("shows an earlier choice to allow", async () => {
    const p = page({ stored: true });
    await p.start();
    assert.equal(p.input.checked, true);
  });
  await check("outside the Android app the switch is off and cannot be used, with no note", async () => {
    const p = page({ inApp: false });
    await p.start();
    assert.equal(p.input.checked, false);
    assert.equal(p.input.disabled, true);
    assert.equal(p.hint.textContent, "");
  });
  await check("if the choice cannot be read, the switch is off, cannot be used, and says why", async () => {
    const p = page({ stored: true, failGet: true });
    await p.start();
    assert.equal(p.input.checked, false);
    assert.equal(p.input.disabled, true);
    assert.equal(p.hint.textContent, "Could not read this setting.");
  });
  await check("turning it on and off saves the choice and says what happens", async () => {
    const p = page();
    await p.start();
    await p.set(true);
    assert.equal(JSON.stringify(p.sets), JSON.stringify([{ allowed: true }])); // objects from the page realm
    assert.equal(p.input.checked, true);
    assert.match(p.toasts.at(-1), /may now include/);
    await p.set(false);
    assert.equal(p.stored(), false);
    assert.equal(p.input.checked, false);
    assert.match(p.toasts.at(-1), /emptied at its next backup/);
  });
  await check("the switch cannot be used while a change is being saved", async () => {
    const p = page();
    await p.start();
    const open = p.hold();
    const saving = p.set(true);
    assert.equal(p.input.disabled, true);
    open();
    await saving;
    assert.equal(p.input.disabled, false);
  });
  await check("a failed save says so and the switch goes back to the saved choice", async () => {
    const p = page({ failSet: true });
    await p.start();
    p.input.checked = true; // what the tap did
    await p.set(true);
    assert.match(p.toasts.at(-1), /Could not change/);
    assert.equal(p.input.checked, false);
  });
  await check("the note says what Android last did, and when a backup was too large", async () => {
    const p = page({ stored: true, record: { lastRequest: { at: 1, transfer: false, copied: true }, tooBig: { bytes: 31e6, quota: 25e6 } } });
    await p.start();
    assert.match(p.hint.textContent, /Too large for Android backup \(31 MB of 25 MB\), so Android copied nothing\./);
    assert.match(p.hint.textContent, /Android last asked 2 hours ago \(Google backup\): LifeMax was included\./);
    const q = page({ record: { lastRequest: { at: 1, transfer: true, copied: false }, tooBig: { bytes: 31e6, quota: 25e6 } } });
    await q.start();
    assert.doesNotMatch(q.hint.textContent, /Too large/, "a size note only matters while the switch is on");
    assert.match(q.hint.textContent, /\(phone transfer\): nothing was copied\./);
  });
  await check("with the switch off and no backup folder, the note says this phone holds the only copy", async () => {
    const p = page({ folder: { on: false, uri: "" } });
    await p.start();
    assert.match(p.hint.textContent, /this phone holds the only copy/);
    const q = page();
    await q.start();
    assert.doesNotMatch(q.hint.textContent, /only copy/);
    const r = page({ stored: true, folder: { on: false, uri: "" } });
    await r.start();
    assert.doesNotMatch(r.hint.textContent, /only copy/);
  });
  await check("the switch and note are read again when the app returns and when the backup folder changes", async () => {
    const p = page();
    await p.start();
    p.change({ stored: true });
    p.listeners.visibilitychange();
    await settle();
    assert.equal(p.input.checked, true);
    p.change({ record: { lastRequest: { at: 1, transfer: false, copied: true } } });
    assert.equal(p.observers.length, 1);
    p.observers[0]();
    await settle();
    assert.match(p.hint.textContent, /LifeMax was included/);
  });
  await check("Settings has the switch and its note, off and disabled until the page reads the choice", async () => {
    const html = read("src/views/settings.html");
    assert.match(html, /<input id="deviceBackupToggle" type="checkbox"[^>]*onchange="setDeviceBackupAllowed\(this\.checked\)"[^>]*disabled>/);
    assert.doesNotMatch(html.match(/<input id="deviceBackupToggle"[^>]*>/)[0], /checked(?!\))/);
    assert.match(html, /<div id="deviceBackupNote"/);
    assert.ok(read("src/index.template.html").includes("@@INCLUDE:src/runtime/segments/50-device-backup.js@@"));
  });
  await check("manifest: Android backs up only through DeviceBackupAgent", async () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "lma-manifest-"));
    for (const app of ['<application android:allowBackup="true" android:icon="@mipmap/ic_launcher">', "<application>"]) {
      const file = path.join(dir, "AndroidManifest.xml");
      fs.writeFileSync(file, `<manifest xmlns:android="http://schemas.android.com/apk/res/android">${app}<activity android:name=".MainActivity" android:launchMode="singleTop"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER" /></intent-filter></activity></application></manifest>`);
      execFileSync("python3", [path.join(repo, "android-wrapper/patch_manifest.py"), file]);
      const tag = fs.readFileSync(file, "utf8").match(/<application\b[^>]*>/)[0];
      assert.equal((tag.match(/allowBackup/g) || []).length, 1);
      assert.match(tag, /android:backupAgent="com\.lifemax\.rpg\.app\.backup\.DeviceBackupAgent"/);
      assert.match(tag, /android:fullBackupOnly="true"/);
      assert.match(tag, /android:fullBackupContent="@xml\/backup_rules"/);
      assert.match(tag, /android:dataExtractionRules="@xml\/data_extraction_rules"/);
    }
    fs.rmSync(dir, { recursive: true, force: true });
    assert.ok(fs.existsSync(path.join(repo, "android-wrapper/native/src/main/java/com/lifemax/rpg/app/backup/DeviceBackupAgent.kt")));
  });
  await check("rules: every section leaves out the web bundle and the switch; Google copies need encryption", async () => {
    const sections = [
      ...native("res/xml/data_extraction_rules.xml").matchAll(/<(cloud-backup|device-transfer)\b[^>]*>([\s\S]*?)<\/\1>/g),
      ...native("res/xml/backup_rules.xml").matchAll(/<(full-backup-content)>([\s\S]*?)<\/\1>/g),
    ];
    assert.equal(sections.length, 3);
    for (const [, name, body] of sections) {
      for (const rule of ['<exclude domain="file" path="live/" />', '<exclude domain="sharedpref" path="lifemax_live.xml" />', '<exclude domain="sharedpref" path="lifemax_device_backup.xml" />'])
        assert.ok(body.includes(rule), `${name} lacks ${rule}`);
    }
    assert.match(native("res/xml/data_extraction_rules.xml"), /<cloud-backup disableIfNoEncryptionCapabilities="true">/);
  });
  await check("agent: standard backup only when allowed, otherwise only a marker; the choice defaults to off and survives a failed write", async () => {
    const dir = "native/src/main/java/com/lifemax/rpg/app/backup/";
    const agent = native(dir + "DeviceBackupAgent.kt"), choice = native(dir + "DeviceBackupChoice.kt");
    assert.match(agent, /if \(allowed\) \{\s*marker\.delete\(\)\s*super\.onFullBackup\(data\)\s*\} else \{[\s\S]*?fullBackupFile\(marker, data\)\s*\}/);
    assert.match(agent, /override fun onQuotaExceeded/);
    assert.match(choice, /getBoolean\(ALLOWED, false\)/);
    assert.match(choice, /if \(!prefs\.edit\(\)\.putBoolean\(ALLOWED, allowed\)\.commit\(\)\) \{\s*prefs\.edit\(\)\.putBoolean\(ALLOWED, before\)\.commit\(\)\s*return false/);
    assert.match(choice, /private const val PREFS = "lifemax_device_backup"/); // the file the rules exclude
  });
  console.log(`PASS ${passed} device backup checks`);
})().catch((e) => { console.error(e); process.exit(1); });
