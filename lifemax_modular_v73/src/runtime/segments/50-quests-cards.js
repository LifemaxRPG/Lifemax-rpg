/* ===== Fate Deck card art: a pixel font, pixel sprites and a small spooky scene per card =====
   Everything is drawn from tables and code (no image files), so it costs almost nothing to ship. */
var QPX_FONT={
'A':[14,17,17,31,17,17,17],'B':[30,17,17,30,17,17,30],'C':[14,17,16,16,16,17,14],'D':[30,17,17,17,17,17,30],'E':[31,16,16,30,16,16,31],'F':[31,16,16,30,16,16,16],
'G':[14,17,16,23,17,17,15],'H':[17,17,17,31,17,17,17],'I':[14,4,4,4,4,4,14],'J':[7,2,2,2,2,18,12],'K':[17,18,20,24,20,18,17],'L':[16,16,16,16,16,16,31],
'M':[17,27,21,21,17,17,17],'N':[17,17,25,21,19,17,17],'O':[14,17,17,17,17,17,14],'P':[30,17,17,30,16,16,16],'Q':[14,17,17,17,21,18,13],'R':[30,17,17,30,20,18,17],
'S':[15,16,16,14,1,1,30],'T':[31,4,4,4,4,4,4],'U':[17,17,17,17,17,17,14],'V':[17,17,17,17,17,10,4],'W':[17,17,17,21,21,21,10],'X':[17,17,10,4,10,17,17],
'Y':[17,17,17,10,4,4,4],'Z':[31,1,2,4,8,16,31],
'0':[14,17,19,21,25,17,14],'1':[4,12,4,4,4,4,14],'2':[14,17,1,2,4,8,31],'3':[31,2,4,2,1,17,14],'4':[2,6,10,18,31,2,2],'5':[31,16,30,1,1,17,14],
'6':[6,8,16,30,17,17,14],'7':[31,1,2,4,8,8,8],'8':[14,17,17,14,17,17,14],'9':[14,17,17,15,1,2,12],
'.':[0,0,0,0,0,12,12],',':[0,0,0,0,12,4,8],'!':[4,4,4,4,4,0,4],'?':[14,17,1,2,4,0,4],'-':[0,0,0,31,0,0,0],"'":[4,4,8,0,0,0,0],':':[0,12,12,0,12,12,0],
'+':[0,4,4,31,4,4,0],'&':[12,18,20,8,21,18,13],'/':[1,1,2,4,8,16,16],'(':[2,4,8,8,8,4,2],')':[8,4,2,2,2,4,8],'%':[25,25,2,4,8,19,19],'*':[0,21,14,31,14,21,0]
};
function qpxClean(s){return String(s==null?'':s).replace(/[‘’]/g,"'").replace(/[–—]/g,'-').toUpperCase()}
function qpxWidth(str){let w=0;for(let i=0;i<str.length;i++)w+=str[i]===' '?3:6;return Math.max(0,w-1)}
function qpxWrap(str,maxCols,maxLines){
 const words=qpxClean(str).split(/\s+/).filter(Boolean),lines=[];let cur='';
 words.forEach(function(w){const t=cur?cur+' '+w:w;if(qpxWidth(t)<=maxCols||!cur)cur=t;else{lines.push(cur);cur=w}});
 if(cur)lines.push(cur);
 if(maxLines&&lines.length>maxLines){const keep=lines.slice(0,maxLines);let l=keep[maxLines-1];while(qpxWidth(l+'.')>maxCols&&l.length>1)l=l.slice(0,-1);keep[maxLines-1]=l.replace(/\s+$/,'')+'.';return keep}
 return lines;
}
function qpxLine(text,s){
 let x=0,r='';
 for(let i=0;i<text.length;i++){
  const ch=text[i];if(ch===' '){x+=3;continue}
  const g=QPX_FONT[ch]||QPX_FONT['?'];
  for(let y=0;y<7;y++){const row=g[y];let c=0;while(c<5){if(row&(16>>c)){let e=c;while(e<5&&(row&(16>>e)))e++;r+='<rect x="'+(x+c)+'" y="'+y+'" width="'+(e-c)+'" height="1"/>';c=e}else c++}}
  x+=6;
 }
 const w=Math.max(1,x-1);
 return '<svg class="qpx" width="'+w*s+'" height="'+7*s+'" viewBox="0 0 '+w+' 7" fill="currentColor" shape-rendering="crispEdges" aria-hidden="true">'+r+'</svg>';
}
/* pixel text that wraps by words; real text kept in aria-label for screen readers */
function qpxText(text,o){
 o=o||{};const s=o.s||2,maxCols=Math.floor((o.w||240)/s),lines=qpxWrap(text,maxCols,o.lines||3);
 return '<span class="qpxt" role="img" aria-label="'+esc(String(text))+'" style="--ql:'+Math.round(s*9)+'px">'+lines.map(function(l){return qpxLine(l,s)}).join('')+'</span>';
}
/* sprites: '.' clear, other letters are colours from the palette */
var QPX_SPR={
 skull:['.XXXXXXX.','XXXXXXXXX','XooXXXooX','XooXXXooX','XXXXoXXXX','.XXXXXXX.','..XXXXX..','..XoXoX..','..XXXXX..'],
 bat:['X.........X','XX..XXX..XX','XXXXXXXXXXX','.XX.XXX.XX.','..X.....X..'],
 eye:['..XXXXXXX..','.XXXIIIXXX.','XXXIIoIIXXX','.XXXIIIXXX.','..XXXXXXX..'],
 moon:['...XXXX..','..XXX....','.XXX.....','.XXX.....','.XXX.....','.XXX.....','..XXX....','...XXXX..'],
 candle:['.F.','FYF','.F.','CCC','CCC','CCC','CCC','BBB'],
 brace:['XXXXXX.','X......','X.XX...','X.X....','X.X....','X......'],
 thorn:['..X....X....X..','.XXX..XXX..XXX.','XXXXXXXXXXXXXXX']
};
function qpxSprite(name,u,pal,cls){
 const rows=QPX_SPR[name],w=rows[0].length;let r='';
 rows.forEach(function(row,y){let x=0;while(x<row.length){const c=row[x];if(c==='.'){x++;continue}let e=x;while(e<row.length&&row[e]===c)e++;r+='<rect x="'+x+'" y="'+y+'" width="'+(e-x)+'" height="1" fill="'+((pal&&pal[c])||'currentColor')+'"/>';x=e}});
 return '<svg class="qspr'+(cls?' '+cls:'')+'" width="'+w*u+'" height="'+rows.length*u+'" viewBox="0 0 '+w+' '+rows.length+'" shape-rendering="crispEdges" aria-hidden="true">'+r+'</svg>';
}
var QPX_ARC={
 physique:{name:'The Colossus',ac:'#e0524a'},endurance:{name:'The Wanderer',ac:'#ff9a3d'},intelligence:{name:'The Oracle',ac:'#4fc3f7'},business:{name:'The Merchant',ac:'#f2c14e'},
 social:{name:'The Bard',ac:'#f06ba6'},discipline:{name:'The Monk',ac:'#9fb4c8'},adventure:{name:'The Fool',ac:'#6fcf7a'},wellbeing:{name:'The Hermit',ac:'#b79cff'}
};
var QPX_TIER=['','MINOR ARCANA','RARE ARCANA','MAJOR ARCANA'];
function qpxRgb(h){const n=parseInt(h.slice(1),16);return [(n>>16)&255,(n>>8)&255,n&255].join(',')}
/* the scene inside the arch: stars, moon, sprites by rarity, candles and thorns. Seeded by the quest so a card always looks the same. */
function qcardScene(q){
 const arc=QPX_ARC[q.attribute]||QPX_ARC.discipline,tier=q.tier||1;
 let h=2166136261;const key=String(q.tpl||q.id);for(let i=0;i<key.length;i++){h^=key.charCodeAt(i);h=Math.imul(h,16777619)}
 const rnd=function(){h=Math.imul(h^(h>>>15),2246822507);h^=h>>>13;return((h>>>0)%10000)/10000};
 let stars='';const ns=tier===3?34:24;for(let i=0;i<ns;i++){const x=Math.floor(rnd()*88)*2,y=Math.floor(rnd()*54)*2,big=rnd()>.86;stars+='<rect x="'+x+'" y="'+y+'" width="'+(big?3:2)+'" height="'+(big?3:2)+'" fill="#fff" opacity="'+(.25+rnd()*.6).toFixed(2)+'"/>'}
 const bats=tier>=2?'<g class="qbat" style="transform:translate(112px,16px)">'+qpxSprite('bat',2,{X:'#0b0716'})+'</g><g class="qbat b2" style="transform:translate(20px,48px)">'+qpxSprite('bat',1.4,{X:'#0b0716'})+'</g>':'';
 const sk=tier===3?'<g style="transform:translate(70px,8px)">'+qpxSprite('skull',4,{X:'#efe6d2',o:'#0b0716'})+'</g>':'';
 const eye=tier===2?'<g style="transform:translate(63px,10px)">'+qpxSprite('eye',4,{X:'#efe6d2',I:arc.ac,o:'#0b0716'})+'</g>':'';
 const moon=tier<3?'<g style="transform:translate(10px,10px)">'+qpxSprite('moon',4,{X:'#efe6d2'})+'</g>':'<g style="transform:translate(10px,10px)">'+qpxSprite('moon',4,{X:'#ffe29a'})+'</g>';
 let thorns='';for(let i=0;i<4;i++)thorns+='<g style="transform:translate('+(i*45-4)+'px,126px)">'+qpxSprite('thorn',3,{X:'#05030c'})+'</g>';
 return '<svg class="qscene" viewBox="0 0 180 150" preserveAspectRatio="xMidYMid slice" shape-rendering="crispEdges" aria-hidden="true">'+stars+moon+sk+eye+bats+thorns+'</svg>';
}
function qcardArt(q){
 const arc=QPX_ARC[q.attribute]||QPX_ARC.discipline,A=ATTRS.find(function(a){return a.id===q.attribute})||ATTRS[0];
 return '<div class="qart">'+qcardScene(q)+'<span class="qglow"></span><span class="qcandle l">'+qpxSprite('candle',3,{F:'#ff7a2a',Y:'#ffd35c',C:'#efe6d2',B:'#6b4a2c'},'qfl')+'</span><span class="qcandle r">'+qpxSprite('candle',3,{F:'#ff7a2a',Y:'#ffd35c',C:'#efe6d2',B:'#6b4a2c'},'qfl')+'</span><span class="qicon">'+px(A.icon,56)+'</span></div>';
}
function qcardCorners(){const s=qpxSprite('brace',2);return '<span class="qcor tl">'+s+'</span><span class="qcor tr">'+s+'</span><span class="qcor bl">'+s+'</span><span class="qcor br">'+s+'</span>'}
function qcardBack(slot){
 const u=typeof qartUrl==='function'&&qartUrl('back');
 if(u)return '<div class="qback art"><img class="qimg" src="'+u+'" alt="" draggable="false"><span class="qback-tag pill">'+qpxText('TAP TO TURN OVER',{s:1,w:200,lines:1})+'</span></div>';
 return '<div class="qback">'+qcardCorners()+'<span class="qback-num">'+qpxText(['I','II','III'][slot]||'I',{s:5,w:80})+'</span><span class="qback-eye">'+qpxSprite('eye',9,{X:'#cdb8ff',I:'#8a5cff',o:'#120d26'})+'</span><span class="qback-tag">'+qpxText('TAP TO TURN OVER',{s:1,w:200,lines:1})+'</span></div>'}

