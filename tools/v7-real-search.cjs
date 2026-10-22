async(page)=>await page.evaluate(async()=>{
 const oldUpdate=updateGlobalResults;let first=null;const start=performance.now();
 try{
  closeSheet();if(state.reader)exitReader();updateGlobalResults=function(){oldUpdate();if(first===null&&state.globalSearch?.visible.some(isBookEntry))first=Math.round(performance.now()-start)};
  await startGlobalSearch('Шинель','all');const g=state.globalSearch;
  return {query:g.query,firstMs:first,completeMs:Math.round(performance.now()-start),shown:g.visible.length,books:g.visible.filter(isBookEntry).length,sources:g.batches.map(b=>({name:b.source.name,status:b.status,entries:b.entries.length,cached:b.cached||false,stale:b.stale||false,error:b.error||null}))};
 }finally{updateGlobalResults=oldUpdate}
})
