'use strict';
/* A shift that crosses midnight is one solid arc on the dial: the little air the dial leaves between neighbouring bands is not left
   at the midnight edge where the band carries on in the next (or the last) day. */
const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict');
const base=process.env.DIRECTOR_SOURCE||path.join(__dirname,'../src/features/life-director');
const engine=fs.readFileSync(path.join(base,'engine.js'),'utf8'),logic=fs.readFileSync(path.join(base,'logic.js'),'utf8');
const sandbox={module:{exports:{}}};vm.runInNewContext(engine.slice(0,engine.indexOf('/* ── Life Director Store')),sandbox);
const E=sandbox.module.exports,U=E.util,z='America/New_York';
const ST={availability:{timezone:z,sleepRequirementMinutes:480,rules:[
 {id:'night',kind:'WORK',days:[0,1,2,3,4,5,6],startMin:18*60+30,durationMin:630,commuteMin:0,decompressMin:0},
 {id:'sleep',kind:'SLEEP',days:[0,1,2,3,4,5,6],startMin:8*60,durationMin:480}]},prefs:{},fixedCommitments:[],schedule:{horizonStartEpoch:1,horizonEndEpoch:2,segments:[],scheduledInstances:[]}};
const a=logic.indexOf('  function unplannedSegments('),b=logic.indexOf('  const KIND_CLASS',a);assert(a>0&&b>a);
const g0=logic.indexOf('  function gap('),g1=logic.indexOf('\n',logic.indexOf('const crossesMidnight'));assert(g0>0&&g1>g0);
const ctx={E,U,ST};
const api=vm.runInNewContext(logic.slice(a,b)+'\n'+logic.slice(g0,g1)+'\n({blocks:dayBlocks,gap,crossesMidnight})',ctx);
const ymd=k=>({y:+k.slice(0,4),mo:+k.slice(5,7),d:+k.slice(8,10)});
const day=k=>{const p=ymd(k);return U.buildHorizon(U.zonedToEpoch(p.y,p.mo,p.d,0,z),1,z)[0];};
const angle=(t,d)=>(t-d.startEpoch)/(d.endEpoch-d.startEpoch)*360;
const outer=(d)=>api.blocks(ST.schedule,d,z).filter(x=>x.kind==='WORK').map(x=>{
  const [k0,k1]=api.crossesMidnight(x,d.startEpoch,d.endEpoch);
  const [a0,a1]=api.gap(angle(x.startEpoch,d),angle(x.endEpoch,d),0.9,k0,k1);return {a0,a1,k0,k1};});
const fri=day('2026-11-13'),arcs=outer(fri).sort((p,q)=>p.a0-q.a0);
assert.equal(arcs.length,2,'a shift across midnight is two pieces on the day it spans');
assert.equal(arcs[0].a0,0,'the morning piece starts exactly at midnight');
assert.equal(arcs[1].a1,360,'the evening piece ends exactly at midnight');
assert(arcs[0].k0&&!arcs[0].k1&&!arcs[1].k0&&arcs[1].k1,'only the midnight ends are kept whole');
assert(arcs[0].a1<angle(fri.startEpoch+5*3600,fri),'the real end of the shift keeps its air');
assert(arcs[1].a0>angle(fri.startEpoch+18.5*3600,fri),'the real start of the shift keeps its air');
/* a band that merely begins at midnight (not carried over) keeps its air on both ends */
ST.availability.rules=[{id:'early',kind:'WORK',days:[0,1,2,3,4,5,6],startMin:0,durationMin:240,commuteMin:0,decompressMin:0}];
const early=outer(fri)[0];assert(early&&early.a0>0&&!early.k0,'a band that starts at midnight by itself is trimmed as before');
/* short bands are never trimmed */
assert.equal(JSON.stringify(api.gap(10,11,0.9,false,false)),'[10,11]');
console.log('PASS overnight work is one solid arc across midnight; true ends keep their air; midnight-starting bands unchanged');
