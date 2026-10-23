async(page)=>{
 await page.evaluate(()=>{
  window.flibustaReserveCheck={running:true,started:Date.now()};
  (async()=>{const group='v8-reserve-'+Date.now(),report={};try{
   const before=new Set((await api('list')).map(b=>b.id));await api('flibustaConfigure',{mode:'snowflake'});let start=Date.now();
   const root=await api('flibustaTest',{group});report.bootstrap={ms:Date.now()-start,...root};
   start=Date.now();const feed=await api('catalog',{url:'https://flibusta.is/opds/search?searchType=books&searchTerm='+encodeURIComponent('Каштанка')+'&reserve='+Date.now(),group});report.search={ms:Date.now()-start,entries:feed.entries.length,cached:!!feed.cached};
   const book=feed.entries.find(b=>/Чехов/i.test(b.author)&&b.downloads?.some(d=>/fb2|fictionbook/i.test(d.type)));if(!book)throw Error('No Russian Chekhov via reserve');
   if(book.coverUrl){start=Date.now();const image=await api('cover',{url:book.coverUrl+'?reserve='+Date.now(),group});if(!image.startsWith('data:image/jpeg;base64,'))throw Error('Reserve cover failed');report.cover={ms:Date.now()-start,length:image.length}}
   start=Date.now();const download=book.downloads.find(d=>/fb2|fictionbook/i.test(d.type)),saved=await api('download',{...download,title:book.title,source:'Флибуста',group});const full=await api('read',{id:saved.id});if(!full.chapters.some(c=>c.text.includes('Каштанк')))throw Error('No Russian book text');report.download={ms:Date.now()-start,title:saved.title,characters:saved.characters,originalName:saved.originalName};
   if(!before.has(saved.id))await api('delete',{id:saved.id});report.connection=await api('flibustaStatus');report.ok=true;
  }catch(e){report.error=e.message;report.connection=await api('flibustaStatus').catch(()=>null)}finally{window.flibustaReserveCheck={...report,running:false,done:true};}})();
 });
 return await page.evaluate(()=>flibustaReserveCheck);
}
