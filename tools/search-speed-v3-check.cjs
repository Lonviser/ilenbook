async(page)=>{return await page.evaluate(async()=>{
 closeSheet();if(state.reader)exitReader();state.tab='catalog';state.globalSearch=null;state.source=null;render();
 let first=null;const start=performance.now();const timer=setInterval(()=>{if(first===null&&state.globalSearch?.visible?.length)first=performance.now()-start},10);await startGlobalSearch('Каштанка','books');clearInterval(timer);const initial=performance.now()-start;
 const again=performance.now();await startGlobalSearch('Каштанка','books');const repeat=performance.now()-again;const cached=state.globalSearch.batches.filter(b=>b.cached).length;
 if(!state.globalSearch.visible.length||cached<4||repeat>3000)throw Error(JSON.stringify({repeat,cached,count:state.globalSearch.visible.length}));return {firstResultsMs:Math.round(first||initial),initialCompleteMs:Math.round(initial),repeatMs:Math.round(repeat),cachedCatalogs:cached,totalCatalogs:state.globalSearch.batches.length,results:state.globalSearch.visible.length};
})}
