/* Native Cash presentation. The web state remains the only source of truth for
   transactions, XP, wages, imports and backups. No bank connection is involved. */
(function(){
 const N={open:false,opening:false,session:'',plugin:null,ready:null,seen:new Map(),timer:0};
 function plugin(){try{const c=window.Capacitor;if(!c||!c.isNativePlatform()||!c.isPluginAvailable('NativeCash'))return null;return c.Plugins.NativeCash}catch(e){return null}}
 function snapshot(){
  const m=payCfg(),bal=moneyBalance(),month=getToday().slice(0,7);
  return {money:m,balance:bal,monthDeposits:m.tx.filter(t=>t.type==='deposit'&&activityDate(t).slice(0,7)===month).reduce((n,t)=>n+t.amount,0),
   pendingTotal:m.pending.reduce((n,p)=>n+payNet(p),0),rules:payRules(),today:getToday(),
   tabs:lmaTabList().map(b=>({id:b.dataset.view,label:(b.textContent||'').trim()})),
   light:document.documentElement.classList.contains('light-theme'),reduceMotion:lmaMotionOff(),haptics:!!fxOpt().haptics};
 }
 function send(extra){if(!N.open)return Promise.resolve();return N.plugin.update(Object.assign({session:N.session,snapshot:snapshot()},extra||{})).catch(e=>console.warn('Cash update',e))}
 function sync(){if(!N.open)return;clearTimeout(N.timer);N.timer=setTimeout(()=>send(),50)}
 const number=(n,lo,hi)=>{const x=Number(n);if(!Number.isFinite(x)||x<lo||x>hi)throw Error('Enter a valid number');return x};
 function command(a){
  let r={ok:true,message:'Saved'};
  if(a.action==='transaction')r=recordMoneyTransaction(a.type,a.amount,a.note);
  else if(a.action==='account')r=updateMoneyAccount(a.account,a.start,a.goal);
  else if(a.action==='delete'){if(!removeMoneyTransaction(String(a.id)))throw Error('Transaction no longer exists');r.message='Transaction removed'}
  else if(a.action==='paySettings'){
   if(!['weekly','biweekly','semimonthly','monthly'].includes(a.freq))throw Error('Choose a pay frequency');
   const validDate=s=>/^\d{4}-\d{2}-\d{2}$/.test(s)&&!isNaN(new Date(s+'T12:00:00'));
   if(!validDate(a.from))throw Error('Choose a valid tracking date');
   if(a.next&&(!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(a.next)||isNaN(new Date(a.next))))throw Error('Choose a valid payday');
   const P=payCfg().pay;Object.assign(P,{on:!!a.on,freq:a.freq,next:a.next||'',from:a.from,tax:number(a.tax,0,60),lag:Math.round(number(a.lag,0,30))});save();payTick();r.message='Pay schedule saved';
  }else if(a.action==='job'){
   if(!payRules().some(j=>j.id===a.id))throw Error('This job is no longer in Life Director');
   const wage=number(a.wage,0,1e6),brk=number(a.brk,0,1440);payCfg().jobs[a.id]={wage,brk};save();payTick();r.message='Wage saved';
  }else if(a.action==='clearPay'){payClearNow();r.message='Pending pay checked'}
  else if(a.action==='removePending'){payRemovePending(String(a.id));r.message='Pending shift removed'}
  else throw Error('Unknown Cash action');
  if(!r.ok)throw Error(r.message);
  return r.message;
 }
 async function action(a){
  if(!N.open||a.session!==N.session)return;
  if(a.action==='refresh'){payTick();await send();return}
  if(!a.requestId)return;
  if(N.seen.has(a.requestId)){await send(N.seen.get(a.requestId));return}
  // Actions are applied synchronously before any await. Repeated delivery never
  // duplicates a deposit. A failed save rolls back the in-memory changes too.
  const before=JSON.stringify(state);let response;
  try{
   const message=command(a),saved=localStorage.getItem(STORE);
   if(!saved||JSON.stringify(JSON.parse(saved).money)!==JSON.stringify(state.money))throw Error('Could not save. Free some storage and try again.');
   response={requestId:a.requestId,message,ok:true};
  }catch(e){state=JSON.parse(before);try{rebuildAttrXP();rebuildSkillXP();renderAll()}catch(_){}response={requestId:a.requestId,message:e.message||'Could not save',ok:false}}
  N.seen.set(a.requestId,response);if(N.seen.size>128)N.seen.delete(N.seen.keys().next().value);
  await send(response);
 }
 async function ready(p){
  if(!N.ready)N.ready=(async()=>{
   await p.addListener('action',action);
   await p.addListener('closed',e=>{
    if(e.session!==N.session)return;N.open=false;N.opening=false;clearTimeout(N.timer);
    if(e.view==='web')return;
    if(e.view&&e.view!=='money'&&document.getElementById('view-'+lmaResolve(e.view)))lmaNavTo(e.view);
    else if(lmaView()==='money')lmaBack();
   });
  })().catch(e=>{N.ready=null;throw e});
  return N.ready;
 }
 async function open(){
  const p=plugin();if(!p||N.open||N.opening)return;
  N.opening=true;N.plugin=p;
  try{
   await ready(p);if(lmaView()!=='money')return;payTick();
   N.session='cash_'+Date.now()+'_'+Math.random().toString(36).slice(2);N.open=true;N.seen.clear();
   await p.open({session:N.session,snapshot:snapshot()});
  }catch(e){N.open=false;console.warn('Native Cash',e);toast('Could not open native Cash. The web view is available below.')}
  finally{N.opening=false}
 }
 const nav=navigate;navigate=function(v){const r=nav.apply(this,arguments);if(v==='money')open();return r};
 const render=renderMoney;renderMoney=function(){const r=render.apply(this,arguments);sync();return r};
 window.openNativeCash=open;
 // A retry entry also permits deliberate side-by-side comparison with the web UI.
 setTimeout(()=>{if(!plugin())return;const head=document.querySelector('#view-money .pagehead');if(head){const b=document.createElement('button');b.className='btn secondary';b.textContent='Open Cash';b.onclick=open;head.appendChild(b)}},700);
})();
