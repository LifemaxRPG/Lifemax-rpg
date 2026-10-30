/* Life Director projection v1 (Today). Life Director (src/features/life-director) stays the owner of the plan, its rules and
   its save: the native screen draws LifeDirector.native's frame and Today, covers the page only while Life Director shows
   Today with nothing over it, picks the day of the strip itself and hands every other tap to the page's own handler.
   The tab lifecycle is NativeTabKit's. */
(function () {
  const director = () => window.LifeDirector && window.LifeDirector.native; // the feature's script runs after the runtime
  let day = 0; // the day of the strip on screen; today whenever the user comes to Life Director
  let celebrating = false; // XP earned by a tap is being celebrated on the page, which the screen must not cover

  function projection() {
    const ld = director();
    return Object.assign(ld.frame(), { today: ld.today(day) });
  }
  const commands = {
    /** show another day of the strip; nothing is saved and the screen stays */
    day(p) {
      const i = Number(p.index),
        today = Number.isInteger(i) ? director().today(i) : null;
      if (!today || today.dayIndex !== i) return { ok: false, message: "That day is not on the plan." };
      day = i;
      return { ok: true };
    },
  };

  /** a session done from the screen earns XP: its celebration plays on the page, from the middle, and the screen waits for it */
  function celebrate(held) {
    celebrating = true;
    lastTap = { x: innerWidth / 2, y: innerHeight / 2 };
    held.forEach(([delta, gains]) => celebrateXP(delta, gains));
    afterCelebrations(() => {
      celebrating = false;
      tab.revisit();
    });
  }

  const tab = NativeTabKit.create({
    id: "life-director",
    plugin: "NativeLifeDirector",
    capability: "director.today.v1",
    setting: "nativeDirector",
    toggle: "nativeDirectorToggle",
    projection,
    /** the page runs the tap as its element would; when that leaves nothing over Today, the screen comes straight back */
    onAction(e) {
      const { held } = holdCelebrations(() => director().act(e.action, e.id));
      if (held.length) celebrate(held);
      tab.revisit();
    },
    commands,
    beforeOpen(entered) {
      if (entered) day = 0;
    },
    blocked: () => celebrating || !director() || director().showing() !== "today",
    globals: { open: "openNativeDirector", enable: "setNativeDirectorEnabled", projection: "NativeDirectorTabProjection" },
  });

  /* follow Life Director: refresh the screen when what the page shows changes, and come back once Today is clear again */
  document.addEventListener("DOMContentLoaded", () => {
    const ld = director();
    if (ld)
      ld.onChange(() => {
        tab.schedule();
        tab.revisit();
      });
  });

  window.NativeDirectorProjection = { snapshot: projection, commands };
})();
