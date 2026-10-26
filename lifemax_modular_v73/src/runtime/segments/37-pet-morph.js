/* ===== Cat body shape (v93): genes for ear, tail, leg, body, head and whisker proportions, and a breeding lab =====
   Six quantitative traits (PET_MORPH), each built from several + / − loci like the size and temperament
   traits in 32: they add up, they are mostly inherited (high heritability) and they are polymorphic in every
   founder, so selecting the longest-eared cats of each litter really does produce longer ears over a few
   generations. A recessive major gene (Tb) makes a bobtail. petDnaPheno gains ph.morph (the proportions as
   multipliers, 1 = the original art), ph.mz (the score -1..1) and ph.mc (the + alleles / loci).
   Cats from before v93 get the new loci filled in deterministically from their id (petMorphFill) the first
   time anything reads them, so nothing about their look changes until the rig draws shapes (38/39).
   The breeding lab (state.cattery) keeps extra cats as genomes only, pairs any mother with any father,
   rolls a litter and lets you keep, release or bring home the kittens. A goal (a trait and a direction)
   shows how close the roster is. Hooks: wraps petDnaPheno, petDnaGamete, petDnaConceive, petDnaPanelHtml,
   petRoomHtml and petBrainRender. */
var PET_MORPH={
  em:{name:'Ear length',R:1.35,h2:0.8,lo:'tiny',hi:'enormous',n:'ears'},
  ew:{name:'Ear width',R:0.6,h2:0.75,lo:'narrow',hi:'broad',n:'ear base'},
  tl:{name:'Tail length',R:1.25,h2:0.8,lo:'stubby',hi:'very long',n:'tail'},
  lg:{name:'Leg length',R:0.75,h2:0.8,lo:'short',hi:'leggy',n:'legs'},
  bl:{name:'Body length',R:0.5,h2:0.75,lo:'cobby',hi:'long and lean',n:'body'},
  wk:{name:'Whisker length',R:1.0,h2:0.7,lo:'short',hi:'long',n:'whiskers'}
};
var PET_MORPH_KEYS=['em','ew','tl','lg','bl','wk'];
(function(){
  /* loci: id, chromosome, approximate Mb. Spread over the genome so that traits segregate independently,
     with a few near neighbours so some are inherited together. */
  const L={
    em:[['Em1','A1',172],['Em2','B3',95],['Em3','C2',140],['Em4','D4',22],['Em5','E2',64]],
    ew:[['Ew1','A3',20],['Ew2','D1',150],['Ew3','F1',85]],
    tl:[['Tl1','A2',100],['Tl2','B4',33],['Tl3','C1',190],['Tl4','D3',70],['Tl5','E3',110]],
    lg:[['Lg1','A1',60],['Lg2','B2',120],['Lg3','C2',55],['Lg4','D2',130],['Lg5','F2',35]],
    bl:[['Bl1','A3',150],['Bl2','B1',10],['Bl3','D1',80],['Bl4','E1',100]],
    wk:[['Wk1','B4',60],['Wk2','D4',100]]
  };
  PET_MORPH_KEYS.forEach(function(k){PET_QTL[k]={name:PET_MORPH[k].name,h2:PET_MORPH[k].h2,loci:L[k]}});
  PET_LOCI.push({id:'Tb',gene:'HES7',name:'Bobtail',chr:'B2',pos:75,al:[['T',0.96,'Full-length tail'],['tb',0.04,'Bobtail: a short, kinked stub (recessive)']]});
})();
function petMorphIds(){const o=['Tb'];PET_MORPH_KEYS.forEach(function(k){PET_QTL[k].loci.forEach(function(l){o.push(l[0])})});return o}
/* old genomes lack the new loci: draw them from the cat's own id, so the same cat always gets the same genes */
function petMorphFill(d){
  if(!d||!d.g)return;
  /* a cat that already lived here before v93 keeps the body it has always had: balanced alleles (half of them +)
     and no developmental noise, so every proportion is exactly 1. Its kittens still vary. */
  const old=!!d.legacy||!!d.neutral;
  PET_MORPH_KEYS.forEach(function(k){PET_QTL[k].loci.forEach(function(l){
    if(!d.g[l[0]]){const r=petRng(petHash((d.id||'x')+'|'+l[0]));d.g[l[0]]=old?['+','-']:[r()<0.5?'+':'-',r()<0.5?'+':'-']}})});
  if(!d.g.Tb){const r=petRng(petHash((d.id||'x')+'|Tb')),L=petDnaLocus('Tb');d.g.Tb=old?['T','T']:[petDnaDraw(L.al,r),petDnaDraw(L.al,r)]}
  if(old&&d.neutral==null)d.neutral=1;
}
function petMorphScore(d,k){
  if(d.neutral&&PET_QTL[k].loci.every(function(l){const v=d.g[l[0]]||[];return v.length===2&&v[0]!==v[1]}))return 0;
  const Q=PET_QTL[k],ids=Q.loci.map(function(l){return l[0]}),n=ids.length,z=petDnaCount(d,ids)/(2*n)*2-1,h=PET_MORPH[k].h2;
  return Math.max(-1,Math.min(1,Math.sqrt(h)*z+Math.sqrt(1-h)*0.5*petDnaEnv(d,k)));
}
function petMorphVal(k,s){return Math.max(0.35,Math.min(2.8,Math.pow(2,PET_MORPH[k].R*s)))}
function petMorphAttach(d,ph){
  const m={},z={},c={};
  PET_MORPH_KEYS.forEach(function(k){const ids=PET_QTL[k].loci.map(function(l){return l[0]});z[k]=petMorphScore(d,k);m[k]=Math.round(petMorphVal(k,z[k])*100)/100;c[k]=[petDnaCount(d,ids),ids.length*2]});
  const tb=(d.g.Tb||[]).length>0&&d.g.Tb.every(function(a){return a==='tb'});
  m.bob=tb?1:0;if(tb)m.tl=Math.round(Math.min(m.tl,1)*0.3*100)/100;
  ph.morph=m;ph.mz=z;ph.mc=c;
}
function petMorphWord(k,v){
  const M=PET_MORPH[k];
  if(v<0.6)return M.lo;if(v<0.85)return 'rather '+M.lo.replace(/^rather /,'');if(v<1.18)return 'average';if(v<1.5)return 'rather '+M.hi.replace(/^very /,'');if(v<2)return M.hi;return 'extremely '+M.hi.replace(/^extremely /,'');
}
/* ---- wrap the genetics core ---- */
(function(){
  const pf=petDnaPheno;
  petDnaPheno=function(d){petMorphFill(d);const ph=pf.apply(this,arguments);if(ph&&!ph.morph)petMorphAttach(d,ph);return ph};
  const gm=petDnaGamete;petDnaGamete=function(d,r){petMorphFill(d);return gm.call(this,d,r)};
  const cv=petDnaConceive;petDnaConceive=function(mom,dad,r){petMorphFill(mom);petMorphFill(dad);const k=cv.apply(this,arguments);if(k)k.gen=1+Math.max(mom.gen||0,dad.gen||0);return k};
  const carries=petDnaCarries;petDnaCarries=function(d,ph){const c=carries.apply(this,arguments);if(petDnaHas(d,'Tb','tb')&&!ph.morph.bob)c.push('bobtail');return c};
})();
/* ---- the Body shape section of the genetics panel ---- */
function petMorphPanelHtml(p){
  const d=petDnaOf(p),ph=petDnaPheno(d),m=ph.morph;
  let h='<div class="pet-likes-h">Body shape</div><p class="muted pet-dna-sub">Proportions come from several genes each, so they vary smoothly and are passed on strongly. Breed two long-eared cats and their kittens tend to have long ears too.'+(petGrowIdx(p)>=0?' (Shown for the grown cat.)':'')+'</p>';
  PET_MORPH_KEYS.forEach(function(k){
    const c=ph.mc[k],pct=Math.round((ph.mz[k]+1)/2*100),v=k==='tl'&&m.bob?m.tl:m[k];
    h+='<div class="pet-thread pet-dna-p"><span>'+PET_MORPH[k].name+' · '+petMorphWord(k,v)+'</span><i><em style="width:'+pct+'%"></em></i><small>'+v.toFixed(2)+'× the standard art · '+c[0]+' of '+c[1]+' lengthening alleles</small></div>';
  });
  h+=petDnaRow('Tail · '+petDnaLocus('Tb').gene,petDnaGt(d,'Tb'),m.bob?'Bobtail: two recessive copies shorten the tail to a stub.':'Full tail'+(petDnaHas(d,'Tb','tb')?', carries bobtail.':'.'));
  return h;
}
(function(){
  const pn=petDnaPanelHtml;
  petDnaPanelHtml=function(p){const h=pn.apply(this,arguments),mark='<div class="pet-likes-h">Temperament</div>';return h.indexOf(mark)>=0?h.replace(mark,petMorphPanelHtml(p)+mark):h+petMorphPanelHtml(p)};
})();

