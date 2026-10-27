
/* ===== Cat DNA (v87): sex, a diploid genome, and what it does =====
   Every cat now carries a genome modelled on real feline genetics (Felis catus, 2n = 38: 18
   autosome pairs plus X/Y). A cat inherits one copy of every chromosome from its mother and one
   from its father, so every locus holds two alleles (maternal, paternal), except on the X of a
   male, which holds one. What the genes do is computed, never stored: the coat, eye colour, size,
   hearing and the personality all come from petDnaPheno(dna).

   - Sex: XX female, XY male. The Y (SRY) makes a male. About 1 kitten in 2,500 is XXY: a male who
     can be tortoiseshell, and is sterile (flagged for breeding later).
   - Coat colour and pattern: the real loci and their real dominance (O on the X, so tortoiseshell
     needs two X chromosomes; B/b/bl at TYRP1; D/d at MLPH; A/a at ASIP; Mc/mc at TAQPEP; Ta at
     DKK4; C/cb/cs/c at TYR with cb and cs codominant; W/ws/w at KIT; L/l at FGF5; I silver).
     Epistasis is modelled: dominant white hides everything; red hides agouti (red cats always show
     tabby markings); B is invisible on a red cat; non-agouti hides the tabby pattern.
   - Polygenic traits: white-spotting grade, eye colour, body size and six personality dimensions
     are quantitative traits: several additive loci each (modelled QTL, not named genes), plus a
     per-cat developmental share that is fixed at birth and not inherited (heritability 0.4-0.5 for
     temperament, roughly what cat studies report).
   - Personality uses the "Feline Five" (skittish, outgoing, dominant, spontaneous, friendly) plus
     vocal. It sets the legacy trait (shy/playful/lazy/curious) the rest of the engine reads, and
     re-weights behaviour options by category in the brain (petBrainArbiter).
   - Drawing: every coat is composed from the four original palette-indexed sheets (black = the
     shape, ginger/gray = the stripes, ginger/gray/tuxedo = three white-spotting masks), so no new
     art is needed. The four legacy coats reproduce their sheets pixel for pixel and keep their
     coat keys; any other coat gets a self-describing key ("dna_...") whose sheets are composed on
     demand through pseudo-URLs ("dna:<kind>:<key>") handed to petLoadImg. Colourpoints are found
     from the shape (thin parts: legs, tail, ears, plus a face mask) and darken as a kitten grows;
     longhair adds a fringed outline; classic tabby widens the stripes.
   - Groundwork for breeding (not used in play yet, except to make the stray litters):
     petDnaGamete (meiosis with crossing-over from approximate chromosome positions),
     petDnaConceive (fertilisation, rare XXY, rare new mutations), petDnaLitter.
   - Saved in state.pet.dna (~0.8 KB) and state.pet2.dna. Cats from before v87 get a genome that is
     consistent with the coat and trait they already have (male, as the app always called them).
   Hooks: none in other segments; this file wraps petAssetsJson, petLoadImg, petDrawCat, petAdopt,
   petPairAdopt, petIntroHtml, petPairAdoptHtml, petCoatPreviewDraw, petPickCoat, petRoomHtml,
   petBrainRender, petBrainArbiter, migratePetBrainState, petRebuildSprites, toast. */
var PET_DNA_V=1;
var PET_DNA_CHROMS=['A1','A2','A3','B1','B2','B3','B4','C1','C2','D1','D2','D3','D4','E1','E2','E3','F1','F2','X'];
/* loci. pos = approximate megabase position on the chromosome (only order and distance matter: they set
   how often genes are inherited together). al = alleles in dominance order with population frequency. */
var PET_LOCI=[
  {id:'O',gene:'ARHGAP36',name:'Orange',chr:'X',pos:109,al:[['O',0.22,'Orange: a ~5 kb deletion that switches pigment to red/yellow'],['o',0.78,'Non-orange']]},
  {id:'B',gene:'TYRP1',name:'Brown',chr:'D4',pos:36,al:[['B',0.9,'Black pigment'],['b',0.07,'Chocolate (TYRP1 variant)'],['bl',0.03,'Cinnamon (TYRP1 nonsense variant)']]},
  {id:'D',gene:'MLPH',name:'Dilute',chr:'C1',pos:223,al:[['D',0.7,'Dense pigment'],['d',0.3,'Dilute (MLPH c.83delT): black to blue, red to cream']]},
  {id:'A',gene:'ASIP',name:'Agouti',chr:'A3',pos:47,al:[['A',0.45,'Agouti: banded hairs, tabby pattern shows'],['a',0.55,'Non-agouti (2-bp deletion): solid colour']]},
  {id:'Mc',gene:'TAQPEP',name:'Tabby',chr:'A1',pos:197,al:[['Mc',0.5,'Mackerel stripes'],['mc',0.5,'Classic (blotched) tabby, recessive']]},
  {id:'Ta',gene:'DKK4',name:'Ticked',chr:'B1',pos:30,al:[['Ta',0.03,'Ticked: stripes fade to an even, flecked coat'],['ta',0.97,'Not ticked']]},
  {id:'C',gene:'TYR',name:'Colour',chr:'D1',pos:46,al:[['C',0.95,'Full colour'],['cb',0.015,'Burmese (TYR G227W): sepia'],['cs',0.03,'Siamese (TYR G302R): temperature-sensitive points'],['c',0.005,'Albino (TYR frameshift)']]},
  {id:'W',gene:'KIT',name:'White',chr:'B1',pos:133,al:[['W',0.012,'Dominant white (full FERV1 insertion in KIT)'],['ws',0.27,'White spotting (FERV1 LTR in KIT)'],['w',0.718,'No white']]},
  {id:'L',gene:'FGF5',name:'Hair length',chr:'B1',pos:156,al:[['L',0.72,'Short hair'],['l',0.28,'Long hair (FGF5 loss of function), recessive']]},
  {id:'I',gene:'unknown',name:'Inhibitor',chr:'',pos:0,al:[['I',0.05,'Silver: pigment kept out of the hair base'],['i',0.95,'No silver']]}
];
/* quantitative trait loci: '+' adds, '-' does not. h2 = heritability of the trait. */
var PET_QTL={
  sm:{name:'White spotting grade',loci:[['Sm1','B3',61],['Sm2','E1',22],['Sm3','A2',140]]},
  ey:{name:'Eye pigment',loci:[['Ey1','A1',58],['Ey2','C2',101],['Ey3','D2',77],['Ey4','F1',40]]},
  sz:{name:'Body size',loci:[['Sz1','A2',35],['Sz2','B4',88],['Sz3','D3',52],['Sz4','E2',19]]},
  sk:{name:'Skittish',h2:0.5,loci:[['Sk1','A1',120],['Sk2','B2',44],['Sk3','E3',30]]},
  ou:{name:'Outgoing',h2:0.45,loci:[['Ou1','A3',112],['Ou2','C1',78],['Ou3','D4',90]]},
  dm:{name:'Dominant',h2:0.4,loci:[['Dm1','B1',80],['Dm2','C2',30],['Dm3','F2',55]]},
  sp:{name:'Spontaneous',h2:0.4,loci:[['Sp1','A2',90],['Sp2','D1',110],['Sp3','E1',60]]},
  fr:{name:'Friendly',h2:0.45,loci:[['Fr1','B4',20],['Fr2','D2',15],['Fr3','F1',70]]},
  vo:{name:'Vocal',h2:0.5,loci:[['Vo1','B3',140],['Vo2','D3',100]]}
};
var PET_PERSONA=['sk','ou','dm','sp','fr','vo'];
var PET_PERSONA_TXT={sk:['Skittish','Startles easily, likes high and hidden places','Steady nerves'],ou:['Outgoing','Explores, watches the window, wanders','Homebody'],
  dm:['Dominant','Bossy with other cats, scratches to mark','Easygoing'],sp:['Spontaneous','Sudden zoomies and games','Predictable'],
  fr:['Friendly','Rubs, slow blinks, comes to the glass','Reserved'],vo:['Vocal','Talks a lot','Quiet']};
var PET_DNA_MUT=2e-5;          /* per locus per gamete: rare, but it happens */
var PET_DNA_XXY=1/2500;        /* an extra X from non-disjunction */
var PET_DNA_KEYS={};           /* custom coat keys registered for drawing */
var PET_DNA_C=null;            /* phenotype cache */

/* ---------- locus helpers ---------- */
function petDnaLocus(id){for(const L of PET_LOCI)if(L.id===id)return L;return null}
function petDnaQtlIds(){const o=[];for(const k in PET_QTL)PET_QTL[k].loci.forEach(function(l){o.push(l[0])});return o}
function petDnaAllLoci(){   /* [{id,chr,pos,al:[[a,freq]]}] for meiosis and display */
  const o=PET_LOCI.map(function(L){return {id:L.id,chr:L.chr,pos:L.pos,al:L.al,q:0}});
  for(const k in PET_QTL)PET_QTL[k].loci.forEach(function(l){o.push({id:l[0],chr:l[1],pos:l[2],al:[['+',0.5],['-',0.5]],q:k})});
  return o;
}
function petDnaDraw(al,r){let x=r(),t=0;for(const a of al){t+=a[1];if(x<t)return a[0]}return al[al.length-1][0]}
function petDnaCount(d,ids){let n=0;ids.forEach(function(id){(d.g[id]||[]).forEach(function(a){if(a==='+')n++})});return n}
function petDnaHas(d,id,a){return (d.g[id]||[]).indexOf(a)>=0}
function petDnaSexOf(d){return d.sex==='XX'?'f':'m'}
function petDnaFertile(d){return d.sex!=='XXY'}

