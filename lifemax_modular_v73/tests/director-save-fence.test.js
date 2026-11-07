'use strict';
const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict');
const base=process.env.GUARD_SOURCE||path.join(__dirname,'../src');
const runtime=fs.readFileSync(path.join(base,'runtime/segments/04-runtime.js'),'utf8');
const engine=fs.readFileSync(path.join(base,'features/life-director/engine.js'),'utf8');
const begin=runtime.indexOf('function timerWriteFence('),end=runtime.indexOf('function save()',begin);
assert(begin>0&&end>begin);
const keys=['lifemax_rpg_prototype_v1','lifemax_director_state_v1'];
const data=new Map();let writes=0;
const storage={getItem:k=>data.get(k)||null,setItem(k,v){writes++;data.set(k,String(v));},removeItem:k=>data.delete(k)};
const context=vm.createContext({localStorage:storage,STORE:keys[0],saveReadOnly:false,state:{},console:{warn(){}},module:{exports:{}}});
vm.runInContext(runtime.slice(begin,end),context);
vm.runInContext(engine.slice(engine.indexOf('/* ── Life Director Store'),engine.indexOf('/* ── Life Director ⇄')),context);
const S=context.module.exports,store=S.create(storage),plain=S.defaultState({});
const metadata=()=>({owner:'NATIVE',epoch:'e',highWater:2,receipts:[{id:'e/2'}],pending:null});
function seed(){const game={timerAuthority:metadata(),activities:[]};const director={...plain,timerAuthority:metadata()};data.set(keys[0],JSON.stringify(game));data.set(keys[1],JSON.stringify(director));context.state=JSON.parse(JSON.stringify(game));writes=0;return director;}
let director=seed();assert.equal(context.writeSave(),true);assert.equal(store.save(director).ok,true);
for(const key of keys){director=seed();const pending=JSON.parse(data.get(key));pending.timerAuthority.pending={id:'e/4'};data.set(key,JSON.stringify(pending));
 const before=JSON.stringify([...data]);assert.equal(context.writeSave(),false);assert.equal(store.save(director).ok,false);assert.equal(store.clear({}).ok,false);assert.equal(writes,0);assert.equal(JSON.stringify([...data]),before);}
director=seed();context.state.timerAuthority.highWater=1;assert.equal(context.writeSave(),false);director.timerAuthority.highWater=1;assert.equal(store.save(director).ok,false);assert.equal(writes,0);
director=seed();const changed=JSON.parse(data.get(keys[1]));changed.timerAuthority.epoch='restored';data.set(keys[1],JSON.stringify(changed));assert.equal(context.writeSave(),false);assert.equal(store.save(director).ok,false);
director=seed();delete director.timerAuthority;assert.equal(store.save(director).ok,false);assert.equal(store.clear({}).ok,false);
data.clear();data.set(keys[1],'{legacy damage');context.state={activities:[]};assert.equal(context.writeSave(),true,'unrelated legacy recovery cannot block game saves');assert.equal(store.save(plain).ok,true);
data.clear();data.set(keys[0],JSON.stringify({timerAuthority:metadata()}));data.set(keys[1],'{unreadable native state');context.state={timerAuthority:metadata()};assert.equal(context.writeSave(),false);
console.log('PASS real game and Director saves: pending work, reset, stale receipts, epoch mismatch, legacy recovery, corrupt native save');
