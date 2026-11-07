'use strict';
const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict');
const base=process.env.DIRECTOR_SOURCE||path.join(__dirname,'../src/features/life-director');
const engine=fs.readFileSync(path.join(base,'engine.js'),'utf8'),logic=fs.readFileSync(path.join(base,'logic.js'),'utf8');
const sandbox={module:{exports:{}}};vm.runInNewContext(engine.slice(0,engine.indexOf('/* ── Life Director Store')),sandbox);
const E=sandbox.module.exports,U=E.util,z='America/New_York';
const ST={availability:{timezone:z,sleepRequirementMinutes:480,rules:[
 {id:'sleep',kind:'SLEEP',days:[0,1,2,3,4,5,6],startMin:23*60,durationMin:480},
 {id:'work',kind:'WORK',days:[1],startMin:9*60,durationMin:480,commuteMin:0,decompressMin:0}
]},prefs:{},fixedCommitments:[],schedule:{horizonStartEpoch:1,horizonEndEpoch:2,segments:[],scheduledInstances:[]}};
const a=logic.indexOf('  function unplannedSegments('),b=logic.indexOf('  const KIND_CLASS',a);assert(a>0&&b>a);
const ctx={E,U,ST};const api=vm.runInNewContext(logic.slice(a,b)+'\n({preview:unplannedSegments,blocks:dayBlocks})',ctx);
function day(key){const y=U.parseYmd?U.parseYmd(key):{y:+key.slice(0,4),mo:+key.slice(5,7),d:+key.slice(8,10)};return U.buildHorizon(U.zonedToEpoch(y.y,y.mo,y.d,0,z),1,z)[0];}
const before=JSON.stringify(ST),monday=day('2026-11-09');
const blocks=api.blocks(ST.schedule,monday,z);
assert(blocks.some(b=>b.kind==='WORK'));assert(blocks.some(b=>b.kind==='SLEEP'));
assert(!blocks.some(b=>b.kind==='MEAL'||b.kind==='ROUTINE'||b.inst),'availability must not invent scheduled activities');
assert(!api.blocks(ST.schedule,day('2026-11-10'),z).some(b=>b.kind==='WORK'),'honor weekday work rules');
const fall=day('2026-11-01');assert.equal(fall.endEpoch-fall.startEpoch,25*3600);
const sleep=api.preview(fall,z).filter(b=>b.kind==='SLEEP');assert(sleep.length>0);
assert(sleep.every(b=>b.startEpoch>=fall.startEpoch&&b.endEpoch<=fall.endEpoch),'clip DST day at local midnight');
assert.equal(JSON.stringify(ST),before,'preview must not mutate saved state');
ST.availability.rules=[{id:'night',kind:'WORK',days:[1],startMin:22*60,durationMin:480,commuteMin:0,decompressMin:0},
 {id:'day-sleep',kind:'SLEEP',days:[0,1,2,3,4,5,6],startMin:8*60,durationMin:480}];
const tuesday=day('2026-11-10'),overnight=api.preview(tuesday,z).filter(b=>b.kind==='WORK');
assert(overnight.some(b=>b.startEpoch===tuesday.startEpoch&&U.localParts(b.endEpoch,z).h===6),'include previous day overnight work');
ST.fixedCommitments=[{id:'appointment',kind:'APPOINTMENT',title:'Dentist',startEpoch:tuesday.startEpoch+18*3600,endEpoch:tuesday.startEpoch+19*3600}];
assert(api.preview(tuesday,z).some(b=>b.refId==='appointment'),'include saved fixed commitments');
const planned={...ST.schedule,horizonStartEpoch:monday.startEpoch,horizonEndEpoch:monday.endEpoch,segments:[{kind:'WORK',startEpoch:monday.startEpoch,endEpoch:monday.startEpoch+3600,title:'Saved band'}]};
ST.schedule=planned;assert(api.blocks(planned,monday,z).some(b=>b.title==='Saved band'),'planned days keep original schedule');
assert(logic.includes('This day hasn\'t been planned yet'));
console.log('PASS unplanned sleep/work availability, weekday/overnight rules, DST, one-off commitments, no generated tasks or save mutations, planned-day parity');