/* ---------- a founder: alleles drawn from the population frequencies (Hardy-Weinberg) ---------- */
function petDnaRandom(r,opt){
  opt=opt||{};r=r||Math.random;
  const sex=opt.sex||(r()<PET_DNA_XXY?'XXY':r()<0.5?'XX':'XY'),g={},nx=sex==='XY'?1:2;
  petDnaAllLoci().forEach(function(L){const n=L.chr==='X'?nx:2,v=[];for(let i=0;i<n;i++)v.push(petDnaDraw(L.al,r));g[L.id]=v});
  return {v:PET_DNA_V,id:petDnaId(r),sex:sex,g:g,dev:Math.floor(r()*4294967295)>>>0,mut:[]};
}
function petDnaId(r){r=r||Math.random;let s='';for(let i=0;i<8;i++)s+='0123456789abcdefghjkmnpqrstvwxyz'[Math.floor(r()*32)];return s}

/* ---------- meiosis: one gamete, with crossing-over (Haldane map, ~1 cM per Mb) ---------- */
function petDnaGamete(d,r){
  r=r||Math.random;if(!petDnaFertile(d))return null;
  const L=petDnaAllLoci(),byChr={},out={g:{},x:true,mut:[]};
  L.forEach(function(l){(byChr[l.chr||('u'+l.id)]=byChr[l.chr||('u'+l.id)]||[]).push(l)});
  const male=d.sex==='XY';
  if(male)out.x=r()<0.5;                                  /* a sperm carries his X or his Y */
  for(const c in byChr){const loci=byChr[c].slice().sort(function(a,b){return a.pos-b.pos});
    if(c==='X'){if(male){if(out.x)loci.forEach(function(l){out.g[l.id]=d.g[l.id][0]});continue}}
    let h=r()<0.5?0:1,prev=null;
    loci.forEach(function(l){
      if(prev!=null){const cm=Math.abs(l.pos-prev),rf=0.5*(1-Math.exp(-2*cm/100));if(r()<rf)h=1-h}
      prev=l.pos;out.g[l.id]=d.g[l.id][h]});
  }
  /* rare new mutations */
  L.forEach(function(l){if(out.g[l.id]==null)return;if(r()<PET_DNA_MUT){const alts=l.al.map(function(a){return a[0]}).filter(function(a){return a!==out.g[l.id]});
    if(alts.length){const nu=alts[Math.floor(r()*alts.length)];out.mut.push(l.id+':'+out.g[l.id]+'>'+nu);out.g[l.id]=nu}}});
  return out;
}
/* fertilisation: index 0 = maternal copy, 1 = paternal copy */
function petDnaConceive(mom,dad,r){
  r=r||Math.random;const e=petDnaGamete(mom,r),s=petDnaGamete(dad,r);if(!e||!s)return null;
  let sex=s.x?'XX':'XY';const extraX=r()<PET_DNA_XXY&&!s.x;if(extraX)sex='XXY';
  const g={};petDnaAllLoci().forEach(function(l){
    if(l.chr==='X'){if(sex==='XX')g[l.id]=[e.g[l.id],s.g[l.id]];else if(sex==='XY')g[l.id]=[e.g[l.id]];
      else g[l.id]=[e.g[l.id],mom.g[l.id][mom.g[l.id][0]===e.g[l.id]?1:0]]}            /* XXY: both of mom's X */
    else g[l.id]=[e.g[l.id],s.g[l.id]]});
  return {v:PET_DNA_V,id:petDnaId(r),sex:sex,g:g,dev:Math.floor(r()*4294967295)>>>0,mut:e.mut.concat(s.mut),par:{m:mom.id,f:dad.id}};
}
function petDnaLitter(mom,dad,n,r){r=r||Math.random;const o=[];for(let i=0;i<n;i++){const k=petDnaConceive(mom,dad,r);if(k)o.push(k)}return o}

/* ---------- phenotype ---------- */
function petDnaEnv(d,k){const r=petRng(petHash(d.dev+'|'+k));return r()+r()-1}   /* -1..1, bell-shaped, fixed at birth */
function petDnaScore(d,k){const Q=PET_QTL[k],ids=Q.loci.map(function(l){return l[0]}),z=petDnaCount(d,ids)/(2*ids.length)*2-1,h=Q.h2;
  return petBC(0.5+0.5*(Math.sqrt(h)*z+Math.sqrt(1-h)*petDnaEnv(d,k)),0,1)}
var PET_EYES=[['Deep green',[60,140,74],[150,224,122]],['Green',[60,130,70],[140,210,110]],['Hazel',[120,128,40],[200,200,84]],
  ['Gold',[200,140,20],[255,220,70]],['Amber',[170,110,20],[250,200,60]],['Copper',[170,70,20],[240,140,50]]];
var PET_EYES_X={b:['Blue',[40,90,170],[120,180,240]],q:['Aqua',[46,128,136],[128,208,206]],p:['Pale blue',[140,170,210],[204,226,250]]};
function petDnaPheno(d){
  if(!d||!d.g)return null;
  PET_DNA_C=PET_DNA_C||new WeakMap();const hit=PET_DNA_C.get(d);if(hit&&hit.sig===JSON.stringify(d.g)+d.sex)return hit.ph;
  const g=d.g,has=function(id,a){return petDnaHas(d,id,a)},both=function(id,a){const v=g[id]||[];return v.length>0&&v.every(function(x){return x===a})};
  const dev=petRng(petHash(d.dev+'|dev'));
  const ph={sex:petDnaSexOf(d),karyo:d.sex==='XXY'?'39,XXY':'38,'+d.sex,sterile:d.sex==='XXY',notes:[]};
  /* orange (X-linked) */
  const ox=g.O||[],nO=ox.filter(function(a){return a==='O'}).length;
  const red=nO===ox.length&&nO>0,tortie=nO>0&&nO<ox.length;
  /* eumelanin colour */
  const bb=both('B','B')?'B':has('B','B')?'B':has('B','b')?'b':'bl',dil=both('D','d');
  const eu=bb==='B'?(dil?'blue':'black'):bb==='b'?(dil?'lilac':'chocolate'):(dil?'fawn':'cinnamon');
  const agouti=has('A','A');
  const pattern=has('Ta','Ta')?'ticked':has('Mc','Mc')?'mackerel':'classic';
  /* TYR: C > cb = cs > c */
  const cc=g.C||['C','C'];let point=null;
  if(cc.indexOf('C')<0){const s=cc.slice().sort().join('/');point=s==='c/c'?'al':s==='cb/cs'?'mk':(s==='cb/cb'||s==='c/cb')?'sp':'cs'}
  /* KIT */
  const nW=(g.W||[]).filter(function(a){return a==='W'}).length,nWs=(g.W||[]).filter(function(a){return a==='ws'}).length,sm=petDnaCount(d,['Sm1','Sm2','Sm3']);
  let white=0;if(nW)white='W';else if(nWs===1)white=sm<=2?1:sm<=4?2:3;else if(nWs===2)white=sm<=2?3:4;
  const silver=has('I','I'),long=both('L','l');
  /* eyes */
  const ec=petDnaCount(d,['Ey1','Ey2','Ey3','Ey4']);let eye=[0,0,1,1,2,3,4,5,5][ec];
  let blue=false,deaf=false;
  if(white==='W'){blue=dev()<0.4;deaf=dev()<(blue?0.65:0.2)}else{dev();dev()}
  if(point==='cs')eye='b';else if(point==='mk')eye='q';else if(point==='al')eye='p';else if(blue)eye='b';
  /* size: a female is 3-5 kg, a male about a quarter heavier */
  const sz=petDnaCount(d,['Sz1','Sz2','Sz3','Sz4']);
  ph.kg=Math.round((3+sz*0.25)*(ph.sex==='m'?1.25:1)*10)/10;ph.sizeN=sz/8;
  /* personality */
  ph.ps={};PET_PERSONA.forEach(function(k){ph.ps[k]=petDnaScore(d,k)});
  if(deaf)ph.ps.vo=petBC(ph.ps.vo+0.25,0,1);              /* deaf cats are often louder */
  const P=ph.ps,cand={shy:P.sk,playful:P.sp,curious:P.ou,lazy:1-(P.ou+P.sp)/2-0.04};
  let best='curious';for(const k in cand)if(cand[k]>cand[best])best=k;ph.trait=best;
  /* recipe: everything the drawing needs */
  let base=red?(dil?'cream':'red'):eu;
  const tab=(white==='W'||point==='al')?null:(red||agouti)?pattern:null;
  const rec={base:base,tortie:tortie&&white!=='W'&&point!=='al'?1:0,tab:tab,white:white,silver:silver&&white!=='W'&&point!=='al'?1:0,point:point==='al'?null:point,long:long?1:0,eye:eye};
  if(white==='W')Object.assign(rec,{base:'white',tortie:0,tab:null,white:0,silver:0,point:null});
  if(point==='al')Object.assign(rec,{base:'albino',tortie:0,tab:null,white:0,silver:0,point:null});
  ph.rec=rec;ph.key=petDnaKey(rec);
  ph.red=red;ph.tortie=tortie;ph.eu=eu;ph.dilute=dil;ph.agouti=agouti;ph.pattern=pattern;ph.point=point;ph.white=white;ph.silver=silver;ph.long=long;ph.deaf=deaf;
  ph.eyeName=(typeof eye==='string'?PET_EYES_X[eye]:PET_EYES[eye])[0];
  ph.coat=petDnaCoatName(ph);
  PET_DNA_C.set(d,{sig:JSON.stringify(d.g)+d.sex,ph:ph});
  return ph;
}
var PET_DNA_LEGACY={
  ginger:{base:'red',tortie:0,tab:'mackerel',white:1,silver:0,point:null,long:0,eye:1},
  gray:{base:'blue',tortie:0,tab:'mackerel',white:2,silver:0,point:null,long:0,eye:4},
  black:{base:'black',tortie:0,tab:null,white:0,silver:0,point:null,long:0,eye:3},
  tuxedo:{base:'black',tortie:0,tab:null,white:3,silver:0,point:null,long:0,eye:0}
};
function petDnaKey(rec){
  for(const k in PET_DNA_LEGACY){const L=PET_DNA_LEGACY[k];if(Object.keys(L).every(function(f){return L[f]===rec[f]}))return k}
  return 'dna_'+[rec.base,rec.tortie,rec.tab?rec.tab[0]:'n',rec.white,rec.silver,rec.point||'n',rec.long,rec.eye].join('_');
}
function petDnaRecipe(key){
  if(PET_DNA_LEGACY[key])return PET_DNA_LEGACY[key];
  const f=String(key).split('_');if(f[0]!=='dna'||f.length<9)return null;
  const num=function(x){return /^\d+$/.test(x)?+x:x};
  return {base:f[1],tortie:+f[2],tab:{m:'mackerel',c:'classic',t:'ticked'}[f[3]]||null,white:num(f[4]),silver:+f[5],point:f[6]==='n'?null:f[6],long:+f[7],eye:num(f[8])};
}
var PET_EU_NAME={black:'Black',chocolate:'Chocolate',cinnamon:'Cinnamon',blue:'Blue',lilac:'Lilac',fawn:'Fawn'};
var PET_TAB_NAME={black:'Brown',chocolate:'Chocolate',cinnamon:'Cinnamon',blue:'Blue',lilac:'Lilac',fawn:'Fawn',red:'Ginger',cream:'Cream'};
function petDnaCoatName(ph){
  const r=ph.rec;let n;
  if(r.base==='white')return 'White';
  if(r.base==='albino')return 'Albino';
  const tortName={black:'Tortoiseshell',blue:'Blue-cream',chocolate:'Chocolate tortie',lilac:'Lilac-cream',cinnamon:'Cinnamon tortie',fawn:'Fawn-cream'};
  if(r.point){
    const pc={black:'Seal',chocolate:'Chocolate',cinnamon:'Cinnamon',blue:'Blue',lilac:'Lilac',fawn:'Fawn',red:'Flame',cream:'Cream'}[r.base]||'Seal';
    n=pc+(r.tortie?' tortie':'')+(r.tab&&!r.tortie&&r.base!=='red'&&r.base!=='cream'?' lynx':'')+' '+{cs:'point',mk:'mink',sp:'sepia'}[r.point];
  }else if(r.tortie){
    if(r.white>=2)n=(ph.dilute?'Dilute calico':'Calico');
    else n=r.tab?((ph.dilute?'Dilute torbie':'Torbie')):tortName[r.base];
    if(r.silver)n='Silver '+n.toLowerCase();
  }else if(r.tab){
    const c=r.silver?({black:'Silver',chocolate:'Chocolate silver',cinnamon:'Cinnamon silver',blue:'Blue silver',lilac:'Lilac silver',fawn:'Fawn silver',red:'Cameo',cream:'Cream cameo'}[r.base]):PET_TAB_NAME[r.base];
    n=c+' '+r.tab+' tabby';
  }else n=PET_EU_NAME[r.base]+(r.silver?' smoke':'');
  if(!(r.tortie&&r.white>=2&&!r.point)){
    if(r.white===1)n+=' with a white locket';
    else if(r.white===2)n+=' & white';
    else if(r.white===3)n=(r.base==='black'&&!r.tab&&!r.tortie&&!r.point&&!r.silver)?'Tuxedo':n+' bicolor';
    else if(r.white===4)n+=' van';
  }else if(r.white===4)n+=' van';
  if(r.long)n='Longhaired '+n.charAt(0).toLowerCase()+n.slice(1);
  return n;
}

