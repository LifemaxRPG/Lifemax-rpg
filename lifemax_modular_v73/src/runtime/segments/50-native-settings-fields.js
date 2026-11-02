/* Settings fields v1: the text, number and time fields of Settings that the native screen edits itself (capability
   settings.fields.v1; without it every one of them is still handed to the page, as before). Loaded before 50-native-settings.js,
   which asks it which controls are fields, which buttons save a form, and for the two commands.
   The page stays the writer and every rule stays the page's:
   - a time (the Notifications card's) is set and the input's own change handler runs, which is lmaSet(key, value);
   - the profile, the decay settings and the XP rules are forms the page saves from its inputs (saveCharacter, saveDecay,
     saveRules read the DOM), so the screen sends the form's values together, they are put in the page's inputs, and the page's own
     save function runs, which clamps them (1-14, 1-10, 1-200) and words the result;
   - a refusal or a write that did not reach the device is the reply (ok only when stored), and a repeat is harmless: the same
     values give the same save, and a request id seen twice is answered from the receipt (50-native-settings.js once()).
   A key or any password input is never read here: the cloud-voice key row is not projected (50-native-settings.js rowOf), and
   nothing below takes an id it was not given by name. */
(function () {
  const PREFS = LMA_PREF_KEY,
    CORE = STORE;
  const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;
  const NUMBER = /^\d*\.?\d*$/; // what a number box holds: digits and one point, or nothing
  // the Notifications card's times, by the key lmaSet is given; the input is found by that key in its change handler
  const TIMES = [
    ["reminders.workoutTime", "workoutTime"],
    ["reminders.checkinTime", "checkinTime"],
    ["reminders.quietFrom", "quietFrom"],
    ["reminders.quietTo", "quietTo"],
  ];
  /* The forms: the page's save button, its save function, and the ids of the inputs it reads. The XP rules are the page's own
     list (rules), so a rule the page adds shows up here with no change. */
  const FORMS = {
    profile: { button: 'button[onclick="saveCharacter()"]', run: () => saveCharacter(), ids: () => ["characterName", "characterWeight", "characterHeightFt", "characterHeightIn"] },
    decay: { button: 'button[onclick="saveDecay()"]', run: () => saveDecay(), ids: () => ["decayGrace", "decayRate"] },
    rules: { button: 'button[onclick="saveRules()"]', run: () => saveRules(), ids: () => Object.keys(rules).map((id) => "rule-" + id) },
  };
  const KINDS = ["text", "number", "time"]; // a slider (the swipe feel) is none of these: it stays with the page

  /** the controls the screen operates beyond the main list: [id, selector, key written, asks, tag] as BOUND is */
  function bound() {
    const out = TIMES.map(([id, key]) => [id, `#lmaNotifBody input[type=time][onchange*="'${key}'"]`, PREFS, false, { field: true }]);
    for (const group of Object.keys(FORMS)) out.push(["save." + group, FORMS[group].button, CORE, false, { group }]);
    return out;
  }
  const formOf = (el) => Object.keys(FORMS).find((g) => FORMS[g].ids().includes(el.id)) || "";
  const limit = (v) => (v === null || v === "" || !Number.isFinite(Number(v)) ? null : Number(v));

  /** what the screen needs to edit this input, or null when it is not one it edits */
  function field(el, b) {
    if (!KINDS.includes(el.type) || el.type === "password") return null;
    const timed = !!(b && b.tag && b.tag.field),
      group = timed ? "" : formOf(el);
    if (!timed && !group) return null;
    return {
      op: "edit",
      group,
      min: limit(el.getAttribute("min")),
      max: limit(el.getAttribute("max")),
      step: limit(el.getAttribute("step")),
      maxLength: el.maxLength >= 0 ? el.maxLength : null,
    };
  }
  /** a form's save button: it sends the form's values (the screen holds them), so it is not pressed by a command */
  const saver = (b) => (b && b.tag && b.tag.group ? { op: "save", group: b.tag.group } : null);

  /** deps: the main file's operate (the page's handler, then ok only once stored), refuse and target */
  function commands({ operate, refuse, target, enabled }) {
    // a screen that did not say it edits fields is never answered as if it did
    const gate = (run) => (payload) => (enabled() ? run(payload) : refuse("The screen does not edit fields. Nothing was changed."));
    const same = (el, v) => (el.type === "number" && v !== "" && el.value !== "" ? Number(el.value) === Number(v) : el.value === v);
    const run = {
      /** a time: {id, value} as "HH:MM". The input's change handler runs, which is the page's lmaSet. */
      async setField({ id, value }) {
        const b = target(String(id || ""));
        if (!b || !b.tag || !b.tag.field || !b.el.matches("input[type=time]")) return refuse("That field is not on the page. Nothing was changed.");
        if (typeof value !== "string" || !TIME.test(value)) return refuse("That is not a time. Nothing was changed.");
        if (b.el.disabled) return refuse("This field cannot be changed right now.");
        if (b.el.value === value) return { ok: true, changed: false, message: "" };
        return operate(
          b,
          () => {
            b.el.value = value;
            b.el.dispatchEvent(new Event("change", { bubbles: true }));
          },
          (el) => el.value === value,
        );
      },
      /** a form: {group, values: {inputId: text}}. The values go into the page's inputs, then its own save function runs. */
      async saveForm({ group, values }) {
        const form = FORMS[group],
          button = form && document.querySelector(form.button);
        if (!form || !button || !values || typeof values !== "object" || Array.isArray(values)) return refuse("That form is not on the page. Nothing was changed.");
        const ids = form.ids(),
          given = Object.entries(values);
        const inputs = [];
        for (const [id, v] of given) {
          const el = ids.includes(id) ? document.getElementById(id) : null;
          if (!el || !KINDS.includes(el.type)) return refuse("That field is not on the page. Nothing was changed.");
          if (typeof v !== "string" || (el.type === "number" && !NUMBER.test(v))) return refuse("That is not a number. Nothing was changed.");
          if (el.maxLength >= 0 && v.length > el.maxLength) return refuse("That is longer than the page allows. Nothing was changed.");
          inputs.push([el, v]);
        }
        const changed = inputs.some(([el, v]) => !same(el, v));
        const reply = await operate(
          { id: "form:" + group, el: button, key: CORE },
          () => {
            for (const [el, v] of inputs) el.value = v;
            form.run();
          },
          null,
        );
        return reply.ok && !changed ? Object.assign(reply, { changed: false }) : reply;
      },
    };
    return { setField: gate(run.setField), saveForm: gate(run.saveForm) };
  }

  window.NativeSettingsFields = { CAPABILITY: "settings.fields.v1", bound, field, saver, commands };
})();
