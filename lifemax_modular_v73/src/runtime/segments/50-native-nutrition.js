/* Native Nutrition 1:1 Compose integration: real web state is authoritative; no fixture data or duplicate saves. */
(function () {
  const NativeTabs = (typeof window !== 'undefined' && window.NativeTabs) || { cover() {}, cancel() {}, leave(id, to, fn) { fn(); }, flush() {}, register() {}, heal() {} };
  const N = {
    plugin: null,
    open: false,
    opening: false,
    session: '',
    ready: null,
    queued: false,
    blocked: false,
    updating: false,
    seam: false, // a fixture or a test is driving the sheet without a native screen
    dirty: false, // an update was asked for while one was in flight: send another when it is done
    generation: 0,
    notice: null, // the page's last message ({id, text, action}) and what its action does
    noticeFn: null,
    noticeId: 0,
    answer: null, // the last write's answer ({op, ok, changed, message}), sent with the updates after it
    done: [] // the operations that landed lately: the same one again is answered "Already recorded." and not done twice
  };
  const ALREADY = 'Already recorded.';
  /** the sheet's writes: each carries an operation id (op) from the screen and is answered */
  const WRITES = ['sheetLog', 'sheetRowAdd', 'sheetQuickSave', 'sheetFav', 'sheetCreateSave', 'sheetGoalSave', 'sheetEditSave', 'sheetEditDelete', 'sheetEditAgain', 'noticeAction'];

  /* The add-food sheet, drawn by the native screen from this state. The rules are the page's (fv2* in 50-food.js: the search, the
     steps, the macros, the logging); this holds only what the sheet is showing, since the page's own sheet is not open. */
  const Q = { open: false, view: 'search', slot: 'lunch', q: '', tab: 'recent', list: [], serial: 0, searching: false, pick: null, grams: 100, stack: [],
    edit: null, // the entry the edit view changes
    goal: null, // the daily targets' boxes, as typed on the screen
    forms: false }; // the screen draws create, the targets and the edit view itself (else they hand over to the page's sheet)

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
      shop: buildShopSnapshot(day),
      sheet: sheetData(),
      sheetForms: true, // this page runs create, the targets and the edit view inside the native sheet
      notice: N.notice ? { id: N.notice.id, text: N.notice.text, action: N.notice.action } : null,
      answer: N.answer
    };
    if (JSON.stringify(data).length > 245000) return null;
    return data;
  }

  function sheetRow(f, i, q) {
    const d = fv2RowData(f, q || !f._grams ? '' : 'last ' + f._grams + ' g');
    return { index: i, id: String(f.id || i), name: String(d.name || 'Food'), detail: d.detail, grams: d.grams, kcal: Math.round(d.kcal), p: d.p, c: d.c, f: d.f };
  }

  function sheetPortion() {
    const f = Q.pick, s = scaleFood(f, Q.grams), ed = Q.view === 'edit' ? Q.edit : null, w = fv2SheetWords();
    return {
      name: String(f.n || 'Food'),
      sub: [f.brand, f.cat].filter(Boolean).join(' · ') || 'Food',
      fav: fv2IsFav(f),
      grams: Q.grams,
      kcal: Math.round(s.kcal),
      small: { fib: +s.fib || 0, sug: +s.sug || 0, na: Math.round(+s.na || 0) },
      servings: fv2ServingsData(f, Q.grams),
      mults: FV2_MULTS.map(function (x) { return { x: x, label: fv2MultLabel(x) }; }),
      impact: fv2ImpactData(f, Q.grams, ed),
      logLabel: fv2LogLabel(s, !!ed, Q.slot),
      // an entry being edited: its id, and its Remove and Log today (only for an entry of another day), as the page's edit view
      edit: ed ? { id: String(ed.id), remove: w.remove, again: ed.date !== getToday() ? w.again : null } : null
    };
  }

  function sheetData() {
    if (!Q.open) return null;
    const none = Q.view === 'search' && !Q.list.length && !Q.searching ? fv2NoneWords(Q.q.trim(), Q.tab) : null;
    const head = fv2HeadData(Q.view), w = fv2SheetWords();
    return {
      view: Q.view,
      title: head.title,
      back: head.back, // the head's button steps back (else it closes)
      slot: Q.slot,
      slots: fv2Slots().map(function (x) { return { id: x[0], label: x[1] }; }),
      query: Q.q,
      tab: Q.tab,
      tabs: fv2TabsData().map(function (x) { return { id: x[0], label: x[1] }; }),
      words: { search: w.search, quick: w.quick, create: w.create, searching: w.searching, back: w.back, close: w.close, less: w.less, more: w.more, grams: w.grams, fav: w.fav, kcal: w.kcal },
      searching: Q.searching,
      rows: Q.view === 'search' ? Q.list.map(function (f, i) { return sheetRow(f, i, Q.q.trim()); }) : [],
      empty: none ? { title: none[0], text: none[1] } : null,
      portion: (Q.view === 'portion' || Q.view === 'edit') && Q.pick ? sheetPortion() : null,
      quick: Q.view === 'quick' ? fv2QuickData(Q.slot) : null,
      create: Q.view === 'create' ? fv2CreateData() : null,
      goal: Q.view === 'goal' && Q.goal ? fv2GoalView(Q.goal) : null
    };
  }

  function sheetList() {
    Q.list = fv2TabFoods(Q.tab);
  }

  function sheetClose() {
    Q.open = false;
    Q.serial++;
    Q.searching = false;
    Q.pick = null;
    Q.list = [];
    Q.stack = [];
    Q.edit = null;
    Q.goal = null;
  }

  /** Runs a write of the sheet's once per operation: fn does it and says whether it was saved. The answer goes with the next
      update, ok only when the save succeeded, with the page's words if it said something; the same operation again (an answer
      lost while the screen was away) is answered "Already recorded." without doing it twice. */
  function write(e, fn) {
    const op = typeof e.op === 'string' ? e.op : '';
    if (op && N.done.indexOf(op) >= 0) {
      N.answer = { op: op, ok: true, changed: false, message: ALREADY };
      if (window.__fv2Notice) window.__fv2Notice(ALREADY);
      return;
    }
    const said = N.noticeId;
    const saved = !!fn();
    N.answer = { op: op, ok: saved, changed: saved, message: N.noticeId !== said && N.notice ? N.notice.text : '' };
    if (saved && op) {
      N.done.push(op);
      if (N.done.length > 40) N.done.shift();
    }
  }

  /** whether the amount controls apply: a food is on show at an amount */
  function amounts() {
    return !!Q.pick && (Q.view === 'portion' || Q.view === 'edit');
  }

  /** the edit view's entry, if it is the one the screen means */
  function editing(e) {
    return Q.view === 'edit' && Q.edit && String(Q.edit.id) === String(e.id) ? Q.edit : null;
  }

  /** opens the sheet on a view with nothing picked */
  function sheetStart(view, slot) {
    Q.open = true;
    Q.view = view;
    Q.slot = fv2Slots().some(function (x) { return x[0] === slot; }) ? slot : fv2Slot();
    Q.q = '';
    Q.pick = null;
    Q.edit = null;
    Q.goal = null;
    Q.stack = [];
    Q.list = [];
    Q.searching = false;
    Q.serial++;
  }

  function sheetGo(view) {
    Q.stack.push(Q.view);
    Q.view = view;
  }

  /** the food a row stands for, if the list still has it there */
  function sheetFood(e) {
    const f = Q.list[Number(e.index)];
    return f && String(f.id || e.index) === String(e.id) ? f : null;
  }

  function sheetQuery(text) {
    Q.q = String(text || '').slice(0, 80);
    const toks = fv2SearchTokens(Q.q), ser = ++Q.serial;
    if (!toks.length) {
      Q.searching = false;
      sheetList();
      return;
    }
    const local = fv2LocalFoods(toks);
    Q.list = local;
    Q.searching = true;
    fv2UsdaFoods(toks, local).then(function (more) {
      if (ser !== Q.serial || !Q.open) return;
      Q.searching = false;
      if (more.length) Q.list = local.concat(more);
      scheduleSync();
    });
  }

  /** an action of the add-food sheet; true when it was one */
  function onSheet(command, e) {
    const isWrite = WRITES.indexOf(command) >= 0;
    if (command === 'sheetOpen') {
      sheetStart('search', e.slot);
      Q.forms = e.forms === true;
      Q.tab = fv2Recents(1).length ? 'recent' : (fv2Favs().length ? 'fav' : 'recent');
      sheetList();
    } else if (command === 'sheetEntry') {
      // a logged entry, in the edit view (the page's fv2OpenEntry)
      const en = state.foodLog.find(function (x) { return x.id === e.id; });
      if (en) {
        sheetStart('edit', en.slot);
        Q.forms = true;
        Q.slot = en.slot;
        Q.edit = en;
        Q.pick = fv2FoodOf(en);
        Q.grams = +en.grams || 100;
      }
    } else if (command === 'sheetGoal') {
      sheetStart('goal', null);
      Q.forms = true;
      Q.goal = fv2GoalDraft();
    } else if (isWrite && typeof e.op === 'string' && e.op && N.done.indexOf(e.op) >= 0) {
      write(e, function () { return false; }); // a repeat of a write that landed, whatever the sheet shows now
    } else if (!Q.open) {
      if (isWrite) write(e, function () { return false; }); // a write to a sheet that is not open changes nothing
      return command.indexOf('sheet') === 0; // an action of a sheet that is not open: nothing to do
    } else if (command === 'sheetQuery') {
      sheetQuery(e.q);
    } else if (command === 'sheetTab') {
      if (['recent', 'fav', 'mine'].indexOf(e.tab) >= 0) {
        Q.tab = e.tab;
        Q.q = '';
        Q.serial++;
        Q.searching = false;
        sheetList();
      }
    } else if (command === 'sheetPick') {
      const f = sheetFood(e);
      if (f) {
        Q.pick = f;
        Q.grams = f._grams || fv2Unit(f).g;
        sheetGo('portion');
      }
    } else if (command === 'sheetRowAdd') {
      write(e, function () {
        const f = sheetFood(e);
        if (!f) return false;
        const grams = f._grams || fv2Unit(f).g, slot = Q.slot;
        sheetClose();
        return !!fv2Log(f, grams, slot);
      });
    } else if (command === 'sheetStep') {
      if (amounts()) Q.grams = fv2StepGrams(Q.grams, Number(e.dir) < 0 ? -1 : 1);
    } else if (command === 'sheetGrams') {
      if (amounts()) Q.grams = Math.max(0, Math.min(99999, Number(e.grams) || 0));
    } else if (command === 'sheetServing') {
      // only a serving the view offers
      if (amounts() && fv2ServingsData(Q.pick, Q.grams).some(function (x) { return x.grams === Number(e.grams); })) Q.grams = Number(e.grams);
    } else if (command === 'sheetMult') {
      if (amounts() && FV2_MULTS.indexOf(Number(e.x)) >= 0) Q.grams = fv2MultGrams(Q.pick, Number(e.x));
    } else if (command === 'sheetSlot') {
      if (fv2Slots().some(function (x) { return x[0] === e.slot; })) Q.slot = e.slot;
    } else if (command === 'sheetFav') {
      write(e, function () {
        if (Q.view !== 'portion' || !Q.pick) return false;
        const was = fv2IsFav(Q.pick);
        fv2ToggleFav(Q.pick);
        fv2Hap('tap');
        return fv2IsFav(Q.pick) !== was;
      });
    } else if (command === 'sheetQuick') {
      if (Q.view === 'search') sheetGo('quick');
    } else if (command === 'sheetQuickSave') {
      write(e, function () {
        if (Q.view !== 'quick') return false;
        const kcal = Math.round(Number(e.kcal) || 0);
        if (kcal <= 0) {
          fv2Snack('Enter the calories');
          return false;
        }
        const food = fv2QuickFood(kcal, String(e.name || '').slice(0, 60)), slot = Q.slot;
        sheetClose();
        return !!fv2Log(food, 100, slot);
      });
    } else if (command === 'sheetLog') {
      write(e, function () {
        if (!Q.pick || !(Q.grams > 0) || Q.view !== 'portion') return false;
        const food = Q.pick, grams = Q.grams, slot = Q.slot;
        sheetClose();
        return !!fv2Log(food, grams, slot);
      });
    } else if (command === 'sheetCreate') {
      if (Q.forms) {
        if (Q.view === 'search') sheetGo('create');
      } else {
        // a screen that does not draw the create form: it steps aside and the page's own create form opens
        const slot = Q.slot;
        sheetClose();
        fallback('add', { slot: slot, view: 'create' });
      }
    } else if (command === 'sheetCreateSave') {
      // the boxes' text comes with the action (name, brand, serving, kcal, p, c, f); the page makes the food as its own Save does
      write(e, function () {
        if (Q.view !== 'create') return false;
        const v = {};
        fv2CreateData().fields.forEach(function (x) { v[x.key] = typeof e[x.key] === 'string' ? e[x.key] : ''; });
        const f = fv2CreateFood(v);
        if (!f) return false; // the screen's form stays filled in
        Q.pick = f;
        Q.grams = fv2Unit(f).g;
        Q.view = 'portion';
        Q.stack = ['search'];
        fv2Hap('success');
        return true;
      });
    } else if (command === 'sheetGoalField') {
      if (Q.view === 'goal' && Q.goal && ['cal', 'p', 'c', 'f', 'water'].indexOf(e.key) >= 0) Q.goal[e.key] = String(e.value == null ? '' : e.value).slice(0, 9);
    } else if (command === 'sheetGoalPreset') {
      if (Q.view === 'goal' && Q.goal && FV2_PRESETS.some(function (x) { return x[0] === e.preset; })) {
        const g = fv2PresetGrams(e.preset, Q.goal.cal);
        Q.goal.p = String(g.p);
        Q.goal.c = String(g.c);
        Q.goal.f = String(g.f);
      }
    } else if (command === 'sheetGoalSave') {
      // the boxes as they are on the screen come with Save, so a box typed a moment ago is not lost
      write(e, function () {
        if (Q.view !== 'goal' || !Q.goal) return false;
        ['cal', 'p', 'c', 'f', 'water'].forEach(function (k) { if (typeof e[k] === 'string') Q.goal[k] = e[k].slice(0, 9); });
        if (!fv2SetGoals(Q.goal)) return false;
        sheetClose();
        renderMeals();
        fv2Hap('success');
        return true;
      });
    } else if (command === 'sheetEditSave') {
      write(e, function () {
        const en = editing(e);
        if (!en || !fv2EditEntry(en, Q.pick, Q.grams, Q.slot)) return false;
        sheetClose();
        return true;
      });
    } else if (command === 'sheetEditDelete') {
      write(e, function () {
        const en = editing(e);
        if (!en || !fv2RemoveEntry(en)) return false;
        sheetClose();
        return true;
      });
    } else if (command === 'sheetEditAgain') {
      write(e, function () {
        const en = editing(e);
        if (!en || en.date === getToday() || !fv2LogAgain(en)) return false;
        sheetClose();
        return true;
      });
    } else if (command === 'sheetBack') {
      if (Q.stack.length) Q.view = Q.stack.pop();
      else sheetClose();
    } else if (command === 'sheetClose') {
      sheetClose();
    } else {
      return false;
    }
    return true;
  }

  /** the page said something to the player (a saved food, a refusal, Undo): the native screen shows it */
  window.__fv2Notice = function (text, action, fn) {
    if (!N.open && !N.seam) return;
    N.notice = { id: ++N.noticeId, text: String(text || ''), action: action ? String(action) : '' };
    N.noticeFn = typeof fn === 'function' ? fn : null;
    if (N.open) scheduleSync();
  };

  async function ready(p) {
    if (!N.ready) {
      N.ready = (async function () {
        await p.addListener('action', onAction);
        await p.addListener('nav-action', function (e) {
          window.NativeOverviewSnapshot?.activate(e.id);
        });
        await p.addListener('swipe', function (e) {
          // a committed swipe: the page decides; one from a screen session that has ended is answered as stale
          window.NativeSwipe?.request('meals', p, e, e?.session === N.session);
        });
        await p.addListener('closed', function (e) {
          if (e.session === N.session) {
            N.open = false;
            N.opening = false;
            N.session = '';
            N.blocked = true;
            N.notice = null;
            N.noticeFn = null;
            N.answer = null;
            sheetClose();
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
    N.notice = null;
    N.noticeFn = null;
    N.answer = null;
    sheetClose();
    if (p && id) {
      try {
        await p.close({ session: id });
      } catch (e) {
        console.warn('NativeNutrition close', e);
      }
    }
  }

  /** what the page shows over Meals that the screen must not cover */
  const COVERED = '.sheet-ov.show,#view-life-director.active .ld-sheet.open,.updpop,#onboarding.show';

  async function open() {
    // an open that does not go ahead lets a swipe that waited for this screen show the page's copy at once
    const notNow = () => { NativeTabs.flush('meals'); window.NativeSwipe?.entered('meals', N.open); };
    if (N.open || N.opening || N.blocked || !enabled() || view() !== 'meals') {
      if (!N.opening) notNow();
      return;
    }
    if (document.querySelector(COVERED)) {
      notNow();
      return;
    }
    const p = plugin();
    if (!p) {
      notNow();
      return;
    }
    const openedAt = performance.now();
    const s = snapshot();
    const projectionAt = performance.now();
    if (!s) {
      console.warn('NativeNutrition: authoritative snapshot unavailable');
      notNow();
      return;
    }
    const generation = ++N.generation;
    N.opening = true;
    N.plugin = p;
    let again = false; // cancelled while opening and wanted again meanwhile: open once more when this attempt has cleaned up
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
      await p.open(Object.assign({ session: id, snapshot: s, chromeScene }, window.NativeSwipe?.takeEnter('meals'))); // a swipe's slide-in
      const nativeAt = performance.now();
      window.__nativeTabPerf = {feature:'food',projectionMs:projectionAt-openedAt,capabilitiesMs:capabilitiesAt-projectionAt,chromeMs:chromeAt-capabilitiesAt,nativeMs:nativeAt-chromeAt,totalMs:nativeAt-openedAt,chromeCache:NativeOverviewSnapshot.diagnostics()};
      if (N.generation !== generation || N.session !== id || view() !== 'meals' || !enabled()) {
        await p.close({ session: id });
        again = true;
        return;
      }
      if (document.querySelector(COVERED)) {
        // a sheet came up while the screen was opening (Recipes' "Log this recipe" during a hand-over): it stays on top
        await p.close({ session: id });
        return;
      }
      N.open = true;
      N.blocked = false;
      NativeTabs.flush('meals');
      setTimeout(scheduleSync, 160); /* settled bar, in case a scene from the previous tab got there late */
    } catch (e) {
      N.open = false;
      N.session = '';
      console.warn('Native Nutrition open failed', e);
    } finally {
      if (N.generation === generation) N.opening = false;
      NativeTabs.flush('meals');
      window.NativeSwipe?.entered('meals', N.open);
      if (again && view() === 'meals' && enabled()) queueMicrotask(open);
    }
  }

  function scheduleSync() {
    if (!N.open || N.queued) return;
    if (N.updating) {
      N.dirty = true;
      return;
    }
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
        if (N.dirty) {
          N.dirty = false;
          scheduleSync();
        }
      }
    });
  }

  async function fallback(command, payload) {
    await close();
    if (command === 'plan' || command === 'shop' || command === 'log') {
      if (typeof fv2S === 'function') {
        fv2S().pane = command;
        if (typeof renderMeals === 'function') renderMeals();
      }
    } else if (command === 'add' || command === 'goals') {
      const slot = (payload && payload.slot) || (typeof fv2Slot === 'function' ? fv2Slot() : 'breakfast');
      if (typeof fv2Open === 'function') {
        fv2Open(command === 'add' ? slot : null, command === 'goals' ? 'goal' : (payload && payload.view) || 'search');
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
      if (command === 'noticeAction') {
        write(e, function () {
          if (!(N.notice && N.notice.id === e.id && N.noticeFn)) return false;
          const fn = N.noticeFn, said = N.noticeId;
          N.noticeFn = null; // once: a second tap on the same Undo does nothing
          fn();
          // the page's Undo says nothing when it lands, and "could not save" when it does not
          return !(N.noticeId !== said && N.notice && N.notice.text === unsavedMessage());
        });
        scheduleSync();
        return;
      }
      if (typeof command === 'string' && command.indexOf('sheet') === 0) {
        if (onSheet(command, e)) scheduleSync();
        return;
      }
      if (command === 'recipes') {
        // the Meals/Recipes switch: navigate's hand-over closes this screen once Recipes shows, at once if Recipes is the page
        lmaNavTo('recipes', { fast: true });
        return;
      } else if (command === 'pane') {
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
    enabled: enabled,
    /** run an action of the add-food sheet as the native screen would (the fixtures and the tests drive it this way) */
    sheetAct: function (command, payload) {
      N.seam = true;
      try {
        return onSheet(command, payload || {});
      } finally {
        N.seam = false;
      }
    }
  };

  /** Android Back, from the page's lmaBack: a native sheet that is open takes it, one view at a time */
  window.NativeFoodSheet = {
    open: function () { return N.open && Q.open; },
    back: function () {
      onSheet('sheetBack', {});
      scheduleSync();
    }
  };

  const nav = navigate;
  navigate = function (destination) {
    const result = nav.apply(this, arguments);
    if (destination !== 'meals' && (N.open || N.opening)) NativeTabs.leave('meals', destination, () => close());
    if (destination === 'meals') {
      NativeTabs.cancel('meals');
      N.blocked = false;
      queueMicrotask(open);
    }
    if (destination === 'settings') syncSettings();
    return result;
  };

  NativeTabs.cover('meals', () => !!plugin() && enabled());
  NativeTabs.register('meals', {
    isOpen: () => N.open,
    isOpening: () => N.opening,
    open: () => {
      N.blocked = false; // asked for because Meals is the current view
      return open();
    },
    close,
    // the screen would open over Meals if the page went there now
    wouldOpen: () => !!plugin() && enabled() && !!state.onboardingComplete && !lmaTopOverlay() && !document.body.classList.contains('noscroll'),
  });

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