/* ---------- colours (index -> RGB). 10, 120-210 are the outline, nose, mouth and eyes and never change ---------- */
var PET_DNA_COL={
  black:{solid:[[10,8,16],[26,22,44],[46,40,68],[90,80,128]],tg:[[70,44,24],[112,74,40],[158,112,62],[200,156,100]],ts:[[10,8,16],[26,22,40],[48,38,46]],pt:[[30,18,16],[56,36,30],[84,58,48],[120,90,76]]},
  chocolate:{solid:[[40,22,18],[70,40,30],[104,64,46],[148,102,76]],tg:[[100,64,44],[140,96,66],[180,134,96],[214,176,136]],ts:[[40,22,18],[62,36,26],[90,56,40]]},
  cinnamon:{solid:[[78,38,22],[120,64,38],[160,98,62],[202,144,104]],tg:[[128,78,46],[168,112,70],[204,152,104],[232,194,150]],ts:[[78,38,22],[104,54,32],[136,78,48]]},
  blue:{solid:[[36,40,56],[60,66,86],[92,100,124],[138,148,172]],tg:[[52,58,74],[84,92,112],[124,134,156],[170,180,200]],ts:[[30,34,46],[50,56,72],[76,84,104]]},
  lilac:{solid:[[88,78,90],[122,110,124],[160,148,160],[198,188,198]],tg:[[118,106,114],[156,144,152],[190,180,186],[222,214,218]],ts:[[80,70,82],[104,92,106],[130,118,130]]},
  fawn:{solid:[[118,92,82],[156,124,110],[192,160,142],[224,196,180]],tg:[[150,120,104],[186,154,136],[214,186,168],[238,218,202]],ts:[[112,86,76],[136,106,94],[164,132,118]]},
  red:{tg:[[122,50,20],[176,84,30],[222,128,48],[246,176,96]],ts:[[92,34,14],[134,46,14],[176,84,30]]},
  cream:{tg:[[156,110,74],[200,150,106],[232,192,146],[250,222,184]],ts:[[126,82,52],[160,110,72],[196,146,100]]}
};
var PET_DNA_SILVER={dense:[[96,100,116],[150,156,172],[204,210,222],[236,240,248]],dil:[[120,126,142],[170,176,192],[214,220,232],[240,244,250]],
  red:[[176,118,98],[220,168,148],[244,212,196],[255,238,228]],redS:[[150,60,30],[190,96,60],[220,140,100]],
  cream:[[200,160,140],[230,196,180],[246,226,214],[255,244,236]],creamS:[[170,110,80],[200,146,110],[226,180,148]]};
var PET_DNA_FIXED={'10':[26,16,28],'120':[150,70,79],'130':[222,120,130],'140':[250,176,176],'150':[20,14,22],'180':[255,255,255],'190':[233,228,238],'200':[74,34,48],'210':[226,86,110]};
function petMix(a,b,t){return [Math.round(a[0]+(b[0]-a[0])*t),Math.round(a[1]+(b[1]-a[1])*t),Math.round(a[2]+(b[2]-a[2])*t)]}
function petDnaPalette(rec){
  const P={};for(const k in PET_DNA_FIXED)P[k]=PET_DNA_FIXED[k].slice();
  const set=function(keys,cols){keys.forEach(function(k,i){P[String(k)]=cols[i].slice()})};
  const S=[20,30,40,50],T=[60,70,80];
  const eye=typeof rec.eye==='string'?PET_EYES_X[rec.eye]:PET_EYES[rec.eye]||PET_EYES[3];P['160']=eye[1].slice();P['170']=eye[2].slice();
  const isRed=rec.base==='red'||rec.base==='cream',C=PET_DNA_COL[rec.base]||PET_DNA_COL.black;
  /* white fur: warm on a ginger cat, brighter on a strongly white cat */
  const wh=(isRed&&!rec.tortie)?[[170,150,140],[236,222,204],[255,248,236]]:rec.white>=3?[[154,160,180],[220,226,240],[250,252,255]]:[[150,156,176],[214,220,236],[246,248,255]];
  set([90,100,110],wh);
  if(rec.base==='white'||rec.base==='albino'){
    const W=rec.base==='white'?[[176,180,196],[214,218,232],[238,240,248],[252,252,255]]:[[200,186,186],[226,214,214],[244,236,236],[255,250,250]];
    set(S,W);set(T,[W[1],W[2],W[2]]);set([75,85],[W[2],W[3]]);[0,1,2].forEach(function(o){set([220+o,230+o,240+o,250+o],W)});return P}
  let ground,stripes;
  if(isRed){ground=C.tg;stripes=C.ts;
    if(rec.silver){ground=rec.base==='red'?PET_DNA_SILVER.red:PET_DNA_SILVER.cream;stripes=rec.base==='red'?PET_DNA_SILVER.redS:PET_DNA_SILVER.creamS}}
  else if(rec.tab){ground=C.tg;stripes=C.ts;if(rec.silver)ground=/blue|lilac|fawn/.test(rec.base)?PET_DNA_SILVER.dil:PET_DNA_SILVER.dense}
  else{ground=C.solid;stripes=[C.solid[0],C.solid[1],C.solid[2]];
    if(rec.silver)ground=[ground[0],ground[1],petMix(ground[2],[200,204,216],0.1),petMix(ground[3],[214,218,230],0.3)]}   /* smoke: pale undercoat */
  if(rec.tab==='ticked')stripes=[petMix(stripes[0],ground[1],0.45),petMix(stripes[1],ground[2],0.55),petMix(stripes[2],ground[2],0.6)];
  set(S,ground);set(T,stripes);
  if(rec.tortie){const dil=/blue|lilac|fawn/.test(rec.base),R=dil?PET_DNA_COL.cream.tg:PET_DNA_COL.red.tg;set([75,85],rec.silver?[petMix(R[1],[236,232,228],0.35),petMix(R[2],[246,240,236],0.35)]:[R[1],R[2]])}
  /* colourpoints: pale body, dark points (220 full, 221 mid, 222 faint for young kittens) */
  if(rec.point){
    const pc=isRed?C.tg:(rec.base==='black'?C.pt:C.solid);
    const k=rec.point==='cs'?0.78:rec.point==='mk'?0.5:0.22,tone=rec.point==='sp'?[176,146,118]:[238,228,212];
    const body=pc.map(function(c){return petMix(c,tone,k)});
    set(S,body);if(!rec.tortie)set(T,[body[1],body[2],body[2]]);
    const pts=rec.point==='sp'?pc.map(function(c){return petMix(c,[20,14,12],0.1)}):pc;
    [0,1,2].forEach(function(o){set([220+o,230+o,240+o,250+o],pts.map(function(c,i){return petMix(c,body[i],[0,0.45,0.78][o])}))});
  }
  return P;
}

