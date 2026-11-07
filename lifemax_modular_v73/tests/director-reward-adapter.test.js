'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm'), assert = require('assert/strict');
const base = process.env.DIRECTOR_SOURCE || path.join(__dirname, '../src/features/life-director');
const segments = path.join(__dirname, '../src/runtime/segments');
const runtime = process.env.REWARD_RUNTIME ? fs.readFileSync(process.env.REWARD_RUNTIME, 'utf8') :
  fs.readdirSync(segments).filter(n => n.endsWith('.js')).sort().map(n => fs.readFileSync(path.join(segments, n), 'utf8')).join('\n');
const engine = fs.readFileSync(path.join(base, 'engine.js'), 'utf8');
function declaration(name) {
  const start = runtime.indexOf('function ' + name + '(');
  assert(start >= 0, 'missing real function ' + name);
  let end = runtime.indexOf('\n', start);
  while (end >= 0) {
    const candidate = runtime.slice(start, end).trim();
    try { new vm.Script('(' + candidate + ')'); return candidate; } catch (_) {}
    end = runtime.indexOf('\n', end + 1);
  }
  throw Error('Cannot extract ' + name);
}
function constant(name) {
  const start = runtime.indexOf('const ' + name + '=');
  assert(start >= 0, 'missing real constant ' + name);
  return runtime.slice(start, runtime.indexOf(';', start) + 1);
}
const functions = ['ecoActsOn', 'ecoDur', 'ecoDayXP', 'ecoRestedPeek', 'ecoRestedTake', 'ecoCap', 'ecoAward', 'ecoQuestXP',
  'ecoPerk', 'ecoStars', 'ecoStarXP', 'ecoSparksSync', 'getToday', 'activityDate', 'questKey', 'isQuestDone', 'toggleQuest',
  'stageLvl', 'questLocked', 'characterLevel', 'totalXP', 'levelData', 'xpForLevel', 'rebuildAttrXP', 'rebuildSkillXP'];
const helpers = ['ECO', 'ECO_PERKS', 'charXpForLevel'].map(constant).concat(functions.map(declaration)).join('\n');
const copy = x => JSON.parse(JSON.stringify(x));
const now = Date.parse('2026-11-07T12:00:00Z');
class Clock extends Date { constructor(...args) { super(...(args.length ? args : [now])); } static now() { return now; } }
function setup(options = {}) {
  const ids = ['physique','endurance','intelligence','business','social','discipline','adventure','wellbeing'];
  const rule = { base: 20, primary: 'physique', secondary: 'discipline', skill: 'Fitness', icon: 'dumbbell' };
  const rules = Object.fromEntries(['custom','strength','reading','language','running','business','task','recovery','practical'].map(k => [k, copy(rule)]));
  const initial = { xpSchemaV2: true, activities: [], quests: [{ id: 'q', name: 'Walk', xp: 20, cadence: 'daily', stage: 1 }],
    questDone: {}, attrXP: Object.fromEntries(ids.map(id => [id,0])), skillXP: {}, restedXP: 100, sparks: 0,
    timerAuthority: { owner: 'NATIVE', epoch: 'e', highWater: 0, receipts: [], pending: null }, customRules: rules, ...copy(options) };
  let raw = JSON.stringify(initial), writes = 0, fault = null, toasts = 0, renders = 0;
  const ctx = vm.createContext({ Date: Clock, console, state: copy(initial), rules: copy(rules), STORE: 'game', saveReadOnly: false,
    ATTRS: ids.map(id => ({id})), localStorage: {
      getItem() { return raw; }, setItem(k, value) { writes++; if (fault === 'before') throw Error('quota'); raw = value; if (fault === 'after') throw Error('lost write ACK'); }
    }, toast() { toasts++; }, save() { return true; }, renderQuests() { renders++; },
    document: { getElementById() { return null; } },
  });
  vm.runInContext(helpers + '\nfunction checkBadges(silent){if(!silent) throw Error("badge UI during commit");state.badges={fixture:true};}\n' + engine + '\nthis.host=LifeMaxHost;', ctx);
  return { ctx, host: ctx.host, initial: copy(initial), get raw() { return raw; }, get writes() { return writes; },
    get toasts() { return toasts; }, get renders() { return renders; }, fault(x) { fault = x; } };
}
const event = { eventId: 'event-1', activityTypeTag: 'fitness', actualStartEpoch: now / 1000,
  actualDurationMinutes: 30, title: 'Brisk walk', lifecycleState: 'COMPLETED_ON_TIME' };
