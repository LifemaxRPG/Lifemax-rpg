/* Developer parkour demo (pet.demo.v1): while the page's developer mode is on (state.devMode, Settings > Enable developer mode), a
   Cat screen that advertised pet.demo.v1 gets a `demo` field {label, hint} and shows a "Demo parkour" button; the native cats run a
   parkour tour on tap. This is a DEVELOPER DEMO with no data effects: the tap goes to the native brain only, never back to the page
   (no command, no XP, no care counters, no likes). The page's only part is whether the button exists.
   - extend(out, tab): adds `demo` only for a screen with pet.demo.v1 and only with state.devMode on; otherwise the projection is
     exactly what it was (an older screen or page, or developer mode off, shows no button).
   - toggling developer mode re-projects the Cat tab so the button appears or goes at once.
   Loaded after 50-native-pet-play.js. */
(function () {
  const CAP = "pet.demo.v1";
  const WORDS = { label: "Demo parkour", hint: "Developer demo: one cat runs a parkour tour in the room" };

  /** Adds the demo button to the Cat tab's projection `out` for a screen that advertised pet.demo.v1 while developer mode is on */
  function extend(out, tab) {
    if (!tab || typeof tab.has !== "function" || !tab.has(CAP) || typeof state === "undefined" || !state || !state.devMode) return out;
    out.demo = { label: WORDS.label, hint: WORDS.hint };
    return out;
  }

  // developer mode toggled in Settings: the Cat tab (if it is showing) is projected again
  const toggle = window.toggleDevMode;
  if (typeof toggle === "function")
    window.toggleDevMode = function (on) {
      const r = toggle.apply(this, arguments);
      try {
        if (window.NativePet && typeof renderPetView === "function") renderPetView(true);
      } catch (e) {
        console.warn("[native demo] refresh", e);
      }
      return r;
    };

  window.NativePetDemo = { capability: CAP, extend, words: WORDS };
})();