/* ---------- composing a sheet from the four original ones ---------- */
var PET_DNA_SRC=null;
function petDnaSrc(){
  if(PET_DNA_SRC)return PET_DNA_SRC;
  const A=petAssetsJson_dna0(),G=petGrowthJson();PET_DNA_SRC={A:A,G:G};return PET_DNA_SRC;
}
function petDnaSrcUrls(kind){
  const S=petDnaSrc(),A=S.A;let m;
  if(kind==='adult')return A.coats;
  if(kind==='depth')return A.depth&&A.depth.coats;
  if((m=/^st(\d)$/.exec(kind)))return S.G&&S.G.stages[+m[1]]&&S.G.stages[+m[1]].coats;
  if((m=/^dst(\d)$/.exec(kind)))return A.depth&&A.depth.stages&&A.depth.stages[+m[1]]&&A.depth.stages[+m[1]].coats;
  if((m=/^hd(.+)$/.exec(kind))){const B=typeof petHdJson==='function'?petHdJson():null;return B&&B.kinds[m[1]]&&B.kinds[m[1]].sheet}      /* the 2x walking sheets */
  return null;
}
var PET_DNA_IDX={};   /* small cache of decoded index sheets, by kind */
async function petDnaSrcIdx(kind){
  if(PET_DNA_IDX[kind])return PET_DNA_IDX[kind];
  const U=petDnaSrcUrls(kind);if(!U)return null;const out={};
  for(const c of ['ginger','gray','black','tuxedo']){const img=await petLoadImg_dna0(U[c]);const cv=document.createElement('canvas');cv.width=img.width;cv.height=img.height;
    const x=cv.getContext('2d',{willReadFrequently:true});x.drawImage(img,0,0);const d=x.getImageData(0,0,cv.width,cv.height).data,a=new Uint8Array(cv.width*cv.height);
    for(let i=0,j=0;j<a.length;i+=4,j++)a[j]=d[i];out[c]=a;out.w=cv.width;out.h=cv.height}
  const ks=Object.keys(PET_DNA_IDX);if(ks.length>=3)delete PET_DNA_IDX[ks[0]];
  PET_DNA_IDX[kind]=out;return out;
}
function petDnaIsW(v){return v===90||v===100||v===110}
function petDnaIsS(v){return v===60||v===70||v===80}
function petDnaIsF(v){return v>=20&&v<=50}
function petDnaIsP(v){return v===75||v===85}        /* tortoiseshell red patches */
function petDnaCompose(kind,rec,S){
  const sc=/^hd/.test(kind)?2:1;kind=String(kind).replace(/^hd/,'');        /* 2x sheets: every width and distance below doubles */
  const w=S.w,h=S.h,n=w*h,G=S.ginger,Y=S.gray,B=S.black;let o;
  const tabGeom=!!(rec.tab||rec.tortie||rec.base==='red'||rec.base==='cream');
  if(tabGeom){o=G.slice();for(let i=0;i<n;i++)if(petDnaIsW(G[i]))o[i]=petDnaIsW(Y[i])?B[i]:Y[i]}else o=B.slice();
  /* classic tabby: wider, blotchier markings (one light stripe pixel either side, half below) */
  if(rec.tab==='classic'&&!rec.tortie){const s=o.slice();
    for(let y=1;y<h-1;y++)for(let x=1;x<w-1;x++){const i=y*w+x;if(!petDnaIsF(s[i]))continue;
      let hit=false;for(let k=1;k<=sc&&!hit;k++){if((x-k>=0&&s[i-k]===80)||(x+k<w&&s[i+k]===80))hit=true}
      if(!hit&&y>=sc&&s[i-sc*w]===80&&((x+y)&1))hit=true;
      if(hit)o[i]=80}}
  /* tortoiseshell: the stripe layer, spread into patches, plus the sunlit fur on the back and head */
  if(rec.tortie){const s=o.slice();
    for(let y=1;y<h-1;y++)for(let x=1;x<w-1;x++){const i=y*w+x;if(!petDnaIsF(s[i]))continue;
      let hit=false;for(let k=1;k<=sc&&!hit;k++){if((x-k>=0&&petDnaIsS(s[i-k]))||(x+k<w&&petDnaIsS(s[i+k])))hit=true}
      if(!hit&&y>=sc&&petDnaIsS(s[i-sc*w])&&((x+y)&1))hit=true;
      if(hit)o[i]=s[i]===20?75:85}
    if(!rec.tab)for(let i=0;i<n;i++){if(o[i]===80||o[i]===70)o[i]=85;else if(o[i]===60)o[i]=75}}
  /* colourpoints: legs, tail, ears (thin parts) and a mask around the eyes and nose */
  if(rec.point){
    const st=/^d?st(\d)$/.exec(kind),gi=st?+st[1]:6,off=gi<=1?2:gi<=3?1:0,thin=(gi<=3?3:4)*sc;
    const body=function(v){return v!==0&&v!==10},hr=new Uint8Array(n),vr=new Uint8Array(n);
    for(let y=0;y<h;y++){let x=0;while(x<w){if(!body(o[y*w+x])){x++;continue}let e=x;while(e<w&&body(o[y*w+e]))e++;const L=Math.min(255,e-x);for(let k=x;k<e;k++)hr[y*w+k]=L;x=e}}
    for(let x=0;x<w;x++){let y=0;while(y<h){if(!body(o[y*w+x])){y++;continue}let e=y;while(e<h&&body(o[e*w+x]))e++;const L=Math.min(255,e-y);for(let k=y;k<e;k++)vr[k*w+x]=L;y=e}}
    const face=new Uint8Array(n);
    for(let i=0;i<n;i++){const v=o[i];if(v>=120&&v<=180){const x=i%w,y=(i/w)|0;for(let dy=-2*sc;dy<=2*sc;dy++)for(let dx=-2*sc;dx<=2*sc;dx++){const xx=x+dx,yy=y+dy;if(xx>=0&&yy>=0&&xx<w&&yy<h)face[yy*w+xx]=1}}}
    const s=o.slice();
    for(let i=0;i<n;i++){const v=s[i];if(!(petDnaIsF(v)||petDnaIsS(v)||petDnaIsP(v)))continue;
      const inPt=Math.min(hr[i],vr[i])<=thin||face[i];
      if(inPt){if(petDnaIsP(v))continue;o[i]=(petDnaIsS(v)?(v===80?230:220):220+(v-20))+off}
      else if(petDnaIsS(v)||petDnaIsP(v))o[i]=40}
  }
  /* white spotting: the original sheets hold three graded masks */
  const M={1:[G],2:[Y],3:[S.tuxedo],4:[S.tuxedo,Y]}[rec.white];
  if(M)for(let i=0;i<n;i++){for(const m of M){if(petDnaIsW(m[i])){o[i]=m[i];break}}}
  /* long hair: a fringed outline on the top and the sides (never under the feet) */
  if(rec.long){const s=o.slice();
    for(let y=1;y<h-1;y++)for(let x=1;x<w-1;x++){const i=y*w+x;if(s[i]!==10)continue;
      const fx=x%(72*sc),fy=y%(48*sc);if(fy>=44*sc)continue;
      /* a soft tuft: the outline steps out one pixel and the fur shows through where it was */
      const fur=function(v){return petDnaIsF(v)||petDnaIsS(v)||petDnaIsW(v)||petDnaIsP(v)};
      if(s[i-w]===0&&fur(s[i+w])&&((fx+fy*2)%3===0)&&fy>4*sc){o[i]=s[i+w];o[i-w]=10}
      else if(s[i-1]===0&&fur(s[i+1])&&((fy*3+fx)%3===0)){o[i]=s[i+1];o[i-1]=10}
      else if(s[i+1]===0&&fur(s[i-1])&&((fy*3+fx)%3===0)){o[i]=s[i-1];o[i+1]=10}}}
  return o;
}
function petDnaIdxCanvas(o,w,h){
  const cv=document.createElement('canvas');cv.width=w;cv.height=h;const x=cv.getContext('2d',{willReadFrequently:true}),id=x.createImageData(w,h),d=id.data;
  for(let i=0,j=0;j<o.length;i+=4,j++){const v=o[j];d[i]=d[i+1]=d[i+2]=v;d[i+3]=255}
  x.putImageData(id,0,0);return cv;
}
async function petDnaPseudoImg(src){
  const m=/^dna:([a-z0-9]+):(.+)$/.exec(src);
  try{if(!m)throw Error('bad pseudo-url');const rec=petDnaRecipe(m[2]);const S=await petDnaSrcIdx(m[1]);if(!rec||!S)throw Error('no source for '+src);
    return petDnaIdxCanvas(petDnaCompose(m[1],rec,S),S.w,S.h)}
  catch(e){console.warn('[pet dna]',e);const cv=document.createElement('canvas');cv.width=1;cv.height=1;return cv}
}
/* a tinted sheet built directly (previews; not registered) */
async function petDnaSheet(key,kind){
  const rec=petDnaRecipe(key);if(!rec)return null;const S=await petDnaSrcIdx(kind);if(!S)return null;
  const o=petDnaCompose(kind,rec,S),pal=petDnaPalette(rec),cv=document.createElement('canvas');cv.width=S.w;cv.height=S.h;
  const x=cv.getContext('2d',{willReadFrequently:true}),id=x.createImageData(S.w,S.h),d=id.data;
  for(let i=0,j=0;j<o.length;i+=4,j++){const c=o[j]?pal[String(o[j])]:null;if(c){d[i]=c[0];d[i+1]=c[1];d[i+2]=c[2];d[i+3]=255}}
  x.putImageData(id,0,0);return cv;
}

