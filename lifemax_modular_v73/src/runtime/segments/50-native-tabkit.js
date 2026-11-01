/* NativeTabKit: the web half of a native tab. A tab supplies what is specific to it (its projection and what its actions do);
   the kit owns the rest: session and sequence numbers, open/update/close, the hand-over between native tabs (NativeTabs),
   refresh on render, resume and a timer, the settings toggle and the public entry points. The native half is ComposeTabPlugin.
   Contract: docs/architecture/CONTRACT.md (A03, A07, A23) and docs/architecture/NATIVE_TAB_GUIDE.md. */
(function () {
  const NativeTabs = (typeof window !== "undefined" && window.NativeTabs) || { cover() {}, cancel() {}, leave(id, to, fn) { fn(); }, flush() {}, register() {}, has() { return false; }, heal() {} };
  const now = () => typeof performance !== "undefined" ? performance.now() : Date.now();
  const TOKENS = ["bg", "bg-elev", "panel", "panel2", "panel3", "text", "text-dim", "muted", "muted2", "accent", "accent-dim", "accent-strong", "line", "line-soft", "good", "gold", "bad"];

  const probe = document.createElement("canvas").getContext("2d", { willReadFrequently: true }); // read on the CPU, no GPU round trip

  /** a CSS colour as ARGB, which is what the native theme takes (translucent only where the colour is, as accent-dim). The canvas
      stores colours premultiplied, so a translucent one comes back with its red, green and blue a few steps off (#7c9cff22 reads
      0x22809eff), which no one can see once it is drawn at that alpha. */
  function color(v) {
    probe.clearRect(0, 0, 1, 1);
    probe.fillStyle = "#000"; // what a colour the canvas cannot parse reads as
    probe.fillStyle = v;
    probe.fillRect(0, 0, 1, 1);
    const a = probe.getImageData(0, 0, 1, 1).data;
    return ((a[3] << 24) | (a[0] << 16) | (a[1] << 8) | a[2]) >>> 0;
  }
  /** the page's current palette, so the native screen matches whatever theme and accent is on */
  function tokens() {
    const root = getComputedStyle(document.documentElement);
    return Object.fromEntries(TOKENS.map((k) => [k, color(root.getPropertyValue("--" + k).trim())]));
  }
  function light() {
    return document.documentElement.classList.contains("light-theme");
  }

  const CUE_AFTER_MS = 120; // the sound and buzz of a celebration, once the reply carrying it has reached the screen
  /** where celebrateXP's orbs fly: the centre of the top bar's logo, from the top of the page, as the shared bar draws it */
  function celebrationTarget() {
    const r = document.getElementById("brandLogo")?.getBoundingClientRect();
    return r && r.width ? { x: r.x + r.width / 2, y: r.y + scrollY + r.height / 2 } : { x: innerWidth / 2, y: 44 };
  }
  /** what an attribute gain looks like on the screen: the page's colour and short name for it */
  function gainOf(g) {
    const a = ATTRS.find((x) => x.id === g.id);
    return { attribute: g.id, short: a ? a.short : g.id, xp: g.xp, color: color(ATTR_COL[g.id] || "#6c8cff") };
  }
  /** XP a command held back (holdCelebrations) for a screen that draws the celebration itself: the reply's `celebrations`
      ([{xp, gains}]), while the page plays only the sound and buzz (xpCue), once the reply has reached the screen */
  function celebrations(held) {
    if (held.length) setTimeout(() => held.forEach((h) => xpCue(h[0])), CUE_AFTER_MS);
    return held.map(([xp, gains]) => ({ xp, gains: gains.map(gainOf) }));
  }

  /**
   * cfg: id (the page view, e.g. "character"), plugin (Capacitor plugin name), capability, setting (the owner's switch, a key in
   * lmaLoad(): off until they turn it on, or, with defaultOn for a tab that has become the default, on until they turn it off),
   * toggle (settings checkbox id), projection() (the tab's own fields), onAction(event) (what a native action does on the page,
   * after the screen has closed; none for a tab whose screen sends only views and commands), render (name of the page's render
   * function to follow while open), globals {open, enable, projection}. An action naming a view ({view}) instead goes there
   * without closing first: the hand-over closes the screen once the next one shows, or at once for a page the web draws, so
   * moving to another native tab never flashes the page.
   * Optional: commands {name: async (payload, {requestId}) => reply} run on the page WITHOUT closing the screen (a draft
   * preview, a save); the reply ({ok, message, ...}) goes back with the next update, and reply.navigate, if set, is followed
   * afterwards. requestId is the screen's id for the request ("" when it sent none).
   * beforeOpen(entered) runs just before the first projection of an open (for example to take over a draft the page prepared);
   * entered is true for the first open after the page went to the tab's view, false when the screen comes back by revisit().
   * afterOpen() runs once the screen has opened and covers the page (for example to empty page fields beforeOpen took over, so
   * an open that fails leaves them as they were).
   * blocked() returns true while the tab's own page must stay visible on its view (a sheet, a sub-page the screen does not
   * draw); the screen closes at its next refresh (send()) and does not open. When the page changes in a way that may unblock
   * it, the tab calls revisit() on the returned object, which opens the screen again if it is wanted; the returned blocked()
   * tells which of the two applies, and active() whether the screen is open over the page now.
   * Every tab steps aside at once when the page puts something over it that blocked() counts (a sheet or other top overlay, the
   * launch update prompt, a noscroll body, and whatever the tab's own cfg.blocked adds, such as Library's and Workout plan's
   * level-up): the kit watches the body's children and classes. watch(observe), for a tab whose screen comes back by
   * itself once the page is clear again, makes the same watcher also call revisit(), and names with observe(node, options)
   * the tab's own nodes to watch (a form shown, a list redrawn); follow() on the returned object runs the watcher by hand
   * (once the focus has left a field).
   * Optional, for a tab whose screen covers only some of what its view shows (Life Director):
   * warm: true asks the native side what it can draw (the handshake) as soon as the tab is enabled, not first inside open(), and
   * calls revisit() when it answers; has(capability) then tells, from the cached answer, whether the screen draws a part (false
   * until the answer). Also with warm, a command whose renders leave the view on something blocked() counts steps the screen
   * aside as soon as its reply has been sent, instead of waiting for the next refresh.
   * arrive() runs when the page goes to the tab's view from somewhere else, just before the page shows it, while the screen is
   * not open (for example to put the tab on today).
   * Optional, for a view with two native drawings (the Overview's cards and its live shell, ADR 0012):
   * managed: true leaves the hand-over registration (NativeTabs.cover/register) and the open on arrival to the view's owner, which
   * asks one drawing at a time; the kit still cancels a pending close and marks the arrival, closes the screen when the page
   * leaves the view, and keeps its watcher, refresh and revisit(). The returned active(), opening() and coverable() are what the
   * owner registers.
   * afterClose() runs after every close, the kit's own and the native side's (its closed event).
   * onFail(reason) runs, after the kit has closed, when the screen cannot draw: 'capability' (the handshake lacks cfg.capability),
   * 'open' (an open threw or was rejected) or 'update' (a refresh was rejected).
   * notices: true (or {quiet(id)}) counts the page's toasts and adds notice {id, text} to every projection while the toast shows,
   * for the screen to draw, since it hides the page's; quiet(id) leaves out a toast that is about the page (the returned toasts()
   * is the latest toast's number).
   */
  function create(cfg) {
    const session = cfg.id + "-web-" + Date.now() + "-" + Math.random().toString(36).slice(2);
    let sequence = 0, active = false, opening = false, handshake = null, asked = null, plugin = null, ready = false, timer = 0, queued = 0;
    let generation = 0; // raised by every close, so an open still in flight knows it was cancelled
    let entered = true; // the page went to this view and no open has projected since
    const fail = (reason) => { try { cfg.onFail?.(reason); } catch (e) { console.warn(cfg.id + " onFail", e); } };
    const afterClose = () => { try { cfg.afterClose?.(); } catch (e) { console.warn(cfg.id + " afterClose", e); } };
    /* the page's toasts (cfg.notices): each toast() writes the toast's text, which is how they are counted; one that comes while
       the screen shows is sent at once */
    const toastEl = cfg.notices ? document.getElementById("toast") : null;
    let toasts = 0;
    const toastWatch = toastEl ? new MutationObserver((records) => { toasts += records.length; schedule(); }) : null;
    if (toastWatch) toastWatch.observe(toastEl, { childList: true });
    /** the number of the page's latest toast, counting those of a handler that has just run, which the observer hears of later */
    function latestToast() {
      if (toastWatch) toasts += toastWatch.takeRecords().length;
      return toasts;
    }
    /** the page's message while its toast shows */
    function notice() {
      const id = latestToast(), quiet = cfg.notices && cfg.notices.quiet;
      return toastEl.classList.contains("show") && !(quiet && quiet(id)) ? { id, text: toastEl.textContent } : null;
    }

    function available() {
      try {
        return Capacitor.isNativePlatform() && Capacitor.isPluginAvailable(cfg.plugin) ? Capacitor.Plugins[cfg.plugin] : null;
      } catch (_) {
        return null;
      }
    }
    /** what the owner chose: an unset switch is off, or on for a tab that has become the default (cfg.defaultOn) */
    function wanted() {
      const on = lmaLoad()[cfg.setting];
      return cfg.defaultOn ? on !== false : on === true;
    }
    function enabled() {
      return !!available() && wanted();
    }
    /** the native side's answer to the handshake, asked once; open() awaits the same question. A failed answer is forgotten,
        so the next open asks again. */
    function ask() {
      if (!asked) {
        const p = available();
        if (!p) return Promise.reject(Error("plugin unavailable"));
        plugin = p;
        asked = p.handshake().then((h) => (handshake = h), (e) => { asked = null; throw e; });
      }
      return asked;
    }
    /** with cfg.warm: ask as soon as the tab is enabled, and look again at the page once the answer is in */
    function warm() {
      if (cfg.warm && !asked && enabled()) ask().then(() => revisit(), () => {});
    }
    /** whether the screen says it draws this capability: false until the handshake has answered */
    function has(capability) {
      return !!handshake && handshake.protocol === 1 && handshake.capabilities.includes(capability);
    }
    /** the page would let the screen cover the tab's view, wherever the page is now: nothing over it, nothing the screen cannot draw */
    function coverable() {
      return !!state.onboardingComplete && !lmaTopOverlay() && !document.body.classList.contains("noscroll") && !(cfg.blocked && cfg.blocked());
    }
    function blocked() {
      return lmaView() !== cfg.id || !coverable();
    }
    function snapshot() {
      return Object.assign({ protocol: 1, webSessionId: session, sequence: ++sequence }, cfg.projection(), cfg.notices && { notice: notice() }, { tokens: tokens(), light: light() });
    }
    async function navScene() {
      if (!window.NativeOverviewSnapshot) throw Error("Overview chrome unavailable");
      return NativeOverviewSnapshot.snapshot();
    }
    function sync() {
      const input = document.getElementById(cfg.toggle);
      if (input) {
        input.disabled = !available();
        input.checked = enabled();
      }
    }
    function schedule() {
      clearTimeout(queued);
      queued = setTimeout(send, 100);
    }
    /** the fresh projection (and a command's reply) for the open screen; nothing if it closed while the bars were captured,
        since a hidden screen takes no updates (A24), or if the page has left the tab's view meanwhile: a screen on its way out
        never takes the shared bars with a newer scene from the screen that replaces it. A reply goes with it, so a reply
        reaches only a showing screen (NATIVE_TAB_GUIDE.md). */
    async function update(reply) {
      const data = snapshot(), mine = generation, scene = await navScene();
      if (active && mine === generation && lmaView() === cfg.id) await plugin.update(Object.assign({ snapshot: data, navScene: scene }, reply && { reply }));
    }
    async function send() {
      if (!active) return;
      if (blocked()) {
        // on its own view the page itself has to show (a sheet, an overlay): no native tab takes over, so close now
        NativeTabs.leave(cfg.id, lmaView() === cfg.id ? "" : lmaView(), () => close());
        return;
      }
      try {
        await update();
      } catch (e) {
        console.warn(cfg.id + " refresh", e);
        await close();
        fail("update");
      }
    }
    /** A command from the screen: run it on the page, then send the fresh projection with the reply. Never closes the screen. */
    let commands = Promise.resolve();
    function runCommand(e) {
      // one at a time and in order, so a save never overtakes the preview before it
      commands = commands.then(async () => {
        const handler = cfg.commands && cfg.commands[e && e.command];
        let reply;
        try {
          reply = handler ? (await handler((e && e.payload) || {}, { requestId: (e && e.requestId) || "" })) || {} : { ok: false, message: "Unknown command" };
        } catch (err) {
          console.warn(cfg.id + " command " + (e && e.command), err);
          reply = { ok: false, message: "Something went wrong. Nothing was changed." };
        }
        // reply.after: page work that must wait until the reply has gone to the screen (an XP celebration the screen steps aside
        // for would otherwise close it before its answer arrived); it is not part of the reply
        const after = typeof reply.after === "function" ? reply.after : null;
        reply = Object.assign({ command: e && e.command, requestId: (e && e.requestId) || "" }, reply);
        delete reply.after;
        if (active) {
          clearTimeout(queued); // a refresh the command's renders asked for: the projection sent with the reply carries them
          try {
            await update(reply);
          } catch (err) {
            console.warn(cfg.id + " reply", err);
          }
          if (cfg.warm && active && lmaView() === cfg.id && blocked()) send(); // the command left the page on what the screen cannot draw
        }
        if (reply.navigate) lmaNavTo(reply.navigate, { fast: true }); // no web transition under a native screen
        if (after) after();
        return reply;
      });
      return commands;
    }
    async function close() {
      generation++;
      clearInterval(timer);
      clearTimeout(queued);
      active = false;
      if (plugin) await plugin.close();
      afterClose();
    }
    async function open(force) {
      plugin = available();
      if (!plugin || active || opening || blocked() || (!force && !wanted())) {
        if (!opening) NativeTabs.flush(cfg.id);
        return false;
      }
      opening = true;
      const mine = generation;
      const openedAt = now();
      try {
        if (!ready) {
          await plugin.addListener("action", async (e) => {
            if (e.view) lmaNavTo(e.view, { fast: true }); // no web transition under a native screen
            else {
              await close();
              await cfg.onAction?.(e);
            }
          });
          await plugin.addListener("command", (e) => runCommand(e));
          await plugin.addListener("refresh", send);
          await plugin.addListener("nav-action", (e) => window.NativeOverviewSnapshot?.activate(e.id));
          await plugin.addListener("swipe", (e) => window.NativeSwipe?.request(cfg.id, plugin, e)); // a committed swipe: the page decides
          await plugin.addListener("closed", () => {
            active = false;
            clearInterval(timer);
            afterClose();
          });
          ready = true;
        }
        const h = handshake || (await ask());
        const handshakeAt = now();
        if (h.protocol !== 1 || !h.capabilities.includes(cfg.capability)) {
          fail("capability");
          return false;
        }
        if (blocked()) return false;
        if (cfg.beforeOpen) cfg.beforeOpen(entered);
        entered = false;
        const data = snapshot();
        const projectionAt = now();
        const scene = await navScene();
        const chromeAt = now();
        await plugin.open({ snapshot: data, navScene: scene });
        const nativeAt = now();
        window.__nativeTabPerf = { feature: cfg.id, handshakeMs: handshakeAt - openedAt, projectionMs: projectionAt - handshakeAt, chromeMs: chromeAt - projectionAt, nativeMs: nativeAt - chromeAt, totalMs: nativeAt - openedAt };
        if (mine !== generation) {
          // closed while this open was in flight: the plugin runs that close right after this open, so if the tab is still
          // wanted it has to be opened again behind it, or the screen ends up hidden
          opening = false;
          return blocked() ? false : open(force);
        }
        if (blocked()) {
          await close();
          return false;
        }
        active = true;
        timer = setInterval(send, 15000);
        if (cfg.afterOpen) cfg.afterOpen();
        NativeTabs.flush(cfg.id);
        setTimeout(schedule, 160); // settled bar, in case a scene from the previous tab got there late
        return true;
      } catch (e) {
        console.warn("Native " + cfg.id + " unavailable", e);
        await close();
        fail("open");
        return false;
      } finally {
        opening = false;
        NativeTabs.flush(cfg.id);
      }
    }

    /** the page may let the screen cover it again (a sheet closed, the drawn sub-page came back): open if it is wanted */
    function revisit() {
      if (!active && !opening && enabled() && !blocked()) open(false);
    }
    /** the page covered the view (step aside now, not at the next refresh) or, for a tab with cfg.watch, gave it back */
    function follow() {
      if (active && blocked()) send();
      else if (cfg.watch && !active) revisit();
    }
    const watcher = new MutationObserver(follow);
    watcher.observe(document.body, { childList: true, attributes: true, attributeFilter: ["class"] });
    if (cfg.watch) cfg.watch((node, options) => watcher.observe(node, options));

    if (!cfg.managed) {
      NativeTabs.cover(cfg.id, enabled);
      NativeTabs.register(cfg.id, { isOpen: () => active, isOpening: () => opening, open: () => open(false), close, wouldOpen: () => enabled() && coverable() });
    }
    const nav = navigate;
    navigate = function (v) {
      if (v !== cfg.id && (active || opening)) NativeTabs.leave(cfg.id, v, () => close());
      // an arrival from another view, before the page shows it, so it shows what arrive set (choosing the view it is already on
      // is no arrival: nothing would draw the reset)
      if (v === cfg.id && lmaView() !== cfg.id && !active && cfg.arrive) cfg.arrive();
      const r = nav.apply(this, arguments);
      if (v === cfg.id) {
        if (!active) entered = true; // choosing the tab again while its screen shows is not a new arrival
        NativeTabs.cancel(cfg.id);
        if (!cfg.managed) queueMicrotask(() => open(false)); // a managed tab is opened by the view's owner, which asks one drawing
      }
      if (v === "settings") sync();
      return r;
    };
    if (cfg.render) {
      const render = globalThis[cfg.render];
      globalThis[cfg.render] = function () {
        const r = render.apply(this, arguments);
        if (active) schedule();
        return r;
      };
    }
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "visible" && active) send();
    });
    window[cfg.globals.open] = () => open(true);
    window[cfg.globals.enable] = async (on) => {
      lmaLoad()[cfg.setting] = !!on;
      lmaSave();
      sync();
      if (!on) await close();
      else warm();
    };
    window[cfg.globals.projection] = { snapshot };
    sync();
    setTimeout(sync, 700);
    setTimeout(warm, 0); // after the page has finished loading, so the plugin and the owner's switch are known
    return { open, close, send, schedule, sync, enabled, blocked, snapshot, runCommand, revisit, follow, has, coverable, toasts: latestToast,
             active: () => active, opening: () => opening };
  }

  window.NativeTabKit = { create, color, tokens, light, celebrationTarget, celebrations };
})();
