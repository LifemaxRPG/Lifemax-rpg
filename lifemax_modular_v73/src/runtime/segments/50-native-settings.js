/* Settings projection v1. The page stays the owner of every setting, its wording and its storage: the native screen draws the
   page's own Settings, card by card and row by row, read from what the page's renderers have put on the page (so a fact they
   learn asynchronously, such as the notification permission or the widget count, arrives with the next refresh), and changes
   only the "easy" settings (docs/architecture/SETTINGS_INVENTORY.md, 7.1) through commands that do on the page exactly what a
   tap on the page's own control does: a switch or a select is set and its own change handler runs, a button is clicked. So every
   rule stays the page's (the bottom bar keeps at least two tabs because lmaTabShow refuses), and each native tab's switch runs
   the same setNativeXEnabled as the page's. Everything else is a delegation: the screen closes and the page shows that card,
   on its category, with the control focused for the owner to use; the screen comes back once the owner's next tap has done its
   work, unless what it opened must stay in front (a sheet, a picker, a field being typed in, the import). The tab lifecycle is
   NativeTabKit's. Inventory: docs/architecture/SETTINGS_INVENTORY.md (section 9 lists what this changed). */
(function () {
  const PREFS = LMA_PREF_KEY,
    CORE = STORE;
  // a write that did not reach the device: the core save names its own reason (read-only or full); other keys can only be full
  const NOT_STORED = "Could not save to this browser (storage full?)";
  // a control the owner is working in on the page: a text, number or time field, or a select (its list, then the form's own button)
  const EDITING = "input:not([type=checkbox]):not([type=radio]):not([type=file]):not([type=button]), textarea, select";
  const FIELD_INPUT = { text: "text", number: "number", range: "number", time: "time" }; // the field kinds the screen draws
  const COMEBACK_MS = 350; // after the owner's tap on the page: time for its handler to open what it opens before the screen returns
  /* The controls a command may operate, by the id the projection gives them, with the storage their handler writes (checked after
     the handler ran, so a write that failed is reported) and, for a switch, whether turning it on asks Android for a permission:
     those are turned on on the page (a delegation) and off by command. Every other control on the page is a delegation. */
  const BOUND = [
    ["lightMode", "#lightModeToggle", "lifeMaxTheme"],
    ["nativeMode", "#nativeModeToggle", PREFS],
    ["nativeMode.customize", "#nativeModeCustomize", "lma_set_cat"],
    ["nativeNutrition", "#nativeNutritionToggle", PREFS],
    ["tabTiming", "#tabTimingToggle", PREFS],
    ["nativeOverview", "#nativeOverviewToggle", PREFS],
    ["nativeOverviewCards", "#nativeOverviewCardsToggle", PREFS],
    ["nativeCats", "#nativeCatsToggle", PREFS],
    ["nativePet", "#nativePetToggle", PREFS],
    ["nativeCharacter", "#nativeCharacterToggle", PREFS],
    ["nativeLog", "#nativeLogToggle", PREFS],
    ["nativePlan", "#nativePlanToggle", PREFS],
    ["nativeDirector", "#nativeDirectorToggle", PREFS],
    ["nativeRecipes", "#nativeRecipesToggle", PREFS],
    ["nativeLibrary", "#nativeLibraryToggle", PREFS],
    ["nativeJournal", "#nativeJournalToggle", PREFS],
    ["nativeAudio", "#nativeAudioToggle", PREFS],
    ["nativeSettings", "#nativeSettingsToggle", PREFS],
    ["tabs.reset", "#tabsResetBtn", PREFS],
    ["swipe", "#lmaSwipeToggle", PREFS],
    ["nativeSwipe", "#nativeSwipeToggle", PREFS],
    ["swipeAnimation", "#lmaSwipeAnimToggle", PREFS],
    ["scrubSpeed", "#lmaScrubSpeed", PREFS],
    ["cardEffects", "#qfxToggle", "lma_qfx"],
    ["questSounds", "#qsfxVol", "lma_sfxvol"],
    ["cardDeal", "#qdealSel", "lma_qdeal"],
    ["cardArt", "#qpackSel", "lma_qpack"],
    ["workoutScreen", "#gwr3dSel", "lma_gwr3d"],
    ["diagnostics3d", "#g3DbgSel", "lma_g3dbg"],
    ["haptics", "#hapBody .settingsline input.check", CORE],
    ["haptics.strength", "#hapBody [role=group]", CORE],
    ["haptics.nativeScrubStrength", '#hapBody input[type="range"][aria-label="Native navigation scrub strength"]', CORE],
    ["hdCat", "#hdToggle", CORE],
    ["voice", "#lmvToggle", "lifemax_voice_enabled"],
    ["calendar", '#lmaCalBody input[aria-label="Import from my calendar"]', "lifemax_calsync_v1", true],
    ["health", '#lmaHealthBody input[aria-label="on"]', CORE, true],
    ["health.workouts", '#lmaHealthBody input[aria-label="ex"]', CORE],
    ["health.steps", '#lmaHealthBody input[aria-label="steps"]', CORE],
    ["health.sleep", '#lmaHealthBody input[aria-label="sleep"]', CORE],
    ["health.weight", '#lmaHealthBody input[aria-label="wt"]', CORE],
    ["health.goal", '#lmaHealthBody select[aria-label="Daily steps goal"]', CORE],
    ["backup", '#lmaBakBody input[aria-label="Automatic backup"]', PREFS],
    ["backup.days", '#lmaBakBody select[aria-label="How often"]', PREFS],
    ["backup.keep", '#lmaBakBody select[aria-label="Keep the newest"]', PREFS],
    ["reminders", '#lmaNotifBody input[aria-label="on"]', PREFS, true],
    ["reminders.sessions", '#lmaNotifBody input[aria-label="sessions"]', PREFS],
    ["reminders.lead", '#lmaNotifBody select[aria-label="Reminder lead time"]', PREFS],
    ["reminders.workout", '#lmaNotifBody input[aria-label="workout"]', PREFS],
    ["reminders.nudge", '#lmaNotifBody input[aria-label="nudge"]', PREFS],
    ["reminders.checkin", '#lmaNotifBody input[aria-label="checkin"]', PREFS],
    ["reminders.quiet", '#lmaNotifBody input[aria-label="quiet"]', PREFS],
  ];
  // words for the one row the page does not have: the way back to the classic page, always on the screen (inventory 7.5, 1)
  const CLASSIC = {
    label: "Classic Settings",
    hint: "The page this screen is drawn from. To stay there, turn off Native Settings under Native mode.",
  };

  let classic = false; // the owner asked for the classic page: it stays until they leave Settings or turn the screen on again
  let warmFrom = null; // while the tab previews are captured (they go through every tab): whether Settings showed before
  let revealed = false; // a card was handed to the owner: the screen stays away until their next tap there has done its work
  let picking = null; // a file input whose picker is open
  let toasts = 0; // the page's toasts so far, so the screen tells a message from a repeat of the same words
  let answered = 0; // the toasts up to here were answered by a command's reply, so they are not sent again as a notice

  // the screen edits the text, number and time fields itself once it says so (settings.fields.v1, 50-native-settings-fields.js)
  const fieldsOn = () => tab.has(NativeSettingsFields.CAPABILITY);
  const view = () => document.getElementById("view-settings");
  const shown = (el) => !el.hidden && el.style.display !== "none" && !el.matches("input[type=file]");
  const slug = (s) =>
    s
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-|-$/g, "");

  /** an element's words as the page shows them: a line break where the page breaks the line, the controls' own words left out */
  function words(el) {
    let s = "";
    (function walk(n) {
      for (const c of n.childNodes) {
        if (c.nodeType === 3) s += c.nodeValue;
        else if (c.nodeType === 1 && c.tagName === "BR") s += "\n";
        else if (c.nodeType === 1 && !c.matches("button, input, select, textarea, svg, script, style")) walk(c);
      }
    })(el);
    return s
      .split("\n")
      .map((l) => l.replace(/\s+/g, " ").trim())
      .filter(Boolean)
      .join("\n");
  }

  /** the controls a command may operate, found on the page as it is now (a renderer may have replaced them since) */
  function bindings() {
    const out = [];
    for (const [id, selector, key, asks, tag] of fieldsOn() ? BOUND.concat(NativeSettingsFields.bound()) : BOUND) {
      const el = document.querySelector(selector);
      if (el) out.push({ id, el, key, asks: !!asks, tag });
    }
    document.querySelectorAll("#hapBody [data-try]").forEach((el) => out.push({ id: "haptics.try." + el.dataset.try, el, key: null }));
    document.querySelectorAll("#tabsEditBody .tabrow[data-view]").forEach((row) => {
      const v = row.dataset.view,
        [earlier, later] = row.querySelectorAll("button.tb"),
        show = row.querySelector("input.check");
      if (earlier) out.push({ id: "tabs." + v + ".earlier", el: earlier, key: PREFS });
      if (later) out.push({ id: "tabs." + v + ".later", el: later, key: PREFS });
      if (show) out.push({ id: "tabs." + v + ".show", el: show, key: PREFS });
    });
    return out;
  }
  function target(id) {
    return bindings().find((b) => b.id === id) || null;
  }

  /* ---------- the projection: the page's own Settings, read card by card ---------- */

  function cards() {
    return [...view().querySelectorAll(".card")].filter((c) => !c.parentElement.closest(".card") && !c.hidden); // a card the page hides (demo mode) is not drawn
  }
  function cardId(card) {
    const h = card.querySelector(":scope > h3");
    return card.id || slug(h ? h.textContent : "card");
  }
  function buttonStyle(el) {
    if (/--red|--bad/.test(el.getAttribute("style") || "")) return "danger";
    return el.classList.contains("secondary") ? "secondary" : "primary";
  }
  /** one control as the screen draws it, with how it is operated: a command (toggle, choose, press) or a delegation (reveal) */
  function controlOf(el, ctx, label) {
    const b = ctx.bound.get(el),
      id = b ? b.id : el.id ? "#" + el.id : el.closest(".note") ? ctx.card + "/note." + ctx.notes++ : ctx.card + "/" + ctx.n++;
    ctx.elements.set(id, el);
    // what TalkBack calls it: the page's own name for the control when it is one (some renderers name a box by its key, "ex",
    // "sessions"), otherwise the row's title
    const enabled = !el.disabled,
      aria = el.getAttribute("aria-label") || "",
      name = / /.test(aria) ? aria : label || aria;
    if (el.matches("input[type=checkbox]")) {
      const op = b && !(b.asks && !el.checked) ? "toggle" : "reveal";
      return { kind: "switch", id, label: name, on: el.checked, enabled, op };
    }
    if (el.matches('input[type="range"]') && b && b.id === "haptics.nativeScrubStrength")
      return { kind: "select", id, label: name, value: el.value,
        options: Array.from({length:101}, (_,n)=>({value:String(n),label:n+"%"})),
        enabled, op:"choose" };
    if (el.matches("select"))
      return {
        kind: "select",
        id,
        label: name,
        value: el.value,
        options: [...el.options].map((o) => ({ value: o.value, label: o.textContent.trim() })),
        enabled,
        op: b ? "choose" : "reveal",
      };
    if (el.matches("[role=group]")) {
      const buttons = [...el.querySelectorAll("button[data-level]")],
        on = buttons.find((x) => x.getAttribute("aria-pressed") === "true");
      return {
        kind: "choice",
        id,
        label: name,
        value: on ? on.dataset.level : "",
        options: buttons.map((x) => ({ value: x.dataset.level, label: x.textContent.trim() })),
        enabled: buttons.some((x) => !x.disabled),
        op: b ? "choose" : "reveal",
      };
    }
    if (el.matches("button"))
      return {
        kind: "button",
        id,
        label: el.textContent.replace(/\s+/g, " ").trim(),
        description: el.getAttribute("aria-label") || "",
        style: buttonStyle(el),
        small: el.classList.contains("small") || el.classList.contains("tb"),
        enabled,
        op: b ? "press" : "reveal",
        ...(fieldsOn() && NativeSettingsFields.saver(b)),
      };
    if (el.matches(".avatar")) {
      const image = /url\(["']?(.*?)["']?\)/.exec(el.style.backgroundImage || "");
      return { kind: "avatar", id, label: el.title || label || "", image: image ? image[1] : "", initial: el.textContent.trim(), enabled, op: "reveal" };
    }
    // a text, number or time field: shown with what it holds, typed into on the page. The screen knows those three kinds, so any
    // other input is named as the nearest one (a slider holds a number, everything else is text); it is worked on the page either way
    const edit = fieldsOn() ? NativeSettingsFields.field(el, b) : null; // a password's text is never read
    return { kind: "field", id, label: name, value: el.type === "password" ? "" : el.value, placeholder: el.placeholder || "", input: FIELD_INPUT[el.type] || "text", enabled, op: "reveal", ...edit };
  }
  const CONTROLS = "input, select, button, [role=group], .avatar";
  function controlsIn(el, ctx, label) {
    const out = [];
    (function walk(n) {
      for (const c of n.children) {
        if (!shown(c)) continue;
        if (c.matches(CONTROLS)) out.push(controlOf(c, ctx, label));
        else walk(c);
      }
    })(el);
    return out;
  }
  /** a .settingsline: its title, its hint lines (all the muted words on its left, in order) and its controls */
  function rowOf(el, ctx) {
    if (el.querySelector("input[type=password]")) return null; // the hidden cloud-voice key row: its key never leaves the page
    const strong = el.querySelector("strong"),
      title = strong ? words(strong) : "",
      hint = [...el.querySelectorAll(".muted")].filter((m) => !m.parentElement.closest(".muted") && shown(m)).map(words).filter(Boolean).join("\n");
    return { type: "row", title, hint, danger: !!(strong && strong.classList.contains("danger")), controls: controlsIn(el, ctx, title) };
  }
  /** a bottom-bar row: the page's own row, with its arrows and its box. An extra tab (Settings) draws its icon without a
      data-px, in the bar as in the editor, so its icon is the one the page gives it (LMA_TAB_EXTRA). */
  function tabOf(el, ctx) {
    const v = el.dataset.view,
      extra = LMA_TAB_EXTRA.find((x) => x.view === v),
      [earlier, later] = el.querySelectorAll("button.tb"),
      label = words(el.querySelector("strong"));
    return {
      type: "tab",
      view: v,
      label,
      icon: el.querySelector("[data-px]")?.dataset.px || (extra ? extra.icon : ""),
      visible: !el.classList.contains("off"),
      earlier: earlier ? controlOf(earlier, ctx, label) : null,
      later: later ? controlOf(later, ctx, label) : null,
      show: controlOf(el.querySelector("input.check"), ctx, label),
    };
  }
  const boundMap = () => new Map(bindings().map((b) => [b.el, b]));
  function walkCard(card, bound = boundMap()) {
    const ctx = { card: cardId(card), n: 0, notes: 0, bound, elements: new Map() };
    const rows = [];
    let intro = "";
    const loose = []; // controls standing on their own, gathered into one row per container
    const flush = () => {
      if (loose.length) rows.push({ type: "controls", controls: loose.splice(0) });
    };
    (function walk(n, top) {
      for (const c of n.children) {
        if (!shown(c)) continue;
        if (c.matches("h3") && top) continue;
        if (top && !intro && !rows.length && c.matches(".muted") && !c.querySelector(CONTROLS)) {
          intro = words(c);
          continue;
        }
        if (c.matches(".tabrow[data-view]")) {
          flush();
          rows.push(tabOf(c, ctx));
        } else if (c.matches(".settingsline") || c.querySelector(":scope > .avatar")) {
          flush();
          const row = rowOf(c, ctx);
          if (row) rows.push(row);
        } else if (c.matches(".note")) {
          flush();
          const warn = /--warn/.test(c.getAttribute("style") || "");
          rows.push({ type: "note", text: words(c), warn, controls: controlsIn(c, ctx, "") });
        } else if (c.matches(".field")) {
          flush();
          const label = c.querySelector("label");
          rows.push({ type: "field", label: label ? words(label) : "", controls: controlsIn(c, ctx, label ? words(label) : "") });
        } else if (c.matches(CONTROLS)) {
          loose.push(controlOf(c, ctx, ""));
        } else if (c.matches(".muted, p") && !c.querySelector(CONTROLS)) {
          flush();
          const text = words(c);
          if (text) rows.push({ type: "text", text });
        } else {
          flush();
          walk(c, false);
          flush();
        }
      }
    })(card, true);
    flush();
    return { ctx, card: { id: ctx.card, title: words(card.querySelector(":scope > h3")), intro, category: setCatOf(card) || "", rows } };
  }

  /* Each toast() writes the toast's text: that is how the page's toasts are counted. One that comes while the screen shows is
     sent at once; one a command raised goes back with its reply instead. */
  const toastEl = document.getElementById("toast");
  const toastWatch = new MutationObserver((records) => {
    toasts += records.length;
    follow();
  });
  toastWatch.observe(toastEl, { childList: true });
  function latestToast() {
    toasts += toastWatch.takeRecords().length;
    return toasts;
  }
  function notice() {
    const id = latestToast();
    return toastEl.classList.contains("show") && id > answered ? { id, text: toastEl.textContent } : null;
  }

  function projection() {
    const head = view().querySelector(".pagehead"),
      bound = boundMap();
    return {
      head: {
        eyebrow: words(head.querySelector(".eyebrow")),
        title: words(head.querySelector("h2")),
        blurb: words(head.querySelector("p")),
      },
      categories: SET_CATS.map((c) => ({ id: c.id, name: c.name })),
      category: SET_CATS.some((c) => c.id === setCat) ? setCat : SET_CATS[0].id, // the decoder takes only a chip
      classic: CLASSIC,
      cards: cards().map((c) => walkCard(c, bound).card),
      notice: notice(),
    };
  }

  /* ---------- commands: what a tap on the page's own control does ---------- */

  const settle = () => new Promise((r) => setTimeout(r, 0));
  function refuse(message) {
    return { ok: false, changed: false, message };
  }
  /** Run fn and tell whether a write to key failed in it. The handlers swallow a failed write (lmaSave, setLightMode, the quest
      keys), so the write is watched for the length of the handler's synchronous part, which is where every bound handler
      writes; a read-only core save is refused by writeSave without a write. */
  function writeFailed(key, fn) {
    const real = Storage.prototype.setItem;
    let failed = false;
    Storage.prototype.setItem = function (k) {
      try {
        return real.apply(this, arguments);
      } catch (e) {
        if (k === key) failed = true;
        throw e;
      }
    };
    try {
      fn();
    } finally {
      Storage.prototype.setItem = real;
    }
    return failed || (key === CORE && !!saveReadOnly);
  }
  /** Run the page's own handler for a control, then answer whether the page took the change and whether it reached the device.
      The page's toasts raised meanwhile become the reply's message (the screen shows the reply, so they are not sent again). */
  async function operate(b, act, took) {
    const from = latestToast(),
      failed = writeFailed(b.key, act);
    await settle();
    const now = latestToast(),
      said = now > from ? toastEl.textContent : "";
    answered = Math.max(answered, now);
    const fresh = target(b.id);
    if (took && fresh && !took(fresh.el)) return refuse(said || "Nothing was changed.");
    if (b.key && failed) return { ok: false, changed: true, saved: false, message: b.key === CORE ? unsavedMessage() : NOT_STORED };
    if (b.id.startsWith("native")) window.NativeTabs?.heal(); // the safety net: no other native screen may stay over Settings
    return { ok: true, changed: true, message: said };
  }
  function control(id, kind) {
    const b = target(String(id || ""));
    if (!b || !b.el.matches(kind)) return null;
    return b;
  }
  const commands = {
    /** a switch: {id, on}. The page's box is set and its own change handler runs (for a native tab, its setNativeXEnabled). */
    async toggle({ id, on }) {
      const b = control(id, "input[type=checkbox]");
      if (!b || typeof on !== "boolean") return refuse("That switch is not on the page. Nothing was changed.");
      if (b.el.disabled) return refuse("This switch cannot be changed right now.");
      if (b.el.checked === on) return { ok: true, changed: false, message: "" };
      if (on && b.asks) return Object.assign(refuse("Android asks first: turn this on on the classic page."), { reveal: b.id });
      return operate(
        b,
        () => {
          b.el.checked = on;
          b.el.dispatchEvent(new Event("change", { bubbles: true }));
        },
        (el) => el.checked === on,
      );
    },
    /** a select or the haptic strength: {id, value}, one of the values the projection listed */
    async choose({ id, value }) {
      const b = control(id, "select, [role=group], input[type=range]");
      value = String(value ?? "");
      if (!b) return refuse("That choice is not on the page. Nothing was changed.");
      if (b.el.matches('input[type="range"]')) {
        const n=Number(value);
        if (!Number.isInteger(n) || n<0 || n>100) return refuse("Strength must be between 0 and 100%.");
        if (b.el.disabled) return refuse("Haptics are turned off.");
        if (b.el.value === value) return {ok:true,changed:false,message:""};
        return operate(b,()=>{b.el.value=value;b.el.dispatchEvent(new Event("input",{bubbles:true}));b.el.dispatchEvent(new Event("change",{bubbles:true}));},el=>el.value===value);
      }
      if (b.el.matches("select")) {
        if (![...b.el.options].some((o) => o.value === value)) return refuse("That is not one of the choices. Nothing was changed.");
        if (b.el.disabled) return refuse("This choice cannot be changed right now.");
        if (b.el.value === value) return { ok: true, changed: false, message: "" };
        return operate(
          b,
          () => {
            b.el.value = value;
            b.el.dispatchEvent(new Event("change", { bubbles: true }));
          },
          (el) => el.value === value,
        );
      }
      const pick = [...b.el.querySelectorAll("button[data-level]")].find((x) => x.dataset.level === value);
      if (!pick) return refuse("That is not one of the choices. Nothing was changed.");
      if (pick.disabled) return refuse("This choice cannot be changed right now.");
      if (pick.getAttribute("aria-pressed") === "true") return { ok: true, changed: false, message: "" };
      return operate(b, () => pick.click(), (el) => el.querySelector('[aria-pressed="true"]')?.dataset.level === value);
    },
    /** a button a command may press: {id} (the bottom bar's arrows and reset, the haptic tries) */
    async press({ id }) {
      const b = control(id, "button");
      if (!b || (b.tag && b.tag.group)) return refuse("That button is not on the page. Nothing was changed.");
      if (b.el.disabled) return refuse("This button cannot be used right now.");
      return operate(b, () => b.el.click(), null);
    },
    /** a category chip: {id}. The chip's own handler keeps the choice for the page too. */
    async category({ id }) {
      const chip = [...document.querySelectorAll("#setCatBar button[data-cat]")].find((x) => x.dataset.cat === id);
      if (!chip) return refuse("There is no such category.");
      if (setCat === id) return { ok: true, changed: false, message: "" };
      return writeFailed("lma_set_cat", () => chip.click()) ? { ok: false, changed: true, saved: false, message: NOT_STORED } : { ok: true, changed: true, message: "" };
    },
  };

  /* Each command's reply by its request id, so a request delivered twice (a retry on the way: A14) is answered again without
     running again; a bar arrow would otherwise move its tab two places. The screen gives every tap an id of its own and never
     sends one again, so a receipt only has to outlast a delivery: the last RECEIPTS of this page's life are kept. The same id
     with another command or payload is refused. */
  const RECEIPTS = 64;
  const receipts = new Map();
  function once(name, run) {
    return (payload, { requestId } = {}) => {
      if (!requestId) return run(payload);
      const asked = JSON.stringify([name, payload]),
        seen = receipts.get(requestId);
      if (seen) return seen.asked === asked ? seen.reply : refuse("That request was already answered. Nothing was changed.");
      const reply = run(payload);
      receipts.set(requestId, { asked, reply });
      if (receipts.size > RECEIPTS) receipts.delete(receipts.keys().next().value);
      return reply;
    };
  }

  /* ---------- delegations: the screen closes and the page shows the card ---------- */

  /** the card holding a control id, the control itself, or null */
  function locate(id) {
    const direct = target(id);
    if (direct) return { card: direct.el.closest(".card"), el: direct.el };
    const bound = boundMap();
    for (const card of cards()) {
      const { ctx } = walkCard(card, bound);
      if (ctx.elements.has(id)) return { card, el: ctx.elements.get(id) };
      if (ctx.card === id) return { card, el: null };
    }
    return null;
  }
  /** show the card on its category, the control in view and focused; the screen stays away until the owner's next tap */
  function reveal(id) {
    const found = locate(String(id || ""));
    if (!found || !found.card) return;
    const cat = setCatOf(found.card);
    if (cat && cat !== setCat) setPickCat(cat);
    const el = found.el && found.el.isConnected ? found.el : found.card;
    revealed = true;
    el.scrollIntoView({ block: "center" });
    if (found.el && !found.el.disabled) found.el.focus({ preventScroll: true });
  }
  /** a native action, after the screen has closed: a delegation or the classic page. An id the page no longer has, or an action
      it does not know, brings the screen straight back. */
  function onAction(e) {
    if (e.action === "reveal") reveal(e.id);
    else if (e.action === "classic") classic = true;
    follow();
  }

  function editing() {
    const a = document.activeElement;
    return !!(a && a.closest && a.closest("#view-settings") && a.matches(EDITING));
  }
  function importing() {
    return !!document.getElementById("importFile")?.value || saveReplacing;
  }
  /* The covering rules: the page must stay in front while a sheet or overlay is up (the kit already covers lmaTopOverlay: the
     rewards, voice diagnostics and health sheets, the update pop-up, onboarding and the tutorial), the dev chat or a level-up
     shows, a file picker is open, the JSON import or a reset is under way, a field is being typed in, a card was just handed to
     the owner, the owner chose the classic page, the tab previews are being captured (they go through every tab), or the page
     is about to reload (lmaLeaveNative). */
  function blocked() {
    return (
      classic ||
      revealed ||
      !!picking ||
      lmaReloading ||
      importing() ||
      lmaThumbWarmBusy ||
      editing() ||
      !!document.querySelector("#devChat.open, .luov")
    );
  }

  const tab = NativeTabKit.create({
    id: "settings",
    plugin: "NativeSettings",
    capability: "settings.projection.v1",
    setting: "nativeSettings",
    toggle: "nativeSettingsToggle",
    projection,
    onAction,
    commands: Object.fromEntries(Object.entries({ ...commands, ...NativeSettingsFields.commands({ operate, refuse, target, enabled: fieldsOn }) }).map(([name, run]) => [name, once(name, run)])),
    blocked,
    globals: { open: "openNativeSettings", enable: "setNativeSettingsEnabled", projection: "NativeSettingsTabProjection" },
  });
  // turned on from the classic page, the screen comes at once (the switch's box is not a field, so nothing keeps it away), the
  // classic page asked for before included
  const enable = window.setNativeSettingsEnabled;
  window.setNativeSettingsEnabled = async (on) => {
    await enable(on);
    if (on) {
      classic = false;
      tab.revisit();
    }
  };

  /** the page changed: step aside if it now needs to be in front, come back if it no longer does, refresh what shows */
  function follow() {
    if (tab.blocked()) tab.send();
    else {
      tab.revisit();
      tab.schedule();
    }
  }
  const later = () => setTimeout(follow, COMEBACK_MS);
  // leaving Settings ends the classic page, a hand-over to the owner and a picker left open; the view's class says so (navigate
  // switches it), so no page function is wrapped for it. The tab previews' capture goes through every tab and back without the
  // owner leaving: it clears the flag only for its own return to Settings (lmaThumbWarm clears it in the same task)
  const isActive = (cls) => ` ${cls || ""} `.includes(" active ");
  new MutationObserver((records) => {
    if (lmaThumbWarmBusy) {
      if (warmFrom === null) warmFrom = isActive(records[0].oldValue);
      return;
    }
    const captured = warmFrom;
    warmFrom = null;
    if (captured && view().classList.contains("active")) return; // the capture is back where it began
    // a class that was without "active" at some point since the last call: the owner left, even if already back
    if (!records.some((r) => !isActive(r.oldValue)) && view().classList.contains("active")) return;
    classic = false;
    revealed = false;
    picking = null;
  }).observe(view(), { attributes: true, attributeFilter: ["class"], attributeOldValue: true });
  // every card's renderer redraws inside the view: the screen follows (an async fact arriving, a download's progress, a control
  // enabled or shown once a plugin has answered)
  new MutationObserver(follow).observe(view(), {
    childList: true,
    subtree: true,
    characterData: true,
    attributes: true,
    attributeFilter: ["disabled", "style", "hidden"],
  });
  // sheets and overlays are added to and removed from the body; the tutorial, the onboarding preview and the dev chat show and
  // hide by their class (the dev chat is built on its first opening)
  const overlays = new MutationObserver(follow),
    watched = new WeakSet();
  const watchOverlays = () =>
    ["tutorial", "onboarding", "devChat"].forEach((id) => {
      const el = document.getElementById(id);
      if (el && !watched.has(el)) {
        watched.add(el);
        overlays.observe(el, { attributes: true, attributeFilter: ["class"] });
      }
    });
  watchOverlays();
  new MutationObserver(() => {
    watchOverlays();
    follow();
  }).observe(document.body, { childList: true });
  document.addEventListener(
    "click",
    (e) => {
      if (e.target.matches?.("#view-settings input[type=file]")) picking = e.target;
      if (lmaView() !== "settings") return;
      revealed = false; // the owner used the page: once the tap has done its work, the screen may come back
      later();
    },
    true,
  );
  const picked = (e) => {
    if (e.target === picking) {
      picking = null;
      if (importing()) waitForImport();
      later();
    }
  };
  document.addEventListener("change", picked, true);
  document.addEventListener("cancel", picked, true);
  document.addEventListener(
    "focusout",
    (e) => {
      if (e.target.matches?.(EDITING) && e.target.closest("#view-settings")) later(); // once a tap that moved the focus has run
    },
    true,
  );
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") later();
  });
  /** the import ends without an event of its own (the page empties the file input when it is done): look until it has */
  function waitForImport() {
    const t = setInterval(() => {
      if (importing()) return;
      clearInterval(t);
      follow();
    }, 500);
  }
})();