/* ---------- registering a coat key so the engine's own loaders can build it ---------- */
function petDnaRegisterInto(A,K){
  if(!A||PET_DNA_LEGACY[K]||!A.coats)return;const rec=petDnaRecipe(K);if(!rec)return;
  A.coats[K]='dna:adult:'+K;A.pal[K]=petDnaPalette(rec);
  if(A.depth){A.depth.coats[K]='dna:depth:'+K;(A.depth.stages||[]).forEach(function(s,i){s.coats[K]='dna:dst'+i+':'+K})}
}
function petDnaRegister(K){
  if(!K||PET_DNA_LEGACY[K]||!petDnaRecipe(K))return;PET_DNA_KEYS[K]=1;
  if(PET_RT&&PET_RT.A)petDnaRegisterInto(PET_RT.A,K);
  const G=petGrowthJson();if(G&&G.stages)G.stages.forEach(function(s,i){s.coats[K]='dna:st'+i+':'+K});
}
function petDnaUnregister(K){
  if(!K||PET_DNA_LEGACY[K])return;delete PET_DNA_KEYS[K];const rt=PET_RT;
  if(rt&&rt.A){delete rt.A.coats[K];delete rt.A.pal[K];if(rt.A.depth){delete rt.A.depth.coats[K];(rt.A.depth.stages||[]).forEach(function(s){delete s.coats[K]})}}
  if(rt){if(rt.sheets)delete rt.sheets[K];if(rt.dsheets)delete rt.dsheets[K]}
  const G=petGrowthJson();if(G&&G.stages)G.stages.forEach(function(s){delete s.coats[K]});
}
var PET_DNA_BUSY={};
async function petDnaEnsureSheets(rt,K){
  if(!rt||!rt.A||!K||PET_DNA_LEGACY[K]||PET_DNA_BUSY[K])return;
  if(!rt.A.coats[K])petDnaRegister(K);if(!rt.A.coats[K])return;
  if(rt.sheets[K]&&(!rt.A.depth||rt.dsheets[K]))return;
  PET_DNA_BUSY[K]=1;
  try{if(!rt.sheets[K])rt.sheets[K]=await petTintSheet(rt.A.coats[K],rt.A.pal[K]);
    if(rt.A.depth&&!rt.dsheets[K])rt.dsheets[K]=await petTintSheet(rt.A.depth.coats[K],rt.A.pal[K])}
  catch(e){console.warn('[pet dna] sheets',e)}finally{delete PET_DNA_BUSY[K]}
}
function petDnaCats(){return [state.pet,state.pet2].filter(function(p){return p&&typeof p==='object'})}
function petDnaFallback(K){const r=petDnaRecipe(K)||{};return (r.base==='red'||r.base==='cream')?'ginger':r.tab?'gray':(r.white>=2||r.base==='white'||r.base==='albino')?'tuxedo':'black'}

/* ---------- cats from before v87: a genome that fits what they already are ---------- */
function petDnaFix(d,id,ok,r){const L=petDnaLocus(id),n=(d.g[id]||[]).length||2;for(let t=0;t<600&&!ok(d.g[id]);t++){const v=[];for(let i=0;i<n;i++)v.push(petDnaDraw(L.al,r));d.g[id]=v}}
function petDnaFixQ(d,ids,ok,r){for(let t=0;t<600&&!ok(petDnaCount(d,ids));t++)ids.forEach(function(id){d.g[id]=[r()<0.5?'+':'-',r()<0.5?'+':'-']})}
function petDnaForLegacy(coat,trait,r,sex){
  r=r||Math.random;const d=petDnaRandom(r,{sex:sex||'XY'}),want=PET_DNA_LEGACY[coat]||PET_DNA_LEGACY.ginger;
  const has=function(a){return function(v){return v.indexOf(a)>=0}},only=function(a){return function(v){return v.every(function(x){return x===a})}},none=function(a){return function(v){return v.indexOf(a)<0}};
  petDnaFix(d,'C',has('C'),r);petDnaFix(d,'I',only('i'),r);petDnaFix(d,'L',has('L'),r);petDnaFix(d,'Ta',only('ta'),r);
  if(want.base==='red'){petDnaFix(d,'O',only('O'),r);petDnaFix(d,'D',has('D'),r)}
  else{petDnaFix(d,'O',only('o'),r);petDnaFix(d,'B',has('B'),r);petDnaFix(d,'D',want.base==='blue'?only('d'):has('D'),r)}
  if(want.tab){petDnaFix(d,'Mc',has('Mc'),r);if(want.base!=='red')petDnaFix(d,'A',has('A'),r)}else petDnaFix(d,'A',only('a'),r);
  const sm=['Sm1','Sm2','Sm3'],ey=['Ey1','Ey2','Ey3','Ey4'];
  if(want.white===0)petDnaFix(d,'W',only('w'),r);
  else{petDnaFix(d,'W',function(v){return none('W')(v)&&v.filter(function(x){return x==='ws'}).length===1},r);
    petDnaFixQ(d,sm,want.white===1?function(c){return c<=2}:want.white===2?function(c){return c>=3&&c<=4}:function(c){return c>=5},r)}
  petDnaFixQ(d,ey,{0:function(c){return c<=1},1:function(c){return c===2||c===3},3:function(c){return c===5},4:function(c){return c===6}}[want.eye],r);
  /* temperament: re-draw the personality loci and the developmental share until the trait matches */
  const pid=[];PET_PERSONA.forEach(function(k){PET_QTL[k].loci.forEach(function(l){pid.push(l[0])})});
  for(let t=0;t<800;t++){const ph=petDnaPheno(d);if(!trait||ph.trait===trait)break;pid.forEach(function(id){d.g[id]=[r()<0.5?'+':'-',r()<0.5?'+':'-']});d.dev=Math.floor(r()*4294967295)>>>0}
  return d;
}
function petDnaOf(p){
  if(!p||typeof p!=='object')return null;
  if(p.dna&&p.dna.g)return p.dna;
  /* deterministic from the birth time, so it is the same until the next save writes it */
  const r=petRng(petHash(p.born+'|dna|'+p.coat));
  const d=petDnaForLegacy(PET_DNA_LEGACY[p.coat]?p.coat:'ginger',p.trait,r,'XY');d.legacy=1;
  p.dna=d;p.sex='m';
  const ph=petDnaPheno(d);if(ph.key!==p.coat)console.warn('[pet dna] migrated '+p.name+' as '+ph.key+' but the coat is '+p.coat);
  if(ph.trait!==p.trait)console.warn('[pet dna] migrated trait mismatch',ph.trait,p.trait);
  return d;
}
function petPheno(p){const d=petDnaOf(p);return d?petDnaPheno(d):null}
function petSexOf(p){const d=petDnaOf(p);return d?petDnaSexOf(d):'m'}
function petDnaScan(){petDnaCats().forEach(function(p){const d=petDnaOf(p);if(d){if(!p.sex)p.sex=petDnaSexOf(d);petDnaRegister(p.coat)}})}

/* ---------- pronouns: the engine's texts say "he"; a female cat gets "she" ---------- */
var PET_PRON={he:'she',He:'She',him:'her',Him:'Her',his:'her',His:'Her',himself:'herself',Himself:'Herself'};
function petDnaHer(s){return s.replace(/\b(he|He|him|Him|his|His|himself|Himself)\b/g,function(w){return PET_PRON[w]})}
function petPron(p,k){const f=petSexOf(p)==='f';return {he:f?'she':'he',He:f?'She':'He',him:f?'her':'him',his:f?'her':'his',His:f?'Her':'His'}[k]}
function petDnaTextCat(t){
  const a=state.pet,b=state.pet2&&state.pet2.alive?state.pet2:null;
  const ia=a&&a.name&&t.indexOf(a.name)>=0,ib=b&&b.name&&t.indexOf(b.name)>=0;
  if(ib&&!ia)return b;if(ia&&!ib)return a;
  return (typeof PET_PAIR!=='undefined'&&PET_PAIR.focus===1&&b)?b:a;
}
function petDnaFixText(root){
  if(!root||!state.pet)return;if(!petDnaCats().some(function(p){return petSexOf(p)==='f'}))return;
  const tw=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,null);let n,ctx=null;const L=[];
  while((n=tw.nextNode()))L.push(n);
  L.forEach(function(t){const s=t.nodeValue;if(!s||!/\b(he|He|him|Him|his|His|himself|Himself)\b/.test(s))return;
    const par=t.parentNode;if(!par||/^(SCRIPT|STYLE|TEXTAREA)$/.test(par.nodeName))return;
    const blk=par.closest&&par.closest('[data-cat]');let p=null;
    if(blk)p=petPairP?petPairP(+blk.dataset.cat):null;
    if(!p){const holder=par.closest&&par.closest('p,div,li,span,small,b');p=petDnaTextCat((holder&&holder.textContent)||s)}
    if(p&&petSexOf(p)==='f'){const v=petDnaHer(s);if(v!==s)t.nodeValue=v}});
}
var PET_DNA_OBS=null;
function petDnaWatch(){
  if(PET_DNA_OBS||typeof MutationObserver==='undefined')return;
  let queued=false;const run=function(){queued=false;['view-pet','petPeek','petRoomFs'].forEach(function(id){const el=document.getElementById(id);if(el)petDnaFixText(el)})};
  PET_DNA_OBS=new MutationObserver(function(){if(!queued){queued=true;Promise.resolve().then(run)}});
  ['view-pet','petPeek'].forEach(function(id){const el=document.getElementById(id);if(el)PET_DNA_OBS.observe(el,{subtree:true,childList:true,characterData:true})});
  run();
}

