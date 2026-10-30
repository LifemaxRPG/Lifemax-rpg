/* Workout plan projection v1. The page stays the owner of the plan, its XP and the save: the native screen draws this projection
   of every week and day, selects days and weeks itself, and ticks boxes through the page's own planToggle. Everything else (the
   guided workout, editing a value or a session log, leaving the plan) closes the screen and runs the page's own handler; the
   sign-up offers shown without a plan stay on the page. The tab lifecycle is NativeTabKit's. Inventory: docs/architecture/PLAN_INVENTORY.md. */
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
  const CUE_AFTER_MS = 120; // the sound and buzz of a celebration, once the reply carrying it has reached the screen
  // the page's date wording (toLocaleDateString), with the formatters made once: there are some 130 dates in a projection
  const DATES = {
    day: new Intl.DateTimeFormat(undefined, { weekday: "short", month: "short", day: "numeric" }),
    long: new Intl.DateTimeFormat(undefined, { month: "long", day: "numeric" }),
    short: new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }),
  };
  const scratch = document.createElement("template");
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

  /** where celebrateXP's orbs fly: the centre of the top bar's logo, from the top of the page, as the shared bar draws it */
  function celebrationTarget() {
    const r = document.getElementById("brandLogo")?.getBoundingClientRect();
    return r && r.width ? { x: r.x + r.width / 2, y: r.y + scrollY + r.height / 2 } : { x: innerWidth / 2, y: 44 };
  }

  function projection() {
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
      celebrationTarget: celebrationTarget(),
    };
  }

  /** what an attribute gain looks like on the screen: the page's colour and short name for it */
  function gainOf(g) {
    const a = ATTRS.find((x) => x.id === g.id);
    return { attribute: g.id, short: a ? a.short : g.id, xp: g.xp, color: NativeTabKit.color(ATTR_COL[g.id] || "#6c8cff") };
  }
  const commands = {
    /** tick or clear one set, like the page's checkbox. The set ends as asked, so repeating the command changes nothing; a day
        completed by it pays its XP once (planToggle keeps the session's activity id). A failed save leaves the change in memory,
        as the page's own checkbox does, with what the save set off (badges, a daily-hand quest), and the next save stores it all:
        the reply says nothing was saved, and the projection sent with it shows the boxes as the page holds them. */
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
      if (!result.saved) return { ok: false, message: "Could not save to this browser (storage full?)" };
      if (held.length) setTimeout(() => held.forEach((h) => xpCue(h[0])), CUE_AFTER_MS);
      return {
        ok: true,
        changed: true,
        message: result.message,
        celebrations: held.map(([xp, gains]) => ({ xp, gains: gains.map(gainOf) })),
      };
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

  const tab = NativeTabKit.create({
    id: "plan",
    plugin: "NativePlan",
    capability: "plan.projection.v1",
    setting: "nativePlan",
    toggle: "nativePlanToggle",
    projection,
    onAction,
    commands,
    render: "renderPlan",
    blocked: () => !state.plan || !!document.activeElement?.matches(EDITING) || !!document.querySelector(".luov"),
    globals: { open: "openNativePlan", enable: "setNativePlanEnabled", projection: "NativePlanTabProjection" },
  });

  /** The page covers its view now and then (the guided workout, a level-up, a sheet, a field being edited) and brings the plan
      back (the runner closed, a plan started from an offer): step aside for it, and come back after. */
  function follow() {
    if (tab.blocked()) tab.send();
    else tab.revisit();
  }
  function leaveField(e) {
    if (e.target.matches?.(EDITING)) setTimeout(follow); // once the page's own change handler has run and the focus has moved
  }
  const watch = new MutationObserver(follow);
  watch.observe(document.body, { attributes: true, attributeFilter: ["class"], childList: true });
  watch.observe(document.getElementById("planBody"), { childList: true });
  document.addEventListener("focusout", leaveField, true);
  document.addEventListener("change", leaveField, true);
})();
