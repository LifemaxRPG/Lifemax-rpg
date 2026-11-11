/* Native Life Director check (beta), the page's half (docs/specs/LIFE_DIRECTOR_SHADOW_SPEC.md; plan WP-LD12). The page stays the
   only writer and the only thing the owner sees. While the switch (nativeDirectorShadow, Settings, Data & advanced, under the economy
   check) is on and the app says director.shadow.v1, Life Director (src/features/life-director/logic.js) tells this module of each save
   and each plan its engine makes (LmaDirectorShadow.saved, .planning), and this module sends NativeShadow, scope "director":
   - director.save: what the save changed in the state, as the page's own JSON texts of its parts, and the length of the text the store
     wrote; native puts the parts together, decodes and re-encodes that exact text with its store codec and compares (SH-9 to SH-11);
   - director.read, at most once a minute after a save: the page's own day, now and week read models with the state they read and the
     XP previews they asked for; native computes the same and compares (SH-12 to SH-14);
   - director.plan and director.repair: the engine's input and its answer; native plans (or repairs) from the same input and compares,
     only when the input is small enough (SH-15 to SH-17, SH-24);
   - director.cmd and director.tick: a command (Do.*, Today.*, a completion, logging for today) or the tick (and its three steps when
     the Overview's glance runs them) with the state before it, its payload and moment, what the host and the store answered it, its
     reply and the state after; native runs its own host commands on the same state against a host that gives the page's answers
     and compares the reply, every host call with its arguments, the controller's memory and the state after (SH-36 to SH-49).
   Native never writes, answers only a state word, and keeps redacted counts; nothing native computes is shown. Every step here is
   caught; envelopes go after the action with setTimeout(0) and are not awaited. Three faults, or five actions in a row over the time
   budget (30 ms for each MB of the state's text, never under 50 ms), turn the page side off until the page loads again. */
