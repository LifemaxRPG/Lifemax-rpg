/* Classic identity: what every Overview card draws, byte for byte (plans/overview.md, phase 0 step 2).
   Each card has a data function (the decisions: what shows, in which order, with which words) and an HTML function that draws
   from it, so a native drawing can read the same data and copy no rule. Splitting them must not change one byte of the page:
   every card's innerHTML is compared with tests/fixtures/overview-classic, captured before the split.
   States: the four golden saves, each on a fixed clock, and on top of them one cat (hungry, starving, away), two cats, a cat that
   has passed, health off and on (partway, goal reached, waiting for the first sync), app shortcuts (with and without icons, without
   a label, and none), luck rolled, over pace, shields and rested XP, an avatar, no class, decay off, daily quests done, a workout day (open
   and done) and a rest day, and a week day picked.
   Life Director's glance (Today's plan) is not here: its data split is step 3, after Life Director phase 2 rewrites it.
   Run: NODE_PATH=$(npm root -g) node tests/overview-classic.test.js [built html]
   Capture again only when a card is meant to change: OV_CAPTURE=1 node tests/overview-classic.test.js */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
const STORE = 'lifemax_rpg_prototype_v1', DIR = path.join(__dirname, 'fixtures', 'overview-classic'), CAPTURE = process.env.OV_CAPTURE === '1';
const golden = n => JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'saves', n + '.json'), 'utf8'));
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + String(x).slice(0, 900) : '')) } };

/* Each golden save on a clock that gives its cards something to draw: the established save the evening of its last activity,
   and the Sunday before it (the workout plan's rest day), the legacy save the morning after its last activity, the new and extras
   saves the day they were exported. */
const BASES = [
  { name: 'new', clock: '2026-10-30T12:00:00Z' },
  { name: 'established', clock: '2026-10-14T21:30:00Z' },
  { name: 'established-sunday', save: 'established', clock: '2026-10-11T12:00:00Z' }, // the workout plan's rest day
  { name: 'legacy', clock: '2024-08-01T09:00:00Z' },
  { name: 'extras', clock: '2026-10-30T12:00:00Z' },
];
/* States on top of a base, each set up from the base's own state and prefs (restored before the next).
   They run in the page; `now` is the fixed clock. Android states turn lmaNative on after load, the map's probe recipe. */
