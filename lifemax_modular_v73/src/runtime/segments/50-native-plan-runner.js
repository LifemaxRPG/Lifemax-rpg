/* The guided workout for the native Workout plan screen (capability plan.runner.v1). The page keeps the session, its timers, the
   sets it logs and the XP: gwrStart, gwrBegin, gwrGo, gwrLogSet and the rest run here exactly as the overlay's buttons run them.
   While the screen shows and draws the runner, nothing opens the overlay (gwrHost.on()); this module projects the session as the
   page's own screens would show it (`runner`) and answers the screen's `runner` command. Words come from GWR_WORDS (21-runtime),
   which the page's screens draw too. Inventory: docs/architecture/PLAN_INVENTORY.md. Loaded before 50-native-plan.js, which hands
   it its tab. */
(function () {
  const CAPABILITY = "plan.runner.v1";
  const LANDED = 60; // operations remembered, so one that landed and is sent again is not done twice
  const scratch = document.createElement("template");
  let tab = null,
    opened = false; // the screen started or resumed a session in this page's life: only then does it draw the runner
  const landed = [];

  /** some programs write their text as escaped HTML; the screen takes plain text */
  function plain(html) {
    const s = String(html ?? "");
    if (!/[&<]/.test(s)) return s;
    scratch.innerHTML = s;
    return scratch.content.textContent;
  }
  /** whether the screen draws the runner now: it shows, it says it can, and the 3D gym (which the page draws) is not wanted */
  function on() {
    return !!tab && tab.active() && tab.has(CAPABILITY) && !(typeof g3Want === "function" && g3Want());
  }
  /** whether the page may hand the runner to a screen that could draw it (the 3D gym stays the page's) */
  function offered() {
    return !!tab && tab.has(CAPABILITY) && !(typeof g3Want === "function" && g3Want());
  }
  function changed() {
    const g = gwrCurrent();
    if (!g || g.phase === "paused") opened = false;
    if (tab) tab.schedule();
  }

  /* What a step shows, from the page's own helpers (gwrNumTarget, gwrStepNext, gwrCue, gwrSuggestedWeight) */
  function stepOf(g, d, st) {
    const c = state.plan.checks[g.date] || {},
      it = st.it,
      info = gwrNumTarget(it),
      ticked = (c.s || {})[it.k] || [],
      prev = lastVal(it.k, g.date) || "",
      timed = info.kind === "seconds" || info.kind === "minutes",
      suggested = Number(info.suggest);
    return {
      key: it.k,
      number: st.ex + 1,
      count: d.items.length,
      heading: gwrExText(st, d),
      setText: gwrSetText(st),
      optional: !!it.opt,
      name: c.substitute?.[it.k] || plain(it.l),
      prescription: plain(it.d),
      set: st.j + 1,
      sets: it.n,
      pips: Array.from({ length: it.n }, (_, i) => (ticked[i] ? "done" : i === st.j ? "current" : "")),
      kind: info.kind,
      unit: info.kind === "reps" ? "reps" : timed ? info.kind : "",
      label: plain(info.label),
      suggest: info.suggest,
      timed,
      askDuration: timed && !(Number.isFinite(suggested) && suggested > 0),
      durationAsk: {
        text: "Set duration in " + (info.kind === "minutes" ? "minutes" : "seconds") + " before starting:",
        start: info.kind === "minutes" ? 5 : 30,
        max: info.kind === "minutes" ? 120 : 7200,
      },
      amountStep: info.kind === "seconds" ? 5 : info.kind === "minutes" ? 0.5 : 1,
      amountMin: 1,
      weight: it.wt ? { suggest: String(gwrSuggestedWeight(g, st)), step: 5, min: 0, max: 2000 } : null,
      lastReady: it.wt ? (prev ? "Last time: " + plain(prev) : GWR_WORDS.noWeight) : "",
      lastActive: it.wt && prev ? "Last time: " + plain(prev) : "",
      cue: gwrCue(it, d),
      skipAsk: gwrSkipAsk({ ...it, l: plain(it.l) }),
      swapAsk: GWR_SUBSTITUTE_ASK,
      swapFrom: c.substitute?.[it.k] || plain(it.l),
    };
  }
  function introOf(g, d) {
    const c = state.plan.checks[g.date] || {},
      base = addDays(g.date, -((parseD(g.date).getDay() + 6) % 7)),
      today = getToday();
    const days = Array.from({ length: 7 }, (_, i) => {
      const date = addDays(base, i),
        x = planDay(date);
      if (!x || x.kind === "rest" || date > today) return null;
      return { date, label: gwrDayLabel(date, x), selected: date === g.date };
    }).filter(Boolean);
    return {
      step: gwrWeekText(d, g.date),
      title: plain(d.label),
      exercises: d.items.length,
      sets: gwrStats(g).all.length,
      xp: d.xp,
      days,
      log: d.items.map((it, i) => ({
        number: i + 1,
        name: c.substitute?.[it.k] || plain(it.l),
        optional: !!it.opt,
        prescription: plain(it.d),
        done: (((c.s || {})[it.k] || []).filter(Boolean).length) >= it.n,
      })),
    };
  }
  function completeOf(g) {
    const s = gwrStats(g),
      c = state.plan.checks[g.date] || {},
      act = c.actId && state.activities.find((a) => a.id === c.actId);
    return {
      full: s.doneReq.length === s.needed.length,
      xp: act?.xp || 0,
      sets: s.completed.length,
      time: gwrFmtDuration(gwrElapsed(g)),
      volume: s.volume.toLocaleString(),
    };
  }

  /** the session as the page's runner shows it, or null when the screen does not draw one */
  function projection() {
    if (!on() || !opened) return null;
    const g = gwrCurrent();
    if (!g || g.phase === "paused") return null;
    if (!gwrInterval) gwrTickStart(); // the page's tick stopped while the screen was away (a level-up over it)
    const settled = gwrSettle(g);
    if (!settled) return null;
    const { d, st } = settled,
      c = state.plan.checks[g.date] || {},
      stats = gwrStats(g),
      live = g.phase !== "intro" && g.phase !== "complete",
      phase = g.phase;
    const next = () => stepOf(g, d, st);
    return {
      phase,
      date: g.date,
      title: plain(d.short || d.label),
      elapsed: { ms: gwrElapsed(g), running: !!g.unpausedAt },
      progress: { done: stats.doneReq.length, total: stats.needed.length },
      route: d.items.map((it) => {
        const done = ((c.s || {})[it.k] || []).filter(Boolean).length,
          skipped = !!g.skip?.[it.k],
          current = live && st && st.it.k === it.k;
        return {
          state: skipped ? "skipped" : done >= it.n ? "done" : current ? "current" : done > 0 ? "partial" : "",
          weight: Math.max(1, it.n),
          fill: done >= it.n ? 100 : Math.round((100 * done) / Math.max(1, it.n)),
        };
      }),
      itinerary: {
        title: plain(d.label),
        rows: d.items.map((it, i) => ({
          number: i + 1,
          name: plain(it.l),
          status: g.skip?.[it.k] ? GWR_WORDS.skipped : ((c.s || {})[it.k] || []).filter(Boolean).length + "/" + it.n,
          current: !!(live && st && st.it.k === it.k),
        })),
      },
      intro: phase === "intro" ? introOf(g, d) : null,
      step: st && phase !== "intro" && phase !== "complete" ? next() : null,
      timer: phase === "timing" ? { remainingMs: Math.max(0, g.timerUntil - Date.now()), totalMs: g.timerTargetMs } : null,
      review:
        phase === "timer-done"
          ? (() => {
              const secs = Math.max(1, Math.round((Number(g.timerActualMs) || 0) / 1000));
              return { time: gwrFmtDuration(secs * 1000), full: g.timerActualMs >= g.timerTargetMs };
            })()
          : null,
      rest: phase === "rest" ? { remainingMs: Math.max(0, g.restUntil - Date.now()), totalMs: g.restLength * 1000 } : null,
      complete: phase === "complete" ? completeOf(g) : null,
      words: GWR_WORDS,
    };
  }

  /* ---- the screen's runner command ---- */
  const signature = () => {
    const g = state.guidedWorkout;
    return JSON.stringify([g, g && state.plan && state.plan.checks[g.date], state.workoutHistory && state.workoutHistory.length]);
  };
  const num = (v) => (v === undefined || v === null || v === "" ? undefined : Number(v));
  const startable = (date) => /^\d{4}-\d{2}-\d{2}$/.test(String(date)) && gwrSeedForDay(date);

  /** each act is the page's own function for the overlay's button of that name; it answers {ok:false, message} to refuse */
  const acts = {
    start(c) {
      const running = gwrCurrent();
      if (running && running.date !== c.date) return { ok: false, message: "A saved session is waiting. Resume it first." };
      if (!startable(c.date)) return { ok: false, message: "Choose an available training day (not a future or rest day)" };
      opened = true;
      gwrStart(c.date);
    },
    resume() {
      if (!gwrCurrent()) return { ok: false, message: "No saved session to resume" };
      opened = true;
      gwrResume();
    },
    date(c) {
      if (!startable(c.date)) return { ok: false, message: "Choose an available training day (not a future or rest day)" };
      gwrChangeDate(c.date);
    },
    begin: () => gwrBegin(),
    go(c) {
      const g = gwrCurrent(),
        st = g && g.phase === "ready" && gwrStepNext(g);
      if (!st) return;
      const info = gwrNumTarget(st.it),
        timed = info.kind === "seconds" || info.kind === "minutes",
        suggested = Number(info.suggest);
      if (timed && !(Number.isFinite(suggested) && suggested > 0)) {
        const n = num(c.amount),
          max = info.kind === "minutes" ? 120 : 7200;
        if (n === undefined || !Number.isFinite(n) || n <= 0 || n > max) return { ok: false, message: "Enter a valid duration" };
        return gwrGo(n);
      }
      gwrGo();
    },
    log: (c) => gwrLogSet({ amount: c.amount, weight: c.weight }),
    stop: () => gwrTimerFinished(true),
    cancel: () => gwrCancelInterval(),
    restart: () => gwrCancelTimedReview(),
    back: () => gwrV60BackToReady(),
    rest: () => gwrRestDone(),
    adjust(c) {
      if (c.delta !== 15 && c.delta !== -15) return { ok: false, message: "Rest can change by 15 seconds." };
      gwrAdjustRest(c.delta);
    },
    undo: () => gwrUndoLast(),
    skip: () => gwrSkipExercise(true),
    swap(c) {
      const text = String(c.name ?? "").trim();
      if (!text) return { ok: false, message: "Enter the exercise you are doing." };
      gwrSubstituteExercise(text);
    },
    pause() {
      gwrPause();
      return { message: GWR_PAUSED };
    },
    exit: () => gwrExitComplete(),
    reopen: () => gwrReopenPartial(),
  };

  /** Runs the page's function for an act of the screen's runner command and says what it did. A repeat of an operation that
      landed answers "Already recorded."; ok only when the save stored it (a failed save stays in memory, as after the
      overlay's own button, and the next save stores it). XP the act earns is held for the screen to draw. */
  function command(c) {
    const act = Object.prototype.hasOwnProperty.call(acts, c.act) ? acts[c.act] : null;
    if (!act || !offered()) return { ok: false, message: "That is not something the guided workout does." };
    return once(c.op, () =>
      c.act !== "start" && !gwrCurrent()
        ? { ok: false, message: c.act === "resume" ? "No saved session to resume" : "There is no workout in progress." }
        : run(act, c),
    );
  }
  /** an operation the screen sent again after it landed (its reply was lost) is answered, not done twice. One that landed is
      remembered when it is ok, and also when its save failed: the change is in the page's memory, so doing it again would
      do it twice. A refusal changed nothing, so the same operation may be sent again. */
  function once(op, run) {
    const named = typeof op === "string" && op !== "";
    if (named && landed.includes(op)) return { ok: true, changed: false, message: "Already recorded." };
    const reply = run();
    if (named && (reply.ok || reply.changed)) {
      landed.push(op);
      if (landed.length > LANDED) landed.shift();
    }
    return reply;
  }
  function run(act, c) {
    const before = signature();
    gwrLastSave = null;
    const { result, held } = holdCelebrations(() => act(c));
    const refused = result && result.ok === false;
    const reply = { ok: !refused, changed: !refused && signature() !== before };
    if (refused) reply.message = result.message;
    else if (gwrLastSave === false) {
      reply.ok = false;
      reply.message = unsavedMessage();
    } else if (result && result.message) reply.message = result.message;
    if (c.act === "pause" || c.act === "exit") opened = false;
    if (held.length) reply.celebrations = NativeTabKit.celebrations(held);
    return reply;
  }

  /** the page changed views while the screen hosted a session (the player left the tab): nothing may keep its clock running */
  function afterClose() {
    if (opened && lmaView() !== "plan" && gwrCurrent() && gwrCurrent().phase !== "paused") gwrPause();
    if (lmaView() !== "plan") opened = false;
  }

  window.NativePlanRunner = {
    bind(t) {
      tab = t;
    },
    capability: CAPABILITY,
    offered,
    projection,
    command,
    once,
    afterClose,
    ended() {
      opened = false;
    },
  };
  gwrHost = { on, changed, opened: () => { opened = true; changed(); } };
})();
