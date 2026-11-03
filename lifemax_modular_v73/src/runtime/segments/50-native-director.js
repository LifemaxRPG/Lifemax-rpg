/* Life Director projection v2 (Today, with the dial's handles and its build; v1 was Today without them). Life Director
   (src/features/life-director) stays the owner of the plan, its rules and its save: the native screen draws LifeDirector.native's frame and Today, covers the page only
   while Life Director shows Today with nothing over it, picks the day of the strip itself, drags a handle over the places the
   page says it can land, asks the page what letting go there would change (dialDrop), has the page's own Change it run
   (dialApply) and plays the build the page describes; every other tap goes to the page's own handler.
   Phase 3A: a screen that says it draws Week and Quests (director.week.v1) also covers those sections; the page keeps the
   section and the day (section, gotoDay and day render the page underneath), and every arrival lands on today (arrive).
   Phase 4B: a screen that says it draws My time (director.setup.v1) also covers My time and the first-run card, gets them as data
   (time, firstRun) with My time's sheets (rule, goalType, goalForm: form schemas), and saves through the do command.
   Phase 4A: Today's sheets and writes (director.sheets.v1), the XP of those writes celebrated by the screen from the reply
   (director.celebrations.v1), a level-up stepping the screen aside until it is dismissed, and the Overview's glance opening
   the screen's sheet (intent).
   The tab lifecycle is NativeTabKit's, warm: the kit asks the screen what it draws as soon as the switch is on. */
