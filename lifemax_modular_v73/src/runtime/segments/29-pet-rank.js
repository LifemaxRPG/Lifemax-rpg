/* ===== Pecking order (v190): who is the more confident cat =====
   Each cat has a confidence (0..1) that starts from its DNA (Dominant up, Skittish down, Outgoing up a little)
   and changes with what happens between the two cats. Power in a contest = DNA Dominant + maturity + confidence.
   A standoff (stare-down, hiss, one backs off) is a weighted coin flip on that power, so the weaker cat can win,
   and a win against the odds lifts its confidence more than an expected one. Saved in state.petPair.rk.
   Rank shows in who starts a chase, who grooms whom, standoffs, and the Together card. Tuning in PET_RANK_CFG. */
var PET_RANK_CFG={
  K:9,                    /* how sharply a power gap decides a contest (gap .2 -> about 86% for the stronger cat) */
  W_DM:0.36,W_AGE:0.26,W_CF:0.38,
  EDGE:0.03,              /* the cat that starts it has a small edge */
  WIN:[0.04,0.12],LOSE:[0.03,0.08],   /* confidence change: base + surprise * x */
  LEAD_GAP:0.05,SETTLE_GAP:0.08,SETTLE_N:3,DRIFT:0.06
};
function petRankPs(p,k){try{const ph=petPheno(p);if(ph&&ph.ps&&typeof ph.ps[k]==='number')return ph.ps[k]}catch(e){}return 0.5}
function petRankClamp(v,a,b){return Math.max(a,Math.min(b,v))}
function petRankBase(i){const p=petPairP(i);if(!p)return 0.5;
  let v=0.5+0.5*(petRankPs(p,'dm')-0.5)-0.3*(petRankPs(p,'sk')-0.5)+0.15*(petRankPs(p,'ou')-0.5);
  if(p.trait==='shy')v-=0.08;else if(p.trait==='playful')v+=0.04;
  return Math.round(petRankClamp(v,0.15,0.85)*1000)/1000}
function petRankData(){
  const d=petPairData();if(!d)return null;let r=d.rk;
  if(!r||r.v!==1)r=d.rk={v:1,cf:[petRankBase(0),petRankBase(1)],n:0,w:[0,0],ups:0,flips:0,lead:-1,settled:false,hist:[],day:''};
  const day=petDay();if(r.day!==day){if(r.day)for(let i=0;i<2;i++)r.cf[i]=Math.round((r.cf[i]+(petRankBase(i)-r.cf[i])*PET_RANK_CFG.DRIFT)*1000)/1000;r.day=day}
  return r;
}
/* how strong a cat is in a contest, 0..1 */
function petRankPower(i){
  const r=petRankData(),p=petPairP(i),C=PET_RANK_CFG;if(!r||!p)return 0.5;
  let g=1;try{g=petGrowth(p)}catch(e){}
  return C.W_DM*petRankPs(p,'dm')+C.W_AGE*g+C.W_CF*r.cf[i];
}
function petRankProb(chal){const C=PET_RANK_CFG,o=1-chal,gap=petRankPower(chal)+C.EDGE-petRankPower(o);return 1/(1+Math.exp(-C.K*gap))}
function petRankLead(){const r=petRankData();if(!r)return -1;const g=petRankPower(0)-petRankPower(1);return Math.abs(g)<PET_RANK_CFG.LEAD_GAP?-1:g>0?0:1}
/* settle the order after a contest */
function petRankUpdate(r,winner){
  const C=PET_RANK_CFG,before=r.lead;r.hist.push(winner);if(r.hist.length>6)r.hist.shift();
  const lead=petRankLead(),gap=Math.abs(petRankPower(0)-petRankPower(1));
  const lastN=r.hist.slice(-C.SETTLE_N),streak=lead>=0&&lastN.length>=C.SETTLE_N&&lastN.filter(function(x){return x===lead}).length>=C.SETTLE_N-1;
  const was=r.settled;r.lead=lead;r.settled=!!(r.n>=C.SETTLE_N&&lead>=0&&gap>=C.SETTLE_GAP&&streak);
  const nm=[state.pet.name,state.pet2.name];
  if(before>=0&&lead>=0&&lead!==before){r.flips++;setTimeout(function(){toast(nm[lead]+' has the upper hand now.')},700)}
  else if(!was&&r.settled)setTimeout(function(){toast(nm[lead]+' and '+nm[1-lead]+' have settled who is in charge.')},700);
}
/* one contest; chal is the cat that starts it. Returns {w,l,p,upset} */
function petRankContest(chal){
  const r=petRankData(),C=PET_RANK_CFG;if(!r)return null;
  const p=petRankProb(chal),chalWins=Math.random()<p,w=chalWins?chal:1-chal,l=1-w,pw=chalWins?p:1-p,sur=1-pw,upset=pw<0.4;
  r.cf[w]=Math.round(petRankClamp(r.cf[w]+C.WIN[0]+C.WIN[1]*sur,0.05,0.95)*1000)/1000;
  r.cf[l]=Math.round(petRankClamp(r.cf[l]-(C.LOSE[0]+C.LOSE[1]*sur),0.05,0.95)*1000)/1000;
  r.n++;r.w[w]++;if(upset)r.ups++;petRankUpdate(r,w);
  return {w:w,l:l,p:pw,upset:upset};
}
/* the director: how likely a standoff is right now, and who starts it */
function petRankChallenger(){const lead=petRankLead();if(lead<0)return Math.random()<0.5?0:1;return Math.random()<0.7?1-lead:lead}
function petRankWeight(){
  const r=petRankData();if(!r)return 0;
  if(!r.settled)return r.n<6?0.55:0.3;
  const lead=r.lead,under=1-lead,drive=Math.max(0,r.cf[under]-r.cf[lead]+0.12);
  return Math.min(0.5,0.05+0.6*drive);
}
function petRankBias(kind,a){const lead=petRankLead();if(lead<0)return a;
  if(kind==='chase')return Math.random()<0.65?lead:a;               /* the confident cat starts the chasing */
  if(kind==='groom')return Math.random()<0.65?1-lead:a;             /* the other grooms up */
  return a}
