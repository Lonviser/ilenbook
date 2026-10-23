async(page)=>{
 const checks=[],widths=[];
 await page.waitForFunction(()=>state.books.length>0);
 await page.evaluate(()=>{
  window.updateUiOriginal={api,update:appUpdateState};window.updateUiCalls=[];
  window.updateUiFixture={currentVersion:'0.7.3',currentCode:10,phase:'available',busy:false,error:'',available:true,ready:false,bytes:0,total:40000000,lastChecked:Date.now(),autoCheck:true,autoDownloadWifi:false,release:{version:'0.7.4',size:40000000,notes:'Исправления <img src=x onerror="window.updateXss=true">'}};
  api=async(action,data={})=>{if(!action.startsWith('updates'))return updateUiOriginal.api(action,data);updateUiCalls.push({action,data});const s=updateUiFixture;
   if(action==='updatesConfigure')Object.assign(s,data);
   if(action==='updatesCheck'){s.phase='checking';s.busy=true;setTimeout(()=>{s.phase='available';s.busy=false;applyAppUpdateStatus({...s})},120);}
   if(action==='updatesDownload'){s.phase='downloading';s.busy=true;s.bytes=20000000;}
   if(action==='updatesCancel'){s.phase='available';s.busy=false;s.error='Скачивание отменено';}
   if(action==='updatesInstall')s.phase='ready';
   return JSON.parse(JSON.stringify(s));
  };
  closeSheet();if(state.reader)exitReader();navigate('settings');applyAppUpdateStatus({...updateUiFixture});
 });
 try{
  await page.locator('#app-updates-open').click();await page.evaluate(()=>applyAppUpdateStatus({...updateUiFixture}));
  if(!await page.locator('#app-update-download').isVisible()||await page.locator('#app-update-install').isVisible())throw Error('Wrong available actions');
  await page.locator('#app-update-auto').uncheck();await page.locator('#app-update-wifi').check();
  if(!await page.evaluate(()=>!updateUiFixture.autoCheck&&updateUiFixture.autoDownloadWifi))throw Error('Options were not saved');checks.push('Automatic check and Wi-Fi download are independent options');
  await page.locator('#app-update-check').click();await page.waitForFunction(()=>!appUpdateState.busy);
  await page.locator('#app-update-download').click();if(!await page.locator('#app-update-cancel').isVisible()||!await page.locator('#app-update-check').isDisabled())throw Error('No download progress controls');
  await page.locator('#app-update-cancel').click();if(!await page.locator('#app-update-error').textContent().then(t=>t.includes('отменено')))throw Error('Cancellation missing');checks.push('Manual check, progress and cancellation');
  await page.evaluate(()=>{Object.assign(updateUiFixture,{ready:true,busy:false,phase:'ready',error:'',downloadedVersion:'0.7.4'});applyAppUpdateStatus({...updateUiFixture})});
  if(!await page.locator('#app-update-install').isVisible()||await page.locator('#app-update-download').isVisible())throw Error('Wrong ready controls');await page.locator('#app-update-install').click();
  if(!await page.evaluate(()=>updateUiCalls.some(c=>c.action==='updatesInstall')))throw Error('Installer action not called');checks.push('Verified ready file exposes installation');
  if(await page.evaluate(()=>!!window.updateXss||!!document.querySelector('#app-update-notes img')))throw Error('Release notes executed HTML');checks.push('Release notes remain passive text');
  for(const width of [320,360,412,600]){await page.setViewportSize({width,height:800});const r=await page.evaluate(()=>({width:innerWidth,overflow:$('.sheet').scrollWidth>$('.sheet').clientWidth+1}));if(r.overflow)throw Error(JSON.stringify(r));widths.push(r)}checks.push('Update dialog fits 320/360/412/600');
  await page.evaluate(()=>{closeSheet();applyAppUpdateStatus({...updateUiFixture});});if(!await page.locator('#app-update-notice').isVisible())throw Error('No nonmodal notice');checks.push('Available update has a nonmodal notice');
  return {checks,widths};
 }finally{await page.evaluate(()=>{closeSheet();api=updateUiOriginal.api;appUpdateState=updateUiOriginal.update;delete window.updateUiFixture;delete window.updateUiCalls;delete window.updateUiOriginal;navigate('library');renderUpdateNotice()})}
}
