
/* ===== Pet likes (v77): favourite places, favourite toys, exploring the house =====
   Every cat starts with its own taste (from its birth time and trait). Time spent in a place
   (credited once a day from the routine he actually lived) and good moments there make him like
   it more, and what he likes shapes tomorrow's routine, so a favourite slowly emerges. Toys work
   the same way: each play session counts, and toys you pick for him count a little more.
   Saved in state.pet.likes. */
var PET_SPOTS={
  bed:    {name:'the cat bed',room:'bedroom',sleep:1,rest:1,soft:1,hidden:0.3},
  sill:   {name:'the bedroom window sill',room:'bedroom',sleep:0.5,rest:1,sunny:1,high:1},
  tree:   {name:'the cat tree',room:'bedroom',sleep:1,rest:1,high:1,hidden:0.4},
  rug:    {name:'the bedroom rug',room:'bedroom',sleep:0.6,rest:1,floor:1},
  couch:  {name:'the sofa',room:'living room',sleep:1,rest:1,soft:1},
  lsill:  {name:'the living room window sill',room:'living room',sleep:0.4,rest:1,sunny:1,high:1},
  lrug:   {name:'the living room rug',room:'living room',sleep:0.6,rest:1,floor:1},
  pouf:   {name:'the pouf',room:'living room',sleep:0.7,rest:1,soft:1},
  cushion:{name:'the big floor cushion',room:'living room',sleep:1,rest:1,soft:1,near:1},
  counter:{name:'the kitchen counter',room:'kitchen',sleep:0.3,rest:1,high:1},
  ksill:  {name:'the kitchen window sill',room:'kitchen',sleep:0.4,rest:1,sunny:1,high:1},
  box:    {name:'the cardboard box',room:'kitchen',sleep:1,rest:1,hidden:1},
  kitchen:{name:'the cool kitchen tiles',room:'kitchen',sleep:0.3,rest:0.8,floor:1}
};
/* toys: chase toys are knocked about the floor, the wand dangles */
var PET_TOYS={
  ball_a: {name:'Jingle ball',mode:'chase',unlock:{}},
  mouse:  {name:'Toy mouse',mode:'chase',unlock:{}},
  feather:{name:'Feather',mode:'chase',unlock:{L:1}},
  ball_b: {name:'Crinkle ball',mode:'chase',unlock:{days:2}},
  yarn:   {name:'Yarn ball',mode:'chase',unlock:{days:3}},
  spring: {name:'Spring',mode:'chase',unlock:{L:2}},
  wand:   {name:'Feather wand',mode:'wand',icon:'feather',unlock:{L:3}}
};
PET_SURF.lsill={y:73,x0:-124,x1:-72};PET_SURF.ksill={y:74,x0:304,x1:338};     /* the other two window sills */
var PET_LIKE_FAV_MIN=8,PET_LIKE_FAV_LEAD=1.15,PET_TOY_FAV_MIN=4;
function petLikes(p){p=p||petData();if(!p)return null;if(!p.likes||p.likes.v!==1)p.likes={v:1,spots:{},toys:{},plays:{},seen:{},dayT:0,favSpot:null,favToy:null,news:[]};return p.likes}
function petNorm(s){return (s||0)/((s||0)+10)}
/* ---------- taste: who this particular cat is ---------- */
function petTaste(p,kind,id){
  const r=petRng(petHash(p.born+'|'+kind+'|'+id+'|taste'));let t=0.6+r()*0.9;const tr=p.trait;
  if(kind==='spot'){const s=PET_SPOTS[id]||{};
    if(tr==='shy')t*=1+0.6*(s.hidden||0)+0.3*(s.high||0);
    if(tr==='lazy')t*=1+0.6*(s.soft||0);
    if(tr==='curious')t*=1+0.4*(s.sunny||0)+0.2*(s.high||0);
    if(tr==='playful')t*=1+0.3*(s.floor||0)+0.2*(s.high||0)}
  else{
    if(tr==='playful'&&/ball|spring/.test(id))t*=1.4;
    if(tr==='curious'&&/feather|wand/.test(id))t*=1.4;
    if(tr==='shy'&&/mouse|yarn/.test(id))t*=1.35;
    if(tr==='lazy'&&/wand|yarn/.test(id))t*=1.3}
  return t;
}
/* ---------- choosing a place for a block of the day (called while building the routine) ---------- */
function petSpotOK(p,id){if(id==='cushion')return petDepthMaxZ(p)>=PET_SURF.cushion.z;return true}
function petLikesPickSpot(p,r,kind,hour){
  const lk=petLikes(p),L=petLevel(p),O=[];let tot=0;
  for(const id of Object.keys(PET_SPOTS)){const s=PET_SPOTS[id];let w=s[kind]||0;if(!w||!petSpotOK(p,id))continue;
    w*=petTaste(p,'spot',id)*Math.pow(1+(lk.spots[id]||0)/4,1.6);          /* liking feeds on itself, so one place wins out over the days */
    if(s.sunny)w*=(hour>=10&&hour<16)?1.8:(hour>=20||hour<6)?0.5:1;
    if(L===0&&s.hidden)w*=1.8;                                   /* a stray wants somewhere to hide */
    if(lk.favSpot===id)w*=1.4;
    O.push([id,w]);tot+=w}
  let x=r()*tot;for(const o of O){if((x-=o[1])<=0)return o[0]}return O.length?O[O.length-1][0]:'bed';
}
/* ---------- new places: how to get there, how to be placed there ---------- */
function petSpotRestAct(id){return /sill$/.test(id)?'lookup':'loaf'}
var PET_SPOT_X={
  lsill:{at:function(c){return c.surf==='lsill'},plan:function(rt,r,c){return petRoute(c,'lsill',-122+r()*48)},place:function(rt,c,r,snap){snap('lsill',-122+Math.round(r()*48))}},
  ksill:{at:function(c){return c.surf==='ksill'},plan:function(rt,r,c){return petRoute(c,'ksill',306+r()*30)},place:function(rt,c,r,snap){snap('ksill',306+Math.round(r()*30))}},
  box:{at:function(c){return c.surf==='box'},plan:function(rt,r,c){const s=PET_SURF.box;return petBoxSettle(rt,petRoute(c,'box',(s.x0+s.x1)/2))},place:function(rt,c,r,snap){const s=PET_SURF.box;snap('box',Math.round((s.x0+s.x1)/2))}},
  pouf:{at:function(c){return c.surf==='pouf'},plan:function(rt,r,c){const s=PET_SURF.pouf;return petRoute(c,'pouf',s.x0+r()*(s.x1-s.x0))},place:function(rt,c,r,snap){const s=PET_SURF.pouf;snap('pouf',Math.round((s.x0+s.x1)/2))}},
  cushion:{at:function(c){return c.surf==='cushion'},   /* if trust has dropped since the routine was made, the pouf instead */
    plan:function(rt,r,c){if(!petSpotOK(petData(),'cushion'))return PET_SPOT_X.pouf.plan(rt,r,c);const s=PET_SURF.cushion;return petRoute(c,'cushion',s.x0+r()*(s.x1-s.x0))},
    place:function(rt,c,r,snap){if(!petSpotOK(petData(),'cushion'))return PET_SPOT_X.pouf.place(rt,c,r,snap);const s=PET_SURF.cushion;snap('cushion',Math.round((s.x0+s.x1)/2))}},
  lrug:{at:function(c){return c.surf==='floor'&&c.x>=-142&&c.x<=-54&&Math.abs(c.z-42)<7},
    plan:function(rt,r,c){const x=-136+r()*76;return petDepthSetLastWalkZ(petRoute(c,'floor',x),petDepthAvoidProps(x,39+r()*6))},
    place:function(rt,c,r,snap){snap('floor',-136+Math.round(r()*76));c.z=petDepthAvoidProps(c.x,39+r()*6);c.y=petFloorY(c.z)}}
};
function petSpotAt(c){
  if(!c)return null;for(const id of Object.keys(PET_SPOT_X))if(PET_SPOT_X[id].at(c))return id;
  if(c.surf==='bed')return 'bed';if(c.surf==='sill')return 'sill';if(c.surf==='mid'||c.surf==='top')return 'tree';if(c.surf==='couch')return 'couch';if(c.surf==='counter')return 'counter';
  if(c.surf==='floor'){if(c.x>=86&&c.x<=128)return 'rug';if(petRoomOf(c.x)==='kitchen')return 'kitchen'}return null;
}
/* ---------- daily credit: the hours he spent in each place yesterday (and any missed days) ---------- */
function petMidnight(t){const d=new Date(t);d.setHours(0,0,0,0);return d.getTime()}
function petKeyOf(d){return d.getFullYear()+'-'+(d.getMonth()+1)+'-'+d.getDate()}
function petLikesDaily(p){
  const lk=petLikes(p);if(!lk)return;const today=petMidnight(petNow());
  if(!lk.dayT){lk.dayT=today;return}
  if(lk.dayT>=today)return;
  const d=new Date(lk.dayT);let n=0;
  while(d.getTime()<today&&n<10){
    const key=petKeyOf(d),S=petBuildDay(p,key);n++;
    for(const id of Object.keys(lk.spots))lk.spots[id]*=0.95;           /* old habits fade slowly */
    for(const b of S.blocks){if(!PET_SPOTS[b.spot])continue;const w=b.k==='nap'?1:b.k==='rest'?0.8:0.5;lk.spots[b.spot]=(lk.spots[b.spot]||0)+(b.b-b.a)*w}
    d.setDate(d.getDate()+1);
  }
  lk.dayT=today;petLikesEvaluate(p);
}
function petLikesBump(p,kind,id,amt){const lk=petLikes(p);if(!lk||!id)return;const m=kind==='spot'?lk.spots:lk.toys;m[id]=(m[id]||0)+amt;petLikesEvaluate(p)}
function petTop(m){const e=Object.keys(m).map(function(k){return [k,m[k]]}).sort(function(a,b){return b[1]-a[1]});return e}
function petLikesEvaluate(p){
  const lk=petLikes(p);if(!lk)return;
  const s=petTop(lk.spots);let fav=lk.favSpot;
  if(s.length&&s[0][1]>=PET_LIKE_FAV_MIN&&(!s[1]||s[0][1]>=s[1][1]*PET_LIKE_FAV_LEAD))fav=s[0][0];
  else if(fav&&(lk.spots[fav]||0)<PET_LIKE_FAV_MIN*0.6)fav=null;
  if(fav&&fav!==lk.favSpot)lk.news.push({k:'spot',id:fav,prev:lk.favSpot});lk.favSpot=fav;
  const t=petTop(lk.toys);let ft=lk.favToy;
  if(t.length&&t[0][1]>=PET_TOY_FAV_MIN&&(!t[1]||t[0][1]>=t[1][1]*PET_LIKE_FAV_LEAD))ft=t[0][0];
  if(ft&&ft!==lk.favToy)lk.news.push({k:'toy',id:ft,prev:lk.favToy});lk.favToy=ft;
}
/* a good petting session where he is lying makes that place a little nicer */
function petLikesPetted(rt){const p=petData(),id=petSpotAt(rt.c);if(id&&PET_SPOTS[id]){petLikesBump(p,'spot',id,0.6)}}
/* ---------- toys ---------- */
function petToyUnlocked(p,id){const u=PET_TOYS[id].unlock;if(u.L!=null&&petLevel(p)<u.L)return false;if(u.days!=null&&petAgeDaysTogether(p)<u.days)return false;return true}
function petAgeDaysTogether(p){return Math.max(0,(Date.now()-p.born)/864e5)}
function petToyLock(p,id){const u=PET_TOYS[id].unlock;if(u.L!=null&&petLevel(p)<u.L)return 'at '+PET_LEVELS[u.L];if(u.days!=null&&petAgeDaysTogether(p)<u.days)return 'day '+(u.days+1);return ''}
function petToyEnthusiasm(p,id){const lk=petLikes(p);let e=petTaste(p,'toy',id)*(1+1.8*petNorm((lk.toys[id]||0)*2.5));if((lk.plays[id]||0)<2)e+=0.5;if(lk.favToy===id)e+=0.4;return e}
function petLikesPickToy(rt,mode,only){
  const p=petData(),O=[];let tot=0;
  for(const id of Object.keys(PET_TOYS)){if(PET_TOYS[id].mode!==mode||!petToyUnlocked(p,id))continue;if(only&&only.indexOf(id)<0)continue;const w=Math.pow(petToyEnthusiasm(p,id),2);O.push([id,w]);tot+=w}
  if(!O.length)return mode==='chase'?'ball_a':'wand';let x=Math.random()*tot;for(const o of O){if((x-=o[1])<=0)return o[0]}return O[O.length-1][0];
}
function petLikesPlayed(id,fromYou,amt){const p=petData(),lk=petLikes(p);if(!lk||!PET_TOYS[id])return;petBrainEvent(fromYou?(amt!=null&&amt<0.3?'toy_meh':'toy'):'play');lk.plays[id]=(lk.plays[id]||0)+1;petLikesBump(p,'toy',id,(amt!=null?amt:1)*(fromYou?1.5:1))}
function petToyWord(p,id){
  const lk=petLikes(p);if(lk.favToy===id)return 'Favorite';if((lk.plays[id]||0)<2)return 'New';
  const e=petToyEnthusiasm(p,id);return e>=1.7?'Loves it':e>=1.05?'Likes it':'Not keen';
}
/* you picked a toy for him: how he takes it depends on how much he likes it */
function petOfferToy(id){
  const rt=PET_RT,p=petData();if(!p||!rt||!rt.ready||!p.alive)return;petToyTray(false);
  if(id==null){id=petLikesPickToy(rt,Math.random()<0.18&&petToyUnlocked(p,'wand')?'wand':'chase')}
  if(!PET_TOYS[id]||!petToyUnlocked(p,id))return;
  if(petStage(p)>=3){toast(p.name+' is too tired to play. A meal first.');return}
  const e=petToyEnthusiasm(p,id),c=rt.c;rt.dangle=null;rt.ball.on=false;rt.lastTapAt=Date.now();
  if(e<0.9&&(petLikes(p).plays[id]||0)>=3&&Math.random()<0.6){         /* not this one, thanks */
    const b=rt.ball;b.on=true;b.col=id==='wand'?'feather':id;b.x=petClampX(c.x+(c.dir>0?40:-40));b.vx=0;b.vy=0;b.h=0;b.t=0;
    const q=petRoute(c,'floor',petClampX(b.x-(b.x>c.x?20:-20)));q.push({k:'face',dir:b.x>=c.x?1:-1});q.push(petAct('idle_sit',2.2));q.push(petAct('groom',petRand(4,7),{endBall:1}));
    q.push({k:'fn',f:function(){petLikesPlayed(id,true,0.25)}});petInterrupt(rt,q);toast(p.name+' sniffs the '+PET_TOYS[id].name.toLowerCase()+' and loses interest.');return}
  const love=e>=1.7;
  if(love){petPlay(rt,'purreow',0.9);for(let i=0;i<3;i++)petSpawn(rt,'heart',c.x+petRand(-12,12),c.y-40*petK(c),petRand(-6,6),-14,1.4)}else petPlay(rt,'idle',0.7);
  let q;
  if(PET_TOYS[id].mode==='wand'){rt.wandFromYou=true;q=petPlayPlan(rt,'bat');const a=q.find(function(s){return s.k==='act'&&s.a==='bat'});if(a)a.d*=love?1.6:1}
  else q=petToyGame(rt,id,love?3:e>=1.05?2:1,love);   /* v79: pounce, floor swats, swat-and-chase or belly-up */
  q.push({k:'fn',f:function(){petLikesPlayed(id,true)}});petInterrupt(rt,q);
}
function petToyH(col){return col==='mouse'||col==='feather'?6:col==='yarn'?9:col==='spring'?10:8}
/* the extra item art (yarn, spring, box) lives beside the props */
function petXItem(rt,o,k,x,y,flip){
  const im=petPropImg(rt,k);if(!im||!im.img.complete)return;x=Math.round(x);y=Math.round(y);
  if(flip){o.save();o.translate(x+im.w,0);o.scale(-1,1);o.drawImage(im.img,0,y);o.restore()}else o.drawImage(im.img,x,y);
}
function petPropImg(rt,k){
  rt.xImg=rt.xImg||{};if(rt.xImg[k])return rt.xImg[k];const D=rt.A.depth||{},src=(D.props&&D.props[k])||(D.toys&&D.toys[k]);if(!src)return null;
  const i=new Image();i.src=src.url;rt.xImg[k]={img:i,w:src.w,h:src.h};return rt.xImg[k];
}
function petLikesGiftItem(r){const p=petData(),lk=petLikes(p);if(lk.favToy&&PET_TOYS[lk.favToy].mode==='chase')return lk.favToy;return r()<0.5?'mouse':'feather'}
/* ---------- exploring: things around the house worth a look ---------- */
var PET_POI=[
  {id:'lamp',name:'the floor lamp',x:-150,z:36,face:-1,act:'lookup'},
  {id:'lwindow',name:'the living room window',x:-96,z:34,face:1,act:'lookup'},
  {id:'plant',name:'the plant',x:-6,z:35,face:-1,act:'lookup'},
  {id:'shelf',name:'the bookshelf',x:84,z:16,face:1,act:'lookup'},
  {id:'bwindow',name:'the bedroom window',x:52,z:36,face:1,act:'lookup'},
  {id:'tree',name:'the cat tree',x:128,z:36,face:1,act:'idle_sit'},
  {id:'fridge',name:'the fridge',x:226,z:30,face:1,act:'lookup'},
  {id:'kettle',name:'the kettle',x:266,z:34,face:1,act:'lookup'},
  {id:'box',name:'the box',x:252,z:44,face:1,act:'idle_sit'},
  {id:'corner',name:'the kitchen corner',x:340,z:22,face:1,act:'groom'},
  {id:'cushion',name:'the floor cushion',x:-90,z:60,face:-1,act:'idle_sit'},
  {id:'doorway',name:'the doorway',x:192,z:40,face:1,act:'idle_sit'}
];
function petPoiPick(r,exclude){
  const p=petData(),lk=petLikes(p),now=Date.now(),O=[];let tot=0;
  for(const q of PET_POI){if(exclude&&exclude.indexOf(q.id)>=0)continue;const seen=lk.seen[q.id];const hrs=seen?(now-seen)/36e5:99;let w=1+Math.min(3,hrs/4);if(q.z>petDepthMaxZ(p))w*=0.2;O.push([q,w]);tot+=w}
  let x=r()*tot;for(const o of O){if((x-=o[1])<=0)return o[0]}return O[0][0];
}
function petPoiVisit(rt,q,r){
  const c=rt.c,p=petData(),z=Math.min(petDepthMaxZ(p),Math.max(petZMinAt(q.x),q.z)),s=petRoute(c,'floor',q.x);
  petDepthSetLastWalkZ(s,petDepthAvoidProps(q.x,z));
  s.push({k:'face',dir:q.face||1});s.push(petAct(q.act,3+r()*5));
  s.push({k:'fn',f:function(){const lk=petLikes(petData());if(lk){lk.seen[q.id]=Date.now()}}});
  return s;
}
function petLikesOptions(rt,add,R,r,pause,o){
  if(o.stage>=3)return;const p=petData(),lk=petLikes(p);
  /* look at one thing that has not been checked for a while */
  add('investigate',2.6*o.cur*(1+0.5*o.kit),function(){const q=petPoiVisit(rt,petPoiPick(r),r);q.push(pause());return q});
  /* a little tour: two or three things, often in different rooms */
  add('patrol',1.0*o.cur*(o.L>=1?1:0.4),function(){const a=petPoiPick(r),b=petPoiPick(r,[a.id]),q=petPoiVisit(rt,a,r).concat(petPoiVisit(rt,b,r));if(r()<0.5)q.push.apply(q,petPoiVisit(rt,petPoiPick(r,[a.id,b.id]),r));q.push(pause());return q});
  /* go and sit in the favourite place for a bit, even when it is not rest time */
  if(lk.favSpot&&PET_SPOTS[lk.favSpot]&&petSpotOK(p,lk.favSpot))add('favspot',1.2,function(){const q=petSpotPlan(rt,lk.favSpot,r);q.push(petAct(petSpotRestAct(lk.favSpot),R(30,80)));q.push(pause());return q});
  /* play alone with the favourite toy */
  petPlayOptions(rt,add,R,r,pause,o);   /* v79 */
  petBoxOptions(rt,add,R,r,pause,o);    /* v79.2: peek-a-boo in the box */
  if(lk.favToy&&PET_TOYS[lk.favToy].mode==='chase')add('favtoy',0.9*o.play*(1+o.kit),function(){rt.lastPlayAt=petNow();return petPounceChain(rt,false,{col:lk.favToy,solo:1}).concat([pause()])});
}
/* ---------- the toy tray under Play ---------- */
function petToyTray(open){
  const el=document.getElementById('petToys');if(!el)return;if(open==null)open=el.hidden;el.hidden=!open;
  if(open){petToyTrayRender();el.scrollIntoView({block:'nearest',behavior:'smooth'})}
}
function petToyIcon(rt,k){
  rt.icon=rt.icon||{};if(rt.icon[k])return rt.icon[k];
  const m=rt.A.room.meta[k],cv=document.createElement('canvas'),S=4;cv.width=16*S;cv.height=16*S;const x=cv.getContext('2d');x.imageSmoothingEnabled=false;
  if(m){x.drawImage(rt.items,m[0],m[1],m[2],m[3],Math.round((16-m[2])/2)*S,Math.round((16-m[3])/2)*S,m[2]*S,m[3]*S)}
  else{const im=petPropImg(rt,k);if(!im||!im.img.complete)return '';x.drawImage(im.img,Math.round((16-im.w)/2)*S,Math.round((16-im.h)/2)*S,im.w*S,im.h*S)}
  return rt.icon[k]=cv.toDataURL();
}
function petToyTrayRender(){
  const g=document.getElementById('petToyGrid'),p=petData(),rt=PET_RT;if(!g||!p||!rt||!rt.ready)return;
  let h='<button type="button" class="pet-toy pet-toy-any" onclick="petOfferToy(null)"><span class="pet-toy-ico pet-toy-q">?</span><b>Let '+esc(p.name)+' pick</b><small>His choice</small></button>';
  for(const id of Object.keys(PET_TOYS)){const t=PET_TOYS[id],ok=petToyUnlocked(p,id),ic=petToyIcon(rt,t.icon||id),w=ok?petToyWord(p,id):'Unlocks '+petToyLock(p,id);
    h+='<button type="button" class="pet-toy'+(ok?'':' locked')+(w==='Favorite'?' fav':'')+'" '+(ok?'onclick="petOfferToy(\''+id+'\')"':'disabled')+'><span class="pet-toy-ico">'+(ic?'<img src="'+ic+'" alt="">':'')+'</span><b>'+t.name+'</b><small>'+w+'</small></button>'}
  g.innerHTML=h;
}
/* ---------- what he likes, on the Cat screen ---------- */
function petLikesRender(force){
  const p=petData();if(!p)return;const lk=petLikes(p),el=document.getElementById('petFavs');
  if(el){const sp=lk.favSpot?PET_SPOTS[lk.favSpot].name:null,ty=lk.favToy?PET_TOYS[lk.favToy].name:null;
    const html='<div><small>Favorite place</small><b>'+(sp?sp.charAt(0).toUpperCase()+sp.slice(1):'Still deciding')+'</b></div><div><small>Favorite toy</small><b>'+(ty||'Still deciding')+'</b></div>';
    if(el.innerHTML!==html)el.innerHTML=html}
  const d=document.getElementById('petLikes');
  if(d&&(force||!d.dataset.t||Date.now()-d.dataset.t>15000)){d.dataset.t=Date.now();
    const sp=petTop(lk.spots).slice(0,5),ty=petTop(lk.toys).slice(0,4),mx=Math.max(1,sp.length?sp[0][1]:1),mt=Math.max(1,ty.length?ty[0][1]:1);
    const row=function(n,v,m,fav){return '<div class="pet-like"><span>'+n+(fav?' ♥':'')+'</span><i><em style="width:'+Math.round(v/m*100)+'%"></em></i></div>'};
    let h='<p><b>What '+esc(p.name)+' likes.</b> Cats pick their own spots. Time spent somewhere, and good moments there, slowly make a favorite. Toys you choose for him count a little more.</p>';
    h+=sp.length?'<div class="pet-likes-h">Places</div>'+sp.map(function(e){const n=PET_SPOTS[e[0]].name;return row(n.charAt(0).toUpperCase()+n.slice(1),e[1],mx,lk.favSpot===e[0])}).join(''):'<p class="muted">No favorite places yet. Give it a day or two.</p>';
    h+=ty.length?'<div class="pet-likes-h">Toys</div>'+ty.map(function(e){return row(PET_TOYS[e[0]].name,e[1],mt,lk.favToy===e[0])}).join(''):'';
    d.innerHTML=h}
  /* announce a new favourite once, when the Cat screen is showing */
  if(lk.news&&lk.news.length&&petDepthViewOpen()){const n=lk.news.shift();
    toast(n.k==='spot'?p.name+' has a favorite place now: '+PET_SPOTS[n.id].name+'.':p.name+'\u2019s favorite toy: the '+PET_TOYS[n.id].name.toLowerCase()+'.');save()}
}
/* ---------- once a minute ---------- */
function petLikesTick(rt){const p=petData();if(!p||!p.alive)return;if(Date.now()-(rt.likesAt||0)<60000)return;rt.likesAt=Date.now();const d0=petLikes(p).dayT;petLikesDaily(p);if(petLikes(p).dayT!==d0)save()}
/* developer: pretend some days have passed for the likes only */
function petDevLikes(days){const p=petData();if(!p)return;const lk=petLikes(p);if(days<0){p.likes=null;petLikes(p)}else{lk.dayT=(lk.dayT||petMidnight(Date.now()))-days*864e5;petLikesDaily(p)}save();petLikesRender(true);petToyTrayRender()}

