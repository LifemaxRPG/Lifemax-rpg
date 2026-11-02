/* ---------- A coat's sheets for the native live cat (overview.cat.coat.v1; plan PET_ENGINE_PLAN 3.3, phase P3b).
   The page stays the only composer of genetic coats (petDnaCompose, 32-pet-dna): native cannot draw a "dna_..." cat without the
   index art the page builds from the four original sheets, so it asks, once per (coat, sheet kind), with the overview cards'
   `coat {key, kind}` command, and caches the answer by key.
   The answer is exactly what the room draws from, before the tint: the composed GREY-INDEX sheet (what petDnaIdxCanvas paints
   and petTintSheet reads, never the recoloured RGBA) as an 8-bit grey PNG, and the coat's palette (petDnaPalette: grey index
   to colour, as the room's A.pal[key]); native recolours with them as petTintSheet does.
   kind is what the pseudo-URL carries (dna:<kind>:<key>): adult, depth, st0..st5 (a kitten's side view) or dst0..dst5 (its depth view).
   Strict: a key the page does not hold (not "dna_...", a legacy coat, not one of the two cats' coats or a registered coat) or a kind
   outside that list is refused with a message. Nothing in an answer is logged.
   The PNG is written here (no library): per row a filter (None, Sub or Up), then DEFLATE with fixed codes and an LZ77 match finder,
   so a sheet, mostly runs of clear pixels, costs tens of kilobytes. */
var PET_COAT={KIND:/^(adult|depth|st[0-5]|dst[0-5])$/,KEY:/^dna_[a-z0-9_]{1,59}$/,MAX_B64:600000,MAX_PIXELS:1500000,cache:new Map(),CACHE:6,crcTable:null};
/* is key a genetic coat the page itself holds (one of the two cats', or registered with the room)? */
function petCoatKnown(key){
  if(typeof key!=='string'||!PET_COAT.KEY.test(key)||PET_DNA_LEGACY[key]||!petDnaRecipe(key))return false;
  return !!((state.pet&&state.pet.coat===key)||(state.pet2&&state.pet2.coat===key)||PET_DNA_KEYS[key]);
}
function petCoatCrc(buf){
  if(!PET_COAT.crcTable){const t=new Uint32Array(256);for(let n=0;n<256;n++){let c=n;for(let k=0;k<8;k++)c=c&1?0xedb88320^(c>>>1):c>>>1;t[n]=c>>>0}PET_COAT.crcTable=t}
  const t=PET_COAT.crcTable;let c=0xffffffff;
  for(let i=0;i<buf.length;i++)c=t[(c^buf[i])&255]^(c>>>8);
  return (c^0xffffffff)>>>0;
}
/* DEFLATE (RFC 1951): one final block of fixed Huffman codes */
var PET_COAT_LB=[3,4,5,6,7,8,9,10,11,13,15,17,19,23,27,31,35,43,51,59,67,83,99,115,131,163,195,227,258],
  PET_COAT_LE=[0,0,0,0,0,0,0,0,1,1,1,1,2,2,2,2,3,3,3,3,4,4,4,4,5,5,5,5,0],
  PET_COAT_DB=[1,2,3,4,5,7,9,13,17,25,33,49,65,97,129,193,257,385,513,769,1025,1537,2049,3073,4097,6145,8193,12289,16385,24577],
  PET_COAT_DE=[0,0,0,0,1,1,2,2,3,3,4,4,5,5,6,6,7,7,8,8,9,9,10,10,11,11,12,12,13,13];