/* ===== painted card faces: the artwork carries the frame, the parchment panel carries the words ===== */
var QART_PANEL={physique:[69.6,10.4],endurance:[61.6,8.4],intelligence:[69.2,10.4],business:[70.4,10.4],social:[70.2,10.4],adventure:[69.2,10.4],wellbeing:[68.9,10.4]};
function qartHas(attr){return !!(QART_PANEL[attr]&&typeof qartUrl==='function'&&qartUrl(attr))}
function qartFace(q,ctx){
 const A=ATTRS.find(function(a){return a.id===q.attribute})||ATTRS[0],arc=QPX_ARC[q.attribute]||QPX_ARC.discipline,pn=QART_PANEL[q.attribute],tier=q.tier||1;
 let pips='';for(let i=0;i<3;i++)pips+='<i class="'+(i<tier?'on':'')+'"></i>';
 return '<div class="qimgwrap"><img class="qimg" src="'+qartUrl(q.attribute)+'" alt="" draggable="false">'
  +'<div class="qtxt" style="top:'+pn[0]+'%;bottom:'+pn[1]+'%"><div class="qtl"><span class="qpips" title="Difficulty">'+pips+'</span>'+qpxText(arc.name,{s:1,w:150,lines:1})+'<span class="qslot">'+qpxText(ctx.numeral,{s:1,w:40,lines:1})+'</span></div>'
  +'<h4 class="dh-name ink">'+qpxText(q.name,{s:2,w:222,lines:2})+'<span class="sr">'+esc(q.name)+'</span></h4><small class="dh-desc ink">'+esc(q.desc||'')+'</small></div>'
  +(ctx.done?'<span class="qstamp">'+qpxText('FULFILLED',{s:3,w:240,lines:1})+'</span>':'')+'</div>';
}
