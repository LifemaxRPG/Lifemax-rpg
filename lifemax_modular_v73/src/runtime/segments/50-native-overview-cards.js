/* Native Overview cards (beta, off by default): the Overview's head and cards drawn by a native screen, projection
   overview.cards.v1. The page stays the owner of every card's decisions and the only writer: each card's fields come from the
   same data function the page's own card draws from (ovHeroData, ovNextData, ... , LifeDirector.native.glance), with colours as
   ARGB and images by key. The screen hands every tap back: a destination as a {view} action, anything that opens over the page
   (the attribute sheet, Daily Luck, the shop, the Health page, the app picker, the web editor) as a command, after which the
   screen steps aside and comes back by itself once the page is clear again.
   One owner for the dashboard view: 50-native-overview.js registers a composite that asks these cards first, then the live
   shell, so this tab is created managed (the kit neither registers it nor opens it on arrival). When the cards cannot draw
   (a missing capability, an open or a refresh rejected) they give up for the session and the live shell takes the view;
   turning the switch off and on again tries once more. Plan: docs/architecture/plans/OVERVIEW_BODY.md, ADR 0012. */
(function () {
  const PLUGIN = "NativeOverviewCards", CAPABILITY = "overview.cards.v1";
  /* the cat card's live cats (beta, off by default): native draws the cats itself from its own art, living in the whole house on a
     local autopilot, when the owner's switch is on and the screen says it can (the capability). .v1 carries the first cat only,
     {coat, stage, weak}; .v2 carries the list, {cats: [first, second?]}, each with its name. The still portrait is always sent too,
     as the fallback. */
  const CAT_LIVE = "overview.cat.live.v1", CAT_LIVE_LIST = "overview.cat.live.v2", CAT_SETTING = "nativeCats";
  const CAT_NAME_MAX = 40; // the longest name the screen accepts
  /* a genetic (dna_) coat is composed by the page (petDnaCompose); a screen that can ask for its sheets (the `coat` command,
     50-pet-coat.js) draws it live too. Without the capability a dna_ coat stays a still. */
  const CAT_COAT = "overview.cat.coat.v1";
  /* each live cat's proportions (rig: ear, tail, leg, body and whisker multipliers from its genes, 38-pet-rig.js petRigWire), for a
     screen that re-assembles the cat's frames from the rig; left out for a cat the art fits as drawn */
  const CAT_RIG = "overview.cat.rig.v1";
  const LEGACY_COATS = ["ginger", "gray", "black", "tuxedo"]; // the coats native ships art for
  const CELEBRATION_KEEP_MS = 2000; // a celebration handed over stays in the projection this long; the screen plays each id once
  const WIDE = 860; // above this the page's sidebar is a side rail, not the bottom bar the shared bars are drawn from (02-layout.css)
  const GAVE_UP = "The native Overview cards could not open. The live Overview is showing.";

  let gaveUp = false, failReason = ""; // the cards could not draw: the live shell holds the view for the rest of the session
  let waiting = false; // a wait for the page's celebration to end is registered (afterCelebrations)
  let held = []; // celebrations handed to the screen: {id, xp, origin, gains, at}
  let lastCelebration = 0;
  /* arrival: the page's Overview arrival (ovReplay: the cards rise, the ring sweeps, the bars grow) is the screen's to play when
     it opens on a real arrival, and only then: the counter rises when the screen opens within ARRIVAL_MS of the page going to the
     Overview from another view, or on a cold start while the veil still hides the page. A screen that comes back after a sheet,
     Luck or a celebration opens with the counter unchanged, so it stays still. It rises with the clock, so a reloaded page never
     repeats one the screen has played; 0 until the first arrival. */
  const ARRIVAL_MS = 600;
  let arrival = 0, arrivedAt = -Infinity;
  /* the cold-start veil: while the cards are wanted at boot the page's Overview stays hidden (html.native-boot), so the screen
     appears over an empty body instead of over the page's cards; lifted when the screen opens, when it declines, or at the latest
     after BOOT_VEIL_MS so a slow or silent plugin never leaves the Overview blank */
  const BOOT_VEIL_MS = 1500;
  let veiled = false, veilTimer = 0;
  let quietToast = -1; // the page's toast of a layout it could not store: the screen says the reply's message instead

  /* ---- colours and images ---- */
  /** a colour the page writes (a hex, an rgba, or var(--token)) as the ARGB the native theme takes */
  function argb(v) {
    const s = String(v || "").trim(), m = /^var\((--[\w-]+)\)$/.exec(s);
    return NativeTabKit.color(m ? getComputedStyle(document.documentElement).getPropertyValue(m[1]).trim() : s);
  }
  const keys = new Map(); // data URL -> key, so a long URL is hashed once
  /** FNV-1a of the URL, the key native decodes an image under once (PageImages) */
  function keyOf(url) {
    let k = keys.get(url);
    if (!k) {
      let h = 0x811c9dc5;
      for (let i = 0; i < url.length; i++) h = Math.imul(h ^ url.charCodeAt(i), 0x01000193);
      k = "i" + (h >>> 0).toString(16);
      if (keys.size > 64) keys.clear();
      keys.set(url, k);
    }
    return k;
  }

  /* ---- the cards, each from the data function the page's card draws from ---- */
  const delta = (d) => (d ? { up: !!d.up, text: d.text } : null);
  function tip() {
    const t = FEATURE_TIPS.dashboard;
    return state.tutorialDone && !(state.tipsSeen || {}).dashboard ? { key: "dashboard", icon: t.icon, title: t.title, body: t.body, dismiss: "Dismiss tip" } : null;
  }
  /** every tile the editor knows, in its default order: name, what it shows, why it may be empty, icon and colour */
  function tileInfo() {
    const pet = petData();
    return OV_DEFAULT.map((id) => {
      const t = OV_TILES.find((x) => x.id === id);
      return { id, name: t.name, sub: id === "cat" && pet ? pet.name + "’s room" : t.sub, note: ovTileNote(id), icon: t.icon, color: argb(t.col) };
    });
  }
  function next() {
    const d = ovNextData();
    return {
      title: d.title, note: d.note,
      moves: d.moves.map((x) => ({ title: x.title, sub: x.sub, icon: x.icon, color: argb(x.color), warn: x.warn || "", done: !!x.done, cta: x.cta || "", pill: !!x.pill,
        go: x.go.attr ? { attr: x.go.attr } : x.go.luck ? { luck: true } : { view: x.go.view } })),
    };
  }
  function hero(images) {
    const d = ovHeroData();
    if (d.avatar) images[keyOf(d.avatar)] = d.avatar;
    return {
      ring: { label: d.ring.label, today: d.ring.today, over: d.ring.over, of: d.ring.of, r: 62, stroke: 11, arcs: d.ring.arcs.map((a) => ({ color: argb(a.color), length: a.length, offset: a.offset })) },
      avatar: d.avatar ? keyOf(d.avatar) : null, initial: d.initial, level: d.level, title: d.title, className: d.className,
      xp: { text: d.xp.text, next: d.xp.next, pct: d.xp.pct },
      streak: { days: d.streak.days, label: d.streak.label, dots: d.streak.dots.map((x) => ({ on: x.on, now: x.now })) },
      foot: d.foot.map((f) => ({ label: f.label, value: f.value, delta: delta(f.delta) })),
    };
  }
  function apps(images) {
    const d = ovAppsData();
    if (!d) return null;
    return {
      title: d.title, link: d.link, add: d.add, hint: d.hint,
      items: d.items.map((a) => {
        if (a.icon) images[keyOf(a.icon)] = a.icon;
        return { pkg: a.pkg, label: a.label, a11y: a.a11y, image: a.icon ? keyOf(a.icon) : null };
      }),
    };
  }
  function health() {
    const d = ovHealthData();
    if (!d || !d.on) return d;
    return Object.assign({}, d, { days: d.days.map((x) => ({ label: x.label, frac: x.frac, now: x.now, a11y: x.a11y })) });
  }
  function attrs() {
    const d = ovAttrsData();
    return { title: d.title, link: d.link, legend: d.legend, gems: d.gems.map((g) => Object.assign({}, g, { color: argb(g.color) })) };
  }
  function week() {
    const d = ovWeekData();
    return {
      title: d.title, total: d.total, delta: delta(d.delta), pace: d.pace, selected: d.selected, empty: d.empty,
      bars: d.bars.map((b) => ({ date: b.date, label: b.label, today: b.today, xp: b.xp, a11y: b.a11y, tip: b.tip, segs: b.segs.map((s) => ({ color: argb(s.color), frac: s.frac })) })),
      top: d.top.map((x) => ({ color: argb(x.color), text: x.text })),
    };
  }
  /** the cat's card: none without a cat (the page hides the card then: #ovDuo.solo). portrait: the first cat sitting, drawn by the
      room's own code (petPortrait), by image key; null while its art loads (the screen is refreshed once it has) or for a cat gone */
  function cat(images) {
    const d = petPeekData();
    if (d.mode === "invite") return null;
    let url = null;
    if (d.mode === "alive" && typeof petPortrait === "function") {
      try { url = petPortrait(state.pet, () => tab.schedule()); } catch (e) { console.warn("overview cards: portrait", e); }
    }
    if (url) images[keyOf(url)] = url;
    return { mode: d.mode, eyebrow: d.eyebrow, name: d.name, hearts: d.mode === "alive" ? d.hearts : null, portrait: url ? keyOf(url) : null,
      live: d.mode === "alive" ? liveCat() : null };
  }
  /** what native needs to draw the cats itself. null with the switch off, a screen that cannot draw them, or a first cat native
      has no art for (the still portrait stays). A screen that lists .v2 gets {cats: [...]}: the first cat and, when it is alive and
      native has art for it too, the second; an older screen gets the first cat's bare look. */
  function liveCat() {
    if (lmaLoad()[CAT_SETTING] !== true || !tab.has(CAT_LIVE)) return null;
    const first = liveLook(state.pet);
    if (!first) return null;
    if (!tab.has(CAT_LIVE_LIST)) return first;
    const cats = [named(first, state.pet)], second = liveLook(state.pet2);
    if (second) cats.push(named(second, state.pet2));
    return { cats };
  }
  /** one cat's look for native: its coat key, growth stage (-1 grown, 0..5 a kitten) and whether it is the hungry, weak cat (the
      room's PET_WEAK rows); null for a cat that is gone or whose coat native cannot draw (a legacy coat, or a genetic one the page
      holds and the screen can ask for) */
  function liveLook(p) {
    if (!p || !p.alive) return null;
    if (!LEGACY_COATS.includes(p.coat) && !(tab.has(CAT_COAT) && typeof petCoatKnown === "function" && petCoatKnown(p.coat))) return null;
    const o = { coat: p.coat, stage: petGrowIdx(p), weak: petStage(p) >= 3 };
    const rig = tab.has(CAT_RIG) && typeof petRigWire === "function" ? petRigWire(p) : null;
    if (rig) o.rig = rig;
    return o;
  }
  /** the look with the cat's name (native seeds the cat's habits with it); a nameless cat keeps its bare look */
  function named(look, p) {
    const name = typeof p.name === "string" ? p.name.slice(0, CAT_NAME_MAX) : "";
    return name ? Object.assign(look, { name }) : look;
  }
  function luck() {
    const d = ovLuckData();
    return { eyebrow: d.eyebrow, rolled: d.rolled, score: d.score, color: d.rolled ? argb(d.color) : null, text: d.text, button: d.button };
  }
  function recent() {
    const d = ovRecentData();
    return { title: d.title, link: d.link, empty: d.empty, items: d.items.map((a) => ({ name: a.name, icon: a.icon, color: argb(a.color), meta: a.meta, xp: a.xp })) };
  }
  function plan() {
    const ld = window.LifeDirector && LifeDirector.native;
    if (!ld || typeof ld.glance !== "function") return null;
    try {
      return ld.glance();
    } catch (e) {
      console.warn("overview cards: glance", e);
      return null;
    }
  }
  function haptics() {
    if (!fxOpt().haptics) return null;
    const L = HAP_LEVELS[hapLevelKey()];
    return { dm: L.dm, add: L.add, amp: L.amp, min: L.min, max: HAP_MAX };
  }
  function projection() {
    const images = {}, now = Date.now();
    held = held.filter((c) => now - c.at < CELEBRATION_KEEP_MS);
    const out = {
      head: ovHeadData(),
      tip: tip(),
      layout: ovLayout(),
      tileInfo: tileInfo(),
      editor: Object.assign({}, OV_EDIT_WORDS),
      tiles: { plan: plan(), next: next(), hero: hero(images), rewards: ovRewardsData(), apps: apps(images), health: health(), attrs: attrs(), week: week(), cat: cat(images), luck: luck(), recent: recent() },
      images,
      celebrations: held.map((c) => ({ id: c.id, xp: c.xp, origin: c.origin, gains: c.gains })),
      celebrationTarget: NativeTabKit.celebrationTarget(),
      motion: { reduced: fxReduced() },
      haptics: haptics(),
      arrival,
    };
    return window.NativeCatWorld ? NativeCatWorld.extend(out, tab) : out; // the cats' world block, for a screen with pet.world.v1
  }

  /* ---- commands: what the screen's taps run on the page, without closing it first ---- */
  const ok = (more) => Object.assign({ ok: true }, more);
  const no = (message) => ({ ok: false, message });
  const commands = {
    /** the live cat's genetic coat: one sheet of it (kind) as the room composes it, a grey-index PNG and the palette, which native
        caches by key (50-pet-coat.js). Refused for a coat or a sheet the page does not hold. */
    async coat(p) {
      if (typeof petCoatAnswer !== "function") return no("Coats are not available.");
      return petCoatAnswer(p);
    },
    /** a tap on the glance (a session, the timer, a meal, the link or the empty state): Life Director's Today and the tap there.
        It leaves the view, so the hand-over closes the cards once Life Director's screen shows; the reply never arrives. */
    plan(p) {
      const ld = window.LifeDirector && LifeDirector.native;
      if (!ld || typeof ld.fromGlance !== "function") return no("Life Director is not available.");
      return ld.fromGlance(String(p.act || ""), String(p.id == null ? "" : p.id)) ? ok() : no("That is no longer on the plan.");
    },
    /** a gem or a Train move: the tapped tile in view behind the sheet (its backdrop is see-through), then the attribute sheet */
    attr(p) {
      const id = String(p.id || "");
      if (!ATTRS.some((a) => a.id === id)) return no("Something went wrong. Nothing was changed.");
      const tile = document.querySelector('#ovTiles > .ov-tile[data-tile="' + String(p.tile || "").replace(/[^\w-]/g, "") + '"]');
      if (tile) tile.scrollIntoView({ block: "center" });
      openAttr(id);
      return ok();
    },
    /** Roll / View roll: the page rolls (and saves) before its overlay, which steps the screen aside before any reply could
        show the number. A roll already made today is shown again and changes nothing. */
    luck() {
      const rolled = !!luckToday();
      openLuck();
      return ok({ changed: !rolled });
    },
    shop() {
      ecoShopOpen();
      return ok();
    },
    health() {
      if (!lmaNative()) return no("Health sync works in the Android app.");
      lmaHealthOpen();
      return ok();
    },
    /** an app shortcut, only one the owner added: it opens over LifeMax, the cards stay; a failure comes as the page's toast */
    appLaunch(p) {
      const pkg = String(p.pkg || ""), app = (lmaLoad().apps || []).find((a) => a.pkg === pkg);
      if (!app || !window.AppsShortcuts) return no("That app is not in your shortcuts.");
      AppsShortcuts.launch(app.pkg);
      return ok();
    },
    appAdd() {
      if (!window.AppsShortcuts) return no("App shortcuts are not available.");
      AppsShortcuts.openPicker();
      return ok();
    },
    /** Apps "Edit ›": the Apps tab, a web page, after the reply */
    appsEdit: () => ok({ navigate: "apps" }),
    /** the tip's ✕: stored, or the page's own words for why not; a tip already dismissed changes nothing */
    tip() {
      if ((state.tipsSeen || {}).dashboard) return ok({ changed: false, message: "Already recorded." });
      return dismissFeatureTip("dashboard") ? ok({ changed: true }) : no(unsavedMessage());
    },
    /** the native editor's change (phase 2): stored through the page's own layout save, made whole as ovLayout() makes any */
    layout(p) {
      const strings = (a) => Array.isArray(a) && a.every((x) => typeof x === "string");
      if (!strings(p.order) || !strings(p.hidden)) return no("Something went wrong. Nothing was changed.");
      return saveLayout(ovLayout({ order: p.order, hidden: p.hidden.filter((id, i, a) => a.indexOf(id) === i) }));
    },
    /** the native editor's Reset to default */
    layoutReset: () => saveLayout({ order: OV_DEFAULT.slice(), hidden: [] }),
    /** Edit (phase 1, kept for an older app): the page's own editor; the screen steps aside while it shows and comes back on
        Done or Back. The app's own editor (phase 2) sends layout and layoutReset instead. */
    edit() {
      const ed = document.getElementById("ovEdit");
      if (ed && ed.hidden) ovEditOpen();
      return ok();
    },
  };

  /** a layout from the native editor, stored by ovLayoutSave (the owner: a write it could not store is taken back, and the
      reply's projection carries the stored layout); a layout already stored is recognised and not written again */
  function saveLayout(L) {
    const was = ovLayout(), set = (a) => a.slice().sort().join();
    if (L.order.join() === was.order.join() && set(L.hidden) === set(was.hidden)) return ok({ changed: false, message: "Already recorded." }); // hidden is a set
    const before = tab.toasts();
    if (ovLayoutSave(L)) return ok({ changed: true });
    if (tab.toasts() > before) quietToast = tab.toasts(); // storage full's toast: the reply says it
    return no(unsavedMessage());
  }

  /* ---- covering and stepping aside ---- */
  /** a celebration plays on the page: the screen keeps away, and a wait registered once brings it back after (however it began) */
  function celebrationShowing() {
    if (!celebrating()) return false;
    if (!waiting) {
      waiting = true;
      afterCelebrations(() => {
        waiting = false;
        tab.revisit();
      });
    }
    return true;
  }
  function blocked() {
    const ed = document.getElementById("ovEdit");
    return gaveUp || innerWidth > WIDE || (typeof lmaThumbWarmBusy !== "undefined" && lmaThumbWarmBusy) || celebrationShowing() ||
      !!document.querySelector(".luov, .lmv-hud.on") || !!(ed && !ed.hidden);
  }
  /** the cards could not draw: give up for the session, let the live shell take the view, say so once */
  function onFail(reason) {
    unveil();
    if (gaveUp) return;
    gaveUp = true;
    failReason = reason;
    console.warn("Native Overview cards gave up: " + reason);
    if (window.NativeTabs) NativeTabs.heal();
    toast(GAVE_UP);
  }
  /** the cat's room loop runs only while the page's card is what shows */
  const pets = () => { try { petSync(); } catch (e) { console.warn("overview cards: pet loop", e); } };

  const tab = NativeTabKit.create({
    id: "dashboard",
    plugin: PLUGIN,
    capability: CAPABILITY,
    setting: "nativeOverviewCards",
    toggle: "nativeOverviewCardsToggle",
    managed: true,
    render: "renderOverview",
    notices: { quiet: (id) => id === quietToast },
    projection,
    commands,
    arrive() {
      arrivedAt = Date.now();
    },
    beforeOpen(entered) {
      if (entered && (veiled || Date.now() - arrivedAt <= ARRIVAL_MS)) arrival = Math.max(arrival + 1, Date.now());
    },
    afterOpen() {
      if (window.NativeOverviewShell && NativeOverviewShell.isOpen()) NativeOverviewShell.close(); // never two drawings behind one entry
      unveil();
      pets();
    },
    afterClose: pets,
    onFail,
    blocked,
    // the editor shows and hides by its hidden attribute; sheets that are body children and noscroll the kit already watches
    watch(observe) {
      const ed = document.getElementById("ovEdit");
      if (ed) observe(ed, { attributes: true, attributeFilter: ["hidden"] });
    },
    globals: { open: "openNativeOverviewCards", enable: "setNativeOverviewCardsEnabled", projection: "NativeOverviewCardsProjection" },
  });

  /* the switch: turning it off or on again tries the cards once more */
  const enable = window.setNativeOverviewCardsEnabled;
  window.setNativeOverviewCardsEnabled = (on) => {
    gaveUp = false;
    failReason = "";
    return enable(on);
  };

  /* a celebration that starts while the cards show is drawn by the screen: the page plays the sound and buzz 120 ms after it
     is sent, and holds nothing, so the screen does not step aside for it */
  fxSink = (xp, gains, origin) => {
    if (!tab.active() || tab.blocked()) return false;
    const [c] = NativeTabKit.celebrations([[xp, gains]]); // also queues the sound and buzz
    lastCelebration = Math.max(lastCelebration + 1, Date.now()); // ids rise with the clock, so a reloaded page never repeats one
    held.push({ id: lastCelebration, xp: c.xp, origin: { x: Math.round(origin.x), y: Math.round(origin.y) }, gains: c.gains, at: Date.now() });
    tab.send();
    return true;
  };

  /* page hooks that change what the cards show, or whether they may show, without a render or a body change */
  if (typeof ovAppsRender === "function") {
    const render = ovAppsRender; // an app's icon arrives outside renderOverview
    globalThis.ovAppsRender = function () {
      const r = render.apply(this, arguments);
      tab.schedule();
      return r;
    };
  }
  if (typeof lmvHudOpen === "function") {
    const hud = lmvHudOpen; // the one place the voice HUD's on class is set; the HUD is created after this runs
    globalThis.lmvHudOpen = function () {
      const r = hud.apply(this, arguments);
      tab.follow();
      return r;
    };
  }
  addEventListener("resize", () => tab.follow()); // a rotation across the wide rule
  if (typeof lmaThumbWarm === "function") {
    const warm = lmaThumbWarm; // the first-install thumbnail pass navigates through every page: never behind the cards
    globalThis.lmaThumbWarm = function () {
      if (tab.active() || tab.opening()) return Promise.resolve();
      return warm.apply(this, arguments);
    };
  }

  const wanted = () => tab.enabled() && !gaveUp;

  /* the live cat's switch: the screen is sent the cat again at once, so it comes to life (or back to the still) without waiting */
  window.setNativeCatsEnabled = (on) => {
    lmaLoad()[CAT_SETTING] = !!on;
    lmaSave();
    syncCats();
    tab.schedule();
  };
  function syncCats() {
    const input = document.getElementById("nativeCatsToggle");
    if (input) input.checked = lmaLoad()[CAT_SETTING] === true;
  }
  document.addEventListener("DOMContentLoaded", syncCats);
  syncCats();
  setTimeout(syncCats, 700);

  /* the cold-start veil (see BOOT_VEIL_MS): set now, while the page is still loading, so the page's cards never paint first */
  function unveil() {
    if (!veiled) return;
    veiled = false;
    clearTimeout(veilTimer);
    document.documentElement.classList.remove("native-boot");
  }
  if (wanted() && state.onboardingComplete && lmaView() === "dashboard" && innerWidth <= WIDE) {
    veiled = true;
    document.documentElement.classList.add("native-boot");
    veilTimer = setTimeout(unveil, BOOT_VEIL_MS);
  }
  /** an open the owner asked for; one that ends without the screen showing (declined: blocked, not wanted, gave up) lifts the veil */
  async function open() {
    const r = await tab.open(false);
    if (!tab.active() && !tab.opening()) unveil();
    return r;
  }

  window.NativeOverviewCards = {
    wanted,
    active: () => tab.active(),
    opening: () => tab.opening(),
    coverable: () => !gaveUp && tab.coverable(),
    open,
    close: () => tab.close(),
    covering: () => tab.active(),
    diagnostics: () => ({ enabled: tab.enabled(), wanted: wanted(), gaveUp, failReason, active: tab.active(), opening: tab.opening(),
      blocked: tab.blocked(), veiled, arrival, petLoop: typeof petWantRun === "function" ? petWantRun() : false, celebrations: held.length }),
  };
  if (window.NativeCatWorld) commands.world = NativeCatWorld.command((c) => tab.has(c)); // the cats' world blob (pet.world.v1)
  window.NativeOverviewCardsProjection.commands = commands;
})();