/* ===== The breeding lab ===== */
var PET_BREED={mom:'',dad:'',busy:false,cv:{}};
var PET_CATTERY_MAX=16;
function petCattery(){const s=state;if(!s.cattery||typeof s.cattery!=='object')s.cattery={v:1,cats:[],goal:'em+',litter:null};if(!Array.isArray(s.cattery.cats))s.cattery.cats=[];return s.cattery}
function petBreedRoster(){
  const o=[];
  [state.pet,state.pet2].forEach(function(p,i){if(p&&typeof p==='object'&&p.alive&&p.dna){const d=petDnaOf(p);o.push({key:'h'+i,name:p.name,dna:d,born:p.born,home:true,p:p})}});
  petCattery().cats.forEach(function(c){o.push({key:c.id,name:c.name,dna:c.dna,born:c.born,home:false,c:c})});
  return o;
}
function petBreedFind(k){return petBreedRoster().find(function(e){return e.key===k})}
function petBreedGoal(){const g=petCattery().goal||'em+';return {k:g.slice(0,2),up:g.charAt(2)!=='-'}}
function petBreedGoalVal(ph){const g=petBreedGoal(),v=ph.morph[g.k];return g.up?v:1/Math.max(0.05,v)}   /* higher is better */
function petBreedName(taken){const pool=PET_NAMES.concat(['Sage','Nori','Poppy','Ziggy','Olive','Pixel','Basil','Mango','Tango','Rumi','Fig','Dot']);
  const free=pool.filter(function(n){return taken.indexOf(n)<0});if(free.length)return free[Math.floor(Math.random()*free.length)];let i=2;while(taken.indexOf('Kit '+i)>=0)i++;return 'Kit '+i}
