/* Demo mode, page half (docs/architecture/DEMO_MODE.md). The storage half (00-demo-storage.js) has already pointed every key and
   database at the demo's own copies when LM_DEMO.on is true; this file is what the owner sees and what the demo must not reach:
     - Settings > Run a demo asks first, then starts a temporary save at the welcome screen; Settings > Return to my save asks
       "Discard the demo data?" and opens the real save again. Both reload the page, because the storage view is chosen at start-up.
     - a DEMO chip on every page screen (tap it to go back) and an amber status bar, which Android draws over native screens too.
     - everything that would leave the demo or touch the phone is off: reminders, Health sync, calendar import, the home-screen
       widget, folder backups, backup export and import and Android's device backup. Their Settings cards say so.
   Nothing here writes a real key: the only real-storage writes are LM_DEMO.enter / leave, which set and clear the marker. */
(function () {
  const D = LM_DEMO;
  const on = D.on;
  const AMBER = "#f5a524";
  /* the native plugins a demo must not reach (they would act on the phone, or read or write outside the demo's own storage) */
  const OFF = ["LocalNotifications", "HealthSync", "CalendarSync", "WidgetBridge", "BackupFolder", "DeviceBackup", "NativeShadow"];
  /* preferences copied INTO the demo when it starts, so it looks the way the owner's app looks (the real keys are only read) */
  const LOOK_KEYS = ["lifeMaxTheme", "lma_sfxvol", "lma_qfx", "lma_qdeal", "lma_qpack", "lma_gwr3d", "lma_g3dbg"];
  const WORDS = {
    off: "Off in the demo, so nothing here reaches your phone or your own save.",
    backupsOff: "Backups are off in the demo.",
    startFailed: "Could not start the demo (storage full?). Nothing was changed.",
    returnFailed: "Could not go back to your save. Nothing was changed.",
  };

  /** the starting preferences for a demo: the owner's look and layout choices, without anything about backups, apps or reminders */
  function seed() {
    const out = {};
    for (const k of LOOK_KEYS) {
      try {
        const v = localStorage.getItem(k);
        if (typeof v === "string") out[k] = v;
      } catch (e) {}
    }
    try {
      const p = JSON.parse(localStorage.getItem(LMA_PREF_KEY) || "null");
      if (p && typeof p === "object") {
        delete p.backup;
        delete p.apps;
        p.notif = Object.assign({}, p.notif, { on: false });
        out[LMA_PREF_KEY] = JSON.stringify(p);
      }
    } catch (e) {}
    return out;
  }

  /** a question over the page: title, paragraphs, optional bullets, a main button (focused) and a way out; Back closes it */
  function ask({ title, text, bullets, yes, no, then }) {
    const old = document.querySelector(".lm-demo-ask");
    if (old) old.remove();
    const o = document.createElement("div");
    o.className = "lmdl-ask lm-demo-ask";
    o.setAttribute("role", "dialog");
    o.setAttribute("aria-modal", "true");
    o.setAttribute("aria-label", title);
    const card = document.createElement("div");
    card.className = "updpop-card";
    const add = (tag, words) => {
      const el = document.createElement(tag);
      el.textContent = words;
      card.appendChild(el);
      return el;
    };
    add("h3", title);
    text.forEach((t) => add("p", t));
    if (bullets) {
      const ul = document.createElement("ul");
      bullets.forEach((b) => {
        const li = document.createElement("li");
        li.textContent = b;
        ul.appendChild(li);
      });
      card.appendChild(ul);
    }
    const row = document.createElement("div");
    row.className = "updpop-btns";
    const cancel = document.createElement("button");
    cancel.type = "button";
    cancel.className = "btn secondary";
    cancel.textContent = no;
    cancel.addEventListener("click", () => o.remove());
    const go = document.createElement("button");
    go.type = "button";
    go.className = "btn";
    go.textContent = yes;
    go.addEventListener("click", () => {
      o.remove();
      then();
    });
    row.append(cancel, go);
    card.appendChild(row);
    o.appendChild(card);
    o.addEventListener("click", (e) => {
      if (e.target === o) o.remove();
    });
    document.body.appendChild(o);
    go.focus();
  }

  /** the page starts over in the other mode: native screens step aside first, as they do for an import */
  async function restart() {
    try {
      if (typeof lmaLeaveNative === "function") await lmaLeaveNative();
    } catch (e) {}
    setTimeout(() => location.reload(), 300);
  }

  window.lmDemoAskStart = function () {
    if (D.on) return;
    ask({
      title: "Run a demo?",
      text: [
        "The demo starts a new, temporary save at the very first screen. Your own save is not opened, changed or deleted, and Settings has a button to go back to it.",
        "While the demo runs, these are switched off:",
      ],
      bullets: ["Backups, and exporting or importing a backup", "Reminders (any you already have stay as they are)", "Health sync, calendar import and the home-screen widget", "Android device backup"],
      yes: "Start the demo",
      no: "Not now",
      then: () => {
        if (!D.enter(seed())) return toast(WORDS.startFailed);
        restart();
      },
    });
  };
  window.lmDemoAskReturn = function () {
    if (!D.on) return;
    ask({
      title: "Discard the demo data?",
      text: ["The demo's temporary save is deleted, and LifeMax opens your own save again, exactly as you left it."],
      yes: "Discard and return",
      no: "Stay in the demo",
      then: () => {
        if (!D.leave()) return toast(WORDS.returnFailed);
        restart();
      },
    });
  };

  /* Settings: the start card is for the real save, the return card for the demo (a card the page hides is not drawn natively either) */
  const startCard = document.getElementById("lmDemoCard"),
    returnCard = document.getElementById("lmDemoOnCard");
  if (startCard) startCard.hidden = on;
  if (returnCard) returnCard.hidden = !on;
  if (!on) return;

  /* ---------- from here on: the demo is running ---------- */
  document.body.classList.add("lm-demo");
  const pill = document.querySelector(".topbar .pill");
  if (pill) pill.textContent = "DEMO";
  const chip = document.createElement("button");
  chip.type = "button";
  chip.className = "lm-demo-chip";
  chip.setAttribute("aria-label", "Demo mode. Open the way back to your own save");
  chip.innerHTML = "DEMO <small>tap to go back</small>";
  chip.addEventListener("click", window.lmDemoAskReturn);
  document.body.appendChild(chip);

  /* the plugins above do not exist in the demo; every caller already copes with a plugin that is missing (a browser has none) */
  /* A function the page declares is replaced through window (the page's functions are its globals), so every caller that looks it
     up by name gets the demo's version. */
  const swap = (name, make) => {
    const was = window[name];
    if (typeof was === "function") window[name] = make(was);
  };
  swap("lmaPlugin", (was) => function (name) { return OFF.indexOf(name) >= 0 ? null : was.apply(this, arguments); });
  /* backups: nothing is exported, imported or written to a folder */
  swap("exportData", () => () => toast(WORDS.backupsOff));
  swap("importData", () => (e) => {
    try {
      e.target.value = "";
    } catch (x) {}
    toast(WORDS.backupsOff);
  });
  swap("applyBackup", () => async () => ({ ok: false, message: WORDS.backupsOff }));
  swap("lmaRunBackup", () => () => Promise.resolve(false));
  /* off-device voice, and the update pop-up (an update is the owner's to take on their own save) */
  swap("lmgOn", () => () => false);
  swap("lmaUpdPopOpen", () => () => {});
  /* the two resets name what they clear: only the demo */
  const ask0 = window.confirm.bind(window);
  window.confirm = function (m) {
    const s = String(m);
    if (/^Delete all Life Max data|^Reset to a clean starter profile/.test(s)) return ask0("Clear the demo and start it over? Only the demo is cleared; your own save is not touched.");
    return ask0(m);
  };
  /* the cards whose features are off say so, whatever the page draws into them */
  for (const [fn, id] of [["lmaRenderSettings", "lmaNotifBody"], ["lmaWidgetRender", "lmaWidgetBody"], ["lmaHealthRender", "lmaHealthBody"], ["lmcRender", "lmaCalBody"], ["lmaRenderBackup", "lmaBakBody"]]) {
    const draw = window[fn];
    if (typeof draw !== "function") continue;
    const note = () => lmaSetHtml(id, '<div class="note">' + WORDS.off + "</div>");
    window[fn] = function () {
      const r = draw.apply(this, arguments);
      note();
      return r && typeof r.then === "function" ? r.then(note) : r;
    };
    note();
  }
  /* the status bar, which Android draws over the native screens as well as the page */
  swap("lmaApplyBars", (was) => function () {
    const P = lmaPlugin("AppLauncher");
    if (!P) return was.apply(this, arguments);
    try {
      P.setSystemBars({ color: AMBER, light: true }).catch(() => {});
    } catch (e) {}
  });
  setTimeout(() => window.lmaApplyBars(), 3500);
})();
