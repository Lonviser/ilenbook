'use strict';

let searchSequence=0;
const SEARCH_PAGE_SIZE=60;
const sourceCatalogRender=renderCatalog;
const isBookEntry=entry=>entry.kind==='book'||!!entry.downloads?.length;

function cancelGlobalSearch(search){
 if(!search)return;search.cancelled=true;
 for(const group of search.groups||[search.group])window.Native?.cancelSearch(group);
}
function recentSearches(){return (Array.isArray(prefs.searchHistory)?prefs.searchHistory:[]).filter(x=>x&&typeof x.query==='string'&&x.query.trim()).slice(0,8)}
function rememberSearch(query,mode){prefs.lastSearch=query;prefs.searchHistory=[{query:query.slice(0,200),mode},...recentSearches().filter(x=>fold(x.query)!==fold(query))].slice(0,8);save()}
function globalSearchForm(query='',mode='all'){
 return `<form id="global-search-form"><label class="searchbox catalog-searchbox">${icon('search')}<input id="global-query" type="search" maxlength="200" placeholder="Название книги или автор" value="${esc(query)}" aria-label="Поиск по всем каталогам" enterkeyhint="search"><button class="icon-btn search-submit" aria-label="Искать во всех каталогах" type="submit">${icon('arrow')}</button></label><div class="search-options"><label>Язык<select class="field" id="global-language" aria-label="Язык результатов"><option value="ru">Русский</option><option value="all">Все языки</option></select></label><label>Искать по<select id="global-mode" class="field" aria-label="Область поиска">${[['all','Книге и автору'],['books','Названию'],['authors','Автору']].map(([id,label])=>`<option value="${id}" ${mode===id?'selected':''}>${label}</option>`).join('')}</select></label></div></form>`;
}
function bindGlobalForm(){
 const form=$('#global-search-form');if(!form)return;$('#global-language').value=prefs.catalogLanguage||'ru';
 $('#global-language').onchange=e=>{prefs.catalogLanguage=e.target.value;save();if(state.globalSearch)startGlobalSearch(state.globalSearch.query,state.globalSearch.mode)};
 form.onsubmit=e=>{e.preventDefault();startGlobalSearch($('#global-query').value,$('#global-mode').value)};
}
function renderSearchHome(){
 const active=searchSources().length,history=recentSearches();
 $('#view').innerHTML=`<div class="section-head"><div><h1>Найти книгу</h1></div><button class="icon-btn" id="show-downloads" aria-label="Загрузки">${icon('download')}</button></div><p class="description search-intro">Один запрос — несколько библиотек. Скачанные книги останутся доступны без сети.</p>${globalSearchForm(prefs.lastSearch||'')}<div class="search-source-summary"><span>${active} ${active===1?'источник':'источников'} в общем поиске</span><button class="text-button" id="manage-search-sources">Выбрать источники</button></div>${history.length?`<section class="search-history"><div class="section-head"><h2>Недавно искали</h2><button class="text-button" id="clear-search-history">Очистить</button></div><div class="chips">${history.map((item,i)=>`<button class="chip" data-recent-search="${i}">${icon('clock')}${esc(item.query)}</button>`).join('')}</div></section>`:''}<section id="catalog-view"><div class="section-head"><h2>Библиотеки</h2><button class="icon-btn" id="new-source" aria-label="Добавить каталог">${icon('plus')}</button></div><div class="catalog-source-grid">${prefs.sources.map((s,i)=>`<button class="catalog-source source-card" data-source="${i}"><span class="source-emblem">${icon(s.provider==='wikisource'?'book':s.provider==='archive'?'folder':'globe')}</span><span><strong>${esc(s.name)}</strong><small>${esc(s.language||'OPDS-каталог')}</small>${s.enabled===false?'<small>Исключён из общего поиска</small>':''}</span>${icon('arrow')}</button>`).join('')}</div></section>`;
 bindGlobalForm();$('#show-downloads').onclick=downloadsDialog;$('#manage-search-sources').onclick=catalogSourceManager;$('#new-source').onclick=()=>sourceForm(-1);
 $$('[data-source]').forEach(button=>button.onclick=()=>{state.source=prefs.sources[Number(button.dataset.source)];state.history=[];state.remoteQuery='';loadCatalog(state.source.url)});
 $$('[data-recent-search]').forEach(button=>button.onclick=()=>{const item=history[Number(button.dataset.recentSearch)];startGlobalSearch(item.query,item.mode||'all')});
 if($('#clear-search-history'))$('#clear-search-history').onclick=()=>{prefs.searchHistory=[];prefs.lastSearch='';save();renderSearchHome()};
}
renderCatalog=function(){if(state.globalSearch)renderGlobalSearch();else if(state.source)sourceCatalogRender();else renderSearchHome()};