function petBreedChips(ph,goalK){
  return PET_MORPH_KEYS.map(function(k){const v=k==='tl'&&ph.morph.bob?ph.morph.tl:ph.morph[k];
    return '<span class="pet-chip'+(k===goalK?' goal':'')+'" title="'+esc(PET_MORPH[k].name)+'">'+PET_MORPH[k].n+' '+v.toFixed(1)+'×</span>'}).join('')+(ph.morph.bob?'<span class="pet-chip goal">bobtail</span>':'');
}
function petBreedCard(e,mode){
  const ph=petDnaPheno(e.dna),f=ph.sex==='f',g=petBreedGoal(),age=Math.max(0,Math.floor((Date.now()-(e.born||Date.now()))/86400000));
  let act='';
  if(mode==='roster'&&!e.home){
    const canHome=state.pet&&state.pet.alive&&!(state.pet2&&state.pet2.alive);
    act='<div class="pet-bact"><button class="btn small secondary" onclick="petBreedHome(\''+e.key+'\')"'+(canHome?'':' disabled title="Your second cat slot is taken"')+'>Bring home</button>'
      +'<button class="btn small secondary" onclick="petBreedRelease(\''+e.key+'\')">Release</button></div>';
  }else if(mode==='litter'){
    act='<div class="pet-bact"><button class="btn small" onclick="petBreedKeep('+e.idx+')">Keep</button><button class="btn small secondary" onclick="petBreedDrop('+e.idx+')">Release</button></div>';
  }
  return '<div class="pet-bc"><canvas class="pet-bcv" data-bk="'+esc(e.key)+'" width="144" height="128"></canvas><div class="pet-bci"><b><i class="pet-sex '+(f?'f':'m')+'">'+(f?'♀':'♂')+'</i> '
    +(mode==='roster'&&!e.home?'<a onclick="petBreedRename(\''+e.key+'\')">'+esc(e.name)+'</a>':esc(e.name))+'</b><small>'+esc(ph.coat)+' · generation '+(e.dna.gen||0)+(e.home?' · at home':'')+(mode==='roster'&&!e.home?' · '+age+' d old':'')+(ph.sterile?' · sterile':'')+'</small>'
    +'<div class="pet-chips">'+petBreedChips(ph,g.k)+'</div></div>'+act+'</div>';
}
function petBreedGoalSelect(){
  const g=petCattery().goal||'em+';let o='';
  PET_MORPH_KEYS.forEach(function(k){[['+',PET_MORPH[k].hi.replace(/^(very|extremely) /,'')],['-',PET_MORPH[k].lo]].forEach(function(x){const v=k+x[0];o+='<option value="'+v+'"'+(g===v?' selected':'')+'>'+(x[0]==='+'?'Longest/largest ':'Shortest/smallest ')+PET_MORPH[k].n+' ('+x[1]+')</option>'})});
  return '<select class="pet-in" onchange="petBreedSetGoal(this.value)" aria-label="Breeding goal">'+o+'</select>';
}
function petBreedHtml(){
  const C=petCattery(),R=petBreedRoster(),g=petBreedGoal();
  const moms=R.filter(function(e){return petDnaSexOf(e.dna)==='f'}),dads=R.filter(function(e){return petDnaSexOf(e.dna)==='m'&&petDnaFertile(e.dna)});
  if(!moms.some(function(e){return e.key===PET_BREED.mom}))PET_BREED.mom=moms.length?moms[0].key:'';
  if(!dads.some(function(e){return e.key===PET_BREED.dad}))PET_BREED.dad=dads.length?dads[0].key:'';
  const opt=function(list,sel){return list.map(function(e){return '<option value="'+esc(e.key)+'"'+(e.key===sel?' selected':'')+'>'+esc(e.name)+(e.home?' (home)':'')+'</option>'}).join('')};
  let h='<div class="pet-likes-h">Breeding lab</div><p class="muted pet-dna-sub">Pair a mother with a father to roll a litter. Each kitten gets a random half of each parent\'s genes, so keep the ones that lean toward your goal and breed those. Cats you keep wait here as genomes; bring one home when your second cat slot is free.</p>';
  /* goal */
  const best=R.slice().sort(function(a,b){return petBreedGoalVal(petDnaPheno(b.dna))-petBreedGoalVal(petDnaPheno(a.dna))})[0];
  h+='<label class="pet-lab">Goal</label>'+petBreedGoalSelect();
  if(best){const bp=petDnaPheno(best.dna),v=bp.morph[g.k];h+='<p class="muted pet-dna-sub">Best in your roster: <b>'+esc(best.name)+'</b> at '+v.toFixed(2)+'× '+PET_MORPH[g.k].n+' (generation '+(best.dna.gen||0)+').</p>'}
  /* pairing */
  if(!moms.length||!dads.length){
    h+='<p class="pet-dna-note">You need at least one female and one fertile male. '+(moms.length?'':'No female yet. ')+(dads.length?'':'No fertile male yet. ')+'Try <b>Another litter</b> below to find a mate.</p>'}
  else{
    h+='<div class="pet-pairing"><div><label class="pet-lab">Mother</label><select class="pet-in" onchange="petBreedPick(\'mom\',this.value)">'+opt(moms,PET_BREED.mom)+'</select></div>'
     +'<div><label class="pet-lab">Father</label><select class="pet-in" onchange="petBreedPick(\'dad\',this.value)">'+opt(dads,PET_BREED.dad)+'</select></div></div>';
    const m=petBreedFind(PET_BREED.mom),f=petBreedFind(PET_BREED.dad);
    if(m&&f){const x=petBreedForecast(m.dna,f.dna);h+='<p class="muted pet-dna-sub">Expected '+PET_MORPH[g.k].n+': <b>'+x.mean.toFixed(2)+'×</b> on average, usually between '+x.lo.toFixed(2)+'× and '+x.hi.toFixed(2)+'×.</p>'}
    h+='<button class="btn" '+(PET_BREED.busy?'disabled ':'')+'onclick="petBreedGo()">Breed</button>';
  }
  /* litter */
  const L=C.litter;
  if(L&&L.kits&&L.kits.length){
    h+='<div class="pet-likes-h">Litter of '+L.kits.length+' <small>'+esc(L.mom)+' × '+esc(L.dad)+'</small></div><div class="pet-bl">'+L.kits.map(function(d,i){return petBreedCard({key:'lit'+i,idx:i,name:'Kitten '+(i+1),dna:d,born:Date.now()},'litter')}).join('')+'</div>'
     +'<div class="pet-bact"><button class="btn small secondary" onclick="petBreedKeepAll()">Keep all</button><button class="btn small secondary" onclick="petBreedDropAll()">Release all</button></div>'
     +(C.cats.length>=PET_CATTERY_MAX?'<p class="pet-dna-note">The cattery is full ('+PET_CATTERY_MAX+'). Release a cat before keeping more.</p>':'');
  }
  /* roster */
  h+='<div class="pet-likes-h">Your cats <small>'+R.length+' · cattery '+C.cats.length+'/'+PET_CATTERY_MAX+'</small></div><div class="pet-bl">'+R.map(function(e){return petBreedCard(e,'roster')}).join('')+'</div>'
   +'<button class="btn small secondary" onclick="petBreedStray()">Another stray</button> <span class="muted" style="font-size:12px">A random unrelated adult joins the cattery. Handy for fresh genes.</span>';
  return h;
}
/* a quick Monte-Carlo of the pairing, for the goal trait */
function petBreedForecast(a,b){
  const g=petBreedGoal(),r=petRng(petHash(a.id+'|'+b.id+'|fc')),v=[];
  for(let i=0;i<120;i++){const k=petDnaConceive(a,b,r);if(k)v.push(petDnaPheno(k).morph[g.k])}
  if(!v.length)return {mean:1,lo:1,hi:1};v.sort(function(x,y){return x-y});
  return {mean:v.reduce(function(s,x){return s+x},0)/v.length,lo:v[Math.floor(v.length*0.1)],hi:v[Math.floor(v.length*0.9)]};
}
async function petBreedPreviews(){
  const rt=PET_RT;if(!rt||!rt.ready)return;
  const cvs=[].slice.call(document.querySelectorAll('#petBreed canvas.pet-bcv'));
  for(const cv of cvs){
    const k=cv.dataset.bk,L=petCattery().litter;let d=null;
    if(/^lit\d+$/.test(k)){d=L&&L.kits&&L.kits[+k.slice(3)]}else{const e=petBreedFind(k);d=e&&e.dna}
    if(!d)continue;await petBreedDraw(cv,d);
  }
}
async function petBreedDraw(cv,d){
  const ph=petDnaPheno(d),key=ph.key;let sh=PET_BREED.cv[key];
  if(!sh){sh=await petDnaSheet(key,'adult');if(!sh)return;PET_BREED.cv[key]=sh;const ks=Object.keys(PET_BREED.cv);if(ks.length>40)delete PET_BREED.cv[ks[0]]}
  if(!cv.isConnected)return;const rt=PET_RT,H=rt.A.H,x=cv.getContext('2d'),s=2;
  x.imageSmoothingEnabled=false;x.clearRect(0,0,cv.width,cv.height);
  /* the cat sits at the middle of the card with its feet near the bottom; a rig frame is bigger than the plain one, so its origin offsets it */
  const fr=typeof petRigFrame==='function'?petRigFrame(rt,sh,'adult','idle_sit',0,ph.morph,null):null,W=rt.A.W,ox=fr?fr.ox:0,oy=fr?fr.oy:0;
  const sx=Math.round(cv.width/2-(ox+W/2)*s),sy=cv.height-2-(oy+H)*s;
  if(fr)x.drawImage(fr.cv,0,0,fr.cv.width,fr.cv.height,sx,sy,fr.cv.width*s,fr.cv.height*s);
  else x.drawImage(sh,0,rt.A.anims.idle_sit.row*H,W,H,sx,sy,W*s,H*s);
}
function petBreedRender(force){
  const el=document.getElementById('petBreed');if(!el)return;
  const key=JSON.stringify([state.pet&&state.pet.dna&&state.pet.dna.id,state.pet2&&state.pet2.alive&&state.pet2.dna&&state.pet2.dna.id,petCattery().cats.map(function(c){return c.id+c.name}),petCattery().litter&&petCattery().litter.kits.length,petCattery().goal,PET_BREED.mom,PET_BREED.dad]);
  if(!force&&el.dataset.k===key)return;el.dataset.k=key;
  const keep=el.querySelector('details')?el.querySelector('details').open:false;
  el.innerHTML='<details class="pet-breed-d"'+(keep||el.dataset.o==='1'?' open':'')+'><summary>Breeding lab</summary><div class="pet-breed-b">'+petBreedHtml()+'</div></details>';
  const dt=el.querySelector('details');dt.addEventListener('toggle',function(){el.dataset.o=dt.open?'1':'0';if(dt.open)petBreedPreviews()});
  if(dt.open)petBreedPreviews();
}
function petBreedRefresh(){const el=document.getElementById('petBreed');if(el)el.dataset.k='';petBreedRender(true)}
function petBreedSetGoal(v){petCattery().goal=v;save();petBreedRefresh()}
function petBreedPick(w,k){PET_BREED[w]=k;petBreedRefresh()}
function petBreedGo(){
  const m=petBreedFind(PET_BREED.mom),f=petBreedFind(PET_BREED.dad);if(!m||!f)return;
  const C=petCattery(),r=petRng((Math.random()*4294967295)>>>0),w=Math.random(),n=w<0.15?2:w<0.45?3:w<0.75?4:w<0.95?5:6;
  const kits=petDnaLitter(m.dna,f.dna,n,r);
  kits.forEach(function(d){petDnaRegister(petDnaPheno(d).key)});
  C.litter={mom:m.name,dad:f.name,kits:kits,at:Date.now()};save();toast(m.name+' had '+kits.length+' kitten'+(kits.length===1?'':'s'));petBreedRefresh();
}
function petBreedAdd(d){
  const C=petCattery();if(C.cats.length>=PET_CATTERY_MAX){toast('The cattery is full. Release a cat first.');return false}
  const taken=petBreedRoster().map(function(e){return e.name});
  C.cats.push({id:'c'+Date.now().toString(36)+Math.floor(Math.random()*1296).toString(36),name:petBreedName(taken),dna:d,born:Date.now()});return true;
}
function petBreedKeep(i){const L=petCattery().litter;if(!L||!L.kits[i])return;if(!petBreedAdd(L.kits[i]))return;L.kits.splice(i,1);save();petBreedRefresh()}
function petBreedDrop(i){const L=petCattery().litter;if(!L||!L.kits[i])return;L.kits.splice(i,1);save();petBreedRefresh()}
function petBreedKeepAll(){const L=petCattery().litter;if(!L)return;while(L.kits.length&&petBreedAdd(L.kits[0]))L.kits.shift();save();petBreedRefresh()}
function petBreedDropAll(){const L=petCattery().litter;if(L)L.kits=[];save();petBreedRefresh()}
function petBreedRelease(k){
  const C=petCattery(),c=C.cats.find(function(x){return x.id===k});if(!c||!confirm('Release '+c.name+'? '+(petDnaSexOf(c.dna)==='f'?'She':'He')+' will not come back.'))return;
  C.cats=C.cats.filter(function(x){return x.id!==k});save();petBreedRefresh();
}
function petBreedRename(k){
  const c=petCattery().cats.find(function(x){return x.id===k});if(!c)return;const n=prompt('Name',c.name);if(n&&n.trim()){c.name=n.trim().slice(0,14);save();petBreedRefresh()}
}
function petBreedStray(){
  const r=petRng((Math.random()*4294967295)>>>0),d=petDnaRandom(r,{sex:Math.random()<0.5?'XX':'XY'});d.gen=0;petDnaRegister(petDnaPheno(d).key);
  if(petBreedAdd(d)){save();petBreedRefresh()}
}
function petBreedHome(k){
  const C=petCattery(),c=C.cats.find(function(x){return x.id===k});
  if(!c||!state.pet||!state.pet.alive||(state.pet2&&state.pet2.alive)){toast('Your second cat slot is taken.');return}
  const ph=petDnaPheno(c.dna);petDnaRegister(ph.key);
  window.__petDnaNext=c.dna;
  const p=petPairAdopt(ph.key,c.name,ph.trait);p.born=c.born;
  C.cats=C.cats.filter(function(x){return x.id!==k});
  const P=PET_PAIR;P.adopting=false;P.arrival=true;P.focus=1;if(P.rt2)petPairDrop();P.focus=1;
  save();toast(c.name+' has moved in.');petSync();
}
(function(){
  const room=petRoomHtml;
  petRoomHtml=function(p){const h=room.apply(this,arguments);return h.replace('<div id="petDev"></div>','<div class="pet-care pet-breed" id="petBreed"></div><div id="petDev"></div>')};
  const br=petBrainRender;petBrainRender=function(force){const r=br.apply(this,arguments);try{petBreedRender(force)}catch(e){console.warn('[pet breed]',e)}return r};
})();

