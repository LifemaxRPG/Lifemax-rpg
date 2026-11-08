'use strict';
const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict');
const s=fs.readFileSync(process.env.DIRECTOR_LOGIC||path.join(__dirname,'../src/features/life-director/logic.js'),'utf8');
const a=s.indexOf('    timer({ act, id, start }) {'),b=s.indexOf('    /* Something came up:',a);
let writes=0,allow=true,available=true;
const old={id:'old',start:100,paused:200,pausedMs:0};
const ctx={ST:{ui:{focus:old}},Date:{now:()=>1000},plannedInst:id=>id==='new'?{lifecycleState:'PLANNED',startEpoch:0,endEpoch:1800}:null,
doable:()=>available,nowEpoch:()=>0,gone:{ok:false},persist:()=>{writes++;return allow;},render:()=>{},saved:()=>({ok:true}),recorded:()=>({ok:true})};
const timer=vm.runInNewContext('({'+s.slice(a,b)+'}).timer',ctx);
assert.equal(timer({act:'replace',id:'new',start:99}).ok,false);assert.equal(ctx.ST.ui.focus,old);
available=false;assert.equal(timer({act:'replace',id:'new',start:100}).ok,false);assert.equal(writes,0);
available=true;allow=false;assert.equal(timer({act:'replace',id:'new',start:100}).ok,false);assert.equal(ctx.ST.ui.focus,old);
allow=true;assert.equal(timer({act:'replace',id:'new',start:100}).ok,true);
assert.equal(ctx.ST.ui.focus.id,'new');assert.equal(ctx.ST.ui.focus.paused,null);assert.equal(ctx.ST.ui.focus.pausedMs,0);assert.equal(ctx.ST.ui.focus.plannedMs,1800000);
const count=writes;timer({act:'replace',id:'new',start:100});assert.equal(writes,count);
console.log('PASS replace from paused timer, target validation, stale confirmation, save rollback and duplicate delivery');