function renderGlobalSearch(){
 const g=state.globalSearch;
 $('#view').innerHTML=`<div class="section-head"><h1>Поиск книг</h1><button class="icon-btn" id="show-downloads" aria-label="Загрузки">${icon('download')}</button></div>${globalSearchForm(g.query,g.mode)}<div class="search-result-status"><p id="global-status" class="search-progress" role="status"></p><button class="secondary compact" id="cancel-global">Остановить</button></div><div class="global-tools"><button class="secondary compact" id="exit-global">${icon('back')}Библиотеки</button><button class="secondary compact" id="manage-search-sources">${icon('settings')}Источники</button><button class="secondary compact" id="open-result-filters" aria-expanded="false">Фильтры</button></div><div id="result-filters" class="result-filters" hidden><label>Источник<select class="field" id="global-filter" aria-label="Источник результатов"><option value="all">Все источники</option>${g.batches.map((b,i)=>`<option value="${i}">${esc(b.source.name)}</option>`).join('')}</select></label><label>Формат<select class="field" id="global-format" aria-label="Формат книги"><option value="all">Все форматы</option><option value="EPUB">EPUB</option><option value="FB2">FB2 / FB2.ZIP</option><option value="TXT">TXT</option></select></label><label>Показывать<select class="field" id="global-kind" aria-label="Тип результатов"><option value="books">Книги и тексты</option><option value="all">Книги и разделы авторов</option></select></label><label>Порядок<select class="field" id="global-sort" aria-label="Порядок результатов"><option value="arrival">Сначала найденные</option><option value="relevance">По соответствию запросу</option><option value="title">По названию</option></select></label><button class="text-button" id="reset-result-filters">Сбросить фильтры</button></div><div id="global-results"></div><button class="secondary full" id="show-local-results" hidden>Показать ещё</button><details id="source-details"><summary>Состояние источников</summary><div id="source-statuses"></div></details><div id="global-pages"></div>`;
 bindGlobalForm();$('#show-downloads').onclick=downloadsDialog;$('#manage-search-sources').onclick=catalogSourceManager;
 $('#exit-global').onclick=()=>{cancelGlobalSearch(g);state.globalSearch=null;state.source=null;renderCatalog()};
 $('#cancel-global').onclick=()=>{cancelGlobalSearch(g);g.batches.forEach(b=>{if(b.status==='loading')b.status='stopped'});updateGlobalResults()};
 $('#open-result-filters').onclick=()=>{const show=$('#result-filters').hidden;$('#result-filters').hidden=!show;$('#open-result-filters').setAttribute('aria-expanded',String(show))};
 for(const [id,key]of [['global-filter','filter'],['global-format','format'],['global-kind','kind'],['global-sort','sort']]){$('#'+id).value=g[key];$('#'+id).onchange=e=>{g[key]=e.target.value;g.limit=SEARCH_PAGE_SIZE;updateGlobalResults()}}
 $('#reset-result-filters').onclick=()=>{Object.assign(g,{filter:'all',format:'all',kind:g.mode==='authors'?'all':'books',sort:'arrival',limit:SEARCH_PAGE_SIZE});for(const [id,key]of [['global-filter','filter'],['global-format','format'],['global-kind','kind'],['global-sort','sort']])$('#'+id).value=g[key];updateGlobalResults()};
 $('#show-local-results').onclick=()=>{g.limit+=SEARCH_PAGE_SIZE;updateGlobalResults()};updateGlobalResults();
}
async function startGlobalSearch(query,mode='all'){
 query=query.trim().slice(0,200);if(!query){toast('Введите название или автора');return}
 const language=prefs.catalogLanguage||'ru',sources=searchSources(language);if(!sources.length){toast('Включите хотя бы один источник');catalogSourceManager();return}
 const signature=JSON.stringify([fold(query),mode,language,sources.map(catalogSourceKey)]),previous=state.globalSearch;
 if(previous&&!previous.cancelled&&previous.pending&&previous.signature===signature)return previous.pending;
 if(previous)cancelGlobalSearch(previous);if(state.catalogGroup){window.Native?.cancelSearch(state.catalogGroup);state.catalogGroup=null}catalogToken++;
 rememberSearch(query,mode);const group='search-'+Date.now()+'-'+(++searchSequence);
 const g={query,mode,signature,group,groups:new Set([group]),language,filter:'all',format:'all',kind:mode==='authors'?'all':'books',sort:'arrival',limit:SEARCH_PAGE_SIZE,cancelled:false,order:[],orderKeys:new Set(),batches:sources.map(source=>({source:{...source},status:'loading',entries:[],pages:[],error:null}))};
 state.globalSearch=g;state.source=null;state.catalog=null;state.history=[];state.tab='catalog';renderCatalog();
 g.pending=Promise.allSettled(g.batches.map(batch=>loadSearchBatch(g,batch,group)));await g.pending;g.pending=null;
}
function searchIsCurrent(g){return !g.cancelled&&state.globalSearch===g}
function combineBatch(batch){
 batch.entries=[...batch.slots.values()].flatMap(feed=>feed.entries||[]).concat(batch.extraEntries);
 const pages=new Set([...batch.slots.values()].flatMap(feed=>feed.next?[feed.next]:[]).concat([...batch.extraNext]));batch.pages=[...pages].filter(url=>!batch.loadedPages.has(url));
 batch.cached=[...batch.slots.values()].every(feed=>!!feed.cached);batch.stale=[...batch.slots.values()].some(feed=>!!feed.stale);
}
async function loadSearchBatch(g,batch,group){
 batch.status='loading';batch.error=null;batch.slots=new Map();batch.extraEntries=[];batch.extraNext=new Set();batch.loadedPages=new Set();
 const paint=()=>{if(searchIsCurrent(g)&&state.tab==='catalog')updateGlobalResults()};paint();
 try{
  let template=batch.source.search;if(!template&&!batch.source.provider){const root=await api('catalog',{url:batch.source.url,group});template=root.search;if(!template)throw Error('Источник не объявил поиск. Укажите шаблон в настройках каталога.')}
  const flibusta=new URL(batch.source.url).hostname==='flibusta.is',modes=flibusta?(g.mode==='all'?['books','authors']:[g.mode]):[g.mode];
  const publish=(kind,feed)=>{if(!searchIsCurrent(g))return;batch.slots.set(kind,feed);combineBatch(batch);paint()};
  const feeds=await Promise.allSettled(modes.map(async kind=>{
   let url=sourceSearchUrl(batch.source,g.query,g.mode,g.language,template);if(flibusta){const u=new URL(url);u.searchParams.set('searchType',kind);url=u.toString()}
   try{const cached=await api('catalogCache',{url,group});if(cached){publish(kind,cached);if(!cached.stale)return cached}}catch{}
   if(!searchIsCurrent(g))throw Error('Поиск отменён');const fresh=await api('catalog',{url,group});publish(kind,fresh);return fresh;
  }));
  if(!searchIsCurrent(g))return;for(const result of feeds)if(result.status==='rejected')batch.error=result.reason.message;
  if(feeds.every(result=>result.status==='rejected')&&!batch.slots.size)throw Error(batch.error);batch.status='done';
 }catch(error){if(!searchIsCurrent(g))return;batch.status='error';batch.error=error.message}paint();
}
async function retrySearchBatch(g,index){
 if(!searchIsCurrent(g))return;const group=g.group+'-retry-'+(++searchSequence);g.groups.add(group);await loadSearchBatch(g,g.batches[index],group);
}
function entryFormats(entry){
 const formats=new Set(entry.formats||[]);for(const d of entry.downloads||[]){const type=(d.type||'').toLowerCase();if(type.includes('epub'))formats.add('EPUB');else if(type.includes('fb2')||type.includes('fictionbook')||type.includes('zip'))formats.add('FB2');else if(type.includes('text/plain'))formats.add('TXT')}return [...formats];
}
function resultRelevance(entry,query){const title=fold(entry.title),author=fold(entry.author),q=fold(query),tokens=q.split(' ');return (title===q?120:title.startsWith(q)?80:title.includes(q)?50:0)+(tokens.every(t=>title.includes(t))?30:0)+(author===q?70:tokens.every(t=>author.includes(t))?40:0)+(isBookEntry(entry)?5:0)}
function mergedGlobalResults(g){
 const merged=new Map();g.orderKeys??=new Set(g.order);
 for(const [index,batch]of g.batches.entries())for(const entry of batch.entries){
  const host=new URL(batch.source.url).hostname.replace(/^www\./,''),id=String(entry.id||entry.downloads?.[0]?.url||entry.url||entry.title+'|'+entry.author).replace(/^https?:\/\/(www\.|m\.)?/,'').replace(/#.*$/,''),key=host+'|'+id;
  const old=merged.get(key);if(old){if(!old.sourceIndexes.includes(index))old.sourceIndexes.push(index);if(!old.sourceNames.includes(batch.source.name))old.sourceNames.push(batch.source.name);if(!old.coverUrl&&entry.coverUrl)old.coverUrl=entry.coverUrl;const urls=new Set(old.downloads.map(d=>d.url));for(const d of entry.downloads||[])if(!urls.has(d.url)){old.downloads.push(d);urls.add(d.url)}}
  else merged.set(key,{...entry,downloads:[...(entry.downloads||[])],key,source:batch.source,sourceNames:[batch.source.name],sourceIndexes:[index]});
  if(!g.orderKeys.has(key)){g.orderKeys.add(key);g.order.push(key)}
 }
 const results=g.order.map(key=>merged.get(key)).filter(entry=>entry&&(g.filter==='all'||entry.sourceIndexes.includes(Number(g.filter)))&&(g.language==='all'||!entry.language||/^(ru|rus)([-_]|$)/i.test(entry.language))&&(g.kind!=='books'||isBookEntry(entry))&&(g.format==='all'||entryFormats(entry).includes(g.format)));
 if(g.sort==='title')results.sort((a,b)=>a.title.localeCompare(b.title,'ru'));else if(g.sort==='relevance')results.sort((a,b)=>resultRelevance(b,g.query)-resultRelevance(a,g.query));return results;
}
function resultCardHTML(entry){
 const book=isBookEntry(entry),formats=entryFormats(entry);
 return `${book?cover(entry,'catalog-cover'):icon('folder')}<div class="entry-copy"><h3>${esc(entry.title)}</h3><p>${esc(entry.author||(book?'Автор не указан':'Автор / раздел'))}</p><small class="result-source">${esc(entry.sourceNames.join(' · '))}</small><small class="result-formats">${book?esc(formats.join(' · ')||'Форматы в карточке')+' · '+esc(entry.language||'язык не указан'):'Открыть книги автора'}</small>${book?'<span class="entry-action">О книге '+icon('arrow')+'</span>':''}</div>${book?'':icon('arrow')}`;
}
function updateGlobalResults(){
 const g=state.globalSearch,container=$('#global-results');if(!g||!container)return;
 const loading=g.batches.some(batch=>batch.status==='loading'),completed=g.batches.filter(batch=>batch.status!=='loading').length,entries=mergedGlobalResults(g);g.visible=entries;
 $('#global-status').textContent=`Найдено ${entries.length} · ${g.cancelled?'поиск остановлен':loading?`ответили ${completed} из ${g.batches.length}`:'поиск завершён'}`;$('#cancel-global').hidden=!loading;
 $('#source-statuses').innerHTML=g.batches.map((batch,index)=>`<section class="source-status"><div><strong>${esc(batch.source.name)}</strong><p>${batch.status==='loading'?'Ищем…'+(batch.entries.length?' · '+batch.entries.length+' уже получено':''):batch.status==='error'?'Нет ответа':batch.status==='stopped'?'Остановлено':batch.entries.length+' записей'+(batch.cached?(batch.stale?' · сохранённые данные':' · из кэша'):'')}</p></div>${batch.error?`<p class="error">${esc(batch.error)}</p>${!g.cancelled?`<button class="secondary compact" data-retry-source="${index}" ${batch.status==='loading'?'disabled':''}>Повторить</button>`:''}`:''}</section>`).join('');
 $$('[data-retry-source]').forEach(button=>button.onclick=()=>retrySearchBatch(g,Number(button.dataset.retrySource)));
 const shown=entries.slice(0,g.limit||SEARCH_PAGE_SIZE),nodes=new Map([...container.children].filter(node=>node.dataset.key).map(node=>[node.dataset.key,node])),wanted=new Set(shown.map(entry=>entry.key));
 for(const [key,node]of nodes)if(!wanted.has(key)){node.remove();nodes.delete(key)}container.querySelector('.search-empty')?.remove();
 let cursor=container.firstElementChild;
 for(const entry of shown){
  let node=nodes.get(entry.key);if(!node){node=document.createElement('button');node.className='catalog-entry global-entry';node.dataset.key=entry.key}
  node.classList.toggle('section-entry',!isBookEntry(entry));
  const signature=JSON.stringify([entry.title,entry.author,entry.coverUrl,entry.cover?entry.cover.length+entry.cover.slice(-24):'',entry.language,entryFormats(entry),entry.sourceNames,isBookEntry(entry)]);
  if(node.dataset.signature!==signature){node.innerHTML=resultCardHTML(entry);node.dataset.signature=signature}
  node.onclick=()=>{if(isBookEntry(entry))catalogDetails(entry,{...entry.source,name:entry.sourceNames.join(' · ')});else if(entry.url){cancelGlobalSearch(g);state.globalSearch=null;state.source=entry.source;loadCatalog(entry.url)}};
  if(node===cursor)cursor=cursor.nextElementSibling;else container.insertBefore(node,cursor);
 }
 if(!shown.length){const p=document.createElement('p');p.className='description search-empty';p.textContent=loading?'Ищем в выбранных библиотеках…':g.batches.every(batch=>batch.status==='error')?'Источники не ответили. Повторите отдельный источник ниже.':'Совпадений нет. Измените запрос или сбросьте фильтры; можно выбрать все языки.';container.appendChild(p)}
 const more=$('#show-local-results');more.hidden=entries.length<=shown.length;more.textContent=`Показать ещё · ${entries.length-shown.length}`;
 $('#global-pages').innerHTML=g.batches.map((batch,index)=>batch.pages.length?`<button class="secondary full" data-global-more="${index}" ${batch.pageLoading?'disabled':''}>Ещё в каталоге · ${esc(batch.source.name)}</button>`:'').join('');
 $$('[data-global-more]').forEach(button=>button.onclick=()=>loadMoreSearchResults(g,Number(button.dataset.globalMore)));
}
async function loadMoreSearchResults(g,index){
 const batch=g.batches[index],url=batch.pages[0];if(!url||batch.pageLoading)return;batch.pageLoading=true;updateGlobalResults();
 try{const feed=await api('catalog',{url,group:g.group});if(!searchIsCurrent(g))return;batch.loadedPages.add(url);batch.extraEntries.push(...feed.entries);if(feed.next)batch.extraNext.add(feed.next);combineBatch(batch)}catch(error){if(searchIsCurrent(g))toast(error.message)}finally{batch.pageLoading=false;if(searchIsCurrent(g)&&state.tab==='catalog')updateGlobalResults()}
}

// Keep only a small memory window; the native image cache remains on disk.
const coverRequests=new Map(),localCoverRequests=new Map();let coverEpoch=0;
function boundedCoverRequest(cache,key,request){
 if(cache.has(key)){const value=cache.get(key);cache.delete(key);cache.set(key,value);return value}
 const pending=request().catch(error=>{if(cache.get(key)===pending)cache.delete(key);throw error});cache.set(key,pending);while(cache.size>32)cache.delete(cache.keys().next().value);return pending;
}
function paintCover(node,data){if(!node.isConnected||!/^data:image\/(jpeg|png|webp);base64,/.test(data||''))return;let img=node.querySelector('img');if(!img){img=document.createElement('img');img.alt='';node.appendChild(img)}img.src=data;node.classList.add('has-cover')}
const coverViewport=new IntersectionObserver(entries=>{for(const entry of entries)if(entry.isIntersecting){coverViewport.unobserve(entry.target);hydrateCover(entry.target)}},{rootMargin:'100px'});
function observeCovers(root=app){
 if(root.nodeType!==1)return;const selector='[data-cover-url]:not([data-cover-observed]),[data-book-cover]:not([data-cover-observed])';
 for(const node of [...(root.matches(selector)?[root]:[]),...root.querySelectorAll(selector)]){node.dataset.coverObserved='1';coverViewport.observe(node)}
}
async function hydrateCover(node){
 const epoch=coverEpoch;try{const id=node.dataset.bookCover,url=node.dataset.coverUrl;
  if(id&&id!=='welcome'){const book=await boundedCoverRequest(localCoverRequests,id,()=>api('findCover',{id}));if(epoch!==coverEpoch)return;if(book){const local=state.books.find(value=>value.id===id);if(local)Object.assign(local,book);if(book.cover){for(const other of $$('[data-book-cover]'))if(other.dataset.bookCover===id)paintCover(other,book.cover);return}}}
  if(url)paintCover(node,await boundedCoverRequest(coverRequests,url,()=>api('cover',{url})));
 }catch{}
}
const coverMutations=new MutationObserver(records=>{for(const record of records)for(const node of record.addedNodes)observeCovers(node)});
coverMutations.observe(app,{childList:true,subtree:true});coverMutations.observe(overlay,{childList:true,subtree:true});observeCovers(app);observeCovers(overlay);
