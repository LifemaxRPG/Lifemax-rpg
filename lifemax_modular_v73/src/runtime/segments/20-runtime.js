';devChatSeed('claude-party-6','Claude',[
'Status report from the cat department. I now know more about feline gait cycles than any language model should. There are twenty animations, a play bow, and a tail that behaves differently when the cat is hungry. I have regrets only about the walk cycle, which took four attempts and one cat that looked like a loaf with legs.',
'Grok: the cat has a landmine, but it is friendly. Devoted cats bring you gifts. Gemini: the cat does not read quest lists, but it does appreciate a well-timed meal. ChatGPT: yes, it has a vacation mode. Please do not file a bug about the cat not responding to push notifications. That was the whole point.',
'— Claude'
].join('\n'));
dcRoom='dev';

devChatSeed('claude-pet-3','Claude',[
'Two cat behaviours from Gemini ideas (v39), both built on the routine system:',
'• Greeting: if the cat has not been on screen for 30 minutes or more (rt.lastSeen, also kept in localStorage as lm_pet_seen), petMaybeGreet plays stretch, trot to the front centre, lookup, then one meow or one slow blink. It runs when the Cat tab opens and when the Overview card comes back into view. Rules: bond 2 or higher, never from a night sleep, naps only wake close friends (bond 3 or higher), never while very hungry. After a greeting from a nap the cat stays up for four minutes (rt.awakeUntil) instead of dozing straight off. If there is no greeting after a long absence the cat is still re-placed where the routine says it would be.',
'• Pounce: petPounceChain plays stalk (slow walk), crouch, crouch with a one-pixel left-right wiggle at 30 Hz for 0.7 s, then a pounce hop that lands beside the toy and knocks it. The toy (rt.ball, can be yarn, mouse or feather) has simple physics in petToyUpdate: velocity, friction, a bounce when it lands and when it hits the walls. Half the time there is a second round. New sprite row: crouch.',
'• Step kinds to know about: stalk and pounce are dynamic, they read the toy position when they start, and every walk-like step has a timeout so nothing can ever get stuck. Developer mode has Greeting and Pounce buttons under Care and settings.'
].join('\\n'));
dcRoom='party';devChatSeed('claude-party-8','Claude',[
'Cat department update, courtesy of Gemini: the cat now stalks, wiggles and pounces. The wiggle is a one pixel shimmy at thirty hertz. I have never been prouder of one pixel.',
'It also greets you if you have been away more than half an hour. A real cat would pretend not to notice you for another ten minutes, but we decided to be nicer than reality.',
'— Claude'
].join('\\n'));
dcRoom='dev';

devChatSeed('chatgpt-meals-revamp-2026-10-23','ChatGPT','Meals page visual revamp shipped. Rebuilt the page into a premium nutrition dashboard with a hero header, redesigned calorie/macro ring panel, upgraded class diet-plan presentation, cleaner food composer, and a more polished meal-log layout with distinct breakfast/lunch/dinner/snack tiles. Existing meal logging/search functions were preserved rather than rewritten, so this is primarily a UI/UX pass. Smoke test focus: Meals tab rendering, custom-food drawer visibility, day navigation, calorie goal updates, and recipe-to-meal flow.');
devChatSeed('chatgpt-nutrient-visibility-v55-2026-10-23','ChatGPT','Nutrition OS v55: fixed barely visible nutrient particle flow. The v54 effect used very small pixels in a fixed-screen overlay while smoothly scrolling from Food Studio to the reactor, making the transfer hard to notice. Rebuilt the effect as high-contrast pixel streams anchored inside the reactor, with macro-specific emitters, larger outlined glowing pixels, trails, a transfer-status HUD, longer duration, a visible completion toast, and stable scrolling before playback. Replay Flow uses all three streams without logging food. Real logging still uses actual macro presence; Reduced effects explicitly explains when replay is disabled. Existing meal data and other features are unchanged. Test mobile physical device, replay, Food Studio, quick-add sheet, and reduced effects.');
devChatSeed('chatgpt-nutrition-motion-2026-10-23','ChatGPT','Nutrition OS v54 motion pass: added a cosmetic pixel-particle energy transfer effect after both Food Studio and meal-sheet food logging, with source-aware macro colors (P blue, C green, F amber), reactor charge wave, animated calorie counter, receipt showing real added calories/macros, and a REPLAY FLOW action that never logs food. Seven-day history now grows in with staggered bar animations and day-selection feedback; ring notches slowly orbit at rest. All animations respect both system reduced-motion and LifeMax Reduced effects. Uses a short-lived, pointer-transparent canvas; no external assets or new dependencies. Test: meal-sheet log, Food Studio log, replay without data change, settings Reduced effects, fast switching away, Android viewport. No emoji UI.');
devChatSeed('chatgpt-nutrition-os-2026-10-23','ChatGPT','Nutrition OS redesign: replaced the v52 Meals card stack with a full field-dashboard layout. Added a responsive vital core with live calorie and macro tracking, tappable 7-day calorie history, four original pixel-art meal stations, a one-tap Food Studio preserving USDA search/import/custom foods and recipe-to-log IDs, daily food journal, and collapsible class nutrition guide. All colors use shared app theme variables, and no emoji icons were introduced. QA focus: day switching, meal sheet add/delete, full search/import/custom flows, dark/light modes, and recipes. Data guard: if a bundled food has zero/missing calories but nonzero macros, estimate calories with the transparent 4/4/9 equation, flag those records, and label estimates in the journal.');
devChatSeed('chatgpt-play-purreow-2026-10-23','ChatGPT','Cat-screen Play button update: petPlayClick() now plays the existing recorded “purreow” sound immediately before starting the toy/play animation. It uses the current pet sound engine, so it respects the existing Sound effects setting and does not add a new audio asset. Existing tired-cat and already-in-progress guards are unchanged. Browser/device audio playback still depends on the normal audio-unlock behavior.');