const ICON = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';
const cat = (name, o) => `Object.assign({v:1,name:${JSON.stringify(name)},coat:'ginger',trait:'curious',born:now-40*864e5,lastFed:now-3*36e5,meals:[],day:getToday(),fedToday:0,petsToday:0,playsToday:0,bondPts:0,care:0,vacation:{on:false,since:0},gifts:0,lastGiftDay:'',alive:true,passed:0,seen:{},greeted:0},${o || '{}'})`;
const EXTRAS = {
  established: {
    'pet': `state.pet=${cat('Miso', '{lastFed:now-20*36e5,bondPts:PET_BOND_AT[3]}')}`,
    'two-cats': `state.pet=${cat('Miso', '{bondPts:PET_BOND_AT[2]}')};state.pet2=${cat('Pip & Co', '{coat:"gray"}')}`,
    'pet-starving': `state.pet=${cat('Miso', '{lastFed:now-30*36e5,bondPts:PET_BOND_AT[5]}')}`,
    'pet-away': `state.pet=${cat('Miso', '{lastFed:now-40*36e5,vacation:{on:true,since:now-36e5}}')}`,
    'cat-gone': `state.pet=${cat('Miso', '{alive:false,passed:now-3*864e5}')}`,
    'stray-hidden': `state.petInviteHidden=true`,
    'luck': `const dice=[6,3,5,2];state.luck={date:getToday(),dice:dice,score:luckCompute(dice).score,ver:LUCK_VER}`,
    'over-pace': `state.activities=state.activities.concat([
        Object.assign({},state.activities[state.activities.length-1],{id:'ov1',name:'Long <b>ride</b>',date:new Date(now-20e3).toISOString(),xp:620,duration:180}),
        Object.assign({},state.activities[state.activities.length-2],{id:'ov2',date:new Date(now-3*36e5).toISOString(),xp:90,duration:0})])`,
    'rewards': `state.streakShields=2;state.restedXP=123.6;state.sparks=(state.sparks||0)+1234`,
    'avatar': `state.avatar=${JSON.stringify(ICON)};state.name='ada'`,
    'no-class': `state.classId=null;state.name='  '`,
    'plan-day': `state.plan={id:'athlete_foundation8',classId:'athlete',start:getToday(),from:getToday(),checks:{}}`,
    'plan-done': `state.plan={id:'athlete_foundation8',classId:'athlete',start:getToday(),from:getToday(),checks:{}};const s={};planDay(getToday()).items.forEach(i=>{s[i.k]=Array(i.n).fill(true)});state.plan.checks[getToday()]={s:s,v:{}}`,
    'quests-done': `state.quests.forEach(q=>{if(q.cadence==='daily')state.questDone[questKey(q)]=true})`,
    'decay-off': `state.decay={on:false}`,
    'all-done': `state.decay={on:false};state.petInviteHidden=true;state.quests.forEach(q=>{if(q.cadence==='daily')state.questDone[questKey(q)]=true});
        const dice=[2,2,4,4];state.luck={date:getToday(),dice:dice,score:luckCompute(dice).score,ver:LUCK_VER};
        state.plan={id:'athlete_foundation8',classId:'athlete',start:getToday(),from:getToday(),checks:{}};const s={};planDay(getToday()).items.forEach(i=>{s[i.k]=Array(i.n).fill(true)});state.plan.checks[getToday()]={s:s,v:{}}`,
    'week-picked': `OV_SEL=3`,
    'android-health-off': `lmaNative=()=>true;lmaHealthCfg().on=false;lmaLoad().apps=[]`,
    'android-health-on': `lmaNative=()=>true;Object.assign(lmaHealthCfg(),{on:true,goal:10000,src:'Samsung Health',last:now-7*6e4,
        today:{date:getToday(),count:6420},snap:{steps:[3100,12050,8000,0,9400,15000,6420].map((c,i)=>({date:new Date(now-(6-i)*864e5).toLocaleDateString('en-CA'),count:c})),
        ex:{count:2,min:95},sleep:{min:452},wt:181.4}});
        lmaLoad().apps=[{pkg:'com.example.one',label:'Maps & more',icon:${JSON.stringify(ICON)}},{pkg:'com.example.two',label:'Two'}]`,
    'android-health-goal': `lmaNative=()=>true;Object.assign(lmaHealthCfg(),{on:true,goal:5000,src:'',last:now-26*36e5,today:{date:getToday(),count:5000},snap:{steps:[{date:getToday(),count:5000}],ex:{count:0,min:0}}})`,
    'android-health-waiting': `lmaNative=()=>true;Object.assign(lmaHealthCfg(),{on:true,last:0,today:{date:'2020-01-01',count:999},snap:{}})`,
    'android-apps-unlabelled': `lmaNative=()=>true;lmaLoad().apps=[{pkg:'a.b'},{pkg:'c.d',label:null,icon:${JSON.stringify(ICON)}}]`, // stored with no label
  },
  'established-sunday': {
    'plan-rest': `state.plan={id:'athlete_foundation8',classId:'athlete',start:'2026-10-05',from:'2026-10-05',checks:{}};
        state.decay={on:false};state.quests.forEach(q=>{if(q.cadence==='daily')state.questDone[questKey(q)]=true})`, // so the rest day makes the cut
  },
  new: {
    'luck': `const dice=[1,1,1,1];state.luck={date:getToday(),dice:dice,score:luckCompute(dice).score,ver:LUCK_VER}`,
    'android': `lmaNative=()=>true`,
  },
  extras: {
    'android': `lmaNative=()=>true`,
  },
};

/* what every card shows: each card drawn afresh (the tiles' memo cleared) from the state as it is */
const read = () => {
  const q = id => document.getElementById(id), shown = id => { const t = q(id).closest('.ov-tile'); return t.style.display };
  ['ovHero', 'ovNext', 'ovAttrs', 'ovWeek', 'ovLuck', 'ovRecent'].forEach(id => { q(id).__h = null });
  renderOverview(); // Classic's own cards, then the tiles later segments wrap it with: health, apps, rewards
  const peek = q('petPeek'); peek.dataset.stamp = ''; renderPetPeek(); // read at once, before the cat's runtime fills its mood line
  return {
    head: { date: q('ovDate').textContent, hello: q('ovHello').textContent, edit: q('ovEditBtn').outerHTML, crest: q('ovCrest').innerHTML, solo: q('ovDuo').classList.contains('solo') },
    hero: q('ovHero').innerHTML, next: q('ovNext').innerHTML, rewards: q('ovRewards').innerHTML,
    apps: { shown: shown('ovApps'), html: q('ovApps').innerHTML }, health: { shown: shown('ovHealth'), html: q('ovHealth').innerHTML },
    attrs: q('ovAttrs').innerHTML, week: q('ovWeek').innerHTML, cat: peek.innerHTML, luck: q('ovLuck').innerHTML, recent: q('ovRecent').innerHTML,
  };
};
/* the week's line under the bars for each day picked; the day picked goes back to today after */
const tips = () => { const out = []; for (let i = 0; i < 7; i++) { ovPick(i); out.push(document.getElementById('ovTip').innerHTML) } ovPick(6); return out };

