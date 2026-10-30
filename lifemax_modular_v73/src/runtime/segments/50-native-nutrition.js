/* Native Nutrition 1:1 Compose integration: real web state is authoritative; no fixture data or duplicate saves. */
(function () {
  const N = {
    plugin: null,
    open: false,
    opening: false,
    session: '',
    ready: null,
    queued: false,
    blocked: false,
    updating: false,
    generation: 0
  };

  function plugin() {
    try {
      const c = window.Capacitor;
      return c && c.isNativePlatform && c.isNativePlatform() &&
        c.isPluginAvailable && c.isPluginAvailable('NativeNutrition')
        ? c.Plugins.NativeNutrition
        : null;
    } catch (e) {
      return null;
    }
  }

  function enabled() {
    return typeof lmaLoad==='function' && lmaLoad().nativeNutrition===true;
  }

  function syncSettings() {
    const toggle = document.getElementById('nativeNutritionToggle');
    const hint = document.getElementById('nativeNutritionHint');
    const available = !!plugin();
    if (toggle) {
      toggle.checked = available && enabled();
      toggle.disabled = !available;
    }
    if (hint) {
      hint.textContent = available
        ? 'Opt-in native Food preview. Plan, Log and Shopping use your real data; unfinished actions open classic Food.'
        : 'Requires an Android APK with Native Nutrition support. Classic Food remains available.';
    }
  }

  window.setNativeNutritionEnabled = function (on) {
    if (typeof lmaLoad !== 'function' || typeof lmaSave !== 'function') return;
    const settings = lmaLoad();
    settings.nativeNutrition = !!on;
    lmaSave();
    if (!on) close();
    syncSettings();
    if (on && view() === 'meals') {
      N.blocked = false;
      open();
    }
  };

  function view() {
    return typeof lmaView === 'function' ? lmaView() : '';
  }

  function buildWeekStrip(day, g) {
    try {
      const d = new Date(day + 'T12:00:00');
      const dow = (d.getDay() + 6) % 7;
      const mon = typeof fv2DayShift === 'function' ? fv2DayShift(day, -dow) : day;
      const todayKey = typeof getToday === 'function' ? getToday() : day;
      const letters = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
      const out = [];
      for (let i = 0; i < 7; i++) {
        const k = typeof fv2DayShift === 'function' ? fv2DayShift(mon, i) : day;
        const es = dayFoodLog(k);
        const kc = fv2Tot(es).kcal;
        const fr = g.cal ? kc / g.cal : (kc ? 0.5 : 0);
        const status = g.cal && fr >= 0.9 && fr <= 1.08 ? 'ok' : (g.cal && fr > 1.08 ? 'bad' : 'under');
        const dd = new Date(k + 'T12:00:00');
        out.push({
          day: k,
          w: letters[i],
          dNum: dd.getDate(),
          kcal: Math.round(kc),
          frac: Math.max(0, Math.min(1, fr)),
          status: status,
          selected: k === day,
          today: k === todayKey
        });
      }
      return out;
    } catch (e) {
      return [];
    }
  }

  function buildPlanSnapshot(day) {
    try {
      if (typeof sdDay !== 'function') return null;
      const d = sdDay(day);
      if (!d) return null;
      const S = typeof sdS === 'function' ? sdS() : {};
      const D = typeof sdData === 'function' ? sdData() : { cat: {}, plans: {} };
      const co = typeof sdCoach === 'function' ? sdCoach(d) : { i: '✦', t: '' };
      const eatenP = dayFoodLog(day).reduce(function (a, e) { return a + (+e.p || 0); }, 0);
      const planP = d.items.reduce(function (a, i) { return a + (i.skipped ? 0 : i.tot.p); }, 0) + (d.extra ? d.extra.p : 0);
      const tagMap = { leftover: 'Leftover', vegan: 'Vegan', no_cook: 'No cooking', shake: 'Shake', quick: 'Quick' };

      const items = d.items.map(function (it, idx) {
        const m = it.meal || {};
        const t = it.skipped && typeof sdTotals === 'function' ? sdTotals(it.ings, d.s) : (it.tot || {});
        const tags = (m.tags || [])
          .filter(function (x) { return !!tagMap[x]; })
          .map(function (x) { return tagMap[x]; })
          .join(' · ');
        const factorFn = typeof sdFactor === 'function' ? sdFactor : function () { return d.s || 1; };
        const sortedIngs = (it.ings || []).slice().sort(function (a, b) {
          return b.g * factorFn(b, d.s) - a.g * factorFn(a, d.s);
        });
        const topNames = sortedIngs.slice(0, 4).map(function (x) {
          return (D.cat && D.cat[x.ing] && D.cat[x.ing].n) ? D.cat[x.ing].n : x.ing;
        }).join(' · ');
        const ingLines = (it.ings || []).map(function (x) {
          const grams = Math.round(x.g * factorFn(x, d.s));
          const name = (D.cat && D.cat[x.ing] && D.cat[x.ing].n) ? D.cat[x.ing].n : x.ing;
          return { name: String(name), grams: grams };
        });
        return {
          index: idx,
          id: String(m.id || idx),
          name: String(m.name || 'Planned meal'),
          slot: String(m.slot || 'breakfast'),
          slotName: typeof sdSlotName === 'function' ? sdSlotName(m.slot) : String(m.slot || 'Meal'),
          slotWhen: typeof sdSlotWhen === 'function' ? sdSlotWhen(m.slot) : 'Morning',
          slotColor: typeof sdSlotColor === 'function' ? sdSlotColor(m.slot) : '#f5b851',
          activeMin: Number(m.active_min) || 0,
          tagsLabel: tags || 'No cooking',
          kcal: Math.round(Number(t.kcal) || 0),
          p: Math.round(Number(t.p) || 0),
          c: Math.round(Number(t.c) || 0),
          f: Math.round(Number(t.f) || 0),
          fib: Math.round(Number(t.fib) || 0),
          topIngs: topNames,
          ings: ingLines,
          steps: Array.isArray(m.steps) ? m.steps.map(String) : [],
          logged: !!it.logged,
          skipped: !!it.skipped
        };
      });

      return {
        workoutHeading: String((d.w && d.w.note) || 'TODAY').toUpperCase() + ' · ' +
          ((d.w && d.w.type === 'training') ? 'TRAINING DAY' : 'REST DAY'),
        target: Math.round(Number(d.target) || 0),
        eaten: Math.round(Number(d.eaten) || 0),
        left: Math.max(0, Math.round((Number(d.target) || 0) - (Number(d.eaten) || 0))),
        eatenP: Math.round(eatenP),
        planP: Math.round(planP || 0),
        nextIndex: typeof d.next === 'number' ? d.next : -1,
        coachIcon: String(co.i || '✦'),
        coachText: String(co.t || '').replace(/<[^>]+>/g, '').replace(/&amp;/g, '&'),
        diet: String(S.diet || 'standard'),
        level: String(S.level || 'auto'),
        veg: !!S.veg,
        df: !!S.df,
        items: items
      };
    } catch (e) {
      return null;
    }
  }

  function buildShopSnapshot(day) {
    try {
      if (typeof sdShopList !== 'function' || typeof sdMonday !== 'function') return null;
      const S = typeof sdS === 'function' ? sdS() : { shop: {} };
      const U = typeof sdUi === 'function' ? sdUi() : {};
      const start = typeof fv2DayShift === 'function' ? fv2DayShift(sdMonday(day), U.next ? 7 : 0) : day;
      const chk = (S.shop && S.shop[start]) || {};
      const raw = sdShopList(start) || [];
      return {
        weekStart: start,
        nextWeek: !!U.next,
        items: raw.map(function (x) {
          return {
            id: String(x.id),
            name: String(x.n || x.id),
            aisle: String(x.aisle || 'pantry'),
            aisleName: typeof sdAisleName === 'function' ? sdAisleName(x.aisle) : String(x.aisle || 'Pantry'),
            grams: Math.round(Number(x.g) || 0),
            packs: Number(x.packs) || 1,
            unit: String(x.unit || 'pack'),
            meals: Number(x.meals) || 1,
            perish: !!x.perish,
            home: !!x.home,
            checked: !!chk[x.id]
          };
        })
      };
    } catch (e) {
      return null;
    }
  }

  function snapshot() {
    if (
      typeof dayFoodLog !== 'function' ||
      typeof mealsDayKey !== 'function' ||
      typeof fv2Goals !== 'function' ||
      typeof fv2Tot !== 'function' ||
      typeof fv2Water !== 'function'
    ) {
      return null;
    }
    const day = mealsDayKey();
    const entries = dayFoodLog(day);
    if (!day || !Array.isArray(entries)) return null;
    const t = fv2Tot(entries);
    const g = fv2Goals();
    if (!Number.isFinite(t.kcal) || !Number.isFinite(g.cal)) return null;

    const todayKey = typeof getToday === 'function' ? getToday() : day;
    const yestKey = typeof fv2DayShift === 'function' ? fv2DayShift(day, -1) : '';
    const yestEntries = yestKey ? dayFoodLog(yestKey) : [];
    const S = typeof fv2S === 'function' ? fv2S() : {};
    const activePane = S.pane || (typeof sdDay === 'function' && sdDay(day) ? 'plan' : 'log');
    const currentSlot = typeof fv2Slot === 'function' ? fv2Slot() : 'breakfast';
    const recentsRaw = typeof fv2Recents === 'function' ? fv2Recents(8) : [];
    const recents = recentsRaw.map(function (f, idx) {
      const grams = Number(f._grams) || 100;
      const kc = typeof fv2KcalFor === 'function' ? fv2KcalFor(f, grams) : Math.round((Number(f.kcal) || 0) * grams / 100);
      const sc = typeof scaleFood === 'function' ? scaleFood(f, grams) : { p: 0, c: 0, f: 0 };
      return {
        index: idx,
        id: String(f.id || idx),
        name: String(f.n || 'Food'),
        grams: grams,
        kcal: Math.round(kc),
        p: Number(sc.p) || 0,
        c: Number(sc.c) || 0,
        f: Number(sc.f) || 0
      };
    });

    const eyebrow = day === todayKey
      ? 'TODAY'
      : (yestKey && day === yestKey ? 'YESTERDAY' : 'VIEWING');

    const data = {
      version: 1,
      day: day,
      today: todayKey,
      eyebrow: eyebrow,
      dayLabel: new Date(day + 'T12:00:00').toLocaleDateString(undefined, {
        weekday: 'long',
        month: 'short',
        day: 'numeric'
      }),
      pane: activePane,
      currentSlot: currentSlot,
      totals: {
        kcal: t.kcal,
        p: t.p,
        c: t.c,
        f: t.f,
        fib: Number(t.fib) || 0,
        sug: Number(t.sug) || 0,
        na: Number(t.na) || 0
      },
      goals: {
        cal: g.cal,
        p: g.p,
        c: g.c,
        f: g.f,
        water: g.water
      },
      waterMl: fv2Water(day),
      light: document.documentElement.classList.contains('light-theme'),
      week: buildWeekStrip(day, g),
      recents: recents,
      copyYesterday: {
        breakfast: yestEntries.some(function (e) { return e.slot === 'breakfast'; }),
        lunch: yestEntries.some(function (e) { return e.slot === 'lunch'; }),
        dinner: yestEntries.some(function (e) { return e.slot === 'dinner'; }),
        snacks: yestEntries.some(function (e) { return e.slot === 'snacks'; })
      },
      entries: entries.map(function (e) {
        return {
          id: String(e.id || ''),
          slot: String(e.slot || 'snacks'),
          name: String(e.name || 'Food'),
          grams: Number(e.grams) || 100,
          kcal: Number(e.kcal) || 0,
          p: Number(e.p) || 0,
          c: Number(e.c) || 0,
          f: Number(e.f) || 0
        };
      }),
      plan: buildPlanSnapshot(day),
      shop: buildShopSnapshot(day)
    };
    if (JSON.stringify(data).length > 245000) return null;
    return data;
  }

  async function ready(p) {
    if (!N.ready) {
      N.ready = (async function () {
        await p.addListener('action', onAction);
        await p.addListener('nav-action', function (e) {
          window.NativeOverviewSnapshot?.activate(e.id);
        });
        await p.addListener('closed', function (e) {
          if (e.session === N.session) {
            N.open = false;
            N.opening = false;
            N.session = '';
            N.blocked = true;
          }
        });
      })().catch(function (e) {
        N.ready = null;
        throw e;
      });
    }
    return N.ready;
  }

  async function close() {
    N.generation++;
    const id = N.session;
    const p = N.plugin;
    N.open = false;
    N.opening = false;
    N.blocked = true;
    N.session = '';
    if (p && id) {
      try {
        await p.close({ session: id });
      } catch (e) {
        console.warn('NativeNutrition close', e);
      }
    }
  }

  async function open() {
    if (N.open || N.opening || N.blocked || !enabled() || view() !== 'meals') return;
    if (document.querySelector('.sheet-ov.show,.ld-sheet.open,.updpop,#onboarding.show')) return;
    const p = plugin();
    if (!p) return;
    const openedAt = performance.now();
    const s = snapshot();
    const projectionAt = performance.now();
    if (!s) {
      console.warn('NativeNutrition: authoritative snapshot unavailable');
      return;
    }
    const generation = ++N.generation;
    N.opening = true;
    N.plugin = p;
    try {
      await ready(p);
      const caps = N.caps || (N.caps = await p.capabilities());
      const capabilitiesAt = performance.now();
      if (!caps || caps.version !== 1 || !caps.authoritativeData) return;
      if (view() !== 'meals' || !enabled()) return;
      const id = 'nutrition_' + Date.now();
      N.session = id;
      if (!window.NativeOverviewSnapshot) throw new Error('Shared Overview chrome unavailable');
      const chromeScene = await NativeOverviewSnapshot.snapshot();
      const chromeAt = performance.now();
      await p.open({ session: id, snapshot: s, chromeScene });
      const nativeAt = performance.now();
      window.__nativeTabPerf = {feature:'food',projectionMs:projectionAt-openedAt,capabilitiesMs:capabilitiesAt-projectionAt,chromeMs:chromeAt-capabilitiesAt,nativeMs:nativeAt-chromeAt,totalMs:nativeAt-openedAt,chromeCache:NativeOverviewSnapshot.diagnostics()};
      if (N.generation !== generation || N.session !== id || view() !== 'meals' || !enabled()) {
        await p.close({ session: id });
        return;
      }
      N.open = true;
      N.blocked = false;
    } catch (e) {
      N.open = false;
      N.session = '';
      console.warn('Native Nutrition open failed', e);
    } finally {
      if (N.generation === generation) N.opening = false;
    }
  }

  function scheduleSync() {
    if (!N.open || N.queued || N.updating) return;
    N.queued = true;
    requestAnimationFrame(async function () {
      N.queued = false;
      if (!N.open || view() !== 'meals') return;
      const s = snapshot();
      if (!s) {
        close();
        return;
      }
      N.updating = true;
      try {
        await N.plugin.update({
          session: N.session,
          snapshot: s,
          chromeScene: await NativeOverviewSnapshot.snapshot()
        });
      } catch (e) {
        console.warn('Native Nutrition sync failed', e);
        close();
      } finally {
        N.updating = false;
      }
    });
  }

  async function fallback(command, payload) {
    await close();
    if (command === 'recipes') {
      navigate('recipes');
      return;
    }
    if (command === 'plan' || command === 'shop' || command === 'log') {
      if (typeof fv2S === 'function') {
        fv2S().pane = command;
        if (typeof renderMeals === 'function') renderMeals();
      }
    } else if (command === 'add' || command === 'goals') {
      const slot = (payload && payload.slot) || (typeof fv2Slot === 'function' ? fv2Slot() : 'breakfast');
      if (typeof fv2Open === 'function') {
        fv2Open(command === 'add' ? slot : null, command === 'goals' ? 'goal' : 'search');
      }
    } else if (command === 'entry' && payload && payload.id) {
      if (typeof fv2OpenEntry === 'function') fv2OpenEntry(payload.id);
    } else if (command === 'sdSwap' && payload && typeof payload.index === 'number') {
      if (typeof sdOpenSwap === 'function') sdOpenSwap(payload.index);
    }
  }

  function onAction(e) {
    if (!N.open || e.session !== N.session) return;
    const command = e.command;
    try {
      if (command === 'pane') {
        if (typeof fv2S === 'function' && e.pane) {
          fv2S().pane = e.pane;
          if (typeof renderMeals === 'function') renderMeals();
          scheduleSync();
          return;
        }
      } else if (command === 'previous' || command === 'next') {
        if (typeof setMealsDay !== 'function' || typeof fv2DayShift !== 'function') return fallback('web');
        setMealsDay(fv2DayShift(mealsDayKey(), command === 'previous' ? -1 : 1));
        if (typeof renderMeals === 'function') renderMeals();
        scheduleSync();
        return;
      } else if (command === 'selectDay' && e.day) {
        if (typeof setMealsDay !== 'function') return fallback('web');
        setMealsDay(String(e.day));
        if (typeof renderMeals === 'function') renderMeals();
        scheduleSync();
        return;
      } else if (command === 'today') {
        if (typeof mealsGoToday === 'function') mealsGoToday();
        if (typeof renderMeals === 'function') renderMeals();
        scheduleSync();
        return;
      } else if (command === 'water') {
        if (typeof fv2AddWater !== 'function') return fallback('web');
        const ml = Number(e.ml) || 250;
        fv2AddWater(ml);
        if (typeof renderMeals === 'function') renderMeals();
        scheduleSync();
        return;
      } else if (command === 'quickAdd') {
        if (typeof fv2Recents === 'function' && typeof fv2Log === 'function') {
          const rc = fv2Recents(8);
          const idx = Number(e.index) || 0;
          const f = rc[idx];
          if (f) {
            const slot = e.slot || (typeof fv2Slot === 'function' ? fv2Slot() : 'breakfast');
            fv2Log(f, f._grams || 100, slot);
            scheduleSync();
            return;
          }
        }
      } else if (command === 'copySlot' && e.slot) {
        if (typeof fv2CopySlot === 'function') {
          fv2CopySlot(String(e.slot));
          scheduleSync();
          return;
        }
      } else if (command === 'sdLog') {
        const day = mealsDayKey();
        const idx = Number(e.index) || 0;
        if (typeof sdS === 'function') {
          const S = sdS();
          if (S.skip && S.skip[day]) delete S.skip[day][idx];
        }
        if (typeof sdLogMeal === 'function') {
          sdLogMeal(day, idx);
          if (typeof fv2S === 'function') fv2S().pane = 'log';
          if (typeof renderMeals === 'function') renderMeals();
          scheduleSync();
          return;
        }
      } else if (command === 'sdSkip') {
        const day = mealsDayKey();
        const idx = Number(e.index) || 0;
        if (typeof sdS === 'function') {
          const S = sdS();
          S.skip = S.skip || {};
          S.skip[day] = S.skip[day] || {};
          S.skip[day][idx] = true;
          if (typeof save === 'function') save();
          if (typeof renderMeals === 'function') renderMeals();
          scheduleSync();
          return;
        }
      } else if (command === 'sdUnskip') {
        const day = mealsDayKey();
        const idx = Number(e.index) || 0;
        if (typeof sdS === 'function') {
          const S = sdS();
          if (S.skip && S.skip[day]) delete S.skip[day][idx];
          if (typeof save === 'function') save();
          if (typeof renderMeals === 'function') renderMeals();
          scheduleSync();
          return;
        }
      } else if (command === 'sdCheck' && e.itemId) {
        const day = mealsDayKey();
        if (typeof sdS === 'function' && typeof sdMonday === 'function' && typeof fv2DayShift === 'function') {
          const S = sdS();
          const U = typeof sdUi === 'function' ? sdUi() : {};
          const start = fv2DayShift(sdMonday(day), U.next ? 7 : 0);
          S.shop = S.shop || {};
          const c = (S.shop[start] = S.shop[start] || {});
          c[e.itemId] = !c[e.itemId];
          if (!c[e.itemId]) delete c[e.itemId];
          if (typeof save === 'function') save();
          if (typeof renderMeals === 'function') renderMeals();
          scheduleSync();
          return;
        }
      } else if (command === 'sdClearShop') {
        const day = mealsDayKey();
        if (typeof sdS === 'function' && typeof sdMonday === 'function' && typeof fv2DayShift === 'function') {
          const S = sdS();
          const U = typeof sdUi === 'function' ? sdUi() : {};
          const start = fv2DayShift(sdMonday(day), U.next ? 7 : 0);
          S.shop = S.shop || {};
          S.shop[start] = {};
          if (typeof save === 'function') save();
          if (typeof renderMeals === 'function') renderMeals();
          scheduleSync();
          return;
        }
      }
    } catch (err) {
      console.warn('Native Nutrition command error', err);
    }
    fallback(command, e);
  }

  window.NativeNutritionBeta = {
    snapshot: snapshot,
    open: open,
    close: close,
    enabled: enabled
  };

  const nav = navigate;
  navigate = function (destination) {
    const result = nav.apply(this, arguments);
    if (destination !== 'meals' && (N.open || N.opening)) close();
    if (destination === 'meals') {
      N.blocked = false;
      queueMicrotask(open);
    }
    if (destination === 'settings') syncSettings();
    return result;
  };

  if (typeof renderMeals === 'function') {
    const original = renderMeals;
    renderMeals = function () {
      const result = original.apply(this, arguments);
      scheduleSync();
      return result;
    };
  }

  if (typeof save === 'function') {
    const original = save;
    save = function () {
      const result = original.apply(this, arguments);
      scheduleSync();
      return result;
    };
  }

  syncSettings();
  setTimeout(syncSettings, 800);
})();
