/* Life Director projection v1 (Today). Life Director (src/features/life-director) stays the owner of the plan, its rules and
   its save: the native screen draws LifeDirector.native's frame and Today, covers the page only while Life Director shows
   Today with nothing over it, picks the day of the strip itself and hands every other tap to the page's own handler.
   The tab lifecycle is NativeTabKit's. */
(function () {
  const director = () => window.LifeDirector && window.LifeDirector.native; // the feature's script runs after the runtime
  let day = 0; // the day of the strip on screen; today whenever the screen opens

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

  const tab = NativeTabKit.create({
    id: "life-director",
    plugin: "NativeLifeDirector",
    capability: "director.today.v1",
    setting: "nativeDirector",
    toggle: "nativeDirectorToggle",
    projection,
    /** the page runs the tap as its element would; when that leaves nothing over Today, the screen comes straight back */
    onAction(e) {
      director().act(e.action, e.id);
      tab.revisit();
    },
    commands,
    beforeOpen() {
      day = 0;
    },
    blocked: () => !director() || director().showing() !== "today",
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
