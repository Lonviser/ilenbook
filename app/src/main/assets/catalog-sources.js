'use strict';

const ARCHIVE_FREE_QUERY='mediatype:texts AND (format:EPUB OR format:DjVuTXT OR format:Text) AND (licenseurl:*publicdomain* OR licenseurl:*creativecommons.org*) AND -access-restricted-item:true';
const ARCHIVE_FIELDS=['identifier','title','creator','language','licenseurl','format'];
function archiveSearchUrl(query='',mode='all',language='ru'){
 const literal='"'+query.replace(/[\\"]/g,'\\$&')+'"';
 const term=query?(mode==='books'?'title:'+literal:mode==='authors'?'creator:'+literal:'(title:'+literal+' OR creator:'+literal+')'):'';
 const q=ARCHIVE_FREE_QUERY+(language==='ru'?' AND (language:rus OR language:Russian)':'')+(term?' AND '+term:'');
 const u=new URL('https://archive.org/advancedsearch.php');u.searchParams.set('q',q);u.searchParams.set('output','json');u.searchParams.set('rows','20');u.searchParams.set('page','1');for(const field of ARCHIVE_FIELDS)u.searchParams.append('fl[]',field);return u.toString();
}
const PUBLIC_CATALOG_SOURCES=[
 {id:'wikisource-ru',name:'Викитека',url:'https://ru.wikisource.org/w/api.php?action=query&list=search&srnamespace=0&srlimit=20&format=json&formatversion=2&srsearch='+encodeURIComponent('Классика'),provider:'wikisource',language:'Русские тексты · TXT',enabled:true},
 {id:'archive-free',name:'Internet Archive',url:archiveSearchUrl(),provider:'archive',language:'Свободные тексты · EPUB, TXT',enabled:true}
];
function migrateCatalogSources(){
 if(Number(prefs.catalogDefaultsVersion)>=7)return;
 for(const [index,source]of PUBLIC_CATALOG_SOURCES.entries())if(!prefs.sources.some(s=>s.id===source.id||s.provider===source.provider&&s.url===source.url))prefs.sources.splice(Math.min(1+index,prefs.sources.length),0,{...source});
 prefs.catalogDefaultsVersion=7;
}
function catalogSourceKey(source){return source.id||source.url}
function searchSources(language=prefs.catalogLanguage||'ru'){
 const sources=prefs.sources.filter(s=>s.enabled!==false);
 // The Russian and general Gutenberg feeds return the same IDs. Avoid a duplicate request in Russian mode.
 const russianGutenberg=sources.some(s=>s.url==='https://www.gutenberg.org/ebooks/search.opds/?query=l.ru');
 return sources.filter(s=>!(language==='ru'&&russianGutenberg&&s.url==='https://www.gutenberg.org/ebooks/search.opds/'));
}
function sourceSearchUrl(source,query,mode,language,template=source.search){
 if(source.provider==='archive')return archiveSearchUrl(query,mode,language);
 if(source.provider==='wikisource'){const term=mode==='books'?'intitle:"'+query.replace(/[\\"]/g,'\\$&')+'"':query;return 'https://ru.wikisource.org/w/api.php?action=query&list=search&srnamespace=0&srlimit=20&format=json&formatversion=2&srsearch='+encodeURIComponent(term)}
 return buildSearchUrl(template,query);
}
function catalogSourceManager(){
 sheet('Источники поиска',`<p class="description">Включённые источники участвуют в общем поиске. Каталог можно открыть отдельно, даже если он выключен.</p><div class="source-manager">${prefs.sources.map((s,i)=>`<div class="source-manage-row"><label class="setting-row"><input type="checkbox" data-source-enabled="${i}" ${s.enabled!==false?'checked':''}><span>${esc(s.name)}<small>${esc(s.language||'OPDS-каталог')}</small></span></label><button class="icon-btn" data-source-edit="${i}" aria-label="Изменить ${esc(s.name)}">${icon('settings')}</button></div>`).join('')}</div><button class="secondary full" id="restore-default-sources">Вернуть встроенные каталоги</button><button class="secondary full" id="manager-add-source">Добавить OPDS-каталог</button><button class="primary full" id="sources-done">Готово</button>`);
 $$('[data-source-enabled]').forEach(c=>c.onchange=()=>{prefs.sources[Number(c.dataset.sourceEnabled)].enabled=c.checked;save()});
 $$('[data-source-edit]').forEach(b=>b.onclick=()=>sourceForm(Number(b.dataset.sourceEdit)));
 $('#restore-default-sources').onclick=()=>{for(const source of DEFAULT_SOURCES)if(!prefs.sources.some(s=>s.url===source.url||source.id&&s.id===source.id))prefs.sources.push({...source});save();catalogSourceManager()};
 $('#manager-add-source').onclick=()=>sourceForm(-1);$('#sources-done').onclick=()=>{closeSheet();if(state.tab==='catalog'&&!state.globalSearch&&!state.source)renderCatalog();else if(state.tab==='settings')renderSettings()};
}
function publicSourceForm(i){const s=prefs.sources[i];sheet('Каталог',`<label class="field-label">Название<input class="field" id="source-name" maxlength="80" value="${esc(s.name)}"></label><p class="description">${esc(s.language)}. Адрес встроенного источника задаётся приложением.</p><label class="setting-row"><span>Участвует в общем поиске</span><input type="checkbox" id="source-enabled" ${s.enabled!==false?'checked':''}></label><button class="primary full" id="save-source">Сохранить</button><button class="danger full" id="remove-source">Удалить каталог</button>`);$('#save-source').onclick=()=>{const name=$('#source-name').value.trim();if(!name){toast('Введите название');return}s.name=name;s.enabled=$('#source-enabled').checked;save();closeSheet();render()};$('#remove-source').onclick=()=>{prefs.sources.splice(i,1);save();closeSheet();render()}}
function wikiPageText(payload,entry){
 const template=document.createElement('template');template.innerHTML=payload.html;
 const root=template.content,title=(root.querySelector('#ws-title')?.textContent||payload.title||entry.title).trim().slice(0,300),author=[...root.querySelectorAll('#ws-author a')].map(n=>n.textContent.trim()).filter(Boolean).join(', ').slice(0,300);
 root.querySelectorAll('script,style,iframe,object,embed,form,img,nav,.ws-noexport,.mw-editsection,.noprint,.navbox,.metadata,.catlinks,.toc,#toc,#headertemplate,.printfooter,.licensetpl,.licenseContainer').forEach(n=>n.remove());
 const chunks=[];function walk(node){if(node.nodeType===3){chunks.push(node.textContent);return}const block=/^(P|DIV|SECTION|H[1-6]|LI|PRE|BLOCKQUOTE|TR)$/.test(node.nodeName);if(block)chunks.push('\n\n');if(node.nodeName==='BR')chunks.push('\n');for(const child of node.childNodes)walk(child);if(block)chunks.push('\n\n')}
 walk(root);const text=chunks.join('').replace(/\u00a0/g,' ').replace(/[ \t]+\n/g,'\n').replace(/\n[ \t]+/g,'\n').replace(/\n{3,}/g,'\n\n').trim();
 return {title,author,text,sourceUrl:Number(payload.revision)>0?'https://ru.wikisource.org/w/index.php?oldid='+Number(payload.revision):entry.externalUrl};
}
async function openCatalogOriginal(url){try{if(window.Native)await api('openCatalogLink',{url});else window.open(url,'_blank','noopener')}catch(e){toast(e.message)}}
async function publicSourceDetails(entry,source){
 sheet('О книге',`<div class="detail-head">${cover(entry,'detail-cover')}<div><h2>${esc(entry.title)}</h2><p class="subtle">${esc(source?.name||'Свободная библиотека')}</p></div></div><div id="public-book-content" role="status"><p class="description">Получаем сведения об издании…</p></div><button class="secondary full" id="open-source-original">Страница источника ↗</button>`);
 $('#open-source-original').onclick=()=>openCatalogOriginal(entry.externalUrl);const panel=$('#public-book-content');
 try{
  const payload=await api('publicBook',{url:entry.detailsUrl});if(!panel.isConnected)return;
  if(entry.provider==='archive'){catalogDetails({...entry,...payload,detailsUrl:'',kind:'book'},source);return}
  const document=wikiPageText(payload,entry);if(document.text.length<20)throw Error('На этой странице нет текста для сохранения. Откройте источник и выберите произведение.');
  panel.innerHTML=`<p class="subtle">${esc(document.author||'Викитека')}</p><p class="description">Будет сохранён TXT с текстом этой страницы. Части произведения на других страницах, иллюстрации и издательская вёрстка в него не входят.</p><blockquote class="note-quote">${esc(document.text.slice(0,360))}…</blockquote><button class="primary full" id="save-wiki-text">Сохранить текст · TXT</button>`;
  $('#save-wiki-text').onclick=async()=>{const button=$('#save-wiki-text');button.disabled=true;try{const book=await api('savePublicText',document);upsertBook(book);closeSheet();if(state.tab==='library')renderLibrary();toast(book.duplicate?'Этот текст уже есть в библиотеке':'Текст сохранён · доступен без сети')}catch(e){toast(e.message)}finally{if(button.isConnected)button.disabled=false}};
 }catch(e){if(panel.isConnected){panel.innerHTML=`<p class="error">${esc(e.message)}</p><button class="secondary full" id="retry-public-book">Повторить</button>`;$('#retry-public-book').onclick=()=>publicSourceDetails(entry,source)}}
}
