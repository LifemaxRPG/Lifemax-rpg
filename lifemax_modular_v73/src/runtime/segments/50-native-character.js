/* Character projection v1. Read-only domain boundary; edits stay in original web handlers. The tab lifecycle is NativeTabKit's. */
(function () {
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
  NativeTabKit.create({
    id: "character",
    plugin: "NativeCharacter",
    capability: "character.projection.v1",
    setting: "nativeCharacter",
    toggle: "nativeCharacterToggle",
    projection,
    onAction,
    render: "renderCharacterPage",
    globals: { open: "openNativeCharacter", enable: "setNativeCharacterEnabled", projection: "NativeCharacterProjection" },
  });
})();
