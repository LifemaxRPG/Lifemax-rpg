/* Native Recipes, web half: the projection a native screen draws from (checked against the page's own Recipes page for a class
   with class recipes, one with Snack recipes and one without), when the screen covers the page, the prefill take-over (and an
   open that fails), no update after a close, "Log this recipe" handed to the page, going to Meals, and native Food's
   Meals/Recipes switch with native Recipes on and off.
   Fake NativeRecipes, NativeNutrition and NativeOverview plugins stand in for Android.
   Run: NODE_PATH=$(npm root -g) node tests/native-recipes.test.js */
const { chromium } = require('playwright');
const page = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') }); // lunch time, for the slot the add-food sheet picks
  await p.addInitScript(`
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'scholar'}));
    window.__calls=[];
    const mk=(name,caps)=>{const ls={};return {
      __emit:(e,d)=>Promise.all((ls[e]||[]).map(f=>f(d))),
      addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
      open:async a=>{window.__calls.push([name,'open',a])},
      update:async a=>{window.__calls.push([name,'update',a])},
      close:async a=>{window.__calls.push([name,'close',a,{sheet:!!document.querySelector('#fv2Ov.show')}]);(ls.closed||[]).forEach(f=>f({session:a&&a.session}))},
      capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true}),
      handshake:async()=>({protocol:1,capabilities:caps})}};
    window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>['NativeRecipes','NativeNutrition','NativeOverview'].includes(n),getPlatform:()=>'android',
      Plugins:{NativeRecipes:mk('recipes',['recipes.projection.v1']),NativeNutrition:mk('food',[]),NativeOverview:mk('overview',[])},registerPlugin:()=>({}),convertFileSrc:u=>u};
  `);
  await p.goto('file://' + page); await p.waitForTimeout(3000);
  const r = await p.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), out = {}, R = window.Capacitor.Plugins.NativeRecipes, F = window.Capacitor.Plugins.NativeNutrition;
    const calls = (name, k) => window.__calls.filter(c => c[0] === name && c[1] === k);
    const count = (name, k) => calls(name, k).length;
    const last = (name, k) => calls(name, k).pop();
    const at = (name, k) => window.__calls.lastIndexOf(last(name, k));
    const showing = name => !!last(name, 'open') && (!last(name, 'close') || at(name, 'open') > at(name, 'close'));
    const latest = () => [...window.__calls].reverse().find(c => c[0] === 'recipes' && (c[1] === 'open' || c[1] === 'update'))[2].snapshot;
    const until = async (f, ms = 5000) => { for (let t = 0; t < ms && !f(); t += 50) await wait(50) };
    const text = el => el ? el.textContent : null;
    const search = document.getElementById('recipeSearch'), select = document.getElementById('recipeCategory');
    lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { } // the one-off thumbnail walk visits every tab
    const S = lmaLoad(); S.nativeOverview = false; lmaSave();

    // off by default: the switch is there, off, and the classic page stays
    navigate('settings'); await wait(50);
    const toggle = document.getElementById('nativeRecipesToggle');
    out.offByDefault = !!toggle && !toggle.checked && !toggle.disabled && !NativeTabs.covered('recipes')
      && /Native Recipes \(beta\)/.test(toggle.closest('.settingsline').textContent);
    navigate('recipes'); await wait(600);
    out.classicWhenOff = count('recipes', 'open') === 0 && lmaView() === 'recipes';
    setNativeRecipesEnabled(true); navigate('settings'); await wait(50);
    out.switchOn = toggle.checked && lmaLoad().nativeRecipes === true && NativeTabs.covered('recipes');

    // the projection says what the page's own Recipes page says, recipe by recipe
    navigate('recipes'); await until(() => count('recipes', 'open')); // the first capture of the shared bars is slow headless
    const s = last('recipes', 'open')[2].snapshot;
    const page = () => [...document.querySelectorAll('#recipesList > article')].map(a => ({
      id: a.querySelector('button.btn').getAttribute('onclick').match(/addRecipeToMeal\('([^']+)'\)/)[1],
      category: text(a.querySelector('.eyebrow')), name: text(a.querySelector('h3')), meta: text(a.querySelector('h3 + .muted')), kcal: text(a.querySelector('.tag')),
      macros: [...a.querySelectorAll('.statvalue')].map(text), labels: [...a.querySelectorAll('.statlabel')].map(text),
      ingredients: [...a.querySelectorAll('.ingredient-pick')].map(text), steps: [...a.querySelectorAll('ol li')].map(text),
      note: text(a.querySelector('details > p.muted')) || '', footnote: text(a.querySelector(':scope > p.muted')),
    }));
    const drawn = rs => rs.map(x => ({
      id: x.id, category: x.category, name: x.name, meta: x.meta, kcal: x.kcal, macros: [x.protein, x.carbs, x.fat], labels: ['Protein', 'Carbs', 'Fat'],
      ingredients: x.ingredients.map(i => i.text + ' ' + (i.link ? '🛒' : '＋')), steps: x.steps, note: x.note, footnote: x.footnote,
    }));
    const compare = (snap) => { const web = page(), nat = drawn(snap.recipes), diff = web.map((w, i) => JSON.stringify(w) === JSON.stringify(nat[i]) ? null : [w, nat[i]]).filter(Boolean); return { same: web.length === nat.length && !diff.length, web: web.length, native: nat.length, diff: diff[0] } };
    const scholar = compare(s);
    out.scholar = s.protocol === 1 && s.recipes.length === 19 && s.recipes.filter(x => /\(est\.\)$/.test(x.kcal)).length === 9 && scholar.same;
    out.scholarDetail = scholar;
    out.categories = JSON.stringify(s.categories) === JSON.stringify([...select.options].map(o => ({ value: o.value, label: o.textContent })))
      && s.categories.map(c => c.label).join('|') === 'All recipes|Breakfast|Pre-workout|Post-workout|Lunch & dinner';
    out.startFilter = JSON.stringify(s.filter) === JSON.stringify({ query: '', category: 'all', revision: 1 });
    selectRecipeIngredient('rcp_pesto_chicken', 0, null);
    const href = document.querySelector('#recipeProduct-rcp_pesto_chicken a').getAttribute('href'), pesto = s.recipes.find(x => x.id === 'rcp_pesto_chicken');
    out.links = pesto.ingredients[0].link === href && /^https:\/\/www\.walmart\.com\//.test(href)
      && s.recipes.flatMap(x => x.ingredients).filter(i => i.link).length === 1;
    out.searchText = s.recipes.every(x => x.searchText === [x.name, x.category, ...x.ingredients.map(i => i.text)].join(' ').toLowerCase());
    const saved = localStorage.getItem(STORE), held = JSON.stringify(state);
    NativeRecipesTabProjection.snapshot(); // synchronous, so nothing else can write in between
    out.noWrites = localStorage.getItem(STORE) === saved && JSON.stringify(state) === held;

    // the page's own search rule, applied to the projection, finds exactly what the page shows
    const filter = (snap, q, c) => { q = q.trim().toLowerCase(); return snap.recipes.filter(x => (c === 'all' || x.category === c) && (!q || x.searchText.includes(q))).map(x => x.id) };
    const cases = [['', 'all'], ['chicken', 'all'], ['  CHICKEN ', 'all'], ['chicken rice', 'all'], ['breakfast 12 eggs', 'all'], ['breakfast', 'all'], ['lunch & dinner', 'all'], ['preheat', 'all'], ['½', 'all'], ['', 'Breakfast'], ['oats', 'Breakfast'], ['zzz', 'all']];
    const parity = cases.map(([q, c]) => { search.value = q; select.value = c; renderRecipes(); const web = page().map(x => x.id); return { q, c, web, native: filter(s, q, c) } });
    search.value = ''; select.value = 'all'; renderRecipes();
    out.parity = parity.every(x => JSON.stringify(x.web) === JSON.stringify(x.native)) && parity.some(x => x.web.length === 0) && parity.find(x => x.q === 'preheat').web.length === 0;
    out.parityDetail = parity.filter(x => JSON.stringify(x.web) !== JSON.stringify(x.native));

    // the class decides the recipes, and the open screen follows a class change
    switchClassTo('entrepreneur'); await until(() => latest().recipes.length === 18, 2000);
    const ent = latest();
    out.entrepreneur = ent.recipes.length === 18 && ent.recipes.filter(x => x.category === 'Snack').length === 2 && !ent.categories.some(c => c.value === 'Snack') && compare(ent).same;
    switchClassTo('disciple'); await until(() => latest().recipes.length === 10, 2000);
    const dis = latest();
    out.disciple = dis.recipes.length === 10 && dis.recipes.every(x => !/\(est\.\)/.test(x.kcal)) && compare(dis).same;
    switchClassTo('scholar'); await until(() => latest().recipes.length === 19, 2000);

    // a command Recipes does not have is refused and the screen stays
    await R.__emit('command', { command: 'save', payload: {}, requestId: 'c1' }); await wait(150);
    const refused = calls('recipes', 'update').map(c => c[2].reply).filter(Boolean).find(x => x.requestId === 'c1');
    out.noCommands = !!refused && refused.ok === false && refused.message === 'Unknown command' && showing('recipes');

    // the diet card's Recipe button: the page writes the name into its search and comes here; the screen takes it over once
    navigate('meals'); await wait(300);
    out.leavesForClassicMeals = !showing('recipes');
    fv2S().pane = 'log'; renderMeals(); // the diet card is on Meals' Log pane
    const recipeButton = () => document.querySelector('#dietPlanWrap .diet-menu-item button[onclick^="dietOpenRecipe"]');
    const asked = RECIPES.find(x => x.id === recipeButton().getAttribute('onclick').match(/'([^']+)'/)[1]), opens = count('recipes', 'open');
    recipeButton().click(); await until(() => count('recipes', 'open') > opens);
    const pre = last('recipes', 'open')[2].snapshot;
    out.prefill = JSON.stringify(pre.filter) === JSON.stringify({ query: asked.n, category: 'all', revision: 2 }) && filter(pre, asked.n, 'all').includes(asked.id)
      && search.value === '' && select.value === 'all' && page().length === 19;
    navigate('dashboard'); await wait(300); navigate('recipes'); await until(() => count('recipes', 'open') > opens + 1);
    out.keepsOwn = last('recipes', 'open')[2].snapshot.filter.revision === 2;
    navigate('meals'); await wait(300); recipeButton().click(); await until(() => count('recipes', 'open') > opens + 2);
    out.prefillAgain = last('recipes', 'open')[2].snapshot.filter.revision === 3;
    navigate('meals'); await wait(300); select.value = 'Breakfast'; navigate('recipes'); await until(() => count('recipes', 'open') > opens + 3);
    const cat = last('recipes', 'open')[2].snapshot.filter;
    out.prefillCategory = cat.query === '' && cat.category === 'Breakfast' && cat.revision === 4 && select.value === 'all';
    // an open that fails leaves the diet card's request on the page, and the screen takes it over when it does open
    navigate('meals'); await wait(300);
    const openScreen = R.open;
    R.open = async () => { throw Error('no screen') };
    recipeButton().click(); await wait(300);
    out.failedOpen = lmaView() === 'recipes' && !showing('recipes') && search.value === asked.n && page().map(x => x.id).includes(asked.id) && page().length < 19;
    R.open = openScreen; await until(() => showing('recipes')); // NativeTabs.heal opens it again
    const retried = last('recipes', 'open')[2].snapshot.filter;
    out.failedOpenRetried = retried.query === asked.n && retried.revision > 4 && search.value === '' && page().length === 19;

    // a hidden screen gets no updates, not even a refresh whose capture of the bars was still running when the screen closed
    navigate('dashboard'); await wait(300);
    const updates = count('recipes', 'update'); renderRecipes(); save(); await wait(400);
    out.hiddenQuiet = !showing('recipes') && count('recipes', 'update') === updates;
    navigate('recipes'); await until(() => showing('recipes'));
    const capture = NativeOverviewSnapshot.snapshot;
    NativeOverviewSnapshot.snapshot = async function () { await wait(300); return capture.apply(this, arguments) };
    renderRecipes(); await wait(150); navigate('dashboard'); await wait(500);
    NativeOverviewSnapshot.snapshot = capture;
    out.noLateUpdate = !showing('recipes') && at('recipes', 'update') < at('recipes', 'close');

    // "Log this recipe": the page opens its add-food sheet with one serving over Meals, and the screen closes; it stays away
    navigate('recipes'); await until(() => showing('recipes'));
    const logged = state.foodLog.length, before = count('recipes', 'open');
    const reply = () => calls('recipes', 'update').map(c => c[2].reply).filter(Boolean).pop();
    await R.__emit('command', { command: 'log', payload: { id: 'rcp_pesto_chicken' }, requestId: 'l1' }); await wait(800);
    const sh = fv2S().sh;
    out.log = !showing('recipes') && lmaView() === 'meals' && !!document.querySelector('#fv2Ov.show') && sh.view === 'portion' && sh.pick.id === 'rcp_pesto_chicken'
      && sh.grams === 230 && sh.slot === 'lunch' && state.foodLog.length === logged && count('recipes', 'open') === before;
    fv2Close(); navigate('recipes'); await until(() => showing('recipes'));
    const closes = count('recipes', 'close');
    await R.__emit('command', { command: 'log', payload: { id: 'nope' }, requestId: 'l2' }); await wait(800);
    out.logUnknown = lmaView() === 'recipes' && showing('recipes') && count('recipes', 'close') === closes && reply().requestId === 'l2' && reply().ok === false
      && !document.querySelector('#fv2Ov.show') && state.foodLog.length === logged;

    // the Meals pill and "＋ Log a meal" name the view: classic Meals at once, or native Food first and then Recipes closes
    window.__calls.length = 0;
    await R.__emit('action', { view: 'meals' }); await wait(300);
    out.mealsClassic = lmaView() === 'meals' && !showing('recipes') && count('food', 'open') === 0;
    S.nativeNutrition = true; lmaSave();
    navigate('recipes'); await until(() => showing('recipes') && !showing('food'));
    window.__calls.length = 0;
    await R.__emit('action', { view: 'meals' }); await until(() => showing('food') && !showing('recipes'));
    out.mealsNative = lmaView() === 'meals' && at('food', 'open') >= 0 && at('food', 'open') < at('recipes', 'close') && count('recipes', 'close') === 1;
    out.mealsNativeDetail = window.__calls.map(c => c[0] + ' ' + c[1]);
    // "Log this recipe" with native Food on: Food steps aside for the sheet, and the screen is asked to close only once the sheet shows
    navigate('recipes'); await until(() => showing('recipes') && !showing('food'));
    window.__calls.length = 0;
    await R.__emit('command', { command: 'log', payload: { id: 'rcp_pesto_chicken' }, requestId: 'l3' }); await until(() => !showing('recipes'));
    out.logOverFood = lmaView() === 'meals' && !showing('food') && count('recipes', 'close') === 1 && last('recipes', 'close')[3].sheet;
    out.logOverFoodDetail = window.__calls.map(c => c[0] + ' ' + c[1]);
    fv2Close(); lmaNavTo('meals', { fast: true }); await until(() => showing('food'));

    // native Food's Meals/Recipes switch hands over to native Recipes, which opens before Food closes
    const session = () => last('food', 'open')[2].session; // the screen ignores an action from a session it no longer shows
    let food = session();
    window.__calls.length = 0;
    await F.__emit('action', { session: food, command: 'recipes' }); await until(() => showing('recipes') && !showing('food'));
    out.switchNative = lmaView() === 'recipes' && state.foodPane === 'recipes' && at('recipes', 'open') < at('food', 'close') && count('food', 'close') === 1;
    out.switchNativeDetail = window.__calls.map(c => c[0] + ' ' + c[1]);
    // "Food" now lands on Recipes, natively
    navigate('dashboard'); await until(() => !showing('recipes'));
    lmaNavTo('food', { fast: true }); await until(() => showing('recipes'));
    out.foodTab = lmaView() === 'recipes' && showing('recipes') && !showing('food');

    // with native Recipes off, the switch closes Food at once and the classic page shows
    lmaNavTo('meals', { fast: true }); await until(() => showing('food') && !showing('recipes'));
    await setNativeRecipesEnabled(false);
    food = session();
    window.__calls.length = 0;
    await F.__emit('action', { session: food, command: 'recipes' }); await wait(800);
    out.switchClassic = lmaView() === 'recipes' && !showing('food') && count('recipes', 'open') === 0 && count('food', 'close') === 1;
    navigate('dashboard'); await wait(300); lmaNavTo('food', { fast: true }); await wait(800);
    out.foodTabClassic = lmaView() === 'recipes' && count('recipes', 'open') === 0 && !showing('food');
    return out;
  });
  ok('Settings shows Native Recipes (beta), off by default; the classic page stays while it is off', r.offByDefault && r.classicWhenOff, r);
  ok('turning it on covers Recipes', r.switchOn, r);
  ok('a Scholar\'s projection is every recipe the page shows, worded as the page prints it', r.scholar, r.scholarDetail);
  ok('the categories are the page\'s own list', r.categories, r);
  ok('the screen starts from an empty search in all recipes', r.startFilter, r);
  ok('the product link is the page\'s, on Quick Pesto Chicken\'s chicken breast only', r.links, r);
  ok('each recipe carries the text the page searches', r.searchText, r);
  ok('projecting changes nothing in the page\'s state or the save', r.noWrites, r);
  ok('the page\'s search rule applied to the projection finds exactly what the page shows', r.parity, r.parityDetail);
  ok('an Entrepreneur\'s two Snack recipes are in the list, with no Snack category; the open screen follows the class change', r.entrepreneur, r);
  ok('a Disciple sees the ten cookbook recipes, none estimated', r.disciple, r);
  ok('a command Recipes does not have is refused and the screen stays', r.noCommands, r);
  ok('leaving for classic Meals closes the screen', r.leavesForClassicMeals, r);
  ok('a prefill from the diet card is taken over on open, and the page\'s own search is emptied', r.prefill, r);
  ok('coming back without a prefill keeps the screen\'s own search (revision unchanged)', r.keepsOwn, r);
  ok('the same prefill again is taken over again', r.prefillAgain, r);
  ok('a category the page chose is taken over too', r.prefillCategory, r);
  ok('an open that fails leaves the prefill on the page', r.failedOpen, r);
  ok('the screen takes that prefill over when it does open, and the page\'s fields are emptied then', r.failedOpenRetried, r);
  ok('a hidden screen gets no updates', r.hiddenQuiet, r);
  ok('a refresh still capturing the bars when the screen closes is not sent', r.noLateUpdate, r);
  ok('Log this recipe: the page\'s add-food sheet opens with one serving over Meals and the screen closes, nothing is logged yet', r.log, r);
  ok('Log for a recipe the page does not know: the page stays on Recipes, the screen stays, the reply says not ok', r.logUnknown, r);
  ok('going to Meals with native Food off: the screen closes, classic Meals', r.mealsClassic, r);
  ok('going to Meals with native Food on: Food opens before Recipes closes', r.mealsNative, r.mealsNativeDetail);
  ok('Log this recipe with native Food on: Food steps aside for the sheet, and Recipes closes only once the sheet shows', r.logOverFood, r.logOverFoodDetail);
  ok('native Food\'s Recipes switch: native Recipes opens before Food closes', r.switchNative, r.switchNativeDetail);
  ok('Food then lands on native Recipes', r.foodTab, r);
  ok('with native Recipes off, Food\'s switch closes Food at once and shows the classic page', r.switchClassic, r);
  ok('with native Recipes off, Food lands on the classic page', r.foodTabClassic, r);
  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
