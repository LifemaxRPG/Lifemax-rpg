/* NativeTabKit: the web half of a native tab. A tab supplies what is specific to it (its projection and what its actions do);
   the kit owns the rest: session and sequence numbers, open/update/close, the hand-over between native tabs (NativeTabs),
   refresh on render, resume and a timer, the settings toggle and the public entry points. The native half is ComposeTabPlugin.
   Contract: docs/architecture/CONTRACT.md (A03, A07, A23) and docs/architecture/NATIVE_TAB_GUIDE.md. */
(function () {
  const NativeTabs = (typeof window !== "undefined" && window.NativeTabs) || { cover() {}, cancel() {}, leave(id, to, fn) { fn(); }, flush() {}, register() {}, heal() {} };
  const now = () => typeof performance !== "undefined" ? performance.now() : Date.now();
  const TOKENS = ["bg", "panel", "panel2", "text", "muted", "accent", "line", "good"];

  /** a CSS colour as opaque ARGB, which is what the native theme takes */
  function color(v) {
    const c = document.createElement("canvas");
    c.width = c.height = 1;
    const x = c.getContext("2d");
    x.fillStyle = v;
    x.fillRect(0, 0, 1, 1);
    const a = x.getImageData(0, 0, 1, 1).data;
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
      return lmaView() !== cfg.id || !state.onboardingComplete || !!lmaTopOverlay() || document.body.classList.contains("noscroll");
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
        NativeTabs.leave(cfg.id, lmaView(), () => close());
        return;
      }
      try {
        await plugin.update({ snapshot: snapshot(), navScene: await navScene() });
      } catch (e) {
        console.warn(cfg.id + " refresh", e);
        await close();
      }
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
    return { open, close, send, schedule, sync, enabled, snapshot };
  }

  window.NativeTabKit = { create, color, tokens, light };
})();
