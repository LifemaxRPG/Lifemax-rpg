/* Library projection v1, the Journal pane (library.journal.projection.v1; plans/JOURNAL_AND_LIBRARY_SHEETS.md §3). The page stays
   the Journal's only writer and keeps every rule: the screen draws what the coach's data builders give the page's own renderers
   (jcHeroData, jcListData, journalDayData, jcDayData, jcReviewData), worded and coloured as the page draws them.
   The Journal is private. While the Journal lock keeps it closed (LifeMaxJournalLock.blocksNative()) the projection is a withheld
   frame: the switch, the lock card's words and the prompt's status, and nothing from the Journal; the builders do not even run
   (CONTRACT A35). Every command but unlock is refused then. A relock or an unlock reaches an open screen at once, and so does the
   prompt starting, failing or being cancelled.
   Reading is native. Writing is the page's (phase 1): write focuses the page's own field for the day while the screen still covers
   it, then the screen steps aside, and it comes back once the field is left and the celebrations have played; the welcome chips and
   the catch-up are hand-backs that come back the same way. A screen that lists library.journal.write.v1 in its handshake writes
   natively (phase 2, edit:"native"): it types in its own field and sends the words, and the page stores them through its own
   owners (journalSaveReflection, journalSaveReview, jcCatchUpWith, jcClaim) and pays the reward as it always did, once. The screen
   draws the XP celebration from the reply (celebrations). Nothing steps aside for a write any more. ADR 0013.
   The tab lifecycle is NativeTabKit's. */
