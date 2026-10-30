/* Android device backup: whether Android may copy LifeMax (journal and health entries included) into the owner's Google
   backup and to a new phone. Off by default. The choice lives on the Android side (DeviceBackupChoice), because Android's
   backup agent reads it there; this shows it, what Android last did, and changes it from Settings. */
(function () {
  function plugin() {
    try {
      return Capacitor.isNativePlatform() && Capacitor.isPluginAvailable("DeviceBackup") ? Capacitor.Plugins.DeviceBackup : null;
    } catch (e) {
      return null;
    }
  }
  const mb = (bytes) => Math.round(bytes / 1e6) + " MB";
  const ago = (t) => (typeof lmaAgo === "function" ? lmaAgo(t) : new Date(t).toLocaleString());
  /** the line under the switch: why it cannot be used, what Android last did, and when this phone is the only copy */
  function note(info) {
    if (!info) return plugin() ? "Could not read this setting." : "";
    const lines = [];
    if (info.allowed && info.tooBig) lines.push("Too large for Android backup (" + mb(info.tooBig.bytes) + " of " + mb(info.tooBig.quota) + "), so Android copied nothing.");
    const r = info.lastRequest;
    if (r) lines.push("Android last asked " + ago(r.at) + " (" + (r.transfer ? "phone transfer" : "Google backup") + "): " + (r.copied ? "LifeMax was included." : "nothing was copied."));
    const folder = typeof lmaLoad === "function" ? lmaLoad().backup : null;
    if (!info.allowed && folder && !(folder.on && folder.uri)) lines.push("With this off and no backup folder, this phone holds the only copy of LifeMax.");
    return lines.join(" ");
  }
  async function sync() {
    const input = document.getElementById("deviceBackupToggle");
    if (!input) return;
    const p = plugin();
    let info = null; // null: unknown, so the switch cannot be used
    if (p) {
      try {
        info = await p.get();
      } catch (e) {
        info = null;
      }
    }
    input.checked = !!info && info.allowed === true;
    input.disabled = !info;
    const hint = document.getElementById("deviceBackupNote");
    if (hint) hint.textContent = note(info);
  }
  window.setDeviceBackupAllowed = async (on) => {
    const p = plugin();
    if (!p) return;
    const input = document.getElementById("deviceBackupToggle");
    if (input) input.disabled = true; // one change at a time; sync() enables it again
    try {
      const r = await p.set({ allowed: !!on });
      toast(r.allowed ? "Android may now include LifeMax in your device backup" : "Android's copy of LifeMax will be emptied at its next backup");
    } catch (e) {
      toast("Could not change Android device backup");
    }
    await sync();
  };
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") sync();
  });
  setTimeout(() => {
    sync();
    // the folder part of the card re-renders when a folder is chosen or backups change: the note follows it
    const folder = document.getElementById("lmaBakBody");
    if (folder && typeof MutationObserver === "function") new MutationObserver(() => sync()).observe(folder, { childList: true });
  }, 700);
})();
