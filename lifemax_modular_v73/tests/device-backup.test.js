/* Android device backup: off by default, a Settings switch that shows and changes the choice kept on the Android side,
   and a manifest that lets Android back up only through the agent that honours it. Run: node tests/device-backup.test.js */
const fs = require("node:fs"), vm = require("node:vm"), os = require("node:os"), path = require("node:path");
const assert = require("node:assert/strict"), { execFileSync } = require("node:child_process");
const root = path.join(__dirname, ".."), repo = path.join(root, "..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");
let passed = 0;
const check = async (name, fn) => { await fn(); passed++; console.log("PASS", name); };

function page({ native = true, stored = false, failGet = false, failSet = false } = {}) {
  const input = { checked: true, disabled: false };
  const toasts = [], sets = [];
  let timer = null;
  const plugin = {
    async get() { if (failGet) throw Error("unavailable"); return { allowed: stored }; },
    async set(o) { sets.push(o); if (failSet) throw Error("disk full"); stored = o.allowed; return { allowed: stored }; },
  };
  const s = {
    window: {}, toast: (m) => toasts.push(m), lmaNative: () => native, lmaPlugin: (n) => (n === "DeviceBackup" ? plugin : null),
    document: { getElementById: (id) => (id === "deviceBackupToggle" ? input : null) },
    setTimeout: (fn) => { timer = fn; return 1; },
  };
  vm.createContext(s);
  vm.runInContext(read("src/runtime/segments/50-device-backup.js"), s);
  return { input, toasts, sets, sync: () => timer(), set: (on) => s.window.setDeviceBackupAllowed(on), stored: () => stored };
}

(async () => {
  await check("off by default: the switch shows the Android side's choice, which starts false", async () => {
    const p = page();
    await p.sync();
    assert.equal(p.input.checked, false);
    assert.equal(p.input.disabled, false);
  });
  await check("shows an earlier choice to allow", async () => {
    const p = page({ stored: true });
    await p.sync();
    assert.equal(p.input.checked, true);
  });
  await check("outside the Android app the switch is off and cannot be used", async () => {
    const p = page({ native: false });
    await p.sync();
    assert.equal(p.input.checked, false);
    assert.equal(p.input.disabled, true);
  });
  await check("if the choice cannot be read, the switch is off and cannot be used", async () => {
    const p = page({ stored: true, failGet: true });
    await p.sync();
    assert.equal(p.input.checked, false);
    assert.equal(p.input.disabled, true);
  });
  await check("turning it on and off saves the choice and says so", async () => {
    const p = page();
    await p.sync();
    await p.set(true);
    assert.equal(JSON.stringify(p.sets), JSON.stringify([{ allowed: true }])); // objects from the page realm
    assert.equal(p.input.checked, true);
    assert.match(p.toasts.at(-1), /may now include/);
    await p.set(false);
    assert.equal(p.stored(), false);
    assert.equal(p.input.checked, false);
    assert.match(p.toasts.at(-1), /no longer included/);
  });
  await check("a failed save says so and the switch goes back to the saved choice", async () => {
    const p = page({ failSet: true });
    await p.sync();
    p.input.checked = true; // what the tap did
    await p.set(true);
    assert.match(p.toasts.at(-1), /Could not change/);
    assert.equal(p.input.checked, false);
  });
  await check("Settings has the switch, off and disabled until the page reads the choice", async () => {
    const html = read("src/views/settings.html");
    assert.match(html, /<input id="deviceBackupToggle" type="checkbox"[^>]*onchange="setDeviceBackupAllowed\(this\.checked\)"[^>]*disabled>/);
    assert.doesNotMatch(html.match(/<input id="deviceBackupToggle"[^>]*>/)[0], /checked(?!\))/);
    assert.ok(read("src/index.template.html").includes("@@INCLUDE:src/runtime/segments/50-device-backup.js@@"));
  });
  await check("manifest: Android backs up only through DeviceBackupAgent, with the web bundle excluded", async () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "lma-manifest-"));
    for (const app of ['<application android:allowBackup="true" android:icon="@mipmap/ic_launcher">', "<application>"]) {
      const file = path.join(dir, "AndroidManifest.xml");
      fs.writeFileSync(file, `<manifest xmlns:android="http://schemas.android.com/apk/res/android">${app}<activity android:name=".MainActivity" android:launchMode="singleTop"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER" /></intent-filter></activity></application></manifest>`);
      execFileSync("python3", [path.join(repo, "android-wrapper/patch_manifest.py"), file]);
      const tag = fs.readFileSync(file, "utf8").match(/<application\b[^>]*>/)[0];
      assert.equal((tag.match(/allowBackup/g) || []).length, 1);
      assert.match(tag, /android:backupAgent="com\.lifemax\.rpg\.core\.platform\.DeviceBackupAgent"/);
      assert.match(tag, /android:fullBackupOnly="true"/);
      assert.match(tag, /android:fullBackupContent="@xml\/backup_rules"/);
      assert.match(tag, /android:dataExtractionRules="@xml\/data_extraction_rules"/);
    }
    fs.rmSync(dir, { recursive: true, force: true });
    const res = (f) => fs.readFileSync(path.join(repo, "android-wrapper/res/xml", f), "utf8");
    assert.equal((res("data_extraction_rules.xml").match(/<exclude domain="file" path="live\/" \/>/g) || []).length, 2);
    assert.match(res("backup_rules.xml"), /<exclude domain="file" path="live\/" \/>/);
    const agent = fs.readFileSync(path.join(repo, "android-wrapper/native/src/main/java/com/lifemax/rpg/core/platform/DeviceBackup.kt"), "utf8");
    assert.match(agent, /if \(DeviceBackupChoice\.allowed\(this\)\) super\.onFullBackup\(data\)/);
    assert.match(agent, /getBoolean\(ALLOWED, false\)/);
  });
  console.log(`PASS ${passed} device backup checks`);
})().catch((e) => { console.error(e); process.exit(1); });
