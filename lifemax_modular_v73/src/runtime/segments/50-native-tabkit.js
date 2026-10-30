/* NativeTabKit: the web half of a native tab. A tab supplies what is specific to it (its projection and what its actions do);
   the kit owns the rest: session and sequence numbers, open/update/close, the hand-over between native tabs (NativeTabs),
   refresh on render, resume and a timer, the settings toggle and the public entry points. The native half is ComposeTabPlugin.
   Contract: docs/architecture/CONTRACT.md (A03, A07, A23) and docs/architecture/NATIVE_TAB_GUIDE.md. */
(function () {
  const NativeTabs = (typeof window !== "undefined" && window.NativeTabs) || { cover() {}, cancel() {}, leave(id, to, fn) { fn(); }, flush() {}, register() {}, has() { return false; }, heal() {} };
  const now = () => typeof performance !== "undefined" ? performance.now() : Date.now();
  const TOKENS = ["bg", "bg-elev", "panel", "panel2", "panel3", "text", "text-dim", "muted", "muted2", "accent", "accent-dim", "accent-strong", "line", "line-soft", "good", "gold", "bad"];

  const probe = document.createElement("canvas").getContext("2d", { willReadFrequently: true }); // read on the CPU, no GPU round trip

  /** a CSS colour as ARGB, which is what the native theme takes (translucent only where the colour is, as accent-dim) */
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

  /**
   * cfg: id (the page view, e.g. "character"), plugin (Capacitor plugin name), capability, setting (key in lmaLoad()),
   * toggle (settings checkbox id), projection() (the tab's own fields), onAction(event) (what a native action does on the page),
   * render (name of the page's render function to follow while open), globals {open, enable, projection}.
   * Optional: commands {name: async (payload) => reply} run on the page WITHOUT closing the screen (a draft preview, a save);
   * the reply ({ok, message, ...}) goes back with the next update, and reply.navigate, if set, is followed afterwards.
   * beforeOpen() runs just before the first projection of an open (for example to take over a draft the page prepared).
   * blocked() returns true while the tab's own page must stay visible on its view (a sheet, a sub-page the screen does not
   * draw); the screen closes at its next refresh (send()) and does not open. When the page changes in a way that may unblock
   * it, the tab calls revisit() on the returned object, which opens the screen again if it is wanted; the returned blocked()
   * tells which of the two applies.
   */
  function create(cfg) {
    const session = cfg.id + "-web-" + Date.now() + "-" + Math.random().toString(36).slice(2);
    let sequence = 0, active = false, opening = false, handshake = null, plugin = null, ready = false, timer = 0, queued = 0;
    let generation = 0; // raised by every close, so an open still in flight knows it was cancelled

    function available() {
      try {
        return Capacitor.isNativePlatform() && Capacitor.isPluginAvailable(cfg.plugin) ? Capacitor.Plugins[cfg.plugin] : null;
      } catch (_) {
        return null;
      }
    }
    function enabled() {
      return !!available() && lmaLoad()[cfg.setting] === true;
    }
    function blocked() {
      return lmaView() !== cfg.id || !state.onboardingComplete || !!lmaTopOverlay() || document.body.classList.contains("noscroll") || !!(cfg.blocked && cfg.blocked());
    }
    function snapshot() {
      return Object.assign({ protocol: 1, webSessionId: session, sequence: ++sequence }, cfg.projection(), { tokens: tokens(), light: light() });
    }
    async function navScene() {
      if (!window.NativeOverviewSnapshot) throw Error("Overview chrome unavailable");
      return NativeOverviewSnapshot.snapshot();
    }
    function sync() {
      const input = document.getElementById(cfg.toggle);
      if (input) {
        input.disabled = !available();
        input.checked = !!available() && lmaLoad()[cfg.setting] === true;
      }
    }
    function schedule() {
      clearTimeout(queued);
      queued = setTimeout(send, 100);
    }
    async function send() {
      if (!active) return;
      if (blocked()) {
        // on its own view the page itself has to show (a sheet, an overlay): no native tab takes over, so close now
        NativeTabs.leave(cfg.id, lmaView() === cfg.id ? "" : lmaView(), () => close());
        return;
      }
      try {
        await plugin.update({ snapshot: snapshot(), navScene: await navScene() });
      } catch (e) {
        console.warn(cfg.id + " refresh", e);
        await close();
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
          reply = handler ? (await handler((e && e.payload) || {})) || {} : { ok: false, message: "Unknown command" };
        } catch (err) {
          console.warn(cfg.id + " command " + (e && e.command), err);
          reply = { ok: false, message: "Something went wrong. Nothing was changed." };
        }
        reply = Object.assign({ command: e && e.command, requestId: (e && e.requestId) || "" }, reply);
        if (active) {
          try {
            await plugin.update({ snapshot: snapshot(), navScene: await navScene(), reply });
          } catch (err) {
            console.warn(cfg.id + " reply", err);
          }
        }
        if (reply.navigate) lmaNavTo(reply.navigate, { fast: true }); // no web transition under a native screen
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
    }
    async function open(force) {
      plugin = available();
      if (!plugin || active || opening || blocked() || (!force && lmaLoad()[cfg.setting] !== true)) {
        if (!opening) NativeTabs.flush(cfg.id);
        return false;
      }
      opening = true;
      const mine = generation;
      const openedAt = now();
      try {
        if (!ready) {
          await plugin.addListener("action", async (e) => {
            await close();
            if (e.view) lmaNavTo(e.view);
            else await cfg.onAction(e);
          });
          await plugin.addListener("command", (e) => runCommand(e));
          await plugin.addListener("refresh", send);
          await plugin.addListener("nav-action", (e) => window.NativeOverviewSnapshot?.activate(e.id));
          await plugin.addListener("closed", () => {
            active = false;
            clearInterval(timer);
          });
          ready = true;
        }
        const h = handshake || (handshake = await plugin.handshake());
        const handshakeAt = now();
        if (h.protocol !== 1 || !h.capabilities.includes(cfg.capability)) return false;
        if (blocked()) return false;
        if (cfg.beforeOpen) cfg.beforeOpen();
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
        NativeTabs.flush(cfg.id);
        setTimeout(schedule, 160); // settled bar, in case a scene from the previous tab got there late
        return true;
      } catch (e) {
        console.warn("Native " + cfg.id + " unavailable", e);
        await close();
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

    NativeTabs.cover(cfg.id, enabled);
    NativeTabs.register(cfg.id, { isOpen: () => active, isOpening: () => opening, open: () => open(false), close });
    const nav = navigate;
    navigate = function (v) {
      if (v !== cfg.id && (active || opening)) NativeTabs.leave(cfg.id, v, () => close());
      const r = nav.apply(this, arguments);
      if (v === cfg.id) {
        NativeTabs.cancel(cfg.id);
        queueMicrotask(() => open(false));
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
    };
    window[cfg.globals.projection] = { snapshot };
    sync();
    setTimeout(sync, 700);
    return { open, close, send, schedule, sync, enabled, blocked, snapshot, runCommand, revisit };
  }

  window.NativeTabKit = { create, color, tokens, light };
})();
