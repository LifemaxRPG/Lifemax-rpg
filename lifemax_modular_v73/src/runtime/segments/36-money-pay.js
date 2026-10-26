/* v92 · Paychecks: work shifts from Life Director become pending pay when they end, and move into the
   account when payday arrives. Data lives in state.money (pay, jobs, pending, seen), so backups carry it. */
const PAY_FREQ=[['weekly','Every week'],['biweekly','Every 2 weeks'],['semimonthly','Twice a month'],['monthly','Monthly']];
const payR2=n=>Math.round(n*100)/100;
/* take-home for one pending shift, using the tax setting as it is now */
const payNet=p=>payR2(p.gross*(1-(Number(payCfg().pay.tax)||0)/100));
function payCfg(){
 const m=M();
 if(!m.pay)m.pay={on:false,freq:'biweekly',next:'',lag:0,tax:0,from:getToday()};
 if(!m.jobs)m.jobs={};if(!m.pending)m.pending=[];if(!m.seen)m.seen={};
 return m;
}
function payRules(){try{return (window.LifeDirector&&LifeDirector.workRules&&LifeDirector.workRules())||[]}catch(e){return []}}
function payDayStr(d){const x=new Date(d);return x.toLocaleDateString(undefined,{weekday:'short',month:'short',day:'numeric'})}
function payNextDate(freq,d){
 const n=new Date(d.getTime());
 if(freq==='weekly')n.setDate(n.getDate()+7);
 else if(freq==='biweekly')n.setDate(n.getDate()+14);
 else if(freq==='monthly'){const day=d.getDate();n.setDate(1);n.setMonth(n.getMonth()+1);n.setDate(Math.min(day,new Date(n.getFullYear(),n.getMonth()+1,0).getDate()))}
 else{ /* twice a month: the chosen day and the day 15 apart */
  const day=d.getDate(),a=day<=15?day:day-15,b=a+15,last=m=>new Date(m.getFullYear(),m.getMonth()+1,0).getDate();
  if(day===a){n.setDate(Math.min(b,last(d)))}
  else{n.setDate(1);n.setMonth(n.getMonth()+1);n.setDate(Math.min(a,last(n)))}
 }
 return n;
}
const payISO=d=>{const p=n=>String(n).padStart(2,'0');return d.getFullYear()+'-'+p(d.getMonth()+1)+'-'+p(d.getDate())+'T'+p(d.getHours())+':'+p(d.getMinutes())};
/* find shifts that ended since the start date and add them to pending pay (each shift only once) */
function payScan(){
 const m=payCfg(),P=m.pay;if(!P.on||!window.LifeDirector||!LifeDirector.shifts)return false;
 const now=Math.floor(Date.now()/1000),startMs=Math.max(parseD(P.from||getToday()).getTime(),Date.now()-90*86400e3),from=Math.floor(startMs/1000);
 let list;try{list=LifeDirector.shifts(from,now)}catch(e){return false}
 const rules=payRules(),firstJob=rules.map(r=>m.jobs[r.id]).find(j=>j&&j.wage>0);
 let changed=false;
 for(const s of list){
  if(m.seen[s.key])continue;
  let job=s.ruleId?m.jobs[s.ruleId]:null;
  if(!s.ruleId){const r=rules.find(x=>(x.title||'').toLowerCase()===(s.title||'').toLowerCase());job=(r&&m.jobs[r.id])||firstJob}
  if(!job||!(job.wage>0))continue; /* no wage yet: leave it, it will be picked up once a wage is set */
  const hours=Math.max(0,(s.durationMin-(Number(job.brk)||0))/60),gross=payR2(hours*job.wage);
  m.pending.push({id:'pp_'+s.endEpoch+'_'+m.pending.length,key:s.key,title:s.title,start:s.startEpoch,end:s.endEpoch,hours:payR2(hours),rate:job.wage,gross});
  m.seen[s.key]=s.endEpoch;changed=true;
 }
 for(const k of Object.keys(m.seen))if(m.seen[k]<from-86400){delete m.seen[k];changed=true}
 return changed;
}
function payClear(cutoffMs,whenISO,note){
 const m=payCfg(),take=m.pending.filter(p=>p.end*1000<=cutoffMs);if(!take.length)return 0;
 const sum=payR2(take.reduce((s,p)=>s+payNet(p),0));
 m.tx.push({id:'tx_'+Date.now()+'_pay'+m.tx.length,type:'deposit',amount:sum,note:take.length+' shift'+(take.length===1?'':'s')+(note?' · '+note:''),date:whenISO,pay:true});
 m.pending=m.pending.filter(p=>p.end*1000>cutoffMs);
 return sum;
}
/* run on a timer and when the app is opened: catch up on shifts, then on any paydays that have passed */
function payTick(){
 const m=payCfg(),P=m.pay;if(!P.on)return;
 let changed=payScan(),cleared=0;
 if(P.next){
  let next=new Date(P.next),guard=0;
  while(!isNaN(next)&&next.getTime()<=Date.now()&&guard++<24){
   const sum=payClear(next.getTime()-(Number(P.lag)||0)*86400e3,next.toISOString(),'');
   if(sum)cleared+=sum;
   next=payNextDate(P.freq,next);P.next=payISO(next);changed=true;
  }
 }
 if(changed){save();if(cleared)toast('Paycheck of '+fmt(cleared)+' added to your account')}
}
function payClearNow(){
 const m=payCfg();if(!m.pending.length){toast('Nothing pending');return}
 const sum=payClear(Date.now(),new Date().toISOString(),'cleared by hand');save();toast(sum?'Paycheck of '+fmt(sum)+' added':'Nothing to clear');
}
function payRemovePending(id){const m=payCfg();m.pending=m.pending.filter(p=>p.id!==id);save();toast('Removed. It will not be counted again.')}
function paySet(k,v){
 const m=payCfg(),P=m.pay;
 if(k==='on'){P.on=!!v;if(v&&!P.from)P.from=getToday()}
 else if(k==='tax')P.tax=Math.min(60,Math.max(0,parseFloat(v)||0));
 else if(k==='lag')P.lag=Math.min(30,Math.max(0,Math.round(parseFloat(v)||0)));
 else if(k==='freq')P.freq=v;
 else if(k==='from'){P.from=v||getToday()}
 else if(k==='nextDate'||k==='nextTime'){
  const cur=P.next?new Date(P.next):null,d0=k==='nextDate'?v:(cur&&!isNaN(cur)?payISO(cur).slice(0,10):getToday()),t0=k==='nextTime'?v:(cur&&!isNaN(cur)?payISO(cur).slice(11):'09:00');
  if(d0)P.next=d0+'T'+(t0||'09:00');
 }
 save();payTick();
}
function payJob(id,field,v){const m=payCfg(),j=m.jobs[id]||(m.jobs[id]={wage:0,brk:0});j[field]=Math.max(0,parseFloat(v)||0);save();payTick()}
function payDays(days){const n=['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];return days.length===7?'Every day':days.length===5&&days.join()==='1,2,3,4,5'?'Mon–Fri':days.map(d=>n[d]).join(', ')}
function payTime(min){const d=new Date(2000,0,1,Math.floor(min/60),min%60);return d.toLocaleTimeString(undefined,{hour:'numeric',minute:'2-digit'})}
function payRender(){
 const m=payCfg(),P=m.pay,el=id=>document.getElementById(id);if(!el('payBody'))return;
 const on=el('payOn');if(on)on.checked=!!P.on;
 const pend=m.pending.slice().sort((a,b)=>a.end-b.end),total=payR2(pend.reduce((s,p)=>s+payNet(p),0));
 const line=el('moneyPendingLine');
 if(line){
  line.style.display=P.on?'flex':'none';
  if(P.on){const nd=P.next?new Date(P.next):null;line.innerHTML='<span>Pending pay <strong>'+fmt(total)+'</strong><span class="muted" style="font-size:12px"> · '+pend.length+' shift'+(pend.length===1?'':'s')+'</span></span><span class="muted" style="font-size:12px">'+(nd&&!isNaN(nd)?'Payday '+payDayStr(nd)+', '+nd.toLocaleTimeString(undefined,{hour:'numeric',minute:'2-digit'}):'Set your payday below')+'</span>'}
 }
 if(!P.on){el('payBody').innerHTML='<p class="muted" style="font-size:12px;margin:0">Turn this on to add your work shifts as pending pay.</p>';return}
 if(el('payBody').contains(document.activeElement)&&document.activeElement.tagName==='INPUT')return; /* do not rebuild while typing */
 const rules=payRules(),nd=P.next?new Date(P.next):null,ndOk=nd&&!isNaN(nd);
 const inp='style="background:var(--panel2);color:var(--text);border:1px solid var(--line);border-radius:8px;padding:7px 9px;font:inherit;width:100%"';
 let h='';
 if(!rules.length)h+='<div class="note" style="margin-bottom:10px">No work hours found. Add them in Life Director → My time, as a commitment of type Work.</div>';
 else{
  h+='<div class="statlabel" style="margin-bottom:6px">YOUR JOBS</div>';
  for(const r of rules){const j=m.jobs[r.id]||{};
   h+='<div class="card" style="padding:12px;margin-bottom:8px"><strong>'+esc(r.title)+'</strong><div class="muted" style="font-size:12px">'+esc(payDays(r.days))+' · '+payTime(r.startMin)+' · '+payR2(r.durationMin/60)+' h</div>'+
    '<div class="formgrid" style="margin-top:8px"><div class="field"><label>Hourly wage ($)</label><input type="number" min="0" step="0.01" inputmode="decimal" value="'+(j.wage||'')+'" placeholder="0.00" onchange="payJob(\''+esc(r.id)+'\',\'wage\',this.value)" '+inp+'></div>'+
    '<div class="field"><label>Unpaid break (min)</label><input type="number" min="0" step="5" inputmode="numeric" value="'+(j.brk||'')+'" placeholder="0" onchange="payJob(\''+esc(r.id)+'\',\'brk\',this.value)" '+inp+'></div></div></div>';}
 }
 h+='<div class="statlabel" style="margin:12px 0 6px">PAYDAY</div><div class="formgrid">'+
  '<div class="field"><label>How often</label><select onchange="paySet(\'freq\',this.value)" '+inp+'>'+PAY_FREQ.map(f=>'<option value="'+f[0]+'"'+(P.freq===f[0]?' selected':'')+'>'+f[1]+'</option>').join('')+'</select></div>'+
  '<div class="field"><label>Next payday</label><input type="date" value="'+(ndOk?payISO(nd).slice(0,10):'')+'" onchange="paySet(\'nextDate\',this.value)" '+inp+'></div>'+
  '<div class="field"><label>Time it arrives</label><input type="time" value="'+(ndOk?payISO(nd).slice(11):'09:00')+'" onchange="paySet(\'nextTime\',this.value)" '+inp+'></div>'+
  '<div class="field"><label>Hold back newest days of work</label><input type="number" min="0" max="30" inputmode="numeric" value="'+(P.lag||0)+'" onchange="paySet(\'lag\',this.value)" '+inp+'></div>'+
  '<div class="field"><label>Estimated tax withheld (%)</label><input type="number" min="0" max="60" step="0.5" inputmode="decimal" value="'+(P.tax||0)+'" onchange="paySet(\'tax\',this.value)" '+inp+'></div>'+
  '<div class="field"><label>Count shifts starting</label><input type="date" value="'+esc(P.from||'')+'" onchange="paySet(\'from\',this.value)" '+inp+'></div></div>'+
  '<div class="muted" style="font-size:12px;margin-top:6px">“Hold back” leaves the newest days unpaid until the next check, like a pay period that ends before payday. Extra shifts added in Life Director use the job with the same name, or your first job’s wage.</div>';
 h+='<div class="statlabel" style="margin:14px 0 6px">PENDING PAY</div>';
 if(!pend.length)h+='<div class="muted" style="font-size:13px">No shifts waiting. They appear here when they end.</div>';
 else{
  h+=pend.map(p=>'<div class="minirow"><span><strong>'+esc(p.title)+'</strong><br><small>'+payDayStr(p.end*1000)+' · '+p.hours+' h × '+fmt(p.rate)+'</small></span><span style="display:flex;align-items:center;gap:10px"><strong style="color:var(--accent2)">+'+fmt(payNet(p))+'</strong><button class="btn small secondary" onclick="payRemovePending(\''+p.id+'\')" title="Did not work this shift">Remove</button></span></div>').join('');
  h+='<div style="margin-top:10px;display:flex;gap:8px;flex-wrap:wrap;align-items:center"><button class="btn secondary" onclick="payClearNow()">Paycheck arrived: add '+fmt(total)+' now</button></div>';
 }
 el('payBody').innerHTML=h;
}
/* ---------- wiring ---------- */
(function payBoot(){
 const _rm=renderMoney;renderMoney=function(){const r=_rm.apply(this,arguments);try{payRender()}catch(e){console.warn('pay render',e)}return r};
 document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible')payTick()});
 setInterval(payTick,60e3);
 setTimeout(payTick,4000);
})();
