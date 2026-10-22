async(page)=>{
 const previous=await page.evaluate(()=>JSON.parse(JSON.stringify(prefs))),files=[];
 try{
  await page.setViewportSize({width:412,height:915});await page.evaluate(()=>{closeSheet();if(state.reader)exitReader();state.globalSearch=null;state.source=null;state.tab='library';state.filter='all';state.query='';state.collection='';state.list=false;state.shelf='large';prefs.settings.eink=false;prefs.sources=DEFAULT_SOURCES.map(s=>({...s}));prefs.searchHistory=[];prefs.lastSearch='';meta('white-nights').lastRead=Date.now();meta('white-nights').status='reading';render()});await page.evaluate(()=>document.fonts.ready);
  const capture=async name=>{const path='output/playwright/'+name+'.png';await page.screenshot({path});files.push(path)};
  await capture('v7-library-color-412');await page.evaluate(()=>{prefs.settings.eink=true;render()});await capture('v7-library-eink-412');await page.evaluate(()=>navigate('catalog'));await capture('v7-discovery-412');await page.setViewportSize({width:320,height:915});await capture('v7-discovery-320');await page.evaluate(()=>catalogSourceManager());await capture('v7-source-manager-320');return files;
 }finally{await page.evaluate(p=>{closeSheet();prefs=p;state.globalSearch=null;state.source=null;state.tab='library';state.list=!!prefs.ui.list;state.shelf=prefs.ui.shelf||'large';save();render()},previous)}
}
