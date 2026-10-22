'use strict';

const readingSearchFold=text=>text.toLocaleLowerCase('ru').replace(/ё/g,'е');
async function readerSearchIndex(reader){
 if(reader.searchIndex)return reader.searchIndex;
 if(reader.searchIndexPending)return reader.searchIndexPending;
 reader.searchIndexPending=(async()=>{
  const chapters=[];let sliceStarted=performance.now();
  for(const chapter of reader.book.chapters){
   if(state.reader!==reader)throw Error('Книга закрыта');
   const root=document.createElement('div');root.innerHTML=chapterReadingHTML(chapter);
   const blocks=[...root.querySelectorAll('p,li,h1,h2,h3,h4,pre,blockquote')].filter(node=>!node.querySelector('p,li,h1,h2,h3,h4,pre,blockquote')).map((node,block)=>({block,text:node.textContent,folded:readingSearchFold(node.textContent)}));
   chapters.push({title:chapter.title,blocks});
   if(performance.now()-sliceStarted>=8){await new Promise(resolve=>requestAnimationFrame(resolve));sliceStarted=performance.now()}
  }
  reader.searchIndex=chapters;return chapters;
 })().finally(()=>{reader.searchIndexPending=null});return reader.searchIndexPending;
}
function readerSearchDialog(){
 const reader=state.reader;if(!reader)return;
 sheet('Найти в книге',`<label class="searchbox">${icon('search')}<input id="text-query" type="search" maxlength="200" placeholder="Слово или фраза" aria-label="Поиск по тексту" enterkeyhint="search"></label><div id="text-hits" aria-live="polite"><p class="description">Введите хотя бы два символа</p></div>`,true);
 const input=$('#text-query'),output=$('#text-hits');let timer=null,revision=0,closed=false;
 sheetCleanup=()=>{closed=true;revision++;clearTimeout(timer)};
 const run=async()=>{
  const q=readingSearchFold(input.value.trim()),epoch=++revision;if(q.length<2){output.textContent='Введите хотя бы два символа';return}
  output.textContent=reader.searchIndex?'Ищем…':'Готовим текст для поиска…';
  try{
   const index=await readerSearchIndex(reader);if(closed||epoch!==revision||!output.isConnected)return;
   const hits=[];
   for(let chapter=0;chapter<index.length&&hits.length<80;chapter++)for(const block of index[chapter].blocks){
    const at=block.folded.indexOf(q);if(at<0)continue;hits.push({chapter,block:block.block,char:at,excerpt:block.text.slice(at,at+40),text:block.text.slice(Math.max(0,at-35),at+q.length+85),title:index[chapter].title});if(hits.length>=80)break;
   }
   output.innerHTML=hits.length?`<p class="description">${hits.length===80?'Первые 80 совпадений':hits.length+' совпадений'}</p>`+hits.map((hit,i)=>`<button class="search-hit" data-hit="${i}"><small>${esc(hit.title)}</small>${esc(hit.text)}</button>`).join(''):'<p class="description">Совпадений нет</p>';
   $$('[data-hit]').forEach(button=>button.onclick=()=>{const hit=hits[Number(button.dataset.hit)];closeSheet();jumpTo(hit.chapter,hit)});
  }catch(error){if(!closed&&epoch===revision&&output.isConnected)output.textContent=error.message}
 };
 input.oninput=()=>{revision++;clearTimeout(timer);if(input.value.trim().length<2){output.textContent='Введите хотя бы два символа';return}timer=setTimeout(run,160)};
 input.onkeydown=event=>{if(event.key==='Enter'){event.preventDefault();clearTimeout(timer);run()}};input.focus({preventScroll:true});
}