function petCoatDeflate(src){
  let out=new Uint8Array(Math.max(1024,src.length>>2)),n=0,acc=0,bits=0;
  const put=function(v,len){acc|=v<<bits;bits+=len;while(bits>=8){if(n>=out.length){const g=new Uint8Array(out.length*2);g.set(out);out=g}out[n++]=acc&255;acc>>>=8;bits-=8}};
  const huff=function(code,len){let r=0;for(let i=0;i<len;i++){r=(r<<1)|(code&1);code>>=1}put(r,len)};   /* Huffman codes go out most significant bit first */
  const sym=function(v){if(v<144)huff(0x30+v,8);else if(v<256)huff(0x190+v-144,9);else if(v<280)huff(v-256,7);else huff(0xc0+v-280,8)};
  put(1,1);put(1,2);                                                            /* final block, fixed codes */
  const HS=1<<15,head=new Int32Array(HS).fill(-1),L=src.length;
  const hash=function(i){return ((src[i]<<10)^(src[i+1]<<5)^src[i+2])&(HS-1)};
  let i=0;
  while(i<L){
    let len=0,dist=0;
    if(i+2<L){const h=hash(i),j=head[h];head[h]=i;
      if(j>=0&&i-j<=32768){let m=0;while(m<258&&i+m<L&&src[j+m]===src[i+m])m++;if(m>=3){len=m;dist=i-j}}}
    if(!len){sym(src[i]);i++;continue}
    let c=28;while(PET_COAT_LB[c]>len)c--;
    sym(257+c);if(PET_COAT_LE[c])put(len-PET_COAT_LB[c],PET_COAT_LE[c]);
    let d=29;while(PET_COAT_DB[d]>dist)d--;
    huff(d,5);if(PET_COAT_DE[d])put(dist-PET_COAT_DB[d],PET_COAT_DE[d]);
    for(let k=1;k<len;k++){const q=i+k;if(q+2<L)head[hash(q)]=q}
    i+=len;
  }
  sym(256);if(bits)put(0,8-bits);
  return out.subarray(0,n);
}
/* an 8-bit grey PNG of idx (w*h index values, 0..255) */
function petCoatPng(w,h,idx){
  const raw=new Uint8Array(h*(w+1));
  for(let y=0;y<h;y++){
    const o=y*(w+1)+1,r=y*w;let best=0,bestCost=Infinity;
    for(let f=0;f<3;f++){let cost=0;
      for(let x=0;x<w;x++){const left=x?idx[r+x-1]:0,up=y?idx[r-w+x]:0,v=(idx[r+x]-(f===1?left:f===2?up:0))&255;cost+=v<128?v:256-v}
      if(cost<bestCost){bestCost=cost;best=f}}
    raw[o-1]=best;
    for(let x=0;x<w;x++){const left=x?idx[r+x-1]:0,up=y?idx[r-w+x]:0;raw[o+x]=(idx[r+x]-(best===1?left:best===2?up:0))&255}
  }
  const z=petCoatDeflate(raw),zl=new Uint8Array(z.length+6);
  zl[0]=0x78;zl[1]=0x01;zl.set(z,2);
  let a=1,b=0;for(let i=0;i<raw.length;i++){a=(a+raw[i])%65521;b=(b+a)%65521}
  const ad=((b<<16)|a)>>>0,e=z.length+2;zl[e]=ad>>>24;zl[e+1]=(ad>>>16)&255;zl[e+2]=(ad>>>8)&255;zl[e+3]=ad&255;
  const ihdr=new Uint8Array(13),dv=new DataView(ihdr.buffer);dv.setUint32(0,w);dv.setUint32(4,h);ihdr[8]=8;
  const chunks=[[73,72,68,82,ihdr],[73,68,65,84,zl.subarray(0,e+4)],[73,69,78,68,new Uint8Array(0)]];
  let total=8;chunks.forEach(function(c){total+=12+c[4].length});
  const png=new Uint8Array(total);png.set([0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a]);
  let p=8;
  chunks.forEach(function(c){
    const len=c[4].length,body=new Uint8Array(4+len);body.set(c.slice(0,4));body.set(c[4],4);
    const v=new DataView(png.buffer);v.setUint32(p,len);png.set(body,p+4);v.setUint32(p+4+body.length,petCoatCrc(body));p+=12+len;
  });
  return png;
}
function petCoatB64(bytes){let s='';for(let i=0;i<bytes.length;i+=0x8000)s+=String.fromCharCode.apply(null,bytes.subarray(i,i+0x8000));return btoa(s)}
/* the coat's palette as the room's canvas holds it: "index" -> "rrggbb" (a Uint8ClampedArray rounds as the canvas does) */
function petCoatPalette(rec){
  const P=petDnaPalette(rec),out={};
  Object.keys(P).forEach(function(k){
    const c=new Uint8ClampedArray([P[k][0],P[k][1],P[k][2]]);
    out[k]=((c[0]<<16)|(c[1]<<8)|c[2]).toString(16).padStart(6,'0');
  });
  return out;
}
/* the sheet of coat key for kind as the room composes it: the composed index values (Uint8Array) and the sheet's size, or null */
async function petCoatIndex(key,kind){
  const rec=petDnaRecipe(key),S=await petDnaSrcIdx(kind);
  if(!rec||!S||S.w*S.h>PET_COAT.MAX_PIXELS)return null;
  return {w:S.w,h:S.h,idx:petDnaCompose(kind,rec,S)};
}
/* the answer to `coat {key, kind}`: {ok:true, coat, kind, png (base64), palette} or {ok:false, message} */
async function petCoatAnswer(p){
  const no=function(m){return {ok:false,message:m}};
  const key=p&&p.key,kind=p&&p.kind;
  if(typeof kind!=='string'||!PET_COAT.KIND.test(kind))return no('That sheet is not one the room has.');
  if(!petCoatKnown(key))return no('That coat is not one this page holds.');
  const ck=key+'|'+kind,hit=PET_COAT.cache.get(ck);
  if(hit){PET_COAT.cache.delete(ck);PET_COAT.cache.set(ck,hit);return Object.assign({ok:true,coat:key,kind:kind},hit)}
  try{
    const s=await petCoatIndex(key,kind);if(!s)return no('The sheet could not be built.');
    const png=petCoatB64(petCoatPng(s.w,s.h,s.idx));
    if(png.length>PET_COAT.MAX_B64)return no('The sheet is too large to hand over.');
    const a={png:png,palette:petCoatPalette(petDnaRecipe(key))};
    if(PET_COAT.cache.size>=PET_COAT.CACHE)PET_COAT.cache.delete(PET_COAT.cache.keys().next().value);
    PET_COAT.cache.set(ck,a);
    return Object.assign({ok:true,coat:key,kind:kind},a);
  }catch(e){console.warn('[pet coat]',e&&e.message);return no('The sheet could not be built.')}
}
