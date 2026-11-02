/* NativePrime: composes the native neighbours of the tab you are on before you swipe to them (plans/TAB_SWIPE.md phase 3, CONTRACT
   A24's prime). A native screen is composed the first time it opens, which takes a moment the swipe's slide has to wait through; a
   screen that has been composed already slides in at once. So, while the Native tab swipe (beta) switch is on and the page has been
   left alone for 800 ms, the neighbour on either side of the current tab that has never been composed is asked to compose itself
   (its kit's prime(): the projection an open would send, answered shell.prime.v1). One at a time, and the next only after another
   800 ms of quiet, so priming never competes with a touch or a screen change. The kit says which tabs may be primed (cfg.prime);
   NativeSwipe says whether the swipe is on and which neighbours a native screen would draw. Nothing here changes what is shown. */
(function () {
  const config = { idleMs: 800, maxTries: 6 };
  const kits = new Map(); // view -> {prime(): Promise<boolean>, primed(): boolean}
  const tries = new Map(); // view -> prime attempts this page load that came back not primed
  let lastTouch = 0, down = 0, timer = 0, busy = false, laters = 0;

  function register(view, kit) {
    kits.set(view, kit);
  }
  /** the views on either side of the current tab on the bar as it is now, next first */
  function neighbours() {
    const bar = lmaTabList(), i = bar.findIndex((b) => b.dataset.view === lmaTabKey(lmaView()));
    return i < 0 ? [] : [bar[i + 1], bar[i - 1]].filter(Boolean).map((b) => lmaResolve(b.dataset.view));
  }
  /** the neighbour to prime now, if any: a tab with a kit that has not been primed, which a native screen would draw */
  function next() {
    return neighbours().find((v) => {
      const kit = kits.get(v);
      return kit && !kit.primed() && (tries.get(v) || 0) < config.maxTries && window.NativeSwipe?.kind(v) === "native";
    });
  }
  /** (re)start the quiet period; called when something that may change what is wanted happens */
  function poke(later = false) {
    clearTimeout(timer);
    if (!later) laters = 0;
    if (kits.size) timer = setTimeout(tick, config.idleMs);
  }
  /** a neighbour that could be primed once the page knows more (a first handshake still in flight): look again in a few seconds */
  function wait() {
    if (laters++ >= 5 || !neighbours().some((v) => kits.has(v) && !kits.get(v).primed() && (tries.get(v) || 0) < config.maxTries)) return;
    clearTimeout(timer);
    timer = setTimeout(tick, config.idleMs * 4);
  }
  async function tick() {
    timer = 0;
    if (busy) return;
    if (down > 0 || Date.now() - lastTouch < config.idleMs) return poke(true);
    if (document.visibilityState === "hidden" || !window.NativeSwipe?.ready()) return; // the next scene or navigation pokes again
    const view = next();
    if (!view) return wait();
    busy = true;
    try {
      if (!(await kits.get(view).prime())) tries.set(view, (tries.get(view) || 0) + 1);
    } catch (err) {
      tries.set(view, config.maxTries);
      console.warn("Native prime", err);
    } finally {
      busy = false;
    }
    poke(true);
  }

  const touch = (e) => {
    lastTouch = Date.now();
    down = e.touches ? e.touches.length : 0; // fingers still on the screen (a touchend's list leaves the lifted one out)
  };
  for (const type of ["touchstart", "touchmove", "touchend", "touchcancel"]) document.addEventListener(type, touch, { capture: true, passive: true });
  document.addEventListener("visibilitychange", () => document.visibilityState === "visible" && poke());
  setTimeout(poke, 3000); // after the page has loaded and the tabs have had their first handshake

  /** native has dropped its composed screens (or a test wants it to have): every tab may be primed again */
  function reset() {
    tries.clear();
    kits.forEach((kit) => kit.forget && kit.forget());
    poke();
  }

  window.NativePrime = { register, poke, reset, next, config, tick };
})();
