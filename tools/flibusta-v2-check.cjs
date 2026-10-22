async (page)=>{
 return await page.evaluate(async()=>{
  const root=await api('catalog',{url:'https://flibusta.is/opds'});
  const url='https://flibusta.is/opds/search?searchType=books&searchTerm='+encodeURIComponent('Каштанка');
  const c=await api('catalog',{url});
  const entry=c.entries.find(e=>e.author.includes('Чехов')&&e.downloads.length);
  if(!entry)throw Error('No Chekhov download: '+JSON.stringify(c.entries.map(e=>({title:e.title,author:e.author,downloads:e.downloads.length}))));
  const d=entry.downloads.find(d=>d.label==='EPUB')||entry.downloads[0];
  const b=await api('download',{...d,title:entry.title,source:'Флибуста'});const full=await api('read',{id:b.id});
  if(!full.chapters.some(c=>c.text.includes('Каштанк')||c.text.includes('Каштанк')))throw Error('No expected Russian content');
  state.books=await api('list');state.lastFlibustaTest=b;
  return {root:root.title,results:c.entries.length,title:b.title,author:b.author,format:b.format,chapters:full.chapters.length,characters:b.characters,originalName:b.originalName,id:b.id};
 });
}
