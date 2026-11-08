/* A stand-in for Android's NativeShadow plugin (ECONOMY_SHADOW_PLAN 2.3) for the page tests of the native economy check. It is
   an init script: script({...}) returns the text to hand to page.addInitScript before the page loads. It sets the owner's switch
   (lifemax_android_prefs_v1.nativeShadow) unless told not to, offers a Capacitor with NativeShadow only (registerPlugin gives
   nothing, as on a phone without the other plugins), and keeps a mirror built exactly as native's ShadowMirror does: the base
   parts joined, then each envelope's before-delta and after-delta laid over it, applied only on base === seq. The test reads
   window.__shadowMock: calls, envelopes, mirror, refusals.
   Modes: record (answers like native), reject (every call's promise rejects), throw (every method throws at once), stale
   (native's fingerprint differs), nocap (the handshake lacks economy.shadow.v1), absent (no plugin at all). compares:
   {compared, different} makes summary() answer as an app that compares (WP6b), the last difference a quest toggle. quests: true
   also says economy.shadow.quests.v1 (an app that compares the quest and Daily Hand kinds, WP6c). */
'use strict';

function mock(opts) {
  const o = Object.assign({ mode: 'record', on: true, fingerprint: '', resyncAt: [], paused: false }, opts);
  const m = window.__shadowMock = { opts: o, calls: [], envelopes: [], bases: [], refused: [], mirror: null, assembly: null, mirrors: [] };
  if (o.on) {
    let prefs = {};
    try { prefs = JSON.parse(localStorage.getItem('lifemax_android_prefs_v1') || '{}') || {} } catch (e) { prefs = {} }
    prefs.nativeShadow = true;
    localStorage.setItem('lifemax_android_prefs_v1', JSON.stringify(prefs));
  }
  const records = k => ['activities', 'quests', 'decayLog'].includes(k);
  function lay(keys, d) {
    const out = Object.assign({}, keys);
    for (const [k, v] of Object.entries(d.rec)) {
      const old = Array.isArray(out[k]) ? out[k] : [], next = old.slice(0, v.len);
      for (const [i, r] of v.at) next[i] = r;
      for (let i = 0; i < v.len; i++) if (!(i in next)) throw new Error('hole in ' + k);
      if (!records(k)) throw new Error('rec for ' + k);
      out[k] = next;
    }
    Object.assign(out, d.set);
    for (const k of d.del) delete out[k];
    return out;
  }
  const answer = v => Promise.resolve(v);
  const methods = {
    handshake: () => answer({ protocol: 1, capabilities: o.mode === 'nocap' ? [] : ['economy.shadow.v1'].concat(o.quests ? ['economy.shadow.quests.v1'] : []), fingerprint: o.mode === 'stale' ? 'not-the-page' : o.fingerprint, state: 'off' }),
    configure: ({ text }) => {
      const c = JSON.parse(text);
      m.config = c;
      m.mirror = null;
      const state = !c.enabled ? 'off' : c.page.fingerprint !== (o.mode === 'stale' ? 'not-the-page' : o.fingerprint) ? 'stale' : o.paused ? 'paused' : 'on';
      m.state = state;
      return answer({ ok: true, state });
    },
    base: ({ text }) => {
      const p = JSON.parse(text);
      if (p.part === 0) m.assembly = { p, text: '' };
      if (!m.assembly || m.assembly.p.seq !== p.seq) return answer({ ok: false, state: m.state, reason: 'badDelta' });
      m.assembly.text += p.text;
      if (p.part === p.parts - 1) {
        const keys = JSON.parse(m.assembly.text).keys;
        m.mirror = { sid: p.sid, epoch: p.epoch, seq: p.seq, keys };
        m.bases.push({ seq: p.seq, epoch: p.epoch, parts: p.parts, chars: m.assembly.text.length });
        m.assembly = null;
      }
      return answer({ ok: true, state: m.state });
    },
    observe: ({ text }) => {
      const e = JSON.parse(text);
      m.envelopes.push({ kind: e.kind, seq: e.seq, base: e.base, epoch: e.epoch, chars: text.length, toasts: e.toasts, env: e.env, flags: e.flags, returns: e.returns, before: e.before, after: e.after, cost: e.cost.ms, t0: e.t0, t1: e.t1, zone: e.zone, args: e.args });
      if (m.state !== 'on') return answer({ accepted: false, resync: false, reason: m.state });
      if (o.resyncAt.includes(m.envelopes.length)) { m.mirror = null; m.refused.push('asked'); return answer({ accepted: false, resync: true, reason: 'gap' }) }
      const r = m.mirror;
      if (!r || r.sid !== e.sid || r.epoch !== e.epoch || e.base !== r.seq) { m.refused.push(!r ? 'noBase' : 'gap'); m.mirror = null; return answer({ accepted: false, resync: true, reason: !r ? 'noBase' : 'gap' }) }
      try { r.keys = lay(lay(r.keys, e.before), e.after); r.seq = e.seq } catch (err) { m.refused.push(String(err.message)); m.mirror = null; return answer({ accepted: false, resync: true, reason: 'badDelta' }) }
      return answer({ accepted: true });
    },
    summary: () => answer(Object.assign({ v: 1, state: m.state || 'off', since: Date.UTC(2026, 10, 1), counts: Object.assign({ seen: m.envelopes.length, applied: m.envelopes.length, skipped: 0 },
      o.compares ? { compared: o.compares.compared, same: o.compares.compared - o.compares.different, allowed: 0, different: o.compares.different } : {}) },
      o.compares && o.compares.different ? { different: { at: Date.UTC(2026, 10, 18, 9, 31), kind: 'quest.toggle' } } : {})),
    clear: () => answer({ ok: true }),
  };
  const plugin = {};
  for (const [name, f] of Object.entries(methods)) {
    plugin[name] = function (a) {
      m.calls.push(name);
      if (o.mode === 'throw') throw new Error('native threw');
      if (o.mode === 'reject') return Promise.reject(new Error('native refused'));
      return f(a || {});
    };
  }
  const plugins = o.mode === 'absent' ? {} : { NativeShadow: plugin };
  window.Capacitor = {
    isNativePlatform: () => true, getPlatform: () => 'android', isPluginAvailable: n => n in plugins, Plugins: plugins,
    registerPlugin: () => undefined, convertFileSrc: u => u,
  };
}

/** The init script for page.addInitScript. */
exports.script = opts => '(' + mock.toString() + ')(' + JSON.stringify(opts || {}) + ');';

/** Runs in the page: the page's own shadow keys as JSON values, for comparing with the mock's mirror. */
exports.pageKeys = () => {
  const t = LMA_SHADOW_BOOT.capture(state), out = {};
  for (const [k, v] of Object.entries(t)) out[k] = typeof v === 'object' ? v.items.map(x => JSON.parse(x)) : JSON.parse(v);
  return out;
};
