/* Pixel-art icons for the Apps tab: 16x16 sprites drawn in code, shown scaled up with hard edges. */
const AppsPixelArt=(function(){
 const N=16,cache={};
 const grid=()=>Array.from({length:N},()=>Array(N).fill(null));
 const put=(g,x,y,c)=>{x=Math.round(x);y=Math.round(y);if(x>=0&&y>=0&&x<N&&y<N)g[y][x]=c};
 const shade=(hex,k)=>{const n=parseInt(hex.slice(1),16),f=v=>Math.max(0,Math.min(255,Math.round(v*k)));return '#'+[(n>>16)&255,(n>>8)&255,n&255].map(f).map(v=>v.toString(16).padStart(2,'0')).join('')};
 const each=f=>{for(let y=0;y<N;y++)for(let x=0;x<N;x++)f(x,y)};
 const dist=(x,y,cx,cy)=>Math.hypot(x-cx,y-cy);
 /* rounded tile with a lit top edge and shaded bottom edge */
 function tile(g,c){
  each((x,y)=>{
   const cx=Math.min(x,N-1-x),cy=Math.min(y,N-1-y);
   if(cx+cy<2)return;
   g[y][x]=y===0?shade(c,1.25):y===N-1?shade(c,.6):y>=N-3?shade(c,.82):c;
  });
 }
 function rows(g,pal,lines,ox,oy){lines.forEach((ln,y)=>{for(let x=0;x<ln.length;x++){const c=pal[ln[x]];if(c)put(g,x+(ox||0),y+(oy||0),c)}})}
 function disc(g,cx,cy,r,c){each((x,y)=>{if(dist(x,y,cx,cy)<=r)g[y][x]=c})}
 function ray(g,cx,cy,deg,r0,r1,w,c){
  const a=deg*Math.PI/180,dx=Math.cos(a),dy=Math.sin(a);
  each((x,y)=>{const px=x-cx,py=y-cy,t=px*dx+py*dy;if(t<r0||t>r1)return;if(Math.abs(-px*dy+py*dx)<=w)g[y][x]=c});
 }
 const ART={
  spotify:{label:'Spotify',draw(g){
   const G='#1ed760',K='#0b1d12';
   each((x,y)=>{if(dist(x,y,7.5,7.5)<=7.7)g[y][x]=y>=13?shade(G,.82):G});
   [[4,3,12],[7,4,11],[10,5,10]].forEach(([top,x0,x1])=>each((x,y)=>{
    if(x<x0||x>x1||y<top||y>top+2)return;
    if(Math.abs(dist(x,y,7.75,top+9.5)-9.5)<=0.8)put(g,x,y,K);
   }));
  }},
  firefox:{label:'Firefox',draw(g){
   const R=7.7;
   each((x,y)=>{
    const d=dist(x,y,7.5,7.5);if(d>R)return;
    const th=Math.atan2(y-7.5,x-7.5)*180/Math.PI;
    g[y][x]=d<4?'#8a5cff':d<6?'#6a3fd6':'#4a22a8';
    const sweep=(th>-35&&th<205)||(th>-120&&th<-60&&d>5);
    if(sweep&&d>3.2){g[y][x]=d>6.1?'#ff6a1a':d>4.6?'#ff9a1f':'#ffc83a'}
    if(th>200&&th<250&&d>6.2)g[y][x]='#ff6a1a';
   });
   put(g,12,2,'#ff6a1a');put(g,13,3,'#ff6a1a');put(g,13,2,'#ff9a1f');
  }},
  claude:{label:'Claude',draw(g){
   const C='#d97757',L='#fbf1e6';
   tile(g,C);
   for(let k=0;k<12;k++)ray(g,7.5,7.5,k*30+15,0,6.6,0.55,L);
   disc(g,7.5,7.5,1.3,L);
  }},
  settings:{label:'Settings',draw(g){
   tile(g,'#3a4558');
   const c='#cfd8e6';
   each((x,y)=>{
    const d=dist(x,y,7.5,7.5),th=Math.atan2(y-7.5,x-7.5)*180/Math.PI;
    const tooth=(((th%45)+45)%45<18);
    if(d<=4.9||(d<=6.4&&tooth))g[y][x]=d<=4.9&&d>=3.9?'#aab6c8':c;
    if(d<2.4)g[y][x]=null;
   });
   each((x,y)=>{if(g[y][x]===null&&dist(x,y,7.5,7.5)<2.4)g[y][x]='#3a4558'});
  }},
  chrome:{label:'Chrome',draw(g){
   each((x,y)=>{
    const d=dist(x,y,7.5,7.5);if(d>7.7)return;
    const th=(Math.atan2(y-7.5,x-7.5)*180/Math.PI+360+90)%360;
    g[y][x]=th<120?'#e8453c':th<240?'#fbbc04':'#34a853';
    if(d<=3.9)g[y][x]=d<=3?'#4285f4':'#ffffff';
   });
  }},
  youtube:{label:'YouTube',draw(g){
   const R='#ff1f1f';
   each((x,y)=>{if(y>=3&&y<=12&&x>=0&&x<=15&&!((x<1||x>14)&&(y<4||y>11)))g[y][x]=y>=11?shade(R,.8):R});
   rows(g,{w:'#ffffff'},['w','ww','www','wwww','www','ww','w'],6,4);
  }},
  snapchat:{label:'Snapchat',draw(g){
   tile(g,'#fffc00');
   rows(g,{o:'#2b2b2b',w:'#ffffff'},[
    '................','................','......oooo......','.....owwwwo.....','....owwwwwwo....','....owwwwwwo....','....owwwwwwo....',
    '...owwwwwwwwo...','..owwwwwwwwwwo..','...owwwwwwwwo...','...owwwwwwwwo...','...owwwwwwwwo...','..owwowwowwowo..','...oo.oo.oo.oo..'
   ]);
  }}
 };
 /* tiny 3x5 pixel font for letters on icons we have no logo for */
 const F={A:'010101111101101',B:'110101110101110',C:'011100100100011',D:'110101101101110',E:'111100110100111',F:'111100110100100',G:'011100101101011',H:'101101111101101',I:'111010010010111',J:'001001001101010',K:'101101110101101',L:'100100100100111',M:'101111111101101',N:'110101101101101',O:'010101101101010',P:'110101110100100',Q:'010101101111011',R:'110101110101101',S:'011100010001110',T:'111010010010010',U:'101101101101111',V:'101101101101010',W:'101101111111101',X:'101101010101101',Y:'101101010010010',Z:'111001010100111',0:'111101101101111',1:'010110010010111',2:'110001010100111',3:'110001010001110',4:'101101111001001',5:'111100110001110',6:'011100111101111',7:'111001010010010',8:'111101111101111',9:'111101111001110'};
 function hue(str){let h=0;for(let i=0;i<str.length;i++)h=(h*31+str.charCodeAt(i))>>>0;return h%360}
 function hsl(h,s,l){s/=100;l/=100;const k=n=>(n+h/30)%12,a=s*Math.min(l,1-l),f=n=>l-a*Math.max(-1,Math.min(k(n)-3,Math.min(9-k(n),1)));return '#'+[f(0),f(8),f(4)].map(v=>Math.round(v*255).toString(16).padStart(2,'0')).join('')}
 function letterTile(g,label,seed){
  tile(g,hsl(hue(seed||label),48,40));
  const ch=((label||'?').trim().charAt(0)||'?').toUpperCase(),bits=F[ch]||F.X;
  for(let i=0;i<15;i++)if(bits[i]==='1'){
   const gx=5+(i%3)*2,gy=3+Math.floor(i/3)*2;
   for(let dy=0;dy<2;dy++)for(let dx=0;dx<2;dx++){put(g,gx+dx+1,gy+dy+1,'rgba(0,0,0,.35)');}
  }
  for(let i=0;i<15;i++)if(bits[i]==='1'){
   const gx=5+(i%3)*2,gy=3+Math.floor(i/3)*2;
   for(let dy=0;dy<2;dy++)for(let dx=0;dx<2;dx++)put(g,gx+dx,gy+dy,'#ffffff');
  }
 }
 function toUrl(g){
  const c=document.createElement('canvas');c.width=c.height=N;const x=c.getContext('2d');
  each((px,py)=>{const v=g[py][px];if(v){x.fillStyle=v;x.fillRect(px,py,1,1)}});
  return c.toDataURL('image/png');
 }
 const MATCH=[
  ['spotify',/(^|\.)spotify|spoti/i],['firefox',/firefox|fenix/i],['claude',/anthropic\.claude|^claude/i],
  ['settings',/^com\.(android|samsung\.android)\.settings$|^settings$/i],['chrome',/^com\.android\.chrome$|^chrome$/i],
  ['youtube',/google\.android\.youtube$|^youtube$/i],['snapchat',/snapchat/i]
 ];
 function autoKey(pkg,label){for(const [k,re] of MATCH)if(re.test(pkg||'')||re.test(label||''))return k;return null}
 return {
  keys:()=>Object.keys(ART),
  name:k=>ART[k]&&ART[k].label,
  autoKey,
  /* key: a logo key, or null for the generic letter tile */
  url(key,label,seed){
   const ck=key?key:'L:'+((label||'?').trim().charAt(0).toUpperCase())+':'+hue(seed||label||'');
   if(cache[ck])return cache[ck];
   const g=grid();
   if(key&&ART[key])ART[key].draw(g);else letterTile(g,label,seed);
   return cache[ck]=toUrl(g);
  }
 };
})();
