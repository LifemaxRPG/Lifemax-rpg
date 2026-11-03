/* Demo mode, storage half (docs/architecture/DEMO_MODE.md). This is the FIRST segment on purpose: it runs before the page reads any
   save, preference or database, because the demo works by changing where the page's storage points, not by guarding each writer.

   While the marker key `lm_demo_active` exists in this browser's localStorage, `window.localStorage` is replaced by a view that
   reads and writes the same store under the prefix `lmdemo:v1:`, and IndexedDB database names get the same prefix. Every part of
   the page (and every feature script) then reaches only the demo's keys; the real save and every other real key are never read,
   written or listed by page code, so they stay byte-for-byte as they were. The marker is localStorage too, so it survives the app
   being killed: the next launch is still the demo, and Settings still offers the way back.

   The marker means "demo" by its presence alone, whatever its value. A damaged or empty marker therefore still shows the (blank)
   demo and never the real save, which keeps the real data out of reach of any mistake in this file. Only removing the marker
   (LM_DEMO.leave) returns to the real save. If this view cannot be installed, `on` stays false: the page then simply runs on the
   real save as it always did, with no demo features on, and nothing demo-related is written. */
const LM_DEMO=(function(){
 const MARKER='lm_demo_active',PREFIX='lmdemo:v1:';
 /* the databases the page opens by name; a fallback for browsers that cannot list databases when the demo's are cleared */
 const KNOWN_DBS=['lifemax_audio_v1','lifemax_usda_branded_v1','lma_trainer'];
 let real=null,present=false,installed=false;
 try{real=window.localStorage}catch(e){real=null}
 try{present=!!real&&real.getItem(MARKER)!==null}catch(e){present=false}
 /* the genuine methods, taken before anything can wrap them, and called with `real` as this */
 const SP=typeof Storage!=='undefined'?Storage.prototype:null;
 const raw={get:SP&&SP.getItem,set:SP&&SP.setItem,del:SP&&SP.removeItem};
 const idb=typeof IDBFactory!=='undefined'?IDBFactory.prototype:null;
 const idbRaw=idb?{open:idb.open,del:idb.deleteDatabase,list:idb.databases}:null;
 const demoKeys=()=>{const out=[];try{for(let i=0;i<real.length;i++){const k=real.key(i);if(k&&k.indexOf(PREFIX)===0)out.push(k.slice(PREFIX.length))}}catch(e){}return out};

 /* The demo's view of localStorage: the same surface, but every key is prefixed on the way in and unprefixed on the way out. */
 function view(){
  const api={
   getItem:k=>raw.get.call(real,PREFIX+String(k)),
   setItem:(k,v)=>raw.set.call(real,PREFIX+String(k),String(v)),
   removeItem:k=>raw.del.call(real,PREFIX+String(k)),
   key:i=>{const a=demoKeys();return i>=0&&i<a.length?a[i]:null},
   clear:()=>{demoKeys().forEach(k=>raw.del.call(real,PREFIX+k))}
  };
  const own=Object.prototype.hasOwnProperty;
  return new Proxy({},{
   get:(_,p)=>p==='length'?demoKeys().length:typeof p==='symbol'?undefined:own.call(api,p)?api[p]:(v=>v===null?undefined:v)(api.getItem(p)),
   set:(_,p,v)=>{if(typeof p==='string')api.setItem(p,v);return true},
   deleteProperty:(_,p)=>{if(typeof p==='string')api.removeItem(p);return true},
   has:(_,p)=>typeof p==='string'&&(own.call(api,p)||api.getItem(p)!==null),
   ownKeys:()=>demoKeys(),
   getOwnPropertyDescriptor:(_,p)=>{const v=typeof p==='string'?api.getItem(p):null;return v===null?undefined:{value:v,writable:true,enumerable:true,configurable:true}}
  });
 }
 /* IndexedDB: the same prefix on every database name the page opens, deletes or lists. */
 function prefixDatabases(){
  idb.open=function(name){const a=Array.prototype.slice.call(arguments);a[0]=PREFIX+String(name);return idbRaw.open.apply(this,a)};
  idb.deleteDatabase=function(name){return idbRaw.del.call(this,PREFIX+String(name))};
  if(idbRaw.list)idb.databases=function(){return idbRaw.list.call(this).then(l=>l.filter(d=>d.name.indexOf(PREFIX)===0).map(d=>Object.assign({},d,{name:d.name.slice(PREFIX.length)})))};
 }

 if(present&&real&&raw.get){
  try{
   const proxy=view();
   Object.defineProperty(window,'localStorage',{configurable:true,enumerable:true,get:()=>proxy});
   installed=window.localStorage===proxy;
  }catch(e){installed=false}
  if(installed&&idb){
   try{prefixDatabases()}catch(e){try{Object.defineProperty(window,'indexedDB',{configurable:true,get:()=>undefined})}catch(x){}}
  }
 }
 const on=installed;

 /* Clears everything the demo kept: its keys and its databases. Works in either mode, through the genuine methods. */
 function purge(){
  let ok=true;
  try{demoKeys().forEach(k=>raw.del.call(real,PREFIX+k))}catch(e){ok=false}
  try{
   const f=idb&&typeof indexedDB!=='undefined'?indexedDB:null;
   if(f){
    const drop=n=>{try{idbRaw.del.call(f,n)}catch(e){}};
    KNOWN_DBS.forEach(n=>drop(PREFIX+n));
    if(idbRaw.list)idbRaw.list.call(f).then(l=>l.forEach(d=>{if(d&&String(d.name).indexOf(PREFIX)===0)drop(d.name)})).catch(()=>{});
   }
  }catch(e){}
  return ok;
 }
 /* Starts a demo: clears what an earlier one left, stores the starting preferences (seed: {key: text}, only the demo's copies are
    written), and sets the marker last, so a failure before it leaves the page exactly as it was. The page reloads afterwards.
    Answers whether the demo is set up. */
 function enter(seed){
  if(on||!real||!raw.set)return false;
  try{
   purge();
   Object.keys(seed||{}).forEach(k=>{if(typeof seed[k]==='string')raw.set.call(real,PREFIX+k,seed[k])});
   raw.set.call(real,MARKER,JSON.stringify({v:1,at:Date.now()}));
   return raw.get.call(real,MARKER)!==null;
  }catch(e){try{purge()}catch(x){}return false}
 }
 /* Ends the demo: the marker goes first (from here the next start is the real save, whatever happens next), then the demo's data.
    Answers whether the marker is gone. */
 function leave(){
  if(!on||!real)return false;
  try{raw.del.call(real,MARKER)}catch(e){return false}
  let gone=false;try{gone=raw.get.call(real,MARKER)===null}catch(e){gone=false}
  if(gone)purge();
  return gone;
 }
 /* A real start clears any demo data an interrupted exit left behind (the page can write once more while it unloads). */
 if(!on&&real&&raw.get&&!present)try{if(demoKeys().length)purge()}catch(e){}
 const api={on,prefix:PREFIX,marker:MARKER,enter,leave,purge};
 try{window.LM_DEMO=api}catch(e){}
 return api;
})();