(function () {
  // what the page writes in views/history.html, 48-journal-lock.js's card, 13-runtime.js's tip and 04/47's renderers, word for word
  const WORDS = {
    panes: [
      { id: "media", label: "Media" },
      { id: "history", label: "Journal" },
    ],
    lock: { title: "Journal locked", body: "Your reflections are private. Unlock with your fingerprint or screen lock.", unlock: "Unlock" },
    hero: { eyebrow: "YOUR CHRONICLE", title: "Journal", backup: { label: "Backup", spoken: "Export backup" } },
    rhythm: { title: "Last 4 weeks", legend: "brighter = bigger day" },
    xpLabel: "XP today",
    empty: {
      icon: "◷",
      title: "No journal entries yet",
      body: "Log an activity, complete a quest, or finish a plan session. Everything you enter will show up here by day.",
    },
    reflect: { kicker: "Reflect", swap: "Swap question", label: "Reflection (optional)", placeholder: "How did today feel? What mattered? What will you do differently?", max: 4000 },
    blank: { icon: "✎", title: "Nothing logged this day yet" },
    workoutNotes: "Workout notes",
    review: { coach: "Coach", placeholder: "Write if you want to.", max: 2000 },
    dismissTip: "Dismiss tip",
    locked: "Unlock the Journal first.",
    unknown: "That day is not in your Journal.",
    noQuestion: "That day has no question to swap.",
    // the catch-up's question is the page's own prompt() (jcCatchUp), asked in the screen's field instead; OK and Cancel are the
    // system prompt's buttons
    catchUp: { prompt: "One sentence: what have you been up to?", min: JC_LINE_MIN, max: 4000, ok: "OK", cancel: "Cancel" },
  };
  const WRITE = "library.journal.write.v1"; // the screen types the words itself and sends them (phase 2)
  const EDITING = "#journalEntry textarea"; // the page's own field: a day's reflection or a review's answer
  const color = (css) => NativeTabKit.color(css);
  const lock = () => window.LifeMaxJournalLock;
  const locked = () => !!lock() && lock().blocksNative();
  let acting = false; // a hand-back (a welcome chip, the catch-up) is running or celebrating on the page
  let returning = false; // the page's field was left: the screen waits for its save's celebrations before it comes back

  /* ---------- the projection ---------- */
  function panes() {
    return WORDS.panes.map((p) => ({ ...p, current: p.id === "history" }));
  }
  function lockOf() {
    return { on: !!lock() && lock().on(), card: WORDS.lock };
  }
  /** while the lock keeps the Journal closed: the chrome, the lock card and the prompt's status, nothing from the Journal */
  function withheldFrame() {
    const st = lock().state();
    return { withheld: true, panes: panes(), lock: lockOf(), status: { prompting: !!st.prompting, error: String(st.error || "") } };
  }

  function tip() {
    const t = FEATURE_TIPS.history;
    return state.tutorialDone && !(state.tipsSeen || {}).history ? { icon: t.icon, title: t.title, body: t.body, dismiss: WORDS.dismissTip } : null;
  }
  function heroOf() {
    const h = jcHeroData();
    return {
      eyebrow: WORDS.hero.eyebrow,
      title: WORDS.hero.title,
      backup: WORDS.hero.backup,
      stats: h.stats.map((s) => ({ value: s.value, label: s.label, color: color(s.color) })),
      // as the page's cells: an archetype's colour at the page's two-decimal opacity, or off (drawn in the page's off look)
      rhythm: Object.assign({}, WORDS.rhythm, {
        cells: h.cells.map((c) => ({ color: c.off ? 0 : color(c.color), alpha: c.off ? 0 : Number(c.alpha.toFixed(2)), off: c.off, today: c.today, label: c.label })),
      }),
    };
  }
  function itemOf(it) {
    if (it.kind === "quiet") return { kind: "quiet", text: it.text };
    if (it.kind === "review") return { kind: "review", key: it.key, badge: it.badge, title: it.title, sub: it.sub };
    return { kind: "day", key: it.key, weekday: it.weekday, date: it.date, bar: it.bar, xp: String(it.xp), pen: it.pen, color: color(it.color), today: it.today, spoken: it.spoken };
  }
  /* The hero and the day list are built again only when what they show may have changed: histRev (raised by every renderHistory and
     by the writes that do not draw), the date, and the 20:00 cutoff that brings a week's review in. A refresh with nothing new
     reuses them; only the selected entry is built each time. */
  let memo = { key: "", days: [], hero: null, list: null };
  function memoKey() {
    return histRev + "|" + getToday() + "|" + (new Date().getHours() >= 20 ? 1 : 0);
  }
  function memoised() {
    const key = memoKey();
    if (memo.key !== key) {
      const days = journalDays();
      memo = { key, days, hero: heroOf(), list: { rev: key, items: days.length ? jcListData(days).map(itemOf) : [] } };
    }
    return memo;
  }

  function reflectOf(d, r) {
    return {
      prompt: r.prompt ? { kicker: WORDS.reflect.kicker, text: r.prompt.text, id: r.prompt.id, swap: WORDS.reflect.swap } : null,
      label: r.prompt ? null : WORDS.reflect.label,
      text: r.text,
      placeholder: WORDS.reflect.placeholder,
      max: WORDS.reflect.max,
      min: JC.reflectMin,
      canPay: r.canPay,
      awarded: r.awarded,
      meter: r.meter,
      // the page's four lines (jcCounterText): awarded, else old when it cannot pay, else ready or short by the length; while
      // typing, the short line also counts the characters: " (n)"
      counter: Object.assign({}, JC_COUNTER),
    };
  }
  function planNote(x) {
    return { label: x.label, weight: x.weight, note: x.note };
  }
  function dayEntry(d) {
    const day = journalDayData(d), c = jcDayData(d), h = day.head;
    return {
      kind: "day",
      key: d,
      head: { number: h.number, weekday: h.weekday, month: h.month, xp: String(h.xp), xpLabel: WORDS.xpLabel },
      meta: day.meta,
      care: c.care ? { lead: c.care.lead, text: c.care.text } : null,
      welcome: c.welcome ? { tier: c.welcome.tier, head: c.welcome.head, body: c.welcome.body, chips: c.welcome.chips.map((x) => ({ id: x.id, label: x.label, done: x.done })) } : null,
      insight: c.insight ? { label: c.insight.label, color: color(c.insight.color), text: c.insight.text, pattern: c.insight.pattern } : null,
      reflect: reflectOf(d, c.reflect),
      timeline: day.timeline.map((t) => {
        const col = color(t.color);
        return {
          time: t.time,
          color: col,
          icon: ICONS[t.icon] ? t.icon : EMOJI_MAP[t.icon] || "star", // the pixel icon the page draws (iconHtml)
          name: t.name,
          attr: t.attr ? { text: t.attr, color: col } : null,
          minutes: t.minutes,
          skill: t.skill,
          xp: "+" + t.xp,
          notes: t.notes,
          plan: t.plan ? t.plan.map(planNote) : null,
        };
      }),
      workoutNotes: day.workoutNotes ? { title: WORDS.workoutNotes, icon: "dumbbell", items: day.workoutNotes.map(planNote) } : null,
      blank: day.blank ? { icon: WORDS.blank.icon, title: WORDS.blank.title, text: day.blank } : null,
    };
  }
  function reviewEntry(key) {
    const r = jcReviewData(key), c = r.chart, W = r.kind === "W";
    const chart =
      c.kind === "bars"
        ? { kind: "bars", bars: c.bars.map((b) => ({ label: b.label, value: b.value, height: b.height, color: color(b.color), today: b.today })) }
        : { kind: "heat", weekdays: c.weekdays, lead: c.lead, cells: c.cells.map((x) => ({ day: x.day, color: color(x.color), alpha: Number(x.off ? 0.18 : x.alpha.toFixed(2)), off: x.off, label: x.label })) };
    return {
      kind: "review",
      key,
      head: { badge: r.badge, title: r.title, range: r.range },
      chart,
      grid: r.grid,
      coach: { badge: WORDS.review.coach, text: r.coach },
      question: r.question,
      answer: r.answer,
      placeholder: WORDS.review.placeholder,
      max: WORDS.review.max,
      min: W ? JC.weeklyMin : JC.monthlyMin,
      paid: r.paid,
      counter: r.counter,
    };
  }
  /** the selected day or review as the page has it (histSel), or the newest day when the page has not chosen one it lists */
  function selected(days) {
    if (histSel && (days.includes(histSel) || (/^[WM]:/.test(histSel) && jcReviewList(days).some((r) => r.key === histSel)))) return histSel;
    return days[0] || null;
  }
  function openFrame() {
    const m = memoised(), sel = selected(m.days);
    return {
      withheld: false,
      panes: panes(),
      lock: lockOf(),
      motion: !fxReduced(),
      edit: nativeWrite() ? "native" : "page", // the screen types the words itself when it says it can (the handshake), else the page's field
      write: nativeWrite() ? { catchUp: WORDS.catchUp, celebrationTarget: NativeTabKit.celebrationTarget() } : null,
      tip: tip(),
      hero: m.hero,
      list: m.list,
      selection: sel,
      empty: m.days.length ? null : WORDS.empty,
      entry: !sel ? null : /^[WM]:/.test(sel) ? reviewEntry(sel) : dayEntry(sel),
      // notice: the kit's (cfg.notices), the page's toast while it shows, in withheld frames too
    };
  }
  /** pure: never writes, never resets the coach; withheld first whenever the lock keeps the Journal closed */
  function projection() {
    if (locked()) return withheldFrame(); // nothing below runs while locked
    return openFrame();
  }

  /* ---------- commands: the screen stays, the reply comes with the next projection ---------- */
  const refuse = () => ({ ok: false, changed: false, message: WORDS.locked });
  const nativeWrite = () => tab.has(WRITE);
  const DAY = /^\d{4}-\d\d-\d\d$/;
  const unknown = () => ({ ok: false, changed: false, message: WORDS.unknown });
  /** a write that may pay: its XP celebration goes to the screen in the reply (the page plays only the sound and buzz), unless no
      screen is showing it (the Journal is locked, or the write is the flush of a screen that has just closed): then the page plays it. The reply is the owner's, plus celebrations. */
  function paying(run) {
    if (locked() || !tab.active()) return run(); // no screen showing (the app left, the tab closed): the page plays it
    const { result, held } = holdCelebrations(run);
    return Object.assign({}, result, { celebrations: NativeTabKit.celebrations(held) });
  }
  const text = (p) => String(p.text == null ? "" : p.text);
  const base = (p) => (p.base == null ? null : String(p.base));
  const ALREADY = "Already recorded."; // a repeat the page recognises (the question already moved on, the tip already gone)
  function textarea() {
    return document.querySelector(EDITING);
  }
  const commands = {
    /** a day or a review, highlighted on the screen at once: the page decides it and draws its own entry (no save, no full render) */
    async select(p) {
      if (locked()) return refuse();
      const key = String(p.key || ""), was = histSel;
      if (!selectJournalEntry(key)) return { ok: false, changed: false, message: WORDS.unknown };
      return { ok: true, changed: was !== key };
    },
    /** the page's own field for that day, scrolled to and focused while the screen still covers it; the focused field makes the
        screen step aside once the reply has gone, and it comes back when the field is left (the focusout below) */
    async write(p) {
      if (locked()) return refuse();
      if (nativeWrite()) return { ok: false, changed: false, message: WORDS.unknown }; // the screen has its own field
      const key = String(p.key || "");
      if (key !== histSel && !selectJournalEntry(key)) return { ok: false, changed: false, message: WORDS.unknown };
      if (histDrawn !== histSel) renderJournalEntry();
      const ta = textarea();
      if (!ta) return { ok: false, changed: false, message: WORDS.unknown };
      ta.scrollIntoView({ block: "center" });
      ta.focus({ preventScroll: true });
      return { ok: true, changed: false, after: () => tab.follow() };
    },
    /** The words of a day's reflection, from the screen's own field. final:false (autosave, 1.5 s after the typing stops) stores the
        words only; final:true (the field, the tab or the app was left) may also pay the reward, which the page pays once a day
        (days[d].xpAwarded, stored in the same save as the coach activity). Words and reward are separate saves, so a reward that
        cannot be stored never takes the words back. Refused as a conflict, with the stored words, when the page holds neither base
        (what the screen last saw) nor text. Still stored while the Journal is locked (the app was left with the field open): the
        owner's words are never dropped, and nothing private comes back in the reply. */
    async reflect(p) {
      const d = String(p.d || "");
      if (!DAY.test(d) || d > getToday()) return unknown();
      return paying(() => journalSaveReflection(d, text(p), base(p), { award: p.final === true, soft: false }));
    },
    /** A review's answer, the same way (the bonus is paid once per review: reviews[key].paid) */
    async answer(p) {
      const key = String(p.key || "");
      if (!/^[WM]:/.test(key) || !jcReviewList(journalDays()).some((r) => r.key === key)) return unknown();
      return paying(() => journalSaveReview(key, text(p), base(p), { award: p.final === true }));
    },
    /** A welcome chip that pays (pet or fresh). The page's card is the receipt: a claimed chip changes nothing. */
    async claim(p) {
      if (locked()) return refuse();
      const kind = String(p.kind || "");
      const w = jcRec("welcome", getToday());
      if (JC_GIFT[kind] && w.claimed && w.claimed[kind]) return { ok: true, changed: false, message: ALREADY };
      if (!JC_GIFT[kind] || !jcOffered(kind)) return { ok: false, changed: false, message: JC_NOT_OFFERED };
      const reply = paying(() => ({ paid: jcClaim(kind) }));
      return reply.paid ? Object.assign(reply, { ok: true, changed: true }) : Object.assign(reply, { ok: false, changed: false, message: unsavedMessage() });
    },
    /** The one-sentence catch-up, with its sentence: stored (joined to today's reflection), then +20 XP, once */
    async catchUp(p) {
      if (locked()) return refuse();
      return paying(() => jcCatchUpWith(text(p)));
    },
    /** the day's question moved on, only while from is the question shown (a repeat changes nothing) */
    async swap(p) {
      if (locked()) return refuse();
      const d = String(p.d || ""), from = String(p.from || "");
      if (!jcPrompt(d)) return { ok: false, changed: false, message: WORDS.noQuestion };
      const r = jcSwapPrompt(d, from);
      if (!r.ok) return { ok: false, changed: false, message: unsavedMessage() };
      return r.changed ? { ok: true, changed: true } : { ok: true, changed: false, message: ALREADY };
    },
    /** the page's own prompt (the system's over the screen). A page that missed a relock native made on its own (the app stopped
        without the page seeing it hidden) relocks first, so its withheld frame clears native's gate before the prompt */
    async unlock() {
      if (!lock() || !lock().on()) return { ok: false, changed: false };
      if (!lock().blocksNative()) lock().relock();
      const r = await lock().prompt();
      return { ok: r === true, changed: r === true };
    },
    /** the backup file: Android's Save as over the screen. Not waited for, so a Save as left open never holds the commands behind
        it; its toast ("Backup saved", "Save cancelled") comes later as the notice */
    async backup() {
      if (locked()) return refuse();
      Promise.resolve()
        .then(() => exportData())
        .catch((e) => console.warn("journal backup", e));
      return { ok: true, changed: false };
    },
    async tip() {
      if (locked()) return refuse();
      if ((state.tipsSeen || {}).history) return { ok: true, changed: false, message: ALREADY };
      dismissFeatureTip("history"); // a failed write is ignored, as on the page
      return { ok: true, changed: true };
    },
  };

  /* ---------- actions: the screen closes, the page runs, the screen comes back after the celebrations ---------- */
  const ACTIONS = {
    claim: (e) => jcClaim(String(e.id || "")),
    catchUp: () => jcCatchUp(),
  };
  async function onAction(e) {
    const run = ACTIONS[e.action];
    acting = true;
    try {
      if (run && !locked()) await run(e);
    } catch (err) {
      console.warn("journal action " + e.action, err);
    }
    // the orbs and a level-up play on the page; the screen comes back once they have
    afterCelebrations(() => {
      acting = false;
      tab.revisit();
    });
  }

  const tab = NativeTabKit.create({
    id: "history",
    plugin: "NativeJournal",
    capability: "library.journal.projection.v1",
    setting: "nativeJournal",
    toggle: "nativeJournalToggle",
    projection,
    notices: true,
    commands,
    onAction,
    render: "renderHistory",
    // a hand-back in progress, the page's field, a level-up, and (until a native player) a loaded book, as native Media
    blocked: () => acting || returning || !!document.activeElement?.matches(EDITING) || !!document.querySelector(".luov") || audioBusy(),
    // the page gives the pane back: a level-up gone (the body watcher), the entry drawn again after a hand-back, the player closed
    watch(observe) {
      observe(document.getElementById("journalEntry"), { childList: true });
      observe(document.getElementById("audioPlayer"), { attributes: true, attributeFilter: ["class"] });
    },
    globals: { open: "openNativeJournal", enable: "setNativeJournalEnabled", projection: "NativeJournalTabProjection" },
  });
  // a relock pushes the withheld frame at once and an unlock the open one (send() does nothing while closed, revisit() while open)
  lock()?.onChange(() => {
    tab.send();
    tab.revisit();
  });
  // the prompt started, failed or was cancelled: the lock card says so at once
  lock()?.onStatus(() => tab.send());

  /* The way back after write: the field left (its change handler, which may pay and celebrate, runs first), then the celebrations,
     then the screen. returning holds blocked() meanwhile, because the orbs are drawn on the body, which the kit watches. A relock
     that blurs the field comes back the same way, on the withheld frame. */
  document.addEventListener(
    "focusout",
    (e) => {
      if (!e.target.matches?.(EDITING)) return;
      returning = true;
      setTimeout(() =>
        afterCelebrations(() => {
          returning = false;
          tab.revisit();
        }),
      );
    },
    true,
  );

  window.NativeJournalProjection = { commands, projection, words: WORDS };
})();