/* ===== test buttons (developer mode) ===== */
function petDevBreedFill(){
  const C=petCattery();while(C.cats.length<8)if(!petBreedStray())break;toast('Cattery filled for experiments');
}
/* breeds N generations choosing the best pair of each litter for the current goal; reports the result */
function petDevBreedEvolve(gens){
  gens=gens||10;const g=petBreedGoal(),r=petRng(12345);
  let a=petDnaRandom(r,{sex:'XX'}),b=petDnaRandom(r,{sex:'XY'});
  const log=[petDnaPheno(a).morph[g.k].toFixed(2)+'/'+petDnaPheno(b).morph[g.k].toFixed(2)];
  for(let i=0;i<gens;i++){
    const kits=petDnaLitter(a,b,12,r).filter(function(k){return k.sex!=='XXY'});
    const fem=kits.filter(function(k){return k.sex==='XX'}),mal=kits.filter(function(k){return k.sex==='XY'});if(!fem.length||!mal.length)continue;
    const sc=function(k){return petBreedGoalVal(petDnaPheno(k))};
    fem.sort(function(x,y){return sc(y)-sc(x)});mal.sort(function(x,y){return sc(y)-sc(x)});
    a=fem[0];b=mal[0];log.push(petDnaPheno(a).morph[g.k].toFixed(2)+'/'+petDnaPheno(b).morph[g.k].toFixed(2));
  }
  console.log('[breed evolve] '+PET_MORPH[g.k].name+(g.up?' up':' down')+': '+log.join(' → '));toast(PET_MORPH[g.k].name+': '+log[0]+' → '+log[log.length-1]);return log;
}
petDevTest('Breeding: fill the cattery',petDevBreedFill,'v93');
petDevTest('Breeding: select for the goal (10 generations)',function(){petDevBreedEvolve(10)},'v93');