devChatSeed('claude-pet-likes-v77-2026-10-24','Claude',[
'Pet likes (v77): favorite places, favorite toys and more exploring. Code in src/runtime/segments/25-pet-likes.js; saved in state.pet.likes.',
'• Places: 13 spots (PET_SPOTS), including new ones: both other window sills (reached via the sofa or the counter), a cardboard box in the kitchen he sits inside, and the living room rug. Each cat has its own taste (birth hash plus trait: shy likes hidden and high places, lazy likes soft ones, curious likes sunny ones). Routine spots, the night included, are picked by petLikesPickSpot. Once a day petLikesDaily credits the hours he spent in each place (0.95 decay). Petting where he lies adds a little. The weight is taste × (1 + hours/4)^1.6, so one place wins out; in tests a clear favorite settles in 2 to 6 days and then holds.',
'• Toys: Play opens a toy tray. There are 7 toys (PET_TOYS); feather, crinkle ball, yarn, spring and wand unlock with bond level or days together. Enthusiasm = taste × past play (+ novelty); toys you pick count 1.5×. A loved toy gets more pounce rounds and hearts. A disliked one is sometimes sniffed and ignored. Yarn rolls farther and the spring bounces higher. His own play, gifts and solo play use the favorite.',
'• Exploring: points of interest (PET_POI) chosen by how long since he last checked them; "investigate" and "patrol" (2 or 3 in a row) behaviours; he also visits his favorite spot outside rest time.',
'• UI: a Favorite place / Favorite toy row under the stats, "What X likes" bars in Care and settings, and a one-time toast when a favorite forms. Dev: Likes +3 days, Reset likes.'
].join('\\n'));
