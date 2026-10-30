/* Android device backup: whether Android may copy LifeMax's data (journal and health entries included) into the owner's
   Google backup and to a new phone. Off by default. The choice lives on the Android side (DeviceBackupChoice), because
   Android's backup agent reads it there; this only shows it and changes it from Settings. */
(function () {
  function plugin() {
    return typeof lmaPlugin === "function" && lmaNative() ? lmaPlugin("DeviceBackup") : null;
  }
  async function sync() {
    const input = document.getElementById("deviceBackupToggle");
    if (!input) return;
    const p = plugin();
    let allowed = null; // null: unknown, so the switch cannot be used
    if (p) {
      try {
        allowed = (await p.get()).allowed === true;
      } catch (e) {
        allowed = null;
      }
    }
    input.checked = allowed === true;
    input.disabled = allowed === null;
  }
  window.setDeviceBackupAllowed = async (on) => {
    const p = plugin();
    if (!p) return;
    try {
      const r = await p.set({ allowed: !!on });
      toast(r.allowed ? "Android may now include LifeMax in your device backup" : "LifeMax is no longer included in new device backups");
    } catch (e) {
      toast("Could not change Android device backup");
    }
    await sync();
  };
  setTimeout(sync, 700);
})();
