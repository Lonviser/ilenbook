'use strict';

const FLIBUSTA_MODES={auto:'Автоматически',direct:'Напрямую',tor:'Через Tor',snowflake:'Через Snowflake'};
const isFlibustaSource=source=>{try{return ['flibusta.is','www.flibusta.is'].includes(new URL(source?.url).hostname)}catch{return false}};
function flibustaPhase(status){
 if(status.phase==='ready')return 'Резервное соединение готово';
 if(status.phase==='tor'||status.phase==='snowflake')return `Подключаем ${status.phase==='tor'?'Tor':'Snowflake'} · ${status.percent}%`;
 return status.phase==='error'?status.error:'Прямое соединение проверяется первым. При сбое используется резервное.';
}
async function flibustaConnectionDialog(){
 let current;try{current=await api('flibustaStatus')}catch{toast('Настройка подключения доступна в Android-приложении');return}
 sheet('Подключение Флибусты',`<label class="field-label">Способ подключения<select class="field" id="flibusta-mode">${Object.entries(FLIBUSTA_MODES).map(([key,label])=>`<option value="${key}">${label}</option>`).join('')}</select></label><p class="description">Автоматический режим сначала обращается к Флибусте напрямую. Если сервер недоступен, ilenbook запускает встроенный Tor, затем пробует мост Snowflake. Книги, поиск и обложки используют одно подключение.</p><p class="description">Первый запуск резервного соединения может занять до трёх минут. Оно применяется только к Флибусте. Если и оно недоступно, попробуйте VPN или другую сеть.</p><p id="flibusta-connection-status" class="description" role="status"></p><button class="primary full" id="flibusta-save">Сохранить</button><button class="secondary full" id="flibusta-test">Проверить Флибусту</button>`,!!state.reader);
 $('#flibusta-mode').value=current.mode;$('#flibusta-connection-status').textContent=flibustaPhase(current);
 let disposed=false,pollTimer,testGroup=null;const previousCleanup=sheetCleanup;
 sheetCleanup=()=>{disposed=true;clearTimeout(pollTimer);if(testGroup)window.Native?.cancelSearch(testGroup);previousCleanup?.()};
 async function poll(){if(disposed||!$('#flibusta-connection-status'))return;try{const s=await api('flibustaStatus');if(!disposed&&['tor','snowflake'].includes(s.phase))$('#flibusta-connection-status').textContent=flibustaPhase(s)}catch{}if(!disposed)pollTimer=setTimeout(poll,700)}poll();
 async function apply(){const status=await api('flibustaStatus'),mode=$('#flibusta-mode').value;if(mode===status.mode&&status.phase!=='error')return status;if(state.globalSearch){cancelGlobalSearch(state.globalSearch);state.globalSearch.batches.forEach(b=>{if(b.status==='loading')b.status='stopped'});if($('#global-results'))updateGlobalResults();}if(state.catalogGroup)window.Native?.cancelSearch(state.catalogGroup);return api('flibustaConfigure',{mode})}
 $('#flibusta-save').onclick=async()=>{try{await apply();closeSheet();toast('Подключение Флибусты сохранено')}catch(e){toast(e.message)}};
 $('#flibusta-test').onclick=async()=>{
  const button=$('#flibusta-test'),saveButton=$('#flibusta-save');button.disabled=saveButton.disabled=true;
  try{await apply();testGroup='flibusta-test-'+Date.now();$('#flibusta-connection-status').textContent='Проверяем живой каталог без кэша…';const result=await api('flibustaTest',{group:testGroup});if(!disposed)$('#flibusta-connection-status').textContent=`Флибуста отвечает · ${(result.elapsedMs/1000).toFixed(1)} с · ${result.phase==='ready'?'резервное соединение':'прямое соединение'}`;}
  catch(e){if(!disposed)$('#flibusta-connection-status').textContent=e.message}
  finally{testGroup=null;if(!disposed)button.disabled=saveButton.disabled=false;}
 };
}
const catalogWithoutConnection=renderCatalog;
let flibustaProgressTimer;
renderCatalog=function(){
 catalogWithoutConnection();clearTimeout(flibustaProgressTimer);
 const host=$('#view');if(!host||!(!state.source||isFlibustaSource(state.source)))return;
 const controls=document.createElement('div');controls.className='flibusta-network-tools';controls.innerHTML='<button class="text-button" id="flibusta-connection">Подключение Флибусты</button><span class="description" id="flibusta-route-status" role="status"></span>';
 const after=$('#global-pages')||$('#catalog-view')||host.lastElementChild;after?.after(controls);$('#flibusta-connection').onclick=flibustaConnectionDialog;
 async function progress(){
  const node=$('#flibusta-route-status'),search=state.globalSearch,loading=search?search.batches.some(b=>isFlibustaSource(b.source)&&b.status==='loading'):state.catalogBusy;
  if(!node||state.tab!=='catalog')return;
  if(!loading){node.textContent='';return}
  if(window.Native)try{const s=await api('flibustaStatus');if(node.isConnected)node.textContent=['tor','snowflake'].includes(s.phase)?flibustaPhase(s):''}catch{}
  flibustaProgressTimer=setTimeout(progress,700);
 }progress();
};
const sourceFormWithoutConnection=sourceForm;
sourceForm=function(i){sourceFormWithoutConnection(i);if(isFlibustaSource(prefs.sources[i])){const button=document.createElement('button');button.className='secondary full';button.textContent='Подключение Флибусты';button.id='source-flibusta-connection';button.onclick=flibustaConnectionDialog;$('#save-source')?.after(button)}};