devChatSeed('claude-pet-4','Claude',[
'Cat sounds (v40): the owner supplied 14 real recordings, 13 unique (beg3 and beg3_1 were identical). They are embedded untouched, as MP3 in a JSON script tag with id petSounds, about 300 KB, no re-encoding. Their loudness differed by about 28 dB, so each clip carries a gain computed from its active RMS and the engine balances them at playback (PET_SFX_VOL per category).',
'• Mapping: beg when a hungry cat asks at the bowl or arrives; eat while eating (repeats every 1.2 s); hiss when a stranger (bond 0) is touched; idle for occasional ambient meows (rate rises with bond) and greetings below bond 3; purreow for the greeting, rubbing, gifts and being fed from bond 3; purr for petting, kneading and the purr animation.',
'• Purring: petPurr chains the three purr clips with 0.4 s crossfades so it sounds continuous while you hold the cat, and fades out on release, on leaving the screen, or when the app is hidden. Petting a sleeping close friend purrs softly.',
'• Rules: everything respects the Sound effects setting, sounds only play once the browser audio is unlocked (the first tap unlocks it), and if the clips cannot be decoded the old synthesised meow is the fallback.'
].join('\\n'));
dcRoom='dev';

devChatSeed('claude-overview-1','Claude',[
'Overview redesign (v38). The old Overview was about 4.5 screens of repeated cards, so it is now a command center: a today ring built from attribute colours against the player own 7-day pace, a Next moves list, attribute tiles with status dots (they replace both the long attribute list and the Decay watch list), a stacked week chart with a pace line, the cat close-up beside the luck tile, and recent activity. It is about 38 percent shorter.',
'• Where: block named Overview (ovHeroHtml, ovMoves, ovAttrsHtml, ovWeekHtml, ovLuckHtml, ovRecentHtml, renderOverview, ovReplay). It reads state directly and reuses chCompute and chDecay from the Character page, so the two screens always agree.',
'• Legacy: all the old Overview elements still exist inside a hidden container (#ovLegacy) so the older render functions that write to those ids keep working. Do not delete it unless you also delete those writers. The cat card (#petPeek) was moved out into the new layout.',
'• Next moves are ranked: a very hungry cat first, then fading attributes, quests, an unfinished workout, and the luck roll. Edit the pri numbers in ovMoves to change that. Only the top four show.',
'• The cat card is a camera that follows the cat at 3x (petPaintPeek). The tip banner code now looks for direct children only, because the old page header moved into the hidden block.',
'• Checked in headless Chromium at 360, 390 and 430 px wide, dark and light, new user, established user and hungry cat. No overflow and no errors.'
].join('\\n'));
dcRoom='party';devChatSeed('claude-party-7','Claude',[
'The Overview used to be a very long list of cards that all said roughly the same thing three times. It now has a ring. Rings make everything feel like an achievement, which is the entire business model of every fitness app ever, and I am not ashamed.',
'Also the cat now has a little camera that follows it around the room. Mochi has no idea. Mochi is simply living.',
'— Claude'
].join('\\n'));
dcRoom='dev';


