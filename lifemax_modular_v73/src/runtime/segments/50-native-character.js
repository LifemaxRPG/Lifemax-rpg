/* Character projection v1. Read-only domain boundary; edits stay in original web handlers. */
(function () {
  const session =
    "character-web-" + Date.now() + "-" + Math.random().toString(36).slice(2);
  let sequence = 0,
    active = false,
    opening = false,
    plugin = null,
    ready = false,
    timer = 0,
    queued = 0;
  function available() {
    try {
      return Capacitor.isNativePlatform() &&
        Capacitor.isPluginAvailable("NativeCharacter")
        ? Capacitor.Plugins.NativeCharacter
        : null;
    } catch (_) {
      return null;
    }
  }
  function blocked() {
    return (
      lmaView() !== "character" ||
      !state.onboardingComplete ||
      !!lmaTopOverlay() ||
      document.body.classList.contains("noscroll")
    );
  }
  function color(v) {
    const c = document.createElement("canvas");
    c.width = c.height = 1;
    const x = c.getContext("2d");
    x.fillStyle = v;
    x.fillRect(0, 0, 1, 1);
    const a = x.getImageData(0, 0, 1, 1).data;
    return ((a[3] << 24) | (a[0] << 16) | (a[1] << 8) | a[2]) >>> 0;
  }
  function projection() {
    const D = chCompute(),
      focus = chFocus(),
      level = characterLevel(),
      cls = state.classId ? classById(state.classId) : null,
      next = TITLES.find((x) => x[0] > level.level),
      root = getComputedStyle(document.documentElement);
    const attrs = ATTRS.map((a) => {
      const l = attrLevel(a.id),
        d = chDecay(a);
      return {
        id: a.id,
        name: a.name,
        short: a.short,
        color: color(ATTR_COL[a.id]),
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
      protocol: 1,
      webSessionId: session,
      sequence: ++sequence,
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
      tokens: Object.fromEntries(
        [
          "bg",
          "panel",
          "panel2",
          "text",
          "muted",
          "accent",
          "line",
          "good",
        ].map((k) => [k, color(root.getPropertyValue("--" + k).trim())]),
      ),
      light: document.documentElement.classList.contains("light-theme"),
    };
  }
  async function navScene() {
    if (!window.NativeOverviewSnapshot) throw Error("Overview chrome unavailable");
    return NativeOverviewSnapshot.snapshot();
  }
  function sync() {
    const input = document.getElementById("nativeCharacterToggle");
    if (input) {
      input.disabled = !available();
      input.checked = !!available() && lmaLoad().nativeCharacter === true;
    }
  }
  function schedule() {
    clearTimeout(queued);
    queued = setTimeout(send, 100);
  }
  async function send() {
    if (!active) return;
    if (blocked()) {
      await close();
      return;
    }
    try {
      await plugin.update({ snapshot: projection(), navScene: await navScene() });
    } catch (e) {
      console.warn("Character refresh", e);
      await close();
    }
  }
  async function close() {
    clearInterval(timer);
    clearTimeout(queued);
    active = false;
    if (plugin) await plugin.close();
  }
  async function open(force) {
    plugin = available();
    if (
      !plugin ||
      active ||
      opening ||
      blocked() ||
      (!force && lmaLoad().nativeCharacter !== true)
    )
      return false;
    opening = true;
    try {
      if (!ready) {
        await plugin.addListener("action", async (e) => {
          await close();
          if (e.view) lmaNavTo(e.view);
          else if (e.action === "attribute" && ATTRS.some((a) => a.id === e.id))
            openAttr(e.id);
          else if (e.action === "luck") openLuck();
          else if (e.action === "benchmarks") {
            document
              .getElementById("benchName")
              .scrollIntoView({ block: "center" });
            document.getElementById("benchName").focus();
          }
        });
        await plugin.addListener("refresh", send);
        await plugin.addListener("nav-action", (e) => window.NativeOverviewSnapshot?.activate(e.id));
        await plugin.addListener("closed", () => {
          active = false;
          clearInterval(timer);
        });
        ready = true;
      }
      const h = await plugin.handshake();
      if (
        h.protocol !== 1 ||
        !h.capabilities.includes("character.projection.v1")
      )
        return false;
      if (blocked()) return false;
      await plugin.open({ snapshot: projection(), navScene: await navScene() });
      if (blocked()) {
        await close();
        return false;
      }
      active = true;
      timer = setInterval(send, 15000);
      return true;
    } catch (e) {
      console.warn("Native Character unavailable", e);
      await close();
      return false;
    } finally {
      opening = false;
    }
  }
  const nav = navigate;
  navigate = function (v) {
    if (v !== "character" && (active || opening)) close();
    const r = nav.apply(this, arguments);
    if (v === "character") queueMicrotask(() => open(false));
    if (v === "settings") sync();
    return r;
  };
  const render = renderCharacterPage;
  renderCharacterPage = function () {
    const r = render.apply(this, arguments);
    if (active) schedule();
    return r;
  };
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible" && active) send();
  });
  window.openNativeCharacter = () => open(true);
  window.setNativeCharacterEnabled = async (on) => {
    lmaLoad().nativeCharacter = !!on;
    lmaSave();
    sync();
    if (!on) await close();
  };
  window.NativeCharacterProjection = { snapshot: projection };
  sync();
  setTimeout(sync, 700);
})();

