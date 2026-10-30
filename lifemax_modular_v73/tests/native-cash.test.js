/* Regression coverage for the shared money commands and the native data boundary.
   Run: node tests/native-cash.test.js */
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const root=path.join(__dirname,'..');const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const money=read('src/runtime/segments/05-runtime.js');
const handlers={},updates=[],stored=new Map();let view='dashboard',opened,failStorage=false,available=true;
const s={console,Date,Intl,Math,Map,Set,JSON,Number,String,Promise,
 state:{activities:[],attrXP:{},skillXP:{},money:{account:'Original',start:100,goal:500,tx:[],customField:'preserve me'}},
 ECO:{depositMin:10,depositCapDay:50,depositCapWeek:100},ecoCap:n=>n,ecoRestedTake:n=>n,
 activityDate:a=>String(a.date||'').slice(0,10),getToday:()=>new Date().toISOString().slice(0,10),
 parseD:d=>new Date(d+'T00:00:00'),toast:()=>{},confirm:()=>true,
 setTimeout:()=>0,clearTimeout:()=>{},setInterval:()=>0,
 document:{getElementById:()=>null,querySelector:()=>null,addEventListener(){},documentElement:{classList:{contains:()=>false}}},
 fxOpt:()=>({haptics:true}),lmaMotionOff:()=>false,lmaView:()=>view,lmaResolve:v=>v,
 lmaTabList:()=>[{dataset:{view:'money'},textContent:'Cash'},{dataset:{view:'dashboard'},textContent:'Overview'}],
 navigate:v=>{view=v},lmaBack:()=>{view='dashboard'},lmaNavTo:v=>{view=v},
 localStorage:{getItem:k=>stored.get(k),setItem(k,v){if(failStorage)throw Error('Storage full');stored.set(k,v)}},STORE:'test',saveReadOnly:'',
 rebuildAttrXP(){s.state.attrXP={};for(const a of s.state.activities)for(const x of a.xpSplit||[])s.state.attrXP[x.attr]=(s.state.attrXP[x.attr]||0)+x.xp},
 rebuildSkillXP(){s.state.skillXP={};for(const a of s.state.activities)s.state.skillXP[a.skill]=(s.state.skillXP[a.skill]||0)+a.xp},
 renderAll(){},
 save(){s.rebuildAttrXP();s.rebuildSkillXP();try{s.localStorage.setItem('test',JSON.stringify(s.state))}catch(e){}},
 Capacitor:{isNativePlatform:()=>true,isPluginAvailable:()=>available,Plugins:{NativeCash:{
  async addListener(event,fn){handlers[event]=fn},async open(a){opened=a},async update(a){updates.push(a)}
 }}}
};s.window=s;vm.createContext(s);
vm.runInContext(money.slice(money.indexOf('function M()'),money.indexOf('let planSel=')),s);
vm.runInContext(read('src/runtime/segments/36-money-pay.js'),s);
vm.runInContext(read('src/runtime/segments/50-native-cash.js'),s);
let count=0;function check(name,fn){fn();count++;console.log('PASS '+name)}
async function flush(){for(let i=0;i<15;i++)await Promise.resolve()}
(async()=>{
 s.navigate('money');await flush();assert(opened);const session=opened.session;
 check('native opens with existing balance and account',()=>{assert.equal(opened.snapshot.balance,100);assert.equal(opened.snapshot.money.account,'Original')});
 async function action(requestId,action,body={}){await handlers.action({session,requestId,action,...body});return updates.at(-1)}
 let ack=await action('1','transaction',{type:'deposit',amount:100,note:'Test transfer'});
 check('deposit saves money and linked XP',()=>{assert(ack.ok);assert.equal(s.moneyBalance(),200);assert(s.state.activities[0].xp>0);assert.equal(JSON.parse(stored.get('test')).money.tx.length,1)});
 const xp=s.state.activities[0].xp;
 await action('1','transaction',{type:'deposit',amount:100});
 check('repeated native event cannot duplicate a deposit',()=>assert.equal(s.state.money.tx.length,1));
 ack=await action('2','transaction',{type:'withdraw',amount:9999});
 check('overdraft is rejected without mutation',()=>{assert.equal(ack.ok,false);assert.equal(s.moneyBalance(),200)});
 await action('3','transaction',{type:'withdraw',amount:50});
 await action('4','transaction',{type:'deposit',amount:50});
 check('withdraw and redeposit do not farm XP',()=>{assert.equal(s.state.activities.length,1);assert.equal(s.state.activities[0].xp,xp)});
 ack=await action('5','transaction',{type:'deposit',amount:Infinity});
 check('nonfinite amounts are rejected',()=>{assert.equal(ack.ok,false);assert.equal(s.moneyBalance(),200)});
 await action('6','account',{account:'Travel jar',start:120,goal:1000});
 check('account edits preserve transaction history and unknown fields',()=>{assert.equal(s.state.money.tx.length,3);assert.equal(s.state.money.customField,'preserve me');assert.equal(s.moneyBalance(),220)});
 failStorage=true;ack=await action('7','transaction',{type:'deposit',amount:25});failStorage=false;
 check('failed persistence rolls back native writes',()=>{assert.equal(ack.ok,false);assert.equal(s.moneyBalance(),220);assert.equal(s.state.money.tx.length,3)});
 s.saveReadOnly='A newer version saved this';ack=await action('7r','transaction',{type:'deposit',amount:25});s.saveReadOnly='';
 check('a read-only save is named as the reason, not storage',()=>{assert.equal(ack.ok,false);assert.match(ack.message,/read-only/);assert.equal(s.moneyBalance(),220)});
 const id=s.state.money.tx.find(t=>t.actId).id;await action('8','delete',{id});
 check('removing a deposit removes its linked XP',()=>{assert.equal(s.state.activities.length,0);assert.equal(s.state.attrXP.business||0,0);assert.equal(s.moneyBalance(),120)});
 const before=JSON.stringify(s.state);await handlers.action({session:'stale',requestId:'9',action:'transaction',type:'deposit',amount:5});
 check('stale screen sessions cannot write',()=>assert.equal(JSON.stringify(s.state),before));
 ack=await action('10','paySettings',{on:false,freq:'monthly',next:'2026-12-01T09:00',from:'2026-10-01',tax:20,lag:2});
 check('native paycheck settings share the existing schema',()=>{assert(ack.ok);assert.equal(s.state.money.pay.freq,'monthly');assert.equal(s.state.money.pay.tax,20)});
 s.state.money.pending.push({id:'pending1',end:Math.floor(Date.now()/1000)-100,hours:2,rate:20,gross:40,title:'Shift'});
 await action('11','clearPay');
 check('pending wages clear once with existing tax rules',()=>{assert.equal(s.state.money.pending.length,0);assert.equal(s.state.money.tx.at(-1).amount,32);assert.equal(s.state.money.tx.at(-1).pay,true)});
 handlers.closed({session,view:''});
 check('native Back returns to the previous app screen',()=>assert.equal(view,'dashboard'));
 available=false;s.navigate('money');await flush();
 check('browser and older APK retain the web fallback',()=>assert.equal(view,'money'));
 console.log(count+' checks passed');
})().catch(e=>{console.error(e);process.exitCode=1});