/* ----- the standoff: walk up, stare, one stands tall and the other backs off ----- */
function petPairStandoffStare(I){
  return {a:function(rt,c){return [[{k:'fn',f:function(){petSpawn(rt,'exclaim',c.x+c.dir*8*petK(c),c.y-42*petK(c),0,-6,1.0)}},petAct('idle_sit',2.6)],'idle_sit']},
          b:function(){return [[petAct('idle_sit',2.6)],'idle_sit']}};
}
function petPairStandoffEnd(I){
  const res=petRankContest(I.a),nm=petPairNames(I),d=petPairData();
  if(!res)return {final:1};
  d.n.standoff=(d.n.standoff||0)+1;
  const W=nm[res.w],L=nm[res.l];
  petPairNote('standoff',res.w,res.upset?W+' won a standoff against the odds':W+' stood their ground against '+L);
  petPairFeel(res.w,0.05,0.1);petPairFeel(res.l,-0.07,0.12);
  const winnerPlan=function(rt,c,oc){const q=[{k:'fn',f:function(){petPlay(rt,'hiss',0.7)}},petAct('idle_sit',1.4),petAct('stretch',2.8)];
    q.push.apply(q,petPairGo(c,c.x+(c.x>=oc.x?1:-1)*petRand(30,55),c.z));q.push(petAct('loaf',petRand(15,30)));return [q]};
  const loserPlan=function(rt,c,oc){const away=petClampHouse(c.x+(c.x>=oc.x?1:-1)*petRand(60,95)),q=[petAct('crouch',1.1)];
    q.push.apply(q,petPairGo(c,away,c.z,true));q.push(petAct('idle_sit',1.6));q.push(petAct('groom',petRand(4,7)));return [q]};
  const spec={final:1};spec[res.w===I.a?'a':'b']=winnerPlan;spec[res.w===I.a?'b':'a']=loserPlan;return spec;
}
PET_PAIR_PLAYS.standoff=[petPairGreetMeet,petPairStandoffStare,petPairStandoffEnd];
/* ----- the Together card ----- */
function petRankLabel(v){return v<0.33?'Timid':v<0.55?'Steady':v<0.75?'Confident':'Bold'}
function petRankHtml(){
  const r=petRankData(),d=petPairData();if(!r||!d||!d.met)return '';const nm=[state.pet.name,state.pet2.name],lead=petRankLead();
  const line=r.settled&&lead>=0?'<b>'+esc(nm[lead])+'</b> is the confident one; '+esc(nm[1-lead])+' defers.'
    :lead>=0?esc(nm[lead])+' is a little ahead, but it is not settled yet.':'They are still working out who is in charge.';
  const row=function(i){return '<div class="pet-thread pet-dna-p"><span>'+esc(nm[i])+'</span><i><em style="width:'+Math.round(r.cf[i]*100)+'%"></em></i><small>'+petRankLabel(r.cf[i])+'</small></div>'};
  return '<div class="pet-rank"><div class="eyebrow" style="margin-top:12px">PECKING ORDER</div><p class="pet-pair-text" style="margin:6px 0 8px">'+line+'</p>'+row(0)+row(1)+'</div>';
}
petDevTest('Two cats: standoff',function(){petDevPair('standoff')},'v190');
