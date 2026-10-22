'use strict';

function libraryLayoutDialog(){
 const selected=state.list?'list':state.shelf;
 sheet('Вид библиотеки',`<p class="description">Выберите удобный размер обложек. Вид сохранится для следующего открытия.</p><div class="layout-choices">${[['large','Крупная полка','Две обложки в ряд на небольшом экране','grid'],['compact','Компактная полка','Больше книг на одном экране','grid'],['list','Список','Название и автор рядом с обложкой','list']].map(([id,title,hint,glyph])=>`<button class="layout-choice ${id===selected?'selected':''}" data-library-layout="${id}" aria-pressed="${id===selected}">${icon(glyph)}<span><strong>${title}</strong><small>${hint}</small></span>${id===selected?icon('check'):''}</button>`).join('')}</div>`);
 $$('[data-library-layout]').forEach(button=>button.onclick=()=>{const layout=button.dataset.libraryLayout;state.list=layout==='list';if(!state.list)state.shelf=layout;rememberUI();flushUISettings();closeSheet();renderLibrary()});
}