/* ---------- personality in behaviour ---------- */
function petDnaWeight(p,name){
  const ph=petPheno(p);if(!ph)return 1;const s=ph.ps,cat=PET_OPT_CAT[name];
  let m=1;
  if(cat==='play')m=(0.6+0.8*s.sp)*(0.85+0.3*s.ou);
  else if(cat==='rest')m=0.7+0.6*(1-(s.ou+s.sp)/2);
  else if(cat==='social')m=0.5+1.0*s.fr;
  else if(cat==='solo')m=0.6+0.8*s.sk;
  else if(cat==='expl')m=(0.5+1.0*s.ou)*(1.15-0.3*s.sk);
  if(name==='scratch')m*=0.6+0.8*s.dm;
  if(name==='groom'&&ph.long)m*=1.35;                       /* long fur takes more looking after */
  if(name==='zoomies'||name==='frap')m*=0.7+0.6*s.sp;
  return m;
}

/* ---------- adoption: a litter of strays from two random parents ---------- */
function petDnaNewLitter(n){
  const r=petRng((Math.random()*4294967295)>>>0);
  let mom=petDnaRandom(r,{sex:'XX'}),dad=petDnaRandom(r,{sex:'XY'});
  const kits=petDnaLitter(mom,dad,n||4,r);
  return {mom:{id:mom.id,coat:petDnaPheno(mom).coat},dad:{id:dad.id,coat:petDnaPheno(dad).coat},kits:kits,pick:0,cv:{}};
}
function petDnaLitterHtml(){
  const L=window.__petLitter||(window.__petLitter=petDnaNewLitter(4));
  return '<div class="pet-litter-par muted">Mother: '+esc(L.mom.coat.toLowerCase())+' · Father: '+esc(L.dad.coat.toLowerCase())+'</div>'
   +'<div class="pet-coats pet-litter" id="petCoats">'+L.kits.map(function(d,i){const ph=petDnaPheno(d),f=ph.sex==='f';
     return '<button type="button" class="pet-coat pet-kit'+(i===L.pick&&!window.__petLegacy?' on':'')+'" data-kit="'+i+'" onclick="petPickKit('+i+')"><canvas data-kit="'+i+'" width="144" height="96"></canvas>'
      +'<span class="pet-kit-n"><i class="pet-sex '+(f?'f':'m')+'" aria-label="'+(f?'female':'male')+'">'+(f?'♀':'♂')+'</i>'+esc(ph.coat)+'</span><small>'+esc(PET_TRAITS[ph.trait])+' · '+petDnaTopPersona(ph)+'</small></button>'}).join('')+'</div>'
   +'<button type="button" class="btn secondary small pet-litter-new" onclick="petNewLitter()">Another litter</button>';
}
function petDnaTopPersona(ph){
  const s=ph.ps,o=PET_PERSONA.filter(function(k){return k!=='vo'}).map(function(k){return [k,Math.abs(s[k]-0.5),s[k]]}).sort(function(a,b){return b[1]-a[1]})[0];
  const t=PET_PERSONA_TXT[o[0]];return (o[2]>=0.5?t[0]:t[2]).toLowerCase();
}
function petPickKit(i){
  const L=window.__petLitter;if(!L||!L.kits[i])return;L.pick=i;window.__petLegacy=false;
  document.querySelectorAll('#petCoats .pet-kit').forEach(function(b){b.classList.toggle('on',+b.dataset.kit===i)});
  const ph=petDnaPheno(L.kits[i]);window.__petCoat=ph.key;window.__petTrait=ph.trait;window.__petDnaNext=L.kits[i];
}
function petNewLitter(){window.__petLitter=petDnaNewLitter(4);window.__petLegacy=false;const box=document.getElementById('petCoats');if(!box)return;
  const wrap=box.parentNode,par=wrap.querySelector('.pet-litter-par'),btn=wrap.querySelector('.pet-litter-new');if(par)par.remove();if(btn)btn.remove();
  box.insertAdjacentHTML('afterend',petDnaLitterHtml());box.remove();petPickKit(0);petCoatPreviewDraw()}
async function petDnaPreview(cv,d){
  const ph=petDnaPheno(d),key=ph.key,L=window.__petLitter;
  let sh=L&&L.cv[key];if(!sh){sh=await petDnaSheet(key,'st0');if(L)L.cv[key]=sh}
  if(!sh||!cv.isConnected)return;const rt=PET_RT,m=rt.A.anims.idle_sit,x=cv.getContext('2d');
  x.imageSmoothingEnabled=false;x.clearRect(0,0,cv.width,cv.height);x.drawImage(sh,14,m.row*rt.A.H+18,44,30,6,6,132,90);
}

