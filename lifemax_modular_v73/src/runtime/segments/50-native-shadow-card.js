/* Native economy check (beta), its Settings card (docs/specs/ECONOMY_SHADOW_PLAN.md 3.3, 3.4, 5.3; WP6e). The card is an ordinary
   page card in Data & advanced (views/settings.html #nativeShadowCard), so the classic page and the native Settings screen draw the
   same words; native Settings presses its buttons through its BOUND list. The transport (50-native-shadow.js) owns the switch,
   the plugin and summary(); this file only words what they say and asks the plugin when the owner taps:
   - the line under the switch: not available, off, starting, checking (counts), paused (Battery Saver, the matching app update,
     a demo), or stopped, with the reason in plain words; and the last difference's kind and time (never a value);
   - Copy report: native builds the redacted report and puts it on the clipboard itself (the page never sees it);
   - Clear: asks in the card first, then empties the counts and differences; "Cleared" only once native says the ring is stored;
   - Save case... (decision O-4): one chosen difference's unredacted action, to a file the owner picks in Android's picker, only
     after a warning in the card; never on its own, never sent anywhere.
   Every answer is shown only after the plugin says it is done; a refused or failed call says so and changes nothing. The card's
   fixed words (the help, who does what, the report's and the case's warnings) are in the markup. */
