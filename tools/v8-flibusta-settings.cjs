async(page)=>{
 const previous=await page.evaluate(()=>JSON.parse(JSON.stringify(prefs))),report=[];
 try{
  await page.evaluate(async()=>{closeSheet();if(state.reader)exitReader();await api('flibustaConfigure',{mode:'auto'});navigate('catalog')});
  await page.locator('#flibusta-connection').click();await page.waitForSelector('#flibusta-mode');
  if(await page.locator('#flibusta-mode').inputValue()!=='auto')throw Error('Wrong selected mode');report.push('Connection settings available from search');
  await page.locator('#flibusta-mode').selectOption('direct');await page.locator('#flibusta-save').click();
  if(await page.evaluate(async()=>(await api('flibustaStatus')).mode)!=='direct')throw Error('Mode not saved');report.push('Connection mode saved natively');
  await page.locator('#flibusta-connection').click();await page.locator('#flibusta-test').click();
  await page.waitForFunction(()=>document.querySelector('#flibusta-connection-status')?.textContent.includes('Флибуста отвечает'),{timeout:30000});report.push('Live catalog test reports real success');
  await page.screenshot({path:'output/playwright/v8-flibusta-connection-device.png'});return report;
 }finally{await page.evaluate(async p=>{closeSheet();await api('flibustaConfigure',{mode:'auto'});prefs=p;save();state.source=null;state.globalSearch=null;navigate('library')},previous)}
}