/* ---------- the genetics panel (Care and settings) ---------- */
function petDnaGt(d,id){const v=d.g[id]||[];return v.join('/')+(v.length===1?' (X·Y)':'')}
function petDnaRow(lab,gt,txt){return '<div class="pet-gene"><b>'+lab+'</b><code>'+esc(gt)+'</code><span>'+txt+'</span></div>'}
function petDnaCarries(d,ph){
  const c=[],h=function(id,a){return petDnaHas(d,id,a)},b2=function(id,a){return (d.g[id]||[]).every(function(x){return x===a})};
  if(h('B','B')&&h('B','b'))c.push('chocolate');if(h('B','bl')&&!b2('B','bl'))c.push('cinnamon');
  if(h('D','d')&&!ph.dilute)c.push('dilute');if(h('A','a')&&ph.agouti)c.push('solid colour');if(h('Mc','mc')&&h('Mc','Mc'))c.push('classic tabby');
  if(h('L','l')&&!ph.long)c.push('long hair');if(h('C','cs')&&h('C','C'))c.push('colourpoint');if(h('C','cb')&&h('C','C'))c.push('Burmese sepia');if(h('C','c')&&!b2('C','c'))c.push('albino');
  return c;
}
function petDnaHidden(ph){
  const o=[];if(ph.white==='W')o.push('Dominant white hides every other colour gene');
  if(ph.red)o.push('Red hides the black/brown gene and whether '+'the cat is agouti: red cats always show tabby markings');
  if(!ph.red&&!ph.agouti&&!ph.tortie&&ph.white!=='W')o.push('Non-agouti hides the tabby pattern genes');
  if(ph.pattern==='ticked'&&ph.rec.tab)o.push('Ticking hides mackerel/classic');
  return o;
}
function petDnaPanelHtml(p){
  const d=petDnaOf(p),ph=petDnaPheno(d);if(!ph)return '';
  const f=ph.sex==='f',He=f?'She':'He',his=f?'her':'his',S=ph.ps,kid=petGrowIdx(p)>=0;
  const xl=(d.g.O||[]);let ot;
  if(ph.tortie)ot='One X carries orange, the other does not. Each patch of fur follows whichever X stayed switched on there, which is why tortoiseshells are '+(f?'female.':'female, or rare XXY males like him.');
  else if(ph.red)ot=(f?'Orange on both X chromosomes.':'Orange on '+his+' only X. Males need just one copy, so most ginger cats are male.');
  else ot=f?'No orange on either X.':'No orange on '+his+' X.';
  const gene=function(id){const L=petDnaLocus(id);return L.gene+(L.chr?' · '+L.chr:'')};
  let h='<div class="pet-likes-h">Genetics</div>';
  h+='<div class="pet-dna-top"><span class="pet-sex-chip '+(f?'f':'m')+'">'+(f?'♀ Female':'♂ Male')+'</span><span class="pet-dna-k">'+esc(ph.karyo)+'</span><span class="pet-dna-coat">'+esc(ph.coat)+'</span></div>';
  if(ph.sterile)h+='<p class="pet-dna-note">'+esc(p.name)+' has an extra X chromosome (XXY). That is why a male can be '+(ph.tortie?'tortoiseshell':'carrying two X genes')+'. XXY males are sterile.</p>';
  h+='<p class="muted pet-dna-sub">'+esc(ph.eyeName)+' eyes'+(ph.deaf?' · deaf (common in white cats)':'')+' · about '+ph.kg+' kg as an adult'+(kid?' (still growing)':'')+'.</p>';
  h+='<div class="pet-genes">'
   +petDnaRow('Sex',d.sex.split('').join(' '),f?'Two X chromosomes: female.':'The Y chromosome (SRY) makes a male.')
   +petDnaRow('Orange · '+gene('O'),petDnaGt(d,'O'),ot)
   +petDnaRow('Colour · '+gene('B'),petDnaGt(d,'B'),ph.red&&!ph.tortie?'Hidden under red.':{black:'Black',blue:'Black',chocolate:'Chocolate',lilac:'Chocolate',cinnamon:'Cinnamon',fawn:'Cinnamon'}[ph.eu]+' pigment.')
   +petDnaRow('Dilute · '+gene('D'),petDnaGt(d,'D'),ph.dilute?'Two dilute copies: '+(ph.red?'red becomes cream.':ph.eu+' instead of '+{blue:'black',lilac:'chocolate',fawn:'cinnamon'}[ph.eu]+'.'):'Dense colour'+(petDnaHas(d,'D','d')?', carries dilute.':'.'))
   +petDnaRow('Agouti · '+gene('A'),petDnaGt(d,'A'),ph.agouti?'Banded hairs: the tabby pattern shows.':'Solid'+(ph.red?' (but red still shows stripes).':'.'))
   +petDnaRow('Tabby · '+gene('Mc'),petDnaGt(d,'Mc')+' · '+petDnaGt(d,'Ta'),ph.rec.tab?{mackerel:'Mackerel (narrow stripes).',classic:'Classic (bold swirls), two recessive copies.',ticked:'Ticked: the dominant DKK4 allele fades the stripes.'}[ph.pattern]:'Pattern genes are present but hidden.')
   +petDnaRow('Colourpoint · '+gene('C'),petDnaGt(d,'C'),{null:'Full colour.',cs:'Siamese points: pigment only where the body is cooler (ears, face, legs, tail). Blue eyes.',mk:'Mink: one Burmese and one Siamese copy, both showing.',sp:'Sepia: Burmese, slightly paler body.',al:'Albino: no pigment at all.'}[ph.point])
   +petDnaRow('White · '+gene('W'),petDnaGt(d,'W')+' · grade '+petDnaCount(d,['Sm1','Sm2','Sm3'])+'/6',ph.white==='W'?'Dominant white: covers everything else.':ph.white?['','A small white locket.','Patches of white.','Bicolor: about half white.','Mostly white (van).'][ph.white]+' The spotting grade comes from several modifier genes.':'No white.')
   +petDnaRow('Hair · '+gene('L'),petDnaGt(d,'L'),ph.long?'Long hair (two recessive copies). Grooms more.':'Short hair'+(petDnaHas(d,'L','l')?', carries long.':'.'))
   +petDnaRow('Silver','I: '+petDnaGt(d,'I'),ph.silver?(ph.rec.tab?'Silver: pale hair bases behind dark stripes.':'Smoke: a pale undercoat.'):'No silver.')
   +'</div>';
  const car=petDnaCarries(d,ph),hid=petDnaHidden(ph);
  if(car.length)h+='<p class="muted pet-dna-sub"><b>Carries</b> (hidden, can be passed on): '+car.join(', ')+'.</p>';
  if(hid.length)h+='<p class="muted pet-dna-sub"><b>Hidden by other genes:</b> '+hid.join('; ')+'.</p>';
  h+='<div class="pet-likes-h">Temperament</div><p class="muted pet-dna-sub">About half of each comes from '+his+' genes, the rest from how '+(f?'she':'he')+' developed before you met. '+He+' reads as <b>'+esc(PET_TRAITS[ph.trait].toLowerCase())+'</b>.</p>';
  h+=PET_PERSONA.map(function(k){const t=PET_PERSONA_TXT[k];return '<div class="pet-thread pet-dna-p"><span>'+t[0]+'</span><i><em style="width:'+Math.round(S[k]*100)+'%"></em></i><small>'+t[1]+'</small></div>'}).join('');
  if(d.mut&&d.mut.length)h+='<p class="muted pet-dna-sub">New mutation'+(d.mut.length>1?'s':'')+' (not from either parent): '+esc(d.mut.join(', '))+'.</p>';
  h+='<details class="pet-genome"><summary>Full genome ('+petDnaAllLoci().length+' loci)</summary><div class="pet-genome-t"><div class="pet-gen-h"><span>Locus</span><span>Chr</span><span>From mother</span><span>From father</span></div>'
   +petDnaAllLoci().slice().sort(function(a,b){return PET_DNA_CHROMS.indexOf(a.chr)-PET_DNA_CHROMS.indexOf(b.chr)||a.pos-b.pos}).map(function(l){const v=d.g[l.id]||[];
     return '<div><span>'+esc(l.id)+(l.q?'':' <small>'+esc(petDnaLocus(l.id).gene)+'</small>')+'</span><span>'+(l.chr||'?')+(l.pos?' <small>~'+l.pos+' Mb</small>':'')+'</span><span>'+esc(v[0]||'')+'</span><span>'+esc(v.length>1?v[1]:(l.chr==='X'?'Y':''))+'</span></div>'}).join('')
   +'</div><p class="muted" style="font-size:11px">Pigment genes are real feline genes with their known effects; positions are approximate. Loci with + / − alleles are modelled quantitative loci for traits that real cats inherit through many small genes.</p></details>';
  return h;
}
function petDnaRender(force){
  const p=petData();if(!p||!p.alive)return;const el=document.getElementById('petDna');if(!el)return;
  const key=p.name+'|'+(p.dna&&p.dna.id)+'|'+petGrowIdx(p);if(!force&&el.dataset.k===key)return;el.dataset.k=key;el.innerHTML=petDnaPanelHtml(p);
}

/* ---------- developer test buttons ---------- */
function petDevDnaReroll(){
  const p=petData();if(!p)return;p.dnaPrev=p.dnaPrev||{dna:p.dna,coat:p.coat,trait:p.trait,sex:p.sex};
  const d=petDnaRandom(null,{});const ph=petDnaPheno(d);p.dna=d;p.sex=ph.sex;p.trait=ph.trait;petDnaRegister(ph.key);p.coat=ph.key;
  if(PET_RT){PET_RT.gsLast=null;PET_RT.sched={};petDnaEnsureSheets(PET_RT,ph.key)}
  save();renderPetView(true);toast(p.name+' is now: '+(ph.sex==='f'?'♀ ':'♂ ')+ph.coat+', '+PET_TRAITS[ph.trait].toLowerCase()+'.');
}
function petDevDnaUndo(){const p=petData();if(!p||!p.dnaPrev){toast('Nothing to undo.');return}const o=p.dnaPrev;p.dna=o.dna;p.coat=o.coat;p.trait=o.trait;p.sex=o.sex;delete p.dnaPrev;
  if(!p.dna)petDnaOf(p);petDnaRegister(p.coat);if(PET_RT){PET_RT.gsLast=null;PET_RT.sched={}}save();renderPetView(true);toast('Restored '+p.name+'.')}
function petDevDnaStats(){
  /* 2,000 crosses: checks Mendel and X-linkage come out right */
  const r=petRng(12345),n=2000;let tm=0,tf=0,m=0,f=0,xxy=0,redM=0,redF=0,lh=0,pts=0,mut=0;
  for(let i=0;i<n;i++){const mo=petDnaRandom(r,{sex:'XX'}),da=petDnaRandom(r,{sex:'XY'}),k=petDnaConceive(mo,da,r),ph=petDnaPheno(k);
    if(ph.sex==='f'){f++;if(ph.tortie)tf++;if(ph.red)redF++}else{m++;if(ph.tortie)tm++;if(ph.red)redM++}if(k.sex==='XXY')xxy++;if(ph.long)lh++;if(ph.point)pts++;mut+=k.mut.length}
  const t='Of '+n+' kittens: '+f+' ♀, '+m+' ♂ ('+xxy+' XXY). Tortie: '+tf+' ♀, '+tm+' ♂. Ginger: '+redF+' ♀, '+redM+' ♂. Long hair '+lh+', points '+pts+', new mutations '+mut+'.';
  console.log('[pet dna]',t);const el=document.getElementById('petDnaDev');if(el)el.textContent=t;toast('DNA check done: see developer panel.');
}
async function petDevDnaGallery(){
  const el=document.getElementById('petDnaDev');if(!el||!PET_RT)return;el.innerHTML='Drawing 12 random cats…';
  const r=petRng((Math.random()*4294967295)>>>0),wrap=document.createElement('div');wrap.className='pet-dna-gal';
  for(let i=0;i<12;i++){const d=petDnaRandom(r,{}),ph=petDnaPheno(d),sh=await petDnaSheet(ph.key,'adult');const cv=document.createElement('canvas');cv.width=144;cv.height=96;
    const x=cv.getContext('2d'),m=PET_RT.A.anims.idle_sit;x.imageSmoothingEnabled=false;if(sh)x.drawImage(sh,0,m.row*PET_RT.A.H,72,48,0,0,144,96);
    const fig=document.createElement('figure');fig.appendChild(cv);const cap=document.createElement('figcaption');cap.textContent=(ph.sex==='f'?'♀ ':'♂ ')+ph.coat;fig.appendChild(cap);wrap.appendChild(fig)}
  el.innerHTML='';el.appendChild(wrap);
}
function petDevDnaForce(rec){const p=petData();if(!p)return;
  for(let t=0;t<20000;t++){const d=petDnaRandom(null,{sex:rec.sex}),ph=petDnaPheno(d);if(rec.test(ph)){p.dnaPrev=p.dnaPrev||{dna:p.dna,coat:p.coat,trait:p.trait,sex:p.sex};p.dna=d;p.sex=ph.sex;p.trait=ph.trait;petDnaRegister(ph.key);p.coat=ph.key;
    if(PET_RT){PET_RT.gsLast=null;PET_RT.sched={};petDnaEnsureSheets(PET_RT,ph.key)}save();renderPetView(true);toast(p.name+' is now: '+ph.coat+'.');return}}
  toast('No match found.')}