(function () {
  const director = () => window.LifeDirector && window.LifeDirector.native; // the feature's script runs after the runtime
  let celebrating = false; // XP is being celebrated on the page, which the screen must not cover

  /** the sections a screen draws besides Today, by the capability that says so */
  const PARTS = { week: "director.week.v1", quests: "director.week.v1", goals: "director.goals.v1", settings: "director.goals.v1",
                  setup: "director.setup.v1", "first-run": "director.setup.v1" };
  /** phase 4A: the screen draws Today's sheets and runs Today's actions itself */
  const SHEETS = "director.sheets.v1";
  /** the screen lets a session be dragged off the day (or removed by a TalkBack action): every session row says whether it can be
      (remove), and unskip takes the last removal back. Needs the sheets' writes, which it runs through. */
  const REMOVE = "director.remove.v1";
  const removes = () => tab.has(SHEETS) && tab.has(REMOVE);
  /** phase 4B: My time's sheets, which are forms (Today's are the rest) */
  const SETUP = "director.setup.v1";
  const FORMS = new Set(["rule", "goalType", "goalForm"]);
  const drawsSheet = (kind) => tab.has(FORMS.has(kind) ? SETUP : SHEETS);
  let sheet = null; // {kind, id}: the sheet the screen asked to see; none whenever the screen opens
  /** phase 4A: the screen draws the XP celebration of its own writes (the reply's celebrations), so the page stays covered and
      plays only the sound and buzz; without it the page plays the celebration itself and the screen steps aside for it */
  const CELEBRATES = "director.celebrations.v1";
  /** XP a write held back: in the reply for a screen that draws it, else played on the page once the reply has gone */
  const withCelebrations = (result, held) =>
    !held.length ? result
    : tab.has(CELEBRATES) ? Object.assign({}, result, { celebrations: NativeTabKit.celebrations(held) })
    : Object.assign({}, result, { after: () => celebrate(held) });
  const covers = () => ["today"].concat(Object.keys(PARTS).filter((k) => tab.has(PARTS[k])));
  /** the guides a screen draws, by section: Week's with Week, Today's with Today's sheets, set-up's with My time (4B). With Today's
      drawn by the screen, the page reports Today rather than its guide after set-up, so the screen covers Today and plays the
      first build itself (cover() stops the page's), then its guide. */
  const GUIDES = { week: "director.week.v1", today: SHEETS, setup: SETUP };
  const guides = (key) => tab.enabled() && !!GUIDES[key] && tab.has(GUIDES[key]);

  /** the frame, and the one section on the page now, only while the screen draws it (null when it has nothing to draw) */
  function projection() {
    const ld = director(), c = covers(), t = ld.frame().tab;
    const part = (k, f) => (t === k && c.includes(k) ? f() : null);
    return Object.assign(ld.frame(), {
      covers: c,
      today: part("today", () => ld.today(undefined, { removal: removes() })),
      week: part("week", () => ld.week()),
      quests: part("quests", () => ld.quests()),
      goals: part("goals", () => ld.goals()),
      settings: part("settings", () => ld.settings()),
      time: part("setup", () => ld.time()),
      firstRun: c.includes("first-run") && ld.showing() === "first-run" ? ld.firstRun() : null,
      guide: guides(t) ? ld.guide(t) : null,
      // the guide ? plays (the plan's guide.help), apart from guide because Goals and Settings have none of their own: the screen
      // switches to that section and plays it; null when the screen does not draw it there, and the ? is handed over
      help: ((k) => (k && guides(k) && c.includes(k) ? k : null))(ld.help()),
      sheet: sheet && drawsSheet(sheet.kind) ? ld.sheet(sheet.kind, sheet.id) : null,
      intent: tab.has(SHEETS) ? ld.intent() : null, // a sheet the Overview's glance asked for, taken once by its id
      celebrationTarget: NativeTabKit.celebrationTarget(),
    });
  }
  const commands = {
    /** show another day of the strip; the page's strip follows, nothing is saved and the screen stays */
    day: (p) => director().day(Number(p.index)),
    /** a day from Week or up next: Today on that day, as the page's own row does */
    gotoDay: (p) => director().gotoDay(Number(p.index)),
    /** another section; one the screen does not draw is shown by the page, and the kit steps the screen aside after the reply */
    section: (p) => director().section(String(p.id || "")),
    /** the sheet the screen shows (phase 4A: session, timer, meal, move, disrupt; 4B: rule, goalType, goalForm): its model comes with
        this reply and every update after it; {kind: ""} closes it */
    sheet: (p) => {
      if (p.kind ? !drawsSheet(String(p.kind)) : !tab.has(SHEETS) && !tab.has(SETUP)) return { ok: false, message: "Life Director cannot do that." };
      sheet = p.kind ? { kind: String(p.kind), id: String(p.id || "") } : null;
      return { ok: true };
    },
    /** Today's writes (phase 4A): done, logToday, skip, pin, moveTo, timer, and Something came up's disrupt, energy, replan, regroup
        and dismissBanner; with director.remove.v1 also unskip {id, op}, which takes the last skip back (a skip that can be taken back
        answers undo: the page's words for it). Those that can pay XP run with the page's celebration held: the reply carries it for a screen that draws
        it (director.celebrations.v1), else it plays on the page once the reply has reached the screen, which steps aside for it;
        a repeat of one that landed answers "Already recorded." (or "Already added", "Already skipped.") */
    today: (p) => {
      if (!tab.has(SHEETS)) return { ok: false, message: "Life Director cannot do that." };
      const name = String(p.name || "");
      if (name === "unskip" && !removes()) return { ok: false, message: "Life Director cannot do that." };
      const { result, held } = holdCelebrations(() => director().todayRun(name, p));
      return withCelebrations(result, held);
    },
    /** a guide the screen drew was finished ({key, finished: true}) or skipped (Skip, Back): seen, saved on the page */
    tourSeen: (p) => director().tourSeen(p.key, p.finished === true),
    /** a change to a goal or a setting ({name, ...payload}): the page's own function runs, re-plans and saves; ok only when saved.
        exportBackup answers the backup's text, which the screen copies. Phase 4B adds My time's: sleepSave, ruleSave, ruleRemove,
        routine, meals, goalAdd and finishSetup, which also answer changed and know a repeat (a new commitment or goal by its op). A repeat after a reply is the screen's to avoid (set-like
        changes: the same priority again changes nothing; a removed goal is "gone"). */
    do: (p) => director().run(String(p.name || ""), p),
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
      if (tab.has(CELEBRATES)) return withCelebrations(result, held);
      if (held.length) celebrate(held); // the page plays it now and the screen waits for it
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
    warm: true,
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
    arrive: () => director() && director().arrive(), // today on the strip whenever the player comes to Life Director
    beforeOpen: () => {
      sheet = null; // the screen opens with no sheet (it closes them when hidden, A24)
    },
    afterOpen: () => director() && director().cover(), // the screen plays the build now, so a build playing on the page stops
    // a level-up is not held: it comes over the page (.luov) while the screen covers it, so the screen steps aside for it; nor is
    // XP earned on the page itself (its rising +N XP, .fx-xp), which the screen must not cover either
    blocked: () => celebrating || !director() || !covers().includes(director().showing()) || !!document.querySelector(".luov, .fx-xp"),
    watch() {}, // and comes back by itself once the page is clear again (the level-up dismissed)
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
    ld.guidedBy(guides);
    // a tap on the Overview's glance opens the screen's own sheet (intent) instead of the page's, once the screen draws them
    ld.intendedBy(() => tab.enabled() && tab.has(SHEETS));
  });

  window.NativeDirectorProjection = { snapshot: projection, commands };
})();
