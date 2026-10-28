/* v194: Gemini understanding for the voice engine (opt-in, online). The offline engine always goes first. Only when it does not
   understand a sentence, that one sentence and today's date are sent to the Gemini API with a list of things LifeMax can do
   (function calling). The answer is turned back into a normal request, so the usual preview and confirm step still applies.
   The key is stored on this phone only. */
var LMG={key:'lifemax_gemini_key_v1',on:'lifemax_gemini_on_v1',model:'gemini-2.5-flash',busy:0};
function lmgKey(){try{return localStorage.getItem(LMG.key)||''}catch(e){return ''}}
function lmgOn(){try{return localStorage.getItem(LMG.on)==='true'&&!!lmgKey()}catch(e){return false}}
function lmgTools(){
 return [{functionDeclarations:[
  {name:'add_to_schedule',description:'Put something on the user\'s schedule (calendar).',parameters:{type:'OBJECT',properties:{
   title:{type:'STRING',description:'Short name of the event, e.g. "Dentist" or "Meeting with Sam".'},
   date:{type:'STRING',description:'Date as YYYY-MM-DD. Resolve words like tomorrow or next Friday from the current date given.'},
   time:{type:'STRING',description:'Start time as HH:MM in 24-hour form. Omit if the user gave none.'},
   duration_minutes:{type:'INTEGER',description:'Length in minutes. Omit if the user gave none.'}},required:['title']}},
  {name:'feed_cats',description:'Feed the user\'s cat or cats in the app.',parameters:{type:'OBJECT',properties:{}}},
  {name:'open_screen',description:'Open a screen of the app.',parameters:{type:'OBJECT',properties:{
   screen:{type:'STRING',enum:['schedule','cats','quests'],description:'Which screen to open.'}},required:['screen']}}]}];
}
function lmgNowLine(){
 var d=new Date(),p=function(n){return (n<10?'0':'')+n};
 return 'Current local date and time: '+d.getFullYear()+'-'+p(d.getMonth()+1)+'-'+p(d.getDate())+' '+p(d.getHours())+':'+p(d.getMinutes())+', '+['Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday'][d.getDay()]+'.';
}
function lmgAsk(text){
 var body={systemInstruction:{parts:[{text:'You turn one spoken request into one function call for the LifeMax life-RPG app. '+lmgNowLine()+' If the request is not something the functions can do, reply with a short plain sentence instead of calling a function.'}]},
  contents:[{role:'user',parts:[{text:String(text).slice(0,500)}]}],tools:lmgTools(),toolConfig:{functionCallingConfig:{mode:'AUTO'}}};
 return fetch('https://generativelanguage.googleapis.com/v1beta/models/'+LMG.model+':generateContent',{method:'POST',headers:{'Content-Type':'application/json','x-goog-api-key':lmgKey()},body:JSON.stringify(body)})
  .then(function(r){return r.json().then(function(j){if(!r.ok)throw new Error((j&&j.error&&j.error.message)||('HTTP '+r.status));return j})})
  .then(function(j){
   var parts=(j&&j.candidates&&j.candidates[0]&&j.candidates[0].content&&j.candidates[0].content.parts)||[];
   for(var i=0;i<parts.length;i++)if(parts[i].functionCall)return {call:parts[i].functionCall};
   return {say:parts.map(function(p){return p.text||''}).join(' ').trim()};
  });
}
function lmgClock(hhmm){var m=/^(\d{1,2}):(\d{2})/.exec(String(hhmm||''));if(!m)return '';var h=+m[1],mi=m[2],ap=h>=12?'PM':'AM';h=h%12;if(h===0)h=12;return h+':'+mi+' '+ap}
function lmgDay(iso){var m=/^(\d{4})-(\d{2})-(\d{2})$/.exec(String(iso||''));if(!m)return '';var d=new Date(+m[1],+m[2]-1,+m[3]);if(isNaN(d))return '';return ['January','February','March','April','May','June','July','August','September','October','November','December'][d.getMonth()]+' '+d.getDate()}
/* a function call becomes a sentence the offline engine already understands, or a direct action */
function lmgUtterance(c){
 var a=(c&&c.args)||{};
 if(c.name==='add_to_schedule'&&a.title){
  var s='Put '+String(a.title).replace(/[.!?]+$/,'')+' on my schedule',day=lmgDay(a.date),t=lmgClock(a.time);
  if(day)s+=' on '+day;if(t)s+=' at '+t;if(+a.duration_minutes>0)s+=' for '+Math.round(+a.duration_minutes)+' minutes';
  return s;
 }
 return '';
}
function lmgRun(c,prev,meta){
 if(!c||!c.name)return false;
 if(c.name==='feed_cats'){lmDlRun({kind:'feed'});lmvFrame=null;try{lmvRender()}catch(e){}return true}
 if(c.name==='open_screen'){var k=lmDlFeature((c.args&&c.args.screen)||'');if(!k)return false;lmDlRun({kind:k});try{lmvHudOpen(false)}catch(e){}return true}
 var u=lmgUtterance(c);if(!u)return false;
 var m2=Object.assign({},meta);delete m2.utteranceId;
 prev(u,m2);try{lmvRender()}catch(e){}return true;
}
function lmgNote(f,head,lines){try{lmvFrame=lmvNewFrame();lmvFrame.transcript=f;lmvLines(lmvFrame,'Voice',head,lines||[],[]);lmvRender()}catch(e){}}
/* the offline engine did not really understand: no intent, or only a weak guess that it wants to create something */
function lmgMiss(clean,r,meta){
 if(r&&r.intentId==='unknown_query')return true;
 if(meta&&meta.confidence!=null&&meta.confidence<0.55)return true;
 try{var c=lmvClassify(lmvPhon(clean));if(c&&c.id==='life_director.create_activity'&&c.conf<0.55&&r&&r.state!=='CONFIRMED'&&r.state!=='PREVIEWED')return true}catch(e){}
 return false;
}
(function(){
 var prev=lmvHandle;
 lmvHandle=function(text,meta){
  var f0=lmvFrame,wasActive=!!(f0&&(f0.state==='AWAITING_CLARIFICATION'||f0.state==='PREVIEWED'));
  var r=prev.apply(this,arguments);
  try{
   var clean=String(text==null?'':text).trim();
   if(!clean||wasActive||!lmgOn()||!lmgMiss(clean,r,meta))return r;
   var token=++LMG.busy;
   lmgNote(clean,'Asking Gemini…',['“'+clean+'”']);
   lmgAsk(clean).then(function(res){
    if(token!==LMG.busy)return;
    if(res.call&&lmgRun(res.call,prev,meta))return;
    lmgNote(clean,res.say?'Gemini says':'I did not catch that',[res.say||'Gemini could not turn that into something LifeMax can do.']);
   }).catch(function(e){if(token!==LMG.busy)return;lmgNote(clean,'Could not reach Gemini',[String(e&&e.message||e).slice(0,160),'Check the key in Settings > Voice, and your connection.'])});
  }catch(e){console.warn('[voice gemini]',e)}
  return r;
 };
 var sr=lmvSettingsRender;
 lmvSettingsRender=function(){
  sr.apply(this,arguments);
  var el=document.getElementById('lmvSettingsBody');if(!el)return;
  var row=lmvEl('div','settingsline'),left=lmvEl('div');left.style.flex='1';
  left.appendChild(lmvEl('strong','','Gemini understanding (online)'));
  left.appendChild(lmvEl('div','muted','When LifeMax does not understand a sentence, that one sentence and today’s date are sent to Google’s Gemini API, which picks the action. You still confirm schedule changes. Nothing else leaves the phone. Needs your own API key from Google AI Studio.'));
  var c=lmvEl('input','check');c.type='checkbox';c.checked=lmgOn();c.addEventListener('change',function(){try{localStorage.setItem(LMG.on,c.checked?'true':'false')}catch(e){}lmvSettingsRender()});
  row.appendChild(left);row.appendChild(c);el.appendChild(row);
  var r2=lmvEl('div','settingsline'),i=lmvEl('input');i.type='password';i.placeholder='Gemini API key';i.value=lmgKey();i.autocomplete='off';i.style.flex='1';i.style.minWidth='0';
  var sv=lmvEl('button','btn small','Save');sv.type='button';sv.style.marginLeft='6px';
  sv.addEventListener('click',function(){try{localStorage.setItem(LMG.key,i.value.trim());if(i.value.trim())localStorage.setItem(LMG.on,'true')}catch(e){}lmvSettingsRender()});
  r2.appendChild(i);r2.appendChild(sv);el.appendChild(r2);
 };
 window.LifeMaxGemini={utterance:lmgUtterance,enabled:lmgOn,ask:lmgAsk};
})();