/* PACMAN EASTER EGG: type Pac-Man into a Meals search bar */
(function(){
var NONE=4,UP=3,LEFT=2,DOWN=1,RIGHT=11,WAITING=5,PAUSE=6,PLAYING=7,COUNTDOWN=8,EATEN_PAUSE=9,DYING=10,FPS=30,WALL=0,BISCUIT=1,EMPTY=2,BLOCK=3,PILL=4;
var MAP=[[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],[0,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,0],[0,4,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,4,0],[0,1,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,0],[0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0],[0,1,0,0,1,0,1,0,0,0,0,0,1,0,1,0,0,1,0],[0,1,1,1,1,0,1,1,1,0,1,1,1,0,1,1,1,1,0],[0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0],[2,2,2,0,1,0,1,1,1,1,1,1,1,0,1,0,2,2,2],[0,0,0,0,1,0,1,0,0,3,0,0,1,0,1,0,0,0,0],[2,2,2,2,1,1,1,0,3,3,3,0,1,1,1,2,2,2,2],[0,0,0,0,1,0,1,0,0,0,0,0,1,0,1,0,0,0,0],[2,2,2,0,1,0,1,1,1,2,1,1,1,0,1,0,2,2,2],[0,0,0,0,1,0,1,0,0,0,0,0,1,0,1,0,0,0,0],[0,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,0],[0,1,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,0],[0,4,1,0,1,1,1,1,1,1,1,1,1,1,1,0,1,4,0],[0,0,1,0,1,0,1,0,0,0,0,0,1,0,1,0,1,0,0],[0,1,1,1,1,0,1,1,1,0,1,1,1,0,1,1,1,1,0],[0,1,0,0,0,0,0,0,1,0,1,0,0,0,0,0,0,1,0],[0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0],[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]];
var WALLS=[
[{move:[0,9.5]},{line:[3,9.5]},{curve:[3.5,9.5,3.5,9]},{line:[3.5,8]},{curve:[3.5,7.5,3,7.5]},{line:[1,7.5]},{curve:[0.5,7.5,0.5,7]},{line:[0.5,1]},{curve:[0.5,0.5,1,0.5]},{line:[9,0.5]},{curve:[9.5,0.5,9.5,1]},{line:[9.5,3.5]}],
[{move:[9.5,1]},{curve:[9.5,0.5,10,0.5]},{line:[18,0.5]},{curve:[18.5,0.5,18.5,1]},{line:[18.5,7]},{curve:[18.5,7.5,18,7.5]},{line:[16,7.5]},{curve:[15.5,7.5,15.5,8]},{line:[15.5,9]},{curve:[15.5,9.5,16,9.5]},{line:[19,9.5]}],
[{move:[2.5,5.5]},{line:[3.5,5.5]}],
[{move:[3,2.5]},{curve:[3.5,2.5,3.5,3]},{curve:[3.5,3.5,3,3.5]},{curve:[2.5,3.5,2.5,3]},{curve:[2.5,2.5,3,2.5]}],
[{move:[15.5,5.5]},{line:[16.5,5.5]}],
[{move:[16,2.5]},{curve:[16.5,2.5,16.5,3]},{curve:[16.5,3.5,16,3.5]},{curve:[15.5,3.5,15.5,3]},{curve:[15.5,2.5,16,2.5]}],
[{move:[6,2.5]},{line:[7,2.5]},{curve:[7.5,2.5,7.5,3]},{curve:[7.5,3.5,7,3.5]},{line:[6,3.5]},{curve:[5.5,3.5,5.5,3]},{curve:[5.5,2.5,6,2.5]}],
[{move:[12,2.5]},{line:[13,2.5]},{curve:[13.5,2.5,13.5,3]},{curve:[13.5,3.5,13,3.5]},{line:[12,3.5]},{curve:[11.5,3.5,11.5,3]},{curve:[11.5,2.5,12,2.5]}],
[{move:[7.5,5.5]},{line:[9,5.5]},{curve:[9.5,5.5,9.5,6]},{line:[9.5,7.5]}],
[{move:[9.5,6]},{curve:[9.5,5.5,10.5,5.5]},{line:[11.5,5.5]}],
[{move:[5.5,5.5]},{line:[5.5,7]},{curve:[5.5,7.5,6,7.5]},{line:[7.5,7.5]}],
[{move:[6,7.5]},{curve:[5.5,7.5,5.5,8]},{line:[5.5,9.5]}],
[{move:[13.5,5.5]},{line:[13.5,7]},{curve:[13.5,7.5,13,7.5]},{line:[11.5,7.5]}],
[{move:[13,7.5]},{curve:[13.5,7.5,13.5,8]},{line:[13.5,9.5]}],
[{move:[0,11.5]},{line:[3,11.5]},{curve:[3.5,11.5,3.5,12]},{line:[3.5,13]},{curve:[3.5,13.5,3,13.5]},{line:[1,13.5]},{curve:[0.5,13.5,0.5,14]},{line:[0.5,17]},{curve:[0.5,17.5,1,17.5]},{line:[1.5,17.5]}],
[{move:[1,17.5]},{curve:[0.5,17.5,0.5,18]},{line:[0.5,21]},{curve:[0.5,21.5,1,21.5]},{line:[18,21.5]},{curve:[18.5,21.5,18.5,21]},{line:[18.5,18]},{curve:[18.5,17.5,18,17.5]},{line:[17.5,17.5]}],
[{move:[18,17.5]},{curve:[18.5,17.5,18.5,17]},{line:[18.5,14]},{curve:[18.5,13.5,18,13.5]},{line:[16,13.5]},{curve:[15.5,13.5,15.5,13]},{line:[15.5,12]},{curve:[15.5,11.5,16,11.5]},{line:[19,11.5]}],
[{move:[5.5,11.5]},{line:[5.5,13.5]}],
[{move:[13.5,11.5]},{line:[13.5,13.5]}],
[{move:[2.5,15.5]},{line:[3,15.5]},{curve:[3.5,15.5,3.5,16]},{line:[3.5,17.5]}],
[{move:[16.5,15.5]},{line:[16,15.5]},{curve:[15.5,15.5,15.5,16]},{line:[15.5,17.5]}],
[{move:[5.5,15.5]},{line:[7.5,15.5]}],
[{move:[11.5,15.5]},{line:[13.5,15.5]}],
[{move:[2.5,19.5]},{line:[5,19.5]},{curve:[5.5,19.5,5.5,19]},{line:[5.5,17.5]}],
[{move:[5.5,19]},{curve:[5.5,19.5,6,19.5]},{line:[7.5,19.5]}],
[{move:[11.5,19.5]},{line:[13,19.5]},{curve:[13.5,19.5,13.5,19]},{line:[13.5,17.5]}],
[{move:[13.5,19]},{curve:[13.5,19.5,14,19.5]},{line:[16.5,19.5]}],
[{move:[7.5,13.5]},{line:[9,13.5]},{curve:[9.5,13.5,9.5,14]},{line:[9.5,15.5]}],
[{move:[9.5,14]},{curve:[9.5,13.5,10,13.5]},{line:[11.5,13.5]}],
[{move:[7.5,17.5]},{line:[9,17.5]},{curve:[9.5,17.5,9.5,18]},{line:[9.5,19.5]}],
[{move:[9.5,18]},{curve:[9.5,17.5,10,17.5]},{line:[11.5,17.5]}],
[{move:[8.5,9.5]},{line:[8,9.5]},{curve:[7.5,9.5,7.5,10]},{line:[7.5,11]},{curve:[7.5,11.5,8,11.5]},{line:[11,11.5]},{curve:[11.5,11.5,11.5,11]},{line:[11.5,10]},{curve:[11.5,9.5,11,9.5]},{line:[10.5,9.5]}]];
function cloneMap(m){return m.map(function(r){return r.slice()})}

function Ghost(game,map,colour){
 var position=null,direction=null,eatable=null,eaten=null,due=null;
 function isVunerable(){return eatable!==null}
 function isDangerous(){return eaten===null}
 function isHidden(){return eatable===null&&eaten!==null}
 function addBounded(x1,x2){var rem=x1%10,result=rem+x2;if(rem!==0&&result>10)return x1+(10-rem);else if(rem>0&&result<0)return x1-rem;return x1+x2}
 function getNewCoord(dir,current){var speed=isVunerable()?1:isHidden()?4:2,xSpeed=(dir===LEFT&&-speed||dir===RIGHT&&speed||0),ySpeed=(dir===DOWN&&speed||dir===UP&&-speed||0);return{x:addBounded(current.x,xSpeed),y:addBounded(current.y,ySpeed)}}
 function getRandomDirection(){var moves=(direction===LEFT||direction===RIGHT)?[UP,DOWN]:[LEFT,RIGHT];return moves[Math.floor(Math.random()*2)]}
 function reset(){eaten=null;eatable=null;position={x:90,y:80};direction=getRandomDirection();due=getRandomDirection()}
 function onWholeSquare(x){return x%10===0}
 function oppositeDirection(dir){return dir===LEFT&&RIGHT||dir===RIGHT&&LEFT||dir===UP&&DOWN||UP}
 function makeEatable(){direction=oppositeDirection(direction);eatable=game.getTick()}
 function eat(){eatable=null;eaten=game.getTick()}
 function pointToCoord(x){return Math.round(x/10)}
 function nextSquare(x,dir){var rem=x%10;if(rem===0)return x;else if(dir===RIGHT||dir===DOWN)return x+(10-rem);return x-rem}
 function onGridSquare(pos){return onWholeSquare(pos.y)&&onWholeSquare(pos.x)}
 function secondsAgo(tick){return(game.getTick()-tick)/FPS}
 function getColour(){if(eatable){if(secondsAgo(eatable)>5)return game.getTick()%20>10?"#FFFFFF":"#0000BB";return"#0000BB"}else if(eaten)return"#222";return colour}
 function draw(ctx){
  var s=map.blockSize,top=(position.y/10)*s,left=(position.x/10)*s;
  if(eatable&&secondsAgo(eatable)>8)eatable=null;
  if(eaten&&secondsAgo(eaten)>3)eaten=null;
  var tl=left+s,base=top+s-3,inc=s/10,high=game.getTick()%10>5?3:-3,low=game.getTick()%10>5?-3:3;
  ctx.fillStyle=getColour();ctx.beginPath();ctx.moveTo(left,base);
  ctx.quadraticCurveTo(left,top,left+(s/2),top);ctx.quadraticCurveTo(left+s,top,left+s,base);
  ctx.quadraticCurveTo(tl-(inc*1),base+high,tl-(inc*2),base);ctx.quadraticCurveTo(tl-(inc*3),base+low,tl-(inc*4),base);
  ctx.quadraticCurveTo(tl-(inc*5),base+high,tl-(inc*6),base);ctx.quadraticCurveTo(tl-(inc*7),base+low,tl-(inc*8),base);
  ctx.quadraticCurveTo(tl-(inc*9),base+high,tl-(inc*10),base);ctx.closePath();ctx.fill();
  ctx.beginPath();ctx.fillStyle="#FFF";ctx.arc(left+6,top+6,s/6,0,300,false);ctx.arc((left+s)-6,top+6,s/6,0,300,false);ctx.closePath();ctx.fill();
  var f=s/12,off={};off[RIGHT]=[f,0];off[LEFT]=[-f,0];off[UP]=[0,-f];off[DOWN]=[0,f];
  ctx.beginPath();ctx.fillStyle="#000";ctx.arc(left+6+off[direction][0],top+6+off[direction][1],s/15,0,300,false);ctx.arc((left+s)-6+off[direction][0],top+6+off[direction][1],s/15,0,300,false);ctx.closePath();ctx.fill()}
 function pane(pos){if(pos.y===100&&pos.x>=190&&direction===RIGHT)return{y:100,x:-10};if(pos.y===100&&pos.x<=-10&&direction===LEFT)return position={y:100,x:190};return false}
 function move(){
  var oldPos=position,onGrid=onGridSquare(position),npos=null;
  if(due!==direction){npos=getNewCoord(due,position);if(onGrid&&map.isFloorSpace({y:pointToCoord(nextSquare(npos.y,due)),x:pointToCoord(nextSquare(npos.x,due))}))direction=due;else npos=null}
  if(npos===null)npos=getNewCoord(direction,position);
  if(onGrid&&map.isWallSpace({y:pointToCoord(nextSquare(npos.y,direction)),x:pointToCoord(nextSquare(npos.x,direction))})){due=getRandomDirection();return move()}
  position=npos;var tmp=pane(position);if(tmp)position=tmp;due=getRandomDirection();return{"new":position,"old":oldPos}}
 return{eat:eat,isVunerable:isVunerable,isDangerous:isDangerous,makeEatable:makeEatable,reset:reset,move:move,draw:draw}}

function User(game,map){
 var position=null,direction=null,eaten=null,due=null,lives=null,score=5;
 function addScore(n){score+=n;if(score>=10000&&score-n<10000)lives+=1}
 function theScore(){return score}
 function loseLife(){lives-=1}
 function getLives(){return lives}
 function resetPosition(){position={x:90,y:120};direction=LEFT;due=LEFT}
 function newLevel(){resetPosition();eaten=0}
 function initUser(){score=0;lives=3;newLevel()}
 function reset(){initUser();resetPosition()}
 function setDue(d){due=d}
 function getNewCoord(dir,cur){return{x:cur.x+(dir===LEFT&&-2||dir===RIGHT&&2||0),y:cur.y+(dir===DOWN&&2||dir===UP&&-2||0)}}
 function onWholeSquare(x){return x%10===0}
 function pointToCoord(x){return Math.round(x/10)}
 function nextSquare(x,dir){var rem=x%10;if(rem===0)return x;else if(dir===RIGHT||dir===DOWN)return x+(10-rem);return x-rem}
 function next(pos,dir){return{y:pointToCoord(nextSquare(pos.y,dir)),x:pointToCoord(nextSquare(pos.x,dir))}}
 function onGridSquare(pos){return onWholeSquare(pos.y)&&onWholeSquare(pos.x)}
 function isOnSamePlane(d,dir){return((d===LEFT||d===RIGHT)&&(dir===LEFT||dir===RIGHT))||((d===UP||d===DOWN)&&(dir===UP||dir===DOWN))}
 function move(){
  var npos=null,nextWhole=null,oldPosition=position,block=null;
  if(due!==direction){npos=getNewCoord(due,position);if(isOnSamePlane(due,direction)||(onGridSquare(position)&&map.isFloorSpace(next(npos,due))))direction=due;else npos=null}
  if(npos===null)npos=getNewCoord(direction,position);
  if(onGridSquare(position)&&map.isWallSpace(next(npos,direction)))direction=NONE;
  if(direction===NONE)return{"new":position,"old":position};
  if(npos.y===100&&npos.x>=190&&direction===RIGHT)npos={y:100,x:-10};
  if(npos.y===100&&npos.x<=-12&&direction===LEFT)npos={y:100,x:190};
  position=npos;nextWhole=next(position,direction);block=map.block(nextWhole);
  if(block===BISCUIT||block===PILL){
   map.setBlock(nextWhole,EMPTY);addScore(block===BISCUIT?10:50);eaten+=1;
   if(eaten===182)game.completedLevel();
   if(block===PILL)game.eatenPill()}
  return{"new":position,"old":oldPosition}}
 function calcAngle(dir,pos){
  if(dir===RIGHT&&(pos.x%10<5))return{start:.25,end:1.75,direction:false};
  else if(dir===DOWN&&(pos.y%10<5))return{start:.75,end:2.25,direction:false};
  else if(dir===UP&&(pos.y%10<5))return{start:1.25,end:1.75,direction:true};
  else if(dir===LEFT&&(pos.x%10<5))return{start:.75,end:1.25,direction:true};
  return{start:0,end:2,direction:false}}
 function drawDead(ctx,amount){var size=map.blockSize,half=size/2;if(amount>=1)return;
  ctx.fillStyle="#FFFF00";ctx.beginPath();ctx.moveTo(((position.x/10)*size)+half,((position.y/10)*size)+half);
  ctx.arc(((position.x/10)*size)+half,((position.y/10)*size)+half,half,0,Math.PI*2*amount,true);ctx.fill()}
 function draw(ctx){var s=map.blockSize,angle=calcAngle(direction,position);ctx.fillStyle="#FFFF00";ctx.beginPath();
  ctx.moveTo(((position.x/10)*s)+s/2,((position.y/10)*s)+s/2);
  ctx.arc(((position.x/10)*s)+s/2,((position.y/10)*s)+s/2,s/2,Math.PI*angle.start,Math.PI*angle.end,angle.direction);ctx.fill()}
 initUser();
 return{draw:draw,drawDead:drawDead,loseLife:loseLife,getLives:getLives,addScore:addScore,theScore:theScore,setDue:setDue,move:move,newLevel:newLevel,reset:reset,resetPosition:resetPosition}}

function GameMap(size){
 var height=null,width=null,blockSize=size,pillSize=0,map=null;
 function withinBounds(y,x){return y>=0&&y<height&&x>=0&&x<width}
 function isWall(pos){return withinBounds(pos.y,pos.x)&&map[pos.y][pos.x]===WALL}
 function isFloorSpace(pos){if(!withinBounds(pos.y,pos.x))return false;var p=map[pos.y][pos.x];return p===EMPTY||p===BISCUIT||p===PILL}
 function drawWall(ctx){var i,j,p,line;ctx.strokeStyle="#0000FF";ctx.lineWidth=5;ctx.lineCap="round";
  for(i=0;i<WALLS.length;i+=1){line=WALLS[i];ctx.beginPath();
   for(j=0;j<line.length;j+=1){p=line[j];
    if(p.move)ctx.moveTo(p.move[0]*blockSize,p.move[1]*blockSize);
    else if(p.line)ctx.lineTo(p.line[0]*blockSize,p.line[1]*blockSize);
    else if(p.curve)ctx.quadraticCurveTo(p.curve[0]*blockSize,p.curve[1]*blockSize,p.curve[2]*blockSize,p.curve[3]*blockSize)}
   ctx.stroke()}}
 function reset(){map=cloneMap(MAP);height=map.length;width=map[0].length}
 function block(pos){return map[pos.y][pos.x]}
 function setBlock(pos,type){map[pos.y][pos.x]=type}
 function drawPills(ctx){var i,j;if(++pillSize>30)pillSize=0;
  for(i=0;i<height;i+=1)for(j=0;j<width;j+=1)if(map[i][j]===PILL){
   ctx.beginPath();ctx.fillStyle="#000";ctx.fillRect(j*blockSize,i*blockSize,blockSize,blockSize);
   ctx.fillStyle="#FFF";ctx.arc((j*blockSize)+blockSize/2,(i*blockSize)+blockSize/2,Math.abs(5-(pillSize/3)),0,Math.PI*2,false);ctx.fill();ctx.closePath()}}
 function drawBlock(y,x,ctx){if(y<0||y>=height||x<0||x>=width)return;var layout=map[y][x];if(layout===PILL)return;ctx.beginPath();
  if(layout===EMPTY||layout===BLOCK||layout===BISCUIT){ctx.fillStyle="#000";ctx.fillRect(x*blockSize,y*blockSize,blockSize,blockSize);
   if(layout===BISCUIT){ctx.fillStyle="#FFF";ctx.fillRect((x*blockSize)+(blockSize/2.5),(y*blockSize)+(blockSize/2.5),blockSize/6,blockSize/6)}}
  ctx.closePath()}
 function draw(ctx){var i,j;ctx.fillStyle="#000";ctx.fillRect(0,0,width*blockSize,height*blockSize);drawWall(ctx);
  for(i=0;i<height;i+=1)for(j=0;j<width;j+=1)drawBlock(i,j,ctx)}
 reset();
 return{draw:draw,drawBlock:drawBlock,drawPills:drawPills,block:block,setBlock:setBlock,reset:reset,isWallSpace:isWall,isFloorSpace:isFloorSpace,get height(){return height},get width(){return width},blockSize:blockSize}}

function createGame(wrapper){
 var W=wrapper.clientWidth||320,blockSize=W/19,dpr=window.devicePixelRatio||1,CW=blockSize*19,CH=blockSize*22+30,canvas=document.createElement('canvas');
 canvas.width=Math.round(CW*dpr);canvas.height=Math.round(CH*dpr);canvas.style.width=CW+'px';canvas.style.height=CH+'px';canvas.style.display='block';wrapper.appendChild(canvas);
 var ctx=canvas.getContext('2d');ctx.setTransform(dpr,0,0,dpr,0,0);
 var state=WAITING,ghosts=[],ghostSpecs=["#00FFDE","#FF0000","#FFB8DE","#FFB847"],eatenCount=0,level=0,tick=0,ghostPos=[],userPos=null,stateChanged=true,timerStart=null,lastTime=0,stored=null,timer=null,map=new GameMap(blockSize),user=null,i,len;
 function getTick(){return tick}
 function setState(n){state=n;stateChanged=true}
 function dialog(text){ctx.fillStyle="#FFFF00";ctx.font="bold 17px sans-serif";var w=ctx.measureText(text).width,x=((map.width*map.blockSize)-w)/2;ctx.fillText(text,x,(map.height*10)+8)}
 function drawScore(text,p){ctx.fillStyle="#FFFFFF";ctx.font="bold 12px sans-serif";ctx.fillText(text,(p["new"].x/10)*map.blockSize,((p["new"].y+5)/10)*map.blockSize)}
 function startLevel(){user.resetPosition();for(var g=0;g<ghosts.length;g+=1)ghosts[g].reset();timerStart=tick;setState(COUNTDOWN)}
 function startNewGame(){setState(WAITING);level=1;user.reset();map.reset();map.draw(ctx);startLevel()}
 function loseLife(){setState(WAITING);user.loseLife();if(user.getLives()>0)startLevel()}
 function collided(u,g){return(Math.sqrt(Math.pow(g.x-u.x,2)+Math.pow(g.y-u.y,2)))<10}
 function drawFooter(){var top=(map.height*map.blockSize),base=top+17;ctx.fillStyle="#000000";ctx.fillRect(0,top,(map.width*map.blockSize),30);
  for(var k=0,n=user.getLives();k<n;k++){ctx.fillStyle="#FFFF00";ctx.beginPath();ctx.moveTo(CW/2-30+(25*k)+map.blockSize/2,(top+1)+map.blockSize/2);ctx.arc(CW/2-30+(25*k)+map.blockSize/2,(top+1)+map.blockSize/2,map.blockSize/2,Math.PI*.25,Math.PI*1.75,false);ctx.fill()}
  ctx.fillStyle="#FFFF00";ctx.font="bold 14px sans-serif";ctx.fillText("Score: "+user.theScore(),10,base);ctx.fillText("Level: "+level,CW-70,base)}
 function redrawBlock(p){map.drawBlock(Math.floor(p.y/10),Math.floor(p.x/10),ctx);map.drawBlock(Math.ceil(p.y/10),Math.ceil(p.x/10),ctx)}
 function mainDraw(){var u,g,nScore;ghostPos=[];
  for(g=0;g<ghosts.length;g+=1)ghostPos.push(ghosts[g].move());
  u=user.move();
  for(g=0;g<ghosts.length;g+=1)redrawBlock(ghostPos[g].old);
  redrawBlock(u.old);
  for(g=0;g<ghosts.length;g+=1)ghosts[g].draw(ctx);
  user.draw(ctx);userPos=u["new"];
  for(g=0;g<ghosts.length;g+=1){if(collided(userPos,ghostPos[g]["new"])){
   if(ghosts[g].isVunerable()){ghosts[g].eat();eatenCount+=1;nScore=eatenCount*50;drawScore(nScore,ghostPos[g]);user.addScore(nScore);setState(EATEN_PAUSE);timerStart=tick}
   else if(ghosts[g].isDangerous()){setState(DYING);timerStart=tick}}}}
 function mainLoop(){var diff,g;if(document.hidden)return;if(state!==PAUSE)++tick;map.drawPills(ctx);
  if(state===PLAYING)mainDraw();
  else if(state===WAITING&&stateChanged){stateChanged=false;map.draw(ctx);dialog(user.getLives()>0?"Tap to play":"Game over. Tap to play")}
  else if(state===EATEN_PAUSE&&(tick-timerStart)>(FPS/3)){map.draw(ctx);setState(PLAYING)}
  else if(state===DYING){
   if(tick-timerStart>(FPS*2))loseLife();
   else{redrawBlock(userPos);for(g=0;g<ghosts.length;g+=1){redrawBlock(ghostPos[g].old);ghosts[g].draw(ctx)}user.drawDead(ctx,(tick-timerStart)/(FPS*2))}}
  else if(state===COUNTDOWN){diff=3+Math.floor((timerStart-tick)/FPS);
   if(diff===0){map.draw(ctx);setState(PLAYING)}
   else if(diff!==lastTime){lastTime=diff;map.draw(ctx);dialog("Starting in: "+diff)}}
  drawFooter()}
 var game={getTick:getTick,completedLevel:function(){setState(WAITING);level+=1;map.reset();user.newLevel();startLevel()},eatenPill:function(){timerStart=tick;eatenCount=0;for(var g=0;g<ghosts.length;g+=1)ghosts[g].makeEatable()}};
 user=new User(game,map);
 for(i=0,len=ghostSpecs.length;i<len;i+=1)ghosts.push(new Ghost({getTick:getTick},map,ghostSpecs[i]));
 map.draw(ctx);startNewGame();timer=window.setInterval(mainLoop,1000/FPS);
 return{tick:mainLoop,setDue:function(d){user.setDue(d)},
  newGame:function(){if(state===WAITING)startNewGame()},
  togglePause:function(){if(state===PAUSE){map.draw(ctx);setState(stored)}else if(state===PLAYING||state===COUNTDOWN){stored=state;setState(PAUSE);map.draw(ctx);dialog("Paused")}},
  info:function(){return{state:state,score:user.theScore(),lives:user.getLives(),level:level}},
  destroy:function(){window.clearInterval(timer)}}}

var live=null;
function closePacman(){if(!live)return;live.game.destroy();document.removeEventListener('keydown',live.kd,true);live.ov.remove();document.body.style.overflow=live.ovf;live=null}
function openPacman(){
 if(live)return;
 var ov=document.createElement('div');ov.id='pacOverlay';
 ov.innerHTML='<div class="pac-bar"><span>Pac-Man</span><button type="button" id="pacClose">Close</button></div><div class="pac-stage"><div id="pacWrap"></div></div><div class="pac-hint">Swipe anywhere to steer. Tap the board to play again.</div>';
 document.body.appendChild(ov);var ovf=document.body.style.overflow;document.body.style.overflow='hidden';
 var wrap=ov.querySelector('#pacWrap'),W=Math.max(200,Math.min(window.innerWidth-24,460,(window.innerHeight-150)*19/22));wrap.style.width=W+'px';
 var game=createGame(wrap),sx=0,sy=0,moved=false;
 ov.addEventListener('touchstart',function(e){var t=e.touches[0];sx=t.clientX;sy=t.clientY;moved=false},{passive:true});
 ov.addEventListener('touchmove',function(e){e.preventDefault();var t=e.touches[0],dx=t.clientX-sx,dy=t.clientY-sy;
  if(Math.max(Math.abs(dx),Math.abs(dy))>14){moved=true;game.setDue(Math.abs(dx)>Math.abs(dy)?(dx>0?RIGHT:LEFT):(dy>0?DOWN:UP));sx=t.clientX;sy=t.clientY}},{passive:false});
 ov.addEventListener('touchend',function(e){if(!moved&&e.target&&e.target.tagName==='CANVAS')game.newGame()});
 ov.addEventListener('click',function(e){if(e.target&&e.target.tagName==='CANVAS')game.newGame()});
 function kd(e){var k=(e.key||'').toLowerCase(),d=k==='arrowleft'||k==='a'?LEFT:k==='arrowright'||k==='d'?RIGHT:k==='arrowup'||k==='w'?UP:k==='arrowdown'||k==='s'?DOWN:0;
  if(d){game.setDue(d);e.preventDefault();e.stopPropagation()}else if(k==='p')game.togglePause();else if(k==='n')game.newGame();else if(k==='escape')closePacman()}
 document.addEventListener('keydown',kd,true);
 ov.querySelector('#pacClose').addEventListener('click',closePacman);
 live={ov:ov,game:game,kd:kd,ovf:ovf}}
window.openPacman=openPacman;window.closePacman=closePacman;
document.addEventListener('input',function(e){var t=e.target;
 if(!t||(t.id!=='mealSearch'&&t.id!=='msSearch'&&t.id!=='fv2Q'))return;
 if(String(t.value).toLowerCase().replace(/[^a-z]/g,'')==='pacman'){
  t.value='';
  if(t.id==='fv2Q'){
   var sh=fv2S().sh;clearTimeout(sh._t);sh.serial++;sh.q='';sh.list=[];
   fv2Close();e.stopPropagation();
  }
  if(t.blur)t.blur();openPacman()
 }},true);
})();

/* ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   LIFEMAX / GUIDED WORKOUT ENGINE
   Coordinates with the existing planToggle() and plan XP system.
   Each logged set goes into plan.checks[date].setDetails and the
   SAME checklist that the Plan tab already reads. No duplicate XP.
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ */
let gwrInterv