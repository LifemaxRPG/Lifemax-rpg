'use strict';
const {chromium}=require('playwright'),assert=require('assert/strict');
const file=require('./current-page')(process.argv[2]);
(async()=>{
 const browser=await chromium.launch({headless:true});
 try{
  const page=await browser.newPage();
  await page.goto('file://'+file,{timeout:120000});
  const result=await page.evaluate(()=>{
   const button=document.getElementById('whatsNewToggle'),panel=document.getElementById('updateHistory');
   const getCard=()=>NativeSettingsTabProjection().cards.find(c=>c.title==='App updates');
   const before=getCard();const initiallyHidden=panel.hidden;
   button.click();const opened=getCard();const text=panel.textContent;const expanded=button.getAttribute('aria-expanded');
   button.click();const closed=getCard();
   return {initiallyHidden,expanded,text,closed:panel.hidden,
    button:before.rows.flatMap(r=>r.controls||[]).find(c=>c.id==='updateHistory'),
    beforeRows:before.rows.length,openRows:opened.rows.length,closedRows:closed.rows.length};
  });
  assert(result.initiallyHidden&&result.closed);assert.equal(result.expanded,'true');
  assert(result.text.includes('Life Director'));assert(!result.text.includes('@@UPDATE_HISTORY@@'));
  assert.equal(result.button.op,'press');
  assert(result.openRows>result.beforeRows,'native Settings must project the expanded history');
  assert.equal(result.closedRows,result.beforeRows);
  console.log('PASS offline update history: real button toggle, accessibility state, native button binding and expanded projection');
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)});
