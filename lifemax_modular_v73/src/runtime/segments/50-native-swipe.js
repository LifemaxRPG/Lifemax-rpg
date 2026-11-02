/* Native tab swipe, the page's half. On a native screen the owner can swipe to the neighbouring tab: the screen follows the finger,
   grows heavy part way, and lets go past a catch half way across (Android draws all of that). The page decides:
   - while the switch is on, every bar scene carries the swipe state (NativeSwipe.state(), added by publishChrome): the bar's tabs in
     order, which of them a native screen would draw (kind()), whether a swipe may start here and how it should feel;
   - a release that commits sends one 'swipe' event on the plugin of the screen it started on. request() answers it with
     swipeSettle, before anything else, then goes to the tab as a bar tap does, so the hand-over (CONTRACT A23) runs as always.
   A swipe moves pixels, never ownership: the page's open and close always win. Phase 1 swipes only from one native screen to
   another. Phase 2 adds the edges (plans/TAB_SWIPE.md §5): a swipe from a native screen into a tab the page draws (a web page, the
   Overview's live shell) is answered "web" and Android's veil reveals the page once it has drawn (html.swipe-reveal keeps its
   cards from fading in under the veil); and the page's own swipe ending on a native tab passes enter/velocity to its open, so the
   screen slides in, while html.native-enter hides the page's static copy of it. Money (Cash) and the ends of the bar stay walls.
   Off by default: Settings, Navigation, Native tab swipe (beta). The feel can be tuned on the phone in Developer mode. */

/* How the swipe feels: native's defaults (SwipeTune.kt), mirrored here so the owner's changes are made against them; a fixture test
   keeps the two equal. Fractions are of the screen width. Native clamps every value the page sends. */
const LMA_TABSWIPE = Object.freeze({
  knee: 0.18, // follows the finger 1:1 up to here, then grows heavy
  breakAt: 0.45, // the catch
  heavy: 0.25, // the screen's speed against the finger's at the catch (1 = no tension)
  hyst: 0.06, // pulled back below breakAt - hyst, the catch holds again
  popDamping: 0.65, // the pop over the catch
  popStiffness: 1200,
  wallMax: 0.08, // the rubber band at a wall or an end of the bar
  wallSlope: 0.35,
  flingDp: 1200, // a flick this fast (dp/s)...
  flingMin: 0.12, // ...over at least this much commits without the catch
  backFlingDp: 600, // a flick back this fast cancels, even past the catch
  commitK: 1000, // the settle springs' stiffness
  cancelK: 800,
  slopFactor: 1.0, // times the system touch slop before a swipe can start
  lockRatio: 1.3, // how much more sideways than vertical a swipe must be
  edgeDp: 24, // a start nearer an edge than this belongs to the system Back gesture
  haptic: "threshold", // the click at the catch: threshold, tick, click or off
});