const activity = { kind: 'activity', event, title: 'Brisk walk' };
const quest = h => ({ kind: 'quest', hostRef: { questId: 'q', key: h.ctx.questKey(h.ctx.state.quests[0]) } });
let passed = 0;
function test(name, fn) { fn(); passed++; console.log('PASS ' + name); }
test('isolated activity reward exactly matches real legacy helper across economy cases', () => {
  const dated = (type, xp) => ({ type, xp, duration: 30, date: new Clock().toISOString(), sp: 1 });
  for (const options of [{}, { restedXP: 0 }, { attrStars: {physique: 2} }, { perk: 'exec', perksOwned:['exec'] },
    { activities: [dated('strength',20),dated('strength',20)] }, { activities: [dated('strength',20),dated('strength',20),dated('strength',20)] },
    { activities: [dated('custom',175)] }, { activities: [dated('custom',240)] }]) {
    const staged = setup(options), reference = setup(options), before = JSON.stringify(staged.ctx.state);
    const result = staged.host.prepareTimerEffect(staged.ctx.state, activity);
    reference.host.awardActivity(event, {title:'Brisk walk'});
    assert.deepEqual(copy(result), copy(reference.ctx.state));
    assert.equal(JSON.stringify(staged.ctx.state), before);
    assert.equal(staged.writes, 0); assert.equal(staged.toasts, 0);
  }
});
test('quest reward matches real helper without emitting a toast or modifying input', () => {
  for (const options of [{}, {restedXP:0}, {perk:'compound',perksOwned:['compound']}]) {
    const staged = setup(options), reference = setup(options), before = JSON.stringify(staged.ctx.state);
    const result = staged.host.prepareTimerEffect(staged.ctx.state, quest(staged));
    reference.host.completeQuest(quest(reference).hostRef);
    assert.deepEqual(copy(result), copy(reference.ctx.state));
    assert.equal(JSON.stringify(staged.ctx.state), before); assert.equal(staged.toasts,0); assert.equal(staged.renders,0);
  }
});
test('changed quest period refuses and restores globals', () => {
  const h=setup(), before=JSON.stringify(h.ctx.state), save=h.ctx.save, rules=h.ctx.rules, toast=h.ctx.toast;
  assert.throws(()=>h.host.prepareTimerEffect(h.ctx.state,{kind:'quest',hostRef:{questId:'q',key:'old-period'}}),/period changed/);
  assert.equal(JSON.stringify(h.ctx.state),before);assert.equal(h.ctx.save,save);assert.equal(h.ctx.rules,rules);assert.equal(h.ctx.toast,toast);
});
test('game writer commits reward, rebuilt totals, Sparks and receipt in one write',()=>{
  const h=setup(), store=h.host.createTimerGameStore(), input=store.readGame();
  const next=h.host.prepareTimerEffect(input,activity);next.timerAuthority.receipts.push({id:'e/2'});
  assert.equal(store.writeGame(next),true); assert.equal(h.writes,1);
  const saved=JSON.parse(h.raw);assert.equal(saved.activities.length,1);assert.equal(saved.timerAuthority.receipts[0].id,'e/2');
  assert.equal(saved.sparks,saved.activities[0].xp);assert.equal(saved.skillXP.Fitness,saved.activities[0].xp);
  assert.equal(h.toasts,0);assert.equal(h.renders,0);
});
for(const point of ['before','after'])test('uncertain '+point+' write reconciles from durable state',()=>{
  const h=setup(), store=h.host.createTimerGameStore(), before=JSON.stringify(h.ctx.state), next=h.host.prepareTimerEffect(store.readGame(),activity);
  next.timerAuthority.receipts.push({id:'e/2'});h.fault(point);
  assert.equal(store.writeGame(next),false);assert.equal(JSON.stringify(h.ctx.state),before);
  assert.throws(()=>store.readGame(),/Reconcile/);h.fault(null);store.reconcile();
  assert.equal(h.ctx.state.activities.length,point==='after'?1:0);
  assert.equal(JSON.stringify(h.ctx.state),h.raw);
});
test('read-only, dirty state and missing ownership fail closed',()=>{
  const h=setup(), store=h.host.createTimerGameStore(), next=store.readGame();
  h.ctx.saveReadOnly=true;assert.equal(store.writeGame(next),false);h.ctx.saveReadOnly=false;
  h.ctx.state.name='unsaved edit';assert.equal(store.writeGame(next),false);assert.throws(()=>store.readGame(),/clean/);assert.equal(h.writes,0);
  const legacy=setup({timerAuthority:null});assert.throws(()=>legacy.host.createTimerGameStore().readGame());
});
test('staged receiver retries real activity and quest rewards after lost game write ACK',()=>{
  const {createReceiver,createEffectCommitter}=require(process.env.COMPLETION_PROTOCOL || path.join(base,'timer-completion-protocol.js'));
  const h=setup(), gameStore=h.host.createTimerGameStore();
  let director={timerAuthority:{owner:'NATIVE',epoch:'e',highWater:0,receipts:[],pending:null},history:[]};
  const host=createEffectCommitter({readGame:()=>gameStore.readGame(),writeGame:s=>gameStore.writeGame(s),apply:(s,e)=>h.host.prepareTimerEffect(s,e)});
  const receiver=createReceiver({readDirector:()=>copy(director),writeDirector:s=>{director=copy(s);return true;},
    gameEpoch:()=>gameStore.readGame().timerAuthority.epoch,...host,
    plan(s,r){s.history.push(r.id);return {director:s,effects:[{...activity,id:'activity'},{...quest(h),id:'quest'}]};}});
  const request={epoch:'e',id:'e/2',sequence:2,activityId:'walk',generation:now,actualStartEpoch:now/1000,minutes:30};
  h.fault('after');assert.equal(receiver(request).status,'UNKNOWN');
  assert.equal(JSON.parse(h.raw).activities.length,1);
  h.fault(null);gameStore.reconcile();assert.equal(receiver(request).status,'COMMITTED');
  const expected=copy(h.ctx.state);assert.equal(expected.activities.length,2);
  assert.equal(receiver(request).status,'COMMITTED');assert.deepEqual(copy(h.ctx.state),expected);
  assert.deepEqual(director.history,['e/2']);assert.equal(h.toasts,0);assert.equal(h.renders,0);
});
console.log(passed+' real reward adapter checks passed');
