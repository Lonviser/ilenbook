async(page)=>await page.evaluate(async()=>{
 const savedSources=prefs.sources,originalUpdate=updateGlobalResults,results=[];
 const source=savedSources.find(s=>new URL(s.url).hostname==='flibusta.is');if(!source)throw Error('Flibusta source missing');
 const query='Толстый и тонкий',modes=['books','authors'];
 try{
  closeSheet();if(state.reader)exitReader();prefs.sources=[source];
  const cacheBefore=[];for(const kind of modes){const u=new URL(buildSearchUrl(source.search,query));u.searchParams.set('searchType',kind);const cached=await api('catalogCache',{url:u.toString()});cacheBefore.push({kind,exists:!!cached,stale:cached?.stale,age:cached?.cacheAgeMs})}
  for(const pass of ['first','repeat']){const start=performance.now();let firstBook=null;updateGlobalResults=function(){originalUpdate();if(firstBook===null&&state.globalSearch?.visible?.some(b=>b.downloads.length))firstBook=Math.round(performance.now()-start)};await startGlobalSearch(query,'all');const g=state.globalSearch;results.push({pass,query,firstBookMs:firstBook,completeMs:Math.round(performance.now()-start),books:g.visible.filter(b=>b.downloads.length).length,authors:g.visible.filter(b=>!b.downloads.length).length,cached:g.batches[0].cached,stale:g.batches[0].stale,error:g.batches[0].error||null});if(!g.visible.some(b=>b.downloads.length))throw Error('No book results: '+JSON.stringify(results));}
  return {cacheBefore,results};
 }finally{updateGlobalResults=originalUpdate;if(state.globalSearch)cancelGlobalSearch(state.globalSearch);prefs.sources=savedSources;state.globalSearch=null;state.source=null;state.catalog=null;state.tab='library';save();render()}
})
