/* A stand-in for Android's NativeShadow plugin, scope "director" (docs/specs/LIFE_DIRECTOR_SHADOW_SPEC.md SH-1 to SH-8), for the page
   tests of the native Life Director check. An init script: script({...}) returns the text for page.addInitScript. It sets the owner's
   switch (lifemax_android_prefs_v1.nativeDirectorShadow) unless told not to, offers a Capacitor with NativeShadow only, and keeps a copy
   built exactly as native's TextMirror does: a base's changes from nothing, then each envelope's changes laid over it, applied only on
   base === the copy's seq; the copy is stitched into one text after each save and read (window.__dsMock.texts). Every message is kept
   as the page sent it (window.__dsMock.stream), so a test can write them to a fixture the Kotlin tests replay.
   Modes: record (answers like native), throw (every method throws at once), reject (every promise rejects), stale (native's
   fingerprint differs), nocap (no director.shadow.v1), absent (no plugin). resyncAt: envelope numbers answered resync. */
'use strict';

function mock(opts) {
  const o = Object.assign({ mode: 'record', on: true, resyncAt: [], fingerprint: '1.1.0/1' }, opts);
  const m = window.__dsMock = { opts: o, calls: [], stream: [], envelopes: [], bases: 0, refused: [], copy: null, seq: 0, texts: [], state: 'off', asked: [] };
  if (o.on) {
    let prefs = {};
    try { prefs = JSON.parse(localStorage.getItem('lifemax_android_prefs_v1') || '{}') || {}; } catch (e) { prefs = {}; }
    prefs.nativeDirectorShadow = true;
    localStorage.setItem('lifemax_android_prefs_v1', JSON.stringify(prefs));
  }
  /* native's TextMirror */
  function apply(root, ops) {
    for (const [kind, path, arg] of ops) {
      const made = (old) => kind === 'o' ? (old && old.o ? (Object.keys(old.k).forEach((k) => { if (!arg.includes(k)) delete old.k[k]; }), old.o = arg, old) : { o: arg, k: {} })
        : kind === 'a' ? (old && old.a ? (old.a.length = arg, old) : { a: new Array(arg).fill(null) }) : { t: arg };
      if (!path.length) { if (kind === 't') throw new Error('root text'); root = made(root); continue; }
      let n = root;
      for (const s of path.slice(0, -1)) { n = n && (n.o ? n.k[s] : n.a ? n.a[s] : null); if (!n) throw new Error('no place'); }
      const last = path[path.length - 1];
      if (n.o) { if (!n.o.includes(last)) throw new Error('not a member'); n.k[last] = made(n.k[last]); }
      else if (n.a) { if (!(last >= 0 && last < n.a.length)) throw new Error('not an item'); for (let i = 0; i < n.a.length; i++) if (n.a[i] === undefined) n.a[i] = null; n.a[last] = made(n.a[last]); }
      else throw new Error('a text has no parts');
    }
    return root;
  }
  const stitch = (n) => { if (!n) throw new Error('missing'); return n.t !== undefined ? n.t : n.a ? '[' + n.a.map(stitch).join(',') + ']' : '{' + n.o.map((k) => JSON.stringify(k) + ':' + stitch(n.k[k])).join(',') + '}'; };
  let assembly = null;
  const answer = (v) => Promise.resolve(v);
  const director = {
    configure: ({ text }) => {
      const c = JSON.parse(text);
      m.config = c;
      m.copy = null;
      m.state = !c.enabled ? 'off' : c.page.fingerprint !== (o.mode === 'stale' ? 'not-the-page' : o.fingerprint) ? 'stale' : 'on';
      return answer({ ok: true, state: m.state });
    },
    base: ({ text }) => {
      const p = JSON.parse(text);
      if (p.part === 0) assembly = { seq: p.seq, text: '' };
      if (!assembly || assembly.seq !== p.seq) return answer({ ok: false, state: m.state, reason: 'badDelta' });
      assembly.text += p.text;
      if (p.part === p.parts - 1) {
        m.copy = apply(null, JSON.parse(assembly.text).ops);
        m.seq = p.seq;
        m.bases++;
        assembly = null;
      }
      return answer({ ok: true, state: m.state });
    },
    observe: ({ text }) => {
      const e = JSON.parse(text);
      m.envelopes.push(e);
      if (m.state !== 'on') return answer({ accepted: false, resync: false, reason: m.state });
      if (o.resyncAt.includes(m.envelopes.length)) { m.copy = null; m.refused.push('asked'); return answer({ accepted: false, resync: true, reason: 'gap' }); }
      if (e.kind === 'director.plan' || e.kind === 'director.repair') return answer(m.copy ? { accepted: true } : { accepted: false, resync: true, reason: 'noBase' });
      if (!m.copy || e.base !== m.seq) { m.refused.push(!m.copy ? 'noBase' : 'gap'); m.copy = null; return answer({ accepted: false, resync: true, reason: 'noBase' }); }
      try { m.copy = apply(m.copy, e.ops); m.seq = e.seq; m.texts.push({ kind: e.kind, seq: e.seq, bytes: e.bytes, text: stitch(m.copy) }); }
      catch (err) { m.refused.push(String(err.message)); m.copy = null; return answer({ accepted: false, resync: true, reason: 'badDelta' }); }
      return answer({ accepted: true });
    },
    summary: () => answer({ v: 1, state: m.state, since: Date.UTC(2026, 10, 1), counts: { seen: m.envelopes.length, applied: m.envelopes.length, skipped: 0, compared: m.envelopes.length, same: m.envelopes.length, allowed: 0, different: o.different || 0 } }),
    copyReport: () => { m.asked.push('copyReport'); return answer({ ok: true, chars: 1234 }); },
    clear: () => { m.asked.push('clear'); return answer({ ok: true }); },
  };
  const methods = {
    handshake: () => answer({ protocol: 1, capabilities: ['economy.shadow.v1'].concat(o.mode === 'nocap' ? [] : ['director.shadow.v1']), fingerprint: 'economy', state: 'off' }),
    economy: () => answer({ ok: false, reason: 'notThisTest' }),
  };
  const plugin = {};
  for (const name of ['handshake', 'configure', 'base', 'observe', 'summary', 'copyReport', 'clear']) {
    plugin[name] = function (a) {
      a = a || {};
      m.calls.push(name + (a.scope ? ':' + a.scope : ''));
      if (a.scope === 'director' && a.text) m.stream.push([name, a.text]);
      if (o.mode === 'throw') throw new Error('native threw');
      if (o.mode === 'reject') return Promise.reject(new Error('native refused'));
      if (name === 'handshake') return methods.handshake();
      return a.scope === 'director' ? director[name](a) : methods.economy();
    };
  }
  const plugins = o.mode === 'absent' ? {} : { NativeShadow: plugin };
  window.Capacitor = { isNativePlatform: () => true, getPlatform: () => 'android', isPluginAvailable: (n) => n in plugins, Plugins: plugins, registerPlugin: () => undefined, convertFileSrc: (u) => u };
}

exports.script = (opts) => '(' + mock.toString() + ')(' + JSON.stringify(opts || {}) + ');';
