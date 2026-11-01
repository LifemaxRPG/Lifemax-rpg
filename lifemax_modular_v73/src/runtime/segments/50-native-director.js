/* Life Director projection v2 (Today, with the dial's handles and its build; v1 was Today without them). Life Director
   (src/features/life-director) stays the owner of the plan, its rules and its save: the native screen draws LifeDirector.native's frame and Today, covers the page only
   while Life Director shows Today with nothing over it, picks the day of the strip itself, drags a handle over the places the
   page says it can land, asks the page what letting go there would change (dialDrop), has the page's own Change it run
   (dialApply) and plays the build the page describes; every other tap goes to the page's own handler.
   The tab lifecycle is NativeTabKit's. */
(function () {
  const director = () => window.LifeDirector && window.LifeDirector.native; // the feature's script runs after the runtime
  let day = 0; // the day of the strip on screen; today whenever the user comes to Life Director
  let celebrating = false; // XP is being celebrated on the page, which the screen must not cover

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
    /** a handle let go on the dial ({key, minute}: a stop's minute) or moved a quarter hour by TalkBack ({key, step: 1 or -1}):
        the confirmation the page would open (reply.confirm), or changed: false where the handle already is; nothing is held or
        saved, so Keep (or OK after a problem) needs no command */
    dialDrop(p) {
      return director().drop(String(p.key || ""), { minute: p.minute, step: p.step });
    },
    /** Change it on that confirmation, with the same payload: the page's own handler changes the rule, repairs the plan around it
        and saves. ok only once it is saved; the message is the page's (what changed, or why nothing was saved); the projection
        sent with the reply has the new plan and, unless motion is reduced, its build. The same request again is refused (the
        handle has moved), so the save changes once. */
    dialApply(p) {
      const { result, held } = holdCelebrations(() => director().apply(String(p.key || ""), { minute: p.minute, step: p.step }));
      if (held.length) celebrate(held);
      return result;
    },
  };

  /** the screen comes back once Today is clear and no XP celebration plays on the page: XP earned by a tap on the screen or in the
      page's own sheet (Done, Finish and log, Log it for today) is celebrated there first */
  function comeBack() {
    if (celebrating) return; // already waiting
    celebrating = true;
    afterCelebrations(() => {
      celebrating = false;
      tab.revisit();
    });
  }
  /** a session done from the screen earns XP: its celebration plays on the page, from the middle, and the screen waits for it */
  function celebrate(held) {
    lastTap = { x: innerWidth / 2, y: innerHeight / 2 };
    held.forEach(([delta, gains]) => celebrateXP(delta, gains));
    comeBack();
  }

  const tab = NativeTabKit.create({
    id: "life-director",
    plugin: "NativeLifeDirector",
    capability: "director.today.v2",
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
    afterOpen: () => director() && director().cover(), // the screen plays the build now, so a build playing on the page stops
    blocked: () => celebrating || !director() || director().showing() !== "today",
    globals: { open: "openNativeDirector", enable: "setNativeDirectorEnabled", projection: "NativeDirectorTabProjection" },
  });

  /* follow Life Director: refresh the screen when what the page shows changes, and come back once Today is clear again */
  document.addEventListener("DOMContentLoaded", () => {
    const ld = director();
    if (!ld) return;
    ld.onChange(() => {
      tab.schedule();
      comeBack();
    });
    ld.coveredBy(() => tab.active());
  });

  window.NativeDirectorProjection = { snapshot: projection, commands };
})();
