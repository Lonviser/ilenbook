async(page)=>{
 const reports=[];
 for(const width of [320,360,412,600]){
  await page.setViewportSize({width,height:740});
  await page.evaluate(async()=>{prefs.gesturesShown=true;if(!state.reader)await openReader(state.books.find(b=>b.chapters?.length>1||b.chapterCount>1).id);toggleReaderChrome(true);});
  await page.waitForTimeout(180);
  const result=await page.evaluate(()=>{const b=$('#toc'),label=[...b.childNodes].find(n=>n.nodeType===3),range=document.createRange();range.selectNode(label);return {width:innerWidth,lines:range.getClientRects().length,buttonWidth:b.clientWidth,textWidth:range.getBoundingClientRect().width,progress:$('#reading-percent').textContent,footerOverflow:$('#reader-menu-toggle').scrollWidth>$('#reader-menu-toggle').clientWidth,toolbarOverflow:$('.reader-menu').scrollWidth>$('.reader-menu').clientWidth}});
  if(result.lines!==1||result.textWidth>result.buttonWidth||result.toolbarOverflow||result.footerOverflow)throw Error(JSON.stringify(result));reports.push(result);
 }
 await page.setViewportSize({width:360,height:740});
 const progress=await page.evaluate(async()=>{const r=state.reader;loadChapter(Math.min(1,r.book.chapters.length-1),.5);await new Promise(resolve=>setTimeout(resolve,180));r.page=Math.floor((r.pages-1)/2);$('#article').style.transform=`translateX(${-r.page*r.stride}px)`;updateReaderProgress();const text=$('#reading-percent').textContent,m=meta(r.book.id),lengths=r.book.chapters.map(c=>c.text.length),expected=Math.round((lengths.slice(0,r.chapter).reduce((n,l)=>n+l,0)+lengths[r.chapter]*m.offset)/lengths.reduce((n,l)=>n+l,0)*100);if(!text.startsWith('Книга ≈ '+expected+'% · Глава '+Math.round(m.offset*100)+'%'))throw Error(text);return {text,expected,chapter:r.chapter+1}});
 await page.screenshot({path:'output/playwright/v8-reader-progress-360.png'});
 return {reports,progress};
}
