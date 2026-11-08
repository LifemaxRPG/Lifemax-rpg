/* Native economy check (beta), the transport (docs/specs/ECONOMY_SHADOW_PLAN.md 2.3-2.5, 3.3, 4; WP6a). The page stays the
   only writer and the only thing the owner sees. While the switch (nativeShadow, Settings, Data & advanced) is on, the app says
   economy.shadow.v1 and its rules are the page's (the content fingerprint), each outermost economy action (the points of
   50-native-shadow-points.js) is sent to NativeShadow as one envelope: the shadow keys' delta since the last envelope, the
   delta the action made, its return value, toasts, save answers and env. Native keeps a copy in memory only, records which
   fields each action touched and, from WP6b, runs the economy actions itself and compares; nothing comes back to the page but
   counts (the Settings row: actions checked, how many differed and the last one's day and kind).
   A wrapper is transparent by construction: the original runs exactly once with its own this, arguments, return value and
   exception; every step of the shadow is caught; envelopes are posted with setTimeout(0) after the action and not awaited.
   Switch off, no plugin, no capability, a demo, a fingerprint that differs: no wrapper is installed at all. Three faults, or
   five actions in a row over the time budget, turn the page side off until the page loads again. */
(function () {
  const CAPABILITY = "economy.shadow.v1", SETTING = "nativeShadow", PROTOCOL = 1, SCOPE = "economy";
  /* an app that compares the quest and Daily Hand kinds (WP6c): every kind carries today's plan facts (its save may deal and
     check the hand), and the journal coach's own plain writes are told apart from the economy's */
  const CAPABILITY_QUESTS = "economy.shadow.quests.v1";
  /* an app that compares the Health sync and the day roll's decay (WP6d): every Health Connect answer is recorded with its
     outcome (no plugin, a sync already running, the read that threw and its message), and the boot's decay carries its plan */
  const CAPABILITY_DAY = "economy.shadow.day.v1";
  /* the Daily Hand's redraw-time points that changed nothing are sent one in this many, and otherwise counted (WP6c) */
  const NOOP_KINDS = ["hand.ensure", "hand.verify"], NOOP_EVERY = 50;
  const B = LMA_SHADOW_BOOT, P = LMA_SHADOW_POINTS;
  const LIMIT = { envelope: 256 * 1024, args: 16 * 1024, env: 64 * 1024, toasts: 20, toast: 300, saves: 8, rec: 256, part: 256 * 1024, parts: 32, base: 8 * 1024 * 1024, queue: 16, faults: 3, slowMs: 50, slowRun: 5, slice: 200000 };
  const WORDS = {
    help: "Your phone also works out every XP and Sparks change the app makes, without saving or showing it, and counts any difference. Nothing leaves the phone.",
    stale: "Paused: waiting for the matching app update",
    disabled: "Stopped after errors. Turn it off and on to try again.",
    tooLarge: "Your save is too large for the check",
    paused: "Paused while Battery Saver is on",
    watching: (n, since) => "Following " + n.toLocaleString("en-GB") + " actions since " + since + " (comparing comes in a later update)",
    checked: (n, since, different, last) => "Checked " + n.toLocaleString("en-GB") + " actions since " + since + " \u00b7 " + different.toLocaleString("en-GB") + " different" + (last ? " \u00b7 last " + last : ""),
  };
  const FAULTS_KEY = "lma_shadow_faults"; // sessionStorage: page faults of an earlier page in this WebView, told at the next configure
  let plugin = null, hello = null, asked = null, live = false, stopped = "", sid = "", epoch = 0, seq = 0, head = 0, cache = null, needBase = true;
  let depth = 0, replacing = 0, current = null, timer = 0, faults = 0, slowRun = 0, summary = null, quests = false, day = false, journal = 0, noops = 0;
  const pending = [], outbox = [], installed = [];
  const stats = { sent: 0, bases: 0, skipped: {}, faults: 0, answers: {}, costs: [] };

  const available = () => {
    try {
      return Capacitor.isNativePlatform() && Capacitor.isPluginAvailable("NativeShadow") ? lmaPlugin("NativeShadow") : null;
    } catch (_) {
      return null;
    }
  };
  const demo = () => !!(window.LM_DEMO && window.LM_DEMO.on);
  const wanted = () => lmaLoad()[SETTING] === true;
  const skip = (why) => (stats.skipped[why] = (stats.skipped[why] || 0) + 1);

  function fault() {
    faults++;
    stats.faults++;
    try { sessionStorage.setItem(FAULTS_KEY, String((Number(sessionStorage.getItem(FAULTS_KEY)) || 0) + 1)); } catch (_) { /* no session storage */ }
    if (faults >= LIMIT.faults) stop("faults");
  }

  /* ---------- the content fingerprint (2.4): the same SHA-256 tools/gen_quest_content.js writes as contentHash ---------- */
  async function sha(text) {
    const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
    return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, "0")).join("");
  }
  async function fingerprint() {
    const clone = (v) => JSON.parse(JSON.stringify(v));
    const economyHash = await sha(JSON.stringify({ eco: clone(ECO), rules: clone(DEFAULT_RULES), durations: ECO_DURATION_RULES }));
    const classQuests = {};
    for (const id of Object.keys(CLASS_CONTENT)) classQuests[id] = clone(CLASS_CONTENT[id].quests);
    const body = {
      schemaVersion: 2, attributes: ATTRS.map((a) => a.id),
      hand: { freeRerolls: DH_FREE_REROLLS, rerollCost: DH_REROLL_COST, classAttrs: clone(DH_CLASS_ATTR), templates: clone(QUEST_LIBRARY) },
      defaultQuests: clone(defaultQuests), stageLevels: [0, 1, 2, 3, 4, 5].map(stageLvl),
      classes: CLASSES.map((c) => ({ id: c.id, ready: !!c.ready })), classQuests, economyHash,
    };
    return sha(JSON.stringify(body));
  }

  /* ---------- deltas (2.2): texts of the shadow keys, compared key by key and, for record keys, record by record ---------- */
  function deltaText(from, to) {
    const set = [], del = [], rec = [];
    for (const k of B.KEYS) {
      const a = from ? from[k] : undefined, b = to[k];
      if (b === undefined) { if (a !== undefined) del.push(k); continue; }
      const key = JSON.stringify(k);
      if (typeof b === "object") {
        const items = b.items;
        if (!a || typeof a !== "object") { set.push(key + ":[" + items.join(",") + "]"); continue; }
        const changed = [];
        for (let i = 0; i < items.length; i++) if (a.items[i] !== items[i]) changed.push(i);
        if (changed.length > LIMIT.rec) set.push(key + ":[" + items.join(",") + "]");
        else if (changed.length || a.items.length !== items.length) rec.push(key + ':{"len":' + items.length + ',"at":[' + changed.map((i) => "[" + i + "," + items[i] + "]").join(",") + "]}");
      } else if (a !== b) set.push(key + ":" + b);
    }
    return '{"set":{' + set.join(",") + '},"del":' + JSON.stringify(del) + ',"rec":{' + rec.join(",") + "}}";
  }
  function baseText(texts) {
    const parts = [];
    for (const k of B.KEYS) {
      const t = texts[k];
      if (t !== undefined) parts.push(JSON.stringify(k) + ":" + (typeof t === "object" ? "[" + t.items.join(",") + "]" : t));
    }
    return '{"keys":{' + parts.join(",") + "}}";
  }
  /* JSON normal form (AGENT_BRIEF 4): undefined is null; a value JSON cannot write is null */
  function normal(v) {
    try { const t = JSON.stringify(v === undefined ? null : v); return t === undefined ? "null" : t; } catch (_) { return "null"; }
  }

  /* ---------- the actions ---------- */
  function target() {
    return current || (pending.length === 1 ? pending[0] : null);
  }
  function begin(kind, args, reads) {
    const t = performance.now();
    for (const r of pending) r.flags.add("interleaved");
    const r = { kind, args: "[" + [...args].map(normal).join(",") + "]", t0: Date.now(), texts: B.capture(state), toasts: [], saves: [], journal: [], confirm: [], prompt: [], env: {}, flags: new Set(), cost: 0 };
    for (const read of reads) {
      if (read === "plan") r.env.plan = P.plan(kind);
      else if (read.indexOf("dom:") === 0) r.env.dom = P.dom(read.slice(4).split(","), args);
      else if (read === "health") r.health = P.healthStart(day);
    }
    if (quests && !r.env.plan) r.env.plan = P.plan(kind, true);
    if (r.health) {
      const real = window.lmaPlugin;
      r.unhook = () => { if (window.lmaPlugin === hooked) window.lmaPlugin = real; };
      const hooked = function (name) {
        const p = real.apply(this, arguments);
        if (name !== "HealthSync" || !r.health) return p;
        if (day) r.health.plugin = !!p;
        return p ? P.recordingHealth(p, r.health, day) : p;
      };
      window.lmaPlugin = hooked;
    }
    current = r;
    r.cost += performance.now() - t;
    return r;
  }
  function end(r, value, threw) {
    const t = performance.now();
    if (current === r) current = null;
    if (r.unhook) r.unhook();
    r.t1 = Math.max(Date.now(), r.t0);
    r.after = B.capture(state);
    r.returns = threw ? '{"threw":true}' : normal(value);
    if (threw) r.flags.add("threw");
    if (saveReadOnly) r.flags.add("readOnly");
    r.cost += performance.now() - t;
    budget(r.cost);
    if (NOOP_KINDS.includes(r.kind) && unchanged(r) && noops++ % NOOP_EVERY !== 0) { skip("noop"); return; }
    if (outbox.length >= LIMIT.queue) { outbox.length = 0; cache = null; needBase = true; skip("queue"); }
    outbox.push(r);
    if (!timer) timer = setTimeout(flush, 0);
  }
  /* an action that changed no shadow key, wrote nothing and said nothing */
  function unchanged(r) {
    if (r.saves.length || r.journal.length || r.toasts.length || r.flags.has("threw")) return false;
    for (const k of B.KEYS) {
      const a = r.texts[k], b = r.after[k];
      if (typeof a === "object" && typeof b === "object") { if (a.items.length !== b.items.length || a.items.some((x, i) => x !== b.items[i])) return false; }
      else if (a !== b) return false;
    }
    return true;
  }
  function settle(r, promise) {
    if (current === r) current = null;
    pending.push(r);
    r.flags.add("nested-async");
    const done = (v, threw) => {
      const i = pending.indexOf(r);
      if (i >= 0) pending.splice(i, 1);
      try { end(r, v, threw); } catch (_) { fault(); }
    };
    promise.then((v) => done(v, false), () => done(undefined, true));
  }
  function budget(ms) {
    stats.costs.push(ms);
    if (stats.costs.length > 200) stats.costs.shift();
    slowRun = ms > LIMIT.slowMs ? slowRun + 1 : 0;
    if (slowRun >= LIMIT.slowRun) { fault(); stop("slow"); }
  }

  /** wraps one page function as a shadow point (plan 2.5): transparent whatever the shadow does */
  function point(kind, orig, reads) {
    const wrapped = function () {
      let r = null;
      // a bare save() while an async action waits and nothing else runs is that action's own (the Health sync saves in its
      // finally); any other point then is an interleaving one (begin flags the waiting action)
      const own = kind === "save.other" && pending.length > 0 && !current;
      try { if (live && !depth && !replacing && !own) r = begin(kind, arguments, reads); } catch (_) { current = null; fault(); }
      depth++;
      let out;
      try {
        out = orig.apply(this, arguments);
      } catch (e) {
        depth--;
        try { if (r) end(r, undefined, true); } catch (_) { fault(); }
        throw e;
      }
      depth--;
      try { if (r) (out && typeof out.then === "function" ? settle(r, out) : end(r, out, false)); } catch (_) { fault(); }
      return out;
    };
    wrapped.__lmaShadow = orig;
    return wrapped;
  }
  /** a whole-save replacement: nothing inside is an action; afterwards a new epoch and a new base */
  function replacement(orig) {
    const wrapped = function () {
      replacing++;
      let out;
      const after = () => { replacing = Math.max(0, replacing - 1); epoch++; cache = null; needBase = true; };
      try { out = orig.apply(this, arguments); } catch (e) { after(); throw e; }
      if (out && typeof out.then === "function") out.then(after, after); else after();
      return out;
    };
    wrapped.__lmaShadow = orig;
    return wrapped;
  }
  /** what the action saw: toasts, writeSave answers, confirm and prompt answers */
  function capturing(name, keep) {
    const orig = window[name];
    const wrapped = function () {
      const out = orig.apply(this, arguments);
      try { const r = target(); if (r) keep(r, out, arguments); } catch (_) { fault(); }
      return out;
    };
    wrapped.__lmaShadow = orig;
    return wrapped;
  }
  function install() {
    if (installed.length) return;
    const put = (owner, name, w) => { owner[name] = w; installed.push([owner, name, w]); };
    for (const [kind, ownerOf, name, reads] of P.points) {
      const owner = ownerOf();
      if (owner && typeof owner[name] === "function" && !owner[name].__lmaShadow) put(owner, name, point(kind, owner[name], reads));
    }
    for (const [ownerOf, name] of P.replaces) {
      const owner = ownerOf();
      if (owner && typeof owner[name] === "function" && !owner[name].__lmaShadow) put(owner, name, replacement(owner[name]));
    }
    put(window, "toast", capturing("toast", (r, _o, a) => r.toasts.push(String(a[0]))));
    /* a write is an action's own only inside it: a write from a timer while the Health sync waits (the check-in's) is not */
    put(window, "writeSave", capturing("writeSave", (r, ok) => { if (depth > 0) (journal ? r.journal : r.saves).push(!!ok); }));
    /* the journal coach's own plain write (its redraw's day memory, outside the economy) */
    if (quests && typeof window.jcWrite === "function") put(window, "jcWrite", (function (orig) {
      const wrapped = function () { journal++; try { return orig.apply(this, arguments); } finally { journal--; } };
      wrapped.__lmaShadow = orig;
      return wrapped;
    })(window.jcWrite));
    put(window, "confirm", capturing("confirm", (r, ok) => r.confirm.push(!!ok)));
    put(window, "prompt", capturing("prompt", (r, v) => r.prompt.push(v === null || v === undefined ? null : String(v))));
  }
  function uninstall() {
    while (installed.length) {
      const [owner, name, w] = installed.pop();
      if (owner[name] === w) owner[name] = w.__lmaShadow; // a later wrapper around ours keeps it, and ours then passes through
    }
  }

  /* ---------- sending (2.3, 2.4): in order, after the action, never awaited ---------- */
  function envelopeText(r) {
    const before = needBase ? '{"set":{},"del":[],"rec":{}}' : deltaText(cache, r.texts);
    const after = deltaText(r.texts, r.after);
    const env = {};
    if (r.saves.length) env.saves = r.saves.map((ok) => ({ ok }));
    if (r.journal.length) env.journal = r.journal.map((ok) => ({ ok }));
    if (r.confirm.length) env.confirm = r.confirm;
    if (r.prompt.length) env.prompt = r.prompt;
    Object.assign(env, r.env);
    if (r.health) env.health = r.health;
    const envText = JSON.stringify(env), zone = (() => { try { return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC"; } catch (_) { return "UTC"; } })();
    const locale = /^[A-Za-z0-9-]{1,35}$/.test(navigator.language || "") ? navigator.language : "und";
    const toasts = r.toasts;
    if (r.args.length > LIMIT.args || envText.length > LIMIT.env || toasts.length > LIMIT.toasts || toasts.some((t) => t.length > LIMIT.toast) || r.saves.length > LIMIT.saves) return null;
    return { before, text: (b, s) =>
      '{"v":' + PROTOCOL + ',"scope":"' + SCOPE + '","sid":' + JSON.stringify(sid) + ',"epoch":' + epoch + ',"seq":' + s + ',"base":' + b + ',"kind":' + JSON.stringify(r.kind) +
      ',"args":' + r.args + ',"t0":' + r.t0 + ',"t1":' + r.t1 + ',"zone":' + JSON.stringify(zone) + ',"offset":' + -new Date(r.t0).getTimezoneOffset() + ',"locale":' + JSON.stringify(locale) +
      ',"env":' + envText + ',"before":' + before + ',"after":' + after + ',"returns":' + r.returns + ',"toasts":' + JSON.stringify(toasts) +
      ',"cost":{"ms":' + Math.round(r.cost * 10) / 10 + '},"flags":' + JSON.stringify([...r.flags]) + "}" };
  }
  function call(method, text) {
    try { return plugin[method]({ text }).then((a) => answered(method, a), () => answered(method, null)); } catch (_) { fault(); return Promise.resolve(); }
  }
  function sendBase(texts) {
    const text = baseText(texts);
    if (text.length > LIMIT.base) { stop("tooLarge"); return false; }
    const parts = [];
    let size = LIMIT.slice;
    for (;;) {
      parts.length = 0;
      for (let i = 0; i < text.length; i += size) parts.push(text.slice(i, i + size));
      if (parts.length > LIMIT.parts) { stop("tooLarge"); return false; }
      const longest = Math.max(...parts.map((p) => JSON.stringify(p).length));
      if (longest + 200 <= LIMIT.part) break;
      size = Math.floor(size * 0.75);
    }
    const s = ++seq;
    parts.forEach((p, i) => call("base", JSON.stringify({ v: PROTOCOL, scope: SCOPE, sid, epoch, seq: s, part: i, parts: parts.length, text: p })));
    head = s;
    stats.bases++;
    needBase = false;
    return true;
  }
  function flush() {
    timer = 0;
    const list = outbox.splice(0);
    for (const r of list) {
      if (!live) return;
      try {
        if (needBase && !sendBase(r.texts)) return;
        const e = envelopeText(r);
        if (!e) { skip("size"); cache = null; needBase = true; continue; }
        const s = ++seq, text = e.text(head, s);
        if (text.length > LIMIT.envelope) { skip("size"); seq--; cache = null; needBase = true; continue; }
        head = s;
        cache = r.after;
        stats.sent++;
        call("observe", text);
      } catch (_) { fault(); cache = null; needBase = true; }
    }
  }
  function answered(method, a) {
    const word = a && (a.reason || (a.accepted === false || a.ok === false ? "refused" : "ok"));
    stats.answers[method + ":" + word] = (stats.answers[method + ":" + word] || 0) + 1;
    if (!a) return;
    if (method === "observe" && a.accepted === false) {
      if (a.resync) { cache = null; needBase = true; }
      if (a.reason === "paused") stop("paused");
      else if (["off", "disabled", "stale", "unsupported"].includes(a.reason)) stop(a.reason);
    }
    if (method === "base" && a.ok === false) {
      cache = null;
      needBase = true;
      if (a.state && a.state !== "on") stop(a.state);
    }
  }

  /* ---------- the boot's actions (05-shadow-boot.js): the stored save as the base, then boot.load and boot.decay ---------- */
  function bootActions() {
    if (!B.on || !B.loaded || B.fault) return;
    let stored = null;
    try { stored = B.stored ? JSON.parse(B.stored) : null; } catch (_) { stored = null; }
    const base = B.capture(stored && typeof stored === "object" ? stored : {});
    const rec = (kind, texts, after, times, saves, toasts, env) => ({ kind, args: "[]", t0: times[0], t1: times[1], texts, after, returns: "null", toasts, saves: saves.map((s) => s.ok), journal: [], confirm: [], prompt: [], env, flags: new Set(), cost: 0 });
    if (!sendBase(base)) return;
    cache = base;
    outbox.push(rec("boot.load", base, B.loaded, B.times.load, B.saves.load, [], {}));
    if (B.decayed) {
      /* WP6d: what the check-in wrote between them is the decay's before-state; the plan's rest days as far back as its walk */
      const plan = day && B.window ? P.plan("boot.decay", false, B.window) : null;
      outbox.push(rec("boot.decay", B.predecay || B.loaded, B.decayed, B.times.decay, B.saves.decay, day ? B.toasts.slice(0, LIMIT.toasts) : [], plan ? { plan } : {}));
    }
    B.stored = null; // the stored text and the boot states are not kept past the base
    B.loaded = B.predecay = B.decayed = null;
    flush();
  }

  /* ---------- the switch, the handshake and the Settings row ---------- */
  function stop(why) {
    live = false;
    stopped = why;
    uninstall();
    outbox.length = 0;
    pending.length = 0;
    cache = null;
    needBase = true;
    sync();
  }
  async function warm() {
    plugin = available();
    if (!plugin || !wanted() || demo()) return sync();
    try {
      asked = asked || plugin.handshake().then((h) => (hello = h), (e) => ((asked = null), Promise.reject(e)));
      await asked;
      if (!hello || hello.protocol !== PROTOCOL || !Array.isArray(hello.capabilities) || !hello.capabilities.includes(CAPABILITY)) return sync();
      quests = hello.capabilities.includes(CAPABILITY_QUESTS);
      day = hello.capabilities.includes(CAPABILITY_DAY);
      const mine = await fingerprint().catch(() => "");
      sid = sid || "p-" + Math.random().toString(36).slice(2, 10);
      let told = 0;
      try { told = Number(sessionStorage.getItem(FAULTS_KEY)) || 0; sessionStorage.removeItem(FAULTS_KEY); } catch (_) { told = 0; }
      const page = { build: String(window.LMA_BUILD || document.documentElement.dataset.build || ""), schema: SAVE_SCHEMA, fingerprint: mine, faults: told };
      const a = await plugin.configure({ text: JSON.stringify({ v: PROTOCOL, enabled: true, sid, page }) });
      summary = a || null;
      if (!a || !a.ok || a.state !== "on" || mine !== hello.fingerprint || stopped === "faults" || stopped === "slow") { live = false; return refresh(); }
      stopped = "";
      cache = null;
      needBase = true;
      install();
      live = true;
      bootActions();
      refresh();
    } catch (_) {
      live = false;
      sync();
    }
  }
  async function refresh() {
    try { if (plugin && hello) summary = await plugin.summary(); } catch (_) { /* the row keeps its last words */ }
    sync();
  }
  function statusWords() {
    if (!wanted() || !summary) return "";
    const s = summary.state, why = summary.reason;
    if (s === "stale") return WORDS.stale;
    if (s === "paused") return WORDS.paused;
    if (s === "disabled") return why === "tooLarge" ? WORDS.tooLarge : WORDS.disabled;
    if (stopped === "tooLarge") return WORDS.tooLarge;
    if (s === "on" && summary.counts) {
      const day = (t) => new Date(t).toLocaleDateString("en-GB", { day: "numeric", month: "short" }), since = day(summary.since), c = summary.counts;
      /* an app that compares (WP6b) counts compared actions and differences; an older one only follows them */
      if (typeof c.compared !== "number") return WORDS.watching(Number(c.seen) || 0, since);
      const d = summary.different, last = d && d.at ? day(d.at) + " " + new Date(d.at).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" }) + " (" + String(d.kind || "").split(".")[0] + ")" : "";
      return WORDS.checked(c.compared, since, Number(c.different) || 0, Number(c.different) ? last : "");
    }
    return "";
  }
  function sync() {
    const input = document.getElementById("nativeShadowToggle"), line = document.getElementById("nativeShadowStatus");
    if (input) {
      input.disabled = !available() || demo();
      input.checked = wanted();
    }
    if (line) line.textContent = statusWords();
  }
  window.setNativeShadowEnabled = async (on) => {
    lmaLoad()[SETTING] = !!on;
    lmaSave();
    if (on) { stopped = ""; return warm(); }
    stop("off");
    summary = null;
    if (plugin && hello) await plugin.configure({ text: JSON.stringify({ v: PROTOCOL, enabled: false, sid: sid || "p-off", page: { build: "", schema: SAVE_SCHEMA, fingerprint: "", faults: 0 } }) }).catch(() => {});
    sync();
  };
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState !== "visible" || !wanted() || demo()) return;
    if (!live && (stopped === "paused" || stopped === "")) warm(); else refresh();
  });
  document.addEventListener("DOMContentLoaded", sync);
  sync();
  setTimeout(warm, 0);

  window.LmaShadow = {
    /** for tests and the Settings row: what the page side is doing, counts only */
    status: () => ({ live, stopped, sid, epoch, seq, head, needBase, quests, day, installed: installed.length, pending: pending.length, queued: outbox.length, stats: JSON.parse(JSON.stringify(stats)), summary }),
    fingerprint,
    words: WORDS,
    refresh,
  };
})();
