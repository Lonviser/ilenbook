async (page)=>{
 return await page.evaluate(async()=>{try{const c=await api('catalog',{url:'https://flibusta.is/opds'});return {ok:true,title:c.title,entries:c.entries.map(e=>e.title),search:c.search}}catch(e){return {ok:false,error:e.message}}});
}