window.LMA_SHADOW_CARD = (function () {
  const REPORT = "economy.shadow.report.v1";
  const day = (t) => new Date(t).toLocaleDateString("en-GB", { day: "numeric", month: "short" });
  const time = (t) => day(t) + " " + new Date(t).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
  const count = (n) => (Number(n) || 0).toLocaleString("en-GB");
  /* what an action kind is called for the owner: its family's plain name (the kind itself when the family is new) */
  const KINDS = {
    log: "logging an activity", voice: "a voice log", quest: "a quest", hand: "the Daily Hand", health: "a Health sync",
    director: "Life Director", bonus: "a cat-care or food bonus", money: "Money", fixed: "a plan or catalog tick",
    coach: "the journal coach", spark: "the Sparks shop", ascend: "an ascension", rest: "a rest day",
    rules: "XP rules or decay settings", decay: "the day's decay", boot: "opening the app", save: "a save",
  };
  const kindWords = (kind) => KINDS[String(kind || "").split(".")[0]] || String(kind || "an action");
  const STOPPED = {
    errors: "Stopped itself after 5 errors in a day. Turn it off and on to try again.",
    resyncLoop: "Stopped itself: it lost its place more than 4 times in an hour. Turn it off and on to try again.",
    slow: "Stopped itself because it was too slow on this phone. Turn it off and on to try again.",
    tooLarge: "Stopped: your save is too large for the check (over 4 MB).",
  };
  const WORDS = {
    help: "Your phone also works out every XP and Sparks change the app makes, without saving or showing it, and counts any difference. Nothing leaves the phone.",
    unavailable: "Not available in this version of the app.",
    off: "Off. Turn it on to start checking.",
    starting: "Starting…",
    demo: "Paused during the demo. It only checks your own save.",
    stale: "Paused: waiting for the matching app update. This page was updated on its own, so the phone's rules are not the same yet.",
    paused: "Paused while Battery Saver is on. It carries on when Battery Saver is off.",
    disabled: "Stopped after errors. Turn it off and on to try again.",
    stopped: STOPPED,
    pageFaults: "Stopped for now after a problem on this page. It starts again the next time the app opens.",
    pageSlow: "Stopped for now: checking was slowing the app down. It starts again the next time the app opens.",
    noAnswer: "The phone's check did not answer. It tries again the next time you come back to the app.",
    tooLarge: STOPPED.tooLarge,
    watching: (n, since) => "Following " + count(n) + " actions since " + since + " (comparing comes in a later update)",
    checked: (n, since, different) => "Checked " + count(n) + " actions since " + since + " · " + (Number(different) ? count(different) + " different" : "none different"),
    last: (kind, at) => "Last difference: " + kindWords(kind) + ", " + time(at),
    copied: "Report copied. Paste it into your chat with the assistant.",
    copyFailed: "Could not copy the report. Try again.",
    clearing: "Clearing…",
    cleared: "Cleared. The check starts counting again from now.",
    clearFailed: "Could not clear it. Nothing was changed; try again.",
    caseOption: (c) => time(c.at) + " · " + kindWords(c.kind) + (c.count > 1 ? " (" + c.count + " times)" : ""),
    caseSaved: "Case saved to the file you chose.",
    caseCancelled: "Not saved: no file was chosen.",
    caseGone: "That difference's details are no longer held (they last until the app closes). The report still lists it.",
    caseFailed: "Could not write the file. Try another place.",
    working: "One moment…",
  };

  /** the card for one transport; host: { available, demo, wanted, state(): {live, stopped, starting, hello, summary}, plugin(), refresh() } */
  function create(host) {
    let said = "", asking = "", busy = false;
    const el = (id) => document.getElementById(id);
    const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
    const reporting = () => {
      const h = host.state().hello;
      return !!(h && Array.isArray(h.capabilities) && h.capabilities.includes(REPORT) && host.plugin());
    };

    /** the line under the switch */
    function status() {
      const { stopped, starting, hello, summary } = host.state();
      if (host.demo()) return WORDS.demo; // the demo hides the plugin (50-demo-mode.js), so it is named first
      if (!host.available()) return WORDS.unavailable;
      if (!host.wanted()) return WORDS.off;
      if (hello && (!Array.isArray(hello.capabilities) || !hello.capabilities.includes("economy.shadow.v1"))) return WORDS.unavailable;
      if (stopped === "faults") return WORDS.pageFaults;
      if (stopped === "slow") return WORDS.pageSlow;
      if (stopped === "noAnswer" && !summary) return WORDS.noAnswer;
      if (stopped === "tooLarge") return WORDS.tooLarge;
      if (!summary) return starting || !stopped ? WORDS.starting : "";
      const s = summary.state, why = summary.reason;
      if (s === "stale") return WORDS.stale;
      if (s === "paused") return WORDS.paused;
      if (s === "disabled") return STOPPED[why] || WORDS.disabled;
      if (s === "on" && summary.counts) {
        const c = summary.counts, since = day(summary.since);
        /* an app that compares (WP6b) counts compared actions and differences; an older one only follows them */
        if (typeof c.compared !== "number") return WORDS.watching(c.seen, since);
        return WORDS.checked(c.compared, since, c.different);
      }
      return s === "on" ? WORDS.starting : "";
    }
    function lastLine() {
      const { summary } = host.state();
      if (!host.wanted() || !summary || !summary.counts || !Number(summary.counts.different)) return "";
      const d = summary.different;
      return d && d.at ? WORDS.last(d.kind, d.at) : "";
    }
    const cases = () => {
      const s = host.state().summary;
      return s && Array.isArray(s.cases) ? s.cases.filter((c) => c && /^m-\d{1,9}$/.test(String(c.id))) : [];
    };

    function show(id, on) {
      const e = el(id);
      if (e) e.style.display = on ? "" : "none";
    }
    function text(id, words) {
      const e = el(id);
      if (!e) return;
      if (e.textContent !== words) e.textContent = words;
      e.style.display = words ? "" : "none";
    }
    /** draws every part of the card from what the transport knows now */
    function sync() {
      const input = el("nativeShadowToggle");
      if (input) {
        input.disabled = !host.available() || host.demo();
        input.checked = host.wanted();
      }
      text("nativeShadowStatus", status());
      text("nativeShadowLast", lastLine());
      const tools = reporting() && host.wanted() && !host.demo();
      show("nativeShadowTools", tools);
      if (!tools) asking = "";
      text("nativeShadowSaid", tools ? said : "");
      for (const id of ["nativeShadowCopyBtn", "nativeShadowClearBtn", "nativeShadowCaseBtn", "nativeShadowClearYes", "nativeShadowCaseYes"]) {
        const b = el(id);
        if (b) b.disabled = busy;
      }
      show("nativeShadowClearAsk", tools && asking === "clear");
      const list = cases();
      show("nativeShadowCaseRow", tools && list.length > 0);
      if (!list.length && asking === "case") asking = "";
      show("nativeShadowCaseAsk", tools && asking === "case" && list.length > 0);
      const sel = el("nativeShadowCaseSel");
      if (sel) {
        const keep = sel.value, html = list.map((c) => '<option value="' + c.id + '">' + esc(WORDS.caseOption(c)) + "</option>").join("");
        if (sel.dataset.html !== html) {
          sel.innerHTML = html;
          sel.dataset.html = html;
          if (list.some((c) => c.id === keep)) sel.value = keep;
        }
      }
    }

    /** one plugin call: the buttons wait, the line says "One moment" until native answers, then what it answered */
    async function ask(method, payload, answer) {
      if (busy || !reporting()) return;
      busy = true;
      said = WORDS.working;
      sync();
      let a = null;
      try { a = await host.plugin()[method](payload || {}); } catch (_) { a = null; }
      busy = false;
      said = answer(a || {});
      await host.refresh();
      sync();
    }
    const card = {
      copy: () => ask("copyReport", {}, (a) => (a.ok ? WORDS.copied : WORDS.copyFailed)),
      clear: () => { said = ""; asking = asking === "clear" ? "" : "clear"; sync(); },
      clearKeep: () => { asking = ""; sync(); },
      clearNow: () => { asking = ""; return ask("clear", {}, (a) => (a.ok ? WORDS.cleared : WORDS.clearFailed)); },
      saveCase: () => { said = ""; asking = asking === "case" ? "" : "case"; sync(); },
      caseKeep: () => { asking = ""; sync(); },
      caseNow: () => {
        const sel = el("nativeShadowCaseSel"), id = sel && sel.value;
        asking = "";
        if (!id || !cases().some((c) => c.id === id)) { said = WORDS.caseGone; sync(); return; }
        return ask("saveCase", { id }, (a) => (a.ok ? WORDS.caseSaved : a.reason === "cancelled" ? WORDS.caseCancelled : a.reason === "noCase" ? WORDS.caseGone : WORDS.caseFailed));
      },
    };
    window.LmaShadowCard = Object.assign(card, { status, lastLine, words: WORDS });
    return { sync, words: WORDS };
  }
  /* the controls native Settings may operate by command (50-native-settings.js reads this list): each is pressed or chosen there
     exactly as a tap does here, and none writes page storage */
  const bound = [
    ["nativeShadow.copy", "#nativeShadowCopyBtn", null],
    ["nativeShadow.clear", "#nativeShadowClearBtn", null],
    ["nativeShadow.clearNow", "#nativeShadowClearYes", null],
    ["nativeShadow.clearKeep", "#nativeShadowClearNo", null],
    ["nativeShadow.case", "#nativeShadowCaseSel", null],
    ["nativeShadow.saveCase", "#nativeShadowCaseBtn", null],
    ["nativeShadow.caseNow", "#nativeShadowCaseYes", null],
    ["nativeShadow.caseKeep", "#nativeShadowCaseNo", null],
  ];
  return { create, words: WORDS, REPORT, bound };
})();
