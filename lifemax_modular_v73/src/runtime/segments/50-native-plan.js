/* Workout plan projection v1. The page stays the owner of the plan, its XP and the save: the native screen draws this projection
   of every week and day, selects days and weeks itself, and ticks boxes through the page's own planToggle. Everything else (the
   guided workout, editing a value or a session log, leaving the plan) closes the screen and runs the page's own handler, unless
   the screen says it draws it: the runner (plan.runner.v1, 50-native-plan-runner.js), the value and session-log fields
   (plan.fields.v1), Leave plan (plan.leave.v1) and the sign-up offers shown without a plan (plan.offers.v1) are then commands
   the page answers, with the page's own functions and refusals. The tab lifecycle is NativeTabKit's.
   Inventory: docs/architecture/PLAN_INVENTORY.md. */
(function () {
  const WEEKDAYS = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];
  const KINDS = { strength: "STRENGTH", rest: "REST", recovery: "RECOVERY", cardio: "CARDIO" };
  const PROGRAMS = {
    scholar_light8: "Scholar · 8-Week Light Program",
    entrepreneur_light8: "Entrepreneur · 8-Week Light Program",
    explorer_outdoor8: "Explorer · 8-Week Outdoor Program",
    athlete_beginner8: "Athlete · 8-Week Beginner Program",
    athlete_foundation8: "Athlete · 8-Week Athletic Foundation Program",
  };
  const EDITING = "#planBody .pv, #planBody .plog"; // the page's own value and session-log fields
  const FIELDS = { value: ".pv", sessionLog: ".plog" };
  // the page's date wording (toLocaleDateString), with the formatters made once: there are some 130 dates in a projection
  const DATES = {
    day: new Intl.DateTimeFormat(undefined, { weekday: "short", month: "short", day: "numeric" }),
    long: new Intl.DateTimeFormat(undefined, { month: "long", day: "numeric" }),
    short: new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }),
  };
  const scratch = document.createElement("template");
  const Runner = window.NativePlanRunner;
  let tab = null;
  /** whether the screen says it draws a part of the view (the handshake; false until it has answered) */
  const has = (capability) => !!tab && tab.has(capability);
  let selectedDate = "",
    selectedRevision = 0;

  /** some programs write their text as escaped HTML; the screen takes plain text */
  function plain(html) {
    const s = String(html ?? "");
    if (!/[&<]/.test(s)) return s;
    scratch.innerHTML = s;
    return scratch.content.textContent;
  }
  function dateText(k, format) {
    return DATES[format].format(parseD(k));
  }
  /** the plan's day for a date the screen sent, or null */
  function validDay(date) {
    return state.plan && /^\d{4}-\d{2}-\d{2}$/.test(date) ? planDay(date) : null;
  }

  /** the guided workout launcher, worded as gwrLauncher words it */
  function launcher(title, detail, button, enabled) {
    return { title, detail, button, enabled };
  }
  function launcherFor(k, d, t) {
    if (d.kind === "rest") return launcher("Guided workout", "Rest day. Pick a training day below to start a session.", "Start", false);
    if (k > t) return launcher("Guided workout", "Pick today or an earlier training day below.", "Start", false);
    const sets = d.items.reduce((n, it) => n + it.n, 0);
    return launcher(
      "Guided workout",
      plain(d.label) + " · " + d.items.length + " exercises · " + sets + " sets",
      dayDone(k) ? "Review" : "Start",
      true,
    );
  }
  /** a saved guided session takes the launcher's place on every day until it is finished */
  function session() {
    const g = gwrCurrent();
    if (!g) return null;
    const d = planDay(g.date),
      s = gwrStats(g);
    return launcher(
      "Workout in progress",
      plain(d ? d.label : "Session") + " · " + s.doneReq.length + " of " + s.needed.length + " sets done",
      "Resume",
      true,
    );
  }

  function itemOf(it, k, c) {
    const ticked = (c.s || {})[it.k] || [],
      log = (c.log || {})[it.k] || "",
      previous = lastLog(it.k, k),
      last = it.wt || it.min ? lastVal(it.k, k) : "";
    return {
      key: it.k,
      title: plain(it.l),
      detail: plain(it.d),
      optional: !!it.opt,
      sets: Array.from({ length: it.n }, (_, i) => !!ticked[i]),
      value:
        it.wt || it.min
          ? { text: (c.v || {})[it.k] || "", placeholder: it.wt ? "Weight used" : "Minutes done", last: last ? "last: " + last : "" }
          : null,
      log: { text: log, previous: previous ? (log ? "Previous: " : "Last time: ") + previous : "" },
    };
  }
  /** a day as its tile and its card show it; null where the plan has no day (before it starts) */
  function dayOf(k, i, t) {
    const d = planDay(k);
    if (!d) return null;
    const c = pcheck(k),
      label = plain(d.label);
    return {
      date: k,
      weekday: WEEKDAYS[i],
      dayOfMonth: String(parseD(k).getDate()),
      kind: d.kind,
      kindLabel: KINDS[d.kind] || "ATHLETIC",
      glyph: d.kind === "strength" ? d.code || d.label.replace(/[^A-Z]/g, "").slice(-1) || "S" : "",
      icon: d.icon,
      status: dayCls(k) || "upcoming",
      today: k === t,
      title: dateText(k, "day") + " · " + label,
      description: label + ", " + dateText(k, "long"),
      tag: "Week " + d.week + " · " + (d.kind === "rest" ? "rest" : dayPct(k) + "% done"),
      note: plain(d.note),
      locked: k > t,
      items: d.items.map((it) => itemOf(it, k, c)),
      guided: launcherFor(k, d, t),
    };
  }
  function weekOf(p, w, t, current) {
    const start = addDays(p.start, w * 7),
      days = WEEKDAYS.map((_, i) => dayOf(addDays(start, i), i, t)),
      training = days.filter((d) => d && d.kind !== "rest"),
      done = training.filter((d) => d.status === "done").length,
      number = String(w + 1).padStart(2, "0");
    return {
      number,
      label: "WEEK " + number,
      range: dateText(start, "short") + " — " + dateText(addDays(start, 6), "short"),
      meta: done + " of " + training.length + " sessions",
      done,
      total: training.length,
      complete: training.length > 0 && done === training.length,
      current,
      days,
    };
  }
  /** the page's selected day (renderPlan's default when it has none); the revision rises whenever the page changes it, so the
      screen follows a day chosen on the page (Life Director, voice, a delegated action) and otherwise keeps its own */
  function selected(p, t) {
    const date = planSel && planDay(planSel) ? planSel : planDay(t) ? t : planDay(p.from) ? p.from : p.start;
    if (date !== selectedDate) {
      selectedDate = date;
      selectedRevision++;
    }
    return { date, revision: selectedRevision };
  }
  function key(p) {
    const light = !!CLASS_PLAN_KEY[p.id],
      foundation = p.id === "athlete_foundation8";
    return [
      { icon: "", label: light ? "A / C · Strength" : foundation ? "A / C / E · Strength" : "A / B / C · Strength" },
      { icon: "heart", label: "Cardio" },
      { icon: "moon", label: "Recovery" },
      ...(light || foundation ? [] : [{ icon: "bolt", label: "Athletic" }]),
      { icon: "bed", label: "Rest" },
    ];
  }
  /** the General rules the page writes for this program, as lines with their bold lead */
  function rules(p) {
    scratch.innerHTML = CLASS_PLAN_KEY[p.id]
      ? scholarRulesHtml()
      : p.id === "athlete_beginner8"
        ? legacyAthleteRulesHtml()
        : athleteRulesHtml();
    const line = (el) => {
      const lead = el.querySelector("strong")?.textContent || "";
      return { lead, text: el.textContent.slice(lead.length).trim() };
    };
    return {
      items: [...scratch.content.querySelectorAll("li")].map(line),
      progression: [...scratch.content.querySelectorAll("div p")].map(line),
    };
  }

  /** what the page offers with no plan, worded by the same data the page draws its offer from (planOfferFor) */
  function offer() {
    const o = planOfferFor(state.classId);
    return {
      eyebrow: planOfferWords().eyebrow,
      title: plain(o.title),
      blurb: plain(o.blurb),
      rows: o.rows.map(([when, what]) => ({ when: plain(when), what: plain(what) })),
      note: plain(o.note),
      startLabel: planOfferWords().start,
      start: getToday(),
      button: planOfferWords().button,
    };
  }
  /** which of the page's hand-overs the screen takes over: the page tells it what it may do itself */
  function features() {
    return { runner: Runner.offered(), fields: has("plan.fields.v1"), leave: has("plan.leave.v1"), offers: has("plan.offers.v1") };
  }

  function projection() {
    if (!state.plan)
      return { offer: offer(), features: features(), reducedMotion: fxReduced(), celebrationTarget: NativeTabKit.celebrationTarget() };
    const p = state.plan,
      t = getToday(),
      over = t > addDays(p.start, 55),
      week = Math.min(8, Math.max(1, Math.floor((dayNum(t) - dayNum(p.start)) / 7) + 1)),
      weeks = Array.from({ length: 8 }, (_, w) => weekOf(p, w, t, !over && w === week - 1)),
      due = weeks.flatMap((w) => w.days).filter((d) => d && d.kind !== "rest" && d.date <= t),
      done = due.filter((d) => d.status === "done").length,
      total = weeks.reduce((n, w) => n + w.total, 0);
    return {
      program: PROGRAMS[p.id] || PROGRAMS.athlete_foundation8,
      status: t < p.from ? "Starts " + parseD(p.from).toLocaleDateString() : over ? "Program complete" : "Week " + week + " of 8",
      summary: done + " of " + total + " sessions done · " + (due.length ? Math.round((done / due.length) * 100) : 0) + "% on track",
      sessions: { done, total },
      weeks,
      selected: selected(p, t),
      session: session(),
      key: key(p),
      rules: rules(p),
      reducedMotion: fxReduced(),
      celebrationTarget: NativeTabKit.celebrationTarget(),
      offer: null,
      features: features(),
      runner: Runner.projection(),
      leaveAsk: LEAVE_PLAN_ASK,
      logMax: PLAN_LOG_MAX,
    };
  }

  /** a value or session-log field, set to what was typed: like the page's onchange (planVal, planLog, which trim and save), with
      the page's refusals. The field ends as asked, so repeating the command changes nothing. */
  function field(kind) {
    return (c) => {
      const d = validDay(c.date),
        it = d && d.items.find((x) => x.k === c.key);
      if (!has("plan.fields.v1")) return { ok: false, message: "Edit this on the page." };
      if (!it || typeof c.text !== "string" || (kind === "value" && !(it.wt || it.min)))
        return { ok: false, message: "That field is not in your plan. Nothing was changed." };
      if (c.date > getToday()) return { ok: false, message: "Locked until the day arrives." };
      if (kind === "log" && c.text.length > PLAN_LOG_MAX)
        return { ok: false, message: "A session log holds " + PLAN_LOG_MAX + " characters at most. Nothing was changed." };
      if (((pcheck(c.date)[kind === "value" ? "v" : "log"] || {})[c.key] || "") === c.text.trim())
        return { ok: true, changed: false, message: "Already recorded." };
      const saved = kind === "value" ? planVal(c.date, c.key, c.text) : planLog(c.date, c.key, c.text);
      return saved ? { ok: true, changed: true } : { ok: false, changed: true, message: unsavedMessage() };
    };
  }

  const commands = {
    value: field("value"),
    log: field("log"),
    /** the guided workout's step of the screen (50-native-plan-runner.js): act and its arguments */
    runner: (c) => Runner.command(c),
    /** Leave plan, after the screen's own confirm in the page's words (leaveAsk): the page's leavePlan, which removes the plan
        and keeps the XP. With no plan left it is a repeat. */
    leave(c) {
      if (!has("plan.leave.v1")) return { ok: false, message: "Leave the plan on the page." };
      return Runner.once(c.op, () => {
        if (!state.plan) return { ok: true, changed: false, message: "Already recorded." };
        Runner.ended();
        return leavePlan(true) ? { ok: true, changed: true, message: PLAN_ENDED } : { ok: false, changed: true, message: unsavedMessage() };
      });
    },
    /** Sign up for the offered plan from the start date chosen: the page's startPlan */
    signUp(c) {
      if (!has("plan.offers.v1")) return { ok: false, message: "Sign up on the page." };
      const start = String(c.start || "");
      if (!/^\d{4}-\d{2}-\d{2}$/.test(start) || dstr(parseD(start)) !== start) return { ok: false, message: "Choose a start date." };
      return Runner.once(c.op, () => {
        if (state.plan) {
          if (state.plan.from === start) return { ok: true, changed: false, message: "Already recorded." };
          return { ok: false, message: "You already follow a plan. Nothing was changed." };
        }
        return startPlan(start) ? { ok: true, changed: true, message: PLAN_STARTED } : { ok: false, changed: true, message: unsavedMessage() };
      });
    },
    /** tick or clear one set, like the page's checkbox. The set ends as asked, so repeating the command changes nothing; a day
        completed by it pays its XP once (planToggle keeps the session's activity id). A failed save leaves the change in memory,
        as the page's own checkbox does, with what the save set off (badges, a daily-hand quest), for the next save that succeeds
        (none while the save is read-only): the reply says nothing was saved and why, and the projection sent with it shows the
        boxes as the page holds them. */
    toggle(c) {
      const d = validDay(c.date),
        it = d && d.items.find((x) => x.k === c.key),
        i = Number(c.set),
        checked = c.checked === true;
      if (!it || !Number.isInteger(i) || i < 0 || i >= it.n)
        return { ok: false, message: "That set is not in your plan. Nothing was changed." };
      if (c.date > getToday()) return { ok: false, message: "Locked until the day arrives." };
      if (!!((pcheck(c.date).s || {})[c.key] || [])[i] === checked) return { ok: true, changed: false };
      // the celebration would play under the screen; the screen draws it from the reply and the page adds the sound and buzz
      const { result, held } = holdCelebrations(() => planToggle(c.date, c.key, i, checked));
      if (!result.saved) return { ok: false, message: unsavedMessage() };
      return { ok: true, changed: true, message: result.message, celebrations: NativeTabKit.celebrations(held) };
    },
  };

  /** show a day's value or session-log field on the page, focused; the screen stays away while the field has the focus */
  function edit(date, k, field) {
    const d = validDay(date),
      index = d && date <= getToday() ? d.items.findIndex((it) => it.k === k) : -1;
    if (index < 0) return;
    selectDay(date);
    const input = document.querySelectorAll("#planBody .pit")[index]?.querySelector(field);
    if (!input) return;
    input.scrollIntoView({ block: "center" });
    input.focus({ preventScroll: true });
  }
  /** a native action, after the screen has closed: run the page's own handler, then come back unless the page now shows
      something the screen does not draw (the guided workout, a focused field, the offers after leaving the plan) */
  function onAction(e) {
    const [date, k] = String(e.id || "").split("/");
    if (e.action === "guided") {
      if (gwrCurrent()) gwrResume();
      else if (validDay(date)) gwrStart(date);
    } else if (FIELDS[e.action]) edit(date, k, FIELDS[e.action]);
    else if (e.action === "leave") leavePlan();
    tab.revisit();
  }

  tab = NativeTabKit.create({
    id: "plan",
    plugin: "NativePlan",
    capability: "plan.projection.v1",
    setting: "nativePlan",
    toggle: "nativePlanToggle",
    projection,
    onAction,
    commands,
    render: "renderPlan",
    // no plan: the page's offer shows unless the screen draws it; a field of the page being edited, a level-up: the page shows
    blocked: () => (!state.plan && !has("plan.offers.v1")) || !!document.activeElement?.matches(EDITING) || !!document.querySelector(".luov"),
    warm: true, // whether the screen draws the offers has to be known before it is opened
    afterClose: () => Runner.afterClose(),
    // The page covers its view now and then (the guided workout, a level-up, a sheet, a field being edited) and brings the plan
    // back (the runner closed, a plan started from an offer): the screen steps aside for it and comes back after.
    watch(observe) {
      observe(document.getElementById("planBody"), { childList: true });
    },
    globals: { open: "openNativePlan", enable: "setNativePlanEnabled", projection: "NativePlanTabProjection" },
  });

  Runner.bind(tab);
  function leaveField(e) {
    if (e.target.matches?.(EDITING)) setTimeout(tab.follow); // once the page's own change handler has run and the focus has moved
  }
  document.addEventListener("focusout", leaveField, true);
  document.addEventListener("change", leaveField, true);
})();
