'use strict';
const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict');
const base=process.env.DIRECTOR_SOURCE||path.join(__dirname,'../src/features/life-director');
const engine=fs.readFileSync(path.join(base,'engine.js'),'utf8'),logic=fs.readFileSync(path.join(base,'logic.js'),'utf8');
const e={module:{exports:{}}};vm.runInNewContext(engine.slice(0,engine.indexOf('/* ── Life Director Store')),e);const E=e.module.exports,U=E.util;
const zone='America/New_York',start=U.zonedToEpoch(2026,11,9,0,zone),now=start+8*3600;
const fresh=()=>({playerId:'p',setupComplete:true,prefs:{},availability:{timezone:zone,sleepRequirementMinutes:480,rules:[{id:'sleep',kind:'SLEEP',days:[0,1,2,3,4,5,6],startMin:23*60,durationMin:480}]},fixedCommitments:[],schedule:{horizonStartEpoch:start,horizonEndEpoch:start+7*86400,scheduledInstances:[],historyInstances:[]}});
let canSave=true,writes=0;
const ctx={ST:fresh(),E,U,CUSTOM_TAGS:[['fitness','Fitness']],NO_OP:'Missing operation',dayMinute:x=>Number.isInteger(Number(x))&&Number(x)>=0&&Number(x)<1440?Number(x):null,
 tz:()=>zone,nowEpoch:()=>now,refuse:message=>({ok:false,message}),recorded:message=>({ok:true,changed:false,message}),applied:()=>({ok:true,changed:true}),render(){},say(){},persist(){writes++;return canSave;}};
vm.createContext(ctx);
const a=logic.indexOf('    activityAtAdd({'),b=logic.indexOf('    goalAdd({',a);assert(a>0&&b>a);
const api=vm.runInContext('({'+logic.slice(a,b)+'})',ctx);
const input={op:'stable-operation',title:'Brisk walk',tag:'fitness',date:'2026-11-09',start:15*60,minutes:30};
assert.equal(api.activityAtAdd(input).ok,true);assert.equal(ctx.ST.schedule.scheduledInstances.length,1);
const instance=ctx.ST.schedule.scheduledInstances[0];assert.equal(instance.startEpoch,U.zonedToEpoch(2026,11,9,15*60,zone));assert.equal(instance.endEpoch-instance.startEpoch,1800);assert(instance.isUserLocked);
const regenerated=E.generateSchedule({timezone:zone,currentTimeEpoch:now,horizonStartEpoch:now,horizonDays:7,playerAvailability:ctx.ST.availability,fixedCommitments:[],goals:[],activeQuests:[],existingSchedule:[instance],playerPreferences:{}});
assert(regenerated.scheduledInstances.some(i=>i.instanceId===instance.instanceId&&i.startEpoch===instance.startEpoch),'pinned activity survives replanning');
assert.equal(api.activityAtAdd(input).changed,false);assert.equal(ctx.ST.schedule.scheduledInstances.length,1);assert.equal(writes,1,'replay does not save or duplicate');
assert.equal(api.activityAtAdd({...input,op:'overlap'}).ok,false);
for(const patch of [{start:23*60},{date:'2027-11-09'},{start:8*60-15},{start:901},{date:'2026-02-31'},{minutes:7},{title:''}]){
 ctx.ST=fresh();const before=JSON.stringify(ctx.ST);assert.equal(api.activityAtAdd({...input,...patch}).ok,false);assert.equal(JSON.stringify(ctx.ST),before);
}
ctx.ST=fresh();const before=JSON.stringify(ctx.ST);canSave=false;assert.equal(api.activityAtAdd(input).ok,false);assert.equal(JSON.stringify(ctx.ST),before,'storage failure restores full state');canSave=true;
ctx.ST=fresh();ctx.ST.availability.rules.push({id:'work',kind:'WORK',days:[1],startMin:14*60,durationMin:180});assert.equal(api.activityAtAdd(input).ok,false);
console.log('PASS schedule-at-time: selected local time, pinned instance, stable operation, sleep/work/activity conflicts, validation, outside-plan refusal and storage rollback');