petDevTest('DNA: random genome',petDevDnaReroll,'v87');
petDevTest('DNA: calico ♀',function(){petDevDnaForce({sex:'XX',test:function(ph){return ph.tortie&&ph.white>=2&&!ph.point}})},'v87');
petDevTest('DNA: Siamese point',function(){petDevDnaForce({test:function(ph){return ph.point==='cs'}})},'v87');
petDevTest('DNA: longhair classic tabby',function(){petDevDnaForce({test:function(ph){return ph.long&&ph.rec.tab==='classic'&&!ph.white}})},'v87');
petDevTest('DNA: undo',petDevDnaUndo,'v87');
petDevTest('DNA: 2,000-kitten check',petDevDnaStats,'v87');
petDevTest('DNA: draw 12 random cats',petDevDnaGallery,'v87');

/* ---------- wiring ---------- */
var petAssetsJson_dna0=petAssetsJson;
petAssetsJson=function(){const A=petAssetsJson_dna0();try{petDnaScan();for(const K in PET_DNA_KEYS)petDnaRegisterInto(A,K)}catch(e){console.warn('[pet dna]',e)}return A};
var petLoadImg_dna0=petLoadImg;
petLoadImg=function(src){if(typeof src==='string'&&src.indexOf('dna:')===0)return petDnaPseudoImg(src);return petLoadImg_dna0(src)};
(function(){
  const draw=petDrawCat;
  petDrawCat=function(rt,o){const p=petData();
    if(p&&rt&&rt.sheets&&!rt.sheets[p.coat]&&!PET_DNA_LEGACY[p.coat]){petDnaEnsureSheets(rt,p.coat);const k=p.coat;p.coat=petDnaFallback(k);try{return draw.apply(this,arguments)}finally{p.coat=k}}
    return draw.apply(this,arguments)};
  const adopt=petAdopt;
  petAdopt=function(coat,name,trait){
    let d=window.__petDnaNext;window.__petDnaNext=null;
    if(d&&petDnaPheno(d).key!==coat)d=null;
    if(!d)d=petDnaForLegacy(PET_DNA_LEGACY[coat]?coat:'ginger',trait,null,Math.random()<0.5?'XX':'XY');
    const ph=petDnaPheno(d);petDnaRegister(ph.key);
    /* the original saves (and re-renders) straight away: hold that until the genome is attached */
    const sv=save;let p;save=function(){};try{p=adopt.call(this,ph.key,name,ph.trait)}finally{save=sv}
    p.dna=d;p.sex=ph.sex;delete p.brain;
    if(PET_RT&&PET_RT.ready)petDnaEnsureSheets(PET_RT,ph.key);
    save();return p};
  if(typeof petPairAdopt==='function'){const pa=petPairAdopt;
    petPairAdopt=function(coat,name,trait){
      let d=window.__petDnaNext;window.__petDnaNext=null;if(d&&petDnaPheno(d).key!==coat)d=null;
      if(!d)d=petDnaForLegacy(PET_DNA_LEGACY[coat]?coat:'gray',trait,null,Math.random()<0.5?'XX':'XY');
      const ph=petDnaPheno(d);petDnaRegister(ph.key);
      const sv=save;let p;save=function(){};try{p=pa.call(this,ph.key,name,ph.trait)}finally{save=sv}
      p.dna=d;p.sex=ph.sex;delete p.brain;
      if(PET_RT&&PET_RT.ready)petDnaEnsureSheets(PET_RT,ph.key);return p}}
  const pick=petPickCoat;
  petPickCoat=function(c){window.__petLegacy=true;window.__petDnaNext=null;document.querySelectorAll('#petCoats .pet-kit').forEach(function(b){b.classList.remove('on')});return pick.apply(this,arguments)};
  /* the intro: a litter instead of four coat buttons and a personality picker */
  petIntroHtml=function(){
    const mem=(state.petMemorial||[]).slice(-1)[0];window.__petLegacy=false;window.__petLitter=window.__petLitter||petDnaNewLitter(4);petPickKit(window.__petLitter.pick||0);
    return '<div class="card pet-intro"><div class="eyebrow">A VISITOR</div><h2>A litter of strays keeps stopping by</h2>'
     +'<p class="muted">They are tiny, and they sit outside the window most evenings. If you leave the door open, one of them might move in. Each kitten has its own genes from the same two parents: its coat, its eyes, how big it will grow and a good part of its temperament. A kitten needs about two meals a day, a little attention, and someone to come back to.</p>'
     +petDnaLitterHtml()
     +'<label class="pet-lab" for="petNameIn">Name</label><input id="petNameIn" class="pet-in" maxlength="14" placeholder="'+PET_NAMES[Math.floor(Math.random()*PET_NAMES.length)]+'" autocomplete="off">'
     +'<button class="btn pet-go" onclick="petAdoptClick()">Welcome home</button>'
     +'<p class="muted" style="font-size:12px;margin:12px 0 0">No notifications, ever. A calm companion: feed it about twice a day. If you will be away, Vacation mode pauses its clock.'+(mem?' In memory: '+esc(mem.name)+'.':'')+'</p></div>'};
  if(typeof petPairAdoptHtml==='function'){
    const pao=petPairAdoptOpen;petPairAdoptOpen=function(){window.__petLitter=petDnaNewLitter(4);window.__petLegacy=false;const r=pao.apply(this,arguments);return r};
    petPairAdoptHtml=function(){
      const p=state.pet;window.__petLitter=window.__petLitter||petDnaNewLitter(4);if(!window.__petLegacy)petPickKit(window.__petLitter.pick||0);
      return '<div class="eyebrow">ANOTHER VISITOR</div><h2>More strays by the kitchen window</h2>'
       +'<p class="muted">Another litter has been turning up. Two cats keep each other company: they work each other out over a few days, then play chase, groom each other and nap together. '+esc(p.name)+' may hiss at first.</p>'
       +petDnaLitterHtml()
       +'<label class="pet-lab" for="petNameIn">Name</label><input id="petNameIn" class="pet-in" maxlength="14" placeholder="'+PET_NAMES.filter(function(n){return n!==p.name})[Math.floor(Math.random()*(PET_NAMES.length-1))]+'" autocomplete="off">'
       +'<div class="pet-pair-go"><button class="btn secondary" type="button" onclick="petPairAdoptClose()">Not now</button><button class="btn" type="button" onclick="petPairAdoptClick()">Let them in</button></div>'}}
  const prev=petCoatPreviewDraw;
  petCoatPreviewDraw=function(){prev.apply(this,arguments);const L=window.__petLitter;if(!L||!PET_RT||!PET_RT.ready)return;
    document.querySelectorAll('#petCoats canvas[data-kit]').forEach(function(cv){const d=L.kits[+cv.dataset.kit];if(d)petDnaPreview(cv,d)})};
  /* the room page: sex next to the name, and a genetics panel under Care and settings */
  const room=petRoomHtml;
  petRoomHtml=function(p){let h=room.apply(this,arguments);const f=petSexOf(p)==='f';
    h=h.replace('<h2 id="petTitle">'+esc(p.name)+'</h2>','<h2 id="petTitle">'+esc(p.name)+'</h2><span class="pet-sex '+(f?'f':'m')+'" title="'+(f?'Female':'Male')+'" aria-label="'+(f?'female':'male')+'">'+(f?'♀':'♂')+'</span>');
    return h.replace('<div id="petDev"></div>','<div class="pet-care pet-dna" id="petDna"></div><div id="petDev"></div>')};
  const br=petBrainRender;petBrainRender=function(force){const r=br.apply(this,arguments);try{petDnaRender(force)}catch(e){console.warn('[pet dna] panel',e)}return r};
  const arb=petBrainArbiter;
  petBrainArbiter=function(rt,O,add,o){const r=arb.apply(this,arguments);
    try{const p=petData();if(p&&p.dna&&!(o&&o.stage>=3))for(const it of O)it[1]*=petDnaWeight(p,it[0])}catch(e){console.warn('[pet dna] weights',e)}return r};
  /* a new cat's voice: bigger cats are a little lower, vocal ones talk more */
  const mig=migratePetBrainState;
  migratePetBrainState=function(p){const fresh=p&&!(p.brain&&p.brain.vo&&p.brain.vo.p);const b=mig.apply(this,arguments);
    try{if(fresh&&b&&p.dna&&!p.dna.legacy){const ph=petDnaPheno(p.dna),r=petRng(petHash(p.born+'|voice'));
      b.vo={p:petR3(petBC((1.08-0.14*ph.sizeN+(ph.sex==='f'?0.03:0))*(0.96+r()*0.08),0.88,1.14)),t:petR3(petBC(0.15+0.75*ph.ps.vo,0.15,0.9))}}}catch(e){}
    return b};
  const rb=petRebuildSprites;petRebuildSprites=async function(rt){petDnaScan();if(rt&&rt.A)for(const K in PET_DNA_KEYS)petDnaRegisterInto(rt.A,K);return rb.apply(this,arguments)};
  const dv=petDevHtml;petDevHtml=function(){const h=dv.apply(this,arguments);return h?h+'<div class="muted" id="petDnaDev" style="font-size:12px;margin-top:6px"></div>':h};
  const ts=toast;toast=function(msg){try{if(typeof msg==='string'&&state&&(state.pet||state.pet2)){const p=petDnaTextCat(msg);if(p&&petSexOf(p)==='f')msg=petDnaHer(msg)}}catch(e){}return ts.call(this,msg)};
})();
/* start: cats already in the save get their genome; sheets for custom coats are built as soon as the room is */
try{petDnaScan();
  if(PET_RT){const go=function(){const rt=PET_RT;if(!rt||!rt.A)return;for(const K in PET_DNA_KEYS)petDnaRegisterInto(rt.A,K);petDnaCats().forEach(function(p){petDnaEnsureSheets(rt,p.coat)})};
    if(PET_RT.ready)go();else if(PET_RT.loading)PET_RT.loading.then(go)}
  petDnaWatch();
}catch(e){console.warn('[pet dna] start',e)}
