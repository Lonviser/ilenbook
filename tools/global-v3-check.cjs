async(page)=>{
 const original=await page.evaluate(()=>prefs.sources);const base='http://10.0.2.2:4191';
 try{
  await page.evaluate(base=>{closeSheet();if(state.reader)exitReader();prefs.sources=[{name:'Первый',url:base+'/one',search:base+'/one?q={searchTerms}'},{name:'Дубликат',url:base+'/two',search:base+'/two?q={searchTerms}'},{name:'Медленный',url:base+'/slow',search:base+'/slow?q={searchTerms}'},{name:'Недоступный',url:base+'/fail',search:base+'/fail?q={searchTerms}'}];state.tab='catalog';state.globalSearch=null;state.source=null;render()},base);
  await page.locator('#global-query').fill('книга');await page.locator('#global-query').press('Enter');
  await page.waitForFunction(()=>state.globalSearch?.visible?.length>0&&state.globalSearch.batches.some(b=>b.status==='loading'));
  const partial=await page.locator('[data-global-entry]').count();await page.waitForFunction(()=>state.globalSearch.batches.every(b=>b.status!=='loading'),{},{timeout:15000});
  const result=await page.evaluate(()=>({count:state.globalSearch.visible.length,sources:state.globalSearch.visible[0].sourceNames,errors:state.globalSearch.batches.filter(b=>b.status==='error').length}));if(result.count!==2||result.errors!==1||result.sources.length!==3)throw Error(JSON.stringify(result));
  await page.locator('[data-global-entry]').first().scrollIntoViewIfNeeded();await page.locator('[data-global-entry]').first().locator('img').waitFor();const image=await page.locator('[data-global-entry]').first().locator('img').getAttribute('src');if(!image.startsWith('data:image/jpeg'))throw Error('Cover not loaded');
  await page.locator('[data-global-entry]').first().click();if(!(await page.locator('[role=dialog]').innerText()).includes('Первый'))throw Error('Source lost in detail');await page.locator('#close-sheet').click();await page.locator('#global-filter').selectOption('3');if(await page.locator('[data-global-entry]').count()!==0)throw Error('Source filter failed');
  return {partialResults:partial,...result,coverLoaded:true,sourceDetail:true,filter:true};
 }finally{await page.evaluate(sources=>{prefs.sources=sources;state.globalSearch=null;state.source=null;renderCatalog()},original)}
}
