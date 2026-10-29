/* Run: node tests/director-finish.test.js. Exercises the real SVG renderer and
   hand movement; no production test API. Also exports fixtures for browser QA. */
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.join(__dirname,'..');
function fixture(source){
 const context={console,Intl,Date,Math,Map,Set,document:{hidden:false,addEventListener(){}},LifeMaxFeatures:{register(){}},matchMedia:()=>({matches:false}),
 px:(name,size)=>'<svg class="px" width="'+size+'" height="'+size+'" viewBox="0 0 16 16" fill="currentColor"><path d="M1 5h3V3h2v4h4V3h2v2h3v6h-3v2h-2V9H6v4H4v-2H1z"/></svg>'};
 context.window=context;vm.createContext(context);
 const marker='  return { mount, glance, widgetSnapshot, voice: voiceApi';
 const code=(source||fs.readFileSync(path.join(root,'src/features/life-director/logic.js'),'utf8')).replace(marker,'  return { finishTest:{dialHtml,faceHtml,dayBlocks,updateDialHand:typeof updateDialHand==="function"?updateDialHand:null,runBuild,setState(s){ST=s}}, mount, glance, widgetSnapshot, voice: voiceApi');
 vm.runInContext(code,context);const api=vm.runInContext('LifeDirectorApp.finishTest',context);
 const start=Date.parse('2026-10-28T00:00:00Z')/1000,at=h=>start+h*3600;
 const segment=(kind,title,s,e)=>({kind,title,code:kind,startEpoch:at(s),endEpoch:at(e)});
 const task=(id,title,s,e,lifecycleState='PLANNED')=>({instanceId:id,title,tag:'fitness',startEpoch:at(s),endEpoch:at(e),lifecycleState,durationSlots:(e-s)*4,linkedQuestRequirementIds:[]});
 const state={availability:{timezone:'UTC',rules:[{id:'sleep',kind:'SLEEP',startMin:480,durationMin:480},{id:'work',kind:'WORK',title:'Work',startMin:0,durationMin:360}]},ui:{},fixedCommitments:[],schedule:{horizonStartEpoch:start,horizonEndEpoch:at(48),historyInstances:[],scheduledInstances:[task('done','Mobility',6.5,7,'COMPLETED_ON_TIME'),task('off','Reading',7,7.5,'SKIPPED_VOLUNTARY'),task('current','Plan: Foundation Strength',18.75,19.25),task('next','Three Strength Sessions',19.25,20.25)],segments:[segment('WORK','Work',0,6),segment('MEAL','Breakfast',6,6.5),segment('SLEEP','Sleep',8,16),segment('ROUTINE','Morning routine',16,16.5),segment('MEAL','Lunch',16.5,17),segment('MEAL','Dinner',21,21.5)]}};
 api.setState(state);const day={startEpoch:start,endEpoch:at(24),ymd:{y:2026,mo:10,d:28},dow:3};
 const render=(hour=19.15,plain=false)=>api.dialHtml(day,api.dayBlocks(state.schedule,day,'UTC'),true,at(hour),plain);
 return {api,context,state,day,render};
}
module.exports={fixture};
if(require.main===module){
 const f=fixture(),first=f.render(),second=f.render();
 let passed=0;const check=(name,fn)=>{fn();passed++;console.log('PASS '+name)};
 check('paint references are self-contained and unique across multiple dials',()=>{
  const ids=html=>[...html.matchAll(/\sid="([^"]+)"/g)].map(m=>m[1]);
  const a=ids(first),b=ids(second);assert.equal(new Set([...a,...b]).size,a.length+b.length);
  for(const html of [first,second,f.api.faceHtml()])for(const match of html.matchAll(/url\(#([^)]+)\)/g))assert(html.includes('id="'+match[1]+'"'));
 });
 check('depth surfaces never claim a touch target or accessible action',()=>{
  assert(first.includes('class="ld-depth" aria-hidden="true" pointer-events="none"'));
  assert(first.includes('class="ld-surface" aria-hidden="true" pointer-events="none"'));
  assert.equal((first.match(/role="slider"/g)||[]).length,4);assert(first.includes('data-id="current"'));assert(first.includes('data-k="meal"'));
 });
 check('skipped sessions keep their dashed state without a solid finish',()=>{
  const off=first.match(/<g class="ld-ses ld-s-off[^]*?<\/g>/)[0];assert(!off.includes('ld-surface'));assert(off.includes('ld-sx'));
 });
 check('plain widget dial contains its own finishes with no live hand or handles',()=>{
  const html=f.render(19,true);assert(!html.includes('class="ld-hand"'));assert(!html.includes('role="slider"'));assert(html.includes('ld-depth-hub'));
 });
 check('hand crosses midnight by a fraction of a degree, preserving true SVG time',()=>{
  let transform='rotate(359.95 180 180)',frames,opts;const hand={getAttribute:()=>transform,setAttribute:(k,v)=>transform=v,animate:(f,o)=>{frames=f;opts=o;return{cancel(){}}}};
  f.api.updateDialHand(hand,.05);assert.equal(transform,'rotate(0.05 180 180)');assert(Math.abs(+frames[1].transform.match(/rotate\(([-\d.]+)/)[1]-(+frames[0].transform.match(/rotate\(([-\d.]+)/)[1]))<.11);assert.equal(opts.duration,650);
 });
 check('reduced motion, hidden pages and large time jumps update without animation',()=>{
  let calls=0;const hand={getAttribute:()=> 'rotate(0 180 180)',setAttribute(){},animate:()=>{calls++}};
  f.context.matchMedia=()=>({matches:true});f.api.updateDialHand(hand,.1);
  f.context.matchMedia=()=>({matches:false});f.context.document.hidden=true;f.api.updateDialHand(hand,.1);
  f.context.document.hidden=false;f.api.updateDialHand(hand,90);assert.equal(calls,0);
 });
 console.log(passed+' finish checks passed');
}
