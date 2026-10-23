'use strict';
let appUpdateState=null,updateDialogOpen=false,updatePoll=null;
function updateSize(bytes){return (bytes/1048576).toFixed(1)+' МБ'}
function updateSummary(s){
 if(!s)return 'Проверка новых версий на GitHub';
 if(s.phase==='checking')return 'Проверяем новую версию…';
 if(s.phase==='downloading')return `Скачиваем ${s.release?.version||''} · ${s.total?Math.min(100,Math.floor(s.bytes/s.total*100)):0}%`;
 if(s.phase==='verifying')return 'Проверяем APK перед установкой…';
 if(s.ready)return 'Версия '+s.downloadedVersion+' готова к установке';
 if(s.available)return 'Доступна версия '+s.release.version;
 if(s.phase==='current')return 'Установлена актуальная версия';
 return s.error||'Проверка новых версий на GitHub';
}
function applyAppUpdateStatus(s){
 appUpdateState=s;const text=$('#app-updates-summary');if(text)text.textContent=updateSummary(s);
 if(updateDialogOpen&&$('#app-update-status'))paintUpdateDialog();
 renderUpdateNotice();
}
function renderUpdateNotice(){
 const old=$('#app-update-notice');if(!appUpdateState?.available&&!appUpdateState?.ready){old?.remove();return}
 if(old){old.textContent=updateSummary(appUpdateState);return}
 const top=$('.top');if(!top)return;const button=document.createElement('button');button.id='app-update-notice';button.className='text-button app-update-notice';button.textContent=updateSummary(appUpdateState);button.onclick=appUpdatesDialog;top.after(button);
}
function paintUpdateDialog(){
 const s=appUpdateState;if(!s||!$('#app-update-status'))return;
 $('#app-update-status').textContent=updateSummary(s);$('#app-update-error').textContent=s.error||'';$('#app-update-current').textContent='Установлена версия '+s.currentVersion;
 $('#app-update-latest').textContent=s.release?'Последний релиз: '+s.release.version+' · '+updateSize(s.release.size):'Источник: GitHub · Lonviser/ilenbook';
 $('#app-update-checked').textContent=s.lastChecked?'Проверено: '+new Date(s.lastChecked).toLocaleString('ru'):'Успешной проверки ещё не было';
 $('#app-update-check').disabled=!!s.busy;$('#app-update-download').hidden=!s.available||s.ready;$('#app-update-download').disabled=!!s.busy;
 $('#app-update-install').hidden=!s.ready;$('#app-update-install').disabled=!!s.busy;$('#app-update-cancel').hidden=s.phase!=='downloading';
 $('#app-update-auto').checked=!!s.autoCheck;$('#app-update-wifi').checked=!!s.autoDownloadWifi;
 $('#app-update-progress').hidden=s.phase!=='downloading';$('#app-update-progress').max=s.total||1;$('#app-update-progress').value=s.bytes||0;
 $('#app-update-notes').textContent=s.release?.notes||'Описание появится после проверки.';
 clearTimeout(updatePoll);if(s.busy)updatePoll=setTimeout(()=>refreshAppUpdateStatus(),700);
}
async function refreshAppUpdateStatus(){try{applyAppUpdateStatus(await api('updatesStatus'))}catch(e){if($('#app-update-error'))$('#app-update-error').textContent=e.message}}
async function updateAction(action,data={}){try{if(action==='updatesInstall')persistReading();applyAppUpdateStatus(await api(action,data))}catch(e){if($('#app-update-error'))$('#app-update-error').textContent=e.message;else toast(e.message)}}
function appUpdatesDialog(){
 sheet('Обновления ilenbook',`<p id="app-update-current">Установлена версия ${esc(VERSION)}</p><p class="description" id="app-update-latest"></p><strong id="app-update-status" role="status">Получаем состояние…</strong><progress id="app-update-progress" class="app-update-progress" hidden></progress><p class="description error-text" id="app-update-error" role="alert"></p><button class="primary full" id="app-update-install" hidden>Установить обновление</button><button class="primary full" id="app-update-download" hidden>Скачать обновление</button><button class="secondary full" id="app-update-cancel" hidden>Отменить скачивание</button><button class="secondary full" id="app-update-check">Проверить обновления</button><p class="description" id="app-update-checked"></p><label class="setting-row"><span>Проверять при открытии приложения<small>Обычно один раз в сутки, без окон поверх чтения</small></span><input type="checkbox" id="app-update-auto" checked></label><label class="setting-row"><span>Скачивать автоматически по Wi-Fi<small>При работе приложения. Установка — по вашей кнопке</small></span><input type="checkbox" id="app-update-wifi"></label><details><summary>Что нового</summary><div id="app-update-notes" class="app-update-notes"></div></details><button class="settings-link" id="app-update-github">Открыть релизы на GitHub ↗</button><p class="description">Android попросит подтвердить установку. При первом обновлении разрешите ilenbook устанавливать приложения. Книги и заметки сохранятся.</p>`,!!state.reader);
 updateDialogOpen=true;const previousCleanup=sheetCleanup;sheetCleanup=()=>{updateDialogOpen=false;clearTimeout(updatePoll);previousCleanup?.()};
 $('#app-update-check').onclick=()=>updateAction('updatesCheck');$('#app-update-download').onclick=()=>updateAction('updatesDownload');$('#app-update-install').onclick=()=>updateAction('updatesInstall');$('#app-update-cancel').onclick=()=>updateAction('updatesCancel');$('#app-update-github').onclick=()=>updateAction('updatesOpenRelease');
 $('#app-update-auto').onchange=e=>updateAction('updatesConfigure',{autoCheck:e.target.checked});$('#app-update-wifi').onchange=e=>updateAction('updatesConfigure',{autoDownloadWifi:e.target.checked});
 if(appUpdateState)paintUpdateDialog();if(window.Native)void refreshAppUpdateStatus();else $('#app-update-error').textContent='Проверка и установка доступны в Android-приложении.';
}
const settingsBeforeUpdates=renderSettings;renderSettings=function(){settingsBeforeUpdates();const card=document.createElement('section');card.className='settings-card';card.innerHTML='<h2>Обновления</h2><button class="settings-link" id="app-updates-open"><span>Проверка и установка<small id="app-updates-summary"></small></span>'+icon('download')+'</button>';$('#view').appendChild(card);$('#app-updates-open').onclick=appUpdatesDialog;$('#app-updates-summary').textContent=updateSummary(appUpdateState)};
const renderBeforeUpdates=render;render=function(){renderBeforeUpdates();renderUpdateNotice()};
const eventBeforeUpdates=window.nativeEvent;window.nativeEvent=async(type,data)=>{if(type==='updates')applyAppUpdateStatus(data);else return eventBeforeUpdates?.(type,data)};
document.addEventListener('DOMContentLoaded',()=>{if(window.Native)void refreshAppUpdateStatus()});
