/* Last segment: reaching this line means every segment before it ran. A web build that was downloaded over the air counts
   as on trial until it reports here; if it never does (a broken build), Android drops it and goes back to the bundled one.
   Keep this the final segment: add new ones before it. */
window.__lmaBooted=true;
setTimeout(function(){
 try{const L=typeof lmaPlugin==='function'&&lmaNative()?lmaPlugin('LiveWeb'):null;if(L&&window.__lmaBooted&&typeof navigate==='function')L.healthy()}catch(e){}
},6000);
