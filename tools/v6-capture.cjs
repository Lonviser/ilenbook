async(page)=>{
 const previous=await page.evaluate(()=>JSON.parse(JSON.stringify(prefs)));const files=[];
 try{
  await page.setViewportSize({width:412,height:915});await page.evaluate(()=>{closeSheet();if(state.reader)exitReader();prefs.settings.font='literata';prefs.settings.size=20;prefs.readerControls={};prefs.gesturesShown=true;delete meta('white-nights').readingOverrides;save()});await page.evaluate(()=>openReader('white-nights'));await page.waitForFunction(()=>state.reader&&!state.reader.restoring);await page.evaluate(()=>toggleReaderChrome(true));
  const capture=async name=>{const path='output/playwright/'+name+'.png';await page.screenshot({path});files.push(path)};
  await capture('v6-reader-412');await page.evaluate(()=>readingProfilesDialog());await capture('v6-profiles-412');await page.evaluate(()=>toolbarEditor());await capture('v6-toolbar-412');await page.evaluate(()=>tapZonesEditor());await capture('v6-zones-412');await page.setViewportSize({width:320,height:915});await capture('v6-zones-320');return files;
 }finally{await page.evaluate(p=>{closeSheet();if(state.reader)exitReader();prefs=p;save();state.tab='library';render()},previous)}
}