(function () {
  const CAPABILITY = "director.shadow.v1", SETTING = "nativeDirectorShadow", PROTOCOL = 1, SCOPE = "director";
  const TICKS = { tick: 1, syncHost: 1, processMisses: 1, maybeRollover: 1 };
  /* LifeMaxHost's and the store's functions Life Director's commands call (LIFE_DIRECTOR_HOST_SPEC HC-4), as native's host has them */
  const HOST_FNS = ["connected", "toast", "playerId", "readQuests", "readPlanSessions", "awardActivity", "completeQuest", "activitiesSince", "activityExists", "milestones", "retiredUntimedQuest"];
  const LIMIT = { envelope: 512 * 1024, part: 256 * 1024, parts: 32, slice: 200000, base: 8 * 1024 * 1024, faults: 3, slowMs: 50, slowMsPerMB: 30, slowRun: 5, queue: 16, readEveryMs: 60000, engine: 384 * 1024 };
  /* where the state is split into parts (SH-5): the plan's sessions and history, the ledger's events and order, the change log */
  const SPLIT = { schedule: { scheduledInstances: 1, historyInstances: 1 }, ledger: { events: 1, order: 1 }, changeLog: 1 };
  let plugin = null, hello = null, live = false, stopped = "", sid = "", seq = 0, head = 0, cache = null, needBase = true, faults = 0, slowRun = 0, slowMs = LIMIT.slowMs;
  let summary = null, starting = false, timer = 0, lastRead = -Infinity, readDue = false, recording = null, copyEpoch = 0;
  const told = new WeakMap(); // a take-back native has been sent in full, since the copy's last base (SH-41)
  const outbox = [];
  const stats = { sent: 0, bases: 0, reads: 0, plans: 0, commands: 0, skipped: {}, faults: 0, answers: {}, costs: [] };
  const skip = (why) => (stats.skipped[why] = (stats.skipped[why] || 0) + 1);
  const available = () => {
    try { return Capacitor.isNativePlatform() && Capacitor.isPluginAvailable("NativeShadow") ? lmaPlugin("NativeShadow") : null; } catch (_) { return null; }
  };
  const demo = () => !!(window.LM_DEMO && window.LM_DEMO.on);
  const wanted = () => lmaLoad()[SETTING] === true;
  const app = () => (typeof LifeDirectorApp !== "undefined" && LifeDirectorApp.shadowReads ? LifeDirectorApp : null);
  function fault() {
    faults++;
    stats.faults++;
    if (faults >= LIMIT.faults) stop("faults");
  }

  /* ---------- the state as parts (SH-5, SH-6): exactly the texts JSON.stringify writes for them ---------- */
  function capture(v, spec) {
    if (spec && v && typeof v === "object" && typeof v.toJSON !== "function") {
      if (Array.isArray(v)) return { a: v.map((x) => (spec === 1 ? item(x) : capture(x, null))) };
      const o = [], k = {};
      for (const key of Object.keys(v)) {
        const c = capture(v[key], spec === 1 ? null : spec[key]);
        if (c !== undefined) { o.push(key); k[key] = c; }
      }
      return { o, k };
    }
    return JSON.stringify(v); // undefined for a value JSON leaves out
  }
  const item = (x) => { const t = JSON.stringify(x); return t === undefined ? "null" : t; };
  /* the changes from a (null: nothing) to b, in order: o (members), a (length), t (a part's text) */
  function changes(a, b, path, out) {
    if (typeof b === "string") { if (a !== b) out.push(["t", path, b]); return out; }
    if (b.a) {
      const old = a && a.a;
      if (!old || old.length !== b.a.length) out.push(["a", path, b.a.length]);
      b.a.forEach((x, i) => { if (!old || old[i] !== x) out.push(["t", path.concat(i), x]); });
      return out;
    }
    const same = a && a.o && a.o.length === b.o.length && a.o.every((k, i) => k === b.o[i]);
    if (!same) out.push(["o", path, b.o]);
    for (const k of b.o) changes(a && a.o ? a.k[k] : undefined, b.k[k], path.concat(k), out);
    return out;
  }
  const textOf = (tree) => {
    const t = (n) => (typeof n === "string" ? n : n.a ? "[" + n.a.join(",") + "]" : "{" + n.o.map((k) => JSON.stringify(k) + ":" + t(n.k[k])).join(",") + "}");
    return t(tree);
  };

  /* ---------- what Life Director tells (logic.js shadow()): only while live, every step caught there too ---------- */
  const api = {
    live: false,
    /** a save the store wrote: the state's parts now, and the length of the text it wrote */
    saved(st, bytes) {
      const t = performance.now();
      const tree = capture(st, SPLIT);
      queue({ kind: "director.save", tree, bytes, t0: Date.now(), cost: performance.now() - t });
      if (performance.now() - lastRead >= LIMIT.readEveryMs) readDue = true;
    },
    /** a plan about to be made from input: its text now, and a function that takes the engine's answer */
    planning(kind, input) {
      const t = performance.now(), text = JSON.stringify(input), t0 = Date.now();
      if (!text || text.length > LIMIT.engine) { skip("engineSize"); return null; }
      const zone = String((kind === "repair" ? input.engineInput && input.engineInput.timezone : input.timezone) || "");
      const now = Number(kind === "repair" ? input.engineInput && input.engineInput.currentTimeEpoch : input.currentTimeEpoch);
      const cost = performance.now() - t;
      return (out) => {
        const t2 = performance.now(), o = JSON.stringify(out);
        if (!o || o.length > 2 * LIMIT.engine) { skip("engineSize"); return; }
        queue({ kind: "director." + kind, input: text, output: o, zone, now, t0, cost: cost + performance.now() - t2 });
      };
    },
    /** a command about to run (logic.js command()): its state, payload and moment now, the host's answers while it runs, and a
        handle whose end(out, err) takes its answer (or throw) and the state after */
    command(op, name, payload, vars) {
      if (!live || recording) return null;
      const t = performance.now();
      const V = vars();
      if (!V || !V.ST || !V.ui) { skip("noState"); return null; }
      const r = { kind: TICKS[op] ? "director.tick" : "director.cmd", op, name: String(name || ""), payload: JSON.stringify(payload === undefined ? null : payload) || "null",
        t0: Date.now(), zone: zone(), planZone: String((V.ST.availability && V.ST.availability.timezone) || zone()), before: capture(V.ST, SPLIT),
        sessionBefore: session(V), takeBackBefore: V.takeBack || null, calls: [], texts: [], index: new Map(), ids: [], broken: false, extra: 0 };
      r.planOffset = offset(Math.floor(r.t0 / 1000), r.planZone);
      const H = typeof LifeMaxHost !== "undefined" ? LifeMaxHost : null;
      r.retires = !!(H && typeof H.retiredUntimedQuest === "function");
      const undo = [];
      recording = { r, vars, undo, depth: 0 };
      try {
        if (H) for (const fn of HOST_FNS) if (typeof H[fn] === "function") patch(undo, H, fn, (real) => host(fn, real));
        if (V.store) wrapStore(undo, V.store);
        if (typeof LifeDirectorStore !== "undefined" && typeof LifeDirectorStore.create === "function")
          patch(undo, LifeDirectorStore, "create", (real) => function () { const s = real.apply(this, arguments); try { wrapStore(undo, s); } catch (_) { r.broken = true; } return s; });
        const C = globalThis.crypto;
        if (C && typeof C.randomUUID === "function") patch(undo, C, "randomUUID", (real) => function () { const v = real.apply(this, arguments); r.ids.push(v); return v; });
      } catch (_) { restore(); return null; }
      r.cost = performance.now() - t;
      return {
        end(out, err) {
          restore();
          if (!live) return;
          const t2 = performance.now(), V2 = vars();
          r.t1 = Date.now();
          if (r.broken) { skip("host"); return; }
          let reply = err ? { threw: (err && err.name) || "Error", message: String(err && err.message) } : out === undefined ? null : out;
          if ((op === "finish" || op === "logToday") && reply && typeof reply === "object" && !err && "ledger" in reply) reply = Object.assign({}, reply, { ledger: undefined }); // the ledger is the state's (SH-38)
          r.reply = JSON.stringify(reply) || "null";
          r.after = capture(V2.ST, SPLIT);
          r.sessionAfter = session(V2);
          r.takeBackAfter = V2.takeBack || null;
          r.cost += r.extra + performance.now() - t2;
          queue(r);
        },
      };
    },
  };
  window.LmaDirectorShadow = api;
  /* ---------- a command's host and store calls (SH-37): recorded as native's recording host notes them; the answers are the real ones ---------- */
  function patch(undo, obj, key, wrap) {
    const own = Object.prototype.hasOwnProperty.call(obj, key), old = obj[key];
    obj[key] = wrap(old);
    undo.push(() => { if (own) obj[key] = old; else delete obj[key]; });
  }
  function restore() {
    const c = recording;
    recording = null;
    if (c) for (const u of c.undo.reverse()) { try { u(); } catch (_) { /* the next one still goes back */ } }
  }
  /* a JSON text kept once per command; calls name it by its place */
  function textIndex(r, v) {
    const t = JSON.stringify(v === undefined ? null : v) || "null";
    let k = r.index.get(t);
    if (k === undefined) { k = r.texts.length; r.texts.push(t); r.index.set(t, k); }
    return k;
  }
  function note(fn, args, out) {
    const c = recording;
    if (!c) return;
    const t = performance.now();
    try { c.r.calls.push([fn, textIndex(c.r, args), textIndex(c.r, out)]); } catch (_) { c.r.broken = true; }
    c.r.extra += performance.now() - t;
  }
  /* only the command's own calls: not one the host makes of itself (H.playerId asks H.connected), nor the screen's drawing */
  const counted = () => { const c = recording; if (!c || c.depth) return false; try { return !c.vars().presenting; } catch (_) { return false; } };
  function host(fn, real) {
    return function () {
      if (!counted()) return real.apply(this, arguments);
      const c = recording, args = Array.prototype.slice.call(arguments);
      c.depth++;
      let out;
      try { out = real.apply(this, arguments); } catch (e) { c.r.broken = true; throw e; } finally { c.depth--; }
      /* everything the page reads of activitiesSince(null) is whether there is one and the latest date (logic.js syncHost) */
      if (fn === "activitiesSince" && args[0] == null && Array.isArray(out)) note(fn, args, out.length ? [out.reduce((m, a) => (a && m && a.iso > m.iso ? a : m), out[0])] : []);
      else note(fn, args, out);
      return out;
    };
  }
  function wrapStore(undo, s) {
    if (!s) return;
    const w = (key, f) => { if (typeof s[key] === "function") patch(undo, s, key, f); };
    const own = (real, f) => function () {
      if (!counted()) return real.apply(this, arguments);
      recording.depth++;
      try { return f.apply(this, [real].concat(Array.prototype.slice.call(arguments))); } finally { if (recording) recording.depth--; }
    };
    w("load", (real) => own(real, function (real, o) { const r = real.call(this, o); note("store.load", [o], { state: r.state, report: r.report }); return r; }));
    w("save", (real) => own(real, function (real, st) { const r = real.call(this, st); note("store.save", [], { ok: r.ok, error: r.error }); return r; }));
    w("clear", (real) => own(real, function (real, o) { const r = real.call(this, o); note("store.clear", [o], { ok: r.ok, error: r.error }); return r; }));
    w("exportJson", (real) => own(real, function (real, st) { const r = real.call(this, st); note("store.exportJson", [], r); return r; }));
    w("importJson", (real) => own(real, function (real, text) {
      let r;
      try { r = real.call(this, text); } catch (e) { note("store.importJson", [text], { error: e.message }); throw e; }
      note("store.importJson", [text], { state: r });
      return r;
    }));
  }
  /* the controller's memory between commands (LIFE_DIRECTOR_HOST_SPEC HC-2), as native's HostState holds it; a take-back as its
     digest (SH-41) */
  function session(V) {
    const ui = V.ui, n = ui.notice, t = V.takeBack, s = V.lastSave || {};
    return { banner: ui.banner || null, notice: n ? { id: n.id, text: n.text, at: n.at } : null, tab: ui.tab, dayIdx: ui.dayIdx, pendingDial: ui.pendingDial || null,
      takeBack: t ? { id: t.id, text: t.text, at: t.at, digest: takeBackDigest(t) } : null, takenBackOp: V.takenBackOp === undefined ? null : V.takenBackOp,
      lastSave: { ok: s.ok, error: s.error }, lastFinishRefusal: V.lastFinishRefusal === undefined ? null : V.lastFinishRefusal };
  }
  const digests = new WeakMap();
  /* FNV-1a over the UTF-16 code units, with the length: the take-back's plan and ledger as JSON text and the plan it left */
  function digest(s) {
    let h = 0x811c9dc5;
    for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 0x01000193); }
    return s.length.toString(36) + "." + (h >>> 0).toString(36);
  }
  function takeBackDigest(t) {
    let d = digests.get(t);
    if (!d) { d = digest(JSON.stringify(t.before) || "") + ":" + digest(String(t.left)); digests.set(t, d); }
    return d;
  }
  function queue(r) {
    if (!live) return;
    if (outbox.length >= LIMIT.queue) { outbox.length = 0; cache = null; needBase = true; skip("queue"); }
    outbox.push(r);
    if (!timer) timer = setTimeout(flush, 0);
  }

  /* ---------- the page's own read models (SH-12), in the read-model recorder's shape ---------- */
  function reads() {
    const L = app(), R = L && L.shadowReads(), st = R && R.ST;
    if (!st || !st.setupComplete || !st.schedule) return null;
    const H = typeof LifeMaxHost !== "undefined" ? LifeMaxHost : null, calls = [], real = H && H.xpFor;
    const s = st.schedule, t0 = Date.now();
    const ref = (i) => { let k = (s.scheduledInstances || []).indexOf(i); if (k >= 0) return "s" + k; k = (s.historyInstances || []).indexOf(i); return k >= 0 ? "h" + k : "?"; };
    const blockOut = (b) => (b.inst ? Object.assign({}, b, { inst: ref(b.inst) }) : Object.assign({}, b));
    const section = (f) => { try { return f(); } catch (e) { return { threw: (e && e.name) || "Error" }; } };
    const out = {};
    if (real) H.xpFor = function (type, minutes, quality, dateKey) {
      let r = null, err = null;
      try { r = real.apply(this, arguments); } catch (e) { err = e; }
      let shown = null;
      try { shown = r ? { xp: r.xp, primary: r.rule.primary, split: r.split.map((x) => ({ attr: x.attr, xp: x.xp })) } : null; } catch (_) { shown = null; }
      calls.push({ args: [type, minutes, quality, dateKey], out: shown });
      if (err) throw err;
      return r;
    };
    try {
      const z = R.tz();
      let days = null;
      out.days = section(() => (days = R.horizonDays(s, z)));
      out.unplanned = section(() => R.unplannedSegments(days[0], z));
      out.blocks = section(() => days.map((d) => R.dayBlocks(s, d, z).map(blockOut)));
      out.timeline = section(() => R.liveTimeline(R.nowEpoch()).map(blockOut));
      out.now = section(() => {
        const n = R.nowState(R.nowEpoch()), b = (x) => (x ? blockOut(x) : null);
        return { cur: b(n.cur), next: b(n.next), nextTask: b(n.nextTask), kick: n.kick, soon: n.soon ? { kind: n.soon.kind, b: b(n.soon.b), sec: n.soon.sec } : null,
          alert: n.alert ? (n.alert === n.kick ? "kick" : "soon") : null, isSes: !!n.isSes, running: !!n.running };
      });
      out.week = section(() => {
        const W = R.weekModel();
        return { rows: W.rows.map((r) => ({ k: r.k, n: r.n, done: r.done, m: r.m, name: r.name, aria: r.aria })), stats: W.stats, attrs: W.attrs, maxA: W.maxA, fitText: W.fitText };
      });
      out.native = section(() => R.nativeWeek());
      out.xp = calls;
    } finally { if (real && H.xpFor !== real) H.xpFor = real; }
    const t1 = Date.now(), planZone = String(R.tz());
    return { st, t0, t1, read: { nowMs: t0, deviceZone: String(R.deviceTz()), planZone, planOffset: offset(Math.floor(t0 / 1000), planZone), xp: calls, out } };
  }
  function offset(epoch, zone) {
    try { const v = LifeDirectorEngine.util.tzOffsetSec(epoch, zone); return Number.isFinite(v) ? Math.round(v) : null; } catch (_) { return null; }
  }

  /* ---------- sending (SH-3, SH-8): in order, after the action, never awaited ---------- */
  function zone() { try { return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC"; } catch (_) { return "UTC"; } }
  function envelope(r, ops, extra, flags) {
    const s = ++seq;
    const text = '{"v":' + PROTOCOL + ',"scope":"' + SCOPE + '","sid":' + JSON.stringify(sid) + ',"seq":' + s + ',"base":' + head + ',"kind":' + JSON.stringify(r.kind) +
      ',"t0":' + r.t0 + ',"t1":' + Math.max(r.t1 || Date.now(), r.t0) + ',"zone":' + JSON.stringify(zone()) + ',"ops":' + JSON.stringify(ops) + extra +
      ',"cost":{"ms":' + Math.round((r.cost || 0) * 10) / 10 + '},"flags":' + JSON.stringify(flags || []) + "}";
    return { s, text };
  }
  function call(method, text) {
    try { return plugin[method]({ scope: SCOPE, text }).then((a) => answered(method, a), () => answered(method, null)); } catch (_) { fault(); return Promise.resolve(); }
  }
  function sendBase(tree) {
    const text = JSON.stringify({ ops: changes(null, tree, [], []) });
    if (text.length > LIMIT.base) { stop("tooLarge"); return false; }
    slowMs = Math.max(LIMIT.slowMs, Math.round(LIMIT.slowMsPerMB * text.length / (1024 * 1024)));
    const parts = [];
    let size = LIMIT.slice;
    for (;;) {
      parts.length = 0;
      for (let i = 0; i < text.length; i += size) parts.push(text.slice(i, i + size));
      if (parts.length > LIMIT.parts) { stop("tooLarge"); return false; }
      if (Math.max(...parts.map((p) => JSON.stringify(p).length)) + 200 <= LIMIT.part) break;
      size = Math.floor(size * 0.75);
    }
    const s = ++seq;
    parts.forEach((p, i) => call("base", JSON.stringify({ v: PROTOCOL, scope: SCOPE, sid, seq: s, part: i, parts: parts.length, text: p })));
    head = s;
    cache = tree;
    needBase = false;
    copyEpoch++;
    stats.bases++;
    return true;
  }
  /* a command (SH-36): the changes to the state before it, then (cmd.after) from before to after; the take-back in full once per copy */
  function sendCommand(r) {
    if (needBase && !sendBase(r.before)) return;
    const tb = r.takeBackBefore, full = !!tb && told.get(tb) !== copyEpoch;
    const before = Object.assign({}, r.sessionBefore);
    if (full) before.takeBack = Object.assign({}, before.takeBack, { before: tb.before, left: String(tb.left) });
    const cmd = (ops) => ',"cmd":{"op":' + JSON.stringify(r.op) + ',"name":' + JSON.stringify(r.name) + ',"payload":' + r.payload + ',"nowMs":' + r.t0 +
      ',"deviceZone":' + JSON.stringify(r.zone) + ',"planZone":' + JSON.stringify(r.planZone) + ',"planOffset":' + JSON.stringify(r.planOffset) + ',"retires":' + r.retires +
      ',"ids":' + JSON.stringify(r.ids) + ',"calls":' + JSON.stringify(r.calls) + ',"texts":' + JSON.stringify(r.texts) + ',"reply":' + r.reply +
      ',"session":{"before":' + JSON.stringify(before) + ',"after":' + JSON.stringify(r.sessionAfter) + '},"after":' + JSON.stringify(ops) + "}";
    const flags = Math.floor(r.t0 / 1000) !== Math.floor(r.t1 / 1000) ? ["second"] : [];
    const afterOps = changes(r.before, r.after, [], []);
    let e = envelope(r, changes(cache, r.before, [], []), cmd(afterOps), flags);
    if (e.text.length > LIMIT.envelope) {
      seq--;
      if (!sendBase(r.before)) return;
      e = envelope(r, [], cmd(afterOps), flags);
      if (e.text.length > LIMIT.envelope) { seq--; skip("size"); return; } // native holds the state before; the next change starts there
    }
    head = e.s;
    cache = r.after;
    if (full) told.set(tb, copyEpoch);
    stats.commands++;
    stats.sent++;
    call("observe", e.text);
  }
  /* a state change and its envelope: a base first when native has no copy, or when the change alone is too large to send */
  function withState(r, tree, extra, flags) {
    if (needBase && !sendBase(tree)) return false;
    let e = envelope(r, changes(cache, tree, [], []), extra, flags);
    if (e.text.length > LIMIT.envelope) {
      seq--;
      if (!sendBase(tree)) return false;
      e = envelope(r, [], extra, flags);
      if (e.text.length > LIMIT.envelope) { seq--; skip("size"); return false; }
    }
    head = e.s;
    cache = tree;
    stats.sent++;
    call("observe", e.text);
    return true;
  }
  function flush() {
    timer = 0;
    const list = outbox.splice(0);
    if (readDue && live) { readDue = false; list.push({ kind: "director.read" }); }
    for (const r of list) {
      if (!live) return;
      const t = performance.now();
      try {
        if (r.kind === "director.save") withState(r, r.tree, ',"bytes":' + r.bytes);
        else if (r.kind === "director.read") {
          lastRead = performance.now();
          const p = reads();
          if (!p) { skip("noPlan"); continue; }
          r.t0 = p.t0; r.t1 = p.t1;
          const tree = capture(p.st, SPLIT);
          stats.reads++;
          withState(r, tree, ',"read":' + JSON.stringify(p.read), Math.floor(p.t0 / 1000) !== Math.floor(p.t1 / 1000) ? ["second"] : []);
        } else if (r.kind === "director.cmd" || r.kind === "director.tick") sendCommand(r);
        else {
          if (needBase) { skip("noCopy"); continue; } // native refuses every envelope until it has a copy; the next save sends one
          const engine = '{"planZone":' + JSON.stringify(r.zone) + ',"planOffset":' + JSON.stringify(offset(r.now, r.zone)) + ',"input":' + r.input + ',"output":' + r.output + "}";
          const e = envelope(r, [], ',"engine":' + engine);
          if (e.text.length > LIMIT.envelope) { seq--; skip("size"); continue; }
          stats.plans++;
          stats.sent++;
          call("observe", e.text);
        }
      } catch (_) { fault(); cache = null; needBase = true; }
      finally { if (live) budget((r.cost || 0) + performance.now() - t); }
    }
  }
  function budget(ms) {
    stats.costs.push(Math.round(ms * 10) / 10);
    if (stats.costs.length > 200) stats.costs.shift();
    slowRun = ms > slowMs ? slowRun + 1 : 0;
    if (slowRun >= LIMIT.slowRun) { fault(); stop("slow"); }
  }
  function answered(method, a) {
    const word = a && (a.reason || (a.accepted === false || a.ok === false ? "refused" : "ok"));
    stats.answers[method + ":" + word] = (stats.answers[method + ":" + word] || 0) + 1;
    if (!a) return;
    if ((method === "observe" && a.accepted === false) || (method === "base" && a.ok === false)) {
      if (a.resync || method === "base") { cache = null; needBase = true; }
      const why = method === "base" ? a.state : a.reason;
      if (why === "paused") stop("paused");
      else if (["off", "disabled", "stale", "unsupported"].includes(why)) stop(why);
    }
  }

  /* ---------- the switch, the handshake and the Settings card ---------- */
  function stop(why) {
    restore();
    live = api.live = false;
    stopped = why;
    outbox.length = 0;
    cache = null;
    needBase = true;
    sync();
  }
  async function warm() {
    plugin = available();
    if (!plugin || !wanted() || demo()) return sync();
    starting = true;
    sync();
    try {
      hello = hello || (await plugin.handshake());
      if (!hello || hello.protocol !== PROTOCOL || !Array.isArray(hello.capabilities) || !hello.capabilities.includes(CAPABILITY)) { starting = false; return sync(); }
      sid = sid || "d-" + Math.random().toString(36).slice(2, 10);
      let fingerprint = "";
      try { fingerprint = LifeDirectorEngine.ENGINE_VERSION + "/" + LifeDirectorStore.CURRENT; } catch (_) { fingerprint = ""; }
      const page = { build: String(window.LMA_BUILD || document.documentElement.dataset.build || ""), fingerprint, faults: stats.faults };
      const a = await plugin.configure({ scope: SCOPE, text: JSON.stringify({ v: PROTOCOL, scope: SCOPE, enabled: true, sid, page }) });
      summary = a || null;
      starting = false;
      if (!a || !a.ok || a.state !== "on" || stopped === "faults" || stopped === "slow" || !app()) return refresh();
      stopped = "";
      cache = null;
      needBase = true;
      live = api.live = true;
      readDue = true;
      if (!timer) timer = setTimeout(flush, 0); // the first read check carries the first copy (a base)
      refresh();
    } catch (_) {
      live = api.live = false;
      starting = false;
      stopped = stopped || "noAnswer";
      sync();
    }
  }
  async function refresh() {
    try { if (plugin && hello) summary = await plugin.summary({ scope: SCOPE }); } catch (_) { /* the line keeps its words */ }
    sync();
  }
  const day = (t) => new Date(t).toLocaleDateString("en-GB", { day: "numeric", month: "short" });
  const count = (n) => (Number(n) || 0).toLocaleString("en-GB");
  const WORDS = {
    unavailable: "Not available in this version of the app.",
    off: "Off. Turn it on to start checking.",
    starting: "Starting…",
    demo: "Paused during the demo. It only checks your own plan.",
    stale: "Paused: waiting for the matching app update.",
    paused: "Paused while Battery Saver is on. It carries on when Battery Saver is off.",
    disabled: "Stopped after errors. Turn it off and on to try again.",
    slow: "Stopped for now: checking was slowing the app down. It starts again the next time the app opens.",
    faults: "Stopped for now after a problem on this page. It starts again the next time the app opens.",
    tooLarge: "Stopped: your Life Director data is too large for the check.",
    noAnswer: "The phone's check did not answer. It tries again the next time you come back to the app.",
    checked: (n, since, different) => "Checked " + count(n) + " times since " + since + " · " + (Number(different) ? count(different) + " different" : "none different"),
    copied: "Report copied. Paste it into your chat with the assistant.",
    copyFailed: "Could not copy the report. Try again.",
    cleared: "Cleared. The check starts counting again from now.",
    clearFailed: "Could not clear it. Nothing was changed; try again.",
    working: "One moment…",
  };
  let said = "", asking = false, busy = false;
  function status() {
    if (demo()) return WORDS.demo;
    if (!available()) return WORDS.unavailable;
    if (!wanted()) return WORDS.off;
    if (hello && (!Array.isArray(hello.capabilities) || !hello.capabilities.includes(CAPABILITY))) return WORDS.unavailable;
    if (stopped === "faults" || stopped === "slow" || stopped === "tooLarge") return WORDS[stopped];
    if (stopped === "noAnswer" && !summary) return WORDS.noAnswer;
    if (!summary || !summary.state) return starting || !stopped ? WORDS.starting : "";
    const s = summary.state;
    if (s === "stale" || s === "paused") return WORDS[s];
    if (s === "disabled") return summary.reason === "tooLarge" ? WORDS.tooLarge : summary.reason === "slow" ? WORDS.slow : WORDS.disabled;
    if (s === "on" && summary.counts) return WORDS.checked(summary.counts.compared, day(summary.since), summary.counts.different);
    return s === "on" ? WORDS.starting : "";
  }
  const el = (id) => document.getElementById(id);
  function text(id, words) {
    const e = el(id);
    if (!e) return;
    if (e.textContent !== words) e.textContent = words;
    e.style.display = words ? "" : "none";
  }
  function sync() {
    try {
      const input = el("nativeDirectorShadowToggle");
      if (input) { input.disabled = !available() || demo(); input.checked = wanted(); }
      text("nativeDirectorShadowStatus", status());
      const tools = !!(hello && plugin && wanted() && !demo() && hello.capabilities && hello.capabilities.includes(CAPABILITY));
      const t = el("nativeDirectorShadowTools");
      if (t) t.style.display = tools ? "" : "none";
      if (!tools) asking = false;
      text("nativeDirectorShadowSaid", tools ? said : "");
      const ask = el("nativeDirectorShadowClearAsk");
      if (ask) ask.style.display = tools && asking ? "" : "none";
      for (const id of ["nativeDirectorShadowCopyBtn", "nativeDirectorShadowClearBtn", "nativeDirectorShadowClearYes"]) { const b = el(id); if (b) b.disabled = busy; }
    } catch (_) { /* the card never stops the check */ }
  }
  async function ask(method, answer) {
    if (busy || !plugin) return;
    busy = true;
    said = WORDS.working;
    sync();
    let a = null;
    try { a = await plugin[method]({ scope: SCOPE }); } catch (_) { a = null; }
    busy = false;
    said = answer(a || {});
    await refresh();
  }
  window.LmaDirectorShadowCard = {
    copy: () => ask("copyReport", (a) => (a.ok ? WORDS.copied : WORDS.copyFailed)),
    clear: () => { said = ""; asking = !asking; sync(); },
    clearKeep: () => { asking = false; sync(); },
    clearNow: () => { asking = false; return ask("clear", (a) => (a.ok ? WORDS.cleared : WORDS.clearFailed)); },
    status,
    words: WORDS,
    /* the controls native Settings may press by command (50-native-settings.js); none writes page storage */
    bound: [
      ["nativeDirectorShadow.copy", "#nativeDirectorShadowCopyBtn", null],
      ["nativeDirectorShadow.clear", "#nativeDirectorShadowClearBtn", null],
      ["nativeDirectorShadow.clearNow", "#nativeDirectorShadowClearYes", null],
      ["nativeDirectorShadow.clearKeep", "#nativeDirectorShadowClearNo", null],
    ],
  };
  window.setNativeDirectorShadowEnabled = async (on) => {
    lmaLoad()[SETTING] = !!on;
    lmaSave();
    if (on) { stopped = ""; return warm(); }
    stop("off");
    summary = null;
    if (plugin && hello) await plugin.configure({ scope: SCOPE, text: JSON.stringify({ v: PROTOCOL, scope: SCOPE, enabled: false, sid: sid || "d-off", page: { build: "", fingerprint: "", faults: 0 } }) }).catch(() => {});
    sync();
  };
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState !== "visible" || !wanted() || demo()) return;
    if (!live && (stopped === "paused" || stopped === "noAnswer" || stopped === "")) warm(); else refresh();
  });
  document.addEventListener("DOMContentLoaded", sync);
  sync();
  setTimeout(warm, 0);

  window.LmaDirectorShadowStatus = () => ({ live, stopped, sid, seq, head, needBase, slowMs, queued: outbox.length, stats: JSON.parse(JSON.stringify(stats)), summary });
  /** for the tests: the texts native would stitch, from the parts this side last sent */
  window.LmaDirectorShadowText = () => (cache ? textOf(cache) : null);
})();