function petDevMorphSet(set){
  const p=petData();if(!p){toast('Adopt a cat first');return}const d=petDnaOf(p);petMorphFill(d);
  for(const k in set){if(k==='bob')continue;const a=set[k]>0?'+':'-';PET_QTL[k].loci.forEach(function(l){d.g[l[0]]=[a,a]})}
  if(set.bob!=null)d.g.Tb=set.bob?['tb','tb']:['T','T'];
  PET_DNA_C=null;petDnaRender(true);petBreedRefresh();
  const m=petDnaPheno(d).morph,kid=petGrowIdx(p)>=0;
  toast('Ears '+m.em.toFixed(1)+'× · tail '+m.tl.toFixed(1)+'× · legs '+m.lg.toFixed(1)+'× · body '+m.bl.toFixed(1)+'×'+(kid?' (a kitten shows about half until it is grown)':''));
}
petDevTest('Shape: very long ears',function(){petDevMorphSet({em:1})},'v94');
petDevTest('Shape: tiny ears',function(){petDevMorphSet({em:-1})},'v94');
petDevTest('Shape: long tail',function(){petDevMorphSet({tl:1,bob:0})},'v94');
petDevTest('Shape: bobtail',function(){petDevMorphSet({bob:1})},'v94');
petDevTest('Shape: leggy and long',function(){petDevMorphSet({lg:1,bl:1})},'v94');
petDevTest('Shape: short and cobby',function(){petDevMorphSet({lg:-1,bl:-1})},'v94');
petDevTest('Shape: everything extreme',function(){petDevMorphSet({em:1,ew:1,tl:1,lg:1,bl:1,wk:1,bob:0})},'v94');
petDevTest('Shape: back to random',function(){const p=petData();if(!p)return;const r=petRng((Math.random()*4294967295)>>>0);PET_MORPH_KEYS.forEach(function(k){PET_QTL[k].loci.forEach(function(l){p.dna.g[l[0]]=[r()<0.5?'+':'-',r()<0.5?'+':'-']})});p.dna.g.Tb=['T','T'];PET_DNA_C=null;petDnaRender(true);petBreedRefresh()},'v94');