(function () {
  /* views with sideways gestures of their own (Life Director's section swipe and dial handles): a swipe never starts there */
  const OWN_GESTURES = ["life-director"];
  /* a lock cover over the page you are on (the Journal's) */
  const LOCKED = ".view.active.jlocked";

  /** switched on, on a view a swipe may start from, and nothing the page is busy with (otherwise refused as "off") */
  function allowed() {
    return lmaLoad().nativeSwipe === true && lmaSwipeEnabled() && !!state.onboardingComplete && !lmaThumbWarmBusy && !OWN_GESTURES.includes(lmaView());
  }
  /** something over the page: a popup or sheet, a body that must not scroll, a lock (otherwise refused as "overlay"). The native
      Journal draws its own lock card, which holds none of the Journal's words, so a swipe may start from it. */
  function covered() {
    const lockCover = !!document.querySelector(LOCKED) && !(lmaView() === "history" && NativeTabs.isOpen("history"));
    return !!lmaTopOverlay() || document.body.classList.contains("noscroll") || lockCover;
  }

  /** what a swipe meets at a tab: "native" a screen would draw it now, "stop" it leaves the window (Cash), "web" the page draws it */
  function kind(view) {
    if (NativeTabs.stopped(view)) return "stop";
    return NativeTabs.has(view) && !NativeTabs.webBody(view) && NativeTabs.covered(view) && NativeTabs.wouldOpen(view) ? "native" : "web";
  }

  function tab(button) {
    const key = button.dataset.view, view = lmaResolve(key);
    // a bar extra (Settings) has its icon drawn in, without the data-px name the built-in tabs carry
    const icon = button.querySelector("[data-px]")?.dataset.px || LMA_TAB_EXTRA.find((x) => x.view === key)?.icon || "";
    return Object.assign({ key, view, label: button.textContent.trim(), icon, kind: kind(view) }, OWN_GESTURES.includes(view) && { source: false });
  }

  function colors() {
    const css = getComputedStyle(document.documentElement), argb = (name) => NativeTabKit.color(css.getPropertyValue("--" + name).trim());
    return { bg: argb("bg"), text: argb("text"), muted: argb("muted") };
  }

  /** The swipe state in every bar scene while the switch is on (protocol v1). The tabs are the bar as it is (order, hidden tabs, the
      Food and Library panes), so native finds a neighbour exactly where the owner sees it. Switched off, there is none: the scenes go
      without it and native does nothing of the swipe, as with a page from before it. A state that cannot be built costs the swipe,
      never the bars it rides with: the scene goes without it, and native starts no swipe. */
  /* the swipe follows the finger unless the owner asked the app or the phone for less motion; the old web swipe's own
     "Swipe animation" switch is not asked (it belongs to that swipe: turning it off must not flatten this one) */
  const reducedMotion = () =>
    document.documentElement.classList.contains("reduce-motion") || !!(window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches);
  function swipeState() {
    try {
      if (lmaLoad().nativeSwipe !== true) return undefined;
      const view = lmaView(), key = lmaTabKey(view), bar = lmaTabList();
      return {
        v: 1,
        on: allowed() && !covered(),
        motion: !reducedMotion(),
        haptics: fxOpt().haptics ? hapLevelKey() : "off",
        view,
        active: bar.some((b) => b.dataset.view === key) ? key : "",
        tabs: bar.map(tab),
        colors: colors(),
        tune: lmaLoad().swipeTune || null,
        reveal: true, // phase 2: a swipe into a tab the page draws is taken (answered "web"); an APK from phase 1 ignores this
      };
    } catch (err) {
      console.warn("Native swipe state", err);
      return undefined;
    }
  }

  /** why the page will not take this swipe now, or "" when it will */
  function refusal(src, e, current) {
    if (!allowed()) return "off";
    if (covered()) return "overlay";
    // it started on the open screen of the view the page is on, toward the tab next to that view's on the bar as it is now
    if (!current || e.from !== src || lmaView() !== src || !NativeTabs.isOpen(src)) return "stale";
    const bar = lmaTabList(), i = bar.findIndex((b) => b.dataset.view === lmaTabKey(src));
    if (i < 0 || (e.dir !== 1 && e.dir !== -1) || bar[i + e.dir]?.dataset.view !== e.to) return "stale";
    const k = kind(lmaResolve(e.to));
    // a tab the page draws only for an APK that reveals it (it says so on the swipe); Money never
    if (k === "stop" || (k === "web" && e.reveal !== true)) return "kind";
    return "";
  }

  /** The answer. Capacitor hands every plugin call to Android in the order the page makes them, so an answer sent before the page
      navigates reaches native before the destination's open (A23). Food's answer names its screen session. */
  function settle(plugin, e, outcome, reason) {
    const answer = Object.assign({ token: e?.token, outcome }, reason && { reason }, e?.session !== undefined && { session: e.session });
    try {
      Promise.resolve(plugin.swipeSettle(answer)).catch((err) => console.warn("Native swipe answer", err));
    } catch (err) {
      console.warn("Native swipe answer", err);
    }
  }

  /** A committed swipe from the screen of src (the source view), sent on its plugin; current is false when the event names a screen
      session that has ended (Food). It is answered exactly once, and before the page does anything about it. */
  function request(src, plugin, e, current = true) {
    let reason;
    try {
      reason = refusal(src, e, current);
    } catch (err) {
      console.warn("Native swipe", err);
      reason = "error";
    }
    if (reason) return settle(plugin, e, "refused", reason);
    // the tab's kind was read just now in refusal(); a native screen that would not open now is "web" and revealed the same way
    const web = kind(lmaResolve(e.to)) !== "native";
    settle(plugin, e, web ? "web" : "native");
    if (web) reveal(e.to);
    else go(e.to);
  }
  /* Marks that hold only across one swipe's arrival. lmaNavTo runs every navigate wrapper, the one below too, so the page's own
     navigate inside these says it is ours; any other navigate clears them (the owner has moved on). */
  let ours = false, revealTimer = 0, enterTimer = 0, entering = null;
  function arrive(to) {
    ours = true;
    try {
      lmaNavTo(to, { fast: true });
    } finally {
      ours = false;
    }
  }
  function unreveal() {
    clearTimeout(revealTimer);
    document.documentElement.classList.remove("swipe-reveal");
  }
  /** goes to a tab the page draws, which native reveals: no fade-in or slide of its cards under the veil, so the page the veil
      lifts off is the page as it stays (cleared at the next navigate, or after 800 ms) */
  function reveal(to) {
    try {
      const root = document.documentElement;
      root.classList.add("swipe-reveal");
      delete root.dataset.swipeDir;
      clearTimeout(revealTimer);
      // native's reveal is the slide, up to 400 ms for the page to draw, then a 120 ms fade, all scaled by the animator setting:
      // the cap only stops a stuck mark; the next navigation ends it sooner (navigated)
      revealTimer = setTimeout(unreveal, 2500);
      arrive(to);
      lmaSyncActive();
    } catch (err) {
      console.warn("Native swipe", err); // answered already: native's watchdog puts the screens right
    }
  }
  /** goes to the tab of a swipe taken, as a bar tap does: no View Transition, no slide of the web cards under the screen, recorded
      for Back, and every navigate wrapper runs, so the hand-over opens the destination and then closes the source */
  function go(to) {
    try {
      const root = document.documentElement;
      root.classList.add("native-cover");
      delete root.dataset.swipeDir;
      lmaNavTo(to, { fast: true });
      lmaSyncActive();
    } catch (err) {
      // answered already, so not refused now: native's watchdog and the hand-over's safety net (heal) put the screens right
      console.warn("Native swipe", err);
    }
  }

  /** The page's own swipe (34-navigation's lmaSwipeGo) ending on a tab a native screen draws, from a web page or the Overview's web
      body: the screen slides in from the side swiped toward, as fast as the finger left (speed in CSS px, about dp, per second).
      Until it is in place html.native-enter hides the page's static copy of the tab, so it never flashes. Returns whether it went
      there; false leaves the web swipe as before (the switch off, an APK from before phase 2, reduced motion, a web tab). */
  function enter(to, dir, speed) {
    try {
      if (lmaLoad().nativeSwipe !== true || edges !== true || (dir !== 1 && dir !== -1) || lmaMotionOff()) return false;
      const view = lmaResolve(to);
      if (kind(view) !== "native") return false;
      const root = document.documentElement;
      entering = { view, enter: dir > 0 ? "next" : "prev", velocity: Math.min(20000, Math.round(Math.abs(speed) || 0)) };
      root.classList.add("native-cover", "native-enter");
      delete root.dataset.swipeDir;
      clearTimeout(enterTimer);
      enterTimer = setTimeout(unenter, ENTER_WAIT_MS);
      arrive(to);
      return true;
    } catch (err) {
      console.warn("Native swipe enter", err);
      unenter();
      return false;
    }
  }
  /* the destination's open may take a moment (a first handshake); its own end clears the mark sooner (entered) */
  const ENTER_WAIT_MS = 1500, SLID_MS = 450; // native's settle cap: the slide is over by then
  function unenter() {
    clearTimeout(enterTimer);
    entering = null;
    document.documentElement.classList.remove("native-enter");
  }
  /** what the open of view's screen passes for the slide, once: {enter, velocity}, or nothing for a plain open */
  function takeEnter(view) {
    const e = entering;
    if (!e || e.view !== view || e.taken) return undefined;
    entering = Object.assign({}, e, { taken: true });
    return { enter: e.enter, velocity: e.velocity };
  }
  /** an open of view's screen is over: in place (the static copy may show once the slide is done) or not (it shows at once) */
  function entered(view, opened) {
    const e = entering;
    if (!e || e.view !== view) return;
    clearTimeout(enterTimer);
    if (opened && e.taken) enterTimer = setTimeout(unenter, SLID_MS);
    else unenter();
  }
  /** the page changes view (34-navigation's navigate wrapper asks): one not made by a swipe here ends that swipe's marks */
  function navigated() {
    window.NativePrime?.poke(); // a new tab has new neighbours
    if (ours) return;
    unreveal();
    unenter();
  }

  /* Settings: the switch, enabled once the APK says it draws the swipe. Every native tab on ComposeTabPlugin shares one handshake,
     so the first tells for all of them; Food answers in its own capabilities. */
  // whether the APK draws the swipe (null until a plugin has answered), and whether it draws the edges too (phase 2: it slides in
  // a screen the page swiped to; its swipes say for themselves whether it reveals a web tab)
  let capable = null, asked = null, edges = false;
  async function ask() {
    const C = window.Capacitor, has = (name) => {
      try {
        return C.isNativePlatform() && C.isPluginAvailable(name);
      } catch (_) {
        return false;
      }
    };
    try {
      if (has("NativeCharacter")) {
        const caps = (await C.Plugins.NativeCharacter.handshake()).capabilities;
        return { swipe: caps.includes("shell.swipe.v1"), edges: caps.includes("shell.swipe.v2") };
      }
      if (has("NativeNutrition")) {
        const caps = await C.Plugins.NativeNutrition.capabilities();
        return { swipe: caps.swipe >= 1, edges: caps.swipe >= 2 }; // 2: it draws the edges too
      }
    } catch (e) {
      console.warn("Native swipe capability", e);
    }
    return { swipe: false, edges: false };
  }
  function sync() {
    asked = asked || ask().then((yes) => {
      capable = yes.swipe;
      edges = yes.swipe && yes.edges;
      sync();
    });
    const input = document.getElementById("nativeSwipeToggle"), hint = document.getElementById("nativeSwipeHint");
    if (input) {
      input.disabled = !capable;
      input.checked = !!capable && lmaLoad().nativeSwipe === true;
    }
    if (hint && capable === false) hint.textContent = lmaNative() ? "Install the latest Android update to use this." : "Available in the Android app.";
    renderTune();
  }
  window.lmaSetNativeSwipe = function (on) {
    lmaLoad().nativeSwipe = !!on;
    lmaSave();
    sync();
    if (on) window.NativePrime?.poke();
    toast(!on ? "Native tab swipe: off" : lmaSwipeEnabled() ? "Native tab swipe: on" : "Native tab swipe: on. It also needs Swipe to switch tabs.");
  };

  /* Developer mode, Swipe feel (testing): the keys worth feeling on the phone, each within native's clamp. Only the changed ones are
     kept (lmaLoad().swipeTune) and sent as swipe.tune; the open tab takes them at its next refresh, any other at its next open. */
  const share = (v) => Math.round(v * 100) + "% of the screen width";
  const CONTROLS = [
    { key: "knee", label: "Tension starts at", min: 0.05, max: 0.35, step: 0.01, show: share },
    { key: "breakAt", label: "Catch point", min: 0.15, max: 0.7, step: 0.01, show: share },
    { key: "heavy", label: "Heaviness", min: 0.05, max: 1, step: 0.05, show: (v) => (v >= 1 ? "1.00: no tension" : v.toFixed(2) + ": lower is heavier") },
    { key: "flingDp", label: "Flick speed", min: 500, max: 3000, step: 100, show: (v) => v + " dp/s commits without the catch" },
    { key: "haptic", label: "Detent haptic", options: { threshold: "Gesture threshold", tick: "Clock tick", click: "Click", off: "Off" } },
  ];
  /** the feel native will use: the defaults, the owner's changes, and the catch at least 10% past the knee as native keeps it */
  function feel() {
    const t = Object.assign({}, LMA_TABSWIPE, lmaLoad().swipeTune);
    t.breakAt = Math.max(t.breakAt, Math.round((t.knee + 0.1) * 100) / 100);
    return t;
  }
  function control(c) {
    const id = "swipeTune-" + c.key, set = `lmaSetSwipeTune('${c.key}',this.value)`;
    const field = c.options
      ? `<select id="${id}" onchange="${set}" aria-label="${c.label}">${Object.entries(c.options).map(([v, name]) => `<option value="${v}">${name}${v === LMA_TABSWIPE[c.key] ? " (default)" : ""}</option>`).join("")}</select>`
      : `<input id="${id}" type="range" min="${c.min}" max="${c.max}" step="${c.step}" oninput="${set}" aria-label="${c.label}" style="width:132px;accent-color:var(--accent)">`;
    return `<div class="settingsline"><div style="flex:1;min-width:0"><strong>${c.label}</strong>${c.show ? `<div id="${id}-value" class="muted" style="font-size:12px"></div>` : ""}</div>${field}</div>`;
  }
  function renderTune() {
    const body = document.getElementById("swipeTuneBody");
    if (!body) return;
    if (!body.childElementCount) body.innerHTML = CONTROLS.map(control).join("") + '<button class="btn secondary" type="button" style="margin-top:8px" onclick="lmaResetSwipeTune()">Reset swipe feel</button>';
    const t = feel();
    for (const c of CONTROLS) {
      document.getElementById("swipeTune-" + c.key).value = String(t[c.key]);
      if (c.show) document.getElementById("swipeTune-" + c.key + "-value").textContent = c.show(t[c.key]);
    }
  }
  window.lmaSetSwipeTune = function (key, value) {
    const base = LMA_TABSWIPE[key];
    if (base === undefined) return;
    const v = typeof base === "number" ? Number(value) : String(value), L = lmaLoad(), changed = Object.assign({}, L.swipeTune);
    if (v === base || (typeof v === "number" && !Number.isFinite(v))) delete changed[key];
    else changed[key] = v;
    if (Object.keys(changed).length) L.swipeTune = changed;
    else delete L.swipeTune;
    lmaSave();
    renderTune();
  };
  window.lmaResetSwipeTune = function () {
    delete lmaLoad().swipeTune;
    lmaSave();
    renderTune();
    toast("Swipe feel: back to the defaults");
  };

  /** the swipe is on and nothing covers the page: the moment native neighbours are worth composing ahead (NativePrime) */
  const ready = () => lmaLoad().nativeSwipe === true && allowed() && !covered();

  window.NativeSwipe = { state: swipeState, kind, request, sync, enter, takeEnter, entered, navigated, ready };
  setTimeout(sync, 700);
})();
