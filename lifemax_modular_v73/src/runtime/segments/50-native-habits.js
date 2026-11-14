/* Habits' place in the shell, and nothing else. Habits is native from its data up: the app keeps the habits itself (a file of its
   own, feature/habits in the Android source), works out every rule and draws every pixel; the page has no habits data, no rules
   and no drawing of it. What is here is only what the shell still needs from the page for any tab: the bar's button (the native
   bar is the page's bar drawn natively), the view the bar switches to (an empty one, which the screen covers), and NativeTabKit's
   open/close/hand-over. What the kit sends is the screen's surroundings, none of it Habits': the palette and theme (the kit's own),
   whether motion is reduced and the owner's haptic strength (app settings the page holds), and the character's class name (shown
   until the owner says who they are becoming).
   Where the app cannot draw the screen (a browser, an APK from before it) the button is taken off the bar, so the tab is never an
   empty page. */
(function () {
  const PLUGIN = "NativeHabits";
  /** the owner's haptic strength (HAP_LEVELS), or null with haptics off: a native buzz is as long and strong as the page's */
  function haptics() {
    if (!fxOpt().haptics) return null;
    const L = HAP_LEVELS[hapLevelKey()];
    return { dm: L.dm, add: L.add, amp: L.amp, min: L.min, max: HAP_MAX };
  }
  /** what the screen cannot know by itself: app settings and the character's class */
  function surroundings() {
    const cls = typeof CLASSES !== "undefined" && state.classId ? CLASSES.find((c) => c.id === state.classId) : null;
    return { v: 1, reducedMotion: !!(fxReduced() || document.documentElement.classList.contains("reduce-motion")), haptics: haptics(), className: cls ? cls.name : "" };
  }

  function available() {
    try {
      return Capacitor.isNativePlatform() && Capacitor.isPluginAvailable(PLUGIN);
    } catch (_) {
      return false;
    }
  }
  /* The bar's button only where the screen can be drawn. It is taken off the bar (not hidden) so everything that reads the bar's
     buttons (the tab editor, the swipe, the scrub) sees a bar without Habits; if the plugin turns up late, it is put back. */
  const button = document.querySelector('.navbtn[data-view="habits"]');
  function bar() {
    if (!button) return;
    const on = available(), there = !!button.parentNode, side = document.querySelector(".sidebar");
    if (on === there || !side) return;
    if (on) {
      side.insertBefore(button, side.querySelector(".sidebar-note"));
      try { lmaTabsApply(); lmaTabsRender(); } catch (_) { /* the editor is not on the page yet */ }
    } else button.remove();
  }
  bar();
  setTimeout(bar, 700);

  const tab = NativeTabKit.create({
    id: "habits",
    plugin: PLUGIN,
    capability: "habits.v1",
    setting: "nativeHabits", // no switch in Settings: the screen is the tab (defaultOn), and nothing writes this key
    defaultOn: true,
    toggle: "nativeHabitsToggle",
    prime: true, // composed ahead at idle as a swipe neighbour (NativePrime); nothing private is sent
    projection: surroundings,
    globals: { open: "openNativeHabits", enable: "setNativeHabitsEnabled", projection: "NativeHabitsTabProjection" },
  });

  window.NativeHabitsShell = { available, tab };
})();
