async(page)=>{
 await page.waitForFunction(()=>window.Native&&typeof api==='function'&&state.books.length>0);
 const result=await page.evaluate(async()=>{
  const before=new Set((await api('list')).map(b=>b.id)),checks={},start=Date.now(),nonce=Date.now();
  window.flibustaDirectCheck=checks;await api('flibustaConfigure',{mode:'direct'});
  let t=Date.now();const root=await api('catalog',{url:'https://flibusta.is/opds?ilencheck='+nonce,group:'v8-direct'});checks.root={ms:Date.now()-t,title:root.title,entries:root.entries.length};
  t=Date.now();const url='https://flibusta.is/opds/search?searchType=books&searchTerm='+encodeURIComponent('Каштанка')+'&ilencheck='+nonce;const feed=await api('catalog',{url,group:'v8-direct'});checks.search={ms:Date.now()-t,entries:feed.entries.length,cached:!!feed.cached};
  const book=feed.entries.find(b=>/Чехов/i.test(b.author)&&b.downloads?.some(d=>/fb2|fictionbook/i.test(d.type)));if(!book)throw Error('Chekhov not found in actual Flibusta search');
  t=Date.now();const repeat=await api('catalog',{url,group:'v8-direct-repeat'});checks.repeat={ms:Date.now()-t,cached:!!repeat.cached};
  t=Date.now();const authors=await api('catalog',{url:'https://flibusta.is/opds/search?searchType=authors&searchTerm='+encodeURIComponent('Чехов')+'&ilencheck='+nonce,group:'v8-authors'});checks.authors={ms:Date.now()-t,entries:authors.entries.length};if(!authors.entries.some(e=>/Чехов/.test(e.title)))throw Error('Author search has no Chekhov');
  if(book.coverUrl){t=Date.now();const cover=await api('cover',{url:book.coverUrl});checks.cover={ms:Date.now()-t,jpeg:cover.startsWith('data:image/jpeg;base64,'),bytes:cover.length};if(!checks.cover.jpeg)throw Error('Cover unavailable');}
  const download=book.downloads.find(d=>/fb2|fictionbook/i.test(d.type));t=Date.now();const saved=await api('download',{...download,title:book.title,source:'Флибуста',coverUrl:book.coverUrl||''});const full=await api('read',{id:saved.id});checks.download={ms:Date.now()-t,title:saved.title,author:saved.author,format:saved.format,chapters:full.chapters.length,characters:saved.characters,originalName:saved.originalName};if(!full.chapters.some(c=>/Каштанк/.test(c.text))||!saved.originalName?.match(/\.fb2(\.zip)?$/))throw Error('Missing Russian text or reusable original');
  if(!before.has(saved.id))await api('delete',{id:saved.id});state.books=await api('list');checks.totalMs=Date.now()-start;checks.connection=await api('flibustaStatus');return checks;
 });
 return result;
}
