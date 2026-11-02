/* The native audiobook dock (shell.audio.v1; plans/JOURNAL_AND_LIBRARY_SHEETS.md phase 4). The page stays the player: it owns the
   <audio> element, the file in IndexedDB and the saved position. The dock is one native view owned by the shell, above the bars of
   whichever native screen shows, so a loaded book keeps its controls on every native tab and on the Overview. This file pushes the
   player's state to it (NativeAudio.state) and runs its commands on the page's own functions; nothing is decided here.
   The dock draws only while a native screen shows; on a view the page draws, the page's own player stays. Native tells which
   (a `dock` event), and the page hides its player and pads its body while the dock shows.
   A native screen over the Library (Media, Journal) steps aside for a loaded book only while no dock carries it: ready(). */
(function () {
  const CAPABILITY = "shell.audio.v1";
  const SETTING = "nativeAudio";
  const DRIFT_MS = 10000; // a correction for the dock's own clock while the book plays
  const WORDS = { refused: "That is not an audio file" }; // the page's words for a file the picker let through that the page's list does not take
  let plugin = null, handshake = null, asked = null, ready = false, queued = 0, timer = 0, announced = "";

  const available = () => {
    try {
      return Capacitor.isNativePlatform() && Capacitor.isPluginAvailable("NativeAudio") ? Capacitor.Plugins.NativeAudio : null;
    } catch (_) {
      return null;
    }
  };
  const wanted = () => lmaLoad()[SETTING] === true;
  const enabled = () => !!available() && wanted();
  /** whether a dock is carrying the player: the owner's switch is on and the native side says it draws one */
  const carrying = () => enabled() && !!handshake && handshake.protocol === 1 && handshake.capabilities.includes(CAPABILITY);

  /* ---------- what the dock is told ---------- */
  const el = () => document.getElementById("audioEl");
  const bar = () => document.getElementById("audioPlayer");
  const finite = (n) => (Number.isFinite(n) && n > 0 ? n : 0);
  /** the speeds the page offers, in its own order */
  const playing = () => !bar().classList.contains("show") ? false : !!el().src && !el().paused && !el().ended;
  function rates() {
    return [...document.getElementById("audioRate").options].map((o) => Number(o.value));
  }
  /**
   * The player as the page has it. A file still loading counts as showing (the dock does not flicker away and back while a book
   * is swapped), and says so. position and duration are seconds; the dock extrapolates between pushes.
   */
  function stateOf() {
    const a = el(), shown = bar().classList.contains("show"), loading = !!audioCtx.loading, on = shown || loading;
    const item = loading && !shown ? mediaById(audioCtx.loading) : null;
    return {
      v: 1,
      visible: on,
      minimized: shown && bar().classList.contains("minimized"),
      loading,
      mediaId: String(audioCtx.mediaId || audioCtx.loading || ""),
      title: item ? String(item.title || "Audiobook") : document.getElementById("audioPlayerTitle").textContent,
      sub: item ? "Loading…" : document.getElementById("audioPlayerSub").textContent,
      playing: playing(),
      position: shown ? finite(a.currentTime) : 0,
      duration: shown ? finite(a.duration) : 0,
      rate: Number(a.playbackRate) || 1,
      rates: rates(),
      tokens: on ? NativeTabKit.tokens() : {},
      light: NativeTabKit.light(),
    };
  }
  function push() {
    clearTimeout(queued);
    queued = 0;
    if (!plugin || !carrying()) return;
    const s = stateOf();
    // a dock that is not showing needs nothing but to be told once
    const key = s.visible ? "" : "off";
    if (!s.visible && announced === key) return;
    announced = key;
    plugin.state(s).catch((e) => console.warn("NativeAudio state", e));
  }
  /** after the burst of events one action makes (play, seeked, ratechange…) */
  function schedule() {
    if (!queued) queued = setTimeout(push, 0);
  }
  function tick() {
    clearInterval(timer);
    timer = 0;
    if (carrying() && playing()) timer = setInterval(push, DRIFT_MS);
  }

  /* ---------- what the dock may ask for: the page's own functions ---------- */
  const COMMANDS = {
    toggle: () => audioTogglePlay(),
    skip: (p) => {
      const sec = Number(p.sec);
      if (Number.isFinite(sec) && Math.abs(sec) <= 3600) audioSkip(sec);
    },
    seek: (p) => {
      const frac = Number(p.frac);
      if (Number.isFinite(frac)) audioSeekChange(Math.min(1, Math.max(0, frac)) * 1000);
    },
    rate: (p) => {
      const select = document.getElementById("audioRate"), v = String(p.value);
      if (![...select.options].some((o) => o.value === v)) return;
      select.value = v;
      audioSetRate(v);
    },
    minimize: () => toggleMinimizeAudioPlayer(),
    close: () => closeAudioPlayer(true),
  };
  function run(e) {
    const f = carrying() && Object.prototype.hasOwnProperty.call(COMMANDS, e && e.command) ? COMMANDS[e.command] : null;
    if (!f) return;
    try {
      f((e && e.payload) || {});
    } catch (err) {
      console.warn("NativeAudio command " + e.command, err);
    }
    push();
  }

  /* ---------- the dock showing or not ---------- */
  /** native: the dock is on screen with this height (dp = CSS px), or gone; the page's own player gives way and its body is padded */
  function docked(e) {
    const root = document.documentElement, on = !!(e && e.shown);
    root.classList.toggle("audio-docked", on);
    root.style.setProperty("--audio-dock-h", on ? Math.max(0, Number(e.height) || 0) + "px" : "0px");
  }

  /* ---------- wiring ---------- */
  async function listen() {
    if (ready) return;
    await plugin.addListener("command", run);
    await plugin.addListener("dock", docked);
    ready = true;
    const a = el();
    for (const name of ["play", "pause", "seeked", "ratechange", "loadedmetadata", "durationchange", "ended", "emptied"]) a.addEventListener(name, () => (schedule(), tick()));
    new MutationObserver(schedule).observe(bar(), { attributes: true, attributeFilter: ["class"] });
    window.addEventListener("lma-audio", schedule);
    document.addEventListener("visibilitychange", () => document.visibilityState === "visible" && push());
  }
  /** the native side's answer, asked once; then it is told what the page lets it accept */
  async function warm() {
    plugin = available();
    if (!plugin || !wanted()) return;
    try {
      asked = asked || plugin.handshake().then((h) => (handshake = h), (e) => ((asked = null), Promise.reject(e)));
      await asked;
      await listen();
      await plugin.configure({ enabled: carrying(), accept: document.getElementById("audioFileInput").accept, refuse: WORDS.refused });
      push();
      tick();
      window.NativeTabs?.heal(); // a native screen that waited for the book to leave may open now
    } catch (e) {
      console.warn("NativeAudio unavailable", e);
    }
  }
  window.setNativeAudioEnabled = async (on) => {
    lmaLoad()[SETTING] = !!on;
    lmaSave();
    sync();
    if (on) return warm();
    announced = "";
    clearInterval(timer);
    docked(null);
    if (plugin) await plugin.configure({ enabled: false, accept: "", refuse: "" }).catch(() => {});
    window.NativeTabs?.heal();
  };
  function sync() {
    const input = document.getElementById("nativeAudioToggle");
    if (input) {
      input.disabled = !available();
      input.checked = enabled();
    }
  }
  document.addEventListener("DOMContentLoaded", sync);
  sync();
  setTimeout(sync, 700);
  setTimeout(warm, 0);

  /** a file the owner picked natively, read by the page: fetch → File → the page's own attachAudioFile (take-back and blob restore) */
  let reading = false;
  async function attachPicked(p) {
    const id = String(p.id || ""), item = mediaById(id);
    if (!item) return { ok: false, message: "That item is gone." };
    if (reading) return { ok: false, message: "Another file is being read." };
    if (Number(p.size) > AUDIO_MAX_BYTES) {
      toast("File too large (max ~512 MB)");
      return { ok: false, message: "File too large (max ~512 MB)" };
    }
    reading = true;
    // the reply goes at once; the job reports through the page's toasts, which the screen shows as notices
    (async () => {
      try {
        toast("Reading the file…");
        const r = await fetch(String(p.url));
        if (!r.ok) throw Error("HTTP " + r.status);
        const blob = await r.blob();
        await attachAudioFile(id, new File([blob], String(p.name || "audio"), { type: String(p.type || blob.type || "") }));
      } catch (err) {
        console.warn("NativeAudio attach", err);
        toast("Could not read that file");
      } finally {
        reading = false;
      }
    })();
    return { ok: true, started: true };
  }

  window.NativeAudioDock = {
    /** whether a dock carries the player, so a native screen over the Library need not step aside for a loaded book */
    ready: carrying,
    stateOf,
    attachPicked,
    refused: (name) => toast(WORDS.refused + (name ? ": " + name : "")),
  };
})();
