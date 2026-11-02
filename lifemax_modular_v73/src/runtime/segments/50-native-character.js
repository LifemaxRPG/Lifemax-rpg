/* Character projection v1 and its commands. The page stays the only writer: the screen asks (Luck, benchmarks, a prefilled Log) and
   the page answers through its own functions. Without the actions capability (an older app) those taps hand over to the web page as
   before. The tab lifecycle is NativeTabKit's. */
(function () {
  const ACTIONS = "character.actions.v1";
  const LIMITS = { name: 60, value: 40 }; // what a benchmark's name and result may hold; sent so the screen hard-codes none
  let tab = null;
  /** the screen's words for the sheets: the web form's own labels where the page has them, else these */
  function words() {
    const field = (id, fallback) => {
      const el = document.getElementById(id), label = el && el.closest(".field") && el.closest(".field").querySelector("label");
      return { label: label ? label.textContent.trim() : fallback[0], placeholder: el ? el.placeholder : fallback[1] };
    };
    const name = field("benchName", ["Benchmark name", "e.g. 5K run time"]), value = field("benchValue", ["Current result", "e.g. 28:30"]);
    const add = document.getElementById("benchAdd");
    return {
      luck: { heading: "Daily Luck", rolling: "Rolling…", retry: "Try again", close: "Close" },
      benchmarks: {
        heading: "Real-world benchmarks", nameLabel: name.label, namePlaceholder: name.placeholder, valueLabel: value.label,
        valuePlaceholder: value.placeholder, add: add ? add.textContent.trim() : "＋ Add benchmark", edit: "Edit", save: "Save",
        cancel: "Cancel", remove: "Remove", close: "Close",
      },
    };
  }
  const ok = (extra) => Object.assign({ ok: true, changed: false, message: "" }, extra);
  const no = (message) => ({ ok: false, changed: false, message });
  /** today's roll as the screen draws it (the dice, the number and the tier's own colour and words) */
  function luckView(L) {
    const t = luckTier(L.score);
    return { date: L.date, dice: L.dice.slice(), score: luckFmt(L.score), tier: t.name, message: luckMsg(L.score), color: NativeTabKit.color(t.c) };
  }
  function projection() {
    const D = chCompute(),
      focus = chFocus(),
      level = characterLevel(),
      cls = state.classId ? classById(state.classId) : null,
      next = TITLES.find((x) => x[0] > level.level);
    const attrs = ATTRS.map((a) => {
      const l = attrLevel(a.id),
        d = chDecay(a);
      return {
        id: a.id,
        name: a.name,
        short: a.short,
        color: NativeTabKit.color(ATTR_COL[a.id]),
        level: l.level,
        into: l.into,
        needed: l.needed,
        pct: l.pct,
        total: attrXP(a.id),
        week: D.wk[a.id],
        previous: levelData(D.prev[a.id]).level,
        days: D.per[a.id],
        focus: focus.includes(a.id),
        status: d.txt,
        last: d.sub,
        rank: d.rank,
      };
    });
    const inches =
        (Number(state.heightFt) || 0) * 12 + (Number(state.heightIn) || 0),
      luck = luckToday();
    const skills = Object.entries(state.skillXP || {})
      .filter((e) => e[1] > 0)
      .sort((a, b) => b[1] - a[1])
      .map(([name, xp]) => ({ name, xp, ...levelData(xp) }));
    return {
      name: state.name || "The Life Architect",
      avatar: state.avatar || "",
      classId: state.classId || "",
      title:
        titleFor(level.level).toUpperCase() +
        (cls ? " · " + cls.name.toUpperCase() : ""),
      level: level.level,
      pct: level.pct,
      xp:
        level.into.toLocaleString() +
        " / " +
        level.needed.toLocaleString() +
        " XP",
      nextLevel: "to level " + (level.level + 1),
      nextTitle: next
        ? "Next title: " +
          next[1] +
          " at level " +
          next[0] +
          " (" +
          (next[0] - level.level) +
          " to go)"
        : "Highest title reached. You are a Legend.",
      streak: streak() + "d",
      totalXP: totalXP().toLocaleString(),
      week: "+" + D.total.toLocaleString(),
      luck: luck ? luckFmt(luck.score) : "Roll",
      actions: !!tab && tab.has(ACTIONS),
      today: getToday(),
      luckRoll: luck ? luckView(luck) : null,
      benchmarkLimits: LIMITS,
      words: words(),
      weight: state.weight
        ? Number(state.weight).toLocaleString() + " lb"
        : "—",
      bmi:
        state.weight && inches > 0
          ? ((703 * Number(state.weight)) / (inches * inches)).toFixed(1)
          : "—",
      cap: chCap(
        attrs.map((a) => a.level),
        attrs.map((a) => a.previous),
      ),
      archetype: chArchetype(attrs.map((a) => a.level)).n,
      attributes: attrs,
      skills,
      benchmarks: (state.benchmarks || []).map((b) => ({
        name: b.name,
        value: String(b.value),
      })),
      tabs: lmaTabList().map((b) => ({
        id: b.dataset.view,
        label: b.textContent.trim(),
      })),
    };
  }
  async function onAction(e) {
    if (e.action === "attribute" && ATTRS.some((a) => a.id === e.id)) openAttr(e.id);
    else if (e.action === "luck") openLuck();
    else if (e.action === "benchmarks") {
      document.getElementById("benchName").scrollIntoView({ block: "center" });
      document.getElementById("benchName").focus();
    }
  }
  /** a change to the benchmarks, kept only if the page could store it (persist() decides): otherwise it is taken back, so the screen
      and the page agree. change() answers the page's refusal, or '' when it did the work. */
  function applyBenchmarks(change) {
    const before = JSON.stringify(state.benchmarks);
    const refused = change();
    if (refused) return no(refused);
    if (save()) return ok({ changed: true });
    state.benchmarks = JSON.parse(before);
    renderBenchmarks();
    return no(unsavedMessage());
  }
  const commands = {
    /** Daily Luck: the page rolls and saves (a roll already made today is shown again and changes nothing); the reply carries it */
    luck(p) {
      if (typeof p.day !== "string" || p.day !== getToday()) return no("The day changed. Open Luck again.");
      const prior = { luck: state.luck, log: state.luckLog && state.luckLog[p.day] };
      const { result: r, held } = holdCelebrations(() => luckRollToday());
      if (!r.saved) {
        state.luck = prior.luck;
        if (state.luckLog) prior.log === undefined ? delete state.luckLog[p.day] : (state.luckLog[p.day] = prior.log);
        return no(unsavedMessage());
      }
      const luck = luckView(luckToday());
      const reply = r.fresh ? ok({ changed: true, message: "Rolled " + luck.score + ". " + luck.tier + ".", luck }) : ok({ message: "Already rolled today.", luck });
      if (held.length) reply.celebrations = NativeTabKit.celebrations(held);
      return reply;
    },
    benchmarkAdd(p) {
      const op = String(p.op || "").slice(0, 80);
      if (!op) return no("Something went wrong. Nothing was changed.");
      if (state.benchmarks.some((b) => b.op === op)) return ok({ message: "Already recorded." });
      const name = String(p.name || "").trim(), value = String(p.value || "").trim();
      if (name.length > LIMITS.name) return no("The name can be up to " + LIMITS.name + " characters.");
      if (value.length > LIMITS.value) return no("The result can be up to " + LIMITS.value + " characters.");
      return applyBenchmarks(() => benchmarkPush(name, value, op));
    },
    /** the result of one benchmark; the name seen guards against the list having changed under the sheet */
    benchmarkSet(p) {
      const i = p.index, b = state.benchmarks[i];
      if (!Number.isInteger(i) || !b || b.name !== p.name) return no("That benchmark changed. Nothing was changed.");
      const value = String(p.value || "").trim();
      if (value.length > LIMITS.value) return no("The result can be up to " + LIMITS.value + " characters.");
      if (value && value === b.value) return ok({ message: "Already recorded." });
      return applyBenchmarks(() => benchmarkRevalue(i, value));
    },
    benchmarkRemove(p) {
      const i = p.index, b = state.benchmarks[i];
      if (!Number.isInteger(i) || !b || b.name !== p.name) return no("That benchmark changed. Nothing was changed.");
      return applyBenchmarks(() => benchmarkDrop(i));
    },
    /** "Log <attribute> activity": the web form's own fields take the attribute (the preset that trains it, else a custom activity),
        which is how the native Log picks a prefill up when it opens */
    log(p) {
      const id = String(p.attribute || "");
      if (!ATTRS.some((a) => a.id === id)) return no("Something went wrong. Nothing was changed.");
      const type = document.getElementById("activityType");
      const preset = Object.keys(rules).find((k) => rules[k].primary === id && [...type.options].some((o) => o.value === k));
      type.value = preset || "custom";
      activityPresetChanged();
      if (!preset) document.getElementById("primaryAttr").value = id;
      return ok({ navigate: "log" });
    },
  };
  tab = NativeTabKit.create({
    id: "character",
    plugin: "NativeCharacter",
    capability: "character.projection.v1",
    prime: true, // composed ahead at idle as a swipe neighbour (NativePrime)
    setting: "nativeCharacter",
    toggle: "nativeCharacterToggle",
    projection,
    onAction,
    commands,
    warm: true, // the screen says at once whether it draws the actions, so the projection can say it
    render: "renderCharacterPage",
    globals: { open: "openNativeCharacter", enable: "setNativeCharacterEnabled", projection: "NativeCharacterProjection" },
  });
})();
