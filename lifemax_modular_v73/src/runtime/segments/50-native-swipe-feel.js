/* SwipeFeel: the web swipe's drag with the native swipe's feel (plans/TAB_SWIPE.md phase 3, "one feel everywhere"). With Native tab
   swipe (beta) on, 34-navigation's swipe between web tabs asks this file where the cards are for the finger's travel, whether a
   release commits, and how a cancel settles, instead of using its own 1:1 follow and 22% rule:
   - the curve is the native one (TensionCurve.kt): 1:1 up to the knee, heavier up to the catch, a rubber band at the ends; past the
     catch the cards pop to the finger (a damped spring takes up the jump) with one lmaBuzz('tick');
   - the release rule is the native one (ReleaseRule.kt): a wall or a flick back cancels, past the catch commits, a hard flick after
     a little travel commits, anything else cancels;
   - a cancel settles on a critically damped spring's curve, as native's does.
   The numbers are LMA_TABSWIPE (50-native-swipe.js) with the owner's overrides, clamped as native clamps them; a test keeps the
   curve equal to native's golden values. Pure functions plus one small state per drag. With the switch off nothing here is asked. */
(function () {
  /* the clamps of SwipeTune.kt: the page and native hold every value to the same range */
  const RANGES = { knee: [0.05, 0.35], heavy: [0.05, 1], hyst: [0, 0.15], popDamping: [0.4, 1], popStiffness: [400, 3000], wallMax: [0, 0.15], wallSlope: [0, 0.6], flingDp: [500, 3000], flingMin: [0.05, 0.35], backFlingDp: [200, 2000] };
  const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

  /** the feel in force: the defaults, the owner's changes, every value within native's range (the catch 0.10 to 0.70 past the knee) */
  function tune(overrides) {
    const t = Object.assign({}, LMA_TABSWIPE, overrides === undefined ? lmaLoad().swipeTune : overrides);
    for (const [k, [lo, hi]] of Object.entries(RANGES)) t[k] = Number.isFinite(t[k]) ? clamp(t[k], lo, hi) : LMA_TABSWIPE[k];
    t.breakAt = Number.isFinite(t.breakAt) ? clamp(t.breakAt, t.knee + 0.1, 0.7) : LMA_TABSWIPE.breakAt;
    return t;
  }

  /* the curve: u is the finger's travel toward the neighbour as a fraction of the width, o how far the cards have moved */
  function hold(u, t) {
    const a = t.knee, b = t.breakAt;
    if (u <= a) return u;
    if (u <= b) return u - ((1 - t.heavy) * (u - a) * (u - a)) / (2 * (b - a));
    return hold(b, t) + t.heavy * (u - b);
  }
  const through = (u) => Math.min(u, 1);
  const wall = (u, t) => (t.wallMax <= 0 ? 0 : t.wallMax * (1 - Math.exp((-t.wallSlope * u) / t.wallMax)));
  const latchedAt = (u, was, t) => (was ? u >= t.breakAt - t.hyst : u >= t.breakAt);
  const shape = (u, latched, isWall, t) => (isWall ? wall(u, t) : latched ? through(u) : hold(u, t));

  /* a damped spring of unit mass toward 0 from x0 at v0 (Spring1D.kt's closed form), at time s seconds */
  function spring(x0, v0, k, zeta, s) {
    const w = Math.sqrt(k);
    if (zeta >= 0.9999) return (x0 + (v0 + w * x0) * s) * Math.exp(-w * s);
    const wd = w * Math.sqrt(1 - zeta * zeta), b = (v0 + zeta * w * x0) / wd;
    return Math.exp(-zeta * w * s) * (x0 * Math.cos(wd * s) + b * Math.sin(wd * s));
  }

  /** a new drag's state; kept on the swipe (lmaSw) by the caller */
  const begin = () => ({ dir: 0, latched: false, res: null, anchor: undefined, u: 0 });

  /**
   * Where the cards are for a finger [dx] px from where the touch began, on a screen [w] px wide. [d] is the drag's state; [atEnd]
   * is true when there is no tab on the side being pulled toward. Returns the cards' offset in px (negative toward the next tab) and
   * buzzes once as the catch lets go. [now] is the event's time in ms.
   */
  function move(d, dx, w, atEnd, now) {
    const t = tune();
    if (d.anchor === undefined) d.anchor = dx; // the claim point: the cards start at 0 under the finger
    const rel = dx - d.anchor, dir = rel < 0 ? 1 : rel > 0 ? -1 : d.dir || 1;
    if (dir !== d.dir) Object.assign(d, { dir, latched: false, res: null }); // back past the claim point: the other side, fresh
    const u = Math.abs(rel) / w;
    const was = d.latched, isWall = atEnd(dir);
    d.latched = !isWall && latchedAt(u, was, t);
    if (d.latched !== was) {
      // the curve jumps: a spring takes up the difference, underdamped at a latch (the pop), critical at an unlatch
      const jump = shape(u, was, isWall, t) - shape(u, d.latched, isWall, t), at = d.res ? (now - d.res.t) / 1000 : 0;
      const old = d.res ? spring(d.res.x, d.res.v, d.res.k, d.res.z, at) : 0;
      // it keeps the speed it had (a numerical derivative of the same closed form is plenty)
      const speed = d.res ? (spring(d.res.x, d.res.v, d.res.k, d.res.z, at + 0.001) - old) / 0.001 : 0;
      d.res = { x: old + jump, v: speed, k: t.popStiffness, z: d.latched ? t.popDamping : 1, t: now };
      if (d.latched) lmaBuzz("tick");
    }
    const r = d.res ? spring(d.res.x, d.res.v, d.res.k, d.res.z, (now - d.res.t) / 1000) : 0;
    d.u = u;
    return -dir * clamp(shape(u, d.latched, isWall, t) + r, 0, 1) * w;
  }

  /** whether a release commits: [v] is the finger's speed in px/ms along dx (the page's own reading), [atEnd] as in move */
  function commits(d, v, w, atEnd) {
    const t = tune(), dir = d.dir || 1;
    if (atEnd(dir)) return false;
    const toward = -dir * v * 1000; // px/s toward the neighbour; a CSS px is about a dp
    if (toward <= -t.backFlingDp) return false;
    if (d.latched) return true;
    return toward >= t.flingDp && d.u >= t.flingMin;
  }

  /** the cancel's easing over k in 0..1: a critically damped spring's curve, to land exactly at 1 */
  const CANCEL_MS = 260;
  function settle(k) {
    const t = tune(), w = Math.sqrt(t.cancelK), s = (k * CANCEL_MS) / 1000, end = 1 - (1 + (w * CANCEL_MS) / 1000) * Math.exp((-w * CANCEL_MS) / 1000);
    return (1 - (1 + w * s) * Math.exp(-w * s)) / end;
  }

  /** the beta switch is on and the page's motion is not off: the swipe feels as native's does */
  const on = () => lmaLoad().nativeSwipe === true && !lmaMotionOff();

  window.SwipeFeel = { on, begin, move, commits, settle, tune, hold, wall, CANCEL_MS };
})();