async function open(b, base) {
  const g = golden(base.save || base.name), ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await p.clock.setFixedTime(new Date(base.clock));
  await p.addInitScript(([STORE, save, extras]) => {
    localStorage.setItem(STORE, save); for (const k in extras) localStorage.setItem(k, extras[k]);
  }, [STORE, JSON.stringify({ ...g.state, tipsSeen: { ...(g.state.tipsSeen || {}), dashboard: true } }), g.extras || {}]);
  await p.goto('file://' + file, { timeout: 120000 });
  await p.waitForFunction(() => window.__ovReady && typeof ovEditOpen === 'function', null, { timeout: 60000 });
  await p.evaluate(() => { lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { } }); // the one-off walk through every tab
  await p.waitForTimeout(2000); // the boot timers: the theme and the late tiles' first draw
  await p.evaluate(() => {
    if (window.AppsShortcuts) AppsShortcuts.loadIcons = () => Promise.resolve({}); // no launcher to ask for icons
    window.__base = { state: JSON.stringify(state), prefs: JSON.stringify(lmaLoad()), native: lmaNative };
  });
  return p;
}
const restore = () => {
  const B = window.__base, s = JSON.parse(B.state), pr = JSON.parse(B.prefs), P = lmaLoad();
  Object.keys(state).forEach(k => delete state[k]); Object.assign(state, s);
  Object.keys(P).forEach(k => delete P[k]); Object.assign(P, pr);
  lmaNative = B.native; OV_SEL = 6;
};

function firstDiff(a, b) { let i = 0; while (i < a.length && a[i] === b[i]) i++; return i }
function compare(name, got) {
  const f = path.join(DIR, name + '.json');
  if (CAPTURE) { fs.mkdirSync(DIR, { recursive: true }); fs.writeFileSync(f, JSON.stringify(got, null, 1) + '\n'); console.log('CAPTURED ' + name); return }
  if (!fs.existsSync(f)) { ok(name + ': has a fixture', false, f); return }
  const want = JSON.parse(fs.readFileSync(f, 'utf8')), flat = (o, pre, out) => { for (const k in o) { const v = o[k], key = pre ? pre + '.' + k : k; if (v && typeof v === 'object') flat(v, key, out); else out[key] = v } return out };
  const W = flat(want, '', {}), G = flat(got, '', {}), keys = [...new Set([...Object.keys(W), ...Object.keys(G)])];
  const bad = keys.filter(k => W[k] !== G[k]);
  ok(name + ': every card is byte-identical to the capture (' + keys.length + ' values)', !bad.length, bad.map(k => {
    const w = String(W[k]), g = String(G[k]), i = firstDiff(w, g);
    return k + ' @' + i + ' want …' + w.slice(Math.max(0, i - 60), i + 80) + '… got …' + g.slice(Math.max(0, i - 60), i + 80) + '…';
  }).join(' | '));
}

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    for (const base of BASES) {
      const p = await open(b, base);
      compare(base.name, { clock: base.clock, cards: await p.evaluate(read), tips: await p.evaluate(tips) });
      for (const [extra, setup] of Object.entries(EXTRAS[base.name] || {})) {
        const got = await p.evaluate(([restore, setup, read]) => {
          (0, eval)('(' + restore + ')()');
          (0, eval)('(function(){const now=Date.now();' + setup + '})()');
          return (0, eval)('(' + read + ')()');
        }, [restore.toString(), setup, read.toString()]);
        compare(base.name + '-' + extra, { clock: base.clock, cards: got });
      }
      ok(base.name + ': no page errors', !p.errors.length, p.errors.join(' | '));
      await p.context().close();
    }
  } finally { await b.close() }
  console.log((CAPTURE ? 'captured' : pass + ' passed, ' + fail + ' failed'));
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1) });
